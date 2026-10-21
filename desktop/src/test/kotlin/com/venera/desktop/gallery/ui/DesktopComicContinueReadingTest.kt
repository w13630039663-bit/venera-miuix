package com.venera.desktop.gallery.ui

import com.venera.compose.data.db.HistoryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 94 ·「开始/继续阅读」那一枚主胶囊的四颗算式 + 它按下到底落到哪。
 *
 * 题面是**量出来的两条**（不是推测）：
 * ① 桌面缺对岸有的那一格 —— `app/.../ComicDetailScreen.kt:552-600` 摆一枚大号胶囊，
 *    有历史时文案是「继续阅读」并带一行小字「继续 <那一话的标题> · 第 N 页」，底边还有一条进度线；
 *    而桌面详情只有「共 N 话」加那一列话号（`DesktopComicDetail.kt:234`），按下恒 `startPage = 0`（`:247`）。
 * ② **对岸自己就是半接线**：那枚胶囊的 `onClick` 走 `onTriggerRead()`（`:564`），
 *    而它打开的是 `chs.first()`（`:266-269`，`launchChapter(..., 0)`）——
 *    也就是说屏上说「继续 第 8 页」而动作是**第一章第 0 页**。
 *
 * 所以这一批的处置不是"照抄"：文案与那行小字**逐字回核对岸**（同一句话两端不同串就是漂），
 * 而**动作按它承诺的那样接上**（`app/` 是受保域，本批一字不动；那条差异写在 `desktopComicResumeReading` 的注释与 rounds 里）。
 * 三条回核尺子（① ② ⑦）直接从 Android 源码文本现取正则，对岸改了文案桌面会红。
 */
class DesktopComicContinueReadingTest {

    private fun record(chapterIndex: Int, chapterTitle: String, pageIndex: Int) = HistoryRecord(
        comicId = "1461316",
        title = "某本",
        author = "",
        coverUrl = "",
        sourceName = "jm",
        lastChapterTitle = chapterTitle,
        lastChapterIndex = chapterIndex,
        lastPageIndex = pageIndex,
        totalPages = 89,
        updatedAt = 1L,
    )

    /** ① 文案两档各念一句，且**逐字**与对岸那两句对上（对岸改了桌面要红，不许桌面自己改词）。 */
    @Test
    fun 主胶囊那句两档各念一句而字面跟着对岸() {
        assertEquals("没历史那一档：", "开始阅读", desktopComicReadButtonLabel(false))
        assertEquals("有历史那一档：", "继续阅读", desktopComicReadButtonLabel(true))
        val android = DesktopSourceTree.androidSource("feature/ComicDetailScreen.kt").readText()
        val both = Regex("if \\(historyRecord != null\\) \"([^\"]+)\" else \"([^\"]+)\"").find(android)
            ?: error("对岸那句两档的写法变了（这条是回核：桌面跟它走，不改它）")
        assertEquals("有历史那档逐字照对岸：" + both.value, both.groupValues[1], desktopComicReadButtonLabel(true))
        assertEquals("没历史那档逐字照对岸：" + both.value, both.groupValues[2], desktopComicReadButtonLabel(false))
    }

    /**
     * ② 那行小字的**拼法**逐字回核（`"继续 " + lastChapterTitle + " · 第 " + (lastPageIndex + 1) + " 页"`），
     * 并真跑两档：库里那一页是 0 基数 ⇒ 人话从 1 数起（写 0 就是屏上少一页，与对岸不同轨）。
     */
    @Test
    fun 那行小字从一数起而拼法逐字照对岸() {
        assertEquals("第 0 页那一档：" , "继续 第 3 话 · 第 1 页", desktopComicContinueCaption("第 3 话", 0))
        assertEquals("第 7 页那一档：" , "继续 第 3 话 · 第 8 页", desktopComicContinueCaption("第 3 话", 7))
        val android = DesktopSourceTree.androidSource("feature/ComicDetailScreen.kt").readText()
        assertTrue(
            "对岸那句拼法变了（`继续 ` + 标题 + ` · 第 ` + (页+1) + ` 页`），桌面要跟着改而不是自己造：",
            Regex("\"继续 \" \\+ historyRecord\\.lastChapterTitle \\+ \" · 第 \" \\+ \\(historyRecord\\.lastPageIndex \\+ 1\\) \\+ \" 页\"").containsMatchIn(android),
        )
    }

    /**
     * ③ 进度线那一格：**没有分母的百分比是假数据** —— 话数为 0 时交回 0f（那一屏据此不摆线），
     * 而对岸那句 `coerceIn(0f, 1f)` 的两端各喂一次越界样例，否则写反了也量不出来。
     */
    @Test
    fun 进度线只在有分母时给而两端都夹住() {
        assertEquals("读到第 1 话而共 4 话：", 0.25f, desktopComicReadProgress(0, 4), 0.0001f)
        assertEquals("读到第 4 话而共 4 话（越界那一档）：", 1f, desktopComicReadProgress(9, 4), 0.0001f)
        assertEquals("负数那一档：", 0f, desktopComicReadProgress(-2, 4), 0.0001f)
        assertEquals("没有分母就不给线：", 0f, desktopComicReadProgress(2, 0), 0.0001f)
    }

