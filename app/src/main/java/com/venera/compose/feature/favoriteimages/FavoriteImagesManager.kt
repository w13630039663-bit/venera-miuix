package com.venera.compose.feature.favoriteimages

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.database.sqlite.SQLiteDatabase
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.db.VeneraDatabase
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
 * 单页/插图收藏管理器 (S7)
 */
class FavoriteImagesManager private constructor(private val context: Context) {

    private val dbHelper = VeneraDatabase.getInstance(context)

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
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("comic_id", comicId)
                put("comic_title", comicTitle)
                put("source_name", sourceName)
                put("chapter_title", chapterTitle)
                put("page_index", pageIndex)
                put("image_url", imageUrl)
                put("local_path", localPath)
                put("created_at", System.currentTimeMillis())
            }
            db.insert("favorite_images", null, cv)
        } catch (_: Exception) {
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
     * ③ 离线时整面收藏墙全是裂图。存下解好的位图，这三条一起解决（S7 方案原本就写了
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
            val db = dbHelper.writableDatabase
            // 先读路径再删行：行一没就再没人知道那个文件存在过（泄漏在 filesDir 里）。
            val path = runCatching {
                db.rawQuery(
                    "SELECT local_path FROM favorite_images WHERE id = ?",
                    arrayOf(id.toString()),
                ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            val removed = db.delete("favorite_images", "id = ?", arrayOf(id.toString())) > 0
            if (removed) deletePersistedFile(path)
            removed
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isFavorited(imageUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery("SELECT id FROM favorite_images WHERE image_url = ? LIMIT 1", arrayOf(imageUrl))
            val exists = cursor.moveToFirst()
            cursor.close()
            exists
        } catch (_: Exception) {
            false
        }
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
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM favorite_images ORDER BY created_at DESC", null)
            cursor.use {
                val idIdx = it.getColumnIndex("id")
                val cidIdx = it.getColumnIndex("comic_id")
                val titleIdx = it.getColumnIndex("comic_title")
                val srcIdx = it.getColumnIndex("source_name")
                val chIdx = it.getColumnIndex("chapter_title")
                val pageIdx = it.getColumnIndex("page_index")
                val urlIdx = it.getColumnIndex("image_url")
                val pathIdx = it.getColumnIndex("local_path")
                val timeIdx = it.getColumnIndex("created_at")

                while (it.moveToNext()) {
                    val comicId = it.getString(cidIdx) ?: ""
                    list.add(
                        FavoriteImageItem(
                            id = it.getLong(idIdx),
                            comicId = comicId,
                            comicTitle = it.getString(titleIdx),
                            sourceName = it.getString(srcIdx),
                            chapterTitle = it.getString(chIdx),
                            pageIndex = it.getInt(pageIdx),
                            imageUrl = it.getString(urlIdx),
                            localPath = it.getString(pathIdx) ?: "",
                            createdAt = it.getLong(timeIdx),
                            author = authors[comicId] ?: "",
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        list
    }

    /** comic_id → 作者名。本地收藏优先于阅读统计。 */
    private suspend fun authorIndex(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        // 先铺覆盖面更广的阅读统计，再让本地收藏覆盖它（author 字段更权威）。
        try {
            dbHelper.readableDatabase
                .rawQuery("SELECT comic_id, tags FROM reading_stats WHERE tags != ''", null)
                .use { c ->
                    val idIdx = c.getColumnIndex("comic_id")
                    val tagIdx = c.getColumnIndex("tags")
                    while (c.moveToNext()) {
                        val id = c.getString(idIdx)?.takeIf { it.isNotBlank() } ?: continue
                        if (out.containsKey(id)) continue
                        val tags = (c.getString(tagIdx) ?: "")
                            .split(ReadingStatsManager.TAG_SEPARATOR)
                            .filter { it.isNotBlank() }
                        TagNormalizer.resolveAuthor("", tags)?.let { out[id] = it }
                    }
                }
        } catch (_: Exception) {}
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
        /** filesDir 下的收藏原画目录名（S7 方案里写的就是这个名字）。 */
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
