package com.venera.compose.source.explore

import com.venera.compose.source.model.CategoryData
import com.venera.compose.source.model.CategoryItem
import com.venera.compose.source.model.CategoryPart
import com.venera.compose.source.model.PageJumpTarget
import com.venera.compose.source.model.RankingOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类分区的随机窗口与合成榜组。
 *
 * 锁的是 2026-10-04 那轮的两件事：
 *
 * 1. `type: "random"` + `randomNumber` 迁移时整个丢了 —— `JsComicSource` 的分类脚本没输出这个字段，
 *    [CategoryPart] 也没有对应位。后果是 nhentai 的「Tags」（`nhentai.js:547-553`，声明只露 20 枚）
 *    把 `Object.values(Nhentai.nhentaiTags)` 约 720 条全渲染。官方判据在
 *    `category.dart:82-86`（随机窗口）+ `parser.dart:470-472`（只有 `type=="random"` 才读它）。
 * 2. 榜的入口原先是一张只会选**第一档**的卡，而 jm 的第一档是「總排行」，用户要的「日排行」排第 4。
 */
class SourceExplorationTagWindowTest {

    private fun item(label: String) = CategoryItem(
        label = label,
        target = PageJumpTarget(
            sourceKey = "nhentai",
            page = PAGE_KIND_SEARCH,
            attributes = mapOf("category" to label, "keyword" to label),
        ),
    )

    private fun items(count: Int) = (1..count).map { item("t$it") }

    private fun build(vararg parts: CategoryPart) = SourceExplorationFactory.build(
        sourceKey = "nhentai",
        sourceName = "nhentai",
        explorePages = emptyList(),
        categoryData = CategoryData(
            title = "nhentai",
            key = "nhentai",
            parts = parts.toList(),
        ),
    )

    // ── 随机窗口 ──────────────────────────────────────────────────

    @Test
    fun `random 分区只露源声明的窗口大小`() {
        val section = build(
            CategoryPart(name = "Tags", type = "random", randomNumber = 20, items = items(720))
        ).nativeSections.single()
        assertEquals(20, section.items.size)
    }

    /**
     * 窗口必须是原列表的**连续片段**（官方 `sublist(start, start + n)` 的语义），
     * 不能是"随机抽 20 枚"那种打乱。标签写成 t1..t720 就是为了让连续性能直接算。
     *
     * 顺带钉死官方那处开区间带来的怪味道：`nextInt(size - n)` 上界不含，
     * 所以起点最远到 699（0-based），**全组最后一枚永远不会进窗口**。逐字对齐就得把它一起对齐，
     * 别偷偷"修好"成闭区间。
     */
    @Test
    fun `窗口是全组的连续片段，不重排也不重复`() {
        val picked = build(
            CategoryPart(name = "Tags", type = "random", randomNumber = 20, items = items(720))
        ).nativeSections.single().items.map { it.label.removePrefix("t").toInt() }
        assertEquals(20, picked.size)
        assertEquals(20, picked.distinct().size)
        val run = (picked.first()..picked.last()).toList()
        assertEquals("必须连续：" + picked, run, picked)
        assertTrue("起点越界：" + picked.first(), picked.first() >= 1)
        assertTrue("尾项越界：" + picked.last(), picked.last() <= 720 - 20 - 1 + 20)
        assertTrue("官方开区间：末项永远进不了窗口", picked.last() < 720)
    }

    @Test
    fun `random 没写 randomNumber 时按官方的 1`() {
        val section = build(
            CategoryPart(name = "Tags", type = "random", items = items(30))
        ).nativeSections.single()
        assertEquals(1, section.items.size)
    }

    @Test
    fun `窗口不小于全组时整组原样`() {
        val section = build(
            CategoryPart(name = "Language", type = "random", randomNumber = 20, items = items(3))
        ).nativeSections.single()
        assertEquals(listOf("t1", "t2", "t3"), section.items.map { it.label })
    }

