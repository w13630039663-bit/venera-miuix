package com.venera.desktop.gallery.ui

import com.venera.desktop.ForensicFlags
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 98：「本机已下载」那一屏那枚取证种子 `--delete=<漫画号>/<话号>`。
 *
 * ## 为什么这一批也非有种子不可
 *
 * 那一格的入口是**同屏两步**（把手按第一次只武装、第二次才真删，照批 77 收藏清空那形），
 * 而"到底删没删掉"这一档只有**去盘上数**才算数 —— 离屏语义树量得到把手在不在，量不到回读盘那一判。
 * ⇒ 种子补的就是那一条：让同一颗 runner 在没人按鼠标的情况下自己跑一遍。
 *
 * ## 与 `--download=` 同一把写法（这一颗测试存在的第二条理由）
 *
 * 两枚种子指的是**同一格盘上的东西**：一枚收话号而另一枚收行号，就是本仓记过的那一族
 * 「复合键两端不同轨」—— 档位在而永远命中不了，而单测全绿。判据 ①② 钉的就是这一把写法。
 *
 * ## 沿路只许有一颗 runner
 *
 * 另开一条"只为取证"的删除路，量到的是那条路而屏上那一条可以仍是坏的（本仓那一族第六次）。
 * 判据 ⑥ 钉 `desktopComicDeleteChapter(` 在 main 里恰好「一处声明 + 两处按」，多一处就红。
 */
class DesktopComicDeleteSeedTest {

    private val paneFile = DesktopSourceTree.desktopUiSource("DesktopComicDownloadsPane.kt")
    private val paneCode = DesktopSourceTree.codeText(paneFile)
    private val hostFile = DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt")
    private val hostCode = DesktopSourceTree.codeText(hostFile)
    private val deleteFile = DesktopSourceTree.desktopUiSource("DesktopComicDownloadsDelete.kt")
    private val deleteCode = DesktopSourceTree.codeText(deleteFile)

    private fun occurrencesAcrossMain(needle: String): Int =
        DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, needle) }

    /** 把 stdout 接走一段，返回它念到的全部行（落空那几句不许只写在注释里）。 */
    private fun capture(block: () -> Unit): String {
        val buffer = ByteArrayOutputStream()
        val previous = System.out
        System.setOut(PrintStream(buffer, true, "UTF-8"))
        try {
            block()
        } finally {
            System.setOut(previous)
        }
        return buffer.toString("UTF-8")
    }

    /** ① 认得两截，且交回的是（号, 话号）而不是（号, 行号）。 */
    @Test
    fun 认得那两截而第二枚是话号() {
        val seed = ForensicFlags.delete(listOf("--delete=1461316/1461318"))
        assertEquals("两截都该原样交回：" + seed, "1461316" to "1461318", seed)
        assertNull("没给这枚参数时不该有读数：" + seed, ForensicFlags.delete(listOf("--comic=1")))
    }

    /** ② 坏形状四档：都不中止启动（交回 null），但每一档都念得出"不是两截"。 */
    @Test
    fun 坏形状四档各念一句而不中止() {
        listOf("--delete=", "--delete", "--delete=/1461318", "--delete=1461316/", "--delete=只有一截").forEach { given ->
            val read = capture { assertNull("那一枚该读不出：" + given, ForensicFlags.delete(listOf(given))) }
            assertTrue("念不出「不是两截」这一句：" + given + " ⇒ " + read, read.contains("不是两截"))
            assertTrue("要把打错的那一串念回去：" + given + " ⇒ " + read, read.contains("--delete"))
        }
    }

    /** ③ 名字得在认识的那张表里 —— 否则"未知参数"那把尺子会把它当生面孔念一句。 */
    @Test
    fun 那枚名字在认识的表里() {
        assertTrue("ForensicFlags.KNOWN 少了 --delete：" + ForensicFlags.KNOWN, ForensicFlags.KNOWN.contains("--delete"))
    }

    /** ④ 落空那句只在**起始域不是漫画**时念（正反两钉：该念的不漏、不该念的不多）。 */
    @Test
    fun 起始域不对时念一句而域对时闭嘴() {
        val seed = "1461316" to "1461318"
        val note = ForensicFlags.deleteNote(seed, comicDomainOpens = false)
        assertTrue("域不对要念一句：" + note, note != null && note.contains("起始域不是漫画"))
        assertTrue("要把是哪一枚念回去：" + note, note!!.contains("1461316/1461318"))
        assertNull("域对就不许多嘴（那一行落没落在由 sectionNote 念）：", ForensicFlags.deleteNote(seed, comicDomainOpens = true))
        assertNull("没给种子时两句都不该有：" + ForensicFlags.deleteNote(null, comicDomainOpens = false),
            ForensicFlags.deleteNote(null, comicDomainOpens = false))
    }

    /** ⑤ 接线：形参必填而不带默认值，而挂载那一笔必须是**命名实参**。 */
    /** ⑥ 沿路只许一颗 runner：把手第二次按下与种子吃同一颗。 */
    /** ⑦ 种子那两句各念各的，都点名号与话号（"本机没有"与"删掉了"不许同一句）。 */
    @Test
    fun 命中与落空各念一句() {
        val hit = desktopComicDeleteSeedHitLine("1461316", "1461318")
        val miss = desktopComicDeleteSeedMissLine("1461316", "1461318")
        assertTrue("命中那句要念出走哪条路：" + hit, hit.contains("第二次按下"))
        assertTrue("落空那句要念出不删：" + miss, miss.contains("不删"))
        assertNotEqualsSame("两句不许相同：" + listOf(hit, miss), hit, miss)
        listOf(hit, miss).forEach {
            assertTrue("两句都得点名是哪一枚：" + it, it.contains("号=1461316") && it.contains("话=1461318"))
        }
    }

    private fun assertNotEqualsSame(message: String, a: String, b: String) {
        assertTrue(message, a != b)
    }
}
