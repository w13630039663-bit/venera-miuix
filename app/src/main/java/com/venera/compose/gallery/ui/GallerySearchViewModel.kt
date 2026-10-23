package com.venera.compose.gallery.ui

import com.venera.compose.gallery.domain.GalleryArtistLinks

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.gallery.data.PixivPremium
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.gallery.data.PixivWork
import com.venera.compose.gallery.data.refineGalleryTagSuggestions
import com.venera.compose.gallery.domain.GalleryArtistAlias
import com.venera.compose.gallery.domain.GalleryContextPlan
import com.venera.compose.gallery.domain.GalleryDailyFeed
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GalleryLegOutcome
import com.venera.compose.gallery.domain.GalleryMerge
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GalleryRankings
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GallerySearchContext
import com.venera.compose.gallery.domain.GallerySearchContextStack
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchMerge
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.GalleryTagFilter
import com.venera.compose.gallery.domain.GelbooruWindowBounds
import com.venera.compose.gallery.domain.GelbooruWindowBoundary
import com.venera.compose.gallery.domain.GelbooruWindowBreak
import com.venera.compose.gallery.domain.GelbooruWindowEnd
import com.venera.compose.gallery.domain.GelbooruWindowFinder
import com.venera.compose.gallery.domain.PixivSearchFilter
import com.venera.compose.gallery.domain.PixivSearchKind
import com.venera.compose.gallery.domain.allLegsExhausted
import com.venera.compose.gallery.domain.decodeGallerySearchHistory
import com.venera.compose.gallery.domain.encodeGallerySearchHistory
import com.venera.compose.gallery.domain.isStaleLeg
import com.venera.compose.gallery.domain.nextPagesForAppend
import com.venera.compose.gallery.domain.pushGallerySearchHistory
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 搜索区当前那一面：**展开**改条件（输入 + 补全 / 历史），还是**收成一条**看结果。 */
enum class GallerySearchMode { INPUT, RESULTS }

/**
 * 补全一次取几行。
 *
 * 这**不是**列表在屏上的行数 —— 屏上由搜索区那份 UI 的 `LIST_MAX_ROWS`（4 行）封顶，
 * 多出来的在列表内滚动。两者分开是有意的：取得多一点，"往下滚还有"才成立；
 * 而取多少不改变卡片高度，所以不给布局添负担。
 */
internal const val SUGGEST_ROWS = 8

/**
 * 画廊搜索层的状态持有者。
 *
 * 单独一个 ViewModel（而不是就地 `remember`）的理由与日榜那份一模一样：
 * 本项目的导航条目被下一页覆盖时**组合会销毁**，返回时从零重建 ——
 * 裸 remember 会让「点进大图再返回」把搜索结果整片丢掉，用户得重新搜一遍
 * （同一根因见记忆「导航条目会重建组合」）。
 *
 * 取数逻辑也放进来（与日榜那份"取数留在页面里"的分工**不同**）只有一个理由：
 * **三个调用方**都要用它 —— 搜索区的条件变更（加/删/改排除）、滚到底的自动续页、
 * 以及那枚"重试续页"；而**搜索的触发点也只能有一个**，否则同一轮会发两笔请求
 * （曾经页面那侧还有一个 `LaunchedEffect(filters)`，见 [setFilters] 上的说明）。
 * 复制两份必然漂，所以走 `AndroidViewModel` 拿 Context。
 */
class GallerySearchViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Context = application

    /** 搜索模式开没开（顶栏那枚图标切换它；退出即回到日榜那一屏）。 */
    var active by mutableStateOf(false)

    /**
     * 这一轮打哪些站 —— 「全部 / Yande.re / Gelbooru」里的那一枚。
     *
     * 默认「全部」是用户 2026-09-30 改判，推翻 09-25 那条「一次只搜一个站」。改判的依据是实测：
     * 两站头部画师标签 **22/25 同名**（`gallery-artist-alias-2026-09.md` §一.2），
     * 所以同一串词并打两站直接就能出结果，**不需要**先建一层跨站身份映射。
     *
     * ⚠️ 未配 Gelbooru 账号时**默认档不许漂**：那一腿根本不发请求（见
     * [GallerySearchSource.availableLegs]），但档级仍写着「全部」，并在页尾明说这一腿为什么空着。
     * 悄悄退回单站就是假开关的另一种长相 —— 用户会看到选中态自己变了一次。
     */
    var source by mutableStateOf(GallerySearchSource.ALL)
        internal set

    /**
     * 单站读法（站表序第一个）。
     *
     * 留着只为了给"这一串条件是从哪个站续上的"这类派生读用；**别拿它当身份** ——
     * 条目 uid、路由键、收藏档用的都是每条结果自带的 `GalleryPost.site`。
     */
    val site: GallerySite get() = source.sites.first()

    /**
     * 当前**排行档**（默认 / 天 / 周 / 月 / 年 / 全部）。
     *
     * 它是"怎么看"而不是"搜什么"，所以**不进胶囊、不进历史**（那两处仍只放用户自己选的条件）。
     * 换档 = 原地重搜，不压上下文栈：栈那一格记的是"换了另一轮搜索"，改排序不算。
     */
    var ranking by mutableStateOf(GalleryRanking.NEWEST)
        internal set

    /**
     * 月/年档看的是**哪一期**（null = 本期）。
     *
     * 只有 [GalleryRankings.supportsHistory] 为真的档会被写进来（日期弹层那一步在
     * [pickPeriod] 里把关）；换档一律清回本期 —— 从"2024 年 3 月"点「按年排行」，
     * 用户要的是今年，不是接着看 2024。
     */
    var periodAnchor by mutableStateOf<LocalDate?>(null)
        internal set

    var mode by mutableStateOf(GallerySearchMode.INPUT)
        internal set

    var term by mutableStateOf("")
        internal set

    var suggestions by mutableStateOf<List<GalleryTagSuggestion>>(emptyList())
        internal set

    var isSuggesting by mutableStateOf(false)
        internal set

    /** 补全取不到单独一个字段：它不该把已经选好的 chips 或结果一起弄没。 */
    var suggestError by mutableStateOf<String?>(null)
        internal set

    var filters by mutableStateOf<List<GalleryTagFilter>>(emptyList())
        internal set

    /**
     * 当前**内容域**（图库站 / Pixiv）。它决定这张搜索卡吃哪一套语法，也决定这一墙是谁的图。
     *
     * ⚠️ 与 [source] 不是一件事：`source` 是**来源轴**（booru 那三站里这一句打哪几站），
     * 域是"这一屏整个换了一套站"。两套并存会有两个来源切换器，所以 pixiv 不进 `source`。
     *
     * 换域必须**把手上这一轮清掉**（见 [setDomain]）：pixiv 卡上写着「筛选 · 横图」，
     * 身下的墙却是 booru 的搜索结果 —— 那就是本仓最忌的假读数。
     */
    var domain by mutableStateOf(GalleryHomeDomain.BOORU)
        internal set

    /**
     * pixiv 那一框里敲的词（**不拆成胶囊**）。
     *
     * 为什么不复用 [term]：那个字段是 booru 的"待加胶囊的半截词"，加一枚就清空；
     * pixiv 这一跳词就是查询本身（站方把它当标签名解释），它得留在框里，
     * 用户才能看见"我在搜什么"并接着改。
     */
    var pixivTerm by mutableStateOf("")
        internal set

    /**
     * pixiv 那一排条件（排序 / 标签模式 / 比例 / 时间窗）。
     *
     * 与 booru 的 [filters] **各存各的**：换域来回切不该把对方挑好的条件抹掉
     * （用户已经为此改判过一次 —— 换站不抹条件）。但结果**不各存各的**：
     * 那是上一套语法取回来的图，留着就会出现"卡是这一域、图是那一域"。
     */
    var pixivFilter by mutableStateOf(PixivSearchFilter.DEFAULT)
        internal set

    /** 「筛选」那块面板开没开。只影响画法，不影响查询 —— 收起面板**不重搜**。 */
    var pixivPanelOpen by mutableStateOf(false)
        internal set

    /**
     * pixiv 有没有登录态 —— **它决定这一屏摆哪几段筛选**。
     *
     * 人气（`sort=popular_desc`）与收藏（`bookmark_num_min`）只有带票据的 `app-api` 发得出去；
     * 匿名那条 www 跳对这两枚是静默忽略的（读数见 `PixivSearchFilter`）。
     * 所以没登录时面板**不摆**这两段，而不是摆一枚点了没反应的开关。
     */
    var pixivLoggedIn by mutableStateOf(false)
        internal set

    /**
     * 票据**这一会儿**拿不拿得到 —— 与 [pixivLoggedIn] 分开的那一格（2026-10-18，
     * 用户「人气和收藏 xxx+ 的筛选没有生效」）。
     *
     * 为什么必须分两格：`authState` 只说"有这个账号"，`bearerOrNull()` 才说"这一跳发得出去"。
     * 两者会不一致 —— token 过期而刷新失败、或者刚登录票据还没落地时，
     * 屏上是登录态、请求却退回匿名腿，于是**人气 / 收藏被站方静默忽略**，
     * 而旧的 notice 判据吃的是 [pixivLoggedIn]，屏上什么都不说 —— 用户读到的就是"点了没生效"。
     *
     * 三态：`null` = 还没探到（首帧不拦人，等探）；`true` = 发得出去；`false` = 发不出去。
     */
    var pixivBearerReady by mutableStateOf<Boolean?>(null)
        internal set

    /**
     * pixiv **会员**身份（2026-10-18，用户「人气 / 收藏 xx+ 照 pixez 的做法」）。
     *
     * 与登录态是第三格：登录着 ≠ 会员。`sort=popular_desc` 在站方是付费档（pixez 非会员直接 toast
     * 并且不发），而 `bookmark_num_min` 那一枚**也是**付费档 —— 从前我们把收藏全押在它上面，
     * 于是非会员（含未登录）点「100+」= 站方静默忽略 = 用户读到的"筛选没有生效"。
     *
     * 现在按身份二选一：会员发真参数，其余走词尾 `users入り`（**匿名实测就真生效**，
     * 读数在 `PixivSearchFilter.PixivBookmark` 那一段）。两档都真筛 ⇒ 这一格探不到就留 false，
     * 不会长成"屏上亮着已选中、请求里根本没发"。
     */
    var pixivPremium by mutableStateOf(false)
        internal set

    /**
     * 登录腿的分页游标（站方 `next_url`，自带 `offset`）。null = 第一页 / 已经到底。
     * 匿名腿不用它（那一跳认页码 `p=`）。
     */
    private var pixivNextUrl: String? = null

    /**
     * 上一页走的是哪条腿（true = 带票据的 `app-api`）。
     *
     * 续页要知道这件事：登录腿的页码对站方没意义，它只认 `next_url`；那一格空着还去发，
     * 拿回来的就是第一页 —— 去重会把整页吃光，屏上读起来像"滚到底了但什么都没加载"。
     */
    private var pixivLoginLeg: Boolean = false

    /**
     * pixiv 搜索结果的**站方标题**与**作者头像**（`uid → 值`），与结果同批落地。
     *
     * 为什么不让它们跟着 `GalleryPost` 走：那一颗是三站共用的行模型，加一格 `title` 就会让
     * booru 那三站永远空着（它们的标题是 `GalleryCardTitle` 从标签算的），两种来源混一层。
     * 所以按 uid 单独存一份，由网格按 `stationTitles` / `authorAvatars` 两个槽喂进去 ——
     * 那也是 pixiv 首页那几节在用的同一条通道。
     */
    var pixivTitles by mutableStateOf<Map<String, String>>(emptyMap())
        internal set

    var pixivAvatars by mutableStateOf<Map<String, String>>(emptyMap())
        internal set

    var results by mutableStateOf<List<GalleryPost>>(emptyList())
        internal set

    var isSearching by mutableStateOf(false)
        internal set

    var isLoadingMore by mutableStateOf(false)
        internal set

    var searchError by mutableStateOf<String?>(null)
        internal set

    /** 已经取到第几页（0 = 还没取过）。 */
    var page by mutableIntStateOf(0)
        internal set

    /** 站方还有没有下一批。判据见 [GallerySearch.isExhausted]。 */
    var exhausted by mutableStateOf(false)
        internal set

    /**
     * 各腿飞到的页码。
     *
     * 按腿记而不是记一个全局页码：两腿一次要的张数不同（yande.re 与 Gelbooru 各自的上限），
     * 混成一个数就会出现"到底判据拿 100 去比一个只回 320 的站"。
     * 缺席的腿不在表里 —— 续页时由 [nextPagesForAppend] 从第 1 页补回来，不能跟着别的腿跳页。
     */
    var pageBySite by mutableStateOf<Map<GallerySite, Int>>(emptyMap())
        internal set

    /** 已经到底的那些腿。混成一个全局标志就会出「A 还在出货、页尾写着到底了」那类假读数。 */
    var exhaustedBySite by mutableStateOf<Set<GallerySite>>(emptySet())
        internal set

    /**
     * 这一轮已经上过屏的去重键，续页要带着它。
     *
     * 站方的翻页窗口会滑动，同一批条目会在下一页再发一遍。不攒着就是
     * "翻一页三张里两张是刚才那两张" —— 它不报错，只显得搜索很笨。
     */
    private var seenKeys = HashSet<String>()

    /**
     * 这一轮**没跑起来的腿**，连着能直接上屏的原因。
     *
     * 两种来源：未配账号（发车前就拦住）、超时或失败（每腿一笔时间预算）。
     * 一律不许静默 —— 屏上少了一个站的货而页尾什么都不说，用户就会以为这一站今天没图。
     */
    var legFailures by mutableStateOf<Map<GallerySite, String>>(emptyMap())
        internal set

    /**
     * 这一轮**换过画师名**的那句话（批次 J，判据见
     * [com.venera.compose.gallery.domain.GalleryArtistAlias]）。
     *
     * 为什么必须上屏：屏上那排胶囊与历史存的都是**用户打的原词**（换词不该篡改他输入的东西），
     * 于是"搜 setmen 出来一批 tokenbox 的图"这件事在界面上没有任何地方写着 ——
     * 用户只会看到"我搜的名字出来的画不是我搜的那个"。悄悄换词是本仓最忌的那一类。
     *
     * 没换、换了还是 0 张都各有说法：只有真解析到了站方给的关联才有值，
     * 拿不到关联时这里保持 null，屏上与没做这个功能时**完全一致**。
     */
    var artistAliasNotice by mutableStateOf<String?>(null)
        internal set

    /**
     * **续页**那一笔失败了。与 [searchError] 分开是刻意的：
     *
     * 第 1 页失败时屏上什么都没有，那句话就该占整屏；续页失败时屏上已经有几十张图，
     * 把整屏换成错误页是把用户已经看到的东西弄没。曾经两者共用一个字段，
     * 而续页的观察点是 `results.size` —— 失败时 size 不变，观察点就不会再跳，
     * 于是"卡死在再也不会重试"（页尾却还写着"上滑继续取"，一句假读数）。
     */
    var loadMoreError by mutableStateOf<String?>(null)
        internal set

    /**
     * 第 1 页成功落地的次数。页面拿它当"结果已经换过一轮"的信号去把网格滚回顶部。
     *
     * 不用 `results.size` 当信号：两轮结果条数一样（很常见）就什么都没变，观察不到；
     * 也不代表"换了一轮"（续页也在改 size）。
     */
    var searchGeneration by mutableIntStateOf(0)
        internal set

    /**
     * 第几轮搜索结果已经落过点（滚过位）—— 续页判据的 `settled` 读它。
     *
     * 与 [GalleryForYouViewModel.appliedGeneration] 同一条理由，必须住在这里而不是页面的
     * `remember` 里：组合销毁重建后 `LaunchedEffect` 会再跑一次首帧，凭据若跟着组合归零，
     * "这一轮已经落过点了"就无从得知，于是每次返回都把墙上的人按回第 1 张。
     */
    var appliedGeneration by mutableIntStateOf(0)

    /**
     * 用户此刻在这面墙上看到第几张 —— 页面在滚动变化时写进来（[recordScroll]）。
     *
     * 只需要"当前这一轮"的那一个数：换轮时 [snapshot] 会把它抄进被压栈的那一份。
     */
    private var currentScrollIndex by mutableIntStateOf(0)

    /**
     * 下一次"结果换了一轮"要把网格放到第几张；null = 回顶部。
     *
     * 只有 [popContext] 会写它（弹回上一轮时抄那一轮存下的位置），[beginSearch] 会清掉它
     * （真发了一笔新查询就该从第 1 张看起）。页面用 [consumeScrollRestore] 取一次即清 ——
     * 不清就会跟着下一次搜索跑，把新那轮的第 1 页停在旧深处。
     */
    private var pendingScrollRestore by mutableStateOf<Int?>(null)

    /** 页面把网格实时位置报回来。 */
    fun recordScroll(index: Int) {
        currentScrollIndex = index
    }

    /** 取走并清空"这一轮该落在第几张"。 */
    fun consumeScrollRestore(): Int? = pendingScrollRestore.also { pendingScrollRestore = null }

    /**
     * 「返回能回去的上一轮」栈，**栈顶在前一格**（[GallerySearchContextStack] 里 append 在尾、
     * 弹出取尾 —— 那个方向由它自己的判据定死，这里只是把它端出来）。
     *
     * 为什么要这一格：从前"换一轮上下文"（大图页点标签、点历史、点推荐标签行）都是**整片覆盖**，
     * 于是搜了 touhou → 点图 → 点画师 wowoguni → 返回，touhou 那 100 张已经不存在了，
     * 用户怎么按返回都回不去。压栈之后返回逐级回退。
     *
     * 顶层页面不摆任何"上一轮"的 affordance —— 这条路径只靠返回键走（见方案 §三·明确不做）。
     */
    var contexts by mutableStateOf<List<GallerySearchContext>>(emptyList())
        internal set

    /**
     * 第几"轮"上下文。开一轮、弹一轮都 +1。
     *
     * 在途响应落地前拿它比对（见 [runSearch] 末尾那道闸）：**只认站点拦不住弹栈之后的串台** ——
     * touhou 正在飞第 2 页 → 交接进 wowoguni → 返回弹回 touhou，站没变，
     * 旧那笔若照常落地就会把 100 张来自错误一轮的图接在正确的墙上。
     */
    private var contextRound by mutableIntStateOf(0)

    /** 一次性提示（标签预算满了、这一站不限预算之类），由页面在动作发生时写。 */
    var notice by mutableStateOf<String?>(null)

    var history by mutableStateOf<List<GallerySearchEntry>>(emptyList())
        internal set

    /** 这一站一次要多少条（也是"到底"判据里的那个 requestedLimit）。 */
    fun pageSizeFor(site: GallerySite): Int = when (site) {
        GallerySite.GELBOORU -> boards.gelbooru.pageSize
        GallerySite.YANDERE -> boards.yandere.pageSize
        GallerySite.SAFEBOORU -> boards.safebooru.pageSize
        // pixiv 这一跳**恒给 60 条**（2026-10-17 现量：`illustManga.data` 长度就是 60，
        // 翻到最后一页才不满）。从前这里是 `notWired`，因为搜索腿没接 ——
        // 现在接了，所以这一格必须答得出来："到底"判据和页尾读数都吃它。
        GallerySite.PIXIV -> PIXIV_PAGE_SIZE
    }

    private val gelbooruIdentity = GalleryPorts.of(app).credentials.gelbooruIdentity
    private val boards = GalleryPorts.of(app).boards
    private val pixivAccount = GalleryPorts.of(app).pixivAccount

    /**
     * Gelbooru **配没配账号**。
     *
     * ⚠️ 这一站与 yande.re 有个根本差别：DAPI **匿名一律 401**，
     * 所以没有账号时它**一张图都取不到**（不是少几档权限）。所以这个读数是
     * **能不能用这一站**的开关，页面据此在搜索前就直说，而不是让它静静长出"没有结果"。
     *
     * 订阅 [GelbooruAccount.identity]（StateFlow）而不是读一次快照：设置页配好
     * 或注销之后，退回画廊这边要**立刻**跟着变，而不是等重启。
     */
    var gelbooruConfigured by mutableStateOf(gelbooruIdentity.value != null)
        internal set

    init {
        viewModelScope.launch {
            gelbooruIdentity.collect { gelbooruConfigured = it != null }
        }
        // pixiv 登录态：面板摆哪几段由它定（人气 / 收藏只有带票据那条腿发得出去）。
        // 订阅 `authState` 而不是在取数时现判：登录 / 退出之后面板要**立刻**跟着变，
        // 而不是等下一次搜索才发现少了一段或多了一段。
        pixivAccount?.let { account ->
            pixivLoggedIn = account.authState.value is PixivAuthState.SignedIn
            // `pixivBearerReady` 的首值留给下面那次 collect：`authState` 是 StateFlow，
            // collect 一启动就把当前值发过来，所以首帧之后它必然有一个真值（而不是 null）。
            viewModelScope.launch {
                account.authState.collect { state ->
                    pixivLoggedIn = state is PixivAuthState.SignedIn
                    // 登录态一变就**现探一次票据**：它是 suspend 的（自带刷新），
                    // 所以只能在协程里问。探不到的那一小段时间 `pixivBearerReady` 还是旧值，
                    // 面板按旧值摆 —— 比"闪一下空"好，且搜索那一跳仍会按真票据判 notice。
                    pixivBearerReady = if (state is PixivAuthState.SignedIn) {
                        runCatching { account.bearerOrNull() != null }.getOrElse { false }
                    } else {
                        null
                    }
                    // 会员身份：**拿到票据就探一次**，不等搜索那一笔。
                    // 等到搜索才探，"登录着但还没搜过"的用户点人气会被当成非会员拒掉 —— 拒错方向
                    // 也是一枚假开关（明明点得动的那一档说成点不动）。
                    // 探失败（没票据 / 站方没答）时**不记下这一号**，下次状态变化再试一次；
                    // 中间按非会员走词尾那条收藏，那一档真生效，所以不会静默不筛。
                    val userId = (state as? PixivAuthState.SignedIn)?.userId
                    val probeBearer = runCatching { account.bearerOrNull() }.getOrNull()
                    pixivPremium = PixivPremium.of(userId) {
                        if (probeBearer == null || userId.isNullOrBlank()) {
                            null
                        } else {
                            GalleryPorts.of(app).pixiv?.userPremium(probeBearer, userId)?.getOrNull()
                        }
                    }
                }
            }
        }
    }

    /**
     * 本次真正要发出去的条件 —— **预算内的前 N 枚**。
     *
     * 与 [filters] 分开是有意的：胶囊是"我要搜什么"（用户自己攒的，一条都不能替他丢），
     * 而这一份是"这一站这次能问什么"。
     * 两站实测**都不限标签数**（Gelbooru 7 枚一次通过、yande.re 更无限制），
     * 所以现在恒等于 [filters] —— 但这一层留着：预算是会变的，
     * 而"发请求之前先按预算截断"这个动作只该有一处落点。
     */
    fun effectiveFilters(): List<GalleryTagFilter> {
        // 「全部」档按**最紧的那条腿**截：发出去的那一串两腿共用，
        // 超出某一条腿预算的话，那一腿会拿一个被站方砍过的条件去搜 —— 结果少了也没人知道。
        val budget = source.sites.mapNotNull { GallerySearch.tagBudget(it) }.minOrNull() ?: return filters
        return filters.take(budget)
    }

    /**
     * 换站。
     *
     * **标签留着**（用户 2026-09-26 改判）：两站词表不通这条仍然成立（Gelbooru 的
     * `1girl` / yande.re 的 `1girl` 之类能共用，而 `rating:general` 这种只在 Gelbooru 有意义），
     * 但实测用户真正在做的动作是"这串标签换个站再看看"，此前一句
     * `filters = emptyList()` 把他刚选好的条件整片抹掉、还得重敲一遍。
     * 换站是换**数据源**，不是换"我要找什么"。
     *
     * 留不下的只有**结果**：那是上一站的图，留着就会出现"胶囊写着这一站、图来自那一站"，
     * 而且续页会拿着旧游标去问新站。所以结果整片清空 + 立刻按同一排胶囊在新站重搜一次
     * （条件为空时退回输入态 —— 空查询在站方语义里是"最新一批"，那不是搜索结果）。
     */
    fun setSite(next: GallerySite) = setSource(GallerySearchSource.single(next))

    /** 分段器上「全部 / Yande.re / Gelbooru」那一档的入口。 */
    fun setSource(next: GallerySearchSource) = switchSource(next, reSearch = true)

    private fun switchSource(next: GallerySearchSource, reSearch: Boolean) {
        if (next == source) return
        // **在途请求要一并掐掉**：不掐的话，旧那一档的响应落地时会把它的结果接成新档的内容
        // （chips 是新的、图是旧档的），而且补全也会被旧词表回填 —— 档名与内容对不上最难被发现。
        source = next
        // 换档后手上这一档排行在新档里**可能压根不存在**（Gelbooru 只有默认与全部两档是真的）。
        // 不落回默认档就会出现：胶囊上写着「周」、发出去的是全站历年高分 —— 假开关最坏的长相。
        // 「全部」档按**每一条腿**过：只要有一条腿不支持这一档，整档落回默认才说得通
        // （只让一条腿按排行搜、另一腿按默认搜，屏上那面墙就成了两种口径的混合，读不出对错）。
        if (!GalleryRankings.supportsForApp(next.sites, ranking)) {
            ranking = GalleryRanking.NEWEST
            periodAnchor = null
        }
        searchJob?.cancel()
        suggestJob?.cancel()
        isSearching = false
        isLoadingMore = false
        suggestions = emptyList()
        suggestError = null
        results = emptyList()
        page = 0
        pageBySite = emptyMap()
        exhausted = false
        exhaustedBySite = emptySet()
        seenKeys = HashSet()
        legFailures = emptyMap()
        artistAliasNotice = null
        searchError = null
        loadMoreError = null
        droppedNoImage = 0
        // reSearch = false 只有 [openContext] 用：它自己会装条件 + 开搜 + 收条，
        // 这里再动一下就是同一轮发两笔请求。
        if (!reSearch) return
        if (filters.isNotEmpty()) runSearch(1) else mode = GallerySearchMode.INPUT
    }

    /**
     * 换条件（页面上的「改成排除」开关、胶囊上的 × 删一枚都走这里）。
     *
     * **搜索的触发点只有这一处** —— 改完条件由这里自己决定要不要开搜。
     *
     * 曾经触发点散在两处（页面的 `LaunchedEffect(svm.filters)` 加 ViewModel 里的命令式调用），
     * 于是"大图页点标签交接"这类动作每笔都发两次请求（当时还有一个空态出口同病，已删）；
     * 更糟的是**用户拍板的「点历史不直接开搜」被那个响应式 effect 直接架空** ——
     * 注释写着不开搜，代码一定开搜。收成单入口之后这两类问题不可能再出现。
     */
    fun setFilters(next: List<GalleryTagFilter>) = applyFilters(next, autoSearch = true)

    /**
     * 条件变更的**唯一落点**。
     *
     * @param autoSearch 条件变了要不要立刻重查。**几乎都是 true** —— 这套 UI 没有"提交"钮，
     *   加一枚胶囊 / 撤一枚 / 改排除 本身就等于一次新查询。唯一传 false 的是 [applyHistory]：
     *   用户拍板「点历史只切源 + 把关键词填回框里」，不替他把搜索也跑了。
     *
     * 清空到零枚时**整段退回输入态**：空查询没有"结果"可言 —— 站方对空 `tags` 回的是
     * "最新一批"，把它当搜索结果摆出来就是假结果。退回后屏上是日榜那片、搜索区仍开着，
     * 用户接着挑下一枚。
     */
    private fun applyFilters(next: List<GalleryTagFilter>, autoSearch: Boolean) {
        if (next == filters) return
        filters = next
        if (next.isEmpty()) {
            // 撤到零枚时要**把在途那一笔也掐掉**：否则它落地后会把结果写进"已经没有条件"的屏上。
            searchJob?.cancel()
            isSearching = false
            isLoadingMore = false
            mode = GallerySearchMode.INPUT
            results = emptyList()
            page = 0
            pageBySite = emptyMap()
            exhausted = false
            exhaustedBySite = emptySet()
            seenKeys = HashSet()
            legFailures = emptyMap()
            artistAliasNotice = null
            searchError = null
            loadMoreError = null
            notice = null
            return
        }
        if (autoSearch) runSearch(1)
    }

    /**
     * 大图页点标签递回来时的入口：切站 + 装条件 + 立刻按新条件搜一遍。
     *
     * 2026-09-28 起它**不再把上一轮覆盖掉**，而是走 [openContext] 把上一轮压栈
     * （真机报的"搜了 touhou → 点图 → 点画师 → 返回掉回每日推荐、touhou 再也回不去"，
     * 病因就是这里原先的整片替换 + 返回那一格到底）。
     *
     * **不拆成两步**（先 [setSite] 再 [setFilters]）：本轮的换站改成"留条件并顺手重搜"之后，
     * 那两步会先拿**上一次那排胶囊**在新站发一笔、紧接着又用新的这排发一笔 ——
     * 同一轮两个请求，正是 [applyFilters] 注释里记的那类返祖现场。
     * 这里一次装到位：条件是新的，只发一笔。
     *
     * ⚠️ 来源档**不跟着那张图的站缩小**：正在用「全部」看的人点了一枚 yande.re 图上的标签，
     * 他要的还是"这串标签的结果"，不是"被悄悄换成只看一个站"。
     * 只有当前那一档根本不含这张图的站时（例如从收藏页那面墙进来），才落到那一个站。
     */
    fun acceptHandoff(site: GallerySite, tags: List<String>) {
        // ── 内容域分流（2026-10-20，用户报「pixiv 作品页点标签没反应，返回后只剩一个空搜索框」）──
        //
        // 病因链在这一句：交接从前**一律**走上面那条 booru 的路 —— 把标签塞进 `filters`（胶囊），
        // 而 pixiv 那一域读的是 `pixivTerm`（一个词）。于是两个域各自都"按设计执行"：
        // 1. `switchSource(single(PIXIV), reSearch = false)` 先把来源改成只有 pixiv 一腿；
        // 2. `filters = [标签]`、`runSearch(1)` → 落到 `runPixivSearch` → 它读 `pixivTerm` = **空串**
        //    ⇒ 走"先打一个词"那条早退，**一笔请求都没发**。
        // 屏上于是只有"一个空搜索框"，而用户点的那枚标签去了一个 pixiv 永远不读的格子里 ——
        // 这就是"点了完全没反应"。
        //
        // 所以这里按**这张图属于哪个站**分流，而不是按"当前这一屏是什么域"：
        // 用户点的是一枚 pixiv 标签，他要的就是"按这个词去 pixiv 搜"，那一刻屏上是什么域不重要。
        if (site == GallerySite.PIXIV) {
            openPixivContext(tags.firstOrNull().orEmpty())
            return
        }
        val next = if (site in source.sites) source else GallerySearchSource.single(site)
        // ── 收条（2026-10-23，用户「点 Tag 直达搜索结果」）──
        //
        // 交接这一枚装完就收成 RESULTS 单行态：点标签的人要的就是"这个词的结果墙"，
        // 不是一张摊开的输入卡 + 键盘（那正是真机上"返回后只剩一个搜索框"的另一种长相）。
        // 两个域现在同一口径 —— pixiv 那一支（[openPixivContext]）本来就收，booru 这支跟上。
        //
        // 键盘由收条顺带收起：RESULTS 态输入行不在组合里，焦点被清掉
        //（见 `GallerySearchArea` 那个 LaunchedEffect(expanded) 的 else 分支），IME 跟着走。
        openContext(next, tags.map { GalleryTagFilter(it) }, thenCollapse = true)
    }

    /**
     * 首页「正在关注的画师」那一栏点进来的入口（批次 M · M5）。
     *
     * 与 [acceptHandoff] 只差一处：**恒落到「全部来源」**。那一栏跨站同名已经并成一条、
     * 卡上并排摆着两枚徽标，此时还按当前那一档来源搜就会出现"卡上摆两枚、结果只有一站的图"——
     * 屏上自相矛盾，比串台更难解释。只在一站被关注的那位仍走 [acceptHandoff]：
     * 另一站的同名那位本来就没被认下，替他扩大范围就是替用户做决定。
     *
     * 收条口径与 [acceptHandoff] 相同（2026-10-23 起）：点画师卡的人要的就是那面墙，
     * 不是一张摊开的输入卡。
     */
    fun acceptHandoffAllSites(tags: List<String>) {
        openContext(GallerySearchSource.ALL, tags.map { GalleryTagFilter(it) }, thenCollapse = true)
    }

    /**
     * **换一轮搜索上下文**的唯一落点（交接、点历史、点推荐标签行三条入口都走这里）。
     *
     * 顺序是刻意的：先按 [GallerySearchContextStack.plan] 处置上一轮，再清场装新的。
     * 反过来写就会出现"屏上已经是新的一轮、上一轮却没存下来"—— 正是本轮要修的那条缺陷。
     *
     * @param thenCollapse 装完之后收成一条（[GallerySearchMode.RESULTS]）还是保持当前那一面。
     *   现在**三个调用方全收条**：点历史（用户 2026-09-26 拍板"点历史直接跑并收成一条"）、
     *   点标签交接与点画师卡（用户 2026-10-23 拍板"点 Tag 直达搜索结果"）—— 两个域同一口径。
     *   参数本身留着：空条件那一档在收条之前就退回输入态了（见下面 `filters.isEmpty()` 分支），
     *   这条分档必须写在调用方能看见的地方。
     *
     * 装条件之后**直接 [runSearch]**、不绕 [applyFilters]：它有一条 `next == filters` 的短路，
     * 撞上"看起来一样"（连点同一条历史、同一枚标签点两次）就会被短路掉 —— 那正是"点了没反应"
     * 的另一种长相。[GalleryContextPlan.RESEARCH_IN_PLACE] 那一档依赖的正是这件事。
     */
    private fun openContext(
        nextSource: GallerySearchSource,
        next: List<GalleryTagFilter>,
        thenCollapse: Boolean,
    ) {
        when (GallerySearchContextStack.plan(source, filters, nextSource, next)) {
            GalleryContextPlan.PUSH -> contexts = GallerySearchContextStack.push(contexts, snapshot())
            // 与当前这一轮逐枚相等：不压栈，只再跑一次。压了就会"白按一次返回"——
            // 那一屏与上一屏一模一样，用户读不出那一下干了什么。
            GalleryContextPlan.RESEARCH_IN_PLACE -> Unit
            // 还没搜过（一枚胶囊都没有）：屏上没有"上一轮"可留，压进去就是一格空壳。
            GalleryContextPlan.SKIP_EMPTY -> Unit
        }
        switchSource(nextSource, reSearch = false)
        // ── 域回位（2026-10-20）──
        // 反向那一条同样要修：在 pixiv 内容域里点一枚 **booru** 标签时，`source` 会被换成
        // 单站 booru，而 `domain` 还停在 PIXIV ⇒ `runSearch` 按域路由到 `runPixivSearch`，
        // 读的是 `pixivTerm`（那是上一轮 pixiv 的词，或者空）⇒ 要么搜错词、要么一笔都不发。
        // booru 的来源轴**永远不含 pixiv**（`GallerySearchSource.ALL` 显式剥了它），
        // 所以走到这里就意味着"这一轮是 booru 的"。
        if (domain != GalleryHomeDomain.BOORU) {
            domain = GalleryHomeDomain.BOORU
            reloadHistory()
        }
        active = true
        // 轮次一 +1：在途那笔（可能是上一轮的续页）落地时会被 [isStaleContext] 作废。
        contextRound++
        term = ""
        suggestions = emptyList()
        suggestError = null
        notice = null
        // 新条件 = 新一轮 = 回到默认档：用户点的是"这串标签的结果"，不是"照刚才那个排法再看一遍"。
        // 上一轮的档级存在快照里，弹栈会带回来。
        ranking = GalleryRanking.NEWEST
        periodAnchor = null
        filters = next
        if (filters.isEmpty()) {
            // 空条件没有"这一轮"可装（站方对空 tags 回的是"最新一批"，那不是搜索结果）。
            mode = GallerySearchMode.INPUT
            return
        }
        if (thenCollapse) mode = GallerySearchMode.RESULTS
        runSearch(1)
    }

    /**
     * **pixiv 那一侧的换一轮上下文**（2026-10-20）—— 与 [openContext] 平行，不是它的一个分支。
     *
     * 为什么不能并进 [openContext]：两个域"搜什么"根本不是同一格（booru 是胶囊串、pixiv 是一个词），
     * 而上游那个入口（[acceptHandoff]）只知道"用户点了一枚哪一站的标签"。并进去就得在每个字段上
     * 加一次 `if (domain == PIXIV)`，而那类分支里任何一处走错都不报错、只出假读数。
     *
     * 三件事按这个次序做，与 [openContext] 逐条对齐（少了任何一条都会长出"返回回不去"那一族）：
     * 1. 先按 [GallerySearchContextStack.plan] 处置上一轮（压栈 / 原地重搜 / 不压空壳）；
     * 2. 再切域 + 装词 + 清场（在途掐掉、轮次 +1 让旧响应作废）；
     * 3. 最后 [runPixivSearch] —— **不绕 [setPixivTerm]**，它会把 `mode` 翻回 INPUT
     *    （那是"用户还在打字"的意思），于是刚装好的这一轮当场退回空搜索框 ——
     *    正是用户报的那个"点了没反应、只剩一个框"。
     *
     * @param word 这一轮要搜的词（pixiv 一次只发一个词；空词 = 没有这一轮）。
     */
    private fun openPixivContext(word: String) {
        val incoming = word.trim()
        when (
            GallerySearchContextStack.plan(
                currentDomain = domain,
                currentKey = if (domain == GalleryHomeDomain.PIXIV) pixivTerm.trim() else filters.joinToString(" "),
                incomingDomain = GalleryHomeDomain.PIXIV,
                incomingKey = incoming,
            )
        ) {
            GalleryContextPlan.PUSH -> contexts = GallerySearchContextStack.push(contexts, snapshot())
            GalleryContextPlan.RESEARCH_IN_PLACE -> Unit
            GalleryContextPlan.SKIP_EMPTY -> Unit
        }
        // 换域那一笔清场**不能走 [setDomain]**：它会把 `mode` 拍回 INPUT 并且不装词，
        // 而这里紧跟着就要装。两件事拆成两句就会先闪一帧空卡再长回结果（同一轮两次重组）。
        if (domain != GalleryHomeDomain.PIXIV) {
            domain = GalleryHomeDomain.PIXIV
            reloadHistory()
        }
        active = true
        // 在途那一笔掐掉：旧域 / 旧词的结果落地会写进这一轮的墙（域已经变了，落地就是脏数据）。
        searchJob?.cancel()
        suggestJob?.cancel()
        artistJob?.cancel()
        isSearching = false
        isLoadingMore = false
        isSearchingArtists = false
        suggestions = emptyList()
        suggestError = null
        results = emptyList()
        page = 0
        pageBySite = emptyMap()
        exhausted = false
        exhaustedBySite = emptySet()
        seenKeys = HashSet()
        legFailures = emptyMap()
        artistAliasNotice = null
        searchError = null
        loadMoreError = null
        droppedNoImage = 0
        notice = null
        pixivPanelOpen = false
        pixivNextUrl = null
        pixivLoginLeg = false
        pixivTitles = emptyMap()
        pixivAvatars = emptyMap()
        pixivArtists = emptyList()
        pixivArtistError = null
        pixivArtistsExhausted = false
        pixivArtistPage = 0
        pixivArtistNextUrl = null
        // 新条件 = 新一轮 = 回到默认档（同 [openContext] 那一条：用户点的是"这个词的结果"）。
        ranking = GalleryRanking.NEWEST
        periodAnchor = null
        // 作品那一栏：用户点的是一枚作品标签，落在"作品"页签上才对得上他看到的那个词。
        pixivKind = PixivSearchKind.ILLUST
        pixivTerm = incoming
        contextRound++
        if (incoming.isEmpty()) {
            // 空词没有"这一轮"可装（站方对空 word 回的是"全站最新"，那不是搜索结果）。
            mode = GallerySearchMode.INPUT
            return
        }
        // ⚠️ **这一档与 booru 不同，有意为之**：`openContext` 把"收不收条"交给调用方
        // （它经手的都是"用户可能还想接着改条件"的情形）。而这里进来的都是**点了一枚标签**：
        // 词已经定了，用户要的就是"这个词的结果"。不收条的话，他从作品页退回来会看见
        // 一张展开的输入卡 + 键盘，而那恰好就是用户报的"只剩一个搜索框"的另一种长相。
        mode = GallerySearchMode.RESULTS
        runPixivSearch(1)
    }

    /** 当前这一屏的可逆快照。只读，不改任何东西。 */
    private fun snapshot() = GallerySearchContext(
        source = source,
        filters = filters,
        results = results,
        page = page,
        exhausted = exhausted,
        droppedNoImage = droppedNoImage,
        pageSize = pageSize,
        ranking = ranking,
        periodAnchor = periodAnchor,
        scrollIndex = currentScrollIndex,
        pageBySite = pageBySite,
        exhaustedBySite = exhaustedBySite,
        aliasNotice = artistAliasNotice,
        // pixiv 那一轮的东西（两个域各存各的，见那颗的注释）。
        domain = domain,
        pixivTerm = pixivTerm,
        pixivNextUrl = pixivNextUrl,
    )

    /** 还有没有"上一轮"可回（页面那侧的返回分支按这一档决定弹栈还是关搜索）。 */
    fun canPopContext(): Boolean = contexts.isNotEmpty()

    /**
     * 当前排行档的读数，如「周（2026-09-21 ~ 2026-09-28）」；默认档返回 null（没什么可念的）。
     *
     * 页尾与空态都念这一句 —— 它同时回答"按什么排的"和"窗口是哪几天"，
     * 而"本周"这种词不带日期就是说不出对错的读数。
     */
    fun rankingReading(): String? {
        if (ranking == GalleryRanking.NEWEST) return null
        val label = GalleryRankings.windowLabel(ranking, periodAnchor, LocalDate.now(ZoneOffset.UTC))
        return if (label == null) ranking.shortLabel else "${ranking.shortLabel}（$label）"
    }

    // ── 页尾 / 空态那几句读数：两个域各按各的语法念（2026-10-17）──
    //
    // 从前这几句直接写在页面里、而且逐字是 booru 的话（`tags=` 那串、排行档、预算）。
    // pixiv 那一侧没有胶囊、没有排行档、也没有预算，照念就是假读数：
    // 屏上会写出"条件：（空）"这种句子，而用户明明搜了一个词。

    /** 页尾那句"搜的是什么"。pixiv 侧念那个词，booru 侧念真正发出去的胶囊串。 */
    val footerQuery: String
        get() = if (domain == GalleryHomeDomain.PIXIV) {
            pixivTerm
        } else {
            GallerySearch.queryOf(effectiveFilters())
        }

    /** 页尾那句"按什么排 / 筛了什么"。pixiv 侧没有排行档，念的是那一排条件。 */
    fun footerRankingReading(): String? = if (domain == GalleryHomeDomain.PIXIV) {
        pixivFilter.summary().drop(1).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    } else {
        rankingReading()
    }

    /** 预算那一句只有 booru 那侧有：pixiv 一次只发一个词，没有"条件被砍"这回事。 */
    fun footerTrimmedNote(): String? =
        if (domain == GalleryHomeDomain.PIXIV) null else budgetTrimNotice()

    /**
     * 空态标题。三档 → 四档：pixiv 那一侧"没有图"说的是"这个词没有"，不是"这串标签没有"。
     */
    fun emptyTitle(): String = when {
        searchError == NEEDS_GELBOORU_ACCOUNT -> "这一站还不能用"
        searchError != null -> "这一轮没搜成"
        domain == GalleryHomeDomain.PIXIV -> "这个词没有可摆的图"
        // 窗口内 0 条 ≠ 这一串标签搜不到东西（实测两站对不认识的伪标签也回空表），
        // 所以设了窗口的档要用另一句读数，免得用户去怀疑自己写的标签。
        rankingWindowActive() ->
            if (periodAnchor == null) "这一档里没有新增命中" else "这一期里没有命中的图"
        else -> "这一串标签没有可摆的图"
    }

    /**
     * 空态那几行说明（不含页面自己那两行：屏蔽规则命中数、以及整轮失败的原文）。
     *
     * 每一行都是"屏上少掉的那部分有个交代"：条件、筛了什么、哪条腿没跑起来、换过名。
     */
    fun emptyDetailLines(): List<String> = if (domain == GalleryHomeDomain.PIXIV) {
        listOfNotNull(
            "词：$pixivTerm",
            pixivFilter.summary().drop(1).takeIf { it.isNotEmpty() }
                ?.joinToString(" · ")?.let { "筛选：$it" },
            noImageNotice(),
        )
    } else {
        listOfNotNull(
            "条件：${GallerySearch.queryOf(effectiveFilters())}",
            // 排行档连日期区间一起念 —— "本周"不带日期就是说不出对错的读数。
            rankingReading()?.let { "排行：$it" },
            budgetTrimNotice(),
            noImageNotice(),
            // 有一条腿没跑起来而另一条回了 0 张：空态必须点名是哪条腿。
            legFailureNotice(),
            // 换过名还是 0 张也要说：否则用户读到的是"这个名字没图"。
            artistAliasNotice,
        )
    }

    /**
     * 这一屏是不是"设了时间窗口"的档。
     *
     * 空态要用它换一句读数：窗口内 0 条与"这一串标签根本搜不到东西"是两件事
     * （实测两站对**不认识的**伪标签也回空数组，所以"空"本身不指向原因）。
     */
    fun rankingWindowActive(): Boolean =
        GalleryRankings.windowBounds(
            ranking,
            periodAnchor,
            LocalDate.now(ZoneOffset.UTC),
        ) != null

    /**
     * 返回弹回**上一轮**：把快照逐字段抄回去，**不发请求**。
     *
     * "不发请求"是这一档的全部意义 —— 用户回到自己刚才那面墙，不该先转两秒圈、
     * 也不该把翻页游标重置掉。轮次仍然要 +1：那是给上一轮的在途响应判死刑用的。
     * [searchGeneration] 一并 +1，让墙按"结果换过一轮"那条既有通路重绘；
     * 但落点不再是顶部 —— 那一轮离开时看到第几张，回来就在第几张（[pendingScrollRestore]，
     * 2026-09-29 真机反馈「返回会自动回到最上面，不保存之前的进度」）。
     */
    fun popContext() {
        val popped = GallerySearchContextStack.pop(contexts) ?: return
        searchJob?.cancel()
        suggestJob?.cancel()
        artistJob?.cancel()
        contexts = popped.second
        val ctx = popped.first
        pendingScrollRestore = ctx.scrollIndex
        currentScrollIndex = ctx.scrollIndex
        // 域与词**先**回位（2026-10-20）：上一轮若是 pixiv 那一轮，`filters` 本来就是空的，
        // 只抄 booru 那一半就会得到"一片结果 + 一个空搜索框"—— 而弹栈**不发请求**，
        // 那个词永远补不回来（用户读到的正是"返回后搜索词没了、只剩一个框"）。
        domain = ctx.domain
        pixivTerm = ctx.pixivTerm
        pixivNextUrl = ctx.pixivNextUrl
        source = ctx.source
        filters = ctx.filters
        results = ctx.results
        page = ctx.page
        pageBySite = ctx.pageBySite
        exhausted = ctx.exhausted
        exhaustedBySite = ctx.exhaustedBySite
        droppedNoImage = ctx.droppedNoImage
        pageSize = ctx.pageSize
        legFailures = emptyMap()
        // 换名那句话跟着那面墙一起回来：抄回来的图是**换过名之后**那一批，胶囊串却是用户的原词，
        // 不一起抄就会出现「弹回上一轮，那句话没了，屏上多出一批对不上名字的画」。
        artistAliasNotice = ctx.aliasNotice
        // 去重键从抄回来的那一片**重建**。不重建就会出现「弹回上一轮 → 往下翻 →
        // 刚才看过的又重复出现」。键的算法与合并那层同一把，不会分叉。
        seenKeys = HashSet(ctx.results.flatMap { with(GalleryMerge) { it.dedupKeys() } })
        // 档级一起回来：不然弹栈后屏上还是那一墙图，排序却变了，而用户看不出哪里变了。
        ranking = ctx.ranking
        periodAnchor = ctx.periodAnchor
        isSearching = false
        isLoadingMore = false
        isSearchingArtists = false
        searchError = null
        loadMoreError = null
        notice = null
        term = ""
        suggestions = emptyList()
        suggestError = null
        // pixiv 那一轮回来时落地的那批是**作品**，页签也跟着回位（快照只记域不记页签，
        // 而画师那一栏没有"上一轮"可回 —— 它压根不吃 `contexts`）。
        if (ctx.domain == GalleryHomeDomain.PIXIV) pixivKind = PixivSearchKind.ILLUST
        // 一律回"看墙"那一态：用户离开那一轮是去别处看图，回来该看到墙，不是键盘。
        mode = GallerySearchMode.RESULTS
        contextRound++
        searchGeneration++
        if (!hasQuery) closeSearch()
    }

    /** 退格删最后一枚（输入框里没字时）。返回是否真删了，页面据此决定要不要吃掉这次按键。 */
    fun removeLastFilter(): Boolean {
        if (filters.isEmpty()) return false
        setFilters(filters.dropLast(1))
        return true
    }

    /**
     * 把输入框里那串文字**当标签提交**（回车在没有补全候选时的兜底）。
     *
     * 为什么必须有这条：补全是站方的接口，慢、会失败、也可能压根没收录用户要找的那个标签
     * （用户敲的正是完整名字时更是如此）。此前回车只取补全首行，于是"补全没回来"= 回车是死键，
     * 用户没有任何办法把手里这串字变成一次搜索。
     *
     * 按空格拆成多枚（`loli -rating:explicit` 这种粘贴串一次全收），逐枚过预算与去重。
     */
    fun submitTerm() {
        val parsed = GallerySearch.parseQuery(term)
        var next = filters
        var rejected = false
        parsed.forEach { filter ->
            if (next.any { it.name == filter.name }) return@forEach
            if (!GallerySearch.fitsTagBudget(site, next.size + 1)) {
                rejected = true
                return@forEach
            }
            next = next + filter
        }
        term = ""
        suggestions = emptyList()
        if (next != filters) {
            applyFilters(next, autoSearch = true)
        } else {
            // 条件没变（点历史装回条件后按回车、或框里那串已经全在框里了）：仍要能开搜。
            // 一枚条件都没有时 [runSearch] 会自己给出"先选至少一个标签"那句提示，不会静默。
            runSearch(1)
        }
        // 预算那句话必须在**开搜之后**写：[beginSearch] 会把 notice 清掉，
        // 先写等于没写（提交多个标签时的预算提示此前就是这么被吃掉的）。
        if (rejected) notice = GallerySearch.budgetNotice(site)
    }

    /**
     * 输入框被聚焦 / 开始打字时调：把区从"收成一条"拉回"展开改条件"，
     * 补全面板与历史行据此重新出现（它们的可见性挂在 [GallerySearchMode.INPUT] 上）。
     *
     * **不动结果**：屏上那批图继续摆着，直到新一轮搜索的结果落地。
     * 否则用户一碰输入框，整面墙就闪回日榜。
     */
    fun ensureInputMode() {
        if (mode != GallerySearchMode.INPUT) mode = GallerySearchMode.INPUT
    }

    /**
     * 顶栏那枚 🔍：开搜索并**直接展开**。
     *
     * 用户点它就是要打字，不是要看上一次的结果 —— 所以不复用上一次的 [mode]。
     * （"回到原上下文接着改"的路径是已经展开着的那张卡，它原样留着 chips 与结果。）
     */
    fun openSearch() {
        active = true
        mode = GallerySearchMode.INPUT
    }

    /**
     * 把展开的区**收成一条**（[GallerySearchMode.RESULTS]）。
     *
     * 内联形态下"收起来"是必须有的一步：展开时它连同键盘吃掉大半个屏，
     * 用户要好好看图就得先收。两个触发点，都由 [GallerySearchArea] / 页面那边给：
     *  - **键盘收起**（`WindowInsets.isImeVisible` 由可见转不可见）—— 手一收就收条；
     *  - **系统返回**（[GalleryScreen] 的 BackHandler，键盘没起时那一枚才落到我们手上）。
     *
     * 一枚条件都没有时**不收**：那时收成一条就只剩一个空搜索框，什么也没表达，
     * 该由顶栏那枚 ✕ 直接关掉整层。所以这里什么都不做，让调用方去看 `filters`。
     */
    fun collapseToResults() {
        if (hasQuery) mode = GallerySearchMode.RESULTS
    }

    /**
     * 屏上此刻有没有"一轮真的搜索"可收条。
     *
     * 两个域各按各的判据：booru 是"胶囊至少一枚"，pixiv 是"框里至少一个词"。
     * 各处（返回键、✕、收条）都读这一格，免得 pixiv 那一侧被判成"没有条件"而直接关掉整层搜索。
     */
    val hasQuery: Boolean
        get() = if (domain == GalleryHomeDomain.PIXIV) pixivTerm.isNotBlank() else filters.isNotEmpty()

    /**
     * 换排行档：**原地重搜**，不压上下文栈。
     *
     * 理由与「同站同条件不压栈」同一条 —— 排序这件事不是"另一轮搜索"，压栈会让返回
     * 先弹回"同样的条件、只是换了排序"的一屏，白按一次。
     * 但**轮次计数器要 +1**：不加的话，上一档正在飞的续页会落进这一档的墙上
     * （站没变、条件没变，[isStaleContext] 那两道都拦不住，只有轮次拦得住）。
     *
     * [GalleryRankings.supportsForApp] 判为"这一档现在摆不出来的"这里再挡一道：菜单那一侧已经不列这一档，
     * 但入口不止菜单一个（弹栈回来的快照、以后可能加的快捷入口），判据得落在状态层。
     * 2026-10-13 起这一把是**移动端那把**（Gelbooru 的四档时间窗是 id 边界合成档，桌面未接）。
     */
    fun setRanking(next: GalleryRanking) {
        if (next == ranking && periodAnchor == null) return
        if (!GalleryRankings.supportsForApp(source.sites, next)) return
        ranking = next
        // 换档 = 回到本期：从"2024 年 3 月"点「按年排行」，用户要的是今年。
        periodAnchor = null
        contextRound++
        searchJob?.cancel()
        if (filters.isNotEmpty()) runSearch(1)
    }

    /**
     * 选**具体哪一期**（日期弹层那一步确认时调）。
     *
     * 一并把档定到 [target]（月或年）：弹层里选了粒度又选了日期，这两件是一件事的两半，
     * 分成两次调用就会出现"档还是周、锚点却是某个月"这种说不清的中间态。
     * 历史期只有月/年开放（用户 2026-09-29 拍板），[GalleryRankings.supportsHistory] 不认的直接不理。
     */
    fun pickPeriod(target: GalleryRanking, anchor: LocalDate?) {
        if (!GalleryRankings.supportsHistory(target)) return
        // 这一路通不通问的是**移动端那把**（[GalleryRankings.supportsForApp]）：Gelbooru 的历史期由
        // id 边界合成（两枚：这一期的头一条、以及出这一期的头一条），站方自己不给 `date:`。
        // 从前这里用的是原生那把，于是菜单那一行摆出来了、点下去却一言不发 —— 假入口。
        // 「全部」档里带着一条给不出窗口的腿，这一档整档不通，与菜单同一条口径。
        if (!GalleryRankings.supportsForApp(source.sites, target)) return
        if (target == ranking && anchor == periodAnchor) return
        ranking = target
        periodAnchor = anchor
        contextRound++
        searchJob?.cancel()
        if (filters.isNotEmpty()) runSearch(1)
    }

    /**
     * 关掉搜索（顶栏那枚 ✕，以及系统返回时已经收成一条的那一态）。
     *
     * chips 与结果**不清**：再点开搜索图标回到原上下文，用户不用重新搜一遍。
     */
    fun closeSearch() {
        active = false
    }

    /**
     * 搜索区被移出组合（关搜索）时调：把在途补全掐掉。
     *
     * 补全那一笔是挂在 ViewModel 上的协程，不会随区的销毁自动取消 —— 不收的话它落地后
     * 会往一个已经关掉的区里写 [suggestions]，下次打开先闪一屏上个词的候选。
     */
    fun clearSuggestions() {
        suggestJob?.cancel()
        isSuggesting = false
        suggestions = emptyList()
        suggestError = null
    }

    fun showSuggestions(list: List<GalleryTagSuggestion>) {
        suggestions = list
    }

    fun acceptHistory(entries: List<GallerySearchEntry>) {
        history = entries
    }

    /** 站方给了行、但**没给可摆的图**而被跳过的张数（见 [noImageNotice]）。 */
    var droppedNoImage by mutableIntStateOf(0)
        internal set

    /** 这一轮用的每页张数（重试/续页要按同一口径判到底）。 */
    var pageSize by mutableIntStateOf(0)
        internal set

    /**
     * 开始一轮新搜索：整片替换结果，翻页游标回到第 1 页。
     *
     * **刻意不碰 [mode]**：开搜不等于"用户改完了"。内联形态下用户加了一枚标签之后
     * 往往还要接着加第二枚，这里一开搜就把区收成一条的话，
     * 那条补全列表会在眼前消失再等他敲下一个字才回来 —— 正是此前"选完第一枚标签后
     * 补全再也出不来"的同一条断流。收起由用户的动作决定（键盘收起 / 系统返回），
     * 不替用户决定。
     */
    fun beginSearch(limit: Int) {
        results = emptyList()
        page = 0
        pageBySite = emptyMap()
        exhausted = false
        exhaustedBySite = emptySet()
        seenKeys = HashSet()
        legFailures = emptyMap()
        artistAliasNotice = null
        searchError = null
        loadMoreError = null
        notice = null
        droppedNoImage = 0
        pageSize = limit
        isSearching = true
        // 真发了一笔新查询 = 从第 1 张看起。留着上一轮的落点会让下一次 generation 递增
        // 把新结果停在旧深处 —— 那正是"搜出来一堆不相干的图"的观感来源。
        pendingScrollRestore = null
    }

    fun appendPage(list: List<GalleryPost>, nextPage: Int, done: Boolean) {
        // 站方给了行不等于给了图：没给可用图片地址的条目原样摆上去，
        // 就是一屏尺寸正确、内容全空的灰卡。跳过并**把张数报出来**，不静默吞。
        val (kept, unusable) = list.partition { GalleryMerge.isDisplayable(it) }
        droppedNoImage += unusable.size
        // 第 1 页**替换**而不是追加：从错误态重试时旧的那一片可能还是半截的。
        results = if (nextPage == 1) kept else results + kept
        page = nextPage
        exhausted = done
        // 只有"整片换过一轮"才推这一代：续页不推（否则每续一页都把用户拽回顶部）。
        if (nextPage == 1) searchGeneration++
    }

    /**
     * 空态上那枚「改条件」：把区展开，让用户接着改。
     *
     * 与 [ensureInputMode] 是同一件事的两种叫法（一个是"用户碰了输入框"，一个是"页面请他改"），
     * 分两个名字只为了让调用点读起来是它自己的意思。
     */
    fun backToInput() {
        mode = GallerySearchMode.INPUT
    }

    /**
     * 加一枚标签。预算满了**当场**说清楚，不等发请求拿 422（站方那句原文仍会在 searchError 里兜底）。
     * 返回是否真的加了，页面据此决定要不要清空输入框。
     */
    fun addTag(suggestion: GalleryTagSuggestion): Boolean {
        if (filters.any { it.name == suggestion.name }) return false
        if (!GallerySearch.fitsTagBudget(site, filters.size + 1)) {
            notice = GallerySearch.budgetNotice(site)
            return false
        }
        notice = null
        suggestions = emptyList()
        // 加胶囊即搜：走条件变更那一个入口，不再由页面另发一笔（否则同一轮搜索发两次）。
        applyFilters(filters + GalleryTagFilter(suggestion.name), autoSearch = true)
        return true
    }

    /**
     * 当前那一笔搜索的句柄。**第 1 页可以打断它**（[runSearch]），续页不能。
     *
     * 收尾那三道加载态靠"这一笔还是不是当前这一笔"来认（`searchJob === coroutineContext[Job]`），
     * 所以这一格必须是**被 launch 赋值的那一份**，别改成局部变量。
     */
    private var searchJob: Job? = null

    /**
     * 取一页结果。第 1 页整片替换、后续页追加（[appendPage]）。
     *
     * 放在 ViewModel 而不是页面里，是因为**两个调用方**都要用它：搜索区的同步重查，
     * 以及画廊页那面墙滚到底时的自动续页（墙在 `GalleryScreen`，搜索区在 `GallerySearchArea`）。
     *
     * 「全部」档在这里**展开成腿**：同一串条件并发打两站，两腿各按各的页宽与游标要页，
     * 落地时一次合并、一次写回（分两次 `results +` 会把整片复制两遍）。
     *
     * 三条不显然的判据：
     * - **未配账号的那条腿不发**，但原因从发车那一刻就挂在 [legFailures] 上 —— 屏上少了一个站
     *   的货而不说原因，用户就会以为这一站今天没图；
     * - **只有全部发出的腿都失败**才算这一轮失败。一腿成、一腿败时屏上是有内容的，
     *   把整屏换成错误页就是拿一次抖动换掉用户已经看到的东西；
     * - **在途作废按腿判**：换档之后旧档那条腿的响应不能落地，但同一笔里另一条腿可能仍是当前档。
     */
    fun runSearch(nextPage: Int) {
        // ── 内容域分流（2026-10-17）──
        // 两个域的语法**不通用**（booru 吃 `tags=a+b order:score`，pixiv 吃"一个词 + s_mode/order"），
        // 所以这一格只做路由，两条腿各在自己的方法里。页面那边一个字都不用改：
        // 墙滚到底那一处调的仍是 `runSearch(page + 1)`。
        when (domain) {
            GalleryHomeDomain.BOORU -> runBooruSearch(nextPage)
            // pixiv 那一域里还有两个页签：画师那一栏只取一页、只认 `next_url`，
            // 它的续页由名单自己的滚动触发（见 `loadMorePixivArtists`）。
            GalleryHomeDomain.PIXIV -> if (pixivKind == PixivSearchKind.ARTIST) {
                if (nextPage == 1) runPixivArtistSearch(first = true)
            } else {
                runPixivSearch(nextPage)
            }
        }
    }

    /**
     * booru 那三站的那一笔（原 `runSearch` 的本体，2026-10-17 分流之后改名）。
     *
     * 改名而不是另起一颗：这一条链上压着两腿并发、别名重搜、Gelbooru 窗口定界、去重合并
     * 这一整套判据，复制一份出来改语法就是让两边慢慢分叉 —— 而分叉出来的差别不报错，只出假读数。
     */
    private fun runBooruSearch(nextPage: Int) {
        if (filters.isEmpty()) {
            notice = "先选至少一个标签（点补全列表里的标签加入）"
            return
        }
        // 第 1 页**可以打断正在跑的那一笔**：同步搜索下用户连选两枚标签，后一次必须盖掉前一次，
        // 否则屏上留下的是上一串标签的结果 —— 看着就像"点了没反应"。
        // 续页仍要挡：同一批结果里不该并着取两页。
        if (nextPage == 1) searchJob?.cancel() else if (isSearching || isLoadingMore) return
        // 档、轮次、排行与"今天"都在发车前**定死**：在途期间用户可能换档、改条件、
        // 跨过 UTC 零点或中途换了期，这一笔仍按发起那一刻查，落地时按这些比对，对不上就作废。
        val sourceAtRequest = source
        val roundAtRequest = contextRound
        val rankingAtRequest = ranking
        val anchorAtRequest = periodAnchor
        // 发出去的是**预算内那几枚**，不是胶囊全部（见 [effectiveFilters]）。
        val sent = effectiveFilters()
        // ⚠️ **两串要分开**：`visibleQuery` 是用户看到的那一排（胶囊、历史都走它），
        // `query` 才是发出去的那一串（多一排排行伪标签）。把它们混成一串，
        // 历史里就会存下 `order:score` 这种东西，用户点历史时它被拆回胶囊 ——
        // 那正是 `GalleryRankingTest` 里"胶囊串永远不含伪标签"那条用例防的事。
        val visibleQuery = GallerySearch.queryOf(sent)
        val todayUtc = LocalDate.now(ZoneOffset.UTC)

        val dispatched = sourceAtRequest.availableLegs(gelbooruConfigured)
        // 发车前就拦住"这一条腿现在根本不能用"的情况（只有 Gelbooru 会这样）：
        // 它的 DAPI 匿名一律 401，发出去必然拿回一句 401。那本来也会被失败路径翻成人话，
        // 但观感完全不同 —— 让用户等一轮网络往返再读一句凭据错误，他会先怀疑标签写错了。
        val blockedLegs = sourceAtRequest.missingLegs(gelbooruConfigured)
        val first = nextPage == 1
        val primarySite = sourceAtRequest.sites.first()
        val limit = pageSizeFor(primarySite)
        if (first) beginSearch(limit) else isLoadingMore = true
        if (first) notice = trimmedNotice(sent) else loadMoreError = null
        legFailures = blockedLegs
        if (dispatched.isEmpty()) {
            // 一条腿都发不出去 = 这一档现在完全不能用。整屏说这一句，不假装搜过了。
            searchError = blockedLegs.values.firstOrNull() ?: "这一档现在没有能用的站"
            isSearching = false
            isLoadingMore = false
            return
        }
        // 各腿要第几页：第 1 页 = 每条腿都从头要；续页 = 到底的腿不再要，
        // 这一轮缺席过的腿从第 1 页补（跟着别的腿跳页就是永久漏掉那一批）。
        val pages = if (first) {
            dispatched.associateWith { 1 }
        } else {
            nextPagesForAppend(pageBySite, exhaustedBySite, dispatched)
        }
        if (pages.isEmpty()) {
            // 能发的腿全都到底了：这一句由 exhausted 去说，不再发一笔拿回空页。
            exhausted = true
            isSearching = false
            isLoadingMore = false
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val attempts: List<GalleryLegAttempt> = coroutineScope {
                    pages.map { (legSite, legPage) ->
                        async {
                            // 每腿一笔时间预算：慢的那一条这一轮缺席（会被 legFailures 说出来），
                            // 换来快的那条立刻出图。预算与包装那份都复用日榜那头（`GalleryLegGuard`），
                            // 三处各写一遍迟早分叉 —— 而分叉出来的差别不报错，只出假读数。
                            // ⚠️ Gelbooru 合成档的**定界探测也算在这笔预算里**：它是几笔 `limit=1` 的轻请求
                            // （2026-10-16 真站台架：本期那一枚 5–8 笔，历史期两枚合计 11 请求 ≈ 10–12 秒，
                            // 上限 18 笔，见 `GelbooruWindowBoundary` 那颗文件头），另起一台计时就会出现
                            // "这一腿既超了预算、又没人说得清它为什么缺席"。定不出来 = 这一腿缺席，
                            // **绝不发一串没有后缀的**。预算因此分两档（[legBudgetMs]）：翻历史期那一腿
                            // 要探两枚边界，12 秒那档会把"再等五六秒就探完"掐成超时。
                            val byOriginal = GalleryLegGuard.guard(
                                legSite,
                                timeoutMs = legBudgetMs(legSite, rankingAtRequest, anchorAtRequest, todayUtc),
                            ) {
                                when (val leg = legQuery(legSite, sent, rankingAtRequest, anchorAtRequest, todayUtc)) {
                                    is LegQuery.Ready -> searchLeg(legSite, leg.query, legPage)
                                    is LegQuery.Skip -> Result.failure(
                                        IOException(legSkipped(rankingAtRequest, leg))
                                    )
                                }
                            }
                            // 批次 J：这一腿**答上了却一行都没给**、而且那一排条件只有一枚包含型标签时，
                            // 用户打的很可能是画师的别名而不是正名（站方两个名字都收，只有正名有货）。
                            // 该不该走这一步全由 [GalleryArtistAlias.resolveToken] 说，这里只按它说的做；
                            // 它说"不换"的时候，屏上与没做这个功能时**逐字节一致**：那 0 张照原样交回去。
                            val token = GalleryArtistAlias.resolveToken(legSite, legPage, sent, byOriginal)
                                ?: return@async GalleryLegAttempt(byOriginal, null)
                            val canonical = canonicalArtist(legSite, token)
                                ?: return@async GalleryLegAttempt(byOriginal, null)
                            val substituted = GalleryArtistAlias.substitute(sent, token, canonical)
                                ?: return@async GalleryLegAttempt(byOriginal, null)
                            // 重搜这一腿**另起一笔预算**：前一笔的 12s 已经花在"确认原词没有货"上了，
                            // 再拿同一个预算去框两笔就是让第二笔必然超时。
                            // 合成档的边界在第一次就已经定出来并缓存过（同日同档同键），所以这里不再探测；
                            // 万一还是定不出来（例如跨了 UTC 零点），就**不重搜** —— 那一腿退回
                            // "没做别名这一步"的样子，比拿一串没有窗口后缀的去搜全站旧账诚实。
                            val retryQuery = (
                                legQuery(legSite, substituted, rankingAtRequest, anchorAtRequest, todayUtc)
                                    as? LegQuery.Ready
                                )?.query ?: return@async GalleryLegAttempt(byOriginal, null)
                            val retried = GalleryLegGuard.guard(legSite) {
                                searchLeg(legSite, retryQuery, legPage)
                            }
                            // 换过名这句话只在重搜那一笔**答上了**时才说：那一笔超时或被打回时，
                            // 「按 tokenbox 也没有图」就是把"我们没问到"念成"站里没有"——
                            // 那种原因不合并的口径与 [legFailures] 那条完全一致，缺席由缺席去说。
                            GalleryLegAttempt(
                                retried,
                                if (retried.answered) {
                                    GalleryArtistAlias.notice(token, canonical, retried.posts.isNotEmpty())
                                } else {
                                    null
                                },
                            )
                        }
                    }.awaitAll()
                }
                // 档已换 **或** 已经不是发车那一轮：这一腿属于别处，落地就是脏数据。
                // 只比站点不够（弹栈那条路上档恰好没变）；整笔丢会误伤同笔里仍有效的那条腿，所以按腿丢。
                val freshAttempts = attempts.filterNot {
                    isStaleLeg(roundAtRequest, it.outcome.site, contextRound, source.sites)
                }
                val fresh = freshAttempts.map { it.outcome }
                if (fresh.isEmpty()) return@launch
                // 换过名字这件事必须上屏：胶囊与历史存的都是用户的**原词**，少了这句话，
                // 「搜 setmen 出来一批 tokenbox 的画」在界面上就没有任何地方解释。
                if (first) {
                    artistAliasNotice = freshAttempts.mapNotNull { it.aliasNotice }
                        .takeIf { it.isNotEmpty() }?.joinToString("；")
                }

                val postsByLeg = LinkedHashMap<GallerySite, List<GalleryPost>>()
                val returnedCount = LinkedHashMap<GallerySite, Int>()
                val reasons = LinkedHashMap(legFailures)
                for (leg in fresh) {
                    // **答上了才算数**：站方回了空表是"这一站到到底"的凭据（记进 returnedCount），
                    // 而请求没成只是"这一轮它没赶上"（进 legFailures，不参与到底判据）。
                    // 两者混成一谈，一次 401 就会把那一站永久踢出翻页，页尾却写着「已经到底」。
                    if (leg.answered) {
                        postsByLeg[leg.site] = leg.posts
                        returnedCount[leg.site] = leg.posts.size
                    } else {
                        reasons[leg.site] = leg.reason ?: "搜索失败"
                    }
                }
                // 缺席的腿不写进 returnedCount ⇒ 不算到底（一次抖动不许永久踢掉一站）。
                exhaustedBySite = exhaustedBySite + GallerySearchMerge.exhaustedSites(returnedCount, ::pageSizeFor)

                val allFailed = fresh.size == dispatched.size && fresh.all { !it.answered }
                if (allFailed) {
                    // 第 1 页失败 → 交给整屏空态说；续页失败 → 只在页尾说。
                    // 续页失败不能写成 searchError：那会把屏上已经摆出来的几十张图换成错误页。
                    val message = fresh.firstOrNull { !it.answered }?.reason ?: "搜索失败"
                    if (first) searchError = message else loadMoreError = message
                    legFailures = reasons
                    return@launch
                }

                val merged = GallerySearchMerge.interleave(postsByLeg, seenKeys).getOrThrow()
                seenKeys = HashSet(merged.seenKeys)
                searchError = null
                loadMoreError = null
                appendPage(
                    merged.posts,
                    // `page` 这一格只当"续页轮次计数"用（retryLoadMore 靠它 +1）；
                    // 各腿真正的游标在 pageBySite 里，两腿页宽不同、不会互相顶。
                    pages.values.max(),
                    allLegsExhausted(dispatched, exhaustedBySite),
                )
                pageBySite = pageBySite + pages
                // 站方给了行没给图的张数：合并那层已经数好了，appendPage 拿到的都是可摆的，
                // 所以这一份要单独并进来（两边都是 +=，不重复计）。
                droppedNoImage += merged.droppedUnusable
                legFailures = reasons
                if (first) {
                    // 历史存的是**用户那一排**（不带排行伪标签），且记的是**这一串真正在哪些站搜过**
                    // —— 未配账号的那条腿没搜过，就不该出现在那条历史的站点标记里。
                    saveHistory(GallerySearchEntry(dispatched.toSet(), visibleQuery))
                }
            } finally {
                // 只有"这一笔还是当前那笔"才收尾：被新查询取消掉的旧协程不能替新的改状态，
                // 否则会出现"结果还在飞、加载态已经灭了"。
                if (searchJob === coroutineContext[Job]) {
                    isSearching = false
                    isLoadingMore = false
                }
            }
        }
    }

    /**
     * 一条腿的那一笔取数。两腿并发与批次 J 的"换个名字再打一次"共用这一条 ——
     * 那两处唯一的差别是查询串，而 `when` 写两遍迟早分叉（漏掉一站是编译期能抓到的那种，
     * 记错每页张数就不是了）。
     */
    private suspend fun searchLeg(site: GallerySite, query: String, page: Int): Result<List<GalleryPost>> =
        when (site) {
            GallerySite.GELBOORU -> boards.gelbooru.searchPosts(query, page, pageSizeFor(site))
            GallerySite.YANDERE -> boards.yandere.searchPosts(query, page, pageSizeFor(site))
            GallerySite.SAFEBOORU -> boards.safebooru.searchPosts(query, page, pageSizeFor(site))
            GallerySite.PIXIV -> GallerySite.notWired("搜索腿")
        }

    /**
     * 这一腿**真正发出去的那一串**（含排行伪标签），必要时先把 Gelbooru 的合成档边界定出来。
     *
     * [LegQuery.Skip] = 这一腿**这一轮不发**（调用方判成缺席并说原因）。两条走到 Skip 的路：
     * - Gelbooru 的时间窗定不出边界（凭据没了、站方抽风、探测超上限、命中总数不自洽）；
     * - 排行档要窗口而窗口边界算不出来。
     * ⚠️ **绝不能退回"没有后缀的那一串"** —— 那按站方语义就是全站最新/全站高分，
     * 而胶囊上写着「周」，就是本仓最忌的静默交错。
     *
     * 翻历史期（[anchorAtRequest] 非空）时这一趟要定**两枚**边界：这一期的第一天、以及
     * "这一期之后的第一天"（不含的上界）。第二枚吃第一枚的读数当种子，两枚共用一份探测预算
     * （见 `huntWindowBounds`）。上界那枚断在"问到站方最新一条仍在这期之内"时**不算断** ——
     * 那是证到了这一期就是收到最新，交回不带上界的那一串才是对的（`GelbooruWindowEnd.Unbounded`）。
     */
    private suspend fun legQuery(
        site: GallerySite,
        filters: List<GalleryTagFilter>,
        rankingAtRequest: GalleryRanking,
        anchorAtRequest: LocalDate?,
        todayUtc: LocalDate,
    ): LegQuery {
        val firstDay = GalleryRankings.boundaryTargetDay(site, rankingAtRequest, anchorAtRequest, todayUtc)
        val startId: Long?
        val end: GelbooruWindowEnd
        if (firstDay == null) {
            startId = null
            end = GelbooruWindowEnd.Unbounded
        } else {
            val dayAfter = GalleryRankings.boundaryEndTargetDay(site, rankingAtRequest, anchorAtRequest, todayUtc)
            val bounds = gelbooruWindowFinder.huntWindowBounds(firstDay, dayAfter, todayUtc)
            if (bounds is GelbooruWindowBounds.Blocked) {
                return LegQuery.Skip(bounds.at, bounds.detail, atEnd = dayAfter != null)
            }
            val ready = bounds as GelbooruWindowBounds.Ready
            startId = ready.startId
            end = ready.end
        }
        val query = GalleryRankings.searchQueryFor(
            site,
            filters,
            rankingAtRequest,
            anchorAtRequest,
            todayUtc,
            startId,
            end,
        ) ?: return LegQuery.Skip(null, null, atEnd = false)
        return LegQuery.Ready(query)
    }

    /**
     * 这一腿的时间预算：**只有一处**要放宽 —— 要探两枚边界的那一腿（Gelbooru 的历史期）。
     *
     * 日/周/月/年**本期**那一档仍然吃日榜那头的 12 秒（真机 2026-10-15 实测在那档内出图），
     * 三处共用一份预算这条纪律不变；这里不是"给 Gelbooru 单独松绑"，是"多探一枚边界的那一腿多等一会儿" ——
     * 同一站在本期那一档照旧 12 秒。数值与量测见 [GelbooruWindowBoundary.CLOSED_WINDOW_LEG_TIMEOUT_MS]。
     */
    private fun legBudgetMs(
        site: GallerySite,
        ranking: GalleryRanking,
        anchor: LocalDate?,
        todayUtc: LocalDate,
    ): Long = if (GalleryRankings.needsGelbooruEndBoundary(site, ranking, anchor, todayUtc)) {
        GelbooruWindowBoundary.CLOSED_WINDOW_LEG_TIMEOUT_MS
    } else {
        GalleryDailyFeed.PER_SITE_TIMEOUT_MS
    }

    /**
     * 屏上那一句缺席原因：先说清"这一站不给时间查询，要先探几次"，再把**断在哪一步**接上去。
     *
     * 光一句"没探成"是不够的：那几种成因里只有一种该去设置里补凭据，其余几种该等站方缓过来，
     * 而用户分不出来的结果就是把凭据改一遍再截一张图过来。站方原话（401 那句）直接带上。
     *
     * ⚠️ 凭据被拒那一族**必须把那句原话摆在最前**：`galleryNotices` 那层按前缀归类
     * （见 [com.venera.compose.gallery.domain.kindOfReason]），归到那一档屏上才长出
     * 「去配置账号」这枚真能到达设置页的按钮。档名与机制退到后半句说。
     */
    private fun legSkipped(ranking: GalleryRanking, skip: LegQuery.Skip): String {
        if (skip.at.isCredentialShaped()) {
            val hint = skip.detail?.takeIf { it.startsWith(GelbooruCredentialState.REJECTED_HINT) }
                ?: GelbooruCredentialState.REJECTED_HINT
            return "$hint。${ranking.shortLabel}排行的窗口边界要靠这一笔定出来，" +
                "定不出来 ⇒ 这一腿这一轮不发（不发好过发一串没有后缀的：那按站方语义就是全站高分）"
        }
        val why = when (skip.at) {
            GelbooruWindowBreak.ProbeNotWired -> "这一档的探测通道没接上"
            GelbooruWindowBreak.LatestIdRejected -> skip.detail ?: "Gelbooru 没接受这组凭据"
            GelbooruWindowBreak.LatestIdUnusable -> "Gelbooru 没给出最新一条的编号"
            GelbooruWindowBreak.ProbeRejected -> skip.detail ?: "Gelbooru 没接受定界那一笔请求"
            GelbooruWindowBreak.ProbeNoEntry -> "定界问到的位置上没有条目"
            // 站方这一笔**压根没带** `created_at`：要去查的是请求参数与 DTO，不是解析器。
            // 从前这一档与下面那档共用一句话，2026-10-16 才分开（真机那张截图把排查带去过早了）。
            GelbooruWindowBreak.StampMissing -> "Gelbooru 这一笔没带上传时间"
            // 带了、可读不出 ⇒ 把**原文带上屏**。这一格的存在理由就是：下一次截图是一条读数，
            // 而不是又一次"看起来像格式问题"的往返。站方的串可能很长，截一段就够认格式。
            // ⚠️ 措辞不套第二层括号：这一整句本来就在「（$why）」里面，
            // 真机 00:30 那张读成了「…读不出日子（原文「…」））」—— 三层括号糊成一团。
            GelbooruWindowBreak.StampUnparsable -> "Gelbooru 的上传时间读不出日子，站方给的是" +
                (skip.detail?.takeIf { it.isNotBlank() }?.let { "「${it.take(48)}」" } ?: "一串空东西")
            GelbooruWindowBreak.CountInconsistent -> "Gelbooru 的编号过滤没按语义生效"
            GelbooruWindowBreak.SameReadingRepeated -> "连着几笔探到同一个位置，没往前走"
            GelbooruWindowBreak.ProbeBudget -> "探满上限还没括住这一档"
            GelbooruWindowBreak.TopReachedWithoutBracket -> "问到最新一条仍没括住这一档"
            null -> "窗口边界算不出来"
        }
        // 断在哪一枚要说清：历史期那一趟是两枚边界，起点与上界的排查出路不同。
        val which = if (skip.atEnd) "这一期的上界" else "窗口起点"
        return "${ranking.shortLabel}排行：这一站的窗口要先探出$which，这一次没探成（$why），" +
            "所以这一腿这一轮不发"
    }

    /**
     * 这一笔断点**是不是凭据那一族**。
     *
     * 判据按**断在哪一步**（枚举成员），不按"报错里含不含『凭据』"：那两笔请求都带着
     * `api_key` 与 `user_id` 出去（[GelbooruWindowBreak.LatestIdRejected] 是锚点那一笔、
     * [GelbooruWindowBreak.ProbeRejected] 是探针那一笔），被拒就是"这一组凭据现在不好使"，
     * 与站方回了空表 / 探不够 / 计数不自洽是四种完全不同的事。
     *
     * ⚠️ 这一族给的是「去配置账号」的出口，判宽了会把"再等一分钟就好"的处境指去设置页，
     * 判窄了就是 2026-10-15 真机那个样子：一句"窗口没探成" + 一枚按下去没用的重试。
     */
    private fun GelbooruWindowBreak?.isCredentialShaped(): Boolean =
        this == GelbooruWindowBreak.LatestIdRejected || this == GelbooruWindowBreak.ProbeRejected

    /**
     * Gelbooru 合成档的定界器（2026-10-13）。
     *
     * 取数走 [GalleryPorts.gelbooruWindows] 那一颗契约，**不直连** `GelbooruClient`（UI 层直连实现类
     * 是 `BusinessApiBoundaryTest` 的 A/B 两条红线，"要开口就加契约"是它自己给出的处置）。
     * 那一颗在桌面上不接线 → 这里就是 null，而桌面今天的判据面本来也不列这四档，null 不会被点出来。
     * 状态机与缓存都在 `gallery/domain` 那颗纯件里，这里只负责把两枚挂起函数递进去。
     */
    private val gelbooruWindowFinder by lazy {
        val windows = GalleryPorts.of(app).gelbooruWindows
        GelbooruWindowFinder(
            // **不压成 null**：出网那一笔的失败原话要一路留到屏上（"没探成"三个字分不出
            // 是没配凭据还是在等站方缓过来，2026-10-13 真机这一笔就是这么卡住的）。
            latestId = { windows?.fetchLatestPostId() },
            probeAtLeast = { atLeast ->
                windows?.probeWindowStart(atLeast)?.map {
                    Triple(it.oldestId, it.createdAtRaw, it.count)
                }
            },
        )
    }

    /**
     * 站方那条画师别名链（批次 J）：先按原词取画师记录，再跟一次 `artist/show/<alias_id>` 的重定向。
     *
     * 交回 null = **站方没有明确给出关联**，调用方就不重搜、保持 0 结果（用户拍的口径）。
     * 取数失败、非 200、挂维护页那一律算 null，不重试也不报错 —— 这一条链是"顺手多救一次"，
     * 它自己出问题的时候，屏上必须退回**没做这个功能时**那个样子。
     */
    private suspend fun canonicalArtist(site: GallerySite, original: String): String? {
        // 别拿 Gelbooru 那条腿去走 yande.re 的门（参数在这一行之前就会求值，所以守卫要放在前面）。
        if (!GalleryArtistAlias.supports(site)) return null
        val title = boards.yandere.resolveArtistAlias(original).getOrNull()
        return GalleryArtistAlias.canonicalNameFor(site, original, title)
    }

    /**
     * 续页失败后页尾那枚「重试」。
     *
     * 必须由**用户点**才再发：自动重试会让 `isLoadingMore` 一落回 false 就再发同一笔，
     * 对着一个已知失败的请求无限循环（页面的续页闸门正是靠 [loadMoreError] 非空挡住的）。
     */
    fun retryLoadMore() {
        loadMoreError = null
        runSearch(page + 1)
    }

    /**
     * 补全：拉一次候选（调用方负责防抖）。
     *
     * 用**自己那一笔 Job**串起来，且落地前复验站 —— 这里曾经不持有 Job，于是：
     *  - 连打两下 `loli` → `lolita`，先发的那笔若回得慢，会把 `lolita` 的候选覆盖成 `loli` 的；
     *  - 换站时旧站的在途响应落地，把旧站词表回填进新站的候选列表。
     * 两种都是"内容与当前上下文对不上"，且都不会报错，最难被发现。
     */
    private var suggestJob: Job? = null

    fun loadSuggestions(term: String) {
        suggestJob?.cancel()
        // **同步置位**，不放进 launch 体里：`launch` 要等一次调度才跑，
        // 中间会空出一帧"没在加载、候选又是空的" —— 那一帧画的正是
        // 「没有以它开头的标签」那句假话（真机上表现为打字后先闪一下"没有"）。
        isSuggesting = true
        suggestJob = viewModelScope.launch(Dispatchers.IO) {
            val sourceAtRequest = source
            try {
                val legs = sourceAtRequest.availableLegs(gelbooruConfigured)
                if (legs.isEmpty()) {
                    // 未配账号的那一档没有词表可问。清空候选、不报错 ——
                    // 那句"这一站还没配置"由卡上的来源读数去说，别让它长成"没有以它开头的标签"
                    // （那是把我们自己的闸说成站方的答案）。
                    suggestions = emptyList()
                    suggestError = null
                    return@launch
                }
                val results = coroutineScope {
                    legs.map { leg ->
                        async {
                            leg to when (leg) {
                                GallerySite.GELBOORU -> boards.gelbooru.searchTags(term)
                                GallerySite.YANDERE -> boards.yandere.searchTags(term)
                                GallerySite.SAFEBOORU -> boards.safebooru.searchTags(term)
                                GallerySite.PIXIV -> GallerySite.notWired("标签候选")
                            }
                        }
                    }.awaitAll()
                }
                // 档已换 → 这批候选属于上一档，作废（连打两下 `loli` → `lolita` 同理）。
                if (sourceAtRequest != source) return@launch
                // 两腿的词表**各问一次再并**：「全部」档搜的是两站，只问一条腿的候选
                // 就会长成"这一站没这个标签"，而另一站其实有货。同名候选保留先到的那条
                // （站表序在前的那一腿）。
                val merged = LinkedHashMap<String, GalleryTagSuggestion>()
                var failedLegs = 0
                for ((_, outcome) in results) {
                    outcome.onSuccess { list ->
                        refineGalleryTagSuggestions(term, list, SUGGEST_ROWS).forEach { suggestion ->
                            merged.putIfAbsent(suggestion.name, suggestion)
                        }
                    }.onFailure { failedLegs++ }
                }
                if (merged.isEmpty() && failedLegs == results.size) {
                    suggestError = "标签补全失败"
                    suggestions = emptyList()
                } else {
                    suggestError = null
                    suggestions = merged.values.take(SUGGEST_ROWS)
                }
            } finally {
                // 只有"这一笔还是当前那笔"才收尾，被更新的输入取消掉的旧笔不许替新的灭灯。
                if (suggestJob === coroutineContext[Job]) isSuggesting = false
            }
        }
    }

    /**
     * 历史：**按内容域各存一份键**，进 ViewModel 就读一次，成功搜索后写回键值门面。
     *
     * ⚠️ **恢复放在 init 里**（2026-10-18，用户「搜索历史经常会自动没了」）：
     * 从前这件事挂在 [restoreHistoryIfNeeded]，而它唯一的调用点是
     * `GallerySearchArea` 的 `LaunchedEffect(Unit)` —— 那个 effect 只在**搜索卡组合时**跑一次。
     * 于是"ViewModel 重建后直接进入收起态（那一态不组合搜索卡）/ 搜索卡被回收 / 从别的入口开搜索"
     * 这几条路上，恢复压根没跑：盘上那几条还在，屏上一开始是空的，读起来就是"历史自动没了"。
     *
     * ⚠️ **一枚键一件事**（同日改判，用户点「分域各存一份」）：两域从前共用 `history` 那一枚键、
     * 只在读取侧按域过滤。于是同一个词在 pixiv 与 booru 都搜过之后，会被
     * [pushGallerySearchHistory] **并成一条含两域的条目**，而屏上按域过滤正好把它从另一域里滤掉 ——
     * 那一行就"永久没了"。键按域分开之后，并键这件事在**结构上**不可能发生。
     * 旧的合并档由 [migrateSharedHistory] 摊一次，一条都不丢。
     */
    private val prefs by lazy {
        GalleryPorts.of(app).stores.open(PreferenceKeys.PREFS_GALLERY_SEARCH)
    }

    /** 每一域自己那一枚键（名字定在共享那颗，桌面与对岸同一个来源）。 */
    private fun historyKey(d: GalleryHomeDomain): String = GallerySearch.historyKeyOf(d)

    init {
        acceptHistory(loadHistoryFor(domain))
    }

    /** 读这一域的历史；这一域还没写过时，先把旧的共用档摊开。 */
    private fun loadHistoryFor(d: GalleryHomeDomain): List<GallerySearchEntry> {
        prefs.getString(historyKey(d), null)?.let { return decodeGallerySearchHistory(it) }
        return migrateSharedHistory()[d] ?: emptyList()
    }

    /**
     * 把旧那一枚共用 `history` 摊成两份、写回各自的键。
     *
     * 摊完**删掉旧键**：留着它，下一次读到的就是"旧档 + 新档"两份，而人眼里只有一份历史；
     * 而一条被并过域的旧条目会同时摊进两域（它本来就属于两边），这正是它从前在屏上消失的那一半。
     */
    private fun migrateSharedHistory(): Map<GalleryHomeDomain, List<GallerySearchEntry>> {
        val legacyKey = GallerySearch.LEGACY_SHARED_HISTORY_KEY
        val legacy = decodeGallerySearchHistory(prefs.getString(legacyKey, null))
        if (legacy.isEmpty()) {
            prefs.remove(legacyKey)
            return emptyMap()
        }
        val split = GalleryHomeDomain.entries.associateWith { d -> legacy.filter { it.belongsTo(d) } }
        split.forEach { (d, list) ->
            if (list.isNotEmpty()) prefs.put(historyKey(d), encodeGallerySearchHistory(list))
        }
        prefs.remove(legacyKey)
        return split
    }

    /** 换域 = 换那一份历史（条件与结果清空，但**不动**用户挑好的条件，见 [setDomain] 那三条纪律）。 */
    private fun reloadHistory() {
        acceptHistory(loadHistoryFor(domain))
    }

    /**
     * 老入口保留（UI 侧那一行还在）：现在 init 已经读过一次盘，这里只在"盘上比内存新"时补。
     */
    fun restoreHistoryIfNeeded() {
        if (history.isNotEmpty()) return
        acceptHistory(loadHistoryFor(domain))
    }

    private fun saveHistory(entry: GallerySearchEntry) {
        val next = pushGallerySearchHistory(history, entry)
        acceptHistory(next)
        prefs.put(historyKey(domain), encodeGallerySearchHistory(next))
    }

    /**
     * 屏上那几条历史 —— `history` 现在**本来就只装当前域**（键都分开了，读出来就是一域的那几行）。
     *
     * 名字与形状留着：UI 有几处在读它。从前它按 `belongsTo(domain)` 过滤，那是两域共用一枚键时的
     * 权宜，也正是"同一个词被并成一条、另一域那一行看不见"的落点 —— 键分开之后这层过滤无事可做。
     */
    val visibleHistory: List<GallerySearchEntry>
        get() = history

    /** 清空 —— 只清**当前这一域**（用户在 pixiv 上点「清空」，不该把 booru 那些一起抹掉）。 */
    fun clearHistory() {
        acceptHistory(emptyList())
        // 走 remove 而不是"写入编码后的空表"：两种读法今天都返回空表，明天不一样 ——
        // 写成空表在盘上留下的是「v2」哨兵，读侧哪天改成"没这个键就带一份默认词"时会被当成"用户清过"。
        // 桌面那颗 `desktopHistoryClear` 走的正是 remove，两端同语义（改分域键时这里一度滑成 put，已改回）。
        prefs.remove(historyKey(domain))
    }

    /**
     * 删掉一条历史（全屏层历史行右侧那枚 ×）。
     *
     * 此前只有 [clearHistory] 一个方法、而且没有任何调用点 —— 历史是"只进不出"的：
     * 单条删不掉、整清也没入口。这条与它一起补上入口。
     */
    fun removeHistory(entry: GallerySearchEntry) {
        val next = history.filterNot { it == entry }
        acceptHistory(next)
        prefs.put(historyKey(domain), encodeGallerySearchHistory(next))
    }

    /**
     * 点一条历史记录：切到那一轮用的站、把条件装回来，**并立刻按它搜一遍、收成一条**
     * （用户 2026-09-26 真机反馈后改判；9-25 那条"只切源 + 填框、不直接开搜"作废）。
     *
     * 改判的直接原因是实测出来的死局：装回条件之后，**"回车"那条退路是死的** ——
     * 框里被填上 `loli`、补全首行也是 `loli`，而回车走的是"选中补全首行"，
     * 撞上去重判断静默返回；于是"点历史"和"按键盘搜索键"两下都没有任何反应。
     * 而搜索历史的通用语义本来就是"把那一次搜索再跑一遍"，所以这里直接跑。
     *
     * 输入框**不再回填查询串**：胶囊已经把条件摆全了，再往框里塞一份就成了同一个 `loli`
     * 在卡里出现两次（真机截图实锤），还会白发一笔以它为前缀的补全请求。
     * 要确认/改条件，收成一条之后点卡上那一行胶囊（或顶栏那枚 🔍）就展开了。
     *
     * 搜这一笔**不绕 [applyFilters]**：它有一条 `next == filters` 的短路，
     * 连点同一条历史时条件没变，会被短路掉 —— 那正是"点了没反应"的另一种长相。
     *
     * 2026-09-28 起它与大图页交接走**同一条路径**（[openContext]）：点历史之前那一轮先压栈，
     * 返回能回到点历史之前那面墙。三条"换一轮上下文"的入口口径必须一致，
     * 否则同屏里出现两种语义（点标签能回退、点历史不能），下一轮一定被当成缺陷再报一次。
     */
    fun applyHistory(entry: GallerySearchEntry) {
        // pixiv 那一条走另一条路：它不是"一串胶囊"，而是"一个词 + 那一排条件"，
        // 而条件**不跟着历史走**（历史只记词 —— 见 `PixivSearchFilter`：那一排是"怎么看这一句"，
        // 与"我搜过什么"不是一件事）。所以这里只把词装回框里，条件留在用户手上。
        if (entry.belongsTo(GalleryHomeDomain.PIXIV)) {
            // 走与"点一枚 pixiv 标签"同一条入口（`openPixivContext`）：点历史 = "把那次搜索再跑一遍"，
            // 与交接唯一的差别是它**要把上一轮压栈**——那条判据在两处都成立，而清场 / 装词 /
            // 收条 / 开搜这一整套只有一份。从前这里自己写一遍，于是「点历史不收条」「在途没掐」
            // 这类改一处漏一处的偏差迟早出现（同一族已在 booru 那侧立案过）。
            openPixivContext(entry.query)
            return
        }
        openContext(
            // 一条在两站都搜过的历史，点开就该回到「全部」那一档。退回单站等于把他上次
            // 看到的两站结果少摆一半 —— 而那正是这条历史被合并成一条的理由。
            if (entry.sites.size > 1) GallerySearchSource.ALL else GallerySearchSource.single(entry.site),
            entry.filters,
            thenCollapse = true,
        )
    }

    // ══════════════════════════════════════════════════════════════════════
    // pixiv 那一侧（2026-10-17 方案 C：卡内展开的筛选面板）
    //
    // 与上面那一整套**平行**，不是"塞进 filters 里换一种拼法"：
    // booru 吃 `tags=a+b order:score`，pixiv 吃"一个词 + s_mode/order/ratio/scd/ecd"。
    // 两条语法硬并进同一颗 `runSearch` 会逼出十来个 `if (site == PIXIV)`，
    // 而那些分支里任何一处走错都不报错，只出假读数。
    //
    // 共用的是**状态**（results / page / exhausted / isSearching / mode）：
    // 那面墙、页尾、读数槽、滚动落点全都只认这几个字段，复制一份出来就是两套墙。
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 换内容域（首页那一排「图库站 / Pixiv」）。
     *
     * 三件事必须一起做，少一件就是假读数：
     * 1. **在途那一笔掐掉** —— 旧域的结果落地会写进新域的墙；
     * 2. **手上这一轮清空** —— 卡上写着 pixiv 的排序与筛选、身下的墙却是 booru 的图，
     *    这是最难被发现的一种错（图看起来"就是些图"）；
     * 3. **条件各留各的** —— 用户挑好的条件不抹（换站不抹条件那条改判的同一个道理），
     *    但也**不自动重搜**：来回切两下就是四笔网络往返，而他此刻还没决定要搜什么。
     *
     * 2026-10-23 加第四件：**主词跟着人走**（用户「双源互搜，切域不重打」）—— 见下面那段。
     */
    fun setDomain(next: GalleryHomeDomain) {
        if (next == domain) return
        // ── 词条保持（2026-10-23，用户「图库站 ↔ Pixiv 互搜，词不用重打」）──
        //
        // 切域时把上一域的**主词**带进下一域的输入格：booru 的主词是第一枚胶囊
        //（一枚都没有时是框里那半截词），pixiv 的主词就是 `pixivTerm`。带过去之后
        // 按一下键盘搜索键就能在新域开搜，不用把同一个词再打一遍。
        //
        // 三条纪律：
        // 1. **只带词，不代搜** —— 守住上面第 3 条"不自动重搜"：来回切两下就是四笔往返，
        //    而他此刻还没决定要搜什么；词进了输入格，按不按那一键由用户决定；
        // 2. **空词不覆盖** —— 上一域什么都没打时不动下一域的输入格，否则切一圈回来词没了，
        //    那与"保持"正好相反；
        // 3. **不做清洗** —— 这里搬的是用户自己敲的字（含 `#123` / `id:123` 这种 pixiv
        //    直跳写法，见 [submitPixivTerm]）；要清洗的只有站方数据的交接标签（见
        //    `GallerySearchHandoff.postTag` 调的 `sanitizeGalleryTagName`）。
        when (next) {
            GalleryHomeDomain.PIXIV -> {
                val main = filters.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: term.trim()
                if (main.isNotBlank()) pixivTerm = main
            }
            GalleryHomeDomain.BOORU -> {
                val main = pixivTerm.trim()
                if (main.isNotBlank()) term = main
            }
        }
        domain = next
        reloadHistory()
        searchJob?.cancel()
        suggestJob?.cancel()
        isSearching = false
        isLoadingMore = false
        suggestions = emptyList()
        suggestError = null
        results = emptyList()
        page = 0
        pageBySite = emptyMap()
        exhausted = false
        exhaustedBySite = emptySet()
        seenKeys = HashSet()
        legFailures = emptyMap()
        artistAliasNotice = null
        searchError = null
        loadMoreError = null
        droppedNoImage = 0
        notice = null
        pixivPanelOpen = false
        // 画师那一栏也跟着清：换域之后屏上不该留着上一域的人名（与"结果不各存各的"同一条）。
        pixivArtists = emptyList()
        pixivArtistError = null
        pixivArtistsExhausted = false
        pixivArtistPage = 0
        pixivArtistNextUrl = null
        isSearchingArtists = false
        mode = GallerySearchMode.INPUT
        // 轮次 +1：已经发出去的那一笔即使取消不掉（底层不配合），落地时也会被这一格判成过期。
        contextRound++
    }

    /** pixiv 那一框里的字。改字**不触发**查询 —— 这一框没有"打一个字就搜一次"的理由。 */
    fun setPixivTerm(next: String) {
        pixivTerm = next
        notice = null
        if (mode != GallerySearchMode.INPUT) mode = GallerySearchMode.INPUT
    }

    /** 键盘上那枚「搜索」/ 框尾那枚箭头。按当前页签走对应那条腿。 */
    fun submitPixivTerm() {
        if (pixivTerm.isBlank()) {
            notice = "先打一个词 —— pixiv 把这一框当标签名搜"
            return
        }
        val trimmed = pixivTerm.trim()
        val directId = trimmed.toLongOrNull()
            ?: GalleryArtistLinks.pixivArtworkId(trimmed)
            ?: (if (trimmed.startsWith("id:", ignoreCase = true)) trimmed.substring(3).trim().toLongOrNull() else null)
            ?: (if (trimmed.startsWith("#")) trimmed.substring(1).trim().toLongOrNull() else null)

        if (directId != null && directId > 0) {
            pendingDirectArtworkId = directId
            return
        }

        // 收条：词已经交了，用户要的是"这个词的结果"。
        mode = GallerySearchMode.RESULTS
        if (pixivKind == PixivSearchKind.ARTIST) {
            runPixivArtistSearch(first = true)
        } else {
            runPixivSearch(1)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // pixiv 搜索的两个页签：作品 / 画师（2026-10-18）
    //
    // 出处：pixez 的 `result_page.dart` 就是 `TabBar(插画 / 画师)`，画师那一栏只调
    // `/v1/search/user`。同一个词、两个端点、两种答复形状 ⇒ 两条独立的取数腿，
    // 但**共用同一份"我在搜什么"**（`pixivTerm`）与同一个 `pixivNextUrl` 之外的游标。
    // ══════════════════════════════════════════════════════════════════════

    /** 这一屏要作品还是要画师。换页签 = 整片换掉（两栏的结果不能混着摆）。 */
    var pixivKind by mutableStateOf(PixivSearchKind.ILLUST)
        internal set

    /** 输入作品号或网页链接时直接唤起大图/作品页的交接槽 */
    var pendingDirectArtworkId by mutableStateOf<Long?>(null)

    /**
     * 画师那一栏的结果（`/v1/search/user`）。
     *
     * 为什么不塞进 `results`：那一格是 `GalleryPost`，喂的是瀑布流、大图页、页尾读数、
     * 滚动落点这一整套。画师不是图，塞进去要么长出一堆假 `GalleryPost`，
     * 要么在每一处调用点加一个 `if (kind == ARTIST)`。两栏各存各的，墙也是两面。
     */
    var pixivArtists by mutableStateOf<List<PixivUser>>(emptyList())
        internal set

    var pixivArtistError by mutableStateOf<String?>(null)
        internal set

    var isSearchingArtists by mutableStateOf(false)
        internal set

    var pixivArtistsExhausted by mutableStateOf(false)
        internal set

    /** 画师那一栏的分页游标（站方 `next_url`）。null = 第一页 / 到底。 */
    private var pixivArtistNextUrl: String? = null

    private var artistJob: Job? = null

    /** 画师那一栏取到第几页（0 = 还没取过）。只给"到底"读数与续页判据用。 */
    var pixivArtistPage by mutableIntStateOf(0)
        internal set

    /**
     * 换页签。
     *
     * 两栏**各存各的结果**（作品那面墙留着，切回来不用重搜）：用户切到"画师"看一眼、
     * 再切回"作品"，屏上该是他刚才那一批图，而不是一片空白 + 一笔新请求。
     * 但**已发的请求按轮次作废**：切走那一刻还在飞的那一笔不能落进另一栏。
     */
    fun setPixivKind(next: PixivSearchKind) {
        if (next == pixivKind) return
        pixivKind = next
        artistJob?.cancel()
        // 作品那一栏在飞的那一笔**也**要掐：从前只 cancel `artistJob`，于是"作品栏发起一笔 →
        // 立刻切到画师再切回"会让第一笔的 `finally` 跑掉（`searchJob` 已指向新协程则不动
        // `isSearching`），而 `contextRound` 已 +1 使它在落地前就 return —— `exhausted` 保持
        // 进入时的 false，页尾与续页判据都认为还能翻。后果是滑到底白转一圈、第二次才到底
        // （2026-10-22 审计）。
        searchJob?.cancel()
        // 游标一并作废：那一笔带走的 `next_url` 属于被作废的那一轮，不该留在原地
        // 让下一轮当"上一页的游标"用。
        pixivNextUrl = null
        isSearchingArtists = false
        pixivArtistError = null
        notice = null
        contextRound++
        // 切过去时手上还没有那一栏的货、也没在飞 ⇒ 就地补一笔（"我打好了词，点画师就该有人"）。
        if (next == PixivSearchKind.ARTIST &&
            pixivArtists.isEmpty() &&
            pixivTerm.isNotBlank()
        ) {
            runPixivArtistSearch(first = true)
        }
    }

    /**
     * 画师那一栏取一页。
     *
     * 与作品那一栏的三条不同：
     * - **没有匿名腿**（这一条端点只认票据）⇒ 没登录时直接给一句人话，不发请求；
     * - **没有筛选**（收藏门槛 / 比例 / 时间只作用于作品）⇒ 只发 `word`；
     * - **一页就是一页**（不本地筛、不补页）。
     */
    private fun runPixivArtistSearch(first: Boolean) {
        val word = pixivTerm.trim()
        if (word.isEmpty()) {
            notice = "先打一个词 —— 画师那一栏按名字搜"
            return
        }
        if (first) artistJob?.cancel() else if (isSearchingArtists) return
        val roundAtRequest = contextRound
        if (first) {
            pixivArtists = emptyList()
            pixivArtistPage = 0
            pixivArtistsExhausted = false
            pixivArtistNextUrl = null
            isSearchingArtists = true
        }
        val cursorAtRequest = if (first) null else pixivArtistNextUrl
        if (!first && cursorAtRequest == null) {
            pixivArtistsExhausted = true
            return
        }
        pixivArtistError = null
        artistJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val port = GalleryPorts.of(app).pixiv
                    ?: throw IOException("这一侧没有接 pixiv 的取数腿")
                val bearer = pixivAccount?.bearerOrNull()
                    ?: throw IOException("画师那一栏要登录 pixiv 才搜得动")
                val page = port.searchUser(bearer, word, cursorAtRequest).getOrThrow()
                if (roundAtRequest != contextRound ||
                    domain != GalleryHomeDomain.PIXIV ||
                    pixivKind != PixivSearchKind.ARTIST ||
                    pixivTerm.trim() != word
                ) {
                    return@launch
                }
                // 去重按用户号：站方翻页边界会重复给同一位（与作品那面墙同一条尺子）。
                val fresh = if (first) {
                    page.users
                } else {
                    val seen = pixivArtists.mapTo(HashSet()) { it.id }
                    page.users.filter { it.id !in seen }
                }
                pixivArtists = if (first) fresh else pixivArtists + fresh
                pixivArtistNextUrl = page.nextUrl
                pixivArtistsExhausted = page.nextUrl == null
                pixivArtistPage = if (first) 1 else pixivArtistPage + 1
                pixivArtistError = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.w(PIXIV_SEARCH_TAG, "pixiv 画师搜索失败", t)
                pixivArtistError = PIXIV_SEARCH_FAILED
            } finally {
                if (artistJob === coroutineContext[Job]) isSearchingArtists = false
            }
        }
    }

    /** 画师名单滚到底时续一页（与作品那面墙的自动续页同一个语义）。 */
    fun loadMorePixivArtists() {
        if (isSearchingArtists || pixivArtistsExhausted || pixivArtistError != null) return
        runPixivArtistSearch(first = false)
    }

    /**
     * 改 pixiv 那一排条件。**改完立刻重搜**（与胶囊同一条纪律：这套 UI 没有"提交"钮）。
     *
     * 框里没词时**不发**：空查询在站方语义里是"全站最新"，把它当搜索结果摆出来就是假结果
     * （booru 那侧清空到零枚退回输入态，是同一条判据的另一种长相）。
     */
    fun setPixivFilter(next: PixivSearchFilter) {
        if (next == pixivFilter) return
        // 「人气」点不点得动，判据收在**这一处**（不散到各颗 UI）：拒的时候**连条件都不改** ——
        // pixez 也是 toast 之后 selectSort 不动。改了不改请求是同一个下场：屏上亮着「人气」，
        // 请求里发的是「最新」，那就是假开关。
        if (next.order.needsPremium) {
            pixivPopularBlockedNotice()?.let { refusal ->
                notice = refusal
                return
            }
        }
        pixivFilter = next
        notice = null
        if (pixivTerm.isNotBlank()) runPixivSearch(1)
    }

    /** 面板右下角那枚「重置全部」—— 只重置面板里那几段（排序常驻可见，不跟着重置）。 */
    fun resetPixivFilter() = setPixivFilter(PixivSearchFilter.DEFAULT)

    fun setPixivPanelOpen(open: Boolean) {
        pixivPanelOpen = open
    }

    /**
     * pixiv 那一笔取数。
     *
     * 只有一条腿（没有 booru 那种"两腿并发 + 合并 + 缺席原因"），所以这里不套 `GalleryLegGuard`
     * 那套包装 —— 套上去会凭空长出一份"哪条腿没跑起来"的读数，而 pixiv 只有一个"跑没跑"。
     *
     * ## 两条腿按登录态二选一（2026-10-17 深夜，按 pixez 的口径补齐）
     *
     * - **登录着**：`app-api /v1/search/illust`（带票据）。人气（`sort=popular_desc`）与
     *   收藏（`bookmark_num_min`）只有这条发得出去，翻页只认站方的 `next_url`；
     * - **没登录**：`www /ajax/search/artworks/{词}`。只发得出排序（旧序）、标签模式、比例、时间窗，
     *   翻页认页码 `p=`。
     *
     * 2026-10-19 再分第三岔：**登录着但非会员**点「人气」时走 `popular-preview`
     * （[GalleryPixivWorks.popularPreview]）。它只在"票 + 非会员 + 人气"三样同时成立时才选上，
     * 代价是那一条**只认词、只给一页** —— 被丢掉的几枚由 [PIXIV_PREVIEW_NOTICE] 说上屏，
     * 而不是静默按其余条件发出去。
     *
     * 两条腿**不混发**：混起来就是"这一页按一套参数、下一页按另一套"，而屏上读不出对错。
     * 票据取不到时**不静默降级**成"人气看起来选着、其实没发"——[pixivAccountNotice] 会先说话。
     *
     * 落地前复验三样：域、条件、轮次。任何一样变过就整笔作废（这条腿没有"按腿作废"那一层）。
     */
    private fun runPixivSearch(nextPage: Int) {
        val word = pixivTerm.trim()
        if (word.isEmpty()) {
            notice = "先打一个词 —— pixiv 把这一框当标签名搜"
            return
        }
        if (nextPage == 1) searchJob?.cancel() else if (isSearching || isLoadingMore) return
        val filterAtRequest = pixivFilter
        val roundAtRequest = contextRound
        val first = nextPage == 1
        // 登录腿的续页只认站方给的 `next_url`：它空着 = 上一页就是最后一页。
        // 这时再发一次会拿回第一页（去重把整页吃光），屏上读起来是"滚到底了却什么都没加载"。
        if (!first && pixivLoginLeg && pixivNextUrl == null) {
            exhausted = true
            isLoadingMore = false
            return
        }
        // 翻页游标在发车前定死：在途期间用户可能换条件，这一笔仍按发起那一刻的游标查。
        val cursorAtRequest = if (first) null else pixivNextUrl
        if (first) {
            beginSearch(PIXIV_PAGE_SIZE)
            pixivNextUrl = null
            // 第一页整片换过一轮 ⇒ 那两张表也从头攒：留着上一轮的就成了
            // "这一轮的墙上摆着上一轮的头像"，而 uid 撞不上时谁也看不出来。
            pixivTitles = emptyMap()
            pixivAvatars = emptyMap()
            // 这一格先按登录态给一句（用户点「人气」那一刻就有回响）；协程里拿到真票据后
            // 会再核一次 —— 两者不一致时以票据为准（见下面 bearer 那一行）。
            notice = pixivAccountNotice(pixivLoggedIn && pixivBearerReady != false)
        } else {
            isLoadingMore = true
            loadMoreError = null
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val ports = GalleryPorts.of(app)
                val port = ports.pixiv
                    ?: throw IOException("这一侧没有接 pixiv 的取数腿")
                // 票据**现取**（`bearerOrNull` 自带刷新）：它 null 就是这一会儿给不出，
                // 那就退回匿名腿 —— 那时人气发不出去，所以 notice 要按它重说一遍
                // （判据见 `pixivAccountNotice(bearerReady)`：登录态与票据会不一致）。
                val bearer = pixivAccount?.bearerOrNull()
                val bearerReady = bearer != null
                // 会员位按这一笔现取的值用：它决定两件事 ——
                // 收藏那档的发法（会员走 `bookmark_num_min`，其余走词尾 `users入り`，两种都真生效），
                // 以及「人气」发哪一条腿（会员走搜索腿的 `sort=popular_desc`，其余走预览腿）。
                val premium = bearer != null && pixivPremium
                // 非会员点「人气」不再被拒，改走**人气预览**那一跳（2026-10-19 · 批 233-4，
                // 用户「参考原版 pixez 是怎么做无会员能用这个人气榜的」）。依据与代价都在
                // `PixivAppClient.popularPreview` 那颗的注释里：只认词、只给一页。
                val previewLeg = bearer != null && !premium && filterAtRequest.order.needsPremium
                // 预览腿连词尾的 `Nusers入り` 都不加：那一枚是给搜索腿做的收藏门槛，
                // 这一条不认收藏 —— 往词里塞它是"看起来筛过"，不是筛。
                val query = if (previewLeg) word else filterAtRequest.searchWord(word, premium)
                if (first) {
                    pixivBearerReady = bearerReady
                    notice = if (previewLeg) PIXIV_PREVIEW_NOTICE else pixivAccountNotice(bearerReady)
                }
                val collected = ArrayList<GalleryPost>(PIXIV_PAGE_SIZE)
                val titles = HashMap<String, String>()
                val avatars = HashMap<String, String>()
                var cursor = cursorAtRequest
                var pageNo = nextPage
                var done = false
                var hops = 0
                // 作品类型与比例只筛得到"已取到的这一页"（站方没有这枚参数 / app 腿没有 ratio）。
                // 一页被筛空**不等于到底**，所以同一笔里最多再往前要 [PIXIV_EMPTY_PAGE_HOPS] 页。
                // 上限是硬的：不能靠"一直翻到有货"，那在冷门词上就是几十趟请求。
                while (true) {
                    val works: List<PixivWork>
                    val nextCursor: String?
                    if (bearer != null) {
                        val page = if (previewLeg) {
                            port.popularPreview(bearer, query).getOrThrow()
                        } else {
                            port.searchIllust(bearer, query, filterAtRequest, premium, cursor).getOrThrow()
                        }
                        works = page.works
                        // 预览腿**不接游标**：它那一跳的调用面没有 offset（见那颗函数的注释）。
                        // 接了站方随手回的那一枚 = 第二页按一套没量过的口径取，屏上读不出对错。
                        nextCursor = if (previewLeg) null else page.nextUrl
                        // 到底判据吃站方的 `next_url`，不吃"这一页剩几条"：筛过的条数与翻不翻页无关。
                        done = nextCursor == null
                    } else {
                        val list = port.tagWorks(query, pageNo, filterAtRequest).getOrThrow()
                        works = list
                        nextCursor = null
                        // 匿名腿没有游标，到底判据吃**站方回了几条**（不是筛完剩几条）。
                        done = list.size < PIXIV_PAGE_SIZE
                    }
                    // 词也进作废判据：画师腿（上面那一支）早就有 `pixivTerm.trim() != word` 这一条，
                    // 作品腿漏了。匿名腿按 `pageNo` 翻页、与词无关，于是"在途时换词"会把新词的
                    // 第二页追加进旧词的结果墙 —— 屏上读不出对错（2026-10-22 审计）。
                    if (roundAtRequest != contextRound ||
                        domain != GalleryHomeDomain.PIXIV ||
                        pixivFilter != filterAtRequest ||
                        pixivTerm.trim() != word
                    ) {
                        return@launch
                    }
                    for (work in works) {
                        if (!filterAtRequest.keeps(work.ugoira, work.post.width, work.post.height)) continue
                        collected += work.post
                        if (work.title.isNotBlank()) titles[work.post.uid] = work.title
                        // 空串也照写：整面墙都走"头像 + 标题 + 作者名"那一档，
                        // 站方没给头像的那几张画首字母座（`GalleryCardCaption` 的降级），
                        // 不在一面墙里混两种形状。
                        avatars[work.post.uid] = work.authorAvatarUrl
                    }
                    cursor = nextCursor
                    pageNo += 1
                    if (collected.isNotEmpty() || done || ++hops >= PIXIV_EMPTY_PAGE_HOPS) break
                }
                // 去重与 booru 那一侧同一把尺子（`GalleryMerge.dedupKeys`）：
                // 站方翻页边界上会重复给同一条，不挡就会在墙中间出现两张一样的图。
                val fresh = ArrayList<GalleryPost>(collected.size)
                for (post in collected) {
                    val keys = with(GalleryMerge) { post.dedupKeys() }
                    if (keys.any { it in seenKeys }) continue
                    seenKeys.addAll(keys)
                    fresh += post
                }
                pixivLoginLeg = bearer != null
                pixivNextUrl = cursor
                pixivTitles = pixivTitles + titles
                pixivAvatars = pixivAvatars + avatars
                searchError = null
                loadMoreError = null
                // `pageNo - 1` 是**这一笔最后取到的那一页**（自动补页时它可能大于 nextPage）：
                // 匿名腿的续页按它 +1 要，报成 nextPage 就会把补过的那几页再取一遍。
                appendPage(fresh, pageNo - 1, done)
                if (first) saveHistory(GallerySearchEntry(GallerySite.PIXIV, word))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                // 技术异常只进日志：那一串可能带着站方正文片段（现量一例是整页 HTML），
                // 直接上屏读不懂，也会把空态那一块撑坏。屏上留一句人话即可。
                Log.w(PIXIV_SEARCH_TAG, "pixiv 搜索失败", t)
                val message = PIXIV_SEARCH_FAILED
                if (first) searchError = message else loadMoreError = message
            } finally {
                if (searchJob === coroutineContext[Job]) {
                    isSearching = false
                    isLoadingMore = false
                }
            }
        }
    }

    /**
     * 「人气」这一档这会儿点不点得动 —— **只要票据**（2026-10-19 改口径：会员那一条限制撤了）。
     *
     * 撤的依据不是猜：非会员的人气有另一条腿可走（`popular-preview`，见 `PixivAppClient.popularPreview`），
     * 而 donor 的调用点正是"toast 一句 + 紧接着发这一条"。会员与否则只决定**发哪一跳**，
     * 不再决定**能不能点** —— 上一版在这里回"要会员"并且连条件都不改，那一版是对的（那时预览腿还没接）。
     *
     * 回 null = 点得动；回一句 = 调用点**不要改条件**、把这句上屏。拒的时候不改条件是故意的：
     * 改了条件而不发参数，屏上就亮着「人气」而请求里发的是「最新」，那正是本仓零容忍的假开关。
     */
    private fun pixivPopularBlockedNotice(): String? =
        if (!pixivLoggedIn || pixivBearerReady == false) PIXIV_NEEDS_ACCOUNT else null

    /**
     * 「这一轮有筛了却发不出去的条件」那句读数。
     *
     * ⚠️ 判据吃的是**票据**（[pixivBearerReady] / 调用点现取的那一枚），不是 [pixivLoggedIn]：
     * 登录态与票据会不一致（token 过期而刷新失败、刚登录票据还没落地），
     * 那时匿名腿会**静默忽略**人气与收藏 —— 屏上再不说一句，用户读到的就是"点了没生效"
     * （2026-10-18 用户原话）。宁可多念一句，不可静默。
     */
    private fun pixivAccountNotice(bearerReady: Boolean): String? =
        if (!bearerReady && pixivFilter.needsAccount) {
            "$PIXIV_NEEDS_ACCOUNT —— 这一轮按其余条件搜"
        } else {
            null
        }

    /**
     * "站方给了行但没给图"那句读数；没有跳过就 null。
     *
     * ⚠️ 这里**刻意只说到"站方没给图"为止**，不再去归因（从前会断言"是受管制标签封的、
     * 要 Gold 才行"）。那条断言是 Danbooru 独有的站方规则，Gelbooru 没有对应机制
     * （四个分级 `general`/`sensitive`/`questionable`/`explicit` 都正常可看）。
     * 换站之后**留着旧站的归因**就成了编造的诊断 —— 而这条读数的作用是
     * "让屏上少掉的张数有个交代"，不是"解释站方为什么这么做"。所以：
     * 说清跳了几张、并指出这多半是删除或审核中（唯一可由本站数据支持的解释），
     * 把"到底是什么原因"留给用户自己去站上看。宁可少说，不可说错。
     */
    fun noImageNotice(): String? = when {
        droppedNoImage == 0 -> null
        else -> "跳过 $droppedNoImage 张（站方给了条目但没给可用的图，多半已被删除或还在审核）"
    }

    /**
     * 「这一轮有哪些腿没跑起来」那句读数；全都跑起来了就 null。
     *
     * 与 [noImageNotice] 是同一类东西：屏上少了一个站的货必须有个交代。
     * 未配账号与超时是两种原因，各按腿念出来，不合并成"部分来源获取失败"那种说不出话的串。
     */
    fun legFailureNotice(): String? = legFailures
        .takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString("；") { (site, reason) ->
            // 「这一站还没配置」那句自带站名（它是 domain 里唯一一份文案），再前缀一次就成了
            // "Gelbooru：Gelbooru 需要…"。其余原因（超时、站方 500）都没写站名，才需要补上。
            if (reason.startsWith(site.displayName)) reason else "${site.displayName}：$reason"
        }

    /**
     * 「没有以它开头的标签」那行的主语 —— 说的是**真的问过的那几条腿**。
     *
     * 不能照来源档直说「两个站都没有」：默认「全部」档在没配 Gelbooru 账号时只问到了 yande.re，
     * 那句"两个站都没有"就是把**我们自己的闸**说成**站方的答案**（同一条错误在补全那笔里已经避过一次）。
     */
    fun suggestionMissCopy(term: String): String {
        val legs = source.availableLegs(gelbooruConfigured)
        return if (legs.size > 1) "两个站都没有以「$term」开头的标签"
        else "${legs.firstOrNull()?.displayName ?: source.label} 没有以「$term」开头的标签"
    }

    /**
     * 公开版：这一轮"条件被砍"的读数（页面在**收成一条**之后也要能读到它 ——
     * 那时展开区已经不画了，提示行也一起没了，页尾必须自己说明白）。
     */
    fun budgetTrimNotice(): String? = trimmedNotice(effectiveFilters())

    /**
     * "这一站只发得出去前几枚"那句读数；全都发得出去就 null。
     *
     * 必须说出来：胶囊是用户**看得见**的东西，而实际发出去的可能少几枚
     * （换站之后预算从"不限"降到 2 就是这么发生的）。不说清就会变成
     * "我明明选了 3 枚，结果怎么像只有 2 枚"。所以把没发出去的那几枚一并念出来。
     */
    private fun trimmedNotice(sent: List<GalleryTagFilter>): String? {
        val dropped = filters.drop(sent.size)
        if (dropped.isEmpty()) return null
        return "换到 ${site.displayName} 后这一站一次最多发 ${sent.size} 枚，" +
            "本次只发了 ${sent.joinToString("、") { it.token }}；" +
            "没发出去的是 ${dropped.joinToString("、") { it.token }}（删掉多余的，或换个站再搜）。" +
            GallerySearch.budgetNotice(site).orEmpty()
    }

    companion object {
        /**
         * Gelbooru 没配账号时那句话。文本的唯一来源在 `GallerySearchSource.NEEDS_ACCOUNT_REASON`
         * （判据与读数是同一件事，两处各写一份迟早分叉），这里只留别名给页面比对用 ——
         * 空态那一档是靠"错误串是不是这一句"来分"这一站还不能用"与"这一轮没搜成"的。
         */
        const val NEEDS_GELBOORU_ACCOUNT: String = GallerySearchSource.NEEDS_ACCOUNT_REASON

        /**
         * pixiv 搜索那一跳**每页恒 60 条**（2026-10-17 现量：`body.illustManga.data` 长度 60，
         * 只有最后一页不满）。这一格同时是"到底"判据：回不满一页 = 到底。
         *
         * 不放在 [GallerySite] 上：那一颗是路由与展示面（站名、地址、字母标），
         * 页宽是**取数腿**的事，而 pixiv 这条腿只有搜索这一个调用点。
         */
        const val PIXIV_PAGE_SIZE: Int = 60

        /**
         * 一页被**本地筛**空之后，同一笔里最多再往前要几页。
         *
         * 作品类型（仅动图 / 排除动图）与比例只能本地筛（站方没有那枚参数 / app 腿没有 `ratio`），
         * 而 60 条里通常只有一两条动图 —— 「仅动图」那一档第一页很可能一张不剩。
         * 一页空**不等于到底**，所以补几页；但上限是硬的：靠"一直翻到有货"在冷门词上
         * 就是几十趟请求，用户等的是图不是这一笔的往返次数。
         */
        const val PIXIV_EMPTY_PAGE_HOPS: Int = 3
    }
}

