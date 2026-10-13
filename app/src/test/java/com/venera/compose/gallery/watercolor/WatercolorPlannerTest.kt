package com.venera.compose.gallery.watercolor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 水彩规划器判据。靶子是四条不能松的口径：
 *
 * 1. **顺序即叙事** —— 晕染 → 背景 → 主体 → 细节，绝不能乱序、缺段；
 * 2. **同种子同构图** —— 同一张图同一个种子，规划必须逐笔相同（回放、对比、判据都靠它）；
 * 3. **笔触顺结构** —— 细节笔的平均边缘强度必须高于背景笔（"哪里该收一笔"的判据）；
 * 4. **颜色取自原图** —— 左红右蓝的图上，左侧落的晕染必须偏红、右侧偏蓝。
 */
class WatercolorPlannerTest {

    // ─────────── 合成图的小工厂 ───────────

    /** 左红右蓝（中间 6% 是渐变过渡），用来验"颜色取自原图"。 */
    private fun leftRedRightBlue(w: Int = 160, h: Int = 120): WatercolorSource {
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val t = x.toFloat() / w
                px[y * w + x] = if (t < 0.47f) 0xFFC03028.toInt()
                else if (t > 0.53f) 0xFF2848B0.toInt()
                else {
                    // 过渡带：红蓝按位置线性混，制造一道竖边。
                    val k = (t - 0.47f) / 0.06f
                    mix(0xFFC03028.toInt(), 0xFF2848B0.toInt(), k)
                }
            }
        }
        return WatercolorSource.of(px, w, h)
    }

    /** 上半亮下半暗（横边在下半处），用来验梯度方向与细节选点。 */
    private fun topBrightBottomDark(w: Int = 160, h: Int = 120): WatercolorSource {
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val lum = if (y < h * 0.45f || y > h * 0.55f) {
                if (y < h * 0.5f) 0xF2 else 0x28
            } else {
                // 过渡带 10 行
                val k = (y - h * 0.45f) / (h * 0.10f)
                (0xF2 - (0xF2 - 0x28) * k).roundToInt()
            }
            val c = WatercolorSource.pack(255, lum, lum, lum)
            for (x in 0 until w) px[y * w + x] = c
        }
        return WatercolorSource.of(px, w, h)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = (
            WatercolorSource.channel(a, shift) +
                (WatercolorSource.channel(b, shift) - WatercolorSource.channel(a, shift)) * f
            ).roundToInt()
        return WatercolorSource.pack(ch(24), ch(16), ch(8), ch(0))
    }

    // ─────────── 1. 阶段顺序与结构 ───────────

    @Test
    fun `阶段顺序固定且笔触按阶段分组排列`() {
        val plan = WatercolorPlanner.plan(leftRedRightBlue(), seed = 42L)
        assertEquals(
            listOf(
                WatercolorPhase.WASH,
                WatercolorPhase.BACKGROUND,
                WatercolorPhase.FORMS,
                WatercolorPhase.DETAILS,
            ),
            plan.phaseOrder,
        )
        // 分组：每个阶段的笔触必须在自己的 [start, start+count) 区间内连续出现。
        var seenOrder = mutableListOf<WatercolorPhase>()
        for (s in plan.strokes) {
            if (seenOrder.lastOrNull() != s.phase) seenOrder.add(s.phase)
        }
        assertEquals(plan.phaseOrder, seenOrder)
        // 四个阶段都不能空 —— 屏上叙事少任何一段都读得出来。
        for (phase in plan.phaseOrder) {
            assertTrue("阶段 $phase 一笔都没有", plan.countOf(phase) > 0)
        }
    }

    @Test
    fun `各阶段笔数占比符合预期`() {
        val src = leftRedRightBlue()
        val plan = WatercolorPlanner.plan(src, seed = 7L)
        val total = plan.strokes.size
        // 背景与细节、主体都有兜底补齐，笔数=计划量（±2 是晕染按"枚"取整的颗粒）。
        assertEquals((total * 0.30f).roundToInt().toDouble(), plan.countOf(WatercolorPhase.BACKGROUND).toDouble(), 2.0)
        assertEquals((total * 0.46f).roundToInt().toDouble(), plan.countOf(WatercolorPhase.FORMS).toDouble(), 2.0)
        assertEquals((total * 0.12f).roundToInt().toDouble(), plan.countOf(WatercolorPhase.DETAILS).toDouble(), 2.0)
        assertTrue("晕染量太小：${plan.countOf(WatercolorPhase.WASH)}", plan.countOf(WatercolorPhase.WASH) >= 24)
        // 阶段边界彼此不重叠、递增。
        val starts = plan.phaseOrder.map { plan.phaseStart(it) }
        assertEquals(starts.sorted(), starts)
        assertEquals(total, plan.phaseStart(plan.phaseOrder.last()) + plan.countOf(plan.phaseOrder.last()))
    }

    @Test
    fun `progress 与 phaseAt 的边界读数`() {
        val plan = WatercolorPlanner.plan(leftRedRightBlue(), seed = 3L)
        assertEquals(WatercolorPhase.WASH, plan.phaseAt(0))
        val bgStart = plan.phaseStart(WatercolorPhase.BACKGROUND)
        assertEquals(WatercolorPhase.BACKGROUND, plan.phaseAt(bgStart))
        assertEquals(WatercolorPhase.DETAILS, plan.phaseAt(plan.strokes.size - 1))
        assertEquals(0f, plan.progress(0), 1e-4f)
        assertEquals(1f, plan.progress(plan.strokes.size), 1e-4f)
    }

    // ─────────── 2. 确定性 ───────────

    @Test
    fun `同种子同构图 换种子换构图`() {
        val src = leftRedRightBlue()
        val a = WatercolorPlanner.plan(src, seed = 99L)
        val b = WatercolorPlanner.plan(src, seed = 99L)
        val c = WatercolorPlanner.plan(src, seed = 100L)
        assertEquals(a.strokes.size, b.strokes.size)
        for (i in a.strokes.indices) {
            val sa = a.strokes[i]; val sb = b.strokes[i]
            assertEquals(sa.phase, sb.phase)
            assertTrue(sa.points.contentEquals(sb.points))
            assertEquals(sa.width, sb.width, 0f)
            assertEquals(sa.color, sb.color)
            assertEquals(sa.alpha, sb.alpha, 0f)
            assertEquals(sa.bleed, sb.bleed, 0f)
        }
        assertEquals(a.paperBase, b.paperBase)
        assertEquals(a.paperTint, b.paperTint)
        // 换种子：构图必须变（点数、颜色、纸色扰动至少一变）。
        val anyDiff = a.strokes.size != c.strokes.size ||
            a.strokes.indices.any { !a.strokes[it].points.contentEquals(c.strokes[it].points) }
        assertTrue("换种子后构图一模一样", anyDiff)
    }

    // ─────────── 3. 笔触顺结构 ───────────

    @Test
    fun `所有笔触都落在画布内`() {
        val plan = WatercolorPlanner.plan(leftRedRightBlue(), seed = 11L)
        for (s in plan.strokes) {
            var i = 0
            while (i + 1 < s.points.size) {
                assertTrue("u 越界: ${s.points[i]}", s.points[i] in 0f..1f)
                assertTrue("v 越界: ${s.points[i + 1]}", s.points[i + 1] in 0f..1f)
                i += 2
            }
        }
    }

    @Test
    fun `细节笔落在高边缘处 背景笔落在平坦处`() {
        val src = topBrightBottomDark()
        val plan = WatercolorPlanner.plan(src, seed = 5L)
        fun meanEdge(phase: WatercolorPhase): Double {
            val pts = plan.strokes.filter { it.phase == phase }.map { it.points[0] to it.points[1] }
            return pts.map { src.edge(it.first, it.second) }.average()
        }
        val detailEdge = meanEdge(WatercolorPhase.DETAILS)
        val bgEdge = meanEdge(WatercolorPhase.BACKGROUND)
        assertTrue("细节笔平均边缘 $detailEdge 不高于背景笔 $bgEdge", detailEdge > bgEdge)
        // 背景笔必须明显偏平坦：这张图的边缘只有中间 10% 一条带，
        // 平均边缘强度高不过 0.15。
        assertTrue("背景笔平均边缘 $bgEdge 太高，说明没往平坦处挑", bgEdge < 0.15)
    }

    @Test
    fun `细节笔比背景笔更浓更细`() {
        val plan = WatercolorPlanner.plan(leftRedRightBlue(), seed = 6L)
        val detail = plan.strokes.filter { it.phase == WatercolorPhase.DETAILS }
        val bg = plan.strokes.filter { it.phase == WatercolorPhase.BACKGROUND }
        val detailAlpha = detail.map { it.alpha }.average()
        val bgAlpha = bg.map { it.alpha }.average()
        assertTrue("细节笔 alpha $detailAlpha 不高于背景笔 $bgAlpha", detailAlpha > bgAlpha)
        val detailW = detail.map { it.width }.average()
        val bgW = bg.map { it.width }.average()
        assertTrue("细节笔宽 $detailW 不小于背景笔 $bgW", detailW < bgW)
    }

    @Test
    fun `晕染是圆章 主体是折线`() {
        val plan = WatercolorPlanner.plan(leftRedRightBlue(), seed = 8L)
        assertTrue(plan.strokes.filter { it.phase == WatercolorPhase.WASH }.all { it.isDab })
        assertTrue(plan.strokes.filter { it.phase != WatercolorPhase.WASH }.none { it.isDab })
        // 折线至少两点（4 个 Float）：一笔只有一起点等于没画。
        assertTrue(plan.strokes.filter { !it.isDab }.all { it.points.size >= 4 })
    }

    // ─────────── 4. 颜色取自原图 ───────────

    @Test
    fun `左红右蓝的图上 晕染偏左红右蓝`() {
        val src = leftRedRightBlue()
        val plan = WatercolorPlanner.plan(src, seed = 21L)
        val wash = plan.strokes.filter { it.phase == WatercolorPhase.WASH }
        fun redness(c: Int) = WatercolorSource.channel(c, 16) - WatercolorSource.channel(c, 0)
        val left = wash.filter { it.points[0] < 0.4f }
        val right = wash.filter { it.points[0] > 0.6f }
        assertTrue(left.isNotEmpty() && right.isNotEmpty())
        val leftAvg = left.map { redness(it.color) }.average()
        val rightAvg = right.map { redness(it.color) }.average()
        assertTrue("左半晕染平均红蓝差 $leftAvg 不够红", leftAvg > 30)
        assertTrue("右半晕染平均红蓝差 $rightAvg 不够蓝", rightAvg < -30)
    }

    @Test
    fun `暗图的纸底依然亮 且带暖色`() {
        // 全黑图：纸必须还是亮暖白，不能跟着一起闷。
        val px = IntArray(64 * 64) { 0xFF101014.toInt() }
        val src = WatercolorSource.of(px, 64, 64)
        val plan = WatercolorPlanner.plan(src, seed = 1L)
        val lum = WatercolorSource.luminanceOf(plan.paperBase)
        // 纸允许被暗图带闷一点，但必须仍然明显是一张纸（也比原图亮一大截）。
        assertTrue("暗图纸底亮度 $lum 太低", lum > 0.55f)
        assertTrue(
            "纸底没比原图亮出一大截（纸 $lum / 原图 ${src.meanLuminance}）",
            lum - src.meanLuminance > 0.4f,
        )
        val r = WatercolorSource.channel(plan.paperBase, 16)
        val b = WatercolorSource.channel(plan.paperBase, 0)
        assertTrue("纸底不暖（r=$r b=$b）", r > b)
    }

    @Test
    fun `高光笔比细节笔更亮更淡`() {
        // 上亮（0.95）下暗（0.10）：高光只可能在亮半场生成，浓边笔只可能在暗半场浓起来。
        val px = IntArray(160 * 120)
        val half = 120 * 60
        for (i in px.indices) {
            val lum = if (i < half) 0.95f else 0.10f
            val c = (lum * 255).roundToInt().coerceIn(0, 255)
            px[i] = WatercolorSource.pack(255, c, c, c)
        }
        val plan = WatercolorPlanner.plan(WatercolorSource.of(px, 160, 120), seed = 13L)
        val details = plan.strokes.filter { it.phase == WatercolorPhase.DETAILS }
        val lumOf = { c: Int -> WatercolorSource.luminanceOf(c) }
        // 高光笔的指纹：色亮（纸色）+ alpha 落在最低那一档（0.30~0.40）。
        val highlights = details.filter { lumOf(it.color) > 0.6 && it.alpha < 0.42 }
        val dim = details.filter { lumOf(it.color) < 0.5 }
        assertTrue("细节阶段没有高光笔", highlights.isNotEmpty())
        assertTrue("细节阶段没有浓边笔", dim.isNotEmpty())
        assertTrue(
            "高光笔平均亮度不高于浓边笔",
            highlights.map { lumOf(it.color) }.average() > dim.map { lumOf(it.color) }.average(),
        )
        assertTrue(
            "高光笔不比浓边笔淡",
            highlights.map { it.alpha }.average() < dim.map { it.alpha }.average(),
        )
    }

    // ─────────── 5. Source 自身性质 ───────────

    @Test
    fun `竖边图的梯度沿 x 方向`() {
        val src = leftRedRightBlue()
        val dir = FloatArray(2)
        src.gradientDirection(0.5f, 0.5f, dir)
        assertTrue("竖边处 |gx|=${abs(dir[0])} 应远大于 |gy|=${abs(dir[1])}", abs(dir[0]) > abs(dir[1]) * 3f)
        // 远离边缘的纯色区梯度接近零向量。
        src.gradientDirection(0.05f, 0.05f, dir)
        assertTrue("纯色区梯度不接近零", abs(dir[0]) + abs(dir[1]) < 0.05f)
    }

    @Test
    fun `横边图的梯度沿 y 方向`() {
        val src = topBrightBottomDark()
        val dir = FloatArray(2)
        src.gradientDirection(0.5f, 0.5f, dir)
        assertTrue("横边处 |gy|=${abs(dir[1])} 应远大于 |gx|=${abs(dir[0])}", abs(dir[1]) > abs(dir[0]) * 3f)
    }

    @Test
    fun `sample 是双线性 越界夹住`() {
        val src = leftRedRightBlue()
        // 过渡带中心（u=0.5）应拿到红蓝之间的色：r 与 b 都应有相当份量。
        val c = src.sample(0.5f, 0.5f)
        assertTrue(WatercolorSource.channel(c, 16) > 30 && WatercolorSource.channel(c, 0) > 30)
        // 越界不抛，值等于边缘取值。
        val l = src.sample(-5f, -5f)
        assertEquals(src.colorAt(0, 0), l)
        val r = src.sample(5f, 5f)
        assertEquals(src.colorAt(src.width - 1, src.height - 1), r)
    }

    @Test
    fun `adjust 保持 hue 且能压暗`() {
        val red = 0xFFC03028.toInt()
        val dark = WatercolorSource.adjust(red, valueScale = 0.5f)
        val lr = WatercolorSource.luminanceOf(red)
        val ld = WatercolorSource.luminanceOf(dark)
        assertTrue("压暗后不暗 ($lr -> $ld)", ld < lr * 0.6f)
        // 仍是红主导。
        assertTrue(WatercolorSource.channel(dark, 16) > WatercolorSource.channel(dark, 0))
    }

    @Test
    fun `plan 对大图与极端长宽比都不抛且总量受控`() {
        for ((w, h) in listOf(64 to 64, 640 to 200, 200 to 640, 331 to 477)) {
            val px = IntArray(w * h) { i ->
                // 简单噪声图，保证有梯度可依。
                val v = (i * 31 + (i / w) * 17) % 256
                WatercolorSource.pack(255, v, (v * 3) % 256, (255 - v))
            }
            val plan = WatercolorPlanner.plan(WatercolorSource.of(px, w, h), seed = 2L)
            assertTrue("${w}x$h 总量失控: ${plan.strokes.size}", plan.strokes.size in 1200..4400)
        }
    }
}
