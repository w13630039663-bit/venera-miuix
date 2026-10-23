package com.venera.compose.gallery.data

import com.venera.compose.gallery.domain.PixivWorkPage
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * pixiv 两条匿名腿的**形状解析**（批次 pixiv · T3）：榜单 `ranking.php` 与按标签那一跳
 * `ajax/search/artworks/{词}`。
 *
 * 为什么手写逐字段走、不给 `@Serializable` DTO（与 [GalleryArtistEndpointParse] 同一个理由，
 * 但这里还多一条更硬的）：**同一种意思在两腿里类型相反**，2026-10-16 深夜现量 ——
 *
 * | | `ranking.php` | `ajax/search/artworks/{词}` |
 * |---|---|---|
 * | 作品号 | `illust_id` 是 **number** | `id` 是 **string** |
 * | 作者号 | `user_id` 是 **number** | `userId` 是 **string** |
 * | 页数 | `illust_page_count` 是 **string**（`"8"`） | `pageCount` 是 **number**（`1`） |
 * | 分级 | **没有这一格** | `xRestrict` 是 **number** |
 *
 * 声明成 `Int`/`String` 任一种，另一腿就**当场反序列化失败**；而 DTO 押错的另一种表现更阴 ——
 * 键对不上时取默认值，编译与运行都不报，屏上读起来是"这一站没有图"。
 *
 * 统一口径照旧只有两条：
 * 1. **整份答复读不懂 = 抛**（不是交空表）；
 * 2. **有 N 条却一条都认不出 = 也抛** —— 站方改了键名的表现就是"整墙消失"，
 *    那与"这一页真的没有作品"必须能被区分开。只有**数组本身为空**才是合法空表
 *    （现量：一个不存在的词 ⇒ `data` 是 `[]`、`total` 是 0，站方答上了"没有"）。
 *
 * 两条都按用户 2026-10-16 的定档处理多页组图：**取第 0 页当这一张**。
 * 两腿给的 `url` 本来就是 `…_p0_…jpg`，所以这一档**零额外请求**（不必打 `/ajax/illust/{id}/pages`）。
 */
internal object PixivFeedParse {

    private val json = Json { ignoreUnknownKeys = true }

    private const val ERROR = "error"
    private const val MESSAGE = "message"
    private const val BODY = "body"

