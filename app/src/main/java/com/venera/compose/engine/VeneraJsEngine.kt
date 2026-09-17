package com.venera.compose.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.venera.compose.data.network.VeneraNetworkClient
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Venera JS 规则引擎（基于 WebView + @JavascriptInterface 桥）
 *
 * ## 关键架构约束（务必理解后再改动）
 *
 * Android WebView 的 `@JavascriptInterface` 方法**全部在同一个 JavaBridge 线程上串行执行**。
 * 也就是说：只要桥方法里发生阻塞（例如同步 OkHttp `.execute()`），整个 WebView 的所有 JS
 * 都会一起卡住。全网 33 源并发搜索时，任何一个不可达的源都会独占该线程直到 connect timeout，
 * 结果就是"搜不到 / 一直转圈"。
 *
 * 因此网络请求被拆成两条通道：
 * - **同步通道** `sendMessage(json)`：只处理内存级快速调用（setting / cookie / html / convert …）
 * - **异步通道** `sendMessageAsync(reqId, json)`：JS 立即拿到 Promise，实际 HTTP 在
 *   [httpExecutor] 线程池执行，完成后回调 `window.__veneraResolve(reqId)`，JS 再用
 *   `takeAsyncResult(reqId)` 极快地同步取回结果。大响应体因此无需转义进 JS 源码。
 */
class VeneraJsEngine(appContext: Context) : AutoCloseable {
    val context: Context = appContext.applicationContext
    val gson: Gson = Gson()

    /**
     * 桥接信封专用 Gson：**必须开启 serializeNulls**。
     *
     * 官方 `loadSetting` 的判空是 `res !== null && res !== undefined`：
     * 一旦让它取到"看起来有值、其实是空"的结果，就**不会回退到 `settings.default`**。
     *
     * 而 `Gson()` 默认 `serializeNulls = false`，会把 `mapOf("__result__" to null)`
     * 序列化成 `{}`。JS 侧解析后 `hasOwnProperty("__result__")` 为 false，于是返回
     * `_deserializeResult({})` → `{}`，绕过判空 → 例如 picacg 的
     * `loadSetting('base_url')` 得到 `{}`，拼出 `[object Object]/auth/sign-in`，登录必然失败。
     *
     * 实测（gson 2.11.0）：
     *   `Gson().toJson(mapOf("__result__" to null))`                     → `{}`
     *   `GsonBuilder().serializeNulls().create().toJson(same)`           → `{"__result__":null}`
     */
    val bridgeGson: Gson = GsonBuilder().serializeNulls().create()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val asyncCallbacks = ConcurrentHashMap<String, CancellableContinuation<String>>()

    /** 异步通道的待领取结果：reqId -> JSON 载荷 */
    private val pendingAsyncResults = ConcurrentHashMap<String, String>()
    private val asyncResultOrder = LinkedBlockingQueue<String>()

    @Volatile
    private var webView: WebView? = null

    /** WebView 是否已加载完成（about:blank 的 onPageFinished 已回调） */
    private val readyLatch = CountDownLatch(1)

    val initialized: Boolean get() = readyLatch.count == 0L

    /** HTTP / compute 专用线程池：有界并发，避免 33 源同时打满网络 */
    private val httpExecutor: ThreadPoolExecutor = ThreadPoolExecutor(
        HTTP_CORE_THREADS,
        HTTP_MAX_THREADS,
        60L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        { r -> Thread(r, "venera-js-http").apply { isDaemon = true } }
    ).apply { allowCoreThreadTimeOut(true) }

