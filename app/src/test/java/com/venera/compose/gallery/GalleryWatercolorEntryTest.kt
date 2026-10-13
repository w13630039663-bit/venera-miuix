package com.venera.compose.gallery

import com.venera.compose.gallery.domain.watercolorEntryVisible
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「画成水彩」入口那一格（2026-10-14 从大图页底栏搬进「关于这张图」面板，评审 S2）。
 *
 * ## 两类判据各有各的用途
 *
 * ① 真跑：`watercolorEntryVisible` 的四格真值表 —— 这是唯一"能证行为"的一条。
 * ② 扫源码：搬动这类改动的坏样子**不是行为错**，而是**接了一半**：
 *   底栏撤干净了而面板没接上（用户彻底失去入口），或者反过来两处都在（同一件事两个入口），
 *   又或者把 `Spacer` 那枚空位一起当成"水彩的零件"撤掉（那就回退了 2026-10-04 用户报的第 8 条）。
 *   这三种编译器与真跑都管不到，只能扫字面。
 *
 * ## 负向为什么都配了对照
 *
 * 每条"不许出现 X"都另断一句"同一片里必须有 Y" —— 抽取失败时 `contains` 恒假、
 * 数数恒 0，那读起来正好就是"撤干净了"。没有对照的负向断言等于没写。
 */
class GalleryWatercolorEntryTest {

    private fun source(dir: String, name: String): String =
        RepoSources.kotlinFiles(dir).first { it.name == name }.readText()

    private val dock = source("com/venera/compose/gallery/ui", "GalleryViewerToolbar.kt")
    private val sheet = source("com/venera/compose/gallery/ui", "GalleryInfoSheet.kt")
    private val postScreen = source("com/venera/compose/gallery/ui", "GalleryPostScreen.kt")
    private val settings = source("com/venera/compose/feature/settings", "GallerySettings.kt")

    private fun occurrences(text: String, needle: String): Int = text.split(needle).size - 1

    /**
     * 按**调用行**计数，而不是按"整份文本含不含那串"。
     *
     * 差别在这里会咬人：`GallerySheetActionDivider()` 这颗件自己的定义行
     * （`private fun GallerySheetActionDivider() {`）里就写着同一串字面量 ——
     * 拿 `occurrences` 数会得到 3（两处调用 + 一颗定义），期望写 2 就红在尺子上，
     * 而它要防的那条并列违例根本没发生。本仓这类错已经栽过好几次，这次提前避开。
     */
    private fun callLines(text: String, call: String): Int = text.lines().count { it.trim() == call }

    private fun callLinesStartingWith(text: String, prefix: String): Int =
        text.lines().count { it.trim().startsWith(prefix) }

    /** ① 唯一能证行为的一条：两个否决者各拆各的，四格全喂。 */
    @Test
    fun `入口可见性真值表 开关与视频各有一票否决`() {
        assertTrue(watercolorEntryVisible(betaEnabled = true, isVideo = false))
        assertFalse("Beta 关着（默认档）也必须摆出来 ⇒ 那一格会变成假开关", watercolorEntryVisible(false, false))
        assertFalse("视频上摆一枚画不出东西的笔刷就是假按钮", watercolorEntryVisible(true, isVideo = true))
        assertFalse(watercolorEntryVisible(false, isVideo = true))
    }

    /** ② 底栏撤干净了：那三个形状一个都不许再留。 */
    @Test
    fun `底栏不再摆水彩`() {
        for (gone in listOf("showPaint", "onPaint", "Icons.Outlined.Brush")) {
            assertEquals("GalleryViewerToolbar.kt 里还留着 `$gone`（底栏该只剩 5 枚高频钮）", 0, occurrences(dock, gone))
        }
        // 对照：抽到的确实是那颗 dock，而不是一份空文本（空文本会让上面三条恒真）。
        assertTrue("对照组失效：没在 GalleryViewerToolbar.kt 里读到那颗「信息」钮", dock.contains("onInfo = onInfo") || dock.contains("onClick = onInfo"))
    }

    /**
     * ③ 视频那一条的**空位守恒**必须活着，而且只能有一枚。
     *
     * 从前这条 dock 上有两枚同尺寸 `Spacer`：HD 一支、水彩一支。撤水彩时最坏的一刀是
     * 把整块 `if/else` 连着 `Spacer` 一起删 —— 屏上看不出来（视频条目少一枚钮、整行窄一档、
     * 「关于这张图」往左挪约 25dp），而那正是用户 2026-10-04 报的第 8 条的成因。
     * 数**次数**而不是判"在不在"：在不在的那把尺子分不清"剩 HD 那一枚"与"两枚都没了"。
     */
    @Test
    fun `视频空位守恒只剩 HD 那一枚`() {
        val spacer = "Spacer(modifier = Modifier.size(VeneraIconButtonTouchSize))"
        assertEquals("dock 里的同尺寸空位应当恰一处（HD 那支）；0 = 守恒被撤了，2 = 水彩那支没删干净", 1, occurrences(dock, spacer))
        assertTrue("HD 那支的 `if (!post.isVideo)` 判据不在了", dock.contains("if (!post.isVideo)"))
    }

