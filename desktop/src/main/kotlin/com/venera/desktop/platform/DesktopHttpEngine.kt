package com.venera.desktop.platform

import com.venera.compose.data.platform.CacheDirs
import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.PathProvider
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * `:desktop` 侧的 [HttpEngine] —— 画廊那十颗客户端在桌面上唯一取流的地方。
 *
 * ## 数值为什么必须与 Android 侧逐条对齐
 *
 * `GalleryLegGuard` 给每一站的那笔时间预算是 **12s**，而这个数是在"单站最坏 40s 级"这个
 * 前提下定的（readTimeout 20s → 限流重试一次 → 再 20s，callTimeout 45s 封顶）。三枚超时
 * 与 `retryOnConnectionFailure(false)` 全都照 `data/network/VeneraNetworkClient.kt:67-72`
 * 抄同一条：桌面把超时放宽或收紧，[GalleryLegGuard] 那条"慢的一站这一轮缺席、会被说出来"
 * 的取舍就不再是同一件事 —— 而它两边读的是同一颗 [com.venera.compose.gallery.domain.GalleryDailyFeed]。
 *
 * ## 这一层比 Android 侧少了什么（不许当成"等价的一层"）
 *
 * - **没有 Cloudflare 交互式过盾**：桌面没有 `CloudflareBypassInterceptor` 那套 WebView 挑战。
 *   画廊这一路本来就一律打 [com.venera.compose.data.platform.NoInteractiveBypassTag]（不弹盾），
 *   所以少它不影响画廊；但**漫画侧那 33 源不在这一层上**，别把它当成通用网络件复用。
 * - **没有 `HostCircuitBreaker`（每主机熔断）与 `RateLimitingInterceptor`（429 退避）**：
 *   那两颗今天住在 `data.network`，而 `data.network` 是桌面共享面的禁引族
 *   （见 `DesktopSharedFaceLedgerTest.FORBIDDEN`）。`HostCircuitBreaker` 本身其实只依赖
 *   okhttp（可以共享），但开它的 srcDir 会连带把同目录那三颗带 `Context`/`Log` 的文件一起
 *   拉进账本重判 —— 那是集成时该一次做完的事，不在这一颗里顺手做。
 *   所以这里只带一颗最小退避 [RetryAfterInterceptor]：**只对 429 等一次 `Retry-After`，
 *   等完仍失败就如实交出去**，不退化成"悄悄重试到成功"。
 * - **没有 CookieJar**：画廊三站全是匿名读，凭据走 query/form 参数而不是会话 cookie。
 *   将来哪一站要登录态，得先把这条改写成"有意为之"而不是默认继承。
 *
 * 构造期不建 client：与 `AndroidHttpEngine` 同一条纪律，第一次真正取用才装配
 * （`okhttp3.Cache` 会打开磁盘目录，装配不该发生在接线那一刻）。
 */
class DesktopHttpEngine(private val paths: PathProvider) : HttpEngine {

    @Volatile
    private var cached: OkHttpClient? = null

    override val okHttpClient: OkHttpClient
        get() = cached ?: synchronized(this) {
            cached ?: build().also { cached = it }
        }

    private fun build(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor(RetryAfterInterceptor())
        // 与 Android 侧同一个子目录名、同一个默认上限：桌面没有 VeneraPreferences，
        // 那一档在 Android 的默认值就是 100MB，桌面今天不给它设置入口，所以直接落默认档。
        // ⚠️ 落点刻意不走 PathProvider.subDir：那枚默认从 **dataRoot** 起算（它管的是要留下的数据），
        // 而 HTTP 响应缓存是"随时可以清掉"的东西，与 Android 侧的 `File(context.cacheDir, …)` 同档，
        // 所以走 cacheRoot —— 用例 `响应缓存落在cacheRoot的子目录里…` 钉的就是这一条。
        .cache(okhttp3.Cache(CacheDirs.inCache(paths.cacheRoot, CACHE_DIR_NAME), HTTP_CACHE_MAX_BYTES))
        // 代理刻意不写死：OkHttp 默认走 ProxySelector.getDefault()，而 `-Pproxy=` 那条路
        // 已经把 http.proxyHost/http.proxyPort 设进系统属性 —— 在这里再设一遍就是第二条账。
        .build()

    /**
     * 429 的最小退避：**等一次，然后照实交回**。
     *
     * 只等一次是刻意的上限。第二次仍被限流就不再挡路 —— 那一笔失败会顺着
     * [com.venera.compose.gallery.domain.GalleryLegOutcome] 的 `reason` 被界面说出来。
     * `Retry-After` 读不出来（没有该头、或不是秒数）时退回 [FALLBACK_WAIT_MS]，
     * 且上限压在时间预算之内：等满再失败，读到的仍是"这一轮没给内容"，不会变成永久转圈。
     */
    private class RetryAfterInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val first = chain.proceed(chain.request())
            if (first.code != HTTP_TOO_MANY_REQUESTS) return first
            val waitMs = retryWaitMs(first.header("Retry-After"))
            first.close()
            Thread.sleep(waitMs)
            return chain.proceed(chain.request())
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_SEC = 10L
        const val READ_TIMEOUT_SEC = 20L
        const val WRITE_TIMEOUT_SEC = 20L
        const val CALL_TIMEOUT_SEC = 45L

        /** 目录名与 Android 侧 `File(context.cacheDir, "venera_http_cache")` 同串。 */
        const val CACHE_DIR_NAME = "venera_http_cache"
        const val HTTP_CACHE_MAX_MB = 100L
        const val HTTP_CACHE_MAX_BYTES = HTTP_CACHE_MAX_MB * 1024L * 1024L

        /** 这一层不设 UA：画廊那几颗客户端各自带（`GelbooruClient.API_USER_AGENT`、
         *  `SauceNao` 的 `User-Agent` 头），在这里再设一枚就是第二份口径、还会盖住站方要求的特例。 */
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val FALLBACK_WAIT_MS = 1_000L

        /** 压在 [com.venera.compose.gallery.domain.GalleryDailyFeed.PER_SITE_TIMEOUT_MS] 之下。 */
        private const val MAX_WAIT_MS = 5_000L

        /**
         * 429 之后等多久：纯计算，所以单独拿出来给用例钉（这一层没有 HTTP 服务可打，
         * 而"等多久"恰好是三个最容易漂的数：没给头、给了非秒数、给了一个超过预算的数）。
         */
        internal fun retryWaitMs(headerValue: String?): Long =
            headerValue?.toLongOrNull()?.times(1000L)?.coerceIn(0L, MAX_WAIT_MS) ?: FALLBACK_WAIT_MS
    }
}
