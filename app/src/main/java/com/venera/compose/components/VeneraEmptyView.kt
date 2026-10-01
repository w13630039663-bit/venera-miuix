package com.venera.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lightbulb
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
 * @param icon 显式指定图标。**一般不要传** —— 语义交给 [tone] 去定图标；传了则以它为准。
 * @param tone 这一屏**为什么**没有内容（见 [VeneraEmptyTone]）。图标由它定。
 * @param size 摆多大一档（见 [VeneraEmptySize]）。默认档 = 加这个参数之前那一档。
 * @param actionText 操作按钮文案；与 [onAction] 同时提供才显示。
 * @param onAction 操作回调。
 * @param iconSize 图标边长。null = 按 [size] 取（一般不用传）。
 */

/**
 * 空态的三种**语义** —— 不是三种样式。
 *
 * 「屏上说什么」一直由调用方定（各页的文案比这里细得多，比如画廊那一处的
 * 「这一站现在用不了」与「这一轮搜失败了」是刻意分成的三档标题），这个枚举只管**图标**
 * 这一件「没有语义就只能乱猜」的事。
 *
 * 加它之前，全项目二十多处调用各自挑图标，于是出现了这样的错配：
 * 「最新流加载失败」「这张图取不到」配 `Icons.Outlined.Image`（那是**内容**图标），
 * 「章节信息加载失败」「探索内容加载失败」落到默认的 `Inbox`（收件箱）——
 * 图标在说"这儿有东西"，文案在说"没取回来"。
 *
 * - [Nothing]：真的没有内容（这一轮没有热门 / 收藏夹是空的 / 该源没搜到）。
 * - [NotYet]：用户还没做那件事（还没收藏、还没读、还没选源）—— 语气是"去做什么"。
 * - [Failed]：我们这一轮没取回来（配一枚「重试」）。
 */
enum class VeneraEmptyTone { Nothing, NotYet, Failed }

/**
 * 空态摆多大一档。
 *
 * 加它之前只有一个尺寸（32dp 图标 + 20dp 竖留白），那是**整页**空态的档；
 * 而实际调用里有六处是「列表里的一行说明」（"该源未找到相关漫画"），
 * 摆一枚 32dp 图标会把那一行撑开。
 *
 * - [Regular]：整页空态（默认，与加这个参数之前逐字相同）。
 * - [Compact]：分区空态，图标矮一档、留白紧一档。
 * - [Inline]：列表里的一行说明 —— **不摆图标**，只有文字。
 */
enum class VeneraEmptySize { Regular, Compact, Inline }

@Composable
fun VeneraEmptyView(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    tone: VeneraEmptyTone = VeneraEmptyTone.Nothing,
    size: VeneraEmptySize = VeneraEmptySize.Regular,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    iconSize: Dp? = null,
) {
    val tokens = VeneraTokens
    // 图标：显式传的优先，否则按 [tone] 取 —— 这一层就是加这个枚举的全部理由。
    val glyph = icon ?: when (tone) {
        VeneraEmptyTone.Nothing -> Icons.Outlined.Inbox
        VeneraEmptyTone.NotYet -> Icons.Outlined.Lightbulb
        VeneraEmptyTone.Failed -> Icons.Outlined.ErrorOutline
    }
    val glyphSize = iconSize ?: when (size) {
        VeneraEmptySize.Regular -> tokens.spacing.space11
        VeneraEmptySize.Compact -> tokens.spacing.space9
        // Inline 档不摆图标 ⇒ 这个读数取不到（留着只为 when 穷尽，不参与绘制）。
        VeneraEmptySize.Inline -> tokens.spacing.space9
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = when (size) {
                    VeneraEmptySize.Regular -> tokens.spacing.space9
                    VeneraEmptySize.Compact -> tokens.spacing.space6
                    VeneraEmptySize.Inline -> tokens.spacing.space6
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        // Inline 档**不摆图标**：它是列表里的一行说明，摆一枚 32dp 图标会把那一行撑开，
        // 而那种场合（"该源未找到相关漫画"）本来也不需要图。
        if (size != VeneraEmptySize.Inline) {
            Icon(
                imageVector = glyph,
                contentDescription = null,
                // 弱化：空态图标不应抢夺正文注意力
                tint = tokens.color.textDisabled,
                modifier = Modifier.size(glyphSize),
            )
        }
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
