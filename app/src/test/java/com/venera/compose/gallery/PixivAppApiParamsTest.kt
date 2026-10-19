package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv **app-api 参数名**的判据（2026-10-19）。
 *
 * ## 这一颗是为一次真机事故立的
 *
 * 用户截图：已登录 → 作品页**每一个作品**都报
 * `pixiv 返回 400: {"error":{"user_message":"","message":"","reason":"",…}}`。
 *
 * 根因：`/v1/illust/detail` 那一条把作品号发成了 **`id`**，而站方要的是 **`illust_id`**。
 * 表现最阴的一点是：`user_message` 返回**空串**，屏上那句"400"不带任何能指向参数名的线索，
 * 而且**匿名腿是好的**（那条走 `www.pixiv.net/ajax/illust/{id}`，路径里带号、没有参数名），
 * 所以只有登录着的人才复现 —— 一个人一旦登录，整个作品页就全废。
 *
 * 因此这条尺子钉两件事：
 * 1. 这一族端点**必须**用 `illust_id`；
 * 2. `addQueryParameter("id", …)` 这个写法**一处都不许再出现** —— 它静默、且只在登录态炸。
 *
 * 与 pixivpy `aapi.py` 对过：`illust_detail` / `illust_comments` / `illust_related`
 * 全部是 `illust_id`。
 */
class PixivAppApiParamsTest {

    private val appMain = RepoSources.appMain

