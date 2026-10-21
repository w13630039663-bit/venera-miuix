package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「切底栏那一格会退出二级页」—— 2026-10-21 接源管理入口时撞上的一格既有缺口。
 *
 * ## 那颗缺口长什么样
 *
 * 壳里的分派是**二级页优先**：`when (route)` 里 `DesktopRoute.Tab ->` 排在最下面那一支，
 * 上面依次是 `SourceManage` / `Detail` / `Reader`。而侧栏那一路原本写的是
 *
 * ```kotlin
 * onTabSelected = { currentTab = it }   // ← 只动 currentTab，不动 route
 * ```
 *
 * 于是停在详情 / 阅读器 / 源管理时点侧栏任何一格：**屏上仍是那一本漫画**，
 * 而侧栏高亮已经跳过去了 —— 用户读到的是"侧栏坏了"，实际是路由没退。
 *
 * ## 归属（别记错了）
 *
 * 这是**既有**缺口，不是本轮弄出来的：本轮之前那三屏已经能进（点探索卡 → 详情 → 阅读器），
 * 差别只在**本轮接上源管理入口后，"停在二级页再点侧栏"这一串动作第一次变得顺手**，
 * 于是本喵自己撞上了它。`git diff` 过 `onTabSelected` 那行：本轮之前它就是那个形状。
 *
 * ## 修法取的口径
 *
 * 「切 tab 就回 tab 那一层」，而不是"每个 tab 记住自己的二级页" —— 后者要引回栈，
 * 而 `DesktopRoute.kt` 那颗的类注释明写着「只有两层，所以不引导航库」。
 * 这条判据因此钉的是**那两件事都做了**（赋 tab **且** 清 route），少一件就红。
 *
 * ## 这把尺子有牙（2026-10-21 现做扰动实验）
 *
 * 把源码那一行改回旧的 `{ currentTab = it }` 再跑：
 *
 * ```
 * DesktopTabExitsSecondaryRouteTest > 切tab时既赋tab也清route FAILED
 * DesktopTabExitsSecondaryRouteTest > 不许退回只动currentTab那一行 FAILED
 * 3 tests completed, 2 failed
 * ```
 *
 * ⇒ 该红的两条红了，而第三条（分派顺序那条，前提没变）**没有跟着红** ——
 * 这正是想要的形状：改坏才红，没改坏不红。改回来再跑是绿的（读数在 rounds 那一节）。
 */
class DesktopTabExitsSecondaryRouteTest {

    private val lines = DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopSourceOf("VeneraDesktopLargeScreen.kt"))
        .filter { it.isNotEmpty() }

    /** 侧栏那一路的回调体（从 `onTabSelected = {` 起到配对的那枚 `}`）。 */
    private fun tabCallbackBody(): String {
        val at = lines.indexOfFirst { it.startsWith("onTabSelected = {") }
        assertTrue("读不到侧栏的 onTabSelected（判据失效，不是通过）", at >= 0)
        val out = StringBuilder()
        var depth = 0
        for (i in at until lines.size) {
            out.append(lines[i]).append('\n')
            depth += lines[i].count { it == '{' } - lines[i].count { it == '}' }
            if (depth <= 0) break
        }
        return out.toString()
    }

    @Test
    fun 切tab时既赋tab也清route() {
        val body = tabCallbackBody()
        assertTrue("回调里没赋 currentTab：$body", "currentTab = it" in body)
        assertTrue(
            "回调里没把 route 清成 Tab —— 停在二级页时点侧栏，屏上仍是那一本而高亮已跳走：$body",
            "route = DesktopRoute.Tab" in body,
        )
    }

    /** 反向自检：那一行**不许**退回单语句形状（历史写法就是它）。 */
    @Test
    fun 不许退回只动currentTab那一行() {
        val naked = lines.filter { it.startsWith("onTabSelected = { currentTab = it }") }
        assertTrue(
            "侧栏回调退回成只动 currentTab 那一行了（那就是那颗缺口复发）：$naked",
            naked.isEmpty(),
        )
    }

    /** 分派那一段的顺序还在：二级页优先、`Tab` 在最下面那一支（这条判据的前提）。 */
    @Test
    fun 分派仍是二级页优先() {
        val order = lines.mapIndexedNotNull { i, l ->
            when {
                l.startsWith("DesktopRoute.SourceManage ->") -> "SourceManage" to i
                l.startsWith("is DesktopRoute.Detail ->") -> "Detail" to i
                l.startsWith("is DesktopRoute.Reader ->") -> "Reader" to i
                l.startsWith("DesktopRoute.Tab ->") -> "Tab" to i
                else -> null
            }
        }
        val names = order.map { it.first }
        val tabAt = order.first { it.first == "Tab" }.second
        assertTrue("分支读不全（那颗 when 被改了而这里没跟上）：$names", names.containsAll(listOf("SourceManage", "Detail", "Reader", "Tab")))
        assertTrue(
            "`DesktopRoute.Tab` 不再排在最后 ⇒ 上面那一条判据的前提变了（谁先谁后要重判）：$order",
            names.last() == "Tab" && order.all { it.second < tabAt || it.first == "Tab" },
        )
    }
}
