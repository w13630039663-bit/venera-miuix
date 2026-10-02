package com.venera.compose.sync

import android.content.Context
import android.util.Log
import com.venera.compose.data.db.CoreTableBackup
import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.db.LocalFavoriteDatabase
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.db.optInt
import com.venera.compose.data.db.optString
import com.venera.compose.data.db.requiredString
import com.venera.compose.data.platform.SqlDatabase
import com.venera.compose.data.platform.android.openReadOnlySqlDatabase
import com.venera.compose.data.db.optLongOrZero
import com.venera.compose.data.db.parseJsonObject
import com.venera.compose.data.db.optTextValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * 导入**外部归档** —— 官方 Venera（Flutter）与 PicaComic 导出的备份包。
 *
 * ## 为什么要单独一条链路
 *
 * 本仓自有的 `.venera` 装的是九个成员（八个数据 JSON 加一份元信息，见 [BackupManager]），而官方 Venera 的 `.venera`
 * 装的是 SQLite 文件：`appdata.json` / `history.db` / `local_favorite.db` / `cookie.db`，
 * 外加 `comic_source/` 目录下的源文件。
 * PicaComic 的 `.picadata` 又是另一套：`appdata`（无扩展名）/ `local_favorite.db` /
 * `history.db` / `cookies.db`（复数）/ `comic_source/` 目录。**三者只是扩展名像，内容物毫无关系**，
 * 所以识别一律看**内容**，不看后缀。
 *
 * ## 只做收藏 + 历史两条，这是有意的
 *
 * 搬运不过来、也**不该假装能搬**的东西，如实列在这里：
 * - `image_favorites`（图片收藏）：本仓的 `favorite_images` 是另一张表（`id/comic_id/.../local_path`），
 *   与官方 `image_favorites(id, ep, page, title, ...)` 的形状对不上，硬塞进去只会得到一堆打不开的图。
 * - `cookie.db` / `cookies.db`：登录态。两个应用的 Cookie 库结构不同，而且认证本来就应该在新设备上重来。
 * - `appdata.json` / `appdata`：偏好设置。本仓的设置项与官方并非一一对应，照搬会产生一堆"看起来设置了其实没生效"的开关。
 * - **画廊的关注名单与画廊收藏**：画廊是本分支独有的模块（yande.re / Gelbooru / Safebooru 三个图站），
 *   上游归档里压根没有对应物，不是"没做映射"。本仓自己导出的包才带这两栏。
 *
 * ## 口径是「逐条合并」，不是「覆盖库文件」
 *
 * 直接把归档里的 `local_favorite.db` 盖到本仓的库上会**立刻坏库**：主键列名不同
 * （PicaComic 叫 `target`，本仓叫 `id`）、`type` 的算法不同（见 [ForeignImportMapping]）。
 * 所以全程是：只读打开归档内的库 → 逐条翻译 → 走本仓的 [LocalFavoritesManager] / 历史表写入。
 *
 * ## 这条链路的上游依据
 *
 * 官方 Venera 自己就带 `importPicaData()`（`.reference/flutter-master/lib/utils/data.dart:115`），
 * 它读的正是 PicaComic 的 `local_favorite.db` / `history.db`。本文件按它的口径实现，
 * 而不是另起一套 —— 包括那几个别扭的枚举值（收藏 nhentai=6、历史 nhentai=5）。
 *
 * ## 读外部库时的"缺列"为什么可以宽容
 *
 * 归档里那两张库**不是本应用维护的结构**，两个上游各有一套列名（`target` vs `id`、
 * `key`/`sync_data` vs `source_key`/`source_folder`），判据本来就写在"这一列在不在"上。
 * 所以那一侧的读法走 `data/db/RowRead.kt` 的 `optString` / `optInt` / `optLongOrZero`
 * （缺列给改造前同样的 `""` / `0` / `0L`），**值本身读不成对应类型仍然抛**。
 * 写进本仓的那一张表（`comic_history`）没有这种宽容：语句与列名走
 * [CoreTableBackup.INSERT_OR_REPLACE_HISTORY]，写不下去就抛、整笔回滚。
 */
