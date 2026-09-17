package com.venera.compose.feature.explore

import com.venera.compose.source.model.ExplorePageData
import com.venera.compose.source.model.ExplorePagePart
import java.util.Locale

/*
 * 探索页的纯逻辑（无 Compose 依赖，便于单测）。
 * 从旧的 ExploreScreen.kt 原样搬来，行为不变。
 */

/** 已装源数量多时用网格铺开，避免把内容挤到屏幕外。 */
internal fun exploreColumnCount(availableWidthDp: Int): Int = (availableWidthDp / 120).coerceAtLeast(3)

/** 探索入口的稳定标识：源 key + 页面下标（不用位置下标，源增删时不会错位）。 */
internal fun ExplorePageData.exploreKey(): String = "$sourceKey:$pageIndex"

/** 选中项在列表里仍存在就保留，否则回落到第一项。 */
internal fun reconcileExploreSelection(selected: String?, keys: List<String>): String? =
    selected?.takeIf { it in keys } ?: keys.firstOrNull()

/** Explicit generations also reject stale completions from source adapters that swallow cancellation. */
internal class ExploreRequestGate {
    private var generation = 0L
    fun begin(): Long = ++generation
    fun isCurrent(request: Long): Boolean = generation == request
}

/** 翻页时把同名分区的内容续接到最后一段，而不是新起一个重复标题。 */
internal fun mergeExploreParts(old: List<ExplorePagePart>, new: List<ExplorePagePart>): List<ExplorePagePart> {
    val result = old.toMutableList()
    new.forEach { part ->
        if (result.lastOrNull()?.title == part.title) {
            val last = result.last()
            result[result.lastIndex] = last.copy(comics = (last.comics + part.comics).distinctBy { it.sourceKey to it.id })
        } else result.add(part)
    }
    return result
}

/**
 * 只翻译**通用**页面名；源自己的品牌名与未知自定义标题原样保留
 * （不把 nhentai 的 "Doujinshi" 硬翻成某种统一分类）。
 */
internal fun explorePageTitle(title: String, sourceName: String): String {
    val pageTitle = title.trim().let { value ->
        if (sourceName.isNotBlank() && value.startsWith(sourceName, ignoreCase = true))
            value.drop(sourceName.length).trim(' ', '·', '-', ':', '：', '|') else value
    }
    val translations = mapOf(
        "home" to "首页", "homepage" to "首页", "home page" to "首页", "explore" to "探索",
        "discovery" to "发现", "discover" to "发现", "popular" to "热门", "hot" to "热门",
        "latest" to "最新", "latest updates" to "最新更新", "recent updates" to "最近更新",
        "updates" to "更新", "new" to "最新", "new releases" to "新作", "recently added" to "最近收录",
        "recommended" to "推荐", "recommendations" to "推荐", "recommendation" to "推荐",
        "featured" to "精选", "trending" to "趋势", "ranking" to "排行", "rankings" to "排行",
        "random" to "随机", "favorites" to "收藏", "all" to "全部", "browse" to "浏览",
        "watched" to "关注", "following" to "关注", "most popular" to "最热门",
        "recent" to "最近", "recently updated" to "最近更新", "newest" to "最新"
    )
    translations[pageTitle.lowercase(Locale.ROOT)]?.let { return it }
    // Some installed sources use a short brand prefix (e.g. "eh latest"), not sourceName.
    for ((english, chinese) in translations.entries.sortedByDescending { it.key.length }) {
        if (pageTitle.endsWith(" $english", ignoreCase = true)) {
            return pageTitle.dropLast(english.length).trimEnd() + " · " + chinese
        }
    }
    return pageTitle.ifBlank { "探索" }
}
