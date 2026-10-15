package com.venera.compose.feature

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-14 探索页**空态给下一步**的判据。
 *
 * 病症（真机读数）：禁漫天堂那一屏读完是一句「该探索方式暂无内容」——
 * 一句把责任推给用户的死话。用户读完不知道该改什么，能做的只有退出这一页。
 * 而这一屏有出路都在手边：换个探索方式、换一颗源。
 *
 * 分类那条出路**不是一枚按钮**：分类区在空态上面常驻摊着（探索 = 浏览，分类是主干道），
 * 再摆一枚「挑一个分类」的按钮就是假入口 —— 2026-10-14 折叠那一轮把这枚按钮折出来过，
 * 用户原话"需要手动点击才显示不好"，分类随后改回常驻，这枚芯片随之删除。
 *
 * 两条判据各防一种退化：
 *  ① 空态正文不再是那句死话，且**出路口袋里那几枚都在**；
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
        // 分类区常驻后「挑一个分类」那枚芯片**不许回来**：分类就在空态上面摊着，
        // 再摆一枚按钮就是假入口（折叠那一轮的教训）。
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
     * 认整串表达式而不是标识符：本仓栽过"判式里写着那个名字、喂饱了自己"。
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
