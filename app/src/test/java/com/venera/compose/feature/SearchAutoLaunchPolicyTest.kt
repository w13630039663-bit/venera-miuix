package com.venera.compose.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索页「打字要不要自动发起检索」的判据。
 *
 * 钉的是 2026-10-13 那一条改判：联想面板在屏上的那一段时间**不发**自动搜索。
 * 病不是"多一步提交"，而是这两件事本来打架 —— 面板一冒出来，400ms 防抖就到，
 * 而 `search()` 头一件事是把 `tagSuggestions` 清空，于是联想"刚显示就被刷掉、点不到"。
 */
class SearchAutoLaunchPolicyTest {

    private fun should(
        suggestions: Boolean = false,
        query: String = "touhou",
        tags: Boolean = false,
        matchedUrl: Boolean = false,
    ) = shouldAutoSearchOnQueryChange(
        suggestionsShowing = suggestions,
        query = query,
        hasTags = tags,
        matchedComicUrl = matchedUrl,
        minQueryLength = 2,
    )

    @Test fun suggestionsOnScreenSuppressAutoSearch() {
        // 这一条就是本次的改动点：联想在屏上，连"够长的关键词"也不许发。
        assertFalse(should(suggestions = true, query = "long_enough_keyword"))
        // 标签挂着也一样压住 —— 否则面板照样会被那一笔自动搜索清掉。
        assertFalse(should(suggestions = true, tags = true))
    }

    @Test fun noSuggestionsKeepsTheOldRule() {
        assertTrue(should(query = "ab"))
        assertFalse(should(query = "a"))
    }

    @Test fun tagsAloneAreACompleteCondition() {
        // 关键词短但挂了标签：照旧立刻搜（这条不许被新判据顺带改掉）。
        assertTrue(should(query = "", tags = true))
    }

    @Test fun matchedComicUrlNeverAutoSearches() {
        assertFalse(should(query = "https://www.bilibilimanga.cn/...", matchedUrl = true))
        // 而它就算没被识别成链接、又没联想，也还是按关键词规则走
        assertTrue(should(query = "https://www.bilibilimanga.cn/...", matchedUrl = false))
    }

    /**
     * 顺序判据：「识别成漫画链接」在「联想」之前表态。
     * 两档结果都是 false，所以这一条钉的不是结果而是**别把两条判据合成一条**——
     * 将来若给链接卡加"仍然搜一次"的旁路，联想那档不该被顺带打开。
     */
    @Test fun urlMatchAndSuggestionsAreSeparateReasons() {
        assertFalse(should(suggestions = true, matchedUrl = true))
        assertFalse(should(suggestions = false, matchedUrl = true))
        assertFalse(should(suggestions = true, matchedUrl = false, query = "x"))
    }
}
