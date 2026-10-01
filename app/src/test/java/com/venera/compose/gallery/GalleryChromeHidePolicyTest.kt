package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryChromeHidePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住「上滑收起两栏」的判据。
 *
 * 这一段每一条判据错了都**不会报错**，只会在屏上看着像"卡了一下"：
 * 方向搞反（下滑才收起）、不复位（切走 Tab 回来栏没了、或者滑一点点就瞬间收起）、
 * 把"没滚动"当成"回滚"（一进页面两栏就自己收走）。所以它值得一组用例。
 *
 * ## 符号约定（2026-09-30 从库源码量的，不是推的）
 *
 * **负的 delta = 手指向上滑 = 往下浏览新内容 ⇒ 该收起**；**正的 delta = 手指向下滑 = 往列表开头回 ⇒ 该展开**。
 *
 * 出处：`androidx.compose.material3` 1.5.0-alpha22 `AppBar.kt` 的
 * `ExitUntilCollapsedScrollBehavior.onPreScroll` 写着 `// Don't intercept if scrolling down.`
 * 配 `if (available.y > 0f) return Offset.Zero`，而它收起靠 `heightOffset += available.y` 往**负**走
 * （`heightOffset ∈ [heightOffsetLimit(负), 0]`，0 = 展开）；`EnterAlwaysScrollBehavior` 的 KDoc 是
 * "collapse when the nested content is **pulled up**"，同样靠负 delta。
 *
 * ⚠️ 这一组用例**曾经整批按错的方向写**（当时认定"正的 delta = 手指向上推 = 下滑"），
 * 于是它们一直绿着把缺陷钉在屏上 —— 真机表现就是用户报的那句"搞反了，应该是上滑收起"。
 * 记在这里是为了提醒下一个人：**判据层的绿，只等于它钉的那个方向是绿的。**
 */
class GalleryChromeHidePolicyTest {

    private val threshold = 200f

    /** 反复喂同一笔位移，返回最终状态。 */
    private fun run(deltas: List<Float>, threshold: Float = 200f): Boolean {
        var hidden = false
        var acc = 0f
        deltas.forEach { delta ->
            val (h, a) = GalleryChromeHidePolicy.afterScroll(hidden, acc, delta, threshold)
            hidden = h
            acc = a
        }
        return hidden
    }

    @Test
    fun `累计上滑超过阈值就收起`() {
        // 三笔各 -80（手指向上滑），累计 240 > 200 ⇒ 第三笔之后收。
        assertFalse("还没到阈值时不该收", run(listOf(-80f, -80f), threshold))
        assertTrue("过了阈值就该收", run(listOf(-80f, -80f, -80f), threshold))
    }

    @Test
    fun `下滑立刻展开 不要求累计`() {
        // 先收起来，然后只给一笔很小的回滚（+1）就该展开 —— 用户口径是"往回滑立刻回来"。
        val hidden = run(listOf(-300f), threshold)
        assertTrue(hidden)
        val (after, acc) = GalleryChromeHidePolicy.afterScroll(hidden, 300f, 1f, threshold)
        assertFalse("任何向下滑都应立即展开", after)
        assertEquals("回滚要把累计清零", 0f, acc, 0.001f)
    }

    @Test
    fun `零位移不算回滚`() {
        // 惯性衰减到 0、或手指按住不动时会出现 0 位移。若把它当成"回滚"，
        // 一次上滑途中就会被自己反复展开 —— 观感是抖动。
        val (hidden, acc) = GalleryChromeHidePolicy.afterScroll(
            hidden = false,
            accumulated = 100f,
            delta = 0f,
            threshold = threshold,
        )
        assertFalse(hidden)
        assertEquals("零位移应当照常累计（这里 100 + 0）", 100f, acc, 0.001f)
    }

    @Test
    fun `收起之后继续上滑不会改变状态`() {
        val (hidden, _) = GalleryChromeHidePolicy.afterScroll(
            hidden = true,
            accumulated = 300f,
            delta = -120f,
            threshold = threshold,
        )
        assertTrue("已经收着就保持收着", hidden)
    }

    @Test
    fun `阈值以下的小幅来回滑动不会收起`() {
        // 60 上滑 → 40 回滚（清零）→ 60 上滑：累计从来没到 200，不该收。
        assertFalse(run(listOf(-60f, 40f, -60f), threshold))
    }

    @Test
    fun `复位同时清掉隐藏与累计`() {
        val (hidden, acc) = GalleryChromeHidePolicy.reset()
        assertFalse(hidden)
        // 只清 hidden 不清累计，下一次上滑会带着上一轮的累计瞬间触发 ——
        // 那是"刚滑一点点栏就没了"的成因，所以这一条必须钉住。
        assertEquals(0f, acc, 0.001f)
    }

    @Test
    fun `复位之后要从头累计`() {
        var hidden = false
        var acc = 0f
        // 先滑到临界（190，差一点）。
        listOf(-95f, -95f).forEach { d ->
            val (h, a) = GalleryChromeHidePolicy.afterScroll(hidden, acc, d, threshold)
            hidden = h; acc = a
        }
        assertFalse(hidden)
        // 复位（例如用户双击顶栏回顶）。
        val (rh, ra) = GalleryChromeHidePolicy.reset()
        hidden = rh; acc = ra
        // 再滑一小段：不该因为"上一轮攒了 190"就当场收。
        val (h2, _) = GalleryChromeHidePolicy.afterScroll(hidden, acc, -50f, threshold)
        assertFalse("复位后必须从头累计", h2)
    }
}
