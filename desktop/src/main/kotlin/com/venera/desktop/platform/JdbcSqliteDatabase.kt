package com.venera.desktop.platform

import com.venera.compose.data.platform.MappedSqlRow
import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.platform.SqlRow
import com.venera.compose.data.platform.SqlType
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Types

/**
 * SqlDatabase 的桌面实现：一条 sqlite-jdbc（org.xerial）连接直连本地 `.db` 文件。
 *
 * 与 JsonKeyValueStore 同立场：不吞连接失败、不做隐式重试、不回退内存库——
 * 驱动不在 classpath、文件建不出、SQL 失败、事务异常都抛，且消息里带上下文（路径或 SQL 原文）。
 *
 * 并发口径（R21）：这里只有**一条共享连接**，而 `data/db` 的 DAO 与收藏管理器是进程级单例、
 * UI 协程池会从多个线程来碰。Android 侧的事务栈是按线程本地的连接走的，桌面侧没有这个条件，
 * 所以连接级串行化补在这里：`exec` / `query` / `inTransaction` / `close` 全部走同一把实例锁
 * （[connectionLock]）。效果是
 *  - 同一线程可重入（事务块里继续 exec/query 是常态，不能自锁）；
 *  - 跨线程互斥：一笔事务的整个块跑完前，别的线程一句 SQL 也插不进来，
 *    而不是两个线程的语句混在同一笔事务里互相提交/回滚。
 * [inTransaction] 标记因此只需要挡"同线程重入"这一种情况（跨线程已经被锁隔开了），
 * 保留 `@Volatile` 是让读到的值对诊断可见，不当作互斥手段。
 */
class JdbcSqliteDatabase(private val file: File) : SqlDatabase {

    private val connection: Connection = try {
        DriverManager.getConnection("jdbc:sqlite:${file.path}")
    } catch (e: SQLException) {
        // 驱动缺失（No suitable driver）与文件不可写都收敛到这一条，消息带路径，不许静默换库
        throw IllegalStateException("建不出 SQLite 连接：${file.absolutePath}：${e.message}", e)
    }

    /** 连接级串行锁：见类注释的并发口径。JVM 监视器可重入，所以事务块内的 SQL 不会自锁。 */
    private val connectionLock = Any()

    // 嵌套标记：单连接没有 savepoint，重入 inTransaction 只能抛，不能装作支持
    @Volatile
    private var inTransaction = false

    override fun exec(sql: String, vararg args: Any?) {
        synchronized(connectionLock) {
            try {
                connection.prepareStatement(sql).use { stmt ->
                    bind(stmt, args)
                    stmt.executeUpdate()
                }
            } catch (e: SQLException) {
                throw IllegalStateException("执行失败：$sql：${e.message}", e)
            }
        }
    }

    override fun insert(sql: String, vararg args: Any?): Long = synchronized(connectionLock) {
        try {
            connection.prepareStatement(sql).use { stmt ->
                bind(stmt, args)
                val updated = stmt.executeUpdate()
                if (updated == 0) {
                    // 与 Android 侧 `executeInsert()` 回 -1 就抛同判据：`INSERT OR IGNORE` 撞了唯一键、
                    // 触发器把写入吞掉等等，都是"这一句没产生新行"。不接住返回值就直接查
                    // `last_insert_rowid()` 的话，SQLite 会把**上一条**插入的 rowid（或 0）交回来，
                    // 桌面端于是既不抛也不报失败 —— 门面 KDoc 写的"失败 ⇒ 抛"就是这么破掉的。
                    throw IllegalStateException("插入未成功（影响行数为 0，未产生新行）：$sql")
                }
                // 单连接 + 本方法全程持锁，所以 last_insert_rowid() 取到的就是刚插的那一行；
                // Android 侧不能这么办（写连接来自连接池），故那边的实现走 SQLiteStatement。
                stmt.connection.prepareStatement("SELECT last_insert_rowid()").use { rowIdStmt ->
                    rowIdStmt.executeQuery().use { rs ->
                        if (!rs.next()) {
                            throw IllegalStateException("取不到新行的 rowid：$sql")
                        }
                        rs.getLong(1)
                    }
                }
            }
        } catch (e: SQLException) {
            throw IllegalStateException("执行失败：$sql：${e.message}", e)
        }
    }

