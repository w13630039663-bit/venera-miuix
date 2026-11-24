package com.venera.compose.components.venera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 选项行模式：单选（Radio，组内互斥）与多选（Checkbox，可并选）。
 * 两者共用同一副行骨架，只有**行首标记**不同 —— 这也是选中态不靠颜色的保证：
 * 标记本身就是选中与否的表达。
 */
enum class VeneraOptionMode { Radio, Checkbox }

/**
 * 模式 × 选中 → 行首标记图标的纯映射。
 * 抽出来只为可断言：四种组合各对应一枚图标，混在组合函数里就没有人能验证
 * "选中态不靠颜色表达"这条规则了。
 */
fun veneraOptionMarker(mode: VeneraOptionMode, selected: Boolean): ImageVector = when {
    mode == VeneraOptionMode.Radio && selected -> Icons.Filled.RadioButtonChecked
    mode == VeneraOptionMode.Radio -> Icons.Filled.RadioButtonUnchecked
    selected -> Icons.Filled.CheckBox
    else -> Icons.Filled.CheckBoxOutlineBlank
}

/**
 * 统一选项行（控件体系统一 · 阶段 2）。
 *
 * 用于**较多的单选项 / 可同时选择的选项**：设置弹窗里的选择列表、多选筛选项。
 * 少量、短文本的互斥模式不该用它 —— 那是 [VeneraSegmentedButton] 的语义。
 *
 * ── 行为契约 ──
 *  - **整行可点**：点击目标是整行（不只是行首标记），标记本身不挂 clickable，
 *    避免一行两个命中区互相抢事件。
 *  - 状态由调用方持有，本组件不自持、不记忆 —— 与 VeneraSwitch / VeneraCheckbox 同一条。
 *  - [onClick] 为 null 或 [enabled] 为 false 时整行不可点、文字走禁用色；
 *    传 null 与传 false 的分工与 [VeneraCheckbox] 一致：null 表"只展示、不可交互"。
 *
 * 尺寸全部取现成 Token：行高下限 [VeneraSpacingTokens.listRowMinHeight]（48dp 触达位）、
 * 水平内距 [VeneraSpacingTokens.rowHorizontal]、标记 [VeneraSpacingTokens.badgeSize]、
 * 图标字形 [VeneraSpacingTokens.chipIconSize]，不新造数字。
 *
 * @param title 选项标题（单行省略）。
 * @param summary 可选描述（单行省略，在标题下方）。
 * @param selected 选中态；显示在行首标记上，不靠颜色洗底。
 * @param onClick 整行点击；null = 只展示不可点。
 * @param mode 单选 / 多选，决定行首标记的形态。
 * @param enabled false 时整行降为禁用色且不可点。
 */
@Composable
fun VeneraOptionRow(
    title: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    mode: VeneraOptionMode = VeneraOptionMode.Radio,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val tokens = VeneraTokens
    val interactive = onClick != null && enabled
    val markerTint = when {
        !enabled -> tokens.color.textDisabled
        selected -> tokens.color.primary
        else -> tokens.color.textSecondary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = tokens.spacing.listRowMinHeight)
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = tokens.spacing.rowHorizontal,
                vertical = tokens.spacing.rowVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = veneraOptionMarker(mode, selected),
            contentDescription = if (selected) "已选择" else "未选择",
            tint = markerTint,
            modifier = Modifier.size(tokens.spacing.badgeSize),
        )
        Spacer(Modifier.width(tokens.spacing.space4))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = tokens.type.itemTitle,
                color = if (enabled) tokens.color.textPrimary else tokens.color.textDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
