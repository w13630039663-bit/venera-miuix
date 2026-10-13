package com.venera.compose.gallery.domain

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

    /** 认不出的一律 null —— 调用方按"这一笔没读到"处理，不许拿猜出来的日期去定窗口边界。 */
    @Test fun unknownShapesYieldNull() {
        assertNull(parseGelbooruStampDay(""))
        assertNull(parseGelbooruStampDay("   "))
        assertNull(parseGelbooruStampDay("Oct 12 2026"))
        assertNull(parseGelbooruStampDay("Mon Xyz 12 13:46:29 -0500 2026"))
        assertNull(parseGelbooruStampDay("Mon Oct 12 13:46:29 -0500 2026 extra"))
        assertNull(parseGelbooruStampDay("not a date"))
    }

    // ── 估点 ──

    @Test fun estimateBacksOffOneDayPerWeekOfTargetSpanPlusMargin() {
        // 目标 = 今天 → 往回 1 天余量
        assertEquals(maxId - GelbooruWindowBoundary.IDS_PER_DAY, estimateStartId(maxId, today, today))
        // 目标 = 7 天前 → 往回 8 天
        assertEquals(maxId - 8 * GelbooruWindowBoundary.IDS_PER_DAY,
            estimateStartId(maxId, today.minusDays(7), today))
    }

    @Test fun estimateIsClampedIntoTheSitesOwnIdRange() {
        // 年档跨度 365 天 × 7,000 = 255 万，站内还够退；给一枚极小的 maxId 也不能问出 0 或负数
        assertEquals(1L, estimateStartId(50L, today.minusDays(365), today))
        // 目标"在未来"（日期弹层里点到自己站还没有的那几天）也不许倒着问出超过最新 id 的数
        assertEquals(1L, estimateStartId(50L, today.plusDays(9), today))
        assertEquals(1L, estimateStartId(1L, today.minusDays(30), today))
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
        )
        assertTrue(step.toString(), step is BoundaryStep.Ask)
        assertTrue((step as BoundaryStep.Ask).atLeastId > 14_889_216L)
    }

    @Test fun firstCallAsksTheEstimate() {
        val step = gelbooruBoundaryStep(maxId, today.minusDays(7), today, emptyList())
        assertEquals(BoundaryStep.Ask(maxId - 8 * GelbooruWindowBoundary.IDS_PER_DAY), step)
    }

    /** 用今天的四笔真实读数复现一次：目标 09-29 时，下界 14,889,216(09-13) 与上界 14,994,000(09-29) 已括住 ⇒ 问中点。 */
    @Test fun bracketsFromTodaysRealReadingsAskTheMidpoint() {
        val step = gelbooruBoundaryStep(
            maxId = maxId,
            targetDay = LocalDate.of(2026, 9, 29),
            todayUtc = today,
            readings = listOf(
                BoundaryReading(14_868_216L, LocalDate.of(2026, 9, 10)),   // 今天真读到
                BoundaryReading(14_889_216L, LocalDate.of(2026, 9, 13)),   // 今天真读到
                BoundaryReading(14_994_000L, LocalDate.of(2026, 9, 29)),   // 今天真读到
            ),
        )
        assertEquals(BoundaryStep.Ask((14_889_216L + 14_994_000L) / 2L), step)
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
        )
        assertEquals(BoundaryStep.Resolved(lowId + gran), step)
    }

    @Test fun givingUpIsItsOwnOutcomeNotAWholeSiteQuery() {
        // 下界**已经站在最新 id 上**、日期还是没到目标日 ⇒ 这一站再往前没有东西可问，认输，
        // 而不是交回全站那批。（故意给 maxId == 读数那枚：不然"还没问过的最新那条"是合法的一问。）
        val step = gelbooruBoundaryStep(
            maxId = 15_000_000L,
            targetDay = today.plusDays(3),   // 故意要一个未来的窗口
            todayUtc = today,
            readings = listOf(BoundaryReading(15_000_000L, LocalDate.of(2026, 9, 1))),
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
        )
        assertEquals(BoundaryStep.Ask(latest), step)
    }

    @Test fun probeCapStopsTheHunt() {
        val readings = (0 until GelbooruWindowBoundary.MAX_PROBES).map {
            BoundaryReading(14_000_000L + it, LocalDate.of(2026, 9, 29))
        }
        val step = gelbooruBoundaryStep(maxId, LocalDate.of(2026, 10, 1), today, readings)
        assertEquals(GelbooruWindowBreak.ProbeBudget, (step as BoundaryStep.GiveUp).at)
    }

    @Test fun noLatestIdIsAFailureNotAGuess() {
        assertEquals(
            BoundaryStep.GiveUp(GelbooruWindowBreak.LatestIdUnusable, "站方最新 id 拿不到"),
            gelbooruBoundaryStep(0L, today, today, emptyList()),
        )
    }
}
