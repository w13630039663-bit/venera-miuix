package com.venera.compose.feature

import com.venera.compose.feature.explore.ExploreRequestGate
import com.venera.compose.feature.explore.exploreColumnCount
import com.venera.compose.feature.explore.exploreKey
import com.venera.compose.feature.explore.explorePageTitle
import com.venera.compose.feature.explore.mergeExploreParts
import com.venera.compose.feature.explore.reconcileExploreSelection
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
}
