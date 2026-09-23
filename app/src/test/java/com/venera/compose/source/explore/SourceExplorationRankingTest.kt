package com.venera.compose.source.explore

import com.venera.compose.source.model.CategoryData
import com.venera.compose.source.model.RankingOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排行榜档位的入口生成规则。
 *
 * 锁的是本轮修掉的那件事：原先这里硬编码一个 mode、option 写死 `"day"`，
 * 而 `"day"` 是我们臆造的 —— 哔咔的合法档位是 `H24/D7/D30`，禁漫是 `mv/mv_m/mv_w/mv_t`，
 * EH 是 `15/13/12/11`。发错档位在界面上只表现为"排行榜是空的"。
 */
class SourceExplorationRankingTest {

    private fun category(enableRanking: Boolean, vararg options: RankingOption) = CategoryData(
        title = "T",
        key = "T",
        enableRankingPage = enableRanking,
        rankingOptions = options.toList(),
    )

    private fun build(category: CategoryData) = SourceExplorationFactory.build(
        sourceKey = "picacg",
        sourceName = "Picacg",
        explorePages = emptyList(),
        categoryData = category,
    )

    @Test
    fun `源声明几档就出几个入口，回传的是 key 不是整串`() {
        val exploration = build(
            category(
                true,
                RankingOption("H24", "日"),
                RankingOption("D7", "周"),
                RankingOption("D30", "月"),
            )
        )
        val rankingModes = exploration.modes.filter { it.kind == ExploreMode.Kind.RANKING }
        assertEquals(3, rankingModes.size)
        assertEquals(listOf("H24", "D7", "D30"), rankingModes.map { it.rankingOption })
        assertEquals(
            listOf("picacg:ranking:H24", "picacg:ranking:D7", "picacg:ranking:D30"),
            rankingModes.map { it.id },
        )
        assertEquals(listOf("排行·日", "排行·周", "排行·月"), rankingModes.map { it.label })
    }

    @Test
    fun `绝不出现臆造的 day`() {
        val exploration = build(category(true, RankingOption("H24", "日")))
        assertTrue(exploration.modes.none { it.rankingOption == "day" })
    }

    @Test
    fun `开了排行页但没给档位就不出入口，不摆死按钮`() {
        val exploration = build(category(true))
        assertFalse(exploration.hasRanking)
        assertTrue(exploration.modes.none { it.kind == ExploreMode.Kind.RANKING })
    }

    /** 空 key 是合法的：manhuagui 的 `-最新发布` 与 mycomic 的 `-views-历史排行` 都拆出空 key，
     *  源用它表示默认排序，不能被当成"没有档位"而丢掉。 */
    @Test
    fun `空 key 的档位照样出入口`() {
        val exploration = build(category(true, RankingOption("", "最新发布")))
        assertTrue(exploration.hasRanking)
        val mode = exploration.modes.single { it.kind == ExploreMode.Kind.RANKING }
        assertEquals("", mode.rankingOption)
        assertEquals("排行·最新发布", mode.label)
    }

    @Test
    fun `没开排行页时即使有档位也不出`() {
        val exploration = build(category(false, RankingOption("week", "周")))
        assertFalse(exploration.hasRanking)
        assertTrue(exploration.modes.none { it.kind == ExploreMode.Kind.RANKING })
    }
}
