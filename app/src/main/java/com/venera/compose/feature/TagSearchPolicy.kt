package com.venera.compose.feature

import com.venera.compose.source.model.Comic

/**
 * 多标签搜索的两路策略（对齐上游 multi_tag_search.dart 的实测行为）：
 *
 * - 原生标签语法源（search 关键词支持 namespace:tag / tag:xxx 语法）：标签
 *   改写后与关键词一起透传，保留站方精度；
 * - 其余源：剥离全部标签词，只用纯文本调源搜索，结果在客户端按标签宽松
 *   过滤 —— 直接透传会让这些源把 "female:lolicon" 当普通关键字，一条都搜不到。
 */
object TagSearchPolicy {
    val nativeTagSources: Set<String> = setOf("ehentai", "nhentai", "hitomi", "manga_dex")

    fun isNativeTagSource(sourceKey: String): Boolean = sourceKey.lowercase() in nativeTagSources

    /**
     * 某个源真正发出的搜索关键词。
     * 非原生源即使带标签也返回纯文本（可能为空串，由调用方决定是否降级）。
     */
    suspend fun requestKeyword(
        sourceKey: String,
        keyword: String,
        tags: List<SearchTag>,
        format: suspend (String, String) -> String,
    ): String {
        val text = keyword.trim()
        if (tags.isEmpty() || isNativeTagSource(sourceKey)) {
            return (listOf(text) + tags.map { format(it.namespace, it.raw) })
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }
        return text
    }

    /** 规范化标签值：去 namespace 前缀、小写、忽略全部空白差异。 */
    fun normalizeTagValue(tag: String): String =
        tag.trim().lowercase().substringAfter(':', tag.trim()).replace(Regex("\\s+"), "")

    /** AND 语义：作品必须带有全部已选标签（宽松：值互相包含即算命中）。 */
    fun matchesTagFilter(comic: Comic, tags: List<SearchTag>): Boolean {
        if (tags.isEmpty()) return true
        return tags.all { selected ->
            val value = normalizeTagValue(selected.raw)
            value.isEmpty() || comic.tags.any { comicTag ->
                val candidate = normalizeTagValue(comicTag)
                candidate == value || candidate.contains(value) || value.contains(candidate)
            }
        }
    }

    fun filterByTags(comics: List<Comic>, tags: List<SearchTag>): List<Comic> =
        if (tags.isEmpty()) comics else comics.filter { matchesTagFilter(it, tags) }
}