package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryArtistFollows
import com.venera.compose.gallery.data.GalleryArtistFollowsStore
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistFollow
import kotlinx.coroutines.flow.StateFlow

/**
 * 桌面侧的关注画师：复用已共享的 [GalleryArtistFollowsStore]。
 *
 * 关注在**这一侧**是纯本地动作 —— 桌面没有 pixiv 腿（`GalleryPorts.pixiv` 是可空槽，桌面交 null），
 * 所以这里不发生任何"替用户向站方登记"的写入，两端共用一份磁盘档也没有"两边各自登记"的风险。
 * 首页那一栏的"还没有关注画师"文案就依赖这条：空名单是真的没有，不是没取到。
 *
 * ⚠️ Android 那一侧从 2026-10-19 起对 **pixiv** 会写站方（画师页那颗关注钮）。
 * 那一族写只从 Android 的画师页发出，桌面走不到 —— 所以本颗件不需要跟着变，
 * 但 `setFollowed` 得实现出来（接口是两端共用的）。
 */
class DesktopGalleryArtistFollows(private val handle: DesktopGalleryHandle) : GalleryArtistFollows {

    private val store: GalleryArtistFollowsStore get() = GalleryArtistFollowsStore.getInstance(handle.paths)

    override val follows: StateFlow<List<GalleryArtistFollow>> get() = store.follows
    override suspend fun toggle(site: GallerySite, name: String): Boolean = store.toggle(site, name)
    override suspend fun setFollowed(site: GallerySite, name: String, followed: Boolean) {
        store.setFollowed(site, name, followed)
    }
    override fun consumeNotice(): String? = store.consumeNotice()
}
