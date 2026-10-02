package com.venera.compose.feature.favoriteimages

import android.content.Context
import android.graphics.Bitmap
import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.FavoriteImageBackupFields
import com.venera.compose.data.db.FavoriteImagesStore
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.db.ReadingStatsStore
import com.venera.compose.data.tags.TagNormalizer
import com.venera.compose.feature.ComicItem
import com.venera.compose.stats.ReadingStatsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 一条插图收藏。
 *
 * 刻意**不**拿它当导航参数：type-safe 导航只认内建类型，自定义对象（哪怕标了 `@Serializable`）
 * 得靠 safeargs 插件生成 NavType，本仓库没装 —— 真机实测直接崩在建图阶段
 * （`could not find any NavType for argument item ... typeMap received was {}`），
 * 表现为冷启动即闪。载荷放 `VeneraShellViewModel` 的表里，路由只带行 id。
 */
/**
 * 插图收藏在备份归档里的那一行。
 *
 * 只有七列，且**没有 `id` 与 `local_path`**：前者是设备本地的自增主键，后者指向的是
 * 那台机器上那份去混淆后的位图副本 —— 换台机器它就是条不存在的路径，而收藏墙和大图的
 * 取图口径是 `localPath.ifBlank { imageUrl }`（`FavoriteImagesScreen`）。把路径带上，
 * 等于亲手把"按 URL 现加载"这条唯一还能走的路堵死。落库时 `local_path` 写空串。
 */
data class ImageFavoriteBackupRow(
    val comicId: String,
    val comicTitle: String,
    val sourceName: String,
    val chapterTitle: String,
    val pageIndex: Int,
    val imageUrl: String,
    val createdAt: Long,
)

/**
 * 上面那行的**编解码判据**（纯 JVM，不碰 SQLite、不碰 org.json）。
 *
 * 单测没有 Robolectric，`FavoriteImagesManager` 那一半在 JVM 里根本跑不起来，而会
 * **静默出错**的恰好是这一层：列名对不上不抛异常，只会恢复出一批打不开的条目。
 */
internal object ImageFavoriteBackupRows {

    fun toMap(row: ImageFavoriteBackupRow): Map<String, Any?> = mapOf(
        "comic_id" to row.comicId,
        "comic_title" to row.comicTitle,
        "source_name" to row.sourceName,
        "chapter_title" to row.chapterTitle,
        "page_index" to row.pageIndex,
        "image_url" to row.imageUrl,
        "created_at" to row.createdAt,
    )

    /**
     * 归档行 → 落库行。地址为空（或整行缺 `image_url`）时返回 null：
     * 那样一行既加载不出图、也去不了重（`image_url` 就是这一路的身份键），
     * 进了库就是一行看不见也删不掉的记录。
     */
    fun fromMap(row: Map<String, Any?>): ImageFavoriteBackupRow? {
        val url = row.string("image_url")
        if (url.isBlank()) return null
        return ImageFavoriteBackupRow(
            comicId = row.string("comic_id"),
            comicTitle = row.string("comic_title"),
            sourceName = row.string("source_name"),
            chapterTitle = row.string("chapter_title"),
            pageIndex = row.int("page_index"),
            imageUrl = url,
            createdAt = row.long("created_at").takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
    }

    private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: ""

    private fun Map<String, Any?>.int(key: String): Int = long(key).toInt()

    private fun Map<String, Any?>.long(key: String): Long = when (val value = this[key]) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull() ?: 0L
        else -> 0L
    }
}

data class FavoriteImageItem(
    val id: Long,
    val comicId: String,
    val comicTitle: String,
    val sourceName: String,
    val chapterTitle: String,
    val pageIndex: Int,
    val imageUrl: String,
    val localPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * 作者名。`favorite_images` 表**没有这一列**，由 [FavoriteImagesManager.getAllFavorites]
     * 从本地真相反查填入（见该函数注释）；查不到时为空串，卡片上就不显示作者行。
     */
    val author: String = "",
)

/**
 * 收藏条目 → 详情页入口载荷。
 *
 * 封面与标签这条记录里没有，留空串即可：详情页本来就支持「只有 id + 源」的深链入参
 * （见 Navigation.kt 的 resolveDetailComic），真数据由它自己拉。带着标题与作者过去只是
 * 让标题区在详情返回前先有内容，不是伪造封面。
 */
fun FavoriteImageItem.toComicItem() = ComicItem(
    id = comicId,
    title = comicTitle,
    author = author,
    coverUrl = "",
    sourceName = sourceName,
)

/**
 * 单页/插图收藏管理器
 *
 * `favorite_images` 的 SQL 全在 [FavoriteImagesStore]（同 [com.venera.compose.data.db.ReadingStatsStore]
 * 的拆法：持久层脱开 Android 类型，`data/db` 才能进桌面编译面并被真库用例跑住）。
 * 这一层留下的是只有 Android 才有的事：`filesDir` 下的原画落盘、`Bitmap` 编码，
 * 以及把库里的行补成卡片要显示的作者。
 */
