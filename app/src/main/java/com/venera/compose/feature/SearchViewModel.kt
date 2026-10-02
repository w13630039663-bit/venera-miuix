package com.venera.compose.feature

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.data.api.ContentGuard
import com.venera.compose.data.network.ComicUrlTable
import com.venera.compose.data.tags.ChineseVariantConverter
import com.venera.compose.data.tags.TagTranslationManager
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.SearchOptionGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

data class SearchUiState(
    val query: String = "",
    val selectedSourceKey: String = SearchViewModel.KEY_ALL,
    val selectedSourceLabel: String = SearchViewModel.SOURCE_ALL_LABEL,
    val results: List<Comic> = emptyList(),
    val aggregatedResults: Map<String, ComicSourceManager.SourceSearchResult> = emptyMap(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val history: List<String> = emptyList(),
    val tagSuggestions: List<Pair<String, String>> = emptyList(),
    val tags: List<SearchTag> = emptyList(),
    val matchedUrlComic: ComicUrlTable.MatchedComic? = null,
    val optionsLoading: Boolean = false,
    val optionsError: String? = null,
    val error: String? = null,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    /**
     * 源自己声明的总页数。null = 该源不声明（游标型 `loadNext` 源，或压根不返回 maxPage 的源）。
     * 实测 33 个源里 26 个声明、7 个不声明。
     */
    val sourceMaxPage: Int? = null,
    /**
     * 「约 N 个」= [sourceMaxPage] × 首页条数。
     *
     * 刻意带「约」：33 个源里 0 个往外返回条目总数（jm 内部有 `data.total`，但它的
     * `search.load` 只返回 maxPage），所以只能估算，且**只会高估不会低估**
     * （jm 是 `ceil(total/80)`，误差上限一个页长）。源没声明时保持 null，UI 显示「未知」。
     */
    val estimatedTotal: Int? = null,
    /**
     * true = 所选标签在**这一批结果里**一个都对不上（多数源卡片不带完整标签，
     * 或源用繁体而用户选了简体），严格 AND 过滤会把整页清空；于是退回未过滤结果。
     * UI 必须据此说明"下面的结果没有按所选标签精确过滤"，不能装作过滤生效了。
     */
    val tagFilterRelaxed: Boolean = false
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = BusinessPorts.of(app).stores.open(PREFS)

    /**
     * 客户端标签过滤要吃**同一张**简繁字级表：源里繁简混写时，简体标签必须能认繁体卡片标签，
     * 否则 AND 交集为空、整页降级成「未过滤」。表在构造时异步自加载，没就绪前是恒等转换。
     */
    private val variantConverter = ChineseVariantConverter.getInstance(app)
    private val metricsCache = com.venera.compose.data.prefs.ComicMetricsCache(app)
    private val sourceManager by lazy { ComicSourceManager.getInstance(app) }
    private val tagManager by lazy { TagTranslationManager.getInstance(app) }
    private val guardManager: ContentGuard = BusinessPorts.of(app).contentGuard
    private var searchJob: Job? = null
    private var debounceJob: Job? = null
    private var optionsJob: Job? = null
    private val optionsMutex = Mutex()
    private val optionsStore = SearchOptionsStore()
    private var loadedSourceKey: String? = null
    private var currentPage = 1

    /**
     * 「只在进页时做一次」的动作的去重表（源预选、标签/关键词下钻）。
     *
     * 搜索页的组合会在「进详情再返回」时被整体销毁重建（导航条目行为），届时的
     * `LaunchedEffect(initialQuery, initialSourceName, initialTag)` 会重新执行 ——
     * 用户看到的就是「返回时整个搜索页又刷了一遍，还闪回加载态」。标志放在 ViewModel
     * 里（与导航条目同生命周期，不随组合销毁），所以入参只认第一次。
     */
    private val entryParamsApplied = mutableSetOf<String>()
    fun shouldApplyEntryParam(key: String): Boolean = entryParamsApplied.add(key)

    /**
     * 结果列表的滚动锚点（firstVisibleItemIndex + 项内偏移）。
     *
     * 组合销毁重建时 `rememberLazyListState()` 没有恢复来源（导航条目没开 saveState），
     * 不把它记在比组合更长命的地方，返回时就会跳回顶部 —— 那也是「搜索页被刷新」观感的一部分。
     */
    var listAnchor: Pair<Int, Int>? = null

    val sourcesFlow = sourceManager.sourcesFlow
    private val _uiState = MutableStateFlow(SearchUiState(history = readHistory()))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private val _searchOptions = MutableStateFlow<List<SearchOptionGroup>>(emptyList())
    val searchOptions = _searchOptions.asStateFlow()
    private val _selectedOptions = MutableStateFlow<List<String?>>(emptyList())
    val selectedOptions = _selectedOptions.asStateFlow()

    /**
     * 「识别到漫画链接」这张卡只在**本机装着那个源**时才出现。
     *
     * 双向表里有条目（哔哩哔哩漫画）在本仓库压根没有对应源。照旧显示的话，点下去会走
     * `ComicDetailViewModel.resolveSourceKey` 的回落（名字匹配不上就用当前活动源），
     * 等于拿另一个源的同 id 漫画开详情、且零提示 —— 那是静默交错，不是降级。
     * 与入站链接那侧（ComicLinkResolver.SourceMissing）同一判据。
     */
    private fun matchedInstalled(input: String): ComicUrlTable.MatchedComic? =
        ComicUrlTable.match(input)?.takeIf { m ->
            sourcesFlow.value.any {
                it.key.equals(m.sourceKey, ignoreCase = true) || it.name.equals(m.sourceName, ignoreCase = true)
            }
        }

    fun suggestTags(value: String) = tagManager.suggestTags(value, limit = 12)

    fun onQueryChange(value: String) {
        debounceJob?.cancel()
        searchJob?.cancel()
        val matched = matchedInstalled(value)
        _uiState.update { it.copy(query = value, matchedUrlComic = matched,
            tagSuggestions = if (matched == null) suggestTags(value) else emptyList(),
            isSearching = false, hasSearched = false, error = null,
            results = emptyList(), aggregatedResults = emptyMap()) }
        if ((value.length >= MIN_QUERY || _uiState.value.tags.isNotEmpty()) && matched == null) {
            debounceJob = viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                search(value)
            }
        }
    }

    fun addTag(raw: String, label: String = raw, namespace: String = "") {
        if (raw.isBlank()) return
        val tag = SearchTag(namespace.trim(), raw.trim(), label.trim())
        _uiState.update { state -> state.copy(tags = (state.tags + tag).distinctBy { it.namespace to it.raw }, tagSuggestions = emptyList()) }
        search(_uiState.value.query)
    }

    /**
     * 点击联想标签：优先走源级 `onTagSuggestionSelected` 专用转换规则，
     * 源未声明（返回 null/空）时退化为「标签作为检索条件加入」。
     */
    fun selectTagSuggestion(raw: String, label: String, namespace: String = "") {
        viewModelScope.launch {
            val currentKey = _uiState.value.selectedSourceKey
            val source = if (currentKey != KEY_ALL) {
                sourceManager.getSource(currentKey) as? com.venera.compose.source.js.JsComicSource
            } else null
            val mappedKeyword = source?.onTagSuggestionSelected(namespace, raw)
            if (!mappedKeyword.isNullOrBlank()) {
                // 源提供了专用转换规则（如 Hitomi 的 "series:xxx" / "type:xxx"）：
                // 直接把转换结果作为查询词并立刻检索。
                _uiState.update {
                    it.copy(
                        query = mappedKeyword,
                        tagSuggestions = emptyList(),
                        matchedUrlComic = null,
                        results = emptyList(),
                        aggregatedResults = emptyMap(),
                        hasSearched = false,
                    )
                }
                search(mappedKeyword)
            } else {
                addTag(raw = raw, label = label, namespace = namespace)
            }
        }
    }

    fun removeTag(index: Int) {
        _uiState.update { it.copy(tags = it.tags.filterIndexed { i, _ -> i != index }) }
        if (_uiState.value.query.isNotBlank() || _uiState.value.tags.isNotEmpty()) search(_uiState.value.query)
        else clearQuery()
    }

    fun onSourceSelected(key: String, label: String) {
        if (key == _uiState.value.selectedSourceKey) return
        debounceJob?.cancel()
        searchJob?.cancel()
        optionsJob?.cancel()
        loadedSourceKey = null
        _searchOptions.value = emptyList()
        _selectedOptions.value = emptyList()
        _uiState.update { it.copy(selectedSourceKey = key, selectedSourceLabel = label,
            results = emptyList(), aggregatedResults = emptyMap(), isSearching = false,
            hasSearched = false, optionsError = null, optionsLoading = key != KEY_ALL) }
        loadSearchOptions(key)
        if (_uiState.value.query.isNotBlank() || _uiState.value.tags.isNotEmpty()) search(_uiState.value.query)
    }

    fun clearQuery() {
        debounceJob?.cancel()
        searchJob?.cancel()
        _uiState.update { it.copy(query = "", tags = emptyList(), results = emptyList(),
            aggregatedResults = emptyMap(), isSearching = false, hasSearched = false,
            matchedUrlComic = null, tagSuggestions = emptyList(), error = null) }
    }

    fun loadSearchOptions(sourceKey: String) {
        optionsJob?.cancel()
        optionsJob = viewModelScope.launch {
            try { ensureOptions(sourceKey) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (_uiState.value.selectedSourceKey == sourceKey) {
                    _uiState.update { it.copy(optionsLoading = false, optionsError = "筛选加载失败：${e.message ?: "未知错误"}") }
                }
            }
        }
    }

    private suspend fun ensureOptions(sourceKey: String) = optionsMutex.withLock {
        if (_uiState.value.selectedSourceKey != sourceKey || loadedSourceKey == sourceKey) return@withLock
        if (sourceKey == KEY_ALL) {
            _searchOptions.value = emptyList()
            _selectedOptions.value = emptyList()
            loadedSourceKey = sourceKey
            _uiState.update { it.copy(optionsLoading = false, optionsError = null) }
            return@withLock
        }
        _uiState.update { it.copy(optionsLoading = true, optionsError = null) }
        val source = sourceManager.getSource(sourceKey) ?: error("漫画源不可用")
        val groups = source.getSearchOptions()
        coroutineContext.ensureActive()
        if (_uiState.value.selectedSourceKey == sourceKey) {
            _searchOptions.value = groups
            _selectedOptions.value = optionsStore.load(sourceKey, groups)
            loadedSourceKey = sourceKey
            _uiState.update { it.copy(optionsLoading = false, optionsError = null) }
        }
    }

    /** Dialog changes remain local until Apply; Cancel cannot change requests. */
    fun applySearchOptions(sourceKey: String, values: List<String?>) {
        if (sourceKey != _uiState.value.selectedSourceKey || loadedSourceKey != sourceKey) return
        _selectedOptions.value = optionsStore.apply(sourceKey, _searchOptions.value, values)
        if (_uiState.value.query.isNotBlank() || _uiState.value.tags.isNotEmpty()) search(_uiState.value.query)
    }

    fun search(query: String) {
        debounceJob?.cancel()
        val snapshot = _uiState.value
        if (query.isBlank() && snapshot.tags.isEmpty()) {
            clearQuery()
            return
        }
        searchJob?.cancel()
        val matched = matchedInstalled(query)
        if (matched != null) {
            _uiState.update { it.copy(query = query, matchedUrlComic = matched, isSearching = false) }
            return
        }
        if (query.isNotBlank()) appendHistory(query)
        val currentKey = snapshot.selectedSourceKey
        currentPage = 1
        _uiState.update { it.copy(query = query, isSearching = true, hasSearched = true,
            results = emptyList(), aggregatedResults = emptyMap(), error = null,
            sourceMaxPage = null, estimatedTotal = null, canLoadMore = false,
            tagFilterRelaxed = false,
            tagSuggestions = emptyList()) }
        searchJob = viewModelScope.launch {
            try {
                if (currentKey == KEY_ALL) {
                    val targets = sourceManager.searchTargets()
                    _uiState.update { it.copy(aggregatedResults = targets.associate { source ->
                        source.key to ComicSourceManager.SourceSearchResult(source.key, source.name, emptyList(), isLoading = true)
                    }) }
                    val semaphore = Semaphore(4)
                    coroutineScope {
                        targets.forEach { source -> launch {
                            val event = try {
                                semaphore.withPermit { withContext(Dispatchers.IO) { withTimeout(30_000L) {
                                    val request = TagSearchPolicy.requestKeyword(source.key, query, snapshot.tags, source::formatSearchTag)
                                    val defaults = SearchOptionValues.defaults(source.getSearchOptions()).takeIf { it.isNotEmpty() }
                                    if (request.isBlank() && snapshot.tags.isNotEmpty()) {
                                        // 非原生标签源会剥掉全部标签词、只留纯文本；纯文本也为空就等于
                                        // 拿 "" 去搜。单源分支早就拦了这一步，聚合分支漏了 —— 于是二十几个
                                        // 源各报一次空结果，用户看到的就是"什么都不显示"且没有原因。
                                        ComicSourceManager.SourceSearchResult(
                                            source.key, source.name, emptyList(), isLoading = false,
                                            error = "该源不支持纯标签搜索：请输入关键词，或改用 EHentai 等原生标签源。",
                                        )
                                    } else {
                                        val page = source.search(request, options = defaults).getOrThrow()
                                        val comics = page.comics
                                        coroutineContext.ensureActive()
                                        cacheMetrics(source.key, comics)
                                        // 原生标签源结果已由站方精确过滤；其余源在客户端按标签过滤。
                                        val filtered = if (TagSearchPolicy.isNativeTagSource(source.key)) {
                                            TagSearchPolicy.TagFilterOutcome(comics, relaxed = false)
                                        } else {
                                            TagSearchPolicy.filterByTagsWithFallback(
                                                comics, snapshot.tags,
                                                variantConverter::traditionalToSimplified)
                                        }
                                        ComicSourceManager.SourceSearchResult(
                                            source.key, source.name,
                                            guardManager.filterComics(filtered.comics),
                                            isLoading = false,
                                            tagFilterRelaxed = filtered.relaxed,
                                        )
                                    }
                                } } }
                            } catch (e: TimeoutCancellationException) {
                                ComicSourceManager.SourceSearchResult(source.key, source.name, error = "检索超时，请进入该源重试")
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { ComicSourceManager.SourceSearchResult(source.key, source.name, emptyList(), isLoading = false, error = e.message ?: "检索失败") }
                            coroutineContext.ensureActive()
                            _uiState.update { it.copy(aggregatedResults = it.aggregatedResults + (source.key to event)) }
                        } }
                    }
                    _uiState.update { it.copy(isSearching = false) }
                } else {
                    ensureOptions(currentKey)
                    val source = sourceManager.getSource(currentKey) ?: error("漫画源不可用")
                    val request = TagSearchPolicy.requestKeyword(currentKey, query, snapshot.tags, source::formatSearchTag)
                    if (request.isBlank() && snapshot.tags.isNotEmpty()) {
                        // 该源不认识标签语法且没有纯文本关键词：明确提示而不是静默空结果。
                        _uiState.update { it.copy(results = emptyList(), isSearching = false,
                            error = "当前源不支持标签搜索：请输入关键词，或选择 EHentai 等原生标签源。") }
                        return@launch
                    }
                    val result = sourceManager.search(currentKey, request, options = _selectedOptions.value.takeIf { it.isNotEmpty() })
                    coroutineContext.ensureActive()
                    val firstPage = result.getOrThrow()
                    val comics = firstPage.comics
                    cacheMetrics(source.key, comics)
                    val filtered = if (TagSearchPolicy.isNativeTagSource(currentKey)) {
                        TagSearchPolicy.TagFilterOutcome(comics, relaxed = false)
                    } else {
                        TagSearchPolicy.filterByTagsWithFallback(
                            comics, snapshot.tags, variantConverter::traditionalToSimplified)
                    }
                    _uiState.update { it.copy(results = guardManager.filterComics(filtered.comics), isSearching = false,
                        tagFilterRelaxed = filtered.relaxed,
                        // 判据与翻页那一路共用（见 SearchPagination）：首页就是空也算到底，
                        // 不能只看源声明的 maxPage —— 它常常比这个关键词真能翻到的页数大得多。
                        canLoadMore = SearchPagination.canLoadMore(
                            declaredMaxPage = firstPage.maxPage,
                            pageJustFetched = 1,
                            unseenItemsOnPage = comics.size,
                        ),
                        sourceMaxPage = firstPage.maxPage,
                        estimatedTotal = firstPage.maxPage
                            ?.takeIf { mp -> mp > 0 && comics.isNotEmpty() }
                            ?.let { mp -> mp * comics.size }) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                coroutineContext.ensureActive()
                _uiState.update { it.copy(isSearching = false, optionsLoading = false,
                    optionsError = if (loadedSourceKey != currentKey && currentKey != KEY_ALL) "筛选加载失败，可重试" else it.optionsError,
                    error = "检索异常：${e.message ?: "未知错误"}") }
            }
        }
    }

    private fun cacheMetrics(sourceKey: String, comics: List<Comic>) {
        comics.forEach { comic ->
            metricsCache.put(sourceKey, comic.id, comic.rating?.toDouble(), comic.likesCount)
        }
    }

    /**
     * 单源搜索的「加载更多」：关键词与筛选保持不变，页码 +1，结果追加到列表。
     * 聚合模式不支持翻页（各源默认选项 + 30s 超时，追加语义不成立）。
     */
    fun loadMore() {
        val snapshot = _uiState.value
        if (snapshot.selectedSourceKey == KEY_ALL || !snapshot.canLoadMore ||
            snapshot.isSearching || snapshot.loadingMore) return
        if (snapshot.query.isBlank() && snapshot.tags.isEmpty()) return
        searchJob?.cancel()
        val currentKey = snapshot.selectedSourceKey
        val nextPage = currentPage + 1
        _uiState.update { it.copy(loadingMore = true, error = null) }
        searchJob = viewModelScope.launch {
            try {
                ensureOptions(currentKey)
                val source = sourceManager.getSource(currentKey) ?: error("漫画源不可用")
                val request = TagSearchPolicy.requestKeyword(currentKey, snapshot.query, snapshot.tags, source::formatSearchTag)
                val result = sourceManager.search(currentKey, request, page = nextPage,
                    options = _selectedOptions.value.takeIf { it.isNotEmpty() })
                coroutineContext.ensureActive()
                val page = result.getOrThrow()
                val comics = page.comics
                cacheMetrics(source.key, comics)
                val filtered = if (TagSearchPolicy.isNativeTagSource(currentKey)) {
                    TagSearchPolicy.TagFilterOutcome(comics, relaxed = false)
                } else {
                    TagSearchPolicy.filterByTagsWithFallback(
                        comics, snapshot.tags, variantConverter::traditionalToSimplified)
                }
                // 先排重、再过屏蔽规则。顺序反过来的话，「这一页被 HIDE 规则剔空」就会被
                // 当成「这一页没有新条目」，进而误判成到底 —— 而源其实还有下一页。
                val unseen = filtered.comics.filter { next ->
                    _uiState.value.results.none { it.id == next.id && it.sourceKey == next.sourceKey }
                }
                val fresh = guardManager.filterComics(unseen)
                _uiState.update {
                    it.copy(results = it.results + fresh, loadingMore = false,
                        // 任一页触发过降级，提示就要一直留着 —— 否则用户以为后面的页是精确的。
                        tagFilterRelaxed = it.tagFilterRelaxed || filtered.relaxed,
                        canLoadMore = SearchPagination.canLoadMore(
                            declaredMaxPage = page.maxPage,
                            pageJustFetched = nextPage,
                            unseenItemsOnPage = unseen.size,
                        ))
                }
                if (comics.isNotEmpty()) currentPage = nextPage
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                coroutineContext.ensureActive()
                _uiState.update { it.copy(loadingMore = false, canLoadMore = false,
                    error = "加载更多失败：${e.message ?: "未知错误"}") }
            }
        }
    }

    fun clearHistory() {
        prefs.remove(KEY_HISTORY)
        _uiState.update { it.copy(history = emptyList()) }
    }

    private fun appendHistory(query: String) {
        val next = (listOf(query) + readHistory()).distinct().take(HISTORY_MAX)
        prefs.put(KEY_HISTORY, next.joinToString("\u001F"))
        _uiState.update { it.copy(history = next) }
    }

    private fun readHistory(): List<String> = prefs.getString(KEY_HISTORY, null)
        ?.split('\u001F')?.filter { it.isNotBlank() }.orEmpty()

    companion object {
        const val KEY_ALL = "all"
        const val SOURCE_ALL_LABEL = "全网聚合"
        private const val PREFS = "venera_search"
        private const val KEY_HISTORY = "history"
        private const val HISTORY_MAX = 20
        private const val MIN_QUERY = 2
        private const val SEARCH_DEBOUNCE_MS = 400L
    }
}
