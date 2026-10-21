package com.venera.desktop.gallery.ui

import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第一百零六批 · 让「下载队列」那一行**自己驱动泵**。
 *
 * ## 这一批治的是哪一条（不是推测，是批 105 第三趟包内读数）
 *
 * 那份演示表里盘上有一笔 `PENDING`、屏上三行都摆出来了，而 `D_队列 笔=3 跑=0 待=1` —— 那一笔永远不会开始。
 * 成因是 `desktopComicEngineNeeded` 把 `QUEUE` 归到 false 那一支 ⇒ 那一屏拿到的句柄是 null ⇒ 泵那一圈压根走不到。
 * 于是屏上「回到漫画域就接着跑」是半句假话：今天它是"到探索或详情那一屏才接着跑"，
 * 而"看队列的那一屏不驱动队列"本身就缺半件功能。
 *
 * ## 为什么判式要**两档各喂一次**
 *
 * `hasQueueWork` 是调用点给的布尔值。只喂一种值的判式会被"恒真/恒假"放过（本仓踩过的那条纪律），
 * 所以 ① 两档各一枚、② 其余七档各拿两种实参过一遍 —— 那一枚专门咬"顺手把别的屏也接上这枚实参"。
 *
 * ## 屏上那一句的归属
 *
 * 「回到漫画域就接着跑」这句在**详情那一屏**是真话（那一屏本来就有引擎），由批 101 判式 ⑯ 钉着，本批一个字不动它；
 * 只把**队列那一屏**里那半句假话换成本批成立的说法，所以 ⑥ 只负向钉队列那一颗文件（不双轨钉同一件事）。
 */
class DesktopComicQueueRowPumpTest {

    private val queueWorkScreens = listOf(
        // ⚠️ 第一百一十一批改判：`DOWNLOADS` 从前落在这一组（恒假），而那一屏从今天起**自己会造队列活**
        // （缺页那一档的「补齐」把手只入队）⇒ 它改吃 `hasQueueWork`，判式搬到
        // `DesktopComicShelfRepairTest` ⑥⑦。留在这里只会把那条新规矩钉成"不许宽"。
        DesktopComicScreen.FAVORITES, DesktopComicScreen.HISTORY, DesktopComicScreen.SOURCES,
        DesktopComicScreen.EXPLORE, DesktopComicScreen.DETAIL, DesktopComicScreen.READER,
    )

    private fun pendingTask(order: Int) = DesktopComicQueueTask(
        taskId = "jm_1461316_c$order", sourceKey = "jm", comicId = "1461316", comicTitle = "某一本", comicCover = "",
        chapterId = "c$order", chapterTitle = "第${order + 1}話", chapterOrder = order, directoryPath = "downloads/jm/1461316/c$order",
        status = DesktopComicQueueStatus.PENDING, downloadedPages = 0, totalPages = 0, error = "", placeholder = false,
        createTime = 1L, updateTime = 1L,
    )

    /** ① 那一行：有活要干才起引擎，没活就不起 —— 两档各一枚断言。 */
    @Test
    fun 队列那一行按有没有活决定() {
        assertTrue("表里有待跑那一笔时站在这一行要起引擎（泵就挂在那颗句柄上）",
            desktopComicEngineNeeded(DesktopComicScreen.QUEUE, hasQueueWork = true))
        assertFalse("空表还去起一颗 GraalJS 加整源装载，是代价不是准备",
            desktopComicEngineNeeded(DesktopComicScreen.QUEUE, hasQueueWork = false))
    }

    /** ② 其余六档与那枚实参无关：三种恒真、三种恒假，两种实参各过一遍。 */
    @Test
    fun 其余六档不许被那枚实参顺手改宽() {
        for (hasWork in listOf(true, false)) {
            val on = queueWorkScreens.filter { desktopComicEngineNeeded(it, hasQueueWork = hasWork) }
            val off = queueWorkScreens.filterNot { desktopComicEngineNeeded(it, hasQueueWork = hasWork) }
            assertEquals("hasQueueWork=$hasWork 时要引擎的那三屏：", listOf(
                DesktopComicScreen.EXPLORE, DesktopComicScreen.DETAIL, DesktopComicScreen.READER,
            ).sortedBy { it.name }, on.sortedBy { it.name })
            assertEquals("hasQueueWork=$hasWork 时读本地的那三屏：", listOf(
                DesktopComicScreen.FAVORITES, DesktopComicScreen.HISTORY, DesktopComicScreen.SOURCES,
            ).sortedBy { it.name }, off.sortedBy { it.name })
        }
    }

    /** ③ 调用点搬过家：新那一形恰一处，而旧那一形一处不剩（搬家类判式两钉）。 */
    /** ④ "有没有活"从那张表的**档位**算，而不是从"表非空"算。 */
    /** ⑤ 泵仍然只由那一颗句柄说话（本批改的是"什么时候有句柄"，不是"泵怎么跑"）。 */
    @Test
    fun 泵那一支的形状不许动() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicQueueRunner.kt"))
        assertEquals("没有句柄时只读表不跑圈（批 105 ⑮ 钉过另一形，这一枚钉本批行为钉子的那一跳）：",
            1, occurrences(code, "if (engine != null) desktopComicQueuePumpLoop(engine, downloadsRoot)"))
    }

    /** ⑥ 队列那一屏不再念那句假话，改念这一批真成立的那一句。 */
    @Test
    fun 那一屏念得出这一屏会接着跑() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicQueuePane.kt"))
        assertEquals("「回到漫画域」在队列那一颗文件里必须零处（那一屏自己就会把活接下来）：",
            0, occurrences(code, "回到漫画域"))
        val waiting = desktopComicQueueScreenHeader(listOf(pendingTask(0)), primed = true)
        assertTrue("排队那一档要念得出「站在这一屏就接着跑」（本批把它变成真话）：" + waiting,
            waiting.contains("站在这一屏就接着跑"))
        assertFalse("不许塌回「正在读那张表」那一档：" + waiting, waiting.contains("正在读那张表"))
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
