package com.venera.desktop.gallery.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大图那一屏的三道算式。它们**必须是纯函数** —— 布局与取证都只吃结果，
 * 两处各写一遍就是"算式漂移"那一族（本仓在预取上已经吃过一次）。
 */
class DesktopGalleryViewerPolicyTest {

    // ① 浮层开着 ⇒ 减两道（340 面板 + 46 渐隐带），不是只减 340。
    //    只减 340 的形状是"大图右缘压在渐隐带底下"，而那条带今天就在屏上。
    @Test
    fun `浮层开着时让开面板与渐隐带`() {
        assertEquals(1226.dp - 340.dp - 46.dp, desktopViewerContentWidth(1226.dp, overlayOpen = true))
    }

    // ② 浮层关着 ⇒ 全宽。
    @Test
    fun `浮层关着时吃全宽`() {
        assertEquals(1226.dp, desktopViewerContentWidth(1226.dp, overlayOpen = false))
    }

    // ③ 窗口窄到不足一档 ⇒ 不许负数（负宽度会让布局直接崩，而崩的是整屏）。
    @Test
    fun `窄到不足一档时不许变负数`() {
        val narrow = desktopViewerContentWidth(120.dp, overlayOpen = true)
        assertTrue("窄窗算出了 $narrow", narrow >= 0.dp)
    }

    // ④ 入口档：表里有「大图」就升到「大图」（设计 §4.1 的 A 案）。
    @Test
    fun `有那一档就升`() {
        assertEquals(
            DesktopDetailTier.SAMPLE,
            desktopViewerEntryTier(listOf(DesktopDetailTier.PREVIEW, DesktopDetailTier.SAMPLE)),
        )
    }

    // ⑤ 表里只有「预览」⇒ 留在「预览」，不许凭空造一档。
    @Test
    fun `没那一档就照表里第一枚`() {
        assertEquals(DesktopDetailTier.PREVIEW, desktopViewerEntryTier(listOf(DesktopDetailTier.PREVIEW)))
    }

    // ⑥ 被挡那张（空表）⇒ 一档都不升。
    //    这条是"直接写 SAMPLE"那种偷懒版本的死穴：被挡那张三档换出来的都是同一句「分级遮挡」。
    @Test
    fun `空表不升`() {
        assertNull(desktopViewerEntryTier(emptyList()))
    }

    // ⑦ 放大种子三档输入各过一遍：越界要夹，合法要原样。
    @Test
    fun `种子夹到合法区间`() {
        assertEquals(1f, desktopViewerZoomScale(0f, 5f), 0f)
        assertEquals(2.5f, desktopViewerZoomScale(2.5f, 5f), 0f)
        assertEquals(5f, desktopViewerZoomScale(9f, 5f), 0f)
    }

    // ⑧ 夹了就必须说出来：读数里同时有"给的"和"用的"。
    @Test
    fun `读数把两枚数都念出来`() {
        val line = desktopViewerZoomReadout(raw = 9f, applied = 5f)
        assertTrue("读数少了给的那枚：$line", line.contains("9"))
        assertTrue("读数少了用的那枚：$line", line.contains("5"))
    }
}
