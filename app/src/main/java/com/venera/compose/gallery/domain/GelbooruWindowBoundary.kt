package com.venera.compose.gallery.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Gelbooru 的**时间窗口起点定界**（2026-10-13 重做，纯函数层 —— 本文件不发请求）。
 *
 * ── 为什么这一站要定界、而 yande.re 不要 ──
 *
 * 站方的检索引擎**不给时间窗**。2026-10-13 带凭据在 `page=dapi` 上现量，判据用信封里的
 * `count`（它随窗口缩 —— 这一点 09-29 那份方案文档写着"未实测"，今天补上了）：
 * - 不带 tags ⇒ `count=14,409,081`（基线）
 * - `date:2026-10-06..2026-10-12` ⇒ `count=0` ⇒ **不认**（与 09-29 站内 HTML 那次同判）
 * - `id:14994000..` 与 `id:14994000..15078216` ⇒ `count` 与基线**逐字相同** ⇒ **被静默忽略**
 *   （最阴的一种：看着像生效，其实交回全站那批 —— 所以这一档的成败只认 `count` 与日期，不认"200")
 * - `id:>=14994000` ⇒ `count=83,868`；`id:>14994000` ⇒ `83,867`（恰好少一条，两枚语义自洽）⇒ **真生效**
 *
 * 于是"这一档从哪天起"必须换算成"从哪个 id 起"。用的原语是**一笔到位**那条：
 * `tags=id:>=M sort:id:asc&limit=1` 回的是"比 M 老不了多少的第一条"，自带 `created_at`
 * ⇒ 问一次就知道 M 这个位置落在站方哪一天（实测 `id:>=14994000 sort:id:asc` ⇒
 * `id=14994000` / `Tue Sep 29 17:30:32 -0500 2026`）。
 * 不用 `id:<M&limit=1`（那颗会踩删帖留下的 id 空洞，且只给"比 M 新的那条"= 上界信息，绕）。
 *
 * ── 2026-10-16 补：窗口**上界**也做得成（用户给凭据后要量的就是这一格） ──
 * `id:>=A` 与 `id:<B` 是**各自生效**的两枚标签，算术严丝合缝（同一基线 14,426,357）：
 * - `id:>=14718000` ⇒ 375,233；`id:>=14994000` ⇒ 101,393
 * - `id:>=14718000 id:<14994000` ⇒ **273,840** = 375,233 − 101,393（严格互补）
 * - `id:<14994000` 单独 ⇒ 14,324,964 = 基线 − 101,393
 * - 窗口内 `sort:id:asc` 回 `id=14718000`（08-17）、`sort:score:desc` 回 `id=14875089`（09-10，确在窗口内）
 * ⇒ 所以"翻到具体某一月 / 某一年"就是 `id:>=A id:<B sort:score:desc` 两枚边界，不是做不到。
 * 旧的"区间做不到"那条结论**只对区间语法成立**：`id:14718000..14994000` ⇒ `count` 与基线**逐字相同**
 * （静默忽略），`date:2026-09-01..2026-09-30` ⇒ `count=0`。整份读数在 `_qa/gb_window_probe.sh`。
 *
 * ── 当年为什么判"月/年收不了口" ──
 *
 * 那一批的日速常数是 **1.2 万条/天**，估点因此比真位置偏后近一倍 —— 退让步数与二分深度全被撑大，
 * 24 次预算就是这么烧光的。今天的现量约 **6.5k–8.4k 条/天**（独立两路：13 天跨 84,094 条 ⇒ 6,469/天；
 * 月档 `id:>=` 的 `count=194,606` ÷ 30 ⇒ 6,487/天），取 **7,000**。
 * 按这枚常数跑今天的真实读数：**周档 2 笔、月档 4 笔**就收口（这台台架在 `_qa` 外的临时脚本上量出来的）。
 *
 * ⚠️ 但 7,000 只是**今天**的日速，这一站早年慢得多（2010 那一段约 1 千条/天）—— 拿常数估 2024 年那一页
 * 会一次偏到几百天之外，二分要烧 14 笔（真站逐笔 0.9–2.0 秒 ⇒ ≈21 秒，12 秒的每腿预算内压根探不完）。
 * 2026-10-16 于是烘进一张 [CALIBRATION] 逐年标定表，并把跳步与区间内的候选都改成交给**当段日速**算。
 * 同一台架、同一批目标日的前后对照（`_qa/gb_tr4.txt` 对 `_qa/gb_tablerate2_compare.txt` 的 bisect 那几行）：
 * - 按天 5 笔 → 5 笔、按周 6 笔 → 3 笔、本期按月 7 笔 → 4 笔
 * - 一年半前那一月（起 + 止两枚）：22 请求 ≈ 22.7 秒 → **11 请求 ≈ 10.0 秒**
 * - 两年半前那一年（起 + 止两枚）：27 请求 ≈ 33.4 秒 → **11 请求 ≈ 12.0 秒**
 * ⇒ 翻历史期从"每腿 12 秒预算内探不完"变成探得完（两枚边界那一腿另给一档预算，见下）。
 * 表怎么量出来的在 `_qa/gb_calibration_ledger.txt`（140 笔，2008..2026 逐年一条）。
 *
 * 单调性也量过了（追加式 id，不是哈希序）：
 * id 15,078,094→2026-10-12、14,994,000→09-29、14,718,000→08-17、14,358,000→06-24，四点全单调；
 * 2026-10-16 沿 id 又采了六点（1→2007-07-16、4,550,684→2019-01-01、9,426,385→2024-01-01、
 * 11,229,956→2025-01-01、13,223,080→2026-01-01、15,095,861→2026-10-15），全单调。
 */
