package com.venera.compose.gallery.watercolor

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
            return WatercolorSource(pixels, width, height, lum, gradX, gradY, edgeNorm)
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
