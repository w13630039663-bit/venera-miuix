package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「横滑一排的高度不许由字形决定」这条口径的判据（2026-10-17 第二十一轮）。
 *
 * ## 它拦的是什么事
 *
 * 用户报「快速滑动画师头像时整张白卡上下跳 1~2px」，随后补一句「排行榜也在抖」。
 * 两处形状相同：一张 `surface` 白卡里装一条横向 `LazyRow`。
 *
 * 链条（每一环都能在源码里指出来）：
 * 1. `LazyRow` 在**交叉轴**上报的是「当前已测量那几格里的最大高」—— 懒布局只测视口内的项，
 *    这是横向量独有的语义（纵向 `LazyColumn` 至少在一屏内稳定）；
 * 2. 格子高度由文字行决定，而 `lineHeight` 未指定时行高取「这一行实际用到的字形」的
 *    ascent + descent —— 同一行字，纯拉丁 / CJK / 带 emoji 差 1~2px；
 * 3. 横滑 ⇒ 视口里换了谁 ⇒ 报高变 ⇒ 白卡高度变；
 * 4. 白卡本身是 `LazyStaggeredGrid` 的一个 item，item 一改高，网格重排**后继全部项**
 *    ⇒ 屏上读作「整张白卡上下跳」。
 *
 * ## 为什么判据钉在源码形状上
 *
 * `app/` 没有 Robolectric / compose-ui-test（同 `GalleryPixivHomeWiringTest` 那条口径），
 * 这种 1~2px 的测量漂移**没法在纯 JVM 里量**。但它的成因是**结构性的**：只要
 * 「外框高度写死 + 行高写死」这两件事在，报高就是常量，链条 ③ 起就断了。
 * 所以判据不量像素，量的是「那两件事还在不在」。
 *
 * 三条断言的分工：
 * - 定高必须**从 token 派生**（`.height(railHeight)`），不许退化成 `.height(320.dp)` 这种
 *   与主题字号脱钩的裸数字 —— 裸数字在另一套字号档下会裁字；
 * - `lineHeight` 必须写死（`TEXT_LINE_HEIGHT.em`），这是治源头那一环；
 * - 两颗常量（`TEXT_LINE_HEIGHT` / 那两处表达式）的**具体形状**钉住，改名会红。
 *
 * ⚠️ 注释行不参与匹配（`codeLines` 那把口径）：这些文件里大段注释都写着同样的符号名，
 * 不过滤就等于判据读的是我们自己的解释。
 *
 * ## 第五节的来由（不加高、但同属"这一排横滑时要稳"）
 *
 * 同一轮里用户还要「左侧也加渐隐」。左端不能照抄右端那道**恒亮**的：停在起点时左侧
 * 没有内容被裁，恒画一道就是把第一位头像的左缘糊在卡面内衬上。于是左端由滚动位置驱动，
 * 而它的可见度**必须在绘制期读**（`State<Float>`）：换成 `Float` 参数就是每帧把新 Modifier
 * 递给 `LazyRow`、连带重组宿主，而 fling 那一秒要跑 60 次。这条也一并钉住。
 */
class GalleryHScrollRailHeightTest {

    private val pixiv = codeLines("com/venera/compose/gallery/ui", "GalleryPixivHome.kt")

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    /** 按**顶层函数体**切一段：从头部那一行到下一个第 0 列的 `}`（函数体内部的括号都缩进）。 */
    private fun bodyOf(header: String): String {
        val start = pixiv.firstOrNull { it.value.contains(header) }?.index
            ?: error("找不到 `$header`")
        val end = pixiv.firstOrNull { it.index > start && it.value == "}" }?.index
            ?: error("找不到 `$header` 的收尾括号")
        return pixiv.filter { it.index in start..end }.joinToString("\n") { it.value }
    }

    private fun countIn(body: String, regex: String): Int = Regex(regex).findAll(body).count()

    // ── 一、画师那一排 ───────────────────────────────────────────────────────

    @Test
    fun `画师一排的外框高度是常量，不随已测集合变`() {
        val body = bodyOf("internal fun PixivArtistRail(")

        assertEquals(
            "这一排必须挂 `.heightIn(min = railHeight)`：交叉轴报高否则会随「视口里是哪几位」变化，" +
                "而它外面就是一张定高白卡 —— 白卡会被这 1~2px 顶着跳。\n$body",
            1,
            countIn(body, """\.heightIn\(min = railHeight\)"""),
        )
        assertEquals(
            "定高必须用 `heightIn(min = …)` 而不是 `height(…)`：`LineHeightStyle` 默认 `Trim.None`，" +
                "行高小于字体度量时不裁溢出而是让文本更高 ⇒ 算出来的值是**下限**，钉死会裁字尾：\n$body",
            0,
            countIn(body, """\.height\(railHeight\)"""),
        )
        assertEquals(
            "外框高度必须从 token 派生（`artistAvatarSize` + `space1` + 行高），" +
                "不许写成与主题脱钩的裸 dp 数字：\n$body",
            0,
            countIn(body, """\.height(In)?\(\s*(min = )?\d"""),
        )
        assertEquals(
            "名字那一行必须写死 lineHeight —— 这是治源头那一环（行高否则跟着字形走）：\n$body",
            1,
            countIn(body, """lineHeight = TEXT_LINE_HEIGHT\.em"""),
        )
    }

