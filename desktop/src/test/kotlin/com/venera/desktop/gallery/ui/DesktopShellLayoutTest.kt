package com.venera.desktop.gallery.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面**壳层**的形状判据 —— 那些"拆错了不会红、但用户一眼看见"的东西。
 *
 * ## 为什么需要这颗用例
 *
 * 2026-10-04 之前，根布局是 `NavigationView(menuItems = {}) { DesktopGalleryHome(...) }`，
 * 而 `DesktopGalleryHome` **自己**又画了 rail 48 + pane 224 ⇒ 窗口里挤了**三层竖栏**。
 * 拆的时候只改了 `VeneraDesktop.kt` 三行，肉眼看得出"变宽了"，但**没有任何判据守住它**：
 * 下一个接线的人照着 S2 文档再把 `NavigationView` 套回去，红绿都照旧。
 *
 * 而这条防线本仓**三周前就立过**：
 * `docs/rounds/large-screen-adaptation-stage2-plan-2026-10-02.md:20` 逐字写着
 * 「compose-fluent 的 `NavigationView` 有**自己的默认宽度**，不是官方的 72/224。
 * 两边都建侧栏就是两套宽度 —— 即『第二套布局系统』」，并据此把桌面化冻结在阶段 1。
 * S2 接线时漏判了这条（`grep NavigationView docs/rounds/windows-gallery-home-s2b-*.md` = 0 命中），
 * 于是"已判负的方案"被接了回来。所以判据写成**源码文本断言**，钉死这一条。
 *
 * 另一条 [FluentTheme 仍在场] 则是防**过度修复**：拆 `NavigationView` 时很容易顺手把
 * `FluentTheme` 一起拆掉，而它是深色档与 fluent `Text` 的唯一 scope，拆了整窗会塌。
 */
class DesktopShellLayoutTest {

    /**
     * ⚠️ 这颗用例扫的是**代码行**（[DesktopSourceTree.codeText]），不是全文。
     *
     * 原因就是本仓反复记过的那条：口径注释里**必须**能点名被禁的那个 token
     * （"这里曾套过 `NavigationView(`" 是这段历史唯一说得清的地方），
     * 含进注释就把说明当成了违例 —— 于是要判据的人不得不把自己的判据删掉。
     * `DesktopSourceTree.codeText` 与 [DesktopSourceTree.codeOccurrences] 用同一套"跳注释"口径。
     */
    private val shellCode: String =
        DesktopSourceTree.codeText(
            File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt"),
        )

    private val shell: String =
        File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt").readText()

    /**
     * ① 根布局不许再套 `NavigationView`。
     *
     * 负向断言三个串：调用点 `NavigationView(`、import `component.NavigationView`、
     * 以及那个空 lambda 的参数名 `menuItems =`。
     */
    @Test
    fun `根布局不许套 NavigationView`() {
        listOf("NavigationView(", "component.NavigationView", "menuItems =").forEach { token ->
            assertTrue(
                "VeneraDesktop.kt 的**代码行**里出现了「$token」—— compose-fluent 的 NavigationView " +
                    "自带一个 180dp 侧栏（实测 SideNavKt 宽度常量 180.0f），与桌面自绘的 rail 48 + " +
                    "pane 224 叠成三套竖栏，即「第二套布局系统」。判负记录见 " +
                    "docs/rounds/large-screen-adaptation-stage2-plan-2026-10-02.md:20",
                shellCode.contains(token).not(),
            )
        }
    }

    /**
     * ② 但 `FluentTheme` 必须留着。
     *
     * 拆掉 `NavigationView` **不等于**拆掉 `FluentTheme`：后者给全窗提供深色档
     * （`DesktopTheme.colors()` = `darkColors(accent=藤紫)`），且 `gallery/ui/` 多处在用
     * fluent 的 `Text` —— 那个组件需要 `FluentTheme` 提供的 CompositionLocal scope。
     * 拆掉它整窗会塌成 fluent 的默认浅色。
     */
    @Test
    fun `FluentTheme 仍在场`() {
        assertTrue(
            "深色档由 FluentTheme(colors = DesktopTheme.colors()) 提供，拆掉它整窗会塌回浅色",
            shell.contains("FluentTheme(") && shell.contains("DesktopTheme.colors()"),
        )
    }

