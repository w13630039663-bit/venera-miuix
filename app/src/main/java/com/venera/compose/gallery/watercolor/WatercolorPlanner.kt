package com.venera.compose.gallery.watercolor

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 水彩绘制的**阶段**。
 *
 * 顺序就是屏上时间的顺序，也是"一幅画长出来"的叙事顺序：
 * [WASH] 淡彩晕染（大色块打底）→ [BACKGROUND] 背景大笔 → [FORMS] 主体成形 → [DETAILS] 细节与高光。
 * 纸底不在这里：它是渲染器的第一刷（铺满 + 尿点），不是笔触。
 */
enum class WatercolorPhase { WASH, BACKGROUND, FORMS, DETAILS }

/**
 * 一笔水彩笔触。
 *
 * 坐标全部归一化到 [0,1]（与 [WatercolorSource] 同约定）；
 * [width] 与 [bleed] 以「画布短边占比」记，渲染期再换算成像素。
 *
 * @param points 折线点（u,v 成对）。**长度为 1 个点时是一枚"印章"**（圆形色点，
 *   晕染阶段的大色块用它）；否则是笔路径线。
 * @param width 线宽 / 印章直径（画布短边占比）。
 * @param color ARGB。**alpha 分量不用作浓度** —— 每笔浓淡在 [alpha]。
 * @param alpha 单遍不透明度 [0,1]。水彩的深浅靠多遍叠加，不靠单遍浓墨。
 * @param bleed 渗色抖动系数（相对 [width]）：渲染期据此在法向两侧各抖一遍，
 *   落点比中心多刷一两遍 —— 这就是水彩"积边"的成因。
 */
class WatercolorStroke(
    val phase: WatercolorPhase,
    val points: FloatArray,
    val width: Float,
    val color: Int,
    val alpha: Float,
    val bleed: Float,
) {
    val isDab: Boolean get() = points.size == 2

    /** 归一化外包盒写入 [out]（minU,minV,maxU,maxV），供判据抽样，不落入渲染。 */
    fun bounds(out: FloatArray) {
        var minU = 1f; var minV = 1f; var maxU = 0f; var maxV = 0f
        var i = 0
        while (i + 1 < points.size) {
            minU = min(minU, points[i]); maxU = max(maxU, points[i])
            minV = min(minV, points[i + 1]); maxV = max(maxV, points[i + 1])
            i += 2
        }
        out[0] = minU; out[1] = minV; out[2] = maxU; out[3] = maxV
    }
}

/**
 * 一整幅画的施工单：阶段顺序 + 全部笔触 + 纸色。
 *
 * 由 [WatercolorPlanner.plan] 产出，纯数据；渲染与回放都只读它，不改它。
 */
class WatercolorPlan(
    val paperBase: Int,
    val paperTint: Int,
    val seed: Long,
    val strokes: List<WatercolorStroke>,
) {
    /** 阶段出现顺序（与 [strokes] 中的排列一致）。 */
    val phaseOrder: List<WatercolorPhase> = listOf(
        WatercolorPhase.WASH,
        WatercolorPhase.BACKGROUND,
        WatercolorPhase.FORMS,
        WatercolorPhase.DETAILS,
    )

    private val startIndices: Map<WatercolorPhase, Int> = buildMap {
        var acc = 0
        for (phase in phaseOrder) {
            put(phase, acc)
            acc += strokes.count { it.phase == phase }
        }
    }

    /** 某阶段第一笔在 [strokes] 中的下标；没有该阶段时等于总数。 */
    fun phaseStart(phase: WatercolorPhase): Int = startIndices[phase] ?: strokes.size

    /** 画到第 [index] 笔（0 起）时所在阶段。 */
    fun phaseAt(index: Int): WatercolorPhase {
        var cur = phaseOrder.first()
        for (phase in phaseOrder) {
            if (index >= phaseStart(phase)) cur = phase else break
        }
        return cur
    }

    /** 进度 [0,1]。 */
    fun progress(index: Int): Float =
        if (strokes.isEmpty()) 1f else (index.toFloat() / strokes.size).coerceIn(0f, 1f)

    fun countOf(phase: WatercolorPhase): Int = strokes.count { it.phase == phase }
}

