package com.venera.compose.gallery.data

import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.NoInteractiveBypassTag
import com.venera.compose.gallery.domain.PixivSearchFilter
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * **带凭据**的那一侧：`app-api.pixiv.net`。
 *
 * ## 为什么不并进 [PixivClient]
 *
 * 那一颗的文件头写着一条红线：「只做未登录读取，不带任何凭据」。它服务的是头像、榜单、按标签、
 * 单张这四条**匿名也通**的腿；而 app-api 这一侧每一条都**必须**带 `Authorization: Bearer`，
 * 401 的含义也与别处不同（登录态过期，不是"这张图没了"）。
 * 两颗件各带各的红线，比一颗件里"有的方法带凭据、有的不带"安全 —— 后者最危险的不是写错，
 * 是**下一个人以为某个方法不带凭据**。
 *
 * ## 凭据怎么进来
 *
 * 由调用方（`AndroidGalleryPixivWorks`）从 `PixivCredentials.bearerOrNull()` 取，
 * **当作参数传进来**。这一颗件不认识登录态、不落盘、不刷新票据 ——
 * 它只回答一个问题："拿着这枚票据，站方给不给这一页。"
 *
 * ## 两根必须守住的
 *
 * 1. **票据不进任何消息**。所有抛出的消息只说端点、状态码、站方那句原因；
 *    `Request` 也不进日志（[NoInteractiveBypassTag] 那个 tag 只是行为开关，不带数据）。
 * 2. **401 / 403 要能被上行区分**。前者是"票据过期，请重新登录"，后者是"站方不让"——
 *    两者都读成"这一轮没取成"的话，用户不知道该去登录还是该稍后再试。
 */
class PixivAppClient internal constructor(private val engine: HttpEngine) {

