package com.venera.compose.data.network

import android.util.Log
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.venera.compose.feature.VeneraTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Cloudflare 人机验证交互界面。
 * 加载目标 URL，监听 CookieManager，一旦产生 cf_clearance 自动回写并关闭。
 */
class CloudflareBypassActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "extra_target_url"
        const val EXTRA_HOST = "extra_host"
    }

    private var targetUrl: String = ""
    private var targetHost: String = ""
    private var isResolved = false

    /// 挑战页自己的 title 恒为 "Just a moment..." 一类；通关后才会变成站点自己的标题。
    /// 刻意**不从 composable 里那个 pageTitle 取** —— 它的初值是"安全验证中..."，
    /// 拿它当"已离开挑战页"的判据会当场误判通关，正是要修的那个错。
    private var lastPageTitle = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        targetHost = intent.getStringExtra(EXTRA_HOST) ?: ""

        if (targetUrl.isBlank()) {
            CloudflareBypassManager.onBypassFailed(targetHost)
            finish()
            return
        }

        setContent {
            VeneraTheme {
                BypassScreen()
            }
        }
    }

    @Composable
    private fun BypassScreen() {
        var pageTitle by remember { mutableStateOf("安全验证中...") }
        var progress by remember { mutableFloatStateOf(0f) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            // 顶栏提示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cloudflare 安全验证",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "域名: $targetHost - $pageTitle",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }

                TextButton(onClick = {
                    if (!isResolved) {
                        CloudflareBypassManager.onBypassFailed(targetHost)
                    }
                    finish()
                }) {
                    Text("取消", color = MiuixTheme.colorScheme.primary)
                }
            }

            // 进度条
            if (progress in 0.01f..0.99f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = progress))
                )
            }

            // WebView 交互区
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = UserAgentPolicy.getUserAgentForHost(targetHost)
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                    checkCookies(view?.url ?: targetUrl, settings.userAgentString)
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) {
                                        pageTitle = title
                                        lastPageTitle = title
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    checkCookies(url ?: targetUrl, settings.userAgentString)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    checkCookies(url ?: targetUrl, settings.userAgentString)
                                }
                            }

                            loadUrl(targetUrl)
                        }
                    }
                )
            }
        }
    }

    private fun checkCookies(currentUrl: String, userAgent: String) {
        if (isResolved) return
        val cookies = CookieManager.getInstance().getCookie(currentUrl) ?: return

        // 检测关键通关标志 cf_clearance
        if (!cookies.contains("cf_clearance=")) return

        // ⚠️ 但**光有 cf_clearance 不算通关** —— 这是从前误判的根因：
        // 1) `cf_clearance` 在**端挑战页上就会下发**，它真正生效要等 JS 跑完、页面跳回目标之后；
        // 2) WebView 的 CookieManager 是**持久**的，上一次会话留下的旧 `cf_clearance`
        //    会在 `onPageStarted` 那一刻就出现在 cookie 串里。
        // 两条叠加的后果：窗口开进去 100ms 就宣布通关并 finish()，**挑战被腰斩**，
        // 重试带的是一枚从未被激活的 cookie，于是同一个 403 会永远重复
        // （2026-09-27 真机日志实测：`onBypassSuccess` 距启动只有 121ms，紧接着
        //  `Cloudflare challenge detected` 又出现一次）。
        // 判据换成"**页面已经离开挑战页**"，并且**优先看 URL 而不是 title** ——
        // title 那条路在本机是漏的：设备语言是中文时 Cloudflare 的挑战页 title 是
        // **"请稍候…"**（2026-09-27 真机日志实测），而下面这串关键词全是英文，
        // 于是它被判成"通关" → finish() 腰斩挑战 → 带一枚未激活的 cookie 重试 → 同一个 403。
        // 从启动到"通关"只有 0.6s，真过 Turnstile 不可能这么快。
        //
        // 两条判据，任一命中就算"仍在挑战页"：
        // 1) **URL 还在 challenges.cloudflare.com 域**（最强，且与语言无关 —— 日志里
        //    那个 `challenges.cloudflare.com/cdn-cgi/challenge-platform/.../turnstile/...`
        //    就是它）；
        // 2) title 命中已知挑战文案（**补齐多语言**，至少中/英；这是兜底，不能当主判据）。
        val title = lastPageTitle
        val stillChallenging = isChallengeUrl(currentUrl) || isChallengeTitle(title)

        // 这个组件从前**只报结论、不报判据**，于是"过盾成功了但重试还是 403"这种状态
        // 完全没有可查的东西 —— 只能靠两次时间戳之差猜它是不是假通关（2026-09-27 就是这么定的案）。
        // 每次检查都留下一行：当前 URL、看到了什么 title、判成哪一边。
        Log.d(
            "CloudflareBypass",
            "check host=$targetHost url=\"${currentUrl.take(80)}\" hasClearance=true " +
                "title=\"${title.take(60)}\" verdict=${if (stillChallenging) "仍在挑战页" else "通关"}",
        )
        if (stillChallenging) return

        isResolved = true
        Toast.makeText(this, "安全验证通过，继续加载", Toast.LENGTH_SHORT).show()
        CloudflareBypassManager.onBypassSuccess(
            context = applicationContext,
            host = targetHost,
            url = currentUrl,
            userAgent = userAgent
        )
        finish()
    }

    /**
     * URL 还在 Cloudflare 的挑战域上 —— **最强判据，且与语言无关**。
     *
     * ⚠️ 这是 2026-09-27 定案的那次修法里加的：光看 title 会漏，因为挑战页 title
     * 是**本机语言**（中文设备上是"请稍候…"），而枚举英文关键词必然漏掉它。
     * `challenges.cloudflare.com` 这个域在任何语言下都一样。
     */
    private fun isChallengeUrl(url: String): Boolean {
        val host = url.substringAfter("://", "").substringBefore("/").lowercase()
        return host == "challenges.cloudflare.com" || host.endsWith(".challenges.cloudflare.com")
    }

    /**
     * title 命中已知的挑战页文案 **或"站方自己的错误页"**。
     *
     * ⚠️ **只能是兜底，不能当主判据** —— Cloudflare 的文案跟着设备语言走，
     * 枚举永远枚举不全（2026-09-27 就是被中文"请稍候…"漏掉的）。
     * 这里把已实测到的几种都列上，但真正的防线是 [isChallengeUrl]。
     *
     * ⚠️ **错误页同样不算通关**（2026-09-27 实测）：SauceNAO 在 CF 放行后返回的是
     * title 为 `SauceNAO Error` 的错误页 —— 它确实"离开了挑战页"，但那不是我们要的结果。
     * 判据只认"离开挑战页"就会把它当通关，于是带一枚从未激活的 cookie 去重试，
     * 表现为"验证完了、跳回去了、还是 403"。
     * 所以这里把 `error` / `错误` / `失败` 一类都算作**未通关**。
     * 空白 title 也算"还在挑战"（页面还没出标题，无从判断，宁可等）。
     */
    private fun isChallengeTitle(title: String): Boolean {
        if (title.isBlank()) return true
        val t = title.lowercase()
        return CHALLENGE_TITLE_MARKERS.any { t.contains(it) } ||
            ERROR_TITLE_MARKERS.any { t.contains(it) }
    }

    /** 已实测到的挑战页标题片段（英文 + 简繁中文；日韩等未实测，靠 [isChallengeUrl] 兜住）。 */
    private val CHALLENGE_TITLE_MARKERS = listOf(
        "just a moment",
        "attention required",
        "checking your browser",
        "cloudflare",
        "请稍候",   // 简体（2026-09-27 真机实测，正是漏掉这一个导致的误判）
        "請稍候",   // 繁体
        "稍候",
        "稍等",
        "请稍等",
        "正在检查",
        "验证",
    )

    /**
     * 「站方报错」的标题片段。命中即判未通关 —— 见 [isChallengeTitle] 那段说明。
     *
     * ⚠️ **刻意不用裸 "error" 去匹配**：正常站点的标题里也可能含这个词
     * （比如一个叫 "Error Handling" 的页面），那会把真通关误判成失败。
     * 只列实测到的形态 + 中文那两个。
     */
    private val ERROR_TITLE_MARKERS = listOf(
        "saucenao error",   // 2026-09-27 真机实测，正是误判成"通关"的那一个
        "错误",
        "失败",
        "出错",
    )

    override fun onDestroy() {
        super.onDestroy()
        if (!isResolved) {
            CloudflareBypassManager.onBypassFailed(targetHost)
        }
    }
}
