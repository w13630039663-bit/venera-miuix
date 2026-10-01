package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GallerySheetScroll
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 半模态滚动位移去向的判据（`GallerySheetScroll`）。
 *
 * 为什么这几条值得钉：返回值是"**我吃掉了多少**"（嵌套滚动的约定，与本能的"我放了多少"相反），
 * 写反的后果是面板又开始抖 —— 而那件事在屏上只在"内容顶到满屏 + 甩一下"这个组合下才出现，
 * 平时看起来一切正常。设备掉线时这是唯一能跑的证据。
 *
 * 用例里的四个数取自真机读数：面板在满屏态被甩时那一份残余位移约 120~400px/帧，
 * 残余速度在 2000~8000 px/s 一档（`GalleryPostScreen` 那次录屏的能量峰值）。
 */
class GallerySheetScrollTest {

    @Test
    fun `拖拽那一份全部放行给面板`() {
        // 手指按着往下拖 = 用户在用这个手势关面板，一份都不能扣。
        assertEquals(0f, GallerySheetScroll.consumedFromDrag(120f), 1e-4f)
        assertEquals(0f, GallerySheetScroll.consumedFromDrag(-360f), 1e-4f)
        assertEquals(0f, GallerySheetScroll.consumedFromDrag(0f), 1e-4f)
    }

    @Test
    fun `惯性那一份整体扣下两个方向都一样`() {
        // 上滑到底再甩（负向）是抖动最常见的起点。
        assertEquals(-480f, GallerySheetScroll.consumedFromFling(-480f), 1e-4f)
        // 回弹那一下（正向）同样会把面板推离锚点。
        assertEquals(200f, GallerySheetScroll.consumedFromFling(200f), 1e-4f)
        // 没有富余时扣 0，与"本来就没得吃"等价（避免出现负数被当成反向消费）。
        assertEquals(0f, GallerySheetScroll.consumedFromFling(0f), 1e-4f)
    }

    @Test
    fun `甩动残余速度那一份也整体扣下`() {
        assertEquals(6400f, GallerySheetScroll.consumedFromFling(6400f), 1e-4f)
    }
}
