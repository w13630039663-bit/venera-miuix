package com.venera.compose.feature

/**
 * 单源搜索「还有下一页吗」的判据。
 *
 * 抽成纯函数的理由：这条判据原先写成"源声明了页数就只看页数"
 * （`SearchViewModel.kt:417` 旧写法 `page.maxPage?.let { nextPage < it } ?: comics.isNotEmpty()`），
 * 而多数源的 `maxPage` 比**这个关键词真能翻到的页数**大得多（实测口径见
 * `explore-capability-matrix.md`：总数 0/33 源提供、26 个只有 maxPage、7 个连它都没有）。
 * 于是翻到真正空了的那一页时 `nextPage < maxPage` 依旧成立，「加载更多」永远挂在页尾，
 * 点它只是把同一页空结果再要一遍（`currentPage` 只在非空时才推进，所以是原地打转）
 * —— 用户完全无法判断这里是加载完了还是要继续加载。
 *
 * 三条判据按证据强度排：
 * 1. **这一页没给出任何未见过的条目就算到底**（空页算，越界把末页原样回吐的那类源
 *    会被排重成"零新条目"，也算）；
 * 2. 源声明了页数时,页数只用来**提前收手、省掉一次注定为空的请求**,不拿来否决第 1 条；
 * 3. 游标型/不声明页数的源,只能靠第 1 条。
 */
internal object SearchPagination {

    /**
     * @param unseenItemsOnPage 这一页里**过屏蔽规则之前**的新条目数。
     *
     * 必须是守卫前的计数：HIDE 规则或用户黑名单把整整一页剔空，不等于源没有下一页
     * （探索页同一个坑，见 `UnifiedExploreScreen.kt` 里 `buildExploreContent` 的注释）。
     */
    fun canLoadMore(
        declaredMaxPage: Int?,
        pageJustFetched: Int,
        unseenItemsOnPage: Int,
    ): Boolean {
        if (unseenItemsOnPage <= 0) return false
        val maxPage = declaredMaxPage ?: return true
        return pageJustFetched < maxPage
    }
}
