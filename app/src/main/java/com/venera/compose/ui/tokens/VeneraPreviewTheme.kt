package com.venera.compose.ui.tokens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.venera.compose.data.prefs.AppearanceStyle
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDark
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLight

/**
 * 预览专用主题包裹。
 *
 * 为什么需要它：
 *  - 正式运行时 [com.venera.compose.feature.VeneraTheme] 依赖 SharedPreferences 与 Activity，
 *    在 @Preview 环境不可用。
 *  - 预览只需要**同一套 Token 推导逻辑**，因此这里用纯函数复刻 VeneraTheme 的 Token 选择规则，
 *    不读取任何持久化状态。
 *
 * 它不参与运行时，仅被 @Preview 使用；因此不会影响线上行为。
 */
@Composable
fun VeneraPreviewTheme(
    appearance: AppearanceStyle,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val m3 = if (dark) darkColorScheme() else lightColorScheme()
    val miuix = if (dark) miuixDark() else miuixLight()

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

    val colorTokens = remember(m3, miuix, appearance) {
        buildVeneraColorTokens(
            m = m3,
            miuixSurface = miuix.surface,
            miuixOnSurface = miuix.onSurface,
            fixedActions = appearance != AppearanceStyle.MD3,
        )
    }

    CompositionLocalProvider(
        LocalVeneraTokens provides tokens,
        LocalVeneraColorTokens provides colorTokens,
        LocalContentColor provides miuix.onBackground,
        androidx.compose.material3.LocalContentColor provides m3.onBackground,
    ) {
        MiuixTheme(colors = miuix) {
            MaterialTheme(colorScheme = m3, content = content)
        }
    }
}
