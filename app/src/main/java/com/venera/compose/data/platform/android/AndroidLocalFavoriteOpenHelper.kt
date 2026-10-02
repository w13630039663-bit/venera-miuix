package com.venera.compose.data.platform.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.venera.compose.data.db.LocalFavoriteDbSchema

/**
 * `local_favorite.db` 的 Android 接线处（原先这层壳就是 `data/db/LocalFavoriteDatabase` 本身）。
 *
 * 它只做一件事：把 `SQLiteOpenHelper` 的建表/升级回调转给 [LocalFavoriteDbSchema] ——
 * 编排与语句两端共用一份，这里不写第二条（R20）。
 */
class AndroidLocalFavoriteOpenHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    LocalFavoriteDbSchema.DATABASE_NAME,
    null,
    LocalFavoriteDbSchema.DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        LocalFavoriteDbSchema.create(AndroidSqlDatabase(db))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        LocalFavoriteDbSchema.upgrade(AndroidSqlDatabase(db), oldVersion, newVersion)
    }
}
