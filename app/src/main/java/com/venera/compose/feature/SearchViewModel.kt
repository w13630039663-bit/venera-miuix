package com.venera.compose.feature

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.network.ComicUrlMatcher
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
    val matchedUrlComic: ComicUrlMatcher.MatchedComic? = null,
    val optionsLoading: Boolean = false,
    val optionsError: String? = null,
    val error: String? = null,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val metricsCache = com.venera.compose.data.prefs.ComicMetricsCache(app)
    private val sourceManager by lazy { ComicSourceManager.getInstance(app) }
    private val tagManager by lazy { TagTranslationManager.getInstance(app) }
    private val guardManager = com.venera.compose.security.guard.ContentGuardManager.getInstance(app)
    private var searchJob: Job? = null
    private var debounceJob: Job? = null
    private var optionsJob: Job? = null
    private val optionsMutex = Mutex()
    private val optionsStore = SearchOptionsStore()
    private var loadedSourceKey: String? = null
    private var currentPage = 1
    val sourcesFlow = sourceManager.sourcesFlow
    private val _uiState = MutableStateFlow(SearchUiState(history = readHistory()))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private val _searchOptions = MutableStateFlow<List<SearchOptionGroup>>(emptyList())
    val searchOptions = _searchOptions.asStateFlow()
    private val _selectedOptions = MutableStateFlow<List<String?>>(emptyList())
    val selectedOptions = _selectedOptions.asStateFlow()

    fun suggestTags(value: String) = tagManager.suggestTags(value, limit = 12)

    fun onQueryChange(value: String) {
        debounceJob?.cancel()
        searchJob?.cancel()
        val matched = ComicUrlMatcher.match(value)
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
        val matched = ComicUrlMatcher.match(query)
        if (matched != null) {
            _uiState.update { it.copy(query = query, matchedUrlComic = matched, isSearching = false) }
            return
        }
        if (query.isNotBlank()) appendHistory(query)
        val currentKey = snapshot.selectedSourceKey
        currentPage = 1
        _uiState.update { it.copy(query = query, isSearching = true, hasSearched = true,
            results = emptyList(), aggregatedResults = emptyMap(), error = null, tagSuggestions = emptyList()) }
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
                                    val comics = source.search(request, options = defaults).getOrThrow()
                                    coroutineContext.ensureActive()
                                    cacheMetrics(source.key, comics)
                                    // 原生标签源结果已由站方精确过滤；其余源在客户端按标签过滤。
                                    val tagFiltered = if (TagSearchPolicy.isNativeTagSource(source.key)) comics
                                        else TagSearchPolicy.filterByTags(comics, snapshot.tags)
                                    ComicSourceManager.SourceSearchResult(source.key, source.name, guardManager.filterComicModels(tagFiltered), isLoading = false)
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
                    val comics = result.getOrThrow()
                    cacheMetrics(source.key, comics)
                    val tagFiltered = if (TagSearchPolicy.isNativeTagSource(currentKey)) comics
                        else TagSearchPolicy.filterByTags(comics, snapshot.tags)
                    _uiState.update { it.copy(results = guardManager.filterComicModels(tagFiltered), isSearching = false,
                        canLoadMore = comics.isNotEmpty()) }
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
                val comics = result.getOrThrow()
                cacheMetrics(source.key, comics)
                val tagFiltered = if (TagSearchPolicy.isNativeTagSource(currentKey)) comics
                    else TagSearchPolicy.filterByTags(comics, snapshot.tags)
                val fresh = guardManager.filterComicModels(tagFiltered)
                    .filter { next -> _uiState.value.results.none { it.id == next.id && it.sourceKey == next.sourceKey } }
                _uiState.update {
                    it.copy(results = it.results + fresh, loadingMore = false,
                        canLoadMore = comics.isNotEmpty())
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
        prefs.edit().remove(KEY_HISTORY).apply()
        _uiState.update { it.copy(history = emptyList()) }
    }

    private fun appendHistory(query: String) {
        val next = (listOf(query) + readHistory()).distinct().take(HISTORY_MAX)
        prefs.edit().putString(KEY_HISTORY, next.joinToString("\u001F")).apply()
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
