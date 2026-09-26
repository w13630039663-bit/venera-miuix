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
import okhttp3.Request

/**
 * Danbooru 的读侧客户端：官方 JSON API，可带账号鉴权（[DanbooruAccount]，只影响标签预算），
 * 不走 JS 源脚本。
 * 四个取法：[fetchDailyPopular]（某一天的热门，画廊落地流）、[fetchById]（大图页）、
 * [searchTags]（搜索页的标签补全）与 [searchPosts]（搜索结果，翻页）。
 *
 * **UA 是最要紧的一条**（2026-09-25 探针，数据见 `gallery-dual-source-hot-pool-plan-2026-09.md` §一）：
 * 站方挂在 Cloudflare 上，而本项目的全局默认 UA（[com.venera.compose.data.network.UserAgentPolicy.DEFAULT_USER_AGENT]）
 * 是**移动端 Chrome 串** —— 拿它去打 `danbooru.donmai.us`，**API 和 CDN 都回 403 + `cf-mitigated: challenge`**
 * （连 180×180 的缩略图都是 5.9 KB 的挑战页 HTML）。换成非浏览器 UA 就 200。
 * 所以本类**必须**自己带 UA：`VeneraNetworkClient` 只在请求没有 UA 时才填全局默认，显式设了就不会被覆写。
 * 图片那一路同理，走 `ImageHeaderPolicy` 的内置表补上。
 *
 * ⚠️ 同一条对**播放视频**同样成立，而且那里没有现成的兜底：media3 走自己的 HTTP 栈，
 * 既不过 `VeneraNetworkClient` 也拿不到 `ImageHeaderPolicy` → 播放器必须自己带 [API_USER_AGENT]，
 * 否则观感就是"缩略图看得到、点开播不了"。见 `GalleryVideoPlayer`。
 *
 * 其余都是实测，不是照另一端点推断的（完整表见方案 §13.1）：
 * - **日榜** `explore/posts/popular.json?date=&scale=day&page=1&limit=200` → 200 条，
 *   字段与 `/posts.json` 同一套；**`date` 缺省静默回 0 条**（本类把空列表当失败交出去，不当"今天没热门"）；
 * - 当天池子成分实测 `jpg 128 / png 61 / `**`mp4 10`**` / zip 1`，分级 `e 87 / q 40 / s 72 / g 1`
 *   —— `g`（general）是 yande.re 没有的一档，放行它的判据在 `GalleryPost.isAdultMarked`；
 * - 匿名 `limit` 上限 **200**（传 400 被截成 200，与 yande.re 的 320 不同）；
 * - 列表里混非图片，而站方的 `type:jpg` / `file_ext:jpg` 过滤实测**返回 0 条**、
 *   `-type:video` 与 `order:rank` 组合时被忽略 → 只能客户端按 `file_ext` 滤（判据在 `GalleryMerge`）。
 *   **`mp4` 现在是要的**（画廊引入视频），只有 `zip` 这类继续滤；
 * - **视频条目的字段形状与图片不同**：`has_large=false`、`large_file_url` 就是原片 `.mp4`，
 *   `media_asset.variants` 只有 `180x180.jpg` / `360x360.jpg` / `720x720.webp`（静帧）/ `original.mp4`，
 *   **没有更小的视频转码档**（实测原片 16.1 MB / 42.8 秒、26.1 MB / 26.3 秒）；
 *   时长只在 `media_asset.duration`，顶层没有 `duration`、也**没有** `type` 键
 *   （早期记的 `type:jpg` 是 tag 查询语法，不是字段）→ 中间档取静帧，见 `toPost`；
 * - **相当一部分条目没有 `sample` 变体**（月榜那份实测 52/200），
 *   所以静图那档要"sample 变体 → 顶层 `large_file_url`"两级兜底，尺寸同理；
 * - 成人条目（`e`）**匿名照样取得到原图**（实测四档全 200、原图 2.8 MB；`cat rating:e` 5/5 有图）
 *   —— 挡图的是标签不是分级：带 `loli` / `shota` 的条目连那四个键都被删掉，见 [CENSORED_TAGS]；
 * - 单条同 yande.re 的坑法：`tags=id:N`（实测正好 1 条）；
 * - 连发 6 笔全 200、无 `Retry-After`，未见限流形态；
 * - **搜索面**（2026-09-25 实测，全表见 `gallery-search-2026-09.md` §〇）：
 *   官方 `/tags/autocomplete.json` **404**，补全只能 `/tags.json?search[name_matches]=词*&search[order]=count`；
 *   匿名标签预算 **2 枚**（第 3 枚 422 + `PostQuery::TagLimitError`）；`-负向标签` 有效；
 *   `rating:safe` 有效（本站有 `g/s/q/e` 四档）；**没有**总数端点（`/posts/count.json` 404），
 *   所以命中数只能报"已取回几张"；查无此标签回 **200 + `[]`**，与日榜那条"空列表当失败"**刻意不同**。
 */