/**
 * 水彩规划器：把一张 [WatercolorSource] 变成一份 [WatercolorPlan]。
 *
 * 参考技术是 p5.js 的 p5.brush（stamp 笔触 + bleed 渗色 + ColorField 色块）
 * 与 Tyler Hobbs 的分层半透明罩染法。落到本仓形状上的四条口径：
 *
 * 1. **顺序即叙事** —— 晕染（大色块）→ 背景（长而淡的大笔）→ 主体（中等笔触描形）
 *    → 细节（短而浓的边线 + 纸色高光）。屏上看到的正是"一幅画长出来"。
 * 2. **深浅全靠叠加** —— 单笔 alpha 都低（0.09~0.65），边缘的深是"多刷了几遍"的果，
 *    不是描出来的边线（见 [WatercolorStroke.bleed]）。
 * 3. **笔触顺结构** —— 方向沿等亮度线（梯度法向），与 p5.brush 的 vector field 同理；
 *    细节笔按边缘密度选点，与"哪里该收一笔"对齐。
 * 4. **纸始终在场** —— 每笔颜色都从原图取样但往纸色回收（饱和度压低、明度微抬），
 *    叠加后纸的颗粒从颜料底下透出来。
 *
 * 纯 JVM：不给它 Bitmap，也不给它 Canvas —— 判据要能单测（本仓口径）。
 */
object WatercolorPlanner {

    /** 暖白纸色。故意不用纯白：纯白上罩什么颜色都像贴纸。 */
    private const val PAPER = 0xFFF3EAD8.toInt()

    /** 单幅笔触总数基准（再按画布长宽比微调）。低分辨率也够罩满三四遍。 */
    private const val BASE_STROKES = 3000

    /** 各阶段笔数占比（和为 1）。 */
    private const val WASH_WEIGHT = 0.12f
    private const val BACKGROUND_WEIGHT = 0.30f
    private const val FORMS_WEIGHT = 0.46f
    private const val DETAILS_WEIGHT = 0.12f

    fun plan(source: WatercolorSource, seed: Long): WatercolorPlan {
        val rng = Random(seed)
        val aspect = source.width.toFloat() / source.height
        val total = (BASE_STROKES * sqrt(aspect).coerceIn(0.7f, 1.4f)).roundToInt()
        val strokes = ArrayList<WatercolorStroke>(total + 64)

        val (paperBase, paperTint) = planPaper(source)
        planWash(source, rng, (total * WASH_WEIGHT).roundToInt().coerceAtLeast(24), strokes)
        planBackground(source, rng, (total * BACKGROUND_WEIGHT).roundToInt(), strokes)
        planForms(source, rng, (total * FORMS_WEIGHT).roundToInt(), strokes)
        planDetails(source, rng, (total * DETAILS_WEIGHT).roundToInt().coerceAtLeast(60), strokes)

        return WatercolorPlan(paperBase, paperTint, seed, strokes)
    }

    /**
     * 纸色：暖白打底，按全图平均色调一点倾向。暗图多偏向平均色 ——
     * 否则一张夜景画完会像"黑纸上糊白纸"，纸必须比画色亮一档。
     */
    private fun planPaper(source: WatercolorSource): Pair<Int, Int> {
        val mean = source.meanColor
        val dark = source.meanLuminance < 0.30f
        // 暗图多带一点平均色进纸底，否则纸在一幅夜景里会亮得像打了灯。
        val baseLean = if (dark) 0.38f else 0.22f
        return Pair(
            WatercolorSource.adjust(
                mixArgb(PAPER, mean, baseLean),
                saturationScale = 0.90f,
                valueScale = 1.03f,
            ),
            mixArgb(PAPER, mean, if (dark) 0.35f else 0.12f),
        )
    }

    /** 两色按 t 线性混（ARGB 四通道各自混）。 */
    internal fun mixArgb(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun m(shift: Int) = (WatercolorSource.channel(a, shift) +
            (WatercolorSource.channel(b, shift) - WatercolorSource.channel(a, shift)) * f).roundToInt()
        return WatercolorSource.pack(m(24), m(16), m(8), m(0))
    }

