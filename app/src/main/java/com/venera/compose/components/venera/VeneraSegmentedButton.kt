package com.venera.compose.components.venera

import com.venera.compose.components.LocalWindowWidthDp
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.isWideScreen
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 分段选择器：**每枚选项各自一颗药丸**，没有外框。
 *
 * 旧版是 MD3 的"药丸中的药丸"：整条大药丸描一根 0.5dp hairline，里面一颗实心块滑动。
 * 那个结构在**实时模糊的玻璃顶栏**上不成立 —— 容器描边贴在亮画作上时几乎看不见，
 * 每颗自己带底就不依赖描边了，背后是什么内容都读得清。
 *
 *    宽屏档 `segmentedHeightWide`（56dp）并升字号到 `itemTitle`（用户平板演示拍板）；
 *  - 单元间隙 `spacing.segmentedGap`（10dp，取现成的 space5）；
 *  - 文字 14sp（`type.body`）Medium，单行省略；
 *  - 选中 = **实心 primary**（主题色，动态色板驱动，不写死色值）+ `onPrimary` 文字；
 *  - 整条宽度仍由**调用方**按"单段占屏宽 25%"推导（用户"太宽太散"那条反馈），本组件不自己定宽。
 *
 * 未选中档分两种底，由调用方用 [SegmentedBackdrop] 表态（**这两档只差一层 alpha，没有描边**）：
 *  - [SegmentedBackdrop.Glass]：`surfaceVariant` 叠现成的 `selectedSurfaceAlpha`（0.5，VeneraChip
 *    禁用态同一档）。刻意**半透明**而不是涂实底 —— 玻璃顶栏上涂不透明底会拉出一条横贯屏幕的硬边（已拍板那条）。
 *  - [SegmentedBackdrop.Opaque]：`surfaceVariant` **不透明**。实色卡（如画廊搜索卡）上那层 0.5 会被
 *    而这一排是首页摘掉重复件之后**唯一的换源入口**。涂实底就回到 MD3 本来的那一档
 *    （`surfaceVariant` 比 `surfaceContainerHigh` 低一档，约 1.05:1），不靠描边表达层次。
 *
 * 动画：旧那颗滑动的果冻块依附容器才成立，取消；换成每颗自己的底色淡变 + 轻微弹性缩放，
 * 阻尼仍取 0.7 那一族（与旧滑动块、布局小钮同族，别另起一档手感）。
 */
@Composable
fun VeneraSegmentedButton(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    /** 这一排落在哪种底上 —— 未选档"看不看得见"完全由它决定（见类头注那两条现量）。 */
    backdrop: SegmentedBackdrop,
    modifier: Modifier = Modifier,
    /**
     * 每段右侧的角标文本（null / 缺项 = 这一段不画角标）。
     *
     * 为什么做成可选参数而不是新组件：角标是"这一段还有几件事没看"的附属信息，
     * 与分段器本身的选中语义无关；另造一颗组件会把药丸底、缩放、玻璃档那套规矩抄第二份。
     */
    badges: List<String?> = emptyList(),
) {
    val tokens = VeneraTokens
    val wide = isWideScreen(LocalWindowWidthDp)
    val barHeight = if (wide) tokens.spacing.segmentedHeightWide else tokens.spacing.segmentedHeight
    val labelSize = if (wide) tokens.type.itemTitle else tokens.type.body
    val shape = RoundedCornerShape(percent = 50)

    Row(
        modifier = modifier.height(barHeight),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.segmentedGap),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val badge = badges.getOrNull(index)
            // 缩放只影响绘制，不动布局：几颗的宽度不会因为哪颗在缩而互相推挤。
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.96f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "segmentedCellScale",
            )
            val seat by animateColorAsState(
                targetValue = when {
                    selected -> tokens.color.primary
                    backdrop == SegmentedBackdrop.Opaque -> tokens.color.surfaceVariant
                    else -> tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)
                },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "segmentedCellSeat",
            )
            val labelColor by animateColorAsState(
                targetValue = if (selected) tokens.color.onPrimary else tokens.color.textPrimary,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "segmentedCellLabel",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .scale(scale)
                    .clip(shape)
                    .background(seat)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                // 四段档把横衬垫从 10dp 收到 6dp：393dp 屏上每格约 79dp，
                // 「本地收藏」四个字在 14sp 下约 56dp，10dp×2 的衬垫会把它挤成「本地收…」。
                val hPad = if (options.size >= 4) tokens.spacing.space3 else tokens.spacing.space5
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = hPad),
                ) {
                    Text(
                        text = label,
                        fontSize = labelSize,
                        fontWeight = tokens.type.weightMedium,
                        color = labelColor,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(tokens.spacing.space2))
                        // 角标本身也走一次换数动画：检查完从 3 变 0 时，直接消失会像点漏了
                        AnimatedContent(
                            targetState = badge,
                            transitionSpec = {
                                (scaleIn(initialScale = 0.7f) + fadeIn()) togetherWith
                                    (scaleOut(targetScale = 0.7f) + fadeOut())
                            },
                            label = "segmentedBadge",
                        ) { text ->
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) tokens.color.onPrimary
                                        else tokens.color.primary.copy(alpha = 0.16f)
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = text,
                                    fontSize = 10.sp,
                                    fontWeight = tokens.type.weightBold,
                                    color = if (selected) tokens.color.primary else tokens.color.primary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分段器所在的那层底。只有两档，且**两档都不画描边** —— 层次一律用底色表达。 */
enum class SegmentedBackdrop {
    /** 实时模糊的玻璃（顶栏 chrome 那一族）：半透明底，涂实会拉出横贯屏幕的硬边。 */
    Glass,

    /** 实色容器（卡、面板）：不透明底，否则半透明底被卡底吃掉、未选档看不见。 */
    Opaque,
}
