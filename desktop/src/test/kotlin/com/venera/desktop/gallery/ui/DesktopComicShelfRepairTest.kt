package com.venera.desktop.gallery.ui

import com.venera.desktop.ForensicFlags
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicPageFixtures
import com.venera.desktop.gallery.data.desktopComicChapterDirName
import com.venera.desktop.gallery.data.desktopComicQueueDir
import com.venera.desktop.gallery.data.DesktopComicShelfRawIdIndex
import com.venera.desktop.gallery.data.desktopComicShelfRepairArgsOf
import com.venera.desktop.gallery.data.desktopComicRepairChapter
import com.venera.desktop.gallery.data.desktopComicRepairLabel
import com.venera.desktop.gallery.data.desktopComicRepairSeedHitLine
import com.venera.desktop.gallery.data.desktopComicRepairSeedMissLine
import com.venera.desktop.gallery.data.desktopComicRepairShouldShow
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第一百一十一批 · 「本机已下载」那一行给一枚「补齐」把手。
 *
 * 批 110 让 `缺 N 页` 那一档多起来（历史坏页从此不算一页），而那一屏只说不办：
 * 行右边唯一那枚把手是**删**（越按越少）。⇒ 说了缺就得给一条补的路。
 *
 * ⚠️ 两枚把手各只改一件事（与批 105 同一条判据）：「补齐」只入队而**盘上一颗文件都不动**，
 * 「删掉」只删而不排队。判式 ⑤ 钉的就是"补齐不许顺手清盘"。
 */
class DesktopComicShelfRepairTest {

    private fun tempRoot(): File = Files.createTempDirectory("b111").toFile().apply {
        deleteRecursively(); mkdirs()
    }

    private fun chapter(
        complete: Boolean,
        written: Int = 28,
        expected: Int = 30,
        chapterId: String = "19777@1253812",
    ) = DesktopComicDownloadedChapter(
        sourceKey = "goda",
        // 盘上那一形（`_manga_panda`），不是站点那一形（`/manga/panda`）：行模型那格是读目录名读回来的
        // （`DesktopComicDownloadedList.kt:99`）。夹具原先递的是站点形，于是删除那一件永远"认不出这一目录"
        // 而返回 Rejected —— P5 那两遍长绿就是这么来的（rounds §10）。两形的差在批 112 立案。
        comicId = "_manga_panda",
        chapterId = chapterId,
        order = 0,
        title = "第01话",
        expectedPages = expected,
        writtenPages = written,
        bytes = 2_658_270L,
        complete = complete,
    )

    /** ① 只有缺页那一档才摆这枚把手（齐了的话摆它就是多一枚没意义的按钮）。 */
    @Test
    fun 不齐那一档才摆补齐把手() {
        assertTrue("缺页那一档该摆：", desktopComicRepairShouldShow(chapter(complete = false)))
        assertFalse("已下全那一档不许摆：", desktopComicRepairShouldShow(chapter(complete = true)))
    }

    /** ② 题面念得出缺几页，而**不许**念成重下整话或删除。 */
    @Test
    fun 把手那句念的是补缺页而不是重下整话() {
        val label = desktopComicRepairLabel(chapter(complete = false, written = 28, expected = 30))
        assertEquals("整串：", "补齐这一话 · 还缺 2 页", label)
        assertFalse("不许把补齐说成重下（那是在吓用户说盘上那些页要重来一遍）：" + label, label.contains("重下"))
        assertFalse("不许把补齐说成删：" + label, label.contains("删"))
    }

