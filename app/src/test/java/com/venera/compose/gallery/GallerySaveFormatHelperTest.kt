package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySaveFormatHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class GallerySaveFormatHelperTest {

    private fun samplePost(
        site: GallerySite = GallerySite.YANDERE,
        id: Long = 1269641L,
        author: String = "Arsy",
        md5: String = "d41d8cd98f00b204e9800998ecf8427e",
        rating: String = "s",
    ) = GalleryPost(
        site = site,
        id = id,
        author = author,
        md5 = md5,
        rating = rating,
    )

    @Test
    fun `默认模板正确格式化站点_画师_编号`() {
        val post = samplePost()
        val res = GallerySaveFormatHelper.format(
            template = "{site}_{artist}_{id}",
            post = post,
            artistName = "gijang",
            timestamp = "20261022-120000",
            ext = "jpg",
        )
        assertEquals("yandere_gijang_1269641.jpg", res)
    }

    @Test
    fun `画师为空时平滑折叠分隔符不留多余下划线`() {
        val post = samplePost()
        val res = GallerySaveFormatHelper.format(
            template = "{site}_{artist}_{id}",
            post = post,
            artistName = "",
            timestamp = "20261022-120000",
            ext = "png",
        )
        assertEquals("yandere_1269641.png", res)
    }

    @Test
    fun `支持上传者占位符准确输出上传账号`() {
        val post = samplePost(author = "Arsy")
        val res = GallerySaveFormatHelper.format(
            template = "{site}_{uploader}_{id}",
            post = post,
            artistName = "gijang",
            timestamp = "20261022-120000",
            ext = "jpg",
        )
        assertEquals("yandere_Arsy_1269641.jpg", res)
    }

    @Test
    fun `支持md5与分级与时间戳占位符`() {
        val post = samplePost()
        val res = GallerySaveFormatHelper.format(
            template = "{site}_{id}_{rating}_{timestamp}",
            post = post,
            artistName = "gijang",
            timestamp = "20261022-120000",
            ext = "jpg",
        )
        assertEquals("yandere_1269641_s_20261022-120000.jpg", res)
    }

    @Test
    fun `特殊字符被安全清洗`() {
        val post = samplePost(id = 999L)
        val res = GallerySaveFormatHelper.format(
            template = "{artist}_{id}",
            post = post,
            artistName = "My/Artist:Bad*Chars?",
            timestamp = "20261022-120000",
            ext = "webp",
        )
        assertEquals("My_Artist_Bad_Chars_999.webp", res)
    }

    @Test
    fun `模板为空时回退默认模板`() {
        val post = samplePost()
        val res = GallerySaveFormatHelper.format(
            template = "",
            post = post,
            artistName = "gijang",
            timestamp = "20261022-120000",
            ext = "jpg",
        )
        assertEquals("yandere_gijang_1269641.jpg", res)
    }
}