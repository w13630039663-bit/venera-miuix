package com.venera.compose.ui.tokens

import androidx.compose.ui.unit.dp
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 顶栏地板 `topBarFloor` 的唯一性与**冻结白名单只许缩短**。
 *
 * 为什么需要扫源码这一层：`statusBarTop + 104.dp` 这个表达式曾经散在 18 处 / 14 颗文件里，
 * 编译器管不了"新页面又抄了一遍"，运行时也管不了 —— 而大屏批次 2 正在改 chrome 几何，
 * 一旦顶栏改高就是"14 颗页逐页找"，其中还会有人把附加高混进同一条相加链
 * （`DownloadScreen` 的 `104 + 92`、`SourceSectionScreen` 的 `104 + chipsHeight` 已经是这个形状）。
 *
 * 大屏那轮先收了 11 处、把剩下 7 处钉在冻结白名单里（当时没有可指认的故障，不动架构）。
 * UI 审查整改批 6（2026-10-14）把最后 7 处也改吃 token，名单清空 —— 本文剩下的职责是**防新增**：
 * 谁再抄一遍字面量，或往名单里加一项，两条用例都会红。
 */
class TopBarFloorGuardTest {

    @Test
    fun `地板值就是各页今天用的那个数，一分未改`() {
        assertEquals(104.dp, VeneraSpacing.topBarFloor)
    }

    @Test
    fun `非冻结页面不许再抄地板字面量`() {
        // 冻结白名单今天为空：UI 审查整改批 6（2026-10-14，`docs/rounds/ui-fixes-2026-10-14.md`）
        // 把最后 7 处逐页抄写的 `statusBarTop + 104.dp` 全部改吃 `tokens.spacing.topBarFloor`。
        // 这一条现在管的是**新增**：任何页面再抄一遍字面量都会红在这里。
        val frozenWhitelist = emptySet<String>()
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
        // 名单已空（批 6 解冻全部 7 处）。写成集合相等而不是"实际 ⊆ 预期"的原意不变：
        // 谁再往名单里加一项、或新抄一处字面量，这里都会红 —— 而"名单为空"本身就是这条要守的状态。
        val expected = emptyMap<String, Int>()
        val actual = RepoSources.allMainLines()
            .filter { (_, _, line) ->
                val t = line.trim()
                line.contains("statusBarTop + 104.dp") && !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")
            }
            .groupingBy { it.first }
            .eachCount()
        assertEquals(expected, actual)
    }
}
