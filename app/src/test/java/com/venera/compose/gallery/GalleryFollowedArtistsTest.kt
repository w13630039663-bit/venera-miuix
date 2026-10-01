package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryFollowedArtists
import com.venera.compose.gallery.domain.GalleryFollowedArtistsGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 首页那一栏「正在关注的画师」的判据（批次 L · L11）。
 *
 * 这一栏**从前叫过这个名字、后来被改掉**：当时仓库里没有任何关注数据源，那名配不上任何读数，
 * 于是改成「收藏里的画师」。批次 L 有了关注名单存储（站别 + 名字 + 关注时刻），那名重新配得上 ——
 * 用户 2026-09-30 拍板换回来，并定了三条：一个都没关注时摆一句引导、卡面缺图用首字母圆座补位、
 * 搜索卡里那行重复的入口撤掉。
 *
 * 三条不是顺手就定的设计：
 * - **只有名单里的人进栏**，收藏不再自己往上挤。这一栏的语义是"你挑过的人"，
 *   混进"我们替你数出来的"就没人知道自己看到的是哪一种。
 * - **张数与卡面仍然从收藏里取**（零新请求、零新存储）：名单只存名字，图只能从已有的收藏里找。
 * - **缺图不是缺人**：关注了、收藏里却没有他名下的图，那位照样进栏，卡面交空串由 UI 换首字母座。
 *   把他挑掉等于让"关注"这件事在没收藏时凭空失效。
 */
class GalleryFollowedArtistsTest {

    private fun fav(site: GallerySite, id: Long, tags: String, savedAt: Long, preview: String = "https://cdn/$id.jpg") =
        GalleryFavorite(siteKey = site.routeKey, id = id, savedAt = savedAt, tags = tags, previewUrl = preview)

    private fun rowOf(
        follows: List<GalleryArtistFollow>,
        favorites: List<GalleryFavorite>,
        rowSize: Int = GalleryFollowedArtists.ROW_SIZE,
    ) = GalleryFollowedArtists.rowOf(follows, favorites, rowSize)

    @Test
    fun `只摆名单里的人 顺序按最近关注在前`() {
        val out = rowOf(
            follows = listOf(
                GalleryArtistFollow(GallerySite.YANDERE, "old_one", addedAt = 1_000L),
                GalleryArtistFollow(GallerySite.GELBOORU, "new_one", addedAt = 9_000L),
            ),
            favorites = listOf(
                fav(GallerySite.YANDERE, 1L, "old_one landscape", 10L),
                fav(GallerySite.GELBOORU, 2L, "new_one dress", 20L),
                fav(GallerySite.GELBOORU, 3L, "unfollowed_artist cat", 30L),
            ),
        )
        assertEquals(listOf("new_one", "old_one"), out.artists.map { it.name })
        // 收藏里那位没被关注的，绝不因为"图多"挤进这一栏。
        assertEquals(2, out.artists.size)
        assertEquals(GalleryFollowedArtistsGap.NONE, out.gap)
    }

    @Test
    fun `卡面取该站该名下最近一张收藏 张数是收藏张数`() {
        val out = rowOf(
            follows = listOf(GalleryArtistFollow(GallerySite.YANDERE, "setmen", 1L)),
            favorites = listOf(
                fav(GallerySite.YANDERE, 11L, "setmen a", savedAt = 100L, preview = "https://cdn/old.jpg"),
                fav(GallerySite.YANDERE, 12L, "setmen b", savedAt = 900L, preview = "https://cdn/new.jpg"),
                // 另一站上的同名收藏不许串过来：卡面与张数都只算本站。
                fav(GallerySite.GELBOORU, 13L, "setmen c", savedAt = 999L, preview = "https://cdn/other-site.jpg"),
            ),
        ).artists.first()
        assertEquals("https://cdn/new.jpg", out.previewUrl)
        assertEquals(2, out.count)
    }

