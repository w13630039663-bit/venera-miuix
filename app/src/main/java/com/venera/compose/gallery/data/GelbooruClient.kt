package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.network.VeneraNetworkClient
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Request

/**
 * Gelbooru 的读侧客户端：官方 DAPI，**必须带账号**（[GelbooruAccount]），不走 JS 源脚本。
 * 四个取法：[fetchTopScored]（高分池，画廊落地流）、[fetchById]（大图页）、
 * [searchTags]（搜索页的标签补全）与 [searchPosts]（搜索结果，翻页）。
 *
 * ## 凭据是硬门槛，不是加分项
 *
 * ⚠️ **DAPI 匿名一律 401**（2026-09-26 实测：`s=post` 与 `s=tag` 不带凭据都回 401，
 * body 为空、没有错误信封可解析）。错 `api_key` 或错 `user_id` 同样 401 —— 三种情形
 * **给的是同一个答案**，所以[客户端这边判不出"哪个字段错了"]，只能如实说"凭据没被接受"。
 * 官方 wiki `howto:api` 的原文是 *"We will occasionally require authentication"*，
 * 而实测是**一律要求**，别按那句"偶尔"去设计降级路径 —— 没有账号就是一张图都取不到。
 *
 * ## 端点与分页（全部实测 + 官方文档）
 *
 * - Posts：`/index.php?page=dapi&s=post&q=index&json=1`；
 * - Tags：`/index.php?page=dapi&s=tag&q=index&json=1`；
 * - 鉴权是**两个 query 参数** `&api_key=&user_id=`（不是 HTTP Basic，与 Danbooru 不同）；
 * - `pid` 是**页号**（0 基）；`limit` 官方文档写明硬上限 **100**；
 * - `tags` 与网页同一套语法，**含元标签**（`rating:` / `sort:` / `score:` / `width:` …）。
 *
 * ## 响应是**信封**，不是裸数组
 *
 * ```json
 * {"@attributes":{"limit":2,"offset":0,"count":14304678},
 *  "post":[{"id":14970719, ...}]}
 * ```
 *
 * - 条目在 **`post`** 键里；tag 端点同理在 **`tag`** 键里；
 * - **`@attributes.count` 是命中总数**（实测 `count:2241`）—— 这一站**有总数**，
 *   与 Danbooru（`/posts/count.json` 404）不同，所以页面尾部的读数可以是真数；
 * - 实测**单条也是数组**（`&id=197798` 回的是 `"post":[{...}]`），
 *   但本类仍按"可能退化成对象"写容错（见 [GelbooruPostEnvelopeDto]），
 *   这是 Gelbooru 系历史上的经典坑法，不为眼前的样本赌它不再变。
 *
 * ## 三个字段形状上的坑（都是实测撞出来的）
 *
 * 1. **`sample_url` 会是空串，不是 null**：原图小到不需要样本（实测 682×597 那条
 *    `sample:0`、`sample_url:""`）或条目是视频时，站方给 `""`。
 *    所以中档必须 `sampleUrl.ifBlank { fileUrl }` 兜底 —— 照抄会让大图页中层全空。
 * 2. **视频的 `image` 与 `file_url` 扩展名可以不一致**：实测一条 `image` 是
 *    `...webm`、而 `file_url` 是 `...mp4`（站方转码过）。判"是不是视频"要看
 *    **`file_url`**，别拿 `image` 判。
 * 3. **没有 `file_size`**：Danbooru 给，这一站不给 → 原图大小只能留 [GalleryPost.fileSize] = 0，
 *    **不要**编一个数（UI 那边没有这个读数就不显示）。
 *
 * ## 分级是**四个单词**，不是单字母
 *
 * 实测 `rating` 取值：`general` / `sensitive` / `questionable` / `explicit`。
 * ⚠️ 与 yande.re 的 `s`/`q`/`e` **不同形**，所以 [GalleryPost.rating] 里存的是原样单词，
 * 归一判据（`isAdultMarked`）在那边按"只认 `general`/`sensitive` 为安全"处理 ——
 * 这正好与它原先"只认 `s`/`g`"的口径等价，不必另开一套。
 *
 * ## 日榜怎么来（这一站**没有**日榜端点）
 *
 * Danbooru 有 `explore/posts/popular.json`、yande.re 有 `popular_recent`，
 * Gelbooru **两者都没有**。所以用元标签模拟（用户 2026-09-26 拍板）：
 * `tags=sort:score:desc` —— 实测回的是全站最高分（31144 / 12482 / 10042 分），
 * 正是 yande.re 日榜想要的那种"当前热门"语义。
 * ⚠️ 用 `sort:score` 而不是 `sort:updated:desc`：后者是"最近被改过的"，
 * 改一条旧图的标签就能把它顶上来，那不是热门。
 */