class ForeignArchiveImport private constructor(private val context: Context) {

    private val tag = "ForeignArchiveImport"

    /** 一包外部归档导完之后的结果。 */
    data class Report(
        val origin: String,
        val folderCount: Int,
        val favoriteCount: Int,
        val historyCount: Int,
        val favoriteSkipped: Int,
        val historySkipped: Int,
        /** 认不出的 `type` 原始值，写进日志便于排查是哪个源没认出来。 */
        val unknownTypes: Set<Int>,
    ) {
        fun toSummary(): BackupSummary = BackupSummary(
            historyCount = historyCount,
            favoriteCount = favoriteCount,
            statsCount = 0,
            guardRulesCount = 0,
            guardRulesSkipped = 0,
            timestamp = System.currentTimeMillis(),
            origin = origin,
            folderCount = folderCount,
            foreignSkipped = favoriteSkipped + historySkipped,
            // 这三栏只有本仓自有归档才有（画廊无上游对应物、官方 image_favorites 表形状对不上），
            // 显式写 0 而不是留默认值：默认值会让"漏传"和"确实一条没有"在文案里长得一模一样。
            imageFavoriteCount = 0,
            galleryFavoriteCount = 0,
            galleryFollowCount = 0,
        )
    }

    /**
     * 导入一包外部归档。
     *
     * 整包先解到私有缓存再动库：格式不认识要在**写库之前**就失败，
     * 否则会留下"收藏进了一半、历史没进"的半截状态。
     */
    suspend fun importArchive(archive: File): Result<Report> = withContext(Dispatchers.IO) {
        runCatching {
            val workDir = File(context.cacheDir, "foreign_import_${System.currentTimeMillis()}")
            check(workDir.isDirectory || workDir.mkdirs()) { "无法创建临时目录" }
            try {
                extract(archive, workDir)

                val favoriteDbFile = File(workDir, FAVORITE_DB)
                val historyDbFile = File(workDir, HISTORY_DB)
                require(favoriteDbFile.exists() || historyDbFile.exists()) {
                    "归档里既没有 $FAVORITE_DB 也没有 $HISTORY_DB，不像是 Venera / PicaComic 的备份"
                }

                var origin = BackupOrigin.VENERA
                var folderCount = 0
                var favoriteCount = 0
                var favoriteSkipped = 0
                val unknownTypes = linkedSetOf<Int>()

                // 候选源 key：归档里带的（官方把每个源存成 comic_source/<key>.js）+ 本仓内置源。
                // 官方归档的 type 是 Dart 哈希，没有明文来源名，只能靠这张表反查。
                val candidateKeys = linkedSetOf<String>().apply {
                    addAll(sourceKeysInArchive(workDir))
                    addAll(ForeignImportMapping.BUILT_IN_SOURCE_KEYS)
                }

                if (favoriteDbFile.exists()) {
                    openReadOnly(favoriteDbFile).use { db ->
                        val folderTables = folderTablesOf(db)
                        val isPica = folderTables.firstOrNull()
                            ?.let { columnsOf(db, it).contains("target") } ?: false
                        origin = if (isPica) BackupOrigin.PICA_COMIC else BackupOrigin.VENERA

                        // PicaComic 的 folder_sync.key 是**明文**源 key，把自定义源也纳入候选，
                        // 否则那些条目的 type（Dart 哈希）反查不到。
                        if (isPica) candidateKeys.addAll(plainSourceKeysFromFolderSync(db))
                        val hashIndex = ForeignImportMapping.dartHashSourceIndex(candidateKeys)

                        val outcome = importFavorites(db, isPica, hashIndex, unknownTypes)
                        folderCount = outcome.folderCount
                        favoriteCount = outcome.added
                        favoriteSkipped = outcome.skipped
                    }
                }

                var historyCount = 0
                var historySkipped = 0
                if (historyDbFile.exists()) {
                    openReadOnly(historyDbFile).use { db ->
                        val isPica = !columnsOf(db, HISTORY_TABLE).contains("id")
                        if (!favoriteDbFile.exists()) {
                            origin = if (isPica) BackupOrigin.PICA_COMIC else BackupOrigin.VENERA
                        }
                        val hashIndex = ForeignImportMapping.dartHashSourceIndex(candidateKeys)
                        val outcome = importHistories(db, isPica, hashIndex, unknownTypes)
                        historyCount = outcome.added
                        historySkipped = outcome.skipped
                    }
                }

                if (unknownTypes.isNotEmpty()) {
                    Log.i(tag, "认不出的来源 type：${unknownTypes.joinToString()}")
                }
                Log.i(
                    tag,
                    "导入完成（$origin）：$folderCount 夹 / $favoriteCount 收藏 / $historyCount 历史，" +
                        "跳过 ${favoriteSkipped + historySkipped} 条"
                )

                Report(
                    origin = origin,
                    folderCount = folderCount,
                    favoriteCount = favoriteCount,
                    historyCount = historyCount,
                    favoriteSkipped = favoriteSkipped,
                    historySkipped = historySkipped,
                    unknownTypes = unknownTypes,
                )
            } finally {
                workDir.deleteRecursively()
            }
        }
    }

