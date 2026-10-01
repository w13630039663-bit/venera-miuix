package com.venera.compose.data.network

import android.content.Context
import com.venera.compose.data.prefs.VeneraPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Venera 核心统一网络引擎（支持运行时重建客户端以应用 DoH / 代理）。
 *
 * 对比原版 Flutter 的 intercepted_client.dart：
 * - OkHttp 4 原生不内置 DoH 解析器，但 okhttp-dnsoverhttps 模块提供 DNS-over-HTTPS。
 * - 当前仅做接入决策（pref_enable_doh → builder.dns()），实际部署依赖脚本引擎那一层的稳定性。
 * - 代理默认走 HTTP 代理（SOCKS 场景极少，未来可补）。
 * - 修改偏好后调用 rebuildClient() 使新请求（包括 Coil 图片）走新配置。
 */
class VeneraNetworkClient private constructor(private val context: Context) {

    /**
     * CookieJar 与 OkHttpClient 都推迟到首次真正使用。真机冷启实测：
     * `PersistentCookieJar()`（prefs 全表读 + 每个 host 一次 gson 解析）要 33~42ms、
     * `buildClient()` 装配五个拦截器要 14~24ms，原先这两笔全压在 Application.onCreate
     * 的主线程上；改延迟后由壳层在 IO 线程预热，首图要用时已经备好。
     */
    val cookieJar: PersistentCookieJar by lazy {
        com.venera.compose.StartupTrace.timed("NetClient: PersistentCookieJar()") {
            PersistentCookieJar(context.applicationContext)
        }
    }
    private val prefs: VeneraPreferences by lazy {
        com.venera.compose.StartupTrace.timed("NetClient: VeneraPreferences.getInstance") {
            VeneraPreferences.getInstance(context)
        }
    }

    @Volatile
    private var _okHttpClient: OkHttpClient? = null

    val okHttpClient: OkHttpClient
        get() = _okHttpClient ?: synchronized(this) {
            _okHttpClient ?: com.venera.compose.StartupTrace.timed("NetClient: buildClient()") {
                buildClient()
            }.also { _okHttpClient = it }
        }

    init {
        // UA 策略刻意**不**跟着 client 一起延迟：CloudflareBypassManager 写 host 绑定 UA
        // 那条路不保证先碰过 OkHttpClient，而 UserAgentPolicy 没 init 时它的 prefs 是 null，
        // setCustomUserAgentForHost 就只在内存里记一笔、静默丢掉持久化（cf_clearance 配不上 UA）。
        com.venera.compose.StartupTrace.timed("NetClient: UserAgentPolicy.init") {
            UserAgentPolicy.init(context)
        }
    }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            // 33 源并发检索场景下，超时必须收紧：过长的 connect timeout 会让
            // 不可达源拖垮整轮搜索（配合 HostCircuitBreaker 快速失败）
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            // OkHttp 默认会为连接失败做路由重试，这里关闭以免死域名被重复消耗
            .retryOnConnectionFailure(false)

        // 响应缓存层：上限走偏好（默认 100MB），改完由 rebuildClient() 生效。
        val cacheDir = java.io.File(context.cacheDir, "venera_http_cache")
        builder.cache(okhttp3.Cache(cacheDir, prefs.httpCacheMaxMb.value * 1024L * 1024L))

        // 代理（支持 HTTP / SOCKS5，后来实装）
        val proxyType = prefs.proxyType.value
        if (proxyType == "HTTP" || proxyType == "SOCKS") {
            val host = prefs.proxyHost.value.ifEmpty { "127.0.0.1" }
            val port = prefs.proxyPort.value
            val type = if (proxyType == "SOCKS") Proxy.Type.SOCKS else Proxy.Type.HTTP
            builder.proxy(Proxy(type, InetSocketAddress(host, port)))
        }

