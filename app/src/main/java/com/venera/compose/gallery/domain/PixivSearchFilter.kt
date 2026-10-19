package com.venera.compose.gallery.domain

import java.time.LocalDate

/**
 * pixiv 搜索的**排序**。
 *
 * ## 两套腿、两套名字（2026-10-17 深夜按 pixez 的口径补齐）
 *
 * 同一种"怎么排"在两条腿上写法完全不同：
 *
 * | 档 | www 匿名（`ajax/search/artworks`） | app-api（`/v1/search/illust`） |
 * |---|---|---|
 * | 最新 | 不发参数（默认就是 `createDate` 降序） | `sort=date_desc` |
 * | 旧序 | `order=date` | `sort=date_asc` |
 * | 人气 | **没有**（`order=popular`/`popular_d` 现量被静默忽略） | `sort=popular_desc` |
 *
 * 人气这一档**只在登录腿上有**，而且站方把它划在 pixiv 高级会员里。
 * **2026-10-18 改判**：上一版写的是"非会员点它 app-api 会回 error，我们原样上屏"，
 * 而 pixez 那侧的实际做法是 —— 点 `popular_desc` 时 `isPremium == 0` 就 **toast 一句并且不发请求、
 * 也不改已选排序**（`result_illust_list.dart:689-693`），而 `popular_male_desc` / `popular_female_desc`
 * 两档**只在会员面前才摆出来**（`:556`）。照它改：非会员点了不发，屏上直接说清楚要会员。
 * 会员身份从 `/v1/user/detail` 的 `is_premium` 取（**取不到就当非会员** —— 宁可少给一档，
 * 不许假装筛过）。male / female 两档不做：用户稿上只有三档。
 *
 * 依据（表与结论，B 档不搬码）：`sort` 取值域就是 `date_desc / date_asc / popular_desc`（我们只用这三串），
 * 收藏门槛会员走 `bookmark_num_min`、非会员走词尾 `users入り`，动图那三档是**客户端筛** `illust.type == 'ugoira'`。
 */
enum class PixivSearchOrder(val label: String, val webParam: String?, val appSort: String) {

    /** 站方默认序就是 `createDate` 降序，所以 www 那一腿**不发任何参数**。 */
    NEWEST("最新", null, "date_desc"),

    /** www：`order=date`（现量首条落在 2007 年）；app-api：`sort=date_asc`。 */
    OLDEST("旧序", "date", "date_asc"),

    /** **只有 app-api 有**（会员档）。www 那一腿对这一档恒回"最新"，所以没登录时不摆。 */
    POPULAR("人气", null, "popular_desc"),
    ;

    /**
     * 这一档站方划在付费墙里 —— **非会员不发**（发出去只会被静默忽略或回 error，两种都读成假开关）。
     * 判据由调用点喂会员位（`/v1/user/detail` 的 `is_premium`）。
     */
    val needsPremium: Boolean get() = this == POPULAR
}

/**
 * 收藏门槛。**按会员身份二选一发**（2026-10-18 照 pixez 的口径改判）。
 *
 * - **非会员 / 匿名腿** → 词尾标签 `{词} {N}users入り`（pixiv 的收藏数标签是真标签）。
 *   现量（2026-10-18，词=`猫`，`s_mode=tag`）：不带那一枚时 60 条里**只有 2 条**带 `*users入り`，
 *   带上之后 58 条里 **47 条带 `100users入り`** ⇒ 这条路**匿名就真生效**，连登录都不要求。
 * - **会员** → 发 app-api 的真参数 `bookmark_num_min`（pixez 那一排 `bookmark_num` 只在 `isPremium == 1` 才摆）。
 *
 * ⚠️ 从前这一排**只发 `bookmark_num_min`**，而那一枚在站方是会员档：非会员点它 = 被静默忽略 =
 * 用户读到的"选了没变化"（2026-10-18 用户「收藏 xxx+ 的筛选没有生效」，且他明确说了自己是**登录态**
 * ⇒ 上一轮那句"成因是票据没落地、退回匿名腿"只解释了另一条路，**不是本案主因**，就地改判）。
 * www 那一跳的 `blt` / `bgt` 仍是无效的（现量逐条一字不差），所以匿名腿走词尾而不是发参数。
 *
 * 只做**单边门槛**：pixez 给会员还多一套 `[min,max]` 区间（`bookmark_num_max` 也传），
 * 而稿上那七枚全是 `100+` 这种形状，区间要两枚数字，屏上摆不下也不常用。
 */
enum class PixivBookmark(val label: String, val min: Int, val starTag: String) {

    ALL("全部", 0, ""),
    P100("100+", 100, "100users入り"),
    P250("250+", 250, "250users入り"),
    P500("500+", 500, "500users入り"),
    P1000("1000+", 1000, "1000users入り"),
    P5000("5000+", 5000, "5000users入り"),
    P10000("10000+", 10000, "10000users入り"),
}

