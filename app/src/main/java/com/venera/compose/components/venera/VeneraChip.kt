package com.venera.compose.components.venera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * Chip 视觉变体。
 *
 * - [Assist]   : 未选中的动作型（描边 + 透明底）
 * - [Selected] : 选中态（强调容器色）
 * - [Tag]      : 标签语义（比 Assist 更轻，用于卡片上的 tag）
 */
enum class VeneraChipVariant { Assist, Selected, Tag }

/**
 * Venera 基础 Chip（基础层）。
 *
 * 这是**唯一**的 Chip 实现：VeneraTagChip / 页面上的各种条件 Chip 都必须基于它，
 * 禁止再复制一套 UI。
 *
 * ── 交互状态（本组件自行统一处理，页面不得自行实现 Chip press effect）──
 *  | 状态 | 视觉响应 |
 *  |---|---|
 *  | pressed  | 轻微缩放（0.96）+ 底色加深 |
 *  | focused  | 描边转为主色（键盘/无障碍焦点可见） |
 *  | selected | 强调容器色（primaryContainer） |
 *  | disabled | 降透明度 + 不可点击 + 不响应按压 |
 *
 * 反馈刻意保持**轻量**：只有 alpha 与 scale 两条动画，都走 spring，
 * 不使用涟漪/模糊/复杂位移，避免 Chip 密集场景下的额外开销。
 *
 * 主题适配：容器用 miuix Surface；MD3 模式下 VeneraTheme 已桥接色板，
 * 因此两套风格自动呈现各自正确的颜色，组件内无需分支。
 *
 * @param text 显示文本（单行 + Ellipsis）。
 * @param selected 选中态。
 * @param onClick 可空；为 null 时不可点击（纯展示），此时也不产生按压反馈。
 * @param leadingIcon 前置图标（如「＋」）。
 * @param trailingIcon 后置图标（如「×」移除）。
 * @param enabled false 时降透明度且不可点击。
 * @param variant 视觉变体；[VeneraChipVariant.Tag] 用于标签语义。
 */
@Composable
fun VeneraChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    variant: VeneraChipVariant = VeneraChipVariant.Assist,
) {
    val tokens = VeneraTokens

    // 交互状态源。onClick == null 时仍创建，但 clickable 不会被挂上，
    // 因此 pressed/focused 恒为 false，纯展示 Chip 不会"假响应"。
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()

    val interactive = onClick != null && enabled
    val isPressed = interactive && pressed

    // 轻量动画：只有 scale 与 alpha，均走 spring，无涟漪/模糊。
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) tokens.current.chipPressedScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "VeneraChipPressScale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) tokens.current.chipPressedAlpha else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "VeneraChipPressAlpha",
    )

    // ── 颜色决策：disabled > selected > Tag > Assist ──
    val container = when {
        !enabled -> tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)
        selected -> tokens.color.primaryContainer
        // Tag：使用**专用**语义 Token，不再用两个 alpha 相加拼凑。
        // 这让 Tag 成为一个可辨识的「块」，同时仍是 surfaceVariant（非 primary），
        // 因而弱于 SourceBadge 的实底深色，满足 Title > SourceBadge > Tag > Metadata。
        variant == VeneraChipVariant.Tag ->
            tokens.color.surfaceVariant.copy(alpha = tokens.current.tagContainerAlpha)
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> tokens.color.textDisabled
        selected -> tokens.color.onPrimaryContainer
        // Tag 文字提到 textPrimary（原为 textSecondary 70%）——
        // 在 0.72f 的容器上，100% 文字才能保证 Light/Dark 均达到 WCAG AA。
        variant == VeneraChipVariant.Tag -> tokens.color.textPrimary
        else -> tokens.color.textPrimary
    }

    // ── 描边：Assist 未选中时用弱描边；focused 时转主色以提供焦点可见性 ──
    val border = when {
        !enabled -> null
        focused && interactive -> BorderStroke(tokens.spacing.space1, tokens.color.primary)
        // Tag：极弱 outlineVariant 描边，用于强化轮廓识别。
        // 单纯提高 alpha 后若 surfaceVariant 与卡片背景仍接近（尤其 Dark 模式），
        // 边框能保证 Tag 与背景分离；用 outlineVariant 而非 primary，
        // 避免与 selected Chip（实底 primaryContainer）混淆。
        variant == VeneraChipVariant.Tag && !selected ->
            BorderStroke(tokens.spacing.space1 / 2, tokens.color.outlineVariant)
        variant == VeneraChipVariant.Assist && !selected ->
            BorderStroke(tokens.spacing.space1 / 2, tokens.color.outline)
        else -> null
    }

    val shape = RoundedCornerShape(tokens.shape.extraLarge)
    // 提前取值：graphicsLayer 的 lambda 不是 @Composable，不能在内部读 tokens.current
    val disabledAlpha = tokens.current.chipDisabledAlpha

    Surface(
        shape = shape,
        color = container,
        border = border,
        modifier = modifier
            // 按压/焦点反馈统一由 Chip 处理，页面不参与
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = if (enabled) pressAlpha else disabledAlpha
            }
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null, // 反馈由上面的 graphicsLayer 统一表达，避免涟漪叠加
                        enabled = true,
                        onClick = onClick!!,
                    )
                } else {
                    // 仍然提供 interactionSource，使无障碍/键盘焦点可被观测
                    Modifier
                }
            ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.chipHorizontalPadding,
                vertical = tokens.spacing.chipVerticalPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(tokens.spacing.chipIconSize),
                )
            }
            Text(
                text = text,
                fontSize = tokens.type.caption,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(tokens.spacing.chipIconSize),
                )
            }
        }
    }
}
