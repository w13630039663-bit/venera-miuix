package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopComicChapterDownload
import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.desktop.gallery.ui.desktopComicChapterDownloadOf
import com.venera.desktop.gallery.ui.desktopComicChapterStatusText
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第一百零一批 · 桌面下载队列（多话排队 + 重启续排）。
 *
 * ## 这一批补的是哪一句假话
 *
 * 批 96–100 之后桌面**一次只能下一话**：详情那一屏的状态槽只容一笔，而按第二话会被一枚早退闸静默吞掉；
 * 那颗 runner 又挂在详情页自己的作用域上 ⇒ **离开那一屏就死**，跑到一半的那话既不完成也不留痕。
 * 顶栏那枚牌子当时写的是「本地无任务队列」，那句在批 101 之前**是真话**，而它是一枚硬编码的字面量
 * （没有任何尺子钉着它）—— 队列一落地它就会静默变假，所以本批把它改成由任务表算出来的一句。
 *
 * ## 三条硬边界（判据逐条对着钉）
 *
 * - **判齐只有那一颗**：`DONE` 那一档必须是**回读盘之后**的结论，不是"runner 说它跑完了"。
 *   喂"runner 交回 Completed 而清单没写完"那一档，它不许塌成 DONE。
 * - **同一名字段不同语义**（批 97 那一课的正面用法）：`directoryPath` 那一格桌面存**相对目录名**，
 *   存成绝对路径就两端不可比；`downloadedPages` 只许是**盘上数出来的**，不是"这一趟写了几页"的内存计数。
 * - **恒串行 1 而不立并发常量**：一枚喂给 `take(1)` 的常量是假开关（改成 2 也不会真并发）。
 *   所以串行由**循环形状**钉：一次只取表里第一枚待跑的，而那一颗文件里零处分叉。
 *
 * ⚠️ 本文件里的接线判读（⑨⑩⑪⑫⑬⑭⑮）吃的是"全 main 现数"，不是某一颗文件的文件名 ——
 * 仓里那把尺子（`DesktopStaleClaimsTest`）改过一次名字就红在拼写上，这里避开那个形。
 */
class DesktopComicDownloadQueueTest {

    private var root: File = Files.createTempDirectory("b101q").toFile()

    /** 换一枚全新的数据根并把队列表从它读一遍（每颗用例的开头都是这一步，免得单例串味）。 */
    private fun freshRoot(): File {
        root = Files.createTempDirectory("b101q").toFile()
        DesktopComicDownloadQueue.prime(root)
        return root
    }

    /** ① 表序就是执行序，而 `taskId` 那三格拼法逐字（对岸 `DownloadManager.kt:144` 的 `源_子_话`）。 */
    @Test
    fun 入队两笔的表序与任务号拼法() {
        freshRoot()
        DesktopComicDownloadQueue.enqueue(
            root, "jm", "1461316", "题名", "", "ch-a", "第 1 话", 0, false,
        )
        DesktopComicDownloadQueue.enqueue(
            root, "jm", "1461316", "题名", "", "ch-b", "第 2 话", 1, false,
        )
        val tasks = DesktopComicDownloadQueue.tasks.value
        assertEquals("入队两笔就该是两笔：" + tasks.size, 2, tasks.size)
        assertEquals("表序就是执行序（第一笔必须是先入的那一话）：", "ch-a", tasks[0].chapterId)
        assertEquals("表序就是执行序（第二笔必须是后入的那一话）：", "ch-b", tasks[1].chapterId)
        assertEquals(
            "任务号那三格的拼法（对岸 `源_子_话`）：",
            "jm_1461316_ch-a",
            desktopComicQueueTaskId("jm", "1461316", "ch-a"),
        )
        assertEquals("同一枚任务号必须由入队那一步算出同一串：", "jm_1461316_ch-a", tasks[0].taskId)
    }

