package com.venera.compose.gallery.data

import android.content.Context
import android.util.LruCache
import com.venera.compose.data.network.NoInteractiveBypassTag
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.gallery.domain.GalleryArtistAlias
import com.venera.compose.gallery.domain.GalleryArtistRecord
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * yande.re 的读侧客户端：**直接打站方官方 JSON API**，匿名、无鉴权、不走 JS 源脚本。
 *
 * 四个取法，都实测过（日榜与单条的坑见下；搜索面全表在 `gallery-search-2026-09.md` §〇）：
 *
 * - [fetchDailyPopular] = 用户点名的 `/post/popular_recent?period=1d` 的 JSON 版。实测
 *   **固定 40 条**、`limit`/`page` 被忽略、不认识的 `period` 值静默退回 `1d`；
 *   并发 8 路、串行 25 次全 200，没看到限流形态。
 * - [fetchById] **必须**走 `tags=id:N`：`/post/{id}.json` 是 404，而 `?id=N` 会被站方
 *   **静默忽略**、返回一整页默认列表（状态还是 200）—— 用后者会把"看这一张"
 *   变成"看一屏无关的图"。这是最容易写错、错了又不报错的坑。
 *
 * 分级只有 `s`/`q`/`e` 三档（本站没有"一般"那一档 —— Gelbooru 那边是四个单词、
 * 而且字形完全不同，见 [GalleryPost.SAFE_RATINGS] 的归一处理）；
 * `webm` 条目的 `jpeg_url` 就是它的静帧，所以本站翻译完不需要为视频特殊处理。
 *
 * 另外两条实测结论留在这里，省得下一个人重新踩：`/post/hot.json` 在本站 **404**；
 * `/post/popular_by_month.json` 可读但**固定 40 条、忽略 limit、没有 page**；
 * 字段表里**没有 `fav_count`**（44 个键里没有）。
 */
class YandeReClient private constructor(context: Context) {

    private val appContext = context.applicationContext

    /**
     * 同一个画师名只解析一次。存的是"站方给出的正名"这一条判定，几十条字符串，64 条足够
     * （口径照 [GalleryTagCategories] 的按条数缓存）。
     *
     * **只有解析成功才入缓存**：没有别名指针的那一类不存（站方的画师页是可以改的，
     * 今天没有别名不代表以后没有），失败更不存（那一档下一次照样真发）。
     */
    private val aliasCache = LruCache<String, String>(ALIAS_CACHE_SIZE)

    /**
     * 取**上一天**的热门（用户点名的 `https://yande.re/post/popular_recent?period=1d` 的 JSON 版）。
     *
     * 实测（2026-09-25）：`period=1d` → 200 / **固定 40 条**；`limit` 与 `page` 被忽略；
     * 不认识的 `period` 值（如 `1mo`）**静默退回 `1d`**（实测 40/40 完全同一批），
     * 而 `1w` 是真生效的另一批（与 `1d` 只重叠 2 条）。另有一份 `popular_by_day.json`（32 条），
     * 与 `period=1d` **零重叠** —— 那是另一套算法，别混用。
     *
     * 失败一律 `Result.failure` 把原因带出去；**空列表不算成功**（否则"网络炸了"会长成"今天没有热门"）。
     */
    suspend fun fetchDailyPopular(): Result<List<GalleryPost>> = withContext(Dispatchers.IO) {
        runCatching {
            val list = requestList("$BASE/post/popular_recent.json?period=1d")
            if (list.isEmpty()) throw IOException("yande.re 的日榜返回 0 条")
            list
        }
    }

    /**
     * 取单条。**必须**走 `tags=id:N`：`/post/{id}.json` 是 404，而 `?id=N` 被忽略并回一整页
     * 别人的图，状态还是 200 —— 用后者会静默把"看这一张"变成"看一屏无关的图"。
     */
    suspend fun fetchById(id: Long): Result<GalleryPost?> = withContext(Dispatchers.IO) {
        runCatching { requestList("$BASE/post.json?tags=${enc("id:$id")}").firstOrNull { it.id == id } }
    }