    // ────────────────────────── 晕染 ──────────────────────────

    /**
     * 晕染阶段：按格网取平均色，落成**三层同心圆章**（外淡、中淡、心稍浓）。
     *
     * 三层同心的用意：外环先把这块区域闷上去，心环再给一点实底，
     * 干透后读起来是一块有边界的湿色块，而不是一枚死板的实心圆。
     * 每格 55% 概率再补一枚偏移副章，格与格之间才有活接缝。
     */
    private fun planWash(
        source: WatercolorSource,
        rng: Random,
        count: Int,
        out: MutableList<WatercolorStroke>,
    ) {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val minEdge = min(w, h)
        val cols = max(2, (4f * sqrt(w / h)).roundToInt())
        val rows = max(2, (cols * h / w).roundToInt())
        val cellW = 1f / cols
        val cellH = 1f / rows

        // 格子的走访顺序也进随机源：同种子同构图，换种子换"先画哪块"。
        val order = (0 until cols * rows).shuffled(rng)
        // 格网一轮最多出 cells*2 枚印章，常常够不到预算 —— **循环刷**直到画满：
        // 同一格会落两三枚湿色块（半径/位置/取样都不同），读起来才是"一遍遍罩上去的"。
        var round = 0
        while (out.size < count && round < 8) {
            for (idx in order) {
                if (out.size >= count) break
                val cx = (idx % cols) * cellW + cellW * 0.5f
                val cy = (idx / cols) * cellH + cellH * 0.5f
                val cellRadiusPx = cellW * w * 0.5f
                val radiusPx = cellRadiusPx * (0.62f + rng.nextFloat() * 0.33f)
                val bu = (cx + (rng.nextFloat() - 0.5f) * cellW * 0.6f).coerceIn(0.04f, 0.96f)
                val bv = (cy + (rng.nextFloat() - 0.5f) * cellH * 0.6f).coerceIn(0.04f, 0.96f)
                emitWashBlob(out, source, bu, bv, radiusPx, minEdge)
                if (out.size < count && rng.nextFloat() < 0.55f) {
                    // 副章：横向错开半格、半径小一档，颜色仍在原格附近取。
                    val off = if (bu > 0.5f) -cellW * 0.5f else cellW * 0.5f
                    emitWashBlob(
                        out, source,
                        (bu + off).coerceIn(0.04f, 0.96f),
                        (bv + (rng.nextFloat() - 0.5f) * cellH * 0.4f).coerceIn(0.04f, 0.96f),
                        radiusPx * 0.68f, minEdge,
                    )
                }
            }
            round++
        }
    }

    private fun emitWashBlob(
        out: MutableList<WatercolorStroke>,
        source: WatercolorSource,
        bu: Float,
        bv: Float,
        radiusPx: Float,
        minEdge: Float,
    ) {
        val avg = averageColorInRadius(source, bu, bv, radiusPx * 0.9f)
        // 往纸色回收一点：湿颜料比照片"粉"。
        val color = WatercolorSource.adjust(
            mixArgb(avg, PAPER, 0.16f),
            saturationScale = 0.82f,
            valueScale = 1.04f,
        )
        val phase = WatercolorPhase.WASH
        out.add(WatercolorStroke(phase, floatArrayOf(bu, bv), radiusPx * 2.16f / minEdge, color, 0.10f, 0.5f))
        out.add(WatercolorStroke(phase, floatArrayOf(bu, bv), radiusPx * 1.56f / minEdge, color, 0.09f, 0.5f))
        out.add(WatercolorStroke(phase, floatArrayOf(bu, bv), radiusPx * 1.10f / minEdge, color, 0.12f, 0.5f))
    }

