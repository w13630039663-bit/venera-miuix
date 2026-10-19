package com.venera.compose.gallery.data

import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryAvatarProbeEndpoint
import com.venera.compose.gallery.domain.GalleryBrowseRow
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.compose.gallery.domain.PixivSearchFilter
import kotlinx.coroutines.flow.StateFlow

/**
 * 画廊侧的**业务 API 契约**（B7' / D1 的「状态类」那一半）。
 *
 * 为什么不并进漫画侧 `data/api/`：隔离口径要求画廊各侧自持，让 `gallery/ui` 去 import 漫画侧的
 * 业务口会把「屏蔽口径、分级模式」这些只读共享重新绑回一个发布者。为什么不写进 `GalleryPorts.kt`：
 * 那颗已经装着容器 + 守卫口 + 偏好口，再涨六颗就没人读得动了；同包内 `internal` 可见，
 * 所以 `AndroidGalleryPorts.createPorts` 照样能直接构造。
 *
 * 宽度一律按**消费面实数**（复算 `_qa/b7-recon.mjs`），没一枚是为「看起来完整」加的。
 * 适配器全部**不在构造里调 `getInstance`**（成员体里现取），于是「第一次真正用到才建那颗单例」的
 * 时机与改造前逐点相同 —— 与 `AndroidBusinessPorts.kt` 从头守到尾的是同一条纪律。
 *
 * ## 这颗文件一句平台类型都没有
 *
 * 原先「契约在上、接线在下」同处一颗，`Context` 把整颗钉死在 Android 面；S1 把下面那十四颗
 * 搬进 `android/GalleryAdaptersAndroid.kt` 后，这颗只剩 `StateFlow` + `Result` + 本仓数据模型，
 * 于是它是桌面编译面的一部分（`:desktop` 排掉 android 子目录就拿到了契约、没拿到接线）。
 */

/** 画廊图片收藏。`clear()`（整表）与 `removeAll()`（按 uid）是两条不同的路。 */
interface GalleryFavorites {

    val favorites: StateFlow<List<GalleryFavorite>>

    /** 给回**切换后**的状态（true = 现在收藏着）。 */
    suspend fun toggle(post: GalleryPost): Boolean

    suspend fun removeAll(uids: Collection<String>)

    suspend fun clear()

    /** 取走并清空那条「刚刚发生了什么」的提示；没有给 null。 */
    fun consumeNotice(): String?
}

/** 关注的画师。 */
interface GalleryArtistFollows {

    val follows: StateFlow<List<GalleryArtistFollow>>

    suspend fun toggle(site: GallerySite, name: String): Boolean

    /**
     * 把某一位**摆成指定状态**（true = 关注，false = 取关），幂等。
     *
     * 为什么需要它而 `toggle` 不够：pixiv 那一侧今天以**站方**为真相（用户 2026-10-19 拍板
     * 「站方为准，本地只当缓存」）。写腿答上"关注成功了"之后，本地这本账要按**目标状态**对齐，
     * 不能按"当前本地是什么"去翻 —— 站方成功、本地翻错方向，屏上就会亮着一个站方没有的关系。
     * 图库两站没有写腿，那边照旧走 [toggle]。
     */
    suspend fun setFollowed(site: GallerySite, name: String, followed: Boolean)

    fun consumeNotice(): String?
}

/**
 * 画廊浏览历史（看过的图）。2026-10-14 新增，入口在画廊首页顶栏（取代原来那枚「换一批」）。
 *
 * 与漫画侧的阅读历史是两件事：那一份记的是**漫画**（跨源、带进度），这一份记的是**图站的图**。
 * 桌面那一侧早有自己的实现（`DesktopGalleryBrowseHistory`，桌面首页「历史」那一行由它点亮），
 * 所以这一颗**只被移动端接** —— 桌面压根不会问这一颗（见 `GalleryPorts.history` 那颗可空槽，
 * 与 `gelbooruWindows` 同一条理由：不是"接了但坏掉"，是"这一侧没有这一档"）。
 */
interface GalleryBrowseHistory {

    /** 已按「最近看过在前」排好的名单（判据在 domain 那颗件里）。 */
    val rows: StateFlow<List<GalleryBrowseRow>>

