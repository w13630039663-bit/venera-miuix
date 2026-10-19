package com.venera.compose.gallery

import com.venera.compose.gallery.domain.EXIT_BODY_FADE_FRACTION
import com.venera.compose.gallery.domain.EXIT_FLIGHT_FADE_FRACTION
import com.venera.compose.gallery.domain.FlyRect
import com.venera.compose.gallery.domain.GalleryExitFrame
import com.venera.compose.gallery.domain.coverSourceRect
import com.venera.compose.gallery.domain.exitBodyAlpha
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
 *    ⚠️ 批 B③（2026-10-19）起**圆角那一格除外**：那一格的锚从"当帧宽 ÷ 终点宽"换成
 *    "当帧宽 ÷ 源块宽"（内容真的被缩放了多少），计划 §5 批 B③ 的原话就是"不再是那一把"。
 *    两者只在"起点与终点同宽"时才相等（[圆角锚两式只在同宽那族下相等] 钉着）。
 *    其余各格（几何 / 源块 / 页面 alpha）仍逐位对表 —— 那一族没动。
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
        // 批 B③：圆角那一格**不再**与老锚对表（锚换了，见本文件头注与
        // `圆角锚两式在同宽族相等异宽族分家`）。对的是 cover 那颗 `scale` 本身 ——
        // 实测（`_probe/l0/probe_scale.kt`）两向恒等，`maxOf` 只为吸收源块取整漂移
        // （源块是 Int，900px 上 1px 就是 1.1e-3，故容差放到 2e-2）。
        assertEquals(
            "圆角补偿 = cover scale @$at",
            frame.dst.width / frame.source.width.toFloat().coerceAtLeast(1f),
            frame.cornerRadiusScale,
            2e-2f,
        )
        assertEquals("页面 alpha @$at", old.pageAlpha, exitPageAlpha(at), 0f)
    }

    // ── 1. 搬过去没搬坏 ──

    /**
     * 批 B③ 的**换锚判据**：新锚（内容缩放比，cover 那颗 `scale`）与旧锚（当帧宽 ÷ 终点宽）
     * 什么场景下相等。**期望值全部来自实测**（`_probe/l0/probe_scale.kt` 一次性量具）：
     *
     * | 场景 | 当帧 | 新式 | 旧式 |
     * |---|---|---|---|
     * | 同宽族 p=0（起点 = 位图） | 1080×1554 | 1.0 | 1.0 |
     * | 同宽族 p=0.1（落点更高 ⇒ 放大） | 1080×1586 | 1.0208 | 1.0 |
     * | 同宽族 p=1 | 1080×1877 | 1.2081 | 1.0 |
     * | 异宽族 p=1（高驱动缩小） | 340×500 | 0.3448 | 1.0 |
     *
     * 真相：旧式 `dst宽 ÷ 终点宽` 在**同宽族里恒为 1**（宽不变）—— 那是"圆角永远不补偿"，
     * 而那一族里内容全程在被**放大**（落点比起点高）⇒ 圆角该跟着放大却纹丝不动，
     * 这正是 §4.4 圆角那一行记的"末段补白"。新式量的是内容缩放比，四档全部正确。
     *
     * 两式**只在起点那一帧**相等（那一刻当帧框就是位图本身，内容没缩放）——
     * 这一笔钉的正是"唯一的重合点"与"其余全程分家"。
     */
    @Test
    fun `圆角锚两式只在起点那一帧重合`() {
        // 同宽族（整屏起点那一族，批 B② 之前唯一的场景）。
        val sameWidth = Triple(
            FlyRect(left = 0f, top = -291f, width = 1080f, height = 1554f),
            FlyRect(left = 0f, top = 100f, width = 1080f, height = 1877f),
            1080 to 1554,
        )
        // ① 起点帧：当帧框 = 位图 ⇒ 内容没缩放 ⇒ 两式都 1 ⇒ 重合。
        run {
            val f = exitExpansion(0f, sameWidth.first, sameWidth.second, sameWidth.third.first, sameWidth.third.second)
            val old = f.dst.width / sameWidth.second.width.coerceAtLeast(1f)
            assertEquals("起点帧两式应重合（内容没缩放）", old, f.cornerRadiusScale, 1e-3f)
            assertEquals(1f, f.cornerRadiusScale, 1e-3f)
        }
        // ② 其余全程：旧式恒 1（同宽），新式跟着内容缩放走 ⇒ 必须分家。
        var diverged = 0
        for (p in sweep()) {
            if (p < 1e-6f) continue
            val f = exitExpansion(p, sameWidth.first, sameWidth.second, sameWidth.third.first, sameWidth.third.second)
            val old = f.dst.width / sameWidth.second.width.coerceAtLeast(1f)
            if (abs(old - f.cornerRadiusScale) > 2e-2f) diverged++
        }
        assertTrue("同宽族除起点外一档都没分家 ⇒ 换锚没生效（$diverged 档）", diverged > 0)

        // ③ 异宽族（批 B② 之后的页块起点）：终点档旧式 = 1（假的"没缩"），新式 = 0.3448（真缩到 34%）。
        val diffWidth = Triple(
            FlyRect(left = 28f, top = 120f, width = 1016f, height = 1450f),
            FlyRect(left = 640f, top = 2100f, width = 340f, height = 500f),
            1016 to 1450,
        )
        val f = exitExpansion(1f, diffWidth.first, diffWidth.second, diffWidth.third.first, diffWidth.third.second)
        val old = f.dst.width / diffWidth.second.width.coerceAtLeast(1f)
        assertEquals("异宽族旧式（当帧宽÷终点宽）", 1f, old, 1e-3f)
        assertEquals("异宽族新式（内容缩放比，实测 0.3448）", 0.3448f, f.cornerRadiusScale, 1e-3f)
        assertTrue(
            "异宽族两式必须分家（相等即证换锚没生效）",
            abs(old - f.cornerRadiusScale) > 0.5f,
        )
    }

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
            // 批 B③：圆角补偿那一格换了口径，不再恒为 1。
            // 旧规格"落点那一帧圆角缩放应为 1"的本意是"收尾不需要补偿"—— 新锚下这件事
            // 变成"内容相对自己那一档缩了多少"：起点与落点同比例那两档仍是 1，
            // 而**落点比起点宽**那一档（宽屏作品）内容是被放大的，补偿 >1（圆角该跟着放大）。
            // 由 `圆角锚两式在同宽族相等异宽族分家` 与下面那一笔分管。
            val expected = maxOf(
                to.width / bitmap.first.toFloat().coerceAtLeast(1f),
                to.height / bitmap.second.toFloat().coerceAtLeast(1f),
            )
            assertEquals("落点那一帧圆角补偿 = 内容缩放比", expected, frame.cornerRadiusScale, 2e-2f)
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
        // 首程走完，后面那一整段页面都已经让开。飞行体自己那一半在 `exitBodyAlpha`
        // 那条判据里：它起步得比这里晚（页面归零那一刻仍是满的），末段才收到 0。
        for (p in listOf(0.3f, 0.5f, 0.75f, 1f, 1.5f)) {
            assertEquals("progress=$p 之后页面应当已经全透明", 0f, exitPageAlpha(p), 1e-6f)
        }
        // 负进度（回弹过头那一档）与老写法一样钳回全不透明，不外推。
        assertEquals(1f, exitPageAlpha(-0.5f), 1e-6f)
    }

    /**
     * 飞行体自己的淡出：**页面早已归零之后**才起步，末段收到 0。
     *
     * 钉三件事：首程恒不透明（那一段屏上只有它）、起步点晚于页面让开的终点（两条曲线不许重叠，
     * 重叠就是"页面和飞行体同时半透明"那种相乘变暗）、越界不回弹。
     */
    @Test
    fun `页面让开之后飞行体才淡，末段归零`() {
        // 与页面那条的归零点严格错开：页面到 0 的那一档，飞行体必须还是满的。
        assertEquals(1f, exitBodyAlpha(EXIT_FLIGHT_FADE_FRACTION), 1e-6f)
        assertEquals(1f, exitBodyAlpha(0f), 1e-6f)
        // 起步点 = 1 - fadeFraction：0.75 那一档 ⇒ 0.25（页面让开的终点）之前恒为 1。
        assertEquals(1f, exitBodyAlpha(1f - EXIT_BODY_FADE_FRACTION), 1e-6f)
        assertEquals(0.5f, exitBodyAlpha(1f - EXIT_BODY_FADE_FRACTION / 2f), 1e-6f)
        assertEquals(0f, exitBodyAlpha(1f), 1e-6f)
        // 弹簧回弹过头与负进度：钳住，不许越界又亮回来。
        assertEquals(0f, exitBodyAlpha(1.5f), 1e-6f)
        assertEquals(1f, exitBodyAlpha(-0.5f), 1e-6f)
        // 整程单调不增（中途翘起来一次就是一次闪）。
        var prev = exitBodyAlpha(0f)
        for (p in sweep(0.01f)) {
            val now = exitBodyAlpha(p)
            assertTrue("progress=$p 处飞行体 alpha 反弹了：$prev → $now", now <= prev + 1e-6f)
            prev = now
        }
    }

    @Test
    fun `飞行体淡出那一段分数钉住`() {
        // 与页面那枚（0.25）是两件事，不许互相借用：页面是"让开"，这是"化进落点"。
        // 0.75 ⇒ 起步点正好落在页面归零那一档（首尾相接、不重叠）。
        assertEquals(0.75f, EXIT_BODY_FADE_FRACTION, 0f)
        assertEquals(
            "飞行体的起步点必须与页面让开的终点相接",
            EXIT_FLIGHT_FADE_FRACTION,
            1f - EXIT_BODY_FADE_FRACTION,
            1e-6f,
        )
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