/**
 * 「人气这一档要登录」那一句话（点的时候说一次、发车时也说一次）。
 *
 * 写成常量而不是两处各写一遍：这两处说的是同一件事，两串字迟早会分叉，
 * 而用户会以为是两种不同的问题。
 *
 * ⚠️ 只剩「人气」这一枚用它：收藏那排的门槛早就是词尾标签（匿名实测生效），不再要求登录。
 */
/** 这一族的日志统一走这个 tag（与首页那几块同一条口径）。 */
private const val PIXIV_SEARCH_TAG = "VeneraDebug"

/**
 * pixiv 搜索两栏失败时上屏的那句话。
 *
 * 不把 `Throwable.message` 直接摆上去：那一串里可能带着站方正文片段
 * （现量一例是整页 `<!DOCTYPE html>`），读不懂也会把空态撑坏。技术异常走
 * `Log.w(PIXIV_SEARCH_TAG, …, t)`，屏上只留这一句。
 */
private const val PIXIV_SEARCH_FAILED = "pixiv 这一回没答上，稍后再试。"

private const val PIXIV_NEEDS_ACCOUNT: String = "人气这一档要登录 pixiv 账号才排得动"

/**
 * 「这一档走的是非会员的人气预览」那一句话（2026-10-19 · 批 233-4）。
 *
 * 为什么必须占一枚常量而不许省：这一跳的调用面**只有一个词**，面板里那几枚条件一枚都没发出去
 * （会员那条腿才发得动）。不说的话，屏上就是"日期窗亮着、请求里根本没有它"——
 * 本仓对假开关那族的判据正是这一句（另一例见 [PIXIV_NEEDS_ACCOUNT]）。
 *
 * 比例与作品类型**不在被丢的那几枚里**：那两枚一直是本地筛，这一页照样筛（见 [PixivSearchFilter.keeps]），
 * 所以句子里不提它们，免得读成"这两枚也没生效"。
 */
