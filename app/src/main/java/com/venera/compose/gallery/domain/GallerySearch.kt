package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.DanbooruClient
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
 * 一条搜索历史：站点 + 那串标签。带上站点是因为两站词表不通、预算也不同，
 * 同一串标签换个站可能就是另一个结果（甚至 422）。
 */
data class GallerySearchEntry(val site: GallerySite, val query: String) {
    val filters: List<GalleryTagFilter> get() = GallerySearch.parseQuery(query)
}

/**
 * 画廊搜索的判据层 —— 全是纯函数，为的是能上单测（本项目单元测试没有 Robolectric）。
 * 三条判据都来自实测（全表见 `gallery-search-2026-09.md` §〇），不是照另一站推断的。
 */
object GallerySearch {

    /**
     * 本站允许的标签数；`null` = 不限。Danbooru 匿名实测只有 2 枚，第 3 枚直接 422。
     *
     * 登录后按账号等级算（Member 仍是 2、Gold 6、Platinum 及以上不限，出处与实测见
     * [DanbooruClient.tagsPerSearch]）—— 所以 [danbooruLevel] 传的是**站方给的真实等级**，
     * 不是"登录没登录"。没登录时传 null。
     */
    fun tagBudget(site: GallerySite, danbooruLevel: Int? = null): Int? = when (site) {
        GallerySite.DANBOORU -> DanbooruClient.tagsPerSearch(danbooruLevel)
        GallerySite.YANDERE -> null
    }

    /**
     * 再加一枚还装得下吗。**排除项也吃预算** —— 站方数的是 `tags=` 里空格分隔的段数，
     * 不在乎那一段前面有没有 `-`（实测 `hime_cut -long_hair` 就是 2 枚）。
     */
    fun fitsTagBudget(site: GallerySite, afterAdding: Int, danbooruLevel: Int? = null): Boolean {
        val budget = tagBudget(site, danbooruLevel) ?: return true
        return afterAdding <= budget
    }

    /** 预算满了要说的那句话；没预算（yande.re / 已登录的 Platinum+）回 null，UI 据此决定摆不摆提示。 */
    fun budgetNotice(site: GallerySite, danbooruLevel: Int? = null): String? {
        val budget = tagBudget(site, danbooruLevel) ?: return null
        // Danbooru 这一档要把"登录了为什么还是 2 枚"写清楚：免费注册只到 Member，
        // 2 枚这条只有付费的 Gold 才放宽（站方 help:users 那张表）。不写明就会变成一次投诉。
        return if (site == GallerySite.DANBOORU && danbooruLevel != null) {
            "${site.displayName} 这一档账号一次最多 $budget 个标签" +
                "（会员与匿名都是 2；要 6 枚需站方的 Gold）。排除项也算在内"
        } else {
            "${site.displayName} 匿名检索一次最多 $budget 个标签（排除项也算在内）"
        }
    }

    /**
     * 条件里**会不会搜出一批站方封掉的图** —— 命中的那几枚受管制标签（没有就空表）。
     *
     * 判据只有 Danbooru 有（[DanbooruClient.CENSORED_TAGS]，出处见那里的注释）。
     * 之所以值得在**发请求之前**就算一遍：命中的查询是**必然 0 张可摆**的
     * （那不是"这个标签冷门"，是站方对 Member 与未登录一律封），
     * 与其等 200 条空跑回来再解释，不如加标签那一刻就说。
     *
     * 排除项（`-loli`）**不算命中**：那是"把这类图排掉"，正是被封之后该做的动作，
     * 对它报警就成了"劝你别做对的事"。
     */
    fun censoredTagsIn(site: GallerySite, filters: List<GalleryTagFilter>): List<String> =
        if (site != GallerySite.DANBOORU) {
            emptyList()
        } else {
            filters.asSequence()
                .filterNot { it.excluded }
                .map { it.name.trim().lowercase() }
                .filter { it in DanbooruClient.CENSORED_TAGS }
                .distinct()
                .toList()
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
     * 也不用总数 —— 两站都没有总数端点（实测 404）。
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
 * 只在**第一个** `=` 处拆 —— routeKey 是 `yandere`/`danbooru` 这种死串，绝不含 `=`，
 * 而标签串里可能出现 `=`（用户手打 `source=xxx` 这类），拆第一个就不会把查询吃掉半截。
 *
 * 认不出站点的那一行**丢掉而不是硬归某个站**：把一串 Danbooru 标签拿去 yande.re 搜
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
