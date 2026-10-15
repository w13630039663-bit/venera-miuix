package com.venera.compose.components

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏「选中那一枚」的着色来源（2026-10-15 修）。
 *
 * 用户报：换主题 / 换强调色 / 跟壁纸取色时，别的控件都跟着变，**唯独底部那条液态玻璃导航栏
 * 选中项那枚图标恒是同一支蓝**。根因是"默认值即违规值"：
 * `VeneraLiquidGlassNavBar.kt` 的 `accentColor` 形参默认写死 `Color(0xFF0088FF)` /
 * `Color(0xFF0091FF)`，而全仓唯一调用点（`Navigation.kt`）根本不传这个参数 ⇒ 那支硬编码蓝
 * 就是运行期真正生效的值，它经 `ColorFilter.tint` 染在图标层上、又被指示器药丸采样，
 * 于是药丸里的选中图标与色板完全脱钩。对照：未选中与标签色走 `MiuixTheme.colorScheme.primary`、
 * 悬浮胶囊那条也走色板、侧栏走 `tokens.color.primary` —— 三处都对，只有这一处绕过。
 *
 * 修在定义处而不是调用点：这样将来任何新调用点都不会退回硬编码。
 *
 * ⚠️ 那颗文件里 `containerColor`（玻璃壳底）仍写死 `0xFFFAFAFA` / `0xFF121212` —— 那是
 * **另一件事**（半透明玻璃的底色档，改它要先真机看透不透），所以判据只认"含 accent 的行"，
 * 不许把它算成违规，也不许让它往强调色那一族扩散。
 */
class BottomNavAccentThemeTest {

    private val files = listOf(
        "VeneraLiquidGlassNavBar.kt",
        "VeneraFloatingNavBar.kt",
        "VeneraSideBar.kt",
    )

    private fun codeLines(name: String): List<Pair<Int, String>> {
        val file = RepoSources.kotlinFiles("com/venera/compose/components")
            .firstOrNull { it.name == name }
            ?: RepoSources.kotlinFiles("com/venera/compose/components/backdrop")
                .firstOrNull { it.name == name }
        assertTrue("找不到源文件 $name，量具失效（扫描类用例不许静默通过）", file != null)
        return file!!.readLines().mapIndexed { i, l -> (i + 1) to l }
            .filter { (_, l) ->
                val t = l.trim()
                !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
            }
    }

    @Test fun `液态玻璃底栏的选中色来自色板`() {
        val line = codeLines("VeneraLiquidGlassNavBar.kt")
            .firstOrNull { (_, l) -> l.contains("accentColor:") && l.contains("=") }
        assertTrue("找不到 accentColor 形参那一行，量具失效", line != null)
        assertTrue(
            "选中色必须吃色板（VeneraTokens.color.primary），现在是：${line!!.second.trim()}",
            line.second.contains("VeneraTokens.color.primary"),
        )
    }

    @Test fun `三条底栏路径都不许把强调色写死`() {
        val offenders = files.flatMap { name ->
            codeLines(name)
                .filter { (_, l) -> l.contains("accent", ignoreCase = true) && l.contains("Color(0x") }
                .map { (n, l) -> "  $name:$n " + l.trim().take(88) }
        }
        assertTrue(
            "含 accent 的代码行里出现了写死的 Color(0x… ⇒ 它不会随 MD3 取色变：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
