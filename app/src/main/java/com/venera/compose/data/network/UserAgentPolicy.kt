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

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Venera/1.0.0"

    /**
     * 我们自己的**产品串**，非浏览器身份。两处图站规则与 Gelbooru 接口侧都必须发同一条 ——
     * 这条不变量此前只有注释级证据（`ImageHeaderPolicy` 里那句"UA 一并带上只为与接口侧
     * `GelbooruClient.API_USER_AGENT` 保持一致"），改一处另一处不会报错，症状是那一站整站取不到图。
     * 现在两边引这一枚常量。
     *
     * ⚠️ 与 [DEFAULT_USER_AGENT] 是两回事：那枚是移动端 Chrome 串（走漫画源与网页），
     * 这枚是"明确表明自己不是浏览器"的串（走图床与部分 DAPI）。别合并。
     */
    const val APP_USER_AGENT = "Venera/1.0 (Android)"

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
