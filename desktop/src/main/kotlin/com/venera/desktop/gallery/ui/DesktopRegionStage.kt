package com.venera.desktop.gallery.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.venera.desktop.gallery.data.DesktopContentRect
import com.venera.desktop.gallery.data.DesktopRegionDecoder
import com.venera.desktop.gallery.data.DesktopRegionGain
import com.venera.desktop.gallery.data.DesktopVisibleRegion
import com.venera.desktop.gallery.data.desktopCoilBytes
import com.venera.desktop.gallery.data.desktopRegionBytesField
import com.venera.desktop.gallery.data.destinationRect
import net.engawapg.lib.zoomable.ZoomState
import kotlin.math.roundToInt

internal const val desktopRegionOverlayLands = true

/**
 * 起手那一句等停（毫秒）。取这一档的理由：拖一下平移时手势每帧都在改偏移量，
 * 而一次区域解码要几毫秒到几十毫秒 —— 90 毫秒比一帧长、比"用户觉得卡"短，
 * 且底下那张一直在屏上，等停那一段不是空白。
 */
private const val SETTLE_MILLIS = 90L

/**
 * 纯像素摆放。偏移那一枚本来就是 `IntOffset`（px），尺寸若改走 dp，
 * 在 Windows 的显示缩放档上两层单位不一致就会错位 —— 图证第一版抓到的正是这个。
 */
private fun Modifier.pxSize(w: Int, h: Int): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/** 解出来的一块与它该落在屏上的哪个矩形 —— 成对递到渲染那一格，不许两处各算一遍。 */
internal class DesktopPlacedRegion(val bitmap: ImageBitmap, val rect: DesktopContentRect)
