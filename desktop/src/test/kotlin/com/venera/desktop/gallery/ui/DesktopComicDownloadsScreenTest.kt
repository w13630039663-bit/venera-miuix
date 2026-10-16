package com.venera.desktop.gallery.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.desktop.gallery.data.DesktopComicDeleteOutcome
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.DesktopComicDownloadedGroup
import com.venera.desktop.gallery.data.desktopComicChaptersByComic
import com.venera.desktop.gallery.data.desktopComicDownloadedChapters
import com.venera.desktop.gallery.data.desktopComicDownloadedSkipped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 批 97 · 漫画域「本机已下载」那一屏。
 *
 * ## 这一屏治的是哪一条
 *
 * 批 96 把整话写到盘上并在交付物里量到 89 颗 / 24,905,629 B，而桌面**没有任何一处读那棵树** ——
 * 用户按了长按、盘上多了 24 MB，任何一屏都数不着。这一批补的就是那一条读侧，所以判据的重点不是"有没有这一屏"，
 * 而是**屏上那一句会不会骗用户去删掉还有用的东西**，以及"读不出/缺页"那两档有没有被塌成"没有"。
 *
 * ## 渲的是 [DesktopComicDownloadsContent] 而不是整颗 pane
 *
 * 整颗 pane 要读盘（一颗 `File`），而装载那件与展示那件在本批一开始就分成两件：
 * 展示那件只吃 `DesktopComicDownloadListing` 这份纯数据，能被喂着离屏渲（与详情那一屏同一条分家规矩）。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopComicDownloadsScreenTest {

    private fun chapter(order: Int, title: String, expected: Int, written: Int, complete: Boolean, bytes: Long = 1024L) =
        DesktopComicDownloadedChapter(
            sourceKey = "jm", comicId = "1461316", chapterId = "c" + order, order = order, title = title,
            expectedPages = expected, writtenPages = written, bytes = bytes, complete = complete,
        )

    private fun listingOf(vararg chapters: DesktopComicDownloadedChapter, skipped: Int = 0, rootExists: Boolean = true) =
        DesktopComicDownloadListing(
            groups = desktopComicChaptersByComic(chapters.toList()),
            skipped = skipped,
            rootExists = rootExists,
        )

    /** ① 两行摆得出：齐的那一行念「已下全」，缺的那一行点名缺几页。 */
    @Test
    fun 齐与缺两行各摆一句() = runComposeUiTest {
        val complete = chapter(1, "第2話", expected = 3, written = 3, complete = true)
        val partial = chapter(2, "第3話", expected = 5, written = 2, complete = false)
        setContent { DesktopComicDownloadsContent(listingOf(complete, partial), armedKey = null, lastResult = null, onRepair = {}, onDelete = {}) }
        onNodeWithText(desktopComicDownloadedRowLine(complete)).assertExists()
        onNodeWithText(desktopComicDownloadedRowLine(partial)).assertExists()
        assertTrue("齐那一行没念「已下全」：" + desktopComicDownloadedRowLine(complete),
            desktopComicDownloadedRowLine(complete).contains("已下全"))
        assertTrue("缺那一行必须点名缺几页（用户据这一句决定删不删）：" + desktopComicDownloadedRowLine(partial),
            desktopComicDownloadedRowLine(partial).contains("缺 3 页"))
        assertTrue("行里要带上是哪一本（一屏可能摆着好几本）：" + desktopComicDownloadedRowLine(complete),
            desktopComicDownloadedRowLine(complete).contains("1461316"))
    }

    /** ② 三种「还没有东西」各念一句，两两不许相同。 */
    @Test
    fun 三态空句各念一句() = runComposeUiTest {
        val noDir = desktopComicDownloadedScreenHeader(listingOf(rootExists = false))
        val emptyDir = desktopComicDownloadedScreenHeader(listingOf())
        val unreadable = desktopComicDownloadedScreenHeader(listingOf(skipped = 2))
        assertTrue("目录没建那一档：" + noDir, noDir.contains("还没建出来"))
        assertTrue("目录在而空那一档：" + emptyDir, emptyDir.contains("一条都没有"))
        assertTrue("读不出那一档要念得出条数：" + unreadable, unreadable.contains("2 条清单读不出"))
        assertEquals("三句必须互不相同（塌成一句就是把\"没读到\"说成\"没有\"）：" + listOf(noDir, emptyDir, unreadable),
            3, listOf(noDir, emptyDir, unreadable).distinct().size)
        assertFalse("读不出那一档不许说\"还没存过\"：" + unreadable, unreadable.contains("还没存过"))
        setContent { DesktopComicDownloadsContent(listingOf(skipped = 2), armedKey = null, lastResult = null, onRepair = {}, onDelete = {}) }
        onNodeWithText(unreadable).assertExists()
    }

    /** ③ 有东西那一句：本子数、话数、齐的那几话都念得出。 */
    @Test
    fun 有东西那一句三格齐() {
        val line = desktopComicDownloadedScreenHeader(
            listingOf(chapter(0, "a", 2, 2, true), chapter(1, "b", 4, 1, complete = false)),
        )
        assertTrue("本子那格：" + line, line.contains("1 本"))
        assertTrue("话数那格：" + line, line.contains("2 话"))
        assertTrue("齐的那格只数齐的：" + line, line.contains("齐了 1 话"))
        assertFalse("有东西时不许念空态：" + line, line.contains("一条都没有"))
    }

    /** ④ 分家规矩：装载那件读盘只一跳，展示那件不吃 `File` 也不吃引擎句柄。 */
    @Test
    fun 装载与展示分在两件() {
        val file = DesktopSourceTree.desktopUiSource("DesktopComicDownloadsPane.kt")
        val code = DesktopSourceTree.codeText(file)
        assertFalse("这一屏又去吃引擎句柄了（那就渲不出来，也测不动）：", code.contains("EngineSession"))
        assertEquals("读盘那一跳不止一处（组合期碰盘就是这一族的老形状）：", 1, DesktopSourceTree.codeOccurrences(file, "withContext(Dispatchers.IO)"))
        // 批 98 改判：展示那件多收两样（武装在哪一行、上一次的果）与一个动作 —— 仍然全是纯数据，`File` 依旧只住在装载那件。
        // 第一百一十一批再改判：多收一枚「补齐」动作（那一行只入队，入队本身仍住在容器那一层）。
        assertTrue("展示那件的形参不是纯数据与动作：" ,
            code.contains("internal fun DesktopComicDownloadsContent(state: DesktopComicDownloadListing, armedKey: String?, lastResult: String?, onRepair: (DesktopComicDownloadedChapter) -> Unit, onDelete: (DesktopComicDownloadedChapter) -> Unit)"))
        assertEquals("整颗 pane 只许一处读那一棵树", 1, DesktopSourceTree.codeOccurrences(file, "desktopComicDownloadedChapters(downloadsRoot)"))
    }

    /** ⑤ 分岔与引擎闸：那一行走的是本地盘，一站都不该问源站。 */
    @Test
    fun 那一屏不吃饭局也不问站方() {
        assertEquals(
            "「本机已下载」没被分到那一档：" + desktopComicScreen(DesktopGallerySectionContent.ComicDownloads, hasComic = false, hasRead = false),
            DesktopComicScreen.DOWNLOADS,
            desktopComicScreen(DesktopGallerySectionContent.ComicDownloads, hasComic = false, hasRead = false),
        )
        assertFalse("那一屏白起一颗 GraalJS（它读的是盘上那棵树）", desktopComicEngineNeeded(DesktopComicScreen.DOWNLOADS, hasQueueWork = false))
        // 对照：载荷优先那条老规矩没被新档顶掉（开着详情时仍然先渲染详情）。
        assertEquals("开着详情时那一行不该把屏换走", DesktopComicScreen.DETAIL,
            desktopComicScreen(DesktopGallerySectionContent.ComicDownloads, hasComic = true, hasRead = false))
        assertEquals("域里的档数（添一行就要在这里跟着数）：" + DesktopComicScreen.entries.size, 8, DesktopComicScreen.entries.size)
    }

    /** ⑥ 接线：那一档真的接到那一屏上，而挂载点只许一处。 */
    @Test
    fun 那一档接到那一屏() {
        val pane = DesktopSourceTree.desktopUiSource("DesktopComicPane.kt")
        assertEquals("分岔里没把那一档递给那一屏：", 1, DesktopSourceTree.codeOccurrences(pane, "DesktopComicScreen.DOWNLOADS -> DesktopComicDownloadsPane("))
        assertEquals("那一屏的挂载点不止一处：", 1, DesktopSourceTree.codeOccurrences(pane, "DesktopComicDownloadsPane("))
        // 根由写那一侧同一颗算式给（两处各算一遍就会长歪），但**读侧不许拼名**（见读侧那颗判据）。
        assertEquals("downloads 根只许一处递给读侧：", 1, DesktopSourceTree.codeOccurrences(pane, "DesktopComicDownloadsPane(desktopComicDownloadsRoot(config.dataDir), deleteSeed = config.deleteSeed, repairSeed = config.repairSeed)"))
    }

    /** ⑦ 取证读数与上屏那句是两个说法，而读数六格齐全（屏上那句给用户，这行给量具）。 */
    @Test
    fun 读数那行六格而与上屏不同串() {
        val state = listingOf(chapter(0, "a", 3, 3, true), chapter(1, "b", 3, 1, complete = false), skipped = 4)
        val reading = desktopComicDownloadedReading(state)
        listOf("D_本机下载", "本=1", "话=2", "齐=1", "缺=1", "跳=4", "字节=").forEach {
            assertTrue("读数少了那一格：" + reading, reading.contains(it))
        }
        val screen = desktopComicDownloadedScreenHeader(state)
        assertFalse("读数被搬上屏了（`D_` 那类东西不进界面）：" + screen, screen.contains("D_"))
        assertTrue("上屏那句得是用户读得通的话：" + screen, screen.contains("本机存过"))
    }

    /** ⑧ 空表也要报得出（这是"下了而看不见"那一族的反面：不许静默）。 */
    @Test
    fun 空表那行照样六格() {
        val reading = desktopComicDownloadedReading(listingOf(rootExists = false))
        assertEquals("空的那一行：" + reading, "D_本机下载 本=0 话=0 齐=0 缺=0 跳=0 字节=0", reading)
    }

    // ── 批 98 · 那一屏要删得掉 ────────────────────────────────────────────────

    /** ⑨ 行上有**可见**把手（这一屏的行没有主动作 ⇒ 不该让人靠长按去猜）。 */
    @Test
    fun 行上摆得出那枚删除把手() = runComposeUiTest {
        val one = chapter(1, "第2話", expected = 3, written = 3, complete = true)
        setContent { DesktopComicDownloadsContent(listingOf(one), armedKey = null, lastResult = null, onRepair = {}, onDelete = {}) }
        onNodeWithText(desktopComicDeleteIdleLabel(2)).assertExists()
        assertFalse("把手不许挂长按（桌面没有右键，那一档在详情那屏是因为整行已被\"打开\"占了）：",
            DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicDownloadsPane.kt")).contains("onLongClick"))
    }

    /** ⑩ 两步：没武装过只武装，武装着再按才真删（纯函数那一档，不靠人眼点界面确认）。 */
    @Test
    fun 第一按只武装第二按才真删() {
        assertFalse("第一按就该问都不问：", desktopComicDeleteShouldFire(armedKey = null, rowKey = "jm/1461316/1"))
        assertTrue("同一行第二按才真删：", desktopComicDeleteShouldFire("jm/1461316/1", "jm/1461316/1"))
        assertNotEquals("两档那句必须不同（相同就等于没确认那一步）：" +
            listOf(desktopComicDeleteIdleLabel(1), desktopComicDeleteArmedLabel(1)),
            desktopComicDeleteIdleLabel(1), desktopComicDeleteArmedLabel(1),
        )
    }

    /** ⑪ 武装着点另一行 ⇒ 确认对准那一行，上一行的武装不许接过来。 */
    @Test
    fun 换一行就重新武装() {
        assertFalse("武装在别行而这一按是第一按：",
            desktopComicDeleteShouldFire(armedKey = "jm/1461316/1", rowKey = "jm/1461316/2"))
        assertTrue("再按本行才算第二按：",
            desktopComicDeleteShouldFire(desktopComicDeleteNextKey("jm/1461316/1", "jm/1461316/2"), "jm/1461316/2"))
        assertEquals("武装那一格只许记住「当下这一行」：", "jm/1461316/2",
            desktopComicDeleteNextKey("jm/1461316/1", "jm/1461316/2"))
    }

    /** ⑫ 四档屏上句**逐档点名**（台架 N10 现量：从前只钉"四句互不相同"，而 `释放=` 那格的数值差就把两句顶开了）。 */
    @Test
    fun 四档结果各说一句而没删成不说已删() {
        val one = chapter(1, "第2話", expected = 3, written = 3, complete = true)
        val lines = listOf(
            desktopComicDeleteScreenLine(one, DesktopComicDeleteOutcome.Deleted(pages = 3, freedBytes = 4096L)),
            desktopComicDeleteScreenLine(one, DesktopComicDeleteOutcome.Absent),
            desktopComicDeleteScreenLine(one, DesktopComicDeleteOutcome.Rejected("这一屏认不出那一目录")),
            desktopComicDeleteScreenLine(one, DesktopComicDeleteOutcome.Failed(leftFiles = 2, leftBytes = 20L, reason = "占用")),
        )
        assertTrue("删掉那一档要说得出删掉了：" + lines[0], lines[0].startsWith("删掉了"))
        lines.drop(1).forEach {
            assertFalse("没删成的档不许邀功（删掉了 / 已删除）：" + it, it.startsWith("删掉了") || it.contains("已删除"))
        }
        assertTrue("「本来就没了」那一档要念得出没动东西：" + lines[1], lines[1].contains("没动任何东西"))
        assertTrue("拒绝那一档要念得出为什么没动：" + lines[2], lines[2].contains("没让删"))
        assertTrue("删不动那一档要念得出还剩几颗：" + lines[3], lines[3].contains("还剩"))
        assertTrue("删掉那句要念得出释放了多少：" + lines[0], lines[0].contains("4.0") || lines[0].contains("KB") || lines[0].contains("4096"))
        assertFalse("屏上句不许带取证读数那串：" + lines, lines.any { it.contains("D_") })
    }

    /** ⑬ 端到端那一档：在**真盘**上删，屏上数与盘上数一起改（不吃返回值、不只看屏）。 */
    @Test
    fun 删完那一屏与盘上一起重数() = runComposeUiTest {
        val root = Files.createTempDirectory("b98screen").toFile()
        val first = chapterOnDisk(root, "jm", "1461316", 1, "第2話", pages = 2)
        chapterOnDisk(root, "jm", "1461316", 2, "第3話", pages = 2)
        // `onAllNodesWithText` 的接收者是测试上下文，所以这颗只能住在这一趟里面（写成成员函数就够不着它）。
        val screenSays: (DesktopComicDownloadListing) -> Boolean = {
            onAllNodesWithText(desktopComicDownloadedScreenHeader(it)).fetchSemanticsNodes().isNotEmpty()
        }
        setContent { DesktopComicDownloadsPane(root, deleteSeed = null, repairSeed = null) }
        waitUntil("等那一屏把盘上那两棵数出来") { screenSays(readFrom(root)) }
        assertTrue("盘上那棵还在（前置）：" + first, first.isDirectory)
        onNodeWithText(desktopComicDeleteIdleLabel(2)).performClick()
        waitForIdle()
        assertTrue("第一按之后盘上必须还在（两步那一条）：" + first, first.isDirectory)
        onNodeWithText(desktopComicDeleteArmedLabel(2)).performClick()
        waitUntil("第二按之后盘上该没了") { !first.exists() }
        assertFalse("目标那棵该没了：" + root.walkTopDown().filter { it.isDirectory }.toList(), first.exists())
        assertTrue("同本的别的话不许跟着少：", File(File(File(root, "jm"), "1461316"), "0002_第3話").isDirectory)
        waitUntil("删完那一屏要跟着重数") { screenSays(readFrom(root)) }
        assertTrue("屏上那句要念得出只剩一话：" + desktopComicDownloadedScreenHeader(readFrom(root)),
            desktopComicDownloadedScreenHeader(readFrom(root)).contains("1 话"))
    }


    /** ⑭ 展示那件仍然只吃纯数据与动作（读盘只在装载那一件，且整颗文件只一跳）。 */
    @Test
    fun 展示那件仍不碰盘() {
        val file = DesktopSourceTree.desktopUiSource("DesktopComicDownloadsPane.kt")
        val code = DesktopSourceTree.codeText(file)
        assertEquals("读盘那一跳不止一处（删除那一路走另一颗件）：", 1, DesktopSourceTree.codeOccurrences(file, "withContext(Dispatchers.IO)"))
        assertFalse("展示那件去吃 File 了：" + code, code.contains("internal fun DesktopComicDownloadsContent(state: DesktopComicDownloadListing, downloadsRoot: File"))
        assertTrue("展示那件要递得出动作与武装那一行：" + code,
            code.contains("armedKey: String?") && code.contains("onDelete: (DesktopComicDownloadedChapter) -> Unit"))
        // 两回而不是一处：把手第二次按下与 `--delete=` 种子**必须同一颗 runner**（另开一条取证专用路就量不到用户那一条）。
        assertEquals("删除那颗件被递进来的路数不对（屏上那颗与种子那颗各一处）：", 2, DesktopSourceTree.codeOccurrences(file, "desktopComicDeleteChapter("))
    }

    /** ⑮ 结果那一句上屏（"删掉了多少"与"删不动"都得让用户看见，而读数只进 stdout）。 */
    @Test
    fun 结果那句上屏() = runComposeUiTest {
        val one = chapter(1, "第2話", expected = 3, written = 3, complete = true)
        val line = desktopComicDeleteScreenLine(one, DesktopComicDeleteOutcome.Deleted(pages = 3, freedBytes = 12L))
        setContent { DesktopComicDownloadsContent(listingOf(one), armedKey = null, onRepair = {}, onDelete = {}, lastResult = line) }
        onNodeWithText(line).assertExists()
    }

    /** 渲一屏之前先把盘上那份读出来（判据 ⑬ 用它核对"屏上的数=盘上的数"）。 */
    private fun readFrom(root: java.io.File): DesktopComicDownloadListing = DesktopComicDownloadListing(
        groups = desktopComicChaptersByComic(desktopComicDownloadedChapters(root)),
        skipped = desktopComicDownloadedSkipped(root),
        rootExists = root.isDirectory,
    )

    /** 在盘上造一条真话（清单 + 非空页），交回那一目录。 */
    private fun chapterOnDisk(root: java.io.File, sourceKey: String, comicId: String, order: Int, title: String, pages: Int): java.io.File {
        val dir = java.io.File(java.io.File(root, sourceKey).let { java.io.File(it, comicId) }, order.toString().padStart(4, '0') + "_" + title)
        dir.mkdirs()
        for (index in 0 until pages) java.io.File(dir, (index + 1).toString().padStart(4, '0') + ".jpg").writeBytes(ByteArray(index + 3))
        java.io.File(dir, "chapter.json").writeText(
            "{\"chapterId\":\"$comicId\",\"title\":\"$title\",\"order\":$order,\"expectedPages\":$pages,\"complete\":true}"
        )
        return dir
    }
}
