package com.venera.desktop.gallery.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 桌面与 Android 两侧**宽度口径**的漂移尺（S2-B §2.5 用例 ②）。
 *
 * 这条尺子只做**只读文本核对**，不改 Android 一行 —— `WideScreenPolicy.kt` 本轮明确不动
 * （原计划要往它加 `railWidth()`，那是动 Android UI 基建，已撤）。
 *
 * ## 三个数的对岸在哪
 *
 * | 桌面的数 | 对岸 | 会不会漂 |
 * |---|---|---|
 * | pane 224 | `components/WideScreenPolicy.kt:60` 的 private `ExpandedSideBarWidth = 224.dp`（master `_kSideBarWidth`） | **会** —— 所以钉 |
 * | 每列 200 | 同一颗文件 `:130` 表达式里的除数 `200f`（沿 master 预览格 `maxCrossAxisExtent: 200`） | **会** —— 所以钉 |
 * | 横向内边距 24 | 同一颗文件 `:121` 的 `imageWallHorizontalPadding()` = `ui/tokens/Spacing.kt:33` 的 `screenHorizontal = 12.dp` × 2 | **会** —— 所以钉 |
 * | rail 48 | **没有对岸**：Android 端没有"域切换条"这一层 | 不会 —— 桌面自持 |
 *
 * ⚠️ 别把 `WideScreenPolicy.kt:154` 那枚 `WideScreenSideBudget = 48.dp` 当成 rail 的对岸：
 * 那是 master 底栏的左右留白（`min(540, 宽 − 24*2)` 那把尺里的 24×2），语义与"域切换条宽"无关。
 *
 * ## 为什么"重复"在这里是被批准的
 *
 * 桌面与 Android 各持一份 224 与 200 不是自由重复，是**受机器看管的重复**：
 * 想合成一把就得把 `components/` 开进 `:desktop` 的 srcDir，而那颗目录是 Android UI 基建
 * （`DesktopUiIsolationTest` 禁的就是这一类跨端引用），并且 `desktop/build.gradle.kts` 本轮冻结。
 */
class DesktopWidthCaliberDriftTest {

    @Test
    fun `Android 那三处口径逐字仍在原位`() {
        val policy = DesktopSourceTree.androidSource("components/WideScreenPolicy.kt").readText()
        CALIBERS_ON_ANDROID.forEach { (name, literal) ->
            assertTrue("对岸 $name 里的「$literal」不见了 —— Android 侧改了口径，桌面的账要跟着重算", policy.contains(literal))
        }
        // 24 这把的另一半在 token 表里，不核对它等于只核对了一半。
        val spacing = DesktopSourceTree.androidSource("ui/tokens/Spacing.kt").readText()
        assertTrue("imageWallHorizontalPadding 的一半来自 screenHorizontal=12.dp，那颗数变了要重算", spacing.contains("screenHorizontal: Dp = 12.dp"))
    }

    @Test
    fun `WideScreenPolicy 没被塞进桌面的 rail 口径`() {
        // §2.4 撤掉的正是这一步：往 Android 的 UI 基建里加 railWidth()。
        val policy = DesktopSourceTree.androidSource("components/WideScreenPolicy.kt").readText()
        assertTrue("RailWidth 不许进 Android 侧的宽度基建", policy.contains("railWidth").not())
    }

    @Test
    fun `桌面侧每颗字面量只有唯一出处`() {
        val metrics = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryMetrics.kt")
        assertTrue("唯一出处那颗文件读不到：${metrics.path}", metrics.isFile)
        CALIBERS_ON_DESKTOP.forEach { (literal, expectedInMetrics) ->
            assertEquals(
                "桌面里「$literal」只许出现在 DesktopGalleryMetrics.kt（其余文件出现 $expectedInMetrics 处即漂移）",
                0,
                DesktopSourceTree.desktopMainSources()
                    .filterNot { it.absolutePath == metrics.absolutePath }
                    .sumOf { DesktopSourceTree.codeOccurrences(it, literal) },
            )
            assertEquals(
                "DesktopGalleryMetrics.kt 的**代码行**里「$literal」必须恰一处（注释里点名对岸不计）",
                expectedInMetrics,
                DesktopSourceTree.codeOccurrences(metrics, literal),
            )
        }
    }