    /**
     * 标签前缀补全。
     *
     * 官方那条 `/tag/auto_complete.json` 在本站**404**（`term` 与 `query` 两种参数名都试过），
     * 所以走列表端点 + 通配：`name=<词>*&order=count`。
     * ⚠️ 两条实测坑：`search[name]=词` 会被**静默忽略**（回一条空名标签加一串不相干的），
     * `name=~词*` 又回空数组 —— 唯一可用的是 `name=词*` 这一种写法，
     * 而且拿回来还要按输入复检一次前缀（[refineGalleryTagSuggestions]）。
     */
    suspend fun searchTags(term: String, limit: Int = GALLERY_TAG_SUGGESTION_LIMIT): Result<List<GalleryTagSuggestion>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$BASE/tag.json?name=${enc(term.trim() + "*")}&order=count&limit=$limit")
                    .header("Accept", "application/json")
                    // 补全也是"用户在等"的流量，同样不弹过盾窗口。
                    .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
                    .build()
                VeneraNetworkClient.getInstance(appContext).okHttpClient
                    .newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IOException("yande.re 返回 ${response.code}")
                        json.decodeFromString<List<YandeReTagDto>>(body).map { it.toSuggestion() }
                    }
            }
        }

    /**
     * 按一串标签检索。**空数组是合法结果**（查无此标签，实测 200 + `[]`），
     * 与 [fetchDailyPopular] 那条"空列表当失败"的口径刻意不同。
     *
     * 实测：`page` 真翻页（`tags=hime` 第 1/2 页条目不同）、`-负向标签` 有效、
     * `limit` 天花板 320（本页取 [SEARCH_PAGE_SIZE]）。
     * ⚠️ `rating:` 只认本站那三档 `s/q/e`（实测 `rating:explicit` 有效），
     * 而**不认识的档位值会被静默忽略**（`rating:general` 回的是全量）——
     * 这条就是搜索页不摆分级筛选的原因，见 `gallery-search-2026-09.md` §四。
     */
    suspend fun searchPosts(tags: String, page: Int, limit: Int = SEARCH_PAGE_SIZE): Result<List<GalleryPost>> =
        withContext(Dispatchers.IO) {
            runCatching {
                requestList("$BASE/post.json?tags=${enc(tags)}&limit=$limit&page=$page")
            }
        }

    /**
     * 把一个画师别名解析成**站方记的正名**（批次 J，判据在
     * [com.venera.compose.gallery.domain.GalleryArtistAlias]，这里只做两笔取数）。
     *
     * 实测的解析链（`gallery-artist-alias-2026-09.md` §1.4，抽样 16 条 `alias_id` 全部跟到落点）：
     *
     * ```
     * GET /artist.json?name=setmen   → [{id 39892, name setmen, alias_id 46523}]
     * GET /artist/show/46523         → 302 → /wiki/show?title=tokenbox（匿名可读）
     * ```
     *
     * 正名从**落点 URL 的 `title` 参数**取，不解析 HTML 正文：那页是给用户看的，
     * 版面一变这里就悄悄读到别的东西，而 `title` 是那条 302 的语义本体。
     *
     * 两条站方坑都写进判据、不靠这里兜：
     * - `name=` 是**前缀匹配**（实测 `name=se` 回 16 条）→ 必须按全等取记录；
     * - `artist.json` 的 `id=` / `ids=` / `show=` 与 `tag_alias.json` 的 `name=` 一类
     *   **全部被静默忽略**（回默认列表且 200）→ 除了 `name=` 前缀这一条没有更短的路子。
     *
     * 返回 `Result<String?>`：**null = 站方没给出别名关联**（取不到全等记录、或那条没有 `alias_id`），
     * 那是正常答复，不是失败；而 404 / 非 JSON / 挂维护页一律走 `failure` ——
     * 把"站方没答上"与"这一路走不通"混成 null 就是本仓最忌的静默降级。
     */
    suspend fun resolveArtistAlias(name: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            aliasCache.get(name)?.let { return@runCatching it }
            val records = requestArtistRecords("$BASE/artist.json?name=${enc(name)}")
            val record = GalleryArtistAlias.exactRecord(records, name) ?: return@runCatching null
            val canonical = GalleryArtistAlias.canonicalName(
                requestRedirectTitle("$BASE/artist/show/${record.aliasId}"),
                name,
            )
            // 只把"解析到了"存进缓存：没有别名指针、或指针指回自己那一类不存（同一个名字以后
            // 可能改判），也不存失败 —— 失败那一档下一次照样真发。
            canonical?.also { aliasCache.put(name, it) }
        }
    }

    /**
     * 那位画师在站方记下的**外链地址**（批次 L：摆平台图标、取 pixiv 头像都从它来）。
     *
     * 与 [resolveArtistAlias] 打的是同一个端点、同一份记录，只是取的是另一个字段 ——
     * 所以这里的失败口径也照它：**站方没这条记录 = 空表（合法答复）**，
     * 404 / 非 JSON / 形态对不上 = `failure`（UI 什么都不摆，但日志里查得到原因）。
     */
    suspend fun artistLinks(name: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val records = requestArtistRecords("$BASE/artist.json?name=${enc(name)}")
            // 这里要的是**记录本身**，不是"带别名指针的那条"：站方给正名记录的 alias_id 恒为 null，
            // 用 [GalleryArtistAlias.exactRecord] 会把所有正名画师都判成"没这条记录"（真机踩过）。
            GalleryArtistAlias.recordByName(records, name)?.urls.orEmpty()
        }
    }

    private fun requestArtistRecords(url: String): List<GalleryArtistRecord> {
        val body = executeForJson(url)
        // 站方这里回的是数组；`{}` 或维护页那种非数组形态会被 `decodeFromString` 炸出来，
        // 不退化成"没有别名"（那条口径与 [requestList] 里"200 但不是 JSON"完全一致）。
        return json.decodeFromString<List<YandeReArtistDto>>(body).map { it.toRecord() }
    }

    /** 跟一次重定向，只读**落点 URL** 上的 `title`。正文一个字节都不解析。 */
    private fun requestRedirectTitle(url: String): String? {
        val client = VeneraNetworkClient.getInstance(appContext).okHttpClient
        val request = Request.Builder()
            .url(url)
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("yande.re 返回 ${response.code}")
            // OkHttp 默认跟重定向，`request.url` 就是最终落点：不是 wiki 那一页就说明这条指针没用
            // （登录页、维护页都长这样），交回 null 让调用方保持 0 结果，不猜。
            return response.request.url.queryParameter("title")
        }
    }

    private fun executeForJson(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        val client = VeneraNetworkClient.getInstance(appContext).okHttpClient
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("yande.re 返回 ${response.code}")
            return body
        }
    }

    private suspend fun requestList(url: String): List<GalleryPost> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            // 与 Gelbooru 同一条理由：日榜是"整屏等结果"的流量，不能弹过盾窗口
            // （那个 await 没有超时，挂住就是永久转圈）。撞盾拿回 403，下面按失败报一句话。
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        // 与漫画侧共用连接池 / Cookie / CF 过盾（方案 §二"复用基础设施"那一列）。
        val client = VeneraNetworkClient.getInstance(appContext).okHttpClient
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("yande.re 返回 ${response.code}")
            // 200 但不是 JSON（挂维护页、被换盾）同样炸出来，不退化成"没有图"。
            return json.decodeFromString<List<YandeReDto>>(body).map { it.toPost() }
        }
    }

    companion object {
        /** 站点身份统一走 [GallerySite]，这里只留"这个客户端是谁"给日志用。 */
        val SITE: GallerySite = GallerySite.YANDERE

        /**
         * 搜索结果每页取多少张（用户 2026-09-25 拍板"两站各按自己的天花板来"）。
         * 站方 `limit` 天花板实测 320，100 够一屏半，又不至于让一次翻页解 100 张 preview。
         *
         * （另一站是 Gelbooru，它的每页上限是 **100**（官方 wiki 写明硬上限），
         * 见 `GelbooruClient.POOL_SIZE` —— 两站的数不一样，别互相照抄。）
         *
         * （这里原先挂着一个 `PAGE_SIZE = 30`，是落地流还"两站交错"那版的遗留，
         * 日榜改成一屏到底之后**零调用点** —— 顺手清掉，不留没人读的常量。）
         */
        const val SEARCH_PAGE_SIZE = 100

        private const val BASE = "https://yande.re"

        /** 画师别名解析的按条数缓存上限（口径同 [GalleryTagCategories]）。 */
        private const val ALIAS_CACHE_SIZE = 64

        private val json = Json { ignoreUnknownKeys = true }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        @Volatile
        private var INSTANCE: YandeReClient? = null

        fun getInstance(context: Context): YandeReClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: YandeReClient(context.applicationContext).also { INSTANCE = it }
        }
    }
}

