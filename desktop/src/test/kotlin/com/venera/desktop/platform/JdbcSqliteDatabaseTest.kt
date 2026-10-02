package com.venera.desktop.platform

import com.venera.compose.data.db.SchemaSql
import com.venera.compose.data.platform.SqlType
import com.venera.compose.data.platform.quoteIdentifier
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JDBC 通道判据：SchemaSql 全量 DDL 能建表、插入能回读、v2 DROP 重建语义不变、
 * 事务中途抛必须回滚且异常原样传出、SqlRow.typeOf 五种存储类逐一锁死。
 * 每条用例一个临时 .db 文件，跑完删掉。
 */
class JdbcSqliteDatabaseTest {
    private lateinit var dbFile: File
    private lateinit var db: JdbcSqliteDatabase

    @Before fun setUp() {
        dbFile = File.createTempFile("r1f-sql-", ".db")
        db = JdbcSqliteDatabase(dbFile)
        SchemaSql.V1_CREATE_STATEMENTS.forEach { db.exec(it) }
    }

    @After fun tearDown() {
        db.close()
        dbFile.delete()
    }

    @Test fun `建表后插入历史行能读回`() {
        db.exec(
            "INSERT INTO comic_history(comic_id, title, author, cover_url, source_name, " +
                "last_chapter_title, last_chapter_index, last_page_index, total_pages, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            "c1", "标题", "作者", "https://cover", "jm", "第 1 话", 0, 3, 40, 123L
        )
        val rows = db.query(
            "SELECT comic_id, title, updated_at FROM comic_history WHERE comic_id = ?", "c1"
        )
        assertEquals(1, rows.size)
        val r = rows[0]
        assertEquals("c1", r.string("comic_id"))
        assertEquals("标题", r.string("TITLE"))           // 列名大小写不敏感，与 SQLite 对齐
        assertEquals(123L, r.long("updated_at"))
        assertFalse(r.isNull("title"))
    }

    @Test fun `v2 DROP 重建后历史清空但表还在`() {
        db.exec(
            "INSERT INTO comic_history(comic_id, title, author, cover_url, source_name, " +
                "last_chapter_title, last_chapter_index, last_page_index, total_pages, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            "c1", "标题", null, "https://cover", "jm", "第 1 话", 0, 3, 40, 123L
        )
        // 照搬 onUpgrade 的 v2 分支：先 DROP 三张表，再跑一遍建表全集
        SchemaSql.V2_DROP_STATEMENTS.forEach { db.exec(it) }
        SchemaSql.V1_CREATE_STATEMENTS.forEach { db.exec(it) }
        assertEquals(0, db.query("SELECT COUNT(*) AS n FROM comic_history").first().long("n").toInt())
        assertEquals(1, db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='comic_history'").size)
    }

    @Test fun `事务中途抛则回滚且异常原样传出`() {
        val boom = RuntimeException("事务中途炸了")
        val thrown = runCatching {
            db.inTransaction {
                db.exec(
                    "INSERT INTO comic_history(comic_id, title, author, cover_url, source_name, " +
                        "last_chapter_title, last_chapter_index, last_page_index, total_pages, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    "tx", "T", null, "u", "jm", "c", 0, 0, 1, 1L
                )
                throw boom
            }
        }.exceptionOrNull()
        assertSame(boom, thrown)   // 不许吞、不许换包装
        assertEquals(0, db.query("SELECT COUNT(*) AS n FROM comic_history").first().long("n").toInt())
    }

    @Test fun `typeOf 覆盖五种存储类`() {
        db.exec("CREATE TABLE t_types(i INTEGER, f FLOAT, s TEXT, b BLOB, n)")
        db.exec("INSERT INTO t_types(i, f, s, b, n) VALUES (?, ?, ?, ?, ?)", 42L, 1.5, "文", byteArrayOf(1, 2, 3), null)
        val r = db.query("SELECT i, f, s, b, n FROM t_types").single()
        assertEquals(SqlType.INTEGER, r.typeOf("i"))
        assertEquals(SqlType.FLOAT, r.typeOf("f"))
        assertEquals(SqlType.TEXT, r.typeOf("s"))
        assertEquals(SqlType.BLOB, r.typeOf("b"))
        assertEquals(SqlType.NULL, r.typeOf("n"))
        assertTrue(r.isNull("n"))
        assertEquals(1.5, r.double("f"), 1e-9)
        assertArrayEquals(byteArrayOf(1, 2, 3), r.bytes("b"))
    }

    @Test fun `未知列名抛明确异常`() {
        val r = db.query("SELECT comic_id FROM comic_history LIMIT 0")
        // 空结果集不触发读列，先插一行再取不存在的列
        db.exec(
            "INSERT INTO comic_history(comic_id, title, author, cover_url, source_name, " +
                "last_chapter_title, last_chapter_index, last_page_index, total_pages, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            "c1", "T", null, "u", "jm", "c", 0, 0, 1, 1L
        )
        assertTrue(r.isEmpty())
        val row = db.query("SELECT comic_id FROM comic_history").single()
        val e = runCatching { row.string("不存在的列") }.exceptionOrNull()
        assertTrue(e is IllegalStateException)
        assertTrue(e!!.message!!.contains("不存在的列"))
    }

    @Test fun `注入串作为表名只建成一张表且原表还在`() {
        val evil = "x`; DROP TABLE comic_favorite; --"
        db.exec("CREATE TABLE IF NOT EXISTS ${quoteIdentifier(evil)} (id TEXT)")
        // 恶意串整体成为一个表名，comic_favorite 分毫未动
        assertEquals(1, db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", evil).size)
        assertEquals(1, db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='comic_favorite'").size)
    }
}
