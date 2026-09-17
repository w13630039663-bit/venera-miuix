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
        // 4. 阅读统计表 (S7)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reading_stats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                comic_id TEXT NOT NULL,
                comic_title TEXT NOT NULL,
                source_name TEXT NOT NULL,
                tags TEXT NOT NULL DEFAULT '',
                chapter_title TEXT NOT NULL,
                pages_read INTEGER NOT NULL DEFAULT 0,
                duration_seconds INTEGER NOT NULL DEFAULT 0,
                read_date TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reading_stats_date ON reading_stats(read_date);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reading_stats_comic ON reading_stats(comic_id, source_name);")

        // 5. 单页/图片收藏表 (S7)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS favorite_images (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                comic_id TEXT NOT NULL,
                comic_title TEXT NOT NULL,
                source_name TEXT NOT NULL,
                chapter_title TEXT NOT NULL,
                page_index INTEGER NOT NULL,
                image_url TEXT NOT NULL,
                local_path TEXT,
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_favorite_images_created ON favorite_images(created_at DESC);")

        // 6. 屏蔽过滤规则表 (S7)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS content_guard_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                rule_type TEXT NOT NULL,
                pattern TEXT NOT NULL,
                is_regex INTEGER NOT NULL DEFAULT 0,
                is_enabled INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS comic_history;")
            db.execSQL("DROP TABLE IF EXISTS comic_favorite;")
            db.execSQL("DROP TABLE IF EXISTS comic_source;")
            onCreate(db)
            return
        }
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reading_stats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    comic_id TEXT NOT NULL,
                    comic_title TEXT NOT NULL,
                    source_name TEXT NOT NULL,
                    tags TEXT NOT NULL DEFAULT '',
                    chapter_title TEXT NOT NULL,
                    pages_read INTEGER NOT NULL DEFAULT 0,
                    duration_seconds INTEGER NOT NULL DEFAULT 0,
                    read_date TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_reading_stats_date ON reading_stats(read_date);")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_reading_stats_comic ON reading_stats(comic_id, source_name);")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS favorite_images (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    comic_id TEXT NOT NULL,
                    comic_title TEXT NOT NULL,
                    source_name TEXT NOT NULL,
                    chapter_title TEXT NOT NULL,
                    page_index INTEGER NOT NULL,
                    image_url TEXT NOT NULL,
                    local_path TEXT,
                    created_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_favorite_images_created ON favorite_images(created_at DESC);")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS content_guard_rules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    rule_type TEXT NOT NULL,
                    pattern TEXT NOT NULL,
                    is_regex INTEGER NOT NULL DEFAULT 0,
                    is_enabled INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL
                );
                """.trimIndent()
            )
        }
    }

    companion object {
        const val DATABASE_NAME = "venera_core.db"
        // v3: 新增阅读统计 (reading_stats)、单页收藏 (favorite_images)、内容屏蔽 (content_guard_rules)
        const val DATABASE_VERSION = 3

        @Volatile
        private var INSTANCE: VeneraDatabase? = null

        fun getInstance(context: Context): VeneraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VeneraDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}