package com.venera.compose.gallery.watercolor

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 水彩规划的**纯数据输入**：一张降过采样的 ARGB 像素网格，附带按需算好的
 * 亮度、梯度与边缘强度。
 *
 * 为什么单独一棵树而不是直接把 `Bitmap` 递进规划器：规划是判据，
 * 判据要能进 JVM 单测（本仓一贯口径，见 README「判据下沉到纯函数」）——
 * 它因此不认识 `android.*`，也不管这张图当初是谁解码的。
 * 降采样这一步由调用方（Android 侧）做，见 [WatercolorRenderer.fromBitmap]。
 *
 * 坐标约定：**全部归一化到 [0,1]**（`u` 向右、`v` 向下，与 Bitmap 同向）。
 * 规划期的长度/宽度以「短边占比」记，渲染期再乘画布短边 ——
 * 同一棵规划在 720p 与 1080p 上构图一致，只是笔触粗细按短边等比放大。
 *
 * @param pixels ARGB_8888  packed int 数组，长度必须等于 `width * height`
 */
class WatercolorSource private constructor(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    private val lum: FloatArray,
    private val gradX: FloatArray,
    private val gradY: FloatArray,
    /** 全图单像素 |gx|+|gy| 的最大值 —— 边缘强度按它归一化（每张图自己的刻度）。 */
    private val edgeNorm: Float,
    /** 扁平化色：分区均值混 15% 原色。水印细字、格纹这类高频纹理在这里被吃掉。 */
    private val flat: IntArray,
    /** 扁平图的逐像素分区号（0 until regionCount）。 */
    private val regionIndex: IntArray,
    /** 分区均值色（用原图算，比量化质心鲜）。 */
    private val regionColorArr: IntArray,
    /** 分区像素数。 */
    private val regionCountArr: IntArray,
    /** 扁平图亮度的 Sobel（未归一化）。结构边缘与轮廓笔触用它。 */
    private val structGradX: FloatArray,
    private val structGradY: FloatArray,
    private val structEdgeNorm: Float,
    /** 结构张量次特征向量（沿结构走向：发丝、布料褶皱、边缘走向）。 */
    private val flowX: FloatArray,
    private val flowY: FloatArray,
    /** 结构张量相干性 [0,1]：1 = 强方向性（发丝），0 = 各向同性（平坦色块）。 */
    private val coherenceArr: FloatArray,
    /** 皮肤区（暖粉、低饱和、高亮）。面部笔触在此更柔、细节笔被门控。 */
    private val skinArr: BooleanArray,
) {

    /** 采样点越界时**夹住**而不是绕回：绕回会让长笔触的对侧颜色渗进这一侧。 */
    fun colorAt(x: Int, y: Int): Int {
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        return pixels[cy * width + cx]
    }

    /** 双线性取色，`u`/`v` 归一化。越界夹住。 */
    fun sample(u: Float, v: Float): Int {
        val fx = (u.coerceIn(0f, 1f) * (width - 1))
        val fy = (v.coerceIn(0f, 1f) * (height - 1))
        val x0 = fx.toInt().coerceIn(0, width - 1)
        val y0 = fy.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val sx = fx - x0
        val sy = fy - y0
        val c00 = pixels[y0 * width + x0]
        val c10 = pixels[y0 * width + x1]
        val c01 = pixels[y1 * width + x0]
        val c11 = pixels[y1 * width + x1]
        val a = mixChannel(mixChannel(channel(c00, 24), channel(c10, 24), sx), mixChannel(channel(c01, 24), channel(c11, 24), sx), sy)
        val r = mixChannel(mixChannel(channel(c00, 16), channel(c10, 16), sx), mixChannel(channel(c01, 16), channel(c11, 16), sx), sy)
        val g = mixChannel(mixChannel(channel(c00, 8), channel(c10, 8), sx), mixChannel(channel(c01, 8), channel(c11, 8), sx), sy)
        val b = mixChannel(mixChannel(channel(c00, 0), channel(c10, 0), sx), mixChannel(channel(c01, 0), channel(c11, 0), sx), sy)
        return (a.toInt() shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    /** 归一化亮度 [0,1]，双线性。 */
    fun luminance(u: Float, v: Float): Float = mixLumBilinear(u, v)

    /**
     * 归一化梯度方向的**单位向量**写入 [out]（out[0]=x, out[1]=y，可能接近 0,0）。
     * 双线性插值后归一化。梯度指向「跨明暗的方向」，与它垂直的方向就是等亮度线 ——
     * 水彩笔触顺着等亮度线走才像在"描形"而不是"乱刷"。
     */
    fun gradientDirection(u: Float, v: Float, out: FloatArray) {
        val fx = (u.coerceIn(0f, 1f) * (width - 1)).toInt().coerceIn(0, width - 1)
        val fy = (v.coerceIn(0f, 1f) * (height - 1)).toInt().coerceIn(0, height - 1)
        val i = fy * width + fx
        var gx = gradX[i]
        var gy = gradY[i]
        // 邻格平均一次，避免单像素噪声把方向带偏（细密纹理上梯度本来就乱）。
        if (fx + 1 < width) { gx += gradX[i + 1]; gy += gradY[i + 1] }
        if (fy + 1 < height) { gx += gradX[i + width]; gy += gradY[i + width] }
        if (fx + 1 < width && fy + 1 < height) { gx += gradX[i + width + 1]; gy += gradY[i + width + 1] }
        val len = sqrt((gx * gx + gy * gy).toDouble()).toFloat()
        out[0] = if (len < 1e-5f) 0f else gx / len
        out[1] = if (len < 1e-5f) 0f else gy / len
    }

    /** 边缘强度 [0,1]（按全图最强边缘归一化）。0 = 平坦色块，≈1 = 全图最强的轮廓。 */
    fun edge(u: Float, v: Float): Float {
        val fx = (u.coerceIn(0f, 1f) * (width - 1)).toInt().coerceIn(0, width - 1)
        val fy = (v.coerceIn(0f, 1f) * (height - 1)).toInt().coerceIn(0, height - 1)
        val i = fy * width + fx
        var m = 0f
        m += abs(gradX[i]) + abs(gradY[i])
        if (fx + 1 < width) m += abs(gradX[i + 1]) + abs(gradY[i + 1])
        if (fy + 1 < height) m += abs(gradX[i + width]) + abs(gradY[i + width])
        if (fx + 1 < width && fy + 1 < height) m += abs(gradX[i + width + 1]) + abs(gradY[i + width + 1])
        return (m / edgeNorm).coerceIn(0f, 1f)
    }

    // ────────────────────── 结构感知（v2：分区/流向/皮肤） ──────────────────────

    /** 扁平化色双线性取样。笔触颜色取它而不是原图：跨区一笔不再混出第三种颜色。 */
    fun sampleFlat(u: Float, v: Float): Int {
        val fx = (u.coerceIn(0f, 1f) * (width - 1))
        val fy = (v.coerceIn(0f, 1f) * (height - 1))
        val x0 = fx.toInt().coerceIn(0, width - 1)
        val y0 = fy.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val sx = fx - x0
        val sy = fy - y0
        val c00 = flat[y0 * width + x0]
        val c10 = flat[y0 * width + x1]
        val c01 = flat[y1 * width + x0]
        val c11 = flat[y1 * width + x1]
        val a = mixChannel(mixChannel(channel(c00, 24), channel(c10, 24), sx), mixChannel(channel(c01, 24), channel(c11, 24), sx), sy)
        val r = mixChannel(mixChannel(channel(c00, 16), channel(c10, 16), sx), mixChannel(channel(c01, 16), channel(c11, 16), sx), sy)
        val g = mixChannel(mixChannel(channel(c00, 8), channel(c10, 8), sx), mixChannel(channel(c01, 8), channel(c11, 8), sx), sy)
        val b = mixChannel(mixChannel(channel(c00, 0), channel(c10, 0), sx), mixChannel(channel(c01, 0), channel(c11, 0), sx), sy)
        return (a.toInt() shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    /** 最近邻分区号（掩码类查询，不做插值——插值会造出不存在分区）。 */
    fun regionAt(u: Float, v: Float): Int {
        val fx = (u.coerceIn(0f, 1f) * (width - 1)).toInt().coerceIn(0, width - 1)
        val fy = (v.coerceIn(0f, 1f) * (height - 1)).toInt().coerceIn(0, height - 1)
        return regionIndex[fy * width + fx]
    }

    /** 非空分区个数。 */
    val regionCount: Int get() = regionColorArr.size

    /** 分区均值色（原图算的）。 */
    fun regionColor(region: Int): Int = regionColorArr[region.coerceIn(0, regionColorArr.size - 1)]

    /** 分区像素数。 */
    fun regionPixelCount(region: Int): Int = regionCountArr[region.coerceIn(0, regionCountArr.size - 1)]

    /**
     * 结构边缘强度 [0,1]。与 [edge] 的区别：它在**扁平图**上算 Sobel ——
     * 水印细字、格纹、字幕条这类高频纹理先被分区吃掉，剩下的才是轮廓与分界。
     */
    fun structuralEdge(u: Float, v: Float): Float {
        val fx = (u.coerceIn(0f, 1f) * (width - 1)).toInt().coerceIn(0, width - 1)
        val fy = (v.coerceIn(0f, 1f) * (height - 1)).toInt().coerceIn(0, height - 1)
        val i = fy * width + fx
        var m = 0f
        m += abs(structGradX[i]) + abs(structGradY[i])
        if (fx + 1 < width) m += abs(structGradX[i + 1]) + abs(structGradY[i + 1])
        if (fy + 1 < height) m += abs(structGradX[i + width]) + abs(structGradY[i + width])
        if (fx + 1 < width && fy + 1 < height) m += abs(structGradX[i + width + 1]) + abs(structGradY[i + width + 1])
        return (m / structEdgeNorm).coerceIn(0f, 1f)
    }

    /**
     * 结构走向单位向量写入 [out]（双线性 + 归一化）。方向是**次特征向量**——
     * 沿最小亮度变化方向，即发丝的走向、褶皱的延伸、边缘的切线。
     * 近零或无方向处写 (0,0)。
     */
    fun flowDirection(u: Float, v: Float, out: FloatArray) {
        val fx = (u.coerceIn(0f, 1f) * (width - 1))
        val fy = (v.coerceIn(0f, 1f) * (height - 1))
        val x0 = fx.toInt().coerceIn(0, width - 1)
        val y0 = fy.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val sx = fx - x0
        val sy = fy - y0
        fun b(a: FloatArray) = mixChannel(
            mixChannel(a[y0 * width + x0], a[y0 * width + x1], sx),
            mixChannel(a[y1 * width + x0], a[y1 * width + x1], sx), sy,
        )
        var vx = b(flowX); var vy = b(flowY)
        val len = sqrt((vx * vx + vy * vy).toDouble()).toFloat()
        out[0] = if (len < 1e-4f) 0f else vx / len
        out[1] = if (len < 1e-4f) 0f else vy / len
    }

    /** 结构相干性 [0,1]（双线性）。高 = 该处有明确走向。 */
    fun flowCoherence(u: Float, v: Float): Float = mixLumBilinearOn(coherenceArr, u, v)

    /** 皮肤区判定（多数表决平滑过， nearest 查询）。 */
    fun skinAt(u: Float, v: Float): Boolean {
        val fx = (u.coerceIn(0f, 1f) * (width - 1)).toInt().coerceIn(0, width - 1)
        val fy = (v.coerceIn(0f, 1f) * (height - 1)).toInt().coerceIn(0, height - 1)
        return skinArr[fy * width + fx]
    }

    /** 扁平色饱和度 [0,1]。眼里的金橙、背包的粉、樱桃的红都靠它保住。 */
    fun chroma(u: Float, v: Float): Float {
        val c = sampleFlat(u, v)
        val r = channel(c, 16) / 255f
        val g = channel(c, 8) / 255f
        val b = channel(c, 0) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        return if (maxC <= 1e-4f) 0f else (maxC - minC) / maxC
    }

    /** 扁平色亮度 [0,1]。深色区（线稿、水印、暗部）主体笔要避开。 */
    fun flatLuminance(u: Float, v: Float): Float {
        val fx = (u.coerceIn(0f, 1f) * (width - 1))
        val fy = (v.coerceIn(0f, 1f) * (height - 1))
        val x0 = fx.toInt().coerceIn(0, width - 1)
        val y0 = fy.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val sx = fx - x0
        val sy = fy - y0
        fun l(x: Int, y: Int) = luminanceOf(flat[y * width + x])
        return mixChannel(
            mixChannel(l(x0, y0), l(x1, y0), sx),
            mixChannel(l(x0, y1), l(x1, y1), sx), sy,
        )
    }

    /** 结构梯度的单位向量写入 [out]（跨明暗的方向；轮廓笔触取它的法向 = 沿边走向）。 */
    fun structuralGradientDirection(u: Float, v: Float, out: FloatArray) {
        val fx = (u.coerceIn(0f, 1f) * (width - 1)).toInt().coerceIn(0, width - 1)
        val fy = (v.coerceIn(0f, 1f) * (height - 1)).toInt().coerceIn(0, height - 1)
        val i = fy * width + fx
        var gx = structGradX[i]
        var gy = structGradY[i]
        if (fx + 1 < width) { gx += structGradX[i + 1]; gy += structGradY[i + 1] }
        if (fy + 1 < height) { gx += structGradX[i + width]; gy += structGradY[i + width] }
        if (fx + 1 < width && fy + 1 < height) { gx += structGradX[i + width + 1]; gy += structGradY[i + width + 1] }
        val len = sqrt((gx * gx + gy * gy).toDouble()).toFloat()
        out[0] = if (len < 1e-5f) 0f else gx / len
        out[1] = if (len < 1e-5f) 0f else gy / len
    }

    private fun mixLumBilinearOn(field: FloatArray, u: Float, v: Float): Float {
        val fx = u.coerceIn(0f, 1f) * (width - 1)
        val fy = v.coerceIn(0f, 1f) * (height - 1)
        val x0 = fx.toInt().coerceIn(0, width - 1)
        val y0 = fy.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val sx = fx - x0
        val sy = fy - y0
        val a = field[y0 * width + x0]
        val b = field[y0 * width + x1]
        val c = field[y1 * width + x0]
        val d = field[y1 * width + x1]
        return mixChannel(mixChannel(a, b, sx), mixChannel(c, d, sx), sy)
    }

    /** 全图平均色（纸色晕染用它打底）。 */
    val meanColor: Int by lazy {
        var r = 0f; var g = 0f; var b = 0f
        for (p in pixels) {
            r += channel(p, 16); g += channel(p, 8); b += channel(p, 0)
        }
        val n = pixels.size.toFloat()
        pack(255, (r / n).roundToInt(), (g / n).roundToInt(), (b / n).roundToInt())
    }

    /** 全图平均亮度 [0,1]。 */
    val meanLuminance: Float by lazy {
        var s = 0f
        for (l in lum) s += l
        s / lum.size
    }

    private fun mixLumBilinear(u: Float, v: Float): Float {
        val fx = u.coerceIn(0f, 1f) * (width - 1)
        val fy = v.coerceIn(0f, 1f) * (height - 1)
        val x0 = fx.toInt().coerceIn(0, width - 1)
        val y0 = fy.toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val sx = fx - x0
        val sy = fy - y0
        val a = lum[y0 * width + x0]
        val b = lum[y0 * width + x1]
        val c = lum[y1 * width + x0]
        val d = lum[y1 * width + x1]
        return mixChannel(mixChannel(a, b, sx), mixChannel(c, d, sx), sy)
    }

    companion object {
        /** 规划前的降采样目标（短边上限）。再大对构图没有增益，只是让规划变慢。 */
        const val WORK_MAX_EDGE = 640

        /** k-means 分区数。少于此丢结构，多于此把色带切碎。 */
        const val REGION_COUNT = 18

        fun of(pixels: IntArray, width: Int, height: Int): WatercolorSource {
            require(width > 0 && height > 0) { "尺寸必须为正：${width}x$height" }
            require(pixels.size == width * height) { "像素数与尺寸不符：${pixels.size} != ${width * height}" }
            val lum = FloatArray(pixels.size)
            for (i in pixels.indices) lum[i] = luminanceOf(pixels[i])
            val gradX = FloatArray(pixels.size)
            val gradY = FloatArray(pixels.size)
            // Sobel。边界整行/列直接复用邻居值，让边缘不做特殊分支（内容上无区别）。
            var edgeNorm = 0f
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val xm = max(0, x - 1); val xp = min(width - 1, x + 1)
                    val ym = max(0, y - 1); val yp = min(height - 1, y + 1)
                    fun l(xx: Int, yy: Int) = lum[yy * width + xx]
                    val gx = (l(xp, ym) + 2 * l(xp, y) + l(xp, yp)) -
                        (l(xm, ym) + 2 * l(xm, y) + l(xm, yp))
                    val gy = (l(xm, yp) + 2 * l(x, yp) + l(xp, yp)) -
                        (l(xm, ym) + 2 * l(x, ym) + l(xp, ym))
                    val i = y * width + x
                    gradX[i] = gx / 4f
                    gradY[i] = gy / 4f
                    val mag = abs(gradX[i]) + abs(gradY[i])
                    if (mag > edgeNorm) edgeNorm = mag
                }
            }
            // 全图纯色（edgeNorm=0）时给个地板，别让 edge() 除零。
            if (edgeNorm < 1e-4f) edgeNorm = 1f

            // ── v2 结构感知：分区 → 扁平化 → 结构边缘 → 流向 → 皮肤 ──
            val (compactRegions, palette) = quantize(pixels, width, height)
            majority3(compactRegions, width, height, palette.size)
            val stats = regionStats(pixels, compactRegions, width, height, palette.size)
            val regionIndex = stats.index
            val regionColorArr = stats.meanColor
            val regionCountArr = stats.count
            val flat = IntArray(pixels.size)
            for (i in pixels.indices) {
                // 扁平色 = 分区均值混 15% 原色：既吃掉纹理，又留一点细腻过渡。
                // 注意用压实后的分区均值（regionColorArr），不是原始 palette——下标体系不同。
                flat[i] = mixChannelPacked(regionColorArr[regionIndex[i]], pixels[i], 0.15f)
            }
            val flatLum = FloatArray(pixels.size) { luminanceOf(flat[it]) }

            // 结构边缘：**先大半径模糊**再 Sobel。水印细字、格纹这类高频纹理被糊掉，
            // 人物轮廓、衣褶分界这种大结构才是边缘 —— 勾勒笔只认后者。
            // 一张盒糊 ≈ 高斯；半径 6（640 规划宽下约 1%）足够抹掉 4~8px 的细杆。
            val blurred = flatLum.copyOf()
            boxBlurInPlace(blurred, width, height, 6)
            // 结构边缘：扁平图亮度的 Sobel + 自适应归一化。
            val sgx = FloatArray(pixels.size)
            val sgy = FloatArray(pixels.size)
            var structEdgeNorm = 0f
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val xm = max(0, x - 1); val xp = min(width - 1, x + 1)
                    val ym = max(0, y - 1); val yp = min(height - 1, y + 1)
                    fun l(xx: Int, yy: Int) = blurred[yy * width + xx]
                    val gx = (l(xp, ym) + 2 * l(xp, y) + l(xp, yp)) -
                        (l(xm, ym) + 2 * l(xm, y) + l(xm, yp))
                    val gy = (l(xm, yp) + 2 * l(x, yp) + l(xp, yp)) -
                        (l(xm, ym) + 2 * l(x, ym) + l(xp, ym))
                    val i = y * width + x
                    sgx[i] = gx / 4f
                    sgy[i] = gy / 4f
                    val mag = abs(sgx[i]) + abs(sgy[i])
                    if (mag > structEdgeNorm) structEdgeNorm = mag
                }
            }
            if (structEdgeNorm < 1e-4f) structEdgeNorm = 1f

            // 结构张量：5x5 盒窗累积 Jxx/Jyy/Jxy → 次特征向量（沿结构）+ 相干性。
            val flowX = FloatArray(pixels.size)
            val flowY = FloatArray(pixels.size)
            val coherenceArr = FloatArray(pixels.size)
            val jxx = FloatArray(pixels.size)
            val jyy = FloatArray(pixels.size)
            val jxy = FloatArray(pixels.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    jxx[i] = sgx[i] * sgx[i]
                    jyy[i] = sgy[i] * sgy[i]
                    jxy[i] = sgx[i] * sgy[i]
                }
            }
            boxBlurInPlace(jxx, width, height, 2)
            boxBlurInPlace(jyy, width, height, 2)
            boxBlurInPlace(jxy, width, height, 2)
            for (i in pixels.indices) {
                val a = jxx[i]; val b = jyy[i]; val c = jxy[i]
                val m = (a + b) * 0.5f
                val diff = (a - b) * 0.5f
                val d = sqrt((diff * diff + c * c).toDouble()).toFloat()
                val lmin = m - d
                val lmax = m + d
                // 次特征向量：对应 lmin 的解 (c, lmin - a)；退化时退到坐标轴。
                var vx = c
                var vy = lmin - a
                var len = sqrt((vx * vx + vy * vy).toDouble()).toFloat()
                if (len < 1e-6f) {
                    vx = if (a < b) 1f else 0f
                    vy = if (a < b) 0f else 1f
                    len = 1f
                }
                // 定向一致性：流向沿切线走动，两个方向等价，固定符号免得向量场打旋。
                if (vx < 0f || (abs(vx) < 1e-6f && vy < 0f)) { vx = -vx; vy = -vy }
                flowX[i] = vx / len
                flowY[i] = vy / len
                val total = lmax + lmin
                val coherence = if (total < 1e-8f) 0f else ((lmax - lmin) / total).coerceIn(0f, 1f)
                // 强度太弱处（近纯色块）相干性没有意义，压到 0。
                coherenceArr[i] = if (total < 1e-6f) 0f else coherence
            }

            // 皮肤掩码：暖粉、低饱和、高亮。多数表决平滑后用作软保护。
            val skinRaw = BooleanArray(pixels.size)
            for (i in pixels.indices) {
                val c = flat[i]
                val r = channel(c, 16); val g = channel(c, 8); val b = channel(c, 0)
                val maxC = max(r, max(g, b)); val minC = min(r, min(g, b))
                val sat = if (maxC <= 1e-4f) 0f else (maxC - minC) / maxC
                val hue = if (maxC - minC < 1e-3f) -1f else hueOf(r, g, b, maxC)
                val warm = (r - b) > 6f
                val inHue = hue < 0f || hue <= 30f || hue >= 335f
                skinRaw[i] = inHue && sat < 0.38f && maxC / 255f > 0.72f && warm
            }
            val skinArr = majority3Bool(skinRaw, width, height)

            return WatercolorSource(
                pixels, width, height, lum, gradX, gradY, edgeNorm,
                flat, regionIndex, regionColorArr, regionCountArr,
                sgx, sgy, structEdgeNorm, flowX, flowY, coherenceArr, skinArr,
            )
        }

        /** k-means 分区（固定种子、子采样、可复现）。返回 (逐像素分区号, 调色板)。 */
        internal fun quantize(pixels: IntArray, width: Int, height: Int): Pair<IntArray, IntArray> {
            val n = pixels.size
            val rng = Random(0x5EED)
            val k = REGION_COUNT
            val sampleCount = min(n, 6000)
            val sampleIdx = IntArray(n) { it }
            // 部分洗牌取前 sampleCount 个（确定性）。
            for (i in 0 until sampleCount) {
                val j = i + rng.nextInt(n - i)
                val t = sampleIdx[i]; sampleIdx[i] = sampleIdx[j]; sampleIdx[j] = t
            }
            val centroids = Array(k) { FloatArray(3) }
            for (c in 0 until k) {
                val p = pixels[sampleIdx[c]]
                centroids[c][0] = channel(p, 16)
                centroids[c][1] = channel(p, 8)
                centroids[c][2] = channel(p, 0)
            }
            val acc = Array(k) { FloatArray(3) }
            val accN = IntArray(k)
            repeat(10) {
                acc.forEach { it.fill(0f) }
                accN.fill(0)
                for (s in 0 until sampleCount) {
                    val p = pixels[sampleIdx[s]]
                    val r = channel(p, 16); val g = channel(p, 8); val b = channel(p, 0)
                    var best = 0; var bestD = Float.MAX_VALUE
                    for (c in 0 until k) {
                        val dr = r - centroids[c][0]
                        val dg = g - centroids[c][1]
                        val db = b - centroids[c][2]
                        val d = dr * dr + dg * dg + db * db
                        if (d < bestD) { bestD = d; best = c }
                    }
                    acc[best][0] += r; acc[best][1] += g; acc[best][2] += b
                    accN[best]++
                }
                for (c in 0 until k) {
                    if (accN[c] > 0) {
                        centroids[c][0] = acc[c][0] / accN[c]
                        centroids[c][1] = acc[c][1] / accN[c]
                        centroids[c][2] = acc[c][2] / accN[c]
                    }
                }
            }
            val out = IntArray(n)
            for (i in 0 until n) {
                val p = pixels[i]
                val r = channel(p, 16); val g = channel(p, 8); val b = channel(p, 0)
                var best = 0; var bestD = Float.MAX_VALUE
                for (c in 0 until k) {
                    val dr = r - centroids[c][0]
                    val dg = g - centroids[c][1]
                    val db = b - centroids[c][2]
                    val d = dr * dr + dg * dg + db * db
                    if (d < bestD) { bestD = d; best = c }
                }
                out[i] = best
            }
            val palette = IntArray(k) { c ->
                pack(255, centroids[c][0].roundToInt(), centroids[c][1].roundToInt(), centroids[c][2].roundToInt())
            }
            return Pair(out, palette)
        }

        /** 3x3 多数表决（就地）。吃掉孤立像素——水印细字、格纹交错的毛边。 */
        internal fun majority3(field: IntArray, width: Int, height: Int, paletteSize: Int) {
            val copy = field.copyOf()
            val counts = IntArray(paletteSize)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    counts.fill(0)
                    for (dy in -1..1) for (dx in -1..1) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        val yy = (y + dy).coerceIn(0, height - 1)
                        counts[copy[yy * width + xx]]++
                    }
                    val center = copy[y * width + x]
                    var bestV = center; var bestN = counts[center]
                    for (v in counts.indices) {
                        if (counts[v] > bestN) { bestN = counts[v]; bestV = v }
                    }
                    field[y * width + x] = bestV
                }
            }
        }

        internal fun majority3Bool(field: BooleanArray, width: Int, height: Int): BooleanArray {
            val copy = field.copyOf()
            val out = BooleanArray(field.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var t = 0
                    for (dy in -1..1) for (dx in -1..1) {
                        val xx = (x + dx).coerceIn(0, width - 1)
                        val yy = (y + dy).coerceIn(0, height - 1)
                        if (copy[yy * width + xx]) t++
                    }
                    out[y * width + x] = t >= 5
                }
            }
            return out
        }

        private class RegionStats(
            val index: IntArray,
            val meanColor: IntArray,
            val count: IntArray,
        )

        /** 分区统计（多数表决后）：压实空簇，均值色用原图算（鲜）。 */
        private fun regionStats(
            pixels: IntArray,
            regionIndex: IntArray,
            width: Int,
            height: Int,
            paletteSize: Int,
        ): RegionStats {
            val n = pixels.size
            val remap = IntArray(paletteSize) { -1 }
            val order = ArrayList<Int>(paletteSize)
            for (i in 0 until n) {
                val r = regionIndex[i]
                if (remap[r] < 0) { remap[r] = order.size; order.add(r) }
            }
            val count = IntArray(order.size)
            val sumR = FloatArray(order.size)
            val sumG = FloatArray(order.size)
            val sumB = FloatArray(order.size)
            for (i in 0 until n) {
                val c = remap[regionIndex[i]]
                count[c]++
                sumR[c] += channel(pixels[i], 16)
                sumG[c] += channel(pixels[i], 8)
                sumB[c] += channel(pixels[i], 0)
            }
            val meanColor = IntArray(order.size)
            for (c in order.indices) {
                meanColor[c] = pack(255, (sumR[c] / count[c]).roundToInt(), (sumG[c] / count[c]).roundToInt(), (sumB[c] / count[c]).roundToInt())
            }
            val newIndex = IntArray(n) { remap[regionIndex[it]] }
            return RegionStats(newIndex, meanColor, count)
        }

        /** 可分离盒模糊（就地，半径 radius）。 */
        internal fun boxBlurInPlace(field: FloatArray, width: Int, height: Int, radius: Int) {
            val tmp = FloatArray(field.size)
            val window = radius * 2 + 1
            for (y in 0 until height) {
                var sum = 0f
                for (x in -radius..radius) sum += field[y * width + x.coerceIn(0, width - 1)]
                for (x in 0 until width) {
                    tmp[y * width + x] = sum / window
                    val xOut = (x - radius).coerceIn(0, width - 1)
                    val xIn = (x + radius + 1).coerceIn(0, width - 1)
                    sum += field[y * width + xIn] - field[y * width + xOut]
                }
            }
            for (x in 0 until width) {
                var sum = 0f
                for (y in -radius..radius) sum += tmp[y.coerceIn(0, height - 1) * width + x]
                for (y in 0 until height) {
                    field[y * width + x] = sum / window
                    val yOut = (y - radius).coerceIn(0, height - 1)
                    val yIn = (y + radius + 1).coerceIn(0, height - 1)
                    sum += tmp[yIn * width + x] - tmp[yOut * width + x]
                }
            }
        }

        /** 打包色的线性混合（供 flat = palette ⊕ 15% original）。 */
        internal fun mixChannelPacked(a: Int, b: Int, t: Float): Int {
            val f = t.coerceIn(0f, 1f)
            fun m(shift: Int) = (channel(a, shift) + (channel(b, shift) - channel(a, shift)) * f).roundToInt()
            return pack(m(24), m(16), m(8), m(0))
        }

        /** RGB（0-255 浮点）→ 色相角度 [0,360)。maxC = max(r,g,b)。 */
        internal fun hueOf(r: Float, g: Float, b: Float, maxC: Float): Float {
            val minC = min(r, min(g, b))
            val d = maxC - minC
            if (d < 1e-3f) return 0f
            val rc = (maxC - r) / d
            val gc = (maxC - g) / d
            val bc = (maxC - b) / d
            var h = when (maxC) {
                r -> (bc - gc) / 6f
                g -> (2f + rc - bc) / 6f
                else -> (4f + gc - rc) / 6f
            } % 6f
            if (h < 0f) h += 6f
            return h * 60f
        }

        internal fun luminanceOf(argb: Int): Float {
            val r = channel(argb, 16)
            val g = channel(argb, 8)
            val b = channel(argb, 0)
            return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }

        internal fun channel(argb: Int, shift: Int): Float = ((argb shr shift) and 0xFF).toFloat()

        internal fun mixChannel(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

        internal fun pack(a: Int, r: Int, g: Int, b: Int): Int =
            (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or
                (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

        /** HSV 的 S/V 域轻改：水彩颜料比照片"闷"一点，饱和与明度都往回收。 */
        internal fun adjust(
            argb: Int,
            saturationScale: Float = 1f,
            valueScale: Float = 1f,
            alpha: Int = 255,
        ): Int {
            val r = channel(argb, 16) / 255f
            val g = channel(argb, 8) / 255f
            val b = channel(argb, 0) / 255f
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val d = maxC - minC
            var h = 0f
            if (d > 1e-5f) {
                val rc = (maxC - r) / d
                val gc = (maxC - g) / d
                val bc = (maxC - b) / d
                h = when (maxC) {
                    r -> (bc - gc) / 6f
                    g -> (2f + rc - bc) / 6f
                    else -> (4f + gc - rc) / 6f
                } % 6f
                if (h < 0f) h += 6f
            }
            val s = (if (maxC <= 0f) 0f else d / maxC) * saturationScale
            val v = maxC * valueScale
            val c = v * s
            val x = c * (1f - abs((h * 6f % 2f) - 1f))
            val m = v - c
            val (rr, gg, bb) = when ((h * 6f).toInt() % 6) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return pack(
                alpha,
                (((rr + m) * 255f)).toInt(),
                (((gg + m) * 255f)).toInt(),
                (((bb + m) * 255f)).toInt(),
            )
        }
    }
}
