package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GallerySheetScroll
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 半模态滚动位移去向的判据（`GallerySheetScroll`）。
 *
 * 为什么这几条值得钉：返回值是"**我吃掉了多少**"（嵌套滚动的约定，与本能的"我放了多少"相反），
 * 写反的后果是面板又开始抖 —— 而那件事在屏上只在"内容顶到满屏 + 拉到底 + 再下拉"这个组合下才出现，
 * 平时看起来一切正常。设备掉线时这是唯一能跑的证据。
 *
 * 2026-10-05 起 `consumedFromDrag` 由「恒不扣」改成**三档条件**（相位 0/1/2 的判据见实现）：
 * 只有「面板已 Expanded **且** 内容已到底」才扣 —— 其余两档放行，保住「下拉关面板」的手势。
 * 下面三条正是照这三档写的。
 *
 * 用例里的四个数取自真机读数：面板在满屏态被甩时那一份残余位移约 120~400px/帧，
 * 残余速度在 2000~8000 px/s 一档（`GalleryPostScreen` 那次录屏的能量峰值）。
 */
class GallerySheetScrollTest {

    @Test
    fun `面板没顶格时拖拽那一份全部放行给面板`() {
        // 半开档：用户就是在用「在内容上往下拖」这个手势关面板，一份都不能扣。
        // 「内容到没到底」在这一档不影响结论（门是 &&）。
        assertEquals(0f, GallerySheetScroll.consumedFromDrag(120f, panelExpanded = false, contentAtBottom = false), 1e-4f)
        assertEquals(0f, GallerySheetScroll.consumedFromDrag(120f, panelExpanded = false, contentAtBottom = true), 1e-4f)
        assertEquals(0f, GallerySheetScroll.consumedFromDrag(-360f, panelExpanded = false, contentAtBottom = true), 1e-4f)
    }

    @Test
    fun `面板顶格但内容还能滚时仍然放行`() {
        // 下拉的第一顺位永远是「滚内容」。内容还有得滚就说明用户想滚它，不是想关面板。
        assertEquals(0f, GallerySheetScroll.consumedFromDrag(120f, panelExpanded = true, contentAtBottom = false), 1e-4f)
        assertEquals(0f, GallerySheetScroll.consumedFromDrag(-360f, panelExpanded = true, contentAtBottom = false), 1e-4f)
    }

    @Test
    fun `面板顶格且内容已到底时整份扣住`() {
        // 2026-10-05 鬼畜的修复点：这一份若不扣，会经 m3 的连接器（SheetDefaults.kt:656）
        // 逐帧推给面板，而面板 Expanded 锚点依赖内容高（BottomSheet.kt:306）⇒ 帧级上下跳。
        // 扣住 = 本层报「全吃了」⇒ 外层拿到 0 ⇒ 面板不动 ⇒ 锚点不重算 ⇒ 环断。
        assertEquals(120f, GallerySheetScroll.consumedFromDrag(120f, panelExpanded = true, contentAtBottom = true), 1e-4f)
        assertEquals(-360f, GallerySheetScroll.consumedFromDrag(-360f, panelExpanded = true, contentAtBottom = true), 1e-4f)
        assertEquals(0f, GallerySheetScroll.consumedFromDrag(0f, panelExpanded = true, contentAtBottom = true), 1e-4f)
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
