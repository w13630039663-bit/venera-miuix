package com.venera.desktop.db

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.db.SqlDatabaseSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 阅读历史的行为：upsert、"空封面沿用旧值" 那条既有规则、删除必须带源、批量删的事务口径。
 */
class HistoryDaoTest {
    private lateinit var store: TestDatabase
    private lateinit var dao: HistoryDao

    @Before
    fun setUp() {
        store = TestDatabase("history")
        CoreDbSchema.ensureOn(store.db)
        dao = HistoryDao(store.source)
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun record(
        comicId: String,
        sourceName: String = "拷贝漫画",
        coverUrl: String = "https://cover/$comicId",
        lastChapterIndex: Int = 0,
        lastPageIndex: Int = 0,
        updatedAt: Long = 1L,
    ) = HistoryRecord(
        comicId = comicId,
        title = "作品 $comicId",
        author = "作者",
        coverUrl = coverUrl,
        sourceName = sourceName,
        lastChapterTitle = "第 ${lastChapterIndex + 1} 话",
        lastChapterIndex = lastChapterIndex,
        lastPageIndex = lastPageIndex,
        totalPages = 40,
        updatedAt = updatedAt,
    )

    private fun rowCount(): Int = store.db.query("SELECT comic_id FROM comic_history").size

    @Test
    fun `保存后读回字段一字不差`() {
        runBlocking { dao.saveHistory(record("c1", lastChapterIndex = 3, lastPageIndex = 7, updatedAt = 99L)) }

        val row = store.db.query("SELECT * FROM comic_history").single()
        assertEquals("c1", row.string("comic_id"))
        assertEquals("作品 c1", row.string("title"))
        assertEquals("作者", row.string("author"))
        assertEquals("https://cover/c1", row.string("cover_url"))
        assertEquals("拷贝漫画", row.string("source_name"))
        assertEquals("第 4 话", row.string("last_chapter_title"))
        assertEquals(3L, row.long("last_chapter_index"))
        assertEquals(7L, row.long("last_page_index"))
        assertEquals(40L, row.long("total_pages"))
        assertEquals(99L, row.long("updated_at"))
    }

    @Test
    fun `同一主键再保存是覆盖进度而不是多出一条`() {
        runBlocking {
            dao.saveHistory(record("c1", lastChapterIndex = 1, updatedAt = 1L))
            dao.saveHistory(record("c1", lastChapterIndex = 5, updatedAt = 2L))
        }

        assertEquals(1, rowCount())
        val loaded = runBlocking { dao.getHistory("c1") }!!
        assertEquals(5, loaded.lastChapterIndex)
        assertEquals(2L, loaded.updatedAt)
    }

    @Test
    fun `入口封面为空时沿用库里已有的好封面`() {
        runBlocking {
            dao.saveHistory(record("c1", coverUrl = "https://good"))
            dao.saveHistory(record("c1", coverUrl = "", lastChapterIndex = 9))
        }

        val row = store.db.query("SELECT cover_url, last_chapter_index FROM comic_history").single()
        assertEquals("https://good", row.string("cover_url"))
        assertEquals(9L, row.long("last_chapter_index"))
    }

    @Test
    fun `首次保存就带空封面则照原样写空串`() {
        runBlocking { dao.saveHistory(record("c2", coverUrl = "")) }

        assertEquals("", store.db.query("SELECT cover_url FROM comic_history").single().string("cover_url"))
    }

    @Test
    fun `不同源的同 ID 是两条进度且删除只带走指定那条`() {
        runBlocking {
            dao.saveHistory(record("c1", sourceName = "拷贝漫画"))
            dao.saveHistory(record("c1", sourceName = "哔咔漫画"))
            dao.deleteHistory("c1", "拷贝漫画")
        }

        val rows = store.db.query("SELECT source_name FROM comic_history")
        assertEquals(listOf("哔咔漫画"), rows.map { it.string("source_name") })
    }

    @Test
    fun `批量删除走一笔事务`() {
        runBlocking {
            dao.saveHistory(record("a"))
            dao.saveHistory(record("b"))
            dao.batchDeleteHistory(listOf(record("a"), record("b")))
        }

        assertEquals(0, rowCount())
    }

    @Test
    fun `批量删除中途抛则整笔回滚且异常原样传出`() {
        CoreDbSchema.ensureOn(store.db)
        val daoWithBoom = HistoryDao(
            object : SqlDatabaseSource {
                override fun reader() = store.db
                override fun writer() = ExplodingSqlDatabase(store.db, failOn = 2)
            },
        )
        runBlocking {
            dao.saveHistory(record("a"))
            dao.saveHistory(record("b"))
            dao.saveHistory(record("c"))
        }
        assertEquals(3, rowCount())

        val boom = runCatching {
            runBlocking { daoWithBoom.batchDeleteHistory(listOf(record("a"), record("b"), record("c"))) }
        }.exceptionOrNull()

        assertTrue(boom is IllegalStateException)
        assertTrue(boom!!.message!!.contains("模拟第 2 次写入失败"))
        // 第一笔删除已经在事务里，回滚后一条都没少
        assertEquals(3, rowCount())
    }

    @Test
    fun `清空历史`() {
        runBlocking {
            dao.saveHistory(record("a"))
            dao.saveHistory(record("b"))
            dao.clearAll()
        }

        assertEquals(0, rowCount())
        assertNull(runBlocking { dao.getHistory("a") })
    }

    @Test
    fun `缓存清单在异步刷新后按更新时间倒序`() {
        runBlocking {
            dao.saveHistory(record("old", updatedAt = 1L))
            dao.saveHistory(record("new", updatedAt = 2L))
        }
        awaitUntil("historyFlow 刷出两条") { dao.historyFlow.value.size == 2 }
        assertEquals(listOf("new", "old"), dao.historyFlow.value.map { it.comicId })
    }
}