        // 1. UA 策略与 Accept-Language 拦截器（尊重既有 UA，优先使用 host 绑定的过盾 UA）
        builder.addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            if (original.header("User-Agent") == null) {
                requestBuilder.header("User-Agent", userAgentFor(original.url))
            }
            if (original.header("Accept-Language") == null) {
                requestBuilder.header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
            }
            chain.proceed(requestBuilder.build())
        }

        // 2. 域名熔断（必须最先判定，让不可达源毫秒级失败）
        builder.addInterceptor(HostCircuitBreakerInterceptor())

        // 3. 限速、429 指数退避与同 URL 并发去重
        builder.addInterceptor(RateLimitingInterceptor())

        // 4. Cloudflare 挑战拦截与透明过盾
        builder.addInterceptor(CloudflareBypassInterceptor(context))

        // 5. 图片防盗链 Header 注入
        builder.addInterceptor(ImageHeaderInterceptor())

        return builder.build()
    }

    /** 在网络偏好变更后调用，使后续请求走新客户端 */
    fun rebuildClient() {
        _okHttpClient = buildClient()
    }

    /**
     * HTTP 响应缓存当前占用字节数。
     *
     * 只统计网络响应缓存，不含 Coil 图片磁盘缓存与下载目录 —— 设置页的文案必须
     * 说清这一点，否则"缓存"会被用户理解成应用占用的全部空间。
     */
    fun httpCacheSizeBytes(): Long = okHttpClient.cache?.let { c ->
        runCatching { c.size() }.getOrDefault(0L)
    } ?: 0L

    /** 清空 HTTP 响应缓存，返回清理前的字节数（用于提示文案）。 */
    fun clearHttpCache(): Long {
        // 走 okHttpClient 取值器而不是 _okHttpClient：客户端还没建时读 null 会报 0 字节，
        // 而磁盘上可能留着上一轮的缓存 —— 那是个假读数。
        val c = okHttpClient.cache ?: return 0L
        val before = runCatching { c.size() }.getOrDefault(0L)
        runCatching { c.evictAll() }
        return before
    }

    /**
     * 三条路统一成一句话：**非 2xx 一律抛，不把正文交回上游**（判据在 [HttpBodyVerdict]，
     * 可脱离 gradle 单跑）。以前这三处都是「拿正文，拿不到给空值」了事 —— 403 的挑战页、
     * 500 的错误页因此被当成正文喂给了上游解析器：漫画源那边表现为「无结果」或一句 gson 错，
     * 真相是那个源被封了；画廊另存那边更坏，一段 HTML 以"非空正文"通过 `bytes.isEmpty()`
     * 那道关，落盘成一个叫 `.jpg` 的网页，而提示说的是「已保存」。
     *
     * `use` 不是装饰：不走消费正文那条分支时也得把响应关掉，否则连接漏了。
     * 2xx 而没有正文时仍交回空值 —— `GallerySaver` 那头本来就把 0 字节判成失败。
     */
    suspend fun get(url: String, headers: Map<String, String>? = null): String = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(url)
        headers?.forEach { (k, v) -> reqBuilder.header(k, v) }
        okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
            HttpBodyVerdict.body(response.code, "GET", url) { response.body?.string() ?: "" }
        }
    }

    suspend fun post(url: String, jsonBody: String, headers: Map<String, String>? = null): String = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val reqBody = jsonBody.toRequestBody(mediaType)
        val reqBuilder = Request.Builder().url(url).post(reqBody)
        headers?.forEach { (k, v) -> reqBuilder.header(k, v) }
        okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
            HttpBodyVerdict.body(response.code, "POST", url) { response.body?.string() ?: "" }
        }
    }

    suspend fun downloadBytes(url: String, referer: String? = null): ByteArray = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(url)
        if (referer != null) {
            reqBuilder.header("Referer", referer)
        }
        okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
            HttpBodyVerdict.body(response.code, "GET", url) { response.body?.bytes() ?: ByteArray(0) }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: VeneraNetworkClient? = null

        fun getInstance(context: Context): VeneraNetworkClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VeneraNetworkClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

/**
 * 一次请求该用哪一串 UA。三层，从强到弱：
 *
 * 1. **该 host 过盾时绑定的真实 UA**（[UserAgentPolicy]）—— 它必须与 CookieJar 里的
 *    `cf_clearance` 严格配对，换掉就等于把刚过完的盾当场作废；
 * 2. **[ImageHeaderPolicy] 给该 host 钉的 UA**（`donmai.us` 那一串 `Venera/1.0 (Android)`）。
 *    ⚠️ 这一层在 2026-09-26 之前**从来没生效过**：本拦截器排在 `ImageHeaderInterceptor`
 *    外层，先把全局 Chrome 串填上，里层「只补请求里还没有的头」再看到 UA 已存在就跳过 ——
 *    于是「浏览器串打某些图站的 CDN 回 403 + `cf-mitigated: challenge`、非浏览器串回 200」
 *    这条实测结论一行都没落地，**反而正是下载必然撞盾的成因**：下载走的是裸
 *    `Request.Builder()`（不带 UA），拿到的就是那串必然 403 的 Chrome UA。
 * 3. 全局默认（移动端 Chrome 串）。
 */
private fun userAgentFor(url: okhttp3.HttpUrl): String {
    val bound = UserAgentPolicy.getUserAgentForHost(url.host)
    if (bound != UserAgentPolicy.DEFAULT_USER_AGENT) return bound
    return ImageHeaderPolicy.headersFor(url.toString())["User-Agent"] ?: bound
}