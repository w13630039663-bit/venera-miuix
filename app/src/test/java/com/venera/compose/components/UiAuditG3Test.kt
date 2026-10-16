package com.venera.compose.components

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-15 问题清单 G3 批的尺子：**节头的动作贴标题** + **收口只给非墙块**。
 *
 * 两条的形状不同，判据的写法就不同：
 * ① 是共用件（`feature/SectionHeader.kt`）里一档通吃 —— 钉的是"两种行尾必须两种排法"，
 *    恒 `SpaceBetween` 那一整行不许再出现（现量：平板上「正在关注的画师」与「另有 2 位」
 *    隔 **1642px ≈ 621dp**，手机上同一排也隔 **220~255dp**，见 `_qa/ui-verify-2026-10-15/section_header_gap.txt`）；
 * ② 是接线（`contentMaxWidthFor`）—— 接线类按仓内纪律**数道数**，同时给墙那一族立**负向名单**：
 *    清单 §一-4 那句"墙本体不限宽"没有尺子守着的话，下一轮顺手把墙也收了口，
 *    症状会从"一行读不完"变成"节头比墙窄、左缘对不齐"，是拿一个新错位换一个旧错位。
 *
 * ⚠️ 手机档 `Compact` 恒 `null` 那一钉**这里不重复立** —— 已有 `WideScreenPolicyTest.phoneBandNeverGetsAContentWidthCap`，
 * 同一件事只许一把尺子（这条纪律本仓红过五次）。
 */
class UiAuditG3Test {

    private fun codeText(dir: String, name: String): String =
        RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
            .joinToString("\n")

    /** ① 行尾是真按钮时贴标题；只有「›」那一支才贴屏幕右缘。 */
    @Test
    fun `节头的两种行尾各有各的排法`() {
        val header = codeText("com/venera/compose/feature", "SectionHeader.kt")
        assertEquals(
            "行尾有真按钮时不许再用 SpaceBetween（标题与动作被拉到屏幕两头）",
            0,
            Regex("""horizontalArrangement = Arrangement\.SpaceBetween""").findAll(header).count(),
        )
        assertEquals(
            "两档排法必须显式分叉，且动作那档吃现成的 space4",
            1,
            Regex(
                """horizontalArrangement = if \(trailing == null\) Arrangement\.SpaceBetween """ +
                    """else Arrangement\.spacedBy\(tokens\.spacing\.space4\)""",
            ).findAll(header).count(),
        )
    }

    /** ② 首页三节各自表态：两节收口、墙那一节不收。 */
    @Test
    fun `首页三节对收口必须逐个表态`() {
        val sections = codeText("com/venera/compose/gallery/ui", "GalleryHomeSections.kt")
        // 必填参数：三处调用点各写一次，谁忘了就编译不过（所以这里只数次数，不改判成"至少一处"）。
        assertEquals("`wallAdjacent = false` 应恰好两节（画师行 + 每日热门预览行）", 2, Regex("""wallAdjacent = false""").findAll(sections).count())
        assertEquals("`wallAdjacent = true` 应恰好一节（猜你喜欢 = 脚下那面墙）", 1, Regex("""wallAdjacent = true""").findAll(sections).count())
        // 对照：外壳真的去吃现成口径，而不是自己写一个数。
        assertEquals(
            1,
            Regex("""contentMaxWidthFor\(wideScreenLayoutMode\(LocalConfiguration\.current\.screenWidthDp\.dp\)\)""")
                .findAll(sections).count(),
        )
        assertTrue("收口之后要居中，否则 720 那一档会贴左缘空出右半屏", sections.contains("contentAlignment = Alignment.TopCenter"))
    }

    /** ③ 墙那一族恒不被收口（负向名单）+ 接线道数只许加长。 */
    @Test
    fun `墙本体恒不参与宽度收口`() {
        val walls = listOf(
            "GalleryScreen.kt",          // 首页搜索墙 + 日榜二级页墙 + 共用件 GalleryCardsGrid
            "GalleryForYouScreen.kt",    // 首页主墙（猜你喜欢）
            "GalleryFavoritesBody.kt",   // 画廊收藏墙
            "GalleryHistoryScreen.kt",   // 画廊历史墙
            "GalleryReverseResults.kt",  // 反搜结果墙
        )
        val offenders = walls.filter { name ->
            codeText("com/venera/compose/gallery/ui", name).contains("contentMaxWidthFor")
        }
        assertTrue("这几面墙被套上了收口（清单 §一-4：墙本体不限宽）：$offenders", offenders.isEmpty())
        // 正向对照：**名单内的每一颗都还得在**（这才是"只许加长"的真形状 —— 用 assertEquals 冻住整张表
        // 会让"合法新增一颗屏"变红，那种红会把人教成"别加尺子"，而不是"加完来登记"）。
        // 新增接线照常欢迎，但要在这里补一行并按 T1a 那把尺子数一遍道数。
        val consumers = RepoSources.allMainLines()
            .filter { !it.first.endsWith("components/WideScreenPolicy.kt") }
            .filter { it.third.contains("contentMaxWidthFor(") }
            .map { it.first }
            .distinct()
        listOf(
            "com/venera/compose/feature/settings/SettingsComponents.kt",
            "com/venera/compose/feature/settings/SettingsHome.kt",
            "com/venera/compose/gallery/ui/GalleryArtistProfileScreen.kt",
            "com/venera/compose/gallery/ui/GalleryHomeSections.kt",
        ).forEach { missing ->
            assertTrue("这一处收口接线没了：$missing（掉一处 = 那一屏重新变成满宽拉伸）", consumers.contains(missing))
        }
    }
}
