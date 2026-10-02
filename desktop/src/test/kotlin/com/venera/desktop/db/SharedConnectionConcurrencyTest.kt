package com.venera.desktop.db

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.FavoriteDao
import com.venera.compose.data.db.FavoriteRecord
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R21 的判据：桌面侧是**一条共享连接**，而 `data/db` 的 DAO 是进程级单例、
 * UI 协程池会从多个线程来碰。这里锁死 `JdbcSqliteDatabase` 的连接级串行化：
 * 跨线程的事务互斥（而不是互相混进同一笔事务），同线程重入仍然允许 exec/query 照走。
 */
class SharedConnectionConcurrencyTest {
    private lateinit var store: TestDatabase

    @Before
    fun setUp() {
        store = TestDatabase("concurrency")
        CoreDbSchema.ensureOn(store.db)
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun record(comicId: String, title: String) = FavoriteRecord(
        comicId = comicId,
        title = title,
        author = "",
        coverUrl = "u",
        sourceName = "拷贝漫画",
        createdAt = 1L,
    )

    @Test
    fun `八个线程各自一笔事务互不吞行`() {
        val failures = AtomicInteger()
        val started = CountDownLatch(8)
        val go = CountDownLatch(1)
        val threads = (0 until 8).map { t ->
            thread(name = "db-write-$t") {
                started.countDown()
                go.await()
                val dao = FavoriteDao(store.source)
                runCatching {
                    runBlocking {
                        (0 until 25).forEach { i -> dao.addFavorite(record("t${t}_$i", "标题 $i")) }
                    }
                }.onFailure { failures.incrementAndGet() }
            }
        }
        started.await()
        go.countDown()
        threads.forEach { it.join(30_000) }

        assertEquals("并发写入有线程抛了", 0, failures.get())
        assertEquals(200, store.db.query("SELECT comic_id FROM comic_favorite").size)
    }

    @Test
    fun `一笔事务没走完之前别的线程一句 SQL 也插不进来`() {
        val insertSql = "INSERT INTO comic_favorite (comic_id, title, author, cover_url, source_name, " +
            "folder_name, tags, has_update, latest_chapter, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val insideStarted = CountDownLatch(1)
        val releaseHolder = CountDownLatch(1)
        val holderFinished = AtomicBoolean(false)

        // 持锁线程：插一行 → 停在事务里等放行 → 再插一行 → 故意抛，整笔回滚
        val holderThread = thread(name = "db-holder") {
            runCatching {
                store.db.inTransaction {
                    store.db.exec(insertSql, "held-1", "在事务里插的", null, "u", "jm", "默认", "", 0, "", 1L)
                    insideStarted.countDown()
                    releaseHolder.await()
                    store.db.exec(insertSql, "held-2", "跟着一起回滚", null, "u", "jm", "默认", "", 0, "", 1L)
                    throw IllegalStateException("故意让事务失败")
                }
            }
            holderFinished.set(true)
        }
        insideStarted.await()

        // 另一个线程此刻要写：串行化生效时它必须排在持锁线程那笔事务结束之后
        val outsiderThread = thread(name = "db-outsider") {
            store.db.exec(insertSql, "outsider", "自己的一笔", null, "u", "jm", "默认", "", 0, "", 1L)
        }
        outsiderThread.join(500)
        // join 超时返回 ⇒ outsider 还活着：这就是"确实被挡在锁外"的直白断言。
        // 不再用 join(10_000) 干等 —— 上一版那 10 秒超时让整类固定花 12.4s，却什么都没断言。
        assertTrue(
            "串行化未生效：持锁事务还没走完，outsiderThread 就已经写完了",
            outsiderThread.isAlive,
        )
        releaseHolder.countDown()
        holderThread.join()
        outsiderThread.join()

        assertTrue("持锁线程没把事务走完", holderFinished.get())
        val ids = store.db.query("SELECT comic_id FROM comic_favorite").map { it.string("comic_id") }
        // 事务里两行都回滚掉了
        assertTrue("回滚不彻底：$ids", listOf("held-1", "held-2").none { it in ids })
        // 别的线程那行没被卷进这次回滚 —— 说明它自成一笔，而不是混进了别人的事务
        assertTrue("外部线程的写入被吞掉了（跨线程事务混在一起）：$ids", "outsider" in ids)
    }

    @Test
    fun `同一线程在事务块里继续读写不许自锁`() {
        store.db.inTransaction {
            store.db.exec(
                "INSERT INTO comic_favorite (comic_id, title, author, cover_url, source_name, " +
                    "folder_name, tags, has_update, latest_chapter, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "reentrant", "可重入", null, "u", "jm", "默认", "", 0, "", 1L
            )
            assertEquals(1, store.db.query("SELECT comic_id FROM comic_favorite").size)
        }
        assertEquals(1, store.db.query("SELECT comic_id FROM comic_favorite").size)
    }

    @Test
    fun `同线程嵌套事务仍然抛而不是装作支持`() {
        val e = runCatching {
            store.db.inTransaction {
                store.db.inTransaction { }
            }
        }.exceptionOrNull()
        assertTrue(e is IllegalStateException)
        assertTrue(e!!.message!!.contains("嵌套"))
    }
}
