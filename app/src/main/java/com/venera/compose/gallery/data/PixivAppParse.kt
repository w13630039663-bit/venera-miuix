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
 * **app-api**（带 Bearer 的那一侧）答复的形状解析。
 *
 * 为什么另起一颗而不并进 [PixivFeedParse]：那是 `www.pixiv.net` 的三条匿名腿，答复外面各套一层
 * `body` / `contents`；app-api 是**平铺**的（`{"illusts":[…]}`），错误形状也不同
 * （`{"error":{"message":…,"reason":…,"status_code":…}}` 而不是 `error=true`）。
 * 两种形状混在一颗件里，下一次改其中一个就会顺手改坏另一个 —— 本仓为这类混账付过账。
 *
 * 同一套口径照旧：
 * 1. **整份读不懂 = 抛**（不是交空表）；
 * 2. **有 N 条却一条都认不出 = 也抛**（站方改键名的表现就是整墙消失，那不能说成"没有图"）。
 *
 * 类型那件事仍然要小心：[PixivFeedParse] 的类注释里有张表，讲同一种意思在三条腿里类型相反。
 * 这里一律走 `numberOrStringOrNull()`（数字与字符串都吃），免得再踩一次。
 */
/**
 * app-api 的**一页**：这一页摆出来的作品 + 下一页的地址。
 *
 * 为什么必须把游标交出来而不是只给 `List<PixivWork>`：瀑布流要"无限下拉"，而翻页口径
 * 只能由站方给（`next_url` 里带着它自己那套 `offset` 与 `filter`）。自己拼页码 = 猜站方分页，
 * 猜错的表现是"第二页回来了跟第一页一样"或"到第 3 页开始 400"，两种都不会报错。
 *
 * [nextUrl] 为 null 只有一种含义：**到底了**（站方没给 `next_url`）。
 */
data class PixivAppPage(
    val works: List<PixivWork>,
    val nextUrl: String?,
)

/**
 * `/v1/user/detail` 交给画师页的那几格（解析见 [PixivAppParse.userDetail]）。
 *
 * **每格都可空，缺一格就退「—」而不是退 0**：0 是"这位没有投稿 / 没有粉丝"的断言，
 * 而我们只是那一格没拿到。
 *
 * [isFollowed] 的三档必须保住差别：`true` = 站方答"关注了"，`false` = 站方答"没关注"，
 * `null` = 站方没答这一格（没票据 / 形状变了）。屏上前两档直接照念，`null` 那档退到本地账。
 */
data class PixivUserDetail(
    val totalIllusts: Int?,
    val totalManga: Int?,
    val totalFollowers: Int?,
    val totalBookmarksPublic: Int?,
    val isFollowed: Boolean?,
)

internal object PixivAppParse {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * `/v1/illust/recommended` 的答复。
     *
     * 现量口径（站方公开接口文档 + 客户端样本）：
     * - `illusts[]` 每项：`id`(number) / `title` / `image_urls.{px_128x128,px_360x360,px_540x540,large,square_medium,medium}`
     *   / `user.{id,name,account}` / `tags[].{name}` / `width,height` / `x_restrict` / `page_count`
     *   / `total_bookmarks` / `meta_single_page.{original_image_url}` / `meta_pages[].image_urls.original`；
     * - 多页组图**只取第 0 页**（与本仓既有定档一致，见 `PixivFeedParse` 那条）。
     * - `next_url`：下一页的**完整地址**（已带 `offset` 与 `filter`）。没有它 = 到底了。
     *   2026-10-17 第七轮加：用户点名「这个推荐也是要做成瀑布流的无限下拉的」。
     *   翻页只认这一枚 —— 自己拼 `offset` 等于在猜站方的分页口径。
     *
     * 图址三档的取法：
     * - 网格缩略 = `medium`（540 宽，**不是** `square_medium` —— 那一档是方裁，塞进瀑布流会变形）；
     * - 大图首屏 = `large`（master1200）；
     * - 原图 = `meta_single_page.original_image_url`，多页则 `meta_pages[0].image_urls.original`。
     *   两处都没有时**留空串**，绝不拿 large 冒充原图（本仓"绝不留一个编出来的地址"那条）。
     */
    fun illustrationRecommended(body: String): PixivAppPage =
        page(body, "pixiv 推荐", withRank = false)

