package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistAlias
import com.venera.compose.gallery.domain.GalleryArtistRecord
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GalleryLegOutcome
import com.venera.compose.gallery.domain.GalleryTagFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索回 0 张时的**站内画师别名解析**判据（批次 J，方案 `gallery-artist-alias-2026-09.md`）。
 *
 * 用户拍板的原话是这条功能的边界，也是这里每条用例的靶子：
 * 「在明确的 Artist 搜索场景中，某站原始查询返回 0 张时，查询该站 Artist 元数据，
 * 尝试解析 canonical name / aliases / source-specific identifier；
 * **只有获得明确站内关联时才重搜，否则保持 0 结果**」。
 *
 * 三条不能松的口径：
 * - **只在 0 张时动**。抽样实测里有一档"原词有货、正名反而更少"（`a-10` 12 张 → 正名 1 张），
 *   非 0 张时替换就会把用户已经能看到的图换少 —— 那是净损失。
 * - **精确同名才算数**。`artist.json?name=` 是**前缀匹配**（实测 `name=se` 回 16 条），
 *   拿第一条就是拿错人。
 * - **不许静默换词**。换过了 must 被念出来（[GalleryArtistAlias.notice]），
 *   屏上那排胶囊与历史仍存用户原词。
 */
class GalleryArtistAliasTest {

    private val setmen = GalleryArtistRecord(id = 39892, name = "setmen", aliasId = 46523)

    @Test
    fun `只有一枚包含型标签时才算明确的画师场景`() {
        assertEquals("setmen", GalleryArtistAlias.singleArtistToken(listOf(GalleryTagFilter("setmen"))))
        // 多条件时 0 张不一定是画师名的锅，而且会把另一枚条件的锅算到换名上。
        assertNull(GalleryArtistAlias.singleArtistToken(listOf(GalleryTagFilter("setmen"), GalleryTagFilter("1girl"))))
        // 只有排除项：那串根本没有"要找的画师"，无处可换。
        assertNull(GalleryArtistAlias.singleArtistToken(listOf(GalleryTagFilter("rating:general", excluded = true))))
        assertNull(GalleryArtistAlias.singleArtistToken(emptyList()))
    }

    @Test
    fun `别名记录必须精确同名 前缀匹配的那几条不算`() {
        // 站方这条是前缀查询，实测能回一屏名字相近的记录 —— 取第一条就是拿错人。
        val records = listOf(
            GalleryArtistRecord(1, "setmenishi", 10),
            setmen,
            GalleryArtistRecord(2, "setmen_irl", 11),
        )
        assertEquals(setmen, GalleryArtistAlias.exactRecord(records, "setmen"))
        assertNull(GalleryArtistAlias.exactRecord(records, "setmenXXX"))
    }

    @Test
    fun `没有别名指针的记录不替换`() {
        val noAlias = GalleryArtistRecord(id = 1, name = "wowoguni", aliasId = null)
        assertNull(GalleryArtistAlias.exactRecord(listOf(noAlias), "wowoguni"))
    }

    /**
     * 上面那条 `aliasId != null` 是**换名那一路**的条件（没有指针就无从替换）。
     * 批次 L 取画师外链要的是"**这条记录本身**"，而真读数里正名记录的 `alias_id` 恒为 null
     * —— 2026-09-30 真机就是栽在复用上面那条上：`tokenbox` 站方给了三条外链
     * （pixiv / 推 / fanbox），屏上却一枚都不摆，因为按换名那条判据它"不存在"。
     * 所以这里单独一条，且把两者的差别钉住。
     */
    @Test
    fun `取记录本身不看别名指针 但名字仍要全等`() {
        val canonical = GalleryArtistRecord(
            id = 46523,
            name = "tokenbox",
            aliasId = null,
            urls = listOf("https://www.pixiv.net/users/7075448", "https://twitter.com/Setmen_uU"),
        )
        val records = listOf(GalleryArtistRecord(1, "tokenbox_hime", 9), canonical)
        assertEquals(canonical, GalleryArtistAlias.recordByName(records, "tokenbox"))
        // 前缀查询带回来的邻居记录不算命中（拿错人比不摆更坏）。
        assertNull(GalleryArtistAlias.recordByName(records, "tokenbox_"))
        // 与换名那条的差别就钉在这里：同一条记录，一个取得到、一个取不到。
        assertNull(GalleryArtistAlias.exactRecord(records, "tokenbox"))
    }

