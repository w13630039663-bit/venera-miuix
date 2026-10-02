package com.venera.compose.data.platform

import java.util.TreeMap

/**
 * SqlDatabase —— SQL 执行的统一门面，与 PathProvider / KeyValueStore 并列。
 *
 * 两端实现：
 *  - Android：`data/platform/android/AndroidSqlDatabase`（包系统 SQLiteDatabase）；
 *  - 桌面：`:desktop` 模块的 `JdbcSqliteDatabase`（org.xerial sqlite-jdbc）。
 *
 * 两端一致的约定（任何一端破例都算降级）：
 *  - SQL 值一律走 `?` 参数绑定；标识符（表名）只能走 [quoteIdentifier]；
 *  - 未知列名直接抛，不许返回 null 蒙混；
 *  - 驱动缺失、连接建不出、执行/事务失败 ⇒ 抛，并把 SQL 原文或路径写进消息，不许静默降级。
 *
 * 继承 [AutoCloseable] 只为一件事：调用方能写 `.use { }`。哪些连接该由取用者关掉、
 * 哪些归 helper 管，仍然由实现自己决定（[close] 的语义见各实现）。
 */
interface SqlDatabase : AutoCloseable {
    /** 执行一条语句（DDL/DML），args 按位置绑定参数。 */
    fun exec(sql: String, vararg args: Any?)

    /**
     * 执行一条 INSERT 并回报新行的 rowid；失败 ⇒ 抛（消息带 SQL 原文）。
     *
     * 为什么门面要有这一条而 [exec] 不够：`FavoriteImagesManager.addFavorite` 与
     * `ContentGuardManager.addRule` 的调用方拿返回的 rowid 判成败
     * （`reader/VeneraReaderScreen.kt:2074` 的 `rowId < 0`、`components/ComicCardContextMenu.kt:93`
     * 的 `addRule(...) >= 0`），而 [exec] 不回报任何东西。
     * 两端都不许用 `SELECT last_insert_rowid()` 另查一次：那是**连接级**的状态，
     * Android 的 SQLiteDatabase 是连接池，取到的可能是别的连接的。
     */
    fun insert(sql: String, vararg args: Any?): Long

    /** 查询并**一次性**把结果读进内存（返回的行不再受游标/连接生命周期牵制）。 */
    fun query(sql: String, vararg args: Any?): List<SqlRow>

    /** 事务执行 block：正常提交；block 抛 ⇒ 回滚后把原异常原样传出（不许吞、不许换包装）。 */
    fun inTransaction(block: () -> Unit)

    /** 关闭底层连接。关闭后继续使用由实现决定抛法，但必须抛。 */
    override fun close()
}

/** SQLite 的五种存储类。[SqlRow.typeOf] 的返回值，Android 端由 Cursor.getType 的 FIELD_TYPE_* 映射而来。 */
enum class SqlType { INTEGER, FLOAT, TEXT, BLOB, NULL }

/** 结果集中一行的只读视图。全部按列名取值，列名大小写不敏感（与 SQLite 一致）。 */
interface SqlRow {
    /**
     * 这一行有没有该列。
     *
     * 存在的理由：`local_favorite.db` 的收藏夹表里，追更三列是运行时按需 `ALTER` 上去的，
     * 「列不存在」是合法状态（改造前用 `Cursor.getColumnIndex(...) < 0` 判）。
     * 除此之外读列一律按名直读，读不到就抛 —— 这个成员只服务那一处「表结构本来就可选」。
     */
    fun has(name: String): Boolean

    fun isNull(name: String): Boolean
    fun typeOf(name: String): SqlType
    fun string(name: String): String?

    /** 读不到整数语义的值时抛；NULL 读作 0（与 android.database.Cursor.getLong 对齐，需区分请先 isNull）。 */
    fun long(name: String): Long

    /** 同 [long]，NULL 读作 0.0。 */
    fun double(name: String): Double

    /** BLOB 之外的列按其文本字节导出（与 Cursor.getBlob 对齐）；NULL 返回 null。 */
    fun bytes(name: String): ByteArray?
}

/** 表名来自用户输入（一个收藏夹一张表，见 LocalFavoriteDatabase），只能走标识符转义；SQL 值一律走参数绑定。 */
fun quoteIdentifier(raw: String): String {
    require(raw.isNotBlank()) { "表名不能为空" }
    require(raw.none { it == '\n' || it == '\r' }) { "表名不能含换行" }
    return "`" + raw.replace("`", "``") + "`"
}

/**
 * [SqlRow] 的两端共用底座：实现方把每行先落成「列名→值」「列名→存储类」两张表再按列名读。
 * Android 端的类型取自 Cursor.getType，JDBC 端取自 getObject 的值分类——都收口到这里，不再各写一份。
 */
class MappedSqlRow(values: Map<String, Any?>, types: Map<String, SqlType>) : SqlRow {

    // CASE_INSENSITIVE_ORDER 对齐 SQLite 列名的大小写不敏感语义
    private val valueMap = TreeMap<String, Any?>(String.CASE_INSENSITIVE_ORDER).apply { putAll(values) }
    private val typeMap = TreeMap<String, SqlType>(String.CASE_INSENSITIVE_ORDER).apply { putAll(types) }

    private fun requireColumn(name: String) {
        if (!valueMap.containsKey(name)) {
            throw IllegalStateException("结果集没有列「$name」，现有列：${valueMap.keys}")
        }
    }

    override fun has(name: String): Boolean = valueMap.containsKey(name)

    override fun isNull(name: String): Boolean {
        requireColumn(name)
        return valueMap[name] == null
    }

    override fun typeOf(name: String): SqlType {
        requireColumn(name)
        return typeMap[name]!!
    }

    override fun string(name: String): String? {
        requireColumn(name)
        return when (val v = valueMap[name]) {
            null -> null
            is String -> v
            is ByteArray -> String(v) // 对齐 Cursor.getString：BLOB 按字节转文本读出
            else -> v.toString()
        }
    }

    override fun long(name: String): Long {
        requireColumn(name)
        val v = valueMap[name] ?: return 0L
        return (v as? Number)?.toLong()
            ?: v.toString().toLongOrNull()
            ?: throw IllegalStateException("列「$name」的值读不成整数：$v")
    }

    override fun double(name: String): Double {
        requireColumn(name)
        val v = valueMap[name] ?: return 0.0
        return (v as? Number)?.toDouble()
            ?: v.toString().toDoubleOrNull()
            ?: throw IllegalStateException("列「$name」的值读不成浮点数：$v")
    }

    override fun bytes(name: String): ByteArray? {
        requireColumn(name)
        return when (val v = valueMap[name]) {
            null -> null
            is ByteArray -> v
            else -> v.toString().toByteArray() // 对齐 Cursor.getBlob：非 BLOB 列按其文本字节导出
        }
    }
}
