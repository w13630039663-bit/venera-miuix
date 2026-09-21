package com.venera.compose.feature

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 主页面之间的**左右滑动切页**。
 *
 * 设计要点（都是为了不和现有手势打架）：
 *
 *  1. 只在**水平位移占优**时接管（|dx| > |dy| * 1.5），纵向滚动列表完全不受影响。
 *  2. **整条手势跑在 [PointerEventPass.Final]**。这是「轮播滑不动 / 一滑就切页」的根因修复：
 *     Main 趟虽然也是孩子先收，但本模块的接管阈值只有 6px，而子组件（轮播 / 横向列表）
 *     自己的触摸斜率阈值是 18dp —— 父级总是先claim再把横向位移 consume 掉，
 *     孩子从此拿到位移 0，于是「想抽卡却翻了页」。Final 趟在整条 Main 之后派发，
 *     孩子消费过的位移在这里是 consumed 状态，直接让位。
 *  3. 起手点落在 [LocalTabSwipeExclusions] 登记的让位带里 -> 整回合完全不参与翻页。
 *  4. 起点落在屏幕左右边缘时不接管（留给系统返回手势）。
 *  5. 松手时按「位移 + 速度」双重判定翻页，快速轻扫也能翻。
 *
 * 注意：这个 modifier **不会**挂到玻璃底栏上 —— 底栏有自己的横向拖拽（指示器跟手），
 * 两者必须互斥，否则拖底栏会同时触发翻页。
 *
 * @param enabled 仅主 tab 页面开启；详情页/阅读器等不要翻页。
 * @param bottomExclusionDp 底部排除带高度：落在这一带内的手势完全不参与翻页。
 *        悬浮底栏就叠在这里，它有自己的横向拖拽（指示器跟手），两者必须互斥。
 * @param exclusions 让位带登记表，必须与页面侧 [LocalTabSwipeExclusions] 是同一个实例。
 */
fun Modifier.tabSwipePager(
    enabled: Boolean,
    onSwipeForward: () -> Unit,
    onSwipeBackward: () -> Unit,
    bottomExclusionDp: Int = 0,
    exclusions: TabSwipeExclusionRegistry = TabSwipeExclusionRegistry(),
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
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val startX = down.position.x
            val startY = down.position.y
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            val fromEdge = startX <= edgeReserved || startX >= width - edgeReserved
            // 起手点在悬浮底栏区域内 -> 这次手势属于底栏（它的拖拽/点击），翻页不参与。
            val fromBottomBar = bottomExclusion > 0f && startY >= height - bottomExclusion
            // 起手点在页面自己登记的让位带里 -> 这次横滑是子区块的，翻页整回合退出。
            if (exclusions.contains(startY)) return@awaitEachGesture

            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)

            var totalDx = 0f
            var totalDy = 0f
            var claimed = false
            // 子节点已经消费这次拖拽（轮播/横向列表在吃它）-> 整回合退出，后面也别再抢。
            var yielded = false

            // foundation 的 drag() 没有 pass 参数（写死 Main），所以 Final 趟自己写读取循环。
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) break
                if (yielded) continue

                val delta = change.positionChange()
                totalDx += delta.x
                totalDy += delta.y
                tracker.addPosition(change.uptimeMillis, change.position)

                if (!claimed) {
                    if (change.isConsumed) {
                        // Final 趟里 consumed 就是「孩子已经拿走这次位移」的信号。
                        yielded = true
                        continue
                    }
                    val movedEnough = abs(totalDx) >= 6f || abs(totalDy) >= 6f
                    if (!movedEnough) continue
                    // 水平占优且不是从边缘/底栏起手 -> 这次手势归我们；否则本回合判死。
                    if (abs(totalDx) > abs(totalDy) * 1.5f && !fromEdge && !fromBottomBar) {
                        claimed = true
                    } else {
                        yielded = true
                        continue
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

/**
 * 「左右滑别切页」的让位带登记表。
 *
 * [tabSwipePager] 挂在 NavHost 上，天然会截走整屏的横向位移；页面里自己吃横滑的区块
 * （首页推荐轮播那一整块）把根坐标下的纵向区间登记进来，起手点落在带内的手势就整回合不翻页。
 * 带子存的是 `MutableState<IntRange>` —— 列表滚动时区间一直在动，注册一次、随布局实时更新。
 */
class TabSwipeExclusionRegistry {
    private val bands = mutableStateListOf<MutableState<IntRange>>()

    fun add(band: MutableState<IntRange>): () -> Unit {
        bands += band
        return { bands -= band }
    }

    fun contains(y: Float): Boolean {
        val pixel = y.roundToInt()
        return bands.any { pixel in it.value }
    }
}

/** 外壳 provide 一次；页面侧只通过 [Modifier.tabSwipeExcluded] 间接使用。 */
val LocalTabSwipeExclusions = compositionLocalOf { TabSwipeExclusionRegistry() }

/**
 * 把这一整块登记成「左右滑不切页」的区域：随滚动实时更新的纵向区间。
 * 组件卸载即反注册，不会残留一条幽灵带误伤别的页面。
 */
@Composable
fun Modifier.tabSwipeExcluded(): Modifier {
    val registry = LocalTabSwipeExclusions.current
    val band = remember { mutableStateOf(0..-1) }
    DisposableEffect(registry) {
        val unregister = registry.add(band)
        onDispose { unregister() }
    }
    return this.onGloballyPositioned { coords ->
        val top = coords.positionInRoot().y.roundToInt()
        band.value = top..(top + coords.size.height)
    }
}
