package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.decodeGallerySearchHistory
import com.venera.compose.gallery.domain.encodeGallerySearchHistory
import com.venera.compose.gallery.domain.pushGallerySearchHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 翻页"到底"判据与搜索历史编解码。
 *
 * 到底为什么不能用"返回 0 条"判：那要多空跑一页才知道到底，用户会先看到一次"没有更多"
 * 再看到页尾 —— 与搜索页 §7 那条「到底了还显示加载更多」是同一类假读数，只是反着长。
 * 为什么也不报总数：两站都**没有**总数端点（实测 `/posts/count.json` 与 `/post/count.json` 都 404）。
 */
class GallerySearchPaginationTest {

    @Test
    fun `给满就是还有下一批`() {
        assertFalse(GallerySearch.isExhausted(returned = 200, requestedLimit = 200))
        assertTrue(GallerySearch.isExhausted(returned = 199, requestedLimit = 200))
        // 空数组是**合法结果**（查无此标签，实测 200 + `[]`）：到底，但不是失败。
        assertTrue(GallerySearch.isExhausted(returned = 0, requestedLimit = 200))
    }

    private fun entry(site: GallerySite, query: String) = GallerySearchEntry(site, query)

    @Test
    fun `历史往返带站点`() {
        val entries = listOf(
            entry(GallerySite.GELBOORU, "hime_cut -ai_generated"),
            entry(GallerySite.YANDERE, "hime"),
        )
        assertEquals(entries, decodeGallerySearchHistory(encodeGallerySearchHistory(entries)))
    }

    @Test
    fun `查询里带等号也不被拆掉`() {
        // 只在**第一个** `=` 处拆：routeKey 是死串绝不含 `=`，而标签串可能含（source=xxx 这类）。
        val entries = listOf(entry(GallerySite.GELBOORU, "source=https://a/?x=1"))
        assertEquals(entries, decodeGallerySearchHistory(encodeGallerySearchHistory(entries)))
    }

    @Test
    fun `认不出的站点整条丢掉而不是硬归某一站`() {
        val raw = "pixiv=hime\ngelbooru=hime_cut\n=只有查询\nyandere="
        val out = decodeGallerySearchHistory(raw)
        assertEquals(listOf(entry(GallerySite.GELBOORU, "hime_cut")), out)
    }

    @Test
    fun `空串与 null 都退成无历史`() {
        assertTrue(decodeGallerySearchHistory(null).isEmpty())
        assertTrue(decodeGallerySearchHistory("").isEmpty())
    }

    @Test
    fun `新条目排最前并按整串去重`() {
        val a = entry(GallerySite.GELBOORU, "hime")
        val b = entry(GallerySite.YANDERE, "hime")
        var history = listOf(a, b)
        history = pushGallerySearchHistory(history, a)
        // 同标签串换站算另一条（两站词表不通），所以 b 留着；a 被提到最前且不重复。
        assertEquals(listOf(a, b), history)
        history = pushGallerySearchHistory(history, entry(GallerySite.GELBOORU, "hime -ai_generated"))
        assertEquals(3, history.size)
        assertEquals("hime -ai_generated", history.first().query)
    }

    @Test
    fun `上限钉死且不会越推越长`() {
        var history = emptyList<GallerySearchEntry>()
        repeat(GallerySearch.HISTORY_MAX + 30) { i ->
            history = pushGallerySearchHistory(history, entry(GallerySite.YANDERE, "tag_$i"))
        }
        assertEquals(GallerySearch.HISTORY_MAX, history.size)
        assertEquals("tag_${GallerySearch.HISTORY_MAX + 29}", history.first().query)
        // 编码侧同样再截一次：调用方塞进来超量也不能写进 SharedPreferences。
        assertEquals(
            GallerySearch.HISTORY_MAX,
            decodeGallerySearchHistory(encodeGallerySearchHistory(history + history)).size,
        )
    }
}
