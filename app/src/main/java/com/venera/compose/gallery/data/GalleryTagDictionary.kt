package com.venera.compose.gallery.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.venera.compose.gallery.domain.GalleryTagCategory
import com.venera.compose.gallery.domain.GalleryTitleTag
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
    suspend fun artistNames(names: List<String>): Set<String> = lookupArtists(names) ?: emptySet()

    /**
     * [artistNames] 的"分得开坏"版本：**库打不开时回 null**（未知），而不是空集。
     *
     * 「收藏里的画师」那一栏要用它。空集会被读成"你收藏里没有画师" —— 那是把**我们的故障**
     * 说成**用户没干活**，而这两件事在屏上本来长得一模一样（同一条错误在标签补全那笔里已经避过一次）。
     */
    suspend fun lookupArtists(names: List<String>): Set<String>? =
        query(names) { (name, row) -> if (row.getInt(1) == GalleryTagCategory.ARTIST) name else null }?.toSet()

    /** 这批名字的中文译名；**没有译名的键不出现**在结果里（调用方据此原样显示）。 */
    suspend fun translations(names: List<String>): Map<String, String> =
        query(names) { (name, row) -> row.getString(2)?.takeIf { it.isNotBlank() }?.let { name to it } }
            ?.toMap() ?: emptyMap()

    /**
     * 这批名字里**能当卡片标题用**的那些（通用 / 画师 / 作品 / 角色四档），连同中文译名与档位。
     *
     * 2026-09-30 新增：画廊卡片那一行标题用的就是它（两站的 post JSON **都没有标题字段**，
     * 判据与理由详见 `GalleryCardTitle`）。返回 `name(lowercase) → GalleryTitleTag`。
     * 批次 M · M6 起它从"只交作品/角色两档"放宽到四档 —— 用户要求「没有标题就不要完全空着」，
     * 回退链要画师名与通用标签译名，这两档就得从库里出来。**元数据档（5）仍然不交**。
     *
     * **档位必须一起交出去**：`GalleryCardTitle` 要靠它排"作品 > 角色 > 画师 > 通用"，
     * 只给一张 `名字 → 译名` 的表就会让那条判据退化成"按标签顺序取第一个"，
     * 遇到角色排在作品前面的图就会摆成「雷电将军（原神）· 原神」。
     *
     * ## 为什么要分块（不能直接复用 [query]）
     *
     * 一屏 40 张卡、每张约 30 枚标签，摊平去重后能到上千个名字；而 `name IN (?,?,…)` 的
     * 占位符个数就是 SQLite 的**变量上限**（老版本 999）。超了不是"少查几个"，是**直接抛异常**，
     * 而外层 `runCatching` 会把它吞成 `null` ⇒ 表现成"所有卡片都没有标题"，
     * 那是"我们的查询炸了"被说成"这些图没有作品标签"——最难发现的一类错。
     * 所以按 [TITLE_CHUNK] 切批，逐批查再合并。
     *
     * **不拼 SQL**（沿用 [query] 那条口径）：标签名是站方给的外部数据，里面有引号与 `%`。
     *
     * @return 库打不开时回 `null`（未知），与"这批名字里没有可当标题的标签"（空 map）**分开**。
     *   调用方据此区分"我们的表没打开"与"这些图确实没有能当标题的标签"。
     */
    suspend fun titleTags(names: List<String>): Map<String, GalleryTitleTag>? {
        val targets = names.map { it.lowercase() }.distinct()
        if (targets.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, GalleryTitleTag>()
        for (chunk in targets.chunked(TITLE_CHUNK)) {
            val part = query(chunk) { (name, row) ->
                val category = row.getInt(1)
                val cn = row.getString(2)?.takeIf { it.isNotBlank() }
                when (category) {
                    TITLE_COPYRIGHT, TITLE_CHARACTER, TITLE_ARTIST ->
                        // 译名为空 → 回原词：人名/罗马字照抄那类在库里 `cn` 是 NULL，
                        // 但它们本来就是"没有公认中文名"，显示原词是对的（同 TagDisplay 的口径）。
                        GalleryTitleTag(label = cn ?: name, category = category) to name

                    // 通用档**只在有中文译名时**进表：没译名就只剩 `shirt_lift` 这种原词，
                    // 摆到标题行上是一串英文下划线，比那一行空着还难读 —— 用户要的是"译名"。
                    TITLE_GENERAL -> cn?.let { GalleryTitleTag(label = it, category = category) to name }

                    // 元数据档（5）与认不出的档位一律不进表（理由见 GalleryCardTitle 判据 4）。
                    else -> null
                }
            } ?: return null // 库打不开：整批交 null，不交半份（半份会让一部分卡有标题、一部分没有）
            part.forEach { (tag, name) -> out[name] = tag }
        }
        return out
    }

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

        /**
         * 一批查多少个名字。
         *
         * 400 是"远低于 SQLite 变量上限（999）"与"批次不至于多到反复开关游标"之间的取中：
         * 上千个名字 → 3 批左右。理由与"超限会抛异常且被吞成 null"那条写在 [titleTags] 里。
         */
        private const val TITLE_CHUNK = 400

        /** 站方档位：0 = 通用、1 = 画师、3 = 作品、4 = 角色。与 `GalleryTagCategory` 同一套编号。 */
        private const val TITLE_GENERAL = 0
        private const val TITLE_ARTIST = 1
        private const val TITLE_COPYRIGHT = 3
        private const val TITLE_CHARACTER = 4

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
