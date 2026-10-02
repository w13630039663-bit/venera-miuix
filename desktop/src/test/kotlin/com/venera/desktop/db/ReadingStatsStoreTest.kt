package com.venera.desktop.db

import com.venera.compose.data.db.CoreDbSchema
import com.venera.compose.data.db.ReadingStatsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * `reading_stats` 的累计与查询（R18：真 `JdbcSqliteDatabase` + 临时 `.db`，不是 mock）。
 *
 * 这一层替 `ReadingStatsManager` 挡住的正是"写了但统计页永远是 0"那一类错：
 * 累加、按源分组、日期区间、Top 榜排序、以及**写不进去必须抛**。
 */
class ReadingStatsStoreTest {

    private lateinit var store: TestDatabase
    private lateinit var stats: ReadingStatsStore

    @Before
    fun setUp() {
        store = TestDatabase("stats")
        CoreDbSchema.ensureOn(store.db)
        stats = ReadingStatsStore(store.source)
    }

    @After
    fun tearDown() = store.close()

    private fun session(
        comicId: String,
        sourceName: String = "jm",
        pages: Int = 1,
        seconds: Long = 10,
        date: String = "2026-10-02",
        tags: String = "",
        createdAt: Long = 1L,
        title: String = "本子",
    ) = stats.insertSession(comicId, title, sourceName, tags, "第1话", pages, seconds, date, createdAt)

    @Test
    fun `两次会话的页数与时长按行累加且总本数按 源 加 ID 去重`() {
        session("g1", pages = 3, seconds = 30)
        session("g1", pages = 2, seconds = 20)
        session("g2", pages = 5, seconds = 5)

        val rows = stats.summaryRows()
        assertEquals(3, rows.size)
        assertEquals(10, rows.sumOf { it.pagesRead })
        assertEquals(55L, rows.sumOf { it.durationSeconds })
        // 同一本漫画（同源同 ID）的两条记录只算一本
        assertEquals(
            setOf("jm_g1", "jm_g2"),
            rows.map { "${it.sourceName}_${it.comicId}" }.toSet(),
        )
    }

    @Test
    fun `不同源的同 ID 是两个本而不是一个`() {
        session("g1", sourceName = "jm")
        session("g1", sourceName = "copy_manga")

        assertEquals(2, stats.summaryRows().map { "${it.sourceName}_${it.comicId}" }.distinct().size)
    }

    @Test
    fun `趋势只取区间内的日子并按日期合计`() {
        session("g1", pages = 3, seconds = 30, date = "2026-10-01")
        session("g1", pages = 2, seconds = 20, date = "2026-10-02")
        session("g1", pages = 9, seconds = 90, date = "2026-09-30") // 区间外

        val rows = stats.trendRows("2026-10-01")
        assertEquals(
            listOf("2026-10-01" to (3 to 30L), "2026-10-02" to (2 to 20L)),
            rows.map { it.readDate to (it.pages to it.seconds) },
        )
    }

    @Test
    fun `Top 榜按时长优先再按页数且限制条数`() {
        session("短", seconds = 5, pages = 100)
        session("长", seconds = 100, pages = 1)
        session("同长多页", seconds = 100, pages = 5)

        val top = stats.topComics(2)
        assertEquals(2, top.size)
        // 时长并列时页数多的在前 —— 这条排序是改造前 ORDER BY 的原样
        assertEquals("同长多页", top[0].comicId)
        assertEquals("长", top[1].comicId)
        assertEquals(5, top[0].pages)
        assertEquals(100L, top[0].seconds)
    }

    @Test
    fun `题材分布只取带标签的行且不越过日期下限`() {
        session("g1", pages = 4, tags = "女仆\u001F咖啡", date = "2026-10-02")
        session("g2", pages = 7, tags = "", date = "2026-10-02")
        session("g3", pages = 8, tags = "女仆", date = "2026-09-01")

        val rows = stats.taggedRows("2026-10-01")
        assertEquals(1, rows.size)
        assertEquals("2026-10-02", rows[0].readDate)
        assertEquals(4, rows[0].pages)
        assertEquals(listOf("女仆", "咖啡"), rows[0].tags.split('\u001F'))
    }

