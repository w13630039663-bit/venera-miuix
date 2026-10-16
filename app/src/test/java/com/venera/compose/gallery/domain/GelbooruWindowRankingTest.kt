package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 移动端「Gelbooru 时间窗合成档」的判据层（2026-10-13）。
 *
 * 这一批的口径是**用户那句「先改移动端的，桌面端的别动」**，所以这里钉的第一件事就是
 * 两把尺子不许合流：站方给不给（[GalleryRankings.supports]）与我们做不做得出
 * （[GalleryRankings.supportsForApp]）是两个问题，而桌面只读前者。
 * 第二件事是**定不出来必须不发**：退成"没有后缀的那一串"在站方语义里就是全站最新/历年高分，
 * 而胶囊上写着「周」—— 那是本仓最忌的静默交错，不是降级。
 */
class GelbooruWindowRankingTest {

    private val today = LocalDate.of(2026, 10, 12)
    private val gelbooruOnly = listOf(GallerySite.GELBOORU)
    private val mixed = listOf(GallerySite.YANDERE, GallerySite.GELBOORU)

    @Test fun nativeVerdictIsUnchangedForDesktop() {
        // 桌面读的就是这一把：它今天一个字不许变
        assertFalse(GalleryRankings.supports(GallerySite.GELBOORU, GalleryRanking.WEEK))
        assertFalse(GalleryRankings.supports(GallerySite.GELBOORU, GalleryRanking.MONTH))
        assertTrue(GalleryRankings.supports(GallerySite.YANDERE, GalleryRanking.WEEK))
        // 而 suffix 的原样那条支路仍给 null（桌面据此不发）
        assertNull(GalleryRankings.suffix(GallerySite.GELBOORU, GalleryRanking.WEEK, null, today))
    }

    @Test fun appSideAddsTheFourSynthesizedWindowsForPureGelbooruOnly() {
        GalleryRanking.entries.forEach { ranking ->
            assertEquals(
                "$ranking",
                GalleryRankings.supportsAll(gelbooruOnly, ranking) ||
                    GalleryRankings.gelbooruWindowSynthesizable(ranking),
                GalleryRankings.supportsForApp(gelbooruOnly, ranking),
            )
        }
        assertTrue(GalleryRankings.supportsForApp(gelbooruOnly, GalleryRanking.WEEK))
        assertTrue(GalleryRankings.supportsForApp(gelbooruOnly, GalleryRanking.MONTH))
    }

    /** 混腿来源（「全部」那一档）绝不因为"有一条腿合成得出来"就列出来。 */
    @Test fun mixedLegsNeverListSynthesizedWindows() {
        listOf(GalleryRanking.DAY, GalleryRanking.WEEK, GalleryRanking.MONTH, GalleryRanking.YEAR).forEach {
            assertFalse("$it", GalleryRankings.supportsForApp(mixed, it))
        }
        // 而它两档原生的是照常（默认与全部）
        assertTrue(GalleryRankings.supportsForApp(mixed, GalleryRanking.NEWEST))
        assertTrue(GalleryRankings.supportsForApp(mixed, GalleryRanking.ALL))
    }

    @Test fun emptySiteListIsNotSupportsForApp() {
        assertFalse(GalleryRankings.supportsForApp(emptyList(), GalleryRanking.WEEK))
    }

    @Test fun onlyTheFourWindowRanksNeedABoundary() {
        GalleryRanking.entries.forEach {
            assertEquals(
                "$it",
                GalleryRankings.gelbooruWindowSynthesizable(it),
                GalleryRankings.boundaryTargetDay(GallerySite.GELBOORU, it, null, today) != null,
            )
        }
        // 非 Gelbooru 站永远不需要（原生站自己就认窗口）
        GalleryRanking.entries.forEach {
            assertNull(GalleryRankings.boundaryTargetDay(GallerySite.YANDERE, it, null, today))
        }
    }

