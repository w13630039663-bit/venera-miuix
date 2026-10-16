package com.venera.desktop.gallery.ui

import com.venera.desktop.gallery.data.DesktopComicChapterRun
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.desktopComicChapterCancelledLine
import com.venera.desktop.gallery.data.desktopComicChapterDownloadLine
import com.venera.desktop.gallery.data.desktopComicChapterProgressLine
import com.venera.desktop.gallery.data.desktopComicDownloadChapter
import com.venera.desktop.gallery.data.desktopComicQueueDir
import com.venera.desktop.gallery.data.desktopComicQueueReading
import com.venera.engineprobe.EngineSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * 第一百零一批 · 队列的执行器（**唯一**那一圈泵）。
 *
 * ## 为什么这一颗住在域那一层而不是详情那一屏
 *
 * 详情那一屏的 runner 从前挂在 `rememberCoroutineScope()` 上 ⇒ **离开那一屏就死**，跑到一半的那话既不完成也不留痕。
 * 引擎句柄归漫画域那一层持有（`DesktopComicPane.kt` 里那枚 `var session by remember`，退域才关），
 * 所以泵也挂在那一层：换屏、退详情都不断，而**退出漫画域**会协作式停手 —— 详情那一屏那一行念「回到漫画域就接着跑」
 * 而不是「已暂停」，因为任务表留在盘上而页一颗都没删。
 * 第一百零六批起，「下载队列」那一行**自己也驱动这一圈泵**（有活才起句柄，那一档见 `desktopComicEngineNeeded`）——
 * 从前它只读不驱动，于是那一屏念得出「排队中」而那一笔永远不动。
 *
 * ## 这一颗文件同时钉着三件事（各有判据）
 *
 * - **恒串行 1**：一次只从表里取**第一枚**待跑的那一笔，跑完才取下一枚；整个文件里只有一枚 `launch`（一圈泵）。
 *   这里**故意不立并发常量** —— 一枚喂给 `take(1)` 的常量是假开关，改成 2 也不会真并发。
 * - **只有一颗取消闸**：`shouldCancel` 递出去的是队列那枚 [DesktopComicDownloadQueue.stopRequested]，
 *   而它吃的是**同一格** —— 屏上那枚把手（`requestStop`）与取证种子那枚页上限（`armPageCap`）都扳它。
 * - **落盘那一跳的口径没换**：页还是由批 96/100/102/103 那颗 [desktopComicDownloadChapter] 写的，
 *   本批只把"谁来跑、跑几笔、跑到哪"这一层挪出了详情那一屏。
 *   ⚠️ 那一整串实参的顺序（**号在前、话号在后**）由 `DesktopComicDetailWiringTest` ⑪ 钉着，改判后钉在本文件。
 */
internal fun desktopComicStartQueuePump(queueScope: CoroutineScope, session: EngineSession?, downloadsRoot: File) {
    val engine = session
    queueScope.launch {
        // ⚠️ 读表这一跳**不在**"有没有引擎"那一支里。第一百零五批包内实测抓到的形状：
        // 站在「下载队列」那一行时引擎刻意不起（那一屏只吃盘上那张表，见 `desktopComicEngineNeeded`），
        // 而旧写法第一行就是 `session ?: return` ⇒ 表从没被读回来，盘上明明有三笔而屏上念「本机队列空」
        //（图证与 `D_队列屏 行=0` 同一刻念出 —— 判据 ⑭ 现在钉着这件事）。
        DesktopComicDownloadQueue.prime(downloadsRoot)
        println(desktopComicQueueReading(DesktopComicDownloadQueue.tasks.value, DesktopComicDownloadQueue.asideName))
        if (engine != null) desktopComicQueuePumpLoop(engine, downloadsRoot)
    }
}

