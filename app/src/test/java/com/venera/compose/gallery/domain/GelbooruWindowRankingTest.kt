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
        )
        // `id:A..B` 与 `date:` 都是实测**不生效**的拼法，这里只许出现 `id:>=` 那一枚
        assertEquals("landscape id:>=14994000 sort:score:desc", query)
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
            )
        )
        // 「默认」与「全部」两档不需要边界，照旧发得出
        assertEquals(
            "landscape",
            GalleryRankings.searchQueryFor(
                GallerySite.GELBOORU, listOf(GalleryTagFilter("landscape")),
                GalleryRanking.NEWEST, null, today, null,
            ),
        )
        assertEquals(
            "landscape sort:score:desc",
            GalleryRankings.searchQueryFor(
                GallerySite.GELBOORU, listOf(GalleryTagFilter("landscape")),
                GalleryRanking.ALL, null, today, null,
            ),
        )
    }

    /** 空条件时不留前导空格（与 `searchQuery` 同一条口径，两处各写一份迟早分叉）。 */
    @Test fun noFiltersLeavesNoLeadingSpace() {
        assertEquals(
            "id:>=14994000 sort:score:desc",
            GalleryRankings.searchQueryFor(GallerySite.GELBOORU, emptyList(), GalleryRanking.WEEK, null, today, 14_994_000L),
        )
    }

    /** 非 Gelbooru 站走这一把时必须与 `searchQuery` **逐字相同** —— 别开第二本账。 */
    @Test fun otherSitesDelegateByteForByteToTheExistingBuilder() {
        val filters = listOf(GalleryTagFilter("touhou"))
        listOf(GalleryRanking.NEWEST, GalleryRanking.WEEK, GalleryRanking.MONTH, GalleryRanking.ALL).forEach { r ->
            listOf(GallerySite.YANDERE, GallerySite.SAFEBOORU).forEach { site ->
                assertEquals(
                    "$site $r",
                    GalleryRankings.searchQuery(site, filters, r, null, today),
                    GalleryRankings.searchQueryFor(site, filters, r, null, today, 123L),
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