    /**
     * 记一笔「看过」。同一条重复看只留一行、刷新时刻并置顶 —— 幂等，不涨行数。
     *
     * @param at 看过那一刻的毫秒时间戳，由调用方传入（屏上以编译器为裁判的可注入点）；
     *   不在实现里现取，是为了让判据能被单测钉住。
     */
    suspend fun recordView(post: GalleryPost, at: Long)

    suspend fun clear()

    /**
     * 批量移除（历史墙多选那一格，2026-10-15）。键是 [GalleryBrowseRow.uid]。
     *
     * 与 `GalleryFavorites.removeAll` 同一条口径：`clear()`（整表）与 `removeAll()`（按 uid）
     * 是两条不同的路，别拿前者实现后者 —— 那会把"删这几张"做成"把整份历史清空"。
     */
    suspend fun removeAll(uids: Collection<String>)

    /** 取走并清空那条「刚刚发生了什么」的提示（坏档留档那一句）；没有给 null。 */
    fun consumeNotice(): String?
}

/** 画师头像的本地缓存位（地址落盘是 2026-10-01 的改判）。 */
interface GalleryArtistAvatars {

    /** 有记录给地址（空串也算记录，调用点自己 `ifBlank`）；没记录给 null = 该去取。 */
    fun peek(site: GallerySite, name: String, source: String): String?

    suspend fun remember(site: GallerySite, name: String, source: String, avatar: String?)
}

/** 标签词典 + 分类：三枚都是「给一堆 tag，问它是什么」。 */
interface GalleryTagLexicon {

    suspend fun artistNames(names: List<String>): Set<String>

    suspend fun translations(names: List<String>): Map<String, String>

    /** 那一页的分类计数；拿不到给 null（面板自己决定不显示）。 */
    suspend fun fetchTagCategories(pageUrl: String): Map<String, Int>?

    /**
     * 从 tag 里挑「可当标题」的那几枚。
     *
     * 返回 null 与返回空表是**两件事**（库没打开 vs 这批名字里确实没有），调用点按这条区分，
     * 判式不许在外面合并（`GalleryTagDictionary.kt:135-137` 的原口径）。
     */
    suspend fun titleTags(names: List<String>): Map<String, GalleryTitleTag>?
}

/**
 * 画廊侧问凭据只需要「配没配」，而这两枚今天正是以 `StateFlow` 形态被消费的
 * （`var x by mutableStateOf(...)` + 一路 `collect`）。
 *
 * ⚠️ 这里**原样交回同一个 flow 实例**，不做 `map { it != null }` 那种派生：派生要 `stateIn(scope)`，
 * 就得给契约造一颗 scope —— 那是新增协程容器、会改取消时机。§七.8 说的「两处变一处」是可选收益，
 * 不是本批的目标（本批的硬约束是行为零变更）。
 */
interface GalleryCredentials {

    val gelbooruIdentity: StateFlow<GelbooruIdentity?>

    val sauceNaoHasKey: StateFlow<Boolean>
}

/** 画廊侧的具名 KV（今天只有搜索缓存那一颗）。存储名由调用点原样递进来，收口不许改名 = 换数据文件。 */
interface GalleryStoreFactory {

    fun open(name: String): KeyValueStore
}

// 十四颗 `Android*` 适配器不住在这颗文件里，住在 `android/GalleryAdaptersAndroid.kt`。
// 搬出去的唯一原因是构造参数 `Context`：契约面一句平台类型都没有，可以进桌面编译面，
// 而 `android/` 子目录被 `:desktop` 的全局排除挡在外面。包名不变 ⇒ 同包构造点无需 import。

// ─────────────────────────── D1b：站点侧 ───────────────────────────

/**
 * 一站的墙。三站今天**各自是一颗类**，`when(site)` 表留在 UI 原处（本批只做纯改引，
 * 不把实测那 7 处三站表折成一遍 —— 那是 §十一 里明确拆出去默认不做的行为敏感项）。
 *
 * [pageSize] 是「这一站一次给多少条」，值就是各站 companion 里的那枚常量
 * （`POOL_SIZE` / `SEARCH_PAGE_SIZE`）；把它做成实例属性而不是让 UI 去 import 实现类拿常量，
 * 是因为 UI 本来就在问这个问题（`limitOf(site)` / `pageSizeFor(site)` 那两张表）。
 */
interface BoardSource {

    val pageSize: Int

