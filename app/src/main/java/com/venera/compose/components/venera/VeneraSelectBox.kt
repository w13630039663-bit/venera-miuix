package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 收藏页**唯一**的多选框（基础层组件，不含任何业务语义）。
 *
 * ## 为什么要有这一枚
 *
 * 抽它之前，同一个"选中了没有"在两处长成两样：本地收藏是右上角的
 * `CheckCircle / RadioButtonUnchecked`，图片收藏是右下角的 `VeneraIconButton + Circle`。
 * 两处的未选态一个是**白色实心图标**（压在浅色封面上直接消失）、
 * 一个是主题次要文字色。同一件事在同一个页面里两种画法，用户学不会。
 *
 * ## 画法：固定深色底 + 白环 / 主色实心
 *
 * 它压在**封面图**上，而封面颜色完全不可控（可能纯白、可能纯黑、可能是高饱和插画）——
 * 与 [com.venera.compose.ui.tokens.StatusColors.BadgeSurface] 那条口径逐字相同
 * （源码已经在给 SourceBadge 用同一个理由）：只有固定底板才能保证任何图上都分得清
 * "这个圈在哪儿"。所以未选态 = 深色半透明底 + 白描边，而不是主题描边色 ——
 * 主题描边在浅色封面上会整体糊掉。
 *
 * 已选态换成主题主色实心 + 白勾：这是**状态**，必须比"这里有个框"更响。
 *
 * 尺寸全部取现成 token（`badgeSize` 触控区、`chipIconSize` 字形），不新造数字。
 *
 * @param selected 已选。
 * @param onClick 点框 = 切换这一项的选中状态。**必须自己接**：
 *   框挂在卡片之上，点它不能顺手把卡片也点了（开了详情页就回不来了）。
 */
@Composable
fun VeneraSelectBox(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .size(tokens.spacing.badgeSize)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(tokens.color.primary)
                } else {
                    Modifier
                        .background(StatusColors.BadgeSurface)
                        .border(tokens.spacing.hairline, StatusColors.OnBadgeSurface, CircleShape)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选择",
                tint = tokens.color.onPrimary,
                modifier = Modifier.size(tokens.spacing.chipIconSize),
            )
        }
    }
}
