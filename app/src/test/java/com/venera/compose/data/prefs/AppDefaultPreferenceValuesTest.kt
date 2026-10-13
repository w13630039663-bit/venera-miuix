package com.venera.compose.data.prefs

import com.venera.compose.reader.ReaderReadingMode
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三枚应用默认档的化石尺子（2026-10-13 用户点名改判）：
 * 默认阅读模式 = 美漫·左至右、默认底栏 = 液态玻璃悬浮栏、默认界面风格 = MD3。
 *
 * 为什么扫源码而不是构造 prefs 来读默认值：`VeneraPreferences` 要 Android `Context`
 * （它自己建 `AndroidKeyValueStore`），纯 JVM 起不来；而这三枚的风险形态恰好就是
 * "某处把默认值改回去了"，扫字面量抓得住。
 *
 * ⚠️ 只钉**从没设过这一项**的安装所读到的那个值；盘上有值照旧读盘上的，不迁移也不覆盖。
 */
class AppDefaultPreferenceValuesTest {

    private val code: List<String> = RepoSources.kotlinFiles("com/venera/compose/data/prefs")
        .first { it.name == "VeneraPreferences.kt" }
        .readLines()
        .map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private fun lineWith(anchor: String): String =
        code.firstOrNull { it.contains(anchor) } ?: error("VeneraPreferences.kt 里找不到 `$anchor`")

    @Test
    fun `默认界面风格是 MD3`() {
        assertTrue(lineWith("KEY_APPEARANCE_STYLE").contains("AppearanceStyle.MD3"))
        assertFalse("默认档被改回 Miuix 了", lineWith("KEY_APPEARANCE_STYLE").contains("AppearanceStyle.MIUIX"))
    }

    @Test
    fun `默认底栏是液态玻璃悬浮栏`() {
        assertTrue(lineWith("KEY_NAVIGATION_BAR_STYLE").contains("NavigationBarStyle.LIQUID_GLASS"))
    }

    /**
     * 内容层材质**故意不动**：它的默认 SOLID 是那颗枚举自己 KDoc 写明的"零回归判据本身"
     * （另有 `SurfaceMaterialPolicyTest.默认档是实色`）。用户要的是底栏那一轴，不是整层玻璃。
     */
    @Test
    fun `内容层材质默认仍是实色这一轴没被顺手改掉`() {
        assertTrue(lineWith("KEY_SURFACE_MATERIAL").contains("SurfaceMaterial.SOLID"))
    }

    @Test
    fun `默认阅读模式是美漫左至右且两处兜底同值`() {
        val line = code.first { it.contains("KEY_DEFAULT_READING_MODE") }
        val occurrences = Regex("\"HORIZONTAL_LTR\"").findAll(line).count()
        // getString 的默认值与 `?:` 那枚兜底必须是同一串 —— 只改一处就会出现
        // "盘上存过 null 的人拿到旧默认"这种没人报得出的一格。
        assertEquals("默认值与兜底要同串：$line", 2, occurrences)
        assertFalse("还留着条漫那枚旧默认", line.contains("\"VERTICAL\""))
    }

    /** 默认值那枚串必须是枚举自己认得的键，否则 `fromKey` 会静默退到兜底档。 */
    @Test
    fun `那枚默认串确实是美漫左至右的键`() {
        assertEquals(ReaderReadingMode.HORIZONTAL_LTR, ReaderReadingMode.fromKey("HORIZONTAL_LTR"))
        assertEquals("美漫·左至右", ReaderReadingMode.HORIZONTAL_LTR.label)
    }
}
