package com.venera.desktop.gallery.data

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 100 · 整话下载的**逐页进度**与**协作式取消**（数据层那八颗）。
 *
 * ## 这一格补的是哪句假话
 *
 * 批 96 那颗 runner 只在**整话跑完**之后被念一次：89 页那一趟从头到尾屏上都是上一句，中途既没有进度
 * 也没有一处能停手（现量：`DesktopComicChapterDownload.kt:158` 那一圈 `for (index in 0 until expectedPages)`
 * 里一句进度都没有）。而 b96d1 那一帧量到的正是最坏的那一幕 —— 第 63 页被站方 `stream was reset: CANCEL` 掐断，
 * 用户等了半天只得到一句失败，而**盘上其实已经有 62 页**（下一趟会补，可屏上没说这句话）。
 *
 * ## 两条口径是这批的硬边界
 *
 * - **取消不许静默交错**：那一档落 `complete=false` 而**页一颗都不删**（清用户的东西是另一种错），
 *   并且 runner 自己交回 `Cancelled(written)` —— 绝不让调用点"猜"这一趟到底是跑完还是被停（对岸那颗
 *   `LocalComicManager.kt:302` 丢掉 `Boolean` 再无条件说"已删除"是同一族的反面教材，批 98 已逐行证实）。
 * - **进度那一格不许自己判齐不齐**：判齐只有 `desktopComicChapterIsComplete` 那一颗（批 96 立、批 97 复用、
 *   批 99 也只吃它算出的那一格）。所以进度念的是"写到第几页"，而"齐了没"仍然只有那一判说了算 ——
 *   这条由 ⑥ 与 ③ 两侧一起钉（起手那一格吃的是**盘上已有的页数**，而它正是那颗判数出来的）。
 */
class DesktopComicChapterProgressTest {

    private fun tempRoot(): File = Files.createTempDirectory("b100dl").toFile()

    /** 一枚"永远不停手"的取消闸，好让不关心取消的那几颗只喂一种值。 */
    private val never: () -> Boolean = { false }

    /** 收进度那几格的记录器（② 要数枚数，所以不能只留最后一格）。 */
    private class Recorder {
        val seen = mutableListOf<Pair<Int, Int>>()
        val progress: suspend (Int, Int) -> Unit = { written, expected ->
            seen += written to expected
        }
    }

    /** ① 续跑那一趟的**起手**那一格念的是盘上已有的页数，而不是 0（写 0 就是把"你已经下了 62 页"藏起来）。 */
    @Test
    fun 起手那一格念的是盘上已有的页数() {
        val dir = File(tempRoot(), "resume").apply { mkdirs() }
        (0 until 2).forEach { DesktopComicPageFixtures.writeWholePage(dir, it) }
        val rec = Recorder()
        runBlocking {
            desktopComicDownloadChapter(
                dir, comicId = "9", chapterId = "1461318", order = 0, title = "t", expectedPages = 4,
                shouldCancel = never, progress = rec.progress,
            ) { index -> DesktopComicPageFixtures.stubPageBytes(index) }
        }
        assertEquals("起手那一格：", 2 to 4, rec.seen.first())
        assertEquals("最后一格该走到满：", 4 to 4, rec.seen.last())
    }

