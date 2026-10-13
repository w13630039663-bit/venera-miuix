package com.venera.compose.feature

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-14 UI 审查第三批的尺子：「关于这张图」那一屏的**去色与主次**。
 *
 * 立案依据是 `关于这张图-杂乱诊断.md`（4 张真机截图逐块量下来）：一屏之内同时出现
 * 块标题彩色徽标 × 3、平台品牌色 × 3、分级色、主题强调色、标签分类色 × 4 ——
 * 面积相当、饱和相当，谁也压不住谁，屏上没有锚点；而动作条把**默认关闭的实验件**
 * 摆成了整行，把真正的高频动作「下载原图」挤成 1/3 宽。
 *
 * 三档彩色锚点收敛到**一处**（分级），其余全部中性。为什么扫源码而不是跑起来看：
 * "把 tint 参数加回去"、"给某一格重新配一枚图标"这类回归不改变任何行为，
 * 只改变屏上有没有那一片颜色 —— 编译器与运行时都看不见，而它恰好是这一批的全部内容。
 *
 * ⚠️ 每条负向都配了"同一片里必须有 Y"的对照：抽取失败时 `contains` 恒假，
 * 那读起来正好就是"撤干净了"。
 */
class UiAuditBatch3Test {

    private fun code(dir: String, name: String): List<String> =
        RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private val block = code("com/venera/compose/gallery/ui", "GallerySheetBlock.kt")
    private val sheet = code("com/venera/compose/gallery/ui", "GalleryInfoSheet.kt")

    private fun blockAfter(lines: List<String>, anchor: String, n: Int): List<String> {
        val i = lines.indexOfFirst { it.startsWith(anchor) }
        assertTrue("找不到锚点：$anchor", i >= 0)
        return lines.subList(i, minOf(i + n, lines.size))
    }

    /** ① 节标题与它的徽标**不再吃色相**；块面那层洗底留着（它管"这是一块"，不抢锚点）。 */
    @Test
    fun `节标题徽标去色而块面洗底还在`() {
        val header = blockAfter(block, "internal fun SheetSectionHeader(", 8)
        assertFalse("节标题又拿回了取色参数：$header", header.any { it.contains("tint") })
        assertTrue(
            "徽标那颗件应当只吃图标",
            block.contains("internal fun SheetSectionBadge(icon: ImageVector) {"),
        )
        val badge = blockAfter(block, "internal fun SheetSectionBadge(icon: ImageVector) {", 18)
        assertTrue("徽标底不是中性槽位：$badge", badge.any { it.contains("tokens.color.surfaceVariant") })
        assertTrue("徽标字形不是中性色：$badge", badge.any { it.contains("tokens.color.textTertiary") })
        // 对照：SheetBlock 那层 tint 仍然在（读空了上面几条恒真）。
        assertTrue("块面洗底被顺手删了：那一层管的是『这是一块』", block.any { it.startsWith("tint: Color,") })
    }

    /** ② 一屏只留**一枚**彩色胶囊 = 标题行右端那枚分级。 */
    @Test
    fun `彩色锚点一屏只留分级那一处`() {
        assertEquals(
            "「关于这张图」里只许有一枚自涂底色的胶囊（分级那一枚）。多出来一枚就说明彩色锚点又长回去了",
            1, sheet.count { it.startsWith("containerColorOverride =") },
        )
        // 对照：那唯一一枚确实是分级，不是别的。
        val badge = blockAfter(sheet, "private fun RatingBadge(rating: String) {", 20)
        assertTrue("唯一那枚彩色胶囊不是分级：$badge", badge.any { it.contains("GalleryRatingColors") })
    }

    /** ③ 基本信息那一格**不带图标**（八枚同色灰度图形不携带读数，而 MD5 那枚指纹语义本来就是错的）。 */
    @Test
    fun `基本信息格去掉装饰性图标`() {
        assertTrue(
            "FactCell 又带回 icon 参数了",
            sheet.any { it == "private class FactCell(val label: String, val value: String)" },
        )
        assertFalse("网格里又出现了指纹图标", sheet.any { it.contains("Icons.Outlined.Fingerprint") })
        // 对照：网格本身还在（不是"整块被删了所以图标也没了"）。
        assertTrue("基本信息那块不摆了", sheet.any { it.contains("GalleryFactCell(cell)") })
        assertTrue("列数常量丢了", sheet.any { it.startsWith("private const val FACT_GRID_COLUMNS = 3") })
    }

    /** ④ 「来源」改叫「站点」：与③那块「来源链接」不是一件事，从前两处同名读起来就是重复。 */
    @Test
    fun `站点与来源链接两种叫法分开`() {
        assertTrue("那一格又写回『来源』了", sheet.none { it.contains("FactCell(\"来源\"") })
        assertTrue("改名没落地：$sheet", sheet.any { it.contains("FactCell(\"站点\"") })
        // 对照：评分那一格还在 —— 报告里"分级出现两次"那条是把 `post.score` 读成了分级，
        // 撤销那条判断的正身证据就是这一格仍然点名它是**评分**。
        assertTrue("评分那一格被顺手删了", sheet.any { it.contains("FactCell(\"评分\"") })
    }

    /** ⑤ 出处那一行的抬头与下面那行**同形**（`域名 · #号`），否则两行读起来像两组数据。 */
    @Test
    fun `来源链接两行抬头同形`() {
        assertTrue(
            "出处那行的编号前丢了 `#`",
            sheet.any { it == "return \"\$host · #\$artworkId\"" },
        )
        // 那两串 `${...}` 要按字面匹配，所以每个 `$` 都写成 `${'$'}`：
        // 直接写进字符串里会被**本测试文件**的编译器插值掉（它可不认识 `post`）。
        assertTrue(
            "本站那行的抬头形状变了",
            sheet.any { it.contains("headline = \"${'$'}{post.site.displayName} · #${'$'}{post.id}\"") },
        )
    }

    /** ⑥ 标签分类的**色只留在图标上**，分类名回中性字色（色=归属那条编码没丢，换了载体）。 */
    @Test
    fun `分类色只留在图标上而名字回中性`() {
        val label = blockAfter(sheet, "private fun GalleryTagGroupLabel(group: GalleryTagGroup) {", 30)
        assertTrue("分类图标又丢了色：$label", label.any { it == "tint = color," })
        assertTrue("分类名又吃上了档位色：$label", label.any { it == "color = tokens.color.textPrimary," })
        assertFalse("分类名那一格又用回变量色：$label", label.any { it == "color = color," })
    }
}