internal object GelbooruWindowBoundary {

    /**
     * 日速估计（条/天）——**今天那一段**的现量，只剩两处用到：
     * ① 目标日就是今天或更晚时的估点（[interpolatedId] 那一支，表上没有"明天"）；
     * ② [CONVERGENCE_GRANULARITY] 的分母。
     * 早年那一带的日速由 [CALIBRATION] 那一段一段给（2010 年约 1 千条/天，差七倍）。
     * 和以前一样：**只用于估点**，错了由日期差修正与二分兜，不参与最终判读。2026-10-13 现量取 7,000。
     */
    const val IDS_PER_DAY = 7_000L

    /**
     * 收口粒度：区间宽度降到这里就认"够准了"。875 条 ≈ **今天**三来小时的上传量 ——
     * 窗口起点最多差这三小时，而再往下切是"每砍一半多一笔请求"，换不来肉眼差别。
     *
     * ⚠️ 这一枚是**条数**而不是小时数，所以早年的窗口边上它折算的时间更长
     * （2010 那一页 875 条 ≈ 一天）。方向仍然只有"少摆"那一头（见 [settleOn]），
     * 而 30 天的月档少一天边角的 0.5% 不影响"这一期的高分是哪些"。
     */
    const val CONVERGENCE_GRANULARITY = IDS_PER_DAY / 8

    /**
     * 探测上限。数的是**这一轮问出去几笔**：历史期那一轮要定两枚边界（起、止），
     * 两枚共用这一份预算（上界那枚吃起点那枚的读数当种子，见 `huntWindowBounds`）。
     * 2026-10-13 真机 401 那一轮糊成"没探成"的教训仍然算数：烧光预算是要被说出来的，不是重试能救的。
     */
    const val MAX_PROBES = 18

    /**
     * 「闭环窗口」那一腿的时间预算（历史期：起、止两枚边界都要探）。
     *
     * 2026-10-16 带凭据在真站上逐笔量（`_qa/gb_range_hunt_rig.js`，读数 `_qa/gb_tr4.txt`）：
     * 烘表之后一年半前那一档是 **11 请求 ≈ 10.0 秒**、两年半前那一年 **11 请求 ≈ 12.0 秒**，
     * 单笔往返 0.4–2.0 秒（同一次会话里差五倍 —— 所以按最慢那档留一倍余量，而不是按平均值）。
     * 本期那一档只定一枚边界、5–8 请求，日/周/月/年 2026-10-15 真机实测能在 12s 那档内出图，**不动它**。
     *
     * 放宽只放宽**等待**，不放宽**判据**：定不出来照样是"这一腿不发"，而屏上那句超时读数里的
     * 秒数跟着预算走（`GalleryLegGuard.outcomeOf`），不会变成一颗没有反馈的转圈。
     */
    const val CLOSED_WINDOW_LEG_TIMEOUT_MS = 30_000L
}

/** 一笔读数：在 `atLeastId` 这位置问到的站方那一天（**站方自己日历上的那一天**，见 [parseGelbooruStampDay]）。 */
internal data class BoundaryReading(val atLeastId: Long, val day: LocalDate)

/**
 * 定界**断在哪一步**。屏上那句「窗口没探成」过去把五种成因糊成一句，用户与排查的人都分不出
 * 该去配凭据还是该等站方缓过来（2026-10-13 真机这一笔就是这么卡的：`fetchLatestPostId` 吃了 401，
 * 而卡片只说"没探成"）。所以每一步各占一枚，调用方按枚说人话。
 *
 * [GelbooruWindowHunt.Broken.detail] 带站方原话（401 那句"没接受这组凭据"就是它给的）。
 */
internal enum class GelbooruWindowBreak {
    /** 探测通道没接上（桌面上那颗契约不接线 → 恒这一枚）。 */
    ProbeNotWired,

    /** 取"站方最新一条的号"那一笔被拒。 */
    LatestIdRejected,

    /** 站方回了但号不可用（空表或 `id<=0`）。 */
    LatestIdUnusable,

    /** 定界探针那一笔被拒。 */
    ProbeRejected,

    /** 探针问到的位置上没有条目。 */
    ProbeNoEntry,

    /** 站方这一笔**没带** `created_at`（空串 / 全空白）。与"带了但读不出"分开：出路不是改解析器。 */
    StampMissing,

    /** `created_at` 读不出日子。 */
    StampUnparsable,

    /** 命中总数与问的位置不自洽 ⇒ 这一站的 `id:>=` 没按语义生效。 */
    CountInconsistent,

    /** 同一枚位置问第二遍，或相邻两问回同一条读数 ⇒ 原地打转。 */
    SameReadingRepeated,

    /** 烧光探测上限仍没括住。 */
    ProbeBudget,

    /** 已经问到站方最新一条，仍没括住这一档。 */
    TopReachedWithoutBracket,
}