class FavoriteImagesManager private constructor(private val context: Context) {

    private val core = DatabasePorts.of(context).core
    private val store = FavoriteImagesStore(core)
    private val statsStore = ReadingStatsStore(core)

    suspend fun addFavorite(
        comicId: String,
        comicTitle: String,
        sourceName: String,
        chapterTitle: String,
        pageIndex: Int,
        imageUrl: String,
        localPath: String = ""
    ): Long = withContext(Dispatchers.IO) {
        try {
            store.insert(
                comicId = comicId,
                comicTitle = comicTitle,
                sourceName = sourceName,
                chapterTitle = chapterTitle,
                pageIndex = pageIndex,
                imageUrl = imageUrl,
                localPath = localPath,
                createdAt = System.currentTimeMillis(),
            )
        } catch (_: Exception) {
            // 写不下去回报 -1：调用方（阅读器）就是按这个数如实提示"没存进去"的。
            // 判据与改造前相同，只是改造前靠 SQLiteDatabase.insert 自己回 -1，现在是门面抛出后收口在这里。
            -1L
        }
    }

    /** 收藏原画的落盘目录（应用私有，随应用卸载）。 */
    private fun persistedDir(): File = File(context.filesDir, PERSIST_DIR_NAME)

    /**
     * 把**去混淆之后**的那一页原画存成文件，返回绝对路径；存不下返回 null。
     *
     * 为什么不能只存 URL：`favorite_images.image_url` 存的是源签发的地址，而
     * ① 禁漫那张图本身是横条混淆过的，收藏墙/灯箱直接按 URL 加载时走的是**小请求盒子**，
     * 还原质量受降采样影响；② EH 这类动态源解析出的地址带临时签名，几天后就 403；
     * ③ 离线时整面收藏墙全是裂图。存下解好的位图，这三条一起解决（方案里原本就写了
     * 「原画落盘持久化至 favorite_images/」，实现里漏了，只对本地漫画页成立）。
     */
    suspend fun persistPage(bitmap: Bitmap, comicId: String, pageIndex: Int): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = persistedDir().apply { mkdirs() }
                // comicId 只用于文件名可读性，一律消毒成 [字母数字-]：源 id 里出现 '/' 或 '..'
                // 就会把文件写到目录外，而 deletePersistedFile 只认这个目录内的文件。
                val safeId = comicId.map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
                val file = File(dir, "${System.currentTimeMillis()}_${safeId}_p$pageIndex.jpg")
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
                file.absolutePath
            }.getOrNull()
        }

    /**
     * 删掉收藏自己存的那份副本。
     *
     * 只删 [persistedDir] 里的文件：本地漫画页的 `local_path` 指向的是**漫画本体**
     * （见 VeneraReaderScreen 收藏处对 LocalFile 的处理），取消收藏不能把用户的书删了。
     */
    private fun deletePersistedFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            if (file.parentFile?.absolutePath == persistedDir().absolutePath) file.delete()
        }
    }

    suspend fun removeFavorite(id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            // 先读路径再删行：行一没就再没人知道那个文件存在过（泄漏在 filesDir 里）。
            val path = store.localPathOf(id)
            val removed = store.deleteById(id)
            if (removed) deletePersistedFile(path)
            removed
        } catch (e: Exception) {
            android.util.Log.e("FavoriteImages", "removeFavorite(id=$id) failed", e)
            false
        }
    }

    /**
     * 批量取消收藏。语义与 [removeFavorite] 逐条一致：**先读路径、再删行**，
     * 且只删 [persistedDir] 内的副本（本地漫画页的 `local_path` 指向的是书本体，
     * 取消收藏不能把用户的书删了）。
     *
     * 一次 SQL 删完，不在 UI 侧循环调单条版 —— 几十张图就是几十次事务。
     * 返回真正删掉的行数：调用方拿 0 要如实提示，不能报"已移除"。
     */
    suspend fun removeFavorites(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext 0
        try {
            val paths = store.localPathsOf(ids)
            val removed = store.deleteByIds(ids)
            if (removed > 0) paths.forEach { deletePersistedFile(it) }
            removed
        } catch (e: Exception) {
            android.util.Log.e("FavoriteImages", "removeFavorites(n=${ids.size}) failed", e)
            0
        }
    }

    suspend fun isFavorited(imageUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            store.isFavorited(imageUrl)
        } catch (_: Exception) {
            // 读不出就报"未收藏"：这颗心只是按钮上的一个初始状态，下一帧重进页面就会再问一次。
            // 与改造前一致（改造前也是 catch → false），这里不升级成抛，因为打不开图墙的代价更大。
            false
        }
    }

    /**
     * 导出备份用的那几列。刻意不用 `SELECT *`：`id` 和 `local_path` 是本地状态，
     * 见 [ImageFavoriteBackupRow] 的说明。
     */
    suspend fun exportBackupRows(): List<ImageFavoriteBackupRow> = withContext(Dispatchers.IO) {
        store.backupFields().map {
            ImageFavoriteBackupRow(
                comicId = it.comicId,
                comicTitle = it.comicTitle,
                sourceName = it.sourceName,
                chapterTitle = it.chapterTitle,
                pageIndex = it.pageIndex,
                imageUrl = it.imageUrl,
                createdAt = it.createdAt,
            )
        }
    }

    /**
     * 恢复插图收藏（备份导入），返回**真正新增**的条数。
     *
     * `local_path` 一律写空串，让收藏墙按 `image_url` 现加载（`ifBlank` 那条回落）——
     * 归档里本来就没有那份位图。`created_at` 用归档里的原值，否则恢复完整个收藏墙的顺序
     * 会变成"导入的这批全在最前"。
     *
     * 去重键是 `image_url`，与 [isFavorited] 用的同一个：同一页被重复导入会在墙上摆两张，
     * 而大图页的心形按钮按地址判定"在不在收藏里"，那时它对两张都显示已收藏，删一张另一张还在。
     *
     * 落库那一步不在这里吞异常：整笔事务里任何一行写不下去就抛出（判据与改造前的
     * "insert 返回 -1 ⇒ 手工抛 SQLException"相同），调用方（`BackupManager`）据此把这次导入
     * 如实报成失败，而不是"报了成功但少几条"。
     */
    suspend fun restoreBackupRows(rows: List<ImageFavoriteBackupRow>): Int = withContext(Dispatchers.IO) {
        store.restoreBackupFields(
            rows.map {
                FavoriteImageBackupFields(
                    comicId = it.comicId,
                    comicTitle = it.comicTitle,
                    sourceName = it.sourceName,
                    chapterTitle = it.chapterTitle,
                    pageIndex = it.pageIndex,
                    imageUrl = it.imageUrl,
                    createdAt = it.createdAt,
                )
            }
        )
    }

    /**
     * 列出全部插图收藏，并给每条补上作者名。
     *
     * 作者为什么不在表里：`favorite_images` 建表时没存 author，而 `comic_history.author`
     * 恒写空串（见 VeneraReaderScreen 的历史写回），两处都指望不上。所以按 comic_id
     * 从下面两处本地真相反查，顺序即优先级：
     *  1. **本地收藏**：`author` 字段优先，为空再回落作者类标签（`Author:` / `作者:` …）；
     *  2. **reading_stats.tags**：阅读时落下的源生标签，覆盖「读过但没收藏这本」的情况。
     * 两边都没有的行留空串 —— 卡片上不显示作者行，不拿源名或空占位糊弄。
     */
    suspend fun getAllFavorites(): List<FavoriteImageItem> = withContext(Dispatchers.IO) {
        val authors = authorIndex()
        val list = mutableListOf<FavoriteImageItem>()
        try {
            list += store.allRows().map { row ->
                FavoriteImageItem(
                    id = row.id,
                    comicId = row.comicId,
                    comicTitle = row.comicTitle,
                    sourceName = row.sourceName,
                    chapterTitle = row.chapterTitle,
                    pageIndex = row.pageIndex,
                    imageUrl = row.imageUrl,
                    localPath = row.localPath,
                    createdAt = row.createdAt,
                    author = authors[row.comicId] ?: "",
                )
            }
        } catch (e: Exception) {
            // 与改造前一致：整表读不出来就交已拿到的部分（此时是空表），页面自己提示加载失败。
            // 换成抛出会让收藏墙在库坏了时直接崩，而那本来只是"这一屏没有图"。
            android.util.Log.e("FavoriteImages", "getAllFavorites failed", e)
        }
        list
    }

    /** comic_id → 作者名。本地收藏优先于阅读统计。 */
    private suspend fun authorIndex(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        // 先铺覆盖面更广的阅读统计，再让本地收藏覆盖它（author 字段更权威）。
        try {
            statsStore.comicTagPairs().forEach { pair ->
                val id = pair.comicId.takeIf { it.isNotBlank() } ?: return@forEach
                if (out.containsKey(id)) return@forEach
                val tags = pair.tags
                    .split(ReadingStatsManager.TAG_SEPARATOR)
                    .filter { it.isNotBlank() }
                TagNormalizer.resolveAuthor("", tags)?.let { out[id] = it }
            }
        } catch (e: Exception) {
            android.util.Log.w("FavoriteImages", "作者反查跳过阅读统计这一路", e)
        }
        // 用 runCatching：本地收藏表是按收藏夹分表的动态结构，建表前调用会抛，
        // 这里失败只意味着少一路作者来源，不该让整张插图列表打不开。
        runCatching {
            LocalFavoritesManager.getInstance(context).getAllComics().forEach { fav ->
                TagNormalizer.resolveAuthor(fav.author, fav.tags)?.let { out[fav.id] = it }
            }
        }
        return out
    }

    companion object {
        /** filesDir 下的收藏原画目录名（当初定的就是这个名字）。 */
        private const val PERSIST_DIR_NAME = "favorite_images"

        @Volatile
        private var INSTANCE: FavoriteImagesManager? = null

        fun getInstance(context: Context): FavoriteImagesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FavoriteImagesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
