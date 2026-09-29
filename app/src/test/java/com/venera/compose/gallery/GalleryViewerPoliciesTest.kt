package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryAutoPlay
import com.venera.compose.gallery.domain.GalleryPreload
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryVolumeKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大图页三条行为档位的判据（批次 C1，2026-09-29）。
 *
 * 本项目单测没有 Robolectric，摸不到 composable，所以"该不该翻页 / 该预取哪几页"这类
 * 会算错的判断全抽进 `gallery/domain/GalleryViewerPolicies.kt`，在这里钉住。
 * 四条设置里只有"屏幕常亮"没有算式（它只是一枚 window flag），所以它不在这里 —— 验点归真机。
 */
class GalleryViewerPoliciesTest {

    // ---------- 音量键 ----------

    @Test
    fun `音量下翻到下一张 音量上翻回上一张`() {
        assertEquals(4, GalleryVolumeKeys.targetPage(enabled = true, isVolumeDown = true, currentPage = 3, pageCount = 10))
        assertEquals(2, GalleryVolumeKeys.targetPage(enabled = true, isVolumeDown = false, currentPage = 3, pageCount = 10))
    }

    @Test
    fun `关着的时候这两笔键交回系统`() {
        // 关 = 我们**一口都不吃**：按音量键还是系统的音量条。半吃（只吃 DOWN 不吃 UP）会被读成"键坏了"。
        assertNull(GalleryVolumeKeys.targetPage(enabled = false, isVolumeDown = true, currentPage = 3, pageCount = 10))
        assertNull(GalleryVolumeKeys.targetPage(enabled = false, isVolumeDown = false, currentPage = 3, pageCount = 10))
    }

    @Test
    fun `端点不循环也不弹回`() {
        // 阅读器到章末会跨章，画廊没有"下一章"这回事 —— 到底就是到底（与自动连播同一条口径）。
        assertNull(GalleryVolumeKeys.targetPage(enabled = true, isVolumeDown = true, currentPage = 9, pageCount = 10))
        assertNull(GalleryVolumeKeys.targetPage(enabled = true, isVolumeDown = false, currentPage = 0, pageCount = 10))
    }

    @Test
    fun `空列表时两笔键都不吃`() {
        assertNull(GalleryVolumeKeys.targetPage(enabled = true, isVolumeDown = true, currentPage = 0, pageCount = 0))
    }

    // ---------- 自动连播 ----------

    private fun canAdvance(
        sec: Int = 5,
        infoOpen: Boolean = false,
        isVideo: Boolean = false,
        zoomed: Boolean = false,
        currentPage: Int = 3,
        pageCount: Int = 10,
    ) = GalleryAutoPlay.shouldAdvance(sec, infoOpen, isVideo, zoomed, currentPage, pageCount)

    @Test
    fun `连播默认走 秒数 0 是关`() {
        assertTrue(canAdvance())
        assertFalse("0 秒那一档就是关闭连播", canAdvance(sec = 0))
        assertFalse(canAdvance(sec = -1))
    }

    @Test
    fun `到底就停 不循环回第一张`() {
        assertFalse(canAdvance(currentPage = 9, pageCount = 10))
        assertTrue(canAdvance(currentPage = 8, pageCount = 10))
        // 只有一张时没什么可走的。
        assertFalse(canAdvance(currentPage = 0, pageCount = 1))
    }

    @Test
    fun `弹层开着 视频页 缩放态 三样都不自动走`() {
        // 弹层与缩放态是"人正在看这一张"，自动翻走就是抢操作；
        // 视频不吃连播是用户 2026-09-29 拍板的（连播不该替人决定何时起播视频、何时出声）。
        assertFalse(canAdvance(infoOpen = true))
        assertFalse(canAdvance(isVideo = true))
        assertFalse(canAdvance(zoomed = true))
    }

    // ---------- 智能预加载 ----------

    @Test
    fun `关闭档不预取任何页`() {
        assertEquals(emptyList<Int>(), GalleryPreload.plan(GalleryPreloadMode.OFF, currentPage = 3, pageCount = 10))
    }

    @Test
    fun `下一张档只向前 不含当前页`() {
        assertEquals(listOf(4), GalleryPreload.plan(GalleryPreloadMode.NEXT, currentPage = 3, pageCount = 10))
    }

    @Test
    fun `前后两张档按距离排 近的先来`() {
        val plan = GalleryPreload.plan(GalleryPreloadMode.BOTH_TWO, currentPage = 5, pageCount = 20)
        assertEquals(listOf(6, 4, 7, 3), plan)
    }

    @Test
    fun `两端夹边界 不越界也不含负数`() {
        assertEquals(listOf(1), GalleryPreload.plan(GalleryPreloadMode.NEXT, currentPage = 0, pageCount = 3))
        assertEquals(emptyList<Int>(), GalleryPreload.plan(GalleryPreloadMode.NEXT, currentPage = 2, pageCount = 3))
        // 0 号位往前没有 -1/-2 可以夹进来，所以只剩 1、2 两张（按距离排）。
        assertEquals(listOf(1, 2), GalleryPreload.plan(GalleryPreloadMode.BOTH_TWO, currentPage = 0, pageCount = 5))
    }

    @Test
    fun `预加载档位跟着决定邻居预组合几页`() {
        // beyondViewportPageCount 是对称的（Compose 那个参数没有"只向前"这一说），
        // 所以"下一张"那一档仍会把上一张组出来 —— 不对称的那一半在 plan() 里：我们只主动预取前方。
        assertEquals(0, GalleryPreload.beyondPages(GalleryPreloadMode.OFF))
        assertEquals(1, GalleryPreload.beyondPages(GalleryPreloadMode.NEXT))
        assertEquals(2, GalleryPreload.beyondPages(GalleryPreloadMode.BOTH_TWO))
    }
}
