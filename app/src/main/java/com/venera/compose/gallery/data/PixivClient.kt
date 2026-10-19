package com.venera.compose.gallery.data

import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.NoInteractiveBypassTag
import com.venera.compose.gallery.domain.PixivSearchFilter
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * pixiv 的**匿名头像**取数（批次 L）。
 *
 * 只用一个端点：`/ajax/user/{id}`。2026-09-30 实测（走本机代理出口）：
 *
 * - **匿名可读**，且 `User-Agent`、`Referer` 两档对照下来**都不需要**（三档都 200、同一份 687 字节）；
 * - 答复里 `body.image` / `body.imageBig` 就是头像两档尺寸（同一位实测 `…_50.png` / `…_170.png`），
 *   挑哪一档由 [GalleryArtistEndpointParse] 与 `GalleryArtistUrls.avatarUrl` 判；
 * - 但**头像图片本身**（`i.pximg.net`）不带 `Referer: https://www.pixiv.net/` 是 403（实测 403 / 200 各一次），
 *   那是取流层的事，见 `ImageHeaderPolicy`（批次 L · L4）。
 *
 * 两条红线：
 *
 * 1. **地址会过期，所以一律不落库**。实测拿一个已经改过的旧用户编号（1360347）来要，
 *    站方直接回 `error=true`。所以这里每次面板打开现取，存下来的只有"名字 + 站别"（关注名单）。
 * 2. **只做未登录读取，不带任何凭据**。这个端点在登录后会多回一堆私人字段，
 *    我们只需要一张公开头像，也就不该让任何会话材料进这条请求。
 */
class PixivClient internal constructor(private val engine: HttpEngine) {

    /** 取那位 pixiv 用户的头像地址。null = 站方答上了但没有可用地址（调用方换首字母占位）。 */
    suspend fun avatarUrl(userId: Long): Result<String?> = withContext(Dispatchers.IO) {
        runCatching { GalleryArtistEndpointParse.pixivAvatarUrl(execute("$BASE/ajax/user/$userId")) }
    }

    /**
     * 从**作品号**反查这张图的作者（批次 L · L10）。
     *
     * 存在的理由：出处常写成 `pixiv.net/artworks/N`，那串是作品号 —— 拿它当用户号去要头像必然
     * `error=true`（实测），而这一跳把它换成真正的用户号，入口和头像才有着落。
     * `failure` = 作品被删/被锁（实测 15 条里 4 条 404）或读不懂；调用方什么都不摆，不说"这位没有 pixiv"。
     */
    suspend fun artworkAuthor(illustId: Long): Result<PixivArtworkAuthor> = withContext(Dispatchers.IO) {
        runCatching { GalleryArtistEndpointParse.pixivArtworkAuthor(execute("$BASE/ajax/illust/$illustId")) }
    }

    /**
     * 榜单那一腿（批次 pixiv · T3）。
     *
     * 档位名**只认站方那一套**：`daily/weekly/monthly/rookie/original/male/female` 现量各 200
     * （每页 50 条，`p=2` 首条 `"rank":51` ⇒ 翻页真生效），而 `day/week/month` 回 **404**。
     * R-18 那两档（`daily_r18`/`male_r18`）匿名是 **403 整档墙**，不是"被静默过滤"——
     * 所以这一条腿**摆不出 R-18 的开关**（摆一枚匿名必 403 的开关就是假开关）。
     */
    suspend fun ranking(mode: PixivRankingMode, page: Int = 1): Result<List<PixivWork>> = withContext(Dispatchers.IO) {
        val url = url("ranking.php") {
            setQueryParameter("mode", mode.siteParam)
            setQueryParameter("format", "json")
            setQueryParameter("p", page.toString())
        }
        runCatching { PixivFeedParse.ranking(execute(url)) }
    }

