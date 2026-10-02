package com.venera.engineprobe

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.venera.compose.engine.JsConvertHandler
import com.venera.compose.engine.JsHtmlHandler
import com.venera.compose.engine.JsHttpHandler
import com.venera.compose.engine.JsSourceDataStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

/**
 * 桌面侧 JS 宿主（S0-3 探针）：用 GraalJS 顶替 Android 那边的 WebView 载体，
 * 桥协议与 [com.venera.compose.engine.VeneraJsEngine] 逐条对齐，handler 复用同一批源文件。
 *
 * 与 Android 侧的**唯一**结构性差异：WebView 的 `@JavascriptInterface` 全在单一
 * JavaBridge 线程上串行，所以那边必须把 http 拆成异步通道；GraalJS 的上下文本来就
 * 独占一个线程，这里 `sendMessageAsync` 直接内联执行完再回调 `window.__veneraResolve`，
 * JS 侧看到的语义（Promise resolve 后 takeAsyncResult 必命中）不变。
 */
class DesktopJsHost(
    assetDir: File,
    dataDir: File,
    proxy: String?,
) : AutoCloseable {

    private val gson = Gson()
    private val bridgeGson = GsonBuilder().serializeNulls().create()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    private val cookieJar = InMemoryCookieJar()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .apply {
            proxy?.split(":")?.takeIf { it.size == 2 }?.let { (h, p) ->
                proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(h, p.toInt())))
            }
        }
        .build()

    private val htmlHandler = JsHtmlHandler()
    private val convertHandler = JsConvertHandler()
    private val httpHandler = JsHttpHandler({ client })
    private val dataStore = JsSourceDataStore(File(dataDir, "comic_source"))

    private val context: Context = Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL)
        .allowExperimentalOptions(true)
        .build()

    private val pendingAsyncResults = ConcurrentHashMap<String, String>()
    private val pendingCallbacks = ConcurrentHashMap<String, (String) -> Unit>()
    private var resolveFn: Value? = null
    private var pumpTimers: Value? = null

    init {
        // shim 需要 window 与"原生"定时器；GraalJS 也没有 btoa/atob/console，一并补
        context.eval(
            "js", """
            var window = globalThis;
            window.__timers = [];
            window.setTimeout = function(fn, ms) {
                window.__timers.push({ fn: fn, due: Date.now() + (ms || 0) });
                return window.__timers.length;
            };
            window.clearTimeout = function () {};
            window.__pumpTimers = function (now) {
                var due = [], keep = [];
                window.__timers.forEach(function (t) { (t.due <= now ? due : keep).push(t); });
                window.__timers = keep;
                due.forEach(function (t) { t.fn(); });
                return due.length;
            };
            var console = {
                log: function () { _venera.log('log', Array.prototype.slice.call(arguments).join(' ')); },
                info: function () { _venera.log('log', Array.prototype.slice.call(arguments).join(' ')); },
                warn: function () { _venera.log('warn', Array.prototype.slice.call(arguments).join(' ')); },
                error: function () { _venera.log('error', Array.prototype.slice.call(arguments).join(' ')); },
            };
            globalThis.btoa = function (s) { return _venera.btoa(s); };
            globalThis.atob = function (s) { return _venera.atob(s); };
        """.trimIndent()
        )
        context.getBindings("js").putMember("_venera", Bridge())

        // 装载脚本是多语句源码，必须原样 eval —— evaluate() 的 `return (...)` 包装只适用于表达式
        // Task 6：这两颗从 EngineAssets 取（包内优先、仓库兜底，取不到就抛）——顺序与内容一字未动
        exec(EngineAssets.readText(assetDir, "venera-shim.js"))
        exec(EngineAssets.readText(assetDir, "venera-init.js"))
        resolveFn = context.eval("js", "window.__veneraResolve")
        pumpTimers = context.eval("js", "window.__pumpTimers")
        val patched = evaluate("_veneraApplyPostInitPatches()")
        if (patched?.contains("true") != true) {
            println("[probe] WARN post-init patches not applied: $patched")
        }
    }

    /** 原样执行（装载 shim/init/源脚本用），不做返回值包装 */
    fun exec(script: String) {
        context.eval("js", script)
    }

    /**
     * 求值并返回完成值的字符串形式。
     *
     * 不能像 Android 侧那样把脚本塞进 `return (...)` —— 源装载脚本以 `;` 结尾，
     * 塞进括号就是语法错误。GraalJS 的 eval 本身就返回 completion value。
     */
    /**
     * 探针专用：按 onImageLoad 给的 headers 取图字节。
     *
     * 源给的 headers 里带 `Accept-Encoding: gzip`，而 OkHttp 一旦看到调用方自己设了这个头
     * 就**关掉透明解压** —— Android 侧由 ImagePipelinePolicy.decodeBody 兜这条，这里同语义兜。
     */
    fun fetchBytes(url: String, headers: Map<String, String>): Pair<Int, ByteArray> {
        val req = okhttp3.Request.Builder().url(url).apply {
            headers.forEach { (k, v) ->
                if (!k.equals("Accept-Encoding", ignoreCase = true)) {
                    runCatching { header(k, v) }
                }
            }
        }.build()
        client.newCall(req).execute().use { r ->
            val raw = r.body?.bytes() ?: ByteArray(0)
            val encoding = r.header("Content-Encoding")
            val gzipped = raw.size > 2 && raw[0] == 0x1F.toByte() && raw[1] == 0x8B.toByte()
            val body = if (encoding?.contains("gzip", true) == true || (encoding == null && gzipped)) {
                java.util.zip.GZIPInputStream(raw.inputStream()).use { it.readBytes() }
            } else raw
            return r.code to body
        }
    }

    fun evaluate(script: String): String? = try {
        decode(context.eval("js", script))
    } catch (e: Exception) {
        println("[probe] eval error: ${e.message?.take(300)}")
        null
    }

    fun registerDefaultSettings(key: String, defs: Map<String, Any?>) {
        dataStore.registerDefaultSettings(key, defs)
    }

    /** 对齐 Android 侧的 evaluateAsync：返回 `{"success":..,"data":..}` 信封原文 */
    fun evaluateAsync(script: String, timeoutMs: Long = 90_000): String {
        val cbId = UUID.randomUUID().toString()
        val latch = CountDownLatch(1)
        val out = arrayOfNulls<String>(1)
        pendingCallbacks[cbId] = { res -> out[0] = res; latch.countDown() }
        context.eval(
            "js", """
            (function() {
                try {
                    var _val = (function() { $script })();
                    _runAsync('$cbId', _val);
                } catch (e) {
                    _venera.postAsyncResult('$cbId', JSON.stringify({ success: false, error: String(e) }));
                }
            })();
        """.trimIndent()
        )
        val deadline = System.currentTimeMillis() + timeoutMs
        while (latch.count > 0L && System.currentTimeMillis() < deadline) {
            runDueTimers()
            if (latch.count > 0L) {
                // GraalJS 在 JS 栈空时排干微任务队列：再进一次 eval 就是给它一个边界
                context.eval("js", "void 0")
                Thread.sleep(2L)
            }
        }
        pendingCallbacks.remove(cbId)
        return out[0] ?: """{"success":false,"error":"evaluateAsync timeout after ${timeoutMs}ms"}"""
    }

    private fun runDueTimers() {
        pumpTimers?.executeVoid(System.currentTimeMillis())
    }

    private fun decode(v: Value?): String? {
        if (v == null || v.isNull) return null
        return when {
            v.isString -> v.asString()
            else -> v.toString()
        }
    }

    inner class Bridge {
        fun sendMessage(jsonArgs: String): String = processMessage(jsonArgs)

        fun sendMessageAsync(reqId: String, jsonArgs: String) {
            val map = runCatching { gson.fromJson<Map<String, Any?>>(jsonArgs, mapType) }.getOrNull()
            if (map == null) {
                publishAsyncResult(reqId, bridgeGson.toJson(mapOf("__error__" to "invalid async args")))
                return
            }
            val payload = try {
                bridgeGson.toJson(mapOf("__result__" to httpHandler.handle(map)))
            } catch (e: Exception) {
                bridgeGson.toJson(mapOf("__error__" to (e.message ?: e.javaClass.simpleName)))
            }
            publishAsyncResult(reqId, payload)
        }

        fun takeAsyncResult(reqId: String): String? = pendingAsyncResults.remove(reqId)

        fun postAsyncResult(callbackId: String, jsonResult: String) {
            pendingCallbacks.remove(callbackId)?.invoke(jsonResult)
        }

        fun log(message: String) = println("[js] $message")

        fun getVersion(): String = "probe"

        fun btoa(s: String): String =
            EngineProbeBase64.encodeToString(ByteArray(s.length) { s[it].code.toByte() })

        // 与 btoa 对偶：每字节还原成一个 0..255 的字符，不能按 UTF-8 解
        fun atob(s: String): String =
            String(EngineProbeBase64.decode(s).map { (it.toInt() and 0xFF).toChar() }.toCharArray())
    }

    private fun publishAsyncResult(reqId: String, payload: String) {
        pendingAsyncResults[reqId] = payload
        // 内联执行完就有结果，直接通知 JS 侧（对齐 WebView 上"回调后 takeAsyncResult 必命中"）
        runCatching { resolveFn?.executeVoid(reqId) }
            .onFailure { println("[probe] WARN resolve failed: $it") }
    }

    /** 与 Android 侧 processMessage 同一张方法表 */
    private fun processMessage(jsonArgs: String): String = try {
        val map = gson.fromJson<Map<String, Any?>>(jsonArgs, mapType)
            ?: return bridgeGson.toJson(mapOf("error" to "invalid args"))
        val method = map["method"] as? String ?: return bridgeGson.toJson(mapOf("error" to "no method"))
        when (method) {
            "log" -> {
                println("[js:${map["level"] ?: "info"}:${map["title"]}] ${map["content"]}")
                bridgeGson.toJson(mapOf("__result__" to null))
            }
            "load_data" -> result(dataStore.loadData(str(map, "key"), str(map, "data_key")))
            "save_data" -> {
                val err = dataStore.saveData(str(map, "key"), str(map, "data_key"), map["data"])
                if (err != null) bridgeGson.toJson(mapOf("error" to err)) else result(null)
            }
            "delete_data" -> {
                dataStore.deleteData(str(map, "key"), str(map, "data_key")); result(null)
            }
            "load_setting" -> result(dataStore.loadSetting(str(map, "key"), str(map, "setting_key")))
            "save_setting" -> {
                dataStore.saveSetting(str(map, "key"), str(map, "setting_key"), map["value"]); result(null)
            }
            "isLogged" -> result(dataStore.isLogged(str(map, "key")))
            "http" -> result(httpHandler.handle(map))
            "html" -> result(htmlHandler.handle(map))
            "convert" -> result(convertHandler.handle(map))
            "random" -> {
                val min = (map["min"] as? Number)?.toDouble() ?: 0.0
                val max = (map["max"] as? Number)?.toDouble() ?: 1.0
                val raw = min + Math.random() * (max - min)
                result(if (map["type"] == "double") raw else kotlin.math.floor(raw).toInt())
            }
            "uuid" -> result(UUID.randomUUID().toString())
            "getLocale" -> result("${Locale.getDefault().language}_${Locale.getDefault().country}")
            // 桌面侧继续报 android：源脚本里有平台分支，报 windows 会走进没验过的路径
            "getPlatform" -> result("android")
            "delay" -> {
                val ms = (map["time"] as? Number)?.toLong() ?: 0L
                if (ms > 0) Thread.sleep(ms)
                result(null)
            }
            "cookie" -> result(handleCookie(map))
            "UI" -> result(handleUi(map))
            "setClipboard" -> result(null)
            "getClipboard" -> result("")
            else -> bridgeGson.toJson(mapOf("error" to "unknown method: $method"))
        }
    } catch (e: Exception) {
        println("[probe] processMessage error: $e")
        bridgeGson.toJson(mapOf("error" to (e.message ?: e.javaClass.simpleName)))
    }

    private fun result(v: Any?): String = bridgeGson.toJson(mapOf("__result__" to v))

    private fun str(map: Map<String, Any?>, key: String): String = map[key] as? String ?: ""

    private fun handleCookie(data: Map<String, Any?>): Any? {
        val fn = data["function"] as? String ?: return null
        val urlStr = data["url"] as? String ?: return null
        val httpUrl = urlStr.toHttpUrlOrNull() ?: "https://$urlStr".toHttpUrlOrNull() ?: return null
        return when (fn) {
            "get" -> cookieJar.loadForRequest(httpUrl).map {
                mapOf(
                    "name" to it.name, "value" to it.value, "domain" to it.domain,
                    "path" to it.path, "expires" to it.expiresAt, "secure" to it.secure,
                    "httpOnly" to it.httpOnly,
                )
            }
            "set" -> {
                val list = data["cookies"] as? List<Map<String, Any?>> ?: return null
                cookieJar.saveFromResponse(httpUrl, list.mapNotNull { buildCookie(it, httpUrl.host) })
                null
            }
            "delete" -> {
                cookieJar.clearForUrl(httpUrl); null
            }
            else -> null
        }
    }

    /** 域名归一化与逐条容错：与 Android 侧 buildJsCookie 同语义（前导点、脏域、整批不能废） */
    private fun buildCookie(m: Map<String, Any?>, fallbackHost: String): Cookie? {
        val name = m["name"] as? String ?: return null
        val value = m["value"] as? String ?: return null
        if (name.isEmpty()) return null
        val domain = (m["domain"] as? String)?.trim()?.lowercase()?.let { raw ->
            var d = raw
            while (d.startsWith(".")) d = d.substring(1)
            if (d.isEmpty() ||
                d.any { it == ' ' || it == '/' || it == ':' || it == ';' || it == ',' || it == '@' } ||
                d.all { it.isDigit() || it == '.' }
            ) null else d
        }
        return runCatching {
            val b = Cookie.Builder().name(name).value(value).domain(domain ?: fallbackHost)
            (m["path"] as? String)?.takeIf { it.isNotEmpty() }?.let { b.path(it) }
            if (m["secure"] == true) b.secure()
            if (m["httpOnly"] == true) b.httpOnly()
            b.build()
        }.onFailure { println("[probe] skip invalid cookie: $name domain=${m["domain"]}") }.getOrNull()
    }

    private fun handleUi(data: Map<String, Any?>): Any? {
        // 与 Android 侧同一处既存缺陷（方案文档 5.2）：对话框类返回 null 让源走取消分支。
        // 探针阶段照抄，是为了让两侧的返回形状可比；修它属于另一轮。
        return when (data["function"] as? String) {
            "showMessage" -> {
                println("[js UI] ${data["message"]}"); null
            }
            "launchUrl" -> {
                println("[js UI] launchUrl ${data["url"]}（探针不打开浏览器）"); null
            }
            "showLoading" -> 1
            else -> null
        }
    }

    override fun close() {
        runCatching { context.close(true) }
    }
}

private object EngineProbeBase64 {
    fun encodeToString(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
    fun decode(text: String): ByteArray = java.util.Base64.getMimeDecoder().decode(text)
}

class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store.getOrPut(url.host) { mutableListOf() }.let { existing ->
            cookies.forEach { c ->
                existing.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                existing.add(c)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host]?.filter { it.matches(url) } ?: emptyList()

    fun clearForUrl(url: HttpUrl) {
        store[url.host]?.removeAll { it.matches(url) }
    }
}
