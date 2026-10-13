package com.venera.compose.components

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-14 UI 审查第六批的尺子：同一档值只许有一处定义。
 *
 * 两条都是"抄一份不会报错、改的时候漏改三处也不会报错"：
 * ① 玻璃补底强度 `0.16f` 从前在顶栏、药丸、详情页背板与 `VeneraGlass` 各写一遍，
 *    四句注释互相指认对方"与某某同步" —— 而"同步"从来没人验证过；
 * ② 顶栏药丸的触达位写着裸 `48.dp`，同仓另一处已有 `VeneraIconButtonTouchSize = 48.dp`。
 *
 * 存量那一本账（裸 dp / sp / Color 三个封顶）在 `ui/tokens/TokenBudgetFrozenTest`，这里不重复立尺子。
 */
class UiAuditBatch6Test {

    private fun text(dir: String, name: String): String =
        RepoSources.kotlinFiles(dir).first { it.name == name }.readText()

    private fun codeLines(dir: String, name: String): List<String> =
        text(dir, name).lines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private val venera = "com/venera/compose/components/venera"

    /** ① 玻璃补底强度只有 `VeneraGlass` 那一处定义，三处 chrome 读它。 */
    @Test
    fun `玻璃补底强度不再逐处抄写`() {
        val offenders = RepoSources.kotlinFiles("com/venera/compose")
            // 水彩纸屑那枚 0.16 是"纸上落多少白点"，与玻璃补底不是一回事，各留各的
            .filter { it.name != "WatercolorPlanner.kt" }
            .filter { f -> codeLinesOf(f).any { it.contains("alpha = 0.16f") } }
            .map { it.name }
        assertEquals("这些文件又自己写了一遍补底强度：$offenders", emptyList<String>(), offenders.sorted())
        // 对照：定义本身确实只有那一处（读空了上面那条恒真）。
        val glass = text(venera, "VeneraGlass.kt")
        assertEquals(1, Regex("""const val GlassTintAlpha = 0\.16f""").findAll(glass).count())
        assertTrue("三处 chrome 都没在读它", glass.contains("alpha = GlassTintAlpha"))
    }

    /** ② 顶栏药丸的触达位吃现成口径，不再自己写一遍 48.dp。 */
    @Test
    fun `顶栏药丸触达位吃现成口径`() {
        val pill = codeLines(venera, "VeneraTopBarPill.kt")
        assertTrue("药丸的触达位没吃 VeneraIconButtonTouchSize：$pill",
            pill.any { it.contains(".size(VeneraIconButtonTouchSize)") })
        assertTrue("药丸又自己写了一遍 48.dp", pill.none { it.contains(".size(48.dp)") })
        // 对照：那枚常量还在，且就是 48dp（改的是引用，不是数值）。
        assertTrue(
            "VeneraIconButtonTouchSize 的定义变了",
            text(venera, "VeneraControls.kt").contains("val VeneraIconButtonTouchSize = 48.dp"),
        )
    }

    private fun codeLinesOf(f: java.io.File): List<String> =
        f.readLines().map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
}
