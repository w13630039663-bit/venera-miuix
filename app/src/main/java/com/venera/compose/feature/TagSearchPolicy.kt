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

    /**
     * 规范化标签值：小写 → 去 namespace 前缀 → 忽略全部空白差异 → **繁转简**。
     *
     * 顺序很重要：**必须先小写再截前缀**。旧写法是
     * `tag.trim().lowercase().substringAfter(':', tag.trim())`，第二个参数是
     * **未小写**的原串，于是标签不含冒号时兜底返回的是原始大小写 ——
     * `"School Girl"` 归一成 `"SchoolGirl"`，卡片里的 `"school girl"` 归一成
     * `"schoolgirl"`，两边永远不相等。也就是说这个助手原本只在带命名空间时
     * 才大小写无关，而这正是它最常见的用法。
     *
     * 最后一级 [toSimplified] 对应原版 `multi_tag_search.dart` 的 `normalizeTagValue`
     * （注释即「去 namespace 前缀、小写、繁转简」）。默认恒等，所以不传就等于没有这一级。
     * 转换是**字级**的，`太陽眼鏡 / 太阳镜` 这类词级差异它修不了。
     */
    fun normalizeTagValue(
        tag: String,
        toSimplified: (String) -> String = { it },
    ): String {
        val lower = tag.trim().lowercase()
        val colon = lower.indexOf(':')
        val body = if (colon >= 0 && colon < lower.length - 1) lower.substring(colon + 1) else lower
        return toSimplified(body.replace(Regex("\\s+"), ""))
    }

    /** AND 语义：作品必须带有全部已选标签（宽松：值互相包含即算命中）。 */
    fun matchesTagFilter(
        comic: Comic,
        tags: List<SearchTag>,
        toSimplified: (String) -> String = { it },
    ): Boolean {
        if (tags.isEmpty()) return true
        return tags.all { selected ->
            val value = normalizeTagValue(selected.raw, toSimplified)
            value.isEmpty() || comic.tags.any { comicTag ->
                val candidate = normalizeTagValue(comicTag, toSimplified)
                candidate == value || candidate.contains(value) || value.contains(candidate)
            }
        }
    }

    fun filterByTags(
        comics: List<Comic>,
        tags: List<SearchTag>,
        toSimplified: (String) -> String = { it },
    ): List<Comic> =
        if (tags.isEmpty()) comics else comics.filter { matchesTagFilter(it, tags, toSimplified) }

    /**
     * 客户端标签过滤的结果。
     *
     * [relaxed] = true 表示「严格 AND 过滤把这一页**全部**清掉了，于是退回未过滤的列表」。
     */
    data class TagFilterOutcome(val comics: List<Comic>, val relaxed: Boolean)

    /**
     * 带降级的过滤。
     *
     * 为什么要降级：多数源的**列表项**根本不带完整标签（原版对此有明确警告，
     * 见 `.reference/flutter-master/lib/utils/multi_tag_search.dart:22-25`：只有 picacg
     * 这类把站方分区塞进 tags 的源才撑得住客户端过滤，其余源「只会把整页清空」）。
     * 再叠加源里繁简混写，选两个简体标签对上卡片里的繁体写法 → AND 交集为空 →
     * 用户看到的不该是一片空白，而是「结果没被精确过滤」这个事实。
     *
     * （此前这里的注释写的是「我们没有繁简转换表」。那句是错的：`assets/opencc.txt`
     * 一直在仓库里，只是 Kotlin 侧零引用。现在由 [toSimplified] 接上，降级触发率因此下降，
     * 但**不会归零** —— 字级表实测只能归一 84.5% 的简繁异名，词级差异仍会走到这里。）
     *
     * 退回时**必须**让 UI 说明清楚：这批结果并不保证满足所选标签。
     */
    fun filterByTagsWithFallback(
        comics: List<Comic>,
        tags: List<SearchTag>,
        toSimplified: (String) -> String = { it },
    ): TagFilterOutcome {
        if (tags.isEmpty()) return TagFilterOutcome(comics, relaxed = false)
        val kept = comics.filter { matchesTagFilter(it, tags, toSimplified) }
        return if (kept.isEmpty() && comics.isNotEmpty()) {
            TagFilterOutcome(comics, relaxed = true)
        } else {
            TagFilterOutcome(kept, relaxed = false)
        }
    }
}