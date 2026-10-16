package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第一百零八批 · 表空过那一瞬之后，新排进来的一笔要有人接走。
 *
 * ## 治的是哪一条（不是推测，是包内读数）
 *
 * `_qa/b107_dl2.log`（批 107 第二趟包内复验，窗口落在「探索」+ 详情浮层）：
 *
 * ```
 * D_队列 笔=0 跑=0 待=0 停=0 败=0 完=0 留档=无      ← 引擎装载完、泵起来的那一刻表是空的
 * D_详情 源=goda 号=/manga/pandajunheleisajun-hiyaku 态=上屏 话=15 …
 * D_下载种子 号=/manga/pandajunheleisajun-hiyaku 话=19777@1253812 落在=第 1 话 目录=15 话
 * D_队列 笔=1 跑=0 待=1 停=0 败=0 完=0 留档=无      ← 一直到关窗：待=1 而跑=0，盘上 0 页 0 清单
 * ```
 *
 * 成因是一行：`desktopComicQueuePumpLoop` 里 `nextPending(root) ?: break`。
 * 泵起来时表是空的 ⇒ 那一圈当场退出；之后 `enqueue` 把那一笔排上，而**没有任何一圈在等它** ——
 * 句柄（`session`）没换，那颗 `DisposableEffect(session)` 也就不会把泵再踢一次。
 * 批 106 让「下载队列」那一行自己驱动泵，可那一行也是"有活才起句柄"，而**起句柄那一跳之后表才空过一次**的形同此理。
 *
 * ## 为什么正解是"挂在表上等新的一笔"而不是轮询
 *
 * 每 200 毫秒问一次表也能让读数变好，而那是**假活**：它把"没人等"改成"有人空转"，
 * 并且新增一格延迟常数（改了它屏上没人知道）。这张表本来就是 `MutableStateFlow` ——
 * 新入队那一次赋值自己就会把挂着的这一跳叫醒，零新地基、零空转。
 * 所以 ⑤ 钉等待吃的是 `_tasks` 那一颗，⑥ 负向钉 `delay(`（连同"另立第二颗状态件"一起堵掉）。
 *
 * ## 两档各喂一次（同一枚纪律第七次）
 *
 * ① 表里**已有**待跑那一笔 ⇒ 那一跳立刻交回；② 表**空** ⇒ 那一跳挂着，直到新入队把它唤醒。
 * 只喂 ② 会被"恒不返回"放过，只喂 ① 会被"恒返回 null"放过。
 */
class DesktopComicQueueWakeTest {

    private var root = Files.createTempDirectory("b108wake").toFile()

    private fun freshRoot(): File {
        root = Files.createTempDirectory("b108wake").toFile()
        DesktopComicDownloadQueue.prime(root)
        return root
    }

    /**
     * 收尾把那张**进程级单例**表清干净：本批两笔行为用例都真往表里排过一笔，
     * 而那张表活当整个测试 JVM（前案 `object DesktopGalleryFailureLog`）—— 留一笔就是给后面的用例串味。
     */
    private fun clearTable() {
        DesktopComicDownloadQueue.prime(Files.createTempDirectory("b108clear").toFile())
    }

    /** ① 表里已有待跑那一笔：那一跳立刻交回，不许挂起。 */
    @Test
    fun 有待跑那一笔时立刻交回() = runBlocking {
        freshRoot()
        try {
            DesktopComicDownloadQueue.enqueue(root, "jm", "1461316", "题名", "", "ch-a", "第 1 话", 0, false)
            val taken = withTimeout(2_000) { DesktopComicDownloadQueue.awaitNextPending(root) }
            assertEquals("交回的必须是表里那一笔：", "ch-a", taken.chapterId)
        } finally {
            clearTable()
        }
    }

