package com.venera.compose.data.network

import android.content.Context
import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.data.platform.android.AndroidKeyValueStore
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

    /**
     * 定义已搬进 [com.venera.compose.data.platform.UserAgentStrings]（桌面编译面的前置：
     * 画廊的 `GelbooruClient` / `SauceNao` 要这两枚串，而本对象吃 `Context`）。
     * 这里留同名别名，是为了不动六处既有调用点 —— 其中一处是 `gallery/ui/GalleryVideoViewer.kt`，
     * 而"桌面端开工不许改 Android UI"是这轮的硬约束。两枚串的语义差别见那颗文件的 KDoc。
     */
    const val DEFAULT_USER_AGENT = com.venera.compose.data.platform.UserAgentStrings.DEFAULT_USER_AGENT

    const val APP_USER_AGENT = com.venera.compose.data.platform.UserAgentStrings.APP_USER_AGENT

    private val hostUaMap = ConcurrentHashMap<String, String>()
    private var prefs: KeyValueStore? = null

    fun init(context: Context) {
        if (prefs == null) {
            val p = AndroidKeyValueStore(context.applicationContext, PreferenceKeys.PREFS_UA_POLICY)
            prefs = p
            // 加载持久化的 host -> UA 映射（这张表里只有 host→UA 一种字符串值）
            p.keys().forEach { key ->
                val value = p.getString(key, null)
                if (!value.isNullOrBlank()) {
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
        prefs?.put(normalizedHost, userAgent)
    }

    /**
     * 清除指定 host 或全部已绑定的 UA
     */
    fun clear(host: String? = null) {
        if (host == null) {
            hostUaMap.clear()
            prefs?.clear()
        } else {
            val normalizedHost = host.lowercase().removePrefix("www.")
            hostUaMap.remove(normalizedHost)
            prefs?.remove(normalizedHost)
        }
    }
}
