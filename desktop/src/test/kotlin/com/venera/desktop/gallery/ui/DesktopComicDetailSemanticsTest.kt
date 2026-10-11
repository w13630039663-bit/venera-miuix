package com.venera.desktop.gallery.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 批 80 · 详情那一屏的**离屏语义树**判据（2026-10-10 口径改口后第一批用得上这一层的）。
 *
 * ## 这一层抓什么、抓不到什么
 *
 * 抓的是"状态 → 真重组 → 屏上那句话真的在/真的换了"——纯函数判据只能验函数返回值，
 * 源码扫描判据只能验接线形状，这两层都盖不住"返回值有没有反映到屏上"。
 * 抓不到的是观感（颜色/糊/尺寸）、跨窗口玻璃、系统剪贴板与文件框、真站方答复。
 *
 * ## 为什么渲的是 [DesktopComicDetailContent] 而不是整颗 pane
 *
 * 整颗 pane 要真 `EngineSession`（GraalJS + 真源 + 真网络），离屏渲不出来。
 * 所以这一屏从一开始就把**装载**与**展示**分成两件：装载那一件只有"发一次 JS + 折成三态"，
 * 展示那一件吃 [ComicDetailState] 这个纯数据，能被喂着渲。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopComicDetailSemanticsTest {

    private fun readySingle() = ComicDetailState.Ready(
        title = "某本漫画",
        description = "一句话简介",
        episodes = listOf(EngineSession.Episode("第 1 话", "a"), EngineSession.Episode("第 2 话", "b")),
        cover = "https://detail.jpg",
    )

    @Test
    fun 详情上屏那刻题名简介与章数都在屏上() = runComposeUiTest {
        setContent {
            DesktopComicDetailContent(
                state = readySingle(),
                target = DesktopComicTarget("9", "卡上那个题名", ""),
                favoriteOutcome = null,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
            )
        }
        // 题名走"站方给了就用站方的"那一条（卡上题名只是过渡）
        onNodeWithText("某本漫画", substring = true).assertExists("站方题名该上屏")
        onNodeWithText("共 2 话").assertExists("章数该上屏")
        onNodeWithText("一句话简介").assertExists("简介该上屏")
    }

    @Test
    fun 站方没给简介时那一栏不摆占位句() = runComposeUiTest {
        setContent {
            DesktopComicDetailContent(
                state = ComicDetailState.Ready(
                    title = "单本",
                    description = "   ",
                    episodes = listOf(EngineSession.Episode("", "9", placeholder = true)),
                    cover = "",
                ),
                target = DesktopComicTarget("9", "单本", ""),
                favoriteOutcome = null,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
            )
        }
        // 「暂无简介」是替源站撒一句它没说过的话；而"没有这一栏"与"这一栏是空的"在屏上必须同形。
        onNodeWithText("暂无", substring = true).assertDoesNotExist()
        onNodeWithText("共 1 话").assertExists("占位章也要算一章，否则整本看着像没目录")
    }

    @Test
    fun 没问到目录时那句点名是没成而不是零话() = runComposeUiTest {
        setContent {
            DesktopComicDetailContent(
                state = ComicDetailState.Failed("IllegalStateException: Invalid Data"),
                target = DesktopComicTarget("9", "某本", ""),
                favoriteOutcome = null,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
            )
        }
        onNodeWithText("没问到目录", substring = true).assertExists()
        onNodeWithText("共 0 话").assertDoesNotExist()
        // 故障那一格不许同时摆章节表：占位章那一行是 Ready 分支才有的东西
        onNodeWithText("整部（这一本没有分话）").assertDoesNotExist()
    }

    @Test
    fun 等回话那一屏不许提前摆章数() = runComposeUiTest {
        setContent {
            DesktopComicDetailContent(
                state = ComicDetailState.Loading,
                target = DesktopComicTarget("9", "某本", ""),
                favoriteOutcome = null,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
            )
        }
        onNodeWithText("共 ", substring = true).assertDoesNotExist()
        // 过渡题名：站方题名还没回来时用卡上那个（拿漫画号充题名是另一种谎）
        onNodeWithText("某本", substring = true).assertExists()
    }

    @Test
    fun 退回列表与收藏各按一次都真落到回调() = runComposeUiTest {
        var backed = 0
        var faved = 0
        setContent {
            DesktopComicDetailContent(
                state = readySingle(),
                target = DesktopComicTarget("9", "某本", ""),
                favoriteOutcome = null,
                onBack = { backed += 1 },
                onFavorite = { faved += 1 },
                onOpenEpisode = {},
            )
        }
        onNodeWithText("← 返回列表").performClick()
        onNodeWithText("收藏").performClick()
        assertEquals("「← 返回列表」按一次该退一笔", 1, backed)
        assertEquals("详情那一屏的收藏按一次该发一笔", 1, faved)
    }

    /**
     * 批 81 那一格的入口：章节表里的一行**按下去该进阅读那一屏**。
     *
     * 这一条为什么不能只靠"源码里挂了 clickable"：批 80 之前那一行就是死的，而屏上写着「第 1 话」
     * —— 文本在、行在、什么都在，缺的只有那一下回调。语义树里按一次才量得到"接上了"。
     */
    @Test
    fun 某一话按一次把那一话交回给阅读屏() = runComposeUiTest {
        val opened = mutableListOf<DesktopComicReading>()
        setContent {
            DesktopComicDetailContent(
                state = readySingle(),
                target = DesktopComicTarget("9", "某本", "https://card.jpg"),
                favoriteOutcome = null,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = { reading -> opened += reading },
            )
        }
        onNodeWithText("第 2 话").performClick()
        assertEquals("按一行该恰好落一次（落两次就是同时按到了别处）", 1, opened.size)
        assertEquals(
            "落回的是**被按的那一话**，而不是第一话；封面按对岸那条优先级取详情那一张（入口那枚只在该栏缺货时才用）",
            DesktopComicReading(
                "9", "某本", "b", "第 2 话",
                placeholder = false, episodeIndex = 1, startPage = 0, coverUrl = "https://detail.jpg",
            ),
            opened[0],
        )
        // 行号必须跟着**被按的那一行**走（批 82 的历史那一列吃它；手机侧读它续读，填错一档就把另一端的进度挪走）。
        // 只按第 2 话这一档量不出"恒 0"这种错法，所以第二下按第一行。
        onNodeWithText("第 1 话").performClick()
        assertEquals("两行各落一次", 2, opened.size)
        assertEquals("第一行的行号是 0", 0, opened[1].episodeIndex)
        assertEquals("第二行的行号是 1", 1, opened[0].episodeIndex)
    }

    @Test
    fun 收藏那句回话上屏而不是只进日志() = runComposeUiTest {
        setContent {
            DesktopComicDetailContent(
                state = readySingle(),
                target = DesktopComicTarget("9", "某本", ""),
                favoriteOutcome = DesktopComicFavoriteOutcome.Already("默认"),
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
            )
        }
        // 与卡角那一枚共用同一份措辞原子（两处各写一句就会念成两个名字）
        onNodeWithText(desktopComicFavoriteScreenReading(DesktopComicFavoriteOutcome.Already("默认")))
            .assertExists("三态里那一句要上屏")
    }

    @Test
    fun 章节表逐条上屏而不合并成一串() = runComposeUiTest {
        setContent {
            DesktopComicDetailContent(
                state = readySingle(),
                target = DesktopComicTarget("9", "某本", ""),
                favoriteOutcome = null,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
            )
        }
        // 两行各自在屏上：拼成一整段也算"看得到章节"，所以必须逐条点名（同一族坑：判式吃整串）。
        onNodeWithText("第 1 话").assertExists()
        onNodeWithText("第 2 话").assertExists()
    }
}
