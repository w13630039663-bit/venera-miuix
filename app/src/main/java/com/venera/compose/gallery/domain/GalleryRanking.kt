package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GallerySite
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 画廊搜索的**排行档**（2026-09-29）。
 *
 * 六档而不是五档：必须留一个「默认（最新）」，否则用户按了"周排行"就没有回头路
 * （站方默认序是新→旧，那一档不需要任何后缀）。
 *
 * 语义是「**这个窗口内新增的图里最热的**」，不是「两串标签求交集」那种合并 ——
 * 与上一轮的上下文栈同一条口径。
 *
 * [label] 给下拉里的整行，[shortLabel] 给胶囊上那一小截（胶囊行每一颗都在抢宽度）。
 */
enum class GalleryRanking(val label: String, val shortLabel: String) {
    NEWEST("默认（最新）", "默认"),
    DAY("按天排行", "天"),
    WEEK("按周排行", "周"),
    MONTH("按月排行", "月"),
    YEAR("按年排行", "年"),

    /** 不设窗口，只按分数排（= 历年最热）。 */
    ALL("全部排行", "全部"),
}

/**
 * 排行档的判据层。全部数字来自实测（`gallery-ranking-windows-2026-09.md` §〇 与 §十一），
 * 不是照另一站推断的：
 *
 * - **yande.re 原生认 `date:A..B`**（四形式都验过：`2026-05-01..2026-05-31` 回的全是 5 月、
 *   `2026-08-01..` 回的全 ≥ 08-01、`..2026-02-28` 回的全 ≤ 02-28、单日 `2026-05-20` 回 2 条都是那天）
 *   → 这一站**一次额外请求都不用发**，六档全真。
 * - **Gelbooru 只认 `sort:score:desc`**，时间窗口那一类它给不了：网页版认 `id:>=`（实测窗口确实收口），
 *   而 App 走的 DAPI 那一路要靠"探 id 边界"把窗口换算出来（真机第二轮：天/周能出图，月/年压根定不出
 *   起点 —— 那站每天约 1.2 万条新 id，月档要跨 36 万个 id，二分收口需要的探测次数超过当时给的预算）。
 *   → 用户拍板：这一站**只摆默认与全部两档**，其余四档连列都不列（同日第五轮由"置灰 +
 *   明写'本站没有'"改成"不列出"，判据没变，只是不再为讲不清来源的行占位）。
 *
 * ⚠️ 两站的分排序写法**不能互相照抄**：`order:score` 在 Gelbooru 是 0 结果，
 * `sort:score:desc` 在 yande.re 那侧没验过（而 `order=rank` 这种看着像的会被**静默忽略**，
 * 返回与默认完全相同的一批 —— 那类开关一律不摆）。
 */
object GalleryRankings {

    private const val YANDERE_SCORE = "order:score"
    private const val GELBOORU_SCORE = "sort:score:desc"
    private const val SAFEBOORU_SCORE = "order:score"

    /** 时间档的窗口起点（按 UTC 日切，与站方 `created_at` 同一时区）；不设窗口的档返回 null。 */
    fun windowStart(ranking: GalleryRanking, todayUtc: LocalDate): LocalDate? = when (ranking) {
        GalleryRanking.NEWEST, GalleryRanking.ALL -> null
        GalleryRanking.DAY -> todayUtc
        GalleryRanking.WEEK -> todayUtc.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        GalleryRanking.MONTH -> todayUtc.withDayOfMonth(1)
        GalleryRanking.YEAR -> todayUtc.withDayOfYear(1)
    }

    /**
     * 这一站这一档**是不是真的**。
     *
     * Gelbooru 的四档时间窗在 2026-09-29 被撤（成因一半在站方：DAPI 只给 `sort:score:desc`，
     * 窗口要靠探 id 边界换算；一半在我们自己：当时给的探测预算 24 次不够月/年收口，那站每天约
     * 1.2 万条新 id。撤下的实现与恢复条件见 `_trash/gallery-gelbooru-boundary-2026-09-29/`）。
     * 菜单列哪几行、状态层落回哪一档都照这一把判，
     * 才不会出"点得动、发出去却是全站结果"那种假开关。
     */
    fun supports(site: GallerySite, ranking: GalleryRanking): Boolean =
        site == GallerySite.YANDERE || site == GallerySite.SAFEBOORU || ranking == GalleryRanking.NEWEST || ranking == GalleryRanking.ALL

