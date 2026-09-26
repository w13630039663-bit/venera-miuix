package com.venera.compose.gallery.domain

import android.content.Context
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GelbooruClient
import com.venera.compose.gallery.data.YandeReClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 一次取数：两站各要**一批热门** → 交给 [GalleryMerge.mix] 去重、抽样、打乱。
 *
 * 为什么要单独一层：两站并发、任一失败要"照样出图但把缺的站说出来"，
 * 这套编排逻辑放在页面里会把 `GalleryScreen` 撑成一团，放 ViewModel 里又测不到。
 *
 * 刻意**不做**缓存，也刻意**没有翻页**：热门池本身是一屏到底的固定池子
 * （实测 yande.re 固定 40 条、`page`/`limit` 被忽略；Gelbooru 取 100 条里抽 20）。
 *
 * ## 两站的"热门"口径**不是同一件事**（这一点必须知道）
 *
 * - yande.re：`popular_recent?period=1d` —— **官方日榜**，真的是"过去一天"；
 * - Gelbooru：**没有日榜端点**（官方 DAPI 只有 post / tag / user / comment 四条列表），
 *   所以用元标签 `sort:score:desc` 模拟，取的是**全站高分池**。
 *
 * 因此 [Daily.date] 只对 yande.re 那一路有意义，页面上不能拿它去描述 Gelbooru ——
 * 那会写成"Gelbooru 这一天的热门"，而站方根本不提供按天的视图。
 * Gelbooru 那一路的说明文案见 [GalleryMerge] 与页面页尾。
 */
class GalleryFeedSource private constructor(context: Context) {

    private val appContext = context.applicationContext

    /**
     * @param failures 这轮**没给内容**的站及原因。非空时页面要挂一条提示 ——
     *                 否则"两站混搭"会静默退化成单站刷屏，那是本仓最忌的静默交错。
     * @param date     实际请求的那一天（只用于 yande.re 那一路的日榜）。
     */
    data class Daily(
        val merged: GalleryMerge.Merged,
        val failures: Map<GallerySite, String>,
        val date: String,
    )

    suspend fun loadDaily(seed: Long): Result<Daily> {
        val date = yesterdayString()
        val (yande, gelbooru) = coroutineScope {
            val y = async { guarded(GallerySite.YANDERE) { YandeReClient.getInstance(appContext).fetchDailyPopular() } }
            val g = async { guarded(GallerySite.GELBOORU) { GelbooruClient.getInstance(appContext).fetchTopScored() } }
            y.await() to g.await()
        }
        val failures = listOf(yande, gelbooru).mapNotNull { it.reason?.let { r -> it.site to r } }.toMap()
        val pools = mapOf(GallerySite.YANDERE to yande.posts, GallerySite.GELBOORU to gelbooru.posts)
        // 两站都没给上内容时，merge 只能报一句笼统的"没有可用内容"，真正的原因（401 / 超时 /
        // 池子空 / 返回非 JSON）就死在这里了 —— 优先把原因交出去。
        if (yande.posts.isEmpty() && gelbooru.posts.isEmpty()) {
            val notice = failures.entries.joinToString(" · ") { "${it.key.displayName}：${it.value}" }
            return Result.failure(IllegalStateException(notice.ifBlank { "两站都没有返回内容" }))
        }
        return GalleryMerge.mix(pools, seed).map { Daily(merged = it, failures = failures, date = date) }
    }

    private data class SiteResult(
        val site: GallerySite,
        val posts: List<GalleryPost>,
        val reason: String?,
    )

    /**
     * 把一站的取数包起来：异常不外抛，但**原因必须留着**交回页面。
     * 异常没带 message（超时类就是这样）时退回类名 —— 吞成 null 就等于没发生过错。
     */
    private inline fun guarded(site: GallerySite, block: () -> Result<List<GalleryPost>>): SiteResult {
        val result = block()
        val posts = result.getOrDefault(emptyList())
        val reason = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
            ?: "这一轮没有返回内容".takeIf { posts.isEmpty() }
        return SiteResult(site, posts, reason)
    }

    companion object {
        /**
         * "上一天"的 `yyyy-MM-dd`，按**设备本地**日历算。
         *
         * 站方按 UTC 日切，所以跨时区时这里可能取到站方口径的"今天"或"前天"——
         * 日榜两头的数都照样有内容，不值得为这个再发一次探测请求。
         * 刻意不做"空了就往前再退一天"的静默回退：那样用户看到的日期标签就是假的。
         */
        internal fun yesterdayString(now: Calendar = Calendar.getInstance()): String {
            val day = now.clone() as Calendar
            day.add(Calendar.DAY_OF_MONTH, -1)
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(day.time)
        }

        @Volatile
        private var INSTANCE: GalleryFeedSource? = null

        fun getInstance(context: Context): GalleryFeedSource = INSTANCE ?: synchronized(this) {
            INSTANCE ?: GalleryFeedSource(context.applicationContext).also { INSTANCE = it }
        }
    }
}
