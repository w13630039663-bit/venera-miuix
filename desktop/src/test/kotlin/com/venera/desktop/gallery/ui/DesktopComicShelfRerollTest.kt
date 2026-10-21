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
    /** ⑦ 接线：队列是**订阅**来的而那颗文件里零处轮询（批 108 立的那条在这里同样成立）。 */
    /** ⑧ 接线：入队那一刻不再推重数（圈进 `fireRepair` 那一颗 lambda 里数，别在全文件数）。 */
    /** ⑨ 批 112 那三颗不许回流：回收仍在要用的那一刻现读，组合期不读队列内容。 */
}
