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
 * [WASH] 湿晕（铺氛围）→ [BLOCKS] 铺色块（分区定结构）→ [FORMS] 主体（沿结构走向的中笔）
 * → [OUTLINE] 勾勒（强结构边缘的细线）→ [DETAILS] 细节（高饱和保色 + 纸色高光）。
 *
 * v2 起中间多了 [BLOCKS] 与 [OUTLINE]：一幅认得出东西的水彩画，
 * 靠的是"色块先锁结构、细线再锁轮廓"，而不是靠细节笔随机撒。
 */
enum class WatercolorPhase { WASH, BLOCKS, FORMS, OUTLINE, DETAILS }

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
        WatercolorPhase.BLOCKS,
        WatercolorPhase.FORMS,
        WatercolorPhase.OUTLINE,
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
 * 与 Tyler Hobbs 的分层半透明罩染法。落到本仓形状上的五条口径：
 *
 * 1. **顺序即叙事** —— 湿晕 → 色块锁结构 → 沿流向的中笔成形 → 轮廓细线 → 细节保色。
 * 2. **深浅全靠叠加** —— 单笔 alpha 都低（0.09~0.65），边缘的深是"多刷了几遍"的果。
 * 3. **笔触困在分区里走**（v2 核心）—— 一笔从哪个分区出发就在哪个分区结束：
 *    眼里的金橙不会混进脸颊的粉，头发丝不会拖出背景的蓝。
 * 4. **笔触顺结构**（v2）—— 方向取结构张量次特征向量（发丝/褶皱走向）或结构边缘的切线，
 *    不再拿逐像素梯度法向当流向（纹理上一律旋涡）。
 * 5. **纸始终在场** —— 湿晕与色块的颜料往纸色回收一点，成形与细节不回收，保住原色。
 *
 * 纯 JVM：不给它 Bitmap，也不给它 Canvas —— 判据要能单测（本仓口径）。
 */
object WatercolorPlanner {

    /** 暖白纸色。故意不用纯白：纯白上罩什么颜色都像贴纸。 */
    private const val PAPER = 0xFFF3EAD8.toInt()

    /** 单幅笔触总数基准（再按画布长宽比微调）。 */
    private const val BASE_STROKES = 3600

    /** 各阶段笔数占比（和为 1）。BLOCKS/FORMS 是大头：结构与成形优先于氛围。
     *  OUTLINE 0.26：本图的重度线稿是结构命脉（眼线、发际、颌线、海报框），
     *  EDGE_TRACE 连续走线不会碎 —— 少了它五官立不住。
     *  internal：占比判据直接引用这些常量，调权重不再双处改。 */
    internal const val WASH_WEIGHT = 0.08f
    internal const val BLOCKS_WEIGHT = 0.27f
    internal const val FORMS_WEIGHT = 0.27f
    internal const val OUTLINE_WEIGHT = 0.26f
    internal const val DETAILS_WEIGHT = 0.12f

