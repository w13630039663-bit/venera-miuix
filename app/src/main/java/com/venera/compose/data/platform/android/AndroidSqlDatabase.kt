package com.venera.compose.data.platform.android

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.venera.compose.data.platform.MappedSqlRow
import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.platform.SqlRow
import com.venera.compose.data.platform.SqlType

/**
 * SqlDatabase 的 Android 实现：贴在系统 SQLiteDatabase 上，不改变它的任何执行语义。
 *
 * 事务走 beginTransaction / setTransactionSuccessful / endTransaction：
 * block 抛出时不标记成功，endTransaction 自动回滚，原异常经 finally 原样传出（不吞、不换包装）。
 *
 * 一条系统 API 的限制要说明：rawQuery 的绑定参数只收 String[]，所以 query 侧把参数逐个
 * toString 后交给 SQLite 的列亲和性转换；exec 走 Object[] 绑定，数值原样进。
 */
class AndroidSqlDatabase(private val db: SQLiteDatabase) : SqlDatabase {

    override fun exec(sql: String, vararg args: Any?) {
        try {
            if (args.isEmpty()) db.execSQL(sql) else db.execSQL(sql, args)
        } catch (e: SQLiteException) {
            throw IllegalStateException("执行失败：$sql：${e.message}", e)
        }
    }

    /**
     * 走 `SQLiteStatement`：编译后的语句在同一条连接上执行并直接回报 rowid，
     * 因此不需要（也不能）再查一次 `last_insert_rowid()` —— 那是连接级状态，
     * 而 SQLiteDatabase 的写连接来自连接池。
     *
     * 与旧写法 `SQLiteDatabase.insert` 的差别只在失败形态：旧写法失败返回 -1 并只打一条日志，
     * 这里把 -1 与 SQLiteException 都换成立即抛并带上 SQL 原文（调用方原本就把 -1 当失败，判据不变）。
     */
    override fun insert(sql: String, vararg args: Any?): Long {
        val statement = try {
            db.compileStatement(sql)
        } catch (e: SQLiteException) {
            throw IllegalStateException("编译失败：$sql：${e.message}", e)
        }
        try {
            args.forEachIndexed { i, arg ->
                val index = i + 1
                when (arg) {
                    null -> statement.bindNull(index)
                    is String -> statement.bindString(index, arg)
                    is ByteArray -> statement.bindBlob(index, arg)
                    // 布尔按 SQLite 的既有口径写 1/0（系统语句没有 bindBoolean）
                    is Boolean -> statement.bindLong(index, if (arg) 1L else 0L)
                    // 整数族走 bindLong：与改造前 execSQL(sql, Object[]) 交给 SQLite 亲和性转换的结果一致，
                    // 而 SQLite 的 INTEGER 是 64 位，Float/Double 落进整数列时会被截断
                    is Int, is Long, is Short, is Byte -> statement.bindLong(index, (arg as Number).toLong())
                    is Double, is Float -> statement.bindDouble(index, (arg as Number).toDouble())
                    // 旧写法走 execSQL(sql, Object[])，SQLite 自己按列亲和性转换任意类型；
                    // 这里没有对应 bind 方法，宁可抛也不拿 null 顶替（静默写 null = 数据错到看不出来）。
                    else -> throw IllegalArgumentException(
                        "参数 ${index} 的类型 ${arg.javaClass.name} 无法绑定到 SQLite 语句：$sql"
                    )
                }
            }
            val rowId = statement.executeInsert()
            if (rowId < 0) {
                throw IllegalStateException("插入未成功（SQLite 回 $rowId，未产生新行）：$sql")
            }
            return rowId
        } catch (e: SQLiteException) {
            throw IllegalStateException("执行失败：$sql：${e.message}", e)
        } finally {
            statement.close()
        }
    }

    override fun query(sql: String, vararg args: Any?): List<SqlRow> {
        val bindArgs = if (args.isEmpty()) null else args.map { it?.toString() }.toTypedArray()
        return try {
            db.rawQuery(sql, bindArgs).use { cursor -> readAll(cursor) }
        } catch (e: SQLiteException) {
            throw IllegalStateException("查询失败：$sql：${e.message}", e)
        }
    }

    override fun inTransaction(block: () -> Unit) {
        db.beginTransaction()
        try {
            block()
            db.setTransactionSuccessful()
        } finally {
            // 没走到 setTransactionSuccessful 时这里即回滚；block 的异常继续往外抛
            db.endTransaction()
        }
    }

    /**
     * 交给系统默认实现——这里不主动 close()： SQLiteDatabase 的生命周期归 SQLiteOpenHelper
     * （writableDatabase/readableDatabase 是引用语义），门面持有者无权关整库；
     * 真正拆 helper 由 Task 4 迁移调用点时统一处理。
     */
    override fun close() = Unit

    private fun readAll(cursor: Cursor): List<SqlRow> {
        val count = cursor.columnCount
        val names = (0 until count).map { cursor.getColumnName(it) }
        val rows = ArrayList<SqlRow>(cursor.count)
        while (cursor.moveToNext()) {
            val values = LinkedHashMap<String, Any?>(count)
            val types = LinkedHashMap<String, SqlType>(count)
            for (i in 0 until count) {
                types[names[i]] = mapType(cursor.getType(i))
                values[names[i]] = if (cursor.isNull(i)) null else when (cursor.getType(i)) {
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                    Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                    else -> throw IllegalStateException("列「${names[i]}」返回了未映射的 Cursor 类型 ${cursor.getType(i)}")
                }
            }
            rows.add(MappedSqlRow(values, types))
        }
        return rows
    }

    /** Cursor.getType 的 FIELD_TYPE_* → SqlType，五种存储类一一对齐。 */
    private fun mapType(fieldType: Int): SqlType = when (fieldType) {
        Cursor.FIELD_TYPE_NULL -> SqlType.NULL
        Cursor.FIELD_TYPE_INTEGER -> SqlType.INTEGER
        Cursor.FIELD_TYPE_FLOAT -> SqlType.FLOAT
        Cursor.FIELD_TYPE_STRING -> SqlType.TEXT
        Cursor.FIELD_TYPE_BLOB -> SqlType.BLOB
        else -> throw IllegalStateException("未知的 Cursor 字段类型 $fieldType，请先补映射")
    }
}

/**
 * 只读打开一个**已经存在的** SQLite 文件：画廊的离线词典副本、外部归档里的
 * `local_favorite.db` / `history.db`。两处都不是本应用维护的库，所以不走 SQLiteOpenHelper，
 * 也就没有建表/迁移那一段。
 *
 * 与 [AndroidSqlDatabase] 的差别只在 `close()`：这里关的是**刚由本函数打开的那条连接**
 * （调用方拿它写 `.use { }`，与改造前 `SQLiteDatabase.openDatabase(...).use { }` 同一形状）；
 * 包 helper 连接的那份不许关，整库生命周期归 SQLiteOpenHelper。
 * 打不开 ⇒ 抛并带路径，处置交回调用方（词典那条记 Log，归档那条让整包导入如实失败）。
 */
fun openReadOnlySqlDatabase(path: String): SqlDatabase {
    val db = try {
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
    } catch (e: SQLiteException) {
        throw IllegalStateException("只读打开失败：$path：${e.message}", e)
    }
    val delegate = AndroidSqlDatabase(db)
    return object : SqlDatabase by delegate {
        override fun close() = db.close()
    }
}
