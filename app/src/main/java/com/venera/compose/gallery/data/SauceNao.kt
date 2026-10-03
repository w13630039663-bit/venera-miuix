package com.venera.compose.gallery.data

import com.venera.compose.data.platform.UserAgentStrings
import com.venera.compose.data.platform.HttpEngine
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 一条 SauceNAO 命中。
 *
 * [siteRef] 是这一屏存在的理由：SauceNAO 的 `data` 里带 `yandere_id` / `gelbooru_id`，
 * 对上就是我们自己的两站 —— 于是"以图搜图"的结果能**直接开我们的大图页**（收藏、
 * 屏蔽规则、防盗链、混淆全都现成），而不是只把用户丢给一个外部网页。
 * 对不上（pixiv / twitter / 番剧截图 / danbooru）时它是 null，那一行退化成"外链 + 作者"。
 */
data class SauceNaoHit(
    /** 0..100 的相似度整数。SauceNAO 给的是字符串（"95.29"），这里已经取整。 */
    val similarity: Int,
    val thumbnailUrl: String,
    val title: String?,
    val artistName: String?,
    /** 站方给的外部链接（已含 source 字段里那条，去重后）。 */
    val extUrls: List<String>,
    /** 命中的库名（`index_name`，如 `danbooru2023` / `materialize`）—— 念出来才知道像的是哪一路。 */
    val indexName: String,
    /** 视频帧的"第几段"（`(3/3)` 这种）；静态图为 null。 */
    val part: String?,
    val siteRef: GallerySiteRef?,
)

/** 命中落在我们已支持的那一站上的哪一个 id。 */
data class GallerySiteRef(val site: GallerySite, val id: Long)

/**
 * 一次反搜的整页结果。
 *
 * [status] 原样留着：出错时页面上要念得出**站方自己那句** `message`，
 * 而不是我们替它编一个"搜索失败"。
 */
data class SauceNaoPage(
    val status: Int,
    val message: String?,
    val resultsReturned: Int,
    val totalMatches: Int,
    val hits: List<SauceNaoHit>,
)

class SauceNaoException(reason: String) : Exception(reason)

private val SAUCE_JSON = Json { ignoreUnknownKeys = true }

/**
 * 把 SauceNAO 的 `output_type=2` 响应解成 [SauceNaoPage]。**纯函数，不碰网络。**
 *
 * 走 kotlinx 的动态 JSON 树而不是 `org.json`：后者在 Android 上是 stub，
 * 纯 JVM 单测里一调就抛 "not mocked"，那样这条解析就永远锁不住。
 *
 * ## 三处刻意的判断
 *
 * 1. **status ≠ 0 一律抛错**，但错误文案优先用站方给的 `message`。
 *    其中 status `-2`（站方语义"没找到"）**不抛**，交回一张空 hits 的页 ——
 *    搜不出相似图是一个**答案**，不是一次故障，页面要能把它和故障分开说。
 * 2. **不丢"没有作者"的命中**（Breadboard 丢了，见其 `SauceNao.kt` 里那句 TODO）。
 *    它那一屏是"按图找画师"，没有作者就等于没结果；我们这一屏是"按图找这张图"，
 *    一条带 `yandere_id` 的命中即使没有作者也照样能点开、能收藏。丢了反而把
 *    最值钱的那类结果筛掉。
 * 3. `member_name` → `author_name` → `creator` → `twitter_user_handle` 依次兜底，
 *    且**值可能是个 JSON 数组的字面量串**（`"[\"xxx\"]"`）—— 站方自己就不统一，
 *    这里两种都吃。
 *
 * @param allowNsfw false 时跳过站方标了 `hidden` 的那几条（与请求里的 `hide` 参数是**两道**，
 *   一道在站方、一道在这里，缺一道就会漏进不该露出来的图）。
 */
