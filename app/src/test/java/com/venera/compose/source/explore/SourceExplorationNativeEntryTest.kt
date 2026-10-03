package com.venera.compose.source.explore

import com.venera.compose.source.model.CategoryData
import com.venera.compose.source.model.CategoryItem
import com.venera.compose.source.model.CategoryPart
import com.venera.compose.source.model.PageJumpTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 原生分类项的跳转类型必须原样保留。
 *
 * 锁的是本轮修的 bug：`NativeEntry` 原先只带 `label` + `param`，把官方
 * `PageJumpTarget.page`（源声明的 `itemType`）丢了。`search` 型入口的 `param` 恒为 null，
 * 于是下钻页一律按分类接口发请求 —— 禁漫天堂的「主題A漫 / 角色扮演 / 特殊PLAY」
 * （jm.js 里全是 `itemType: "search"`）拿分类名去查分类接口，点哪个都得到同一份内容。
 *
 * 官方判据：`models.dart:537-560` 的 `PageJumpTarget.jump()` 按 `page` 分发 ——
 * `"search"` → SearchResultPage（关键词 attributes["keyword"]），
 * `"category"` → CategoryComicsPage（attributes["category"] + attributes["param"]）。
 */
class SourceExplorationNativeEntryTest {

    private fun target(page: String, attrs: Map<String, Any?>?) =
        PageJumpTarget(sourceKey = "jm", page = page, attributes = attrs)

    private fun part(name: String, vararg items: CategoryItem) =
        CategoryPart(name = name, type = "fixed", items = items.toList())

    private fun sectionOf(vararg parts: CategoryPart) = SourceExplorationFactory.build(
        sourceKey = "jm",
        sourceName = "禁漫天堂",
        explorePages = emptyList(),
        categoryData = CategoryData(title = "禁漫天堂", key = "禁漫天堂", parts = parts.toList()),
    ).nativeSections

    @Test
    fun `search 型入口的 page 必须原样保留，不得退化成 category`() {
        val section = sectionOf(
            part(
                "主題A漫",
                CategoryItem(
                    label = "無修正",
                    target = target(
                        PAGE_KIND_SEARCH,
                        mapOf("category" to "無修正", "param" to null, "keyword" to "無修正"),
                    ),
                ),
            )
        ).single().items.single()

        // 这三个断言就是修复的判据：page=search（走搜索）、param 为空、keyword 是源侧原文。
        assertEquals(PAGE_KIND_SEARCH, section.page)
        assertNull(section.param)
        assertEquals("無修正", section.keyword)
    }

    @Test
    fun `category 型入口保持 page=category 并保留 param`() {
        val item = sectionOf(
            part(
                "成人A漫",
                CategoryItem(
                    label = "同人",
                    target = target(
                        PAGE_KIND_CATEGORY,
                        mapOf("category" to "同人", "param" to "doujin", "keyword" to "同人"),
                    ),
                ),
            )
        ).single().items.single()

        assertEquals(PAGE_KIND_CATEGORY, item.page)
        assertEquals("doujin", item.param)
    }

    /** 禁漫天堂真实形态：同一源里 category 型与 search 型混排，必须各归各位。 */
    @Test
    fun `同一源内 category 与 search 混排时逐项保留各自的 page`() {
        val items = sectionOf(
            part("成人A漫", CategoryItem("同人", target(PAGE_KIND_CATEGORY, mapOf("param" to "doujin")))),
            part("角色扮演", CategoryItem("御姐", target(PAGE_KIND_SEARCH, mapOf("keyword" to "御姐")))),
            part("特殊PLAY", CategoryItem("觸手", target(PAGE_KIND_SEARCH, mapOf("keyword" to "觸手")))),
        ).flatMap { it.items }

        assertEquals(
            listOf(PAGE_KIND_CATEGORY, PAGE_KIND_SEARCH, PAGE_KIND_SEARCH),
            items.map { it.page },
        )
        assertEquals("doujin", items[0].param)
        assertNull(items[1].param)
        assertEquals("御姐", items[1].keyword)
    }

    /** 没有 attributes 时（官方对未知 itemType 会传 null）不能崩，page 仍原样带出。 */
    @Test
    fun `无 attributes 时 page 原样保留且字段为空`() {
        val item = sectionOf(
            part("X", CategoryItem("Y", target(PAGE_KIND_SEARCH, null)))
        ).single().items.single()

        assertEquals(PAGE_KIND_SEARCH, item.page)
        assertNull(item.param)
        assertNull(item.keyword)
    }
}
