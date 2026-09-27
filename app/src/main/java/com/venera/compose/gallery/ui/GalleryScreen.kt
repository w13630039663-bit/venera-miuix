package com.venera.compose.gallery.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.isWideScreen
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraSegmentedButton
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.data.network.HostCircuitBreaker
import com.venera.compose.gallery.data.GalleryFavoritesStore
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryFeedSource
import com.venera.compose.gallery.domain.GalleryGuard
import com.venera.compose.gallery.domain.GalleryRecommendations
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 画廊主 Tab（第 4 位，搜索右侧）。落地档 = **两站热门各 20 张打乱**：
 * yande.re 走官方日榜 `popular_recent?period=1d`，Gelbooru 没有日榜端点、
 * 走 `sort:score:desc` 的全站高分池（两站口径的差别见 `GalleryFeedSource`），
 * 抽样与打乱都锁在同一个种子上（见 `GalleryMerge`），一屏到底、没有翻页。
 *
 * ⚠️ **Gelbooru 那一路需要账号**（DAPI 匿名一律 401）。没配时这一屏只剩 yande.re 出图，
 * 页尾会如实写出原因 —— 不允许它长成"Gelbooru 今天没图"那种假读数。
 *
 * 与漫画侧的关系：只共用 OkHttpClient、VeneraCover/空态/波浪环这些技术层，
 * 取数、缓存、图片预算各自独立（边界见 `gallery-module-isolation-plan-2026-09.md` §二）。
 * 分级模式与用户屏蔽规则**只读共享**，但图站这边的 tag 匹配判据单独一把（见 `GalleryBlockMatch`）。
 */
