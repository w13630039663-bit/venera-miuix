package com.venera.compose.data.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Cloudflare 智能过盾拦截器。
 * 拦截 403/503，检测 Cloudflare 特征，若触发则挂起拉起 WebView 过盾，
 * 通关后注入最新 cf_clearance 与 UA 并自动重放请求。
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

                    return chain.proceed(newRequest)
                } else {
                    Log.w(tag, "Bypass cancelled or failed for $urlString")
                }
            }
        }

        return response
    }
}
