package com.venera.compose.data.db

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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

class HistoryDao(private val dbHelper: VeneraDatabase) {

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

    private fun getAllHistorySync(): List<HistoryRecord> {
        val list = mutableListOf<HistoryRecord>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "comic_history",
            null,
            null,
            null,
            null,
            null,
            "updated_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    HistoryRecord(
                        comicId = it.getString(it.getColumnIndexOrThrow("comic_id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        author = it.getString(it.getColumnIndexOrThrow("author")) ?: "",
                        coverUrl = it.getString(it.getColumnIndexOrThrow("cover_url")),
                        sourceName = it.getString(it.getColumnIndexOrThrow("source_name")),
                        lastChapterTitle = it.getString(it.getColumnIndexOrThrow("last_chapter_title")),
                        lastChapterIndex = it.getInt(it.getColumnIndexOrThrow("last_chapter_index")),
                        lastPageIndex = it.getInt(it.getColumnIndexOrThrow("last_page_index")),
                        totalPages = it.getInt(it.getColumnIndexOrThrow("total_pages")),
                        updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at"))
                    )
                )
            }
        }
        return list
    }

    suspend fun saveHistory(record: HistoryRecord) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        // CONFLICT_REPLACE 是「删掉整行再插」，不是逐列更新 —— 入口数据不全时（阅读器只有
        // id/章节/页码，封面可能为空）会把这行原本已有的好封面一起抹掉，且之后不会自愈。
        // 所以空值一律沿用旧值。
        val coverUrl = record.coverUrl.ifBlank {
            db.query(
                "comic_history", arrayOf("cover_url"), "comic_id = ?",
                arrayOf(record.comicId), null, null, null
            ).use { if (it.moveToFirst()) it.getString(0) else "" }
        }
        val values = ContentValues().apply {
            put("comic_id", record.comicId)
            put("title", record.title)
            put("author", record.author)
            put("cover_url", coverUrl)
            put("source_name", record.sourceName)
            put("last_chapter_title", record.lastChapterTitle)
            put("last_chapter_index", record.lastChapterIndex)
            put("last_page_index", record.lastPageIndex)
            put("total_pages", record.totalPages)
            put("updated_at", record.updatedAt)
        }
        db.insertWithOnConflict(
            "comic_history",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        refresh()
    }

    suspend fun getHistory(comicId: String): HistoryRecord? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "comic_history",
            null,
            "comic_id = ?",
            arrayOf(comicId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                HistoryRecord(
                    comicId = it.getString(it.getColumnIndexOrThrow("comic_id")),
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    author = it.getString(it.getColumnIndexOrThrow("author")) ?: "",
                    coverUrl = it.getString(it.getColumnIndexOrThrow("cover_url")),
                    sourceName = it.getString(it.getColumnIndexOrThrow("source_name")),
                    lastChapterTitle = it.getString(it.getColumnIndexOrThrow("last_chapter_title")),
                    lastChapterIndex = it.getInt(it.getColumnIndexOrThrow("last_chapter_index")),
                    lastPageIndex = it.getInt(it.getColumnIndexOrThrow("last_page_index")),
                    totalPages = it.getInt(it.getColumnIndexOrThrow("total_pages")),
                    updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at"))
                )
            } else null
        }
    }

    /**
     * 删除单条历史。
     *
     * ⚠️ v2 起主键是 `(comic_id, source_name)`，不同源的同 ID 漫画是两条独立记录，
     * 因此**必须带 sourceName**，否则会把其他源的进度一起删掉。
     */
    suspend fun deleteHistory(comicId: String, sourceName: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("comic_history", "comic_id = ? AND source_name = ?", arrayOf(comicId, sourceName))
        refresh()
    }

    suspend fun batchDeleteHistory(records: List<HistoryRecord>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            records.forEach {
                db.delete(
                    "comic_history",
                    "comic_id = ? AND source_name = ?",
                    arrayOf(it.comicId, it.sourceName)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refresh()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("comic_history", null, null)
        refresh()
    }

    companion object {
        @Volatile
        private var INSTANCE: HistoryDao? = null

        fun getInstance(context: Context): HistoryDao {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HistoryDao(VeneraDatabase.getInstance(context)).also { INSTANCE = it }
            }
        }
    }
}