@Composable
fun GalleryScreen(
    onOpenPost: (GallerySite, Long) -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val vm: GalleryViewModel = viewModel()
    // 搜索层的状态与日榜分开两个 ViewModel：结果要跨"点进大图再返回"活下来，
    // 而日榜那份状态每次刷新都是整片替换，混在一起会互相清掉。
    val svm: GallerySearchViewModel = viewModel()
    // 「以图搜图」是第三个宿主状态，与日榜 / 搜索各自独立（分三个的理由见该类头注）。
    // 它也住 ViewModel：反搜结果要活过"点进大图页再返回"。
    val rvm: GalleryReverseViewModel = viewModel()
    // 猜你喜欢那一屏的状态（分页、双源、可单站缺席）与日榜分开两份 —— 混在一起就是两套
    // 状态机互相清（搜索层当年分出去也是同一条理由）。为什么不复用 svm，见该类头注。
    val fvm: GalleryForYouViewModel = viewModel()

    // ── 两页：0 = 每日推荐，1 = 猜你喜欢 ──
    // 选中态必须 [rememberSaveable]：导航条目被下一页覆盖时组合会销毁，裸 `remember` 会把
    // 用户停留的那一页打回第 0 页（收藏页那处 `mode` 同一病根）。
    var galleryPage by rememberSaveable { mutableStateOf(GalleryPage.Daily) }
    val pagerState = rememberPagerState(initialPage = GalleryPage.entries.indexOf(galleryPage)) {
        GalleryPage.entries.size
    }
    // 滑动 → 页：只在**停稳**之后落。拖到一半就改 galleryPage 会让顶栏 actions、
    // 内容避让高度、分段小药丸三处同时跟着跳（收藏页那条注释原样适用）。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { galleryPage = GalleryPage.entries[it] }
    }
    // 页 → 滑动：点分段器走与手滑同一条欠阻尼弹簧，两条路径观感一致。
    LaunchedEffect(galleryPage) {
        val index = GalleryPage.entries.indexOf(galleryPage)
        if (pagerState.settledPage != index) {
            pagerState.animateScrollToPage(
                page = index,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    // 系统返回**两级**：展开态先收成一条（接着看图），收成一条之后才关掉整个搜索回日榜。
    // 展开着、而且一枚条件都没有时退无可退，直接关掉 —— 否则会剩一张空搜索卡停在一屏日榜上，
    // 用户不知道刚才那一下干了什么。
    //
    // 注意：**键盘起着时返回键被输入法吃掉**，那种返回落不到这里 ——
    // 那一档由搜索区自己看着 IME 收起去收条（见 `GallerySearchArea`）。
    // chips 与结果**不清**：再点搜索图标回到原上下文，不用重新搜。
    BackHandler(enabled = svm.active) {
        when {
            // 反搜排在最前：它开着时屏上是"一张图 → 一批相似图"，返回要先退回标签搜索
            // 那一具身体，而不是一下子把人丢回日榜。
            rvm.open -> rvm.closeLayer()
            svm.mode == GallerySearchMode.INPUT && svm.filters.isNotEmpty() -> svm.collapseToResults()
            else -> svm.closeSearch()
        }
    }

    // 大图页里点了某枚标签（跨 Activity 的交接槽，见 [GallerySearchHandoff]）：
    // 切进搜索态、按那枚标签直接开搜。key 读的是快照字段，所以写入方在另一个 Activity 里
    // 也能立刻把这边叫醒 —— 不靠 onResume（大图页是透明窗，底下这屏只是 paused）。
    LaunchedEffect(GallerySearchHandoff.pending) {
        val request = GallerySearchHandoff.consume() ?: return@LaunchedEffect
        // **整片换掉**而不是往现有 chips 上加：那是另一张图带来的上下文，留着旧的会让用户
        // 以为搜的还是刚才那串。切站 + 装条件 + 开搜是**一步**（见 acceptHandoff）：
        // 本轮的换站会顺手重搜一次，拆成 setSite + setFilters 就会先拿旧条件在新站发一笔，
        // 同一轮两个请求。
        svm.acceptHandoff(request.site, request.tags)
    }

    val source = remember { GalleryFeedSource.getInstance(context) }
    // 取图走画廊自己的 ImageLoader：独立目录 + 独立预算，不挤漫画侧的封面缓存。
    val imageLoader = remember { GalleryImageLoader.get(context) }

    // 分级与屏蔽**只读共享**漫画侧那一份，不新造开关（理由见 GalleryGuard）。
    val guard = remember { ContentGuardManager.getInstance(context) }
    val maskMode by guard.nsfwMaskMode.collectAsState()
    // 反搜要的是**同一把**分级判据，不是第二份开关：「成人内容处理」选了不过滤，
    // 才让 SauceNAO 把成人内容也带回来（站方的 `hide` 参数与解析期的 hidden 过滤两道同源）。
    val allowNsfw = maskMode == "OFF"
    val rules by guard.rules.collectAsState()

    /**
     * 「根据你的收藏」的标签集。判据全在 `GalleryRecommendations`（纯函数、有单测），
     * 这里只做三件事：喂收藏、注入**与那面墙同一把**屏蔽判据、把种子交下去。
     *
     * ⚠️ 种子读 [GalleryForYouViewModel.seed]（不是日榜那颗）：chips 与猜你喜欢那一屏是
     * **同一次抽样**，不同源就会出现"卡片上写着 A 串、点进去那面墙是 B 串"。
     * 从前它读 `vm.seed`，于是「换一批日榜」会把搜索卡里那行推荐标签一起换掉 ——
     * 用户从没要求过这个联动，而且它正是下面那句"返回时组合重建会重跑出一批不一样的推荐标签"
     * 要防的东西的另一半：真正该防的是**没有任何用户动作就变**。
     */
    val galleryFavorites by GalleryFavoritesStore.getInstance(context).favorites.collectAsState()
    val recommendations = remember(galleryFavorites, rules, fvm.seed) {
        GalleryRecommendations.recommendBySite(galleryFavorites, seed = fvm.seed) { tag ->
            guard.findGalleryBlockedRule(author = "", tags = listOf(tag)) != null
        }
    }

    // 第 0 页（日榜）与搜索结果**共用这一把**网格状态 —— 搜索墙接管时 pager 不在组合里，
    // 沿用同一把才保得住"从搜索结果退回日榜还在原处"的既有观感。
    val gridState = rememberLazyStaggeredGridState()
    // 猜你喜欢那一页**另持一把**：两页共用一把滚动位置就会互相踩
    // （第 1 页滑到深处、横滑回第 0 页，那屏会从中段开始画，页尾读数也跟着对不上）。
    val forYouGridState = rememberLazyStaggeredGridState()
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 大屏三列、手机两列 —— 与图片收藏那面墙同一口径（用户真机反馈「平板上两列偏大」定的）。
    val wide = isWideScreen(LocalConfiguration.current.screenWidthDp.dp)
    val columnCount = if (wide) 3 else 2
    /**
     * 分段器所在行的让位高度：`segmentedHeight(48dp) / segmentedHeightWide(56dp) + space3 × 2`。
     *
     * 逐字同收藏页那一处（口径出处见 `FavoritesScreen.kt:199-206` 与记忆
     * 「收藏页二级分段器必须挂顶栏 chrome」）。**必须是常量**：玻璃顶栏的
     * `heightOffsetLimit` 是普通 `var` 且存负值，composition 期读它不会随写入更新，
     * 拿它推导让位就会画出"内容顶进顶栏"或"让出一条空带"两种失败。
     */
    val tabsRowHeight = (if (wide) tokens.spacing.segmentedHeightWide else tokens.spacing.segmentedHeight) +
        tokens.spacing.space3 * 2

    /**
     * 取一次两站的热门池并合成整屏（两站口径不同，见 GalleryFeedSource）。
     *
     * 只有这一条路径，没有"加载更多"：日榜是一屏到底的固定池子（实测 yande.re 固定 40 条、
     * `page`/`limit` 被忽略；Gelbooru 100 条里抽 20）。
     */
    /**
     * 取一次两站的热门池并合成整屏（两站口径不同，见 GalleryFeedSource）。
     *
     * 只有这一条路径，没有"加载更多"：日榜是一屏到底的固定池子（实测 yande.re 固定 40 条、
     * `page`/`limit` 被忽略；Gelbooru 100 条里抽 20）。
     *
     * @param force true = 用户主动要求的那一轮（「刷新」「重试」），**忽略**在途标志。
     *
     * ## ⚠️ 那个"永久转圈"的缺陷（2026-09-26 修）
     *
     * 旧写法：`isLoadingFeed = true` 之后，只有协程**自己跑完**才在末尾清回 false。
     * 而这笔协程跑在 [rememberCoroutineScope] 里 —— 转圈期间切走 Tab / 转屏 →
     * 组合销毁 → 协程被取消 → **标志永远停在 true**。
     * 标志住在 [GalleryViewModel]（条目作用域，跨组合重建存活），于是回来时：
     * - `LaunchedEffect(refreshTick)` 的 loadedKey 守卫放行（因为没写凭证），
     * - 但本函数开头的 `if (vm.isLoadingFeed) return` 直接拦死，
     * 屏上只剩一个波浪环。更糟的是「重试」走 `retryFromUser() → vm.refresh()` 又落回
     * 同一行 early return —— **刷新与重试两个按钮当场变假按钮**，
     * 正是 `GalleryViewModel.refreshTick` 那段注释要防的东西。
     *
     * 两条修法，缺一不可：
     * 1. **用 try/finally + NonCancellable 清标志**：取消也要清，否则取消路径就是漏的那一条；
     *    NonCancellable 是必须的 —— 已取消的协程里再写 State 会被取消语义吃掉。
     * 2. **用户主动那一轮绕过标志**（[force]）：即便标志因某种残留卡住，
     *    显式重试也必须真发请求 —— "重试"按下去没有任何行为，是最坏的一种假按钮。
     */
    fun loadDaily(force: Boolean = false) {
        if (vm.isLoadingFeed && !force) return
        vm.feedError = null
        vm.isLoadingFeed = true
        scope.launch {
            try {
                source.loadDaily(seed = vm.seed)
                    .onSuccess { daily ->
                        vm.accept(
                            list = daily.merged.posts,
                            day = daily.date,
                            notice = daily.failures.entries
                                .joinToString(" · ") { "${it.key.displayName}：${it.value}" }
                                .takeIf { daily.failures.isNotEmpty() },
                        )
                        // 成功才写凭证：从错误态点重试要能真发请求。
                        vm.feedLoadedKey = "feed#${vm.refreshTick}|${vm.seed}"
                    }.onFailure { e ->
                        vm.feedError = e.message ?: "加载失败"
                    }
            } finally {
                // 取消路径也要清：见上面那段"永久转圈"的说明。
                withContext(NonCancellable) {
                    vm.isLoadingFeed = false
                }
            }
        }
    }

    /** 见 [retryFromUser]：只有用户显式要求重试才清熔断。 */
    fun resetBreakers() = GallerySite.entries.forEach { HostCircuitBreaker.reset(it.apiHost) }

    /**
     * 用户主动要求重来一次。
     *
     * 必须先清两站的域名熔断：连续两笔失败会把 host 拉黑 60 秒（`HostCircuitBreaker`），
     * 那期间任何请求都毫秒级快速失败 —— 不清的话「刷新」和「重试」按下去必然还是同一句
     * "熔断中，Ns 后重试"，那就是个假按钮。
     * 顺带换种子（在 [GalleryViewModel.refresh] 里），所以刷新确实会换一批、换个顺序。
     */
    /**
     * 下一轮取数要不要 [loadDaily] 的 force 语义。由 [retryFromUser] 置位、由下面的
     * `LaunchedEffect(vm.refreshTick)` 消费并复位。
     *
     * 用 `remember` 而不是 VM 字段：它只描述"这一次 effect 触发是不是用户点的"，
     * 属于页面内的瞬时意图，不需要跨组合重建存活（VM 那些字段才需要）。
     */
    var forceNextLoad by remember { mutableStateOf(false) }

    /**
     * 用户主动要求重来一次。
     *
     * 必须先清两站的域名熔断：连续两笔失败会把 host 拉黑 60 秒（`HostCircuitBreaker`），
     * 那期间任何请求都毫秒级快速失败 —— 不清的话「刷新」和「重试」按下去必然还是同一句
     * "熔断中，Ns 后重试"，那就是个假按钮。
     * 顺带换种子（在 [GalleryViewModel.refresh] 里），所以刷新确实会换一批、换个顺序。
     *
     * ⚠️ 置 [forceNextLoad]：这一轮**必须**忽略在途标志（见 [loadDaily] 的 force 参数）。
     * 否则「刷新 / 重试」若撞上残留的 `isLoadingFeed`，就成了按下去毫无行为的假按钮 ——
     * 正是那两个按钮存在的意义要防的东西。
     */
    fun retryFromUser() {
        resetBreakers()
        forceNextLoad = true
        vm.refresh()
    }

    /** [forceNextLoad] 的猜你喜欢版：只描述"这一次 effect 触发是不是用户点的"。 */
    var forceNextForYouLoad by remember { mutableStateOf(false) }

    /**
     * 猜你喜欢那一页的「换一批 / 重试」。
     *
     * 与 [retryFromUser] 同一条结构：先清熔断（否则按下去必然还是那句"熔断中，Ns 后重试"，
     * 那是假按钮），再置 force 让这一轮忽略残留的在途标志，最后换种子重抽标签。
     *
     * ⚠️ 两页**共用那两个 host 的熔断状态** —— 在推荐页清熔断，等于也把日榜那两站的
     * 60 秒快败窗口关掉了。这一处刻意不回避：日榜那头本来也只有"用户主动重试"才清。
     */
    fun retryForYouFromUser() {
        resetBreakers()
        forceNextForYouLoad = true
        fvm.refresh()
    }

    // 条目组合重建后这个 effect 会再跑一次，靠 loadedKey 挡住"返回即重拉"。
    LaunchedEffect(vm.refreshTick) {
        if (vm.feedLoadedKey == "feed#${vm.refreshTick}|${vm.seed}" && vm.posts.isNotEmpty()) return@LaunchedEffect
        val force = forceNextLoad
        forceNextLoad = false
        loadDaily(force = force)
    }

    /**
     * 落屏清单，外加「为什么比 40 张少」的两个计数。
     *
     * 遮罩判定在滚动里是"每项每次重组"的密度，所以按输入记忆；HIDDEN 直接不落进列表。
     * 两个"少了"的成因**分开记**：命中黑名单是用户自己的规则，按分级收起是模式选择 ——
     * 混成一个数字就没法告诉用户该动哪一个（2026-09-25 真机反馈的 某一站 整站空白，
     * 当时页尾还写着「那站     * 
     */
    val wall = remember(vm.posts, maskMode, rules) {
        buildGalleryWall(vm.posts, maskMode) { post ->
            guard.findGalleryBlockedRule(author = post.author, tags = post.tagList)?.pattern
        }
    }
    // ── 三面墙**共用同一把过滤判据**（用户 2026-09-25 改判：结果与画廊同屏，可退回原画廊）──
    // 日榜、搜索结果、猜你喜欢各自持有一面墙的清单，但过滤都走同一个 buildGalleryWall：
    // 否则会出现"墙上被挡掉、搜索结果里全裸"或"日榜上被挡、推荐页全裸"的分叉。
    //
    // 判据是「有条件的搜索上下文」，**不再是** `mode == RESULTS`：现在 INPUT 态下条件可能已经
    // 在框里（点历史装回来的、用户正打字改的），此时屏上该继续摆着上一轮结果而不是闪回日榜。
    val searching = svm.active && svm.filters.isNotEmpty()
    // 结果墙只在「这一轮真的取过（page > 0）或正在取」时接管。条件刚装好、还没搜时仍显示日榜 ——
    // 否则会摆出一屏"0 张"的假空态（不是没有结果，是还没搜）。
    val searchWallActive = searching && (svm.page > 0 || svm.isSearching)

    // ── 结果态"胶囊并入顶栏"的判据（2026-09-26 第九轮，用户口径）──
    // 下拉看图时整张搜索卡收走、条件胶囊去到顶栏左上角与大标题同一行；重新上拉回到顶部，
    // 搜索卡才长回来。
    //
    // 判据取**两个端点**（顶栏彻底折平 / 网格真正回到顶部），不取中间值：
    //  - Miuix 的 ScrollBehavior 在滚动停下时会把折叠进度 snap 到 0 或 1（它自己的阈值正是 0.5），
    //    所以两端之间有整段大标题行程当缓冲，不存在"一直停在中间"的状态；
    //  - 若拿中间值当阈值，用户在临界点小幅来回滚，卡片就会反复收放 —— 观感是闪。
    // 于是"收"只发生在顶栏折平之后，"放"只发生在回到顶部之时 ——
    // 正好就是用户说的"重新上拉回到顶的时候才出现搜索框"。
    //
    // 只在 RESULTS 态成立：INPUT 态是编辑态（卡片要完整背板承载补全 / 历史列表），
    // 而且那一态键盘起着，本来也滚不动网格。
    val topBarState = topBarBehavior.state
    val searchCollapsed by remember(topBarState) {
        derivedStateOf {
            svm.active &&
                svm.mode == GallerySearchMode.RESULTS &&
                topBarState.collapsedFraction >= COLLAPSE_SETTLED_FRACTION
        }
    }

    val searchWall = remember(svm.results, maskMode, rules) {
        buildGalleryWall(svm.results, maskMode) { post ->
            guard.findGalleryBlockedRule(author = post.author, tags = post.tagList)?.pattern
        }
    }
    // 猜你喜欢那一面墙**同一把判据**（`buildGalleryWall`）：否则会出现"日榜上被挡、
    // 推荐页全裸"的分叉 —— 同一张图在同一个页面的两页上待遇不同，是最难解释的一种不一致。
    val forYouWall = remember(fvm.posts, maskMode, rules) {
        buildGalleryWall(fvm.posts, maskMode) { post ->
            guard.findGalleryBlockedRule(author = post.author, tags = post.tagList)?.pattern
        }
    }

    // 搜索区的真实高度由它自己上报（里面有 chips、补全、历史，行数会变），
    // 网格的顶部避让按它加 —— 避让本身还要**跟着动画**，否则展开那一刻整屏是"往下跳"而不是"被推下去"。
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    val areaHeight = with(LocalDensity.current) { areaSize.height.toDp() }
    // 与其他主 Tab 完全一致的顶栏避让地板（口径出处见冻结声明第七轮）。
    val topBarFloor = statusBarTop + 104.dp
    // 搜索区是**内联**的：它不盖住内容，而是把网格推下去 —— 只要它开着，避让就得加上它的实测高度。
    // 展开态的高度变化（补全列表一节一节长出来）由 `areaSize` 每帧回报，网格因此是被"推"下去的；
    // 关搜索时落回**分段器那一行**的高度（不是落回地板 —— 那一行常驻在 bottomContent 里）。
    val gridTopPadding by animateDpAsState(
        // 搜索区开着 = 让位给它的实测高度；**没开搜索 = 让位给分段器那一行**（它常驻在
        // `bottomContent`，见下面那处槽位互斥）。两档都跟着同一条 medium 动画，
        // 切页与开关搜索才像同一段动作。
        targetValue = if (svm.active) topBarFloor + areaHeight + tokens.spacing.space3 else topBarFloor + tabsRowHeight,
        animationSpec = tween(durationMillis = tokens.motion.medium),
        label = "galleryGridTopPadding",
    )

    /**
     * 新一轮结果落地 → 滚回顶部。
     *
     * 不滚的后果有两个，都实际发生：用户落在"上一轮滚到的深处"，看到的是新结果的中段
     * （第一眼像是"搜出来一堆不相干的图"）；同时续页判定会当场认为已经到底、顺手把第 2 页也取了。
     * 用 [GallerySearchViewModel.searchGeneration] 而不是 `results.size` 当信号：两轮条数相同就观察不到。
     */
    var scrolledGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(svm.searchGeneration) {
        if (svm.searchGeneration == 0) return@LaunchedEffect
        gridState.scrollToItem(0)
        scrolledGeneration = svm.searchGeneration
    }

    // 结果滚到离底几张以内就续下一页；到底了就不再发（页尾那行会说出来）。
    //
    // ⚠️ **触发源必须是滚动本身**，不能是"状态字段变没变"。此前这里是
    // `LaunchedEffect(svm.results.size, svm.isSearching, …)`，而那些字段只在一页落地时变一次，
    // 偏偏那一瞬间 `gridState.layoutInfo` 还没反映出新条目（组合与布局都还没跑），
    // `totalItemsCount - 1` 取到的是旧值 → 判定"还没到底" → 跳过；之后再没有任何 key 会变，
    // 这个 effect 永远不会重跑。真机上的长相就是：页尾写着「上滑继续取」，滑到底一点动静都没有、
    // 也刷不出新图（用户 2026-09-26 反馈，只有 100 张后再无下文）。
    // 改成盯住网格布局信息（滚动到哪儿 / 加了几项都算变化），判定点自然跟着走。
    //
    // 判定所需的每一项都**在 snapshotFlow 里读**（含 [searchWallActive] 与顶部那次回滚的凭据）：
    // `LaunchedEffect(gridState)` 的 block 只在首次组合时被捕获，外面那些 `val` 都是那一刻的旧值，
    // 读进来才会跟着变（`svm` 的字段本身是快照状态，读一次就建立依赖）。
    //
    // 三道闸门原样保留：
    //  1. 新一轮结果落地后先滚回顶部，滚回去之前不判续页 —— 否则"旧滚动位置还在深处"
    //     会让这次搜索当场顺手把第 2 页也取了；
    //  2. `loadMoreError` —— 续页失败后**不再自动重试**（否则 isLoadingMore 一落回 false 就再发，
    //     踩着同一个失败请求无限循环），改成页尾一枚「重试」由用户点；
    //  3. 在取（首屏或续页）时不并发取下一页。
    LaunchedEffect(gridState) {
        snapshotFlow {
            val wallActive = svm.active && svm.filters.isNotEmpty() && (svm.page > 0 || svm.isSearching)
            val info = gridState.layoutInfo
            GalleryLoadMoreState(
                wallActive = wallActive,
                exhausted = svm.exhausted,
                busy = svm.isSearching || svm.isLoadingMore,
                failed = svm.loadMoreError != null,
                // 顶部回滚已完成（两条 effect 的先后由 searchGeneration 这个凭据保证）。
                settled = scrolledGeneration == svm.searchGeneration,
                lastIndex = info.totalItemsCount - 1,
                visibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1,
            )
        }
            .distinctUntilChanged()
            .collect { state ->
                if (!state.wallActive || state.exhausted || state.busy || state.failed) return@collect
                if (!state.settled) return@collect
                if (state.lastIndex >= 0 && state.visibleIndex >= state.lastIndex - LOAD_MORE_AHEAD) {
                    svm.runSearch(svm.page + 1)
                }
            }
    }

    // ── 猜你喜欢那一页的三个观察点 ──
    // 形状与日榜/搜索那头**逐条对应**（取数凭据、滚回顶部、盯滚动判续页），
    // 因为那三处每一条都已经踩过一次具体的坑，写在它们的注释里；这里只复述结论。
    // 首次可见才取数 + `forceNextForYouLoad` 的声明在 [retryForYouFromUser] 旁边（同一个用途的一对）。

    // **首次滑到那一页才取数**：冷启动不去抢日榜那一屏的带宽与 12s 预算。
    // 条目组合重建时 loadedKey 守卫放行不了第二次拉（与日榜 `LaunchedEffect(vm.refreshTick)` 同一条）。
    LaunchedEffect(galleryPage, fvm.refreshTick) {
        if (galleryPage != GalleryPage.ForYou) return@LaunchedEffect
        if (fvm.loadedKey == "foryou#${fvm.refreshTick}|${fvm.seed}" && fvm.posts.isNotEmpty()) {
            return@LaunchedEffect
        }
        val force = forceNextForYouLoad
        forceNextForYouLoad = false
        fvm.load(recommendations, force = force)
    }

    // 新一轮结果落地 → 滚回顶部。不滚的后果与搜索页那条一样：用户落在上一轮的深处，
    // 而续页判据会当场认为已经到底。信号用 [GalleryForYouViewModel.generation]，不用 posts.size。
    var forYouScrolledGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(fvm.generation) {
        if (fvm.generation == 0 || forYouScrolledGeneration == fvm.generation) return@LaunchedEffect
        forYouGridState.scrollToItem(0)
        forYouScrolledGeneration = fvm.generation
    }

    // 滑到底取下一页 —— **每页各一份判据**：搜索那一份整块硬编码读 `svm`，套不过来。
    // 三道闸门原样保留（没滚回顶部不判、续页刚失败不自动重试、在取时不并发）。
    LaunchedEffect(forYouGridState) {
        snapshotFlow {
            val info = forYouGridState.layoutInfo
            GalleryLoadMoreState(
                // 这一页没有"还没搜"的那种空：取过就是取过，page > 0 即墙在。
                wallActive = fvm.page > 0,
                exhausted = fvm.exhausted,
                busy = fvm.isLoading || fvm.isLoadingMore,
                failed = fvm.loadMoreError != null,
                settled = forYouScrolledGeneration == fvm.generation,
                lastIndex = info.totalItemsCount - 1,
                visibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1,
            )
        }
            .distinctUntilChanged()
            .collect { state ->
                if (!state.wallActive || state.exhausted || state.busy || state.failed) return@collect
                if (!state.settled) return@collect
                if (state.lastIndex >= 0 && state.visibleIndex >= state.lastIndex - LOAD_MORE_AHEAD) {
                    fvm.loadMore()
                }
            }
    }

    // 页尾那一行：搜索与日榜各一句，**类型显式写出来**（`when` 里混 `@Composable {}` 字面量
    // 会被推成 Unit?，编译器就不认了）。
    val searchFooter: (@Composable () -> Unit)? = if (searchWallActive) {
        @Composable {
            GallerySearchEnd(
                cards = searchWall.cards.size,
                // 报的是**真正发出去的那串**（预算内那几枚），不是全部胶囊 ——
                // 页尾是读数，读数与实际请求不一致就是假读数。
                query = GallerySearch.queryOf(svm.effectiveFilters()),
                page = svm.page,
                exhausted = svm.exhausted,
                loadingMore = svm.isLoadingMore,
                blockedCount = searchWall.blockedCount,
                blockedRules = searchWall.blockedRules,
                hiddenByRating = searchWall.hiddenByRating,
                videos = searchWall.cards.count { it.post.isVideo },
                noImage = svm.noImageNotice(),
                // 条件被预算砍过也要在这儿说一遍：收成一条之后展开区不画了，
                // 那句提示只在展开态看得见，页尾得自己承担这个交代。
                trimmedNote = svm.budgetTrimNotice(),
                // 续页失败：页尾如实说 + 给一枚「重试」。没有它，失败之后页面怎么滑都不会再发请求。
                loadMoreError = svm.loadMoreError,
                onRetryLoadMore = { svm.retryLoadMore() },
            )
        }
    } else {
        null
    }
    // 日榜只有一轮，没有"下一页"。整屏拉完就在页尾把「哪天、各站摆了几张、
    // 几张被挡掉」说出来 —— 不再放「上滑加载更多」：那是搜索页 §7 刚修掉的同一类假象。
    // 这一行现在归 [GalleryDailyPage] 自己拼（两页各自的页尾是各自那面墙的读数，
    // 放在宿主层就会出现"屏上是推荐页、页尾还写着日榜那天"的错配）。

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // 反搜开着 → 这一屏摆的是"这张图像哪些图"。它与日榜 / 标签搜索互斥，
            // 所以必须排在所有分支**最前**：否则日榜那一面墙会在它底下同时存在，
            // 变成"两层内容叠着、只有上面那层收得到点击"。
            rvm.open -> GalleryReverseResults(
                rvm = rvm,
                topPadding = gridTopPadding,
                allowNsfw = allowNsfw,
                onOpenPost = onOpenPost,
                modifier = Modifier
                    .nestedScroll(topBarBehavior.nestedScrollConnection)
                    .blurBackdropSource(topBarBackdrop),
            )

            // 第 1 页还在飞：这一屏什么都还没有，摆一行"已摆出 0 张；上滑继续取"是假读数
            // （此刻上滑确实什么也不会发生，续页 effect 正被 isSearching 挡着）。
            // 从大图页点标签交接过来时这一帧最容易撞见，所以给整页级波浪环 —— 与日榜那一屏同一条口径。
            searchWallActive && searchWall.cards.isEmpty() && svm.isSearching -> Box(
                // 顶栏避让与网格同一条口径：搜索区是内联的，这一屏的中心得落在**它下面**，
                // 否则波浪环会被那张卡压住一半。
                //
                // `imePadding` 也是必须的：`fillMaxSize` 是**含键盘下方那一截**的，
                // 只按整屏居中会让中心正好落在键盘底下 —— 展开态下键盘叠着卡片时尤其明显。
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = gridTopPadding)
                    .imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(tokens.spacing.loaderPage),
                    color = tokens.color.primary,
                    trackColor = tokens.color.surfaceVariant,
                )
            }

            // 搜完 0 张：成因必须点名。最常见的一种不是"没有这个标签"，而是
            // **站方给了行却没给图**（条目本身取不到可用的图片地址）——
            // 具体张数由 `svm.noImageNotice()` 如实说。
            //
            // 这里曾经还有一枚「排掉成人分级再搜一次」的出口，2026-09-26 已删：它的前提是错的。
            // 实测成人分级匿名本来就看得见，那枚按钮按下去是白按，
            // 比没有更糟（它同时在暗示一个不存在的原因）。剩下的正确出口只有一个：改条件。
            // ⚠️ 换到 Gelbooru 之后这条判断**更成立**：它四个分级都正常可看，
            // 没有任何"排掉某一档就能出图"的情形。
            searchWallActive && searchWall.cards.isEmpty() && !svm.isSearching -> Box(
                // 中心不按整屏算：展开着的搜索区 + 键盘会把整屏中心推得很低，按钮就够不着了。
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = gridTopPadding,
                        // 左右与其余空态同一口径。
                        start = tokens.spacing.screenHorizontal,
                        end = tokens.spacing.screenHorizontal,
                    )
                    .imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                VeneraEmptyView(
                    /*
                     * 标题分三档而不是两档：**"这一站现在用不了"与"这一轮搜失败了"是不同的事**。
                     * 前者（目前只有 Gelbooru 未配账号）一次请求都没发出去，说成"没搜成"
                     * 会让用户去重试、去改网络 —— 而他该做的是去配置页填两个字段。
                     * 判据用 `searchError` 是否就是那句固定文案，而不是再加一个状态位：
                     * 那句话本身就是"发车前被拦住"的唯一来源，多一个旗标就多一处会不同步的状态。
                     */
                    title = when {
                        svm.searchError == GallerySearchViewModel.NEEDS_GELBOORU_ACCOUNT ->
                            "这一站还不能用"
                        svm.searchError != null -> "这一轮没搜成"
                        else -> "这一串标签没有可摆的图"
                    },
                    message = svm.searchError
                        ?: listOfNotNull(
                            "条件：${GallerySearch.queryOf(svm.effectiveFilters())}",
                            svm.budgetTrimNotice(),
                            svm.noImageNotice(),
                            if (searchWall.blockedRules.isNotEmpty()) {
                                "命中屏蔽规则 ${searchWall.blockedRules.joinToString("、")}"
                            } else {
                                null
                            },
                        ).joinToString("\n"),
                    icon = Icons.Outlined.Image,
                    actionText = "改条件",
                    onAction = { svm.backToInput() },
                )
            }

            searchWallActive -> GalleryCardsGrid(
                cards = searchWall.cards,
                imageLoader = imageLoader,
                columnCount = columnCount,
                state = gridState,
                contentPadding = PaddingValues(
                    start = tokens.spacing.screenHorizontal,
                    end = tokens.spacing.screenHorizontal,
                    top = gridTopPadding,
                    bottom = VeneraSpacing.bottomBarClearance,
                ),
                modifier = Modifier
                    .nestedScroll(topBarBehavior.nestedScrollConnection)
                    .blurBackdropSource(topBarBackdrop),
                onOpen = { post ->
                    // 左右翻页的交接：把**这一面墙真正摆出来的那批**（已过屏蔽 / 分级判据）
                    // 交给大图页，顺序即屏上顺序。搜索结果、日榜、猜你喜欢三面墙各自交各自的队列，
                    // 所以三处都有左右翻，且翻页范围不会跨到另一面墙去。
                    GalleryViewerQueue.set(searchWall.cards.map { it.post })
                    onOpenPost(post.site, post.id)
                },
                footer = searchFooter,
            )

            // ── 两页都归 pager ──
            // 日榜那一屏的行为逐字不变（同一面墙、同一个页尾读数、同一枚重试）；
            // 猜你喜欢那一页另持一把网格状态、另一个队列、另一套空态。
            else -> HorizontalPager(
                state = pagerState,
                key = { GalleryPage.entries[it].name },
                beyondViewportPageCount = 1,
                // 搜索卡展开着（含输入法起着）时禁横滑：那一态用户的拇指在打字，
                // 一划就把背后的墙换成另一面，读起来像"图自己变了"。
                // （对应参照物 Breadboard 那处 `userScrollEnabled = !shouldShowLargeImage` 的门控精神。）
                userScrollEnabled = !svm.active,
                // 回弹换成欠阻尼：松手过阈值会冲过头再收回来一点，与分段小药丸那条 0.7 同族。
                // 库默认的无阻尼弹簧到位就停，读起来是"啪"地贴上，没有跟手感。
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topBarBehavior.nestedScrollConnection),
                pageContent = { index ->
                    // 页外的面板不参与毛玻璃采样：两页同时往同一个 LayerBackdrop 里录制，
                    // 顶栏模糊到底糊谁就成了未定义行为（收藏页那条原样适用）。
                    val backdrop = if (pagerState.currentPage == index) topBarBackdrop else null
                    when (GalleryPage.entries[index]) {
                        GalleryPage.Daily -> GalleryDailyPage(
                            hasPosts = vm.posts.isNotEmpty(),
                            wall = wall,
                            date = vm.date,
                            sourceNotice = vm.sourceNotice,
                            isLoading = vm.isLoadingFeed,
                            error = vm.feedError,
                            imageLoader = imageLoader,
                            columnCount = columnCount,
                            state = gridState,
                            topPadding = gridTopPadding,
                            backdrop = backdrop,
                            onOpen = { post ->
                                GalleryViewerQueue.set(wall.cards.map { it.post })
                                onOpenPost(post.site, post.id)
                            },
                            onRetry = { retryFromUser() },
                        )

                        GalleryPage.ForYou -> GalleryForYouPage(
                            fvm = fvm,
                            wall = forYouWall,
                            // 空态那句"有 N 张收藏却抽不出标签"要说得出张数 —— 不说就与"你还没收藏"分不开。
                            favoriteCount = galleryFavorites.size,
                            imageLoader = imageLoader,
                            columnCount = columnCount,
                            state = forYouGridState,
                            topPadding = gridTopPadding,
                            backdrop = backdrop,
                            onOpen = { post ->
                                GalleryViewerQueue.set(forYouWall.cards.map { it.post })
                                onOpenPost(post.site, post.id)
                            },
                            onRetry = { retryForYouFromUser() },
                            onGoToDaily = { galleryPage = GalleryPage.Daily },
                        )
                    }
                },
            )
        }

        // 顶栏**一直在**（用户 2026-09-25 第四轮：不跳页、也不替换标题栏）。
        // 搜索区挂在它的 bottomContent 里 —— 那枚槽位是这套顶栏给"顶栏下方常驻内容"准备的
        // 正规出口（下载页 / 分类页 / 收藏页 / 历史页都在用），所以搜索区天然贴在标题下面，
        // 不占用标题栏，也不需要在页面里自己拼一层状态栏避让。
        VeneraTopAppBar(
            title = "画廊",
            largeTitle = "画廊",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            actions = {
                // 结果态多一枚 🔍：那一态搜索条已收成"一行胶囊"，**改条件 / 接着加标签的入口在这里**
                // （点那一行胶囊同样能展开，两条路等价 —— 一枚看得见的按钮 + 一个顺手的快捷方式）。
                if (svm.active && svm.mode == GallerySearchMode.RESULTS) {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        svm.backToInput()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "改搜索条件",
                            tint = tokens.color.textPrimary,
                        )
                    }
                }
                // 没开搜索时它是「开搜索」，开着时同一位置换成「✕ 关掉搜索」——
                // 两态共用一枚，因为它们不会同时需要（开着时"开搜索"没有意义）。
                // 点它**直接展开并弹键盘**；「收成一条」刻意不挤在这枚上：
                // 那是键盘收起与系统返回的事（见 `GallerySearchArea`），三态塞进一枚图标谁也读不出来。
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (svm.active) {
                        // 关搜索要把反搜那一层一起关掉：它的输入挂在搜索卡里，
                        // 只关搜索会留下"卡片没了但墙还在摆反搜结果"这种孤儿态。
                        rvm.closeLayer()
                        svm.closeSearch()
                    } else svm.openSearch()
                }) {
                    Icon(
                        imageVector = if (svm.active) Icons.Outlined.Close else Icons.Outlined.Search,
                        contentDescription = if (svm.active) "关掉搜索" else "搜索标签",
                        tint = tokens.color.textPrimary,
                    )
                }
                // 「换一批」只管**当前这一页**：搜索态下屏上是搜索结果，按它用户看不见效果，
                // 所以在搜索态整枚收掉，而不是摆一颗"按了没反应"的钮（换结果由「搜索」钮负责）。
                //
                // 两页并存后它必须跟着页走（用户 2026-09-28 拍板）：在推荐页按下去却换掉
                // 没在看的那屏日榜，是最容易被读成 bug 的联动 —— 两页的种子也因此各自独立。
                if (!svm.active) {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        // refresh() 会清掉 posts/loadedKey 并**换种子**，所以这一下既真发请求，
                        // 也真换一批、换个顺序（抽样与打乱都挂在种子上）。
                        when (galleryPage) {
                            GalleryPage.Daily -> retryFromUser()
                            GalleryPage.ForYou -> retryForYouFromUser()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = when (galleryPage) {
                                GalleryPage.Daily -> "换一批"
                                // 推荐页那句要说出它干了什么：这一按是**重抽标签**，
                                // 不只是把同一批图再洗一次顺序。
                                GalleryPage.ForYou -> "按收藏重抽一批"
                            },
                            tint = tokens.color.textPrimary,
                        )
                    }
                }
            },
            bottomContent = {
                // ── 那枚槽位是**独占**的：搜索区与分段器不能同时画 ──
                // 分段器挂顶栏玻璃层是 2026-09-28 沿用收藏页那次拍板的口径（二级分段器不进内容）。
                // 但同一格也是搜索区的家（`AnimatedVisibility(visible = svm.active && !searchCollapsed)`），
                // 所以判据就是那一条的**反面**：搜索区让位时分段器才回来。
                // 它不参与搜索/反搜两层，那两层整屏接管内容墙（见上面那个 `when` 的分支顺序）。
                AnimatedVisibility(
                    visible = !svm.active,
                    enter = expandVertically(
                        animationSpec = tween(durationMillis = tokens.motion.medium),
                    ) + fadeIn(animationSpec = tween(tokens.motion.medium)),
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = tokens.motion.medium),
                    ) + fadeOut(animationSpec = tween(tokens.motion.medium)),
                ) {
                    GalleryPageTabs(
                        selected = GalleryPage.entries.indexOf(galleryPage),
                        onSelect = { galleryPage = GalleryPage.entries[it] },
                    )
                }
                // 搜索区**挂在顶栏下面就地展开**（用户 2026-09-26 拍板的形态）。
                // 这一格是这套顶栏给"顶栏下方常驻内容"准备的规格出口（下载页 / 分类页 /
                // 收藏页 / 历史页都在用），所以它天然贴在标题下面、不占标题栏，
                // 也不需要在页面里自己拼一层状态栏避让。
                //
                // 它**两个高度**（2026-09-26 第八轮按真机截图收口）：
                //  - 展开态 = 条件 + 输入框 + 站点 + 补全 / 历史列表；
                //  - 结果态 = **只剩一行胶囊**（最低 48dp），查询串不再靠输入框表达。
                // 具体形态由 `GallerySearchArea` 按 `svm.mode` 自己选，这里不必分叉。
                //
                // 第九轮再加一道门控：结果态**下拉看图时整卡收走**（见 [searchCollapsed]），
                // 条件改由 `GallerySearchCollapsedChips` 呈现在顶栏里。卡片一收，
                // 它上报的高度随之归零，网格的顶部避让也跟着动画回落 —— 让出的那段屏高是真的还给了图片，
                // 而不只是把卡片画透明（那会留一条谁也用不上的空白）。
                AnimatedVisibility(
                    visible = svm.active && !searchCollapsed,
                    // 展开/收起与网格避让用同一档时长（motion.medium），两处才像同一段动作。
                    enter = expandVertically(
                        animationSpec = tween(durationMillis = tokens.motion.medium),
                    ) + fadeIn(animationSpec = tween(tokens.motion.medium)),
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = tokens.motion.medium),
                    ) + fadeOut(animationSpec = tween(tokens.motion.medium)),
                ) {
                    GallerySearchArea(
                        svm = svm,
                        recommendations = recommendations,
                        // 点一枚 = 切站 + 装条件 + 开搜，**一步**（理由见 acceptHandoff：
                        // 拆成 setSite + setFilters 会先拿旧条件在新站发一笔）。
                        onPickRecommendation = { site, tags -> svm.acceptHandoff(site, tags) },
                        onSizeChanged = { areaSize = it },
                        rvm = rvm,
                        allowNsfw = allowNsfw,
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // ── 吸附态的条件胶囊 ──
        // 画在**顶栏之后**（Box 的最后一个子项，z 序最上）：它是"胶囊并入顶栏"的落点，
        // 必须压在顶栏的模糊背板**上面**，而不是被背板盖住。
        //
        // 纵向补偿 `statusBarTop`：本层从屏幕顶开始算，而顶栏的内容区是从状态栏下方开始的 ——
        // 不补这一截，胶囊会顶到状态栏里去。
        //
        // 位置口径（与顶栏既有元素同一条基准线）见 `GallerySearchCollapsedChips`。
        AnimatedVisibility(
            visible = searchCollapsed,
            // 淡入 + 从下方一小段滑上来：卡片就在它下方，这个方向读起来是"被顶栏吸上去"。
            enter = fadeIn(animationSpec = tween(durationMillis = tokens.motion.medium)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = tokens.motion.medium),
                ) { height -> height / 2 },
            exit = fadeOut(animationSpec = tween(durationMillis = tokens.motion.short)),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            GallerySearchCollapsedChips(
                svm = svm,
                onExpand = { svm.backToInput() },
                modifier = Modifier.offset(y = statusBarTop),
            )
        }
    }
}

