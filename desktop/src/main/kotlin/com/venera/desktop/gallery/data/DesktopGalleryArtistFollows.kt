package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryArtistFollows
import com.venera.compose.gallery.data.GalleryArtistFollowsStore
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistFollow
import kotlinx.coroutines.flow.StateFlow

/**
 * 桌面侧的关注画师：复用已共享的 [GalleryArtistFollowsStore]。
 *
 * 关注是**本地动作** —— 这一路与站方不发生任何写入（`GalleryArtistFollowsStore.kt:36-42`），
 * 所以两端共用一份磁盘档没有任何"两边各自向站方登记"的风险。首页那一栏的
 * "还没有关注画师"文案就依赖这条：空名单是真的没有，不是没取到。
 */
class DesktopGalleryArtistFollows(private val handle: DesktopGalleryHandle) : GalleryArtistFollows {

    private val store: GalleryArtistFollowsStore get() = GalleryArtistFollowsStore.getInstance(handle.paths)

    override val follows: StateFlow<List<GalleryArtistFollow>> get() = store.follows
    override suspend fun toggle(site: GallerySite, name: String): Boolean = store.toggle(site, name)
    override fun consumeNotice(): String? = store.consumeNotice()
}
