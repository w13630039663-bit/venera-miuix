package com.venera.compose.gallery.domain

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Gelbooru 时间窗口定界的纯函数层（2026-10-13）。
 *
 * fixture 用的是**今天带凭据现量的读数**（不是编的）：
 * `id:>=14994000 sort:id:asc` ⇒ `id=14994000` / `Tue Sep 29 17:30:32 -0500 2026`，
 * 最新一条 ⇒ `id=15078216` / `Mon Oct 12 13:46:29 -0500 2026`，
 * 站脚那条 ⇒ `id=1` / `Mon Jul 16 00:19:58 -0500 2007`。
 *
 * 这台状态机**不出网**，所以这里钉的是"下一步问哪一枚 id / 什么时候收口 / 什么时候认输"，
 * 而不是站方的答复本身 —— 网络那一半留在 client 里，等桌面判据面能复跑时再接。
 */
class GelbooruWindowBoundaryTest {

    private val maxId = 15_078_216L
    private val today = LocalDate.of(2026, 10, 12)

    // ── created_at 解析 ──

    @Test fun parsesRubyAsctimeSeenOnTheWire() {
        assertEquals(LocalDate.of(2026, 10, 12), parseGelbooruStampDay("Mon Oct 12 13:46:29 -0500 2026"))
        assertEquals(LocalDate.of(2026, 9, 29), parseGelbooruStampDay("Tue Sep 29 17:30:32 -0500 2026"))
        assertEquals(LocalDate.of(2007, 7, 16), parseGelbooruStampDay("Mon Jul 16 00:19:58 -0500 2007"))
        // 十二月与十一月这两枚缩写最容易串（Dec/Nov 只差一个字母）
        assertEquals(LocalDate.of(2024, 12, 1), parseGelbooruStampDay("Sun Dec 01 08:00:00 -0500 2024"))
        assertEquals(LocalDate.of(2024, 11, 1), parseGelbooruStampDay("Fri Nov 01 08:00:00 -0400 2024"))
    }

    @Test fun parsesEpochAndIsoFallbacks() {
        // 0 = 1970-01-01，纯 epoch 秒那一路
        assertEquals(LocalDate.of(1970, 1, 1), parseGelbooruStampDay("0"))
        assertEquals(LocalDate.of(2026, 10, 12), parseGelbooruStampDay("2026-10-12T13:46:29.000-05:00"))
    }

    /**
     * **十二枚月缩写逐枚现喂**（2026-10-16 真机立案）。
     *
     * 那条真读数 `Tue Sep 29 04:45:35 -0500 2026` 在开发机上解析得过、在设备上恒判"读不出日子"：
     * 实现是拿 `Month.getDisplayName(SHORT, ROOT)` 逐枚比，而**开发机 JVM 说 `Sep`、
     * Android 那份 ICU/CLDR 说 `Sept`**。日/周/月/年四档的估点全落在九月那一片，
     * 于是屏上表现成"只有 G 站排行是坏的，而『最新』『全部排行』正常"（用户 00:30 原话）。
     * 所以这里逐枚钉，而不是只钉一两个 —— 少钉一枚就是下一次换月时再复一次发。
     */
    @Test fun everyWireMonthAbbreviationParsesWithoutAskingTheRuntime() {
        val wire = listOf(
            "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
            "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12,
        )
        wire.forEach { (abbr, month) ->
            // 日取 21：二月 29 在 2026 不存在，拿它当"每枚都过"的样例会让**期望值自己抛异常**
            // （第一遍就是这么红的 —— 红得像是解析器坏了，其实是尺子喂了个假日期）。
            val raw = "Tue $abbr 21 04:45:35 -0500 2026"
            assertEquals(raw, LocalDate.of(2026, month, 21), parseGelbooruStampDay(raw))
        }
        // 真机那条原样（就是它把整条线卡住的）
        assertEquals(LocalDate.of(2026, 9, 29), parseGelbooruStampDay("Tue Sep 29 04:45:35 -0500 2026"))
        // ICU 那侧的写法也收：站方哪天换库串，不该由我们决定它哪天坏。
        assertEquals(LocalDate.of(2026, 9, 29), parseGelbooruStampDay("Tue Sept 29 04:45:35 -0500 2026"))
    }