internal fun parseSauceNao(body: String, allowNsfw: Boolean): SauceNaoPage {
    val root = runCatching { SAUCE_JSON.parseToJsonElement(body).jsonObject }
        .getOrElse { throw SauceNaoException("SauceNAO 回的不是能解析的 JSON（可能被 Cloudflare 拦了）") }

    val header = root["header"] as? JsonObject ?: JsonObject(emptyMap())
    val status = header.intOrNull("status") ?: 0
    val message = header.strOrNull("message")

    if (status != 0 && status != NO_RESULTS_STATUS) {
        throw SauceNaoException(message ?: "SauceNAO 返回状态码 $status")
    }

    val returned = header.intOrNull("results_returned") ?: 0
    val total = header.intOrNull("total_matches") ?: 0

    if (status == NO_RESULTS_STATUS) {
        return SauceNaoPage(status, message, 0, 0, emptyList())
    }

    val results = root["results"] as? JsonArray ?: JsonArray(emptyList())
    val hits = ArrayList<SauceNaoHit>(results.size)

    for (element in results) {
        val row = element as? JsonObject ?: continue
        val resultHeader = row["header"] as? JsonObject ?: continue
        val data = row["data"] as? JsonObject ?: continue

        if (!allowNsfw && (resultHeader.intOrNull("hidden") ?: 0) != 0) continue

        val similarity = resultHeader.strOrNull("similarity")?.toFloatOrNull()?.toInt() ?: 0
        val thumbnail = resultHeader.strOrNull("thumbnail").orEmpty()
        val indexName = resultHeader.strOrNull("index_name").orEmpty()

        /*
         * 外链的顺序**是给用户看的**，不能按站方给的先后排。
         *
         * ⚠️ 2026-09-27 真机反馈「打开来源全部跳到 i.pximg.net」：
         * 旧写法先加 `source` 再加 `ext_urls`，而 SauceNAO 对 pixiv 库的 `source`
         * 常常是**图片直链**（`https://i.pximg.net/img-original/...jpg`），
         * `ext_urls` 里才是作品页（`https://www.pixiv.net/artworks/123`）。
         * 结果"打开来源"取 firstOrNull 拿到的是直链 —— 点下去是一张裸图，
         * 而用户要的是那个作品页（有作者、有标签、能关注）。
         *
         * 所以：**`ext_urls` 在前（作品页为主），`source` 只当补充放后面**。
         * 两者仍去重（同一条会塌成一份）。
         */
        val source = data.strOrNull("source")
        val extUrls = LinkedHashSet<String>()
        (data["ext_urls"] as? JsonArray)?.forEach { url ->
            url.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }?.let { extUrls.add(it) }
        }
        source?.let { if (it.isLikelyWebLink()) extUrls.add(it) }

        val artist = data.strOrNull("member_name")
            ?: data.strOrNull("author_name")
            ?: data.strOrNull("creator")
            ?: data.strOrNull("twitter_user_handle")
            ?.let { if (it.startsWith("[\"")) jsonArrayLiteralFirst(it) else it }

        val ref = listOf(
            GallerySite.YANDERE to data.longOrNull("yandere_id"),
            GallerySite.GELBOORU to data.longOrNull("gelbooru_id"),
            GallerySite.SAFEBOORU to data.longOrNull("safebooru_id"),
        ).firstOrNull { it.second != null && it.second!! > 0L }
            ?.let { GallerySiteRef(it.first, it.second!!) }

        hits.add(
            SauceNaoHit(
                similarity = similarity,
                thumbnailUrl = thumbnail,
                title = data.strOrNull("title"),
                artistName = artist?.takeIf { it.isNotBlank() },
                extUrls = extUrls.toList(),
                indexName = indexName,
                part = data.strOrNull("pair"),
                siteRef = ref,
            )
        )
    }

    return SauceNaoPage(status, message, returned, total, hits)
}

/** 站方语义"没找到相似图"。这是一次有效回答，不是故障。 */
private const val NO_RESULTS_STATUS = -2

/**
 * 只判"像不像一条链接"，不做完整 URL 解析 —— 站方偶尔在 `source` 里塞
 * 画师主页、塞文件名、塞空串，解析成 URI 反而会把文件名误判成相对链接。
 */
