package com.venera.compose.feature

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.db.FavoriteDao
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.network.ImageHeaderPolicy
import com.venera.compose.reader.ReaderSession
import com.venera.compose.reader.SampleReaderData
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.ComicDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 详情页 UI 状态 */
data class DetailUiState(
    val details: ComicDetails? = null,
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val reversed: Boolean = false,
    val error: String? = null,
)

/** 一次性事件：打开阅读器（真链路 or 演示链路） */
sealed interface ReaderEvent {
    data class Live(val session: ReaderSession) : ReaderEvent
    data class Sample(val chapterIndex: Int, val pageIndex: Int) : ReaderEvent
}

/**
 * 详情页 ViewModel（S0-4）。
 *
 * 之前 getComicDetails / getChapterPages 跑在 rememberCoroutineScope 里，
 * 且 loadingMessage、liveDetails、章节反转都是 Composable 内的 remember 状态：
 * 旋转会中断请求、离开页面也停不掉。现在请求归 viewModelScope，
 * 状态归 StateFlow，打开阅读器用事件流下发。
 */
class ComicDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val sourceManager = ComicSourceManager.getInstance(app)
    private val favoriteDao = FavoriteDao.getInstance(app)
    private val historyDao = HistoryDao.getInstance(app)

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _readerEvents = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 4)
    val readerEvents: SharedFlow<ReaderEvent> = _readerEvents.asSharedFlow()

    val favoritesFlow by lazy { favoriteDao.favoritesFlow }
    val historyFlow by lazy { historyDao.historyFlow }

    private var loadedId: String? = null

    /** 拉取真实详情（章节目录等）。同一本漫画重复进入不会二次请求。 */
    fun load(comic: ComicItem) {
        val key = sourceKeyOf(comic.sourceName)
        val needFetch = comic.chapters.isEmpty() || comic.sourceName in FETCHABLE_SOURCES
        if (!needFetch || loadedId == comic.id) return
        loadedId = comic.id
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "正在拉取章节目录...", error = null) }
            val res = sourceManager.getComicDetails(key, comic.id)
            _uiState.update {
                it.copy(
                    details = res.getOrNull() ?: it.details,
                    isLoading = false,
                    loadingMessage = "",
                    error = res.exceptionOrNull()?.let { e -> "章节加载失败：${e.message ?: e.javaClass.simpleName}" },
                )
            }
        }
    }

    fun setReversed(value: Boolean) = _uiState.update { it.copy(reversed = value) }

    /** 收藏/取消收藏（FavoriteDao.toggleFavorite 本身是同步写库，放到 IO 线程执行） */
    fun toggleFavorite(comic: ComicItem) {
        viewModelScope.launch(Dispatchers.IO) {
            favoriteDao.toggleFavorite(
                comicId = comic.id,
                title = comic.title,
                coverUrl = comic.coverUrl,
                author = comic.author,
                sourceName = comic.sourceName,
                latestChapter = comic.latestChapter,
            )
        }
    }

    /** 解析一章的真实图片地址；失败或源不支持时退回演示章节 */
    fun openChapter(comic: ComicItem, chapterId: String, chapterTitle: String, fallbackIdx: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMessage = "正在解析章节画质...") }
            val res = sourceManager.getChapterPages(sourceKeyOf(comic.sourceName), comic.id, chapterId)
            val pagesData = res.getOrNull()
            val pages = pagesData?.pages.orEmpty()
            _uiState.update { it.copy(loadingMessage = "") }
            // 防盗链头交给图片加载策略（S0-2 接的管道）
            pagesData?.headers?.takeIf { it.isNotEmpty() }?.let { hdrs ->
                ImageHeaderPolicy.publishForUrls(pages, hdrs)
                if (comic.coverUrl.isNotEmpty()) ImageHeaderPolicy.publishForUrls(listOf(comic.coverUrl), hdrs)
            }
            if (pages.isNotEmpty()) {
                _readerEvents.tryEmit(
                    ReaderEvent.Live(
                        SampleReaderData.createLiveSession(
                            comicId = comic.id,
                            comicTitle = comic.title,
                            coverUrl = comic.coverUrl,
                            chapterId = chapterId,
                            chapterTitle = chapterTitle,
                            pages = pages,
                        ),
                    ),
                )
            } else {
                _readerEvents.tryEmit(ReaderEvent.Sample(fallbackIdx, 0))
            }
        }
    }

    companion object {
        private val FETCHABLE_SOURCES = listOf("MangaDex", "拷贝漫画", "包子漫画")

        fun sourceKeyOf(sourceName: String): String = when (sourceName) {
            "MangaDex" -> "manga_dex"
            "拷贝漫画" -> "copy_manga"
            "包子漫画" -> "baozi"
            else -> "manga_dex"
        }
    }
}