    /**
     * 按标签那一腿（批次 pixiv · T3）：`ajax/search/artworks/{词}`，每页 60 条。
     *
     * ⚠️ 词在**路径里**，不在查询串里（`?word=` 与 `web/v1/search/illust` 那些形状现量 404 ——
     * 本鱼先前把同一个错路径名打了五遍，才在 2026-10-16 深夜被那句"关键词在路径里"推翻）。
     * 走 [HttpUrl.Builder.addPathSegment] 就是为了这件事：词是从站方读回来的标签文本，
     * 里面可能有空格、`%`、甚至 `../` —— 手工拼串等于把路径的控制权交出去。
     *
     * 这一跳**没有"全站最新"**：词一律被站方当标签名解释（`extraData.meta.canonical` = `/tags/{词}`），
     * 所以词的来处由调用方决定；默认序本身就是 `createDate` 降序。
     *
     * ## 哪些参数真的生效（2026-10-17 晚现量复核，词=`初音ミク`、`p=1`）
     *
     * | 参数 | 结果 |
     * |---|---|
     * | `order=date` | 首条 2007-09-13 ⇒ **生效**（旧序） |
     * | `order=popular` / `popular_d` | 与不带参数逐条一致 ⇒ **被忽略**（会员档） |
     * | `s_mode=s_tag_full` | `total` 620880 → 614352 ⇒ **生效** |
     * | `ratio=0.5` / `-0.5` | `total` → 64924 / 68280，首条宽高对得上 ⇒ **生效** |
     * | `scd` / `ecd` | `total` → 610 ⇒ **生效** |
     * | `blt` / `bgt` | 与不带参数逐条一致 ⇒ **被忽略**（收藏门槛） |
     * | `type` / `mode` / `csw` | `total` 一动不动 ⇒ **被忽略**（动图那一档） |
     *
     * 所以 [PixivSearchFilter] 只收前者那几枚；后两段（收藏、作品）在那一颗的注释里写明了
     * 为什么不摆 —— 摆一枚点了没反应的开关，比没有这一枚更糟。
     */
    suspend fun tagWorks(
        word: String,
        page: Int = 1,
        filter: PixivSearchFilter = PixivSearchFilter.DEFAULT,
    ): Result<List<PixivWork>> = withContext(Dispatchers.IO) {
        val url = url("ajax/search/artworks/$word") {
            setQueryParameter("p", page.toString())
            // 默认态一枚都不发（`webParams()` 回空表）：与"没有这块面板"是同一串请求。
            //
            // ⚠️ 人气 / 收藏**不在这一串里**：这条腿对 `order=popular*` 与 `blt/bgt` 是静默忽略的。
            // 那两档要真起来得走 `PixivAppClient.searchIllust`（带票据那条）。
            filter.webParams().forEach { (name, value) -> setQueryParameter(name, value) }
        }
        runCatching { PixivFeedParse.tagWorks(execute(url)) }
    }

    /**
     * 造地址只走 [HttpUrl.Builder]：路径段与查询参数各归各位，词里带空格、`%`、甚至 `../`
     * 也出不了它自己的那一段（这两条腿新收的"词"是从站方读回来的标签文本，不是我们自己写的常量，
     * 手工拼串等于把路径的控制权交出去）。origin 恒取 [BASE]，不收调用方给的片段。
     */
    /**
     * 单张按号（大图页与「历史」那一行按号回取都吃它）。
     *
     * 现量：真号 200 / 26,642 字节；拿一个已经改过的旧编号（1360347）要，站方回 `error=true` ⇒ 抛。
     * 这条腿**不带任何凭据**（口径同文件头：只做未登录读取）。
     */
    suspend fun workById(illustId: Long): Result<GalleryPost?> = withContext(Dispatchers.IO) {
        runCatching { PixivFeedParse.workDetail(execute(url("ajax/illust/$illustId") {})) }
    }

    /**
     * **作品页**那一腿（2026-10-18）：同一份详情多读八格，外加多页对账。
     *
     * 两跳：`ajax/illust/{id}` 拿全貌与页数；`pageCount > 1` 时再打 `ajax/illust/{id}/pages`
     * 把剩下的原图地址逐页取齐。**第二跳失败就整条失败** —— 交回"只有第 1 页"的半份，
     * 屏上就会写着「共 4 页」而只画得出 1 页，那是把缺数说成读完（宁可慢，不可静默交错）。
     *
     * 与上面 [workById] 不是重复：那颗只回三站共用的行模型（列表与「历史」按号回取吃它），
     * 装不下原题、页数、逐枚标签、浏览数、发布时间、评论数这些**只有作品页要**的格子。
     */
    suspend fun workDetail(illustId: Long): Result<PixivWorkDetail?> = withContext(Dispatchers.IO) {
        runCatching {
            val head = PixivFeedParse.workDetailPage(execute(url("ajax/illust/$illustId") {}))
                ?: return@runCatching null
            if (head.pageCount <= 1) return@runCatching head
            val pages = PixivFeedParse.workPages(
                execute(url("ajax/illust/$illustId/pages") {}),
                head.pageCount,
            )
            head.copy(pages = pages)
        }
    }

