package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2026-10-16「Gelbooru 翻到具体哪一期」这一批的尺子。
 *
 * 用户给的题面是一句能力问句："凭证给你，你自己测下到底能不能获取到具体到 x月 xx日 xx年"。
 * 答案是**能**，但能的方式不是站方给窗口（`date:` 回 0 条、区间语法被静默忽略），
 * 而是两枚各自生效的 id 标签 `id:>=A id:<B`（真站算术严丝合缝：273,840 = 375,233 − 101,393）。
 *
 * 于是这一批的失败模式全在**接缝**上，而接缝编译器管不到：
 * 菜单摆了这一行、状态层却按原生那把判死 ⇒ 点开挑一期什么也不发生；
 * 第二枚边界没探 ⇒ 发出去的是"这一期起到今天"，而胶囊写着这一期；
 * 上界收口收错一侧 ⇒ 下一期的头几小时混进这一期的榜，屏上读不出任何异常。
 * 所以下面每一把都钉"必须存在的那一行/那一步"，并且自带对照。
 */
class GalleryGelbooruClosedWindowTest {

    private val domain = "com/venera/compose/gallery/domain"
    private val galleryUi = "com/venera/compose/gallery/ui"

    private fun codeLines(dir: String, name: String): List<String> = RepoSources.kotlinFiles(dir)
        .first { it.name == name }
        .readLines()
        .map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private fun rankingCode() = codeLines(domain, "GalleryRanking.kt")
    private fun boundaryCode() = codeLines(domain, "GelbooruWindowBoundary.kt")
    private fun vmCode() = codeLines(galleryUi, "GallerySearchViewModel.kt")
    private fun areaCode() = codeLines(galleryUi, "GallerySearchArea.kt")

    /**
     * ① 「选具体哪一期…」这一行在移动端必须认**合成档也算**那一把。
     *
     * 桌面那把 `supportsPeriodPickerAll` 只看站方原生给不给，Gelbooru 恒判死 —— 菜单那一行就永远不出现，
     * 而同一屏的天/周/月/年四档已经由 id 边界合成了（2026-10-13 接的）。两把合流过一次就再分不开。
     */
    @Test fun `期次入口用移动端那把判据`() {
        assertEquals(
            "RankingChip 里那一行的守卫变了形（应当恰好一处调用 supportsPeriodPickerForApp）",
            1,
            areaCode().count { it == "if (GalleryRankings.supportsPeriodPickerForApp(svm.source.sites)) {" },
        )
        // gallery/ui 整棵树不许再读桌面那把 —— 读了就是"菜单按原生判、菜单项按合成判"的分叉。
        val hits = areaCode().filter { it.contains("supportsPeriodPickerAll") } +
            codeLines(galleryUi, "GalleryScreen.kt").filter { it.contains("supportsPeriodPickerAll") } +
            vmCode().filter { it.contains("supportsPeriodPickerAll") }
        assertEquals("gallery/ui 又去读桌面那把判据了：$hits", emptyList<String>(), hits)
        // 对照：尺子咬得动 —— 自己喂一遍被禁的那一行必须抓得住
        assertTrue((areaCode() + "if (GalleryRankings.supportsPeriodPickerAll(svm.source.sites)) {").any {
            it.contains("supportsPeriodPickerAll")
        })
    }

    /**
     * ② 状态层 `pickPeriod` 那道闸也得是同一把。
     *
     * 这一处是真缺陷的形状：菜单那一行摆出来了、点下去 `pickPeriod` 用原生那把判死、**一言不发**。
     * 入口不止菜单一个（弹栈快照、以后的快捷入口），所以判据必须落在状态层，而不只是不摆那一行。
     */
    @Test fun `选期次那道闸与菜单同一把尺`() {
        assertEquals(
            1,
            vmCode().count { it == "if (!GalleryRankings.supportsForApp(source.sites, target)) return" },
        )
        assertTrue(
            "pickPeriod 又退回原生那把（supportsAll）了 —— 菜单点下去会没有反应",
            vmCode().none { it.contains("supportsAll(source.sites, target)") },
        )
    }

    /** ③ 这一腿真的去探**两枚**边界，而不是只探起点那一枚。 */
    @Test fun `历史期那一腿探两枚边界`() {
        assertEquals(
            1,
            vmCode().count {
                it == "val bounds = gelbooruWindowFinder.huntWindowBounds(firstDay, dayAfter, todayUtc)"
            },
        )
        assertEquals(
            1,
            vmCode().count {
                it == "val dayAfter = GalleryRankings.boundaryEndTargetDay(site, rankingAtRequest, anchorAtRequest, todayUtc)"
            },
        )
        // 第二枚断在原点那一枚的结论里要留着（屏上那句"没探成"得指得出是起是止）
        assertEquals(
            1,
            vmCode().count { it == "return LegQuery.Skip(bounds.at, bounds.detail, atEnd = dayAfter != null)" },
        )
    }

