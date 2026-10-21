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

    /**
     * 批 81 那一格的入口：章节表里的一行**按下去该进阅读那一屏**。
     *
     * 这一条为什么不能只靠"源码里挂了 clickable"：批 80 之前那一行就是死的，而屏上写着「第 1 话」
     * —— 文本在、行在、什么都在，缺的只有那一下回调。语义树里按一次才量得到"接上了"。
     */
    /**
     * 批 94 · 那枚主胶囊的三档（有历史 / 没历史 / 库没读到），**按一次**量它落到哪儿。
     *
     * 为什么必须在这一层量：`DesktopComicContinueReadingTest` 那几颗纯函数判据只证明"算式对"，
     * 而本批立的正是"不许摆着「继续阅读」而按下开第一章第 0 页"（对岸自己就是那一形）。
     * 那句只有语义树里按一次才量得到 —— 批 81 那一课：屏上写着「第1话」而那一行是死的。
     */
    /**
     * 库没读到那一档：**两档胶囊都不摆**。
     *
     * 这一条是"降级路径宁可错慢不可静默交错"落在屏上的形状：那一档若摆「开始阅读」，
     * 按下就把 `comic_history`（**两端共用那张表**）里"下次从第 40 页读"写成第一话第 0 页 ——
     * 手机上那半本进度会被桌面上的一个按钮挪走。少一枚钮是可见的慢，挪走进度是静默的错。
     */
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
    /** 批 96 · 那一行状态只挂在**被长按的那一行**上（三行同屏时不许三行一起念同一句进度）。 */
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
}
