package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 批 97 · 桌面「本机已下载的那一屏」的**读侧**（读盘上那一棵树，不是再拼一次名字）。
 *
 * ## 这一格为什么是缺陷而不是新feature
 *
 * 批 96 把整话写到 `数据根/downloads/<站名键>/<号>/<话目录>/{NNNN.jpg, chapter.json}`，并在包内量到
 * 89 颗 / 24,905,629 B / 清单 `complete:true` —— 可是**桌面没有任何一处读这棵树**（现查：`chapter.json` 与
 * `desktopComicChapterDir(` 的调用点全在写那一侧）。"下得着而看不见"就是本仓记过一整族的那件事的另一半：
 * 用户按了长按、盘上多了 24 MB，而任何一屏都数不着它。
 *
 * ## 齐不齐那一判吃的是**盘上**而不是清单
 *
 * 对岸自己两颗读侧就不同口径（`DownloadManager.stateOfChapterDir` 吃 `expectedPages` + `complete` + `listFiles` 计数；
 * `LocalComicManager` 只看有没有图）。桌面沿批 96 立的那一档，并把它做硬：**逐页查盘**（数得出盘上非空的那几页），
 * 清单说齐而盘上少页 ⇒ 那一行念"缺 N 页"而不是"已下全"。屏上那一句是用户据以删文件的话，宁可念少不许念多。
 *
 * ## 三条不许静默
 *
 * 清单读不出（烂掉 / 缺字段）、清单里的号与目录名对不上、目录里没有清单 —— 三档都不算"已下载"，
 * 但**都要在计数里点名**：静默跳过会让用户以为"下了而不见了"，而那正好是这一屏要治的病。
 */
class DesktopComicDownloadedListTest {

    private fun tempRoot(): File = Files.createTempDirectory("b97dl").toFile()

    /** 造一条盘上真实存在的话：`根/<站>/<号>/<000O_题>/` + 清单 + `count` 颗**算已有**的页桩（头部是图且够大）。 */
    private fun chapterOnDisk(
        root: File, sourceKey: String, comicId: String, order: Int, title: String,
        expected: Int, pages: Int, manifest: String? = null,
    ): File {
        val dir = File(File(File(root, sourceKey), comicId), order.toString().padStart(4, '0') + "_" + title)
        dir.mkdirs()
        for (index in 0 until pages) DesktopComicPageFixtures.writeWholePage(dir, index)
        if (manifest != null) File(dir, "chapter.json").writeText(manifest)
        return dir
    }

    private fun manifestOf(chapterId: String, title: String, order: Int, expected: Int, complete: Boolean): String =
        "{\"chapterId\":\"$chapterId\",\"title\":\"$title\",\"order\":$order,\"expectedPages\":$expected,\"complete\":$complete}"

    /** ① 三层里认出清单：两条各归一条，多摆的目录与散文件都不算。 */
    @Test
    fun 三层里认出那两条而散文件不算() {
        val root = tempRoot()
        chapterOnDisk(root, "jm", "1461316", 1, "第2話", expected = 3, pages = 3,
            manifest = manifestOf("1461316", "第2話", 1, 3, true))
        chapterOnDisk(root, "jm", "1461316", 2, "第3話", expected = 2, pages = 2,
            manifest = manifestOf("1461316", "第3話", 2, 2, true))
        val found = desktopComicDownloadedChapters(root)
        assertEquals("两本目录各一条：" + found.map { it.order }, 2, found.size)
        assertEquals("次序按 order：" + found.map { it.order }, listOf(1, 2), found.map { it.order })
        assertEquals("号与站名从清单取：" + found.first(), "jm", found.first().sourceKey)
        assertEquals("题名跟着清单：" + found[1].title, "第3話", found[1].title)
        // 目录里再丢一颗散文件也不改变计数（只数 `<根>/<站>/<号>/<话>` 那一层）。
        File(root, "stray.txt").writeText("x")
        assertEquals("散文件被当成了一条：" + desktopComicDownloadedChapters(root).size, 2, desktopComicDownloadedChapters(root).size)
        assertEquals("空根要交回空表而不是抛", 0, desktopComicDownloadedChapters(File(root.parentFile, "没有这一颗")).size)
    }

