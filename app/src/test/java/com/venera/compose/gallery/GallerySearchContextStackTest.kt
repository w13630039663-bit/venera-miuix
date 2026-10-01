package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryContextPlan
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GallerySearchContext
import com.venera.compose.gallery.domain.GallerySearchContextStack.MAX_DEPTH
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.GallerySearchContextStack.pop
import com.venera.compose.gallery.domain.GallerySearchContextStack.plan
import com.venera.compose.gallery.domain.GallerySearchContextStack.push
import com.venera.compose.gallery.domain.GalleryTagFilter
import com.venera.compose.gallery.domain.isStaleContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画廊搜索的**上下文栈**判据（2026-09-28 第 2 项）。
 *
 * 病因是两条按设计执行的语义：交接时 `acceptHandoff` 把条件**整片替换**，返回时 BackHandler
 * 一格到底 `closeSearch()` —— 于是"搜 touhou → 点进大图 → 点画师 wowoguni → 返回"落到每日推荐，
 * touhou 那一轮再也回不去。修法是压栈：换一轮之前把上一轮整片存下来，返回先弹栈。
 *
 * 判据全在 `gallery/domain`（本项目单元测试没有 Robolectric，ViewModel 层测不到），
 * 所以这一层要能被这些用例问倒。
 */
class GallerySearchContextStackTest {

    private fun one(site: GallerySite, tag: String, results: Int, page: Int) = GallerySearchContext(
        source = GallerySearchSource.single(site),
        filters = listOf(GalleryTagFilter(tag)),
        results = List(results) { GalleryPost(site = site, id = it.toLong()) },
        page = page,
        exhausted = false,
        droppedNoImage = 3,
        pageSize = 40,
    )

    /** touhou 那一轮：100 张、已经取到第 2 页。 */
    private val touhou = one(GallerySite.YANDERE, "touhou", results = 100, page = 2)

    /** 还没搜过的空一屏（刚点顶栏 🔍，一枚胶囊都没有）。 */
    private val nothingSearched = GallerySearchContext(
        source = GallerySearchSource.single(GallerySite.YANDERE),
        filters = emptyList(),
        results = emptyList(),
        page = 0,
        exhausted = false,
        droppedNoImage = 0,
        pageSize = 0,
    )

    private val wowoguni = listOf(GalleryTagFilter("wowoguni"))

    @Test
    fun `换一轮上下文时上一轮整片压栈`() {
        assertEquals(GalleryContextPlan.PUSH, plan(touhou.site, touhou.filters, GallerySite.YANDERE, wowoguni))
        val stack = push(emptyList(), touhou)
        val saved = stack.single()
        // 弹回来要能**原样**接上那一屏，所以每一项都得在快照里：站、条件、图、游标、每页张数。
        assertEquals(listOf("touhou"), saved.filters.map { it.name })
        assertEquals(100, saved.results.size)
        assertEquals(2, saved.page)
        assertEquals(40, saved.pageSize)
        assertEquals(3, saved.droppedNoImage)
    }

    @Test
    fun `出栈回到的那一轮的条件与站与原样一致`() {
        val wowo = one(GallerySite.GELBOORU, "wowoguni", results = 42, page = 1)
        val stack = push(push(emptyList(), touhou), wowo)
        val (top, rest) = pop(stack)!!
        assertEquals(wowo, top)
        assertEquals(listOf(touhou), rest)
    }

    @Test
    fun `同站同条件再交接不压栈只重搜`() {
        // 同一枚标签点两次必须幂等：压栈的话，返回会先弹回"一模一样的一轮"，白按一次。
        assertEquals(
            GalleryContextPlan.RESEARCH_IN_PLACE,
            plan(touhou.site, touhou.filters, GallerySite.YANDERE, listOf(GalleryTagFilter("touhou"))),
        )
    }

    @Test
    fun `当前还没搜过时不压空壳`() {
        // filters 为空时那一屏根本没有"上一轮"，压进去就等于塞一格空壳 —— 返回会先弹到它、
        // 屏上什么都不变，用户读不出那一下干了什么。
        assertEquals(
            GalleryContextPlan.SKIP_EMPTY,
            plan(nothingSearched.site, nothingSearched.filters, GallerySite.YANDERE, wowoguni),
        )
    }

    @Test
    fun `超过深度丢的是最旧那一轮`() {
        var stack = emptyList<GallerySearchContext>()
        repeat(MAX_DEPTH + 3) { i ->
            stack = push(stack, one(GallerySite.YANDERE, "tag$i", results = 1, page = 1))
        }
        assertEquals(MAX_DEPTH, stack.size)
        // 丢栈底（最旧）而不是丢栈顶：最旧那轮是"八次返回之前再也到不了"的上下文，用户读不出来；
        // 刚压进去那轮正是他要回的东西，绝不能丢。
        assertEquals("tag3", stack.first().filters.single().name)
        assertEquals("tag10", stack.last().filters.single().name)
    }