/**
 * 顶栏"已经折平"的判据 —— 到了这个点才把结果态那张搜索卡收走。
 *
 * 取 0.99 而不是 1：折叠进度是 `heightOffset / heightOffsetLimit`，滚到深处被夹在 1，
 * 但那是浮点除法，边界上可能停在 0.9999…。留一丝余量，语义上仍然是"完全折叠"。
 * 之所以要"完全折叠"而不取中间值，见 [GalleryScreen] 里 [searchCollapsed] 那段注释。
 */
private const val COLLAPSE_SETTLED_FRACTION = 0.99f

/**
 * 续页判定所需的一切，收成一个值。
 *
 * 收起来是为了让 `snapshotFlow { … }.distinctUntilChanged()` 能一次比干净：
 * 只要其中任一项变了就重新判一次，都没变就不做任何事（滚动里 `layoutInfo` 每帧都在动，
 * 不过这一层就会出现"每帧判一次"的无效工作）。
 */
private data class GalleryLoadMoreState(
    /** 屏上这面墙是不是"有条件的搜索结果墙"（日榜没有下一页，直接短路）。 */
    val wallActive: Boolean,
    val exhausted: Boolean,
    val busy: Boolean,
    val failed: Boolean,
    /** 新一轮结果是否已经滚回顶部（没回之前不判续页）。 */
    val settled: Boolean,
    val lastIndex: Int,
    val visibleIndex: Int,
)

