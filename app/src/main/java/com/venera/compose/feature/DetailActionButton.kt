/**
 * 机械拆分自 MainActivity.kt（代码正文逐行原样搬运，未作改写）。
 */
package com.venera.compose.feature

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import com.venera.compose.reader.*
import com.venera.compose.data.db.*
import com.venera.compose.data.prefs.*
import com.venera.compose.source.model.*
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 详情页动作钮 —— **两族两种形**（读数与"为什么从四色缩成两色"见 `ui/tokens/Color.kt` 的状态型头注）：
 *  - **状态型**（递 [identityColor]，如收藏 / 点赞）：没做 = 中性座 + 线性字形；
 *    做了 = 身份色字形 + 实心字形 + 粗标签。**换形与换色两条通道同时给**，不只靠颜色。
 *  - **动作型**（不递 [identityColor]，如评论 / 分享 / 复制链接）：恒中性，没有"激活"这一档。
 *
 * 圆座一律实底 `surfaceVariant`：旧的 `identityColor.copy(alpha = 0.12f)` 与 MD3 的
 * **禁用态**同值，真机实测五枚底色与页底只差 11~13 级亮度（彼此差 <2 级），
 * 于是"没点过的动作"读起来像"不能点的动作"。
 *
 * @param onLongClick 长按回调（收藏按钮用它做「快捷收藏」，对齐官方 `quickFavorite`）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailActionButton(
    icon: ImageVector,
    label: String,
    identityColor: Color? = null,
    isActive: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val tokens = VeneraTokens
    val activeColor = identityColor ?: tokens.color.textSecondary
    val active = isActive && identityColor != null
    val glyphColor = if (active) activeColor else tokens.color.textSecondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.shape.medium))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = tokens.spacing.space5, vertical = tokens.spacing.space3)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tokens.color.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = glyphColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(tokens.spacing.space2))
        Text(
            text = label,
            fontSize = tokens.type.badge,
            color = glyphColor,
            fontWeight = if (active) tokens.type.weightBold else FontWeight.Normal
        )
    }
}
