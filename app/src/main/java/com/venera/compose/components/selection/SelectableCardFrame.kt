package com.venera.compose.components.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.venera.compose.components.venera.VeneraSelectBox
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 把任意一张卡片包成"可多选"的卡片：**选中洗底 + 右上角选择框**。
 *
 * ## 为什么是包一层，而不是给每种卡加参数
 *
 * 收藏页四处的卡片载体各不相同（本地收藏是 `VeneraCard` / `ComicRowCard`、
 * 插图收藏是 miuix `Card`、网络收藏是 `VeneraCard`、画廊是 `GalleryPostCard`），
 * 但它们对"选中"的表达必须**逐像素一致**，否则又回到"同一件事两种画法"。
 * 于是共用这一个 wrapper：卡片长什么样各家自己管，选中态只有这一处实现。
 *
 * ## 两层各自的作用（缺一不可）
 *
 * - **洗底**：选中时整卡蒙一层极淡的主色。它压**在内容之上** —— 卡片载体自带不透明
 *   表面色，铺在下面根本透不出来。代价是封面与文字都被染上一层，
 *   所以 alpha 取 [com.venera.compose.ui.tokens.VeneraTokenSet.selectionHighlightAlpha]
 *   那一档（很低），它负责"一眼看出这张被选了"，而不是"把这张点亮"。
 * - **选择框**：右上角那一枚。洗底说明"选中了"，框说明"**可以**选中、点这里切换" ——
 *   只有洗底没有框，用户在多选态里找不到逐个勾的入口。
 *
 * ## 事件
 *
 * 洗底层与内容层都**不消费**指针事件（没有 pointerInput / clickable 的 Box 不参与命中测试），
 * 所以点卡片仍然落到卡片自己的 `onClick` 上；只有选择框那一枚是可点的。
 * 这正是要的：多选态下点卡片 = 勾选（由调用方在卡片的 onClick 里判 `selecting`），
 * 点框 = 勾选，两条路都通。
 *
 * ## ⚠️ 洗底必须自己裁圆角
 *
 * 洗底是 `matchParentSize()` 的直角矩形，而它盖着的卡片是圆角矩形 —— 不裁的话，
 * 卡片圆角**外面**那四个小角会被染上主色（父级底色是列表背景，露出来一眼可见）。
 * 所以这里按 [cornerRadius] 裁一刀，默认取 `shape.card`（`VeneraCard` 用的同一档）。
 * 卡片换用别的圆角时调用方要显式传，否则四个角会重新露出来。
 *
 * @param selecting 当前**是否处于多选态**。false 时选择框整枚不摆（不是变透明）。
 * @param selected 这一项是否已选。
 * @param onToggleSelect 点选择框。
 * @param cornerRadius 卡片本体的圆角半径（洗底按它裁）。默认 `shape.card`。
 * @param content 卡片本体。
 */
@Composable
fun SelectableCardFrame(
    selecting: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = VeneraTokens.shape.card,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = VeneraTokens
    Box(modifier = modifier) {
        content()
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(
                        tokens.color.primary.copy(alpha = tokens.current.selectionHighlightAlpha)
                    )
            )
        }
        if (selecting) {
            VeneraSelectBox(
                selected = selected,
                onClick = onToggleSelect,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(tokens.spacing.space4),
            )
        }
    }
}
