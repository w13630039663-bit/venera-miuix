package com.venera.desktop.gallery.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.venera.desktop.gallery.data.DesktopReaderMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第八十七批：阅读那一屏的**翻页判据**（纯函数面，全部真跑）。
 *
 * 这一族的档位表与算式是桌面自持的一份同轨件（B 路：对岸那两颗纯件进不了桌面编译面，
 * 成因见 `gallery/data/DesktopReaderMode.kt`），所以这一颗管的是**行为**：三档、哪一档整页翻、
 * 步长跟着档位、翻不动交回空、页号钳位、落起点那颗件的有界重试。
 * 三份表与算式**逐字对岸**那一面由 `DesktopReaderPagedSeedTest` 的回核尺子管（从对岸源码文本现取再比），
 * 两边各钉各的，别在这里重复一遍那把尺子。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopReaderPagingTest {

    /** 那颗共用件是 suspend 的：测试这一侧用 runBlocking 递进去，断言的仍是同一份算式。 */
    private fun applyStartPageProbe(
        startPage: Int,
        pageCount: Int,
        observed: () -> Int,
        scroll: suspend (Int) -> Unit,
        settled: (Int) -> Flow<Int>,
    ): String = runBlocking { desktopApplyStartPage(startPage, pageCount, observed, scroll, settled) }

    // ── 模式那一排：三档、名字、哪一档是整页翻 ──────────────────────────────

    @Test
    fun 模式那一排只摆今天真能翻的三档() {
        assertEquals(
            "横向连续与对开双页今天没做，摆上去就是按不动的那一枚",
            listOf(
                DesktopReaderMode.VERTICAL_CONTINUOUS,
                DesktopReaderMode.HORIZONTAL_RTL,
                DesktopReaderMode.HORIZONTAL_LTR,
            ),
            DESKTOP_READER_MODE_CHOICES,
        )
    }

    @Test
    fun 种子顶的是没做的那档时折回默认档而那句实话跟着说() {
        // 顶不动 ≠ 静默：折回之后必须念清"照哪一档摆"，否则用户以为参数生效了。
        val actual = desktopReaderModeOf(DesktopReaderMode.DOUBLE_PAGE, DesktopReaderMode.HORIZONTAL_LTR)
        assertEquals(DesktopReaderMode.HORIZONTAL_LTR, actual)
        val note = desktopReaderModeSeedNote(DesktopReaderMode.DOUBLE_PAGE, DesktopReaderMode.HORIZONTAL_LTR)
        checkNotNull(note) { "种子没生效却不吭声，就是本仓最忌的假开关" }
        assertTrue("那句没点名被顶的那档：$note", note.contains("对开·双页"))
        assertTrue("那句没交代实际摆的是哪档：$note", note.contains("美漫·左至右"))
        assertTrue("那句没说明盘上没改动：$note", note.contains("盘上没改动"))
    }

    @Test
    fun 种子顶得动时念的是顶上而盘上那档原样留着() {
        assertEquals(
            DesktopReaderMode.HORIZONTAL_RTL,
            desktopReaderModeOf(DesktopReaderMode.HORIZONTAL_RTL, DesktopReaderMode.VERTICAL_CONTINUOUS),
        )
        val note = desktopReaderModeSeedNote(DesktopReaderMode.HORIZONTAL_RTL, DesktopReaderMode.VERTICAL_CONTINUOUS)
        checkNotNull(note)
        assertTrue("顶过那一句要说盘上还是哪档：$note", note.contains("条漫·连续"))
        assertTrue("要说这一档是参数顶的：$note", note.contains("取证参数"))
    }

    @Test
    fun 没种子而盘上那档本批没做时也要吭声() {
        assertEquals(
            DesktopReaderMode.VERTICAL_CONTINUOUS,
            desktopReaderModeOf(null, DesktopReaderMode.HORIZONTAL_CONTINUOUS),
        )
        val note = desktopReaderModeSeedNote(null, DesktopReaderMode.HORIZONTAL_CONTINUOUS)
        checkNotNull(note) { "屏上摆的与盘上存的不是同一档，不说就是静默换档" }
        assertTrue("那句没点名盘上那一档：$note", note.contains("横向·连续"))
        // 对照组：盘上那档做得了时这一格不该出现（否则三档正常路径也被念成异常）
        assertNull(desktopReaderModeSeedNote(null, DesktopReaderMode.HORIZONTAL_LTR))
    }

    @Test
    fun 那一排摆的档位都能被种子上屏() {
        // choices 与"种子能不能生效"必须同源：两处各列一份就会漂出"能顶而摆不出"或"能摆而顶不动"的档
        DESKTOP_READER_MODE_CHOICES.forEach { choice ->
            assertEquals(choice, desktopReaderModeOf(choice, DesktopReaderMode.VERTICAL_CONTINUOUS))
        }
    }

    @Test
    fun 哪几档是整页翻由一颗件说而不散在屏上() {
        assertEquals(listOf(false, true, true), DESKTOP_READER_MODE_CHOICES.map { desktopReaderIsPaged(it) })
    }

    @Test
    fun 只有日漫那一档反过来排() {
        assertTrue(desktopReaderReverses(DesktopReaderMode.HORIZONTAL_RTL))
        assertEquals(
            "左至右与纵向都不反",
            listOf(false, false),
            listOf(DesktopReaderMode.HORIZONTAL_LTR, DesktopReaderMode.VERTICAL_CONTINUOUS).map { desktopReaderReverses(it) },
        )
    }

    // ── 步长与边界：那几枚数跟着档位，不在调用点写死 ────────────────────────

    @Test
    fun 双页那一档一步跨两页而单页档一步一页() {
        assertEquals(
            5,
            desktopReaderStepTarget(current = 4, delta = 1, mode = DesktopReaderMode.HORIZONTAL_LTR, lastIndex = 9),
        )
        // 对开双页今天不在那一排里，但算式整颗留着（那两枚数与对岸同一份，由回核尺子钉）：
        // 写死 +1 会被 `scrollToPage` 的 /2 收回同一组（对岸注释里那条真实缺陷：点右下没反应，
        // 页码气泡与进度条一起冻在偶数页）。
        assertEquals(
            4,
            desktopReaderStepTarget(current = 3, delta = 1, mode = DesktopReaderMode.DOUBLE_PAGE, lastIndex = 9),
        )
    }

    @Test
    fun 翻不动时交回空而不是原地那一页() {
        // 交回原页 = 调用方以为翻成了；对岸那两颗件交回 null 正是为了这个。
        assertNull(desktopReaderStepTarget(current = 9, delta = 1, mode = DesktopReaderMode.HORIZONTAL_LTR, lastIndex = 9))
        assertNull(desktopReaderStepTarget(current = 0, delta = -1, mode = DesktopReaderMode.HORIZONTAL_LTR, lastIndex = 9))
    }

    @Test
    fun 空页表那一档也交回空而不是零() {
        assertNull(desktopReaderStepTarget(current = 0, delta = 1, mode = DesktopReaderMode.HORIZONTAL_LTR, lastIndex = -1))
        assertNull(desktopReaderStepTarget(current = 0, delta = -1, mode = DesktopReaderMode.HORIZONTAL_LTR, lastIndex = -1))
    }

    @Test
    fun 落在组内第二页时往回退的是整组() {
        // 拖进度条可以落在组内那一页（奇数）。已经在第一组里就该说"退无可退"，而不是交出一个不动的 0。
        assertNull(desktopReaderStepTarget(current = 1, delta = -1, mode = DesktopReaderMode.DOUBLE_PAGE, lastIndex = 9))
        assertEquals(2, desktopReaderStepTarget(current = 5, delta = -1, mode = DesktopReaderMode.DOUBLE_PAGE, lastIndex = 9))
    }

    @Test
    fun 方向那两枚符号各自指向前后而步数不放大() {
        // 调用点递的是 -1 / +1（与浮层顶上那两枚按钮同向：‹ 在左、› 在右）。
        // 递 3 也只跨一步：对岸一次就跨一组，"步数"不是这一颗的参数（否则调用点能悄悄多翻）。
        assertEquals(
            listOf(7, 5),
            listOf(
                desktopReaderStepTarget(current = 8, delta = -1, mode = DesktopReaderMode.VERTICAL_CONTINUOUS, lastIndex = 9),
                desktopReaderStepTarget(current = 4, delta = 3, mode = DesktopReaderMode.VERTICAL_CONTINUOUS, lastIndex = 9),
            ),
        )
    }

    // ── 换档那一格：落点接手，不重落起点（rounds §2.4 那条自造缺陷）─────────

    @Test
    fun 没摆过任何一页时落点就是种子折过的那一页() {
        assertEquals(3, desktopReaderCarriedPage(seedPage = 3, shown = null))
        assertEquals(0, desktopReaderCarriedPage(seedPage = 0, shown = null))
    }

    @Test
    fun 摆过之后换档落在摆过那一页而不是甩回第一页() {
        assertEquals(40, desktopReaderCarriedPage(seedPage = 0, shown = 40))
        // 往回翻过也跟着人走：种子 3 而人已翻到第 1 页 ⇒ 换档落 1，不许往前甩
        assertEquals(1, desktopReaderCarriedPage(seedPage = 3, shown = 1))
        // 对照组：`null` 那一档才用种子（否则上面两条是恒真的空判式）
        assertEquals(3, desktopReaderCarriedPage(seedPage = 3, shown = null))
    }

    // ── 页号那一句 ──────────────────────────────────────────────────────────

    @Test
    fun 页号那句从一起算而空表不念() {
        assertEquals("第 3 / 89 页", desktopReaderPageLabel(page = 2, total = 89))
        assertEquals("", desktopReaderPageLabel(page = 0, total = 0))
        assertEquals("", desktopReaderPageLabel(page = 0, total = -1))
    }

    @Test
    fun 页号越界时钳到真实那一头而不是念第零页() {
        assertEquals("第 89 / 89 页", desktopReaderPageLabel(page = 400, total = 89))
        assertEquals("第 1 / 89 页", desktopReaderPageLabel(page = -7, total = 89))
    }

    // ── 落起点那一颗：两档共用同一个循环，而各滚各的那面墙 ───────────────────

    @Test
    fun 落起点那颗件落成立即停而到顶照实念() {
        var observed = 0
        var scrolls = 0
        val reading = applyStartPageProbe(
            startPage = 3,
            pageCount = 10,
            observed = { observed },
            scroll = { target -> scrolls++; observed = target },
            settled = { target -> flowOf(target) },
        )
        assertTrue("落定了还念没落到：$reading", reading.contains("请求=3 量到=3 页表=10 试=1"))
        assertEquals("落成了就不该再滚第二遍", 1, scrolls)
    }

    @Test
    fun 落起点那颗件到顶也不把请求值当读数() {
        val reading = applyStartPageProbe(
            startPage = 3,
            pageCount = 10,
            observed = { 0 },
            scroll = { },
            // 那一面墙永远只念 0：等不到落定
            settled = { target -> flowOf(target - 3) },
        )
        assertTrue("到顶必须照实念没落到：$reading", reading.contains("没落到"))
        assertTrue("量到的那一格还是现值 0：$reading", reading.contains("量到=0"))
        assertEquals(
            "试数念到的是那枚上限（无上限就是把这一屏挂死在等上）",
            "试=$DESKTOP_READER_START_PAGE_TRIES",
            Regex("试=\\d+").find(reading)?.value,
        )
    }

    @Test
    fun 没种起点时那一颗一次都不等() {
        var scrolls = 0
        val reading = applyStartPageProbe(
            startPage = 0,
            pageCount = 10,
            observed = { 0 },
            scroll = { scrolls++ },
            settled = { target -> flowOf(target) },
        )
        assertEquals("起手先量现值，0 就不用滚", 0, scrolls)
        assertTrue(reading.contains("试=0"))
    }

    @Test
    fun 空页表时那一颗一次都不滚() {
        var scrolls = 0
        val reading = applyStartPageProbe(
            startPage = 2,
            pageCount = 0,
            observed = { 0 },
            scroll = { scrolls++ },
            settled = { target -> flowOf(target) },
        )
        // 起点先折（0 页没有第 2 页可落），折完就是 0，而现值本来就是 0 ⇒ 一次都不滚
        assertEquals("0 页不滚（落在一面还没造的墙上就是那第一趟的错）", 0, scrolls)
        assertTrue(reading.contains("页表=0"))
    }

    @Test
    fun 那一颗的两枚分档包装各滚自己那面墙() {
        // 拿 listState 去滚横向档 = 空操作（本仓记过同族：撤了 pager 之后 scrollToItem 打在另一面墙上）。
        val text = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopReaderPaging.kt"))
        assertEquals("纵向那一档只许滚列表", 1, text.split("listState.scrollToItem(").size - 1)
        assertEquals("横向那一档只许滚 pager", 1, text.split("pagerState.scrollToPage(").size - 1)
        assertTrue(
            "两枚包装都得先取一次现值（0 起点不空等）",
            text.contains("listState.firstVisibleItemIndex") && text.contains("pagerState.settledPage"),
        )
    }

    @Test
    fun 对照流那一颗自证喂进去就取得到() {
        val seen = runBlocking { flowOf(5).map { it + 1 }.first() }
        assertEquals(6, seen)
    }

    /** 真窗口之外也能造出那一枚页翻状态：这一条不为功能，只为"上面那些不是纸面判据"。 */
    @Test
    fun 离屏能真造出一页翻的那枚状态并读到落定的那一页() {
        var seen = -1
        runComposeUiTest {
            lateinit var state: PagerState
            setContent {
                state = rememberPagerState(initialPage = 3) { 5 }
            }
            waitForIdle()
            seen = state.settledPage
        }
        // 这一条同时也是 `settledPage` 那枚名字的在场证明：以后它改名或语义变了，红的是这里
        assertEquals(3, seen)
    }
}
