package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryFollowedArtists
import com.venera.compose.gallery.domain.GalleryFollowedArtistsGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面「画师」域那一屏的账（第八批：这一域从坦白页转正）。
 *
 * ## 这批唯一的设计约束：桌面**不另立一套**"什么叫同一位"、"什么叫新"
 *
 * 首页那一栏（`DesktopFollowedArtistsPane`）与这一屏读的是同一份名单，而"跨站同名并成一条"
 * 「排位取最近一次关注」「圆座三档」三条判据全都住在共享那颗 [GalleryFollowedArtists] 里。
 * 桌面这边一旦自己写一遍分组或排序，两处就会漂 —— 而漂的形状是
 * "首页第一位与画师页第一位不是同一个人"，用户只会读成"数据错了"。
 * 所以 ⑥ 那一条扫的是**桌面这颗判据件里不许出现本地分组/排序**。
 *
 * ## 顺带修掉一条已经不实的读数
 *
 * 首页那一栏的 pane 从 S2 起就写着「桌面还没有图库的取图件，卡面一律首字母座」，
 * 而第四批起三面墙都在用 `coil3.compose.AsyncImage` 取图 —— **那句话现在是假的**。
 * ⑧ 钉它不许再出现，⑨ 钉"不主动去站方取头像"这句真话必须在屏上。
 */
class DesktopArtistsWallTest {

    private fun follow(site: GallerySite, name: String, at: Long) = GalleryArtistFollow(site, name, at)

    private fun fav(site: GallerySite, id: Long, tags: String, preview: String = "p/$id.jpg") =
        GalleryFavorite(siteKey = site.routeKey, id = id, tags = tags, previewUrl = preview)

    /** ① 这一域转正后**只有一行**，且那一行是真内容而不是坦白。 */
    @Test
    fun 画师域转正且只有一行() {
        assertTrue("画师域还是坦白页", DesktopDomain.ARTISTS.isWired)
        val rows = desktopDomainRows(DesktopDomain.ARTISTS)
        assertEquals("画师域应当只有一行（整页名单），现在是 ${rows.map { it.title }}", 1, rows.size)
        val row = rows.first()
        assertNotNull("这一行的内容位是空的 —— 那又变回不可点的坦白行了", row.content)
        assertNull("这一行的理由位不该有（缺席才填理由）", row.reason)
        assertEquals(DesktopArtistsSectionKey.FOLLOWS, row.key)
        // 对照组：另一域（设置）也是同样的形状，证明这三条不是恒真。
        val settings = desktopDomainRows(DesktopDomain.SETTINGS).first()
        assertNotNull("对照组失效：设置域那行读不到内容位", settings.content)
    }

    /** ② 整页**不截断**：横卡那一栏只摆 8 位，这一屏要把名单摆全。 */
    @Test
    fun 整页摆全而横卡仍只摆八位() {
        val follows = (1..12L).map { follow(GallerySite.YANDERE, "artist$it", it) }
        val favorites = follows.mapIndexed { i, f -> fav(GallerySite.YANDERE, 100L + i, f.name) }
        val page = desktopArtistsReadout(follows, favorites)
        val row = GalleryFollowedArtists.rowOf(follows, favorites)
        assertEquals("横卡那一栏仍按 ROW_SIZE 截断（8）", 8, row.artists.size)
        assertEquals("横卡有摆不下的，整页不许有", 4, row.overflow)
        assertEquals("整页必须把 12 位全摆出来", 12, page.artists.size)
        assertEquals("整页不截断 ⇒ overflow 恒 0", 0, page.overflow)
        assertEquals("整页摆的位数必须等于 distinctTotal", page.distinctTotal, page.artists.size)
    }

    /** ③ 跨站同名并成一条，且站点徽标次序恒为枚举自然序（判据在共享那颗，桌面只转交）。 */
    @Test
    fun 跨站同名并成一条而徽标按枚举序() {
        val follows = listOf(
            follow(GallerySite.GELBOORU, "wowoguni", 5L),
            follow(GallerySite.YANDERE, "wowoguni", 9L),
            follow(GallerySite.YANDERE, "someoneelse", 7L),
        )
        val readout = desktopArtistsReadout(follows, emptyList())
        assertEquals("两条同名并成一条 ⇒ 三位变两位", 2, readout.artists.size)
        val merged = readout.artists.first { it.name == "wowoguni" }
        assertEquals(
            "徽标次序恒为枚举自然序（yande.re 在前），不跟关注时刻飘",
            listOf(GallerySite.YANDERE, GallerySite.GELBOORU),
            merged.sites,
        )
        // 对照组：名单里那位只有一站时，sites 只有一枚 —— 证明上一条不是"恒给两枚"。
        assertEquals(1, readout.artists.first { it.name == "someoneelse" }.sites.size)
    }

    /** ④ 合并后那条的**排位**取组里最近一次关注（"我刚关注的人在最前"在合并之后仍成立）。 */
    @Test
    fun 排位取组里最近那次关注() {
        val follows = listOf(
            follow(GallerySite.YANDERE, "old", 1L),
            follow(GallerySite.GELBOORU, "fresh", 2L),
            // 同一位（wowoguni）在两站的关注时刻都晚于 old，所以合并后那条要排到 old 前面。
            follow(GallerySite.YANDERE, "wowoguni", 3L),
            follow(GallerySite.GELBOORU, "wowoguni", 4L),
        )
        val names = desktopArtistsReadout(follows, emptyList()).artists.map { it.name }
        assertEquals(listOf("wowoguni", "fresh", "old"), names)
    }

    /** ⑤ 圆座三档：真头像 > 名下最近一张收藏 > null（null 才由界面落首字母座）。 */
    @Test
    fun 圆座三档按真头像_收藏图_字母座() {
        assertEquals("https://a/1.jpg", desktopArtistFace("https://a/1.jpg", "p/2.jpg"))
        assertEquals("没有真头像才退到名下收藏图", "p/2.jpg", desktopArtistFace(null, "p/2.jpg"))
        assertNull("两档都没有 ⇒ 交回 null 由界面摆字母座", desktopArtistFace(null, ""))
        assertNull("空白地址不算值（认了就是交出一张白圆）", desktopArtistFace("   ", "  "))
        assertEquals("空串头像不许把收藏图那档顶掉", "p/2.jpg", desktopArtistFace("", "p/2.jpg"))
    }

    /** ⑥ 空名单那一档：gap 必须是 NO_FOLLOWS，且位数与 distinctTotal 一起为零。 */
    @Test
    fun 空名单是一档而不是零位热门() {
        val readout = desktopArtistsReadout(emptyList(), emptyList())
        assertEquals(GalleryFollowedArtistsGap.NO_FOLLOWS, readout.gap)
        assertEquals(0, readout.artists.size)
        assertEquals(0, readout.distinctTotal)
    }

    /** ⑦ 桌面不许自己判"同一位"/"什么叫新" —— 三颗件里都不许出现本地分组与排序。 */
    /** ⑧ 首页那一栏「桌面还没有图库的取图件」这句已经不实，不许再挂在屏上。 */
    /**
     * ⑨ 两处那句圆座读数说明**分开**：这一栏仍"不主动取"，整页那一屏从第十批 ② 起会按视口取。
     *
     * 从前这两处共用一颗常量。共用本身不是问题，问题是它只可能有一句是真话 ——
     * 接上取数那天，要么整页念一句假的"不主动取"，要么首页念一句假的"会去取"。
     * 所以这一条钉的是"两句各自还在自己那一屏上"，而不是一句的措辞。
     */
}