    /**
     * 榜单那一腿：`ranking.php?mode={档}&format=json&p={页}`。
     *
     * 档位名**只认站方那一套**（`daily/weekly/monthly/rookie/original/male/female`）——
     * `day/week/month` 现量 404，而 R-18 那两档匿名是 **403 整档墙**（不是静默过滤），
     * 所以这里进来的每一条都来自"站方允许匿名读的那一档"。
     *
     * ⚠️ 分级格在这条腿上**不存在**（现量：`"rating" in contents[0] === false`）。
     * [GalleryPost.isAdultMarked] 那句是「不在安全白名单里就算成人」，留空串会让**每张 p 站卡**
     * 被打码。所以这里给出翻译而不是猜测：`is_masked=true` ⇒ 站方自己已经遮了 ⇒ 当成人档处理；
     * 其余记成 `"general"`，依据就是上面那句"匿名只回得到非 R-18 的那几档"。
     * `illust_content_type` 那 13 枚旗标**不当档位用**（`sexual` 是"多少人标了性内容"的计数）。
     */
    fun ranking(body: String): List<PixivWork> {
        val root = parseObject(body, "pixiv 榜单")
        val contents = root["contents"].asArrayOrNull()
            ?: throw IOException("pixiv 榜单答复里没有 contents 数组：${body.take(SAMPLE)}")
        val posts = contents.mapNotNull { element ->
            val record = element as? JsonObject ?: return@mapNotNull null
            val id = record["illust_id"].numberOrStringOrNull()?.toLongOrNull() ?: return@mapNotNull null
            val image = record["url"].textOrNull().orEmpty()
            if (image.isEmpty()) return@mapNotNull null
            val post = GalleryPost(
                site = GallerySite.PIXIV,
                id = id,
                tags = record["tags"].joinedText(separator = " "),
                rating = if (record["is_masked"].isTrue()) RATING_MASKED else RATING_GENERAL,
            // 这一腿**没有** `aiType`（载荷那 25 枚键表里没有 AI 格，读数记在 pixivAiFlagOf 与
            // `docs/rounds/gallery-pixiv-placement-options-2026-10-16.md`）⇒ `siteAiFlag` 留 null：
            // "站方没说"不是"站方说不是 AI"。这一节的角标于是仍走标签词表那一档兜底。
                // `rating_count` 这格先**不喂任何字段**：名字读起来像评分数，取值（现量 2402）
                // 又像收藏数，语义未定性 ⇒ 喂进 score 或 favCount 都是把猜测写进读数。
                author = record["user_name"].textOrNull().orEmpty(),
                source = record["user_id"].numberOrStringOrNull()?.let { USERS + it }.orEmpty(),
                width = record["width"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
                height = record["height"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
                // 卡上那枚「几 P」的出处（2026-10-18）。这一腿给的是**字符串** `"8"`，
                // 而搜索腿那格叫 `pageCount` 且是数字 —— 同一件事在两腿里类型相反，所以两侧
                // 都走 numberOrStringOrNull 那一颗，押任一种形状都会让另一腿静默解成 null。
                pageCount = record["illust_page_count"].numberOrStringOrNull()?.toIntOrNull(),
                previewUrl = image,
                fileExt = extensionOf(image),
                // 榜单这一腿**给** `illust_type`（现量字符串 `"0"`/`"1"`/`"2"`，逐条对过）
                // ⇒ 墙上那排卡也能认出"这作会动"（2026-10-23 动图播放）。
                ugoira = record["illust_type"].numberOrStringOrNull() == "2",
            )
            PixivWork(
                post = post,
                title = record["title"].textOrNull().orEmpty(),
                rank = record["rank"].numberOrStringOrNull()?.toIntOrNull(),
                // 旗标只有**一处出处**（行模型那一格），这里转交而不是按同一键再读一遍。
                ugoira = post.ugoira,
            )
        }
        requireRecognized(posts.size, contents.size, "pixiv 榜单")
        return posts
    }

    /**
     * 按标签那一腿：`ajax/search/artworks/{词}`。**词一律被站方当标签名解释**
     * （现量：`extraData.meta.canonical` 就是 `/tags/{词}`，`all` 命中的是同名标签而不是"全站"），
     * 所以这一跳**没有"全站最新"** —— 首页那一节要挂在一个具体的词上，词的来处由调用方决定。
     *
     * 默认序**就是** `createDate` 降序（六份样本全部单调，最新一条落在"几分钟前"），
     * 而 `sort=` / `order=` / `order=popular_d` 六种变体首条一字不差 ⇒ 那些参数在这一跳是被忽略的，
     * 调用方**别靠传参拿"最新"**。
     *
     * 分级这条腿**有**：`xRestrict`（现量 0/1/2）。翻译表见 [RATING_BY_XRESTRICT]。
     */
    fun tagWorks(body: String): List<PixivWork> {
        val root = parseObject(body, "pixiv 标签作品")
        if (root[ERROR].isTrue()) {
            throw IOException(
                "pixiv 标签作品回 error=true（message=${root[MESSAGE]?.jsonPrimitive?.contentOrNull.orEmpty()}）",
            )
        }
        val data = root[BODY]?.jsonObject?.get("illustManga")?.asArrayOrNullOf("data")
            ?: throw IOException("pixiv 标签作品答复里没有 body.illustManga.data：${body.take(SAMPLE)}")
        val posts = data.mapNotNull { element ->
            val record = element as? JsonObject ?: return@mapNotNull null
            val id = record["id"].numberOrStringOrNull()?.toLongOrNull() ?: return@mapNotNull null
            val image = record["url"].textOrNull().orEmpty()
            if (image.isEmpty()) return@mapNotNull null
            val post = GalleryPost(
                site = GallerySite.PIXIV,
                id = id,
                tags = record["tags"].joinedText(separator = " "),
                rating = RATING_BY_XRESTRICT[record["xRestrict"].numberOrStringOrNull()] ?: RATING_MASKED,
                // 站方的 AI 硬位：这一腿**给**（`aiType`，翻译见 pixivAiFlagOf）。角标从此不靠猜标签。
                siteAiFlag = pixivAiFlagOf(record["aiType"].numberOrStringOrNull()),
                author = record["userName"].textOrNull().orEmpty(),
                source = record["userId"].numberOrStringOrNull()?.let { USERS + it }.orEmpty(),
                width = record["width"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
                height = record["height"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
                // 这一腿是**数字** 5（榜腿那格是字符串 "8"）；缺那一格就留 null，不补 1。
                pageCount = record["pageCount"].numberOrStringOrNull()?.toIntOrNull(),
                previewUrl = image,
                fileExt = extensionOf(image),
                // `illustType` 0=插画 1=漫画 2=动图（现量三档都出现过）。旗标的唯一出处在行模型。
                ugoira = record["illustType"].numberOrStringOrNull() == "2",
            )
            // 这一腿站方不给排名 ⇒ rank 交 null：序号摆上去就必须是真序号，
            // 拿"这一排里的第几枚"冒充站方排名就是编读数。
            PixivWork(
                post = post,
                title = record["title"].textOrNull().orEmpty(),
                rank = null,
                // 搜索答复里**带头像**（`profileImageUrl`，现量是 `..._50.png`）——
                // 网格卡走「头像 + 标题 + 画师名」那一档时靠它，取不到留空串（卡上退首字母座）。
                authorAvatarUrl = record["profileImageUrl"].textOrNull().orEmpty(),
                // 「仅动图 / 排除动图」那一档只能靠它（站方搜索接口没有对应参数）；
                // 这里转交行模型那一格，两处不各读一遍同一键。
                ugoira = post.ugoira,
            )
        }
        requireRecognized(posts.size, data.size, "pixiv 标签作品")
        return posts
    }

    /**
     * 单张按号那一腿：`/ajax/illust/{id}`。列表与「历史」那一行按号回取吃它（只要共用那颗行模型），
     * 作品页吃 [workDetailPage]（同一份答复，多读只有作品页要的那几格）。
     *
     * 现量（真号 150822243 / 150926978）：`body` 56 枚键，其中 `urls.{mini,thumb,small,regular,original}`
     * 五档齐、`illustId` 与 `userId` 是**字符串**、`pageCount`/`width`/`height`/`xRestrict` 是**数字**、
     * 标签在 `tags.tags[]` 里每枚带一个 `tag`。
     * ⚠️ `original` 的扩展名**逐张不同**（同一位实测一张 `.jpg`、另一张 `.png`）⇒ 扩展名一律从地址尾巴取，
     * 别写死 jpg。
     *
     * **列表这一侧**仍然只取第 0 页（用户 2026-10-16 定档，卡上本来就只画一张）；
     * 作品页要逐页连读，走 [workPages] 那一跳 —— 那句定档**不覆盖作品页**（2026-10-18 改判）。
     *
     * 交 `null` 只有一种情形：站方答上了、但这条没有可用的原图地址（被删/被锁）。
     * 整份读不懂仍然抛 —— "没有这一张"与"我们看不懂这一张"是两句话。
     */
    fun workDetail(body: String): GalleryPost? = postOf(detailOf(body))

    /**
     * 作品页那一腿：同一份答复多读八格（原题、作者号、账号、页数、逐枚标签、浏览数、发布时间、
     * 评论数与开关、动图旗标）。
     *
     * 读数（2026-10-18 现量，真号 150320368）：`title` = `[CHARA] Kakkoi Elbertson`、`pageCount` = 4、
     * `viewCount` = 851、`bookmarkCount` = 32、`commentCount` = 6、`commentOff` = 0、
     * `uploadDate` = `2026-10-01T06:12:00+00:00`、`tags.tags` 7 枚（内层键叫 `tag` **不叫** `name`）、
     * `illustType` = 0、`seriesNavData` = **null**（这条腿没有"上一张/下一张"可拿）。
     *
     * ⚠️ 这一腿的 `urls.original` 尾巴上写着 `_p0` ⇒ **只到第 0 页**。`pageCount > 1` 时调用方
     * 必须再打 [workPages] 那一跳把剩下的取齐（[PixivClient.workDetail] 里对账）。
     *
     * 2026-10-18 第四批第七件：这一腿**读不出图**时不再一律交 null —— 站方在那一份答复里
     * 竖了旗标的（私密 / 隐藏 / 成人向 / 仅登录）按档抛，屏上念得出是哪一面墙；
     * 一枚旗标都没竖的才交 null（≈已被删 / 号错 / 审核中）。见 [lockOrNothing]。
     */
    fun workDetailPage(body: String): PixivWorkDetail? {
        val detail = detailOf(body)
        val post = postOf(detail) ?: return lockOrNothing(detail)
        // 站方报的页数原样留着（缺格 = null ⇒ 卡上那枚角标不摆）；下面那颗 `pageCount` 才是
        // "对账用的那一档"，缺格时它必须有个数才能把 `pages.size == pageCount` 这条不变式写完。
        val reportedPages = detail["pageCount"].numberOrStringOrNull()?.toIntOrNull()
        val isUgoira = detail["illustType"].numberOrStringOrNull() == "2"
        return PixivWorkDetail(
            post = post.copy(pageCount = reportedPages, ugoira = isUgoira),
            title = detail["title"].textOrNull().orEmpty(),
            authorId = detail["userId"].numberOrStringOrNull()?.toLongOrNull(),
            authorAccount = detail["userAccount"].textOrNull().orEmpty(),
            pages = listOf(
                PixivPage(
                    // 详情腿自己就带五档与宽高：单页作品也不必多打一跳就有中档（`largeUrl` 存的正是 `regular`）。
                    mediumUrl = post.largeUrl.ifBlank { post.fileUrl },
                    originalUrl = post.fileUrl,
                    width = detail["width"].numberOrStringOrNull()?.toIntOrNull(),
                    height = detail["height"].numberOrStringOrNull()?.toIntOrNull(),
                ),
            ),
            pageCount = (reportedPages ?: 1).coerceAtLeast(1),
            tags = ((detail["tags"] as? JsonObject)?.get("tags") as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.get("tag").textOrNull() }
                ?: emptyList(),
            tagTranslations = tagTranslationsOf((detail["tags"] as? JsonObject)?.get("tags") as? JsonArray),
            viewCount = detail["viewCount"].numberOrStringOrNull()?.toIntOrNull(),
            uploadDate = detail["uploadDate"].textOrNull(),
            commentCount = detail["commentCount"].numberOrStringOrNull()?.toIntOrNull(),
            commentsOff = detail["commentOff"].numberOrStringOrNull() == "1",
            ugoira = isUgoira,
        )
    }

    /**
     * "答上了却没有可看的图"到底是哪一种（判式在 [PixivWorkPage.lockReason]，四档各有句子）。
     *
     * 四枚旗标都按**站方给的那一格的字面**读：`restrict` / `xRestrict` 是数字，
     * `isLoginOnly` / `isUnlisted` 现量是布尔字面（`true`）；缺键一律当"没给"而不是"没竖"，
     * 所以它们交给 [PixivWorkPage.lockReason] 的都是可空 —— 把缺键读成置位就会对一条正常作品
     * 念"这一条被作者设为私密"（假归因那一族）。
     */
    private fun lockOrNothing(detail: JsonObject): PixivWorkDetail? {
        val reason = PixivWorkPage.lockReason(
            isLoginOnly = detail["isLoginOnly"].truthyOrNull(),
            xRestrict = detail["xRestrict"].numberOrStringOrNull()?.toIntOrNull(),
            restrict = detail["restrict"].numberOrStringOrNull()?.toIntOrNull(),
            isUnlisted = detail["isUnlisted"].truthyOrNull(),
        ) ?: return null
        throw IOException(reason.sentence)
    }

    /**
     * 多页那一跳 `/ajax/illust/{id}/pages`：逐页的**两档地址 + 站方报的宽高**，顺序就是答复里那一排的顺序。
     *
     * 中档（`urls.regular`）给作品页连读，原图档（`urls.original`）只给大图播放器那一层的 HD ——
     * 从前这一腿只取原图，于是"打开作品页"= 一上来拉四张原图（用户 2026-10-18 真机报"加载图片
     * 应该用低清点的图片"）。宽高是图回来之前按比例占位的唯一依据，缺键交 null，不编 1:1。
     *
     * ⚠️ 这一跳的 `body` 本身就是**数组**（2026-10-18 现量 150320368：4 个元素，各带
     * `urls.{thumb_mini,small,regular,original}` + `width`/`height`，尾巴 `_p0`…`_p3`，扩展名 `.png`）——
     * **没有** `meta_pages` 那一层，那是 app-api 的形状。两族的键名别互相套。
     *
     * [expected] 是详情腿说的页数，**逐页对账**：认出来的枚数不等就抛。
     * "4 页只回 3 页"与"这一作本来只有一页"是两句话 —— 前一句必须是错，不能被读成后一句。
     * 只有**两档都没有**的那一页才算丢页；缺一档退回另一档（宁可在 HD 那一档说"没有更高清"，
     * 也不少画一页）。所以这一条腿只在 expected > 1 时打（单页作品站方仍回一枚，但那一跳是白花的）。
     */
    fun workPages(body: String, expected: Int): List<PixivPage> {
        val root = parseObject(body, "pixiv 多页")
        if (root[ERROR].isTrue()) {
            throw IOException(
                "pixiv 多页回 error=true（message=${root[MESSAGE]?.jsonPrimitive?.contentOrNull.orEmpty()}）",
            )
        }
        val pages = root[BODY].asArrayOrNull()
            ?: throw IOException("pixiv 多页答复里的 body 不是数组：${body.take(SAMPLE)}")
        val urls = pages.mapNotNull { element ->
            val page = element as? JsonObject
            val tiers = page?.get("urls") as? JsonObject
            val regular = tiers?.get("regular").textOrNull()
            val original = tiers?.get("original").textOrNull()
            val medium = regular ?: original
            val file = original ?: regular
            // 两档都没有才算丢页（丢一枚由下面的对账抛）；只缺一档 ⇒ 两档同串，屏上仍是真图。
            if (medium == null || file == null) return@mapNotNull null
            PixivPage(
                mediumUrl = medium,
                originalUrl = file,
                width = page?.get("width").numberOrStringOrNull()?.toIntOrNull(),
                height = page?.get("height").numberOrStringOrNull()?.toIntOrNull(),
            )
        }
        if (urls.size != expected) {
            throw IOException("pixiv 多页说一共 $expected 页，实际认出来 ${urls.size} 页")
        }
        return urls
    }

    /** 详情腿那一步：把答复读成 `body` 那一层对象。读不懂就抛（多页那一跳走 [workPages]，形状不同）。 */
    private fun detailOf(body: String): JsonObject {
        val root = parseObject(body, "pixiv 单张")
        if (root[ERROR].isTrue()) {
            throw IOException(
                "pixiv 单张回 error=true（message=${root[MESSAGE]?.jsonPrimitive?.contentOrNull.orEmpty()}）",
            )
        }
        return root[BODY]?.jsonObject
            ?: throw IOException("pixiv 单张答复里没有 body：${body.take(SAMPLE)}")
    }

    private fun postOf(detail: JsonObject): GalleryPost? {
        val sample = detail.toString().take(SAMPLE)
        val id = detail["illustId"].numberOrStringOrNull()?.toLongOrNull()
            ?: throw IOException("pixiv 单张答复里没有可用的 illustId：$sample")
        val urls = detail["urls"]?.jsonObject
            ?: throw IOException("pixiv 单张答复里没有 urls：$sample")
        val original = urls["original"].textOrNull() ?: return null
        return GalleryPost(
            site = GallerySite.PIXIV,
            id = id,
            tags = ((detail["tags"] as? JsonObject)?.get("tags") as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.get("tag").textOrNull() }
                ?.joinToString(" ")
                .orEmpty(),
            rating = RATING_BY_XRESTRICT[detail["xRestrict"].numberOrStringOrNull()] ?: RATING_MASKED,
            // 这一格**有**名字了：`bookmarkCount`（现量两条各 2402 / 3）。榜单那腿的 `rating_count`
            // 仍然不喂任何字段 —— 那颗名字像评分数、取值像收藏数，语义没定性（见 ranking 那一段）。
            favCount = detail["bookmarkCount"].numberOrStringOrNull()?.toIntOrNull(),
            // 单张详情这一腿也硬位齐（现量一条 AI 作品回 `"aiType":2`）⇒ 大图页的角标有真依据。
            siteAiFlag = pixivAiFlagOf(detail["aiType"].numberOrStringOrNull()),
            author = detail["userName"].textOrNull().orEmpty(),
            source = detail["userId"].numberOrStringOrNull()?.let { USERS + it }.orEmpty(),
            width = detail["width"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
            height = detail["height"].numberOrStringOrNull()?.toIntOrNull() ?: 0,
            previewUrl = urls["thumb"].textOrNull().orEmpty(),
            largeUrl = urls["regular"].textOrNull().orEmpty(),
            fileUrl = original,
            fileExt = extensionOf(original),
            // 动图旗标搬进行模型（2026-10-23）：`illustType == 2` 是这条腿的说法。
            // 不搬的话，深链直开（只有这一腿）的动图在屏上就是一张静帧，谁也不会去问 ugoira_meta。
            ugoira = detail["illustType"].numberOrStringOrNull() == "2",
        )
    }

    private fun parseObject(body: String, who: String): JsonObject =
        runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IOException("$who 回来的不是 JSON 对象（可能是维护页或被换盾）：${body.take(SAMPLE)}") }

    /**
     * 「有 N 条却一条都没认出来」必须炸（键名被改的表现就是整墙消失，那不能说成"没有图"）；
     * 数组本身为空是合法答复，交空表由调用方去念"这一页没有"。
     */
    private fun requireRecognized(recognized: Int, total: Int, who: String) {
        if (total > 0 && recognized == 0) {
            throw IOException("$who 给了 $total 条，但没有一条认得出作品号与图址 —— 站方大概改了字段名")
        }
    }

    private fun JsonElement?.asArrayOrNull(): JsonArray? = (this as? JsonArray)

    private fun JsonElement?.asArrayOrNullOf(key: String): JsonArray? =
        ((this as? JsonObject)?.get(key) as? JsonArray)

    /**
     * 匿名腿的**标签译名**（2026-10-20）：`tags.tags[]` 每枚带一个 `translation` 对象。
     *
     * ## 形状与依据
     *
     * 现成的三份外部依据都指向同一件事：这一格**只在请求带了 `Accept-Language` 时才有值** ——
     * pixivpy 的 `set_accept_language` 注释原文就是「用于获取 tags 的对应语言 translated_name」，
     * 它自己那条 issue #76 记着"缺这枚头时字段恒为 None"；pixiv 网站的译名也是按这一枚头切的。
     *
     * 而 `translation` 里的**键就是那枚语言标签**（`{"zh-cn": "..."}` / `{"en": "..."}`），
     * 所以这里**不按固定键捞**：站方回哪一枚我们就读哪一枚，全都不认识就交空表
     * （空表 = 这次没有译文 ⇒ 屏上只摆原名，绝不猜）。
     *
     * 登录腿那一格叫 `translated_name`（平铺字符串），由 `PixivAppParse` 自己读 ——
     * 两条腿的字段名不同是站方的形状，不是我们抄漏了。
     */
    private fun tagTranslationsOf(tags: JsonArray?): Map<String, String> {
        if (tags == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (element in tags) {
            val item = element as? JsonObject ?: continue
            val name = item["tag"].textOrNull() ?: continue
            val translation = item["translation"] as? JsonObject ?: continue
            // 取第一枚**非空且不等于原名**的译文：键是语言码，不该在屏上把它们排个序比漂亮。
            val label = translation.values
                .mapNotNull { it.textOrNull() }
                .firstOrNull { !it.equals(name, ignoreCase = true) }
                ?: continue
            // ⚠️ 键一律**小写**：消费点 `tagDisplayLabel` 是按 `tag.lowercase()` 查的
            // （画廊那边同一套口径）。这里存原名的话，`東方Project` 这种带大写的标签
            // 在屏上就永远取不到译名 —— 而那与"站方没给译文"长得一模一样。
            out[name.lowercase()] = label
        }
        return out
    }

    private fun JsonElement?.textOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** 数字与字符串两种形状都吃：这一族的同一种意思在两腿里类型相反（见类注释那张表）。 */
    private fun JsonElement?.numberOrStringOrNull(): String? = textOrNull()

    /**
     * 站方那两枚布尔旗标的读法：**缺键与 false 不是一回事**，所以交可空。
     *
     * 现量两种字面都出现过（`true` / `false` 与 `1` / `0`），故两种都认；认不出（别的字符串、
     * 嵌套对象）交 null = "这一格没给"，由 [PixivWorkPage.lockReason] 那一侧当作没竖。
     */
    private fun JsonElement?.truthyOrNull(): Boolean? = when (numberOrStringOrNull()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }

    private fun JsonElement?.isTrue(): Boolean = textOrNull()?.lowercase() == "true"

    private fun JsonElement?.joinedText(separator: String): String =
        ((this as? JsonArray)?.mapNotNull { it.textOrNull() } ?: emptyList()).joinToString(separator)

    /** 图址尾巴上的扩展名。两腿现量全是 `.jpg`（`…_p0_master1200.jpg` / `…_p0_square1200.jpg`）。 */
    private fun extensionOf(url: String): String = url.substringAfterLast('.', "").substringBefore('?')

    private val RATING_BY_XRESTRICT = mapOf("0" to RATING_GENERAL, "1" to RATING_R18, "2" to RATING_R15)

    private const val RATING_GENERAL = "general"
    private const val RATING_R18 = "r18"
    private const val RATING_R15 = "r15"

    /** 站方自己已经遮过的（`is_masked=true` / 认不出的 `xRestrict` 取值）走这一档。 */
    private const val RATING_MASKED = "masked"

    private const val USERS = "https://www.pixiv.net/users/"

    /** 报错消息里带多少原始答复：够认出是哪一种坏，又不至于把整份答复抄进日志。 */
    private const val SAMPLE = 120
}

/**
 * pixiv 那两枚站方 AI 位的**同一条**翻译：`2` = AI 生成、`1` = 非 AI、其余（含缺键）= 不知道。
 *
 * 读数（2026-10-18 现量，匿名 `ajax/search/artworks/{词}`，都要带 `--compressed` 否则答复被截断）：
 * - 词=`AI生成画像` → 60 条里 **59 枚 =2**；词=`NovelAI` → **58 枚 =2**；
 * - 对照词=`オリジナル` → `=1` 有 37、`=2` 有 22，而那一页里**带 AI 味标签的 5 条全部 =2**。
 * 登录腿那一格叫 `illust_ai_type`，pixez 明写「本地过滤 AI 生成作品（`illust_ai_type == 2`）」
 * （`data/settings/SettingsRepository.kt:239`）⇒ 两条腿同一个数字含义，共用这一颗。
 *
 * 为什么只留一颗：两条腿各写一遍的话，其中一条改了档位而另一条忘了，
 * 同一张图就会在两个屏上"一枚角标有一枚没有" —— 那比缺一枚角标更难解释。
 */
internal fun pixivAiFlagOf(raw: String?): Boolean? = when (raw) {
    "2" -> true
    "1" -> false
    else -> null
}
