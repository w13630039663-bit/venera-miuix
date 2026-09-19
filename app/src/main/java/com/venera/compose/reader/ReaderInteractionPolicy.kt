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