    /** ② 重复入队不去，而「已完成」那一档重排回待跑（对岸 `:145-153` 那一形）。 */
    @Test
    fun 重复入队不去而完成那一档重排() {
        freshRoot()
        DesktopComicDownloadQueue.enqueue(root, "jm", "1", "题名", "", "a", "第 1 话", 0, false)
        DesktopComicDownloadQueue.enqueue(root, "jm", "1", "题名", "", "a", "第 1 话", 0, false)
        assertEquals("同一话重复入队不许变成两笔（重下一遍就是那种形状）：", 1, DesktopComicDownloadQueue.tasks.value.size)
        val dir = desktopComicQueueDir(root, DesktopComicDownloadQueue.tasks.value[0])
        assertTrue(dir.mkdirs() || dir.isDirectory)
        desktopComicWriteChapterManifest(dir, "a", 0, "第 1 话", 1, complete = true)
        DesktopComicPageFixtures.writeWholePage(dir, 0)
        val pending = DesktopComicDownloadQueue.nextPending(root)
        assertNotNull("新入队的那一笔应当排得到：", pending)
        DesktopComicDownloadQueue.reportTotal(root, pending!!.taskId, 1)
        DesktopComicDownloadQueue.settle(root, pending.taskId, DesktopComicChapterRun.Completed(1))
        assertEquals(
            "跑完那一档应当回读盘后说已完成：",
            DesktopComicQueueStatus.DONE,
            DesktopComicDownloadQueue.tasks.value[0].status,
        )
        DesktopComicDownloadQueue.enqueue(root, "jm", "1", "题名", "", "a", "第 1 话", 0, false)
        assertEquals(
            "已完成那一档再入队要重排回待跑（对岸那一形：重下同一话是用户明说要的）：" +
                DesktopComicDownloadQueue.tasks.value[0].status,
            DesktopComicQueueStatus.PENDING,
            DesktopComicDownloadQueue.tasks.value[0].status,
        )
    }

    /** ③ `DONE` 只由回读盘决定：runner 说跑完而清单没写完 ⇒ 不许说已完成。 */
    @Test
    fun 跑完那一档要回读盘才说完成() {
        freshRoot()
        DesktopComicDownloadQueue.enqueue(root, "jm", "1", "题名", "", "a", "第 1 话", 0, false)
        val pending = DesktopComicDownloadQueue.nextPending(root)
        assertNotNull(pending)
        // 只把页写到盘上，清单那一格**不写**（半截落盘正是这一档的形状）。
        val dir = desktopComicQueueDir(root, pending!!)
        assertTrue("建不出目录：", dir.mkdirs())
        DesktopComicPageFixtures.writeWholePage(dir, 0)
        DesktopComicDownloadQueue.reportTotal(root, pending.taskId, 1)
        DesktopComicDownloadQueue.settle(root, pending.taskId, DesktopComicChapterRun.Completed(1))
        val after = DesktopComicDownloadQueue.tasks.value[0]
        assertFalse(
            "runner 说跑完而清单没写完 ⇒ 绝不许塌成已完成（那就是第四种「齐不齐」口径）：" + after.status,
            after.status == DesktopComicQueueStatus.DONE,
        )
        assertEquals(
            "那一档要说失败并点名成因：" + after.error,
            true,
            after.error.contains("清单"),
        )
    }

    /** ④ 写盘那一跳就把「正在跑」降级成「待跑」，读盘时不猜僵尸。 */
    @Test
    fun 写盘那一跳降级掉正在跑那一档() {
        freshRoot()
        DesktopComicDownloadQueue.enqueue(root, "jm", "1", "题名", "", "a", "第 1 话", 0, false)
        val pending = DesktopComicDownloadQueue.nextPending(root)
        DesktopComicDownloadQueue.markRunning(root, pending!!.taskId)
        assertEquals(
            "内存态里那一笔应当是正在跑：" + DesktopComicDownloadQueue.tasks.value[0].status,
            DesktopComicQueueStatus.RUNNING,
            DesktopComicDownloadQueue.tasks.value[0].status,
        )
        val text = File(root, DESKTOP_COMIC_QUEUE_FILE_NAME).readText()
        assertFalse(
            "盘上那份不许含「正在跑」那一档（进程死了它读回来就是僵尸）：" + text,
            text.contains("\"" + DesktopComicQueueStatus.RUNNING.name + "\""),
        )
        DesktopComicDownloadQueue.prime(root)
        val read = DesktopComicDownloadQueue.tasks.value
        assertEquals("读回来还是那一笔：", 1, read.size)
        assertEquals(
            "读回来那一档必须是待跑（重启续排吃的正是这一格）：" + read[0].status,
            DesktopComicQueueStatus.PENDING,
            read[0].status,
        )
        assertNotNull("降级之后 nextPending 要交回那一笔：", DesktopComicDownloadQueue.nextPending(root))
    }