class GelbooruClient private constructor(context: Context) {

    private val appContext = context.applicationContext

    /**
     * 取**高分池**（本站没有日榜端点，用 `sort:score:desc` 模拟，理由见类注释）。
     *
     * 空列表**当失败**交出去：这一站取到空池只有两种可能 —— 凭据没了，
     * 或者站方抽风。两种情况都不该长成"今天没有热门"（那是假读数，
     * 页尾会写着 Gelbooru 0 而用户不知道要去看账号设置）。
     */
    suspend fun fetchTopScored(): Result<List<GalleryPost>> = withContext(Dispatchers.IO) {
        runCatching {
            val list = requestPosts(
                "$BASE?page=dapi&s=post&q=index&json=1&tags=${enc("sort:score:desc")}&limit=$POOL_SIZE&pid=0"
            )
            if (list.isEmpty()) throw IOException("Gelbooru 的高分池返回 0 条")
            list
        }
    }

    suspend fun fetchById(id: Long): Result<GalleryPost?> = withContext(Dispatchers.IO) {
        runCatching { requestPosts("$BASE?page=dapi&s=post&q=index&json=1&id=$id").firstOrNull { it.id == id } }
    }

    /**
     * 标签前缀补全。
     *
     * `name_pattern` 是 **SQL LIKE 语法**（官方 wiki：`%` 多字符通配、`_` 单字符通配），
     * 所以前缀匹配写成 `<词>%`。`orderby=count` 让常用的排前面。
     * ⚠️ 通配符是 `%` 而**不是** Danbooru 的 `*` —— 用错不会报错，
     * 只会静默回一堆不相干的标签，兜底复检在 [refineGalleryTagSuggestions]。
     */
    suspend fun searchTags(term: String, limit: Int = GALLERY_TAG_SUGGESTION_LIMIT): Result<List<GalleryTagSuggestion>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pattern = enc(term.trim() + "%")
                val url = "$BASE?page=dapi&s=tag&q=index&json=1&name_pattern=$pattern" +
                    "&orderby=count&limit=$limit"
                json.decodeFromString<GelbooruTagEnvelopeDto>(get(url))
                    .tags()
                    .map { it.toSuggestion() }
            }
        }

    /**
     * 按一串标签检索。**空数组是合法结果**（查无此标签），与 [fetchTopScored] 那条
     * "空列表当失败"的口径**刻意不同** —— 沿用那边会让用户搜一个冷门标签时得到一句"加载失败"。
     *
     * ⚠️ `pid` 是**0 基**：调用方传的 `page` 从 1 起（与 yande.re 那条一致），
     * 这里减 1 再发。不减的话第一页会取到第二页的数据，而**不会有任何报错**。
     *
     * @param tags 已经拼好的标签串（含 `-` 前缀的排除项）。本站实测**标签数不限**
     *   （2026-09-26：7 枚一次通过、HTTP 200），所以没有 Danbooru 那种"第 3 枚 422"的形态。
     */
    suspend fun searchPosts(tags: String, page: Int, limit: Int = POOL_SIZE): Result<List<GalleryPost>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pid = (page - 1).coerceAtLeast(0)
                requestPosts("$BASE?page=dapi&s=post&q=index&json=1&tags=${enc(tags)}&limit=$limit&pid=$pid")
            }
        }

    /** 一次取回 + 解信封 + 翻译。三处调用共用同一条口径。 */
    private suspend fun requestPosts(url: String): List<GalleryPost> =
        json.decodeFromString<GelbooruPostEnvelopeDto>(get(url))
            .posts()
            .map { it.toPost() }
            .filterNotNull()

    /**
     * 发一笔 GET 并交出 body；非 2xx 一律翻成人话。
     *
     * ⚠️ 凭证走**类内部**的 [GelbooruAccount]，不走 `VeneraNetworkClient` 的公共头 ——
     * 后者是全应用的，把站别凭据挂上去会漏给别的站。
     */
    private fun get(url: String): String {
        val client = VeneraNetworkClient.getInstance(appContext).okHttpClient
        client.newCall(requestBuilder(url).build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw failure(response.code)
            return body
        }
    }

    /**
     * 这一站所有请求的公共部分：**UA + 两个凭据参数**。
     *
     * UA 用非浏览器串（与 yande.re 同一条理由的弱化版）：本项目的全局默认 UA 是移动端
     * Chrome 串，而图站挂在 Cloudflare 后面，浏览器串容易被当脚本流量拦。
     * 这一站实测没有 Danbooru 那种"浏览器串必 403"的强判据，但保持一致更省事。
     *
     * 凭据是 query 参数（官方 wiki 的写法），**不是** Authorization 头。
     * 两个都空时照发 —— 让站方回 401，比在这里编一句"未登录"更接近事实
     * （用户可能是只填了一半）。
     */
    private fun requestBuilder(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(withCredentials(url))
            .header("Accept", "application/json")
            .header("User-Agent", API_USER_AGENT)
        return builder
    }

    /** 把 `&api_key=` / `&user_id=` 拼上去（没有凭据就原样返回，由站方回 401）。 */
    private fun withCredentials(url: String): String {
        val account = GelbooruAccount.getInstance(appContext)
        val key = account.apiKey
        val uid = account.userId
        if (key.isBlank() || uid.isBlank()) return url
        val sep = if (url.contains('?')) '&' else '?'
        return "$url$sep&api_key=${enc(key)}&user_id=${enc(uid)}"
    }

    /**
     * 非 2xx → 一句人话。
     *
     * **401 是主角**：本站的 401 **body 为空**（实测三种错法都是），
     * 所以解析不出"是 key 错还是 id 错"，只能给一句把两种可能都写上的话，
     * 并把"去哪儿改"指出来 —— 否则用户会以为是网络问题反复重试。
     */
    private fun failure(code: Int): IOException = when (code) {
        401 -> IOException("Gelbooru 没接受这组凭据（API Key 与 User ID 要对得上，且都应来自同一账号）")
        403 -> IOException("Gelbooru 回了 403：请求被站方拦了")
        429 -> IOException("Gelbooru 限流了（429），稍后再试")
        else -> IOException("Gelbooru 返回 $code")
    }

    companion object {
        val SITE: GallerySite = GallerySite.GELBOORU

        /** 非浏览器串（理由见 [requestBuilder]）。 */
        const val API_USER_AGENT = "Venera/1.0 (Android)"

        /**
         * 一次取多少条。
         *
         * ⚠️ **官方文档写明 `limit` 硬上限 100**（wiki 原话：*There is a default limit of
         * 100 posts per request*）。所以这里就是 100 —— 与 Danbooru 的 200 不同，
         * 传 200 会被站方截掉或报错，别照抄那边的数。
         */
        const val POOL_SIZE = 100

        private const val BASE = "https://gelbooru.com/index.php"

        private val json = Json { ignoreUnknownKeys = true }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        @Volatile
        private var INSTANCE: GelbooruClient? = null

        fun getInstance(context: Context): GelbooruClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: GelbooruClient(context.applicationContext).also { INSTANCE = it }
        }
    }
}

