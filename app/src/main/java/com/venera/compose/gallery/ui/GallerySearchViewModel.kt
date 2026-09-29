package com.venera.compose.gallery.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.gallery.data.GelbooruAccount
import com.venera.compose.gallery.data.GelbooruClient
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.data.YandeReClient
import com.venera.compose.gallery.data.refineGalleryTagSuggestions
import com.venera.compose.gallery.domain.GalleryContextPlan
import com.venera.compose.gallery.domain.GalleryMerge
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GalleryRankings
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GallerySearchContext
import com.venera.compose.gallery.domain.GallerySearchContextStack
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GalleryTagFilter
import com.venera.compose.gallery.domain.decodeGallerySearchHistory
import com.venera.compose.gallery.domain.encodeGallerySearchHistory
import com.venera.compose.gallery.domain.isStaleContext
import com.venera.compose.gallery.domain.pushGallerySearchHistory
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    /** 一次只搜一个站（用户 2026-09-25 拍板）：两站词表不通、标签预算也不同。 */
    var site by mutableStateOf(GallerySite.GELBOORU)
        internal set

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
        GallerySite.GELBOORU -> GelbooruClient.POOL_SIZE
        GallerySite.YANDERE -> com.venera.compose.gallery.data.YandeReClient.SEARCH_PAGE_SIZE
    }

    private val gelbooruAccount = GelbooruAccount.getInstance(app)

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
    var gelbooruConfigured by mutableStateOf(gelbooruAccount.identity.value != null)
        internal set

    init {
        viewModelScope.launch {
            gelbooruAccount.identity.collect { gelbooruConfigured = it != null }
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
        val budget = GallerySearch.tagBudget(site) ?: return filters
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
    fun setSite(next: GallerySite) = switchSite(next, reSearch = true)

    private fun switchSite(next: GallerySite, reSearch: Boolean) {
        if (next == site) return
        // **在途请求要一并掐掉**：不掐的话，旧站那笔响应落地时会把它的结果 append 到新站的上下文里
        // （chips 是新的、图是旧站的），而且补全也会被旧站词表回填 —— 站名与内容对不上最难被发现。
        site = next
        // 换站后手上这一档在新站**可能压根不存在**（Gelbooru 只有默认与全部两档是真的）。
        // 不落回默认档就会出现：胶囊上写着「周」、发出去的是全站历年高分 —— 假开关最坏的长相。
        if (!GalleryRankings.supports(next, ranking)) {
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
        exhausted = false
        searchError = null
        loadMoreError = null
        droppedNoImage = 0
        // reSearch = false 只有 [applyHistory] 用：它自己会装条件 + 开搜 + 收条，
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
            exhausted = false
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
     */
    fun acceptHandoff(site: GallerySite, tags: List<String>) =
        openContext(site, tags.map { GalleryTagFilter(it) }, thenCollapse = false)

    /**
     * **换一轮搜索上下文**的唯一落点（交接、点历史、点推荐标签行三条入口都走这里）。
     *
     * 顺序是刻意的：先按 [GallerySearchContextStack.plan] 处置上一轮，再清场装新的。
     * 反过来写就会出现"屏上已经是新的一轮、上一轮却没存下来"—— 正是本轮要修的那条缺陷。
     *
     * @param thenCollapse 装完之后收成一条（[GallerySearchMode.RESULTS]）还是保持当前那一面。
     *   点历史要收条（用户 2026-09-26 拍板"点历史直接跑并收成一条"）；交接由调用方那侧的
     *   IME/返回逻辑去收，这里不替用户决定。
     *
     * 装条件之后**直接 [runSearch]**、不绕 [applyFilters]：它有一条 `next == filters` 的短路，
     * 撞上"看起来一样"（连点同一条历史、同一枚标签点两次）就会被短路掉 —— 那正是"点了没反应"
     * 的另一种长相。[GalleryContextPlan.RESEARCH_IN_PLACE] 那一档依赖的正是这件事。
     */
    private fun openContext(nextSite: GallerySite, next: List<GalleryTagFilter>, thenCollapse: Boolean) {
        when (GallerySearchContextStack.plan(site, filters, nextSite, next)) {
            GalleryContextPlan.PUSH -> contexts = GallerySearchContextStack.push(contexts, snapshot())
            // 与当前这一轮逐枚相等：不压栈，只再跑一次。压了就会"白按一次返回"——
            // 那一屏与上一屏一模一样，用户读不出那一下干了什么。
            GalleryContextPlan.RESEARCH_IN_PLACE -> Unit
            // 还没搜过（一枚胶囊都没有）：屏上没有"上一轮"可留，压进去就是一格空壳。
            GalleryContextPlan.SKIP_EMPTY -> Unit
        }
        switchSite(nextSite, reSearch = false)
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

    /** 当前这一屏的可逆快照。只读，不改任何东西。 */
    private fun snapshot() = GallerySearchContext(
        site = site,
        filters = filters,
        results = results,
        page = page,
        exhausted = exhausted,
        droppedNoImage = droppedNoImage,
        pageSize = pageSize,
        ranking = ranking,
        periodAnchor = periodAnchor,
        scrollIndex = currentScrollIndex,
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
        contexts = popped.second
        val ctx = popped.first
        pendingScrollRestore = ctx.scrollIndex
        currentScrollIndex = ctx.scrollIndex
        site = ctx.site
        filters = ctx.filters
        results = ctx.results
        page = ctx.page
        exhausted = ctx.exhausted
        droppedNoImage = ctx.droppedNoImage
        pageSize = ctx.pageSize
        // 档级一起回来：不然弹栈后屏上还是那一墙图，排序却变了，而用户看不出哪里变了。
        ranking = ctx.ranking
        periodAnchor = ctx.periodAnchor
        isSearching = false
        isLoadingMore = false
        searchError = null
        loadMoreError = null
        notice = null
        term = ""
        suggestions = emptyList()
        suggestError = null
        // 一律回"看墙"那一态：用户离开那一轮是去别处看图，回来该看到墙，不是键盘。
        mode = GallerySearchMode.RESULTS
        contextRound++
        searchGeneration++
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
        if (filters.isNotEmpty()) mode = GallerySearchMode.RESULTS
    }

    /**
     * 换排行档：**原地重搜**，不压上下文栈。
     *
     * 理由与「同站同条件不压栈」同一条 —— 排序这件事不是"另一轮搜索"，压栈会让返回
     * 先弹回"同样的条件、只是换了排序"的一屏，白按一次。
     * 但**轮次计数器要 +1**：不加的话，上一档正在飞的续页会落进这一档的墙上
     * （站没变、条件没变，[isStaleContext] 那两道都拦不住，只有轮次拦得住）。
     *
     * [GalleryRankings.supports] 判为"本站没有"的档这里再挡一道：菜单那一侧已经不列这一档，
     * 但入口不止菜单一个（弹栈回来的快照、以后可能加的快捷入口），判据得落在状态层。
     */
    fun setRanking(next: GalleryRanking) {
        if (next == ranking && periodAnchor == null) return
        if (!GalleryRankings.supports(site, next)) return
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
        exhausted = false
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
     * 取一页结果。第 1 页整片替换、后续页追加（[appendPage]）。
     *
     * 放在 ViewModel 而不是页面里，是因为**两个调用方**都要用它：搜索区的同步重查，
     * 以及画廊页那面墙滚到底时的自动续页（墙在 `GalleryScreen`，搜索区在 `GallerySearchArea`）。
     */
    private var searchJob: Job? = null

    fun runSearch(nextPage: Int) {
        if (filters.isEmpty()) {
            notice = "先选至少一个标签（点补全列表里的标签加入）"
            return
        }
        // 第 1 页**可以打断正在跑的那一笔**：同步搜索下用户连选两枚标签，后一次必须盖掉前一次，
        // 否则屏上留下的是上一串标签的结果 —— 看着就像"点了没反应"。
        // 续页仍要挡：同一批结果里不该并着取两页。
        if (nextPage == 1) searchJob?.cancel() else if (isSearching || isLoadingMore) return
        // 站与查询都在发车前**定死**：在途期间用户可能换站或改条件，
        // 落地时用它们比对，对不上就整笔作废（见下面的站比对与 query 的使用处）。
        val siteAtRequest = site
        val roundAtRequest = contextRound
        val rankingAtRequest = ranking
        // 锚点与"今天"都在发车前定死：在途跨了 UTC 零点、或用户中途换了期，这一笔仍按发起那一刻查。
        val anchorAtRequest = periodAnchor
        // 发出去的是**预算内那几枚**，不是胶囊全部（见 [effectiveFilters]）。
        val sent = effectiveFilters()
        // ⚠️ **两串要分开**：`visibleQuery` 是用户看到的那一排（胶囊、历史都走它），
        // `query` 才是发出去的那一串（多一排排行伪标签）。把它们混成一串，
        // 历史里就会存下 `order:score` 这种东西，用户点历史时它被拆回胶囊 ——
        // 那正是 `GalleryRankingTest` 里"胶囊串永远不含伪标签"那条用例防的事。
        val visibleQuery = GallerySearch.queryOf(sent)
        val todayUtc = LocalDate.now(ZoneOffset.UTC)
        val limit = pageSizeFor(siteAtRequest)
        if (nextPage == 1) {
            beginSearch(limit)
            // 提示必须写在 [beginSearch] **之后** —— 它会把 notice 清成 null。
            notice = trimmedNotice(sent)
        } else {
            isLoadingMore = true
            loadMoreError = null
        }
        /*
         * **发车前拦住"这一站现在根本不能用"的情况**（只有 Gelbooru 会这样）。
         *
         * 它的 DAPI 匿名一律 401，所以没配账号时发出去必然拿回一句 401。
         * 那本来也会被下面的 onFailure 翻成人话，但**观感完全不同**：
         * 让用户等一轮网络往返，然后在一个"没有可摆的图"的空态上读一句凭据错误，
         * 他会先怀疑是标签写错了、再怀疑网络。这里直接说清"要去哪里配"，
         * 一次请求都不发。
         *
         * ⚠️ 只在**第 1 页**拦：续页时账号刚被注销的情况交给正常的失败路径，
         * 那时已经有一屏图在，不该因为续页失败把屏上的东西换成错误页。
         */
        if (nextPage == 1 && siteAtRequest == GallerySite.GELBOORU && !gelbooruConfigured) {
            searchError = NEEDS_GELBOORU_ACCOUNT
            isSearching = false
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val query = GalleryRankings.searchQuery(
                    siteAtRequest,
                    sent,
                    rankingAtRequest,
                    anchorAtRequest,
                    todayUtc,
                )
                val result = when (siteAtRequest) {
                    GallerySite.GELBOORU -> GelbooruClient.getInstance(app).searchPosts(query, nextPage, limit)
                    GallerySite.YANDERE -> YandeReClient.getInstance(app).searchPosts(query, nextPage, limit)
                }
                // 站已换 **或** 已经不是发车那一轮：这一笔属于别处，落地就是脏数据
                // （chips 是新的、图是旧站的 / 旧一轮的）—— 整笔丢掉。
                // 为什么光比站点不够：见 [isStaleContext]（返回弹栈那条路上站恰好没变）。
                if (isStaleContext(roundAtRequest, siteAtRequest, contextRound, site)) return@launch
                result.onSuccess { list ->
                    searchError = null
                    loadMoreError = null
                    appendPage(list, nextPage, GallerySearch.isExhausted(list.size, limit))
                    // 历史存的是**用户那一排**（不带排行伪标签），见 [visibleQuery] 上方那段说明。
                    if (nextPage == 1) saveHistory(GallerySearchEntry(siteAtRequest, visibleQuery))
                }.onFailure { e ->
                    val message = e.message ?: "搜索失败"
                    // 第 1 页失败 → 交给整屏空态说；续页失败 → 只在页尾说。
                    // 续页失败不能写成 searchError：那会把屏上已经摆出来的几十张图换成错误页。
                    if (nextPage == 1) searchError = message else loadMoreError = message
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
            val siteAtRequest = site
            try {
                val result = when (siteAtRequest) {
                    GallerySite.GELBOORU -> GelbooruClient.getInstance(app).searchTags(term)
                    GallerySite.YANDERE -> YandeReClient.getInstance(app).searchTags(term)
                }
                // 站已换 → 这笔候选属于上一个站，作废。
                if (siteAtRequest != site) return@launch
                // 复检 + 截断：站方的前缀参数**可能被静默忽略**（少个 s 就回"最新标签"），
                // 所以必须按原始输入再滤一遍，理由见 refineGalleryTagSuggestions。
                result.onSuccess {
                    suggestError = null
                    suggestions = refineGalleryTagSuggestions(term, it, SUGGEST_ROWS)
                }.onFailure { suggestError = it.message ?: "标签补全失败" }
            } finally {
                // 只有"这一笔还是当前那笔"才收尾，被更新的输入取消掉的旧笔不许替新的灭灯。
                if (suggestJob === coroutineContext[Job]) isSuggesting = false
            }
        }
    }

    /** 历史：进 ViewModel 时读一次，成功搜索后写回 SharedPreferences。 */
    private val prefs by lazy {
        app.getSharedPreferences("venera_gallery_search", Context.MODE_PRIVATE)
    }

    fun restoreHistoryIfNeeded() {
        if (history.isNotEmpty()) return
        acceptHistory(decodeGallerySearchHistory(prefs.getString("history", null)))
    }

    private fun saveHistory(entry: GallerySearchEntry) {
        val next = pushGallerySearchHistory(history, entry)
        acceptHistory(next)
        prefs.edit().putString("history", encodeGallerySearchHistory(next)).apply()
    }

    fun clearHistory() {
        acceptHistory(emptyList())
        prefs.edit().remove("history").apply()
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
        prefs.edit().putString("history", encodeGallerySearchHistory(next)).apply()
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
    fun applyHistory(entry: GallerySearchEntry) =
        openContext(entry.site, entry.filters, thenCollapse = true)

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
         * Gelbooru 没配账号时给用户看的那句话。
         *
         * 措辞的三个要点，都不是随便写的：
         * - **说清"完全用不了"而不是"效果差一点"**：它的接口匿名一律 401，
         *   不是少几档权限。写成"登录后体验更好"会让用户以为不配也能凑合用；
         * - **指出在哪儿配**（漫画源管理页顶部那张卡）—— 只说"需要账号"等于把问题
         *   丢回给用户去找入口；
         * - **不说 yande.re 也受影响**：这一站挂了不影响另一站，说清边界，
         *   否则用户会以为整个画廊都瘫了。
         */
        const val NEEDS_GELBOORU_ACCOUNT: String =
            "Gelbooru 需要先配置账号才能用：它的接口对匿名请求一律拒绝（401）。" +
                "到「漫画源管理」页顶部的「画廊站点账号」卡里填 User ID 与 API Key 即可；" +
                "yande.re 不受影响，照常能搜。"
    }
}
