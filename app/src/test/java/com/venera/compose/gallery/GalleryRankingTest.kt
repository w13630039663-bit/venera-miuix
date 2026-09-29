package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GalleryRankings
import com.venera.compose.gallery.domain.GalleryTagFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 画廊搜索的**时间窗口排行**判据（2026-09-29，第二轮真机读数之后收口）。
 *
 * 全部数字来自实测（见 `gallery-ranking-windows-2026-09.md` §〇 与 §十一）：
 * - yande.re **原生认** `date:A..B` + `order:score`（四形式都验过，返回的 created_at 全在窗口内）→ 六档全真；
 * - Gelbooru 只认 `sort:score:desc`：时间窗口那一类它给不了（真机第二轮：天/周能出图，月/年压根定不出
 *   起点 —— 探测次数不够），用户拍板整类撤掉 → **只有默认与全部两档是真的**。
 *
 * 因此这里锁三件事：**每站哪几档能摆**、**每档用哪条串**，以及
 * **"发出去的那一串"与"胶囊摆的那一排"必须不同源**（伪标签绝不能出现在用户可见的胶囊里 ——
 * 那是用户没选过的东西）。
 */
class GalleryRankingTest {

    /** 2026-09-28 是周一 —— 周档的起点就应该是它自己。 */
    private val monday = LocalDate.of(2026, 9, 28)

    /** 2026-09-27 是周日 —— 周档的起点要退到上周一（09-21）。 */
    private val sunday = LocalDate.of(2026, 9, 27)

    private val touhou = listOf(GalleryTagFilter("touhou"))

    @Test
    fun `yande re 六档全真`() {
        GalleryRanking.entries.forEach {
            assertTrue("${it.name} 该能摆", GalleryRankings.supports(GallerySite.YANDERE, it))
        }
    }

    @Test
    fun `Gelbooru 只有默认与全部两档是真的`() {
        // 这四档在 Gelbooru 上要么摆出全站结果冒充本周、要么压根定不出起点 —— 两条都是假读数。
        for (ranking in listOf(
            GalleryRanking.DAY,
            GalleryRanking.WEEK,
            GalleryRanking.MONTH,
            GalleryRanking.YEAR,
        )) {
            assertFalse(
                "Gelbooru 的 ${ranking.name} 不该能摆",
                GalleryRankings.supports(GallerySite.GELBOORU, ranking),
            )
        }
        assertTrue(GalleryRankings.supports(GallerySite.GELBOORU, GalleryRanking.NEWEST))
        assertTrue(GalleryRankings.supports(GallerySite.GELBOORU, GalleryRanking.ALL))
    }

    @Test
    fun `Gelbooru 的排行下拉只列两行`() {
        // 2026-09-29 第五轮：本站没有的档从"置灰 + 写'本站没有'"改成不列出。
        // 这一条锁的是**列出来的那几行**，不只是判据本身 —— 菜单照这个表达式画。
        assertEquals(
            listOf(GalleryRanking.NEWEST, GalleryRanking.ALL),
            GalleryRanking.entries.filter { GalleryRankings.supports(GallerySite.GELBOORU, it) },
        )
        assertEquals(
            GalleryRanking.entries.size,
            GalleryRanking.entries.count { GalleryRankings.supports(GallerySite.YANDERE, it) },
        )
    }

    @Test
    fun `选期入口只出现在给得出时间窗的站`() {
        assertTrue(GalleryRankings.supportsPeriodPicker(GallerySite.YANDERE))
        assertFalse(GalleryRankings.supportsPeriodPicker(GallerySite.GELBOORU))
    }

