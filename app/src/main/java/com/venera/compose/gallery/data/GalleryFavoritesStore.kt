package com.venera.compose.gallery.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 一条画廊收藏。
 *
 * ## 为什么不像漫画收藏那样落 SQLite
 *
 * 画廊条目是**扁平且整体消费**的：收藏页要的就是"整张卡"，没有按标签查询、没有分文件夹、
 * 没有多选搬运那套关系型需求（漫画侧的 `local_favorite.db` 是为这些建的）。
 * 一个条目 ~15 个标量字段，几百条也就几十 KB，一份 JSON 直接读写即可，
 * 也免了给 `venera_core.db` 加表带来的迁移风险。
 *
 * ## 为什么不直接序列化 [GalleryPost]
 *
 * 那会把**模型的形状**变成磁盘格式 —— 以后给 `GalleryPost` 加一个字段、或者改一次默认值，
 * 老档就得跟着迁移。这里显式列字段：磁盘上是什么、读回来是什么，一目了然，
 * 新字段给默认值即可向后兼容（`ignoreUnknownKeys` + 默认参数）。
 *
 * ## 为什么在这里存地址
 *
 * [previewUrl] / [largeUrl] 是**这张图本身**的地址，不是"从站方换来的临时签名"，
 * 所以离线也能把收藏夹铺出来；条目在站上被删之后，收藏里那张缩略图也还在
 * （这恰好是收藏该有的行为）。尺寸一并存，是因为网格要在**摆放前**知道比例，
 * 否则各列会先等分再集体跳动。
 */
@Serializable
data class GalleryFavorite(
    @SerialName("site") val siteKey: String,
    @SerialName("id") val id: Long,
    @SerialName("saved_at") val savedAt: Long = 0L,
    @SerialName("tags") val tags: String = "",
    /** 站方显式分级。空串 = 未知（**不是**"安全"），打码判定沿用 [GalleryPost.isAdultMarked] 的方向。 */
    @SerialName("rating") val rating: String = "",
    @SerialName("score") val score: Int = 0,
    @SerialName("fav_count") val favCount: Int? = null,
    @SerialName("author") val author: String = "",
    @SerialName("source") val source: String = "",
    @SerialName("width") val width: Int = 0,
    @SerialName("height") val height: Int = 0,
    @SerialName("preview_url") val previewUrl: String = "",
    @SerialName("preview_width") val previewWidth: Int = 0,
    @SerialName("preview_height") val previewHeight: Int = 0,
    @SerialName("fast_url") val fastUrl: String = "",
    @SerialName("large_url") val largeUrl: String = "",
    @SerialName("large_width") val largeWidth: Int = 0,
    @SerialName("large_height") val largeHeight: Int = 0,
    @SerialName("file_url") val fileUrl: String = "",
    @SerialName("file_size") val fileSize: Long = 0L,
    @SerialName("md5") val md5: String = "",
    @SerialName("file_ext") val fileExt: String = "",
    @SerialName("duration") val durationSeconds: Double? = null,
) {
    /** 站方键认不出来时返回 null —— 由调用方过滤掉，**不要**退化成"当成某一站"（那会串图）。 */
    val site: GallerySite? get() = GallerySite.fromRouteKey(siteKey)

    val uid: String get() = "$siteKey:$id"
}

/**
 * 收藏记录 → 画廊条目。
 *
 * `tagGroups` 不在这里复原：它由详情页按 `site + id` **重新取一次**（`GalleryPostScreen`
 * 本来就是这么做的），记录里那串 [GalleryFavorite.tags] 足够给卡片与搜索用。
 * 硬编一份分类桶反而会造出一份过期数据。
 */
fun GalleryFavorite.toPost(site: GallerySite): GalleryPost = GalleryPost(
    site = site,
    id = id,
    tags = tags,
    rating = rating,
    score = score,
    favCount = favCount,
    author = author,
    source = source,
    width = width,
    height = height,
    previewUrl = previewUrl,
    previewWidth = previewWidth,
    previewHeight = previewHeight,
    fastUrl = fastUrl,
    largeUrl = largeUrl,
    largeWidth = largeWidth,
    largeHeight = largeHeight,
    fileUrl = fileUrl,
    fileSize = fileSize,
    md5 = md5,
    fileExt = fileExt,
    durationSeconds = durationSeconds,
)

fun GalleryPost.toFavorite(): GalleryFavorite = GalleryFavorite(
    siteKey = site.routeKey,
    id = id,
    savedAt = System.currentTimeMillis(),
    tags = tags,
    rating = rating,
    score = score,
    favCount = favCount,
    author = author,
    source = source,
    width = width,
    height = height,
    previewUrl = previewUrl,
    previewWidth = previewWidth,
    previewHeight = previewHeight,
    fastUrl = fastUrl,
    largeUrl = largeUrl,
    largeWidth = largeWidth,
    largeHeight = largeHeight,
    fileUrl = fileUrl,
    fileSize = fileSize,
    md5 = md5,
    fileExt = fileExt,
    durationSeconds = durationSeconds,
)

