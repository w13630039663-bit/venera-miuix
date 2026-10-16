package com.venera.compose.gallery.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * [GelbooruWindowFinder] 的图证（2026-10-13）—— 移动端接线这一批**唯一**能证明那台循环真会收口的东西。
 *
 * 桩子是一张贴着今天真实锚点的**稠密理想站**：最新 id 15,078,216 = 2026-10-12，日速取
 * [GelbooruWindowBoundary.IDS_PER_DAY]（今天的现量 6.5k–8.4k 的中间值）。注意模型与真站差一天：
 * 真实读数 `id:>=14994000` 是 09-29，模型按 7,000/天 算出 09-30 —— 这正是**估点不参与判读**的理由：
 * 最终答案只由"读到的日期"决定，常数错了由修正步与二分兜住。所以这里钉的是
 * **落在哪一天、花几笔、什么时候认输**，不钉那一枚 id 的数值。
 *
 * 认输的出口各占一枚 [GelbooruWindowBreak]（探测通道没接上、凭据被拒、日期换了形状、`id:>=` 被静默忽略、
 * 删帖留下大空洞）：每一种都必须交回 [GelbooruWindowHunt.Broken] 让调用方**不发这一腿**，
 * 而不是交回全站那批 —— 而屏上那句缺席原因要指得出是哪一步（见 `GelbooruWindowHunt.Broken.detail`）。
 */
class GelbooruWindowFinderTest {

    private val latest = 15_078_216L
    private val latestDay = LocalDate.of(2026, 10, 12)
    private val rate = GelbooruWindowBoundary.IDS_PER_DAY
    private val gran = GelbooruWindowBoundary.CONVERGENCE_GRANULARITY

    /** 稠密站的日期函数：往回每满一天降一枚 id 档。 */
    private fun dayOf(id: Long): LocalDate = latestDay.minusDays((latest - id) / rate)

