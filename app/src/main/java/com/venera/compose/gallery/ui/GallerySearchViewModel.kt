package com.venera.compose.gallery.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.gallery.data.DanbooruAccount
import com.venera.compose.gallery.data.DanbooruClient
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.data.YandeReClient
import com.venera.compose.gallery.data.refineGalleryTagSuggestions
import com.venera.compose.gallery.domain.GalleryMerge
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GalleryTagFilter
import com.venera.compose.gallery.domain.decodeGallerySearchHistory
import com.venera.compose.gallery.domain.encodeGallerySearchHistory
import com.venera.compose.gallery.domain.pushGallerySearchHistory
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
    var site by mutableStateOf(GallerySite.DANBOORU)
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

    /** 一次性提示（标签预算满了、这一站不限预算之类），由页面在动作发生时写。 */
    var notice by mutableStateOf<String?>(null)

    var history by mutableStateOf<List<GallerySearchEntry>>(emptyList())
        internal set

    /** 这一站一次要多少条（也是"到底"判据里的那个 requestedLimit）。 */
    fun pageSizeFor(site: GallerySite): Int = when (site) {
        GallerySite.DANBOORU -> DanbooruClient.POOL_SIZE
        GallerySite.YANDERE -> com.venera.compose.gallery.data.YandeReClient.SEARCH_PAGE_SIZE
    }

    private val danbooruAccount = DanbooruAccount.getInstance(app)

    /**
     * Danbooru 账号等级；null = 未登录。
     *
     * 两处读数挂在它上面：**标签预算**（[effectiveFilters]）与
     * **"这批受管制标签的图看不到"**（[noImageNotice] / [censoredQueryNotice]）——
     * 后者的门槛是 Gold(30)，与"6 枚标签"同一档。
     * 订阅 [DanbooruAccount.identity]（StateFlow）而不是读一次快照：设置页登录完
     * 或校准完等级，退回画廊这边要**立刻**跟着变，而不是等重启。
     */
    var danbooruLevel by mutableStateOf(danbooruAccount.identity.value?.level)
        internal set

    init {
        viewModelScope.launch {
            danbooruAccount.identity.collect { danbooruLevel = it?.level }
        }
    }

    /**
     * 本次真正要发出去的条件 —— **预算内的前 N 枚**。
     *
     * 与 [filters] 分开是有意的：胶囊是"我要搜什么"（用户自己攒的，一条都不能替他丢），
     * 而这一份是"这一站这次能问什么"。Danbooru 匿名 / Member 只有 2 枚，yande.re 不限，
     * 所以同一排胶囊换站之后能不能全发出去是**不一样的**，绝不能拿一个数去蒙两站。
     */
    fun effectiveFilters(): List<GalleryTagFilter> {
        val budget = GallerySearch.tagBudget(site, danbooruLevel) ?: return filters
        return filters.take(budget)
    }

    /**
     * 换站。
     *
     * **标签留着**（用户 2026-09-26 改判）：两站词表不通这条仍然成立（Danbooru 的
     * `1girl` / yande.re 的 `1girl` 之类能共用，而 `rating:general` 这种只在 Danbooru 有意义），
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
        droppedNoImageCensored = 0
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
     * **不拆成两步**（先 [setSite] 再 [setFilters]）：本轮的换站改成"留条件并顺手重搜"之后，
     * 那两步会先拿**上一次那排胶囊**在新站发一笔、紧接着又用新的这排发一笔 ——
     * 同一轮两个请求，正是 [applyFilters] 注释里记的那类返祖现场。
     * 这里一次装到位：条件是新的，只发一笔。
     *
     * 装条件绕开 [applyFilters] 的短路（`next == filters` 直接 return）：
     * 交接的语义就是"换一批条件重搜"，撞上"看起来一样"时也必须真发一次。
     */
    fun acceptHandoff(site: GallerySite, tags: List<String>) {
        switchSite(site, reSearch = false)
        active = true
        term = ""
        suggestions = emptyList()
        suggestError = null
        notice = null
        filters = tags.map { GalleryTagFilter(it) }
        if (filters.isEmpty()) {
            mode = GallerySearchMode.INPUT
            return
        }
        runSearch(1)
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
            if (!GallerySearch.fitsTagBudget(site, next.size + 1, danbooruLevel)) {
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
        if (rejected) notice = GallerySearch.budgetNotice(site, danbooruLevel)
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

    /** 站方给了行、但**没给可摆的图**而被跳过的张数（含各成因，见 [noImageNotice]）。 */
    var droppedNoImage by mutableIntStateOf(0)
        internal set

    /**
     * 其中**带着站方受管制标签**（`loli` / `shota`）的张数 —— 值得单独说，因为它的出路
     * 与"被删 / 还在审核"完全不同：那几条是站方**按规则封的**，等级不到就是看不到，
     * 换搜索词也绕不过（标签长在那张图上）。
     *
     * 这个字段从前叫 `droppedNoImageAdult`，数的是"成人分级" —— 那是个**错判**
     * （2026-09-26 复核实测：`cat rating:e` 匿名取回 5/5 都带图，成人分级根本不被挡；
     * 当年被抹的那批是 `loli`，成因挂在分级上了）。见 [DanbooruClient.CENSORED_TAGS]。
     */
    var droppedNoImageCensored by mutableIntStateOf(0)
        internal set

    /** 这一轮用的每页张数（重试/续页要按同一口径判到底）。 */
    var pageSize by mutableIntStateOf(0)
        internal set

    /**
     * 开始一轮新搜索：整片替换结果，翻页游标回到第 1 页。
     *
     * **刻意不碰 [mode]**：开搜不等于"用户改完了"。内联形态下用户加了一枚标签之后
     * 往往还要接着加第二枚（Danbooru 预算就是 2 枚），这里一开搜就把区收成一条的话，
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
        droppedNoImageCensored = 0
        pageSize = limit
        isSearching = true
    }

    fun appendPage(list: List<GalleryPost>, nextPage: Int, done: Boolean) {
        // 站方给了行不等于给了图：带 `loli` / `shota` 的条目，站方把四支 URL 键整片删掉，
        // 原样摆上去就是一屏尺寸正确、内容全空的灰卡。跳过并**按成因计数报出来**，不静默吞。
        val (kept, unusable) = list.partition { GalleryMerge.isDisplayable(it) }
        droppedNoImage += unusable.size
        droppedNoImageCensored += unusable.count { DanbooruClient.hasCensoredTag(it) }
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
        if (!GallerySearch.fitsTagBudget(site, filters.size + 1, danbooruLevel)) {
            notice = GallerySearch.budgetNotice(site, danbooruLevel)
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
        // 发出去的是**预算内那几枚**，不是胶囊全部（见 [effectiveFilters]）。
        val sent = effectiveFilters()
        val query = GallerySearch.queryOf(sent)
        val limit = pageSizeFor(siteAtRequest)
        if (nextPage == 1) {
            beginSearch(limit)
            // 提示必须写在 [beginSearch] **之后** —— 它会把 notice 清成 null。
            // 两条提示互斥地取一条：条件被砍那一件事更要紧（它改变了实际发出去的东西）。
            notice = trimmedNotice(sent) ?: censoredQueryNotice(sent)
        } else {
            isLoadingMore = true
            loadMoreError = null
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = when (siteAtRequest) {
                    GallerySite.DANBOORU -> DanbooruClient.getInstance(app).searchPosts(query, nextPage, limit)
                    GallerySite.YANDERE -> YandeReClient.getInstance(app).searchPosts(query, nextPage, limit)
                }
                // 站已换：这一笔属于上一个站，落地就是脏数据（chips 是新的、图是旧站的）——整笔丢掉。
                if (siteAtRequest != site) return@launch
                result.onSuccess { list ->
                    searchError = null
                    loadMoreError = null
                    appendPage(list, nextPage, GallerySearch.isExhausted(list.size, limit))
                    if (nextPage == 1) saveHistory(GallerySearchEntry(siteAtRequest, query))
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
                    GallerySite.DANBOORU -> DanbooruClient.getInstance(app).searchTags(term)
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
     */
    fun applyHistory(entry: GallerySearchEntry) {
        // 切站但**不重搜**：下面立刻会装条件并跑一笔，这里再搜一次就是同一轮发两笔。
        switchSite(entry.site, reSearch = false)
        filters = entry.filters
        term = ""
        suggestions = emptyList()
        suggestError = null
        notice = null
        if (filters.isEmpty()) {
            // 历史里不该有空条件（只有搜成功才写历史），真碰上就当"没条件"处理，别开一笔空搜。
            mode = GallerySearchMode.INPUT
            return
        }
        runSearch(1)
        mode = GallerySearchMode.RESULTS
    }

    /**
     * "站方给了行但没给图"那句读数；没有跳过就 null。
     *
     * 成因必须**点到站方那条规则本身**，不能含糊成"这几条已被删除"：
     * 2026-09-26 复核确认，带 `loli` / `shota` 的条目是站方按 wiki `help:censored_tags`
     * **成批封掉的**（Member 与未登录一律取不到图，Gold / Builder / Platinum 才行），
     * 而且**换搜索词绕不过** —— 标签就长在那张图上。
     * 说成"被删 / 还在审核"会让人白等一个永不到来的结果，也把"规则如此"误当成站方抽风。
     */
    fun noImageNotice(): String? = when {
        droppedNoImage == 0 -> null
        // 主因是受管制标签，且这一档看不到 —— 唯一需要解释清楚的一种。
        // "登录解锁不了"这半句是必须写的：用户的默认推断就是"登个号就好了"，
        // 而这条规则对 Member 与未登录一视同仁，不点破就会变成一次白折腾的注册。
        droppedNoImageCensored > 0 && !DanbooruClient.canViewCensoredTags(danbooruLevel) ->
            "跳过 $droppedNoImage 张，其中 $droppedNoImageCensored 张带 loli / shota：" +
                "站方把这两枚列为受管制标签，会员与未登录一样取不到图（登录解锁不了它），" +
                "要 Gold / Builder / Platinum 才行。换搜索词也绕不过 —— 标签就在那张图上"
        // 等级本来就看得到管制标签，那"没给图"就真的只剩被删 / 审核中这类原因。
        droppedNoImageCensored > 0 ->
            "跳过 $droppedNoImage 张（其中 $droppedNoImageCensored 张带 loli / shota，" +
                "你这个等级本该看得到，多半已被删除或还在审核）"
        else -> "跳过 $droppedNoImage 张（站方没给图，多半已被删除或还在审核）"
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
            GallerySearch.budgetNotice(site, danbooruLevel).orEmpty()
    }

    /**
     * "这一串条件必然搜不出图"那句**发车前**的提示；没有这种情况就 null。
     *
     * 为什么值得在发请求之前说：命中的查询不是"标签冷门"，是站方对这批条目**成批封了**，
     * 拿回来必然 0 张可摆（实测 `loli` 200 条一条都没有 URL）。让用户等 200 条跑完、
     * 再在空态上读一长句"跳过 200 张"，不如在条件装上的那一刻就说清是为什么。
     *
     * 与页尾那句分工：这里说"会搜不出来、为什么"，页尾说"这一轮实际跳了多少张"。
     * 两句都由 [DanbooruClient.CENSORED_TAGS] 这条规则派生，口径不会分叉。
     */
    private fun censoredQueryNotice(sent: List<GalleryTagFilter>): String? {
        val hits = GallerySearch.censoredTagsIn(site, sent)
        if (hits.isEmpty()) return null
        val names = hits.joinToString(" / ")
        // 已经是看得到管制标签的等级（Gold 起）就不用警告了 —— 那时它只是一枚普通标签。
        if (DanbooruClient.canViewCensoredTags(danbooruLevel)) return null
        return "$names 是站方的受管制标签：带它的条目对会员与未登录一律不给图" +
            "（登录解锁不了，要 Danbooru 的 Gold 起），这一轮多半是空的。"
    }
}
