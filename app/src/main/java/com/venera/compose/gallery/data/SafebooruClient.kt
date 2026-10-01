package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.network.NoInteractiveBypassTag
import com.venera.compose.data.network.VeneraNetworkClient
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * Safebooru 的读侧客户端：Danbooru 官方全年龄镜像，**匿名可用**（不像 Gelbooru 需要 api_key+user_id）。
 *
 * 与 Gelbooru 同属 Danbooru 系（`/posts.json`、`tag_string_*`、`media_asset.variants` 同字形），
 * 但端点、鉴权、字段名都不同，**不能照抄 GelbooruClient**。
 *
 * ## 实测结论（2026-10-01）
 *
 * - 列表：`/posts.json?tags=&limit=&page=` → 200
 * - 单条：`/posts.json?tags=id:N`（**不能**用 `/posts/{id}.json`，那是 404）
 * - 单页网页地址：`/posts/{id}` → 200（与主站的 `/posts/{id}` 同形，已实测）
 * - 标签补全：`/tags.json?search[name_matches]=词*&limit=N` → 200
 * - 排行：`?tags=order:rank` → 200
 * - 图床：`cdn.donmai.us` 直连通
 * - 分级：`g`/`s`/`q`/`e`（单字母，同 yande.re 字形）
 * - 有 `fav_count` 与 `tag_string_*`（yande.re 没有）
 *
 * ## 三条**错了不会报错**的字段坑（都实测钉在 `GallerySafebooruParsingTest`）
 *
 * 1. **标签串叫 `tag_string`，没有 `tags` 这个键**。按 `tags` 声明会解成空串 ——
 *    HTTP 200、正文合法、卡片却一枚标签都没有，屏蔽规则与标签翻译同时静默失效。
 * 2. **尺寸没有现成的 sample 档字段**（既不是 Gelbooru 的 `sample_width`，也没有
 *    `large_file_width`），真值只在 `media_asset.variants[]` 里逐档带着。
 *    写死一个数字会让**整站所有卡**都按那一个比例摆 —— 实测同一池子里 sample 档
 *    是 850×1133 / 850×988 / 850×1416 / 850×1074 各不相同。
 * 3. **视频条目的 `large_file_url` 就是原片 mp4 本身**（与 `file_url` 同址），
 *    与 Gelbooru 那个坑同形：拿它当中档等于先下整片再交给 Coil 解码失败。
 *    静帧在 `variants` 里（720x720 是 webp、360x360 是 jpg），所以中档换成静帧，
 *    与 yande.re 那一路"中档本身就是静帧"的模型对齐。
 *
 * ## 字段名与 Gelbooru 的差异
 *
 * | 用途 | Gelbooru | Safebooru |
 * |---|---|---|
 * | 原图 | `file_url` | `file_url` |
 * | 中档 | `sample_url` | `large_file_url` |
 * | 预览 | `preview_url` | `preview_file_url` |
 * | 尺寸 | `width`/`height` + `sample_width/height` | `image_width`/`image_height`，**降档尺寸只在 `media_asset.variants[]`** |
 * | 标签串 | `tags` | `tag_string` |
 * | 画师 | 无 | `tag_string_artist` |
 * | 收藏数 | 无 | `fav_count` |
 * | 时长 | 无 | `media_asset.duration` |
 *
 * ⚠️ `image_width`/`image_height` 不是 `width`/`height` —— 照抄 Gelbooru 会拿到 0。
 */
class SafebooruClient private constructor(context: Context) {

    private val appContext = context.applicationContext

    /**
     * 取**高分池**（`order:rank`）。
     *
     * 空列表**当失败**交出去：取到空池只有两种可能 —— 站方抽风或网络问题。
     * 两种情况都不该长成"今天没有热门"（那是假读数）。
     */
    suspend fun fetchTopScored(): Result<List<GalleryPost>> = withContext(Dispatchers.IO) {
        runCatching {
            val list = requestList("$BASE/posts.json?tags=${enc("order:rank")}&limit=$POOL_SIZE&page=1")
            if (list.isEmpty()) throw IOException("Safebooru 的高分池返回 0 条")
            list
        }
    }

    /**
     * 取单条。**必须**走 `tags=id:N`：`/posts/{id}.json` 是 404。
     */
    suspend fun fetchById(id: Long): Result<GalleryPost?> = withContext(Dispatchers.IO) {
        runCatching { requestList("$BASE/posts.json?tags=${enc("id:$id")}").firstOrNull { it.id == id } }
    }

