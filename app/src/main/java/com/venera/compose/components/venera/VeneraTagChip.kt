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
 * @param text 标签文本（单行 + Ellipsis）。
 * @param selected 选中态（选中时提升为强调容器色）。
 * @param onClick 可空；为 null 时纯展示（如卡片上的只读标签）。
 * @param enabled false 时降透明度且不可点击。
 */
@Composable
fun VeneraTagChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    VeneraChip(
        text = text,
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        variant = VeneraChipVariant.Tag,
    )
}
