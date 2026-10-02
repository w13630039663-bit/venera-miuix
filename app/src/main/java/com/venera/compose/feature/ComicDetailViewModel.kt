package com.venera.compose.feature

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.network.ImageHeaderPolicy
import com.venera.compose.data.prefs.ComicMetricsCache
import com.venera.compose.reader.ReaderSession
import com.venera.compose.reader.ReaderSessionFactory
import com.venera.compose.source.model.ComicDetails
import com.venera.compose.source.model.Comment
import com.venera.compose.source.model.CommentCapabilities
import com.venera.compose.source.model.ResolvedThumbnailConfig
import com.venera.compose.data.api.BusinessPorts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.venera.compose.data.api.ComicContentApi
import com.venera.compose.data.api.SourceCatalog

/** Root comments and reply threads keep independent cursors and errors. */
data class DetailCommentState(
    val items: List<Comment> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val requestedPage: Int = 1,
    val maxPage: Int? = null,
    val hasMore: Boolean = true,
    val loaded: Boolean = false,
) {
    fun received(comments: List<Comment>, requestedPage: Int, maximumPage: Int?): DetailCommentState = copy(
        items = if (requestedPage == 1) comments else items + comments,
        isLoading = false,
        error = null,
        page = requestedPage,
        maxPage = maximumPage ?: if (requestedPage == 1) null else maxPage,
        hasMore = comments.isNotEmpty() && requestedPage < (maximumPage ?: if (requestedPage == 1) Int.MAX_VALUE else maxPage ?: Int.MAX_VALUE),
        loaded = true,
    )
}

/** 详情页 UI 状态 */
data class DetailUiState(
    val details: ComicDetails? = null,
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val reversed: Boolean = false,
    val error: String? = null,
    val selectedGroupIndex: Int = 0,
    val commentThread: DetailCommentState = DetailCommentState(),
    val replyThread: DetailCommentState = DetailCommentState(),
    val replyTo: Comment? = null,
    val commentCapabilities: CommentCapabilities = CommentCapabilities(),
    val isSendingComment: Boolean = false,
    val isLiked: Boolean = false,
    val likesCount: Int = 0,
    val userRating: Float = 0f,
    /** 预览缩略图（源 `comic.loadThumbnails` 分页累计，或详情接口自带的首批）。 */
    val thumbnails: List<String> = emptyList(),
    val isLoadingThumbnails: Boolean = false,
    /** 还有下一页缩略图（末页置 false）。 */
    val hasMoreThumbnails: Boolean = false,
    /** 首屏就失败且一张图都没有时的错误（已有图时不打扰用户）。 */
    val thumbnailError: String? = null,
    /** 预览缩略图所属章节 ID（点击缩略图直接开读该章节该页）。 */
    val previewChapterId: String = "",
    /**
     * 预览图的原 url → 源声明的加载配置（`comic.onThumbnailLoad`）。
     *
     * 只覆盖**已换算过**的那些 url；缺项就按原样请求。源没实现该钩子时整张表为空，
     * 预览条的行为与接线前完全一致。
     */
    val thumbnailConfigs: Map<String, ResolvedThumbnailConfig> = emptyMap(),
    /**
     * 用户是否已点开「查看更多预览」。
     *
     * 与分页游标 [hasMoreThumbnails] **互相独立**：详情自带全量预览图的源
     * （nhentai 一次返回整本等）根本没有「下一页」，若只靠 [hasMoreThumbnails]
     * 判定，折叠后按钮会消失、用户再也展不开。
     */
    val thumbnailsExpanded: Boolean = false
) {
    /** 折叠态下实际渲染的预览图（最多 [PREVIEW_LIMIT] 张）。 */
    val visibleThumbnails: List<String>
        get() = if (thumbnailsExpanded) thumbnails else thumbnails.take(PREVIEW_LIMIT)

    /** 是否被折叠 —— 只表示「有图没显示出来」，与 [hasMoreThumbnails] 无关。 */
    val thumbnailsCollapsed: Boolean
        get() = !thumbnailsExpanded && thumbnails.size > PREVIEW_LIMIT

    val comments: List<Comment> get() = commentThread.items
    val isCommentLoading: Boolean get() = commentThread.isLoading
    val activeCommentThread: DetailCommentState get() = if (replyTo == null) commentThread else replyThread
}

/**
 * 预览图默认最多渲染的张数。
 *
 * 为什么要有这个上限：**详情接口自带全量预览图的源会一次返回整本**
 * （nhentai 的 `data.pages`、hitomi 等，动辄两三百张）。全量铺进详情页有两个
 * 实测代价：一次性发起几百个图片请求把带宽和内存吃光；详情页被预览图撑成
 * 一条几千 dp 的长列表，章节列表要翻很久才看得到。
 * 超出部分收进「查看更多预览」按钮，用户显式点击才展开。
 */
const val PREVIEW_LIMIT = 10

/**
 * 一次性事件：打开阅读器。
 *
 * 只存在真实链路 —— 章节图片解析不出来时如实报 [Failed]，
 * 不再回退到硬编码占位图（历史遗留的 `Sample` 事件已删除）。
 */