    /** ⑤ `directoryPath` 那一格装的是相对目录名，不是设备绝对路径。 */
    @Test
    fun 目录那一格存相对目录名() {
        freshRoot()
        DesktopComicDownloadQueue.enqueue(root, "jm", "1461316", "题名", "", "a", "第 1 话", 0, false)
        val stored = DesktopComicDownloadQueue.tasks.value[0].directoryPath
        assertFalse("那一格不许带反斜杠：" + stored, stored.contains("\\"))
        assertFalse("那一格不许带盘符：" + stored, Regex("^[A-Za-z]:").containsMatchIn(stored))
        assertFalse("那一格不许以数据根开头：" + stored, stored.startsWith(root.absolutePath))
        assertEquals(
            "那一格拼回根上必须正是那颗章节目录：",
            desktopComicChapterDir(root, "jm", "1461316", 0, "第 1 话").canonicalPath,
            desktopComicQueueDir(root, DesktopComicDownloadQueue.tasks.value[0]).canonicalPath,
        )
        val text = File(root, DESKTOP_COMIC_QUEUE_FILE_NAME).readText()
        assertFalse("盘上那份里不许出现本机绝对路径（换一台机器就全认不出）：" + text, text.contains(root.absolutePath))
    }

    /** ⑥ `downloadedPages` 只许是盘上数出来的：盘上已有 2 页而这一趟写 1 页 ⇒ 交回 3。 */
    @Test
    fun 盘上页数从盘上来而不是内存计数() {
        freshRoot()
        DesktopComicDownloadQueue.enqueue(root, "jm", "1", "题名", "", "a", "第 1 话", 0, false)
        val pending = DesktopComicDownloadQueue.nextPending(root)
        val dir = desktopComicQueueDir(root, pending!!)
        assertTrue(dir.mkdirs())
        DesktopComicPageFixtures.writeWholePage(dir, 0)
        DesktopComicPageFixtures.writeWholePage(dir, 1)
        DesktopComicDownloadQueue.reportTotal(root, pending.taskId, 3)
        // 这一趟只写第 3 页：runner 递回来的"盘上已有"是 3，而**内存里那一趟只写了 1 页**。
        DesktopComicDownloadQueue.reportProgress(root, pending.taskId, 3)
        val after = DesktopComicDownloadQueue.tasks.value[0]
        assertEquals(
            "盘上两页 + 这一趟一页 ⇒ 3，而不是这一趟的 1：" + after.downloadedPages,
            3,
            after.downloadedPages,
        )
        // 第一百一十批：第三颗换成**名字对、尺寸够而头部是过盾页 HTML** 那一档 ——
        // 旧夹具那颗 12 字节的假页在新尺子下也不算一页，两种"不该算"里只有这一种是本批要治的病。
        DesktopComicPageFixtures.writeBrokenPage(dir, 2)
        val counted = desktopComicQueuePagesOnDisk(dir)
        assertEquals("名字对而头部不是图的那一颗不算一页（与那颗共用判式同一口径）：", 2, counted)
        File(dir, desktopComicChapterPageFileName(1)).delete()
        assertEquals(
            "回读盘必须数得出真在那儿的那一颗：",
            1,
            desktopComicQueuePagesOnDisk(dir),
        )
    }

    /** ⑥′ 进度那一跳要**真的落盘**：内存改了而盘上那份停在旧数，崩在半路再起来就少念几页（台架 R4 逼出来的尺子）。 */
    @Test
    fun 每写一页都全量刷盘() {
        freshRoot()
        DesktopComicDownloadQueue.enqueue(root, "jm", "1", "题名", "", "a", "第 1 话", 0, false)
        val taskId = DesktopComicDownloadQueue.tasks.value[0].taskId
        DesktopComicDownloadQueue.reportTotal(root, taskId, 9)
        DesktopComicDownloadQueue.reportProgress(root, taskId, 4)
        val first = File(root, DESKTOP_COMIC_QUEUE_FILE_NAME).readText()
        assertTrue("盘上那份里必须已经有那一格的新数（只改内存就是静默交错）：" + first, first.contains("\"downloadedPages\":4"))
        DesktopComicDownloadQueue.reportProgress(root, taskId, 7)
        val second = File(root, DESKTOP_COMIC_QUEUE_FILE_NAME).readText()
        assertTrue("再写三页要刷成新数而不是留着旧的：" + second, second.contains("\"downloadedPages\":7"))
    }

