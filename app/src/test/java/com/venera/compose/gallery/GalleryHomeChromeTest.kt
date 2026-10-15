package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画廊首页那一格 chrome 的形状（2026-10-15 改口径）。
 *
 * 用户两条：① 「把首页默认显示的源站去掉，只在搜索框展开时显示」；
 * ② 点开搜索那一下要有容器变化的过渡 —— 病是入口条那一行原先**整块从内容里消失**
 * （墙硬跳 ~136dp），而卡片是另一层浮长开的，两处不同步 ⇒ 读起来像换了一块东西。
 *
 * 拍板：入口条**常驻不撤**，卡片从它身上原地长开把它盖住（全程墙不跳），
 * 首页那一排来源分段器摘掉 —— 卡片里那一排本来就有，且吃同一份 `GallerySearchSource.options`。
 *
 * 这四条钉的是改完之后不许悄悄退回去的形状：
 *  - 首页那一块里没有分段器（负向扫描，一次列全违例）；
 *  - 但卡片里那一排必须在（防"两处一起删"这种过头修法）；
 *  - 那一行不再被 `svm.active` 摘走；
 *  - 高度常量与实际摆件逐项对应（这条耦合从前只靠注释里的纪律，没有尺子）。
 */
class GalleryHomeChromeTest {

    private val uiDir = "com/venera/compose/gallery/ui"

    private fun file(name: String): List<String> =
        RepoSources.kotlinFiles(uiDir).first { it.name == name }.readLines()

    private fun lineHits(lines: List<String>, needle: String): List<Int> =
        lines.indices.filter { lines[it].contains(needle) }.map { it + 1 }

    /**
     * 只看**代码行**。
     *
     * 这几条钉的是"渲染了什么"，而头注里要写明"从前渲染过、现在搬去哪了" —— 那种句子
     * 一定会点名组件。把注释也算命中，尺子就会去惩罚写清楚的人，下一批索性把注释删掉，
     * 判据反而更薄。注释行 = 去掉首尾空白后以双斜线、星号、或块注释起始那两字符开头的那些
     * （这块头注里就不写那两个字符了 —— 本仓踩过：KDoc 里出现块注释起始符会被当成嵌套注释
     * 的开头，把后面的真代码整段吞掉，报错现场还在文件尾）。
     */
    private fun codeLineHits(lines: List<String>, needle: String): List<Int> =
        lineHits(lines, needle).filter { n ->
            val t = lines[n - 1].trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }

    @Test fun `首页那一块不渲染来源分段器`() {
        val lines = file("GalleryHomeChrome.kt")
        val hits = codeLineHits(lines, "VeneraSegmentedButton")
        assertTrue(
            "首页那一排的源站要摘掉（只在展开的搜索卡里摆）。命中行：\n" +
                hits.joinToString("\n") { "  第 $it 行：" + lines[it - 1].trim().take(72) } +
                "\n注意 import 行也算命中 —— 摘件要把 import 一起摘干净。",
            hits.isEmpty(),
        )
        // 量具自证：这颗文件得真的有代码行可读，否则上一条是假绿
        assertTrue(
            "GalleryHomeChrome.kt 读不到代码行，量具失效",
            lines.any { it.trim().startsWith("internal fun GalleryHomeChrome(") },
        )
    }

    @Test fun `搜索卡里那一排源站照旧在`() {
        // 这条是上一颗的配偶：只许把"首页"那一排摘掉，不许把唯一的那一排也删了
        // —— 那样换源就没有任何入口了，而这条编译器与运行时都看不见。
        val lines = file("GallerySearchArea.kt")
        val hits = lineHits(lines, "VeneraSegmentedButton(")
        assertEquals(
            "搜索卡里应当恰好一处来源分段器（`GallerySearchSource.options` 的唯一落点）",
            1,
            hits.size,
        )
    }

    @Test fun `入口条那一行不再被搜索态摘走`() {
        val lines = file("GalleryScreen.kt")
        val gates = lineHits(lines, "chrome = if (!svm.active)")
        assertTrue(
            "常驻 chrome 必须恒传给墙：把它在搜索开着时摘走，就是那一下硬跳的成因。命中行：$gates",
            gates.isEmpty(),
        )
        // 量具自证：`chrome =` 这个传参处得在场，否则上一条是假绿
        assertTrue(
            "GalleryScreen 里找不到 chrome 传参处，量具失效",
            lineHits(lines, "chrome =").isNotEmpty(),
        )
    }

    @Test fun `搜索卡的进出场动画挂在能被观察到的状态变化上`() {
        // 2026-10-15 第二笔：用户回「还是没有容器变化动画」。真根因不在动画定义 ——
        // `AnimatedVisibility` 从前被 `if (svm.active) { … }` 包着：开搜索那一刻它是**新组合进来的**，
        // 内部状态初值直接等于目标值，没有 false→true 那一跳 ⇒ enter 一个字都不播。
        // 钉两条：visible 自己吃 svm.active；且 `AnimatedVisibility(` 不许再紧跟在 `if (svm.active)` 之后。
        val lines = file("GalleryScreen.kt")
        val cardGate = lineHits(lines, "visible = svm.active && !searchCollapsed")
        val scrimGate = lineHits(lines, "visible = svm.active && svm.mode == GallerySearchMode.INPUT")
        assertTrue("搜索卡的 AnimatedVisibility 没在吃 svm.active（找到 $cardGate 处）", cardGate.isNotEmpty())
        assertTrue("scrim 的 AnimatedVisibility 没在吃 svm.active（找到 $scrimGate 处）", scrimGate.isNotEmpty())
        val offenders = codeLineHits(lines, "if (svm.active) {").filter { n ->
            lines.drop(n).take(3).any { it.contains("AnimatedVisibility(") }
        }
        assertTrue(
            "又有 AnimatedVisibility 被包进 if (svm.active) 了（那样首次展开不播动画）：$offenders",
            offenders.isEmpty(),
        )
    }

    @Test fun `高度常量与那一行实际摆的件逐项对应`() {
        val lines = file("GalleryHomeChrome.kt")
        val compStart = lines.indexOfFirst { it.contains("internal fun GalleryHomeChrome(") }
        val heightStart = lines.indexOfFirst { it.contains("fun galleryHomeChromeHeight") }
        assertTrue(
            "量具找不到 GalleryHomeChrome / galleryHomeChromeHeight 两处锚点（$compStart, $heightStart）",
            compStart >= 0 && heightStart > compStart,
        )
        val body = lines.drop(heightStart + 1).take(heightStart + 40)
        val end = body.indexOfFirst { it.trim() == "}" }
        assertTrue("galleryHomeChromeHeight 的函数体收不到边界（量具失效）", end >= 0)
        val used = Regex("""tokens\.spacing\.([A-Za-z0-9]+)""")
            .findAll(body.take(end).joinToString("\n"))
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(
            "高度常量只许加「内缩 + 一条定高条 + 下内缩」这三项；再出现 segmentedHeight 那一族 " +
                "就说明有人在首页补了第二行而没改这条常量（收栏阈值会跟着算少）",
            setOf("space2", "dockedSearchBarHeight", "space3"),
            used,
        )
        // 组件侧配偶：那一格里不该再有第二种"行间距"件
        val component = lines.subList(compStart, heightStart).filter {
            val t = it.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }.joinToString("\n")
        assertTrue(
            "首页那一块里不该再有 Spacer —— 它就是这个高度常量漏项的形态",
            !component.contains("Spacer("),
        )
    }
}
