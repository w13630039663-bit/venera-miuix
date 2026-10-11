package com.venera.desktop.gallery.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.venera.desktop.gallery.data.desktopCoilBytes
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image as SkiaImage

/**
 * 逐帧播一张动图（批 51）。**只挂在大图那一屏** —— 墙上与浮层照旧静帧，
 * 因为 `coil3.Image` 那个接口在 JVM 侧根本没有"帧"这一档，接不进 `AsyncImage`（设计 §2 第 1 条）。
 *
 * 三条纪律：
 *
 * 1. **字节从 `AsyncImage` 背后同一份缓存里取**（转交 [desktopCoilBytes]，全树只那一颗件），
 *    不另发一趟请求 —— 另起一条轨就是"预取白花流量"那一族（§76 钉过）。
 *    ⚠️ 键不中是**恒空**，所以退化必须出声。
 * 2. **一次只持一帧**：解码复用同一颗 [Bitmap]，不预解全片（几百帧的动图会把堆打穿）。
 * 3. **任何一步不成 ⇒ 交回调用方去摆 `AsyncImage`**（[onFallback]），不许静默留白屏。
 *
 * 帧合成不在这里做：`readPixels(bitmap, frame, priorFrame)` 里 Skia 自己会从
 * `getFrameInfo(i).requiredFrame` 起解（设计 §2 第 2 条），所以 disposal 那一族逻辑我们不重写。
 */
@Composable
internal fun DesktopAnimatedImage(
    url: String,
    tier: DesktopDetailTier,
    animateGifs: Boolean,
    contentDescription: String,
    onFallback: (DesktopAnimatedCause) -> Unit,
) {
    val context = LocalPlatformContext.current
    var frame by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, tier, animateGifs) {
        frame = null
        val bytes = desktopCoilBytes(context, url)
        if (bytes == null) {
            onFallback(DesktopAnimatedCause.NO_CACHE_SNAPSHOT)
            return@LaunchedEffect
        }
        val codec = withContext(Dispatchers.IO) {
            runCatching { Codec.makeFromData(Data.makeFromBytes(bytes)) }.getOrNull()
        }
        if (codec == null) {
            onFallback(DesktopAnimatedCause.DECODE_FAILED)
            return@LaunchedEffect
        }
        try {
            val frames = codec.frameCount
            if (!desktopShouldAnimate(frames, animateGifs)) {
                onFallback(DesktopAnimatedCause.SINGLE_FRAME)
                return@LaunchedEffect
            }
            val durations = IntArray(frames) { codec.getFrameInfo(it).duration }
            val repetition = codec.repetitionCount
            val bitmap = Bitmap().apply { allocPixels(codec.imageInfo) }
            println(
                desktopAnimatedReadout(
                    frames, repetition, cacheHit = true, tier = tier,
                    width = bitmap.width, height = bitmap.height,
                    bytesPerPixel = bitmap.bytesPerPixel,
                ),
            )
            var index = 0
            var loops = 0
            while (true) {
                val prior = if (index == 0) -1 else index - 1
                val next = withContext(Dispatchers.IO) {
                    codec.readPixels(bitmap, index, prior)
                    SkiaImage.makeFromBitmap(bitmap).use { it.toComposeImageBitmap() }
                }
                frame = next
                delay(desktopFrameDelayMs(durations[index]).toLong())
                index++
                if (index >= frames) {
                    index = 0
                    if (repetition >= 0) {
                        loops++
                        if (loops > repetition) return@LaunchedEffect
                    }
                }
            }
        } finally {
            codec.close()
        }
    }
    frame?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            // 恒 Fit：与大图那一屏同一条（Crop 裁掉的边缘放大了回不来）。
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
