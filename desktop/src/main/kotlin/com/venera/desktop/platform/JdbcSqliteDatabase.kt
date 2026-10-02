package com.venera.desktop.platform

import com.venera.compose.data.platform.MappedSqlRow
import com.venera.compose.data.platform.PathProvider
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

    /** 正式入口走 PathProvider：库文件固定落在 `db/` 子目录下（与 Android 侧同构）。 */
    constructor(paths: PathProvider, name: String) : this(File(paths.subDir("db"), "$name.db"))

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
                stmt.executeUpdate()
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
     * Kotlin 的数字族 → sqlite-jdbc 认得的值形态。
     *
     * 这一层不换算就交给 `setObject` 的话，`Int` 会变成 `INTEGER` 列里的 4 字节整数（没问题），
     * 但 `ULong`/`Char`/`Boolean` 这些 JDBC 不认的类型会被驱动直接拒绝；
     * 而 `Float` 交给 `setObject` 会写成 REAL 的 4 字节舍入值，与 Android 侧
     * `execSQL(sql, Object[])` 的 `bindDouble` 结果不同 —— 两端必须给同一个字节。
     */
    private fun bindable(arg: Any): Any = when (arg) {
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
