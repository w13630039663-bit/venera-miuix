package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRecommendation
import com.venera.compose.gallery.domain.GalleryRecommendations
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GalleryTagFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面「发现」那一屏的**取数计划**（第九批）。
 *
 * ## 这批要钉住的不是"取到了什么"，而是"没取到的时候说的是哪一句"
 *
 * 这一屏最容易做坏的三处都在空态上，而且 Android 那侧已经把理由写在前头了
 * （`GalleryForYouViewModel.stageOf` 的头注）：
 *
 * - **「你还没收藏」与「我们的判据算不出东西」是两句话**。合成一句就是拿"用户没干活"
 *   替自己的缺陷遮丑 —— 而后者是 `NoUsableTags`（有收藏，但标签全被黑名单滤掉）。
 * - **一站有货、另一站没货时不许把整屏报空**。少的那一站由逐站点名去说。
 * - **发出去的那一串与页尾念的那一串必须是同一串** —— 所以只能经 `GallerySearch.queryOf`，
 *   桌面这边不许自己 `joinToString`。
 *
 * ## 为什么这里只测计划而不测那一屏
 *
 * 与 `DesktopGalleryWallPolicy` 同一条理由：**能真跑的判据永远比能扫的可靠**。
 * 计划那颗是纯函数，直接取值；屏上那几行字（守卫、三枚偏好、两句缺席点名、换一批）
 * 由 `DesktopForYouPaneTest` 扫源码 —— 那一屏今天没有可注入的假端口，成因写在那颗类注释里。
 */
class DesktopForYouPlanTest {

    private fun fav(site: GallerySite, id: Long, tags: String) =
        GalleryFavorite(siteKey = site.routeKey, id = id, tags = tags)

    private fun planFor(
        favourites: List<GalleryFavorite>,
        blocked: Set<String> = emptySet(),
    ) = desktopForYouPlan(favourites, seed = 42L, isBlocked = blocked::contains)

    /** ① 三站各有收藏 ⇒ 三串都发得出去，且串是 `queryOf` 那一串而不是桌面自己拼的。 */
    @Test
    fun 三站有货时三串都发得出且经queryOf拼() {
        val favourites = listOf(
            fav(GallerySite.YANDERE, 1L, "mountain_river"),
            fav(GallerySite.GELBOORU, 2L, "1girl"),
            fav(GallerySite.SAFEBOORU, 3L, "sky"),
        )
        val plan = planFor(favourites)
        assertEquals("三站都该发得出去", 3, plan.queries.size)
        plan.perSite.forEach { (site, rec) ->
            assertTrue("「${site.displayName}」该抽得出标签，实际是 $rec", rec is GalleryRecommendation.Tags)
        }
        // 逐字回核：桌面串必须等于共享那颗对同一批标签算出来的那一串。
        val tags = (plan.perSite.getValue(GallerySite.YANDERE) as GalleryRecommendation.Tags).tags
        assertEquals(
            "页尾念的那串与真发出去的那串必须同源（都走 GallerySearch.queryOf）",
            GallerySearch.queryOf(tags.map { GalleryTagFilter(it) }),
            plan.queries.getValue(GallerySite.YANDERE),
        )
        // 对照组：桌面没有把标签直接 joinToString —— 那会长出第三种形状。
        assertTrue("对照组失效：抽到的标签为空，上面那条比的是空气", tags.isNotEmpty())
    }

    /** ② 一站有货另两站没货 ⇒ 整屏**照发**，没货那两站逐站点名，不许把整屏报空。 */
    @Test
    fun 一站有货时整屏照发而另两站点名() {
        val plan = planFor(listOf(fav(GallerySite.YANDERE, 1L, "sky")))
        assertTrue("有一站发得出去就不许把整屏判成空", plan.canDispatch)
        assertEquals(1, plan.queries.size)
        assertEquals(
            "该点名的两站（次序恒为枚举自然序，不跟数据飘）",
            listOf(GallerySite.GELBOORU, GallerySite.SAFEBOORU),
            plan.silentSites,
        )
        assertNotNull("没点名「Gelbooru」为什么发不出去", plan.reasonFor(GallerySite.GELBOORU))
        assertTrue(
            "那句必须说清是「这一站没有收藏」，而不是含糊的「没有内容」",
            plan.reasonFor(GallerySite.GELBOORU)!!.contains("收藏"),
        )
        assertNull("发得出去的那一站不该有原因", plan.reasonFor(GallerySite.YANDERE))
    }

    /** ③ 两站都没收藏 ⇒ 整屏那句是"引导去收藏"，**不是**技术错误。 */
    @Test
    fun 两站都没收藏时那句引导而不是报错() {
        val plan = planFor(emptyList())
        assertTrue("一条都发不出去", plan.queries.isEmpty())
        assertFalse(plan.canDispatch)
        val blank = plan.blankScreenReason
        assertNotNull("整屏空态必须有一句", blank)
        assertTrue("那句必须点「收藏」这件事，不许写成「暂无数据」：$blank", blank!!.contains("收藏"))
        assertTrue("那句不许把用户没干活说成系统故障：$blank", blank.contains("失败").not())
    }

