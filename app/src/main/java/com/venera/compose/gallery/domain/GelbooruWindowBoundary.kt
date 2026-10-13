package com.venera.compose.gallery.domain

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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
 * ── 当年为什么判"月/年收不了口" ──
 *
 * 那一批的日速常数是 **1.2 万条/天**，估点因此比真位置偏后近一倍 —— 退让步数与二分深度全被撑大，
 * 24 次预算就是这么烧光的。今天的现量约 **6.5k–8.4k 条/天**（独立两路：13 天跨 84,094 条 ⇒ 6,469/天；
 * 月档 `id:>=` 的 `count=194,606` ÷ 30 ⇒ 6,487/天），取 **7,000**。
 * 按这枚常数跑今天的真实读数：**周档 2 笔、月档 4 笔**就收口（这台台架在 `_qa` 外的临时脚本上量出来的）。
 *
 * 单调性也量过了（追加式 id，不是哈希序）：
 * id 15,078,094→2026-10-12、14,994,000→09-29、14,718,000→08-17、14,358,000→06-24，四点全单调。
 */
internal object GelbooruWindowBoundary {

    /**
     * 日速估计（条/天）。**只用于估点**：错了由日期差修正与二分兜，不参与最终判读
     * （最终起点完全由读到的日期决定）。2026-10-13 现量取 7,000。
     */
    const val IDS_PER_DAY = 7_000L

    /**
     * 收口粒度：区间宽度降到这里就认"够准了"。1/8 天 ≈ 875 条 ≈ 站方三来小时的上传量 ——
     * 窗口起点最多差这三小时，而再往下切是"每砍一半多一笔请求"，换不来肉眼差别。
     */
    const val CONVERGENCE_GRANULARITY = IDS_PER_DAY / 8

    /** 探测上限。年档实测形状：估点 + 两三笔日期差修正 + 二分 log2(2.5e6/875) ≈ 12 笔，留余量。 */
    const val MAX_PROBES = 18
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

/** 一次定界的结论：要么给出起点，要么说清断在哪一步。 */
internal sealed interface GelbooruWindowHunt {
    val startId: Long?

    data class Found(override val startId: Long) : GelbooruWindowHunt

    data class Broken(
        val at: GelbooruWindowBreak,
        val detail: String? = null,
    ) : GelbooruWindowHunt {
        override val startId: Long? = null
    }
}

internal sealed interface BoundaryStep {
    /** 下一步去问这一枚 id（`tags=id:>=it sort:id:asc&limit=1`）。 */
    data class Ask(val atLeastId: Long) : BoundaryStep

    /** 定出来了：窗口起点 = 这一枚（含）。 */
    data class Resolved(val startId: Long) : BoundaryStep