/**
 * 离底还剩几张就预取。
 *
 * 4 张在 2 列瀑布流里差不多就是一屏 —— 用户滑到底时下一批刚好接上，不会先看到一次"空一阵"。
 */
private const val LOAD_MORE_AHEAD = 4

/**
 * 落屏清单 + 两类"少了"的成因 —— 日榜与搜索结果**共用这一把判据**。
 *
 * 遮罩判定在滚动里是"每项每次重组"的密度，所以按输入记忆；HIDDEN 直接不落进列表。
 * 两个"少了"的成因**分开记**：命中黑名单是用户自己的规则，按分级收起是模式选择 ——
 * 混成一个数字就没法告诉用户该动哪一个（2026-09-25 真机反馈的 某一站 整站空白，
 * 当时页尾还写着「那站 * 
 *
 * @param blockedRuleOf 这条被哪条用户规则挡了（返回规则原文，给页面念出来）。
 *   判据由调用方给：画廊侧是 [ContentGuardManager.findGalleryBlockedRule]，
 *   日榜与搜索必须**同一个调用**，否则会出现"墙上被挡、搜索结果里全裸"的分叉。
 */
internal fun buildGalleryWall(
    posts: List<GalleryPost>,
    maskMode: String,
    blockedRuleOf: (GalleryPost) -> String?,
): GalleryWall {
    val kept = ArrayList<GalleryCard>(posts.size)
    val blockedRules = LinkedHashSet<String>()
    var blockedCount = 0
    var hiddenByRating = 0
    posts.forEach { post ->
        val blocked = blockedRuleOf(post)
        when (GalleryGuard.maskStateFor(post, maskMode, blockedByUser = blocked != null)) {
            GalleryGuard.HIDDEN -> if (blocked != null) {
                blockedCount++
                blockedRules += blocked
            } else hiddenByRating++
            GalleryGuard.BLURRED -> kept += GalleryCard(post, masked = true)
            else -> kept += GalleryCard(post, masked = false)
        }
    }
    return GalleryWall(kept, blockedCount, blockedRules.toList(), hiddenByRating)
}

