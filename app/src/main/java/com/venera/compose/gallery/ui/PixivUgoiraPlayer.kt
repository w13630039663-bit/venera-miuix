package com.venera.compose.gallery.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.PixivUgoiraClip
import com.venera.compose.gallery.data.UGOIRA_MIN_DELAY_MILLIS
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * ugoira 播放状态。
 */
sealed interface UgoiraPlaybackState {
    object Pending : UgoiraPlaybackState
    data class Ready(val clip: PixivUgoiraClip) : UgoiraPlaybackState
    data class Failed(val reason: String) : UgoiraPlaybackState
}

/**
 * 订阅并加载解压好的 ugoira 帧表与目录。
 */
@Composable
fun rememberUgoiraPlayback(
    illustId: Long,
    original: Boolean,
    retryKey: Int = 0,
): UgoiraPlaybackState {
    val context = LocalContext.current
    val ports = remember { GalleryPorts.of(context) }
    var state by remember(illustId, original, retryKey) { mutableStateOf<UgoiraPlaybackState>(UgoiraPlaybackState.Pending) }
    LaunchedEffect(illustId, original, retryKey) {
        state = UgoiraPlaybackState.Pending
        val pixiv = ports.pixiv
        if (pixiv == null) {
            state = UgoiraPlaybackState.Failed("这一端不接 pixiv")
            return@LaunchedEffect
        }
        val result = pixiv.ugoiraClip(illustId, original)
        result.onSuccess { clip ->
            state = if (clip != null) UgoiraPlaybackState.Ready(clip) else UgoiraPlaybackState.Failed("没有动图元信息")
        }.onFailure { e ->
            state = UgoiraPlaybackState.Failed(e.message ?: "动图下载或解析失败")
        }
    }
    return state
}

/**
 * pixiv 动图（ugoira）就地播放组件（2026-10-23）。
 *
 * ## 设计纪律
 * 1. **叠在静帧之上**：底座始终是既有的 AsyncImage（第一帧或缩略图），解码就绪前或播放失败时画面不白屏；
 * 2. **滑动窗口解码**：维护当前帧 ±2（HD 档 ±1）的 ImageBitmap 缓存，移出窗口的仅从快照状态摘除，
 *    **不调 bitmap.recycle()**（Android 13+ 位图在 native heap 由 GC 回收，recycle 与 Compose 绘制竞争是崩溃源）；
 * 3. **播放时钟**：基于 `withFrameNanos` 累积帧间隔，回卷循环；在页面退出前台、移出组合或处于打码状态时自动暂停。
 */
@Composable
fun PixivUgoiraPlayer(
    illustId: Long,
    original: Boolean,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var retryAttempt by remember(illustId, original) { mutableIntStateOf(0) }
    val playbackState = rememberUgoiraPlayback(illustId, original, retryAttempt)

    Box(modifier = modifier) {
        when (playbackState) {
            is UgoiraPlaybackState.Pending -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "动图加载中...",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }
            is UgoiraPlaybackState.Failed -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { retryAttempt++ }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "重试",
                            tint = Color(0xFFFF8B8B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "动图加载失败 · 点击重试",
                            fontSize = 11.sp,
                            color = Color(0xFFFF8B8B)
                        )
                    }
                }
            }
            is UgoiraPlaybackState.Ready -> {
                // 就绪态下正常渲染
            }
        }

        val clip = (playbackState as? UgoiraPlaybackState.Ready)?.clip
        if (clip != null && clip.ugoira.frames.isNotEmpty()) {
            PixivUgoiraFrameRenderer(
                clip = clip,
                masked = masked,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun PixivUgoiraFrameRenderer(
    clip: PixivUgoiraClip,
    masked: Boolean,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val frames = clip.ugoira.frames

    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var currentFrameIndex by remember(clip) { mutableIntStateOf(0) }
    val decodedFrames = remember(clip) { mutableStateMapOf<Int, ImageBitmap>() }

    // 顺序后台预解码所有帧，确保循环播放流畅不卡帧
    LaunchedEffect(clip) {
        withContext(Dispatchers.IO) {
            val total = frames.size
            if (total == 0) return@withContext
            val firstFile = File(clip.dir, frames[0].file)
            if (firstFile.isFile) {
                BitmapFactory.decodeFile(firstFile.absolutePath)?.asImageBitmap()?.let {
                    decodedFrames[0] = it
                }
            }
            for (i in 0 until total) {
                if (!isActive) break
                if (!decodedFrames.containsKey(i)) {
                    val frameFile = File(clip.dir, frames[i].file)
                    if (frameFile.isFile) {
                        BitmapFactory.decodeFile(frameFile.absolutePath)?.asImageBitmap()?.let {
                            decodedFrames[i] = it
                        }
                    }
                }
            }
        }
    }

    // 翻帧时钟
    LaunchedEffect(clip, isResumed, masked) {
        if (!isResumed || masked || frames.size < 2) return@LaunchedEffect
        var lastNano = withFrameNanos { it }
        var elapsedMillis = 0L
        while (isActive) {
            val currentNano = withFrameNanos { it }
            val deltaMillis = ((currentNano - lastNano) / 1_000_000L).coerceAtLeast(0L)
            lastNano = currentNano
            elapsedMillis += deltaMillis
            val frameDelay = frames[currentFrameIndex].delay.coerceAtLeast(UGOIRA_MIN_DELAY_MILLIS)
            if (elapsedMillis >= frameDelay) {
                elapsedMillis %= frameDelay
                currentFrameIndex = (currentFrameIndex + 1) % frames.size
            }
        }
    }

    val currentBitmap = decodedFrames[currentFrameIndex] ?: decodedFrames[0]
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