/**
 * 作品类型：全部 / 仅动图 / 排除动图。
 *
 * **两条腿都在本地筛**（`illustType == 2` / app-api 的 `type == "ugoira"`）。
 * 站方搜索接口没有这一枚参数：`type=ugoira`、`mode=all`、`csw=1` 现量对 www 全部被忽略
 * （`total` 一动不动）。pixez 也是这么做的（`UgoiraFilter` 挂在列表的 `filter` 上），
 * 所以这里不是"抄近路"，是**唯一能真生效的落点**。
 *
 * 代价照实说：它只筛**已取到的这一页**（60 条里通常只有一两条动图），
 * 「仅动图」那一档一页可能只剩一张。这是站方不给参数的直接后果，不是我们省事。
 */
enum class PixivWorkType(val label: String) {

    ALL("全部"),
    UGOIRA("仅动图"),
    NO_UGOIRA("排除动图"),
}

/**
 * 标签怎么匹配：**部分**（默认）/ **完全**。
 *
 * 两条腿各有一枚自己的参数，语义对齐：
 *
 * | 档 | www（`s_mode`） | app-api（`search_target`） |
 * |---|---|---|
 * | 部分 | 不发（默认） | `partial_match_for_tags` |
 * | 完全 | `s_tag_full` | `exact_match_for_tags` |
 *
 * www 那一侧现量：不带参数 `total=620880`，带 `s_mode=s_tag_full` `total=614352` ——
 * 两个数不同，说明这一枚是真的在筛，不是被忽略的装饰。
 *
 * 这一枚住在**输入框里**（那一枚「部分/完全」小令牌），不住在筛选面板里 ——
 * 它是"这一句怎么解释"，与"这一批要哪一些"是两件事（方案 C · D 那一刀）。
 */
enum class PixivTagMode(val label: String, val webParam: String?, val appTarget: String) {

    PARTIAL("部分", null, "partial_match_for_tags"),

    FULL("完全", "s_tag_full", "exact_match_for_tags"),
}

/**
 * 画面比例。现量：`ratio=0.5` 首条 `3479×2268`（横）、`ratio=-0.5` 首条 `1204×1820`（竖），
 * 而且 `total` 从 620880 掉到 64924 / 68280 —— 站方真的按这一枚筛。
 *
 * ⚠️ 稿上还有第四档「方图」，这里**没有**：站方只有横/竖两枚（`0.5` / `-0.5`），
 * 方图要么再传一个不存在的值（被忽略 ⇒ 假开关），要么拿比例在本地估（只估得了已取到那一页）。
 */
enum class PixivRatio(val label: String, val param: String?) {

    ALL("全部", null),

    LANDSCAPE("横图", "0.5"),

    PORTRAIT("竖图", "-0.5"),
}

/**
 * pixiv 搜索的那一排条件（2026-10-17 方案 C；深夜按 pixez 的口径补齐五段）。
 *
 * ## 五段各自落在哪条腿上
 *
 * | 段 | 发到哪 | 说明 |
 * |---|---|---|
 * | 排序 | www `order` / app `sort` | 人气只有 app 有，而且是**会员档**（非会员不发） |
 * | 标签模式 | www `s_mode` / app `search_target` | 在输入框里那枚令牌 |
 * | 收藏 | 会员 app `bookmark_num_min` / 其余**词尾 `users入り`** | www 的 `blt` 被忽略，而词尾那枚匿名实测生效 |
 * | 排除 AI | www `ai_type=1` / app `search_ai_type=1` | 2026-10-18 现量：同词 `ai_type=0` 有 21 条 AI、`=1` 剩 **0 条** |
 * | 作品 | **本地筛** | 两条腿的站方参数都被忽略，见 [PixivWorkType] |
 * | 比例 | www `ratio` + **本地筛** | app 没有这一枚 ⇒ 登录腿上退化成本地筛 |
 * | 时间 | www `scd/ecd` / app `start_date/end_date` | 单边有效 |
 *
 * 「登录腿」= 带 `Authorization: Bearer` 的 `app-api /v1/search/illust`（pixez 走的就是这条）；
 * 「匿名腿」= `www.pixiv.net/ajax/search/artworks/{词}`，不登录也能用。
 * 两条腿**不混发**：混起来就是"半面墙按一套参数、半面墙按另一套"，屏上读不出对错。
 *
 * ## 默认值纪律
 *
 * 默认态**一个参数都不发**（[webParams] 回空表；[appParams] 只发 app-api 必填的那两枚）——
 * 也就是说"升级到这一版、什么都不点"与"没有这一块"是同一批结果。单测钉在 `PixivSearchFilterTest`。
 */