    private fun src(rel: String): String {
        val f = File(appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    private fun code(rel: String): List<String> = src(rel).lines().map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private val appClient = "com/venera/compose/gallery/data/PixivAppClient.kt"

    /** 作品详情那一腿：必须发 `illust_id`。 */
    @Test
    fun `作品详情发的是 illust_id 不是 id`() {
        val body = code(appClient)
        // 两条腿都发 `illust_id`（详情 + 评论，2026-10-19 批 231-F 加了第二条）。
        // 判据是">= 1"而不是"== 1"：将来再加第三条腿（`/v2/illust/related` 之类）
        // 时不必回来改测试 —— 那条腿同样用 `illust_id`。
        val hits = body.count { it.contains("""addQueryParameter("illust_id", illustId.toString())""") }
        assertTrue(
            "作品详情必须发 illust_id（写成 id 会让登录态下每个作品都 400），实测 $hits 处",
            hits >= 1,
        )
    }

    /**
     * `addQueryParameter("id", …)` **一处都不许有**。
     *
     * 这是这条事故的"形状级"判据：不管将来谁在哪条腿上再写一次 `id`，
     * 都会在这一行红掉 —— 而不是等到某个登录用户把整个作品页点成 400。
     */
    @Test
    fun `app-api 任何一处都不许再用 id 作作品号参数名`() {
        val offenders = code(appClient).filter { it.contains("""addQueryParameter("id"""") }
        assertTrue(
            "app-api 不许用 \"id\" 作作品号参数名（应为 illust_id），实测：$offenders",
            offenders.isEmpty(),
        )
    }

    /**
     * 画师页新接的那几条腿（2026-10-19 晚）：端点与参数名逐枚钉。
     *
     * 现在就钉的理由与这颗件头注那次事故**同一个**：这几条腿还没有真机读数（要装包在用户那台
     * 登录着的机上复量），而参数名写错的表现正是那次事故那一族 —— 站方回 400 且
     * `user_message` 是空串，屏上只剩一句"没取到"，线索为零。
     *
     * 参数面来源两份互证：pixivpy `aapi.py`（Unlicense 档）与 pixez `UserRepository` /
     * `BookmarkRepository`（GPL 档，**只出表不搬码**）。
     */
    @Test
    fun `画师页那几条腿的端点与参数名`() {
        val body = code(appClient)
        assertEquals(
            "画师作品腿的端点", 1,
            body.count { it.contains("""USER_ILLUSTS = "https://app-api.pixiv.net/v1/user/illusts"""") },
        )
        assertEquals(
            "关注写腿的端点", 1,
            body.count { it.contains("""FOLLOW_ADD = "https://app-api.pixiv.net/v1/user/follow/add"""") },
        )
        assertEquals(
            "取关写腿的端点", 1,
            body.count { it.contains("""FOLLOW_DELETE = "https://app-api.pixiv.net/v1/user/follow/delete"""") },
        )
        // 画师作品腿的两枚参数：`type` 是站方切桶那一枚（传错不报错，只会回另一类作品）。
        assertEquals(
            "type 只发给画师作品腿", 1,
            body.count { it.contains("""addQueryParameter("type", type)""") },
        )
        // 两条写腿发的是 **form 的** `user_id`（不是 query）；写腿一共两枚，add 另带 restrict。
        assertEquals(
            "两条写腿各发一枚 form user_id", 2,
            body.count { it.contains(""""user_id" to userId""") },
        )
        assertEquals(
            "restrict 只由 private 那一枚参数决定（收藏写腿与关注写腿各一枚，两枚必须同形）", 2,
            body.count { it.contains(""""restrict" to if (private) "private" else "public"""") },
        )
        // `/v1/user/detail` 的 URL 构造**只许有一处**：会员旗标与画师详情打的是同一个端点，
        // 两遍拼串迟早有一处漏掉 `filter=for_android`（那一枚缺了站方直接 400/191）。
        assertEquals(
            "user/detail 的地址只拼一处", 1,
            body.count { it.contains("private fun userDetailUrl(userId: String): String =") },
        )
    }

    /**
     * 评论腿（本轮新接）也必须用 `illust_id` —— 与上一条同一个坑，提前钉住。
     *
     * 这一腿的**参数名**与 pixivpy `aapi.py:illust_comments` 一致（`illust_id`），
     * 但**端点版本号以现量为准**：我们打的是 `/v3/illust/comments`（`/v1` 现在回 404，
     * 实测记在 `PixivAppClient.illustComments` 头注）；除 `illust_id` 外一枚参数都不发。
     */
    @Test
    fun `评论腿用的是 illust_id`() {
        val body = code(appClient)
        assertTrue(
            "评论腿必须发 illust_id",
            body.any { it.contains("""addQueryParameter("illust_id", illustId.toString())""") },
        )
    }

    /**
     * **人气预览腿**的参数面：只发 donor 那一串五枚，一枚不多（2026-10-19 · 批 233-4）。
     *
     * 立案：`/v1/search/popular-preview/illust` 这一条是**非会员**唯一走得通的人气
     * （donor 的调用面 `api_client.dart:649-652` 就五个参数，且 `search_target` 是**写死**的）。
     * 所以这里多写一枚参数，就是往屏上"亮着的那枚条件"里塞一枚站方不认的 ——
     * 而少说一句（被丢掉的几枚）就是假开关。两半各钉一把：这一条钉**发出去的**，
     * 被丢掉的那几枚由 `GalleryPixivAccountWiringTest` 钉屏上那句话。
     */
    @Test
    fun `人气预览腿只发 donor 那一串五枚`() {
        val lines = code(appClient)
        val start = lines.indexOfFirst { it.startsWith("suspend fun popularPreview(") }
        assertTrue("量具失效：PixivAppClient 里找不到 popularPreview 那颗", start >= 0)
        // 函数窗口：从签名到**下一颗 suspend fun**（同层成员的天然边界，不靠数括号）
        val stop = ((start + 1) until lines.size).firstOrNull { lines[it].startsWith("suspend fun ") } ?: lines.size
        val body = lines.subList(start, stop)
        assertEquals("五枚，一枚不多一枚不少", 5, body.count { it.contains("addQueryParameter(") })
        for (needle in listOf(
            """addQueryParameter("filter", "for_android")""",
            """addQueryParameter("include_translated_tag_results", "true")""",
            """addQueryParameter("merge_plain_keyword_results", "true")""",
            """addQueryParameter("word", word)""",
            """addQueryParameter("search_target", "partial_match_for_tags")""",
        )) {
            assertEquals("$needle 必须恰好一枚", 1, body.count { it.contains(needle) })
        }
        // 这一条不认的那些：一枚都不许混进来（发了 = 站方不理而屏上亮着）。
        // `offset` 也在这一列：这一条只给一页，带游标就是"第二页按一套没量过的口径"。
        for (forbidden in listOf(
            "\"sort\"", "\"start_date\"", "\"end_date\"", "\"bookmark_num_min\"",
            "\"search_ai_type\"", "\"ratio\"", "\"offset\"",
        )) {
            assertTrue("预览腿不许发 $forbidden", body.none { it.contains("addQueryParameter($forbidden") })
        }
        // 签名**不吃筛选面板**：接一枚不读的参数就是第二处真相（判据在 `PixivSearchFilter.needsAccount` 的注释里）。
        val signature = body.takeWhile { !it.startsWith("): Result") }
        assertTrue("签名不许接 PixivSearchFilter", signature.none { it.contains("PixivSearchFilter") })
        assertEquals("端点常量只有一处声明", 1, lines.count { it.contains(POPULAR_ENDPOINT) })
    }

    /** 端点那一句原样钉在这里，不进被测源码。 */
    private val POPULAR_ENDPOINT =
        """POPULAR_PREVIEW = "https://app-api.pixiv.net/v1/search/popular-preview/illust""""

    /**
     * 预览腿的**键表与搜索腿共用同一颗** `page(...)`（2026-10-19）。
     *
     * 两条腿回的是同一个 illust 对象。抄第二遍键表就是两把尺 —— 站方哪天换键名，
     * 只有一处读空，而那一处恰好是"非会员才走的那一条"，真机上最难复现。
     * 单独一颗 `popularPreview(...)` 只为了让失败读数说的是**这一条腿**（"pixiv 人气预览"而不是"pixiv 搜索"）。
     */
    @Test
    fun `人气预览的解析与搜索共用同一张键表`() {
        val lines = code("com/venera/compose/gallery/data/PixivAppParse.kt")
        assertEquals("预览那一颗只有一处声明", 1, lines.count { it.contains("fun popularPreview(body: String): PixivAppPage =") })
        assertEquals(
            "它走的是同一颗 page(...)，而读数说的是这一条腿",
            1,
            lines.count { it.contains("""page(body, "pixiv 人气预览", withRank = false)""") },
        )
    }
}
