package com.venera.compose.gallery.data

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.venera.compose.data.network.VeneraImageFetcher
import com.venera.compose.data.network.VeneraImageLogger
import com.venera.compose.data.network.VeneraNetworkClient
import java.io.File
import okio.Path.Companion.toPath

/**
 * 画廊**自己的** ImageLoader —— 这是"图库与漫画完全隔离"里最实在的一条，不是洁癖。
 *
 * 为什么不能共用 `VeneraApp` 那个单例：应用级那套 `memoryCache` / `diskCache` 一个都没显式设过，
 * 全吃 Coil 默认值。图站一批就是几百张 preview，灌进去会把漫画侧的封面挤掉；
 * 反过来给共用的那个开大预算，又会把漫画链路的口径带偏（推导见方案 §一.3）。
 * 所以：**独立目录 + 独立预算，漫画侧那行默认配置一个字不改**。
 *
 * 仍然复用的是纯技术层那两件：
 * - `VeneraImageFetcher.Factory` —— 它给请求打 [com.venera.compose.data.network.ImageFetchTag]，
 *   图片流因此**不参与域名熔断**（否则两笔超时就把 `files.yande.re` 拉黑 60 秒，满屏红），
 *   也**不会被拉起交互式过盾**（同一标记，理由见
 *   [com.venera.compose.data.network.offersInteractiveBypass]：撞盾就原样抛错，由 HD 钮报一句话）。
 *   它的 JM 去混淆 / EH 雪碧图两条分支都按 URL 特征判定，对 yande.re 地址不触发，直接透传字节。
 * - 共享 OkHttpClient（连接池 / Cookie / 限流间隔）。
 */
object GalleryImageLoader {

    /**
     * 内存预算。
     *
     * 一张 preview 实测约 20 KB（300×207 那一档），解码后约 0.25 MB；
     * 64 MB ≈ 250 张解码位图，够两三个屏幕的滚动窗口 + 回退余量，
     * 又不会像默认值那样按设备内存百分比无界膨胀。
     */
    private const val MEMORY_CACHE_BYTES = 64L * 1024 * 1024

    /**
     * 磁盘预算。
     *
     * Coil 默认那档是「2% 设备存储，夹在 10~250 MB」，与图片字节数无关 ——
     * 这里按「一屏 60 张 × 约 20 KB × 能翻十几屏」的量给到 512 MB，
     * 让回看已翻过的人气榜不必重新下载，同时给原图留不出"顺手囤整站"的空间。
     */
    private const val DISK_CACHE_BYTES = 512L * 1024 * 1024

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: build(context.applicationContext).also { instance = it }
    }

    private fun build(appContext: Context): ImageLoader {
        val okHttpClient = VeneraNetworkClient.getInstance(appContext).okHttpClient
        return ImageLoader.Builder(appContext)
            .logger(VeneraImageLogger)
            .memoryCache {
                MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_BYTES).build()
            }
            // 独立目录：清缓存时能单独收拾画廊，不会连漫画封面一起清掉。
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "gallery_img").absolutePath.toPath())
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .components {
                add(VeneraImageFetcher.Factory(appContext, okHttpClient))
                add(OkHttpNetworkFetcherFactory(callFactory = okHttpClient))
            }
            .build()
    }
}