    /**
     * `/v1/illust/ranking` 的答复。
     *
     * 与推荐**同一个 `illusts[]` 形状**，只多一枚 `rank`（站方给的 1..500，真名次）。
     * 所以这里复用同一颗行解析，只把 `rank` 读出来 —— 两份各行写一遍解析器的话，
     * 站方哪天加一档字段就得记着改两处（本仓为这类分叉付过账）。
     */
    fun illustrationRanking(body: String): PixivAppPage =
        page(body, "pixiv 榜单", withRank = true)

    /**
     * `/v1/user/bookmarks/illust` 的答复（2026-10-17 第三十一轮，收藏 hub 那枚 🔖 卡）。
     *
     * 与推荐/榜单**同一个 `illusts[]` 形状**（书签这一腿不带 `rank`，`withRank = false`）——
     * 三方共用 [page] 这一颗行解析。书签页每项还多一枚 `is_bookmarked`（恒 true，因为这就是书签列表），
     * 这里不读它：那一格在这颗件的使用点上没有意义（屏上不会画"已收藏"在收藏列表里）。
     */
    fun illustrationBookmarks(body: String): PixivAppPage =
        page(body, "pixiv 书签", withRank = false)

    /**
     * `/v1/search/illust` 的答复（2026-10-17 深夜，用户「这三段应该是有的，看看 pixez 怎么做的」）。
     *
     * 与推荐/榜单/书签**同一个 `illusts[]` 形状**，所以复用同一颗行解析。
     * 多出来的那一枚 `type`（`illust`/`manga`/`ugoira`）在行解析里被读成 [PixivWork.ugoira] ——
     * 「仅动图」那一档只能靠它（站方搜索接口没有动图参数）。
     *
     * `next_url` 同样自带 `offset`，翻页只认它。
     */
    fun illustrationSearch(body: String): PixivAppPage =
        page(body, "pixiv 搜索", withRank = false)

    /**
     * **人气预览**那条的答复（`/v1/search/popular-preview/illust`，2026-10-19 · 批 233-4）。
     *
     * 键表与 [illustrationSearch] 共用同一颗 [page]：两条腿回的是**同一个 illust 对象**，
     * 抄两遍就是两把尺。单独一颗只为了那句失败读数说的是哪一条腿 ——
     * 把"pixiv 搜索"套在预览腿上，出问题时会把人引到另一条腿上去查。
     */
    fun popularPreview(body: String): PixivAppPage =
        page(body, "pixiv 人气预览", withRank = false)

    /**
     * `/v1/user/detail` 里**只取一枚**：`user.is_premium`（会员身份，决定收藏门槛用哪一种发法）。
     *
     * 缺键、不是布尔、写成 `"true"` 之外的任何形状 ⇒ 一律 `false`（当非会员）。
     * 方向是故意的：判成非会员只是"改用词尾标签"，那一档匿名实测就真生效；
     * 反过来判成会员就会发一枚站方按付费墙拒绝的参数，屏上读成"点了没生效"。
     */
    fun userPremium(body: String): Boolean {
        val flag = (parseObject(body, "pixiv 用户详情")["user"] as? JsonObject)
            ?.get("is_premium") as? JsonPrimitive
        return flag?.contentOrNull?.toBooleanStrictOrNull() == true
    }

