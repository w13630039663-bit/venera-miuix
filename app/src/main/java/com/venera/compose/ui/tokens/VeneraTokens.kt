package com.venera.compose.ui.tokens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.data.prefs.AppearanceStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面访问 Token 的唯一入口。
 *
 * 用法（页面代码）：
 * ```
 * VeneraTokens.color.textSecondary
 * VeneraTokens.spacing.rowVertical
 * VeneraTokens.type.itemTitle
 * VeneraTokens.shape.card
 * ```
 *
 * 设计要点：
 *  - [color] 是**动态**的：它从当前生效的 Compose 主题（MiuixTheme 或 MaterialTheme）实时取值，
 *    所以同一份页面代码在 MD3 / MIUIX 下天然拿到各自正确的颜色，不需要页面写 if。
 *  - [spacing] / [shape] / [type] 由 [VeneraTheme] 通过 CompositionLocal 下发（随风格切换）。
 */
@Immutable
data class VeneraTokenSet(
    val spacing: VeneraSpacingTokens,
    val shape: VeneraShapeTokens,
    val type: VeneraTypographyTokens,
    val motion: VeneraMotionTokens,
    val elevation: VeneraElevationTokens,
    val appearance: AppearanceStyle,
    /** 图标徽章底色透明度。 */
    val badgeAlpha: Float = 0.15f,
    /** 选中/次级表面（胶囊未选中态、统计条轨道）的底色透明度。 */
    val selectedSurfaceAlpha: Float = 0.5f,
    /** 统计区分隔线透明度（要求「非常弱」）。 */
    val dividerAlpha: Float = 0.2f,
    /** 封面占位底色透明度。 */
    val placeholderAlpha: Float = 0.12f,
    /** 卡片表面叠加层透明度（用于在 surface 上再叠一层极淡的强调色）。 */
    val cardOverlayAlpha: Float = 0.04f,
    /** Skeleton / 占位块的基础不透明度。 */
    val skeletonAlpha: Float = 0.12f,
    /**
     * Tag（VeneraChip 的 Tag variant / VeneraTagChip）容器底色透明度。
     *
     * 为什么是独立 Token：此前用 cardOverlayAlpha + placeholderAlpha 相加凑出 0.16f，
     * 这是把两个「语义不同」的 alpha 拼成一个颜色 —— 结果容器几乎与背景融合，
     * Tag 看起来像一段无背景的浅色文字，辨识度不足（真机反馈）。
     *
     * 取值原则（对比度层级 Title > SourceBadge > Tag > Metadata）：
     *  - 0.72f 明显高于 placeholder(0.12) 与 overlay(0.04)，让 Tag 成为可辨识的「块」；
     *  - 用 surfaceVariant 而非 primary，避免与 SourceBadge 的实底深色同等强度。
     */
    val tagContainerAlpha: Float = 0.90f,

    // ── 交互反馈（由组件层统一消费，页面不得自行实现）──
    /** Chip 按下时的缩放比例（轻量反馈，不做涟漪）。 */
    val chipPressedScale: Float = 0.96f,
    /** Chip 按下时的整体不透明度。 */
    val chipPressedAlpha: Float = 0.88f,
    /** Chip 禁用时的整体不透明度。 */
    val chipDisabledAlpha: Float = 0.45f,
    /**
     * 封面打码（VeneraCoverMask.Masked）时叠加的暗色磨砂遮罩不透明度。
     *
     * 为什么是独立 Token：Modifier.blur 在部分老系统 / 关闭硬件加速的设备上会静默失效，
     * 打码视觉不能依赖 blur 是否生效 —— 遮罩是兜底的最后一道视觉屏障。
     * 0.65f 在任意封面上都能压暗到无法辨认细节，同时保留「这里有一张封面」的轮廓感。
     */
    val maskScrimAlpha: Float = 0.65f,
)

/**
 * Elevation Token。
 *
 * 两套体系对「高度」的表达不同：
 *  - MD3   -> tonal elevation（表面着色）+ 轻微阴影
 *  - MIUIX -> 更依赖描边/表面分层，阴影更克制
 * 因此这里给出**语义档位**，由各组件按当前风格取用，页面不直接写 elevation 数值。
 */
