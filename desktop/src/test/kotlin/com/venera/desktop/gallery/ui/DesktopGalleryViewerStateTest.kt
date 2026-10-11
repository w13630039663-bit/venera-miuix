package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第五十批 · 档位与缩放态**上提**的三颗判据。
 *
 * ## 为什么要上提
 *
 * 大图那一屏与浮层必须读**同一颗**档位，而预取（批 47）已经踩过"两条轨"的坑：
 * 预取算的地址与屏上取的不是同一枚档，就是"预取白费、点下去照样重取一趟"，而屏上看不出异常。
 * 那一层自持一份 `tier` 的形状，正是那条轨的起点。
 */
class DesktopGalleryViewerStateTest {

    private val panel get() = DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt")
    private val app get() = DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")

    /** ① 档位只有一处声明：浮层里不许再自己 remember 一份。 */
    @Test
    fun `档位只有一处声明`() {
        assertEquals("浮层还自持档位", 0, DesktopSourceTree.codeOccurrences(panel, "var tier by remember"))
        assertEquals("应用根的档位声明应恰好一处", 1, DesktopSourceTree.codeOccurrences(app, "var tier by remember"))
    }

    /** ② 缩放态也只有一处：查看器与连播判据读同一颗。 */
    @Test
    fun `缩放态只有一处声明`() {
        assertEquals("rememberZoomState 应恰好一处", 1, DesktopSourceTree.codeOccurrences(app, "rememberZoomState("))
    }

    /**
     * ③ 浮层的档位形参**必填**。
     *
     * 带默认值时漏传不报错，而漏传的形状正是"两条轨"—— 它会安静地退回浮层自己那份初值。
     */
    @Test
    fun `浮层吃必填档位`() {
        assertEquals("形参表里那枚档位应恰好一处", 1, DesktopSourceTree.codeOccurrences(panel, "tier: DesktopDetailTier,"))
        assertEquals("档位不许带默认值", 0, DesktopSourceTree.codeOccurrences(panel, "tier: DesktopDetailTier ="))
    }

    /**
     * ④ 对照组：这颗判据自己没读空。
     *
     * 三条 `codeOccurrences` 都在同一批读数上，若那批读数是空串，"0 处"会**恒真**地通过
     * —— 这就是本仓最贵的那类假绿（判据扫源码那一支的固有代价，见手册 §4）。
     */
    @Test
    fun `判据自己没读空`() {
        assertTrue(
            "浮层那份读空了：上面三条会在空串上恒真",
            DesktopSourceTree.codeText(panel).contains("DesktopGalleryDetailPanel("),
        )
        assertTrue(
            "应用根那份读空了：① ② 会恒真",
            DesktopSourceTree.codeText(app).contains("LocalDesktopWallPick provides"),
        )
    }
}
