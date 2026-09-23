package com.venera.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 竖向擦除渐变。DstIn 只看 alpha：白=保留、透明=擦掉。
 *
 * **只有底部收口**：这一层会被出血变换撑到屏幕左/右/上三条边（见 [CoverHeroBackdrop] 的
 * [bleedHorizontal]/[bleedTop]），那三条边落在屏幕边缘上，本来就没有"边界"可言；
 * 顶部再擦一次反而会在顶栏下面留一条可见的收口线。
 * 比例是相对值，与 Hero 实际高度无关。
 */
private val FadeToBottom = Brush.verticalGradient(
    0f to Color.White,
    0.46f to Color.White,
    1f to Color.Transparent,
)

/**
 * 详情页 Hero 背景：封面放大、重模糊、整层压到 [VeneraTokens] 的 `heroBackdropAlpha`，
 * 底边擦成透明以软着陆到页面背景。
 *
 * 为什么是"擦"而不是"盖一圈页面底色的渐变"：页面背景不是纯色，它底下还铺着
 * `VeneraAmbientBackground` 的两团主题色光斑。拿 `surface` 画一圈渐变去遮，
 * 遮出来的是一整块不透明的平面矩形 —— 真机第一版就栽在这里（深色主题下头部变成一块
 * 边缘清晰的紫色矩形）。DstIn 擦出来的洞透出的是**真正那层背景**，淡出才是连续的。
 *
 * 出血走 [graphicsLayer] 的缩放+平移，**不走** `Modifier.padding(负值)`：
 * 后者在 Compose 里是硬校验，运行时直接 `IllegalArgumentException: Padding must be non-negative`
 * （真机闪退实测）。绘制变换不碰布局，item 的测量尺寸一点不变，也就不可能把列表顶乱。
 * 背景本来就是一团模糊，非等比拉伸看不出来。
 *
 * 三条会静默出错的点：
 * - DstIn 必须落在**离屏层**上（`CompositingStrategy.Offscreen`），否则混合直接作用到窗口画布，
 *   等于把整扇窗口那块擦穿。
 * - `tokens.current` 是 @Composable getter，不能在 `graphicsLayer {}` 的 lambda 里读 ——
 *   那里不是组合上下文，读了不会随深浅色刷新（本仓库同一格踩过两次）。
 * - **内容守卫打码命中时不要画这里**：模糊大图会绕过打码。调用方判 `maskState`。
 */
@Composable
fun CoverHeroBackdrop(
    coverUrl: String,
    modifier: Modifier = Modifier,
    /** 左右各撑出去多少（把这一层铺到屏幕两边，消掉列表 contentPadding 留下的两条边）。 */
    bleedHorizontal: Dp = 0.dp,
    /** 往上撑出去多少（顶穿状态栏与顶栏，做真·沉浸头部）。 */
    bleedTop: Dp = 0.dp,
) {
    val tokens = VeneraTokens
    // tokens.current / tokens.spacing 都是 @Composable getter：先在组合里取成局部量，
    // 再带进 graphicsLayer 与 draw 的 lambda。
    val backdropAlpha = tokens.current.heroBackdropAlpha
    val blurRadius = tokens.spacing.space11
    if (coverUrl.isBlank()) return
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .onSizeChanged { layerSize = it }
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = backdropAlpha
                val w = layerSize.width.toFloat()
                val h = layerSize.height.toFloat()
                if (w > 0f && h > 0f) {
                    val bleedH = bleedHorizontal.toPx()
                    val bleedT = bleedTop.toPx()
                    // 缩放以中心为原点：左右各扩 bleedH 正好对齐屏幕两边；
                    // 纵向只往上扩，所以再平移 -bleedT/2 把下边缘挪回原位。
                    scaleX = (w + 2f * bleedH) / w
                    scaleY = (h + bleedT) / h
                    translationY = -bleedT / 2f
                }
            }
            .drawWithContent {
                drawContent()
                drawRect(brush = FadeToBottom, blendMode = BlendMode.DstIn)
            },
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                // 放大一档：不放大时背景还能"认出是那张封面"，构图细节全在，
                // 观感就是"贴了张模糊图"。放大 + 下面那档重模糊才成"氛围"。
                .scale(1.35f)
                // 比打码那档（space10=24dp）再重一级，取现成的 space11=32dp。
                .blur(blurRadius),
        )
    }
}
