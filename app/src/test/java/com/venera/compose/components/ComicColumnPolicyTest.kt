package com.venera.compose.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

/**
 * 列数口径来自 master 的 `SliverGridDelegateWithComics`（brief 每 220dp 一列、
 * detailed 每 360dp 一列）与 `thumbnails.dart`（预览格宽上限 200dp）。
 * 这里锁死两端：手机档不得因为换算法而变化，宽屏档必须真的加列。
 */
class ComicColumnPolicyTest {
    @Test fun briefKeepsTwoColumnsOnPhonesAndGrowsOnlyWhenThereIsRoom() {
        assertEquals(2, comicListColumnCount("brief", 360.dp))
        assertEquals(2, comicListColumnCount("brief", 411.dp))
        assertEquals(2, comicListColumnCount(null, 360.dp))
        assertEquals(2, comicListColumnCount("ranking", 360.dp))
        assertEquals(3, comicListColumnCount("brief", 660.dp))
        assertEquals(5, comicListColumnCount("brief", 1280.dp))
    }

    @Test fun detailedIsSingleColumnOnPhonesAndMultiColumnOnWideWindows() {
        assertEquals(1, comicListColumnCount("detailed", 412.dp))
        assertEquals(2, comicListColumnCount("detailed", 720.dp))
        assertEquals(3, comicListColumnCount("detailed", 1280.dp))
    }

    @Test fun measuredWidthNeverCollapsesBelowTheShippedLayout() {
        // 首帧之前宽度可能是 0：列数必须落回既有观感档位，而不是炸成一列/零列
        assertEquals(2, comicListColumnCount("brief", 0.dp))
        assertEquals(1, comicListColumnCount("detailed", 0.dp))
        assertEquals(3, comicPreviewColumnCount(0.dp))
    }

    @Test fun previewColumnsStayThreeOnPhonesAndAddColumnsOnTablets() {
        assertEquals(3, comicPreviewColumnCount(312.dp))
        assertEquals(3, comicPreviewColumnCount(400.dp))
        assertEquals(5, comicPreviewColumnCount(1000.dp))
        assertEquals(6, comicPreviewColumnCount(1200.dp))
    }

    @Test fun chunkingRoundTripsAndPadsLastRowOnly() {
        for (count in 0..80) {
            val entries = (0 until count).toList()
            val rows = entries.chunked(comicListColumnCount("brief", 1280.dp))
            assertEquals(entries, rows.flatten())
            rows.dropLast(1).forEach { assertEquals(5, it.size) }
        }
    }

    // ── 首页推荐那一横排（2026-10-13：平板上"四张海报占掉半屏"改的口径）──

    /**
     * 每张的实宽**永远不窄于 token**，且越宽越贴近它。
     *
     * 第一版这里写的是"实宽 ∈ [token, token+gap)"，被自己跑出来的读数否掉了：
     * 320dp 上 floor 只给 2 张、每张 154dp（除不尽的余宽摊给每张）。那一档由调用点的
     * `comicListColumnCount(...) >= 3` 闸门挡着（手机档不走这一排），所以不影响屏，
     * 但尺子要钉**真性质**：上界随张数收敛（`card + (card+gap)/n`），宽档才逼近 token。
     */
    @Test fun recommendStripKeepsEachCardAtLeastTheTokenAndConvergingOnWide() {
        val card = 124f
        val gap = 12f
        for (width in intArrayOf(320, 420, 600, 800, 960, 1280, 1600, 2400)) {
            val columns = recommendStripColumns(width.dp, card.dp, gap.dp)
            val real = (width - (columns - 1) * gap) / columns
            assertTrue("${width}dp 上每张 $real 比 token 还窄", real >= card)
            assertTrue(
                "${width}dp 上每张 $real 超出收敛上界",
                real < card + (card + gap) / columns,
            )
        }
        // 平板那一档才是这条改判要看的：960dp 上每张 126.9，与历史行的 124 同一观感
        assertEquals(126.85714f, (960f - 6 * gap) / 7, 1e-3f)
    }

    /** 平板那一档要看到"一排小卡"而不是"四张海报"：960dp 上应是 7 张（旧口径是 4 张）。 */
    @Test fun recommendStripFillsWideBlocksInsteadOfFourPosters() {
        assertEquals(7, recommendStripColumns(960.dp, 124.dp, 12.dp))
        assertEquals(4, comicListColumnCount("brief", 960.dp))
        assertEquals(14, recommendStripColumns(1900.dp, 124.dp, 12.dp))
    }

    /** 越宽只会越多，不许反向掉档（分屏拖动与旋转都走这一条）。 */
    @Test fun recommendStripIsMonotoneInWidth() {
        var previous = 0
        for (width in 240..2400 step 20) {
            val columns = recommendStripColumns(width.dp, 124.dp, 12.dp)
            assertTrue("${width}dp 反而掉到 $columns（上一档 $previous）", columns >= previous)
            previous = columns
        }
    }

    /** 退化输入不许给出 0 张或负数：0 张就是那一行凭空消失。 */
    @Test fun recommendStripNeverCollapsesToZeroOrNegative() {
        assertEquals(1, recommendStripColumns(0.dp, 124.dp, 12.dp))
        assertEquals(1, recommendStripColumns((-500).dp, 124.dp, 12.dp))
        assertEquals(1, recommendStripColumns(960.dp, 0.dp, 0.dp))
        assertEquals(1, recommendStripColumns(960.dp, (-100).dp, 12.dp))
    }
}
