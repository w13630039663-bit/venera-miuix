package com.venera.desktop.db

import com.venera.compose.data.db.ComicSourceDao
import com.venera.compose.data.db.CoreDbSchema
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * `comic_source` 表的读写：首次播种五条默认源、顺序、启用位，以及再次构造不重复播种。
 *
 * 播种口径（谁默认开、谁默认关、sort_order 从 0 到 4）逐条对着改造前的 `checkAndSeedDefaults`。
 */
class ComicSourceDaoTest {
    private lateinit var store: TestDatabase

    @Before
    fun setUp() {
        store = TestDatabase("source")
        CoreDbSchema.ensureOn(store.db)
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun `空表首次构造按 sort_order 播下五条默认源`() {
        val dao = ComicSourceDao(store.source)

        assertEquals(
            listOf("copymanga", "picacg", "mangadex", "ehentai", "nhentai"),
            dao.sourcesFlow.value.map { it.sourceId },
        )
        assertEquals(
            listOf(true, true, true, false, false),
            dao.sourcesFlow.value.map { it.isEnabled },
        )
        assertEquals(listOf(0, 1, 2, 3, 4), dao.sourcesFlow.value.map { it.sortOrder })

        val row = store.db.query("SELECT * FROM comic_source WHERE source_id = 'copymanga'").single()
        assertEquals("拷贝漫画", row.string("name"))
        assertEquals("1.0.0", row.string("version"))
        assertEquals("", row.string("icon_url"))
        assertEquals(1L, row.long("is_enabled"))
        assertEquals("{}", row.string("config_json"))
    }

    @Test
    fun `再构造一次不重复播种`() {
        ComicSourceDao(store.source)
        ComicSourceDao(store.source)

        assertEquals(5, store.db.query("SELECT source_id FROM comic_source").size)
    }

    @Test
    fun `开关某个源只改 is_enabled 其余列不动`() {
        val dao = ComicSourceDao(store.source)

        runBlocking { dao.toggleSource("ehentai", true) }

        val row = store.db.query("SELECT * FROM comic_source WHERE source_id = 'ehentai'").single()
        assertEquals(1L, row.long("is_enabled"))
        assertEquals("E-Hentai", row.string("name"))
        assertEquals(3L, row.long("sort_order"))
        // 关回去再读，缓存流也跟着翻
        runBlocking { dao.toggleSource("copymanga", false) }
        assertEquals(false, dao.sourcesFlow.value.first { it.sourceId == "copymanga" }.isEnabled)
    }
}