/** 一次定界的结论：要么给出这一枚边界，要么说清断在哪一步。 */
internal sealed interface GelbooruWindowHunt {
    /**
     * 定出来的那枚编号，含义随 [BoundarySide] 走：
     * 起点那一档是"**这一期第一条**"（含，拼 `id:>=`），上界那一档是"**第一条已出这一期**"（不含，拼 `id:<`）。
     * 断掉时是 null。
     */
    val boundaryId: Long?

    /** 这一轮到目前为止的观测，供**下一枚边界**当种子（同一站、同一轮、单调函数相同，读数是通用的）。 */
    val readings: List<BoundaryReading>

    data class Found(
        override val boundaryId: Long,
        override val readings: List<BoundaryReading>,
    ) : GelbooruWindowHunt

    data class Broken(
        val at: GelbooruWindowBreak,
        val detail: String? = null,
        override val readings: List<BoundaryReading> = emptyList(),
    ) : GelbooruWindowHunt {
        override val boundaryId: Long? = null
    }
}

/**
 * 这一枚边界是窗口的**哪一头** —— 两头的收口方向不一样，而且不是对称的取舍。
 *
 * 见 [settleOn]：一头少摆几张是看得出的方向，另一头多摆下一期的图是**读不出**的方向。
 */
internal enum class BoundarySide {
    /** 窗口第一天：交"第一条落在这一期里的编号"（含）。 */
    WINDOW_START,

    /** 窗口**之后**的第一天：交"第一条不属于这一期的编号"（不含）。 */
    WINDOW_END,
}

internal sealed interface BoundaryStep {
    /** 下一步去问这一枚 id（`tags=id:>=it sort:id:asc&limit=1`）。 */
    data class Ask(val atLeastId: Long) : BoundaryStep

    /** 定出来了：交 [BoundarySide] 说的那一枚（起点含、上界不含，见 [settleOn]）。 */
    data class Resolved(val boundaryId: Long) : BoundaryStep

    /** 定不出来。⚠️ 调用方必须把它当"**这一档不发**"处理，不许原样交回全站那批 ——
     *  那正是"按了周榜、屏上却是全站高分"那种假开关的形状。
     *  [at] 说清是哪一步断的（屏上那一句要能指着它说话），[reason] 是给自己人看的整句。 */
    data class GiveUp(val at: GelbooruWindowBreak, val reason: String) : BoundaryStep
}

/**
 * 收口时交哪一枚 —— 两头只能舍一边，而两头的失败模式**不对称**。
 *
 * 括住目标日的 `[low, high]` 里，真边界落在粒度 [GelbooruWindowBoundary.CONVERGENCE_GRANULARITY]
 * 之内（今天 875 条 ≈ 三小时的上传量），这一段没法再问：
 * - 起点取 `high`（已经在这一期里的第一条）⇒ 最坏**少**开头几小时。少了看得出（榜少几张），
 *   这一侧的口径 2026-10-13 就定了。
 * - 上界取 `low + 1`（还没出这一期的最后一条之后）⇒ 同样只会**少**期末几小时。
 *   反过来交 `high` 就是把**下一期**的开头摆进这一期的榜里 —— 多出来的那批在屏上读不出任何异常，
 *   而胶囊上写着这一期的期次，那正是本仓最忌的静默交错。
 */
private fun settleOn(side: BoundarySide, low: BoundaryReading, high: BoundaryReading): Long =
    if (side == BoundarySide.WINDOW_END) low.atLeastId + 1L else high.atLeastId

/**
 * 定界状态机：给定站方最新 id、目标日、以及**这一轮到目前为止的读数**，返回下一步。
 *
 * 记 `f(M)` = 读数里"比 M 老不了多少的第一条"的日期（随 M 不减）。要找的是
 * **第一个日期 ≥ [targetDay] 的 id**，所以：
 * - `f(M) < targetDay` ⇒ M 还在目标日之前 ⇒ 它是**下界**（起点在 M 之后）；
 * - `f(M) ≥ targetDay` ⇒ M 已进入或越过目标日 ⇒ 它是**上界**（起点 ≤ M）。
 * 一上一下括住、宽度收到 [GelbooruWindowBoundary.CONVERGENCE_GRANULARITY] 就交 [settleOn] 那枚。
 *
 * @param maxId 站方最新 id（一笔 `limit=1` 不带 tags 取的）
 * @param todayUtc 只用来算估点，不参与判读
 * @param side 这一枚是窗口的哪一头（收口方向不同，见 [settleOn]）
 */
