package com.venera.desktop.gallery.data

import com.venera.compose.gallery.domain.GalleryAnimatedMode
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十八批 批 18 · **动图在桌面上到底画成什么样**（把批 16 挂的那格填掉）
 *
 * ## 为什么这一格值得单独量
 *
 * 设置那一屏的动图那行从前写着"桌面今天一律让它动"，而那半句**从来没有人量过**。
 * 批 16 量到的只是结构事实（`javap` 读 `coil-core-jvm`：JVM 侧唯一的解码件是 `SkiaImageDecoder`，
 * 它调的是 `org.jetbrains.skia.Image.makeFromEncoded(byte[])` 而**不是** `Codec`）。
 * 结构事实能说明"没有动着放那条路"，却说明不了**屏上今天看得见什么** ——
 * "停在第一帧"与"整张画不出来"是两句话，用户会按它决定要不要在桌面看动图。
 *
 * ## 这一颗量的是同一颗件，不是它的替身
 *
 * 夹具是一段**自己造的 2 帧 GIF**（第 0 帧整幅红、第 1 帧整幅蓝，两帧像素差得开，判读不会撞车），
 * 然后走 `Image.makeFromEncoded` —— 与 `SkiaImageDecoder.decodeBitmapSync` 同一颗入口。
 * 三档原始读数都打到 stdout（`ADP_` 行）：`Codec.frameCount`（夹具真是动图吗）、解出来的宽高、解出来的那颗像素。
 */
class DesktopAnimatedDecodeProbeTest {

