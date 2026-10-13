package com.venera.compose.gallery.watercolor

import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

class WatercolorDebugProbeTest {

    private fun leftRedRightBlue(w: Int = 160, h: Int = 120): WatercolorSource {
        val px = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val t = x.toFloat() / w
                px[y * w + x] = if (t < 0.47f) 0xFFC03028.toInt()
                else if (t > 0.53f) 0xFF2848B0.toInt()
                else {
                    val k = (t - 0.47f) / 0.06f
                    mix(0xFFC03028.toInt(), 0xFF2848B0.toInt(), k)
                }
            }
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

    @Test
    fun probeRealImage() {
        val f = java.io.File("D:/venera-compose/.worktrees/painterly/_qa/wc-v2/original.jpg")
        val src = javax.imageio.ImageIO.read(f)
        val aspect = src.width.toFloat() / src.height
        val pw: Int; val ph: Int
        if (src.width >= src.height) { pw = 640; ph = (640 / aspect).toInt() } else { ph = 640; pw = (640 * aspect).toInt() }
        val small = java.awt.image.BufferedImage(pw, ph, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g0 = small.createGraphics()
        g0.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g0.drawImage(src, 0, 0, pw, ph, null)
        g0.dispose()
        val px = IntArray(pw * ph) { small.getRGB(it % pw, it / pw) }
        val wcSrc = WatercolorSource.of(px, pw, ph)
        println("REAL size=${pw}x${ph} regions=${wcSrc.regionCount}")
        val sorted = (0 until wcSrc.regionCount).sortedByDescending { wcSrc.regionPixelCount(it) }
        for (r in sorted) {
            val c = wcSrc.regionColor(r)
            val lum = WatercolorSource.luminanceOf(c)
            println(
                "REAL region $r: count=${wcSrc.regionPixelCount(r)} " +
                    "(${(wcSrc.regionPixelCount(r) * 100f / (pw * ph)).toInt()}%) " +
                    "rgb=(${WatercolorSource.channel(c, 16).toInt()},${WatercolorSource.channel(c, 8).toInt()},${WatercolorSource.channel(c, 0).toInt()}) lum=$lum",
            )
        }
        // 深色笔触聚集区的边缘强度分布
        var strongEdges = 0; var total = 0
        for (i in 0 until 2000) {
            val u = kotlin.random.Random(i).nextFloat(); val v = kotlin.random.Random(i + 7).nextFloat()
            total++
            if (wcSrc.structuralEdge(u, v) > 0.3f) strongEdges++
        }
        println("REAL structuralEdge>0.3 占比: $strongEdges/$total")
        // 皮肤覆盖探针：全图皮肤像素占比 + 逐阶段落在皮肤上的笔数
        var skinPx = 0
        val N = 20000
        for (i in 0 until N) {
            val u = kotlin.random.Random(i).nextFloat(); val v = kotlin.random.Random(i + 7).nextFloat()
            if (wcSrc.skinAt(u, v)) skinPx++
        }
        println("REAL skin pixel share ≈ ${skinPx * 100f / N}%")
        val plan = WatercolorPlanner.plan(wcSrc, seed = 42L)
        for (p in WatercolorPhase.entries) {
            val ss = plan.strokes.filter { it.phase == p }
            var onSkin = 0
            for (s in ss) if (wcSrc.skinAt(s.points[0], s.points[1])) onSkin++
            println("REAL ${p.name}: total=${ss.size} startOnSkin=$onSkin")
        }
        // 脸部框（原图人脸大致在 x 0.30-0.62, y 0.28-0.52，归一化）内收到的 FORMS/BLOCKS 笔数
        var faceStrokes = 0
        var faceSkinStart = 0
        for (s in plan.strokes) {
            if (s.phase != WatercolorPhase.FORMS && s.phase != WatercolorPhase.BLOCKS) continue
            val sx = s.points[0]; val sy = s.points[1]
            if (sx in 0.30f..0.62f && sy in 0.28f..0.52f) {
                faceStrokes++
                if (wcSrc.skinAt(sx, sy)) faceSkinStart++
            }
        }
        println("REAL face box strokes=$faceSkinStart(skin-start of $faceStrokes)")
        // 暖色目标探针：粉包/腮红（r-b>25）、金眼睛（r>150 g 100..190 b<90）的像素量与分布
        var pink = 0; var gold = 0
        var pinkMinU = 1f; var pinkMaxU = 0f; var pinkMinV = 1f; var pinkMaxV = 0f
        var goldMinU = 1f; var goldMaxU = 0f; var goldMinV = 1f; var goldMaxV = 0f
        val nPix = pw * ph
        for (i in 0 until nPix) {
            val c = px[i]
            val r = WatercolorSource.channel(c, 16); val g = WatercolorSource.channel(c, 8); val b = WatercolorSource.channel(c, 0)
            val u = (i % pw).toFloat() / pw; val v = (i / pw).toFloat() / ph
            if (r - b > 25 && r > 170 && g > 130) {
                pink++
                if (u < pinkMinU) pinkMinU = u; if (u > pinkMaxU) pinkMaxU = u
                if (v < pinkMinV) pinkMinV = v; if (v > pinkMaxV) pinkMaxV = v
            }
            if (r > 150 && g in 100f..190f && b < 90) {
                gold++
                if (u < goldMinU) goldMinU = u; if (u > goldMaxU) goldMaxU = u
                if (v < goldMinV) goldMinV = v; if (v > goldMaxV) goldMaxV = v
            }
        }
        println("REAL pink px=$pink (${pink * 100f / nPix}%) u∈[$pinkMinU,$pinkMaxU] v∈[$pinkMinV,$pinkMaxV]")
        println("REAL gold px=$gold (${gold * 100f / nPix}%) u∈[$goldMinU,$goldMaxU] v∈[$goldMinV,$goldMaxV]")
        // DETAILS vivid 实际选中点采样：chroma 分布
        var chHi = 0; var chMid = 0; var chLo = 0
        for (i in 0 until 4000) {
            val u = kotlin.random.Random(i).nextFloat(); val v = kotlin.random.Random(i + 13).nextFloat()
            val ch = wcSrc.chroma(u, v)
            if (ch > 0.38) chHi++ else if (ch > 0.20) chMid++ else chLo++
        }
        println("REAL chroma dist: >0.38=$chHi 0.20-0.38=$chMid <0.20=$chLo (of 4000)")
        // 眼睛/五官区的结构边强度：OUTLINE 皮肤区门槛 e≥0.50 是否拦得住眼线
        var eEyeSum = 0f; var eEyeN = 0; var eEyeStrong = 0
        for (i in 0 until 3000) {
            val u = 0.36f + kotlin.random.Random(i).nextFloat() * 0.16f
            val v = 0.26f + kotlin.random.Random(i + 3).nextFloat() * 0.14f
            val e = wcSrc.structuralEdge(u, v)
            eEyeSum += e; eEyeN++
            if (e > 0.44f) eEyeStrong++
        }
        println("REAL eye-region structuralEdge mean=${eEyeSum / eEyeN} >0.44占比=$eEyeStrong/$eEyeN")
        // 脸部整体 OUTLINE 通过率
        var eFaceSum = 0f; var eFaceN = 0; var eFaceStrong = 0
        for (i in 0 until 4000) {
            val u = 0.30f + kotlin.random.Random(i).nextFloat() * 0.32f
            val v = 0.28f + kotlin.random.Random(i + 5).nextFloat() * 0.24f
            if (!wcSrc.skinAt(u, v)) continue
            val e = wcSrc.structuralEdge(u, v)
            eFaceSum += e; eFaceN++
            if (e > 0.44f) eFaceStrong++
        }
        println("REAL face-region(skin) structuralEdge mean=${eFaceSum / eFaceN} >0.44占比=$eFaceStrong/$eFaceN")
    }

    @Test
    fun probe() {
        val src = leftRedRightBlue()
        val plan = WatercolorPlanner.plan(src, seed = 7L)
        println("PROBE total=${plan.strokes.size}")
        for (p in plan.phaseOrder) println("PROBE ${p.name}=${plan.countOf(p)}")
        println("PROBE regionCount=${src.regionCount}")
        for (r in 0 until src.regionCount) {
            val c = src.regionColor(r)
            println(
                "PROBE region $r: rgb=(${WatercolorSource.channel(c, 16)},${WatercolorSource.channel(c, 8)},${WatercolorSource.channel(c, 0)}) " +
                    "count=${src.regionPixelCount(r)} atLeft=${src.regionAt(0.1f, 0.5f)} atRight=${src.regionAt(0.9f, 0.5f)}",
            )
        }
        val fL = src.sampleFlat(0.1f, 0.5f)
        val fR = src.sampleFlat(0.9f, 0.5f)
        println(
            "PROBE flatLeft=(${WatercolorSource.channel(fL, 16)},${WatercolorSource.channel(fL, 8)},${WatercolorSource.channel(fL, 0)}) " +
                "flatRight=(${WatercolorSource.channel(fR, 16)},${WatercolorSource.channel(fR, 8)},${WatercolorSource.channel(fR, 0)})",
        )
        val wash = plan.strokes.filter { it.phase == WatercolorPhase.WASH }
        val left = wash.filter { it.points[0] < 0.4f }
        val right = wash.filter { it.points[0] > 0.6f }
        println("PROBE wash n=${wash.size} left=${left.size} right=${right.size}")
        if (right.isNotEmpty()) {
            val c = right.first().color
            println("PROBE right wash sample color rgb=(${WatercolorSource.channel(c, 16)},${WatercolorSource.channel(c, 8)},${WatercolorSource.channel(c, 0)})")
        }
        // 皮肤探针
        val w2 = 120; val h2 = 90
        val px2 = IntArray(w2 * h2)
        for (y in 0 until h2) for (x in 0 until w2) {
            px2[y * w2 + x] = if (x < w2 / 2) 0xFFF2CFC4.toInt() else 0xFFA8C0E8.toInt()
        }
        val s2 = WatercolorSource.of(px2, w2, h2)
        println("PROBE skin: left=${s2.skinAt(0.25f, 0.5f)} right=${s2.skinAt(0.75f, 0.5f)} regions=${s2.regionCount}")
        for (r in 0 until s2.regionCount) {
            val c = s2.regionColor(r)
            println("PROBE skin-region $r rgb=(${WatercolorSource.channel(c, 16)},${WatercolorSource.channel(c, 8)},${WatercolorSource.channel(c, 0)})")
        }
        // 困区探针
        var cross = 0
        var checked = 0
        for (s in plan.strokes) {
            if (s.phase != WatercolorPhase.BLOCKS && s.phase != WatercolorPhase.FORMS) continue
            val sr = src.regionAt(s.points[0], s.points[1])
            var i = 2
            while (i + 1 < s.points.size) {
                if (src.regionAt(s.points[i], s.points[i + 1]) != sr) cross++
                checked++
                i += 2
            }
        }
        println("PROBE confinement: cross=$cross checked=$checked")
    }
}
