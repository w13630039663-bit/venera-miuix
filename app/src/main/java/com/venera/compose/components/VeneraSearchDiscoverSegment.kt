package com.venera.compose.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** 「搜索与发现」页的两个分段（2026-11-03，探索 tab 并入搜索页的落点）。 */
enum class SearchDiscoverSegment(val label: String) {
    SEARCH("搜索"),
    EXPLORE("探索"),
}

/**
 * 「搜索 / 探索」分段胶囊（两屏共用；视觉与交互稿一致：滑块随选中段平移）。
 *
 * 为什么放 components：搜索屏与探索屏**两颗都要画同一枚**（各自目的地里一枚，
 * 指向自己那段的按钮置灰为当前），分属 feature 与 feature.explore 两个包 ——
 * 谁拥有都不对，共享层才是它的家（`WideScreenPolicy` 那条"唯一口径"的同一理由）。
 *
 * 曲线与引导共享元素同族（M-B 0.2,0,0,1），全应用一套动效语言。
 */
@Composable
fun SearchDiscoverSegmentBar(
    current: SearchDiscoverSegment,
    onSwitch: (SearchDiscoverSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(3.dp),
    ) {
        val half = maxWidth / 2
        val slide by animateDpAsState(
            targetValue = if (current == SearchDiscoverSegment.EXPLORE) half else 0.dp,
            animationSpec = tween(260, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
            label = "discoverSegmentSlide",
        )
        Box(
            Modifier
                .width(half)
                .fillMaxHeight()
                .offset(x = slide)
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.surface),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            SearchDiscoverSegment.entries.forEach { seg ->
                val selected = seg == current
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSwitch(seg) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        seg.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
