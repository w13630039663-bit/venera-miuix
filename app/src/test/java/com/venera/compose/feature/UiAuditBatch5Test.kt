package com.venera.compose.feature

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-14 UI 审查第五批的尺子：列表卡减负、大图页的可见出口、底栏配色与顶栏地板。
 *
 * 三条都是"改了不会报错、回退了也不会报错"的那一类：
 * ① 简介那一档回到列表卡上 → 单卡又长回 ~155dp、一屏 3.5 张，没人会为此跑一次测试；
 * ② 大图页那枚返回被撤 → 屏上又只剩「4 / 180」，退出只能靠手势；
 * ③ 悬浮底栏又吃一枚写死的近白 → 换种子色/壁纸时它与整屏脱节，而它是屏上最大的一块常驻面。
 *
 * 本批还改了顶栏地板（7 处逐页抄写的 `104.dp` 换成 `tokens.spacing.topBarFloor`），
 * 但那条不变量已由 `ui/tokens/TopBarFloorGuardTest` 守着，这里不再立第二把尺子。
 */
class UiAuditBatch5Test {

    private fun code(dir: String, name: String): List<String> =
        RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private fun text(dir: String, name: String): String =
        RepoSources.kotlinFiles(dir).first { it.name == name }.readText()

    /** ① 列表行卡不再铺简介（要看简介去详情那一屏）。 */
    @Test
    fun `列表行卡不铺简介`() {
        val pres = text("com/venera/compose/components", "ComicListPresentation.kt")
        val body = pres.substringAfter("fun ComicRowCard(").substringBefore("\n) {")
        assertFalse("形参表里又出现 description：$body", body.contains("description"))
        assertFalse("简介那一格又画回来了", pres.contains("text = description,"))
        // 对照：这颗件本身还在、标题与标签都还在（读空了上面两条恒真）。
        assertTrue("对照组失效：没读到行卡那颗件", pres.contains("fun ComicRowCard("))
        assertTrue("标题那一格不在了", pres.contains("text = title,"))
        assertTrue("标签那一排不在了", pres.contains("listTags"))
    }

    /** ② 大图页有一枚看得见的返回，且触达位与底部那排同尺寸。 */
    @Test
    fun `大图页有可见的返回`() {
        val post = code("com/venera/compose/gallery/ui", "GalleryPostScreen.kt")
        assertTrue(
            "左上角那枚返回不摆了（退出只剩手势，屏上没有任何说法）",
            post.any { it.contains("GalleryViewerBackButton(onBack = onBack)") },
        )
        val back = post.subList(
            post.indexOfFirst { it.startsWith("private fun GalleryViewerBackButton(") }.coerceAtLeast(0),
            post.size,
        ).take(20)
        assertTrue(
            "返回那枚的触达位没吃现成的 48dp 口径：$back",
            back.any { it.contains("VeneraIconButtonTouchSize") },
        )
        // 对照：页码读数仍然在同一排（撤返回时最坏的一刀是把整排一起删）。
        assertTrue("页码读数不在了", post.any { it.contains("GalleryViewerPageCounter(") })
    }

    /** ③ 悬浮底栏两档都吃色板，浅色档不再写死近白。 */
    @Test
    fun `悬浮底栏不再写死浅色`() {
        val bar = code("com/venera/compose/components", "VeneraFloatingNavBar.kt")
        assertTrue("底栏又出现写死的颜色字面量", bar.none { it.contains("Color(0x") })
        // 对照：它确实还在取色板那枚 surface（读空了上一条恒真）。
        assertTrue(
            "底栏不再吃色板槽位",
            bar.any { it.contains("MiuixTheme.colorScheme.surface.copy(") },
        )
    }
}