    /**
     * 一个**来源**（可能两条腿）能不能摆这一档：每一条腿都得真。
     *
     * 「全部」档只要有一条腿认不了这一档，这一档就不能算数 —— 半面墙按排行、半面墙按默认，
     * 屏上读不出对错，而胶囊上那个「周」字是在骗人。判据与 [supports] 同一把，只是量词从"这站"变成"每条腿"。
     */
    fun supportsAll(sites: List<GallerySite>, ranking: GalleryRanking): Boolean =
        sites.all { supports(it, ranking) }

    /**
     * 这一站有没有"具体某一期"可挑 —— 日期弹层那一个入口的判据。
     *
     * 定义直接挂在 [supports] 上（"月档在这一站是真的"），这样它不可能独立漂成新站多出一个
     * 点开就翻空墙的入口。Gelbooru 四档时间窗全判死 → 这一路为假。
     */
    fun supportsPeriodPicker(site: GallerySite): Boolean =
        supports(site, GalleryRanking.MONTH)

    /** 同上，量词换成"每一条腿"：「全部」档里有一条腿给不出时间窗，这一行整档不出现。 */
    fun supportsPeriodPickerAll(sites: List<GallerySite>): Boolean =
        sites.all { supportsPeriodPicker(it) }

    /** 这一档能往回翻到多早的一期（yande.re 存档实测：`date:..2006-06-01` 已经回 0 条）。 */
    val EARLIEST_PERIOD: LocalDate = LocalDate.of(2007, 1, 1)

    /** 窗口**终点**的站方口径：不设窗口的档为 null；有窗口的档是"锚点所在期的最后一天"。 */
    private fun periodEnd(ranking: GalleryRanking, anchor: LocalDate): LocalDate? = when (ranking) {
        GalleryRanking.NEWEST, GalleryRanking.ALL -> null
        GalleryRanking.DAY -> anchor
        GalleryRanking.WEEK -> anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        GalleryRanking.MONTH -> anchor.withDayOfMonth(anchor.lengthOfMonth())
        GalleryRanking.YEAR -> anchor.withDayOfYear(anchor.lengthOfYear())
    }

    /**
     * 这一档在 [anchor] 那一期上的**实际区间**（起、止都含当天）。
     *
     * 终点钉在 [todayUtc] 上：本期没走完的月/年不能把"下个月第一天"算进窗口 ——
     * 那会多摆一天，而多出来的那一天正好是"为什么这榜里有明天的图"这种查不动的读数。
     *
     * [anchor] 为 null = 本期（跟着 [todayUtc] 走）。历史期只给 [supportsHistory] 为真的档，
     * 见 `gallery-ranking-windows-2026-09.md` §十二。
     */
    fun windowBounds(
        ranking: GalleryRanking,
        anchor: LocalDate?,
        todayUtc: LocalDate,
    ): Pair<LocalDate, LocalDate>? {
        // 锚点夹回今天：日期弹层里"今年 12 月"是能点出来的，而窗口终点恒被今天钉住 ——
        // 不夹就会出现 `date:2026-12-01..2026-09-29` 这种起点在终点之后的串（站方回空，
        // 而空会被读成"那个月没有图"）。
        val pinned = anchor?.let { if (it.isAfter(todayUtc)) todayUtc else it } ?: todayUtc
        val start = windowStart(ranking, pinned) ?: return null
        val end = (periodEnd(ranking, pinned) ?: todayUtc).let {
            if (it.isAfter(todayUtc)) todayUtc else it
        }
        return start to end
    }

