package com.venera.compose.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderInteractionPolicyTest {
    @Test fun `middle is always the menu in every reading mode`() {
        ReaderReadingMode.values().forEach { mode ->
            listOf(0.25f, 0.5f, 0.75f).forEach { x ->
                assertEquals(ReaderTapAction.TOGGLE_CONTROLS, readerTapAction(x, mode, true, ReaderPanel.NONE))
            }
        }
    }
    @Test fun `rtl swaps edge navigation but does not change menu access`() {
        assertEquals(ReaderTapAction.NEXT_PAGE, readerTapAction(0.1f, ReaderReadingMode.HORIZONTAL_RTL, true, ReaderPanel.NONE))
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, readerTapAction(0.9f, ReaderReadingMode.HORIZONTAL_RTL, true, ReaderPanel.NONE))
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, readerTapAction(0.1f, ReaderReadingMode.HORIZONTAL_LTR, true, ReaderPanel.NONE))
        assertEquals(ReaderTapAction.NEXT_PAGE, readerTapAction(0.9f, ReaderReadingMode.HORIZONTAL_LTR, true, ReaderPanel.NONE))
    }
    @Test fun `disabled edge navigation and vertical mode still open controls`() {
        ReaderReadingMode.values().forEach { mode ->
            assertEquals(ReaderTapAction.TOGGLE_CONTROLS, readerTapAction(0.01f, mode, false, ReaderPanel.NONE))
        }
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, readerTapAction(0.99f, ReaderReadingMode.VERTICAL_CONTINUOUS, true, ReaderPanel.NONE))
    }
    @Test fun `modal panels suppress underlying menu and page actions`() {
        ReaderPanel.values().filter { it != ReaderPanel.NONE }.forEach { panel ->
            listOf(0.1f, 0.5f, 0.9f).forEach { x ->
                assertEquals(ReaderTapAction.NONE, readerTapAction(x, ReaderReadingMode.HORIZONTAL_LTR, true, panel))
            }
        }
    }
}
