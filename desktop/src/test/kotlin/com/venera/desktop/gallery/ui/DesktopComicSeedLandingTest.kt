package com.venera.desktop.gallery.ui

import com.venera.desktop.ForensicFlags
import com.venera.desktop.ForensicFlags.ForensicLanding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 80 · `--comic=` 那颗种子的**接管与落空**（纯函数层）。
 *
 * ## 这一颗为什么必须存在（不是补测试，是补一次真故障）
 *
 * 第一次起窗拍到的一帧是这样：`D_落点 域=图库(启动) 行=每日热门 … 详情=开(启动)`，
 * 而屏上根本没有详情 —— 详情那一屏只在「漫画」域里才组合，`D_详情` 一个字都没念。
 * **读数与屏上不一致**就是谎，而这枚种子的判读层、消费点、`D_落点` 那一格全都"看着接好了"。
 *
 * 为什么做成纯函数而不写在 `main()` 里：`main()` 要建窗口要数据目录，测试碰不到；
 * 留在里面就只剩文本锚点，而文本锚点**看不见死分支**（把接管改成恒不接管，屏上落回图库，
 * 而"main() 里出现过 DesktopDomain.COMIC"那一条照样绿）—— 同一族坑手册 §0 记过两次。
 */
class DesktopComicSeedLandingTest {

    private val gallery = ForensicLanding(DesktopDomain.GALLERY, "gallery-daily")
    private val comic = DesktopComicTarget("1480597", "1480597", "")

    @Test
    fun 种子给了就把起始域接到漫画而把行交回默认() {
        val landed = ForensicFlags.comicLanding(comic, gallery, takenOverByOther = false)
        assertEquals("域没跟过去 ⇒ 那一屏根本没组合，而读数会说谎", DesktopDomain.COMIC, landed.domain)
        // 行交回 null 而不是带着上一域的键：漫画域第一行就是「探索」，
        // 而"gallery-daily"种进漫画域对不上任何东西（那一格 sectionNote 会照实念）。
        assertNull(landed.section)
    }

    @Test
    fun 没给种子时一字不改() {
        val landed = ForensicFlags.comicLanding(null, gallery, takenOverByOther = false)
        assertEquals(gallery.domain, landed.domain)
        assertEquals(gallery.section, landed.section)
    }

    @Test
    fun 被别的种子抢走时不许偷偷改用户落点() {
        val landed = ForensicFlags.comicLanding(comic, gallery, takenOverByOther = true)
        assertEquals("两枚同给而赢的是那一枚时，这一枚不许把用户从图库拽走", gallery.domain, landed.domain)
        assertEquals(gallery.section, landed.section)
    }

    @Test
    fun 三档落空各念一句而接管那一档念的是结果() {
        assertNull(ForensicFlags.comicNote(null, gallery, false))
        val stolen = ForensicFlags.comicNote(comic, gallery, takenOverByOther = true)!!
        assertTrue("抢走那一句要点名赢的是谁：$stolen", stolen.contains("--artist") && stolen.contains("--ranking"))
        assertTrue("抢走那一句要说清本轮不进详情屏", stolen.contains("不进详情屏"))
        // 正面那一档念的是**结果**（域真跟过去了）而不是请求（参数打了什么）
        val took = ForensicFlags.comicNote(comic, ForensicLanding(DesktopDomain.COMIC, null), false)!!
        assertTrue("接管那一句要带 D_ 前缀才进得了读数那把尺子：$took", took.startsWith("D_"))
        assertTrue(took.contains(DesktopDomain.COMIC.title))
    }

    @Test
    fun 用户已点名漫画域某一行时接管不许吃掉那一行() {
        // 批 82.7 的量具那一半：要拍出"载荷设上了而那一屏根本没换"这一格，必须能把
        // 行=阅读历史 与 详情=开 同时种进去。旧写法把行一律交回 null（=第一行「探索」），
        // 于是**唯一能拍出这一格的路径被量具自己堵死**，而详情只在探索那一行里组合 —— 缺陷看不见。
        val landed = ForensicFlags.comicLanding(
            comic, ForensicLanding(DesktopDomain.COMIC, DesktopComicSectionKey.HISTORY), takenOverByOther = false
        )
        assertEquals(DesktopDomain.COMIC, landed.domain)
        assertEquals("用户点名的那一行必须留着", DesktopComicSectionKey.HISTORY, landed.section)
    }

    @Test
    fun 上一域的键带进漫画域时原样交回而不静默() {
        // 对照组：这一枚不替用户"猜"一行。对不上任何一行时根上 `?: rows.first()` 落回第一行，
        // 而那一格由 `sectionNote` 照实念出来。
        val landed = ForensicFlags.comicLanding(
            comic, ForensicLanding(DesktopDomain.COMIC, "gallery-daily"), takenOverByOther = false
        )
        assertEquals("gallery-daily", landed.section)
    }

    @Test
    fun 接管那一句念得出行留在哪一格() {
        val stayed = ForensicFlags.comicNote(
            comic, ForensicLanding(DesktopDomain.COMIC, DesktopComicSectionKey.HISTORY), takenOverByOther = false
        )!!
        // 行名从行表现取（不写死）：改名时这句跟着变，而不会替一屏已经不存在的行作伪证。
        val rowTitle = desktopDomainRows(DesktopDomain.COMIC).first { it.key == DesktopComicSectionKey.HISTORY }.title
        assertTrue("行留着那一档要念得出是哪一行：$stayed", stayed.contains("行留在「$rowTitle」"))
        val fell = ForensicFlags.comicNote(comic, ForensicLanding(DesktopDomain.COMIC, null), false)!!
        assertTrue("没点名行时念的是「交回第一行」：$fell", fell.contains("行交回第一行"))
        assertTrue("两档必须是两句话（合成一句就定不了行是谁定的）", stayed != fell)
    }

    @Test
    fun 认得出而域没跟过去时那句不许与接管那句同形() {
        // 这一档是"接线断在半路"的形状：种子给了、没被抢、而落点不在漫画域
        val line = ForensicFlags.comicNote(comic, gallery, takenOverByOther = false)!!
        assertTrue("那一句要念得出落在哪一域：$line", line.contains(DesktopDomain.GALLERY.title))
        assertTrue(line.contains("不进详情屏"))
        assertTrue("三档里被抢走与没接管必须是两句话（合成一句就定不了因）", line != ForensicFlags.comicNote(comic, gallery, true))
    }
}
