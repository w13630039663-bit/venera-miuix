package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryBrowseRow
import com.venera.compose.gallery.domain.GalleryBrowseRows
import com.venera.compose.gallery.domain.GalleryDayGroup
import com.venera.compose.gallery.ui.galleryDayGroupLabel
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 画廊浏览历史的**纯判据**（`domain/GalleryBrowseHistory.kt` + 分组头的日期词）。
 *
 * 2026-10-14 与首页顶栏那枚「浏览历史」同一轮立起来。三件事最值得钉：
 * ① 重复看同一张**刷新时刻并置顶**（与关注名单那条「不重掷时间戳」刻意相反）；
 * ② 分组按**日历日**而不是 24 小时窗口；
 * ③ 日期词是屏上的话，改了得连屏一起看 —— 所以它是颗可直接调用的纯函数，不用 grep 源码。
 */
class GalleryBrowseRowsTest {

    private fun row(
        site: GallerySite = GallerySite.YANDERE,
        id: Long = 1L,
        thumb: String = "https://cdn/$id.jpg",
        at: Long = 1_000L,
    ) = GalleryBrowseRow(site, id, thumb, at)

    /** 本地时区的一个时刻。判据与测试用同一个默认时区，所以不必换算。 */
    private fun at(y: Int, month: Int, d: Int, h: Int, mi: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(y, month, d, h, mi)
        }.timeInMillis

    // ---- 记一笔 ----

    @Test
    fun `重复看同一张只留一行，时刻刷新并置顶`() {
        val older = row(id = 1L, at = 100L)
        val other = row(id = 2L, at = 200L)
        val again = row(id = 1L, at = 300L)

        val after = GalleryBrowseRows.withViewed(listOf(other, older), again)

        assertEquals("同一张又出现了一次", 2, after.size)
        assertEquals("置顶的必须是刚看过那张", 300L, after.first().viewedAt)
        assertEquals(1L, after.first().postId)
    }

    @Test
    fun `重复看时缩略图地址跟着换新`() {
        // 站方换 CDN 路径之后旧地址可能已经失效；留旧的等于历史墙上那一格永远读不出图。
        val stale = row(id = 1L, thumb = "https://old/1.jpg", at = 100L)
        val fresh = row(id = 1L, thumb = "https://new/1.jpg", at = 300L)

        val after = GalleryBrowseRows.withViewed(listOf(stale), fresh)

        assertEquals(1, after.size)
        assertEquals("https://new/1.jpg", after.first().thumbUrl)
    }

    @Test
    fun `两站的同图号是两张不同的图`() {
        // uid 带站键：两站各自编号，撞了不能去重（撞 key 会直接崩，见墙上那条注释）。
        val yande = row(site = GallerySite.YANDERE, id = 7L, at = 100L)
        val gelbooru = row(site = GallerySite.GELBOORU, id = 7L, at = 100L)

        assertEquals(2, GalleryBrowseRows.withViewed(listOf(yande), gelbooru).size)
    }

    // ---- 排序与读取时的收拢 ----

    @Test
    fun `上屏顺序是最近看过在前`() {
        val rows = listOf(row(id = 1L, at = 10L), row(id = 2L, at = 30L), row(id = 3L, at = 20L))
        assertEquals(listOf(2L, 3L, 1L), GalleryBrowseRows.sorted(rows).map { it.postId })
    }

    @Test
    fun `同一时刻按站键加图号定序，不靠集合迭代顺序`() {
        val rows = listOf(
            row(site = GallerySite.SAFEBOORU, id = 1L, at = 50L),
            row(site = GallerySite.YANDERE, id = 2L, at = 50L),
            row(site = GallerySite.YANDERE, id = 1L, at = 50L),
        )
        assertEquals(
            // 同刻不比来路，只比 uid：'s' 排在 'y' 前面（判据写的是 thenBy { uid }，
            // 不是"先记的在前"—— 那种顺序读起来像 bug 又没有任何报错会指向它）。
            listOf("safebooru:1", "yandere:1", "yandere:2"),
            GalleryBrowseRows.sorted(rows).map { it.uid },
        )
    }

    @Test
    fun `读盘时同一条重复行留最近那次`() {
        val rows = listOf(row(id = 1L, at = 100L), row(id = 1L, at = 900L), row(id = 2L, at = 5L))
        val deduped = GalleryBrowseRows.dedupe(rows)
        assertEquals(2, deduped.size)
        assertEquals(900L, deduped.first { it.postId == 1L }.viewedAt)
    }

    @Test
    fun `站点筛子里 null 就是全部那一档`() {
        val rows = listOf(
            row(site = GallerySite.YANDERE, id = 1L),
            row(site = GallerySite.GELBOORU, id = 2L),
        )
        assertEquals(2, GalleryBrowseRows.ofSite(rows, null).size)
        assertEquals(listOf(1L), GalleryBrowseRows.ofSite(rows, GallerySite.YANDERE).map { it.postId })
        assertEquals(emptyList<GalleryBrowseRow>(), GalleryBrowseRows.ofSite(rows, GallerySite.SAFEBOORU))
    }

    // ---- 分组 ----

