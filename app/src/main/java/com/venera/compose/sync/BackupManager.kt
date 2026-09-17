package com.venera.compose.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.venera.compose.data.db.VeneraDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 本地数据备份与恢复服务 (S7)
 *
 * 核心特性：
 * 1. 一键全量打包导出为标准 `.venera` 归档文件
 * 2. 包含阅读历史、本地收藏、阅读统计、屏蔽规则与设置
 * 3. 跨设备双向无损还原
 */
class BackupManager private constructor(private val context: Context) {

    private val tag = "BackupManager"
    private val dbHelper = VeneraDatabase.getInstance(context)

    /**
     * 导出全量备份为标准 ZIP / .venera 文件
     */
    suspend fun exportBackup(targetFile: File? = null): Result<File> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase
            val outDir = File(context.cacheDir, "backups").apply { if (!exists()) mkdirs() }
            val timestamp = System.currentTimeMillis()
            val backupFile = targetFile ?: File(outDir, "venera_backup_$timestamp.venera")
            if (backupFile.exists()) backupFile.delete()

            val historyJson = exportTableToJson(db, "comic_history")
            val favoriteJson = exportTableToJson(db, "comic_favorite")
            val statsJson = exportTableToJson(db, "reading_stats")
            val guardJson = exportTableToJson(db, "content_guard_rules")

            val metaJson = JSONObject().apply {
                put("version", 3)
                put("timestamp", timestamp)
                put("app", "Venera Compose")
                put("historyCount", historyJson.length())
                put("favoriteCount", favoriteJson.length())
                put("statsCount", statsJson.length())
                put("guardCount", guardJson.length())
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile))).use { zos ->
                addJsonEntry(zos, "meta.json", metaJson.toString())
                addJsonEntry(zos, "history.json", historyJson.toString())
                addJsonEntry(zos, "favorite.json", favoriteJson.toString())
                addJsonEntry(zos, "stats.json", statsJson.toString())
                addJsonEntry(zos, "guard_rules.json", guardJson.toString())
            }

            Result.success(backupFile)
        } catch (e: Exception) {
            Log.e(tag, "exportBackup failed", e)
            Result.failure(e)
        }
    }

    /**
     * 导入并恢复备份数据
     */
    suspend fun importBackup(backupFile: File): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            if (!backupFile.exists()) return@withContext Result.failure(Exception("备份文件不存在"))

            val zip = ZipFile(backupFile)
            var historyCount = 0
            var favoriteCount = 0
            var statsCount = 0
            var guardCount = 0
            var timestamp = System.currentTimeMillis()

            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                // 1. 恢复阅读历史
                zip.getEntry("history.json")?.let { entry ->
                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    val array = JSONArray(text)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val cv = ContentValues().apply {
                            put("comic_id", item.getString("comic_id"))
                            put("title", item.getString("title"))
                            put("author", item.optString("author"))
                            put("cover_url", item.getString("cover_url"))
                            put("source_name", item.getString("source_name"))
                            put("last_chapter_title", item.getString("last_chapter_title"))
                            put("last_chapter_index", item.getInt("last_chapter_index"))
                            put("last_page_index", item.getInt("last_page_index"))
                            put("total_pages", item.getInt("total_pages"))
                            put("updated_at", item.getLong("updated_at"))
                        }
                        db.insertWithOnConflict("comic_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                        historyCount++
                    }
                }

                // 2. 恢复本地收藏
                zip.getEntry("favorite.json")?.let { entry ->
                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    val array = JSONArray(text)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val cv = ContentValues().apply {
                            put("comic_id", item.getString("comic_id"))
                            put("title", item.getString("title"))
                            put("author", item.optString("author"))
                            put("cover_url", item.getString("cover_url"))
                            put("source_name", item.getString("source_name"))
                            put("folder_name", item.optString("folder_name", "默认"))
                            put("tags", item.optString("tags", ""))
                            put("has_update", item.optInt("has_update", 0))
                            put("latest_chapter", item.optString("latest_chapter"))
                            put("created_at", item.getLong("created_at"))
                        }
                        db.insertWithOnConflict("comic_favorite", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                        favoriteCount++
                    }
                }

                // 3. 恢复阅读统计
                zip.getEntry("stats.json")?.let { entry ->
                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    val array = JSONArray(text)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val cv = ContentValues().apply {
                            put("comic_id", item.getString("comic_id"))
                            put("comic_title", item.getString("comic_title"))
                            put("source_name", item.getString("source_name"))
                            put("tags", item.optString("tags", ""))
                            put("chapter_title", item.getString("chapter_title"))
                            put("pages_read", item.getInt("pages_read"))
                            put("duration_seconds", item.getInt("duration_seconds"))
                            put("read_date", item.getString("read_date"))
                            put("created_at", item.getLong("created_at"))
                        }
                        db.insert("reading_stats", null, cv)
                        statsCount++
                    }
                }

                // 4. 恢复屏蔽规则
                zip.getEntry("guard_rules.json")?.let { entry ->
                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    val array = JSONArray(text)
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val cv = ContentValues().apply {
                            put("rule_type", item.getString("rule_type"))
                            put("pattern", item.getString("pattern"))
                            put("is_regex", item.optInt("is_regex", 0))
                            put("is_enabled", item.optInt("is_enabled", 1))
                            put("created_at", item.getLong("created_at"))
                        }
                        db.insert("content_guard_rules", null, cv)
                        guardCount++
                    }
                }

                zip.getEntry("meta.json")?.let { entry ->
                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    timestamp = JSONObject(text).optLong("timestamp", timestamp)
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                zip.close()
            }

            val summary = BackupSummary(
                historyCount = historyCount,
                favoriteCount = favoriteCount,
                statsCount = statsCount,
                guardRulesCount = guardCount,
                timestamp = timestamp
            )
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(tag, "importBackup failed", e)
            Result.failure(e)
        }
    }

    private fun exportTableToJson(db: SQLiteDatabase, tableName: String): JSONArray {
        val array = JSONArray()
        val cursor = db.rawQuery("SELECT * FROM $tableName", null)
        cursor.use {
            val colNames = it.columnNames
            while (it.moveToNext()) {
                val obj = JSONObject()
                for (name in colNames) {
                    val idx = it.getColumnIndex(name)
                    when (it.getType(idx)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> obj.put(name, it.getLong(idx))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> obj.put(name, it.getDouble(idx))
                        android.database.Cursor.FIELD_TYPE_STRING -> obj.put(name, it.getString(idx))
                        android.database.Cursor.FIELD_TYPE_NULL -> obj.put(name, JSONObject.NULL)
                        else -> obj.put(name, it.getString(idx))
                    }
                }
                array.put(obj)
            }
        }
        return array
    }

    private fun addJsonEntry(zos: ZipOutputStream, entryName: String, jsonContent: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(jsonContent.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    companion object {
        @Volatile
        private var INSTANCE: BackupManager? = null

        fun getInstance(context: Context): BackupManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackupManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