    @Test fun boundaryTargetDayIsTheWindowFirstDay() {
        assertEquals(
            GalleryRankings.windowBounds(GalleryRanking.MONTH, null, today)!!.first,
            GalleryRankings.boundaryTargetDay(GallerySite.GELBOORU, GalleryRanking.MONTH, null, today),
        )
        // 带历史锚点时按那一期念，不是本期
        val anchor = LocalDate.of(2024, 3, 17)
        assertEquals(
            LocalDate.of(2024, 3, 1),
            GalleryRankings.boundaryTargetDay(GallerySite.GELBOORU, GalleryRanking.MONTH, anchor, today),
        )
    }

    @Test fun resolvedBoundaryBecomesTheOnlyIdTagThatActuallyWorks() {
        val query = GalleryRankings.searchQueryFor(
            GallerySite.GELBOORU,
            listOf(GalleryTagFilter("landscape")),
            GalleryRanking.WEEK,
            null,
            today,
            14_994_000L,
            GelbooruWindowEnd.Unbounded,
        )
        // `id:A..B` 与 `date:` 都是实测**不生效**的拼法，这里只许出现 `id:>=` 那一枚
        assertEquals("landscape id:>=14994000 sort:score:desc", query)
    }

    /**
     * 闭环窗口：**两枚各自生效的标签**，不是区间语法。
     *
     * 2026-10-16 带凭据在真站上量的算术（基线 14,426,357）：
     * `id:>=14718000` ⇒ 375,233、`id:>=14994000` ⇒ 101,393、两枚合起来 ⇒ **273,840**（严格互补），
     * 而 `id:14718000..14994000` ⇒ `count` 与基线**逐字相同**（被静默忽略）。
     * 所以 2024 年 3 月那一期的榜只能是 `id:>=A id:<B`，写成区间那一串就是"全站高分配一个写着 2024-03 的胶囊"。
     */
    @Test fun closedWindowQueryCarriesBothBounds() {
        val anchor = LocalDate.of(2024, 3, 17)
        val query = GalleryRankings.searchQueryFor(
            GallerySite.GELBOORU,
            listOf(GalleryTagFilter("landscape")),
            GalleryRanking.MONTH,
            anchor,
            today,
            14_718_000L,
            GelbooruWindowEnd.Before(14_994_000L),
        )
        assertEquals("landscape id:>=14718000 id:<14994000 sort:score:desc", query)
        // 两枚都是**独立标签**：不许有人把它"简化"成区间语法（那种写法不报错，只交回全站那批）
        assertFalse("$query", query!!.contains(".."))
    }

    /** 证到了"站方最新一条仍在这一期之内" ⇒ 交回的那一串与本期那一档**逐字同形**。 */
    @Test fun provenOpenEndedWindowIsByteIdenticalToTheCurrentPeriod() {
        val filters = listOf(GalleryTagFilter("landscape"))
        val historical = GalleryRankings.searchQueryFor(
            GallerySite.GELBOORU, filters, GalleryRanking.MONTH, LocalDate.of(2024, 3, 17), today,
            14_718_000L, GelbooruWindowEnd.Unbounded,
        )
        val current = GalleryRankings.searchQueryFor(
            GallerySite.GELBOORU, filters, GalleryRanking.MONTH, null, today,
            14_718_000L, GelbooruWindowEnd.Unbounded,
        )
        assertEquals("landscape id:>=14718000 sort:score:desc", historical)
        assertEquals(historical, current)
    }

    // ── 上界那一枚什么时候要（只有翻历史期才要）──

