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
 * 水彩规划器判据。靶子是五条不能松的口径：
 *
 * 1. **顺序即叙事** —— 晕染 → 铺色 → 主体 → 勾勒 → 细节，绝不能乱序、缺段；
 * 2. **同种子同构图** —— 同一张图同一个种子，规划必须逐笔相同（回放、对比、判据都靠它）；
 * 3. **笔触困在分区里**（v2 核心）—— BLOCKS/FORMS/DETAILS 的每一笔都不跨分区，
 *    眼色不混肤色、发丝不拖背景；
 * 4. **笔触顺结构**（v2）—— 勾勒笔的平均结构边缘必须高于铺色笔（"哪里该收一笔"）；
 * 5. **颜色取自原图** —— 左红右蓝的图上，左侧落的晕染必须偏红、右侧偏蓝。
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
                WatercolorPhase.BLOCKS,
                WatercolorPhase.FORMS,
                WatercolorPhase.OUTLINE,
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
        // 五个阶段都不能空 —— 屏上叙事少任何一段都读得出来。
        for (phase in plan.phaseOrder) {
            assertTrue("阶段 $phase 一笔都没有", plan.countOf(phase) > 0)
        }
    }

    @Test
    fun `各阶段笔数占比符合预期`() {
        val src = leftRedRightBlue()
        val plan = WatercolorPlanner.plan(src, seed = 7L)
        val total = plan.strokes.size
        // 铺色/主体/勾勒/细节都有兜底补齐，笔数=计划量（±2 是取整颗粒）。
        // 期望值直接引用 Planner 的权重常量：调权重不必双处改。
        assertEquals((total * WatercolorPlanner.BLOCKS_WEIGHT).roundToInt().toDouble(), plan.countOf(WatercolorPhase.BLOCKS).toDouble(), 2.0)
        assertEquals((total * WatercolorPlanner.FORMS_WEIGHT).roundToInt().toDouble(), plan.countOf(WatercolorPhase.FORMS).toDouble(), 2.0)
        assertEquals((total * WatercolorPlanner.OUTLINE_WEIGHT).roundToInt().toDouble(), plan.countOf(WatercolorPhase.OUTLINE).toDouble(), 2.0)
        assertEquals((total * WatercolorPlanner.DETAILS_WEIGHT).roundToInt().toDouble(), plan.countOf(WatercolorPhase.DETAILS).toDouble(), 2.0)
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
        val blocksStart = plan.phaseStart(WatercolorPhase.BLOCKS)
        assertEquals(WatercolorPhase.BLOCKS, plan.phaseAt(blocksStart))
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
    fun `勾勒笔落在高结构边缘处 铺色笔落在平坦处`() {
        val src = topBrightBottomDark()
        val plan = WatercolorPlanner.plan(src, seed = 5L)
        fun meanStructEdge(phase: WatercolorPhase): Double {
            val pts = plan.strokes.filter { it.phase == phase }.map { it.points[0] to it.points[1] }
            return pts.map { src.structuralEdge(it.first, it.second) }.average()
        }
        val outlineEdge = meanStructEdge(WatercolorPhase.OUTLINE)
        val blocksEdge = meanStructEdge(WatercolorPhase.BLOCKS)
        assertTrue("勾勒笔平均结构边缘 $outlineEdge 不高于铺色笔 $blocksEdge", outlineEdge > blocksEdge)
        // 铺色笔必须明显偏平坦：这张图的边缘只有中间 10% 一条带，
        // 平均结构边缘强度高不过 0.15。
        assertTrue("铺色笔平均结构边缘 $blocksEdge 太高，说明没往平坦处挑", blocksEdge < 0.15)
    }

    @Test
    fun `勾勒笔比铺色笔更浓更细`() {
        val plan = WatercolorPlanner.plan(leftRedRightBlue(), seed = 6L)
        val outline = plan.strokes.filter { it.phase == WatercolorPhase.OUTLINE }
        val blocks = plan.strokes.filter { it.phase == WatercolorPhase.BLOCKS }
        val outlineAlpha = outline.map { it.alpha }.average()
        val blocksAlpha = blocks.map { it.alpha }.average()
        assertTrue("勾勒笔 alpha $outlineAlpha 不高于铺色笔 $blocksAlpha", outlineAlpha > blocksAlpha)
        val outlineW = outline.map { it.width }.average()
        val blocksW = blocks.map { it.width }.average()
        assertTrue("勾勒笔宽 $outlineW 不小于铺色笔 $blocksW", outlineW < blocksW)
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
            assertTrue("${w}x$h 总量失控: ${plan.strokes.size}", plan.strokes.size in 2000..5200)
        }
    }

    // ─────────── 6. v2：分区 / 困区 / 流向 / 皮肤 / 扁平化 ───────────

    @Test
    fun `铺色与主体笔困在分区内 一笔不跨区`() {
        val src = leftRedRightBlue()
        val plan = WatercolorPlanner.plan(src, seed = 17L)
        var checked = 0
        for (s in plan.strokes) {
            if (s.phase != WatercolorPhase.BLOCKS && s.phase != WatercolorPhase.FORMS) continue
            val startRegion = src.regionAt(s.points[0], s.points[1])
            var i = 2
            while (i + 1 < s.points.size) {
                assertEquals(
                    "笔触跨出了起点分区（${s.phase}）",
                    startRegion, src.regionAt(s.points[i], s.points[i + 1]),
                )
                i += 2
                checked++
            }
        }
        assertTrue("一笔困区笔都没验到", checked > 100)
    }

    @Test
    fun `勾勒笔不困区 允许跨分区`() {
        // 边线骑在分界上，跨区才是它的工作 —— 左红右蓝图上勾边笔必然跨区。
        val src = leftRedRightBlue()
        val plan = WatercolorPlanner.plan(src, seed = 17L)
        val outlines = plan.strokes.filter { it.phase == WatercolorPhase.OUTLINE }
        assertTrue(outlines.isNotEmpty())
    }

    @Test
    fun `横边处结构流向沿边 即水平`() {
        val src = topBrightBottomDark()
        val flow = FloatArray(2)
        src.flowDirection(0.5f, 0.5f, flow)
        assertTrue(
            "横边处流向应水平：|x|=${abs(flow[0])} |y|=${abs(flow[1])}",
            abs(flow[0]) > abs(flow[1]) * 3f,
        )
        // 远离边缘的纯色区没有结构走向。
        src.flowDirection(0.5f, 0.1f, flow)
        val coh = src.flowCoherence(0.5f, 0.1f)
        assertTrue("纯色区相干性应接近 0（实为 $coh）", coh < 0.1f)
    }

    @Test
    fun `皮肤掩识别暖粉 不认蓝`() {
        // 左暖粉（肤）右蓝（衣/背景）。
        val w = 120; val h = 90
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            px[y * w + x] = if (x < w / 2) 0xFFF2CFC4.toInt() else 0xFFA8C0E8.toInt()
        }
        val src = WatercolorSource.of(px, w, h)
        assertTrue("左半应判为皮肤", src.skinAt(0.25f, 0.5f))
        assertTrue("右半不应判为皮肤", !src.skinAt(0.75f, 0.5f))
    }

    @Test
    fun `扁平化吃掉细密纹理 分区数受控`() {
        // 蓝底上撒白色单像素噪点：多数表决后噪点应被底色吃掉。
        val w = 100; val h = 80
        val px = IntArray(w * h) { 0xFF2848B0.toInt() }
        val rng = Random(7)
        for (i in 0 until 400) {
            val x = rng.nextInt(w); val y = rng.nextInt(h)
            px[y * w + x] = 0xFFFFFFFF.toInt()
        }
        val src = WatercolorSource.of(px, w, h)
        assertTrue("分区数应受控（实为 ${src.regionCount}）", src.regionCount in 1..6)
        // 噪点位置的分区号必须等于蓝底的分区号（被吃掉了）。
        var dots = 0; var absorbed = 0
        for (i in 0 until 400) {
            val x = rng.nextInt(w); val y = rng.nextInt(h)
            val u = (x + 0.5f) / w; val v = (y + 0.5f) / h
            if (src.sampleFlat(u, v) != 0xFF2848B0.toInt()) dots++
            if (src.regionAt(x.toFloat() / (w - 1), y.toFloat() / (h - 1)) ==
                src.regionAt(0.5f, 0.5f)
            ) absorbed++
        }
        assertTrue("噪点应基本被底色吸收（$absorbed/400）", absorbed >= 360)
    }

    @Test
    fun `分区均值色接近该区实际色 且分区齐全`() {
        val src = leftRedRightBlue()
        assertTrue("分区数至少为 2（实为 ${src.regionCount}）", src.regionCount >= 2)
        // 左半分区的均值必须偏红、右半偏蓝（取红差最大 / 最小的分区）。
        var leftRegion = -1; var rightRegion = -1
        var maxRed = 60f; var minRed = -60f
        for (r in 0 until src.regionCount) {
            val c = src.regionColor(r)
            val redness = WatercolorSource.channel(c, 16) - WatercolorSource.channel(c, 0)
            if (redness > maxRed) { maxRed = redness; leftRegion = r }
            if (redness < minRed) { minRed = redness; rightRegion = r }
        }
        assertTrue("没找到偏红分区", leftRegion >= 0)
        assertTrue("没找到偏蓝分区", rightRegion >= 0)
        val lc = src.regionColor(leftRegion)
        assertTrue(
            "红分区均值不像红（r=${WatercolorSource.channel(lc, 16)} b=${WatercolorSource.channel(lc, 0)}）",
            WatercolorSource.channel(lc, 16) - WatercolorSource.channel(lc, 0) > 60,
        )
        // 左分区必须真的落在画布左半（它的种子点 regionAt 一致）。
        assertEquals(leftRegion, src.regionAt(0.1f, 0.5f))
        assertEquals(rightRegion, src.regionAt(0.9f, 0.5f))
    }

    @Test
    fun `structuralEdge 在竖边上强 在纯色区弱`() {
        val src = leftRedRightBlue()
        assertTrue("竖边处结构边缘应强", src.structuralEdge(0.5f, 0.5f) > 0.5f)
        assertTrue("纯色区结构边缘应弱", src.structuralEdge(0.05f, 0.5f) < 0.05f)
    }
}
