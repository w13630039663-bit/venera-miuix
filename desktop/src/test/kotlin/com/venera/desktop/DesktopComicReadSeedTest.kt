package com.venera.desktop

import com.venera.desktop.ForensicFlags.ForensicLanding
import com.venera.desktop.gallery.ui.DesktopDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 81 · `--read=` 那颗种子（纯判据）。
 *
 * ## 为什么这一批**必须**有取证种子
 *
 * 阅读那一屏的入口是详情里的一行，而 agent 侧不许动鼠标（手册 §8）⇒ 没有它这一屏只能记成"没拍成"。
 * 批 80 的量具账里那条"漏了 `--comic` 到 `:desktop:app` 那一跳"的坑在这一批同样会犯，
 * 所以判据 ⑥ 直接扫 `build.gradle.kts` 里那一行 `gradleProperty`（第七次踩同一格，别再量一遍）。
 */
class DesktopComicReadSeedTest {

    @Test
    fun 两截时号与话号各归各() {
        val reading = ForensicFlags.read(listOf("--read=1461316/g"), startPage = null)
        assertNotNull(reading)
        assertEquals("1461316", reading!!.comicId)
        assertEquals("g", reading.episodeId)
        assertEquals("给了话号就不是占位章（占位章取页时要把话号还原成 null）", false, reading.placeholder)
        assertEquals("没给题名时用号充题名（与 --comic 同一条口径）", "1461316", reading.title)
        assertEquals("没给章名就是空串，屏上退成「整部…」那一句", "", reading.episodeName)
    }

    @Test
    fun 只给号时是占位章() {
        val reading = ForensicFlags.read(listOf("--read=1461316"), startPage = null)!!
        assertEquals("", reading.episodeId)
        assertTrue("整本就一章那一类必须标成占位章，否则取页会拿着空串去问 loadEp", reading.placeholder)
    }

    @Test
    fun 三截时第三截是章名() {
        val reading = ForensicFlags.read(listOf("--read=1461316/g/第 2 话"), startPage = null)!!
        assertEquals("g", reading.episodeId)
        assertEquals("第 2 话", reading.episodeName)
    }

    @Test
    fun 号段空是打错了而不是没给() {
        assertNull(ForensicFlags.read(listOf("--read=/g"), startPage = null))
        assertNull(ForensicFlags.read(listOf("--read="), startPage = null))
        // 没给这个参数：不念、不报（与"给了而空"必须分开）
        assertNull(ForensicFlags.read(listOf("--comic=1"), startPage = null))
    }

    @Test
    fun 种子把起始域带到漫画域() {
        val seed = ForensicFlags.read(listOf("--read=9/g"), startPage = null)!!
        val landing = ForensicLanding(DesktopDomain.GALLERY, null)
        assertEquals(DesktopDomain.COMIC, ForensicFlags.readLanding(seed, landing, takenOverByOther = false).domain)
        // 被 --artist / --ranking 抢走时**一字不改**用户的落点
        assertEquals(
            DesktopDomain.GALLERY,
            ForensicFlags.readLanding(seed, landing, takenOverByOther = true).domain,
        )
        assertNull("没给种子时不许改落点", ForensicFlags.readLanding(null, landing, takenOverByOther = false).takeIf { it != landing })
    }

    @Test
    fun 三档各念一句而正面那句念的是结果() {
        val seed = ForensicFlags.read(listOf("--read=9/g"), startPage = null)!!
        val landed = ForensicLanding(DesktopDomain.COMIC, null)
        assertNull(ForensicFlags.readNote(null, landed, takenOverByOther = false))
        assertTrue(
            "被抢走那一句要点名赢家并说清本轮不进阅读屏",
            ForensicFlags.readNote(seed, landed, takenOverByOther = true)!!.contains("本轮不进阅读屏"),
        )
        assertTrue(
            "真接管那一句念的是**结果**（起始域跟着落到了哪一域）",
            ForensicFlags.readNote(seed, landed, takenOverByOther = false)!!.contains("起始域"),
        )
    }

    @Test
    fun 参数登记与到app那一跳都在() {
        // ① 漏登记 ⇒ 会被 unknown 报成拼错（而参数其实是对的）
        assertTrue("--read 没登记进 KNOWN，会被念成拼错", ForensicFlags.KNOWN.contains("--read"))
        // ② gradle 那一跳：`-Pread` 不接进 gradleProperty，参数打进去了而进程根本收不到（第七次踩这一格）
        val gradle = java.io.File("build.gradle.kts").readText()
        assertTrue(":desktop:app 没有把 -Pread 接成 --read（种子到不了进程）", gradle.contains("gradleProperty(\"read\")"))
    }
}