internal fun gelbooruBoundaryStep(
    maxId: Long,
    targetDay: LocalDate,
    todayUtc: LocalDate,
    readings: List<BoundaryReading>,
    side: BoundarySide,
): BoundaryStep {
    val gran = GelbooruWindowBoundary.CONVERGENCE_GRANULARITY
    // 跳步用的日速取**目标日那一带**的实测斜率，不是全局常数：这一站 2010 年前后 1 千多条/天，
    // 今天 6 千多条/天，拿常数去跳 2024 年那一页会一次跨过几百天（真站实测多烧 8 笔）。
    val rate = interpolatedRate(maxId, targetDay, todayUtc)
    if (maxId <= 0L) return BoundaryStep.GiveUp(GelbooruWindowBreak.LatestIdUnusable, "站方最新 id 拿不到")
    if (readings.size >= GelbooruWindowBoundary.MAX_PROBES) {
        return BoundaryStep.GiveUp(
            GelbooruWindowBreak.ProbeBudget,
            "探测到上限 ${GelbooruWindowBoundary.MAX_PROBES} 笔仍没括住",
        )
    }

    val sorted = readings.sortedBy { it.atLeastId }
    val low = sorted.filter { it.day.isBefore(targetDay) }.maxByOrNull { it.atLeastId }
    val high = sorted.filter { !it.day.isBefore(targetDay) }.minByOrNull { it.atLeastId }

    return when {
        // 括住了：收口够就交 [settleOn] 那一枚（两头取的边不同），否则问下一笔。
        low != null && high != null -> {
            val mid = (low.atLeastId + high.atLeastId) / 2L
            // 候选按准头排，取第一个**严格落在区间里**的（2026-10-16 真站台架逐档量过这四档的相对位次）：
            // ① 从"最近一条已知仍在目标日之前"的观测，按当段日速插过去 —— 地基是真读数，比表的绝对位置准；
            // ② 标定表的绝对插值（观测全在目标日之后时它才是有用的那一枚）；
            // ③ 往回跳一天的量：上界那条读数**就落在目标日里**，边界最多在它左边一天的量之内；
            // ④ 取中点。
            // ⚠️ "严格落在区间里"这道守卫不许省：区间已经窄过一天的时候，③会被夹成 `low+1`，
            // 退化成一次挪一个 id 直到烧光上限（同一台架实测 18 笔全废、一笔没收口）。
            val secant = low.atLeastId + rate * ChronoUnit.DAYS.between(low.day, targetDay)
            val next = listOfNotNull(
                secant,
                interpolatedId(maxId, targetDay, todayUtc),
                if (high.day == targetDay) high.atLeastId - rate else null,
            ).firstOrNull { it > low.atLeastId && it < high.atLeastId }
                ?: mid
            if (high.atLeastId - low.atLeastId <= gran || next <= low.atLeastId) {
                BoundaryStep.Resolved(settleOn(side, low, high))
            } else {
                BoundaryStep.Ask(next)
            }
        }
        // 全是上界 ⇒ 起点在更小的 id 那头，按"多读了多少天"往回退（比翻倍省得多）。
        high != null -> {
            val overDays = ChronoUnit.DAYS.between(targetDay, high.day).coerceAtLeast(0L)
            val back = high.atLeastId - (overDays * rate + gran)
            if (back < 1L) BoundaryStep.Ask(1L) else BoundaryStep.Ask(back)
        }
        // 全是下界 ⇒ 起点在更大的 id 那头，按"还差多少天"往前进。
        low != null -> {
            val shortDays = ChronoUnit.DAYS.between(low.day, targetDay).coerceAtLeast(0L)
            val forward = low.atLeastId + (shortDays * rate + gran)
            // 过头了**不许直接认输**：「按天」那一档的目标日就是今天，估点天然贴着最新 id，
            // 往前一跳必然越界 —— 那就等于这一档永远定不出来、永远不发，屏幕上是一颗按不动的行。
            // 最新那条自己就是一枚合法的上界候选（问它必然命中，就是站方此刻的位置）。
            if (forward >= maxId) {
                if (maxId > low.atLeastId) BoundaryStep.Ask(maxId)
                else BoundaryStep.GiveUp(
                    GelbooruWindowBreak.TopReachedWithoutBracket,
                    "已到站方最新 id $maxId，仍没括住这一档",
                )
            } else BoundaryStep.Ask(forward)
        }
        // 第一笔：估点。
        else -> BoundaryStep.Ask(estimateStartId(maxId, targetDay, todayUtc))
    }
}

/**
 * 站方 **id ↔ 日期**标定表：2026-10-16 由 `_qa/gb_calibration_rig.js` 在带凭据的 `page=dapi` 上
 * 逐年量出来的"每一年的第一条"（2008..2026，共 140 笔探测；2007 那一年站方 7 月 16 日才开张，
 * 最老的一条就是 `id=1`，所以表里没有 2007 那一行，比它更早的目标日一律落到 1）。
 *
 * 用途只有一个：**估点**（以及估点需要的那一带日速）。估点不参与判读 —— 最终交出去的边界
 * 完全由真读数决定，所以这张表哪怕整体偏移，后果也只是多问一两笔，不会把窗口挪到错误的一天。
 *
 * 为什么值得烘一张表进来（2026-10-16 同一批目标日各跑一遍，`_qa/gb_range_hunt_rig.js`）：
 * 全局常数 7,000 条/天是**今天**的日速，而这一站早年慢得多 —— 按常数估 2024 年 1 月，
 * 一笔画到站方另一头去，二分要烧 14 笔（≈ 21 秒）；照表插值同一枚目标日 **2 笔**。
 * 七年前的月档：17 笔 → 5 笔。翻历史期那一档从"12 秒预算内压根探不完"变成"探得完"。
 *
 * id 只会追加，这些读数不会过期。唯一的失效场景是站方清库重编号 —— 那时表与真位置对不上，
 * 判读仍然只看真读数，多花的探测笔数由 [GelbooruWindowBoundary.MAX_PROBES] 封顶、超了就"不发"。
 */
