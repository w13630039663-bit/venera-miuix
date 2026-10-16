package com.venera.desktop.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 批 98 · 桌面「本机已下载」那一屏的**删除**那颗件。
 *
 * ## 这一批要顶住的那条对岸缺陷（逐行现量，见 rounds `desktop-comic-downloads-delete-2026-10-12.md` §1）
 *
 * `app/…/download/LocalComicManager.kt:302` 是 `root.deleteRecursively()` —— **返回值丢弃**，下一行 `:304` 恒 `true`；
 * 而 `deleteRecursively` 不抛异常、只返回 false ⇒ 半树删失败、权限占用、甚至目录压根不存在（`:301` 为假）都说"删掉了"，
 * 调用点（`feature/LocalComicScreen.kt:317-318`）接都不接就 Toast「已删除本地漫画」。
 * 桌面这一颗**删完回读盘**才定档，且「本来就没了」与「真删掉了」是两档。
 *
 * ## 三条回核，一条都不许省
 *
 * 1. `canonicalPath` 前缀：目标必须落在 `downloads` 根之内（对岸**零**这一道）。
 * 2. 清单在场：只删这一屏认得出的那一目录，用户手工放的裸目录不许碰。
 * 3. 清单内容与递进来的那一话对上：`order` 与 `title` 不一致就拒绝（防把 A 话的确认删到 B 话的目录上）。
 */
class DesktopComicChapterDeleteTest {

    private fun tempRoot(): File = Files.createTempDirectory("b98del").toFile()

    private fun manifestOf(chapterId: String, title: String, order: Int, expected: Int, complete: Boolean): String =
        "{\"chapterId\":\"$chapterId\",\"title\":\"$title\",\"order\":$order,\"expectedPages\":$expected,\"complete\":$complete}"

    /** 造一条盘上真实存在的话（页从 1 数起、字节逐颗不同，好让"释放字节"能被独立算出来）。 */
    private fun chapterOnDisk(
        root: File, sourceKey: String, comicId: String, order: Int, title: String,
        pages: Int, manifest: String? = null,
    ): File {
        val dir = File(File(File(root, sourceKey), comicId), desktopComicChapterDirName(order, title))
        dir.mkdirs()
        for (index in 0 until pages) File(dir, desktopComicChapterPageFileName(index)).writeBytes(ByteArray(index + 3))
        if (manifest != null) File(dir, "chapter.json").writeText(manifest)
        return dir
    }

    private fun entry(sourceKey: String, comicId: String, order: Int, title: String, expected: Int) =
        DesktopComicDownloadedChapter(
            sourceKey = sourceKey, comicId = comicId, chapterId = "c" + order, order = order, title = title,
            expectedPages = expected, writtenPages = expected, bytes = 0L, complete = true,
        )

