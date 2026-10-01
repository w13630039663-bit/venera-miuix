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
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

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
 *
 * ## 每站一笔独立的时间预算（2026-09-26）
 *
 * 旧写法把两站 `async` 之后依次 `await()`，整屏耗时 = max(两站)；
 * 而单站最坏能拖到 40s 级（readTimeout 20s → RateLimiting 失败重试一次 → 再 20s，
 * callTimeout 45s 封顶）。UI 那头只有「posts 为空 → 满屏波浪环」一种表达，
 * 于是"某一站慢"必然表现为**整屏空等** —— 用户既看不出是哪一站，也拿不到图。
 *
 * 现在每站套 [PER_SITE_TIMEOUT_MS]：超时的那站按"这一轮没给内容"交出去，
 * 由页面挂进 `sourceNotice` 明说，**另一站照旧出图**。
 * 取舍：慢的那一站这一轮缺席（会被说出来），换来快的那站立刻画出来（空等不会说）。
 *
 * 2026-09-30 起这套"一条腿 + 一笔预算 + 原因不许吞"的包装收在 [GalleryLegGuard]（三处共用一份，
 * 从前是三处各写一遍，其中一份已经把"请求失败"记成"回了 0 条"）；
 * 预算数值仍留在本类，因为日榜、推荐翻页与搜索两腿念的是同一个数。
 */
class GalleryFeedSource private constructor(context: Context) {

    private val appContext = context.applicationContext

    /**
     * @param failures 这轮**没给内容**的站及原因。非空时页面要挂一条提示 ——
     *                 否则"两站混搭"会静默退化成单站刷屏，那是本仓最忌的静默交错。
     * @param date     实际请求的那一天（只用于 yande.re 那一路的日榜）。
     * @param pools    两站**滤之前的原始池**。日榜那面墙的「换一批」靠它做本地重排
     *                 （只换种子、不再联网，用户 2026-09-30 拍板），所以整片原始返回要交出去 ——
     *                 合并后那 40 张已经被抽样与去重吃掉了大半，拿它重排只是把同一批图再洗一遍。
     */
    data class Daily(
        val merged: GalleryMerge.Merged,
        val failures: Map<GallerySite, String>,
        val date: String,
        val pools: Map<GallerySite, List<GalleryPost>>,
    )

    suspend fun loadDaily(seed: Long): Result<Daily> {
        val date = yesterdayString()
        val (yande, gelbooru) = coroutineScope {
            val y = async { GalleryLegGuard.guard(GallerySite.YANDERE) { YandeReClient.getInstance(appContext).fetchDailyPopular() } }
            val g = async { GalleryLegGuard.guard(GallerySite.GELBOORU) { GelbooruClient.getInstance(appContext).fetchTopScored() } }
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
        return GalleryMerge.mix(pools, seed).map {
            Daily(merged = it, failures = failures, date = date, pools = pools)
        }
    }

    companion object {
        /**
         * 每一站的时间预算（毫秒）。
         *
         * 12s 的来由：本机实测正常态 TTFB 1.1~3.8s（yande.re 日榜 / gelbooru 同形状请求），
         * 取 3~4 倍作尾部余量；同时明显低于单站最坏的 40s 级，
         * 好让"某一站拖住"不至于变成"整屏空等"。
         * ⚠️ 这是**观感取舍**不是正确性判据 —— 慢的那一站这一轮缺席（会被说出来），
         * 换来快的那站立刻出图。
         */
        const val PER_SITE_TIMEOUT_MS = 12_000L

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
