package com.venera.compose.gallery.data

import com.venera.compose.data.platform.NoInteractiveBypassTag
import com.venera.compose.data.platform.HttpEngine
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * Konachan 的读侧客户端：**Moebooru 系**（与 yande.re 同一内核），接 `.net` 全年龄镜像，匿名可用。
 *
 *
 * - `konachan.net/post.json` 对 okhttp / curl / 浏览器 UA 一律 **200**（走 Cloudflare，TCP 443 直连可达）；
 * - `konachan.com` 挂 **JS 挑战盾**（403 "Just a moment"），而画廊取数全带
 *   [NoInteractiveBypassTag]（不许弹过盾窗，那个 await 没有超时）⇒ 对盾站等于永久失败；
 * - `.net` 同时是**全年龄镜像**（实测 500 条样本 rating 全 `s`，`rating:e` 回 0 条），
 *   与撤下的 Safebooru 内容定位同档。
 *
 * ## 端点形状（逐条实测，都钉在 `GalleryKonachanParsingTest`）
 *
 * - 列表：`/post.json?tags=&limit=&page=` → 200，`page` 真翻页
 * - 单条：`/post.json?tags=id:N`（**不能**用 `/post/{id}.json`，实测 404）
 * - 单页网页地址：`/post/show/{id}` → 200
 * - 标签补全：`/tag.json?name=词*&order=count&limit=N` → 200。张数键是 **`count`**、
 *   分类键是 **`type`**（同 yande.re，不是 Danbooru/Gelbooru 那两系的 `post_count` / `category`）；
 *   **没有 `is_deprecated`** —— 一律 false，与 [YandeReTagDto] 同口径
 * - 高分池：`?tags=order:score` → 实测真按分数降序（1718/1570/1349…）
 * - 时间窗：`date:A..B order:score` **真生效**（实测 2020 窗口回的全是 2020 的图）
 * - `limit` 实测到 **500 都不截断**（本站没有 Gelbooru 那种 100 硬上限），本页仍取 [POOL_SIZE] 与另两站同口径
 * - 分级：单字母 `s`/`q`/`e`（同 yande.re 字形；**没有** Danbooru 系的 `g`）
 * - 画师：帖子 `author` 字段是**上传者账号名**（Moebooru 语义，同 yande.re），**不是**画师档名 ——
 *   它不能直接当画师用（旧 Safebooru 的 `tag_string_artist` 才是站方标好的画师标签）。
 *   画师外链那几处 when 表借 danbooru 供体（与 Gelbooru 同一条路，理由见 `GalleryArtistRows`）；
 *   本站自己的 `artist.json?name=` 实测前缀匹配可用、`/artist/show/{id}` 302 → wiki 别名链也与
 *   yande.re 同形，但**库薄**：拿帖子作者名查实测全回 0 条（`junmin`、`BattlequeenYume` 都没有），
 *   所以本批不接画师端点（行为敏感项，单独拍）。
 *
 * ## 三条**错了不会报错**的坑（都实测过）
 *
 * 1. ⚠️ `order:rank` 与 `order:fav_percent` 被**静默忽略** —— 回的就是默认 id 降序那一页。
 *    旧 Safebooru 那颗的高分池用的正是 `order:rank`；照抄过来会拿到"一屏新图假装是高分"，
 *    高分池必须用 `order:score`（上面实测钉着）。
 * 2. **没有 `fav_count`**（实测 44 键里没有）→ [GalleryPost.favCount] 交 null，不是 0。
 * 3. **没有 `media_asset`/`duration`**（那是 Danbooru 系的新版字段）→ 时长一律 null。
 */
class KonachanClient internal constructor(private val engine: HttpEngine) {

    /**
     * 取**高分池**（`order:score`，见文件头坑 1：`order:rank` 在这站是静默忽略的假读数）。
     *
     * 空列表**当失败**交出去：取到空池只有两种可能 —— 站方抽风或网络问题。
     * 两种情况都不该长成"今天没有热门"（那是假读数）。
     */
    suspend fun fetchTopScored(): Result<List<GalleryPost>> = withContext(Dispatchers.IO) {
        runCatching {
            val list = requestList("$BASE/post.json?tags=${enc("order:score")}&limit=$POOL_SIZE&page=1")
            if (list.isEmpty()) throw IOException("Konachan 的高分池返回 0 条")
            list
        }
    }

    /**
     * 取单条。**必须**走 `tags=id:N`：`/post/{id}.json` 实测 404（与 yande.re 同一形状）。
     */
    suspend fun fetchById(id: Long): Result<GalleryPost?> = withContext(Dispatchers.IO) {
        runCatching { requestList("$BASE/post.json?tags=${enc("id:$id")}").firstOrNull { it.id == id } }
    }

