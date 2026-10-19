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
import androidx.compose.ui.unit.lerp
import com.venera.compose.gallery.domain.exitExpansion
import kotlin.math.roundToInt

/**
 * **离场飞行体**：把"屏上那一帧"画成"飞回卡片那一格"（2026-10-19 抽成共享件）。
 *
 * ## 为什么抽
 *
 * pixiv 作品页要接返回程，而这段绘制原本**只长在** `GalleryPostScreen` 里（那 55 行
 * `drawBehind` 埋在极深的组合里），2026-10-19 上午又被抄进了作品页进场与播放器进场 ——
 * 同一个动作"把一块位图画进一个从 A 框变到 B 框的圆角矩形"共有四份抄本。各写一遍不行：
 * 飞行体只要有一处算式不同，同一部作品从这面墙点进去和从那一面墙点进去就会**飞得不一样**，
 * 而那种错只有真机能看见。
 *
 * 所以这里留**一份渲染**，算式继续吃 [com.venera.compose.gallery.domain.exitExpansion]
 * （那颗纯判据早就有单测逐位钉着）。进场那一趟见 [GalleryEntryFlyBody] —— 两颗件同一包、
 * 同一套形参形状，几何共用同一颗判据（`exitExpansion` 就是"逐边插值 + cover 取源"，
 * 方向无关，它的名字是历史包袱），只有圆角那一档各按自己的位图模型走（见两颗件各自的头注）。
 *
 * ## 三件输入缺一不可
 *
 * - [from]：起点，屏上那一帧在窗口里的框；
 * - [to]：终点，墙上那张卡封面那一块（由一级页面按 `viewerKey` 回写）。它是**lambda**而不是
 *   值：落点是晚到的那一样（等墙里那一格量出来），冻在组合期就会把"没量到那一帧"的画法
 *   一路带到飞完。每帧现取，量到的那一刻下一帧就改道。
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
 * @param progress 0 = 还在起点，1 = 已落到卡片那一格。**传 lambda 不传值**：动画值在
 *   `drawBehind` 里每帧现取，只重绘这一块；当 `Float` 实参递就等于把动画值读回组合期，
 *   每帧重组这一层（2026-10-19 批 A 之前的形状，那一条把较好的绘制期取值降级成了较差的）。
 * @param alpha 飞行体自己的不透明度。默认 1 = 整程不透明（老口径）。调用方若要"末段化进落点"
 *   就传 `{ exitBodyAlpha(exitFly.value) }` —— 见 [com.venera.compose.gallery.domain.exitBodyAlpha] 头注。
 */
@Composable
internal fun GalleryExitFlyBody(
    progress: () -> Float,
    from: Rect,
    to: () -> Rect,
    frame: ImageBitmap,
    flyCornerRadius: Dp,
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
                // 圆角随路程在"画面框那一档"与"卡封面那一档"之间插值。
                val radius = lerp(
                    flyCornerRadius,
                    coverCornerRadius,
                    p.coerceIn(0f, 1f),
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
                        alpha = bodyAlpha,
                    )
                }
            },
    )
}

/**
 * `Rect` → 判据层的 `FlyRect`。
 *
 * 全仓**只此一份**（`internal`，进出两颗飞行体共用）：从前 `GalleryPostScreen` 里还有一颗
 * 同形 private，各留一份是"那颗文件正被别人频繁改、不想互相等"；批 A 把播放器那两趟也换成
 * 共享件之后两份抄本并成一份，「两边同形由尺子钉着」那句话由本函数自身成立，不再需要尺子。
 */
internal fun Rect.toFlyRect() = com.venera.compose.gallery.domain.FlyRect(
    left = left,
    top = top,
    width = width,
    height = height,
)
