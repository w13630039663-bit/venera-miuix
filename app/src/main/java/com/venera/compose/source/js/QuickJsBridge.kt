package com.venera.compose.source.js

import android.content.Context
import android.util.Base64
import app.cash.quickjs.QuickJs
import com.venera.compose.data.network.VeneraNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 暴露给 QuickJS 的网络接口
 */
interface JsNetworkInterface {
    fun get(url: String, headersJson: String): String
    fun post(url: String, body: String, headersJson: String): String
}

/**
 * 暴露给 QuickJS 的加密/转换接口
 */
interface JsConvertInterface {
    fun encodeBase64(str: String): String
    fun decodeBase64(str: String): String
    fun md5(str: String): String
    fun sha256(str: String): String
    fun hmacSha256(keyBase64: String, data: String): String
}

/**
 * 暴露给 QuickJS 的 HTML DOM 接口
 */
interface JsHtmlInterface {
    fun querySelector(html: String, query: String): String
    fun querySelectorAttr(html: String, query: String, attr: String): String
}

/**
 * QuickJS 原生沙箱桥接引擎
 * 用于执行扩展漫画源规则脚本 (.js)
 */
class QuickJsBridge(private val context: Context) {

    private val networkClient by lazy { VeneraNetworkClient.getInstance(context) }

    suspend fun executeScript(script: String): Any? = withContext(Dispatchers.Default) {
        val quickJs = QuickJs.create()
        try {
            // 注入原生桥接对象
            quickJs.set("__network", JsNetworkInterface::class.java, object : JsNetworkInterface {
                override fun get(url: String, headersJson: String): String = runBlocking {
                    val headersMap = parseHeaders(headersJson)
                    networkClient.get(url, headersMap)
                }

                override fun post(url: String, body: String, headersJson: String): String = runBlocking {
                    val headersMap = parseHeaders(headersJson)
                    networkClient.post(url, body, headersMap)
                }
            })

            quickJs.set("__convert", JsConvertInterface::class.java, object : JsConvertInterface {
                override fun encodeBase64(str: String): String =
                    Base64.encodeToString(str.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                override fun decodeBase64(str: String): String =
                    String(Base64.decode(str, Base64.DEFAULT), Charsets.UTF_8)

                override fun md5(str: String): String {
                    val md = MessageDigest.getInstance("MD5")
                    return md.digest(str.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                }

                override fun sha256(str: String): String {
                    val md = MessageDigest.getInstance("SHA-256")
                    return md.digest(str.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                }

                override fun hmacSha256(keyBase64: String, data: String): String {
                    val keyBytes = Base64.decode(keyBase64, Base64.DEFAULT)
                    val mac = Mac.getInstance("HmacSHA256")
                    mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
                    val sigBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
                    return sigBytes.joinToString("") { "%02x".format(it) }
                }
            })

            quickJs.set("__html", JsHtmlInterface::class.java, object : JsHtmlInterface {
                override fun querySelector(html: String, query: String): String {
                    val doc = Jsoup.parse(html)
                    return doc.selectFirst(query)?.text() ?: ""
                }

                override fun querySelectorAttr(html: String, query: String, attr: String): String {
                    val doc = Jsoup.parse(html)
                    return doc.selectFirst(query)?.attr(attr) ?: ""
                }
            })

            // 初始化全局兼容运行时环境
            val initEnv = """
                var Network = {
                    get: function(url, headers) {
                        var h = headers ? JSON.stringify(headers) : "{}";
                        var body = __network.get(url, h);
                        return { status: 200, body: body };
                    },
                    post: function(url, body, headers) {
                        var h = headers ? JSON.stringify(headers) : "{}";
                        var res = __network.post(url, body, h);
                        return { status: 200, body: res };
                    }
                };

                var Convert = {
                    encodeBase64: function(s) { return __convert.encodeBase64(s); },
                    decodeBase64: function(s) { return __convert.decodeBase64(s); },
                    md5: function(s) { return __convert.md5(s); },
                    sha256: function(s) { return __convert.sha256(s); },
                    hmacString: function(k, d, algo) { return __convert.hmacSha256(k, d); }
                };

                var Html = {
                    parse: function(html) {
                        return {
                            querySelector: function(q) { return __html.querySelector(html, q); },
                            attr: function(q, a) { return __html.querySelectorAttr(html, q, a); }
                        };
                    }
                };

                class ComicSource {
                    constructor() {
                        this.name = "";
                        this.key = "";
                        this.version = "1.0.0";
                    }
                }
            """.trimIndent()

            quickJs.evaluate(initEnv)
            quickJs.evaluate(script)
        } finally {
            quickJs.close()
        }
    }

    private fun parseHeaders(headersJson: String): Map<String, String> {
        if (headersJson.isEmpty() || headersJson == "{}") return emptyMap()
        val map = mutableMapOf<String, String>()
        val json = JSONObject(headersJson)
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.optString(key)
        }
        return map
    }
}