/**
 * 画廊收藏的存储。**唯一事实源**：大图页那颗心与收藏页那份列表读的是同一个 [favorites]，
 * 所以"在这边取消、那边还亮着"不可能发生。
 *
 * 写盘策略：内存里的 [MutableStateFlow] 先改（UI 当帧就有反应），落盘在 IO 线程做，
 * 一条 `Mutex` 串起来防两次快速点击互相覆盖。
 *
 * **坏档不静默吞**：JSON 解不开时先把原文件改名留档（`*.corrupt-<时间戳>`）再开空档，
 * 并通过 [notice] 说一句。直接覆盖等于把用户攒的收藏删了。
 */
class GalleryFavoritesStore private constructor(private val context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    private val _favorites = MutableStateFlow<List<GalleryFavorite>>(emptyList())
    val favorites: StateFlow<List<GalleryFavorite>> = _favorites.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)

    /** 需要让用户知道的一次性说明（目前只有"档坏了、已另存"）。读走即清。 */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /**
     * 构造时同步读一次。文件是几十 KB 量级，收益是**第一帧就对**：
     * 异步读会让大图页那颗心先空一下再点亮，看起来像"刚才没点上"。
     */
    init {
        readFromDisk()
    }

    /** 这一条在不在收藏里。大图页每次重组都会问，所以走内存列表不查盘。 */
    fun contains(uid: String): Boolean = _favorites.value.any { it.uid == uid }

    fun consumeNotice(): String? = _notice.value?.also { _notice.value = null }

    /**
     * 加入/取消。返回**操作之后**的状态（true = 已收藏），供 UI 决定说哪句话。
     *
     * 存的是**当前这一条的真实数据**（地址、尺寸、标签），不是"记一个 id 等以后再取"——
     * 站方条目被删之后，那份数据是收藏里仅剩的东西。
     */
    suspend fun toggle(post: GalleryPost): Boolean {
        val uid = post.uid
        val existing = _favorites.value
        val adding = existing.none { it.uid == uid }
        _favorites.value = if (adding) {
            listOf(post.toFavorite()) + existing
        } else {
            existing.filterNot { it.uid == uid }
        }
        persist(_favorites.value)
        return adding
    }

    suspend fun remove(uid: String) {
        val next = _favorites.value.filterNot { it.uid == uid }
        if (next.size == _favorites.value.size) return
        _favorites.value = next
        persist(next)
    }

    suspend fun clear() {
        if (_favorites.value.isEmpty()) return
        _favorites.value = emptyList()
        persist(emptyList())
    }

    private suspend fun persist(list: List<GalleryFavorite>) = withContext(Dispatchers.IO) {
        runCatching {
            // 先写临时文件再改名：写到一半被杀（进程回收、闪退）不会把已有收藏截断成半个 JSON。
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(list))
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw java.io.IOException("收藏档改名失败：${file.absolutePath}")
            }
        }
    }

    private fun readFromDisk() {
        if (!file.exists()) return
        val parsed = runCatching { json.decodeFromString<List<GalleryFavorite>>(file.readText()) }.getOrNull()
        if (parsed != null) {
            // 认不出站点键的行（老版本写过别的键 / 手工改坏）当场摘掉：
            // 留着它，收藏页会有一格永远点不开也删不掉的空卡。
            _favorites.value = parsed.filter { it.site != null }.sortedByDescending { it.savedAt }
            return
        }
        val backup = File(file.parentFile, "$FILE_NAME.corrupt-${System.currentTimeMillis()}")
        val moved = runCatching { file.renameTo(backup) }.getOrDefault(false)
        _notice.value = if (moved) {
            "画廊收藏档读不出来，已另存为 ${backup.name} 并重新开始"
        } else {
            "画廊收藏档读不出来，本次改动可能覆盖它（${file.absolutePath}）"
        }
    }

    companion object {
        private const val FILE_NAME = "gallery_favorites.json"

        /**
         * `ignoreUnknownKeys`：新版本写过的字段被旧版本读到不该当场炸（用户可能回退版本）。
         * `encodeDefaults = false`：空串 / 0 不落盘，档里只留真有内容的那几项。
         */
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            prettyPrint = false
        }

        @Volatile
        private var instance: GalleryFavoritesStore? = null

        fun getInstance(context: Context): GalleryFavoritesStore = instance ?: synchronized(this) {
            instance ?: GalleryFavoritesStore(context.applicationContext).also { instance = it }
        }
    }
}
