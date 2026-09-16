package com.venera.compose.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 原生漫画阅读器手势缩放与平移状态机（借鉴 PuffComic 核心手势算法）
 * 
 * 职责：
 * 1. 管理双击快速缩放（1.0x <-> 2.5x），带 240ms FastOutSlowInEasing 平滑曲线
 * 2. 管理双指 Pinch-to-zoom 捏合缩放（1.0x ~ 4.0x）
 * 3. 管理放大状态下的单指拖动画布，并提供边界物理阻尼约束（clampOffset）
 * 4. 当 scale == 1.0f 时彻底释放滑动事件，防止与外层 LazyColumn / HorizontalPager 冲突
 */
class ReaderZoomState {
    var scale: Float by mutableFloatStateOf(1f)
        private set
    var offsetX: Float by mutableFloatStateOf(0f)
        private set
    var offsetY: Float by mutableFloatStateOf(0f)
        private set
    var viewportSize: IntSize by mutableStateOf(IntSize.Zero)

    private var animationJob: Job? = null

    val isZoomed: Boolean
        get() = scale > 1.05f

    fun reset(scope: CoroutineScope? = null, animated: Boolean = false) {
        animationJob?.cancel()
        if (animated && scope != null && isZoomed) {
            animateTo(targetScale = 1f, targetOffsetX = 0f, targetOffsetY = 0f, scope = scope)
        } else {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    fun animateTo(
        targetScale: Float,
        targetOffsetX: Float = 0f,
        targetOffsetY: Float = 0f,
        scope: CoroutineScope
    ) {
        animationJob?.cancel()
        val startScale = scale
        val startOffsetX = offsetX
        val startOffsetY = offsetY
        animationJob = scope.launch {
            Animatable(0f).animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            ) {
                scale = lerpFloat(startScale, targetScale, value)
                offsetX = lerpFloat(startOffsetX, targetOffsetX, value)
                offsetY = lerpFloat(startOffsetY, targetOffsetY, value)
            }
            scale = targetScale
            offsetX = targetOffsetX
            offsetY = targetOffsetY
            animationJob = null
        }
    }

    fun clampOffset(scale: Float, offsetX: Float, offsetY: Float): Offset {
        if (scale <= 1f || viewportSize == IntSize.Zero) return Offset.Zero
        val maxOffsetX = (viewportSize.width * (scale - 1f) / 2f).coerceAtLeast(0f)
        val maxOffsetY = (viewportSize.height * (scale - 1f) / 2f).coerceAtLeast(0f)
        return Offset(
            x = offsetX.coerceIn(-maxOffsetX, maxOffsetX),
            y = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    fun update(zoomChange: Float, panChange: Offset) {
        animationJob?.cancel()
        animationJob = null
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        if (newScale <= 1.01f) {
            reset()
        } else {
            val scaleRatio = newScale / scale
            scale = newScale
            val clamped = clampOffset(
                scale = newScale,
                offsetX = (offsetX + panChange.x) * scaleRatio,
                offsetY = (offsetY + panChange.y) * scaleRatio
            )
            offsetX = clamped.x
            offsetY = clamped.y
        }
    }

    private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
        return start + fraction * (stop - start)
    }
}

@Composable
fun rememberReaderZoomState(vararg keys: Any?): ReaderZoomState {
    return remember(*keys) { ReaderZoomState() }
}