    /**
     * 标签前缀补全。
     *
     * `name=词*&order=count` 是 Moebooru 系写法（实测 `maid*` 回 `maid/maidforge/maid_dolce…`，
     * 按张数降序），复检前缀的兜底在 [refineGalleryTagSuggestions] 统一做。
     */
    suspend fun searchTags(term: String, limit: Int = GALLERY_TAG_SUGGESTION_LIMIT): Result<List<GalleryTagSuggestion>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$BASE/tag.json?name=${enc(term.trim() + "*")}&order=count&limit=$limit")
                    .header("Accept", "application/json")
                    .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
                    .build()
                engine.okHttpClient
                    .newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IOException("Konachan 返回 ${response.code}")
                        json.decodeFromString<List<KonachanTagDto>>(body).map { it.toSuggestion() }
                    }
            }
        }

    /**
     * 按一串标签检索。**空数组是合法结果**（查无此标签，实测 200 + `[]`）。
     *
     * 实测：`page` 真翻页、`limit` 到 500 不截（本页取 [POOL_SIZE]，与另两站同口径）。
     */
    suspend fun searchPosts(tags: String, page: Int, limit: Int = POOL_SIZE): Result<List<GalleryPost>> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestList("$BASE/post.json?tags=${enc(tags)}&limit=$limit&page=$page")
            }
        }

    private suspend fun requestList(url: String): List<GalleryPost> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        val client = engine.okHttpClient
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Konachan 返回 ${response.code}")
            return json.decodeFromString<List<KonachanPostDto>>(body).map { it.toPost() }
        }
    }

    companion object {
        val SITE: GallerySite = GallerySite.KONACHAN

        /** 搜索结果每页取多少张。与另两站同口径（本站实测 `limit` 到 500 都不截，取 100 够用）。 */
        const val POOL_SIZE = 100

        private const val BASE = "https://konachan.net"

        private val json = Json { ignoreUnknownKeys = true }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        @Volatile
        private var INSTANCE: KonachanClient? = null

        fun getInstance(engine: HttpEngine): KonachanClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: KonachanClient(engine).also { INSTANCE = it }
        }
    }
}

/**
 * Konachan 的一条 post 的**站方原始字段名**（Moebooru 字形，与 [YandeReDto] 同族）。
 *
 * ⚠️ 与撤下的 Safebooru（Danbooru 系）不是同一套键名：标签串叫 `tags`（不是 `tag_string`）、
 * 尺寸就叫 `width`/`height`（不是 `image_width`/`image_height`）、中档有现成的
 * `sample_width/height`（不在 `media_asset.variants` 里）。
 */
@Serializable
internal data class KonachanPostDto(
    val id: Long,
    val tags: String = "",
    val rating: String = "",
    val score: Int = 0,
    val author: String = "",
    val source: String = "",
    @SerialName("file_ext") val fileExt: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("preview_url") val previewUrl: String = "",
    @SerialName("actual_preview_width") val actualPreviewWidth: Int = 0,
    @SerialName("actual_preview_height") val actualPreviewHeight: Int = 0,
    /**
     * 站方中档（长边 ≤1500，实测 1500×844）。与 yande.re 同一语义：
     * 原图比 sample 还小时它回落成原图，所以不需要"没有 sample 退哪档"的兜底。
     */
    @SerialName("sample_url") val sampleUrl: String = "",
    @SerialName("sample_width") val sampleWidth: Int = 0,
    @SerialName("sample_height") val sampleHeight: Int = 0,
    /**
     * **不是降档**：实测 `jpeg_width/height` 恒等于 `width/height`（同 yande.re 那条坑，
     * 是原分辨率 JPEG 重编码）⇒ 只当 sample 为空时的兜底地址用。
     */
    @SerialName("jpeg_url") val jpegUrl: String = "",
    @SerialName("jpeg_width") val jpegWidth: Int = 0,
    @SerialName("jpeg_height") val jpegHeight: Int = 0,
    @SerialName("file_url") val fileUrl: String = "",
    @SerialName("file_size") val fileSize: Long = 0,
    val md5: String = "",
) {
    fun toPost(): GalleryPost = GalleryPost(
        site = GallerySite.KONACHAN,
        id = id,
        tags = tags,
        rating = rating,
        score = score,
        // 站方没有 `fav_count`（实测）→ null，不是 0。口径同 yande.re。
        favCount = null,
        author = author,
        source = source,
        width = width,
        height = height,
        previewUrl = previewUrl,
        previewWidth = actualPreviewWidth,
        previewHeight = actualPreviewHeight,
        // 开门档与网格卡**同址**（`preview_url`，实测 300 级 / 与 yande.re 那一路同一模型）⇒ 缓存白拿。
        fastUrl = previewUrl,
        // 中档 = `sample_url`；万一为空退 `jpeg_url`（同址不同容器，仍比原图便宜）。
        largeUrl = sampleUrl.ifBlank { jpegUrl },
        largeWidth = if (sampleUrl.isNotBlank()) sampleWidth else jpegWidth,
        largeHeight = if (sampleUrl.isNotBlank()) sampleHeight else jpegHeight,
        fileUrl = fileUrl,
        fileSize = fileSize,
        md5 = md5,
        fileExt = fileExt,
        // 本站没有 `media_asset.duration`（Danbooru 系才有）→ 一律 null，不当 0。
        durationSeconds = null,
    )
}

/**
 * Konachan `/tag.json` 的一条标签记录（Moebooru 字形）。
 *
 * ⚠️ 张数键是 `count`（不是 Gelbooru/Konachan 那两系的 `post_count`/`tags` 串）、
 * 分类键是 `type`（不是 `category`）、**没有 `is_deprecated`** —— 一律 false，
 * 别拿 `ambiguous`（"标签有歧义"）去顶替（同 [YandeReTagDto] 那条纪律）。
 */
@Serializable
internal data class KonachanTagDto(
    val id: Long = 0,
    val name: String = "",
    val count: Int = 0,
    val type: Int = 0,
) {
    fun toSuggestion(): GalleryTagSuggestion =
        GalleryTagSuggestion(GallerySite.KONACHAN, name, count, type, deprecated = false)
}
