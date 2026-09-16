package com.venera.compose.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * 防盗链请求头策略中心。
 *
 * 原版由源解析层把 ImageLoadingConfig（Referer / UA / Cookie）交给宿主侧图片加载器；
 * 本项目此前 ChapterPages.headers 生成了却零消费者，导致拷贝漫画、包子等站的
 * 封面与内页必然 403。这里把「按 host 生效的请求头」集中起来，交给 Coil3 使用的
 * OkHttp 客户端统一注入。
 *
 * 用法：拿到章节/详情的 headers 后调用 publishForUrls，之后命中同 host 的图片请求自动带上。
 */
object ImageHeaderPolicy {

    /** 运行时发布的规则：host(或后缀) -> 请求头，优先级高于内置 */
    private val perHost = ConcurrentHashMap<String, Map<String, String>>()

    /** 内置规则：不依赖解析结果的通用防盗链 */
    private val builtin: Map<String, Map<String, String>> = mapOf(
        "mangadex.org" to mapOf("Referer" to "https://mangadex.org/"),
        "mangadex.cc" to mapOf("Referer" to "https://mangadex.org/"),
        "copymanga.com" to mapOf("Referer" to "https://www.copymanga.com/"),
        "copymanga.org" to mapOf("Referer" to "https://www.copymanga.org/"),
        "copymanhua.com" to mapOf("Referer" to "https://www.copymanhua.com/"),
        "baozimh.org" to mapOf("Referer" to "https://baozimh.org/"),
        "uc1zali8.com" to mapOf("Referer" to "https://baozimh.org/"),
    )

    /** 从一批图片 URL 推出各自的 host，并绑定这批请求头 */
    fun publishForUrls(urls: List<String>, headers: Map<String, String>) {
        if (headers.isEmpty()) return
        for (u in urls) {
            val host = hostOf(u) ?: continue
            perHost[host] = (perHost[host].orEmpty() + headers)
        }
    }

    fun publish(host: String, headers: Map<String, String>) {
        val h = host.lowercase().removePrefix("www.")
        if (h.isEmpty() || headers.isEmpty()) return
        perHost[h] = perHost[h].orEmpty() + headers
    }

    fun clear() = perHost.clear()

    /** 取某个 URL 应附加的请求头（运行时发布优先于内置；长后缀赢） */
    fun headersFor(url: String): Map<String, String> {
        val host = hostOf(url)?.lowercase() ?: return emptyMap()
        val runtime = perHost.entries
            .filter { host.endsWith(it.key) }
            .sortedByDescending { it.key.length }
            .firstOrNull()?.value
        val base = builtin.entries
            .filter { host.endsWith(it.key) }
            .sortedByDescending { it.key.length }
            .firstOrNull()?.value
        return base.orEmpty() + runtime.orEmpty()
    }

    private fun hostOf(url: String): String? = url
        .substringAfter("://", "")
        .substringBefore("/", "")
        .substringBefore("?", "")
        .takeIf { it.isNotEmpty() }
}

/**
 * 把 ImageHeaderPolicy 落到 OkHttp 请求链上。
 *
 * 只补「请求里还没有」的头，因此 API/脚本显式设置的头永远不会被覆盖
 * （对齐原版 Network.get 的头语义，也为 S1.5 的 UA 策略改造铺路）。
 */
class ImageHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val extra = ImageHeaderPolicy.headersFor(request.url.toString())
        if (extra.isEmpty()) return chain.proceed(request)
        val builder = request.newBuilder()
        extra.forEach { (name, value) ->
            if (request.header(name) == null) builder.header(name, value)
        }
        return chain.proceed(builder.build())
    }
}
