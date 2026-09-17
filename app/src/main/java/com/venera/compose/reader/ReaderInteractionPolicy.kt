package com.venera.compose.reader

internal enum class ReaderTapAction { TOGGLE_CONTROLS, PREVIOUS_PAGE, NEXT_PAGE, NONE }
internal enum class ReaderPanel { NONE, SETTINGS, CHAPTERS, COMMENTS }

/** Shared by Telephoto's single-click callback and the non-zooming page container. */
internal fun readerTapAction(
    xFraction: Float,
    mode: ReaderReadingMode,
    clickToTurn: Boolean,
    panel: ReaderPanel
): ReaderTapAction {
    if (panel != ReaderPanel.NONE) return ReaderTapAction.NONE
    if (!clickToTurn || mode == ReaderReadingMode.VERTICAL_CONTINUOUS ||
        xFraction in 0.25f..0.75f || !xFraction.isFinite()) {
        return ReaderTapAction.TOGGLE_CONTROLS
    }
    val next = (xFraction > 0.75f) != (mode == ReaderReadingMode.HORIZONTAL_RTL)
    return if (next) ReaderTapAction.NEXT_PAGE else ReaderTapAction.PREVIOUS_PAGE
}
