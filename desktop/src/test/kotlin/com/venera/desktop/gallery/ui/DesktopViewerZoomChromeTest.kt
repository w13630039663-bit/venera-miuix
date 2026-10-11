package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大图那一屏念得出**放大到几倍**，并给一枚「回到原大小」（批 55）。
 *
 * ## 为什么是这一格
 *
 * 那一屏今天只有「第 N / M 张」：滚轮滚一下到底生没生效，屏上看不出来 ——
 * 倍数只住在 `ZoomState` 里，而它的读数只有取证种子那一路会打到 stdout（`D_放大种子 给=… 用=…`）。
 * 手册 §17 那条「滚轮真滚一下图动不动 —— 仍未过」缺的正是这个：**用户手上没有 stdout**。
 * 摆出倍数与一枚复位，那一条就从"只能靠手感"变成"一眼看得见"。
 *
 * ## 三条纪律各有一条尺子
 *
 * 1. **吃活的那枚 `zoomState.scale`**，不吃种子那个数 —— 否则滚轮滚了半天而那一格永远念初值，
 *    比没有更糟（本仓的"假读数"那一族）。
 * 2. **没放大就整格不摆**，而不是摆一句「放大 1.0x」：常驻的废话会把真正有用的那一格压成背景。
 * 3. **复位走库那颗 `reset()`**：它连位移一起归零，而 `changeScale(1f)` 只改倍数 ——
 *    放大后拖偏过再"回到原大小"却还偏着，那是另一枚假开关。
 */
class DesktopViewerZoomChromeTest {

    // ① 纯函数那五档：没放大 / 差半格 / 放大着 / 顶格 / 非法值。
    @Test
    fun `倍数那一格五档各一个答案`() {
        assertNull("1.0 倍不摆：" + desktopZoomChrome(1f), desktopZoomChrome(1f))
        assertNull("差半格（1.04）不摆：" + desktopZoomChrome(1.04f), desktopZoomChrome(1.04f))
        assertTrue("2.4 倍要念得出数：" + desktopZoomChrome(2.4f), desktopZoomChrome(2.4f)!!.contains("2.4"))
        assertTrue("顶格也要念得出：" + desktopZoomChrome(5f), desktopZoomChrome(5f)!!.contains("5"))
        assertTrue("那句要带倍数单位：" + desktopZoomChrome(2.4f), desktopZoomChrome(2.4f)!!.contains("x"))
        // 非法值一律不摆：摆出「放大 0.0x」或「NaNx」比不摆更像坏了。
        assertNull("0 倍不摆", desktopZoomChrome(0f))
        assertNull("负数不摆", desktopZoomChrome(-2f))
        assertNull("NaN 不摆", desktopZoomChrome(Float.NaN))
        assertNull("无穷不摆", desktopZoomChrome(Float.POSITIVE_INFINITY))
    }

    // ② 那一格吃的是**活的那枚**倍数，且算式全树只一处（两处各算一遍就会漂成两个数）。
    @Test
    fun `那一格吃活的倍数而算式只一处`() {
        val viewer = DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt")
        val code = DesktopSourceTree.codeText(viewer)
        assertTrue("那一格没吃 `zoomState.scale`（吃种子那个数就是假读数）", code.contains("zoomState.scale"))
        val everywhere = DesktopSourceTree.desktopMainSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "desktopZoomChrome(") }
        // 一处定义 + 一处调用；再多就是第二份算式。
        assertEquals("那颗算式在桌面 main 里出现了 $everywhere 次（定义一处、调用一处）", 2, everywhere)
    }

    // ③ 没放大时整格不摆。
    @Test
    fun `没放大时整格不摆`() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt"))
        assertTrue("那一格没按「放没放大」判（常驻一句废话）", code.contains("if (zoomChrome != null)"))
    }

    // ④ 复位那一路：字面在、走库那颗 `reset()`、且 suspend 要有人 launch。
    @Test
    fun `复位那一枚走库的reset而在协程里`() {
        val viewer = DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt")
        val code = DesktopSourceTree.codeText(viewer)
        assertTrue("屏上没有「回到原大小」那一句", code.contains("回到原大小"))
        assertTrue(
            "那枚按钮没走 `scope.launch { zoomState.reset() }`（`reset()` 是 suspend，漏了 launch 根本编译不过，" +
                "而换成 changeScale 就是另一种假开关）",
            code.contains("scope.launch { zoomState.reset() }"),
        )
    }

    // ⑤ 复位只许有一颗算式：翻页那一处与按钮那一处都走库的 `reset()`，谁都不许自己写 `changeScale(1f)`。
    @Test
    fun `复位只走那一颗reset`() {
        val viewer = DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt")
        assertEquals("复位那件事被写了不止两遍（翻页一处 + 按钮一处）", 2, DesktopSourceTree.codeOccurrences(viewer, "zoomState.reset()"))
        assertEquals(
            "有人用 `changeScale(1f)` 当复位（那只改倍数，拖偏过的位移回不来）",
            0,
            DesktopSourceTree.codeOccurrences(viewer, "changeScale(1f"),
        )
    }

    // ⑥⑦ 那一格与页码各占一角，且页码没被顺手换掉。
    @Test
    fun `那一格与页码各占一角`() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt"))
        assertTrue("倍数那一格没占右下角", code.contains("Alignment.BottomEnd"))
        assertTrue("对照组失效：页码那一格不在了", code.contains("Alignment.BottomCenter"))
        assertTrue("对照组失效：页码那一句被改掉了", code.contains("第 \${selection.index + 1}"))
        assertFalse("那一格不许把内部枚举名念上屏", code.contains("SAMPLEx"))
    }

    /**
     * ⑧ **覆盖层不许住在缩放层里**（图证换来的那一条，不是补出来的漂亮判据）。
     *
     * 第一版把那两格摆在带 `.zoomable` 的那枚 `Box` 里面，于是 `-PviewerZoom=2.5` 那一帧上
     * 页码与倍数**整屏都不见了** —— `.zoomable` 的变换吃的是它那一格的整棵子树，放大两倍半就把
     * 右下角推出屏外。而 727 颗判据当时全绿（单测从不排版）。
     *
     * 钉法是**顺序**而不是"有没有分家"：缩放层整颗搬进 `DesktopViewerStage`，
     * 于是「调用在前、`.zoomable` 在后（在定义里）」这两条一断，把覆盖层塞回变换里就会红。
     */
    @Test
    fun `覆盖层不许住在缩放层里`() {
        val viewer = DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt")
        val code = DesktopSourceTree.codeText(viewer)
        assertEquals("缩放修饰在桌面 main 里不止一处", 1, DesktopSourceTree.desktopMainSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, ".zoomable(") })
        val stageCall = code.indexOf("DesktopViewerStage(")
        val zoomable = code.indexOf(".zoomable(")
        val chrome = code.indexOf("Alignment.BottomEnd")
        assertTrue("对照组失效：没读到那一屏的正文（读空会让下面三条恒真）", stageCall in 0 until chrome)
        assertTrue("覆盖层排到了缩放层那一格之前（调用点找不到）", stageCall < zoomable)
        // 缩放层整颗在文件末尾那颗 private 件里 ⇒ 「覆盖层在前、`.zoomable` 在后」就是"没塞进变换里"。
        // 谁把那两格搬回 `DesktopViewerStage` 里面，这一条当场红（第一版的形状）。
        assertTrue("覆盖层被塞回缩放层里了（放大到几倍时那两格会被一起推出屏外）", chrome < zoomable)
    }
}