/**
 * Posts 端点的**信封**。
 *
 * 单独一个类而不是直接解 `List`：站方把条目放在 `post` 键里（不是裸数组），
 * 而且 `@attributes` 里带着**命中总数** `count`（实测有，与 Danbooru 不同）。
 *
 * `post` 声明成 [JsonElement] 是为了兼容"单条退化成对象"这种历史上的坑法 ——
 * 本轮的样本里它一直是数组（连 `&id=` 单条取也是），但兼容的代价只有 [posts] 里那一处判断。
 */
@Serializable
internal data class GelbooruPostEnvelopeDto(
    @SerialName("@attributes") val attributes: GelbooruAttributesDto = GelbooruAttributesDto(),
    val post: JsonElement? = null,
) {
    /** 条目列表；`post` 缺席时回空表（不抛，由调用方按"0 条"处理）。 */
    fun posts(): List<GelbooruDto> = decodeEnvelope(this.post)
}

@Serializable
internal data class GelbooruAttributesDto(
    val limit: Int = 0,
    val offset: Int = 0,
    /** **命中总数**（实测 `count:14304678`）。0 表示站方没给。 */
    val count: Long = 0,
)

/**
 * Tags 端点的信封（形状同上，条目在 `tag` 键里）。
 *
 * `tag` 同样声明成 [JsonElement]：实测 `count:1` 时它**仍是数组**，
 * 但两种形态都兼容，免得站方哪天改。
 */
