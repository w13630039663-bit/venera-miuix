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

    @Test fun `insert 回报新行 rowid 且失败抛并带 SQL 原文`() {
        // 门面新增成员 insert 的判据（4b，供 FavoriteImagesStore / GuardRuleStore 的 rowid 语义）：
        // 行号是**这一条**新行的，主键冲突不许悄悄给个 -1。
        val historyInsert = "INSERT INTO comic_history(comic_id, title, author, cover_url, source_name, " +
            "last_chapter_title, last_chapter_index, last_page_index, total_pages, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        assertEquals(1L, db.insert(historyInsert, "a", "A", null, "u", "jm", "c", 0, 0, 1, 1L))
        assertEquals(2L, db.insert(historyInsert, "b", "B", null, "u", "jm", "c", 0, 0, 1, 2L))
        // 同一张表里 rowid 可指定：撞已存在的主键必须抛，且消息里带 SQL 原文
        db.exec("CREATE TABLE t_pk(id INTEGER PRIMARY KEY)")
        db.insert("INSERT INTO t_pk(id) VALUES (?)", 9L)
        val e = runCatching { db.insert("INSERT INTO t_pk(id) VALUES (?)", 9L) }.exceptionOrNull()
        assertTrue("实际异常：$e", e is IllegalStateException)
        assertTrue("实际消息：${e!!.message}", e.message!!.contains("INSERT INTO t_pk"))
        // 抛了也不许有第二行
        assertEquals(1, db.query("SELECT id FROM t_pk WHERE id = 9").size)
        // 两端一致的那一半：`INSERT OR IGNORE` 撞唯一键时驱动不报错、只是**没写进行**，
        // 而 SQLite 的 last_insert_rowid() 这时交回的是上一条插入的 rowid —— 不接住
        // executeUpdate() 的返回值就会"假装成功"并把旧 rowid 给调用方（Android 侧
        // executeInsert() 回 -1 当场抛，桌面侧必须同样抛）。
        val ignored = runCatching { db.insert("INSERT OR IGNORE INTO t_pk(id) VALUES (?)", 9L) }.exceptionOrNull()
        assertTrue("INSERT OR IGNORE 撞主键本该抛，实际没抛：$ignored", ignored is IllegalStateException)
        assertTrue("实际消息：${ignored!!.message}", ignored.message!!.contains("INSERT OR IGNORE INTO t_pk"))
        // 上面那一句没成功，rowid 序列也不许被它污染：紧接着的真插入拿到的仍是新行
        assertEquals(3L, db.insert(historyInsert, "c", "C", null, "u", "jm", "c", 0, 0, 1, 3L))
        assertEquals(1, db.query("SELECT id FROM t_pk WHERE id = 9").size)
    }

    @Test fun `绑定表：布尔给一到零、Float 升 Double、整数族给 Long、其余原样`() {
        // [JdbcSqliteDatabase.bindable] 是"驱动拿到什么形态"这张表的唯一出处，直着钉它：
        // 走公开的 exec 钉不住 —— 存储层读回来的形态是 sqlite-jdbc 自己决定的（2026-10-02 实测：
        // 绕开本层直接 setObject，Boolean 已经是 INTEGER 1/0、Float 0.1f 已经是 REAL
        // 0.10000000149011612，与本层输出一字不差），把那几支删掉这条用例照样绿，
        // 所以旧版那句"用例已证明这一层必要"不成立，已从注释与断言里一起撤掉。
        // 本层能被证明的只有它自己写了什么：删掉任何一支、或改成交给驱动的另一种形态，这里就红。
        assertEquals(1L, db.bindable(true))
        assertEquals(0L, db.bindable(false))
        assertEquals(7L, db.bindable(7))
        assertEquals(7L, db.bindable(7L))
        assertEquals(7L, db.bindable(7.toShort()))
        assertEquals(7L, db.bindable(7.toByte()))
        // Float 在交给驱动之前就已升成 Double（0.1f 的精确值是 0.10000000149011612，不是 0.1）
        assertEquals(0.10000000149011612, db.bindable(0.1f))
        assertEquals(0.1, db.bindable(0.1))
        // 不在表里的类型原样交出去，认不认由驱动决定（认不下就在 bind 处抛并点名，见下一条）
        assertEquals("文", db.bindable("文"))
        assertEquals('A', db.bindable('A'))
        val blob = byteArrayOf(1, 2)
        assertSame(blob, db.bindable(blob))
    }

    @Test fun `参数绑定：整数族按 64 位进 INTEGER 且文本与 null 各归各位`() {
        // 存储侧的往返：列不声明亲和性，所以存进去是什么存储类就读回什么。
        // 防的是本层（或往后的改动）把某一族写成文本 —— 那种行在 Android 侧读出来是另一回事。
        db.exec("CREATE TABLE t_bind(i, big, small, tiny, s, n)")
        db.exec(
            "INSERT INTO t_bind(i, big, small, tiny, s, n) VALUES (?, ?, ?, ?, ?, ?)",
            7, 1234567890123L, 3.toShort(), 4.toByte(), "文", null,
        )
        val r = db.query("SELECT i, big, small, tiny, s, n FROM t_bind").single()
        assertEquals(SqlType.INTEGER, r.typeOf("i"))
        assertEquals(7L, r.long("i"))
        assertEquals(1234567890123L, r.long("big"))     // 超出 Int 的值不许在绑定处掉高位
        assertEquals(3L, r.long("small"))
        assertEquals(4L, r.long("tiny"))
        assertEquals(SqlType.TEXT, r.typeOf("s"))
        assertTrue(r.isNull("n"))
        assertEquals(1, db.query("SELECT i FROM t_bind").size)
    }
}
