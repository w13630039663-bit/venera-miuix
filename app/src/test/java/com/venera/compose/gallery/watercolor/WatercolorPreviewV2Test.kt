package com.venera.compose.gallery.watercolor

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 临时预览器（不入库）：在 JVM 上复刻 Android 渲染器，对 [plan] 出的真实笔触出图。
 * 图片缺席时整套 skip（assumeTrue），不进正式判据。
 */
class WatercolorPreviewV2Test {

    @Test
    fun previewStages() {
        val dir = File("D:/venera-compose/.worktrees/painterly/_qa/wc-v2")
        val originalFile = File(dir, "original.jpg")
        assumeTrue("缺原图，跳过", originalFile.isFile)

        val src = ImageIO.read(originalFile)
        val aspect = src.width.toFloat() / src.height
        // 规划降采样：短边 640（复刻 downscaleForPlanning）
        val pw: Int; val ph: Int
        if (src.width >= src.height) { pw = 640; ph = (640 / aspect).roundToInt() }
        else { ph = 640; pw = (640 * aspect).roundToInt() }
        val small = BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB)
        val g0 = small.createGraphics()
        g0.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g0.drawImage(src, 0, 0, pw, ph, null)
        g0.dispose()
        val px = IntArray(pw * ph) { small.getRGB(it % pw, it / pw) }
        val source = WatercolorSource.of(px, pw, ph)
        val plan = WatercolorPlanner.plan(source, seed = 20261013L)

        // 画布：复刻 create() 的最大边 1024
        val outH: Int; val outW: Int
        if (aspect >= 1f) { outW = 1024; outH = (1024 / aspect).roundToInt() }
        else { outH = 1024; outW = (1024 * aspect).roundToInt() }
        val painter = JvmPainter(outW, outH, plan)

        // 按阶段切快照
        val phaseStats = StringBuilder()
        for (phase in plan.phaseOrder) {
            val start = plan.phaseStart(phase)
            val end = start + plan.countOf(phase)
            for (i in start until end) painter.drawStroke(plan.strokes[i])
            painter.write(dir, "stage-${phase.ordinal}-${phase.name.lowercase()}.png")
            phaseStats.append(phase.name).append('=').append(plan.countOf(phase)).append(' ')
        }
        println("[preview] 阶段笔数: $phaseStats")

