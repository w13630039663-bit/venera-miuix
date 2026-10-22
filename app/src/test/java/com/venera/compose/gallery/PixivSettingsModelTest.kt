package com.venera.compose.gallery

import com.venera.compose.gallery.domain.PixivImageQuality
import com.venera.compose.gallery.domain.PixivSaveFormatHelper
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import org.junit.Assert.assertEquals
import org.junit.Test

class PixivSettingsModelTest {

    @Test
    fun `pixiv 文件名格式化模板正确替换占位符`() {
        val res = PixivSaveFormatHelper.format(
            template = "{illust_id}_p{part}",
            illustId = 123456L,
            part = 0,
            title = "Test Title",
            userId = 654321L,
            artistName = "Artist",
            ext = "png",
        )
        assertEquals("123456_p0.png", res)
    }

    @Test
    fun `pixiv 文件名包含标题和画师名时清理非法字符`() {
        val res = PixivSaveFormatHelper.format(
            template = "{artist_name}_{title}_{illust_id}",
            illustId = 99999L,
            part = 1,
            title = "Special/Title:A*B",
            userId = 888L,
            artistName = "My/Artist",
            ext = "jpg",
        )
        assertEquals("My_Artist_Special_Title_A_B_99999.jpg", res)
    }

    @Test
    fun `pixiv 卡片预览根据画质选择对应 URL`() {
        val post = GalleryPost(
            site = GallerySite.PIXIV,
            id = 1L,
            previewUrl = "https://preview.jpg",
            largeUrl = "https://large.jpg",
            fileUrl = "https://original.jpg",
            author = "author",
        )
        assertEquals("https://preview.jpg", GallerySettingsModel.pixivWallUrl(post, PixivImageQuality.MEDIUM))
        assertEquals("https://large.jpg", GallerySettingsModel.pixivWallUrl(post, PixivImageQuality.LARGE))
        assertEquals("https://original.jpg", GallerySettingsModel.pixivWallUrl(post, PixivImageQuality.ORIGINAL))
    }
}
