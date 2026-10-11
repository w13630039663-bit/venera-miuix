package com.venera.desktop.gallery.ui

import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 批 81 · 阅读那一屏的口径（纯判据）。
 *
 * ## 为什么每一枚数都要回读对岸那一行
 *
 * 手册里"观感尺寸用现成口径"那一条的桌面版：这一屏的间距、每页的缩放、列表 `key` 的形状
 * 在 Android 侧全有现成值，桌面**不许另造**。而"抄了"这件事只有回读对岸源码那一行才量得到 ——
 * 对岸把默认值从 8 改成 12 而这里没跟上时，红的是判据而不是屏上那点缝。
 *
 * ## `key` 为什么要有判据
 *
 * 对岸那枚 `key = { index, _ -> "v-$currentChapterIndex-$index" }` 的作用是**换话时不复用旧位**。
 * 桌面少一档：占位章的话号是空串，直接拼会得到 `"v--3"` 这种跟另一本的 `"v--3"` 撞车的键 ——
 * 撞车后 LazyColumn 会把上一话的位当成同一项复用，屏上表现为"换了一页还是上一页那张"。
 */
class DesktopComicReaderTest {

    @Test
    fun 每页间距用的是对岸那枚默认值而不是本批自定() {
        val appLine = DesktopSourceTree.androidSource("data/prefs/VeneraPreferences.kt")
            .readLines().first { it.contains("KEY_PAGE_GAP_DP,") }
        val counterpartDefault = Regex("""KEY_PAGE_GAP_DP,\s*(\d+)f""").find(appLine)!!
            .groupValues[1].toInt()
        assertEquals(
            "间距必须与对岸 `prefs.pageGapDp` 的默认值同值（对岸那行：${appLine.trim()}）",
            counterpartDefault,
            DESKTOP_READER_PAGE_GAP_DP,
        )
        assertNotEquals("不许顺手把 0 当成「没有间距」——那是对岸的可改档，不是桌面的口径", 0, DESKTOP_READER_PAGE_GAP_DP)
    }

    @Test
    fun 列表键有话号时带话号没有时退成漫画号() {
        assertEquals("v-g-0", desktopReaderItemKey("g", "1461316", 0))
        assertEquals("v-g-14", desktopReaderItemKey("g", "1461316", 14))
        // 占位章：话号是空串 ⇒ 退成漫画号，绝不留成 "v--3"（会与另一本的占位章撞车）
        assertEquals("v-1461316-3", desktopReaderItemKey("", "1461316", 3))
        assertEquals("v-1461316-3", desktopReaderItemKey("   ", "1461316", 3))
        assertNotEquals(
            "两本不同漫画的占位章第 3 页不许同一个键",
            desktopReaderItemKey("", "1461316", 3),
            desktopReaderItemKey("", "9", 3),
        )
    }

    @Test
    fun 读数一行说清是哪一话几页() {
        val reading = DesktopComicReading(
            comicId = "1461316", title = "某本", episodeId = "g",
            episodeName = "第 1 话", placeholder = false, episodeIndex = 0, startPage = 0, coverUrl = "",
        )
        assertEquals(
            "D_阅读 源=jm 号=1461316 话=g 起点=0 态=上屏 页=15",
            desktopComicReaderReading(reading, "jm", ComicReaderState.Ready(List(15) { "p$it" })),
        )
        assertEquals(
            "D_阅读 源=jm 号=1461316 话=g 起点=0 态=等回话",
            desktopComicReaderReading(reading, "jm", ComicReaderState.Loading),
        )
        assertEquals(
            "D_阅读 源=jm 号=1461316 话=g 起点=0 态=没成 因=IllegalStateException: 取页失败 http=403",
            desktopComicReaderReading(
                reading,
                "jm",
                ComicReaderState.Failed("IllegalStateException: 取页失败 http=403"),
            ),
        )
        // 起点那一格吃的是载荷的值，写死或漏念都会在这里红：换一种值再喂一次。
        val seeded = reading.copy(startPage = 7)
        assertTrue(
            "读数没念出载荷里那个起点（`--read-page=` 那颗种子在屏上就没影了）",
            desktopComicReaderReading(seeded, "jm", ComicReaderState.Ready(List(15) { "p$it" }))
                .contains("起点=7"),
        )
    }