    /**
     * 站方推荐：`/v1/illust/recommended`（用户 2026-10-17 点名「下面推荐应该自动换成 pixiv 站方的推荐」）。
     *
     * 三枚参数都是官方客户端那一套：
     * - `filter=for_android` —— 官方 Android 客户端的口径；
     * - `include_ranking_illusts=false` —— 不带榜单那几张（榜单在首页是**另一节**，混进来就重复了）；
     * - `include_privacy_policy=false` —— 我们不需要那段政策文本（它会白占一截 payload）。
     *
     * @param bearer 当前账号的 access token（调用方现取，别缓存）。
     * @param nextUrl 上一页答复里的 `next_url`。传 null = 取第一页。
     *
     * ⚠️ 翻页**只认站方给的那串地址**：它自己带着 `offset` 与 `filter`，我们不去拼。
     * 第二页开始只换地址、**不加参数** —— 往 `next_url` 上再叠一枚 `filter` 就是给它换口径。
     */
    suspend fun recommendedIllusts(
        bearer: String,
        nextUrl: String? = null,
    ): Result<PixivAppPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = nextUrl ?: RECOMMENDED.toHttpUrl().newBuilder()
                .addQueryParameter("filter", "for_android")
                .addQueryParameter("include_ranking_illusts", "false")
                .addQueryParameter("include_privacy_policy", "false")
                .build()
                .toString()
            PixivAppParse.illustrationRecommended(execute(url, bearer))
        }
    }

    /**
     * 排行榜：`/v1/illust/ranking`（2026-10-17 第七轮，用户那张截图上排行榜整档是空的）。
     *
     * 为什么登录着还要另开一条腿：`www` 那一条（`PixivClient.ranking`）是**匿名**的，
     * 现量对匿名请求时好时坏，而它一旦不给，屏上只剩一句"这一档没有答上" ——
     * 用户完全看不出是站方限了，还是图真的没有。这条腿带票据，且与推荐同一个 `illusts[]` 形状
     * （多一枚 `rank`），顺手把标题也一起拿回来。
     *
     * `mode` 走 [PixivRankingMode.appParam]（`day/week/…` 那一套，与 www 的 `daily/weekly/…` 不通用）。
     */
    suspend fun rankingIllusts(
        mode: PixivRankingMode,
        bearer: String,
        nextUrl: String? = null,
    ): Result<PixivAppPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = nextUrl ?: RANKING.toHttpUrl().newBuilder()
                .addQueryParameter("mode", mode.appParam)
                .addQueryParameter("filter", "for_android")
                .build()
                .toString()
            PixivAppParse.illustrationRanking(execute(url, bearer))
        }
    }

    /**
     * 关注列表：`/v1/user/following`（2026-10-17 第八轮，用户「关注画师你参考 pixez-miuix 是怎么拿到的」）。
     *
     * 参数就是 pixez 那一套（`UserRepository.getUserFollowingResponse`）：
     * `filter=for_android` + `user_id={自己}` + `restrict={档}`。
     * 三枚都必须在 —— 少了 `user_id` 站方会按"匿名"回 401/400，少了 `restrict` 只回该档的一部分。
     *
     * @param userId 自己的 pixiv 用户号（`PixivAuthState.SignedIn.userId`，登录响应里那枚）。
     * @param restrict 看哪一份关注账（2026-10-19 从写死 `public` 改成形参）。账号页「全部」那一档
     *   由调用方**发两趟再合**，不是这一腿一次给的 —— 站方没有"两档都要"这一个值。
     * @param nextUrl 上一页的 `next_url`；传 null 取第一页。翻页只认它，不自己拼 `offset`。
     */
    suspend fun following(
        bearer: String,
        userId: String,
        restrict: PixivRestrict,
        nextUrl: String? = null,
    ): Result<PixivUserPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = nextUrl ?: FOLLOWING.toHttpUrl().newBuilder()
                .addQueryParameter("filter", "for_android")
                .addQueryParameter("user_id", userId)
                .addQueryParameter("restrict", restrict.param)
                .build()
                .toString()
            PixivUserParse.userPreviews(execute(url, bearer), "pixiv 关注列表")
        }
    }

    /**
     * **关注动态**：`/v2/illust/follow`（2026-10-19，pixiv 个人账号页「动态 › 关注动态」那一档）。
     *
     * ## 它与 [following] 不是一回事，别合用
     *
     * [following] 给的是**人**（`user_previews[]`，不带作品），这一腿给的是**作品**（`illusts[]`）——
     * 也就是"我关注的人最近画了什么"。端点与参数形状由 `Notsfsssf/pixez-flutter` 的
     * `api_client.dart:getFollowIllusts` 互证（GPL 档，**只出表不搬码**）：
     * 只有 `restrict` 一枚查询参数，**不带 `user_id`** —— 这一腿问的是"谁的动态"，
     * 答案由票据自己决定。所以带错凭据不会"看到别人的动态"，只会 401。
     *
     * 答复与推荐/榜单/书签**同一个 `illusts[]` 形状**，因此复用 [PixivAppParse] 那一颗行解析。
     * 翻页同样只认站方的 `next_url`（它自带 `max_id` 游标）。
     *
     * ⚠️ 本仓**没有这一腿的真机读数**（形状只由外部 donor 与同族端点互证）。
     * 落地当轮要按本仓规矩带日期现量一次：公开档与私密档各打一趟，核 `illusts[]` 在不在、
     * `next_url` 给不给、私密档是不是真的只回非公开关注的那批。
     */
    suspend fun followIllustFeed(
        bearer: String,
        restrict: PixivRestrict,
        nextUrl: String? = null,
    ): Result<PixivAppPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = nextUrl ?: FOLLOW_ILLUST_FEED.toHttpUrl().newBuilder()
                .addQueryParameter("restrict", restrict.param)
                .build()
                .toString()
            PixivAppParse.illustrationFollowFeed(execute(url, bearer))
        }
    }

    /**
     * 推荐用户：`/v1/user/recommended`（同一条路的另一半）。
     *
     * ⚠️ 2026-10-17 之前这颗端点我们量到的是 **400 / 191**，当时就没接、只在屏上写明"没接上"。
     * 这次照 pixez 的调用点补上 `filter=for_android` 才发现差别就在这一枚 ——
     * 不带它时站方回 191（invalid filter 那一族），不是"这条端点不存在"。
     */
    suspend fun recommendedUsers(
        bearer: String,
        nextUrl: String? = null,
    ): Result<PixivUserPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = nextUrl ?: RECOMMENDED_USERS.toHttpUrl().newBuilder()
                .addQueryParameter("filter", "for_android")
                .build()
                .toString()
            PixivUserParse.userPreviews(execute(url, bearer), "pixiv 推荐用户")
        }
    }

    /**
     * 云端书签（收藏）：`/v1/user/bookmarks/illust`（2026-10-17 第三十一轮，用户稿那枚 🔖 卡）。
     *
     * 三枚参数与 [following] 同源（都要票据 + 自己的用户号）：
     * - `user_id={自己}` —— 书签是**账号维度**的，少了它站方按匿名回 401；
     * - `restrict={档}` —— 2026-10-19 从写死 `public` 改成形参（用户拍板「非公开接」，
     *   **推翻**了这一条原先写的"`private` 那一档不接"）。改判的理由：那道界是号主自己划的，
     *   由他本人在他的账号页上擦，不是我们替他擦 —— 但私密那一档**只出现在账号页选了非公开的时候**，
     *   首页、收藏 hub、分享出去的读数里都不含它（[PixivRestrict] 的头注那条边界）；
     * - `filter=for_android` —— 同族端点能不能答的关键（缺它回 400/191）。
     *
     * 翻页**只认站方的 `next_url`**（它自带 `max_bookmark_id` 游标），同 [recommendedIllusts]。
     * 答复是与推荐/榜单同一个 `illusts[]` 形状（无 `rank`），所以解析复用 [PixivAppParse.illustrationBookmarks]。
     *
     * ⚠️ 这一条与本地收藏（`GalleryFavoritesStore`）**是两套账**：这边读的是 pixiv 账号里的收藏，
     * 屏上两个入口分开摆（见收藏 hub 那两颗卡），不合并、不去重 —— 合并就意味着"删了这边那边也没了"
     * 这种用户没做过的操作。
     */
    suspend fun bookmarkIllusts(
        bearer: String,
        userId: String,
        restrict: PixivRestrict,
        nextUrl: String? = null,
    ): Result<PixivAppPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = nextUrl ?: BOOKMARK_ILLUSTS.toHttpUrl().newBuilder()
                .addQueryParameter("filter", "for_android")
                .addQueryParameter("user_id", userId)
                .addQueryParameter("restrict", restrict.param)
                .build()
                .toString()
            PixivAppParse.illustrationBookmarks(execute(url, bearer))
        }
    }

    /**
     * 搜索：`/v1/search/illust`（2026-10-17 深夜，用户「这三段应该是有的，你看看 pixez 是怎么做的」）。
     *
     * ## 为什么搜索要另起一条腿，而不是继续用 www 那条
     *
     * 稿上那三段（人气 / 收藏 / 作品）在匿名那条腿上是**发不出去的**：
     * `order=popular*`、`blt/bgt`、`type/mode` 现量全被静默忽略（读数见 `PixivSearchFilter`）。
     * pixez 用的是这一条（`ApiClient.getSearchIllust`），参数逐字如下：
     *
     * ```
     * /v1/search/illust?filter=for_android&merge_plain_keyword_results=true
     *   &word=…&sort=date_desc|date_asc|popular_desc
     *   &search_target=partial_match_for_tags|exact_match_for_tags
     *   &bookmark_num_min=…&start_date=…&end_date=…
     * ```
     *
     * `merge_plain_keyword_results=true` 这一枚不能省：不带它，纯关键词（不是标签）的命中会被
     * 分成"标签结果"与"标题/简介结果"两块，`illusts[]` 只回前者 —— 屏上就是"搜词搜不到图"。
     *
     * ⚠️ `sort=popular_desc` 是**会员档**：非会员站方会回一句 error。所以非会员点「人气」时
     * 调用点**不发这一条**，改走下面的 [popularPreview]（2026-10-19 改口径；再往前一版是"拒一下并且不改条件"）。
     *
     * @param nextUrl 上一页的 `next_url`（自带 `offset` 与全部参数）。传 null 取第一页。
     *   翻页时**一个参数都不加**：往 `next_url` 上再叠一枚 `sort` 就是给它换口径。
     */
    suspend fun searchIllust(
        bearer: String,
        word: String,
        filter: PixivSearchFilter,
        premium: Boolean,
        nextUrl: String? = null,
    ): Result<PixivAppPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = nextUrl ?: SEARCH_ILLUST.toHttpUrl().newBuilder()
                .addQueryParameter("filter", "for_android")
                .addQueryParameter("merge_plain_keyword_results", "true")
                .addQueryParameter("word", word)
                .apply { filter.appParams(premium).forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
                .toString()
            PixivAppParse.illustrationSearch(execute(url, bearer))
        }
    }

    /**
     * **人气预览**：非会员也能走的那条人气（2026-10-19 · 批 233-4，用户「参考原版 pixez 是怎么做无会员能用这个人气榜的」）。
     *
     * ## 依据（B 档只产表与结论，一行 donor 码不搬）
     *
     * pixez-flutter 的调用面就长这样（`api_client.dart:649-652`，2026-10-19 现读缓存源）：
     * ```
     * getPopularPreview(String keyword) ->
     *   {a}/v1/search/popular-preview/illust?filter=for_android
     *     &include_translated_tag_results=true&merge_plain_keyword_results=true
     *     &word={keyword}&search_target=partial_match_for_tags
     * ```
     * 调用点 `result_illust_list.dart:689-692`：非会员点「人气」时它 `toast('not premium')`
     * 之后**紧接着**发这一条（`onPremium` 回调里 `futureGet = ApiForceSource(getPopularPreview(word))`，
     * `:358-362`）。
     *
     * ## 三条口径差别，逐条落屏（判据在调用点 `GallerySearchViewModel`）
     *
     * - **签名里只有一个词**：那颗函数不吃 `sort` / 日期 / 收藏门槛 / AI / 比例中的任何一枚
     *   （`search_target` 在 donor 那侧是**写死**的 `partial_match_for_tags`，不是用户选的那一枚）。
     *   所以这里也**不接** [com.venera.compose.gallery.domain.PixivSearchFilter]：接了却不读，
     *   就是"屏上亮着一排条件、请求里一枚都没发"那一族假开关。被丢掉的几枚由调用点上屏说清楚。
     * - **只有这一页**：donor 的调用面没有 `offset`、也没有 `next_url` 的用法 ⇒ 这一条是"预览"，
     *   取完调用点就把"到底"置上，不许滚到底转圈却什么都不来（"假活"那一族）。
     *   ⚠️ 本机**没有第二页的反证**（不带票据只量到 400），这条是按 donor 的参数面写的；
     *   真机若发现站方其实给得出第二页，改的是这里与调用点那一行，不是屏上那句。
     * - **要票据**：不带票据现量回 **400**「Error occurred at the OAuth process」（2026-10-19 本机自证；
     *   对照：404 = 路由不存在，400 = 路由在、缺凭据）。没有匿名腿 ⇒ 没登录时说明白"这一档要登录"，
     *   不许静默退成日期序。
     */
    suspend fun popularPreview(
        bearer: String,
        word: String,
    ): Result<PixivAppPage> = withContext(Dispatchers.IO) {
        runCatching {
            // 参数串与 donor 那颗**逐枚同值**：这里没有一枚是我们替站方猜的默认值。
            val url = POPULAR_PREVIEW.toHttpUrl().newBuilder()
                .addQueryParameter("filter", "for_android")
                .addQueryParameter("include_translated_tag_results", "true")
                .addQueryParameter("merge_plain_keyword_results", "true")
                .addQueryParameter("word", word)
                .addQueryParameter("search_target", "partial_match_for_tags")
                .build()
                .toString()
            PixivAppParse.popularPreview(execute(url, bearer))
        }
    }

    /**
     * 作品页那一腿的**登录版**：`/v1/illust/detail?id=`。
     *
     * 为什么要它（2026-10-18 第四批第七件，用户「我已经登录而且站点也全无限制的，为啥有报错图片看不了」）：
     * 屏上那些 pixiv 卡的**来源**已经是登录腿（关注画师 / 推荐 / 排行榜 / 书签都带票据），而详情从前
     * 只有匿名腿 —— 站方对未登录的读者把成人向、仅登录可见、私密那几条**整条撤图**，于是
     * "卡明明在墙上、点开说没有"。这一腿带票据，同一台已登录的机器上就读得出。
     *
     * ⚠️ 本机**没有这一腿的真读数**：不带票据现量回 400「Error occurred at the OAuth process」
     * （2026-10-18，带票据才通），而我们不拿用户的票据做本地取证。键表因此是从
     * [PixivAppParse.illustrationRecommended] 那张**已现量过的同一个 illust 对象**抄的；
     * 万一某个键名对不上，失败形状是"读不出图"而不是崩 —— 调用点在读不出时退回匿名腿，
     * 屏上维持本轮之前的样子（这一条记在手册的待上机那一格）。
     */
    suspend fun illustDetail(bearer: String, illustId: Long): Result<PixivWorkDetail?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = ILLUST_DETAIL.toHttpUrl().newBuilder()
                    // ⚠️ 参数名是 **`illust_id`**，不是 `id`。2026-10-19 真机读数（用户截图）：
                    // 已登录时走这一腿，写成 `id` 站方回 **400**，`user_message` 是空串 ——
                    // 于是作品页**每一个作品**都报「pixiv 返回 400」。
                    // pixivpy `aapi.py:illust_detail` 是 `params = {"illust_id": illust_id}`，
                    // 与这里原先的写法只差这一个词。
                    //
                    // 这一族端点（`/v1/illust/detail`、`/v3/illust/comments`、`/v2/illust/related`）
                    // **全部**用 `illust_id`；写成 `id` 的表现是"400 且不带任何有用的话"，
                    // 从屏上几乎看不出是参数名错，所以把话记在这儿。
                    .addQueryParameter("illust_id", illustId.toString())
                    .build()
                    .toString()
                PixivAppParse.workDetail(execute(url, bearer))
            }
        }

    /**
     * **作品评论**：`GET /v3/illust/comments`（2026-10-19 · 批 231-F，**同日改判过版本号**）。
     *
     * ## 为什么只有登录腿拿得到评论
     *
     * 匿名那条走不通 —— 2026-10-19 本机实测四种形状全 **404**
     * （`ajax/illust/{id}/comments`、`ajax/illust/comments?illust_id=`、
     * `ajax/illusts/comments`、带 `lang=zh` 那一档）。`/ajax/illust/{id}` 只给得出
     * **条数**（`commentCount`），给不出评论本身。
     *
     * 所以评论内容**必须登录**；未登录时调用点不许摆空排，要照实说"登录才看得到"。
     *
     * ## ⚠️ 版本号是量出来的，不是抄来的（2026-10-19 真机读数改判）
     *
     * 第一版按 pixivpy `aapi.py:illust_comments` 写成 `/v1/illust/comments`，真机与匿名探针都回
     * **404「指定されたエンドポイントは存在しません」** —— 而 404 在本仓的判读口径里是**路由不存在**
     * （对照件：同一颗 client 打 `/v1/illust/detail` 无票据回 **400**「Error occurred at the OAuth
     * process」，那才是"路由在、只是缺凭据"）。本鱼当时据此写下"评论结构性取不到"，**那句是错的**：
     * 错在候选集是我自己猜的（v1 / v2 / new_comment / web/v1/…），没先去读同场景参考项的端点表。
     *
     * 参考项 pixez-miuix 用的是 `GET /v3/illust/comments?illust_id=`（`IllustRepository.kt:404`，
     * GPL-3.0 ⇒ 这里只取"端点与形状"这张表，不搬代码）。2026-10-19 匿名复量两条：
     * `v1/illust/comments` = **404**、`v3/illust/comments` = **400 缺凭据** ⇒ 站方早就把这一族挪到 v3，
     * pixivpy 那份表是旧的。
     *
     * ## 形状（v3 实测到的那几格 + 参考项那张表）
     *
     * ```
     * params = { "illust_id": illust_id }        # ⚠️ 不是 "id"
     * ```
     *
     * 答复：`comments[]`（`id` / `comment` / `date` / `user{name,account,profile_image_urls.medium}` /
     * `parent_comment` / `has_replies` / `stamp`）+ `next_url` + `total_comments`。
     * **不带** `include_total_comments` 那枚 v1 参数 —— 它没在 v3 上量过，而 v3 的
     * `total_comments` 本来就在答复里；未量过的参数不发是本仓的既有纪律（见 `PixivSearchFilter.webParams`）。
     *
     * @param nextUrl 上一页答复里的 `next_url`（自带游标）。传 null 取第一页 ——
     *   **不自己拼 offset**（与推荐 / 榜单 / 书签同一条纪律）。
     */
    suspend fun illustComments(
        bearer: String,
        illustId: Long,
        nextUrl: String? = null,
    ): Result<PixivCommentPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = nextUrl ?: ILLUST_COMMENTS.toHttpUrl().newBuilder()
                .addQueryParameter("illust_id", illustId.toString())
                .build()
                .toString()
            PixivAppParse.illustComments(execute(url, bearer))
        }
    }
    /**
     * 会员身份：`/v1/user/detail` 里的 `user.is_premium`（2026-10-18，用户「人气 / 收藏照 pixez 的做法」）。
     *
     * pixez 也是这么取的：它的账号库存着 `isPremium`，点「人气」那一档时吃的就是它
     * （`result_illust_list.dart:689` —— 非会员 toast 并且**不发请求**）。
     *
     * **读不到就当非会员**：这一格只决定"收藏用哪一种发法"（会员发 `bookmark_num_min`、
     * 其余走词尾 `users入り`），而词尾那条对两种身份都真生效 ⇒ 判错方向的代价是少用一枚参数，
     * 不是"屏上亮着、请求里没发"那种静默假开关。
     */
    suspend fun userPremium(bearer: String, userId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching { PixivAppParse.userPremium(execute(userDetailUrl(userId), bearer)) }
        }

    /**
     * 画师页的**详情**那一腿：`/v1/user/detail`（2026-10-19）。
     *
     * 与 [userPremium] 打的是**同一个端点**（URL 构造共用 [userDetailUrl]，一份不写两遍），
     * 只是各取各的格子：那一颗要会员旗标决定搜索怎么发，这一颗要总数与关注态上屏。
     *
     * 这一腿带票据，所以它给的是**站方眼里的真数**：
     * - `total_illusts` / `total_manga` 含成人档（匿名 id 表按口径过滤过）；
     * - `is_followed` 是"我关注过这位没有"—— 关注按钮那两个字以此为真相，
     *   本地那本账退成离线兜底（用户 2026-10-19 拍板「站方为准，本地只当缓存」）。
     *
     * 未登录时调用点**不发这一跳**（发了必 401），屏上退匿名那套读数并明说这一档少给什么。
     */
    suspend fun userDetail(bearer: String, userId: String): Result<PixivUserDetail> =
        withContext(Dispatchers.IO) {
            runCatching { PixivAppParse.userDetail(execute(userDetailUrl(userId), bearer)) }
        }

    /** `/v1/user/detail` 的地址。**只有这一处**拼它，两腿共用。 */
    private fun userDetailUrl(userId: String): String = USER_DETAIL.toHttpUrl().newBuilder()
        .addQueryParameter("user_id", userId)
        .addQueryParameter("filter", "for_android")
        .build()
        .toString()

    /**
     * 画师的作品列表（登录版）：`/v1/user/illusts?filter=for_android&user_id=…&type=…`。
     *
     * ## 为什么换掉匿名那两跳
     *
     * 从前这一屏走 `www/ajax/user/{id}/profile/all`（拿 id 表）+ `profile/illusts`（按 id 取卡）。
     * 那条 `profile/all` 是**匿名**的，站方按口径把成人档整批滤掉 —— 2026-10-19 用户读数：
     * 同一位画师屏上「插画 3」、pixiv 网页「插画·漫画 26」。而更难看的一层是：
     * 那 3 枚 id 再走匿名 `profile/illusts` 一条都没解析出声，于是屏上同时念出
     * 「这一位还没有插画作品」（假话）和「已经到底了 · 共 3 张」（真话）。
     *
     * 这一腿一次给一页**带全字段的作品**（不用再打第二跳），并且带票据 ⇒ 含成人档。
     * 端点与参数面由两份独立来源互证：pixivpy `aapi.py:user_detail`/`user_illusts`（Unlicense 档）
     * 与 pixez `UserRepository.getUserIllustsResponse`（GPL 档，**只出表不搬码**）。
     *
     * 翻页只认站方的 `next_url`（自带 `offset`）—— 自己拼页码就是猜站方分页口径。
     *
     * @param type `"illust"` / `"manga"` / `"novel"`：站方自己的三个桶，与屏上那三段的前两段一一对应。
     *   ⚠️ 传错桶**不报错**，只会回另一类作品，所以调用方必须把段与它绑死。
     */
    suspend fun userIllusts(
        bearer: String,
        userId: String,
        type: String,
        nextUrl: String? = null,
    ): Result<PixivAppPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = nextUrl ?: USER_ILLUSTS.toHttpUrl().newBuilder()
                .addQueryParameter("filter", "for_android")
                .addQueryParameter("user_id", userId)
                .addQueryParameter("type", type)
                .build()
                .toString()
            PixivAppParse.userIllusts(execute(url, bearer))
        }
    }

    /**
     * **关注一位画师**：`POST /v1/user/follow/add`，form `user_id` + `restrict`。
     *
     * ⚠️ 这一跳**会改变用户在 pixiv 账号上的真实状态**，与 [bookmarkAdd] 同一条纪律：
     * 只在用户点了那一下才发，绝不在装载时自动补写。
     * `restrict=private` 是会员档（非会员发过去站方拒），所以判不准时给 `public`。
     */
    suspend fun followAdd(bearer: String, userId: String, private: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                executePost(
                    FOLLOW_ADD,
                    bearer,
                    listOf(
                        "user_id" to userId,
                        "restrict" to if (private) "private" else "public",
                    ),
                    "pixiv 关注这一跳",
                )
                Unit
            }
        }

    /**
     * **取关一位画师**：`POST /v1/user/follow/delete`，只带 `user_id`。
     *
     * 与 [followAdd] 成对 —— 本地删了而站方不撤，用户下次进 pixiv 还会看到这位挂在关注里，
     * 读起来就是"应用撤了但站方没撤"，比不接同步更糟。
     */
    suspend fun followRemove(bearer: String, userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                executePost(
                    FOLLOW_DELETE,
                    bearer,
                    listOf("user_id" to userId),
                    "pixiv 取关这一跳",
                )
                Unit
            }
        }

    /**
     * 把一张图加进**站方**的收藏（2026-10-18，用户「pixiv 站点收藏图片自动同步到 pixiv 站点」）。
     *
     * 形状由两份独立来源互证：pixivpy `aapi.py:610-628`（Unlicense 档）与 pixez `api_client.dart:240-265`
     * （GPL 档，只出表）—— `POST /v2/illust/bookmark/add`（⚠️ **加是 v2、删是 v1**，站方自己就不对称），
     * form-urlencoded：`illust_id` + `restrict`（`public` / `private`），可选 `tags[]`
     * （多枚标签**空格拼成一枚值**，那是站方的形状）。鉴权只带 Bearer —— 不需要 www 私 API 那一族才要的签名。
     *
     * `private` 是**会员档**：非会员发过去会被站方拒 ⇒ 调用方按 [PixivPremium] 给这一枚，
     * 判不准时给 `public`（宁可公开，也不要留一条"看起来收藏上了其实站方没收"的假记录）。
     */
    suspend fun bookmarkAdd(bearer: String, illustId: Long, private: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                executePost(
                    BOOKMARK_ADD,
                    bearer,
                    listOf(
                        "illust_id" to illustId.toString(),
                        "restrict" to if (private) "private" else "public",
                    ),
                    "pixiv 加收藏这一跳",
                )
                Unit
            }
        }

    /**
     * 从站方收藏里撤掉一张：`POST /v1/illust/bookmark/delete`，只带 `illust_id`。
     *
     * 与 [bookmarkAdd] 成对 —— 本地取消而站方不撤，用户下次进 pixiv 主页还会看到那张，
     * 读起来就是"应用撤了但站方没撤"，比不接同步更糟。
     */
    suspend fun bookmarkRemove(bearer: String, illustId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                executePost(
                    BOOKMARK_DELETE,
                    bearer,
                    listOf("illust_id" to illustId.toString()),
                    "pixiv 撤收藏这一跳",
                )
                Unit
            }
        }

    /**
     * 画师搜索：`/v1/search/user`（2026-10-18，用户「搜索作者（画师）的分栏怎么没做」）。
     *
     * pixez 的搜索结果页就是一颗两页签的 `TabBar`：**插画 / 画师**（`result_page.dart`），
     * 画师那一页只调这一条端点（`ApiClient.getSearchUser`）：
     *
     * ```
     * /v1/search/user?filter=for_android&word=…
     * ```
     *
     * 答复形状与 `/v1/user/recommended` 同族（`user_previews[]` + `next_url`），
     * 所以解析复用 [PixivUserParse.userPreviews] —— 两份各写一遍的话，站方加一格字段
     * 就得记着改两处（本仓为这类分叉付过账）。
     *
     * ⚠️ **必须带票据**：这一条与 [searchIllust] 的匿名 www 那一跳不同，没有匿名版本
     * （`www.pixiv.net/search_user.php` 那一套早就不给 JSON 了）。调用点在没有票据时要
     * **说清楚为什么搜不了画师**，不许静默退回"作品"那一栏。
     *
     * @param nextUrl 上一页的 `next_url`（自带 `offset`）。传 null 取第一页。
     */
    suspend fun searchUser(
        bearer: String,
        word: String,
        nextUrl: String? = null,
    ): Result<PixivUserPage> = withContext(Dispatchers.IO) {
        runCatching {
            val url = nextUrl ?: SEARCH_USER.toHttpUrl().newBuilder()
                .addQueryParameter("filter", "for_android")
                .addQueryParameter("word", word)
                .build()
                .toString()
            PixivUserParse.userPreviews(execute(url, bearer), "pixiv 画师搜索")
        }
    }

    /**
     * 写口共用的一跳（form POST）。
     *
     * 与 [execute] 分开写，而不是给它加一枚"是不是 POST"的参数：写口失败必须**说得出是哪一枚写**，
     * 而读口那条"不许在共用件里写死某条腿的名字"的理由仍然成立（它服务五条读腿）。
     */
    private fun executePost(
        url: String,
        bearer: String,
        fields: List<Pair<String, String>>,
        who: String,
    ): String {
        val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder()
            .url(url)
            .post(form)
            .header("Authorization", "Bearer $bearer")
            .header("Accept", "application/json")
            // 与读口同一套官方客户端头：app-api 对"看起来不像 App"的请求给 403 而不是 401。
            .header("App-OS", "android")
            .header("App-OS-Version", APP_OS_VERSION)
            .header("App-Version", APP_VERSION)
            .header("User-Agent", "PixivAndroidApp/$APP_VERSION (Android $APP_OS_VERSION)")
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when {
                response.code == 401 -> throw IOException(
                    "pixiv 说这枚登录票据已经不能用了（401）—— 点顶栏那颗头像重新登录一次",
                )
                response.code == 403 -> throw IOException(
                    "$who 被站方挡下（403）—— 多半是这台设备的登录态被判成异常",
                )
                !response.isSuccessful -> throw IOException("$who 返回 ${response.code}：${body.take(SNIPPET)}")
            }
            return body
        }
    }

    private fun execute(url: String, bearer: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $bearer")
            .header("Accept", "application/json")
            // 这三枚是官方客户端都会带的：app-api 对"看起来不像 App"的请求会给 403 而不是 401。
            .header("App-OS", "android")
            .header("App-OS-Version", APP_OS_VERSION)
            .header("App-Version", APP_VERSION)
            .header("User-Agent", "PixivAndroidApp/$APP_VERSION (Android $APP_OS_VERSION)")
            // 标签译名（`tags[].translated_name`）**只在这枚头存在时才有值**（2026-10-20，
            // 依据：pixivpy `set_accept_language` 的注释原文 + 它那条 issue #76「缺这枚头时字段恒为 None」）。
            // 不挂它的话，我们的译名那一路永远是空表 —— 而屏上"没有译文"与"我们没问"长得一模一样。
            .header("Accept-Language", PIXIV_TAG_LANGUAGE)
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        engine.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when (response.code) {
                401 -> throw IOException("pixiv 说这枚登录票据已经不能用了（401）—— 点顶栏那颗头像重新登录一次")
                403 -> throw IOException("pixiv 拒了这一跳（403）—— 多半是这台设备的登录态被站方判成异常")
                else -> if (!response.isSuccessful) {
                    // 这句**不带端点名**：这一颗件服务推荐/榜单/关注/推荐用户/书签五条腿，
                    // 写死任何一条腿的名字都会在别条腿失败时念错（书签失败念成"推荐返回"就是假归因）。
                    throw IOException("pixiv 返回 ${response.code}：${body.take(SNIPPET)}")
                }
            }
            return body
        }
    }

    companion object {
        private const val RECOMMENDED = "https://app-api.pixiv.net/v1/illust/recommended"
        private const val RANKING = "https://app-api.pixiv.net/v1/illust/ranking"
        private const val FOLLOWING = "https://app-api.pixiv.net/v1/user/following"
        private const val FOLLOW_ILLUST_FEED = "https://app-api.pixiv.net/v2/illust/follow"
        private const val USER_ILLUSTS = "https://app-api.pixiv.net/v1/user/illusts"
        private const val FOLLOW_ADD = "https://app-api.pixiv.net/v1/user/follow/add"
        private const val FOLLOW_DELETE = "https://app-api.pixiv.net/v1/user/follow/delete"
        private const val RECOMMENDED_USERS = "https://app-api.pixiv.net/v1/user/recommended"
        private const val BOOKMARK_ILLUSTS = "https://app-api.pixiv.net/v1/user/bookmarks/illust"
        private const val SEARCH_ILLUST = "https://app-api.pixiv.net/v1/search/illust"
        private const val POPULAR_PREVIEW = "https://app-api.pixiv.net/v1/search/popular-preview/illust"
        private const val USER_DETAIL = "https://app-api.pixiv.net/v1/user/detail"
        private const val ILLUST_DETAIL = "https://app-api.pixiv.net/v1/illust/detail"
        private const val BOOKMARK_ADD = "https://app-api.pixiv.net/v2/illust/bookmark/add"
        private const val BOOKMARK_DELETE = "https://app-api.pixiv.net/v1/illust/bookmark/delete"
        private const val SEARCH_USER = "https://app-api.pixiv.net/v1/search/user"
    private const val ILLUST_COMMENTS = "https://app-api.pixiv.net/v3/illust/comments"

        /**
         * 这两枚只是"像官方客户端"的标识，**不是**我们的版本号，也不参与任何功能判定。
         * 站方按它决定回哪种形状（`filter=for_android` 那一路）；哪天它开始挑版本，改这里一处。
         */
        private const val APP_VERSION = "6.0.0"
        private const val APP_OS_VERSION = "14"
        private const val SNIPPET = 160

        /**
         * 要站方按哪种语言回**标签译名**（`tags[].translated_name`）。
         *
         * 取 `zh-cn` 而不是跟系统走：这个字段只影响**屏上那几个字的显示**，
         * 而界面语言是中文（`values/strings.xml` 与全仓文案都是中文）——
         * 系统语言是英文时跟过去，屏上就会变成「女の子 (Girl)」混一串中文界面里的英文。
         * 用户若真要看原文，标签本身就是原文，就在括号里。
         *
         * ⚠️ 这是**显示口径**，不参与任何查询：`word=` 发的永远是站方原名。
         */
        internal const val PIXIV_TAG_LANGUAGE = "zh-cn"

        @Volatile
        private var INSTANCE: PixivAppClient? = null

        fun getInstance(engine: HttpEngine): PixivAppClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: PixivAppClient(engine).also { INSTANCE = it }
        }
    }
}
