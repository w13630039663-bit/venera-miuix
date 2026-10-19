package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 卡片上那枚「几 P」的判据与接线（2026-10-18，用户「要能在图片卡片上另加看出这张有几页，
 * 而不是点进作品才显示有多少 P」）。
 *
 * 这一条立案时先量过数据面：**两腿本来就给页数**（榜腿 `illust_page_count` 是字符串、
 * 搜索腿 `pageCount` 是数字，读数记在 `PixivFeedParse` 那两条用例里），从前解析时把这格丢了 ——
 * 所以这一枚零额外请求，不是"为了角标多打一跳"。
 */
class GalleryCardPagesBadgeTest {

    private val appMain = RepoSources.appMain

    private fun src(rel: String): String {
        val f = File(appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    /** 注释行不参与匹配（与 `TokenBudgetFrozenTest` 同一把尺子）。 */
    private fun code(rel: String): List<String> = src(rel).lines().map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private fun List<String>.countWith(needle: String): Int = count { it.contains(needle) }

    private fun post(pageCount: Int?) = GalleryPost(
        site = GallerySite.PIXIV,
        id = 1L,
        pageCount = pageCount,
    )

    // ── 一、判据：只有"站方说这一作不止一页"才摆 ────────────────────────

    @Test
    fun `页数两枚都摆得出，单页与缺格都不摆`() {
        assertEquals("4P", post(4).pagesBadge)
        assertEquals("12P", post(12).pagesBadge)
        // 单页摆一枚"1P"是噪声（那张卡本来就只有一张）。
        assertEquals(null, post(1).pagesBadge)
        // null = 这一腿站方压根没给这一格（图库三站与收藏存档都是这种）⇒ 不摆，也不写"1P"：
        // "不知道有几页"与"站方说只有一页"是两句话。
        assertEquals(null, post(null).pagesBadge)
    }

    @Test
    fun `退化页数不当成多页`() {
        assertEquals(null, post(0).pagesBadge)
        assertEquals(null, post(-3).pagesBadge)
    }

    // ── 二、接线：两个宿主各摆一枚，形状只有一颗 ────────────────────────

    /**
     * pixiv 首页那一族卡（排行榜 / 推荐）与六处墙共用的那张卡走的是**两个件** ——
     * AI 角标从前就因为只接了后者而整域零枚（2026-10-18 用户「开了 AI 角标一个都没见到」）。
     * 所以这里钉"两处各一枚"，而不是钉某一处有。
     */
    @Test
    fun `两处卡各摆一枚，胶囊形状仍只有一颗`() {
        assertEquals(1, code("com/venera/compose/gallery/ui/GalleryScreen.kt").countWith("GalleryPagesPill("))
        assertEquals(1, code("com/venera/compose/gallery/ui/GalleryPixivHome.kt").countWith("GalleryPagesPill("))
        // 定义只在共用那颗件里：为某一处另画一枚胶囊，迟早与另一处分叉。
        val caption = code("com/venera/compose/gallery/ui/GalleryCardCaption.kt")
        assertEquals(1, caption.countWith("internal fun BoxScope.GalleryPagesPill("))
        assertEquals("胶囊形状仍只有一颗", 1, caption.countWith("internal fun BoxScope.GalleryCornerPill("))
    }

    /**
     * ⑦ 页轴播放器那一侧：每一页的行模型带页序位。
     *
     * 钉这一格的理由与缓存键同一条（那把尺子在 `GalleryPixivPageAxisTest`）：四页共用一个号，
     * 而"这是第几页"必须在行模型上，才不会出现"某一处忘了带页位"那种只在这一条路上才错的裂口。
     */
    @Test
    fun `页位也在行模型上，非页轴的那些恒 null`() {
        assertEquals(null, post(4).pageOrdinal)
        val p = GalleryPost(site = GallerySite.PIXIV, id = 1L, pageCount = 4, pageOrdinal = 2)
        assertEquals(2, p.pageOrdinal)
        // 页序位不参与身份键：收藏/历史/去重仍按"这一条作品"记，翻到第 3 页不是收藏了第 3 页。
        assertEquals("pixiv:1", p.uid)
        assertEquals("pixiv:1", post(4).uid)
    }

    // ── 三、不给的东西不编：三站与收藏存档都不许出现页数 ────────────────

    /**
     * booru 三站的字段表里没有"这一作几页"这一说（一张 = 一幅），收藏存档也没有落这一格。
     * 谁要是为了"看起来齐"在那里填一个 1，卡上就多出一枚"1P"—— 那是编出来的读数。
     */
    @Test
    fun `三站与收藏存档都没有这一格，所以那一侧恒不摆`() {
        listOf(
            "com/venera/compose/gallery/data/YandeReClient.kt",
            "com/venera/compose/gallery/data/GelbooruClient.kt",
            "com/venera/compose/gallery/data/SafebooruClient.kt",
            "com/venera/compose/gallery/data/GalleryFavoritesStore.kt",
        ).forEach { rel ->
            assertEquals("$rel 不该给行模型填页数", 0, code(rel).countWith("pageCount"))
        }
    }
}