    @Test
    fun 回了零页要念成零页而不是折成成功() {
        // 「整本翻不出页」那条老缺陷的形状就是"成功但零页"。读数不许把它写成 `态=上屏 页=0` 之外的美话。
        val reading = DesktopComicReading("9", "某本", "", "整部", placeholder = true, episodeIndex = 0, startPage = 0, coverUrl = "")
        val line = desktopComicReaderReading(reading, "jm", ComicReaderState.Ready(emptyList()))
        assertTrue("零页也必须照实念页=0（念成别的就等于替源站撒谎）：$line", line.contains("页=0"))
        assertTrue("零页不许改口念成成功之外的第二句：$line", !line.contains("因="))
    }

    @Test
    fun 失败原话压成一行且不超上限() {
        val reading = DesktopComicReading("9", "某本", "g", "第 1 话", placeholder = false, episodeIndex = 2, startPage = 0, coverUrl = "")
        val multiline = ComicReaderState.Failed("第一行\n第二行\r\n第三行")
        val line = desktopComicReaderReading(reading, "jm", multiline)
        assertEquals("读数必须一行（两行会把证据送到抓读数脚本看不见的地方）", 1, line.lines().size)
        val long = desktopComicReaderReading(reading, "jm", ComicReaderState.Failed("x".repeat(900)))
        assertTrue("原话要截断（站方 stack 能把一行拉到几千字）：${long.length}", long.length <= 300)
    }

    @Test
    fun 没有分话的那一章念的是详情同一句() {
        assertEquals("整部（这一本没有分话）", desktopReaderEpisodeLabel("", placeholder = true, episodeId = "9"))
        assertEquals("整部（这一本没有分话）", desktopReaderEpisodeLabel("   ", placeholder = true, episodeId = "9"))
        assertEquals("第 1 话", desktopReaderEpisodeLabel("第 1 话", placeholder = false, episodeId = "g"))
    }

    /**
     * 章名空而**不是**占位章 —— 这一档是 `--read=` 那颗种子造出来的：它只带话号，章名我们并不晓得。
     *
     * 第一版在这里念了「整部（这一本没有分话）」，而图证那一帧当场把它抓成假话：
     * 那本漫画有 15 话（`D_详情 … 话=15`），屏上却说"这一本没有分话"。
     * 不知道章名就念话号 —— 替源站说一句它没说过的话是本仓的一号忌。
     */
    @Test
    fun 章名不晓得时念话号而不是替源站说没有分话() {
        assertEquals("话 1461318", desktopReaderEpisodeLabel("", placeholder = false, episodeId = "1461318"))
        assertNotEquals(
            "非占位章绝不许念成「整部…」",
            "整部（这一本没有分话）",
            desktopReaderEpisodeLabel("", placeholder = false, episodeId = "g"),
        )
        // 两样都没有（话号也空）时不摆那一格而不是编一句：空串由调用方按 null 处理
        assertEquals("", desktopReaderEpisodeLabel("", placeholder = false, episodeId = ""))
    }

    @Test
    fun 阅读载荷从详情载荷与章节推出() {
        val target = DesktopComicTarget("1461316", "卡上题名", "https://card.jpg")
        val episode = EngineSession.Episode("第 2 话", "h")
        val reading = desktopComicReadingOf(target, episode, episodeIndex = 1, startPage = 0, detailCover = "https://detail.jpg")
        assertEquals("1461316", reading.comicId)
        assertEquals("卡上题名", reading.title)
        assertEquals("h", reading.episodeId)
        assertEquals("第 2 话", reading.episodeName)
        // 行号是调用点给的，这颗件只许原样带过去。两种值各喂一次：只喂 1 的话"恒 1"也能过。
        assertEquals(1, reading.episodeIndex)
        assertEquals(
            "换一行就该换成另一档",
            7,
            desktopComicReadingOf(target, episode, episodeIndex = 7, startPage = 0, detailCover = "").episodeIndex,
        )
        // 起点页同样是调用点给的（批 82.5）：这一颗件只许原样带，两种值各喂一次。
        assertEquals(0, reading.startPage)
        assertEquals(
            "换一页就该换成另一档",
            4,
            desktopComicReadingOf(target, episode, episodeIndex = 1, startPage = 4, detailCover = "").startPage,
        )
        // 封面那一格在拼载荷那一刻就算好（批 82.6）：两种值各喂一次，"详情优先"与"入口兜底"才分得开。
        assertEquals(
            "详情回了封面就用详情那张（这一颗件的调用点不该自己再算一遍）",
            "https://detail.jpg",
            reading.coverUrl,
        )
        assertEquals(
            "详情没给 ⇒ 落入口那枚（对岸那句 `ifBlank`）",
            "https://card.jpg",
            desktopComicReadingOf(target, episode, episodeIndex = 1, startPage = 0, detailCover = "").coverUrl,
        )
    }
}