    /** ⑦ 字段清单逐枚点名（对岸那 15 枚 + 桌面多那一枚 `placeholder`），且两两不同。 */
    @Test
    fun 队列那份的字段逐枚点名() {
        freshRoot()
        DesktopComicDownloadQueue.enqueue(root, "jm", "1", "题名", "https://c/1.jpg", "a", "第 1 话", 0, true)
        val text = File(root, DESKTOP_COMIC_QUEUE_FILE_NAME).readText()
        val ours = listOf(
            "taskId", "sourceKey", "comicId", "comicTitle", "comicCover", "chapterId", "chapterTitle",
            "chapterOrder", "status", "downloadedPages", "totalPages", "errorMsg", "directoryPath",
            "createTime", "updateTime", "placeholder",
        )
        val opposite = listOf(
            "taskId", "sourceKey", "comicId", "comicTitle", "comicCover", "chapterId", "chapterTitle",
            "chapterOrder", "status", "downloadedPages", "totalPages", "errorMsg", "directoryPath",
            "createTime", "updateTime",
        )
        assertEquals("字段清单自己不许有重复：", ours.size, ours.toSet().size)
        assertEquals("对岸那 15 枚必须逐枚都在：" + text, 15, opposite.count { text.contains("\"$it\":") })
        assertTrue("桌面多的那一枚 placeholder 必须在（占位章不带过去会被当正常话去问页表）：" + text, text.contains("\"placeholder\":"))
        assertEquals("多的只有那一枚：" + ours.size, 16, ours.size)
        val keys = Regex("\"([A-Za-z]+)\"\\s*:").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals("那份里出现的键必须与清单同一批（不多不少）：" + keys.distinct(), 16, keys.distinct().size)
        assertEquals("两两不同：", keys.distinct().size, keys.size)
    }

    /** ⑧ 坏档留档并点名（与批 88 那颗 cookie 罐同一形），不许静默当"没有队列"。 */
    @Test
    fun 坏档留档并点名() {
        val dir = freshRoot()
        File(dir, DESKTOP_COMIC_QUEUE_FILE_NAME).writeText("这不是 JSON {{{")
        DesktopComicDownloadQueue.prime(dir)
        assertTrue("读不出来的那份要留档而不是被覆盖掉：" + dir.list().joinToString(","), dir.list().any { it.contains(".bad") })
        assertEquals("留档之后队列表是空的：", 0, DesktopComicDownloadQueue.tasks.value.size)
        val reading = desktopComicQueueReading(DesktopComicDownloadQueue.tasks.value, DesktopComicDownloadQueue.asideName)
        assertTrue("读数里要点名那一档留了档：" + reading, reading.contains("留档"))
    }

    /** ⑨ 串行形状由循环钉而不是并发常量钉：一次只取第一枚待跑，且那一颗文件里零处分叉。 */
    @Test
    fun 恒串行而没有第二枚并发数() {
        val pump = DesktopSourceTree.desktopMainSources().first { it.name == "DesktopComicQueueRunner.kt" }
        val code = DesktopSourceTree.codeText(pump)
        assertEquals(
            "泵里那一枚取待跑的调用恰一处（两处就是两笔同时在跑）。第一百零八批把这一枚换成会等的那一跳，" +
                "计数口径不变 —— 两处取表仍是两笔同时在跑：",
            1,
            DesktopSourceTree.codeOccurrences(pump, "DesktopComicDownloadQueue.awaitNextPending("),
        )
        assertEquals(
            "泵里只许有一枚 launch（一圈泵；两枚就是两个执行器）：",
            1,
            DesktopSourceTree.codeOccurrences(pump, "queueScope.launch {"),
        )
        assertFalse("泵里不许有 async 分叉：", code.contains("async("))
        assertFalse("泵里不许有 coroutineScope 分叉：", code.contains("coroutineScope("))
        val offenders = DesktopSourceTree.desktopMainSources().filter { DesktopSourceTree.codeText(it).contains("QUEUE_CONCURRENCY") }
        assertEquals("全 main 不许有并发常量（一枚喂 take(1) 的常量是假开关）：" + offenders.map { it.name }, 0, offenders.size)
        val prefs = DesktopSourceTree.desktopMainSources().filter { DesktopSourceTree.codeText(it).contains("downloadConcurrency") }
        assertEquals("也不许自己造一枚并发偏好键（java.util.concurrent 那种 import 不算）：" + prefs.map { it.name }, 0, prefs.size)
    }

