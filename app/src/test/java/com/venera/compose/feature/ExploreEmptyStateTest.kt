package com.venera.compose.feature

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 探索页空态的判据：空态要给出下一步出路（换一种方式 / 换一颗源），
 * 而不是「该探索方式暂无内容」这类死话。
 * 分类区在空态上方常驻（探索 = 浏览，分类是主干道），
 * 不摆「挑一个分类」这类假入口。
 *
 * 两条判据各防一种退化：
 *  ① 空态正文不再是死话，且出路都在；
 *  ② 出路的显隐跟着"那一条路真的存在"—— 摆一枚切不动当前模式的按钮
 *     就是假开关（UiAuditBatch7Test ① 那一族）。
 */
class ExploreEmptyStateTest {

    private fun code(dir: String, name: String): List<String> =
        RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private val explore = code("com/venera/compose/feature/explore", "UnifiedExploreScreen.kt")

    /** ① 那句死话撤了，出路都在。 */
    @Test
    fun `空态给出出路而不是一句暂无内容`() {
        assertTrue(
            "「该探索方式暂无内容」那句死话还在 —— 用户读完不知道下一步该干什么",
            explore.none { it.contains("VeneraEmptyView(message = \"该探索方式暂无内容\")") },
        )
        assertTrue("空态少了「换一种方式」那一枚", explore.any { it.contains("text = \"换一种方式\"") })
        assertTrue("空态少了「换一颗源」那一枚", explore.any { it.contains("text = \"换一颗源\"") })
        // 分类区常驻后「挑一个分类」芯片不许回来：分类就在空态上面摊着，
        // 再摆一枚按钮就是假入口。
        assertFalse(
            "「挑一个分类」芯片回来了 —— 分类区常驻摊着，那枚按钮是假入口",
            explore.any { it.contains("text = \"挑一个分类\"") },
        )
        // 空态标题要说清"是这一格空的"，不是"app 坏了"
        assertTrue("空态标题没换", explore.any { it.contains("title = \"这一格现在没有内容\"") })
        // 筛选用空态要点名是哪个标签筛空的 —— 那是用户刚点的东西，不说就是他不知道。
        // （那一串是 Kotlin 字符串模板，所以这里写成拼接而不是直接复制带 $ 的原文。）
        assertTrue(
            "筛选用空态没点名是哪个标签（用户刚点的那个）",
            explore.any {
                it.contains("」在这个源里没有匹配的作品") && it.contains("activeFilter.label")
            },
        )
    }

    /**
     * ② 出路的显隐跟着"那一条路真的存在"。
     *
     * 认整串表达式而不是标识符：只认名字的话，判式里写着那个名字也会假通过。
     */
    @Test
    fun `出路的显隐各自被那一条路的判式门控`() {
        assertTrue(
            "「换一种方式」没被 exploreModeRowVisible 门控 —— 只有一枚模式时摆出来就是假开关",
            explore.any { it.contains("if (exploreModeRowVisible(pageModes)) {") },
        )
        assertTrue(
            "「换一颗源」没被 explorations.size > 1 门控 —— 只有一颗源时摆出来就是假开关",
            explore.any { it.contains("if (explorations.size > 1) {") },
        )
    }
}
