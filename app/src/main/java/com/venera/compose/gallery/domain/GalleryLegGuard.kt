package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 一条**腿**（一站的一次取数）的结果。
 *
 * [answered] 是这一份形状存在的理由：**"站方回了 0 条"与"这一腿压根没答上"是两件事**。
 * 混成一谈，"缺席不算到底"那把判据就会把一次 401 或一次解析失败当成"这站到底了"，
 * 于是那一站在本次会话里再也不出货，而页尾写着「已经到底」—— 现象是"少了一个站的图"，
 * 读起来像站方本来就没图，且全程不报错。
 */
data class GalleryLegOutcome(
    val site: GallerySite,
    val posts: List<GalleryPost>,
    /** true = 站方回了一段读得懂的表（哪怕 0 条）；超时、凭据被拒、解析不出来都是 false。 */
    val answered: Boolean,
    /** 没给货时说出来为什么；答上了且表非空时是 null。 */
    val reason: String?,
)

/**
 * 「一条腿」的取数包装：**并发 + 一笔时间预算 + 原因不许吞**。
 *
 * 这一份从前在项目里有三处各写一遍（日榜取数、猜你喜欢翻页、搜索的两腿并发）。
 * 三份迟早分叉 —— 2026-09-30 收口时就在其中一份里查到一条真缺陷
 * （失败那一支照样把 `returned` 记成 0，见 [GalleryLegOutcome.answered]），
 * 所以这里只留一份，三处共用。
 *
 * 时间预算的理由原样保留：不加预算的话单站最坏能拖到 40s 级
 * （readTimeout 20s → 限流重试一次 → 再 20s，callTimeout 45s 封顶），
 * 而 UI 那头只有"整屏空等"一种表达 —— 用户既看不出是哪一站慢，也拿不到快的那一站。
 * 取舍是**慢的那一站这一轮缺席（会被说出来），换来快的那站立刻出图**（空等不会说）。
 */
object GalleryLegGuard {

    /** 空表那句读数：站方答上了、却没给行。与"没答上"必须用不同的话说。 */
    const val NO_CONTENT: String = "这一轮没有返回内容"

    /**
     * 把一次取数翻成 [GalleryLegOutcome]。
     *
     * 拆成纯函数是为了上单测：`withTimeoutOrNull` 那一层要协程，能测的只有下面这几行判据，
     * 而这几行恰好是三处最容易分叉的地方。
     */
    fun outcomeOf(
        site: GallerySite,
        timedOut: Boolean,
        outcome: Result<List<GalleryPost>>?,
        timeoutMs: Long = GalleryFeedSource.PER_SITE_TIMEOUT_MS,
    ): GalleryLegOutcome {
        if (timedOut || outcome == null) {
            // 超时**不是失败**，是"这一站这一轮没赶上"：调用方按"没答上"处理，
            // 于是它会挂进既有的缺席读数里被明说出来，而不是把整屏换成错误页。
            return GalleryLegOutcome(site, emptyList(), answered = false, "超过 ${timeoutMs / 1000}s 没返回")
        }
        val posts = outcome.getOrDefault(emptyList())
        val error = outcome.exceptionOrNull()
        return when {
            // 异常没带 message（超时类就是这样）时退回类名 —— 吞成 null 就等于没发生过错。
            error != null -> GalleryLegOutcome(site, posts, answered = false, error.message ?: error.javaClass.simpleName)
            posts.isEmpty() -> GalleryLegOutcome(site, posts, answered = true, NO_CONTENT)
            else -> GalleryLegOutcome(site, posts, answered = true, reason = null)
        }
    }

    suspend fun guard(
        site: GallerySite,
        timeoutMs: Long = GalleryFeedSource.PER_SITE_TIMEOUT_MS,
        block: suspend () -> Result<List<GalleryPost>>,
    ): GalleryLegOutcome {
        val outcome = withTimeoutOrNull(timeoutMs) { block() }
        return outcomeOf(site, timedOut = outcome == null, outcome = outcome, timeoutMs = timeoutMs)
    }
}
