package com.venera.compose.components.venera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.venera.compose.components.isWideScreen
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * MD3 Segmented Button（用户拍板定制版）：整条大药丸 + 一段果冻滑动的实心选中块。
 *
 * 用户设计口径（覆盖 MD3 默认的 secondaryContainer@12% 弱填充）：
 *  - 高 48dp（spacing.segmentedHeight，MD3 组件默认档；用户真机反馈 40dp 太矮细弱后升档）；
 *    宽屏档（isWideScreen，>600dp）再加高到 spacing.segmentedHeightWide（56dp）
 *    并升字号到 itemTitle——用户平板演示拍板「大屏上又扁又细弱」；
 *  - 容器全胶囊 + hairlineThin（0.5dp）描边，颜色走 outlineVariant（浅色 #E0E0E0 / 深色 #404040 的令牌等价）；
 *  - 单元间隙 segmentedGap（4dp），相邻**未选中**单元之间画一根 hairline 竖分隔线
 *    （MD3 规定选中段两侧的分隔线让位）；
 *  - 文字 14sp（type.body）Medium；
 *  - 选中块 = **实心 primary**（主题色，动态色板驱动，不写死 #E91E63）+ 文字转 onPrimary（白）。
 *
 * 滑动用阻尼 0.7 的 spring（用户拍板），几何按实测像素跟随（折叠/分栏改宽自动重排）。
 */
@Composable
fun VeneraSegmentedButton(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val density = LocalDensity.current
    // 档位判定走 WideScreenPolicy 的唯一阈值（master changePoint），手机档保持 40dp+body 原样。
    val wide = isWideScreen(LocalConfiguration.current.screenWidthDp.dp)
    val barHeight = if (wide) tokens.spacing.segmentedHeightWide else tokens.spacing.segmentedHeight
    val labelSize = if (wide) tokens.type.itemTitle else tokens.type.body
    val gapPx = with(density) { tokens.spacing.segmentedGap.toPx() }
    val shape = RoundedCornerShape(percent = 50)

    // 单元实测宽（各段等宽，取第 0 段测量值）；测得前块宽为 0，不会满宽闪跳。
    var cellWidthPx by remember { mutableFloatStateOf(0f) }
    // 选中块目标 x。内容区已被外层 padding 让出一个 gap，单元之间又各有 gap，
    // 所以第 i 段的左边缘就在 i*(cellW+gap) —— 此前多算了一个 gap，
    // 选中块会整体右溢、顶到容器描边上，看着就是「直角矩形撑满半边」而非药丸。
    val targetOffsetPx = selectedIndex * (cellWidthPx + gapPx)
    val slide = remember { Animatable(targetOffsetPx) }
    LaunchedEffect(targetOffsetPx) {
        slide.animateTo(
            targetOffsetPx,
            spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        )
    }

    Surface(
        modifier = modifier.height(barHeight),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(tokens.spacing.hairlineThin, tokens.color.outlineVariant),
    ) {
        // 外层 padding = 单元间隙：分隔线挂在单元末端即自然落在两格正中间。
        Box(Modifier.fillMaxSize().padding(gapPx.dp)) {
            // 果冻选中块：实心主题色，先于单元渲染（背景层），文字压在其上。
            Box(
                Modifier
                    .offset { IntOffset(slide.value.toInt(), 0) }
                    .width(with(density) { cellWidthPx.toDp() })
                    .fillMaxHeight()
                    .clip(shape)
                    .background(tokens.color.primary),
            )
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gapPx.dp),
            ) {
                options.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onSizeChanged { size ->
                                if (index == 0) cellWidthPx = size.width.toFloat()
                            }
                            .clip(shape)
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
                        // 相邻未选中单元之间的竖分隔线（选中段两侧让位）。
                        // 单元之间现在真有 gap，故要右移半个 gap 才落在间隙正中。
                        if (!selected && index != options.lastIndex && index + 1 != selectedIndex) {
                            Box(
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .offset { IntOffset((gapPx / 2f).toInt(), 0) }
                                    .width(tokens.spacing.hairline)
                                    .fillMaxHeight()
                                    .background(tokens.color.divider),
                            )
                        }
                    }
                }
            }
        }
    }
}