        // 并排对比：原图（贴输出尺寸） | 画
        val side = BufferedImage(outW * 2 + 24, outH, BufferedImage.TYPE_INT_RGB)
        val g = side.createGraphics()
        g.color = Color(0x22, 0x22, 0x22)
        g.fillRect(0, 0, side.width, side.height)
        g.drawImage(src.getScaledInstance(outW, outH, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
        g.drawImage(painter.image, outW + 24, 0, null)
        g.dispose()
        ImageIO.write(side, "png", File(dir, "compare.png"))
        painter.write(dir, "final.png")
        // 头部特写：原图与画各裁上半幅中央 62% 宽，2 倍放大并排看五官区。
        val cropW = (outW * 0.62f).toInt()
        val cropH = (outH * 0.42f).toInt()
        val cx = (outW - cropW) / 2
        val cy = (outH * 0.04f).toInt()
        val origScaled = BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB)
        val go = origScaled.createGraphics()
        go.drawImage(src, 0, 0, outW, outH, null)
        go.dispose()
        val zoom = BufferedImage(cropW * 4 + 16, cropH * 2, BufferedImage.TYPE_INT_RGB)
        val gz = zoom.createGraphics()
        gz.color = Color(0x22, 0x22, 0x22)
        gz.fillRect(0, 0, zoom.width, zoom.height)
        gz.drawImage(origScaled.getSubimage(cx, cy, cropW, cropH), 0, 0, cropW * 2, cropH * 2, null)
        gz.drawImage(painter.image.getSubimage(cx, cy, cropW, cropH), cropW * 2 + 16, 0, cropW * 2, cropH * 2, null)
        gz.dispose()
        ImageIO.write(zoom, "png", File(dir, "zoom-head.png"))
        // 笔触地图：各阶段笔触以不透明细线画在黑底上，看实际落点与长度。
        val map = BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB)
        val gm = map.createGraphics()
        gm.color = Color(0x11, 0x11, 0x11)
        gm.fillRect(0, 0, outW, outH)
        for (phase in plan.phaseOrder) {
            gm.color = when (phase) {
                WatercolorPhase.WASH -> Color(0x40, 0x40, 0xFF)
                WatercolorPhase.BLOCKS -> Color(0x00, 0xC0, 0x40)
                WatercolorPhase.FORMS -> Color(0xFF, 0x30, 0x30)
                WatercolorPhase.OUTLINE -> Color(0xFF, 0xC0, 0x00)
                WatercolorPhase.DETAILS -> Color(0xFF, 0x60, 0xE0)
            }
            val start = plan.phaseStart(phase)
            val end = start + plan.countOf(phase)
            for (i in start until end) {
                val s = plan.strokes[i]
                val n = s.points.size / 2
                if (s.isDab || n < 2) {
                    gm.fillRect(
                        (s.points[0] * outW - 1f).toInt(), (s.points[1] * outH - 1f).toInt(), 2, 2,
                    )
                    continue
                }
                var px = s.points[0] * outW
                var py = s.points[1] * outH
                for (k in 1 until n) {
                    val nx = s.points[k * 2] * outW
                    val ny = s.points[k * 2 + 1] * outH
                    gm.drawLine(px.toInt(), py.toInt(), nx.toInt(), ny.toInt())
                    px = nx; py = ny
                }
            }
        }
        gm.dispose()
        ImageIO.write(map, "png", File(dir, "stroke-map.png"))
        assertTrue(File(dir, "final.png").length() > 10_000)
    }

    /** ── 以下为 WatercolorRenderer 的 java.awt 复刻（糊边用多层淡圆近似） ── */
    private class JvmPainter(val w: Int, val h: Int, plan: WatercolorPlan) {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        private val g: Graphics2D = image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            composite = AlphaComposite.SrcOver
        }
        private val minEdge = min(w, h).toFloat()

        init {
            g.color = Color(plan.paperBase, true)
            g.fillRect(0, 0, w, h)
        }

        fun write(dir: File, name: String) {
            // 不 dispose 共用的 g（后续阶段还要往上画）：每次用临时 Graphics 拼一张不透明的。
            val flat = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val gf = flat.createGraphics()
            gf.drawImage(image, 0, 0, null)
            gf.dispose()
            ImageIO.write(flat, "png", File(dir, name))
        }

        fun drawStroke(s: WatercolorStroke) {
            if (s.isDab) { drawDab(s); return }
            val pts = s.points
            val n = pts.size / 2
            val xs = FloatArray(n); val ys = FloatArray(n)
            for (i in 0 until n) { xs[i] = pts[i * 2] * w; ys[i] = pts[i * 2 + 1] * h }
            if (n < 2) { circle(xs[0], ys[0], max(1f, s.width * minEdge) * 0.5f, s.color, s.alpha); return }
            val mid = midNormal(xs, ys)
            val widthPx = max(1f, s.width * minEdge)
            if (s.bleed > 0f) {
                val off = widthPx * 0.5f * s.bleed
                ribbon(xs, ys, mid[0] * off, mid[1] * off, widthPx * (1f + 0.10f * s.bleed), s.color, s.alpha * 0.16f)
                ribbon(xs, ys, -mid[0] * off, -mid[1] * off, widthPx * (1f + 0.10f * s.bleed), s.color, s.alpha * 0.16f)
            }
            ribbon(xs, ys, 0f, 0f, widthPx * 0.90f, s.color, s.alpha)
        }

        private fun drawDab(s: WatercolorStroke) {
            val cx = s.points[0] * w; val cy = s.points[1] * h
            val d = max(1f, s.width * minEdge)
            val r = d * 0.5f
            // 外糊边：三层递淡
            circle(cx, cy, r * 1.10f, s.color, s.alpha * 0.55f * 0.45f)
            circle(cx, cy, r * 1.02f, s.color, s.alpha * 0.55f * 0.75f)
            circle(cx, cy, r * 0.80f, s.color, s.alpha)
        }

        private fun circle(cx: Float, cy: Float, r: Float, argb: Int, alpha: Float) {
            if (alpha <= 0.003f || r <= 0f) return
            g.composite = AlphaComposite.SrcOver.derive(alpha.coerceIn(0f, 1f))
            g.color = Color(argb, true)
            g.fill(java.awt.geom.Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f))
        }

        private fun ribbon(xs: FloatArray, ys: FloatArray, dx: Float, dy: Float, width: Float, argb: Int, alpha: Float) {
            if (alpha <= 0.003f) return
            val n = xs.size
            val half = width * 0.5f
            val path = Path2D.Float()
            var i = 0
            while (i < n) {
                val tan = tangentAt(xs, ys, i)
                val taper = taperAt(i, n) * half
                val px = xs[i] + dx - tan[1] * taper
                val py = ys[i] + dy + tan[0] * taper
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                i++
            }
            var j = n - 1
            while (j >= 0) {
                val tan = tangentAt(xs, ys, j)
                val taper = taperAt(j, n) * half
                path.lineTo(xs[j] + dx + tan[1] * taper, ys[j] + dy - tan[0] * taper)
                j--
            }
            path.closePath()
            g.composite = AlphaComposite.SrcOver.derive(alpha.coerceIn(0f, 1f))
            g.color = Color(argb, true)
            g.fill(path)
        }

        private fun tangentAt(xs: FloatArray, ys: FloatArray, i: Int): FloatArray {
            val n = xs.size
            val a = max(0, i - 1); val b = min(n - 1, i + 1)
            var tx = xs[b] - xs[a]; var ty = ys[b] - ys[a]
            val len = Math.sqrt((tx * tx + ty * ty).toDouble()).toFloat()
            if (len < 1e-3f) return floatArrayOf(1f, 0f)
            return floatArrayOf(tx / len, ty / len)
        }

        private fun taperAt(i: Int, n: Int): Float {
            if (n <= 2) return 0.55f
            val t = i.toFloat() / (n - 1)
            val s = sin(Math.PI * t).toFloat()
            return 0.18f + 0.82f * s * s
        }

        private fun midNormal(xs: FloatArray, ys: FloatArray): FloatArray {
            val n = xs.size; val i = n / 2
            val a = max(0, i - 1); val b = min(n - 1, i + 1)
            var tx = xs[b] - xs[a]; var ty = ys[b] - ys[a]
            val len = Math.sqrt((tx * tx + ty * ty).toDouble()).toFloat()
            if (len < 1e-3f) return floatArrayOf(1f, 0f)
            tx /= len; ty /= len
            return floatArrayOf(-ty, tx)
        }
    }
}
