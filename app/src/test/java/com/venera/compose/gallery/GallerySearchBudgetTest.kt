package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GalleryTagFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标签预算与"站方语法串 ↔ chips"的判据。
 *
 * ⚠️ **现在的结论是"两站都不限"**，与从前（Danbooru 匿名只有 2 枚、第 3 枚直接 422）不同。
 * 依据都是实测：
 * - Gelbooru：官方 wiki `howto:api` 与 `howto:cheatsheet` **通篇没有任何标签数上限**
 *   （`limit` 那项限的是"一次取回多少条"），2026-09-26 实测 **7 枚一次通过、HTTP 200**；
 * - yande.re：一直没有限制（`hime -kaibutsu_oujo` 两枚照样通）。
 *
 * 那为什么 `tagBudget` 这套判据还留着？因为它是"**发请求之前先拦住**"的唯一落点 ——
 * 哪天接一个有限额的站，只要在那一个函数里返回一个数，UI 就会自动在
 * **加标签那一刻**拦住并报原因，不必再改一遍所有调用点。
 * 所以下面的用例是**把这个落点钉住**（两站恒 null、`fitsTagBudget` 恒真），
 * 而不是"测一个现在不存在的限制"。
 */
class GallerySearchBudgetTest {

    @Test
    fun `两站现在都不限标签数`() {
        assertNull(GallerySearch.tagBudget(GallerySite.GELBOORU))
        assertNull(GallerySearch.tagBudget(GallerySite.YANDERE))
        // 不限时任何枚数都装得下 —— 包括用户一次粘进来一大串的情况。
        assertTrue(GallerySearch.fitsTagBudget(GallerySite.GELBOORU, afterAdding = 1))
        assertTrue(GallerySearch.fitsTagBudget(GallerySite.GELBOORU, afterAdding = 99))
        assertTrue(GallerySearch.fitsTagBudget(GallerySite.YANDERE, afterAdding = 99))
    }

    @Test
    fun `不限的站不摆预算提示`() {
        // 提示行是给"有限制而且被截断"那种情况用的。两站都不限时它恒为 null，
        // 否则页尾会长期挂一句没有信息量的废话。
        assertNull(GallerySearch.budgetNotice(GallerySite.GELBOORU))
        assertNull(GallerySearch.budgetNotice(GallerySite.YANDERE))
    }

    @Test
    fun `排除项与普通项在语法串里同等计数`() {
        // 这条**与预算无关、但是同一个语法前提**：站方数的是空格分隔的段数，
        // 不在乎那一段前面有没有 `-`。将来接有限额的站时，排除项照样要吃额度。
        val two = listOf(GalleryTagFilter("hime_cut"), GalleryTagFilter("long_hair", excluded = true))
        assertEquals(2, GallerySearch.queryOf(two).split(' ').size)
        assertEquals("hime_cut -long_hair", GallerySearch.queryOf(two))
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

    /**
     * 分页到底的判据。这条与预算无关，但同属 `GallerySearch` 的纯函数层，
     * 放这里一起钉住（它决定了"还有下一页吗"，写反会让页尾永远挂着"加载更多"）。
     */
    @Test
    fun `给回的条数少于要的条数就是到底了`() {
        assertTrue(GallerySearch.isExhausted(returned = 42, requestedLimit = 100))
        assertTrue(GallerySearch.isExhausted(returned = 0, requestedLimit = 100))
        // 恰好吃满不算到底（可能还有下一页）。
        assertTrue(!GallerySearch.isExhausted(returned = 100, requestedLimit = 100))
    }
}
