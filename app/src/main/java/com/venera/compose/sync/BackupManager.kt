package com.venera.compose.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.db.VeneraDatabase
import com.venera.compose.feature.favoriteimages.FavoriteImagesManager
import com.venera.compose.feature.favoriteimages.ImageFavoriteBackupRows
import com.venera.compose.gallery.data.GalleryArtistFollowsStore
import com.venera.compose.gallery.data.GalleryFavoritesStore
import com.venera.compose.security.guard.GuardRulePattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 本地数据备份与恢复服务
 *
 * 核心特性：
 * 1. 一键全量打包导出为标准 `.venera` 归档文件
 * 2. 跨设备双向还原
 *
 * ## 一份归档里有什么（9 个 member，格式版本 5）
 *
 * | member | 内容 | 事实源 |
 * | --- | --- | --- |
 * | `meta.json` | 格式版本、导出时刻、各类条数 | —— |
 * | `history.json` | 阅读历史与已读进度 | `comic_history` |
 * | `favorites.json` | 漫画收藏条目 | `local_favorite.db`（一夹一表） |
 * | `favorite_folders.json` | 收藏夹清单、顺序、网络夹绑定 | 同上 |
 * | `stats.json` | 阅读统计（时长 / 页数 / 日期） | `reading_stats` |
 * | `guard_rules.json` | 屏蔽规则 | `content_guard_rules` |
 * | `gallery_follows.json` | 画廊关注画师名单 | `filesDir/gallery_artist_follows.json` |
 * | `gallery_favorites.json` | 画廊收藏（含地址与尺寸，离线也能铺出收藏页） | `filesDir/gallery_favorites.json` |
 * | `image_favorites.json` | 插图收藏的**元数据 + 地址** | `favorite_images` |
 *
 * 统计页与首页摘要卡上的每个读数都是从 `reading_stats` 现算的，所以这张表进包 = 整页统计复现。
 *
 * ## 仍然不进包的东西，以及各自真实的理由
 *
 * - **插图收藏的那份图片文件**：包里只带元数据与 `image_url`，恢复后按地址现加载。
 *   不带图是因为一份归档要能分享出去，而整页原图按收藏张数线性膨胀。真实代价明写着：
 *   禁漫那批画质受降采样影响、EH 那类动态源解析出的地址带临时签名（几天后 403）、离线时是裂图。
 *   被排除的是**图片字节**与那条指向它的 `local_path`，不是整行记录。
 * - **画师头像地址档**（`gallery_artist_avatars.json`）：可随时重取的解析结果，不是用户攒下的关系。
 *   代价是新设备第一次打开关注面板要为一位画师重发 2~4 笔 JSON。
 * - **画廊的 Feed 缓存与标签词典**：都可再生。
 * - `comic_source`（已安装源）、偏好设置、Cookie 登录态、下载任务与下载下来的图片。
 *
 * **收藏这一路必须走 [LocalFavoritesManager]，不能直接写 SQL。**
 * 早期版本读写的是 `venera_core.db` 里的单表 `comic_favorite`，而那张表在
 * `LocalFavoritesManager.migrateLegacyFavorites()` 迁移完就被清空、运行期再没有写入点，
 * 于是"导出收藏"恒导出 0 条、"恢复收藏"写进一张没有任何 UI 会读的表 —— 全程不报错，
 * 用户看到的就是"备份里怎么一本收藏都没有""恢复了但收藏页还是空的"。
 * 走 Manager 还有第二个必要性：它每次写完会 `notifyChanged()` 刷新 `folders`/`counts` 缓存，
 * 直接写库的话恢复出来的收藏要等重启才看得见 —— 那只是把"看不见"往后推了一格。
 *
 * 跨库的原子性限制（是结构不是遗漏）：收藏在 `local_favorite.db`，历史/统计/屏蔽/插图在
 * `venera_core.db`，画廊那两份是 `filesDir` 下的 JSON 档 —— 三者不可能共用一个事务。所以先把整包
 * 解析进内存（格式不认识就在**动任何一处存储之前**失败），再分四段写：核心库一笔事务 →
 * 收藏与夹子（另一库）→ 插图收藏（核心库第二笔，同库嵌套事务会抛）→ 画廊两块。
 * 中途失败时重跑一次导入即可补齐 —— [LocalFavoritesManager.addComic] 对已存在的条目返回 false，
 * 画廊与插图那三路也各自按身份键去重，都不会写重。
 *
 * **画廊那两块也必须走 store 单例，理由与上面同一条、只是换了一层。**
 * `GalleryFavoritesStore` / `GalleryArtistFollowsStore` 的内存 `StateFlow` 才是收藏页与
 * 首页那排入口运行期读的东西，直接改 `filesDir` 上那份 JSON 会让「导入报了成功」和
 * 「画廊还是空的」同时成立 —— 值确实进盘了，只是没人再读盘。插图收藏同理走
 * [FavoriteImagesManager]，它的去重键（`image_url`）也是在这一层判的。
 */