    @Test
    fun `分组按日历日而不是按 24 小时窗口`() {
        val now = at(2026, Calendar.OCTOBER, 14, 13, 0)
        // 这一行距 now 已过 36.5 小时，但仍是"昨天"—— 按 24 小时窗口算会掉进「更早」。
        assertEquals(
            GalleryDayGroup.YESTERDAY,
            GalleryBrowseRows.dayGroupOf(row(at = at(2026, Calendar.OCTOBER, 13, 0, 30)), now),
        )
    }

    @Test
    fun `零点整算今天，前一分钟算昨天`() {
        val now = at(2026, Calendar.OCTOBER, 14, 13, 0)
        assertEquals(
            GalleryDayGroup.TODAY,
            GalleryBrowseRows.dayGroupOf(row(at = at(2026, Calendar.OCTOBER, 14, 0, 0)), now),
        )
        assertEquals(
            GalleryDayGroup.YESTERDAY,
            GalleryBrowseRows.dayGroupOf(row(at = at(2026, Calendar.OCTOBER, 13, 23, 59)), now),
        )
    }

    @Test
    fun `昨天与更早的边界也是日历日`() {
        val now = at(2026, Calendar.OCTOBER, 14, 13, 0)
        assertEquals(
            GalleryDayGroup.YESTERDAY,
            GalleryBrowseRows.dayGroupOf(row(at = at(2026, Calendar.OCTOBER, 13, 0, 0)), now),
        )
        assertEquals(
            GalleryDayGroup.OLDER,
            GalleryBrowseRows.dayGroupOf(row(at = at(2026, Calendar.OCTOBER, 12, 23, 59)), now),
        )
    }

    @Test
    fun `只有有货的组进结果，组间按今天昨天更早`() {
        val now = at(2026, Calendar.OCTOBER, 14, 13, 0)
        val rows = listOf(
            row(id = 1L, at = at(2026, Calendar.OCTOBER, 12, 9, 0)),   // 更早
            row(id = 2L, at = at(2026, Calendar.OCTOBER, 14, 9, 0)),   // 今天
            row(id = 3L, at = at(2026, Calendar.OCTOBER, 14, 8, 0)),   // 今天（早一刻）
        )
        val groups = GalleryBrowseRows.groupedByDay(rows, now)

        // 没有"昨天"的行 ⇒ 那一组不出现：三个头恒摆会把没内容的头也摆出来。
        assertEquals(listOf(GalleryDayGroup.TODAY, GalleryDayGroup.OLDER), groups.keys.toList())
        assertEquals(listOf(2L, 3L), groups.getValue(GalleryDayGroup.TODAY).map { it.postId })
        assertEquals(listOf(1L), groups.getValue(GalleryDayGroup.OLDER).map { it.postId })
    }

    // ---- 摆哪档比例 ----

    @Test
    fun `比例取不到时退回占位档并夹进墙上那两道边界`() {
        // 0.4~2.5 不是本屏自造的数字，是墙上那张卡同一口径（见 GalleryPostCard 的 ratio 那行）。
        // 方向要看准：ratio 是 宽/高 —— 9 是**横长条**（夹到上限 2.5），0.05 是**竖长条**（夹到下限 0.4）。
        assertEquals(1.5f, GalleryBrowseRows.tileRatio(ratio = 1.5f, placeholderRatio = 0.66f))
        assertEquals(0.66f, GalleryBrowseRows.tileRatio(ratio = 0f, placeholderRatio = 0.66f))
        assertEquals(2.5f, GalleryBrowseRows.tileRatio(ratio = 9f, placeholderRatio = 0.66f))
        assertEquals(0.4f, GalleryBrowseRows.tileRatio(ratio = 0.05f, placeholderRatio = 0.66f))
    }

    @Test
    fun `坏掉的比例不往屏上交`() {
        // 老档被人手工改坏成负数或 NaN 时，那一格不会报错，只会在屏上占 0 高 ——
        // 读起来就是"历史记录丢了"。所以这里要退占位档，不是原样交回。
        assertEquals(0.66f, GalleryBrowseRows.tileRatio(ratio = -3f, placeholderRatio = 0.66f))
        assertEquals(0.66f, GalleryBrowseRows.tileRatio(ratio = Float.NaN, placeholderRatio = 0.66f))
        assertEquals(0.66f, GalleryBrowseRows.tileRatio(ratio = Float.POSITIVE_INFINITY, placeholderRatio = 0.66f))
        // 连占位档 itself 坏掉也不能交 NaN 出去：那是恒 1（正方），一条看得见的退路。
        assertEquals(1f, GalleryBrowseRows.tileRatio(ratio = 0f, placeholderRatio = 0f))
    }

    // ---- 屏上的日期词 ----

    @Test
    fun `分组头那三枚日期词`() {
        // 这三枚词照参考图定；改了屏上的字就得改这里，两边不许各走各的。
        assertEquals("今天", galleryDayGroupLabel(GalleryDayGroup.TODAY))
        assertEquals("昨天", galleryDayGroupLabel(GalleryDayGroup.YESTERDAY))
        assertEquals("更早", galleryDayGroupLabel(GalleryDayGroup.OLDER))
    }
}