class DanbooruClient private constructor(context: Context) {

    private val appContext = context.applicationContext

    /**
     * 取**某一天**的热门（用户点名的那个页面的 JSON 版）。
     *
     * ⚠️ **`date` 必填**：实测 `scale=day` 不带 `date` 直接回 **0 条**，状态还是 200 ——
     * 照另一档 `scale=month` 的写法推断（那里 date 可省、默认当月）就会得到一屏空白。
     * 空列表因此**不算成功**：调用方要拿到原因，别让它长成"今天没有热门"。
     *
     * @param date `yyyy-MM-dd`（站方按 UTC 日切；本仓传"本地昨天的日期"）。
     */
    suspend fun fetchDailyPopular(date: String): Result<List<GalleryPost>> = withContext(Dispatchers.IO) {
        runCatching {
            val list = requestList(
                "$BASE/explore/posts/popular.json?date=${enc(date)}&scale=day&page=1&limit=$POOL_SIZE"
            )
            if (list.isEmpty()) throw IOException("Danbooru 的 $date 日榜返回 0 条")
            list
        }
    }

    suspend fun fetchById(id: Long): Result<GalleryPost?> = withContext(Dispatchers.IO) {
        runCatching { requestList("$BASE/posts.json?tags=${enc("id:$id")}").firstOrNull { it.id == id } }
    }

