package com.venera.engineprobe

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * 桌面侧（纯 JVM）的图像还原工具。**唯一一份 JVM 实现**：探针与桌面 App 都走这里，
 * 免得两边各写一版块序算法然后漂移。
 *
 * ⚠️ 与 Android 侧 `ImagePipelinePolicy.reorderBlocksBottomUp` 是**同构的两份实现**
 * （那边吃 `android.graphics.Bitmap`，这边吃 `BufferedImage`），
 * 口径靠下面的注释逐条对齐，**像素级对拍尚未做**（归阶段 3）。
 */
object JvmImageOps {

    init {
        // ImageIO 默认往 %TEMP% 写缓存文件；本机打包 exe 被按程序拦写入（成因见探针文档第七节末
        // "追加更正"那条：火绒 HIPS，不是 jpackage 上下文），缓存文件建不出来就是
        // `IIOException: Can't create cache file!`。这里字节整幅已在内存里，落盘缓存零收益，直接关。
        // 写侧同理：encodePng 走 ImageIO.write，也吃这个开关。
        ImageIO.setUseCache(false)
    }

    /**
     * 块序自下而上倒回：`blockSize = floor(h / num)`、**余数归最后一块**、
     * 目标顺序 = 原块 `num-1 … 0`。与 Android 侧同一套算式。
     *
     * @return 还原后的 PNG 字节；`null` = 高度不够切 `num` 块（这是"还原不了"的一种，
     *   与"解不了码"必须分开报，见 [[feedback-degrade-paths-must-fail-loud]]）
     */
    fun descrambleBlocks(img: BufferedImage, num: Int): ByteArray? {
        if (num <= 1) return encodePng(img)
        val w = img.width
        val h = img.height
        val blockSize = h / num
        if (blockSize <= 0) return null
        val remainder = h - blockSize * num
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        var y = 0
        for (i in num - 1 downTo 0) {
            val start = i * blockSize
            val end = i * blockSize + blockSize + if (i == num - 1) remainder else 0
            g.drawImage(img, 0, y, w, end, 0, start, w, end - start, null)
            y += end - start
        }
        g.dispose()
        return encodePng(out)
    }

    /** 相邻行的平均像素差：自然图偏低，被切块打乱的图会在块界处出现大跳变 —— "已还原"的量化判据 */
    fun verticalGradient(img: BufferedImage): Double {
        var sum = 0.0
        var n = 0
        for (y in 0 until img.height - 1) {
            for (x in 0 until img.width step 3) {
                val a = img.getRGB(x, y)
                val b = img.getRGB(x, y + 1)
                sum += Math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
                sum += Math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
                sum += Math.abs((a and 0xFF) - (b and 0xFF))
                n += 3
            }
        }
        return if (n == 0) 0.0 else sum / n
    }

    /**
     * 解码失败时把原始异常带出去。
     * 这里原本写作 `runCatching{...}.getOrNull()`，结果打包后 ImageIO 抛的真异常被吞成
     * 一句"页图解不了码"，把格式问题和插件问题混成一个 —— 降级路径不许静默交回 null。
     */
    fun decode(bytes: ByteArray): BufferedImage = try {
        ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalStateException("没有解码器认这段数据")
    } catch (t: Throwable) {
        throw IllegalStateException(
            "ImageIO ${t::class.java.simpleName}: ${t.message?.take(200)}",
            t,
        )
    }

    /** 头 16 字节的十六进制与可打印形式 —— 解码失败时分清"格式不认"和"数据坏了" */
    fun magic(bytes: ByteArray): String {
        val head = bytes.take(16).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        val ascii = bytes.take(16).joinToString("") {
            if (it.toInt() in 32..126) it.toInt().toChar().toString() else "."
        }
        return "$head | $ascii"
    }

    /** 运行期真认得哪些格式（TwelveMonkeys 靠 ServiceLoader 注册，打包后最容易掉的就是它） */
    fun readerFormats(): String =
        ImageIO.getReaderFormatNames().map { it.lowercase() }.distinct().sorted().joinToString(",")

    fun encodePng(img: BufferedImage): ByteArray {
        val bos = ByteArrayOutputStream()
        ImageIO.write(img, "png", bos)
        return bos.toByteArray()
    }
}
