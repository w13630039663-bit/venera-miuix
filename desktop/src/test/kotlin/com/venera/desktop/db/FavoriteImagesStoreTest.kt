package com.venera.desktop.db

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.FavoriteImageBackupFields
import com.venera.compose.data.db.FavoriteImagesStore
import com.venera.compose.data.db.SqlDatabaseSource
import com.venera.compose.data.platform.SqlDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * `favorite_images`（插图收藏）的读写与备份那两笔（R18：真库跑，不是 mock）。
 *
 * 盯的是三件会在真机上"看起来没事、其实坏了"的地方：备份行的列清单（不能带本机才成立的
 * `id` / `local_path`）、恢复时的去重键，以及恢复中途写不下去时**整笔回滚**而不是报成功少几条。
 */
class FavoriteImagesStoreTest {

    private lateinit var store: TestDatabase
    private lateinit var images: FavoriteImagesStore

    @Before
    fun setUp() {
        store = TestDatabase("favimages")
        CoreDbSchema.ensureOn(store.db)
        images = FavoriteImagesStore(store.source)
    }

    @After
    fun tearDown() = store.close()

    private fun add(
        comicId: String = "g1",
        title: String = "本子",
        source: String = "jm",
        chapter: String = "第1话",
        page: Int = 3,
        url: String,
        localPath: String = "",
        createdAt: Long = 1L,
    ) = images.insert(comicId, title, source, chapter, page, url, localPath, createdAt)

    @Test
    fun `新增回报新行 rowid 且逐列读回`() {
        val rowId = add(url = "https://a/1.jpg", localPath = "/data/local/1.jpg", createdAt = 123L)

        assertEquals(1L, rowId)
        val rows = images.allRows()
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals(1L, row.id)
        assertEquals("g1", row.comicId)
        assertEquals("本子", row.comicTitle)
        assertEquals("jm", row.sourceName)
        assertEquals("第1话", row.chapterTitle)
        assertEquals(3, row.pageIndex)
        assertEquals("https://a/1.jpg", row.imageUrl)
        assertEquals("/data/local/1.jpg", row.localPath)
        assertEquals(123L, row.createdAt)
    }

    @Test
    fun `local_path 为 null 或空串时收藏墙读回来都是空串`() {
        // 建表里 local_path 没带 NOT NULL：只存地址的条目写入的是空串，而更早的数据可能是 NULL。
        // 收藏墙那条读法（allRows）两种都要读成 ""，因为它的取图口径是 `localPath.ifBlank { imageUrl }`。
        // 而删文件用的 localPathOf 保持改造前的游标读数：值是 NULL 就交回 null（改造前
        // `c.getString(0)` 对 NULL 列正是 null，下游 `deletePersistedFile` 对 null 与空串同样不动手）。
        add(url = "https://a/2.jpg")
        assertEquals("", images.localPathOf(1L))

        store.db.exec("UPDATE favorite_images SET local_path = NULL WHERE id = 1")
        assertNull(images.localPathOf(1L))
        assertEquals("", images.allRows()[0].localPath)
    }

    @Test
    fun `行不存在时路径读法交回 null 而不是空串`() {
        // 空串在这里会被调用方读成"这一条没有副本"，null 才是"根本没有这一行"
        assertEquals(null, images.localPathOf(999L))
    }

    @Test
    fun `按地址判是否已收藏与恢复用的去重键是同一个`() {
        add(url = "https://a/3.jpg")

        assertTrue(images.isFavorited("https://a/3.jpg"))
        assertFalse(images.isFavorited("https://a/4.jpg"))
    }

    @Test
    fun `备份栏只交七个字段并按创建时间倒序`() {
        add(url = "https://a/old.jpg", createdAt = 1L, title = "旧")
        add(url = "https://a/new.jpg", createdAt = 9L, title = "新", localPath = "/x/new.jpg")

        val rows = images.backupFields()
        assertEquals(2, rows.size)
        assertEquals("新", rows[0].comicTitle)
        assertEquals(9L, rows[0].createdAt)
        // 备份里不许出现 local_path（换台机器它就是条不存在的路径）
        assertTrue(rows.none { it.toString().contains("local") })
    }

    @Test
    fun `恢复时本机已有的地址与批内重复都不再写且只数真正新增的`() {
        add(url = "https://a/dup.jpg")

        val restored = images.restoreBackupFields(
            listOf(
                backup("https://a/dup.jpg"),   // 本机已有
                backup("https://a/new.jpg"),   // 新增
                backup("https://a/new.jpg"),   // 批内重复
            )
        )

        assertEquals(1, restored)
        val urls = images.backupFields().map { it.imageUrl }
        assertEquals(listOf("https://a/new.jpg", "https://a/dup.jpg"), urls.sortedDescending())
        // 恢复进来的行 local_path 一律空串（归档里没有那份位图，收藏墙按地址现加载）
        assertEquals("", images.allRows().first { it.imageUrl == "https://a/new.jpg" }.localPath)
    }

    @Test
    fun `恢复的 created_at 用归档原值而不是导入时刻`() {
        images.restoreBackupFields(listOf(backup("https://a/keep.jpg", createdAt = 555L)))

        assertEquals(555L, images.backupFields()[0].createdAt)
    }

    @Test
    fun `恢复中途写不下去则整笔回滚且异常传出`() {
        val exploding = ExplodingSqlDatabase(store.db, failOn = 2).asSource()

        try {
            FavoriteImagesStore(exploding).restoreBackupFields(
                listOf(backup("https://a/1.jpg"), backup("https://a/2.jpg"))
            )
            fail("第二笔写失败本该抛出")
        } catch (e: IllegalStateException) {
            assertTrue("实际消息：${e.message}", e.message!!.contains("模拟"))
        }
        // 第一张也没留下：整笔事务回滚，不出现"报成功但少一条"
        assertEquals(0, images.backupFields().size)
    }

    @Test
    fun `空批次不碰库`() {
        assertEquals(0, images.restoreBackupFields(emptyList()))
        assertEquals(0, images.deleteByIds(emptyList()))
        assertEquals(emptyList<String?>(), images.localPathsOf(emptyList()))
    }

    @Test
    fun `删除回报有没有删掉并先交回路径`() {
        val rowId = add(url = "https://a/gone.jpg", localPath = "/data/gone.jpg")

        assertEquals("/data/gone.jpg", images.localPathOf(rowId))
        assertTrue(images.deleteById(rowId))
        assertFalse(images.deleteById(rowId)) // 第二次没有这一行了，如实报没删到
        assertEquals(0, images.allRows().size)
    }

    @Test
    fun `批量删除回报真正删掉的行数`() {
        val a = add(url = "https://a/1.jpg")
        val b = add(url = "https://a/2.jpg")

        assertEquals(2, images.deleteByIds(listOf(a, b, 999L))) // 999 不存在，不计入
        assertEquals(0, images.allRows().size)
    }

    private fun backup(url: String, createdAt: Long = 1L) = FavoriteImageBackupFields(
        comicId = "g1",
        comicTitle = "本子",
        sourceName = "jm",
        chapterTitle = "第1话",
        pageIndex = 1,
        imageUrl = url,
        createdAt = createdAt,
    )
}
