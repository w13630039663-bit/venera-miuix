package com.venera.desktop.db

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.FavoritesPreferences
import com.venera.compose.data.db.LocalFavoriteDbSchema
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.db.SqlDatabaseSource
import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.platform.SqlRow
import com.venera.compose.data.platform.quoteIdentifier
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 「一个收藏夹一张表」这套动态表在真 sqlite 上的行为。
 *
 * 覆盖 R18 点名的几项：动态建表（中文 / 带空格 / 带反引号的名字一律过 quoteIdentifier）、
 * 收藏增删查、搬运与排序、追更列的按需 ALTER，以及事务中途抛 ⇒ 整笔回滚。
 * 每条判据都对着改造前的 Android 实现比过，位置策略与去重口径这类细节尤其没动。
 *
 * **生命周期口径**：每个用例建过的管理器都登记在 [managers] 里，[tearDown] **先逐个
 * `close()`（取消内部刷新协程）再关连接**。顺序反了就会在测试输出里留下一批
 * `database connection closed` 的未捕获异常 —— 上一轮就是这么炸的，别再退回去。
 */
class LocalFavoritesManagerTest {
    private lateinit var core: TestDatabase
    private lateinit var fav: TestDatabase
    private lateinit var prefs: FakeFavoritesPreferences
    private lateinit var manager: LocalFavoritesManager

    /** 本用例建过的全部管理器（含替身 source 的那颗），tearDown 里统一先 close 再关库。 */
    private val managers = mutableListOf<LocalFavoritesManager>()

    /** 建一个并登记，杜绝"忘了 close"的新增点。 */
    private fun newManager(
        core: SqlDatabaseSource,
        favorites: SqlDatabaseSource,
        prefs: FavoritesPreferences,
    ): LocalFavoritesManager =
        LocalFavoritesManager(core, favorites, prefs).also { managers += it }

    @Before
    fun setUp() {
        core = TestDatabase("legacy-core")
        fav = TestDatabase("local-fav")
        CoreDbSchema.ensureOn(core.db)
        LocalFavoriteDbSchema.ensureOn(fav.db)
        prefs = FakeFavoritesPreferences()
        manager = newManager(core.source, fav.source, prefs)
    }

    @After
    fun tearDown() {
        // 先 close 再关连接：close 掉在飞的 refreshFolders，才轮到关连接。
        // 一颗 close 有抛（比如超时）不许跳过其余：全部关完（含两颗库）再重抛第一颗 ——
        // 既不静默，也不把"上一轮清掉的 database connection closed 噪音"从缝隙里放回来。
        var first: Throwable? = null
        for (m in managers) {
            try {
                m.close()
            } catch (e: Throwable) {
                if (first == null) first = e else first.addSuppressed(e)
            }
        }
        for (db in listOf(fav, core)) {
            try {
                db.close()
            } catch (e: Throwable) {
                if (first == null) first = e else first.addSuppressed(e)
            }
        }
        first?.let { throw it }
    }

    private fun item(id: String, sourceKey: String = "jm", name: String = "作品 $id") = FavoriteItem(
        id = id,
        name = name,
        author = "作者",
        sourceKey = sourceKey,
        tags = listOf("恋爱", "校园"),
        coverPath = "https://cover/$id",
    )

    private fun tableNames(): List<String?> =
        fav.db.query("SELECT name FROM sqlite_master WHERE type='table'").map { it.string("name") }

    private fun countIn(table: String): Long =
        fav.db.query("SELECT count(*) AS c FROM ${quoteIdentifier(table)}").single().long("c")

    // region ---- 动态建表与表名转义 ----

    @Test
    fun `中文带空格带反引号的夹子名各自建成一张表且名字逐字相同`() {
        val names = listOf("我的 收藏", "带`反引号`的夹", "追更2026")
        names.forEach { n -> assertEquals(n, runBlocking { manager.createFolder(n) }) }

        assertEquals((names + "默认").toSet(), runBlocking { manager.currentFolders() }.toSet())
        names.forEach { assertTrue("缺表 $it", it in tableNames()) }

        // 名字过了转义仍然能正常读写
        runBlocking { assertTrue(manager.addComic("带`反引号`的夹", item("c1"))) }
        assertEquals(1L, countIn("带`反引号`的夹"))
        assertEquals("c1", runBlocking { manager.getFolderComics("带`反引号`的夹") }.single().id)
    }

