package com.venera.compose.gallery.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.venera.compose.components.VeneraEmptyTone
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.rememberImageWallColumnCount
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.selection.SelectableCardFrame
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraGallerySourceMark
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.data.network.HostCircuitBreaker
import com.venera.compose.gallery.data.GalleryArtistFollowsStore
import com.venera.compose.gallery.data.GalleryFavoritesStore
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.openGalleryArtistProfile
import com.venera.compose.gallery.domain.GalleryChromeHidePolicy
import com.venera.compose.gallery.domain.GalleryFeedSource
import com.venera.compose.gallery.domain.GalleryFollowedArtists
import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.domain.GalleryGuard
import com.venera.compose.gallery.domain.GalleryRecommendations
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GalleryWallFeed
import com.venera.compose.gallery.data.GalleryPorts
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
import com.venera.compose.ui.tokens.LocalBottomBarClearance

/**
 * 画廊主 Tab（第 4 位，搜索右侧）。
 *
 * ## 2026-09-30 第二轮的形态（用户按新版效果图点名的信息架构）
 *
 * 一屏自上而下：
 * 1. 顶栏大标题「画廊」+ 常驻 chrome（搜索入口条 + 来源分段器，见 [GalleryHomeChrome]）；
 * 2. 三节恒定项：收藏里的画师（圆形头像）→ 每日热门**预览行**（「查看全部」进二级页）→
 *    「猜你喜欢」节头；
 * 3. 主墙**恒为猜你喜欢**的瀑布流。
 *
 * 每日热门的全量与「换一批」在**独立二级页**（`GalleryDailyRoute` / [GalleryDailyScreen]），
 * 所以首页不再有"装的是哪一批"的状态（那个二选一 `wallFeed` 整枚撤下），
 * 而热门那一批的数据仍然由 [GalleryViewModel] 拉 —— 首页预览行要用它。
 *
 * 取数仍是**两站热门各 20 张打乱**：yande.re 走官方日榜 `popular_recent?period=1d`，
 * Gelbooru 没有日榜端点、走 `sort:score:desc` 的全站高分池（两站口径的差别见 `GalleryFeedSource`），
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
    /**
     * 「每日热门 → 查看全部」：进那面墙的**二级页**看全量与换一批。
     *
     * 首页这一节从此只是**预览**（一行横卡），全量在二级页 —— 用户 2026-09-30 的拍板。
     * 二级页是独立路由（`GalleryDailyRoute`），所以这里交出去的是一个导航动作，
     * 而不是"在页内切成另一面墙"（后者会让返回语义变成页内状态机）。
     */
    onOpenDailyAll: () -> Unit,
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
    // 猜你喜欢（首页主墙）的状态（分页、双源、可单站缺席）与日榜分开两份 —— 混在一起就是两套
    // 状态机互相清（搜索层当年分出去也是同一条理由）。为什么不复用 svm，见该类头注。
    val fvm: GalleryForYouViewModel = viewModel()

    // 系统返回**逐级**：反搜 → 展开态收成一条（接着看图）→ 上一轮搜索上下文 → 关掉整个搜索。
    //
    // 2026-09-28 之前这里只有两档、而且最后一档就是 `closeSearch()`：搜了 touhou → 点进大图 →
    // 点一枚画师标签回来 → 按一次返回掉回首页，touhou 那一轮再也回不去。
    // 原因是两条按设计执行的语义叠在一起：交接把上一轮**覆盖**了，返回又一格到底。
    // 现在上一轮压在 `svm.contexts` 里，返回先弹它（不发请求、不重转圈）。
    // 展开着、而且一枚条件都没有时退无可退，直接关掉 —— 否则会剩一张空搜索卡停在一屏内容上，
    // 用户不知道刚才那一下干了什么。
    //
    // 注意：**键盘起着时返回键被输入法吃掉**，那种返回落不到这里 ——
    // 那一档由搜索区自己看着 IME 收起去收条（见 `GallerySearchArea`）。
    // chips 与结果**不清**：再点搜索图标回到原上下文，不用重新搜（底下压着的那几轮也一并还在，
    // 顶栏那枚 ✕ 只关这一层、不清栈）。
    BackHandler(enabled = svm.active) {
        when {
            // 反搜排在最前：它开着时屏上是"一张图 → 一批相似图"，返回要先退回标签搜索
            // 那一具身体，而不是一下子把人丢回首页。
            rvm.open -> rvm.closeLayer()
            svm.mode == GallerySearchMode.INPUT && svm.filters.isNotEmpty() -> svm.collapseToResults()
            // 上一轮（"搜 touhou 那会儿的 100 张"）：整片从快照抄回来。
            svm.canPopContext() -> svm.popContext()
            else -> svm.closeSearch()
        }
    }

    // 大图页里点了某枚标签（跨 Activity 的交接槽，见 [GallerySearchHandoff]）：
    // 切进搜索态、按那枚标签直接开搜。key 读的是快照字段，所以写入方在另一个 Activity 里
    // 也能立刻把这边叫醒 —— 不靠 onResume（大图页是透明窗，底下这屏只是 paused）。
    //
    // 这条链 2026-09-28 被报过"返回后回到画廊首页、回不到最初那串标签"，当时装了只读探针取现场。
    // 读数证明 ViewModel 全程同一个实例、每次写入都有消费 —— 但**据此判"缺陷没重现出来、
    // 没有代码要改"是错的**：病因从来不是链断，而是这一句把上一轮**整片覆盖**
    // （`filters=[touhou] results=100` → `filters=[wowoguni] results=0`，同一个实例、没换 VM），
    // 再叠上返回那一格到底。那张"四种病因 ↔ 四种读数形状"表仍留在
    // `gallery-tag-category-translation-and-nav-2026-09.md` §7.4，当作再犯时的取证手册
    // （但它只答"链断在哪一环"，答不了"链通着而行为不对" —— 后者要读状态跳变）。
    LaunchedEffect(GallerySearchHandoff.pending) {
        val request = GallerySearchHandoff.consume() ?: return@LaunchedEffect
        // chips 装的是**这一轮**那一串（那是另一张图带来的上下文，留着旧的会让用户以为搜的
        // 还是刚才那串），而上一轮**压进栈里**、不丢 —— 见 GallerySearchViewModel.openContext。
        // 切站 + 装条件 + 开搜是**一步**（见 acceptHandoff）：
        // 本轮的换站会顺手重搜一次，拆成 setSite + setFilters 就会先拿旧条件在新站发一笔，
        // 同一轮两个请求。
        svm.acceptHandoff(request.site, request.tags)
    }

    val source = remember { GalleryFeedSource.getInstance(context) }
    // 取图走画廊自己的 ImageLoader：独立目录 + 独立预算，不挤漫画侧的封面缓存。
    val imageLoader = remember { GalleryImageLoader.get(context) }

    // 分级与屏蔽**只读共享**漫画侧那一份，不新造开关（理由见 GalleryGuard）。
    val guard = remember { GalleryPorts.of(context).contentGuard }
    val maskMode by guard.nsfwMaskMode.collectAsState()
    // 反搜要的是**同一把**分级判据，不是第二份开关：「成人内容处理」选了不过滤，
    // 才让 SauceNAO 把成人内容也带回来（站方的 `hide` 参数与解析期的 hidden 过滤两道同源）。
    val allowNsfw = maskMode == "OFF"
    val rules by guard.rules.collectAsState()
    // 画廊**自己那把** AI 屏蔽开关（批次 C2，用户拍板"画廊和漫画分开"）。
    // 与守卫页的 block_ai 是两枚开关、共用一份词表（判据在 GalleryAi）；
    // 它进的是三面墙的同一个 remember 键 —— 键里不带上它，改完开关要重进页面才生效，就是假开关。
    val blockAi by GalleryPorts.of(context).prefs.galleryBlockAi.collectAsState()

    /**
     * 「根据你的收藏」的标签集。判据全在 `GalleryRecommendations`（纯函数、有单测），
     * 这里只做三件事：喂收藏、注入**与那面墙同一把**屏蔽判据、把种子交下去。
     *
     * ⚠️ 种子读 [GalleryForYouViewModel.seed]（不是日榜那颗）：推荐与猜你喜欢那条墙是
     * **同一次抽样**，不同源就会出现"卡片上写着 A 串、点进去那面墙是 B 串"。
     * 从前它读 `vm.seed`，于是「换一批日榜」会把搜索卡里那行推荐标签一起换掉 ——
     * 用户从没要求过这个联动，而且它正是下面那句"返回时组合重建会重跑出一批不一样的推荐标签"
     * 要防的东西的另一半：真正该防的是**没有任何用户动作就变**。
     *
     * 2026-09-30 第二轮：首页那排展示栏撤了（用户点名"去掉根据你的收藏这个栏"），
     * 但这份数据**照留** —— 它是猜你喜欢那条墙的取数种子，也是搜索卡空框态那行推荐的来路。
     */
    val galleryFavorites by GalleryFavoritesStore.getInstance(context).favorites.collectAsState()
    val recommendations = remember(galleryFavorites, rules, fvm.seed) {
        GalleryRecommendations.recommendBySite(galleryFavorites, seed = fvm.seed) { tag ->
            guard.blockedGalleryRule(author = "", tags = listOf(tag)) != null
        }
    }

    // 搜索结果那面墙用的滚动状态。
    //
    // 2026-09-30 第二轮：原来这里还有第二把（`dailyGridState`，给"日榜那一面墙"用），
    // 现在整条主墙是猜你喜欢、日榜去了二级页（那边自带一把，见 `GalleryDailyScreen`），
    // 所以首页只剩这两把：搜索结果一把、猜你喜欢一把。
    // ⚠️ 两把**不能合并**：节头进墙之后"第几项"不再等于"第几张卡"，而
    // `recordScroll` 与 `scrollToItem` 用的都是 item index —— 共用一把就会拿节头的序号
    // 去恢复搜索的落点（那条既有观感："从搜索结果退回主墙，回到的是主墙自己离开时的位置"）。
    val gridState = rememberLazyStaggeredGridState()
    val forYouGridState = rememberLazyStaggeredGridState()
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 列数按窗口宽自适应：每列预算 200dp（照详情页预览格那把 master 原值），手机档恒 2 列不参与计算。
    // 历史：这一档原本"手机 2 / 平板 3"两值定死（3 是当时用户反馈「平板上两列偏大」定的），
    // 但侧栏落地后宽窗仍恒排 3 列 ⇒ 一格 478dp，2026-10-02 用户再报「图片卡片过大」才改成自适应。
    // 「画廊设置 → 网格列数」的 TWO/THREE 仍是显式覆盖，只有 AUTO 走自适应。
    val columnMode by GalleryPorts.of(context).prefs.galleryColumnMode.collectAsState()
    val columnCount = GallerySettingsModel.gridColumns(columnMode, rememberImageWallColumnCount())

    // ── 下滑收起两栏（2026-09-30，用户点名；两枚开关默认关）──
    val hideTopBarOnScroll by GalleryPorts.of(context).prefs.galleryHideTopBar.collectAsState()
    val hideBottomBarOnScroll by GalleryPorts.of(context).prefs.galleryHideBottomBar.collectAsState()
    // 开关写进跨层状态：**底栏在壳层**，它读不到页面里的局部变量（见 GalleryChromeAutoHide）。
    // 用 SideEffect 而不是在组合期直接赋值：组合期写快照状态会触发"在组合中写状态"的重组循环。
    SideEffect {
        GalleryChromeAutoHide.topEnabled = hideTopBarOnScroll
        GalleryChromeAutoHide.bottomEnabled = hideBottomBarOnScroll
    }
    // 离开画廊（切 Tab / 进二级页 / 这一屏被移出组合）时**必须复位**：
    // 底栏是壳层的，`hidden` 留在 true 会让用户在别的 Tab 上也看不到它，
    // 而那时这一屏已经不在组合里，没有任何人会去清（理由见 GalleryChromeAutoHide）。
    DisposableEffect(Unit) { onDispose { GalleryChromeAutoHide.reset() } }

    // 收栏阈值 = 常驻 chrome 的高度（"滑过搜索框的位置"）。
    // 提前到这里算：滚动判据与网格避让读同一个值，两处不会漂。
    val chromeHeight = galleryHomeChromeHeight()
    val hideThresholdPx = with(LocalDensity.current) { chromeHeight.toPx() }
    // 累计位移带着走（不用 remember：它只服务于下一次判定，丢了只是"要从头累计"）。
    var scrollAccumulated by remember { mutableStateOf(0f) }
    /**
     * 「此刻两栏收没收」—— 页内滚动判据写进 [GalleryChromeAutoHide]，
     * 壳层（底栏）与页内（顶栏）都读它。
     */
    val chromeHidden = GalleryChromeAutoHide.hidden
    val chromeHideConnection = remember(hideThresholdPx) {
        object : NestedScrollConnection {
            // ⚠️ 必须是 **onPreScroll**（2026-09-30 第三轮修的缺陷，原话「上下滑收起顶和底栏没有效果」）。
            //
            // 原来这里挂的是 `onPostScroll(consumed, available)` 读 `available.y` —— 那是
            // **子级消费完之后剩下的那一截**：瀑布流只要还能滚就把整笔位移吃干净，
            // 到这里恒为 0，判据每帧拿到 `delta = 0`，累计永远到不了阈值 ⇒ 日常滑动**整条判据不参与**。
            // 只有滚到两端还继续推时才有非 0 值（顶部往下推、底部往上推），日常手势碰不到 ——
            // 这就是用户读到的"没有效果"。
            //
            // pre 段的 `available` 才是这一笔的真实位移。
            //
            // ⚠️ 符号（2026-09-30 从 material3 源码量的，别再抄回去）：**负 = 手指向上滑（往下浏览）
            // ⇒ 该收起**，**正 = 手指向下滑（回滚）⇒ 该展开**。这里曾经写成"正 = 手指向上推 = 下滑"，
            // 于是整条链做反，真机读起来就是"上滑收起搞反了"。依据见 GalleryChromeHidePolicy 类头注。
            // 本连接恒回 Offset.Zero，不抢任何位移，所以它对顶栏折叠与瀑布流都是透明的。
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta != 0f) {
                    val (hidden, accumulated) = GalleryChromeHidePolicy.afterScroll(
                        hidden = GalleryChromeAutoHide.hidden,
                        accumulated = scrollAccumulated,
                        delta = delta,
                        threshold = hideThresholdPx,
                    )
                    scrollAccumulated = accumulated
                    // 只有真的变了才写快照状态：每帧写会白白让壳层重组（底栏那一支）。
                    if (hidden != GalleryChromeAutoHide.hidden) GalleryChromeAutoHide.hidden = hidden
                }
                return Offset.Zero
            }
        }
    }

    /**
     * 取一次两站的热门池并合成整屏（两站口径不同，见 GalleryFeedSource）。
     *
     * 只有这一条路径，没有"加载更多"：日榜是一屏到底的固定池子（实测 yande.re 固定 40 条、
     * `page`/`limit` 被忽略；Gelbooru 100 条里抽 20）。
     *
     * ⚠️ 2026-09-30 第二轮：首页**只**用这一批摆一行预览横卡，
     * 全量在二级页（[GalleryDailyScreen]，那边复用同一个 [GalleryViewModel]）。
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
                            // 原始池一起存下：二级页的「换一批」靠它做**本地**重排（零请求）。
                            pools = daily.pools,
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

    // ⚠️ 2026-09-30 第二轮：这里原来还有一个 `remixDaily()`（"日榜那面墙的换一批"，
    // 零请求本地重排 + 池子不够就真取）。首页现在只有猜你喜欢一面墙，而日榜的「换一批」
    // 搬到了它的二级页（`GalleryDailyScreen.remix`，那边同样走 `vm.remix()` 这条零请求路径）。
    // 撤掉它不是因为不好用，而是**首页已经没有能让用户看见这一下的地方**了。

    /** [forceNextLoad] 的猜你喜欢版：只描述"这一次 effect 触发是不是用户点的"。 */
    var forceNextForYouLoad by remember { mutableStateOf(false) }

    /**
     * 首页主墙（猜你喜欢）的「换一批 / 重试」。
     *
     * 与 [retryFromUser] 同一条结构：先清熔断（否则按下去必然还是那句"熔断中，Ns 后重试"，
     * 那是假按钮），再置 force 让这一轮忽略残留的在途标志，最后换种子重抽标签。
     *
     * ⚠️ 与日榜那头**共用那两个 host 的熔断状态** —— 在这一侧清熔断，等于也把那两站的
     * 60 秒快败窗口关掉了。这一处刻意不回避：那头本来也只有"用户主动重试"才清。
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
        // 冷启动那一屏是**今天**存的缓存 → 不联网（用户 2026-09-29：「同一天之内不再自动联网，
        // 要新内容靠下拉或那枚刷新」—— 判据与"补拉不清屏"都在 GalleryFeedRefreshPolicy）。
        if (!force && !vm.shouldAutoLoad()) return@LaunchedEffect
        loadDaily(force = force)
    }

    // 猜你喜欢（首页主墙）的取数：**首页一进来就取**（它就是主墙，没有"滑到那一页才取"的前提了）。
    // 条目组合重建时 loadedKey 守卫放行第二次拉（与日榜 `LaunchedEffect(vm.refreshTick)` 同一条）。
    //
    // ⚠️ 原来这里只有 loadedKey 一道守卫，而它**挡不住切 Tab**：切走时这条目的地是被 pop 掉的
    // （`gotoTab` 的 `popUpTo{saveState=true}`），VM 随之清空，新实例的 loadedKey 恒为 null
    // ⇒ 每次回到画廊都真发一笔，而且 `fvm.seed` 是新值、推荐标签整批重抽 —— 用户读到的
    // 就是"整个页面重新加载、内容还换了"。日榜那头 09-29 配过的同日门，这一屏当时漏了。
    // 补的就是那道门：铺着今天的快照就不联网，要新内容靠「换一批」。
    //
    // ⚠️ 原来这里还要等日榜先落地（两页各抢各的带宽）。现在主墙就是它：
    // 猜你喜欢**先**取，日榜（预览行）随后 —— 顺序反过来才符合"进屏第一眼是主墙"。
    LaunchedEffect(fvm.refreshTick) {
        if (fvm.loadedKey == "foryou#${fvm.refreshTick}|${fvm.seed}" && fvm.posts.isNotEmpty()) {
            return@LaunchedEffect
        }
        val force = forceNextForYouLoad
        forceNextForYouLoad = false
        if (!force && !fvm.shouldAutoLoad()) return@LaunchedEffect
        fvm.load(recommendations, force = force)
    }

    // 新一轮结果落地 → 滚回顶部。不滚的后果与搜索页那条一样：用户落在上一轮的深处，
    // 而续页判据会当场认为已经到底。信号用 [GalleryForYouViewModel.generation]，不用 posts.size。
    //
    // ⚠️ 2026-09-30 第二轮：这里原来还带一个 [`wallFeed`] 键与"不是猜你喜欢那一面就跳过"
    // 的守卫 —— 那是"两面墙共用一屏、只画当前那一面"时代的产物。现在首页那条主墙**恒为**
    // 猜你喜欢（每日热门搬去了二级页），所以那个守卫恒真、那个键恒不变：
    // 留着它只会让下一个人以为"这一屏还可能不是猜你喜欢"。
    var forYouScrolledGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(fvm.generation) {
        if (fvm.generation == 0 || forYouScrolledGeneration == fvm.generation) return@LaunchedEffect
        forYouGridState.scrollToItem(0)
        forYouScrolledGeneration = fvm.generation
    }

    // 滑到底取下一页 —— **每面墙各一份判据**：搜索那一份整块硬编码读 `svm`，套不过来。
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

    // 与其他主 Tab 完全一致的顶栏避让地板（口径出处见冻结声明第七轮）。
    val topBarFloor = statusBarTop + VeneraSpacing.topBarFloor

    /**
     * 网格的顶部避让 = 顶栏展开地板。
     *
     * 2026-09-30 第三版：chrome 挪进墙里当第一行后，避让**只剩顶栏地板**这一支 ——
     * chrome 的高度由它自己在网格里占位，不再从 padding 里扣。
     * （第二轮那版"地板 + chrome 高度"是 chrome 当浮层时的口径，浮层已撤。）
     */
    val gridTopPadding = topBarFloor

    /**
     * 搜索浮层的纵向落点 = 常驻 chrome 上边缘（状态栏之下、顶栏大标题之下）。
     *
     * chrome 现在是墙里的第一行内容（2026-09-30 第三版），首屏时它的上边缘恰好在
     * [topBarFloor] —— 卡片弹出来时**正好盖住**入口条的位置，"从入口条原地长开"的语义不变。
     * 顶栏展开高度用与各主 Tab 同一条地板常量（`statusBarTop + 104.dp` 那一档里的 104dp），
     * 不另量（同一处理由：`heightOffsetLimit` 读不可靠）。
     */
    val searchOverlayTop = topBarFloor

    /**
     * 顶栏"已经折平"的判据 —— 到了这个点才把结果态那张搜索卡收走。
     *
     * 取 0.99 而不是 1：折叠进度是 `heightOffset / heightOffsetLimit`，滚到深处被夹在 1，
     * 但那是浮点除法，边界上可能停在 0.9999…。留一丝余量，语义上仍然是"完全折叠"。
     * 之所以要"完全折叠"而不取中间值，见 [GalleryScreen] 里 [searchCollapsed] 那段注释。
     */
    val topBarState = topBarBehavior.state
    val searchCollapsed by remember(topBarState) {
        derivedStateOf {
            svm.active &&
                svm.mode == GallerySearchMode.RESULTS &&
                topBarState.collapsedFraction >= COLLAPSE_SETTLED_FRACTION
        }
    }

    val searchWall = remember(svm.results, maskMode, rules, blockAi) {
        buildGalleryWall(svm.results, maskMode, blockAi = blockAi) { post ->
            guard.blockedGalleryRule(author = post.author, tags = post.tagList)?.pattern
        }
    }
    // 猜你喜欢（首页主墙）**同一把判据**（`buildGalleryWall`）：否则会出现"首页预览上没被挡、
    // 主墙上全裸"的分叉 —— 同一张图在同一个页面的两处待遇不同，是最难解释的一种不一致。
    val forYouWall = remember(fvm.posts, maskMode, rules, blockAi) {
        buildGalleryWall(fvm.posts, maskMode, blockAi = blockAi) { post ->
            guard.blockedGalleryRule(author = post.author, tags = post.tagList)?.pattern
        }
    }
    // 日榜那一批（首页预览行 + 二级页全量）**同一把判据**：预览上没被挡的图，
    // 点进二级页也不能变成裸的（同一张图在同一页面的两处待遇不同）。
    val wall = remember(vm.posts, maskMode, rules, blockAi) {
        buildGalleryWall(vm.posts, maskMode, blockAi = blockAi) { post ->
            guard.blockedGalleryRule(author = post.author, tags = post.tagList)?.pattern
        }
    }

    // 首页三节（关注的画师 / 热门预览 / 猜你喜欢节头）。**恒为三节**（理由见 galleryHomeSections）。
    //
    // 这一栏 2026-09-30 从「收藏里的画师」换成「正在关注的画师」（批次 L · L11，用户拍板）：
    // 换名的前提是那之前有了关注名单存储，"关注"这两个字第一次配得上一个真读数。
    // 卡面与张数仍从收藏里取 —— 名单只存「站别 + 名字」，不为此多发任何请求。
    val followsStore = remember(context) { GalleryArtistFollowsStore.getInstance(context) }
    val follows by followsStore.follows.collectAsState()
    // 名单档读不出来时这一栏只剩空引导，屏上必须说得出为什么空 —— 那份留档改名其实已经做了，
    // 只是不说就没人知道去哪儿找回（读走即清，只说一次）。
    LaunchedEffect(Unit) {
        followsStore.consumeNotice()?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    val artists = remember(follows, galleryFavorites) {
        GalleryFollowedArtists.rowOf(follows, galleryFavorites)
    }
    val homeSections = galleryHomeSections(
        daily = wall.cards,
        dailyLoading = vm.isLoadingFeed,
        onOpenDaily = { post ->
            GalleryViewerQueue.set(wall.cards.map { it.post })
            onOpenPost(post.site, post.id)
        },
        artists = artists,
        // 点一枚画师 = 拿这个名字另起一轮搜索（走与大图页交接同一个入口：上一轮压栈、返回能回来）。
        // 跨站合并之后（批次 M · M5）：卡上并排两枚徽标的那位，点进去要两站一起搜 ——
        // 只搜一站就会出现"卡上摆两枚、结果只有一站的图"。只有一枚的仍只搜那一站。
        onPickArtist = { artist ->
            val only = artist.sites.singleOrNull()
            if (only == null) svm.acceptHandoffAllSites(listOf(artist.name))
            else svm.acceptHandoff(only, listOf(artist.name))
        },
        // 长按 = 进介绍页（批次 Q+R · 批量 4）。跨站合并的那位在介绍页只走**一站**：
        // 那一页的关注关系、别名与外链都是按站取的（一站一条，页上明写站点徽标），
        // 而"两个站并排摆在一页里"没有任何一处判据说该怎么合。取第一个站，页上就标第一个站。
        onOpenArtistProfile = { artist ->
            artist.sites.firstOrNull()?.let { site ->
                (context as? android.app.Activity)?.openGalleryArtistProfile(site, artist.name)
            }
        },
        // 「查看全部」= 进每日热门二级页（首页这一行只是预览，全量与「换一批」都在那一页）。
        onOpenDailyAll = onOpenDailyAll,
        // 猜你喜欢节头那枚「换一批」（这一节本身不摆卡片：主墙就在它下面）。
        onRemixForYou = { retryForYouFromUser() },
        // 重取期间那枚轻指示（用户 2026-09-30：「换一批不要整个页面刷新」——
        // 三节与上一批卡片都留着，"正在换"这件事由节头这枚环去说）。
        forYouLoading = fvm.isLoading,
        imageLoader = imageLoader,
    )

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
    // （`searchWallActive` 的定义搬到了上面那几个 remember 之后，因为三个墙都要用同一句判据。）
    val searching = svm.active && svm.filters.isNotEmpty()
    // 结果墙只在「这一轮真的取过（page > 0）或正在取」时接管。条件刚装好、还没搜时仍显示主墙 ——
    // 否则会摆出一屏"0 张"的假空态（不是没有结果，是还没搜）。
    val searchWallActive = searching && (svm.page > 0 || svm.isSearching)

    // 页尾那一行：搜索与猜你喜欢各一句，**类型显式写出来**（`when` 里混 `@Composable {}` 字面量
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
                // 排法与日期区间一起念：切了档而页尾读不出区别，用户只会怀疑那一下没生效。
                ranking = svm.rankingReading(),
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
    // 放在宿主层就会出现"屏上是主墙、页尾还写着日榜那天"的错配）。

    // 搜索的滚动记录 / 恢复（`recordScroll` 与 `scrollToItem` 用的都是 item index）。
    // ⚠️ 键里必须带上"这面墙是不是活的"：搜索墙不接管时它根本不在布局，
    // 那一次滚动能真跑。主墙恒在（2026-09-30 起），所以这条判据只对搜索墙有意义。
    LaunchedEffect(gridState) {
        snapshotFlow {
            val wallActive = svm.active && svm.filters.isNotEmpty() && (svm.page > 0 || svm.isSearching)
            wallActive to (gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0)
        }
            .distinctUntilChanged()
            .collect { (wallActive, index) ->
                if (!wallActive) return@collect
                svm.recordScroll(index)
            }
    }
    // 新一轮结果落地 → 滚回**该去的那一格**。不滚的后果与上面那条一样。
    // 只有"这面墙此刻在布局里"才滚：搜索墙不接管时 `scrollToItem` 是空操作，
    // 而那一次落点本来就该由下一次接管时的 [consumeScrollRestore] 去还。
    //
    // ⚠️ [scrolledGeneration] 是"已回顶凭据"（续页判据用它挡"旧位置在深处"就顺手取第 2 页），
    // 所以它必须在这里声明、两个 effect 共读 —— 挪进哪个 effect 里，另一个就读不到了。
    var scrolledGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(svm.searchGeneration) {
        if (svm.searchGeneration == 0) return@LaunchedEffect
        if (!(svm.active && svm.filters.isNotEmpty())) return@LaunchedEffect
        gridState.scrollToItem(svm.consumeScrollRestore() ?: 0)
        scrolledGeneration = svm.searchGeneration
    }

    // 结果滚到离底几张以内就续下一页（搜索那面墙）。
    // 三道闸门原样保留（没滚回顶部不判、续页刚失败不自动重试、在取时不并发）。
    LaunchedEffect(gridState) {
        snapshotFlow {
            val wallActive = svm.active && svm.filters.isNotEmpty() && (svm.page > 0 || svm.isSearching)
            val info = gridState.layoutInfo
            GalleryLoadMoreState(
                wallActive = wallActive,
                exhausted = svm.exhausted,
                busy = svm.isSearching || svm.isLoadingMore,
                failed = svm.loadMoreError != null,
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

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // 反搜开着 → 这一屏摆的是"这张图像哪些图"。它与主墙 / 标签搜索互斥，
            // 所以必须排在所有分支**最前**：否则主墙会在它底下同时存在，
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
            // 从大图页点标签交接过来时这一帧最容易撞见，所以给整页级波浪环。
            searchWallActive && searchWall.cards.isEmpty() && svm.isSearching -> Box(
                // 顶栏避让与网格同一条口径：搜索浮层锚在它的位置上，这一屏的中心得落在**它下面**，
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
                // 中心不按整屏算：展开着的搜索浮层 + 键盘会把整屏中心推得很低，按钮就够不着了。
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
                        // 窗口内 0 条 ≠ 这一串标签搜不到东西（实测两站对不认识的伪标签也回空表），
                        // 所以设了窗口的档要用另一句读数，免得用户去怀疑自己写的标签。
                        // 历史期那句不能说"新增"：2024 年 3 月那一月里搜不到东西，与"本期没有新增"是两件事。
                        svm.rankingWindowActive() ->
                            if (svm.periodAnchor == null) "这一档里没有新增命中" else "这一期里没有命中的图"
                        else -> "这一串标签没有可摆的图"
                    },
                    message = svm.searchError
                        ?: listOfNotNull(
                            "条件：${GallerySearch.queryOf(svm.effectiveFilters())}",
                            // 排行档连日期区间一起念 —— "本周"不带日期就是说不出对错的读数。
                            svm.rankingReading()?.let { "排行：$it" },
                            svm.budgetTrimNotice(),
                            svm.noImageNotice(),
                            // 有一条腿没跑起来而另一条回了 0 张：空态必须点名是哪条腿。
                            // 只写"这一串标签没有可摆的图"就是把"我们没问到"说成"站里没有"。
                            svm.legFailureNotice(),
                            // 换过名还是 0 张也要说：否则用户读到的是"这个名字没图"，
                            // 而真原因是"站方把它记成另一个名字的别名，那个名字也没图"。
                            svm.artistAliasNotice,
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
                    bottom = LocalBottomBarClearance.current,
                ),
                modifier = Modifier
                    .nestedScroll(topBarBehavior.nestedScrollConnection)
                    .blurBackdropSource(topBarBackdrop),
                onOpen = { post ->
                    // 左右翻页的交接：把**这一面墙真正摆出来的那批**（已过屏蔽 / 分级判据）
                    // 交给大图页，顺序即屏上顺序。搜索结果、主墙、二级页三面墙各自交各自的队列，
                    // 所以三处都有左右翻，且翻页范围不会跨到另一面墙去。
                    GalleryViewerQueue.set(searchWall.cards.map { it.post })
                    onOpenPost(post.site, post.id)
                },
                footer = searchFooter,
            )

            // ── 首页主墙：**恒为「猜你喜欢」**（2026-09-30 第二轮，用户点名）──
            //
            // 用户的原话是"主页下面的内容改为猜你喜欢，不接入每日热门"。
            // 所以 [wallFeed] 那套二选一在首页**撤下了**：
            //  - 每日热门在首页只剩一行**预览**（节头「查看全部」进二级页看全量、在那边换一批）；
            //  - 这一整面瀑布流是猜你喜欢，节头就在它上方（`section-for-you`）。
            //
            // ⚠️ 2026-09-30 第三轮：**下拉刷新撤了**（用户原话「取消掉整个画廊的下拉刷新」）。
            // 它 2026-09-29 第四轮加进来的理由是"同一天之内不再自动联网，得有个说得出口的动作"，
            // 那个动作现在由猜你喜欢节头那枚「换一批」承担（走的正是同一条清熔断 + force 路径），
            // 所以撤掉它不会让用户失去"我要新内容"的出口。
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 下滑收起两栏的判据挂在这一层（页内滚动 → 跨层状态，见 GalleryChromeAutoHide）。
                    // ⚠️ 顺序：本连接在顶栏折叠连接**之后**（= 更贴近子级）。
                    // pre 段是"近的先看"，而 miuix 顶栏折叠会在 pre 段把位移吃掉 ——
                    // 摆到外面就只能拿到顶栏吃剩的，得等顶栏完全折平才开始累计，
                    // 读起来就是"滑了一大段栏才收"。本连接恒回 Offset.Zero，不抢顶栏的位移。
                    .nestedScroll(topBarBehavior.nestedScrollConnection)
                    .nestedScroll(chromeHideConnection),
            ) {
                GalleryForYouPage(
                    fvm = fvm,
                    wall = forYouWall,
                    // 空态那句"有 N 张收藏却抽不出标签"要说得出张数 —— 不说就与"你还没收藏"分不开。
                    favoriteCount = galleryFavorites.size,
                    imageLoader = imageLoader,
                    columnCount = columnCount,
                    state = forYouGridState,
                    topPadding = gridTopPadding,
                    backdrop = topBarBackdrop,
                    // 三节**必须带**：不带就会让画师/热门/节头整片消失 ——
                    // 屏上少一大块内容、视口锚点跟着漂，读起来像页面坏了。
                    sections = homeSections,
                    // 常驻 chrome（搜索入口条 + 来源分段器）= 墙的第一行内容：
                    // 随内容滚走（2026-09-30 第三版，用户口径「搜索栏保持在这里就行」）。
                    // 搜索开着时它照旧不渲染 —— 与搜索浮层两态互斥的口径不变。
                    chrome = if (!svm.active) {
                        @Composable {
                            GalleryHomeChrome(
                                source = svm.source,
                                onOpenSearch = {
                                    // 只开搜索、**直接展开**：与顶栏那枚 🔍 同一个入口同一种语义
                                    // （点搜索就是要打字，不是要看上一轮的结果）。
                                    svm.openSearch()
                                },
                                onOpenReverse = {
                                    // 顺序不能反：先开搜索再开反搜。搜索卡的抢焦点 effect 在 `rvm.open`
                                    // 为真时刻意让位给反搜框，先开反搜会让标签框与反搜框抢一次焦点。
                                    svm.openSearch()
                                    rvm.openLayer()
                                },
                                onPickSource = { next -> svm.setSource(next) },
                            )
                        }
                    } else {
                        null
                    },
                    onOpen = { post ->
                        GalleryViewerQueue.set(forYouWall.cards.map { it.post })
                        onOpenPost(post.site, post.id)
                    },
                    onRetry = { retryForYouFromUser() },
                    // 首页不再有"日榜那一面墙"可切，所以这条出口去掉 ——
                    // 而它原本的作用（空态里给一枚"去看每日热门"）现在由节头那枚「查看全部」
                    // 与它的二级页承担。留着它会让空态指向一个**首页上没有的状态**。
                    onGoToDaily = null,
                )
            }
        }

        // 顶栏**一直在**（用户 2026-09-25 第四轮：不跳页、也不替换标题栏）。
        // ⚠️ 常驻 chrome（搜索入口条 + 来源分段器）**不在顶栏里** —— 它现在是猜你喜欢墙的
        // 第一行内容（见 GalleryForYouPage 调用处），随内容滚走；顶栏的下滑折叠照常工作。
        //
        // ── 下滑收起（2026-09-30，用户点名；默认关）──
        // 收的是**整条顶栏**（含折叠后那行小标题）。chrome 已挪进墙里当内容（见上面），
        // 所以这一下收的只有顶栏自己。位移走 `animateDpAsState`（与全站同一档时长），
        // 所以上下滑是同一段动作的两个方向。
        // 用 `offset` 而不是把它移出组合：移出组合会让 `topBarBehavior` 的折叠状态与滚动锚点
        // 一起丢掉（再展开时大标题会从展开态重来一遍）。
        val topBarHiddenOffset by animateDpAsState(
            // 只有"顶栏开关开着"才偏移：开关关着时它恒为 0（用户口径：分开关两栏）。
            targetValue = if (hideTopBarOnScroll && chromeHidden) -TOP_BAR_HIDE_DISTANCE else 0.dp,
            animationSpec = tween(durationMillis = tokens.motion.medium),
            label = "galleryTopBarHideOffset",
        )
        VeneraTopAppBar(
            title = "画廊",
            largeTitle = "画廊",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            // 双击任意空白处回顶部（2026-09-30 新增，加性参数：其余调用点不传即零改动）。
            onDoubleTap = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                scope.launch {
                    // 回顶部的同时把两栏的收起状态复位 —— 否则用户双击之后栏还是收着的，
                    // 而"我在哪、我要去哪"恰恰是回顶那一刻最需要看见的信息。
                    val (hidden, accumulated) = GalleryChromeHidePolicy.reset()
                    scrollAccumulated = accumulated
                    GalleryChromeAutoHide.hidden = hidden
                    // 首页只有一面墙（猜你喜欢），所以只有一把滚动状态要回顶。
                    forYouGridState.animateScrollToItem(0)
                }
            },
            actions = {
                // 结果态多一枚 🔍：那一态搜索条已收成"一行胶囊"，**改条件 / 接着加标签的入口在这里**
                // （点那一行胶囊同样能展开，两条路等价 —— 一枚看得见的按钮 + 一个顺手的快捷方式）。
                if (svm.active && svm.mode == GallerySearchMode.RESULTS) {
                    VeneraTopBarPill(onClick = {
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
                VeneraTopBarPill(onClick = {
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
                // 「换一批」只管**屏上这一面墙**：搜索态下屏上是搜索结果，按它用户看不见效果，
                // 所以在搜索态整枚收掉，而不是摆一颗"按了没反应"的钮（换结果由「搜索」钮负责）。
                //
                // ⚠️ 2026-09-30 第二轮：首页只有一面墙了（猜你喜欢），所以这一枚**只有一种语义** ——
                // 重抽标签并重取。它原来还有一个"日榜那一面 = 零请求本地重排"的分支，
                // 现在那个动作住在每日热门的二级页上（那里才是"看全量的人"）。
                if (!svm.active) {
                    VeneraTopBarPill(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        retryForYouFromUser()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            // 说出它干了什么：这一按是**重抽标签**，不只是把同一批图再洗一次顺序。
                            contentDescription = "按收藏重抽一批",
                            tint = tokens.color.textPrimary,
                        )
                    }
                }
            },
            // ── 常驻 chrome（搜索入口条 + 来源分段器）**不在顶栏的 bottomContent 里了** ──
            //
            // 2026-09-30 用户口径「顶住在这个位置就行，不需要跟随顶栏」。从前它住在顶栏的
            // `bottomContent` 槽里，而 miuix 顶栏把那一格钉在 `contentTop`（= 折叠中的栏高 +
            // 底距，见 miuix TopAppBar.kt:927）上 —— 大标题一折叠栏高收缩，整条 chrome 就跟着
            // 往上爬，用户读起来是"搜索框自己会跑"。
            //
            // 所以这一格改传空：顶栏只剩大标题那一条，chrome 挪成下面的**顶层浮层**，
            // 锚在 [topBarFloor]（与搜索浮层同一条基准线）。网格避让 [gridTopPadding] 与
            // 首帧布局因此**一个数字都不用改** —— 静止时与从前逐像素一致，
            // 变的只有滚动时它不再跟随折叠。
            bottomContent = {},
            modifier = Modifier
                .align(Alignment.TopCenter)
                // 下滑收起：整条往上滑走。
                // ⚠️ 偏移只影响**绘制与命中区域**，不影响布局 —— 网格的避让仍按顶栏"存在"算。
                // 这是刻意的：避让若跟着收起一起变，图片会在收起过程中被重新排版（重排 + 走位，
                // 观感是"图抖了一下"），而用户要的只是把顶栏那一层让开。
                .offset(y = topBarHiddenOffset),
        )

        // ── 常驻 chrome（搜索入口条 + 来源分段器）**不再是浮层**（2026-09-30 第三版）──
        //
        // 用户口径（原话）：「顶栏我需要下滑折叠，我只需要搜索栏保持在这里就行而不是图二这样」。
        // 两版浮层方案都被真机否了：
        // - 钉在 topBarFloor（第一版）：顶栏折叠后 chrome 上方露一段空隙，内容从空隙穿上去，
        //   整个页头读起来散了；
        // - 冻结顶栏折叠：与"顶栏要折叠"直接冲突。
        //
        // 最终形态：chrome 是**猜你喜欢墙的第一行内容**（传给 [GalleryForYouPage] 的 chrome 槽），
        // 与三节一起随内容滚走 —— 搜索栏"保持在这里"= 保持它是页面的一部分。
        // 网格避让 [gridTopPadding] 因此回到 [topBarFloor]（chrome 的高度由内容自己占位）。

        // ── 搜索卡：从**入口条的位置**弹出来的浮层（2026-09-30 第二轮，用户拍板"内容形变"）──
        //
        // 形态与从前那条内联卡的差别只有一条，但它决定了整套做法：**它不再推内容**。
        // 从前它住在顶栏的 `bottomContent` 里，长开时把整个网格往下推（避让公式跟着它算）——
        // 用户对这一下的读感是"页面被顶开了"，而他要的是"这张卡自己长出来"。
        //
        // 所以现在：
        //  - 它是一层**叠在内容之上**的浮层，锚在常驻 chrome 那条搜索入口条的位置
        //    （`statusBarTop + 顶栏展开高度`，与 chrome 同一条基准线）；
        //  - 进出场是 `scaleIn(transformOrigin = TopCenter)` + 淡入 —— 从那条 56dp 的条
        //    原地长开、收回去，读起来是同一块面在变高，而不是"另一块东西滑进来"；
        //  - 网格避让因此**不再跟随它**（公式简化成常量，见 [gridTopPadding]）。
        //
        // z 序：它在顶栏**之后**画，所以压在顶栏背板之上；INPUT 态那层 scrim 在它之下。
        if (svm.active) {
            // INPUT 态（正在打字/挑标签）：铺一层可点 scrim。
            // 点它 = 有胶囊就收成一条（接着看图）、没有就整个关掉 —— 与系统返回那两档同义。
            //
            // 只在 INPUT 态铺：RESULTS 态那张卡只有一行胶囊、下面的结果墙是**用户正在看的东西**，
            // 铺一层 scrim 会把那面墙的滚动与点击全吃掉（用户会以为页面卡死了）。
            if (svm.mode == GallerySearchMode.INPUT && !searchCollapsed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = SEARCH_SCRIM_ALPHA))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (svm.filters.isNotEmpty()) svm.collapseToResults() else svm.closeSearch()
                        },
                )
            }
            AnimatedVisibility(
                visible = !searchCollapsed,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = searchOverlayTop),
                enter = fadeIn(animationSpec = tween(durationMillis = tokens.motion.medium)) +
                    scaleIn(
                        initialScale = SEARCH_OVERLAY_INITIAL_SCALE,
                        // 从**顶部**长开：与它锚在入口条位置这件事一致（从中心长开会像"凭空浮出来"）。
                        transformOrigin = TransformOrigin(0.5f, 0f),
                        animationSpec = tween(durationMillis = tokens.motion.medium),
                    ),
                exit = fadeOut(animationSpec = tween(durationMillis = tokens.motion.short)) +
                    scaleOut(
                        targetScale = SEARCH_OVERLAY_INITIAL_SCALE,
                        transformOrigin = TransformOrigin(0.5f, 0f),
                        animationSpec = tween(durationMillis = tokens.motion.short),
                    ),
            ) {
                GallerySearchArea(
                    svm = svm,
                    recommendations = recommendations,
                    // 点一枚 = 切站 + 装条件 + 开搜，**一步**（理由见 acceptHandoff：
                    // 拆成 setSite + setFilters 会先拿旧条件在新站发一笔）。
                    // 与大图页交接同一个入口，所以上一轮也压栈 —— 三条入口口径必须一致。
                    onPickRecommendation = { site, tags -> svm.acceptHandoff(site, tags) },
                    rvm = rvm,
                    allowNsfw = allowNsfw,
                )
            }
        }

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
 * 顶栏收起时向上滑走的距离。
 *
 * 取一个**足够大**的定值（而不是量顶栏真实高度）：顶栏是 `align(TopCenter)` 的浮层，
 * 量它的高度要在布局期回写状态（本仓在首页顶栏避让上为这个形状吃过亏，见 FREEZE-STATEMENT 第七轮）。
 * 400dp 覆盖"状态栏 + 大标题 + chrome"全部实际高度之和还有余量，
 * 而多滑走的那一点是屏幕外，肉眼不可见。
 */
