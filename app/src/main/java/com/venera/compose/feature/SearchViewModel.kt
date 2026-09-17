package com.venera.compose.feature

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.network.ComicUrlMatcher
import com.venera.compose.data.tags.TagTranslationManager
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.Comic
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchSortBy(val label: String) {
    DEFAULT("默认排序"),
    TITLE("作品名称"),
    AUTHOR("作者/艺术家")
}

data class SearchUiState(
    val query: String = "",
    val selectedSourceKey: String = SearchViewModel.KEY_ALL,
    val selectedSourceLabel: String = SearchViewModel.SOURCE_ALL_LABEL,
    val results: List<Comic> = emptyList(),
    val aggregatedResults: Map<String, ComicSourceManager.SourceSearchResult> = emptyMap(),
    val isSearching: Boolean = false,
    val history: List<String> = emptyList(),
    val tagSuggestions: List<Pair<String, String>> = emptyList(),
    val matchedUrlComic: ComicUrlMatcher.MatchedComic? = null,
    val sortBy: SearchSortBy = SearchSortBy.DEFAULT,
    val error: String? = null
)

/**
 * 工业级搜索与全网聚合 ViewModel (S4 核心重构)
 *
 * 核心能力：
 * 1. 全网聚合搜索流式响应 (channelFlow 逐源极速下发，告别慢源卡顿)
 * 2. 规则源动态发现与分类切换 (内置源 + 所有 S1 注册的 JS 规则源)
 * 3. 漫画链接 URL 自动识别与直达 (识别拷贝/包子/MangaDex等链接一键打开)
 * 4. 亿级标签翻译与实时输入联想 (基于 1MB tags.json 字典)
 * 5. 搜索历史持久化存储与一键清空
 */
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val sourceManager by lazy { ComicSourceManager.getInstance(app) }
    private val tagManager by lazy { TagTranslationManager.getInstance(app) }
    private var searchJob: Job? = null
    private var debounceJob: Job? = null

    val sourcesFlow = sourceManager.sourcesFlow

    // S7 内容守卫：搜索结果统一过屏蔽规则（单源流与全网聚合流共用）
    private val appContext = getApplication<Application>()
    private val guardManager = com.venera.compose.security.guard.ContentGuardManager.getInstance(appContext)

    private val _uiState = MutableStateFlow(SearchUiState(history = readHistory()))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(value: String) {
        val matchedUrl = ComicUrlMatcher.match(value)
        val suggestions = if (value.isNotBlank() && matchedUrl == null) {
            tagManager.suggestTags(value, limit = 8)
        } else emptyList()

        _uiState.update {
            it.copy(
                query = value,
                matchedUrlComic = matchedUrl,
                tagSuggestions = suggestions
            )
        }

        // 输入防抖：旧实现在 length>=2 时**每敲一个字符**就立即发起一次全网聚合搜索，
        // 33 源 × 每次击键 = 巨量请求排队，队列永远排不完，表现为"搜不到"。
        debounceJob?.cancel()
        if (value.length >= MIN_QUERY && matchedUrl == null) {
            debounceJob = viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                search(value)
            }
        } else if (value.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), aggregatedResults = emptyMap()) }
        }
    }

    fun onSourceSelected(key: String, label: String) {
        _uiState.update { it.copy(selectedSourceKey = key, selectedSourceLabel = label) }
        _uiState.value.query.takeIf { it.length >= MIN_QUERY }?.let { search(it) }
    }

    fun setSortBy(sortBy: SearchSortBy) {
        _uiState.update { state ->
            val sortedList = when (sortBy) {
                SearchSortBy.DEFAULT -> state.results
                SearchSortBy.TITLE -> state.results.sortedBy { it.title }
                SearchSortBy.AUTHOR -> state.results.sortedBy { it.subTitle }
            }
            state.copy(sortBy = sortBy, results = sortedList)
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                results = emptyList(),
                aggregatedResults = emptyMap(),
                isSearching = false,
                matchedUrlComic = null,
                tagSuggestions = emptyList(),
                error = null
            )
        }
    }

    // ==================== S8 批次B：搜索筛选（对齐官方 _SearchSettingsDialog） ====================

    /** 当前源声明的搜索筛选组（空 = 该源无筛选） */
    private val _searchOptions = MutableStateFlow<List<com.venera.compose.source.model.SearchOptionGroup>>(emptyList())
    val searchOptions: StateFlow<List<com.venera.compose.source.model.SearchOptionGroup>> = _searchOptions.asStateFlow()

    /** 当前选中的筛选值（索引与 searchOptions 对齐；空串 = 用该组默认） */
    private val _selectedOptions = MutableStateFlow<List<String>>(emptyList())
    val selectedOptions: StateFlow<List<String>> = _selectedOptions.asStateFlow()

    /** 源切换或首次进入时加载筛选组定义 */
    fun loadSearchOptions(sourceKey: String) {
        viewModelScope.launch {
            val source = sourceManager.getSourceOrFallback(sourceKey) ?: run {
                _searchOptions.value = emptyList()
                _selectedOptions.value = emptyList()
                return@launch
            }
            val groups = source.getSearchOptions()
            _searchOptions.value = groups
            // 保留用户已选值；组数变化时重置为默认
            _selectedOptions.value = if (_selectedOptions.value.size != groups.size) {
                groups.map { g -> _selectedOptions.value.getOrNull(groups.indexOf(g)) ?: g.defaultKey }
            } else {
                _selectedOptions.value
            }
        }
    }

    fun setSearchOption(groupIndex: Int, value: String) {
        val current = _selectedOptions.value.toMutableList()
        while (current.size <= groupIndex) current.add("")
        current[groupIndex] = value
        _selectedOptions.value = current
    }

    fun search(query: String) {
        if (query.isBlank()) return
        debounceJob?.cancel()
        appendHistory(query)
        val currentKey = _uiState.value.selectedSourceKey
        val activeOptions = _selectedOptions.value.takeIf { it.any { v -> v.isNotBlank() } }

        val matchedUrl = ComicUrlMatcher.match(query)
        if (matchedUrl != null) {
            _uiState.update { it.copy(matchedUrlComic = matchedUrl) }
            return
        }

        searchJob?.cancel()
        _uiState.update { it.copy(isSearching = true, error = null, tagSuggestions = emptyList()) }

        searchJob = viewModelScope.launch {
            if (currentKey == KEY_ALL) {
                // ==================== 全网聚合流式下发 ====================
                // 只对**已启用**的源建骨架屏，禁用源不参与检索
                val availableSources = sourceManager.searchTargets()
                val initialMap = availableSources.associate { src ->
                    src.key to ComicSourceManager.SourceSearchResult(
                        sourceKey = src.key,
                        sourceName = src.name,
                        comics = emptyList(),
                        isLoading = true
                    )
                }
                _uiState.update { it.copy(aggregatedResults = initialMap, results = emptyList()) }

                sourceManager.searchAggregatedStream(query).collect { event ->
                    // 每个源的流式结果在进入 UI 前剔除命中屏蔽规则的条目
                    val filteredEvent = if (event.comics.isNotEmpty()) {
                        event.copy(comics = guardManager.filterComicModels(event.comics))
                    } else event
                    _uiState.update { state ->
                        val updated = state.aggregatedResults.toMutableMap()
                        updated[filteredEvent.sourceKey] = filteredEvent
                        state.copy(aggregatedResults = updated)
                    }
                }
                _uiState.update { it.copy(isSearching = false) }
            } else {
                // ==================== 单源完整检索 ====================
                val res = sourceManager.search(currentKey, query, options = activeOptions)
                val list = guardManager.filterComicModels(res.getOrDefault(emptyList()))
                val message = res.exceptionOrNull()?.let { err -> "检索异常：${err.message ?: err.javaClass.simpleName}" }
                _uiState.update {
                    it.copy(
                        results = list,
                        aggregatedResults = emptyMap(),
                        isSearching = false,
                        error = message
                    )
                }
            }
        }
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
        _uiState.update { it.copy(history = emptyList()) }
    }

    private fun appendHistory(query: String) {
        val current = readHistory()
        if (current.firstOrNull() == query) return
        val next = (listOf(query) + current).distinct().take(HISTORY_MAX)
        prefs.edit().putString(KEY_HISTORY, next.joinToString("\u001F")).apply()
        _uiState.update { it.copy(history = next) }
    }

    private fun readHistory(): List<String> =
        prefs.getString(KEY_HISTORY, null)
            ?.split('\u001F')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    companion object {
        const val KEY_ALL = "all"
        const val SOURCE_ALL_LABEL = "全网聚合"

        private const val PREFS = "venera_search"
        private const val KEY_HISTORY = "history"
        private const val HISTORY_MAX = 20
        private const val MIN_QUERY = 2

        /** 输入防抖窗口（毫秒） */
        private const val SEARCH_DEBOUNCE_MS = 400L
    }
}