    /** `limit` 不设默认值：调用点全部显式给数，让契约替它兜默认反而是新增行为。 */
    suspend fun searchPosts(tags: String, page: Int, limit: Int): Result<List<GalleryPost>>

    /** 只给 term：条数默认值留在实现侧，与改造前同一枚常量。 */
    suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>>

    suspend fun fetchById(id: Long): Result<GalleryPost?>
}

/** yande.re 独有的两枚（另两站没有别名解析与画师出处接口）。 */
interface YandeReBoard : BoardSource {

    suspend fun artistLinks(name: String): Result<List<String>>

    suspend fun resolveArtistAlias(name: String): Result<String?>

    /**
     * 昨日日榜（桌面首页「今日推荐」那一格取的正是它）。
     *
     * 只住在 `YandeReClient`（那颗的 `fetchDailyPopular`），Gelbooru/Safebooru 两站**没有**对应接口，
     * 所以它进 yande.re 的专属窄口而不是公共 `BoardSource` —— 进公共口就等于逼那两站的适配器
     * 写一枚"永远失败"的假实现，而假实现是本仓最忌的静默交错。
     */
    suspend fun fetchDailyPopular(): Result<List<GalleryPost>>
}

/**
 * 「这一站有按分数排的墙」—— 只有两站有（`GelbooruClient` / `SafebooruClient` 的 `fetchTopScored`），
 * `YandeReClient` **没有**，所以它不进公共 [BoardSource]（理由同 [YandeReBoard.fetchDailyPopular]，
 * 方向相反：那里是 yande.re 独有，这里是另两站独有）。
 *
 * 收窄 [GalleryBoards] 那两枚的声明类型对既有调用方**源码兼容**：子类型只加成员、不减成员，
 * 原先按 `BoardSource` 用它们的 `when(site)` 表一行都不用改。
 */
interface ScoredBoard : BoardSource {

    /** 一次给多少条由实现侧那枚 `POOL_SIZE` 定，契约不兜默认（口径同 [BoardSource.searchPosts]）。 */
    suspend fun fetchTopScored(): Result<List<GalleryPost>>
}

interface GalleryBoards {

    val yandere: YandeReBoard
    val gelbooru: ScoredBoard
    val safebooru: ScoredBoard
}

/**
 * 定界探针的一笔读数：信封上的**命中总数**，与那一条的 id 和原始上传时刻。
 *
 * `count` 是这一档成败唯一的数值判据 —— 2026-10-13 实测它**随 id 窗口缩**
 * （全站 14,409,081 → `id:>=14994000` 83,868），而"HTTP 200 + 合法 JSON"在
 * `id:A..B` 那种**被静默忽略**的拼法下长得和生效一模一样。
 */
data class GelbooruWindowProbe(
    val count: Long,
    val oldestId: Long,
    val createdAtRaw: String,
)

/**
 * Gelbooru 时间窗的**定界取数**（2026-10-13）：这一站不认 `date:`，"这一档从哪天起"要换算成
 * "从哪个 id 起"，换算要靠两笔问句。状态机在 `gallery/domain/GelbooruWindowBoundary.kt`（纯件），
 * 这里只是它伸向网络的那两手。
 *
 * ## 为什么不并进 [ScoredBoard]
 * 那是 Gelbooru 与 Safebooru **共用**的一颗，而只有前者需要定界 —— 理由与
 * [YandeReBoard.fetchDailyPopular] 写在同一颗文件上的那条一样：进公共口就等于逼另一站的适配器
 * 挂一枚"永远失败"的假实现。这里另开一颗口，[GalleryPorts.gelbooruWindows] 那一位**没接线就是 null**
 * （桌面今天不接：那边的排行判据根本不列这四档，所以不存在"点了没反应"那一格）。
 */
interface GelbooruWindowProbeSource {

    /** 站方最新一条的 id —— 估点往回推的那根锚。取不到就失败，**不许**退成 0（那等于全站）。 */
    suspend fun fetchLatestPostId(): Result<Long>

    /** 问一次「从 [atLeastId] 起，最早的一条是哪天」；那一段没有条目算失败，不算"窗口里没图"。 */
    suspend fun probeWindowStart(atLeastId: Long): Result<GelbooruWindowProbe>
}

/** 画师出处与头像：三颗客户端的取用面合成一颗（成员名带站点前缀，因为两站都叫 `avatarUrl`）。 */
interface GalleryArtistDirectory {