    @Test
    fun `默认档在两站都不产生任何后缀`() {
        assertNull(GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.NEWEST, null, monday))
        assertNull(GalleryRankings.suffix(GallerySite.GELBOORU, GalleryRanking.NEWEST, null, monday))
    }

    @Test
    fun `全部排行两站各用自己的那串实测生效的写法`() {
        // ⚠️ Gelbooru 那一站**不认** `order:score`（实测 0 结果页），别照抄隔壁站。
        assertEquals("order:score", GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.ALL, null, monday))
        assertEquals(
            "sort:score:desc",
            GalleryRankings.suffix(GallerySite.GELBOORU, GalleryRanking.ALL, null, monday),
        )
    }

    @Test
    fun `Gelbooru 的时间档不产任何串`() {
        // 就算状态层被绕过（未来的快捷入口直接写了 ranking），这里也不会把 yande.re 的 date: 发给它。
        for (ranking in listOf(
            GalleryRanking.DAY,
            GalleryRanking.WEEK,
            GalleryRanking.MONTH,
            GalleryRanking.YEAR,
        )) {
            assertNull(
                "${ranking.name} 在 Gelbooru 不该有后缀",
                GalleryRankings.suffix(GallerySite.GELBOORU, ranking, null, monday),
            )
        }
    }

    @Test
    fun `yande re 周档拼出 date 周一到今天 加 order score`() {
        // 周日 09-27 那天的"本周"= 09-21 ~ 09-27（起点退到上周一，终点是它自己那天）。
        assertEquals(
            "date:2026-09-21..2026-09-27 order:score",
            GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.WEEK, null, sunday),
        )
        // 周一当天就是新一周的开始：起点等于终点那一天。
        assertEquals(
            "date:2026-09-28..2026-09-28 order:score",
            GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.WEEK, null, monday),
        )
    }

    @Test
    fun `日 月 年档的起点各按 UTC 日切`() {
        assertEquals(
            "date:2026-09-28..2026-09-28 order:score",
            GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.DAY, null, monday),
        )
        assertEquals(
            "date:2026-09-01..2026-09-28 order:score",
            GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.MONTH, null, monday),
        )
        assertEquals(
            "date:2026-01-01..2026-09-28 order:score",
            GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.YEAR, null, monday),
        )
    }

    @Test
    fun `周档起点落在周内不同日都退到本周一`() {
        // 周一当天：起点就是今天（off-by-one 的高发处，两条都要钉住）。
        assertEquals(monday, GalleryRankings.windowStart(GalleryRanking.WEEK, monday))
        // 周日：退到上周一 09-21。
        assertEquals(LocalDate.of(2026, 9, 21), GalleryRankings.windowStart(GalleryRanking.WEEK, sunday))
        // 全部/默认没有"窗口"可言。
        assertNull(GalleryRankings.windowStart(GalleryRanking.ALL, monday))
        assertNull(GalleryRankings.windowStart(GalleryRanking.NEWEST, monday))
    }

    @Test
    fun `胶囊串里永远不含排序与窗口伪标签`() {
        // 这两句锁的是"摆出来的"与"发出去的"不同源：用户看到的胶囊只有他自己选的东西。
        val query = GalleryRankings.searchQuery(GallerySite.YANDERE, touhou, GalleryRanking.WEEK, null, monday)
        assertEquals("touhou", GalleryRankings.queryOfVisible(touhou))
        assertTrue(query.contains("order:score"))
        assertFalse(GalleryRankings.queryOfVisible(touhou).contains("order:"))
        assertFalse(GalleryRankings.queryOfVisible(touhou).contains("date:"))
    }

    @Test
    fun `发出去的串是胶囊串加后缀 空条件时不留前导空格`() {
        assertEquals(
            "touhou date:2026-09-28..2026-09-28 order:score",
            GalleryRankings.searchQuery(GallerySite.YANDERE, touhou, GalleryRanking.DAY, null, monday),
        )
        // 排除项照旧在胶囊那半，后缀永远在末尾（站方按空格分段，顺序不影响命中）。
        val withExcluded = listOf(GalleryTagFilter("touhou"), GalleryTagFilter("long_hair", excluded = true))
        assertEquals(
            "touhou -long_hair order:score",
            GalleryRankings.searchQuery(GallerySite.YANDERE, withExcluded, GalleryRanking.ALL, null, monday),
        )
        // 一枚胶囊都没有（不该发生，但判据不能留个前导空格串出去）。
        assertEquals(
            "order:score",
            GalleryRankings.searchQuery(GallerySite.YANDERE, emptyList(), GalleryRanking.ALL, null, monday),
        )
        assertEquals(
            "",
            GalleryRankings.searchQuery(GallerySite.YANDERE, emptyList(), GalleryRanking.NEWEST, null, monday),
        )
    }

    @Test
    fun `窗口副标念得出实际日期区间`() {
        // 下拉里每一项都带真实区间，"本周"就不是一个说不清的词。
        assertEquals("2026-09-21 ~ 2026-09-27", GalleryRankings.windowLabel(GalleryRanking.WEEK, sunday))
        assertEquals("2026-09-28", GalleryRankings.windowLabel(GalleryRanking.DAY, monday))
        assertNull(GalleryRankings.windowLabel(GalleryRanking.ALL, monday))
        assertNull(GalleryRankings.windowLabel(GalleryRanking.NEWEST, monday))
    }

    @Test
    fun `历史月档取整月 不受今天是哪天影响`() {
        // 锚点给月内任意一天（2024-03-15）都取整个 3 月 —— 日历里点的就是"那个月的某一天"。
        assertEquals(
            "date:2024-03-01..2024-03-31 order:score",
            GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.MONTH, LocalDate.of(2024, 3, 15), monday),
        )
        assertEquals(
            "date:2019-01-01..2019-12-31 order:score",
            GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.YEAR, LocalDate.of(2019, 6, 1), monday),
        )
    }

    @Test
    fun `本期月档终点钉在今天 不越过`() {
        // 09-28 那天点"按月排行"：窗口是 09-01..09-28，不能把整个 9 月都算进去（后面那些天还没发生）。
        assertEquals(
            "date:2026-09-01..2026-09-28 order:score",
            GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.MONTH, null, monday),
        )
        // 周档同理：周一起算，终点是今天而不是本周日。
        assertEquals(
            "date:2026-09-28..2026-09-28 order:score",
            GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.WEEK, null, monday),
        )
    }

    @Test
    fun `锚点在未来也夹回今天 不产起在止之后的串`() {
        // 弹层的年份范围已经不给未来的年份，这里再钉一道：判据层自己不能产出 `date:2026-12-01..2026-09-29`。
        assertEquals(
            "date:2026-09-01..2026-09-28 order:score",
            GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.MONTH, LocalDate.of(2026, 12, 5), monday),
        )
    }

    @Test
    fun `只有月档与年档开放历史期`() {
        assertTrue(GalleryRankings.supportsHistory(GalleryRanking.MONTH))
        assertTrue(GalleryRankings.supportsHistory(GalleryRanking.YEAR))
        // 天/周技术上同样能拼（下面那条用例就是证据），是**入口**按用户拍板收着不开。
        assertFalse(GalleryRankings.supportsHistory(GalleryRanking.DAY))
        assertFalse(GalleryRankings.supportsHistory(GalleryRanking.WEEK))
        assertFalse(GalleryRankings.supportsHistory(GalleryRanking.ALL))
        assertFalse(GalleryRankings.supportsHistory(GalleryRanking.NEWEST))
    }

    @Test
    fun `周档给历史锚点也拼得出完整那一周`() {
        // 2026-09-23 是周三：那一周 = 09-21 ~ 09-27，且终点**不该**被今天（09-28）截断。
        assertEquals(
            "date:2026-09-21..2026-09-27 order:score",
            GalleryRankings.suffix(GallerySite.YANDERE, GalleryRanking.WEEK, LocalDate.of(2026, 9, 23), monday),
        )
    }

    @Test
    fun `期次短标只在历史期出现`() {
        assertEquals("2024-03", GalleryRankings.periodShortLabel(GalleryRanking.MONTH, LocalDate.of(2024, 3, 15)))
        assertEquals("2019", GalleryRankings.periodShortLabel(GalleryRanking.YEAR, LocalDate.of(2019, 6, 1)))
        // 本期（锚点为 null）与不开放历史期的档都不标：胶囊上那一截是给"翻到了别处"用的。
        assertNull(GalleryRankings.periodShortLabel(GalleryRanking.MONTH, null))
        assertNull(GalleryRankings.periodShortLabel(GalleryRanking.WEEK, LocalDate.of(2026, 9, 23)))
        assertNull(GalleryRankings.periodShortLabel(GalleryRanking.NEWEST, null))
    }

    @Test
    fun `按年能挑的年份 由近及远且不越过存档起点`() {
        val years = GalleryRankings.yearChoices(LocalDate.of(2026, 9, 29))
        assertEquals(2026, years.first())
        // 2007 是实测地板（`date:..2006-06-01` 已经回 0 条）；给一个翻得出空页的年份就是假入口。
        assertEquals(GalleryRankings.EARLIEST_PERIOD.year, years.last())
        assertEquals(2026 - 2007 + 1, years.size)
        // 今年本身也要能挑（"就看本年"= 回到本期那条路径之外，用户确实会点它）。
        assertTrue(years.contains(2026))
    }
}
