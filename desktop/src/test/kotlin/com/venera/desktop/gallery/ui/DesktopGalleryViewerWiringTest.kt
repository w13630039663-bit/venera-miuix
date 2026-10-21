package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第五十批 · 接线那一层：点卡开两层、焦点上提、`zoomed` 换真值、两枚取证种子全链。
 *
 * ## 为什么这六颗里大半是接线式判据
 *
 * 这一批的每一件事都是"接了但没人调"或"取了请求当结果"那一族：
 * 点卡没带上那一层（形状是浮层照旧，而屏上什么也不报错）、
 * `zoomed` 交一枚假 `false`（形状是连播在放大时抢页）、
 * 焦点三件套没上提（形状是两层各读一遍键，Esc 一次退两层）、
 * gradle 那一跳漏了（形状是参数打了不报错也不做事）。
 * 它们全都不会让编译失败，所以纯函数判据量不到。
 */
class DesktopGalleryViewerWiringTest {

    private val app get() = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
    private val flags get() = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("ForensicFlags.kt"))
    private val main get() = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
    private val gradle get() = java.io.File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()

    /** ① 点卡时两层一起开（用户原话：点卡就进大图，不该再多一步）。 */
    /**
     * ② `zoomed` 交真读数，那枚假 `false` 下架。
     *
     * 连播判据整条转交共享的 `GalleryAutoPlay.shouldAdvance`，而 `zoomed` 是它四枚闸之一：
     * 交假 `false` 的形状是"正在看细节的时候它自己翻到下一张"。
     */
    /**
     * ③ 这一条**不在这里钉了**。
     *
     * 从前这里数"全模块只有一处按键接线"，与 `DesktopKeyNavigationTest` ⑥ 钉的是同一件事 ——
     * 第八十七批·下半把那条口径改成"一屏一处"（阅读那一屏是另一层，与画廊浮层不共存）时，
     * 两把尺子就露出本仓记过的那一形：**改一把漏一把，另一把继续按旧口径红**。
     * 现在那一处数量只由 ⑥ 独家钉（措辞、判式、对照组都在那一颗），这里留一个槽位说明它搬走了。
     */

    /** ④ 两枚种子登记进 KNOWN（漏登记会被 `unknown` 报成拼错）。 */
    @Test
    fun `两枚种子登记了`() {
        assertTrue(flags.contains("\"--viewer\""))
        assertTrue(flags.contains("VIEWER_ZOOM"))
    }

    /** ⑤ gradle 那一跳接上了（批 47 那条纪律：判读层接好而这一跳漏 = 不报错也不做事）。 */
    @Test
    fun `参数从 gradle 传到 CLI`() {
        assertTrue(gradle.contains("""providers.gradleProperty("viewer")"""))
        assertTrue(gradle.contains("""providers.gradleProperty("viewerZoom")"""))
        assertTrue(main.contains("\"--viewer\""))
        // ⚠️ 改判一次：原判式钉 `main` 里有 `--viewer-zoom=`，而那一串在**代码**里只出现在
        // 注释中（`ForensicFlags` 那颗件的 KDoc 与函数注释）—— 判据走 `codeText`（跳注释），
        // 所以它读到的是空。集成方在代码里真正调的是那颗函数：`ForensicFlags.viewerZoom(args…)`。
        // 钉函数名才是"那一跳接上了"的真对象（注释能写得像接线，接线却不会只写在注释里）。
        assertTrue(
            "集成方没调那颗判读函数（`--viewer-zoom=` 打进去不会被读）",
            main.contains("ForensicFlags.viewerZoom("),
        )
    }

    /** ⑥ 落点那行补了种子字段。 */
    /**
     * ⑧ 「浮层 ×」要**两层一起收**（第五十批图证量出来的缺口）。
     *
     * `onClose` 是浮层唯一的关闭主人，而浮层顶上那枚 × 走的就是它。
     * Esc 那条路因为 `onDesktopOverlayKey` 里有 `viewerOpen` 分流，不会经过 `onClose` 的那一支 ——
     * 于是"从 × 按下去"只有 `selected` 变、**没人收 `viewerOpen`**，
     * 屏上留下「浮层没了而大图还开着」。那一帧的读数是 `浮层=关(关掉浮层) 查看器=开`。
     *
     * 这一颗是**读数给的**，不是想出来的：判据全绿时屏上已经错了（第五批那种"接线对了而行为错了"）。
     */
    /**
     * ⑦ 对照组：这批判据自己没读空。
     *
     * ①⑥ 全是 `contains`，读空会**全部恒真** —— 判据扫源码那一支最贵的那类假绿
     * （手册 §4.1 那句"一条把合法点扫红的判据比没有判据更坏"的另一面）。
     */
}