/**
 * 那面错落屏的墙 —— 日榜与搜索结果共用（用户拍板搜索层后抽出来的，
 * 目的不是省事：key 带站点、比例夹 0.4~2.5、点卡片要截"滑入那一帧"这三条口径
 * 一旦分叉，搜索页就会出现"点这张飞过去那张"或"进场没有占位"）。
 */
@Composable
internal fun GalleryCardsGrid(
    cards: List<GalleryCard>,
    imageLoader: ImageLoader,
    columnCount: Int,
    state: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    onOpen: (GalleryPost) -> Unit,
    modifier: Modifier = Modifier,
    /** 长按一张卡。收藏页用它拉出「从收藏移除」，日榜/搜索不传（长按在那儿没有任何事可做）。 */
    onLongPress: ((GalleryPost) -> Unit)? = null,
    /** 卡片右下角那一层的补充内容（画在来源胶囊之后）。null 时与从前逐像素一致。 */
    overlay: (@Composable BoxScope.(GalleryPost) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    LazyVerticalStaggeredGrid(
        state = state,
        columns = StaggeredGridCells.Fixed(columnCount),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space5),
        verticalItemSpacing = tokens.spacing.gridGap,
        modifier = modifier.fillMaxSize(),
    ) {
        header?.let { slot ->
            item(key = "grid-header", span = StaggeredGridItemSpan.FullLine) { slot() }
        }
        // key 必须带站点：两站 id 各自编号，同 id 是两张不同的图，撞 key 会直接崩。
        items(cards, key = { it.post.uid }) { card ->
            GalleryPostCard(
                card = card,
                imageLoader = imageLoader,
                onOpen = { bounds ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    // 整页向上滑入时垫在最上面的那一帧：趁卡片还在屏上截下来（见 GalleryFlyIn）。
                    GalleryFlyIn.capture(view, bounds)
                    onOpen(card.post)
                },
                onLongPress = onLongPress?.let { handler ->
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        handler(card.post)
                    }
                },
                overlay = overlay,
            )
        }
        footer?.let { slot ->
            item(key = "grid-footer", span = StaggeredGridItemSpan.FullLine) { slot() }
        }
    }
}

