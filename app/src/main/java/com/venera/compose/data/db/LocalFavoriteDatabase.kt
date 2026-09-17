package com.venera.compose.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地收藏数据库（S5-1）
 *
 * 1:1 对齐原版 `lib/foundation/favorites.dart` 所用到的 `local_favorite.db`：
 *
 * - `folder_order(folder_name PK, order_value)` —— 文件夹排序
 * - `folder_sync(folder_name PK, source_key, source_folder)` —— 收藏夹 ↔ 网络源收藏夹绑定
 * - **每个收藏夹一张动态表**（表名即文件夹名），列同官方：
 *   `id / name / author / type / tags / cover_path / time / display_order / translated_tags`，
 *   主键 `(id, type)`
 * - 追更列 `last_update_time / has_new_update / last_check_time` 由
 *   [prepareTableForFollowUpdates] 在使用时 ALTER 追加（与官方一致，建表时不带）。
 *
 * **与官方的唯一差异**：`type` 列存的是 sourceKey 的 JVM `hashCode`。
 * 原版存的是 Dart `String.hashCode`，JVM 与 Dart 的哈希算法不同、无法复刻同一数值，
 * 且哈希不可逆 ⇒ 官方靠 `ComicType` 反查源，我们没有等价机制，
 * 因此**新增 `source_key TEXT` 列**保存可读来源。这是必要的适配，不是偏离。
 */
class LocalFavoriteDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS folder_order (
                folder_name TEXT PRIMARY KEY,
                order_value INTEGER
            );
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS folder_sync (
                folder_name TEXT PRIMARY KEY,
                source_key TEXT,
                source_folder TEXT
            );
            """.trimIndent()
        )
        createFolderTable(db, DEFAULT_FOLDER)
        db.execSQL(
            "INSERT OR IGNORE INTO folder_order(folder_name, order_value) VALUES (?, 0)",
            arrayOf(DEFAULT_FOLDER)
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 为首个版本，暂无历史数据需要迁移。
    }

    // region ---- 收藏夹表（动态表名） ----

    /**
     * 建一张收藏夹表。表名即文件夹名，来自用户输入，
     * 因此必须经过 [quoteId] 转义（原版 Dart 直接字符串插值，存在注入风险，这里不照搬）。
     */
    fun createFolderTable(db: SQLiteDatabase, name: String) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${quoteId(name)}(
                id TEXT,
                name TEXT,
                author TEXT,
                type INTEGER,
                source_key TEXT,
                tags TEXT,
                cover_path TEXT,
                time TEXT,
                display_order INTEGER,
                translated_tags TEXT,
                PRIMARY KEY (id, type)
            );
            """.trimIndent()
        )
        db.execSQL(
            "INSERT OR IGNORE INTO folder_order(folder_name, order_value) VALUES (?, ?)",
            arrayOf<Any?>(name, 0)
        )
    }

    /** 删除收藏夹 = 删表 + 清掉 order / sync 记录。 */
    fun dropFolderTable(db: SQLiteDatabase, name: String) {
        db.execSQL("DROP TABLE IF EXISTS ${quoteId(name)}")
        db.delete("folder_order", "folder_name = ?", arrayOf(name))
        db.delete("folder_sync", "folder_name = ?", arrayOf(name))
    }

    /** 重命名收藏夹（SQLite 的 ALTER TABLE ... RENAME TO 会保留数据与索引）。 */
    fun renameFolderTable(db: SQLiteDatabase, oldName: String, newName: String) {
        // 顺序值必须在删除旧记录之前取，否则会读成 0
        val order = folderOrderOf(db, oldName)
        db.execSQL("ALTER TABLE ${quoteId(oldName)} RENAME TO ${quoteId(newName)}")
        db.delete("folder_order", "folder_name = ?", arrayOf(oldName))
        db.delete("folder_sync", "folder_name = ?", arrayOf(oldName))
        db.execSQL(
            "INSERT OR REPLACE INTO folder_order(folder_name, order_value) VALUES (?, ?)",
            arrayOf<Any?>(newName, order)
        )
    }

    /**
     * 追更前置：给收藏夹表补上三列。官方是运行时按需 ALTER，这里同样按需，
     * 以便与官方的「未开启追更的夹子没有这些列」状态保持一致。
     */
    fun prepareTableForFollowUpdates(db: SQLiteDatabase, table: String, clearData: Boolean = true) {
        if (!hasColumn(db, table, "last_update_time")) {
            db.execSQL("ALTER TABLE ${quoteId(table)} ADD COLUMN last_update_time TEXT")
        }
        if (!hasColumn(db, table, "has_new_update")) {
            db.execSQL("ALTER TABLE ${quoteId(table)} ADD COLUMN has_new_update INTEGER")
        }
        if (clearData) {
            db.execSQL("UPDATE ${quoteId(table)} SET has_new_update = 0")
        }
        if (!hasColumn(db, table, "last_check_time")) {
            db.execSQL("ALTER TABLE ${quoteId(table)} ADD COLUMN last_check_time INTEGER")
        }
    }

    fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        db.rawQuery("PRAGMA table_info(${quoteId(table)})", null).use { c ->
            val idx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (c.getString(idx) == column) return true
            }
        }
        return false
    }

    // endregion

    // region ---- 文件夹清单与排序 ----

    /** 所有收藏夹名，按 folder_order 排序（对应官方 `_getFolderNamesWithDB`）。 */
    fun folderNames(db: SQLiteDatabase = readableDatabase): List<String> {
        val folders = mutableListOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
            while (c.moveToNext()) {
                folders.add(c.getString(0))
            }
        }
        folders.removeAll(META_TABLES)
        val order = folderOrderMap(db)
        folders.sortWith(compareBy({ order[it] ?: 0 }, { it }))
        return folders
    }

    fun folderOrderMap(db: SQLiteDatabase = readableDatabase): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        db.rawQuery("SELECT folder_name, order_value FROM folder_order", null).use { c ->
            while (c.moveToNext()) map[c.getString(0)] = c.getInt(1)
        }
        return map
    }

    private fun folderOrderOf(db: SQLiteDatabase, folder: String): Int {
        db.rawQuery("SELECT order_value FROM folder_order WHERE folder_name = ?", arrayOf(folder))
            .use { c -> if (c.moveToFirst()) return c.getInt(0) }
        return 0
    }

    /** 覆盖式写入文件夹顺序（对应官方 `updateOrder`）。 */
    fun updateOrder(db: SQLiteDatabase = writableDatabase, folders: List<String>) {
        db.beginTransaction()
        try {
            folders.forEachIndexed { i, name ->
                db.execSQL(
                    "INSERT OR REPLACE INTO folder_order (folder_name, order_value) VALUES (?, ?)",
                    arrayOf<Any?>(name, i)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // endregion

    companion object {
        const val DATABASE_NAME = "local_favorite.db"
        const val DATABASE_VERSION = 1

        /** 首次运行的默认收藏夹，沿用既有 `FavoriteDao` 的命名，避免破坏已有行为。 */
        const val DEFAULT_FOLDER = "默认"

        /** 元数据表，不属于收藏夹。 */
        private val META_TABLES =
            setOf("folder_order", "folder_sync", "android_metadata", "sqlite_sequence")

        /**
         * SQLite 标识符转义：文件夹名来自用户输入，直接拼进 SQL 有注入风险
         * （原版 Dart 就是直接插值，这里不照搬）。
         */
        fun quoteId(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""

        @Volatile
        private var INSTANCE: LocalFavoriteDatabase? = null

        fun getInstance(context: Context): LocalFavoriteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE
                    ?: LocalFavoriteDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
