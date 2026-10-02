package com.venera.compose.data.db

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.platform.SqlRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本地收藏管理器，对齐原版 `foundation/favorites.dart` 的 [LocalFavoritesManager]。
 *
 * 官方用 `ChangeNotifier` 通知 UI，这里换成 `StateFlow`：
 * - [folders] 收藏夹清单（已按 folder_order 排序）
 * - [counts] 每个收藏夹的条目数
 * - [version] 内容变更计数，UI 用它触发重新查询
 *
 * 所有写操作都在 IO 线程且包在事务里。
 *
 * 本轮改造点：这个类以前满手 `SQLiteDatabase` / `Cursor` / `ContentValues`，
 * 现在只吃 [SqlDatabaseSource]（两个库的连接获取口）与 [FavoritesPreferences]（三项偏好），
 * 不碰任何 Android 类型，所以它连同 `LocalFavoriteDatabase` 一起进了桌面的编译面。
 * SQL 的语句文本、绑定参数、排序与去重口径逐条照搬改造前，只换了 API 形状。
 */
class LocalFavoritesManager(
    private val core: SqlDatabaseSource,
    private val favorites: SqlDatabaseSource,
    private val prefs: FavoritesPreferences,
) {

    private val dbHelper = LocalFavoriteDatabase(favorites)
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

    /** 取一次写连接即触发 `local_favorite.db` 的建表/迁移（与原 `dbHelper.writableDatabase` 同义）。 */
    private fun favWriter(): SqlDatabase = favorites.writer()

    private fun favReader(): SqlDatabase = favorites.reader()

    fun init() {
        scope.launch {
            // 触发建表（Android 侧即 helper 的 onCreate / onUpgrade）
            favWriter()
            migrateLegacyFavorites()
            refreshFolders()
        }
    }

    private suspend fun refreshFolders() = withContext(Dispatchers.IO) {
        val db = favReader()
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
        dbHelper.folderNames(favReader()).contains(name)
    }

    /**
     * 新建收藏夹，返回实际使用的名字。
     *
     * 对齐官方：空名或重名时，若 [renameWhenInvalidName] 为 true 则自动改名，否则抛异常。
     */
    suspend fun createFolder(name: String, renameWhenInvalidName: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val db = favWriter()
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
        val db = favWriter()
        if (!dbHelper.folderNames(db).contains(before)) return@withContext
        if (dbHelper.folderNames(db).contains(after)) throw IllegalArgumentException("Folder is existing")
        dbHelper.renameFolderTable(db, before, after)
        if (prefs.followUpdatesFolder == before) prefs.setFollowUpdatesFolder(after)
        notifyChanged()
    }

    /** 删除收藏夹（官方 `deleteFolder`）。 */
    suspend fun deleteFolder(name: String) = withContext(Dispatchers.IO) {
        val db = favWriter()
        dbHelper.dropFolderTable(db, name)
        if (prefs.followUpdatesFolder == name) prefs.setFollowUpdatesFolder(null)
        dbHelper.updateOrder(db, dbHelper.folderNames(db))
        notifyChanged()
    }

    /** 调整收藏夹顺序（官方 `updateOrder`）。 */
    suspend fun updateOrder(folders: List<String>) = withContext(Dispatchers.IO) {
        dbHelper.updateOrder(favWriter(), folders)
        notifyChanged()
    }

    /**
     * 实时读一遍收藏夹清单（按 `folder_order`）。
     *
     * 与 [folders] 缓存的区别：缓存要等 [init] 的那次协程跑完才有值，刚启动时读它是空的。
     * 备份导出这种"必须拿到当下真值"的场合用这个；UI 仍用 [folders]。
     */
    suspend fun currentFolders(): List<String> = withContext(Dispatchers.IO) {
        dbHelper.folderNames(favReader())
    }

    suspend fun folderComics(folder: String): Int = withContext(Dispatchers.IO) {
        countInternal(favReader(), folder)
    }

    private fun countInternal(db: SqlDatabase, folder: String): Int {
        if (!dbHelper.folderNames(db).contains(folder)) return 0
        return db.query("SELECT count(*) AS c FROM ${q(folder)}").firstOrNull()
            ?.long("c")?.toInt() ?: 0
    }

    // endregion

    // region ---- 收藏条目读取 ----

    suspend fun getFolderComics(folder: String): List<FavoriteItem> = withContext(Dispatchers.IO) {
        val db = favReader()
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext emptyList()
        db.query("SELECT * FROM ${q(folder)} ORDER BY display_order").map { rowToItem(it) }
    }

    /** 所有收藏夹的全部条目（去重，官方按 `id + type` 判等）。 */
    suspend fun getAllComics(): List<FavoriteItem> = withContext(Dispatchers.IO) {
        val db = favReader()
        val res = LinkedHashMap<Pair<String, Int>, FavoriteItem>()
        for (folder in dbHelper.folderNames(db)) {
            db.query("SELECT * FROM ${q(folder)} ORDER BY display_order").forEach { row ->
                val item = rowToItem(row)
                res.putIfAbsent(item.id to item.type, item)
            }
        }
        res.values.toList()
    }

    /** 某条目在哪些收藏夹里（官方 `find`）。 */
    suspend fun find(id: String, sourceKey: String): List<String> = withContext(Dispatchers.IO) {
        val db = favReader()
        val type = sourceKey.hashCode()
        dbHelper.folderNames(db).filter { folder ->
            db.query(
                "SELECT 1 FROM ${q(folder)} WHERE id = ? AND type = ? LIMIT 1",
                id, type.toString()
            ).isNotEmpty()
        }
    }

    suspend fun comicExists(folder: String, id: String, sourceKey: String): Boolean =
        withContext(Dispatchers.IO) {
            val db = favReader()
            if (!dbHelper.folderNames(db).contains(folder)) return@withContext false
            db.query(
                "SELECT 1 FROM ${q(folder)} WHERE id = ? AND type = ? LIMIT 1",
                id, sourceKey.hashCode().toString()
            ).isNotEmpty()
        }

    /** 是否已被收藏（任意收藏夹）。官方 `isExist` 走内存 hashedIds，这里直接查库。 */
    suspend fun isExist(id: String, sourceKey: String): Boolean =
        withContext(Dispatchers.IO) { findInternal(id, sourceKey).isNotEmpty() }

    private fun findInternal(id: String, sourceKey: String): List<String> {
        val db = favReader()
        val type = sourceKey.hashCode().toString()
        return dbHelper.folderNames(db).filter { folder ->
            db.query("SELECT 1 FROM ${q(folder)} WHERE id = ? AND type = ? LIMIT 1", id, type)
                .isNotEmpty()
        }
    }

    suspend fun searchInFolder(folder: String, keyword: String): List<FavoriteItem> =
        withContext(Dispatchers.IO) {
            if (keyword.isBlank()) return@withContext getFolderComicsInternal(folder)
            val like = "%${keyword.trim()}%"
            val db = favReader()
            if (!dbHelper.folderNames(db).contains(folder)) return@withContext emptyList()
            db.query(
                "SELECT * FROM ${q(folder)} WHERE name LIKE ? OR author LIKE ? OR tags LIKE ? ORDER BY display_order",
                like, like, like
            ).map { rowToItem(it) }
        }

    /** 跨收藏夹搜索（官方 `search`）。 */
    suspend fun search(keyword: String): List<FavoriteItem> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val like = "%${keyword.trim()}%"
        val db = favReader()
        val res = LinkedHashMap<Pair<String, Int>, FavoriteItem>()
        for (folder in dbHelper.folderNames(db)) {
            db.query(
                "SELECT * FROM ${q(folder)} WHERE name LIKE ? OR author LIKE ? OR tags LIKE ? ORDER BY display_order",
                like, like, like
            ).forEach { row ->
                val item = rowToItem(row)
                res.putIfAbsent(item.id to item.type, item)
            }
        }
        res.values.toList()
    }

    private fun getFolderComicsInternal(folder: String): List<FavoriteItem> {
        val db = favReader()
        if (!dbHelper.folderNames(db).contains(folder)) return emptyList()
        return db.query("SELECT * FROM ${q(folder)} ORDER BY display_order").map { rowToItem(it) }
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
        val db = favWriter()
        if (!dbHelper.folderNames(db).contains(folder)) throw IllegalArgumentException("Folder does not exists")
        if (comicExistsInternal(db, folder, comic.id, comic.type)) return@withContext false

        val displayOrder = order ?: if (prefs.newFavoriteAddTo == "end") {
            maxValue(db, folder) + 1
        } else {
            minValue(db, folder) - 1
        }

        insertIgnoringDuplicate(db, folder, comic, displayOrder)

        if (updateTime != null && dbHelper.hasColumn(db, folder, "last_update_time")) {
            db.exec(
                "UPDATE ${q(folder)} SET last_update_time = ? WHERE id = ? AND type = ?",
                updateTime, comic.id, comic.type.toString()
            )
        }
        notifyChanged()
        true
    }

    /**
     * 批量加入收藏（导入外部归档时用），返回**实际新增**的条数。
     *
     * 为什么不循环调 [addComic]：那个方法每插一条都要 `notifyChanged()`，而它内部会
     * launch 一次 [refreshFolders]（对**每个**收藏夹做一次全表 count）。导入上千条时会
     * 排出上千个这样的协程，界面长时间卡顿。这里改成单事务 + 末尾只通知一次。
     *
     * 去重口径与 [addComic] 一致，靠主键 `(id, type)` 上的 IGNORE 语义，
     * 所以已存在的条目既不会写重、也不计入返回值。空 id 的行直接跳过 —— 落库就是一行
     * 看不见也删不掉的垃圾。
     *
     * [comics] 的**先后顺序会被保留**（自当前夹子末尾依次追加），调用方应先把归档里的
     * 条目按 `display_order` 排好再传进来。
     */
    suspend fun addComics(folder: String, comics: List<FavoriteItem>): Int = withContext(Dispatchers.IO) {
        if (comics.isEmpty()) return@withContext 0
        val db = favWriter()
        if (!dbHelper.folderNames(db).contains(folder)) {
            throw IllegalArgumentException("Folder does not exists")
        }

        var added = 0
        db.inTransaction {
            var order = maxValue(db, folder)
            for (comic in comics) {
                if (comic.id.isBlank()) continue
                order++
                // 改造前靠 `insertWithOnConflict` 返回 -1 判「没插进去」；门面不回报影响行数，
                // 所以这里显式查一次主键 (id, type)。同一事务内前面插的行本就可见，判重口径一致。
                if (comicExistsInternal(db, folder, comic.id, comic.type)) continue
                insertIgnoringDuplicate(db, folder, comic, order)
                added++
            }
        }
        if (added > 0) notifyChanged()
        added
    }

    suspend fun deleteComicWithId(folder: String, id: String, sourceKey: String) =
        withContext(Dispatchers.IO) {
            val db = favWriter()
            if (!dbHelper.folderNames(db).contains(folder)) return@withContext
            db.exec(
                "DELETE FROM ${q(folder)} WHERE id = ? AND type = ?",
                id, sourceKey.hashCode().toString()
            )
            notifyChanged()
        }

    suspend fun batchDeleteComics(folder: String, comics: List<FavoriteItem>) =
        withContext(Dispatchers.IO) {
            val db = favWriter()
            if (!dbHelper.folderNames(db).contains(folder)) return@withContext
            db.inTransaction {
                comics.forEach {
                    db.exec(
                        "DELETE FROM ${q(folder)} WHERE id = ? AND type = ?",
                        it.id, it.type.toString()
                    )
                }
            }
            notifyChanged()
        }

    /** 在所有收藏夹中删除（官方 `batchDeleteComicsInAllFolders`）。 */
    suspend fun batchDeleteComicsInAllFolders(comics: List<FavoriteItem>) =
        withContext(Dispatchers.IO) {
            val db = favWriter()
            val names = dbHelper.folderNames(db)
            db.inTransaction {
                comics.forEach { item ->
                    names.forEach { folder ->
                        db.exec(
                            "DELETE FROM ${q(folder)} WHERE id = ? AND type = ?",
                            item.id, item.type.toString()
                        )
                    }
                }
            }
            notifyChanged()
        }

    /** 移动单条到另一个收藏夹（官方 `moveFavorite`，目标插到开头）。 */
    suspend fun moveFavorite(sourceFolder: String, targetFolder: String, id: String, sourceKey: String) =
        withContext(Dispatchers.IO) {
            val db = favWriter()
            val names = dbHelper.folderNames(db)
            if (sourceFolder !in names || targetFolder !in names) return@withContext
            val type = sourceKey.hashCode().toString()
            db.inTransaction {
                db.exec(
                    "INSERT OR IGNORE INTO ${q(targetFolder)} " +
                        "(id, name, author, type, source_key, tags, cover_path, time, display_order) " +
                        "SELECT id, name, author, type, source_key, tags, cover_path, time, ? " +
                        "FROM ${q(sourceFolder)} WHERE id = ? AND type = ?",
                    minValue(db, targetFolder) - 1, id, type
                )
                db.exec(
                    "DELETE FROM ${q(sourceFolder)} WHERE id = ? AND type = ?",
                    id, type
                )
            }
            notifyChanged()
        }

    /** 批量移动（官方 `batchMoveFavorites`：保留相对顺序，追加到目标末尾）。 */
    suspend fun batchMoveFavorites(
        sourceFolder: String,
        targetFolder: String,
        comics: List<FavoriteItem>,
    ) = withContext(Dispatchers.IO) {
        val db = favWriter()
        val names = dbHelper.folderNames(db)
        if (sourceFolder !in names || targetFolder !in names) return@withContext
        db.inTransaction {
            var displayOrder = maxValue(db, targetFolder) + 1
            comics.forEach { item ->
                copyRow(db, sourceFolder, targetFolder, displayOrder, item)
                db.exec(
                    "DELETE FROM ${q(sourceFolder)} WHERE id = ? AND type = ?",
                    item.id, item.type.toString()
                )
                displayOrder++
            }
        }
        notifyChanged()
    }

    /** 批量复制（官方 `batchCopyFavorites`：只复制不删除源）。 */
    suspend fun batchCopyFavorites(
        sourceFolder: String,
        targetFolder: String,
        comics: List<FavoriteItem>,
    ) = withContext(Dispatchers.IO) {
        val db = favWriter()
        val names = dbHelper.folderNames(db)
        if (sourceFolder !in names || targetFolder !in names) return@withContext
        db.inTransaction {
            var displayOrder = maxValue(db, targetFolder) + 1
            comics.forEach { item ->
                copyRow(db, sourceFolder, targetFolder, displayOrder, item)
                displayOrder++
            }
        }
        notifyChanged()
    }

    /** 手动排序：按给定顺序重写 display_order（官方 `reorder`）。 */
    suspend fun reorder(newFolder: List<FavoriteItem>, folder: String) = withContext(Dispatchers.IO) {
        val db = favWriter()
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext
        db.inTransaction {
            newFolder.forEachIndexed { i, item ->
                db.exec(
                    "UPDATE ${q(folder)} SET display_order = ? WHERE id = ? AND type = ?",
                    i, item.id, item.type.toString()
                )
            }
        }
        notifyChanged()
    }

    suspend fun updateInfo(folder: String, comic: FavoriteItem) = withContext(Dispatchers.IO) {
        val db = favWriter()
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext
        db.exec(
            "UPDATE ${q(folder)} SET name = ?, author = ?, tags = ?, cover_path = ? WHERE id = ? AND type = ?",
            comic.name, comic.author, tagsToString(comic.tags), comic.coverPath, comic.id, comic.type.toString()
        )
        notifyChanged()
    }

    suspend fun editTags(id: String, folder: String, tags: List<String>) = withContext(Dispatchers.IO) {
        val db = favWriter()
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext
        db.exec(
            "UPDATE ${q(folder)} SET tags = ? WHERE id = ?",
            tagsToString(tags), id
        )
        notifyChanged()
    }

    /**
     * 阅读后调整（官方 `onRead`）：按 `moveFavoriteAfterRead` 把条目移到收藏夹首尾，
     * 若是追更夹则顺带清掉 NEW 标记。
     */
    suspend fun onRead(id: String, sourceKey: String) = withContext(Dispatchers.IO) {
        val db = favWriter()
        val type = sourceKey.hashCode().toString()
        val move = prefs.moveFavoriteAfterRead ?: return@withContext
        val followFolder = prefs.followUpdatesFolder

        db.inTransaction {
            for (folder in dbHelper.folderNames(db)) {
                if (!comicExistsInternal(db, folder, id, sourceKey.hashCode())) continue
                val locationSql = when (move) {
                    "end" -> "display_order = ${maxValue(db, folder) + 1},"
                    "start" -> "display_order = ${minValue(db, folder) - 1},"
                    else -> ""
                }
                val updateFlag = if (followFolder == folder) "has_new_update = 0," else ""
                db.exec(
                    "UPDATE ${q(folder)} SET $locationSql $updateFlag time = ? WHERE id = ? AND type = ?",
                    FavoriteItem.currentTimeString(), id, type
                )
            }
        }
        notifyChanged()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val db = favWriter()
        dbHelper.folderNames(db).forEach { folder ->
            db.exec("DELETE FROM ${q(folder)}")
        }
        notifyChanged()
    }

    // endregion

    // region ---- 网络收藏夹绑定 / 导入导出 ----

    suspend fun linkFolderToNetwork(folder: String, source: String, networkFolder: String) =
        withContext(Dispatchers.IO) {
            favWriter().exec(
                "INSERT OR REPLACE INTO folder_sync (folder_name, source_key, source_folder) VALUES (?, ?, ?)",
                folder, source, networkFolder
            )
            notifyChanged()
        }

    suspend fun unlinkFolderFromNetwork(folder: String) = withContext(Dispatchers.IO) {
        favWriter().exec("DELETE FROM folder_sync WHERE folder_name = ?", folder)
        notifyChanged()
    }

    data class FolderSyncInfo(val folder: String, val sourceKey: String?, val sourceFolder: String?)

    suspend fun getFolderSync(): List<FolderSyncInfo> = withContext(Dispatchers.IO) {
        favReader().query("SELECT folder_name, source_key, source_folder FROM folder_sync").map { row ->
            FolderSyncInfo(
                folder = row.requiredString("folder_name"),
                sourceKey = row.string("source_key"),
                sourceFolder = row.string("source_folder"),
            )
        }
    }

    suspend fun folderToJson(folder: String): String = withContext(Dispatchers.IO) {
        val arr = JsonArray()
        getFolderComicsInternal(folder).forEach { item ->
            val obj = JsonObject()
            obj.addProperty("id", item.id)
            obj.addProperty("name", item.name)
            obj.addProperty("author", item.author)
            obj.addProperty("sourceKey", item.sourceKey)
            obj.addProperty("coverPath", item.coverPath)
            obj.add("tags", JsonArray().apply { item.tags.forEach { t -> add(t) } })
            arr.add(obj)
        }
        JsonObject().apply {
            addProperty("name", folder)
            add("comics", arr)
        }.toString()
    }

    // endregion

    // region ---- 追更 ----

    suspend fun prepareTableForFollowUpdates(table: String, clearData: Boolean = true) =
        withContext(Dispatchers.IO) {
            val db = favWriter()
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
        val db = favWriter()
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext
        if (!dbHelper.hasColumn(db, folder, "last_update_time")) return@withContext
        val type = sourceKey.hashCode().toString()
        val oldTime = db.query(
            "SELECT last_update_time FROM ${q(folder)} WHERE id = ? AND type = ?",
            id, type
        ).firstOrNull()?.string("last_update_time")
        val hasNewUpdate = oldTime != updateTime
        db.exec(
            "UPDATE ${q(folder)} SET last_update_time = ?, has_new_update = ?, last_check_time = ? WHERE id = ? AND type = ?",
            updateTime, if (hasNewUpdate) 1 else 0, System.currentTimeMillis(), id, type
        )
        notifyChanged()
    }

    /** 只刷新检查时间、不改更新状态（官方 `updateCheckTime`），用于节流。 */
    suspend fun updateCheckTime(folder: String, id: String, sourceKey: String) =
        withContext(Dispatchers.IO) {
            val db = favWriter()
            if (!dbHelper.folderNames(db).contains(folder)) return@withContext
            if (!dbHelper.hasColumn(db, folder, "last_check_time")) return@withContext
            db.exec(
                "UPDATE ${q(folder)} SET last_check_time = ? WHERE id = ? AND type = ?",
                System.currentTimeMillis(), id, sourceKey.hashCode().toString()
            )
        }

    suspend fun countUpdates(folder: String): Int = withContext(Dispatchers.IO) {
        val db = favReader()
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext 0
        if (!dbHelper.hasColumn(db, folder, "has_new_update")) return@withContext 0
        db.query("SELECT count(*) AS c FROM ${q(folder)} WHERE has_new_update = 1").firstOrNull()
            ?.long("c")?.toInt() ?: 0
    }

    /** 仅返回有新更新的条目（官方 `getUpdates`）。 */
    suspend fun getUpdates(folder: String): List<FavoriteItemWithUpdateInfo> =
        withContext(Dispatchers.IO) { queryUpdates(folder, onlyNew = true) }

    /** 返回夹内全部条目及其更新信息（官方 `getComicsWithUpdatesInfo`）。 */
    suspend fun getComicsWithUpdatesInfo(folder: String): List<FavoriteItemWithUpdateInfo> =
        withContext(Dispatchers.IO) { queryUpdates(folder, onlyNew = false) }

    private fun queryUpdates(folder: String, onlyNew: Boolean): List<FavoriteItemWithUpdateInfo> {
        val db = favReader()
        if (!dbHelper.folderNames(db).contains(folder)) return emptyList()
        if (!dbHelper.hasColumn(db, folder, "has_new_update")) return emptyList()
        val where = if (onlyNew) " WHERE has_new_update = 1" else ""
        return db.query("SELECT * FROM ${q(folder)}$where").map { row ->
            FavoriteItemWithUpdateInfo(
                item = rowToItem(row),
                // 保持「读不到就是空串」：官方那侧靠空串与 null 的差别不多，但 UI 的
                // description 会把 null 显示成 "Unknown"、空串显示成空 —— 这里不替它改口径。
                updateTime = row.optString("last_update_time"),
                hasNewUpdate = row.optInt("has_new_update") == 1,
                lastCheckTime = row.optLongOrNull("last_check_time"),
            )
        }
    }

    suspend fun markAsRead(id: String, sourceKey: String) = withContext(Dispatchers.IO) {
        val folder = prefs.followUpdatesFolder ?: return@withContext
        val db = favWriter()
        if (!dbHelper.folderNames(db).contains(folder)) return@withContext
        if (!dbHelper.hasColumn(db, folder, "has_new_update")) return@withContext
        db.exec(
            "UPDATE ${q(folder)} SET has_new_update = 0 WHERE id = ? AND type = ?",
            id, sourceKey.hashCode().toString()
        )
        notifyChanged()
    }

    // endregion

    // region ---- 内部工具 ----

    /** 原 `insertWithOnConflict(表, null, values, CONFLICT_IGNORE)` 的等价语句，列序同原 ContentValues。 */
    private fun insertIgnoringDuplicate(
        db: SqlDatabase,
        folder: String,
        comic: FavoriteItem,
        displayOrder: Int,
    ) {
        db.exec(
            "INSERT OR IGNORE INTO ${q(folder)} " +
                "(id, name, author, type, source_key, tags, cover_path, time, translated_tags, display_order) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            comic.id, comic.name, comic.author, comic.type, comic.sourceKey,
            tagsToString(comic.tags), comic.coverPath, comic.time, tagsToString(comic.tags), displayOrder
        )
    }

    /** 跨夹搬运（官方那条 `INSERT OR IGNORE ... SELECT`，批量移动与批量复制共用）。 */
    private fun copyRow(
        db: SqlDatabase,
        sourceFolder: String,
        targetFolder: String,
        displayOrder: Int,
        item: FavoriteItem,
    ) {
        db.exec(
            "INSERT OR IGNORE INTO ${q(targetFolder)} " +
                "(id, name, author, type, source_key, tags, cover_path, time, display_order) " +
                "SELECT id, name, author, type, source_key, tags, cover_path, time, ? " +
                "FROM ${q(sourceFolder)} WHERE id = ? AND type = ?",
            displayOrder, item.id, item.type.toString()
        )
    }

    private fun comicExistsInternal(db: SqlDatabase, folder: String, id: String, type: Int): Boolean =
        db.query(
            "SELECT 1 FROM ${q(folder)} WHERE id = ? AND type = ? LIMIT 1",
            id, type.toString()
        ).isNotEmpty()

    private fun maxValue(db: SqlDatabase, folder: String): Int =
        db.query("SELECT MAX(display_order) AS max_value FROM ${q(folder)}").firstOrNull()
            ?.long("max_value")?.toInt() ?: 0

    private fun minValue(db: SqlDatabase, folder: String): Int =
        db.query("SELECT MIN(display_order) AS min_value FROM ${q(folder)}").firstOrNull()
            ?.long("min_value")?.toInt() ?: 0

    private fun rowToItem(c: SqlRow): FavoriteItem = FavoriteItem(
        id = c.requiredString("id"),
        name = c.requiredString("name"),
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

    /**
     * 一次性迁移：把旧 `venera_core.db` 的 `comic_favorite` 单表数据搬进新的
     * 「每夹一表」结构。旧表遷移后即清空，避免重复搬家。
     */
    private fun migrateLegacyFavorites() {
        val legacy = core.reader()
        val target = favWriter()
        val hasLegacy = legacy.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='comic_favorite'"
        ).isNotEmpty()
        if (!hasLegacy) return

        val rows = legacy.query("SELECT * FROM comic_favorite").map { c ->
            LegacyRow(
                comicId = c.optString("comic_id"),
                title = c.optString("title"),
                author = c.optString("author"),
                coverUrl = c.optString("cover_url"),
                sourceName = c.optString("source_name"),
                folderName = c.optString("folder_name").ifBlank { LocalFavoriteDatabase.DEFAULT_FOLDER },
                tags = c.optString("tags"),
                hasUpdate = c.optInt("has_update"),
                latestChapter = c.optString("latest_chapter"),
                createdAt = c.optLongOrZero("created_at"),
            )
        }
        if (rows.isEmpty()) return

        val existingFolders = dbHelper.folderNames(target).toMutableSet()
        target.inTransaction {
            rows.forEach { r ->
                val folder = r.folderName.ifBlank { LocalFavoriteDatabase.DEFAULT_FOLDER }
                if (folder !in existingFolders) {
                    dbHelper.createFolderTable(target, folder)
                    existingFolders.add(folder)
                }
                val type = r.sourceName.hashCode()
                target.exec(
                    "INSERT OR IGNORE INTO ${q(folder)} " +
                        "(id, name, author, type, source_key, tags, cover_path, time, translated_tags, display_order) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    r.comicId, r.title, r.author, type, r.sourceName, r.tags, r.coverUrl,
                    FavoriteItem.currentTimeString(r.createdAt), r.tags,
                    maxValue(target, folder) + 1
                )
                if (r.hasUpdate == 1 && r.latestChapter.isNotBlank()) {
                    dbHelper.prepareTableForFollowUpdates(target, folder, clearData = false)
                    target.exec(
                        "UPDATE ${q(folder)} SET last_update_time = ?, has_new_update = 1 WHERE id = ? AND type = ?",
                        r.latestChapter, r.comicId, type.toString()
                    )
                }
            }
        }
        // 旧表清空，防止二次迁移
        core.writer().exec("DELETE FROM comic_favorite")
        _version.value++
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

        /**
         * 兼容既有调用点的取用写法（UI / ViewModel / 备份与导入递的是 Android 的 Context）。
         *
         * 参数类型只能是 `Any`：本层不许再出现 Android 的 Context 类型，句柄原样交给
         * 平台装的 factory 去解释（Android 侧不是 Context 就抛）。首次取用时才建接线口，
         * 时机与改造前 `getInstance(context)` 里现取 helper 完全一致。
         */
        fun getInstance(context: Any): LocalFavoritesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val ports = DatabasePorts.of(context)
                    LocalFavoritesManager(
                        core = ports.core,
                        favorites = ports.localFavorites,
                        prefs = ports.favoritesPreferences,
                    ).also {
                        INSTANCE = it
                        it.init()
                    }
                }
            }
        }
    }
}
