package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistAvatars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 头像地址磁盘档的**判据层**：三档语义、同键覆盖、超上限丢谁。
 *
 * 这一层脱离 Android（读写盘在 `GalleryArtistAvatarStore`），所以三条语义都有专门用例 ——
 * 把"查过了、没有"当成"没查过"是最贵的那类错：每一位没头像的画师都会在每次冷启动被重问一遍。
 */
class GalleryArtistAvatarsTest {

    private val pixivUrl = "https://i.pximg.net/user-profile/img/2017/03/08/00/26/25/12240120_x_170.jpg"

    private fun entry(
        site: GallerySite = GallerySite.SAFEBOORU,
        name: String = "qitoli",
        source: String = "",
        avatar: String = pixivUrl,
        at: Long = 1L,
    ) = GalleryArtistAvatars.Entry(site, name, source, avatar, at)

    @Test
    fun `三档语义分得开：没查过、查过没有、有地址`() {
        val empty = GalleryArtistAvatars.index(emptyList())
        // 从没查过 → null，调用方该发请求。
        assertNull(GalleryArtistAvatars.lookup(empty, GallerySite.SAFEBOORU, "qitoli", ""))

        // 查过了、站方没给脸 → 空串（不是 null）：这一档必须与上一档分得开。
        val none = GalleryArtistAvatars.index(listOf(entry(avatar = "")))
        assertEquals("", GalleryArtistAvatars.lookup(none, GallerySite.SAFEBOORU, "qitoli", ""))

        // 有地址 → 直接拿来画。
        val known = GalleryArtistAvatars.index(listOf(entry()))
        assertEquals(pixivUrl, GalleryArtistAvatars.lookup(known, GallerySite.SAFEBOORU, "qitoli", ""))
    }

    @Test
    fun `站点与出处都进键，同名不同站不算命中`() {
        val indexed = GalleryArtistAvatars.index(listOf(entry(site = GallerySite.SAFEBOORU)))
        // 同一名字在另一站上不保证是同一个人（键里那一条理由）。
        assertNull(GalleryArtistAvatars.lookup(indexed, GallerySite.YANDERE, "qitoli", ""))
        // 出处不同也不算：那一枚 pixiv 入口是从出处反查来的，混用会给这一位挂上另一位的脸。
        assertNull(
            GalleryArtistAvatars.lookup(indexed, GallerySite.SAFEBOORU, "qitoli", "https://www.pixiv.net/users/1"),
        )
    }

    @Test
    fun `画师名大小写不该把同一位拆成两份`() {
        val indexed = GalleryArtistAvatars.index(listOf(entry(name = "Qitoli")))
        assertEquals(pixivUrl, GalleryArtistAvatars.lookup(indexed, GallerySite.SAFEBOORU, "qitoli", ""))
    }

    @Test
    fun `同一键重复解析只留最新那一条`() {
        val first = entry(avatar = "https://old/x_170.jpg", at = 10L)
        val second = entry(avatar = "https://new/y_170.jpg", at = 20L)
        val after = GalleryArtistAvatars.put(GalleryArtistAvatars.put(emptyList(), first), second)
        assertEquals(1, after.size)
        assertEquals("https://new/y_170.jpg", after.single().avatar)
    }

    @Test
    fun `超出上限丢最早解析的那批而不是最后写的顺序`() {
        val written = (1..5).map { entry(name = "artist$it", at = it.toLong()) }
        // 故意倒序写入：丢的必须是 resolvedAt 最小的那两条，而不是列表头两条。
        val kept = GalleryArtistAvatars.put(written.reversed(), entry(name = "artist6", at = 6L), max = 4)
        assertEquals(4, kept.size)
        assertEquals(
            listOf("artist6", "artist5", "artist4", "artist3"),
            kept.map { it.name },
        )
        assertEquals(
            listOf("artist1", "artist2"),
            written.map { it.name }.filter { name -> kept.none { it.name == name } },
        )
    }

    @Test
    fun `盘上同键的重复行按时间留最新的那一条`() {
        // 老档被手工并过、或写盘一半被打断都可能留下两行同键 —— 读回来不能是"看运气哪条赢"。
        val duplicated = listOf(
            entry(avatar = "https://old/x.jpg", at = 1L),
            entry(avatar = "https://new/y.jpg", at = 9L),
        )
        val indexed = GalleryArtistAvatars.index(duplicated)
        assertEquals(1, indexed.size)
        assertEquals("https://new/y.jpg", indexed.values.single().avatar)
    }
}
