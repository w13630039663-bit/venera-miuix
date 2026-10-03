package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryFavorites
import com.venera.compose.gallery.data.GalleryFavoritesStore
import com.venera.compose.gallery.data.GalleryPost
import kotlinx.coroutines.flow.StateFlow

/**
 * 桌面侧的画廊收藏：直接复用已共享的 [GalleryFavoritesStore]（S1 让它吃 [com.venera.compose.data.platform.PathProvider]）。
 *
 * 这里不另立第二份磁盘格式是判据不是省事：Android 那侧的 `gallery_favorites.json` 字段名与形状
 * 由那颗类自持，桌面重抄一份迟早漂（漂的那一半通常是被抄的那份，而且没有任何一条错误会指向它）。
 *
 * 形状照 `AndroidGalleryFavorites`：**不在构造里取单例**，成员体里现取，
 * "第一次真正用到才建那颗 store"的时机与 Android 侧逐点相同。
 */
class DesktopGalleryFavorites(private val handle: DesktopGalleryHandle) : GalleryFavorites {

    private val store: GalleryFavoritesStore get() = GalleryFavoritesStore.getInstance(handle.paths)

    override val favorites: StateFlow<List<GalleryFavorite>> get() = store.favorites
    override suspend fun toggle(post: GalleryPost): Boolean = store.toggle(post)
    override suspend fun removeAll(uids: Collection<String>) = store.removeAll(uids)
    override suspend fun clear() = store.clear()
    override fun consumeNotice(): String? = store.consumeNotice()
}
