package com.venera.compose.source.explore

import com.venera.compose.source.model.ExplorePageData
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * explore 页的 `type` → [ExploreMode.Kind] 映射。
 *
 * 锁的是 2026-10-14 真机读数暴露的那条 bug：引擎只认 `singlePageWithMultiPart`，
 * 另外三个 `type` 全被当成「分页列表」——
 *  - `multiPartPage`（**10 颗源在用**：禁漫天堂 / 紳士漫畫 / MangaDex / 漫画柜 / 漫画人 /
 *    mh18 / 漫小肆 / 优酷漫画 / goda / h-comic）的分区对象里没有 `comic` 字段，
 *    逐条解析成 null，默认页一片空白；
 *  - `mixed`（nhentai）被当成分页列表，两个分区标题被压平；
 *  - 且 `mixed` 在取数层调的是源脚本里根本不存在的 `loadMixed`，真机直接报
 *    "exploadMixed is not a function"。
 *
 * 四个 type 收敛到三种 Kind 是这一轮的判据，任何一条退化都会在这里红。
 */
class SourceExplorationModeKindTest {

    private fun kindOf(type: String): ExploreMode.Kind {
        val exploration = SourceExplorationFactory.build(
            sourceKey = "s",
            sourceName = "S",
            explorePages = listOf(
                ExplorePageData(title = "S", type = type, sourceKey = "s", sourceName = "S", pageIndex = 0)
            ),
            categoryData = null,
        )
        return exploration.modes.single().kind
    }

    @Test
    fun `单页多分区与 multiPartPage 同归 EXPLORE_MULTI_PART`() {
        assertEquals(ExploreMode.Kind.EXPLORE_MULTI_PART, kindOf("singlePageWithMultiPart"))
        assertEquals(ExploreMode.Kind.EXPLORE_MULTI_PART, kindOf("multiPartPage"))
    }

    @Test
    fun `mixed 是独立一档，首页分区且仍可翻页`() {
        assertEquals(ExploreMode.Kind.EXPLORE_MIXED, kindOf("mixed"))
        // mixed 不能被并进 EXPLORE_MULTI_PART —— 那一档在页面上永不翻页（hasMore 恒 false），
        // 而 nhentai 的 New Uploads 靠翻页续。
        assertEquals(false, kindOf("mixed") == ExploreMode.Kind.EXPLORE_MULTI_PART)
    }

    @Test
    fun `分页列表与未知 type 都落到 EXPLORE_PAGE`() {
        assertEquals(ExploreMode.Kind.EXPLORE_PAGE, kindOf("multiPageComicList"))
        assertEquals(ExploreMode.Kind.EXPLORE_PAGE, kindOf("somethingBrandNew"))
    }

    /** pageIndex 原样带出：内容取数靠它定位到源脚本 explore 数组的第几项。 */
    @Test
    fun `pageIndex 原样带出`() {
        val exploration = SourceExplorationFactory.build(
            sourceKey = "s",
            sourceName = "S",
            explorePages = listOf(
                ExplorePageData("A", "multiPageComicList", "s", "S", pageIndex = 0),
                ExplorePageData("B", "multiPartPage", "s", "S", pageIndex = 1),
                ExplorePageData("C", "mixed", "s", "S", pageIndex = 2),
            ),
            categoryData = null,
        )
        assertEquals(listOf(0, 1, 2), exploration.modes.map { it.pageIndex })
        assertEquals(
            listOf(
                ExploreMode.Kind.EXPLORE_PAGE,
                ExploreMode.Kind.EXPLORE_MULTI_PART,
                ExploreMode.Kind.EXPLORE_MIXED,
            ),
            exploration.modes.map { it.kind },
        )
    }
}