    /** 圆域平均色：中心 + 对角四点采样求均值。 */
    internal fun averageColorInRadius(source: WatercolorSource, bu: Float, bv: Float, radiusPx: Float): Int {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val ru = radiusPx / w
        val rv = radiusPx / h
        val samples = listOf(
            bu to bv,
            (bu - ru) to (bv - rv), (bu + ru) to (bv - rv),
            (bu - ru) to (bv + rv), (bu + ru) to (bv + rv),
        )
        var r = 0f; var g = 0f; var b = 0f
        for ((u, v) in samples) {
            val c = source.sample(u, v)
            r += WatercolorSource.channel(c, 16)
            g += WatercolorSource.channel(c, 8)
            b += WatercolorSource.channel(c, 0)
        }
        val n = samples.size
        return WatercolorSource.pack(255, (r / n).roundToInt(), (g / n).roundToInt(), (b / n).roundToInt())
    }

    // ────────────────────────── 走线公共件 ──────────────────────────

    /**
     * 从 (u,v) 出发沿等亮度线走出一笔，返回 null 表示这一步没收（起点太贴边、走不动）。
     *
     * 走法：初始方向取梯度法向；每步朝当前方向滚一小段（步长在 0.7~1.3 倍间抖），
     * 同时方向本身按 curl Random Walk —— 直线是描、微弯才是"手画的水彩"。
     * 颜色取**沿线均值**而非中点：长笔不会因途中一处高光整体偏色。
     */
    private fun emitStroke(
        source: WatercolorSource,
        rng: Random,
        phase: WatercolorPhase,
        u: Float,
        v: Float,
        targetPx: Float,
        stepPx: Float,
        widthPx: Float,
        alpha: Float,
        curl: Float,
        towardPaper: Float,
        saturationScale: Float,
        valueScale: Float,
    ): WatercolorStroke? {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val minEdge = min(w, h)
        val pts = ArrayList<Float>(12)
        pts.add(u.coerceIn(0.02f, 0.98f))
        pts.add(v.coerceIn(0.02f, 0.98f))

        val grad = FloatArray(2)
        source.gradientDirection(u, v, grad)
        // 等亮度线 = 梯度转 90°：(-gy, gx)。梯度近零的平坦区给一个近水平的随机向。
        val hasGrad = abs(grad[0]) + abs(grad[1]) > 0.05f
        var ang = if (hasGrad) atan2(grad[0], -grad[1]) else (rng.nextFloat() - 0.5f) * 1.2f

        var cu = pts[0]
        var cv = pts[1]
        var walked = 0f
        var guard = 0
        while (walked < targetPx && pts.size < 12 && guard < 24) {
            guard++
            ang += (rng.nextFloat() - 0.5f) * 2f * curl
            val step = stepPx * (0.7f + rng.nextFloat() * 0.6f)
            cu = (cu + cos(ang) * step / w).coerceIn(0.02f, 0.98f)
            cv = (cv + sin(ang) * step / h).coerceIn(0.02f, 0.98f)
            pts.add(cu)
            pts.add(cv)
            walked += step
        }
        if (pts.size < 4) return null

        var r = 0f; var g = 0f; var b = 0f
        var n = 0
        var i = 0
        while (i + 1 < pts.size) {
            val c = source.sample(pts[i], pts[i + 1])
            r += WatercolorSource.channel(c, 16)
            g += WatercolorSource.channel(c, 8)
            b += WatercolorSource.channel(c, 0)
            n++
            i += 2
        }
        var color = WatercolorSource.pack(255, (r / n).roundToInt(), (g / n).roundToInt(), (b / n).roundToInt())
        if (towardPaper > 0f) color = mixArgb(color, PAPER, towardPaper)
        color = WatercolorSource.adjust(color, saturationScale, valueScale)
        val bleed = 0.25f + rng.nextFloat() * 0.35f
        return WatercolorStroke(phase, pts.toFloatArray(), widthPx / minEdge, color, alpha, bleed)
    }

    // ────────────────────────── 背景 ──────────────────────────