    /**
     * 月份表**不许再问运行时**（负向尺，钉的是上一笔的根因，不是症状）。
     *
     * 只测"十二枚都过"挡不住有人在三个月后把实现改回 `getDisplayName` ——
     * 那一改在开发机上照样全绿，只有设备会坏。所以这一把扫代码行。
     */
    @Test fun monthTableNeverAsksTheRuntimeLocale() {
        val code = RepoSources.kotlinFiles("com/venera/compose/gallery/domain")
            .first { it.name == "GelbooruWindowBoundary.kt" }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
            .joinToString("\n")
        listOf("getDisplayName", "TextStyle", "java.time.Month").forEach { banned ->
            assertTrue("月份表又去问运行时的本地化数据了（$banned）", !code.contains(banned))
        }
        // 对照组：这张尺子咬得动 —— 自己喂一遍被禁的那一行，必须抓得住。
        val selfControl = code + "\nimport java.time.Month\n"
        assertTrue("负向尺自己就是恒真的", selfControl.contains("java.time.Month"))
    }

    /**
     * 同一套 asctime 语法**少掉时区偏移**的那一截也认（2026-10-16 放宽）。
     *
     * ⚠️ 诚实交代：这一形**没有在站方读到过**。立案的是真机第一次带凭据跑到探针那一步就断在
     * 「上传时间读不出日子」（`_qa/ui-verify-2026-10-15/scr-23-37-gelbooru-year-window-break.jpg`），
     * 而当时屏上读不到原文 —— 所以现在同时做了两件事：把原文带上屏（下一次截图就是读数），
     * 以及只放宽"偏移段可缺"这一枚**位置一枚都不许动**的变体。
     * 真要是别的形状，下面那笔"认不出给 null"的用例仍然守着：宁可不发，不猜日期。
     */
    @Test fun parsesAsctimeWithoutZoneOffset() {
        assertEquals(LocalDate.of(2026, 10, 12), parseGelbooruStampDay("Mon Oct 12 13:46:29 2026"))
        assertEquals(LocalDate.of(2007, 7, 16), parseGelbooruStampDay("Mon Jul 16 00:19:58 2007"))
        // 放宽的只是"少一截"，不是"看着像日期"：星期位与月缩写位错了照样 null。
        assertNull(parseGelbooruStampDay("Oct 12 13:46:29 2026"))
        // 日子写歪了（不在 1..31）→ LocalDate.of 当场验假 → null。
        assertNull(parseGelbooruStampDay("Mon Oct 47 13:46:29 2026"))
    }

    /** 认不出的一律 null —— 调用方按"这一笔没读到"处理，不许拿猜出来的日期去定窗口边界。 */
    @Test fun unknownShapesYieldNull() {
        assertNull(parseGelbooruStampDay(""))
        assertNull(parseGelbooruStampDay("   "))
        assertNull(parseGelbooruStampDay("Oct 12 2026"))
        assertNull(parseGelbooruStampDay("Mon Xyz 12 13:46:29 -0500 2026"))
        assertNull(parseGelbooruStampDay("Mon Oct 12 13:46:29 -0500 2026 extra"))
        assertNull(parseGelbooruStampDay("not a date"))
    }

    /**
     * 「没带时间」与「带了读不出」是两件事（2026-10-16 分开）。
     *
     * 从前两枝共用一句「上传时间读不出日子」，于是排查的人第一反应是去改解析器 ——
     * 而空串那一枝要去查的是请求参数与 DTO，解析器怎么改都读不出不存在的东西。
     */
    @Test fun missingStampIsDistinguishedFromUnparsableOne() {
        assertTrue(isGelbooruStampMissing(""))
        assertTrue(isGelbooruStampMissing("   "))
        assertTrue(isGelbooruStampMissing("\t"))
        assertNull(parseGelbooruStampDay("   "))
        // 有内容却读不出 ⇒ 不算"没带"，且原文要能一路带到屏上（调用方读 detail）。
        assertTrue(!isGelbooruStampMissing("Mon Oct 12 13:46:29 -0500 贰零贰陆"))
        assertNull(parseGelbooruStampDay("Mon Oct 12 13:46:29 -0500 贰零贰陆"))
    }