    /** ④ 有收藏但标签全被滤掉 ⇒ 这一档**必须与 ③ 不同一句**（这是我们的缺陷，不是用户没干活）。 */
    @Test
    fun 标签被滤光与没收藏是两句话() {
        val favourites = listOf(
            fav(GallerySite.YANDERE, 1L, "sky"),
            fav(GallerySite.GELBOORU, 2L, "ocean"),
            fav(GallerySite.SAFEBOORU, 3L, "forest"),
        )
        val all = setOf("sky", "ocean", "forest")
        val blocked = planFor(favourites, blocked = all)
        val empty = planFor(emptyList())
        assertFalse("标签全被滤掉时不该发车", blocked.canDispatch)
        assertFalse("没收藏时也不该发车", empty.canDispatch)
        val ours = requireNotNull(blocked.blankScreenReason)
        val theirs = requireNotNull(empty.blankScreenReason)
        assertTrue("两档必须念成两句不同的话", ours != theirs)
        // 我们这一档那句要认得出"有收藏"这个前提，否则用户会以为是自己没干活。
        assertTrue("「我们的缺陷」那句必须说出收藏是有的：$ours", ours.contains("有收藏"))
        // 逐站点名那两档同样要分得开（整屏那句对，站级那句也不能糊）。
        val blockedSite = requireNotNull(blocked.reasonFor(GallerySite.YANDERE))
        val emptySite = requireNotNull(empty.reasonFor(GallerySite.YANDERE))
        assertTrue("站级那句也必须分开：$blockedSite / $emptySite", blockedSite != emptySite)
    }

    /** ⑤ 种子决定抽样：同一份收藏换种子要真换东西，否则「换一批」是假按钮。 */
    @Test
    fun 换种子确实换抽样而同一串不变() {
        val favourites = List(24) { i ->
            fav(GallerySite.YANDERE, i.toLong(), "tag$i")
        } + List(24) { i -> fav(GallerySite.GELBOORU, 100L + i, "other$i") }
        val a = desktopForYouPlan(favourites, seed = 1L, isBlocked = { false })
        val b = desktopForYouPlan(favourites, seed = 2L, isBlocked = { false })
        val same = desktopForYouPlan(favourites, seed = 1L, isBlocked = { false })
        assertEquals("同一颗种子必须交回同一串（组合重建时不许换序）", a.queries, same.queries)
        assertTrue(
            "换种子却两串都一样 ⇒「换一批」按下去不换东西",
            a.queries != b.queries || a.perSite.keys != b.perSite.keys,
        )
    }

    /** ⑥ 计划那颗件只许转交：不许出现桌面自己拼串或自己算标签。 */
    @Test
    fun 计划件里不许出现桌面自己拼的那串() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopForYouPolicy.kt"))
        listOf("joinToString(\" \")", "split(\" \")", "Regex(").forEach { banned ->
            assertTrue("计划件里出现了「$banned」—— 拼串与切串都只许走共享那颗", code.contains(banned).not())
        }
        assertTrue("对照组失效：没读到转交 recommendBySite", code.contains("GalleryRecommendations.recommendBySite"))
        assertTrue("对照组失效：没读到转交 queryOf", code.contains("queryOf"))
        assertTrue("对照组失效：codeText 读空了", code.contains("internal "))
    }

    /** ⑦ 屏上那几句不许把**站点数目**写死 —— `GallerySite` 加一站，那些句子当场变假读数。 */
    @Test
    fun 计划里的句子不许写死站点数目() {
        val stale = listOf("两站", "三站", "2 站", "3 站")
        val plan = desktopForYouPlan(
            listOf(fav(GallerySite.YANDERE, 1L, "sky")),
            seed = 7L,
            isBlocked = { true },
        )
        val sentences = buildList {
            GallerySite.entries.forEach { site -> plan.reasonFor(site)?.let { add(it) } }
            plan.blankScreenReason?.let { add(it) }
        }
        assertTrue("对照组失效：一句都没取到（那这条恒真）", sentences.size >= 2)
        sentences.forEach { line ->
            stale.forEach { word ->
                assertTrue("那句里写死了站点数目「$word」：$line", line.contains(word).not())
            }
        }
    }

    /** ⑧ 那枚"漏传不报错"的参数一律**必填**。 */
    @Test
    fun 屏蔽判据那一枚参数不许带默认值() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopForYouPolicy.kt"))
        assertTrue("对照组失效：没读到那枚参数的声明", code.contains("isBlocked: (String) -> Boolean"))
        assertTrue(
            "那枚参数带了默认值 ⇒ 漏传不报错，而漏传的形状正好是「屏蔽规则不参与抽样」，" +
                "且屏上那句「是我们的判据空」永远走不到",
            code.contains("isBlocked: (String) -> Boolean =").not(),
        )
        // 屏上那一处必须真把守卫的判据传进来，而不是随手传一枚恒假。
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopForYouPane.kt"))
        assertTrue("那一屏没把内容守卫的黑名单判据传给计划件", pane.contains("blockedGalleryRule("))
    }
}
