package com.venera.compose.feature.settings

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 过度滚动（overscroll）的禁用口径（2026-10-16，用户「钳制效果，禁止在设置了头图的页面使用过度滚动，
 * 不然效果不好」）。
 *
 * ⚠️ 这一轮抓到的根因是**API 换代，而且老写法自写下那天起就一直是空转**：
 *
 * 设置页那两处 `CompositionLocalProvider(LocalOverscrollConfiguration provides null)` 在
 * foundation **1.12.0** 下**完全不起作用**。该 CompositionLocal 已是 `@Deprecated`，官方弃用文案
 * 原话：「To disable overscroll, instead of `LocalOverscrollConfiguration provides null`,
 * **use `LocalOverscrollFactory provides null`**」。两者是**两个不同的 CompositionLocal 实例**
 * （各自独立的 holder），所以 `provides null` 写在哪一个上就是哪一件事 —— 写在旧的那个上，
 * 新的那个仍拿平台默认值，表现出来就是"设了等于没设"。
 *
 * 因此尺子必须同时钉**正反两面**：新名字要在，旧名字在**代码行**里必须绝迹。
 * 只钉一面都留得下"半边换错"的口子。
 */
class OverscrollDisablePolicyTest {

    private val files = listOf(
        "com/venera/compose/feature/settings/SettingsHome.kt",
        "com/venera/compose/feature/settings/SettingsComponents.kt",
    )

    /**
     * 与 [com.venera.compose.ui.tokens.TokenBudgetFrozenTest] 同一把口径：注释行不参与匹配。
     * 这条对本用例是**刚需** —— 上面那段解释里就写着旧名字，不去注释行会自己把自己钉红。
     */
    private fun codeLines(rel: String): List<String> {
        val f = RepoSources.appMain.resolve(rel)
        assertTrue("找不到源码文件：${f.absolutePath}（量具失效）", f.isFile)
        return f.readLines().map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
    }

    @Test fun `设置相关两屏都用新的工厂 local 禁过度滚动`() {
        files.forEach { rel ->
            val lines = codeLines(rel)
            assertTrue(
                "$rel 没 import androidx.compose.foundation.LocalOverscrollFactory" +
                    "（只改 provide 不改 import 会编译不过，只改 import 不改 provide 就是空转）",
                lines.any { it == "import androidx.compose.foundation.LocalOverscrollFactory" },
            )
            assertTrue(
                "$rel 里找不到 `CompositionLocalProvider(LocalOverscrollFactory provides null)`",
                lines.any { it.contains("CompositionLocalProvider(LocalOverscrollFactory provides null)") },
            )
        }
    }

    @Test fun `代码行里不许再出现已废弃的 LocalOverscrollConfiguration`() {
        files.forEach { rel ->
            val hit = codeLines(rel).filter { it.contains("LocalOverscrollConfiguration") }
            assertTrue(
                "$rel 的**代码行**里仍有 LocalOverscrollConfiguration。foundation 1.12 起它已被弃用、" +
                    "`provides null` 不再生效（官方指明改用 LocalOverscrollFactory）。命中行：$hit",
                hit.isEmpty(),
            )
        }
    }
}
