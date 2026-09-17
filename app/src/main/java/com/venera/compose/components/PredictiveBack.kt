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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    PredictiveBackHandler(enabled = enabled) { events ->
        val startedKey = currentKey
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
        if (currentEnabled && startedKey == currentKey) {
            if (receivedProgress) state.animation.animateTo(1f, tween(120))
            // Recheck after suspension: a button/dialog may already have closed this layer.
            if (commit.tryCommit(currentEnabled, currentKey)) currentOnBack()
            // Let the new stack/visibility render before resetting the outgoing surface.
            withFrameNanos { }
        }
        state.animation.snapTo(0f)
    }
    return state
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
 * A small internal stack, not a second NavController. All stacked pages stay composed so
 * their remember state, scroll positions and running requests survive push/cancel/pop.
 * Only the top page is placed normally; the preceding page is placed during a back preview.
 * Hidden pages cannot draw, receive pointer input, or participate in accessibility hit tests.
 * Keys must be unique, Bundle-saveable entry IDs. Removed entries retain saveable state.
 */
@Composable
fun <T : Any> PredictiveBackStack(
    entries: List<T>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    entryKey: (T) -> String,
    content: @Composable (T) -> Unit,
) {
    require(entries.isNotEmpty())
    val state = rememberPredictiveBackState(
        enabled = entries.size > 1,
        gestureKey = entries.map(entryKey),
        onBack = onBack,
    )
    val savedState = rememberSaveableStateHolder()
    val surface = MiuixTheme.colorScheme.background
    Layout(
        modifier = modifier,
        content = {
            entries.forEachIndexed { index, entry ->
                key(entryKey(entry)) {
                    savedState.SaveableStateProvider(entryKey(entry)) {
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            val direction = if (state.fromRightEdge) -1f else 1f
                            val p = state.progress
                            translationX = when (index) {
                                entries.lastIndex -> size.width * p * direction
                                entries.lastIndex - 1 -> -size.width * .25f * (1f - p) * direction
                                else -> 0f
                            }
                        }.background(surface)) { content(entry) }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val pages = measurables.map { it.measure(constraints) }
        val width = pages.maxOfOrNull { it.width } ?: constraints.minWidth
        val height = pages.maxOfOrNull { it.height } ?: constraints.minHeight
        layout(width, height) {
            if (state.progress > 0f && pages.size > 1) pages[pages.lastIndex - 1].place(0, 0)
            pages.lastOrNull()?.place(0, 0)
        }
    }
}
