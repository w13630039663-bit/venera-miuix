package com.venera.compose.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertTrue
import org.junit.Test

class WideScreenPolicyTest {
    @Test fun phoneBandNeverGetsAWidthCap() {
        // 手机档必须返回 null：一旦返回数值，顶栏/底栏/控制岛都会被悄悄改掉。
        assertNull(wideScreenChromeMaxWidth(360.dp))
        assertNull(wideScreenChromeMaxWidth(411.dp))
        assertNull(wideScreenChromeMaxWidth(600.dp))
    }

    @Test fun wideBandIsCappedByMasterGlassBarContract() {
        // master: min(_kGlassBarMaxWidth = 540, 窗口宽 - 24*2)，阈值 changePoint = 600。
        assertEquals(540.dp, wideScreenChromeMaxWidth(601.dp))
        assertEquals(540.dp, wideScreenChromeMaxWidth(1280.dp))
        assertEquals(540.dp, wideScreenChromeMaxWidth(2000.dp))
    }

    /**
     * 「窗口宽 - 48」这一项在 >600dp 档里永远赢不了 540（600-48=552 > 540），
     * 也就是说 master 原式在手机上其实退化成了定宽 540。这里把它钉住，
     * 免得后人以为宽窗变窄时收口会跟着缩 —— 不会，除非同时改阈值。
     */
    @Test fun windowTermCannotBeatTheCapAboveTheThreshold() {
        for (width in 601..2000 step 37) {
            assertEquals(540.dp, wideScreenChromeMaxWidth(width.dp))
        }
    }

    // ── 档位模型（第二轮）──────────────────────────────────────────────
    // 阈值 600 / 1300 抄 master 的 changePoint / changePoint2，不是 MD3 的 600 / 840。

    /**
     * 两条断点都按严格 `>` 判，边界值卡两侧各钉一次：600 归 Compact、601 才进 Medium；1300 归 Medium。
     *
     * 「恰好 600 归 Compact」不是随手挑的：master `nav:251` 原文是 `if (width > changePoint) target = 2`，
     * 600dp 上官方给的是底部胶囊而非侧栏；本仓 `wideScreenChromeMaxWidth(600.dp)` 既有单测也是同一侧。
     */
    @Test fun breakpointsAreExclusiveOnBothSides() {
        assertEquals(WideScreenLayoutMode.Compact, wideScreenLayoutMode(599.dp))
        assertEquals(WideScreenLayoutMode.Compact, wideScreenLayoutMode(600.dp))
        assertEquals(WideScreenLayoutMode.Medium, wideScreenLayoutMode(601.dp))
        assertEquals(WideScreenLayoutMode.Medium, wideScreenLayoutMode(1299.dp))
        assertEquals(WideScreenLayoutMode.Medium, wideScreenLayoutMode(1300.dp))
        assertEquals(WideScreenLayoutMode.Expanded, wideScreenLayoutMode(1301.dp))
    }

    /** 侧栏宽抄 master `_kFoldedSideBarWidth` / `_kSideBarWidth` 的插值稳态值。 */
    @Test fun sideBarWidthsMatchMasterSteadyState() {
        assertEquals(0.dp, sideBarWidthFor(WideScreenLayoutMode.Compact))
        assertEquals(72.dp, sideBarWidthFor(WideScreenLayoutMode.Medium))
        assertEquals(224.dp, sideBarWidthFor(WideScreenLayoutMode.Expanded))
    }

    /**
     * 批次 2 的终值：Compact 76 / Medium 0 / Expanded 0。
     *
     * 归0 的前提是「侧栏档不渲染底栏」，两者必须同一批落地（批次 2 已同时做：
     * `Navigation.kt` 的底栏门加了 `!useSideBar`，`VeneraSideBar` 接管侧栏档导航）。
     *
     * 这条用例会主动绊红任何「只改 clearance 没关底栏」或「只关底栏没改 clearance」的半截改动。
     */
    @Test fun bottomBarClearanceIsZeroOnceSideBarReplacesBottomBar() {
        assertEquals(76.dp, bottomBarClearanceFor(WideScreenLayoutMode.Compact))
        assertEquals(0.dp, bottomBarClearanceFor(WideScreenLayoutMode.Medium))
        assertEquals(0.dp, bottomBarClearanceFor(WideScreenLayoutMode.Expanded))
    }

    /**
     * 黄金不变量（方案 §3.6 扩写的那条）：**给定档位下，底栏可见 ⟺ 底部留白 76dp**。
     *
     * 「有底栏 ⟺ 有留白」如果破了，症状是侧栏档最后一行内容被悬浮底栏压住，
     * 而且**只在宽窗出现、手机档全好**，普通回归测试抓不到 —— 所以钉在这里。
     */
    @Test fun bottomBarVisibleIffClearanceIsPositive() {
        for (mode in WideScreenLayoutMode.entries) {
            val bottomBarRenders = mode == WideScreenLayoutMode.Compact
            assertEquals(
                bottomBarRenders,
                bottomBarClearanceFor(mode) > 0.dp,
            )
        }
    }