    override fun query(sql: String, vararg args: Any?): List<SqlRow> = synchronized(connectionLock) {
        try {
            connection.prepareStatement(sql).use { stmt ->
                bind(stmt, args)
                stmt.executeQuery().use { rs ->
                    val md = rs.metaData
                    val labels = (1..md.columnCount).map { md.getColumnLabel(it) }
                    buildList<SqlRow> {
                        while (rs.next()) {
                            val values = LinkedHashMap<String, Any?>()
                            val types = LinkedHashMap<String, SqlType>()
                            labels.forEachIndexed { i, label ->
                                val v = rs.getObject(i + 1)
                                values[label] = v
                                types[label] = typeOfValue(v)
                            }
                            add(MappedSqlRow(values, types))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw IllegalStateException("查询失败：$sql：${e.message}", e)
        }
    }

    override fun inTransaction(block: () -> Unit) = synchronized(connectionLock) {
        check(!inTransaction) { "inTransaction 不允许嵌套（sqlite-jdbc 单连接无 savepoint）" }
        val previous = try {
            inTransaction = true
            connection.autoCommit
        } catch (e: SQLException) {
            inTransaction = false
            throw IllegalStateException("读取 autoCommit 失败：${file.absolutePath}", e)
        }
        try {
            connection.autoCommit = false
            block()
            connection.commit()
        } catch (e: Throwable) {
            // 回滚尽力而为；失败作为 suppressed 附在原始异常上——原异常必须原样抛出，不许吞
            try {
                connection.rollback()
            } catch (re: SQLException) {
                if (e !== re) e.addSuppressed(re)
            }
            throw e
        } finally {
            try {
                connection.autoCommit = previous
            } catch (ignored: SQLException) {
                // 连接已废（库文件被删等）：autoCommit 复位失败不该盖掉主异常，close() 兜底
            }
            inTransaction = false
        }
    }

    override fun close() = synchronized(connectionLock) {
        try {
            connection.close()
        } catch (e: SQLException) {
            throw IllegalStateException("关闭 SQLite 连接失败：${file.absolutePath}：${e.message}", e)
        }
    }

    private fun bind(stmt: PreparedStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, arg ->
            try {
                if (arg == null) stmt.setNull(i + 1, Types.NULL) else stmt.setObject(i + 1, bindable(arg))
            } catch (e: SQLException) {
                throw IllegalStateException("第 ${i + 1} 个参数绑定失败（值类型 ${arg?.javaClass?.name ?: "null"}）：${e.message}", e)
            }
        }
    }

    /**
     * Kotlin 的数字族 → JDBC 认得的值形态。这一层是**防御性归一**，不是修复驱动的问题：
     * 2026-10-02 拿 sqlite-jdbc 3.53.4.0 实测（绕开本层直接 `setObject`），
     *  `Boolean true/false` 已经是 INTEGER 1/0、`Float 0.1f` 已经是 REAL 0.10000000149011612、
     *  `Int`/`Long` 已经是 INTEGER —— 与本层的输出**一字不差**。
     * 留着它的理由只有两条：把"按哪一族存"写死在本仓一侧（驱动升级换语义时这里不变），
     * 以及和 Android 的 `bindLong`/`bindDouble` 摆在一起读时对得上。
     *
     * 不要再往外说"不这么写两端字节就不同"：SQLite 的 REAL 恒为 8 字节，没有 4 字节 REAL 这一档，
     * Float 与 Double 在存储层根本区分不出来（`JdbcSqliteDatabaseTest` 因此不再断这两族）。
     * 那两条用例只断本层能决定的事：「绑定表」逐支钉死这张映射表，「参数绑定」走存储侧往返
     * （整数族不截断、文本与 null 各归各位）。没有"驱动拒收的类型当场点名"那种断言 ——
     * 2026-10-02 实测驱动连 `Char` 都收（不抛），编不出能红的"拒收"判据，故撤。
     * 真正跨端要对齐的是 Android `execSQL(sql, Object[])` 交给 SQLite 的列亲和性转换结果，
     * 那只能靠真机回归核，桌面侧的用例证不了。
     *
     * 可见性放成 `internal` 只为一件事：这张映射表本身要能被用例逐条钉住（`JdbcSqliteDatabaseTest`
     * 的「绑定表」）。走公开的 `exec` 反而钉不住 —— 存储层读回来的形态是那台驱动决定的，
     * 把这几支删掉读数也一样，用例就变成一句恒真的话。
     */
    internal fun bindable(arg: Any): Any = when (arg) {
        is Boolean -> if (arg) 1L else 0L // SQLite 没有独立布尔存储类
        is Byte, is Short, is Int, is Long -> (arg as Number).toLong()
        is Float, is Double -> (arg as Number).toDouble()
        else -> arg
    }

    /** 值分类对齐 SQLite 存储类：sqlite-jdbc 的 getObject 只会给出这几族。 */
    private fun typeOfValue(v: Any?): SqlType = when (v) {
        null -> SqlType.NULL
        is ByteArray -> SqlType.BLOB
        is String -> SqlType.TEXT
        is Double, is Float -> SqlType.FLOAT
        is Boolean, is Number -> SqlType.INTEGER // SQLite 无独立布尔存储类，1/0 即 INTEGER
        else -> throw IllegalStateException("sqlite-jdbc 返回了未识别的值类型 ${v.javaClass.name}，请先补映射再放行")
    }
}
