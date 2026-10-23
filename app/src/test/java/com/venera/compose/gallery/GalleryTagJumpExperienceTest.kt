package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「点 Tag 跳转体验」四件套的源码尺（2026-10-23，用户一次点四件事）：
 *
 * 1. **交接即收条**：`acceptHandoff`（booru 那一支）从 `thenCollapse = false` 改成 true ——
 *    点标签的人要的就是"这个词的结果墙"，不是一张摊开的输入卡 + 键盘；
 * 2. **直达结果**：`Navigation.kt` 交接那一个 effect 改用
 *    `navigate(GalleryRoute) { launchSingleTop = true; popUpTo<GalleryRoute>() }`，
 *    清掉中间的画廊二级页。根因是 `gotoTab` 带 `restoreState`，会把「GalleryRoute + 二级页」
 *    整条已存栈恢复回来，目的地一字未变（真机读作"点了没反应"）；
 * 3. **标签名清洗**：`postTag` 写槽前过 `sanitizeGalleryTagName`（纯函数判据在
 *    `GalleryTagNameSanitizeTest`，这里只钉接线）；
 * 4. **双源词条保持**：`setDomain` 切域时把主词带进下一域的输入格（booru 第一枚胶囊 ↔
 *    pixivTerm），只带词、不代搜。
 *
 * ⚠️ 与 `PixivTagHandoffTest` 同一把尺子：本仓无 Robolectric，ViewModel 与导航层摸不到，
 * 这些不变量只能钉在**源码形状**上。改形状前先读懂每条断言在钉什么。
 */
class GalleryTagJumpExperienceTest {

