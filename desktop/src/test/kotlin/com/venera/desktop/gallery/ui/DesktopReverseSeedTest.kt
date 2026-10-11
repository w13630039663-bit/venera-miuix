package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第三十六批 批 36 · `--reverse=` 那颗取证种子的接线链。
 *
 * ## 它补的是哪一格
 *
 * 批 35 把「找同一张图」那枚入口与结果那一格接上了，判据 12 条全绿，而**图证只能拍到那枚按钮**：
 * 结果那一格要按一下才出来，而鼠标自验是禁的（手册 §8）。这与批 19 的单人页、批 29 的期次按钮
 * 是同一种缺口，补法也一样 —— 把"那一下"变成命令行能给的东西。
 *
 * ## 为什么这一批的判据几乎全是接线形状而不是行为
 *
 * 行为那一份在批 35 已经做成跑得动的纯函数了（`DesktopReverseSearchPolicy`），种子这一层
 * 只负责"自动按一次"，它唯一能坏的地方就是**某一跳没接**：判读层认了、集成方读了、
 * 应用根收了、那一格按了、构建脚本传了 —— 少任何一跳都是"参数给了不报错而屏上没反应"，
 * 而那正是本仓最忌的形状（`-Pdetail=` 当年就漏在最后一跳，见 `DesktopKeyNavigationTest` ⑨）。
 */
class DesktopReverseSeedTest {

    /**
     * ① 那颗种子走**必填参数**，而覆盖层里不许再有"读一枚 local"的那条路。
     *
     * 这一条是**图证逼出来的**：第一版把它做成了 `compositionLocalOf<Boolean> { false }`，
     * 判据全绿、stdout 两句读数都念过，而 2026-10-08 那一帧上结果那一格根本没出现 ——
     * 覆盖层挂在应用根那圈 provider **外面**，永远读到默认值。同一颗坑第三次（前两次是 `onSearch`
     * 与那颗点卡回调，手册 §0 那一族的"默认值会抛的 compositionLocal 对覆盖层是埋雷"）。
     */
    @Test
    fun 那颗种子走必填参数而不再读局部槽() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        assertTrue("种子没做成必填参数（漏传不报错的那种形状不许有）", panel.contains("seedReverse: Boolean,"))
        assertEquals(
            "覆盖层里又出现了一枚只给它自己读的局部槽 —— 那一枚永远读到默认值",
            0,
            panel.split("LocalDesktopWallSeedReverse").size - 1,
        )
        // 对照组：这颗文件真在被扫（读空会让上面两条恒真），而既有的那枚种子局部槽仍在原处。
        assertTrue("对照组失效：面板这颗文件里已经没有局部槽声明了（那说明读错了文件）", panel.contains("compositionLocalOf<Boolean> { false }"))
    }

    /** ② 三跳：集成方读参数并接管浮层、应用根必填、那一格从**构造点**吃到它。 */
    @Test
    fun 三跳一处不缺() {
        val main = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
        assertTrue("集成方没读 --reverse", main.contains("""ForensicFlags.state(args.asList(), "--reverse")"""))
        assertTrue("集成方没走那颗接管浮层的纯函数", main.contains("ForensicFlags.detailPanel("))
        assertTrue("集成方没把它递进应用根（整串都得钉：只数 `startReverse =` 前缀，恒传 false 也绿 —— 坑 ㉔）",
            main.contains("startReverse = reverse == true"))
        assertTrue("浮层那一路没跟着接管走（读了 detailPanel 却仍按 --detail 开，参数同给时那一格根本不在组合里）",
            main.contains("startDetail = panelOpen"))
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue("应用根收了它（漏传不报错的那种默认值不许用）", app.contains("startReverse: Boolean"))
        assertTrue("那一格没从构造点吃到它（覆盖层读不到 provider）", app.contains("seedReverse = startReverse"))
        // 对照组：`--detail` 那两跳仍在（读空了会让上面几条恒真）。
        assertTrue("对照组失效：--detail 那一跳不在这颗文件里了", app.contains("LocalDesktopWallSeedFirst provides startDetail"))
    }

    /** ③ 构建脚本那一跳：判读层与消费点接好了也不算通，参数得真传得进来。 */
    @Test
    fun 构建脚本把那一枚传进app任务() {
        val gradle = java.io.File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertTrue("`app` 任务没读 gradleProperty(\"reverse\")", gradle.contains("""gradleProperty("reverse")"""))
        assertTrue("`--reverse` 没被拼进 args（值传不下去，判读层再对也接不到）", gradle.contains("\"--reverse=\$it\""))
        // 对照组：读的是那颗任务体（读空会让上面两条 contains 恒真）。
        assertTrue("对照组失效：这份构建脚本里没有 :desktop:app 那颗粒任务", gradle.contains("""tasks.register<JavaExec>("app")"""))
    }

    /** ④ 种子真按一次，而那枚动作只有一份实现（种子不许自己再写一遍发车）。 */
    @Test
    fun 种子按的是按钮那同一枚动作() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        assertTrue("种子没去按那枚动作", panel.contains("if (seedReverse)"))
        assertTrue("按的不是抽出来的那一枚动作（自己写第二份就会漂）", panel.contains("LaunchedEffect(post.uid) { fireReverse() }"))
        assertEquals("那枚动作被调了不止一处（发车那一笔只许有一个落点）", 1, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"), "desktopReverseRun("))
        // 对照：抽出来的那枚动作里真调着那颗件（读空会让上面两条恒真）。
        assertTrue("对照组失效：那枚动作里没有发车", panel.contains("fireReverse"))
    }
}
