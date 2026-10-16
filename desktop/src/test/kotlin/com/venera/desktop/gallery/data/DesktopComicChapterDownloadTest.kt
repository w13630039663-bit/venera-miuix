package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 批 96 · 漫画「把一整话存到本机」那一格的**命名与落盘口径**（对岸 `download/DownloadManager.kt` 逐字回核）。
 *
 * ## 题面不是"想加个下载"
 *
 * `README.md:253` 与 `README.en.md:253` 那句写着：图片落盘已接（批 85），而**「把一整话存到本机」还没接**。
 * 桌面今天有的：`desktopSaveImageRun`（只吃 `GalleryPost`，画廊那一侧）、`DesktopDownloadsPane(saveDir: File)`
 * （只数 `数据根/downloads/图库` 那一个目录里的散文件）、`engine.pages(comicId, Episode(...))`（只在阅读那一屏的
 * `LaunchedEffect` 里 inline 用了一次，`gallery/ui/DesktopComicReader.kt:291-292`）。
 * 缺的正是中间那一颗：**取一整话的页序列 → 逐页落盘 → 判"这一话齐不齐"**。
 *
 * ## 为什么先把命名口径钉死
 *
 * 本仓那一族反复踩的坑是"两端各写一套目录名 ⇒ 桌面下过的对岸认不出、反之亦然"。对岸那三行净化与两行拼名是
 * **用户看得见的东西**（盘上目录树），所以逐字回核：对岸改了命名，桌面会红在这里，
 * 而不是红在"用户发现同一本漫画在盘上有两个目录"。
 *
 * ⚠️ 对岸那三处净化**不一致**：`sourceKey` / `comicId` 那两行不 `.trim()`，而章节标题那一行 `.trim()`。
 * 桌面**照抄**而不"顺手统一"（统一就是两端不同轨），① 把这条不一致钉成读数。
 */
class DesktopComicChapterDownloadTest {

    private fun tempRoot(): File = Files.createTempDirectory("b96dl").toFile()

    /** ① 净化正则**逐字回核**对岸那三行，并真跑：非法字符全替 `_`；标题那档多一步 trim 而 key 那档没有。 */
    @Test
    fun 净化那三行逐字照对岸而标题多一步去空白() {
        val android = DesktopSourceTree.androidSource("download/DownloadManager.kt").readText()
        val sanitize = Regex("Regex\\(\"\\[/\\\\\\\\:\\\\*\\\\?\\\\\"<>\\\\|\\]\"\\), \"_\"")
        val hits = sanitize.findAll(android).toList()
        assertEquals(
            "对岸那句净化（`replace(Regex(\"[/\\\\:*?\\\"<>|]\"), \"_\")`）在 `DownloadManager.kt` 里该出现三处" +
                "（sourceKey / comicId / 章节标题），少一处或多一处都说明它改了写法而桌面没跟：",
            3,
            hits.size,
        )
        val raw = "a/b\\c:d*e?f\"g<h>i|j"
        assertEquals("key 那一档逐字符替：" + raw, "a_b_c_d_e_f_g_h_i_j", desktopComicSafePathKey(raw))
        assertEquals("标题那一档同样替（中间没有空白时两档同值）：", "a_b_c_d_e_f_g_h_i_j", desktopComicSafeChapterTitle(raw))
        assertEquals("标题那一档首尾空白去掉（对岸多这一步）：", "第 3 话", desktopComicSafeChapterTitle("  第 3 话  "))
        assertEquals(
            "key 那一档**不**去空白 —— 这一条钉的是对岸的不一致，桌面照抄而不统一（统一了就会与对岸写出的目录名不同轨）：",
            "  jm  ", desktopComicSafePathKey("  jm  "),
        )
    }

    /** ② 章节目录名：话号补零四位 + `_` + 净化后的标题；补零**不截断**（对岸 `padStart` 的语义）。 */
    @Test
    fun 章节目录名补零四位而不截断() {
        assertEquals("第 0 话那一档：", "0000_第 1 话", desktopComicChapterDirName(0, "第 1 话"))
        assertEquals("第 12 话那一档：", "0012_第 13 话", desktopComicChapterDirName(12, "第 13 话"))
        assertEquals(
            "超过四位的那一档照原样（`padStart` 只补不截 —— 写成 take(4) 就会把后面的页混进同一目录）：",
            "12345_x", desktopComicChapterDirName(12345, "x"),
        )
        assertEquals("标题里的路径分隔符进不了目录名：", "0001_a_b", desktopComicChapterDirName(1, "a/b"))
    }