    @Test
    fun `反查作者的读法交回 comic_id 与标签串两列`() {
        session("g1", tags = "Author:某人")
        session("g2", tags = "")

        val pairs = stats.comicTagPairs()
        assertEquals(1, pairs.size)
        assertEquals("g1", pairs[0].comicId)
        assertEquals("Author:某人", pairs[0].tags)
    }

    @Test
    fun `连续打卡从今天往前数且断一天就停`() {
        session("g1", date = "2026-10-03")
        session("g1", date = "2026-10-02")
        session("g1", date = "2026-09-30") // 10-01 空着

        val dates = stats.distinctReadDates()
        val streak = stats.streakDays(dates, "2026-10-03", "2026-10-02", ::previousDay)
        assertEquals(2, streak)
    }

    @Test
    fun `今天没读但昨天读了连续仍然成立`() {
        session("g1", date = "2026-10-02")
        session("g1", date = "2026-10-01")

        val dates = stats.distinctReadDates()
        assertEquals(2, stats.streakDays(dates, "2026-10-03", "2026-10-02", ::previousDay))
    }

    @Test
    fun `前天及更早的记录不构成连续`() {
        session("g1", date = "2026-09-28")

        assertEquals(0, stats.streakDays(stats.distinctReadDates(), "2026-10-03", "2026-10-02", ::previousDay))
    }

    @Test
    fun `库是空的时候各条读法交回空而不是编一个零头`() {
        assertEquals(0, stats.summaryRows().size)
        assertEquals(0, stats.trendRows("2026-10-01").size)
        assertEquals(0, stats.topComics(10).size)
        assertEquals(0, stats.distinctReadDates().size)
        assertEquals(0, stats.streakDays(emptyList(), "2026-10-03", "2026-10-02", ::previousDay))
    }

    @Test
    fun `写入失败当场抛出而不是吞掉这一次会话`() {
        // 这就是本轮补上的那条：改造前 `catch (_: Exception) {}` 让它变成"读了书、统计页没数据"。
        val exploding = ExplodingSqlDatabase(store.db, failOn = 1)
        try {
            ReadingStatsStore(exploding.asSource()).insertSession(
                "g1", "本子", "jm", "", "第1话", 1, 10, "2026-10-02", 1L
            )
            fail("写入失败本该抛出")
        } catch (e: IllegalStateException) {
            assertTrue("实际消息：${e.message}", e.message!!.contains("模拟"))
        }
        // 一行都没进去
        assertEquals(0, stats.summaryRows().size)
    }

    @Test
    fun `写坏的表结构读出来是抛而不是空表`() {
        // 下面这份 DDL 不是第二份建表事实源，它是**故意坏掉**的结构（把 NOT NULL 摘掉），
        // 只为验一件事：库里真出现"逻辑上不许为 null"的列是 null 时，读法必须点名抛，
        // 而不是走 `?: ""` 那条把它扮成空标题（4a 定的 RowRead 口径，本轮不许放宽）。
        store.db.exec("DROP TABLE reading_stats")
        store.db.exec(
            "CREATE TABLE reading_stats (id INTEGER PRIMARY KEY AUTOINCREMENT, comic_id TEXT," +
                " comic_title TEXT, source_name TEXT, tags TEXT, chapter_title TEXT," +
                " pages_read INTEGER, duration_seconds INTEGER, read_date TEXT, created_at INTEGER)"
        )
        store.db.exec(
            "INSERT INTO reading_stats (comic_id, comic_title, source_name, tags, chapter_title," +
                " pages_read, duration_seconds, read_date, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
            "g1", null, "jm", "", "", 1, 10, "2026-10-02", 1L,
        )

        try {
            stats.topComics(10) // 这一条要读 comic_title
            fail("comic_title 读出 null 本该抛")
        } catch (e: IllegalStateException) {
            assertTrue("实际消息：${e.message}", e.message!!.contains("comic_title"))
        }
    }

    /** 与 manager 那份 SimpleDateFormat 走法等价的日期回退（JVM 侧用 java.time 直给）。 */
    private fun previousDay(date: String): String = LocalDate.parse(date).minusDays(1).toString()
}