@Serializable
internal data class GelbooruTagEnvelopeDto(
    @SerialName("@attributes") val attributes: GelbooruAttributesDto = GelbooruAttributesDto(),
    val tag: JsonElement? = null,
) {
    /** 候选列表；`tag` 缺席时回空表。 */
    fun tags(): List<GelbooruTagDto> = decodeEnvelope(this.tag)
}

/**
 * 把信封里那一段**既可能是数组、也可能退化成单个对象**的内容解成列表。
 *
 * 这是 Gelbooru 系 API 的老坑（条目数 1 时改变容器类型），本轮实测它没有发作，
 * 但这个兼容的成本只有几行，而不兼容的代价是"某个冷门查询整页崩"，
 * 那种 bug 复现条件很窄、极难查。所以留着。
 *
 * `null`（整键缺席）与 `JsonNull` 都回空表；解单条失败也回空表而不是抛出 ——
 * 一条脏数据不该让整页空白。
 *
 * ⚠️ 自己持一份 `Json` 而不复用 [GelbooruClient] companion 里那个：这是**顶层**函数，
 * 看不到类的 private 成员，而且 companion 那个在类初始化时才生效 ——
 * 顶层函数不该依赖某个类的初始化顺序才能工作。同样 `ignoreUnknownKeys`，口径一致。
 */
private val envelopeJson = Json { ignoreUnknownKeys = true }

private inline fun <reified T> decodeEnvelope(element: JsonElement?): List<T> = when {
    element == null || element is JsonNull -> emptyList()
    element is JsonArray -> element.mapNotNull { runCatching { envelopeJson.decodeFromJsonElement<T>(it) }.getOrNull() }
    else -> listOfNotNull(runCatching { envelopeJson.decodeFromJsonElement<T>(element) }.getOrNull())
}

/**
 * Gelbooru 一条 post 的**站方原始字段名**（2026-09-26 实测原文抄录）。
 *
 * ```json
 * {"id":14970719,"created_at":"Sat Sep 26 03:51:09 -0500 2026","score":0,
 *  "width":1334,"height":1002,"md5":"da56...","directory":"da/56",
 *  "image":"da56....png","rating":"general","source":"https://...","change":1790412670,
 *  "owner":"beamblue367","creator_id":1120925,"parent_id":0,"sample":1,
 *  "preview_height":262,"preview_width":349,"tags":"2girls bang_dream! ...",
 *  "title":"","has_notes":"false","has_comments":"false",
 *  "file_url":"https://img4.gelbooru.com/images/da/56/da56....png",
 *  "preview_url":"https://img4.gelbooru.com/thumbnails/da/56/thumbnail_da56....jpg",
 *  "sample_url":"https://img4.gelbooru.com/samples/da/56/sample_da56....jpg",
 *  "sample_height":638,"sample_width":849,"status":"active","post_locked":0,
 *  "has_children":"false"}
 * ```
 *
 * 每个字段都给了默认值：站方会**整键省略**（比如 `sample_url` 在小图上给空串、
 * `file_size` 这一站根本没有），少一个键不该让整页解析失败。
 */
