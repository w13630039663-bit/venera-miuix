package com.venera.compose.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.venera.compose.gallery.domain.exitExpansion
import com.venera.compose.gallery.domain.payloadCornerRadiusPx
import kotlin.math.roundToInt

/**
 * **进场飞行体**：把"墙上那一格的封面像素"画成"飞向落点那一格"（2026-10-19 抽成共享件）。
 *
 * ## 为什么抽
 *
 * 这 55 行 `drawBehind` 原本有两份抄本：`GalleryPostScreen`（播放器进场）与
 * `PixivWorkScreen`（作品页进场）。两份各自算几何、各自算圆角，一旦漂一处，同一部作品
 * 从这面墙点进去和从那面墙点进去就**飞得不一样** —— 而这种错只有真机能看见，所以合成一份。
 * 离场那一趟是 [GalleryExitFlyBody]（它的 `from`/`to` 与这一颗同两块矩形，方向相反）。
 *
 * ## 与离场件同形不同档的那一处：圆角
 *
 * 两颗件的**几何完全同源**（同一颗 [com.venera.compose.gallery.domain.exitExpansion]
 * —— 它就是"逐边插值 + cover 取源"，方向无关，名字是历史包袱）。差的是圆角：
 * - 离场件装的是**整屏那一帧**，两头是"画面框档 → 卡封面档"，随路程插值再乘 `cornerRadiusScale`；
 * - **这一颗**装的是**卡片里封面那一块的屏幕像素**，位图自己的四角画的是卡片面色
 *   （封面被 `VeneraCover` 按 `veneraCoverCornerRadius()` 裁掉的那圈），所以半径必须按
 *   **位图自己那一档**算：`× dst / src`（[com.venera.compose.gallery.domain.payloadCornerRadiusPx]）。
 *   拿更松的画面框档去裁，等于是把那四枚白角原样放行 —— 2026-10-16 用户报的
 *   「飞行体把按压那层白一起带进来了」就是这条。
 *
 * 两条口径都有单测逐位钉着，**不许合并**：它们的分界是"位图里装的是什么"，不是风格偏好。
 *
 * ## 三件输入缺一不可
 *
 * - [from]：起点，点下去那一刻墙上那一格在窗口里的框（`GalleryFlyIn.capture` 那一格）；
 * - [to]：终点，落点那一格。它是 **lambda** 而不是值：落点是**晚到**的那一样（等详情接口、
 *   等那一格量出来），冻在组合期就把"没量到那一帧"的画法一路带到飞完。每帧现取，
 *   量到的那一刻下一帧就改道 —— 量到之前调用方通常把 progress 冻在 0 垫着（"垫着不空"）。
 * - [frame]：**像素**，起点那一格的截图。
 *
 * 缺任何一件就**什么都不画**：让调用方走它自己的退路，不许把"飞不起来"演成"飞完了"。
 *
 * @param progress 0 = 还在卡片那一格，1 = 已落到落点。**传 lambda 不传值**：动画值在
 *   `drawBehind` 里每帧现取，只重绘这一块；当 `Float` 实参递就等于把动画值读回组合期，
 *   每帧重组这一层。等待期调用方传 `{ 0f }` 即可垫在卡片原位（与点击前那一帧同位同像素）。
 * @param coverCornerRadius 卡封面那一档圆角。**组合期**读（`veneraCoverCornerRadius()` 是
 *   @Composable getter，绘制阶段读 compositionLocal 拿不到随主题变的值，本仓为这一格栽过），
 *   进来只当 `Dp`，`.toPx()` 在 DrawScope 里做。
 * @param alpha 飞行体自己的不透明度。默认 1 = 去程全程不淡（2026-10-01 那条"图先暗一下"
 *   的读数：弹簧的百分比不是路程，中途淡出会和身后的页面叠成重影）。要接交棒后半程的
 *   淡出就传 `{ handoffBodyAlpha(handoff.value) }`。
 */
@Composable
internal fun GalleryEntryFlyBody(
    progress: () -> Float,
    from: Rect,
    to: () -> Rect,
    frame: ImageBitmap,
    coverCornerRadius: Dp,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // 三颗 lambda 各取一次，同一帧内同一个值（不然几何与圆角可能差半帧）。
                val p = progress()
                val bodyAlpha = alpha()
                val computed = exitExpansion(
                    progress = p,
                    from = from.toFlyRect(),
                    to = to().toFlyRect(),
                    sourceWidthPx = frame.width,
                    sourceHeightPx = frame.height,
                )
                val dst = computed.dst
                val src = computed.source
                // 圆角按**位图自己那一档**算，源宽取**裁过之后**那块：比例不同档时圆角跟着放大，
                // 不会因为被裁而变软。判据见 [payloadCornerRadiusPx] 头注（2026-10-16 白角那条）。
                val radius = payloadCornerRadiusPx(
                    payloadCornerPx = coverCornerRadius.toPx(),
                    srcWidthPx = src.width,
                    dstWidthPx = dst.width,
                )
                clipPath(
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = dst.left,
                                top = dst.top,
                                right = dst.left + dst.width,
                                bottom = dst.top + dst.height,
                                cornerRadius = CornerRadius(radius, radius),
                            ),
                        )
                    },
                ) {
                    drawImage(
                        image = frame,
                        srcOffset = IntOffset(src.left, src.top),
                        srcSize = IntSize(src.width, src.height),
                        dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()),
                        dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt()),
                        alpha = bodyAlpha,
                    )
                }
            },
    )
}
