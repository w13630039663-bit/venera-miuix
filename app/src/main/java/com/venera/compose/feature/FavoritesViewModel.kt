package com.venera.compose.feature

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.LocalFavoritesManager
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
 * 收藏页 ViewModel（S5-3）。
 *
 * 数据全部来自 [LocalFavoritesManager]，ViewModel 只负责：
 * 当前收藏夹 / 搜索关键字 / 多选状态，以及把 UI 动作转发给 Manager。
 */
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = LocalFavoritesManager.getInstance(application)

    val folders: StateFlow<List<String>> = manager.folders
    val counts: StateFlow<Map<String, Int>> = manager.counts

    /** 当前选中的收藏夹，默认「全部」。 */
    var currentFolder: String by mutableStateOf(LOCAL_ALL_FOLDER)
        private set

    var comics: List<FavoriteItem> by mutableStateOf(emptyList())
        private set

    var keyword: String by mutableStateOf("")
        private set

    var isLoading: Boolean by mutableStateOf(false)
        private set

    var multiSelectMode: Boolean by mutableStateOf(false)
        private set

    /** 多选集合，元素是 `(id, type)`，与官方 `id + type` 判等一致。 */
    var selected: Set<Pair<String, Int>> by mutableStateOf(emptySet())
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
        exitMultiSelect()
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
            comics = if (keyword.isBlank()) {
                if (currentFolder == LOCAL_ALL_FOLDER) manager.getAllComics() else manager.getFolderComics(currentFolder)
            } else {
                if (currentFolder == LOCAL_ALL_FOLDER) manager.search(keyword) else manager.searchInFolder(currentFolder, keyword)
            }
            isLoading = false
        }
    }

    // region ---- 多选 ----

    fun enterMultiSelect(item: FavoriteItem) {
        multiSelectMode = true
        selected = selected + (item.id to item.type)
    }

    fun toggleSelect(item: FavoriteItem) {
        val key = item.id to item.type
        selected = if (key in selected) selected - key else selected + key
        if (selected.isEmpty()) multiSelectMode = false
    }

    fun selectAll() {
        selected = comics.map { it.id to it.type }.toSet()
    }

    fun invertSelection() {
        selected = comics.map { it.id to it.type }.filterNot { it in selected }.toSet()
    }

    fun exitMultiSelect() {
        multiSelectMode = false
        selected = emptySet()
    }

    // endregion

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

    fun deleteSelected() {
        val items = comics.filter { (it.id to it.type) in selected }
        viewModelScope.launch {
            if (currentFolder == LOCAL_ALL_FOLDER) {
                manager.batchDeleteComicsInAllFolders(items)
            } else {
                manager.batchDeleteComics(currentFolder, items)
            }
            exitMultiSelect()
        }
    }

    fun moveSelectedTo(target: String) {
        val items = comics.filter { (it.id to it.type) in selected }
        viewModelScope.launch {
            if (currentFolder == LOCAL_ALL_FOLDER) {
                // 「全部」视图里没有源收藏夹，退化为复制到目标
                manager.batchCopyFavorites(target, target, items)
            } else {
                manager.batchMoveFavorites(currentFolder, target, items)
            }
            exitMultiSelect()
        }
    }

    fun copySelectedTo(target: String) {
        val items = comics.filter { (it.id to it.type) in selected }
        viewModelScope.launch {
            if (currentFolder == LOCAL_ALL_FOLDER) {
                manager.batchCopyFavorites(target, target, items)
            } else {
                manager.batchCopyFavorites(currentFolder, target, items)
            }
            exitMultiSelect()
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