    // ── 二、排行榜收起态（`PixivWorkCard` 也在展开态网格里） ────────────────

    @Test
    fun `排行榜卡的文字区定高，两行都写死行高`() {
        val body = bodyOf("private fun PixivWorkCard(")

        assertEquals(
            "文字区必须定高（封面高度由卡宽经 `aspectRatio` 定、卡宽又确定，所以这一段定了整卡就定了）：\n$body",
            1,
            countIn(
                body,
                """\.heightIn\(min = titleLineHeight \+ tokens\.spacing\.space1 \+ captionLineHeight\)""",
            ),
        )
        assertEquals(
            "卡片里**每一处**文字都要写死 lineHeight：标题、画师名、以及站方没给头像时" +
                "那颗首字母座（`itemTitle` + `caption` + 首字母座 = 3）：\n$body",
            3,
            countIn(body, """lineHeight = TEXT_LINE_HEIGHT\.em"""),
        )
        assertEquals(
            "文字区不许用裸 dp 定高，也不许钉成固定 `height(...)`（会裁字尾）：\n$body",
            0,
            countIn(body, """\.height\(\s*\d"""),
        )
    }

    // ── 三、同一张白卡里那两排会切字重的标签 ────────────────────────────────

    @Test
    fun `白卡里切字重的两排标签也写死行高`() {
        // 「已关注 / 发现」切换器与排行榜档名药丸：选中态换 `fontWeight`，而它们都坐在定高白卡里。
        // 行高若随字重动，**切一次 Tab 整张卡跳一下** —— 与横滑抖动是同一个病灶的另一条入口。
        assertEquals(
            "`PixivSegmentedRow` 与 `PixivPillRow` 的标签都必须写死 lineHeight",
            2,
            countIn(
                bodyOf("private fun <T> PixivSegmentedRow(") +
                    bodyOf("private fun <T> PixivPillRow("),
                """lineHeight = TEXT_LINE_HEIGHT\.em""",
            ),
        )
    }

    // ── 四、那个倍数的出处 ──────────────────────────────────────────────────
    @Test
    fun `行高倍数照抄 miuix paragraph 的 12 倍`() {
        val decl = pixiv.firstOrNull {
            it.value.contains("internal const val TEXT_LINE_HEIGHT")
        }?.value ?: error("找不到 TEXT_LINE_HEIGHT 的声明")

        assertTrue(
            "倍数必须与 miuix `paragraph` 同档（`MiuixTextStyles.kt` 的 `1.2f.em`）：$decl",
            decl.contains("1.2f"),
        )
    }

    // ── 五、左缘渐隐 ────────────────────────────────────────────────────────

    @Test
    fun `两条横滑一排都挂了双端渐隐，且共用一个出处`() {
        val artist = bodyOf("internal fun PixivArtistRail(")
        val ranking = bodyOf("internal fun PixivRankedWorkGrid(")
        // 2026-10-17 第三十轮起它是 `internal`：图库站关注画师行进了白卡之后与
        // PixivArtistRail 同档同形，两端渐隐必须只有这一颗（两站各写一版就会漂）。
        val helper = bodyOf("internal fun Modifier.horizontalRailFades(")

        assertEquals(
            "画师一排必须走 `.horizontalRailFades(...)`（两端渐隐不许各写一遍）：\n$artist",
            1,
            countIn(artist, """\.horizontalRailFades\("""),
        )
        assertEquals(
            "排行榜收起态也必须走同一颗（与画师那一排是同一形状、同一个病灶）：\n$ranking",
            1,
            countIn(ranking, """\.horizontalRailFades\("""),
        )
        assertEquals(
            "两端渐隐必须都在 helper 里（右端恒亮 + 左端跟滚动位置）：\n$helper",
            2,
            countIn(helper, """\.hScroll(Start|End)Fade\(container = container"""),
        )
        assertTrue(
            "左端可见度必须以 `State` 形式交进去（绘制期读值 ⇒ 只重绘）。" +
                "传成 `Float` 会让 fling 期间每帧重组这一排的宿主：\n$helper",
            helper.contains("visible = startVisible"),
        )
    }

    @Test
    fun `左缘渐隐的可见度参数收的是 State 而不是裸 Float`() {
        val fade = codeLines("com/venera/compose/components/venera", "VeneraHScrollFade.kt")
        val sig = fade.dropWhile { !it.value.contains("fun Modifier.hScrollStartFade(") }
            .takeWhile { !it.value.contains("): Modifier") }
            .joinToString("\n") { it.value }

        assertTrue(
            "`hScrollStartFade` 的签名里必须有 `visible: State<Float>`：\n$sig",
            sig.contains("visible: State<Float>,"),
        )
        assertTrue(
            "`State` 要从 androidx.compose.runtime 引进来：\n$sig",
            fade.any { it.value == "import androidx.compose.runtime.State" },
        )
        assertTrue(
            "绘制期必须真读 `visible.value`（只捕获不读就等于没有订阅）：\n$sig",
            fade.any { it.value.contains("visible.value.coerceIn(") },
        )
    }
}
