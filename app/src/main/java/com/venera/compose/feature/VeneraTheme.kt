package com.venera.compose.feature

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.venera.compose.data.prefs.ThemeMode
import com.venera.compose.data.prefs.VeneraPreferences
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * Venera 主题壳（S0-6）。
 *
 * 原来 EntranceActivity/MainActivity 写死 MiuixTheme()，themeMode 偏好虽然能存 SharedPreferences
 * 但从未被消费过。现在这样才构成闭环：偏好 → 主题色 → UI。
 *
 * 注意 Miuix 自带 Monet 取色系统（ColorSchemeMode.MonetSystem/MonetLight/MonetDark），
 * 原版 Venera 也用了它。本包选择了「跟随系统或用户显式切换亮/暗」的简化路径；
 * Monet 取色（ColorSchemeMode.MonetSystem）作为 S1 后的可选项。
 */
@Composable
fun VeneraTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = VeneraPreferences.getInstance(context)
    val mode by prefs.themeMode.collectAsState()

    val isDark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colors = if (isDark) darkColorScheme() else lightColorScheme()

    MiuixTheme(colors = colors, content = content)
}
