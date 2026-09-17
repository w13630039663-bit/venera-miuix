package com.venera.compose.feature.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 原版关于区已整合在主页。版本号来自实际安装包，不伪造发布状态。 */
@Composable
internal fun AboutSection() {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "未知"
    }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show() }
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(MiuixTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text("薇", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onPrimary)
        }
        Text("版本 $version", fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
        Text("免费、开源的漫画阅读应用", fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
        SettingsGroup {
            UnsupportedSetting("检查更新", "原生版尚未配置发布通道，不能用参考版版本号判断原生版更新")
            UnsupportedSetting("启动时检查更新", "尚无更新检查器及启动检查偏好")
            SettingsAction("参考项目代码仓库", "原版界面与功能参考") { open("https://github.com/w13630039663-bit/venera-miuix/tree/master") }
            SettingsAction("原始项目", "保留上游出处") { open("https://github.com/venera-app/venera") }
            SettingsAction("上游发布频道") { open("https://t.me/venera_release") }
        }
    }
}