    /**
     * 跨站同名**并成一条**（批次 M · M5，用户 2026-09-30 拍板"y 站和 g 站合并显示不要分站显示"）。
     *
     * 这一条推翻了批次 L 的判据 1（"跨站同名是两条"）。那条判据担心的是串台 ——
     * 从 yande.re 那条点过去、实际搜的是 Gelbooru 的那位。合并之后串台的出口被换掉了：
     * 点进去搜的是**全部来源**，两站的图一起出，屏上那两枚徽标说的正是这件事。
     */
    @Test
    fun `跨站同名并成一条 两枚徽标都在`() {
        val out = rowOf(
            follows = listOf(
                GalleryArtistFollow(GallerySite.YANDERE, "tokenbox", 1L),
                GalleryArtistFollow(GallerySite.GELBOORU, "tokenbox", 2L),
            ),
            favorites = emptyList(),
        )
        assertEquals(1, out.artists.size)
        assertEquals(
            listOf(GallerySite.YANDERE, GallerySite.GELBOORU),
            out.artists.first().sites,
        )
        assertEquals("tokenbox", out.artists.first().name)
        // 总数数的是**人**，不是名单条目。
        assertEquals(1, out.distinctTotal)
    }

    @Test
    fun `并成一条后张数跨站合计 卡面取两站里最近那张`() {
        val out = rowOf(
            follows = listOf(
                GalleryArtistFollow(GallerySite.YANDERE, "setmen", 1L),
                GalleryArtistFollow(GallerySite.GELBOORU, "setmen", 2L),
            ),
            favorites = listOf(
                fav(GallerySite.YANDERE, 11L, "setmen a", savedAt = 100L, preview = "https://cdn/y-old.jpg"),
                fav(GallerySite.GELBOORU, 13L, "setmen c", savedAt = 999L, preview = "https://cdn/g-new.jpg"),
                fav(GallerySite.GELBOORU, 14L, "setmen d", savedAt = 500L, preview = "https://cdn/g-mid.jpg"),
            ),
        ).artists.first()
        assertEquals(3, out.count)
        assertEquals("https://cdn/g-new.jpg", out.previewUrl)
    }

    @Test
    fun `只在一站关注就只有一枚徽标 另一站的同名收藏不算进来`() {
        // 合并只发生在**名单**上：没关注 g 站那位，就不该把 g 站的图算成他的，
        // 也不该在卡上摆一枚"点进去能搜到、但你没关注过"的徽标。
        val out = rowOf(
            follows = listOf(GalleryArtistFollow(GallerySite.YANDERE, "setmen", 1L)),
            favorites = listOf(
                fav(GallerySite.YANDERE, 11L, "setmen a", savedAt = 100L, preview = "https://cdn/y.jpg"),
                fav(GallerySite.GELBOORU, 13L, "setmen c", savedAt = 999L, preview = "https://cdn/g.jpg"),
            ),
        ).artists.first()
        assertEquals(listOf(GallerySite.YANDERE), out.sites)
        assertEquals(1, out.count)
        assertEquals("https://cdn/y.jpg", out.previewUrl)
    }

    @Test
    fun `并成一条后排位与名字都跟着最近那次关注`() {
        val out = rowOf(
            follows = listOf(
                GalleryArtistFollow(GallerySite.YANDERE, "old_one", 1_000L),
                // 这位在 y 站关注得早，但 g 站那次是全场最近 —— 合并后整条要排到最前。
                GalleryArtistFollow(GallerySite.YANDERE, "Setmen_uU", 2_000L),
                GalleryArtistFollow(GallerySite.GELBOORU, "setmen_uu", 9_000L),
            ),
            favorites = emptyList(),
        )
        assertEquals(listOf("setmen_uu", "old_one"), out.artists.map { it.name.lowercase() })
        // 摆出来的大小写用**最近那次关注**添加的那一份（判据"不篡改用户看到的字"照旧）。
        assertEquals("setmen_uu", out.artists.first().name)
        assertEquals(2, out.distinctTotal)
    }

