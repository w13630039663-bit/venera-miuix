package com.venera.compose.ui.tokens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.venera.compose.data.prefs.AppearanceStyle
import com.venera.compose.data.prefs.SurfaceMaterial
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
    material: SurfaceMaterial = SurfaceMaterial.SOLID,
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
        // 注意：这里**没有** provide `LocalVeneraDarkTheme`（它在 feature/VeneraTheme.kt 里声明，
        // 而 ui/tokens 从不反向 import feature；补上要么引反向依赖，要么改 8 个读取点的 import，
        // 其中若干在 FROZEN 名单里）。后果如实记着：深色预览里那些读它的元素（氛围光背景、
        // 玻璃描边的明暗选择）仍按浅色算 —— 这是既有的缺口，不是这一轮引入的。
        LocalSurfaceMaterial provides material,
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
