package com.venera.compose.gallery.data

import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * pixiv 画师页那三份匿名答复的解析（2026-10-19）。
 *
 * 三跳、两份模型：
 * ```
 * GET /ajax/user/{id}?full=1                          -> PixivUserHead        （头 + 背景 + 简介 + 外链）
 * GET /ajax/user/{id}/profile/all                     -> PixivUserWorks       （插画/漫画 id 表 + 收藏总数）
 * GET /ajax/user/{id}/profile/illusts?ids[]=…         -> List<PixivWork>     （按 id 取卡上那几格，分批）
 * ```
 * 第二、三跳合起来才是"三段内容"：`profile/all` 给**全量 id 表**（本机实测一位 150 件的画师
 * 一次全给），`profile/illusts` 一次认 24 枚 —— 游标就是那张 id 表本身，不自己拼 `offset`。
 *
 * ## 统一口径（与 [PixivUserParse] / [PixivFeedParse] 同一套）
 *
 * 1. **整份读不懂 = 抛**，绝不交空表。空表在屏上读成"这位没有作品"，
 *    而"站方换了盾 / 我们被挡"必须能被日志查出来。
 * 2. **有 N 条却一条都认不出 = 也抛**（站方改键名的表现就是整墙消失）。
 * 3. 单条认不出 ⇒ 跳过那一条，**不拖垮整批**（一张被删/被锁的作品不该让另外 23 张消失）。
 *
 * ⚠️ `background` 那一格**允许是 null**：那是站方明说"这位没设置背景"，
 * 与"这一跳失败"是两句话。所以它是**唯一**允许静默为 null 的字段 ——
 * 其余字段"没给"也留 null，但由调用方决定摆不摆，不在这里编默认值。
 */
internal object PixivUserWorksParse {

    private val json = Json { ignoreUnknownKeys = true }

    // ─────────────────────────────────────────────────────────────────────
    // 一跳：头部
    // ─────────────────────────────────────────────────────────────────────

