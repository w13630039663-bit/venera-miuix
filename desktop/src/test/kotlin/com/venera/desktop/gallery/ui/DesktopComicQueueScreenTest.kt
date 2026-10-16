package com.venera.desktop.gallery.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.DesktopComicPageFixtures
import com.venera.desktop.gallery.data.desktopComicQueuePagesOnDisk
import com.venera.desktop.gallery.data.desktopComicQueueRelativeDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 第一百零五批 · 漫画域「下载队列」那一屏（一列任务 + 「停这一笔」+「撤掉这一笔」）。
 *
 * ## 这一批治的是哪一条
 *
 * 批 101 之后队列**能跑而看不见**：全局只有顶栏那枚牌子的一句摘要，而「停这一笔」的把手只挂在详情那一屏 ——
 * 用户按完第三话退回列表时，手上那一笔就没有把手了；对岸那颗「撤掉这一笔」（不删已写的页）桌面一处都没有。
 * 所以判据的重点不是"有没有这一屏"，而是：
 *
 * 1. **两枚把手各只改一件事**（停手 = 扳那颗协作式闸而行留下；撤掉 = 从表里摘掉那一行而页一颗不动）——
 *    写成一枚「取消」按钮就塌成了第二种事实；
 * 2. **撤掉不许顺手删页**（删除归批 98 那一屏，那里说的是**结果**；这一屏只说**过程**）；
 * 3. 屏上那四档（空 / 排队 / 正在跑 / 都排完）不许互相塌，尤其"没有排队而有已完成"不许念成"队列空"。
 *
 * ## 渲的是 [DesktopComicQueueContent] 而不是整颗 pane
 *
 * 与批 97 那一屏同一条分家规矩：装载那件碰表与盘，展示那件只吃纯数据与两枚回调 ⇒ 喂着离屏渲得动
 * （`runComposeUiTest`，进程内、零窗口、不动用户光标）。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopComicQueueScreenTest {

    private fun occurrences(hay: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var n = 0
        var at = hay.indexOf(needle)
        while (at != -1) {
            n += 1
            at = hay.indexOf(needle, at + needle.length)
        }
        return n
    }

    private fun mainOccurrences(literal: String): Int =
        DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, literal) }

    private fun task(
        order: Int,
        status: DesktopComicQueueStatus,
        title: String = "第${order + 1}話",
        written: Int = 0,
        total: Int = 0,
        error: String = "",
    ): DesktopComicQueueTask {
        val chapterId = "c$order"
        return DesktopComicQueueTask(
            taskId = "jm_1461316_$chapterId",
            sourceKey = "jm",
            comicId = "1461316",
            comicTitle = "某一本",
            comicCover = "",
            chapterId = chapterId,
            chapterTitle = title,
            chapterOrder = order,
            directoryPath = desktopComicQueueRelativeDir("jm", "1461316", order, title),
            status = status,
            downloadedPages = written,
            totalPages = total,
            error = error,
            placeholder = false,
            createTime = 1L,
            updateTime = 1L,
        )
    }

    /** ① 那一行**追加在末尾**：前五行逐枚点 key 一字不变（插中间会把既有尺子指到别的话上去）。 */
    @Test
    fun 队列那一行追加在末尾() {
        val rows = desktopComicRows()
        assertEquals("漫画域从今天起六行（探索 / 收藏夹 / 阅读历史 / 源管理 / 本机已下载 / 下载队列）", 6, rows.size)
        assertEquals(
            "前五行的次序与 key 一字不变（追加的意义就在这里，插中间这一条会红）",
            listOf("comic-explore", "comic-favorites", "comic-history", "comic-sources", "comic-downloads"),
            rows.take(5).map { it.key },
        )
        val last = rows.last()
        assertEquals("comic-queue", last.key)
        assertEquals("下载队列", last.title)
        assertEquals("队", last.glyph)
        assertEquals(DesktopGallerySectionContent.ComicQueue, last.content)
        assertEquals("那一屏是真屏（摆队列里每一笔），不许挂「未实现」的理由", null, last.reason)
    }

    /** ② 域路那几处闸都认这一档：漫画域放行串 + 载荷优先判断 + 五条域路的穷尽 `when`。 */
    @Test
    fun 域路闸认这一档() {
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicPane.kt"))
        assertEquals(
            "「不是漫画域的页」那一串必须加这一档（漏了起窗就炸那句 error，而那是屏上量不到的形状）",
            1,
            occurrences(pane, "content != DesktopGallerySectionContent.ComicQueue"),
        )
        assertEquals(
            "载荷优先那一颗判断要认这一档（否则这一行永远落到 EXPLORE）",
            1,
            occurrences(pane, "content == DesktopGallerySectionContent.ComicQueue -> DesktopComicScreen.QUEUE"),
        )
        assertEquals("「这一屏恒不要引擎」那一形已就地改判（第一百零六批：恒 false 让那一屏只读不驱动，" +
            "包内读数 `跑=0 待=1`）—— 它回来就是要抹掉那一行的驱动权", 0,
            occurrences(pane, "DesktopComicScreen.QUEUE -> false"))
        assertEquals(
            "以逗号结尾的那一串 = 六处域路的穷尽 when（图库 / 收藏 / 搜索 / 画师 / 发现 / 设置）+ 行模型那一枚 content 位",
            7,
            mainOccurrences("DesktopGallerySectionContent.ComicQueue,"),
        )
    }

    /** ③ 分家规矩：装载件收 `File` 而展示件不吃饭局；读盘只一跳；「撤掉」那一侧零处删文件。 */
    @Test
    fun 装载与展示分在两件() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicQueuePane.kt"))
        assertEquals("展示那件只吃纯数据，离屏才渲得动", 0, occurrences(code, "EngineSession"))
        assertEquals("读盘只在那一跳里发生（组合期一次都不许碰盘）", 1, occurrences(code, "withContext(Dispatchers.IO)"))
        assertEquals("撤掉不许顺手删页：这一屏一处 deleteRecursively 都没有", 0, occurrences(code, "deleteRecursively("))
        assertEquals("全 main 仍恰一处（那一颗住在批 98 的删除件里，不在这里）", 1, mainOccurrences("deleteRecursively("))
        assertEquals(1, occurrences(code, "internal fun DesktopComicQueuePane(downloadsRoot: File"))
        assertEquals(1, occurrences(code, "internal fun DesktopComicQueueContent(tasks: List<DesktopComicQueueTask>"))
        // 撤掉那一句里那个页数只能来自 `drop` 的回值（现量），不许是行上的内存数、更不许是恒 0：
        // 「还剩几页」是用户决定要不要去「本机已下载」那一屏删的依据。
        assertEquals("页数的出处只能是那颗件的回值：", 1,
            occurrences(code, "val pagesLeft = withContext(Dispatchers.IO) { DesktopComicDownloadQueue.drop("))
        assertEquals("递进屏上那句的就是那个回值：", 1, occurrences(code, "desktopComicQueueRemovedLine(task, pagesLeft)"))
    }

    /** ④ 四档各一句、两两不同；「没有排队而有已完成」不许塌成「队列空」。 */
    @Test
    fun 四档表头各念一句() {
        val empty = desktopComicQueueScreenHeader(emptyList(), primed = true)
        val waiting = desktopComicQueueScreenHeader(listOf(task(0, DesktopComicQueueStatus.PENDING)), primed = true)
        val running = desktopComicQueueScreenHeader(
            listOf(task(0, DesktopComicQueueStatus.RUNNING, written = 3, total = 9), task(1, DesktopComicQueueStatus.PENDING)),
            primed = true,
        )
        val allDone = desktopComicQueueScreenHeader(listOf(task(0, DesktopComicQueueStatus.DONE, written = 4, total = 4)), primed = true)
        assertTrue("空表那一句：" + empty, empty.contains("本机队列空"))
        assertTrue("排队那一句要念得出笔数：" + waiting, waiting.contains("1 笔排队"))
        assertTrue("正在跑那一句要念得出页码：" + running, running.contains("3/9 页"))
        assertTrue("都排完那一句要念得出笔数：" + allDone, allDone.contains("1 笔都排完了"))
        assertEquals("四句必须互不相同：" + listOf(empty, waiting, running, allDone),
            4, listOf(empty, waiting, running, allDone).distinct().size)
        assertFalse("有已完成那一档时不许念成空表（用户会以为按下去没排上）：" + allDone, allDone.contains("本机队列空"))
    }

    /** ⑤ 每一行那一句：档位、页码、题名带书名号、是哪一本都念得出。 */
    @Test
    fun 行里那一句四格齐() {
        val running = desktopComicQueueRowLine(task(1, DesktopComicQueueStatus.RUNNING, written = 2, total = 5))
        assertTrue("档位：" + running, running.contains("正在下"))
        assertTrue("页码：" + running, running.contains("2/5 页"))
        assertTrue("第几话：" + running, running.contains("第 2 话"))
        assertTrue("本子号（一屏可能摆着好几本）：" + running, running.contains("1461316"))
        val failed = desktopComicQueueRowLine(task(0, DesktopComicQueueStatus.FAILED, error = "取不到那一页"))
        assertTrue("失败那一档要把原话念出来，不许只说「失败了」：" + failed, failed.contains("取不到那一页"))
        assertAllDistinct(listOf(running, failed))
    }

    /** ⑥ 「停这一笔」只挂在真在飞的那一笔上（排队那一笔还没开始，扳闸没有对象）。 */
    @Test
    fun 停手只挂在正在跑那一行() {
        assertFalse("排队那一笔不许摆「停这一笔」", desktopComicQueueCanStop(task(0, DesktopComicQueueStatus.PENDING)))
        assertTrue("正在跑那一笔必须摆：手上那一笔的把手就在这一行",
            desktopComicQueueCanStop(task(0, DesktopComicQueueStatus.RUNNING)))
        assertFalse("已排完那一笔没有可停的东西", desktopComicQueueCanStop(task(0, DesktopComicQueueStatus.DONE)))
        assertFalse("已停手那一笔没有可停的东西（而它照样摆得出「撤掉这一笔」）：",
            desktopComicQueueCanStop(task(0, DesktopComicQueueStatus.STOPPED)))
        assertFalse("失败了那一档也没有可停的东西：", desktopComicQueueCanStop(task(0, DesktopComicQueueStatus.FAILED)))
        // 顺序钉子（不是存在钉子）：撤掉那一枚必须**先扳闸再摘行** —— 行一摘，表里就没人管那一趟了。
        // ⚠️ 只看 `onRemove` 那一颗 lambda 的身体：全文件的 `indexOf` 会被**同一屏另一处合法调用**
        //    （停手那一枚的 requestStop，文本上本来就排在 drop 之前）喂饱 —— 那种判式撤掉那枚漏扳闸也照绿。
        val removeBody = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicQueuePane.kt"))
            .substringAfter("onRemove = { task ->")
        val dropAt = removeBody.indexOf("DesktopComicDownloadQueue.drop(")
        val gate = removeBody.lastIndexOf("DesktopComicDownloadQueue.requestStop(", dropAt)
        assertTrue("撤掉那一枚要先扳闸再摘行（反了就成了「屏上已念撤掉而它一路写到最后一页」）：gate=$gate drop=$dropAt",
            gate in 0 until dropAt)
    }

    /** ⑦ 语义树：两枚把手各落到**哪一颗**回调（写成一枚「取消」就塌了）。 */
    @Test
    fun 两枚把手各落到哪一颗回调() = runComposeUiTest {
        val running = task(0, DesktopComicQueueStatus.RUNNING, written = 1, total = 4)
        var stops = 0
        var removes = 0
        val removedTasks = mutableListOf<String>()
        // 只摆一行：两枚把手的文案在同一屏上是**按行重复**的，摆两行就撞"节点不唯一"那一堵墙
        //（量具的坏，不是产品的坏 —— 多行那一形由 ⑧ 与 ⑤ 各自钉）。
        setContent {
            DesktopComicQueueContent(
                tasks = listOf(running),
                primed = true,
                lastResult = null,
                onStop = { stops += 1 },
                onRemove = { task -> removes += 1; removedTasks.add(task.taskId) },
            )
        }
        onNodeWithText(desktopComicQueueRowLine(running)).assertExists()
        onNodeWithText(DESKTOP_COMIC_QUEUE_STOP_LABEL).performClick()
        assertEquals("「停这一笔」按一次只扳一次：" + stops, 1, stops)
        assertEquals("停手那一下不许顺手摘行：" + removes, 0, removes)
        onNodeWithText(DESKTOP_COMIC_QUEUE_REMOVE_LABEL).performClick()
        assertEquals("「撤掉这一笔」按一次只摘一次：" + removes, 1, removes)
        assertEquals("摘的是哪一行要递得出来：" + removedTasks, listOf(running.taskId), removedTasks)
        assertEquals("两枚把手的文案不许同形：" + listOf(DESKTOP_COMIC_QUEUE_STOP_LABEL, DESKTOP_COMIC_QUEUE_REMOVE_LABEL),
            2, listOf(DESKTOP_COMIC_QUEUE_STOP_LABEL, DESKTOP_COMIC_QUEUE_REMOVE_LABEL).distinct().size)
        assertEquals("装载件里扳闸只在那一枚停手把手上（撤掉那一枚另有一处，见 ⑥′ 的顺序钉子）：", 2,
            occurrences(DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicQueuePane.kt")),
                "DesktopComicDownloadQueue.requestStop("))
    }

    /** ⑧ 排队那一行：摆得出「撤掉这一笔」而**不摆**「停这一笔」（同一颗 `setContent` 不许调两次，所以分两趟）。 */
    @Test
    fun 排队那一行只有一枚把手() = runComposeUiTest {
        val waiting = task(0, DesktopComicQueueStatus.PENDING)
        setContent { DesktopComicQueueContent(listOf(waiting), primed = true, lastResult = null, onStop = {}, onRemove = {}) }
        onNodeWithText(DESKTOP_COMIC_QUEUE_STOP_LABEL).assertDoesNotExist()
        onNodeWithText(DESKTOP_COMIC_QUEUE_REMOVE_LABEL).assertExists()
        onNodeWithText(desktopComicQueueRowLine(waiting)).assertExists()
    }

    /** ⑧′ 正在跑那一行：两枚都摆得出（这就是"手上那一笔退回列表后仍有把手"那一格）。 */
    @Test
    fun 正在跑那一行有两枚把手() = runComposeUiTest {
        val running = task(0, DesktopComicQueueStatus.RUNNING, written = 1, total = 3)
        setContent { DesktopComicQueueContent(listOf(running), primed = true, lastResult = null, onStop = {}, onRemove = {}) }
        onNodeWithText(DESKTOP_COMIC_QUEUE_STOP_LABEL).assertExists()
        onNodeWithText(DESKTOP_COMIC_QUEUE_REMOVE_LABEL).assertExists()
    }

    /** ⑨ 数据层：摘掉那一行之后表少一枚，而那棵目录的页数一字不变（§2①②的正面钉子）。 */
    @Test
    fun 摘掉一行而盘上页数不动() {
        val root = Files.createTempDirectory("b105-drop").toFile()
        try {
            DesktopComicDownloadQueue.prime(root)
            DesktopComicDownloadQueue.enqueue(root, "jm", "1461316", "某一本", "", "c0", "第1話", 0, false)
            DesktopComicDownloadQueue.enqueue(root, "jm", "1461316", "某一本", "", "c1", "第2話", 1, false)
            val before = DesktopComicDownloadQueue.tasks.value
            assertEquals("先排上两笔：" + before.map { it.chapterId }, 2, before.size)
            val dir = File(root, before[0].directoryPath)
            assertTrue("目录建出来：" + dir, dir.mkdirs() || dir.isDirectory)
            DesktopComicPageFixtures.writeWholePage(dir, 0)
            DesktopComicPageFixtures.writeWholePage(dir, 1)
            assertEquals("盘上两颗页：" + dir, 2, desktopComicQueuePagesOnDisk(dir))
            val left = DesktopComicDownloadQueue.drop(root, before[0].taskId)
            assertEquals("撤掉那一句要念得出盘上还剩几页（现量，不是推断）：", 2, left)
            assertEquals("表少一枚：" + DesktopComicDownloadQueue.tasks.value.map { it.chapterId },
                1, DesktopComicDownloadQueue.tasks.value.size)
            assertEquals("盘上那两颗一颗没动：" + dir, 2, desktopComicQueuePagesOnDisk(dir))
            assertEquals("摘掉要落盘（那份表就是它的凭据）：", 1,
                File(root, "download_tasks.json").readText().let { Regex("\"taskId\"").findAll(it).count() })
            assertEquals("摘没过的号再撤一次不许炸而只回 0（幂等）：", 0, DesktopComicDownloadQueue.drop(root, before[0].taskId))
        } finally {
            root.deleteRecursively()
        }
    }

    /** ⑩ 撤的正是**在飞那一笔** ⇒ 那颗协作式闸不许被摘行顺手清掉（否则它会一路写到最后一页）。 */
    @Test
    fun 撤掉在飞那一笔时那颗闸还咬着() {
        val root = Files.createTempDirectory("b105-stop").toFile()
        try {
            DesktopComicDownloadQueue.prime(root)
            DesktopComicDownloadQueue.enqueue(root, "jm", "1461316", "某一本", "", "c0", "第1話", 0, false)
            val id = DesktopComicDownloadQueue.tasks.value[0].taskId
            DesktopComicDownloadQueue.markRunning(root, id)
            DesktopComicDownloadQueue.requestStop(id)
            DesktopComicDownloadQueue.drop(root, id)
            assertTrue("行摘了而闸还在（下一页边界就停手）：", DesktopComicDownloadQueue.stopRequested(id))
            assertFalse("表里已经没有那一行：", DesktopComicDownloadQueue.tasks.value.any { it.taskId == id })
        } finally {
            root.deleteRecursively()
        }
    }

    /** ⑪ 屏上读数那一行自己念得出（屏上每句行为断言都要答得出"哪次量的"）。 */
    @Test
    fun 那一屏自己念一行读数() {
        val tasks = listOf(
            task(0, DesktopComicQueueStatus.RUNNING, written = 2, total = 6),
            task(1, DesktopComicQueueStatus.PENDING),
            task(2, DesktopComicQueueStatus.FAILED),
        )
        val line = desktopComicQueueScreenReading(tasks, stops = 2, removes = 1)
        assertTrue("行数那格：" + line, line.startsWith("D_队列屏 行=3"))
        assertTrue("停手那格：" + line, line.contains("停=2"))
        assertTrue("撤掉那格：" + line, line.contains("撤=1"))
        assertFalse("读数不进界面（它只进 stdout）：" + line, line.contains("三站"))
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicQueuePane.kt"))
        assertEquals("那一行由这一屏自己念（不是顶栏牌子那句的替换）：", 1, occurrences(code, "println(desktopComicQueueScreenReading("))
    }

    /** ⑫ 顶栏那枚牌子没被这一屏替换掉：两句各有各的对象（牌子说整体，这一屏说每一笔）。 */
    @Test
    fun 顶栏牌子没被替换() {
        val bar = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryTopBar.kt"))
        assertEquals("牌子那一颗仍恰一处挂着：", 1, occurrences(bar, "StatusLamp(statusText)"))
        assertEquals("仍由应用根算那一句：", 1,
            occurrences(DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")),
                "desktopComicQueueLampText("))
        // 第一百一十一批改判：入队那一路从一枚变两枚（详情长按 + 「本机已下载」那一行的「补齐」）。
        // 这一枚钉的是**这一屏自己不开第三条路**，数的是全 main 的总枚数而不是「这一屏零枚」。
        assertEquals("全 main 只有那两处入队把手（详情长按 + 补齐；队列那一屏自己不开第三条路）：", 2,
            mainOccurrences("DesktopComicDownloadQueue.enqueue("))
    }

    /** ⑬ 两句文案：撤掉那一句念得出"页留着"，停手那一句不许说"已停"（协作式，掐不掉的就不许诺）。 */
    @Test
    fun 两枚把手各自那一句各说各的事() {
        val removed = desktopComicQueueRemovedLine(task(1, DesktopComicQueueStatus.RUNNING), pagesLeft = 3)
        assertTrue("撤掉那一句要点名盘上还剩几页：" + removed, removed.contains("3 页"))
        assertTrue("要指路到删除那一屏：" + removed, removed.contains("本机已下载"))
        assertTrue("说清是撤掉而不是删除：" + removed, removed.contains("撤掉了"))
        val stopped = desktopComicQueueStoppedLine(task(0, DesktopComicQueueStatus.RUNNING))
        assertFalse("停手不许说成「已停/已暂停」（那是立刻停的口气，而桌面是页边界才停）：" + stopped,
            stopped.contains("已停"))
        assertTrue("要说清写到哪儿为止：" + stopped, stopped.contains("页边界"))
        assertAllDistinct(listOf(removed, stopped))
    }

    /** ⑭ 包内实测抓到那一枚缺陷的形状：站在这一行时**引擎刻意不起**，而那张表照样要读回来。 */
    @Test
    fun 没有引擎也要把那张表读回来() {
        val seed = Files.createTempDirectory("b105-seed").toFile()
        val empty = Files.createTempDirectory("b105-empty").toFile()
        val live = Files.createTempDirectory("b105-live").toFile()
        try {
            DesktopComicDownloadQueue.prime(seed)
            DesktopComicDownloadQueue.enqueue(seed, "goda", "2130882", "包内那一本", "", "1", "第1話", 0, false)
            DesktopComicDownloadQueue.enqueue(seed, "goda", "2130882", "包内那一本", "", "2", "第2話", 1, false)
            val text = File(seed, "download_tasks.json").readText()
            assertEquals("前置：种子上两笔：" + text, 2, Regex("\"taskId\"").findAll(text).count())
            live.mkdirs()
            File(live, "download_tasks.json").writeText(text)
            // 先把内存那一格倒空（吃一颗**没有那份表**的目录），于是后面念出的 2 笔只能来自 starter 那一跳。
            DesktopComicDownloadQueue.prime(empty)
            assertEquals("前置：换一份没有表的目录要清空内存：" + DesktopComicDownloadQueue.tasks.value.size,
                0, DesktopComicDownloadQueue.tasks.value.size)
            // `Dispatchers.Unconfined` 让那一跳就地跑完（不靠 sleep 也不靠轮询）：这一颗要量的是"没有引擎时读不读表"。
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            desktopComicStartQueuePump(scope, session = null, downloadsRoot = live)
            val rows = DesktopComicDownloadQueue.tasks.value
            assertEquals("没有引擎也必须把那张表读回来（旧形状先 return ⇒ 屏上念「本机队列空」而盘上有两笔）：" + rows.size,
                2, rows.size)
            assertTrue("读回来过那一格要跟着置真（那一屏第一档吃它）：", DesktopComicDownloadQueue.primed.value)
            scope.cancel()
        } finally {
            seed.deleteRecursively()
            empty.deleteRecursively()
            live.deleteRecursively()
        }
    }

    /** ⑮ 同一件事的字面钉子：那颗 starter 里不许再有"没有引擎就直接 return"那一形。 */
    @Test
    fun 泵那颗入口不许在引擎缺席时直接返回() {
        val runner = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicQueueRunner.kt"),
        )
        assertEquals("旧那一形（先 return 才读表）不许回来：", 0, occurrences(runner, "session ?: return"))
        assertEquals("读表那一跳要在引擎判式**之前**：", 1, occurrences(runner, "DesktopComicDownloadQueue.prime(downloadsRoot)"))
        assertTrue(
            "引擎缺席时只读表而不跑圈（跑圈那一颗仍要引擎）：",
            runner.indexOf("DesktopComicDownloadQueue.prime(downloadsRoot)") < runner.indexOf("if (engine != null)"),
        )
    }

    /** ⑯ 「还没读到那张表」单独一档，不许与"读到了而空"同形（包内实测到的那一帧）。 */
    @Test
    fun 没读到那张表时不许念队列空() = runComposeUiTest {
        val notYet = desktopComicQueueScreenHeader(emptyList(), primed = false)
        val reallyEmpty = desktopComicQueueScreenHeader(emptyList(), primed = true)
        assertTrue("那一档要念得出在读：" + notYet, notYet.contains("正在读"))
        assertNotEquals("还没读到 与 读到了没有 是两件事：" + notYet, notYet, reallyEmpty)
        setContent { DesktopComicQueueContent(emptyList(), primed = false, lastResult = null, onStop = {}, onRemove = {}) }
        onNodeWithText(notYet).assertExists()
        onNodeWithText(reallyEmpty).assertDoesNotExist()
        assertEquals("装载件要把手上那一格递下去：", 1,
            occurrences(DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicQueuePane.kt")), "primed = primed,"))
    }

    private fun assertAllDistinct(lines: List<String>) {
        assertEquals("这些句子必须互不相同（塌成一句就是把两种事实说成一种）：" + lines,
            lines.size, lines.distinct().size)
    }
}