private val TOP_BAR_HIDE_DISTANCE = 400.dp

/**
 * 搜索浮层背后那层 scrim 的不透明度。
 *
 * 0.32 是"能看出下面还有东西、但读得出上面那张卡是主角"的一档。它只在 INPUT 态出现
 * （RESULTS 态不铺，理由见 [GalleryScreen] 里那处注释），所以不必压得太暗 ——
 * 用户点 scrim 是"我改完了"，不是"我要离开这一屏"。
 */
private const val SEARCH_SCRIM_ALPHA = 0.32f

/**
 * 搜索浮层进出场的起始缩放。
 *
 * 从 0.94 长到 1：`transformOrigin` 钉在**顶边中点**（那条入口条的位置），
 * 所以这一下读起来是"入口条自己长开成一张卡"，而不是"一块面板从中间放大"。
 * 幅度刻意很轻 —— 再大就成了"弹窗"，而这是同一页上的一次形变。
 */
private const val SEARCH_OVERLAY_INITIAL_SCALE = 0.94f

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
 * 当时页尾还写着「那站 40 张」的假读数，正是这次定位真机反馈的唯一线索被抹掉的原因）。
 *
 * @param blockedRuleOf 这条被哪条用户规则挡了（返回规则原文，给页面念出来）。
 *   判据由调用方给：画廊侧是 GalleryPorts.contentGuard.blockedGalleryRule，
 *   日榜与搜索必须**同一个调用**，否则会出现"墙上被挡、搜索结果里全裸"的分叉。
 * @param blockAi 画廊那一把**独立的** AI 屏蔽开关（批次 C2，用户拍板"画廊和漫画分开"）。
 *   命中的条目按"命中一条屏蔽规则"记账，规则名写死成 `AI 生成` —— 页尾与空态那两处读数
 *   本来就会把 `blockedRules` 念出来，这样"AI 挡了几张、为什么挡"是同一句真话，
 *   不需要再造第三个计数器（第三个计数器意味着第三套文案，那才是会漂的地方）。
 */