private val CALIBRATION: List<Pair<LocalDate, Long>> = listOf(
    LocalDate.of(2008, 1, 1) to 132_780L,
    LocalDate.of(2009, 1, 1) to 408_115L,
    LocalDate.of(2010, 1, 1) to 676_962L,
    LocalDate.of(2011, 1, 1) to 1_020_846L,
    LocalDate.of(2012, 1, 1) to 1_382_843L,
    LocalDate.of(2013, 1, 1) to 1_757_475L,
    LocalDate.of(2014, 1, 1) to 2_116_802L,
    LocalDate.of(2015, 1, 1) to 2_536_841L,
    LocalDate.of(2016, 1, 1) to 2_994_895L,
    LocalDate.of(2017, 1, 1) to 3_497_853L,
    LocalDate.of(2018, 1, 1) to 4_038_636L,
    LocalDate.of(2019, 1, 1) to 4_550_684L,
    LocalDate.of(2020, 1, 1) to 5_065_808L,
    LocalDate.of(2021, 1, 1) to 5_781_465L,
    LocalDate.of(2022, 1, 1) to 6_790_924L,
    LocalDate.of(2023, 1, 1) to 8_084_232L,
    LocalDate.of(2024, 1, 1) to 9_426_385L,
    LocalDate.of(2025, 1, 1) to 11_229_956L,
    LocalDate.of(2026, 1, 1) to 13_223_080L,
)

/** 表 + 一枚**活锚**（今天 = 站方最新 id）；比表上最老一行更早的日子用这一枚下界。 */
private val CALIBRATION_FLOOR_ID = 1L

private fun segment(from: Pair<LocalDate, Long>, to: Pair<LocalDate, Long>, day: LocalDate): Long {
    val span = ChronoUnit.DAYS.between(from.first, to.first).coerceAtLeast(1L)
    val into = ChronoUnit.DAYS.between(from.first, day).coerceAtLeast(0L)
    return from.second + (to.second - from.second) * into / span
}

/** [day] 落在表上哪一段（找不到 = 在最老一行之前，返回第一段的索引 1）。 */
private fun segmentIndex(maxId: Long, day: LocalDate, todayUtc: LocalDate): Pair<List<Pair<LocalDate, Long>>, Int> {
    val points = CALIBRATION + (todayUtc to maxId)
    if (points.size < 2) return points to 1
    var index = points.lastIndex
    for (i in 1..points.lastIndex) {
        if (day.isBefore(points[i].first)) {
            index = i
            break
        }
    }
    return points to index
}

/**
 * [day] 大致落在哪枚 id 上 —— 表上那一段按天线性插值。
 *
 * ⚠️ 这只用来**开问**，从不参与判读（判读只看站方回的那一天的日期）。
 */
internal fun interpolatedId(maxId: Long, day: LocalDate, todayUtc: LocalDate): Long {
    if (maxId <= 0L) return CALIBRATION_FLOOR_ID
    if (day.isBefore(CALIBRATION.first().first)) return CALIBRATION_FLOOR_ID
    // 目标日就是今天（「按天」那一档）或更晚：表上没有"今天之后"那一段，而贴着最新一条开问会掉进
    // "从今天一步步往回挪"的形状（真站逐笔量：那样要 9 笔，往左让开一天只要 5 笔）。
    if (!day.isBefore(todayUtc)) {
        return (maxId - GelbooruWindowBoundary.IDS_PER_DAY).coerceIn(CALIBRATION_FLOOR_ID, maxId)
    }
    val (points, i) = segmentIndex(maxId, day, todayUtc)
    return segment(points[i - 1], points[i], day).coerceIn(CALIBRATION_FLOOR_ID, maxId)
}

/** [day] 那一带的日速 = 表上那一段的斜率（早年 1 千多/天、今天 6 千多/天，差五倍 —— 跳步必须用当段的）。 */
internal fun interpolatedRate(maxId: Long, day: LocalDate, todayUtc: LocalDate): Long {
    if (maxId <= 0L) return GelbooruWindowBoundary.IDS_PER_DAY
    val (points, i) = segmentIndex(maxId, day, todayUtc)
    val from = points[i - 1]
    val to = points[i]
    val span = ChronoUnit.DAYS.between(from.first, to.first).coerceAtLeast(1L)
    return ((to.second - from.second) / span).coerceAtLeast(1L)
}

/** 估点：照标定表插值出 [targetDay] 那一带的位置（口径见 [interpolatedId]）。 */
internal fun estimateStartId(maxId: Long, targetDay: LocalDate, todayUtc: LocalDate): Long =
    interpolatedId(maxId, targetDay, todayUtc)

/**
 * 这一枚窗口的**上界**（拼进 `searchQueryFor` 的那一颗）。
 *
 * ⚠️ 三档语义不能压成一枚可空 Long：`null` 既可能是"这一档压根不需要上界"，也可能是
 * "上界没探成"—— 前者该发、后者该**不发**，读错一边就是屏上那句"周榜"配上一整面全站高分的墙。
 */
sealed interface GelbooruWindowEnd {

    /** 不给上界：本期没有比"站方最新一条"更晚的图，或已**证到**最新一条仍在这期之内。 */
    data object Unbounded : GelbooruWindowEnd

