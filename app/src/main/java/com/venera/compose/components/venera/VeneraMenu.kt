package com.venera.compose.components.venera

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu as Md3DropdownMenu
import androidx.compose.material3.DropdownMenuItem as Md3DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 统一下拉 / 上下文菜单**容器**（控件体系统一 · 阶段 4）。
 *
 * 两套风格、Android 与大屏共用同一份实现：弹出模型沿用 M3 `DropdownMenu`
 * （锚点对齐、Dismiss、返回键、焦点行为全是库的原生行为，本组件一个都不碰），
 * 只把**视觉**收进 Token —— 圆角 [VeneraShapeTokens.medium]、容器底
 * [VeneraColorTokens.surfaceContainerHigh]。玻璃刻意不上：菜单是瞬时浮层，
 * 逐帧离屏采样换不来任何可读性（与 Chip 选中态不上玻璃同一条判据）。
 *
 * miuix 的 `ListPopup` 是**另一套锚点模型**（挂在 Scaffold 的 MiuixPopupHost 上），
 * 接进来等于改弹出位置与 Dismiss 行为 —— 那是回归不是统一，所以本仓菜单统一走这里。
 *
 * @param content 菜单项，用 [VeneraMenuItem]（或 M3 `DropdownMenuItem` 兼容槽位）。
 */
@Composable
fun VeneraDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = VeneraTokens
    Md3DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = RoundedCornerShape(tokens.shape.medium),
        containerColor = tokens.color.surfaceContainerHigh,
        content = content,
    )
}

/**
 * 统一菜单项。
 *
 *  - 文字走 [VeneraTypographyTokens.caption]（全仓菜单的既有档，收进组件不再各写一遍）；
 *  - **选中态不只靠颜色**：`selected = true` 时行尾挂一枚对勾，主色只是加强；
 *  - [destructive] 走错误色（屏蔽 / 清空这类动作），任何菜单不得另配色表达"危险"；
 *  - [enabled] = false 时整项降禁用色且不可点。
 *
 * @param selected 当前值即这一项（排序 / 档位选择菜单用）。行尾对勾与本组件持有的
 *   trailing 语义同一格：**选中赢**（选中标记是状态，自定义 trailing 是装饰）。
 */
@Composable
fun VeneraMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    selected: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = VeneraTokens
    val color = when {
        !enabled -> tokens.color.textDisabled
        destructive -> tokens.color.error
        selected -> tokens.color.primary
        else -> tokens.color.textPrimary
    }
    Md3DropdownMenuItem(
        text = {
            Text(
                text = text,
                fontSize = tokens.type.caption,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = color,
            leadingIconColor = color,
            trailingIconColor = color,
            disabledTextColor = tokens.color.textDisabled,
            disabledLeadingIconColor = tokens.color.textDisabled,
            disabledTrailingIconColor = tokens.color.textDisabled,
        ),
        leadingIcon = leadingIcon?.let { vector ->
            {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    modifier = Modifier.size(tokens.spacing.chipIconSize),
                )
            }
        },
        trailingIcon = when {
            selected -> {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "已选择",
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                }
            }
            trailingIcon != null -> {
                {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                }
            }
            else -> null
        },
    )
}
