package com.venera.compose.feature

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-14 UI 审查第四批的尺子：横向容器的溢出证据、探索页的空节头、搜索页的两行灰字。
 *
 * 三条各防一种"静默"：
 * ① 渐隐与尾部内边距**只接一半** —— 屏上看着修好了，滑到底末枚仍然压在渐隐底下
 *   （把"被裁"从中途挪到终点），不报错、不崩，只在真机上露出来；
 * ② 渐隐的两端点写成默认 `startX = endX = 0` —— 那是退化的线性渐变，读起来就是一块实色硬边；
 * ③ 节头管着空内容 —— 屏上多一个标题，用户读到的不是"这里有一组筛选"而是"还没加载完"。
 */
class UiAuditBatch4Test {

    private fun code(dir: String, name: String): List<String> =
        RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private fun text(dir: String, name: String): String =
        RepoSources.kotlinFiles(dir).first { it.name == name }.readText()

    private val fade = code("com/venera/compose/components/venera", "VeneraHScrollFade.kt")

    /** ① 三处横向行：渐隐与尾部内边距**成对**出现。 */
    @Test
    fun `横向行的渐隐与尾部内边距成对`() {
        val sites = listOf(
            "com/venera/compose/feature" to "SearchScreen.kt",
            "com/venera/compose/feature" to "FavoritesScreen.kt",
            "com/venera/compose/feature" to "ComicDetailScreen.kt",
        )
        sites.forEach { (dir, name) ->
            val lines = code(dir, name)
            val fades = lines.count { it.contains("hScrollEndFade(container =") }
            val pads = lines.count { it.contains("hScrollEndContentPadding()") }
            assertTrue("$name 一处渐隐都没有", fades >= 1)
            assertEquals(
                "$name 的渐隐与尾部内边距不成对 —— 只接一半等于把『被裁』从中途挪到终点",
                fades, pads,
            )
        }
    }

    /** ② `container` 必填：给默认色会让新调用点静默长成一道错色硬边。 */
    @Test
    fun `渐隐的底色列没有默认值`() {
        assertTrue(
            "container 又带回默认值了",
            fade.any { it == "container: Color," },
        )
        assertFalse("签名里 container 带上了 = ", fade.any { it.startsWith("container: Color =") })
    }

    /** ③ 端点写在节点坐标里（画刷不跟着 topLeft 走）。 */
    @Test
    fun `渐隐两端点写在节点坐标里`() {
        assertTrue("缺 startX", fade.any { it == "startX = size.width - w," })
        assertTrue("缺 endX", fade.any { it == "endX = size.width," })
        assertTrue("带宽要钳进视口，否则窄屏上端点会跑到左边去", fade.any { it.contains("width.toPx().coerceAtMost(size.width)") })
    }

    /** ④ 探索页：不给任何内容的标题一行都不许留（真机读数是一屏四行标题、下面全是空的）。 */
    @Test
    fun `探索页空区块与套层标题一起撤`() {
        val explore = code("com/venera/compose/feature/explore", "UnifiedExploreScreen.kt")
        // ① 那枚总标题：它管的两块各自带题，再套一层就是两行标题压一行药丸。
        assertFalse("「快捷筛选」那枚总标题又回来了", explore.any { it.contains("SectionHeader(\"快捷筛选\")") })
        // ② 零入口的组在**源头**剔掉 —— 这样 `visibleSections` 与 `hiddenEntryCount` 共用一把尺子，
        //    不会出现"折叠计数说还有 12 个入口，屏上却一行都展不出"那种两本账。
        assertTrue(
            "空组没在源头剔掉（屏上会留一行光杆标题）",
            explore.any { it.contains("nativeSections.orEmpty().filter { it.items.isNotEmpty() }") },
        )
        // ③ 榜档那一组同一条判据（它不吃组预算，也就没人替它判空）。
        assertTrue(
            "榜档为空时仍会摆出一枚「排行榜」节头",
            explore.any { it.contains("src.rankingSection?.takeIf { it.items.isNotEmpty() }?.let") },
        )
        // ④ 「内容」那枚节头跟着内容走：加载中 / 失败 / 空 三档下面各自会念一句话。
        assertTrue(
            "内容节头又回到恒摆了（真机读数：一行「内容」压着一句「该探索方式暂无内容」）",
            explore.any { it.contains("if (!isLoadingContent && contentError == null && !content.isEmpty) {") },
        )
        // 对照：「本源分类」那枚节头的判据还在，而且判的是**非空**而不是"存在"。
        assertTrue(
            "「本源分类」那枚节头的判据被改坏了",
            explore.any { it.startsWith("if (visibleSections.isNotEmpty() || src.rankingSection?.items?.isNotEmpty() == true) {") },
        )
    }

    /** ⑤ 搜索页那句两行技术解释走一行档，且不再自己排两行。 */
    @Test
    fun `搜索页那句长提示收成一行可展开`() {
        val search = text("com/venera/compose/feature", "SearchScreen.kt")
        val start = search.indexOf("private fun TagFilterRelaxedNotice()")
        assertTrue("找不到那颗提示", start > 0)
        val body = search.substring(start).substringBefore("\n}")
        assertTrue("没走 VeneraNotice：$body", body.contains("VeneraNotice("))
        assertTrue("没走一行档：$body", body.contains("compact = true"))
        assertFalse("又自己排两行（lineHeight 那一套）：$body", body.contains("lineHeight"))
        // 对照：措辞一个字没删 —— 这一条存在的理由恰恰是它必须能被读到。
        assertTrue("『多数源不带完整标签』那半句被吞了", body.contains("多数源的列表项不带完整标签"))
        assertTrue("『繁简不匹配』那半句被吞了", body.contains("繁简写法不同也判为不匹配"))
    }
}
