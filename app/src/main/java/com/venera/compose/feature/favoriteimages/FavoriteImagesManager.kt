package com.venera.compose.feature.favoriteimages

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.venera.compose.data.db.VeneraDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FavoriteImageItem(
    val id: Long,
    val comicId: String,
    val comicTitle: String,
    val sourceName: String,
    val chapterTitle: String,
    val pageIndex: Int,
    val imageUrl: String,
    val localPath: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 单页/插图收藏管理器 (S7)
 */
class FavoriteImagesManager private constructor(private val context: Context) {

    private val dbHelper = VeneraDatabase.getInstance(context)

    suspend fun addFavorite(
        comicId: String,
        comicTitle: String,
        sourceName: String,
        chapterTitle: String,
        pageIndex: Int,
        imageUrl: String,
        localPath: String = ""
    ): Long = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("comic_id", comicId)
                put("comic_title", comicTitle)
                put("source_name", sourceName)
                put("chapter_title", chapterTitle)
                put("page_index", pageIndex)
                put("image_url", imageUrl)
                put("local_path", localPath)
                put("created_at", System.currentTimeMillis())
            }
            db.insert("favorite_images", null, cv)
        } catch (_: Exception) {
            -1L
        }
    }

    suspend fun removeFavorite(id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            db.delete("favorite_images", "id = ?", arrayOf(id.toString())) > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isFavorited(imageUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery("SELECT id FROM favorite_images WHERE image_url = ? LIMIT 1", arrayOf(imageUrl))
            val exists = cursor.moveToFirst()
            cursor.close()
            exists
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getAllFavorites(): List<FavoriteImageItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FavoriteImageItem>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM favorite_images ORDER BY created_at DESC", null)
            cursor.use {
                val idIdx = it.getColumnIndex("id")
                val cidIdx = it.getColumnIndex("comic_id")
                val titleIdx = it.getColumnIndex("comic_title")
                val srcIdx = it.getColumnIndex("source_name")
                val chIdx = it.getColumnIndex("chapter_title")
                val pageIdx = it.getColumnIndex("page_index")
                val urlIdx = it.getColumnIndex("image_url")
                val pathIdx = it.getColumnIndex("local_path")
                val timeIdx = it.getColumnIndex("created_at")

                while (it.moveToNext()) {
                    list.add(
                        FavoriteImageItem(
                            id = it.getLong(idIdx),
                            comicId = it.getString(cidIdx),
                            comicTitle = it.getString(titleIdx),
                            sourceName = it.getString(srcIdx),
                            chapterTitle = it.getString(chIdx),
                            pageIndex = it.getInt(pageIdx),
                            imageUrl = it.getString(urlIdx),
                            localPath = it.getString(pathIdx) ?: "",
                            createdAt = it.getLong(timeIdx)
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        list
    }

    companion object {
        @Volatile
        private var INSTANCE: FavoriteImagesManager? = null

        fun getInstance(context: Context): FavoriteImagesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FavoriteImagesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
