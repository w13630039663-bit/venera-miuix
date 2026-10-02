package com.venera.desktop.db

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.LocalFavoriteDbSchema
import com.venera.compose.data.db.SchemaSql
import com.venera.compose.data.db.userVersion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R20 的判据：建表/迁移的编排只有一份，Android 与桌面都走它。
 *
 * 这里在真 sqlite（sqlite-jdbc）上把三个版本场景各跑一遍，钉的是改造前 `VeneraDatabase`
 * 那两个回调的行为，包括 v1 分支那条**提前 return**（v1 起的库不会补 v3 的三张表）。
 */
class CoreDbMigrationTest {
    private lateinit var store: TestDatabase

    @Before
    fun setUp() {
        store = TestDatabase("core-migration")
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun tables(): Set<String> =
        store.db.query("SELECT name FROM sqlite_master WHERE type='table'")
            .map { it.string("name") ?: "" }.toSet()

    private fun indexes(): Set<String> =
        store.db.query("SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%'")
            .map { it.string("name") ?: "" }.toSet()

    @Test
    fun `新库建表得到 v3 全集并把版本写回`() {
        CoreDbSchema.ensureOn(store.db)

        assertEquals(3, userVersion(store.db))
        listOf(
            "comic_history", "comic_favorite", "comic_source",
            "reading_stats", "favorite_images", "content_guard_rules",
        ).forEach { assertTrue("缺表 $it", it in tables()) }
        // 索引条数与 SchemaSql 里的索引语句一一对应，少一条就是编排漏跑
        assertEquals(
            SchemaSql.V1_CREATE_STATEMENTS.count { it.startsWith("CREATE INDEX") }.toLong(),
            indexes().size.toLong(),
        )
        assertTrue("idx_history_updated_at" in indexes())
    }

    @Test
    fun `同一份编排重复执行不再动库`() {
        CoreDbSchema.ensureOn(store.db)
        store.db.exec(
            "INSERT INTO comic_source(source_id, name, version) VALUES (?, ?, ?)", "jm", "拷贝漫画", "1.0.0"
        )

        CoreDbSchema.ensureOn(store.db)

        assertEquals(1, store.db.query("SELECT source_id FROM comic_source").size)
        assertEquals(3, userVersion(store.db))
    }

    @Test
    fun `v1 升 v2 走 DROP 重建且保留提前 return 的既有语义`() {
        // 手工造一个「v1 已经建过表」的库：跑建表全集但把版本停在 1
        CoreDbSchema.create(store.db)
        store.db.exec(
            "INSERT INTO comic_history(comic_id, title, author, cover_url, source_name, " +
                "last_chapter_title, last_chapter_index, last_page_index, total_pages, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            "c1", "旧进度", null, "u", "jm", "第 1 话", 0, 0, 10, 1L
        )
        assertEquals(0, userVersion(store.db))
        store.db.exec("PRAGMA user_version = 1")

        CoreDbSchema.ensureOn(store.db)

        // 三张老表被 DROP 后重建：进度没了，表还在 —— 这就是 v2 分支的可观察后果，不许"改良"成 ALTER
        assertEquals(0, store.db.query("SELECT comic_id FROM comic_history").size)
        assertTrue("comic_history" in tables())
        assertTrue("comic_favorite" in tables())
        assertTrue("comic_source" in tables())
        // 原 onUpgrade 在 v2 分支里跑完建表全集就 return，不再走 v3 那段。今天 [V1_CREATE_STATEMENTS]
        // 本身已含那三张表，所以这条 return 无可观察差异 —— 但它仍然是编排的一部分，逐字保着。
        assertEquals(3, userVersion(store.db))
    }

    @Test
    fun `v2 升 v3 补齐三张表且不清历史`() {
        // 造一个「v2 时代的库」：三张后补的表还没有，版本停在 2
        CoreDbSchema.create(store.db)
        store.db.exec("DROP TABLE reading_stats")
        store.db.exec("DROP TABLE favorite_images")
        store.db.exec("DROP TABLE content_guard_rules")
        store.db.exec(
            "INSERT INTO comic_history(comic_id, title, author, cover_url, source_name, " +
                "last_chapter_title, last_chapter_index, last_page_index, total_pages, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            "c1", "进度要留着", null, "u", "jm", "第 1 话", 0, 3, 10, 1L
        )
        store.db.exec("PRAGMA user_version = 2")

        CoreDbSchema.ensureOn(store.db)

        listOf("reading_stats", "favorite_images", "content_guard_rules").forEach {
            assertTrue("v3 该补的表 $it 没建出来", it in tables())
        }
        // 这条路径不 DROP 任何东西：历史原样在
        assertEquals("进度要留着", store.db.query("SELECT title FROM comic_history").single().string("title"))
        assertEquals(3, userVersion(store.db))
    }

    @Test
    fun `库版本高于代码时期望即抛不许降级`() {
        CoreDbSchema.ensureOn(store.db)
        store.db.exec("PRAGMA user_version = 99")

        val e = runCatching { CoreDbSchema.ensureOn(store.db) }.exceptionOrNull()
        assertTrue(e is IllegalStateException)
        assertTrue(e!!.message!!.contains("99"))
    }

    @Test
    fun `local_favorite 建表得到两张元表与默认夹`() {
        LocalFavoriteDbSchema.ensureOn(store.db)

        assertTrue("folder_order" in tables())
        assertTrue("folder_sync" in tables())
        // 默认夹既建了自己的表，也在 folder_order 里占了一行（顺序值 0）
        assertTrue("默认" in tables())
        val row = store.db.query("SELECT order_value FROM folder_order WHERE folder_name = ?", "默认").single()
        assertEquals(0L, row.long("order_value"))
        assertEquals(1, userVersion(store.db))
    }

    @Test
    fun `local_favorite 重复建表不写重默认夹`() {
        LocalFavoriteDbSchema.ensureOn(store.db)
        LocalFavoriteDbSchema.ensureOn(store.db)

        assertEquals(1, store.db.query("SELECT folder_name FROM folder_order").size)
    }
}
