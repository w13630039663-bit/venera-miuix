package com.venera.compose.components.venera

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * Venera 骨架屏（基础层）：**静态浅底 + 一道从左扫到右的流光**。
 *
 * 为什么不再是 alpha 呼吸：原先这里只做 `skeletonAlpha → skeletonAlpha * 2.2` 的明暗变化，
 * 而底色是 theme-aware 的 surfaceVariant —— 它本来就比外壳背景浅不了多少，
 * 0.12 与 0.26 之间的差在手机和平板上都几乎看不出来。master 的骨架用的是
 * `shimmer_animation` 包（`lib/components/loading.dart:141`），显眼的正是那道
 * **会移动的色带**，而不是底色变化 —— 这里按该包的真实参数逐字移植。
 *
 * 性能：色带取「固定 Brush + 每帧 translate」，不逐帧重建 shader；骨架屏一屏会出
 * 几十个方块，逐帧分配 Brush 的垃圾很可观。也没有引入 blur（原注释担心的就是这个），
 * 流光只是线性渐变。
 *
 * Theme-aware：底色仍取 surfaceVariant；流光颜色按 master 的取法
 * （`dark ? Colors.white : Colors.black`），不写死灰度值。
 */
@Composable
fun VeneraShimmer(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(VeneraTokens.shape.small),
) {
    val tokens = VeneraTokens
    val isDark = LocalVeneraDarkTheme.current
    val transition = rememberInfiniteTransition(label = "venera-shimmer")
    // 包内是 Tween(0→1) 配 CurvedAnimation(Interval(0, 0.6, Curves.decelerate))：
    // 行程只占周期的前 60% 并减速，后 40% 停一拍 —— 读起来是"扫一下、歇一下"。
    // 周期取包的默认 3s。时钟与行程拆开算，避免把 Interval 塞进 tween 的 easing 里。
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = tokens.spacing.shimmerPeriodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "venera-shimmer-clock",
    )
    val position = if (clock <= ShimmerTravelFraction) {
        ShimmerDecelerate.transform(clock / ShimmerTravelFraction)
    } else {
        1f
    }

    val sweep = if (isDark) Color.White else Color.Black
    val base = tokens.color.surfaceVariant.copy(alpha = tokens.current.skeletonAlpha)

    // clip 必须在 drawWithCache **之前**：Modifier 链里靠后的节点才受靠前的裁剪约束，
    // 反过来的话色带会画出圆角之外。
    Box(modifier = modifier.clip(shape).drawWithCache {
        // 渐变跨度取包内 shader rect 的 2 倍宽（-0.5w .. 1.5w）；
        // 峰值占跨度 50% 处，两侧各 20% 淡出（包内 width = 0.2）。
        val span = size.width * 2f
        val band = Brush.linearGradient(
            0f to Color.Transparent,
            ShimmerBandPeakStop - ShimmerBandHalf to sweep.copy(alpha = ShimmerBandEdgeAlpha),
            ShimmerBandPeakStop to sweep.copy(alpha = ShimmerBandCoreAlpha),
            ShimmerBandPeakStop + ShimmerBandHalf to sweep.copy(alpha = ShimmerBandEdgeAlpha),
            1f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(span, size.height),
        )
        onDrawBehind {
            drawRect(base)
            // 包内靠"移动色标"实现，这里等价地"固定色标 + 平移画布"：
            // 视觉上同一道带子，但每帧不再重建 shader。
            translate((position * 2f - 1f) * size.width) {
                drawRect(brush = band, size = Size(span, size.height))
            }
        }
    })
}

/** 包内 Curves.decelerate = Cubic(0.0, 0.0, 0.2, 1.0)。 */
private val ShimmerDecelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/** 包内 CurvedAnimation 的 Interval(0, 0.6)：行程只占周期的前 60%。 */
private const val ShimmerTravelFraction = 0.6f

/** 峰值放在跨度中点；包内 width = 0.2 ⇒ 半带宽 0.2（两侧各 20% 淡出）。 */
private const val ShimmerBandPeakStop = 0.5f
private const val ShimmerBandHalf = 0.2f

/** 包内色标 [transparent, c@0.05, c@colorOpacity, c@0.05, transparent]，colorOpacity 默认 0.3。 */
private const val ShimmerBandEdgeAlpha = 0.05f
private const val ShimmerBandCoreAlpha = 0.3f

/**
 * 封面骨架：固定 3:4 比例，与 [VeneraCover] 的占位几何一致。
 * 之所以提供它，是为了让「加载中」与「加载完成」不发生布局跳动。
 */
@Composable
fun VeneraCoverShimmer(modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    VeneraShimmer(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(tokens.shape.medium)),
        shape = RoundedCornerShape(tokens.shape.medium),
    )
}
