package com.venera.compose.data.db

import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.platform.quoteIdentifier

/**
 * 本地收藏数据库（`local_favorite.db`）的表结构与收藏夹增删改查。
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
 *
 * 本轮改造点：这个类以前**就是** `SQLiteOpenHelper`，
 * 建表/迁移回调只有 Android 有。现在它不碰任何 Android 类型：
 *  - 底层连接从 [SqlDatabaseSource] 现取，SQL 全部走 [SqlDatabase]；
 *  - 建库/升库编排搬到 [LocalFavoriteDbSchema]（两端共用一份），Android 的 helper 壳在
 *    `data/platform/android/AndroidLocalFavoriteOpenHelper`，只转发回调；
 *  - 方法签名的形状（可选 `db` 参数、缺省取当前连接）与改造前一致，调用点无需改口径。
 */
class LocalFavoriteDatabase(private val source: SqlDatabaseSource) {

    // region ---- 收藏夹表（动态表名） ----

    /**
     * 建一张收藏夹表。表名即文件夹名，来自用户输入，
     * 因此必须经过 [quoteIdentifier] 转义（原版 Dart 直接字符串插值，存在注入风险，这里不照搬）。
     * 语句文本的唯一出处是 [LocalFavoriteDbSchema.folderTableCreate]。
     */
    fun createFolderTable(db: SqlDatabase = source.writer(), name: String) {
        db.exec(LocalFavoriteDbSchema.folderTableCreate(name))
        db.exec(
            "INSERT OR IGNORE INTO folder_order(folder_name, order_value) VALUES (?, ?)",
            name, 0
        )
    }

    /** 删除收藏夹 = 删表 + 清掉 order / sync 记录。 */
    fun dropFolderTable(db: SqlDatabase = source.writer(), name: String) {
        db.exec("DROP TABLE IF EXISTS ${quoteId(name)}")
        db.exec("DELETE FROM folder_order WHERE folder_name = ?", name)
        db.exec("DELETE FROM folder_sync WHERE folder_name = ?", name)
    }

    /** 重命名收藏夹（SQLite 的 ALTER TABLE ... RENAME TO 会保留数据与索引）。 */
    fun renameFolderTable(db: SqlDatabase, oldName: String, newName: String) {
        // 顺序值必须在删除旧记录之前取，否则会读成 0
        val order = folderOrderOf(db, oldName)
        db.exec("ALTER TABLE ${quoteId(oldName)} RENAME TO ${quoteId(newName)}")
        db.exec("DELETE FROM folder_order WHERE folder_name = ?", oldName)
        db.exec("DELETE FROM folder_sync WHERE folder_name = ?", oldName)
        db.exec(
            "INSERT OR REPLACE INTO folder_order(folder_name, order_value) VALUES (?, ?)",
            newName, order
        )
    }

    /**
     * 追更前置：给收藏夹表补上三列。官方是运行时按需 ALTER，这里同样按需，
     * 以便与官方的「未开启追更的夹子没有这些列」状态保持一致。
     */
    fun prepareTableForFollowUpdates(
        db: SqlDatabase = source.writer(),
        table: String,
        clearData: Boolean = true,
    ) {
        if (!hasColumn(db, table, "last_update_time")) {
            db.exec("ALTER TABLE ${quoteId(table)} ADD COLUMN last_update_time TEXT")
        }
        if (!hasColumn(db, table, "has_new_update")) {
            db.exec("ALTER TABLE ${quoteId(table)} ADD COLUMN has_new_update INTEGER")
        }
        if (clearData) {
            db.exec("UPDATE ${quoteId(table)} SET has_new_update = 0")
        }
        if (!hasColumn(db, table, "last_check_time")) {
            db.exec("ALTER TABLE ${quoteId(table)} ADD COLUMN last_check_time INTEGER")
        }
    }

    /** 列是否存在（`PRAGMA table_info`）；表不存在时为 false，与改造前一致。 */
    fun hasColumn(db: SqlDatabase = source.reader(), table: String, column: String): Boolean =
        db.query("PRAGMA table_info(${quoteId(table)})").any { it.string("name") == column }

    // endregion

    // region ---- 文件夹清单与排序 ----

    /** 所有收藏夹名，按 folder_order 排序（对应官方 `_getFolderNamesWithDB`）。 */
    fun folderNames(db: SqlDatabase = source.reader()): List<String> {
        val folders = db.query("SELECT name FROM sqlite_master WHERE type='table'")
            .map { it.string("name") ?: "" }
            .toMutableList()
        folders.removeAll(META_TABLES)
        val order = folderOrderMap(db)
        folders.sortWith(compareBy({ order[it] ?: 0 }, { it }))
        return folders
    }

    fun folderOrderMap(db: SqlDatabase = source.reader()): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        db.query("SELECT folder_name, order_value FROM folder_order").forEach {
            map[it.string("folder_name") ?: ""] = it.long("order_value").toInt()
        }
        return map
    }

    private fun folderOrderOf(db: SqlDatabase, folder: String): Int =
        db.query("SELECT order_value FROM folder_order WHERE folder_name = ?", folder)
            .firstOrNull()?.long("order_value")?.toInt() ?: 0

    /** 覆盖式写入文件夹顺序（对应官方 `updateOrder`）。 */
    fun updateOrder(db: SqlDatabase = source.writer(), folders: List<String>) {
        db.inTransaction {
            folders.forEachIndexed { i, name ->
                db.exec(
                    "INSERT OR REPLACE INTO folder_order (folder_name, order_value) VALUES (?, ?)",
                    name, i
                )
            }
        }
    }

    // endregion

    companion object {
        /** 库名与版本的事实源在 [LocalFavoriteDbSchema]（两端共用的编排），这里只做转发。 */
        const val DATABASE_NAME = LocalFavoriteDbSchema.DATABASE_NAME
        const val DATABASE_VERSION = LocalFavoriteDbSchema.DATABASE_VERSION

        /** 首次运行的默认收藏夹，沿用既有 `FavoriteDao` 的命名，避免破坏已有行为。 */
        const val DEFAULT_FOLDER = "默认"

        /** 元数据表，不属于收藏夹。`android_metadata` 是 Android helper 建的，桌面侧不会有。 */
        private val META_TABLES =
            setOf("folder_order", "folder_sync", "android_metadata", "sqlite_sequence")

        /**
         * SQLite 标识符转义：文件夹名来自用户输入，直接拼进 SQL 有注入风险
         * （原版 Dart 就是直接插值，这里不照搬）。规则收口到 data/platform 的
         * [quoteIdentifier]（外包反引号、内部反引号翻倍，另拒空串与换行），两端共用一条转义；
         * 双引号换反引号只是引号风格，SQLite 语义等价，不动任何已有表名。
         */
        fun quoteId(name: String): String = quoteIdentifier(name)
    }
}
