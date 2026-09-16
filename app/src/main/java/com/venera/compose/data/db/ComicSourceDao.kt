package com.venera.compose.data.db

import android.content.ContentValues
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ComicSourceRecord(
    val sourceId: String,
    val name: String,
    val version: String,
    val iconUrl: String = "",
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val configJson: String = "{}"
)

class ComicSourceDao(private val dbHelper: VeneraDatabase) {

    private val _sourcesFlow = MutableStateFlow<List<ComicSourceRecord>>(emptyList())
    val sourcesFlow: StateFlow<List<ComicSourceRecord>> = _sourcesFlow.asStateFlow()

    init {
        checkAndSeedDefaults()
        refresh()
    }

    private fun checkAndSeedDefaults() {
        val db = dbHelper.writableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM comic_source", null)
        val count = cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (count == 0) {
            val defaults = listOf(
                ComicSourceRecord("copymanga", "拷贝漫画", "1.0.0", "", true, 0),
                ComicSourceRecord("picacg", "哔咔漫画", "1.0.0", "", true, 1),
                ComicSourceRecord("mangadex", "MangaDex", "1.0.0", "", true, 2),
                ComicSourceRecord("ehentai", "E-Hentai", "1.0.0", "", false, 3),
                ComicSourceRecord("nhentai", "NHentai", "1.0.0", "", false, 4)
            )
            defaults.forEach { src ->
                val values = ContentValues().apply {
                    put("source_id", src.sourceId)
                    put("name", src.name)
                    put("version", src.version)
                    put("icon_url", src.iconUrl)
                    put("is_enabled", if (src.isEnabled) 1 else 0)
                    put("sort_order", src.sortOrder)
                    put("config_json", src.configJson)
                }
                db.insert("comic_source", null, values)
            }
        }
    }

    fun refresh() {
        val list = mutableListOf<ComicSourceRecord>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "comic_source",
            null,
            null,
            null,
            null,
            null,
            "sort_order ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    ComicSourceRecord(
                        sourceId = it.getString(it.getColumnIndexOrThrow("source_id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        version = it.getString(it.getColumnIndexOrThrow("version")),
                        iconUrl = it.getString(it.getColumnIndexOrThrow("icon_url")) ?: "",
                        isEnabled = it.getInt(it.getColumnIndexOrThrow("is_enabled")) == 1,
                        sortOrder = it.getInt(it.getColumnIndexOrThrow("sort_order")),
                        configJson = it.getString(it.getColumnIndexOrThrow("config_json")) ?: "{}"
                    )
                )
            }
        }
        _sourcesFlow.value = list
    }

    suspend fun toggleSource(sourceId: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("is_enabled", if (isEnabled) 1 else 0)
        }
        db.update("comic_source", values, "source_id = ?", arrayOf(sourceId))
        refresh()
    }

    companion object {
        @Volatile
        private var INSTANCE: ComicSourceDao? = null

        fun getInstance(context: Context): ComicSourceDao {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ComicSourceDao(VeneraDatabase.getInstance(context)).also { INSTANCE = it }
            }
        }
    }
}