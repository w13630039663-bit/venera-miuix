package com.venera.desktop.gallery.ui

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

    @Test
    fun `默认 1080 窗口是 4 列 不是稿上的 5`() {
        val windowWidth = 1080f
        val contentWidth = windowWidth - DesktopGalleryMetrics.railWidth.value - DesktopGalleryMetrics.paneWidth.value
        assertEquals("默认档窗口扣掉 rail 48 与 pane 224 之后是 808", 808f, contentWidth, 0.001f)
        assertEquals(
            "1080 − 48 − 224 = 808，再扣横向内边距 24 = 784，ceil(784/200) = 4 —— 列数不许照设计稿抄 5",
            4,
            DesktopGalleryMetrics.imageWallColumnCount(contentWidth),
        )
        // 5 列有它的出处，但那是**更宽的窗**：1280 − 48 − 224 = 1008，(1008−24)/200 = 4.92 → 5。
        // 记在这里是为了"稿上画 5 列"与"默认档算 4 列"这两件事不再被人当成矛盾。
        assertEquals(5, DesktopGalleryMetrics.imageWallColumnCount(1280f - 48f - 224f))
        // 极窄窗的下限来自 Android 原式的 coerceAtLeast(3)，不是桌面另定的一档。
        assertEquals(3, DesktopGalleryMetrics.imageWallColumnCount(300f))
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
