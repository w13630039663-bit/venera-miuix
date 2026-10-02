package com.venera.compose.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Venera 原生 SQLite 核心数据库
 * 承载阅读历史 (comic_history)、本地收藏夹 (comic_favorite)、漫画源元数据 (comic_source)
 *
 * 建表/迁移 DDL 逐字存于 [SchemaSql]（与桌面端共享同一棵源），本类只负责执行顺序与版本分支。
 */
class VeneraDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        // 6 张建表语句 + 索引，逐字存于 SchemaSql（与 :desktop 共享同一棵源），按原顺序执行
        SchemaSql.V1_CREATE_STATEMENTS.forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v2：DROP 重建语义不变——先删三张旧表，再跑一遍建表全集
            SchemaSql.V2_DROP_STATEMENTS.forEach { db.execSQL(it) }
            onCreate(db)
            return
        }
        if (oldVersion < 3) {
            // v3：补建 reading_stats / favorite_images / content_guard_rules 三张表及索引
            SchemaSql.V3_CREATE_STATEMENTS.forEach { db.execSQL(it) }
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