    /** ② 表空那一格：挂住 → 新入队把它唤醒 → 交回的是**新排进来那一笔**（本批的缺陷本体）。 */
    @Test
    fun 表空那一格等新排进来的一笔() = runBlocking {
        freshRoot()
        val waiting = async(Dispatchers.Default) { DesktopComicDownloadQueue.awaitNextPending(root) }
        try {
            delay(150)
            assertFalse("表空时那一跳不许自己交回（旧那一形在这里返回 null，泵就此退出，之后没人接新的一笔）：",
                waiting.isCompleted)
            DesktopComicDownloadQueue.enqueue(root, "jm", "1461316", "题名", "", "ch-new", "第 2 话", 1, false)
            val taken = withTimeout(2_000) { waiting.await() }
            assertEquals("唤醒之后交回的必须是新排进来那一笔：", "ch-new", taken.chapterId)
        } finally {
            waiting.cancel()
            clearTable()
        }
    }

    /** ③ 旧那一形在泵里一处不剩（正面向的"取待跑恰一处"仍由批 101 ⑨ 那一枚钉，这里不双轨）。 */
    @Test
    fun 泵那一圈吃的是等待那一跳() {
        val pump = DesktopSourceTree.desktopMainSources().first { it.name == "DesktopComicQueueRunner.kt" }
        val code = DesktopSourceTree.codeText(pump)
        assertEquals("不等的那一跳必须零处（留着就是有第二处取表）：",
            0, DesktopSourceTree.codeOccurrences(pump, "DesktopComicDownloadQueue.nextPending("))
        assertEquals("表空时退出那一形必须零处（这一行就是缺陷本体）：", 0, occurrences(code, "?: break"))
    }

    /** ④ 等待吃的是那张表的**档位**，钉整串表达式（标识符看不见死分支）。 */
    @Test
    fun 等待的那一跳看的是待跑档位() {
        val queue = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicDownloadQueue.kt")
        val code = DesktopSourceTree.codeText(queue)
        assertEquals("唤醒条件钉整串：表里有任何一枚待跑（写成恒真就是每次都往下跑、等于没等）：",
            1, occurrences(code, "_tasks.first { tasks -> tasks.any { it.status == DesktopComicQueueStatus.PENDING } }"))
        assertEquals("那一跳必须是 suspend 的（不 suspend 就是恒等当前值，新入队叫不醒它）：",
            1, occurrences(code, "suspend fun awaitNextPending(root: File): DesktopComicQueueTask"))
    }

    /** ⑤ 唤醒走的是那张表自己，不许另立第二颗状态件（两颗状态会漂 —— 本仓踩过两回）。 */
    @Test
    fun 没有第二颗状态件() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicDownloadQueue.kt"))
        // ⚠️ 钉的是**裸词**而不是 `Channel(`：带类型参数那一形写成 `Channel<DesktopComicQueueTask>(1)`，
        //    括号前插着尖括号，拿 `Channel(` 去寻就恒寻不到 —— 台架 P4 第一遍正是从这一格漏过去的（改的是尺子不是笔）。
        assertFalse("不许为唤醒另造一颗 Channel（裸词，带类型参数那一形也算）：" + code.contains("Channel"), code.contains("Channel"))
        assertFalse("也不许另造一颗 MutableSharedFlow（同理钉裸词）：", code.contains("MutableSharedFlow"))
        assertEquals("收表的那三屏仍是那三屏（多一枚就是第二个宿主；批 113 那一屏只订阅「何时再读一次盘」）：",
            listOf("DesktopComicDownloadsPane.kt", "DesktopComicPane.kt", "VeneraDesktopApp.kt"),
            DesktopSourceTree.desktopMainSources()
                .filter { DesktopSourceTree.codeOccurrences(it, "DesktopComicDownloadQueue.tasks.collectAsState()") > 0 }
                .map { it.name }.sorted(),
        )
    }

    /** ⑥ 反轮询：等待那一跳与泵那一圈里都不许有睡眠（一枚延迟常数就是假活）。 */
    @Test
    fun 等待不是轮询() {
        val files = listOf(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicDownloadQueue.kt"),
            DesktopSourceTree.desktopUiSource("DesktopComicQueueRunner.kt"),
        )
        for (file in files) {
            val code = DesktopSourceTree.codeText(file)
            assertEquals(file.name + " 里不许有 delay（挂起等新的一笔不需要延迟常数）：", 0, occurrences(code, "delay("))
            assertEquals(file.name + " 里也不许自己造 Timer：", 0, occurrences(code, "Timer("))
        }
    }

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
}