    /**
     * `/v1/user/illusts` 的答复（2026-10-19，用户报「返回的插画数量少很多」）。
     *
     * 与推荐/榜单/书签/搜索**同一个 `illusts[]` 形状**，所以复用同一颗 [page]：
     * 键表抄第二遍的话，站方哪天改一档，只有真机能看见其中一处读空。
     *
     * 它比匿名那条 `www/ajax/user/{id}/profile/all` 多出来的东西正是这一批的立案读数 ——
     * **成人档只有带票据这一腿才回**（匿名那一腿站方按口径整批过滤掉了）。
     * 于是同一位画师"屏上 3 张 / 网页 26 张"不是我们数错，是腿选错了。
     */
    fun userIllusts(body: String): PixivAppPage =
        page(body, "pixiv 画师作品", withRank = false)

    /**
     * `/v1/user/detail` 里画师页要的那几格（2026-10-19）。
     *
     * 一次拿齐三件此前各缺一块的事：
     * - `profile.total_illusts` / `total_manga` —— 屏上那两格的**真数**（站方自己的口径，
     *   不再由"id 表长度"代算：匿名 id 表少给，那个长度就是个偏小的数）；
     * - `profile.total_follow_users` —— 粉丝数。此前那一格恒退「—」，理由写的是"要票据腿"，
     *   而这一屏的调用方**全程是登录态** ⇒ 那个「—」不是降级，是没去取；
     * - 顶层 `is_followed` —— **我是否关注了这位**。按钮那两个字以此为真相。
     *
     * ⚠️ `is_followed` 缺键 ⇒ **null**，不是 false。false 是"没关注"的断言，null 才是
     * "站方没答这一格"；屏上那一档要退到本地那本账，不许直接念"关注"。
     */
    fun userDetail(body: String): PixivUserDetail {
        val root = parseObject(body, "pixiv 画师详情")
        rethrowAppError(root, "pixiv 画师详情")
        val profile = root["profile"] as? JsonObject
            ?: throw IOException("pixiv 画师详情答复里没有 profile：${body.take(SAMPLE)}")
        return PixivUserDetail(
            totalIllusts = profile["total_illusts"].numberOrStringOrNull()?.toIntOrNull(),
            totalManga = profile["total_manga"].numberOrStringOrNull()?.toIntOrNull(),
            totalFollowers = profile["total_follow_users"].numberOrStringOrNull()?.toIntOrNull(),
            totalBookmarksPublic = profile["total_illust_bookmarks_public"]
                .numberOrStringOrNull()?.toIntOrNull(),
            isFollowed = root["is_followed"].booleanOrNull(),
        )
    }

