package com.venera.compose.feature

import com.venera.compose.feature.explore.ExploreRequestGate
import com.venera.compose.feature.explore.exploreColumnCount
import com.venera.compose.feature.explore.exploreKey
import com.venera.compose.feature.explore.exploreModeRowVisible
import com.venera.compose.feature.explore.explorePageTitle
import com.venera.compose.feature.explore.filterEntryCount
import com.venera.compose.feature.explore.mergeExploreParts
import com.venera.compose.feature.explore.reconcileExploreSelection
import com.venera.compose.source.explore.ExploreMode
import com.venera.compose.source.explore.UnifiedTag
import com.venera.compose.source.explore.UnifiedTagAvailability
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ExplorePageData
import com.venera.compose.source.model.ExplorePagePart
import org.junit.Assert.*
import org.junit.Test

class ExplorePolicyTest {
    @Test fun columnsGrowAndRowsNeverDropSources() {
        assertEquals(3, exploreColumnCount(280))
        assertEquals(3, exploreColumnCount(412))
        assertEquals(6, exploreColumnCount(800))
        for (count in 0..80) {
            val entries = (0 until count).toList()
            val rows = entries.chunked(exploreColumnCount(360))
            assertEquals(entries, rows.flatten())
            assertEquals((count + 2) / 3, rows.size)
        }
    }
    @Test fun sourceInstallRemovalAndReorderPreserveIdentityNotPosition() {
        assertEquals("b:0", reconcileExploreSelection("b:0", listOf("new:0", "a:0", "b:0")))
        assertEquals("b:0", reconcileExploreSelection("b:0", listOf("b:0", "a:0")))
        assertEquals("a:0", reconcileExploreSelection("b:0", listOf("a:0")))
        assertNull(reconcileExploreSelection("b:0", emptyList()))
        assertEquals("new:0", reconcileExploreSelection(null, listOf("new:0")))
        val page = ExplorePageData("Home", sourceKey = "a", sourceName = "Brand")
        assertEquals(page.exploreKey(), page.copy(title = "首页", sourceName = "新名称").exploreKey())
        assertNotEquals(page.exploreKey(), page.copy(pageIndex = 1).exploreKey())
    }
    @Test fun staleRequestsCannotReplaceNewSelectionOrDeletedSource() {
        val gate = ExploreRequestGate()
        val sourceA = gate.begin()
        val sourceB = gate.begin()
        assertFalse(gate.isCurrent(sourceA))
        assertTrue(gate.isCurrent(sourceB))
        gate.begin() // invalidated when the selected source is removed
        assertFalse(gate.isCurrent(sourceB))
    }
    @Test fun genericEnglishTitlesAreChineseAndBrandNamesArePreserved() {
        assertEquals("首页", explorePageTitle("MangaDex · Home", "MangaDex"))
        assertEquals("最新更新", explorePageTitle("Latest Updates", "MangaDex"))
        assertEquals("热门", explorePageTitle("POPULAR", "Brand"))
        assertEquals("eh · 关注", explorePageTitle("eh watched", "E-Hentai"))
        assertEquals("eh · 最新", explorePageTitle("eh latest", "E-Hentai"))
        assertEquals("探索", explorePageTitle("MangaDex", "MangaDex"))
        assertEquals("Brand Special", explorePageTitle("Brand Special", "Other"))
        assertEquals("编辑精选", explorePageTitle("编辑精选", "Brand"))
    }
    @Test fun paginationKeepsAllSectionsAndDeduplicatesOnlySameSourceComic() {
        val one = Comic("1", "one", sourceKey = "a")
        val two = Comic("2", "two", sourceKey = "a")
        val anotherSource = one.copy(sourceKey = "b")
        val result = mergeExploreParts(
            listOf(ExplorePagePart("Latest", listOf(one))),
            listOf(ExplorePagePart("Latest", listOf(one, two, anotherSource)), ExplorePagePart("Other", listOf(two)))
        )
        assertEquals(listOf(one, two, anotherSource), result[0].comics)
        assertEquals(listOf(two), result[1].comics)
    }

    /**
     * mixed 型源（nhentai）第 2 页起返回的是**裸作品列表**，解析出来标题为空 ——
     * 这种空标题分区必须折进上一段，而不是每翻一页多出一个标题为空的段
     * （PartHeader 会把空标题兜底成「推荐」，读起来像凭空多了一栏）。
     */
    @Test fun mixedPaginationFoldsUntitledPageIntoLastSection() {
        val one = Comic("1", "one", sourceKey = "a")
        val two = Comic("2", "two", sourceKey = "a")
        val three = Comic("3", "three", sourceKey = "a")
        val result = mergeExploreParts(
            listOf(
                ExplorePagePart("Popular Now", listOf(one)),
                ExplorePagePart("New Uploads", listOf(two)),
            ),
            listOf(ExplorePagePart("", listOf(three))),
        )
        assertEquals(2, result.size)
        assertEquals("New Uploads", result[1].title)
        assertEquals(listOf(two, three), result[1].comics)
    }

    /* ── 2026-10-14 显隐判式与屏上文案 ── */

    private fun mode(id: String) =
        ExploreMode(id = id, label = id, kind = ExploreMode.Kind.EXPLORE_PAGE, pageIndex = 0)

    @Test
    fun modeRowOnlyShowsWhenThereIsAChoice() {
        // 只有一个模式 = 没得选，整行不渲染。6 颗源命中这条（模式标题与源名同名）。
        assertFalse(exploreModeRowVisible(emptyList()))
        assertFalse(exploreModeRowVisible(listOf(mode("m1"))))
        assertTrue(exploreModeRowVisible(listOf(mode("m1"), mode("m2"))))
        assertTrue(exploreModeRowVisible(listOf(mode("m1"), mode("m2"), mode("m3"))))
    }

    @Test
    fun filterCountCountsOnlyTagsThisSourceCanActuallyUse() {
        assertEquals(0, filterEntryCount(UnifiedTagAvailability(emptyList())))
        assertEquals(1, filterEntryCount(UnifiedTagAvailability(listOf(UnifiedTag.LONG))))
        assertEquals(
            UnifiedTag.entries.size,
            filterEntryCount(UnifiedTagAvailability(UnifiedTag.entries.toList())),
        )
        // isFallbackOnly 不许反过来抬计数：那条分支现网零调用（UnifiedTags.kt 的读数），
        // 它恒与 available.isEmpty() 同时成立 —— 正因为它不是判据，才不会把空按钮喂出来。
        assertEquals(0, filterEntryCount(UnifiedTagAvailability(emptyList(), isFallbackOnly = true)))
    }
}