@Serializable
internal data class GelbooruDto(
    val id: Long = 0,
    val score: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val md5: String = "",
    /** 分级是**单词**：`general` / `sensitive` / `questionable` / `explicit`。 */
    val rating: String = "",
    val source: String = "",
    val tags: String = "",
    @SerialName("owner") val owner: String = "",
    /**
     * 文件名（**可能带路径前缀**，实测高分池里有一条是 `6d15efdf...jpg`）。
     * ⚠️ 扩展名要以 [fileUrl] 为准 —— 实测视频那条 `image` 是 `.webm` 而 [fileUrl] 是 `.mp4`。
     */
    val image: String = "",
    /** 原图。⚠️ 视频条目这里可能是转码后的 `.mp4`，与 [image] 不一致。 */
    @SerialName("file_url") val fileUrl: String = "",
    /** 缩略图（**恒非空**，实测连 `sample:0` 的小图也有）。 */
    @SerialName("preview_url") val previewUrl: String = "",
    /**
     * 中档样本。⚠️ **可能是空串**（原图小到不需要样本、或条目是视频），
     * 实测 682×597 那条就是 `""`。翻译时兜底到 [fileUrl]，见 [toPost]。
     */
    @SerialName("sample_url") val sampleUrl: String = "",
    @SerialName("preview_width") val previewWidth: Int = 0,
    @SerialName("preview_height") val previewHeight: Int = 0,
    @SerialName("sample_width") val sampleWidth: Int = 0,
    @SerialName("sample_height") val sampleHeight: Int = 0,
    /**
     * 站方有没有给样本（1/0）。实测与 [sampleUrl] 是否为空**一致**，
     * 所以翻译时不看它、只看 [sampleUrl] 空不空 —— 一处判据比两处好。
     */
    val sample: Int = 0,
    /** Unix 秒。日榜模拟用不到它（我们用 `sort:score`），留作读数。 */
    val change: Long = 0,
) {
    /**
     * 翻成归一形状。
     *
     * 四处不显然的取法：
     * - **中档兜底**：`sampleUrl.ifBlank { fileUrl }` —— 站方给空串时不兜底，
     *   大图页中层就是空的（实测 682×597 那条会整张显示不出来）；
     * - **扩展名取自 [fileUrl] 而不是 [image]**：实测视频那条 `image` 是 `webm`、
     *   `file_url` 是 `mp4`，拿 `image` 判会把"站方转码好的 mp4"误判成 webm；
     * - **`tags` 要解 HTML 实体**：站方把单引号转义成 `&#039;`（实测
     *   `grabbing_another&#039;s_arm`），不解开会让标签串里出现实体、
     *   而 [GalleryPost.tagList] 是与屏蔽规则比对的输入 —— 差一个字符就漏挡；
     * - **`source` 同样要解**：实测一条视频的 `source` 是
     *   `...page=post&amp;s=view&amp;id=7457880` —— 那是**要被当链接打开的字符串**，
     *   带着 `&amp;` 交给浏览器会打到一个不存在的地址。
     *   （旧实现只解 tags，那是因为旧站不做这种转义；这一站两处都做。）
     */
    fun toPost(): GalleryPost? {
        if (id <= 0L || fileUrl.isBlank()) return null
        val ext = fileUrl.substringAfterLast('.', "").substringBefore('?').lowercase()
        return GalleryPost(
            site = GallerySite.GELBOORU,
            id = id,
            tags = decodeHtmlEntities(tags),
            rating = rating,
            score = score,
            // 本站**不给 fav_count**（实测字段表里没有）→ null，不当 0。
            favCount = null,
            author = owner,
            source = decodeHtmlEntities(source),
            width = width,
            height = height,
            previewUrl = previewUrl,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            /*
             * 开门档 = 缩略图本身。与 yande.re 同一路数（那边也是 preview_url 直接顶上）：
             * 这一站的缩略图 350px 级（实测 preview_width 349/350），铺满屏够用且**恒非空**，
             * 比另找一个"更清楚但要额外算地址"的档稳。
             */
            fastUrl = previewUrl,
            largeUrl = sampleUrl.ifBlank { fileUrl },
            // 样本缺位时中档就是原图，尺寸要跟着换成原图的那对，否则瀑布流比例会错。
            largeWidth = if (sampleUrl.isNotBlank()) sampleWidth else width,
            largeHeight = if (sampleUrl.isNotBlank()) sampleHeight else height,
            fileUrl = fileUrl,
            // 站方不给文件大小（实测无 file_size）→ 0，UI 没有这个读数就不显示。
            fileSize = 0,
            md5 = md5,
            fileExt = ext,
            // 本站只有一串平铺 tags，没有分类字段（实测）→ 只能一桶，与 yande.re 同理。
            tagGroups = listOfNotNull(galleryTagGroup("标签", decodeHtmlEntities(tags))),
            // 站方不 video 时长（实测字段表里没有 duration）→ null，不当 0。
            durationSeconds = null,
        )
    }
}

/**
 * 解开站方塞进 JSON 里的 HTML 实体。
 *
 * 实测站方会转义单引号（`grabbing_another&#039;s_arm`）、`&`（`lilo_&amp;_stitch`）。
 * 本仓只需要处理标签串里会出现的这几个 —— 用 `Html.fromHtml` 会引入 Android 依赖、
 * 且会把 `<` 之类当标签吃掉，而标签表里出现尖括号的场合本来就不该发生。
 *
 * 顺序要紧：`&amp;` **最后**解，否则 `&amp;#039;` 这种双重转义会被拆错。
 */
private fun decodeHtmlEntities(raw: String): String = raw
    .replace("&#039;", "'")
    .replace("&#39;", "'")
    .replace("&quot;", "\"")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
