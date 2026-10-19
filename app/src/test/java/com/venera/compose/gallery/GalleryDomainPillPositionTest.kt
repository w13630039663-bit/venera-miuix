package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 站源选择器那颗滑动胶囊的**位置算法**判据（2026-10-17 第二十七轮）。
 *
 * ## 它拦的是什么事
 *
 * 用户原话：「站源选择器你要看看，点到 pixiv 里面的胶囊不会过去」。
 * 同一形状在仓里已经真机红过一次（`UiAuditG3Test` 那一笔的 KDoc 记着：选中态切到右格、
 * 字的颜色换了档，底色却停在左格）—— 而那一轮写下的修法「量宽 + 前缀和」**当时没有落地**，
 * 判据反过来还在要求 `localBoundingBoxOf` 在场，于是它一直是绿的。本轮两件事一起做。
 *
 * ## 为什么「量宽 + 前缀和」比「换算到父坐标」稳
 *
 * 旧写法是 `coords.parentCoordinates?.localBoundingBoxOf(coords)`：把子的框换算到**父坐标**再画。
 * 换算链上任何一环拿不到真值（`parentCoordinates` 为空、父节点不是你以为的那一层、
 * 换算发生在放置还没定格的某一帧），落地的都是**上一帧的矩形** —— 而胶囊的动画值只认当期目标，
 * 屏上读作"胶囊不跟手"。
 *
 * 新写法：每格只报**自己的尺寸**（`onSizeChanged`，量的是节点自身），胶囊左缘 =
 * **前面各格宽度之和**。格子是 `Row` 里依次摆的，这条恒等式在 Row 自己的坐标系里成立，
 * 整条路上没有"换算"这一步 —— 少一环就少一个能错的地方。
 *
 * ## 为什么判据钉在源码形状上
 *
 * `app/` 没有 Robolectric / compose-ui-test（同 `GalleryHScrollRailHeightTest` 那条口径），
 * 弹簧动画跑到哪一帧没法在纯 JVM 里量。但"位置从哪来"这件事是**结构性的**：
 * 只要「每格报自己 + 前缀和 + 零处父子坐标」这三句话在，胶囊的目标就与当期布局同源。
 *
 * ⚠️ 注释行不参与匹配（`codeLines` 那把口径）：这些文件的大段注释里写着同样的符号名，
 * 不过滤就等于判据读的是我们自己的解释。
 */
class GalleryDomainPillPositionTest {

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    /** 按**函数体**切一段：从头部那一行到下一个第 0 列的 `}`（函数体内部的括号都缩进）。 */
    private fun bodyOf(header: String): String {
        val lines = codeLines("com/venera/compose/gallery/ui", "GalleryPixivHome.kt")
        val start = lines.firstOrNull { it.value.contains(header) }?.index
            ?: error("找不到 `$header`")
        val end = lines.firstOrNull { it.index > start && it.value == "}" }?.index
            ?: error("找不到 `$header` 的收尾括号")
        return lines.filter { it.index in start..end }.joinToString("\n") { it.value }
    }

    private fun countIn(body: String, regex: String): Int = Regex(regex).findAll(body).count()

    // ── 一、每格只报自己的尺寸 ─────────────────────────────────────────────

    @Test
    fun `每格报自己的尺寸，不牵涉父坐标`() {
        val body = bodyOf("internal fun GalleryHomeDomainChips(")

        assertTrue(
            "每格必须挂 `onSizeChanged`（量的是节点自己，不经过任何父子坐标换算）：\n$body",
            body.contains("onSizeChanged {"),
        )
        assertEquals(
            "整段里不许再出现 `parentCoordinates`：2026-10-17 真机上正是它让底色停在未选中那一格" +
                "（`UiAuditG3Test` 那一笔的 KDoc）：\n$body",
            0,
            countIn(body, """parentCoordinates"""),
        )
        assertEquals(
            "整段里不许再出现 `localBoundingBoxOf`（同上，这条量法不许回来）：\n$body",
            0,
            countIn(body, """localBoundingBoxOf"""),
        )
        assertEquals(
            "整段里不许再出现 `onGloballyPositioned`：这一排不需要窗口坐标，只需要自己的尺寸：\n$body",
            0,
            countIn(body, """onGloballyPositioned"""),
        )
    }

    // ── 二、位置 = 前缀和 ─────────────────────────────────────────────────

    @Test
    fun `胶囊左缘是前缀和，宽高取选中格自己`() {
        val body = bodyOf("internal fun GalleryHomeDomainChips(")

        assertTrue(
            "左缘必须是「前面各格宽之和」—— 格子是 Row 里依次摆的，这是恒等式：\n$body",
            body.contains(".take(selectedIndex).fold(0f)"),
        )
        assertTrue(
            "宽与高必须取选中格自己那一格（`getOrNull(selectedIndex)`）：\n$body",
            body.contains("sizes.getOrNull(selectedIndex)?.width?.toFloat()") &&
                body.contains("sizes.getOrNull(selectedIndex)?.height?.toFloat()"),
        )
        assertTrue(
            "还没量到时必须落 0（`?: 0f`）而不是某个默认矩形 —— 首帧什么都不画，" +
                "画一枚贴在左上角的 0 宽圆角矩形是噪声：\n$body",
            2 == countIn(body, """\?: 0f"""),
        )
        // 三条动画值：左 / 宽 / 高。纵坐标恒 0（每格同一档定高，Row 的交叉轴报高就是它），
        // 所以**没有**第四条从量测来的 top 动画。
        assertEquals(
            "胶囊必须走三条 `animateFloatAsState`（左 / 宽 / 高）：\n$body",
            3,
            countIn(body, """animateFloatAsState\("""),
        )
        assertTrue(
            "纵坐标必须是常量 0f（每格同一档 `.height(segmentedHeight - space1 * 2)`，" +
                "Row 的交叉轴报高就是它 ⇒ 不需要、也不该再量一个 top）：\n$body",
            body.contains("Offset(pillLeft, 0f)"),
        )
        assertEquals(
            "绘制前必须判 `pillWidth > 0f`（没量到时什么都不画）：\n$body",
            1,
            countIn(body, """if \(pillWidth > 0f\)"""),
        )
    }

    // ── 三、量出来的尺寸槽必须跟条目表同生命周期 ──────────────────────────

    @Test
    fun `尺寸槽按条目表记住，每帧新建会把状态洗一遍`() {
        val body = bodyOf("internal fun GalleryHomeDomainChips(")

        assertTrue(
            "尺寸槽必须 `remember(entries)`：键是伴生里那一份实例（`GalleryHomeDomain.HOME_OPTIONS`），" +
                "每帧新建 List 会把量好的尺寸洗回 0 —— 那一帧胶囊会缩成左上角一个点：\n$body",
            Regex("""remember\(entries\) \{ mutableStateListOf\(""").containsMatchIn(body),
        )
        assertTrue(
            "条目表必须取伴生里那一份实例（页面上这一排只有这一处消费）：\n$body",
            body.contains("GalleryHomeDomain.HOME_OPTIONS"),
        )
        assertTrue(
            "槽的初始值必须是 `IntSize.Zero`（首帧还没量 与 量出来是 0 由此分得开）：\n$body",
            body.contains("IntSize.Zero"),
        )
    }
}
