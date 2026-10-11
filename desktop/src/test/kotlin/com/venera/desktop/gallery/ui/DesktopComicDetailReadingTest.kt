package com.venera.desktop.gallery.ui

import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 80 · 漫画详情那一屏的 stdout 读数：这一屏**开得成不成、几章、简介有没有**要说得出。
 *
 * 判据只吃那颗纯函数（`ComicDetailState` → 一行），不吃 composable：组合要起窗口，
 * 而"屏上到底动没动"这一格由图证负责（本批帧 `_qa/b80_frame_comic_detail.png`）。
 */
class DesktopComicDetailReadingTest {

    @Test
    fun `等回话那一格不许念成没成`() {
        val line = desktopComicDetailReading("123", "jm", ComicDetailState.Loading)
        assertEquals("D_详情 源=jm 号=123 态=等回话", line)
    }

    @Test
    fun `没成那一格念得出站方那句原话`() {
        val line = desktopComicDetailReading("123", "jm", ComicDetailState.Failed("Invalid Data"))
        assertEquals("D_详情 源=jm 号=123 态=没成 因=Invalid Data", line)
    }

    @Test
    fun `上屏那一格数得出章数而占位章也算一章`() {
        val one = ComicDetailState.Ready(
            title = "单本",
            description = "",
            episodes = listOf(EngineSession.Episode("", "123", placeholder = true)),
            cover = "",
        )
        val line = desktopComicDetailReading("123", "jm", one)
        // 单本（没有章节表的源）在屏上必须显示成一章，而不是"没有目录"——
        // 那一格从前是"整部翻不出页"的成因，读数跟着摆同一本账。
        assertEquals("D_详情 源=jm 号=123 态=上屏 话=1 简介=无 封面=无 话号=-", line)
    }

    @Test
    fun `封面那一格只念有无而不念那串地址`() {
        val given = ComicDetailState.Ready(
            title = "某本",
            description = "",
            episodes = listOf(EngineSession.Episode("1 话", "a")),
            cover = "https://cdn.example/signed/abc?t=token",
        )
        val line = desktopComicDetailReading("9", "jm", given)
        assertTrue("站方给了封面就要念得出给了：$line", line.contains("封面=有"))
        val blank = ComicDetailState.Ready(
            title = "某本",
            description = "",
            episodes = listOf(EngineSession.Episode("1 话", "a")),
            cover = "   ",
        )
        val blankLine = desktopComicDetailReading("9", "jm", blank)
        assertTrue("空白那串算「没给」（与简介那一格同一口径）：$blankLine", blankLine.contains("封面=无"))
        assertTrue(
            "读数里不许出现那串地址（签名参数、可选的凭据都住在里面；而这一行会被抄进 rounds 文档）：" + line,
            !line.contains("cdn.example") && !line.contains("token"),
        )
    }

    @Test
    fun `话号那一串封顶五枚而占位章念横线`() {
        // 批 81 补的取证那一格：上限类判据要喂"必然超"的那一串（§0 坑 ⑲），这里喂 9 枚。
        val many = (1..9).map { EngineSession.Episode("$it 话", "ep$it") }
        assertEquals("喂 9 枚必须只念前 5 枚（不封顶的话整本目录刷进 stdout）", "ep1,ep2,ep3,ep4,ep5", desktopComicEpisodeIds(many))
        assertEquals("占位章念 -（空串夹在两枚话号之间看不出来）", "-", desktopComicEpisodeIds(listOf(EngineSession.Episode("", "123", placeholder = true))))
        assertEquals("话号本身是空串也念 -", "-", desktopComicEpisodeIds(listOf(EngineSession.Episode("1 话", ""))))
        assertEquals("零章念空串而不是逗号", "", desktopComicEpisodeIds(emptyList()))
    }

    @Test
    fun `简介那一格数得出字数而不是有或没有`() {
        val ready = ComicDetailState.Ready(
            title = "某本",
            description = "第一行\n第二行",
            episodes = listOf(EngineSession.Episode("1 话", "a"), EngineSession.Episode("2 话", "b")),
            cover = "",
        )
        val line = desktopComicDetailReading("9", "goda", ready)
        // 「第一行」3 + 换行 1 + 「第二行」3 = 7 字（字数是 trim 后的 `length`，不是字节数）
        assertEquals("D_详情 源=goda 号=9 态=上屏 话=2 简介=7字 封面=无 话号=a,b", line)
    }

    @Test
    fun `空白简介算无简介而不许报成零字`() {
        val blank = ComicDetailState.Ready(
            title = "某本",
            description = "   ",
            episodes = listOf(EngineSession.Episode("1 话", "a")),
            cover = "",
        )
        // ⚠️ 从前这里写的是 `endsWith("简介=无")` —— 那钉的是"简介是最后一格"这一形状，
        // 而批 81 在尾巴上补了一格 `话号=` 就误红了（坑 ㉖ 的第七次，红在形状而不在语义）。
        // 这一条真正要防的是"空白简介被念成 0 字"，所以两头各钉一次。
        val line = desktopComicDetailReading("9", "jm", blank)
        assertTrue("空白简介要念「无」：$line", line.contains("简介=无"))
        assertTrue("空白简介不许念成 0 字：$line", !line.contains("简介=0字"))
    }

    @Test
    fun `读数里不许夹换行`() {
        // 站方的原话常带 stack 尾巴；一行读数被拆成两行，抓读数的脚本就会漏掉后半句
        //（同一族坑在失败那三枚原子里已经收过：desktopOneLineMessage）。
        val line = desktopComicDetailReading("9", "jm", ComicDetailState.Failed("a\nb"))
        assertFalse("那一行里不许有换行：${line.replace("\n", "\\n")}", line.contains("\n"))
        assertEquals("D_详情 源=jm 号=9 态=没成 因=a b", line)
    }
}
