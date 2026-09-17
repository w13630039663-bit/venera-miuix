package com.venera.compose.feature

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 主页面之间的**左右滑动切页**。
 *
 * 设计要点（都是为了不和现有手势打架）：
 *
 *  1. 只在**水平位移占优**时接管（|dx| > |dy| * 1.5），纵向滚动列表完全不受影响。
 *  2. 第一段位移先「观望」再决定是否消费 —— 如果子节点（LazyRow / 横向 Pager / 图表）
 *     已经消费了这次拖拽，我们就放手，绝不抢夺子组件的手势。
 *  3. 起点落在屏幕左右边缘时不接管（留给系统返回手势）。
 *  4. 松手时按「位移 + 速度」双重判定翻页，快速轻扫也能翻。
 *
 * 注意：这个 modifier **不会**挂到玻璃底栏上 —— 底栏有自己的横向拖拽（指示器跟手），
 * 两者必须互斥，否则拖底栏会同时触发翻页。
 *
 * @param enabled 仅主 tab 页面开启；详情页/阅读器等不要翻页。
 * @param bottomExclusionDp 底部排除带高度：落在这一带内的手势完全不参与翻页。
 *        悬浮底栏就叠在这里，它有自己的横向拖拽（指示器跟手），两者必须互斥。
 */
fun Modifier.tabSwipePager(
    enabled: Boolean,
    onSwipeForward: () -> Unit,
    onSwipeBackward: () -> Unit,
    bottomExclusionDp: Int = 0,
): Modifier = composed {
    val currentForward by rememberUpdatedState(onSwipeForward)
    val currentBackward by rememberUpdatedState(onSwipeBackward)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    // 触发阈值：位移超过 56dp，或速度够快（快速轻扫）。
    val minDistance = with(density) { 56.dp.toPx() }
    val minVelocity = with(density) { 480.dp.toPx() }
    // 屏幕左右各留 24dp 给系统返回手势。
    val edgeReserved = with(density) { 24.dp.toPx() }
    // 底部排除带：悬浮底栏占用的高度，落在其中的手势交给底栏自己。
    val bottomExclusion = with(density) { bottomExclusionDp.dp.toPx() }

    if (!enabled) return@composed this

    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startX = down.position.x
            val startY = down.position.y
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            val fromEdge = startX <= edgeReserved || startX >= width - edgeReserved
            // 起手点在悬浮底栏区域内 -> 这次手势属于底栏（它的拖拽/点击），翻页不参与。
            val fromBottomBar = bottomExclusion > 0f && startY >= height - bottomExclusion

            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)

            var totalDx = 0f
            var totalDy = 0f
            var claimed = false

            drag(down.id) { change ->
                val delta = change.positionChange()
                totalDx += delta.x
                totalDy += delta.y
                tracker.addPosition(change.uptimeMillis, change.position)

                if (!claimed) {
                    val movedEnough = abs(totalDx) >= 6f || abs(totalDy) >= 6f
                    if (movedEnough) {
                        // 水平占优且不是从边缘起手 -> 这次手势归我们。
                        if (abs(totalDx) > abs(totalDy) * 1.5f && !fromEdge && !fromBottomBar) claimed = true
                        else return@drag   // 判定为纵向/边缘/底栏手势，本回合不再参与（也不消费）
                    } else {
                        return@drag
                    }
                }
                // 已接管：吃掉水平位移，避免它同时驱动外层滚动。
                if (delta.x != 0f) change.consume()
            }

            if (!claimed) return@awaitEachGesture

            val vx = tracker.calculateVelocity().x
            val farEnough = abs(totalDx) > minDistance
            val fastEnough = abs(vx) > minVelocity
            if (!farEnough && !fastEnough) return@awaitEachGesture

            // 在 RTL 下「向左滑」代表前进，方向语义翻转。
            val forward = if (isLtr) totalDx < 0 else totalDx > 0
            if (forward) currentForward() else currentBackward()
        }
    }
}
