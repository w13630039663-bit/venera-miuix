package com.venera.compose.sync

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistFollow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 归档里画廊那两栏的编解码判据。
 *
 * 守的是四件会**静默出错**的事：字段名漂移、站点键认错、缺档当成坏档、坏档被当成没档。
 * 这四条没有一条会抛在恢复路径上，用户看到的都是"导入报了成功，可屏上就是少东西"。
 */
class GalleryBackupRowsTest {

    private fun favorite(
        site: GallerySite = GallerySite.YANDERE,
        id: Long = 88_123L,
        savedAt: Long = 1_700_000_000_000L,
    ) = GalleryFavorite(
        siteKey = site.routeKey,
        id = id,
        savedAt = savedAt,
        tags = "landscape_scenery mountains",
        rating = "g",
        score = 42,
        author = "a_maker",
        width = 1920,
        height = 1080,
        previewUrl = "https://cdn.example/preview.jpg",
        previewWidth = 300,
        previewHeight = 169,
        largeUrl = "https://cdn.example/large.jpg",
        fileUrl = "https://cdn.example/file.jpg",
        fileSize = 3_600_000L,
        fileExt = "jpg",
        durationSeconds = 12.5,
    )

    @Test
    fun `关注名单往返保住站点、名字与关注时刻`() {
        val follows = listOf(
            GalleryArtistFollow(GallerySite.YANDERE, "maker_a", 100L),
            GalleryArtistFollow(GallerySite.GELBOORU, "Maker B", 200L),
        )
        assertEquals(follows, GalleryBackupRows.decodeFollows(GalleryBackupRows.encodeFollows(follows)))
    }

    @Test
    fun `归档的字段名与磁盘档一致`() {
        val raw = GalleryBackupRows.encodeFollows(listOf(GalleryArtistFollow(GallerySite.SAFEBOORU, "x", 123L)))
        assertTrue(raw, raw.contains("\"site\":\"safebooru\""))
        assertTrue(raw, raw.contains("\"added_at\":123"))
    }

    @Test
    fun `认不出的站点键摘掉而不是当成某一站`() {
        // "pixiv" 是头像反查那条路用到的站，它不在 GallerySite 里：
        // 若退化成"随便算某一站"，就会把一个人的关注挂到另一个人的入口上。
        val dropped = GalleryBackupRows.decodeFollows("""[{"site":"pixiv","name":"a","added_at":1}]""")
        assertEquals(emptyList<GalleryArtistFollow>(), dropped)

        val kept = GalleryBackupRows.decodeFollows(
            """[{"site":"pixiv","name":"a","added_at":1},{"site":"yandere","name":"b","added_at":2}]"""
        )
        assertEquals(listOf(GalleryArtistFollow(GallerySite.YANDERE, "b", 2L)), kept)
    }

    @Test
    fun `名字空白的关注行不收`() {
        assertTrue(GalleryBackupRows.decodeFollows("""[{"site":"yandere","name":"  ","added_at":1}]""").isEmpty())
    }

    @Test
    fun `画廊收藏往返保住地址尺寸与时长`() {
        val items = listOf(favorite(), favorite(GallerySite.GELBOORU, 14_970_001L, 2L))
        assertEquals(items, GalleryBackupRows.decodeFavorites(GalleryBackupRows.encodeFavorites(items)))
    }

    @Test
    fun `收藏行认不出站点或没有 id 时摘掉`() {
        val raw = GalleryBackupRows.encodeFavorites(listOf(favorite()))
            .replace("\"site\":\"yandere\"", "\"site\":\"danbooru\"")
            .replace("\"id\":88123", "\"id\":0")
        assertTrue(GalleryBackupRows.decodeFavorites(raw).isEmpty())
    }

    @Test
    fun `member 不存在交回空列表`() {
        assertTrue(GalleryBackupRows.decodeFollows(null).isEmpty())
        assertTrue(GalleryBackupRows.decodeFavorites(null).isEmpty())
        // 空名单导出的是字面量 `[]`，它同样解回空 —— 这两条在导入侧都是"恢复 0 条"，不是失败。
        assertEquals(emptyList<GalleryArtistFollow>(), GalleryBackupRows.decodeFollows("[]"))
    }

    @Test
    fun `档在但解不开要抛出去，不能当成没有这一栏`() {
        val corrupt = """[{"site":"yandere","name":"""
        assertFalse(runCatching { GalleryBackupRows.decodeFollows(corrupt) }.isSuccess)
        assertFalse(runCatching { GalleryBackupRows.decodeFavorites(corrupt) }.isSuccess)
    }

    @Test
    fun `旧归档里少字段时按默认值补齐`() {
        val minimal = """[{"site":"yandere","id":7,"saved_at":9}]"""
        val decoded = GalleryBackupRows.decodeFavorites(minimal)
        assertEquals(1, decoded.size)
        assertEquals(7L, decoded.single().id)
        assertEquals("", decoded.single().tags)
        assertEquals(0, decoded.single().width)
        assertNull(decoded.single().durationSeconds)
    }
}
