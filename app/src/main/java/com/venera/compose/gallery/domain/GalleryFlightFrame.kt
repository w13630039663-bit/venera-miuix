package com.venera.compose.gallery.domain

/**
 * 批 B 的核心算式：**一条 expansion 定飞行体的形**（活画面那一路，2026-10-19 计划 §5 批 B ①②③）。
 *
 * ## 它与 [exitExpansion] 的分工（为什么不是改它，而是新长一颗）
 *
 * [exitExpansion] 是"飞截图"时代的几何：**目标框每帧现算居中裁**（`coverSourceRect`），
 * 于是同一幅画在途中会随框的比例换裁法 —— §4.4 那一行写的"途中主体会漂"就是它。
 * 这一颗换成 pixez 的形状：**裁切窗 = 卡片窗与落点窗的逐边插值**，中间帧不再每帧重新解题，
 * 比例差异全部由"另一方向的溢出被裁"吸收，内容**从头到尾没被拉伸过**。
 *
 * ## 轴
 *
 * `expansion = 0` ⇒ 飞行体与**卡上那张封面**逐像素相同；`expansion = 1` ⇒ 与**落点真图**逐像素相同。
 * 与 pixez 的 `expansion` 同轴（0=卡片、1=落位）；与 [exitExpansion] 的 `progress` **反着**，
 * 离场把 `1 - progress` 喂进来即可 —— 倒着走回同一档出同一帧（有尺子钉着），
 * 所以进场 / 离场 / 跟手 / 下滑四条路可以共用这一颗，不需要第二套算式（§4.2 的"一标量四源"）。
 *
 * ## 刻意不碰 Compose / Android 任何类型
 *
 * 与 [GalleryFlyGeometry] / [GalleryExitExpansion] 同一条口径：纯算术，能脱 gradle 单跑
 * （`_probe/l0/run-judgment-tests.sh`）。调用侧（飞行体渲染件）自己把 [FlightFrame] 翻成
 * `IntOffset` / `IntSize`，画之前还要按 [clipRadius] 裁掉溢出那一圈。
 */
data class FlightFrame(
    /** 当帧屏上那一格（卡片框与落点框的逐边插值）。画布上的**裁剪框**就是它。 */
    val dst: FlyRect,
    /** 当帧取**图像空间**的哪一块（浮点像素，左上角 + 宽高）。 */
    val window: FlyRect,
    /**
     * 当帧**等比** scale：源那一块每 1px 画多大。
     * 取"更吃像素那一方向"（`maxOf(宽比, 高比)`）⇒ 保证盖满 [dst]，另一方向的溢出交给裁剪。
     * 只有一枚标量 ⇒ 内容不可能被拉伸，这是这一族的根。
     */
    val uniformScale: Float,
    /**
     * 当帧裁剪框的圆角（**屏幕像素**）。锚点按计划 §5 批 B③：两档 token 之间随 expansion 插值
     * （卡封面档 → 落点容器档），两端各自**恰好等于**该档 —— 不再吃 `dst.width / to.width` 那把
     * （旧锚在末段补白的那一条，§4.4 圆角那一行）。
     */
    val clipRadius: Float,
)

/**
 * 图像空间里"按 cover 语义盖满 [frameWidth]×[frameHeight] 该取的那一块"，**浮点版**。
 *
 * 与 [coverSourceRect] 同一条算式（连"取更吃像素那一个缩放、另一向居中裁"也同），只是不四舍五入：
 * 插值要在连续量上做，先取整会把两端档对不齐（1px 的缝在 0.75 的缩放档下会被读成落点错位）。
 * 退化输入交回**整张图**，与 [coverSourceRect] 同一口径。
 */
fun coverWindowRect(
    imageWidth: Int,
    imageHeight: Int,
    frameWidth: Float,
    frameHeight: Float,
): FlyRect {
    if (imageWidth <= 0 || imageHeight <= 0 || frameWidth <= 0f || frameHeight <= 0f) {
        return FlyRect(
            left = 0f,
            top = 0f,
            width = imageWidth.coerceAtLeast(1).toFloat(),
            height = imageHeight.coerceAtLeast(1).toFloat(),
        )
    }
    val s = maxOf(frameWidth / imageWidth, frameHeight / imageHeight)
    val w = frameWidth / s
    val h = frameHeight / s
    return FlyRect(
        left = (imageWidth - w) / 2f,
        top = (imageHeight - h) / 2f,
        width = w,
        height = h,
    )
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * 给定 [expansion]（0=卡片那一格，1=落到落点）求当帧的画法。
 *
 * 六件事逐条对着 §4.3「算式形状」那一段：
 * 1. [card]/[to] 逐边插值 ⇒ 当帧框；
 * 2. 裁切窗 = **卡片 cover 窗**与**落点 cover 窗**的逐边插值（不每帧重解 ⇒ 主体不漂）；
 * 3. 等比 scale 取两向里更吃像素那枚 ⇒ 恒盖满当帧框，溢出的那一向交给裁剪（不拉伸）；
 * 4. 居中偏置：溢出方向居中裁（与 `VeneraCover` 屏上 Crop 显示同口径 —— pixez 的 bias 规则锚的是
 *    它自己"页从顶开始读"的排版语义，我们的封面本来就是 center-crop，跟我们的档走，不跟它的注释走）；
 * 5. 圆角 = 两档 token 的插值（屏幕 px），两端各自精确落在该档；
 * 6. 退化输入（零面积、越界 expansion）**不产负尺寸**、钳进 0..1 不外推。
 *
 * @param cardCornerRadiusPx 卡封面那一档圆角（屏幕 px，起点档用）。
 * @param toCornerRadiusPx 落点容器那一档圆角（屏幕 px，终点档用）。
 */
fun flightFrame(
    expansion: Float,
    card: FlyRect,
    to: FlyRect,
    imageWidth: Int,
    imageHeight: Int,
    cardCornerRadiusPx: Float,
    toCornerRadiusPx: Float,
): FlightFrame {
    val t = expansion.coerceIn(0f, 1f)
    val dst = FlyRect(
        left = lerp(card.left, to.left, t),
        top = lerp(card.top, to.top, t),
        width = lerp(card.width, to.width, t),
        height = lerp(card.height, to.height, t),
    )
    val windowCard = coverWindowRect(imageWidth, imageHeight, card.width, card.height)
    val windowTo = coverWindowRect(imageWidth, imageHeight, to.width, to.height)
    val window = FlyRect(
        left = lerp(windowCard.left, windowTo.left, t),
        top = lerp(windowCard.top, windowTo.top, t),
        width = lerp(windowCard.width, windowTo.width, t),
        height = lerp(windowCard.height, windowTo.height, t),
    )
    // 等比 scale：更吃像素那一方向。窗退化（零面积）时钳到 1，不产 NaN、不产负。
    val safeW = window.width.coerceAtLeast(1e-6f)
    val safeH = window.height.coerceAtLeast(1e-6f)
    val scale = maxOf(dst.width.coerceAtLeast(0f) / safeW, dst.height.coerceAtLeast(0f) / safeH)
    val radius = lerp(cardCornerRadiusPx.coerceAtLeast(0f), toCornerRadiusPx.coerceAtLeast(0f), t)
    return FlightFrame(dst = dst, window = window, uniformScale = scale, clipRadius = radius)
}
