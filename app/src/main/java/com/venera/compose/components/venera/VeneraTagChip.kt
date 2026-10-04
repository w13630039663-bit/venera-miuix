package com.venera.compose.components.venera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Venera 标签 Chip（语义层）。
 *
 * 只表达 **Tag 语义**：使用 [VeneraChipVariant.Tag] 的默认视觉。
 * **不包含 Source identity**（那是 [VeneraSourceBadge] 的职责）。
 *
 * 实现上**基于 [VeneraChip]**，不复制一套 UI —— 满足「不要创建多个重复 Chip 实现」。
 *
 * @param text 标签文本（默认单行 + Ellipsis，见 [textMaxLines]）。
 * @param selected 选中态（选中时提升为强调容器色）。
 * @param onClick 可空；为 null 时纯展示（如卡片上的只读标签）。
 * @param onLongClick 可空；长按菜单等场景用。传了它即使 [onClick] 为 null 也算可交互。
 * @param enabled false 时降透明度且不可点击。
 * @param textMaxLines 标签文字的行数上限。**卡片里的标签保持默认 1**（整卡高度由标签区驱动），
 *   标签本身就是内容的地方放到 2，理由见 [VeneraChip] 的同名参数。
 */
@Composable
fun VeneraTagChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    textMaxLines: Int = 1,
) {
    VeneraChip(
        text = text,
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        variant = VeneraChipVariant.Tag,
        textMaxLines = textMaxLines,
    )
}
