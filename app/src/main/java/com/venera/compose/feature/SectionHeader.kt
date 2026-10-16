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
    /*
     * 两种行尾 = 两种排法，这一格从前是**一档通吃**（恒 `SpaceBetween`）：
     * 「›」那一支要的是箭头贴屏幕右缘（它表达"整行是一个入口"，右缘那枚就是入口的把手），
     * 而 `trailing` 那一支摆的是**真按钮**（画廊首页的「查看全部」「换一批」与「另有 N 位」读数），
     * 贴到屏幕右缘就把"标题 + 动作"这一组拉到屏幕两头。
     *
     * 现量（`_qa/ui-verify-2026-10-15/section_header_gap.txt`，探针 `_qa/section_header_gap_probe.js`，
     * 同行右邻与标题右缘的间隙）：
     *  - 平板 1920px：「正在关注的画师」→「另有 2 位」**1642px ≈ 621dp**、
     *    「每日热门」→ 动作那枚 **1676px ≈ 634dp**；
     *  - 手机 1080px：同一排 **577 / 639 / 670px ≈ 220 / 243 / 255dp**（三节各一条）。
     * 两档都成立 ⇒ 这一格不是"平板专属适配"，改的是共用件本身（手机一起修，见 rounds §七那条口径声明）。
     * 动作贴标题才读得出"这两个东西是一组的"。
     */
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.small))
            .then(if (clickable) Modifier.clickable { onTap?.invoke() } else Modifier)
            .padding(
                horizontal = tokens.spacing.space2,
                vertical = tokens.spacing.space3,
            ),
        horizontalArrangement = if (trailing == null) Arrangement.SpaceBetween else Arrangement.spacedBy(tokens.spacing.space4),
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
