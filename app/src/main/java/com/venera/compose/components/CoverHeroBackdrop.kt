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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.imageLoader
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
 * Hero 背景：底图放大、模糊、整层压到 [VeneraTokens] 的 `heroBackdropAlpha`，
 * 底边擦成透明以软着陆到页面背景。
 *
 * **两处在用，档位不同**，靠 [blurRadius] / [backdropScale] 两个参数分，不靠调用方
 * 传不同的 `modifier`：
 * - 漫画详情页 —— 一个参数都不传（默认 `space11` 32dp × 1.35）。那里底图**压在封面卡下面**，
 *   职责只是"把头部染成这张封面的颜色"，糊到什么也认不出正是要的效果。
 * - 画师介绍页 —— 显式传轻档（`artistHeroBackdropBlur` / `artistHeroBackdropScale`）。
 *   那里底图**就是这一页唯一的主视觉**，同一个档摆过去只剩一块色块。
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
    /**
     * 取图用的 ImageLoader；null = 应用级单例（漫画侧现状，调用点一个都不变）。
     *
     * 画廊那侧必须显式传自己的实例：那边的图要带 Referer / UA 才拿得到
     * （见 `gallery/data/GalleryImageLoader.kt`），用默认单例发出去就是一枚 403 的空底 ——
     * 而屏上"只是一块没有图的纯色面"，谁也不会以为它坏了。
     */
    imageLoader: ImageLoader? = null,
    /**
     * 底图的**模糊核半径**（不是屏上看到的模糊量，见 [backdropScale]）。
     *
     * 默认 `space11`(32dp) 是漫画详情页那一档：那里底图压在封面卡下面、只负责"把头部
     * 染成这张封面的颜色"，糊到什么也认不出正是它的职责，所以那一处一个参数都不用传。
     *
     * 换用途时这一档必须跟着换 —— 画师介绍页里底图是**这一页唯一的主视觉**，
     * 同一条 32dp 叠上放大之后屏上约 43dp，只剩色块（2026-10-01 用户报「模糊过头」）。
     */
    blurRadius: Dp = VeneraTokens.spacing.space11,
    /**
     * 底图的放大量。与 [blurRadius] 是**一对**，调一个就得调另一个。
     *
     * 它存在的唯一理由是给模糊留出外扩余量：`Modifier.blur` 的默认边缘处理会按原矩形
     * 把模糊结果裁掉，不放大就会在四边露出一圈被裁淡的边。下界是
     * `1 + 2 × blur / min(宽, 高)`（横条按矮边算，别按宽边）。
     *
     * ⚠️ 顺序：下面 modifier 链是 `.scale(backdropScale).blur(blurRadius)`，
     * 缩放挂在外层 ⇒ **屏上实际模糊 ≈ `blurRadius × backdropScale`**。
     * 读参数时两个数要一起看，别只看前者。默认的 1.35 是为 32dp 那个量级配的，
     * 模糊降到 10dp 一级时它要跟着降到 1.15（见 `VeneraSpacingTokens.artistHeroBackdropScale`）。
     */
    backdropScale: Float = 1.35f,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    // tokens.current 是 @Composable getter：先在组合里取成局部量，再带进 graphicsLayer。
    // （两个新参数是普通值类型，不受这条约束。）
    val backdropAlpha = tokens.current.heroBackdropAlpha
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
            imageLoader = imageLoader ?: context.imageLoader,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                // 放大一档只是为了给下面的模糊留外扩余量（见 backdropScale 那条注释）。
                // 注意顺序：scale 在外层 ⇒ 屏上模糊 ≈ blurRadius × backdropScale。
                .scale(backdropScale)
                .blur(blurRadius),
        )
    }
}
