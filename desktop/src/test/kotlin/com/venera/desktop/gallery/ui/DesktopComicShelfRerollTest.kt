package com.venera.desktop.gallery.ui

import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.desktopComicShelfRerollKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 第一百一十三批 · 「补齐」跑完之后那一行会不会自己改口。
 *
 * 批 111 装了把手、批 112 让它排原号，而那一屏的**重读时机**仍挂在"入队那一刻"（盘上那时什么都没变），
 * 于是补齐真跑完之后屏上还念「缺 2 页」+ 一枚还能按的把手 —— 包内 A 趟读数就是这一串
 * （`_qa/desktop-exe-2026-10-15-b112/run-raw-a.log` 里 `页=2/30 全=是` 之后再无一行 `D_本机下载`）。
 * 这一批钉的是"何时再读一次盘"，不改读侧那件、不改行模型。
 */
class DesktopComicShelfRerollTest {

    private fun task(
        status: DesktopComicQueueStatus,
        pages: Int = 0,
        order: Int = 0,
    ) = DesktopComicQueueTask(
        taskId = "jm_1461316_" + order,
        sourceKey = "jm",
        comicId = "1461316",
        comicTitle = "本子",
        comicCover = "",
        chapterId = "19777@" + order,
        chapterTitle = "第01话",
        chapterOrder = order,
        directoryPath = "jm/1461316/0000_第01话",
        status = status,
        downloadedPages = pages,
        totalPages = 30,
        error = "",
        createTime = 1L,
        updateTime = 2L,
        placeholder = false,
    )

    /** ① 缺陷本体：那一笔跑到 `DONE` ⇒ 键要变（屏上才会再读一次盘）。 */
    @Test
    fun 补完那一笔推得动重读() {
        val before = desktopComicShelfRerollKey(listOf(task(DesktopComicQueueStatus.RUNNING, pages = 28)))
        val after = desktopComicShelfRerollKey(listOf(task(DesktopComicQueueStatus.DONE, pages = 30)))
        assertNotEquals("那一笔从 RUNNING 落 DONE 而键没变 ⇒ 那一屏永远不重读：", before, after)
    }

    /** ② 另一头：逐页刷进度**不许**推重读（每一页都去全量读盘就是批 110 那把尺子的浪费）。 */
    @Test
    fun 逐页刷进度不推重读() {
        val a = desktopComicShelfRerollKey(listOf(task(DesktopComicQueueStatus.RUNNING, pages = 28)))
        val b = desktopComicShelfRerollKey(listOf(task(DesktopComicQueueStatus.RUNNING, pages = 29)))
        val c = desktopComicShelfRerollKey(listOf(task(DesktopComicQueueStatus.RUNNING, pages = 30)))
        assertEquals("还在跑的那一笔刷页码就推重读（28 与 29）：", a, b)
        assertEquals("还在跑的那一笔刷页码就推重读（28 与 30）：", a, c)
    }

    /** ③ 排上队而还没开跑 ⇒ 盘上什么都没变，不必重读。 */
    @Test
    fun 排队与开跑之间不推重读() {
        val pending = desktopComicShelfRerollKey(listOf(task(DesktopComicQueueStatus.PENDING)))
        val running = desktopComicShelfRerollKey(listOf(task(DesktopComicQueueStatus.RUNNING)))
        assertEquals("PENDING 变 RUNNING 就推重读（那时盘上还是老样子）：", pending, running)
    }

    /** ④ 终局三档各算一次迁移 —— 失败与停手同样改了盘（补缺页失败会留下半截）。逐档点名，不比"两两不同"。 */
    @Test
    fun 终局三档各推一次() {
        assertEquals(
            "DONE 那一档：", "完=1 败=0 停=0",
            desktopComicShelfRerollKey(listOf(task(DesktopComicQueueStatus.DONE, pages = 30))),
        )
        assertEquals(
            "FAILED 那一档：", "完=0 败=1 停=0",
            desktopComicShelfRerollKey(listOf(task(DesktopComicQueueStatus.FAILED, pages = 29))),
        )
        assertEquals(
            "STOPPED 那一档：", "完=0 败=0 停=1",
            desktopComicShelfRerollKey(listOf(task(DesktopComicQueueStatus.STOPPED, pages = 12))),
        )
    }

