package com.venera.desktop.gallery.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.data.db.HistoryRecord
import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                progress = null,
                progressUnread = false,
                download = null,
                cancelRequested = false,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
                onDownloadEpisode = {},
                onCancelDownload = {},
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
                progress = null,
                progressUnread = false,
                download = null,
                cancelRequested = false,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
                onDownloadEpisode = {},
                onCancelDownload = {},
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
                progress = null,
                progressUnread = false,
                download = null,
                cancelRequested = false,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
                onDownloadEpisode = {},
                onCancelDownload = {},
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
                progress = null,
                progressUnread = false,
                download = null,
                cancelRequested = false,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
                onDownloadEpisode = {},
                onCancelDownload = {},
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
                progress = null,
                progressUnread = false,
                download = null,
                cancelRequested = false,
                onBack = { backed += 1 },
                onFavorite = { faved += 1 },
                onOpenEpisode = {},
                onDownloadEpisode = {},
                onCancelDownload = {},
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
                progress = null,
                progressUnread = false,
                download = null,
                cancelRequested = false,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = { reading -> opened += reading },
                onDownloadEpisode = {},
                onCancelDownload = {},
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
                progress = null,
                progressUnread = false,
                download = null,
                cancelRequested = false,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
                onDownloadEpisode = {},
                onCancelDownload = {},
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
                progress = null,
                progressUnread = false,
                download = null,
                cancelRequested = false,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
                onDownloadEpisode = {},
                onCancelDownload = {},
            )
        }
        // 两行各自在屏上：拼成一整段也算"看得到章节"，所以必须逐条点名（同一族坑：判式吃整串）。
        onNodeWithText("第 1 话").assertExists()
        onNodeWithText("第 2 话").assertExists()
    }

    /**
     * 批 94 · 那枚主胶囊的三档（有历史 / 没历史 / 库没读到），**按一次**量它落到哪儿。
     *
     * 为什么必须在这一层量：`DesktopComicContinueReadingTest` 那几颗纯函数判据只证明"算式对"，
     * 而本批立的正是"不许摆着「继续阅读」而按下开第一章第 0 页"（对岸自己就是那一形）。
     * 那句只有语义树里按一次才量得到 —— 批 81 那一课：屏上写着「第1话」而那一行是死的。
     */
    @Test
    fun 有历史时胶囊念继续阅读而按下落到那一话那一页() = runComposeUiTest {
        val opened = mutableListOf<DesktopComicReading>()
        setContent {
            DesktopComicDetailContent(
                state = ComicDetailState.Ready(
                    title = "某本漫画",
                    description = "",
                    episodes = listOf(
                        EngineSession.Episode("第 1 话", "a"),
                        EngineSession.Episode("第 2 话", "b"),
                        EngineSession.Episode("第 3 话", "c"),
                    ),
                    cover = "https://detail.jpg",
                ),
                target = DesktopComicTarget("9", "某本", ""),
                favoriteOutcome = null,
                progress = historyRecord(chapterIndex = 1, chapterTitle = "第 2 话", pageIndex = 7),
                progressUnread = false,
                download = null,
                cancelRequested = false,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = { reading -> opened += reading },
                onDownloadEpisode = {},
                onCancelDownload = {},
            )
        }
        onNodeWithText("继续阅读").assertExists("有历史那一档念的是「继续阅读」")
        // 两档同屏 = 屏上同时念两句实话；`assertDoesNotExist()` 在这一版没有消息位，理由写在这行注释上。
        onNodeWithText("开始阅读").assertDoesNotExist()
        onNodeWithText("继续 第 2 话 · 第 8 页").assertExists("那行小字（库里 0 基数 ⇒ 人话从 1 数起）")
        // 那枚「上次读到这里」挂在**库里那一话**那一行上（对岸 :1197 同一格）。
        // 两档都吃 substring：那一句将来带前后缀（分隔符、括号）时，负例仍咬得住、正例不会读成"没挂上"。
        onNodeWithText(DESKTOP_HISTORY_CHAPTER_MARK_DESCRIPTION, substring = true).assertExists()
        onNodeWithText("第 2 话").performClick()
        assertEquals("行那一下仍是从该话开头读（照对岸，本批不给行加续读语义）", 0, opened.single().startPage)
        opened.clear()
        onNodeWithText("继续阅读").performClick()
        assertEquals(
            "胶囊那一下落到**记录那一话 + 记录那一页**（而它对岸的形状是第一章第 0 页）",
            DesktopComicReading(
                "9", "某本", "b", "第 2 话",
                placeholder = false, episodeIndex = 1, startPage = 7, coverUrl = "https://detail.jpg",
            ),
            opened.single(),
        )
    }

    @Test
    fun 没历史时胶囊念开始阅读而按下落在第一话第零页() = runComposeUiTest {
        val opened = mutableListOf<DesktopComicReading>()
        setContent {
            DesktopComicDetailContent(
                state = readySingle(),
                target = DesktopComicTarget("9", "某本", ""),
                favoriteOutcome = null,
                progress = null,
                progressUnread = false,
                download = null,
                cancelRequested = false,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = { reading -> opened += reading },
                onDownloadEpisode = {},
                onCancelDownload = {},
            )
        }
        onNodeWithText("开始阅读").assertExists()
        // 没历史就不许编一行小字出来：「继续 …」那一句是替库里编一句它没记过的话。
        onNodeWithText("继续 ", substring = true).assertDoesNotExist()
        onNodeWithText(DESKTOP_HISTORY_CHAPTER_MARK_DESCRIPTION, substring = true).assertDoesNotExist()
        onNodeWithText("开始阅读").performClick()
        assertEquals("落在第一话", "第 1 话", opened.single().episodeName)
        assertEquals(0, opened.single().episodeIndex)
        assertEquals(0, opened.single().startPage)
    }

    /**
     * 库没读到那一档：**两档胶囊都不摆**。
     *
     * 这一条是"降级路径宁可错慢不可静默交错"落在屏上的形状：那一档若摆「开始阅读」，
     * 按下就把 `comic_history`（**两端共用那张表**）里"下次从第 40 页读"写成第一话第 0 页 ——
     * 手机上那半本进度会被桌面上的一个按钮挪走。少一枚钮是可见的慢，挪走进度是静默的错。
     */
    @Test
    fun 库没读到那一档不摆胶囊而只在日志里念原因() = runComposeUiTest {
        val opened = mutableListOf<DesktopComicReading>()
        setContent {
            DesktopComicDetailContent(
                state = readySingle(),
                target = DesktopComicTarget("9", "某本", ""),
                favoriteOutcome = null,
                progress = null,
                progressUnread = true,
                download = null,
                cancelRequested = false,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = { reading -> opened += reading },
                onDownloadEpisode = {},
                onCancelDownload = {},
            )
        }
        // 没读到那一档不许冒充「没历史」：那一枚按下去会写掉两端共用的那半本进度。
        onNodeWithText("开始阅读").assertDoesNotExist()
        onNodeWithText("继续阅读").assertDoesNotExist()
        assertEquals("没有那一枚，就没有那一笔", 0, opened.size)
        // 目录那一列照旧点得动：撤的是胶囊那一枚，不是整屏。
        onNodeWithText("第 1 话").performClick()
        assertEquals("章节表没被那一档带走", 1, opened.size)
    }

    /** 库里那一格的形状（与 `HistoryDao` 那十枚字段同形；只有三枚是这一屏吃得进的）。 */
    private fun historyRecord(chapterIndex: Int, chapterTitle: String, pageIndex: Int) = HistoryRecord(
        comicId = "9",
        title = "某本漫画",
        author = "",
        coverUrl = "",
        sourceName = "jm",
        lastChapterTitle = chapterTitle,
        lastChapterIndex = chapterIndex,
        lastPageIndex = pageIndex,
        totalPages = 30,
        updatedAt = 1L,
    )

    /**
     * 批 96 · **长按**某一话交回的是这一行的行号，而它不该顺带进阅读那一屏。
     *
     * 为什么是长按而不是行里再摆一枚「下载」：第一版就是那么写的，那颗嵌进去的 `clickable`
     * 把整行的点击吃掉了（三颗既有判据当场红："按一行该恰好落一次"变成 0 次）—— 那是屏上真按不动，
     * 不是测试噪音。桌面这一族早有口径：`combinedClickable` 有 `onLongClick` 而没有 `onRightClick`
     * ⇒ 第二枚动作只能挂长按，而那两句说明必须上屏（下面 ③ 钉的就是那句说明真在屏上）。
     */
    @Test
    fun 长按某一话交回的是这一行的行号而不进阅读屏() = runComposeUiTest {
        val requested = mutableListOf<Int>()
        val opened = mutableListOf<DesktopComicReading>()
        setContent {
            DesktopComicDetailContent(
                state = ComicDetailState.Ready(
                    title = "某本漫画",
                    description = "",
                    episodes = listOf(
                        EngineSession.Episode("第 1 话", "a"),
                        EngineSession.Episode("第 2 话", "b"),
                        EngineSession.Episode("第 3 话", "c"),
                    ),
                    cover = "https://detail.jpg",
                ),
                target = DesktopComicTarget("9", "某本", ""),
                favoriteOutcome = null,
                progress = null,
                progressUnread = false,
                download = null,
                cancelRequested = false,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = { reading -> opened += reading },
                onDownloadEpisode = { index -> requested += index },
                onCancelDownload = {},
            )
        }
        // 这一版桌面测试没有可用的长按手势件（`performTouchInput` 的 `TouchInputScope` 里没有 `longClick`），
        // 所以那一格改由**语义树里这一行到底带不带 LongClick 动作**来证 —— 它比源码锚点强：
        // 锚点只证明写了那一句，而这一条证明它真挂到了那一行上。交回的是哪一行由那颗源码锚点钉。
        assertTrue(
            "第 2 话那一行该带长按动作（不带就是屏上那句说明在说谎）：",
            onNodeWithText("第 2 话").fetchSemanticsNode().config.contains(SemanticsActions.OnLongClick),
        )
        assertEquals("还没长按 ⇒ 不该有任何一笔下载：", 0, requested.size)
        onNodeWithText("第 3 话").performClick()
        assertEquals("而普通那一按仍是进阅读（长按没把原动作挤掉）：", listOf(2), opened.map { it.episodeIndex })
        assertEquals(
            "那句说明必须上屏（没有可见把手的动作，藏起来就等于没有）：",
            1,
            onAllNodesWithText(DESKTOP_CHAPTER_DOWNLOAD_HINT).fetchSemanticsNodes().size,
        )
    }

    /** 批 96 · 那一行状态只挂在**被长按的那一行**上（三行同屏时不许三行一起念同一句进度）。 */
    @Test
    fun 那一行下载状态只挂在被按的那一行上() = runComposeUiTest {
        setContent {
            DesktopComicDetailContent(
                state = ComicDetailState.Ready(
                    title = "某本漫画",
                    description = "",
                    episodes = listOf(
                        EngineSession.Episode("第 1 话", "a"),
                        EngineSession.Episode("第 2 话", "b"),
                        EngineSession.Episode("第 3 话", "c"),
                    ),
                    cover = "https://detail.jpg",
                ),
                target = DesktopComicTarget("9", "某本", ""),
                favoriteOutcome = null,
                progress = null,
                progressUnread = false,
                download = DesktopComicChapterDownload(
                    chapterIndex = 1, written = 5, total = 5, busy = false, failure = null,
                    pagesOnDisk = 5, cancelled = false, queued = false,
                ),
                cancelRequested = false,
                onBack = {},
                onFavorite = {},
                onOpenEpisode = {},
                onDownloadEpisode = {},
                onCancelDownload = {},
            )
        }
        assertEquals("那一句只出现一次：", 1, onAllNodesWithText("已写下 5 页").fetchSemanticsNodes().size)
        onNodeWithText("第 1 话").assertExists("状态行不该把别的行挤掉")
    }

    /**
     * 批 96 · 那一行状态各档各念一句（**批 100 改判：四档 → 五档**）。
     *
     * `written == 0` 与"这一趟写了 N 页"必须念不同的话：合成一句就会把"本机早有了、这一趟一页没动"
     * 念成"这一趟下完了" —— 那正是对岸那句注释要挡的形状（"点一下就直接重下一遍"）。
     *
     * ⚠️ 批 100 改判的两档（这两句从前是**真的**，只是不够）：
     * - `busy` 从前只念"正在写本机…"，89 页那一趟从头到尾不吭声 ⇒ 现在带得上 `已写/应写`；
     * - `failure` 从前不带"盘上已有几页"，而第 63 页被掐断那一趟盘上确实有 62 页 ——
     *   不提它就是 b96d1 那帧 `页=0/0` 的同一族谎（见 `desktopComicChapterOutcome` 那段注释）。
     * 添的正是 `cancelled` 那一档：它**不许**塌进"已写下 N 页"（那一趟没干完活）。
     */
    @Test
    fun 那一行下载状态五档各念一句() {
        assertEquals(
            "正在那一档（批 100 起带得上进度）：", "正在写本机… 2/5 页",
            desktopComicChapterStatusText(
                DesktopComicChapterDownload(0, 0, 5, busy = true, failure = null, pagesOnDisk = 2, cancelled = false, queued = false),
            ),
        )
        assertEquals(
            "下完那一档：", "已写下 5 页",
            desktopComicChapterStatusText(
                DesktopComicChapterDownload(0, 5, 5, busy = false, failure = null, pagesOnDisk = 5, cancelled = false, queued = false),
            ),
        )
        assertEquals(
            "本机已有那一档（这一趟一页都没写）：", "这一话本机已有 5 页",
            desktopComicChapterStatusText(
                DesktopComicChapterDownload(0, 0, 5, busy = false, failure = null, pagesOnDisk = 5, cancelled = false, queued = false),
            ),
        )
        assertEquals(
            "取消那一档要念得出「下次接着补」而不是「已写下」：", "已停在 3/5 页 · 下次从这里接着补",
            desktopComicChapterStatusText(
                DesktopComicChapterDownload(0, 3, 5, busy = false, failure = null, pagesOnDisk = 3, cancelled = true, queued = false),
            ),
        )
        assertEquals(
            "失败那一档要带得上原因，也要带得上盘上已有几页：",
            "下载失败：IllegalStateException: 取页失败 页=3（本机已有 2 页）",
            desktopComicChapterStatusText(
                DesktopComicChapterDownload(
                    0, 0, 5, busy = false, failure = "IllegalStateException: 取页失败 页=3",
                    pagesOnDisk = 2, cancelled = false, queued = false,
                ),
            ),
        )
    }

    /**
     * 批 100 · 那枚「取消」把手：只在跑着的时候摆，按下去那句要改口。
     *
     * 两格各钉一件事：**不跑就不摆**（摆一枚按下去什么都不会发生的钮，就是本仓「灰行不许留在屏上」那一族的形状）；
     * **按下去立刻改口成「正在停手…」**（协作式取消要等到下一页边界才真停，屏上什么都不变就会被读成"我按了而它没反应"）。
     * ⚠️ 那句**不许**写成"已停止" —— 正在飞的那一笔请求掐不掉。
     */
    @Test
    fun 那一枚取消只在跑着的时候摆() {
        fun render(busy: Boolean, requested: Boolean) = runComposeUiTest {
            setContent {
                DesktopComicDetailContent(
                    state = ComicDetailState.Ready(
                        title = "某本",
                        description = "",
                        episodes = listOf(EngineSession.Episode("第 1 话", "a"), EngineSession.Episode("第 2 话", "b")),
                        cover = "https://detail.jpg",
                    ),
                    target = DesktopComicTarget("9", "某本", ""),
                    favoriteOutcome = null,
                    progress = null,
                    progressUnread = false,
                    download = DesktopComicChapterDownload(
                        chapterIndex = 0, written = 0, total = 5, busy = busy, failure = null,
                        pagesOnDisk = 2, cancelled = false, queued = false,
                    ),
                    cancelRequested = requested,
                    onBack = {},
                    onFavorite = {},
                    onOpenEpisode = {},
                    onDownloadEpisode = {},
                    onCancelDownload = {},
                )
            }
            val running = onAllNodesWithText(DESKTOP_CHAPTER_DOWNLOAD_CANCEL_LABEL).fetchSemanticsNodes().size
            val stopping = onAllNodesWithText(DESKTOP_CHAPTER_DOWNLOAD_CANCELLED_HINT).fetchSemanticsNodes().size
            assertEquals("跑着且没按：摆一枚「取消」", if (busy && !requested) 1 else 0, running)
            assertEquals("跑着且按过了：改口成「正在停手…」而不留「取消」", if (busy && requested) 1 else 0, stopping)
            assertTrue(
                "任何时候都不许念「已停止」（那一笔在飞的请求掐不掉）",
                onAllNodesWithText("已停止").fetchSemanticsNodes().isEmpty(),
            )
        }
        render(busy = true, requested = false)
        render(busy = true, requested = true)
        render(busy = false, requested = false)
    }
}
