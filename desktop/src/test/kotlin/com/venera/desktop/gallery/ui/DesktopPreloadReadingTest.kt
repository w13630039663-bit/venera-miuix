package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryPreloadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预取那一族要念得出**排了几张、为什么一张都没有、有没有收手**（批 56 起，批 57 补"为什么"）。
 *
 * ## 这一格补的是什么
 *
 * 那枚开关（`galleryPreload` 三档）屏上能改（批 47 接上消费点）、批 49 又教会它"发 → 等取消 → 停手"，
 * 可批 55 之前 stdout 上关于它的字是**零**。批 56 把两行读数接上后，剩下的是「排=0」那一句：
 * 它当时只说"这一轮一笔都不发"，而**四种因在屏上同形** —— 档设成了不预取、前后没有那几张了（翻到底 / 墙是空的）、
 * 前后那几张被分级挡住、站方没给可取的地址。这正是「没取到 / 没有 / 没接」那族：
 * 少说一种因，就会有人拿"它坏了"去查一条本来按设计没动的路。
 *
 * ## 口径
 *
 * 1. **一次一行**：`LaunchedEffect` 每换页/换档/换模式重开一次，就念一行。刷屏与静默一样坏。
 * 2. **因由账本出**：计划几张、挡几张、无址几张都住在 `desktopPreloadPlan` 那颗件**同一次**过滤里 ——
 *    读数不许自己再走一遍那三道 filter（两条轨那一族：各写一遍就会漂）。
 * 3. **收手那行必须在 `dispose()` 之后**：先念"收手=3"再停手，就是拿请求当结果（批 49 钉过顺序）。
 */
class DesktopPreloadReadingTest {

    private fun card(
        id: Long,
        masked: Boolean = false,
        preview: String = "https://cdn/p$id.jpg",
        large: String? = null,
    ) = DesktopWallCard(
        post = GalleryPost(site = GallerySite.YANDERE, id = id, previewUrl = preview, largeUrl = large ?: ""),
        masked = masked,
    )

    // ① 发那一行：三档各念得出档、张数与吃的是哪一档。
    @Test
    fun `发那一行三档各一句`() {
        val cards = (1L..6L).map { card(it) }
        val lines = GalleryPreloadMode.entries.map {
            desktopPreloadReading(it, desktopPreloadPlan(it, 2, cards, DesktopDetailTier.PREVIEW), DesktopDetailTier.PREVIEW)
        }
        assertEquals("三档各一行：" + lines.size, 3, lines.size)
        assertEquals("有两行撞在一起：" + lines, lines.size, lines.distinct().size)
        assertTrue("每行都要念得出档：" + lines, lines.all { it.contains("PREVIEW") })
        assertTrue("前缀要能被 grep 挑出来：" + lines, lines.all { it.startsWith("D_预取") })
        assertTrue("NEXT 那一行排 1 张：" + lines[1], lines[1].contains("排=1"))
    }

    // ② 零张那一句要跟着"为什么"，而"为什么"住在同一本账里。
    @Test
    fun `零张那一句说得出为什么没发`() {
        val off = desktopPreloadReading(
            GalleryPreloadMode.OFF,
            desktopPreloadPlan(GalleryPreloadMode.OFF, 0, listOf(card(1), card(2)), DesktopDetailTier.SAMPLE),
            DesktopDetailTier.SAMPLE,
        )
        println("PLR_$off")
        assertTrue("零张要有一句实话：" + off, off.contains("一笔都不发"))
        assertTrue("OFF 那一档要念成「设成了不预取」：" + off, off.contains("不预取"))
        assertFalse("OFF 不许念成「前后没有」（那是另一种缺席）：" + off, off.contains("前后没有"))
    }

    // ③ 收手那一行念得出停了几笔，且与发那一行是两个前缀形状（同一行混着念会把两件事念成一件）。
    @Test
    fun `收手那一行念得出停了几笔`() {
        val line = desktopPreloadStopReading(disposed = 4)
        println("PLR_$line")
        assertTrue("收手那行前缀不在：" + line, line.startsWith("D_预取"))
        assertTrue("停了几笔要念得出：" + line, line.contains("收手=4"))
    }

    // ④ 读数件结构上带不出地址：那颗纯函数的形参里就没有 URL（站方那一路常带键）。
    @Test
    fun `读数形参里没有地址`() {
        val policy = DesktopSourceTree.desktopUiSource("DesktopGalleryPreloadPolicy.kt")
        val code = DesktopSourceTree.codeText(policy)
        val reading = code.substring(code.indexOf("internal fun desktopPreloadReading"))
        val params = reading.substringAfter("(").substringBefore(")")
        assertFalse("读数形参里出现了 String（那一路会把带键的地址念进 stdout）：" + params, params.contains("String"))
        assertTrue(
            "档与那本账都得是现取的：" + params,
            params.contains("GalleryPreloadMode") && params.contains("DesktopPreloadPlan"),
        )
    }

