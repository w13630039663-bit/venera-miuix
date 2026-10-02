package com.venera.compose.feature

import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.NavigationEvent
import androidx.navigation.toRoute
import com.venera.compose.SettingsActivity
import com.venera.compose.components.CoverTransitionScopes
import com.venera.compose.components.LocalCoverTransitionScopes
import com.venera.compose.components.VeneraAmbientBackground
import android.app.Activity
import com.venera.compose.components.VeneraFloatingNavBar
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.components.VeneraSideBar
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.backdrop.VeneraLiquidGlassNavBar
import com.venera.compose.components.backdrop.VeneraLiquidNavTabs
import com.venera.compose.components.bottomBarClearanceFor
import com.venera.compose.components.wideScreenLayoutMode
import com.venera.compose.feature.explore.SourceSectionScreen
import com.venera.compose.feature.explore.UnifiedExploreScreen
import com.venera.compose.openGalleryPost
import com.venera.compose.gallery.ui.GalleryChromeAutoHide
import com.venera.compose.gallery.ui.GalleryDailyScreen
import com.venera.compose.gallery.ui.GallerySearchHandoff
import com.venera.compose.gallery.ui.GalleryScreen
import com.venera.compose.gallery.ui.GalleryViewModel
import com.venera.compose.feature.favoriteimages.FavoriteImageItem
import com.venera.compose.feature.favoriteimages.toComicItem
import com.venera.compose.source.ComicLinkResolver
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.platform.LocalContext
import com.venera.compose.data.prefs.NavigationBarStyle
import com.venera.compose.ui.tokens.LocalBottomBarClearance
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.reader.ReaderSession
import com.venera.compose.reader.VeneraReaderScreen
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.Scaffold
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

/**
 * S0-3 导航骨架。
 *
 * 取代此前 "var currentTab / var selectedComic / var activeReadingSession" 三个变量硬切的做法：
 * 现在是一个真正的 NavHost 栈，原版 52 个页面里的 39 个「无入口无路由」问题从这里开始收口。
 */

@Serializable data object HomeRoute
@Serializable data object SearchRoute
    /**
     * S8: 详情页标签点击的搜索直达（携带预填关键词）。
     *
     * `sourceName` = 那本漫画所属的源；搜索页据此把目标切到同一个源再搜。
     * 用名字而不是 key：DetailRoute 本身就只携带 sourceName，且同名异源由搜索页
     * 在源列表里精确匹配后决定，解不到就退回默认目标，不猜。
     *
     * `tagNamespace` + `tagRaw` 非空时，这个词以**结构化标签**（[SearchTag]）而不是纯文本
     * 进入搜索页 —— 统计页的题材云下钻必须走这条。差别是实质性的：只有走结构化标签，
     * `TagSearchPolicy` 才会在「非原生标签语法源」上改走客户端过滤；把 `tag:萝莉` 当纯文本
     * 发出去，这些源会拿它当关键字，一条都搜不到。
     * `tagLabel` 只用于已选标签胶囊上的显示（可以是中文规范名）。
     */
    @Serializable data class TagSearchRoute(
        val keyword: String,
        val sourceName: String = "",
        val tagNamespace: String = "",
        val tagRaw: String = "",
        val tagLabel: String = "",
    )
@Serializable data object FavoritesRoute
@Serializable data object HistoryRoute
@Serializable data object ExploreRoute
@Serializable data object CategoriesRoute

/** 画廊主 Tab（两站月度热门混合池，与漫画侧完全隔离的独立模块，见 gallery/ 包）。 */
@Serializable data object GalleryRoute

/**
 * 画廊**二级页**：每日热门全量（2026-09-30 新增）。
 *
 * 首页那一节从此只是预览（一行横卡 + 「查看全部」），全量与「换一批」都搬到了这里。
 *
 * **它是主 Tab 的子页**：`currentTab` 判据里落在这条路由上时算 `GALLERY`
 * （否则底栏会在点进来的那一刻消失 —— 那是"进了个子页面底栏就没了"的观感缺陷，
 * 与历史页那种"降级成二级页、底栏隐藏"是两回事：这里是从画廊 Tab 往下走一层）。
 * 见 [tabIndex] 与 `currentTab` 两处。
 */
@Serializable data object GalleryDailyRoute

/**
 * 画廊二级（单张大图）。
 *
 * `siteKey` 走字符串而不是枚举：导航这版对"Serializable 路由类里嵌枚举"的支持不稳
 * （同一位置塞自定义对象时 `generateRoutePattern` 直接编译失败），字符串键最稳。
 * 两站 id 各自独立编号，所以**站别必须进路由** —— 只带 postId 会拿 A 站的号去 B 站取图，
 * 静默看到另一张不相干的画。
 */

/**
 * 从「探索」页下钻到某个源的**原生**分类 / Tag 列表。
 *
 * 关键：sourceKey 是身份的一部分 —— 同一个 label（如「同人」）在 Picacg 与 nhentai
 * 下是完全不同的东西，绝不能跨源复用。
 * unifiedTag 非空表示这是应用层「通用标签」入口（按关键词搜索），与原生分类区分开。
 */
@Serializable data class SourceSectionRoute(
    val sourceKey: String,
    val sourceTitle: String,
    val category: String,
    val param: String? = null,
    val unifiedTag: String? = null,
)
@Serializable data object DownloadRoute
@Serializable data object LocalComicRoute

/** 详情页的入口载荷。S2 会改成「带 sourceKey+comicId，由 ViewModel 拉真详情」，现在先保持行为一致。 */
@Serializable data class CoverViewerRoute(val coverUrl: String, val title: String)
@Serializable data class DetailRoute(val comicId: String, val sourceName: String)

/** 路由是详情身份的唯一依据；内存条目只能补充同一本漫画的展示数据。 */
internal fun resolveDetailComic(route: DetailRoute, selectedComic: ComicItem?): ComicItem =
    selectedComic?.takeIf { it.id == route.comicId && it.sourceName == route.sourceName }
        ?: ComicItem(
            id = route.comicId,
            title = "",
            author = "",
            coverUrl = "",
            sourceName = route.sourceName,
        )

/** 阅读会话含非序列化对象，交给宿主 ViewModel 暂存（reader 是唯一读它的目的地）。 */
@Serializable data object ReaderRoute