    /** 侧栏宽度：Compact 必须是 0（侧栏不出现，内容占满），否则会与底栏叠加。 */
    @Test fun sideBarWidthIsZeroOnCompactSoItNeverStacksWithBottomBar() {
        assertEquals(0.dp, sideBarWidthFor(WideScreenLayoutMode.Compact))
        // 内容内缩量 == 侧栏宽是master nav:310-312 稳态公式的自洽性保证（方案 §2.3）
        assertTrue(
            "侧栏宽必须与内容内缩量同源，Compact 档两者都是 0",
            sideBarWidthFor(WideScreenLayoutMode.Compact) == 0.dp,
        )
    }

    /**
     * `isWideScreen` 改成走档位后，现有消费点必须一个像素都不动 ——
     * 它就是 `!= Compact` 的别名，跨 600 的翻转点也得一致。
     */
    @Suppress("DEPRECATION")
    @Test fun isWideScreenStaysAnAliasOfNotCompact() {
        for (width in intArrayOf(0, 360, 599, 600, 601, 1299, 1300, 1301, 2000)) {
            val expected = wideScreenLayoutMode(width.dp) != WideScreenLayoutMode.Compact
            assertEquals(expected, isWideScreen(width.dp))
        }
    }

    /**
     * 图片墙列数（画廊墙 / 每日推荐 / 画廊收藏 / 图片收藏共用）。
     * 这条用例是用户 2026-10-02 那句「我们调的都是大屏版，别动手机版」的**实现层保险**：
     * Compact 档直接 return 2，不参与 200dp 预算 —— 因为裸算 `ceil(宽/200)` 会让
     * 425dp 以上的大屏机与折叠屏合起态从 2 列变 3 列，那是偷偷改了手机版观感。
     * 逐个宽度扫到 600dp（含 600 这条边界，它按 §2.4 归 Compact）都不许出现 3。
     */
    @Test fun phoneBandIsPinnedAtTwoColumnsAndNeverTouchesTheBudget() {
        for (width in 320..600 step 5) {
            val columns = imageWallColumnCount(width.dp, WideScreenLayoutMode.Compact, 24.dp)
            assertEquals("手机档 $width.dp 必须是 2 列", 2, columns)
        }
    }

    /** 侧栏档下限 3 = 改动前 `if (wide) 3 else 2` 的原值 ⇒ 任何宽度都不会变得比今天更大。 */
    @Test fun wideBandNeverGivesFewerColumnsThanTheOldFixedThree() {
        for (width in 601..2000 step 7) {
            val mode = wideScreenLayoutMode(width.dp)
            val columns = imageWallColumnCount(width.dp, mode, 24.dp)
            assertTrue("$width.dp 只许加列不许减列", columns >= 3)
        }
    }

    /**
     * 具体读数钉死（每列预算 200dp，扣掉本档侧栏宽与左右 12dp 内边距）。
     * 这几格就是"平板 3 列→6 列、宽窗 3 列→8 列"那次改版的验收口径，观感变了要在这里留痕。
     */
    @Test fun columnCountsAtTheMeasuredBands() {
        val padding = 24.dp
        // 601dp：刚跨断点，扣掉 72dp 侧栏后只够 3 列 ⇒ 与改版前一致
        assertEquals(3, imageWallColumnCount(601.dp, WideScreenLayoutMode.Medium, padding))
        // 1280dp（Pixel Tablet 原生横屏）：1280-72-24 = 1184 ⇒ 6 列，一格约 189dp
        assertEquals(6, imageWallColumnCount(1280.dp, WideScreenLayoutMode.Medium, padding))
        // 1706dp（本机覆写密度后的实测宽）：1706-224-24 = 1458 ⇒ 8 列，一格约 173dp
        assertEquals(8, imageWallColumnCount(1706.dp, WideScreenLayoutMode.Expanded, padding))
    }

    /** 跨进 Expanded 时侧栏从 72 跳到 224，列数会回落一档 —— 这是扣掉侧栏宽的正常结果，不是抖动。 */
    @Test fun crossingIntoExpandedDropsColumnsBecauseTheRailWidens() {
        val padding = 24.dp
        val mediumAt1300 = imageWallColumnCount(1300.dp, WideScreenLayoutMode.Medium, padding)
        val expandedAt1301 = imageWallColumnCount(1301.dp, WideScreenLayoutMode.Expanded, padding)
        assertEquals(7, mediumAt1300)
        assertEquals(6, expandedAt1301)
    }
}
