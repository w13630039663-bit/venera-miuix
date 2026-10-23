package com.venera.compose.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/** Real system gesture progress; never a timer simulating the user's drag. */
@Stable
class PredictiveBackState internal constructor() {
    internal val animation = Animatable(0f)
    val progress: Float get() = animation.value
    var fromRightEdge by mutableStateOf(false)
        internal set
}

/**
 * For an in-route UI layer only. Route pops belong to Navigation's seekable NavHost.
 * The callback runs once, after a committed gesture (or a legacy back press), never on cancel.
 * A newer gesture interrupts the previous rollback through Animatable's mutation mutex.
 */
@Composable
fun rememberPredictiveBackState(
    enabled: Boolean,
    gestureKey: Any? = enabled,
    onBack: () -> Unit,
): PredictiveBackState {
    val state = remember(gestureKey) { PredictiveBackState() }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentKey by rememberUpdatedState(gestureKey)
    val currentOnBack by rememberUpdatedState(onBack)
    // 预测性返回是 Android 的系统手势（`androidx.activity` 那颗件桌面没有）。
    // 2026-10-20（写法 A）：改成由平台服务报"这台平台支不支持"。
    //  - Android：`AndroidUxPlatformServices.supportsPredictiveBack` ⇒ 挂真 handler，行为不变；
    //  - 桌面：恒 false ⇒ 走 else 分支，不挂 handler。
    val ux = LocalUxPlatformServices.current
    if (ux.supportsPredictiveBack) {
        PredictiveBackGestureReceiver(
            enabled = enabled,
            state = state,
            currentEnabled = { currentEnabled },
            currentKey = { currentKey },
            currentOnBack = { currentOnBack() },
        )
    } else if (enabled) {
        // 平台没有预测性返回 ⇒ 进度恒 0（浮层不跟手），但"能关掉"不许丢：
        // 桌面侧的返回是导航栈的事（Esc / 返回按钮），所以这里**不**注册任何返回监听 ——
        // 挂一颗永远不触发的 BackHandler 会把 Esc 吃掉。
        // 只保留一条契约：composition 离开时把进度清掉（与真 handler 的收尾一致）。
        LaunchedEffect(gestureKey) {
            state.animation.snapTo(0f)
        }
    }
    return state
}

/**
 * 真挂 `PredictiveBackHandler` 的那一段 —— 单独提出来，是为了让
 * [rememberPredictiveBackState] 里**只留一个平台判据**，而不是把 handler 调用
 * 散在校验逻辑中间。桌面侧永远不会走到它（`supportsPredictiveBack` 恒 false）。
 */
@Composable
private fun PredictiveBackGestureReceiver(
    enabled: Boolean,
    state: PredictiveBackState,
    currentEnabled: () -> Boolean,
    currentKey: () -> Any?,
    currentOnBack: () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { events ->
        val startedKey = currentKey()
        val commit = PredictiveBackCommit(startedKey)
        var receivedProgress = false
        try {
            events.collect { event ->
                receivedProgress = true
                state.fromRightEdge = event.swipeEdge == BackEventCompat.EDGE_RIGHT
                // ── miuix 跟手弹簧（用户 2026-10-20 第 9 条）──
                // 从前的 `snapTo` 逐点直驱在澎湃 / HyperOS 那类只派发少量进度点的机器上
                // 是整页跳位；换成高刚度无回弹弹簧（`PredictiveFollowSpec`，与
                // `GalleryExitGesture` 那条跟手同源）后，读起来是「几乎贴手指、稀疏时平滑追赶」。
                // 弹簧自己带互斥，后到的事件打断前一笔补间 —— 语义与 snapTo 同级。
                state.animation.animateTo(predictiveBackProgress(event.progress), PredictiveFollowSpec)
            }
        } catch (cancelled: CancellationException) {
            commit.cancel()
            // The platform cancels this coroutine on gesture cancellation. Keep the surface
            // alive and roll back without mutating the stack; do not swallow other failures.
            withContext(NonCancellable) {
                // 回弹换 miuix 的 200ms + HyperOS 减速曲线（从前是 600f 弹簧 —— 那一档在
                // 松手瞬间有一段加速感，与原厂「先快后缓地滑回原位」不一致）。
                state.animation.animateTo(0f, PredictiveCancelSpec)
            }
            throw cancelled
        }
        if (currentEnabled() && startedKey == currentKey()) {
            // 确认收尾也换 miuix 的 240ms + HyperOS 曲线（从前 120ms tween 线性 —— 太急，
            // 且与取消那一侧的两档不等长，松手提交与滑回原位读起来不像同一种动效）。
            if (receivedProgress) state.animation.animateTo(1f, PredictiveFinishSpec)
            // Recheck after suspension: a button/dialog may already have closed this layer.
            if (commit.tryCommit(currentEnabled(), currentKey())) currentOnBack()
            // Let the new stack/visibility render before resetting the outgoing surface.
            withFrameNanos { }
        }
        state.animation.snapTo(0f)
    }
}

/** In-composition modal scrim; system Dialog/ModalBottomSheet keep their own back owner. */
@Composable
fun PredictiveBackOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val back = rememberPredictiveBackState(enabled = true, onBack = onDismiss)
    Box(
        modifier.fillMaxSize().graphicsLayer { alpha = 1f - back.progress }
            .background(Color.Black.copy(alpha = .45f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // Consume card-area taps instead of dismissing through the outer scrim.
        Box(Modifier.graphicsLayer {
            scaleX = 1f - .08f * back.progress
            scaleY = scaleX
            translationY = size.height * .1f * back.progress
        }.clickable { }) { content() }
    }
}

/**
 * 包装整个页面层级的 MIUIX / HyperOS 风格预测性返回手势容器（用于二级子页）。
 *
 * 当用户从屏幕左边缘或右边缘侧滑返回时：
 * - 页面整体平滑跟随手指横向拖拽（向右滑动离开），并同步进行 MIUIX 风格微缩放与圆角过渡；
 * - 露出的底层产生优雅的微视差与渐隐遮罩效果；
 * - 若用户松手确认，执行 [onBack] 弹出路由；若取消则按 HyperOS 减速曲线平滑回弹。
 */
@Composable
fun MiuixPredictiveBackPage(
    enabled: Boolean = true,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val back = rememberPredictiveBackState(enabled = enabled, onBack = onBack)
    val progress = back.progress
    val fromRight = back.fromRightEdge
    val maxRadius = VeneraTokens.shape.large
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                if (progress > 0.001f) {
                    val sign = if (fromRight) -1f else 1f
                    // 全宽横向位移
                    translationX = size.width * progress * sign
                    // 缩放至 0.94f，增强空间层次感
                    val scale = 1f - 0.06f * progress
                    scaleX = scale
                    scaleY = scale
                    // 边缘圆角平滑过渡（吃令牌中的大圆角）
                    val cornerRadius = maxRadius.toPx() * progress
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
                    clip = true
                    alpha = 1f - 0.2f * progress
                }
            }
    ) {
        content()
    }
}