/** 一张卡片：条目 + 要不要打码。打码判定在派生列表时一次算好，卡片本身不做判断。 */
internal class GalleryCard(val post: GalleryPost, val masked: Boolean)

/** 一屏的落屏结果：摆出来的卡片 + 两类"少了"的成因，页尾那一行全靠它报数。 */
internal class GalleryWall(
    val cards: List<GalleryCard>,
    /** 命中用户屏蔽规则而被剔掉的张数。 */
    val blockedCount: Int,
    /** 命中了哪几条规则（去重后的规则原文，用于"是哪条规则挡的"）。 */
    val blockedRules: List<String>,
    /** 按「成人内容处理 = 彻底隐藏」收起的张数。 */
    val hiddenByRating: Int,
)

@Composable
private fun GalleryPostCard(
    card: GalleryCard,
    imageLoader: ImageLoader,
    /** 把这张卡在**窗口坐标系**里的矩形一起交出去 —— 只用来裁出滑入期间垫着的那一帧（见 [GalleryFlyIn]）。 */
    onOpen: (Rect) -> Unit,
    onLongPress: (() -> Unit)? = null,
    overlay: (@Composable BoxScope.(GalleryPost) -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val post = card.post
    var bounds by remember { mutableStateOf(Rect.Zero) }
    // 高度在**摆放前**就知道：两站的 JSON 都直接带中间档宽高，
    // 不像本地插图那样要靠加载成功回填 —— 所以这里不会有"各列先等分再集体跳动"。
    // 比例夹在 0.4~2.5，与图片收藏那面墙同一口径：防一张横长条把整列撑出屏幕。
    val ratio = (post.cardRatio.takeIf { it > 0f } ?: tokens.spacing.coverAspectRatio)
        .coerceIn(0.4f, 2.5f)
    Card(
        // 用 miuix Card 的可长按重载而不是换了外观的 [VeneraCard]：这张卡是"图出血"的，
        // 换过去会多出一圈内边距 + 一层圆角，二级页那张大图就会跟着被裁掉一圈。
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInWindow() },
        onClick = { onOpen(bounds) },
        onLongPress = onLongPress,
    ) {
        VeneraCover(
            url = post.previewUrl,
            contentDescription = "${post.site.displayName} #${post.id}",
            // 宽高由外部 aspectRatio 定死，封面再自持 3:4 会在槽位里横向留缝（轮播那条同因）。
            preserveAspectRatio = false,
            mask = if (card.masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
            imageLoader = imageLoader,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio),
        ) {
            // 用户点名：卡片上**只**留左下角一个很小的来源胶囊，别的文字都不摆。
            GallerySourcePill(post.site.displayName)
            // 视频唯一的例外：不标出来，它就是一张"加载失败的图"（缩略图是站方的静帧 jpg）。
            // 摆右下角，免得和来源胶囊抢位置。
            if (post.isVideo) GalleryVideoPill(post.durationLabel)
            overlay?.invoke(this, post)
        }
    }
}

