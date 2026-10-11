package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 82 · 阅读那一屏的**历史写口**（纯判据）。
 *
 * ## 为什么这一颗必须逐字回核对岸那一段
 *
 * `comic_history` 是**两端共用的一张表**（`data/db` 整目录在桌面编译面上）。同一行数据两端各写一种形状，
 * 表现就是"手机上看到的阅读进度在桌面丢了"或反过来 —— 而这张表没有版本号也没有迁移，漂了不会红。
 * 所以这里不"设计"字段语义，只**回核**对岸 `VeneraReaderScreen.kt` 那一处 `saveHistory(` 的每一个实参。
 *
 * ## 两处最容易自己发明而又不许发明的地方
 *
 * - `author`：对岸**恒留空串**（阅读器那一屏手里没有作者）。桌面要是"顺手补一个作者名"，
 *   补的是猜出来的值 —— 那是把假数据写进共享表，比空着严重。
 * - `updatedAt`：时钟由调用方递进来而不是在这颗件里 `System.currentTimeMillis()`。
 *   不是洁癖：内部取时钟的件**没法判等**（两次调用差几毫秒就红），而那笔账要的正是"同一输入同一行"。
 *
 * ⚠️ `coverUrl` 这一格在批 82 被钉成"恒空串"，理由是"阅读那一屏手里没有地址"—— 那句**量过是假的**
 * （卡上、库里、`loadInfo` 三处都有），于是这一条锚点把一句错诊断冻成了规格（手册 §0 坑 ㉖ 第十三次）。
 * 批 82.6 改判：这一格吃载荷里那枚地址，链路那一半的尺子在 `DesktopComicCoverChainTest`。
 */
class DesktopComicHistoryTest {

    private fun progress(
        page: Int = 7,
        total: Int = 89,
        chapter: Int = 1,
        sourceKey: String = "jm",
        title: String = "某本漫画",
        coverUrl: String = "https://cdn.example/a.jpg",
    ) = DesktopReadingProgress(
        comicId = "1461316",
        title = title,
        sourceKey = sourceKey,
        chapterTitle = "第 2 话",
        chapterIndex = chapter,
        pageIndex = page,
        totalPages = total,
        coverUrl = coverUrl,
    )

    @Test
    fun 字段逐个照对岸那一处写口() {
        val record = desktopHistoryRecord(progress(), nowMillis = 1_700_000_000_000L)
        assertEquals("漫画号原样", "1461316", record.comicId)
        assertEquals("题名吃卡上那个", "某本漫画", record.title)
        assertEquals(
            "author 恒空 —— 对岸那一段写的就是 `author = \"\"`（阅读器手里没有作者）；" +
                "桌面补一个猜出来的名字 = 把假数据写进两端共用的表",
            "",
            record.author,
        )
        assertEquals("封面那一格原样吃载荷里那枚地址", "https://cdn.example/a.jpg", record.coverUrl)
        assertEquals("源名原样落库", "jm", record.sourceName)
        assertEquals("上次那话的题名照递", "第 2 话", record.lastChapterTitle)
        assertEquals(1, record.lastChapterIndex)
        assertEquals(7, record.lastPageIndex)
        assertEquals(89, record.totalPages)
        assertEquals("时钟由调用方递进来（内部取时钟的件没法判等）", 1_700_000_000_000L, record.updatedAt)
    }

    @Test
    fun 源名空时退成对岸那一个兜底名() {
        val counterpart = DesktopSourceTree.androidSource("reader/VeneraReaderScreen.kt").readText()
        val fallback = Regex("""sourceName\.ifBlank \{ "([^"]+)" }""").find(counterpart)
            ?: error("对岸那处写口不再带兜底名了（这一条要跟着改判，而不是桌面自己定一个）")
        assertEquals(
            "桌面必须用对岸那一个兜底名，不许另造一句",
            fallback.groupValues[1],
            desktopHistoryRecord(progress(sourceKey = "   "), nowMillis = 1_700_000_000_000L).sourceName,
        )
    }

    @Test
    fun 零页那一话照对岸写下去而不是悄悄不写() {
        // 对岸不夹这一档（页表回 0 也照样落一笔）。桌面**跟着对岸**，不自己定新语义（批 10 那条教训：
        // "想改得更聪明"会在两端造出两套行为，而这张表是共用的）。
        val record = desktopHistoryRecord(progress(page = 0, total = 0), nowMillis = 1_700_000_000_000L)
        assertEquals(0, record.lastPageIndex)
        assertEquals("零页也要落得下去（不写=屏上那句「回了 0 页」与库里那行从此对不上）", 0, record.totalPages)
    }

    @Test
    fun 写那一笔的读数一行说清写到哪() {
        assertEquals(
            "D_历史 写 源=jm 号=1461316 章=1 页=7/89",
            desktopComicHistoryReading(progress(), null),
        )
        val failed = desktopComicHistoryReading(progress(), "IllegalStateException: no such table")
        assertTrue("失败那一句必须带成因（只念「写了」是假读数）：$failed", failed.contains("因="))
        assertTrue("失败那一句仍要说清是哪一本：$failed", failed.contains("号=1461316"))
        assertEquals("读数不许带换行（两行会把证据送到抓读数脚本看不见的地方）", 1, failed.lines().size)
    }

    @Test
    fun 零页与脏页号都照实念出不折成百分比() {
        // `totalPages` 来自页表长度，而页表可能回 0（对岸那一话也可能被裁过）。
        // 读数里那一格写成 `页=0/0` 或 `页=-1/0` 是**实话**，写成 `0%` / `NaN%` 才是把脏数据粉饰掉。
        assertEquals(
            "D_历史 写 源=jm 号=1461316 章=0 页=0/0",
            desktopComicHistoryReading(progress(page = 0, total = 0, chapter = 0), null),
        )
        assertEquals(
            "负页号原样念（夹一层 max(0,..) 会让「库里那行」与屏上那一格从此对不上）",
            "D_历史 写 源=jm 号=1461316 章=0 页=-1/0",
            desktopComicHistoryReading(progress(page = -1, total = 0, chapter = 0), null),
        )
    }
}
