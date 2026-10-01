package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryArtistUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 画师取数面的两条判据（批次 L · L2）。
 *
 * 两条都来自 2026-09-30 的取证：
 *
 * - **`-` 前缀不是脏数据，是站方的"这条已停用"标记**（danbooru 源码 `Artist` 的 `url_string`
 *   就是 `urls.map(&:to_s).join("\n")`，而 `ArtistURL.parse_prefix` 认这个前缀）。
 *   把它当普通字符串切出来，等于把站方已经作废的地址摆到画师行上。
 * - **头像两档只差尺寸**（实测同一位：`…_50.png` 与 `…_170.png`），而两档都是**会过期的地址**，
 *   所以只能现取现用、不许落库 —— 这一条锁住"取不到就交 null、不交空串"：
 *   空串进 Coil 只会"什么都不显示且没有原因"，null 才让调用方有机会摆首字母占位。
 */
class GalleryArtistUrlsTest {

    @Test
    fun `url_string 按空白切分 一条一个地址`() {
        assertEquals(
            listOf("https://www.pixiv.net/users/7075448", "https://twitter.com/setmen"),
            GalleryArtistUrls.split("https://www.pixiv.net/users/7075448\nhttps://twitter.com/setmen"),
        )
        // 站方给的是空格分隔也同一个结果（实测过两种写法并存）。
        assertEquals(
            listOf("http://a.example/", "http://b.example/"),
            GalleryArtistUrls.split("http://a.example/   http://b.example/"),
        )
    }

    @Test
    fun `以负号开头的那条是站方标停用 不许进结果`() {
        assertEquals(
            listOf("https://www.pixiv.net/users/1"),
            GalleryArtistUrls.split("-http://old.example/x\nhttps://www.pixiv.net/users/1"),
        )
    }

    @Test
    fun `空输入与纯空白都不产出条目`() {
        assertEquals(emptyList<Any>(), GalleryArtistUrls.split(null))
        assertEquals(emptyList<Any>(), GalleryArtistUrls.split(""))
        assertEquals(emptyList<Any>(), GalleryArtistUrls.split("  \n\t \n"))
    }

    @Test
    fun `同一条重复只留一次`() {
        assertEquals(
            listOf("https://x.com/a"),
            GalleryArtistUrls.split("https://x.com/a https://x.com/a\nhttps://x.com/a"),
        )
    }

    @Test
    fun `头像优先那档大的 没有才退小的`() {
        assertEquals(
            "https://i.pximg.net/user-profile/a_170.png",
            GalleryArtistUrls.avatarUrl("https://i.pximg.net/user-profile/a_170.png", "https://i.pximg.net/user-profile/a_50.png"),
        )
        assertEquals(
            "https://i.pximg.net/user-profile/a_50.png",
            GalleryArtistUrls.avatarUrl(null, "https://i.pximg.net/user-profile/a_50.png"),
        )
    }

    @Test
    fun `两档都拿不到时交 null 而不是空串`() {
        assertNull(GalleryArtistUrls.avatarUrl(null, null))
        assertNull(GalleryArtistUrls.avatarUrl("", ""))
        // 站方偶发把字段给成非地址（相对路径、占位串）：那也等于"没有"，
        // 交给 Coil 只会静默失败，而这里交 null 才会换成首字母占位。
        assertNull(GalleryArtistUrls.avatarUrl("/images/missing.png", "null"))
    }
}