    // ⑤ 那本账四枚都在：少一枚就有一种因永远念不出（读数只能报它拿得到的那几枚）。
    @Test
    fun `那本账四枚计数各在各的位`() {
        val maskedTwo = listOf(card(1, masked = true), card(2, masked = true))
        val byMask = desktopPreloadPlan(GalleryPreloadMode.NEXT, 0, maskedTwo, DesktopDetailTier.PREVIEW)
        assertEquals("计划里两张：" + byMask.wanted, 1, byMask.wanted)
        assertEquals("被挡两张：" + byMask.blocked, 1, byMask.blocked)
        assertEquals("无址零张：" + byMask.noUrl, 0, byMask.noUrl)
        assertEquals("发零张：" + byMask.urls, 0, byMask.urls.size)

        val noAddr = listOf(card(1, preview = "  "), card(2, preview = "  "))
        val byUrl = desktopPreloadPlan(GalleryPreloadMode.NEXT, 0, noAddr, DesktopDetailTier.PREVIEW)
        assertEquals("无址两张：" + byUrl.noUrl, 1, byUrl.noUrl)
        assertEquals("被挡零张：" + byUrl.blocked, 0, byUrl.blocked)

        // 翻到底：index 0 而墙上只有一张 ⇒ 计划本身是空的（这才叫"前后没有那几张"）
        val edge = desktopPreloadPlan(GalleryPreloadMode.NEXT, 0, listOf(card(1)), DesktopDetailTier.PREVIEW)
        assertEquals("计划零张：" + edge.wanted, 0, edge.wanted)
    }

    // ⑥⑦ 三种缺席各念各的句，两两不许混（同形就是"说了而没做"那一族）。
    @Test
    fun `三种缺席念三句话`() {
        // NEXT 从第 0 张起只向前取那一张 ⇒ 把"出事的那一张"放在 index 1，它才真在窗口里。
        val blocked = desktopPreloadReading(
            GalleryPreloadMode.NEXT,
            desktopPreloadPlan(GalleryPreloadMode.NEXT, 0, listOf(card(1), card(2, masked = true)), DesktopDetailTier.PREVIEW),
            DesktopDetailTier.PREVIEW,
        )
        val noAddr = desktopPreloadReading(
            GalleryPreloadMode.NEXT,
            desktopPreloadPlan(GalleryPreloadMode.NEXT, 0, listOf(card(1), card(2, preview = "  ")), DesktopDetailTier.PREVIEW),
            DesktopDetailTier.PREVIEW,
        )
        val edge = desktopPreloadReading(
            GalleryPreloadMode.BOTH_TWO,
            desktopPreloadPlan(GalleryPreloadMode.BOTH_TWO, 0, listOf(card(1)), DesktopDetailTier.PREVIEW),
            DesktopDetailTier.PREVIEW,
        )
        println("PLR_挡=$blocked")
        println("PLR_址=$noAddr")
        println("PLR_底=$edge")
        assertTrue("「排」数的是真发出去那几张，不是计划那几张：" + blocked, blocked.contains("排=0"))
        assertTrue("被挡那句要念得出挡了几张：" + blocked, blocked.contains("挡=1"))
        assertFalse("被挡那句不许带无址：" + blocked, blocked.contains("无址"))
        assertTrue("没地址那句要念得出无址几张：" + noAddr, noAddr.contains("无址=1"))
        assertFalse("没地址那句不许带挡：" + noAddr, noAddr.contains("挡="))
        assertTrue("翻到底那句要念得出「前后」：" + edge, edge.contains("前后没有"))
        val three = listOf(blocked, noAddr, edge)
        assertEquals("三句不许撞成两句：" + three, 3, three.distinct().size)
    }

    // ⑧ 混合因：挡一张、无址一张时两枚计数都要在（只报第一枚就是把第二种缺席藏起来）。
    @Test
    fun `两种缺席同时成立时两枚都念`() {
        val mixed = desktopPreloadReading(
            GalleryPreloadMode.BOTH_TWO,
            desktopPreloadPlan(
                GalleryPreloadMode.BOTH_TWO,
                0,
                listOf(card(1), card(2, masked = true), card(3, preview = "  ")),
                DesktopDetailTier.PREVIEW,
            ),
            DesktopDetailTier.PREVIEW,
        )
        println("PLR_混=$mixed")
        assertTrue("挡那张要在：" + mixed, mixed.contains("挡=1"))
        assertTrue("无址那张也要在：" + mixed, mixed.contains("无址=1"))
    }

    // ⑨ 接线两处，且**顺序**对：发那行在"空不空"判之前（空批也要念），收那行在 dispose 之后。
    @Test
    fun `两行读数各在其位`() {
        val app = DesktopSourceTree.desktopSourceOf("gallery/ui/VeneraDesktopApp.kt")
        val code = DesktopSourceTree.codeText(app)
        assertTrue(
            "发那一行没排在「空不空」之前（空批就静默了，OFF 与没接分不出）",
            code.indexOf("desktopPreloadReading(") in 0 until code.indexOf("urls.isEmpty()"),
        )
        assertTrue(
            "收手那一行排在 dispose 之前（先念后停是拿请求当结果）",
            code.indexOf("dispose()") in 0 until code.indexOf("desktopPreloadStopReading("),
        )
        // 对照组：这两行读的是真在被扫的那颗集成方文件，且那一本账只算一次。
        assertEquals("对照组失效：预取计划不在这颗文件里了", 1, DesktopSourceTree.codeOccurrences(app, "desktopPreloadPlan("))
    }

    // ⑪ 出口只许有这两颗：谁在调用点自己拼一句 `D_预取` 就是第二份口径（将来只改一处）。
    @Test
    fun `读数只许从两颗件里念`() {
        val app = DesktopSourceTree.desktopSourceOf("gallery/ui/VeneraDesktopApp.kt")
        assertEquals(
            "集成方自己拼了 `D_预取` 那句（该走读数件）",
            0,
            DesktopSourceTree.codeOccurrences(app, "\"D_预取"),
        )
        val printed = DesktopSourceTree.codeOccurrences(app, "println(desktopPreload")
        assertEquals("发与收两行读数没各走一处（现数出 $printed 处）", 2, printed)
    }
}
