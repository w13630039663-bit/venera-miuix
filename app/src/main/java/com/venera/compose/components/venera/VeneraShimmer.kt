package com.venera.compose.components.venera

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * Venera 骨架屏（基础层）。
 *
 * 设计约束：
 *  - **仅做 alpha 呼吸动画**，不使用实时 blur / gradient shader —— 骨架屏通常成批出现
 *    （一屏几十个方块），实时模糊会显著增加 GPU 负担。
 *  - Theme-aware：颜色取 surfaceVariant（两套主题各自正确），不写死灰度值。
 *  - 不依赖任何页面：只接受 modifier 与 bounds。
 *
 * 用法：
 * ```
 * VeneraShimmer(Modifier.fillMaxWidth().aspectRatio(tokens.spacing.coverAspectRatio))
 * ```
 */
@Composable
fun VeneraShimmer(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(VeneraTokens.shape.small),
) {
    val tokens = VeneraTokens
    val transition = rememberInfiniteTransition(label = "venera-shimmer")
    val alpha by transition.animateFloat(
        initialValue = tokens.current.skeletonAlpha,
        targetValue = tokens.current.skeletonAlpha * 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = tokens.spacing.shimmerPeriodMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "venera-shimmer-alpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(tokens.color.surfaceVariant.copy(alpha = alpha)),
    )
}

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