    fun plan(source: WatercolorSource, seed: Long): WatercolorPlan {
        val rng = Random(seed)
        val aspect = source.width.toFloat() / source.height
        val total = (BASE_STROKES * sqrt(aspect).coerceIn(0.7f, 1.4f)).roundToInt()
        val strokes = ArrayList<WatercolorStroke>(total + 64)

        val (paperBase, paperTint) = planPaper(source)
        planWash(source, rng, (total * WASH_WEIGHT).roundToInt().coerceAtLeast(24), strokes)
        planBlocks(source, rng, (total * BLOCKS_WEIGHT).roundToInt(), strokes)
        planForms(source, rng, (total * FORMS_WEIGHT).roundToInt(), strokes)
        planOutline(source, rng, (total * OUTLINE_WEIGHT).roundToInt(), strokes)
        planDetails(source, rng, (total * DETAILS_WEIGHT).roundToInt(), strokes)

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
        // 亮图少带：淡彩图的亮部（脸、白裙）本来就接近纸色，纸再往平均色靠
        // 就彻底分不出纸与色 —— 纸保持暖白，让画的冷白/粉白有对比可读。
        val baseLean = if (dark) 0.34f else 0.12f
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

    // ────────────────────────── 湿晕 ──────────────────────────

    /**
     * 湿晕阶段：按格网取平均色，落成**三层同心圆章**（外淡、中淡、心稍浓）。
     *
     * 三层同心的用意：外环先把这块区域闷上去，心环再给一点实底，
     * 干透后读起来是一块有边界的湿色块，而不是一枚死板的实心圆。
     * 每格 55% 概率再补一枚偏移副章，格与格之间才有活接缝。
     * v2：颜色从扁平图取（格纹、水印字在这里已被分区吃掉）。
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

    /** 圆域平均色：中心 + 对角四点采样求均值（扁平色，v2）。 */
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
            val c = source.sampleFlat(u, v)
            r += WatercolorSource.channel(c, 16)
            g += WatercolorSource.channel(c, 8)
            b += WatercolorSource.channel(c, 0)
        }
        val n = samples.size
        return WatercolorSource.pack(255, (r / n).roundToInt(), (g / n).roundToInt(), (b / n).roundToInt())
    }

    // ────────────────────────── 走线公共件 ──────────────────────────

    /** 初始方向来源。EDGE_TRACE = 咬住结构边走（每步向局部法向回正）。 */
    private enum class DirMode { GRADIENT, FLOW, STRUCT_GRADIENT, RANDOM, EDGE_TRACE }

    /** 走线取色来源：RAW = 原图，FLAT = 扁平图（保分区纯色），REGION = 起点分区均值。 */
    private enum class ColorMode { RAW, FLAT, REGION }

    /**
     * 从 (u,v) 出发走出一笔，返回 null 表示这一步没收（起点太贴边、走不动）。
     *
     * 走法：初始方向按 [dirMode]；每步朝当前方向滚一小段（步长在 0.7~1.3 倍间抖），
     * 同时方向本身按 curl Random Walk —— 直线是描、微弯才是"手画的水彩"。
     * 颜色取**沿线均值**而非中点：长笔不会因途中一处高光整体偏色。
     *
     * v2 新增两件事：
     * - **困区**（confine）：一笔从哪个分区出发就在哪个分区结束 ——
     *   跨区那一刻收笔。眼色不混肤色、发丝不拖背景，全靠这条。
     * - **FLAT 取色**：颜色从扁平图取，水印字、格纹这类像素噪声进不了笔。
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
        dirMode: DirMode = DirMode.GRADIENT,
        colorMode: ColorMode = ColorMode.RAW,
        confine: Boolean = false,
        jitter: Float = 0f,
        reorient: Float = 0f,
        stopEdge: Float = 0f,
    ): WatercolorStroke? {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val minEdge = min(w, h)
        val pts = ArrayList<Float>(12)
        var cu = u.coerceIn(0.02f, 0.98f)
        var cv = v.coerceIn(0.02f, 0.98f)
        pts.add(cu)
        pts.add(cv)

        val grad = FloatArray(2)
        val flow = FloatArray(2)
        var ang = when (dirMode) {
            DirMode.GRADIENT -> {
                source.gradientDirection(cu, cv, grad)
                val has = abs(grad[0]) + abs(grad[1]) > 0.05f
                if (has) atan2(grad[0], -grad[1]) else (rng.nextFloat() - 0.5f) * 1.2f
            }
            DirMode.FLOW -> {
                source.flowDirection(cu, cv, flow)
                val has = abs(flow[0]) + abs(flow[1]) > 0.1f
                if (has) atan2(flow[1], flow[0]) else rng.nextFloat() * Math.PI.toFloat() * 2f
            }
            DirMode.STRUCT_GRADIENT, DirMode.EDGE_TRACE -> {
                source.structuralGradientDirection(cu, cv, grad)
                val has = abs(grad[0]) + abs(grad[1]) > 0.05f
                if (has) atan2(grad[0], -grad[1]) else rng.nextFloat() * Math.PI.toFloat() * 2f
            }
            DirMode.RANDOM -> rng.nextFloat() * Math.PI.toFloat() * 2f
        }

        val startRegion = source.regionAt(cu, cv)
        var walked = 0f
        var guard = 0
        while (walked < targetPx && pts.size < 16 && guard < 28) {
            guard++
            // 咬线走线：每步先朝局部结构法向回正一笔，再抖 —— 笔咬住边线走，
            // 不会飘进平坦区；落到边没了（stopEdge）就收。
            if (dirMode == DirMode.EDGE_TRACE) {
                source.structuralGradientDirection(cu, cv, grad)
                if (abs(grad[0]) + abs(grad[1]) > 0.05f) {
                    val nAng = atan2(grad[0], -grad[1])
                    var diff = nAng - ang
                    while (diff > Math.PI) diff -= Math.PI.toFloat() * 2f
                    while (diff < -Math.PI) diff += Math.PI.toFloat() * 2f
                    ang += diff * reorient
                }
            }
            ang += (rng.nextFloat() - 0.5f) * 2f * curl
            val step = stepPx * (0.7f + rng.nextFloat() * 0.6f)
            val nu = (cu + cos(ang) * step / w).coerceIn(0.02f, 0.98f)
            val nv = (cv + sin(ang) * step / h).coerceIn(0.02f, 0.98f)
            if (stopEdge > 0f && source.structuralEdge(nu, nv) < stopEdge) break
            if (confine && source.regionAt(nu, nv) != startRegion) break // 困区：跨区即收
            cu = nu; cv = nv
            pts.add(cu)
            pts.add(cv)
            walked += step
        }
        if (pts.size < 4) return null

        var r = 0f; var g = 0f; var b = 0f
        var n = 0
        if (colorMode == ColorMode.REGION) {
            val rc = source.regionColor(startRegion)
            r = WatercolorSource.channel(rc, 16)
            g = WatercolorSource.channel(rc, 8)
            b = WatercolorSource.channel(rc, 0)
            n = 1
        } else {
            var i = 0
            while (i + 1 < pts.size) {
                val c = when (colorMode) {
                    ColorMode.RAW -> source.sample(pts[i], pts[i + 1])
                    ColorMode.FLAT -> source.sampleFlat(pts[i], pts[i + 1])
                }
                r += WatercolorSource.channel(c, 16)
                g += WatercolorSource.channel(c, 8)
                b += WatercolorSource.channel(c, 0)
                n++
                i += 2
            }
        }
        var color = WatercolorSource.pack(255, (r / n).roundToInt(), (g / n).roundToInt(), (b / n).roundToInt())
        if (jitter > 0f) {
            // 分区内轻微色抖：同区笔触不完全同色，才有"一遍遍罩"的笔笔分明。
            color = WatercolorSource.pack(
                255,
                (WatercolorSource.channel(color, 16) + (rng.nextFloat() - 0.5f) * 2f * jitter).roundToInt().coerceIn(0, 255),
                (WatercolorSource.channel(color, 8) + (rng.nextFloat() - 0.5f) * 2f * jitter).roundToInt().coerceIn(0, 255),
                (WatercolorSource.channel(color, 0) + (rng.nextFloat() - 0.5f) * 2f * jitter).roundToInt().coerceIn(0, 255),
            )
        }
        if (towardPaper > 0f) color = mixArgb(color, PAPER, towardPaper)
        color = WatercolorSource.adjust(color, saturationScale, valueScale)
        val bleed = 0.25f + rng.nextFloat() * 0.35f
        return WatercolorStroke(phase, pts.toFloatArray(), widthPx / minEdge, color, alpha, bleed)
    }

    // ────────────────────────── 铺色块（v2） ──────────────────────────

    /**
     * 铺色块阶段：**按分区**把结构锁住。
     *
     * 每个分区按像素占比分预算（小区保底 2 笔，眼睛大小的金子区也有笔），
     * 种子点在区内拒绝采样，笔触**困在区内**、沿结构走向走，
     * 颜色取分区均值加微抖。宽笔画、低浓度 —— 读起来是"湿纸上一块块落色"。
     *
     * 这是 v2 的认知核心：先有分区色块，后面所有阶段都只是在块上找形，
     * 而不是从一张白纸开始用三千根随机线"码"一张画。
     */
    private fun planBlocks(
        source: WatercolorSource,
        rng: Random,
        count: Int,
        out: MutableList<WatercolorStroke>,
    ) {
        val minEdge = min(source.width, source.height).toFloat()
        val totalPx = source.width * source.height
        val base = out.size
        if (source.regionCount == 0 || count <= 0) return
        val regions = (0 until source.regionCount).sortedByDescending { source.regionPixelCount(it) }

        // 预算分配：∝ 像素占比，小区保底 2 笔；总量强制收敛到 count。
        val alloc = IntArray(source.regionCount)
        var assigned = 0
        for (r in regions) {
            val share = source.regionPixelCount(r).toFloat() / totalPx
            alloc[r] = max(2, (count * share).roundToInt())
            assigned += alloc[r]
        }
        while (assigned > count) {
            val r = regions.maxByOrNull { alloc[it] } ?: break
            if (alloc[r] <= 2) break
            alloc[r]--; assigned--
        }
        while (assigned < count) {
            val r = regions.maxByOrNull { alloc[it] } ?: break
            alloc[r]++; assigned++
        }

        for (r in regions) {
            val areaFrac = source.regionPixelCount(r).toFloat() / totalPx
            // 大区笔宽、小区笔窄：宽 ∝ 面积开根。
            var widthBase = minEdge * (0.016f + 0.052f * sqrt(areaFrac.coerceAtMost(0.45f) * 2.2f))
            // 深色区（海报框、线稿）退场：面积大但只铺一层淡影，
            // 形状留给勾勒阶段的细线。不压的话黑框被铺成四条死黑带。
            val rcLum = WatercolorSource.luminanceOf(source.regionColor(r))
            val dark = rcLum < 0.45f
            if (dark) widthBase *= 0.25f
            // 阴影自适应：暗区不主动加深（淡影即可），亮区不提。
            val shadowVal = if (dark) 0.92f else 1.02f
            for (i in 0 until alloc[r]) {
                var u = rng.nextFloat()
                var v = rng.nextFloat()
                var guard = 0
                while (source.regionAt(u, v) != r && guard < 80) {
                    u = rng.nextFloat(); v = rng.nextFloat(); guard++
                }
                if (source.regionAt(u, v) != r) continue
                emitStroke(
                    source = source, rng = rng,
                    phase = WatercolorPhase.BLOCKS,
                    u = u, v = v,
                    targetPx = minEdge * (0.16f + rng.nextFloat() * 0.20f),
                    stepPx = minEdge * 0.060f,
                    widthPx = widthBase * (0.72f + rng.nextFloat() * 0.62f),
                    alpha = if (dark) 0.06f + rng.nextFloat() * 0.04f
                    else 0.24f + rng.nextFloat() * 0.14f,
                    curl = 0.30f,
                    towardPaper = 0f,
                    saturationScale = if (dark) 1.06f else 1.14f,
                    valueScale = shadowVal,
                    dirMode = DirMode.FLOW,
                    colorMode = ColorMode.REGION,
                    confine = true,
                    jitter = 5f,
                ).let { s -> if (s != null) out.add(s) }
            }
        }
        // 极端图下困区走不动可能不满额 —— 重试着补（仍困区；走不动的点换随机点重试，
        // 尝试有上限，绝不空转）。契约：BLOCKS/FORMS 任何一笔都不跨分区。
        var attempts = 0
        val attemptCap = max(64, count * 6)
        while (out.size < base + count && attempts < attemptCap) {
            attempts++
            val s = emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.BLOCKS,
                u = rng.nextFloat(), v = rng.nextFloat(),
                targetPx = minEdge * (0.08f + rng.nextFloat() * 0.12f),
                stepPx = minEdge * 0.050f,
                widthPx = minEdge * (0.018f + rng.nextFloat() * 0.03f),
                alpha = 0.16f + rng.nextFloat() * 0.10f,
                curl = 0.30f,
                towardPaper = 0.05f,
                saturationScale = 0.98f,
                valueScale = 1.02f,
                dirMode = DirMode.RANDOM,
                colorMode = ColorMode.FLAT,
                confine = true,
            ) ?: continue
            out.add(s)
        }
    }

    // ────────────────────────── 主体（v2） ──────────────────────────

    /**
     * 主体阶段：沿**结构走向**的中笔，困区、FLAT 取色。
     *
     * 接受概率随结构相干性上升（有走向的地方多走几笔）。皮肤区单独一档：
     * 密度必须给够（脸的结构就靠这层半透明色顶起来），笔偏宽、alpha 偏低、
     * curl 小 —— 脸上要的是细腻过渡，不是糙笔触。
     * 大笔在前、小笔在后（按目标长度降序发出）：先定大形，再收小形。
     */
    private fun planForms(
        source: WatercolorSource,
        rng: Random,
        count: Int,
        out: MutableList<WatercolorStroke>,
    ) {
        val minEdge = min(source.width, source.height).toFloat()
        val base = out.size
        data class Pending(val targetPx: Float, val u: Float, val v: Float, val skin: Boolean)
        val pending = ArrayList<Pending>(count)
        var guard = 0
        while (pending.size < count && guard < count * 60) {
            guard++
            val u = rng.nextFloat()
            val v = rng.nextFloat()
            val coh = source.flowCoherence(u, v)
            val skin = source.skinAt(u, v)
            // 皮肤接受率给够：脸只占全图一成，0.18 的接受率下脸上只剩
            // 二十几笔，画完是一张白脸 —— 五官全挂在勾勒线上，没有成形。
            var accept = if (skin) 0.42f else 0.28f + 0.52f * coh
            // 深色区（线稿、水印、暗部）主体笔退场：深色归勾勒阶段的咬线笔管，
            // 主体笔在深色区里打转正是"深蓝碎笔"的成因。
            if (source.flatLuminance(u, v) < 0.35f) accept *= 0.20f
            if (rng.nextFloat() > accept) continue
            pending.add(Pending(minEdge * (0.10f + rng.nextFloat() * 0.20f), u, v, skin))
        }
        while (pending.size < count) {
            pending.add(Pending(minEdge * (0.10f + rng.nextFloat() * 0.20f), rng.nextFloat(), rng.nextFloat(), false))
        }
        pending.sortByDescending { it.targetPx }
        for (p in pending) {
            emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.FORMS,
                u = p.u, v = p.v,
                targetPx = p.targetPx,
                stepPx = minEdge * 0.034f,
                widthPx = minEdge * (
                    if (p.skin) 0.016f + rng.nextFloat() * 0.012f
                    else 0.013f + rng.nextFloat() * 0.014f
                    ),
                alpha = if (p.skin) 0.26f + rng.nextFloat() * 0.12f else 0.34f + rng.nextFloat() * 0.14f,
                curl = if (p.skin) 0.35f else 0.55f,
                towardPaper = 0f,
                // 苍白色 HSV 提饱和救不回来（S 本来就 0.14），必须同时压明度：
                // 脸比纸暗半档、粉过半档，五官区才从纸里浮出来。
                saturationScale = if (p.skin) 1.28f else 1.12f,
                valueScale = if (p.skin) 0.95f else 1.0f,
                dirMode = DirMode.FLOW,
                // RAW 沿线平均：BLOCKS 已经铺了扁平色块打底，这里要把原图
                // 细微的明暗渐变（眼窝、鼻影、颌线）带回来 —— 用分区均值就只剩
                // 一片死白，脸没有体积。平均本身会化掉格纹/水印的点状噪点。
                colorMode = ColorMode.RAW,
                confine = true,
            )?.let(out::add)
        }
        // 困区 + 拒绝采样双从下，走不动的点就换点重试；量靠兜底保证，尝试有上限。
        var attempts = 0
        val attemptCap = max(64, count * 6)
        while (out.size < base + count && attempts < attemptCap) {
            attempts++
            val s = emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.FORMS,
                u = rng.nextFloat(), v = rng.nextFloat(),
                targetPx = minEdge * (0.10f + rng.nextFloat() * 0.20f),
                stepPx = minEdge * 0.034f,
                widthPx = minEdge * (0.013f + rng.nextFloat() * 0.014f),
                alpha = 0.34f + rng.nextFloat() * 0.14f,
                curl = 0.55f,
                towardPaper = 0.02f,
                saturationScale = 1.10f,
                valueScale = 1.0f,
                dirMode = DirMode.RANDOM,
                colorMode = ColorMode.FLAT,
                confine = true,
            ) ?: continue
            out.add(s)
        }
    }

    // ────────────────────────── 勾勒（v2） ──────────────────────────

    /**
     * 勾勒阶段：**咬住结构边线**走的细线，深色、低浓度。
     *
     * 三点关键：
     * 1. 挑点门槛高（结构边缘 > 0.42）—— 半透明水印字对比度低，过不了门槛；
     *    海报框、人物轮廓、线稿这些高对比结构才描。
     * 2. 走法是 EDGE_TRACE：每步朝局部结构法向回正，边一直咬住，
     *    边没了（< 0.22）就收笔 —— 描出来的是一条连续线，不是碎笔。
     * 3. 皮肤区内只接强边（> 0.5）：眼线、嘴线留着，脸颊上的碎边不描。
     */
    private fun planOutline(
        source: WatercolorSource,
        rng: Random,
        count: Int,
        out: MutableList<WatercolorStroke>,
    ) {
        val minEdge = min(source.width, source.height).toFloat()
        val base = out.size
        var guard = 0
        while (out.size < base + count && guard < count * 60) {
            guard++
            val u = rng.nextFloat()
            val v = rng.nextFloat()
            // 外缘 9% 带内不描线：海报外框贴着纸边，全描上去会得到一道
            // 死黑的框还吃光勾勒预算。纸边就是画框。
            if (u < 0.09f || u > 0.91f || v < 0.09f || v > 0.91f) continue
            val e = source.structuralEdge(u, v)
            // 皮肤区接强边：眼线、唇线、发际留着，脸颊上的碎边不描。
            // 0.44 是实测线稿的边强平台值（眼区 31% 的点过线）。
            if (source.skinAt(u, v) && e < 0.44f) continue
            if (e < 0.42f) continue
            // 强势边几乎全收：线稿是结构命脉，斜坡只做门槛附近的柔化，
            // 不再把 25% 的过线率稀释成 7%（那会让全图只剩几十根线）。
            val accept = ((e - 0.42f) / 0.18f).coerceIn(0.15f, 1f)
            if (rng.nextFloat() > accept) continue
            emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.OUTLINE,
                u = u, v = v,
                targetPx = minEdge * (0.07f + rng.nextFloat() * 0.09f),
                stepPx = minEdge * 0.014f,
                widthPx = minEdge * (0.004f + rng.nextFloat() * 0.003f),
                alpha = 0.30f + rng.nextFloat() * 0.20f,
                curl = 0.12f,
                towardPaper = 0f,
                saturationScale = 1.12f,
                valueScale = 0.45f,
                dirMode = DirMode.EDGE_TRACE,
                colorMode = ColorMode.FLAT,
                reorient = 0.45f,
                stopEdge = 0.22f,
            )?.let(out::add)
        }
        var attempts = 0
        val attemptCap = max(64, count * 6)
        while (out.size < base + count && attempts < attemptCap) {
            attempts++
            val u = rng.nextFloat()
            val v = rng.nextFloat()
            if (u < 0.06f || u > 0.94f || v < 0.06f || v > 0.94f) continue
            val s = emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.OUTLINE,
                u = u, v = v,
                targetPx = minEdge * (0.07f + rng.nextFloat() * 0.09f),
                stepPx = minEdge * 0.014f,
                widthPx = minEdge * (0.004f + rng.nextFloat() * 0.003f),
                alpha = 0.30f + rng.nextFloat() * 0.20f,
                curl = 0.12f,
                towardPaper = 0f,
                saturationScale = 1.12f,
                valueScale = 0.45f,
                dirMode = DirMode.EDGE_TRACE,
                colorMode = ColorMode.FLAT,
                reorient = 0.45f,
                stopEdge = 0.22f,
            ) ?: continue
            out.add(s)
        }
    }

    // ────────────────────────── 细节（v2） ──────────────────────────

    /**
     * 细节阶段：**保色**优先于一切。
     *
     * 60% 高饱和笔（chroma > 0.38）：眼里的金橙、背包的粉、樱桃的红——
     * 这些是识别度的命脉，用本区纯色、微提饱和、困区小笔重描一遍。
     * 40% 纸色高光：挑亮且平的地方盖纸色，把高光"留"出来。
     * v2 起细节笔不再满图撒深色碎线（那正是"碎笔触"的元凶）。
     */
    private fun planDetails(
        source: WatercolorSource,
        rng: Random,
        count: Int,
        out: MutableList<WatercolorStroke>,
    ) {
        val minEdge = min(source.width, source.height).toFloat()
        val vividCount = (count * 0.60f).roundToInt()
        val highlightCount = count - vividCount

        // 保色笔：高饱和 + 有结构的点。按 chroma² 加权 —— 金眼睛、粉背包
        // 这种高彩点几乎每抽必中，低彩点靠缘分，保色笔自然堆到识别度要害上。
        // 计数含重描笔：金眼重描不额外吃总量，占比契约才守得住。
        var vividDone = 0
        var guard = 0
        while (vividDone < vividCount && guard < vividCount * 80) {
            guard++
            val u = rng.nextFloat()
            val v = rng.nextFloat()
            val ch = source.chroma(u, v)
            if (ch < 0.38f) continue
            val chAccept = ((ch - 0.38f) / 0.45f).coerceIn(0.1f, 1f)
            if (rng.nextFloat() > chAccept * (0.30f + 0.70f * source.flowCoherence(u, v))) continue
            val s = emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.DETAILS,
                u = u, v = v,
                targetPx = minEdge * (0.015f + rng.nextFloat() * 0.035f),
                stepPx = minEdge * 0.014f,
                widthPx = minEdge * (0.005f + rng.nextFloat() * 0.004f),
                alpha = 0.32f + rng.nextFloat() * 0.20f,
                curl = 0.50f,
                towardPaper = 0f,
                saturationScale = 1.10f,
                valueScale = 1.02f,
                dirMode = DirMode.FLOW,
                colorMode = ColorMode.FLAT,
                confine = true,
            ) ?: continue
            out.add(s)
            vividDone++
            // 极高彩（金眼睛这个量级）重描一笔：识别度命脉不能只靠
            // 一次拒绝采样的一笔 —— 同一簇上再来一笔，金色才抱成团。
            if (ch > 0.55f) {
                val s2 = emitStroke(
                    source = source, rng = rng,
                    phase = WatercolorPhase.DETAILS,
                    u = u, v = v,
                    targetPx = minEdge * (0.012f + rng.nextFloat() * 0.025f),
                    stepPx = minEdge * 0.012f,
                    widthPx = minEdge * (0.004f + rng.nextFloat() * 0.003f),
                    alpha = 0.36f + rng.nextFloat() * 0.16f,
                    curl = 0.45f,
                    towardPaper = 0f,
                    saturationScale = 1.12f,
                    valueScale = 1.05f,
                    dirMode = DirMode.FLOW,
                    colorMode = ColorMode.FLAT,
                    confine = true,
                )
                if (s2 != null) {
                    out.add(s2)
                    vividDone++
                }
            }
        }
        // 保色兜底：图里高彩点不够（低饱和原图）。放弃困区，
        // 至少让细节阶段仍有颜色笔在场，不把活全甩给纸色高光。
        var vattempts = 0
        val vCap = max(64, vividCount * 6)
        while (vividDone < vividCount && vattempts < vCap) {
            vattempts++
            val su = rng.nextFloat()
            val sv = rng.nextFloat()
            // 低彩区多数跳过（保色是细节阶段的正事），但留三成保量 ——
            // 灰度线稿类的图也要有细节笔在场，不能整个阶段放空。
            if (source.chroma(su, sv) < 0.22f && rng.nextFloat() < 0.7f) continue
            val s = emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.DETAILS,
                u = su, v = sv,
                targetPx = minEdge * (0.015f + rng.nextFloat() * 0.030f),
                stepPx = minEdge * 0.014f,
                widthPx = minEdge * (0.005f + rng.nextFloat() * 0.003f),
                alpha = 0.30f + rng.nextFloat() * 0.15f,
                curl = 0.50f,
                towardPaper = 0f,
                saturationScale = 1.06f,
                valueScale = 1.02f,
                dirMode = DirMode.RANDOM,
                colorMode = ColorMode.FLAT,
                confine = false,
            ) ?: continue
            out.add(s)
        }

        // 高光：亮且平的扁平像素上盖纸色。
        var hlGuard = 0
        var hl = 0
        while (hl < highlightCount && hlGuard < highlightCount * 80) {
            hlGuard++
            val u = rng.nextFloat()
            val v = rng.nextFloat()
            // 皮肤不是高光：脸又亮又平滑，曾是纸色笔的重灾区 ——
            // 半透明纸白一遍遍罩上去，FORMS 攒下的粉全被洗掉，脸只剩纸色。
            if (source.skinAt(u, v)) continue
            val c = source.sampleFlat(u, v)
            if (WatercolorSource.luminanceOf(c) < 0.85f) continue
            if (source.structuralEdge(u, v) > 0.22f) continue
            val stroke = emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.DETAILS,
                u = u, v = v,
                targetPx = minEdge * (0.015f + rng.nextFloat() * 0.025f),
                stepPx = minEdge * 0.014f,
                widthPx = minEdge * (0.006f + rng.nextFloat() * 0.006f),
                alpha = 0.26f + rng.nextFloat() * 0.10f,
                curl = 0.8f,
                towardPaper = 0f,
                saturationScale = 0.85f,
                valueScale = 1.06f,
                dirMode = DirMode.RANDOM,
                colorMode = ColorMode.FLAT,
            )
            if (stroke != null) {
                // 盖的是纸色：把颜色换成 paperTint 调的纸白。
                out.add(
                    WatercolorStroke(
                        WatercolorPhase.DETAILS,
                        stroke.points,
                        stroke.width,
                        mixArgb(stroke.color, PAPER, 0.55f),
                        stroke.alpha,
                        stroke.bleed,
                    ),
                )
                hl++
            }
        }
        // 高光挑不满（图里没有大亮面）就用纸色任意处补 ——
        // "留白"是水彩的呼吸，一幅没有高光的罩染会闷。
        var hAttempts = 0
        val hCap = max(64, highlightCount * 6)
        while (hl < highlightCount && hAttempts < hCap) {
            hAttempts++
            // 兜底也避开皮肤：脸上的红润是高光之外的东西，不能拿纸白盖。
            val hu = rng.nextFloat(); val hv = rng.nextFloat()
            if (source.skinAt(hu, hv)) continue
            val s = emitStroke(
                source = source, rng = rng,
                phase = WatercolorPhase.DETAILS,
                u = hu, v = hv,
                targetPx = minEdge * (0.015f + rng.nextFloat() * 0.025f),
                stepPx = minEdge * 0.014f,
                widthPx = minEdge * (0.006f + rng.nextFloat() * 0.006f),
                alpha = 0.26f + rng.nextFloat() * 0.10f,
                curl = 0.8f,
                towardPaper = 0f,
                saturationScale = 0.85f,
                valueScale = 1.06f,
                dirMode = DirMode.RANDOM,
                colorMode = ColorMode.FLAT,
                confine = false,
            ) ?: continue
            out.add(
                WatercolorStroke(
                    WatercolorPhase.DETAILS, s.points, s.width,
                    mixArgb(s.color, PAPER, 0.55f), s.alpha, s.bleed,
                ),
            )
            hl++
        }
    }
}