    /**
     * 这一档**允不允许选历史期**。
     *
     * 只有月/年两档开放（用户 2026-09-29 拍板）：天/周回看是"上一天/上一周"这种一步的事，
     * 为它开一层日期弹层不值，而菜单里那六行每一行都在抢下拉的高度。
     */
    fun supportsHistory(ranking: GalleryRanking): Boolean =
        ranking == GalleryRanking.MONTH || ranking == GalleryRanking.YEAR

    /**
     * 「按年」那一档能挑哪些年，**由近及远**。
     *
     * 为什么自己列而不是用 Material3 的年份选择器：1.5.0-alpha22 里那个 `YearPicker` 是
     * **private**，`showModeToggle` 只切"日历 ↔ 数字输入"，公开 API 到不了年份列表 ——
     * 于是"按年"只能画成一屏天数日历，用户 2026-09-29 的反馈原话是"按年份也是和按月份一样
     * 是一样的天数选择器"。地板取 [EARLIEST_PERIOD] 的年份，翻得出空页的年份一律不给。
     */
    fun yearChoices(todayUtc: LocalDate): List<Int> =
        (EARLIEST_PERIOD.year..todayUtc.year).toList().reversed()

    /** 下拉里那一行的副标：把"本周"落成一个说不错的日期区间（本期口径，不带历史锚点）。 */
    fun windowLabel(ranking: GalleryRanking, todayUtc: LocalDate): String? =
        windowLabel(ranking, null, todayUtc)

    /** 同上，但按 [anchor] 那一期的实际区间念。 */
    fun windowLabel(ranking: GalleryRanking, anchor: LocalDate?, todayUtc: LocalDate): String? {
        val (start, end) = windowBounds(ranking, anchor, todayUtc) ?: return null
        return if (start == end) "$start" else "$start ~ $end"
    }

    /** 摆给用户看的那一排（胶囊、页尾读数、复制去网页版都用它）——**永远不含伪标签**。 */
    fun queryOfVisible(filters: List<GalleryTagFilter>): String = GallerySearch.queryOf(filters)

    /** 拼在胶囊串**后面**那一截；`NEWEST`（站方默认序）不需要后缀。 */
    fun suffix(
        site: GallerySite,
        ranking: GalleryRanking,
        anchor: LocalDate?,
        todayUtc: LocalDate,
    ): String? {
        if (ranking == GalleryRanking.NEWEST) return null
        if (ranking == GalleryRanking.ALL) return when (site) {
            GallerySite.GELBOORU -> GELBOORU_SCORE
            GallerySite.SAFEBOORU -> SAFEBOORU_SCORE
            else -> YANDERE_SCORE
        }
        val (start, end) = windowBounds(ranking, anchor, todayUtc) ?: return null
        return when (site) {
            GallerySite.YANDERE -> "date:$start..$end $YANDERE_SCORE"
            GallerySite.SAFEBOORU -> "date:$start..$end $SAFEBOORU_SCORE"
            // Gelbooru 到不了这一支：[supports] 已经把它的四档时间窗判死（菜单不列 + 状态层落回默认档）。
            GallerySite.GELBOORU -> null
        }
    }

    /** 真正发出去的那一串 = 胶囊串 + 后缀。空条件时不留前导空格。 */
    fun searchQuery(
        site: GallerySite,
        filters: List<GalleryTagFilter>,
        ranking: GalleryRanking,
        anchor: LocalDate?,
        todayUtc: LocalDate,
    ): String {
        val base = queryOfVisible(filters)
        val suffix = suffix(site, ranking, anchor, todayUtc) ?: return base
        return if (base.isEmpty()) suffix else "$base $suffix"
    }

    /**
     * 胶囊上那一小截期次标记：月档 `2024-03`、年档 `2019`；本期与不给历史期的档都返回 null
     * （本期没什么可标的，标了反而像"这档和别处不一样"）。
     */
    fun periodShortLabel(ranking: GalleryRanking, anchor: LocalDate?): String? {
        if (anchor == null || !supportsHistory(ranking)) return null
        return if (ranking == GalleryRanking.MONTH) "%d-%02d".format(anchor.year, anchor.monthValue) else "${anchor.year}"
    }
}
