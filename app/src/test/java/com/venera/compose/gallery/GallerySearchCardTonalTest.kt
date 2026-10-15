package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 画廊搜索卡的**色调台阶**（2026-10-15，用户「除了这些整个ui直接改成图一吧」）。
 *
 * 参考图逐像素量出来的账：卡底 `#FCF0F0`，控件底 `#F6E4EA` / `#F0E4E4` —— 两级台阶
 * 1.055:1 与 1.115:1，**全卡零描边**。我们当时整张卡只有一档平面：卡底 `#EAE7EE`、
 * 筹码填的 `Neutral` 档与卡底是同一颗 `surfaceContainerHigh` = 1.000:1，未选药丸那层
 * 0.5 alpha 被卡底吃掉 = 实测 1.021:1，输入行与推荐两行干脆没有底。
 *
 * 上一批的错法是**拿描边代替台阶**（四根线），被用户否掉并整体撤销；这一批改的是
 * 台阶本身。所以这里钉的形状是"**该有底的地方有底，且一根线都不许画**"：
 *  - 输入行 / 推荐容器 / 筹码 / 未选药丸 四处都吃 `surfaceVariant` 那一档；
 *  - 整颗文件里不许再出现 `BorderStroke(` 或 `.border(`（负向，一次列全违例）；
 *  - 输入行那颗必须**定高** —— 它内部有一根 `fillMaxHeight()` 的竖分隔，
 *    只给下限时那一行会被撑成整屏高的拱门（真机截图量过，见那颗 Row 的注释）；
 *  - `VeneraChipVariant.Tonal` 是这一档的载体：填 `surfaceVariant`、不吃玻璃、不描边。
 */
class GallerySearchCardTonalTest {

    private val appMain = RepoSources.appMain

    private fun src(rel: String): List<String> {
        val f = File(appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n").split("\n")
    }

    /** 注释行不算命中（头注里一定会点名这些形状，把注释算进去就是在惩罚写清楚的人）。 */
    private fun codeOnly(lines: List<String>, needle: String): List<Int> =
        lines.indices.filter { i ->
            val t = lines[i].trim()
            t.contains(needle) && !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }.map { it + 1 }

    private val card = "com/venera/compose/gallery/ui/GallerySearchArea.kt"

    @Test fun `卡内四格都吃 surfaceVariant 那一档`() {
        val lines = src(card)
        // ① 输入行那颗胶囊
        assertTrue("输入行没有填充底（该吃 surfaceVariant）", codeOnly(lines, ".background(tokens.color.surfaceVariant)").isNotEmpty())
        // ② 分段器那一排走"实色卡"档
        assertTrue("卡里那排分段器没表态 Opaque（未选档会被卡底吃掉）", codeOnly(lines, "SegmentedBackdrop.Opaque").isNotEmpty())
        // ③ 历史筹码走 Tonal
        assertTrue("最近搜索的筹码还在吃 Neutral（与卡底同一颗色 = 1.000:1）", codeOnly(lines, "VeneraChipVariant.Tonal").isNotEmpty())
        // ④ 收藏页那两排住在玻璃上，必须仍是 Glass（不许被这次改动顺手带走）
        val fav = src("com/venera/compose/feature/FavoritesScreen.kt")
        assertEquals("收藏页那两排应当各表态一次 Glass", 2, codeOnly(fav, "SegmentedBackdrop.Glass").size)
    }

    @Test fun `整张卡一根描边都不许画`() {
        val lines = src(card)
        val strokes = codeOnly(lines, "BorderStroke(") + codeOnly(lines, ".border(")
        assertTrue(
            "这一版的层次靠底色台阶，不靠线。出现描边就是退回上一批被否掉的那副样子：$strokes",
            strokes.isEmpty(),
        )
        // 量具自证：这颗文件确实有代码行可读
        assertTrue("读不到搜索卡的入口函数，量具失效", codeOnly(lines, "fun GallerySearchArea(").isNotEmpty())
    }

    @Test fun `填充胶囊定高且满高子件住在定高格里`() {
        val lines = src(card)
        // 输入行：clip(percent=50) 之后必须紧跟一枚定高
        val pills = codeOnly(lines, ".clip(RoundedCornerShape(percent = 50))")
        assertTrue("量具找不到输入行那颗胶囊，量具失效", pills.isNotEmpty())
        for (n in pills) {
            val chain = lines.drop(n - 1).take(10).joinToString("\n")
            assertTrue(
                "第 $n 行那颗胶囊没有定高：它内部有 fillMaxHeight 的子件时会把整行撑满（真机：整屏拱门）",
                chain.contains(".height(tokens.spacing.dockedSearchBarHeight)"),
            )
        }
        // 每一处满高子件，所在那一格（往上最近的一枚 Row / Column）必须自己定高
        val floaters = codeOnly(lines, ".fillMaxHeight()")
        assertTrue("量具读不到那根竖分隔，量具失效", floaters.isNotEmpty())
        val unbounded = floaters.filter { n ->
            val back = (1 until n).firstOrNull { k ->
                val t = lines[n - 1 - k].trim()
                t.startsWith("Row(") || t.startsWith("Column(")
            } ?: return@filter true
            lines.subList(n - 1 - back, n - 1).none { it.contains(".height(") }
        }
        assertTrue("这些 fillMaxHeight 住在未定高的格子里，会把那一格撑到父级上限：$unbounded", unbounded.isEmpty())
    }

    @Test fun `Tonal 档填 surfaceVariant 且不描边不上玻璃`() {
        val lines = src("com/venera/compose/components/venera/VeneraChip.kt")
        assertTrue("Tonal 档没进枚举", codeOnly(lines, "enum class VeneraChipVariant").any { lines[it - 1].contains("Tonal") })
        assertTrue(
            "Tonal 档必须填 surfaceVariant（比卡底 surfaceContainerHigh 低一档）",
            codeOnly(lines, "variant == VeneraChipVariant.Tonal -> tokens.color.surfaceVariant").isNotEmpty(),
        )
        assertTrue(
            "Tonal 档必须显式不吃玻璃，否则半透明容器会把这一档洗平",
            codeOnly(lines, "variant == VeneraChipVariant.Tonal -> null").isNotEmpty(),
        )
        // 描边那一段不许把 Tonal 收进去（它靠底色分层，再加轮廓就是两层装饰）
        val strokeLines = codeOnly(lines, "variant == VeneraChipVariant.Tonal")
        assertTrue(
            "Tonal 出现在描边决策里（这一档必须无描边）",
            strokeLines.none { lines[it - 1].contains("BorderStroke") },
        )
    }
}
