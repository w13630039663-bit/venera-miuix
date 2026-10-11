package com.venera.desktop.gallery.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 82 · 写口那颗件的**行为**判据（把一行交出去、把读数念回来）。
 *
 * 为什么单独一颗：`DesktopComicHistoryTest` 钉的是"字段形状对不对岸"，这里钉的是"这一次调用到底
 * 发没发出去、发出去的是不是这一行、没发出去时屏上念的是哪一句"。三者错任一件都会做成
 * 「屏上念了『写了』而库里没有那行」这种假读数 —— 那正是这个项目反复挨打的那一类。
 */
class DesktopComicHistoryWriterTest {

    private fun progress(page: Int = 7, total: Int = 89) = DesktopReadingProgress(
        comicId = "1461316",
        title = "某本漫画",
        sourceKey = "jm",
        chapterTitle = "第 2 话",
        chapterIndex = 1,
        pageIndex = page,
        totalPages = total,
        coverUrl = "",
    )

    @Test
    fun 写成功时交出去的就是那一行且读数念写() {
        val written = mutableListOf<com.venera.compose.data.db.HistoryRecord>()
        val line = runBlocking {
            desktopWriteReadingHistory(progress(), nowMillis = 1_700_000_000_000L) { written += it }
        }
        assertEquals("一次调用只发一笔", 1, written.size)
        assertEquals("交出去的就是这一本", "1461316", written[0].comicId)
        assertEquals("页号原样落到那一行", 7, written[0].lastPageIndex)
        assertEquals("D_历史 写 源=jm 号=1461316 章=1 页=7/89", line)
    }

    @Test
    fun 库里炸了不把阅读那一屏带崩而是念没写成() {
        // 这一档是真会发生的：`comic_history` 那棵树在旧档上可能压根没建（DesktopDatabasePorts 只保证连接开得了）。
        // 写历史是**顺带**的事，它炸了不许把正在看的那一屏一起带走。
        val line = runBlocking {
            desktopWriteReadingHistory(progress(page = 3, total = 40), nowMillis = 1L) {
                throw IllegalStateException("no such table: comic_history")
            }
        }
        assertTrue("失败要念成没写成：$line", line.startsWith("D_历史 没写成"))
        assertTrue("成因上屏：$line", line.contains("因=IllegalStateException: no such table: comic_history"))
        assertTrue("仍说清是哪一本：$line", line.contains("号=1461316"))
    }

    @Test
    fun 取消不是写失败那一档() {
        // 兜住 `catch (Throwable)` 最容易犯的错：把上游的取消当成"没写成"念出来，
        // 于是收掉的那一笔会被当成一次数据库故障（读数还在，而活儿确实没干）。
        var thrown: Throwable? = null
        try {
            runBlocking { desktopWriteReadingHistory(progress(), nowMillis = 1L) { throw CancellationException("上游收了") } }
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("取消要原样抛出去，不折成失败读数：$thrown", thrown is CancellationException)
    }

    @Test
    fun 写这一笔走的是对岸那一对上下文() {
        // 对岸那句是 `withContext(NonCancellable + Dispatchers.IO)`：退出时组合被收掉，
        // 带 NonCancellable 才写得完最后这一笔；少了它"退出阅读时那一笔"就会十次里丢几次。
        val body = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicHistory.kt"))
        val at = body.indexOf("withContext(")
        assertTrue("写口里必须有那一层上下文", at >= 0)
        val expression = body.substring(at, minOf(body.length, at + 60))
        assertTrue("两样都要，次序按对岸那句：$expression", expression.contains("NonCancellable + Dispatchers.IO"))
    }

    @Test
    fun 读数那颗件没有内部时钟() {
        // 反向钉：这颗文件里出现 `System.currentTimeMillis()` 就等于"内部取时钟"回来了 ——
        // 那时上面那几条判等全都白写（两次调用差几毫秒就红，而红会被读成判据不稳）。
        val writer = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicHistory.kt")
        val code = DesktopSourceTree.codeText(writer)
        assertEquals(
            "时钟只许由调用方递进来（nowMillis 实参）",
            0,
            code.lines().count { it.contains("System.currentTimeMillis()") },
        )
    }
}
