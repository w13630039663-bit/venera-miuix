package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GalleryLegOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「一条腿的取数结果」怎么翻成（货 / 答没答 / 为什么没给）的判据锁（批次 K）。
 *
 * 这一层从前有**三份各写一遍**（日榜取数、猜你喜欢翻页、搜索两腿并发）。三份迟早分叉，
 * 而分叉的代价不报错、只出假读数 —— 2026-09-30 就在其中一份里查到一条：
 * 请求**失败**那一支照样把 `returned` 记成 0，于是"缺席"被并入"回了 0 条"，
 * [GallerySearchMerge.exhaustedSites] 那把判据当场把那一站永久踢出后续翻页。
 * 现象是"某一站的图再也不出现了"，而页尾写着"已经到底"。
 *
 * 所以这里钉死的重点是 **[GalleryLegOutcome.answered]** 这一半：
 * 站方真回了空表（到底的信号）与请求压根没成（缺席）必须能被分开。
 */
class GalleryLegGuardTest {

    private fun post(id: Long) = GalleryPost(
        site = GallerySite.YANDERE,
        id = id,
        previewUrl = "https://example.invalid/$id.jpg",
        fileExt = "jpg",
    )

    private fun outcome(result: Result<List<GalleryPost>>?) =
        GalleryLegGuard.outcomeOf(GallerySite.YANDERE, timedOut = result == null, outcome = result)

    @Test
    fun `答上了且有货时没有任何要念的原因`() {
        val leg = outcome(Result.success(listOf(post(1), post(2))))

        assertTrue(leg.answered)
        assertNull(leg.reason)
        assertEquals(2, leg.posts.size)
    }

    @Test
    fun `站方回了空表是答上了 这一点与请求失败必须分开`() {
        val leg = outcome(Result.success(emptyList()))

        // `answered = true` 是"这一站可以说到底了"的唯一凭据：把它和失败混在一起，
        // 一次 401 就会把那一站永久踢出翻页，而页尾写着"已经到底"。
        assertTrue(leg.answered)
        // 但空表仍然要说出来 —— 屏上一张没有而页尾不给个说法，读起来像我们没发请求。
        assertEquals("这一轮没有返回内容", leg.reason)
    }

    @Test
    fun `请求失败不算答上 原因取异常自带的句子`() {
        val leg = outcome(Result.failure(IllegalStateException("HTTP 401 未授权")))

        assertFalse(leg.answered)
        assertEquals("HTTP 401 未授权", leg.reason)
        assertTrue(leg.posts.isEmpty())
    }

    @Test
    fun `异常没带话时退回类名 不吞成 null`() {
        val leg = outcome(Result.failure(RuntimeException()))

        assertFalse(leg.answered)
        // 吞成 null 就等于"没发生过错"：那一档页尾什么都不念，用户只看到少了一个站的货。
        assertEquals("RuntimeException", leg.reason)
    }

    @Test
    fun `超时是这一轮没赶上 不是这一站到底了`() {
        val leg = outcome(null)

        assertFalse(leg.answered)
        // 秒数字面量是**故意钉死**的：它是日榜那头按本机 TTFB 实测定的预算
        // （`GalleryFeedSource.PER_SITE_TIMEOUT_MS`）。有人调那个数，这里会红 ——
        // 那就是一次重新核对观感取舍的机会，而不是让读数与预算悄悄分叉。
        assertEquals("超过 12s 没返回", leg.reason)
        assertTrue(leg.posts.isEmpty())
    }
}