    // ── 估点 ──

    /**
     * 估点照**标定表**插值（2026-10-16 改口径）。
     *
     * 旧写法"最新 id 往回推 (天数+1) × 7,000"只对今天那一段成立：这一站早年约 1 千条/天，
     * 拿 7,000 估 2024 年那一页会一次偏到几百天之外，二分要烧 14 笔（真站逐笔 0.9–2.0 秒）。
     * 烘表之后同一枚目标日 2–5 笔 —— 前后对照 `_qa/gb_tr4.txt`。
     */
    @Test fun estimateInterpolatesBetweenCalibrationAnchors() {
        // 2026-10-05 落在"2026-01-01 = 13,223,080"与活锚"今天 = 最新 id"那一段里：
        //   13,223,080 + (15,078,216 − 13,223,080) × 277 / 284 = 15,032,490
        assertEquals(15_032_490L, estimateStartId(maxId, today.minusDays(7), today))
        // 表上没有"今天之后"那一段 ⇒ 目标日就是今天（「按天」那一档）时往左让开**一天的量**再开问：
        // 贴着最新一条开问会退化成"从今天一步步往回挪"（真站实测 9 笔），让开一天 5 笔收口。
        assertEquals(maxId - GelbooruWindowBoundary.IDS_PER_DAY, estimateStartId(maxId, today, today))
        assertEquals(maxId - GelbooruWindowBoundary.IDS_PER_DAY, estimateStartId(maxId, today.plusDays(9), today))
        // 表最老一行（2008-01-01）之前：站方最老的那条（id=1，2026-10-16 现量 `Mon Jul 16 2007`）就是下界。
        assertEquals(1L, estimateStartId(maxId, LocalDate.of(2007, 1, 1), today))
    }

    /** 跳步用的日速取**表上那一段**的斜率，不是全局常数 —— 这一站前后差五倍。 */
    @Test fun rateComesFromTheTablesOwnSegment() {
        // 2026 那一段：(15,078,216 − 13,223,080) / 284 天 = 6,532
        assertEquals(6_532L, interpolatedRate(maxId, today.minusDays(7), today))
        // 2024 那一段：(11,229,956 − 9,426,385) / 366 天 = 4,927（整数除取整）—— 拿 7,000 跳这一页会一次跳过两百多天
        assertEquals(4_927L, interpolatedRate(maxId, LocalDate.of(2024, 7, 1), today))
    }

    /**
     * 估点永远落在 `[1, maxId]` 里（问 0 那枚、或问比最新还新那枚，站方给的都是没条目的位置）。
     *
     * 这一把从前钉的是"往回推到负数就夹到 1"那两枚具体数 —— 换成表插值之后夹取的位置会变，
     * 但要守的东西没变：**问出去的编号必须是这一站问得出的**。所以改成逐组合验区间。
     */
    @Test fun estimateIsClampedIntoTheSitesOwnIdRange() {
        listOf(50L, 1L, 9_999L, maxId).forEach { top ->
            listOf(
                today, today.plusDays(9), today.minusDays(7), today.minusDays(365),
                today.minusDays(3_000), LocalDate.of(2007, 1, 1),
            ).forEach { day ->
                val asked = estimateStartId(top, day, today)
                assertTrue("top=$top day=$day -> $asked", asked in 1L..top)
            }
        }
    }

    // ── 状态机：判式方向 ──

    /**
     * 这条是整个定界的命门：**"读到的日期 ≥ 目标日"是上界**（起点在它或更左），
     * "日期 < 目标日"才是下界。写反了会往错误方向收敛，最后交出一个"窗口起点在目标日之后"
     * 的 id —— 屏上表现就是"周榜少了开头一两天"，而一条读数都看不出毛病。
     */
    @Test fun readingOnOrAfterTargetDayIsTheUpperBound() {
        val target = LocalDate.of(2026, 9, 29)
        val step = gelbooruBoundaryStep(
            maxId = maxId, targetDay = target, todayUtc = today,
            readings = listOf(BoundaryReading(14_994_000L, target)),  // 正好落在目标日
            BoundarySide.WINDOW_START,
        )
        // 只有上界 ⇒ 往更小的 id 退
        assertTrue(step.toString(), step is BoundaryStep.Ask)
        assertTrue((step as BoundaryStep.Ask).atLeastId < 14_994_000L)
    }

