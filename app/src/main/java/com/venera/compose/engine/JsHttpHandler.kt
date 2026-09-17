package com.venera.compose.engine

import android.content.Context
import android.util.Base64
import android.util.Log
import com.venera.compose.data.network.VeneraNetworkClient
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

class JsHttpHandler(private val context: Context) {

    private companion object {
        const val TAG = "VeneraHttp"
    }

    private val gson = com.google.gson.Gson()

    fun handle(req: Map<String, Any?>): Map<String, Any?> {
        val url = req["url"] as? String ?: return mapOf("error" to "No url provided")
        val method = (req["http_method"] as? String ?: "GET").uppercase()
        val headersMap = (req["headers"] as? Map<*, *>)?.mapNotNull { (k, v) ->
            if (k != null && v != null) k.toString() to v.toString() else null
        }?.toMap() ?: emptyMap()
        val bytesMode = req["bytes"] == true
        val dataObj = req["data"]

        val builder = Request.Builder().url(url)

        var hasUa = false
        var manualCookieHeader: String? = null
        for ((k, v) in headersMap) {
            if (k.equals("user-agent", ignoreCase = true)) hasUa = true
            // 源脚本常照抄浏览器头 `Accept-Encoding: gzip, deflate, br, zstd`。
            // OkHttp 一旦发现调用方自定义了该头，就判定「解压由调用方负责」从而跳过
            // 透明解压；而官方的 Dart HttpClient 默认 autoUncompress=true，无论如何
            // 都会解压。为对齐官方行为，这里移除该头，交由 OkHttp 自动协商并解压。
            // 否则 jm / manhuaren / ikmmh / mycomic / ehentai 会直接拿到 gzip 二进制，
            // 最终在 JSON.parse 处报 "Unexpected token ..." 之类的莫名错误。
            if (k.equals("accept-encoding", ignoreCase = true)) continue
            // 手动 cookie 不直接进 header：OkHttp 的 BridgeInterceptor 会用 cookieJar
            // 的结果**无条件覆盖** Cookie header，直接设置等于把源脚本传的值丢掉。
            // 官方 CookieManagerSql.onRequest 的语义是「手动 cookie 与 cookieJar 合并」
            // （cookie_jar.dart: `cookies = "${options.headers["cookie"]}; $cookies"`），
            // EH 的 nw=1（绕内容警告页）正是依赖这一语义才能送达。
            if (k.equals("cookie", ignoreCase = true)) { manualCookieHeader = v; continue }
            builder.header(k, v)
        }
        if (!hasUa) {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            )
        }

        // 源脚本显式声明的 Content-Type 必须优先生效。
        // 注意：OkHttp 的 BridgeInterceptor 会用 `body.contentType()` **覆盖**同名
        // header，所以若这里不把 header 里的值喂给 RequestBody，源设置的
        // `application/x-www-form-urlencoded` 会被悄悄改成 application/json，
        // 服务端按 JSON 解析表单串就会拿不到任何字段
        //（jm 登录因此报「用戶名和密碼字段不能留空」）。
        val declaredContentType = headersMap.entries
            .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
            ?.value
        val isForm = declaredContentType?.contains("x-www-form-urlencoded", ignoreCase = true) == true

        val body: RequestBody? = when {
            method == "GET" || method == "HEAD" -> null
            dataObj is Map<*, *> && dataObj["__bytes_base64__"] is String -> {
                val b = Base64.decode(dataObj["__bytes_base64__"] as String, Base64.DEFAULT)
                b.toRequestBody((declaredContentType ?: "application/octet-stream").toMediaTypeOrNull())
            }
            dataObj is String -> {
                val ct = declaredContentType ?: "application/json; charset=utf-8"
                dataObj.toRequestBody(ct.toMediaTypeOrNull())
            }
            // 对齐 Dio：Map/List 形式的 data 会按 Content-Type 自动编码。
            // 以前这里一律发空 body，导致登录等表单提交全部失败。
            dataObj is Map<*, *> || dataObj is List<*> -> {
                val encoded = if (isForm) encodeForm(dataObj) else gson.toJson(dataObj)
                val ct = declaredContentType
                    ?: if (isForm) "application/x-www-form-urlencoded" else "application/json; charset=utf-8"
                encoded.toRequestBody(ct.toMediaTypeOrNull())
            }
            else -> {
                if (method in listOf("POST", "PUT", "PATCH")) {
                    ByteArray(0).toRequestBody()
                } else null
            }
        }
        builder.method(method, body)

