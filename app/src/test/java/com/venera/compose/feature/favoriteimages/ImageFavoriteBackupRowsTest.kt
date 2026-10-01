package com.venera.compose.feature.favoriteimages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 插图收藏那一栏的编解码判据。
 *
 * 这一路的取舍是「元数据 + 地址进包，图片文件不带」，所以这里守的是：
 * 带出去的不含本地路径（换台机器它就是条不存在的路径，而取图口径是「路径为空才按地址加载」），
 * 收进来的必须有地址（没地址的一行既加载不出图、也去不了重）。
 */
class ImageFavoriteBackupRowsTest {

    private val row = ImageFavoriteBackupRow(
        comicId = "jm_abc123",
        comicTitle = "某本漫画",
        sourceName = "禁漫天堂",
        chapterTitle = "第 3 话",
        pageIndex = 27,
        imageUrl = "https://cdn.example/12/34/56.jpg",
        createdAt = 1_700_000_000_000L,
    )

    @Test
    fun `导出的行里没有 id 也没有本地路径`() {
        val map = ImageFavoriteBackupRows.toMap(row)
        assertFalse(map.keys.toString(), map.containsKey("id"))
        assertFalse(map.keys.toString(), map.containsKey("local_path"))
        assertEquals(7, map.size)
    }

    @Test
    fun `往返保住七列`() {
        val decoded = ImageFavoriteBackupRows.fromMap(ImageFavoriteBackupRows.toMap(row))
        assertEquals(row, decoded)
    }

    @Test
    fun `没有地址的行不收`() {
        assertNull(ImageFavoriteBackupRows.fromMap(ImageFavoriteBackupRows.toMap(row.copy(imageUrl = ""))))
        assertNull(ImageFavoriteBackupRows.fromMap(mapOf("comic_id" to "x")))
        assertNull(ImageFavoriteBackupRows.fromMap(ImageFavoriteBackupRows.toMap(row.copy(imageUrl = "   "))))
    }

    @Test
    fun `数字以字符串或浮点回来时也收拢`() {
        // org.json 把整数解成 Long，手工改过的备份里也可能写成字符串或 27.0。
        val decoded = ImageFavoriteBackupRows.fromMap(
            mapOf(
                "comic_id" to "c",
                "comic_title" to "t",
                "source_name" to "s",
                "chapter_title" to "ch",
                "page_index" to "27",
                "image_url" to "u",
                "created_at" to 123.0,
            )
        )
        assertEquals(27, decoded?.pageIndex ?: -1)
        assertEquals(123L, decoded?.createdAt ?: -1L)
    }

    @Test
    fun `时刻缺失或为 0 时退到当前时间，不进一个 1970 的排序位`() {
        val decoded = ImageFavoriteBackupRows.fromMap(ImageFavoriteBackupRows.toMap(row.copy(createdAt = 0L)))
        assertTrue((decoded?.createdAt ?: 0L) > 0L)
    }
}
