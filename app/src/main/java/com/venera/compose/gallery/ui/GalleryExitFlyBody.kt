package com.venera.compose.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.lerp
import com.venera.compose.gallery.domain.exitExpansion
import kotlin.math.roundToInt

/**
 * **离场飞行体**：把"屏上那一帧"画成"飞回卡片那一格"（2026-10-19 抽成共享件）。
 *
 * ## 为什么抽
 *
 * pixiv 作品页要接返回程，而这段绘制原本**只长在** `GalleryPostScreen` 里（那 55 行
 * `drawBehind` 埋在极深的组合里）。两处各写一遍不行：飞行体只要有一处算式不同，
 * 同一部作品从这面墙点进去和从那一面墙点进去就会**飞得不一样**，而那种错只有真机能看见。
 *
 * 所以这里留**一份渲染**，算式继续吃 [com.venera.compose.gallery.domain.exitExpansion]
 * （那颗纯判据早就有单测逐位钉着）。`GalleryPostScreen` 那一处暂未替换成这一颗 ——
 * 它在 2219 行的屏里、回归面大，本轮只先让作品页用上（见 §8 的交付说明）。
 *
 * ## 三件输入缺一不可
 *
 * - [from]：起点，屏上那一帧在窗口里的框；
 * - [to]：终点，墙上那张卡封面那一块（由一级页面按 `viewerKey` 回写）；
 * - [frame]：**像素**，起点那一帧的截图（`GalleryFlyIn.capture` 得来）。
 *
 * 缺任何一件就**什么都不画**：让调用方老实 finish，不许把"飞不起来"演成"飞完了"。
 *
 * ## 圆角为什么随路程换
 *
 * 起点是画面框（`flyCornerRadius` = `shape.large`），终点是**卡里那张封面**
 * （`coverCornerRadius` = `shape.medium`，见 `veneraCoverCornerRadius`）。两处都用大圆角的话，
 * 落回卡片时飞行体的角比封面圆 4dp ⇒ "贴上去"那一帧在卡片四角盖着一小块**不属于卡片**的图，
 * 飞行体一撤、卡片自己的四角才补上来 —— 一次角上的补白（2026-10-16 真机读数）。
 *
 * 进度插值与"哪一档圆角"是**正交**的两件事：前者管"同一比例缩到多大"（`cornerRadiusScale`），
 * 后者管"随路程换用哪一档"，所以两处相乘而不是二选一。
 *
 * @param progress 0 = 还在起点，1 = 已落到卡片那一格。
 * @param alpha 飞行体自己的不透明度。默认 1 = 整程不透明（老口径）。调用方若要"末段化进落点"
 *   就传 `exitBodyAlpha(progress)` —— 见 [com.venera.compose.gallery.domain.exitBodyAlpha] 头注。
 */
@Composable
internal fun GalleryExitFlyBody(
    progress: Float,
    from: androidx.compose.ui.geometry.Rect,
    to: androidx.compose.ui.geometry.Rect,
    frame: ImageBitmap,
    flyCornerRadius: Dp,
    coverCornerRadius: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val computed = exitExpansion(
                    progress = progress,
                    from = from.toFlyRect(),
                    to = to.toFlyRect(),
                    sourceWidthPx = frame.width,
                    sourceHeightPx = frame.height,
                )
                val dst = computed.dst
                val src = computed.source
                // 圆角随路程在"画面框那一档"与"卡封面那一档"之间插值。
                val radius = lerp(
                    flyCornerRadius,
                    coverCornerRadius,
                    progress.coerceIn(0f, 1f),
                ).toPx() * computed.cornerRadiusScale
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
                        alpha = alpha,
                    )
                }
            },
    )
}

/**
 * `Rect` → 判据层的 `FlyRect`。
 *
 * 与 `GalleryPostScreen` 里那颗**同形**：那是 `private`，而且它所在的那颗文件正被别人频繁改，
 * 所以这里各留一份转换（纯数据搬运，没有判断）。两边同形由
 * `GalleryExitFlyBodyTest` 钉着 —— 一处漂了当场红。
 */
private fun androidx.compose.ui.geometry.Rect.toFlyRect() = com.venera.compose.gallery.domain.FlyRect(
    left = left,
    top = top,
    width = width,
    height = height,
)
