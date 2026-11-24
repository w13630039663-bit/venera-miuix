/**
 * 机械拆分自 MainActivity.kt（代码正文逐行原样搬运，未作改写）。
 */
package com.venera.compose.feature

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 详情页动作钮 —— **两族两种形**（读数与"为什么从四色缩成两色"见 `ui/tokens/Color.kt` 的状态型头注）：
 *  - **状态型**（递 [identityColor]，如收藏 / 追更 / 点赞）：没做 = 中性座 + 线性字形；
 *    做了 = 身份色字形 + 实心字形 + 粗标签。**换形与换色两条通道同时给**，不只靠颜色。
 *  - **动作型**（不递 [identityColor]，如评论 / 分享 / 复制链接）：恒中性，没有"激活"这一档。
 *
 * 圆座一律实底 `surfaceVariant`：旧的 `identityColor.copy(alpha = 0.12f)` 在深色下几乎与背景同色，
 * 于是"没点过的动作"读起来像"不能点的动作"。
 *
 * ## 过渡动画（本轮新增）
 *
 * 三档都是"同一颗钮换状态"，不是三颗钮互相替换，所以每一档都走 [AnimatedContent] / 动画 token：
 *  - **字形**：`scaleIn(fadeIn)` 进、`scaleOut(fadeOut)` 出 —— 线性铃铛 ↔ 实心铃铛是**换形**，
 *    不是淡入一颗新图标。
 *  - **标签**：只做交叉淡入淡出（"收藏 → 已收藏 → 本地+源"字宽不同，缩放会带布局抖动）。
 *  - **圆座底色与字形色**：`animateColorAsState` + `spring(StiffnessMediumLow)`，与分段器那族同档。
 *  - **按下**：整颗缩到 0.94 再弹回；长按快捷收藏的手感与点击一致。
 *
 * 六枚同排时由调用方给 `Modifier.weight(1f)` 等分槽位（见 [ComicDetailScreen] 的动作行）。
 *
 * @param modifier 布局修饰（六枚排布时传 `weight`）
 * @param onLongClick 长按回调（收藏按钮用它做「快捷收藏」，对齐官方 `quickFavorite`）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailActionButton(
    icon: ImageVector,
    label: String,
    identityColor: Color? = null,
    isActive: Boolean = false,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val tokens = VeneraTokens
    val activeColor = identityColor ?: tokens.color.textSecondary
    val active = isActive && identityColor != null

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val glyphColor by animateColorAsState(
        targetValue = if (active) activeColor else tokens.color.textSecondary,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "detailActionGlyph",
    )
    val seatColor by animateColorAsState(
        targetValue = if (active) activeColor.copy(alpha = 0.16f) else tokens.color.surfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "detailActionSeat",
    )
    // 按下 0.96 → 松开弹回；激活态本身不放大（三颗状态钮并排时放大=抢视线）
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "detailActionPress",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(pressScale)
            .clip(RoundedCornerShape(tokens.shape.medium))
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            // 六枚同排：横衬垫从 space5(10dp) 收到 space3(6dp)，单槽 52dp 才塞得下四个字
            .padding(horizontal = tokens.spacing.space3, vertical = tokens.spacing.space3),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(seatColor),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (scaleIn(
                        initialScale = 0.6f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    ) + fadeIn()) togetherWith
                        (scaleOut(targetScale = 0.7f) + fadeOut())
                },
                label = "detailActionGlyph",
            ) { glyph ->
                Icon(
                    imageVector = glyph,
                    contentDescription = label,
                    tint = glyphColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(tokens.spacing.space2))
        AnimatedContent(
            targetState = label,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "detailActionLabel",
        ) { text ->
            Text(
                text = text,
                fontSize = tokens.type.badge,
                color = glyphColor,
                fontWeight = if (active) tokens.type.weightBold else tokens.type.weightRegular,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