    /**
     * `/ajax/user/{id}?full=1`。
     *
     * 现量字段（2026-10-19 本机实测，真号 7075448 / 11 / 49384704 三份）：
     * `userId` / `name` / `image` / `imageBig` / `premium` / `background{url,isPrivate,repeat,color}` /
     * `following` / `comment` / `webpage` / `social.{twitter,pawoo}.url` / `official` / `group[]`。
     *
     * ⚠️ `background` 是**整个对象为 null**（没设置），里面 `url` 也可能为 null。
     * 两种都收成 [PixivUserHead.backgroundUrl] = null —— 屏上的处理完全一样（回退首张作品），
     * 所以这里不必把两种情形分开记（分开只会让调用点多一个没人用的分支）。
     */
    fun userHead(body: String): PixivUserHead {
        val root = parseObject(body, "pixiv 用户头部")
        if (root[ERROR].isTrue()) {
            throw IOException(
                "pixiv 用户头部回 error=true（message=${root[MESSAGE]?.textOrNull().orEmpty()}）",
            )
        }
        // `partial` 字段在这条腿上是 1，但那不表示"答复不全"——它是站方的另一个开关，
        // 实测三份全为 1 而字段一个不缺。所以不拿它判完整性。
        val user = root[BODY]?.jsonObjectOrNull()
            ?: throw IOException("pixiv 用户头部答复里没有 body：${body.take(SAMPLE)}")
        val id = user[USER_ID].numberOrStringOrNull()?.toLongOrNull()?.takeIf { it > 0 }
            ?: throw IOException("pixiv 用户头部答复里没有可用的 userId：${body.take(SAMPLE)}")
        val name = user[NAME].textOrNull()
            ?: user[ACCOUNT].textOrNull()
            ?: throw IOException("pixiv 用户头部答复里既没有 name 也没有 account：${body.take(SAMPLE)}")

        // 背景：`background` 整个为 null / 非对象 ⇒ 没设置；对象里 `url` 为空 ⇒ 也算没设置。
        // 只认 http(s)：站方哪天给一个相对路径，摆上去就是一张空图。
        val background = user[BACKGROUND]?.jsonObjectOrNull()
            ?.get(URL)?.textOrNull()
            ?.takeIf { it.isHttpUrl() }

        val social = user[SOCIAL]?.jsonObjectOrNull()
        val links = buildList {
            social?.get(TWITTER)?.jsonObjectOrNull()?.get(URL).textOrNull()?.let { add(it) }
            social?.get(PAWOO)?.jsonObjectOrNull()?.get(URL).textOrNull()?.let { add(it) }
            user[WEBPAGE].textOrNull()?.let { add(it) }
        }.filter { it.isHttpUrl() }.distinct()

        return PixivUserHead(
            id = id,
            name = name,
            account = user[ACCOUNT].textOrNull().orEmpty(),
            // 头像两档：170 级优先、50 级兜底。两档都缺 ⇒ null（界面退首字母座）。
            avatarUrl = listOfNotNull(
                user[IMAGE_BIG].textOrNull(),
                user[IMAGE].textOrNull(),
            ).firstOrNull { it.isHttpUrl() },
            backgroundUrl = background,
            // 简介：`comment` 是纯文本。`commentHtml` 我们**不读** —— 它带 `<br />` 与 `<a>`，
            // 摆进 Text 里就是一堆尖括号（要渲染得另配一套 HTML 解析，而简介本来只是两行字）。
            comment = user[COMMENT].textOrNull().orEmpty(),
            links = links,
            followingCount = user[FOLLOWING].numberOrStringOrNull()?.toIntOrNull(),
            premium = user[PREMIUM].isTrue(),
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 二跳：作品索引
    // ─────────────────────────────────────────────────────────────────────

    /**
     * `/ajax/user/{id}/profile/all` —— 只要 id 表与**公开收藏总数**。
     *
     * 现量形状（真号 7075448）：
     * ```
     * {"body":{"illusts":{"148343100":null, …},"manga":{},"novels":[],
     *          "bookmarkCount":{"public":{"illust":860,…},"private":{"illust":…}},…}}
     * ```
     *
     * ## 两个桶的键顺序就是站方的顺序
     *
     * 站方按 `illust_id` **降序**给的（本机实测 150 条单调递减）⇒ 最新在前。
     * 所以 id 表**原样保留顺序**，不排序、不去重（去重会把站方的顺序依据也一并抹掉）。
     *
     * ## 收藏数读的是 public 那一格
     *
     * `private` 那一格我们**不看**：本页只读公开收藏，读私密数会让屏上"公开收藏 N"
     * 与下面那一墙的张数对不上（自己给自己造一个解释不清的差）。
     */
    fun profileAll(body: String): PixivUserWorks {
        val root = parseObject(body, "pixiv 作品索引")
        if (root[ERROR].isTrue()) {
            throw IOException(
                "pixiv 作品索引回 error=true（message=${root[MESSAGE]?.textOrNull().orEmpty()}）",
            )
        }
        val data = root[BODY]?.jsonObjectOrNull()
            ?: throw IOException("pixiv 作品索引答复里没有 body：${body.take(SAMPLE)}")
        // 桶在但形状不对（不是对象）⇒ 抛。缺桶（键不在）⇒ 空表，那是"这位没有投稿"的合法形状。
        val illusts = data[BUCKET_ILLUSTS].requireBucket("illusts", body)
        val manga = data[BUCKET_MANGA].requireBucket("manga", body)
        // ⚠️ 收藏数那一格叫 **`public.illust`（单数）**，不是 `illusts` —— 与上面那两个桶
        // 的键名不同形。2026-10-19 实测原答复：
        // `"bookmarkCount":{"public":{"illust":0,"novel":0,"collection":0},"private":{…}}`。
        // 第一版这里误用了 `BUCKET_ILLUSTS`（"illusts"）⇒ 恒取不到 ⇒ 屏上那格永远「—」，
        // 而且**不会报错**（链式 `?.` 一路 null 到底）。测试 `索引 - 两个桶的 id 表与公开收藏数`
        // 就是为这条抓的。
        val publicBookmarks = data[BOOKMARK_COUNT]?.jsonObjectOrNull()
            ?.get(PUBLIC)?.jsonObjectOrNull()
            ?.get(BOOKMARK_ILLUST).numberOrStringOrNull()?.toIntOrNull()
        return PixivUserWorks(illusts = illusts, manga = manga, publicBookmarkCount = publicBookmarks)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 三跳：按 id 取一批
    // ─────────────────────────────────────────────────────────────────────

    /**
     * `/ajax/user/{id}/profile/illusts?ids[]=…&work_category=…` 的一批。
     *
     * 现量形状：`{"body":{"works":{"<id>":{…9 格…}|null, …}}}`。
     * 认不出的那一格**交 null**（不跳过 —— 调用方要拿它跟请求的 id 表**对账**：
     * 请求 24 枚、回来 20 枚有声的，那 4 枚是站方说"这一条没了"，屏上不该把位置让给别人的图）。
     *
     * ## 只取卡上要的那几格
     *
     * `url`(250×250 缩略) / `width` / `height` / `pageCount` / `xRestrict` / `aiType` /
     * `tags[]` / `createDate` / `title` / `userName` / `userId`。
     * 这一腿**不带** `largeUrl` / `fileUrl` —— 它只给缩略档。点开大图时由大图页自己按号取
     * （`PixivClient.workDetail`），所以这里**不许**把 `url` 冒充成大图地址
     * （`GalleryPost.largeUrl` 留空串，`backdropUrl` 自然落回 `previewUrl`）。
     */
    fun worksByIds(body: String, requested: List<Long>): List<PixivWork?> {
        val root = parseObject(body, "pixiv 画师作品批")
        if (root[ERROR].isTrue()) {
            throw IOException(
                "pixiv 画师作品批回 error=true（message=${root[MESSAGE]?.textOrNull().orEmpty()}）",
            )
        }
        val works = root[BODY]?.jsonObjectOrNull()?.get(WORKS)?.jsonObjectOrNull()
            ?: throw IOException("pixiv 画师作品批答复里没有 body.works：${body.take(SAMPLE)}")
        // 按**请求顺序**交回，而不是按站方对象键序：渲染顺序由 id 表决定（最新在前），
        // 跟着 JSON 对象的键序走等于把顺序交给序列化器的实现细节。
        return requested.map { id -> works[id.toString()]?.let { element -> workOf(element) } }
    }

    /** 一条 `works[<id>]`。形不成一张卡的（没有 id 或没有缩略图）交 null。 */
    private fun workOf(element: JsonElement): PixivWork? {
        val record = element as? JsonObject ?: return null
        val id = record[ID].numberOrStringOrNull()?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val image = record[URL].textOrNull()?.takeIf { it.isHttpUrl() } ?: return null
        val post = GalleryPost(
            site = GallerySite.PIXIV,
            id = id,
            tags = record[TAGS].joinedText(separator = " "),
            // 分级字形与搜索那一腿同一条翻译（`xRestrict` 0/1/2）。
            // ⚠️ 这张表在 [PixivFeedParse] 里是 **private** —— 那一颗文件正被别人频繁改，
            // 我去把它开成 internal 就是在别人的文件上动刀。所以这里各留一份
            // `xRestrict` 的翻译；两边必须同值，由 `PixivUserWorksParseTest` 对表钉住。
            rating = ratingOfXRrestrict(record[XRESTRICT].numberOrStringOrNull()),
            siteAiFlag = pixivAiFlagOf(record[AI_TYPE].numberOrStringOrNull()),
            author = record[USER_NAME].textOrNull().orEmpty(),
            source = record[USER_ID].numberOrStringOrNull()?.let { PIXIV_USERS + it }.orEmpty(),
            width = record[WIDTH].numberOrStringOrNull()?.toIntOrNull() ?: 0,
            height = record[HEIGHT].numberOrStringOrNull()?.toIntOrNull() ?: 0,
            // 这一腿是**数字** `pageCount`（榜腿那格是字符串 `illust_page_count`）。
            pageCount = record[PAGE_COUNT].numberOrStringOrNull()?.toIntOrNull(),
            previewUrl = image,
            fileExt = extensionOf(image),
        )
        return PixivWork(
            post = post,
            title = record[TITLE].textOrNull().orEmpty(),
            rank = null,
            // 这一腿给 `profileImageUrl`（50 级，恒是本画师的）—— 与搜索腿同一格用法：
            // 网格卡走「头像 + 标题 + 画师名」那一档时靠它，取不到留空串（卡上退首字母座）。
            authorAvatarUrl = record[PROFILE_IMAGE].textOrNull().orEmpty(),
            ugoira = record[ILLUST_TYPE].numberOrStringOrNull() == "2",
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 共用
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 一个桶（`illusts` / `manga`）。
     *
     * - 键不在 ⇒ 空表（这位没投过那一类，是合法答复）；
     * - 键在但不是对象 ⇒ **抛**（形状变了，交空表就是把"读不懂"说成"没有"）；
     * - 是对象 ⇒ 取它的键当 id，**跳过非正数**（`"0"` / 非数字那些不是作品号）。
     */
    /**
     * 一个桶 → id 表。
     *
     * 三种形状要分清，而**前两种都是合法答复**（2026-10-19 现量改判，病根见 `PixivUserWorksParseTest`
     * 那两条用例）：
     * - 键不在 / `null` ⇒ 空表；
     * - `{}` **或 `[]`** ⇒ 空表。站方对"这一类没有投稿"给的就是空数组
     *   （`profile/all` 实测：`"illusts":{…133 条},"manga":[],"novels":[]`），
     *   从前只认对象，于是只发插画的画师整条 `profileAll` 抛出去 —— 而调用点把它吞了，
     *   屏上读到的就是"画师页不出图、四格全是「—」、一句失败话都没有"；
     * - 对象 ⇒ 取键（**原样保留站方顺序**，那是"最新在前"的唯一依据）；
     * - 其余（带元素的数组、标量）⇒ 抛。那一档才是"站方真改了形状"，不许读成"这位没有"。
     */
    private fun JsonElement?.requireBucket(bucket: String, body: String): List<Long> {
        if (this == null) return emptyList()
        if (this is JsonArray) {
            if (isEmpty()) return emptyList()
            throw IOException("pixiv 作品索引的 $bucket 是带 ${size} 个元素的数组，不是对象：${body.take(SAMPLE)}")
        }
        val obj = this as? JsonObject
            ?: throw IOException("pixiv 作品索引的 $bucket 不是对象：${body.take(SAMPLE)}")
        return obj.keys.mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0 } }
    }

    private fun parseObject(body: String, who: String): JsonObject =
        runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IOException("$who 回来的不是 JSON 对象（可能是风控页或被换盾）：${body.take(SAMPLE)}") }

    private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.textOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonElement?.numberOrStringOrNull(): String? = textOrNull()

    private fun JsonElement?.isTrue(): Boolean = textOrNull()?.lowercase() == "true"

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    /** 标签数组拼成空格串（`GalleryPost.tags` 那一格要的形状）。非字符串项跳过。 */
    private fun JsonElement?.joinedText(separator: String): String {
        val array = this as? JsonArray ?: return ""
        return array.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
            .joinToString(separator)
    }

    /** 扩展名：从地址尾巴取，**写死 jpg 就是错的**（同一作品不同页可能是 png）。 */
    private fun extensionOf(url: String): String =
        url.substringAfterLast('.', "").substringBefore('?').lowercase()

    /**
     * `xRestrict` → 分级串（0 一般 / 1 R-18 / 2 R-15，其余一律按成人档）。
     *
     * ⚠️ 与 [PixivFeedParse] 里那份 private 表**必须同值**。不共用是因为那一颗文件
     * 正被另一条线频繁修改，把它的私有件开出来就是在别人文件上动刀；
     * 两边同值这件事由 `PixivUserWorksParseTest` 的对表用例钉住（一处漂了当场红）。
     */
    private fun ratingOfXRrestrict(raw: String?): String = when (raw) {
        "0" -> "general"
        "2" -> "r15"
        else -> "r18"
    }

    internal const val PIXIV_USERS = "https://www.pixiv.net/users/"

    private const val ERROR = "error"
    private const val MESSAGE = "message"
    private const val BODY = "body"
    private const val USER_ID = "userId"
    private const val NAME = "name"
    private const val ACCOUNT = "account"
    private const val IMAGE = "image"
    private const val IMAGE_BIG = "imageBig"
    private const val BACKGROUND = "background"
    private const val URL = "url"
    private const val SOCIAL = "social"
    private const val TWITTER = "twitter"
    private const val PAWOO = "pawoo"
    private const val WEBPAGE = "webpage"
    private const val COMMENT = "comment"
    private const val FOLLOWING = "following"
    private const val PREMIUM = "premium"
    private const val BUCKET_ILLUSTS = "illusts"
    private const val BUCKET_MANGA = "manga"
    private const val BOOKMARK_COUNT = "bookmarkCount"
    /** 收藏数那一格的内层键：**单数** `illust`（与桶名 `illusts` 不同形，实测）。 */
    private const val BOOKMARK_ILLUST = "illust"
    private const val PUBLIC = "public"
    private const val WORKS = "works"
    private const val ID = "id"
    private const val TAGS = "tags"
    private const val XRESTRICT = "xRestrict"
    private const val AI_TYPE = "aiType"
    private const val USER_NAME = "userName"
    private const val WIDTH = "width"
    private const val HEIGHT = "height"
    private const val PAGE_COUNT = "pageCount"
    private const val TITLE = "title"
    private const val PROFILE_IMAGE = "profileImageUrl"
    private const val ILLUST_TYPE = "illustType"
    private const val SAMPLE = 160
}

/**
 * `profile/all` 的那一份读数。
 *
 * [illusts] / [manga] 是**全量 id 表**（站方降序 = 最新在前），不是"这一页的张数"——
 * 屏上那两个数字直接取 `size`；翻页靠"下一批多少个 id 还没取"来判，不另存游标。
 *
 * [publicBookmarkCount] 可空：`bookmarkCount.public.illust` 这一格站方可能不给
 * （实测本机三位都有；但缺了就该退成「—」，不能写 0 —— 0 是"他一张都没收藏"的断言）。
 */
data class PixivUserWorks(
    val illusts: List<Long>,
    val manga: List<Long>,
    val publicBookmarkCount: Int?,
) {
    fun idsFor(section: PixivArtistSection): List<Long> = when (section) {
        PixivArtistSection.ILLUST -> illusts
        PixivArtistSection.MANGA -> manga
        // 收藏段不在这一份里：它是别人作品的一批 id，由票据腿另取
        // （`app-api /v1/user/bookmarks/illust`），所以这里交空表。
        PixivArtistSection.BOOKMARK -> emptyList()
    }
}

/**
 * pixiv 画师页那三段。**顺序即屏上顺序**（用户 2026-10-19 点名：插画 / 漫画 / 收藏）。
 *
 * ⚠️ 名字刻意**不叫** `PixivArtistTab`：那个名字已经被首页「画师」那一节的
 * 「已关注 / 发现」两档占了（`GalleryPixivHome.kt`），两者是**不同层级的两件事**
 * （那一档切"摆哪一份人"，这一段切"看这位的哪一类作品"）。
 * 同名会让两处在 import 上撞车，也会让下一个人以为它们该统一。
 */
enum class PixivArtistSection(val label: String) {
    ILLUST("插画"),
    MANGA("漫画"),
    BOOKMARK("收藏"),
}
