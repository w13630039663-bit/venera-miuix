package com.venera.compose.data.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * 这条请求**能不能**弹过盾窗口要用户手动过。
 *
 * 图片取流（[ImageFetchTag]）一律不能，两条实测理由：
 * 1. **弹了也白弹**。过盾成功后 [CloudflareBypassManager.onBypassSuccess] 会把 WebView 那串
 *    真实浏览器 UA 绑到该 host 上，拦截器再拿它重放原请求。而 Cloudflare 对 donmai 的判定
 *    实测是**按指纹不按 UA**：同一张 `/original/` 图，`Venera/1.0 (Android)` 回 200，
 *    换成 Chrome 移动串（哪怕补全 `Sec-Fetch-*`）回 403 + `cf-mitigated: challenge` ——
 *    一个自称 Chrome 却没有 Chrome TLS/H2 指纹的请求，bot 分数比老实报自己是谁的还高。
 *    也就是说"过盾 → 换浏览器 UA → 重放"这条链在图片上必然自相矛盾，越治越死。
 * 2. **代价在别的线程上**。这里是 `runBlocking` 等一个人机交互，跑在 Coil 的取图线程上：
 *    一张图能把一个 worker 挂到用户点"取消"为止，同屏其余图片全排队。
 * 所以图片撞盾 = 原样抛错，由调用方说一句话（画廊 HD 档就是这么报的）。
 *
 * 除图片外，[NoInteractiveBypassTag] 也走这一条 —— 那是画廊日榜这类
 * "整屏等结果"的 API 流量：等一个人机交互会把它挂死（详见那个 tag 的注释）。
 * API/网页流量照旧走交互式过盾 —— 那条链对它有效（拿到 `cf_clearance` 后重放确实通）。
 */
internal fun offersInteractiveBypass(request: Request): Boolean =
    request.tag(ImageFetchTag::class.java) == null && request.tag(NoInteractiveBypassTag::class.java) == null

/**
 * Cloudflare 智能过盾拦截器。
 * 拦截 403/503，检测 Cloudflare 特征，若触发则挂起拉起 WebView 过盾，
 * 通关后注入最新 cf_clearance 与 UA 并自动重放请求。
 * 图片取流不在此列，判据见 [offersInteractiveBypass]。
 */
class CloudflareBypassInterceptor(private val context: Context) : Interceptor {

    private val tag = "CloudflareBypass"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        // 检查是否遇到 403 或 503 且具有 Cloudflare 特征
        if (response.code == 403 || response.code == 503) {
            val peekSnippet = try {
                response.peekBody(2048).string()
            } catch (e: Exception) {
                null
            }

            if (CloudflareBypassManager.isCloudflareChallenge(response, peekSnippet)) {
                Log.w(tag, "Cloudflare challenge detected on: ${request.url}")
                val urlString = request.url.toString()
                val host = request.url.host

                if (!offersInteractiveBypass(request)) {
                    // 不弹过盾窗口的流量（图片 / 日榜这类整屏等待）：**原样把 403 交回**，
                    // 调用方抛错、UI 报一句话。挂在这里等交互会把整屏挂死。
                    Log.w(tag, "该请求不弹过盾窗口，直接失败: $urlString")
                    return response
                }

                // 挂起启动过盾流程
                val bypassSuccess = runBlocking {
                    CloudflareBypassManager.bypass(context, urlString)
                }

                if (bypassSuccess) {
                    Log.i(tag, "Bypass successful! Retrying request for $urlString with updated UA & Cookies")
                    response.close()

                    // 使用过盾后的真实 UA 和最新 CookieJar 重新构建请求
                    val updatedUa = UserAgentPolicy.getUserAgentForHost(host)
                    val newRequest = request.newBuilder()
                        .header("User-Agent", updatedUa)
                        .build()

                    val retried = chain.proceed(newRequest)
                    // 重试**仍然**是被挡的样子，说明带过去的 cookie / UA 组合不被认账
                    // （最常见是那枚 cf_clearance 从未被真正激活，或站点还要求 __cf_bm）。
                    // 这一行是"过盾成功了却还是 403"这类状态里唯一说得清事实的读数。
                    if (retried.code == 403 || retried.code == 503) {
                        Log.w(
                            tag,
                            "过盾后重试仍被拒: $host code=${retried.code} " +
                                "ua=${updatedUa.take(40)}... server=${retried.header("Server") ?: "-"}",
                        )
                    }
                    return retried
                } else {
                    Log.w(tag, "Bypass cancelled or failed for $urlString")
                }
            }
        }

        return response
    }
}
