package com.venera.compose.reader

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 翻页特效的**两处入口**判据（2026-10-14 用户报"加了翻页特效但找不到入口"）。
 *
 * 病症：特效接线齐全（PageTurn.kt 三档都有实现），但屏上**只有设置页**一处入口。
 * 用户读到一半想当场试卷边，得退出阅读器 → 进设置 → 选档 → 回阅读器重进那一话，
 * 四步才能看到自己刚选的动画 —— 于是"像是没做这个功能"。
 *
 * 这一份尺子钉三件事：
 *  ① 阅读器面板里**有**翻页特效那一块（同一颗枚举的短称/图形两档字段）；
 *  ② 设置页那一份**没被撤**（总入口必须留着），并且它的选项从枚举来、不是页面里另抄一份；
 *  ③ 阅读器那一块的显隐跟着"这一档模式有没有'翻'这个动作"，条漫与横向连续
 *     整块隐藏 —— 不留"选不了却摆着"的假开关。
 */
class ReaderPageTurnEntryTest {

    private fun lines(dir: String, name: String): List<String> =
        RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private val screen = lines("com/venera/compose/reader", "VeneraReaderScreen.kt")
    private val settings = lines("com/venera/compose/feature/settings", "ReaderSettings.kt")

    /** ① 阅读器面板里那三枚档位格真的存在，且写的是同一份偏好。 */
    @Test
    fun `阅读器面板里有翻页特效那一块并且写的是同一个偏好键`() {
        assertTrue(
            "阅读器面板里没有翻页特效入口 —— 用户在场内试不了，只能退出阅读器去设置页",
            screen.any { it.contains("ReaderPageTurnEffect.entries.forEach { effect ->") },
        )
        assertTrue(
            "面板里那三枚没有写回翻页特效偏好（点了个假开关）",
            screen.any { it.contains("prefs.setPageTurnEffect(effect.key)") },
        )
        // 档位名必须从枚举来（短称 + 图形），不是页面里另抄一份文案：
        // 另抄一份就是两本账，改一忘一之后两处说法分叉。
        assertTrue(
            "面板里的档位名不是从 ReaderPageTurnEffect.shortLabel 来的",
            screen.any { it.contains("text = effect.shortLabel") },
        )
        assertTrue(
            "面板里的档位图标不是从 ReaderPageTurnEffect.glyph 来的",
            screen.any { it.contains("text = effect.glyph") },
        )
    }

    /** ② 设置页那一份仍在，且选项同源于枚举。 */
    @Test
    fun `设置页那一份还在且选项从枚举来`() {
        assertTrue(
            "设置页的「翻页特效」那一行不见了 —— 总入口不能撤",
            settings.any { it.contains("SettingsSelect(\"翻页特效\"") },
        )
        assertTrue(
            "设置页的选项是页面里另抄的一份 —— 与阅读器两处各写就会分叉",
            settings.any { it.contains("ReaderPageTurnEffect.entries.map { it.key to it.label }") },
        )
    }

    /**
     * ③ 阅读器那一块只在"真的有翻页动作"的模式下出现。
     *
     * 条漫与横向连续**没有翻页这个动作**，摆三枚翻页特效在那儿就是假开关
     * （本仓 UiAuditBatch7Test ① 认的就是这一族）。
     */
    @Test
    fun `阅读器里翻页特效只在三种翻页模式下出现`() {
        val gate = screen.indexOfFirst { it.contains("val horizontalPageMode =") }
        assertTrue("找不到翻页特效那一块的模式门控", gate > 0)
        val block = screen.drop(gate).take(12).joinToString("\n")
        // 三个模式一个都不能少：LTR / RTL / 双页对开
        assertTrue("门控里漏了美漫 LTR", block.contains("ReaderReadingMode.HORIZONTAL_LTR"))
        assertTrue("门控里漏了日漫 RTL", block.contains("ReaderReadingMode.HORIZONTAL_RTL"))
        assertTrue("门控里漏了对开双页", block.contains("ReaderReadingMode.DOUBLE_PAGE"))
        // 条漫与横向连续必须不在门控里（它们没有"翻"这个动作）
        assertFalse("条漫也在门控里 —— 条漫没有翻页动作", block.contains("VERTICAL_CONTINUOUS"))
        assertFalse("横向连续也在门控里 —— 它没有翻页动作", block.contains("HORIZONTAL_CONTINUOUS"))
    }

    /**
     * ③ 的对照：双页对开下仿真卷边**选不了**（ReaderCurlPageItem 与拼合对开观感冲突，
     * 翻页区只在单页横向两条支上挂它）。选不中要比"选完没动画"好。
     */
    @Test
    fun `双页对开下仿真卷边置灰而不是给一枚假开关`() {
        assertTrue(
            "卷边那一档没有可用态判式 —— 双页下点得动却没有效果",
            screen.any { it.contains("val effectEnabled = effect != ReaderPageTurnEffect.CURL ||") },
        )
        assertTrue(
            "置灰的那一档没说清为什么不生效（用户只知道点不了，不知道为什么）",
            screen.any { it.contains("双页对开下仿真卷边不生效") },
        )
    }

    /**
     * 档位表本身：默认必须是 NONE（"新能力默认不改动观感"铁律，同图片增强总开关）。
     * 这条与 AppDefaultPreferenceValuesTest.翻页特效默认关闭 是同一条，只是那边钉持久化，
     * 这边钉枚举自身的兜底方向 —— 盘上有未知值时 fromKey 必须落回 NONE 而不是第一档。
     */
    @Test
    fun `默认档与兜底都是原样`() {
        assertEquals(ReaderPageTurnEffect.NONE, ReaderPageTurnEffect.fromKey(null))
        assertEquals(ReaderPageTurnEffect.NONE, ReaderPageTurnEffect.fromKey("unknown-key"))
        assertEquals(ReaderPageTurnEffect.NONE, ReaderPageTurnEffect.fromKey("NONE"))
        assertEquals(ReaderPageTurnEffect.BOOK_FLIP_3D, ReaderPageTurnEffect.fromKey("BOOK_FLIP_3D"))
        assertEquals(ReaderPageTurnEffect.CURL, ReaderPageTurnEffect.fromKey("CURL"))
        // 三枚短名互不相同，且都不含"（原样）"那种长称后缀 —— 均分格里塞不下
        assertEquals(3, ReaderPageTurnEffect.entries.size)
        assertEquals(3, ReaderPageTurnEffect.entries.map { it.shortLabel }.distinct().size)
        ReaderPageTurnEffect.entries.forEach {
            assertFalse("短名里带着长称后缀：${it.shortLabel}", it.shortLabel.contains("（"))
            assertTrue("短名是空的：${it.key}", it.shortLabel.isNotBlank())
        }
    }
}
