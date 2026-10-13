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

    /**
     * 取**某一处调用往后 n 行**那一整段（含实参表与它自己的内容槽位）。
     *
     * 为什么不能只看"含不含锚点那一行"：`VeneraButton(` 的四个判点（递的是哪个回调、
     * 吃没吃 `saving`、吃没吃满整行、标签跟不跟着内容走）**全在它下面那十几行里** ——
     * 按行匹配的尺子看不见它们，而"把整行主按钮换成一枚小钮"这种回归恰好只改下面那几行。
     */
    private fun blockAfter(text: String, anchor: String, n: Int): String =
        text.lines().let { lines ->
            val i = lines.indexOfFirst { it.trim().startsWith(anchor) }
            if (i < 0) error("找不到调用点：$anchor") else lines.subList(i, minOf(i + n, lines.size)).joinToString(" ") { it.trim() }
        }

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
     * ⑤ 动作条的**三档权重**（2026-10-14 重排：评审里那条 P0"主次倒置"修掉）。
     *
     * 从前那一排是"三格等分 + 上面一整行「画成水彩 Beta」"：一枚默认关闭的实验件拿到了
     * 比这一页真正的高频动作更高的视觉权重。评审 S2 的原话是"与三枚并列"，这一条**故意反着做**
     * 并钉住 —— 并列进等分排会把每格挤到截字（360dp 上「在浏览器中打开」要 104~111dp 而每格只剩 83dp），
     * 还会让两格各自挪位（= 用户 2026-10-04 报的第 8 条"按记忆去点却点在空隙里"）。
     *
     * 现在三档：① 下载 = 整行 `VeneraButton`（这一条上唯一有底色的一枚）；
     * ② 复制链接 / 在浏览器中打开 = 等分两格、中间**一条**发丝线；
     * ③ 画成水彩 = 最低一档的小字整行入口。
     * 尺子取**发丝线的条数**：并进等分排就会多一条，而"多一条"正是那件事唯一的源码指纹。
     */
    @Test
    fun `动作条三档权重 下载是整行主按钮而水彩是最低一档`() {
        assertEquals(
            "等分那一排应当只剩两格（中间一条发丝线）。0 = 两格并成一格，2 = 有人把第三格并进了 weight(1f) 均分那一排",
            1, callLines(sheet, "GallerySheetActionDivider()"),
        )
        assertEquals(
            "等分排里的动作格数（现在该是 复制链接 + 在浏览器中打开）",
            2, callLinesStartingWith(sheet, "GallerySheetAction("),
        )
        // 对照①：主行动那一枚确实还在，而且是**整行 + 吃 saving 那道闸**（读空了上面两条恒真）。
        val button = blockAfter(sheet, "VeneraButton(", 32)
        assertTrue("下载那一枚不再是整行主按钮：$button", button.contains("onClick = onDownload"))
        assertTrue("主按钮没吃 saving 那道闸（进行中还能再按一次）：$button", button.contains("enabled = !saving"))
        assertTrue("主按钮没吃满整行：$button", button.contains(".fillMaxWidth()"))
        assertTrue("主按钮的标签不再跟着内容走（视频该念「下载视频」）：$button", button.contains("if (isVideo) \"下载视频\" else \"下载原图\""))
        assertTrue(
            "主按钮的字与图标没显式取色 —— miuix 那颗 Button 不把内容色喂给 LocalContentColor，" +
                "真机读数是一枚浅蓝实底配白字（2026-10-14 截图量到，本批自己写出来又自己看到的）：$button",
            button.contains("color = tokens.color.onPrimary"),
        )
        // 对照②：水彩这一格真的还摆着，而不是"因为整块被删了所以发丝线也没多"。
        assertEquals("GallerySheetAction 里应当只有那一枚笔刷图标", 1, occurrences(sheet, "Icons.Outlined.Brush"))
        assertTrue("水彩入口的文案不见了", sheet.contains("\"画成水彩 Beta\""))
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
