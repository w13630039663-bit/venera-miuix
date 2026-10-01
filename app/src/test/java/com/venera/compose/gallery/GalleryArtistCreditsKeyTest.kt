package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistCreditsKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 画师署名缓存键（判据头注见 `GalleryArtistCreditsKey`）。
 *
 * 这一组用例守的是一件**会说话的错**：键里少一样，屏上就会把 A 的 pixiv 挂到 B 的名字底下
 * —— 那不是"少取了一次"，是**指着别人的账号说这是你**。
 */
class GalleryArtistCreditsKeyTest {

    private val pixivArtwork = "https://www.pixiv.net/artworks/12345678"
    private val twitterPost = "https://twitter.com/someone/status/999"

    @Test
    fun `同一站同一位同出处是同一个键`() {
        assertEquals(
            GalleryArtistCreditsKey.of(GallerySite.YANDERE, "foo", pixivArtwork),
            GalleryArtistCreditsKey.of(GallerySite.YANDERE, "foo", pixivArtwork),
        )
    }

    @Test
    fun `画师名大小写不拆键`() {
        // 从画师页点进来那一路拿的是屏上显示的名字，大小写不该把同一位拆成两份缓存。
        assertEquals(
            GalleryArtistCreditsKey.of(GallerySite.GELBOORU, "SomeArtist", twitterPost),
            GalleryArtistCreditsKey.of(GallerySite.GELBOORU, "someartist", twitterPost),
        )
    }

    @Test
    fun `同一站同一位但出处不同必须是两个键`() {
        // 反查 pixiv 作者是从出处走的：混用一份就会给这一位挂上另一位的号。
        assertNotEquals(
            GalleryArtistCreditsKey.of(GallerySite.YANDERE, "foo", pixivArtwork),
            GalleryArtistCreditsKey.of(GallerySite.YANDERE, "foo", twitterPost),
        )
    }

    @Test
    fun `同名不同站必须是两个键`() {
        assertNotEquals(
            GalleryArtistCreditsKey.of(GallerySite.YANDERE, "foo", ""),
            GalleryArtistCreditsKey.of(GallerySite.GELBOORU, "foo", ""),
        )
    }

    @Test
    fun `出处为空仍然成键`() {
        // 站方没给出处是常态：空串也必须能当键用（不是"不进缓存"）。
        assertEquals(
            GalleryArtistCreditsKey.of(GallerySite.GELBOORU, "foo", ""),
            GalleryArtistCreditsKey.of(GallerySite.GELBOORU, "foo", ""),
        )
    }
}
