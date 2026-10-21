package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GalleryRankings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第二十八批 批 28 · 排行榜那一屏（rail 那六行里第三枚未实现的行点亮）。
 *
 * ## 为什么这一批能点亮它
 *
 * 批 25 量准过这一行的缺席理由：那两句一处说取榜那条口不存在、一处拿另一条端点的读数描述排行榜走的
 * 那一路，都不是事实 —— 口与六档判据都在共享面（`GalleryRankings`，桌面单人页从批 19 就在吃它）。
 * 当时改成实话「取榜的口与六档判据都在，缺的是选档那一屏」，本批就是那一屏。
 *
 * ## 桌面这边一行判据都不重定
 *
 * 六档、每站的窗口写法、哪一站兑现到哪一档、历史期开不开放 —— 全转交 [GalleryRankings]，
 * 桌面只负责"把这一组腿真兑现的那几档摆出来 + 摆不出的点名是哪一站拦的"。
 * ⚠️ 这一族最忌的是**假开关**：点了「按周」而其中一条腿根本不认时间窗，屏上那个「周」字就是在骗人
 * （共享面 [GalleryRankings.supportsAll] 的注释里写着同一条理由）。
 */
class DesktopRankingTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 7)
    private val threeLegs = listOf(GallerySite.YANDERE, GallerySite.SAFEBOORU, GallerySite.GELBOORU)
    private val twoLegs = listOf(GallerySite.YANDERE, GallerySite.SAFEBOORU)

    private fun code(name: String) = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))

    /**
     * ① 可摆的档**由共享那颗判**，而不是桌面自己列一张表。
     *
     * 三腿（含 Gelbooru）时只剩两档：它对四档时间窗一律判死（探测预算不够，2026-09-29 撤下的）；
     * 两腿时六档全真。这一条同时是"桌面不许把 Gelbooru 也算进时间档"的锚。
     */
    @Test
    fun `① 可摆的档由共享那颗判而两腿三腿是两个答案`() {
        assertEquals(
            "带 Gelbooru 的那一组腿只兑现两档（其余四档点得动而发出去是全站结果）",
            listOf(GalleryRanking.NEWEST, GalleryRanking.ALL),
            desktopRankingChoices(threeLegs, today).map { it.ranking },
        )
        assertEquals(
            "两腿时六档全真（次序恒为枚举序，跟着数据飘会变成每次进来不一样）",
            GalleryRanking.entries.toList(),
            desktopRankingChoices(twoLegs, today).map { it.ranking },
        )
        // 对照：清单不是硬编码两枚 —— 每档都带自己的窗口读数。
        val month = desktopRankingChoices(twoLegs, today).first { it.ranking == GalleryRanking.MONTH }
        assertEquals("月档那一行要念出本期区间", "2026-10-01 ~ 2026-10-07", month.window)
        assertNull("「默认（最新）」没有窗口可念", desktopRankingChoices(twoLegs, today).first { it.ranking == GalleryRanking.NEWEST }.window)
    }

    /**
     * ② 摆不出的档**逐档点名是哪一站拦的**。
     *
     * "这一档不支持"是一句没有对象的话 —— 用户接着会问"那谁不支持"，而那一问屏上答不了。
     */
    @Test
    fun `② 摆不出的档点名是哪一站认不了`() {
        val hidden = desktopRankingHidden(threeLegs)
        assertEquals(
            "三腿时四档时间窗摆不出",
            listOf(GalleryRanking.DAY, GalleryRanking.WEEK, GalleryRanking.MONTH, GalleryRanking.YEAR),
            hidden.keys.toList(),
        )
        hidden.forEach { (ranking, blockers) ->
            assertEquals(
                "「${ranking.label}」的拦事者应当只有 Gelbooru",
                listOf(GallerySite.GELBOORU),
                blockers,
            )
        }
        assertTrue("对照失效：两腿时不该有任何一档摆不出", desktopRankingHidden(twoLegs).isEmpty())
    }

    /**
     * ③ 请求的档兑现不了时**落回默认档，而那一句必须出声**。
     *
     * 静默落回就是"点按周、看到最新"那种最难查的读数；而崩窗更不是答案（这一档没有开错窗的代价）。
     */
    @Test
    fun `③ 兑现不了就落回默认而那句要念出来`() {
        assertEquals(GalleryRanking.NEWEST, desktopRankingLanding(threeLegs, GalleryRanking.MONTH))
        assertEquals("兑现得了的档不许被改动", GalleryRanking.ALL, desktopRankingLanding(threeLegs, GalleryRanking.ALL))
        val note = desktopRankingNote(GalleryRanking.MONTH, desktopRankingLanding(threeLegs, GalleryRanking.MONTH))
        assertNotNull("落回而不说 = 假开关", note)
        assertTrue("那句没点名要的是哪一档：$note", note!!.contains(GalleryRanking.MONTH.label))
        assertTrue("那句没说落到哪：$note", note.contains(GalleryRanking.NEWEST.label) || note.contains("默认"))
        assertNull("没落回时不该有话说", desktopRankingNote(GalleryRanking.ALL, GalleryRanking.ALL))
    }

    /**
     * ④ 发出去那一串**逐字转交**共享那颗，桌面一个字都不拼。
     *
     * 两站的分排序写法不能互抄（`order:score` 在 Gelbooru 是 0 结果，而 `order=rank` 这种看着像的会被
     * **静默忽略**并回一批与默认完全相同的东西）—— 桌面一旦自己拼串，这条就只剩注释在守。
     */
    @Test
    fun `④ 查询串逐字转交共享那颗`() {
        listOf(
            GallerySite.YANDERE to GalleryRanking.DAY,
            GallerySite.SAFEBOORU to GalleryRanking.MONTH,
            GallerySite.GELBOORU to GalleryRanking.ALL,
            GallerySite.YANDERE to GalleryRanking.ALL,
            GallerySite.YANDERE to GalleryRanking.NEWEST,
        ).forEach { (site, ranking) ->
            assertEquals(
                "$site 的「${ranking.label}」那一串不是共享那颗算出来的",
                GalleryRankings.searchQuery(site, emptyList(), ranking, null, today),
                desktopRankingQuery(site, ranking, null, today),
            )
        }
        assertEquals("「默认（最新）」不需要任何后缀", "", desktopRankingQuery(GallerySite.YANDERE, GalleryRanking.NEWEST, null, today))
        assertTrue(
            "对照失效：yande.re 的时间档那一串里既没有日期窗也没有分数串",
            desktopRankingQuery(GallerySite.YANDERE, GalleryRanking.DAY, null, today).let { it.contains("date:") && it.contains("order:score") },
        )
    }

    /**
     * ⑤ 桌面这一侧不许给排行**另造机制**：不加排序形参、不做本地重排。
     *
     * 与 `DesktopLatestTest` ④⑥ 同一族（那一屏量过"本地排一次排出来的是三站第一页里的前 N 条"）。
     */
    /**
     * ⑥ 那一屏吃**同一面墙的三层**：分级守卫、三枚偏好、点卡的接收者。
     *
     * 新屏最容易犯的错不是崩，而是"少接一枚" —— 少接守卫就是又一面没过分级就摆出来的墙（第四批立的那一族）。
     */
    /**
     * ⑦ 续页吃那颗共享持有件，桌面这边不另造页码账（与另三面墙同一条判式）。
     */
    /**
     * ⑧ 屏上那句"这一榜数的是哪一段时间"由**真跑**算出来，而不是跟着档名猜。
     */
    @Test
    fun `⑧ 窗口那句真跑而不是猜`() {
        assertNull("「默认（最新）」没有窗口可念", desktopRankingWindowReading(GalleryRanking.NEWEST, null, today))
        assertNull("「全部」不设窗口", desktopRankingWindowReading(GalleryRanking.ALL, null, today))
        assertEquals("天档就是今天", "2026-10-07", desktopRankingWindowReading(GalleryRanking.DAY, null, today))
        assertEquals(
            "周档落在本周一 ~ 今天",
            "2026-10-05 ~ 2026-10-07",
            desktopRankingWindowReading(GalleryRanking.WEEK, null, today),
        )
        // 历史锚点：月档挑 2024-03 时念的是那个月，而不是"本月"。
        assertEquals(
            "月档带历史锚点",
            "2024-03-01 ~ 2024-03-31",
            desktopRankingWindowReading(GalleryRanking.MONTH, LocalDate.of(2024, 3, 12), today),
        )
    }

    /**
     * ⑨ 那一行**已转正**：内容件在、缺席说明撤掉（屏上那句"缺的是选档那一屏"不许还挂着）。
     *
     * 项数那笔账（未实现 3→2）由 `DesktopGalleryHomeSectionsTest` ⑤ 钉，这里只钉"这一行自己"，
     * 免得同一件事两把尺子各钉一遍（批 14 那条）。
     */
    @Test
    fun `⑨ 排行榜那一行已转正`() {
        val row = desktopGalleryHomeRows().first { it.key == DesktopGalleryHomeSectionKey.RANKING }
        assertNotNull("「排行榜」应当有内容件", row.content)
        assertNull("「排行榜」转正后不许还挂着缺席说明", row.reason)
        assertEquals(
            "转正后那一档的内容件应是 Ranking",
            DesktopGallerySectionContent.Ranking,
            row.content,
        )
    }
}