    @Test
    fun `正名与原词相同时不重搜`() {
        // 站方的指针偶尔指回自己（大小写与规范化差异都在这一档里）。相同时重搜只是白跑一笔。
        assertNull(GalleryArtistAlias.canonicalName("tokenbox", "tokenbox"))
        assertEquals("tokenbox", GalleryArtistAlias.canonicalName("tokenbox", "setmen"))
    }

    @Test
    fun `换名只换那一枚 其余条件与排除面原样`() {
        val filters = listOf(GalleryTagFilter("setmen"), GalleryTagFilter("rating:general", excluded = true))
        val next = GalleryArtistAlias.substitute(filters, "setmen", "tokenbox")!!

        assertEquals(listOf("tokenbox", "rating:general"), next.map { it.name })
        assertEquals(listOf(false, true), next.map { it.excluded })
        // 换名之后必须真的不一样：正名 == 原词那一档不许产出一条"已按 X 搜索"的假读数。
        assertNull(GalleryArtistAlias.substitute(filters, "setmen", "setmen"))
    }

    @Test
    fun `gelbooru 那条腿不参与解析`() {
        // 它的匿名接口给不出可用的别名路由（方案 §1.6 逐个端点试过）。
        // 这条判据存在的意义就是"别屏上暗示两站都会自动换名"。
        assertTrue(GalleryArtistAlias.supports(GallerySite.YANDERE))
        assertNull(GalleryArtistAlias.canonicalNameFor(GallerySite.GELBOORU, "setmen", "tokenbox"))
        assertEquals("tokenbox", GalleryArtistAlias.canonicalNameFor(GallerySite.YANDERE, "setmen", "tokenbox"))
    }

    @Test
    fun `读数分两档 换到了有货与换到了还是没货`() {
        // 两档必须不同话：只说"已按 tokenbox 搜索"而屏上还是 0 张，用户会以为那一下没生效。
        assertEquals(
            "yande.re 把 setmen 记作 tokenbox 的别名，已按 tokenbox 搜索",
            GalleryArtistAlias.notice("setmen", "tokenbox", hasResults = true),
        )
        assertEquals(
            "yande.re 把 setmen 记作 tokenbox 的别名，按 tokenbox 也没有图",
            GalleryArtistAlias.notice("setmen", "tokenbox", hasResults = false),
        )
    }

    @Test
    fun `只有第一页答上了且回 0 张的那条腿才进解析链`() {
        val empty = GalleryLegOutcome(GallerySite.YANDERE, emptyList(), answered = true, GalleryLegGuard.NO_CONTENT)
        val oneGallery = GalleryLegOutcome(GallerySite.YANDERE, listOf(GalleryPost(GallerySite.YANDERE, 7)), true, null)
        val filters = listOf(GalleryTagFilter("setmen"))

        assertEquals("setmen", GalleryArtistAlias.resolveToken(GallerySite.YANDERE, 1, filters, empty))
        // 红线：**原词有货就不许换**。抽样里有一档 `a-10` 原词 12 张、正名只有 1 张，
        // 非 0 张时替换是把用户已经看得见的图换少。
        assertNull(GalleryArtistAlias.resolveToken(GallerySite.YANDERE, 1, filters, oneGallery))
        // 这一腿没答上（超时、401、非 JSON）：那由缺席读数去说，不该再叠一句"我们换过词"。
        assertNull(
            GalleryArtistAlias.resolveToken(
                GallerySite.YANDERE,
                1,
                filters,
                GalleryLegOutcome(GallerySite.YANDERE, emptyList(), answered = false, "超过 12s 没返回"),
            ),
        )
        // 续页不换：0 张在这里的含义是"翻到头了"，不是"这个名字是别名"。
        assertNull(GalleryArtistAlias.resolveToken(GallerySite.YANDERE, 2, filters, empty))
        // 另一站没有可用的解析链（见上一条用例），换过去就是屏上暗示"两站都会自动换名"。
        assertNull(
            GalleryArtistAlias.resolveToken(
                GallerySite.GELBOORU,
                1,
                listOf(GalleryTagFilter("setmen")),
                GalleryLegOutcome(GallerySite.GELBOORU, emptyList(), true, GalleryLegGuard.NO_CONTENT),
            ),
        )
        // 多条件时 0 张的成因不止画师名，换名会把另一枚条件的锅算到这一枚上。
        assertNull(
            GalleryArtistAlias.resolveToken(
                GallerySite.YANDERE,
                1,
                listOf(GalleryTagFilter("setmen"), GalleryTagFilter("1girl")),
                empty,
            ),
        )
    }
}