    /** ③ 页文件名：库里 0 基数 ⇒ 名从 1 数起、补零四位，而扩展名**恒 `.jpg`**（对岸那句就是写死的）。 */
    @Test
    fun 页名从一数起补零四位而扩展名恒为jpg() {
        assertEquals("第 0 页那一档：", "0001.jpg", desktopComicChapterPageFileName(0))
        assertEquals("第 998 页那一档：", "0999.jpg", desktopComicChapterPageFileName(998))
        assertEquals("第 999 页那一档（跨过四位）：", "1000.jpg", desktopComicChapterPageFileName(999))
        val android = DesktopSourceTree.androidSource("download/DownloadManager.kt").readText()
        assertTrue(
            "对岸那句页名写法变了（`(idx + 1).toString().padStart(4, '0')` + `.jpg`），桌面要跟着改而不是自己加扩展名判断：",
            Regex("idx \\+ 1\\)\\.toString\\(\\)\\.padStart\\(4, '0'\\)\\}\\.jpg").containsMatchIn(android),
        )
    }

    /** ④ 目录四段拼接：`根/<净化 sourceKey>/<净化 comicId>/<章节目录名>`（对岸 `getComicDir` 那两行 + 那一行 chDir）。 */
    @Test
    fun 目录四段拼接与对岸同形() {
        val root = tempRoot()
        val dir = desktopComicChapterDir(root, "j/m", "146:1316", 3, " 第 4 话 ")
        val expected = File(File(File(root, "j_m"), "146_1316"), "0003_第 4 话")
        assertEquals("四段一条不多一条不少：", expected, dir)
        assertFalse("这颗只算路径不建目录（建目录留给写那一侧，与 `desktopGallerySaveDir` 同一口径）：", dir.exists())
    }

    /** ⑤ 「这一话齐不齐」三档：页数齐且清单说齐 → 齐；缺一页 → 不齐；没有清单 → 不齐（不能只看页数）。 */
    @Test
    fun 齐不齐那一判三档而没清单不算齐() {
        val dir = File(tempRoot(), "ch").apply { mkdirs() }
        (0 until 3).forEach { DesktopComicPageFixtures.writeWholePage(dir, it) }
        assertFalse(
            "只有页而没有清单那一档不算齐（半途而废的目录与下全的目录在盘上同形 —— 只看页数就会把前者认成后者）：",
            desktopComicChapterIsComplete(dir, 3),
        )
        desktopComicWriteChapterManifest(dir, chapterId = "1461318", order = 3, title = "第 4 话", expectedPages = 3, complete = true)
        assertTrue("齐的那一档：", desktopComicChapterIsComplete(dir, 3))
        assertFalse("清单说齐而盘上少一页那一档不算齐：", desktopComicChapterIsComplete(dir, 4))
        File(dir, "0003.jpg").delete()
        assertFalse("页被用户删掉之后要重新判不齐：", desktopComicChapterIsComplete(dir, 3))
    }