/**
 * 卡片左下角的来源胶囊。样式与视频角标共用 [GalleryCornerPill]。
 */
@Composable
private fun BoxScope.GallerySourcePill(name: String) {
    GalleryCornerPill(name, Alignment.BottomStart)
}

/**
 * 视频角标：一个「▶」，站方给了时长就带上秒数。
 *
 * 摆右下角，与 [GallerySourcePill] 分角不抢位。给视频打标不是装饰 ——
 * 站方那档 preview 是**静帧 jpg**，不打标它就是一张看着正常、点开什么也不会动的图，
 * 用户没法知道那张可以播。时长在 yande.re 那侧拿不到（实测字段表里没有），
 * 所以只在有的时候显示，不写"0″"。
 */
@Composable
private fun BoxScope.GalleryVideoPill(durationLabel: String) {
    GalleryCornerPill(if (durationLabel.isEmpty()) "▶" else "▶ $durationLabel", Alignment.BottomEnd)
}

/**
 * 卡片角上那枚小胶囊。
 *
 * 不复用 [com.venera.compose.components.venera.VeneraSourceBadge]：那枚是**左上角小圆角**、
 * 且被漫画侧 6 个页面共用，改它等于一起动别人。这里的口径是"胶囊 + 更小"，
 * 但**尺寸全部取现成 token**（badge 字号 / badge 内边距 / extraLarge 圆角），不新造数字。
 * 底板仍用固定深色：封面颜色不可控，只有固定底板能保证任何图上都可读。
 *
 * 刻意**不可点**：点下去要的是"只看这一站"，本仓还没有筛选态的出口，做了就是假按钮。
 */
@Composable
private fun BoxScope.GalleryCornerPill(text: String, alignment: Alignment) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.badge,
        fontWeight = tokens.type.weightMedium,
        color = StatusColors.OnBadgeSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(alignment)
            .padding(tokens.spacing.badgeInset)
            .background(
                StatusColors.BadgeSurface.copy(alpha = 0.72f),
                RoundedCornerShape(tokens.shape.extraLarge),
            )
            .padding(
                horizontal = tokens.spacing.badgeHorizontalPadding,
                vertical = tokens.spacing.badgeVerticalPadding / 2,
            ),
    )
}

/**
 * 只有一站给出内容时的说明条。放在网格首行、跟着滚走，不占固定空间。
 *
 * 颜色刻意不用 textTertiary：这一条是"两站混搭已经退化成单站"的**唯一**可见信号，
 * 灰到看不见就等于没有 —— 用户只会看到"怎么没有 donmai 的图"，而原因就在这一行里。
 */
@Composable
private fun GallerySourceNotice(text: String) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.caption,
        color = tokens.color.textSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spacing.space3),
    )
}

