package com.venera.compose.data.network

import android.content.Context
import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.FileSystem
import java.io.IOException

/**
 * 漫画图片的**字节管道** Fetcher —— 只服务"必须改写二进制"的两条路。
 *
 * 1. JM 去混淆、2. EH 雪碧图裁剪：这两条要把整张图解开重排，只能先把字节读进内存，
 *    结果直接以位图交回 Coil（见 [ImagePipelinePolicy.needsBytePipeline] 与 [Factory] 的接管条件）。
 * 3. 顺带统一应用防盗链 ([ImageHeaderPolicy])、动态 UA 与过盾 Cookie —— 但这些共享客户端
 *    上的拦截器本来就做，**普通图片（画廊、封面）已不再走这里**，改由 Coil 的
 *    `OkHttpNetworkFetcherFactory` + [ImageFetchCallFactory] 取流：流式落盘、文件源解码、
 *    每主机并发有上限。别把这条改回去 —— 一屏动图在旧路上要占两三份堆内字节，会 OOM。
 */
class VeneraImageFetcher(
    private val url: String,
    private val options: Options,
    private val okHttpClient: OkHttpClient,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val hasSpriteCrop = url.contains("@x=") || url.contains("@y=")
        val cleanUrl = if (hasSpriteCrop) url.substringBefore('@') else url
        val cropRange = if (hasSpriteCrop) ImagePipelinePolicy.parseCropRange(url.substringAfter('@')) else null

        val (rawBytes, mimeType) = if (hasSpriteCrop) {
            ImagePipelinePolicy.fetchSpriteSheet(cleanUrl, okHttpClient)
        } else {
            val reqBuilder = Request.Builder()
                .url(cleanUrl)
                // 图片取流不进域名熔断口径：两笔超时就把整站封面拉黑 60s（理由见 ImageFetchTag）。
                .tag(ImageFetchTag::class.java, ImageFetchTag())

            // 注入防盗链请求头。
            // **UA 除外**：它交给 `VeneraNetworkClient.userAgentFor` 那一条优先级链
            // （过盾绑定的 host UA > ImageHeaderPolicy 钉的 UA > 全局默认）。
            // 在这里显式塞上钉的那串，会让"过完盾之后的图片请求"带着**与 `cf_clearance` 不配对的 UA**
            // —— 盾会当场再拦一次，这条链就永远收不了尾。
            val dynamicHeaders = ImageHeaderPolicy.headersFor(cleanUrl)
            for ((k, v) in dynamicHeaders) {
                if (k.equals("User-Agent", ignoreCase = true)) continue
                reqBuilder.header(k, v)
            }

            val request = reqBuilder.build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                response.close()
                throw IOException("HTTP ${response.code}: Failed to fetch comic image at $cleanUrl")
            }

            val body = response.body ?: throw IOException("Empty response body for image: $cleanUrl")
            val bytes = body.bytes()
            val mime = response.header("Content-Type")?.substringBefore(";")?.trim()
            bytes to mime
        }

        // 需要字节级变换的两条路（JM 去混淆 / EH 雪碧图裁剪）直接交位图给 Coil：
        // 旧实现把重排结果 JPEG 重编码、再让 Coil 解一次，每张白走一遍编解码往返，
        // 而且重编码出来的字节拿不到 Coil 解码器的降采样（所以这里自己补 sampleSize）。
        // 两条路互斥：`@x=` 裁剪后缀只由 EH 雪碧图挂上，JM 的 /media/photos/ 不会带，
        // 所以命中去混淆时可以直接 return，不必再裁。
        val scrambleNum = ImagePipelinePolicy.getScrambleNum(cleanUrl)
        if (scrambleNum > 1) {
            // 边界映射要按**原图高**算（服务端那份切块依据），所以 bounds 在这里解一次，
            // 同时喂给降采样决策和去混淆 —— 别再让去混淆自己拿降采样高重新除。
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            // 第二次按原尺寸重试 = 本次改动前的行为。宁可慢，也绝不能把未还原的字节往下交：
            // 下面那条 SourceFetchResult 会让 Coil 原样解码，观感就是条状撕裂图，
            // 而且这张错图还会按 cacheKeyFor 的 key 进内存缓存被锁死。
            val descrambled = ImagePipelinePolicy.decodeAndDescramble(
                rawBytes, scrambleNum, sampleSizeFor(bounds, scrambleNum), bounds.outHeight,
            ) ?: ImagePipelinePolicy.decodeAndDescramble(rawBytes, scrambleNum, 1, bounds.outHeight)
                ?: throw IOException("JM 去混淆失败（$cleanUrl, num=$scrambleNum），不交未还原字节")
            return ImageFetchResult(
                image = descrambled.asImage(),
                isSampled = true,
                dataSource = DataSource.NETWORK,
            )
        }
        if (cropRange != null) {
            val cropped = ImagePipelinePolicy.cropToBitmap(rawBytes, cropRange)
            if (cropped != null) {
                return ImageFetchResult(
                    image = cropped.asImage(),
                    isSampled = false,
                    dataSource = DataSource.NETWORK,
                )
            }
        }

        val buffer = Buffer().write(rawBytes)
        val imageSource = ImageSource(
            source = buffer,
            fileSystem = FileSystem.SYSTEM
        )

        return SourceFetchResult(
            source = imageSource,
            mimeType = mimeType ?: "image/jpeg",
            dataSource = DataSource.NETWORK
        )
    }

    /**
     * 2 的幂降采样自己定（位图短路把 Coil 的解码器整个绕过了，不补就会按原尺寸解，
     * 预览一次进来十张 7 MiB 的位图）。三条硬约束，每条都对应一次"图又裂成条"：
     *  1. 盒子必须是正数像素。0（首帧未测量、动画起手的 0 高容器）或拿不到像素维度时
     *     一律返回 1 —— 覆盖式 while 循环在 box<=0 时条件恒真，sample 会一路翻倍到 Int
     *     溢出：要么 `sample*2` 归零后除零崩，要么把负数送进 inSampleSize 解不出图。
     *     （实测 box=0x0 与 -1x-1 时旧写法都走到溢出。）
     *  2. 封顶 [MaxSample]：BitmapFactory 只有 1/2/4/8 是 IDCT 真降采样，再大只是白算一遍。
     *  3. 降完还要给 num 个块各留 [MinScrambleBlockPx] 高，否则重排无从谈起。
     *     （块边界不再靠这条兜着 —— 旧写法以为漂移不超过 sample 像素，实际会逐块累加；
     *     现在边界按原图高映射，误差钉在 1 像素内，见 ImagePipelinePolicy.reorderBlocksBottomUp。）
     */
    private fun sampleSizeFor(bounds: BitmapFactory.Options, num: Int): Int {
        val boxW = (options.size.width as? Dimension.Pixels)?.px ?: return 1
        val boxH = (options.size.height as? Dimension.Pixels)?.px ?: return 1
        if (boxW <= 0 || boxH <= 0) return 1
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return 1
        var sample = 1
        while (sample < MaxSample &&
            bounds.outWidth / (sample * 2) >= boxW &&
            bounds.outHeight / (sample * 2) >= boxH
        ) {
            sample *= 2
        }
        while (sample > 1 && bounds.outHeight / sample < num * MinScrambleBlockPx) {
            sample /= 2
        }
        return sample
    }

    private companion object {
        const val MaxSample = 8
        const val MinScrambleBlockPx = 8
    }

    class Factory(
        private val context: Context,
        private val okHttpClient: OkHttpClient
    ) : Fetcher.Factory<Uri> {

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            // 不改字节的图片一律交回 Coil 的 NetworkFetcher（注册在本 factory 之后）。
            // 判据与下面两条分支共用同一个函数，理由见 ImagePipelinePolicy.needsBytePipeline：
            // 这条路上每张图在 Java 堆里要留两三份编码字节，一屏动图足够把 256 MB 堆吃穿。
            if (!ImagePipelinePolicy.needsBytePipeline(data.toString())) return null
            return VeneraImageFetcher(
                url = data.toString(),
                options = options,
                okHttpClient = okHttpClient,
                context = context
            )
        }
    }
}
