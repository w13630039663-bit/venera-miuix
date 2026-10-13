package com.venera.compose.gallery.watercolor

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 水彩**渲染器**：把一份 [WatercolorPlan] 一笔一笔落到位图上。
 *
 * 与 p5.brush 的水彩笔画对应的三件事：
 * - **渗色（bleed）**：每笔先沿法向两侧各刷一遍（略宽、更淡），再刷芯线。
 *   边缘因此比中心多刷一两遍，干后就出现水彩的"积边"。
 * - **纸（paper）**：先铺暖白底 + 大块尿点，之后每笔都是半透明罩染，
 *   纸的颗粒从颜料底下透出来。
 * - **印章（dab）**：晕染的大色块是一枚枚圆章，外淡内浓、边缘微糊。
 *
 * 位图是**软件位图**（`ARGB_8888`）：渗色的糊边要 `BlurMaskFilter`，
 * 硬件位图不支持它。整幅画最大边 1024，一张 4 MB 左右的软件位图，
 * 全帧重绘对 CPU 没有压力；Compose 侧再把它当 `ImageBitmap` 贴上屏。
 *
 * 非线程安全：所有绘制都在同一线程上按顺序调用（绘制循环那里保证）。
 */
class WatercolorRenderer private constructor(
    private val bitmap: Bitmap,
) {
    private val canvas = Canvas(bitmap)
    private val width = bitmap.width
    private val height = bitmap.height
    private val minEdge = min(width, height).toFloat()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** 已经画到第几笔（= 调用方的主进度）。 */
    var applied: Int = 0
        private set

    companion object {
        /** 成图最大边。再大只是多耗内存与每帧上传，观感没有增益。 */
        const val MAX_OUTPUT_EDGE = 1024

        /** 规划前降采样上限（与 [WatercolorSource.WORK_MAX_EDGE] 一致）。 */
        const val WORK_EDGE = WatercolorSource.WORK_MAX_EDGE

        /**
         * 从一张已解码的图开一幅画布：按比例缩到 [MAX_OUTPUT_EDGE] 内，
         * 铺上 [WatercolorPlan.paperBase]，再点一片尿点。
         */
        fun create(source: Bitmap, plan: WatercolorPlan): WatercolorRenderer {
            val aspect = source.width.toFloat() / source.height
            val outW: Int
            val outH: Int
            if (source.width >= source.height) {
                outW = MAX_OUTPUT_EDGE
                outH = (MAX_OUTPUT_EDGE / aspect).roundToInt()
            } else {
                outH = MAX_OUTPUT_EDGE
                outW = (MAX_OUTPUT_EDGE * aspect).roundToInt()
            }
            val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val renderer = WatercolorRenderer(bitmap)
            renderer.paintPaper(plan)
            return renderer
        }

        /** 规划用降采样：长边缩进 [WORK_EDGE]，一次 `getPixels` 拿到 IntArray。 */
        fun downscaleForPlanning(source: Bitmap, maxEdge: Int = WORK_EDGE): WatercolorSource {
            val aspect = source.width.toFloat() / source.height
            val w: Int
            val h: Int
            if (source.width >= source.height) {
                w = maxEdge
                h = (maxEdge / aspect).roundToInt()
            } else {
                h = maxEdge
                w = (maxEdge * aspect).roundToInt()
            }
            val scaled = Bitmap.createScaledBitmap(source, w, h, true)
            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
            if (scaled !== source) scaled.recycle()
            return WatercolorSource.of(pixels, w, h)
        }
    }

    /** 给 Compose 贴屏用。同一个位图包一层，内容变了绘制期自然会重读。 */
    fun asImageBitmap(): ImageBitmap =
        bitmap.asImageBitmap()

    /** 成图位图（保存用）。调用方拿到后不要改写。 */
    fun outputBitmap(): Bitmap = bitmap

    /**
     * 纸：底色铺满 + 尿点。
     *
     * 尿点用大半径、低 alpha 的糊边圆随机撒一层：纸的"纤维团"有了，
     * 后面每一笔半透明叠上去时，颜色才会一块深一块浅 —— 平纸上罩色是塑料感。
     */
    private fun paintPaper(plan: WatercolorPlan) {
        canvas.drawColor(plan.paperBase)
        val rng = Random(plan.seed xor 0x9E3779B9L)
        val tint = plan.paperTint
        val blobs = 150
        val blur = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(minEdge * 0.012f, BlurMaskFilter.Blur.NORMAL)
        }
        for (i in 0 until blobs) {
            val cx = rng.nextFloat() * width
            val cy = rng.nextFloat() * height
            val r = minEdge * (0.025f + rng.nextFloat() * 0.06f)
            val darker = rng.nextFloat() < 0.5f
            val k = if (darker) 0.94f else 1.05f
            blur.color = WatercolorSource.adjust(
                tint,
                saturationScale = if (darker) 1.05f else 0.85f,
                valueScale = k,
                alpha = 14,
            )
            canvas.drawCircle(cx, cy, r, blur)
        }
    }

    /**
     * 画一笔。返回是否真的落了笔（供调用方计进度）。
     *
     * 顺序：渗色两遍（法向 ±offset、略宽、各 0.16 alpha）→ 芯线一遍。
     * 两遍渗色合计 0.32、芯线 0.5 上下 —— 边缘 (≈0.32) 与芯 (≈0.82) 的差就是积边。
     * 每一遍都是**锥形带**（两端收笔）：等粗圆头线读起来是套管，锥形才是"一笔"。
     */
    fun drawStroke(stroke: WatercolorStroke): Boolean {
        val pts = stroke.points
        val w = max(1f, stroke.width * minEdge)
        val alpha = stroke.alpha
        if (stroke.isDab) {
            drawDab(stroke, pts[0] * width, pts[1] * height, w)
            applied++
            return true
        }
        val n = pts.size / 2
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        for (i in 0 until n) {
            xs[i] = pts[i * 2] * width
            ys[i] = pts[i * 2 + 1] * height
        }
        if (n < 2) {
            // 理论不到这里（规划保证折线至少两点）；真到了画个点兜底。
            fillPaint.color = colorWithAlpha(stroke.color, alpha)
            canvas.drawCircle(xs[0], ys[0], w * 0.5f, fillPaint)
            applied++
            return true
        }
        val mid = midNormal(xs, ys)
        // 渗色：沿法向各抖一遍。上下两声出来就是"湿边"。
        if (stroke.bleed > 0f) {
            val off = w * 0.5f * stroke.bleed
            val bleedColor = colorWithAlpha(stroke.color, alpha * 0.16f)
            val bleedW = w * (1f + 0.10f * stroke.bleed)
            drawRibbon(xs, ys, mid.x * off, mid.y * off, bleedW, bleedColor)
            drawRibbon(xs, ys, -mid.x * off, -mid.y * off, bleedW, bleedColor)
        }
        // 芯线略收窄：糊边比芯宽，边缘色才堆得起来。
        drawRibbon(xs, ys, 0f, 0f, w * 0.90f, colorWithAlpha(stroke.color, alpha))
        applied++
        return true
    }

    /** 圆章：外糊边、心实底。 */
    private fun drawDab(stroke: WatercolorStroke, cx: Float, cy: Float, diameter: Float) {
        val r = diameter * 0.5f
        val blur = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(r * 0.30f, BlurMaskFilter.Blur.NORMAL)
        }
        blur.color = colorWithAlpha(stroke.color, stroke.alpha * 0.55f)
        canvas.drawCircle(cx, cy, r * 1.10f, blur)
        fillPaint.color = colorWithAlpha(stroke.color, stroke.alpha)
        canvas.drawCircle(cx, cy, r * 0.80f, fillPaint)
    }

    /**
     * 画一条锥形带：沿折线法向展开，两端按 [taperAt] 收细。
     *
     * 收笔曲线用 sin² —— 起笔快、收笔慢，接近 real brush 的提按；
     * 端点不收到 0（留 0.18），全收死的尖在叠加几百笔后会变成一片"毛刺"。
     */
    private fun drawRibbon(xs: FloatArray, ys: FloatArray, dx: Float, dy: Float, width: Float, color: Int) {
        val n = xs.size
        val half = width * 0.5f
        val path = Path()
        // 左缘（法向一侧）：正序
        var i = 0
        while (i < n) {
            val tan = tangentAt(xs, ys, i)
            val taper = taperAt(i, n) * half
            val px = xs[i] + dx - tan.y * taper
            val py = ys[i] + dy + tan.x * taper
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            i++
        }
        // 右缘：逆序，闭合
        var j = n - 1
        while (j >= 0) {
            val tan = tangentAt(xs, ys, j)
            val taper = taperAt(j, n) * half
            path.lineTo(xs[j] + dx + tan.y * taper, ys[j] + dy - tan.x * taper)
            j--
        }
        path.close()
        fillPaint.color = color
        canvas.drawPath(path, fillPaint)
    }

    /** 第 [i] 点的单位切向。 */
    private fun tangentAt(xs: FloatArray, ys: FloatArray, i: Int): PointF {
        val n = xs.size
        val a = max(0, i - 1)
        val b = min(n - 1, i + 1)
        var tx = xs[b] - xs[a]
        var ty = ys[b] - ys[a]
        val len = sqrt((tx * tx + ty * ty).toDouble()).toFloat()
        if (len < 1e-3f) return PointF(1f, 0f)
        tx /= len; ty /= len
        return PointF(tx, ty)
    }

    /** 收笔系数：两端细、中段 full。 */
    private fun taperAt(i: Int, n: Int): Float {
        if (n <= 2) return 0.55f
        val t = i.toFloat() / (n - 1)
        val s = kotlin.math.sin(Math.PI * t).toFloat()
        return 0.18f + 0.82f * s * s
    }

    /** 折线中点处的单位法向（像素平面）。 */
    private fun midNormal(xs: FloatArray, ys: FloatArray): PointF {
        val n = xs.size
        val i = n / 2
        val a = max(0, i - 1)
        val b = min(n - 1, i + 1)
        var tx = xs[b] - xs[a]
        var ty = ys[b] - ys[a]
        val len = sqrt((tx * tx + ty * ty).toDouble()).toFloat()
        if (len < 1e-3f) return PointF(1f, 0f)
        tx /= len; ty /= len
        return PointF(-ty, tx)
    }

    private fun colorWithAlpha(argb: Int, alpha: Float): Int {
        val a = (strokeAlpha(argb) * alpha).roundToInt().coerceIn(0, 255)
        return (argb and 0x00FFFFFF) or (a shl 24)
    }

    private fun strokeAlpha(argb: Int): Int = (argb shr 24) and 0xFF
}