    /**
     * 标签前缀补全。
     *
     * `search[name_matches]=词*` 是前缀匹配（实测 `hatsune*` 返回 5 条）。
     * ⚠️ 通配符是 `*` 而**不是** Gelbooru 的 `%` —— 用错不会报错，只会静默回空。
     */
    suspend fun searchTags(term: String, limit: Int = GALLERY_TAG_SUGGESTION_LIMIT): Result<List<GalleryTagSuggestion>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$BASE/tags.json?search[name_matches]=${enc(term.trim() + "*")}&limit=$limit")
                    .header("Accept", "application/json")
                    .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
                    .build()
                VeneraNetworkClient.getInstance(appContext).okHttpClient
                    .newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IOException("Safebooru 返回 ${response.code}")
                        json.decodeFromString<List<SafebooruTagDto>>(body).map { it.toSuggestion() }
                    }
            }
        }

    /**
     * 按一串标签检索。**空数组是合法结果**（查无此标签，实测 200 + `[]`）。
     *
     * 实测：`page` 真翻页、`-负向标签` 有效、`limit` 天花板 320（本页取 [POOL_SIZE]）。
     */
    suspend fun searchPosts(tags: String, page: Int, limit: Int = POOL_SIZE): Result<List<GalleryPost>> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestList("$BASE/posts.json?tags=${enc(tags)}&limit=$limit&page=$page")
            }
        }

    private suspend fun requestList(url: String): List<GalleryPost> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        val client = VeneraNetworkClient.getInstance(appContext).okHttpClient
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Safebooru 返回 ${response.code}")
            return json.decodeFromString<List<SafebooruPostDto>>(body).map { it.toPost() }
        }
    }

    companion object {
        val SITE: GallerySite = GallerySite.SAFEBOORU

        /** 搜索结果每页取多少张。站方 `limit` 天花板实测 320，100 够一屏半。 */
        const val POOL_SIZE = 100

        private const val BASE = "https://safebooru.donmai.us"

        private val json = Json { ignoreUnknownKeys = true }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        @Volatile
        private var INSTANCE: SafebooruClient? = null

        fun getInstance(context: Context): SafebooruClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: SafebooruClient(context.applicationContext).also { INSTANCE = it }
        }
    }
}

/**
 * Safebooru 的一条 post 的**站方原始字段名**。
 *
 * ⚠️ 与 Gelbooru 的差异都写在本文件头那张对照表与三条坑里，最容易静默交错的两条：
 * - 标签串叫 `tag_string`（**没有** `tags` 这个键）
 * - 降档尺寸只在 `media_asset.variants[]` 里，顶层没有任何 `sample_width` 类字段
 */
