package com.venera.compose.components.venera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * 收藏页统一的筛选胶囊（源栏 + 文件夹栏共用一份实现）。
 *
 * 用户拍板口径：32dp 高、横向间距由外层 LazyRow 决定。
 *  - 未选中 = 透明底 + 1dp outline 描边 + textPrimary 文字；
 *  - 选中   = 实心 primary（主题色，动态色板驱动）+ onPrimary 白字，描边消失。
 *
 * 刻意与 [VeneraChip] 分开：Chip 是「内容标签」语义（卡片上的 tag、已选条件），
 * 这个是「视图筛选器」语义，两者高度与选中态强度都不同，合并只会互相牵制。
 *
 * @param logged 非空时左侧带登录态小圆点。圆点只用主题色（选中态转 onPrimary 以保证
 *   在实心底上仍可见），不使用绿/蓝等异色。
 */
@Composable
fun VeneraFilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    logged: Boolean? = null,
) {
    val tokens = VeneraTokens
    Surface(
        modifier = modifier
            .height(tokens.spacing.filterChipHeight)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) tokens.color.primary else Color.Transparent,
        border = if (selected) null else BorderStroke(tokens.spacing.hairline, tokens.color.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = tokens.spacing.space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (logged != null) {
                Box(
                    modifier = Modifier
                        .size(tokens.spacing.space3)
                        .clip(CircleShape)
                        .background(
                            when {
                                selected -> tokens.color.onPrimary
                                logged -> tokens.color.primary
                                else -> tokens.color.textDisabled
                            },
                        ),
                )
                Spacer(Modifier.width(tokens.spacing.space2))
            }
            Text(
                text = text,
                fontSize = tokens.type.caption,
                fontWeight = if (selected) tokens.type.weightSemibold else tokens.type.weightMedium,
                color = if (selected) tokens.color.onPrimary else tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