    /** 定不出来。⚠️ 调用方必须把它当"**这一档不发**"处理，不许原样交回全站那批 ——
     *  那正是"按了周榜、屏上却是全站高分"那种假开关的形状。
     *  [at] 说清是哪一步断的（屏上那一句要能指着它说话），[reason] 是给自己人看的整句。 */
    data class GiveUp(val at: GelbooruWindowBreak, val reason: String) : BoundaryStep
}

/**
 * 定界状态机：给定站方最新 id、窗口第一天、以及**到当前为止的读数**，返回下一步。
 *
 * 记 `f(M)` = 读数里"比 M 老不了多少的第一条"的日期（随 M 不减）。要找的起点是
 * **第一个日期 ≥ [targetDay] 的 id**，所以：
 * - `f(M) < targetDay` ⇒ M 还在目标日之前 ⇒ 它是**下界**（起点在 M 之后）；
 * - `f(M) ≥ targetDay` ⇒ M 已进入或越过目标日 ⇒ 它是**上界**（起点 ≤ M）。
 * 一上一下括住、宽度收到 [GelbooruWindowBoundary.CONVERGENCE_GRANULARITY] 就交上界那枚。
 *
 * @param maxId 站方最新 id（一笔 `limit=1` 不带 tags 取的）
 * @param todayUtc 只用来算估点，不参与判读
 */
internal fun gelbooruBoundaryStep(
    maxId: Long,
    targetDay: LocalDate,
    todayUtc: LocalDate,
    readings: List<BoundaryReading>,
): BoundaryStep {
    val gran = GelbooruWindowBoundary.CONVERGENCE_GRANULARITY
    val rate = GelbooruWindowBoundary.IDS_PER_DAY
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
        // 括住了：收口够就交上界，否则取中点再问一笔。
        low != null && high != null -> {
            val mid = (low.atLeastId + high.atLeastId) / 2L
            if (high.atLeastId - low.atLeastId <= gran || mid <= low.atLeastId) {
                BoundaryStep.Resolved(high.atLeastId)
            } else {
                BoundaryStep.Ask(mid)
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

/** 估点：最新 id 往回推「目标日到今天的天数 + 1 天余量」，夹在 `[1, maxId]` 里。 */
internal fun estimateStartId(maxId: Long, targetDay: LocalDate, todayUtc: LocalDate): Long {
    val days = ChronoUnit.DAYS.between(targetDay, todayUtc).coerceAtLeast(0L)
    val back = (days + 1L) * GelbooruWindowBoundary.IDS_PER_DAY
    return (maxId - back).coerceIn(1L, maxId)
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
 * - **失败不缓存**（凭据掉了、站方抽风都是会好的事），成功才缓存，按目标日一把 ——
 *   同一天的同一档天然同键，不需要 TTL。
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

    private val cached = mutableMapOf<LocalDate, Long>()

    /** [hunt] 只取起点的那一面 —— 四档收口笔数那几条判据用它，不必跟着结论换形。 */
    suspend fun startIdFor(targetDay: LocalDate, todayUtc: LocalDate): Long? =
        hunt(targetDay, todayUtc).startId

    /**
     * 开一次定界。断在哪一步由 [GelbooruWindowHunt.Broken.at] 说，站方的原话在 `.detail`。
     *
     * 成功才缓存（失败存一次就变永久不发），按目标日一把。
     */
    suspend fun hunt(targetDay: LocalDate, todayUtc: LocalDate): GelbooruWindowHunt {
        cached[targetDay]?.let { return GelbooruWindowHunt.Found(it) }
        val topProbe = latestId() ?: return GelbooruWindowHunt.Broken(GelbooruWindowBreak.ProbeNotWired)
        val top = topProbe.getOrElse {
            return GelbooruWindowHunt.Broken(
                GelbooruWindowBreak.LatestIdRejected,
                topProbe.exceptionOrNull()?.message,
            )
        }
        if (top <= 0L) return GelbooruWindowHunt.Broken(GelbooruWindowBreak.LatestIdUnusable)
        val readings = mutableListOf<BoundaryReading>()
        // 问出去的每一枚 id 与它当时的命中总数。自检**按位置比**：命中总数应随"问的 id"增大而不增。
        // 二分回退时会问比上一笔更靠左的 id，所以不能拿"这一笔 vs 上一笔"当尺子（那会把正常收口误杀）。
        val countAsks = mutableListOf<Pair<Long, Long>>()
        while (true) {
            when (val step = gelbooruBoundaryStep(top, targetDay, todayUtc, readings.toList())) {
                is BoundaryStep.Resolved -> {
                    cached[targetDay] = step.startId
                    return GelbooruWindowHunt.Found(step.startId)
                }
                is BoundaryStep.GiveUp -> return GelbooruWindowHunt.Broken(step.at, step.reason)
                is BoundaryStep.Ask -> {
                    // 自检：同一枚位置问第二遍 = 状态机没往前走。
                    if (countAsks.any { it.first == step.atLeastId }) {
                        return GelbooruWindowHunt.Broken(GelbooruWindowBreak.SameReadingRepeated)
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
                    val day = parseGelbooruStampDay(reading.second) ?: return GelbooruWindowHunt.Broken(
                        GelbooruWindowBreak.StampUnparsable,
                        reading.second,
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
                        return GelbooruWindowHunt.Broken(GelbooruWindowBreak.SameReadingRepeated)
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
 * **认不出的串返回 null**，调用方按"这一笔没读到"处理：拿一个错位半天、一天的日期去定窗口边界，
 * 比定不出来更坏 —— 那会把窗口挪到错误的一天，而屏上完全看不出来。
 */
internal fun parseGelbooruStampDay(raw: String): LocalDate? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    // ① 纯 epoch 秒
    s.toLongOrNull()?.let { return Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate() }
    // ② Ruby asctime：`Mon Oct 12 13:46:29 -0500 2026`（六段、第二段是月缩写、末段是年）
    val parts = s.split(Regex("\\s+"))
    if (parts.size == 6) {
        val month = monthNumber(parts[1])
        val day = parts[2].toIntOrNull()
        val year = parts[5].toIntOrNull()
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

private fun monthNumber(raw: String): Int? {
    val want = raw.take(3).lowercase(Locale.ROOT)
    return (1..12).firstOrNull {
        Month.of(it).getDisplayName(TextStyle.SHORT, Locale.ROOT).lowercase(Locale.ROOT) == want
    }
}
