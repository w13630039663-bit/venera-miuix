package com.venera.compose.reader

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.absoluteValue
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.venera.compose.ui.tokens.StatusColors

/**
 * 翻页特效的纯渲染层（与业务逻辑解耦）：
 * - [pageTurn3D]：3D 翻书，绕 Y 轴旋转 + 透视，套在 HorizontalPager 的页面容器上，由页面偏移驱动。
 * - [ReaderCurlPageItem]：仿真卷边，单页横向模式专用，把整页画成「从翻页边卷起、露出背面与落影」。
 *
 * 两种特效都只在「翻页过渡中」（`offsetFraction` 离开 0）才改变渲染；静止（`offsetFraction == 0`）
 * 恒等变换，与接入此功能之前逐像素一致 —— 这是默认 [ReaderPageTurnEffect.NONE] 之外的兜底保证。
 */

/**
 * 某页相对视口中心的偏移：0 = 静止居中，±1 = 完全翻走。
 * 自算而不依赖 `getOffsetFractionForPage`，避免 Compose 基础库版本差异。
 */
fun pagerOffset(state: PagerState, page: Int): Float =
    (page - state.currentPage).toFloat() - state.currentPageOffsetFraction

/**
 * 3D 翻书修饰符。
 *
 * @param offsetFraction 该页相对视口的偏移（[pagerOffset]）。
 * @param isRtl 是否从右至左翻页（翻页轴镜像）。
 */
fun Modifier.pageTurn3D(
    offsetFraction: Float,
    isRtl: Boolean,
): Modifier = graphicsLayer {
    val off = offsetFraction.coerceIn(-1f, 1f)
    if (off == 0f) return@graphicsLayer // 静止页 = 恒等变换，零回归
    val dir = if (isRtl) -1f else 1f
    // 翻离的页绕翻页边旋转；幅度随偏移增大。
    rotationY = -off * 30f * dir
    // 透视：cameraDistance 越大越平。默认 8*密度 ≈ View 的 8dp 透视。
    cameraDistance = 8f * density
    // 翻起的页略微缩小，强化纵深。
    val s = 1f - off.absoluteValue * 0.06f
    scaleX = s
    scaleY = s
    // 翻页轴落在「下一页所在的那条边」：LTR 从右缘翻、RTL 从左缘翻。
    transformOrigin = TransformOrigin(if (isRtl) 0f else 1f, 0.5f)
}

/**
 * 翻页边的一道阴影，强化 3D 翻书的纵深感。只在过渡中、且偏移足够大时才画，静止时不画。
 */
@Composable
private fun PageEdgeShadow(alpha: Float, isRtl: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val band = w * 0.32f
        val brush = if (isRtl) {
            Brush.horizontalGradient(
                colorStops = arrayOf(0f to Color.Black.copy(alpha), 1f to Color.Black.copy(alpha = 0f)),
                startX = 0f,
                endX = band,
            )
        } else {
            Brush.horizontalGradient(
                colorStops = arrayOf(0f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha)),
                startX = w - band,
                endX = w,
            )
        }
        drawRect(brush, size = size)
    }
}

/**
 * 把页面内容包进翻页容器：按当前特效决定怎么包。
 *
 * - [ReaderPageTurnEffect.NONE]：原样（恒等）。
 * - [ReaderPageTurnEffect.BOOK_FLIP_3D]：容器绕 Y 轴旋转，并在翻页边叠一道阴影。
 * - [ReaderPageTurnEffect.CURL]：容器不旋转（卷边由内部 [ReaderCurlPageItem] 自己画），
 *   所以这里只是个占位 Box。
 */
@Composable
internal fun ReaderPageTurnBox(
    effect: ReaderPageTurnEffect,
    offsetFraction: Float,
    isRtl: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when (effect) {
        ReaderPageTurnEffect.NONE -> Box(modifier.fillMaxSize()) { content() }
        ReaderPageTurnEffect.BOOK_FLIP_3D -> {
            val off = offsetFraction.coerceIn(-1f, 1f)
            Box(modifier.fillMaxSize()) {
                Box(modifier.fillMaxSize().pageTurn3D(off, isRtl)) { content() }
                if (off.absoluteValue > 0.02f) {
                    PageEdgeShadow(alpha = off.absoluteValue * 0.45f, isRtl = isRtl)
                }
            }
        }
        ReaderPageTurnEffect.CURL -> Box(modifier.fillMaxSize()) { content() }
    }
}

/**
 * 仿真卷边页面项（单页横向模式专用）。
 *
 * 直接把整页画进 Canvas，按翻页进度把「翻页边那一条」画成书页背面（渐变压暗）+ 折痕暗线，
 * 模拟纸张卷起。代价：该模式不走 telephoto 缩放（卷边与多指缩放手势天然互斥），
 * 需要在设置页说明。图片走 Coil 同款缓存键，命中磁盘/内存缓存，不会重复下载。
 *
 * @param turnProgress 0 = 平铺，1 = 翻完。由调用方用 `|pagerOffset|` 喂入。
 */
