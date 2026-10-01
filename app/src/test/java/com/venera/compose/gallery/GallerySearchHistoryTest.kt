package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.decodeGallerySearchHistory
import com.venera.compose.gallery.domain.encodeGallerySearchHistory
import com.venera.compose.gallery.domain.pushGallerySearchHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 最近搜索历史的编解码（批次 K）。
 *
 * 为什么要改：一条历史存的是 `(站点, 标签串)`，去重键含站点 —— 于是同一个词在两站各占一行
 * （真机上就是 `setmen / Gelbooru` 与 `setmen / yande.re` 连着两行）。
 * 来源轴上了三档之后更明显：用「全部」搜一次就会写两行，历史区立刻被自己的重复撑满。
 *
 * 三条判据：
 * - **同一批标签算同一条**，与书写顺序无关（booru 的条件是 AND，顺序不影响结果集）；
 *   但**排除项算不同的串**（`a -b` 与 `a` 是两个结果集，合一条就是假读数）。
 * - **旧档不改写也要能长成一条两站的记录**（嗅探版本，v1 逐行读回后现场合并）。
 * - **认不出的站点整行丢掉**，不硬归某个站 —— 与 `GallerySearch.kt` 上原本那条注释同一条理由。
 */
class GallerySearchHistoryTest {

    private val both = setOf(GallerySite.YANDERE, GallerySite.GELBOORU)

    @Test
    fun `v1 档里同一串在两站各一行 读回来合成一条并带两站`() {
        val v1 = "gelbooru=setmen\nyandere=setmen"
        val out = decodeGallerySearchHistory(v1)
        assertEquals(1, out.size)
        assertEquals("setmen", out.single().query)
        assertEquals(both, out.single().sites)
    }

    @Test
    fun `v2 编再解逐条等值`() {
        val entries = listOf(
            GallerySearchEntry(both, "setmen"),
            GallerySearchEntry(setOf(GallerySite.YANDERE), "touhou -rating:explicit"),
            GallerySearchEntry(setOf(GallerySite.GELBOORU), "1girl"),
        )
        val raw = encodeGallerySearchHistory(entries)
        assertTrue("必须带版本哨兵，否则下次改动又只能靠猜", raw.startsWith("v2\n"))
        val back = decodeGallerySearchHistory(raw)
        assertEquals(entries.map { it.query to it.sites }, back.map { it.query to it.sites })
    }

    @Test
    fun `顺序不同但同一批标签算同一条历史`() {
        var list = pushGallerySearchHistory(emptyList(), GallerySearchEntry(GallerySite.YANDERE, "a b"))
        list = pushGallerySearchHistory(list, GallerySearchEntry(GallerySite.GELBOORU, "b a"))
        assertEquals(1, list.size)
        assertEquals(both, list.single().sites)
    }

    @Test
    fun `带排除项的串与不带的是两条历史`() {
        var list = pushGallerySearchHistory(emptyList(), GallerySearchEntry(GallerySite.YANDERE, "a"))
        list = pushGallerySearchHistory(list, GallerySearchEntry(GallerySite.YANDERE, "a -b"))
        // 排除掉一枚标签会换掉整个结果集，合成一条就是"我明明搜过带排除的那次"。
        assertEquals(2, list.size)
    }

    @Test
    fun `命中同一条时并入站集合并顶到最前`() {
        val seed = listOf(
            GallerySearchEntry(setOf(GallerySite.YANDERE), "older"),
            GallerySearchEntry(setOf(GallerySite.YANDERE), "setmen"),
        )
        val out = pushGallerySearchHistory(seed, GallerySearchEntry(setOf(GallerySite.GELBOORU), "setmen"))
        assertEquals(listOf("setmen", "older"), out.map { it.query })
        assertEquals(both, out.first().sites)
    }

    @Test
    fun `认不出站点的整行丢掉而不是硬归某站`() {
        val out = decodeGallerySearchHistory("pixiv=1girl\nyandere=ok")
        // 把一串 Pixiv 标签硬当成某个 booru 的写法，会得到一句"没有结果"—— 比少一条历史更坏。
        assertEquals(listOf("ok"), out.map { it.query })
        assertEquals(setOf(GallerySite.YANDERE), out.single().sites)
    }

    @Test
    fun `条数上限仍是二十条`() {
        val many = (1..30).map { GallerySearchEntry(setOf(GallerySite.YANDERE), "tag$it") }
        val raw = encodeGallerySearchHistory(many)
        assertEquals(GallerySearch.HISTORY_MAX, raw.lines().size - 1)
        assertEquals(GallerySearch.HISTORY_MAX, decodeGallerySearchHistory(raw).size)
    }

    @Test
    fun `旧的单站构造器仍可用且站点派生自集合`() {
        val entry = GallerySearchEntry(GallerySite.GELBOORU, "setmen")
        assertEquals(GallerySite.GELBOORU, entry.site)
        assertEquals(setOf(GallerySite.GELBOORU), entry.sites)
    }

    @Test
    fun `编码里的站点顺序按站表定死`() {
        val a = encodeGallerySearchHistory(listOf(GallerySearchEntry(both, "setmen")))
        val b = encodeGallerySearchHistory(
            listOf(GallerySearchEntry(setOf(GallerySite.GELBOORU, GallerySite.YANDERE), "setmen")),
        )
        // 跟着 LinkedHashSet 的插入序写盘，同一档存两次会产出不同字节，diff 与去重都会跟着飘。
        assertEquals(a, b)
    }

    @Test
    fun `v1 档里同站重复的行读回来只留一条`() {
        val out = decodeGallerySearchHistory("yandere=setmen\nyandere=setmen")
        assertEquals(1, out.size)
        assertEquals(setOf(GallerySite.YANDERE), out.single().sites)
    }

    @Test
    fun `空串与整行没标签的脏档都不炸`() {
        assertTrue(decodeGallerySearchHistory(null).isEmpty())
        assertTrue(decodeGallerySearchHistory("").isEmpty())
        assertTrue(decodeGallerySearchHistory("v2\nyandere=").isEmpty())
    }
}
