package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GallerySite
import java.util.Calendar

/**
 * 浏览历史里的一行：看过的**那一张图**。
 *
 * 只存五样事实 —— 站别、图号、缩略图地址、看过时刻、摆那一格要的比例。
 * 与桌面那颗件（`DesktopBrowseView`）逐字对齐：两边各写各的存储，字段口径分开漂了就会出现
 * "桌面看到的历史和手机不是一回事"。
 *
 * - **存比例而不存宽高**：墙上那一格吃的是 `GalleryPost.cardRatio`（优先 large 档、退 preview、
 *   再退原片，三者取一，见那颗属性的注释）。存两个数会多出一整族要判的形（0×0、负、除零），
 *   而这三档尺寸里只有一个数有意义 —— 那就存那一个数。
 * - **不存标题/画师**：那两样要从词典或站方现取，而历史墙只负责"让你认出并回到那张图"。
 *   存下来半年后就会把一个过期译名当标题摆出来（画师编号失效那一课，见 `GalleryArtistFollow` 头注）。
 *
 * @param viewedAt 看过那一刻的毫秒时间戳。它同时是定序键 —— 这份名单里"最近看过"就是全部语义。
 * @param ratio 当初站方给的画面宽高比。**0 = 没量到**（老档、或站方那一条没带尺寸），
 *   由 [GalleryBrowseRows.tileRatio] 退回占位比例，不许在这里除出 NaN 摆到屏上。
 */
data class GalleryBrowseRow(
    val site: GallerySite,
    val postId: Long,
    val thumbUrl: String,
    val viewedAt: Long,
    val ratio: Float = 0f,
) {
    /** 身份键，与收藏 / 关注同一族口径：`站点的 routeKey` + 图号（两站 id 各自编号，撞了是两张不同的图）。 */
    val uid: String get() = "${site.routeKey}:$postId"

    /**
     * 飞行落点登记的键（批 A 键统一，2026-10-19）：与 [com.venera.compose.gallery.data.GalleryPost.viewerKey]
     * 同一族 —— 历史是列表腿、没有页序，它就等于 [uid]。
     * 查询侧（大图页返回程）只认 `viewerKey`，登记侧从此不再各念各的键；
     * 两者字面相同是有意的，不是"碰巧相等"。
     */
    val viewerKey: String get() = uid
}

/** 浏览历史的时间分组。次序即屏上自上而下的次序。 */
enum class GalleryDayGroup {
    TODAY,
    YESTERDAY,
    OLDER,
}

/**
 * 浏览历史的纯判据。
 *
 * ## 与关注名单刻意相反的一条
 *
 * [GalleryArtistFollows.dedupe] 对重复关注**不重掷时间戳**（"我认得这个人"，连点两下不该把人顶到最前）。
 * 这里的 [withViewed] 对重复观看**刷新时刻并置顶** —— 历史是"我最近看了什么"，
 * 留旧时刻就等于"反复看的那张永远沉在昨天"，那这份名单就不是历史了。
 * 两条判据各写各的、各自注一句相反的理由，是刻意的：它们管的是两件不同的事。
 *
 * ## 排序不依赖集合实现
 *
 * 一律 `sortedWith(compareByDescending { viewedAt }.thenBy { uid })`：同一时刻按 uid 兜底，
 * 不靠 `LinkedHashSet` 或 map 的迭代顺序 —— 那种顺序读起来像 bug，又没有任何一条错误会指向它。
 */
object GalleryBrowseRows {

    /**
     * 记一笔「看过」之后的名单。
     *
     * 同一条（同 uid）已有的那一行**整行换掉**：时刻刷新、地址换新（站方换了缩略图 CDN 路径时，
     * 旧地址可能已失效），位置由排序自然带到最前。返回的是新名单，调用方自己决定要不要落盘。
     */
    fun withViewed(rows: List<GalleryBrowseRow>, row: GalleryBrowseRow): List<GalleryBrowseRow> =
        sorted(rows.filterNot { it.uid == row.uid } + row)

