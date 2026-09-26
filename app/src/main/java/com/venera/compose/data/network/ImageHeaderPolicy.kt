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
        "jmapinodeudzn.net" to mapOf(
            "Referer" to "https://localhost/",
            "X-Requested-With" to "com.example.app"
        ),
        "18comic.vip" to mapOf(
            "Referer" to "https://localhost/",
            "X-Requested-With" to "com.example.app"
        ),
        "18comic.org" to mapOf(
            "Referer" to "https://localhost/",
            "X-Requested-With" to "com.example.app"
        ),
        "hamreus.com" to mapOf("Referer" to "https://www.manhuagui.com/"),
        "manhuagui.com" to mapOf("Referer" to "https://www.manhuagui.com/"),
        "manhuaren.com" to mapOf("Referer" to "https://www.manhuaren.com/"),
        "komiic.com" to mapOf("Referer" to "https://komiic.com/"),
        "wnacg.com" to mapOf("Referer" to "https://www.wnacg.com/"),
        "wnacg.org" to mapOf("Referer" to "https://www.wnacg.org/"),
        "hitomi.la" to mapOf("Referer" to "https://hitomi.la/"),
        "nhentai.net" to mapOf("Referer" to "https://nhentai.net/"),
        "picacg.com" to mapOf("User-Agent" to "okhttp/3.8.1"),
        // Danbooru 挂在 Cloudflare 上，而本项目的全局默认 UA 是**移动端 Chrome 串** ——
        // 实测拿它去打 cdn.donmai.us，连 180×180 的缩略图都回 403 + cf-mitigated: challenge
        // （5.9 KB 挑战页 HTML 而不是图片）。非浏览器 UA 同一个 URL 就 200。
        // ⚠️ 这条是 donmai.us **独有**的强判据，别当成"所有图站都这样"往外推。
        "donmai.us" to mapOf("User-Agent" to "Venera/1.0 (Android)"),
        // Gelbooru 的图片 CDN —— **必须带 Referer，否则一张图都取不到**。
        //
        // 这是 2026-09-26 真机反馈「搜索出来但全是灰卡」查出来的根因，
        // 走了一段弯路，把正确结论与那段弯路都记下来：
        //
        // **症状**：搜索正常（DAPI 200、条目元数据全对、URL 拼得完全正确），
        // 但缩略图全取不到。OkHttp 报 `HTTP 429`（那是个误导性的表象，
        // 它来自 Coil 重试链），Coil 报 `BitmapFactory returned a null bitmap`。
        //
        // **真相**：站方开了 **hotlink 防盗链**。无 Referer 时：
        //   `HTTP 302 → https://gelbooru.com/hotlink.php?hash=/thumbnails/...`
        // 而那个 hotlink 页最终吐的是一句极具误导性的
        // *"This content is not available in your country"* ——
        // 它读起来像地域封禁，于是很容易顺着"换网络出口"那条死路查下去。
        // ⚠️ **它不是地域问题**：加上下面这个 Referer 就 200，与本机 IP 归属地无关。
        // （真机复测：`Referer: https://example.com/` → 302；
        // `https://gelbooru.com/` 或 `https://www.gelbooru.com/` → 200。）
        //
        // **作用范围**：三档**全部**要（`thumbnails/` / `samples/` / `images/` 实测一致），
        // 所以一条规则覆盖整站，不需要按目录分。
        // **可用镜像**：实测只有 `img4.gelbooru.com` 通，`img1`/`img2`/`img3` 连不上 ——
        // 但 DAPI 回的地址本来就是 `img4`，所以不需要我们改域名。
        //
        // UA 一并带上只为与接口侧（`GelbooruClient.API_USER_AGENT`）保持一致，
        // **不是**必需项：这一站没有 donmai.us 那种"浏览器串必 403"的强判据。
        "gelbooru.com" to mapOf(
            "Referer" to "https://gelbooru.com/",
            "User-Agent" to "Venera/1.0 (Android)",
        ),
        "ehgt.org" to mapOf(
            "Referer" to "https://e-hentai.org/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        ),
        "e-hentai.org" to mapOf(
            "Referer" to "https://e-hentai.org/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        ),
        "exhentai.org" to mapOf(
            "Referer" to "https://exhentai.org/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        ),
        "hath.network" to mapOf(
            "Referer" to "https://e-hentai.org/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        ),
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
        val merged = base.orEmpty() + runtime.orEmpty()
        return merged
    }

    /**
     * 从 URL 取 host。
     *
     * ⚠️ 这里曾经有一个**让整张内置表全部失效**的 bug，留个记录免得再犯：
     * 旧写法是 `substringAfter("://").substringBefore("/").substringBefore("?", "")` ——
     * Kotlin 的 `substringBefore(delim, missingDelimiterValue)` 在**找不到分隔符时返回
     * 第二个参数**，而 host 在 `substringBefore("/")` 之后**不可能再含 `?`**
     * （`?` 属于 query，早被那个 `/` 一起截掉了）。于是这第三步对**所有**正常 URL
     * 都返回 `""`，`takeIf { isNotEmpty() }` 再把它变成 null ——
     * 结果 `headersFor` 恒回空表，**整张防盗链表从来没生效过**。
     * 2026-09-26 真机「Gelbooru 搜得出图、屏上全是灰卡」就是它：
     * 图片请求没带 Referer，站方 302 到 hotlink.php 返回 HTML，Coil 解不出图。
     *
     * 现在按「`?` 或 `/` **谁先出现就截到那里**」取，两种 URL 形态都对：
     * - `https://host/path/x.jpg` → host
     * - `https://host?x=1`（无路径）→ host
     */
    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", "")
        if (afterScheme.isEmpty()) return null
        val cut = listOf(afterScheme.indexOf('/'), afterScheme.indexOf('?'))
            .filter { it >= 0 }
            .minOrNull() ?: afterScheme.length
        return afterScheme.substring(0, cut).takeIf { it.isNotEmpty() }
    }
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