/**
 * 插图预览页。
 *
 * 路由只带**行 id**（`Long` 是导航内建类型）。整条 `FavoriteImageItem` 进参数这条路走不通：
 * type-safe 导航对自定义对象要 safeargs 生成 NavType，本仓库没装，真机上直接崩在建图阶段
 * （详见 FavoriteImageItem 顶部注释）。载荷放 [VeneraShellViewModel.favoriteImagePayloads]。
 */
@Serializable data class FavoriteImageRoute(val itemId: Long)

/**
 * 「读到某一页」的定位请求。
 *
 * 只有插图收藏这条来源：`favorite_images` 表存的是**章节标题**而不是章节 id，
 * 而章节 id 必须等源详情解析出目录才拿得到。所以这里只能带标题 + 页码过去，
 * 由详情页在目录就绪时按标题找回章节并打开阅读器 —— 见 ComicDetailScreen。
 */
data class ReadTarget(
    val comicId: String,
    val sourceName: String,
    val chapterTitle: String,
    val pageIndex: Int,
) {
    constructor(item: FavoriteImageItem) : this(
        comicId = item.comicId,
        sourceName = item.sourceName,
        chapterTitle = item.chapterTitle,
        pageIndex = item.pageIndex,
    )
}

/**
 * 把封面共享元素所需的两个作用域交给页面深处的卡片。
 *
 * 卡片埋在「页面 → 分组 → 行 → 卡」的第四层（搜索页正是如此），逐层灌参数会把每一层
 * 的签名都污染一遍；在导航条目这一层 provide 一次就够。没有 provide 的页面（历史/下载）
 * 读到 null，封面就是不参与飞行的普通封面，照常渲染。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.CoverTransitionHost(
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalCoverTransitionScopes provides CoverTransitionScopes(this, animatedVisibilityScope),
        content = content,
    )
}

/** 外部入口（应用链接 / 别的 app 分享文本）要做的两件事。 */
sealed interface EntryIntent {
    /** ACTION_VIEW：浏览器或别的 app 点进来的一条漫画链接。 */
    data class ComicLink(val url: String) : EntryIntent

    /** ACTION_SEND(text/plain)：分享进来的纯文本，当关键词去搜（master 的 handle_text_share 口径）。 */
    data class SearchText(val text: String) : EntryIntent
}

/**
 * 外部意图的交接通道。
 *
 * 为什么不是「一次性 var 槽位 + `LaunchedEffect(Unit)` 读一次」（`SettingsEscapeHandoff` 那套）：
 * `launchMode="singleTop"` 下热启动走 `onNewIntent`，**不会重新进组合**，那个 Effect 不会再跑 ——
 * 槽位写了也没人读，表现为"app 开着的时候点链接没反应，只有冷启动那一次有效"。
 * 通道两个方向都覆盖：冷启动时消息先排在缓冲里，NavHost 开始 collect 就取到。
 *
 * 容量 UNLIMITED：发送方是 Activity 回调线程，不该阻塞；也不用 CONFLATED ——
 * 两条意图各自都要落地一次，合并会丢一跳。
 */
object EntryIntentHandoff {
    private val _intents = kotlinx.coroutines.channels.Channel<EntryIntent>(
        capacity = kotlinx.coroutines.channels.Channel.UNLIMITED,
    )

    val intents: kotlinx.coroutines.channels.ReceiveChannel<EntryIntent> = _intents

    fun send(intent: EntryIntent) {
        _intents.trySend(intent)
    }
}

/**
 * 落一条外部意图：链接→详情页，文本→搜索页。
 *
 * @return 是否真的推了页。没推的三种原因都要用户看得见 —— 静默停在首页等于白点一次链接。
 */
