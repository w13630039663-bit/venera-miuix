package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GalleryTagFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标签预算与"站方语法串 ↔ chips"的判据。
 *
 * 预算那条是实测：Danbooru 匿名第 3 枚标签直接 **422**
 * （`PostQuery::TagLimitError —— You cannot search for more than 2 tags at a time.`），
 * 而 yande.re 没有这个限制。UI 要在**加标签那一刻**就拦住并报原因，
 * 不等发请求 —— 但 422 的 message 仍会被翻出来兜底（哪天站方改了预算就是它说话）。
 */
class GallerySearchBudgetTest {

    @Test
    fun `两站的预算口径不同`() {
        assertEquals(2, GallerySearch.tagBudget(GallerySite.DANBOORU))
        assertNull(GallerySearch.tagBudget(GallerySite.YANDERE))
        assertTrue(GallerySearch.fitsTagBudget(GallerySite.YANDERE, afterAdding = 99))
    }

    @Test
    fun `第 3 枚就装不下`() {
        assertTrue(GallerySearch.fitsTagBudget(GallerySite.DANBOORU, afterAdding = 1))
        assertTrue(GallerySearch.fitsTagBudget(GallerySite.DANBOORU, afterAdding = 2))
        assertFalse(GallerySearch.fitsTagBudget(GallerySite.DANBOORU, afterAdding = 3))
    }

    @Test
    fun `排除项同样吃预算`() {
        // 站方数的是空格分隔的段数，不在乎前面有没有 `-`（实测 hime_cut -long_hair 就是 2 枚）。
        val two = listOf(GalleryTagFilter("hime_cut"), GalleryTagFilter("long_hair", excluded = true))
        assertEquals(2, GallerySearch.queryOf(two).split(' ').size)
        assertFalse(GallerySearch.fitsTagBudget(GallerySite.DANBOORU, afterAdding = two.size + 1))
    }

    @Test
    fun `预算提示只在有限制的站说`() {
        val notice = GallerySearch.budgetNotice(GallerySite.DANBOORU)
        assertTrue(notice != null && notice.contains("2") && notice.contains("Danbooru"))
        assertNull(GallerySearch.budgetNotice(GallerySite.YANDERE))
        // 登录之后那句话必须点明"会员也还是 2"——免费注册不放宽，只有付费的 Gold 才变 6。
        val signedIn = GallerySearch.budgetNotice(GallerySite.DANBOORU, danbooruLevel = 20)
        assertTrue(signedIn != null && signedIn.contains("Gold"))
    }

    /**
     * 登录后的预算按**站方等级表**算（`help:users`，2026-09-26 抄录）：
     * 匿名 / Restricted(10) / Member(20) 都是 2，Gold(30) 是 6，Platinum(31)+ 不限。
     *
     * 这条单测存在的唯一理由是**防止有人想当然**：登录并不等于放宽，免费注册只到 Member。
     * 哪天有人把 `tagsPerSearch` 改成"登录了就 6"，这个用例会当场红。
     */
    @Test
    fun `登录后的预算跟着账号等级`() {
        // 未登录 / 已登录但等级拿不到 → 匿名口径。
        assertEquals(2, GallerySearch.tagBudget(GallerySite.DANBOORU, danbooruLevel = null))
        // Restricted：站方写明"验证邮箱前与未注册用户相同"。
        assertEquals(2, GallerySearch.tagBudget(GallerySite.DANBOORU, danbooruLevel = 10))
        // Member：免费注册就是这一档，**没有放宽**。
        assertEquals(2, GallerySearch.tagBudget(GallerySite.DANBOORU, danbooruLevel = 20))
        // Gold：付费升级，6 枚。
        assertEquals(6, GallerySearch.tagBudget(GallerySite.DANBOORU, danbooruLevel = 30))
        assertTrue(GallerySearch.fitsTagBudget(GallerySite.DANBOORU, afterAdding = 6, danbooruLevel = 30))
        assertFalse(GallerySearch.fitsTagBudget(GallerySite.DANBOORU, afterAdding = 7, danbooruLevel = 30))
        // Platinum 及以上：不限。
        assertNull(GallerySearch.tagBudget(GallerySite.DANBOORU, danbooruLevel = 31))
        assertTrue(GallerySearch.fitsTagBudget(GallerySite.DANBOORU, afterAdding = 99, danbooruLevel = 31))
        // yande.re 与账号无关，永远不限。
        assertNull(GallerySearch.tagBudget(GallerySite.YANDERE, danbooruLevel = 20))
    }

    @Test
    fun `语法串与 chips 往返一致`() {
        val filters = listOf(GalleryTagFilter("hime_cut"), GalleryTagFilter("ai_generated", excluded = true))
        val query = GallerySearch.queryOf(filters)
        assertEquals("hime_cut -ai_generated", query)
        assertEquals(filters, GallerySearch.parseQuery(query))
    }

    @Test
    fun `脏输入拆不出东西时给空表而不是崩`() {
        assertEquals(emptyList<GalleryTagFilter>(), GallerySearch.parseQuery("   "))
        // 光一个 "-" 不是排除项：它被丢掉，紧跟的 "-x" 才是真排除项。
        assertEquals(listOf(GalleryTagFilter("x", excluded = true)), GallerySearch.parseQuery("- -x"))
        assertEquals(listOf(GalleryTagFilter("x", excluded = true)), GallerySearch.parseQuery("  -x  "))
    }
}