    @Test
    fun `交接不与上一轮 AND`() {
        // 定案：从一张 touhou 同人图里点画师名，用户要的是"这个人的别的画"。
        // 合成 `touhou wowoguni` 只剩寥寥几张，恰好把他想要的那批排掉 —— 所以是**另起一轮**。
        // 哪天有人顺手改成合并，这两句都会红（两串各归各格，谁也不吃掉谁、也不并进来）。
        val wowo = one(GallerySite.YANDERE, "wowoguni", results = 7, page = 1)
        val (top, rest) = pop(push(push(emptyList(), touhou), wowo))!!
        assertEquals(listOf("wowoguni"), top.filters.map { it.name })
        assertEquals(listOf("touhou"), rest.single().filters.map { it.name })
    }

    @Test
    fun `跨站交接必然开新一轮`() {
        // 条件一串不差、换了站：两站词表不通、结果也不同源，这一轮**必须**压栈
        // （原地重搜会让"胶囊写着这一站、图来自那一站"那类返祖有可乘之机）。
        assertEquals(
            GalleryContextPlan.PUSH,
            plan(touhou.site, touhou.filters, GallerySite.GELBOORU, listOf(GalleryTagFilter("touhou"))),
        )
    }

    @Test
    fun `过期判据认轮次也认站点`() {
        assertFalse(isStaleContext(3, GallerySite.YANDERE, 3, GallerySite.YANDERE))
        // 同站换了轮次也要作废：touhou 正在飞第 2 页 → 交接进 wowoguni → 返回弹回 touhou，
        // **站没变**，旧那笔若只认站就会把 100 张新页 append 回错误的轮次。
        assertTrue(isStaleContext(3, GallerySite.YANDERE, 4, GallerySite.YANDERE))
        // 轮次没动但站换了：顶栏那排源切换不压栈、不转轮次，靠站点这一半拦住。
        assertTrue(isStaleContext(3, GallerySite.YANDERE, 3, GallerySite.GELBOORU))
    }

    @Test
    fun `同来源同条件幂等不压栈 换来源必开新一轮`() {
        // 「全部」那一屏，条件一串不差地再交接一次 —— 幂等，不许压栈（同上一条用例的理由）。
        val touhouAll = touhou.copy(source = GallerySearchSource.ALL)
        assertEquals(
            GalleryContextPlan.RESEARCH_IN_PLACE,
            plan(touhouAll.source, touhouAll.filters, GallerySearchSource.ALL, touhouAll.filters),
        )
        // 同一串条件从「全部」退回单站：少了一条腿，屏上张数会变。不压栈就回不去刚才那一屏，
        // 页尾的读数还会写着两站的张数配一张站的墙 —— 这一类错位必须由"换来源=换一轮"拦住。
        assertEquals(
            GalleryContextPlan.PUSH,
            plan(
                touhouAll.source,
                touhouAll.filters,
                GallerySearchSource.single(GallerySite.YANDERE),
                touhouAll.filters,
            ),
        )
    }

    @Test
    fun `弹栈把来源与每条腿的游标一起带回去`() {
        // 「全部」档的游标是分腿记的：只抄一个全局 page 就会「弹回来还是那 200 张，
        // 再往下翻却从头重要一遍已经看过的内容」。
        val all = touhou.copy(
            source = GallerySearchSource.ALL,
            pageBySite = mapOf(GallerySite.YANDERE to 3, GallerySite.GELBOORU to 2),
            exhaustedBySite = setOf(GallerySite.GELBOORU),
        )
        val (top, rest) = pop(push(emptyList(), all))!!
        assertEquals(GallerySearchSource.ALL, top.source)
        assertEquals(mapOf(GallerySite.YANDERE to 3, GallerySite.GELBOORU to 2), top.pageBySite)
        assertEquals(setOf(GallerySite.GELBOORU), top.exhaustedBySite)
        assertTrue(rest.isEmpty())
    }

    @Test
    fun `弹栈把排行档与所选期一起带回去`() {
        // 档位是"那一屏怎么排的"，属于那一轮自己 —— 漏抄这一项就会出现
        // 「按周排行看着 → 点一枚标签 → 返回 → 屏上还是那批图，档却悄悄变回默认」。
        // 期次（periodAnchor）同一条理由：翻到 2024 年 3 月再点一枚标签，返回后
        // 屏上若还是那批 2024-03 的图而读数写着本期，就是同一类错位。
        val march = touhou.copy(
            ranking = GalleryRanking.MONTH,
            periodAnchor = LocalDate.of(2024, 3, 15),
        )
        val wowo = one(GallerySite.YANDERE, "wowoguni", results = 7, page = 1)
        val (top, rest) = pop(push(push(emptyList(), march), wowo))!!
        assertEquals(GalleryRanking.MONTH, rest.single().ranking)
        assertEquals(LocalDate.of(2024, 3, 15), rest.single().periodAnchor)
        // 刚压上去那一轮用的是它自己的档（新条件 = 默认档 + 本期，由调用方写进快照）。
        assertEquals(GalleryRanking.NEWEST, top.ranking)
        assertNull(top.periodAnchor)
    }

    @Test
    fun `空栈出栈返回 null 即该关搜索`() {
        assertNull(pop(emptyList()))
        assertTrue(pop(listOf(touhou))!!.second.isEmpty())
    }
}
