package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 82.5 · 进那一屏时**先滚到哪一页**这一格（纯函数 + 接线次序）。
 *
 * 为什么要有这颗纯函数：`LazyListState.scrollToItem` 吃的是**合法索引**，而种子那一路的页号
 * 是外人打进来的（可以比页数大、可以是负数，也可以在本页表还没回来时对着 0 页）。
 * 直接把种子递过去就会撞在"请求一个不存在的项"那一档 —— 而那一条在屏上根本没有解释。
 * 折到哪一档是**这一颗**说了算，所以它得能被喂着量。
 */
class DesktopComicReaderStartPageTest {

    @Test
    fun 页号折到真有的那一页上() {
        assertEquals("0 页时只有一个落点，就是 0（负数与超界都不许漏出去）", 0, desktopReaderStartPageIndex(3, 0))
        assertEquals(0, desktopReaderStartPageIndex(-2, 0))
        assertEquals("负数折到第一页，不是抛", 0, desktopReaderStartPageIndex(-2, 10))
        assertEquals("超界折到最后一页", 9, desktopReaderStartPageIndex(99, 10))
        // 对照组：合法的数**原样**交回（全折成边界时上面四条会一起假绿）
        assertEquals(3, desktopReaderStartPageIndex(3, 10))
        assertEquals(0, desktopReaderStartPageIndex(0, 1))
    }

    /**
     * 「请求滚到那一页」之后**量到的是第几页**这一格（批 82.5 的自证读数）。
     *
     * 为什么要有这一条：`--read-page=3` 起窗后库里那一行念的是 `页=0/89`，而那一格同时是两种故障的形状
     * —— "种子没活到 pane"与"活到了而列表没滚"。前者由 `D_阅读 … 起点=` 那一格分开，
     * 后者只有把 `scrollToItem` 前后的值各念一次才分得开。**量具自己动不动得起来，也得有读数。**
     */
    @Test
    fun 起点落没落成念得出来() {
        assertEquals(
            "D_阅读 起点 请求=3 量到=3 页表=89 试=1",
            desktopReaderStartPageReading(requested = 3, observed = 3, pageCount = 89, tries = 1),
        )
        val missed = desktopReaderStartPageReading(requested = 3, observed = 0, pageCount = 89, tries = 12)
        assertTrue("没落到那一格必须出声（不出声就是把\"列表没滚\"读成\"种子没给\"）：$missed", missed.contains("没落到"))
        assertTrue(
            "落成了的那一句不许带那句丧气话",
            !desktopReaderStartPageReading(requested = 3, observed = 3, pageCount = 89, tries = 1).contains("没落到"),
        )
        // 试了几次也要念：到顶与一轮就中是两件事，前者说明这一屏的落点还受网络摆布
        assertTrue(missed.contains("页表=89"))
        assertTrue("没念试了几次（到顶那一句得答得出等过多久）：$missed", missed.contains("试=12"))
    }

    /**
     * 次序判据：**先把那一屏落到起始那一页，再开始盯**。
     *
     * 反过来的话第一笔历史写的是"还没滚之前的 0"，而真窗口里量到的会是两笔 ——
     * 那一条读数会被读成"页号没生效"，其实是我自己把盯梢早开了一步。
     * ⚠️ 锚点一律取**调用形状**：这些名字在文件里先出现在**函数定义**那一行（定义排在 pane 之前），
     * 拿裸名字当"调用点次序"的证据会把次序读反 —— 第一次跑就是这样红的（"名字在场"那一族）。
     */
    /**
     * 重试那一颗件的形状（每一条都对应一次量出来的病，不是想象中的防护）。
     *
     * 起窗第一趟念 `请求=3 量到=0 没落到`；改成"等列表量出非零尺寸再滚"**照样**没落到；
     * 隔一会儿再滚同一枚才念 `量到=3` 且库里那一行跟着变成 `last_page_index=3`。
     * ⇒ 成因是"滚下去那一刻列表还不认这个请求"，而那一刻没法用一扇闸刻画，只能重试到落定为止。
     *
     * ⚠️ 第八十七批把这一颗搬进了 `DesktopReaderPaging.kt` 并让它**两档共用**（各滚各的那面墙），
     * 所以这里读的源码与钉的表达式跟着搬家 —— 原样留在旧文件里就会恒红（本仓那条"扫源码的尺子
     * 在被扫那句搬家后失去靶子"的坑，㊞ 那一族）。行为一条没放宽，只是"滚"这一步交给了 `scroll(target)` 那一枚形参。
     */
    @Test
    fun 重试有界而等不到不假装落定() {
        val file = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopReaderPaging.kt")
        val helper = DesktopSourceTree.codeText(file)
            .substringAfter("internal suspend fun desktopApplyStartPage")
        assertTrue("没有有界重试的循环（滚一次就交卷就是那第一趟）", helper.contains("while (observedNow != target"))
        assertTrue(
            "循环没被上限管住（无上限就是把这一屏挂死在等上）",
            helper.contains("tries < DESKTOP_READER_START_PAGE_TRIES"),
        )
        assertTrue("每轮没真滚一次", helper.contains("scroll(target)"))
        assertTrue("等页号落定没带超时闸", helper.contains("withTimeoutOrNull(DESKTOP_READER_START_PAGE_WAIT_MS)"))
        // 等不到时保留上一次量到的那个值，而不是写成请求值 —— 后者会把"我没等到"念成"落成了"
        assertTrue("等不到时把请求值当读数交回来（那是替自己作伪证）", helper.contains("?: observedNow"))
        assertEquals(
            "尝试上限得是常量（散在调用点里的数改不动也读不出）",
            1,
            DesktopSourceTree.codeOccurrences(file, "internal const val DESKTOP_READER_START_PAGE_TRIES"),
        )
    }

    /** 真人那条路（没种起点）一次都不许多等：这一格钉的是"起点 0 时循环根本不进"。 */
    @Test
    fun 没种起点时一次都不等() {
        val helper = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopReaderPaging.kt"))
        assertEquals(
            "两枚包装各取自己那面墙的现值（0 起点不空等；递错那面就是恒 0 的假读数）",
            listOf(1, 1),
            listOf(
                DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopReaderPaging.kt"), "observed = { listState.firstVisibleItemIndex }"),
                DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopReaderPaging.kt"), "observed = { pagerState.settledPage }"),
            ),
        )
        assertTrue(helper.contains("internal suspend fun desktopApplyReaderStartPage("))
        assertTrue(helper.contains("internal suspend fun desktopApplyReaderStartPagePager("))
    }

}
