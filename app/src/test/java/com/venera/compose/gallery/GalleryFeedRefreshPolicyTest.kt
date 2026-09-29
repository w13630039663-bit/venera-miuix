package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryFeedRefreshPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 日榜缓存的补拉判据（2026-09-29 第四轮：用户要"进来别老刷新"，但日榜按天换）。
 */
class GalleryFeedRefreshPolicyTest {

    private val today = LocalDate.of(2026, 9, 29).toEpochDay()

    @Test
    fun `没有缓存 一定要取`() {
        assertTrue(GalleryFeedRefreshPolicy.shouldRefetch(null, today))
    }

    @Test
    fun `同一天 不取 —— 这就是「每次进画廊都刷新」那条反馈的落点`() {
        assertFalse(GalleryFeedRefreshPolicy.shouldRefetch(today, today))
    }

    @Test
    fun `跨天 补拉一次`() {
        assertTrue(GalleryFeedRefreshPolicy.shouldRefetch(today - 1, today))
        // 存得更"晚"（回退过系统时钟、或档是别的设备同步来的）也算过期，不做"未来缓存"的特例。
        assertTrue(GalleryFeedRefreshPolicy.shouldRefetch(today + 1, today))
    }
}
