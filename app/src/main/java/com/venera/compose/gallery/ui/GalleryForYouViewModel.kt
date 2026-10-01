package com.venera.compose.gallery.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.gallery.data.GalleryFavoritesStore
import com.venera.compose.gallery.data.GalleryForYouCache
import com.venera.compose.gallery.data.GalleryForYouSnapshot
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GelbooruAccount
import com.venera.compose.gallery.data.GelbooruClient
import com.venera.compose.gallery.data.YandeReClient
import com.venera.compose.gallery.data.bySite
import com.venera.compose.gallery.data.countBySite
import com.venera.compose.gallery.data.toCountRows
import com.venera.compose.gallery.data.toFavorite
import com.venera.compose.gallery.data.toPost
import com.venera.compose.gallery.data.toSiteRows
import com.venera.compose.gallery.domain.GalleryFeedSource
import com.venera.compose.gallery.domain.GalleryForYouMerge
import com.venera.compose.gallery.domain.GalleryForYouRefreshPolicy
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GalleryRecommendation
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GallerySearchMerge
import com.venera.compose.gallery.domain.GalleryTagFilter
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 猜你喜欢那一屏的状态持有者。
 *
 * ## 为什么不复用 [GallerySearchViewModel]
 *
 * 这是最自然的第一反应，也是错的，两条实测理由（`GallerySearchViewModel` 读了全文才看出来）：
 *
 * 1. 它是**单站引擎** —— 一个 `site` 字段配一份 `filters`，而这一屏要两站各抽一份标签、
 *    各取一页再混摆。套它就得开两个实例然后在外面再合一次，而那"再合一次"正是
 *    [GalleryForYouMerge]，等于绕一圈回到原点。
 * 2. 它每次第 1 页成功都写**搜索历史**（`GallerySearchViewModel.kt:519` 的 `saveHistory`）。
 *    机器生成的标签串每轮都变，灌进去会把用户自己敲的那几行「最近搜索」挤没 ——
 *    搜索卡里最上面那一区是用户自己的入口，不该被自动产生的串占掉。
 *
 * 所以这里只依赖**纯/域层**（[GalleryRecommendation] 的产物、[GallerySearch.queryOf]、
 * [GalleryForYouMerge]）与两个 client 现成的 `searchPosts`。一条新的取数链路都没有。
 *
 * ## 为什么单独一个 ViewModel（而不是就地 remember，也不是并进 [GalleryViewModel]）
 *
 * 与日榜那头一模一样的理由：本项目导航条目被下一页覆盖时**组合会销毁**，返回时从零重建 ——
 * 裸 remember 会让"点进大图再返回"把这一屏整片丢掉。
 * 而**不并进日榜那份**是因为语义不合：`GalleryViewModel` 的字段是"整片替换的一轮、没有下一页"，
 * 这一屏是分页、双源、可单站缺席。混在一起就是两套状态机互相清（搜索层当年分出去也是这一条）。
 */
class GalleryForYouViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Context = application

    /**
     * 本轮抽样与打乱用的种子。**只在这里生成**。
     *
     * 日榜那份种子仍归 [GalleryViewModel.seed]，两页**不联动**（方案 §四.2）：
     * 在推荐页按"换一批"却把没在看的那屏日榜也换掉，是最容易被读成 bug 的联动。
     *
     * ⚠️ 但搜索卡里那行「根据你的收藏」chips 改用**这一颗**（`GalleryScreen` 那侧）：
     * chips 与这一屏是同一次抽样，不同源就会出现"卡片上写着 A 串、点进去那面墙是 B 串"。
     */
    var seed by mutableLongStateOf(System.currentTimeMillis())
        internal set

    /** 手动「换一批」的计数，参与 [loadedKey] 的比对口径 —— 没有它刷新就是假按钮。 */
    var refreshTick by mutableIntStateOf(0)
        internal set

    /** 「这一轮已经成功取过」的凭证，**只在成功后写**（组合重建时命中它，返回才不重拉）。 */
    var loadedKey by mutableStateOf<String?>(null)
        internal set

    /**
     * 这一屏处于哪一档。**只有 [GalleryForYouStage.READY] 才发请求**。
     *
     * `NO_SEEDS` 与 `NO_USABLE_TAGS` 必须分开：前者要引导用户去收藏，后者是我们的缺陷
     * （标签全被黑名单吃掉了）。把后者说成"快去收藏"就是拿"用户没干活"替自己的判据遮丑。
     */
    var stage by mutableStateOf(GalleryForYouStage.IDLE)
        internal set

    /** 这一轮**实际发出去**的标签串（按站一份）。页尾念它，续页也照它发 —— 不重新抽。 */
    var queryBySite by mutableStateOf<Map<GallerySite, String>>(emptyMap())
        internal set

    var posts by mutableStateOf<List<GalleryPost>>(emptyList())
        internal set

    /** 各站累计进屏的张数（页尾那句"yande.re X · Gelbooru Y"读它）。 */
    var perSite by mutableStateOf<Map<GallerySite, Int>>(emptyMap())
        internal set

    /** 因"已经在收藏里"被剔掉的**累计**张数 —— 页尾那句"已排除 N 张你收藏过的图"读它。 */
    var excludedFavourite by mutableIntStateOf(0)
        internal set

    var videos by mutableIntStateOf(0)
        internal set

    /** 已经取到第几页（0 = 还没取过）。 */
    var page by mutableIntStateOf(0)
        internal set

    var isLoading by mutableStateOf(false)
        internal set

    var isLoadingMore by mutableStateOf(false)
        internal set

    /** 第 1 页失败：屏上什么都没有，这句话该占整屏。 */
    var error by mutableStateOf<String?>(null)
        internal set

    /** **续页**那一笔失败。与 [error] 分开是刻意的：那时屏上已有几十张图，不该换成错误页。 */
    var loadMoreError by mutableStateOf<String?>(null)
        internal set

    /**
     * 这一轮**没给上内容**的站及原因（含"这一站还没收藏"与"这一站还不能用"）。
     *
     * 与 [error] 不是一回事：一站缺席时另一站照旧出图，屏上不该整片换成错误页 ——
     * 但"双源混摆"静默退化成单源是本屏最难被发现的一种错，所以它必须占一条**可见**的位置
     * （同 `GalleryFeedSource` 那头 `sourceNotice` 的理由）。
     */
    var failures by mutableStateOf<Map<GallerySite, String>>(emptyMap())
        internal set

    /**
     * 到底了没 —— 判据是"**这一轮发得出去的站都到底**"，不是"两站都到底"。
     *
     * 后者的写法会把没抽到标签、或还没配账号的那站算成"永远没到底"，于是用户每次滑到底
     * 都再发一笔**注定只剩一站应答**的请求，看着像"到底了却还在转"。
     */
    var exhausted by mutableStateOf(false)
        internal set

    /** 第 1 页成功落地的次数 —— 页面拿它当"该把网格滚回顶部"的信号（拿 size 当信号两轮条数相同就观察不到）。 */
    var generation by mutableIntStateOf(0)
        internal set

    // ── 「沿用上次那一屏」的两个标记（批次 O）──
    //
    // 与 GalleryViewModel 那两处同名同义，连"为什么要分开记"也一样：
    // loadedKey 只在成功后写（从错误态点重试要靠它为空才放行），
    // 而缓存那一屏谈不上"成功"——把它写进 loadedKey 会连带把「重试」变成假按钮。

    /** 屏上这一屏是从快照铺出来的、本轮还没真取过。 */
    private var hydratedFromCache = false

    /** 快照写于哪一天（epochDay）；null = 没有快照。 */
    private var cachedOnEpochDay: Long? = null

    /**
     * 页面问"这一轮要不要自己去联网取"。
     *
     * 与日榜那头同一条口径（判据在 [GalleryForYouRefreshPolicy.shouldAutoLoad]）：
     * 铺着今天的快照就不取；跨天了补拉；用户点过「换一批」后两个标记一起清，那一轮必取。
     */
    fun shouldAutoLoad(todayEpochDay: Long = LocalDate.now(ZoneOffset.UTC).toEpochDay()): Boolean =
        GalleryForYouRefreshPolicy.shouldAutoLoad(hydratedFromCache, cachedOnEpochDay, todayEpochDay)

    private val gelbooruAccount = GelbooruAccount.getInstance(app)

    /** 订阅而不是读一次快照：设置页配好或注销之后，退回画廊这边要**立刻**跟着变。 */
    var gelbooruConfigured by mutableStateOf(gelbooruAccount.identity.value != null)
        internal set

    init {
        viewModelScope.launch {
            gelbooruAccount.identity.collect { gelbooruConfigured = it != null }
        }
    }

    /** 这一站一次要多少条（也是"到底"判据里的那个 requestedLimit）。 */
    fun limitOf(site: GallerySite): Int = when (site) {
        GallerySite.GELBOORU -> GelbooruClient.POOL_SIZE
        GallerySite.YANDERE -> YandeReClient.SEARCH_PAGE_SIZE
    }

    /** 前面各页已上屏条目的去重键（跨页去重全靠它，`mix` 那头没有这一维）。 */
    private var seenKeys = HashSet<String>()

    /** 已经到底的站。缺席（超时/没账号）的站**不进**这里 —— 见 [GallerySearchMerge.exhaustedSites]。 */
    private var sitesDone = mutableSetOf<GallerySite>()

    // 必须排在 seenKeys / sitesDone **之后**：init 块按声明顺序跑，写在它们上面就是
    // "在声明之前初始化"，编译不过（这不是风格问题，是在这个类里放错位置就必然报错）。
    init {
        // 同步读：下面那次"这一轮要不要联网"的判断要用得到结果，异步读会抢不过（理由见 GalleryForYouCache.read）。
        val snapshot = GalleryForYouCache.read(app)
        val restored = snapshot?.posts.orEmpty().mapNotNull { fav -> fav.site?.let { fav.toPost(it) } }
        if (snapshot != null && GalleryForYouRefreshPolicy.canHydrate(restored.size, snapshot.seed)) {
            seed = snapshot.seed
            posts = restored
            queryBySite = snapshot.queries.bySite()
            perSite = snapshot.perSite.countBySite()
            page = snapshot.page
            excludedFavourite = snapshot.excludedFavourite
            videos = snapshot.videos
            failures = snapshot.failures.bySite()
            sitesDone = snapshot.sitesDone.toMutableSet()
            exhausted = queryBySite.isNotEmpty() && sitesDone.containsAll(queryBySite.keys)
            // 跨页去重的键**不存**，从恢复出来的那一屏重算：它就是从这些 post 累出来的，
            // 存第二份只会给"两份不一致"留位置（存了旧键、屏上换了新条，去重就会漏剔或误剔）。
            seenKeys = HashSet<String>().apply { posts.forEach { this += GalleryForYouMerge.keysOf(it) } }
            // stage 也要铺：否则页面第一帧念的是 IDLE，那一档摆的是整屏加载环 ——
            // 快照就白存了。判据与 load() 那句一致：发得出标签串才算 READY。
            if (queryBySite.isNotEmpty()) stage = GalleryForYouStage.READY
            hydratedFromCache = true
            cachedOnEpochDay = snapshot.savedEpochDay
        }
    }

    private var loadJob: Job? = null
    private var moreJob: Job? = null

    /**
     * 取第 1 页。
     *
     * @param force true = 用户主动要求的那一轮（「换一批」「重试」），**忽略在途标志**。
     *   在途标志因某种残留卡在 true 时，显式重试也必须真发请求 —— "重试"按下去毫无行为，
     *   是最坏的一种假按钮（日榜那头同一病，全过程记在 `GalleryScreen.kt:204-221`）。
     *
     * ⚠️ 取消路径也要清标志：`try/finally` + `NonCancellable`。这笔协程虽然住在
     * `viewModelScope`，但页面那侧的观察点随组合销毁而取消时，标志不清就会永久转圈。
     */
    fun load(recommendations: Map<GallerySite, GalleryRecommendation>, force: Boolean = false) {
        if (isLoading && !force) return
        val nextStage = stageOf(recommendations)
        stage = nextStage
        if (nextStage != GalleryForYouStage.READY) {
            // 空态不发请求，也不假装在取：那样屏上会是一枚永远转不完的环。
            loadJob?.cancel()
            moreJob?.cancel()
            isLoading = false
            isLoadingMore = false
            posts = emptyList()
            error = null
            failures = emptyMap()
            queryBySite = emptyMap()
            return
        }

        val queries = queriesOf(recommendations)
        if (force) {
            loadJob?.cancel()
            moreJob?.cancel()
        }
        error = null
        loadMoreError = null
        failures = emptyMap()
        queryBySite = queries
        isLoading = true
        // 第 1 页 = 新的一轮：跨页去重的键与"哪些站到底了"都从头来。
        seenKeys = HashSet()
        sitesDone = mutableSetOf()
        val round = seed
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            // 先抓住"这一笔自身的 Job"再进 finally：`withContext(NonCancellable)` 会把
            // `coroutineContext[Job]` 换成 `NonCancellable` **本身**，在块里现读现比就是
            // 拿一个恒不相等的东西比 → 标志永远清不掉。2026-09-29 那三条真机读数
            // （下拉环转不停 / 同时两枚加载图标 / 墙上只摆 40 张）同一根因，
            // 库行为钉在 `NonCancellableJobIdentityTest`。
            val self = coroutineContext[Job]
            try {
                val outcome = fetchPage(nextPage = 1, round = round, queries = queries)
                applyPage(outcome, nextPage = 1, round = round)
            } finally {
                withContext(NonCancellable) {
                    if (loadJob === self) isLoading = false
                }
            }
        }
    }

    /**
     * 续页。四道闸门：不在 READY、已到底、正在取、上一笔续页刚失败（那要等用户点「重试」）。
     *
     * 最后那道是必须的：自动重试会让 `isLoadingMore` 一落回 false 就再发同一笔，
     * 对着一个已知失败的请求无限循环（搜索页那头同一处踩过）。
     */
    fun loadMore() {
        if (stage != GalleryForYouStage.READY) return
        if (exhausted || isLoading || isLoadingMore) return
        if (loadMoreError != null) return
        val queries = queryBySite
        if (queries.isEmpty()) return
        val nextPage = page + 1
        isLoadingMore = true
        loadMoreError = null
        val round = seed
        moreJob = viewModelScope.launch(Dispatchers.IO) {
            // 同一处坑、同一修法，理由见上面 load() 里那段。
            val self = coroutineContext[Job]
            try {
                applyPage(fetchPage(nextPage, round, queries), nextPage = nextPage, round = round)
            } finally {
                withContext(NonCancellable) {
                    if (moreJob === self) isLoadingMore = false
                }
            }
        }
    }

    /** 续页失败后页尾那枚「重试」。 */
    fun retryLoadMore() {
        loadMoreError = null
        loadMore()
    }

    /**
     * 用户主动「换一批」。
     *
     * 换种子 = **重抽标签** + 重排两站节奏，所以这一按确实换东西（只换顺序不换口味是没用的）。
     * 熔断那两站要不要连带清，见 [com.venera.compose.gallery.ui.GalleryScreen] 里调用它的那处
     * —— 清的动作留在页面那侧（与日榜那头 `resetBreakers()` 同一处口径），本类不越权碰网络层状态。
     *
     * ## ⚠️ 这里**刻意不清 [posts]**（2026-09-30 第三轮）
     *
     * 用户原话「换一批不要整个页面刷新，只刷新下面推荐的内容」。旧写法把 `posts` 清空 + `stage`
     * 打回 IDLE，页面那一档（`stage == IDLE || (isLoading && posts.isEmpty())`）就命中，
     * **整屏**换成一个居中波浪环 —— 首页三节（关注画师 / 每日热门 / 猜你喜欢节头）跟着一起消失，
     * 读起来就是"整页刷新"。留着上一批，页面才会走网格那一档，三节与旧卡片都在屏上，
     * 新第一批到货时由 [applyPage] 的 `nextPage == 1` 那一支**整批替换**（不是追加）。
     *
     * 其余字段照旧清，其中三个是"不清就会念出假读数"的：
     * - [perSite] / [excludedFavourite] 是**累加**的（`applyPage` 里 `+=`），不清就会把
     *   上一批的张数算进新一批，页尾念出「Yande.re 80」而真实是 40；
     * - [seenKeys] 不清，新一批会被当成"已经摆过"整批剔空 —— 那是"换了却啥也没变"。
     */
    fun refresh() {
        refreshTick++
        seed = System.currentTimeMillis()
        loadJob?.cancel()
        moreJob?.cancel()
        isLoading = false
        isLoadingMore = false
        perSite = emptyMap()
        excludedFavourite = 0
        videos = 0
        page = 0
        exhausted = false
        error = null
        loadMoreError = null
        failures = emptyMap()
        queryBySite = emptyMap()
        loadedKey = null
        stage = GalleryForYouStage.IDLE
        seenKeys = HashSet()
        sitesDone = mutableSetOf()
        // 用户明确要"换一批"：快照那一屏当场作废。不清这两个，同日门会把这一笔拦成假按钮
        // （与 GalleryViewModel.refresh() 那两处同一对）。
        hydratedFromCache = false
        cachedOnEpochDay = null
    }

    /**
     * 标签集 → 每站实际发出去的那一串。
     *
     * 走 [GallerySearch.queryOf]（不自己 `joinToString`）：join 的口径全仓只该有一处，
     * 否则页尾念的那串和真发出去的那串会长得不一样。
     */
    private fun queriesOf(recommendations: Map<GallerySite, GalleryRecommendation>): Map<GallerySite, String> =
        GallerySite.entries.mapNotNull { site ->
            val tags = (recommendations[site] as? GalleryRecommendation.Tags)?.tags ?: return@mapNotNull null
            site to GallerySearch.queryOf(tags.map { GalleryTagFilter(it) })
        }.toMap()

    /**
     * 一屏处于哪一档。
     *
     * **只要有一站抽得出标签就是 READY** —— 另一站没收藏是"这一屏少一站"，
     * 由 [failures] 那条可见位置去说，不该把整屏报成"你还没收藏"。
     * 两站都算不出时，`NoUsableTags`（有收藏但标签被挡完）优先于 `NoSeeds`：
     * 前者是我们的缺陷，后者只是用户还没干活，把混在一起说就是替自己遮丑。
     */
    private fun stageOf(recommendations: Map<GallerySite, GalleryRecommendation>): GalleryForYouStage {
        if (recommendations.values.any { it is GalleryRecommendation.Tags }) return GalleryForYouStage.READY
        return if (recommendations.values.any { it is GalleryRecommendation.NoUsableTags }) {
            GalleryForYouStage.NO_USABLE_TAGS
        } else {
            GalleryForYouStage.NO_SEEDS
        }
    }

    /** 一笔取数的三样结果：各站返回了几条、各站为什么没给、混好的那一页。 */
    private data class PageOutcome(
        val returnedBySite: Map<GallerySite, Int>,
        val reasons: Map<GallerySite, String>,
        val merged: Result<GalleryForYouMerge.Paged>,
    )

    /**
     * 两站**并发**取这一页，每站各自一笔时间预算。
     *
     * 预算复用 [GalleryFeedSource.PER_SITE_TIMEOUT_MS]（12s：本机实测正常态 TTFB 1.1~3.8s 的 3~4 倍），
     * 包装那份在 [GalleryLegGuard]（日榜、搜索两腿与这一处共用一份，不再各写一遍）。
     * 同一个理由：不加预算的话单站最坏能拖到 40s 级，而 UI 只有"整屏空等"一种表达 ——
     * 用户既看不出是哪一站慢，也拿不到快的那一站。
     *
     * 已经到底的站不再问（[sitesDone]）；这一站这一轮没标签的也不问，但**不**记进 `returnedBySite` ——
     * 缺席不等于到底，收藏之后下一轮就该重新问它。
     */
    private suspend fun fetchPage(
        nextPage: Int,
        round: Long,
        queries: Map<GallerySite, String>,
    ): PageOutcome {
        val favourites = GalleryFavoritesStore.getInstance(app).favorites.value.mapTo(HashSet()) { it.uid }
        // 每笔只**返回**自己的结果，不在 async 里写共享容器：这几路跑在 Dispatchers.IO 的不同线程上，
        // 并发写一个 HashMap 是竞态（`GalleryFeedSource` 那头也是 `async` 出值、外面合并）。
        val legs = coroutineScope {
            GallerySite.entries.filterNot { it in sitesDone }.map { site ->
                async { leg(site, nextPage, queries[site]) }
            }.awaitAll()
        }
        val pools = HashMap<GallerySite, List<GalleryPost>>()
        val reasons = HashMap<GallerySite, String>()
        val returned = HashMap<GallerySite, Int>()
        legs.forEach { leg ->
            leg.reason?.let { reasons[leg.site] = it }
            if (leg.returned != null) {
                returned[leg.site] = leg.returned
                pools[leg.site] = leg.posts
            }
        }

        val merged = GalleryForYouMerge.page(
            pools = pools,
            seed = round,
            page = nextPage,
            seenKeys = seenKeys,
            favouriteUids = favourites,
        )
        return PageOutcome(returned, reasons, merged)
    }

    /** 一站这一页的结果。[returned] 为 null = **这一轮它没应答**（没标签 / 没账号 / 超时），缺席不算到底。 */
    private data class Leg(
        val site: GallerySite,
        val posts: List<GalleryPost> = emptyList(),
        val returned: Int? = null,
        val reason: String? = null,
    )

    private suspend fun leg(site: GallerySite, nextPage: Int, query: String?): Leg {
        if (query == null) {
            return Leg(site, reason = "${site.displayName}这一站还没有能推荐的标签")
        }
        if (site == GallerySite.GELBOORU && !gelbooruConfigured) {
            // 发车前就拦住：它的 DAPI 匿名一律 401，发出去只是让用户等一轮网络再读一句凭据错误 ——
            // 而他该做的是去配置页填那两个字段。
            return Leg(site, reason = GallerySearchViewModel.NEEDS_GELBOORU_ACCOUNT)
        }
        val outcome = GalleryLegGuard.guard(site) {
            when (site) {
                GallerySite.GELBOORU -> GelbooruClient.getInstance(app).searchPosts(query, nextPage, limitOf(site))
                GallerySite.YANDERE -> YandeReClient.getInstance(app).searchPosts(query, nextPage, limitOf(site))
            }
        }
        // "空"有两种：站方真给了 0 条，与请求本身失败（401 / 解析不出来）。
        // 吞成一句笼统的"没有内容"就把后者说成了前者 —— 更糟的是 `returned`：
        // 只有**答上了**才记张数。失败也记成"回了 0 条"，`exhaustedSites` 那把判据就会
        // 把那一站当成到底、从此这轮不再问它，而页尾写着「已经到底」
        // （2026-09-30 收口三处重复实现时在这一份里查到的真缺陷）。
        return Leg(
            site,
            posts = outcome.posts,
            returned = outcome.posts.size.takeIf { outcome.answered },
            reason = outcome.reason,
        )
    }

    /**
     * 把一页落地。
     *
     * `round != seed` 整笔作废：中途换过一批的话，这一笔的标签串与打乱流都属于上一轮，
     * 落地就会出现"卡片写着新串、图来自旧串"（搜索那头拿站比对防的是同一类脏落地）。
     */
    private fun applyPage(outcome: PageOutcome, nextPage: Int, round: Long) {
        if (round != seed) return
        outcome.merged.onSuccess { paged ->
            error = null
            loadMoreError = null
            posts = if (nextPage == 1) paged.posts else posts + paged.posts
            perSite = GallerySite.entries.associateWith { site ->
                (perSite[site] ?: 0) + (paged.perSite[site] ?: 0)
            }
            excludedFavourite += paged.excludedFavourite
            videos = posts.count { it.isVideo }
            paged.posts.forEach { post -> seenKeys += GalleryForYouMerge.keysOf(post) }
            sitesDone += GallerySearchMerge.exhaustedSites(outcome.returnedBySite, ::limitOf)
            exhausted = queryBySite.isNotEmpty() && sitesDone.containsAll(queryBySite.keys)
            failures = outcome.reasons
            page = nextPage
            if (nextPage == 1) {
                generation++
                // 凭证写在这里（成功后），所以从错误态点重试仍会真发请求。
                loadedKey = "foryou#$refreshTick|$round"
            }
            // 每一页成功都刷一次快照（不是只在第 1 页）：只存第一页的话，
            // 用户翻到第 4 页再切回来，"沿用上次那一屏"会退回到第 1 页那一屏 ——
            // 页码与"哪些站到底了"是这一屏状态的一部分，不存就等于没接上同日门。
            persistSnapshot()
        }.onFailure { e ->
            val message = e.message ?: "这一轮没取成"
            if (nextPage == 1) error = message else loadMoreError = message
        }
    }

    /**
     * 落一份当前这一屏。
     *
     * **空屏不存**（与日榜那头同一句）：错误态与"还没抽到标签"存进去，
     * 下次切回来铺的就是一屏空 —— 而 `canHydrate` 那道闸此时会关掉门，永远不再取。
     * 存的是"取到的那一屏"，不是"最后一次状态"。
     */
    private fun persistSnapshot() {
        if (posts.isEmpty()) return
        val snapshot = GalleryForYouSnapshot(
            savedEpochDay = LocalDate.now(ZoneOffset.UTC).toEpochDay(),
            seed = seed,
            posts = posts.map { it.toFavorite() },
            queries = queryBySite.toSiteRows(),
            page = page,
            perSite = perSite.toCountRows(),
            excludedFavourite = excludedFavourite,
            videos = videos,
            failures = failures.toSiteRows(),
            sitesDone = sitesDone.toList(),
        )
        viewModelScope.launch { GalleryForYouCache.write(app, snapshot) }
    }
}

/**
 * 猜你喜欢那一屏处于哪一档。
 *
 * 四档分开是因为**页面对用户说的话不一样**（判据层 `GalleryRecommendations` 头注同一条理由）：
 * 合成一个"空列表"就会让"你还没收藏"和"我们的判据算不出东西"长成同一张脸。
 */
enum class GalleryForYouStage {
    /** 还没判过（冷进来第一次组合之前）。 */
    IDLE,

    /** 有标签可发。 */
    READY,

    /** 两站都没有收藏 —— 要引导去收藏。 */
    NO_SEEDS,

    /** 有收藏，但标签全空或全被黑名单滤掉 —— 这是我们的缺陷。 */
    NO_USABLE_TAGS,
}