    /** 上界（**不含**）：第一条已经出了这一期的编号，拼成 `id:<exclusiveId`。 */
    data class Before(val exclusiveId: Long) : GelbooruWindowEnd
}

/** 一次闭环窗口定界的结论：起、止两枚都到手，或者说清断在哪一步。 */
internal sealed interface GelbooruWindowBounds {
    data class Ready(val startId: Long, val end: GelbooruWindowEnd) : GelbooruWindowBounds

    /** 有一枚边界没定出来 ⇒ 调用方必须让这一腿**不发**（见 [BoundaryStep.GiveUp] 同一条口径）。 */
    data class Blocked(val at: GelbooruWindowBreak, val detail: String?) : GelbooruWindowBounds
}

/**
 * 把上面那台状态机**开成一次真探测**（移动端专用，2026-10-13 —— 桌面那一侧今天不接这一路）。
 *
 * 取数由调用方用两枚挂起函数递进来，所以这颗文件仍然不出网、仍然能在单测里真跑：
 * - [latestId]：站方最新一条的 id（估点要往回推的那根锚）；
 * - [probeAtLeast]：问 `id:>=M sort:id:asc&limit=1`，交回 `Triple(最老那条的 id, 它的 created_at 原文, 命中总数)`。
 *   两枚都**把出网那一笔的失败原样带回来**（`Result.failure`），而不是先压成 null —— 压掉之后屏上就只剩
 *   一句"没探成"，分不清是没配凭据还是在等站方缓过来（2026-10-13 真机就是被这一格卡住的）。
 *
 * 两条口径是照 [feedback-degrade-paths] 那条规矩写的（这里指本仓那句"降级不许静默交错"）：
 * - **定不出来交回 [GelbooruWindowHunt.Broken]**，调用方必须让这一档**不发**，而不是原样交回全站那批；
 * - **失败不缓存**（凭据掉了、站方抽风都是会好的事），成功才缓存，按"哪一头 + 哪一天"一把 ——
 *   同一天的同一头天然同键，不需要 TTL。
 *
 * `count` 不是摆设：这里拿它做一次**单调自检**。站方命中总数应随 `M` 增大而不增
 * （`id:>=14994000` ⇒ 83,868 而 `id:>=15029216` ⇒ 48,893，今天实测）。
 * 一旦读到反了，说明这一站的 `id:>=` 没按它该按的方式生效（最阴的一种就是"被静默忽略"）——
 * 那就认输，别把一个全站结果当成周榜交上去。
 */
