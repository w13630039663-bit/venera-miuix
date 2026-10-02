package com.venera.engineprobe

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * S0-4 探针：**纯 JVM 的 OkHttp（= 桌面侧将来真实用的那颗客户端与 TLS/H2 指纹）**
 * 打一遍本仓已知撞 Cloudflare 的站点，量"不过盾能不能直接出数据"。
 *
 * 判据（方案文档第七节）原文是"拿到 `cf_clearance` 且后续请求 200、`ehentai` 出图"。
 * 本探针先把前半件事量清楚：桌面侧**没有 WebView**（Android 那条交互式过盾的载体根本不存在），
 * 所以只有两种结局 —— 要么这些站点对 JVM OkHttp 本来就放行，要么必须走"浏览器登录后手动导入 cookie"。
 *
 * 读数只报原始字段（code / Server / cf-mitigated / CF-RAY / body 命中的挑战特征），
 * 判定留给方案文档里那套判据，不在这儿再造一份。
 */
private const val UA_APP =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Venera/1.0.0"

/** `ImageHeaderPolicy` 给 donmai 的那条覆盖值，文档记它"非浏览器 UA 同一个 URL 就 200" */
private const val UA_NON_BROWSER = "Venera/1.0 (Android)"

/** 与 `CloudflareBypassManager.isCloudflareChallenge` 同一份 body 特征表（逐字照抄，别改口径） */
private val CHALLENGE_MARKERS = listOf(
    "challenge-platform", "Just a moment...", "cf-browser-verification", "Turnstile",
)

/**
 * 两档一起量：**根页**只说明"这颗指纹过没过 CF"，**业务端点**才说明源能不能用
 * （根页 200 而 API 回挑战页是实测到过的形态）。
 * 端点全部取自本仓现有代码/内置源脚本，不自己编路径。
 */
private val TARGETS = listOf(
    "https://e-hentai.org/",
    "https://e-hentai.org/?f_search=Venera&mode=search",
    "https://exhentai.org/",
    "https://exhentai.org/api.php",
    "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json",
    "https://saucenao.com/",
    "https://saucenao.com/search.php?db=999&mask=16&testsearch=blue",
    "https://danbooru.donmai.us/",
    "https://danbooru.donmai.us/posts.json?limit=1",
    "https://gelbooru.com/",
    "https://gelbooru.com/index.php?page=dapi&s=post&q=index&limit=1",
    "https://yande.re/",
    "https://yande.re/post.json?limit=1",
)

fun main(args: Array<String>) {
    val proxy = args.firstOrNull { it.startsWith("--proxy=") }?.removePrefix("--proxy=")
    val builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
    if (proxy != null) {
        val (h, p) = proxy.split(":")
        builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(h, p.toInt())))
    }
    val client = builder.build()
    println("S04_客户端=JVM OkHttp proxy=${proxy ?: "直连"}")

    for (url in TARGETS) {
        for ((tag, ua) in listOf("app默认" to UA_APP, "非浏览器" to UA_NON_BROWSER)) {
            val outcome = runCatching {
                client.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute()
            }.fold({ r ->
                r.use { resp ->
                    val body = runCatching { resp.peekBody(8192).string() }.getOrDefault("")
                    val hit = CHALLENGE_MARKERS.filter { body.contains(it, ignoreCase = true) }
                    // 200 也可能是"套着 200 的插页"，所以把内容前缀与 cookie 名一起打出来自审
                    val sniff = body.replace(Regex("\\s+"), " ").trim().take(70)
                    val cookieNames = resp.headers("Set-Cookie")
                        .mapNotNull { Regex("^\\s*([^=;]+)=").find(it)?.groupValues?.get(1) }
                        .joinToString(",").ifBlank { "-" }
                    "code=${resp.code} server=${resp.header("Server") ?: "-"} " +
                        "mitigated=${resp.header("cf-mitigated") ?: "-"} ray=${resp.header("CF-RAY") ?: "-"} " +
                        "setCookie=$cookieNames bytes=${body.length} bodyHit=${hit.ifEmpty { "-" }} sniff=[$sniff]"
                }
            }, { e -> "FAIL ${e::class.java.simpleName}: ${e.message?.take(80)}" })
            println("S04_[$tag] $url -> $outcome")
        }
    }
    probeEhentaiImage(client)
    println("S04_DONE")
}

/**
 * "ehentai 出图"这半句要**从搜索页走到详情页、再抠出详情页给的图地址**去打。
 * 上一版直接在搜索页抠第一个 ehgt 链接，结果抓到的是 `opensearchdescription.xml`
 * —— 那是 200 但根本不是"出图"，所以判据得靠**图像签名魔数**收口，不能只看 200。
 */
private fun probeEhentaiImage(client: OkHttpClient) {
    val search = get(client, "https://e-hentai.org/?f_search=Venera&mode=search")
    val gallery = Regex("""https://e-hentai\.org/g/\d+/[0-9a-f]+/""").find(search)?.value
    if (gallery == null) {
        println("S04_出图 FAIL 搜索页没抠到 gallery 地址（html=${search.length} 字节）—— 这条没量到，不是判负")
        return
    }
    val detail = get(client, gallery)
    // 详情页里图地址出现在 `url(...)` 与 `src="..."` 两种上下文里，正则必须把右括号也排除，
    // 否则抠出来的是带尾巴的死链（实测第一版就抓到过 `...webp)` → 404）
    val images = Regex("""https://[a-z0-9.]*ehgt\.org/[^"'\\\s)]+""")
        .findAll(detail).map { it.value }.distinct().take(4).toList()
    if (images.isEmpty()) {
        println("S04_出图 FAIL 详情页 $gallery 没抠到 ehgt 图地址（html=${detail.length} 字节）—— 这条没量到，不是判负")
        return
    }
    for (url in images) {
        val probe = runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", UA_APP).build()).execute().use { r ->
                val bytes = runCatching { r.body?.bytes() }.getOrNull() ?: ByteArray(0)
                val magic = bytes.take(4).joinToString(" ") { "%02X".format(it) }
                "code=${r.code} server=${r.header("Server") ?: "-"} mitigated=${r.header("cf-mitigated") ?: "-"} " +
                    "ct=${r.header("Content-Type") ?: "-"} bytes=${bytes.size} magic=[$magic] 是图像=${looksLikeImage(bytes)}"
            }
        }.fold({ it }, { "FAIL ${it::class.java.simpleName}: ${it.message?.take(80)}" })
        println("S04_出图 $url -> $probe")
    }
}

/** JPEG / PNG / GIF / WebP 的魔数 —— "取到的是图"这件事只认这个，不认 200 */
private fun looksLikeImage(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    val b = bytes
    return (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()) ||
        (b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte()) ||
        (b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()) ||
        (b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte())
}

private fun get(client: OkHttpClient, url: String): String = runCatching {
    client.newCall(Request.Builder().url(url).header("User-Agent", UA_APP).build())
        .execute().use { it.body?.string() ?: "" }
}.getOrElse { "" }
