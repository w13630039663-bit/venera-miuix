package com.venera.compose.feature.settings

import com.venera.compose.ui.tokens.ThemeSeedPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 钉「自定义取色」的两件事：预设表有没有被改动过口径，以及手输色值的边界。
 *
 * 色板值逐字来自 jay3-yy/BiliPai，所以这里要的不是「看起来对」，
 * 而是**与截图上那行 `#FA7298` 逐字相同** —— 那是我们当初认定这张表可信的唯一校准点。
 */
class ThemeSeedPresetTest {

    @Test fun sakuraPinkMatchesTheScreenshotCalibration() {
        val sakura = ThemeSeedPresets.All.first { it.name == "樱花粉" }
        assertEquals(0xFFFA7298.toInt(), sakura.argb)
        assertEquals("#FA7298", themeSeedHex(sakura.argb))
    }

    @Test fun tableIsWellFormed() {
        // BiliPai 用两条按下标对齐的表 + 单测防错位；这里合成一条，只剩重名与空值需要防。
        assertEquals(
            ThemeSeedPresets.All.size,
            ThemeSeedPresets.All.map { it.name }.toSet().size,
        )
        assertEquals(
            ThemeSeedPresets.All.size,
            ThemeSeedPresets.All.map { it.argb }.toSet().size,
        )
        ThemeSeedPresets.All.forEach {
            assertEquals("种子色必须实色", 0xFF000000.toInt(), it.argb and 0xFF000000.toInt())
        }
    }

    @Test fun gridFillsWholeRows() {
        // 5 列一行的排法：尾行不满会被占位撑开，这里先确认当前表本身是整的。
        assertEquals(0, ThemeSeedPresets.All.size % ThemeSeedPresets.Columns)
        assertEquals(5, ThemeSeedPresets.All.chunked(ThemeSeedPresets.Columns).size)
    }

    @Test fun everyPresetSurvivesHexRoundTrip() {
        ThemeSeedPresets.All.forEach { preset ->
            assertEquals(preset.argb, parseThemeSeedHex(themeSeedHex(preset.argb)))
            assertEquals(preset.name, ThemeSeedPresets.nameOf(preset.argb))
        }
    }

    @Test fun unpresetColorReportsCustom() {
        assertEquals(ThemeSeedPresets.CustomName, ThemeSeedPresets.nameOf(0xFF123456.toInt()))
    }

    @Test fun defaultSeedIsFirstPreset() {
        assertEquals(ThemeSeedPresets.All.first().argb, ThemeSeedPresets.DefaultArgb)
    }

    // ── 手输色值 ──

    @Test fun acceptsSixHexDigitsWithOrWithoutHash() {
        val expected = 0xFF00A1D6.toInt()
        assertEquals(expected, parseThemeSeedHex("#00A1D6"))
        assertEquals(expected, parseThemeSeedHex("00a1d6"))
        assertEquals(expected, parseThemeSeedHex("  #00A1D6  "))
    }

    @Test fun rejectsAnythingThatIsNotSixHexDigits() {
        // 不认 #RGB 缩写与 8 位带透明度：种子色必须实色，否则展开出来的色板说不清自己在哪个明暗档。
        assertNull(parseThemeSeedHex("#FFF"))
        assertNull(parseThemeSeedHex("FFF"))
        assertNull(parseThemeSeedHex("#FA7298FF"))
        assertNull(parseThemeSeedHex("12345"))
        assertNull(parseThemeSeedHex("gggggg"))
        assertNull(parseThemeSeedHex(""))
        assertNull(parseThemeSeedHex("# FA7298"))
    }
}