internal fun buildGalleryWall(
    posts: List<GalleryPost>,
    maskMode: String,
    blockAi: Boolean = false,
    blockedRuleOf: (GalleryPost) -> String?,
): GalleryWall {
    val kept = ArrayList<GalleryCard>(posts.size)
    val blockedRules = LinkedHashSet<String>()
    var blockedCount = 0
    var hiddenByRating = 0
    posts.forEach { post ->
        val blocked = blockedRuleOf(post)
            ?: if (blockAi && GalleryAi.isAiMarked(post.tagList)) AI_BLOCKED_RULE else null
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
 * 那面错落屏的墙 —— 主墙与搜索结果共用（用户拍板搜索层后抽出来的，
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
    /** 长按一张卡。收藏页用它拉出「从收藏移除」，主墙/搜索不传（长按在那儿没有任何事可做）。 */
    onLongPress: ((GalleryPost) -> Unit)? = null,
    /**
     * 非空 = 这面墙**处于多选态**（眼下只有收藏页会传）。此时：
     * 卡片右上角摆那枚统一选择框、点卡片改成勾选，`onLongPress` 的语义由调用方切成"区间反选"。
     *
     * null = 非多选态，卡片行为与从前**逐字一致**（点开大图页）—— 主墙与搜索墙不传，
     * 它们的交互因此零改动。判据用"传没传这个函数"而不是再加一个布尔开关：
     * 两个参数表达同一件事就有对不上的可能，一个参数不会。
     */
    selection: ((GalleryPost) -> Boolean)? = null,
    /** 点选择框，以及多选态下点整张卡。 */
    onToggleSelect: ((GalleryPost) -> Unit)? = null,
    /** 卡片上的补充内容。null 时与从前一致。 */
    overlay: (@Composable BoxScope.(GalleryPost) -> Unit)? = null,
    /**
     * 墙首那一行（日榜的"只有一站给了内容"说明条）。
     *
     * ⚠️ 传进来的槽位**存在与否不能跟着内容变**：懒列表在第 0 项之后插一个新项，
     * 视口锚点会漂到第 1 项，于是内容画进 `contentPadding` 里（首页顶栏重叠那次真机缺陷的根因，
     * 见记忆「首页顶栏重叠的真根因：锚定漂移」）。所以判空要**移到槽位内部**：
     * 这一行恒在，里面有没有话由数据决定。
     */
    header: (@Composable () -> Unit)? = null,
    /**
     * 首页那几节（2026-09-30 起三节：画师 / 每日热门预览 / 猜你喜欢节头）。
     * 每一项都是墙里一个**恒定存在**的 FullLine 项，"这一节有没有货"由项内部判 ——
     * 节的数量不许跟着数据变（锚定漂移那条，见 [GalleryGridSection]）。
     */
    sections: List<GalleryGridSection> = emptyList(),
    footer: (@Composable () -> Unit)? = null,
    /**
     * 这一面墙上的卡**是否参与封面共享元素**，以及用哪个 key。
     *
     * 默认 null = 不挂。只有「每日热门」二级页那一处传（它的对面是首页预览行那张同 uid 的横卡）——
     * 首页主墙 / 搜索墙 / 收藏墙的对面是**跨 Activity** 的大图页，那边没有同 key 的落地槽位，
     * 挂了就是白挂：飞行体找不到去处，而两端各自淡入淡出，看起来像"动画没做完"。
     * 为什么不做成布尔开关而要一个函数：key 的身份是 `uid`（带站键），这层判断留在调用方更清楚。
     */
    sharedElementKey: ((GalleryPost) -> String)? = null,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    // 这一面墙所有卡片的标题**整批算一次**（要开 SQLite 查离线词典，逐卡各查一次就是几十趟）。
    // 墙是唯一知道"这批卡是哪些"的地方，所以取数落在这里；下面按 uid 交给每张卡。
    val titles = rememberGalleryCardTitles(cards.map { it.post })
    // 滚动越界预取（2026-09-30「预览能不能更快」那轮）：见 [rememberGalleryPreviewPrefetch]。
    rememberGalleryPreviewPrefetch(
        cards = cards,
        imageLoader = imageLoader,
        state = state,
        leadingItemCount = (if (header != null) 1 else 0) + sections.size,
    )
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
        // 节头（首页三节）。**恒定项**（数量不许跟着数据变，理由见 [GalleryGridSection]）。
        sections.forEach { section ->
            item(key = section.key, span = StaggeredGridItemSpan.FullLine) { section.content() }
        }
        // key 必须带站点：两站 id 各自编号，同 id 是两张不同的图，撞 key 会直接崩。
        items(cards, key = { it.post.uid }) { card ->
            GalleryPostCard(
                card = card,
                imageLoader = imageLoader,
                titles = titles,
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
                selected = selection?.invoke(card.post) == true,
                selecting = selection != null,
                onToggleSelect = onToggleSelect?.let { handler -> { handler(card.post) } },
                overlay = overlay,
                sharedElementKey = sharedElementKey?.invoke(card.post),
            )
        }
        footer?.let { slot ->
            item(key = "grid-footer", span = StaggeredGridItemSpan.FullLine) { slot() }
        }
    }
}