    // region ---- 收藏 ----

    private data class FavoritesOutcome(val folderCount: Int, val added: Int, val skipped: Int)

    private suspend fun importFavorites(
        db: SqlDatabase,
        isPica: Boolean,
        hashIndex: Map<Int, String>,
        unknownTypes: MutableSet<Int>,
    ): FavoritesOutcome {
        val manager = LocalFavoritesManager.getInstance(context)
        val folderTables = folderTablesOf(db)
        val folders = orderedFolders(db, folderTables)
        // 必须在建夹**之前**记下来：createFolder 会把新夹子的 folder_order 写成 0，
        // 建夹后再读，本机原有夹子的相对次序就被这批 0 冲乱了。
        val localFoldersBefore = manager.currentFolders()

        var folderCount = 0
        for (folder in folders) {
            if (folder.isBlank()) continue
            if (!manager.existsFolder(folder)) {
                // renameWhenInvalidName 保持默认 false：夹名是用户起的，静默改名会让
                // "这一本在哪个夹子里"和归档对不上号。
                runCatching { manager.createFolder(folder) }
            }
            if (manager.existsFolder(folder)) folderCount++
        }

        // 口径与 [BackupManager] 一致：归档里的夹子按归档顺序排在前面，
        // 本机原有而归档没有的接在后面。
        val restored = folders.filter { it.isNotBlank() && manager.existsFolder(it) }
        val localOnly = localFoldersBefore.filterNot { it in restored }
        if (restored.isNotEmpty()) manager.updateOrder(restored + localOnly)

        var added = 0
        var skipped = 0
        for (folder in folders) {
            if (folder.isBlank() || !manager.existsFolder(folder)) continue
            val items = ArrayList<FavoriteItem>()
            for (row in readFolderRows(db, folder)) {
                if (row.id.isBlank()) {
                    skipped++
                    continue
                }
                val sourceKey = resolveSourceKey(
                    type = row.type,
                    isPica = isPica,
                    forHistory = false,
                    hashIndex = hashIndex,
                )
                if (sourceKey == null) {
                    skipped++
                    unknownTypes += row.type
                    continue
                }
                items += FavoriteItem(
                    id = row.id,
                    name = row.name,
                    author = row.author,
                    sourceKey = sourceKey,
                    tags = splitTags(row.tags),
                    coverPath = row.coverPath,
                    time = row.time.ifBlank { FavoriteItem.currentTimeString() },
                )
            }
            added += manager.addComics(folder, items)
        }

        linkFoldersToNetwork(db, isPica)
        return FavoritesOutcome(folderCount, added, skipped)
    }