    /** ⑥ 清单字段表**逐字回核**对岸那份 `chapter.json`（字段名对不上，两端就互认不出同一话）。 */
    @Test
    fun 清单那五个字段名逐字照对岸() {
        val android = DesktopSourceTree.androidSource("download/DownloadManager.kt").readText()
        listOf("chapterId", "title", "order", "expectedPages", "complete").forEach { field ->
            assertTrue("对岸那份 `chapter.json` 里找不到 `$field` 这一格了（写法变了 ⇒ 桌面的清单要跟着改，否则两端互认不出）：", android.contains(field))
        }
        val dir = File(tempRoot(), "c").apply { mkdirs() }
        desktopComicWriteChapterManifest(dir, chapterId = "1461318", order = 7, title = "t", expectedPages = 2, complete = false)
        val text = File(dir, "chapter.json").readText()
        // 字段名同轨**还不够**：对岸那一格装的是话号（`DownloadManager.kt:505` `put("chapterId", task.chapterId)`），
        // 装成本子号就两端互认不出同一话（批 97 §6 那条真缺陷就是这么撞上的）。
        assertEquals(
            "清单里 `chapterId` 那一格必须是**话号**原样（不是本子的号、不是拼出来的串）：$text",
            "1461318",
            Regex("\"chapterId\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1),
        )
        listOf("chapterId", "title", "order", "expectedPages", "complete").forEach { field ->
            assertTrue("桌面写出的清单里缺 `$field`：$text", text.contains(field))
        }
        assertTrue("清单名沿用对岸那一串（不另起 `manifest.json`）：", File(dir, "chapter.json").exists())
    }

    /** ⑥b 整趟下载写下的那份清单，`chapterId` 那一格必须是**话号**而不是本子的号。 */
    @Test
    fun 整趟下载写的清单带的是话号() {
        val dir = File(tempRoot(), "c2").apply { mkdirs() }
        // 两种值各喂一次：号=9 而话号=1461318 —— 只喂一种的话，实现里把两枚换成同一个变量也测不出。
        runBlocking {
            desktopComicDownloadChapter(
                dir, comicId = "9", chapterId = "1461318", order = 0, title = "t", expectedPages = 2,
                shouldCancel = { false }, progress = { _, _ -> },
            ) { index ->
                DesktopComicPageFixtures.stubPageBytes(index)
            }
        }
        val text = File(dir, "chapter.json").readText()
        val field = Regex("\"chapterId\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1)
        assertEquals("清单里那一格念的是：$text", "1461318", field)
        assertNotEquals("写成本子号就和对岸读不上了（`DownloadManager.kt:309` 拿它与话号比）：$text", "9", field)
    }

    /** ⑦ 失败那一档**抛出去**而不静默少页（本仓那条「降级路径宁可错慢不可静默交错」）。 */
    @Test
    fun 有一页取不到就抛而不静默少页() {
        val dir = File(tempRoot(), "d").apply { mkdirs() }
        val message = runCatching {
            runBlocking {
                desktopComicDownloadChapter(
                    dir, comicId = "9", chapterId = "1461318", order = 1, title = "t", expectedPages = 3,
                    shouldCancel = { false }, progress = { _, _ -> },
                ) { index ->
                    if (index == 1) error("连接断了") else DesktopComicPageFixtures.stubPageBytes(index)
                }
            }
        }.exceptionOrNull()?.let { it::class.java.simpleName + "|" + (it.message ?: "") }
        assertTrue("那一趟本该抛出异常而它成了：$message", message != null && message.startsWith("IllegalStateException"))
        assertTrue("原因要带得上是哪一页（只报「失败了」就查不动）：" + message, message!!.contains("页=1"))
        assertTrue("还要带上原始那句（站方的话不吞）：" + message, message.contains("连接断了"))
        assertTrue("第一页留在盘上（不清用户已有的东西）：", File(dir, "0001.jpg").exists())
        assertFalse("而清单不许说齐了：", desktopComicChapterIsComplete(dir, 3))
    }

    /** ⑧ 已下全那一档**一页都不取**（对岸那条"点一下就直接重下一遍"的坑，靠那一判挡住）。 */
    @Test
    fun 已下全那一档一页都不重取() {
        val dir = File(tempRoot(), "e").apply { mkdirs() }
        (0 until 2).forEach { DesktopComicPageFixtures.writeWholePage(dir, it) }
        desktopComicWriteChapterManifest(dir, chapterId = "1461318", order = 0, title = "t", expectedPages = 2, complete = true)
        var fetched = 0
        val run = runBlocking {
            desktopComicDownloadChapter(
                dir, comicId = "9", chapterId = "1461318", order = 0, title = "t", expectedPages = 2,
                shouldCancel = { false }, progress = { _, _ -> },
            ) { _ ->
                fetched += 1
                byteArrayOf(1)
            }
        }
        assertEquals("重跑那一趟交回的：", DesktopComicChapterRun.Completed(written = 0), run)
        assertEquals("重跑那一趟调用取页的次数（不为 0 就是重下一遍）：", 0, fetched)
    }

    /** ⑨ 那一行读数五格齐全，而失败那一档带原因（屏上同形的两档只有它能分）。 */
    @Test
    fun 那一行读数五格而失败那档带原因() {
        assertEquals(
            "没齐那一档：",
            "D_下载 号=9 话=3 页=2/3 全=否",
            desktopComicChapterDownloadLine("9", 3, 2, 3, complete = false),
        )
        assertEquals(
            "齐了那一档：",
            "D_下载 号=9 话=3 页=3/3 全=是",
            desktopComicChapterDownloadLine("9", 3, 3, 3, complete = true),
        )
        val failed = desktopComicChapterDownloadLine("9", 3, 0, 3, cause = IllegalStateException("连接断了"))
        assertTrue("失败那一档要念得出原因：" + failed, failed.contains("因=连接断了"))
        assertTrue("失败那一档也要带得上分母：" + failed, failed.contains("页=0/3"))
        assertTrue("失败那一档要念得出「全=否」：" + failed, failed.contains("全=否"))
    }

    /** ⑩ 清单走原子写那一族（Windows 上 `File.renameTo` 目标已存在只返回 false ⇒ 本仓有一条全目录扫描钉着）。 */
    @Test
    fun 清单那一笔走替换改名而不留临时档() {
        val dir = File(tempRoot(), "f").apply { mkdirs() }
        desktopComicWriteChapterManifest(dir, chapterId = "1461318", order = 1, title = "t", expectedPages = 2, complete = true)
        desktopComicWriteChapterManifest(dir, chapterId = "1461319", order = 1, title = "t", expectedPages = 5, complete = false)
        val text = File(dir, "chapter.json").readText()
        assertTrue("第二笔连 `chapterId` 也要一起覆盖（不许留第一笔那格的残值）：" + text, text.contains("1461319") && !text.contains("1461318"))
        assertTrue("第二笔要覆盖第一笔（同目标名写两次 ⇒ 用的是替换而不是 `renameTo`）：" + text, text.contains("5"))
        val leftovers = dir.listFiles()?.filter { it.name.endsWith(".tmp") } ?: emptyList()
        assertEquals("临时档不许留在盘上：" + leftovers.joinToString { it.name }, 0, leftovers.size)
        if (dir.listFiles()?.any { it.name == "chapter.json" } != true) fail("清单那一颗不见了")
    }
}