/** 预取窗口：视野之外再往前暖 [GALLERY_PREFETCH_AHEAD] 张缩略图。 */
private const val GALLERY_PREFETCH_AHEAD = 24

/**
 * 画廊墙的**滚动越界预取**（2026-09-30「预览能不能更快」那轮加的）。
 *
 * 病：懒网格只对"进入视野的项"发请求 —— 卡片组合的那一刻 `AsyncImage` 才开跑，
 * 用户以正常速度上滑时，图开始下载的那一刻他已经看到空卡 + shimmer 了。
 * 两站缩略图都很小（本机实测 yande.re ~13 KB、Gelbooru ~25 KB），下载本身只要一两百毫秒，
 * **"看到才开始下"这一拍浪费的才是大头**。
 *
 * 做法：盯着网格最后一个可见项（`snapshotFlow` + `distinctUntilChanged`，滚动停下才判一次），
 * 从可见末尾往前看 [GALLERY_PREFETCH_AHEAD] 张，用 `imageLoader.enqueue` 把没请求过的
 * 缩略图**异步**暖进缓存。与网格正式请求是同一条命中链路：
 * - `enqueue` 跑完整引擎管线，成功后位图进**内存缓存**；就算之后被挤掉，原始字节也已落
 *   **磁盘缓存** —— 两站缩略 CDN 都回 `max-age=315360000`（2026-09-30 本机 HEAD 实测），
 *   Coil 的 DiskCache 只由 NetworkFetcher 写，预取恰恰走它，回看零请求；
 * - 内存缓存 key 在无 transformations 时不带尺寸、compose 侧精度恒 INEXACT
 *   （coil 3.6.2 `AsyncImagePainter.updateRequest` 字节码实测），预取解好的位图网格直接命中
 *   —— **不重解码、不重复请求**；同 key 的并发请求由引擎自己合并，不会双发。
 *
 * 为什么不挂 target：挂了会参与视图生命周期与可见性裁剪，而"滚太快"时恰恰是
 * 卡片离场、请求被取消的窗口 —— 预取要的就是无主请求，落地进缓存即可。
 *
 * 收口（防三件事）：
 * 1. **判重按 uid 存 `HashSet`（普通字段）而不是靠重组**：请求过就跳过，预取是网络层的事，
 *    重发一笔比多重组贵得多；集合跟着 `cards` 的实例换（新一轮墙）一起重置；
 * 2. **只取 preview 档**（[GalleryPost.previewUrl]）：用户开了「预览清晰度 = 样例」时卡片自己
 *    会走 LARGE，那档一张几百 KB~几 MB，自动预取它就是拿用户的流量下赌注 ——
 *    大档永远只在用户真正看见时才取；
 * 3. **协程挂在 `LaunchedEffect(cards)`**：组合销毁 / 换一批换墙即取消，在途预取随之断流。
 *
 * @param state 调用方那把网格滚动状态（自建一把读不到滚动位置 —— 第 0 版在这上面栽过）。
 * @param leadingItemCount 卡片之前恒定存在的整行项数（header + 节头）：
 *   懒列表的 item index 是"整行项 + 卡片"混排的，不减掉它就会把节头当卡片、
 *   整个预取窗口向后错位同样的格数。
 */
