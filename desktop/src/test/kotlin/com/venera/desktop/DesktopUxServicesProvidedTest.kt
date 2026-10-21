package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「桌面真的 provide 了 `LocalUxPlatformServices`」—— 修 2026-10-21 验收发现的 R3。
 *
 * ## 这条缺口长什么样
 *
 * `LocalUxPlatformServices` 是 `staticCompositionLocalOf { NoOpUxPlatformServices }`。
 * 桌面此前**从未 provide** ⇒ 一直吃默认值 ⇒ `coverMaskStateOf(...)` **恒返回 `"VISIBLE"`**。
 *
 * 而共享零件 `RecommendCarouselItem` / `HistoryCardHome` 里那句
 * `maskState == "VISIBLE"` 就是"要不要打码"的唯一判据 —— 恒真就等于**永不打码**。
 * 同时 `coverSharedElement(allowFly = maskState == "VISIBLE")` 会让本该遮住的项
 * 也去做共享元素飞行（安卓侧明确禁止）。
 *
 * **这不是样式问题，是内容安全缺口。** 而它**不会让任何测试变红** ——
 * `NoOp` 的返回值是合法值，屏上也不崩，只是该遮的没遮。所以必须专门钉一条。
 *
 * ## 为什么判据是「有非 NoOp 的 provide」而不是「import 存在」
 *
 * 验收工程师指出过我们已有的一条尺（`DesktopHomeUsesSharedWidgetsTest` 只校验 import 存在）
 * 放行过**死 import**。同一种失效在这里代价更大：import 一个 `CompositionLocalProvider`
 * 什么也不证明。所以这里要的是三件事同时成立：
 *   ① 壳里有 `LocalUxPlatformServices provides …`；
 *   ② provides 的**不是** `NoOp…`；
 *   ③ 装配点（`VeneraDesktop.kt`）真的把守卫件递了进去（`guard.nsfwMaskMode`）。
 */
class DesktopUxServicesProvidedTest {

    private fun src(name: String): File {
        val f = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/$name")
        assertTrue("读不到 $name —— 判据失效（不是通过）：${f.path}", f.isFile)
        return f
    }

    /** 非注释的代码行。 */
    private fun code(file: File): List<String> = file.readText().lines().filterNot { ln ->
        val s = ln.trim()
        s.startsWith("//") || s.startsWith("*") || s.startsWith("/*")
    }

    @Test
    fun `桌面壳必须 provide 一个非 NoOp 的 UxPlatformServices`() {
        val lines = code(src("VeneraDesktopLargeScreen.kt"))
        val provide = lines.filter { "LocalUxPlatformServices provides" in it }
        assertTrue(
            "桌面壳里没有 `LocalUxPlatformServices provides …` —— 那就是吃默认 NoOp，封面永不打码（R3）。" +
                "实际包含 LocalUxPlatformServices 的行：${lines.filter { "LocalUxPlatformServices" in it }}",
            provide.isNotEmpty(),
        )
        val block = lines.dropWhile { "LocalUxPlatformServices provides" !in it }.take(6).joinToString("\n")
        assertTrue(
            "provide 的居然是 NoOp —— 那与不 provide 等价（`coverMaskStateOf` 恒 `VISIBLE`）。块：\n$block",
            "NoOp" !in block,
        )
    }

    @Test
    fun `装配点必须把守卫件递进去 而不是留空 lambda`() {
        val lines = code(src("VeneraDesktop.kt"))
        val modeLine = lines.filter { "maskModeOf" in it }
        assertTrue(
            "装配点没传 `maskModeOf` —— 遮罩档从哪来就没答案了",
            modeLine.isNotEmpty(),
        )
        assertTrue(
            "`maskModeOf` 读的不是守卫件那条流（应形如 `{ guard.nsfwMaskMode.value }`）。实际：$modeLine",
            modeLine.any { "nsfwMaskMode" in it },
        )
        val blocked = lines.dropWhile { "blockedByUserOf" !in it }.take(12).joinToString("\n")
        assertTrue(
            "`blockedByUserOf` 没接上黑名单判定 —— 恒 false 就等于用户屏蔽全失效。块：\n$blocked",
            "GalleryBlockMatch.blocked" in blocked || "blockedGalleryRule" in blocked,
        )
    }

    @Test
    fun `桌面实现类本身不许把 coverMaskStateOf 交回给 NoOp`() {
        val lines = code(src("DesktopUxPlatformServices.kt"))
        assertTrue(
            "桌面实现里没有覆写 `coverMaskStateOf`",
            lines.any { "override fun coverMaskStateOf" in it },
        )
        // 档位字面值必须与安卓/画廊同一套（GalleryGuard 那三档）。
        assertTrue(
            "档位字面值没走 `GalleryGuard` —— 三档值必须与漫画侧同一套（VISIBLE/BLURRED/HIDDEN）",
            lines.any { "GalleryGuard." in it },
        )
    }
}