    @Test fun readingBeforeTargetDayIsTheLowerBound() {
        val target = LocalDate.of(2026, 9, 29)
        val step = gelbooruBoundaryStep(
            maxId = maxId, targetDay = target, todayUtc = today,
            readings = listOf(BoundaryReading(14_889_216L, LocalDate.of(2026, 9, 13))),
            BoundarySide.WINDOW_START,
        )
        assertTrue(step.toString(), step is BoundaryStep.Ask)
        assertTrue((step as BoundaryStep.Ask).atLeastId > 14_889_216L)
    }

    @Test fun firstCallAsksTheEstimate() {
        // 表插值 2026-10-05 那枚（算式见 estimateInterpolatesBetweenCalibrationAnchors）
        val step = gelbooruBoundaryStep(maxId, today.minusDays(7), today, emptyList(), BoundarySide.WINDOW_START)
        assertEquals(BoundaryStep.Ask(15_032_490L), step)
    }

    /**
     * 用今天的四笔真实读数复现一次：目标 09-29 时下界 14,889,216(09-13) 与上界 14,994,000(09-29) 已括住
     * ⇒ 问的不是中点，而是**从最近那条仍在目标日之下的观测、按当段日速插过去**那一枚。
     *
     * 14,889,216 + 6,532 × 16 天 = 14,993,728 —— 而中点(14,941,608)落在 09-21，等于白问一笔。
     * 2026-10-16 真站逐档量过这个次序（`_qa/gb_tr4.txt`：一年半前那一档的上界 9 笔 → 6 笔）。
     */
    @Test fun bracketsAskTheSecantFromTheNearestKnownLowerReading() {
        val step = gelbooruBoundaryStep(
            maxId = maxId,
            targetDay = LocalDate.of(2026, 9, 29),
            todayUtc = today,
            readings = listOf(
                BoundaryReading(14_868_216L, LocalDate.of(2026, 9, 10)),   // 今天真读到
                BoundaryReading(14_889_216L, LocalDate.of(2026, 9, 13)),   // 今天真读到
                BoundaryReading(14_994_000L, LocalDate.of(2026, 9, 29)),   // 今天真读到
            ),
            BoundarySide.WINDOW_START,
        )
        assertEquals(BoundaryStep.Ask(14_993_728L), step)
    }

    /**
     * 三枚候选（当段插值 / 表上绝对位 / 上界往回一天的量）**全不在区间里**才退回取中点。
     *
     * 这一档不能省：候选越界时被夹成 `low+1` 的话，状态机会退化成"一次挪一个 id"直到烧光上限
     * —— 真站台架第一次就是这么废了 18 笔的（同一天量出来的读数，见那颗文件头的 ⚠️）。
     */
    @Test fun bracketsFallBackToTheMidpointWhenNoCandidateIsInside() {
        val step = gelbooruBoundaryStep(
            maxId = maxId,
            targetDay = LocalDate.of(2026, 9, 29),
            todayUtc = today,
            readings = listOf(
                BoundaryReading(14_990_000L, LocalDate.of(2026, 9, 25)),
                BoundaryReading(14_993_000L, LocalDate.of(2026, 10, 5)),
            ),
            BoundarySide.WINDOW_START,
        )
        assertEquals(BoundaryStep.Ask(14_991_500L), step)
    }

    @Test fun convergedBracketResolvesToTheUpperBound() {
        val gran = GelbooruWindowBoundary.CONVERGENCE_GRANULARITY
        val lowId = 14_900_000L
        val step = gelbooruBoundaryStep(
            maxId = maxId,
            targetDay = LocalDate.of(2026, 9, 29),
            todayUtc = today,
            readings = listOf(
                BoundaryReading(lowId, LocalDate.of(2026, 9, 28)),
                BoundaryReading(lowId + gran, LocalDate.of(2026, 9, 29)),
            ),
            BoundarySide.WINDOW_START,
        )
        assertEquals(BoundaryStep.Resolved(lowId + gran), step)
    }