internal class GelbooruWindowFinder(
    /** 站方最新一条的 id。`null` = 这颗契约压根没接上；`failure` = 出网那一笔被拒，**原话留着**报给用户。 */
    private val latestId: suspend () -> Result<Long>?,
    /** 问 `id:>=M sort:id:asc&limit=1`，交回 `Triple(最老那条的 id, created_at 原文, 命中总数)`。 */
    private val probeAtLeast: suspend (Long) -> Result<Triple<Long, String, Long>>?,
) {

    private val cached = mutableMapOf<Pair<BoundarySide, LocalDate>, GelbooruWindowHunt.Found>()

    /** [hunt] 只取起点的那一面 —— 四档收口笔数那几条判据用它，不必跟着结论换形。 */
    suspend fun startIdFor(targetDay: LocalDate, todayUtc: LocalDate): Long? =
        hunt(BoundarySide.WINDOW_START, targetDay, todayUtc, emptyList()).boundaryId

    /**
     * 一次**闭环窗口**的定界：先定这一天的起点，再定"这一期之后第一天"的上界。
     *
     * [dayAfterWindow] 为 null = 这一档天然收到最新（本期），第二笔不用发。
     *
     * 第二枚吃第一枚的**读数当种子**：同一站、同一轮、同一支单调函数，观测是通用的 ——
     * 2026-10-16 真站台架（`_qa/gb_range_hunt_ledger.txt`）：2024-04-01 那一枚单独探要 14 笔，
     * 带着另一枚的读数只问 8 笔。代价是探测上限变成**这一对**共同的预算
     * （见 [GelbooruWindowBoundary.MAX_PROBES]），起点那一枚烧光了，上界就不问了 —— 反正结论一样：不发。
     * ⚠️ 那对读数是**换标定表之前**那版估点量出来的；换成表插值之后两枚各自都已省到 2–6 笔，
     * 稠密桩上共享读数甚至不省笔（15 vs 15，量过）。所以"省笔"只留在真站台账里，没钉成判据 ——
     * 判据钉的是这一对**共担一份上限**（`GelbooruWindowFinderTest.thePairSharesOneProbeBudget`）。
     */
    suspend fun huntWindowBounds(
        firstDay: LocalDate,
        dayAfterWindow: LocalDate?,
        todayUtc: LocalDate,
    ): GelbooruWindowBounds {
        val start = hunt(BoundarySide.WINDOW_START, firstDay, todayUtc, emptyList())
        if (start is GelbooruWindowHunt.Broken) {
            return GelbooruWindowBounds.Blocked(start.at, start.detail)
        }
        val startId = checkNotNull(start.boundaryId) { "定出来的一枚不会没有编号" }
        if (dayAfterWindow == null) return GelbooruWindowBounds.Ready(startId, GelbooruWindowEnd.Unbounded)
        val end = hunt(BoundarySide.WINDOW_END, dayAfterWindow, todayUtc, start.readings)
        if (end is GelbooruWindowHunt.Broken) {
            // 上界断在"问到站方最新一条、它仍在这期之内"：那不是没探成，是**证到了**这一期就是收到最新 ——
            // 于是交回不带上界的那一串，与本期那一档逐字相同，而不是"探不成还硬发"。
            if (end.at == GelbooruWindowBreak.TopReachedWithoutBracket) {
                return GelbooruWindowBounds.Ready(startId, GelbooruWindowEnd.Unbounded)
            }
            return GelbooruWindowBounds.Blocked(end.at, end.detail)
        }
        return GelbooruWindowBounds.Ready(startId, GelbooruWindowEnd.Before(checkNotNull(end.boundaryId)))
    }

    /**
     * 开一次定界。断在哪一步由 [GelbooruWindowHunt.Broken.at] 说，站方的原话在 `.detail`。
     *
     * 成功才缓存（失败存一次就变永久不发）。[seed] = 同一轮里另一枚边界已经问到的观测。
     */
    suspend fun hunt(
        side: BoundarySide,
        targetDay: LocalDate,
        todayUtc: LocalDate,
        seed: List<BoundaryReading>,
    ): GelbooruWindowHunt {
        cached[side to targetDay]?.let { return it }
        val topProbe = latestId() ?: return GelbooruWindowHunt.Broken(GelbooruWindowBreak.ProbeNotWired)
        val top = topProbe.getOrElse {
            return GelbooruWindowHunt.Broken(
                GelbooruWindowBreak.LatestIdRejected,
                topProbe.exceptionOrNull()?.message,
            )
        }
        if (top <= 0L) return GelbooruWindowHunt.Broken(GelbooruWindowBreak.LatestIdUnusable)
        val readings = seed.toMutableList()
        // 问出去的每一枚 id 与它当时的命中总数。自检**按位置比**：命中总数应随"问的 id"增大而不增。
        // 二分回退时会问比上一笔更靠左的 id，所以不能拿"这一笔 vs 上一笔"当尺子（那会把正常收口误杀）。
        // 这一份**不跨种子继承**：命中总数是问的那一刻的量，隔了几秒的另一枚边界问出来的数不可比。
        val countAsks = mutableListOf<Pair<Long, Long>>()
        while (true) {
            when (val step = gelbooruBoundaryStep(top, targetDay, todayUtc, readings.toList(), side)) {
                is BoundaryStep.Resolved -> {
                    val found = GelbooruWindowHunt.Found(step.boundaryId, readings.toList())
                    cached[side to targetDay] = found
                    return found
                }
                is BoundaryStep.GiveUp -> return GelbooruWindowHunt.Broken(step.at, step.reason, readings.toList())
                is BoundaryStep.Ask -> {
                    // 自检：同一枚位置问第二遍 = 状态机没往前走。
                    if (countAsks.any { it.first == step.atLeastId }) {
                        return GelbooruWindowHunt.Broken(
                            GelbooruWindowBreak.SameReadingRepeated,
                            readings = readings.toList(),
                        )
                    }
                    val probe = probeAtLeast(step.atLeastId)
                        ?: return GelbooruWindowHunt.Broken(GelbooruWindowBreak.ProbeNotWired)
                    val reading = probe.getOrElse {
                        return GelbooruWindowHunt.Broken(
                            GelbooruWindowBreak.ProbeRejected,
                            probe.exceptionOrNull()?.message,
                        )
                    }
                    val oldestId = reading.first
                    val rawStamp = reading.second
                    // 没带时间 与 带了读不出 是两件事，分开交出去（屏上那两句的出路不同，见这两枚枚举）。
                    if (isGelbooruStampMissing(rawStamp)) {
                        return GelbooruWindowHunt.Broken(GelbooruWindowBreak.StampMissing)
                    }
                    val day = parseGelbooruStampDay(rawStamp) ?: return GelbooruWindowHunt.Broken(
                        GelbooruWindowBreak.StampUnparsable,
                        rawStamp,
                    )
                    val asked = step.atLeastId
                    val count = reading.third
                    val tooWideOnTheLeft = countAsks.filter { it.first < asked }.any { count > it.second }
                    val tooWideOnTheRight = countAsks.filter { it.first > asked }.any { count < it.second }
                    if (tooWideOnTheLeft || tooWideOnTheRight) {
                        return GelbooruWindowHunt.Broken(GelbooruWindowBreak.CountInconsistent)
                    }
                    countAsks += asked to count
                    // 自检：**问出去的位置必须往前走**。读数记的是"观测到的最老那条"而不是问的那枚，
                    // 所以当站方在这一段里条目很稀（或删帖留下大空洞）时，相邻两次问会回**同一条读数**，
                    // 状态机就原地打转直到烧光探测上限 —— 那既慢又看不出问题。这里当场认输。
                    val observed = BoundaryReading(oldestId, day)
                    if (readings.any { it.atLeastId == observed.atLeastId && it.day == observed.day }) {
                        return GelbooruWindowHunt.Broken(
                            GelbooruWindowBreak.SameReadingRepeated,
                            readings = readings.toList(),
                        )
                    }
                    readings += observed
                }
            }
        }
    }
}