    /** ② 齐那一判吃盘上页数：清单说齐而盘上少页 ⇒ 那一行必须念缺页。 */
    @Test
    fun 清单说齐而盘上少页那一档不算齐() {
        val root = tempRoot()
        chapterOnDisk(root, "jm", "9", 0, "t", expected = 3, pages = 2,
            manifest = manifestOf("9", "t", 0, 3, true))
        val only = desktopComicDownloadedChapters(root).single()
        assertEquals("盘上那几页要数得出来：" + only.writtenPages, 2, only.writtenPages)
        assertEquals("清单说 3 页：", 3, only.expectedPages)
        assertFalse("清单说齐而盘上少一页时不许报齐（屏上那句是用户据以删文件的话）：" + only, only.complete)
        // 齐的那一档正面对照：三页都在。
        chapterOnDisk(root, "ex", "9", 0, "t", expected = 3, pages = 3, manifest = manifestOf("9", "t", 0, 3, true))
        val good = desktopComicDownloadedChapters(root).first { it.sourceKey == "ex" }
        assertTrue("三页都在时报齐：" + good, good.complete)
    }

    /** ③ 零字节的那一颗不算一页（半途写坏的文件不能被数成"下到了"）。 */
    @Test
    fun 零字节那一颗不算一页() {
        val root = tempRoot()
        val dir = chapterOnDisk(root, "jm", "9", 0, "t", expected = 3, pages = 3,
            manifest = manifestOf("9", "t", 0, 3, true))
        File(dir, "0002.jpg").writeBytes(ByteArray(0))
        val only = desktopComicDownloadedChapters(root).single()
        assertEquals("三颗里有一颗是空的：" + only.writtenPages, 2, only.writtenPages)
        assertFalse("空文件那颗不许算齐：" + only, only.complete)
    }

    /** ④ 清单读不出（烂掉 / 缺字段）与没有清单：三档都不算已下，而抛出去是不许的。 */
    @Test
    fun 清单读不出那一档不算已下而不抛() {
        val root = tempRoot()
        chapterOnDisk(root, "jm", "1", 0, "t", expected = 1, pages = 1, manifest = "{这根本不是 JSON")
        chapterOnDisk(root, "jm", "2", 0, "t", expected = 1, pages = 1, manifest = "{\"chapterId\":\"2\",\"title\":\"t\",\"order\":0}")
        chapterOnDisk(root, "jm", "3", 0, "t", expected = 1, pages = 1, manifest = null)
        val found = runCatching { desktopComicDownloadedChapters(root) }
        assertTrue("清单读不出时那颗抛了：" + found.exceptionOrNull(), found.isSuccess)
        assertEquals("三档都不算一条已下载：" + found.getOrNull(), 0, found.getOrNull()?.size)
        val skipped = desktopComicDownloadedSkipped(root)
        assertEquals("跳过那一格要数得出是哪三条（静默跳过＝用户以为下了而不见了）：" + skipped, 3, skipped)
    }

    /** ⑤ 对岸形状的清单（`chapterId` 那格装的是**话号**）必须认得出；题名与目录名对不上的才点名。 */
    @Test
    fun 对岸写的整话认得出而题名对不上要点名() {
        val root = tempRoot()
        // 正例：号住在**目录名**、话号住在清单 —— 那正是对岸 `DownloadManager.kt:505` 的形状。
        // 认不出它 = "对岸下的那些在桌面这一屏看不见"，就是这一屏要治的那条病换个边界再犯一次。
        chapterOnDisk(root, "jm", "1461316", 1, "第2話", expected = 2, pages = 2,
            manifest = manifestOf("1461318", "第2話", 1, 2, true))
        val found = desktopComicDownloadedChapters(root)
        assertEquals("对岸写的整话要摆得出来：" + found, 1, found.size)
        assertEquals("号从目录名取，清单那一格不许顶替：" + found.first(), "1461316", found.first().comicId)
        assertEquals("正例不许进「读不出」那一格：" + desktopComicDownloadedSkipped(root), 0, desktopComicDownloadedSkipped(root))
        // 负例：目录名后缀与清单题名对不上 ⇒ 两端各拼一套名、或用户手工挪过的树。
        // 认下来会把 A 话的清单套到 B 话的页上，而屏上那一句是用户据以删文件的话。
        val bad = File(File(File(root, "ex"), "77"), "0000_第1話")
        bad.mkdirs()
        File(bad, "0001.jpg").writeBytes(byteArrayOf(1))
        File(bad, "chapter.json").writeText(manifestOf("78", "第2話", 0, 1, true))
        assertEquals("题名对不上那一档不许摆出来：" + desktopComicDownloadedChapters(root), 1, desktopComicDownloadedChapters(root).size)
        assertEquals("而要进「读不出」那一格计数：" + desktopComicDownloadedSkipped(root), 1, desktopComicDownloadedSkipped(root))
    }

