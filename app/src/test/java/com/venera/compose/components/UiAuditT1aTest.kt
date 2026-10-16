package com.venera.compose.components

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-15 问题清单 T1a 第一颗（统计页）的尺子。
 *
 * 立的形状跟着清单 §二-4 那条根因判据走：**一屏一个收口点**。统计页三块内容都住在同一棵
 * `LazyColumn` 里，所以只在列表那一处挂 `widthIn` 就够；四个 `item` 各套一次会得到
 * "同一屏里两档宽度"（有一处忘了套、或内边距被算两遍），那是本仓最忌的那种分叉。
 *
 * 于是这里两把尺子各防一种滑坡：
 *  - ① 数这一颗文件里的收口**次数恒为一处**（多点 = 回到逐 item 打补丁）；
 *  - ② 全仓不许把收口宽度**抄成字面数**（`widthIn(max = 720.dp)` 那种）—— 抄一份就少一处跟着
 *    `contentMaxWidthFor` 改，而"改的时候漏改三处也不报错"正是 G3 那条同一族病。
 */
class UiAuditT1aTest {

    private fun codeText(dir: String, name: String): String =
        RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
            .joinToString("\n")

    /**
     * 已接线的每一颗页面级列表：收口点**恒为一处**、直接挂在满高之后、且收宽后居中。
     *
     * 名单加一颗就要在这里加一颗（G3 那把"只许加长"的尺子读的是文件名单，读不出"某一颗屏套了三遍"）。
     */
    @Test
    fun `每一颗屏只在页面级列表那一处收口`() {
        val wired = listOf(
            "com/venera/compose/feature/StatsScreen.kt" to "StatsScreen.kt",
            "com/venera/compose/feature/sourcemanage/ComicSourceScreen.kt" to "ComicSourceScreen.kt",
            // 这一颗带网格列数：收口必须挂在 `listWidthModifier` **之前**（列数吃实测宽，见源文件那段注释），
            // 所以它多一道自己的锚点要钉。
            "com/venera/compose/feature/explore/SourceSectionScreen.kt" to "SourceSectionScreen.kt",
        )
        wired.forEach { (rel, name) ->
            val stats = codeText(rel.substringBeforeLast('/'), name)
            assertEquals(
                "$name 的收口点数变了：这一页只该在页面级列表那一处挂（逐 item 套会得到两档宽度）",
                1,
                Regex("""contentMaxWidthFor\(""").findAll(stats).count(),
            )
            // 收宽之后必须居中，否则 720 那一档整页贴左缘、右边空出一屏。
            assertEquals("$name 收口之后没居中", 1, Regex("""\.align\(Alignment\.TopCenter\)""").findAll(stats).count())
            // 收口那一道要**直接挂在列表的满高之后**（不是挂在某个 item 里、也不是挂在 Box 上）。
            // 钉相邻两行的形状，不钉"谁在前面"那种 indexOf 比较 —— 前者咬得住"把 cap 挪进 item"，
            // 后者会被文件里别处的 `.fillMaxSize()` 喂饱。
            assertEquals(
                "$name 的 widthIn 没挂在列表自己的 modifier 上",
                1,
                Regex(
                    """\.fillMaxSize\(\)\n[ \t]*\.then\(contentMaxWidth\?\.let \{ Modifier\.widthIn\(max = it\) \} \?: Modifier\)""",
                ).findAll(stats).count(),
            )
        }
        // 带网格那一颗额外钉**顺序**（列数吃 `onSizeChanged` 的实测宽：收口必须排在量宽之前，
        // 排在后面就等于列数仍按窗口宽算，收口反而把卡片压小）。
        val source = codeText("com/venera/compose/feature/explore", "SourceSectionScreen.kt")
        val capAt = source.indexOf("Modifier.widthIn(max = it)")
        val measureAt = source.indexOf(".then(listWidthModifier)")
        assertTrue("收口那一道没找到", capAt >= 0)
        assertTrue(
            "收口必须排在 listWidthModifier 之前（否则列数仍按窗口宽算，卡片会被压小）",
            capAt in 0 until measureAt,
        )
    }

    @Test
    fun `收口宽度不许抄成字面数`() {
        val offenders = RepoSources.allMainLines()
            .filter { (_, _, line) ->
                val t = line.trim()
                !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")
            }
            .filter { (_, _, line) -> Regex("""widthIn\(max = \d+\.dp""").containsMatchIn(line) }
            .map { (rel, no) -> "$rel:$no" }
        // 现量：这条判式在今天的树上必须是"能跑到、且读得出 0"的状态 —— 对照组见下面那条。
        assertEquals("这些处把收口宽度抄成了字面数，改 helper 时不会跟着变：$offenders", emptyList<String>(), offenders)
        // 对照组：判式不是恒真 —— 把边界那串自己喂一遍，它得抓得住。
        assertTrue(
            "判式坏了（连样例都不咬，这条尺等于没有）",
            Regex("""widthIn\(max = \d+\.dp""").containsMatchIn("Modifier.widthIn(max = 720.dp)"),
        )
    }
}