class BackupManager private constructor(private val context: Context) {

    private val tag = "BackupManager"
    private val dbHelper = VeneraDatabase.getInstance(context)

    /** 懒取：`getInstance` 会打开数据库并触发旧表迁移，构造 BackupManager 时不该发生这些。 */
    private val favoritesManager by lazy { LocalFavoritesManager.getInstance(context) }

    /** 同上：画廊那两个 store 构造时会同步读一次自己的 JSON 档。 */
    private val galleryFollows by lazy { GalleryArtistFollowsStore.getInstance(context) }
    private val galleryFavorites by lazy { GalleryFavoritesStore.getInstance(context) }
    private val favoriteImagesManager by lazy { FavoriteImagesManager.getInstance(context) }

    /**
     * 导出全量备份为标准 ZIP / .venera 文件
     */
    suspend fun exportBackup(targetFile: File? = null): Result<File> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase
            val outDir = File(context.cacheDir, "backups").apply { if (!exists()) mkdirs() }
            val timestamp = System.currentTimeMillis()
            val backupFile = targetFile ?: File(outDir, "venera_backup_$timestamp.venera")
            if (backupFile.exists()) backupFile.delete()

            val historyJson = exportTableToJson(db, "comic_history")
            val statsJson = exportTableToJson(db, "reading_stats")
            val guardJson = exportTableToJson(db, "content_guard_rules")
            val favoriteFoldersJson = exportFavoriteFolders()
            val favoriteJson = exportFavoriteItems()
            val imageFavoritesJson = exportImageFavorites()
            // 一次取快照：打包期间用户又点了颗心，不该让 meta 的计数与包里的内容对不上。
            val followsSnapshot = galleryFollows.follows.value
            val galleryFavoritesSnapshot = galleryFavorites.favorites.value