    /** ⑤b 目录名前缀对不上 order：认不出就不算已下，而那一格要点名。 */
    @Test
    fun 目录名前缀对不上order那一档点名() {
        val root = tempRoot()
        val good = chapterOnDisk(root, "jm", "1461316", 1, "第2話", expected = 1, pages = 1,
            manifest = manifestOf("1461316", "第2話", 1, 1, true))
        // 夹具手工把前缀换成另一枚（`0001_` → `0002_` 而清单里的 order 仍是 1）：那正是"两端各拼一套名"
        // 会在同一台机器上造出来的形状 —— 号对得上而话对不上，认下来就会把 A 话的清单套到 B 话的页上。
        val moved = File(good.parentFile, "0002_第2話")
        check(good.renameTo(moved)) { "夹具挪不动目录：${good.path}" }
        assertEquals("这一条不许当成已下：" + desktopComicDownloadedChapters(root), 0, desktopComicDownloadedChapters(root).size)
        assertEquals("而对不上的那一格要点名：" + desktopComicDownloadedSkipped(root), 1, desktopComicDownloadedSkipped(root))
    }

    /** ⑥ 字节数从盘上取（这一格是用户"要不要清掉它"的唯一依据）。 */
    @Test
    fun 字节数从盘上取() {
        val root = tempRoot()
        // 第一百一十批之后页桩必须"头部是图且够大"才算一页 ⇒ 两页各给一枚**不同**尺寸，
        // 才测得出这一格是从盘上取的而不是颗数乘了一枚常数。
        val dir = chapterOnDisk(root, "jm", "9", 0, "t", expected = 2, pages = 2, manifest = manifestOf("9", "t", 0, 2, true))
        DesktopComicPageFixtures.writeWholePage(dir, 1, 3000)
        val only = desktopComicDownloadedChapters(root).single()
        assertEquals("两页的字节和（2048 + 3000）：" + only.bytes, 5048L, only.bytes)
    }

    /** ⑦ 按本子分组：同号的几话并成一格，两本分开，次序稳定。 */
    @Test
    fun 分组按号并话() {
        val root = tempRoot()
        chapterOnDisk(root, "jm", "1", 0, "a", expected = 1, pages = 1, manifest = manifestOf("1", "a", 0, 1, true))
        chapterOnDisk(root, "jm", "1", 1, "b", expected = 1, pages = 1, manifest = manifestOf("1", "b", 1, 1, true))
        chapterOnDisk(root, "jm", "2", 0, "c", expected = 1, pages = 1, manifest = manifestOf("2", "c", 0, 1, true))
        val groups = desktopComicChaptersByComic(desktopComicDownloadedChapters(root))
        assertEquals("两本：" + groups.map { it.comicId }, 2, groups.size)
        assertEquals("第一本两话：" + groups.first(), 2, groups.first().chapters.size)
        assertEquals("第二本一话：" + groups.last(), 1, groups.last().chapters.size)
        assertEquals("齐了的那两话数得出来：" + groups.first().completeCount, 2, groups.first().completeCount)
        assertEquals("页码合计（一话一页 ⇒ 两话两页）：", 2, groups.first().pageCount)
        // 分组那一格只许念**号**：清单里存的是话的题名，本子的题名今天盘上根本没有 ⇒ 造一个就是假话。
        assertEquals("那一格念的是号而不是编出来的题名：" + groups.first().label, "1", groups.first().label)
        assertEquals("话的题名跟着清单走：" + groups.first().chapters.first().title, "a", groups.first().chapters.first().title)
        assertEquals("空表分组要交回空而不抛", 0, desktopComicChaptersByComic(emptyList()).size)
    }

