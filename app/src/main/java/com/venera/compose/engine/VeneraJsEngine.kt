package com.venera.compose.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.venera.compose.data.network.VeneraNetworkClient
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class VeneraJsEngine(appContext: Context) : AutoCloseable {
    val context: Context = appContext.applicationContext
    val gson: Gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val responseQueue: BlockingQueue<String?> = LinkedBlockingQueue()
    private val asyncCallbacks = ConcurrentHashMap<String, CancellableContinuation<String>>()
    private var webView: WebView? = null
    var initialized: Boolean = false
        private set

    val htmlHandler = JsHtmlHandler()
    val convertHandler = JsConvertHandler()
    val httpHandler = JsHttpHandler(context)
    val dataStore = JsSourceDataStore(context)

    fun init() {
        if (initialized) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            val wv = WebView(context)
            wv.settings.javaScriptEnabled = true
            wv.settings.allowContentAccess = false
            wv.settings.allowFileAccess = false
            wv.settings.domStorageEnabled = true
            wv.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            wv.addJavascriptInterface(NativeBridge(), "_venera")
            wv.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    initialized = true
                }
            }
            wv.loadUrl("about:blank")
            webView = wv
            initialized = true
        } else {
            val latch = CountDownLatch(1)
            mainHandler.post {
                val wv = WebView(context)
                wv.settings.javaScriptEnabled = true
                wv.settings.allowContentAccess = false
                wv.settings.allowFileAccess = false
                wv.settings.domStorageEnabled = true
                wv.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                wv.addJavascriptInterface(NativeBridge(), "_venera")
                wv.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        initialized = true
                        latch.countDown()
                    }
                }
                wv.loadUrl("about:blank")
                webView = wv
            }
            latch.await(5, TimeUnit.SECONDS)
        }
    }

    fun evaluate(script: String): String? {
        check(initialized) { "VeneraJsEngine not initialized" }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            var directResult: String? = null
            webView?.evaluateJavascript(script) { result ->
                directResult = result
            }
            return directResult
        }
        val latch = CountDownLatch(1)
        mainHandler.post {
            webView?.evaluateJavascript(script) { result ->
                responseQueue.offer(result)
                latch.countDown()
            }
        }
        latch.await(15, TimeUnit.SECONDS)
        return responseQueue.poll()
    }

    suspend fun evaluateAsync(script: String): String = suspendCancellableCoroutine { cont ->
        check(initialized) { "VeneraJsEngine not initialized" }
        val callbackId = UUID.randomUUID().toString()
        asyncCallbacks[callbackId] = cont
        cont.invokeOnCancellation {
            asyncCallbacks.remove(callbackId)
        }
        mainHandler.post {
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
            webView?.evaluateJavascript(wrappedScript, null)
        }
    }

    /** 初始化引擎并加载 venera-shim.js + venera-init.js */
    fun loadStandardLib(): Boolean {
        return try {
            val shim = context.assets.open("venera-shim.js").bufferedReader().use { it.readText() }
            val init = context.assets.open("venera-init.js").bufferedReader().use { it.readText() }
            evaluate(shim)
            evaluate(init)
            true
        } catch (e: Exception) {
            android.util.Log.e("VeneraJS", "Failed to load standard lib", e)
            false
        }
    }

    fun loadAsset(name: String): String? {
        val stream: InputStream = context.assets.open(name)
        val script = stream.bufferedReader().use { it.readText() }
        return evaluate(script)
    }

    inner class NativeBridge {
        @JavascriptInterface
        fun sendMessage(jsonArgs: String): String {
            return processMessage(jsonArgs)
        }

        @JavascriptInterface
        fun postAsyncResult(callbackId: String, jsonResult: String) {
            val cont = asyncCallbacks.remove(callbackId) ?: return
            cont.resumeWith(Result.success(jsonResult))
        }

        @JavascriptInterface
        fun log(message: String) {
            android.util.Log.d("VeneraJS", message)
        }

        @JavascriptInterface
        fun getVersion(): String = "1.0.0"
    }

    private fun processMessage(jsonArgs: String): String {
        return try {
            val map = gson.fromJson<Map<String, Any?>>(jsonArgs, object : TypeToken<Map<String, Any?>>() {}.type)
                ?: return gson.toJson(mapOf("error" to "invalid args"))
            val method = map["method"] as? String ?: return gson.toJson(mapOf("error" to "no method"))

            when (method) {
                "log" -> {
                    val level = map["level"] as? String ?: "info"
                    val title = map["title"] as? String ?: "JS"
                    val content = map["content"]?.toString() ?: ""
                    when (level) {
                        "error" -> android.util.Log.e("VeneraJS:$title", content)
                        "warning" -> android.util.Log.w("VeneraJS:$title", content)
                        else -> android.util.Log.i("VeneraJS:$title", content)
                    }
                    gson.toJson(mapOf("__result__" to null))
                }
                "load_data" -> {
                    val key = map["key"] as? String ?: ""
                    val dataKey = map["data_key"] as? String ?: ""
                    val res = dataStore.loadData(key, dataKey)
                    gson.toJson(mapOf("__result__" to res))
                }
                "save_data" -> {
                    val key = map["key"] as? String ?: ""
                    val dataKey = map["data_key"] as? String ?: ""
                    val err = dataStore.saveData(key, dataKey, map["data"])
                    if (err != null) {
                        gson.toJson(mapOf("error" to err))
                    } else {
                        gson.toJson(mapOf("__result__" to null))
                    }
                }
                "delete_data" -> {
                    val key = map["key"] as? String ?: ""
                    val dataKey = map["data_key"] as? String ?: ""
                    dataStore.deleteData(key, dataKey)
                    gson.toJson(mapOf("__result__" to null))
                }
                "load_setting" -> {
                    val key = map["key"] as? String ?: ""
                    val settingKey = map["setting_key"] as? String ?: ""
                    val res = dataStore.loadSetting(key, settingKey)
                    gson.toJson(mapOf("__result__" to res))
                }
                "save_setting" -> {
                    val key = map["key"] as? String ?: ""
                    val settingKey = map["setting_key"] as? String ?: ""
                    val value = map["value"]
                    dataStore.saveSetting(key, settingKey, value)
                    gson.toJson(mapOf("__result__" to null))
                }
                "isLogged" -> {
                    val key = map["key"] as? String ?: ""
                    gson.toJson(mapOf("__result__" to dataStore.isLogged(key)))
                }
                "http" -> {
                    val res = httpHandler.handle(map)
                    gson.toJson(mapOf("__result__" to res))
                }
                "html" -> {
                    val res = htmlHandler.handle(map)
                    gson.toJson(mapOf("__result__" to res))
                }
                "convert" -> {
                    val res = convertHandler.handle(map)
                    gson.toJson(mapOf("__result__" to res))
                }
                "random" -> {
                    val min = (map["min"] as? Number)?.toDouble() ?: 0.0
                    val max = (map["max"] as? Number)?.toDouble() ?: 1.0
                    val type = map["type"] as? String ?: "int"
                    val res = if (type == "double") {
                        min + Math.random() * (max - min)
                    } else {
                        (min.toInt()..max.toInt()).random()
                    }
                    gson.toJson(mapOf("__result__" to res))
                }
                "uuid" -> gson.toJson(mapOf("__result__" to UUID.randomUUID().toString()))
                "getLocale" -> gson.toJson(mapOf("__result__" to "${Locale.getDefault().language}_${Locale.getDefault().country}"))
                "getPlatform" -> gson.toJson(mapOf("__result__" to "android"))
                "delay" -> {
                    val ms = (map["time"] as? Number)?.toLong() ?: 0L
                    if (ms > 0) Thread.sleep(ms)
                    gson.toJson(mapOf("__result__" to null))
                }
                "cookie" -> {
                    val res = handleCookie(map)
                    gson.toJson(mapOf("__result__" to res))
                }
                "setClipboard" -> {
                    handleSetClipboard(map["text"]?.toString())
                    gson.toJson(mapOf("__result__" to null))
                }
                "getClipboard" -> {
                    val text = handleGetClipboard()
                    gson.toJson(mapOf("__result__" to text))
                }
                "compute" -> {
                    gson.toJson(mapOf("error" to "compute not needed on single webview"))
                }
                "UI" -> {
                    val res = handleUI(map)
                    gson.toJson(mapOf("__result__" to res))
                }
                else -> gson.toJson(mapOf("error" to "unknown method: $method"))
            }
        } catch (e: Exception) {
            android.util.Log.e("VeneraJS", "processMessage error", e)
            gson.toJson(mapOf("error" to (e.message ?: e.javaClass.simpleName)))
        }
    }

    private fun handleCookie(data: Map<String, Any?>): Any? {
        val fn = data["function"] as? String ?: return null
        val urlStr = data["url"] as? String ?: return null
        val httpUrl = urlStr.toHttpUrlOrNull() ?: return null
        val cookieJar = VeneraNetworkClient.getInstance(context).cookieJar
        return when (fn) {
            "get" -> {
                val cookies = cookieJar.loadForRequest(httpUrl)
                cookies.map { c ->
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
            }
            "set" -> {
                val list = data["cookies"] as? List<Map<String, Any?>> ?: return null
                val cookies = list.mapNotNull { m ->
                    val name = m["name"] as? String ?: return@mapNotNull null
                    val value = m["value"] as? String ?: return@mapNotNull null
                    val domain = m["domain"] as? String ?: httpUrl.host
                    val b = okhttp3.Cookie.Builder().name(name).value(value).domain(domain)
                    if (m["path"] is String) b.path(m["path"] as String)
                    if (m["secure"] == true) b.secure()
                    if (m["httpOnly"] == true) b.httpOnly()
                    b.build()
                }
                cookieJar.saveFromResponse(httpUrl, cookies)
                null
            }
            else -> null
        }
    }

    private fun handleSetClipboard(text: String?) {
        if (text == null) return
        mainHandler.post {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Venera", text)
            clipboard?.setPrimaryClip(clip)
        }
    }

    private fun handleGetClipboard(): String {
        val latch = CountDownLatch(1)
        var result = ""
        mainHandler.post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                result = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            } finally {
                latch.countDown()
            }
        }
        latch.await(2, TimeUnit.SECONDS)
        return result
    }

    private fun handleUI(data: Map<String, Any?>): Any? {
        val fn = data["function"] as? String ?: return null
        return when (fn) {
            "showMessage" -> {
                val msg = data["message"]?.toString() ?: ""
                mainHandler.post {
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
                null
            }
            "launchUrl" -> {
                val url = data["url"]?.toString() ?: return null
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("VeneraJS", "Failed to launch url: $url", e)
                }
                null
            }
            "showLoading" -> 1
            "cancelLoading" -> null
            "showInputDialog" -> null
            "showSelectDialog" -> null
            else -> null
        }
    }

    override fun close() {
        mainHandler.post { webView?.destroy() }
    }
}