    @Test
    fun `注入串整体成为表名不动其他表`() {
        val evil = "x`; DROP TABLE folder_order; --"
        runBlocking { manager.createFolder(evil) }

        assertTrue(evil in tableNames())
        // folder_order 还在，默认夹也在
        assertEquals(1, fav.db.query("SELECT folder_name FROM folder_order WHERE folder_name = ?", "默认").size)
    }

    @Test
    fun `重名夹子在允许改名时后缀数字否则抛`() {
        assertEquals("默认0", runBlocking { manager.createFolder("默认", renameWhenInvalidName = true) })
        val e = runCatching { runBlocking { manager.createFolder("默认0") } }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test
    fun `删夹同时删表并清掉排序与绑定记录`() {
        runBlocking {
            val folder = manager.createFolder("要删的")
            manager.linkFolderToNetwork(folder, "jm", "线上夹")
            assertTrue(folder in manager.currentFolders())

            manager.deleteFolder(folder)

            assertFalse(folder in manager.currentFolders())
            assertFalse(folder in tableNames())
            assertEquals(0, fav.db.query("SELECT folder_name FROM folder_order WHERE folder_name = ?", folder).size)
            assertEquals(0, fav.db.query("SELECT folder_name FROM folder_sync WHERE folder_name = ?", folder).size)
        }
    }

    @Test
    fun `改名保留条目与顺序值并同步追更指向`() {
        runBlocking {
            val before = manager.createFolder("改名前")
            manager.addComic(before, item("c1"))
            manager.updateOrder(listOf("默认", before))
            prefs.setFollowUpdatesFolder(before)

            manager.rename(before, "改名后")

            assertEquals(listOf("默认", "改名后"), manager.currentFolders())
            assertEquals(1L, countIn("改名后"))
            assertEquals("改名后", prefs.followUpdatesFolder)
            // 顺序值跟着搬过来，不是被重置成 0
            assertEquals(
                1L,
                fav.db.query("SELECT order_value FROM folder_order WHERE folder_name = ?", "改名后")
                    .single().long("order_value"),
            )
        }
    }

    // endregion

    // region ---- 条目读写 ----

    @Test
    fun `新收藏默认插到开头`() {
        runBlocking {
            val folder = manager.createFolder("开头夹")
            manager.addComic(folder, item("a"))
            manager.addComic(folder, item("b"))
            assertEquals(listOf("b", "a"), manager.getFolderComics(folder).map { it.id })
        }
    }

    @Test
    fun `偏好设为 end 时新收藏插到末尾`() {
        prefs.newFavoriteAddTo = "end"
        runBlocking {
            val folder = manager.createFolder("尾部夹")
            manager.addComic(folder, item("a"))
            manager.addComic(folder, item("b"))
            assertEquals(listOf("a", "b"), manager.getFolderComics(folder).map { it.id })
        }
    }

    @Test
    fun `指定 order 时按给定的排序值落库`() {
        runBlocking {
            val folder = manager.createFolder("指定序")
            assertTrue(manager.addComic(folder, item("a"), order = 7))
            assertEquals(
                7L,
                fav.db.query("SELECT display_order FROM ${quoteIdentifier(folder)}").single().long("display_order"),
            )
        }
    }

    @Test
    fun `条目字段逐列落库并读回`() {
        runBlocking {
            val folder = manager.createFolder("字段夹")
            assertTrue(manager.addComic(folder, item("c1", name = "带标签的作品")))
            val row = fav.db.query("SELECT * FROM ${quoteIdentifier(folder)}").single()
            assertEquals("c1", row.string("id"))
            assertEquals("带标签的作品", row.string("name"))
            assertEquals("作者", row.string("author"))
            assertEquals("jm".hashCode().toLong(), row.long("type"))
            assertEquals("jm", row.string("source_key"))
            assertEquals("恋爱,校园", row.string("tags"))
            assertEquals("恋爱,校园", row.string("translated_tags"))
            assertEquals("https://cover/c1", row.string("cover_path"))
            assertTrue(row.string("time")!!.isNotBlank())

            val loaded = manager.getFolderComics(folder).single()
            assertEquals(listOf("恋爱", "校园"), loaded.tags)
            assertEquals("jm", loaded.sourceKey)
            assertEquals(-1, loaded.displayOrder)
        }
    }

    @Test
    fun `重复添加返回 false 且主键上不写重`() {
        runBlocking {
            val folder = manager.createFolder("去重夹")
            assertTrue(manager.addComic(folder, item("c1")))
            assertFalse(manager.addComic(folder, item("c1")))
            assertEquals(1L, countIn(folder))
            assertTrue(manager.isExist("c1", "jm"))
            assertEquals(listOf(folder), manager.find("c1", "jm"))
            // 换个源就是另一条
            assertFalse(manager.isExist("c1", "pica"))
        }
    }

    @Test
    fun `批量添加保留顺序空 id 跳过重复不计数`() {
        runBlocking {
            val folder = manager.createFolder("批量夹")
            manager.addComic(folder, item("c1"))

            val batch = listOf(item("c1"), FavoriteItem(id = "  ", name = "空 id"), item("c2"), item("c3"), item("c2"))
            assertEquals(2, manager.addComics(folder, batch))
            assertEquals(3L, countIn(folder))
            assertEquals(listOf("c1", "c2", "c3"), manager.getFolderComics(folder).map { it.id })
            assertEquals(0, manager.addComics(folder, emptyList()))
        }
    }

    @Test
    fun `批量添加中途抛则整笔回滚且异常原样传出`() {
        val boomManager = newManager(
            core.source,
            object : SqlDatabaseSource {
                override fun reader(): SqlDatabase = fav.db
                // 第二次写入（也就是批量里的第二条 INSERT）炸掉
                override fun writer(): SqlDatabase = ExplodingSqlDatabase(fav.db, failOn = 2)
            },
            prefs,
        )
        val folder = "回滚夹"
        runBlocking {
            manager.createFolder(folder)
            assertEquals(0L, countIn(folder))

            val boom = runCatching {
                boomManager.addComics(folder, listOf(item("a"), item("b"), item("c")))
            }.exceptionOrNull()

            assertTrue(boom is IllegalStateException)
            assertTrue(boom!!.message!!.contains("模拟第 2 次写入失败"))
            // 第一条已经在同一笔事务里，回滚后一条都没落
            assertEquals(0L, countIn(folder))
        }
    }

    @Test
    fun `删除与批量删除按 id 加 type 精确命中`() {
        runBlocking {
            val folder = manager.createFolder("删除夹")
            manager.addComic(folder, item("c1", sourceKey = "jm"))
            manager.addComic(folder, item("c1", sourceKey = "pica"))
            assertEquals(2L, countIn(folder))

            manager.deleteComicWithId(folder, "c1", "jm")
            assertEquals(1L, countIn(folder))
            assertEquals(listOf("pica"), manager.getFolderComics(folder).map { it.sourceKey })

            manager.batchDeleteComics(folder, listOf(item("c1", sourceKey = "pica")))
            assertEquals(0L, countIn(folder))
        }
    }

    @Test
    fun `整夹之外删除会扫遍所有收藏夹`() {
        runBlocking {
            val one = manager.createFolder("全删夹一")
            val two = manager.createFolder("全删夹二")
            manager.addComic(one, item("c1"))
            manager.addComic(two, item("c1"))

            manager.batchDeleteComicsInAllFolders(listOf(item("c1")))

            assertEquals(0L, countIn(one))
            assertEquals(0L, countIn(two))
        }
    }

    @Test
    fun `移动与复制`() {
        runBlocking {
            val from = manager.createFolder("来源夹")
            val to = manager.createFolder("目标夹")
            manager.addComic(from, item("a"))
            manager.addComic(from, item("b"))
            manager.addComic(to, item("keep"))

            manager.moveFavorite(from, to, "a", "jm")
            assertEquals(listOf("b"), manager.getFolderComics(from).map { it.id })
            // 官方口径：单条移动插到目标开头
            assertEquals(listOf("a", "keep"), manager.getFolderComics(to).map { it.id })

            manager.batchCopyFavorites(from, to, manager.getFolderComics(from))
            assertEquals(listOf("b"), manager.getFolderComics(from).map { it.id })
            // 批量复制追加到目标末尾（display_order 接着最大值往后排）
            assertEquals(listOf("a", "keep", "b"), manager.getFolderComics(to).map { it.id })

            manager.batchMoveFavorites(from, to, manager.getFolderComics(from))
            assertEquals(0L, countIn(from))

            // 目标夹已有同一条时 IGNORE 语义不写重，源照样清掉
            manager.addComic(from, item("keep"))
            manager.moveFavorite(from, to, "keep", "jm")
            assertEquals(3L, countIn(to))
            assertEquals(0L, countIn(from))
        }
    }

    @Test
    fun `手动排序按给定顺序重写 display_order`() {
        runBlocking {
            val folder = manager.createFolder("排序夹")
            listOf("a", "b", "c").forEach { manager.addComic(folder, item(it)) }
            assertEquals(listOf("c", "b", "a"), manager.getFolderComics(folder).map { it.id })

            manager.reorder(listOf(item("a"), item("b"), item("c")), folder)
            assertEquals(listOf("a", "b", "c"), manager.getFolderComics(folder).map { it.id })
        }
    }

    @Test
    fun `更新条目信息与改标签`() {
        runBlocking {
            val folder = manager.createFolder("更新夹")
            manager.addComic(folder, item("c1"))

            manager.updateInfo(folder, item("c1", name = "改名了").copy(coverPath = "https://new"))
            val updated = manager.getFolderComics(folder).single()
            assertEquals("改名了", updated.name)
            assertEquals("https://new", updated.coverPath)

            manager.editTags("c1", folder, listOf("新标签"))
            assertEquals(listOf("新标签"), manager.getFolderComics(folder).single().tags)
        }
    }

    @Test
    fun `搜索与跨夹去重`() {
        runBlocking {
            val one = manager.createFolder("搜索夹一")
            val two = manager.createFolder("搜索夹二")
            manager.addComic(one, item("c1", name = "夜航的船"))
            manager.addComic(two, item("c1", name = "夜航的船"))
            manager.addComic(two, item("c2", name = "别的作品"))

            assertEquals(listOf("c1"), manager.searchInFolder(one, "夜航").map { it.id })
            assertTrue(manager.searchInFolder(one, "不存在的词").isEmpty())
            // 空关键词等于列全量（官方口径）
            assertEquals(listOf("c1"), manager.searchInFolder(one, "   ").map { it.id })
            assertTrue(manager.search("   ").isEmpty())
            // 跨夹去重按 id + type 判等
            assertEquals(listOf("c1", "c2"), manager.getAllComics().map { it.id }.sorted())
            assertEquals(listOf("c1"), manager.search("夜航").map { it.id })
            assertEquals(listOf(one, two).toSet(), manager.find("c1", "jm").toSet())
            assertEquals(2, manager.folderComics(two))
        }
    }

    @Test
    fun `清空只清条目不删表`() {
        runBlocking {
            val folder = manager.createFolder("清空夹")
            manager.addComic(folder, item("c1"))
            manager.clearAll()
            assertEquals(0L, countIn(folder))
            assertTrue(folder in manager.currentFolders())
        }
    }

    // endregion

    // region ---- 追更 ----

    @Test
    fun `追更三列按需 ALTER 且标新与清标`() {
        runBlocking {
            val folder = manager.createFolder("追更夹")
            manager.addComic(folder, item("c1"))
            assertFalse(
                fav.db.query("PRAGMA table_info(${quoteIdentifier(folder)})")
                    .any { it.string("name") == "has_new_update" }
            )

            manager.prepareTableForFollowUpdates(folder)
            assertEquals(0, manager.countUpdates(folder))

            manager.updateUpdateTime(folder, "c1", "jm", "2026-10-02")
            assertEquals(1, manager.countUpdates(folder))
            val withUpdate = manager.getUpdates(folder).single()
            assertEquals("2026-10-02", withUpdate.updateTime)
            assertTrue(withUpdate.hasNewUpdate)
            // 写入服务端更新时间时顺带就把检查时间翻成"刚查过"（改造前正是这条 UPDATE 语句的口径），
            // 节流判据靠它
            val checkTimeAfterUpdate = withUpdate.lastCheckTime
            assertTrue("updateUpdateTime 没写检查时间", checkTimeAfterUpdate != null && checkTimeAfterUpdate > 0L)

            // updateCheckTime 只翻检查时间，不动更新状态
            manager.updateCheckTime(folder, "c1", "jm")
            val afterThrottle = manager.getComicsWithUpdatesInfo(folder).single()
            assertTrue(afterThrottle.lastCheckTime!! >= checkTimeAfterUpdate!!)
            assertTrue(afterThrottle.hasNewUpdate)

            // 同一更新时间再查一次 ⇒ 不算新
            manager.updateUpdateTime(folder, "c1", "jm", "2026-10-02")
            assertEquals(0, manager.countUpdates(folder))

            prefs.setFollowUpdatesFolder(folder)
            manager.updateUpdateTime(folder, "c1", "jm", "2026-10-03")
            assertEquals(1, manager.countUpdates(folder))
            manager.markAsRead("c1", "jm")
            assertEquals(0, manager.countUpdates(folder))
        }
    }

    @Test
    fun `读完按偏好移到末尾并清掉追更标记`() {
        prefs.moveFavoriteAfterRead = "end"
        runBlocking {
            val folder = manager.createFolder("读完夹")
            prefs.setFollowUpdatesFolder(folder)
            manager.addComic(folder, item("a"))
            manager.addComic(folder, item("b"))
            manager.prepareTableForFollowUpdates(folder)
            manager.updateUpdateTime(folder, "b", "jm", "2026-10-02")
            assertEquals(listOf("b", "a"), manager.getFolderComics(folder).map { it.id })

            manager.onRead("b", "jm")

            assertEquals(listOf("a", "b"), manager.getFolderComics(folder).map { it.id })
            assertEquals(0, manager.countUpdates(folder))
        }
    }

    // endregion

    // region ---- 绑定 / 迁移 ----

    @Test
    fun `网络收藏夹绑定的写入与读出`() {
        runBlocking {
            val folder = manager.createFolder("绑定夹")
            manager.linkFolderToNetwork(folder, "jm", "线上同名夹")
            val info = manager.getFolderSync().single()
            assertEquals(folder, info.folder)
            assertEquals("jm", info.sourceKey)
            assertEquals("线上同名夹", info.sourceFolder)

            manager.unlinkFolderFromNetwork(folder)
            assertTrue(manager.getFolderSync().isEmpty())
        }
    }

    @Test
    fun `旧单表收藏一次性迁进每夹一表并清空旧表`() {
        core.db.exec(
            "INSERT OR REPLACE INTO comic_favorite " +
                "(comic_id, title, author, cover_url, source_name, folder_name, tags, has_update, latest_chapter, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            "legacy1", "旧收藏", "旧作者", "https://old/cover", "拷贝漫画", "老夹子", "热血,战斗", 1, "第 9 话", 123L
        )
        val migrating = newManager(core.source, fav.source, FakeFavoritesPreferences())

        migrating.init()

        awaitUntil("老夹子出现在收藏夹清单") { migrating.folders.value.contains("老夹子") }
        val moved = runBlocking { migrating.getFolderComics("老夹子") }.single()
        assertEquals("legacy1", moved.id)
        assertEquals("旧收藏", moved.name)
        assertEquals("拷贝漫画", moved.sourceKey)
        assertEquals("https://old/cover", moved.coverPath)
        assertEquals(listOf("热血", "战斗"), moved.tags)
        assertEquals(1L, countIn("老夹子"))
        // 旧表清空，防二次搬家
        assertEquals(0, core.db.query("SELECT comic_id FROM comic_favorite").size)
        // has_update=1 且带 latest_chapter 的行会顺带补出追更列并标新
        assertTrue(
            fav.db.query("PRAGMA table_info(${quoteIdentifier("老夹子")})")
                .any { it.string("name") == "has_new_update" }
        )
        assertEquals(1, runBlocking { migrating.countUpdates("老夹子") })
        assertEquals(
            "第 9 话",
            runBlocking { migrating.getComicsWithUpdatesInfo("老夹子") }.single().updateTime,
        )

        // 二次 init 不会再搬一次
        val again = newManager(core.source, fav.source, FakeFavoritesPreferences())
        again.init()
        awaitUntil("二次 init 完成") { again.folders.value.contains("老夹子") }
        assertEquals(1L, countIn("老夹子"))
    }

    @Test
    fun `init 后续上收藏夹清单与条目数缓存`() {
        runBlocking {
            manager.createFolder("缓存夹")
            manager.addComic("缓存夹", item("c1"))
            manager.addComic("缓存夹", item("c2"))
        }
        val reader = newManager(core.source, fav.source, FakeFavoritesPreferences())

        reader.init()

        awaitUntil("folders 缓存续上缓存夹") { reader.folders.value.contains("缓存夹") }
        assertEquals(2, reader.counts.value["缓存夹"])
    }

    // endregion

    // region ---- 生命周期 ----

    @Test
    fun `close 后不再排后台刷新且直查照常`() {
        runBlocking { manager.createFolder("关停首夹") }
        awaitUntil("folders 缓存含关停首夹") { manager.folders.value.contains("关停首夹") }

        manager.close()

        // 公开挂起方法跑在调用方协程里、不经过管理器内部 scope ⇒ close 后写入与直查仍可用
        runBlocking { manager.createFolder("关停后新增") }
        // 给"没被取消成功"的在飞刷新一点时间：close 若失效，此刻缓存早该带上新夹
        Thread.sleep(200)
        assertFalse(
            "close 后缓存不该再被后台刷新：${manager.folders.value}",
            manager.folders.value.contains("关停后新增"),
        )
        assertTrue("直查应读到 close 后新建的夹", runBlocking { manager.currentFolders() }.contains("关停后新增"))
    }

    @Test
    fun `在飞刷新等不完时有超时上界且抛出来不静默`() {
        // 只包 reader（后台刷新走的那一头）；写入照常，用例自己的准备动作不受替身影响。
        val blockingReader = BlockingFirstQuerySqlDatabase(fav.db)
        val blockingSource = object : SqlDatabaseSource {
            override fun reader(): SqlDatabase = blockingReader
            override fun writer(): SqlDatabase = fav.db
        }
        val m = newManager(core.source, blockingSource, prefs)
        try {
            runBlocking { m.createFolder("在飞夹") } // notifyChanged 排的后台刷新会在第一笔查询上卡住
            assertTrue(
                "后台刷新没进查询 —— 替身没被用住，这条用例就恒绿了",
                blockingReader.queryEntered.await(2, TimeUnit.SECONDS),
            )
            val boom = runCatching { m.close(awaitMillis = 250) }
            val err = boom.exceptionOrNull()
            assertTrue("期望 IllegalStateException，实际：$err", err is IllegalStateException)
            assertTrue("报错要点名『在飞』这件事，实际：${err?.message}", err!!.message?.contains("在飞") == true)
        } finally {
            // 放行在飞段：tearDown 里 close（默认上界）才等得完，连接才关得干净
            blockingReader.release.countDown()
        }
    }

    // endregion
}

/**
 * 把**第一笔查询**卡住的连接包装：查询进入时数一次闩、等放行闩，其余全部照常委托。
 * 用来钉 R25 的 close() 超时判据 —— 在飞刷新没等完 ⇒ 抛，不静默说已关干净。
 */
private class BlockingFirstQuerySqlDatabase(private val delegate: SqlDatabase) : SqlDatabase {
    private val blocked = AtomicBoolean(false)
    val queryEntered = CountDownLatch(1)
    val release = CountDownLatch(1)

    override fun query(sql: String, vararg args: Any?): List<SqlRow> {
        if (blocked.compareAndSet(false, true)) {
            queryEntered.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "10 秒内没等到放行 —— 用例的收尾环节断了" }
        }
        return delegate.query(sql, *args)
    }

    override fun exec(sql: String, vararg args: Any?) = delegate.exec(sql, *args)

    override fun insert(sql: String, vararg args: Any?) = delegate.insert(sql, *args)

    override fun inTransaction(block: () -> Unit) = delegate.inTransaction(block)

    override fun close() = delegate.close()
}