@Composable
private fun rememberGalleryPreviewPrefetch(
    cards: List<GalleryCard>,
    imageLoader: ImageLoader,
    state: LazyStaggeredGridState,
    leadingItemCount: Int,
) {
    val context = LocalContext.current
    // 预取过的 uid 集合。普通字段（非状态）：它只服务判重，变了不需要触发重组。
    val requested = remember { HashSet<String>() }
    // 卡片位次 → 预览地址。key 用 uid（两站 id 各自编号，同 id 是两张不同的图）。
    val urlsByUid = remember(cards) {
        HashMap<String, String>(cards.size).apply {
            cards.forEach { card -> put(card.post.uid, card.post.previewUrl) }
        }
    }
    LaunchedEffect(cards, leadingItemCount) {
        requested.clear()
        snapshotFlow {
            // 读进 derivedStateOf 语义的块里：只有"最后一个可见项"变了才重新判，
            // 滚动中 layoutInfo 每帧都在变，不加这层就是每帧白算一次窗口。
            state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex < 0) return@collect
                val lastCardIndex = lastVisibleIndex - leadingItemCount
                if (lastCardIndex < 0) return@collect
                val from = (lastCardIndex + 1).coerceAtMost(cards.size)
                val to = (lastCardIndex + 1 + GALLERY_PREFETCH_AHEAD).coerceAtMost(cards.size)
                for (i in from until to) {
                    val post = cards[i].post
                    if (post.uid in requested) continue
                    val url = post.previewUrl
                    if (url.isBlank()) continue
                    requested += post.uid
                    imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            // 不挂 target：无主请求，落地进缓存即完成（理由见上）。
                            // 内存缓存写回由引擎默认档（ENABLED）承担。
                            .build(),
                    )
                }
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
internal fun GalleryPostCard(
    card: GalleryCard,
    imageLoader: ImageLoader,
    /**
     * 这一张的标题（`uid → 标题`，由调用方整批算好）。
     *
     * 透传而不是在卡内各查一次：标题要开 SQLite 查离线词典，几十张卡各查一次就是
     * 几十趟查询（理由与"整批摊平去重"的做法见 `rememberGalleryCardTitles`）。
     */
    titles: Map<String, String>,
    /**
     * 把这张卡**封面那一块**在窗口坐标系里的矩形一起交出去 —— 只用来裁出滑入期间飞进来的那一帧
     * （见 [GalleryFlyIn]）。**不是整张卡**：落点是大图页的画面框，起点必须与它同构才不闪，
     * 理由写在卡内 [coverBounds] 那处头注里。
     */
    onOpen: (Rect) -> Unit,
    onLongPress: (() -> Unit)? = null,
    /** 这一张是否已选中（仅在 [selecting] 为真时有意义）。 */
    selected: Boolean = false,
    /** 是否处于多选态：右上角摆统一选择框，点卡片改成勾选。 */
    selecting: Boolean = false,
    /** 点选择框 / 多选态下点卡片。 */
    onToggleSelect: (() -> Unit)? = null,
    overlay: (@Composable BoxScope.(GalleryPost) -> Unit)? = null,
    /** 非空 = 这张卡的封面参与共享元素飞行，值就是那对两端逐字相同的 key（见 [GalleryCardsGrid]）。 */
    sharedElementKey: String? = null,
    /**
     * 非空 = **不按原图比例**，一律按这个比例摆（画师介绍页的「热门作品」用）。
     *
     * 取数回来的那几张比例各不相同，按原比例摆就是一行三张高矮不齐 —— 而它们是**按行**摆的
     * （`Row + weight`，行与行必然对齐），于是矮的那两张底下留出空洞（真机截图里能直接看到）。
     * 钉死比例之后一行三张等高，"参差"这件事从屏上消失。
     *
     * 代价如实写在这里：**原图会被裁**（ContentScale.Crop）。这是"整齐"换"信息完整"，
     * 不是白拿的 —— 首页主墙那种瀑布流不该传它（那里列与列各自往下排，错落是刻意的）。
     */
    fixedRatio: Float? = null,
) {
    val tokens = VeneraTokens
    val post = card.post
    // 墙上摆哪一档由「画廊设置 → 预览清晰度」定（判据与"取不到就退回 preview"那条都在
    // GallerySettingsModel.wallUrl）。按卡片各读一次：它是整面墙唯一的消费点，
    // 往下透传要改四个调用方（主墙/搜索/推荐/收藏），往上提又要改 GalleryWall 的派生 —— 都不值。
    val quality by GalleryPorts.of(LocalContext.current).prefs
        .galleryPreviewQuality.collectAsState()
    /**
     * 飞行体的**起点矩形**：卡片里**封面那一块**的窗口矩形，**不是整张卡**。
     *
     * 2026-10-01 12:00 用户报"卡片整个飞进去了，内容包裹还是会闪一下"，成因就在这里：
     * 落点是大图页里"按真实比例定框"的**画面框**（只有图、没有下面那条标题），
     * 而整卡像素里还带着标题条与卡片面板的圆角 —— 飞行末段等于把整块"包裹"一起放大铺进画面框
     * （卡宽约半屏、画面框约一屏，包裹被放大近两倍），交棒那一刻包裹整片消失，读起来就是闪。
     *
     * 共享元素的起点与终点**必须同构**：两份画面都该是"同一比例的一张图"。这也正是页内那对
     * 真共享元素的口径 —— `coverSharedElement` 挂的本来就是封面（见 [GalleryPosterRow]）。
     */
    var coverBounds by remember { mutableStateOf(Rect.Zero) }
    // 卡片被 LazyGrid 回收（滚出屏幕、换了一批）时把自己的落点撤掉：
    // 大图页返回时若还查得到一条过期矩形，就会朝屏幕外飞。
    DisposableEffect(post.uid) {
        onDispose { GalleryFlyIn.forgetCardBounds(post.uid) }
    }
    // 高度在**摆放前**就知道：两站的 JSON 都直接带中间档宽高，
    // 不像本地插图那样要靠加载成功回填 —— 所以这里不会有"各列先等分再集体跳动"。
    // 比例夹在 0.4~2.5，与图片收藏那面墙同一口径：防一张横长条把整列撑出屏幕。
    // 钉死比例那一档**不再夹取**：它是调用方给的设计值（现成 token），不是站方数据。
    val ratio = fixedRatio?.takeIf { it > 0f }
        ?: (post.cardRatio.takeIf { it > 0f } ?: tokens.spacing.coverAspectRatio).coerceIn(0.4f, 2.5f)
    // 选中态与那枚选择框交给 SelectableCardFrame —— 四处收藏面板同一份实现
    // （洗底不透明度、框的位置与画法全在那一处，这里不再各画一遍）。
    SelectableCardFrame(
        selecting = selecting,
        selected = selected,
        onToggleSelect = { onToggleSelect?.invoke() },
        modifier = Modifier.fillMaxWidth(),
    ) {
    Card(
        // 用 miuix Card 的可长按重载而不是换了外观的 [VeneraCard]：前者的内边距与圆角由我们自己
        // 按这张卡的形态定（封面在上、来源与标题在下），后者会给整卡套一圈固定的内边距 +
        // 一层 `shape.card` 圆角，封面两侧会各留一条缝。
        // 圆角现在显式跟 [VeneraCard] 同口径（tokens.shape.card）：不传时 miuix 自家默认
        // 16dp，MIUIX 模式下比别处卡片小一档，洗底（同按 shape.card 裁）也对不上。
        cornerRadius = tokens.shape.card,
        modifier = Modifier.fillMaxWidth(),
        // 多选态下点整张卡 = 勾选（只让人去够右上角那个小圈太费劲）；非多选态才是点开大图页。
        onClick = { if (selecting) onToggleSelect?.invoke() else onOpen(coverBounds) },
        onLongPress = onLongPress,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            VeneraCover(
                url = GallerySettingsModel.wallUrl(post, quality),
                contentDescription = "${post.site.displayName} #${post.id}",
                // 宽高由外部 aspectRatio 定死，封面再自持 3:4 会在槽位里横向留缝（轮播那条同因）。
                preserveAspectRatio = false,
                mask = if (card.masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                imageLoader = imageLoader,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    // 起点矩形在这里量：它必须与落点（画面框）同构，所以量的是封面而不是整卡。
                    .onGloballyPositioned {
                        val rect = it.boundsInWindow()
                        coverBounds = rect
                        // 顺手给**返回程**留一个落点（大图页离场时按 uid 来查）。
                        // ⚠️ 打码卡**不回写** —— 红线是"打码那张两边都不飞"，
                        // 而"表里压根没有它"是这条红线最不容易被绕过的一种落法。
                        if (!card.masked) GalleryFlyIn.noteCardBounds(post.uid, rect)
                    }
                    // 不挂飞行时压根不调那一层（而不是传一个空 key 进去）：
                    // key 两端必须逐字相同才触发，空串是个"看起来挂了其实永远对不上"的假状态。
                    .let { base ->
                        val key = sharedElementKey ?: return@let base
                        base.coverSharedElement(key = key, allowFly = !card.masked)
                    },
            ) {
                // 视频唯一的例外：不标出来，它就是一张"加载失败的图"（缩略图是站方的静帧 jpg）。
                // 摆右下角。**来源不再压角**（2026-09-30）：它搬到封面下方那一行与标题并排，
                // 压角方案在两站混排时要用户从图的四个角里找，而参考图那枚就在标题左边。
                if (post.isVideo) GalleryVideoPill(post.durationLabel)
                // AI 角标（批次 C2）：命中站方 AI 标签才摆，摆不摆由画廊自己那枚开关管
                // （与漫画守卫页的 block_ai 是两把，词表仍是同一份）。
                // 屏蔽开着时这条根本不会上屏，所以角标的真正用途是"我不屏蔽，但我要看得出来"。
                // 偏好读法与上面「预览清晰度」同一条口径：按卡片各读一次，不往下透传四个调用方。
                val aiBadgeOn by GalleryPorts.of(LocalContext.current).prefs
                    .galleryAiBadge.collectAsState()
                if (aiBadgeOn && GalleryAi.isAiMarked(post.tagList)) GalleryAiPill()
                overlay?.invoke(this, post)
            }
            GalleryCardCaption(
                site = post.site,
                // 没有作品/角色标签时给 null ⇒ 那一行只画来源标识，不画空文字。
                title = titles[post.uid],
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = tokens.spacing.space4,
                        end = tokens.spacing.space4,
                        top = tokens.spacing.space3,
                        bottom = tokens.spacing.space4,
                    ),
            )
        }
    }
    }
}

