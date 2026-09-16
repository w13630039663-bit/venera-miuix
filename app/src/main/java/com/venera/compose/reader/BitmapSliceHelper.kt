package com.venera.compose.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 超长漫画图片（条漫/韩漫）切片辅助器（借鉴 PuffComic 核心防 OOM 实践）
 * 
 * 背景：
 * 现代条漫单张图片高度动辄上万像素，直接解码会导致 Android Skia/OpenGL 抛出
 * "Texture too large" 致命崩溃或瞬间 OOM。
 * 
 * 方案：
 * 当图片高度超过阈值（如 > 4096px 且长宽比 > 3:1）时，通过 Android 原生 [BitmapRegionDecoder]
 * 将大图切片为 2048px 高度的块（Slice），仅在当前视口滑动到对应块时按需解码与渲染。
 */
object BitmapSliceHelper {
    const val LONG_IMAGE_HEIGHT_THRESHOLD_PX = 4096
    const val LONG_IMAGE_ASPECT_RATIO_THRESHOLD = 3.0f
    const val SLICE_HEIGHT_PX = 2048

    // 限制解码并发数，防止用户极速滑动时 CPU 线程被图片解码占满
    val decodeSemaphore = Semaphore(3)

    // 内存切片缓存（最大 1/8 可用内存）
    private val sliceCache = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt().coerceAtLeast(8 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    data class ImageDimensions(val width: Int, val height: Int) {
        val isLongImage: Boolean
            get() = height >= LONG_IMAGE_HEIGHT_THRESHOLD_PX && (height.toFloat() / width.coerceAtLeast(1)) >= LONG_IMAGE_ASPECT_RATIO_THRESHOLD
    }

    data class ImageSlice(
        val index: Int,
        val rect: Rect,
        val height: Int,
        val totalSlices: Int
    )

    /**
     * 仅探测图片尺寸，零内存开销
     */
    fun probeDimensions(file: File): ImageDimensions? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                ImageDimensions(options.outWidth, options.outHeight)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 将超长图划分为若干切片
     */
    fun calculateSlices(width: Int, height: Int): List<ImageSlice> {
        val slices = mutableListOf<ImageSlice>()
        var top = 0
        var index = 0
        val total = ((height + SLICE_HEIGHT_PX - 1) / SLICE_HEIGHT_PX).coerceAtLeast(1)

        while (top < height) {
            val bottom = (top + SLICE_HEIGHT_PX).coerceAtMost(height)
            slices.add(
                ImageSlice(
                    index = index,
                    rect = Rect(0, top, width, bottom),
                    height = bottom - top,
                    totalSlices = total
                )
            )
            top = bottom
            index++
        }
        return slices
    }

    /**
     * 解码指定切片区域
     */
    suspend fun decodeSlice(
        file: File,
        slice: ImageSlice,
        cacheKeyPrefix: String = file.absolutePath
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${cacheKeyPrefix}_slice_${slice.index}"
        sliceCache.get(cacheKey)?.let { return@withContext it }

        decodeSemaphore.withPermit {
            try {
                val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(file.absolutePath)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(file.absolutePath, false)
                }

                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = decoder.decodeRegion(slice.rect, options)
                decoder.recycle()
                if (bitmap != null) {
                    sliceCache.put(cacheKey, bitmap)
                }
                bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    fun clearCache() {
        sliceCache.evictAll()
    }
}