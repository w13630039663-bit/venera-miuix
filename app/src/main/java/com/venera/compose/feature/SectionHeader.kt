/**
 * 分区标题（首页六分区共用）。
 *
 * 本轮 Token 化：字号 / 颜色 / 圆角 / 间距全部走 Token。
 * 「›」原为 20sp（明显过大，与 17sp 标题打架），已收敛到 type.chevron。
 * 该组件目前仅被 HomeScreen 使用，因此本次改动不波及其他页面。
 */
package com.venera.compose.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun MiuixSectionHeader(
    title: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.small))
            .clickable { onTap() }
            .padding(
                horizontal = tokens.spacing.space2,
                vertical = tokens.spacing.space3,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = tokens.type.itemTitle,
            fontWeight = tokens.type.weightSemibold,
            color = tokens.color.textPrimary,
        )
        Text(
            text = "›",
            fontSize = tokens.type.chevron,
            color = tokens.color.textTertiary,
        )
    }
}
