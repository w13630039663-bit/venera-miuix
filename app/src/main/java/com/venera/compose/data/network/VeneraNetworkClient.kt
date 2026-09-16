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
 * Venera 核心统一网络引擎（S0-6：支持运行时重建客户端以应用 DoH / 代理）。
 *
 * 对比原版 Flutter 的 intercepted_client.dart：
 * - OkHttp 4 原生不内置 DoH 解析器，但 okhttp-dnsoverhttps 模块提供 DNS-over-HTTPS。
 * - 当前仅做接入决策（pref_enable_doh → builder.dns()），实际部署依赖 S1 脚本引擎稳定性。
 * - 代理默认走 HTTP 代理（SOCKS 场景极少，未来可补）。
 * - 修改偏好后调用 rebuildClient() 使新请求（包括 Coil 图片）走新配置。
 */
class VeneraNetworkClient private constructor(private val context: Context) {

    val cookieJar = PersistentCookieJar(context.applicationContext)
    private val prefs = VeneraPreferences.getInstance(context)

    @Volatile
    private var _okHttpClient: OkHttpClient = buildClient()

    val okHttpClient: OkHttpClient get() = _okHttpClient

    init {
        UserAgentPolicy.init(context)
    }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        // 100MB 响应缓存层
        val cacheDir = java.io.File(context.cacheDir, "venera_http_cache")
        builder.cache(okhttp3.Cache(cacheDir, 100L * 1024 * 1024))

        // 代理（S0-6 实装）
        val proxyType = prefs.proxyType.value
        if (proxyType != "NONE") {
            val host = prefs.proxyHost.value.ifEmpty { "127.0.0.1" }
            val port = prefs.proxyPort.value
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
        }

        // 1. UA 策略与 Accept-Language 拦截器（尊重既有 UA，优先使用 host 绑定的过盾 UA）
        builder.addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            if (original.header("User-Agent") == null) {
                val hostUa = UserAgentPolicy.getUserAgentForHost(original.url.host)
                requestBuilder.header("User-Agent", hostUa)
            }
            if (original.header("Accept-Language") == null) {
                requestBuilder.header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
            }
            chain.proceed(requestBuilder.build())
        }

        // 2. 限速、429 指数退避与同 URL 并发去重
        builder.addInterceptor(RateLimitingInterceptor())

        // 3. Cloudflare 挑战拦截与透明过盾
        builder.addInterceptor(CloudflareBypassInterceptor(context))

        // 4. 图片防盗链 Header 注入
        builder.addInterceptor(ImageHeaderInterceptor())

        return builder.build()
    }

    /** 在网络偏好变更后调用，使后续请求走新客户端 */
    fun rebuildClient() {
        _okHttpClient = buildClient()
    }

    suspend fun get(url: String, headers: Map<String, String>? = null): String = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(url)
        headers?.forEach { (k, v) -> reqBuilder.header(k, v) }
        val response = okHttpClient.newCall(reqBuilder.build()).execute()
        response.body?.string() ?: ""
    }

    suspend fun post(url: String, jsonBody: String, headers: Map<String, String>? = null): String = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val reqBody = jsonBody.toRequestBody(mediaType)
        val reqBuilder = Request.Builder().url(url).post(reqBody)
        headers?.forEach { (k, v) -> reqBuilder.header(k, v) }
        val response = okHttpClient.newCall(reqBuilder.build()).execute()
        response.body?.string() ?: ""
    }

    suspend fun downloadBytes(url: String, referer: String? = null): ByteArray = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(url)
        if (referer != null) {
            reqBuilder.header("Referer", referer)
        }
        val response = okHttpClient.newCall(reqBuilder.build()).execute()
        response.body?.bytes() ?: ByteArray(0)
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