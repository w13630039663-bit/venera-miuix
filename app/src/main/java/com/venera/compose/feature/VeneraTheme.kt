package com.venera.compose.feature

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.venera.compose.data.prefs.AppearanceStyle
import com.venera.compose.data.prefs.ThemeMode
import com.venera.compose.data.prefs.VeneraPreferences
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import androidx.compose.material3.darkColorScheme as materialDarkColorScheme
import androidx.compose.material3.lightColorScheme as materialLightColorScheme

val LocalVeneraDarkTheme = staticCompositionLocalOf { false }
val LocalAppearanceStyle = staticCompositionLocalOf { AppearanceStyle.MIUIX }

/** One palette for both component families; MD3 uses wallpaper colors on Android 12+. */
@Composable
fun VeneraTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = VeneraPreferences.getInstance(context)
    val mode by prefs.themeMode.collectAsState()
    val appearance by prefs.appearanceStyle.collectAsState()
    val isDark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val nativeMiuix = if (isDark) darkColorScheme() else lightColorScheme()
    // Do not cache only by Context: configuration/wallpaper overlays can change in-place.
    val materialColors = when {
        appearance == AppearanceStyle.MIUIX -> nativeMiuix.toMaterialColors(isDark)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> materialDarkColorScheme()
        else -> materialLightColorScheme()
    }
    val miuixColors = if (appearance == AppearanceStyle.MIUIX) nativeMiuix
        else materialColors.toMiuixColors(nativeMiuix)

    CompositionLocalProvider(
        LocalVeneraDarkTheme provides isDark,
        LocalAppearanceStyle provides appearance,
        LocalContentColor provides miuixColors.onBackground,
        androidx.compose.material3.LocalContentColor provides materialColors.onBackground,
    ) {
        MiuixTheme(colors = miuixColors) {
            MaterialTheme(colorScheme = materialColors, content = content)
        }
    }
}