    /**
     * 同一对读数、同一个目标日，**窗口的哪一头**决定交哪一枚 —— 而这两头不对称。
     *
     * 起点交 `high`（已在这一期里的第一条）：最坏少摆开头几小时，少了看得出。
     * 上界交 `low + 1`（还没出这一期的最后条之后）：同样只会少，**绝不会把下一期的图摆进这一期**。
     * 这一条是 2026-10-16 加闭环窗口时定的：上界那枚如果照起点那样交 `high`，
     * 「2024 年 3 月的高分榜」就会混进 4 月头几小时的分 —— 屏上没有任何地方读得出来。
     */
    @Test fun theTwoEndsOfTheWindowSettleOnOppositeSides() {
        val gran = GelbooruWindowBoundary.CONVERGENCE_GRANULARITY
        val lowId = 11_700_000L
        val dayAfter = LocalDate.of(2025, 5, 1)
        val readings = listOf(
            BoundaryReading(lowId, dayAfter.minusDays(1)),
            BoundaryReading(lowId + gran, dayAfter),
        )
        assertEquals(
            "上界必须往安全那一侧收口",
            BoundaryStep.Resolved(lowId + 1L),
            gelbooruBoundaryStep(maxId, dayAfter, today, readings, BoundarySide.WINDOW_END),
        )
        assertEquals(
            BoundaryStep.Resolved(lowId + gran),
            gelbooruBoundaryStep(maxId, dayAfter, today, readings, BoundarySide.WINDOW_START),
        )
    }

    @Test fun givingUpIsItsOwnOutcomeNotAWholeSiteQuery() {
        // 下界**已经站在最新 id 上**、日期还是没到目标日 ⇒ 这一站再往前没有东西可问，认输，
        // 而不是交回全站那批。（故意给 maxId == 读数那枚：不然"还没问过的最新那条"是合法的一问。）
        val step = gelbooruBoundaryStep(
            maxId = 15_000_000L,
            targetDay = today.plusDays(3),   // 故意要一个未来的窗口
            todayUtc = today,
            readings = listOf(BoundaryReading(15_000_000L, LocalDate.of(2026, 9, 1))),
            BoundarySide.WINDOW_START,
        )
        assertEquals(
            GelbooruWindowBreak.TopReachedWithoutBracket,
            (step as BoundaryStep.GiveUp).at,
        )
    }

    /**
     * 「按天」那一档的目标日就是今天，估点天然贴着最新 id ⇒ 往前一跳必然越界。
     * 这时**先问最新那一条**，别直接认输 —— 否则这一档永远定不出来，屏幕上就是一颗按不动的行。
     */
    @Test fun jumpingPastTheLatestIdAsksTheLatestIdInsteadOfGivingUp() {
        val latest = 15_078_216L
        val step = gelbooruBoundaryStep(
            maxId = latest,
            targetDay = today,
            todayUtc = today,
            readings = listOf(BoundaryReading(latest - GelbooruWindowBoundary.IDS_PER_DAY, today.minusDays(1))),
            BoundarySide.WINDOW_START,
        )
        assertEquals(BoundaryStep.Ask(latest), step)
    }

    @Test fun probeCapStopsTheHunt() {
        val readings = (0 until GelbooruWindowBoundary.MAX_PROBES).map {
            BoundaryReading(14_000_000L + it, LocalDate.of(2026, 9, 29))
        }
        val step = gelbooruBoundaryStep(maxId, LocalDate.of(2026, 10, 1), today, readings, BoundarySide.WINDOW_START)
        assertEquals(GelbooruWindowBreak.ProbeBudget, (step as BoundaryStep.GiveUp).at)
    }

    @Test fun noLatestIdIsAFailureNotAGuess() {
        assertEquals(
            BoundaryStep.GiveUp(GelbooruWindowBreak.LatestIdUnusable, "站方最新 id 拿不到"),
            gelbooruBoundaryStep(0L, today, today, emptyList(), BoundarySide.WINDOW_START),
        )
    }
}