/**
 * yande.re 的一条 post 的**站方原始字段名**。只在这里出现，翻译完全站名的活儿归 [GalleryPost]。
 *
 * 站方**没有** `large_file_url`，中间档叫 `jpeg_url`；其余三十多个字段
 * （`frames` / `is_note_locked` / `approver_id` …）在这里没有消费点，不接进来。
 */
@Serializable
internal data class YandeReDto(
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
     * 站方**真正的中档**（长边 ≤1500）。原图比 sample 还小的时候站方把它回落成
     * `/image/` 原图 —— 实测 70/70 条非空，所以不需要"没有 sample 就退到哪一档"的兜底。
     */
    @SerialName("sample_url") val sampleUrl: String = "",
    @SerialName("sample_width") val sampleWidth: Int = 0,
    @SerialName("sample_height") val sampleHeight: Int = 0,
    /**
     * 原分辨率的 JPEG 重编码 —— **刻意不用**。
     *
     * 旧版把它当中档（"二级大图吃 jpeg_url"），因为 `jpeg_width/height` 看起来像一档尺寸；
     * 但实测 30/30 条它**恒等于** `width/height`，也就是说它不是降档而是"原图换了个容器"
     * （同一份池子里有 9600×5400、10240×5760 的条目，单条 3 MB 级）。
     * 拿它当中档 = 每次打开都要先下一个原图大小的文件。
     * 真正的中档是 [sampleUrl]（209 KB / 1500×1060），原档那一格仍归 `file_url`。
     * 这里留着它只剩一个用途：sample 万一为空时兜底（见 `toPost`），
     * 顺手也让下次想"用 jpeg_url 当中档"的人先读到这段实测。
     */
    @SerialName("jpeg_url") val jpegUrl: String = "",
    @SerialName("jpeg_width") val jpegWidth: Int = 0,
    @SerialName("jpeg_height") val jpegHeight: Int = 0,
    @SerialName("file_url") val fileUrl: String = "",
    @SerialName("file_size") val fileSize: Long = 0,
    val md5: String = "",
) {
    fun toPost(): GalleryPost = GalleryPost(
        site = GallerySite.YANDERE,
        id = id,
        tags = tags,
        rating = rating,
        score = score,
        // 站方 JSON 没有这个字段（实测）→ null，不是 0。
        favCount = null,
        author = author,
        source = source,
        width = width,
        height = height,
        previewUrl = previewUrl,
        previewWidth = actualPreviewWidth,
        previewHeight = actualPreviewHeight,
        // 开门那一档就用 `preview_url`（300×212 / 13.7 KB）：本站没有比它更"中间"的一档，
        // 而且它**与网格卡片是同一个地址** —— 打开大图页时磁盘/内存缓存直接命中，等于白拿。
        fastUrl = previewUrl,
        // 中档必须是 `sample_url`，不能是 `jpeg_url`（理由见 DTO 里那段实测）。
        // 空样本兜底到 jpeg_url：实测 70/70 条非空、这一支按理不会走到，
        // 但万一站方某条没给 sample，退到同一张画的另一个**真地址**远好过交一个空串
        // （空串在 UI 里只会变成"停留在 300 像素的开门档上，还没有任何提示"）。
        largeUrl = sampleUrl.ifBlank { jpegUrl },
        largeWidth = if (sampleUrl.isNotBlank()) sampleWidth else jpegWidth,
        largeHeight = if (sampleUrl.isNotBlank()) sampleHeight else jpegHeight,
        fileUrl = fileUrl,
        fileSize = fileSize,
        md5 = md5,
        fileExt = fileExt,
    )
}

