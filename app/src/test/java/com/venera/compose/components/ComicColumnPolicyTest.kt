package com.venera.compose.components

import org.junit.Assert.assertEquals
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
}