internal suspend fun NavHostController.handleEntryIntent(
    intent: EntryIntent,
    context: android.content.Context,
): Boolean = when (intent) {
    is EntryIntent.ComicLink ->
        when (val outcome = ComicLinkResolver.getInstance(context).resolve(intent.url)) {
            is ComicLinkResolver.Outcome.Resolved -> {
                // sourceName 传**已核实装着的那个源的显示名**：详情侧 resolveSourceKey 按
                // key/name 两种写法匹配，与列表点击同一口径；能走到这里说明回落分支碰不上。
                navigate(DetailRoute(comicId = outcome.comicId, sourceName = outcome.sourceName))
                true
            }

            else -> {
                android.widget.Toast.makeText(
                    context,
                    entryLinkMessage(outcome, intent.url),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                false
            }
        }

    is EntryIntent.SearchText -> {
        // 复用标签下钻那条目的地：它已带 initialQuery→viewModel.search 的一次性入参链，
        // 且默认聚合（KEY_ALL）正好对上 master 的 AggregatedSearchPage。
        navigate(TagSearchRoute(keyword = intent.text))
        true
    }
}

/** 三种没跳成的原因说人话。 */
private fun entryLinkMessage(outcome: ComicLinkResolver.Outcome, url: String): String = when (outcome) {
    is ComicLinkResolver.Outcome.SourceMissing ->
        "未安装漫画源「${outcome.sourceKeyOrName}」，这条链接打不开"

    is ComicLinkResolver.Outcome.SourcesNotReady -> "漫画源还没加载完，请稍后再点这条链接"
    is ComicLinkResolver.Outcome.Unrecognized -> "认不出这条链接里的漫画：$url"
    is ComicLinkResolver.Outcome.Resolved -> ""
}

class VeneraShellViewModel : ViewModel() {
    var pendingSession: com.venera.compose.reader.ReaderSession? = null
    var selectedComic: ComicItem? = null
    /** 进详情页后要立刻打开的那一页；详情页取用一次即清。 */
    var pendingReadTarget: ReadTarget? = null
    /**
     * 预览页的载荷表：行 id → 那一条收藏。**故意不"取用一次即清"**。
     *
     * 预览页不是叶子目的地 —— 它下面还能压详情页，从详情页返回时这条目的地会重新进组合、
     * 重新回读载荷。谁在这时候清过它（`pendingSession` 那套一次性槽位的写法），返回就变成
     * "整屏 UI 消失再按一次才回得来"。表随 Activity 一起没，量级是每条几百字节。
     */
    val favoriteImagePayloads = mutableMapOf<Long, FavoriteImageItem>()
}

/**
 * 预测式返回手势的起手势边缘 → 上层退出方向。
 *
 * 左边缘（以及三键返回这种 EDGE_NONE）手势是向右推，与点击返回同向，取 +1；
 * 右边缘镜像。返回 1 时两个 predictive lambda 与既有 popEnter / popExit
 * 逐参数相同，所以提交帧换分支时不会产生形状跳变。
 */
private fun predictiveBackDirection(swipeEdge: Int): Int =
    if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1

/*
 * 页面转场一族的单一配方 = 官方 Material Motion 的 **shared axis X**
 * （`io.github.fornewid:material-motion-compose-core`，EhViewer 用的就是它）。
 *
 * 为什么换：旧配方是整屏宽横推 + 全程淡入淡出，那读起来像「换了一页」，而列表→详情是
 * 「同一本书换个容器」；封面又在按自己的 400ms 曲线飞，两套动作并行就散。
 * shared axis X 只推 30dp、淡入按 0.35 阈值错峰，页面几乎不动，封面成为主语。
 * 时长 300ms 与本文件原值一致，滑动距离/错峰比/曲线全取库内官方值，不做本地加码。
 *
 * 现在只有 MainActivity 的壳 NavHost 一个消费者（设置子树已改跨 Activity）。
 */

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.veneraEnter(
    slideDistance: Int,
): EnterTransition = materialSharedAxisXIn(forward = true, slideDistance = slideDistance)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.veneraExit(
    slideDistance: Int,
): ExitTransition = materialSharedAxisXOut(forward = true, slideDistance = slideDistance)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.veneraPopEnter(
    slideDistance: Int,
): EnterTransition = materialSharedAxisXIn(forward = false, slideDistance = slideDistance)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.veneraPopExit(
    slideDistance: Int,
): ExitTransition = materialSharedAxisXOut(forward = false, slideDistance = slideDistance)

/**
 * 预测式返回**跟手**转场：navigation 2.10.1 的 NavHostEventHandler 把系统手势进度喂给
 * SeekableTransitionState.seekTo()，并用 swipeEdge 作 lambda 入参告诉我们是左边缘还是
 * 右边缘起手 —— 所以退出方向能跟随手势。形状一律与上面 popEnter / popExit 同族：
 * 左边缘时 `forward = false`，与点击返回逐参数相同，松手提交不会跳形。
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.veneraPredictiveEnter(
    swipeEdge: Int,
    slideDistance: Int,
): EnterTransition = materialSharedAxisXIn(
    forward = predictiveBackDirection(swipeEdge) < 0,
    slideDistance = slideDistance,
)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.veneraPredictiveExit(
    swipeEdge: Int,
    slideDistance: Int,
): ExitTransition = materialSharedAxisXOut(
    forward = predictiveBackDirection(swipeEdge) < 0,
    slideDistance = slideDistance,
)

private fun routeFor(tab: VeneraNavTab): Any = when (tab) {
    VeneraNavTab.HOME -> HomeRoute
    VeneraNavTab.FAVORITES -> FavoritesRoute
    VeneraNavTab.SEARCH -> SearchRoute
    VeneraNavTab.GALLERY -> GalleryRoute
    VeneraNavTab.EXPLORE -> ExploreRoute
}

/**
 * 这条目的地是第几个主 Tab；不是 Tab 就返回 null。
 *
 * 判据与 [VeneraComposeApp] 里 `currentTab` 那串 `hasRoute` 同源（含 CategoriesRoute 那条
 * 重定向到探索的旧路由）—— 两处若走了两套口径，就会出现"底栏点亮了、切页动画却没有方向"
 * 这种读不出来的不一致。
 */
private fun NavDestination.tabIndex(): Int? {
    val tab = when {
        hasRoute(HomeRoute::class) -> VeneraNavTab.HOME
        hasRoute(FavoritesRoute::class) -> VeneraNavTab.FAVORITES
        hasRoute(SearchRoute::class) -> VeneraNavTab.SEARCH
        hasRoute(GalleryRoute::class) -> VeneraNavTab.GALLERY
        // 子页同样算 GALLERY：与 `currentTab` 那处同源（两处口径分叉就会出现
        // "底栏点亮了、切页动画却没有方向"，那条注释在 `tabIndex` 上写着）。
        hasRoute(GalleryDailyRoute::class) -> VeneraNavTab.GALLERY
        hasRoute(ExploreRoute::class) -> VeneraNavTab.EXPLORE
        hasRoute(CategoriesRoute::class) -> VeneraNavTab.EXPLORE
        else -> return null
    }
    return VeneraNavTab.entries.indexOf(tab)
}

/**
 * 切 Tab 的滑动方向：+1 = 往 Tab 序列后面走，-1 = 往前，null = 两端不全是 Tab。
 *
 * 用户 2026-09-29 的口径是「Tab 顺序与滑动方向一一对应」：首页→收藏新页从右滑入，
 * 收藏→首页镜像。只有**两端都是主 Tab**才这样画 —— 列表→详情、二级页仍走官方 shared axis X，
 * 那是"同一本书换个容器"的形状，封面共享元素按自己的曲线在飞，不能被整页横推盖掉。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSlideDirection(): Int? {
    val from = initialState.destination.tabIndex() ?: return null
    val to = targetState.destination.tabIndex() ?: return null
    return to.compareTo(from).takeIf { it != 0 }
}

/** 整页横推的时长：与 shared axis 那族同值（本文件原值 300ms），只换形状不换节奏。 */
private const val TAB_SLIDE_DURATION_MS = 300

/**
 * 底栏收起时向下滑出的距离。
 *
 * 取一个**足够大**的定值（不是量它的真实高度）：底栏高度是常量口径
 * （`VeneraSpacing.bottomBarHeight` 64dp + `bottomBarBottomGap` 12dp），
 * 但下滑还要盖上系统导航栏那一段（`navigationBarsPadding` 消费的 inset，各机型不同）。
 * 200dp 把这两段都覆盖还有余量，而多滑走的那截在屏幕之外、肉眼不可见 ——
 * 比"读 WindowInsets 再算"少一处会在折叠屏/手势条机型上算错的地方。
 */
private val BOTTOM_BAR_HIDE_DISTANCE = 200.dp

/** 底栏收起/展开的时长：与全站同一档（`VeneraMotionTokens.medium` 的 220ms 量级）。 */
private const val BOTTOM_BAR_HIDE_DURATION_MS = 220

private fun titleFor(tab: VeneraNavTab): String = when (tab) {
    VeneraNavTab.HOME -> "Venera"
    VeneraNavTab.FAVORITES -> "我的收藏"
    VeneraNavTab.SEARCH -> "搜索与发现"
    VeneraNavTab.GALLERY -> "画廊"
    VeneraNavTab.EXPLORE -> "探索"
}

private fun NavHostController.gotoTab(tab: VeneraNavTab) {
    navigate(routeFor(tab)) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) { saveState = true }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VeneraComposeApp() {
    val navController = rememberNavController()
    val shell: VeneraShellViewModel = viewModel()
    val view = LocalView.current
    val context = LocalContext.current
    // 设置 Activity 的三个越界出口（阅读器 / 详情 / 标签搜索）在另一个 Activity 里发起，
    // 目标页只在本图上有：落地时消费一次交接槽，消费即清，避免重建时重复推页。
    LaunchedEffect(Unit) { navController.consumeSettingsEscape(shell) }
    // 外部入口（应用链接 / 收文本）常驻接收：冷启动那条排在缓冲里，开始收就取到；
    // 热启动由 onNewIntent 直接投进来 —— 这里**不能**只读一次槽位，singleTop 不重新进组合。
    LaunchedEffect(Unit) {
        for (entry in EntryIntentHandoff.intents) {
            navController.handleEntryIntent(entry, context)
        }
    }
    val prefs = VeneraPreferences.getInstance(context)
    val navigationBarStyle by prefs.navigationBarStyle.collectAsState()
    val startTab by prefs.startPage.collectAsState()
    // 官方 Backdrop 的 lens 折射用 RuntimeShader，需要 Android 13（TIRAMISU）及以上；
    // 低版本不创建 backdrop / 录制层，直接退回普通悬浮底栏。
    val useLiquidGlass = navigationBarStyle == NavigationBarStyle.LIQUID_GLASS &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val currentTab: VeneraNavTab? = when {
        destination == null -> null
        destination.hasRoute(HomeRoute::class) -> VeneraNavTab.HOME
        // 历史不再是主 Tab（2026-09-23 降回二级页）→ 落在这条路由上时 currentTab 为 null，
        // 底栏与壳顶栏随之隐藏，与其它二级页同一口径。
        destination.hasRoute(FavoritesRoute::class) -> VeneraNavTab.FAVORITES
        destination.hasRoute(SearchRoute::class) -> VeneraNavTab.SEARCH
        destination.hasRoute(GalleryRoute::class) -> VeneraNavTab.GALLERY
        // 画廊的子页（每日热门全量）：仍然算 GALLERY ⇒ **底栏不消失**、切页动画仍有方向。
        // 与"历史页那种降级成二级页、底栏隐藏"是两回事：这是从画廊 Tab 往下走一层。
        destination.hasRoute(GalleryDailyRoute::class) -> VeneraNavTab.GALLERY
        destination.hasRoute(ExploreRoute::class) -> VeneraNavTab.EXPLORE
        // 旧的「分类索引」路由重定向到合并后的「探索」页，避免深链失效。
        destination.hasRoute(CategoriesRoute::class) -> VeneraNavTab.EXPLORE
        // 子页一律 currentTab = null（底栏与壳顶栏隐藏）。设置主页与漫画源管理已搬进
        // SettingsActivity / SettingsSubActivity，走系统跨 activity 转场，不再是本图的目的地。
        else -> null
    }

    fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    /**
     * 大图页点了某枚标签 → 先把用户送回画廊那一屏。
     *
     * 为什么这段必须在**宿主层**、而不是留在 `GalleryScreen` 里那句消费旁边：
     * 画廊收藏是漫画侧收藏页里的一个分段（`FavoritesScreen` 的 `ImageSection.Gallery`），
     * 从**那里**点开一张图再点标签时，画廊那屏根本不在返回栈顶、组合也没了
     * （被下一页覆盖 = 组合销毁，见记忆「导航条目会重建组合」）—— 槽位写了没人读，
     * 用户看到的就是"退回收藏页，得手动切到画廊才进入搜索"。
     * 每日热门二级页那一档同理：`currentTab` 在那儿也算 GALLERY，所以判据读的是
     * **目的地**而不是 Tab，否则从二级页进大图页点标签这一路仍然不跳。
     *
     * 这里**只读不清**（`pending` 是快照属性、`consume()` 才是清）：能消费它的只有
     * `GalleryScreen`，它才拿得到 `GallerySearchViewModel`。宿主层若也清槽，
     * 就变成"谁先到谁吃掉"，两条链互相抢同一笔交接。
     */
    LaunchedEffect(GallerySearchHandoff.pending, destination) {
        if (GallerySearchHandoff.pending == null) return@LaunchedEffect
        if (destination?.hasRoute(GalleryRoute::class) == true) return@LaunchedEffect
        navController.gotoTab(VeneraNavTab.GALLERY)
    }

    fun openComic(comic: ComicItem) {
        haptic()
        shell.selectedComic = comic
        navController.navigate(DetailRoute(comic.id, comic.sourceName))
    }

    /**
     * 插图收藏长按「从该页开始阅读」。
     *
     * 阅读器打不开这一页 —— 它要的 ReaderSession 必须由源解析章节图片，而收藏里只存了
     * 章节标题。所以这里推的是**详情页**，附带一条 pendingReadTarget：详情页解析出目录后
     * 按标题找回章节，再自行推阅读器。标题对不上时详情页会如实提示，不会随手开第一章糊弄。
     */
    fun readFavoriteImage(item: FavoriteImageItem) {
        haptic()
        shell.selectedComic = item.toComicItem()
        shell.pendingReadTarget = ReadTarget(item)
        navController.navigate(DetailRoute(item.comicId, item.sourceName))
    }

    /** 点插图收藏卡 → 预览页。卡片那张图与预览页之间是一条共享元素飞行。 */
    fun openFavoriteImagePreview(item: FavoriteImageItem) {
        haptic()
        shell.favoriteImagePayloads[item.id] = item
        navController.navigate(FavoriteImageRoute(item.id))
    }

    // 录制层只覆盖「背景 + 页面内容」，底栏是它的兄弟节点覆盖在上层，
    // 因此采样源永远不会递归包含底栏自身（挂到祖先上会触发 RenderNode 无限递归崩溃）。
    //
    // onDraw 里先铺不透明底色：官方《Glass Bottom Bar》教程第 2 步指出，只录制页面内容时
    // 底栏下方会出现透明像素（模糊往外扩散成黑块），必须把背景一并画进 backdrop。
    val surfaceBase = MiuixTheme.colorScheme.surface
    val contentLayerBackdrop = if (useLiquidGlass) {
        rememberLayerBackdrop {
            drawRect(surfaceBase)
            drawContent()
        }
    } else null
    // ── 底部留白的唯一注入点 ──
    // 档位判断只在这一处发生，底栏可见性与 LocalBottomBarClearance 由同一个 mode 驱动 ——
    // 「有底栏 ⟺ 有留白」由构造保证，而不是靠各页面自己记。
    // 包在最外层 Box 之外（不是 Scaffold 的 slot 上）：内容层与底栏 overlay 是它的两个子节点，
    // 这样两侧读到同一个数。宽度源与既有 11 处 isWideScreen 消费点同为 screenWidthDp。
    val layoutMode = wideScreenLayoutMode(LocalConfiguration.current.screenWidthDp.dp)
    // 侧栏档（Medium/Expanded）用左侧栏替代底栏；Compact 档原样走底栏。
    // 判定只在这一处发生 —— 底栏可见性、底部留白、侧栏有无三件事由同一个 layoutMode 驱动，
    // 「有底栏 ⟺ 有留白 ⟺ 无侧栏」由构造保证，不靠各页面自己记。
    val useSideBar = layoutMode != WideScreenLayoutMode.Compact
    CompositionLocalProvider(LocalBottomBarClearance provides bottomBarClearanceFor(layoutMode)) {
    Box(modifier = Modifier.fillMaxSize()) {
        VeneraAmbientBackground {
        val layoutDirection = LocalLayoutDirection.current
        // shared axis X 的 30dp 要换成 px，由库的 rememberSlideDistance 负责 density 取整。
        val slideDistance = rememberSlideDistance()
        // ── 大屏侧栏 ──
        // 侧栏只包**内容层**（下面那个 SharedTransitionLayout），不包底栏 overlay：
        // 底栏 overlay 是 VeneraAmbientBackground 的兄弟节点，侧栏档已被 `!useSideBar` 门控掉。
        // Compact 档 VeneraSideBar 内部直通 content（见该组件的 Compact 分支），
        // 所以这里无条件调用即可，不需要在调用点分叉。
        VeneraSideBar(
            currentTab = currentTab,
            onTabSelected = { tab ->
                haptic()
                navController.gotoTab(tab)
            },
            mode = layoutMode,
        ) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            // ── 顶栏控制权已交还各页面（页内自治）──
            // 外壳不再挂 TopAppBar：统一顶栏 = 各页自身的 VeneraTopAppBar（大标题折叠 + 毛玻璃），
            // 彻底消灭「全局顶栏 + 页面二级工具栏」的双层汉堡包割裂结构。
            // 外壳只负责：内容层的系统 insets 与底栏 overlay 几何。
            Scaffold(
                containerColor = Color.Transparent,
                // 不再使用 topBar / bottomBar slot：
                //  - topBar 由各页面自绘（页内自治，见 VeneraTopAppBar）；
                //  - bottomBar 100% 作为 overlay 承载（见下方 Box），避免 Bar 高度被计入 innerPadding
                //    而 Liquid Glass 走 overlay 不计入 —— 那正是两条路径几何不一致的根因。
                // 因此这里显式提供 contentWindowInsets，让页面拿到正确的状态栏/导航栏内边距基准。
                contentWindowInsets = WindowInsets.navigationBars,
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    // 「启动页面」偏好：只决定冷启动落在哪个主 Tab，非法值回落首页。
                    startDestination = routeFor(
                        runCatching { VeneraNavTab.valueOf(startTab) }.getOrDefault(VeneraNavTab.HOME)
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        // 统一契约：顶部不加 padding，让 NavHost 直达屏幕物理顶部 (0,0)，
                        // 页面顶栏才能覆盖状态栏并实现内容自状态栏下方穿过。
                        // 底部由 innerPadding 消费 navigationBars inset。
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .then(if (contentLayerBackdrop != null) Modifier.layerBackdrop(contentLayerBackdrop) else Modifier),
                    // 转场配方见文件顶部 veneraEnter/Exit/PopEnter/PopExit 一组（官方 shared axis X）。
                    // 唯一例外是**主 Tab 之间**：整页水平滑动、方向跟着 Tab 顺序走（判据见 tabSlideDirection）。
                    enterTransition = {
                        val dir = tabSlideDirection()
                        if (dir != null) {
                            slideInHorizontally(
                                animationSpec = tween(TAB_SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
                            ) { fullWidth -> fullWidth * dir }
                        } else {
                            veneraEnter(slideDistance)
                        }
                    },
                    exitTransition = {
                        val dir = tabSlideDirection()
                        if (dir != null) {
                            slideOutHorizontally(
                                animationSpec = tween(TAB_SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
                            ) { fullWidth -> -fullWidth * dir }
                        } else {
                            veneraExit(slideDistance)
                        }
                    },
                    popEnterTransition = { veneraPopEnter(slideDistance) },
                    popExitTransition = { veneraPopExit(slideDistance) },
                    predictivePopEnterTransition = { swipeEdge -> veneraPredictiveEnter(swipeEdge, slideDistance) },
                    predictivePopExitTransition = { swipeEdge -> veneraPredictiveExit(swipeEdge, slideDistance) },
                ) {
                    composable<HomeRoute> {
                        CoverTransitionHost(animatedVisibilityScope = this) {
                            AndroidHomeScreen(
                                // 统一契约：页面只消费底部留白，且真源是 LocalBottomBarClearance（本文件
                                // 上方的注入点），不是 VeneraSpacing 里那个常量 —— 后者只是窄窗档默认值。
                                // 系统 navigationBars inset 由上面的 NavHost padding 统一提供，
                                // 页面**不得**再自行叠加 —— 此前 Liquid 88dp / fallback 8dp 的路径差异
                                // 叠加 Home 内部 80dp Spacer，正是 168dp 双重留白的根因。
                                bottomContentPadding = LocalBottomBarClearance.current,
                                animatedVisibilityScope = this,
                                onSelect = ::openComic,
                                onOpenHistory = {
                                    haptic()
                                    // 历史已降回二级页：分区头直达 = 压栈，能返回（原先是切 Tab 不压栈，
                                    // 那套返回语义随主 Tab 一起撤掉了）。
                                    navController.navigate(HistoryRoute)
                                },
                                // 以下四个入口一律跨 Activity，与齿轮同一口径：只有跨过 Activity
                                // 边界，系统才施加预测式返回动画与返回模糊。
                                onOpenStats = {
                                    haptic()
                                    context.openSettingsSubScreen(SettingsSubScreen.STATS)
                                },
                                onOpenLocal = {
                                    haptic()
                                    context.openSettingsSubScreen(SettingsSubScreen.LOCAL_COMICS)
                                },
                                // 图片收藏已并入收藏页第三个分段，主页那个入口随之删除。
                                onOpenSourceManage = {
                                    haptic()
                                    context.openSettingsSubScreen(SettingsSubScreen.SOURCE_MANAGE)
                                },
                                // 设置齿轮从外壳迁入首页顶栏（页内自治）。
                                // 跨 Activity 而不是 NavHost 目的地：只有跨过 Activity 边界，
                                // 系统才会施加 AOSP 跨 activity 预测式返回动画。
                                onOpenSettings = {
                                    haptic()
                                    context.startActivity(
                                        Intent(context, SettingsActivity::class.java),
                                    )
                                }
                            )
                        }
                    }
                    composable<SearchRoute> {
                        // 主 Tab：底栏显示，需要避让。
                        CoverTransitionHost(animatedVisibilityScope = this) {
                            AndroidSearchScreen(
                                animatedVisibilityScope = this,
                                onSelect = ::openComic,
                                consumesBottomBarClearance = true,
                            )
                        }
                    }
                    // S8: 标签直达搜索（详情页标签点击跳入，自动执行搜索）
                    composable<TagSearchRoute> { backStackEntry ->
                        val args = backStackEntry.arguments
                        val keyword = args?.getString("keyword") ?: ""
                        val sourceName = args?.getString("sourceName") ?: ""
                        val tagNamespace = args?.getString("tagNamespace") ?: ""
                        val tagRaw = args?.getString("tagRaw") ?: ""
                        val tagLabel = args?.getString("tagLabel") ?: ""
                        // 下钻子页：底栏不显示，只保留自身滚动留白。
                        CoverTransitionHost(animatedVisibilityScope = this) {
                            AndroidSearchScreen(
                                animatedVisibilityScope = this,
                                onSelect = ::openComic,
                                initialQuery = keyword,
                                initialSourceName = sourceName,
                                initialTag = if (tagRaw.isBlank()) null
                                    else SearchTag(
                                        namespace = tagNamespace,
                                        raw = tagRaw,
                                        label = tagLabel.ifBlank { tagRaw },
                                    ),
                                consumesBottomBarClearance = false,
                            )
                        }
                    }
                    composable<FavoritesRoute> {
                        CoverTransitionHost(animatedVisibilityScope = this) {
                            AndroidFavoritesScreen(
                                animatedVisibilityScope = this,
                                onSelect = ::openComic,
                                onPreviewFavoriteImage = ::openFavoriteImagePreview,
                                onReadFavoriteImage = ::readFavoriteImage,
                            )
                        }
                    }
                    composable<FavoriteImageRoute> { entry ->
                        val route = entry.toRoute<FavoriteImageRoute>()
                        // **每次重组都回读**，不套 remember、也不在退场时清：这条目的地下面还能压
                        // 详情页，从详情页返回时它会重新进组合 —— 旧的一次性槽位写法就是在这里读到
                        // null，既画不出内容（整屏空）又自行 pop 一次，真机报的"返回两次 UI 消失"。
                        val item = shell.favoriteImagePayloads[route.itemId]
                        if (item == null) {
                            // 只剩进程被杀后恢复这一种可能：VM 没了、表也空了。如实退回，
                            // 不摆一张没有内容的预览页。
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            CoverTransitionHost(animatedVisibilityScope = this) {
                                FavoriteImagePreviewScreen(
                                    item = item,
                                    onBack = {
                                        haptic()
                                        navController.popBackStack()
                                    },
                                    onOpenComicDetail = { openComic(it.toComicItem()) },
                                    onReadFromPage = ::readFavoriteImage,
                                )
                            }
                        }
                    }
                    composable<HistoryRoute> {
                        // 二级页（2026-09-23 起）：有返回语义，从首页分区头 / 设置 / 阅读器三处进来。
                        CoverTransitionHost(animatedVisibilityScope = this) {
                            AndroidHistoryScreen(
                                onSelect = ::openComic,
                                onBack = {
                                    haptic()
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                    composable<GalleryRoute> {
                        // 大图页**不再是本图的目的地**：2026-09-25 起它是独立 Activity
                        // （`GalleryPostActivity`）—— 只有跨过 Activity 边界，系统才会
                        // ① 实时糊掉后面这一屏（blur-behind 背景模糊）、② 施加 AOSP 跨 activity
                        // 预测式返回动画。向上滑入期间垫着的那一帧由 GalleryScreen 在点击那一刻截好。
                        // 单击的震动反馈在 GalleryScreen 里已经做过，这里不再 haptic()。
                        //
                        // 包这一层只为一件事：把封面共享元素的两个作用域交给深处那张横卡
                        // （首页「每日热门」预览行 ↔ 二级页同一张）。不包 = `LocalCoverTransitionScopes`
                        // 读到 null = 画廊里任何卡片都挂不上飞行，而它**不报错**，只表现为"没有过渡"。
                        CoverTransitionHost(animatedVisibilityScope = this) {
                            GalleryScreen(
                                onOpenPost = { site, postId ->
                                    (view.context as? Activity)?.openGalleryPost(site, postId)
                                },
                                onOpenDailyAll = {
                                    haptic()
                                    navController.navigate(GalleryDailyRoute)
                                },
                            )
                        }
                    }
                    composable<GalleryDailyRoute> {
                        // 「每日热门」二级页：**复用首页那一个 `GalleryViewModel`**。
                        //
                        // 不新开 `viewModel()` 的理由有两条，都不是省事：
                        // 1. 新实例会让这一页重新联网拉一遍同一份日榜（同一个池子、等两次网络）；
                        // 2. 「换一批」是**零请求的本地重排**，它吃的正是首页那份**原始池**
                        //    （`GalleryViewModel.pools`）—— 新实例手上没有池子，按下去只能真取一次。
                        //
                        // 取同一个实例的唯一办法是借用 `GalleryRoute` 那个返回栈条目作 owner：
                        // 本项目其余二级页没有"与主 Tab 共享 VM"的需求，所以这是第一处。
                        // ⚠️ 深链直接落到这一页时 `GalleryRoute` 条目不存在，`getBackStackEntry`
                        // 会抛 IllegalArgumentException —— 所以先 `runCatching`，取不到就退化成
                        // 自己的实例（那一档只有"冷启动直达"一种，联网拉一遍是可接受的代价，
                        // 总比崩掉好）。
                        val dailyOwner = remember(navController) {
                            runCatching { navController.getBackStackEntry(GalleryRoute) }.getOrNull()
                        }
                        val dailyViewModel: GalleryViewModel = if (dailyOwner != null) {
                            viewModel(viewModelStoreOwner = dailyOwner)
                        } else {
                            viewModel()
                        }
                        // 与 `GalleryRoute` 同一对作用域：飞行要**两端都挂**才成立，
                        // 只包首页那一头，二级页就是普通换页（key 对不上等于没挂）。
                        CoverTransitionHost(animatedVisibilityScope = this) {
                            GalleryDailyScreen(
                                vm = dailyViewModel,
                                onOpenPost = { site, postId ->
                                    (view.context as? Activity)?.openGalleryPost(site, postId)
                                },
                                onBack = {
                                    haptic()
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                    composable<ExploreRoute> {
                        CoverTransitionHost(animatedVisibilityScope = this) {
                            UnifiedExploreScreen(
                                onSelectComic = ::openComic,
                                onOpenNativeSection = { args ->
                                    haptic()
                                    navController.navigate(
                                        SourceSectionRoute(
                                            sourceKey = args.sourceKey,
                                            sourceTitle = args.sourceTitle,
                                            category = args.category,
                                            param = args.param,
                                            unifiedTag = args.unifiedTag?.name,
                                        )
                                    )
                                },
                            )
                        }
                    }
                    // 旧「分类索引」入口重定向到合并后的探索页。
                    composable<CategoriesRoute> {
                        CoverTransitionHost(animatedVisibilityScope = this) {
                            UnifiedExploreScreen(
                                onSelectComic = ::openComic,
                                onOpenNativeSection = { args ->
                                    haptic()
                                    navController.navigate(
                                        SourceSectionRoute(
                                            sourceKey = args.sourceKey,
                                            sourceTitle = args.sourceTitle,
                                            category = args.category,
                                            param = args.param,
                                            unifiedTag = args.unifiedTag?.name,
                                        )
                                    )
                                },
                            )
                        }
                    }
                    composable<SourceSectionRoute> { entry ->
                        val route = entry.toRoute<SourceSectionRoute>()
                        CoverTransitionHost(animatedVisibilityScope = this) {
                            SourceSectionScreen(
                                route = route,
                                onBack = {
                                    haptic()
                                    navController.popBackStack()
                                },
                                onSelectComic = ::openComic,
                            )
                        }
                    }
                    composable<DownloadRoute> {
                        DownloadScreen(
                            onBack = {
                                haptic()
                                navController.popBackStack()
                            },
                            onNavigateToLocalLibrary = {
                                haptic()
                                navController.navigate(LocalComicRoute)
                            }
                        )
                    }
                    composable<LocalComicRoute> {
                        LocalComicScreen(
                            onBack = {
                                haptic()
                                navController.popBackStack()
                            },
                            onOpenLocalSession = { session ->
                                haptic()
                                shell.pendingSession = session
                                navController.navigate(ReaderRoute)
                            },
                            onNavigateToDownloads = {
                                haptic()
                                navController.navigate(DownloadRoute)
                            },
                            onOpenComicDetail = { item ->
                                haptic()
                                shell.selectedComic = item
                                navController.navigate(DetailRoute(comicId = item.id, sourceName = item.sourceName))
                            }
                        )
                    }
                    composable<DetailRoute> { entry ->
                        val route = entry.toRoute<DetailRoute>()
                        val comic = resolveDetailComic(route, shell.selectedComic)
                        // 与 pendingSession 同一条纪律：只在进入这一条时取一次，退场动画期间
                        // 重组不回读，否则同一页会被再推一次。取完即从壳里摘掉，所有权交给本条目。
                        val readTarget = remember {
                            shell.pendingReadTarget?.takeIf {
                                it.comicId == route.comicId && it.sourceName == route.sourceName
                            }
                        }
                        LaunchedEffect(readTarget) {
                            if (readTarget != null && shell.pendingReadTarget === readTarget) {
                                shell.pendingReadTarget = null
                            }
                        }
                        AndroidComicDetailScreen(
                            comic = comic,
                            animatedVisibilityScope = this,
                            readTarget = readTarget,
                            onBack = {
                                haptic()
                                navController.popBackStack()
                            },
                            onStartLiveReading = { session ->
                                haptic()
                                shell.pendingSession = session
                                navController.navigate(ReaderRoute)
                            },
                            onSearchTag = { tag ->
                                haptic()
                                // 带上这本漫画的源：点标签应该「在同源里搜这个词」，
                                // 而不是掉回全网聚合 —— 标签词本来就是源生的。
                                navController.navigate(
                                    TagSearchRoute(keyword = tag, sourceName = comic.sourceName)
                                )
                            },
                            onOpenCoverViewer = { url ->
                                haptic()
                                navController.navigate(CoverViewerRoute(coverUrl = url, title = comic.title))
                            },
                        )
                    }
                    // S8 批次C: 封面全屏查看器
                    composable<CoverViewerRoute> { entry ->
                        val cover = entry.toRoute<CoverViewerRoute>()
                        CoverViewerScreen(
                            coverUrl = cover.coverUrl,
                            title = cover.title,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<ReaderRoute> { entry ->
                        // 会话**只在进入这一条时取一次**，之后不再回读：
                        //  1) 退场动画期间条目仍会被重组，若那时回读到 null 就会翻进下面的
                        //     自动退出分支 —— LaunchedEffect(Unit) 作为新进入组合的节点再 pop
                        //     一次，把详情页一起弹掉（实测：点左上角箭头直达首页，
                        //     而系统返回手势正常，差别正是 onBack 里那次写 null）。
                        //  2) 同时也保证动画期间内容不会先变空白。
                        val session = remember { shell.pendingSession }
                        // 系统 pop / 预测返回不经过 onBack lambda：条目离开组合后统一清理会话。
                        DisposableEffect(entry) {
                            onDispose {
                                if (shell.pendingSession === session) shell.pendingSession = null
                            }
                        }
                        if (session == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            VeneraReaderScreen(
                                session = session,
                                // 这里**不**清 pendingSession：清的理由只有"下一次进入前别复用旧会话"，
                                // 而那件事已由上面的 onDispose 兜住。在 pop 之前清它，
                                // 就是上面那条双弹的起因。
                                onBack = {
                                    haptic()
                                    navController.popBackStack()
                                },
                                onOpenHistory = {
                                    haptic()
                                    navController.navigate(HistoryRoute)
                                },
                            )
                        }
                    }
                }
            }
        } // ← SharedTransitionLayout
        } // ← VeneraSideBar（侧栏只包内容层；Compact 档内部直通）
        // 玻璃底栏作为覆盖层叠在内容之上，位于录制层之外（Pixez 结构）。
        // 录制只覆盖页面内容，因此玻璃采样不会递归包含自身。
        // 用 Box 承担底部对齐：VeneraAmbientBackground 的 content 是普通 lambda，
        // 不是 BoxScope，直接 .align() 不会生效（底栏会跑到左上角）。
        //
        // ── 「画廊：下滑收起底栏」那一道闸（2026-09-30）──
        // 状态住在 `GalleryChromeAutoHide`（页内滚动写、壳层读，形状见那个对象）。
        // ⚠️ 这里**必须**再判一次"当前是不是画廊"：
        //  `hidden` 是页内写进跨层对象的快照值，而底栏是**壳层共享**的 ——
        //  在画廊收了栏、切到首页，若这里无条件跟着隐，用户会发现底栏在别的页面也永久消失了，
        //  而那时画廊那一屏早已不在组合里、没有任何人会去复位它。
        // 判据用 `currentTab == GALLERY`（`GalleryDailyRoute` 也算 GALLERY，见那两处映射）：
        // 二级页是画廊往下走的一层，底栏行为与画廊一致。
        val chromeHiddenHere = GalleryChromeAutoHide.hidden &&
            GalleryChromeAutoHide.bottomEnabled &&
            currentTab == VeneraNavTab.GALLERY
        val bottomBarOffset by animateDpAsState(
            targetValue = if (chromeHiddenHere) BOTTOM_BAR_HIDE_DISTANCE else 0.dp,
            animationSpec = tween(BOTTOM_BAR_HIDE_DURATION_MS),
            label = "galleryBottomBarHideOffset",
        )
        Box(modifier = Modifier.fillMaxSize()) {
            // ── 大屏侧栏档：整条底栏不渲染，改由左侧栏承担导航（master `nav:933` 同款裁决）──
            // master 的 `shouldShowAppBar = controller.value < 2` 同时门控顶栏（`:957`）、
            // 底栏占位（`:967-968`）与整段 overlay（`:972-974` 提前 return）。
            // 本仓顶栏是**页内自治**（各页自绘 VeneraTopAppBar），外壳管不到，
            // 所以这里只关底栏；顶栏的收起在批次 2 之后按拍板项 13 另行处理。
            //
            // ⚠️ 底栏消失是 bottomBarClearanceFor 归 0 的**前提**（见 WideScreenPolicy.kt
            // 那个函数的 KDoc）—— 两者必须同一批落地，回退侧栏时必须同时回退那两档。
            if (currentTab != null && !useSideBar && contentLayerBackdrop != null) {
                val isDark = LocalVeneraDarkTheme.current
                VeneraLiquidGlassNavBar(
                    selectedTabIndex = { VeneraNavTab.entries.indexOf(currentTab) },
                    onTabSelected = { index ->
                        haptic()
                        navController.gotoTab(VeneraNavTab.entries[index])
                    },
                    backdrop = contentLayerBackdrop,
                    tabsCount = VeneraNavTab.entries.size,
                    isLightTheme = !isDark,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // 底栏自己负责它与系统 inset 的关系（navigationBars inset 由它消费）：
                        .navigationBarsPadding()
                        .padding(
                            bottom = VeneraSpacing.bottomBarBottomGap,
                            start = VeneraSpacing.space8,
                            end = VeneraSpacing.space8,
                        )
                        .fillMaxWidth()
                        // 下滑收起：整条往下滑出屏幕（含它自己的底部间距）。
                        // 与顶栏那一处同一口径：只偏移绘制，不改页面内的补偿常量 ——
                        // 内容底部留白在收起期间仍是 `bottomBarClearance`（滑出后那一段本来就在
                        // 屏幕之外，若跟着收会让最后一行在收起瞬间被重排一下）。
                        .offset(y = bottomBarOffset),
                ) {
                    VeneraLiquidNavTabs(
                        tabs = VeneraNavTab.entries,
                        currentTab = currentTab,
                        isDark = isDark,
                        onTabSelected = { tab ->
                            haptic()
                            navController.gotoTab(tab)
                        },
                    )
                }
            } else if (currentTab != null && !useSideBar) {
                // Fallback：普通悬浮底栏，作为**同样的 overlay** 承载。
                //
                // 关键：它与 Liquid Glass 共用完全相同的几何修饰符链
                // （align(BottomCenter) + navigationBarsPadding + bottomBarBottomGap + 相同水平边距），
                // 因此两条路径的 bottomBarClearance 契约天然一致 —— 不存在
                // 「Liquid 正确、Floating 多留一块空白」的情况。
                // 差异仅在于视觉实现（纯色胶囊 vs 玻璃折射），几何零差异。
                VeneraFloatingNavBar(
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        haptic()
                        navController.gotoTab(tab)
                    },
                    // VeneraFloatingNavBar 自带 .padding(bottom = 12.dp)（== bottomBarBottomGap）
                    // 且 barWidth 已内含 28dp 两侧边距，因此这里**只补系统 navigationBars inset**，
                    // 不再叠加任何底部/水平间距 —— 否则会与内建值重复。
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .offset(y = bottomBarOffset),
                )
            }
        }
        }
    }
    }
}
