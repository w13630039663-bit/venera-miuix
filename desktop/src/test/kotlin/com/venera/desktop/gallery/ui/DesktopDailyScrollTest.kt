package com.venera.desktop.gallery.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「每日热门」那一屏**整页只有一个滚动容器**（用户 2026-10-04 提的那条：整页该能下滑，
 * 不该只有精选推荐那一截在滚）。
 *
 * ## 为什么不是"外层挂 verticalScroll"
 *
 * 那是本仓已经判过死刑的形状：`LazyVerticalGrid` 自己就是滚动容器，父层给无限高会**当场抛**
 * （手册 §23.3，那条整屏崩溃）。剩下两种正解里，"给墙算一个定高"是漫画那一屏的解法，
 * 而照片墙的行数随窗宽变 —— 那条高是一条会漂的账。所以这里走第三种：
 * **把 hero 与那几排读数搬进同一颗 grid，做成跨整行的 item**，于是全页只剩一个滚动容器。
 *
 * 三条判式各对一种"以后会被顺手改坏"的形状：
 * ① hero 被搬回滚动容器外面（= 用户抱怨的那件事回来了）；
 * ② 有人在这一屏自己再写一颗 `LazyVerticalGrid`（= 长出第二份列数口径，
 *    与 [DesktopGalleryPostWall] 类注释里"三面墙各摆一次就会漂"是同一条）；
 * ③ `lead` 被加上默认值（= 将来该有节头的那一屏漏传不报错）。
 */
class DesktopDailyScrollTest {

    private val pane = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopUiSource("DesktopDailyPane.kt"),
    )

    private val wall = DesktopSourceTree.codeText(
        DesktopSourceTree.desktopUiSource("DesktopGalleryPostTile.kt"),
    )

    /** ① hero 与缺席读数、节头、被挡那一行，必须在 `lead` 那一段里。 */
    @Test
    fun hero与那几排读数必须住在滚动容器里面() {
        val at = pane.indexOf("lead = {")
        assertTrue("这一屏没把前置内容交给 `lead` —— 那就是又变回「只有下面那截在滚」", at >= 0)
        listOf("DesktopDailyHero(", "这一轮没给内容的站", "精选推荐", "DesktopWallGuardLine(").forEach { piece ->
            val found = pane.indexOf(piece, at)
            assertTrue("「$piece」没搬进滚动容器里（它还在 `lead` 段之外）", found > at)
        }
        // 对照组：`lead` 之后确实还有内容（读空会让上面那几条恒真）。
        assertTrue("对照组失效：`lead` 段之后什么源码都没有", pane.length - at > 400)
    }

    /** ② 整屏只许有一个滚动容器：这一颗文件里既不挂 `verticalScroll`，也不再写一颗 grid。 */
    @Test
    fun 那一屏不许出现第二个滚动容器() {
        assertTrue("这一屏挂了 `verticalScroll` —— 墙吃无限高会整屏崩（手册 §23.3）", pane.contains(".verticalScroll(").not())
        assertTrue(
            "这一屏自己写了一颗 `LazyVerticalGrid` —— 列数与首卡跨列的口径会长出第二份",
            pane.contains("LazyVerticalGrid(").not(),
        )
        // 对照组：滚动容器确实来自那颗共用的墙，而不是"这一屏什么都没画"。
        assertTrue(
            "那颗墙在这一屏应当恰好被调一次，实际 ${DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopDailyPane.kt"), "DesktopGalleryPostWall(")} 次",
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopUiSource("DesktopDailyPane.kt"),
                "DesktopGalleryPostWall(",
            ) == 1,
        )
    }

    /** ③ `lead` 必填：带默认值时"该有节头的那一屏漏传"不报错。 */
    @Test
    fun lead那一枚参数必须必填() {
        assertTrue("对照组失效：没读到 `lead` 的声明", wall.contains("lead: LazyGridScope.() -> Unit"))
        assertTrue(
            "`lead` 被加了默认值 —— 漏传不报错，而漏传的形状是「那一屏悄悄没有前置内容」",
            wall.contains("lead: LazyGridScope.() -> Unit =").not(),
        )
    }
}