/**
 * 视频角标：一个「▶」，站方给了时长就带上秒数。
 *
 * 摆右下角。给视频打标不是装饰 ——
 * 站方那档 preview 是**静帧 jpg**，不打标它就是一张看着正常、点开什么也不会动的图，
 * 用户没法知道那张可以播。时长在 yande.re 那侧拿不到（实测字段表里没有），
 * 所以只在有的时候显示，不写"0″"。
 *
 * ⚠️ 2026-09-30 起**左下角那枚来源胶囊撤了**：来源搬到封面下方那一行
 * （与标题并排，见 [GalleryCardCaption]）。所以这里的"与来源分角不抢位"那条理由
 * 已经不存在，四个角只剩视频与 AI 两枚。
 */
@Composable
private fun BoxScope.GalleryVideoPill(durationLabel: String) {
    GalleryCornerPill(if (durationLabel.isEmpty()) "▶" else "▶ $durationLabel", Alignment.BottomEnd)
}

/**
 * AI 角标：一枚「AI」。
 *
 * 摆**左上角**（左下那枚来源胶囊已撤，但左上仍然是它与视频角标最远的一角，不会撞）。
 * 判据与"没有信号就不摆"这条硬口径同一条：站方没打 AI 标签的条目一律不摆，
 * 不靠"画风像 AI"编一个读数出来。
 */
