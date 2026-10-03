package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryArtistAvatarStore
import com.venera.compose.gallery.data.GalleryArtistAvatars
import com.venera.compose.gallery.data.GallerySite
import com.venera.desktop.platform.DesktopLogger

/**
 * 桌面侧的头像地址档：复用已共享的 [GalleryArtistAvatarStore]。
 *
 * 那颗 store 是 S1 里唯一需要 [com.venera.compose.data.platform.Logger] 的一份 —— 它报的两句
 * （"头像地址档没写成" / "档读不出来，已另存为 …"）说的都是**可能覆盖用户数据**的事，
 * 删掉就成了静默交错。桌面这边落 [DesktopLogger]（stdout），Android 那边落 logcat，
 * 两边同一句话、同一个 tag。
 */
class DesktopGalleryArtistAvatars(private val handle: DesktopGalleryHandle) : GalleryArtistAvatars {

    private val store: GalleryArtistAvatarStore
        get() = GalleryArtistAvatarStore.getInstance(handle.paths, DesktopLogger)

    override fun peek(site: GallerySite, name: String, source: String): String? =
        store.peek(site, name, source)

    override suspend fun remember(site: GallerySite, name: String, source: String, avatar: String?) =
        store.remember(site, name, source, avatar)
}
