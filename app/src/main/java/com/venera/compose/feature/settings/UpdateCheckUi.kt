package com.venera.compose.feature.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.venera.compose.components.venera.VeneraDialog
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.data.update.AppUpdateChecker
import com.venera.compose.data.update.ProjectChannel
import com.venera.compose.data.update.UpdateCheck
import kotlinx.coroutines.delay

/** 冷启动后多久才允许弹更新框（master 的 `checkUpdateUi(delay = true)` 同口径：不与首屏抢帧）。 */
private const val STARTUP_CHECK_DELAY_MS = 2_000L

/**
 * 「发现新版本」确认框 —— 设置页手动检查与启动检查共用。
 *
 * 确认后**跳浏览器**下载，不在应用内自装：本包与 master 分支（Flutter 版）用同一个
 * applicationId、不同签名，应用内覆盖安装只会得到一句"应用未安装"。
 */
@Composable
internal fun UpdateAvailableDialog(remoteVersion: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    VeneraDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "发现新版本",
        content = { Text("${ProjectChannel.SLUG} 发布了 v$remoteVersion，现在去下载？") },
        confirmText = "更新",
        onConfirm = {
            onDismiss()
            val ok = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(ProjectChannel.RELEASES_URL)),
                )
            }.isSuccess
            if (!ok) Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
        },
        dismissText = "以后再说",
        onDismiss = onDismiss,
    )
}

/**
 * 启动时检查更新的宿主，挂在 `VeneraTheme` 之下、`VeneraComposeApp()` 之外 ——
 * 这样全应用只有一个弹窗位，也不必去改导航层。
 *
 * 静默路径只在"确实有新版本"时打扰，与 master 一致（手动检查才播报"已是最新"）。
 */
@Composable
internal fun StartupUpdateHost() {
    val context = LocalContext.current
    val prefs = remember { VeneraPreferences.getInstance(context) }
    var remoteVersion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!AppUpdateChecker.shouldCheckOnStartup(prefs)) return@LaunchedEffect
        // 先占坑再发请求：断网或被限流时，不该下次冷启动又自动重试一遍
        prefs.lastUpdateCheckAt = System.currentTimeMillis()
        delay(STARTUP_CHECK_DELAY_MS)
        remoteVersion = (AppUpdateChecker.check(context) as? UpdateCheck.Available)?.remoteVersion
    }

    remoteVersion?.let { UpdateAvailableDialog(it) { remoteVersion = null } }
}
