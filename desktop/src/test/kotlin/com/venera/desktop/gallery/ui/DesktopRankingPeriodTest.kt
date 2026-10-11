package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GalleryRankings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第二十九批 批 29 · 排行榜的**历史期回看**（批 28 那一屏欠的那一层）。
 *
 * ## 补的是哪一格
 *
 * 批 28 落地时屏上明写着"这一屏只摆本期"，因为桌面没有日期弹层 —— 那是实话，但那一层确实缺。
 * 共享面早就开好了三条判据：`supportsHistory`（只有月/年两档允许回看）、
 * `supportsPeriodPickerAll`（这一组腿**每一条**都给得出时间窗才给入口）、`windowBounds`（锚点夹回今天）。
 * 本批不画日历，只摆「上一期 / 下一期 / 回到本期」三枚 —— 画日历是"新造一层控件"，
 * 而这三枚把回看这件事接上而**不新造任何口径**。
 *
 * ## 一条都不许在桌面重定
 *
 * 步长（月档落到月初、年档落到年初）、地板（`EARLIEST_PERIOD`，yande.re 存档实测再早就回空）、
 * 区间读数（终点恒被今天钉住）三处全部转交；桌面这一侧只写"往哪一步走"和"那一句怎么念"。
 */
class DesktopRankingPeriodTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 7)
    private val twoLegs = listOf(GallerySite.YANDERE, GallerySite.SAFEBOORU)
    private val threeLegs = twoLegs + GallerySite.GELBOORU

    private fun code(name: String) = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))

    /** ① 开不开这一层由共享那两条判 —— 而不是"这一档看起来像有历史"。 */
    @Test
    fun `① 开不开历史期由共享那两条判`() {
        assertTrue("月档在两腿集里该开", desktopRankingHistoryAllowed(twoLegs, GalleryRanking.MONTH))
        assertTrue("年档在两腿集里该开", desktopRankingHistoryAllowed(twoLegs, GalleryRanking.YEAR))
        assertFalse("天档不开（共享面只对月/年开放回看）", desktopRankingHistoryAllowed(twoLegs, GalleryRanking.DAY))
        assertFalse("周档不开", desktopRankingHistoryAllowed(twoLegs, GalleryRanking.WEEK))
        assertFalse("「全部」不设窗口，没有期可回", desktopRankingHistoryAllowed(twoLegs, GalleryRanking.ALL))
        // 带 Gelbooru 的那一组腿：它给不出时间窗 ⇒ 整层不给（半面墙能回看、半面墙不能，读不出对错）。
        assertFalse("三腿集里月档不该开", desktopRankingHistoryAllowed(threeLegs, GalleryRanking.MONTH))
        // 对照：这一条不是恒假 —— 同一枚判据在两腿与三腿上是两个答案。
        assertTrue(
            "对照失效：两腿集里 `supportsPeriodPickerAll` 本身该为真",
            GalleryRankings.supportsPeriodPickerAll(twoLegs),
        )
    }

    /**
     * ② 步长落到"那一期"的起点，地板取共享面那个数，走到头交回 null（= 本期或到不了）。
     *
     * 锚点为什么归一到月初 / 年初：`windowBounds` 只看锚点落在哪一期，但屏上那一句期次标记
     * （`periodShortLabel`）跟着锚点的年月走 —— 不归一就会出现"2024-03 那一期的锚点是 03-12"这种读法。
     */
    @Test
    fun `② 步长归一到那一期而地板取共享那个数`() {
        assertEquals(
            "本期往回一步 = 上个月一号",
            LocalDate.of(2026, 9, 1),
            desktopRankingStep(GalleryRanking.MONTH, null, backwards = true, todayUtc = today),
        )
        assertEquals(
            "月档锚点先归一到月初再往前一期（08-19 落在八月，往回一步是七月初）",
            LocalDate.of(2026, 7, 1),
            desktopRankingStep(GalleryRanking.MONTH, LocalDate.of(2026, 8, 19), backwards = true, todayUtc = today),
        )
        assertEquals(
            "归一：同一期内的两个锚点往回一步该落到同一期",
            desktopRankingStep(GalleryRanking.MONTH, LocalDate.of(2026, 8, 1), backwards = true, todayUtc = today),
            desktopRankingStep(GalleryRanking.MONTH, LocalDate.of(2026, 8, 19), backwards = true, todayUtc = today),
        )
        assertEquals(
            "年档往回一步 = 去年年初",
            LocalDate.of(2025, 1, 1),
            desktopRankingStep(GalleryRanking.YEAR, null, backwards = true, todayUtc = today),
        )
        assertNull("本期再往前一步 = 没有更前的了（null = 已经在本期）", desktopRankingStep(GalleryRanking.MONTH, null, backwards = false, todayUtc = today))
        assertNull(
            "回到本期之后不许越界到未来期",
            desktopRankingStep(GalleryRanking.MONTH, LocalDate.of(2026, 11, 3), backwards = false, todayUtc = today),
        )
        assertEquals(
            "地板：最早那一期的再往前一步是 null（yande.re 存档实测再早回空）",
            null,
            desktopRankingStep(GalleryRanking.MONTH, GalleryRankings.EARLIEST_PERIOD, backwards = true, todayUtc = today),
        )
        assertEquals(
            "地板前一期还能走一步",
            GalleryRankings.EARLIEST_PERIOD,
            desktopRankingStep(GalleryRanking.MONTH, LocalDate.of(2007, 2, 14), backwards = true, todayUtc = today),
        )
        assertNull("天档没有步长", desktopRankingStep(GalleryRanking.DAY, null, backwards = true, todayUtc = today))
    }

    /** ③ 那一句念的是**区间**，而区间逐字转交共享那颗（桌面不许自己算日期）。 */
    @Test
    fun `③ 区间那句逐字转交而桌面不算日期`() {
        assertEquals(
            "本期那句念法",
            "本期 " + requireNotNull(GalleryRankings.windowLabel(GalleryRanking.MONTH, null, today)),
            desktopRankingAnchorReading(GalleryRanking.MONTH, null, today),
        )
        val anchor = LocalDate.of(2024, 3, 1)
        assertEquals(
            "历史期那句要带期次标记与区间",
            "2024-03 " + requireNotNull(GalleryRankings.windowLabel(GalleryRanking.MONTH, anchor, today)),
            desktopRankingAnchorReading(GalleryRanking.MONTH, anchor, today),
        )
        assertEquals(
            "不设窗口的档没有区间可念",
            "",
            desktopRankingAnchorReading(GalleryRanking.ALL, null, today),
        )
        // 桌面这一侧不许出现日期算术（那一族只许住在转交那颗里）。
        listOf("minusMonths", "minusYears", "withDayOfMonth", "withDayOfYear", "lengthOfMonth").forEach { banned ->
            val where = code("DesktopRankingPane.kt")
            assertTrue("排行那一屏自己算了日期（出现「$banned」）—— 步长与窗口都该转交共享那颗", where.contains(banned).not())
        }
    }

    /** ④ 换期 = 换一轮：锚点必须进那两枚 key，否则上一期的账会混进这一期。 */
    @Test
    fun `④ 换期要重开一轮`() {
        val pane = code("DesktopRankingPane.kt")
        assertTrue(
            "取数 effect 的 key 没带锚点（换期不会重取）",
            pane.contains("LaunchedEffect(ports, hasGelbooru, landing, anchor, round)"),
        )
        assertTrue(
            "状态那格的 key 没带锚点（换期还留着上一期的墙）",
            pane.contains("rememberSaveable(ports, hasGelbooru, landing, anchor, round)"),
        )
        // 对照：锚点真的进了查询串（不是一枚只挂在按钮上的装饰）。
        assertTrue("发出去的串没吃锚点", pane.contains("desktopRankingQuery(it, landing, anchor, todayUtc)"))
    }

    /**
     * ⑤ 三枚按钮只在开那一层时摆；不开时要念出为什么少这一层（不许静默少一层）。
     *
     * "没摆"与"摆不了而说清为什么"是两句话 —— 前者用户会以为坏了。
     */
    @Test
    fun `⑤ 不开那一层也要说清为什么少`() {
        val pane = code("DesktopRankingPane.kt")
        listOf(
            "desktopRankingHistoryAllowed(" to "这一屏没问过共享那两条判",
            "上一期" to "少了那一枚按钮",
            "下一期" to "少了那一枚按钮",
            "回到本期" to "少了那一枚按钮",
            "回看历史期" to "不开这一层时那句为什么没说（屏上得念出这是判过的，不是漏了）",
        ).forEach { (token, why) -> assertTrue("$why：屏上读不到 $token", pane.contains(token)) }
        // 对照：三枚按钮都在那一层为真时才摆（负向：不许无条件摆）。
        val at = pane.indexOf("if (historyOpen) {")
        assertTrue("摆那三枚之前没判 `historyOpen`", at >= 0)
        assertTrue("那三枚不在 `historyOpen` 那一支里", pane.substring(at).take(400).contains("上一期"))
    }

    /**
     * ⑥ 换档时锚点要落回本期（月档挑了 2024-03 再切到年档，那一串年月对年档没有意义）。
     *
     * ⚠️ 批 30 起这一条**不再钉"初值写死 null"那个形状** —— 种子那条路进来之后初值是算出来的
     * （`--ranking=MONTH/2024-03` 要落在 2024-03），而"换档即落回本期"这件事的真跑判据是 ⑦：
     * 锚点只在种子那一档与真发出去的那一档**对上**时才带过来。钉字面形状只会把某一版的写法当规格（§0 坑 ㉖）。
     */
    @Test
    fun `⑥ 换档时锚点落回本期`() {
        val pane = code("DesktopRankingPane.kt")
        assertTrue(
            "选档那一下没把锚清回本期（换档后带着上一档的锚点发车）",
            pane.contains("selected = choice.ranking") && pane.contains("anchor = null"),
        )
        // 对照：锚点状态跟着档走（key 里带 landing ⇒ 换档就是新造一格）。
        assertTrue("锚点状态没跟着档走", pane.contains("rememberSaveable(landing)"))
        assertTrue(
            "锚点的初值没走那颗纯函数（种子那条路就只是装饰）",
            pane.contains("desktopRankingSeedAnchor("),
        )
    }

    /**
     * ⑦ 种子怎么落成档与锚点 —— 两颗真跑的纯函数（第三十批）。
     *
     * `desktopRankingSeedAnchor` 那一条判的是"档对不上就不许带锚点"：三腿集里种 `MONTH/2024-03`
     * 会落回默认档，此时锚点若跟过来就成了"屏上是默认档、发出去带着一串 2024-03"那种查不动的读数。
     */
    @Test
    fun `⑦ 种子的档与锚点各按对没对上落地`() {
        assertEquals(
            "没给种子就是默认档（那一站的站方序）",
            GalleryRanking.NEWEST,
            desktopRankingSeedRanking(null),
        )
        val month = DesktopRankingSeed(GalleryRanking.MONTH, LocalDate.of(2024, 3, 1))
        assertEquals(
            "给了种子就停在那一档",
            GalleryRanking.MONTH,
            desktopRankingSeedRanking(month),
        )
        assertEquals(
            "种子档与真发出去的那一档对上 ⇒ 锚点带过来",
            LocalDate.of(2024, 3, 1),
            desktopRankingSeedAnchor(month, GalleryRanking.MONTH),
        )
        assertNull(
            "档没对上（兑现不了已落回默认档）⇒ 锚点绝不跟过来",
            desktopRankingSeedAnchor(month, GalleryRanking.NEWEST),
        )
        assertNull(
            "没有种子就没有锚点",
            desktopRankingSeedAnchor(null, GalleryRanking.MONTH),
        )
        // 对照：种子本身指本期时两档都交 null —— 上面那三条不是靠"种子恒非空"蒙过去的。
        assertNull(
            "对照失效：本期种子（锚点为空）在该档上也该交 null",
            desktopRankingSeedAnchor(DesktopRankingSeed(GalleryRanking.MONTH, null), GalleryRanking.MONTH),
        )
    }

    /**
     * ⑧ 那一屏真吃了这颗种子，而且是从应用根一路递下来的（不是自己 new 一个）。
     *
     * 钉的是三个调用点：形参、档的初值、锚点的初值。少任一处都不会红在编译上 ——
     * 那正是"参数打进去了而屏上没反应"最省事的一种做法。
     */
    @Test
    fun `⑧ 那一屏收种子并拿它作两枚初值`() {
        val pane = code("DesktopRankingPane.kt")
        assertTrue("那一屏没收这颗种子（形参里没有 DesktopRankingSeed）", pane.contains("seed: DesktopRankingSeed?"))
        assertTrue(
            "档的初值没吃种子（屏上永远是默认档）",
            pane.contains("mutableStateOf(desktopRankingSeedRanking(seed))"),
        )
        assertTrue(
            "锚点的初值没吃种子与档的配对结果",
            pane.contains("desktopRankingSeedAnchor(seed, landing)"),
        )
        // 对照组：这份文本真读到了内容（读空会让上面三条 contains 恒真）。
        assertTrue("对照组失效：这一屏连档行那颗件都没读到", pane.contains("desktopRankingChoices("))
    }
}
