package com.venera.compose.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
                state.animation.snapTo(predictiveBackProgress(event.progress))
            }
        } catch (cancelled: CancellationException) {
            commit.cancel()
            // The platform cancels this coroutine on gesture cancellation. Keep the surface
            // alive and roll back without mutating the stack; do not swallow other failures.
            withContext(NonCancellable) {
                state.animation.animateTo(0f, spring(dampingRatio = 1f, stiffness = 600f))
            }
            throw cancelled
        }
        if (currentEnabled() && startedKey == currentKey()) {
            if (receivedProgress) state.animation.animateTo(1f, tween(120))
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

