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
                comic_id TEXT NOT NULL,
                title TEXT NOT NULL,
                author TEXT,
                cover_url TEXT NOT NULL,
                source_name TEXT NOT NULL,
                last_chapter_title TEXT NOT NULL,
                last_chapter_index INTEGER NOT NULL,
                last_page_index INTEGER NOT NULL,
                total_pages INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                -- v2：主键必须含 source_name，否则不同源的同 ID 漫画会互相覆盖阅读进度
                PRIMARY KEY (comic_id, source_name)
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_updated_at ON comic_history(updated_at DESC);")

        // 2. 收藏夹表
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS comic_favorite (
                comic_id TEXT NOT NULL,
                title TEXT NOT NULL,
                author TEXT,
                cover_url TEXT NOT NULL,
                source_name TEXT NOT NULL,
                folder_name TEXT NOT NULL DEFAULT '默认',
                tags TEXT NOT NULL DEFAULT '',
                has_update INTEGER NOT NULL DEFAULT 0,
                latest_chapter TEXT,
                created_at INTEGER NOT NULL,
                -- v2：同样按 (comic_id, source_name) 唯一，收藏不再串源
                PRIMARY KEY (comic_id, source_name)
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

    /**
     * v1 -> v2：comic_history / comic_favorite 的主键从单列 comic_id 改成
     * (comic_id, source_name)。SQLite 不支持改主键，只能重建表。
     *
     * 复刻阶段没有真实用户数据，因此这里直接重建；
     * 正式发布前必须换成「建新表 → INSERT SELECT → 改名」的数据保留式迁移。
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS comic_history;")
            db.execSQL("DROP TABLE IF EXISTS comic_favorite;")
            db.execSQL("DROP TABLE IF EXISTS comic_source;")
            onCreate(db)
        }
    }

    companion object {
        const val DATABASE_NAME = "venera_core.db"
        // v2: 历史/收藏主键补 source_name（修跨源同 ID 互盖）
        const val DATABASE_VERSION = 2

        @Volatile
        private var INSTANCE: VeneraDatabase? = null

        fun getInstance(context: Context): VeneraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VeneraDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}