@Composable
internal fun ReaderCurlPageItem(
    page: ComicPageSource,
    index: Int,
    colorFilter: ColorFilter?,
    isRtl: Boolean,
    turnProgress: () -> Float,
) {
    val context = LocalContext.current
    val model: Any? = when (page) {
        is ComicPageSource.Network -> page.url
        is ComicPageSource.DynamicNetwork -> rememberDynamicPageResolution(page).url
        is ComicPageSource.LocalFile -> page.file
        is ComicPageSource.ZipEntry -> null
    }
    val cacheKey = if (page is ComicPageSource.DynamicNetwork) page.cacheKey else null
    var bitmap by remember(model) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(model) { mutableStateOf(false) }

    // 复用 Coil 管线取位图（与 VeneraReaderScreen.saveCurrentImage 同手法），allowHardware(false) 才能读像素。
    LaunchedEffect(model, cacheKey) {
        bitmap = null
        failed = false
        if (model == null) {
            failed = true
            return@LaunchedEffect
        }
        val req = ImageRequest.Builder(context)
            .data(model)
            .allowHardware(false)
            .apply { if (cacheKey != null) memoryCacheKey(cacheKey).diskCacheKey(cacheKey) }
            .build()
        when (val res = context.imageLoader.execute(req)) {
            is SuccessResult -> {
                val bm = (res.image as? BitmapImage)?.bitmap
                    ?: (res.image as? BitmapDrawable)?.bitmap
                bitmap = bm?.asImageBitmap()
                if (bm == null) failed = true
            }
            else -> failed = true
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            when {
                bitmap != null -> drawCurlPage(bitmap!!, turnProgress().coerceIn(0f, 1f), isRtl, colorFilter)
                else -> drawRect(StatusColors.BadgeSurface, size = size)
            }
        }
    }
}

/**
 * 卷边绘制：前景（未卷起部分）+ 背面（渐变压暗）+ 折痕暗线。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCurlPage(
    bitmap: ImageBitmap,
    progress: Float,
    isRtl: Boolean,
    colorFilter: ColorFilter?,
) {
    val W = size.width
    val H = size.height
    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    val fit = minOf(W / bw, H / bh)
    val dw = bw * fit
    val dh = bh * fit
    val ox = (W - dw) / 2f
    val oy = (H - dh) / 2f
    val oxI = ox.toInt()
    val oyI = oy.toInt()
    val dwI = dw.toInt()
    val dhI = dh.toInt()

    // 卷起宽度：progress 0→1 时从 0 长到半屏。
    val fold = (progress * W * 0.5f).coerceAtLeast(0f)
    if (fold < 0.5f) {
        // 基本平铺（含极小卷起）：直接画整图，与静止态逐像素一致。
        drawImage(bitmap, dstOffset = IntOffset(oxI, oyI), dstSize = IntSize(dwI, dhI), colorFilter = colorFilter)
        return
    }

    val frontLeft = if (isRtl) ox + fold else ox
    val frontRight = if (isRtl) ox + dw else ox + dw - fold

    // 1) 未卷起的前景（保留真实画面），按区域裁剪。
    clipRect(frontLeft, oy, frontRight, oy + dh, ClipOp.Intersect) {
        drawImage(bitmap, dstOffset = IntOffset(oxI, oyI), dstSize = IntSize(dwI, dhI), colorFilter = colorFilter)
    }

    // 2) 卷起部分 = 书页背面：沿折叠方向由亮到暗的渐变，模拟纸张卷起后的受光。
    val backLeft = if (isRtl) ox else ox + dw - fold
    val backRight = if (isRtl) ox + fold else ox + dw
    if (backRight > backLeft) {
        val stops = if (isRtl) {
            arrayOf(0f to Color.Black.copy(alpha = 0.8f), 1f to Color.Black.copy(alpha = 0.2f))
        } else {
            arrayOf(0f to Color.Black.copy(alpha = 0.2f), 1f to Color.Black.copy(alpha = 0.8f))
        }
        drawRect(
            Brush.horizontalGradient(colorStops = stops, startX = backLeft, endX = backRight),
            topLeft = Offset(backLeft, oy),
            size = Size(backRight - backLeft, dh),
        )
    }

    // 3) 折痕暗线。
    val crease = if (isRtl) frontLeft else frontRight
    drawLine(
        Color.Black.copy(alpha = 0.85f),
        Offset(crease, oy),
        Offset(crease, oy + dh),
        strokeWidth = (2f * density).coerceAtLeast(1f),
    )
}
