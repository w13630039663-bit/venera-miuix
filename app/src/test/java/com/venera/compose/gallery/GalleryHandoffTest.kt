package com.venera.compose.gallery

import com.venera.compose.gallery.domain.handoffBodyAlpha
import com.venera.compose.gallery.domain.handoffCoverage
import com.venera.compose.gallery.domain.handoffPageAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交棒那一档的判据。要防的**只有一件事**：两层同时半透明。
 *
 * 老写法（页面的淡入挂在去程弹簧的尾段上）就是这么栽的 —— 这里把那条老算式也复刻进来，
 * 用同一个断言把它钉红，这样"再改回去"会在上一层的单测里当场暴露。
 */
class GalleryHandoffTest {

    private fun sweep(step: Float = 0.01f): List<Float> {
        val out = ArrayList<Float>()
        var p = 0f
        while (p <= 1.0001f) {
            out.add(p)
            p += step
        }
        return out
    }

    @Test
    fun `两层叠起来恒为不透明`() {
        for (p in sweep()) {
            val c = handoffCoverage(p)
            assertTrue(
                "progress=$p 时总不透明度只有 $c —— 低于 1 就是图会暗一下（幕布透上来）",
                c >= 1f - 1e-4f,
            )
        }
    }

    @Test
    fun `页面先到位 飞行体后才开始淡出`() {
        // 前半程：页面淡到位（0→1），飞行体必须**还是全不透明**（它盖着图那一块）。
        assertEquals(0f, handoffPageAlpha(0f), 1e-6f)
        assertEquals(1f, handoffPageAlpha(0.5f), 1e-6f)
        assertEquals(1f, handoffPageAlpha(1f), 1e-6f)
        for (p in listOf(0f, 0.1f, 0.25f, 0.4f, 0.5f)) {
            assertEquals(
                "progress=$p 时页面还没铺满，飞行体就不能开始淡（否则又是两层半透明）",
                1f,
                handoffBodyAlpha(p),
                1e-6f,
            )
        }
        // 后半程：页面全不透明，飞行体才淡出。
        assertEquals(0f, handoffBodyAlpha(1f), 1e-6f)
    }

    @Test
    fun `两档都在 0 到 1 之间且单调`() {
        var prevPage = -1f
        var prevBody = 2f
        for (p in sweep()) {
            val page = handoffPageAlpha(p)
            val body = handoffBodyAlpha(p)
            assertTrue("页面 alpha 越界：$page @ $p", page in 0f..1f)
            assertTrue("飞行体 alpha 越界：$body @ $p", body in 0f..1f)
            assertTrue("页面 alpha 不单调：$prevPage -> $page @ $p", page >= prevPage - 1e-6f)
            assertTrue("飞行体 alpha 不单调：$prevBody -> $body @ $p", body <= prevBody + 1e-6f)
            prevPage = page
            prevBody = body
        }
    }

    @Test
    fun `区间外的输入被夹住而不是外推`() {
        assertEquals(0f, handoffPageAlpha(-0.5f), 1e-6f)
        assertEquals(1f, handoffPageAlpha(1.5f), 1e-6f)
        assertEquals(1f, handoffBodyAlpha(-0.5f), 1e-6f)
        assertEquals(0f, handoffBodyAlpha(1.5f), 1e-6f)
    }

    @Test
    fun `栽过的那条老算式会被这组断言钉红`() {
        // 老写法：页面在弹簧尾段淡入、飞行体同窗淡出（同一个 0.25 的窗口）。
        fun oldCoverage(fly: Float): Float {
            val page = ((fly - 0.75f) / 0.25f).coerceIn(0f, 1f)
            val body = ((1f - fly) / 0.25f).coerceIn(0f, 1f)
            return body + (1f - body) * page
        }
        val atMid = oldCoverage(0.875f)
        assertTrue(
            "交叉中点应当掉到 0.75（正是真机上那一下「图暗一下」）—— 实际 $atMid",
            atMid < 0.8f,
        )
        // 而现在的写法在同一个进度上仍然是 1：这一条就是"改回去会红"的凭据。
        assertEquals(1f, handoffCoverage(0.875f), 1e-4f)
    }
}
