package com.venera.compose.gallery.domain

import android.content.Context
import com.venera.compose.gallery.data.DanbooruClient
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.YandeReClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 一次取数：两站各要**上一天**的热门 → 交给 [GalleryMerge.mix] 去重、抽样、打乱。
 *
 * 为什么要单独一层：两站并发、任一失败要"照样出图但把缺的站说出来"，
 * 这套编排逻辑放在页面里会把 `GalleryScreen` 撑成一团，放 ViewModel 里又测不到。
 *
 * 刻意**不做**缓存，也刻意**没有翻页**：日榜本身就是一屏到底的固定池子
 * （实测 yande.re 固定 40 条、`page`/`limit` 被忽略；Danbooru 200 条里抽 20）。
 */
class GalleryFeedSource private constructor(context: Context) {

    private val appContext = context.applicationContext

    /**
     * @param failures 这轮**没给内容**的站及原因。非空时页面要挂一条提示 ——
     *                 否则"两站混搭"会静默退化成单站刷屏，那是本仓最忌的静默交错。
     * @param date     实际请求的那一天，供页面把"看的是哪天的热门"说出来。
     */
    data class Daily(
        val merged: GalleryMerge.Merged,
        val failures: Map<GallerySite, String>,
        val date: String,
    )

    suspend fun loadDaily(seed: Long): Result<Daily> {
        val date = yesterdayString()
        val (yande, danbooru) = coroutineScope {
            val y = async { guarded(GallerySite.YANDERE) { YandeReClient.getInstance(appContext).fetchDailyPopular() } }
            val d = async { guarded(GallerySite.DANBOORU) { DanbooruClient.getInstance(appContext).fetchDailyPopular(date) } }
            y.await() to d.await()
        }
        val failures = listOf(yande, danbooru).mapNotNull { it.reason?.let { r -> it.site to r } }.toMap()
        val pools = mapOf(GallerySite.YANDERE to yande.posts, GallerySite.DANBOORU to danbooru.posts)
        // 两站都没给上内容时，merge 只能报一句笼统的"没有可用内容"，真正的原因（403 / 超时 /
        // 日榜空 / 返回非 JSON）就死在这里了 —— 优先把原因交出去。
        if (yande.posts.isEmpty() && danbooru.posts.isEmpty()) {
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
