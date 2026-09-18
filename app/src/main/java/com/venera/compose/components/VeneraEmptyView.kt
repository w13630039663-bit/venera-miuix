package com.venera.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * 统一空状态视图（全项目通用 Empty State）。
 *
 * ── API 设计依据（不是凭空定的）──
 * 本项目已存在两种空状态形态，本组件的签名是对它们的收敛：
 *  1. \`NetworkFavoritesScreen.EmptyHint(title, sub)\` —— 两层文字（标题 + 说明）+ 大图标。
 *  2. \`NetworkFavoritesScreen.ErrorHint(message, onRetry)\` —— 说明 + **操作按钮**。
 *  3. \`HistoryScreen\` / \`FollowUpdatesScreen\` —— 仅一行 16sp SemiBold 标题。
 * 因此 [title] / [message] / [actionText] + [onAction] 是真实需求，而非过度设计：
 *  - [title] 可空：只传 [message] 时退化为单行说明（覆盖形态 1 的简化用法）。
 *  - [actionText] + [onAction] 同时非空才渲染按钮（覆盖形态 2）。
 *
 * ── 视觉约束 ──
 *  - 全部取值来自 Token，调用方不传字号/颜色/间距。
 *  - 默认紧凑内联（首页分区内使用）；需要整页居中时调用方自行用 Box 包裹。
 *  - 不得喧宾夺主：图标使用 textDisabled 色，标题 textPrimary，说明 textSecondary。
 *
 * @param title 主标题（可空）。为空时只显示 [message]，适合轻量分区空态。
 * @param message 说明文字。
 * @param icon 顶部图标，默认 Inbox。
 * @param actionText 操作按钮文案；与 [onAction] 同时提供才显示。
 * @param onAction 操作回调。
 * @param iconSize 图标尺寸，默认取 spacing.space11。
 */
@Composable
fun VeneraEmptyView(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    iconSize: Dp = VeneraTokens.spacing.space11,
) {
    val tokens = VeneraTokens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space9),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            // 弱化：空态图标不应抢夺正文注意力
            tint = tokens.color.textDisabled,
            modifier = Modifier.size(iconSize),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    fontSize = tokens.type.itemTitle,
                    fontWeight = tokens.type.weightSemibold,
                    color = tokens.color.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = message,
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (!actionText.isNullOrBlank() && onAction != null) {
            Surface(
                shape = RoundedCornerShape(tokens.shape.small),
                color = tokens.color.primaryContainer,
                onClick = onAction,
            ) {
                Text(
                    text = actionText,
                    fontSize = tokens.type.caption,
                    fontWeight = tokens.type.weightMedium,
                    color = tokens.color.onPrimaryContainer,
                    modifier = Modifier.padding(
                        horizontal = tokens.spacing.space8,
                        vertical = tokens.spacing.space5,
                    ),
                )
            }
        }
    }
}
