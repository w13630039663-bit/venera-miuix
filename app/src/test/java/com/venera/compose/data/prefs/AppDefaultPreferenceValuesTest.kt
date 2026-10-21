package com.venera.compose.data.prefs

import com.venera.compose.data.platform.NavigationBarStyle
import com.venera.compose.reader.ReaderPageTurnEffect
import com.venera.compose.reader.ReaderReadingMode
import com.venera.compose.testsupport.RepoSources
import java.io.File
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
 *
 * 本篇同时钉**这句默认值在屏上与文档里怎么说**（设置页选项标签、两份 README）——
 * 改一档会同时动三处（磁盘默认、设置页标签、文档），编译器只看得见第一处。
 */
class AppDefaultPreferenceValuesTest {

    private val code: List<String> = RepoSources.kotlinFiles("com/venera/compose/data/prefs")
        .first { it.name == "VeneraPreferences.kt" }
        .readLines()
        .map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private fun lineWith(anchor: String): String =
        code.firstOrNull { it.contains(anchor) } ?: error("VeneraPreferences.kt 里找不到 `$anchor`")

    /** 外观设置页去掉注释行后的每一条 —— 「屏上写了什么」只有从这里取才算数。 */
    private val appearance: List<String> = RepoSources.kotlinFiles("com/venera/compose/feature/settings")
        .first { it.name == "AppearanceSettings.kt" }
        .readLines()
        .map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    /** 锚点必须**只**命中一行：同屏另一处合法调用会把「存在性」判式喂饱而漏掉这一处。 */
    private fun appearanceLineWith(anchor: String): String {
        val hits = appearance.filter { it.contains(anchor) }
        assertEquals("`$anchor` 应当只出现在一行", 1, hits.size)
        return hits.first()
    }

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
     * 设置页那两档的「（默认）」字样必须挂在真的那一档上。
     *
     * 这条不是打磨：`cd0e1bd` 把默认档从 Miuix 换成液态玻璃时，选项标签里那句
     * 「Miuix 悬浮栏（默认）」原样留着 —— 屏上从此写着一条与磁盘默认值相反的话，
     * 而它比 `VeneraPreferences` 里那行更接近用户眼睛，编译器与运行期都管不到。
     */
    @Test
    fun `设置页底栏的默认字样挂在真的那一档上`() {
        val line = appearanceLineWith("Miuix 悬浮栏")
        assertFalse("标签仍说 Miuix 是默认档（实际已改判液态玻璃）：$line", line.contains("Miuix 悬浮栏（默认"))
        assertTrue("液态玻璃是真默认，标签上却没有「默认」二字：$line", line.contains("液态玻璃（默认）"))
    }

    /**
     * 底栏选项表与 [NavigationBarStyle] 同宽 —— 两档就是两档。
     *
     * 起因：两份 README 一直写着底栏「三选一（含实色）」，而枚举从接入那天起只有
     * `MD3` / `LIQUID_GLASS` 两值。这里钉 UI 那一侧的宽度，文档由下一条钉。
     */
    @Test
    fun `底栏选项表与枚举同宽`() {
        val line = appearanceLineWith("Miuix 悬浮栏")
        assertEquals("选项表条目数：$line", 2, Regex("""\bto\b""").findAll(line).count())
        assertEquals("枚举档位与选项表不同轨：${NavigationBarStyle.values().toList()}", 2, NavigationBarStyle.values().size)
    }

    /** 两份 README 不许再声称底栏有第三档；默认那句必须落在液态玻璃上。 */
    @Test
    fun `两份 README 的底栏那一行只写两档且默认是液态玻璃`() {
        val anchors = listOf(
            "README.md" to ("**底栏**" to "液态玻璃悬浮栏"),
            "README.en.md" to ("**bottom bar**" to "liquid glass"),
        )
        for ((name, pair) in anchors) {
            val (anchor, defaultValue) = pair
            val file = File(RepoSources.root, name)
            check(file.isFile) { "找不到 $file —— 文档尺子不能静默通过" }
            val lines = file.readLines()
            val line = lines.firstOrNull { it.contains(anchor) }
                ?: error("$name 里找不到 `$anchor` 那一行")
            assertFalse("$name 又写回三选一：$line", line.contains("三选一") || line.contains("/ solid"))
            assertTrue("$name 的默认档没写液态玻璃：$line", line.contains(defaultValue))
        }
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

    /**
     * 「画成水彩」默认**关**（2026-10-13 用户点名）：Beta 功能的默认档也是产品要求，
     * 同本篇其余几枚扫字面量钉住。只钉从没设过这一项的安装读到的值，盘上有值照旧。
     */
    @Test
    fun `画成水彩 Beta 默认关闭`() {
        assertTrue(lineWith("KEY_GALLERY_WATERCOLOR_BETA").contains("false"))
    }

    /**
     * 翻页特效默认**关闭（NONE）**：与图片增强总开关、内容层材质同口径 —— 升级后所有人看到的
     * 翻页行为与改动前逐像素一致。默认串必须是 `fromKey` 认得的键，否则会静默退到 NONE
     * （这里仍钉住，防止有人把默认值写成枚举名而非键名）。
     */
    @Test
    fun `翻页特效默认关闭`() {
        val line = code.first { it.contains("KEY_READER_PAGE_TURN_EFFECT") }
        // getString 的默认值与 `?:` 那枚兜底必须是同一串 —— 只改一处会出现不一致的一格。
        assertEquals("默认值与兜底要同串：$line", 2, Regex("\"NONE\"").findAll(line).count())
        assertEquals(ReaderPageTurnEffect.NONE, ReaderPageTurnEffect.fromKey("NONE"))
    }
}