            val metaJson = JSONObject().apply {
                put("version", FavoriteBackupRows.CURRENT_VERSION)
                put("timestamp", timestamp)
                put("app", "Venera Compose")
                put("historyCount", historyJson.length())
                put("favoriteCount", favoriteJson.length())
                put("favoriteFolderCount", favoriteFoldersJson.length())
                put("statsCount", statsJson.length())
                put("guardCount", guardJson.length())
                put("imageFavoriteCount", imageFavoritesJson.length())
                put("galleryFollowCount", followsSnapshot.size)
                put("galleryFavoriteCount", galleryFavoritesSnapshot.size)
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile))).use { zos ->
                addJsonEntry(zos, "meta.json", metaJson.toString())
                addJsonEntry(zos, "history.json", historyJson.toString())
                addJsonEntry(zos, FAVORITE_ENTRIES_JSON, favoriteJson.toString())
                addJsonEntry(zos, FAVORITE_FOLDERS_JSON, favoriteFoldersJson.toString())
                addJsonEntry(zos, "stats.json", statsJson.toString())
                addJsonEntry(zos, "guard_rules.json", guardJson.toString())
                addJsonEntry(zos, IMAGE_FAVORITES_JSON, imageFavoritesJson.toString())
                addJsonEntry(zos, GALLERY_FOLLOWS_JSON, GalleryBackupRows.encodeFollows(followsSnapshot))
                addJsonEntry(zos, GALLERY_FAVORITES_JSON, GalleryBackupRows.encodeFavorites(galleryFavoritesSnapshot))
            }

            Result.success(backupFile)
        } catch (e: Exception) {
            Log.e(tag, "exportBackup failed", e)
            Result.failure(e)
        }
    }

    /**
     * 导入并恢复备份数据
     */
    suspend fun importBackup(backupFile: File): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            if (!backupFile.exists()) return@withContext Result.failure(Exception("备份文件不存在"))

            val zip = ZipFile(backupFile)
            // 先把整包解析进内存再动库：格式问题必须在这一阶段暴露，
            // 否则半路抛在写入过程中，就成了"历史已经进库、收藏没进"的半截状态。
            val history = readArray(zip, "history.json")
            val stats = readArray(zip, "stats.json")
            val guardRules = readArray(zip, "guard_rules.json")
            val favoriteFolders = readArray(zip, FAVORITE_FOLDERS_JSON)
            // v3 及更早的归档里这一栏叫 favorite.json，行是旧单表的列名；
            // FavoriteBackupRows.decode 认得它，所以老备份照样导得进来。
            val favorites = readArray(zip, FAVORITE_ENTRIES_JSON)
                .let { if (it.length() > 0) it else readArray(zip, "favorite.json") }
            val imageFavoriteRows = readArray(zip, IMAGE_FAVORITES_JSON)
            // 画廊那两份：归档格式与 filesDir 上的磁盘格式是同一套定义（见 GalleryBackupRows），
            // 所以这里交原文、不转 JSONArray —— 少一层列名映射就少一处能各自漂移的地方。
            val galleryFollowsJson = readText(zip, GALLERY_FOLLOWS_JSON)
            val galleryFavoritesJson = readText(zip, GALLERY_FAVORITES_JSON)
            val timestamp = readTimestamp(zip)
            zip.close()

            // 解析全部留在动库之前：画廊那两份解不开就抛，一笔库都不会被写过。
            val incomingFollows = GalleryBackupRows.decodeFollows(galleryFollowsJson)
            val incomingGalleryFavorites = GalleryBackupRows.decodeFavorites(galleryFavoritesJson)
            val incomingImageFavorites = buildList {
                for (i in 0 until imageFavoriteRows.length()) {
                    ImageFavoriteBackupRows.fromMap(asMap(imageFavoriteRows.getJSONObject(i)))?.let { add(it) }
                }
            }

            var historyCount = 0
            var statsCount = 0
            var guardCount = 0
            var guardSkipped = 0
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                // 1. 恢复阅读历史
                for (i in 0 until history.length()) {
                    val item = history.getJSONObject(i)
                    val cv = ContentValues().apply {
                        put("comic_id", item.getString("comic_id"))
                        put("title", item.getString("title"))
                        put("author", item.optString("author"))
                        put("cover_url", item.getString("cover_url"))
                        put("source_name", item.getString("source_name"))
                        put("last_chapter_title", item.getString("last_chapter_title"))
                        put("last_chapter_index", item.getInt("last_chapter_index"))
                        put("last_page_index", item.getInt("last_page_index"))
                        put("total_pages", item.getInt("total_pages"))
                        put("updated_at", item.getLong("updated_at"))
                    }
                    db.insertWithOnConflict("comic_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    historyCount++
                }

                // 2. 恢复阅读统计
                for (i in 0 until stats.length()) {
                    val item = stats.getJSONObject(i)
                    val cv = ContentValues().apply {
                        put("comic_id", item.getString("comic_id"))
                        put("comic_title", item.getString("comic_title"))
                        put("source_name", item.getString("source_name"))
                        put("tags", item.optString("tags", ""))
                        put("chapter_title", item.getString("chapter_title"))
                        put("pages_read", item.getInt("pages_read"))
                        put("duration_seconds", item.getInt("duration_seconds"))
                        put("read_date", item.getString("read_date"))
                        put("created_at", item.getLong("created_at"))
                    }
                    db.insert("reading_stats", null, cv)
                    statsCount++
                }

                // 3. 恢复屏蔽规则
                for (i in 0 until guardRules.length()) {
                    val item = guardRules.getJSONObject(i)
                    val regexFlag = item.optInt("is_regex", 0)
                    val pattern = item.getString("pattern")
                    if (regexFlag == 1 && !GuardRulePattern.compiles(pattern)) {
                        // 编译不过的正则进了库也命中不了任何东西（ContentGuardManager 的 match() 兜底是
                        // catch → false），却会在屏蔽列表里显示「已启用 · 正则规则」。
                        // 恢复它 = 恢复一条假开关，所以不收，收多少条要在屏上说得出。
                        guardSkipped++
                        continue
                    }
                    val cv = ContentValues().apply {
                        put("rule_type", item.getString("rule_type"))
                        put("pattern", pattern)
                        put("is_regex", regexFlag)
                        put("is_enabled", item.optInt("is_enabled", 1))
                        put("created_at", item.getLong("created_at"))
                    }
                    db.insert("content_guard_rules", null, cv)
                    guardCount++
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            // 4. 恢复本地收藏（另一个 db 文件，单独一笔）
            val folderCount = restoreFavoriteFolders(favoriteFolders)
            val favoriteCount = restoreFavoriteItems(favorites)
            // 5. 插图收藏（回到 venera_core.db，但必须在上面那笔事务结束之后 —— 同一库里嵌套事务会抛）
            val imageFavoriteCount = favoriteImagesManager.restoreBackupRows(incomingImageFavorites)
            // 6. 画廊两块：写 store 单例，收藏页与首页那排入口当帧就看见，不需要重启
            val galleryFollowCount = galleryFollows.restore(incomingFollows)
            val galleryFavoriteCount = galleryFavorites.restore(incomingGalleryFavorites)
            Log.i(
                tag,
                "导入完成：$folderCount 个收藏夹 / $favoriteCount 本漫画收藏 / $imageFavoriteCount 张插图收藏 / " +
                    "$galleryFavoriteCount 条画廊收藏 / $galleryFollowCount 位关注画师 / " +
                    "跳过 $guardSkipped 条写法有误的屏蔽规则",
            )

            Result.success(
                BackupSummary(
                    historyCount = historyCount,
                    favoriteCount = favoriteCount,
                    statsCount = statsCount,
                    guardRulesCount = guardCount,
                    guardRulesSkipped = guardSkipped,
                    timestamp = timestamp,
                    folderCount = folderCount,
                    imageFavoriteCount = imageFavoriteCount,
                    galleryFavoriteCount = galleryFavoriteCount,
                    galleryFollowCount = galleryFollowCount,
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "importBackup failed", e)
            Result.failure(e)
        }
    }

    // region ---- 收藏 ----

    private suspend fun exportFavoriteFolders(): JSONArray {
        val syncByFolder = favoritesManager.getFolderSync().associateBy { it.folder }
        val out = JSONArray()
        // currentFolders() 已按 folder_order 排好，导入侧就按数组顺序还原
        for (folder in favoritesManager.currentFolders()) {
            val sync = syncByFolder[folder]
            out.put(
                JSONObject(
                    mapOf(
                        "folder" to folder,
                        "sourceKey" to sync?.sourceKey.orEmpty(),
                        "sourceFolder" to sync?.sourceFolder.orEmpty(),
                    )
                )
            )
        }
        return out
    }

    private suspend fun exportFavoriteItems(): JSONArray {
        val out = JSONArray()
        for (folder in favoritesManager.currentFolders()) {
            for (item in favoritesManager.getFolderComics(folder)) {
                out.put(JSONObject(FavoriteBackupRows.encode(item, folder)))
            }
        }
        return out
    }

    /**
     * 插图收藏那一栏。走 [FavoriteImagesManager] 而不是 `exportTableToJson(db, "favorite_images")`：
     * 后者是 `SELECT *`，会把 `id` 与 `local_path` 一起打进包 —— 前者是本地自增主键，后者换台机器
     * 就指向一个不存在的文件，而收藏墙的取图口径是「路径为空才按地址加载」。
     */
    private suspend fun exportImageFavorites(): JSONArray {
        val out = JSONArray()
        for (row in favoriteImagesManager.exportBackupRows()) {
            out.put(JSONObject(ImageFavoriteBackupRows.toMap(row)))
        }
        return out
    }

    /**
     * 建收藏夹 + 恢复顺序 + 恢复网络夹绑定。返回恢复到的收藏夹数量。
     *
     * 顺序是「备份里的夹子在前，本机原有而这份备份里没有的夹子接在后面」：导入是**合并**，
     * 不该把本机已有的夹子挤到未知位置去。
     */
    private suspend fun restoreFavoriteFolders(folders: JSONArray): Int = withContext(Dispatchers.IO) {
        if (folders.length() == 0) return@withContext 0
        val restored = mutableListOf<String>()
        for (i in 0 until folders.length()) {
            val row = folders.getJSONObject(i)
            val name = row.optString("folder").trim()
            if (name.isBlank() || name in restored) continue
            if (!favoritesManager.existsFolder(name)) {
                // renameWhenInvalidName 保持默认 false：夹名是用户起的，静默改名会让
                // "这一本在哪个夹子里"和备份对不上号。
                favoritesManager.createFolder(name)
            }
            val sourceKey = row.optString("sourceKey")
            val sourceFolder = row.optString("sourceFolder")
            if (sourceKey.isNotBlank() && sourceFolder.isNotBlank()) {
                favoritesManager.linkFolderToNetwork(name, sourceKey, sourceFolder)
            }
            restored += name
        }
        val localOnly = favoritesManager.currentFolders().filterNot { it in restored }
        favoritesManager.updateOrder(restored + localOnly)
        restored.size
    }

    /** 逐条写回收藏，返回**实际新增**的条数（本机已有的不重复写，也不计入）。 */
    private suspend fun restoreFavoriteItems(items: JSONArray): Int = withContext(Dispatchers.IO) {
        if (items.length() == 0) return@withContext 0
        val knownFolders = favoritesManager.currentFolders().toHashSet()
        var added = 0
        for (i in 0 until items.length()) {
            val row = FavoriteBackupRows.decode(asMap(items.getJSONObject(i)))
            // 没有 id 的行落库就是一行看不见也删不掉的垃圾（主键是 id + type），直接跳掉。
            if (row.item.id.isBlank()) continue
            if (row.folder !in knownFolders) {
                favoritesManager.createFolder(row.folder)
                knownFolders += row.folder
            }
            if (favoritesManager.addComic(row.folder, row.item, row.order)) added++
        }
        added
    }

    // endregion

    // region ---- 通用 JSON / 归档读写 ----

    private fun exportTableToJson(db: SQLiteDatabase, tableName: String): JSONArray {
        val array = JSONArray()
        val cursor = db.rawQuery("SELECT * FROM $tableName", null)
        cursor.use {
            val colNames = it.columnNames
            while (it.moveToNext()) {
                val obj = JSONObject()
                for (name in colNames) {
                    val idx = it.getColumnIndex(name)
                    when (it.getType(idx)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> obj.put(name, it.getLong(idx))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> obj.put(name, it.getDouble(idx))
                        android.database.Cursor.FIELD_TYPE_STRING -> obj.put(name, it.getString(idx))
                        android.database.Cursor.FIELD_TYPE_NULL -> obj.put(name, JSONObject.NULL)
                        else -> obj.put(name, it.getString(idx))
                    }
                }
                array.put(obj)
            }
        }
        return array
    }

    private fun readArray(zip: ZipFile, entryName: String): JSONArray {
        val entry = zip.getEntry(entryName) ?: return JSONArray()
        return zip.getInputStream(entry).bufferedReader().use { JSONArray(it.readText()) }
    }

    /** member 不存在交回 null（v4 及更早的归档就是没有这一栏），与"存在但内容坏掉"是两件事。 */
    private fun readText(zip: ZipFile, entryName: String): String? {
        val entry = zip.getEntry(entryName) ?: return null
        return zip.getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun readTimestamp(zip: ZipFile): Long {
        val entry = zip.getEntry("meta.json") ?: return System.currentTimeMillis()
        val meta = runCatching {
            zip.getInputStream(entry).bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull() ?: return System.currentTimeMillis()
        return meta.optLong("timestamp", System.currentTimeMillis())
    }

    private fun addJsonEntry(zos: ZipOutputStream, entryName: String, jsonContent: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(jsonContent.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    /**
     * `JSONObject` → 普通 Map。
     *
     * 为什么要绕这一层：单元测试没有 Robolectric，android 的 `org.json` 在 JVM 测试里
     * 全是返回默认值的桩，解析逻辑一沾 JSONObject 就没法测。所以解析完立刻转 Map，
     * 字段判据全留在 [FavoriteBackupRows] 那种纯函数里。
     */
    private fun asMap(obj: JSONObject): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>(obj.length())
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = obj.get(key).takeIf { it !== JSONObject.NULL }
        }
        return map
    }

    // endregion

    companion object {
        private const val FAVORITE_ENTRIES_JSON = "favorites.json"
        private const val FAVORITE_FOLDERS_JSON = "favorite_folders.json"
        private const val IMAGE_FAVORITES_JSON = "image_favorites.json"
        private const val GALLERY_FOLLOWS_JSON = "gallery_follows.json"
        private const val GALLERY_FAVORITES_JSON = "gallery_favorites.json"

        @Volatile
        private var INSTANCE: BackupManager? = null

        fun getInstance(context: Context): BackupManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackupManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
