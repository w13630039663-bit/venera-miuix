package com.venera.compose.gallery.data

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.venera.compose.data.network.ImageFetchCallFactory
import com.venera.compose.data.network.VeneraImageFetcher
import com.venera.compose.data.prefs.VeneraPreferences
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
 * - 共享 OkHttpClient（连接池 / Cookie / 限流间隔 / 防盗链头与动态 UA 都在它的拦截器链上）。
 * - [com.venera.compose.data.network.ImageFetchTag] 这个标记（由
 *   [com.venera.compose.data.network.ImageFetchCallFactory] 补上）：图片取流因此**不参与域名熔断**
 *   （否则两笔超时就把 `files.yande.re` 拉黑 60 秒，满屏红），也**不会被拉起交互式过盾**
 *   （同一标记，理由见 [com.venera.compose.data.network.offersInteractiveBypass]：撞盾就原样抛错，
 *   由 HD 钮报一句话）。
 *
 * ⚠️ 2026-09-29 起，画廊的图**不再**经过 `VeneraImageFetcher`（它只接管需要改写字节的两条路，
 * 判据见 [com.venera.compose.data.network.ImagePipelinePolicy.needsBytePipeline]）。改动的直接收益
 * 是那把 [diskCache] **真的会被写入** —— 之前它配了却从没人往里放东西，"缓存上限"是个假开关。
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
     * 磁盘预算的**默认**档位（MB）。
     *
     * Coil 默认那档是「2% 设备存储，夹在 10~250 MB」，与图片字节数无关 ——
     * 这里按「一屏 60 张 × 约 20 KB × 能翻十几屏」的量给到 512 MB，
     * 让回看已翻过的人气榜不必重新下载，同时给原图留不出"顺手囤整站"的空间。
     * 用户改了档位时读 `VeneraPreferences.galleryCacheMaxMb`，这一处只是兜底默认。
     */
    private const val DEFAULT_DISK_CACHE_MB = 512

    /** 画廊独占的缓存目录名（独立目录 = 清它不会连漫画封面一起清掉）。 */
    private const val DISK_DIR_NAME = "gallery_img"

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: build(context.applicationContext).also { instance = it }
    }

    /** 缓存目录（设置页要按它数当前占用）。 */
    fun diskDir(context: Context): File = File(context.applicationContext.cacheDir, DISK_DIR_NAME)

    /**
     * 丢掉实例，下一次 [get] 按**当前偏好**重建。
     *
     * 为什么必须丢：`DiskCache.maxSize` 只在构造时读一次，改完档位不重建就是个假开关。
     * 在途请求继续用旧实例跑完（旧引用还在那笔协程里），不会被打断 —— 这里只切断"以后"。
     */
    fun reset(context: Context) {
        val old = synchronized(this) {
            instance.also { instance = null }
        }
        old?.shutdown()
    }

    /** 立即清空画廊那份图片缓存。返回清掉的字节数（给设置页那句读数用）。 */
    fun clearDiskCache(context: Context): Long {
        val bytes = diskUsedBytes(context)
        get(context).diskCache?.clear()
        return bytes
    }

    /**
     * 当前占用字节数 —— **自己数目录**，不读库的口径。
     *
     * Coil 的 `DiskCache` 没有公开"当前大小"的稳定读数（它有日志文件、有配额，
     * 但那些都不是"这个目录占了多少"），而这一句要摆给用户看，只能按事实数。
     */
    fun diskUsedBytes(context: Context): Long = diskDir(context).walkBottomUp()
        .filter { it.isFile }
        .sumOf { it.length() }

    private fun build(appContext: Context): ImageLoader {
        val okHttpClient = VeneraNetworkClient.getInstance(appContext).okHttpClient
        val cacheMb = VeneraPreferences.getInstance(appContext)
            .galleryCacheMaxMb
            .value
            .takeIf { it > 0 } ?: DEFAULT_DISK_CACHE_MB
        return ImageLoader.Builder(appContext)
            .logger(VeneraImageLogger)
            .memoryCache {
                MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_BYTES).build()
            }
            // 独立目录：清缓存时能单独收拾画廊，不会连漫画封面一起清掉。
            .diskCache {
                DiskCache.Builder()
                    .directory(diskDir(appContext).absolutePath.toPath())
                    .maxSizeBytes(cacheMb * 1024L * 1024L)
                    .build()
            }
            .components {
                // 需要改写字节的图（JM 去混淆 / EH 雪碧图）才走自定义 fetcher；画廊两站的
                // 预览/样例/原图全是普通 http 图片，走下面 Coil 自带的网络 fetcher。
                add(VeneraImageFetcher.Factory(appContext, okHttpClient))
                // ImageFetchCallFactory 只做一件事：给请求盖 ImageFetchTag。
                // 没有它，画廊图片会被算进域名熔断（两笔超时拉黑整站 60 秒），
                // 还可能在取图线程上弹出交互式过盾 —— 图库要求永不弹。
                // 防盗链头与 UA 不靠它：共享客户端上的拦截器对所有请求都生效。
                add(OkHttpNetworkFetcherFactory(callFactory = ImageFetchCallFactory(okHttpClient)))
                // GIF 必须显式挂动图解码器，否则画廊里的动图**只会解出首帧**：Coil 3.6.2 在 Android 上
                // 内置的两个解码器都不动图（`StaticImageDecoder` 名字即结论，`BitmapFactoryDecoder`
                // 显式请求 animated=false），仓库里此前也没有任何一处注册过动图解码器 ——
                // 2026-09-29 用户反馈"目前无法播放 gif"的根因就是这一条，不是地址错。
                // 循环不用另设：`AnimatedImage` 默认无限循环。
                // 注：3.6.2 里这个类叫 `AnimatedImageDecoder`（本机 aar 实测），不叫早期文档里的
                // `ImageDecoderDecoder`。
                //
                // 但**不裸注册它**：动图自动播放那一档只管大图页，墙上必须静帧（一屏动图同时解动画
                // 就是当天那条 OOM 读数）。闸门替它坐在链上，请求没表态就返回 null 落到内置静态解码器。
                // 链上因此**根本没有**动图解码器 —— 这是"从不放动图"能成立的前提，
                // 理由见 [GalleryAnimationGate]。
                add(GalleryAnimationGate())
            }
            .build()
    }
}
