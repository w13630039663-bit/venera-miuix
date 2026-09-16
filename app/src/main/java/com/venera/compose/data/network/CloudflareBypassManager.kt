package com.venera.compose.data.network

import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Cloudflare 智能过盾调度管理器。
 *
 * 核心逻辑：
 * 1. 识别 403/503 及 Cloudflare 特征头/特征体。
 * 2. 对同一 Host 的过盾请求进行防抖合并，只拉起一次 WebView 过盾窗口。
 * 3. 过盾成功后，同步捕获真实 WebView UA 与 `cf_clearance` Cookie，
 *    回写至持久化 CookieJar 与 UserAgentPolicy。
 */
object CloudflareBypassManager {

    private const val TAG = "CloudflareBypass"

    /** 正在进行的过盾任务，key 为 Host */
    private val activeBypassDeferred = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /**
     * 判断响应是否命中了 Cloudflare 的挑战拦截（403/503 + cf-mitigated / 特征签名）
     */
    fun isCloudflareChallenge(response: Response, bodySnippet: String? = null): Boolean {
        val code = response.code
        if (code != 403 && code != 503) return false

        // 1. 响应头检查
        val cfMitigated = response.header("cf-mitigated")
        if (cfMitigated.equals("challenge", ignoreCase = true)) return true

        val server = response.header("Server").orEmpty()
        val isCfServer = server.contains("cloudflare", ignoreCase = true)

        val cfRay = response.header("CF-RAY")
        if (cfRay != null && isCfServer) return true

        // 2. HTML 响应体验证
        if (!bodySnippet.isNullOrBlank()) {
            if (bodySnippet.contains("challenge-platform", ignoreCase = true) ||
                bodySnippet.contains("Just a moment...", ignoreCase = true) ||
                bodySnippet.contains("cf-browser-verification", ignoreCase = true) ||
                bodySnippet.contains("Turnstile", ignoreCase = true)
            ) {
                return true
            }
        }

        return isCfServer
    }

    /**
     * 阻塞/挂起等待指定 URL 的 Cloudflare 过盾完成。
     * 若已有针对该 Host 的过盾正在进行，则复用该等待。
     */
    suspend fun bypass(context: Context, targetUrl: String): Boolean = withContext(Dispatchers.Main) {
        val host = targetUrl.toHttpUrlOrNull()?.host?.lowercase() ?: return@withContext false

        val existingDeferred = activeBypassDeferred[host]
        if (existingDeferred != null) {
            Log.i(TAG, "Reusing existing bypass task for host: $host")
            return@withContext existingDeferred.await()
        }

        val deferred = CompletableDeferred<Boolean>()
        activeBypassDeferred[host] = deferred

        try {
            Log.i(TAG, "Launching CloudflareBypassActivity for $targetUrl")
            val intent = Intent(context, CloudflareBypassActivity::class.java).apply {
                putExtra(CloudflareBypassActivity.EXTRA_URL, targetUrl)
                putExtra(CloudflareBypassActivity.EXTRA_HOST, host)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            val success = deferred.await()
            Log.i(TAG, "Bypass completed for $host, success: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Bypass failed for $host", e)
            false
        } finally {
            activeBypassDeferred.remove(host)
        }
    }

    /**
     * 由 CloudflareBypassActivity 调用：过盾通关，提交捕获到的 UA 和 Cookie
     */
    fun onBypassSuccess(context: Context, host: String, url: String, userAgent: String) {
        Log.i(TAG, "onBypassSuccess for host: $host")

        // 1. 记忆并绑定该 host 的真实过盾 UA
        UserAgentPolicy.setCustomUserAgentForHost(host, userAgent)

        // 2. 提取并同步 WebView CookieManager 中的 Cookie 到持久化 CookieJar
        val cookieManager = CookieManager.getInstance()
        val cookieString = cookieManager.getCookie(url)
        if (!cookieString.isNullOrBlank()) {
            val httpUrl = url.toHttpUrlOrNull()
            if (httpUrl != null) {
                val cookieJar = VeneraNetworkClient.getInstance(context).cookieJar
                val okHttpCookies = mutableListOf<Cookie>()
                val pairs = cookieString.split(";")
                for (pair in pairs) {
                    val trimmed = pair.trim()
                    if (trimmed.isEmpty()) continue
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val value = parts[1].trim()
                        Cookie.Builder()
                            .name(name)
                            .value(value)
                            .domain(httpUrl.host)
                            .path("/")
                            .build().also { okHttpCookies.add(it) }
                    }
                }
                cookieJar.saveFromResponse(httpUrl, okHttpCookies)
                Log.i(TAG, "Saved ${okHttpCookies.size} cookies from WebView for $host (includes cf_clearance)")
            }
        }

        // 3. 唤醒所有排队等待该 host 的协程
        activeBypassDeferred[host]?.complete(true)
    }

    /**
     * 由 CloudflareBypassActivity 调用：过盾失败或用户取消
     */
    fun onBypassFailed(host: String) {
        Log.w(TAG, "onBypassFailed for host: $host")
        activeBypassDeferred[host]?.complete(false)
    }
}
