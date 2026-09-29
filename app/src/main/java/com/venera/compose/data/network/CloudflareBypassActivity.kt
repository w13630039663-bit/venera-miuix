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
import com.venera.compose.components.venera.VeneraTextButton
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

    /**
     * 本窗口启动时 CookieManager 里**已经有**的那枚 `cf_clearance` 的值（没有则 null）。
     *
     * ⚠️ 这是"假通关"最后一个、也是最根本的一条防线：WebView 的 CookieManager 是
     * **持久**的，上一次会话留下的 `cf_clearance` 在页面刚开始加载那一刻就在 cookie 串里
     * （真机日志里每次 check 都带 `hasClearance=true`，且判定发生在启动后 0.002 秒 ——
     * Turnstile 要跑 JS、可能还要用户点一下，绝不可能这么快）。
     * 于是"有没有 cf_clearance"这个判据**恒为真**，等于没判。
     *
     * 真通关的标志是：cookie 串里出现了一枚**本次之前不存在**的 cf_clearance
     * （站方在挑战通过后新下发的）。所以用启动时的值当基线，
     * 只认"值变了 / 从无到有"这两种情况。
     */
    private var baselineClearance: String? = null

    /** 本窗口启动时刻，用于"太快就不可能是真通关"那条判据。 */
    private var startedAtMs: Long = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        targetHost = intent.getStringExtra(EXTRA_HOST) ?: ""

        // 基线必须在**加载之前**取：一加载，旧 cookie 就已经在串里了。
        startedAtMs = System.currentTimeMillis()
        baselineClearance = clearanceOf(CookieManager.getInstance().getCookie(targetUrl))
        Log.d(
            "CloudflareBypass",
            "start host=$targetHost baselineClearance=${baselineClearance?.take(12) ?: "无"}",
        )

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

                VeneraTextButton(
                    text = "取消",
                    onClick = {
                        if (!isResolved) {
                            CloudflareBypassManager.onBypassFailed(targetHost)
                        }
                        finish()
                    },
                )
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

        val clearance = clearanceOf(cookies)

        // ⚠️ **最关键的一条**：这枚 cf_clearance 必须是本次**新下发**的。
        // 见 [baselineClearance] 那段说明 —— CookieManager 是持久的，旧 cookie 会让
        // `contains("cf_clearance=")` 恒为真，于是过盾在启动 0.002 秒后就"成功"，
        // 带一枚从未激活的 cookie 去重试，表现为"验证完了、跳回去了、还是 403"。
        val isFresh = clearance != null && clearance != baselineClearance
        if (!isFresh) {
            Log.d(
                "CloudflareBypass",
                "check host=$targetHost 仍用旧 cf_clearance（ basline=${baselineClearance?.take(12) ?: "无"} ）" +
                    " elapsed=${System.currentTimeMillis() - startedAtMs}ms verdict=仍在挑战页",
            )
            return
        }

        // 太快也不可能是真通关：Turnstile 要跑 JS，通常还要用户点一下。
        // 这一条是兜底（真机实测最快的真通关也要数秒），防的是"cookie 恰好变了但挑战没过"。
        val elapsed = System.currentTimeMillis() - startedAtMs
        if (elapsed < MIN_PLAUSIBLE_MS) {
            Log.d(
                "CloudflareBypass",
                "check host=$targetHost 新 cookie 但只过了 ${elapsed}ms（<${MIN_PLAUSIBLE_MS}ms），" +
                    "视作仍在挑战页",
            )
            return
        }

        /*
         * 此刻已确认：cookie 是本次新下发的、且耗时合理 —— 这是最强的"挑战已通过"信号。
         *
         * ⚠️ 此处**不能再让 title 一票否决**（2026-09-27 第四层，真机截图定案）：
         * 过盾后站方返回的是**裸 JSON**（`{"header":{"status":-1,...}}`，没有 <title>），
         * 于是 lastPageTitle 停在空串 / URL 形 —— 而旧的 title 判据对这两种都回 true，
         * 结果挑战明明通了，窗口却永远停在那个 JSON 页上（用户看到的"停在这个页面"）。
         *
         * 所以 title 只用来拦**仍在挑战域**与**明确的挑战文案**两种硬信号；
         * "空 / URL 形 / 认不出"一律放行 —— 由新 cookie 这条主判据说算。
         * 两种错误的代价对齐过：误放行 = 回到"带无效 cookie 重试 403"（可再试）；
         * 误拦截 = 永远卡在验证窗口（用户只能取消）。后者更糟，且此处已有硬信号兜底。
         */
        val title = lastPageTitle
        if (isChallengeUrl(currentUrl) || isChallengeTitleText(title)) {
            Log.d(
                "CloudflareBypass",
                "check host=$targetHost url=\"${currentUrl.take(80)}\" 新 cookie 已下发，但 " +
                    "title=\"${title.take(60)}\" 仍是挑战页特征，继续等",
            )
            return
        }

        Log.d(
            "CloudflareBypass",
            "通关 host=$targetHost url=\"${currentUrl.take(80)}\" " +
                "elapsed=${elapsed}ms title=\"${title.take(60)}\"",
        )

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
     * 从 cookie 串里取出 `cf_clearance` 的值。
     *
     * 只要值、不要"有没有" —— 判据必须是"值变了"，因为 WebView 的 CookieManager 持久，
     * "有没有"这一问在窗口刚开那一刻就恒为真（见 [baselineClearance]）。
     */
    private fun clearanceOf(cookies: String?): String? {
        if (cookies.isNullOrBlank()) return null
        for (pair in cookies.split(";")) {
            val parts = pair.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0].trim() == "cf_clearance") {
                return parts[1].trim().takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    /**
     * 真通关的合理最短时间。Turnstile 要跑 JS、多数还要用户点一下 ——
     * 实测最快的真通关也要数秒。取 2s 已经很宽松，只为挡掉"0.002 秒通关"那类假象。
     */
    private val MIN_PLAUSIBLE_MS = 2_000L

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
    /**
     * title 是否仍是**明确的**挑战页特征。
     *
     * ⚠️ 调用时机已经变了（见 checkCookies 里那段说明）：走到这里的请求**必然**
     * 已有"新 cookie 已下发"这个硬信号，所以本函数**只拦明确特征**：
     * - 空白 / URL 形 title **不再**判"仍在挑战" —— 那正是"站方返回裸 JSON"的形态，
     *   判了就会把真通关永远卡在验证窗口里（2026-09-27 第四层，真机截图定案）。
     * - 只认挑战文案（多语言）与站方错误页标题。
     */
    private fun isChallengeTitleText(title: String): Boolean {
        if (title.isBlank()) return false
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
     * 「站方报错」的标题片段。命中即判未通关 —— 见 [isChallengeTitleText] 那段说明。
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
