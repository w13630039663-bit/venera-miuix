package com.venera.compose.data.db

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.platform.SqlRow
import com.venera.compose.data.platform.SqlType
import com.venera.compose.data.platform.quoteIdentifier

/**
 * `venera_core.db` 的表 ↔ 备份 JSON 那一栏的对接（导出与恢复共用一份列名清单）。
 *
 * 为什么放在 `data/db`：这三张表的建表语句在这里（[SchemaSql]），列名/可空性只有这一处事实源；
 * 备份读写要是自己再抄一份列名，改了表就只会在"恢复备份"那一步悄悄错。
 * 放这一层还有第二个用处：它不碰任何 Android 类型，所以 `:desktop:test` 能拿真
 * `JdbcSqliteDatabase` + 临时 `.db` 跑「导出 → 恢复 → 逐字段对照」，备份格式因此有用例钉着
 * （见 `CoreTableBackupTest` 的逐字节锚点）。调用方是 `sync/BackupManager`。
 *
 * 两条刻意的口径：
 *  - **恢复历史走 `INSERT OR REPLACE`**，与改造前 `insertWithOnConflict(..., CONFLICT_REPLACE)`
 *    逐字等价；它**不**走 [HistoryDao.saveHistory]，因为那条路带「空封面沿用库里旧值」的规则，
 *    而备份恢复的语义是"归档里写什么就是什么"，两回事。
 *  - **导出的值按存储类分派**（INTEGER→整数、FLOAT→小数、TEXT→字符串、NULL→`null`、
 *    其余按文本），与改造前 `Cursor.getType()` 的 `FIELD_TYPE_*` 分支一一对应；
 *    列的顺序取 `PRAGMA table_info`（= `SELECT *` 的列序），所以产出的 JSON 键序也不变。
 */
internal object CoreTableBackup {

    /** 原 `insertWithOnConflict("comic_history", null, cv, CONFLICT_REPLACE)` 的等价语句。 */
    const val INSERT_OR_REPLACE_HISTORY =
        "INSERT OR REPLACE INTO comic_history " +
            "(comic_id, title, author, cover_url, source_name, last_chapter_title, " +
            "last_chapter_index, last_page_index, total_pages, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    /**
     * 整表导出成备份里那个数组。
     *
     * 表名是调用方给的固定字面量（`comic_history` / `reading_stats` / `content_guard_rules`），
     * 仍然过 [quoteIdentifier]：门面对标识符的口径只有一条，不为三张表开特例。
     */
    fun exportTable(db: SqlDatabase, table: String): JsonArray {
        val columns = db.query("PRAGMA table_info(${quoteIdentifier(table)})")
            .map { it.requiredString("name") }
        val array = JsonArray()
        for (row in db.query("SELECT * FROM ${quoteIdentifier(table)}")) {
            array.add(rowToJson(row, columns))
        }
        return array
    }

    private fun rowToJson(row: SqlRow, columns: List<String>): JsonObject {
        val obj = JsonObject()
        for (name in columns) {
            when (row.typeOf(name)) {
                SqlType.INTEGER -> obj.addProperty(name, row.long(name))
                SqlType.FLOAT -> obj.addProperty(name, row.double(name))
                SqlType.TEXT -> obj.addProperty(name, row.string(name))
                // 改造前是 obj.put(name, JSONObject.NULL) —— 键要**留着**并写 null，不是把键省掉
                SqlType.NULL -> obj.add(name, JsonNull.INSTANCE)
                // BLOB（以及未来任何新存储类）按文本导出，与改造前 `getString` 的回落一致：
                // 列里存的是去混淆位图这类字节时，备份本来就只能带它的文本形态。
                SqlType.BLOB -> obj.addProperty(name, row.string(name))
            }
        }
        return obj
    }

    /** 恢复阅读历史。返回写入条数；任一行字段读不出 ⇒ 抛（由调用方的事务整笔回滚）。 */
    fun importHistory(db: SqlDatabase, rows: JsonArray): Int {
        var count = 0
        for (i in 0 until rows.size()) {
            val item = rows.objectAt(i)
            db.exec(
                INSERT_OR_REPLACE_HISTORY,
                item.textValue("comic_id"),
                item.textValue("title"),
                item.optTextValue("author"),
                item.textValue("cover_url"),
                item.textValue("source_name"),
                item.textValue("last_chapter_title"),
                item.intValue("last_chapter_index"),
                item.intValue("last_page_index"),
                item.intValue("total_pages"),
                item.longValue("updated_at"),
            )
            count++
        }
        return count
    }

    /** 恢复阅读统计。 */
    fun importStats(db: SqlDatabase, rows: JsonArray): Int {
        var count = 0
        for (i in 0 until rows.size()) {
            val item = rows.objectAt(i)
            db.exec(
                ReadingStatsStore.INSERT_STATS,
                item.textValue("comic_id"),
                item.textValue("comic_title"),
                item.textValue("source_name"),
                item.optTextValue("tags", ""),
                item.textValue("chapter_title"),
                item.intValue("pages_read"),
                item.intValue("duration_seconds"),
                item.textValue("read_date"),
                item.longValue("created_at"),
            )
            count++
        }
        return count
    }

    /**
     * 恢复屏蔽规则。
     *
     * [regexCompiles] 由调用方注入（生产传 `GuardRulePattern::compiles`）：`data/db` 不认识
     * `security.guard`，而"编译不过的正则进了库也永不命中"那条判据本身是在那一层定的。
     */
    fun importGuardRules(
        db: SqlDatabase,
        rows: JsonArray,
        regexCompiles: (pattern: String) -> Boolean,
    ): GuardImportOutcome {
        var imported = 0
        var skipped = 0
        for (i in 0 until rows.size()) {
            val item = rows.objectAt(i)
            val regexFlag = item.optIntValue("is_regex", 0)
            val pattern = item.textValue("pattern")
            if (regexFlag == 1 && !regexCompiles(pattern)) {
                // 不收一条永远生效不了的规则（理由见 GuardRulePattern 与 BackupManager 的注释）
                skipped++
                continue
            }
            db.exec(
                GuardRuleStore.INSERT_GUARD_RULE,
                item.textValue("rule_type"),
                pattern,
                regexFlag,
                item.optIntValue("is_enabled", 1),
                item.longValue("created_at"),
            )
            imported++
        }
        return GuardImportOutcome(imported, skipped)
    }

    /** 恢复屏蔽规则的两项计数：进库多少条、因写法有误不收多少条（屏上要说得出这个数）。 */
    data class GuardImportOutcome(val imported: Int, val skipped: Int)
}
