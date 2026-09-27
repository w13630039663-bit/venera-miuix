package com.venera.compose.reader

internal enum class ReaderTapAction { TOGGLE_CONTROLS, PREVIOUS_PAGE, NEXT_PAGE, NONE }
internal enum class ReaderPanel { NONE, SETTINGS, CHAPTERS, COMMENTS }

/** Shared by Telephoto's single-click callback and the non-zooming page container. */
internal fun readerTapAction(
    xFraction: Float,
    mode: ReaderReadingMode,
    clickToTurn: Boolean,
    panel: ReaderPanel,
    /** 左右半屏语义互换（左利手）。与 RTL 的镜像是两回事，二者可叠加。 */
    reversed: Boolean = false,
): ReaderTapAction {
    if (panel != ReaderPanel.NONE) return ReaderTapAction.NONE
    if (!clickToTurn || mode == ReaderReadingMode.VERTICAL_CONTINUOUS ||
        xFraction in 0.25f..0.75f || !xFraction.isFinite()) {
        return ReaderTapAction.TOGGLE_CONTROLS
    }
    val rightIsNext = (xFraction > 0.75f) != (mode == ReaderReadingMode.HORIZONTAL_RTL)
    return if (rightIsNext != reversed) ReaderTapAction.NEXT_PAGE else ReaderTapAction.PREVIOUS_PAGE
}

/**
 * 一次翻页跨几页。
 *
 * 双页模式**一组两页**，所以"下一页"必须跨 2 —— 这一条以前散在调用方的 `+1` 里，
 * 而页码本身又是"组首"（`doublePagerState.currentPage * 2`，恒为偶数），
 * 于是 `+1` 落到 `scrollToPage` 那里被 `/2` 收回同一组：**点击与音量键的"下一页"永远不动**，
 * 页码气泡、进度条、自动巡航、进度持久化跟着一起冻在偶数页上。
 * 步长归到这一处，调用方不再各自猜。
 */
internal fun readerPageStep(mode: ReaderReadingMode): Int =
    if (mode == ReaderReadingMode.DOUBLE_PAGE) 2 else 1

/**
 * 「下一页」的目标页；null = 已经到头。
 *
 * 返回 null 而不是原页：调用方拿它同时决定"翻不翻"和"自动巡航要不要停"。
 * 若返回原页，巡航会**空转着报"下一页"**—— 屏上什么都没变，这正是本函数要防的那类假象。
 *
 * 目标先**对齐到组首**再跨步：拖动进度条可以落在组内第二页（奇数），
 * 不对齐就会交出奇数页码 —— 那种值要靠 `scrollToPage` 的整除悄悄纠正，
 * 页码气泡与持久化会短暂读到组内页，读数与实际摆出的组差一页。
 */
internal fun nextPageIndex(current: Int, mode: ReaderReadingMode, lastIndex: Int): Int? {
    if (lastIndex < 0) return null
    val step = readerPageStep(mode)
    val target = pairStart(current, step) + step
    return target.takeIf { it <= lastIndex }
}

/**
 * 「上一页」的目标页；null = 已经在第一组。
 *
 * 判据是**组首**而不是当前页码：落在组内第二页（奇数）时，往回退仍然是"退一整组"，
 * 而已经在第一组里（无论第 0 页还是第 1 页）就该报"退无可退"，
 * 不能交出一个原地不动的 0 —— 那会让调用方以为翻页成功了。
 */
internal fun previousPageIndex(current: Int, mode: ReaderReadingMode): Int? {
    val step = readerPageStep(mode)
    val target = pairStart(current, step) - step
    return target.takeIf { it >= 0 }
}

/** 这一页所在组的组首：单页档就是它自己，双页档收回到偶数页。 */
private fun pairStart(page: Int, step: Int): Int = if (step == 1) page else page - page % step