    /** 同步 evaluate 的专用线程：主线程调用时转交此处，避免阻塞 UI */
    private val evalExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "venera-js-eval").apply { isDaemon = true }
    }

    private val computeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val htmlHandler = JsHtmlHandler()
    val convertHandler = JsConvertHandler()
    val httpHandler = JsHttpHandler(context)
    val dataStore = JsSourceDataStore(context)

    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    /* ------------------------------------------------------------------ *
     * 初始化
     * ------------------------------------------------------------------ */

    fun init() {
        if (webView != null) return
        synchronized(this) {
            if (webView != null) return
            if (Looper.myLooper() == Looper.getMainLooper()) {
                createWebView()
            } else {
                val latch = CountDownLatch(1)
                mainHandler.post {
                    createWebView()
                    latch.countDown()
                }
                latch.await(10, TimeUnit.SECONDS)
            }
        }
    }

    /** 必须在主线程调用（WebView 只能在主线程构造） */
    private fun createWebView() {
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.settings.allowContentAccess = false
        wv.settings.allowFileAccess = false
        wv.settings.domStorageEnabled = true
        wv.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        wv.addJavascriptInterface(NativeBridge(), "_venera")
        wv.webChromeClient = object : android.webkit.WebChromeClient() {
            // JS 侧 console.log/warn/error 转发到 logcat（tag=VeneraJS）。
            // 此前 console 输出被整个丢弃，源脚本里的报错只能靠猜。
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                val m = consoleMessage ?: return true
                Log.i(TAG, "[JS:${m.messageLevel()}] ${m.message()} @${m.sourceId()}:${m.lineNumber()}")
                return true
            }
        }
        wv.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                readyLatch.countDown()
            }
        }
        wv.loadUrl("about:blank")
        webView = wv

        // 兜底：部分 ROM 的 about:blank 可能不回调 onPageFinished，超时后强制放行
        mainHandler.postDelayed({
            if (readyLatch.count > 0L) {
                Log.w(TAG, "WebView ready timeout, force release")
                readyLatch.countDown()
            }
        }, READY_TIMEOUT_MS)
    }

    private fun awaitReady(timeoutMs: Long = READY_TIMEOUT_MS): Boolean {
        init()
        return try {
            readyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    /* ------------------------------------------------------------------ *
     * 同步 evaluate
     * ------------------------------------------------------------------ */

    /**
     * 同步执行 JS 并返回**已解码**的结果。
     *
     * 注意：`evaluateJavascript` 的回调返回的是"结果的 JSON 表示"，即当 JS 返回字符串
     * `'{"a":1}'` 时回调拿到的是 `"\"{\\\"a\\\":1}\""`。历史上这里做了一次多余的
     * `JSON.stringify` + 又一次 JSON 编码，导致 `gson.fromJson` 恒抛异常并被 catch 吞掉，
     * 于是源的设置项与账号信息**永远为空**。此处统一做一次字符串字面量反解。
     */
    fun evaluate(script: String): String? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 主线程：转交专用线程完成往返，避免阻塞 UI（也避免桥回调依赖主线程时死锁）
            return try {
                evalExecutor.submit<String?> { evaluateBlocking(script) }.get(30, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.e(TAG, "evaluate on main thread failed", e)
                null
            }
        }
        return evaluateBlocking(script)
    }

    private fun evaluateBlocking(script: String): String? {
        if (!awaitReady()) {
            Log.w(TAG, "evaluate skipped: engine not ready")
            return null
        }
        val latch = CountDownLatch(1)
        val holder = AtomicReference<String?>(null)
        mainHandler.post {
            val wv = webView
            if (wv == null) {
                latch.countDown()
                return@post
            }
            wv.evaluateJavascript(script) { result ->
                holder.set(result)
                latch.countDown()
            }
        }
        if (!latch.await(EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "evaluate timeout (${EVAL_TIMEOUT_MS}ms)")
            return null
        }
        return decodeEvalResult(holder.get())
    }

    /** 把 evaluateJavascript 的 JSON 表示还原成"实际返回值" */
    private fun decodeEvalResult(raw: String?): String? {
        if (raw == null || raw == "null" || raw == "undefined") return raw
        if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return runCatching { gson.fromJson(raw, String::class.java) }.getOrNull() ?: raw
        }
        return raw
    }

    /* ------------------------------------------------------------------ *
     * 异步 evaluate（Promise 语义）
     * ------------------------------------------------------------------ */

    /**
     * 异步执行 JS。
     *
     * 返回的是**未经二次编码**的原始 JSON 信封：`{"success":true,"data":...}`，
     * 由 JS 侧 `_runAsync` → `_venera.postAsyncResult` 直接回传，不经过 evaluateJavascript。
     */
    suspend fun evaluateAsync(script: String): String = suspendCancellableCoroutine { cont ->
        if (!awaitReady()) {
            cont.resumeWith(Result.success("""{"success":false,"error":"engine not ready"}"""))
            return@suspendCancellableCoroutine
        }
        val callbackId = UUID.randomUUID().toString()
        asyncCallbacks[callbackId] = cont
        cont.invokeOnCancellation { asyncCallbacks.remove(callbackId) }

        mainHandler.post {
            val wv = webView
            if (wv == null) {
                asyncCallbacks.remove(callbackId)?.resumeWith(
                    Result.success("""{"success":false,"error":"webview destroyed"}""")
                )
                return@post
            }
            val wrappedScript = """
                (function() {
                    try {
                        var _val = (function() { $script })();
                        _runAsync('$callbackId', _val);
                    } catch (e) {
                        _venera.postAsyncResult('$callbackId', JSON.stringify({ success: false, error: String(e) }));
                    }
                })();
            """.trimIndent()
            wv.evaluateJavascript(wrappedScript, null)
        }
    }

    /** 初始化引擎并加载 venera-shim.js + venera-init.js */
    fun loadStandardLib(): Boolean {
        return try {
            val shim = context.assets.open("venera-shim.js").bufferedReader().use { it.readText() }
            val init = context.assets.open("venera-init.js").bufferedReader().use { it.readText() }
            evaluate(shim)
            evaluate(init)
            // init.js 里依赖平台字节表示的函数（如 Convert.hexEncode）需要在 init 之后
            // 由 shim 打补丁，否则字节参数会被当成空视图（详见 venera-shim.js 注释）
            try {
                val patched = evaluate("_veneraApplyPostInitPatches()")
                if (patched?.contains("true") != true) {
                    Log.w(TAG, "post-init patches not applied: $patched")
                }
            } catch (e: Exception) {
                Log.w(TAG, "failed to apply post-init patches", e)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load standard lib", e)
            false
        }
    }

    fun loadAsset(name: String): String? {
        val script = context.assets.open(name).bufferedReader().use { it.readText() }
        return evaluate(script)
    }

    /* ------------------------------------------------------------------ *
     * NativeBridge
     * ------------------------------------------------------------------ */

    inner class NativeBridge {
        /** 同步通道：仅用于内存级快速调用 */
        @JavascriptInterface
        fun sendMessage(jsonArgs: String): String {
            return processMessage(jsonArgs)
        }

        /**
         * 异步通道：立即返回，真正工作在 [httpExecutor] 上完成。
         * 完成后回调 `window.__veneraResolve(reqId)`，JS 再同步取回结果。
         */
        @JavascriptInterface
        fun sendMessageAsync(reqId: String, jsonArgs: String) {
            val map = runCatching { gson.fromJson<Map<String, Any?>>(jsonArgs, mapType) }.getOrNull()
            if (map == null) {
                publishAsyncResult(reqId, bridgeGson.toJson(mapOf("__error__" to "invalid async args")))
                return
            }
            val method = map["method"] as? String
            if (method == "compute") {
                computeScope.launch { runCompute(reqId, map) }
                return
            }
            httpExecutor.execute {
                val payload = try {
                    bridgeGson.toJson(mapOf("__result__" to httpHandler.handle(map)))
                } catch (e: Exception) {
                    Log.e(TAG, "async http failed", e)
                    bridgeGson.toJson(mapOf("__error__" to (e.message ?: e.javaClass.simpleName)))
                }
                publishAsyncResult(reqId, payload)
            }
        }

        /** 同步取回异步结果（内存级，不回退到网络） */
        @JavascriptInterface
        fun takeAsyncResult(reqId: String): String? {
            return pendingAsyncResults.remove(reqId)
        }

        @JavascriptInterface
        fun postAsyncResult(callbackId: String, jsonResult: String) {
            val cont = asyncCallbacks.remove(callbackId) ?: return
            cont.resumeWith(Result.success(jsonResult))
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, message)
        }

        @JavascriptInterface
        fun getVersion(): String = "1.0.0"
    }

    private fun publishAsyncResult(reqId: String, payload: String) {
        pendingAsyncResults[reqId] = payload
        asyncResultOrder.offer(reqId)
        // 有界化，避免 WebView 销毁时无人领取的结果无限堆积
        while (asyncResultOrder.size > MAX_PENDING_ASYNC) {
            val stale = asyncResultOrder.poll() ?: break
            pendingAsyncResults.remove(stale)
        }
        mainHandler.post {
            webView?.evaluateJavascript(
                "window.__veneraResolve && window.__veneraResolve('$reqId');",
                null
            )
        }
    }

    /**
     * `compute(func, ...args)`：官方语义是在独立 isolate 中执行函数。
     * 这里复用同一 WebView 的 JS 上下文异步执行，并把 args 还原为真实类型
     * （ArrayBuffer 等）后作为唯一实参传入。
     */
    private suspend fun runCompute(reqId: String, map: Map<String, Any?>) {
        val func = map["function"] as? String
        if (func.isNullOrBlank()) {
            publishAsyncResult(reqId, bridgeGson.toJson(mapOf("__error__" to "compute: empty function")))
            return
        }
        val argsJson = gson.toJson(map["args"] ?: emptyList<Any?>())
        val script = """
            return (async function() {
                var f = ($func);
                if (typeof f !== 'function') throw new Error('compute: not a function');
                var a = _deserializeResult($argsJson);
                return await f(a);
            })()
        """.trimIndent()
        val payload = try {
            val raw = evaluateAsync(script)
            val env = gson.fromJson<Map<String, Any?>>(raw, mapType)
            if (env?.get("success") == true) {
                bridgeGson.toJson(mapOf("__result__" to env["data"]))
            } else {
                bridgeGson.toJson(mapOf("__error__" to (env?.get("error")?.toString() ?: "compute failed")))
            }
        } catch (e: Exception) {
            bridgeGson.toJson(mapOf("__error__" to (e.message ?: "compute failed")))
        }
        publishAsyncResult(reqId, payload)
    }

    /** 处理所有**非网络**的同步桥方法 */
    private fun processMessage(jsonArgs: String): String {
        return try {
            val map = gson.fromJson<Map<String, Any?>>(jsonArgs, mapType)
                ?: return bridgeGson.toJson(mapOf("error" to "invalid args"))
            val method = map["method"] as? String ?: return bridgeGson.toJson(mapOf("error" to "no method"))

            when (method) {
                "log" -> {
                    val level = map["level"] as? String ?: "info"
                    val title = map["title"] as? String ?: "JS"
                    val content = map["content"]?.toString() ?: ""
                    when (level) {
                        "error" -> Log.e("$TAG:$title", content)
                        "warning" -> Log.w("$TAG:$title", content)
                        else -> Log.i("$TAG:$title", content)
                    }
                    bridgeGson.toJson(mapOf("__result__" to null))
                }
                "load_data" -> {
                    val key = map["key"] as? String ?: ""
                    val dataKey = map["data_key"] as? String ?: ""
                    bridgeGson.toJson(mapOf("__result__" to dataStore.loadData(key, dataKey)))
                }
                "save_data" -> {
                    val key = map["key"] as? String ?: ""
                    val dataKey = map["data_key"] as? String ?: ""
                    val err = dataStore.saveData(key, dataKey, map["data"])
                    if (err != null) bridgeGson.toJson(mapOf("error" to err))
                    else bridgeGson.toJson(mapOf("__result__" to null))
                }
                "delete_data" -> {
                    val key = map["key"] as? String ?: ""
                    val dataKey = map["data_key"] as? String ?: ""
                    dataStore.deleteData(key, dataKey)
                    bridgeGson.toJson(mapOf("__result__" to null))
                }
                "load_setting" -> {
                    val key = map["key"] as? String ?: ""
                    val settingKey = map["setting_key"] as? String ?: ""
                    bridgeGson.toJson(mapOf("__result__" to dataStore.loadSetting(key, settingKey)))
                }
                "save_setting" -> {
                    val key = map["key"] as? String ?: ""
                    val settingKey = map["setting_key"] as? String ?: ""
                    dataStore.saveSetting(key, settingKey, map["value"])
                    bridgeGson.toJson(mapOf("__result__" to null))
                }
                "isLogged" -> {
                    val key = map["key"] as? String ?: ""
                    bridgeGson.toJson(mapOf("__result__" to dataStore.isLogged(key)))
                }
                // http / compute 已由 sendMessageAsync 接管；此处兜底为旧同步调用（不推荐）
                "http" -> bridgeGson.toJson(mapOf("__result__" to httpHandler.handle(map)))
                "html" -> bridgeGson.toJson(mapOf("__result__" to htmlHandler.handle(map)))
                "convert" -> bridgeGson.toJson(mapOf("__result__" to convertHandler.handle(map)))
                "random" -> {
                    val min = (map["min"] as? Number)?.toDouble() ?: 0.0
                    val max = (map["max"] as? Number)?.toDouble() ?: 1.0
                    val type = map["type"] as? String ?: "int"
                    // 与官方 js_engine.dart 的 _random 完全一致：
                    //   double -> min + (max-min) * nextDouble()
                    //   int    -> 同一结果取整，因此范围是 [min, max)（不含 max）
                    // 用 (min..max).random() 会多出 max，源脚本拿它当数组下标会越界。
                    val raw = min + Math.random() * (max - min)
                    val res = if (type == "double") {
                        raw
                    } else {
                        kotlin.math.floor(raw).toInt()
                    }
                    bridgeGson.toJson(mapOf("__result__" to res))
                }
                "uuid" -> bridgeGson.toJson(mapOf("__result__" to UUID.randomUUID().toString()))
                "getLocale" -> gson.toJson(
                    mapOf("__result__" to "${Locale.getDefault().language}_${Locale.getDefault().country}")
                )
                "getPlatform" -> bridgeGson.toJson(mapOf("__result__" to "android"))
                "delay" -> {
                    val ms = (map["time"] as? Number)?.toLong() ?: 0L
                    if (ms > 0) Thread.sleep(ms)
                    bridgeGson.toJson(mapOf("__result__" to null))
                }
                "cookie" -> bridgeGson.toJson(mapOf("__result__" to handleCookie(map)))
                "setClipboard" -> {
                    handleSetClipboard(map["text"]?.toString())
                    bridgeGson.toJson(mapOf("__result__" to null))
                }
                "getClipboard" -> bridgeGson.toJson(mapOf("__result__" to handleGetClipboard()))
                "UI" -> bridgeGson.toJson(mapOf("__result__" to handleUI(map)))
                else -> bridgeGson.toJson(mapOf("error" to "unknown method: $method"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "processMessage error", e)
            bridgeGson.toJson(mapOf("error" to (e.message ?: e.javaClass.simpleName)))
        }
    }

    private fun handleCookie(data: Map<String, Any?>): Any? {
        val fn = data["function"] as? String ?: return null
        val urlStr = data["url"] as? String ?: return null
        // 官方部分源（包子漫画注销等）传入的是裸域名而非完整 URL，此处兜底补全协议
        val httpUrl = urlStr.toHttpUrlOrNull()
            ?: "https://$urlStr".toHttpUrlOrNull()
            ?: return null
        val cookieJar = VeneraNetworkClient.getInstance(context).cookieJar
        return when (fn) {
            "get" -> cookieJar.loadForRequest(httpUrl).map { c ->
                mapOf(
                    "name" to c.name,
                    "value" to c.value,
                    "domain" to c.domain,
                    "path" to c.path,
                    "expires" to c.expiresAt,
                    "secure" to c.secure,
                    "httpOnly" to c.httpOnly
                )
            }
            "set" -> {
                val list = data["cookies"] as? List<Map<String, Any?>> ?: return null
                val cookies = list.mapNotNull { m -> buildJsCookie(m, httpUrl.host) }
                cookieJar.saveFromResponse(httpUrl, cookies)
                null
            }
            // 官方 API：Network.deleteCookies(url) 清空该 URL 下的全部 Cookie（退出登录用）
            "delete" -> {
                cookieJar.clearForUrl(httpUrl)
                null
            }
            else -> null
        }
    }

    /**
     * 把 JS 侧 `Cookie` 对象转换为 OkHttp [okhttp3.Cookie]。
     *
     * ## 为什么不能直接照抄 JS 给的 domain（平台差异补齐）
     *
     * 官方 Dart 侧的实现是：
     * ```dart
     * var c = Cookie(e["name"], e["value"]);
     * if (e['domain'] != null) { c.domain = e['domain']; }   // 直接收
     * ```
     * 源脚本因此惯用**带前导点**的域名，典型是 ehentai 的
     * `Network.setCookies("https://exhentai.org", cookies)`，其中每条 cookie 都被写成
     * `domain = ".exhentai.org"`。Dart 的 `Cookie.domain` 接受前导点（RFC 6265 规定
     * 前导点语义等价于「该域及其子域」），而 **OkHttp 的 `Cookie.Builder.domain()`
     * 会直接抛 `IllegalArgumentException: unexpected domain: .exhentai.org`**。
     *
     * 因此这里必须：① 归一化域名（去前导点、小写），② 按「域 cookie」（hostOnly=false）
     * 构建 —— 两者合起来才与 Dart 语义等价（`x.org` ↔ `.x.org` 对 x.org 及子域均生效）。
     *
     * ## 为什么必须逐条容错
     *
     * Dart 侧单个非法 cookie 只会让那一行写不进去，**不会抛异常**；而 OkHttp 的
     * `domain()` 是在 `build()` 之前就抛，一旦不拦就会把**整批 setCookies 一起废掉**。
     * ehentai 的 `loginWithCookies.validate()` 恰好在 `deleteCookies('https://e-hentai.org')`
     * **之后**调用 setCookies，整批失败等于「先清空旧登录态、再什么都不写」，
     * 表现为「重新登录后收藏全没了」，且因为 `Network.setCookies` 是单向的
     * `sendMessage`（shim 吞掉错误只留 console.error），**全程静默无提示**。
     */
    private fun buildJsCookie(m: Map<String, Any?>, fallbackHost: String): okhttp3.Cookie? {
        val name = m["name"] as? String ?: return null
        val value = m["value"] as? String ?: return null
        if (name.isEmpty()) return null
        val domain = normalizeCookieDomain(m["domain"] as? String) ?: fallbackHost
        return runCatching {
            val builder = okhttp3.Cookie.Builder().name(name).value(value).domain(domain)
            (m["path"] as? String)?.takeIf { it.isNotEmpty() }?.let { builder.path(it) }
            if (m["secure"] == true) builder.secure()
            if (m["httpOnly"] == true) builder.httpOnly()
            builder.build()
        }.onFailure {
            Log.w(TAG, "Skip invalid cookie from JS: $name (domain=${m["domain"]})", it)
        }.getOrNull()
    }

    /**
     * 归一化 JS 传来的 cookie domain。
     *
     * - `.exhentai.org` → `exhentai.org`（去前导点，RFC 6265 语义等价）
     * - `..Foo.ORG` → `foo.org`
     * - 空串 / 含空白或 `/` `:` `;` `,` / 纯数字与点（裸 IP 或脏数据）→ null，
     *   由调用方回退到请求 URL 的 host
     */
    private fun normalizeCookieDomain(raw: String?): String? {
        var d = raw?.trim()?.lowercase() ?: return null
        while (d.startsWith(".")) d = d.substring(1)
        if (d.isEmpty()) return null
        if (d.any { it == ' ' || it == '/' || it == ':' || it == ';' || it == ',' || it == '@' }) return null
        if (d.all { it.isDigit() || it == '.' }) return null
        return d
    }

    private fun handleSetClipboard(text: String?) {
        if (text == null) return
        mainHandler.post {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Venera", text)
            runCatching { clipboard?.setPrimaryClip(clip) }
        }
    }

    /** 读取剪贴板：在后台线程执行，绝不依赖主线程（否则主线程调用 evaluate 时会死锁） */
    private fun handleGetClipboard(): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun handleUI(data: Map<String, Any?>): Any? {
        val fn = data["function"] as? String ?: return null
        return when (fn) {
            "showMessage" -> {
                val msg = data["message"]?.toString() ?: ""
                mainHandler.post {
                    runCatching {
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                null
            }
            "launchUrl" -> {
                val url = data["url"]?.toString() ?: return null
                runCatching {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url)
                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                }.onFailure { Log.e(TAG, "Failed to launch url: $url", it) }
                null
            }
            // 对话框类 API 暂未接入原生 UI：返回 null 让源走"用户取消"分支，
            // 避免 Promise 永久 pending 导致整条调用链挂死。
            "showLoading" -> 1
            "cancelLoading" -> null
            "showInputDialog" -> null
            "showSelectDialog" -> null
            "showDialog" -> null
            else -> null
        }
    }

    override fun close() {
        httpExecutor.shutdownNow()
        evalExecutor.shutdownNow()
        mainHandler.post { webView?.destroy() }
        webView = null
    }

    companion object {
        private const val TAG = "VeneraJS"
        private const val READY_TIMEOUT_MS = 5_000L
        private const val EVAL_TIMEOUT_MS = 30_000L
        private const val MAX_PENDING_ASYNC = 256
        private const val HTTP_CORE_THREADS = 8
        private const val HTTP_MAX_THREADS = 24
    }
}