    /** 站方那一枚 Ruby asctime 串（解析器只认月/日/年三段，星期与偏移照抄今天真见到的形状）。 */
    private fun stampOf(day: LocalDate): String =
        day.atTime(13, 46, 29).format(DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss '-0500' yyyy", Locale.ROOT))

    private val countAt = { id: Long -> latest - id + 1 }

    /** 出过网的每一枚问 —— 用来数探测笔数。 */
    private fun denseSite(probes: MutableList<Long>) = GelbooruWindowFinder(
        latestId = { Result.success(latest) },
        probeAtLeast = { m ->
            probes += m
            // 稠密站里"比 m 老不了多少的第一条"就是 m 自己
            Result.success(Triple(m, stampOf(dayOf(m)), countAt(m)))
        },
    )

    // ── 收口：四档都要能在预算内落到**目标那一天** ──
    //
    // 那四枚 `assertEquals(探测笔数)` 是**这台桩子上量出来的**（不是预算，也不是手推）：
    // 天 6 / 周 6 / 月 8 / 年 5（2026-10-16 换成标定表估点之后的桩子读数，改前的读数是 5/6/6/6）。
    // 钉成等式而不是上界，是因为"多问一笔"在这台状态机里只可能是估点或收口粒度被改坏了。
    //
    // ⚠️ **这台桩子的笔数不是产品口径的笔数**：桩子恒 7,000 条/天，而烘进估点的那张表上 2026 那一段
    // 平均 6,532 条/天 —— 斜率差 7%，月档因此在桩子上多花两笔，而真站上同一档是 7 笔 → 4 笔。
    // 差出来的那两笔正是"估点错了也收得了口"这条稳健性的代价，也是这里**故意保留**不一致的原因
    // （见上一段 KDoc：估点从不参与判读）。真站各档的笔数看 `_qa/gb_tr4.txt`。

    @Test fun weekWindowResolvesOntoTheTargetDay() = runBlocking {
        val probes = mutableListOf<Long>()
        val target = latestDay.minusDays(7)
        val start = denseSite(probes).startIdFor(target, latestDay)
        assertNotNull(start)
        assertEquals(target, dayOf(start!!))
        assertEquals("$probes", 6, probes.size)
    }

    @Test fun monthWindowResolvesOntoTheTargetDay() = runBlocking {
        val probes = mutableListOf<Long>()
        val target = latestDay.withDayOfMonth(1)
        val start = denseSite(probes).startIdFor(target, latestDay)
        assertNotNull(start)
        assertEquals(target, dayOf(start!!))
        assertEquals("$probes", 8, probes.size)
    }

    /**
     * 年档就是 09-29 那一批判"收不了口"的那一档。常数从 1.2 万/天 改成现量 7,000/天 之后 6 笔收口；
     * 2026-10-16 换成标定表估点之后是 **5 笔**（表上 2026-01-01 那一枚就是这一档的目标日，估点直接落定）。
     */
    @Test fun yearWindowResolvesInsideTheProbeCap() = runBlocking {
        val probes = mutableListOf<Long>()
        val target = LocalDate.of(2026, 1, 1)
        val start = denseSite(probes).startIdFor(target, latestDay)
        assertNotNull(start)
        assertEquals(target, dayOf(start!!))
        assertEquals("$probes", 5, probes.size)
    }

    /**
     * 「按天」那一档最容易做成假开关：目标日就是今天，估点天然贴着最新 id，
     * 往前一跳必然越界 —— 早先那版在这里直接认输，于是这一档**永远**不发。
     *
     * 表上没有"今天之后"那一段，所以这一档的估点走"往左让开一天的量"那一支
     * （`interpolatedId` 里那一条，真站实测：贴着最新开问要 9 笔，让开一天 5–6 笔）。
     */
    @Test fun dayWindowResolvesEvenThoughTheEstimateStartsAtTheTop() = runBlocking {
        val probes = mutableListOf<Long>()
        val start = denseSite(probes).startIdFor(latestDay, latestDay)
        assertNotNull(start)
        assertEquals(latestDay, dayOf(start!!))
        assertEquals("$probes", 6, probes.size)
        // 起点必须落在"今天"这一天的条目段里，而不是今天之后（那会少摆今天）或以前（那会多摆昨天）
        assertTrue(start.toString(), start <= latest)
    }

    /** 收口交的是**上界**那一枚：窗口起点含当天，所以不许比目标日的头一条更靠后一整天。 */
    @Test fun resolvedStartNeverSkipsPastTheTargetDay() = runBlocking {
        listOf(1L, 7L, 30L, 120L).forEach { back ->
            val probes = mutableListOf<Long>()
            val target = latestDay.minusDays(back)
            val start = denseSite(probes).startIdFor(target, latestDay)
            assertNotNull("$target", start)
            val firstIdOfTargetDay = latest - (back + 1L) * rate + 1L
            assertTrue("$target -> $start", start!! >= firstIdOfTargetDay)
            assertEquals(target, dayOf(start))
        }
    }

    // ── 闭环窗口（翻历史期：起、止两枚边界，2026-10-16）──

    /**
     * 两枚都定出来时交回 `Before(上界)`，而且上界一定**大于**起点。
     *
     * 反了就是一个空窗口（`id:>=A id:<B` 且 B ≤ A ⇒ 站方回 0 条，而屏上写着"这一期没有图"）——
     * 那种 0 条读起来像站方没货，其实是我们把两枚边贴倒了。
     */
    @Test fun closedWindowResolvesBothBoundaries() = runBlocking {
        val probes = mutableListOf<Long>()
        val firstDay = latestDay.minusDays(40)
        val dayAfter = latestDay.minusDays(9)
        val ready = denseSite(probes).huntWindowBounds(firstDay, dayAfter, latestDay)
            as GelbooruWindowBounds.Ready
        assertTrue("$ready", ready.startId in 1L..latest)
        val end = ready.end as GelbooruWindowEnd.Before
        assertTrue("上界 ${end.exclusiveId} 必须大于起点 ${ready.startId}", end.exclusiveId > ready.startId)
        assertEquals(firstDay, dayOf(ready.startId))
        // 收口交的是 `low + 1`（安全那一侧）：被包进来的最后一批**必须仍在这一期之内**。
        // 反过来交 `high` 就会把下一期的开头摆进这一期的榜里 —— 屏上读不出异常，而胶囊写着期次。
        assertTrue(end.toString(), dayOf(end.exclusiveId - 1L).isBefore(dayAfter))
    }

    /** 本期（没有第二枚目标日）只探一枚，交回 Unbounded —— 那一串与这功能落地前**逐字同形**。 */
    @Test fun currentPeriodHuntsOnlyTheLowerBound() = runBlocking {
        val pair = mutableListOf<Long>()
        val ready = denseSite(pair).huntWindowBounds(latestDay.minusDays(7), null, latestDay)
            as GelbooruWindowBounds.Ready
        assertEquals(GelbooruWindowEnd.Unbounded, ready.end)
        val solo = mutableListOf<Long>()
        denseSite(solo).startIdFor(latestDay.minusDays(7), latestDay)
        assertEquals("$pair / $solo", solo.size, pair.size)
    }

    /**
     * 上界断在"问到站方最新一条、它仍在这期之内"= **证到了**天然收到最新 ⇒ 不发才是错的。
     *
     * 这一档与 `currentPeriodHunt` 交回同一串（`id:>=A sort:score:desc`）：既然全站没有一条比这一期更晚的图，
     * 不带上界就是精确的那一串。把它判成"没探成 ⇒ 不发"会做出一个按不动的历史期入口。
     */
    @Test fun upperBoundBeyondTheNewestPostMeansOpenEndedNotBlocked() = runBlocking {
        val ready = denseSite(mutableListOf()).huntWindowBounds(
            firstDay = latestDay.minusDays(7),
            dayAfterWindow = latestDay.plusDays(1),
            todayUtc = latestDay,
        ) as GelbooruWindowBounds.Ready
        assertEquals(GelbooruWindowEnd.Unbounded, ready.end)
    }

    /** 上界断在**别处**（这里造的是探针被拒）⇒ 整对判 Blocked，起点定成功了也不发。 */
    @Test fun blockedPairIsNotReadyEvenWhenTheLowerBoundResolved() = runBlocking {
        // 先量一遍起点要用几笔，第二趟就从那一笔之后开始炸 —— 保证炸的是第二枚边界
        val warm = mutableListOf<Long>()
        denseSite(warm).startIdFor(latestDay.minusDays(7), latestDay)
        val used = warm.size
        val probes = mutableListOf<Long>()
        val finder = GelbooruWindowFinder(
            latestId = { Result.success(latest) },
            probeAtLeast = { m ->
                probes += m
                if (probes.size > used) Result.failure(IOException("401 没接受这组凭据"))
                else Result.success(Triple(m, stampOf(dayOf(m)), countAt(m)))
            },
        )
        val blocked = finder.huntWindowBounds(latestDay.minusDays(7), latestDay.minusDays(1), latestDay)
            as GelbooruWindowBounds.Blocked
        assertEquals(GelbooruWindowBreak.ProbeRejected, blocked.at)
        assertEquals("401 没接受这组凭据", blocked.detail)
    }

    /**
     * 第二枚吃第一枚的读数：这一对的探测上限是**共用**的（起点烧光了就不问上界）。
     *
     * ⚠️ 这里刻意**不**钉"共享读数比各探各的少问笔"：真站上确实省（2026-10-16 `_qa/gb_range_hunt_ledger.txt`：
     * 2024-04-01 那一枚独立探 14 笔、带着另一枚的读数只问 8 笔），但这台稠密桩量不出来 ——
     * 桩子的日↔id 映射是均匀的，两枚边界的估点各自都已经落在位子上，共享读数不省笔（15 vs 15，量过）。
     * 桩子上量不出来的东西不许当判据，所以这一格钉的是**上限共担**这一条真性质。
     */
    @Test fun thePairSharesOneProbeBudget() = runBlocking {
        val firstDay = latestDay.minusDays(40)
        val dayAfter = latestDay.minusDays(9)
        val pair = mutableListOf<Long>()
        val bounded = denseSite(pair).huntWindowBounds(firstDay, dayAfter, latestDay)
        val solo = mutableListOf<Long>()
        val alone = denseSite(solo)
        alone.startIdFor(firstDay, latestDay)
        alone.startIdFor(dayAfter, latestDay)
        assertTrue(
            "成对那一趟问出去 ${pair.size} 笔，超过共用上限 ${GelbooruWindowBoundary.MAX_PROBES} 了",
            pair.size <= GelbooruWindowBoundary.MAX_PROBES,
        )
        assertTrue(bounded.toString(), solo.size <= GelbooruWindowBoundary.MAX_PROBES * 2)
    }

    // ── 缓存：成功才存，失败不存 ──

    @Test fun theSameTargetDayIsServedFromCache() = runBlocking {
        val probes = mutableListOf<Long>()
        val finder = denseSite(probes)
        val target = latestDay.minusDays(7)
        val first = finder.startIdFor(target, latestDay)
        val used = probes.size
        assertEquals(first, finder.startIdFor(target, latestDay))
        assertEquals(used, probes.size)
    }

    @Test fun aDifferentTargetDayDoesNotReuseAnotherDaysBoundary() = runBlocking {
        val probes = mutableListOf<Long>()
        val finder = denseSite(probes)
        finder.startIdFor(latestDay.minusDays(7), latestDay)
        val used = probes.size
        assertNotNull(finder.startIdFor(latestDay.minusDays(30), latestDay))
        assertTrue(probes.size > used)
    }

    /** 失败**不许**进缓存：凭据掉了、站方抽风都是会好的事，存一次就变成永久不发。 */
    @Test fun aFailedHuntIsNotCached() = runBlocking {
        val probes = mutableListOf<Long>()
        var reachable = false
        val finder = GelbooruWindowFinder(
            latestId = { Result.success(latest) },
            probeAtLeast = { m ->
                if (!reachable) {
                    Result.failure(IOException("Gelbooru 没接受这组凭据"))
                } else {
                    probes += m
                    Result.success(Triple(m, stampOf(dayOf(m)), countAt(m)))
                }
            },
        )
        val target = latestDay.minusDays(7)
        val broken = finder.hunt(BoundarySide.WINDOW_START, target, latestDay, emptyList())
        assertTrue(broken.toString(), broken is GelbooruWindowHunt.Broken)
        // 断在哪一步、站方怎么说的，两句都要留在结论里 —— 屏上那句"没探成"就是靠它才分得开
        // "去设置里补凭据"和"等站方缓过来"（2026-10-13 真机这一笔当时只有前一种被想到过）。
        assertEquals(GelbooruWindowBreak.ProbeRejected, (broken as GelbooruWindowHunt.Broken).at)
        assertEquals("Gelbooru 没接受这组凭据", broken.detail)
        assertNull(broken.boundaryId)
        assertEquals(0, probes.size)
        reachable = true
        assertNotNull(finder.startIdFor(target, latestDay))
        assertTrue(probes.isNotEmpty())
    }

    // ── 认输的出口：每一枚各占一步，屏上要说得出是哪一步 ──

    /**
     * 站方这一笔**没带** `created_at` ⇒ `StampMissing`，且不与"读了却认不出"混成一枝。
     *
     * 2026-10-16 分开的：两枝从前共用一句「上传时间读不出日子」，而那句话把人往解析器上带，
     * 空串这一枝真正该查的是请求参数与 DTO。
     */
    @Test fun blankStampBreaksAsMissingNotUnparsable() = runBlocking {
        val finder = GelbooruWindowFinder(
            latestId = { Result.success(latest) },
            probeAtLeast = { m -> Result.success(Triple(m, "   ", countAt(m))) },
        )
        val broken = finder.hunt(BoundarySide.WINDOW_START, latestDay.minusDays(7), latestDay, emptyList()) as GelbooruWindowHunt.Broken
        assertEquals(GelbooruWindowBreak.StampMissing, broken.at)
        // 没带就是没带，detail 不编一份"看起来像原文"的东西。
        assertNull(broken.detail)
    }

    /** 带了、可读不出 ⇒ `StampUnparsable`，**原文留在 detail** 里（屏上那一句要能把它念出来）。 */
    @Test fun unparsableStampKeepsTheRawTextForTheScreen() = runBlocking {
        val raw = "1759988789 UTC"
        val finder = GelbooruWindowFinder(
            latestId = { Result.success(latest) },
            probeAtLeast = { m -> Result.success(Triple(m, raw, countAt(m))) },
        )
        val broken = finder.hunt(BoundarySide.WINDOW_START, latestDay.minusDays(7), latestDay, emptyList()) as GelbooruWindowHunt.Broken
        assertEquals(GelbooruWindowBreak.StampUnparsable, broken.at)
        assertEquals("原文没带回来，下一次截图就还是读不出格式：$broken", raw, broken.detail)
    }

    @Test fun noLatestIdIsNotAGuess() = runBlocking {
        val probes = mutableListOf<Long>()
        val finder = GelbooruWindowFinder(
            latestId = { null },
            probeAtLeast = { m -> probes += m; Result.success(Triple(m, stampOf(dayOf(m)), countAt(m))) },
        )
        val broken = finder.hunt(BoundarySide.WINDOW_START, latestDay.minusDays(7), latestDay, emptyList()) as GelbooruWindowHunt.Broken
        assertEquals(GelbooruWindowBreak.ProbeNotWired, broken.at)
        assertEquals(0, probes.size)
    }

    /** 站方把"取最新一条的号"那一笔拒了（401 那一族）⇒ 一步都别往前走，原话留着。 */
    @Test fun aRejectedLatestIdProbeStopsBeforeAnyBoundaryAsk() = runBlocking {
        val probes = mutableListOf<Long>()
        val finder = GelbooruWindowFinder(
            latestId = { Result.failure(IOException("Gelbooru 没接受这组凭据（API Key 与 User ID 要对得上）")) },
            probeAtLeast = { m -> probes += m; Result.success(Triple(m, stampOf(dayOf(m)), countAt(m))) },
        )
        val broken = finder.hunt(BoundarySide.WINDOW_START, latestDay.minusDays(7), latestDay, emptyList()) as GelbooruWindowHunt.Broken
        assertEquals(GelbooruWindowBreak.LatestIdRejected, broken.at)
        assertTrue(broken.detail ?: "", broken.detail!!.contains("没接受这组凭据"))
        assertEquals(0, probes.size)
    }

    /**
     * 日期串换了形状（站方改版）⇒ 当作"没读到"，不许拿猜出来的日子去定边界，且**一笔就停**。
     *
     * ⚠️ 2026-10-16：这一笔原先的桩子是**空串**，而注释写着"站方改版、字段给空"—— 两件事被同一枝
     * 判据盖住，正是屏上那句「上传时间读不出日子」把人带去改解析器的原因。
     * "字段给空"现在有自己那一枝（[blankStampBreaksAsMissingNotUnparsable]），这里只钉"形状变了"。
     */
    @Test fun anUnparsableStampAbortsTheHunt() = runBlocking {
        val probes = mutableListOf<Long>()
        val finder = GelbooruWindowFinder(
            latestId = { Result.success(latest) },
            probeAtLeast = { m -> probes += m; Result.success(Triple(m, "12/10/2026 13:46:29", countAt(m))) },
        )
        assertEquals(
            GelbooruWindowBreak.StampUnparsable,
            (finder.hunt(BoundarySide.WINDOW_START, latestDay.minusDays(7), latestDay, emptyList()) as GelbooruWindowHunt.Broken).at,
        )
        assertEquals(1, probes.size)
    }

    /**
     * `count` 随问的 id 变大而**变大** = 这一站的 `id:>=` 没按语义生效（最阴的一种是被静默忽略、
     * 交回全站）。读到反了就认输，别把全站高分当成"本周排行"交上去。
     */
    @Test fun anIncreasingCountMeansTheWindowTagWasIgnored() = runBlocking {
        val probes = mutableListOf<Long>()
        val finder = GelbooruWindowFinder(
            latestId = { Result.success(latest) },
            probeAtLeast = { m -> probes += m; Result.success(Triple(m, stampOf(dayOf(m)), m)) },
        )
        assertEquals(
            GelbooruWindowBreak.CountInconsistent,
            (finder.hunt(BoundarySide.WINDOW_START, latestDay.minusDays(7), latestDay, emptyList()) as GelbooruWindowHunt.Broken).at,
        )
        assertEquals(2, probes.size)
    }

    /**
     * 删帖留下大空洞：相邻两问回**同一条读数**（记的是"观测到的最老那条"而不是问的那枚），
     * 状态机会原地打转到烧光上限。这里当场认输，并且**只花两笔**。
     */
    @Test fun aHoleThatRepeatsTheSameReadingGivesUpInsteadOfSpinning() = runBlocking {
        val probes = mutableListOf<Long>()
        val onlyEntry = 15_000_000L
        val finder = GelbooruWindowFinder(
            latestId = { Result.success(onlyEntry) },
            probeAtLeast = { m ->
                probes += m
                Result.success(Triple(onlyEntry, stampOf(latestDay.minusDays(7)), 1L))
            },
        )
        assertEquals(
            GelbooruWindowBreak.SameReadingRepeated,
            (finder.hunt(BoundarySide.WINDOW_START, latestDay.minusDays(7), latestDay, emptyList()) as GelbooruWindowHunt.Broken).at,
        )
        assertEquals(2, probes.size)
    }

    /** 桩子自己得站得住：日期函数与串形必须能往返，否则上面每一条都在钉一个不存在的东西。 */
    @Test fun theStubRoundTripsTheSitesOwnStampShape() {
        assertEquals(latestDay.minusDays(7), parseGelbooruStampDay(stampOf(latestDay.minusDays(7))))
        assertEquals(LocalDate.of(2007, 7, 16), parseGelbooruStampDay(stampOf(LocalDate.of(2007, 7, 16))))
        // 今天真实那两条：模型与站方差不到一天（这就是"常数只用于估点"的由来）
        assertEquals(LocalDate.of(2026, 9, 30), dayOf(14_994_000L))
        assertEquals(LocalDate.of(2026, 9, 29), parseGelbooruStampDay("Tue Sep 29 17:30:32 -0500 2026"))
    }
}
