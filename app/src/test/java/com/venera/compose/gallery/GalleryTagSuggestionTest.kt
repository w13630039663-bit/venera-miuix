package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.data.galleryTagCategoryLabel
import com.venera.compose.gallery.data.refineGalleryTagSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标签补全的**复检**判据。
 *
 * 为什么单独立一条：两站的前缀参数都**可能被静默忽略**，而忽略之后回的还是 200 + 一个像样的数组
 * （实测 Danbooru 把 `search[name_match]` 丢掉后回的是"最新建的标签"：搜 `hime` 得到
 * `huasu01`、`yakuen_sapuri`）。也就是说站方给错数据时**没有任何信号** ——
 * 不复检就会把不相干的标签摆成"你要搜的"，用户点了得到一个必然 0 条的查询。
 */
class GalleryTagSuggestionTest {

    private fun tag(name: String, count: Int = 1, category: Int = 0, deprecated: Boolean = false) =
        GalleryTagSuggestion(GallerySite.GELBOORU, name, count, category, deprecated)

    @Test
    fun `站方回错数据时按输入前缀丢掉不相干的`() {
        val raw = listOf(tag("huasu01"), tag("hime_cut", 31951), tag("yakuen_sapuri"), tag("HIMEkaidou", 8873))
        val out = refineGalleryTagSuggestions("hime", raw, limit = 8)
        assertEquals(listOf("hime_cut", "HIMEkaidou"), out.map { it.name })
    }

    @Test
    fun `空格按站方语法换成下划线再比`() {
        // 用户在实体键盘上打不出下划线那么自然，输入 "hime cut" 应当仍能匹配 hime_cut。
        val out = refineGalleryTagSuggestions("hime cut", listOf(tag("hime_cut", 5), tag("himeko")), limit = 8)
        assertEquals(listOf("hime_cut"), out.map { it.name })
    }

    @Test
    fun `空名条目一律丢掉`() {
        // yande.re 实测存在 name:"" 的脏数据（`search[name]` 那次回的就是它）。
        val out = refineGalleryTagSuggestions("a", listOf(tag(""), tag("a")), limit = 8)
        assertEquals(listOf("a"), out.map { it.name })
    }

    @Test
    fun `按张数降序并把废弃标签沉到底`() {
        val raw = listOf(
            tag("hime_a", 10),
            tag("hime_b", 900, deprecated = true),
            tag("hime_c", 50),
        )
        val out = refineGalleryTagSuggestions("hime", raw, limit = 8)
        assertEquals(listOf("hime_c", "hime_a", "hime_b"), out.map { it.name })
        // 废弃的**不删**：它照样能搜出东西，删掉等于替用户决定"这个你别想搜"。
        assertEquals(3, out.size)
    }

    @Test
    fun `limit 截断且负数不炸`() {
        val raw = (1..30).map { tag("hime_$it") }
        assertEquals(5, refineGalleryTagSuggestions("hime", raw, limit = 5).size)
        assertTrue(refineGalleryTagSuggestions("hime", raw, limit = 0).isEmpty())
    }

    @Test
    fun `档位名与 tagGroups 那五桶同一套编号`() {
        assertEquals("通用", galleryTagCategoryLabel(0))
        assertEquals("画师", galleryTagCategoryLabel(1))
        assertEquals("作品", galleryTagCategoryLabel(3))
        assertEquals("角色", galleryTagCategoryLabel(4))
        assertEquals("元数据", galleryTagCategoryLabel(5))
        // 没记录的编号要说话，不给空串（空串在列表里长成"这行没有档位"）。
        assertEquals("其他", galleryTagCategoryLabel(77))
    }
}