    private fun dirBytes(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** ① 真删：那一棵连页带清单一起走。 */
    @Test
    fun 真删那一棵话目录而页与清单一起走() {
        val root = tempRoot()
        val dir = chapterOnDisk(root, "jm", "1461316", 1, "第2話", pages = 3,
            manifest = manifestOf("9", "第2話", 1, 3, true))
        val outcome = desktopComicDeleteDownloadedChapter(root, entry("jm", "1461316", 1, "第2話", 3))
        assertFalse("目录必须真没了：" + dir.walkTopDown().toList(), dir.exists())
        assertEquals("四档里只有真删成那一档：" + outcome, true, outcome is DesktopComicDeleteOutcome.Deleted)
    }

    /** ② 本来就不在盘上 ⇒ `Absent` 而**绝不**是 `Deleted`（对岸那一笔的形状：没删掉却说删掉了）。 */
    @Test
    fun 本来不在盘上时说不成删掉了() {
        val root = tempRoot()
        File(root, "jm").mkdirs()
        val outcome = desktopComicDeleteDownloadedChapter(root, entry("jm", "999", 5, "没下过的话", 2))
        assertEquals("那一档只能是 Absent：" + outcome, true, outcome is DesktopComicDeleteOutcome.Absent)
        assertFalse("也不许顺手把根目录建出那一层", File(File(root, "jm"), "999").exists())
    }

    /** ③ `canonicalPath` 回核不过 ⇒ `Rejected` **且盘上那棵还在**（拒绝不能顺手删半棵）。 */
    @Test
    fun 逃出下载根的那种名字直接拒绝() {
        val outer = tempRoot()
        val root = File(outer, "downloads").apply { mkdirs() }
        // 站名键 ".." 不在净化那张字符表里（表只有 / \ : * ? " < > |）⇒ 拼出来就是父目录，正是要挡的那一档
        val victim = File(outer, "别的东西").apply { mkdirs() }
        File(victim, "a.bin").writeBytes(ByteArray(7))
        val before = victim.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val outcome = desktopComicDeleteDownloadedChapter(root, entry("..", "别的东西", 0, "", 1))
        assertTrue("要拒绝：" + outcome, outcome is DesktopComicDeleteOutcome.Rejected)
        assertTrue("被指的那棵一颗字节都不许少", victim.exists() &&
            victim.walkTopDown().filter { it.isFile }.sumOf { it.length() } == before)
    }

    /** ④ 目录在而里面没有清单 ⇒ `Rejected`：用户手工放的裸目录不是这一屏认下的东西。 */
    @Test
    fun 没有清单的裸目录不许删() {
        val root = tempRoot()
        val dir = chapterOnDisk(root, "jm", "1461316", 1, "第2話", pages = 2, manifest = null)
        val outcome = desktopComicDeleteDownloadedChapter(root, entry("jm", "1461316", 1, "第2話", 2))
        assertTrue("裸目录只能拒绝：" + outcome, outcome is DesktopComicDeleteOutcome.Rejected)
        assertTrue("盘上那两页还在", File(dir, "0001.jpg").isFile && File(dir, "0002.jpg").isFile)
    }

    /** ⑤ 清单内容与递进来的那一话对不上 ⇒ `Rejected`（防 A 话的确认删到 B 话的目录上）。 */
    @Test
    fun 清单里那话与递进来的不是同一话时拒绝() {
        val root = tempRoot()
        val dir = chapterOnDisk(root, "jm", "1461316", 1, "第2話", pages = 2,
            manifest = manifestOf("9", "第2話", 7, 2, true)) // 清单里 order=7，与目录名/递进来的 1 不同轨
        val outcome = desktopComicDeleteDownloadedChapter(root, entry("jm", "1461316", 1, "第2話", 2))
        assertTrue("清单与目录不同轨只能拒绝：" + outcome, outcome is DesktopComicDeleteOutcome.Rejected)
        assertTrue("页还在盘上", File(dir, "0001.jpg").isFile)
    }

    /** ⑥ 删不动 ⇒ `Failed` 带**残留文件数与字节**（回读盘，不吃 `deleteRecursively` 的返回值）。 */
    @Test
    fun 删不动时念得出还剩几颗多少字节() {
        val root = tempRoot()
        val dir = chapterOnDisk(root, "jm", "1461316", 1, "第2話", pages = 3,
            manifest = manifestOf("9", "第2話", 1, 3, true))
        val left = dir.walkTopDown().filter { it.isFile }.toList()
        val outcome = desktopComicDeleteDownloadedChapter(root, entry("jm", "1461316", 1, "第2話", 3)) { false }
        assertTrue("删不动那一档：" + outcome, outcome is DesktopComicDeleteOutcome.Failed)
        val failed = outcome as DesktopComicDeleteOutcome.Failed
        assertEquals("残留文件数要从盘上现数：" + failed, left.size, failed.leftFiles)
        assertEquals("残留字节要从盘上现数：" + failed, left.sumOf { it.length() }, failed.leftBytes)
    }

    /** ⑥b 缝**声称删成了而盘上还在**（对岸那一笔的真形状：`deleteRecursively` 报 true 也算不上盘上没了）⇒ 仍须落 `Failed`。 */
    @Test
    fun 谁说删成了不算数而只信回读盘() {
        val root = tempRoot()
        val dir = chapterOnDisk(root, "jm", "1461316", 1, "第2話", pages = 2,
            manifest = manifestOf("9", "第2話", 1, 2, true))
        val outcome = desktopComicDeleteDownloadedChapter(root, entry("jm", "1461316", 1, "第2話", 2)) { true }
        assertTrue("信号说成了而盘上还在 ⇒ 只能是删不动：" + outcome, outcome is DesktopComicDeleteOutcome.Failed)
        assertTrue("那一目录必须真还在（这颗缝没动手）：" + dir, dir.isDirectory)
    }

    /** ⑦ 只吃那一棵：同本的别的话与 `downloads/图库` 那棵散图一颗字节都不许少。 */
    @Test
    fun 删那一棵而邻居与图库散图都不动() {
        val root = tempRoot()
        chapterOnDisk(root, "jm", "1461316", 1, "第2話", pages = 2, manifest = manifestOf("9", "第2話", 1, 2, true))
        val sibling = chapterOnDisk(root, "jm", "1461316", 2, "第3話", pages = 2, manifest = manifestOf("10", "第3話", 2, 2, true))
        val gallery = File(root, "图库").apply { mkdirs() }
        File(gallery, "pic.png").writeBytes(ByteArray(11))
        val outcome = desktopComicDeleteDownloadedChapter(root, entry("jm", "1461316", 1, "第2話", 2))
        assertTrue("要删成：" + outcome, outcome is DesktopComicDeleteOutcome.Deleted)
        assertTrue("别的话还在", sibling.isDirectory && File(sibling, "0001.jpg").isFile)
        assertTrue("图库那棵散图还在", File(gallery, "pic.png").isFile)
        assertFalse("目标那棵没了", File(File(File(root, "jm"), "1461316"), "0001_第2話").exists())
    }

    /** ⑧ 释放字节 = 删前**现量**那一棵（含清单而不只页），交回给用户看的那个数要真。 */
    @Test
    fun 释放的字节是删前现量那一棵而不是只数页() {
        val root = tempRoot()
        val dir = chapterOnDisk(root, "jm", "1461316", 1, "第2話", pages = 3, manifest = manifestOf("9", "第2話", 1, 3, true))
        val whole = dirBytes(dir)
        val pagesOnly = (0 until 3).sumOf { (it + 3).toLong() }
        val outcome = desktopComicDeleteDownloadedChapter(root, entry("jm", "1461316", 1, "第2話", 3))
        val deleted = outcome as DesktopComicDeleteOutcome.Deleted
        assertEquals("释放字节要含清单：" + deleted, whole, deleted.freedBytes)
        assertFalse("不许只数那几页：" + pagesOnly + " vs " + whole, deleted.freedBytes == pagesOnly)
        assertEquals("页数那一格数的是盘上真有的页：" + deleted, 3, deleted.pages)
    }

    /** ⑧b 第一百一十批那条边界：这一屏数的是**要删掉的颗数**，不是"齐不齐"。 */
    @Test
    fun 坏页那一颗也算进删掉了几页() {
        // 批 110 把"这一页在不在"抬到"名字 + 尺寸 + 头部是图"，而**这一处故意不跟着换**：
        // 过盾页那颗 HTML 是真占着磁盘的真文件，"删掉了 2 页"而盘上少 3 颗就是把释放说小。
        // ⇒ 同一件事在两屏各有各的数，这一枚就是那条"同名字不同语义"的接缝钉。
        val root = tempRoot()
        val dir = chapterOnDisk(root, "jm", "1461316", 1, "第2話", pages = 3, manifest = manifestOf("9", "第2話", 1, 3, true))
        DesktopComicPageFixtures.writeBrokenPage(dir, 1)
        val whole = dirBytes(dir)
        val outcome = desktopComicDeleteDownloadedChapter(root, entry("jm", "1461316", 1, "第2話", 3))
        val deleted = outcome as DesktopComicDeleteOutcome.Deleted
        assertEquals("三颗文件都真没了，那一格就得念三（吃「齐不齐」那把尺子会少报一颗）：" + deleted, 3, deleted.pages)
        assertEquals("释放字节仍要含那颗坏页与清单：" + deleted, whole, deleted.freedBytes)
    }

    /** ⑨ 根目录还不存在 ⇒ `Absent`，**不建目录也不抛**。 */
    @Test
    fun 根还没建出来时说本来就没了而不是抛() {
        val root = File(tempRoot(), "还没有这一层")
        val outcome = desktopComicDeleteDownloadedChapter(root, entry("jm", "1", 0, "t", 1))
        assertEquals("那一档只能是 Absent：" + outcome, true, outcome is DesktopComicDeleteOutcome.Absent)
        assertFalse("不许顺手把根建出来", root.exists())
    }

    /** ⑩ 读数那一行四档各念各的结果值（取证要分得清"没删成"与"没东西可删"）。 */
    @Test
    fun 读数那一行四档各念各的() {
        val chapter = entry("jm", "1461316", 1, "第2話", 3)
        val lines = listOf(
            desktopComicDeleteReading(chapter, DesktopComicDeleteOutcome.Deleted(pages = 3, freedBytes = 12L)),
            desktopComicDeleteReading(chapter, DesktopComicDeleteOutcome.Absent),
            desktopComicDeleteReading(chapter, DesktopComicDeleteOutcome.Rejected("逃出下载根")),
            desktopComicDeleteReading(chapter, DesktopComicDeleteOutcome.Failed(leftFiles = 2, leftBytes = 9L, reason = "占用")),
        )
        lines.forEach { assertTrue("每档都要带归属与结果：" + it, it.startsWith("D_删除 号=1461316 话=1 结果=")) }
        // 钉的是**「结果」那一格**四样，而不是整句四样：整句比distinct会被 `释放=` 的数值差顶开（台架 P7 量出来的空档）。
        val stages = lines.map { Regex("结果=(\\S+)").find(it)!!.groupValues[1] }
        assertEquals("四档的「结果」那一格必须四样：" + stages, 4, stages.distinct().size)
        assertEquals("四档四句：" + lines, 4, lines.distinct().size)
        assertTrue("删掉那档念释放：" + lines[0], lines[0].contains("释放=12"))
        assertTrue("删不动那档念残留：" + lines[3], lines[3].contains("剩=2") && lines[3].contains("因=占用"))
    }
}