    /**
     * 标签前缀补全。
     *
     * 官方那条 `/tags/autocomplete.json` 在本站版本里**不存在**（实测 404，
     * 而且是被当成 `#show` 的 id 去查、报 `ActiveRecord::RecordNotFound`），
     * 所以走列表端点 + 通配：`search[name_matches]=<词>*`，再按张数排。
     * ⚠️ 参数名少写末尾那个 `s` 会被**静默忽略**（回的是最新建的标签，不是前缀匹配）——
     * 兜底的前缀复检在 [refineGalleryTagSuggestions]，理由写在那里。
     */
    suspend fun searchTags(term: String, limit: Int = GALLERY_TAG_SUGGESTION_LIMIT): Result<List<GalleryTagSuggestion>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE/tags.json?search%5Bname_matches%5D=${enc(term.trim() + "*")}" +
                    "&search%5Border%5D=count&limit=$limit"
                val request = requestBuilder(url).build()
                VeneraNetworkClient.getInstance(appContext).okHttpClient
                    .newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw failure(response.code, body)
                        json.decodeFromString<List<DanbooruTagDto>>(body).map { it.toSuggestion() }
                    }
            }
        }

    /**
     * 按一串标签检索。**空数组是合法结果**（查无此标签，实测 200 + `[]`），
     * 与 [fetchDailyPopular] 那条"空列表当失败"的口径**刻意不同** ——
     * 沿用那边会让用户搜一个冷门标签时得到一句"加载失败"。
     *
     * @param tags 已经拼好预算的标签串（含 `-` 前缀的排除项）。预算判据在 `GallerySearch`，
     *   由 UI 在**加标签那一刻**就把住（匿名只有 [TAG_BUDGET] 枚），这里仍要认站方的 422：
     *   预算哪天改了、或站方收紧了，报文的 `message` 会被人话翻译兜住，不会只剩一句"返回 422"。
     */
    suspend fun searchPosts(tags: String, page: Int, limit: Int = POOL_SIZE): Result<List<GalleryPost>> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestList("$BASE/posts.json?tags=${enc(tags)}&limit=$limit&page=$page")
            }
        }

    private suspend fun requestList(url: String): List<GalleryPost> {
        val client = VeneraNetworkClient.getInstance(appContext).okHttpClient
        client.newCall(requestBuilder(url).build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw failure(response.code, body)
            return json.decodeFromString<List<DanbooruDto>>(body).map { it.toPost() }
        }
    }

    /**
     * 这一站所有请求的公共头。
     *
     * 登录后补一条 `Authorization: Basic`（[DanbooruAccount]）。
     *
     * ⚠️ **它买到的只有"标签预算按等级算"这一件事**，别把它想成"解锁成人内容的钥匙"：
     * 带 `loli` / `shota` 的条目（[CENSORED_TAGS]）**对 Member 与未登录一样封**，
     * 而成人分级（`rating:e`）本来**匿名就看得见**（实测 `cat rating:e` 5/5 都有图）——
     * 所以这句 `Authorization` 对"看不看得到图"没有任何影响。从前这里写着"成人分级靠它拿
     * URL"，那是个错判：把当年 `loli` 那批被抹的成因挂在了分级上。
     * 没登录时一个头都不多加，与从前逐字节一致。
     *
     * 不做"有 Authorization 就绕缓存"那类处理：站方对 API 应答一律回
     * `Cache-Control: no-cache`（实测 `/posts.json` 与 `/profile.json` 都是），
     * OkHttp 因此不会拿旧的匿名应答来糊已登录的请求。
     */
    private fun requestBuilder(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            // 见类注释：浏览器型 UA 会被 Cloudflare 挡成 403 挑战页。
            .header("User-Agent", API_USER_AGENT)
        DanbooruAccount.getInstance(appContext).authHeader()?.let {
            builder.header("Authorization", it)
        }
        return builder
    }

    /**
     * 非 2xx → 一句人话。三条分支都是"别让调用方拿到没头没尾的状态码"：
     * - 403 + 挑战页 = UA 那条规则失效了（比如有人把全局默认 UA 改回浏览器串）；
     * - 站方的错误信封（422 的标签数超限就是这一类）**带着自己的 message**，
     *   翻成中文反而不如把站方原话交出去 —— 它写的就是"一次最多 2 个标签"这种可执行信息；
     * - 其余只剩状态码。
     */
    private fun failure(code: Int, body: String): IOException {
        if (code == 403 && body.contains("cf-mitigated")) {
            return IOException("Danbooru 被 Cloudflare 拦截（需要非浏览器 UA）")
        }
        val message = runCatching { json.decodeFromString<DanbooruErrorDto>(body).message }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        return IOException(if (message != null) "Danbooru：$message" else "Danbooru 返回 $code")
    }

    companion object {
        val SITE: GallerySite = GallerySite.DANBOORU

        /** 与 `ImageHeaderPolicy` 里给 `cdn.donmai.us` 绑的那个 UA **必须同串**。 */
        const val API_USER_AGENT = "Venera/1.0 (Android)"

        /**
         * 日榜一次取多少条。**匿名 `limit` 上限实测就是 200**（传 400 被截成 200）。
         * 取满是为了给"滤掉非图片 + 抽 20 条"留出余地：实测当天 200 条里 `zip` 1 条、
         * 静图 189 条、`mp4` 10 条，抽得到 20 条可摆的。
         * 搜索结果每页也吃这一档（用户 2026-09-25 拍板"Danbooru 200 / yande.re 100"）。
         */
        const val POOL_SIZE = 200

        /**
         * 匿名检索一次最多几枚标签（含 `-` 排除项）。实测第 3 枚直接 **422**：
         * `PostQuery::TagLimitError —— You cannot search for more than 2 tags at a time.`
         * 判据在 `GallerySearch.fitsTagBudget`，UI 在**加标签那一刻**就拦住并报原因。
         *
         * 登录后这个数由账号等级决定，见 [tagsPerSearch]。
         */
        const val TAG_BUDGET = 2

        /**
         * 这个等级一次能搜几枚标签；`null` = 不限。
         *
         * 出处是站方自己的 `help:users` 等级表（2026-09-26 抄录，不是推断）：
         *
         * | 等级 | 值 | 一次最多几枚 |
         * |---|---|---|
         * | 匿名 / Restricted | 0 / 10 | 2 |
         * | Member（免费注册就是这一档） | 20 | **2** |
         * | Gold（付费升级） | 30 | 6 |
         * | Platinum 及以上 / Builder | 31+ | 不限 |
         *
         * ⚠️ 免费注册**不放宽** 2 枚这条 —— 只有 Gold 起才变 6。
         * 所以 UI 在登录之后必须按真实等级说人数，不能笼统写"登录即可搜更多标签"。
         *
         * 另外站方规定 `rating:safe`、`status:deleted`、`-status:deleted`、`limit:200`
         * 这几个元标签**不占额度**；本仓不为它们单开一条通道（用户手里的条件里几乎不会出现），
         * 真撞上了站方会回 422 原话，`failure()` 会照原样转达。
         */
        fun tagsPerSearch(level: Int?): Int? = when {
            level == null -> TAG_BUDGET
            level >= LEVEL_PLATINUM -> null
            level >= LEVEL_GOLD -> 6
            else -> TAG_BUDGET
        }

        /**
         * 站方的**受管制标签**（官方 wiki `help:censored_tags`，2026-09-26 原文抄录）：
         *
         * > Posts with the `loli` and `shota` tags are blocked for **Member-level and logged out
         * > users**. You may view blocked posts by being promoted to a Builder for contributing
         * > to the site, by upgrading to a Gold account (Currently unavailable. See topic #21157),
         * > or by winning a Platinum upgrade raffle.
         *
         * 实测到的形态是"**整整删掉四个键**"，不是给 null：带这两枚的条目，
         * `file_url` / `large_file_url` / `preview_file_url` / `media_asset` 全部消失，
         * 顶层键数从 46 掉到 42 —— 而 `tag_string` 照给，所以这一条**判得出来**
         * （见 [hasCensoredTag]）。对照实验：`cat rating:e` 匿名取回 5/5 都有图，
         * 所以**被挡的不是分级**，是标签本身（`loli rating:s` 同样 0 张有图）。
         *
         * 三条会踩空的推论，写在这里免得再走一遍：
         * - **登录不解决这件事**。门槛是 Gold(30)，免费注册只到 Member(20) ——
         *   官方那张表里 "View censored tags" 一行，Member 与未登录同样是 ✗。
         * - **换搜索词绕不过**。标签长在那张图上，搜什么都不会改变它被管制。
         * - `lolicon` / `shotacon` 是**空标签**（实测 post_count：前者 0，后者也 0），
         *   搜它们命中的正是带 `loli` / `shota` 的那批（站方标签蕴含），一样取不到图。
         */
        val CENSORED_TAGS = setOf("loli", "shota")

        /**
         * 这一档账号能不能看受管制标签的条目。
         *
         * 门槛与"一次 6 枚标签"同一档（Gold = 30）：官方表里 `View censored tags` 从 Gold 起是 ✓，
         * Builder(32) / Moderator(40) 自然也过。**未登录与 Member 都是 false**。
         */
        fun canViewCensoredTags(level: Int?): Boolean = (level ?: 0) >= LEVEL_GOLD

        /**
         * 这一条是不是带着受管制标签。
         *
         * 只看条目自己的标签，不看"有没有图" —— 在站方那里这两件事是同一件
         * （被挡 ⇒ 四个 URL 键一起消失），而标签串**被挡时也照给**，所以判据只能也只用标签。
         * 逐字比对而不做前缀匹配：`loli_(genshin_impact)` 是另一枚标签，站方不管它。
         */
        fun hasCensoredTag(post: GalleryPost): Boolean = post.tagList.any { it in CENSORED_TAGS }

        /** Gold 那一档（站方 `User::Levels::Gold`）。 */
        private const val LEVEL_GOLD = 30

        /** Platinum 那一档 —— 从它起标签数不限。 */
        private const val LEVEL_PLATINUM = 31

        private const val BASE = "https://danbooru.donmai.us"

        private val json = Json { ignoreUnknownKeys = true }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        @Volatile
        private var INSTANCE: DanbooruClient? = null

        fun getInstance(context: Context): DanbooruClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: DanbooruClient(context.applicationContext).also { INSTANCE = it }
        }
    }
}

