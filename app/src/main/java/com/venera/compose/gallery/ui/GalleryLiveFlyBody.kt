package com.venera.compose.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.BitmapImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.SuccessResult
import com.venera.compose.gallery.domain.flightFrame
import kotlin.math.roundToInt

/**
 * 活画面那一枚位图（**组合期**取；没到手交 null）。
 *
 * 为什么单独一颗、而不是藏在飞行体里：调用侧要靠它决定"这一趟走活画面还是截图族"。
 * 藏起来的话，调用侧只能按"URL 非空"猜 —— 而那正是 2026-10-20 真机那一格泛白的病根：
 * 截图族按"活画面会接管"让开，而活画面这边图还没到手也什么都没画 ⇒ **两条路都不画，屏上空的**。
 * 现在把这份判据交出去：拿得到位图才是"活画面接管"，拿不到就老实走截图族。
 *
 * 口径同 `WatercolorPainterScreen:120`（coil3 的真位图在 `SuccessResult.image` 里，
 * 且只有 `BitmapImage` 有 `.bitmap`）。
 */
@Composable
internal fun rememberLiveFlyBitmap(coverUrl: String): ImageBitmap? {
    if (coverUrl.isBlank()) return null
    val painter = rememberAsyncImagePainter(model = coverUrl)
    return (painter.state as? SuccessResult)
        ?.let { it.image as? BitmapImage }
        ?.bitmap
        ?.asImageBitmap()
}

/**
 * **活画面飞行体**（批 B①，2026-10-19）：飞的不再是一帧截图，而是**与墙上那张卡同一颗 URL
 * 解析件**的封面（pixez 那条路，§4.3 第 1 条的对照物）。
 *
 * ## 为什么不再是截图（这一族要治的三件事）
 *
 * 1. **两帧交接的"贴错再撕"**：截图是**起点那一刻**的像素，落点是终点那一刻的画面。
 *    截图族只能在两者之间二选一地淡（于是有了 `handoffBodyAlpha` / `exitBodyAlpha` 两条补丁曲线）。
 *    活画面是**同一份画**从卡片形变到落点形 —— 没有"两份"，也就不需要任何淡出补丁。
 * 2. **白角**（2026-10-16 用户报）：截图截的是屏上像素，封面四角露的卡片色被一起带进来。
 *    活画面自己按封面档裁圆角，四角从来就不属于图。
 * 3. **慢网首开是骨架**：截图只在"墙卡已经画出来"时才有；活画面与墙卡同 URL ⇒
 *    墙卡画出来了它就画得出来（⑥ 的种子那一格同理）。
 *
 * ## 与截图族的分工（**不**替换 `GalleryEntryFlyBody` / `GalleryExitFlyBody`）
 *
 * 两颗截图件仍服务**离场**与**没有 URL 可用的深链**（那一族没有墙卡，只能截屏）。
 * 这一颗只服务**进场**、且调用方手里有那个 URL 时 —— 所以它是**加**不是**换**：
 * 截图族的历史读数（白角、obstructed、让位两条复位路）都还钉着，不许一起洗掉。
 *
 * ## 批 B④⑤：为什么这一颗**没有** alpha 形参
 *
 * 计划里 ④ 是"一标量两层"、⑤ 是"淡的对象换成源卡"。活画面这一族把两件一起收掉了：
 *
 * - **④**：从前两条钟（`entryFly` 几何 + `entryHandoff` 交棒）之外还有一颗给飞行体的淡出
 *   （`handoffBodyAlpha`）。活画面是**同一份画**从卡片形变到落点形 —— 没有"两份"同时可见，
 *   也就没有可淡的东西。于是飞行体那一侧只剩几何一颗标量，淡出补丁整条不用上。
 * - **⑤**："淡源卡"在这一屏**够不着**：墙卡住在另一个窗口（MainActivity），而本仓没有
 *   跨窗口共享元素能力（翻了 SDK 33~36 的 `android.window`，没有
 *   `SplashScreenViewProvider`、也没有 `overrideNextTransitionSplashScreenStartingPoint`，
 *   理由逐字见 `GalleryFlyIn` 头注）。进场这一侧能做的全部就是"不淡飞行体"——
 *   同一份画不需要淡，观感同"源卡被这一格吸走"。
 *
 * ⚠️ **离场那一侧的两件事都还留着**：`GalleryExitFlyBody` 仍带 `exitBodyAlpha`，
 * 因为离场仍是截图族（②③ 那一族：飞行体带**一页图块**缩回小卡，页块与卡封面不是同一张画），
 * 末段不淡就是"贴错了再撕下来"。谁想把那一笔也删掉，先得把离场也换成活画面 —— 那是另一刀。
 *
 * ## 形状：组合期取位图，绘制期只做几何
 *
 * 为什么不放一颗 `AsyncImage` 再挂 `graphicsLayer`：那条路要让"层"每帧变成 dst 那么大，
 * 而层的 size 由布局定（`graphicsLayer` 里改不了）⇒ 只能反过来"把内容逆变换到层里"，
 * 于是层的基准变成"落点那一格"—— 可落点是**晚到**的那一样，基准会中途改道，
 * 逆变换的锚点跟着漂（这正是 `flightFrame` 用"逐边插值"要避免的那类漂）。
 *
 * 所以走 `drawWithCache`：位图在**组合期**从 painter 的 Success 态拿出来（口径同
 * `WatercolorPainterScreen` 那一处），几何在**绘制期**每帧按 [flightFrame] 算。
 * 缓存块按 `bitmap` 与两档圆角做键 ⇒ 位图一到手只重算一次，之后每帧只走 `onDrawBehind`，
 * **不重组**（动画值走组合期参数就会每帧拖重组，本仓为这一格栽过）。
 *
 * @param progress 0 = 与墙上那张卡逐像素相同，1 = 与落点那一格逐像素相同。
 *   **传 lambda 不传值**：动画值在绘制期每帧现取，只失效这一层。
 * @param coverUrl 墙上那张卡的封面地址。**必须与墙卡同源**（同一颗 URL ⇒ 同一个缓存项 ⇒
 *   首帧就有像素）；空串或图还没到手 = 这一层不画（调用方那一边有截图族垫着，不空）。
 * @param from 墙上那一格在窗口里的矩形（**没裁过**那一把，批 B §3 地基）。
 * @param to 落点那一格。**lambda**：落点是晚到的那一样，每帧现取。
 * @param imageRatio 那张封面的宽高比（宽/高）。墙卡与落点两端都按它构图，
 *   所以由调用侧量一次递进来（`cardRatio`，列表腿与详情腿同源），不在飞行体里猜。
 * @param cardCornerRadiusPx 起点那一档圆角（**屏幕像素**，组合期算好递进来）。
 * @param toCornerRadiusPx 终点那一档圆角（屏幕像素）。
 */
