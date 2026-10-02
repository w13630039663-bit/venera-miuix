package com.venera.desktop.db

import com.venera.compose.data.db.FavoritesPreferences
import com.venera.compose.data.db.SqlDatabaseSource
import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.platform.SqlRow
import com.venera.desktop.platform.JdbcSqliteDatabase
import java.io.File

/**
 * 一个临时 SQLite 库文件 + 一个 [SqlDatabaseSource]。
 *
 * reader/writer 给的是同一条 JDBC 连接 —— 桌面侧 Task 5 就是这么接的（一库一连接两头用），
 * 所以这些用例跑的就是将来真跑在桌面上那份代码路径。
 */
class TestDatabase(nameHint: String) : AutoCloseable {
    val file: File = File.createTempFile("r1f-$nameHint-", ".db")
    val db: SqlDatabase = JdbcSqliteDatabase(file)

    val source = object : SqlDatabaseSource {
        override fun reader(): SqlDatabase = db
        override fun writer(): SqlDatabase = db
    }

    override fun close() {
        db.close()
        file.delete()
    }
}

/**
 * 把第 [failOn] 次**写入**换成抛的包装，用来验「事务中途抛 ⇒ 整笔回滚」。
 * 查询照常放行：`folderNames` 这类读操作不该被算进写入次数。
 * [insert] 与 [exec] 共用同一个计数器 —— 被模拟的是"第 N 次落盘"，不是"第 N 次调哪个方法"。
 */
class ExplodingSqlDatabase(
    private val delegate: SqlDatabase,
    private val failOn: Int,
) : SqlDatabase {
    private var calls = 0

    override fun exec(sql: String, vararg args: Any?) {
        if (++calls == failOn) throw IllegalStateException("模拟第 $failOn 次写入失败：$sql")
        delegate.exec(sql, *args)
    }

    override fun insert(sql: String, vararg args: Any?): Long {
        if (++calls == failOn) throw IllegalStateException("模拟第 $failOn 次写入失败：$sql")
        return delegate.insert(sql, *args)
    }

    override fun query(sql: String, vararg args: Any?): List<SqlRow> = delegate.query(sql, *args)

    override fun inTransaction(block: () -> Unit) = delegate.inTransaction(block)

    override fun close() = delegate.close()
}

/**
 * 把任意一条连接包成"读写都走它"的 [SqlDatabaseSource]：
 * 用例要把 [ExplodingSqlDatabase] 之类的替身递给某一层的 store 时用。
 */
fun SqlDatabase.asSource(): SqlDatabaseSource = object : SqlDatabaseSource {
    override fun reader(): SqlDatabase = this@asSource
    override fun writer(): SqlDatabase = this@asSource
}

/**
 * 三项偏好的替身。
 *
 *
 * [followUpdatesFolder] 用私有字段 + `override val` 实现，不写 `override var`：
 * 接口的 `setFollowUpdatesFolder` 与 var 自动生成的 setter 是同一个 JVM 签名，会撞车。
 */
class FakeFavoritesPreferences(
    override var newFavoriteAddTo: String? = "start",
    override var moveFavoriteAfterRead: String? = null,
    followUpdatesFolder: String? = null,
) : FavoritesPreferences {
    private var followedFolder: String? = followUpdatesFolder

    override val followUpdatesFolder: String? get() = followedFolder

    override fun setFollowUpdatesFolder(folder: String?) {
        followedFolder = folder
    }
}

/**
 * 轮询等一个条件成立。
 *
 * DAO 的 `StateFlow` 刷新、管理器的 `init()` 都是 IO 线程上的异步动作（改造前就是这样，
 * 本轮没改），所以测试不能假设调完写操作缓存就更新了。超时即抛，不静默放过。
 */
fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(20)
    }
    throw AssertionError("等待超时（${timeoutMs}ms）：$what")
}
