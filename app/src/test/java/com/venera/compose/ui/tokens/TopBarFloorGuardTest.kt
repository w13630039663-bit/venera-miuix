package com.venera.compose.ui.tokens

import androidx.compose.ui.unit.dp
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 顶栏地板 `topBarFloor` 的唯一性与**冻结白名单只许缩短**。
 *
 * 为什么需要扫源码这一层：`statusBarTop + 104.dp` 这个表达式今天散在 18 处 / 14 颗文件里，
 * 编译器管不了"新页面又抄了一遍"，运行时也管不了 —— 而大屏批次 2 正在改 chrome 几何，
 * 一旦顶栏改高就是"14 颗页逐页找"，其中还会有人把附加高混进同一条相加链
 * （`DownloadScreen` 的 `104 + 92`、`SourceSectionScreen` 的 `104 + chipsHeight` 已经是这个形状）。
 *
 * 本轮只收非冻结的 11 处：`FREEZE-STATEMENT.md:18` 允许"修实际 Bug、修明确回归"，
 * 禁止"无明确需求的架构重构 / 顺手拆文件"，而下面这 7 处今天没有可指认的故障。
 * 它们留在原地，但**钉在名单里**：谁再新增一处裸 `104.dp`，这条用例就红。
 */
class TopBarFloorGuardTest {

    @Test
    fun `地板值就是各页今天用的那个数，一分未改`() {
        assertEquals(104.dp, VeneraSpacing.topBarFloor)
    }

    @Test
    fun `非冻结页面不许再抄地板字面量`() {
        val frozenWhitelist = setOf(
            "com/venera/compose/feature/HomeScreen.kt",
            "com/venera/compose/feature/HistoryScreen.kt",
            "com/venera/compose/feature/FavoritesScreen.kt",
            "com/venera/compose/feature/SearchScreen.kt",
            "com/venera/compose/feature/explore/UnifiedExploreScreen.kt",
            "com/venera/compose/feature/explore/SourceSectionScreen.kt",
        )
        val hits = RepoSources.allMainLines().filter { (rel, _, line) ->
            val t = line.trim()
            line.contains("statusBarTop + 104.dp") &&
                // 注释里引这条口径的文字（本文档口径说明、GalleryScreen 的对照说明）不算抄写。
                !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") &&
                rel !in frozenWhitelist
        }
        assertEquals(
            "地板又被抄成字面量，应改读 VeneraSpacing.topBarFloor / tokens.spacing.topBarFloor：\n" +
                hits.joinToString("\n") { "${it.first}:${it.second} ${it.third.trim()}" },
            emptyList<Triple<String, Int, String>>(),
            hits,
        )
    }

    @Test
    fun `冻结白名单只许缩短不许变长`() {
        val expected = mapOf(
            "com/venera/compose/feature/HomeScreen.kt" to 1,
            "com/venera/compose/feature/HistoryScreen.kt" to 2,
            "com/venera/compose/feature/FavoritesScreen.kt" to 1,
            "com/venera/compose/feature/SearchScreen.kt" to 1,
            "com/venera/compose/feature/explore/UnifiedExploreScreen.kt" to 1,
            "com/venera/compose/feature/explore/SourceSectionScreen.kt" to 1,
        )
        val actual = RepoSources.allMainLines()
            .filter { (_, _, line) ->
                val t = line.trim()
                line.contains("statusBarTop + 104.dp") && !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")
            }
            .groupingBy { it.first }
            .eachCount()
        // 冻结文件解冻或被顺手改掉时，这里会少一项 —— 那是**好消息**，但必须有人显式确认，
        // 所以断言写成集合相等而不是"实际 ⊆ 预期"。
        assertEquals(expected, actual)
    }
}