sealed interface ReaderEvent {
    data class Live(val session: ReaderSession) : ReaderEvent
    data class Failed(val message: String) : ReaderEvent
}

/**
 * 详情页收藏面板状态，对齐官方 `_FavoritePanel` / `_FavoriteList`：
 *
 * 面板分两个互相独立的分区（原版从不把本地收藏自动同步到网络收藏）：
 * - **本地收藏**：[localFolders] 每个夹一行 + 「新建收藏夹」
 * - **网络收藏**：仅当源声明了 `favorites` **且账号已登录**时出现；
 *   多夹源逐夹一行，单夹源只有一个 `folderId == ""` 的条目
 */
data class FavoritePanelState(
    val visible: Boolean = false,
    // ---- 本地收藏分区 ----
    val localFolders: List<String> = emptyList(),
    val localAdded: Set<String> = emptySet(),
    // ---- 网络收藏分区 ----
    /** 源声明了 `favorites` 且账号已登录时才显示网络分区。 */
    val hasNetwork: Boolean = false,
    /** 是否多收藏夹（源提供了 `loadFolders`）。 */
    val networkMultiFolder: Boolean = false,
    /** `folderId -> folderName`。 */
    val networkFolders: Map<String, String> = emptyMap(),
    /** 该漫画已加入的网络收藏夹 id 集合。 */
    val networkAdded: Set<String> = emptySet(),
    /** 单文件夹模式下「网络收藏」整体是否已添加。 */
    val networkSingleAdded: Boolean = false,
    /** 源限制「一本漫画只进一个网络收藏夹」。 */
    val singleFolderForSingleComic: Boolean = false,
    val isLoadingNetwork: Boolean = false,
    val networkError: String? = null,
    // ---- 交互 ----
    /** 等待中的条目 key（本地 = 夹名，网络 = `net:<夹id>`）。 */
    val pending: Set<String> = emptySet(),
    val toast: String? = null,
)

/**
 * 详情页 ViewModel。
 *
 * 追加：详情页收藏按钮 → 打开收藏面板（本地 + 网络双分区）。
 * 收藏的唯一数据源是 [LocalFavoritesManager]（对齐官方），
 * 原先写的旧单表 `comic_favorite` 已废弃，否则收藏页看不到详情页收藏的内容。
 */
class ComicDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val sources: SourceCatalog = BusinessPorts.of(app).sources
    private val comics: ComicContentApi = BusinessPorts.of(app).comics
    private val favoritesManager = LocalFavoritesManager.getInstance(app)
    private val prefs = BusinessPorts.of(app).comicPrefs
    private val historyDao = HistoryDao.getInstance(app)

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _readerEvents = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 4)
    val readerEvents: SharedFlow<ReaderEvent> = _readerEvents.asSharedFlow()

    private val _favPanel = MutableStateFlow(FavoritePanelState())
    val favPanel: StateFlow<FavoritePanelState> = _favPanel.asStateFlow()

    /** 当前漫画是否已在本地收藏（任一收藏夹）。 */
    private val _isLocalFav = MutableStateFlow(false)
    val isLocalFav: StateFlow<Boolean> = _isLocalFav.asStateFlow()

    /**
     * 该漫画在**源账号**（网络收藏）上是否已收藏。
     * 权威来源是源详情自带的 isFavorite（EH 的 comic.isFavorite / 哔咔 isFavourite /
     * jm 的 is_favorite，getComicDetails 时已解析到手）——不依赖面板里那条脆弱的
     * loadFolders 二次查询（EH 该查询内部还要再跑一次 loadInfo，易失败）。
     * 面板查询成功只会把它置 true 增强，绝不因查询失败回退成 false。
     */
    private val _isNetworkFav = MutableStateFlow(false)
    val isNetworkFav: StateFlow<Boolean> = _isNetworkFav.asStateFlow()

    val historyFlow by lazy { historyDao.historyFlow }

    private var loadedId: String? = null
    private var detailJob: Job? = null
    private var commentJob: Job? = null
    private var replyJob: Job? = null
    private var detailGeneration = 0
    private var currentComicItem: ComicItem? = null

    /** 预览图分页游标与状态（对齐官方 thumbnails.dart 的 `next` / `isInitialLoading`）。 */
    private var thumbnailNext: String? = null
    private var thumbnailStarted = false
    private var thumbnailExhausted = false
    private var fallbackChapterPages: List<String> = emptyList()

    init {
        // 收藏库任何变更后，同步详情页按钮状态与（若开着的）收藏面板
        viewModelScope.launch {
            favoritesManager.version.collect {
                val c = currentComicItem ?: return@collect
                val key = resolveSourceKey(c.sourceName)
                _isLocalFav.value = favoritesManager.isExist(c.id, key)
                if (_favPanel.value.visible) {
                    _favPanel.update {
                        it.copy(
                            localFolders = favoritesManager.folders.value,
                            localAdded = favoritesManager.find(c.id, key).toSet(),
                        )
                    }
                }
            }
        }
    }

    /** 拉取真实详情（章节目录、分卷、推荐等） */
    fun load(comic: ComicItem) {
        currentComicItem = comic
        val identity = "${resolveSourceKey(comic.sourceName)}:${comic.id}"
        if (loadedId == identity && (_uiState.value.details != null || _uiState.value.isLoading)) return
        loadedId = identity
        val generation = ++detailGeneration
        detailJob?.cancel()
        commentJob?.cancel()
        replyJob?.cancel()
        _uiState.value = DetailUiState(isLoading = true)
        _isNetworkFav.value = false
        // 换作品：重置预览图分页游标
        thumbnailNext = null
        thumbnailStarted = false
        thumbnailExhausted = false
        fallbackChapterPages = emptyList()

        val key = resolveSourceKey(comic.sourceName)
        detailJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "正在拉取章节目录与元数据...",
                    error = null,
                    thumbnails = emptyList(),
                    isLoadingThumbnails = false,
                    hasMoreThumbnails = false,
                    thumbnailError = null,
                    previewChapterId = "",
                    thumbnailsExpanded = false
                )
            }
            val res = comics.getComicDetails(key, comic.id)
            if (generation != detailGeneration) return@launch
            val d = res.getOrNull()
            if (d != null) {
                cacheMetrics(d, key)
                val capabilities = withContext(Dispatchers.IO) {
                    sources.getSource(key)?.getCommentCapabilities() ?: CommentCapabilities()
                }
                if (generation != detailGeneration) return@launch
                val initialPreviewChId = d.chapters.firstOrNull()?.id
                    ?: d.chapterGroups.firstOrNull()?.chapters?.firstOrNull()?.id
                    ?: ""
                _uiState.update {
                    it.copy(
                        details = d,
                        isLoading = false,
                        loadingMessage = "",
                        error = null,
                        isLiked = d.isLiked,
                        likesCount = d.likesCount,
                        commentThread = DetailCommentState(items = d.comments),
                        commentCapabilities = capabilities,
                        // 部分源的详情接口自带首批缩略图（EH 不带，走 loadThumbnails）
                        thumbnails = d.thumbnails,
                        previewChapterId = initialPreviewChId
                    )
                }
                // 源端收藏状态：详情返回即种子（EH/哔咔/jm 均自带），收藏图标与面板摘要据此标深
                _isNetworkFav.value = d.isFavorite
                // 异步拉取全量评论
                if (capabilities.canLoad) loadComments()
                // 预览图：详情自带首批时先展示，否则调源接口分页拉
                if (d.thumbnails.isNotEmpty()) thumbnailStarted = true else loadThumbnails()
            } else {
                val e = res.exceptionOrNull()
                Log.e("VeneraDebug", "getComicDetails($key) failed for id=${comic.id}", e)
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

    private fun cacheMetrics(details: ComicDetails, key: String = currentSourceKey()) {
        ComicMetricsCache(getApplication()).put(
            key, details.comic.id, details.comic.rating?.toDouble(), details.comic.likesCount
        )
    }

    /** 重新计算「是否已本地收藏」（进入页面时调用一次）。 */
    fun syncLocalFav(comic: ComicItem) {
        currentComicItem = comic
        viewModelScope.launch {
            _isLocalFav.value = favoritesManager.isExist(comic.id, resolveSourceKey(comic.sourceName))
        }
    }

    fun selectGroup(index: Int) {
        _uiState.update { it.copy(selectedGroupIndex = index) }
    }

    fun setReversed(value: Boolean) = _uiState.update { it.copy(reversed = value) }

    // region ---- 收藏面板（对齐官方 _FavoritePanel）----

    /** 打开收藏面板：读本地收藏夹 + 该漫画已加入的夹，再异步拉网络收藏夹。 */
    fun openFavoritePanel(comic: ComicItem) {
        _uiState.value.details?.let { cacheMetrics(it) }
        currentComicItem = comic
        val key = resolveSourceKey(comic.sourceName)
        _favPanel.value = FavoritePanelState(visible = true)

        viewModelScope.launch {
            _favPanel.update {
                it.copy(
                    localFolders = favoritesManager.folders.value,
                    localAdded = favoritesManager.find(comic.id, key).toSet(),
                    // 摘要行先用详情已知的源端状态兜底（单夹源此后不再有二次查询）
                    networkSingleAdded = _isNetworkFav.value,
                )
            }

            // 网络收藏：源声明了 favorites 且已登录才有。注意这两步都会同步跑 JS 引擎，
            // 必须在 IO 线程，否则主线程会被 evaluate 的 30s 超时卡死（踩过一次）。
            val src = sources.getSource(key)
            val (fd, logged) = withContext(Dispatchers.IO) {
                val f = src?.favoriteData
                f to (f != null && src.getAccountInfo().isLogged)
            }
            if (fd == null || !logged) {
                _favPanel.update { it.copy(hasNetwork = false, isLoadingNetwork = false) }
                return@launch
            }

            _favPanel.update {
                it.copy(
                    hasNetwork = true,
                    networkMultiFolder = fd.loadFolders != null,
                    singleFolderForSingleComic = fd.singleFolderForSingleComic,
                    isLoadingNetwork = fd.loadFolders != null,
                    // 单夹源没有 loadFolders 查询通道（哔咔/JM 单夹等），此前 state 恒为默认
                    // false → 永远显示「尚未收藏」。改用源详情自带的 isFavorite（picacg 的
                    // isFavourite / jm 的 is_favorite 等，loadComic info 已带回）作为初始态。
                    networkError = null,
                )
            }

            val loader = fd.loadFolders ?: return@launch
            loader(comic.id)
                .onSuccess { res ->
                    // EH 等多夹源的 favorited 查询成功 → 增强已知状态（写入 isNetworkFav，
                    // 让收藏图标也能标深）；空结果不回退详情已判定的 true，防二次查询
                    // 抖动把真实状态冲掉。
                    if (res.favorited.isNotEmpty()) {
                        _isNetworkFav.value = true
                    }
                    _favPanel.update { st ->
                        st.copy(
                            networkFolders = res.folders,
                            networkAdded = res.favorited.toSet(),
                            networkSingleAdded = if (res.favorited.isNotEmpty()) true else st.networkSingleAdded,
                            isLoadingNetwork = false,
                        )
                    }
                }
                .onFailure { e ->
                    _favPanel.update { it.copy(isLoadingNetwork = false, networkError = e.message ?: "加载网络收藏夹失败") }
                }
        }
    }

    fun closeFavoritePanel() {
        _favPanel.update { it.copy(visible = false) }
    }

    /** 加入 / 移出某个本地收藏夹。 */
    fun toggleLocalFavorite(comic: ComicItem, folder: String) {
        if (folder in _favPanel.value.pending) return
        val key = resolveSourceKey(comic.sourceName)
        _favPanel.update { it.copy(pending = it.pending + folder) }

        viewModelScope.launch {
            val wasAdded = folder in _favPanel.value.localAdded
            runCatching {
                if (wasAdded) {
                    favoritesManager.deleteComicWithId(folder, comic.id, key)
                } else {
                    favoritesManager.addComic(folder, comic.toFavoriteItem(key), null, currentUpdateTime())
                }
            }.onSuccess {
                _favPanel.update {
                    it.copy(
                        localAdded = if (wasAdded) it.localAdded - folder else it.localAdded + folder,
                        pending = it.pending - folder,
                        toast = if (wasAdded) "已移出「$folder」" else "已收藏到「$folder」",
                    )
                }
            }.onFailure { e ->
                _favPanel.update { it.copy(pending = it.pending - folder, toast = e.message ?: "操作失败") }
            }
        }
    }

    /** 新建本地收藏夹。 */
    fun createLocalFolder(name: String, onError: (String) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            onError("收藏夹名不能为空")
            return
        }
        viewModelScope.launch {
            runCatching { favoritesManager.createFolder(trimmed) }
                .onSuccess { real ->
                    _favPanel.update {
                        it.copy(
                            localFolders = if (real in it.localFolders) it.localFolders else it.localFolders + real,
                            toast = "已创建「$real」",
                        )
                    }
                }
                .onFailure { onError(it.message ?: "创建失败") }
        }
    }

    /**
     * 加入 / 移出某个网络收藏夹。
     *
     * @param folderId 多夹源传夹 id；单夹源传 `""`（对齐官方 `addOrDelFavorite(cid, '', ...)`）
     */
    fun toggleNetworkFavorite(comic: ComicItem, folderId: String, isAdded: Boolean) {
        val entryKey = "net:$folderId"
        if (entryKey in _favPanel.value.pending) return
        val key = resolveSourceKey(comic.sourceName)
        _favPanel.update { it.copy(pending = it.pending + entryKey) }

        viewModelScope.launch {
            val src = sources.getSource(key)
            val fd = withContext(Dispatchers.IO) { src?.favoriteData }
            if (fd == null) {
                _favPanel.update { it.copy(pending = it.pending - entryKey, toast = "该源不支持网络收藏") }
                return@launch
            }
            fd.addOrDelFavorite(comic.id, folderId, !isAdded, null)
                .onSuccess {
                    _favPanel.update { st ->
                        val added = if (isAdded) st.networkAdded - folderId else st.networkAdded + folderId
                        val nowAdded = if (folderId.isEmpty()) !isAdded else added.isNotEmpty()
                        st.copy(
                            networkAdded = added,
                            networkSingleAdded = nowAdded,
                            pending = st.pending - entryKey,
                            toast = if (isAdded) "已从网络收藏移除" else "已加入网络收藏",
                        )
                    }
                    // 网络收藏变动同步到详情页收藏图标（标深/褪色）
                    _isNetworkFav.value = if (isAdded) false else true
                }
                .onFailure { e ->
                    _favPanel.update { it.copy(pending = it.pending - entryKey, toast = e.message ?: "操作失败") }
                }
        }
    }

    /** 消费一次性提示。 */
    fun consumeToast() {
        _favPanel.update { it.copy(toast = null) }
    }

    /**
     * 长按收藏按钮的快捷收藏（对齐官方 `quickFavorite`）：
     * 加入设置的快捷夹；未设置快捷夹时退化为打开收藏面板。
     */
    fun quickFavorite(comic: ComicItem) {
        _uiState.value.details?.let { cacheMetrics(it) }
        val target = prefs.quickFavorite.value?.takeIf { it.isNotBlank() }
            ?: favoritesManager.folders.value.firstOrNull()
        if (target == null) {
            openFavoritePanel(comic)
            return
        }
        currentComicItem = comic
        val key = resolveSourceKey(comic.sourceName)
        viewModelScope.launch {
            runCatching { favoritesManager.addComic(target, comic.toFavoriteItem(key), null, currentUpdateTime()) }
                .onSuccess { added ->
                    _favPanel.update { it.copy(toast = if (added) "已收藏到「$target」" else "「$target」里已有这本") }
                }
                .onFailure { e -> _favPanel.update { it.copy(toast = e.message ?: "收藏失败") } }
        }
    }

    // endregion

    /** 喜欢/点赞漫画（乐观更新 + 失败回滚） */
    fun toggleLike() {
        val details = _uiState.value.details ?: return
        val key = resolveSourceKey(details.sourceKey)
        val currentLiked = _uiState.value.isLiked
        val newCount = (if (currentLiked) _uiState.value.likesCount - 1 else _uiState.value.likesCount + 1).coerceAtLeast(0)
        // 1) 乐观更新：先让 UI 即时响应
        _uiState.update { it.copy(isLiked = !currentLiked, likesCount = newCount) }

        viewModelScope.launch {
            val src = sources.getSource(key)
            val result = src?.likeComic(details.comic.id, isLike = !currentLiked)
            val supported = result?.getOrDefault(false) == true
            if (result == null || result.isFailure || !supported) {
                // 2) 失败 / 源不支持：回滚本地状态并如实提示
                _uiState.update {
                    it.copy(isLiked = currentLiked, likesCount = _uiState.value.likesCount.let { c ->
                        if (currentLiked) c - 1 else c + 1
                    }.coerceAtLeast(0))
                }
                _uiState.update {
                    it.copy(
                        error = when {
                            result == null -> "点赞失败：找不到对应漫画源"
                            result.isFailure -> "点赞失败：" + (result.exceptionOrNull()?.message ?: "未知错误")
                            else -> "该源不支持作品点赞"
                        }
                    )
                }
            }
        }
    }

    /** 评分 (0.0 - 5.0)：乐观更新 + 失败或源不支持时回滚并如实提示（与 toggleLike 同一套）。 */
    fun rateComic(rating: Float) {
        val details = _uiState.value.details ?: return
        val key = resolveSourceKey(details.sourceKey)
        val previousRating = _uiState.value.userRating
        // 1) 乐观更新：先让星级立刻变
        _uiState.update { it.copy(userRating = rating) }

        viewModelScope.launch {
            val src = sources.getSource(key)
            val result = src?.starRating(details.comic.id, rating)
            val supported = result?.getOrDefault(false) == true
            if (result == null || result.isFailure || !supported) {
                // 2) 失败 / 源不支持：回滚星级并说明原因，不能留着"已经评上了"的假象
                _uiState.update {
                    it.copy(
                        userRating = previousRating,
                        error = when {
                            result == null -> "评分失败：找不到对应漫画源"
                            result.isFailure -> "评分失败：" + (result.exceptionOrNull()?.message ?: "未知错误")
                            else -> "该源不支持作品评分"
                        }
                    )
                }
            }
        }
    }

    /**
     * 拉取预览缩略图 —— 对齐官方 `comic_details_page/thumbnails.dart`。
     *
     * - 详情接口自带首批缩略图的源：先展示，「加载更多」时再调接口（结果去重合并）。
     * - EH 这类详情不带缩略图的源：由源 `comic.loadThumbnails` 分页拉取
     *   （一次请求拿一整页 gallery HTML 里的官方预览小图，比逐页请求大图快得多）。
     * - 已有图时失败不打扰用户（官方亦只在首屏失败且无兜底时才显示错误）。
     */
    fun loadThumbnails(loadMore: Boolean = false) {
        val d = _uiState.value.details ?: return
        if (_uiState.value.isLoadingThumbnails) return
        if (loadMore) {
            if (thumbnailExhausted) return
            if (fallbackChapterPages.isNotEmpty()) {
                val currentCount = _uiState.value.thumbnails.size
                val nextCount = (currentCount + 12).coerceAtMost(fallbackChapterPages.size)
                thumbnailExhausted = nextCount >= fallbackChapterPages.size
                _uiState.update {
                    it.copy(
                        thumbnails = fallbackChapterPages.take(nextCount),
                        hasMoreThumbnails = !thumbnailExhausted
                    )
                }
                return
            }
        } else if (thumbnailStarted) {
            return
        }
        thumbnailStarted = true
        val key = resolveSourceKey(d.sourceKey)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingThumbnails = true, thumbnailError = null) }
            val res = comics.loadThumbnails(key, d.comic.id, if (loadMore) thumbnailNext else null)
            val page = res.getOrNull()
            if (page != null && page.thumbnails.isNotEmpty()) {
                thumbnailNext = page.next
                thumbnailExhausted = page.next == null
                _uiState.update {
                    it.copy(
                        thumbnails = (it.thumbnails + page.thumbnails)
                            .filter { t -> t.isNotBlank() }
                            .distinct(),
                        isLoadingThumbnails = false,
                        hasMoreThumbnails = !thumbnailExhausted,
                        thumbnailError = null
                    )
                }
                return@launch
            }

            // 官方 loadThumbnails 接口未实现或无结果时（包括 JM、拷贝、MangaDex、包子等大部分源），
            // 自动拉取第一话正文图片作为预览图，并在详情页展示
            val firstChapter = d.chapters.firstOrNull()
                ?: d.chapterGroups.firstOrNull()?.chapters?.firstOrNull()
            if (firstChapter != null) {
                val chRes = comics.getChapterPages(key, d.comic.id, firstChapter.id)
                val chPages = chRes.getOrNull()
                if (chPages != null && chPages.pages.isNotEmpty()) {
                    fallbackChapterPages = chPages.pages
                    if (chPages.headers.isNotEmpty()) {
                        ImageHeaderPolicy.publishForUrls(chPages.pages, chPages.headers)
                    }
                    val initialSlice = fallbackChapterPages.take(12)
                    thumbnailExhausted = fallbackChapterPages.size <= 12
                    _uiState.update {
                        it.copy(
                            thumbnails = initialSlice,
                            isLoadingThumbnails = false,
                            hasMoreThumbnails = !thumbnailExhausted,
                            thumbnailError = null,
                            previewChapterId = firstChapter.id
                        )
                    }
                    return@launch
                }
            }

            val msg = res.exceptionOrNull()?.message ?: "预览图加载失败"
            Log.w("VeneraDebug", "loadThumbnails($key, cid=${d.comic.id}, loadMore=$loadMore) failed: $msg")
            _uiState.update {
                it.copy(
                    isLoadingThumbnails = false,
                    thumbnailError = if (it.thumbnails.isEmpty()) msg else null
                )
            }
        }
    }

    /**
     * 展开被 [PREVIEW_LIMIT] 折叠的预览图。
     *
     * 展开是纯粹的 UI 操作：已到手的图直接放出来，不做网络请求。
     * 只有源真的还有下一页（[DetailUiState.hasMoreThumbnails]）且用户
     * 还没拉过时，才顺带补一次分页 —— 否则光展开会漏掉后面的页。
     */
    fun expandThumbnails() {
        val state = _uiState.value
        if (state.thumbnailsExpanded) return
        _uiState.update { it.copy(thumbnailsExpanded = true) }
        // 折叠期间没拉过的后续分页，展开时补齐；已经在拉/已到末页则不重复触发。
        if (state.hasMoreThumbnails && !state.isLoadingThumbnails) {
            loadThumbnails(loadMore = true)
        }
    }

    /** 收起预览图，回到最多 [PREVIEW_LIMIT] 张的折叠态。 */
    fun collapseThumbnails() {
        _uiState.update { it.copy(thumbnailsExpanded = false) }
    }

    /**
     * 已经问过 `onThumbnailLoad` 的 url。每个 url **只问一次**：
     * 源没实现这个钩子时拿不到任何结果，不记一笔就会在每次预览窗口变化时重跑一遍 JS
     * （引擎在主线程串行，那是纯浪费）。超时的那次同样不再重试 —— 预览条退回今天的行为，
     * 比反复堵主线程划算。
     */
    private val thumbnailConfigAsked = mutableSetOf<String>()

    /**
     * 为**当前挂载的那批**预览图换算源声明的加载配置（`comic.onThumbnailLoad`）。
     * 由屏幕侧在预览窗口变化时调用，所以一次只有几十个 url，不会把整本两三百张塞进一次 JS。
     *
     * ⚠️ 这个钩子**不换小图**，只回 url + 防盗头（实测与依据见 [ResolvedThumbnailConfig]）。
     * 拿到头就按 host 记进 [ImageHeaderPolicy]：预览条原先只有「首话页面图兜底」那一条发过
     * 头（[loadThumbnails] 里的 publishForUrls），详情自带 thumbnails 与 loadThumbnails
     * 两条**一处都没发**，于是内置表覆盖不到的 host 就是一排 403 空白。
     */
    fun ensureThumbnailConfigs(urls: List<String>) {
        val pending = urls.filter { it.isNotBlank() && thumbnailConfigAsked.add(it) }
        if (pending.isEmpty()) return
        viewModelScope.launch {
            val configs = comics.resolveThumbnailConfigs(currentSourceKey(), pending)
            if (configs.isEmpty()) return@launch
            configs.forEach { (_, cfg) ->
                // 按**实际要请求的那个 url** 的 host 记账（EH 会换成镜像域 ehgt.org）。
                if (cfg.headers.isNotEmpty()) {
                    ImageHeaderPolicy.publishForUrls(listOf(cfg.url), cfg.headers)
                }
            }
            _uiState.update { it.copy(thumbnailConfigs = it.thumbnailConfigs + configs) }
        }
    }

    /** Use the resolved source key for comments and downloads, not its display name. */
    fun currentSourceKey(): String = resolveSourceKey(
        _uiState.value.details?.sourceKey?.takeIf { it.isNotBlank() }
            ?: currentComicItem?.sourceName.orEmpty()
    )

    fun openReplies(comment: Comment) {
        if (comment.id.isBlank() || !_uiState.value.commentCapabilities.canLoad) return
        replyJob?.cancel()
        _uiState.update { it.copy(replyTo = comment, replyThread = DetailCommentState()) }
        loadComments(replyId = comment.id)
    }

    fun closeReplies() {
        replyJob?.cancel()
        _uiState.update { it.copy(replyTo = null, replyThread = DetailCommentState()) }
    }

    /** Refresh replaces even an empty result; only successful pages advance the cursor. */
    fun loadComments(loadMore: Boolean = false, replyId: String? = null) {
        val state = _uiState.value
        val details = state.details ?: return
        if (!state.commentCapabilities.canLoad) return
        if (replyId != null && state.replyTo?.id != replyId) return
        val thread = if (replyId == null) state.commentThread else state.replyThread
        if (thread.isLoading || (loadMore && !thread.hasMore)) return
        val requestedPage = if (loadMore) thread.page + 1 else 1
        val generation = detailGeneration
        val key = currentSourceKey()
        fun updateThread(transform: (DetailCommentState) -> DetailCommentState) {
            if (generation != detailGeneration) return
            _uiState.update { current ->
                if (replyId == null) current.copy(commentThread = transform(current.commentThread))
                else if (current.replyTo?.id == replyId) current.copy(replyThread = transform(current.replyThread))
                else current
            }
        }
        updateThread { it.copy(isLoading = true, error = null, requestedPage = requestedPage) }
        val job = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    val source = sources.getSource(key) ?: error("找不到对应漫画源")
                    source.loadCommentsPage(details.comic.id, details.subId, requestedPage, replyId).getOrThrow()
                }
                updateThread { it.received(page.comments, requestedPage, page.maxPage) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateThread { it.copy(isLoading = false, error = e.message ?: "评论加载失败") }
            }
        }
        if (replyId == null) commentJob = job else replyJob = job
    }

    /** Preserve subId and pass replyId separately, as the Flutter/JS protocol requires. */
    fun sendComment(content: String, replyId: String? = null, onComplete: (Boolean, String?) -> Unit) {
        val state = _uiState.value
        val details = state.details
        val validationError = when {
            details == null -> "详情尚未加载完成"
            !state.commentCapabilities.canSend -> "当前源暂不支持发送评论"
            content.isBlank() -> "评论不能为空"
            state.isSendingComment -> "评论正在发送中"
            replyId != null && state.replyTo?.id != replyId -> "回复对象已改变，请重试"
            else -> null
        }
        if (validationError != null || details == null) {
            onComplete(false, validationError ?: "详情尚未加载完成")
            return
        }
        val generation = detailGeneration
        val key = currentSourceKey()
        _uiState.update { it.copy(isSendingComment = true) }
        viewModelScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    val source = sources.getSource(key) ?: error("找不到对应漫画源")
                    source.sendComment(details.comic.id, details.subId, content, replyId).getOrThrow()
                }
                if (generation != detailGeneration) return@launch
                if (!success) {
                    onComplete(false, "源拒绝了评论，请重试")
                    return@launch
                }
                onComplete(true, null)
                // Cancel an in-flight pre-send load so it cannot replace the fresh result.
                commentJob?.cancel()
                _uiState.update { it.copy(commentThread = it.commentThread.copy(isLoading = false)) }
                loadComments()
                if (replyId != null && _uiState.value.replyTo?.id == replyId) {
                    replyJob?.cancel()
                    _uiState.update { it.copy(replyThread = it.replyThread.copy(isLoading = false)) }
                    loadComments(replyId = replyId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == detailGeneration) onComplete(false, e.message ?: "发表失败")
            } finally {
                if (generation == detailGeneration) _uiState.update { it.copy(isSendingComment = false) }
            }
        }
    }

    /**
     * 解析一章的真实图片地址并打开阅读器。
     *
     * @param initialPageIndex 起始页（0 基）；预览图点击进入时用
     *        （对齐官方 thumbnails.dart 的 `state.read(null, index + 1)`）
     */
    fun openChapter(
        comic: ComicItem,
        chapterId: String,
        chapterTitle: String,
        fallbackIdx: Int,
        initialPageIndex: Int = 0
    ) {
        viewModelScope.launch {
            val key = resolveSourceKey(comic.sourceName)
            // 封面优先用详情解析出来的那一张，其次才是入口带的。必须在这里定：
            // ReaderSession.coverUrl 会被阅读器原样写进 comic_history.cover_url，
            // 而从插图收藏进来的入口 coverUrl 恒为空串（FavoriteImageItem.toComicItem 有意
            // 留空、真封面由详情页自己拉），照抄入口值就等于给历史页留一个永久空白框 ——
            // url 为空连请求都不发，Coil 那边一条错误日志都不会有。
            // 口径与 ComicDetailScreen 头部封面那一处保持一致。
            val sessionCover =
                _uiState.value.details?.comic?.cover?.ifBlank { comic.coverUrl } ?: comic.coverUrl

            // 优先检查本地离线下载文件，已下载章节实现秒开与无网离线阅读
            val dlMgr = com.venera.compose.download.DownloadManager.getInstance(getApplication())
            val localFiles = dlMgr.getDownloadedChapterFiles(key, comic.id, chapterId)
            if (!localFiles.isNullOrEmpty()) {
                val state = _uiState.value
                val currentGroup = state.details?.chapterGroups?.getOrNull(state.selectedGroupIndex)
                val allChapters = currentGroup?.chapters?.map { it.id to it.title }
                    ?: state.details?.chapters?.map { it.id to it.title }
                val mappedPages = localFiles.mapIndexed { idx, file ->
                    com.venera.compose.reader.ComicPageSource.LocalFile(file, idx)
                }
                val readerChapter = com.venera.compose.reader.ReaderChapter(
                    id = chapterId,
                    title = chapterTitle,
                    pages = mappedPages,
                    isLoaded = true
                )
                _readerEvents.tryEmit(
                    ReaderEvent.Live(
                        com.venera.compose.reader.ReaderSession(
                            comicId = comic.id,
                            comicTitle = comic.title,
                            coverUrl = sessionCover,
                            sourceName = comic.sourceName,
                            sourceKey = key,
                            chapters = listOf(readerChapter),
                            initialChapterIndex = 0,
                            initialPageIndex = initialPageIndex
                        )
                    )
                )
                return@launch
            }

            _uiState.update { it.copy(loadingMessage = "正在解析章节画质...") }
            val res = comics.getChapterPages(key, comic.id, chapterId)
            val pagesData = res.getOrNull()
            val pages = pagesData?.pages.orEmpty()
            _uiState.update { it.copy(loadingMessage = "") }

            // 防盗链头交给图片加载策略。注意动态页（useOnImageLoad）的 pages 是
            // 「图片键」不是 URL，不能按 URL 预发布 —— 真实头由阅读器逐页解析后发布
            pagesData?.headers?.takeIf { it.isNotEmpty() }?.let { hdrs ->
                // 外层 ?.let 已保证 pagesData 非空，这里不必再判一次（原先的 pd != null 恒真）
                if (!pagesData.useOnImageLoad) ImageHeaderPolicy.publishForUrls(pages, hdrs)
                // 用 sessionCover 而不是 comic.coverUrl：picacg 这类站的封面必须带防盗头，
                // 入口封面为空时若跳过这一步，详情拉到的真封面就没发布过头 → 取图 403 → 空白。
                if (sessionCover.isNotEmpty()) ImageHeaderPolicy.publishForUrls(listOf(sessionCover), hdrs)
            }

            if (pages.isNotEmpty()) {
                val state = _uiState.value
                val currentGroup = state.details?.chapterGroups?.getOrNull(state.selectedGroupIndex)
                val allChapters = currentGroup?.chapters?.map { it.id to it.title }
                    ?: state.details?.chapters?.map { it.id to it.title }
                _readerEvents.tryEmit(
                    ReaderEvent.Live(
                        ReaderSessionFactory.createLiveSession(
                            comicId = comic.id,
                            comicTitle = comic.title,
                            coverUrl = sessionCover,
                            chapterId = chapterId,
                            chapterTitle = chapterTitle,
                            pages = pages,
                            sourceName = comic.sourceName,
                            sourceKey = key,
                            allChapters = allChapters,
                            // 题材统计的标签来源。**必须优先用详情的 tagMap 打平结果**：列表接口
                            // 给的 tags 是裸词（picacg / jm / 拷贝漫画都是），而归一化按原口径会
                            // 丢掉所有不带 namespace 的标签 —— 真机实测「只有 EH 记录得到标签」就是因为
                            // EH 的列表项恰好自带 "female:xxx"，其余源全军覆没。
                            // 并上 comic.tags 是为了详情还没加载完就进阅读器的场景（EH 列表串在那时是唯一数据）。
                            tags = (state.details?.plainTags.orEmpty() + comic.tags).distinct(),
                            useOnImageLoad = pagesData?.useOnImageLoad == true,
                            initialPageIndex = initialPageIndex
                        ),
                    ),
                )
            } else {
                // 解析不出图片就如实报错：**绝不回退到占位图**
                // （曾用硬编码 Unsplash 图伪造整章，用户会读到与作品无关的图片）
                val reason = res.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                _readerEvents.tryEmit(
                    ReaderEvent.Failed(
                        reason ?: "「$chapterTitle」没有解析到任何图片页，请稍后重试或更换源"
                    )
                )
            }
        }
    }

    /** 详情里带回来的更新时间，收藏时一并写入（追更用）。 */
    private fun currentUpdateTime(): String? =
        _uiState.value.details?.updateTime?.takeIf { it.isNotBlank() }

    private fun resolveSourceKey(sourceNameOrKey: String): String {
        return sources.sourcesFlow.value.find {
            it.key.equals(sourceNameOrKey, ignoreCase = true) || it.name.equals(sourceNameOrKey, ignoreCase = true)
        }?.key ?: sources.activeSourceKey.value
    }
}

/** 详情页漫画 → 本地收藏条目（对齐官方 `_toFavoriteItem`）。 */
private fun ComicItem.toFavoriteItem(sourceKey: String) = FavoriteItem(
    id = id,
    name = title,
    author = author,
    sourceKey = sourceKey,
    tags = tags,
    coverPath = coverUrl,
)