    /**
     * baozi 类型(27)、ikmmh 分类(42)、wnacg 各分区都在 **fixed** 上写了 `randomNumber: 5`。
     * 官方 `parser.dart` 只在 `type == "random"` 分支读它 —— 顺手照做就把这些源的"整组露出"
     * 悄悄改成了"只露 5 枚"，那是官方没有的观感。
     */
    @Test
    fun `fixed 分区上的 randomNumber 一律不生效`() {
        val section = build(
            CategoryPart(name = "类型", type = "fixed", randomNumber = 5, items = items(27))
        ).nativeSections.single()
        assertEquals(27, section.items.size)
    }

    @Test
    fun `随机窗口只作用于 random 分区，同页其他分组不受影响`() {
        val exploration = build(
            CategoryPart(name = "Language", type = "fixed", items = items(3)),
            CategoryPart(name = "Tags", type = "random", randomNumber = 20, items = items(720)),
        )
        assertEquals(3, exploration.nativeSections[0].items.size)
        assertEquals(20, exploration.nativeSections[1].items.size)
    }

    // ── 合成榜组 ──────────────────────────────────────────────────

    private fun withRanking(vararg options: RankingOption) = SourceExplorationFactory.build(
        sourceKey = "jm",
        sourceName = "禁漫天堂",
        explorePages = emptyList(),
        categoryData = CategoryData(
            title = "禁漫天堂",
            key = "禁漫天堂",
            enableRankingPage = true,
            rankingOptions = options.toList(),
            parts = listOf(CategoryPart(name = "主題A漫", type = "fixed", items = items(18))),
        ),
    )

    @Test
    fun `源声明几档就出几枚榜入口，标签用源自己的档名`() {
        val ranking = withRanking(
            RankingOption("mv", "總排行"),
            RankingOption("mv_m", "月排行"),
            RankingOption("mv_w", "周排行"),
            RankingOption("mv_t", "日排行"),
        ).rankingSection
        assertEquals(
            listOf("總排行", "月排行", "周排行", "日排行"),
            ranking?.items?.map { it.label },
        )
        assertEquals(listOf("mv", "mv_m", "mv_w", "mv_t"), ranking?.items?.map { it.rankingOption })
    }

    /** `nativeSections` 的头一条契约是「原样保留源声明的分区名，绝不合并」，合成的榜不能进去。 */
    @Test
    fun `榜组不混进 nativeSections`() {
        val exploration = withRanking(RankingOption("mv_t", "日排行"))
        assertEquals(listOf("主題A漫"), exploration.nativeSections.map { it.name })
        assertEquals(SourceExplorationFactory.RANKING_SECTION_NAME, exploration.rankingSection?.name)
    }

    @Test
    fun `没档位就没榜组，不摆死按钮`() {
        val exploration = SourceExplorationFactory.build(
            sourceKey = "jm",
            sourceName = "禁漫天堂",
            explorePages = emptyList(),
            categoryData = CategoryData(
                title = "禁漫天堂",
                key = "禁漫天堂",
                enableRankingPage = true,
                rankingOptions = emptyList(),
            ),
        )
        assertFalse(exploration.hasRanking)
        assertNull(exploration.rankingSection)
    }

    /** manhuagui 的 `-最新发布`、mycomic 的 `-views-历史排行` 拆出空 key，源用它表示默认排序。 */
    @Test
    fun `空 key 的档位照样是入口`() {
        val entry = withRanking(RankingOption("", "最新发布")).rankingSection!!.items.single()
        assertEquals("", entry.rankingOption)
        assertEquals("最新发布", entry.label)
    }

    /** 榜入口不带 param，也不带跳转页型。 */
    @Test
    fun `榜入口不带 param 也不带 keyword`() {
        val entry = withRanking(RankingOption("mv_t", "日排行")).rankingSection!!.items.single()
        assertNull(entry.param)
        assertNull(entry.keyword)
        assertEquals(PAGE_KIND_CATEGORY, entry.page)
    }
}
