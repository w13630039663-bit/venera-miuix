package com.venera.compose.feature

import com.venera.compose.source.model.Comic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagSearchPolicyTest {
    private val tag = SearchTag(namespace = "female", raw = "lolicon", label = "萝莉")

    @Test fun nativeSourcesKeepTagsInRequest() {
        assertEquals("妹妹 female:lolicon",
            kotlinx.coroutines.runBlocking {
                TagSearchPolicy.requestKeyword("ehentai", "妹妹", listOf(tag)) { _, raw -> "female:$raw" }
            })
        assertEquals("tag:lolicon",
            kotlinx.coroutines.runBlocking {
                TagSearchPolicy.requestKeyword("manga_dex", "", listOf(SearchTag(raw = "lolicon"))) { _, raw -> "tag:$raw" }
            })
    }

    @Test fun nonNativeSourcesSearchPlainTextOnly() {
        val request = kotlinx.coroutines.runBlocking {
            TagSearchPolicy.requestKeyword("copy_manga", " 妹妹 ", listOf(tag)) { _, _ -> "female:lolicon" }
        }
        assertEquals("妹妹", request)
    }

    @Test fun clientFilterRequiresEverySelectedTag() {
        val comic = Comic(id = "1", title = "t", tags = listOf("Female:  Loli", "校服"))
        assertTrue(TagSearchPolicy.filterByTags(listOf(comic), listOf(tag)).isNotEmpty())
        assertFalse(TagSearchPolicy.matchesTagFilter(comic, listOf(tag, SearchTag(raw = "JK"))))
    }

    @Test fun namespacePrefixAndWhitespaceDoNotBreakMatching() {
        assertTrue(TagSearchPolicy.matchesTagFilter(
            Comic(id = "1", title = "t", tags = listOf("big breasts")),
            listOf(SearchTag(raw = "female:Big  Breasts"))))
    }

    @Test fun variantLayerIsWhatMakesSimplifiedTagMatchTraditionalCard() {
        val comic = Comic(id = "1", title = "t", tags = listOf("Female: 蘿莉"))
        val tags = listOf(SearchTag(namespace = "female", raw = "萝莉"))
        // 不给繁简那一级就不匹配 —— 这正是「整页被 AND 清空 → 降级成未过滤」的来源
        assertFalse(TagSearchPolicy.matchesTagFilter(comic, tags))
        assertTrue(TagSearchPolicy.matchesTagFilter(comic, tags) { it.replace('蘿', '萝') })
    }
}