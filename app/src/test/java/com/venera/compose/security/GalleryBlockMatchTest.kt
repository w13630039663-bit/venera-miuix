package com.venera.compose.security

import com.venera.compose.security.guard.GalleryBlockMatch
import com.venera.compose.security.guard.GuardRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画廊黑名单判据的行为锁。
 *
 * 起因是真机反馈（2026-09-25）：yande.re 只上屏 13 张、Danbooru 一张都没有。
 * 拉设备库一看，用户开着一条关键字 `ai`（非正则）—— 子串匹配打在 `long_hair`、`tail`
 * 这类下划线标识符上，实测把 Danbooru 当天日榜 **200 条挡掉 199 条**。
 * 这几条测试钉的就是"该挡的还挡住、不该挡的别顺手清站"这条分界。
 */
class GalleryBlockMatchTest {

    private fun rule(type: String, pattern: String, regex: Boolean = false) =
        GuardRule(id = 1, type = type, pattern = pattern, isRegex = regex, isEnabled = true)

    private val keywordAi = rule("KEYWORD", "ai")
    private val tagAi = rule("TAG", "ai", regex = true)

    @Test
    fun `子串关键字不再吃掉整站 —— long_hair 这类标识符不算命中`() {
        // 实测 Danbooru 命中最多的几个 tag（long_hair 144 条 / hair_ornament 49 条 / tail 39 条）。
        listOf("long_hair", "hair_ornament", "tail", "white_hairband", "very_long_hair", "paid_reward_available")
            .forEach { tag ->
                assertFalse("$tag 不该命中 ai", GalleryBlockMatch.blocked(keywordAi, "", listOf(tag)))
                assertFalse("$tag 不该命中正则 ai", GalleryBlockMatch.blocked(tagAi, "", listOf(tag)))
            }
    }

    @Test
    fun `真正是 ai 的词元照样挡住`() {
        listOf("ai", "ai_generated", "generated_by_ai", "female:ai", "ai-生成").forEach { tag ->
            assertTrue("$tag 应命中", GalleryBlockMatch.blocked(keywordAi, "", listOf(tag)))
            assertTrue("$tag 应命中正则", GalleryBlockMatch.blocked(tagAi, "", listOf(tag)))
        }
    }

    @Test
    fun `中文规则整串相等才算`() {
        val r = rule("KEYWORD", "AI生成")
        assertTrue(GalleryBlockMatch.blocked(r, "", listOf("AI生成")))
        assertFalse("角色名里的字面不能算", GalleryBlockMatch.blocked(r, "", listOf("AI生成少女")))
    }

    @Test
    fun `KEYWORD 仍按子串比作者名`() {
        // 作者是人名的自由文本，与漫画侧同口径：`oumi_neneha` 对 `neneha` 要命中。
        assertTrue(GalleryBlockMatch.blocked(rule("KEYWORD", "neneha"), "oumi_neneha", emptyList()))
    }

    @Test
    fun `AUTHOR 规则不碰 tag，TAG 规则不碰作者`() {
        assertFalse(GalleryBlockMatch.blocked(rule("AUTHOR", "ai"), "", listOf("ai")))
        assertTrue(GalleryBlockMatch.blocked(rule("AUTHOR", "ai"), "studio_aim", emptyList()))
        assertFalse(GalleryBlockMatch.blocked(tagAi, "hair", emptyList()))
    }

    @Test
    fun `非法正则是 false 而不是把滚动炸掉`() {
        assertFalse(GalleryBlockMatch.blocked(rule("TAG", "ai(", regex = true), "", listOf("ai")))
    }

    @Test
    fun `未知规则类型不参与画廊判定`() {
        // COMIC_ID 那类是漫画身份，图站条目没有，判命中等于凭空挡图。
        assertFalse(GalleryBlockMatch.blocked(rule("COMIC_ID", "1"), "", listOf("ai")))
    }
}