    /**
     * ③ 默认窗尺寸走**按屏钳制**，目标是设计稿画板 1440×900。
     *
     * 目标尺寸的理由不只是"好看"：它决定列数 —— `1440−48−224 = 1168`，
     * 正是权威稿 `:338`/`:657` 标注的「内容净宽 1168 → 6 列」。
     *
     * ⚠️ 但**不许把 1440 写死在窗口上**：`WindowPlacement.Floating` 不做尺寸自适应，
     * 1366 宽的屏上写死 1440，右边那一截直接跑到屏幕外（计划里挂着的 R-g）。
     * 所以这条断的是"引用了钳制函数"，而"每一档屏幕各得多少"由
     * `DesktopWidthCaliberDriftTest` 的 `默认窗尺寸按屏钳制…` 逐屏钉死 ——
     * 那条吃的是纯函数，不用开窗就能真跑。
     */
    @Test
    fun `默认窗口尺寸走按屏钳制`() {
        assertTrue(
            "窗宽高应来自 DesktopGalleryMetrics.windowSizeFor(…)（按屏钳制），而不是写死一颗数",
            shellCode.contains("windowSizeFor("),
        )
        assertTrue(
            "不许再把 1440.dp 写死在 rememberWindowState 上 —— 1366 屏上那会让最右一列跑到屏幕外",
            shellCode.contains("1440.dp").not(),
        )
        assertEquals(
            "目标尺寸仍是设计稿画板 1440×900（大屏上原样开出来）",
            1440f,
            DesktopGalleryMetrics.windowSizeFor(2560, 1440).width.value,
            0.001f,
        )
    }

    /**
     * ④ rail 与 pane 各自引**不同**的底色 token。
     *
     * 这是根因 3 的机械防线：那一处原先的注释声称「rail 底色刻意比 pane 更深一档」，
     * 而代码里两处是**同一个** `SidebarBackground` —— 注释与代码互相矛盾，
     * 结果两级导航在深色下糊成一片。`DesktopCardOverlayTest` ① 从色值那头钉，
     * 这里从**引用点**这头钉：改回去少一处都会红。
     */
    /**
     * ④ rail 与 pane 各自引**不同**的底色 token。
     *
     * 这是根因 3 的机械防线：那一处原先的注释声称「rail 底色刻意比 pane 更深一档」，
     * 而代码里两处是**同一个** `SidebarBackground` —— 注释与代码互相矛盾，
     * 结果两级导航在深色下糊成一片。`DesktopCardOverlayTest` ① 从色值那头钉，
     * 这里从**引用点**这头钉：改回去少一处都会红。
     *
     * ⚠️ 扫的是 `VeneraDesktopApp.kt` 而不是旧的 `DesktopGalleryHome.kt`：2026-10-04 起了
     * **应用根**之后，三级壳（标题栏 / rail / pane / 主区）由那颗文件统一画，
     * 旧的 `DesktopGalleryHome` 连同它的 Katherine Column/Row 一起删了。
     */
    @Test
    fun `rail 与 pane 引不同的底色 token`() {
        val shell = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue(
            "rail 应引 RailBackground（#1C1C1C，比 pane 更暗）",
            shell.contains("DesktopTheme.RailBackground"),
        )
        assertTrue(
            "pane 应引 SidebarBackground（#272727）",
            shell.contains("DesktopTheme.SidebarBackground"),
        )
    }

    /**
     * ⑤ 标题栏在**三栏之外**，且搜索位不是一枚假开关。
     *
     * 稿把 `.tb` 放在 `.body` 之外（`:340` 的 `.tb` 与 `:352` 的 `.body` 是兄弟节点），
     * 所以它必须横跨 rail / pane / 主区。要是有人把它塞进 pane 或主区里，
     * 截图上是"标题栏缩了一截"——编译通过、单测全绿，只有人眼看得出来。
     *
     * 搜索位那一条在 2026-10-04 **换了判法**：原先它弹一条 `railNotice` 就完事，
     * 断言守的是"那句话没被删成空串"。改成不动款之后 —— rail 七枚都是真导航，
     * 搜索位也跟着**真的把人带到搜索域** —— 该守住的东西升级成了"点了Destination 真的换了"。
     * 一条 cue 会被删空，一次导航不会。
     */
    @Test
    fun `标题栏跨三栏且搜索位不是假开关`() {
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue(
            "根布局里应把 DesktopGalleryTopBar 放在三栏之外（Column 直接子节点，与 Row 平级）",
            app.contains("DesktopGalleryTopBar("),
        )
        assertTrue(
            "搜索位点下去必须把当前域换成 SEARCH（真导航），而不是吐一句话原地不动",
            app.contains("domain = DesktopDomain.SEARCH"),
        )
        // 反过来：旧的"提示吐丝"那条路必须真的没有了 —— 留着它就是两个说法并存，
        // 而同一件事讲两处的时候，改动迟早只改一处。
        assertTrue(
            "DESKTOP_GALLERY_SEARCH_NOTICE / railNotice 应已随旧根一起删掉，缺席说法现在只有搜索域坦白页一处",
            app.contains("DESKTOP_GALLERY_SEARCH_NOTICE").not() && app.contains("railNotice").not(),
        )

        // 三枚自绘窗口按钮：系统标题栏已经有了，跟着稿再画一排就是两排。
        // （稿上那三枚是它在浏览器里模拟窗口 chrome，不是应用内容。）
        val bar = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryTopBar.kt"))
        listOf("最小化", "最大化", "关闭").forEach { label ->
            assertTrue(
                "标题栏里出现了「$label」—— 系统标题栏已有窗口按钮，再自绘一排就是两排按钮",
                bar.contains(label).not(),
            )
        }
    }
}
