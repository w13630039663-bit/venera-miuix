package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「源管理那一屏真有一个入口，而且进去出得来」—— 进度清单 §2 那一格「没有入口 ⇒ 这屏打不开」。
 *
 * ## 缺口长什么样
 *
 * 那屏 256 行、启停与两档守卫设置都接上了（H4 靠它闭合），而**全仓没有一处 UI 会切到
 * `DesktopRoute.SourceManage`**：路由分了、屏渲了，可没人按得到它。清单原话是
 * 「属于 [值]（补一个入口很便宜），不是 [术]」—— 补完就把这三件事各钉一条：
 *
 *  ① 有一处把路由切过去（不是一句注释里提到那个名字）；
 *  ② 切过去之前那枚按钮真的接到屏上（`onOpenSourceManage` 是必传参数，不是带默认值的假开关）；
 *  ③ 那一屏有 `onBack` 且切回 `Tab`（入口接上前它根本没有返回，是单行道）。
 *
 * ## 为什么是源码尺而不是行为尺
 *
 * 离屏 `runComposeUiTest` 够不到壳里那颗 `when (route)`（那要起整只壳 + 真引擎 + 真库）。
 * 按不切得到路由由这里钉；那枚按钮按下落得到回调由
 * `DesktopNewScreensSemanticsTest.探索屏那枚源管理入口按一次恰好落一次` 钉。
 * 两把合起来才是"用户真进得去"。
 */
class DesktopSourceManageEntryTest {

    private val shell = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktopLargeScreen.kt"))
    private val explore = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("explore/DesktopExploreScreen.kt"))
    private val manage = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("sourcemanage/DesktopSourceManageScreen.kt"))

    @Test
    fun 有一处真把路由切到源管理() {
        // 只数代码行：那句 KDoc 里本来就写着那个名字（"含进注释就是把说明当违例"是同一口径的反向）。
        val cuts = shell.lines().filter { "route = DesktopRoute.SourceManage" in it }
        assertEquals(
            "切进源管理路由的赋值不止一处（那多半是有人另开了第二个入口而没写进这里的口径）或一处都没有：",
            1, cuts.size,
        )
        assertTrue(
            "那一处不是从 `onOpenSourceManage` 那枚回调里切过去的 ⇒ 入口不是用户按得到的，只是代码里过了一遍：\n" +
                cuts.single(),
            "onOpenSourceManage = { route = DesktopRoute.SourceManage }" in cuts.single(),
        )
    }

    @Test
    fun 探索屏那枚入口是必传参数而不是带默认值的假开关() {
        // 带 `{}` 默认值的参数等于"没接也不报错"—— 评审 R6 那一族的形状。
        assertTrue(
            "`onOpenSourceManage` 有了默认值：那壳里忘传也不会红，入口就又没了",
            "onOpenSourceManage: () -> Unit," in explore,
        )
        assertTrue(
            "探索屏顶栏那枚按钮没真的把回调接上",
            "VeneraButton(onClick = onOpenSourceManage)" in explore,
        )
        // 装配点递了形参（不是靠默认值）。
        assertTrue(
            "壳里没给探索屏递 `onOpenSourceManage` ⇒ 那枚按钮按不动",
            "onOpenSourceManage = {" in shell,
        )
    }

    /**
     * 那一屏有出路：`onBack` 是必传形参、顶栏真渲了那枚返回、壳里切回 `Tab`。
     *
     * ⚠️ 这一条 2026-10-21 之前是**不成立**的：那屏只有「源管理」四个字，从探索进来就出不去。
     */
    @Test
    fun 源管理屏有出路而不是单行道() {
        assertTrue("`onBack` 不是必传形参（`=> Unit =` 那种带默认值的不算）", "onBack: () -> Unit," in manage)
        assertTrue("顶栏没渲那枚返回按钮", "VeneraButton(onClick = onBack)" in manage)
        val back = shell.lines().dropWhile { "DesktopRoute.SourceManage ->" !in it }.take(30).joinToString("\n")
        assertTrue("壳里那一支没把返回切回 Tab（那就是进去出不来）：$back", "onBack = { route = DesktopRoute.Tab }" in back)
    }

    /** 源清单那颗 effect 的键里必须有 `route`（入口接通后 `currentTab` 那一路不再变）。 */
    @Test
    fun 源清单那颗effect的键里有route() {
        val line = shell.lines().firstOrNull { "LaunchedEffect(currentTab, route, sourceTick)" in it }
        assertTrue(
            "源清单那颗 effect 的键里没有 `route` ⇒ 从探索屏切进这一屏时不会重读，屏上永远念\"一个源都没有\"。" +
                "（与清单里 B2 那颗同一个形状：effect 读了哪一路状态，键里就得有哪一路。）",
            line != null,
        )
        val block = shell.lines().dropWhile { "LaunchedEffect(currentTab, route, sourceTick)" !in it }.take(4)
            .joinToString("\n")
        assertTrue("那颗 effect 的守卫里还得留着 FAVORITES 那一路（收藏格也读源清单）：$block", "VeneraNavTab.FAVORITES" in block)
    }

    /**
     * 入口只有一处（探索屏顶栏）。
     *
     * 为什么钉"恰好一处"：安卓那边源管理住在「设置」里，桌面这批**还没有设置屏**。
     * 将来补了设置屏，入口应该挪过去而不是留两处 —— 那时这一判会红，
     * 而红得有意义：提醒改的是"两处还是一个"这条口径，不是随手放宽成 `<=2`。
     */
    @Test
    fun 全仓入口只有一处() {
        val sites = DesktopSourceTree.desktopMainSources().filter {
            DesktopSourceTree.codeOccurrences(it, "onOpenSourceManage") > 0 && it.name != "DesktopExploreScreen.kt"
        }.map { it.name }.sorted()
        assertEquals("`onOpenSourceManage` 的接线点变了：", listOf("VeneraDesktopLargeScreen.kt"), sites)
    }
}
