package com.venera.compose.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.venera.compose.data.prefs.VeneraPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本地收藏管理器（S5-2），对齐原版 `foundation/favorites.dart` 的 [LocalFavoritesManager]。
 *
 * 官方用 `ChangeNotifier` 通知 UI，这里换成 `StateFlow`：
 * - [folders] 收藏夹清单（已按 folder_order 排序）
 * - [counts] 每个收藏夹的条目数
 * - [version] 内容变更计数，UI 用它触发重新查询
 *
 * 所有写操作都在 IO 线程且包在事务里。
 */
class LocalFavoritesManager private constructor(private val context: Context) {

    private val dbHelper = LocalFavoriteDatabase.getInstance(context)
    private val prefs = VeneraPreferences.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    /** 任意写操作后自增；UI 观察它以刷新收藏夹内容。 */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** 收藏夹表名来自用户输入，拼 SQL 前必须转义。 */
    private fun q(name: String): String = LocalFavoriteDatabase.quoteId(name)

    fun init() {
        scope.launch {
            // 触发 onCreate / onUpgrade
            dbHelper.writableDatabase
            migrateLegacyFavorites()
            refreshFolders()
        }
    }

    private suspend fun refreshFolders() = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val names = dbHelper.folderNames(db)
        _folders.value = names
        _counts.value = names.associateWith { countInternal(db, it) }
    }

    private fun notifyChanged() {
        _version.value++
        scope.launch { refreshFolders() }
    }

    // region ---- 收藏夹 ----

    suspend fun existsFolder(name: String): Boolean = withContext(Dispatchers.IO) {
        dbHelper.folderNames(dbHelper.readableDatabase).contains(name)
    }

    /**
     * 新建收藏夹，返回实际使用的名字。
     *
     * 对齐官方：空名或重名时，若 [renameWhenInvalidName] 为 true 则自动改名，否则抛异常。
     */
    suspend fun createFolder(name: String, renameWhenInvalidName: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            var real = name
            if (real.isEmpty()) {
                if (!renameWhenInvalidName) throw IllegalArgumentException("name is empty!")
                var i = 0
                while (dbHelper.folderNames(db).contains(i.toString())) i++
                real = i.toString()
            } else if (dbHelper.folderNames(db).contains(real)) {
                if (!renameWhenInvalidName) throw IllegalArgumentException("Folder is existing")
                var i = 0
                while (dbHelper.folderNames(db).contains(i.toString())) i++
                real = "$name$i"
            }
            dbHelper.createFolderTable(db, real)
            dbHelper.updateOrder(db, dbHelper.folderNames(db))
            notifyChanged()
            real
        }

    /** 重命名收藏夹（官方 `rename`）。 */
    suspend fun rename(before: String, after: String) = withContext(Dispatchers.IO) {
        if (before == after) return@withContext
        val db = dbHelper.writableDatabase
        if (!dbHelper.folderNames(db).contains(before)) return@withContext
        if (dbHelper.folderNames(db).contains(after)) throw IllegalArgumentException("Folder is existing")
        dbHelper.renameFolderTable(db, before, after)
        if (prefs.followUpdatesFolder.value == before) prefs.setFollowUpdatesFolder(after)
        notifyChanged()
    }

    /** 删除收藏夹（官方 `deleteFolder`）。 */
    suspend fun deleteFolder(name: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        dbHelper.dropFolderTable(db, name)
        if (prefs.followUpdatesFolder.value == name) prefs.setFollowUpdatesFolder(null)
        dbHelper.updateOrder(db, dbHelper.folderNames(db))
        notifyChanged()
    }

    /** 调整收藏夹顺序（官方 `updateOrder`）。 */
    suspend fun updateOrder(folders: List<String>) = withContext(Dispatchers.IO) {
        dbHelper.updateOrder(dbHelper.writableDatabase, folders)
        notifyChanged()
    }

    suspend fun folderComics(folder: String): Int = withContext(Dispatchers.IO) {
        countInternal(dbHelper.readableDatabase, folder)
    }

    private fun countInternal(db: SQLiteDatabase, folder: String): Int {
        if (!dbHelper.folderNames(db).contains(folder)) return 0
        db.rawQuery("SELECT count(*) AS c FROM ${q(folder)}", null).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }

    // endregion

    // region ---- 收藏条目读取 ----

    suspend fun getFolderComics(folder: String): List<FavoriteItem> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext emptyList()
        db.rawQuery("SELECT * FROM ${q(folder)} ORDER BY display_order", null).use { c ->
            buildList { while (c.moveToNext()) add(rowToItem(c)) }
        }
    }

    /** 所有收藏夹的全部条目（去重，官方按 `id + type` 判等）。 */
    suspend fun getAllComics(): List<FavoriteItem> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val res = LinkedHashMap<Pair<String, Int>, FavoriteItem>()
        for (folder in dbHelper.folderNames(db)) {
            db.rawQuery("SELECT * FROM ${q(folder)} ORDER BY display_order", null).use { c ->
                while (c.moveToNext()) {
                    val item = rowToItem(c)
                    res.putIfAbsent(item.id to item.type, item)
                }
            }
        }
        res.values.toList()
    }

    /** 某条目在哪些收藏夹里（官方 `find`）。 */
    suspend fun find(id: String, sourceKey: String): List<String> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val type = sourceKey.hashCode()
        dbHelper.folderNames(db).filter { folder ->
            db.rawQuery(
                "SELECT 1 FROM ${q(folder)} WHERE id = ? AND type = ? LIMIT 1",
                arrayOf(id, type.toString())
            ).use { it.moveToFirst() }
        }
    }

    suspend fun comicExists(folder: String, id: String, sourceKey: String): Boolean =
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            if (!dbHelper.folderNames(db).contains(folder)) return@withContext false
            db.rawQuery(
                "SELECT 1 FROM ${q(folder)} WHERE id = ? AND type = ? LIMIT 1",
                arrayOf(id, sourceKey.hashCode().toString())
            ).use { it.moveToFirst() }
        }

    /** 是否已被收藏（任意收藏夹）。官方 `isExist` 走内存 hashedIds，这里直接查库。 */
    suspend fun isExist(id: String, sourceKey: String): Boolean =
        withContext(Dispatchers.IO) { findInternal(id, sourceKey).isNotEmpty() }

    private fun findInternal(id: String, sourceKey: String): List<String> {
        val db = dbHelper.readableDatabase
        val type = sourceKey.hashCode().toString()
        return dbHelper.folderNames(db).filter { folder ->
            db.rawQuery("SELECT 1 FROM ${q(folder)} WHERE id = ? AND type = ? LIMIT 1", arrayOf(id, type))
                .use { it.moveToFirst() }
        }
    }

    suspend fun searchInFolder(folder: String, keyword: String): List<FavoriteItem> =
        withContext(Dispatchers.IO) {
            if (keyword.isBlank()) return@withContext getFolderComicsInternal(folder)
            val like = "%${keyword.trim()}%"
            val db = dbHelper.readableDatabase
            if (!dbHelper.folderNames(db).contains(folder)) return@withContext emptyList()
            db.rawQuery(
                "SELECT * FROM ${q(folder)} WHERE name LIKE ? OR author LIKE ? OR tags LIKE ? ORDER BY display_order",
                arrayOf(like, like, like)
            ).use { c -> buildList { while (c.moveToNext()) add(rowToItem(c)) } }
        }

    /** 跨收藏夹搜索（官方 `search`）。 */
    suspend fun search(keyword: String): List<FavoriteItem> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val like = "%${keyword.trim()}%"
        val db = dbHelper.readableDatabase
        val res = LinkedHashMap<Pair<String, Int>, FavoriteItem>()
        for (folder in dbHelper.folderNames(db)) {
            db.rawQuery(
                "SELECT * FROM ${q(folder)} WHERE name LIKE ? OR author LIKE ? OR tags LIKE ? ORDER BY display_order",
                arrayOf(like, like, like)
            ).use { c ->
                while (c.moveToNext()) {
                    val item = rowToItem(c)
                    res.putIfAbsent(item.id to item.type, item)
                }
            }
        }
        res.values.toList()
    }

    private fun getFolderComicsInternal(folder: String): List<FavoriteItem> {
        val db = dbHelper.readableDatabase
        if (!dbHelper.folderNames(db).contains(folder)) return emptyList()
        db.rawQuery("SELECT * FROM ${q(folder)} ORDER BY display_order", null).use { c ->
            return buildList { while (c.moveToNext()) add(rowToItem(c)) }
        }
    }

    // endregion

    // region ---- 收藏条目写入 ----

    /**
     * 加入收藏（官方 `addComic`）。
     *
     * @param order 指定 display_order；为 null 时按 `newFavoriteAddTo` 策略：
     *              `"end"` 加到末尾，否则加到开头（官方默认行为）。
     * @return true 成功，false 已存在
     */
    suspend fun addComic(
        folder: String,
        comic: FavoriteItem,
        order: Int? = null,
        updateTime: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        if (!dbHelper.folderNames(db).contains(folder)) throw IllegalArgumentException("Folder does not exists")
        if (comicExistsInternal(db, folder, comic.id, comic.type)) return@withContext false

        val displayOrder = order ?: if (prefs.newFavoriteAddTo.value == "end") {
            maxValue(db, folder) + 1
        } else {
            minValue(db, folder) - 1
        }

        val values = ContentValues().apply {
            put("id", comic.id)
            put("name", comic.name)
            put("author", comic.author)
            put("type", comic.type)
            put("source_key", comic.sourceKey)
            put("tags", tagsToString(comic.tags))
            put("cover_path", comic.coverPath)
            put("time", comic.time)
            put("translated_tags", tagsToString(comic.tags))
            put("display_order", displayOrder)
        }
        db.insertWithOnConflict(q(folder), null, values, SQLiteDatabase.CONFLICT_IGNORE)

        if (updateTime != null && dbHelper.hasColumn(db, folder, "last_update_time")) {
            db.execSQL(
                "UPDATE ${q(folder)} SET last_update_time = ? WHERE id = ? AND type = ?",
                arrayOf(updateTime, comic.id, comic.type.toString())
            )
        }
        notifyChanged()
        true
    }

    suspend fun deleteComicWithId(folder: String, id: String, sourceKey: String) =
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            if (!dbHelper.folderNames(db).contains(folder)) return@withContext
            db.delete(q(folder), "id = ? AND type = ?", arrayOf(id, sourceKey.hashCode().toString()))
            notifyChanged()
        }

    suspend fun batchDeleteComics(folder: String, comics: List<FavoriteItem>) =
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            if (!dbHelper.folderNames(db).contains(folder)) return@withContext
            db.beginTransaction()
            try {
                comics.forEach {
                    db.delete(q(folder), "id = ? AND type = ?", arrayOf(it.id, it.type.toString()))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyChanged()
        }

    /** 在所有收藏夹中删除（官方 `batchDeleteComicsInAllFolders`）。 */
    suspend fun batchDeleteComicsInAllFolders(comics: List<FavoriteItem>) =
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            val folders = dbHelper.folderNames(db)
            db.beginTransaction()
            try {
                comics.forEach { item ->
                    folders.forEach { folder ->
                        db.delete(q(folder), "id = ? AND type = ?", arrayOf(item.id, item.type.toString()))
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyChanged()
        }

    /** 移动单条到另一个收藏夹（官方 `moveFavorite`，目标插到开头）。 */
    suspend fun moveFavorite(sourceFolder: String, targetFolder: String, id: String, sourceKey: String) =
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            val names = dbHelper.folderNames(db)
            if (sourceFolder !in names || targetFolder !in names) return@withContext
            val type = sourceKey.hashCode().toString()
            db.beginTransaction()
            try {
                db.execSQL(
                    "INSERT OR IGNORE INTO ${q(targetFolder)} " +
                        "(id, name, author, type, source_key, tags, cover_path, time, display_order) " +
                        "SELECT id, name, author, type, source_key, tags, cover_path, time, ? " +
                        "FROM ${q(sourceFolder)} WHERE id = ? AND type = ?",
                    arrayOf<Any?>(minValue(db, targetFolder) - 1, id, type)
                )
                db.delete(q(sourceFolder), "id = ? AND type = ?", arrayOf(id, type))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyChanged()
        }

    /** 批量移动（官方 `batchMoveFavorites`：保留相对顺序，追加到目标末尾）。 */
    suspend fun batchMoveFavorites(
        sourceFolder: String,
        targetFolder: String,
        comics: List<FavoriteItem>,
    ) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val names = dbHelper.folderNames(db)
        if (sourceFolder !in names || targetFolder !in names) return@withContext
        db.beginTransaction()
        try {
            var displayOrder = maxValue(db, targetFolder) + 1
            comics.forEach { item ->
                db.execSQL(
                    "INSERT OR IGNORE INTO ${q(targetFolder)} " +
                        "(id, name, author, type, source_key, tags, cover_path, time, display_order) " +
                        "SELECT id, name, author, type, source_key, tags, cover_path, time, ? " +
                        "FROM ${q(sourceFolder)} WHERE id = ? AND type = ?",
                    arrayOf<Any?>(displayOrder, item.id, item.type.toString())
                )
                db.delete(q(sourceFolder), "id = ? AND type = ?", arrayOf(item.id, item.type.toString()))
                displayOrder++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    /** 批量复制（官方 `batchCopyFavorites`：只复制不删除源）。 */
    suspend fun batchCopyFavorites(
        sourceFolder: String,
        targetFolder: String,
        comics: List<FavoriteItem>,
    ) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val names = dbHelper.folderNames(db)
        if (sourceFolder !in names || targetFolder !in names) return@withContext
        db.beginTransaction()
        try {
            var displayOrder = maxValue(db, targetFolder) + 1
            comics.forEach { item ->
                db.execSQL(
                    "INSERT OR IGNORE INTO ${q(targetFolder)} " +
                        "(id, name, author, type, source_key, tags, cover_path, time, display_order) " +
                        "SELECT id, name, author, type, source_key, tags, cover_path, time, ? " +
                        "FROM ${q(sourceFolder)} WHERE id = ? AND type = ?",
                    arrayOf<Any?>(displayOrder, item.id, item.type.toString())
                )
                displayOrder++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    /** 手动排序：按给定顺序重写 display_order（官方 `reorder`）。 */
    suspend fun reorder(newFolder: List<FavoriteItem>, folder: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext
        db.beginTransaction()
        try {
            newFolder.forEachIndexed { i, item ->
                db.execSQL(
                    "UPDATE ${q(folder)} SET display_order = ? WHERE id = ? AND type = ?",
                    arrayOf<Any?>(i, item.id, item.type.toString())
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    suspend fun updateInfo(folder: String, comic: FavoriteItem) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext
        db.execSQL(
            "UPDATE ${q(folder)} SET name = ?, author = ?, tags = ?, cover_path = ? WHERE id = ? AND type = ?",
            arrayOf(comic.name, comic.author, tagsToString(comic.tags), comic.coverPath, comic.id, comic.type.toString())
        )
        notifyChanged()
    }

    suspend fun editTags(id: String, folder: String, tags: List<String>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext
        db.execSQL(
            "UPDATE ${q(folder)} SET tags = ? WHERE id = ?",
            arrayOf(tagsToString(tags), id)
        )
        notifyChanged()
    }

    /**
     * 阅读后调整（官方 `onRead`）：按 `moveFavoriteAfterRead` 把条目移到收藏夹首尾，
     * 若是追更夹则顺带清掉 NEW 标记。
     */
    suspend fun onRead(id: String, sourceKey: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val type = sourceKey.hashCode().toString()
        val move = prefs.moveFavoriteAfterRead.value ?: return@withContext
        val followFolder = prefs.followUpdatesFolder.value

        db.beginTransaction()
        try {
            for (folder in dbHelper.folderNames(db)) {
                if (!comicExistsInternal(db, folder, id, sourceKey.hashCode())) continue
                val locationSql = when (move) {
                    "end" -> "display_order = ${maxValue(db, folder) + 1},"
                    "start" -> "display_order = ${minValue(db, folder) - 1},"
                    else -> ""
                }
                val updateFlag = if (followFolder == folder) "has_new_update = 0," else ""
                db.execSQL(
                    "UPDATE ${q(folder)} SET $locationSql $updateFlag time = ? WHERE id = ? AND type = ?",
                    arrayOf(FavoriteItem.currentTimeString(), id, type)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        dbHelper.folderNames(db).forEach { folder ->
            db.delete(q(folder), null, null)
        }
        notifyChanged()
    }

    // endregion

    // region ---- 网络收藏夹绑定 / 导入导出 ----

    suspend fun linkFolderToNetwork(folder: String, source: String, networkFolder: String) =
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            db.execSQL(
                "INSERT OR REPLACE INTO folder_sync (folder_name, source_key, source_folder) VALUES (?, ?, ?)",
                arrayOf(folder, source, networkFolder)
            )
            notifyChanged()
        }

    suspend fun unlinkFolderFromNetwork(folder: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("folder_sync", "folder_name = ?", arrayOf(folder))
        notifyChanged()
    }

    data class FolderSyncInfo(val folder: String, val sourceKey: String?, val sourceFolder: String?)

    suspend fun getFolderSync(): List<FolderSyncInfo> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT folder_name, source_key, source_folder FROM folder_sync", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        FolderSyncInfo(
                            folder = c.getString(0),
                            sourceKey = c.getString(1),
                            sourceFolder = c.getString(2),
                        )
                    )
                }
            }
        }
    }

    suspend fun folderToJson(folder: String): String = withContext(Dispatchers.IO) {
        val arr = org.json.JSONArray()
        getFolderComicsInternal(folder).forEach { item ->
            val obj = org.json.JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("author", item.author)
            obj.put("sourceKey", item.sourceKey)
            obj.put("coverPath", item.coverPath)
            obj.put("tags", org.json.JSONArray(item.tags))
            arr.put(obj)
        }
        org.json.JSONObject().apply {
            put("name", folder)
            put("comics", arr)
        }.toString()
    }

    // endregion

    // region ---- 追更 ----

    suspend fun prepareTableForFollowUpdates(table: String, clearData: Boolean = true) =
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            if (!dbHelper.folderNames(db).contains(table)) return@withContext
            dbHelper.prepareTableForFollowUpdates(db, table, clearData)
            notifyChanged()
        }

    /**
     * 写入服务端给出的更新时间（官方 `updateUpdateTime`）。
     * 与旧值不同 ⇒ 判定为「有新更新」。
     */
    suspend fun updateUpdateTime(
        folder: String,
        id: String,
        sourceKey: String,
        updateTime: String,
    ) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext
        if (!dbHelper.hasColumn(db, folder, "last_update_time")) return@withContext
        val type = sourceKey.hashCode().toString()
        val oldTime = db.rawQuery(
            "SELECT last_update_time FROM ${q(folder)} WHERE id = ? AND type = ?",
            arrayOf(id, type)
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        val hasNewUpdate = oldTime != updateTime
        db.execSQL(
            "UPDATE ${q(folder)} SET last_update_time = ?, has_new_update = ?, last_check_time = ? WHERE id = ? AND type = ?",
            arrayOf<Any?>(updateTime, if (hasNewUpdate) 1 else 0, System.currentTimeMillis(), id, type)
        )
        notifyChanged()
    }

    /** 只刷新检查时间、不改更新状态（官方 `updateCheckTime`），用于节流。 */
    suspend fun updateCheckTime(folder: String, id: String, sourceKey: String) =
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            if (!dbHelper.folderNames(db).contains(folder)) return@withContext
            if (!dbHelper.hasColumn(db, folder, "last_check_time")) return@withContext
            db.execSQL(
                "UPDATE ${q(folder)} SET last_check_time = ? WHERE id = ? AND type = ?",
                arrayOf<Any?>(System.currentTimeMillis(), id, sourceKey.hashCode().toString())
            )
        }

    suspend fun countUpdates(folder: String): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext 0
        if (!dbHelper.hasColumn(db, folder, "has_new_update")) return@withContext 0
        db.rawQuery("SELECT count(*) AS c FROM ${q(folder)} WHERE has_new_update = 1", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** 仅返回有新更新的条目（官方 `getUpdates`）。 */
    suspend fun getUpdates(folder: String): List<FavoriteItemWithUpdateInfo> =
        withContext(Dispatchers.IO) { queryUpdates(folder, onlyNew = true) }

    /** 返回夹内全部条目及其更新信息（官方 `getComicsWithUpdatesInfo`）。 */
    suspend fun getComicsWithUpdatesInfo(folder: String): List<FavoriteItemWithUpdateInfo> =
        withContext(Dispatchers.IO) { queryUpdates(folder, onlyNew = false) }

    private fun queryUpdates(folder: String, onlyNew: Boolean): List<FavoriteItemWithUpdateInfo> {
        val db = dbHelper.readableDatabase
        if (!dbHelper.folderNames(db).contains(folder)) return emptyList()
        if (!dbHelper.hasColumn(db, folder, "has_new_update")) return emptyList()
        val where = if (onlyNew) " WHERE has_new_update = 1" else ""
        return db.rawQuery("SELECT * FROM ${q(folder)}$where", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        FavoriteItemWithUpdateInfo(
                            item = rowToItem(c),
                            updateTime = c.optString("last_update_time"),
                            hasNewUpdate = c.optInt("has_new_update") == 1,
                            lastCheckTime = c.optLong("last_check_time"),
                        )
                    )
                }
            }
        }
    }

    suspend fun markAsRead(id: String, sourceKey: String) = withContext(Dispatchers.IO) {
        val folder = prefs.followUpdatesFolder.value ?: return@withContext
        val db = dbHelper.writableDatabase
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext
        if (!dbHelper.hasColumn(db, folder, "has_new_update")) return@withContext
        db.execSQL(
            "UPDATE ${q(folder)} SET has_new_update = 0 WHERE id = ? AND type = ?",
            arrayOf(id, sourceKey.hashCode().toString())
        )
        notifyChanged()
    }

    // endregion

    // region ---- 内部工具 ----

    private fun comicExistsInternal(db: SQLiteDatabase, folder: String, id: String, type: Int): Boolean {
        db.rawQuery("SELECT 1 FROM ${q(folder)} WHERE id = ? AND type = ? LIMIT 1", arrayOf(id, type.toString()))
            .use { return it.moveToFirst() }
    }

    private fun maxValue(db: SQLiteDatabase, folder: String): Int {
        db.rawQuery("SELECT MAX(display_order) AS max_value FROM ${q(folder)}", null).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }

    private fun minValue(db: SQLiteDatabase, folder: String): Int {
        db.rawQuery("SELECT MIN(display_order) AS min_value FROM ${q(folder)}", null).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }

    private fun rowToItem(c: Cursor): FavoriteItem = FavoriteItem(
        id = c.getString(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        author = c.optString("author"),
        sourceKey = c.optString("source_key"),
        tags = stringToTags(c.optString("tags")),
        coverPath = c.optString("cover_path"),
        time = c.optString("time").ifBlank { FavoriteItem.currentTimeString() },
        displayOrder = c.optInt("display_order"),
    )

    private fun tagsToString(tags: List<String>): String = tags.joinToString(",")

    private fun stringToTags(s: String): List<String> =
        s.split(",").filter { it.isNotBlank() }

    private fun Cursor.optString(column: String): String {
        val idx = getColumnIndex(column)
        return if (idx < 0) "" else (getString(idx) ?: "")
    }

    private fun Cursor.optInt(column: String): Int {
        val idx = getColumnIndex(column)
        return if (idx < 0) 0 else getInt(idx)
    }

    private fun Cursor.optLong(column: String): Long? {
        val idx = getColumnIndex(column)
        return if (idx < 0) null else (if (isNull(idx)) null else getLong(idx))
    }

    /**
     * 一次性迁移：把旧 `venera_core.db` 的 `comic_favorite` 单表数据搬进新的
     * 「每夹一表」结构。旧表遷移后即清空，避免重复搬家。
     */
    private fun migrateLegacyFavorites() {
        val legacy = VeneraDatabase.getInstance(context).readableDatabase
        val target = dbHelper.writableDatabase
        val hasLegacy = legacy.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='comic_favorite'", null
        ).use { it.moveToFirst() }
        if (!hasLegacy) return

        val rows = mutableListOf<LegacyRow>()
        legacy.rawQuery("SELECT * FROM comic_favorite", null).use { c ->
            while (c.moveToNext()) {
                rows.add(
                    LegacyRow(
                        comicId = c.optStringRaw("comic_id"),
                        title = c.optStringRaw("title"),
                        author = c.optStringRaw("author"),
                        coverUrl = c.optStringRaw("cover_url"),
                        sourceName = c.optStringRaw("source_name"),
                        folderName = c.optStringRaw("folder_name").ifBlank { LocalFavoriteDatabase.DEFAULT_FOLDER },
                        tags = c.optStringRaw("tags"),
                        hasUpdate = c.optIntRaw("has_update"),
                        latestChapter = c.optStringRaw("latest_chapter"),
                        createdAt = c.optLongRaw("created_at"),
                    )
                )
            }
        }
        if (rows.isEmpty()) return

        val existingFolders = dbHelper.folderNames(target).toMutableSet()
        target.beginTransaction()
        try {
            rows.forEach { r ->
                val folder = r.folderName.ifBlank { LocalFavoriteDatabase.DEFAULT_FOLDER }
                if (folder !in existingFolders) {
                    dbHelper.createFolderTable(target, folder)
                    existingFolders.add(folder)
                }
                val type = r.sourceName.hashCode()
                val values = ContentValues().apply {
                    put("id", r.comicId)
                    put("name", r.title)
                    put("author", r.author)
                    put("type", type)
                    put("source_key", r.sourceName)
                    put("tags", r.tags)
                    put("cover_path", r.coverUrl)
                    put("time", FavoriteItem.currentTimeString(r.createdAt))
                    put("translated_tags", r.tags)
                    put("display_order", maxValue(target, folder) + 1)
                }
                target.insertWithOnConflict(q(folder), null, values, SQLiteDatabase.CONFLICT_IGNORE)
                if (r.hasUpdate == 1 && r.latestChapter.isNotBlank()) {
                    dbHelper.prepareTableForFollowUpdates(target, folder, clearData = false)
                    target.execSQL(
                        "UPDATE ${q(folder)} SET last_update_time = ?, has_new_update = 1 WHERE id = ? AND type = ?",
                        arrayOf(r.latestChapter, r.comicId, type.toString())
                    )
                }
            }
            target.setTransactionSuccessful()
        } finally {
            target.endTransaction()
        }
        // 旧表清空，防止二次迁移
        VeneraDatabase.getInstance(context).writableDatabase.delete("comic_favorite", null, null)
        _version.value++
    }

    private fun Cursor.optStringRaw(column: String): String {
        val idx = getColumnIndex(column)
        return if (idx < 0) "" else (getString(idx) ?: "")
    }

    private fun Cursor.optIntRaw(column: String): Int {
        val idx = getColumnIndex(column)
        return if (idx < 0) 0 else getInt(idx)
    }

    private fun Cursor.optLongRaw(column: String): Long {
        val idx = getColumnIndex(column)
        return if (idx < 0) 0L else getLong(idx)
    }

    // endregion

    /** 旧 `comic_favorite` 单行结构，仅在迁移时使用。 */
    private data class LegacyRow(
        val comicId: String,
        val title: String,
        val author: String,
        val coverUrl: String,
        val sourceName: String,
        val folderName: String,
        val tags: String,
        val hasUpdate: Int,
        val latestChapter: String,
        val createdAt: Long,
    )

    companion object {
        @Volatile
        private var INSTANCE: LocalFavoritesManager? = null

        fun getInstance(context: Context): LocalFavoritesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE
                    ?: LocalFavoritesManager(context.applicationContext).also {
                        INSTANCE = it
                        it.init()
                    }
            }
        }
    }
}
