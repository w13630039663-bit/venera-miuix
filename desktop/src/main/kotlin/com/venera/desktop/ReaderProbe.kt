package com.venera.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.composefluent.FluentTheme
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.scrollBy

/**
 * S0-8 判据探针：条漫单列数千图的**滚动帧时间**与**内存**。
 *
 * 两个数分开量，因为它们的成因不同：
 * 1. 纯绘制吞吐 —— LazyColumn 里 3000 项、每项一张 1200×1800 的位图，逐帧滚动取样；
 * 2. 单页解码成本 —— JPEG 解码一张要多少毫秒、解出来占多少 MB。
 * 把 (2) 乘上"一屏要同时驻留几页"就能判断"全驻留"这条路在桌面上是否根本走不通。
 */
private const val PAGES = 3000
private const val PAGE_W = 1200
private const val PAGE_H = 1800

/** 第二相：每项各自解码一份位图（= "绑定时同步解码"的朴素做法），量端到端帧时间 */
private var decodePerItem = false

private val pageBytes: ByteArray by lazy { pageJpegBytes() }

fun main(args: Array<String>) {
    decodePerItem = args.firstOrNull()?.toBoolean() ?: false
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Venera R1-F / S0-8 长列表探针",
            state = rememberWindowState(width = 900.dp, height = 700.dp),
        ) {
            FluentTheme {
                ReaderProbe()
            }
        }
    }
}

@Composable
private fun ReaderProbe() {
    val bitmap = remember { loadOnePageBitmap() }
    val listState = remember { LazyListState() }

    LaunchedEffect(bitmap) {
        // 先量单页解码成本：走 skia 的解码器（= 列表项真正用的那条路），
        // 同一份字节重复 20 次取平均；ImageIO 只作对照
        val bytes = pageBytes
        repeat(3) { decodeSkia(bytes) }
        val t0 = System.nanoTime()
        repeat(20) { decodeSkia(bytes) }
        val perDecodeMs = (System.nanoTime() - t0) / 20 / 1_000_000.0
        val t1 = System.nanoTime()
        repeat(20) { ImageIO.read(java.io.ByteArrayInputStream(bytes)) }
        val imageIoMs = (System.nanoTime() - t1) / 20 / 1_000_000.0
        println(
            "S08_单页解码 ${PAGE_W}x${PAGE_H} skia=%.1f ms/页 ImageIO对照=%.1f ms/页；解码后驻留 %.1f MB/页".format(
                perDecodeMs, imageIoMs, PAGE_W * PAGE_H * 4 / 1048576.0
            )
        )

        // 再量滚动帧时间：每帧推进 160px，取 240 帧
        val frames = ArrayList<Long>(240)
        var prev = 0L
        repeat(240) {
            withFrameNanos { t ->
                if (prev != 0L) frames.add(t - prev)
                prev = t
            }
            listState.scrollBy(160f)
        }
        val sorted = frames.sorted()
        fun pct(p: Double) = sorted[(sorted.size * p).roundToInt().coerceAtMost(sorted.size - 1)] / 1_000_000.0
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576.0
        val maxMb = rt.maxMemory() / 1048576.0
        println("S08_位图实现类=${bitmap::class.java.name} 可显式释放=${bitmap is java.io.Closeable}")
        println(
            "S08_滚动[${if (decodePerItem) "每项各自解码" else "共享一张图"}](${PAGES} 项单列) " +
                "p50=%.2fms p95=%.2fms max=%.2fms 丢帧(>16.7ms)=%d/%d 堆已用=%.0fMB/上限=%.0fMB".format(
                    pct(0.50), pct(0.95), pct(1.0), frames.count { it > 16_700_000 }, frames.size,
                    usedMb, maxMb
                )
        )
        println("S08_DONE")
        // 探针取完数就走，别把 gradle 任务吊在窗口上
        kotlin.system.exitProcess(0)
    }

    if (decodePerItem) {
        // 朴素做法：绑定该项时在组合线程里同步解码。只有正在显示的几项驻留，
        // 代价落在"每滚过一页的那一帧"上 —— 这才是要量的数。
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            items(List(PAGES) { it }) { page ->
                val bmp = remember(page) { decodeSkia(pageBytes) }
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            items(List(PAGES) { it }) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 造一份真实可解码的 1200×1800 JPEG（噪声内容，避免压缩走捷径） */
private fun pageJpegBytes(): ByteArray {
    val img = BufferedImage(PAGE_W, PAGE_H, BufferedImage.TYPE_3BYTE_BGR)
    val rnd = java.util.Random(42)
    val g = img.createGraphics()
    var y = 0
    while (y < PAGE_H) {
        var x = 0
        while (x < PAGE_W) {
            g.color = java.awt.Color(rnd.nextInt(0xFFFFFF))
            g.fillRect(x, y, 12, 12)
            x += 12
        }
        y += 12
    }
    g.dispose()
    val bos = ByteArrayOutputStream()
    ImageIO.write(img, "jpg", bos)
    return bos.toByteArray()
}

private fun loadOnePageBitmap() = decodeSkia(pageBytes)

/** 列表项真正用的那条解码路：skia 解封装 + 拷进堆上 ARGB 位图；中间的 native Image 要关掉 */
private fun decodeSkia(bytes: ByteArray) =
    org.jetbrains.skia.Image.makeFromEncoded(bytes).use { it.toComposeImageBitmap() }