    /** 本期不探上界：本期终点就是今天，站方没有比今天更晚的图，再探六七笔换不来一个字。 */
    @Test fun currentPeriodNeedsNoUpperBound() {
        assertNull(GalleryRankings.boundaryEndTargetDay(GallerySite.GELBOORU, GalleryRanking.MONTH, null, today))
        // 弹层里点到"本月/今年"（锚点非空但终点就是今天）也算本期 —— 那一档与本期逐字同形
        assertNull(
            GalleryRankings.boundaryEndTargetDay(
                GallerySite.GELBOORU, GalleryRanking.MONTH, today.withDayOfMonth(1), today,
            ),
        )
        assertNull(
            GalleryRankings.boundaryEndTargetDay(
                GallerySite.GELBOORU, GalleryRanking.YEAR, LocalDate.of(2026, 6, 1), today,
            ),
        )
    }

    /** 历史期要上界，而且那是"这一期之后的第一天"（不含），不是这一期的最后一天。 */
    @Test fun historicalPeriodNeedsTheDayAfterTheWindow() {
        assertEquals(
            LocalDate.of(2024, 4, 1),
            GalleryRankings.boundaryEndTargetDay(
                GallerySite.GELBOORU, GalleryRanking.MONTH, LocalDate.of(2024, 3, 17), today,
            ),
        )
        assertEquals(
            LocalDate.of(2024, 1, 1),
            GalleryRankings.boundaryEndTargetDay(
                GallerySite.GELBOORU, GalleryRanking.YEAR, LocalDate.of(2023, 8, 3), today,
            ),
        )
        // 上一天/上一周那种"往回一格"不是弹层的路（supportsHistory 只开月/年），但定义也得跟着窗口走
        assertEquals(
            LocalDate.of(2026, 10, 11),
            GalleryRankings.boundaryEndTargetDay(
                GallerySite.GELBOORU, GalleryRanking.DAY, today.minusDays(2), today,
            ),
        )
        // 非 Gelbooru 站压根不需要第二枚（原生站自己认 date:A..B）
        GalleryRanking.entries.forEach {
            assertNull(
                "$it",
                GalleryRankings.boundaryEndTargetDay(
                    GallerySite.YANDERE, it, LocalDate.of(2024, 3, 17), today,
                ),
            )
        }
    }

    /** 「要不要两枚」与「第二枚问哪天」必须是同一个答案 —— 两把尺子分叉就意味着有一腿会超时或空转。 */
    @Test fun theBudgetVerdictTracksTheSecondTargetDay() {
        listOf(
            GalleryRanking.MONTH to LocalDate.of(2024, 3, 17),
            GalleryRanking.YEAR to LocalDate.of(2023, 1, 1),
            GalleryRanking.MONTH to null,
            GalleryRanking.MONTH to today.withDayOfMonth(1),
            GalleryRanking.NEWEST to LocalDate.of(2024, 3, 17),
        ).forEach { (ranking, anchor) ->
            assertEquals(
                "$ranking / $anchor",
                GalleryRankings.boundaryEndTargetDay(GallerySite.GELBOORU, ranking, anchor, today) != null,
                GalleryRankings.needsGelbooruEndBoundary(GallerySite.GELBOORU, ranking, anchor, today),
            )
        }
    }

    /** 这一档摆不摆「选具体哪一期…」，移动端问的是**合成档也算**那一把；原生那把留给桌面。 */
    @Test fun periodPickerRowUsesTheAppSideVerdict() {
        assertTrue(GalleryRankings.supportsPeriodPickerForApp(gelbooruOnly))
        assertTrue(GalleryRankings.supportsPeriodPickerForApp(listOf(GallerySite.YANDERE)))
        // 混腿：一条原生一条合成 ⇒ 半面墙按这一期、半面墙按别的，胶囊上的期次就是在骗人
        assertFalse(GalleryRankings.supportsPeriodPickerForApp(mixed))
        assertFalse(GalleryRankings.supportsPeriodPickerForApp(emptyList()))
        // 而桌面那把一个字没动（纯 Gelbooru 照旧判死 —— 它不接 id 边界这一路）
        assertFalse(GalleryRankings.supportsPeriodPickerAll(gelbooruOnly))
    }