    /** ③ 从行递出去的那一笔逐格对得上 —— `comicId` 与 `chapterId` 是两枚，不许并成一枚。 */
    @Test
    fun 行里那五格原样进队列而两枚号不许并() {
        val args = desktopComicShelfRepairArgsOf(chapter(complete = false), DesktopComicShelfRawIdIndex.of(emptyList()))
        assertEquals("站名键：", "goda", args.sourceKey)
        assertEquals("本子号（盘上那一形）：", "_manga_panda", args.comicId)
        assertEquals("话号（清单里那一格，不是本子号）：", "19777@1253812", args.chapterId)
        assertEquals("话序：", 0, args.chapterOrder)
        assertEquals("章名：", "第01话", args.chapterTitle)
        assertFalse("号都是从盘上真目录读回来的，不是占位：", args.placeholder)
        // 盘上根本没有本子的题名（`DesktopComicDownloadedGroup.label` 就是号），而那一格**不上屏**：
        // 队列那一行念的是 `第 N 话「章名」 · 档位 · 页 · 本子 <comicId>`（DesktopComicQueuePane.kt:176-177 现量）。
        assertEquals("题名那一格递的是号（它不进那一句）：", "_manga_panda", args.comicTitle)
        assertEquals("封面那一格盘上没有，递空串：", "", args.comicCover)
    }

    /** ④ 同一行连按两次仍是表里一条（复用批 101「重复入队不去」那颗口径）。 */
    @Test
    fun 连按两次只多一笔() {
        val root = tempRoot()
        try {
            val c = chapter(complete = false)
            desktopComicRepairChapter(root, c, DesktopComicShelfRawIdIndex.of(emptyList()))
            desktopComicRepairChapter(root, c, DesktopComicShelfRawIdIndex.of(emptyList()))
            assertEquals(
                "队列表里该只有一条：" + DesktopComicDownloadQueue.tasks.value.map { it.chapterId },
                1, DesktopComicDownloadQueue.tasks.value.size,
            )
        } finally { root.deleteRecursively() }
    }

    /** ⑤ 补齐那一跳一颗文件都不动 —— 它排的是"把缺的补上"，不是"清盘重下"。 */
    @Test
    fun 补齐那一跳不删盘上任何一颗文件() {
        val root = tempRoot()
        try {
            val dir = File(File(File(root, "goda"), "_manga_panda"), desktopComicChapterDirName(0, "第01话"))
            assertTrue("建不出目录：", dir.mkdirs())
            (0 until 28).forEach { DesktopComicPageFixtures.writeWholePage(dir, it) }
            // 清单必须摆上：删除那一件先要"认得出这一目录"才动手（`DesktopComicChapterDelete.kt:76-80`），
            // 没有清单时它返回 Rejected 什么都不删 ⇒ 这一格会变成长绿的真假判式（批 111 P5 第一遍就是这么绿的）。
            File(dir, "chapter.json").writeText(
                "{\"chapterId\":\"19777@1253812\",\"title\":\"第01话\",\"order\":0,\"expectedPages\":30,\"complete\":false}"
            )
            val before = dir.listFiles()!!.sortedBy { it.name }.associate { it.name to it.length() }
            assertEquals("起手该有 28 颗页加一份清单：", 29, before.size)

            val line = desktopComicRepairChapter(root, chapter(complete = false), DesktopComicShelfRawIdIndex.of(emptyList()))

            val task = DesktopComicDownloadQueue.tasks.value.first()
            assertEquals("排的就是这一话：", "19777@1253812", task.chapterId)
            val dirAfterEnqueue = desktopComicQueueDir(root, task)
            // 目录整颗没了也要念得出话（留成 NPE 就没人知道那一跳干了删除件的活）。
            val after = (dirAfterEnqueue.listFiles() ?: emptyArray()).sortedBy { it.name }
                .associate { it.name to it.length() }
            assertEquals("按完那一格盘上还剩几颗（0 颗＝整颗目录被删了）：", before.size, after.size)
            assertEquals("盘上那些页一颗都没少、一字节都没改：" + after.keys, before, after)
            assertEquals(
                "按完那一格念的是这一句（缺几页、盘上留几页都得说清）：",
                "已排进下载队列：第 1 话「第01话」还缺 2 页 · 盘上那 28 页一颗都没动" +
                    " · 本机没有这号的原始记录，排的是目录那一形 _manga_panda",
                line,
            )
        } finally { root.deleteRecursively() }
    }

