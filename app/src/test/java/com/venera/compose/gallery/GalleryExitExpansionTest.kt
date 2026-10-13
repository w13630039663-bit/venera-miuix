package com.venera.compose.gallery

import com.venera.compose.gallery.domain.EXIT_FLIGHT_FADE_FRACTION
import com.venera.compose.gallery.domain.FlyRect
import com.venera.compose.gallery.domain.GalleryExitFrame
import com.venera.compose.gallery.domain.coverSourceRect
import com.venera.compose.gallery.domain.exitExpansion
import com.venera.compose.gallery.domain.exitPageAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 离场那一趟的算式判据。两件事分开钉：
 *
 * 1. **搬过去没搬坏**：把 `GalleryPostScreen` 里原来那句就地表达式逐字复刻成 [legacy]，
 *    与 [exitExpansion] 在同一批输入上逐位对表（这条只能证"等价"，证不了"正确"）。
 * 2. **这一帧本来该是什么样**：起点/终点逐像素重合、宽度单调、越界被钳住、退化输入不产出负尺寸
 *    —— 这几条与复刻无关，是独立的行为规格（复刻错了它们会红）。
 */
class GalleryExitExpansionTest {

    private data class Legacy(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val srcLeft: Int,
        val srcTop: Int,
        val srcWidth: Int,
        val srcHeight: Int,
        val cornerRadiusScale: Float,
        val pageAlpha: Float,
    )

