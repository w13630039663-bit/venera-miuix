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

    @Test fun `只有双页一次跨两页`() {
        assertEquals(2, readerPageStep(ReaderReadingMode.DOUBLE_PAGE))
        ReaderReadingMode.values().filter { it != ReaderReadingMode.DOUBLE_PAGE }.forEach { mode ->
            assertEquals("模式 $mode 不该跨两页", 1, readerPageStep(mode))
        }
    }

    @Test fun `双页连点走完所有组 不漏组也不回环`() {
        // 40 页 = 20 组（组首 0,2,...,38）。旧写法 +1 再被 scrollToPage 的 /2 收回去，
        // 序列会在 0,0,0… 上永远不动 —— 这条测试钉的就是"每次点都真的换一组"。
        val lastIndex = 39
        var page = 0
        val visited = mutableListOf(page)
        while (true) {
            page = nextPageIndex(page, ReaderReadingMode.DOUBLE_PAGE, lastIndex) ?: break
            visited += page
        }
        assertEquals((0..38 step 2).toList(), visited)
    }

    @Test fun `双页最后一组只有一页时也能走到组首`() {
        // 41 页：最后一组是 40 + 无伴。步长 2 必须正好停在 40，再点才报"到头"。
        assertEquals(40, nextPageIndex(38, ReaderReadingMode.DOUBLE_PAGE, 40))
        assertEquals(null, nextPageIndex(40, ReaderReadingMode.DOUBLE_PAGE, 40))
    }

    @Test fun `到头返回 null 而不是原页`() {
        // 返回原页的话，自动巡航会空转着报"下一页"：屏上没变，却像是在翻。
        assertEquals(null, nextPageIndex(5, ReaderReadingMode.HORIZONTAL_LTR, 5))
        assertEquals(null, nextPageIndex(0, ReaderReadingMode.HORIZONTAL_LTR, -1))
        assertEquals(null, previousPageIndex(0, ReaderReadingMode.DOUBLE_PAGE))
    }

    @Test fun `进度条停在组内第二页时 退一步仍是退一整组`() {
        // 拖 Slider 可以落在奇数页（组内第二页）。判据取**组首**：
        // 从第 1 页（第一组的后半）往回退是"退无可退"，而不是原地交一个 0 假装翻成功了。
        assertEquals(null, previousPageIndex(1, ReaderReadingMode.DOUBLE_PAGE))
        assertEquals(0, previousPageIndex(3, ReaderReadingMode.DOUBLE_PAGE))
        assertEquals(4, previousPageIndex(5, ReaderReadingMode.HORIZONTAL_LTR))
    }

    @Test fun `双页从组内页起步也能走到下一组首`() {
        // 停在第 3 页（第二组的后半）时点"下一页"，要交组首 4，而不是 5 ——
        // 交奇数页就得靠 scrollToPage 整除悄悄纠正，页码气泡会短暂与实际摆出的组差一页。
        assertEquals(4, nextPageIndex(3, ReaderReadingMode.DOUBLE_PAGE, 39))
    }
}