@Immutable
data class VeneraElevationTokens(
    /** 平面（无高度）：列表内的裸内容。 */
    val flat: Dp = 0.dp,
    /** 卡片默认。 */
    val card: Dp = 1.dp,
    /** 悬浮元素（FAB / 浮层）。 */
    val floating: Dp = 6.dp,
)

/** 由 VeneraTheme 提供；默认值保证预览/单测无需主题也能取到合法 Token。 */
val LocalVeneraTokens = staticCompositionLocalOf {
    VeneraTokenSet(
        spacing = VeneraSpacing,
        shape = MiuixShapes,
        type = MiuixTypography,
        motion = VeneraMotionTokens(),
        elevation = VeneraElevationTokens(),
        appearance = AppearanceStyle.MIUIX,
    )
}

object VeneraTokens {

    /** 非颜色 Token（间距/形状/字号/动效），随主题切换，来自 CompositionLocal。 */
    val current: VeneraTokenSet
        @Composable @ReadOnlyComposable get() = LocalVeneraTokens.current

    val spacing: VeneraSpacingTokens
        @Composable @ReadOnlyComposable get() = LocalVeneraTokens.current.spacing

    val shape: VeneraShapeTokens
        @Composable @ReadOnlyComposable get() = LocalVeneraTokens.current.shape

    val type: VeneraTypographyTokens
        @Composable @ReadOnlyComposable get() = LocalVeneraTokens.current.type

    val motion: VeneraMotionTokens
        @Composable @ReadOnlyComposable get() = LocalVeneraTokens.current.motion

    val elevation: VeneraElevationTokens
        @Composable @ReadOnlyComposable get() = LocalVeneraTokens.current.elevation

    val appearance: AppearanceStyle
        @Composable @ReadOnlyComposable get() = LocalVeneraTokens.current.appearance

    /**
     * 颜色 Token。
     *
     * 刻意**同时**读取 MiuixTheme 与 MaterialTheme：
     * VeneraTheme 已保证两者指向同一套语义（MIUIX 模式下 material 由 miuix 色板派生，
     * MD3 模式下 miuix 由 material 色板派生），因此这里取哪边都一致，
     * 取 MaterialTheme 是因为它的槽位命名更完整、且被动态取色驱动。
     */
    val color: VeneraColorTokens
        @Composable @ReadOnlyComposable
        get() {
            val m = MaterialTheme.colorScheme
            val miuixSurface = MiuixTheme.colorScheme.surface
            val miuixOnSurface = MiuixTheme.colorScheme.onSurface
            // 动作色只在 MD3 风格下取色。MIUIX 色板实际只有一个主蓝：
            // toMaterialColors 把 tertiary 映成 onTertiaryContainer、secondary 也贴着主色，
            // 跟着取色会让四个钮退化成同色系，反而不如固定语义色可辨。
            val fixedActions = LocalVeneraTokens.current.appearance != AppearanceStyle.MD3
            return VeneraColorTokens(
                background = m.background,
                onBackground = m.onBackground,
                surface = m.surface,
                onSurface = m.onSurface,
                surfaceVariant = m.surfaceVariant,
                onSurfaceVariant = m.onSurfaceVariant,

                primary = m.primary,
                onPrimary = m.onPrimary,
                primaryContainer = m.primaryContainer,
                onPrimaryContainer = m.onPrimaryContainer,

                outline = m.outline,
                outlineVariant = m.outlineVariant,
                divider = m.outlineVariant,

                // 文字层级：统一以 onSurface 为基准，用透明度表达层级，
                // 避免 MD3 与 MIUIX 对「次要文字」的定义差异泄漏到页面。
                textPrimary = m.onSurface,
                textSecondary = m.onSurface.copy(alpha = 0.70f),
                textTertiary = m.onSurface.copy(alpha = 0.45f),
                textDisabled = m.onSurface.copy(alpha = 0.30f),

                pressedOverlay = m.onSurface.copy(alpha = 0.06f),
                badgeTint = if (miuixSurface == Color.Unspecified) m.surface else miuixOnSurface,

                actionFavorite = if (fixedActions) StatusColors.Favorite else m.primary,
                actionLike = if (fixedActions) StatusColors.Like else m.error,
                actionComment = if (fixedActions) StatusColors.Comment else m.tertiary,
                actionShare = if (fixedActions) StatusColors.Share else m.secondary,
            )
        }
}
