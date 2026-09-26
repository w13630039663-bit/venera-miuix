package com.venera.compose.components.venera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * Chip 视觉变体。
 *
 * - [Assist]   : 未选中的动作型（描边 + 透明底）
 * - [Selected] : 选中态（强调容器色）
 * - [Tag]      : 标签语义（比 Assist 更轻，用于卡片上的 tag）
 * - [Filter]   : MD3 filter chip —— 未选中只有描边，选中填 **secondaryContainer**
 *   （不是 primaryContainer：选中一枚筛选条件不该有"主行动"那么响，规范里两者分档）
 * - [Neutral]  : MD3 tonal chip —— **无描边**、填 surfaceContainerHigh，用于"最近搜索"这类次要项
 */
enum class VeneraChipVariant { Assist, Selected, Tag, Filter, Neutral }

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
 * @param onLongClick 可空；长按（如弹出「复制 / 屏蔽」菜单）。传了它即使 [onClick] 为 null
 *   也算可交互 —— 按压反馈仍由本组件统一持有，页面不要自行叠 combinedClickable。
 * @param leadingIcon 前置图标（如「＋」）。
 * @param trailingIcon 后置图标（如「×」移除）。
 * @param onRemoveClick 给 [trailingIcon] 单独一个动作（胶囊自带的 ×）。
 *   **为什么要有它**：整枚胶囊的点击常被别的语义占着（标签胶囊点一下=改成排除），
 *   此时"删掉这枚"必须挂在 × 那一小块上，不能靠整枚 —— 否则要么两个动作打架，
 *   要么画了个按不动的 ×（假开关）。只在 [trailingIcon] 非空时生效。
 * @param leadingText 前置**次要文字**（如历史 chip 里那枚站点名）：比 [text] 小一档、吃
 *   [VeneraColorTokens.textTertiary]，让主文字（关键词）赢过它。摆这个而不摆图标是为了
 *   "同一枚 chip 里两段文字两种层级"这种需求 —— 别再为它复制一套 chip UI。
 * @param enabled false 时降透明度且不可点击。
 * @param variant 视觉变体；[VeneraChipVariant.Tag] 用于标签语义。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VeneraChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    leadingText: String? = null,
    onRemoveClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    variant: VeneraChipVariant = VeneraChipVariant.Assist,
) {
    val tokens = VeneraTokens

    // 交互状态源。onClick == null 时仍创建，但 clickable 不会被挂上，
    // 因此 pressed/focused 恒为 false，纯展示 Chip 不会"假响应"。
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()

    val interactive = (onClick != null || onLongClick != null) && enabled
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

    // ── 颜色决策：disabled > selected > Neutral > Tag > Assist ──
    val container = when {
        !enabled -> tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)
        // Filter 的选中态吃 secondaryContainer（MD3 给 filter chip 的就是这一档，比
        // primaryContainer 退一级）；其余变体的选中仍是主色容器。
        selected && variant == VeneraChipVariant.Filter -> tokens.color.secondaryContainer
        selected -> tokens.color.primaryContainer
        // Neutral：无描边的 tonal 面，靠底色本身与背景分层，不画轮廓。
        variant == VeneraChipVariant.Neutral -> tokens.color.surfaceContainerHigh
        // Tag：使用**专用**语义 Token，不再用两个 alpha 相加拼凑。
        // 这让 Tag 成为一个可辨识的「块」，同时仍是 surfaceVariant（非 primary），
        // 因而弱于 SourceBadge 的实底深色，满足 Title > SourceBadge > Tag > Metadata。
        variant == VeneraChipVariant.Tag ->
            tokens.color.surfaceVariant.copy(alpha = tokens.current.tagContainerAlpha)
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> tokens.color.textDisabled
        selected && variant == VeneraChipVariant.Filter -> tokens.color.onSecondaryContainer
        selected -> tokens.color.onPrimaryContainer
        // Tag 文字提到 textPrimary（原为 textSecondary 70%）——
        // 在 0.72f 的容器上，100% 文字才能保证 Light/Dark 均达到 WCAG AA。
        variant == VeneraChipVariant.Tag -> tokens.color.textPrimary
        else -> tokens.color.textPrimary
    }

    // ── 描边：Assist / Filter 未选中时用弱描边；focused 时转主色以提供焦点可见性 ──
    val border = when {
        !enabled -> null
        focused && interactive -> BorderStroke(tokens.spacing.space1, tokens.color.primary)
        // Tag：极弱 outlineVariant 描边，用于强化轮廓识别。
        // 单纯提高 alpha 后若 surfaceVariant 与卡片背景仍接近（尤其 Dark 模式），
        // 边框能保证 Tag 与背景分离；用 outlineVariant 而非 primary，
        // 避免与 selected Chip（实底 primaryContainer）混淆。
        variant == VeneraChipVariant.Tag && !selected ->
            BorderStroke(tokens.spacing.space1 / 2, tokens.color.outlineVariant)
        // Assist 与 Filter 未选中态同形：只有一层 outline 描边（MD3 filter chip 的默认档）。
        // Neutral 刻意不描边 —— 它靠 surfaceContainerHigh 的底色分层，再加轮廓就成了两层装饰。
        (variant == VeneraChipVariant.Assist || variant == VeneraChipVariant.Filter) && !selected ->
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
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null, // 反馈由上面的 graphicsLayer 统一表达，避免涟漪叠加
                        enabled = true,
                        onClick = onClick ?: {},
                        onLongClick = onLongClick,
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
            // 带次要前缀时收一档间隙：那两段文字是一体的（站点名 + 关键词），
            // 留出"两个独立元素"的间距会把一枚 chip 读成两枚。
            horizontalArrangement = Arrangement.spacedBy(
                if (leadingText == null) tokens.spacing.space2 else tokens.spacing.space1,
            ),
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
            // 次要前缀：小一档 + 退到 tertiary，让主文字赢过它（历史 chip 里的站点名就是这个用法）。
            // 它跟着 contentColor 走会一起变响，所以这里**不**吃 contentColor。
            if (leadingText != null) {
                Text(
                    text = leadingText,
                    fontSize = tokens.type.overline,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                // × 单独可点（删这枚）时，触达位往外扩一档：图标本身只有 chipIconSize，
                // 原尺寸 + 零内边距在真机上基本按不准（会连整枚的 onClick 一起误触发）。
                // 没给 onRemoveClick 就保持原样，不加任何按不动的假触达位。
                val removable = onRemoveClick != null
                Box(
                    modifier = if (removable) {
                        Modifier.padding(
                            start = tokens.spacing.space2,
                            end = if (onLongClick != null || onClick != null) tokens.spacing.space1 else 0.dp,
                        )
                    } else {
                        Modifier
                    },
                ) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = if (removable) "移除" else null,
                        tint = contentColor,
                        modifier = Modifier
                            .size(tokens.spacing.chipIconSize)
                            .then(
                                if (removable) {
                                    Modifier.combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onRemoveClick ?: {},
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                    )
                }
            }
        }
    }
}