    /** ⑥ 那一屏自己造的活不许等换屏：有待跑才要引擎，表空就不起（与 `QUEUE` 那一支同轨）。 */
    @Test
    fun 本机已下载那一屏也驱动泵() {
        assertTrue("表里有活时那一屏要引擎：", desktopComicEngineNeeded(DesktopComicScreen.DOWNLOADS, hasQueueWork = true))
        assertFalse("表空时不许白起引擎：", desktopComicEngineNeeded(DesktopComicScreen.DOWNLOADS, hasQueueWork = false))
        // 对照组：那四屏本地行仍故意不起引擎（批 106 那条边界不许漂）
        assertFalse("收藏那一屏仍不起：", desktopComicEngineNeeded(DesktopComicScreen.FAVORITES, hasQueueWork = true))
        assertFalse("源管理那一屏仍不起：", desktopComicEngineNeeded(DesktopComicScreen.SOURCES, hasQueueWork = true))
    }

    /** ⑦ 字面那一半：那一支必须真写成吃 `hasQueueWork`（钉整串表达式而不是标识符）。 */
    @Test
    fun 那一支的字面必须是hasQueueWork() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicPane.kt"))
        assertEquals(
            "DOWNLOADS 那一支要钉整串（只查标识符会放过 `DOWNLOADS -> false`）：",
            1,
            Regex("""DesktopComicScreen\.DOWNLOADS\s*,\s*DesktopComicScreen\.QUEUE\s*->\s*hasQueueWork""").findAll(code).count(),
        )
        assertEquals(
            "也不许把它留在恒假那一组里（那就是批 111 之前的形状）：",
            0,
            Regex("""DesktopComicScreen\.DOWNLOADS[^>\n]*->\s*false""").findAll(code).count(),
        )
    }

    /** ⑧ 展示那一件仍"只吃纯数据与动作"：入队住在容器那一层，与删除同一分家规矩。 */
    /** ⑨ 种子那两句各念各的：命中带"缺几页 / 留几页"，落空说不排队（静默会把"号打错了"读成"补齐坏了"）。 */
    @Test
    fun 种子命中与落空两档各念一句() {
        val hit = desktopComicRepairSeedHitLine("/manga/panda", "19777@1253812", chapter(complete = false, written = 28, expected = 30), DesktopComicShelfRawIdIndex.of(emptyList()))
        assertEquals("命中那一串：", "D_补齐种子 号=/manga/panda 话=19777@1253812 缺=2 留=28 排队=是" +
            " · 本机没有这号的原始记录，排的是目录那一形 _manga_panda", hit)
        val miss = desktopComicRepairSeedMissLine("/manga/panda", "打错的话号")
        assertTrue("落空那一句要点名不排队：" + miss, miss.contains("不排队"))
        assertNotEquals("两档不许塌成同一句：" + hit, hit, miss)
    }

    /** ⑩ 把手与种子走同一条路：入队那一跳在那颗文件里仍恰一处，而那一屏没有第二条入队道。 */
    /** ⑫ 装配那一跳：种子从装配根一路递到那一屏（参数在而没人递 = 屏上按不动也没读数）。 */
    /** ⑪ 参数本身三档：没给、形不对、两截（`%2F` 那一档要解码成号里的斜杠）。 */
    @Test
    fun 那枚种子三档各归各() {
        assertNull("没给就是没给（不许凭空排一笔）：", ForensicFlags.repair(listOf("goda")))
        assertNull("形不对要念一句并交回 null（本轮不补齐）：", ForensicFlags.repair(listOf("--repair=只有一截")))
        val seed = ForensicFlags.repair(listOf("--repair=%2Fmanga%2Fpanda/19777@1253812"))
        assertEquals("号里那截斜杠要解码回斜杠而话号原样：", "/manga/panda" to "19777@1253812", seed)
        assertTrue(
            "登记在 KNOWN 里（漏登记会被念成拼错的参数）：",
            ForensicFlags.KNOWN.any { it.endsWith("--repair") },
        )
    }
}
