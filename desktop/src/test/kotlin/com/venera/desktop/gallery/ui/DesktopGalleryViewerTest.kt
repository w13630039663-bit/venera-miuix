package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第五十批 · 大图那一屏（[DesktopGalleryViewer]）的五颗判据。
 *
 * ## 为什么这五颗里四颗是扫描式的
 *
 * 这一层要防的不是"算错了"，而是**接了但没接对**：取址重述一遍共享判据、
 * `contentScale` 用 Crop（裁掉的边缘放大了回不来）、滚轮档吃库的默认值（`EnabledWithCtrlKey`
 * ⇒ 裸滚轮不缩放，而那个现象会被读成"库在桌面不生效"）、遮挡那张照发请求。
 * 这四件事屏上**都有一份正确的读数**（图出来了 / 不出来），所以纯函数判据量不到它们。
 */
class DesktopGalleryViewerTest {

    // 一律走 codeText：缺席文案也住在文件头的 KDoc 里，用 readText 会让正锚点被自家注释喂饱。
    private val viewer get() = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt"))

    /** ① 取址**转交**那颗件，桌面不重述三档算式（重述就会与浮层漂成两条轨）。 */
    @Test
    fun `取址转交那颗件`() {
        assertEquals("desktopDetailTierUrl 应恰好一处", 1, viewer.split("desktopDetailTierUrl(").size - 1)
        assertEquals("不许重述共享判据", 0, viewer.split("GallerySettingsModel.wallUrl").size - 1)
    }

    /**
     * ② `contentScale` **恒 Fit**。
     *
     * 浮层今天是「原图档才 Fit、其余 Crop」，而放大看图时 Crop 裁掉的那块**回不来** ——
     * 那一层存在的意义就是看细节，Crop 等于把这一层存在的理由削掉。
     */
    @Test
    fun `恒用 Fit`() {
        assertEquals(1, viewer.split("contentScale = ContentScale.Fit").size - 1)
        assertEquals("不许出现 Crop", 0, viewer.split("ContentScale.Crop").size - 1)
    }

    /**
     * ③ 滚轮档**必须显式选 Enabled**。
     *
     * 库的默认档是 `EnabledWithCtrlKey`（裸滚轮不缩放）。漏了这一处，`-PviewerZoom` 种出来的
     * 放大帧拍得到、手动滚一下却没反应，而结论会错成"库在桌面上不生效"。
     */
    @Test
    fun `显式开滚轮缩放`() {
        assertEquals("mouseWheelZoom 应恰好一处", 1, viewer.split("mouseWheelZoom = MouseWheelZoom.Enabled").size - 1)
    }

    /**
     * ④ 遮挡那张**不发请求**，且三句缺席文案与浮层**逐字同串**。
     *
     * 同一件事在两屏念成两个名字就是假话（批 39 那条：行件两处共用）。
     */
    @Test
    fun `遮挡那张不发请求且与浮层同句`() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        assertEquals("要先判遮挡再摆图", 1, viewer.split("card?.masked == true").size - 1)
        listOf("\"分级遮挡\"", "\"站方没给这一档的图\"").forEach { line ->
            assertTrue("浮层今天就该有这句对照：$line", panel.contains(line))
            assertTrue("查看器那句要与浮层同串：$line", viewer.contains(line))
        }
        assertEquals("取图失败那句要上屏，不许只打 stdout", 1, viewer.split("failure?.let").size - 1)
        assertEquals("失败要带原话", 1, viewer.split("D_大图 取图失败").size - 1)
    }

    /**
     * ⑤ 种子那句读数两枚数都在，且翻页要复位缩放。
     *
     * 复位那条：带着放大态翻页，页码与屏上那张就对不上（假读数）。
     */
    @Test
    fun `种子念两枚数`() {
        assertTrue("种子读数没走那颗纯函数（夹取与念数会各写一遍）", viewer.contains("desktopViewerZoomReadout("))
        assertTrue("翻页要复位缩放", viewer.contains("zoomState.reset()"))
    }

    /**
     * ⑥ 对照组：这颗判据自己没读空。
     *
     * 五条都在同一批读数上，读空会让它们**全部恒真**—— 判据扫源码那一支最贵的那类假绿。
     */
    @Test
    fun `判据自己没读空`() {
        assertTrue(
            "查看器那份读空了：上面五条会在空串上恒真",
            viewer.contains("@Composable"),
        )
    }
}
