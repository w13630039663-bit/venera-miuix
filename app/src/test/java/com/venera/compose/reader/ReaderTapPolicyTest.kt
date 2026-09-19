package com.venera.compose.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「反转点击翻页方向」与 RTL 镜像是两回事，二者可叠加 —— 这里把四种组合钉死，
 * 免得以后有人把 reversed 当成"切换阅读方向"来改。
 */
class ReaderTapPolicyTest {

    private fun action(x: Float, mode: ReaderReadingMode = ReaderReadingMode.HORIZONTAL_LTR, reversed: Boolean = false) =
        readerTapAction(x, mode, clickToTurn = true, panel = ReaderPanel.NONE, reversed = reversed)

    @Test fun leftToRightRightEdgeTurnsNext() = assertEquals(ReaderTapAction.NEXT_PAGE, action(0.9f))

    @Test fun leftToRightLeftEdgeTurnsPrevious() = assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(0.1f))

    @Test fun rightToLeftMirrorsEdges() {
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(0.9f, ReaderReadingMode.HORIZONTAL_RTL))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(0.1f, ReaderReadingMode.HORIZONTAL_RTL))
    }

    @Test fun reversedSwapsBothDirections() {
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(0.9f, reversed = true))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(0.1f, reversed = true))
        // RTL 叠加反转 = 回到 LTR 的语义
        assertEquals(ReaderTapAction.NEXT_PAGE, action(0.9f, ReaderReadingMode.HORIZONTAL_RTL, true))
    }

    @Test fun middleAndVerticalOnlyToggleControls() {
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, action(0.5f))
        assertEquals(
            ReaderTapAction.TOGGLE_CONTROLS,
            action(0.9f, ReaderReadingMode.VERTICAL_CONTINUOUS),
        )
    }

    /** 面板开着时点击不该顺手切换控制层显隐。 */
    @Test fun openPanelSwallowsTap() = assertEquals(
        ReaderTapAction.NONE,
        readerTapAction(0.9f, ReaderReadingMode.HORIZONTAL_LTR, true, ReaderPanel.CHAPTERS),
    )
}