    /**
     * **画师页**那三条腿（2026-10-19）。全部匿名、全部不带凭据 —— 与这一颗件的头注同一条红线
     * （「只做未登录读取」），所以它们住在这儿而不是 [PixivAppClient]。
     *
     * 三条的关系是"一跳给目录、一跳给内容"：
     * `userHead` 定调（横图/名字/简介），`profileAll` 给全量 id 表，`worksByIds` 按 id 分批取卡。
     *
     * @param userId 画师用户号。**不是作品号** —— 拿作品号来问会得到 `error=true`
     *   （`ajax/user/{id}` 对不存在的号回 error，实测），那是失败不是"这位没作品"。
     */
    suspend fun userHead(userId: Long): Result<PixivUserHead> = withContext(Dispatchers.IO) {
        runCatching {
            PixivUserWorksParse.userHead(
                execute(url("ajax/user/$userId") { setQueryParameter("full", "1") }),
            )
        }
    }

    /** 作品索引：插画/漫画两张全量 id 表 + 公开收藏总数。 */
    suspend fun profileAll(userId: Long): Result<PixivUserWorks> = withContext(Dispatchers.IO) {
        runCatching { PixivUserWorksParse.profileAll(execute(url("ajax/user/$userId/profile/all") {})) }
    }

    /**
     * 按 id 取一批（[PixivUserWorksParse.worksByIds]）。
     *
     * `ids[]` 是**重复键**（`ids[]=1&ids[]=2`），不是逗号串 —— 那是站方自己的形状
     * （实测 24 枚一批通过）。用 `addQueryParameter` 重复追加同一枚键即可。
     *
     * `workCategory` 取 `"illust"` / `"manga"`：站方按它切桶。**传错桶不会报错**，
     * 只会回一批对不上的作品 —— 所以调用方必须把 tab 与 category 绑死（见画师页）。
     *
     * @param firstPage 首屏那一批传 true，其余 false。站方口径如此（实测两档都通），
     *   本屏只用它区分"第一批"这件事，不拿它当分页游标（游标是 id 表本身）。
     */
    suspend fun worksByIds(
        userId: Long,
        ids: List<Long>,
        workCategory: String,
        firstPage: Boolean = false,
    ): Result<List<PixivWork?>> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext Result.success(emptyList())
        runCatching {
            val body = execute(
                url("ajax/user/$userId/profile/illusts") {
                    ids.forEach { addQueryParameter("ids[]", it.toString()) }
                    setQueryParameter("work_category", workCategory)
                    setQueryParameter("is_first_page", if (firstPage) "1" else "0")
                },
            )
            PixivUserWorksParse.worksByIds(body, ids)
        }
    }

    private fun url(path: String, block: HttpUrl.Builder.() -> Unit): String =
        BASE.toHttpUrl().newBuilder()
            .apply { path.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) } }
            .apply(block)
            .build()
            .toString()

    private fun execute(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            // 与日榜同一条口径：画廊这一路**不弹过盾窗口**（挂在那儿等交互会把整屏挂死）。
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        val client = engine.okHttpClient
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("pixiv 返回 ${response.code}")
            return body
        }
    }

    companion object {
        private const val BASE = "https://www.pixiv.net"

        @Volatile
        private var INSTANCE: PixivClient? = null

        /**
         * 只收 [HttpEngine]，不收 `Context` —— 收口前这里是 `getInstance(context)`，
         * 于是整颗客户端被钉在 Android 面。Android 侧的同名工厂搬在
         * `gallery/data/android/GalleryClientsAndroid.kt`（同包，所以调用点一字未改）。
         * 单例照旧：一端只会传进一颗 engine。
         */
        fun getInstance(engine: HttpEngine): PixivClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: PixivClient(engine).also { INSTANCE = it }
        }
    }
}
