package com.venera.compose.feature

import com.venera.compose.source.model.SearchOptionGroup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SearchOptionsStateTest {
    private fun select(default: String? = null) = SearchOptionGroup(
        label = "排序", options = linkedMapOf("" to "相关度", "new" to "最新"), defaultValue = default
    )

    @Test fun defaultsKeepEmptySelectNullDropdownAndJsonMultiSelectInPosition() {
        val groups = listOf(select(), SearchOptionGroup(type = "dropdown", options = linkedMapOf("zh" to "中文")),
            SearchOptionGroup(type = "multi-select", options = linkedMapOf("a" to "分类A")))
        assertEquals(listOf("", null, "[]"), SearchOptionValues.defaults(groups))
        assertEquals(listOf("", null, "[]"), SearchOptionsStore().apply("eh", groups, listOf("", null, "[]")))
    }

    @Test fun dropdownExplicitNullCanClearEvenWhenSourceDeclaresDefault() {
        val dropdown = SearchOptionGroup(type = "dropdown", options = linkedMapOf("zh" to "中文"), defaultValue = "zh")
        val store = SearchOptionsStore()
        assertEquals(listOf("zh"), store.load("eh", listOf(dropdown)))
        assertEquals(listOf<String?>(null), store.apply("eh", listOf(dropdown), listOf(null)))
        assertEquals(listOf<String?>(null), store.load("eh", listOf(dropdown)))
        assertEquals(listOf("zh"), store.apply("eh", listOf(dropdown), emptyList()))
    }

    @Test fun explicitDefaultsOverrideFirstOption() {
        assertEquals(listOf("new"), SearchOptionValues.defaults(listOf(select("new"))))
    }

    @Test fun identicalGroupsOnDifferentSourcesNeverShareChoices() {
        val store = SearchOptionsStore()
        val groups = listOf(select())
        store.load("source-a", groups)
        store.apply("source-a", groups, listOf("new"))
        assertEquals(listOf(""), store.load("source-b", groups))
        assertEquals(listOf("new"), store.load("source-a", groups))
    }

    @Test fun changedDeclarationWithSameGroupCountResetsToNewDefault() {
        val store = SearchOptionsStore()
        store.apply("source", listOf(select()), listOf("new"))
        val changed = select().copy(label = "源更新后的排序", defaultValue = "")
        assertEquals(listOf(""), store.load("source", listOf(changed)))
    }

    @Test fun abandonedDialogDraftDoesNotMutateAppliedSelection() {
        val store = SearchOptionsStore()
        val groups = listOf(select())
        val applied = store.load("source", groups)
        val draft = applied.toMutableList()
        draft[0] = "new"
        assertEquals(listOf(""), store.load("source", groups))
        assertEquals(listOf("new"), store.apply("source", groups, draft))
    }

    @Test fun multiSelectUsesJsonNotCommaDelimitedAndCanBeCleared() {
        val group = SearchOptionGroup(type = "multi-select", options = linkedMapOf("a,b" to "甲", "quote\"" to "乙"))
        val first = SearchOptionValues.toggle(group, "[]", "a,b")
        val both = SearchOptionValues.toggle(group, first, "quote\"")
        assertEquals(listOf("a,b", "quote\""), SearchOptionValues.selectedKeys(both))
        val last = SearchOptionValues.toggle(group, SearchOptionValues.toggle(group, both, "a,b"), "quote\"")
        assertEquals("[]", last)
    }

    @Test fun invalidKeysAreRemovedAndEmptyStringIsARealKey() {
        assertEquals("", SearchOptionValues.normalize(select("new"), ""))
        assertEquals("new", SearchOptionValues.normalize(select("new"), "invalid"))
        val multi = SearchOptionGroup(type = "multi-select", options = linkedMapOf("a" to "甲"))
        assertEquals("[\"a\"]", SearchOptionValues.normalize(multi, "[\"a\",\"bad\",\"a\"]"))
        assertEquals("[]", SearchOptionValues.normalize(multi, "not JSON"))
    }

    @Test fun tagQueryPassesRawNamespaceAndPreservesKeyword() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()
        val result = composeSearchQuery(" keyword ", listOf(SearchTag("artist", "raw tag", "中文展示"))) { namespace, raw ->
            calls.add(namespace to raw)
            "$namespace:$raw"
        }
        assertEquals(listOf("artist" to "raw tag"), calls)
        assertEquals("keyword artist:raw tag", result)
        assertFalse(result.contains("中文展示"))
    }

    @Test fun tagOnlySearchFormatsIndependentlyForEachSource() = runBlocking {
        val tags = listOf(SearchTag(raw = "raw", label = "翻译"))
        assertEquals("source-a:raw", composeSearchQuery("", tags) { _, raw -> "source-a:$raw" })
        assertEquals("raw", composeSearchQuery("", tags) { _, raw -> raw })
    }
}