    /** 这一条就是"不许静默交回全站"的闸：边界没定出来 ⇒ 不发，交回 null 而不是交回一串没后缀的。 */
    @Test fun unresolvedBoundarySendsNothingInsteadOfWholeSite() {
        assertNull(
            GalleryRankings.searchQueryFor(
                GallerySite.GELBOORU,
                listOf(GalleryTagFilter("landscape")),
                GalleryRanking.WEEK,
                null,
                today,
                null,
                GelbooruWindowEnd.Unbounded,
            ),
        )
        // 「默认」与「全部」两档不需要边界，照旧发得出
        assertEquals(
            "landscape",
            GalleryRankings.searchQueryFor(
                GallerySite.GELBOORU, listOf(GalleryTagFilter("landscape")),
                GalleryRanking.NEWEST, null, today, null, GelbooruWindowEnd.Unbounded,
            ),
        )
        assertEquals(
            "landscape sort:score:desc",
            GalleryRankings.searchQueryFor(
                GallerySite.GELBOORU, listOf(GalleryTagFilter("landscape")),
                GalleryRanking.ALL, null, today, null, GelbooruWindowEnd.Unbounded,
            ),
        )
    }

    /** 空条件时不留前导空格（与 `searchQuery` 同一条口径，两处各写一份迟早分叉）。 */
    @Test fun noFiltersLeavesNoLeadingSpace() {
        assertEquals(
            "id:>=14994000 sort:score:desc",
            GalleryRankings.searchQueryFor(
                GallerySite.GELBOORU, emptyList(), GalleryRanking.WEEK, null, today,
                14_994_000L, GelbooruWindowEnd.Unbounded,
            ),
        )
        assertEquals(
            "id:>=14718000 id:<14994000 sort:score:desc",
            GalleryRankings.searchQueryFor(
                GallerySite.GELBOORU, emptyList(), GalleryRanking.MONTH, LocalDate.of(2024, 3, 17), today,
                14_718_000L, GelbooruWindowEnd.Before(14_994_000L),
            ),
        )
    }

    /** 非 Gelbooru 站走这一把时必须与 `searchQuery` **逐字相同** —— 别开第二本账。 */
    @Test fun otherSitesDelegateByteForByteToTheExistingBuilder() {
        val filters = listOf(GalleryTagFilter("touhou"))
        listOf(
            GalleryRanking.NEWEST, GalleryRanking.WEEK, GalleryRanking.MONTH, GalleryRanking.ALL,
        ).forEach { r ->
            listOf(GallerySite.YANDERE, GallerySite.SAFEBOORU).forEach { site ->
                assertEquals(
                    "$site $r",
                    GalleryRankings.searchQuery(site, filters, r, null, today),
                    GalleryRankings.searchQueryFor(site, filters, r, null, today, 123L, GelbooruWindowEnd.Unbounded),
                )
            }
        }
    }

    /**
     * 接线判据：Android 那侧**必须真把定界口接上**。
     *
     * [com.venera.compose.gallery.data.GalleryPorts.gelbooruWindows] 的默认值是 null（桌面不接这一路），
     * 漏接的屏上表现恰好是本仓最忌的那一型：菜单照样列这四档（判据面看的是"我们做不做得出"），
     * 而每一档点下去都探不出边界、这一腿不发。编译器与运行时都管不到"少传了一枚参数"，只能扫源码。
     */
    @Test fun androidSideWiresTheBoundarySeam() {
        val dir = "com/venera/compose/gallery/data/android"
        val hits = RepoSources.linesOf(dir)
            .filter { (_, _, text) -> text.contains("gelbooruWindows = AndroidGelbooruWindowProbe(") }
        assertEquals(
            "GalleryPortsAndroid 没把定界口接上（四档合成窗口会变成点了发不出去的假开关）。" +
                "该目录下文件：${RepoSources.kotlinFiles(dir).map { it.name }}",
            1,
            hits.size,
        )
    }
}
