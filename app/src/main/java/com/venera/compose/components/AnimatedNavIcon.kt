package com.venera.compose.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 带物理弹性形变（Q弹跳跃）的导航图标组件。
 *
 * 当 [selected] 变为 true 时：
 * 1. 触发轻微下压缩放 (0.88x) 与微量下沉；
 * 2. 紧接着向上反弹跳跃至 1.16x 并带有微量负向 translationY；
 * 3. 最终以高刚度临界阻尼弹簧平滑回弹锁定在 1.0x 正常尺寸；
 * 图标同步由 outlined 平滑切为 filled。
 */
@Composable
fun AnimatedNavIcon(
    tab: VeneraNavTab,
    selected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    baseSize: Dp = 22.dp,
) {
    val scale = remember { Animatable(1f) }
    val bounceY = remember { Animatable(0f) }

    LaunchedEffect(selected) {
        if (selected) {
            // 下压蓄力
            scale.animateTo(
                targetValue = 0.88f,
                animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy)
            )
            // 向上回弹并伴随轻微弹性超调
            bounceY.animateTo(
                targetValue = -3f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.5f)
            )
            // 回归稳态
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.65f)
            )
            bounceY.animateTo(
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
            )
        } else {
            scale.snapTo(1f)
            bounceY.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .size(baseSize)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationY = bounceY.value
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
            contentDescription = tab.title,
            tint = tint,
            modifier = Modifier.size(baseSize),
        )
    }
}