@Composable
internal fun GalleryLiveFlyBody(
    progress: () -> Float,
    bitmap: ImageBitmap?,
    from: Rect,
    to: () -> Rect,
    imageRatio: Float,
    modifier: Modifier = Modifier,
    cardCornerRadiusPx: Float = 0f,
    toCornerRadiusPx: Float = 0f,
) {
    if (bitmap == null || imageRatio <= 0f) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                // 缓存块按位图与两档圆角做键：位图一到手只重算一次，之后每帧只重绘。
                // imgW/imgH 是判据层那一颗的"逻辑位图"尺寸 —— 它内部只用宽高比算 cover 窗，
                // 绝对尺寸不参与，所以这里按 1000 为基准、高由 imageRatio 反推。
                val imgW = 1000
                val imgH = (1000f / imageRatio).roundToInt().coerceAtLeast(1)
                onDrawBehind {
                    val p = progress()
                    val t = to()
                    val frame = flightFrame(
                        expansion = p,
                        card = from.toFlyRect(),
                        to = t.toFlyRect(),
                        imageWidth = imgW,
                        imageHeight = imgH,
                        cardCornerRadiusPx = cardCornerRadiusPx,
                        toCornerRadiusPx = toCornerRadiusPx,
                    )
                    val dst = frame.dst
                    // 圆角 = 判据层那一枚（两档插值 + 逆补偿），路径裁掉溢出那一圈。
                    clipPath(
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = dst.left,
                                    top = dst.top,
                                    right = dst.left + dst.width,
                                    bottom = dst.top + dst.height,
                                    cornerRadius = CornerRadius(frame.clipRadius, frame.clipRadius),
                                ),
                            )
                        },
                    ) {
                        drawLiveFrame(bitmap, frame, dst, imgW, imgH)
                    }
                }
            },
    )
}

/**
 * 把活画面那一枚位图按当帧 [frame] 画进 dst。
 *
 * 拆出来是为了让 `clipPath` 块里只剩一句：几何与取源都在这里，
 * 与两颗截图件（`GalleryEntryFlyBody` / `GalleryExitFlyBody`）逐段对照时能一对一看齐。
 *
 * @param logicalWidth 判据层那一颗的"逻辑位图"宽（调用处的 `imgW` = 1000）。
 * @param logicalHeight 同上，高（= `1000 / imageRatio`）。
 */
private fun DrawScope.drawLiveFrame(
    bitmap: ImageBitmap,
    frame: com.venera.compose.gallery.domain.FlightFrame,
    dst: com.venera.compose.gallery.domain.FlyRect,
    logicalWidth: Int,
    logicalHeight: Int,
) {
    // 判据层给的是**图像空间**的浮点窗（相对那张逻辑位图），而手里是真实位图 ——
    // 按同一比例换算过去（窗的坐标都是 0..逻辑宽高 的归一值）。
    val sx = bitmap.width / logicalWidth.toFloat()
    val sy = bitmap.height / logicalHeight.toFloat()
    val srcLeft = (frame.window.left * sx).roundToInt().coerceIn(0, bitmap.width - 1)
    val srcTop = (frame.window.top * sy).roundToInt().coerceIn(0, bitmap.height - 1)
    val srcW = (frame.window.width * sx).roundToInt().coerceIn(1, bitmap.width - srcLeft)
    val srcH = (frame.window.height * sy).roundToInt().coerceIn(1, bitmap.height - srcTop)
    drawImage(
        image = bitmap,
        srcOffset = IntOffset(srcLeft, srcTop),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
        dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt()),
    )
}
