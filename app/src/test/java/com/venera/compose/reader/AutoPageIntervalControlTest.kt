package com.venera.compose.reader

import com.venera.compose.data.platform.PreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动翻页间隔的步进与"区间只有一把尺子"（2026-10-13，用户点名"加上时间控制 1-10s"）。
 *
 * 步进件是纯函数，所以这里能真跑到两端；**夹取**是这一批最容易做坏的一格 ——
 * 回调里各夹一遍就会与写入钳位那份分叉，屏上表现是"按到顶了还能再按"。
 */
class AutoPageIntervalControlTest {

    private val range = PreferenceKeys.AUTO_PAGE_INTERVAL_SEC_RANGE

    @Test
    fun `区间就是用户点名的 1 到 10 秒`() {
        assertEquals(1f, range.start, 0f)
        assertEquals(10f, range.endInclusive, 0f)
    }

    @Test
    fun `步进一格是一秒`() {
        assertEquals(5f, nextAutoPageIntervalSec(4f, +1), 0f)
        assertEquals(3f, nextAutoPageIntervalSec(4f, -1), 0f)
    }

    /** 两端必须夹住：到顶再按 + 不许变大，到底再按 − 不许变小。 */
    @Test
    fun `两端夹在区间里`() {
        assertEquals(10f, nextAutoPageIntervalSec(10f, +1), 0f)
        assertEquals(10f, nextAutoPageIntervalSec(9.5f, +1), 0f)
        assertEquals(1f, nextAutoPageIntervalSec(1f, -1), 0f)
        assertEquals(1f, nextAutoPageIntervalSec(1.4f, -1), 0f)
    }

    /** 存量盘上可能有旧版滑块留下的 11~15（旧区间到 15）：读出来不许跳出区间。 */
    @Test
    fun `存量越界值被夹回区间`() {
        assertEquals(10f, nextAutoPageIntervalSec(15f, -1), 0f)
        assertEquals(1f, nextAutoPageIntervalSec(0f, +1), 0f)
    }

    /**
     * 三处读同一枚常量：设置页滑块、prefs 写入钳位、面板步进件。
     *
     * 源码扫描而不是行为测试 —— 后两处要 Android `Context` 才构造得出来，
     * 而"漂移"这件事本身就是**某处又写了一个字面量**，扫字面量正好抓得住。
     */
    @Test
    fun `三处都引用同一枚区间常量而没有再写字面量`() {
        val settings = codeLines("com/venera/compose/feature/settings", "ReaderSettings.kt")
        val prefs = codeLines("com/venera/compose/data/prefs", "VeneraPreferences.kt")
        val policy = codeLines("com/venera/compose/reader", "ReaderAutoAdvancePolicy.kt")
        assertTrue("设置页滑块没读那把尺子", settings.any { it.contains("AUTO_PAGE_INTERVAL_SEC_RANGE") })
        assertFalse("设置页还留着旧的 1f..15f", settings.any { it.contains("1f..15f") })
        assertTrue("写入钳位没读那把尺子", prefs.any { it.contains("coerceIn(PreferenceKeys.AUTO_PAGE_INTERVAL_SEC_RANGE)") })
        assertFalse("写入钳位还留着旧的 15f", prefs.any { it.contains("coerceIn(1f, 15f)") })
        assertTrue("面板步进件没读那把尺子", policy.any { it.contains("coerceIn(PreferenceKeys.AUTO_PAGE_INTERVAL_SEC_RANGE)") })
    }

    /** 面板上那两枚 −/+ 真的存在（从前翻页档只有一枚只读标签，点了没反应）。 */
    @Test
    fun `翻页档面板上有减速与加速两枚把手`() {
        val screen = codeLines("com/venera/compose/reader", "VeneraReaderScreen.kt")
        val down = screen.indexOfFirst { it.contains("nextAutoPageIntervalSec(intervalSec, -1)") }
        val up = screen.indexOfFirst { it.contains("nextAutoPageIntervalSec(intervalSec, +1)") }
        assertTrue("面板里找不到 −/+ 两枚把手", down > 0 && up > 0)
        // 顺序也要钉：夹取之后"− 在前、+ 在后"是这一格的观感，写反了屏上是"−"在右边
        assertTrue("两枚把手的顺序反了", down < up)
    }

    private fun codeLines(dir: String, name: String): List<String> =
        com.venera.compose.testsupport.RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
}