/**
 * 页尾那一行「已经到底了」。
 *
 * 样式照搜索页的 `ss-end`（overline + textTertiary + 居中），不另造口径。
 * 内容要报三件事：各站**实际摆上屏**几张、其中几个是视频、看的是哪一天；
 * 少掉的张数按成因分开报（规则 / 分级），因为这两件事用户能做的处置完全不同。
 *
 * 各站张数**含 0 也照报**：以前这一行读的是守卫之前的 `vm.posts`，
 * 于是"整站被一条规则清空"时它照样写出那站的张数、而屏上一张没有 —— 那是假读数，
 * 也正是这次定位真机反馈的唯一线索被抹掉的原因。
 *
 * ⚠️ **日期的措辞要小心**：那两站的"热门"口径不同（yande.re 是官方日榜，
 * Gelbooru 是 `sort:score` 的全站高分池、**没有按天的视图**）。
 * 所以这里写「$date 的热门」时不含站名 —— 一旦写成"Gelbooru 这一天的热门"，
 * 就是在描述一个站方根本不提供的东西。日期只对日榜那一路成立。
 */
@Composable
private fun GalleryFeedEnd(wall: GalleryWall, date: String) {
    val tokens = VeneraTokens
    val perSite = GallerySite.entries.joinToString(" · ") { site ->
        "${site.displayName} ${wall.cards.count { it.post.site == site }}"
    }
    val videos = wall.cards.count { it.post.isVideo }
    Text(
        text = buildString {
            append(if (date.isNotBlank()) "${date} 的热门已全部显示" else "当天热门已全部显示")
            append("（$perSite")
            if (videos > 0) append("，含 $videos 个视频")
            if (wall.blockedCount > 0) {
                append("；另有 ${wall.blockedCount} 张命中屏蔽规则 ${wall.blockedRules.joinToString("、")}")
            }
            if (wall.hiddenByRating > 0) {
                append("；${wall.hiddenByRating} 张按「成人内容处理」收起")
            }
            append("）")
        },
        fontSize = tokens.type.overline,
        color = tokens.color.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space6),
    )
}

/**
 * 画廊主 Tab 的两页。枚举顺序即分段器的段序，也是 pager 的页序。
 *
 * `Daily` 是**落地档**：冷启动固定落这一页（用户 2026-09-28 拍板），推荐页要首次可见才取数 ——
 * 冷启动不该为了一个可能整片是空的页面去抢日榜那一屏的带宽与 12s 预算。
 */
internal enum class GalleryPage { Daily, ForYou }

/**
 * 两页的切换器 —— 挂在顶栏玻璃层的 `bottomContent` 里，不在内容里。
 *
 * 这是收藏页 2026-09-27 那次拍板定下的口径（"二级分段器必须挂顶栏 chrome"），
 * 几何也**逐字照抄**那一处：手机档整条宽 = 单段 25% × 段数（那 25% 的原始出处是用户
 * 对**两段**控制器说的"整条 50% 居中"，我们正好两段），宽屏档全宽。
 * 段高 = `segmentedHeight` / 宽屏 `segmentedHeightWide`，上下各 `space3`。
 *
 * ⚠️ 这一行**不许涂不透明底**：玻璃顶栏的模糊是采样层，涂实心底会在标题下面拉出一条
 * 横贯屏幕的硬边（收藏页真机撞过，见记忆「玻璃顶栏上的内联展开区三条硬约束」）。
 */
@Composable
private fun GalleryPageTabs(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val tokens = VeneraTokens
    val options = remember { GalleryPage.entries.map { it.label } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space3),
        contentAlignment = Alignment.Center,
    ) {
        VeneraSegmentedButton(
            options = options,
            selectedIndex = selected,
            onSelect = onSelect,
            modifier = if (isWideScreen(LocalConfiguration.current.screenWidthDp.dp)) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.fillMaxWidth(segmentedCellWidthFraction * options.size)
            },
        )
    }
}

/** 手机档分段控制器**单段**占屏宽的比例。与收藏页同一口径（`FavoritesScreen` 同名常量的出处）。 */
private const val segmentedCellWidthFraction = 0.25f

internal val GalleryPage.label: String
    get() = when (this) {
        GalleryPage.Daily -> "每日推荐"
        GalleryPage.ForYou -> "猜你喜欢"
    }

/**
 * 第 0 页 = 每日推荐（两站上一天热门混合打乱）。
 *
 * 这一层是从前 `GalleryScreen` 那个 `when` 里的四支**原样搬过来**的：加载环、错误重试、
 * "被规则挡完了"那档空态、以及那面墙与它的页尾。搬的理由不是省事，是**判据必须各页自持** ——
 * 留在宿主层就会出现"屏上是推荐页、页尾还写着日榜那一天"这种错配。
 */
@Composable
internal fun GalleryDailyPage(
    hasPosts: Boolean,
    wall: GalleryWall,
    date: String,
    sourceNotice: String?,
    isLoading: Boolean,
    error: String?,
    imageLoader: ImageLoader,
    columnCount: Int,
    state: LazyStaggeredGridState,
    topPadding: Dp,
    backdrop: LayerBackdrop?,
    onOpen: (GalleryPost) -> Unit,
    onRetry: () -> Unit,
) {
    val tokens = VeneraTokens
    val cards = wall.cards
    val contentPadding = PaddingValues(
        start = tokens.spacing.screenHorizontal,
        end = tokens.spacing.screenHorizontal,
        // 顶栏避让地板 + 当前挂在顶栏里那一条的高度（都跟着动画，见 gridTopPadding）。
        top = topPadding,
        bottom = VeneraSpacing.bottomBarClearance,
    )
    val wallModifier = Modifier
        .fillMaxSize()
        .blurBackdropSource(backdrop)
    when {
        isLoading && !hasPosts -> Box(wallModifier, contentAlignment = Alignment.Center) {
            // 整页级加载统一走 M3 Expressive 波浪环（全站口径，不用 material 的转圈）。
            CircularWavyProgressIndicator(
                modifier = Modifier.size(tokens.spacing.loaderPage),
                color = tokens.color.primary,
                trackColor = tokens.color.surfaceVariant,
            )
        }

        error != null && !hasPosts -> Box(
            wallModifier.padding(horizontal = tokens.spacing.screenHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            VeneraEmptyView(
                title = "最新流加载失败",
                message = error,
                icon = Icons.Outlined.Image,
                actionText = "重试",
                onAction = onRetry,
            )
        }

        cards.isEmpty() && !isLoading -> Box(
            wallModifier.padding(horizontal = tokens.spacing.screenHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            // 刻意不说"没有图"：两站的日榜这一轮明明有内容
            // （实测 yande.re 40 条、Gelbooru 100 条），是被用户的规则或分级模式剔完了。
            // 报成空态就是在掩盖判定链的效果（假空态），所以把**是哪几条规则**念出来。
            VeneraEmptyView(
                title = "这一屏被你的规则挡完了",
                message = buildString {
                    if (wall.blockedRules.isNotEmpty()) {
                        append("命中屏蔽规则：${wall.blockedRules.joinToString("、")}")
                        append("。图站的 tag 是 `long_hair`、`tail` 这类下划线标识符，")
                        append("短关键字很容易整站命中 —— 想看到内容就把那条规则收掉或改长。")
                    } else {
                        append("「成人内容处理」选了「彻底隐藏」，这一轮的图全被判为成人内容。")
                        append("想看到内容，去设置里改成「封面打码」或「不过滤」。")
                    }
                    if (sourceNotice != null) append("\n$sourceNotice")
                },
                icon = Icons.Outlined.Image,
            )
        }

        else -> GalleryCardsGrid(
            cards = cards,
            imageLoader = imageLoader,
            columnCount = columnCount,
            state = state,
            contentPadding = contentPadding,
            modifier = wallModifier,
            onOpen = onOpen,
            header = sourceNotice?.let { notice ->
                // 只有一站给上内容时把缺的那站说出来 —— 静默退化成单源最难被发现。
                @Composable { GallerySourceNotice(notice) }
            },
            footer = if (hasPosts) {
                @Composable { GalleryFeedEnd(wall, date) }
            } else {
                null
            },
        )
    }
}