    /**
     * **作品评论**那一条的答复（2026-10-19 · 批 231-F）。
     *
     * 形状取自 pixivpy `aapi.py:illust_comments` 与 `models.py:Comment` / `CommentUser`：
     * ```
     * {"comments":[{"id":123,"comment":"…","date":"2026-10-01T06:12:00+09:00",
     *               "user":{"id":123,"name":"…","account":"…",
     *                       "profile_image_urls":{"medium":"https://i.pximg.net/…"}},
     *               "parent_comment":{…}|{}}],
     *  "next_url":"…", "total_comments":6}
     * ```
     *
     * 三处口径与这一族其它解析同源：
     * 1. **整份读不懂 = 抛**（不交空表：空表在屏上读成"这一条没有评论"）；
     * 2. `user` **可能是 `{}`**（删号）⇒ 那一条照样交，只是没有脸没有名字 ——
     *    丢掉它就等于少一条真评论；
     * 3. `parent_comment` 是 `{}` 时**就是没有父评论**（站方用空对象表达 null）。
     *
     * 单条认不出（没有 `id` 或没有任何正文）⇒ 跳过那一条，不拖垮整批。
     */
    fun illustComments(body: String): PixivCommentPage {
        val root = parseObject(body, "pixiv 评论")
        // app-api 的错误答复是平铺在 `error` 上的对象（同 `page()`）。
        (root["error"] as? JsonObject)?.let { error ->
            val status = error["status_code"].textOrNull()
            val userMessage = error["user_message"].textOrNull()
            val message = error["message"].textOrNull()
            val reason = error["reason"].textOrNull()
            throw IOException(
                "pixiv 评论回 error" + (status?.let { "（$it）" } ?: "") + "：" +
                    listOf(userMessage, message, reason).filter { !it.isNullOrBlank() }
                        .joinToString(" · ").ifBlank { "站方没给原因" },
            )
        }
        val array = root["comments"] as? JsonArray
            ?: throw IOException("pixiv 评论答复里没有 comments 数组：${body.take(SAMPLE)}")
        val comments = array.mapNotNull { element ->
            val record = element as? JsonObject ?: return@mapNotNull null
            val id = record["id"].numberOrStringOrNull()?.toLongOrNull() ?: return@mapNotNull null
            val user = record["user"] as? JsonObject
            // `parent_comment` 为 `{}` ⇒ 没有父评论（站方用空对象表达 null，不是缺键）。
            val parentId = (record["parent_comment"] as? JsonObject)
                ?.get("id").numberOrStringOrNull()?.toLongOrNull()
            PixivComment(
                id = id,
                // 正文：站方给的 `comment` 是纯文本。空正文**不再整条丢掉** ——
                // 纯贴纸评论的正文本来就是空串（2026-10-19 六家字段表对齐 + 用户"要正常显示评论图"），
                // 丢掉它就是"屏上条数比站方给的少"那一族假话。两样都没有才丢。
                text = record["comment"].rawTextOrEmpty(),
                date = record["date"].textOrNull(),
                userName = user?.get("name").textOrNull().orEmpty(),
                userAccount = user?.get("account").textOrNull().orEmpty(),
                userAvatarUrl = (user?.get("profile_image_urls") as? JsonObject)
                    ?.get("medium").textOrNull().orEmpty(),
                replyToId = parentId,
                stampUrl = (record["stamp"] as? JsonObject)?.get("stamp_url").textOrNull().orEmpty(),
            ).takeIf { it.text.isNotBlank() || it.stampUrl.isNotBlank() }
                ?: return@mapNotNull null
        }
        // 有 N 条却一条都认不出 = 站方改了字段名（整栏消失），不许说成"没有评论"。
        if (array.isNotEmpty() && comments.isEmpty()) {
            throw IOException("pixiv 评论给了 ${array.size} 条，但没有一条认得出 id 与正文 —— 站方大概改了字段名")
        }
        return PixivCommentPage(
            comments = comments,
            nextUrl = root["next_url"].textOrNull(),
            totalCount = root["total_comments"].numberOrStringOrNull()?.toIntOrNull(),
        )
    }
    private fun page(body: String, who: String, withRank: Boolean): PixivAppPage {
        val root = parseObject(body, who)
        rethrowAppError(root, who)
        val illusts = root["illusts"] as? JsonArray
            ?: throw IOException("$who 答复里没有 illusts 数组：${body.take(SAMPLE)}")
        val posts = illusts.mapNotNull { element ->
            val record = element as? JsonObject ?: return@mapNotNull null
            val user = record["user"]?.jsonObject
            // 键表只许有一份：列表腿与作品页那条登录腿（`/v1/illust/detail`）吃的是**同一个 illust 对象**。
            // 两处各抄一遍就是两把尺 —— 站方哪天换键名，只有真机能看见其中一处读空。
            val post = postOf(record, requirePreview = true) ?: return@mapNotNull null
            PixivWork(
                post = post,
                title = record["title"].textOrNull().orEmpty(),
                // 推荐那条**没有名次**（那是榜单那一腿的事）⇒ null，理由同 `tagWorks` 那一处。
                rank = if (withRank) record["rank"].numberOrStringOrNull()?.toIntOrNull() else null,
                // 头像就在同一个响应里（`user.profile_image_urls.medium`，px_170x170）——
                // 取不到留空串，卡上退回首字母座（见 [PixivWork.authorAvatarUrl]）。
                authorAvatarUrl = user?.get("profile_image_urls")?.jsonObject
                    ?.get("medium").textOrNull().orEmpty(),
                // app-api 的作品类型是字符串：`illust` / `manga` / `ugoira`。
                // 「仅动图 / 排除动图」那一档只能靠它（站方搜索没有这一枚参数）。
                ugoira = record["type"].textOrNull() == "ugoira",
            )
        }
        requireRecognized(posts.size, illusts.size, who)
        return PixivAppPage(
            works = posts,
            // 空串也算到底（站方给 null 时 textOrNull 已经是 null；给 "" 时归一成 null）。
            nextUrl = root["next_url"].textOrNull(),
        )
    }