    suspend fun danbooruArtistUrls(name: String): Result<List<String>>

    suspend fun danbooruArtistCredits(name: String): Result<DanbooruArtistCredits>

    suspend fun pixivArtworkAuthor(illustId: Long): Result<PixivArtworkAuthor>

    suspend fun pixivAvatarUrl(userId: Long): Result<String?>

    suspend fun probeAvatarUrl(endpoint: GalleryAvatarProbeEndpoint): Result<String?>
}

/**
 * pixiv 的三条匿名腿（2026-10-17）。
 *
 * 为什么不塞进 [GalleryBoards]：那颗的形状是 booru 那一族（`searchPosts(tags=…)`、`fetchById` 走
 * `tags=id:N`、`searchTags`、`artistLinks`），pixiv 一条都不吃 —— 硬并进去就会出现
 * "pixiv 那一枚的成员抛 notWired 而它的 else 分支替三站说话"那种假抽象（选型稿 §3.2 数的就是这一族）。
 */
interface GalleryPixivWorks {

    suspend fun ranking(mode: PixivRankingMode, page: Int): Result<List<PixivWork>>

    /**
     * 按标签搜（`ajax/search/artworks/{词}`）。
     *
     * [filter] 是 2026-10-17 方案 C 那排条件（排序 / 标签模式 / 比例 / 时间窗）。
     * 默认值刻意给在**契约这一层**：默认态一枚参数都不发，所以"没接这块面板的调用点"
     * 与"接了但没动过"是同一串请求（判据见 `PixivSearchFilter` 的类注释）。
     */
    suspend fun tagWorks(
        word: String,
        page: Int,
        filter: PixivSearchFilter = PixivSearchFilter.DEFAULT,
    ): Result<List<PixivWork>>

    /**
     * 搜索的**登录腿**：`app-api /v1/search/illust`（2026-10-17 深夜，按 pixez 的口径）。
     *
     * 与 [tagWorks] 不是重复：那一条是匿名可用的 `www` 跳，只发得出排序（旧序）、标签模式、
     * 比例、时间窗与**排除 AI**（`ai_type=1`，2026-10-18 现量匿名就真生效）；
     * **人气**（`sort=popular_desc`，会员档）与**会员那一档收藏**（`bookmark_num_min`）只有这条发得出去。
     * 非会员的人气由 [popularPreview] 那一跳代劳（2026-10-19）。
     *
     * @param bearer 当前账号的 access token（调用方从 `PixivCredentials.bearerOrNull()` 现取）。
     *   给 null 的调用方**不许把这一笔当"人气/收藏筛过了"** —— 退回 [tagWorks] 时那两档的发法不同。
     * @param premium 会员身份（[userPremium]）。非会员**不发** `bookmark_num_min`（站方按付费墙拒绝 ⇒
     *   屏上"点了没生效"），那一档改由调用方走 [PixivSearchFilter.searchWord] 的词尾标签。
     * @param nextUrl 上一页的 `next_url`；null = 第一页。翻页只认它。
     */
    suspend fun searchIllust(
        bearer: String,
        word: String,
        filter: PixivSearchFilter,
        premium: Boolean,
        nextUrl: String? = null,
    ): Result<PixivAppPage>

    /**
     * **人气预览**：`app-api /v1/search/popular-preview/illust`（2026-10-19 · 批 233-4）。
     *
     * 它是 [searchIllust] 的**同档替代品**，不是第四条搜索腿：非会员点「人气」时
     * `sort=popular_desc` 会被付费墙挡掉，而这一条只要票据不要会员（依据是 donor 的调用面，
     * 逐枚参数记在 `PixivAppClient.popularPreview` 那颗的注释里）。
     *
     * **签名里只有词**，没有 [PixivSearchFilter]：这一条不吃面板里的任何一枚（`search_target` 是写死的）。
     * 接一枚不读的参数就是"屏上亮着一排条件、请求里一枚都没发"那一族假开关 ——
     * 被丢掉的几枚由调用点**说上屏**，不许静默。
     *
     * 两条口径差别，调用点必须落屏：只有**一页**（取完把"到底"置上）；**要票据**（没有匿名腿，
     * 没登录时说明白"这一档要登录"，不许悄悄退回日期序）。
     */
    suspend fun popularPreview(
        bearer: String,
        word: String,
    ): Result<PixivAppPage>

