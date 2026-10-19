package com.venera.compose.gallery.domain

/**
 * 批 B 的**锚点闸门**（计划 §5 批 B ⑥⑦ + §3 地基坑）：一块登记/截获来的矩形，
 * 此刻到底算不算"有锚"。纯 Kotlin，口径同 [GalleryFlightFrame]：能脱 gradle 单跑。
 *
 * ## 它防的那一档（pixez 10-13/10-19 两轮现量的"两半都要"）
 *
 * `boundsInWindow()` **默认把矩形裁到窗口内**（Compose 1.12.0 `LayoutCoordinates.kt:196`：
 * "can even result in an empty rectangle … completely clipped ⇒ Rect.Zero"）。本仓现量
 * 9 处调用**零处**显式传 `clipBounds` ⇒ 登记进表里的可能是"被窗口裁剩的一条"。
 * 真机活体证据（10-19 晚 FlyProbe）：`落点首次登记 size=495x16`、`size=495x2` ——
 * 卡被裁到只剩 16px 高照样登记，之后被回收摘掉才补上正常的。
 *
 * pixez 的处置是两半：**零尺寸只忽略不注销**（我们已有，`GalleryFlyIn.noteCardBounds`）
 * ＋ **宽、高两枚可见比都 ≥25% 才算有锚**，否则当场判无锚、退路走侧滑。
 * 我们从前只有前半 —— "裁过但非零"那一档被当真相用了，这就是 §3 说的那半截。
 *
 * ## ⚠️ 不许加"纵横比门槛"（§4.3 冲突表第 4 行的陷阱）
 *
 * pixez 的 `landable` 注释里宣称有"容器纵横比与卡片差距过大 ⇒ 不可落"这一支，
 * **现量代码里根本没有**（只剩尺寸合法性检查）。照抄注释会做出一整条不存在的分支。
 * 这一族的判式只按"屏上真发生的"写：**可见比两枚 + 面积非零**，到此为止。
 */

/** 有锚的最低**可见比**：宽、高两枚**各自**都要 ≥ 这一档（pixez 现量口径 25%）。 */
const val MIN_VISIBLE_RATIO = 0.25f

/**
 * 矩形 [rect] 相对**布局全框** [full] 的可见比（逐轴），钳进 0..1。
 *
 * [full] 取 `boundsInWindow(clipBounds = false)`（没裁的那一块才叫位置），
 * [rect] 取 `boundsInWindow()`（裁过的那一块只用来算"露出多少"与截像素）。
 * 退化输入（任一边没有面积）交回 0、0 = 无锚，不抛、不外推。
 */
fun visibleRatios(full: FlyRect, rect: FlyRect): Pair<Float, Float> {
    if (full.width <= 0f || full.height <= 0f) return 0f to 0f
    // [FlyRect] 是"左上角 + 宽高"的裸形状（domain 层的历史口径），没有 right/bottom：边界自己算。
    val overlapW = minOf(full.left + full.width, rect.left + rect.width) - maxOf(full.left, rect.left)
    val overlapH = minOf(full.top + full.height, rect.top + rect.height) - maxOf(full.top, rect.top)
    val rw = (overlapW / full.width).coerceIn(0f, 1f)
    val rh = (overlapH / full.height).coerceIn(0f, 1f)
    return rw to rh
}

/** 有锚 ⇔ 面积非零 **且** 宽、高两枚可见比都 ≥ [MIN_VISIBLE_RATIO]。没有第三条、也没有纵横比那一档。 */
fun hasAnchor(full: FlyRect, rect: FlyRect): Boolean {
    if (rect.width <= 0f || rect.height <= 0f) return false
    val (rw, rh) = visibleRatios(full, rect)
    return rw >= MIN_VISIBLE_RATIO && rh >= MIN_VISIBLE_RATIO
}

/**
 * 登记侧的入表闸门：裁剩过多（任一枚可见比 < 25%）的矩形**不收**，交回 null = 不登记。
 *
 * 与 `noteCardBounds` 既有的"零尺寸只忽略不注销"同一档脾气：**只忽略、不注销** ——
 * 被裁是因为用户把那排滚出屏幕了，不是这张卡没了；回收那一路由 `forgetCardBounds` 负责。
 * 可见比用**当帧现量**（Lazy 项滚出可视时两枚都会掉下去，判据不需要第二处真相）。
 */
fun anchorAcceptable(full: FlyRect, rect: FlyRect): FlyRect? =
    if (hasAnchor(full, rect)) rect else null