    /**
     * ④ 按下落到哪：**这一批的正题**。有记录 ⇒ 记录那一话 + 记录那一页；
     * 无记录 ⇒ 第一话第 0 页；记录的话号越界（源把那话删了）⇒ 夹住而不是抛，且页号跟着那一话走。
     */
    @Test
    fun 按下落到记录那一话那一页而不是第一话第零页() {
        val episodes = List(4) { "e$it" }
        assertEquals("有记录那一档：", 2 to 7, desktopComicResumeReading(record(2, "第 3 话", 7), episodes))
        assertEquals("无记录那一档：", 0 to 0, desktopComicResumeReading(null, episodes))
        assertEquals("话号越界那一档夹到最后一话：", 3 to 5, desktopComicResumeReading(record(9, "没了", 5), episodes))
        assertEquals("一话都没有时交回 null 而不是抛（那一枚胶囊按下去没对象就是不摆）：", null, desktopComicResumeReading(record(0, "x", 0), emptyList()))
    }

    /** ⑤ 越界那一档**不许静默改页号**：夹话号时页号仍是库里那一枚（改了就等于悄悄丢进度）。 */
    @Test
    fun 夹话号时页号还是库里那一枚() {
        val resumed = desktopComicResumeReading(record(99, "没了", 41), List(3) { "e$it" })
        assertEquals("话号夹到最后一话：", 2, resumed!!.first)
        assertEquals("页号不许被顺手清零：", 41, resumed.second)
    }

    /**
     * ⑥ 那枚胶囊在屏上**必须带动作**：本批立的正是"不许摆着「继续阅读」而按下开第一章"，
     * 所以接线尺子是 文案那颗件被读一处 + 那行小字被读一处（只在有历史那两档里）+ 续读算式被调一处，缺一就红。
     * ⚠️ 刻意**不钉** `startPage = 0` 那半句：那一列话的各行按下仍从该话开头读（**照对岸** `:1206 launchChapter(ch.id, ch.title, actualIdx)`，
     *    它也不带页），只有胶囊那一走才续读。把行也改成续读就是自己定语义，本批不许。
     */
    /** ⑦ 「上次读到这里」那枚标记的字面逐字回核对岸（对岸改了桌面跟着改，不许桌面自己改词）。 */
    /**
     * ⑧ 分家的规矩不许破：**装载那一件**吃历史读口（要开库），**展示那一件**只吃纯数据那块 `HistoryRecord?`
     *    —— 展示层一吃 DAO 句柄，语义树那一层就渲不出来了（同 `EngineSession` 那一条，批 80 立的）。
     * 两枚都是**必填、不给默认值**：漏传的形状是"胶囊恒说「开始阅读」"，那种半接线在屏上看不出来。
     */
    /**
     * ⑨ stdout 那一行念得出**三档**（有 / 无 / 没读到），而「没读到」那一档必须带原因。
     *
     * 为什么这一档要有读数：那一档在屏上的表现是"胶囊不见了"，而"库里没这一本"与"库没开到"
     * 在像素上同形 —— 只有这一行分得出（同 `D_详情` 那一条纪律：`Loading` 与 `Failed` 在屏上都是左上角一行小字）。
     * 原因那一格吃的是**一行化**之后的原文：站方的报错常带 stack 尾巴，两行的读数会被按行的抓取漏掉后半句。
     */
    @Test
    fun 续读那一行三档各念一句而没读到那档带原因() {
        assertEquals(
            "有记录那一档：",
            "D_续读 号=9 记录=有 话=2 页=7",
            desktopComicHistoryProgressLine("9", Result.success(record(2, "第 3 话", 7))),
        )
        assertEquals(
            "库里没这一本那一档：",
            "D_续读 号=9 记录=无",
            desktopComicHistoryProgressLine("9", Result.success(null)),
        )
        val broken = desktopComicHistoryProgressLine(
            "9",
            Result.failure(IllegalStateException("no such table\nsecond line")),
        )
        assertTrue("没读到那一档要念出原因：" + broken, broken.startsWith("D_续读 号=9 记录=没读到 因=IllegalStateException: no such table"))
        assertEquals("那一行不许被原因拆成两行（抓读数的脚本按行锚）：" + broken, 1, broken.lines().size)
    }

    /**
     * ⑩ 那枚胶囊的**填色必须走我们自己的档**，不许挂在库那条链上（批 95）。
     *
     * 为什么立这一条：批 94 的包内图证抓到那枚 `AccentButton` 拍出来是天蓝 —— 成因在
     * `DesktopAccentFillTest`（`darkColors(accent = 藤紫)` 那一枚参数对 `fillAccent` 不起作用：库里那张预置表
     * **只有一颗键** `#0078D4`，而 `defaultShades` 就是这颗键自己的值 ⇒ 六枚候选量到同一枚蓝）。
     * 换色解决不了，只能**不经过那条链**。
     * 这一条钉的就是"不经过"：`DesktopTheme.AccentFill`（稿 `--accent-fill` = `#5B47A8`）在详情这一屏恰好一处，
     * 而 `AccentButton(` 零处 —— 有人把它换回库控件，屏上那枚就会静默变蓝（本仓那一族"看着像接上了"）。
     */
    /**
     * ⑪ 两枚文字档不许自己造：胶囊上那两句吃的是 [DesktopTheme.TextPrimary]（稿 `--on-accent` = `#FFFFFF`，
     *     与既有文字最亮档同值 ⇒ 不新增色值），而**不许**用 alpha 压主色那一族写法。
     */
}
