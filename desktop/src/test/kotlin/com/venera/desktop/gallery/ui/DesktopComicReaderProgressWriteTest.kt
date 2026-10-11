package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.venera.desktop.gallery.data.DesktopReaderMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.venera.compose.data.db.HistoryRecord
import io.github.composefluent.component.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 82 · 「翻页真的会换那一笔的页号」——**行为层**判据（离屏渲 + 真 `scrollToItem`）。
 *
 * ## 为什么源码扫描那一层不够
 *
 * `DesktopComicHistoryWiringTest` 钉的是"页号从 `listState` 取、排在 `distinctUntilChanged` 之后"这类**形状**。
 * 形状全对而行为是零的失败形态真实存在：状态提错了那一枚、`LazyColumn` 拿不到有界高度而永远停在第 0 项、
 * 或者 `collect` 收的是effect 起手那一次的快照 —— 屏上一切正常，而库里那一行永远念 `页=0`。
 * 那一格只有"真滚一次"量得到，而真滚一次不需要窗口：离屏渲展示件 + 递同一枚 `LazyListState` 进去。
 *
 * ## 两档各钉一次
 *
 * 页表没到手时**一笔都不发**（发出去的是一个没发生过的进度）；到手之后起手一笔、滚到哪一页记到哪一页。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopComicReaderProgressWriteTest {

    private val reading = DesktopComicReading(
        comicId = "1461316",
        title = "某本漫画",
        episodeId = "g",
        episodeName = "第 1 话",
        placeholder = false,
        episodeIndex = 2,
        startPage = 0,
        coverUrl = "https://cdn.example/a.jpg",
    )

    private val ready = ComicReaderState.Ready(List(6) { index -> "p$index.jpg" })

    @Test
    fun 页表没到手时一笔都不发() {
        runComposeUiTest {
            val written = mutableListOf<HistoryRecord>()
            setContent {
                val listState = rememberLazyListState()
                DesktopComicReaderContent(
                    reading = reading,
                    state = ComicReaderState.Loading,
                    sourceKey = "jm",
                    listState = listState,
                    // 批 87 起那一屏有两面墙：这一组用例全程站在纵向那一档，pager 那面只是陪着存在
                    pagerState = rememberPagerState { 0 },
                    mode = DesktopReaderMode.VERTICAL_CONTINUOUS,
                    seedNote = null,
                    onModeChange = {},
                    onStep = {},
                    onBack = {},
                    pageImage = { index, _ -> Box(Modifier.fillMaxWidth().height(60.dp)) { Text("页位$index") } },
                )
                LaunchedEffect(listState) {
                    desktopWatchReaderProgress(
                        snapshotFlow { listState.firstVisibleItemIndex },
                        reading, "jm", ComicReaderState.Loading, { 1L },
                    ) { written += it }
                }
            }
            waitForIdle()
            assertEquals("等回话那一档写下去的就是一个没发生过的进度", 0, written.size)
        }
    }

    /**
     * 离屏渲那一屏，起点摆在第 [start] 页（`rememberLazyListState(initialFirstVisibleItemIndex = …)`），
     * 然后把写口收到的行收进 [written]。
     *
     * ⚠️ **本批没量到那一格，写在这里而不是假装量过**：第一版这条判据想钉"滚到哪一页就记到哪一页"，
     * 两种起手都试过了 —— 真 `scrollToItem(3)` 之后 `firstVisibleItemIndex` 仍是 0，
     * 换 `initialFirstVisibleItemIndex = 3` 也是 0（而 `visibleItemsInfo` 有货 ⇒ 不是没渲出来）。
     * 也就是说**离屏那一屏的列表状态不反映滚动**，拿它量"跟不跟手"会恒绿，是一把不动的尺子。
     * 真滚一次要 OS 级输入，那一条被明令禁掉（不动用户光标与前台）。
     * 于是这一层只留两档量得到的：页表没到手时一笔都不发、到手之后那一笔的**字段**逐项落对；
     * "页号从 `listState` 取而不是写死 0"由 `DesktopComicHistoryWiringTest` ① 钉形状，
     * 而**真跑那一页的读数当天就量到了**（同日批 82.5）：`--read-page=7` 起窗念
     * `D_阅读 起点 请求=7 量到=7 页表=89 试=8` → `D_历史 写 … 页=7/89` → 库里 `last_page_index=7`，
     * 也就是说那一笔跟着页号走、不是假开关。五趟对照与"为什么只能重试"在
     * `docs/rounds/desktop-reader-start-page-2026-10-10.md` §2；本颗件钉的两档仍然各自有效。
     */
    private fun androidx.compose.ui.test.ComposeUiTest.composeAt(start: Int, written: MutableList<HistoryRecord>) {
        setContent {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
            DesktopComicReaderContent(
                reading = reading,
                state = ready,
                sourceKey = "jm",
                listState = listState,
                // 同上：这一组量的是纵向那一档的页号流（横向那档由 DesktopReaderPagingTest 那一边量）
                pagerState = rememberPagerState { ready.pages.size },
                mode = DesktopReaderMode.VERTICAL_CONTINUOUS,
                seedNote = null,
                onModeChange = {},
                onStep = {},
                onBack = {},
                pageImage = { index, _ -> Box(Modifier.fillMaxWidth().height(60.dp)) { Text("页位$index") } },
            )
            LaunchedEffect(listState) {
                desktopWatchReaderProgress(
                    snapshotFlow { listState.firstVisibleItemIndex },
                    reading, "jm", ready, { 1L },
                ) { written += it }
            }
        }
        // 落库那一半走 `withContext(NonCancellable + Dispatchers.IO)`，**不在渲染那一根线上**，
        // 所以 `waitForIdle` 回来时那一笔可能还在 IO 上 —— 轮询等它，而不是把"我还没等到"读成"它没发"。
        val deadline = System.currentTimeMillis() + 4_000
        while (written.isEmpty() && System.currentTimeMillis() < deadline) {
            waitForIdle()
            mainClock.advanceTimeBy(20)
        }
        waitForIdle()
    }

    @Test
    fun 到手那一笔的字段逐项落对() {
        val written = mutableListOf<HistoryRecord>()
        runComposeUiTest {
            composeAt(0, written)
            assertEquals("页表到手那一笔当场就该发（不等用户翻页）", 1, written.size)
            val rec = written[0]
            assertEquals("漫画号与题名从载荷来", "某本漫画", rec.title)
            assertEquals("1461316", rec.comicId)
            assertEquals("章号吃载荷那一个而不是恒 0", 2, rec.lastChapterIndex)
            assertEquals("第 1 话", rec.lastChapterTitle)
            assertEquals("总页数跟着页表走", 6, rec.totalPages)
            assertEquals("源名从调用点来", "jm", rec.sourceName)
            assertEquals("时钟由调用点递进来", 1L, rec.updatedAt)
            assertEquals("author 留空 —— 对岸那一段就这样写（阅读器手里没有作者）", "", rec.author)
            assertEquals(
                "封面从载荷一路流到那一笔（批 82.6：这一格两端共用，链路断在这里就是手机侧永久空白框）",
                "https://cdn.example/a.jpg",
                rec.coverUrl,
            )
        }
    }
}