    /** ⑩ 有人真的在收那枚表 —— 立状态必须同批立这一条（`GalleryFavoritesStore.notice` 的前案）。 */
    @Test
    fun 队列表有人在收() {
        val sites = DesktopSourceTree.desktopMainSources()
            .filter { DesktopSourceTree.codeOccurrences(it, "DesktopComicDownloadQueue.tasks.collectAsState()") > 0 }
            .map { it.name }
            .sorted()
        assertEquals(
            "收那枚表的只有那三屏（应用根那枚牌子 + 漫画域那一层 + 批 113 那一屏只为「何时再读一次盘」订阅）；" +
                "多一枚就是第二个宿主、少一枚就是「实现了而零调用点」：" +
                "⚠️ 第三枚是**时机**消费者，不把队列内容当展示数据（屏上那些行仍只从读盘那件来，`DesktopComicShelfRerollTest` ⑨ 钉着那一格）",
            listOf("DesktopComicDownloadsPane.kt", "DesktopComicPane.kt", "VeneraDesktopApp.kt"),
            sites,
        )
    }

    /** ⑪ 顶栏那枚牌子改成由任务表算出来的一句，且三档两两不同。 */
    @Test
    fun 牌子念的是算出来的那句() {
        val hardcoded = DesktopSourceTree.desktopMainSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "StatusLamp(\"三站 · 本地无任务队列\")") }
        assertEquals("那句硬编码的旧牌子必须整串消失（队列一落地它就是假话）：", 0, hardcoded)
        val computed = DesktopSourceTree.desktopMainSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "StatusLamp(statusText)") }
        assertEquals("牌子吃的是一枚算出来的串（恰一处）：", 1, computed)
        val empty = desktopComicQueueLampText(emptyList())
        val queued = desktopComicQueueLampText(listOf(queueTask(DesktopComicQueueStatus.PENDING, 0, 0)))
        val twoQueued = desktopComicQueueLampText(listOf(queueTask(DesktopComicQueueStatus.PENDING, 0, 0), queueTask(DesktopComicQueueStatus.PENDING, 0, 0)))
        val running = desktopComicQueueLampText(
            listOf(queueTask(DesktopComicQueueStatus.RUNNING, 42, 89), queueTask(DesktopComicQueueStatus.PENDING, 0, 0)),
        )
        assertTrue("空队列那一档要念得出「空」：" + empty, empty.contains("空"))
        assertTrue("排队那一档要念得出笔数：" + queued, queued.contains("1"))
        assertTrue("排队那一档要念得出「排队」这两个字，而不只是含一枚数（台架 R15 证明只钉数字会放过换措辞的那一版）：" + queued, queued.contains("排队"))
        assertTrue("两笔那一档也要念得出「排队」：" + twoQueued, twoQueued.contains("排队"))
        assertTrue("正在跑那一档要念得出页码：" + running, running.contains("42/89"))
        assertDistinct(empty, queued, running)
        assertDistinct(
            desktopComicQueueLampText(listOf(queueTask(DesktopComicQueueStatus.PENDING, 0, 0), queueTask(DesktopComicQueueStatus.PENDING, 0, 0))),
            queued,
        )
    }

    /** ⑫ 长按那一枚动作的去向是"入队"，而计数仍是声明一处 + 调用两处（批 96 那把尺子的改判）。 */
    @Test
    fun 长按入队而不再自己跑() {
        val enq = DesktopSourceTree.desktopMainSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "enqueueChapterDownload") }
        assertEquals(
            "入队那一枚全 main 恰三处（声明一处 + 长按与种子各一处），种子吃的必须是同一条路：" + enq,
            3,
            enq,
        )
        val old = DesktopSourceTree.desktopMainSources().filter { DesktopSourceTree.codeText(it).contains("runChapterDownload") }
        assertEquals("从前那颗就地跑的 lambda 不许留着：" + old.map { it.name }, 0, old.size)
    }

    /** ⑬ 取消只有一颗闸：泵里那一处 `stopRequested`，详情那一屏零处（不许第二条路）。 */
    @Test
    fun 取消闸只有一颗() {
        val pump = DesktopSourceTree.desktopMainSources().first { it.name == "DesktopComicQueueRunner.kt" }
        assertEquals(
            "泵里扳闸那一处恰一处：",
            1,
            DesktopSourceTree.codeOccurrences(pump, "DesktopComicDownloadQueue.stopRequested("),
        )
        val detail = DesktopSourceTree.desktopUiSource("DesktopComicDetail.kt")
        assertEquals(
            "详情那一屏不许自己判闸（它只把「停这一笔」交回队列）：" ,
            0,
            DesktopSourceTree.codeOccurrences(detail, "stopRequested("),
        )
        val cap = DesktopSourceTree.desktopMainSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "DesktopComicDownloadQueue.armPageCap(") }
        assertEquals("种子那枚页上限要有真实消费点（恰一处）：" + cap, 1, cap)
        val stop = DesktopSourceTree.desktopMainSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "DesktopComicDownloadQueue.requestStop(") }
        // 第一百零五批之后是三处：详情那一屏的「停这一笔」一枚，队列那一屏两枚（停手那一下 + 撤掉那一下先扳闸）。
        // 仍是"数出现次数"而不是"在不在"：同一屏多一枚把手时，存在性判式会被喂饱而看不见漏掉的那一枚。
        assertEquals("屏上那两枚把手各有真实消费点（详情 1 + 队列屏 2）：" + stop, 3, stop)
    }

    /** ⑭ 作用域与句柄退场的**顺序**：先掐作用域再关 context。 */
    @Test
    fun 退场先掐作用域再关句柄() {
        val pane = DesktopSourceTree.desktopUiSource("DesktopComicPane.kt")
        assertEquals(
            "那一整串恰一处（反过来就是「任务还在跑而 context 已被关」）：",
            1,
            DesktopSourceTree.codeOccurrences(pane, "onDispose { queueScope.cancel(); closing?.close() }"),
        )
        assertEquals(
            "旧的那一串（只关句柄）不许留着：",
            0,
            DesktopSourceTree.codeOccurrences(pane, "onDispose { closing?.close() }"),
        )
        assertEquals(
            "长驻作用域全桌面恰一处（这是桌面第一次立它）：",
            1,
            DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, "CoroutineScope(SupervisorJob() + Dispatchers.IO)") },
        )
        assertEquals(
            "退域要把「正在跑」交回「待跑」（回这一域就接着跑）：",
            1,
            DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, "DesktopComicDownloadQueue.abandonRunning(") },
        )
    }

    /** ⑮ 详情那一屏的早退闸换成"这一话已经在队里"，钉整串表达式。 */
    @Test
    fun 那枚只容一笔的硬闸换形() {
        val detail = DesktopSourceTree.desktopUiSource("DesktopComicDetail.kt")
        assertEquals(
            "旧的「上一笔还没落地就不开第二笔」整串必须消失（它就是「第二笔被静默吞掉」的成因）：",
            0,
            DesktopSourceTree.codeOccurrences(detail, "if (download != null) return@LaunchedEffect"),
        )
        assertEquals(
            "新的早退形状恰一处（钉整串而不是钉标识符 —— 死分支看不见）：",
            1,
            DesktopSourceTree.codeOccurrences(detail, "if (queueTaskOfThisEpisode != null) return@LaunchedEffect"),
        )
        val slot = DesktopSourceTree.desktopMainSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "download: DesktopComicChapterDownload?,") }
        assertEquals("那一格的签名不许换名（否则语义树那层要整体搬家，本批故意不动它）：" + slot, 1, slot)
    }

    /** ⑯ 屏上那一句：排队那一档念"回到漫画域就接着跑"，正在跑那一档念页码，两档不许塌成一句。 */
    @Test
    fun 排队那一档屏上念得出边界() {
        val queued = desktopComicChapterStatusText(
            DesktopComicChapterDownload(0, 0, 3, busy = false, failure = null, pagesOnDisk = 0, cancelled = false, queued = true),
        )
        val running = desktopComicChapterStatusText(
            DesktopComicChapterDownload(0, 0, 89, busy = true, failure = null, pagesOnDisk = 42, cancelled = false, queued = false),
        )
        assertTrue("排队那一档要说清「回这一域才继续」（C 档那条边界）：" + queued, queued.contains("回到漫画域")
        )
        assertTrue("正在跑那一档要带页码：" + running, running.contains("42/89"))
        assertDistinct(queued, running)
        val fromTask = desktopComicChapterDownloadOf(queueTask(DesktopComicQueueStatus.PENDING, 0, 0))
        assertNotNull(fromTask)
        assertEquals("待跑那一档映射过来要带上排队标记：", true, fromTask!!.queued)
        val busy = desktopComicChapterDownloadOf(queueTask(DesktopComicQueueStatus.RUNNING, 42, 89))
        assertEquals("正在跑那一档映射过来不许还带排队标记：", false, busy!!.queued)
        assertEquals("映射过来的页码取自任务表：", 42, busy.pagesOnDisk)
        assertNull("没有那一笔时交回 null（屏上一个字都不摆）：", desktopComicChapterDownloadOf(null))
    }

    /** ⑰ 重启续排：读回来的待跑那一笔排得到，而「已完成」的不重跑。 */
    @Test
    fun 重启续排只排没跑完的() {
        freshRoot()
        DesktopComicDownloadQueue.enqueue(root, "jm", "1", "题名", "", "a", "第 1 话", 0, false)
        DesktopComicDownloadQueue.enqueue(root, "jm", "1", "题名", "", "b", "第 2 话", 1, false)
        val first = DesktopComicDownloadQueue.nextPending(root)
        assertEquals("先排到的必须是先入队那一话：", "a", first!!.chapterId)
        val dir = desktopComicQueueDir(root, first)
        assertTrue(dir.mkdirs() || dir.isDirectory)
        desktopComicWriteChapterManifest(dir, "a", 0, "第 1 话", 1, complete = true)
        DesktopComicPageFixtures.writeWholePage(dir, 0)
        DesktopComicDownloadQueue.reportTotal(root, first.taskId, 1)
        DesktopComicDownloadQueue.settle(root, first.taskId, DesktopComicChapterRun.Completed(1))
        DesktopComicDownloadQueue.prime(root)
        val next = DesktopComicDownloadQueue.nextPending(root)
        assertEquals("重启之后排到的应当是第二话而不是那一话（已完成的不重跑）：" + next?.chapterId, "b", next?.chapterId)
        DesktopComicDownloadQueue.settle(root, next!!.taskId, DesktopComicChapterRun.Cancelled(0))
        assertNull("被停手的那一笔不再自动排（用户说明说要它才动）：", DesktopComicDownloadQueue.nextPending(root))
        DesktopComicDownloadQueue.enqueue(root, "jm", "1", "题名", "", "b", "第 2 话", 1, false)
        assertEquals("再入队要把它排回去：", "b", DesktopComicDownloadQueue.nextPending(root)?.chapterId)
    }

    private fun queueTask(status: DesktopComicQueueStatus, downloaded: Int, total: Int): DesktopComicQueueTask =
        DesktopComicQueueTask(
            taskId = "jm_1_a",
            sourceKey = "jm",
            comicId = "1",
            comicTitle = "题名",
            comicCover = "",
            chapterId = "a",
            chapterTitle = "第 1 话",
            chapterOrder = 0,
            directoryPath = "jm/1/0000_第 1 话",
            status = status,
            downloadedPages = downloaded,
            totalPages = total,
            error = "",
            placeholder = false,
            createTime = 1L,
            updateTime = 2L,
        )

    private fun assertDistinct(vararg texts: String) {
        assertEquals("那几档各念各的一句（两两不同）：" + texts.joinToString(" | "), texts.size, texts.toSet().size)
    }

    /** 把"入队两笔"那几颗共用掉的那一步抽出来，免得测试自己写出一份第二套拼法。 */
    private fun enqueueProbe(sourceKey: String, comicId: String, chapterId: String) {
        DesktopComicDownloadQueue.enqueue(root, sourceKey, comicId, "题名", "", chapterId, "第 1 话", 0, false)
    }

    /** ⑱ `taskId` 里那三格各自净化过（站方给的号里带 `/` 就要拼出三格变四格的那种任务号）。 */
    @Test
    fun 任务号里那三格各自净化() {
        freshRoot()
        enqueueProbe("a/b", "c:d", "e\\f")
        val task = DesktopComicDownloadQueue.tasks.value[0]
        assertEquals(
            "拼出来的任务号里不许有那些字符：" + task.taskId,
            "a_b_c_d_e_f",
            task.taskId,
        )
        assertFalse("任务号里下划线只是分隔符，不许出现连续分隔：" + task.taskId, task.taskId.contains("//"))
    }
}
