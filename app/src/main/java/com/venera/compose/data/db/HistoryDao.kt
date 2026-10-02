package com.venera.compose.data.db

import com.venera.compose.data.platform.SqlRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryRecord(
    val comicId: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val sourceName: String,
    val lastChapterTitle: String,
    val lastChapterIndex: Int,
    val lastPageIndex: Int,
    val totalPages: Int,
    val updatedAt: Long
)

/**
 * 阅读历史（`comic_history`）的读写层。
 *
 * 本轮改造点同 `FavoriteDao`：吃 [SqlDatabaseSource] 而不是 `SQLiteOpenHelper`，
 * `ContentValues` 换成参数绑定的显式语句，upsert 仍是 `INSERT OR REPLACE`
 * （含「空封面沿用旧值」那条既有规则，见 [saveHistory]）。
 */
class HistoryDao(private val source: SqlDatabaseSource) {

    private val _historyFlow = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val historyFlow: StateFlow<List<HistoryRecord>> = _historyFlow.asStateFlow()

    /**
     * 全表读取一律放到 IO 线程（原实现是 init 里同步查全表，
     * 而 DAO 是在 Composable 里 remember 出来的 ⇒ 冷启动主线程扫库）。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        refresh()
    }

    fun refresh() {
        scope.launch { _historyFlow.value = getAllHistorySync() }
    }

    private fun getAllHistorySync(): List<HistoryRecord> =
        source.reader().query("SELECT * FROM comic_history ORDER BY updated_at DESC").map { rowToRecord(it) }

    suspend fun saveHistory(record: HistoryRecord) = withContext(Dispatchers.IO) {
        val db = source.writer()
        // CONFLICT_REPLACE 是「删掉整行再插」，不是逐列更新 —— 入口数据不全时（阅读器只有
        // id/章节/页码，封面可能为空）会把这行原本已有的好封面一起抹掉，且之后不会自愈。
        // 所以空值一律沿用旧值。
        // 类型保持可空是刻意的：改造前 `Cursor.getString` 在「有行但值为 NULL」时就给 null，
        // 这条路径一路带到 INSERT（cover_url 是 NOT NULL，真发生会抛约束异常），不提前替它兜成 ""。
        val coverUrl: String? = record.coverUrl.ifBlank {
            val existing = db.query(
                "SELECT cover_url FROM comic_history WHERE comic_id = ?", record.comicId
            )
            if (existing.isNotEmpty()) existing[0].string("cover_url") else ""
        }
        db.exec(
            INSERT_OR_REPLACE,
            record.comicId, record.title, record.author, coverUrl, record.sourceName,
            record.lastChapterTitle, record.lastChapterIndex, record.lastPageIndex,
            record.totalPages, record.updatedAt
        )
        refresh()
    }

    suspend fun getHistory(comicId: String): HistoryRecord? = withContext(Dispatchers.IO) {
        source.reader().query(
            "SELECT * FROM comic_history WHERE comic_id = ?", comicId
        ).firstOrNull()?.let { rowToRecord(it) }
    }

    /**
     * 删除单条历史。
     *
     * ⚠️ v2 起主键是 `(comic_id, source_name)`，不同源的同 ID 漫画是两条独立记录，
     * 因此**必须带 sourceName**，否则会把其他源的进度一起删掉。
     */
    suspend fun deleteHistory(comicId: String, sourceName: String) = withContext(Dispatchers.IO) {
        source.writer().exec(
            "DELETE FROM comic_history WHERE comic_id = ? AND source_name = ?", comicId, sourceName
        )
        refresh()
    }

    suspend fun batchDeleteHistory(records: List<HistoryRecord>) = withContext(Dispatchers.IO) {
        val db = source.writer()
        db.inTransaction {
            records.forEach {
                db.exec(
                    "DELETE FROM comic_history WHERE comic_id = ? AND source_name = ?",
                    it.comicId, it.sourceName
                )
            }
        }
        refresh()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        source.writer().exec("DELETE FROM comic_history")
        refresh()
    }

    /** 列序与 `SchemaSql` 里 comic_history 的建表语句逐列对齐。 */
    private fun rowToRecord(row: SqlRow) = HistoryRecord(
        comicId = row.requiredString("comic_id"),
        title = row.requiredString("title"),
        author = row.string("author") ?: "",
        coverUrl = row.requiredString("cover_url"),
        sourceName = row.requiredString("source_name"),
        lastChapterTitle = row.requiredString("last_chapter_title"),
        lastChapterIndex = row.long("last_chapter_index").toInt(),
        lastPageIndex = row.long("last_page_index").toInt(),
        totalPages = row.long("total_pages").toInt(),
        updatedAt = row.long("updated_at")
    )

    companion object {
        /** 原 `insertWithOnConflict(..., CONFLICT_REPLACE)` 的等价语句。 */
        private const val INSERT_OR_REPLACE =
            "INSERT OR REPLACE INTO comic_history " +
                "(comic_id, title, author, cover_url, source_name, last_chapter_title, " +
                "last_chapter_index, last_page_index, total_pages, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

        @Volatile
        private var INSTANCE: HistoryDao? = null

        /**
         * 兼容既有调用点的取用写法（各 UI / ViewModel / 阅读器递的是 Android 的 Context）。
         * 参数只能是 `Any`、句柄由平台 factory 解释，理由见 `FavoriteDao.getInstance`。
         */
        fun getInstance(context: Any): HistoryDao {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HistoryDao(DatabasePorts.of(context).core).also { INSTANCE = it }
            }
        }
    }
}
