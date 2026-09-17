package com.venera.compose.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.data.prefs.AppearanceStyle
import com.venera.compose.data.prefs.NavigationBarStyle
import com.venera.compose.data.prefs.ThemeMode
import com.venera.compose.data.prefs.VeneraPreferences
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppearanceSettings(prefs: VeneraPreferences, onBack: () -> Unit) {
    val mode by prefs.themeMode.collectAsState()
    val appearance by prefs.appearanceStyle.collectAsState()
    val navigationBar by prefs.navigationBarStyle.collectAsState()
    SettingsPage("外观", onBack) {
        // 对照原版顶部手机模型，直接跟随真实 Miuix 色板。
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.size(196.dp, 296.dp).clip(RoundedCornerShape(28.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant).padding(10.dp)) {
                Column(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                    .background(MiuixTheme.colorScheme.surface).padding(12.dp)) {
                    Text("薇奈拉", fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreviewBlock(74, true); PreviewBlock(60, false)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreviewBlock(52, false); PreviewBlock(82, false)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(13.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text("●     ·     ·     ·", color = MiuixTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                }
            }
        }
        SettingsGroup("主题模式") {
            SettingsSelect("主题模式", mode.name, listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色"),
                { prefs.setThemeMode(ThemeMode.valueOf(it)) })
        }
        SettingsGroup("界面风格") {
            SettingsSelect("界面风格", appearance.name,
                listOf("MIUIX" to "Miuix", "MD3" to "MD3 · 壁纸动态取色"),
                { prefs.setAppearanceStyle(AppearanceStyle.valueOf(it)) })
            Text(
                text = if (appearance == AppearanceStyle.MIUIX) "使用 Miuix 浅色／深色色板。"
                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        "使用系统壁纸动态色板；现有 Miuix 控件同步取色，形状与交互仍保留原样。"
                    else "此 Android 版本不支持壁纸动态取色，使用 MD3 静态色板；现有 Miuix 控件同步取色。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        SettingsGroup("导航栏") {
            SettingsSelect("导航栏样式", navigationBar.name,
                listOf("MD3" to "Miuix 悬浮栏（默认）", "LIQUID_GLASS" to "Liquid Glass 液态玻璃"),
                { prefs.setNavigationBarStyle(NavigationBarStyle.valueOf(it)) })
            Text(
                "Liquid Glass 基于 Kyant0/AndroidLiquidGlass（Backdrop）官方实现：背景模糊 + 折射透镜 + 高光，" +
                    "拖拽指示器带色散与形变。需要 Android 13 及以上（RuntimeShader 折射）。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        SettingsGroup("返回动画") { UnsupportedSetting("返回动画样式", "路由返回已内置预返回跟手的滑动转场；尚未提供如原版多套动画样式的可选偏好") }
        SettingsGroup("沉浸式背景") {
            UnsupportedSetting("沉浸式背景", "关闭／氛围／壁纸模式尚未实现")
            UnsupportedSetting("自定义壁纸", "尚无壁纸持久化与全局背景渲染")
            UnsupportedSetting("模糊强度", "依赖尚未实现的壁纸背景")
        }
    }
}

@Composable
private fun PreviewBlock(height: Int, primary: Boolean) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(8.dp))
        .background(if (primary) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant))
}