private const val PIXIV_PREVIEW_NOTICE: String =
    "这一档走的是非会员的人气预览：只有这一页，日期窗、收藏门槛与排除 AI 都没发出去，标签模式按部分匹配"

/**
 * 这一条历史属于哪个内容域。
 *
 * 判据只有一处：pixiv 的条目里 `sites` 只有 pixiv（它不进来源轴，见 `GallerySearchSource`），
 * 而 booru 那几条永远不含它。所以"含不含 PIXIV"就是域，**不需要**在历史里另存一格 ——
 * 那一格一旦加上去，旧数据全是空的，而空值会被读成"哪一域都不是"，历史就整片消失。
 */
private fun GallerySearchEntry.belongsTo(domain: GalleryHomeDomain): Boolean =
    if (domain == GalleryHomeDomain.PIXIV) {
        GallerySite.PIXIV in sites
    } else {
        GallerySite.PIXIV !in sites
    }

/**
 * 一条腿最终交回的东西：要上屏的那批图，加上**这一次换名**说的那句话（没换就是 null）。
 *
 * 为什么不把这句话塞进 [GalleryLegOutcome]：那份是三条链路（日榜、猜你喜欢、搜索）共用的
 * "一条腿的取数结果"，换名这件事只有搜索这一条链路有。塞进去会让日榜那头也带一个恒为 null 的格子。
 *
 * 为什么要**带着结果一起返回**而不是在腿里直接写状态：多条腿并发，谁先落地不定，
 * 从协程里写共享状态会把上一条的读数盖掉；而落地前那道"认轮次"的闸要把**过期那条腿的话**一起丢，
 * 只有把话绑在结果上才丢得干净。
 */
private class GalleryLegAttempt(
    val outcome: GalleryLegOutcome,
    val aliasNotice: String?,
)

/**
 * 一条腿「发什么」的结论。
 *
 * 不用 `String?` 是因为屏上要说得出**为什么不发**（[GelbooruWindowBreak] 那一步 + 站方原话），
 * 而 null 装不下这句话。与 [GalleryLegAttempt] 同一纪律：结论**跟着返回值走**，不写共享状态 ——
 * 多条腿并发时从协程里写一格"上一次为什么失败"会把别人的话盖掉。
 */
private sealed interface LegQuery {
    data class Ready(val query: String) : LegQuery

    /**
     * @param at null = 不是 Gelbooru 合成档那一路（这一档压根要不到窗口边界）。
     * @param atEnd 断在**上界**那一枚上（翻历史期才有第二枚）。屏上那一句得说清是起是止 ——
     *   两枚的排查出路不同：起点定不出是探针/凭据的问题，上界定不出多半是那段日子站方没货。
     */
    data class Skip(
        val at: GelbooruWindowBreak?,
        val detail: String?,
        val atEnd: Boolean,
    ) : LegQuery
}
