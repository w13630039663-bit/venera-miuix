package com.venera.compose.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.venera.compose.data.platform.android.AndroidSqlDatabase

/**
 * Venera 原生 SQLite 核心数据库
 * 承载阅读历史 (comic_history)、本地收藏夹 (comic_favorite)、漫画源元数据 (comic_source)
 *
 * **这一类只是 Android 接线处**：`SQLiteOpenHelper` 的回调原样转给 [CoreDbSchema]，
 * 建表/迁移的编排（版本号、语句顺序、v2 的 DROP 重建、v3 的补表）一份都不在这里，
 * 桌面侧与 JVM 测试走 [CoreDbSchema.ensureOn] 同一个入口 —— 两端共用一份编排（R20）。
 *
 * 库名与版本常量同样住在 [CoreDbSchema]（两端共用的事实源），这里不再重复声明。
 * 建表/迁移 DDL 的逐字原文在 [SchemaSql]。
 *
 * 上层已经不吃这个类：`data/db` 的 DAO 与 `LocalFavoritesManager` 走 [SqlDatabaseSource]，
 * 备份/导入/统计/守卫/图库/阅读器那批原调用点（4b-1）也全部改经 `DatabasePorts`。
 * 现在唯一引用它的是 `data/platform/android/AndroidDatabasePorts`（Android 接线，把 helper
 * 包成 SqlDatabaseSource 供 install）；等它整体挪进 `data/platform/android/` 后，
 * `data/db` 目录对桌面就**少一颗**了 —— 别读成"全透明"：还差追更两颗
 * （`FollowUpdatesRepository` / `FollowUpdatesWorker`，另计，见 desktop/build.gradle.kts
 * 排除清单注释里的逐颗理由）。
 */
class VeneraDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    CoreDbSchema.DATABASE_NAME,
    null,
    CoreDbSchema.DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        CoreDbSchema.create(AndroidSqlDatabase(db))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        CoreDbSchema.upgrade(AndroidSqlDatabase(db), oldVersion, newVersion)
    }

    companion object {
        @Volatile
        private var INSTANCE: VeneraDatabase? = null

        fun getInstance(context: Context): VeneraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VeneraDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