    /** 造一段 2 帧的 GIF：第 0 帧整幅红，第 1 帧整幅蓝（序列写入本身就给两帧，不碰 GIF 元数据）。 */
    private fun animatedGifBytes(width: Int = 4, height: Int = 4): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val out = ByteArrayOutputStream()
        MemoryCacheImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(null)
            listOf(0x00FF0000, 0x000000FF).forEach { color ->
                val frame = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                for (x in 0 until width) for (y in 0 until height) frame.setRGB(x, y, color)
                writer.writeToSequence(IIOImage(frame, null, null), null)
            }
            writer.endWriteSequence()
        }
        return out.toByteArray()
    }

    /** 只读不判：把三档原始读数打到 stdout（本仓规矩：探针要打印原始片段，不能只报"过了"）。 */
    private fun probe(bytes: ByteArray): Triple<Int, Pair<Int, Int>, Int> {
        val data = org.jetbrains.skia.Data.makeFromBytes(bytes)
        val codec = org.jetbrains.skia.Codec.makeFromData(data)
        val frameCount = codec.frameCount
        codec.close()
        data.close()
        val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        val size = image.width to image.height
        val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
        val pixel = bitmap.getColor(1, 1) and 0xFFFFFF
        image.close()
        println(
            "ADP_字节=${bytes.size} Codec帧数=$frameCount makeFromEncoded宽高=$size 解出像素=0x${pixel.toString(16)}",
        )
        return Triple(frameCount, size, pixel)
    }

    /** ① 夹具自己是真的两帧动图（不真的话，后面两条读数全是空的）。 */
    @Test
    fun 夹具真的是两帧动图() {
        val bytes = animatedGifBytes()
        val (frameCount, size, _) = probe(bytes)
        assertTrue("夹具解不出宽高：$size", size.first > 0 && size.second > 0)
        assertEquals(
            "夹具不是两帧动图 ⇒ 这一颗探针没有对象（先看 ADP_ 那行的帧数）",
            2, frameCount,
        )
    }

    /** ② 桌面那颗解码入口交出的是**一张静态图**，而它就是第 0 帧。 */
    @Test
    fun 桌面解出来是首帧而不是动着放() {
        val bytes = animatedGifBytes()
        val (_, _, pixel) = probe(bytes)
        val red = 0xFF0000
        val blue = 0x0000FF
        assertNotEquals(
            "解出来的是第 1 帧（蓝）—— 那说明桌面会自己往后走一帧，「停在第一帧」这句就不成立",
            blue, pixel,
        )
        assertEquals(
            "解出来的既不是第 0 帧也不是第 1 帧（0x${pixel.toString(16)}）⇒ 判读要重看",
            red, pixel,
        )
    }

    /** ③ 对岸那枚枚举今天仍是三档（这一档的语义要重新谈时，这里会红着提醒）。 */
    @Test
    fun 对岸那枚枚举仍是三档() {
        assertEquals(
            "对岸 `GalleryAnimatedMode` 的档数变了（设置那一行与这一颗探针都要跟着重谈）",
            listOf("ALWAYS", "NEVER", "WIFI_ONLY"),
            GalleryAnimatedMode.entries.map { it.name }.sorted(),
        )
    }

    /**
     * ④ 批 51 的机制凭据：走 `Codec.readPixels(bitmap, frame, priorFrame)` 那一档，
     *   第 1 帧交出的是**蓝**，而 ② 那颗入口交出的还是红。
     *
     * 为什么钉在库里而不是钉播放器：`DesktopAnimatedImage` 是组合态，单测里起不了它
     * （而批 47 的账就在隔壁 —— 单测从不构造 `ImageLoader`，崩溃只在屏上）。
     * 这一条钉的是"播放器脚下那块地板还在"：哪天 skiko 升级把逐帧解动图解坏了，
     * 播放器会**静默退回首帧**，屏上看不出来，而这里当场红。
     */
    @Test
    fun 逐帧那一档交出第二帧而不是首帧() {
        val bytes = animatedGifBytes()
        val data = org.jetbrains.skia.Data.makeFromBytes(bytes)
        val codec = org.jetbrains.skia.Codec.makeFromData(data)
        val bitmap = org.jetbrains.skia.Bitmap()
        check(bitmap.allocPixels(codec.imageInfo)) { "夹具的 ImageInfo 装不下像素位" }
        try {
            codec.readPixels(bitmap, 0, -1)
            val first = bitmap.getColor(1, 1) and 0xFFFFFF
            codec.readPixels(bitmap, 1, 0)
            val second = bitmap.getColor(1, 1) and 0xFFFFFF
            println("ADP_逐帧 帧数=${codec.frameCount} 第0帧=0x${first.toString(16)} 第1帧=0x${second.toString(16)}")
            assertEquals("第 0 帧不是红 ⇒ 判读要重看", 0xFF0000, first)
            assertEquals(
                "第 1 帧还是红 ⇒ `readPixels(bitmap, frame, priorFrame)` 不再往后走一帧，" +
                    "大图那一屏会静默退回首帧（播放器没有别的凭据，只有这一条）",
                0x0000FF, second,
            )
        } finally {
            codec.close()
            data.close()
        }
    }

    /**
     * ⑤ 批 51 收尾那一格：**每帧在堆里占多大**。
     *
     * 读数不许是"宽×高×4"那种算出来的假设 —— 这里量的是 `Bitmap.computeByteSize()` 这个真值，
     * 并把 Skia 自己的算法钉成一条恒等式（w×h×`bytesPerPixel`）。等式红就说明 Skia 换了位深算法，
     * 而播放器那行 `每帧=` 的算法当场跟着变味。
     * 夹具换成 100×100：4×4 那一段算出来的字节太小，撞不出"1MB 那条线"上的判读。
     */
    @Test
    fun 每帧真实字节等于宽高乘位深() {
        val bytes = animatedGifBytes(100, 100)
        val data = org.jetbrains.skia.Data.makeFromBytes(bytes)
        val codec = org.jetbrains.skia.Codec.makeFromData(data)
        val bitmap = org.jetbrains.skia.Bitmap()
        check(bitmap.allocPixels(codec.imageInfo)) { "夹具的 ImageInfo 装不下像素位" }
        try {
            val actual = bitmap.computeByteSize().toLong()
            val expected = bitmap.width.toLong() * bitmap.height.toLong() * bitmap.bytesPerPixel.toLong()
            println(
                "ADP_每帧 宽高=${bitmap.width}x${bitmap.height} 位深=${bitmap.bytesPerPixel} " +
                    "色型=${bitmap.colorType} computeByteSize=$actual 宽高乘位深=$expected",
            )
            assertEquals(
                "Skia 自己的每帧字节不再是 w×h×bytesPerPixel ⇒ 播放器那行 `每帧=` 的算法要重看",
                expected, actual,
            )
        } finally {
            codec.close()
            data.close()
        }
    }
}
