package com.venera.compose.gallery

import com.venera.compose.gallery.domain.FlyRect
import com.venera.compose.gallery.domain.flightFrame
import com.venera.compose.gallery.domain.coverWindowRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Test

/**
 * 批 B 核心算式 [flightFrame] 的判据（计划 §5 批 B 判据①那六格，逐格一颗）：
 *
 * 1. 两端逐像素重合（t=0 逐像素等于卡上封面、t=1 逐像素等于落点真图的 cover）；
 * 2. 全程等比不产拉伸（唯一一枚 uniformScale，绑定方向逐位相等）；
 * 3. 宽高两向覆盖不足 ⇒ 另一向被裁（cover 语义：溢出只可能在一侧，且裁的是窗、不是压扁画面）；
 * 4. 倒着走回同一帧（进场/离场/跟手四条路共用一颗的前提）；
 * 5. 退化输入不产负尺寸；
 * 6. 圆角两端各自精确落在该档、随路程单调（÷scale 的逆补偿在渲染侧做，见注）。
 *
 * 写法照 `GalleryExitExpansionTest` 那 10 颗：先扫档、再逐位比，扰动用例放最后。
 */
class GalleryFlightFrameTest {

    /** 竖图收回竖卡（比例同档）、横图收回竖卡（cover 会裁）、落点比卡片还宽。 */
    private val cases = listOf(
        Triple(
            FlyRect(left = 64f, top = 444f, width = 373f, height = 355f),
            FlyRect(left = 0f, top = 96f, width = 1080f, height = 1028f),
            373 to 355,
        ),
        Triple(
            FlyRect(left = 20f, top = 800f, width = 240f, height = 520f),
            FlyRect(left = 0f, top = 120f, width = 1080f, height = 720f),
            1080 to 720,
        ),
        Triple(
            FlyRect(left = 700f, top = 180f, width = 180f, height = 260f),
            FlyRect(left = 0f, top = 0f, width = 2400f, height = 4000f),
            600 to 900,
        ),
    )

    private fun sweep(step: Float = 0.01f): List<Float> {
        val out = ArrayList<Float>()
        var p = 0f
        while (p <= 1.0001f) {
            out.add(p)
            p += step
        }
        return out
    }

    // ── 1. 两端逐像素重合 ──

    @Test
    fun `t=0 那一帧与卡上封面逐像素重合`() {
        for ((card, to, img) in cases) {
            val f = flightFrame(0f, card, to, img.first, img.second, 16f, 28f)
            assertEquals(card, f.dst)
            // 起点：窗 = 卡的 cover 窗，等比放大后**恰好**盖满卡框（两向都等 ⇒ 没有可裁的溢出）。
            val win = coverWindowRect(img.first, img.second, card.width, card.height)
            assertEquals(win, f.window)
            assertEquals("起点宽向应逐位盖满", card.width, f.window.width * f.uniformScale, 1e-3f)
            assertEquals("起点高向应逐位盖满", card.height, f.window.height * f.uniformScale, 1e-3f)
        }
    }

    @Test
    fun `t=1 那一帧与落点真图逐像素重合`() {
        for ((card, to, img) in cases) {
            val f = flightFrame(1f, card, to, img.first, img.second, 16f, 28f)
            assertEquals(to, f.dst)
            val win = coverWindowRect(img.first, img.second, to.width, to.height)
            assertEquals(win, f.window)
            assertEquals(to.width, f.window.width * f.uniformScale, 1e-3f)
            assertEquals(to.height, f.window.height * f.uniformScale, 1e-3f)
        }
    }

    // ── 2. 全程等比不产拉伸 ──

    @Test
    fun `每一档都只有一枚 scale 且绑定方向逐位相等`() {
        // 单一标量的定义：画面 = 窗 × 同一枚 uniformScale ⇒ 窗的纵横比在屏上**不变**（不拉伸），
        // 绑定方向恰好盖满（等号），另一向只许溢出被裁（≥），两向都不许盖不满。
        for ((card, to, img) in cases) {
            for (t in sweep()) {
                val f = flightFrame(t, card, to, img.first, img.second, 16f, 28f)
                val sx = f.dst.width / f.window.width
                val sy = f.dst.height / f.window.height
                assertEquals("t=$t 绑定方向与 uniformScale 脱钩", maxOf(sx, sy), f.uniformScale, 1e-3f)
                assertTrue("t=$t 宽向盖不满", f.window.width * f.uniformScale >= f.dst.width - 1e-3f)
                assertTrue("t=$t 高向盖不满", f.window.height * f.uniformScale >= f.dst.height - 1e-3f)
            }
        }
    }

    // ── 3. 两向覆盖 ⇒ 另一向被裁；主体位置走插值不逐帧重解 ──

