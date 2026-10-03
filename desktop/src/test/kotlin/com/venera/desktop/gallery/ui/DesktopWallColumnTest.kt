package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 桌面画廊墙的列数读数。
 *
 * ## 这颗用例 2026-10-04 的变化：删掉了那份私有复刻
 *
 * 原先这里有一个 `calculateColumnCount(screenWidth)`，把生产公式
 * （[DesktopGalleryMetrics.imageWallColumnCount]）**又抄了一遍**：`rail=48 / pane=224 /
 * horizontalPadding=48 / 200f / 下限 3`。两个问题：
 *
 * 1. **它与生产零耦合** —— 生产改了公式（比如调下限、调预算），它照样绿。
 *    一颗"测列数"的用例测不到列数，只测了自己的副本，这是本仓记过的那种**看起来有判据、
 *    其实没有**。现在直接调生产函数，生产一动这里就红。
 * 2. **它把 padding 语义读错了** —— `horizontalPadding = 48` 配注释 `// 24 * 2`，
 *    而生产 [DesktopGalleryMetrics.imageWallHorizontalPadding] 是 **24dp 合计**
 *    （对岸 `WideScreenPolicy.imageWallHorizontalPadding()` = `screenHorizontal 12 × 2`）。
 *    24dp 的差没暴露纯属 `ceil` 恰好在三档上把它吞了。
 *
 * ## 读数逐档
 *
 * | 窗口 | 内容区 | 列数 |
 * |---|---|---|
 * | **1440**（当前默认，稿 `.frame`） | `1440−48−224 = 1168` | **6** |
 * | 1280 | 1008 | 5 |
 * | 1080（旧默认） | 808 | 4 |
 * | 960 | 688 | 4 |
 *
 * 1440 = 6 与权威稿 `:338` / `:657` 的「内容净宽 1168 → 6 列」逐字相符。
 * 推导与"为什么不是展示稿那个 5"写在 [DesktopWidthCaliberDriftTest] 同名用例里。
 */
class DesktopWallColumnTest {

    /**
     * 内容区宽 = 窗口 − rail − pane。
     *
     * ⚠️ 这里引 [DesktopGalleryMetrics.railWidth] / [paneWidth] 而**不是**写 `48` / `224`：
     * 写死就等于又造了第二份口径，正是本用例刚删掉的那个毛病。
     */
    private fun columns(windowWidth: Int): Int =
        DesktopGalleryMetrics.imageWallColumnCount(
            windowWidth - DesktopGalleryMetrics.railWidth.value - DesktopGalleryMetrics.paneWidth.value,
        )

    @Test
    fun `1440 窗口下应得 6 列`() {
        assertEquals(6, columns(1440))
    }

    @Test
    fun `1280 窗口下应得 5 列`() {
        assertEquals(5, columns(1280))
    }

    @Test
    fun `1080 窗口下应得 4 列`() {
        assertEquals(4, columns(1080))
    }

    @Test
    fun `960 窗口下应得 4 列`() {
        assertEquals(4, columns(960))
    }

    /** 极窄窗守住下限 3 —— 那颗下限来自 Android 原式的 `coerceAtLeast(3)`，不是桌面另定的。 */
    @Test
    fun `极窄窗守住下限 3 列`() {
        assertEquals(3, DesktopGalleryMetrics.imageWallColumnCount(300f))
    }
}
