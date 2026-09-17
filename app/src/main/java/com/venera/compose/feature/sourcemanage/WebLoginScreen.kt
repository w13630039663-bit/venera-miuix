package com.venera.compose.feature.sourcemanage

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.coroutines.resume

/**
 * 内嵌 WebView 登录页（对齐官方 `_LoginPage.loginWithWebview()`）。
 *
 * 官方流程：
 * 1. 用内嵌 WebView 打开 `account.loginWithWebview.url`
 * 2. 在**每次导航 / 标题变化**时调用 `checkStatus(url, title)` 判定是否登录成功
 * 3. 命中后同时抓取 **cookies**（→ 全局 CookieJar）与 **localStorage**
 *    （→ 源数据 `_localStorage`），再触发源的 `onLoginSuccess()`
 * 4. 把账号标记为已登录（官方用 `"ok"`）
 *
 * ⚠️ 与「用系统浏览器打开链接」的本质区别：只有内嵌 WebView 才拿得到登录后的
 * cookie 与 localStorage。外部浏览器既不判定成功、也不回抓凭证，
 * 对依赖网页登录的源（ccc / ehentai / manga_dex / mycomic / nhentai）等于登不进去。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(
    loginUrl: String,
    sourceName: String,
    onCheckLogin: suspend (url: String, title: String) -> Boolean,
    onComplete: suspend (
        cookieHeader: String?,
        localStorageJson: String?,
        url: String
    ) -> Result<Unit>,
    onFinish: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }

    var loaded by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var busy by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    /**
     * 读取 WebView 的 localStorage 快照。
     *
     * `evaluateJavascript` 回调给出的是**结果的 JSON 编码形式**（字符串会被再引号 +
     * 转义一层），所以必须用 Gson 反解一次才能拿到真正的 JSON 文本。
     */
    suspend fun readLocalStorage(wv: WebView): String? {
        val raw = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                try {
                    wv.evaluateJavascript(
                        "(function(){try{return JSON.stringify(window.localStorage);}" +
                            "catch(e){return null;}})()"
                    ) { value -> cont.resume(value) }
                } catch (e: Exception) {
                    cont.resume(null)
                }
            }
        }
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            gson.fromJson(raw, String::class.java)
        } catch (e: Exception) {
            raw.trim('"')
        }
    }

    /** 判定命中 → 抓取凭证并落库。整个过程只允许完成一次。 */
    fun tryComplete(wv: WebView, url: String, title: String) {
        if (finished || busy) return
        busy = true
        scope.launch {
            try {
                val hit = runCatching { onCheckLogin(url, title) }.getOrDefault(false)
                if (!hit) return@launch

                // 1) 先取 localStorage —— 必须在 onLoginSuccess 之前落库，
                //    因为源的回调正是靠 this.loadData("_localStorage") 取 token 的
                val localStorageJson = readLocalStorage(wv)
                // 2) 再取 cookie 请求头
                val cookieHeader = CookieManager.getInstance().getCookie(url)

                val res = onComplete(cookieHeader, localStorageJson, url)
                finished = true
                if (res.isSuccess) {
                    Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        "已识别登录，但源回调执行失败：${res.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                onFinish(true)
            } finally {
                busy = false
            }
        }
    }

    // Dialog owns back dismissal; do not register a competing Activity callback.

    Dialog(
        onDismissRequest = { onFinish(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ---------------- 顶部栏 ----------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onFinish(false) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "登录 $sourceName",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "在网页中完成登录后会自动识别并保存凭证",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                // 加载进度条（用两个 Box 手搓，避免依赖特定 Compose 版本的进度 API）
                if (!loaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color.DarkGray.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
                                .height(2.dp)
                                .background(MiuixTheme.colorScheme.primary)
                        )
                    }
                }

                // ---------------- WebView ----------------
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val wv = WebView(ctx)
                        wv.apply {
                            settings.javaScriptEnabled = true
                            // ★ localStorage 必需，否则网页登录后读不到 token
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true

                            // 三方 cookie 也要收：部分源的登录态是跨子域下发的
                            CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setAcceptThirdPartyCookies(wv, true)
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    super.onPageStarted(view, url, favicon)
                                    loaded = false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    loaded = true
                                    if (view != null && !url.isNullOrBlank()) {
                                        tryComplete(view, url, view.title.orEmpty())
                                    }
                                }

                                /**
                                 * 单页应用（SPA）切路由常靠 pushState，不触发 onPageFinished，
                                 * 但会走这里 —— 补上它才能让「登录后跳转」被及时识别。
                                 */
                                override fun doUpdateVisitedHistory(
                                    view: WebView?,
                                    url: String?,
                                    isReload: Boolean
                                ) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    if (view != null && !url.isNullOrBlank()) {
                                        tryComplete(view, url, view.title.orEmpty())
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false
                            }

                            /**
                             * ⚠️ compileSdk 37 起，平台 `WebViewClient` 已**移除** `onReceivedTitle`，
                             * 该回调现仅存在于 `WebChromeClient`。官方 App 的 onTitleChange 等价物
                             * 就是它 —— 标题变化是「登录成功」最常见的信号，必须挂上。
                             */
                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (view != null && !title.isNullOrBlank()) {
                                        val currentUrl = view.url
                                        if (!currentUrl.isNullOrBlank()) {
                                            tryComplete(view, currentUrl, title)
                                        }
                                    }
                                }
                            }

                            loadUrl(loginUrl)
                            webView = this
                        }
                    }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webView?.let { wv ->
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.destroy()
                }
            } catch (_: Exception) {
            }
            webView = null
        }
    }
}
