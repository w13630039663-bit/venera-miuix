package com.venera.compose.feature.explore

import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ExplorePagePart

/**
 * 探索页当前展示的内容。
 *
 * 两种形态由**源**决定，不由 UI 强行统一：
 *  - [Sections]：源返回了多分区（推荐 / 热门 / 今日排行…），按分区渲染。
 *  - [ComicList]：普通分页列表（最新 / H24 / D7…），按列表渲染并支持翻页。
 */
sealed interface ExploreContent {
    data object Empty : ExploreContent

    /** 源声明的多分区（singlePageWithMultiPart）。 */
    data class Sections(val parts: List<ExplorePagePart>) : ExploreContent

    /**
     * 普通分页列表（multiPageComicList / 排行榜）。
     *
     * 命名为 ComicList 而非 List：后者会遮蔽 kotlin.collections.List，
     * 导致 sealed interface 内部所有 List<...> 写法解析失败。
     */
    data class ComicList(
        val comics: List<Comic>,
        val page: Int = 1,
        val maxPage: Int? = null,
        val hasMore: Boolean = false,
    ) : ExploreContent

    val isEmpty: Boolean
        get() = when (this) {
            Empty -> true
            is Sections -> parts.all { it.comics.isEmpty() }
            is ComicList -> comics.isEmpty()
        }
}
