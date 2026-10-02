package com.venera.compose.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 画廊页尾读数的**逐字形状**与四档互斥顺序。
 *
 * 这四件今天散在四处页尾里逐字重复（命中屏蔽规则 4 处、成人内容处理 3 处、
 * 取数状态串 2 处、失败重试块 2 处）。收口不改一个字，但收口之后
 * 「改一处措辞」等于同时改四行 —— 所以要把现行措辞原样钉住：
 * 将来谁调整它会在这里被逼着确认是**几处一起改**，而不是只改自己那一页，
 * 免得出现「猜你喜欢已说到底、搜索页还在转」那种同一屏两处读数不一致。
 */
class GalleryFeedReadoutTextsTest {

    @Test
    fun `屏蔽与分级那两半句的措辞逐字钉住`() {
        assertEquals("3 张命中屏蔽规则 甲、乙", blockedByRulesFragment(3, listOf("甲", "乙")))
        assertEquals("0 张命中屏蔽规则 ", blockedByRulesFragment(0, emptyList()))
        assertEquals("2 张按「成人内容处理」收起", ratingHiddenFragment(2))
    }

    @Test
    fun `取数状态四档互斥且顺序是 正在取 到底 失败 还能滑`() {
        // 正在取优先：第 1 页还在飞的时候说"已经到底"是假读数。
        assertEquals("；正在取下一页", feedTailStatus(loadingMore = true, exhausted = true, hasLoadMoreError = true))
        assertEquals("；已经到底", feedTailStatus(loadingMore = false, exhausted = true, hasLoadMoreError = false))
        // 失败档必须是**空串**：原因由 GalleryLoadMoreRetryLine 说，两头都说就成了两行重复读数；
        // 而"上滑继续取"在这一档尤其不能出现 —— 此刻怎么滑都不会再发请求（那是本轮修过的假读数）。
        assertEquals("", feedTailStatus(loadingMore = false, exhausted = false, hasLoadMoreError = true))
        assertEquals("；上滑继续取", feedTailStatus(loadingMore = false, exhausted = false, hasLoadMoreError = false))
    }
}
