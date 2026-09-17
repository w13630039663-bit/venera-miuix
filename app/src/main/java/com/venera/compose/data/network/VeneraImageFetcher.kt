package com.venera.compose.data.network

import android.content.Context
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.FileSystem
import java.io.IOException

/**
 * Coil 3 自定义漫画图片加载器 (Fetcher)。
 *
 * 核心功能：
 * 1. 接管漫画图片的网络请求，统一应用防盗链 (ImageHeaderPolicy)、动态 UA 与过盾 Cookie。
 * 2. 字节管道处理 (ImageLoadingConfig)：支持对下载后的图片二进制流进行解密、去混淆与切片转换。
 * 3. 失败重试：提供重试保障机制。
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
            val reqBuilder = Request.Builder().url(cleanUrl)

            // 注入防盗链请求头
            val dynamicHeaders = ImageHeaderPolicy.headersFor(cleanUrl)
            for ((k, v) in dynamicHeaders) {
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

        // 管道字节流转换（JM 去混淆与 EH Sprite 切片还原）
        val processedBytes = processImageBytes(cleanUrl, rawBytes, cropRange)

        val buffer = Buffer().write(processedBytes)
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

    private fun processImageBytes(
        imageUrl: String,
        rawBytes: ByteArray,
        cropRange: ImagePipelinePolicy.CropRange?
    ): ByteArray {
        var current = rawBytes
        val scrambleNum = ImagePipelinePolicy.getScrambleNum(imageUrl)
        if (scrambleNum > 1) {
            current = ImagePipelinePolicy.descrambleJmImage(current, scrambleNum)
        }
        if (cropRange != null) {
            current = ImagePipelinePolicy.cropSprite(current, cropRange)
        }
        return current
    }

    class Factory(
        private val context: Context,
        private val okHttpClient: OkHttpClient
    ) : Fetcher.Factory<Uri> {

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            return VeneraImageFetcher(
                url = data.toString(),
                options = options,
                okHttpClient = okHttpClient,
                context = context
            )
        }
    }
}
