package com.venera.desktop

import com.venera.compose.data.db.ReadingStatsStore
import com.venera.compose.data.db.SchemaSql
import com.venera.compose.data.db.SqlDatabaseSource
import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.platform.SqlRow
import com.venera.desktop.platform.JdbcSqliteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 钉住「桌面首页的数字来自**真库**」—— 不是编的、也不是空壳。
 *
 * ## 为什么要有这一条
 *
 * 2026-10-20 之前，首页那三格摆的是 `DesktopHomeStats.Empty`（恒 0）。
 * 那种写法**永远不会红**：没人能靠测试发现"数字是假的"，因为假数字与
 * "库里确实没记录"长得一模一样。
 *
 * 这一条的做法：先往一颗**临时库**里写几条真实记录，再让
 * [DesktopHomeStatsSource.readFrom] 去读，然后断言读回来的三个数字与
 * 直接用 SQL 算出来的一致。
 *
 * ## 为什么用临时库而不是真库
 *
 * 真库（`%LOCALAPPDATA%\venera\db\venera_core.db`）的内容随主人读什么而变，
 * 断言不了具体数字。临时库是这条判据自己造的，数字是确定的。
 */
class DesktopHomeStatsSourceTest {

    private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 建表用的 DDL：直接用生产那份 `SchemaSql`，不手抄一句会漂的 `CREATE TABLE`。 */
    private fun createSchema(db: SqlDatabase) {
        SchemaSql.V1_CREATE_STATEMENTS.forEach { db.exec(it) }
    }

    @Test
    fun `首页三个数字与库里的真值一致`() = runBlocking {
        val dir = Files.createTempDirectory("venera-home-stats").toFile()
        try {
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val dayBefore = today.minusDays(2)
            val weekStart = today.minusDays(today.dayOfWeek.value - 1L)

            val seeded = mutableListOf<Triple<String, Int, String>>()
            seeded += Triple("今天读的", 42, today.format(FMT))
            seeded += Triple("昨天读的", 20, yesterday.format(FMT))
            seeded += Triple("前天读的", 12, dayBefore.format(FMT))
            if (dayBefore.isBefore(weekStart)) {
                seeded += Triple("本周早些读的", 5, weekStart.format(FMT))
            }

            val dbFile = File(dir, "probe.db")
            val seedDb = JdbcSqliteDatabase(dbFile)
            createSchema(seedDb)
            val seedSource = OnceSource(seedDb)
            val seedStore = ReadingStatsStore(seedSource)
            seeded.forEachIndexed { i, (title, pages, date) ->
                seedStore.insertSession(
                    comicId = "probe-$i",
                    comicTitle = title,
                    sourceName = "探针源",
                    tags = "",
                    chapterTitle = "第 1 话",
                    pagesRead = pages,
                    durationSeconds = 60L * pages,
                    readDate = date,
                    createdAt = 1_000L + i,
                )
            }

            // 用同一颗库算期望值（走 store 的 SQL，不经过被测的聚合代码）
            val expectedStore = ReadingStatsStore(seedSource)
            val expectedToday = expectedStore.summaryRows()
                .filter { it.readDate == today.format(FMT) }
                .sumOf { it.pagesRead }
            val expectedWeek = expectedStore.trendRows(weekStart.format(FMT)).sumOf { it.pages }
            val expectedStreak = expectedStore.streakDays(
                expectedStore.distinctReadDates(),
                today.format(FMT),
                yesterday.format(FMT),
            ) { d -> LocalDate.parse(d, FMT).minusDays(1).format(FMT) }

            val got = DesktopHomeStatsSource.readFrom(seedSource)

            assertEquals("今日页数没读对 —— 说明数字不是从库里来的", expectedToday, got.todayPages)
            assertEquals("本周累计没读对", expectedWeek, got.weekPages)
            assertEquals("连续天数没读对", expectedStreak, got.streakDays)
            assertTrue("今日页数不该是 0 —— 那就是没读库", got.todayPages > 0)
            assertTrue("读得到就不该有失败说明，实际=${got.note}", got.note == null)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `库打不开时如实报成因 而不是假装 0`() = runBlocking {
        // 库打不开必须给 note，不许悄悄返回全 0 ——
        // 全 0 会被读成「今天读了 0 页」，而实际是「库根本打不开」，两件事。
        val broken = OnceSource(object : SqlDatabase {
            override fun query(sql: String, vararg args: Any?): List<SqlRow> =
                throw IllegalStateException("库打不开（判据探针）")
            override fun exec(sql: String, vararg args: Any?) =
                throw IllegalStateException("库打不开（判据探针）")
            override fun insert(sql: String, vararg args: Any?): Long =
                throw IllegalStateException("库打不开（判据探针）")
            override fun inTransaction(block: () -> Unit) =
                throw IllegalStateException("库打不开（判据探针）")
            override fun close() = Unit
        })
        val got = DesktopHomeStatsSource.readFrom(broken)
        assertTrue(
            "读不到库时 note 必须有话说，否则屏上会显示成「今天读了 0 页」。实际 note=${got.note}",
            got.note != null,
        )
    }

    /** 一颗包住单个连接的 [SqlDatabaseSource]。 */
    private class OnceSource(private val db: SqlDatabase) : SqlDatabaseSource {
        override fun reader(): SqlDatabase = db
        override fun writer(): SqlDatabase = db
    }
}