        return try {
            val baseClient = VeneraNetworkClient.getInstance(context).okHttpClient
            val client = if (manualCookieHeader.isNullOrBlank()) baseClient else {
                val httpUrl = url.toHttpUrlOrNull()
                val manualCookies = if (httpUrl != null) parseManualCookies(httpUrl, manualCookieHeader!!) else emptyList()
                if (manualCookies.isEmpty()) baseClient
                else baseClient.newBuilder()
                    .cookieJar(MergingCookieJar(baseClient.cookieJar, manualCookies))
                    .build()
            }
            val response = client.newCall(builder.build()).execute()
            val resHeaders = mutableMapOf<String, String>()
            for (name in response.headers.names()) {
                resHeaders[name] = response.headers.values(name).joinToString(",")
            }
            // 兜底：若响应仍带 Content-Encoding（例如被别的拦截器改写过），手动解压，
            // 与官方 Dart autoUncompress=true 的语义保持一致。
            val encoding = response.header("Content-Encoding")
            val bytes = decodeBody(response.body?.bytes() ?: ByteArray(0), encoding)
            if (!encoding.isNullOrBlank() &&
                (encoding.contains("gzip", true) || encoding.contains("deflate", true))
            ) {
                // 已解压，头就不该再留着，否则源脚本会误判响应仍是压缩的
                resHeaders.remove("Content-Encoding")
                resHeaders.remove("content-encoding")
            }
            val resBody: Any = if (bytesMode) {
                mapOf("__bytes_base64__" to Base64.encodeToString(bytes, Base64.NO_WRAP))
            } else {
                // 必须吃掉开头的 UTF-8 BOM（EF BB BF）。
                //
                // 官方 Flutter 版用 Dart 的 `utf8.decode` 解响应体，而 Dart 的
                // Utf8Decoder 会**静默跳过**前导 BOM；Kotlin 的 `String(bytes, UTF_8)`
                // 则会把 BOM 原样译成 U+FEFF 字符。这个差异会让所有「嗅探首字符」
                // 的源脚本误判。
                //
                // 已实测：e-hentai 的 favorites.php 响应正是以 BOM 开头
                //（body[0] == '\uFEFF'），而 ehentai.js 里 `getGalleries` 写着
                // `if (res.body[0] !== '<') throw "Failed to load page"`，
                // 于是文件夹列表（不做首字符检查）能出来，一进具体文件夹就炸
                //「Failed to load page」。同源的 addOrDelFavorite 也有同样的
                // `res.body[0] !== "<"` 判断。
                String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
            }

            // 4xx/5xx 是「源不可用」类问题的唯一线索来源。源脚本只会把它压成
            // `Invalid status code: NNN`，所以这里必须把服务端的原始原因留下来，
            // 否则用户只能看到一个没有信息量的状态码（picacg 的 400 实际是限流）。
            if (response.code >= 400) {
                val snippet = if (bytesMode) "" else resBody.toString()
                SourceHttpDiagnostics.record(response.code, snippet)
                Log.w(TAG, "HTTP ${response.code} $method $url :: ${snippet.replace(Regex("\\s+"), " ").take(200)}")
            }

            mapOf(
                "status" to response.code,
                "headers" to resHeaders,
                "body" to resBody,
                "error" to null
            )
        } catch (e: Exception) {
            Log.w(TAG, "HTTP 请求失败 $method $url :: ${e.message ?: e.javaClass.simpleName}")
            mapOf(
                "status" to 0,
                "headers" to emptyMap<String, String>(),
                "body" to "",
                "error" to (e.message ?: e.javaClass.simpleName)
            )
        }
    }

    /**
     * 解析源脚本 headers 里手动声明的 cookie 串（`"nw=1"` / `"k=v; k2=v2"`）。
     * 域名绑定当前请求 host（请求级、不落库 —— 与官方「headers 里传 cookie」语义一致，
     * 绝不能 saveFromResponse 持久化）。
     */
    private fun parseManualCookies(url: HttpUrl, header: String): List<Cookie> {
        val cookies = mutableListOf<Cookie>()
        for (part in header.split(";")) {
            val idx = part.indexOf('=')
            if (idx <= 0) continue
            val name = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (name.isEmpty()) continue
            runCatching {
                Cookie.Builder().name(name).value(value).domain(url.host).build()
            }.getOrNull()?.let { cookies.add(it) }
        }
        return cookies
    }

    /**
     * 请求级 cookie 合并 —— 对齐官方 `CookieManagerSql.onRequest`：
     * `cookies = "${options.headers["cookie"]}; $cookies"`（手动在前，jar 在后）。
     * 同名时保留手动那份（HTTP 语义里服务端取首个，与官方拼接顺序等效）。
     */
    private class MergingCookieJar(
        private val delegate: CookieJar,
        private val manualCookies: List<Cookie>
    ) : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            delegate.saveFromResponse(url, cookies)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            if (manualCookies.isEmpty()) return delegate.loadForRequest(url)
            val jarCookies = delegate.loadForRequest(url)
            if (jarCookies.isEmpty()) return manualCookies
            val names = manualCookies.mapTo(HashSet()) { it.name }
            return manualCookies + jarCookies.filter { it.name !in names }
        }
    }

    /**
     * 把 Map/List 编码成 `k=v&k2=v2` 形式（对齐 Dio 对
     * application/x-www-form-urlencoded 的处理）。
     */
    private fun encodeForm(data: Any?): String {
        val pairs = mutableListOf<String>()
        when (data) {
            is Map<*, *> -> {
                for ((k, v) in data) {
                    if (k == null || v == null) continue
                    val key = URLEncoder.encode(k.toString(), "UTF-8")
                    val value = URLEncoder.encode(v.toString(), "UTF-8")
                    pairs.add("$key=$value")
                }
            }
            is List<*> -> {
                for (v in data) {
                    if (v == null) continue
                    pairs.add(URLEncoder.encode(v.toString(), "UTF-8"))
                }
            }
        }
        return pairs.joinToString("&")
    }

    /**
     * 按 Content-Encoding 解压响应体。
     *
     * 正常情况下 OkHttp 会自动解压 gzip（并移除 Content-Encoding 头），
     * 但源脚本一旦自己声明了 Accept-Encoding，OkHttp 就不再插手。
     * 这里补上与官方 Dart HttpClient(autoUncompress=true) 一致的行为。
     *
     * 覆盖 gzip / deflate；br 与 zstd 不做处理（与上游 Dart 的能力一致——
     * 上游同样无法解压这两种，源脚本声明它们只是照抄浏览器头）。
     */
    private fun decodeBody(bytes: ByteArray, contentEncoding: String?): ByteArray {
        if (bytes.isEmpty() || contentEncoding.isNullOrBlank()) return bytes
        return try {
            when {
                contentEncoding.contains("gzip", ignoreCase = true) ->
                    GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
                contentEncoding.contains("deflate", ignoreCase = true) ->
                    InflaterInputStream(bytes.inputStream()).use { it.readBytes() }
                else -> bytes
            }
        } catch (e: Exception) {
            Log.w(TAG, "解压失败 Content-Encoding=$contentEncoding :: ${e.message}")
            bytes
        }
    }
}