    /**
     * 会员身份：`/v1/user/detail` 的 `user.is_premium`（2026-10-18，用户「人气 / 收藏照 pixez 的做法」）。
     *
     * 取不到就是 `false`（当非会员）—— 那一档只决定收藏用哪一种发法，两种都真生效，
     * 所以这里**没有"未知"这一态**，也就不会长成"屏上说不清我按哪档筛的"。
     */
    suspend fun userPremium(bearer: String, userId: String): Result<Boolean>

    /**
     * 搜索的**画师分栏**：`app-api /v1/search/user`（2026-10-18）。
     *
     * 与 [searchIllust] 是**同一屏的两个页签**（pixez 的 `result_page.dart` 就是
     * `TabBar(插画 / 画师)`），不是两种"搜索方式"：同一个词，一栏给作品、一栏给画师。
     *
     * 只收 `bearer`（没有匿名腿）：`www` 那一侧早就不给 JSON 了，所以没登录时
     * 调用点要**说明白**这一栏为什么空着，不许静默退回作品那一栏。
     */
    suspend fun searchUser(
        bearer: String,
        word: String,
        nextUrl: String? = null,
    ): Result<PixivUserPage>

    suspend fun fetchById(id: Long): Result<GalleryPost?>

    /**
     * **作品页**那一腿（2026-10-18，pixiv 站源独用）：原题、页数与逐页原图、逐枚标签、
     * 作者号、浏览数、发布时间、评论数与评论开关、动图旗标。
     *
     * 为什么不让 [fetchById] 长成一枚可空参数齐的大对象：`GalleryPost` 是三站共用的行模型，
     * 给它开 pixiv 才有的格子，另外两站就永远带着恒 null 的那几格 —— 屏上"这一站没有"
     * 与"站方没给"就分不开了。
     *
     * `pageCount > 1` 时内部打第二跳取齐；**第二跳失败整条失败**，不交半份（理由见
     * [PixivClient.workDetail]）。
     *
     * @param bearer 当前账号的 access token，**没有就交 null**（口径同 [recommended] 那几条：
     *   契约这一层不碰登录态，票据由调用方现取再传进来，免得"谁在用这个账号"长出第二份判断）。
     *   有票据时实现先走登录腿（`app-api/v1/illust/detail`）、答上了却读不出图才退匿名腿；
     *   登录腿**抛**（票据过期 / 站方 403 / 这一条不存在）不退 —— 那一句就是真成因，
     *   退匿名腿只会把它盖成"这一条没有图"（2026-10-18 第四批第七件）。
     *
     * null = 站方答上了、这一条既没给图也没竖任何旗标（≈已被删 / 号错 / 审核中）。
     * 被挡的四种（私密 / 隐藏 / 成人向 / 仅登录）**带各自的句子抛**，不并进这一档。
     */
    suspend fun workDetail(illustId: Long, bearer: String?): Result<PixivWorkDetail?>

    /**
     * **作品评论**（2026-10-19 · 批 231-F）：`app-api /v3/illust/comments`。
     *
     * ⚠️ 这条腿**必须带票据**：匿名那侧四种形状全是 404（见
     * [PixivAppClient.illustComments] 头注的实测记录）。没有票据时调用方要照实说
     * "登录才看得到"，不许摆空排 —— 那是本仓反复修掉的那种假件。
     *
     * ⚠️ 版本号只认**量过那一条**：`/v1/illust/comments` 现在是 404（路由不存在），
     * 判别法是"无票据时 400 = 路由在、404 = 路由不存在"，同一颗 client 现量对照过。
     *
     * @param nextUrl 上一页答复里的 `next_url`（自带站方的游标）。传 null 取第一页。
     */
    suspend fun illustComments(
        bearer: String,
        illustId: Long,
        nextUrl: String? = null,
    ): Result<PixivCommentPage>

    /**
     * 站方推荐（`/v1/illust/recommended`）—— 2026-10-17 用户点名
     * 「下面推荐应该自动换成 pixiv 站方的推荐」。
     *
     * ⚠️ 与上面那三条**不同类**：那三条匿名也通，这一条**必须带票据**。票据由调用方从
     * `PixivCredentials.bearerOrNull()` 现取再传进来 —— 契约这一层不碰登录态，
     * 否则"谁在用这个账号"会长出第二份判断（本仓为这类分叉付过账）。
     */
    suspend fun recommended(bearer: String, nextUrl: String? = null): Result<PixivAppPage>