    /**
     * 列数读数：**1440 档 6 列**（当前默认窗，稿 `.frame` 尺寸）、1080 档 4 列、1280 档 5 列。
     *
     * ## 为什么 1440 是 6 而不是稿上画的那个 5
     *
     * 权威稿 `docs/designs/windows-gallery-home-touhou-2026-10-03.html:338` 与 `:657` 两处**逐字**写着
     * 「内容净宽 **1168** → **6 列**」。核算：`1440 − 48 − 224 = 1168`（正是稿标的净宽）
     * → `1168 − 24 = 1144` → `ceil(1144/200) = 6`，三步全对。
     *
     * 而稿上 `.wall{grid-template-columns:repeat(5,minmax(0,1fr))}`（`:184`）那个 5 是
     * **展示稿的硬编码摆位**，不是推导产物 —— CSS Grid 画板要的是"看起来对"，生产要的是"公式对"。
     * 本仓的立场就写在这颗用例的名字里（`桌面侧每颗字面量只有唯一出处`）：**推导不许抄展示稿**。
     * 所以 6 列是正解，不是偏差。
     */
    @Test
    fun `列数逐档钉死 1440 是 6 列 都不许照展示稿的 5 抄`() {
        val w1440 = 1440f
        val c1440 = w1440 - DesktopGalleryMetrics.railWidth.value - DesktopGalleryMetrics.paneWidth.value
        assertEquals(
            "1440 − 48 − 224 = 1168（与稿 :338/:657 标注的「内容净宽 1168」同值）",
            1168f, c1440, 0.001f,
        )
        assertEquals(
            "1440 档：1168 − 24 = 1144，ceil(1144/200) = 6 —— 展示稿那个 repeat(5) 是画板摆位，不是口径",
            6, DesktopGalleryMetrics.imageWallColumnCount(c1440),
        )

        // 1080 仍是有效档（窗口可缩放），它的 4 列由**同一条公式**得出，不是另一把尺。
        val c1080 = 1080f - DesktopGalleryMetrics.railWidth.value - DesktopGalleryMetrics.paneWidth.value
        assertEquals("1080 − 48 − 224 = 808", 808f, c1080, 0.001f)
        assertEquals(
            "1080 档：808 − 24 = 784，ceil(784/200) = 4",
            4, DesktopGalleryMetrics.imageWallColumnCount(c1080),
        )
        // 1280 是 5 列那一档，记在这里是为了"某天有人把默认窗改回 1280"时不必重新推一遍。
        assertEquals(5, DesktopGalleryMetrics.imageWallColumnCount(1280f - 48f - 224f))
        // 极窄窗的下限来自 Android 原式的 coerceAtLeast(3)，不是桌面另定的一档。
        assertEquals(3, DesktopGalleryMetrics.imageWallColumnCount(300f))
    }

    /**
     * 默认窗尺寸**按屏钳制**（收掉计划里的 R-g）。
     *
     * ## 为什么这条必须存在
     *
     * `VeneraDesktop.kt` 用的是 `WindowPlacement.Floating`，它**不做尺寸自适应** ——
     * 写死 1440 的窗开在 1366 宽的屏上，右边那一截直接跑到屏幕外，
     * 图片墙最右一列**永远看不见**。而"看不见"这件事：不报错、不抛异常、单测照跑绿、
     * 截图也只截得到屏幕内的部分 —— 只有真的坐在那台小屏前的人才会发现。
     *
     * 所以钳制写成了纯函数（屏幕读数由调用方喂，见 [DesktopGalleryMetrics.windowSizeFor]），
     * 这条用例就是那台"小屏"：不用开窗，也能把"1366 屏上开多大"钉死。
     */
    @Test
    fun `默认窗尺寸按屏钳制 小屏上不许开出比屏幕还大的窗`() {
        // 大屏（本机 2560×1440）：照设计稿画板开，一颗数不改，列数仍是 6。
        assertEquals(
            "2560×1440 屏上应原样开 1440×900（= 稿 .frame 画板）",
            DpSize(1440.dp, 900.dp),
            DesktopGalleryMetrics.windowSizeFor(2560, 1440),
        )
        // 1920 屏：宽够（1920−80 = 1840 > 1440），高也够（1080−120 = 960 > 900）⇒ 仍是目标尺寸。
        assertEquals(DpSize(1440.dp, 900.dp), DesktopGalleryMetrics.windowSizeFor(1920, 1080))
        // 1366×768（R-g 点名的那一档）：宽 1366−80 = 1286、高 768−120 = 648，两轴都收。
        assertEquals(
            "1366 宽的屏上应收到 1286×648，而不是开一扇 1440 的窗让最右一列跑到屏幕外",
            DpSize(1286.dp, 648.dp),
            DesktopGalleryMetrics.windowSizeFor(1366, 768),
        )

        // 不变量：任何屏幕上都**不许开出比屏幕还大的窗**。
        listOf(
            2560 to 1440, 1920 to 1080, 1600 to 900, 1366 to 768,
            1280 to 720, 1024 to 768, 800 to 600, 640 to 480,
        ).forEach { (sw, sh) ->
            val size = DesktopGalleryMetrics.windowSizeFor(sw, sh)
            assertTrue(
                "屏幕 ${sw}×${sh} 上算出了 ${size.width.value.toInt()}×${size.height.value.toInt()} 的窗 —— 超出屏幕",
                size.width.value <= sw && size.height.value <= sh,
            )
        }
    }

    private companion object {
        /** 要在 Android 侧钉住的三处口径：文件 → 必须逐字存在的串。（写成 List 而不是 Map：三处同在一颗文件里，Map 会互相覆盖。） */
        val CALIBERS_ON_ANDROID = listOf(
            "WideScreenPolicy.kt" to "224.dp",
            "WideScreenPolicy.kt" to "72.dp",
            "WideScreenPolicy.kt" to "200f",
        )

        /**
         * 桌面侧的字面量 → 它在 DesktopGalleryMetrics.kt 代码行里应有的出现次数。
         * `72.dp` 是 0：rail 是 48 不是 72，桌面压根不该有那把折叠侧栏的数。
         */
        val CALIBERS_ON_DESKTOP = listOf(
            "224.dp" to 1,
            "72.dp" to 0,
            "200f" to 1,
        )
    }
}
