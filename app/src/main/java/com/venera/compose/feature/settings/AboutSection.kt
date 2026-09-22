package com.venera.compose.feature.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.R
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.data.update.AppUpdateChecker
import com.venera.compose.data.update.ProjectChannel
import com.venera.compose.data.update.UpdateCheck
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于区：大图标与版本号都取本包实际值（图标来自 master 分支的 `assets/app_icon.png`）。
 * 「检查更新」走本仓库的 releases，不再挂假开关。
 */
@Composable
internal fun AboutSection() {
    val context = LocalContext.current
    val prefs = remember { VeneraPreferences.getInstance(context) }
    val version = remember(context) { AppUpdateChecker.localVersion(context) ?: "未知" }
    val checkOnStart by prefs.checkUpdateOnStart.collectAsState()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var availableVersion by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show() }
    }

    fun checkUpdate() {
        if (checking) return
        checking = true
        scope.launch {
            when (val result = AppUpdateChecker.check(context)) {
                is UpdateCheck.Available -> availableVersion = result.remoteVersion
                // 拿不到远端版本 ≠ 没有更新，播报必须分开，否则一次断网就是一次假成功
                UpdateCheck.UpToDate -> notice = "已是最新版本"
                UpdateCheck.Failed -> notice = "检查失败：拿不到 ${ProjectChannel.SLUG} 的发布记录"
            }
            checking = false
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.app_icon),
            contentDescription = "venera-miuix",
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)),
            contentScale = ContentScale.Crop,
        )
        Text("版本 $version", fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
        Text("免费、开源的漫画阅读应用", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
        SettingsGroup {
            SettingsAction(
                title = if (checking) "正在检查更新…" else "检查更新",
                summary = "发布通道：${ProjectChannel.SLUG}",
                enabled = !checking,
            ) { checkUpdate() }
            SettingsToggle(
                title = "启动时检查更新",
                checked = checkOnStart,
                onCheckedChange = prefs::setCheckUpdateOnStart,
                summary = "距上次检查不满 24 小时不再请求",
            )
            SettingsAction("项目仓库与发布") { open(ProjectChannel.REPO_URL) }
            SettingsAction("原始项目", "保留上游出处") { open("https://github.com/venera-app/venera") }
            SettingsAction("上游发布频道") { open("https://t.me/venera_release") }
        }
    }

    availableVersion?.let { UpdateAvailableDialog(it) { availableVersion = null } }
    notice?.let {
        LaunchedEffect(it) {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            notice = null
        }
    }
}
