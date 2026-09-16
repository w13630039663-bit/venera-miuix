package com.venera.compose.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Venera 原生 SQLite 核心数据库
 * 承载阅读历史 (comic_history)、本地收藏夹 (comic_favorite)、漫画源元数据 (comic_source)
 */
class VeneraDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        // 1. 阅读历史表
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS comic_history (
                comic_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                author TEXT,
                cover_url TEXT NOT NULL,
                source_name TEXT NOT NULL,
                last_chapter_title TEXT NOT NULL,
                last_chapter_index INTEGER NOT NULL,
                last_page_index INTEGER NOT NULL,
                total_pages INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_updated_at ON comic_history(updated_at DESC);")

        // 2. 收藏夹表
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS comic_favorite (
                comic_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                author TEXT,
                cover_url TEXT NOT NULL,
                source_name TEXT NOT NULL,
                folder_name TEXT NOT NULL DEFAULT '默认',
                tags TEXT NOT NULL DEFAULT '',
                has_update INTEGER NOT NULL DEFAULT 0,
                latest_chapter TEXT,
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_favorite_folder ON comic_favorite(folder_name);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_favorite_created ON comic_favorite(created_at DESC);")

        // 3. 漫画源元数据表
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS comic_source (
                source_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                version TEXT NOT NULL,
                icon_url TEXT,
                is_enabled INTEGER NOT NULL DEFAULT 1,
                sort_order INTEGER NOT NULL DEFAULT 0,
                config_json TEXT NOT NULL DEFAULT '{}'
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 预留平滑迁移逻辑
    }

    companion object {
        const val DATABASE_NAME = "venera_core.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: VeneraDatabase? = null

        fun getInstance(context: Context): VeneraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VeneraDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}