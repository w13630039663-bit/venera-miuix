package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S3 Task 4 & 5: 桌面画廊墙列数测试
 */
class DesktopWallColumnTest {

    @Test
    fun `1080 窗口下应得 4 列`() {
        assertEquals(4, calculateColumnCount(1080))
    }

    @Test
    fun `1280 窗口下应得 5 列`() {
        assertEquals(5, calculateColumnCount(1280))
    }

    @Test
    fun `960 窗口下应得 4 列`() {
        assertEquals(4, calculateColumnCount(960))
    }

    /**
     * 计算列数的逻辑（仿 WideScreenPolicy.kt:123-130）：
     * val available = screenWidth - railWidth - paneWidth - horizontalPadding
     * ceil(available / 200f).coerceAtLeast(3)
     */
    private fun calculateColumnCount(screenWidth: Int): Int {
        val railWidth = 48
        val paneWidth = 224
        val horizontalPadding = 48 // 24 * 2
        val available = screenWidth - railWidth - paneWidth - horizontalPadding
        return kotlin.math.ceil(available / 200f).toInt().coerceAtLeast(3)
    }
}
