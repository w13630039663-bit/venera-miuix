package com.venera.compose.gallery.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.venera.compose.artistProfileIntent
import com.venera.compose.components.VeneraEmptyTone
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.rememberImageWallColumnCount
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.selection.SelectableCardFrame
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraGallerySourceMark
import com.venera.compose.components.venera.VeneraPressFeedback
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistLinks
import com.venera.compose.openGalleryArtistProfile
import com.venera.compose.gallery.domain.GalleryChromeHidePolicy
import com.venera.compose.gallery.domain.cardBoxRatio
import com.venera.compose.gallery.domain.cardBoxRatioClamped
import com.venera.compose.gallery.domain.GalleryFeedSource
import com.venera.compose.gallery.domain.GalleryFollowedArtists
import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.domain.GalleryGuard
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.gallery.data.PixivRankingMode
import com.venera.compose.gallery.data.PixivRestrict
import com.venera.compose.gallery.data.PixivWork
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.domain.GalleryRecommendation
import com.venera.compose.gallery.domain.GalleryRecommendations
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GalleryWallFeed
import com.venera.compose.gallery.domain.PixivSearchKind
import com.venera.compose.gallery.domain.SavedWallDepth
import com.venera.compose.gallery.domain.depthToApply
import com.venera.compose.gallery.domain.galleryNotices
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.feature.settings.SettingsHeroImage
import com.venera.compose.feature.settings.SettingsHeroSystemBarIcons
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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
 * 1. 顶栏大标题「画廊」+ 常驻 chrome（一条搜索入口条，见 [GalleryHomeChrome]）；
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
    /**
     * 顶栏那枚「浏览历史」（2026-10-14 起，占的正是原来「换一批」那枚的位置）。
     *
     * 换一批没有丢：它与猜你喜欢节头那枚走的是同一条 `retryForYouFromUser()` 路径
     * （下拉刷新 2026-09-30 撤下时，注释就写明节头那枚「承担」了「我要新内容」的出口），
     * 所以这一格换成历史入口之后，首页换一批仍在节头。
     *
     * 这里交出去的是导航动作（`GalleryHistoryRoute`，二级页、有返回语义）而不是页内状态：
     * 历史是**另一屏内容**，做成页内一层会让"从历史点开一张图、再返回"的栈说不清楚。
     */
    onOpenHistory: () -> Unit,
    /**
     * 顶栏那枚 🔖「收藏」（2026-10-17 第三十一轮，用户「在搜索和历史记录两个圆形按钮之间，
     * 新增一个书签按钮……按照这个方式去做收藏」）。
     *
     * 它落在搜索与浏览历史两枚**之间**：三枚都是"去另一屏取一批东西"，
     * 顺序按用户点名的那句原话钉死（判据见 `GalleryBooruBookmarksTest`）。
     *
     * 递出去的是导航动作（`GalleryBookmarksHubRoute`，两颗卡那一页），不是页内状态：
     * 收藏 hub 是**另一屏内容**，且本地收藏那颗最终要走到 FAVORITES 主 Tab 上。
     */
    onOpenBookmarks: () -> Unit = {},
    /**
     * 首页头图的**绝对路径**，空串 = 未设置（2026-10-15，用户「图廊首页也加上和设置页一样的可切换头图」）。
     *
     * 为什么是一根字符串而不是这一屏自己去读偏好：`BusinessApiBoundaryTest` 的 A/B 两条钉着
     * 「画廊 UI 那一层不许直连业务实现类单例」，白名单只许缩短。而往 `GalleryPreferences`
     * 端口上加成员这条路也走不通 —— 那颗接口与桌面侧**共享源码**（`desktop/build.gradle.kts:96`
     * 把 `app/…/gallery/data` 整目录 srcDir 进去），动它就要动本轮不许动的桌面目录。
     * 所以路径在装配根（`feature/Navigation.kt`，那颗本来就在白名单里）读出来递进来。
     */
    heroPath: String,
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
            svm.mode == GallerySearchMode.INPUT && svm.hasQuery -> svm.collapseToResults()
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
    val galleryFavorites by GalleryPorts.of(context).favorites.favorites.collectAsState()
    // 收藏 uid 集合（2026-10-17 第十八轮）：排行榜那张卡的 ♥ 要按 uid 查"这张收没收藏"。
    // 只认 `siteKey:id`（= [GalleryPost.uid] 的构造法），不看别的字段 —— 两条账必须同一个键。
    val galleryFavoriteUids = remember(galleryFavorites) {
        galleryFavorites.mapTo(HashSet(galleryFavorites.size)) { "${it.siteKey}:${it.id}" }
    }
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
    // 首页头图（2026-10-15，用户「图廊首页也加上和设置页面一样的可以切换的头图」）。
    // 路径由装配根递进来（理由见参数上的注）；这一侧只做**收据核验**：
    // 空串 / 文件不在了 / 文件是零字节，三种都必须当成"没设过头图"，绝不摆一张裂图。
    val galleryHeroFile = remember(heroPath) {
        java.io.File(heroPath).takeIf { it.isFile && it.length() > 0L }
    }
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

    // ── 头图在场时的顶栏契约（2026-10-16，与设置页同源）──
    //
    // 抄设置页那套（理由见 `SettingsHome` 的「hero 页的滚动契约」，是真机量出来的坑）：
    // 头图在场时 behavior 的 nestedScroll 连接**不挂** —— 空 `largeTitle` 仍占一行字体度量，
    // miuix 会给它留几十 dp 的"幽灵折叠行程"，上滑先被它在 pre-scroll 里吃掉（列表看似
    // 没反应）、回顶后展开支路又把顶栏"拉开"。折叠机制整个退场后，玻璃与小标题的进度
    // 改由**墙自己的滚动状态**直接推导：48dp 淡满，与其余页 contentOffset 的口径一致。
    //
    // 为什么不用 `topBarBehavior.state.contentOffset` 那条原路：那条路要求连接在链上，
    // 而这里恰恰不能挂（上面那条）。头图在墙的第一项（header item）里 ⇒ 第 0 项被滚走
    // 多少像素就是头图被推上去多少，这正是要的那个进度。
    val heroPresent = galleryHeroFile != null
    //
    // ⚠️ **让位契约只对画廊首页成立**（2026-10-18，用户「还是没有左边的大标题」）：
    // `heroPresent` 只回答"设没设过头图"；凡是"让位给图 / 因为图而不挂折叠 / 玻璃改由墙喂"这一类
    // 判断都要再问一句"现在是不是搜索态"—— 搜索态的大标题是「搜索」（不是空串），"幽灵折叠行程"
    // 那一档根本不发生，照旧让位就会让顶栏左边永远空一行、卡片也收不进顶栏。
    //
    // ⚠️ 同日 01:32 收窄（用户「在首页搜索框的时候，头图会异常显示」）：**落点类不在这一条里**。
    // 原先这里写的是"搜索态那一屏摆的是另一面墙，屏幕上根本没有头图" —— 那条不成立：
    // `GalleryForYouPage` 是无条件组合的（下面没有任何 `if (svm.active)` 包着它），搜索卡只是
    // 压在它之上的浮层，INPUT 态的 scrim 把它连同头图、入口条一起压暗，图仍在原位。
    // 于是墙顶留白与卡的落点继续只问 `galleryHeroFile != null`：跟的是**入口条实际画在哪**。
    val heroActive = heroPresent && !svm.active
    val heroGlassRangePx = with(LocalDensity.current) { HERO_TOP_BAR_FADE_RANGE.toPx() }
    val heroScrollProgress = remember(forYouGridState, heroGlassRangePx, heroPresent) {
        derivedStateOf {
            when {
                !heroPresent -> 1f
                // 第 0 项（头图那一颗 header item）已经整个滚出去了 ⇒ 满进度。
                forYouGridState.firstVisibleItemIndex > 0 -> 1f
                else -> (forYouGridState.firstVisibleItemScrollOffset / heroGlassRangePx)
                    .coerceIn(0f, 1f)
            }
        }
    }
    val heroScrolledPast by remember(forYouGridState, heroGlassRangePx, heroPresent) {
        derivedStateOf {
            heroPresent && (
                forYouGridState.firstVisibleItemIndex > 0 ||
                    forYouGridState.firstVisibleItemScrollOffset >= heroGlassRangePx
                )
        }
    }
    SettingsHeroSystemBarIcons(heroPresent = heroPresent, scrolledPastHero = heroScrolledPast)
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
                source.loadDaily(
                    seed = vm.seed,
                    // 哪一站先回来就先摆哪一站（见 `GalleryDailyFeed.loadDaily` 的 `onLeg`）：
                    // 三笔并发但整轮要等最慢那站，屏上原本那十几秒是纯骨架。
                    // 回调跑在取数线程上 ⇒ 这里借 scope 切回主线程再写状态。
                    onLeg = { pools -> scope.launch { vm.acceptPartial(pools) } },
                )
                    .onSuccess { daily ->
                        vm.accept(
                            list = daily.merged.posts,
                            day = daily.date,
                            notice = daily.failures.entries
                                .joinToString(" · ") { "${it.key.displayName}：${it.value}" }
                                .takeIf { daily.failures.isNotEmpty() },
                            // 结构那一份一起交下去（理由见 `GalleryViewModel.failures`）：
                            // 首行那块提示要按档分色、还要给未配账号那一档挂真入口。
                            failures = daily.failures,
                            // 原始池一起存下：二级页的「换一批」靠它做**本地**重排（零请求）。
                            pools = daily.pools,
                        )
                        // 成功才写凭证：从错误态点重试要能真发请求。
                        vm.feedLoadedKey = "feed#${vm.refreshTick}|${vm.seed}"
                    }.onFailure { e ->
                        android.util.Log.w("VeneraDebug", "gallery feed failed", e)
                        // 各站为什么没给，一起收进 vm —— 屏上摆着缓存那一屏时，
                        // 整页错误视图上不了屏，那一排按档提示是唯一的失败说法（见 GalleryViewModel.reject）。
                        vm.reject(e)
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
    fun resetBreakers() = GalleryPorts.of(context).hygiene.resetAllSiteBreakers()

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
    // 「离开这面墙时看到第几张」记在 saveable 里，而不是 VM 里 ——
    // 切主 Tab 走的是 `gotoTab` 的 `popUpTo(startDestination){saveState=true}`，这条目的地是被
    // **pop** 掉的：`NavBackStackEntry` 类头明写 "ViewModels will be cleared"，saveState 只救
    // `rememberSaveable` 那一族（判据出处见 `GalleryWallDepth` 的头注）。
    // 锚 = 记下位置时墙上第一张的 id：换一批之后同一个序号指的是另一块内容，
    // 把人送到那儿既不是"回到原处"，也会让续页判据以为已经到底。−1 = 还没记过。
    var forYouDepthAnchor by rememberSaveable { mutableLongStateOf(-1L) }
    var forYouDepthIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(forYouGridState) {
        snapshotFlow {
            val info = forYouGridState.layoutInfo
            (info.visibleItemsInfo.firstOrNull()?.index ?: 0) to fvm.posts.firstOrNull()?.id
        }
            .distinctUntilChanged()
            .collect { (index, anchor) ->
                // 墙上还没内容时不记：那一次报回来的 0 会把用户真实离开的位置抹掉，
                // 而"下一轮落地该落第几张"这个判据从此就没有输入了。
                if (anchor == null) return@collect
                forYouDepthAnchor = anchor
                forYouDepthIndex = index
            }
    }

    // 新一轮结果落地 → 滚回**该去的那一格**（不滚的后果与原注释一样：用户落在上一轮深处，
    // 而续页判据当场认为已经到底）。
    //
    // 这一处原来是 `scrollToItem(0)` 无条件回顶，而"这一轮已经落过点"的凭据记在 `remember` 里 ——
    // 组合一销毁凭据就归零，`LaunchedEffect` 在重建后又必跑首帧，于是**每次切回画廊都被按回顶部**。
    // 凭据搬进 fvm（`appliedGeneration`），落点交给 [depthToApply]：还是刚才那块内容就回原处，
    // 换了一批（锚变了）才回第 1 张。
    //
    // 键里带 posts.size：内容还没落地时先不落点，那一次滚会被 Lazy 的夹取按成 0，
    // 而 appliedGeneration 一旦写上就再也不会重来。
    LaunchedEffect(fvm.generation, fvm.posts.size) {
        if (fvm.generation == 0 || fvm.appliedGeneration == fvm.generation) return@LaunchedEffect
        val anchor = fvm.posts.firstOrNull()?.id ?: return@LaunchedEffect
        val target = depthToApply(
            saved = if (forYouDepthAnchor == -1L) null else SavedWallDepth(forYouDepthAnchor, forYouDepthIndex),
            currentAnchorId = anchor,
        ) ?: 0
        forYouGridState.scrollToItem(target)
        forYouDepthIndex = target
        fvm.appliedGeneration = fvm.generation
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
                settled = fvm.appliedGeneration == fvm.generation,
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
     * 首页头图在场时，**墙的顶部避让归零** —— 头图从屏幕最顶出血。
     *
     * 2026-10-16（用户「画廊的头图有问题，表现方式应该和图二一样才对」，图二 = 设置页）。
     * 头图复用 [SettingsHeroImage]，但 2026-10-15 加它时只挂上了那颗件、**没同步设置页
     * 那套"出血契约"**：墙的顶留白仍是 [gridTopPadding]（= 顶栏展开地板那一档），
     * 于是头图被顶栏整个推到它下面 —— 真机上读起来是"一条标题栏 + 一张图"两张皮，
     * 而不是"图铺满顶部、标题压在图上的白字"。
     *
     * 设置页那边头图摆在**内容列之外**（不吃 `topPadding`）所以天然出血；画廊的头图是
     * 墙的第一行（与 chrome 同处一颗 header item，见 [GalleryForYouPage.hero]），
     * 只能在这里把留白归零。
     *
     * 未设头图时恒为 [gridTopPadding] —— 首帧几何与从前逐像素一致。
     *
     * ⚠️ **判据不吃 `heroActive`**（2026-10-18 用户「在首页搜索框的时候，头图会异常显示」那条）：
     * 这一条量的是**头图自己摆在哪**，而头图住在主墙的 header item 里 ——
     * 搜索态那一屏主墙照旧在背景里（scrim 把它压暗，所以截图里仍看得见画师排与排行榜），
     * 头图也跟着照旧。搜索态把留白放回去 = 头图在背景里**凭空下移一截**，
     * 用户读到的就是"打开搜索、头图跳了一下 / 露出半张"。所以这里仍只问"设没设过头图"。
     */
    val wallTopPadding = if (galleryHeroFile != null) 0.dp else gridTopPadding

    /**
     * 搜索浮层的纵向落点 = 常驻 chrome 上边缘（状态栏之下、顶栏大标题之下）。
     *
     * chrome 现在是墙里的第一行内容（2026-09-30 第三版），首屏时它的上边缘恰好在
     * [topBarFloor] —— 卡片弹出来时**正好盖住**入口条的位置，"从入口条原地长开"的语义不变。
     * 顶栏展开高度用与各主 Tab 同一条地板常量（`statusBarTop + 104.dp` 那一档里的 104dp），
     * 不另量（同一处理由：`heightOffsetLimit` 读不可靠）。
     *
     * ⚠️ **头图在场时**入口条整体上移了 [topBarFloor] 那一段（墙顶留白归零，见 [wallTopPadding]）
     * ⇒ 落点必须跟着走，否则"从入口条原地长开"会退化成"卡从上方掉下来盖住入口条"。
     * 头图那一段的**占位高**就是 `状态栏 + settingsHeroHeightSubpage − settingsHeroOverlap`
     * （与 [SettingsHeroImage] 报给布局的高度同一个式子），入口条紧随其后 ⇒ 这里等于它的上边缘。
     *
     * ⚠️ 2026-10-16 换档（用户「画廊这个空位有点太多了…1选A吧」）：头图从 `settingsHeroHeight`
     * (208dp) 降到 `settingsHeroHeightSubpage` (148dp) —— 就是设置子页那一档，**不造新数字**。
     * 占位从 `状态栏 + 160dp` 收到 `状态栏 + 100dp`（少 60dp），图自己也矮一档。
     * 如实记两笔代价：清晰段只剩 **52dp**（`148 − fadeHeight 96`），标题白字落在图的中下段、
     * 正好压在淡出带上 —— 靠顶 scrim 保对比。真要再瘦，得动淡出带本身（那是共享件 C 方案）。
     * 两处**必须同一个常量**：这里写的式子与下面 `SettingsHeroImage` 的 `totalHeight` 是同一条，
     * 改一处不改另一处，"从入口条原地长开"立刻退化。
     *
     * ⚠️ **判据同样不吃 `heroActive`**（2026-10-18，同 `wallTopPadding` 那条理由）：
     * 落点跟着的是**入口条**（chrome）在哪儿，而入口条在主墙 header item 里、头图**下面** ——
     * 这与搜索态开不开没有关系（搜索态主墙仍在背景里，入口条还在老位置）。
     * 改成"搜索态一律 topBarFloor"会让卡从入口条上方掉下来盖住它。
     */
    val searchOverlayTop = if (galleryHeroFile != null) {
        statusBarTop + VeneraSpacing.settingsHeroHeightSubpage - VeneraSpacing.settingsHeroOverlap
    } else {
        topBarFloor
    }

    /**
     * 搜索卡**跟着顶栏折叠一起往上收**的那一段（2026-10-18，用户「搜索栏和顶部的间距还是太高了，压下」）。
     *
     * ── 那截空白是怎么来的 ──
     *
     * [searchOverlayTop] 取的是**展开态**地板（`statusBar + 104dp`）。但用户点 🔍 那一刻，
     * 墙往往已经滚过一段 —— 顶栏早就折成 52dp 那一行，大标题「搜索」的 alpha 归零、
     * **不占任何可见高度**，而卡照旧锚在 104dp 处 ⇒ 屏上读到的是
     * 「折叠顶栏 52dp + 一片什么都没有的 52dp + 卡片」，就是那句"间距还是太高"。
     *
     * ── 取值 ──
     *
     * `topBarFloor − topBarCollapsedHeight` = 104 − 52 = **52dp**，正是大标题那一行的高度，
     * 一枚新数字都没有。首屏（`collapsedFraction = 0`）位移为零 ⇒ 未滚动时的几何**逐像素不变**。
     *
     * ⚠️ 有头图时是 **96dp**（落点 148 − 52）：跟着 [searchOverlayTop] 走，不另立一套数字。
     * 首页态不挂折叠连接（`collapsedFraction` 恒 0）⇒ 这一截在首页本来就不动；
     * 搜索态连接挂回去之后（`heroActive`，见它的定义）它才开始起作用。
     *
     * ── 为什么用 `offset { }` 的 lambda 而不是直接算进 [searchOverlayTop] ──
     *
     * `collapsedFraction` 是滚动的逐帧读数。在组合期读它 = 滚动全程每帧重组这一整屏；
     * 放进布局阶段的 lambda 里读，只会让这一层重跑布局，组合照旧跳过。
     * （同一处理由：本文件那两处"读折叠进度会逐帧重组"的注释。）
     */
    val searchOverlayFollow =
        (searchOverlayTop - statusBarTop - VeneraSpacing.topBarCollapsedHeight).coerceAtLeast(0.dp)

    /**
     * 结果态给搜索结果墙的**稳定**顶部内缩（方案 B，2026-10-16 用户拍板「直接方案 b」）。
     *
     * ── 它解决什么 ──
     *
     * 结果态那张摘要卡是**浮层**（锚在 [searchOverlayTop] = [topBarFloor]，不推内容），
     * 于是首屏它正压在网格第一排图上。方案 A 那一路（把卡收窄到内容宽）只能**减少**遮挡面积，
     * 要"压图"这件事归零只有一条路：给网格一段自己的空白带，让卡落在带子里。
     *
     * ── 取值 ──
     *
     * `listRowMinHeight`(48dp，摘要卡在这一态的实际高度) + `space6`(12dp 收口) = 60dp。
     * 对齐关系：卡顶在 `topBarFloor + space2`、卡高 48dp ⇒ 卡底在 `topBarFloor + 50`，
     * 空白带从 `topBarFloor + 60` 起 ⇒ **卡底与第一排图上沿之间留 10dp**。
     *
     * ⚠️ 那 12dp 是用户 2026-10-16 点名要的（原话「搜索框离下面的图片要有点边距」）：
     * 上一版取 `space2`(4dp)，卡底到图上沿只剩 **2dp** —— 真机上读起来是"卡片贴在图上"。
     *
     * ⚠️ 当时把成因写成"卡片自己的投影（`elevation.attached` 3dp）把那条缝吃掉"，
     * 这一半是错的：玻璃档下那张卡**没有**投影了（见 `VeneraGlass.veneraSurfaceShadowElevation`，
     * 投影透出卡底正是"灰块"那条 bug）。12dp 这个值**不变** —— 它本来就是按"看得出是一段留白"
     * 定的，不是按"补掉 3dp 投影"定的；只是成因里去掉投影那一项。
     * 12dp 是"看得出是一段留白、又不至于把首屏白白让掉一行"的那一档，且是现成 token。
     * **一枚 token 都不新立**（两枚都是现成的）。
     *
     * ── 为什么这不犯 2026-09-30 撤"网格避让"那条 ──
     *
     * 当时否掉避让的理由**针对"padding 随滚动状态变化"**（会让整面墙跳一下）。
     * 这一条只随"进 / 出结果态"变化 —— 那本来就是一次整页切换（卡片 scaleIn 长开 / 收起），
     * 墙跟着让半屏高是顺的；**滚动过程中它是常量**（收没收起都不影响它），墙不会跳。
     *
     * ⚠️ 收起之后这段空白**照旧占位**是刻意的：那时它在屏上看不见了（已滚到顶栏之上），
     * 而回滚到顶时卡片正好落回它里面 —— 两边位置对得上，不会出现"卡片回来了但底下挤着"。
     */
    val searchResultsInset = if (svm.active && svm.mode == GallerySearchMode.RESULTS) {
        tokens.spacing.listRowMinHeight + tokens.spacing.space6
    } else {
        tokens.spacing.none
    }

    /**
     * 结果态那张搜索卡**收起**的判据 —— 与顶栏**居中标题**的出现同一条线（2026-10-16 用户拍板）。
     *
     * ── 阈值 1/3 的出处是 miuix 源码，不是拍出来的 ──
     *
     * `.reference/miuix-ui-094/…/basic/TopAppBar.kt:647-682` 两条逐字：
     * 大标题 `alpha = 1 − (collapsedFraction × 3f).coerceIn(0f, 1f)`、
     * 居中那行小标题 `visible = collapsedFraction × 3f >= 1f` —— 两条都落在 **1/3** 这一个点上。
     * 取 1/3 的语义因此是「居中标题一出来，搜索卡就收起」，两个动作同帧起跑
     * （出场曲线也取 miuix 那一颗 spring，见 [SEARCH_CARD_SHOW_SPEC]）。
     *
     * 从前这里是 `COLLAPSE_SETTLED_FRACTION`（0.99，完全折平才收）—— 用户真机读到的是
     * "我都滚过一屏了它还在"。
     *
     * ── 为什么是滞回而不是单阈值 ──
     *
     * 用户口径（原话）：「向上滑动内容到显示居中标题的时候搜索栏收入消失，
     * 向下拉到最顶的位置才重新显示」。单阈值做不到后半截 —— 那样滚回 1/3 之上它就弹回来了，
     * 而"退回到刚刚折平的位置"与"回顶"在用户心里是两件事。两个方向各给一条：
     * 上滑越过 1/3 收，**只有回到接近顶部才放出来**。
     *
     * 判据走 `snapshotFlow` 而不是 `derivedStateOf`：滞回**有记忆**、要写状态，
     * 而 `derivedStateOf` 只能算纯函数。滚动全程只在越线那两下各写一次（不是逐帧写）。
     */
    val topBarState = topBarBehavior.state
    var searchCollapsed by remember { mutableStateOf(false) }
    LaunchedEffect(topBarState, svm.active, svm.mode) {
        if (!svm.active || svm.mode != GallerySearchMode.RESULTS) {
            // 不在结果态（没搜 / 正在打字 / 反搜那一层）：这一格没有"收起"这件事，复位。
            searchCollapsed = false
            return@LaunchedEffect
        }
        snapshotFlow { topBarState.collapsedFraction }
            .collect { fraction ->
                when {
                    fraction >= SEARCH_CARD_COLLAPSE_AT -> searchCollapsed = true
                    fraction <= SEARCH_CARD_RESTORE_AT -> searchCollapsed = false
                }
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
    val followsStore = remember(context) { GalleryPorts.of(context).follows }
    val follows by followsStore.follows.collectAsState()
    // 名单档读不出来时这一栏只剩空引导，屏上必须说得出为什么空 —— 那份留档改名其实已经做了，
    // 只是不说就没人知道去哪儿找回（读走即清，只说一次）。
    LaunchedEffect(Unit) {
        followsStore.consumeNotice()?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    val artists = remember(follows, galleryFavorites) {
        GalleryFollowedArtists.rowOf(follows, galleryFavorites)
    }
    // ── 首页内容域与 Pixiv 那三节的取数（2026-10-17）──────────────────────────────
    //
    // 状态留在屏上而不是塞进 GalleryViewModel：那颗管的是"一屏墙 + 缓存 + 每日热门那台合并机器"，
    // 而 pixiv 这两条匿名腿今天**不参与任何合并**（选型⑤：它只从首页那一格进）。
    // 域与档位用 rememberSaveable（存的是枚举名，Bundle 认），到货的卡片用 remember（不落盘）。
    var homeDomain by rememberSaveable { mutableStateOf(GalleryHomeDomain.BOORU.name) }
    var pixivModeName by rememberSaveable { mutableStateOf(PixivRankingMode.DAILY.name) }
    var pixivExpanded by rememberSaveable { mutableStateOf(false) }
    val domain = GalleryHomeDomain.entries.first { it.name == homeDomain }
    // 内容域**同步进搜索那侧**（2026-10-17）：搜索卡要按域换一整套语法（booru 的标签胶囊
    // vs pixiv 的"一个词 + 站内参数"），而那面墙只认 `svm.results` —— 所以"这一墙是谁的图"
    // 必须由 ViewModel 自己知道，页面这一侧只负责把域报过去。
    //
    // 换域时 `setDomain` 会把手上那一轮清掉（在途掐掉 + 结果清空）：卡上写着 pixiv 的筛选、
    // 身下的墙却是 booru 的图，是本仓最忌的那种假读数。条件不抹，两边各留各的。
    LaunchedEffect(domain) { svm.setDomain(domain) }
    val rankingMode = PixivRankingMode.entries.first { it.name == pixivModeName }
    var pixivRanking by remember { mutableStateOf(emptyList<PixivWork>()) }
    var pixivRankingLoading by remember { mutableStateOf(false) }
    /** 这一档为什么是空的（站方那句原文）。null = 没有失败，空就是真的空。 */
    var pixivRankingCaption by remember { mutableStateOf<String?>(null) }
    /** 排行榜那一条也吃「下拉刷新」：它的 effect 键里没有别的用户动作可挂，单独给一枚计数器。 */
    var pixivRankingTick by remember { mutableStateOf(0) }
    /**
     * 榜单**按档缓存**（`"${mode}#${app|www}"` → 那一档那一次的结果）。
     *
     * 加它是因为 2026-10-17 第七轮用户那句「不要每次切页面都刷新，手动下拉才刷新」：
     * 从前 `domain` 是 effect 的键，但域一变 effect 就重跑 —— 切去图库站再切回 pixiv，
     * 排行榜与推荐各重发一遍，用户读到的是"每次切过来都在转"。
     * 现在 effect 仍挂在域上（否则切回来那一屏没人填），但**命中缓存就直接摆**。
     * 缓存只在「下拉刷新」那一下清空（`pixivRankingTick` 变 ⇒ 整批作废）。
     *
     * ⚠️ 它住在 [GalleryPixivHomeStore] 里（进程级），不是 `remember` —— 切主 Tab 时
     * 这条目的地会被 pop、`remember` 全部丢失，那一路也得靠这份存档才不重取。
     */
    val pixivRankingCache = GalleryPixivHomeStore.ranking
    val pixivStore = GalleryPixivHomeStore
    var pixivRecommend by remember { mutableStateOf(pixivStore.recommendWorks) }
    var pixivRecommendLoading by remember { mutableStateOf(false) }
    /** 瀑布流"下一屏"是否在取（与首屏分开：首屏空态与页脚转圈不是同一件事）。 */
    var pixivRecommendLoadingMore by remember { mutableStateOf(false) }
    var pixivRecommendCaption by remember { mutableStateOf(pixivStore.recommendCaption) }
    // 「还没判过档」那一道（口径同猜你喜欢的 `stage == IDLE`）：首帧既没到货也没失败，
    // 那一刻摆空态就是假读数 —— 屏上会说"pixiv 没回图"，而请求还没发出去。
    var pixivRecommendIdle by remember { mutableStateOf(pixivStore.recommendIdle) }
    // 空态那枚「重试」的把手：这一池的取数挂在 LaunchedEffect 的键上，不另起一台定时器。
    var pixivRecommendTick by remember { mutableStateOf(0) }
    // 「换一批」期间为真。刻意**不进存档**：它是一次操作的瞬时态，
    // 切走 Tab 时若还存着"正在换"，回来那颗胶囊就永远是禁按的（换早就结束了）。
    var pixivRecommendRemixLoading by remember { mutableStateOf(false) }
    /** 这一池是不是**站方推荐**（要登录）。false = 走"按收藏标签"那一跳（匿名可读）。 */
    var pixivRecommendStation by remember { mutableStateOf(pixivStore.recommendStation) }
    /** 站方推荐下一页的地址（`next_url` 原样）；null = 到底。 */
    var pixivRecommendNext by remember { mutableStateOf(pixivStore.recommendNext) }
    /** 种子那一跳当前取到第几页（它是 `p=` 翻页，与站方的游标不是一回事）。 */
    var pixivRecommendSeedPage by remember { mutableStateOf(pixivStore.recommendSeedPage) }
    var pixivRecommendExhausted by remember { mutableStateOf(pixivStore.recommendExhausted) }
    /**
     * 「这一档已经取过」的凭证（见 [GalleryPixivHomeStore]）。
     *
     * 与榜单那份缓存同一个理由，但推荐这一池没有"按档缓存"的形状可言（它的键里包着种子表），
     * 所以用一份"取过就不再动"的凭证：同键 ⇒ 直接 return，屏上原样留着。
     * 下拉刷新与空态「重试」都把它清成 null，所以它们仍能真的重发。
     *
     * ⚠️ 凭证里**不拼刷新计数**：拼了的话，重新组合（切 Tab 回来）计数归零、凭证永远对不上，
     * 于是每次回来都重取 —— 那正是这一条要治的病。
     */
    var pixivRecommendLoadedKey by remember { mutableStateOf(pixivStore.recommendLoadedKey) }

    /**
     * 关注画师 / 推荐用户（2026-10-17 第八轮，用户「关注画师你参考 pixez-miuix 是怎么拿到的」）。
     *
     * 两条腿都在 `app-api`、都要票据，形状同族（`user_previews[]`）：
     *  - `/v1/user/following?filter=for_android&user_id={自己}&restrict=public`
     *  - `/v1/user/recommended?filter=for_android`
     * 参数照 pixez-miuix 的 `UserRepository` 抄。**`filter=for_android` 是那条端点能不能答的关键** ——
     * 2026-10-17 我们先前量到 `user/recommended` 回 400/191，就是因为少了这一枚。
     *
     * ⚠️ 两栏**各存各的**：混成一排的话，用户会以为自己关注了根本没关注的人。
     */
    var pixivFollowing by remember { mutableStateOf(pixivStore.followingUsers) }
    var pixivRecommendedUsers by remember { mutableStateOf(pixivStore.recommendedUsers) }
    var pixivArtistsLoading by remember { mutableStateOf(false) }
    var pixivFollowCaption by remember { mutableStateOf(pixivStore.followingCaption) }
    var pixivUsersCaption by remember { mutableStateOf(pixivStore.usersCaption) }
    /** 「这两栏取过没有」的凭证。键 = 自己的 uid；登录态一变就作废（见 clear）。 */
    var pixivUsersLoadedKey by remember { mutableStateOf(pixivStore.usersLoadedKey) }
    /** 下拉刷新那枚把手：它一变就重发（凭证同时被清空）。 */
    var pixivArtistsTick by remember { mutableStateOf(0) }
    /**
     * 「画师」那一格摆哪一半（已关注 / 发现）。
     *
     * 与 `pixivRecommendStation` 同一层：这是**界面选择**，不是取数条件 —— 两条腿都取好了，
     * 它只决定摆哪一份。所以切它不触发任何 effect（也就不会闪一帧空态）。
     */
    var pixivArtistTab by remember { mutableStateOf(PixivArtistTab.FOLLOWING) }

    // 离开这一屏（切主 Tab / 返回）时把这一池写回进程级存档：回来直接摆，不重取。
    DisposableEffect(Unit) {
        onDispose {
            pixivStore.recommendWorks = pixivRecommend
            pixivStore.recommendCaption = pixivRecommendCaption
            pixivStore.recommendIdle = pixivRecommendIdle
            pixivStore.recommendStation = pixivRecommendStation
            pixivStore.recommendNext = pixivRecommendNext
            pixivStore.recommendSeedPage = pixivRecommendSeedPage
            pixivStore.recommendExhausted = pixivRecommendExhausted
            pixivStore.recommendLoadedKey = pixivRecommendLoadedKey
            pixivStore.followingUsers = pixivFollowing
            pixivStore.recommendedUsers = pixivRecommendedUsers
            pixivStore.followingCaption = pixivFollowCaption
            pixivStore.usersCaption = pixivUsersCaption
            pixivStore.usersLoadedKey = pixivUsersLoadedKey
        }
    }
    val pixivWorks = GalleryPorts.of(context).pixiv
    // 登录态只从这一条流读（与顶栏那颗头像、「关注画师」那一节同一个来源）。
    val pixivAccount = remember(context) { GalleryPorts.of(context).pixivAccount }
    val pixivAuth = pixivAccount?.authState?.collectAsState()?.value ?: PixivAuthState.Anonymous

    // 「推荐」那一节的种子：**图库站收藏里抽出来的推荐标签**。
    // 为什么借那一侧的语料：pixiv 今天还没有可收藏的卡（这一批才让它上墙），所以"你在 p 站
    // 收藏过什么"恒为空；而"你常看 1girl / mountain_river 这类标签"是真读数，
    // 拿它去 p 站按标签那一跳取几张新作，说的是同一件事实（现量：1girl 那一跳 60 条命中）。
    val pixivSeeds = remember(recommendations) {
        GallerySite.entries.filter { it != GallerySite.PIXIV }
            .mapNotNull { (recommendations[it] as? GalleryRecommendation.Tags)?.tags }
            .flatten().distinct().take(3)
    }

    // ── 排行榜（2026-10-17 第七轮重写）──
    //
    // 两件事在这条 effect 里定：
    // 1. **切域回来不重发**：命中 [pixivRankingCache] 就直接摆那一档（用户「不要每次切页面都刷新」）。
    //    缓存只在"用户下拉"那一下整批清空（见 [refreshWholePage]），所以刷新仍是真刷新。
    // 2. **登录着就走 app-api**：www 那条是匿名的，站方给不给看运气，而一旦不给屏上只有一句
    //    "这一档没有答上" —— 那张截图上排行榜整档空着就是它。app-api 要票据、答复里还带标题。
    LaunchedEffect(domain, rankingMode, pixivWorks, pixivRankingTick, pixivAuth) {
        val works = pixivWorks ?: return@LaunchedEffect
        if (domain != GalleryHomeDomain.PIXIV) return@LaunchedEffect
        val signedIn = pixivAuth is PixivAuthState.SignedIn
        // 档名 + 哪条腿一起进键：同一档在登录前后是两份不同的内容（www 匿名 / app-api 带票据），
        // 共用一个键就会把匿名那一批当成登录后的那一批摆上屏。
        val cacheKey = rankingMode.name + if (signedIn) "#app" else "#www"
        pixivRankingCache[cacheKey]?.let { snap ->
            pixivRanking = snap.works
            pixivRankingCaption = snap.caption
            return@LaunchedEffect
        }
        pixivRankingLoading = true
        pixivRankingCaption = null
        if (signedIn) {
            val bearer = pixivAccount?.bearerOrNull()
            if (bearer == null) {
                pixivRanking = emptyList()
                pixivRankingCaption = "这枚登录票据这一会儿取不到（可能刚过期）—— 点顶栏那颗头像重新登录一次。"
            } else {
                val result = works.rankingInApp(rankingMode, bearer)
                pixivRanking = result.getOrNull()?.works.orEmpty()
                pixivRankingCaption = result.exceptionOrNull()?.message
            }
        } else {
            // 匿名那一档仍走 www（带 `p=` 翻页、每页 50 条）。失败**不再吞掉**：
            // 从前 `.getOrNull().orEmpty()` 一吞，屏上只剩"没有答上"，读不出是限流还是真空。
            val result = works.ranking(rankingMode, page = 1)
            pixivRanking = result.getOrNull().orEmpty()
            pixivRankingCaption = result.exceptionOrNull()?.message
        }
        // 展开态要跟着档位一起重算：换了档还留着上一档的 21 张，读起来像"周榜和日榜一样"。
        pixivRankingCache[cacheKey] = PixivRankingSnapshot(pixivRanking, pixivRankingCaption)
        pixivRankingLoading = false
    }

    // ── 关注画师 / 推荐用户（2026-10-17 第八轮）──
    //
    // 两条腿都在 app-api、都要票据，形状同族（`user_previews[]`）：
    //  - `/v1/user/following?filter=for_android&user_id={自己}&restrict=public`
    //  - `/v1/user/recommended?filter=for_android`
    // 参数照 pixez-miuix 的 `UserRepository` 抄。**`filter=for_android` 是那条端点能不能答的关键** ——
    // 我们先前量到 `user/recommended` 回 400/191，缺的就是这一枚。
    //
    // 两栏**各存各的**：混成一排的话，用户会以为自己关注了根本没关注的人。
    LaunchedEffect(domain, pixivAuth, pixivWorks, pixivArtistsTick) {
        val works = pixivWorks ?: return@LaunchedEffect
        if (domain != GalleryHomeDomain.PIXIV) return@LaunchedEffect
        val me = (pixivAuth as? PixivAuthState.SignedIn)?.userId
        if (me.isNullOrBlank()) {
            // 未登录（或这一档没带 user_id）：这两栏本来就要票据与自己的用户号。
            // 屏上那几句由节里按登录态念，不在这里造第二套文案。
            pixivFollowing = emptyList()
            pixivRecommendedUsers = emptyList()
            pixivFollowCaption = null
            pixivUsersCaption = null
            pixivUsersLoadedKey = null
            // 已登录却**没存下用户号**（老版本写下的登录记录）：关注那一跳的 `user_id` 没有值可传。
            // 这一档不能沉默 —— 否则顶栏说"已登录"、这一栏说"你还没关注人"，两句话在屏上打架。
            if (pixivAuth is PixivAuthState.SignedIn) {
                val line = "这一版没记下你的 pixiv 用户号 —— 点顶栏那颗头像重新登录一次，这里就能读出来。"
                pixivFollowCaption = line
                pixivUsersCaption = line
            }
            return@LaunchedEffect
        }
        val requestKey = "users:$me"
        if (pixivUsersLoadedKey == requestKey) return@LaunchedEffect
        val bearer = pixivAccount?.bearerOrNull()
        if (bearer == null) {
            // 票据取不到是**这一刻**的事，不记账：下一次组合还能再试，别把失败钉成空态。
            pixivFollowCaption = "这枚登录票据这一会儿取不到（可能刚过期）—— 点顶栏那颗头像重新登录一次。"
            pixivUsersCaption = null
            return@LaunchedEffect
        }
        pixivArtistsLoading = true
        val following = works.following(bearer, me, PixivRestrict.Public)
        pixivFollowing = following.getOrNull()?.users.orEmpty()
        pixivFollowCaption = following.exceptionOrNull()?.message
        val recommended = works.recommendedUsers(bearer)
        pixivRecommendedUsers = recommended.getOrNull()?.users.orEmpty()
        pixivUsersCaption = recommended.exceptionOrNull()?.message
        pixivArtistsLoading = false
        pixivUsersLoadedKey = requestKey
    }

    // 登录态一变，两条腿的缓存整批作废：登录前是匿名那份、登录后该是站方那份（反之亦然）。
    // 不做这件事的话，用户刚登完仍然看着匿名那一批，直到下拉一次才换 —— 屏上看不出来的错配。
    //
    // ⚠️ 首帧**不许**清：这条 effect 在每次组合开始都会跑一次，而那一刻屏上的数据正是
    // 上一屏写回 [GalleryPixivHomeStore] 的存档（切 Tab 回来就靠它）。所以先记一枚"见过的登录态"，
    // 只有它**真的变了**才清 —— 否则每次回到画廊都会把刚恢复的那一池抹掉。
    var pixivAuthSeen by remember { mutableStateOf(pixivAuth) }
    LaunchedEffect(pixivAuth) {
        if (pixivAuth == pixivAuthSeen) return@LaunchedEffect
        pixivAuthSeen = pixivAuth
        pixivStore.clear()
        pixivRecommendLoadedKey = null
        pixivRecommend = emptyList()
        pixivRecommendCaption = null
        pixivRecommendStation = false
        pixivRecommendNext = null
        pixivRecommendSeedPage = 1
        pixivRecommendExhausted = false
        pixivRecommendIdle = true
        // 关注 / 推荐画师也整批作废：换了账号之后还摆着上一个号关注的人，那是错认。
        pixivFollowing = emptyList()
        pixivRecommendedUsers = emptyList()
        pixivFollowCaption = null
        pixivUsersCaption = null
        pixivUsersLoadedKey = null
    }

    LaunchedEffect(domain, pixivSeeds, pixivWorks, pixivRecommendTick, pixivAuth) {
        val works = pixivWorks ?: return@LaunchedEffect
        if (domain != GalleryHomeDomain.PIXIV) return@LaunchedEffect
        val signedIn = pixivAuth is PixivAuthState.SignedIn
        // 「这一次该取什么」的键：登录态 + （未登录时的）种子表。
        // 键没变 ⇒ 屏上这一池就是它的结果，直接留着 —— 切来切去不再重发（同排行榜那条）。
        // ⚠️ 里面**不拼刷新计数**：拼了的话重新组合时计数归零、凭证永远对不上（见存档那段）。
        val requestKey = if (signedIn) "app" else "seeds:" + pixivSeeds.joinToString(",")
        if (pixivRecommendLoadedKey == requestKey) return@LaunchedEffect
        // ── 已登录：读**站方推荐**（2026-10-17 第五轮，用户「下面推荐应该自动换成 pixiv 站方的推荐」）──
        //
        // 这一档与下面那一档不是"同一个东西的两种实现"：
        //  - 站方推荐按**你看过谁、收藏过谁**算，要不要给是站方决定的（`/v1/illust/recommended`，必须带票据）；
        //  - 种子那一跳是拿图库站的标签去 p 站搜，它是**我们的**口味推断，匿名也能读。
        // 同一句空态会把两种处境读成一种，所以读数那份也分了两档（`pixivRecommendReading(station=…)`）。
        if (signedIn) {
            pixivRecommendStation = true
            pixivRecommendLoading = true
            pixivRecommendIdle = false
            pixivRecommendCaption = null
            // 翻页状态跟着首屏一起重置：不重置的话第一屏落下来、页脚却以为还有"下一页"，
            // 一滑到底就把第一页又取一遍。
            pixivRecommendNext = null
            pixivRecommendSeedPage = 1
            pixivRecommendExhausted = false
            // 票据**现取**（`bearerOrNull` 内部会在快过期时先刷一次）；取不到就如实说，
            // 不许静默退回种子那一跳 —— 那会让"登录着却还是旧内容"成为屏上看不出来的错配。
            val bearer = pixivAccount?.bearerOrNull()
            if (bearer == null) {
                pixivRecommend = emptyList()
                pixivRecommendExhausted = true
                pixivRecommendCaption = "这枚登录票据这一会儿取不到（可能刚过期）—— 点顶栏那颗头像重新登录一次。"
            } else {
                val result = works.recommended(bearer)
                val page = result.getOrNull()
                pixivRecommend = page?.works.orEmpty()
                pixivRecommendNext = page?.nextUrl
                // `next_url` 缺席 = 站方说没有了（这一池到底）。失败那一档也照此：墙上没内容，
                // 续页判据本来就不会跑（它要求墙上已有卡片）。
                pixivRecommendExhausted = page?.nextUrl == null
                pixivRecommendCaption = result.exceptionOrNull()?.message
            }
            pixivRecommendLoading = false
            pixivRecommendLoadedKey = requestKey
            return@LaunchedEffect
        }
        // ── 未登录：种子标签那一跳（匿名可读，见上）──
        pixivRecommendStation = false
        pixivRecommendNext = null
        pixivRecommendSeedPage = 1
        pixivRecommendExhausted = false
        if (pixivSeeds.isEmpty()) {
            pixivRecommend = emptyList()
            pixivRecommendCaption = "还没有能当种子的收藏：在大图上点收藏，这里会按那些标签去 pixiv 取"
            pixivRecommendIdle = false
            pixivRecommendLoadedKey = requestKey
            return@LaunchedEffect
        }
        pixivRecommendLoading = true
        val picked = ArrayList<PixivWork>()
        pixivSeeds.forEach { seed ->
            val found = works.tagWorks(seed, page = 1).getOrNull().orEmpty()
            picked += found.take(GalleryPixivHome.RECOMMEND_PER_SEED)
        }
        // 三枚种子之间去重：同一张图会在好几个标签下命中，不去重就是同一张连着摆两遍
        // （这一族本仓犯过：见记忆「按收藏推荐那轮的复读页」）。
        // 与**排行榜那一排**没有去重：那是「按分数/时间排行」的取数、这是「按种子标签的最新作」，
        // 两批不是同一次命中，而墙上这一池是瀑布流、上面那排是带标题的小卡，形状也不同。
        pixivRecommend = picked.distinctBy { it.post.uid }
        pixivRecommendCaption = if (pixivRecommend.isEmpty())
            "种子那几枚标签这一轮没从 pixiv 取回图（「${pixivSeeds.joinToString("、")}」）" else null
        pixivRecommendLoading = false
        pixivRecommendIdle = false
        pixivRecommendLoadedKey = requestKey
    }

    // ── 推荐那一面墙的**无限下拉**（2026-10-17 第七轮，用户「这个推荐也是要做成瀑布流的无限下拉的」）──
    //
    // 判据复用图库站那道 [GalleryLoadMoreState]（同一套三道闸门：墙得是活的、到底了不判、在取时不并发），
    // 只是"下一页"的取法分两档：
    //  - 站方推荐：地址就是上一页答复里的 `next_url`（站方自己带的 `offset`/`filter`，我们不拼）；
    //  - 种子那一跳：`p = 页码 + 1`，三枚种子各取一页、去重后追加。
    // 追加时**一律按 uid 去重**：站方推荐跨页会重复（它按兴趣打分，不是按页切），
    // 不去重就是同一张图在瀑布流里连着出现两遍 —— 这一族本仓犯过一次（复读页那轮）。
    LaunchedEffect(forYouGridState, domain, pixivWorks) {
        val works = pixivWorks ?: return@LaunchedEffect
        if (domain != GalleryHomeDomain.PIXIV) return@LaunchedEffect
        snapshotFlow {
            val info = forYouGridState.layoutInfo
            GalleryLoadMoreState(
                wallActive = pixivRecommend.isNotEmpty(),
                exhausted = pixivRecommendExhausted,
                busy = pixivRecommendLoading || pixivRecommendLoadingMore,
                failed = false,
                settled = true,
                lastIndex = info.totalItemsCount - 1,
                visibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1,
            )
        }
            .distinctUntilChanged()
            .collect { state ->
                if (!state.wallActive || state.exhausted || state.busy) return@collect
                if (state.lastIndex < 0 || state.visibleIndex < state.lastIndex - LOAD_MORE_AHEAD) return@collect
                pixivRecommendLoadingMore = true
                if (pixivRecommendStation) {
                    val next = pixivRecommendNext
                    val bearer = pixivAccount?.bearerOrNull()
                    if (next == null || bearer == null) {
                        // 没有游标 / 票据这一刻取不到：这一轮到此为止，别把页脚永远转着。
                        pixivRecommendExhausted = true
                    } else {
                        val result = works.recommended(bearer, next)
                        val page = result.getOrNull()
                        if (page == null) {
                            pixivRecommendCaption = result.exceptionOrNull()?.message
                        } else {
                            val seen = pixivRecommend.mapTo(HashSet()) { it.post.uid }
                            pixivRecommend = pixivRecommend + page.works.filter { it.post.uid !in seen }
                            pixivRecommendNext = page.nextUrl
                            if (page.nextUrl == null) pixivRecommendExhausted = true
                        }
                    }
                } else {
                    val nextPage = pixivRecommendSeedPage + 1
                    val picked = ArrayList<PixivWork>()
                    pixivSeeds.forEach { seed ->
                        picked += works.tagWorks(seed, page = nextPage).getOrNull().orEmpty()
                    }
                    val seen = pixivRecommend.mapTo(HashSet()) { it.post.uid }
                    val fresh = picked.filter { it.post.uid !in seen }
                    if (fresh.isEmpty()) {
                        // 三枚种子这一页都没新东西 = 到底了（再往后取只会是同一批）。
                        pixivRecommendExhausted = true
                    } else {
                        pixivRecommend = pixivRecommend + fresh.distinctBy { it.post.uid }
                        pixivRecommendSeedPage = nextPage
                    }
                }
                pixivRecommendLoadingMore = false
            }
    }

    /**
     * 「换一批」= 把整面墙换成**下一批**（2026-10-17 第二十三轮，用户「推荐标题右边要多个换一批的胶囊」）。
     *
     * ## 两腿各有各的"下一批"
     *
     * - **站方推荐**（登录态）：`next_url` 那一页；
     * - **种子腿**（匿名）：`pixivRecommendSeedPage + 1`，按 `page` 参数翻 —— 续页那一段就是走这条，
     *   所以换批不是新发明的一条取数路，只是把它**提前触发**。
     *
     * ## 关键差别：**替换**，不是追加
     *
     * 续页是追加（用户滑到底想要更多），换一批是替换（按下去要看见"整墙都换了"）。
     * 追加会让按过之后屏上多出十几张旧图 —— 那读起来是"加载更多"，不是"换一批"。
     *
     * 换完把墙**滚回顶**（`scrollToItem(0)`，第 0 项是头图）：留在原滚动位置的话，
     * 用户看到的是"中间某一段忽然变成别的图"，读不出刚才那一下是换批。
     *
     * 失败时**保留原来那一墙**并念站方原话 —— 换批失败就把屏清空的话，按一下等于删了一屏内容。
     */
    fun remixPixivRecommend() {
        val works = pixivWorks ?: return
        if (pixivRecommendRemixLoading) return
        scope.launch {
            pixivRecommendRemixLoading = true
            pixivRecommendCaption = null
            try {
                if (pixivRecommendStation) {
                    val next = pixivRecommendNext
                    val bearer = pixivAccount?.bearerOrNull()
                    if (next == null || bearer == null) {
                        pixivRecommendCaption = "这一池已经翻到底了，站方没有给下一批。"
                    } else {
                        val result = works.recommended(bearer, next)
                        val page = result.getOrNull()
                        if (page == null || page.works.isEmpty()) {
                            pixivRecommendCaption =
                                result.exceptionOrNull()?.message ?: "下一批这一回没有答上东西。"
                        } else {
                            pixivRecommend = page.works
                            pixivRecommendNext = page.nextUrl
                            pixivRecommendExhausted = page.nextUrl == null
                            forYouGridState.scrollToItem(0)
                        }
                    }
                } else {
                    if (pixivSeeds.isEmpty()) {
                        pixivRecommendCaption = "还没有可用来换的种子标签（先下拉刷新一次）。"
                    } else {
                        val nextPage = pixivRecommendSeedPage + 1
                        val picked = ArrayList<PixivWork>()
                        pixivSeeds.forEach { seed ->
                            picked += works.tagWorks(seed, page = nextPage).getOrNull().orEmpty()
                        }
                        val fresh = picked.distinctBy { it.post.uid }
                        if (fresh.isEmpty()) {
                            // 换批失败/翻到底都落到这里：**不动原来那一墙**，只说一声。
                            pixivRecommendExhausted = true
                            pixivRecommendCaption = "这几枚标签的下一页也没有新东西了。"
                        } else {
                            pixivRecommend = fresh
                            pixivRecommendSeedPage = nextPage
                            pixivRecommendExhausted = false
                            forYouGridState.scrollToItem(0)
                        }
                    }
                }
            } finally {
                pixivRecommendRemixLoading = false
            }
        }
    }

    // ── 整页下拉刷新（2026-10-17 第五轮）──
    //
    // "整页"= 这一屏上所有腿都重发：图库站那侧是日榜 + 猜你喜欢两腿；Pixiv 那侧是排行榜 + 推荐两腿。
    // 只刷脚下那面墙会留下"上面那排还是旧的"，而用户读到的是一屏没刷新。
    //
    // `pullRefreshing` 是**屏上自己记的一次**，不直接吃任何一条腿的 loading：那几条 loading
    // 同时被"切域""换档位""加载更多"复用，照搬会把那些动作也点亮成"正在下拉刷新"。
    // 收尾的条件与网络收藏那屏同形：**先见过 loading 起来、再落下**才算这次结束；
    // 一条腿都没起 loading（比如刷新同步早退）时兜一个 600ms，别让那一行永远挂着。
    var pullRefreshing by remember { mutableStateOf(false) }
    var pullSawLoading by remember { mutableStateOf(false) }
    val pullLoading = vm.isLoadingFeed || fvm.isLoading || pixivRecommendLoading || pixivRankingLoading
    LaunchedEffect(pullRefreshing, pullLoading) {
        if (!pullRefreshing) {
            pullSawLoading = false
            return@LaunchedEffect
        }
        if (pullLoading) {
            pullSawLoading = true
            return@LaunchedEffect
        }
        if (!pullSawLoading) {
            delay(600)
            if (pullRefreshing && !pullLoading && !pullSawLoading) pullRefreshing = false
        } else {
            pullRefreshing = false
        }
    }
    val refreshWholePage: () -> Unit = {
        pullSawLoading = false
        pullRefreshing = true
        if (domain == GalleryHomeDomain.PIXIV) {
            // 下拉 = **唯一**清缓存的地方（用户 2026-10-17：「不要每次切页面都刷新，手动下拉才刷新」）。
            // 那几枚 tick 只是让对应 effect 重跑；真正让它们"不命中缓存"的是这几行清空。
            pixivRankingCache.clear()
            pixivRecommendLoadedKey = null
            // 关注 / 推荐画师也一起重取（这三栏都在同一屏，用户下拉时的心智是"整页都刷"）。
            pixivUsersLoadedKey = null
            pixivRankingTick++
            pixivRecommendTick++
            pixivArtistsTick++
        } else {
            // 两腿都走"用户主动"那条路：先清 60 秒熔断再置 force，否则按下必然还是同一句
            // "熔断中" —— 那就是一枚假按钮（这两枚把手存在的意义正是防它）。
            //
            // ⚠️ 日榜这一腿用 [GalleryViewModel.refreshKeepingContent] 而不是 `refresh()`：
            // 后者会把 `posts` 清空 ⇒ 下拉那一下整面墙换成骨架、等三站都回来才重新长出来
            // （用户同一条反馈里的「每日热门这个加载时间太长了」多半就是这个"先清再等"）。
            // 现在旧屏留着，新的一批落地时整屏替换。
            resetBreakers()
            forceNextLoad = true
            vm.refreshKeepingContent()
            retryForYouFromUser()
        }
    }

    // 切域 = 第 2~4 节的 key 整批改换。懒列表在第 0 项之后换项会让视口锚点漂 ——
    // 所以回到顶部，别让用户落在"换了内容的那一格"上。
    LaunchedEffect(domain) { forYouGridState.scrollToItem(0) }

    // 打码那一把与首页其余三批**同一颗** `buildGalleryWall`（同一张图在两处待遇不同是最难解释的不一致）。
    //
    // ⚠️ 2026-10-17 第八轮（用户截图「排行榜为啥都说这样显示 r18，我这边屏蔽也没开」）：
    // 这里从前写的是 `cards.mapTo(HashSet()) { … }` —— 而 `GalleryWall.cards` 是**保留下来的全部卡**
    // （`buildGalleryWall` 里 `masked = true` 与 `masked = false` **都**进 `kept`），
    // 所以那一句等于"每一张都在打码集合里" ⇒ 排行榜**恒全糊 + 恒画 R18 角标**，
    // 与 rating、与 `nsfw_mode` 全都无关（`maskMode = OFF` 时也一样糊，因为这条判据根本不看 mode）。
    // 正确写法是先按 `card.masked` 滤一遍：这个集合的语义是"命中打码的那几张"，不是"这一墙有哪几张"。
    val pixivRankingMasked = remember(pixivRanking, maskMode, rules, blockAi) {
        buildGalleryWall(pixivRanking.map { it.post }, maskMode, blockAi = blockAi) { post ->
            guard.blockedGalleryRule(author = post.author, tags = post.tagList)?.pattern
        }.cards.filter { it.masked }.mapTo(HashSet()) { it.post.uid }
    }

    // ── 首页那面墙由谁填（2026-10-17 用户点名：pixiv 的推荐走「猜你喜欢」那种墙，不再是卡片排）──
    // 打码那把仍是同一颗 buildGalleryWall：同一张图在两处待遇不同是最难解释的不一致。
    val pixivRecommendWall = remember(pixivRecommend, maskMode, rules, blockAi) {
        buildGalleryWall(pixivRecommend.map { it.post }, maskMode, blockAi = blockAi) { post ->
            guard.blockedGalleryRule(author = post.author, tags = post.tagList)?.pattern
        }
    }
    /**
     * 推荐那面墙的**站方标题**（`uid → title`，2026-10-17 第七轮）。
     *
     * 病根：那一池的卡在屏上**只有图、没有标题行** —— 墙上那行标题是 `GalleryCardTitle`
     * 拿标签去离线词典**算**出来的，而 pixiv 的标签是日文（`女の子`、`制服`），
     * 词典里一条都没有 ⇒ 算不出来 ⇒ 整行不画。
     * 站方其实每行都给了 `title`（`PixivWork.title`），直接摆它就行。
     *
     * ⚠️ **空串也收进表**：`PixivWork.title` 有时是空串，那表示"站方这一张就是无题"。
     * 收进去 = 覆盖掉算出来的那批；不收 = 又退回去摆我们猜的那个（两种来源混成一层）。
     * 墙那一侧会把空串折成"这一行不画"。
     */
    val pixivRecommendTitles = remember(pixivRecommend) {
        pixivRecommend.associate { it.post.uid to it.title }
    }
    /**
     * 作者头像表（`uid → 地址`，第二十四轮）。
     *
     * 同 [pixivRecommendTitles] 那把路径：整批摊平成一张表，而不是让每张卡自己回头找那条作品 ——
     * 地址本来就躺在 `PixivWork` 里，跟着那一池走，缺哪条就是空串 ⇒ 卡上退回首字母座。
     */
    val pixivRecommendAvatars = remember(pixivRecommend) {
        pixivRecommend.associate { it.post.uid to it.authorAvatarUrl }
    }
    val homeWallReading = if (domain == GalleryHomeDomain.PIXIV) {
        pixivRecommendReading(
            wall = pixivRecommendWall,
            pending = pixivRecommendIdle || pixivRecommendLoading,
            seeds = pixivSeeds,
            caption = pixivRecommendCaption,
            onRetry = { pixivRecommendTick++ },
            station = pixivRecommendStation,
            loadingMore = pixivRecommendLoadingMore,
            exhausted = pixivRecommendExhausted,
        )
    } else {
        // 空态那句"有 N 张收藏却抽不出标签"要说得出张数 —— 不说就与"你还没收藏"分不开。
        // 首页没有"另一面墙"可切（每日热门搬去了独立二级页），所以 onGoToDaily 恒 null：
        // 摆一枚把同一面墙重滚一次的按钮就是假开关。
        forYouWallReading(
            fvm = fvm,
            wall = forYouWall,
            favoriteCount = galleryFavorites.size,
            onRetry = { retryForYouFromUser() },
            onGoToDaily = null,
        )
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
        domain = domain,
        pixiv = PixivHomeState(
            rankingMode = rankingMode,
            ranking = if (pixivExpanded) pixivRanking.take(GalleryPixivHome.RANKING_EXPANDED_ITEMS) else pixivRanking,
            rankingLoading = pixivRankingLoading,
            rankingExpanded = pixivExpanded,
            maskedOf = { post -> post.uid in pixivRankingMasked },
            onPickRankingMode = { picked ->
                pixivModeName = picked.name
                pixivExpanded = false
            },
            onToggleRankingExpanded = { pixivExpanded = !pixivExpanded },
            onOpen = { pool, post ->
                GalleryViewerQueue.set(pool.map { it.post })
                onOpenPost(post.site, post.id)
            },
            // 「关注画师」那一节的话要跟顶栏那颗头像同源（同一个 authState，不另算一遍）。
            credentials = GalleryPorts.of(context).pixivAccount,
            // 未登录时「推荐」那一池是**按收藏标签**取的，不是站方推荐 —— 屏上要说出来，
            // 否则会被读成"站方推荐就长这样"。站方推荐在场时不摆话（真读数不需要解释自己）。
            recommendNote = if (pixivRecommendStation) null else
                "还没登录 pixiv：这一栏暂时按你收藏里的标签去站里找。登录后换成站方推荐。",
            // 空那一档念站方原话（见 `PixivHomeState.rankingCaption` 的理由）。
            rankingCaption = pixivRankingCaption,
            // 「换一批」（第二十三轮）：能不能摆 = 那一腿现在**有没有下一批**。
            // 站方腿看 `next_url`（翻到底就不摆，那一按没有下一批可取）；
            // 种子腿看有没有种子标签（匿名那一跳的"下一批"是按 `page` 往后翻，不会真的翻到底）。
            recommendRemixable = if (pixivRecommendStation) {
                pixivRecommendNext != null
            } else {
                pixivSeeds.isNotEmpty()
            },
            recommendRemixLoading = pixivRecommendRemixLoading,
            onRemixRecommend = { remixPixivRecommend() },
            // 关注 / 推荐画师（2026-10-17 第八轮）：两栏分开放，各自一套空/忙/失败。
            following = pixivFollowing,
            recommendedUsers = pixivRecommendedUsers,
            artistsLoading = pixivArtistsLoading,
            followingCaption = pixivFollowCaption,
            usersCaption = pixivUsersCaption,
            // 点一位画师 = **进画师页**（用户 2026-10-19：「改成点击画师头像进入画师页」）。
            // 从前这里开的是站方网页（`openPixivUserPage`）—— 那时确实没有一屏可去。
            // 现在有了，网页退成屏内那枚「在 pixiv 打开」的正当出口。
            onOpenUser = { user -> context.openPixivArtistPage(user.id, user.name) },
            artistTab = pixivArtistTab,
            onPickArtistTab = { pixivArtistTab = it },
            // 排行榜卡那颗 ♥：走**本地收藏档**（与收藏页 / 大图页那颗同一份数据）。
            // 集合按 uid 建，"这张在不在收藏里"是一次哈希查表 —— 榜单一排三张，逐张线性扫
            // 收藏表在几百条时会变成每帧几百次比较。
            favoritedOf = { post -> post.uid in galleryFavoriteUids },
            onToggleFavorite = { post ->
                scope.launch { GalleryPorts.of(context).favorites.toggle(post) }
            },
        ),
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
    val searching = svm.active && svm.hasQuery
    // 结果墙只在「这一轮真的取过（page > 0）或正在取**或这一轮已经说过一句失败**」时接管。
    //
    // `searchError != null` 那一支是 2026-10-15 补的：此前只有前两支，于是**第 1 页整轮失败**
    // （`beginSearch` 把 page 归零、失败路径又不走 `appendPage`，page 就停在 0）时这一句判据
    // 当场为假 —— 内容区落回**首页那面墙**，用户读到的是"搜不到就自己退回主页"，
    // 而下面那台专门给失败态的空态（"这一轮没搜成" + 条件 + 排行 + 改条件）永远命中不了。
    // 真机读数：`_qa/ui-verify-2026-10-15/scr-23-39-search-fail-fell-back-home.jpg` ——
    // 条件行「loli ×」「排行：年」还挂在顶上，身子已经是首页（正在关注的画师 / 每日热门）。
    //
    // ⚠️ 条件刚装好、还没搜时仍显示主墙 —— 否则会摆出一屏"0 张"的假空态（不是没有结果，是还没搜）。
    // 这条没动，也**不能**用 `svm.legFailures` 非空来代替 `searchError`：
    // 一腿成、一腿败时屏上是有内容的，那一档由页尾与展开区说，不该把整面墙换成错误页。
    val searchWallActive = searching && (svm.page > 0 || svm.isSearching || svm.searchError != null)

    /**
     * pixiv 那一屏现在是不是在**画师**页签上。
     *
     * 这一格决定的是"摆哪一面墙"，与 `searchWallActive`（作品那面瀑布流）是**并列**的两件事，
     * 不是它的子集：画师那一栏没有 `page` / `exhausted` 那套（它有自己的 `pixivArtistPage`），
     * 拿作品那面墙的判据去推它，只会在两边各写一遍之后慢慢分叉。
     */
    val pixivArtistMode = domain == GalleryHomeDomain.PIXIV &&
        svm.pixivKind == PixivSearchKind.ARTIST

    // 页尾那一行：搜索与猜你喜欢各一句，**类型显式写出来**（`when` 里混 `@Composable {}` 字面量
    // 会被推成 Unit?，编译器就不认了）。
    val searchFooter: (@Composable () -> Unit)? = if (searchWallActive && !pixivArtistMode) {
        @Composable {
            GallerySearchEnd(
                cards = searchWall.cards.size,
                // 报的是**真正发出去的那串**（预算内那几枚），不是全部胶囊 ——
                // 页尾是读数，读数与实际请求不一致就是假读数。
                query = svm.footerQuery,
                page = svm.page,
                exhausted = svm.exhausted,
                loadingMore = svm.isLoadingMore,
                blockedCount = searchWall.blockedCount,
                blockedRules = searchWall.blockedRules,
                hiddenByRating = searchWall.hiddenByRating,
                videos = searchWall.cards.count { it.post.isVideo },
                noImage = svm.noImageNotice(),
                // 排法与日期区间一起念：切了档而页尾读不出区别，用户只会怀疑那一下没生效。
                ranking = svm.footerRankingReading(),
                // 条件被预算砍过也要在这儿说一遍：收成一条之后展开区不画了，
                // 那句提示只在展开态看得见，页尾得自己承担这个交代。
                trimmedNote = svm.footerTrimmedNote(),
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
            // 判据吃 `svm.hasQuery`（与下面续页那一处同一条）：pixiv 那一域没有标签胶囊，
            // 按 `filters` 判会让它的落点一次都记不下来。
            val wallActive = svm.active && svm.hasQuery && (svm.page > 0 || svm.isSearching)
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
    // ⚠️ "这一轮已经落过点"的凭据原先记在这里（`var scrolledGeneration by remember`），
    // 那是本次修复的病灶：`LaunchedEffect` 在组合重建后会**再跑一次首帧**，而 `remember` 跟着
    // 组合归零 ⇒ 守卫形同不存在 ⇒ 每次从别的页/别的 Tab 回到画廊搜索都被按回第 1 张。
    // 凭据搬进 `svm.appliedGeneration`（与它守卫的那个 `searchGeneration` 住同一层）。
    LaunchedEffect(svm.searchGeneration) {
        if (svm.searchGeneration == 0) return@LaunchedEffect
        if (svm.appliedGeneration == svm.searchGeneration) return@LaunchedEffect
        if (!(svm.active && svm.hasQuery)) return@LaunchedEffect
        gridState.scrollToItem(svm.consumeScrollRestore() ?: 0)
        svm.appliedGeneration = svm.searchGeneration
    }

    // 结果滚到离底几张以内就续下一页（搜索那面墙）。
    // 三道闸门原样保留（没滚回顶部不判、续页刚失败不自动重试、在取时不并发）。
    //
    // ⚠️ 2026-10-18「pixiv 搜索下滑无法继续获取更多新图」的根因就在这一行：
    // 判据原先是 `svm.filters.isNotEmpty()` —— 那是 **booru 的标签胶囊**，而 pixiv 那一域
    // 根本没有胶囊（词在 `pixivTerm` 里）⇒ `wallActive` 恒为假 ⇒ 续页 effect 一次都不触发。
    // 现在走 `svm.hasQuery`（两个域各按各的判据），与返回键、空态那几处同一个口径。
    LaunchedEffect(gridState) {
        snapshotFlow {
            val wallActive = svm.active && svm.hasQuery && (svm.page > 0 || svm.isSearching)
            val info = gridState.layoutInfo
            GalleryLoadMoreState(
                wallActive = wallActive,
                exhausted = svm.exhausted,
                busy = svm.isSearching || svm.isLoadingMore,
                failed = svm.loadMoreError != null,
                settled = svm.appliedGeneration == svm.searchGeneration,
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

            // ── pixiv 的「画师」页签（2026-10-18）──
            //
            // 这一栏摆的是**人**，不是图：它走 `/v1/search/user`，结果是一份名单，
            // 与下面那面瀑布流不是同一种墙（所以三支都排在那面墙前面）。
            // 判据里带 `svm.active`：退出搜索之后这一屏该回到首页主墙，而不是留着上一次的名单。
            pixivArtistMode && svm.active &&
                svm.pixivArtists.isEmpty() && svm.isSearchingArtists -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = gridTopPadding + searchResultsInset)
                    .imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(tokens.spacing.loaderPage),
                    color = tokens.color.primary,
                    trackColor = tokens.color.surfaceVariant,
                )
            }

            pixivArtistMode && svm.active &&
                svm.pixivArtists.isEmpty() && !svm.isSearchingArtists -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = gridTopPadding + searchResultsInset,
                        start = tokens.spacing.screenHorizontal,
                        end = tokens.spacing.screenHorizontal,
                    )
                    .imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                VeneraEmptyView(
                    // 两档分开说：**取失败**（没登录 / 站方没答上）与**真的没有这个人**是不同的事。
                    // 合成一句"没找到"会让用户以为名字打错了，而实际是没登录。
                    title = if (svm.pixivArtistError != null) "画师没取到" else "没有这位画师",
                    message = svm.pixivArtistError
                        ?: "pixiv 的画师是按名字搜的。换个写法，或者切回「作品」那一栏试试。",
                    icon = Icons.Outlined.PersonSearch,
                    actionText = "改条件",
                    onAction = { svm.backToInput() },
                )
            }

            pixivArtistMode && svm.active -> GalleryPixivArtistResults(
                users = svm.pixivArtists,
                imageLoader = imageLoader,
                loading = svm.isSearchingArtists,
                error = svm.pixivArtistError,
                exhausted = svm.pixivArtistsExhausted,
                // 搜索「画师」分栏那一行：同样进画师页（与首页横排同一个动作）。
                onOpen = { user -> context.openPixivArtistPage(user.id, user.name) },
                onLoadMore = { svm.loadMorePixivArtists() },
                contentPadding = PaddingValues(
                    start = tokens.spacing.screenHorizontal,
                    end = tokens.spacing.screenHorizontal,
                    top = gridTopPadding + searchResultsInset,
                    bottom = LocalBottomBarClearance.current,
                ),
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
                    // 加上结果态内缩：这一段空白带在结果态是**摘要卡的落点**（见 [searchResultsInset]），
                    // 波浪环再居中一次才不会落在卡下面一半看不见。
                    .padding(top = gridTopPadding + searchResultsInset)
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
                        // 结果态内缩与网格同一口径（见 [searchResultsInset]）：这一屏的语义中心
                        // 落在摘要卡**下面**，而不是被那张卡顶下来一截。
                        top = gridTopPadding + searchResultsInset,
                        // 左右与其余空态同一口径。
                        start = tokens.spacing.screenHorizontal,
                        end = tokens.spacing.screenHorizontal,
                    )
                    .imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                VeneraEmptyView(
                    /*
                     * 标题分档而不是一刀切：**"这一站现在用不了"与"这一轮搜失败了"是不同的事**。
                     * 前者（目前只有 Gelbooru 未配账号）一次请求都没发出去，说成"没搜成"
                     * 会让用户去重试、去改网络 —— 而他该做的是去配置页填两个字段。
                     * 判据用 `searchError` 是否就是那句固定文案，而不是再加一个状态位：
                     * 那句话本身就是"发车前被拦住"的唯一来源，多一个旗标就多一处会不同步的状态。
                     *
                     * 2026-10-17 起这一整段搬进 `svm.emptyTitle()`：pixiv 那一域说的
                     * 是"这个词没有可摆的图"，与"这串标签没有"不是同一句话（它没有胶囊）。
                     */
                    title = svm.emptyTitle(),
                    message = svm.searchError
                        ?: (
                            svm.emptyDetailLines() +
                                listOfNotNull(
                                    if (searchWall.blockedRules.isNotEmpty()) {
                                        "命中屏蔽规则 ${searchWall.blockedRules.joinToString("、")}"
                                    } else {
                                        null
                                    },
                                )
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
                    // 方案 B：结果态多让出 [searchResultsInset] 一条空白带，
                    // 摘要卡落进带子里、首排图完整可见（不再是"浮层压在第一排图上"）。
                    top = gridTopPadding + searchResultsInset,
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
                // pixiv 那一域：卡片文字区走「头像 + 标题 + 作者名」那一档（用户 2026-10-17：
                // 「p 站搜索出来的图片卡片就不要带 p 站图标了，按照外面的头像 + 标题 + 作者名」）。
                // 传空表 = 其余域与从前逐字一致 —— 那张头像表就是判据（见 `GalleryCardCaption`），
                // 非空即换档，所以 booru 那三站一个字都不用改。
                stationTitles = if (domain == GalleryHomeDomain.PIXIV) {
                    svm.pixivTitles
                } else {
                    emptyMap()
                },
                authorAvatars = if (domain == GalleryHomeDomain.PIXIV) {
                    svm.pixivAvatars
                } else {
                    emptyMap()
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
                    // 头图在场时**不挂**折叠连接（见上面「头图在场时的顶栏契约」）：
                    // 空大标题仍占一行字体度量，behavior 会留一段"幽灵折叠行程"，
                    // 上滑先被它在 pre 段吃掉 —— 读起来是"滑了一段列表没反应"。
                    //
                    // ⚠️ 同样只对画廊首页成立（`heroActive`）：搜索态的大标题是「搜索」，空标题
                    // 那一档不会发生，所以连接照挂 ——
                    // 不挂会让顶栏整屏折不下去，`searchCollapsed` 那族判据永远为假，
                    // 搜索卡从此收不进顶栏。挂不挂连接只改首页契约，搜索态回到多数派那条。
                    .then(
                        if (heroActive) Modifier
                        else Modifier.nestedScroll(topBarBehavior.nestedScrollConnection)
                    )
                    .nestedScroll(chromeHideConnection),
            ) {
                GalleryForYouPage(
                    reading = homeWallReading,
                    imageLoader = imageLoader,
                    columnCount = columnCount,
                    state = forYouGridState,
                    // 下拉刷新：整页那四条腿一起重发，动画长在搜索条下面那一行（见 GalleryForYouPage）。
                    onRefresh = refreshWholePage,
                    refreshing = pullRefreshing,
                    // 站方标题只在 pixiv 那一域有意义（booru 三站的 API 根本没有标题这一格，
                    // 它们的标题是标签算出来的）。传空表 = 墙的行为与从前逐字一致。
                    stationTitles = if (domain == GalleryHomeDomain.PIXIV) {
                        pixivRecommendTitles
                    } else {
                        emptyMap()
                    },
                    // 作者头像**只给 pixiv 域**：booru 三站那侧响应里根本没有头像这一格，
                    // 传一张空表等于给墙画首字母座，而那三站的卡面形状不该被 pixiv 的
                    // 一轮改版带着走。非空表 ⇒ 卡片文字区走「头像 + 两行」那一档
                    // （第三十二轮，见 [GalleryCardCaption]；第二十四~三十一轮的
                    // 浮层毛玻璃已在第三十二轮退役）。
                    authorAvatars = if (domain == GalleryHomeDomain.PIXIV) {
                        pixivRecommendAvatars
                    } else {
                        emptyMap()
                    },
                    // 推荐那一节包一张**实色**卡面（2026-10-17 第二十四轮撤掉了模糊底图：
                    // 用户报「整个推荐面会有这种模糊效果」—— 86% 不透明挡不住底图透出的颜色，
                    // 那一大片读成"雾蒙蒙"，而上面两节是干净的实色白卡，三节必须一致）。
                    //
                    // 只有 pixiv 域有这一节（`galleryHomeSectionOrder` 里 booru 三站没有）。
                    sectionSurface = if (domain == GalleryHomeDomain.PIXIV) {
                        GallerySectionSurface(
                            sectionKey = GalleryHomeSectionKey.PIXIV_RECOMMEND,
                            inset = VeneraTokens.spacing.screenHorizontal,
                        )
                    } else {
                        null
                    },
                    // 头图在场时归零 ⇒ 头图从屏幕顶出血（见 [wallTopPadding]）。
                    topPadding = wallTopPadding,
                    backdrop = topBarBackdrop,
                    // 三节**必须带**：不带就会让画师/热门/节头整片消失 ——
                    // 屏上少一大块内容、视口锚点跟着漂，读起来像页面坏了。
                    sections = homeSections,
                    // 头图摆在 chrome **之上**、同一颗 header item 里（理由见 GalleryForYouPage 的
                    // hero 参数文档）。
                    //
                    // ── 标题归属（2026-10-16 改口径）──
                    // 从前这里传空串，理由是"这一屏的大标题「画廊」住在顶栏里，图上再压一遍
                    // 就是同一屏两处同名标题"。那是**头图还被顶栏压在下面**时的情形 ——
                    // 头图出血之后顶栏大标题必须让位（见下面 `VeneraTopAppBar.largeTitle`），
                    // 否则一条深色大字会直接横压在图的上段上，既挡图又读不清。
                    // 现在与设置页同一套分工：**标题是头图自己画的图上白字**。
                    hero = if (galleryHeroFile != null) {
                        @Composable {
                            SettingsHeroImage(
                                file = galleryHeroFile,
                                // 与顶栏那一枚同一个判据（2026-10-18）：搜索态这一屏叫「搜索」。
                                // 头图上的白字是**这一屏唯一的大标题**（顶栏那一格这时是空的），
                                // 所以它必须跟着换名，否则搜索态屏上会留着「画廊」两个字。
                                title = if (svm.active) "搜索" else "画廊",
                                // 2026-10-17 第十八轮（用户稿）：头图上那一行副标题。
                                // 稿上标题下面还有小字，一整片只有「画廊」两个字会读成"标题没加载完"。
                                //
                                // ⚠️ 同日晚些时候用户又改了：**「去掉那个副标题」**。
                                // 原因是那一行小字是她当时随口读到的文案，不是站方信息 ——
                                // 头图上摆一句编出来的话，等于给这一屏加了一条"读数"。
                                // 所以回到 `null`（= 只有标题），不另换一句话上去。
                                subtitle = null,
                                // 矮一档（2026-10-16，用户「空位有点太多…1选A吧」）：用设置**子页**
                                // 那一档 148dp，不造新数字。与上面 `searchOverlayTop` 是同一条式子，
                                // 两处必须一起改 —— 只改这里，搜索卡的落点会停在旧的 160dp 上。
                                totalHeight = statusBarTop + VeneraSpacing.settingsHeroHeightSubpage,
                                overlapBottom = VeneraSpacing.settingsHeroOverlap,
                                imageDescription = "画廊首页头图",
                            )
                        }
                    } else {
                        null
                    },
                    // 常驻 chrome（一条搜索入口条）= 墙的第一行内容：
                    // 随内容滚走（2026-09-30 第三版，用户口径「搜索栏保持在这里就行」）。
                    // 2026-10-15 起**搜索开着也照旧传**（从前这一格在 `svm.active` 时整块摘走）：
                    // 摘走会让内容硬跳一行，而卡片是另一层浮起来长开的 —— 两处不同步就是用户
                    // 读到的"没有过渡"。现在条留在原地，卡片从它身上原地盖过来、收回时缩回去。
                    chrome = {
                        GalleryHomeChrome(
                            // 内容域那一排第三轮起住在这一行（用户稿：「看下站源切换能不能做成这样」）。
                            // 必填、没有默认值：漏传就是把切换器整排摘掉，而"切不回图库站"是当天刚修过那一族。
                            domain = domain,
                            onPickDomain = { picked -> homeDomain = picked.name },
                            // 搜索一开，这一条就让位给从它身上长开的卡（两端同一个共享元素 key，
                            // 2026-10-16 第七轮）。传 `svm.active` 而不是 `active && !collapsed`：
                            // 结果态滚到 1/3 那一路的对面是**顶栏胶囊**那一对（本轮不做），
                            // 这里只认"搜索开没开"，免得条在屏幕外跟着翻档、给卡一个看不见的落点。
                            searchOpen = svm.active,
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
                        )
                    },
                    onOpen = { post ->
                        // 队列 = 屏上这一池。从前这里写死 `forYouWall`，切到 Pixiv 域就变成
                        // 「卡是 pixiv 的、左右滑过去全是 booru 的」—— 与本仓那族"复读页"同一形。
                        GalleryViewerQueue.set(homeWallReading.wall.cards.map { it.post })
                        onOpenPost(post.site, post.id)
                    },
                    onRetry = { retryForYouFromUser() },
                )
            }
        }

        // 顶栏**一直在**（用户 2026-09-25 第四轮：不跳页、也不替换标题栏）。
        // ⚠️ 常驻 chrome（一条搜索入口条）**不在顶栏里** —— 它现在是猜你喜欢墙的
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
            // 折叠后**居中**的小标题（用户 2026-10-18：「搜索页面的居中标题显示搜索才对」）。
            // 搜索态换名而不是继续叫「画廊」：那一屏做的事已经不是"看画廊"了。
            title = if (svm.active) "搜索" else "画廊",
            // 头图在场时大标题退场 —— 标题已由头图画成图上的白字（见上面的 hero 调用）。
            // 与设置页同一套分工（`SettingsHome.kt` 那处的 `if (heroFile != null) "" else 大标题`）。
            //
            // ⚠️ **只在画廊首页让位**（判据 `heroActive`，见它的定义）：搜索态那一屏头图仍在背景里
            // （主墙无条件组合），但顶栏左边该照常给标题 —— 这一格再空着就等于"搜索页左边
            // 永远没有标题"（用户 2026-10-18：「还是没有左边的大标题」）。
            // 传空串而不是 null：miuix 的 `largeTitle` 是 String，空串即不渲染；其余调用点零影响。
            //
            // ⚠️ 2026-10-18：搜索态的大标题同样叫「搜索」（用户：「搜索好像缺失了大标题」）。
            // 同一条理由：这一格空着时，展开态顶栏会留下**一整行空白**（miuix 的 `expansion` 仍然占位），
            // 读起来就是"搜索卡离顶部还有一大截"—— 填上「搜索」之后那一截是标题，不是空隙。
            largeTitle = if (heroActive) "" else if (svm.active) "搜索" else "画廊",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            // 左槽 = pixiv 账号那颗圆形头像（2026-10-17 用户点名：「在顶栏的左边做圆形头像，要和其他按钮对齐」）。
            // 对齐不是抄数字：外壳就是右侧那排用的同一颗 [VeneraTopBarPill]（触达 48 / 视觉 40，批次 H 那一档）。
            //
            // ⚠️ **只在画廊首页摆**（用户 2026-10-17 第二轮：「头像挡住了，头像只显示在画廊首页就行」）：
            // 进搜索态之后顶栏标题居中、搜索卡又顶上来，这颗圆头像正好落在两者之间被压住 ——
            // 一个被遮一半的入口比没有它更糟。判据用 `svm.active`（搜索层开没开）而不是"标题居没居中"：
            // 后者是布局的结果，拿它当条件会让这颗按钮跟着滚动高度闪。
            navigationIcon = {
                if (!svm.active) {
                    GalleryPorts.of(context).pixivAccount?.let { account ->
                        GalleryPixivAccountButton(account = account, imageLoader = imageLoader)
                    }
                }
            },
            // 头图在场时不挂 behavior 的连接（见上面「头图在场时的顶栏契约」），
            // 玻璃进度改由墙自己的滚动状态喂进来；null = 其余情形照旧走 contentOffset 原路。
            //
            // ⚠️ 同样是 `heroActive`（不是 heroPresent）：搜索态的大标题是「搜索」、不空，
            // 不挂连接的理由（空大标题的"幽灵折叠行程"）在那里不成立 —— 连接既然挂上了，
            // 玻璃进度就回到 contentOffset 原路；硬喂主墙的滚动进度会让它跟着背景那面墙跑，
            // 与 `searchCollapsed` 那一族判据互相矛盾。
            scrollProgressOverride = if (heroActive) heroScrollProgress else null,
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
                // 「收藏」hub（2026-10-17 第三十一轮，用户「在搜索和历史记录两个圆形按钮之间，
                // 新增一个书签按钮」）：这一枚就落在搜索与浏览历史**之间**，三颗都是
                // "去另一屏取一批东西"（搜结果 / 看历史 / 翻收藏）。
                //
                // ⚠️ 2026-10-18 改判（用户：「顶栏收藏和历史图标只需要出现在画廊首页就行，
                // 其他二级页面不要出现」）：这一枚与下面那枚都套上 `!svm.active` 那道门。
                // 上一版刻意不门控（"搜索态也要能翻收藏"），但真机上看是**顶栏被四颗图标塞满**，
                // 而搜索态的主角是那张卡 —— 四颗并列里有两颗与当前这件事无关。
                // 门控之后搜索态只剩「改条件 / 关掉搜索」两枚，与卡上的条件入口不重复。
                if (!svm.active) {
                    VeneraTopBarPill(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onOpenBookmarks()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkBorder,
                            contentDescription = "收藏",
                            tint = tokens.color.textPrimary,
                        )
                    }
                    // 「浏览历史」（2026-10-14 起占住这枚位置，取代原来的「换一批」）。
                    //
                    // 换一批没有丢：它与猜你喜欢节头那枚走的是同一条 `retryForYouFromUser()` 路径
                    // —— 下拉刷新 2026-09-30 撤下时那条注释就写明节头那枚「承担」了「我要新内容」
                    // 的出口，所以这一格换成历史入口之后，首页换一批仍在节头。
                    //
                    // 与上面那枚同一道门（见它的注）：搜索态里"最近搜过什么"由卡里的历史块承担，
                    // 顶栏再摆一枚是同一件事的第二个入口。
                    VeneraTopBarPill(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onOpenHistory()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "浏览历史",
                            tint = tokens.color.textPrimary,
                        )
                    }
                }
            },
            // ── 常驻 chrome（一条搜索入口条）**不在顶栏的 bottomContent 里了** ──
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

        // ── 常驻 chrome（一条搜索入口条）**不再是浮层**（2026-09-30 第三版）──
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
        //
        // ⚠️ 这两层都不许再被 `if (svm.active) { … }` 包着（2026-10-15 修「还是没有过渡」）：
        // `AnimatedVisibility` 被包在 `if` 里时，开搜索那一刻它是**新组合进来的**，
        // 内部状态的初值直接等于目标值 `true` —— 没有"false→true"那一跳，
        // `enter` 那套 fadeIn + scaleIn 一个字都不播（它只在 `searchCollapsed` 随滚动翻档时才播）。
        // 所以"没有过渡"从来不是缺动画定义，而是动画挂在一次**不会被观察到**的状态变化上。
        // 现在 `visible` 自己吃 `svm.active`：关闭态它仍在组合里（只是不渲染内容），
        // 一开就是一次真的 false→true ⇒ 动画照播。
        AnimatedVisibility(
            visible = svm.active && svm.mode == GallerySearchMode.INPUT && !searchCollapsed,
            enter = fadeIn(animationSpec = tween(durationMillis = tokens.motion.medium)),
            exit = fadeOut(animationSpec = tween(durationMillis = tokens.motion.short)),
        ) {
            // INPUT 态（正在打字/挑标签）：铺一层可点 scrim。
            // 点它 = 有胶囊就收成一条（接着看图）、没有就整个关掉 —— 与系统返回那两档同义。
            //
            // 只在 INPUT 态铺：RESULTS 态那张卡只有一行胶囊、下面的结果墙是**用户正在看的东西**，
            // 铺一层 scrim 会把那面墙的滚动与点击全吃掉（用户会以为页面卡死了）。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = SEARCH_SCRIM_ALPHA))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (svm.hasQuery) svm.collapseToResults() else svm.closeSearch()
                    },
            )
        }
        // ── 结果态那张卡：进出场取顶栏居中标题那一颗 spring ──
        // 收起时机与"居中标题出现"同帧（见 [SEARCH_CARD_COLLAPSE_AT]），曲线也必须同源
        // （`SEARCH_CARD_SHOW_SPEC` / `SEARCH_CARD_HIDE_SPEC` = miuix `TopAppBar.kt:688` 那两颗），
        // 否则两边各动各的、读起来是两件事而不是一个动作。
        //
        // ⚠️ 2026-10-16（第七轮）**这一层不再是这一下的主角**：展开/收起现在是
        // 「入口条 ↔ 这张卡」的真容器变化（同一个共享元素 key，见 `GallerySearchContainer.kt`），
        // 这里退成"配不上对时"的兜底 —— 详见下面 enter/exit 上的注释。
        AnimatedVisibility(
            visible = svm.active && !searchCollapsed,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // 纵向落点 = [searchOverlayFloor] 之上再收掉 [searchOverlayFollow] 那一截
                // （见那两处的文档：收起的是"顶栏已折叠、大标题不占高"的那 52dp 空白）。
                // 放布局 lambda 里读 `collapsedFraction`：惰性读取只重跑布局，不逐帧重组这一屏。
                .offset {
                    IntOffset(
                        x = 0,
                        y = (searchOverlayTop.toPx() -
                            searchOverlayFollow.toPx() * topBarBehavior.state.collapsedFraction)
                            .roundToInt(),
                    )
                },
            // 进出场只留 fade（2026-10-16 第七轮）：原先那对
            // `scaleIn / scaleOut(initialScale = 0.94f, transformOrigin = 顶边中点)` 是
            // "容器变化"的**近似物**，真容器变化落地后退休 —— 它俩叠在共享元素的 bounds
            // 插值上就是双重形变（卡会一边被拉伸一边被缩放）。
            //
            // 这一层与 fade 都留着，是因为它还要给**配不上对**的两路兜底：
            // 结果态滚到 1/3 收起、以及从收起态 `backToInput` 展开 —— 那两下入口条恒不可见
            // （`svm.active` 仍为真），共享元素无从匹配，只能由这里淡出淡入。
            enter = fadeIn(animationSpec = SEARCH_CARD_SHOW_SPEC),
            exit = fadeOut(animationSpec = SEARCH_CARD_HIDE_SPEC),
        ) {
            Box(
                // 与常驻入口条**同一个 key**：这两层是"同一块面"的两态。外面包一层 Box
                // 而不是把修饰符直接挂给 `GallerySearchArea` —— 它的根是那个带 padding 的
                // Column，共享元素的 bounds 要的是**外框**（与入口条的外框同构），挂进去就变成
                // "面 vs 面 + 边距"，飞行途中会横跳一下。
                modifier = Modifier.gallerySearchContainerElement(this@AnimatedVisibility),
            ) {
                GallerySearchArea(
                    svm = svm,
                    rvm = rvm,
                    allowNsfw = allowNsfw,
                    // 结果态读数槽要报的张数：**与页尾 `GallerySearchEnd` 同一个来源、同一个数**
                    // （都是 `searchWall.cards.size`，"真正摆出来的那些"）——
                    // 两处各算一遍一定会分叉，而屏上同时摆着两个不同的数，两个都不可信。
                    // 第一轮在途时传 null（那一格改画波浪环），不报 0。
                    // 判据取 `page > 0`（有一轮到过货）而不是 `!isSearching` ——
                    // "已经开了搜索但一次都还没落地"那一档若报 `0 张`，就是把"还没搜"说成"没有结果"。
                    //
                    // 画师那一栏吃的是它自己那份读数（`pixivArtistPage` / `pixivArtists`）：
                    // 两栏的"到货了没"是两条腿，拿作品那面墙的 `page` 去推它，
                    // 切到画师时就会先报一次"0 位"再跳成真实人数。
                    resultCount = if (pixivArtistMode) {
                        svm.pixivArtists.size.takeIf { svm.pixivArtistPage > 0 }
                    } else {
                        searchWall.cards.size.takeIf { svm.page > 0 }
                    },
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
            // 与那一颗 spring **同一份 spec**：胶囊上来的过程正是卡片缩回去的过程，
            // 一上一下共一条曲线，读起来是"这张卡收进顶栏、留下一行胶囊"。
            enter = fadeIn(animationSpec = SEARCH_CARD_SHOW_SPEC) +
                slideInVertically(
                    animationSpec = SEARCH_CARD_SHOW_SLIDE_SPEC,
                ) { height -> height / 2 },
            exit = fadeOut(animationSpec = SEARCH_CARD_HIDE_SPEC),
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
/**
 * 头图在场时，顶栏玻璃"淡满"所需的滚动行程（48dp）。
 *
 * 与设置页那一档**同源**（`SettingsHome` 里 `heroGlassRangePx` 用的就是 48dp，2026-10-02
 * 第五轮定的口径）：两处都是"滚过 48dp 玻璃就满、小标题接管"。**别只改单边** ——
 * 两个 hero 页若用不同行程，同一个手势在两屏读起来的快慢会不一样。
 */
private val HERO_TOP_BAR_FADE_RANGE = 48.dp

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
 * 结果态那张搜索卡「收进顶栏」的阈值 = **顶栏居中标题的出现点** = 1/3。
 *
 * 不是拍出来的：miuix 顶栏里大标题 `alpha = 1 − (collapsedFraction × 3f).coerceIn(0f, 1f)`、
 * 居中那行小标题 `visible = collapsedFraction × 3f >= 1f`，两条都落在 1/3
 * （`.reference/miuix-ui-094/…/basic/TopAppBar.kt:647-682`）。取同一个点，
 * "居中标题出来"与"搜索卡收起"才是同一件事的两面，而不是先后两下。
 */
private const val SEARCH_CARD_COLLAPSE_AT = 1f / 3f

/**
 * 结果态那张搜索卡「回到屏上」的阈值 ≈ 回到顶部。
 *
 * 与 [SEARCH_CARD_COLLAPSE_AT] 一起构成**滞回**：滚回来要几乎到顶才放它出来
 * （用户 2026-10-16 口径「向下拉到最顶的位置才重新显示」）。
 * 取 0.02 而不是 0：折叠进度是浮点除法，回顶那一下不一定精确落在 0。
 */
private const val SEARCH_CARD_RESTORE_AT = 0.02f

/**
 * 结果态搜索卡（与它收起后顶栏那行胶囊）的进出场曲线 —— **miuix 顶栏居中标题那一颗 spring**。
 *
 * `folmeSpring(damping = 1.0f, response = 0.3f / 0.15f)` 与
 * `.reference/miuix-ui-094/…/basic/TopAppBar.kt:688` 那两颗逐字同源（显示 0.3、隐藏 0.15）。
 * 抄同一颗曲线是刻意的：居中标题淡入与搜索卡收起在**同一帧**触发，若两边曲线不同，
 * 用户读到的是"两个东西各动各的"；同源之后它是一个动作。
 *
 * ⚠️ 2026-10-16（第七轮）起**那两个数字搬到 `GallerySearchContainer.kt`**：那一对共享元素
 * （常驻入口条 ↔ 展开的搜索卡）的 bounds 转场与这两颗必须**同一时长** —— 时长对不上时，
 * 内容会在 bounds 动画跑完之前被 `AnimatedVisibility` 移除、尾巴被硬切。数字只留那一处，这里是别名。
 *
 * `damping = 1.0` 是临界阻尼（不过冲）—— alpha 与 scale 都不过冲，不会出现"闪一下"；
 * 弹性来自曲线形状本身（速度先快后缓、带 settle 尾巴），而不是靠回弹。
 */
private val SEARCH_CARD_SHOW_SPEC = SEARCH_CONTAINER_SHOW_SPEC
private val SEARCH_CARD_HIDE_SPEC = SEARCH_CONTAINER_HIDE_SPEC

/**
 * 同一条曲线、给位移那一路用的一份实例。
 *
 * `slideInVertically` 的参数类型是 `FiniteAnimationSpec<IntOffset>`（不是 Float），
 * 所以按类型参数各建一份 —— 参数值逐字相同，两路仍是同一条弹簧。
 */
private val SEARCH_CARD_SHOW_SLIDE_SPEC = SEARCH_CONTAINER_SHOW_SLIDE_SPEC

/**
 * 续页判定所需的一切，收成一个值。
 *
 * 收起来是为了让 `snapshotFlow { … }.distinctUntilChanged()` 能一次比干净：
 * 只要其中任一项变了就重新判一次，都没变就不做任何事（滚动里 `layoutInfo` 每帧都在动，
 * 不过这一层就会出现"每帧判一次"的无效工作）。
 *
 * ⚠️ 2026-10-17 第三十一轮起 `internal`：pixiv 云端书签二级页（`GalleryPixivBookmarksScreen`）
 * 与 pixiv 推荐墙是同一套三道闸门 —— 两颗件各写一版就会漂（书签页那份要是少了"在取时不并发"，
 * 用户甩到尾就会连发三四跳）。判据钉在 `GalleryBooruBookmarksTest` 里。
 */
internal data class GalleryLoadMoreState(
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
 *
 * 同上：`internal` 是给书签二级页共用（第三十一轮）。
 */
internal const val LOAD_MORE_AHEAD = 4

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
            ?: if (blockAi && GalleryAi.isAiMarked(post.siteAiFlag, post.tagList)) AI_BLOCKED_RULE else null
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
 * 「这一面墙上有一段内容坐在一张卡面上」（2026-10-17 第二十二轮）。
 *
 * 用户拍板原话：「推荐区包卡，但是还是目前这样的瀑布流，然后卡面会有图片的很低的模糊」。
 *
 * ## 为什么卡面只能画在网格**外面**
 *
 * 推荐那一节是「节头 + 脚下整面瀑布流」，而 `LazyVerticalStaggeredGrid` 里**没有"行"**
 * —— 卡片逐列往下排、高度各不相同。所以"给这一段包一张卡"不能逐 item 画：
 * 相邻两列的卡片结束位置不同，逐 item 画出来的卡面会在先结束那一列旁边露出**阶梯缺口**。
 * 唯一成立的位置是网格**下面**的一整层（[SectionSurfaceLayer]）。
 *
 * ## 与「节背板」那条被否的方案不是一回事
 *
 * `GalleryHomeSection` 的头注记着：M7 给每节套一块 `VeneraCard` 背板被撤了，因为
 * "节卡 + 条目卡 = 卡中卡，两层颜色只差一档，实测条目卡面比页面底只亮 +7，糊成一层"。
 * 这里能成立是因为**卡面上有图**：一层 `blur` 过的图给出纹理，卡面与卡片的分界不再只靠那 +7 的色差。
 * 纯色档（`imageUrl = null`）只用于"这一池没好图"的兜底，那种处境下它确实会偏弱。
 *
 * @param sectionKey 从**这一节的节头顶**开始铺卡面（含节头本身），一路延伸到视口底。
 * @param inset 卡面左右内缩（递页面的水平内边距）—— 卡面不贴屏幕边。
 * @param imageUrl 卡面上的模糊图。⚠️ null 不是"忘了传"，见 [SectionSurfaceLayer] 那条。
 */
internal data class GallerySectionSurface(
    val sectionKey: String,
    val inset: Dp,
)

/**
 * [GallerySectionSurface] 的那层卡面 —— 画在网格**下面**。
 *
 * ## 定位（全部在 **draw 阶段**读，零重组零重布局）
 *
 * 卡面顶端 = 节头 item 的 `offset.y`：
 * - 节头还在可见集合里 ⇒ 直接取它的 y（跟随滚动）；
 * - 节头已滚到视口上方（可见首项的 index 比它大）⇒ 顶端取 0，卡面铺满整个视口；
 * - 节头还没进入视口 ⇒ 这一屏不该有卡面，什么都不画。
 *
 * 底端恒为视口底：推荐段后面只有页尾，卡面"延伸出屏"正是这类设计的常态。
 * 圆角只有上两角 —— 下沿在屏外，画了也看不见。
 *
 * ## 卡面是**纯色**，不再挂模糊底图（2026-10-17 第二十四轮，推翻本日第二十二轮的做法）
 *
 * 那一轮给这层铺过「首图模糊 32dp + 压 86% 卡面色」，理由是"纯色卡中卡只差 +7 色差会糊，
 * 用模糊图给纹理拉开分界"。真机反馈是**反的**：用户报「整个推荐面会有这种模糊效果」——
 * 86% 的不透明挡不住底图的颜色透出来，那一大片就读成"这一区雾蒙蒙的"，
 * 而上面两节（画师 / 排行榜）是干净的实色白卡，三节放一起反而是推荐这一节脏。
 *
 * 所以撤掉底图、只铺 `surface` 实色：三节用**同一种**包裹，卡面只负责"这段属于这一节"。
 * ⚠️ 这条同时把 M7 那个顾虑又请回来了（卡中卡只差一档色差）——
 * 但那是取舍，不是缺陷：用户要的是一致，不是一个节另有一套材质。
 */
@Composable
private fun SectionSurfaceLayer(
    spec: GallerySectionSurface,
    state: LazyStaggeredGridState,
    sectionIndex: Int,
) {
    val tokens = VeneraTokens
    val density = LocalDensity.current
    val insetPx = with(density) { spec.inset.toPx() }
    val cornerPx = with(density) { tokens.shape.large.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val top = sectionSurfaceTopPx(state, sectionIndex) ?: return@drawWithContent
                // 节头在屏下方（top ≥ 视口高）时矩形是退化/反向的，`RoundRect` 会炸；
                // 极窄窗口下左右内缩吃光整宽也一样 ⇒ 两种都直接不画。
                if (top >= size.height || size.width <= insetPx * 2f) return@drawWithContent
                val surfacePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(insetPx, top, size.width - insetPx, size.height),
                            topLeft = CornerRadius(cornerPx),
                            topRight = CornerRadius(cornerPx),
                            bottomRight = CornerRadius.Zero,
                            bottomLeft = CornerRadius.Zero,
                        ),
                    )
                }
                clipPath(surfacePath) { this@drawWithContent.drawContent() }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.color.surface),
        )
    }
}

/**
 * 卡面顶端在**视口坐标**里的 y；null = 这一屏不该画卡面。
 *
 * ⚠️ 只许在 draw 阶段调用（读 `state.layoutInfo`）—— 在组合期读它就是每帧重组。
 */
private fun sectionSurfaceTopPx(
    state: LazyStaggeredGridState,
    sectionIndex: Int,
): Float? {
    val items = state.layoutInfo.visibleItemsInfo
    items.firstOrNull { it.index == sectionIndex }?.let { return it.offset.y.toFloat() }
    // 节头不在可见集合里只剩两种处境：已滚到视口上方（卡面铺满），或还没滚进来（不画）。
    val firstVisible = items.firstOrNull()?.index ?: return null
    return if (firstVisible > sectionIndex) 0f else null
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
    /**
     * 是否禁掉这面墙的**过度滚动**（2026-10-16，用户「禁止在设置了头图的页面使用过度滚动」）。
     *
     * 有头图的那面墙（首页主墙）传 true：到顶继续下拉，`stretch` 会整张带头图顶下去、
     * 上方露出一大片页面背景 —— 观感很糟。没有头图的墙保持默认，回弹是正常的触感反馈。
     *
     * ⚠️ 走 `overscrollEffect = null` 这条**组件参数**，不是包一层 CompositionLocal：
     * 这一面墙就是那颗滚动容器本身，参数是最短的路径，也不必让"禁没禁"这件事
     * 依赖读者去别处找一个 provider。
     */
    disableOverscroll: Boolean = false,
    /**
     * 站方自己给的标题（`uid → 标题`），**盖过**下面那批由标签算出来的标题。
     *
     * 出处是 2026-10-17 第七轮用户那句「推荐下面的图标题没了，直接用 p 站自己的标题就行」：
     * pixiv 每行都带 `title`，而 booru 三站没有 —— 后者的标题是 `GalleryCardTitle`
     * 从标签里**算**的，前者被算成空（那一池的标签是日文，词典里没有）。
     *
     * ⚠️ 只在"这一格真的有字"时覆盖：`PixivWork.title` 可能为空串（站方给了但空），
     * 那时那一行整行不画（同 [GalleryCardCaption] 的 `title == null` 那档），
     * **不拿标签算出来的那批顶上去** —— 那会让"站方标题"与"我们猜的标题"在屏上混成同一层。
     */
    stationTitles: Map<String, String> = emptyMap(),
    /**
     * 作者头像（`uid → 地址`），与 [stationTitles] 同一条透传路径 —— 同理也是
     * "整批算好再摊平"，而不是卡内各查一次（那要开网络/查库）。
     * 空 map = 这一面墙不画头像（其余五处调用点逐像素不变）；非空 ⇒ 卡片文字区走
     * 「头像 + 标题/画师名」两行（2026-10-17 第三十二轮，见 [GalleryCardCaption]）。
     */
    authorAvatars: Map<String, String> = emptyMap(),
    /**
     * 非空 = 这一面墙上**有一段内容坐在一张卡面上**（[GallerySectionSurface]，眼下只有推荐那一节）。
     *
     * null = 与从前逐字一致（其余四处调用点都不传）。**不做成布尔开关**：
     * "哪一段"与"卡面长什么样"是同一件事的两半，拆成两个参数就会对不上。
     */
    sectionSurface: GallerySectionSurface? = null,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    // 卡面那一节的 item index：`header` 占一格，之后按 [sections] 的顺序排。
    // 找不到那个 key（调用方传了本节没有的 key）⇒ null ⇒ 不画 —— 静默退化成"没包卡"，
    // 而不是把卡面画到错的节上。
    val surfaceSectionIndex = remember(sectionSurface, sections, header != null) {
        sectionSurface?.let { spec ->
            val order = sections.indexOfFirst { it.key == spec.sectionKey }
            if (order < 0) null else order + (if (header != null) 1 else 0)
        }
    }
    val context = LocalContext.current
    // 平台默认的过度滚动效果。⚠️ 必须**无条件**先 remember 出来再按开关取舍：
    // 把 `rememberOverscrollEffect()` 写进 `if` 的分支里就是条件调用 Composable，会崩。
    val platformOverscrollEffect = rememberOverscrollEffect()
    /*
     * 长按一张卡 → 那张动作表（用户 2026-10-04 第 2 条点名的五项）。
     *
     * 挂在**这一颗共用件**上而不是四个调用点各接一遍：搜索墙 / 猜你喜欢主墙 / 日榜二级页 /
     * 收藏页走的是同一个 `GalleryCardsGrid`，只在一面墙上有长按就是假开关。
     * 收藏页把 `onLongPress` 拿去做了多选与区间反选 —— **调用方传的优先**，
     * 判据仍用"传没传这个函数"而不是再加一个布尔开关（与本函数 `selection` 那条同规矩：
     * 两个参数表达同一件事就有对不上的可能，一个参数不会）。
     */
    var actionCard by remember { mutableStateOf<GalleryCard?>(null) }
    // 这一面墙所有卡片的标题**整批算一次**（要开 SQLite 查离线词典，逐卡各查一次就是几十趟）。
    // 墙是唯一知道"这批卡是哪些"的地方，所以取数落在这里；下面按 uid 交给每张卡。
    //
    // 站方那一批（见 [stationTitles]）**在后覆盖**：一张卡只有一个标题格，站方给了真题就摆真题。
    // 覆盖值是空串也算"给了" —— 那把 `dict + station` 里该 uid 已经被空串盖掉，
    // 传到卡上之前统一折成 null（`takeIf { isNotBlank() }`）⇒ 那一行整行不画，
    // 而**不是**又摆回我们算的那个（两种来源混成一层是这里最要防的错配）。
    val dictTitles = rememberGalleryCardTitles(cards.map { it.post })
    val titles = remember(dictTitles, stationTitles) {
        if (stationTitles.isEmpty()) {
            dictTitles
        } else {
            // 覆盖值是空串也算"站方给了"：那颗 uid 从 dict 那批里摘掉、又不进结果
            // ⇒ 卡上取到 null ⇒ 那一行整行不画（同 `GalleryCardCaption` 的 null 档）。
            val merged = HashMap<String, String>(dictTitles.size + stationTitles.size)
            dictTitles.forEach { (uid, title) -> if (uid !in stationTitles) merged[uid] = title }
            stationTitles.forEach { (uid, title) -> if (title.isNotBlank()) merged[uid] = title }
            merged
        }
    }
    // 滚动越界预取（2026-09-30「预览能不能更快」那轮）：见 [rememberGalleryPreviewPrefetch]。
    rememberGalleryPreviewPrefetch(
        cards = cards,
        imageLoader = imageLoader,
        state = state,
        leadingItemCount = (if (header != null) 1 else 0) + sections.size,
    )
    // 卡面层与网格包在一个 Box 里：卡面在**下**、网格在**上**，卡面因此不会盖住任何一张卡。
    //
    // `modifier`（含调用方的 `blurBackdropSource`）挂在 Box 上 —— 它描述的是"这一整块区域"，
    // 挂在 Box 与挂在网格上等价；网格自己要 `fillMaxSize()` 铺满这个 Box。
    Box(modifier = modifier.fillMaxSize()) {
        if (sectionSurface != null && surfaceSectionIndex != null) {
            SectionSurfaceLayer(
                spec = sectionSurface,
                state = state,
                sectionIndex = surfaceSectionIndex,
            )
        }
        LazyVerticalStaggeredGrid(
            state = state,
            columns = StaggeredGridCells.Fixed(columnCount),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space5),
            verticalItemSpacing = tokens.spacing.gridGap,
            // 头图那面墙禁掉过度滚动（见 [disableOverscroll]）；其余墙保持平台默认。
            overscrollEffect = if (disableOverscroll) null else platformOverscrollEffect,
            modifier = Modifier.fillMaxSize(),
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
                    authorAvatar = authorAvatars[card.post.uid],
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
                    } ?: {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        actionCard = card
                    },
                    selected = selection?.invoke(card.post) == true,
                    selecting = selection != null,
                    onToggleSelect = onToggleSelect?.let { handler -> { handler(card.post) } },
                    overlay = overlay,
                    sharedElementKey = sharedElementKey?.invoke(card.post),
                    // 点署名头像 = 进画师页（用户 2026-10-19）。**只对 pixiv 生效**：
                    // 图库三站没有「用户号」这个身份（它们的画师是标签名），
                    // 硬套会让点脸去搜一个同名标签 —— 那是另一件事。
                    onOpenArtist = if (card.post.site == GallerySite.PIXIV) {
                        val id = GalleryArtistLinks.pixivUserId(card.post.source)
                        if (id != null) {
                            { context.openPixivArtistPage(id, card.post.author) }
                        } else {
                            null
                        }
                    } else {
                        null
                    },
                )
            }
            footer?.let { slot ->
                item(key = "grid-footer", span = StaggeredGridItemSpan.FullLine) { slot() }
            }
        }
    }

    actionCard?.let { card ->
        GalleryCardActionSheet(
            post = card.post,
            onDismiss = { actionCard = null },
            onOpenDetail = { onOpen(card.post) },
            // 画师介绍页是另一颗 Activity（`artistProfileIntent` 那颗入口），
            // source 传这张图的**画师出处**（站方那串，可能为空 —— 介绍页对它只当"没出处"）。
            onOpenArtist = { name ->
                context.startActivity(
                    artistProfileIntent(context, card.post.site, name, card.post.source),
                )
            },
        )
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

/**
 * 排行榜某一档的**一次**结果（含"为什么空"），用于切域回来直接摆。
 *
 * 为什么连 [caption] 一起缓存：失败也是一种"这一轮已经问过了"。只缓存成功那一档的话，
 * 站方一直不给内容时，用户每次切回 pixiv 都要再等一轮超时 —— 那正好是用户点名的
 * 「不要每次切页面都刷新」。缓存住之后，屏上那句话就是上一轮站方给的原话（本来就是真读数）。
 */
internal class PixivRankingSnapshot(
    val works: List<PixivWork>,
    val caption: String?,
)

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

/**
 * 卡片比例取证那一条日志的标签（用法与为什么只加读数见 [GalleryPostCard] 内那一处）。
 * 取走：`adb logcat -d -s GalleryCardRatio`。
 */
internal const val CARD_RATIO_TAG = "GalleryCardRatio"

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
    /**
     * 作者头像地址（`uid → 地址`，与 `titles` 同一条透传路径）。
     * `null`（map 里没这一项）= 文字区走「站标 + 标题」那一行；
     * 非 null = 「头像 + 标题/画师名」两行（2026-10-17 第三十二轮，见 [GalleryCardCaption]）。
     */
    authorAvatar: String? = null,
    /**
     * 点**署名行那颗头像** = 进这位作者的画师页（用户 2026-10-19：「改成点击画师头像进入画师页」）。
     *
     * `null` ⇒ 头像不可点（图库三站的墙不传头像表，天然走不到这一档）。
     * 与整卡点击**分开**：整卡是"看这张图"，头像那一下是"看这个人"。
     */
    onOpenArtist: (() -> Unit)? = null,
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
    // 键统一 viewerKey（批 A）：墙上的卡没有页序，它就等于 uid —— 行为不变，
    // 但登记侧与查询侧（`cardBoundsOf(post.viewerKey)`）从此念同一把键。
    DisposableEffect(post.viewerKey) {
        onDispose { GalleryFlyIn.forgetCardBounds(post.viewerKey) }
    }
    // 高度在**摆放前**就知道：两站的 JSON 都直接带中间档宽高，
    // 不像本地插图那样要靠加载成功回填 —— 所以这里不会有"各列先等分再集体跳动"。
    //
    // ⚠️ 夹取区间**不再就地写**（2026-10-20）：它搬进 `cardBoxRatio`（唯一出处），
    // 因为"框比例"与"作品页那一页图块的比例"必须是**同一把尺** —— 两处各夹一次、
    // 或者一处夹一处不夹，飞行体两端比例就不同，几何必须变形成另一个比例（读起来"乱变"）。
    // pixiv 那一档按主人的口径放宽到 0.2~4.0（竖向跨页 0.27 / 横向大图 3.0 都不裁），
    // 图库三站保持原样（`LEGACY_*`），不动它们一根手指。
    // 钉死比例那一档**不再夹取**：它是调用方给的设计值（现成 token），不是站方数据。
    val ratio = fixedRatio?.takeIf { it > 0f }
        ?: cardBoxRatio(post, tokens.spacing.coverAspectRatio)
    // ── 取证（2026-10-19，用户报「画师页这样画面显示不全」，附 pixez 同图对照）──
    //
    // 夹取会把**框**与**原图**掰开，而封面是 `Crop` ⇒ 一旦不相等，屏上就是"图被裁掉一条"。
    // 2026-10-20 主人点名「限制 pixiv 站点图片卡片的显示比例，要整张卡全部显示出来」⇒
    // pixiv 区间放宽到 0.2~4.0（`cardBoxRatio`），夹取到 0 的图**不再裁**。
    // 这一格留读数：真机上若还看见被裁，`logcat -s GalleryCardRatio` 会念出是哪一张、差多少。
    val clamped = cardBoxRatioClamped(post, tokens.spacing.coverAspectRatio)
    LaunchedEffect(post.uid, clamped, ratio) {
        if (clamped) {
            Log.d(
                CARD_RATIO_TAG,
                "${post.site}#${post.id} 原图比例=${"%.3f".format(post.cardRatio)} " +
                    "框比例=${"%.3f".format(ratio)} 被夹到区间外 → Crop 会裁掉一条",
            )
        }
    }
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
        /*
         * 按下去整卡下沉（0.94）。**这张卡此前是全画廊手感最"死"的一块** —— miuix `Card`
         * 的 `pressFeedbackType` 默认 None，而这一面墙上十几张卡全靠"点得动"这一件事，
         * 按下没有任何回话就等于让人怀疑点空了。
         *
         * 缩的是**卡面**，不是外面那圈 [SelectableCardFrame]：多选态那层洗底与右上角那枚选择框
         * 是"选择"这套 chrome，它们不跟着缩才是对的（要缩一起缩的话，那枚选择框会在按下的
         * 一百多毫秒里从卡角上滑开，读起来像两块东西没对齐）。
         */
        pressFeedbackType = VeneraPressFeedback.card,
        // 多选态下点整张卡 = 勾选（只让人去够右上角那个小圈太费劲）；非多选态才是点开大图页。
        onClick = {
            if (selecting) {
                onToggleSelect?.invoke()
            } else {
                onOpen(coverBounds)
            }
        },
        onLongPress = onLongPress,
    ) {
        // 封面地址取一次：存成局部量，免得两次调用之间设置项变化
        // （那会让封面与文字区里的头像不同源）。
        val wallUrl = GallerySettingsModel.wallUrl(post, quality)
        @Composable
        fun CoverNode() {
            VeneraCover(
                url = wallUrl,
                contentDescription = "${post.site.displayName} #${post.id}",
                // 宽高由外部 aspectRatio 定死，封面再自持 3:4 会在槽位里横向留缝（轮播那条同因）。
                preserveAspectRatio = false,
                mask = if (card.masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                imageLoader = imageLoader,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .onGloballyPositioned {
                        // 截像素那一路**必须**吃裁过的那一把（PixelCopy 只能读窗口内的表面）：显式写出来。
                        val rect = it.boundsInWindow(clipBounds = true)
                        // 批 B §3 地基：定位那一把要**没裁过**的（`clipBounds = false`）。
                        // pixiv 竖长条页常年有几屏高住在窗口外，裁过的那一把只配截像素、不配当位置。
                        val full = it.boundsInWindow(clipBounds = false)
                        // 只在矩形**真的变了**才写。这不是省几行开销的打磨：`boundsInWindow()`
                        // 每次返回一个新的 `Rect` 实例，而 `mutableStateOf` 默认按引用比较 ——
                        // 无条件赋值等于"滚动每一帧都把这张卡重组一遍"（Lazy 项在窗口坐标系里的
                        // 位置每帧都在动，这个回调就每帧都来一次）。用户报的「滚动中图片反复重建」
                        // 最直接的自激环就在这里。
                        // 读 `coverBounds` 再比是安全的：`Rect` 的 equals 是结构相等。
                        if (rect != coverBounds) coverBounds = rect
                        // 顺手给**返回程**留一个落点（大图页离场时按 uid 来查）。
                        // ⚠️ 打码卡**不回写** —— 红线是"打码那张两边都不飞"，
                        // 而"表里压根没有它"是这条红线最不容易被绕过的一种落法。
                        if (!card.masked) GalleryFlyIn.noteCardBounds(post.viewerKey, full, rect)
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
                // AI 角标（批次 C2 → 2026-10-18 接硬字段）：判据 = **站方硬位优先、标签词表兜底**。
                // 摆不摆由画廊自己那枚开关管，与漫画守卫页的 block_ai 是两把；
                // ⚠️ 词表也**不是同一份**（漫画侧收裸 `ai`、画廊侧剔掉它）—— 设置页那句文案已跟着改真话。
                // 屏蔽开着时这条根本不会上屏，所以角标的真正用途是"我不屏蔽，但我要看得出来"。
                // 偏好读法与上面「预览清晰度」同一条口径：按卡片各读一次，不往下透传四个调用方。
                val aiBadgeOn by GalleryPorts.of(LocalContext.current).prefs
                    .galleryAiBadge.collectAsState()
                if (aiBadgeOn && GalleryAi.isAiMarked(post.siteAiFlag, post.tagList)) GalleryAiPill()
                // 多页角标（2026-10-18）：站方报了页数且大于一才摆，判据在 `GalleryPost.pagesBadge`。
                post.pagesBadge?.let { GalleryPagesPill(it) }
                overlay?.invoke(this, post)
            }
        }
        @Composable
        fun CaptionNode(mod: Modifier) {
            GalleryCardCaption(
                site = post.site,
                // 没有作品/角色标签时给 null ⇒ 那一行只画来源标识，不画空文字。
                title = titles[post.uid],
                authorAvatar = authorAvatar,
                authorName = post.author.takeIf { it.isNotBlank() },
                imageLoader = imageLoader,
                onOpenArtist = onOpenArtist,
                modifier = mod,
            )
        }
        // 封面在上、文字区在下（2026-10-17 第三十二轮回改）：第二十四~三十一轮 pixiv
        // 推荐墙的文字区曾浮在封面下沿（半透明毛玻璃），用户第三十二轮「改成图片能在外面
        // 预览全部显示那种不要浮层，这样飞行动画也会好很多」把它撤了 —— 浮层压掉封面底沿
        // 一条，图在网格里看不全；且飞行起点（PixelCopy 截的封面那一块）带着那条 UI，
        // 要额外做「两段画 + 随进度淡出」才接得上画面框。现在六处墙同一形状：`Column`，
        // 封面 + 下方文字区（递了头像表的墙多一颗头像两行，见 [GalleryCardCaption]）。
        // 卡比浮层那一版高一个文字区，瀑布流会重排一回。
        Column(modifier = Modifier.fillMaxWidth()) {
            CoverNode()
            CaptionNode(
                Modifier
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

/** 被画廊那把 AI 开关收起时，页尾念出来的"规则名"。它不是一条文本规则，是一枚开关。 */
const val AI_BLOCKED_RULE = "AI 生成"

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
    /**
     * 「这一轮哪几站没给内容、各是什么原因」，**按站一份**。
     *
     * 与 [sourceNotice] 并存而不是替掉它：那个串仍供下面**空态**那整段说明用
     * （它要把缺席与"这一轮没货"念在同一段里），而首行那块提示吃结构
     * （要按档分色、还要给未配账号那一档挂真入口，理由见 `GalleryViewModel.failures`）。
     */
    failures: Map<GallerySite, String>,
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
                /*
                 * 行动层只在"站方这一轮没回内容"那一档给：另两档（命中屏蔽规则 / 被分级收起）的
                 * 出路是去收那条规则或改「成人内容处理」，重试还是同一个结果 ——
                 * 按下去屏上纹丝不动的那枚按钮是本仓最忌的假开关（那两档的正解消息里已经说清了去哪儿改）。
                 */
                actionText = if (blocked == 0 && hidden == 0) "重试" else null,
                onAction = if (blocked == 0 && hidden == 0) onRetry else null,
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
                //
                // 2026-10-12 改造：从"一行灰字"换成按档分块的提示组件。
                // 未配账号那一档带一枚「去配置账号」（真能到 设置 → 画廊），
                // 出错那一档带「重试」—— 重试走 `onRetry`（= 顶栏那枚「换一批」同一条路径：
                // 先清熔断再真取），直接调 refresh 撞上 60s 熔断窗口时是一枚假按钮。
                //
                // 2026-10-14 改判：这一面墙上的块改成**一行档**（`compact = true`）。
                // 手机档截图量到：两块各 3~4 行正文，把「正在关注的画师 / 每日热门」
                // 整块推出首屏 —— 核心模块的第一眼成了故障说明书。
                // 正文一个字没删，改成点那一行就地展开；判据见 `UiAuditBatch2Test`。
                GalleryNotices(
                    notices = remember(failures) { galleryNotices(failures) },
                    compact = true,
                    onRetry = onRetry,
                    modifier = Modifier.padding(bottom = tokens.spacing.space3),
                )
            },
            footer = if (hasPosts) {
                @Composable { GalleryFeedEnd(wall, date) }
            } else {
                null
            },
        )
    }
}
