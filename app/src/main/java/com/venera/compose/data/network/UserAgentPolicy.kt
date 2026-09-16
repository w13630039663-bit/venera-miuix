package com.venera.compose.data.network

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一 User-Agent 策略与记忆管理中心。
 *
 * 功能与规范：
 * 1. 默认移动端 UA：模拟真实 Chrome 浏览器，包含标准移动标识与 Venera/1.0。
 * 2. 域名绑定 UA：Cloudflare 要求请求携带的 User-Agent 必须与生成 cf_clearance 时的 WebView UA
 *    完全一致，否则校验直接失效。这里记录并持久化每个域名专用过盾 UA。
 * 3. 允许请求显式指定的 Header 优先，不强行覆盖脚本自定义的特异性 UA。
 */
object UserAgentPolicy {

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Venera/1.0.0"

    private val hostUaMap = ConcurrentHashMap<String, String>()
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            val p = context.applicationContext.getSharedPreferences("venera_ua_policy", Context.MODE_PRIVATE)
            prefs = p
            // 加载持久化的 host -> UA 映射
            p.all.forEach { (key, value) ->
                if (value is String && value.isNotBlank()) {
                    hostUaMap[key] = value
                }
            }
        }
    }

    /**
     * 获取指定 host 的有效 User-Agent。
     * 优先返回该域名绑定的过盾/自定义 UA，若无则返回全局默认 UA。
     */
    fun getUserAgentForHost(host: String?): String {
        if (host.isNullOrBlank()) return DEFAULT_USER_AGENT
        val normalizedHost = host.lowercase().removePrefix("www.")
        return hostUaMap[normalizedHost] ?: DEFAULT_USER_AGENT
    }

    /**
     * 绑定并持久化指定 host 的专用 User-Agent（如 Cloudflare 过盾后 WebView 提取到的真实 UA）
     */
    fun setCustomUserAgentForHost(host: String, userAgent: String) {
        if (host.isBlank() || userAgent.isBlank()) return
        val normalizedHost = host.lowercase().removePrefix("www.")
        hostUaMap[normalizedHost] = userAgent
        prefs?.edit()?.putString(normalizedHost, userAgent)?.apply()
    }

    /**
     * 清除指定 host 或全部已绑定的 UA
     */
    fun clear(host: String? = null) {
        if (host == null) {
            hostUaMap.clear()
            prefs?.edit()?.clear()?.apply()
        } else {
            val normalizedHost = host.lowercase().removePrefix("www.")
            hostUaMap.remove(normalizedHost)
            prefs?.edit()?.remove(normalizedHost)?.apply()
        }
    }
}