/**
 * 那一圈泵：一笔一笔跑 → **没活时挂在表上等下一笔** → 退场时把「正在跑」交回「待跑」。
 *
 * 读表那一跳住在 [desktopComicStartQueuePump] 而不是这里 —— 没有引擎时也要有人把表读回来。
 *
 * 第一百零八批：这一圈从前在表空那一刻 `?: break` 退出，而句柄没换就不会再被踢一次 ⇒
 * 之后排进来的一笔一直没人接（包内读数 `待=1 跑=0` 到关窗）。现在取表那一枚换成
 * [DesktopComicDownloadQueue.awaitNextPending] —— 挂在一张 `MutableStateFlow` 上等新的一笔，
 * 不轮询、不丢唤醒。
 *
 * ⚠️ `finally` 那一格不是清理样式：泵被掐掉（退域，或挂在那一等时作用域被取消）时手上那一笔还停在
 * `RUNNING` 那一档，不交回待跑的话，回到这一域它**永远不会再被排上** —— 而顶栏那枚牌子会念一句「正在下」念到天荒地老。
 */
internal suspend fun desktopComicQueuePumpLoop(engine: EngineSession, root: File) {
    try {
        // 作用域被掐掉之后不许再去问引擎要下一页（那一句的近亲是批 80 真跑撞到的 `Context execution was cancelled.`）。
        while (coroutineContext[Job]?.isActive == true) {
            val task = DesktopComicDownloadQueue.awaitNextPending(root)
            desktopComicRunOneTask(engine, root, task)
        }
    } finally {
        DesktopComicDownloadQueue.abandonRunning(root)
        println(desktopComicQueueReading(DesktopComicDownloadQueue.tasks.value, DesktopComicDownloadQueue.asideName))
    }
}

/**
 * 跑**一笔**：问页表 → 逐页落盘 → **回读盘**之后才定档。
 *
 * 页表那一问只有跑到这里才做（入队时并不知道分母），而它问出来的页数要立刻报回表里 ——
 * 顶栏与详情那一句「正在下 第 k 话 · i/M 页」吃的就是这一格，缺了它分母恒 0。
 *
 * ⚠️ `written` 这里记的是**盘上已有**的页数（进度那一格递回来的分子），不是"这一趟写了几页" ——
 * 批 100 那条读数口径分得很清，而队列那一格要的是前者（屏上要说"已有几页"）。
 */
private suspend fun desktopComicRunOneTask(engine: EngineSession, root: File, task: DesktopComicQueueTask) {
    val taskId = task.taskId
    DesktopComicDownloadQueue.markRunning(root, taskId)
    val dir = desktopComicQueueDir(root, task)
    var knownPages = 0
    var onDisk = 0
    val outcome = runCatching {
        val pages = engine.pages(task.comicId, EngineSession.Episode(task.chapterTitle, task.chapterId, task.placeholder))
        knownPages = pages.size
        DesktopComicDownloadQueue.reportTotal(root, taskId, pages.size)
        val run = desktopComicDownloadChapter(dir, task.comicId, task.chapterId, task.chapterOrder, task.chapterTitle, pages.size,
            shouldCancel = { DesktopComicDownloadQueue.stopRequested(taskId) },
            progress = { written, expected ->
                DesktopComicDownloadQueue.reportProgress(root, taskId, written)
                onDisk = written
                println(desktopComicChapterProgressLine(task.comicId, task.chapterOrder, written, expected))
            },
        ) { page -> engine.pageImage(pages[page], task.comicId, task.chapterId) }
        pages.size to run
    }
    val settled = desktopComicChapterOutcome(task.chapterOrder, outcome, knownPages, onDisk)
    val cause = outcome.exceptionOrNull()
    println(
        when {
            cause != null ->
                desktopComicChapterDownloadLine(task.comicId, task.chapterOrder, settled.written, settled.total, cause = cause)

            settled.cancelled ->
                desktopComicChapterCancelledLine(task.comicId, task.chapterOrder, settled.pagesOnDisk, settled.total)

            else ->
                desktopComicChapterDownloadLine(task.comicId, task.chapterOrder, settled.written, settled.total, complete = true)
        },
    )
    when {
        cause != null -> DesktopComicDownloadQueue.fail(root, taskId, settled.failure ?: "取页没成交")
        outcome.getOrNull()?.second is DesktopComicChapterRun.Cancelled ->
            DesktopComicDownloadQueue.settle(root, taskId, DesktopComicChapterRun.Cancelled(settled.written))

        else -> DesktopComicDownloadQueue.settle(root, taskId, DesktopComicChapterRun.Completed(settled.written))
    }
    println(desktopComicQueueReading(DesktopComicDownloadQueue.tasks.value, DesktopComicDownloadQueue.asideName))
}
