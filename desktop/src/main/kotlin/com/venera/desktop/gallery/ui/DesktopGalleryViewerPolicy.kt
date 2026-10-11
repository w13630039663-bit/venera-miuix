package com.venera.desktop.gallery.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 大图那一屏的三道算式（第五十批）。全部纯函数：布局与取证都只吃它们的结果，
 * 不许在调用点各写一遍 —— 这一族（本仓叫"两条轨"）已经在预取上吃过一次。
 */

/**
 * 浮层开着时那一屏能用多宽。
 *
 * **减两道而不是一道**：面板 [DesktopGalleryMetrics.detailWidth] 与它左缘那道渐隐带
 * [DesktopGalleryMetrics.detailScrimWidth] 挂在同一个 `Row` 上（`VeneraDesktopApp` 里那两格），
 * 只减面板的话大图右缘就压在渐隐带底下 —— 而那条带今天就在屏上。
 *
 * 窗口窄到不足一档时**夹到 0 而不是负数**：负宽度会让布局直接崩，而崩的是整屏。
 */
internal fun desktopViewerContentWidth(contentWidth: Dp, overlayOpen: Boolean): Dp {
    if (!overlayOpen) return contentWidth
    val used = DesktopGalleryMetrics.detailWidth + DesktopGalleryMetrics.detailScrimWidth
    return (contentWidth - used).coerceAtLeast(0.dp)
}

/**
 * 进那一屏时落在哪一档（设计 §4.1 的 A 案：升到屏上叫「大图」的那档）。
 *
 * 吃的是 [desktopDetailTiers] 给出来的那张表**而不是直接回 `SAMPLE`**：
 * 被挡那张表是空的（`masked` ⇒ 一档都不摆），而视频与站方没给 `file_url` 那两条撤档规则
 * 也住在表里 —— 跳过表就等于把它们的判据绕过去。判据 ⑥ 钉的就是这一条。
 */
internal fun desktopViewerEntryTier(tiers: List<DesktopDetailTier>): DesktopDetailTier? =
    if (tiers.contains(DesktopDetailTier.SAMPLE)) DesktopDetailTier.SAMPLE else tiers.firstOrNull()

/**
 * 取证种子的倍数夹进合法区间。
 *
 * 库那枚 `changeScale` 内部也会 `coerceIn`，但它夹得**不出声** ——
 * 不在这里先夹并念出来，"我给了 9 倍"与"屏上只有 5 倍"就分不清是谁错。
 */
internal fun desktopViewerZoomScale(raw: Float, maxScale: Float): Float =
    raw.coerceIn(1f, maxScale.coerceAtLeast(1f))

/** 夹完那句实话：给的与用的都要在。 */
internal fun desktopViewerZoomReadout(raw: Float, applied: Float): String =
    "D_放大种子 给=$raw 用=$applied" + if (raw == applied) "" else "（已夹到合法区间）"

/**
 * 键盘那一档的步进倍率（第六十六批）。
 *
 * 为什么是 1.25 而不是 +0.5 一档：库的滚轮走的是**乘**（一格 ×1.1 那一族），
 * 键盘若走加法就会出现"滚两轮再按两下加号回到原地"这种两套真相；
 * 而 1.25 比滚轮那一档略大，是因为键盘一次按到底要几步才到 5 倍，1.1 那种按到手酸。
 */
internal const val DesktopZoomStepFactor = 1.25f

/**
 * 键盘放大 / 缩小一档之后的倍数。算式**只住在这里**（调用点只递方向）。
 *
 * 两头都夹：下限 1f（缩到 1 以下就是"图比屏幕还小而看不见"），上限吃库里那枚 `maxScale`。
 * `maxScale` 自己也要 `coerceAtLeast(1f)` —— 它要是 0 或负（构造期没量好），
 * `coerceIn(1f, 0f)` 会直接抛，而"抛在按键上"读起来就是"这枚键坏了"。
 */
internal fun desktopViewerZoomStep(current: Float, maxScale: Float, direction: Int): Float =
    (if (direction >= 0) current * DesktopZoomStepFactor else current / DesktopZoomStepFactor)
        .coerceIn(1f, maxScale.coerceAtLeast(1f))

/**
 * 放大那一格上屏的那一句（第五十五批）。**没放大就交回 null** —— 那一格整个不摆。
 *
 * 为什么常驻一句「放大 1.0x」不算更周到：那一屏右下角要一直占着一格废话，
 * 而它说"没放大"的时候用户本来就看得见没放大。这一格存在的唯一理由是**放大之后看得境倍数**。
 *
 * 一位小数而不是原样念 `scale`：滚轮一格一格滚出来的数是 1.0734… 那种，
 * 常驻刷新的两位小数读起来像坏了；四舍五入到 0.1 之后，1.04 那一档仍算"没放大"（贴着 1 的抖动不摆）。
 * 非有限值（NaN / 无穷）与 0 以下一律不摆：摆出「放大 NaNx」比不摆更像坏了。
 */
internal fun desktopZoomChrome(scale: Float): String? {
    if (!scale.isFinite() || scale <= 0f) return null
    val tenths = (scale * 10f).roundToInt()
    if (tenths <= 10) return null
    return "放大 ${tenths / 10}.${tenths % 10}x"
}
