package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryTagCollapse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标签墙「收起 / 展开」的判据（2026-10-01，信息卡按图二重排那一批）。
 *
 * 这一层要钉住的是**三条错了也看不出来**的读数：
 *
 * 1. **收起态只截尾巴，不重排**。摆出来的必须是**原串的前 N 枚**（站方给的顺序带含义：
 *    画师档的常用 tag 在前）。如果用 `takeLast` 或先去重再截，屏上依然是一排像样的胶囊，
 *    只有对账的时候才发现少的那几枚不对。
 * 2. **`+N` 那个数要与实际藏起来的枚数逐字相等**。差一枚不会有任何报错，
 *    只会让人以为这张画少一个标签。
 * 3. **展开态一律全给**，与上限无关。展开之后还留着一条截断线，那枚「展开全部」就是假开关
 *    （本仓零容忍假按钮）。
 *
 * 另外两条边界也在这里挡着：**正好等于上限的一桶不摆 `+0`**，
 * 以及 `overflows` 与 `slice(false).hasMore` **必须同真同假** —— 不同的话，
 * 要么节标题那枚「展开全部」是假的、要么有一桶永远展不开。
 */
class GalleryTagCollapseTest {

    private fun tags(n: Int): List<String> = (1..n).map { "tag$it" }

    @Test
    fun `收起态摆前六枚并把剩下的如实计入 hidden`() {
        val slice = GalleryTagCollapse.slice(tags(16), expanded = false)
        assertEquals(listOf("tag1", "tag2", "tag3", "tag4", "tag5", "tag6"), slice.visible)
        assertEquals(10, slice.hiddenCount)
        assertTrue(slice.hasMore)
    }

    @Test
    fun `正好等于上限的一桶不留加零`() {
        // 摆一枚「+0」是纯噪声，而且会让人以为"还有东西没看见"。
        val slice = GalleryTagCollapse.slice(tags(GalleryTagCollapse.COLLAPSED_PER_GROUP), expanded = false)
        assertEquals(GalleryTagCollapse.COLLAPSED_PER_GROUP, slice.visible.size)
        assertEquals(0, slice.hiddenCount)
        assertFalse(slice.hasMore)
    }

    @Test
    fun `短于上限的一桶原样全给`() {
        val slice = GalleryTagCollapse.slice(tags(3), expanded = false)
        assertEquals(tags(3), slice.visible)
        assertEquals(0, slice.hiddenCount)
    }

    @Test
    fun `展开态一律全给不看上限`() {
        val slice = GalleryTagCollapse.slice(tags(40), expanded = true)
        assertEquals(40, slice.visible.size)
        assertEquals(0, slice.hiddenCount)
        assertFalse(slice.hasMore)
    }

    @Test
    fun `空桶不给读数也不留加零`() {
        val slice = GalleryTagCollapse.slice(emptyList(), expanded = false)
        assertTrue(slice.visible.isEmpty())
        assertEquals(0, slice.hiddenCount)
        assertFalse(slice.hasMore)
    }

    @Test
    fun `上限为零时一枚都不摆但把枚数如实报出来`() {
        val slice = GalleryTagCollapse.slice(tags(4), expanded = false, limit = 0)
        assertTrue(slice.visible.isEmpty())
        assertEquals(4, slice.hiddenCount)
        assertTrue(slice.hasMore)
    }

    @Test
    fun `负数上限按零处理不抛异常`() {
        // 这个值只会来自常量，取值离谱时该让屏上退化得难看一点，而不是把整页崩掉。
        val slice = GalleryTagCollapse.slice(tags(2), expanded = false, limit = -5)
        assertTrue(slice.visible.isEmpty())
        assertEquals(2, slice.hiddenCount)
    }

    @Test
    fun `展开开关的判据与收起的读数同真同假`() {
        // 不同真同假的话：要么节标题那枚「展开全部」是个假开关，要么有一桶永远展不开。
        for (size in 0..20) {
            val list = tags(size)
            assertEquals(
                "size=$size",
                GalleryTagCollapse.overflows(list),
                GalleryTagCollapse.slice(list, expanded = false).hasMore,
            )
        }
        // 边界档也一起对一遍：上限为零时只要有一枚可藏，开关就该亮。
        assertEquals(true, GalleryTagCollapse.overflows(tags(1), limit = 0))
        assertEquals(false, GalleryTagCollapse.overflows(emptyList(), limit = 0))
    }
}
