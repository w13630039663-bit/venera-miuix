package com.venera.compose.feature

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.FavoriteData
import com.venera.compose.source.model.Comic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 网络收藏「源列表」的展示模型。
 *
 * ⚠️ 为什么登录态要在这里算好，而不是让 UI 直接调 `ComicSource.getAccountInfo()`：
 * `JsComicSource` 的 `getAccountInfo()` / `favoriteData` 内部走的是**同步** `engine.evaluate(...)`，
 * 而 `VeneraJsEngine.evaluate()` 一旦在主线程被调用，会 `evalExecutor.submit { ... }.get(30s)`；
 * 主线程被 `.get()` 占死后，`evaluateBlocking` 里 post 到 `mainHandler` 的 Runnable
 * 永远排不上队，只能等满 30s 超时。于是「主线程每碰一次 = 卡 30 秒」，
 * 源一多就是十几分钟的假死（这正是「打开网络收藏卡死」的根因）。
 * 因此：**一切触碰 JS 引擎的调用必须离开主线程**，UI 只读这份预先算好的快照。
 */
data class NetSourceUi(
    val key: String,
    val name: String,
    val logged: Boolean,
)

/**
 * 网络收藏 ViewModel（S5 网络收藏夹）。
 * 对齐官方 `pages/favorites/network_favorites_page.dart` + `pages/favorites/side_bar.dart`：
 * - 列出所有声明了 `favorites` 的源（侧栏维度）
 * - 选中源后：多文件夹先列文件夹，单文件夹 / 进入文件夹后直接列漫画
 * - 翻页对齐官方 `ComicList`：源声明 `loadNext` 时走游标（首屏 next=null），
 *   否则走 `loadComics` 的 page/maxPage
 * - 所有网络调用经 [FavoriteData] 内建的 retryZone（未登录 / 登录过期自动重登）
 *
 * 线程约定（重要）：凡是会同步跑 JS 引擎的属性（`favoriteData`、`getAccountInfo()`）
 * 一律只在 `Dispatchers.IO` 上访问；真正的收藏调用（`loadComic` / `loadFolders` / …）
 * 内部是 `evaluateEnvelope`（挂起 + `evaluateAsync`），不会阻塞主线程，可放心调用。
 */
class NetworkFavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = ComicSourceManager.getInstance(application)

    /**
     * key -> 已解析的 [FavoriteData] 缓存。
     * 在后台扫描源列表时顺手填好，这样后续 `selectSource` 无需再碰一次 JS 引擎。
     */
    private val favCache = ConcurrentHashMap<String, FavoriteData>()

    /** 支持网络收藏的源（声明了 favoriteData 且非 null），附带登录态；计算在 IO 线程完成 */
    val sourcesFlow: StateFlow<List<NetSourceUi>> = manager.sourcesFlow
        .map { list ->
            list.mapNotNull { src ->
                // 这两行都会同步跑 JS，必须在 IO 线程（flowOn 已保证）
                val fd = runCatching { src.favoriteData }.getOrNull() ?: return@mapNotNull null
                favCache[src.key] = fd
                val logged = runCatching { src.getAccountInfo().isLogged }.getOrDefault(false)
                NetSourceUi(key = src.key, name = src.name, logged = logged)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前选中的源 key；null 表示停留在「源列表」 */
    var selectedSourceKey: String? by mutableStateOf(null)
        private set

    var favoriteData: FavoriteData? by mutableStateOf(null)
        private set

    var isMultiFolder: Boolean by mutableStateOf(false)
        private set

    /** 多文件夹模式下，文件夹 id -> 名称；非多文件夹为 null */
    var folders: Map<String, String>? by mutableStateOf(null)
        private set

    /** 当前选中的文件夹 id；非多文件夹 / 未选文件夹时，命中源的默认收藏 */
    var currentFolderId: String? by mutableStateOf(null)
        private set

    var comics: List<Comic> by mutableStateOf(emptyList())
        private set

    /** `loadComics` 通道的当前页号（`loadNext` 通道不使用） */
    var page: Int by mutableStateOf(1)
        private set

    /** `loadComics` 通道的总页数 */
    var maxPage: Int by mutableStateOf(1)
        private set

    /** `loadNext` 通道的游标；null 表示首屏尚未加载或已到末尾 */
    var nextToken: String? by mutableStateOf(null)
        private set

    /** 是否还有下一页（由当前源采用的分页通道决定） */
    var hasMore: Boolean by mutableStateOf(false)
        private set

    var isLoading: Boolean by mutableStateOf(false)
        private set

    var isFolderLoading: Boolean by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

    /**
     * 选中某个源，进入其网络收藏。
     *
     * 注意：`favoriteData` 会同步跑 JS 引擎，绝不能在主线程（点击回调里）直接取，
     * 因此优先走 [favCache]（扫描列表时已备好），未命中才切到 IO 线程解析。
     */
    fun selectSource(key: String) {
        val cached = favCache[key]
        if (cached != null) {
            applySource(key, cached)
            return
        }
        viewModelScope.launch {
            val fd = withContext(Dispatchers.IO) {
                runCatching { manager.getSource(key)?.favoriteData }.getOrNull()
            }
            if (fd == null) {
                selectedSourceKey = null
                return@launch
            }
            favCache[key] = fd
            applySource(key, fd)
        }
    }

    private fun applySource(key: String, fd: FavoriteData) {
        selectedSourceKey = key
        favoriteData = fd
        isMultiFolder = fd.multiFolder
        resetComicState()
        folders = null
        if (fd.multiFolder) {
            loadFolders()
        } else {
            loadFirstPage(null)
        }
    }

    private fun resetComicState() {
        comics = emptyList()
        page = 1
        maxPage = 1
        nextToken = null
        hasMore = false
        error = null
    }

    /** 返回「源列表」视图 */
    fun backToSources() {
        selectedSourceKey = null
        favoriteData = null
        isMultiFolder = false
        folders = null
        currentFolderId = null
        resetComicState()
    }

    fun loadFolders() {
        val fd = favoriteData ?: return
        viewModelScope.launch {
            isFolderLoading = true
            error = null
            val loader = fd.loadFolders ?: run {
                error = "当前源未提供文件夹列表能力"
                isFolderLoading = false
                return@launch
            }
            loader(null)
                .onSuccess { res -> folders = res.folders }
                .onFailure { error = it.message ?: "加载文件夹失败" }
            isFolderLoading = false
        }
    }

    /** 进入某个文件夹（多文件夹模式） */
    fun enterFolder(folderId: String) {
        currentFolderId = folderId
        resetComicState()
        loadFirstPage(folderId)
    }

    /** 回到文件夹列表（多文件夹模式） */
    fun backToFolders() {
        currentFolderId = null
        resetComicState()
        loadFolders()
    }

    fun refresh() {
        val fd = favoriteData ?: return
        if (fd.multiFolder && currentFolderId == null) loadFolders() else {
            resetComicState()
            loadFirstPage(currentFolderId)
        }
    }

    /**
     * 加载首屏。
     * 对齐官方 `ComicList(loadPage:, loadNext:)`——优先使用 `loadComics`，
     * 源未声明时退回 `loadNext(null, folder)`（官方两条通道可单独存在）。
     */
    private fun loadFirstPage(folderId: String?) {
        val fd = favoriteData ?: return
        viewModelScope.launch {
            isLoading = true
            error = null
            val pageLoader = fd.loadComic
            val nextLoader = fd.loadNext
            when {
                pageLoader != null -> pageLoader(1, folderId)
                    .onSuccess {
                        comics = it.comics
                        maxPage = it.maxPage
                        page = 1
                        hasMore = 1 < it.maxPage
                    }
                    .onFailure { error = it.message ?: "加载收藏失败" }

                nextLoader != null -> nextLoader(null, folderId)
                    .onSuccess {
                        comics = it.comics
                        nextToken = it.next
                        hasMore = it.next != null
                    }
                    .onFailure { error = it.message ?: "加载收藏失败" }

                else -> error = "当前源未提供收藏列表能力"
            }
            isLoading = false
        }
    }

    fun loadMore() {
        val fd = favoriteData ?: return
        if (isLoading || !hasMore) return
        viewModelScope.launch {
            isLoading = true
            val pageLoader = fd.loadComic
            val nextLoader = fd.loadNext
            if (pageLoader != null) {
                val next = page + 1
                pageLoader(next, currentFolderId)
                    .onSuccess {
                        comics = comics + it.comics
                        maxPage = it.maxPage
                        page = next
                        hasMore = next < it.maxPage
                    }
                    .onFailure { error = it.message ?: "加载更多失败" }
            } else if (nextLoader != null) {
                nextLoader(nextToken, currentFolderId)
                    .onSuccess {
                        comics = comics + it.comics
                        nextToken = it.next
                        hasMore = it.next != null
                    }
                    .onFailure { error = it.message ?: "加载更多失败" }
            }
            isLoading = false
        }
    }

    /** 从网络收藏移除一本漫画 */
    fun deleteComic(
        comicId: String,
        folderId: String,
        favoriteId: String?,
        onResult: (Boolean, String?) -> Unit = { _, _ -> },
    ) {
        val fd = favoriteData ?: return
        viewModelScope.launch {
            fd.addOrDelFavorite(comicId, folderId, false, favoriteId)
                .onSuccess {
                    comics = comics.filterNot { it.id == comicId }
                    onResult(true, null)
                }
                .onFailure { onResult(false, it.message) }
        }
    }

    fun createFolder(name: String, onError: (String) -> Unit = {}) {
        val fd = favoriteData ?: return
        val adder = fd.addFolder ?: return
        viewModelScope.launch {
            adder(name.trim())
                .onSuccess { loadFolders() }
                .onFailure { onError(it.message ?: "创建失败") }
        }
    }

    fun deleteFolder(folderId: String, onError: (String) -> Unit = {}) {
        val fd = favoriteData ?: return
        val deleter = fd.deleteFolder ?: return
        viewModelScope.launch {
            deleter(folderId)
                .onSuccess { backToFolders() }
                .onFailure { onError(it.message ?: "删除失败") }
        }
    }
}
