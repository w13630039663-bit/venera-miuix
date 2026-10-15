package com.venera.compose.feature.explore

import com.venera.compose.source.explore.ExploreMode
import com.venera.compose.source.explore.SourceExploration
import com.venera.compose.source.explore.UnifiedTagAvailability
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
        val last = result.lastOrNull()
        // 同名续接；**空标题的新分区也折进上一段** —— `mixed` 型源（nhentai）第 1 页给
        // 带标题的分区、第 2 页起给裸作品列表，解析出来标题就是空的。不折的话每翻一页就
        // 多出一个空标题段（PartHeader 会把空标题兜底成「推荐」，读起来像凭空多了一栏）。
        if (last != null && (last.title == part.title || part.title.isBlank())) {
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

/* ------------------------------------------------------------------ *
 * 显隐判据与屏上文案（2026-10-14 模式行 / 筛选 / 容错那一轮）
 * ------------------------------------------------------------------ */

/**
 * 模式药丸行是否值得占一行。
 *
 * 只有一枚模式时整行不渲染 —— 没得选就不摆：那一枚没有第二个可比，
 * 一行描边药丸既选不动也读不出当前模式。
 * 而 6 颗源的模式标题与源名只差空格与大小写（禁漫天堂 / MangaDex /
 * Komiic / Lanraragi / Gelbooru / 紳士漫畫），那一行会出现第二枚同名药丸，
 * 于是"源名 ×模式名"这句话在两处各说一半，读起来像两套东西。
 *
 * @param modes 已剔除榜档之后的探索方式（榜档改在分类区逐档成入口）。
 */
internal fun exploreModeRowVisible(modes: List<ExploreMode>): Boolean = modes.size > 1

/**
 * 动作行里「筛选 · N」这枚按钮要显示的入口数。
 *
 * N = 该源真正可用的通用标签数，**N == 0 时按钮不渲染**（点开没东西的按钮
 * 不许出现）。取 [UnifiedTagAvailability.available] 而不是原生分区数：
 * 这是跨源快捷筛选的计数，不随源分类体系大小变。
 */
internal fun filterEntryCount(availability: UnifiedTagAvailability): Int =
    availability.available.size

/**
 * 把裸异常 message 翻成人话。
 *
 * **2026-10-14 第三轮：整条逻辑搬去 [com.venera.compose.source.humanizeSourceError]** ——
 * 原来只有探索页用，详情页/二级分类/搜索/网络收藏各写各的 `e.message`，于是同一颗源的
 * 同一个 210 在四个页面是四种说法（探索页"加载失败，请稍后重试"、详情页直接念
 * "Invalid status code: 210"）。收口到一处之后，屏上文案只有一份来源。
 */