    /**
     * 收藏夹 ↔ 网络收藏夹的绑定。
     *
     * 两边列名不同：PicaComic 是 `key` / `sync_data`（JSON，`folderId` 才是夹 ID），
     * 本仓与官方 Venera 是 `source_key` / `source_folder`。判据按列在不在，老库缺列就跳过。
     */
    private suspend fun linkFoldersToNetwork(db: SqlDatabase, isPica: Boolean) {
        if (!tableExists(db, "folder_sync")) return
        val cols = columnsOf(db, "folder_sync")
        val manager = LocalFavoritesManager.getInstance(context)

        if (isPica) {
            if (!cols.contains("key") || !cols.contains("sync_data")) return
            db.query("SELECT folder_name, key, sync_data FROM folder_sync").forEach { c ->
                val folder = c.optString("folder_name")
                val key = ForeignImportMapping.normalizeSourceKey(c.optString("key"))
                val folderId = runCatching {
                    parseJsonObject(c.optString("sync_data")).optTextValue("folderId")
                }.getOrDefault("")
                if (folder.isNotBlank() && key.isNotBlank() && folderId.isNotBlank() &&
                    manager.existsFolder(folder)
                ) {
                    manager.linkFolderToNetwork(folder, key, folderId)
                }
            }
        } else {
            if (!cols.contains("source_key") || !cols.contains("source_folder")) return
            db.query("SELECT folder_name, source_key, source_folder FROM folder_sync").forEach { c ->
                val folder = c.optString("folder_name")
                val key = ForeignImportMapping.normalizeSourceKey(c.optString("source_key"))
                val sourceFolder = c.optString("source_folder")
                if (folder.isNotBlank() && key.isNotBlank() && sourceFolder.isNotBlank() &&
                    manager.existsFolder(folder)
                ) {
                    manager.linkFolderToNetwork(folder, key, sourceFolder)
                }
            }
        }
    }

    // endregion

    // region ---- 历史 ----

    private data class HistoriesOutcome(val added: Int, val skipped: Int)

    private suspend fun importHistories(
        db: SqlDatabase,
        isPica: Boolean,
        hashIndex: Map<Int, String>,
        unknownTypes: MutableSet<Int>,
    ): HistoriesOutcome {
        if (!tableExists(db, HISTORY_TABLE)) return HistoriesOutcome(0, 0)
        val cols = columnsOf(db, HISTORY_TABLE)
        val idColumn = if (cols.contains("id")) "id" else "target"
        val now = System.currentTimeMillis()

        val records = ArrayList<HistoryRecord>()
        var skipped = 0
        db.query("SELECT * FROM $HISTORY_TABLE").forEach { c ->
            val id = c.optString(idColumn)
            if (id.isBlank()) {
                skipped++
                return@forEach
            }
            val type = c.optInt("type")
            val sourceKey = resolveSourceKey(
                type = type,
                isPica = isPica,
                forHistory = true,
                hashIndex = hashIndex,
            )
            if (sourceKey == null) {
                skipped++
                unknownTypes += type
                return@forEach
            }
            records += HistoryRecord(
                comicId = id,
                title = c.optString("title"),
                // 官方的 subtitle 就是作者，本仓作者单列
                author = c.optString("subtitle"),
                coverUrl = c.optString("cover"),
                sourceName = sourceKey,
                // 归档里没有章节标题（只有索引），如实留空，不编一个"第 N 话"填进去
                lastChapterTitle = "",
                // ⚠️ 归档是 1 基、本仓是 0 基，不转换会整体偏移一章一页
                lastChapterIndex = ForeignImportMapping.toZeroBasedIndex(c.optInt("ep")),
                lastPageIndex = ForeignImportMapping.toZeroBasedIndex(c.optInt("page")),
                totalPages = c.optInt("max_page"),
                // 缺时间戳的记录会沉到历史最底，等于看不见；用导入时刻兜底
                updatedAt = c.optLongOrZero("time").takeIf { it > 0 } ?: now,
            )
        }
        if (records.isEmpty()) return HistoriesOutcome(0, skipped)

        val core = DatabasePorts.of(context).core.writer()
        core.inTransaction {
            for (record in records) {
                core.exec(
                    CoreTableBackup.INSERT_OR_REPLACE_HISTORY,
                    record.comicId, record.title, record.author, record.coverUrl, record.sourceName,
                    record.lastChapterTitle, record.lastChapterIndex, record.lastPageIndex,
                    record.totalPages, record.updatedAt
                )
            }
        }
        // 直接写库不会刷新 HistoryDao 的缓存 flow，不补这一下历史页要等重启才看得见
        HistoryDao.getInstance(context).refresh()
        return HistoriesOutcome(records.size, skipped)
    }