/**
 * yande.re `artist.json` 的一条画师记录。这里只接**解析链与批次 L 那排图标要用的四个键**
 * （其余 `name_japanese` / `related_tags` / `is_active` / `created_at` 等没有消费点）。
 *
 * `alias_id` 站方给的是 `null` 或整个键缺失都有（实测记录里两种形态并存），
 * 所以它是可空带默认值 —— 缺键与空值在这里是同一件事："这个名字本身就是正名"。
 */
@Serializable
internal data class YandeReArtistDto(
    val id: Long,
    val name: String = "",
    @SerialName("alias_id") val aliasId: Long? = null,
    /**
     * 这位画师的外链地址（批次 L 摆平台图标、取 pixiv 头像都用它）。
     *
     * 实测是**字符串数组**，所以这里就按数组接 —— 站方哪天给成别的样子，`decodeFromString`
     * 会炸出来、整条链路算失败（UI 什么都不摆），而不是悄悄吞成"这位没有外链"。
     * 那正是 [GalleryArtistEndpointParse] 对 danbooru 那一侧押不了形态时用的同一条口径。
     */
    val urls: List<String> = emptyList(),
) {
    fun toRecord(): GalleryArtistRecord = GalleryArtistRecord(id = id, name = name, aliasId = aliasId, urls = urls)
}