    /** ④ 多探一枚边界的那一腿必须有**另一档等待预算**，而且数值只许写在一处。 */
    @Test fun `闭环窗口那一腿的预算有出处`() {
        assertEquals(
            1,
            vmCode().count {
                it == "timeoutMs = legBudgetMs(legSite, rankingAtRequest, anchorAtRequest, todayUtc),"
            },
        )
        assertEquals(
            "放宽的那一档必须来自量测出来的常量，不是这一行现写的数",
            1,
            vmCode().count { it == "GelbooruWindowBoundary.CLOSED_WINDOW_LEG_TIMEOUT_MS" },
        )
        // 本期那一档不许跟着放宽（三处共用一份预算那条纪律，只在"多探一枚"这一格上开口）
        assertEquals(1, vmCode().count { it == "GalleryDailyFeed.PER_SITE_TIMEOUT_MS" })
        assertTrue(boundaryCode().any { it.contains("const val CLOSED_WINDOW_LEG_TIMEOUT_MS = 30_000L") })
    }

    /** ⑤ 上界那枚拼成 `id:<B`，而且整颗文件永远不许出现**区间语法**那一枚编号标签。 */
    @Test fun `合成窗口只用实测生效的两枚标签`() {
        listOf(
            "append(\"id:>=\").append(start)",
            "append(\" id:<\").append(bounded.exclusiveId)",
            "append(' ').append(GELBOORU_SCORE)",
        ).forEach { shape ->
            assertEquals("闭环窗口那一路的形状变了：$shape", 1, rankingCode().count { it == shape })
        }
        // 站方把 `id:A..B` **静默忽略**（count 与全站逐字相同），所以 `id:` 后面出现 `..` 就是假开关。
        // 对照：这一把不会误伤 yande.re 那两行 —— 它们合法地写 `date:A..B`（那一站真认）。
        val rangeForm = Regex("""id:[^"\n]*\.\.""")
        val hits = rankingCode().filter { rangeForm.containsMatchIn(it) }
        assertEquals("又有人拿区间语法当上界了：$hits", emptyList<String>(), hits)
        assertTrue(
            "尺子咬得动：喂它一行区间语法必须抓住",
            rangeForm.containsMatchIn("val suffix = \"id:>=14718000..14994000 sort:score:desc\""),
        )
    }

    /** ⑥ 两头的收口方向不对称 —— 上界必须交 `low + 1`（安全那一侧）。 */
    @Test fun `上界收口只许往安全那一侧`() {
        assertEquals(
            1,
            boundaryCode().count {
                it == "if (side == BoundarySide.WINDOW_END) low.atLeastId + 1L else high.atLeastId"
            },
        )
    }

    /**
     * ⑦ 标定表自己得是**单调**的（烘错一行，估点就把某一页永远定不出来）。
     *
     * 这张表是 2026-10-16 拿凭据在真站上逐年量出来的 19 行（`_qa/gb_calibration_ledger.txt`）。
     * 表里任何一行抄反（日期与编号串了、少了一位），屏上不报错 —— 只是某一档的历史期永远在转圈然后说
     * "没探成"。所以这里把形状逐行读回来验：日期严格增、编号严格增、行数对得上。
     */
    @Test fun `标定表逐行读回来验单调`() {
        val row = Regex("""LocalDate\.of\((\d+), (\d+), (\d+)\) to ([\d_]+)L,""")
        val rows = boundaryCode().mapNotNull { m ->
            row.find(m)?.groupValues?.let {
                LocalDate.of(it[1].toInt(), it[2].toInt(), it[3].toInt()) to it[4].replace("_", "").toLong()
            }
        }
        assertEquals(
            "标定表行数变了（改表要连带重跑 _qa/gb_calibration_rig.js）",
            19,
            rows.size,
        )
        rows.zipWithNext().forEach { (earlier, later) ->
            assertTrue("表不单调：$earlier 之后是 $later", earlier.first.isBefore(later.first))
            assertTrue("编号不单调：$earlier 之后是 $later", earlier.second < later.second)
        }
        // 第一行是 2008：站方最老的一条是 2007-07-16 的 id=1（2026-10-16 现量），比 2008-01-01 更早的
        // 目标日一律落到 1 那一枚 —— 于是"表最早一行"与"站方最早一条"之间只可能差半年，不会差一个数量级。
        assertEquals(LocalDate.of(2008, 1, 1), rows.first().first)
        assertEquals(132_780L, rows.first().second)
        assertEquals(LocalDate.of(2026, 1, 1), rows.last().first)
        assertEquals(13_223_080L, rows.last().second)
    }

    /** ⑧ 桌面没被顺手动过：原生那把判据与它的两行仍然在位。 */
    @Test fun `桌面那把判据一个字没动`() {
        assertEquals(
            1,
            rankingCode().count {
                it == "site == GallerySite.YANDERE || site == GallerySite.SAFEBOORU || ranking == GalleryRanking.NEWEST || ranking == GalleryRanking.ALL"
            },
        )
        assertEquals(
            1,
            rankingCode().count { it == "sites.all { supportsPeriodPicker(it) }" },
        )
    }
}
