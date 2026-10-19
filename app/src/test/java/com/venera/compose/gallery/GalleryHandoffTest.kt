package com.venera.compose.gallery

import com.venera.compose.gallery.domain.HANDOFF_DETAIL_LEAD_FLY
import com.venera.compose.gallery.domain.HANDOFF_DETAIL_MS
import com.venera.compose.gallery.domain.handoffBodyAlpha
import com.venera.compose.gallery.domain.handoffCoverage
import com.venera.compose.gallery.domain.handoffDetailAlpha
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

    // ── 详情内容区那条钟（2026-10-19 · 用户「飞入完成才突然几帧内加载出下面的界面」）──

    @Test
    fun `内容区那头 0 尾 1 单调且不越界`() {
        assertEquals("起步必须是全透明（不然页面还没成形就露字）", 0f, handoffDetailAlpha(0f), 1e-6f)
        assertEquals("走完必须是全不透明", 1f, handoffDetailAlpha(1f), 1e-6f)
        assertEquals(0f, handoffDetailAlpha(-0.4f), 1e-6f)
        assertEquals(1f, handoffDetailAlpha(1.4f), 1e-6f)
        var prev = -1f
        for (p in sweep()) {
            val a = handoffDetailAlpha(p)
            assertTrue("内容区 alpha 越界：$a @ $p", a in 0f..1f)
            assertTrue("内容区 alpha 不单调：$prev -> $a @ $p", a >= prev - 1e-6f)
            prev = a
        }
    }

    @Test
    fun `内容区前重后轻`() {
        // 用户报的是"整块浮上来"：线性淡入中段两成半都不动，读起来就是一整片一起亮。
        // 这一块很密，前段要走在前面（成形），后段慢下来（压实）。
        for (p in listOf(0.2f, 0.3f, 0.5f)) {
            val a = handoffDetailAlpha(p)
            assertTrue("progress=$p 时内容区只有 $a —— 前段没走在前面，中段就是「整块浮上来」", a > p + 0.05f)
        }
        // 但它仍然要在末段收干净，不能"早早到位然后在 1 上停着"（那样末段反而没动静）。
        assertTrue(handoffDetailAlpha(0.9f) < 1f)
    }

    @Test
    fun `内容区那条钟与交棒那条是两条`() {
        // 两条钟的域都是 0..1，但形状不同：交棒那条前半程走完（0→1 用掉整个前 50%），
        // 内容区这条全程走（二次 ease-out）。合回一条的话，用户报的那条顺序感就会回来 ——
        // 所以这里把"两条曲线在同一个输入上必须给出不同的值"钉住。
        for (p in listOf(0.25f, 0.5f, 0.75f)) {
            assertTrue(
                "progress=$p 时两条算式给出了同一个值 —— 说明又合回一条钟了",
                kotlin.math.abs(handoffDetailAlpha(p) - handoffPageAlpha(p)) > 0.01f,
            )
        }
        // 交棒的起点仍是 0：图那一块的 alpha 在落位之前必须恒 0（防"同一张画两份"那条判据不动）。
        assertEquals(0f, handoffPageAlpha(0f), 1e-6f)
    }

    @Test
    fun `内容区必须在飞完之前起手，且不能几帧走完`() {
        // 这两条就是用户 2026-10-19 报的那一条的时间口径：
        // 「点击图片进详情页，他是图片飞入完成才突然几帧内加载出下面的界面不好」。
        //
        // ① 起手点在飞行**进行中**：等于 1 就是"等飞完才起"，正是报的那一条。
        //    也不宜太小（0.5 以下）：那一刻飞行体还在半空，页面先成形会让"从卡片飞过来"读成"图是后补的"。
        assertTrue(
            "起手点必须严格早于飞完，否则又退回「飞完才长出来」",
            HANDOFF_DETAIL_LEAD_FLY > 0.5f && HANDOFF_DETAIL_LEAD_FLY < 1f,
        )
        // ② 走得比交棒那一档长：交棒只有 160ms，内容区跟着它走就还是"几帧之内整块长出来"。
        assertTrue(
            "内容区要走 ${HANDOFF_DETAIL_MS}ms —— 低于 200ms 在人眼里就是「几帧之内」",
            HANDOFF_DETAIL_MS >= 200L,
        )
    }
}
