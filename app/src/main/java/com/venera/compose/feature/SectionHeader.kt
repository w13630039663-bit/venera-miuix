/**
 * 分区标题（首页六分区与画廊首页共用）。
 *
 * 本轮 Token 化：字号 / 颜色 / 圆角 / 间距全部走 Token。
 * 「›」原为 20sp（明显过大，与 17sp 标题打架），已收敛到 type.chevron。
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
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * 行尾那一段（画廊首页用它摆「换一批 / 查看全部」）。
     *
     * 给了 [trailing] 就**不画那枚「›」，整行也不再可点**：那一颗箭头表达的是"整行是一个入口"，
     * 而行里已经有按钮了 —— 两个动作打架，点标题与点按钮做得不是同一件事，
     * 用户却没法从外观上知道哪个能点、点了会去哪。
     */
    trailing: (@Composable () -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val clickable = onTap != null && trailing == null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.small))
            .then(if (clickable) Modifier.clickable { onTap?.invoke() } else Modifier)
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
        if (trailing != null) {
            trailing()
        } else {
            Text(
                text = "›",
                fontSize = tokens.type.chevron,
                color = tokens.color.textTertiary,
            )
        }
    }
}