    /** ⑧ 表头那一行四档各念一句，而空表那一档不许说"有东西"。 */
    @Test
    fun 表头那行四档各念一句() {
        val empty = desktopComicDownloadedHeaderReading(emptyList(), skipped = 0)
        assertTrue("空那一档：" + empty, empty.startsWith("D_本机下载 本=0 话=0") && empty.contains("缺=0"))
        assertFalse("空表却报了条数：" + empty, empty.contains("本=1"))
        val root = tempRoot()
        chapterOnDisk(root, "jm", "1", 0, "a", expected = 2, pages = 2, manifest = manifestOf("1", "a", 0, 2, true))
        chapterOnDisk(root, "jm", "1", 1, "b", expected = 4, pages = 1, manifest = manifestOf("1", "b", 1, 4, false))
        val groups = desktopComicChaptersByComic(desktopComicDownloadedChapters(root))
        val line = desktopComicDownloadedHeaderReading(groups, skipped = 3)
        assertTrue("本子那格：" + line, line.contains("本=1"))
        assertTrue("话数那格：" + line, line.contains("话=2"))
        assertTrue("齐的那格只数齐的：" + line, line.contains("齐=1"))
        assertTrue("缺的那格数缺的：" + line, line.contains("缺=1"))
        assertTrue("读不出/对不上那格要点名：" + line, line.contains("跳=3"))
        assertTrue("字节那格要念得出（用户要不要清掉它，只有这一格说得出）：" + line, Regex("字节=\\d+").containsMatchIn(line))
    }

    /** ⑨ 分家规矩：读侧那颗件不吃引擎句柄，也不许复用写侧那枚拼名算式。 */
    @Test
    fun 读侧不吃引擎也不拼名字() {
        val file = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicDownloadedList.kt")
        val code = DesktopSourceTree.codeText(file)
        assertFalse("读侧又去吃引擎句柄了（那一屏就渲不出来，也测不动）：", code.contains("EngineSession"))
        // `desktopComicChapterDir(` 是把"该写到哪里"拼出来的算式，读侧数的是**盘上真存在**的目录 ——
        // 复用它就等于"按名字假定那儿有东西"，那正是本屏要治的反面。批 96 那条计数判式（恰 2 处）在这里是邻居保护。
        assertEquals("读侧不许拼目录名：" + DesktopSourceTree.codeOccurrences(file, "desktopComicChapterDir("), 0, DesktopSourceTree.codeOccurrences(file, "desktopComicChapterDir("))
        // 批 98 改判 2 → 3：删除那颗件复用同一算式反推目录，而不是另拼一次名（另拼就是两把写法）。
        assertEquals("写侧那枚拼名算式的调用点变了（批 101 把那一跳搬进泵）：", 2, DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, "desktopComicChapterDir(") })
        // 逐页查盘那一判**复用批 96 那颗**，不许在屏旁边再写一份"齐不齐"（两处各认一种＝漂移）。
        assertTrue("读侧没复用那颗齐不齐的判：" + code, code.contains("desktopComicChapterIsComplete("))
    }

    /** ⑩ 清单字段名单源：读侧用的那几枚串必须与写侧那一份逐字相同。 */
    @Test
    fun 字段名两处同轨() {
        val writer = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicChapterDownload.kt"))
        val reader = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicDownloadedList.kt"))
        listOf("chapterId", "expectedPages", "complete").forEach { field ->
            assertTrue("写侧少了 `$field` 那一格（对岸改了字段名的话先红在这里）：", writer.contains(field))
            assertTrue("读侧没跟着认 `$field`：", reader.contains(field))
        }
        // 对照组：清单那个文件名两处同一串（改了名就读不到，而那形状必须是红的而不是空的）。
        assertTrue("读侧没认 `chapter.json`：", reader.contains("chapter.json"))
    }
}
