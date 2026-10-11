package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GalleryRankings
import java.time.LocalDate

/**
 * 「排行榜」那一屏的选档计划（桌面第二十八批 批 28）。
 *
 * ## 这一屏为什么今天才点亮
 *
 * 批 25 量过这一行原先那句缺席理由：它一半说"取榜那条口不存在"、一半拿另一条端点上的读数
 * （`popular_recent` 固定 40 条、忽略分页）来描述排行榜走的那一路 —— 两处都不是事实。
 * 真实的边界是：口与六档判据都在共享面（[GalleryRankings]，桌面单人页从批 19 就在吃它），
 * 缺的只是"选档 + 摆榜"这一屏。本批就是那一屏。
 *
 * ## 桌面这边一行判据都不重定
 *
 * | 事 | 出处（桌面只转交，不另写） |
 * |---|---|
 * | 六档是哪六档、各自叫什么 | [GalleryRanking] |
 * | 这一站这一档是不是真的 | [GalleryRankings.supports] |
 * | 一组腿能不能摆这一档 | [GalleryRankings.supportsAll] |
 * | 窗口怎么算、终点为什么钉在今天 | [GalleryRankings.windowBounds] |
 * | 发出去那一串（两站写法不能互抄） | [GalleryRankings.searchQuery] |
 * | 屏上那一小截区间读数 | [GalleryRankings.windowLabel] |
 *
 * ## 这一族最忌的是假开关
 *
 * Gelbooru 的四档时间窗在 2026-09-29 被撤（那站 DAPI 只给 `sort:score:desc`，窗口要靠探 id 边界换算，
 * 而它每天约 1.2 万条新 id，二分收口要的次数超过当时给的预算）。所以带 Gelbooru 的那一组腿
 * **只兑现两档**：把「按周」摆出来点了它，发出去的是全站结果 —— 屏上那个「周」字在骗人。
 * 摆不出来的档逐档点名是哪一站拦的（[desktopRankingHidden]），而落回默认档那一句必须出声
 * （[desktopRankingNote]）：静默落回是这一族最难查的一种读数。
 */
internal data class DesktopRankingChoice(
    val ranking: GalleryRanking,
    /** 下拉/档行上那一整行的字（转交 [GalleryRanking.label]，桌面不改写）。 */
    val label: String,
    /** 本期窗口读数；不设窗口的两档为 null。 */
    val window: String?,
)

/** 这一组腿真兑现的档（次序恒为枚举序 —— 跟着数据飘会变成每次进来次序不一样）。 */
internal fun desktopRankingChoices(legs: List<GallerySite>, todayUtc: LocalDate): List<DesktopRankingChoice> =
    GalleryRanking.entries
        .filter { GalleryRankings.supportsAll(legs, it) }
        .map { DesktopRankingChoice(it, it.label, GalleryRankings.windowLabel(it, null, todayUtc)) }

/**
 * 摆不出来的档 → 拦事的那几站。
 *
 * "这一档不支持"是一句没有对象的话；用户接着要问"那谁不支持"，而那一问屏上答不了。
 */
internal fun desktopRankingHidden(legs: List<GallerySite>): Map<GalleryRanking, List<GallerySite>> =
    GalleryRanking.entries
        .filterNot { GalleryRankings.supportsAll(legs, it) }
        .associateWith { ranking -> legs.filterNot { GalleryRankings.supports(it, ranking) } }

/** 请求的档兑现不了 ⇒ 落回哪一档（落回**默认（最新）**，它是唯一一站都不挑的档）。 */
internal fun desktopRankingLanding(legs: List<GallerySite>, requested: GalleryRanking): GalleryRanking =
    if (GalleryRankings.supportsAll(legs, requested)) requested else GalleryRanking.NEWEST

/** 落回了就说一句；没落回交回 null（不念）。 */
internal fun desktopRankingNote(requested: GalleryRanking, landing: GalleryRanking): String? =
    if (requested == landing) null else "这一组腿里兑现不了「${requested.label}」，已落回「${landing.label}」"

/** 这一站这一档发出去的那一串 —— 逐字转交共享那颗，桌面一个字都不拼。 */
internal fun desktopRankingQuery(
    site: GallerySite,
    ranking: GalleryRanking,
    anchor: LocalDate?,
    todayUtc: LocalDate,
): String = GalleryRankings.searchQuery(site, emptyList(), ranking, anchor, todayUtc)

/** 屏上那句"这一榜数的是哪一段时间"（不设窗口的两档交回 null = 不念）。 */
internal fun desktopRankingWindowReading(
    ranking: GalleryRanking,
    anchor: LocalDate?,
    todayUtc: LocalDate,
): String? = GalleryRankings.windowLabel(ranking, anchor, todayUtc)

