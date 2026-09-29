package com.venera.compose.gallery.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.venera.compose.gallery.domain.GalleryTagCategory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 画廊那一份离线标签表（`assets/gallery_tags_*.sqlite`）的读取层。
 *
 * 数据来自 `ffdkj/…Danbooru_Tag-Chinese-English-Translation-Table`（MIT，每日更新），
 * 构建期由 `scripts/build_tag_dictionaries.mjs` 从它的 `tag.sqlite` 截出
 * `post_count >= 100` 且"有中文译名 **或** 是画师档"的那些行，两件事一起喂：
 *
 * - [artistNames]：「关于这张图」在**站方 HTML 分类取不到时**的画师兜底（方案 §1.4）；
 * - [translations]：标签药丸的中文显示名（方案 §三）。
 *
 * 为什么是 SQLite 而不是一份 JSON 词表：这张表 79,415 行，全量灌进 HashMap 是十几 MB 常驻堆，
 * 而它服务的场景是"用户打开了那一面板"——每回只查屏幕上那二三十枚。
 * SQLite 的代价是盘上 3.1 MB（APK 内 deflate 后约 1.6 MB）+ 每次几条主键查，常驻只有页缓存。
 *
 * ⚠️ Android 的 SQLite **要真实路径**，不能直接开 assets —— 所以首次使用把资产复制到 `databases/`。
 * 副本文件名**就是资产文件名**（里面带行数）：数据换一批 → 行数变 → 文件名变 → 重新复制。
 * 若只按"存在就不复制"，刷了词典的包会继续读旧副本，而且看不出来。
 */
class GalleryTagDictionary private constructor(context: Context) {

    private val appContext = context.applicationContext

    /** 复制+打开的只此一次：双检由 [lock] 兜，失败后不重试到无限（[openFailed] 记住这次进程的结果）。 */
    private val lock = Any()

    @Volatile
    private var database: SQLiteDatabase? = null

    @Volatile
    private var openFailed = false

    private fun openDatabase(): SQLiteDatabase? {
        database?.let { return it }
        if (openFailed) return null
        synchronized(lock) {
            database?.let { return it }
            if (openFailed) return null
            val opened = runCatching {
                // 按名字里的**行数**取最新那一份，不做字典序比较：
                // "gallery_tags_79415" 字典序大于 "gallery_tags_100000"，而它其实更旧。
                val assetName = appContext.assets.list("")
                    ?.filter { it.startsWith(ASSET_PREFIX) && it.endsWith(".sqlite") }
                    ?.maxByOrNull { it.rowCountInName() ?: -1 }
                    ?: error("没有 $ASSET_PREFIX*.sqlite 资产（跑 scripts/build_tag_dictionaries.mjs 生成）")
                val target = appContext.getDatabasePath(assetName)
                target.parentFile?.mkdirs()
                if (!target.exists()) {
                    // 先落临时文件再改名：复制一半被杀会留下一个截断的库，
                    // 而那种库能"打开成功"并在查询时炸，比直接没有更难查。
                    val temp = File(target.parentFile, "${target.name}.tmp")
                    appContext.assets.open(assetName).use { input ->
                        temp.outputStream().use { to -> input.copyTo(to) }
                    }
                    if (!temp.renameTo(target)) {
                        temp.delete()
                        error("复制 $assetName 失败")
                    }
                }
                SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
            }.onFailure {
                // 这条必须响：静默返回 null 的表现是"画师栏永远不出、标签永远没译文"，
                // 和"词典就是没有这些词"在屏上一模一样。
                android.util.Log.w(TAG, "打不开画廊标签词典，分桶兜底与标签译文都不可用", it)
                openFailed = true
            }.getOrNull()
            database = opened
            return opened
        }
    }

    /**
     * 这批名字里哪些是**画师档**。只回站方表里明确 `category = 1` 的那些。
     *
     * 实测它对两站的画师档**误报 0 例**（Gelbooru 253 枚 / yande.re 56 枚真值），
     * 漏的方向是"这一栏不摆"（yande.re 上 7 枚漏 2 枚）—— 所以它可以当兜底，
     * 而它的**其它**档位不能当兜底（名称覆盖只有 73%，见 `GalleryTagBuckets` 的说明）。
     */
    suspend fun artistNames(names: List<String>): Set<String> =
        query(names) { (name, row) -> if (row.getInt(1) == GalleryTagCategory.ARTIST) name else null }
            ?.toSet() ?: emptySet()

    /** 这批名字的中文译名；**没有译名的键不出现**在结果里（调用方据此原样显示）。 */
    suspend fun translations(names: List<String>): Map<String, String> =
        query(names) { (name, row) -> row.getString(2)?.takeIf { it.isNotBlank() }?.let { name to it } }
            ?.toMap() ?: emptyMap()

    /**
     * 一次 `name IN (…) ?` 的主键查。
     *
     * 占位符逐位传参，**不把标签名拼进 SQL**：标签名是站方给的外部数据，
     * 里面合法地出现过引号与 `%`（实测 `tag.json` 的返回里有 `"kimi_wo_aisuru…"` 这种带引号的长串）。
     */
    private suspend fun <T> query(names: List<String>, map: (Pair<String, android.database.Cursor>) -> T?): List<T>? =
        withContext(Dispatchers.IO) {
            val db = openDatabase() ?: return@withContext null
            val targets = names.map { it.lowercase() }.distinct()
            if (targets.isEmpty()) return@withContext emptyList()
            val placeholders = targets.joinToString(",") { "?" }
            runCatching {
                db.query(
                    "tags", arrayOf(PROJECTION_COLUMN, "category", "cn"),
                    "name IN ($placeholders)", targets.toTypedArray(), null, null, null,
                ).use { cursor ->
                    val out = ArrayList<T>(cursor.count)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0)?.lowercase() ?: continue
                        map(name to cursor)?.let { out.add(it) }
                    }
                    out
                }
            }.onFailure { android.util.Log.w(TAG, "查画廊标签词典失败", it) }.getOrNull()
        }

    companion object {
        private const val TAG = "GalleryTagDictionary"
        private const val ASSET_PREFIX = "gallery_tags_"
        private const val PROJECTION_COLUMN = "name"

        @Volatile
        private var INSTANCE: GalleryTagDictionary? = null

        fun getInstance(context: Context): GalleryTagDictionary = INSTANCE ?: synchronized(this) {
            INSTANCE ?: GalleryTagDictionary(context.applicationContext).also { INSTANCE = it }
        }
    }
}

/** `gallery_tags_79415.sqlite` → 79415；读不出数字的（人工放进去的文件）算最旧。 */
private fun String.rowCountInName(): Int? =
    removePrefix("gallery_tags_").removeSuffix(".sqlite").toIntOrNull()
