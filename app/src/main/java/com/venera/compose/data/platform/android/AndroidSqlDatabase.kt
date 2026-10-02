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