/**
 * 这一档在桌面上**开不开**"回看历史期"那一层（批 29）。
 *
 * 两条判据都是共享面的：`supportsHistory` 只对月/年开放回看（天/周回看是"一步"的事，
 * 为它开一层控件不值），而 `supportsPeriodPickerAll` 要求**每一条腿**都给得出时间窗 ——
 * 半面墙能回看、半面墙不能，屏上那个「2024-03」就是在骗人。
 */
internal fun desktopRankingHistoryAllowed(legs: List<GallerySite>, ranking: GalleryRanking): Boolean =
    GalleryRankings.supportsHistory(ranking) && GalleryRankings.supportsPeriodPickerAll(legs)

/** 锚点归一到"那一期的起点"：月档落到月初、年档落到年初。 */
private fun periodStart(ranking: GalleryRanking, day: LocalDate): LocalDate = when (ranking) {
    GalleryRanking.MONTH -> day.withDayOfMonth(1)
    GalleryRanking.YEAR -> day.withDayOfYear(1)
    else -> day
}

/**
 * 往某一步走之后的锚点；**交回 null = 本期**（包括"已经在本期还要往前"与"越过本期"两种）。
 *
 * 归一先于步长：`2026-08-19` 与 `2026-08-01` 是同一个月，往回一步都该落到 `2026-07-01`。
 * 地板取共享面那个实测数（[GalleryRankings.EARLIEST_PERIOD]，yande.re 再早就回空）——
 * 到得了的那一期是地板本身，再往前一步交回 null 而不是造一个翻得出空页的锚点。
 */
internal fun desktopRankingStep(
    ranking: GalleryRanking,
    from: LocalDate?,
    backwards: Boolean,
    todayUtc: LocalDate,
): LocalDate? {
    if (!GalleryRankings.supportsHistory(ranking)) return null
    val current = from?.let { periodStart(ranking, it) } ?: periodStart(ranking, todayUtc)
    val thisPeriod = periodStart(ranking, todayUtc)
    if (backwards) {
        val next = when (ranking) {
            GalleryRanking.MONTH -> current.minusMonths(1)
            else -> current.minusYears(1)
        }
        return if (next.isBefore(GalleryRankings.EARLIEST_PERIOD)) null else next
    }
    if (!current.isBefore(thisPeriod)) return null
    val next = when (ranking) {
        GalleryRanking.MONTH -> current.plusMonths(1)
        else -> current.plusYears(1)
    }
    return if (next.isBefore(thisPeriod)) next else null
}

/**
 * 那一行期次读数：`本期 2026-10-01 ~ 2026-10-07` / `2024-03 2024-03-01 ~ 2024-03-31`。
 *
 * 期次标记与区间两处都转交共享那颗（桌面一处日期算术都不写 —— 判据在 `DesktopRankingPeriodTest` ③）。
 * 不设窗口的两档交回空串（那两档没有"期"可念）。
 */
internal fun desktopRankingAnchorReading(
    ranking: GalleryRanking,
    anchor: LocalDate?,
    todayUtc: LocalDate,
): String {
    val window = desktopRankingWindowReading(ranking, anchor, todayUtc) ?: return ""
    return "${GalleryRankings.periodShortLabel(ranking, anchor) ?: "本期"} $window"
}

/**
 * `--ranking=` 那颗种子的形状（桌面第三十批 批 30）。
 *
 * 它是**初值**而不是指令：[anchor] 为 null = 本期，而这一屏今天会不会真发出去那一档、
 * 那一期开不给回看，仍由 [desktopRankingLanding] 与 [desktopRankingHistoryAllowed] 那两条判 ——
 * 种子不越过判据，它只替用户把下拉拨到那一格。
 */
internal data class DesktopRankingSeed(val ranking: GalleryRanking, val anchor: LocalDate?)

/** 档的初值：没给种子就是站方默认序那一档（它是唯一一条腿都不挑的档）。 */
internal fun desktopRankingSeedRanking(seed: DesktopRankingSeed?): GalleryRanking =
    seed?.ranking ?: GalleryRanking.NEWEST

/**
 * 锚点的初值：只有"种子指的那一档"与"这一屏真发出去的那一档"**对上**才带过来。
 *
 * 带 Gelbooru 的那一组腿里种 `MONTH/2024-03` 会落回默认档，此时锚点若跟过来就成了
 * 「屏上写着默认档、发出去带着一串 2024-03」—— 屏上看不出、只有抓包能看出，正是这一族最难查的读数。
 */
internal fun desktopRankingSeedAnchor(seed: DesktopRankingSeed?, landing: GalleryRanking): LocalDate? =
    seed?.takeIf { it.ranking == landing }?.anchor