    @Test
    fun `行里摆不下时 overflow 数的是人`() {
        // 10 条名单条目里有两对跨站同名 → 8 个人；一行 6 位，溢出 2 位。
        val follows = (1..6).map { GalleryArtistFollow(GallerySite.YANDERE, "artist$it", it.toLong()) } +
            listOf(
                GalleryArtistFollow(GallerySite.YANDERE, "dup_a", 7L),
                GalleryArtistFollow(GallerySite.GELBOORU, "dup_a", 8L),
                GalleryArtistFollow(GallerySite.YANDERE, "dup_b", 9L),
                GalleryArtistFollow(GallerySite.GELBOORU, "dup_b", 10L),
            )
        val out = rowOf(follows = follows, favorites = emptyList(), rowSize = 6)
        assertEquals(8, out.distinctTotal)
        assertEquals(6, out.artists.size)
        assertEquals(2, out.overflow)
    }

    @Test
    fun `收藏里没有他名下的图 仍然摆那位 卡面交空串`() {
        val out = rowOf(
            follows = listOf(GalleryArtistFollow(GallerySite.GELBOORU, "lakart", 1L)),
            favorites = listOf(fav(GallerySite.GELBOORU, 7L, "someone_else cat", 10L)),
        )
        assertEquals(1, out.artists.size)
        assertEquals("", out.artists.first().previewUrl)
        assertEquals(0, out.artists.first().count)
        // 这不是空栏：空栏说的是"一个都没关注"，那是另一句话。
        assertEquals(GalleryFollowedArtistsGap.NONE, out.gap)
    }

    @Test
    fun `一条关注都没有才是空栏`() {
        val out = rowOf(follows = emptyList(), favorites = listOf(fav(GallerySite.YANDERE, 1L, "setmen a", 10L)))
        assertEquals(emptyList<Any>(), out.artists)
        assertEquals(GalleryFollowedArtistsGap.NO_FOLLOWS, out.gap)
        assertEquals(0, out.distinctTotal)
    }

    @Test
    fun `名字比对不认大小写 摆出来仍用添加时那一份`() {
        val out = rowOf(
            follows = listOf(GalleryArtistFollow(GallerySite.YANDERE, "Setmen_uU", 1L)),
            favorites = listOf(fav(GallerySite.YANDERE, 5L, "setmen_uu cat", 10L)),
        ).artists.first()
        assertEquals(1, out.count)
        assertEquals("Setmen_uU", out.name)
    }

    @Test
    fun `行里摆不下的那几位念得出来`() {
        val follows = (1..10).map { GalleryArtistFollow(GallerySite.YANDERE, "artist$it", it.toLong()) }
        val out = rowOf(follows = follows, favorites = emptyList(), rowSize = 8)
        assertEquals(8, out.artists.size)
        assertEquals(2, out.overflow)
        assertEquals(10, out.distinctTotal)
    }

    // ── 圆座摆什么（批次 Q+R · 批量 4 之后追加，用户 2026-10-01 报"关注了却只有英文字母"）──
    //
    // 那一栏从前只认"名下最近一张收藏"，所以**只关注没收藏**的人必然落首字母座 ——
    // 而这一栏的语义是"人"，用户拿它跟详情页的真头像比，读出来就是缺图。
    // 现在次序是：现取到的真头像 > 名下收藏缩略图 > 首字母。判据只管优先次序与"空串不算值"，
    // 取头像那一路（外链 → pixiv → fanbox）在 UI 侧，不落库。
    @Test
    fun `圆座优先真头像 名下有收藏图也不换`() {
        assertEquals(
            "https://px/avatar.jpg",
            GalleryFollowedArtists.faceOf("https://px/avatar.jpg", "https://cdn/artwork.jpg"),
        )
    }

    @Test
    fun `头像没取到才退名下收藏图`() {
        assertEquals("https://cdn/artwork.jpg", GalleryFollowedArtists.faceOf(null, "https://cdn/artwork.jpg"))
    }

    @Test
    fun `空串与空白都不算值 两者都缺才交回首字母座`() {
        // 老收藏里偶有空 preview_url，认了它就等于交出一张白圆（[rowOf] 那条口径在这里同样成立）。
        assertEquals("https://cdn/a.jpg", GalleryFollowedArtists.faceOf("", "https://cdn/a.jpg"))
        assertEquals("https://cdn/a.jpg", GalleryFollowedArtists.faceOf("   ", "https://cdn/a.jpg"))
        assertNull(GalleryFollowedArtists.faceOf("", "  "))
        assertNull(GalleryFollowedArtists.faceOf(null, ""))
    }
}
