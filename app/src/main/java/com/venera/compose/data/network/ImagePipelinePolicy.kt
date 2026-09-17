package com.venera.compose.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 图片管道策略中心：接管漫画图片的切片还原、去混淆与解密（如 JMComic / 禁漫分块乱序还原）。
 */
object ImagePipelinePolicy {

    private const val TAG = "ImagePipeline"

    /** 运行时注册的 URL -> 混淆分块数 */
    private val scrambleRegistry = ConcurrentHashMap<String, Int>()

    fun registerScramble(url: String, num: Int) {
        if (num > 1) {
            scrambleRegistry[url] = num
        }
    }

    /**
     * 判断该图片是否需要去混淆，返回分块数；<= 1 表示无需处理。
     */
    fun getScrambleNum(url: String): Int {
        scrambleRegistry[url]?.let { return it }

        // 针对禁漫 (JM) 图片地址自动推导混淆块数：
        // 典型路径: /media/photos/{epId}/{pictureName}.webp (或 .jpg/.png)
        if (isJmPhotoUrl(url)) {
            return calculateJmScrambleNum(url)
        }

        return 0
    }

    private fun isJmPhotoUrl(url: String): Boolean {
        if (url.endsWith(".gif", ignoreCase = true)) return false
        val lower = url.lowercase()
        val isJmDomain = lower.contains("jmapinodeudzn.net") ||
                lower.contains("18comic") ||
                lower.contains("cdntwice.org") ||
                lower.contains("cdnsha.org") ||
                lower.contains("cdnaspa.cc") ||
                lower.contains("cdnntr.cc")
        return isJmDomain && lower.contains("/media/photos/")
    }

    /**
     * 对齐 jm.js 中的 1:1 分块计算算法：
     * - epId < 220980 -> num = 0 (不混淆)
     * - epId < 268850 -> num = 10
     * - epId > 421926 -> MD5(epId + pictureName) 最后一位 charCode % 8 * 2 + 2
     * - 其他 -> MD5(epId + pictureName) 最后一位 charCode % 10 * 2 + 2
     */
    fun calculateJmScrambleNum(url: String): Int {
        return try {
            if (url.endsWith(".gif", ignoreCase = true)) return 0

            // 提取 epId 与 pictureName
            val afterPhotos = url.substringAfter("/media/photos/", "")
            if (afterPhotos.isEmpty()) return 0

            val parts = afterPhotos.split("/")
            if (parts.size < 2) return 0

            val epId = parts[0].toLongOrNull() ?: return 0
            val filePart = parts[1].substringBefore("?")
            val lastDot = filePart.lastIndexOf('.')
            val pictureName = if (lastDot > 0) filePart.substring(0, lastDot) else filePart

            val scrambleId = 220980L
            val num = when {
                epId < scrambleId -> 0
                epId < 268850L -> 10
                epId > 421926L -> {
                    val str = "$epId$pictureName"
                    val md5 = MessageDigest.getInstance("MD5").digest(str.toByteArray(Charsets.UTF_8))
                    val hex = md5.joinToString("") { "%02x".format(it) }
                    val charCode = hex.last().code
                    val remainder = charCode % 8
                    remainder * 2 + 2
                }
                else -> {
                    val str = "$epId$pictureName"
                    val md5 = MessageDigest.getInstance("MD5").digest(str.toByteArray(Charsets.UTF_8))
                    val hex = md5.joinToString("") { "%02x".format(it) }
                    val charCode = hex.last().code
                    val remainder = charCode % 10
                    remainder * 2 + 2
                }
            }
            if (num <= 1) 0 else num
        } catch (e: Exception) {
            Log.w(TAG, "calculateJmScrambleNum failed for $url", e)
            0
        }
    }

    /**
     * 执行禁漫切片逆序还原。
     * 对齐 jm.js 中的 modifyImage：将高度分为 num 块，自下而上重新拼接。
     */
    fun descrambleJmImage(rawBytes: ByteArray, num: Int): ByteArray {
        if (num <= 1) return rawBytes
        return try {
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return rawBytes
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) {
                bitmap.recycle()
                return rawBytes
            }

            val blockSize = height / num
            val remainder = height % num

            val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)
            val srcRect = Rect()
            val dstRect = Rect()

            var y = 0
            for (i in (num - 1) downTo 0) {
                val start = i * blockSize
                val end = start + blockSize + (if (i == num - 1) remainder else 0)
                val currentHeight = end - start

                srcRect.set(0, start, width, end)
                dstRect.set(0, y, width, y + currentHeight)
                canvas.drawBitmap(bitmap, srcRect, dstRect, null)
                y += currentHeight
            }