@Serializable
internal data class SafebooruPostDto(
    val id: Long,
    @SerialName("tag_string") val tagString: String = "",
    val rating: String = "",
    val score: Int = 0,
    @SerialName("fav_count") val favCount: Int = 0,
    val source: String = "",
    @SerialName("file_ext") val fileExt: String = "",
    @SerialName("image_width") val imageWidth: Int = 0,
    @SerialName("image_height") val imageHeight: Int = 0,
    @SerialName("preview_file_url") val previewFileUrl: String = "",
    @SerialName("large_file_url") val largeFileUrl: String = "",
    @SerialName("file_url") val fileUrl: String = "",
    @SerialName("file_size") val fileSize: Long = 0,
    val md5: String = "",
    @SerialName("tag_string_artist") val tagStringArtist: String = "",
    @SerialName("media_asset") val mediaAsset: SafebooruMediaAssetDto? = null,
) {
    /** 取某一档转码产物；**只认图片档**（视频条目的 `original` 档是 mp4，不能当图用）。 */
    private fun variant(type: String): SafebooruVariantDto? =
        mediaAsset?.variants?.firstOrNull {
            it.type == type && it.fileExt.lowercase() !in GalleryPost.VIDEO_EXTS
        }

    fun toPost(): GalleryPost {
        val isVideo = fileExt.lowercase() in GalleryPost.VIDEO_EXTS
        val preview = variant("180x180")
        // 开门档取 360 那一档，与另两站的 preview 级同口径（yande 300×212、Gelbooru 350px 级）：
        // `preview_file_url` 只有 180，铺满一屏会糊到认不出画的是什么。
        // 代价是大图页开门多一笔约 32 KB 的下载，且不再与网格卡同址命中缓存 —— 实测同一素材
        // 180 档 9.7 KB / 360 档 32 KB / sample 档 210 KB / 原图 2.7 MB，这一档最划算。
        val fast = variant("360x360")
        // 视频那一档必须换掉站方的 `large_file_url`：它对视频条目就是原片 mp4 本身（实测与
        // `file_url` 同址、且 `has_large=false`），交给 Coil 就是先下整片再解码失败。
        // `variants` 里的 720/360 是站方自己的静帧（webp/jpg），与 yande.re 那一路同形。
        val still = if (isVideo) variant("720x720") ?: fast else null
        val large = still ?: variant("sample")
        return GalleryPost(
            site = GallerySite.SAFEBOORU,
            id = id,
            tags = tagString,
            rating = rating,
            score = score,
            // 这一站**有**这个字段（另两站没有，见 GalleryPost 的注释），所以给真数而不是 null。
            favCount = favCount,
            // tag_string_artist 是站方给的正名，比从 tags 里推更可靠
            author = tagStringArtist,
            source = source,
            width = imageWidth,
            height = imageHeight,
            previewUrl = previewFileUrl,
            previewWidth = preview?.width ?: 0,
            previewHeight = preview?.height ?: 0,
            // 与网格不同址是刻意选择（见上），但取不到 360 档时仍回落到缩略档，不留空。
            fastUrl = fast?.url ?: previewFileUrl,
            largeUrl = when {
                still != null -> still.url
                else -> largeFileUrl.ifBlank { fileUrl }
            },
            // 降档尺寸站方只给 `sample` 档那一对；`has_large=false` 时中档就是原图，
            // 比例与 `image_width/height` 一致（实测），所以直接用它，不编数字。
            largeWidth = still?.width ?: large?.width
                ?: (if (largeFileUrl.isBlank() || largeFileUrl == fileUrl) imageWidth else 0),
            largeHeight = still?.height ?: large?.height
                ?: (if (largeFileUrl.isBlank() || largeFileUrl == fileUrl) imageHeight else 0),
            fileUrl = fileUrl,
            fileSize = fileSize,
            md5 = md5,
            fileExt = fileExt,
            // 站方给的是**秒**（实测一条 9.055782），且图片条目为 null → 保持 null，不当 0。
            durationSeconds = mediaAsset?.duration,
        )
    }
}

/**
 * `media_asset`：Safebooru 的**降档尺寸唯一出处**。
 *
 * ⚠️ `duration` 只对视频条目非空；图片条目站方直接给 `null`（实测）。
 */
@Serializable
internal data class SafebooruMediaAssetDto(
    @SerialName("file_ext") val fileExt: String = "",
    val duration: Double? = null,
    @SerialName("image_width") val imageWidth: Int = 0,
    @SerialName("image_height") val imageHeight: Int = 0,
    val variants: List<SafebooruVariantDto> = emptyList(),
)

/**
 * 一档转码产物。`type` 是站方自己的档名（`180x180` / `360x360` / `720x720` / `sample` / `original`）。
 *
 * ⚠️ 档名里的数字是**外接框**而不是实际尺寸（实测一条 3000×4000 的竖图，`180x180` 档是
 * 135×180），所以比例一律读 `width`/`height`，不拿档名去算。
 * 视频条目的 `original` 档 `file_ext` 是 `mp4` —— 它不是图，见 [SafebooruPostDto.variant]。
 */
@Serializable
internal data class SafebooruVariantDto(
    val type: String = "",
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("file_ext") val fileExt: String = "",
)

/**
 * Safebooru `/tags.json` 的一条标签记录。
 *
 * ⚠️ 张数字段叫 `post_count`（不是 Gelbooru 的 `count`）；
 * 分类字段叫 `category`（不是 Gelbooru 的 `type`）；
 * 弃用标记**有**（`is_deprecated`，Gelbooru 那边没有、只能一律 false）。
 */
@Serializable
internal data class SafebooruTagDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("post_count") val postCount: Int = 0,
    val category: Int = 0,
    @SerialName("is_deprecated") val isDeprecated: Boolean = false,
) {
    fun toSuggestion(): GalleryTagSuggestion =
        GalleryTagSuggestion(GallerySite.SAFEBOORU, name, postCount, category, deprecated = isDeprecated)
}