    // endregion

    // region ---- 只读打开与列/表探查 ----

    private fun openReadOnly(file: File): SqlDatabase =
        openReadOnlySqlDatabase(file.absolutePath)

    private fun foldersOf(db: SqlDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type='table'")
            .map { it.requiredString("name") }

    /** 收藏夹表 = 除元数据表以外的所有表（两边都是"一夹一表"的结构）。 */
    private fun folderTablesOf(db: SqlDatabase): List<String> =
        foldersOf(db).filterNot { it in META_TABLES }

    private fun columnsOf(db: SqlDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(${LocalFavoriteDatabase.quoteId(table)})")
            .map { it.requiredString("name") }

    private fun tableExists(db: SqlDatabase, table: String): Boolean =
        db.query(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name = ? LIMIT 1",
            table
        ).isNotEmpty()

    private fun orderedFolders(db: SqlDatabase, tables: List<String>): List<String> {
        val order = HashMap<String, Int>()
        if (tableExists(db, "folder_order")) {
            db.query("SELECT folder_name, order_value FROM folder_order").forEach { c ->
                order[c.optString("folder_name")] = c.optInt("order_value")
            }
        }
        return tables.sortedWith(compareBy({ order[it] ?: 0 }, { it }))
    }

    private data class RawFavorite(
        val id: String,
        val name: String,
        val author: String,
        val type: Int,
        val tags: String,
        val coverPath: String,
        val time: String,
    )

    private fun readFolderRows(db: SqlDatabase, folder: String): List<RawFavorite> {
        val cols = columnsOf(db, folder)
        val idColumn = if (cols.contains("target")) "target" else "id"
        val orderBy = if (cols.contains("display_order")) " ORDER BY display_order" else ""
        return db.query("SELECT * FROM ${LocalFavoriteDatabase.quoteId(folder)}$orderBy")
            .map { c ->
                RawFavorite(
                    id = c.optString(idColumn),
                    name = c.optString("name"),
                    author = c.optString("author"),
                    type = c.optInt("type"),
                    tags = c.optString("tags"),
                    coverPath = c.optString("cover_path"),
                    time = c.optString("time"),
                )
            }
    }

    // endregion

    // region ---- 归档解包 ----

    private fun extract(archive: File, workDir: File) {
        val root = workDir.canonicalPath + File.separator
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name
                if (!isWantedEntry(name)) continue

                val target = File(workDir, name)
                // Zip Slip：条目名里的 `../` 会把文件写出去
                if (!target.canonicalPath.startsWith(root)) {
                    Log.w(tag, "跳过可疑条目：$name")
                    continue
                }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }

    /** 只要这三个来源：收藏库、历史库、源定义目录（用来反查 Dart 哈希）。 */
    private fun isWantedEntry(name: String): Boolean =
        name == FAVORITE_DB || name == HISTORY_DB || name.startsWith("$SOURCE_DIR/")

    /**
     * 从归档 `comic_source/` 目录里收集源 key。
     *
     * ⚠️ **文件名不是 key**，两个反例都在本仓的源目录里摆着：`komiic.js` 的 key 是
     * `Komiic`（大小写不同）、`copy_manga_multi_accounts.js` 的 key 是 `copy_manga`
     * （完全不同）。拿文件名当 key 会让这些源下的条目全部"来源未知"。
     *
     * 所以走两条路：
     * - `*.js`：读**内容**取顶层 `key = "..."`（见 [ForeignImportMapping.sourceKeyFromSourceFile]）；
     * - `*.data`：官方把这些源的账号数据存成 `<数据目录>/comic_source/<key>.data`，
     *   这一类的**文件名确实就是 key**，直接收下。
     */
    private fun sourceKeysInArchive(workDir: File): Set<String> {
        val keys = linkedSetOf<String>()
        val files = File(workDir, SOURCE_DIR).listFiles() ?: return keys
        for (file in files) {
            when (file.extension.lowercase()) {
                "js" -> {
                    val text = runCatching { file.readText() }.getOrNull() ?: continue
                    ForeignImportMapping.sourceKeyFromSourceFile(text)?.let { keys += it }
                }
                "data" -> {
                    val name = file.nameWithoutExtension
                    if (name.isNotBlank()) keys += name
                }
            }
        }
        return keys
    }

    /** PicaComic 的 `folder_sync.key` 是明文源 key，用它把自装源补进候选集合。 */
    private fun plainSourceKeysFromFolderSync(db: SqlDatabase): Set<String> {
        val keys = linkedSetOf<String>()
        if (!tableExists(db, "folder_sync")) return keys
        if (!columnsOf(db, "folder_sync").contains("key")) return keys
        db.query("SELECT key FROM folder_sync").forEach { c ->
            val key = ForeignImportMapping.normalizeSourceKey(c.optString("key"))
            if (key.isNotBlank()) keys += key
        }
        return keys
    }

    // endregion

    // region ---- 通用小工具 ----

    /**
     * `type` → sourceKey。
     *
     * PicaComic 先查小整数枚举（内置源），查不到再当作 Dart 哈希反查（自定义源）；
     * 官方 Venera 只有哈希一条路。两种都认不出就返回 null，由调用方跳过并计数。
     */
    private fun resolveSourceKey(
        type: Int,
        isPica: Boolean,
        forHistory: Boolean,
        hashIndex: Map<Int, String>,
    ): String? {
        if (isPica) {
            val byEnum = if (forHistory) {
                ForeignImportMapping.picaHistorySourceKey(type)
            } else {
                ForeignImportMapping.picaFavoriteSourceKey(type)
            }
            if (byEnum != null) return byEnum
        }
        return hashIndex[type]
    }

    private fun splitTags(raw: String): List<String> =
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    // endregion

    companion object {
        /** 官方 Venera / PicaComic 归档里的收藏库文件名（两边同名）。 */
        const val FAVORITE_DB = "local_favorite.db"

        /** 两边的历史库同名。 */
        const val HISTORY_DB = "history.db"

        private const val SOURCE_DIR = "comic_source"
        private const val HISTORY_TABLE = "history"

        private val META_TABLES =
            setOf("folder_order", "folder_sync", "android_metadata", "sqlite_sequence")

        /** 归档里出现这两个文件之一，就按外部归档处理（本应用自己的备份装的是 JSON）。 */
        fun isForeignArchiveEntry(name: String): Boolean =
            name == FAVORITE_DB || name == HISTORY_DB

        @Volatile
        private var INSTANCE: ForeignArchiveImport? = null

        fun getInstance(context: Context): ForeignArchiveImport {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ForeignArchiveImport(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
