package com.venera.desktop.db

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.FavoriteDao
import com.venera.compose.data.db.FavoriteRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 收藏这一颗心在真 sqlite 上的行为：写入、按夹子读、排序、跨源不串、删除、去重口径。
 *
 * 判据全部对着改造前的 Android 实现逐条比过：`ORDER BY created_at DESC`、
 * `INSERT OR REPLACE`（同主键替换而非新增）、`removeFavorite` 只按 comic_id 匹配（既有口径，
 * 会带走其他源同 ID 的收藏 —— 本轮没改它）。
 */
class FavoriteDaoTest {
    private lateinit var store: TestDatabase
    private lateinit var dao: FavoriteDao

    @Before
    fun setUp() {
        store = TestDatabase("favorite")
        CoreDbSchema.ensureOn(store.db)
        dao = FavoriteDao(store.source)
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun record(
        comicId: String,
        title: String = "标题 $comicId",
        sourceName: String = "拷贝漫画",
        folderName: String = "默认",
        createdAt: Long = 1L,
        hasUpdate: Boolean = false,
    ) = FavoriteRecord(
        comicId = comicId,
        title = title,
        author = "作者",
        coverUrl = "https://cover/$comicId",
        sourceName = sourceName,
        folderName = folderName,
        tags = "恋爱,校园",
        hasUpdate = hasUpdate,
        latestChapter = "第 1 话",
        createdAt = createdAt,
    )

    @Test
    fun `写入后能读回全部字段并按创建时间倒序`() {
        runBlocking {
            dao.addFavorite(record("old", createdAt = 100L))
            dao.addFavorite(record("new", createdAt = 200L, hasUpdate = true))
        }

        val rows = store.db.query("SELECT * FROM comic_favorite ORDER BY created_at DESC")
        assertEquals(listOf("new", "old"), rows.map { it.string("comic_id") })
        val first = rows.first()
        assertEquals("标题 new", first.string("title"))
        assertEquals("作者", first.string("author"))
        assertEquals("https://cover/new", first.string("cover_url"))
        assertEquals("默认", first.string("folder_name"))
        assertEquals("恋爱,校园", first.string("tags"))
        assertEquals(1L, first.long("has_update"))
        assertEquals("第 1 话", first.string("latest_chapter"))
        assertEquals(200L, first.long("created_at"))
    }

    @Test
    fun `缓存里的清单在异步刷新后与库里一致`() {
        runBlocking { dao.addFavorite(record("c1")) }
        awaitUntil("favoritesFlow 刷出 c1") { dao.favoritesFlow.value.any { it.comicId == "c1" } }
        assertEquals(1, dao.favoritesFlow.value.size)
    }

    @Test
    fun `同主键重复写入是替换而不是新增`() {
        runBlocking {
            dao.addFavorite(record("c1", title = "第一版", createdAt = 1L))
            dao.addFavorite(record("c1", title = "第二版", createdAt = 2L))
        }

        val rows = store.db.query("SELECT title, created_at FROM comic_favorite")
        assertEquals(1, rows.size)
        assertEquals("第二版", rows.single().string("title"))
        assertEquals(2L, rows.single().long("created_at"))
    }

    @Test
    fun `不同源的同 ID 是两条收藏`() {
        runBlocking {
            dao.addFavorite(record("c1", sourceName = "拷贝漫画"))
            dao.addFavorite(record("c1", sourceName = "哔咔漫画"))
        }

        assertEquals(2, store.db.query("SELECT source_name FROM comic_favorite").size)
        assertTrue(runBlocking { dao.isFavorite("c1") })
    }

    @Test
    fun `移除按 comic_id 匹配会把各源同 ID 一起带走`() {
        runBlocking {
            dao.addFavorite(record("c1", sourceName = "拷贝漫画"))
            dao.addFavorite(record("c1", sourceName = "哔咔漫画"))
            dao.removeFavorite("c1")
        }

        assertEquals(0, store.db.query("SELECT comic_id FROM comic_favorite").size)
        assertFalse(runBlocking { dao.isFavorite("c1") })
    }

    @Test
    fun `收藏夹清单首位恒为默认且不带重复`() {
        runBlocking {
            dao.addFavorite(record("c1", folderName = "追更"))
            dao.addFavorite(record("c2", folderName = "追更"))
            dao.addFavorite(record("c3", folderName = "默认"))
        }

        assertEquals(listOf("默认", "追更"), runBlocking { dao.getFolders() })
    }

    @Test
    fun `开关收藏两次回到未收藏`() {
        // toggleFavorite 读的是内存缓存，先等 refresh 落地再开关，口径与 UI 上点两下等价
        runBlocking { dao.addFavorite(record("c9")) }
        awaitUntil("缓存里有 c9") { dao.favoritesFlow.value.any { it.comicId == "c9" } }

        dao.toggleFavorite("c9", title = "点出来的", coverUrl = "u")
        assertEquals(0, store.db.query("SELECT comic_id FROM comic_favorite WHERE comic_id = 'c9'").size)

        awaitUntil("缓存里没了 c9") { dao.favoritesFlow.value.none { it.comicId == "c9" } }
        dao.toggleFavorite("c9", title = "点出来的", coverUrl = "u", sourceName = "拷贝漫画")
        val row = store.db.query("SELECT title, tags, has_update FROM comic_favorite WHERE comic_id = 'c9'").single()
        assertEquals("点出来的", row.string("title"))
        assertEquals("", row.string("tags"))
        assertEquals(0L, row.long("has_update"))
    }
}
