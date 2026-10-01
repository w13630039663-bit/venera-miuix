package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryFeedSource
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画廊日榜加载的判据。
 *
 * 为什么值得单测：2026-09-26 真机反馈「画廊首页加载慢 / 永久转圈」，
 * 查下来不止一条成因，而其中**能在 JVM 上钉住的**只有这些判据
 * （真正的转圈要靠真机复现，见交付说明）。
 *
 * 这里锁的是**超时语义**与**缺席要说出来**这两件事 ——
 * 它们是"慢"这个观感问题里唯一可判的部分：
 * 快站不该等慢站，而缺席的站必须被明说（否则就成了静默单源，
 * 那是本仓最忌的"看起来正常但实际少了一半"）。
 */
class GalleryFeedBudgetTest {

    @Test
    fun `每站时间预算是一个明确的正数常量`() {
        // 12s：正常态 TTFB 实测 1.1~3.8s 的 3~4 倍余量，
        // 且明显低于单站最坏的 40s 级（readTimeout 20s ×2 + 重试）。
        assertTrue(GalleryFeedSource.PER_SITE_TIMEOUT_MS > 0)
        assertTrue(
            "预算应明显小于单站最坏的 40s，否则等于没设",
            GalleryFeedSource.PER_SITE_TIMEOUT_MS < 40_000L,
        )
        assertTrue(
            "预算要容得下正常态（实测 3.8s 也要过）",
            GalleryFeedSource.PER_SITE_TIMEOUT_MS >= 5_000L,
        )
    }

    @Test
    fun `超时被当成这一站没给内容而不是整轮失败`() {
        // 这条是②的核心：超时后**另一站仍要出图**。
        // 判据体现在 `GalleryLegOutcome` 上：超时的站 posts 为空、answered=false、reason 非空，
        // 于是 loadDaily 的 failures 会带上它、页面挂进 sourceNotice 说出来。
        // 只有两站**同时**为空才整轮失败。（批次 K 把这份包装收口进 `GalleryLegGuard`，
        // 逐条断言搬到了 `GalleryLegGuardTest`，这里只留预算那条读数。）
        val timeoutReason = "超过 ${GalleryFeedSource.PER_SITE_TIMEOUT_MS / 1000}s 没返回"
        assertTrue(timeoutReason.contains("没返回"))
        assertTrue(timeoutReason.contains("12"))
    }

    @Test
    fun `日期只对日榜那一站有意义`() {
        // 两站"热门"口径不同（yande.re 官方日榜 / Gelbooru 全站高分池），
        // 所以 date 不能拿去描述 Gelbooru —— 这里锁住"日期是独立返回的"这一事实，
        // 页面上怎么用是页面自己的口径（见 GalleryFeedEnd 的注释）。
        val d = GalleryFeedSource.yesterdayString()
        assertTrue("日期应是 yyyy-MM-dd", Regex("\\d{4}-\\d{2}-\\d{2}").matches(d))
    }
}
