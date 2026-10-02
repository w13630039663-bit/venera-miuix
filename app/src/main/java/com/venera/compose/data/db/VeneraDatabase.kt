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
 * 上层（`data/db` 的 DAO 与 `LocalFavoritesManager`）已经不吃这个类，它们吃
 * [SqlDatabaseSource]；此处保留 `getInstance` + `readableDatabase` / `writableDatabase`
 * 的既有写法，是为了让还没改造的调用点（备份 / 导入 / 统计 / 守卫 / 图库 / 阅读器）继续编译。
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