@Composable
private fun BoxScope.GalleryAiPill() {
    GalleryCornerPill("AI", Alignment.TopStart)
}

/** 被画廊那把 AI 开关收起时，页尾念出来的"规则名"。它不是一条文本规则，是一枚开关。 */
const val AI_BLOCKED_RULE = "AI 生成"

/**
 * 卡片角上那枚小胶囊。
 *
 * 不复用 [com.venera.compose.components.venera.VeneraSourceBadge]：那枚是**左上角小圆角**、
 * 且被漫画侧 6 个页面共用，改它等于一起动别人。这里的口径是"胶囊 + 更小"，
 * 但**尺寸全部取现成 token**（badge 字号 / badge 内边距 / extraLarge 圆角），不新造数字。
 * 底板仍用固定深色：封面颜色不可控，只有固定底板能保证任何图上都可读。
 *
 * 刻意**不可点**：点下去要的是"只看这一站"，本仓还没有筛选态的出口，做了就是假按钮。
 *
 * 2026-09-30 第二轮：它只剩视频 / AI 两枚在用（来源那一枚换成了
 * [VeneraGallerySourceMark] 图形标识），所以这里只留形状，不再有"源名"这个用途。
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
                append("；另有 ${blockedByRulesFragment(wall.blockedCount, wall.blockedRules)}")
            }
            if (wall.hiddenByRating > 0) {
                append("；${ratingHiddenFragment(wall.hiddenByRating)}")
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
 * 每日热门那一面墙（两站上一天热门混合打乱）。
 *
 * 这一层是 2026-09-30 第二轮从首页那个 `when` 里**原样搬过来**的：加载环、错误重试、
 * "被规则挡完了"那档空态、以及那面墙与它的页尾。搬的理由不是省事，是**判据必须各面墙自持** ——
 * 留在宿主层就会出现"屏上是主墙、页尾还写着日榜那一天"这种错配。
 *
 * ⚠️ 2026-09-30 第二轮：首页那条主墙恒为猜你喜欢，这一面墙**只被二级页**
 * （`GalleryDailyScreen`）组合，所以它的 `sections` 恒为空 —— 三节都在首页那面墙上。
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
        // 顶栏避让地板（这一页没有常驻 chrome，避让就是地板本身）。
        top = topPadding,
        bottom = LocalBottomBarClearance.current,
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
                // 「加载失败」不许配**内容**图标（这里原来是 `Icons.Outlined.Image`，
                // 那枚图标的意思是"这儿有图"）。语义档自己去挑一枚说"没取回来"的。
                tone = VeneraEmptyTone.Failed,
                actionText = "重试",
                onAction = onRetry,
            )
        }

        cards.isEmpty() && !isLoading -> Box(
            wallModifier.padding(horizontal = tokens.spacing.screenHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            /*
             * 归因**只按实际剔掉的张数说**。此前这一支只要墙空着就说"被你的规则挡完了"，
             * 而墙空着还有第三种可能：站方这一轮本来就什么都没回（当天零新增、源被限）。
             * 把"没有"说成"被挡住"是假归因 —— 用户会去收规则，而收完还是空的，
             * 于是他把一条正常链当成坏了。（2026-09-29 真机报出来的。）
             */
            val blocked = wall.blockedCount
            val hidden = wall.hiddenByRating
            VeneraEmptyView(
                title = when {
                    blocked > 0 || hidden > 0 -> "这一屏被你的规则挡完了"
                    else -> "这一轮没有可摆的图"
                },
                message = buildString {
                    if (blocked > 0) {
                        append("$blocked 张命中屏蔽规则：${wall.blockedRules.joinToString("、")}")
                        append("。图站的标签是 long_hair、tail 这类下划线写法，")
                        append("短关键字很容易整站命中 —— 想看到内容就把那条规则收掉或改长。")
                    }
                    if (hidden > 0) {
                        if (blocked > 0) append("\n")
                        append("$hidden 张被「成人内容处理」收起。想看到内容，去设置里改成「封面打码」或「不过滤」。")
                    }
                    if (blocked == 0 && hidden == 0) {
                        // 不说"两站都没有"：哪几站参与了的实情由下面那行 sourceNotice 念。
                        append("站方这一轮没有回内容。一张也没被剔掉，所以不是屏蔽规则的事 —— ")
                        append("稍后再刷一次，或换一天（日榜是按站方那一天算的）。")
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
            // 这一面墙是共享元素的**落地一头**：首页「每日热门」预览行那张横卡带着同一个
            // `uid` 的 key，两端逐字相同才飞得起来（key 的口径见 `galleryCoverKey`）。
            // 其余三面墙（首页主墙 / 搜索 / 收藏）不传 —— 它们对面是跨 Activity 的大图页，没有槽位。
            sharedElementKey = { post -> galleryCoverKey(post.uid) },
            header = {
                // 槽位**恒在**（理由见 [GalleryCardsGrid] 的 header 注释）：有没有话由内部判。
                // 只有一站给上内容时把缺的那站说出来 —— 静默退化成单源最难被发现。
                sourceNotice?.let { notice -> GallerySourceNotice(notice) }
            },
            footer = if (hasPosts) {
                @Composable { GalleryFeedEnd(wall, date) }
            } else {
                null
            },
        )
    }
}
