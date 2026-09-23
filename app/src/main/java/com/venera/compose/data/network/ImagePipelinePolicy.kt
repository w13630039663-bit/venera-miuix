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
            // 源脚本是权威口径。这里只和「URL 推导」那份对账：一旦 jm.js 改了算法，
            // 预览（走推导）和阅读（走注册）会呈现两种图，而观感只是"图裂成条"，
            // 不记日志就没人知道是哪一份漂了。
            val derived = scrambleRegistry[url] ?: calculateJmScrambleNum(url).takeIf { isJmPhotoUrl(url) }
            if (derived != null && derived != num) {
                Log.w(TAG, "JM 块数分叉 $url：源脚本=$num，URL 推导=$derived")
            }
            scrambleRegistry[url] = num
        }
    }

    /**
     * 图片缓存 key：带块数的版本。
     * 详情页预览那批 URL 从没走过 `comic.onImageLoad`，只拿得到「URL 推导」的块数；
     * 万一它和源脚本那份不一致，两种还原结果会共用同一条内存缓存 —— 先加载的那个把
     * 后加载的锁死。key 里带上块数，两者各占一条，谁也不会覆盖谁。
     */
    fun cacheKeyFor(url: String): String {
        val num = getScrambleNum(url)
        return if (num > 1) "$url#jm$num" else url
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
        val hasPhotoPath = lower.contains("/media/photos/")
        val isJmDomain = lower.contains("jmapinode") ||
                lower.contains("jmapic") ||
                lower.contains("18comic") ||
                lower.contains("cdntwice.org") ||
                lower.contains("cdnsha.org") ||
                lower.contains("cdnaspa.cc") ||
                lower.contains("cdnntr.cc") ||
                lower.contains("asjmapic")
        // JM CDN 动态分流节点（cdn-msp.jmapic.net 等）域名难以穷举：
        // 只要路径是 /media/photos/ 且域名命中任一已知 JM 段，或显式带 cdn-msp 前缀，
        // 一律按禁漫混淆图片处理（分块数再由 epId 算法精确判定，误判代价仅一次位运算）。
        return hasPhotoPath && (isJmDomain || lower.contains("cdn-msp"))
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
            // pictureName 必须与 jm.js 里那段 for 循环逐字同串：它取的是「最后一个 '/' 之后、
            // 整条 URL 末尾往前数 5 个字符之前」，也就是**无条件砍掉 5 个尾字符**，不是去扩展名。
            // .webp（含点正好 5 字符）两种切法等价；.jpg（4 字符）会多砍一位数字 → MD5 入参不同
            // → 块数不同 → 还原出条状撕裂图（实测约 87% 的页会分叉）。
            val lastSlash = url.lastIndexOf('/')
            val nameEnd = (url.length - 5).coerceAtLeast(lastSlash + 1)
            val pictureName = url.substring(lastSlash + 1, nameEnd)

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
     * 执行禁漫切片逆序还原，返回重编码前的**位图**。
     * 交图给 Coil 的短路路径走这里：旧链路是 解码 → 画 → JPEG 编码 → Coil 再解码，
     * 每张白走一遍编解码往返，且重编码后的字节拿不到 Coil 的降采样 —— 1080×1700 的页
     * ARGB_8888 一张就是 7 MiB，解码图 / 重排图 / 重解图三份同时在峰值就是 22 MiB。
     * [sampleSize] 由请求盒子决定（2 的幂，位图短路绕过了 Coil 的解码器，降采样得自己做）。
     *
     * [originalHeight] 必须是**降采样前**的原图高（`inJustDecodeBounds` 那份 outHeight）。
     * 服务端切块按的是原图高，降采样后不能拿降采样高重新除一遍：每次 floor 的余量会
     * 逐块累加，块数越多、块高越小漂得越狠（实测 1700 高 12 块降 2 倍时底部漂到 5~6 像素，
     * 而块高才 70 像素 ≈ 8%；JPEG 的 IDCT 降采样高还未必等于 ⌊H/s⌋，误差更大）。
     * 观感就是预览网格里那种"细横条错位 + 局部重复"，而阅读器（盒子够大、sample=1）正常。
     * 现在改成把服务端的边界**映射**进降采样空间，误差钉在 1 像素内。
     * sampleSize 为 1 时位图本身就是原尺寸，[originalHeight] 留 0 即可（两者等价）。
     *
     * 返回 null 表示这次没能还原（解码失败或高不够切 num 块），调用方必须换尺寸重试，
     * 不能把结果或原始字节当作图交出去。
     */
    fun decodeAndDescramble(
        rawBytes: ByteArray,
        num: Int,
        sampleSize: Int,
        originalHeight: Int = 0,
    ): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts) ?: return null
            if (num <= 1) return decoded
            val reordered = reorderBlocksBottomUp(decoded, num, originalHeight)
            // 重排没做成必须返回 null：交回 decoded 就是交回**未还原**的图，观感是条状撕裂。
            decoded.recycle()
            reordered
        } catch (e: Throwable) {
            Log.e(TAG, "decodeAndDescramble error", e)
            null
        }
    }

    /**
     * 与 jm.js 的 modifyImage 同构：把高度分 num 块，自下而上重排。
     *
     * 边界一律按 [originalHeight]（服务端切块依据的那份高度）算，再线性映射到位图实际高度上，
     * 所以降采样与否都不改变还原结果。返回 null = 这份位图高到不够切 num 块，
     * 重排无从谈起，调用方必须换更大的高度重试，而不是把未还原的图往下交。
     */
    private fun reorderBlocksBottomUp(bitmap: Bitmap, num: Int, originalHeight: Int): Bitmap? {
        val width = bitmap.width
        val height = bitmap.height
        if (num <= 0 || width <= 0 || height <= 0) return null
        // 未提供原图高 = 位图就是原尺寸（sampleSize 1 的两条调用路：下载落盘、去混淆重试兜底）。
        val srcHeight = if (originalHeight > 0) originalHeight else height
        if (srcHeight < height) {
            // 原图高比位图还矮说明传错了映射基准；此时按位图高算，退化成旧行为而不是画错边界。
            Log.w(TAG, "JM 去混淆边界基准异常：原高=$srcHeight < 位图高=$height")
        }
        // jm.js 那份：blockSize = floor(原高/num)，余数全给最后一块（最底下那块）。
        val blockSize = srcHeight / num
        if (blockSize <= 0) return null
        val remainder = srcHeight - blockSize * num

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val srcRect = Rect()
        val dstRect = Rect()

        // 原图像素 y → 位图像素 y。单调不减，所以相邻块映射后仍然首尾相接；
        // 最后一块的末端正好落在 height（map(srcHeight) = height）。
        fun map(y: Int): Int = (y.toLong() * height / srcHeight).toInt()

        var y = 0
        for (i in (num - 1) downTo 0) {
            val start = map(i * blockSize)
            val end = map(i * blockSize + blockSize + (if (i == num - 1) remainder else 0))
            val currentHeight = end - start
            // 降采样把某块压到 0 高就没有可重排的余量（旧实现在这里会画出空块）。
            if (currentHeight <= 0) {
                resultBitmap.recycle()
                return null
            }
            srcRect.set(0, start, width, end)
            dstRect.set(0, y, width, y + currentHeight)
            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
            y += currentHeight
        }
        return resultBitmap
    }

    /**
     * 下载落盘用的字节版还原：磁盘要的是编码后的图片文件，所以这里仍然要 compress。
     * 显示路径请用 [decodeAndDescramble]，别再走一遍编解码。
     */
    fun descrambleJmImage(rawBytes: ByteArray, num: Int): ByteArray {
        if (num <= 1) return rawBytes
        val bitmap = decodeAndDescramble(rawBytes, num, 1) ?: return rawBytes
        return try {
            val out = ByteArrayOutputStream(rawBytes.size)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray()
        } catch (e: Throwable) {
            Log.e(TAG, "descrambleJmImage error", e)
            rawBytes
        } finally {
            bitmap.recycle()
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
                val reqBuilder = Request.Builder()
                    .url(cleanUrl)
                    // 图片取流不进域名熔断口径（理由见 ImageFetchTag）。
                    .tag(ImageFetchTag::class.java, ImageFetchTag())
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

    /**
     * 从雪碧图里裁出单个缩略图，直接返回位图（同 [decodeAndDescramble]：显示路径不再
     * 编解码往返）。返回 null 表示裁剪没做成，调用方按原始字节继续走 Coil 解码。
     */
    fun cropToBitmap(rawBytes: ByteArray, range: CropRange): Bitmap? {
        if (rawBytes.isEmpty()) return null
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            if (origW <= 0 || origH <= 0) return null

            val x1 = range.x1.coerceIn(0, origW)
            val x2 = range.x2.coerceIn(x1, origW)
            val y1 = range.y1.coerceIn(0, origH)
            val y2 = range.y2.coerceIn(y1, origH)

            val targetW = x2 - x1
            val targetH = y2 - y1
            if (targetW <= 0 || targetH <= 0) return null

            val rect = Rect(x1, y1, x2, y2)
            try {
                val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(rawBytes, 0, rawBytes.size)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(rawBytes, 0, rawBytes.size, false)
                }
                decoder.decodeRegion(rect, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            } catch (e: Throwable) {
                null
            } ?: run {
                val full = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
                val sub = Bitmap.createBitmap(full, x1, y1, targetW, targetH)
                if (sub !== full) full.recycle()
                sub
            }
        } catch (e: Throwable) {
            Log.e(TAG, "cropToBitmap failed for $range", e)
            null
        }
    }
}
