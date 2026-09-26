package com.venera.compose.sync

import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.LocalFavoriteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份文件里「收藏」行的编解码判据。
 *
 * 这一层是备份功能真正会静默出错的地方：列名对不上不会抛异常，只会恢复出一批
 * 打开是空白条目的收藏。而 `BackupManager` 那半要 SQLite + Context 才跑得动
 * （本项目单元测试没有 Robolectric），所以能测的判据全抽在 [FavoriteBackupRows] 里测。
 */
class FavoriteBackupRowsTest {

    private val item = FavoriteItem(
        id = "g/123456/abc",
        name = "测试本",
        author = "某画师",
        sourceKey = "ehentai",
        tags = listOf("中文", "汉化"),
        coverPath = "https://cover.example/1.jpg",
        time = "2026-09-25 10:00:00",
        displayOrder = 7,
    )

    @Test
    fun `当前格式一行能原样走回来`() {
        val row = FavoriteBackupRows.encode(item, "追更中")

        val restored = FavoriteBackupRows.decode(row)

        assertEquals("追更中", restored.folder)
        assertEquals(item, restored.item)
        // 带 displayOrder 的行要把它交回 addComic，否则夹子里的手动顺序白排
        assertEquals(7, restored.order)
    }

    @Test
    fun `v3 旧单表的列名照样认`() {
        val legacy = mapOf(
            "comic_id" to "g/123456/abc",
            "title" to "测试本",
            "author" to "某画师",
            "source_name" to "ehentai",
            "tags" to "中文,汉化",
            "cover_url" to "https://cover.example/1.jpg",
            "folder_name" to "追更中",
            "has_update" to 1L,
            "latest_chapter" to "第 3 话",
            "created_at" to FavoriteItem.parseTime("2026-09-25 10:00:00"),
        )

        val restored = FavoriteBackupRows.decode(legacy)

        assertEquals("追更中", restored.folder)
        // displayOrder 归 0：旧单表压根没有这一列，硬造一个数才是假数据
        assertEquals(item.copy(displayOrder = 0), restored.item)
    }

    @Test
    fun `旧行没有排序列时交给偏好决定顺序`() {
        // 给旧行硬填 0 的话，一次导入的所有条目排序值相同，夹子顺序就成了读取顺序的副产品
        val restored = FavoriteBackupRows.decode(mapOf("comic_id" to "a", "title" to "b"))

        assertNull(restored.order)
        assertEquals(0, restored.item.displayOrder)
    }

    @Test
    fun `标签串两种写法都拆`() {
        val fromComma = FavoriteBackupRows.decode(
            mapOf("comic_id" to "a", "tags" to "中文, 汉化 ,")
        )
        assertEquals(listOf("中文", "汉化"), fromComma.item.tags)

        val fromNewline = FavoriteBackupRows.decode(
            mapOf("comic_id" to "a", "tags" to "中文\n汉化")
        )
        assertEquals(listOf("中文", "汉化"), fromNewline.item.tags)

        val fromArray = FavoriteBackupRows.decode(mapOf("comic_id" to "a", "tags" to listOf("中文", "汉化")))
        assertEquals(listOf("中文", "汉化"), fromArray.item.tags)
    }

    @Test
    fun `没有夹名落到默认夹而不是凭空造一个夹`() {
        val blankFolder = FavoriteBackupRows.decode(mapOf("id" to "a", "folder" to "  "))
        assertEquals(LocalFavoriteDatabase.DEFAULT_FOLDER, blankFolder.folder)

        val noFolder = FavoriteBackupRows.decode(mapOf("comic_id" to "a"))
        assertEquals(LocalFavoriteDatabase.DEFAULT_FOLDER, noFolder.folder)
    }

    @Test
    fun `新旧行靠列名判别且互不吞`() {
        assertTrue(FavoriteBackupRows.isLegacy(mapOf("comic_id" to "a", "title" to "b")))
        assertFalse(FavoriteBackupRows.isLegacy(mapOf("id" to "a", "name" to "b")))
        // 两套列名同时出现（手改过的文件）按当前格式走：id 才是主键的一半
        assertFalse(FavoriteBackupRows.isLegacy(mapOf("comic_id" to "a", "id" to "b")))
    }

    @Test
    fun `数字从 json 回来是长整数也要认`() {
        val row = FavoriteBackupRows.decode(
            mapOf("id" to "a", "displayOrder" to -3L, "time" to "2026-09-25 10:00:00")
        )

        assertEquals(-3, row.item.displayOrder)
        assertEquals(-3, row.order)
    }

    @Test
    fun `缺 id 的行交出来就是空 id 由调用方跳掉`() {
        val row = FavoriteBackupRows.decode(mapOf("name" to "没有主键的一行"))

        assertTrue(row.item.id.isBlank())
    }

    @Test
    fun `收藏时间为空时补当下而不是留一个解不开的串`() {
        val row = FavoriteBackupRows.decode(mapOf("id" to "a", "time" to ""))

        // 空串写进 time 列，收藏页上的日期就是空白；1970 同理是假日期
        assertTrue(row.item.time.isNotBlank())
        assertFalse(row.item.time.startsWith("1970"))
    }
}
