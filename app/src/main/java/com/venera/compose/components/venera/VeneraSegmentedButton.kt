package com.venera.compose.components.venera

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.isWideScreen
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 分段选择器：**每枚选项各自一颗药丸**，没有外框。
 *
 * 2026-09-28 换掉旧形态（用户点名照 pixez-miuix 动态页那排「全部 / 公开 / 私密」改）。
 * 旧版是 MD3 的"药丸中的药丸"：整条大药丸描一根 0.5dp hairline，里面一颗实心块滑动。
 * 那个结构在**实时模糊的玻璃顶栏**上不成立 —— 容器描边贴在亮画作上时几乎看不见，
 * 于是整条读起来像一个断掉的框（真机截图：画廊两页切换与收藏页图片收藏那一行都这样）。
 * 每颗自己带底就不依赖描边了，背后是什么内容都读得清。
 *
 * 保留的既有口径（这些是历轮真机反馈定的，不随这次改动）：
 *  - 高 48dp（`spacing.segmentedHeight`；用户真机反馈 40dp 太矮细弱后升档），
 *    宽屏档 `segmentedHeightWide`（56dp）并升字号到 `itemTitle`（用户平板演示拍板）；
 *  - 单元间隙 `spacing.segmentedGap`（10dp，取现成的 space5）；
 *  - 文字 14sp（`type.body`）Medium，单行省略；
 *  - 选中 = **实心 primary**（主题色，动态色板驱动，不写死色值）+ `onPrimary` 文字；
 *  - 整条宽度仍由**调用方**按"单段占屏宽 25%"推导（用户"太宽太散"那条反馈），本组件不自己定宽。
 *
 * 未选中档：`surfaceVariant` 叠现成的 `selectedSurfaceAlpha`（0.5，VeneraChip 禁用态同一档）。
 * 刻意**半透明**而不是涂实底 —— 玻璃顶栏上涂不透明底会拉出一条横贯屏幕的硬边（已拍板那条）。
 *
 * 动画：旧那颗滑动的果冻块依附容器才成立，取消；换成每颗自己的底色淡变 + 轻微弹性缩放，
 * 阻尼仍取 0.7 那一族（与旧滑动块、布局小钮同族，别另起一档手感）。
 */
@Composable
fun VeneraSegmentedButton(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val wide = isWideScreen(LocalConfiguration.current.screenWidthDp.dp)
    val barHeight = if (wide) tokens.spacing.segmentedHeightWide else tokens.spacing.segmentedHeight
    val labelSize = if (wide) tokens.type.itemTitle else tokens.type.body
    val shape = RoundedCornerShape(percent = 50)

    Row(
        modifier = modifier.height(barHeight),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.segmentedGap),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            // 缩放只影响绘制，不动布局：三颗的宽度不会因为哪颗在缩而互相推挤。
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.96f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "segmentedCellScale",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .scale(scale)
                    .clip(shape)
                    .background(
                        if (selected) {
                            tokens.color.primary
                        } else {
                            tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)
                        },
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = labelSize,
                    fontWeight = tokens.type.weightMedium,
                    color = if (selected) tokens.color.onPrimary else tokens.color.textPrimary,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = tokens.spacing.space5),
                )
            }
        }
    }
}