    @Test
    fun `途中溢出只可能在一向且窗面积单调走向落点`() {
        for ((card, to, img) in cases) {
            var prevW = Float.NaN
            var prevH = Float.NaN
            for (t in sweep()) {
                val f = flightFrame(t, card, to, img.first, img.second, 16f, 28f)
                val overW = f.window.width * f.uniformScale - f.dst.width
                val overH = f.window.height * f.uniformScale - f.dst.height
                assertTrue("t=$t 两向同时溢出（窗画不进框）", overW >= -1e-3f && overH >= -1e-3f)
                assertTrue("t=$t 两向溢出都超过容差（等于窗被压扁）", overW <= 1e-3f || overH <= 1e-3f)
                if (!prevW.isNaN()) {
                    // 窗宽高各自连续单调地走向终点档（插值的定义；跳档 = 逐帧重解回来了）。
                    val winTo = coverWindowRect(img.first, img.second, to.width, to.height)
                    assertTrue("t=$t 窗宽不回落点", abs(f.window.width - winTo.width) <= abs(prevW - winTo.width) + 1e-3f)
                    assertTrue("t=$t 窗高不回落点", abs(f.window.height - winTo.height) <= abs(prevH - winTo.height) + 1e-3f)
                }
                prevW = f.window.width
                prevH = f.window.height
            }
        }
    }

    // ── 4. 倒着走回同一帧 ──

    @Test
    fun `倒着走回同一进度得到同一帧`() {
        for ((card, to, img) in cases) {
            val forward = sweep(0.05f).map { flightFrame(it, card, to, img.first, img.second, 16f, 28f) }
            val backward = sweep(0.05f).reversed().map { flightFrame(it, card, to, img.first, img.second, 16f, 28f) }
            assertEquals(forward.size, backward.size)
            for (i in forward.indices) {
                val a = forward[i]
                val b = backward[forward.size - 1 - i]
                assertEquals(a.dst, b.dst)
                assertEquals(a.window, b.window)
                assertEquals(a.uniformScale, b.uniformScale, 0f)
                assertEquals(a.clipRadius, b.clipRadius, 0f)
            }
        }
    }

    // ── 5. 退化输入 ──

    @Test
    fun `退化输入不产负尺寸也不产NaN`() {
        val inputs = listOf(
            Triple(FlyRect(0f, 0f, 0f, 0f), FlyRect(0f, 0f, 100f, 100f), 0 to 0),
            Triple(FlyRect(0f, 0f, 100f, 100f), FlyRect(0f, 0f, 0f, 0f), 100 to 100),
            Triple(FlyRect(0f, 0f, 100f, 100f), FlyRect(0f, 0f, 100f, 100f), -5 to 50),
        )
        for ((card, to, img) in inputs) {
            for (t in listOf(-1f, 0f, 0.5f, 1f, 2f)) {
                val f = flightFrame(t, card, to, img.first, img.second, 8f, 12f)
                assertTrue("负宽度：${f.dst}", f.dst.width >= 0f)
                assertTrue("负高度：${f.dst}", f.dst.height >= 0f)
                assertTrue("窗出了负数：${f.window}", f.window.width >= 1f && f.window.height >= 1f)
                assertTrue("scale NaN/负：${f.uniformScale}", f.uniformScale.isFinite() && f.uniformScale >= 0f)
                assertTrue("圆角负：${f.clipRadius}", f.clipRadius >= 0f)
            }
        }
    }

    @Test
    fun `越界的expansion钳进两端不外推`() {
        val (card, to, img) = cases[0]
        assertEquals(flightFrame(0f, card, to, img.first, img.second, 16f, 28f), flightFrame(-0.5f, card, to, img.first, img.second, 16f, 28f))
        assertEquals(flightFrame(1f, card, to, img.first, img.second, 16f, 28f), flightFrame(1.5f, card, to, img.first, img.second, 16f, 28f))
    }

    // ── 6. 圆角 ──

    @Test
    fun `圆角两端各自精确落在该档且随路程单调`() {
        // 注：计划那一格写的是「lerp(两档) ÷ 当帧 uniformScale」—— 除号是**渲染侧**的事：
        // pixez 的封面层走 graphicsLayer 缩放，半径要预乘逆补偿；我们的渲染件在屏幕空间
        // clip 圆角矩形 + drawImage（窗→dst），没有缩放层，逆补偿天然约掉。
        // 所以这里钉的是"档位与单调"，渲染件那颗尺（批 B 的 UI 侧）再钉"画出的半径 = clipRadius"。
        for ((card, to, img) in cases) {
            assertEquals(16f, flightFrame(0f, card, to, img.first, img.second, 16f, 28f).clipRadius, 1e-4f)
            assertEquals(28f, flightFrame(1f, card, to, img.first, img.second, 16f, 28f).clipRadius, 1e-4f)
            var prev = flightFrame(0f, card, to, img.first, img.second, 16f, 28f).clipRadius
            for (t in sweep(0.05f)) {
                val now = flightFrame(t, card, to, img.first, img.second, 16f, 28f).clipRadius
                assertTrue("圆角 t=$t 反弹：$prev → $now", now >= prev - 1e-6f)
                prev = now
            }
        }
    }

    // ── 扰动三笔（照 GalleryExitExpansionTest 的流程）：同一输入连打两次必须全等 ──

    @Test
    fun `同输入重放全等且浮点扰动下不跳档`() {
        for ((card, to, img) in cases) {
            val a = flightFrame(0.42f, card, to, img.first, img.second, 16f, 28f)
            val b = flightFrame(0.42f, card, to, img.first, img.second, 16f, 28f)
            assertEquals(a, b)
            val c = flightFrame(0.4200001f, card, to, img.first, img.second, 16f, 28f)
            assertEquals(a.dst.left, c.dst.left, 1e-2f)
            assertEquals(a.window.width, c.window.width, 1e-2f)
            assertEquals(a.uniformScale, c.uniformScale, 1e-3f)
        }
    }
}
