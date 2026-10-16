package com.venera.compose.ui.tokens

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-15 自审修订版第一批（S1 · 令牌层）的尺子。
 *
 * 立案读数来自真机，不是审美判断（`_qa/ui-verify-2026-10-15/audit_glyphs.ps1`、
 * `audit_pixels2.ps1`，截图 `scr-44-detail.png` 圆座那一排）：
 *  - 旧的 MD3 四角色映射（`primary / error / tertiary / secondary`）屏上实测字形最暗像素
 *    收藏 (174,47,52) 与点赞 (186,26,26) **同为红族**，分享与链接同为 (119,86,85) 且饱和只剩 29%
 *    ⇒ "四色互异"这个目标本来就没达成，右半排还读起来比左半排更"旧"；
 *  - 圆座底色用 `identityColor.copy(alpha = 0.12f)`，与 MD3 **禁用态**同值，
 *    五枚实测与页底只差 11~13 级亮度（彼此差 <2 级）⇒ 没点过的动作看着像不能点的动作。
 *
 * 所以这一批改的是**令牌层一处映射 + 一颗公共件**，不是逐页调色。
 * 每把尺子都配了"对照"断言 —— 扫描类判式读空了会恒真，那等于没有用例。
 */
class UiAuditS1Test {

    private val main get() = RepoSources.allMainLines()

    private fun codeLines(rel: String): List<String> {
        val file = RepoSources.appMain.resolve(rel)
        assertTrue("找不到源码文件：${file.absolutePath}", file.isFile)
        return file.readLines().map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
    }

    /** ① 状态型身份色只剩两枚槽位，且点赞不再借全站唯一的破坏性色。 */
    @Test
    fun `动作身份色只剩收藏与点赞两枚`() {
        val tokens = codeLines("com/venera/compose/ui/tokens/VeneraTokens.kt")
        // 钉整串表达式：只钉标识符的话，`else m.error` 换回来说谎也不会红。
        assertEquals(
            "点赞的 MD3 档取值变了（它不该借 error，读数见本文件头注）",
            1,
            tokens.count { it.contains("actionLike = if (fixedActions) StatusColors.Like else m.tertiary,") },
        )
        assertTrue(
            "action* 赋值行里又出现了 m.error：" +
                main.filter { it.third.contains("action") && it.third.contains("m.error") }.map { "${it.first}:${it.second}" },
            main.none { it.third.trim().startsWith("action") && it.third.contains("m.error") },
        )
        // 对照：两枚槽位与固定色定义都还在，上面那条不是读空。
        val color = codeLines("com/venera/compose/ui/tokens/Color.kt")
        assertEquals(1, color.count { it == "val actionFavorite: Color," })
        assertEquals(1, color.count { it == "val actionLike: Color," })
        assertEquals(0, color.count { it.startsWith("val actionComment") || it.startsWith("val actionShare") })
        assertTrue(
            "StatusColors.Like 的定义变了",
            color.any { it.contains("val Like = Color(0xFFE91E63)") },
        )
    }

    /** ② 详情页那一排：恰好两枚递身份色（状态型），三枚动作型不递 —— 数道数，不钉存在。 */
    @Test
    fun `详情动作行只有两枚状态型递身份色`() {
        val detail = codeLines("com/venera/compose/feature/ComicDetailScreen.kt")
        assertEquals(
            "递身份色的道数变了（状态型 = 收藏 + 点赞，共 2 道）",
            2,
            detail.count { it.startsWith("identityColor = tokens.color.action") },
        )
        assertEquals(5, detail.count { it.startsWith("DetailActionButton(") })
        // 对照：旧参数名与两枚被删的槽位在全仓一处不剩（防"只改了一半"）。
        assertEquals(
            "还有地方在用旧参数名或旧槽位：" +
                main.filter { it.third.contains("iconColor") || it.third.contains("actionComment") || it.third.contains("actionShare") }
                    .map { "${it.first}:${it.second}" },
            emptyList<Triple<String, Int, String>>(),
            main.filter { it.third.contains("iconColor") || it.third.contains("actionComment") || it.third.contains("actionShare") },
        )
    }

    /** ③ 圆座不再用 12% 那一档；字号与圆角都归到槽位。 */
    @Test
    fun `圆座不再抄 alpha 也不再用禁用态那一档`() {
        val button = codeLines("com/venera/compose/feature/DetailActionButton.kt")
        assertTrue(
            "这颗件里又出现了内联 alpha：$button",
            button.none { it.contains("copy(alpha =") },
        )
        assertEquals(1, button.count { it.contains(".background(tokens.color.surfaceVariant)") })
        assertEquals(1, button.count { it.contains("fontSize = tokens.type.badge") })
        assertEquals(1, button.count { it.contains("RoundedCornerShape(tokens.shape.medium)") })
        // 对照：badge 与 shape.medium 两档确实存在，上面不是钉在空气上。
        val typo = codeLines("com/venera/compose/ui/tokens/Typography.kt")
        assertTrue("字阶里 badge 档没了", typo.any { it.contains("badge = 11.sp") || it.contains("badge = 10.sp") })
        val spacing = codeLines("com/venera/compose/ui/tokens/Spacing.kt")
        assertTrue("shape.medium 档没了", spacing.count { it.contains("medium = 12.dp") || it.contains("medium = 14.dp") } >= 2)
    }

    /** ④ 封面圆角与 `VeneraCover` 走同一把槽位（旧的一处写死 12dp，Miuix 档下两枚封面会差 2dp）。 */
    @Test
    fun `封面圆角与 VeneraCover 同一把尺子`() {
        val tile = codeLines("com/venera/compose/components/ComicTileLayout.kt")
        assertEquals(1, tile.count { it.contains(".clip(RoundedCornerShape(VeneraTokens.shape.medium))") })
        assertTrue("列表卡又自己写了一遍裸圆角", tile.none { it.contains("RoundedCornerShape(12.dp)") })
        // 对照：VeneraCover 那处仍是同一个槽位（两边同档才是"一把尺子"）。
        val cover = codeLines("com/venera/compose/components/venera/VeneraCover.kt")
        assertEquals(1, cover.count { it.contains("val shape = RoundedCornerShape(tokens.shape.medium)") })
    }

    /** ⑤ 裸 `RoundedCornerShape(N.dp)` 存量封顶，只许往下带（口径见下）。 */
    @Test
    fun `裸圆角存量只许缩短`() {
        // 口径：整棵 app/src/main，跳过三种注释行，只数「RoundedCornerShape(数字」这种带字面量的写法。
        // 走 tokens 的 `RoundedCornerShape(tokens.shape.x)` 不在此列（它不以数字开头）。
        val offenders = main.filter {
            val t = it.third.trim()
            (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")).not() &&
                Regex("""RoundedCornerShape\([0-9]""").containsMatchIn(it.third)
        }
        assertTrue(
            "裸圆角从 45 处涨到了 ${offenders.size}：" + offenders.take(12).map { "${it.first}:${it.second}" },
            offenders.size <= 45,
        )
    }
}
