package com.venera.compose.feature

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.rememberDynamicColorScheme
import com.venera.compose.data.prefs.AppearanceStyle
import com.venera.compose.data.prefs.ThemeColorSource
import com.venera.compose.data.prefs.ThemeMode
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.ui.tokens.LocalVeneraColorTokens
import com.venera.compose.ui.tokens.LocalVeneraTokens
import com.venera.compose.ui.tokens.Md3Shapes
import com.venera.compose.ui.tokens.Md3Typography
import com.venera.compose.ui.tokens.MiuixShapes
import com.venera.compose.ui.tokens.MiuixTypography
import com.venera.compose.ui.tokens.VeneraElevationTokens
import com.venera.compose.ui.tokens.VeneraMotionTokens
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokenSet
import com.venera.compose.ui.tokens.buildVeneraColorTokens
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
    val colorSource by prefs.themeColorSource.collectAsState()
    val seedArgb by prefs.themeSeedColor.collectAsState()
    // Do not cache only by Context: configuration/wallpaper overlays can change in-place.
    val materialColors = when {
        appearance == AppearanceStyle.MIUIX -> nativeMiuix.toMaterialColors(isDark)
        // 自定义取色只在 MD3 风格下生效：Miuix 那套是它自己设计好的固定色板，
        // 拿种子色硬展开会破坏它。MaterialKolor 是纯计算，所以这条路不挑 Android 版本
        // （壁纸动态取色才需要 12+）。
        colorSource == ThemeColorSource.CUSTOM ->
            rememberDynamicColorScheme(Color(seedArgb), isDark)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> materialDarkColorScheme()
        else -> materialLightColorScheme()
    }
    val miuixColors = if (appearance == AppearanceStyle.MIUIX) nativeMiuix
        else materialColors.toMiuixColors(nativeMiuix)

    // 非颜色 Token 随风格切换：MIUIX 走大圆角 + 大字号，MD3 走 Material3 标准阶梯。
    val tokens = remember(appearance) {
        if (appearance == AppearanceStyle.MIUIX) {
            VeneraTokenSet(
                spacing = VeneraSpacing,
                shape = MiuixShapes,
                type = MiuixTypography,
                motion = VeneraMotionTokens(),
                elevation = VeneraElevationTokens(),
                appearance = AppearanceStyle.MIUIX,
            )
        } else {
            VeneraTokenSet(
                spacing = VeneraSpacing,
                shape = Md3Shapes,
                type = Md3Typography,
                motion = VeneraMotionTokens(),
                elevation = VeneraElevationTokens(),
                appearance = AppearanceStyle.MD3,
            )
        }
    }

    // 颜色 Token 在这里算一次，全页通过 LocalVeneraColorTokens 取同一份实例
    // （原先 VeneraTokens.color 每次读取都新建一份，见该处的注释）。
    val colorTokens = remember(materialColors, miuixColors, appearance) {
        buildVeneraColorTokens(
            m = materialColors,
            miuixSurface = miuixColors.surface,
            miuixOnSurface = miuixColors.onSurface,
            fixedActions = appearance != AppearanceStyle.MD3,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalVeneraDarkTheme provides isDark,
        LocalAppearanceStyle provides appearance,
        LocalVeneraTokens provides tokens,
        LocalVeneraColorTokens provides colorTokens,
        LocalContentColor provides miuixColors.onBackground,
        androidx.compose.material3.LocalContentColor provides materialColors.onBackground,
    ) {
        MiuixTheme(colors = miuixColors) {
            MaterialTheme(colorScheme = materialColors, content = content)
        }
    }
}
