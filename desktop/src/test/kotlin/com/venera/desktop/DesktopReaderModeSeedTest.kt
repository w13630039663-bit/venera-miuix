package com.venera.desktop

import com.venera.desktop.gallery.data.DesktopReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 87 · `--read-mode=` 那颗种子的判读（纯函数面）。
 *
 * ## 为什么这一族要单独有行为尺子
 *
 * 接线那几条（`DesktopReaderPagedSeedTest` ⑥）钉的是"三层各有一处"，而**参数怎么被读成一档**
 * 只有真喂过才知道 —— 本仓记过一整族"存在性判式全绿而行为是反的"（坑 ㉗ 那一族）。
 * 这一颗只管判读：落到哪一档、认不出时交回什么；"顶过去之后屏上摆哪一档"在
 * `DesktopReaderPagingTest` 那三态里量。
 *
 * ## 认不出时**不折回默认档**
 *
 * 那是这一颗与 `DesktopReaderMode.fromKey` 唯一的分别：读档要兜底（盘上那串可能是历史留下的旧写法），
 * 而取证参数兜底就等于「递了个错字而屏上静静换了一档」—— 本批要防的正是那一类。
 */
class DesktopReaderModeSeedTest {

    @Test
    fun 没给这一枚时不念也不顶档() {
        assertNull(ForensicFlags.readMode(emptyList()))
        assertNull(ForensicFlags.readMode(listOf("--read=1/2")))
    }

    @Test
    fun 认表里的键也认枚名而两者都不分大小写() {
        assertEquals(DesktopReaderMode.HORIZONTAL_RTL, ForensicFlags.readMode(listOf("--read-mode=HORIZONTAL_RTL")))
        assertEquals(DesktopReaderMode.HORIZONTAL_LTR, ForensicFlags.readMode(listOf("--read-mode=horizontal_ltr")))
        // 枚名与键今天同串，但判据两路各写一遍：对岸若给某档另起一枚键，这两条会分开红
        assertEquals(DesktopReaderMode.VERTICAL_CONTINUOUS, ForensicFlags.readMode(listOf("--read-mode=VERTICAL_CONTINUOUS")))
    }

    @Test
    fun 错字与空值都不折回默认档() {
        assertNull("错字读成默认档就是静默换档", ForensicFlags.readMode(listOf("--read-mode=HORIZONTAL_RT2")))
        assertNull(ForensicFlags.readMode(listOf("--read-mode=")))
        assertNull(ForensicFlags.readMode(listOf("--read-mode")))
        // 对照组：同一条判读确实认得出合法的一档（读空会让上面三条恒真）
        assertEquals(DesktopReaderMode.HORIZONTAL_LTR, ForensicFlags.readMode(listOf("--read-mode=HORIZONTAL_LTR")))
    }

    @Test
    fun 别名表那一串不在取证参数这一路上() {
        // `RTL` 是**盘上读档**的别名，不是参数写法：借别名表就会把"递错了一档"变成"落到另一档"。
        assertNull(ForensicFlags.readMode(listOf("--read-mode=RTL")))
        assertEquals(DesktopReaderMode.HORIZONTAL_RTL, DesktopReaderMode.fromKey("RTL"))
    }

    @Test
    fun 本批没做的那两档照样认得出而不在判读层折() {
        // 折与念那句实话归那一屏（`desktopReaderModeSeedNote` 第二态），判读层只管"这是不是一档"。
        assertEquals(DesktopReaderMode.DOUBLE_PAGE, ForensicFlags.readMode(listOf("--read-mode=DOUBLE_PAGE")))
        assertEquals(
            DesktopReaderMode.HORIZONTAL_CONTINUOUS,
            ForensicFlags.readMode(listOf("--read-mode=HORIZONTAL_CONTINUOUS")),
        )
    }

    @Test
    fun 登记在表里而不是被念成拼错() {
        assertTrue("--read-mode 没登记进 KNOWN（会被 unknown 念成拼错）", ForensicFlags.KNOWN.contains("--read-mode"))
        assertTrue("认得出的参数被念成不认识", ForensicFlags.unknown(listOf("--read-mode=HORIZONTAL_RTL")).isEmpty())
    }
}