            val out = ByteArrayOutputStream(rawBytes.size)
            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            bitmap.recycle()
            resultBitmap.recycle()
            out.toByteArray()
        } catch (e: Throwable) {
            Log.e(TAG, "descrambleJmImage error", e)
            rawBytes
        }
    }

    data class CropRange(val x1: Int, val x2: Int, val y1: Int, val y2: Int)

    /** 内存缓存 Sprite Sheet 字节，避免 EH 等画廊中同一张雪碧图被 20+ 个缩略图重复并发下载 */
    private val spriteSheetCache = object : LruCache<String, ByteArray>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    private val spriteInflight = ConcurrentHashMap<String, Deferred<Pair<ByteArray, String?>>>()

    fun parseCropRange(spec: String): CropRange? {
        var x1 = 0
        var x2 = Int.MAX_VALUE
        var y1 = 0
        var y2 = Int.MAX_VALUE
        var matched = false

        val parts = spec.split('&')
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("x=")) {
                val range = trimmed.removePrefix("x=").split('-')
                if (range.size == 2) {
                    val start = range[0].toIntOrNull()
                    val end = range[1].toIntOrNull()
                    if (start != null && end != null) {
                        x1 = start
                        x2 = end
                        matched = true
                    }
                }
            } else if (trimmed.startsWith("y=")) {
                val range = trimmed.removePrefix("y=").split('-')
                if (range.size == 2) {
                    val start = range[0].toIntOrNull()
                    val end = range[1].toIntOrNull()
                    if (start != null && end != null) {
                        y1 = start
                        y2 = end
                        matched = true
                    }
                }
            }
        }
        return if (matched) CropRange(x1, x2, y1, y2) else null
    }

    suspend fun fetchSpriteSheet(
        cleanUrl: String,
        client: OkHttpClient
    ): Pair<ByteArray, String?> = coroutineScope {
        synchronized(spriteSheetCache) {
            spriteSheetCache.get(cleanUrl)
        }?.let {
            return@coroutineScope it to "image/jpeg"
        }

        val deferred = spriteInflight.computeIfAbsent(cleanUrl) {
            async(Dispatchers.IO) {
                val reqBuilder = Request.Builder().url(cleanUrl)
                for ((k, v) in ImageHeaderPolicy.headersFor(cleanUrl)) {
                    reqBuilder.header(k, v)
                }
                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: Failed to fetch sprite sheet at $cleanUrl")
                    }
                    val body = response.body ?: throw IOException("Empty body for sprite sheet at $cleanUrl")
                    val bytes = body.bytes()
                    val mime = response.header("Content-Type")?.substringBefore(";")?.trim() ?: "image/jpeg"
                    synchronized(spriteSheetCache) {
                        spriteSheetCache.put(cleanUrl, bytes)
                    }
                    bytes to mime
                }
            }
        }
        try {
            deferred.await()
        } finally {
            spriteInflight.remove(cleanUrl)
        }
    }

    fun cropSprite(rawBytes: ByteArray, range: CropRange): ByteArray {
        if (rawBytes.isEmpty()) return rawBytes
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            if (origW <= 0 || origH <= 0) return rawBytes

            val x1 = range.x1.coerceIn(0, origW)
            val x2 = range.x2.coerceIn(x1, origW)
            val y1 = range.y1.coerceIn(0, origH)
            val y2 = range.y2.coerceIn(y1, origH)

            val targetW = x2 - x1
            val targetH = y2 - y1
            if (targetW <= 0 || targetH <= 0) return rawBytes

            val rect = Rect(x1, y1, x2, y2)
            val croppedBitmap = try {
                val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(rawBytes, 0, rawBytes.size)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(rawBytes, 0, rawBytes.size, false)
                }
                decoder.decodeRegion(rect, BitmapFactory.Options())
            } catch (e: Throwable) {
                null
            } ?: run {
                val full = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return rawBytes
                val sub = Bitmap.createBitmap(full, x1, y1, targetW, targetH)
                if (sub !== full) full.recycle()
                sub
            }

            val out = ByteArrayOutputStream()
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            croppedBitmap.recycle()
            out.toByteArray()
        } catch (e: Throwable) {
            Log.e(TAG, "cropSprite failed for $range", e)
            rawBytes
        }
    }
}
