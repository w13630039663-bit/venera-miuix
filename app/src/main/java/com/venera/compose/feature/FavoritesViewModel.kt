package com.venera.compose.feature

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.api.BusinessPorts
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 「全部」这个虚拟收藏夹的标记值。
 *
 * 对齐原版 `local_favorites_page.dart` 的 `_localAllFolderLabel`；
 * 用一个不可能与真实文件夹名冲突的哨兵值，避免在 DB 里建实体表。
 */
const val LOCAL_ALL_FOLDER = "^_^[%local_all%]^_^"

/**
 * 本地收藏列表的排序规则。
 *
 * [CUSTOM] 走数据库的 `display_order`（用户拖动过的顺序，也是官方的默认语义），
 * 其余四种是只读视图层的排序，不写库。
 */
enum class FavoriteSortOrder(val label: String) {
    TIME_DESC("最新收藏"),
    TIME_ASC("最早收藏"),
    NAME_ASC("名称 (A-Z)"),
    NAME_DESC("名称 (Z-A)"),
    CUSTOM("自定义排序")
}

/**
 * 收藏页 ViewModel。
 *
 * 数据全部来自 [LocalFavoritesManager]，ViewModel 只负责：
 * 当前收藏夹 / 搜索关键字 / 多选状态，以及把 UI 动作转发给 Manager。
 */
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = LocalFavoritesManager.getInstance(application)
    private val prefs = BusinessPorts.of(application).comicPrefs

    val folders: StateFlow<List<String>> = manager.folders
    val counts: StateFlow<Map<String, Int>> = manager.counts

    /** 当前选中的收藏夹，默认「全部」。 */
    var currentFolder: String by mutableStateOf(LOCAL_ALL_FOLDER)
        private set

    var comics: List<FavoriteItem> by mutableStateOf(emptyList())
        private set

    var keyword: String by mutableStateOf("")
        private set

    /** 当前排序规则（持久化于偏好，跨会话保留）。 */
    var sortOrder: FavoriteSortOrder by mutableStateOf(
        runCatching {
            FavoriteSortOrder.valueOf(prefs.favoriteSortOrder.value)
        }.getOrDefault(FavoriteSortOrder.TIME_DESC)
    )
        private set

    var isLoading: Boolean by mutableStateOf(false)
        private set

    init {
        // 观察内容变更计数，任何写操作后自动重载
        viewModelScope.launch {
            manager.version.collect { loadComics() }
        }
        viewModelScope.launch {
            manager.folders.collect { foldersNow ->
                if (currentFolder != LOCAL_ALL_FOLDER && currentFolder !in foldersNow) {
                    currentFolder = LOCAL_ALL_FOLDER
                    loadComics()
                }
            }
        }
    }

    fun selectFolder(folder: String) {
        if (currentFolder == folder) return
        currentFolder = folder
        loadComics()
    }

    /** 注意：不能叫 `setKeyword`，会与 `var keyword` 生成的 setter 产生 JVM 签名冲突。 */
    fun updateKeyword(value: String) {
        keyword = value
        loadComics()
    }

    fun loadComics() {
        viewModelScope.launch {
            isLoading = true
            val loaded = if (keyword.isBlank()) {
                if (currentFolder == LOCAL_ALL_FOLDER) manager.getAllComics() else manager.getFolderComics(currentFolder)
            } else {
                if (currentFolder == LOCAL_ALL_FOLDER) manager.search(keyword) else manager.searchInFolder(currentFolder, keyword)
            }
            // 统一经排序管线后再赋值：切换排序 / 刷新 / 搜索都走同一条路径
            comics = applySorting(loaded)
            isLoading = false
        }
    }

    /**
     * 切换排序规则：立即重排当前列表并写入偏好持久化。
     */
    fun updateSortOrder(order: FavoriteSortOrder) {
        if (sortOrder == order) return
        sortOrder = order
        prefs.setFavoriteSortOrder(order.name)
        comics = applySorting(comics)
    }

    /**
     * 排序管线（纯函数，不写库）。
     *
     * - 时间：`time` 是 `yyyy-MM-dd HH:mm:ss` 定长字符串，字典序等价于时间序，可直接比较；
     * - 名称：忽略大小写比较，中文按 Unicode 序（与官方 `compareTo` 行为一致）；
     * - 自定义：走 `displayOrder`（拖动排序结果）。
     */
    private fun applySorting(items: List<FavoriteItem>): List<FavoriteItem> = when (sortOrder) {
        FavoriteSortOrder.TIME_DESC -> items.sortedByDescending { it.time }
        FavoriteSortOrder.TIME_ASC -> items.sortedBy { it.time }
        FavoriteSortOrder.NAME_ASC -> items.sortedBy { it.name.lowercase() }
        FavoriteSortOrder.NAME_DESC -> items.sortedByDescending { it.name.lowercase() }
        FavoriteSortOrder.CUSTOM -> items.sortedBy { it.displayOrder }
    }

    // region ---- 收藏夹动作 ----

    fun createFolder(name: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { manager.createFolder(name.trim()) }
                .onFailure { onError(it.message ?: "创建失败") }
        }
    }

    fun renameFolder(before: String, after: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { manager.rename(before, after.trim()) }
                .onFailure { onError(it.message ?: "重命名失败") }
        }
    }

    fun deleteFolder(name: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { manager.deleteFolder(name) }
                .onFailure { onError(it.message ?: "删除失败") }
                .onSuccess { if (currentFolder == name) selectFolder(LOCAL_ALL_FOLDER) }
        }
    }

    fun saveFolderOrder(ordered: List<String>) {
        viewModelScope.launch { manager.updateOrder(ordered) }
    }

    // endregion

    // region ---- 条目动作 ----
    //
    // 四个动作都**收 items 参数**，不读内部的多选集合：多选状态机已经统一到 UI 侧的
    // `MultiSelectState`（四处收藏面板共用同一份，见 `components/selection/`）。
    // ViewModel 只留"对这批条目做什么"，这样同一份实现对任何一个面板都成立。

    fun deleteItems(items: List<FavoriteItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            if (currentFolder == LOCAL_ALL_FOLDER) {
                manager.batchDeleteComicsInAllFolders(items)
            } else {
                manager.batchDeleteComics(currentFolder, items)
            }
        }
    }

    fun moveItemsTo(items: List<FavoriteItem>, target: String) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            if (currentFolder == LOCAL_ALL_FOLDER) {
                // 「全部」视图里没有源收藏夹，退化为复制到目标
                manager.batchCopyFavorites(target, target, items)
            } else {
                manager.batchMoveFavorites(currentFolder, target, items)
            }
        }
    }

    fun copyItemsTo(items: List<FavoriteItem>, target: String) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            if (currentFolder == LOCAL_ALL_FOLDER) {
                manager.batchCopyFavorites(target, target, items)
            } else {
                manager.batchCopyFavorites(currentFolder, target, items)
            }
        }
    }

    fun saveOrder(ordered: List<FavoriteItem>) {
        if (currentFolder == LOCAL_ALL_FOLDER) return
        viewModelScope.launch { manager.reorder(ordered, currentFolder) }
    }

    /**
     * 加入 / 移出收藏（供详情页调用）。
     * @return true 表示操作后处于「已收藏」状态
     */
    fun toggleFavorite(item: FavoriteItem, folder: String? = null): Boolean {
        var added = false
        viewModelScope.launch {
            val target = folder ?: (folders.value.firstOrNull() ?: "默认")
            val existing = manager.find(item.id, item.sourceKey)
            if (existing.isNotEmpty()) {
                existing.forEach { manager.deleteComicWithId(it, item.id, item.sourceKey) }
            } else {
                manager.addComic(target, item)
                added = true
            }
        }
        return added
    }
}