    /**
     * 背景阶段：长而淡的大笔，优先落在**平坦区域**（接受概率随边缘强度下降）。
     *
     * 方向取等亮度线：背景是"铺"不是"描"，顺着明暗交界走才不会先把主体轮廓勾出来
     * —— 先描线后填色是卡通画法，不是水彩。
     */
    private fun planBackground(
        source: WatercolorSource,
        rng: Random,
        count: Int,
        out: MutableList<WatercolorStroke>,
    ) {
        val minEdge = min(source.width, source.height).toFloat()
        val base = out.size
        var guard = 0
        while (out.size < base + count && guard < count * 40) {
            guard++
            val u = rng.nextFloat()
            val v = rng.nextFloat()
            if (rng.nextFloat() > 1f - source.edge(u, v) * 0.85f) continue
            emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.BACKGROUND,
                u = u, v = v,
                targetPx = minEdge * (0.30f + rng.nextFloat() * 0.40f),
                stepPx = minEdge * 0.048f,
                widthPx = minEdge * (0.036f + rng.nextFloat() * 0.026f),
                alpha = 0.13f + rng.nextFloat() * 0.06f,
                curl = 0.40f,
                towardPaper = 0.12f,
                saturationScale = 0.92f,
                valueScale = 1.04f,
            )?.let(out::add)
        }
        // 兜底：拒绝采样在极端图（满图强边缘）里可能凑不满。宁可位置不理想，
        // 也不让背景阶段比晕染还短 —— 屏上会看出缺一段。
        while (out.size < base + count) {
            emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.BACKGROUND,
                u = rng.nextFloat(), v = rng.nextFloat(),
                targetPx = minEdge * (0.30f + rng.nextFloat() * 0.40f),
                stepPx = minEdge * 0.048f,
                widthPx = minEdge * (0.036f + rng.nextFloat() * 0.026f),
                alpha = 0.13f + rng.nextFloat() * 0.06f,
                curl = 0.40f,
                towardPaper = 0.12f,
                saturationScale = 0.92f,
                valueScale = 1.04f,
            )?.let(out::add)
        }
    }

    // ────────────────────────── 主体 ──────────────────────────

    /**
     * 主体阶段：中等笔触，**偏挑有结构的点**（接受概率随边缘强度上升）。
     * 大笔在前、小笔在后（本阶段内按目标长度降序发出）——
     * 读起来是"先定大形，再收小形"。
     */
    private fun planForms(
        source: WatercolorSource,
        rng: Random,
        count: Int,
        out: MutableList<WatercolorStroke>,
    ) {
        val minEdge = min(source.width, source.height).toFloat()
        val base = out.size
        data class Pending(val targetPx: Float, val u: Float, val v: Float)
        val pending = ArrayList<Pending>(count)
        var guard = 0
        while (pending.size < count && guard < count * 60) {
            guard++
            val u = rng.nextFloat()
            val v = rng.nextFloat()
            val e = source.edge(u, v)
            if (rng.nextFloat() > 0.45f + 0.55f * e) continue
            pending.add(Pending(minEdge * (0.10f + rng.nextFloat() * 0.22f), u, v))
        }
        while (pending.size < count) {
            pending.add(Pending(minEdge * (0.10f + rng.nextFloat() * 0.22f), rng.nextFloat(), rng.nextFloat()))
        }
        pending.sortByDescending { it.targetPx }
        for (p in pending) {
            emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.FORMS,
                u = p.u, v = p.v,
                targetPx = p.targetPx,
                stepPx = minEdge * 0.035f,
                widthPx = minEdge * (0.015f + rng.nextFloat() * 0.016f),
                alpha = 0.32f + rng.nextFloat() * 0.13f,
                curl = 0.55f,
                towardPaper = 0.06f,
                saturationScale = 1.0f,
                valueScale = 1.0f,
            )?.let(out::add)
        }
        // 极端图下 emitStroke 可能整体落空（起点贴角走不动）——补几笔短的保底，
        // 让主体阶段的量与计划一致（判据数的是计划，不是屏上的运气）。
        var topUp = base + count
        while (out.size < topUp) {
            emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.FORMS,
                u = rng.nextFloat(), v = rng.nextFloat(),
                targetPx = minEdge * (0.10f + rng.nextFloat() * 0.22f),
                stepPx = minEdge * 0.035f,
                widthPx = minEdge * (0.015f + rng.nextFloat() * 0.016f),
                alpha = 0.32f + rng.nextFloat() * 0.13f,
                curl = 0.55f,
                towardPaper = 0.06f,
                saturationScale = 1.0f,
                valueScale = 1.0f,
            ) ?: break // 走不动的点不该无限循环；放行即可（量已在别处兜底）
        }
    }

    // ────────────────────────── 细节 ──────────────────────────

    /**
     * 细节阶段：85% 是**浓而短的边线**（专挑高边缘处，颜色压深、饱和微抬），
     * 15% 是**纸色高光**（挑亮且平的点，用纸色盖一下，把高光"留"出来）。
     * 这一步才让画从"罩染底"变成"看得清东西"。
     */
    private fun planDetails(
        source: WatercolorSource,
        rng: Random,
        count: Int,
        out: MutableList<WatercolorStroke>,
    ) {
        val minEdge = min(source.width, source.height).toFloat()
        val base = out.size
        val edgeCount = (count * 0.85f).roundToInt()
        val highlightCount = count - edgeCount
        var guard = 0
        while (out.size < base + edgeCount && guard < edgeCount * 60) {
            guard++
            val u = rng.nextFloat()
            val v = rng.nextFloat()
            val e = source.edge(u, v)
            val accept = e * e * e * 0.9f + 0.1f
            if (rng.nextFloat() > accept) continue
            emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.DETAILS,
                u = u, v = v,
                targetPx = minEdge * (0.02f + rng.nextFloat() * 0.04f),
                stepPx = minEdge * 0.016f,
                widthPx = minEdge * (0.005f + rng.nextFloat() * 0.006f),
                alpha = 0.45f + rng.nextFloat() * 0.20f,
                curl = 0.9f,
                towardPaper = 0f,
                saturationScale = 1.12f,
                valueScale = 0.82f,
            )?.let(out::add)
        }
        while (out.size < base + edgeCount) {
            emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.DETAILS,
                u = rng.nextFloat(), v = rng.nextFloat(),
                targetPx = minEdge * (0.02f + rng.nextFloat() * 0.04f),
                stepPx = minEdge * 0.016f,
                widthPx = minEdge * (0.005f + rng.nextFloat() * 0.006f),
                alpha = 0.45f + rng.nextFloat() * 0.20f,
                curl = 0.9f,
                towardPaper = 0f,
                saturationScale = 1.12f,
                valueScale = 0.82f,
            ) ?: break
        }
        // 高光：亮且平的像素上盖纸色。落点先按亮度粗筛，避免盲撒。
        var hlGuard = 0
        var hl = 0
        while (hl < highlightCount && hlGuard < highlightCount * 80) {
            hlGuard++
            val u = rng.nextFloat()
            val v = rng.nextFloat()
            if (source.luminance(u, v) < 0.80f) continue
            if (source.edge(u, v) > 0.25f) continue
            emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.DETAILS,
                u = u, v = v,
                targetPx = minEdge * (0.015f + rng.nextFloat() * 0.025f),
                stepPx = minEdge * 0.014f,
                widthPx = minEdge * (0.006f + rng.nextFloat() * 0.006f),
                alpha = 0.30f + rng.nextFloat() * 0.10f,
                curl = 0.8f,
                towardPaper = 0f,
                saturationScale = 0.85f,
                valueScale = 1.06f,
            )?.let { out.add(it); hl++ }
        }
        // 高光挑不满（图里没有大亮面）就用纸色在任意平整处补 ——
        // "留白"是水彩的呼吸，一幅没有高光的罩染会闷。
        while (hl < highlightCount) {
            val s = emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.DETAILS,
                u = rng.nextFloat(), v = rng.nextFloat(),
                targetPx = minEdge * (0.015f + rng.nextFloat() * 0.025f),
                stepPx = minEdge * 0.014f,
                widthPx = minEdge * (0.006f + rng.nextFloat() * 0.006f),
                alpha = 0.30f + rng.nextFloat() * 0.10f,
                curl = 0.8f,
                towardPaper = 0f,
                saturationScale = 0.85f,
                valueScale = 1.06f,
            ) ?: break
            out.add(s)
            hl++
        }
    }
}