    /**
     * 榜单的**登录态**腿：`app-api/v1/illust/ranking`。
     *
     * 与上面 [ranking] 那条不是重复：那条打 `www.pixiv.net/ranking.php`，**匿名**、按 `p` 翻页；
     * 这条要票据、按站方给的 `next_url` 翻页，答复里还带标题与 `rank`。
     * 2026-10-17 用户那张截图上排行榜整档是空的 —— 匿名的 www 那条对这台机器不给内容，
     * 而屏上只说"这一档没有答上"，读不出是限流还是真空。登录着就该走这条。
     */
    suspend fun rankingInApp(
        mode: PixivRankingMode,
        bearer: String,
        nextUrl: String? = null,
    ): Result<PixivAppPage>

    /**
     * 自己关注的画师：`/v1/user/following`（2026-10-17 第八轮）。
     *
     * 与 [recommendedUsers] 是**两条腿、两栏**：前者是"你已经在关注的人"，
     * 后者是"站方觉得你可能想看的人"。界面必须分开摆 —— 混成一排的话，
     * 用户会以为自己关注了根本没关注的人（那是最容易产生误解的一种假读数）。
     *
     * @param userId 自己的 pixiv 用户号（`PixivAuthState.SignedIn.userId`）。
     */
    suspend fun following(
        bearer: String,
        userId: String,
        restrict: PixivRestrict,
        nextUrl: String? = null,
    ): Result<PixivUserPage>

    /**
     * **关注动态**：`/v2/illust/follow`（2026-10-19，账号页「动态 › 关注动态」那一档）。
     *
     * 与 [following] 的分别是"人 ⇄ 作品"：那一腿回关注名单，这一腿回这批人最近画的东西。
     * 它**不带 `user_id`** —— 是谁的动态由票据决定，所以这一腿没有"看别人的动态"这种用法。
     *
     * @param restrict 「全部」那一档由调用方发两趟再合（见 `PixivAccountFeed`），这里一次只答一档。
     */
    suspend fun followIllustFeed(
        bearer: String,
        restrict: PixivRestrict,
        nextUrl: String? = null,
    ): Result<PixivAppPage>

    /** 站方推荐用户：`/v1/user/recommended?filter=for_android`（同上一族形状）。 */
    suspend fun recommendedUsers(
        bearer: String,
        nextUrl: String? = null,
    ): Result<PixivUserPage>

    /**
     * 云端书签（收藏）：`/v1/user/bookmarks/illust`（2026-10-17 第三十一轮）。
     *
     * 与 [following] 同族：都要票据 + 自己的用户号，答复是与推荐同一个 `illusts[]` 形状。
     *
     * ⚠️ 它与本地收藏（[GalleryFavorites]）是**两套账**，见 [PixivAppClient.bookmarkIllusts] 那条注：
     * 屏上两个入口分开摆，这一条读的始终是 pixiv 账号里的那一份，不并、不去重、不互相删。
     *
     * @param userId 自己的 pixiv 用户号（`PixivAuthState.SignedIn.userId`）。
     * @param restrict 读公开还是非公开那一份（2026-10-19 拍板：两档都读，但非公开只出现在账号页里）。
     * @param nextUrl 上一页答复的 `next_url`（自带 `max_bookmark_id` 游标）；null = 第一页。
     */
    suspend fun bookmarkedIllusts(
        bearer: String,
        userId: String,
        restrict: PixivRestrict,
        nextUrl: String? = null,
    ): Result<PixivAppPage>

    /**
     * **画师页**那三条匿名腿（2026-10-19）。
     *
     * ## 为什么必须住在这条契约上
     *
     * 本仓有一条架构守卫（`BusinessApiBoundaryTest`）钉着「**UI 不许直连实现类，白名单只许缩短**」。
     * 让画师屏自己 `PixivClient.getInstance(context)` 在这条守卫下就是**新增违规** ——
     * 而开口子的正确做法是**在这里加契约**，不是往白名单里塞一行：
     * 白名单一旦能变长，"不许直连"这条就只剩自我约束了。
     *
     * 这三条是**匿名**的（不带任何凭据，与 [PixivClient] 头注那条红线一致），
     * 而它们**不再是这一屏唯一的一条路**：下面那组登录腿才是主路，这三条退成"没有票据"
     * 那一档的兜底（2026-10-19 用户读数：匿名 `profile/all` 给 3 张，站方网页给 26 张）。
     * 桌面那一侧 `GalleryPorts.pixiv` 是可空槽（`= null`），所以加在这里不会逼桌面表态 ——
     * 它照旧走进"这一侧没有 pixiv"那一档。
     */
    suspend fun userHead(userId: Long): Result<PixivUserHead>

