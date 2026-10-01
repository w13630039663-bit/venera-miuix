package com.venera.compose.components.selection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 收藏页**唯一**的多选工具条。
 *
 * ## 为什么抽出来
 *
 * 抽之前四处各有一条：本地收藏是顶栏下方的一条两行卡（"已选择 N 项 / 全选 / 移动到 / 复制到 / 删除 / ✕"），
 * 插图收藏是**面板底部**的浮层单行条（"已选择 N 项 / 全选 / 移除 / 关闭"），
 * 另外两处连条都没有。同一个多选态在两个面板里长得不一样、位置也不一样，
 * 用户在 A 屏学会的"从哪儿退出多选"，到 B 屏就不成立了。
 *
 * 现在四处共用这一条，只有 `actions` 槽按面板不同 —— 那是真的不同
 * （本地收藏有收藏夹可以"移动到"，画廊收藏只有"移除"），不是画法分叉。
 *
 * ## 形态
 *
 * 两行：第一行是**状态与总控**（已选择 N 项 / 全选 / 反选 / 关闭），第二行是**动作**。
 * 第一行恒在，第二行由 `actions` 决定有没有内容（没有动作的面板自然只剩一行高）。
 *
 * 第一行这三枚正好是官方菜单里那三项整体操作（`local_favorites_page.dart:515-537`）：
 * "Select All" / "Invert Selection"、"Deselect"（= 关闭，官方那一个动作就是清空 + 退出）。
 * 关闭钮固定在最右，"全选/反选"紧邻它 —— "对整体下手"的三个动作挨在一起，
 * 与下面那排"对选中的东西下手"的动作分开，误触面小。
 *
 * @param selectedCount 已选条数。
 * @param allSelected 是否已全选（决定"全选"那一枚是否变灰不可点）。
 * @param onExit 退出多选（清空并关闭）—— 官方 "Deselect"。
 * @param onSelectAll 选中当前列表全部。
 * @param onInvert 反选当前列表全部。
 * @param actions 各面板自己的动作，横排；用 [MultiSelectBarAction] 摆，保证四格等宽同款。
 */
@Composable
fun VeneraMultiSelectBar(
    selectedCount: Int,
    allSelected: Boolean,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val tokens = VeneraTokens
    Surface(
        shape = RoundedCornerShape(tokens.shape.medium),
        color = MiuixTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.space6,
                vertical = tokens.spacing.space2,
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已选择 $selectedCount 项",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                // 全选已达成时这一枚不再有可做的事（官方 "Select All" 也是幂等的），
                // 变灰而不是变成"取消全选" —— 后者会与最右的关闭钮变成同一个动作。
                Text(
                    text = "全选",
                    fontSize = tokens.type.caption,
                    color = if (allSelected) tokens.color.textTertiary else tokens.color.primary,
                    modifier = Modifier
                        .clickable(enabled = !allSelected) { onSelectAll() }
                        .padding(tokens.spacing.space2),
                )
                Spacer(modifier = Modifier.width(tokens.spacing.space2))
                Text(
                    text = "反选",
                    fontSize = tokens.type.caption,
                    color = tokens.color.primary,
                    modifier = Modifier
                        .clickable { onInvert() }
                        .padding(tokens.spacing.space2),
                )
                Spacer(modifier = Modifier.width(tokens.spacing.space2))
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "退出多选",
                    tint = tokens.color.textPrimary,
                    modifier = Modifier
                        .size(tokens.spacing.chipIconSize)
                        .clickable { onExit() },
                )
            }
            Spacer(modifier = Modifier.height(tokens.spacing.space3))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2)) {
                actions()
            }
        }
    }
}

/**
 * 多选工具条里的一枚动作。四格等宽（调用方给 `Modifier.weight(1f)`），
 * 图标 + 文字一个模子 —— 条上不再出现"一枚是图标钮、一枚是文字钮"的混搭。
 */
@Composable
fun RowScope.MultiSelectBarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    /** 破坏性动作（删除 / 移除）用 1.0 的强调；普通动作走主色。 */
    destructive: Boolean = false,
) {
    val tokens = VeneraTokens
    // 破坏性动作用 StatusColors.Failing：与插图预览页那颗「移除收藏」同一个红，
    // 全应用的"删掉"只有这一种颜色。
    val tint = if (destructive) StatusColors.Failing else tokens.color.primary
    Surface(
        shape = RoundedCornerShape(tokens.shape.small),
        color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.space3,
                vertical = tokens.spacing.space5,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(tokens.spacing.chipIconSize),
            )
            Spacer(modifier = Modifier.width(tokens.spacing.space2))
            Text(
                text = label,
                fontSize = tokens.type.caption,
                color = tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
