package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryFeedSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * 「上一天」这一格日期是怎么算出来的。
 *
 * Danbooru 的日榜 `date` 是**必填**（实测缺省静默回 0 条），算错一天就是在看另一天的榜，
 * 而页面上那行日期标签还会把它说成用户以为的"昨天" —— 所以这条得有测试钉着。
 */
class GalleryFeedDateTest {

    private fun cal(year: Int, month: Int, day: Int) =
        GregorianCalendar(year, month - 1, day, 12, 0, 0).apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }

    @Test
    fun `月中普通一天退一格`() {
        assertEquals("2026-09-24", GalleryFeedSource.yesterdayString(cal(2026, 9, 25)))
    }

    @Test
    fun `跨月与跨年退到上月或上一年的最后一天`() {
        assertEquals("2026-08-31", GalleryFeedSource.yesterdayString(cal(2026, 9, 1)))
        assertEquals("2025-12-31", GalleryFeedSource.yesterdayString(cal(2026, 1, 1)))
    }

    @Test
    fun `闰年二月`() {
        assertEquals("2028-02-29", GalleryFeedSource.yesterdayString(cal(2028, 3, 1)))
        assertEquals("2026-02-28", GalleryFeedSource.yesterdayString(cal(2026, 3, 1)))
    }
}
