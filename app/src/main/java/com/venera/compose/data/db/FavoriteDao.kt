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

data class FavoriteRecord(
    val comicId: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val sourceName: String,
    val folderName: String = "默认",
    val tags: String = "",
    val hasUpdate: Boolean = false,
    val latestChapter: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

class FavoriteDao(private val dbHelper: VeneraDatabase) {

    private val _favoritesFlow = MutableStateFlow<List<FavoriteRecord>>(emptyList())
    val favoritesFlow: StateFlow<List<FavoriteRecord>> = _favoritesFlow.asStateFlow()

    /**
     * 全表读取一律放到 IO 线程（S0-5：原实现是 init 里同步查全表，
     * 而 DAO 是在 Composable 里 remember 出来的 ⇒ 冷启动主线程扫库）。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        refresh()
    }

    fun refresh() {
        scope.launch { _favoritesFlow.value = getAllFavoritesSync() }
    }

    private fun getAllFavoritesSync(folderName: String? = null): List<FavoriteRecord> {
        val list = mutableListOf<FavoriteRecord>()
        val db = dbHelper.readableDatabase
        val selection = if (folderName != null) "folder_name = ?" else null
        val selectionArgs = if (folderName != null) arrayOf(folderName) else null

        val cursor = db.query(
            "comic_favorite",
            null,
            selection,
            selectionArgs,
            null,
            null,
            "created_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    FavoriteRecord(
                        comicId = it.getString(it.getColumnIndexOrThrow("comic_id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        author = it.getString(it.getColumnIndexOrThrow("author")) ?: "",
                        coverUrl = it.getString(it.getColumnIndexOrThrow("cover_url")),
                        sourceName = it.getString(it.getColumnIndexOrThrow("source_name")),
                        folderName = it.getString(it.getColumnIndexOrThrow("folder_name")),
                        tags = it.getString(it.getColumnIndexOrThrow("tags")) ?: "",
                        hasUpdate = it.getInt(it.getColumnIndexOrThrow("has_update")) == 1,
                        latestChapter = it.getString(it.getColumnIndexOrThrow("latest_chapter")) ?: "",
                        createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return list
    }

    suspend fun addFavorite(record: FavoriteRecord) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("comic_id", record.comicId)
            put("title", record.title)
            put("author", record.author)
            put("cover_url", record.coverUrl)
            put("source_name", record.sourceName)
            put("folder_name", record.folderName)
            put("tags", record.tags)
            put("has_update", if (record.hasUpdate) 1 else 0)
            put("latest_chapter", record.latestChapter)
            put("created_at", record.createdAt)
        }
        db.insertWithOnConflict(
            "comic_favorite",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        refresh()
    }

    suspend fun removeFavorite(comicId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("comic_favorite", "comic_id = ?", arrayOf(comicId))
        refresh()
    }

    fun toggleFavorite(
        comicId: String,
        title: String,
        coverUrl: String,
        author: String = "",
        sourceName: String = "拷贝漫画",
        latestChapter: String = "",
        folderName: String = "默认"
    ) {
        val currentFavs = _favoritesFlow.value
        val exists = currentFavs.any { it.comicId == comicId }
        val db = dbHelper.writableDatabase
        if (exists) {
            db.delete("comic_favorite", "comic_id = ?", arrayOf(comicId))
        } else {
            val values = ContentValues().apply {
                put("comic_id", comicId)
                put("title", title)
                put("author", author)
                put("cover_url", coverUrl)
                put("source_name", sourceName)
                put("folder_name", folderName)
                put("tags", "")
                put("has_update", 0)
                put("latest_chapter", latestChapter)
                put("created_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(
                "comic_favorite",
                null,
                values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
        }
        refresh()
    }

    suspend fun isFavorite(comicId: String): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT 1 FROM comic_favorite WHERE comic_id = ? LIMIT 1",
            arrayOf(comicId)
        )
        cursor.use { it.moveToFirst() }
    }

    suspend fun getFolders(): List<String> = withContext(Dispatchers.IO) {
        val folders = mutableListOf("默认")
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT DISTINCT folder_name FROM comic_favorite ORDER BY folder_name ASC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val f = it.getString(0)
                if (f != null && !folders.contains(f)) {
                    folders.add(f)
                }
            }
        }
        folders
    }

    companion object {
        @Volatile
        private var INSTANCE: FavoriteDao? = null

        fun getInstance(context: Context): FavoriteDao {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FavoriteDao(VeneraDatabase.getInstance(context)).also { INSTANCE = it }
            }
        }
    }
}