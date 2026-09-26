package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GallerySite

/** 一枚已选标签：名字 + 是不是排除项（站方语法就是在前面加 `-`）。 */
data class GalleryTagFilter(val name: String, val excluded: Boolean = false) {
    val token: String get() = if (excluded) "-$name" else name

    companion object {
        /** 从站方语法串还原（历史条目、一次粘贴多个标签都走这里）。空名返回 null。 */
        fun of(token: String): GalleryTagFilter? {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return null
            if (!trimmed.startsWith("-")) return GalleryTagFilter(trimmed)
            val name = trimmed.removePrefix("-").trim()
            return name.takeIf { it.isNotEmpty() }?.let { GalleryTagFilter(it, excluded = true) }
        }
    }
}

/**
 * 一条搜索历史：站点 + 那串标签。带上站点是因为两站词表不通，
 * 同一串标签换个站可能就是另一个结果。
 */
data class GallerySearchEntry(val site: GallerySite, val query: String) {
    val filters: List<GalleryTagFilter> get() = GallerySearch.parseQuery(query)
}

/**
 * 画廊搜索的判据层 —— 全是纯函数，为的是能上单测（本项目单元测试没有 Robolectric）。
 * 判据来自实测（全表见 `gallery-search-2026-09.md`），不是照另一站推断的。
 */
object GallerySearch {

    /**
     * 本站允许的标签数；`null` = 不限。
     *
     * ⚠️ **两站现在都是不限**，但这条判据本身留着 —— 它是"发请求之前先拦住"的唯一落点，
     * 而预算这件事是**会变的**：换回一个有限额的站、或站方哪天收紧，只要在这里返回一个数，
     * UI 就会自动在**加标签那一刻**拦住并报原因，不必再改一遍调用点。
     *
     * Gelbooru 这一档的依据：官方 wiki `howto:api` 与 `howto:cheatsheet` 通篇
     * **没有任何标签数上限**的说明（`limit` 那一项限的是"一次取回多少条"，
     * 不是"能写几枚标签"），实测的 `tag1 {tag2 ~ tag3}` 这类多标签组合也照常工作。
     * 与 Danbooru 那个"匿名第 3 枚直接 422"是完全不同的形态。
     */
    fun tagBudget(site: GallerySite): Int? = when (site) {
        GallerySite.GELBOORU -> null
        GallerySite.YANDERE -> null
    }

    /**
     * 再加一枚还装得下吗。**排除项也吃预算** —— 站方数的是 `tags=` 里空格分隔的段数，
     * 不在乎那一段前面有没有 `-`。
     */
    fun fitsTagBudget(site: GallerySite, afterAdding: Int): Boolean {
        val budget = tagBudget(site) ?: return true
        return afterAdding <= budget
    }

    /** 预算满了要说的那句话；两站现在都不限，恒回 null。UI 据此决定摆不摆提示。 */
    fun budgetNotice(site: GallerySite): String? {
        val budget = tagBudget(site) ?: return null
        return "${site.displayName} 一次最多 $budget 个标签（排除项也算在内）"
    }

    /** 拼成站方 `tags=` 那一段。 */
    fun queryOf(filters: List<GalleryTagFilter>): String = filters.joinToString(" ") { it.token }

    /** 把一串站方语法拆回 chips。 */
    fun parseQuery(query: String): List<GalleryTagFilter> =
        query.split(' ').mapNotNull { GalleryTagFilter.of(it) }

    /**
     * 到底了没：这次给回的条数**少于**要的条数，就是站方没有下一批了。
     *
     * 不用"返回 0 条"判 —— 那要多空跑一页才知道到底，用户会先看到一次"没有更多"再看到页尾，
     * 与搜索页 §7 那条"到底了还显示加载更多"是同一类假读数，只是反着长。
     * 也不用总数 —— 两站都没有总数端点。
     */
    fun isExhausted(returned: Int, requestedLimit: Int): Boolean = returned < requestedLimit

    /** 历史最多存几条、空态摆几条 —— 与漫画搜索同口径（`feature/SearchViewModel.kt:440`）。 */
    const val HISTORY_MAX = 20
    const val HISTORY_SHOWN = 5
}

/**
 * 历史编解码（纯函数，SharedPreferences 那一层只管存这个串）。
 *
 * 格式：一条一行，行内 `站点routeKey=标签串`。
 * 只在**第一个** `=` 处拆 —— routeKey 是 `yandere`/`gelbooru` 这种死串，绝不含 `=`，
 * 而标签串里可能出现 `=`（用户手打 `source=xxx` 这类），拆第一个就不会把查询吃掉半截。
 *
 * 认不出站点的那一行**丢掉而不是硬归某个站**：把一串 Gelbooru 标签拿去 yande.re 搜
 * 会得到一句"没有结果"，那是比少一条历史更坏的结果（宁可错慢不可静默交错）。
 */
fun encodeGallerySearchHistory(entries: List<GallerySearchEntry>): String =
    entries.take(GallerySearch.HISTORY_MAX).joinToString("\n") { "${it.site.routeKey}=${it.query}" }

fun decodeGallerySearchHistory(raw: String?): List<GallerySearchEntry> =
    raw.orEmpty().split('\n').mapNotNull { line ->
        val key = line.substringBefore('=', "").trim()
        val query = line.substringAfter('=', "").trim()
        val site = GallerySite.fromRouteKey(key)
        site?.takeIf { query.isNotEmpty() }?.let { GallerySearchEntry(it, query) }
    }

/** 新条目排到最前，并按"站点 + 标签串"整串去重（同一串标签换站搜算另一条历史）。 */
fun pushGallerySearchHistory(
    current: List<GallerySearchEntry>,
    entry: GallerySearchEntry,
): List<GallerySearchEntry> {
    val key = "${entry.site.routeKey}=${entry.query}"
    return (listOf(entry) + current.filter { "${it.site.routeKey}=${it.query}" != key })
        .take(GallerySearch.HISTORY_MAX)
}