    /** ② 每写一页各念一次，而枚数 = 缺的那几页 + 起手那一格（少一格就是中途没吭声）。 */
    @Test
    fun 每写一页各念一次() {
        val dir = File(tempRoot(), "each").apply { mkdirs() }
        val rec = Recorder()
        runBlocking {
            desktopComicDownloadChapter(
                dir, comicId = "9", chapterId = "1461318", order = 1, title = "t", expectedPages = 3,
                shouldCancel = never, progress = rec.progress,
            ) { index -> DesktopComicPageFixtures.stubPageBytes(index) }
        }
        assertEquals("整趟进度念了几格：", listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), rec.seen)
    }

    /** ③ 取消那一档：交回 `Cancelled`、清单说不齐、**页一颗不少**（屏上那句"下次接着补"的前提就在这）。 */
    @Test
    fun 取消那一档落不齐而页全留() {
        val dir = File(tempRoot(), "cancel").apply { mkdirs() }
        var stop = false
        val rec = Recorder()
        val run = runBlocking {
            desktopComicDownloadChapter(
                dir, comicId = "9", chapterId = "1461318", order = 2, title = "t", expectedPages = 6,
                shouldCancel = { stop }, progress = rec.progress,
            ) { index ->
                if (index == 2) stop = true
                DesktopComicPageFixtures.stubPageBytes(index)
            }
        }
        assertEquals("取消那一档交回的：", DesktopComicChapterRun.Cancelled(written = 3), run)
        assertFalse("取消之后清单不许说齐：", desktopComicChapterIsComplete(dir, 6))
        assertEquals("盘上留下的页数（一颗都不许多、一颗都不许少）：",
            3, (0 until 6).count { File(dir, desktopComicChapterPageFileName(it)).isFile })
        assertEquals("进度最后一格念的是取消时已写下的那一格：", 3 to 6, rec.seen.last())
    }

    /** ④ 取消之后**一页都不许多取**（停了还继续问站方就是要花用户的钱与时间）。 */
    @Test
    fun 取消之后一页都不许多取() {
        val dir = File(tempRoot(), "stopfetch").apply { mkdirs() }
        var stop = false
        var fetched = 0
        runBlocking {
            desktopComicDownloadChapter(
                dir, comicId = "9", chapterId = "1461318", order = 0, title = "t", expectedPages = 9,
                shouldCancel = { stop }, progress = { _, _ -> },
            ) { index ->
                if (index == 1) stop = true
                fetched += 1
                DesktopComicPageFixtures.stubPageBytes(index)
            }
        }
        assertEquals("取消之后还调用取页的次数：", 2, fetched)
        assertEquals("盘上写下的页数：", 2, (0 until 9).count { File(dir, desktopComicChapterPageFileName(it)).isFile })
    }

    /** ⑤ 跑完那一档交回 `Completed` 而清单说齐 —— 两档必须**各是各的**，不许都塌成同一个形状。 */
    @Test
    fun 跑完那一档交回跑完而清单说齐() {
        val dir = File(tempRoot(), "done").apply { mkdirs() }
        val run = runBlocking {
            desktopComicDownloadChapter(
                dir, comicId = "9", chapterId = "1461318", order = 0, title = "t", expectedPages = 2,
                shouldCancel = never, progress = { _, _ -> },
            ) { index -> DesktopComicPageFixtures.stubPageBytes(index) }
        }
        assertEquals("跑完那一档交回的：", DesktopComicChapterRun.Completed(written = 2), run)
        assertTrue("跑完之后清单要说齐：", desktopComicChapterIsComplete(dir, 2))
    }

    /** ⑥ 已经齐的那一趟：`Completed(0)`、进度只念**一格**而那一格是满的（念 `0/3` 就是把"早就下全了"说成"一页都没有"）。 */
    @Test
    fun 已经齐的那一趟只念一格而念的是满的() {
        val dir = File(tempRoot(), "already").apply { mkdirs() }
        (0 until 2).forEach { DesktopComicPageFixtures.writeWholePage(dir, it) }
        desktopComicWriteChapterManifest(dir, chapterId = "1461318", order = 0, title = "t", expectedPages = 2, complete = true)
        val rec = Recorder()
        var fetched = 0
        val run = runBlocking {
            desktopComicDownloadChapter(
                dir, comicId = "9", chapterId = "1461318", order = 0, title = "t", expectedPages = 2,
                shouldCancel = never, progress = rec.progress,
            ) { index ->
                fetched += 1
                DesktopComicPageFixtures.stubPageBytes(index)
            }
        }
        assertEquals("已经齐的那一趟写的页数：", DesktopComicChapterRun.Completed(written = 0), run)
        assertEquals("已经齐的那一趟取页的次数：", 0, fetched)
        assertEquals("已经齐的那一趟进度念了几格：", listOf(2 to 2), rec.seen)
    }

    /** ⑦ 进度那一句四格齐全，两枚数各喂两种值（"值由调用点给"的参数只喂一种等于没测）。 */
    @Test
    fun 进度那一句四格齐全() {
        assertEquals(
            "中途那一格：", "D_下载进度 号=9 话=3 页=2/89",
            desktopComicChapterProgressLine("9", 3, 2, 89),
        )
        assertEquals(
            "起手那一格（零也要念得出来）：", "D_下载进度 号=1461316 话=0 页=0/1",
            desktopComicChapterProgressLine("1461316", 0, 0, 1),
        )
    }

    /** ⑧ 取消那一句念得出"接着补"是从第几页，而它**不许**出现在"已下载"那一形里。 */
    @Test
    fun 取消那一句念的是接着补而不是已下载() {
        val line = desktopComicChapterCancelledLine("9", 3, 62, 89)
        assertEquals("整串：", "D_下载取消 号=9 话=3 页=62/89 下次=第 63 页", line)
        assertFalse("取消那句不许说已下载：" + line, line.contains("已下载"))
        val zero = desktopComicChapterCancelledLine("9", 0, 0, 5)
        assertTrue("一页都没写下时要念「下次=第 1 页」：" + zero, zero.contains("下次=第 1 页"))
    }

    /**
     * ⑨ 取消那一趟写的清单，**字面上**那一格必须是 `"complete":false`（台架 N1 跑出绿之后补的）。
     *
     * 为什么 ③ 那把不够：③ 钉的是 `desktopComicChapterIsComplete(dir, 6)` 交回假，而那一判**两样都看**
     * （清单那一格 + 逐页查盘）—— 只把清单那一格改成 `true` 时它仍然因为页缺着而交回假 ⇒ 一把尺子内自带冗余闸
     * （批 96 台架 P5 那一课的同形）。而这一格是要写给**对岸**看的（它拿 `complete` 与页数一起判"要不要重下"），
     * 所以钉的是文件原文那一串，不是那颗判的返回值。同理"取消那一档干脆不写清单"也要红在这里。
     */
    @Test
    fun 取消那一趟写的清单字面上说不齐() {
        val dir = File(tempRoot(), "manifest").apply { mkdirs() }
        var stop = false
        runBlocking {
            desktopComicDownloadChapter(
                dir, comicId = "9", chapterId = "1461318", order = 4, title = "t", expectedPages = 5,
                shouldCancel = { stop }, progress = { _, _ -> },
            ) { index ->
                if (index == 1) stop = true
                DesktopComicPageFixtures.stubPageBytes(index)
            }
        }
        val manifest = File(dir, "chapter.json")
        assertTrue("取消那一趟要在盘上留下清单（不写就等于让下一趟猜）：", manifest.isFile)
        val text = manifest.readText()
        assertTrue("清单那一格字面上必须是 `\"complete\":false`：" + text, text.contains("\"complete\":false"))
        assertFalse("取消那一趟的清单不许说齐：" + text, text.contains("\"complete\":true"))
    }

    /** ⑩ 续跑那一趟不重取已有的那几页（台架 N19 第一遍跑出绿之后补的尺子）。 */
    @Test
    fun 续跑那一趟不重取已有的那几页() {
        val dir = File(tempRoot(), "skip").apply { mkdirs() }
        (0 until 2).forEach { DesktopComicPageFixtures.writeWholePage(dir, it) }
        val fetched = mutableListOf<Int>()
        val run = runBlocking {
            desktopComicDownloadChapter(
                dir, comicId = "9", chapterId = "1461318", order = 0, title = "t", expectedPages = 4,
                shouldCancel = never, progress = { _, _ -> },
            ) { index ->
                fetched += index
                DesktopComicPageFixtures.stubPageBytes(index)
            }
        }
        assertEquals("只该问缺的那两页：" + fetched, listOf(2, 3), fetched)
        assertEquals("这一趟新写的页数：" + run, DesktopComicChapterRun.Completed(written = 2), run)
        assertTrue("盘上那两页还在：" + File(dir, "0002.jpg").length(), File(dir, "0002.jpg").isFile)
    }
}
