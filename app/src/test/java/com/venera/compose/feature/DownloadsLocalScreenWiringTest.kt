package com.venera.compose.feature

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「下载与本地」屏的接线测试，两处都属于改回去也不报错不崩的形态：
 * ① 底栏让位 —— 底栏是 overlay（`Navigation.kt` 里不进 Scaffold 的 bottomBar slot，
 *    innerPadding 只含系统 navigationBars），页面必须自己吃 `LocalBottomBarClearance`；
 * ② 批量选择 —— 三段（队列/漫画/图片）都进得了多选。
 *
 * 口径与 `TokenBudgetFrozenTest` 同一把：注释行不参与匹配。
 * 锚点查找函数叫 [rowOf]，不叫 indexOf —— 同签名同名的话赢的是 stdlib 那一枚
 * （按整行相等比，找不到静默返回 -1，判据全数假失败）。
 */
class DownloadsLocalScreenWiringTest {

    private fun codeLines(): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles("com/venera/compose/feature")
            .firstOrNull { it.name == "DownloadsLocalScreen.kt" }
            ?: error("找不到 DownloadsLocalScreen.kt（尝试过的根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    private fun rowOf(lines: List<IndexedValue<String>>, needle: String): Int =
        lines.indexOfFirst { it.value.contains(needle) }

    /** ① 三段列表的底部留白都吃让位契约，且调用点把真源接进来。 */
    @Test
    fun `三段列表底部都吃底栏让位契约`() {
        val lines = codeLines()
        assertEquals(
            "三段（队列 / 漫画 / 图片）的底部留白必须都写成 bottomClearance + space8，" +
                "少一段就是那一段的尾巴被底栏压住：" +
                lines.filter { it.value.contains("bottom = bottomClearance") }.map { it.index + 1 to it.value.trim() },
            3,
            lines.count { it.value.contains("bottom = bottomClearance + VeneraTokens.spacing.space8") },
        )
        assertEquals(
            "三个调用点都要把 LocalBottomBarClearance.current 递给 pane（真源在 provider 处，页面不许自算档位）",
            3,
            lines.count { it.value.contains("bottomClearance = LocalBottomBarClearance.current") },
        )
        assertTrue(
            "列表底部不许再拿 space11 这类魔法数顶替底栏让位 —— 悬浮档 76dp、贴底档 54dp，" +
                "32dp 两档都不够，主人 08 那条「清空已完成被挡住」就是这么来的",
            lines.none { it.value.contains("bottom = VeneraTokens.spacing.space11") },
        )
    }

    /** ② 三段都进得了多选：入口不按段排除，条吃全项目那一条。 */
    @Test
    fun `队列段也在多选范围内`() {
        val lines = codeLines()
        assertTrue(
            "顶栏的「选择」入口不许再写 `segment != QUEUE` 这类按段排除（一百多笔任务逐格点不是批量选择）",
            lines.none { it.value.contains("segment != DownloadsLocalSegment.QUEUE") },
        )
        assertEquals(
            "三段调用点都要把 multiSelect 接进自己的 pane",
            3,
            lines.count { it.value.contains("multiSelect = isMultiSelect") },
        )
        assertTrue(
            "队列段必须有「长按进多选」这一档出口（与全应用长按=多选同语义）",
            rowOf(lines, "onEnterSelect = { task ->") >= 0,
        )
        assertTrue(
            "多选条必须吃 components/selection 那一条 VeneraMultiSelectBar（带全选与反选），" +
                "不许在页内再造一条形状",
            rowOf(lines, "VeneraMultiSelectBar(") >= 0,
        )
        assertTrue(
            "多选条浮在底边时也要让开底栏：它的 bottom 落在 space8 + LocalBottomBarClearance 那一档",
            rowOf(lines, "bottom = tokens.spacing.space8 + LocalBottomBarClearance.current") >= 0,
        )
    }

    /** ③ 勾选框吃全项目那一枚，页内不自画。 */
    @Test
    fun `三段勾选框同一枚`() {
        val lines = codeLines()
        assertEquals(
            "VeneraSelectBox 在这屏恰好三道（队列卡 / 漫画行 / 图片格），多出来就是有人在页内自画勾选框",
            3,
            lines.count { it.value.contains("VeneraSelectBox(") },
        )
    }

    /** ④ 整段清理挂顶栏，不再躺在列表尾巴上。 */
    @Test
    fun `整段清理挂顶栏不挂列表尾巴`() {
        val lines = codeLines()
        assertTrue(
            "队列段的「清空已完成」不再当列表尾巴那一行 —— 它永远要滚到最底才够得着",
            lines.none { it.value.contains("清空已完成记录") },
        )
        assertTrue(
            "图片段同一条：尾巴那行「清空已完成与失败的记录」已换到顶栏",
            lines.none { it.value.contains("清空已完成与失败的记录") },
        )
        assertTrue(
            "顶栏两枚清理动作要在数上说话（清理已完成 N / 清理记录 N），不许摆一枚空的",
            rowOf(lines, "completedRecordCount > 0") >= 0 && rowOf(lines, "galleryClearableCount > 0") >= 0,
        )
        assertTrue(
            "多选条上的撤掉是破坏性动作，必须有确认框 keyed 住",
            rowOf(lines, "showRemoveTasksConfirm = true") >= 0,
        )
    }
}
