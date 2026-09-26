package com.venera.compose.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「还有下一页吗」判据。这几条全部是**修复前的实际错法**，逐条对应一种源。
 *
 * 修复前写法：`page.maxPage?.let { nextPage < it } ?: comics.isNotEmpty()`
 * —— 源声明了页数时，空页这一条硬证据被完全否决。
 */
class SearchPaginationTest {

    @Test
    fun `源虚报页数时 空页那一次就该判到底`() {
        // jm 这类：maxPage 报的是分类总量而不是本关键词能翻到的页数，翻到第 4 页已经空了
        assertFalse(
            SearchPagination.canLoadMore(declaredMaxPage = 400, pageJustFetched = 4, unseenItemsOnPage = 0)
        )
    }

    @Test
    fun `页数还没到 且这一页有新条目 才算还有下一页`() {
        assertTrue(
            SearchPagination.canLoadMore(declaredMaxPage = 5, pageJustFetched = 2, unseenItemsOnPage = 20)
        )
    }

    @Test
    fun `翻到声明的末页就收手 不再发那次注定为空的请求`() {
        assertFalse(
            SearchPagination.canLoadMore(declaredMaxPage = 3, pageJustFetched = 3, unseenItemsOnPage = 20)
        )
    }

    @Test
    fun `游标型源不声明页数时 非空即还有`() {
        assertTrue(
            SearchPagination.canLoadMore(declaredMaxPage = null, pageJustFetched = 7, unseenItemsOnPage = 1)
        )
        assertFalse(
            SearchPagination.canLoadMore(declaredMaxPage = null, pageJustFetched = 7, unseenItemsOnPage = 0)
        )
    }

    @Test
    fun `越界回吐末页的源排重后零新条目即到底`() {
        // 这类源过界后把最后一页原样再给一遍，条目全被排重掉，列表不再长，
        // 但修复前 canLoadMore 仍按页数判成 true —— 用户看到的就是「加载更多」永远点不完。
        assertFalse(
            SearchPagination.canLoadMore(declaredMaxPage = 40, pageJustFetched = 5, unseenItemsOnPage = 0)
        )
    }

    @Test
    fun `屏蔽规则剔空一页不等于没有下一页`() {
        // 计数必须取过守卫**之前**的新条目数：这里源给了 20 条、只是全被 HIDE 规则剔掉，
        // 下一页照样有货，不能报到底。
        assertTrue(
            SearchPagination.canLoadMore(declaredMaxPage = null, pageJustFetched = 3, unseenItemsOnPage = 20)
        )
    }

    @Test
    fun `首页就空时判到底而不是提示还能加载更多`() {
        assertFalse(
            SearchPagination.canLoadMore(declaredMaxPage = 12, pageJustFetched = 1, unseenItemsOnPage = 0)
        )
    }
}
