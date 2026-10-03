package com.venera.desktop.gallery.ui

import java.io.File
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
     * ③ 默认窗口对齐设计稿画板 1440×900。
     *
     * 理由不只是"好看"：它决定列数。`1440−48−224 = 1168`，正是权威稿 `:338`/`:657`
     * 标注的「内容净宽 1168 → 6 列」。
     */
    @Test
    fun `默认窗口是 1440 乘 900`() {
        assertTrue(
            "默认窗应写死 1440.dp × 900.dp（稿 .frame 的画板尺寸）",
            shell.contains("width = 1440.dp") && shell.contains("height = 900.dp"),
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
    @Test
    fun `rail 与 pane 引不同的底色 token`() {
        val home = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryHome.kt"))
        assertTrue(
            "rail 应引 RailBackground（#1C1C1C，比 pane 更暗）",
            home.contains("DesktopTheme.RailBackground"),
        )
        assertTrue(
            "pane 应引 SidebarBackground（#272727）",
            home.contains("DesktopTheme.SidebarBackground"),
        )
    }
}