    /**
     * app-api 的错误答复是**平铺在 `error` 上的对象**（与匿名腿那个 `error:true` 旗标不同形），
     * 里面站方原话就在 `message` / `user_message` / `reason` 三格。列表腿与作品页那条登录腿共用这一颗：
     * 同一份信封在两处各解释一遍，迟早有一处把 404 念成"这一轮没有"。
     */
    private fun rethrowAppError(root: JsonObject, who: String) {
        (root["error"] as? JsonObject)?.let { error ->
            val status = error["status_code"].textOrNull()
            val message = error["message"].textOrNull().orEmpty()
            val reason = error["reason"].textOrNull().orEmpty()
            val userMessage = error["user_message"].textOrNull()
            throw IOException(
                "$who 回 error" + (status?.let { "（$it）" } ?: "") + "：" +
                    listOf(message, userMessage, reason).filter { !it.isNullOrBlank() }.joinToString(" · ")
                        .ifBlank { "站方没给原因" },
            )
        }
    }

    /**
     * app-api 的一颗 **illust 对象** → 三站共用的行模型 [GalleryPost]。
     *
     * 键表就是 [illustrationRecommended] 头注那张现量表（`image_urls` / `meta_single_page` /
     * `meta_pages[].image_urls` / `user` / `x_restrict` / `illust_ai_type` / `total_bookmarks` /
     * `page_count`），所以列表腿与详情腿共用这一颗。
     *
     * [requirePreview] 是两腿唯一的差别：
     * - 列表腿 **true** —— 一排卡里认不出缩略图的那一行就跳掉（站方给的多半是被撤下来的旧号），
     *   它与 [requireRecognized] 那句对账配成一对；
     * - 作品页那条 **false** —— 这一屏只画一条，站方 `image_urls` 空着而 `meta_pages` 给了原图时，
     *   中档退回原图照样读得出（"这一页没有更低清的一档"是真话，"这一条没有图"不是）。
     */
    private fun postOf(record: JsonObject, requirePreview: Boolean): GalleryPost? {
        val id = record["id"].numberOrStringOrNull()?.toLongOrNull() ?: return null
        val images = record["image_urls"]?.jsonObject
        val preview = images?.get("medium").textOrNull()
            ?: images?.get("square_medium").textOrNull()
            ?: images?.get("large").textOrNull()
        if (preview == null && requirePreview) return null
        val user = record["user"]?.jsonObject
        val original = (record["meta_single_page"] as? JsonObject)
            ?.get("original_image_url").textOrNull()
            ?: ((record["meta_pages"] as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.get("image_urls")?.jsonObject?.get("original").textOrNull()
            ?: preview
        if (preview == null && original == null) return null
        return GalleryPost(
            site = GallerySite.PIXIV,
            id = id,
            tags = (record["tags"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.get("name").textOrNull() }
                ?.joinToString(" ")
                .orEmpty(),
            rating = RATING_BY_XRESTRICT[record["x_restrict"].numberOrStringOrNull()] ?: RATING_MASKED,
            // 登录腿的 AI 硬位叫 `illust_ai_type`（2=AI，与匿名腿的 `aiType` 同一含义，
            // 所以共用 pixivAiFlagOf 那一颗翻译）。
            siteAiFlag = pixivAiFlagOf(record["illust_ai_type"].numberOrStringOrNull()),
            // 这一格**有真数**（同匿名腿的 `bookmarkCount`）：`total_bookmarks` 就是收藏数。
            favCount = record["total_bookmarks"].numberOrStringOrNull()?.toIntOrNull(),
            author = user?.get("name").textOrNull().orEmpty(),
            source = user?.get("id").numberOrStringOrNull()?.let { USERS + it }.orEmpty(),
            width = record["width"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
            height = record["height"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
            // 登录腿那一格叫 `page_count`（数字）—— 卡上那枚「几 P」在登录那一侧同样有出处
            // （匿名榜腿是字符串 `illust_page_count`、搜索腿是 `pageCount`，三形各归各腿）。
            pageCount = record["page_count"].numberOrStringOrNull()?.toIntOrNull(),
            previewUrl = preview ?: original.orEmpty(),
            fastUrl = images?.get("large").textOrNull().orEmpty(),
            largeUrl = images?.get("large").textOrNull().orEmpty(),
            fileUrl = original.orEmpty(),
            fileExt = extensionOf(original ?: preview.orEmpty()),
        )
    }

    /**
     * 作品页那一腿的**登录版**：`/v1/illust/detail` 的答复 → [PixivWorkDetail]。
     *
     * 为什么要有这一颗（2026-10-18 第四批第七件，用户「我已经登录而且站点也全无限制的，
     * 为啥有报错图片看不了」）：屏上那些 pixiv 卡的**来源**已经是登录腿（关注画师 / 推荐 /
     * 排行榜 / 书签都带票据），而详情从前**只有匿名腿** —— 站方对未登录的读者把成人向、
     * 仅登录可见、私密那几条整条撤图，于是"卡明明在墙上、点开说没有"。两腿同轨之后，
     * 已经登录的那台机器上这些条就读得出。
     *
     * 三格只有作品页要、而这一腿**没有**的，一律交"没有那一格"而不是编一个：
     * - [PixivWorkDetail.commentCount] = null（app-api 的 illust 对象里没有评论数）⇒ 屏上那一枚不摆数；
     * - [PixivWorkDetail.commentsOff] = false（读不出开关就不替作者关上它）；
     * - 逐页宽高缺档就交 null ⇒ 骨架那一格不预留比例。
     *
     * ⚠️ **这一腿的键表尚无本机真读数**：app-api 不带票据现量回 400「Error occurred at the OAuth
     * process」（2026-10-18），拿不到原文。键表是从本文件上面那笔已现量过的**同一个 illust 对象**
     * 抄来的（列表腿那一张表），所以写错的后果不是崩，是这一腿读不出图 —— 而调用点在
     * "答上了却读不出图"时退回匿名腿，屏上维持本轮之前的样子，不会更坏。真读数记在手册待上机那一格。
     *
     * 交 null 只有一种情形：答复读得懂、可这一条没有可看的图。**整份读不懂仍然抛**
     * （缺 `illust` 那一层、`error` 信封、页数对不齐），因为"没有这一条"与"我们看不懂这一份"是两句话。
     */
    fun workDetail(body: String): PixivWorkDetail? {
        val root = parseObject(body, "pixiv 作品页（登录腿）")
        rethrowAppError(root, "pixiv 作品页（登录腿）")
        val record = root["illust"]?.jsonObject
            ?: throw IOException("pixiv 作品页（登录腿）答复里没有 illust：${body.take(SAMPLE)}")
        val post = postOf(record, requirePreview = false) ?: return null
        val images = record["image_urls"]?.jsonObject
        val medium = images?.get("medium").textOrNull() ?: images?.get("large").textOrNull()
        val rows = (record["meta_pages"] as? JsonArray).orEmpty().mapNotNull { element ->
            val page = element as? JsonObject ?: return@mapNotNull null
            val urls = page["image_urls"]?.jsonObject
            val original = urls?.get("original").textOrNull()
            val one = urls?.get("medium").textOrNull() ?: urls?.get("large").textOrNull() ?: original
            if (one == null && original == null) return@mapNotNull null
            PixivPage(
                mediumUrl = one ?: original.orEmpty(),
                originalUrl = original ?: one.orEmpty(),
                width = page["width"].numberOrStringOrNull()?.toIntOrNull(),
                height = page["height"].numberOrStringOrNull()?.toIntOrNull(),
            )
        }
        // 单页那一形：站方只给 `meta_single_page.original_image_url`，中档退回 `image_urls.medium`。
        val pages = rows.ifEmpty {
            val original = (record["meta_single_page"] as? JsonObject)
                ?.get("original_image_url").textOrNull() ?: post.fileUrl.ifBlank { null }
            val first = medium ?: original
            if (first == null || first.isBlank()) emptyList() else listOf(
                PixivPage(
                    mediumUrl = first,
                    originalUrl = original ?: first,
                    width = record["width"].numberOrStringOrNull()?.toIntOrNull(),
                    height = record["height"].numberOrStringOrNull()?.toIntOrNull(),
                ),
            )
        }
        if (pages.isEmpty()) return null
        val reported = record["page_count"].numberOrStringOrNull()?.toIntOrNull()
        // 逐页对账（与匿名腿同一把尺）：站方说 4 页而手里只有 3 页就是错，不能读成"这一作本来 3 页"。
        if (reported != null && reported != pages.size) {
            throw IOException("pixiv 作品页（登录腿）说 $reported 页而认得出 ${pages.size} 页")
        }
        return PixivWorkDetail(
            post = post,
            title = record["title"].textOrNull().orEmpty(),
            authorId = record["user"]?.jsonObject?.get("id").numberOrStringOrNull()?.toLongOrNull(),
            authorAccount = record["user"]?.jsonObject?.get("account").textOrNull().orEmpty(),
            pages = pages,
            pageCount = reported ?: pages.size,
            tags = (record["tags"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.get("name").textOrNull() }
                ?: emptyList(),
            viewCount = record["total_view"].numberOrStringOrNull()?.toIntOrNull(),
            uploadDate = record["create_date"].textOrNull(),
            commentCount = null,
            commentsOff = false,
            ugoira = record["type"].textOrNull() == "ugoira",
        )
    }

    private fun parseObject(body: String, who: String): JsonObject =
        runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IOException("$who 回来的不是 JSON 对象（可能是风控页或被换盾）：${body.take(SAMPLE)}") }

    private fun requireRecognized(recognized: Int, total: Int, who: String) {
        if (total > 0 && recognized == 0) {
            throw IOException("$who 给了 $total 条，但没有一条认得出作品号与图址 —— 站方大概改了字段名")
        }
    }

    private fun JsonElement?.textOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /**
     * 与 [textOrNull] 只差一件事：**不因为空串就当没给**。
     *
     * 只有一处需要它 —— 评论正文。站方允许"纯贴纸评论"（`comment` 就是空串），
     * 用 `textOrNull()` 会把那一条整条丢掉（第一版就是这么丢的），
     * 而"屏上的条数比站方给的少"是本仓反复立案的那一族假话。
     */
    private fun JsonElement?.rawTextOrEmpty(): String =
        (this as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonElement?.numberOrStringOrNull(): String? = textOrNull()

    /**
     * 布尔格：**只有站方真写了 `true`/`false` 才作数**。
     *
     * 缺键、空串、`"1"` 这些形状一律 null，不折成 false —— 折成 false 等于替用户下一个
     * "他没关注这位"的断言，而那件事我们并不知道（[userDetail] 的 `is_followed` 就靠这一颗区分）。
     */
    private fun JsonElement?.booleanOrNull(): Boolean? =
        (this as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

    private fun extensionOf(url: String): String = url.substringAfterLast('.', "").substringBefore('?')

    /** 与 `PixivFeedParse` 同一张表（`x_restrict` 0/1/2）；认不出的取值走"站方已遮"那一档。 */
    private val RATING_BY_XRESTRICT = mapOf("0" to "general", "1" to "r18", "2" to "r15")
    private const val RATING_MASKED = "masked"
    private const val USERS = "https://www.pixiv.net/users/"
    private const val SAMPLE = 120
}