    private fun src(rel: String): String {
        val f = File(RepoSources.appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    /** 注释行不参与匹配（与 `PixivTagHandoffTest` 同一把尺子）。 */
    private fun code(rel: String): List<String> = src(rel).lines().map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private fun List<String>.countWith(needle: String): Int = count { it.contains(needle) }

    private val vm = "com/venera/compose/gallery/ui/GallerySearchViewModel.kt"
    private val handoff = "com/venera/compose/gallery/ui/GallerySearchHandoff.kt"
    private val navigation = "com/venera/compose/feature/Navigation.kt"

    /** 交接 effect 的函数体行数窗口（含首行共 8 行：3 句判断 + navigate 块 + 收尾）。 */
    private val EFFECT_WINDOW = 8

    // ── 一、交接即收条：两个域同一个口径 ────────────────────────────────

    @Test
    fun `交接与画师卡都收成 RESULTS 单行态`() {
        val body = code(vm)
        assertEquals(
            "booru 交接装完就收条（用户 2026-10-23「点 Tag 直达搜索结果」）",
            1,
            body.countWith("openContext(next, tags.map { GalleryTagFilter(it) }, thenCollapse = true)"),
        )
        assertEquals(
            "画师卡那一档同样收条（与 acceptHandoff 只差来源那一处）",
            1,
            body.countWith("openContext(GallerySearchSource.ALL, tags.map { GalleryTagFilter(it) }, thenCollapse = true)"),
        )
        // 点历史那一处原本就是 true，别被顺手改掉。
        assertEquals("点历史仍收条", 1, body.countWith("thenCollapse = true,"))
        // 全文件不许再有人不收条：三种入口（点历史 / 点标签 / 点画师）一个口径。
        assertEquals("不许再有 thenCollapse = false 的调用方", 0, body.countWith("thenCollapse = false"))
    }

    // ── 二、直达结果：交接那跳清掉画廊二级页 ────────────────────────────

    @Test
    fun `交接跳转清掉二级页直达 GalleryRoute`() {
        val nav = code(navigation)
        // 交接那一个 effect 里只许有一种跳法。
        assertEquals("交接 effect 只许有一处 navigate(GalleryRoute)", 1, nav.countWith("navController.navigate(GalleryRoute) {"))
        assertEquals("必须带 popUpTo<GalleryRoute>() 清二级页", 1, nav.countWith("popUpTo<GalleryRoute>()"))

        val start = nav.indexOfFirst { it.contains("LaunchedEffect(GallerySearchHandoff.pending, destination)") }
        assertTrue("量具失效：找不到交接 effect", start >= 0)
        val effect = nav.subList(start, (start + EFFECT_WINDOW).coerceAtMost(nav.size))
        assertTrue("交接 effect 必须落到 GalleryRoute", effect.any { it.contains("navController.navigate(GalleryRoute)") })
        assertTrue("交接 effect 必须 popUpTo<GalleryRoute>()", effect.any { it == "popUpTo<GalleryRoute>()" })
        assertEquals("清完二级页要 singleTop，否则压出两页画廊", 1, effect.countWith("launchSingleTop = true"))
        // 交接这条路上不许再走 gotoTab：它带 restoreState = true，而 popUpTo(起始页){saveState}
        // 刚把「GalleryRoute + 二级页」整条存进 backStackMap ⇒ 恢复回来目的地一字未变。
        assertTrue(
            "交接 effect 不许再走 gotoTab（restoreState 会把二级页整条恢复回来）",
            effect.none { it.contains("gotoTab") },
        )
        // 判据读目的地而不是 Tab（画廊二级页上 currentTab 也是 GALLERY）。
        assertTrue(
            "判据仍读目的地",
            effect.any { it.contains("if (destination?.hasRoute(GalleryRoute::class) == true) return@LaunchedEffect") },
        )
    }

    // ── 三、写槽前先清洗标签名 ─────────────────────────────────────────

    @Test
    fun `交接槽写入前先清洗标签名`() {
        val body = code(handoff)
        val start = body.indexOfFirst { it.startsWith("fun postTag(") }
        assertTrue("量具失效：找不到 postTag", start >= 0)
        val stop = (start + 1 until body.size).firstOrNull { body[it].startsWith("fun ") } ?: body.size
        val fn = body.subList(start, stop)
        assertEquals("写槽前必须过清洗", 1, fn.countWith("sanitizeGalleryTagName(tag)"))
        assertEquals("洗成空串不写槽（没有可搜的词）", 1, fn.countWith("if (cleaned.isEmpty()) return"))
        assertEquals("写进去的必须是洗过的那一份", 1, fn.countWith("pending = Request(site, listOf(cleaned))"))
    }

    // ── 四、双源词条保持：切域带主词、不代搜 ────────────────────────────

    @Test
    fun `切域把主词带进下一域的输入格且不代搜`() {
        val body = code(vm)
        val start = body.indexOfFirst { it.startsWith("fun setDomain(") }
        assertTrue("量具失效：找不到 setDomain", start >= 0)
        val stop = (start + 1 until body.size).firstOrNull { body[it].startsWith("fun ") } ?: body.size
        val fn = body.subList(start, stop)
        // 两个方向各带一次：booru 主词（第一枚胶囊，无胶囊时取框里半截词）→ pixivTerm；
        // pixiv 主词 → booru 的 term（框里那半截词，按键盘搜索键即 submitTerm 开搜）。
        assertEquals("带去 pixiv 那一格", 1, fn.countWith("if (main.isNotBlank()) pixivTerm = main"))
        assertEquals("带去 booru 那一格", 1, fn.countWith("if (main.isNotBlank()) term = main"))
        // 只带词、不代搜：这一段里出现任何开搜入口都意味着"来回切两下四笔网络往返"
        // （setDomain 既有纪律第 3 条，用户 2026-09-30 改判立下的）。
        assertEquals("切域不许顺手开搜（booru 腿）", 0, fn.countWith("runSearch("))
        assertEquals("切域不许顺手开搜（pixiv 腿）", 0, fn.countWith("runPixivSearch("))
        assertEquals("切域不许改条件（改条件会触发自动搜）", 0, fn.countWith("applyFilters("))
        // 主词的来源写死：booru 取第一枚胶囊、pixiv 取 pixivTerm，不猜。
        assertTrue(
            "booru 主词取第一枚胶囊",
            fn.any { it.contains("filters.firstOrNull()?.name") },
        )
    }
}
