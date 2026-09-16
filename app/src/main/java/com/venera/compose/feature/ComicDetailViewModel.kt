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
    val selectedGroupIndex: Int = 0,
    val comments: List<com.venera.compose.source.model.Comment> = emptyList(),
    val isCommentLoading: Boolean = false,
    val isLiked: Boolean = false,
    val likesCount: Int = 0,
    val userRating: Float = 0f
)

/** 一次性事件：打开阅读器（真链路 or 演示链路） */
sealed interface ReaderEvent {
    data class Live(val session: ReaderSession) : ReaderEvent
    data class Sample(val chapterIndex: Int, val pageIndex: Int) : ReaderEvent
}

/**
 * 详情页 ViewModel（S2 深度重构）。
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
    private var currentComicItem: ComicItem? = null

    /** 拉取真实详情（章节目录、分卷、推荐等） */
    fun load(comic: ComicItem) {
        currentComicItem = comic
        if (loadedId == comic.id && _uiState.value.details != null) return
        loadedId = comic.id

        val key = resolveSourceKey(comic.sourceName)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "正在拉取章节目录与元数据...", error = null) }
            val res = sourceManager.getComicDetails(key, comic.id)
            val d = res.getOrNull()
            if (d != null) {
                _uiState.update {
                    it.copy(
                        details = d,
                        isLoading = false,
                        loadingMessage = "",
                        error = null,
                        isLiked = d.isLiked,
                        likesCount = d.likesCount,
                        comments = d.comments
                    )
                }
                // 异步拉取全量评论
                loadComments(d.comic.id, d.subId, 1)
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        error = res.exceptionOrNull()?.let { e -> "详情加载失败：${e.message ?: e.javaClass.simpleName}" }
                    )
                }
            }
        }
    }

    fun selectGroup(index: Int) {
        _uiState.update { it.copy(selectedGroupIndex = index) }
    }

    fun setReversed(value: Boolean) = _uiState.update { it.copy(reversed = value) }

    /** 收藏/取消收藏 */
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

    /** 喜欢/点赞漫画 */
    fun toggleLike() {
        val details = _uiState.value.details ?: return
        val key = resolveSourceKey(details.sourceKey)
        val currentLiked = _uiState.value.isLiked
        val newCount = if (currentLiked) _uiState.value.likesCount - 1 else _uiState.value.likesCount + 1
        _uiState.update { it.copy(isLiked = !currentLiked, likesCount = newCount.coerceAtLeast(0)) }

        viewModelScope.launch {
            val src = sourceManager.getSource(key)
            src?.likeComic(details.comic.id)
        }
    }

    /** 评分 (0.0 - 5.0) */
    fun rateComic(rating: Float) {
        val details = _uiState.value.details ?: return
        val key = resolveSourceKey(details.sourceKey)
        _uiState.update { it.copy(userRating = rating) }

        viewModelScope.launch {
            val src = sourceManager.getSource(key)
            src?.starRating(details.comic.id, rating)
        }
    }

    /** 拉取评论 */
    fun loadComments(comicId: String? = null, subId: String? = null, page: Int = 1) {
        val details = _uiState.value.details ?: return
        val targetComicId = comicId ?: details.comic.id
        val targetSubId = subId ?: details.subId
        val key = resolveSourceKey(details.sourceKey)

        viewModelScope.launch {
            _uiState.update { it.copy(isCommentLoading = true) }
            val src = sourceManager.getSource(key)
            val res = src?.loadComments(targetComicId, targetSubId, page)
            val commentList = res?.getOrNull().orEmpty()
            _uiState.update {
                it.copy(
                    isCommentLoading = false,
                    comments = if (commentList.isNotEmpty()) commentList else it.comments
                )
            }
        }
    }

    /** 发送评论 */
    fun sendComment(content: String, onComplete: (Boolean, String?) -> Unit) {
        val details = _uiState.value.details ?: return
        val key = resolveSourceKey(details.sourceKey)

        viewModelScope.launch {
            val src = sourceManager.getSource(key)
            val res = src?.sendComment(details.comic.id, details.subId, content)
            if (res != null && res.isSuccess) {
                onComplete(true, null)
                loadComments(page = 1)
            } else {
                onComplete(false, res?.exceptionOrNull()?.message ?: "发表失败")
            }
        }
    }

    /** 解析一章的真实图片地址 */
    fun openChapter(comic: ComicItem, chapterId: String, chapterTitle: String, fallbackIdx: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMessage = "正在解析章节画质...") }
            val key = resolveSourceKey(comic.sourceName)
            val res = sourceManager.getChapterPages(key, comic.id, chapterId)
            val pagesData = res.getOrNull()
            val pages = pagesData?.pages.orEmpty()
            _uiState.update { it.copy(loadingMessage = "") }

            // 防盗链头交给图片加载策略
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

    private fun resolveSourceKey(sourceNameOrKey: String): String {
        return sourceManager.sourcesFlow.value.find {
            it.key.equals(sourceNameOrKey, ignoreCase = true) || it.name.equals(sourceNameOrKey, ignoreCase = true)
        }?.key ?: sourceManager.activeSourceKey.value
    }
}
