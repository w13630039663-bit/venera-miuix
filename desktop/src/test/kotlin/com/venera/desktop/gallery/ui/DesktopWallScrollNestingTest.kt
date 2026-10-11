package com.venera.desktop.gallery.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 那一面墙**不许被包进纵向滚动里**（第九批，由一次真崩溃换来）。
 *
 * ## 读数
 *
 * `-PstartDomain=DISCOVER -Pproxy=…` 那一跑，屏上是一枚空白的 `Error` 对话框，日志里是：
 *
 * ```
 * java.lang.IllegalStateException: Vertically scrollable component was measured with
 * an infinity maximum height constraints, which is disallowed. … nesting layouts like
 * LazyColumn and Column(Modifier.verticalScroll()) …
 *     at …lazy.grid.LazyGridKt$rememberLazyGridMeasurePolicy$1$1.measure(LazyGrid.kt:202)
 * ```
 *
 * 成因：[DesktopGalleryPostWall] 里面是一颗 `LazyVerticalGrid` —— 它**自己就是滚动容器**，
 * 所以要吃**有界**的高度。把它放进 `Column(Modifier.verticalScroll())`，父层交下来的是无限高，
 * 那颗 Grid 当场抛。不是"滚不动"，是**整屏崩**。
 *
 * ## 为什么这一条要扫而不是测
 *
 * 没有可注入的假端口能真跑那一屏（同 `DesktopForYouPaneTest` 类注释那条），
 * 而这个形状在源码里就看得见：**同一颗文件里既 `.verticalScroll(` 又渲染那一面墙**就是错的。
 *
 * ⚠️ 只扫**渲染那一面墙的**文件。漫画那一屏自己也用 `verticalScroll`，但它的 `LazyVerticalGrid`
 * 拿了 `Modifier.height(estimatedGridHeight(…))` 一个算出来的定高（`DesktopComicPane.kt:289`），
 * 那是另一种正确解法 —— 把那条也判成违例就是误伤。
 *
 * ## 正确解法有两种，这里选第一种
 *
 * 1. **别滚那一屏**：外层 `Column` 不挂 `verticalScroll`，让墙吃 `weight(1f)` 自己滚
 *    （搜索那一屏今天就是这个形状，实测过 148 张）。
 * 2. 给墙算一个定高 —— 漫画那一屏用的一种，但照片墙的行数随窗宽变，算出来的高是一条会漂的账。
 */
class DesktopWallScrollNestingTest {

    /** 谁渲染了那一面墙。名单由这颗断言自己钉：漏一颗就是漏扫一颗。 */
    @Test
    fun 渲染那一面墙的文件一处都不许挂在纵向滚动里() {
        val wallRenderers = DesktopSourceTree.desktopUiSources().filter { file ->
            DesktopSourceTree.codeText(file).contains("DesktopGalleryPostWall(")
        }
        assertTrue(
            "对照组失效：一颗都没扫到 —— 那下面那个循环是空的，恒绿",
            wallRenderers.isNotEmpty(),
        )
        // 三面墙 + 发现那一屏：日榜 / 收藏 / 搜索 / 发现。少一颗就是有人把墙撤了或换了名字。
        val names = wallRenderers.map { it.name }.sorted()
        assertTrue(
            "渲染那一面墙的文件应当包含日榜、收藏、搜索、发现四颗，现在是 $names",
            names.containsAll(
                listOf(
                    "DesktopDailyPane.kt",
                    "DesktopForYouPane.kt",
                    "DesktopGalleryFavoritesPane.kt",
                    "DesktopGallerySearchPane.kt",
                ),
            ),
        )
        // 一次把**所有**违例点出来，而不是撞到第一颗就停：这一条抓的是"还有哪几屏同病"，
        // 逐颗试会让我以为只有第一颗坏了（第一次跑就是这么漏掉收藏那一屏的）。
        val offenders = wallRenderers.filter { DesktopSourceTree.codeText(it).contains(".verticalScroll(") }
        assertTrue(
            "这些屏把那一面墙包进了 `verticalScroll` —— `LazyVerticalGrid` 收到无限高会当场抛" +
                "（整屏崩，不是滚不动）。解法见本颗类注释：${offenders.map { it.name }}",
            offenders.isEmpty(),
        )
    }

    /** 对照：漫画那一屏确实挂了滚动，但它的 Grid 拿了定高 —— 这条不许被上面那判误伤。 */
    @Test
    fun 漫画那一屏的定高解法不算违例() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicPane.kt"))
        assertTrue("对照组失效：漫画那一屏今天并不挂 verticalScroll", code.contains(".verticalScroll("))
        assertTrue("它靠的是算出来的定高，不是靠不滚", code.contains("Modifier.height(estimatedGridHeight("))
    }
}