    /**
     * 按 uid 去重，同一条留**最近**那次（与 [withViewed] 同一条语义：这份名单里时刻就是序）。
     *
     * 读盘时用：手工改坏的档、或将来格式演进留下的重复行，进内存前先收成一份。
     */
    fun dedupe(rows: List<GalleryBrowseRow>): List<GalleryBrowseRow> {
        val byUid = LinkedHashMap<String, GalleryBrowseRow>()
        for (row in rows) {
            val prev = byUid[row.uid]
            if (prev == null || row.viewedAt > prev.viewedAt) byUid[row.uid] = row
        }
        return byUid.values.toList()
    }

    /** 上屏顺序：最近看过的在前；同一时刻按 uid 定序。 */
    fun sorted(rows: List<GalleryBrowseRow>): List<GalleryBrowseRow> =
        rows.sortedWith(compareByDescending<GalleryBrowseRow> { it.viewedAt }.thenBy { it.uid })

    /** 站点过滤：null = 「全部」那一档（胶囊行点掉的态）。 */
    fun ofSite(rows: List<GalleryBrowseRow>, site: GallerySite?): List<GalleryBrowseRow> =
        if (site == null) rows else rows.filter { it.site == site }

    /**
     * 历史墙那一格摆哪一档比例。夹在 0.4~2.5 —— **与墙上那张卡同一口径**（见 `GalleryPostCard`
     * 的 `ratio` 那行），否则同一张横长条在墙上是收着的、在历史里能把一整列撑出屏幕。
     *
     * 兜底值由调用方递（domain 不吃 UI token），传进来的就是 `tokens.spacing.coverAspectRatio`。
     * 负数与 NaN 一并挡掉：老档里那格可能是手工改坏的值，而一个 NaN 比例不会报错，
     * 只会让那一格在屏上占 0 高 —— 读起来像"历史记录丢了"。
     */
    fun tileRatio(ratio: Float, placeholderRatio: Float): Float {
        val usable = if (ratio.isFinite() && ratio > 0f) ratio else placeholderRatio
        return if (usable.isFinite() && usable > 0f) usable.coerceIn(0.4f, 2.5f) else 1f
    }

    /**
     * 这一行属于哪一组。按**日历日**判，不按 24 小时窗口 —— 「今天」是用户心里的那个今天。
     *
     * @param now 判据用的"现在"。由调用方传入（屏上 remember 一次），不在函数里现取：
     *   现取会让每次重组的边界都飘，跨午夜那一刷能把一行从「昨天」跳到「今天」。
     */
    fun dayGroupOf(row: GalleryBrowseRow, now: Long): GalleryDayGroup {
        val todayStart = startOfDay(now)
        return when {
            row.viewedAt >= todayStart -> GalleryDayGroup.TODAY
            // 昨天那一格取"今天的起点再往前一天"，而不是 24 小时前：夏令时辖区两者会错开一小时，
            // 而判据是日历日。本仓不涉夏令时，但算式按对的那条写，不留"反正一样"的隐患。
            row.viewedAt >= startOfDay(todayStart - 1L) -> GalleryDayGroup.YESTERDAY
            else -> GalleryDayGroup.OLDER
        }
    }

    /**
     * 分组后的名单，**只留有货的那些组**、按 [GalleryDayGroup] 声明次序。
     *
     * 空组不进结果：屏上"今天 / 昨天 / 更早"三个头恒摆会把没有内容的头也摆出来，
     * 而分组这件事的全部意义就是让你按时间找到那张图。
     */
    fun groupedByDay(
        rows: List<GalleryBrowseRow>,
        now: Long,
    ): Map<GalleryDayGroup, List<GalleryBrowseRow>> {
        val groups = LinkedHashMap<GalleryDayGroup, MutableList<GalleryBrowseRow>>()
        for (row in sorted(rows)) {
            groups.getOrPut(dayGroupOf(row, now)) { mutableListOf() }.add(row)
        }
        // 组内已经按 viewedAt 倒序（sorted 保证），组间按枚举次序重排一次：
        // 先出现的组未必是 TODAY（第一行可能已是更早的）。
        val ordered = LinkedHashMap<GalleryDayGroup, List<GalleryBrowseRow>>()
        for (group in GalleryDayGroup.entries) {
            groups[group]?.let { ordered[group] = it }
        }
        return ordered
    }

    private fun startOfDay(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
