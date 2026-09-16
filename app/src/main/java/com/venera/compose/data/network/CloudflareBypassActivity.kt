package com.venera.compose.data.network

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
            MiuixTheme {
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
        if (cookies.contains("cf_clearance=")) {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isResolved) {
            CloudflareBypassManager.onBypassFailed(targetHost)
        }
    }
}