    /** ⑤ 空表与只有待跑的表 ⇒ 同一串（还什么都没落地）。 */
    @Test
    fun 没落地时那一串是同一句() {
        assertEquals(
            "空表与只有 PENDING 的表念的不是一句：",
            desktopComicShelfRerollKey(emptyList()),
            desktopComicShelfRerollKey(listOf(task(DesktopComicQueueStatus.PENDING), task(DesktopComicQueueStatus.RUNNING, order = 1))),
        )
    }

    /** ⑥ 接线：那一屏把这枚键吃进 `LaunchedEffect` 的**键位**（钉整串，标识符看不见"进没进键位"）。 */
    @Test
    fun 那枚键吃在装载那一跳的键位() {
        val pane = DesktopSourceTree.desktopUiSource("DesktopComicDownloadsPane.kt")
        assertEquals(
            "装载那一跳的键位上没有这枚键（整串钉，别只认标识符）：",
            1,
            DesktopSourceTree.codeOccurrences(pane, "LaunchedEffect(downloadsRoot, pass, rerollKey)"),
        )
    }

    /** ⑦ 接线：队列是**订阅**来的而那颗文件里零处轮询（批 108 立的那条在这里同样成立）。 */
    @Test
    fun 队列是订阅来的而那颗文件不轮询() {
        val pane = DesktopSourceTree.desktopUiSource("DesktopComicDownloadsPane.kt")
        assertEquals("队列表只许被订阅一次（两处就是有人在别处另起一条路）：", 1, DesktopSourceTree.codeOccurrences(pane, "collectAsState()"))
        assertEquals(
            "那一屏里轮询的形一处都不许有（`delay(` 零处）：",
            0,
            DesktopSourceTree.codeOccurrences(pane, "delay("),
        )
    }

    /** ⑧ 接线：入队那一刻不再推重数（圈进 `fireRepair` 那一颗 lambda 里数，别在全文件数）。 */
    @Test
    fun 入队那一刻不再推重数() {
        val pane = DesktopSourceTree.desktopUiSource("DesktopComicDownloadsPane.kt")
        val body = DesktopSourceTree.codeText(pane)
            .substringAfter("val fireRepair: (DesktopComicDownloadedChapter) -> Unit = { chapter ->")
            .substringBefore("var repairSeedHandled")
        assertEquals("入队那一刻还推着 `pass`（那时盘上还是 28 页，重数出来的还是假话）：" + body, 0, body.split("pass += 1").size - 1)
        // 删除那两路（把手第二按与 `--delete=` 种子）都是**同步**改盘的，它们那两次推进**必须留着**
        // （撤了就成了"删完屏上不改口"）；落地时现数是两处，不是凭"应该只有一处"推的。
        assertEquals("删除那一路的重数推进被一起撤了：", 2, DesktopSourceTree.codeOccurrences(pane, "pass += 1"))
    }

    /** ⑨ 批 112 那三颗不许回流：回收仍在要用的那一刻现读，组合期不读队列内容。 */
    @Test
    fun 回收仍在要用的那一刻现读() {
        val pane = DesktopSourceTree.desktopUiSource("DesktopComicDownloadsPane.kt")
        assertEquals(
            "队列表被读成展示数据的那一格只许一处（`.value` 在 lambda 里）：",
            1,
            DesktopSourceTree.codeOccurrences(pane, "DesktopComicDownloadQueue.tasks.value"),
        )
        assertEquals("回收那张表仍是现读的 lambda：", 1, DesktopSourceTree.codeOccurrences(pane, "val shelfRawIdIndex = {"))
    }
}
