package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「推荐那一节坐在一张卡面上」的接线判据（2026-10-17 第二十二轮）。
 *
 * ## 它守的是什么
 *
 * 用户拍板：「推荐区包卡，但是还是目前这样的瀑布流，然后卡面会有图片的很低的模糊」。
 *
 * 这条诉求在本仓有一个**结构性难点**：推荐节是「节头 + 脚下整面瀑布流」，
 * 而 `LazyVerticalStaggeredGrid` 里没有"行"——卡片逐列往下排、高度各不相同。
 * 逐 item 画卡面会在某一列先结束的地方露出**阶梯缺口**。所以卡面只能画在网格**下面**的一整层。
 *
 * 判据因此钉三件事：
 * 1. 卡面层是网格的**兄弟**（同一个 `Box` 里、先于网格），不是逐 item 的装饰；
 * 2. 定位与裁剪在 **draw 阶段**读 `layoutInfo`（组合期读就是每帧重组）；
 * 3. 底图**排除打码**的那一张 —— 拿打码图当底等于把被屏蔽的内容换个方式摆出来。
 *
 * ⚠️ `app/` 没有 Robolectric，像素级的"卡片有没有露出来"测不了；
 * 但上面三条都是**结构**，改坏了源码形状就变 —— 那正是这类判据能拦住的范围。
 */
class GallerySectionSurfaceTest {

    private val screen = codeLines("com/venera/compose/gallery/ui", "GalleryScreen.kt")
    private val forYou = codeLines("com/venera/compose/gallery/ui", "GalleryForYouScreen.kt")

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    private fun bodyOf(lines: List<IndexedValue<String>>, header: String): String {
        val start = lines.firstOrNull { it.value.contains(header) }?.index
            ?: error("找不到 `$header`")
        val end = lines.firstOrNull { it.index > start && it.value == "}" }?.index
            ?: error("找不到 `$header` 的收尾括号")
        return lines.filter { it.index in start..end }.joinToString("\n") { it.value }
    }

    private fun countIn(body: String, regex: String): Int = Regex(regex).findAll(body).count()

    // ── 一、卡面层挂在网格外面 ──────────────────────────────────────────────

    @Test
    fun `卡面层是网格的兄弟，且先于网格`() {
        val grid = bodyOf(screen, "internal fun GalleryCardsGrid(")

        assertEquals(
            "网格里必须挂一层 `SectionSurfaceLayer(...)`：\n$grid",
            1,
            countIn(grid, """SectionSurfaceLayer\("""),
        )
        val layerAt = grid.indexOf("SectionSurfaceLayer(")
        val gridAt = grid.indexOf("LazyVerticalStaggeredGrid(")
        assertTrue("找不到网格本体", gridAt > 0)
        assertTrue(
            "卡面层必须写在网格**之前**（同一个 Box 里先摆的在下层）。" +
                "写到网格之后就变成盖住所有卡片的一层蒙版：\n$grid",
            layerAt in 1 until gridAt,
        )
        assertEquals(
            "网格自己要 `fillMaxSize()` 铺满那个 Box（`modifier` 已经挂到 Box 上，" +
                "再给网格一次就等于同一个修饰符挂两次）：\n$grid",
            1,
            countIn(grid, """modifier = Modifier\.fillMaxSize\(\)"""),
        )
    }

    // ── 二、定位在绘制阶段 ──────────────────────────────────────────────────

    @Test
    fun `卡面顶端在 draw 阶段算，不在组合期算`() {
        val layer = bodyOf(screen, "private fun SectionSurfaceLayer(")

        assertEquals(
            "裁剪必须走 `drawWithContent`（卡面要垫在内容下面）：\n$layer",
            1,
            countIn(layer, """\.drawWithContent \{"""),
        )
        assertTrue(
            "顶端必须在 `drawWithContent` 的块里算（组合期读 `layoutInfo` 就是每帧重组）：\n$layer",
            layer.contains("val top = sectionSurfaceTopPx(state, sectionIndex)"),
        )
        assertTrue(
            "`clipPath` 之后必须把内容画回来，否则整面墙都不见了：\n$layer",
            layer.contains("clipPath(surfacePath) { this@drawWithContent.drawContent() }"),
        )

        // 定位那颗粒子本身必须是**普通函数**：挂 `@Composable` 就只能在组合期调用。
        val helperAt = screen.indexOfFirst { it.value.contains("private fun sectionSurfaceTopPx(") }
        assertTrue("找不到 sectionSurfaceTopPx 的声明", helperAt > 0)
        assertTrue(
            "`sectionSurfaceTopPx` 不许标 @Composable —— 它靠 draw 期调用来做到零重组",
            !screen[helperAt - 1].value.contains("@Composable"),
        )
    }

    @Test
    fun `节头不在可见集合时按首项 index 判断该铺满还是不该画`() {
        val helper = bodyOf(screen, "private fun sectionSurfaceTopPx(")

        assertTrue(
            "必须按 `index == sectionIndex` 从可见集合里找节头：\n$helper",
            helper.contains("it.index == sectionIndex"),
        )
        assertTrue(
            "节头滚出后要看可见首项的 index 决定铺满（0f）还是没到（null）：\n$helper",
            helper.contains("firstVisible > sectionIndex"),
        )
    }

    // ── 三、卡面是**纯色**，不再挂模糊底图 ───────────────────────────────────
    //
    // ⚠️ 这一节原本钉的是「底图取第一张没被打码的」（第二十二轮，防"打码图当底"）。
    // 2026-10-17 第二十四轮**整条作废**：用户报「整个推荐面会有这种模糊效果」——
    // 86% 的卡面色挡不住底图颜色透出来，那一大片读成"雾蒙蒙"，而上面两节是干净的实色白卡。
    // 底图整个撤掉 ⇒ "打码图当底"这条风险随之消失，判据改成钉**不许再有模糊底**，
    // 免得下一轮又有人按老结论把它加回来。

    @Test
    fun `卡面是纯色，不再挂任何模糊底图`() {
        val layer = bodyOf(screen, "private fun SectionSurfaceLayer(")

        assertTrue(
            "卡面层里不许再出现模糊底图（`blur(` / `AsyncImage`）—— 那正是用户报「有模糊效果」的来源：\n$layer",
            !layer.contains(".blur(") && !layer.contains("AsyncImage"),
        )
        assertTrue(
            "卡面就是 `surface` 实色（与画师 / 排行榜那两节同一种材质）：\n$layer",
            Regex("""\.background\(tokens\.color\.surface\)""").containsMatchIn(layer) &&
                !layer.contains("surface.copy(alpha"),
        )
    }

    // ── 四、页与墙之间的透传 ────────────────────────────────────────────────

    @Test
    fun `首页把卡面规格透传给墙`() {
        val page = bodyOf(forYou, "internal fun GalleryForYouPage(")

        assertTrue(
            "`GalleryForYouPage` 必须收下这个参数：\n$page",
            page.contains("sectionSurface: GallerySectionSurface? = null"),
        )
        assertTrue(
            "并且透传给 `GalleryCardsGrid`（卡面的几何全在那一层，这一页不掺和）：\n$page",
            page.contains("sectionSurface = sectionSurface,"),
        )
    }
}