    /** ④ 面板这一头真接住了：形参在、往下递的那一行也在、渲染点也在。 */
    @Test
    fun `信息面板把这一格接到底`() {
        assertTrue("GalleryInfoSheet 的形参表里没有 onPaint", sheet.contains("onPaint: (() -> Unit)?,"))
        assertTrue("形参给了默认值 ⇒ 调用点漏传不会报错，只会让那一格永远不出现", !sheet.contains("onPaint: (() -> Unit)? = null"))
        assertTrue("没把 onPaint 递给 GallerySheetActionBar（接了一半）", sheet.contains("onPaint = onPaint,"))
        assertTrue("动作条里没有摆出这一格的那一行", sheet.contains("onPaint?.let {"))
        // 对照：那颗动作条本身确实被读到了。
        assertTrue("对照组失效：没读到 GallerySheetActionBar", sheet.contains("private fun GallerySheetActionBar("))
    }

    /**
     * ⑤ 它**不许挤进等分那一排**（评审 S2 原话是"与三枚并列"，这条故意反着做并钉住）。
     *
     * 那一排三格靠 `weight(1f)` 均分、中间两条发丝线。加第 4 格会把其余三格各自挪位
     * （= 第 8 条那种"按记忆去点却点在空隙里"），而 360dp 上每格只剩约 82dp，
     * 「在浏览器中打开」要 106dp ⇒ 会被截成省略号 —— Beta 关着的用户也跟着受害。
     * 尺子取**发丝线的条数**：并列进那一排就会多第三条。
     */
    @Test
    fun `水彩单独成一行 没有挤进等分那排`() {
        assertEquals(
            "底部动作条那一排应当仍是三格（中间两条发丝线）。数出第三条 = 有人把第四格并进了 weight(1f) 均分那一排",
            2, callLines(sheet, "GallerySheetActionDivider()"),
        )
        // 对照①：整排三格本身读得到（读空了上面那条恒真）。同样按调用行数，
        // 免得 `private fun RowScope.GallerySheetAction(` 那颗定义行被算成一次调用。
        assertEquals("动作条里的动作格数变了（现在该是 3 枚常驻 + 1 枚水彩 = 4）", 4, callLinesStartingWith(sheet, "GallerySheetAction("))
        // 对照②：水彩这一格真的在文件里摆着，而不是"因为整块被删了所以发丝线也没多"。
        assertEquals("GallerySheetAction 里应当只有那一枚笔刷图标", 1, occurrences(sheet, "Icons.Outlined.Brush"))
    }

    /** ⑥ 开关只在调用点判一次：面板里不许自己再读一遍偏好。 */
    @Test
    fun `可见性只在调用点判一次`() {
        assertEquals("GalleryPostScreen 里该只有一处调用 watercolorEntryVisible(", 1, occurrences(postScreen, "watercolorEntryVisible("))
        assertFalse("GalleryInfoSheet 自己读了 Beta 偏好 ⇒ 两处各判一遍，迟早漂", sheet.contains("galleryWatercolorBeta"))
        assertFalse("面板里二次判了开关", sheet.contains("betaEnabled"))
        assertTrue("对照组失效：调用点没把这一张是不是视频递进去", postScreen.contains("isVideo = current.isVideo"))
    }

    /**
     * ⑦ 设置页那句说明要说**新位置**（措辞只许这一把尺子钉）。
     *
     * 从前那句写的是"开启后大图页底栏会多出一枚笔刷按钮" —— 搬走之后它就是屏上的假话，
     * 而用户会照着它去底栏找那枚已经不在的钮（本仓反复踩过的那族：文案说 X 在而 X 已搬/已撤）。
     */
    @Test
    fun `设置页那句说明指的是新位置`() {
        val line = settings.lines().firstOrNull { it.contains("实验功能，默认关闭") }
            ?: error("GallerySettings.kt 里找不到「画成水彩」那条 summary")
        assertTrue("summary 不再指向新的落点（「关于这张图」面板）：$line", line.contains("关于这张图"))
        assertFalse("summary 还写着旧的落点「底栏」：$line", line.contains("底栏"))
    }
}
