package com.venera.compose.feature

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.Comic
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 搜索页 UI 状态（S0-4：状态从 Composable 搬到 ViewModel，旋转/重组不再重跑网络） */
data class SearchUiState(
    val query: String = "",
    val selectedSource: String = SearchViewModel.SOURCE_ALL_LABEL,
    val results: List<Comic> = emptyList(),
    val isSearching: Boolean = false,
    val history: List<String> = emptyList(),
    val error: String? = null,
)

/**
 * 搜索页 ViewModel。
 *
 * 与旧实现的差别：
 * 1. 网络跑在 viewModelScope：屏幕旋转或重组不会中断请求，也不会重复触发；
 * 2. 搜索历史落到 SharedPreferences（原来是一串写死的假历史 One/Frieren/…）；
 * 3. 失败不再被静默吞成空列表，error 会带到界面上。
 *
 * 注意：这里仍是「单源一次查询」，S4 才换成逐源流式（channelFlow）聚合。
 */
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val sourceManager by lazy { ComicSourceManager.getInstance(app) }
    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow(SearchUiState(history = readHistory()))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        if (value.length >= MIN_QUERY) search(value)
        else _uiState.update { it.copy(results = emptyList()) }
    }

    fun onSourceSelected(label: String) {
        _uiState.update { it.copy(selectedSource = label) }
        _uiState.value.query.takeIf { it.length >= MIN_QUERY }?.let { search(it) }
    }

    fun clearQuery() {
        searchJob?.cancel()
        _uiState.update { it.copy(query = "", results = emptyList(), isSearching = false, error = null) }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        appendHistory(query)
        _uiState.update { it.copy(isSearching = true, error = null) }
        searchJob?.cancel()
        val label = _uiState.value.selectedSource
        searchJob = viewModelScope.launch {
            val res = sourceManager.search(sourceKeyOf(label), query)
            val message = res.exceptionOrNull()?.let { err -> "检索失败：${err.message ?: err.javaClass.simpleName}" }
            _uiState.update { it.copy(results = res.getOrDefault(emptyList()), isSearching = false, error = message) }
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
        const val SOURCE_MANGA_DEX = "MangaDex"
        const val SOURCE_COPY_MANGA = "拷贝漫画"
        const val SOURCE_BAOZI = "包子漫画"
        const val SOURCE_ALL_LABEL = "全网聚合"
        val SOURCES = listOf(SOURCE_MANGA_DEX, SOURCE_COPY_MANGA, SOURCE_BAOZI, SOURCE_ALL_LABEL)

        private const val PREFS = "venera_search"
        private const val KEY_HISTORY = "history"
        private const val HISTORY_MAX = 20
        private const val MIN_QUERY = 2

        /** 界面标签 -> 源 key；"all" 交给 ComicSourceManager 聚合 */
        fun sourceKeyOf(label: String): String = when (label) {
            SOURCE_MANGA_DEX -> "manga_dex"
            SOURCE_COPY_MANGA -> "copy_manga"
            SOURCE_BAOZI -> "baozi"
            else -> "all"
        }
    }
}
