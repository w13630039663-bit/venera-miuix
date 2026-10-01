package com.venera.compose.gallery.domain

import kotlin.math.roundToInt

/**
 * 飞行体绘制时，**源位图**里该取的那一块（居中裁切 = cover 语义）。
 *
 * ## 为什么需要这一层（2026-10-01 12:00 用户报"包裹还是会闪一下"）
 *
 * 飞行体的起点像素是"卡片里**封面那一块**"（见 `GalleryFlyIn` 文件头注），落点是"大图页按原图
 * 比例定出来的**画面框**"。两者的比例**并不总是同一档**：
 * - 墙上的卡把比例夹在 `0.4~2.5`（防一张横长条把整列撑出屏幕），原图比例超出这个范围就被夹过；
 * - 首页预览行那条横卡的封面是**固定 124×170**（`historyCardWidth × historyCoverHeight`），
 *   与图上真实比例无关。
 *
 * 老写法把源位图整张**拉伸**到落点框：比例一致时是对的，不一致时整块内容被拉扯变形，
 * 交棒那一刻形状又还回来 —— 读起来就是"闪"。
 * 改成 cover 语义之后，比例不一致也只是"裁掉一部分"（与卡上那张封面本来就是 Crop 显示同一口径），
 * 交棒读起来是"被裁掉的部分补回来"，而不是"整块换了形状"。
 * **比例一致时算出来的就是整张位图，与老写法逐像素等价** —— 这条由用例钉着。
 *
 * ## 为什么不放在 ui 包里
 *
 * 这段是纯粹算术，刻意不碰 Compose / Android 任何类型：调用侧（`GalleryPostScreen`）自己把它
 * 翻译成 `IntOffset` / `IntSize`。这样它能脱离 gradle 单跑（`_probe/l0/run-judgment-tests.sh`），
 * 手边没设备时它是这条几何唯一能跑的回归证据（与 `GallerySharedTransition` 同一口径）。
 */
data class FlySourceRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * 求 [imageWidth] × [imageHeight] 的位图按 cover 语义盖满 [dstWidth] × [dstHeight] 时该取的那一块。
 *
 * 退化输入（任一边拿不到面积）一律交回**整张**：这一层不做兜底阈值判断，
 * 调用侧本来就要把 `dstSize` `coerceAtLeast(1)`，这里再编一个"至少 1px 的裁切窗口"只会多一处真相。
 */
fun coverSourceRect(
    imageWidth: Int,
    imageHeight: Int,
    dstWidth: Float,
    dstHeight: Float,
): FlySourceRect {
    if (imageWidth <= 0 || imageHeight <= 0 || dstWidth <= 0f || dstHeight <= 0f) {
        return FlySourceRect(0, 0, imageWidth.coerceAtLeast(1), imageHeight.coerceAtLeast(1))
    }
    // 取两个方向里更"吃像素"的那个缩放：它保证源那一块放大之后能盖满框（cover 的定义）。
    // 另一个方向自然就多出来，居中裁掉。
    val scale = maxOf(dstWidth / imageWidth, dstHeight / imageHeight)
    val width = (dstWidth / scale).roundToInt().coerceIn(1, imageWidth)
    val height = (dstHeight / scale).roundToInt().coerceIn(1, imageHeight)
    return FlySourceRect(
        left = (imageWidth - width) / 2,
        top = (imageHeight - height) / 2,
        width = width,
        height = height,
    )
}