private fun String.isLikelyWebLink(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

/** `"[\"xxx\",\"yyy\"]"` → `xxx`；解不动就原样交回，不为这一格把整条命中丢掉。 */
private fun jsonArrayLiteralFirst(raw: String): String =
    runCatching { SAUCE_JSON.parseToJsonElement(raw).jsonArray.first().jsonPrimitive.contentOrNull }
        .getOrNull() ?: raw

/**
 * 三个取值器共用一条规矩：**值可能是任何形状**。
 *
 * 站方同一字段在不同库里给过字符串、给过数字、给过 `null`，也给过整个对象
 * （`jsonPrimitive` 撞上对象会直接抛）。一条命中的形状不对，代价应该是这一格空着，
 * 而不是整次搜索在解析中途炸掉 —— 所以这里一律 runCatching 换 null。
 */
private fun JsonObject.strOrNull(key: String): String? {
    val value = this[key] ?: return null
    if (value is JsonNull) return null
    val text = runCatching { value.jsonPrimitive.contentOrNull }.getOrNull()
    return text?.takeIf { it.isNotBlank() }
}

private fun JsonObject.intOrNull(key: String): Int? {
    val value = this[key] ?: return null
    if (value is JsonNull) return null
    val text = runCatching { value.jsonPrimitive.contentOrNull }.getOrNull() ?: return null
    // 站方同一字段有时给数字、有时给字符串数字，两种都吃。
    return text.trim().toIntOrNull()
}

private fun JsonObject.longOrNull(key: String): Long? {
    val value = this[key] ?: return null
    if (value is JsonNull) return null
    val text = runCatching { value.jsonPrimitive.contentOrNull }.getOrNull() ?: return null
    return text.trim().toLongOrNull()
}

/**
 * 这一条外链**是不是一张图片直链**（而不是"作品页"）。
 *
 * 用户点「打开来源」要的是那个作品页（有作者、有标签、能关注），不是一张裸图 ——
 * SauceNAO 的 `source` / `ext_urls` 里两种都混着给（pixiv 库尤其如此：
 * `source` 常是 `i.pximg.net/...jpg` 直链，`ext_urls` 才是 `pixiv.net/artworks/…`）。
 *
 * 判据收敛为两条，够了就停：
 * 1. 路径带常见图片扩展名（jpg/png/webp/gif/jpeg）；
 * 2. host 是已知的纯图床（`i.pximg.net` 这类，它只托管字节、没有页面）。
 *
 * ⚠️ 不做"猜域名"那一类扩展：认不出的**一律当作品页**（宁可跳错一个页面，
 * 也不要把用户想看的页面藏起来）。
 */
internal fun isDirectImageUrl(url: String): Boolean {
    val lower = url.lowercase()
    val path = lower.substringAfter("://", "").substringAfter("/", "")
    val beforeQuery = path.substringBefore("?").substringBefore("#")
    if (IMAGE_EXT_SUFFIX.any { beforeQuery.endsWith(it) }) return true
    val host = lower.substringAfter("://", "").substringBefore("/").substringBefore("?")
    return host in IMAGE_ONLY_HOSTS
}

private val IMAGE_EXT_SUFFIX = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

/** 只托管字节、没有对应页面的图床。 */
private val IMAGE_ONLY_HOSTS = setOf(
    "i.pximg.net",
    "img.pximg.net",
    "pbs.twimg.com",
)

/**
 * 「打开来源」该用哪一条外链：**优先作品页，实在没有才退回直链**。
 *
 * 见 [isDirectImageUrl] 那段说明 —— 直接取 firstOrNull 会拿到 pximg 直链，
 * 正是 2026-09-27 那条真机反馈。
 */
internal fun preferredExternalUrl(extUrls: List<String>): String? =
    extUrls.firstOrNull { !isDirectImageUrl(it) } ?: extUrls.firstOrNull()

/** 反搜只要一枚可选的 key，所以这里只给这一枚（与 [GelbooruCredentials] 同一手法，理由也相同）。 */
interface SauceNaoCredentials {
    val apiKey: String
}


/**
 * SauceNAO 读侧客户端。两种喂法，同一个解析出口：
 *
 * - [searchByUrl] —— 走 query 参数 `url=`，站方自己去抓图。**我们这一屏的主路**，
 *   因为画廊里的图本来就有个能直接给的 URL；
 * - [searchByFile] —— 走 multipart `file=`，用户从本机挑的图。
 *
 * ## 三条刻意的取舍
 *
 * - **key 实际上必需，但闸门不在这里**：调用方（[com.venera.compose.gallery.ui.GalleryReverseViewModel.submit]）
 *   没 key 就不发。2026-09-27 实测匿名请求**过盾能成功**（`onBypassSuccess`、存下 cf_clearance、
 *   重试），**重试仍然 403** —— 所以"没 key 照发"这条从前写在这里的说法是错的，已推翻。
 *   本类仍按"有 key 才拼参数"实现，是为了让站方那句 message 有机会原样念出来，
 *   而不是我们在本地替它编一句"额度不够"。
 * - **允许弹过盾**（不打 [NoInteractiveBypassTag]，与日榜那一路相反）：saucenao.com 实测在
 *   Cloudflare 交互挑战后面，豁免过盾就等于这个功能永远 403。理由与风险见 [request] 那段。
 * - `hide` 与解析期的 hidden 过滤**两道都要**（见 [parseSauceNao] 第 3 点）。
 */
class SauceNaoClient internal constructor(
    private val engine: HttpEngine,
    private val credentials: SauceNaoCredentials,
) {

    /** 用一张已经在网上的图去反搜。 */
    suspend fun searchByUrl(imageUrl: String, allowNsfw: Boolean): SauceNaoPage {
        val url = "$API_URL?output_type=2" +
            "&hide=${if (allowNsfw) 0 else 3}" +
            "&numres=$SAUCE_NUM_RESULTS" +
            "&url=${URLEncoder.encode(imageUrl, "UTF-8")}" +
            keyParameter()
        return parseSauceNao(getBody(url), allowNsfw)
    }

    /** 用本机挑的一张图去反搜。 */
    suspend fun searchByFile(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        allowNsfw: Boolean,
    ): SauceNaoPage {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("output_type", "2")
            .addFormDataPart("hide", if (allowNsfw) "0" else "3")
            .addFormDataPart("numres", SAUCE_NUM_RESULTS.toString())
            .addFormDataPart(
                "file",
                fileName.ifBlank { "image.jpg" },
                bytes.toRequestBody(mimeType.toMediaType()),
            )
        credentials.apiKey.takeIf { it.isNotBlank() }
            ?.let { builder.addFormDataPart("api_key", it) }
        return parseSauceNao(postBody(builder.build()), allowNsfw)
    }

    private fun keyParameter(): String {
        val key = credentials.apiKey
        return if (key.isBlank()) "" else "&api_key=${URLEncoder.encode(key, "UTF-8")}"
    }

    private suspend fun getBody(url: String): String =
        withContext(Dispatchers.IO) { execute(request(url).build()) }

    private suspend fun postBody(body: MultipartBody): String =
        withContext(Dispatchers.IO) { execute(request(API_URL).post(body).build()) }

    private fun request(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("User-Agent", UserAgentStrings.DEFAULT_USER_AGENT)
    // ⚠️ 这里**刻意不打** [NoInteractiveBypassTag]，与画廊日榜那一路相反 —— 2026-09-27 实测：
    // saucenao.com 挂在 Cloudflare 交互挑战后面，三种 UA（移动端 Chrome 串 / 应用串 / 不带 UA）
    // 全部 403 + `server: cloudflare` + body 是 "Just a moment..."。
    // 也就是说**打过盾豁免标 = 这个功能永远是死的**，而那枚 tag 是为"整屏等结果的图片站流量"设的。
    //
    // 反搜不一样：它是用户**主动发起的一笔**、屏上有自己的加载态，弹一次人机验证窗口是可以接受的代价。
    // 真正的风险（过盾里那个 `await()` 没有超时，用户 Home 掉窗口会把线程挂住）
    // 由调用方 [com.venera.compose.gallery.ui.GalleryReverseViewModel] 的**硬超时**兜住 ——
    // UI 一定会落回一个确定态，最坏情况是后台多 parked 一个线程直到用户处理完那个窗口。

    /**
     * 发出去并交出**原始 body**；非 2xx 与网络失败一律翻成一句能念给用户的话。
     *
     * 状态码的翻译**不在这里**：SauceNAO 出错时回的是 `200` + `header.status ≠ 0`，
     * 所以"到底哪儿不对"只有 [parseSauceNao] 看过 body 才说得清。这一层只管 HTTP 层。
     *
     * 429 单独一档：SauceNAO 的免费额度是按天/按月的，撞上限是常态而不是偶发，
     * 把它混进"未知错误"会让人去重试，而重试只会再撞一次。
     */
    private fun execute(request: Request): String {
        val response = try {
            engine.okHttpClient
                .newCall(request).execute()
        } catch (e: IOException) {
            throw SauceNaoException("连不上 SauceNAO：${e.message ?: e.javaClass.simpleName}")
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw SauceNaoException(httpReason(it.code))
            return body
        }
    }

    private fun httpReason(code: Int): String = when (code) {
        403 -> "被 Cloudflare 挡住了（403）。刚才那个人机验证窗口要么被关掉、要么没通过 —— 再搜一次并把它点过去"
        429 -> "SauceNAO 在限流（额度用完了），过一会儿再试"
        in 500..599 -> "SauceNAO 自己出错了（$code）"
        else -> "SauceNAO 回了 $code"
    }

    companion object {
        private const val API_URL = "https://saucenao.com/search.php"

        /**
         * 一次要几条。20 与日榜"各站抽 20"同一档，不是拍的数；
         * 站方免费档上限更低时会自己截断，不会报错。
         */
        internal const val SAUCE_NUM_RESULTS = 20

        @Volatile
        private var INSTANCE: SauceNaoClient? = null

        fun getInstance(engine: HttpEngine, credentials: SauceNaoCredentials): SauceNaoClient =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SauceNaoClient(engine, credentials).also { INSTANCE = it }
            }
    }
}