    /**
     * 复刻自搬走之前的 `GalleryPostScreen`：
     * `drawBehind` 里那四行插值 + `coverSourceRect` + 圆角那一行，以及 `exitFadeAlpha()` 那一句。
     * 刻意**照抄**（连"几何钳进度、alpha 吃原始进度"那处不一致也照抄），否则对表就没有意义。
     */
    private fun legacy(
        progress: Float,
        from: FlyRect,
        to: FlyRect,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): Legacy {
        val t = progress.coerceIn(0f, 1f)
        val left = from.left + (to.left - from.left) * t
        val top = from.top + (to.top - from.top) * t
        val width = from.width + (to.width - from.width) * t
        val height = from.height + (to.height - from.height) * t
        val src = coverSourceRect(bitmapWidth, bitmapHeight, width, height)
        return Legacy(
            left = left,
            top = top,
            width = width,
            height = height,
            srcLeft = src.left,
            srcTop = src.top,
            srcWidth = src.width,
            srcHeight = src.height,
            cornerRadiusScale = (width / to.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
            // 这里**刻意写死字面量**而不是复用 domain 那枚常量：复用的话改动常量两边一起跟着走，
            // 对表就永远绿，验不到"分数还是原来那一枚"。原来 UI 文件里的私有常量就是这个 0.25。
            pageAlpha = (1f - progress / 0.25f).coerceIn(0f, 1f),
        )
    }

    /** 三档形状：竖图收回竖卡、横图收回竖卡（cover 会裁）、宽屏那档落点比起点还宽。 */
    private val cases = listOf(
        Triple(
            FlyRect(left = 28f, top = 120f, width = 1016f, height = 1450f),
            FlyRect(left = 640f, top = 2100f, width = 340f, height = 500f),
            1016 to 1450,
        ),
        Triple(
            FlyRect(left = 0f, top = 96f, width = 1080f, height = 720f),
            FlyRect(left = 720f, top = 180f, width = 180f, height = 260f),
            1080 to 720,
        ),
        Triple(
            FlyRect(left = 100f, top = 100f, width = 600f, height = 900f),
            FlyRect(left = 1400f, top = 2000f, width = 900f, height = 640f),
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

    private fun assertSameFrame(at: Float, from: FlyRect, to: FlyRect, bitmap: Pair<Int, Int>) {
        val frame: GalleryExitFrame = exitExpansion(at, from, to, bitmap.first, bitmap.second)
        val old = legacy(at, from, to, bitmap.first, bitmap.second)
        assertEquals("dst.left @$at", old.left, frame.dst.left, 0f)
        assertEquals("dst.top @$at", old.top, frame.dst.top, 0f)
        assertEquals("dst.width @$at", old.width, frame.dst.width, 0f)
        assertEquals("dst.height @$at", old.height, frame.dst.height, 0f)
        assertEquals("src.left @$at", old.srcLeft, frame.source.left)
        assertEquals("src.top @$at", old.srcTop, frame.source.top)
        assertEquals("src.width @$at", old.srcWidth, frame.source.width)
        assertEquals("src.height @$at", old.srcHeight, frame.source.height)
        assertEquals("圆角缩放 @$at", old.cornerRadiusScale, frame.cornerRadiusScale, 0f)
        assertEquals("页面 alpha @$at", old.pageAlpha, exitPageAlpha(at), 0f)
    }

    // ── 1. 搬过去没搬坏 ──

    @Test
    fun `每一档进度都与搬走之前的就地表达式逐位相同`() {
        for ((from, to, bitmap) in cases) {
            for (p in sweep()) assertSameFrame(p, from, to, bitmap)
        }
    }

    @Test
    fun `越界的进度也与老写法同形`() {
        // 老写法对越界输入的行为：几何钳、alpha 结果钳。这两条都得原样留着。
        for ((from, to, bitmap) in cases) {
            for (p in listOf(-0.5f, -0.001f, 0f, 1f, 1.001f, 1.5f, 4f)) {
                assertSameFrame(p, from, to, bitmap)
            }
        }
    }

    // ── 2. 独立于复刻的行为规格 ──

    @Test
    fun `起点那一帧与画面框逐像素重合`() {
        for ((from, to, bitmap) in cases) {
            val frame = exitExpansion(0f, from, to, bitmap.first, bitmap.second)
            assertEquals(from, frame.dst)
        }
    }

    @Test
    fun `终点那一帧与卡片落点逐像素重合`() {
        // 这一条是"返回落点归位"的机械尺子：收尾不需要任何补偿位移。
        for ((from, to, bitmap) in cases) {
            val frame = exitExpansion(1f, from, to, bitmap.first, bitmap.second)
            assertEquals(to, frame.dst)
            assertEquals("落点那一帧圆角缩放应为 1", 1f, frame.cornerRadiusScale, 0f)
        }
    }

    @Test
    fun `宽度单调走向落点且全程有面积`() {
        // 刻意不说"收缩"：宽屏那一档落点比起点**还宽**（起飞时画面框是窄的），
        // 第一遍这条就咬在自己手上 —— 要钉的是"单调走向落点、不越界、有面积"，不是方向。
        for ((from, to, bitmap) in cases) {
            val lo = minOf(from.width, to.width)
            val hi = maxOf(from.width, to.width)
            var prev = Float.NaN
            for (p in sweep()) {
                val width = exitExpansion(p, from, to, bitmap.first, bitmap.second).dst.width
                assertTrue("宽度跑出起点与落点之间：$width @$p", width >= lo - 1e-4f && width <= hi + 1e-4f)
                assertTrue("宽度出了负数或 0：$width @$p", width > 0f)
                if (!prev.isNaN()) {
                    assertTrue(
                        "宽度没有单调走向落点：$prev -> $width @$p（落点 ${to.width}）",
                        abs(width - to.width) <= abs(prev - to.width) + 1e-4f,
                    )
                }
                prev = width
            }
        }
    }

    @Test
    fun `倒着走回同一进度得到同一帧`() {
        // 批 2 的跟手回弹靠的就是这一条：帧只由进度决定，所以"往回退"不需要另一套算式。
        for ((from, to, bitmap) in cases) {
            val forward = sweep(0.05f).map { exitExpansion(it, from, to, bitmap.first, bitmap.second) }
            val backward = sweep(0.05f).reversed().map {
                exitExpansion(it, from, to, bitmap.first, bitmap.second)
            }
            assertEquals(forward.size, backward.size)
            for (i in forward.indices) {
                val a = forward[i]
                val b = backward[forward.size - 1 - i]
                assertEquals(a.dst, b.dst)
                assertEquals(a.source, b.source)
                assertEquals(a.cornerRadiusScale, b.cornerRadiusScale, 0f)
            }
        }
    }

    @Test
    fun `搬过来的分数还是原来那一枚`() {
        // 与上面那条对表配套：对表用的是字面量，这一条钉常量本身，两边都不许悄悄换数。
        assertEquals(0.25f, EXIT_FLIGHT_FADE_FRACTION, 0f)
    }

    @Test
    fun `页面让开只发生在首程`() {
        assertEquals(1f, exitPageAlpha(0f), 1e-6f)
        assertEquals(0.5f, exitPageAlpha(EXIT_FLIGHT_FADE_FRACTION / 2f), 1e-6f)
        assertEquals(0f, exitPageAlpha(EXIT_FLIGHT_FADE_FRACTION), 1e-6f)
        // 首程走完，后面那一整段页面都已经让开（飞行体自己不许淡，两头才都无缝）。
        for (p in listOf(0.3f, 0.5f, 0.75f, 1f, 1.5f)) {
            assertEquals("progress=$p 之后页面应当已经全透明", 0f, exitPageAlpha(p), 1e-6f)
        }
        // 负进度（回弹过头那一档）与老写法一样钳回全不透明，不外推。
        assertEquals(1f, exitPageAlpha(-0.5f), 1e-6f)
    }

    @Test
    fun `退化输入不产出负尺寸`() {
        // 落点量不到面积（0×0）时：几何仍可插值，源裁切交回整张，圆角缩放被钳在合法区间。
        val frame = exitExpansion(
            progress = 0.5f,
            from = FlyRect(0f, 0f, 1000f, 1000f),
            to = FlyRect(500f, 500f, 0f, 0f),
            sourceWidthPx = 1000,
            sourceHeightPx = 1000,
        )
        assertTrue("宽度出了负数：${frame.dst.width}", frame.dst.width >= 0f)
        assertTrue("高度出了负数：${frame.dst.height}", frame.dst.height >= 0f)
        assertTrue(frame.source.width >= 1 && frame.source.height >= 1)
        assertTrue(frame.cornerRadiusScale in 0f..1f)
    }

    @Test
    fun `位图尺寸拿不到时交回整张而不是崩`() {
        val frame = exitExpansion(0.5f, cases[0].first, cases[0].second, 0, 0)
        assertEquals(1, frame.source.width)
        assertEquals(1, frame.source.height)
    }
}
