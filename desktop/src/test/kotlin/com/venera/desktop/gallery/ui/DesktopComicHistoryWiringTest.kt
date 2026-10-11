package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 82 · 历史写口的**接线**判据（源码扫描那一族）。
 *
 * ## 为什么这一族必须钉顺序与次数
 *
 * 写历史最容易做成"看着接好了"：`LaunchedEffect` 挂了、`saveHistory` 调了、stdout 也念了，
 * 而顶部那一页从来没进过那笔（写的一直是 `页=0`）—— 屏上一切正常，库里那一行永远停在第一页。
 * 所以这里不钉"有没有出现某个符号"，钉的是**从哪一层取页号、几次、按什么次序**。
 *
 * ## 一条不许漂的口径
 *
 * 时钟在**消费点**取（`System.currentTimeMillis()` 只许出现在 `gallery/ui/`，一颗 `History` 件里恒 0 次）；
 * 读数在消费点 println。这两条把"可判等"这件事留在纯那一层，把"真时间"留在真那一层。
 */
class DesktopComicHistoryWiringTest {

    private val reader = DesktopSourceTree.desktopUiSource("DesktopComicReader.kt")
    private val readerCode = DesktopSourceTree.codeText(reader)
    private val detail = DesktopSourceTree.desktopUiSource("DesktopComicDetail.kt")
    private val pane = DesktopSourceTree.desktopUiSource("DesktopComicPane.kt")
    private val app = DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt")

    @Test
    fun 顶部那一页是从列表状态里取的而不是恒零() {
        val flowAt = readerCode.indexOf("snapshotFlow { listState.firstVisibleItemIndex }")
        assertTrue("装载那一层要盯住顶部页号：$flowAt", flowAt >= 0)
        val distinctAt = readerCode.indexOf(".distinctUntilChanged()", flowAt)
        val writeAt = readerCode.indexOf("desktopWriteReadingHistory(", flowAt)
        assertTrue("同一页不重复发（翻页才有新的一笔）", distinctAt in flowAt until writeAt)
        assertTrue("取到页号之后才交写口", writeAt > flowAt)
    }

    @Test
    fun 列表状态挂在装载那一层而不是展示那一层() {
        // 展示件里新建 state 的话，装载那一层**拿不到**顶部页号（两颗各自 remember 出两枚 state）。
        assertEquals("rememberLazyListState 只许出现在装载那一件", 1, DesktopSourceTree.codeOccurrences(reader, "rememberLazyListState("))
        val contentAt = readerCode.indexOf("internal fun DesktopComicReaderContent")
        assertTrue("展示那一层不许自己造状态：${readerCode.substring(contentAt, contentAt + 200)}",
            !readerCode.substring(contentAt).contains("rememberLazyListState("))
        assertTrue("展示件收一枚现成的列表状态", readerCode.contains("listState: LazyListState"))
    }

    @Test
    fun 章号从载荷一路带到库里那一行() {
        // 对岸那一列写的是"这一话在章表里的第几行"。手机侧读它来续读，桌面填错就等于把另一端的进度挪走。
        assertTrue("载荷里必须带行号", DesktopSourceTree.codeOccurrences(reader, "val episodeIndex: Int") == 1)
        assertTrue(
            "写口吃的是载荷那一个而不是恒 0：${Regex("chapterIndex = [^,\\n]+").find(readerCode)}",
            Regex("chapterIndex = reading\\.episodeIndex").containsMatchIn(readerCode),
        )
        assertEquals(
            "详情那一屏的每一个可点行都要把**自己那一行**交出去",
            1,
            // 锚点**不带闭括号**：那一行还会继续添实参（批 82.6 就添了封面），钉到 `)` 等于把合法生长红成误伤（坑 ㉖）。
            DesktopSourceTree.codeOccurrences(detail, "desktopComicReadingOf(target, episode, index, startPage = 0"),
        )
    }

    @Test
    fun 读数在消费点念而不是藏进写口() {
        val printlnAt = readerCode.indexOf("println(")
        val writeAt = readerCode.indexOf("desktopWriteReadingHistory(")
        assertTrue("阅读那一屏念这一笔：$printlnAt / $writeAt", printlnAt in 0 until writeAt)
        assertEquals(
            "时钟只在消费点取（纯件里那颗判据钉它 0 次）",
            1,
            DesktopSourceTree.codeOccurrences(reader, "System.currentTimeMillis()"),
        )
    }

    @Test
    fun 历史那颗件与收藏同一口径懒装载并接到应用根() {
        assertEquals(
            "config 里一枚懒装载的口子",
            1,
            DesktopSourceTree.codeOccurrences(pane, "val history: () -> HistoryDao,"),
        )
        assertEquals(
            "应用根把真口子接上去（漏这一跳就是「不报错也不做事」，同一颗纪律第八次）",
            1,
            DesktopSourceTree.codeOccurrences(app, "history = ::history,"),
        )
        assertTrue(
            "取 DAO 走桌面那颗端口，不吃 Android 的 context",
            DesktopSourceTree.codeOccurrences(app, "HistoryDao.getInstance(DesktopDatabasePorts.PLATFORM)") == 1,
        )
    }
}