/**
 * 站方 `created_at` → **站方自己日历上的那一天**（串上写的那天，不做时区换算）。
 *
 * ⚠️ 这一枚与窗口边界用的 UTC 日切**差站方那侧的偏移**（今天见到的是 `-0500`）。后果是有界的、
 * 也是刻意接受的：窗口**起点最多晚五小时**（那一天的头五小时在站方日历上还属于前一天，被排除在外），
 * 而**绝不会把标签窗口之外的条目摆进来** —— 少了开头几小时看得出（榜少了几张），
 * 多摆出窗口外的图则完全读不出来。要改成严格 UTC 得把偏移烘进判读，而**今天两笔真读数（09-29、10-12）
 * 都是 `-0500`，这一站是否随夏令时换偏移并没有量过** —— 所以这里不押它，只把有界的偏差写明。
 *
 * 今天真实见到的是 **Ruby asctime**：`Mon Oct 12 13:46:29 -0500 2026` —— 星期在前、月缩写、
 * **年在最后**、带时区偏移。这一种逐字解析；另外两种形态（09-29 那批定界探针记过形）留兜：
 * 纯 epoch 秒、ISO-8601（日切取串上那一段）。
 *
 * 2026-10-15 真机第一次跑到探针这一步就断在这里（`_qa/ui-verify-2026-10-15/
 * scr-23-37-gelbooru-year-window-break.jpg`：屏上「Gelbooru 的上传时间读不出日子」），
 * 所以**同一套语法的偏移段放宽成可缺**：`Mon Oct 12 13:46:29 2026`（五段）现在也认。
 * 这不是"放宽成什么像日期都收"—— 位置一枚都不许动：星期、月缩写、日、`时:分:秒`、年
 * 必须**按原序各占一格**，且 `LocalDate.of` 当场验真。原来那两条"认不出给 null"的
 * 用例（`Oct 12 2026` 三截、末尾多一截 `extra`）照旧 null。
 *
 * **认不出的串返回 null**，调用方按"这一笔没读到"处理：拿一个错位半天、一天的日期去定窗口边界，
 * 比定不出来更坏 —— 那会把窗口挪到错误的一天，而屏上完全看不出来。
 */
internal fun parseGelbooruStampDay(raw: String): LocalDate? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    // ① 纯 epoch 秒
    s.toLongOrNull()?.let { return Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate() }
    // ② Ruby asctime：`Mon Oct 12 13:46:29 -0500 2026`（六段、第二段是月缩写、末段是年），
    //    以及**少掉时区偏移**的那一截（五段、末段仍是年）。见上面那段 2026-10-15 的成因。
    val parts = s.split(Regex("\\s+"))
    if (parts.size == 5 || parts.size == 6) {
        val month = monthNumber(parts[1])
        val day = parts[2].toIntOrNull()
        val year = parts.last().toIntOrNull()
        if (month != null && day != null && year != null) {
            runCatching { return LocalDate.of(year, month, day) }
        }
    }
    // ③ ISO-8601 前 10 位就是日切
    if (s.length >= 10) {
        runCatching { return LocalDate.parse(s.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE) }
    }
    return null
}

/**
 * 这一枚**是不是压根没给时间**（空串 / 全空白）。
 *
 * 与"给了但读不出"分开，是因为屏上那两句的出路完全不同：前者是站方这一笔没带 `created_at`
 * （要去查请求参数与 DTO），后者是我们的解析器落后于站方的格式。
 * 2026-10-15 之前这两件事共用一句「上传时间读不出日子」，于是排查的人照着后半句去改解析器。
 */
internal fun isGelbooruStampMissing(raw: String): Boolean = raw.isBlank()

/**
 * 站方那十二枚月缩写 → 月份号。
 *
 * ⚠️ **这张表必须自己写死，不许问 `java.time`**（2026-10-16 真机就是被这一格卡住的）：
 * 原先的实现是 `Month.of(i).getDisplayName(TextStyle.SHORT, Locale.ROOT)` 逐枚比 ——
 * 开发机那台 JVM 说九月是 `Sep`（所以 09-29 与 10-13 两批用例全绿），
 * 而 Android 的 `java.time` 走 ICU 那份 CLDR，九月写 **`Sept`**。
 * 于是屏上「Tue Sep 29 …」这种**站方原样**的串在设备上恒判"读不出日子"，
 * 而周/月/年三档的估点恰好都落在九月那一片 ⇒ 看起来就是"只有 G 站的排行是坏的"。
 * 线上格式不该依赖运行时的本地化数据 —— 这条与"不拿随便一笔请求当校验"是同一族。
 *
 * `sept` 那一格留着：它是 ICU 那侧的写法，站方哪天换库串就会带过来，认下来零成本。
 */
private val MONTHS = mapOf(
    "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
    "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
)

/** 月缩写认前三到四字母（`Sep` / `Sept` 都收），其余一律不认 —— 认不出就交 null，不猜。 */
private fun monthNumber(raw: String): Int? {
    val s = raw.lowercase(Locale.ROOT)
    return MONTHS[s] ?: MONTHS[s.take(3)]
}

