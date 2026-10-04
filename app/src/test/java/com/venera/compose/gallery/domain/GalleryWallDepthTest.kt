package com.venera.compose.gallery.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 墙上落点判据（2026-10-04，用户报「画廊搜索往下滑，切到别的主页面再切回来会回到顶部」）。
 *
 * 钉的是两条容易写反的语义：
 * - **锚不同 = 不是同一块内容**，这时不能把人送到旧序号那儿去（换一批之后第 40 张是别的图），
 *   返回 null 让调用方按"新一轮 → 第 1 张"走；
 * - **内容还没落地时不能落点**，那一次 `scrollToItem` 会被 Lazy 的夹取按成 0，
 *   反而把真实落点抹掉。
 */
class GalleryWallDepthTest {

    @Test
    fun `没记过位置就没有落点要求`() {
        assertNull(depthToApply(saved = null, currentAnchorId = 77L))
    }

    @Test
    fun `内容还没落地时一律不落点`() {
        assertNull(depthToApply(SavedWallDepth(anchorId = 77L, index = 40), currentAnchorId = null))
    }

    @Test
    fun `还是刚才那块内容就回原处`() {
        assertEquals(40, depthToApply(SavedWallDepth(77L, 40), currentAnchorId = 77L))
    }

    @Test
    fun `换了一批（锚变了）就不送去旧序号`() {
        assertNull(depthToApply(SavedWallDepth(77L, 40), currentAnchorId = 78L))
    }

    /** 落点"第 0 张"也是一种落点：它必须回 0 而不是 null，否则调用方分不清"没人要求"和"就在顶部"。 */
    @Test
    fun `序号 0 与没有落点要求是两件事`() {
        assertEquals(0, depthToApply(SavedWallDepth(77L, 0), currentAnchorId = 77L))
    }
}