/**
 * Danbooru 的一条 post 的**站方原始字段名**。
 *
 * 与 yande.re 几乎全不同名：中间档叫 `large_file_url`、尺寸叫 `image_width/height`、
 * 标签叫 `tag_string`、**画师没有独立字段**（只有 `tag_string_artist` 这一串），
 * 而且**有** `fav_count`。档位宽高在 `media_asset.variants[]` 里，不在顶层。
 */
@Serializable
internal data class DanbooruDto(
    val id: Long,
    @SerialName("tag_string") val tagString: String = "",
    val rating: String = "",
    val score: Int = 0,
    @SerialName("fav_count") val favCount: Int = 0,
    @SerialName("tag_string_artist") val tagStringArtist: String = "",
    /**
     * 分类桶。实测这一站**五串全给**（`tag_string` 是它们的合集），
     * 而 yande.re 那边一个都没有 —— 所以分桶只在本站翻得出来，见 [GalleryPost.tagGroups]。
     */
    @SerialName("tag_string_general") val tagStringGeneral: String = "",
    @SerialName("tag_string_character") val tagStringCharacter: String = "",
    @SerialName("tag_string_copyright") val tagStringCopyright: String = "",
    @SerialName("tag_string_meta") val tagStringMeta: String = "",
    val source: String = "",
    @SerialName("file_ext") val fileExt: String = "",
    @SerialName("image_width") val imageWidth: Int = 0,
    @SerialName("image_height") val imageHeight: Int = 0,
    @SerialName("preview_file_url") val previewFileUrl: String = "",
    @SerialName("large_file_url") val largeFileUrl: String = "",
    @SerialName("file_url") val fileUrl: String = "",
    @SerialName("file_size") val fileSize: Long = 0,
    val md5: String = "",
    @SerialName("media_asset") val mediaAsset: MediaAssetDto? = null,
) {
    fun toPost(): GalleryPost {
        val isVideo = fileExt.lowercase() in GalleryPost.VIDEO_EXTS
        // sample 档是站方给"看这张但不用原图"的那一档；小图没有 sample，此时退回 large_file_url。
        val sample = mediaAsset?.variants?.firstOrNull { it.type == "sample" }
        // 视频**没有** sample 档，而 `large_file_url` 就是原片 mp4（实测 has_large=false、两串完全一样）。
        // 中间档改取 `720x720` 那张静帧，否则图片链路会把 .mp4 当图喂给 Coil。
        val stillFrame = mediaAsset?.variants?.firstOrNull { it.type == "720x720" }
        return GalleryPost(
            site = GallerySite.DANBOORU,
            id = id,
            tags = tagString,
            rating = rating,
            score = score,
            favCount = favCount,
            author = tagStringArtist.trim().split(' ').firstOrNull().orEmpty(),
            source = source,
            width = imageWidth,
            height = imageHeight,
            previewUrl = previewFileUrl,
            previewWidth = mediaAsset?.variants?.firstOrNull { it.type == "180x180" }?.width ?: 0,
            previewHeight = mediaAsset?.variants?.firstOrNull { it.type == "180x180" }?.height ?: 0,
            // 大图页开门那一档。**不能拿 preview_file_url 顶**：实测它只有 121×180（9.3 KB），
            // 铺满一屏是 9 倍放大。站方的 `360x360`（242×360 / 28.8 KB）同样秒出，清楚一倍。
            // 站方变体表实测 59/60 条带它（缺的那条连整张 variants 都是空的，是被删条目），
            // 所以两级兜底，取不到就交空串由 UI 降级 —— 不编地址。
            fastUrl = mediaAsset?.variants?.firstOrNull { it.type == "360x360" }?.url
                ?: previewFileUrl,
            largeUrl = if (isVideo) stillFrame?.url ?: previewFileUrl else sample?.url ?: largeFileUrl,
            largeWidth = if (isVideo) stillFrame?.width ?: imageWidth else sample?.width ?: imageWidth,
            largeHeight = if (isVideo) stillFrame?.height ?: imageHeight else sample?.height ?: imageHeight,
            fileUrl = fileUrl,
            fileSize = fileSize,
            md5 = md5,
            fileExt = fileExt,
            durationSeconds = mediaAsset?.duration?.takeIf { isVideo },
            // 桶序固定按"人最常看的在前"，与站方给的五串一一对应；空桶由 galleryTagGroup 剔掉。
            tagGroups = listOfNotNull(
                galleryTagGroup("通用", tagStringGeneral),
                galleryTagGroup("画师", tagStringArtist),
                galleryTagGroup("角色", tagStringCharacter),
                galleryTagGroup("作品", tagStringCopyright),
                galleryTagGroup("元数据", tagStringMeta),
            ),
        )
    }
}

@Serializable
internal data class MediaAssetDto(
    /**
     * 视频时长（秒）。**只在这里有** —— 实测顶层没有 `duration` 键（方案 §13.1 那张字段表），
     * 图片条目这里是 null。
     */
    val duration: Double? = null,
    val variants: List<VariantDto> = emptyList(),
)

@Serializable
internal data class VariantDto(
    val type: String = "",
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * 站方的错误信封（实测 422 标签数超限就是这个形状，`backtrace` 不接）。
 *
 * 只取 `message` 那一支：它写的就是可执行信息（"You cannot search for more than 2 tags at a time."），
 * 我们自己再编一句中文只会比它更不准 —— 而且哪天站方改了预算，中文那句就成假读数了。
 */
@Serializable
internal data class DanbooruErrorDto(
    val message: String = "",
    val error: String = "",
)