data class PixivSearchFilter(
    val order: PixivSearchOrder = PixivSearchOrder.NEWEST,
    val tagMode: PixivTagMode = PixivTagMode.PARTIAL,
    val bookmarks: PixivBookmark = PixivBookmark.ALL,
    val excludeAi: Boolean = false,
    val workType: PixivWorkType = PixivWorkType.ALL,
    val ratio: PixivRatio = PixivRatio.ALL,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {

    /** 全默认 = 与"没有这块面板"逐字节一致。 */
    val isDefault: Boolean get() = this == DEFAULT

    /**
     * 这一排条件里有没有**只有登录腿才发得出去**的那几枚。
     *
     * 2026-10-18 只剩「人气」这一枚：收藏那排从前算在这里，是因为它只发 `bookmark_num_min`
     * （会员档）；现在非会员改走词尾 `users入り`，**匿名腿实测就生效**，所以不再要求登录。
     *
     * ⚠️ 2026-10-19 这一枚的语义**收窄成"要票据"**：非会员点人气不再被拒，改走
     * `popular-preview` 那条单页腿（见 `PixivAppClient.popularPreview`）。那条腿**不吃这一排里的任何一枚**
     * （donor 的调用面只有一个词），所以签名上压根不接 [PixivSearchFilter] —— 接了却不读，
     * 就是"屏上亮着一排条件、请求里一枚都没发"那一族假开关。
     */
    val needsAccount: Boolean
        get() = order.needsPremium

    /**
     * 发给**两条腿**的搜索词：非会员那一档把收藏门槛做成词尾标签（`{词} {N}users入り`）。
     *
     * 会员原样返回 —— 那一档走 [appParams] 里的真参数，往词里加东西会**双重筛**
     * （站方按收藏数筛过一遍，标签又把结果限死在带 `users入り` 的那批里，冷门门槛直接空墙）。
     *
     * ⚠️ 历史与摘要吃的仍是**用户原词**：把 `100users入り` 写进历史就是往用户的记录里塞我们的实现细节。
     */
    fun searchWord(word: String, premium: Boolean): String =
        if (!premium && bookmarks != PixivBookmark.ALL) "$word ${bookmarks.starTag}" else word

    /**
     * 时间窗的两端（**起 > 止 时对调**，任一端可以空着）。
     *
     * 两端都空 = 不限 ⇒ null。
     *
     * 单边是真的有意义，2026-10-17 现量（词=`初音ミク`）：
     * `scd=2026-10-10` 单独发 → `total` 936（首条 2026-10-18，即"那天之后"）；
     * `ecd=2026-10-12` 单独发 → `total` 46415（首条 2026-10-12，即"截至那天"）。
     * 所以这里**不**把缺的那一端补成另一端：补上之后"从这天起"会静默变成"就这一天"，
     * 而屏上那两格明明一格是空的。
     *
     * 对调而不是拒绝：日期是两枚手填格，用户先填止、再回头填起是最常见的顺序，
     * 那一刻弹一句"起不能晚于止"只会让他再点一次。而对调之后的语义他一看结果就懂。
     */
    fun bounds(): Pair<LocalDate?, LocalDate?>? {
        if (from == null && to == null) return null
        val a = from
        val b = to
        return if (a != null && b != null && a > b) b to a else a to b
    }

    /**
     * **匿名腿**发出去的那几枚查询参数（`ajax/search/artworks/{词}`）。
     *
     * 默认项一律不发 —— 站方的默认就是我们要的默认，多发一枚等于把"我们以为的默认"
     * 押在站方哪天会改的一个值上。
     *
     * ⚠️ 人气**不在这里**：www 那一跳对 `order=popular*` 是静默忽略的，发出去等于假装筛过。
     * 收藏也**不在这里**（`blt` / `bgt` 同样被忽略，现量逐条一字不差）—— 那一档走 [searchWord] 的词尾标签。
     * 排除 AI **在**这里：`ai_type=1` 是匿名腿就生效的硬过滤（2026-10-18 现量，词=`猫`：
     * `ai_type=0` 回 21 条 `aiType=2`，`ai_type=1` 回 **0 条**）。
     */
    fun webParams(): List<Pair<String, String>> = buildList {
        order.webParam?.let { add("order" to it) }
        tagMode.webParam?.let { add("s_mode" to it) }
        if (excludeAi) add("ai_type" to "1")
        ratio.param?.let { add("ratio" to it) }
        bounds()?.let { (start, end) ->
            start?.let { add("scd" to it.toString()) }
            end?.let { add("ecd" to it.toString()) }
        }
    }

    /**
     * **登录腿**发出去的那几枚（`/v1/search/illust`）。
     *
     * 与 [webParams] 不同，这里**恒发** `sort` 与 `search_target`：app-api 这两枚没有
     * "不发就是默认"的约定（不发 `sort` 时站方的默认序没写在任何公开文档里，
     * 而我们屏上那枚「最新」必须真的对应 `date_desc`，否则就是拿站方的默认冒充我们的档）。
     *
     * 收藏门槛**只在会员那一边**发：`bookmark_num_min` 在站方是付费墙里的档位，非会员发过去会被静默忽略
     * （那正是 2026-10-18「收藏 xxx+ 没生效」的主因）；非会员改走 [searchWord] 的词尾标签。
     * 「全部」那一档也不发：`bookmark_num_min=0` 与不传同义，多发一枚只是噪音。
     */
    fun appParams(premium: Boolean): List<Pair<String, String>> = buildList {
        add("sort" to order.appSort)
        add("search_target" to tagMode.appTarget)
        if (excludeAi) add("search_ai_type" to "1")
        if (premium && bookmarks != PixivBookmark.ALL) {
            add("bookmark_num_min" to bookmarks.min.toString())
        }
        bounds()?.let { (start, end) ->
            start?.let { add("start_date" to it.toString()) }
            end?.let { add("end_date" to it.toString()) }
        }
    }

    /**
     * 这一张图在**本地**该不该留下（作品类型 + 画面比例）。
     *
     * 两枚都只筛得到"已取到的这一页"，所以调用点必须在**翻页判据**上留出余量：
     * 一页被筛空不等于到底（见 `GallerySearchViewModel.runPixivSearch` 那条注释）。
     *
     * @param ugoira 站方那条腿给的"这是动图"判据（www `illustType==2` / app `type=="ugoira"`）。
     */
    fun keeps(ugoira: Boolean, width: Int, height: Int): Boolean {
        when (workType) {
            PixivWorkType.ALL -> Unit
            PixivWorkType.UGOIRA -> if (!ugoira) return false
            PixivWorkType.NO_UGOIRA -> if (ugoira) return false
        }
        return when (ratio) {
            PixivRatio.ALL -> true
            // 宽高都没给（两条腿都可能缺这两格）时**留下**：筛掉等于替站方断言"这不是横图"，
            // 而我们手里根本没有那一格数据。宁可多摆一张，也不静默吞掉一张。
            PixivRatio.LANDSCAPE -> width <= 0 || height <= 0 || width > height
            // 横/竖的判据就是宽高比较，与站方 `ratio=0.5/-0.5` 那一刀同义（现量首条 3479×2268 / 1204×1820）。
            PixivRatio.PORTRAIT -> width <= 0 || height <= 0 || height > width
        }
    }

    /**
     * 「筛选」那枚钮上的数字：**面板里真的动过的段数**（排序不算 —— 它常驻在那一行上，
     * 自己就看得见；标签模式也不算 —— 它住在输入框里，同样看得见）。
     * 0 的时候钮上不摆数字（摆一个「0」等于说"我筛了 0 件事"）。
     */
    val activeCount: Int
        get() = (if (bookmarks != PixivBookmark.ALL) 1 else 0) +
            (if (excludeAi) 1 else 0) +
            (if (workType != PixivWorkType.ALL) 1 else 0) +
            (if (ratio != PixivRatio.ALL) 1 else 0) +
            (if (bounds() != null) 1 else 0)

    /**
     * 收成一条时那一行摘要（每枚都是一句人话，不是参数名）。
     *
     * 顺序固定：排序 → 标签 → 收藏 → 排除 AI → 作品 → 比例 → 时间。**排序在最前**，因为它是那一行上
     * 唯一也常驻在别处的东西，读起来才连贯。
     */
    fun summary(): List<String> = buildList {
        add(order.label)
        if (tagMode != PixivTagMode.PARTIAL) add("标签${tagMode.label}")
        if (bookmarks != PixivBookmark.ALL) add("收藏 ${bookmarks.label}")
        if (excludeAi) add("排除 AI")
        if (workType != PixivWorkType.ALL) add(workType.label)
        if (ratio != PixivRatio.ALL) add(ratio.label)
        bounds()?.let { (start, end) ->
            when {
                start != null && end != null ->
                    add(if (start == end) "$start" else "$start 至 $end")
                start != null -> add("$start 起")
                end != null -> add("截至 $end")
            }
        }
    }

    companion object {
        /** 默认态的唯一实例（比较用它，不要另写一份"新的默认对象"）。 */
        val DEFAULT = PixivSearchFilter()

        /**
         * 时间那四个快捷档（天）。`null` = 不限。
         *
         * 取 7 / 30 / 365 三档：这一屏的用途是"最近在画什么"，九成查询落在这三档里；
         * 再细分（14 天、90 天）只会把一行塞满，而精确区间有两枚日期格兜着。
         */
        val DATE_PRESETS: List<Int?> = listOf(7, 30, 365, null)
    }
}