    /** 作品索引：插画 / 漫画两张全量 id 表 + 公开收藏总数（`profile/all`）。 */
    suspend fun profileAll(userId: Long): Result<PixivUserWorks>

    /**
     * 按 id 取一批作品卡（`profile/illusts`）。
     *
     * @param workCategory `"illust"` / `"manga"` —— 站方按它切桶。
     *   ⚠️ **传错桶不会报错**，只会回一批对不上的作品，所以调用方必须把段与它绑死。
     * @param firstPage 第一批传 true（站方口径；**不是**分页游标 —— 游标是 id 表本身）。
     */
    suspend fun worksByIds(
        userId: Long,
        ids: List<Long>,
        workCategory: String,
        firstPage: Boolean = false,
    ): Result<List<PixivWork?>>

    // ── 画师页的**登录版**那几腿（2026-10-19，用户「我这些操作全部都是有登录的」）──
    //
    // 上面三条匿名腿**不删**：它们是"没有票据"那一档的退路，屏上要明说那一档少给什么。
    // 这一组全部要 Bearer，所以签名上就把 bearer 摆成第一枚参数 —— 少一枚就是假接线
    // （编译期看不出"忘了带票据"，运行期只表现为 401 后一整屏空）。

    /**
     * 画师的作品列表（含成人档）：`/v1/user/illusts`。
     *
     * 与 [profileAll] + [worksByIds] 那两跳**是同一件事的两条路**：这一条一次给一页带全字段的
     * 作品并直接交出 `next_url`，不再需要"先要 id 表、再按 id 取卡"。
     *
     * @param type `"illust"` / `"manga"` —— 站方自己的桶，与屏上那三段的前两段一一对应。
     */
    suspend fun userIllusts(
        bearer: String,
        userId: String,
        type: String,
        nextUrl: String? = null,
    ): Result<PixivAppPage>

    /** 画师详情：投稿总数 / 粉丝数 / 公开收藏数 + **我是否关注了这位**（`is_followed`）。 */
    suspend fun userDetail(bearer: String, userId: String): Result<PixivUserDetail>

    /**
     * **关注**一位画师（写站方）。
     *
     * ⚠️ 这一跳会改变用户在 pixiv 账号上的真实状态 ⇒ 只在用户点了那一下才发，
     * 装载时绝不自动补写（与 `bookmarkAdd` 同一条纪律）。
     */
    suspend fun followAdd(bearer: String, userId: String, private: Boolean): Result<Unit>

    /** **取关**一位画师（写站方）。与 [followAdd] 成对：只删本地会把站方那一份留成暗账。 */
    suspend fun followRemove(bearer: String, userId: String): Result<Unit>
}

/** 以图搜图。返回类型 `SauceNaoPage` 与本文件同包（`gallery/data/SauceNao.kt:56`）。 */
interface GalleryReverseSearch {

    /**
     * 站方一次只回前 N 条 —— 这枚**是要给用户看的**（`GalleryReverseResults` 的「到底了」那行文案），
     * 所以它进契约，判据与 `BoardSource.pageSize` 同一条：UI 在问业务问题，不是在调参。
     */
    val maxResults: Int

    suspend fun searchByFile(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        allowNsfw: Boolean,
    ): SauceNaoPage

    suspend fun searchByUrl(imageUrl: String, allowNsfw: Boolean): SauceNaoPage
}

/**
 * 画廊侧的熔断卫生口。
 *
 * 与漫画侧 `data/api/NetworkApi.kt` 的 `NetworkHygiene.resetAllBreakers()` 是**同一条动作的两份**，
 * 按各侧自持不合并（`gallery/ui/GalleryFeedReadout.kt:32` 那条纪律：跨侧相似不许并成一颗）。
 * 循环体逐字照抄改造前 UI 那两行，一条 host 都不许多都不许少。
 */
interface GalleryNetworkHygiene {

    fun resetAllSiteBreakers()
}
