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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
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
import com.venera.compose.components.VeneraFloatingNavBar
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.components.backdrop.VeneraLiquidGlassNavBar
import com.venera.compose.components.backdrop.VeneraLiquidNavTabs
import com.venera.compose.feature.explore.SourceSectionScreen
import com.venera.compose.feature.explore.UnifiedExploreScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.platform.LocalContext
import com.venera.compose.data.prefs.NavigationBarStyle
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.reader.ReaderSession
import com.venera.compose.reader.VeneraReaderScreen
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.Scaffold
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

class VeneraShellViewModel : ViewModel() {
    var pendingSession: com.venera.compose.reader.ReaderSession? = null
    var selectedComic: com.venera.compose.feature.ComicItem? = null
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
 * 横滑转场一族的单一配方。MainActivity 的壳 NavHost 与 SettingsActivity 的设置 NavHost
 * 共用这四个函数 —— 配方复制成两份必然飘（时长、视差比各改一处就不同步）。
 * 时长 300ms 与视差比 1/4 都是本文件既有值，不是新造的数。
 */

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.veneraEnter(): EnterTransition =
    slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(animationSpec = tween(300))

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.veneraExit(): ExitTransition =
    slideOutHorizontally(animationSpec = tween(300)) { -it / 4 } + fadeOut(animationSpec = tween(300))

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.veneraPopEnter(): EnterTransition =
    slideInHorizontally(animationSpec = tween(300)) { -it / 4 } + fadeIn(animationSpec = tween(300))

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.veneraPopExit(): ExitTransition =
    slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut(animationSpec = tween(300))

/**
 * 预测式返回**跟手**转场：navigation 2.10.1 的 NavHostEventHandler 把系统手势进度喂给
 * SeekableTransitionState.seekTo()，并用 swipeEdge 作 lambda 入参告诉我们是左边缘还是
 * 右边缘起手 —— 所以退出方向能跟随手势。形状一律沿用上面 popEnter / popExit 那一族，
 * 不引入新数字：左边缘时两条分支逐参数相同，松手提交才不会跳形。
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.veneraPredictiveEnter(
    swipeEdge: Int,
): EnterTransition {
    val dir = predictiveBackDirection(swipeEdge)
    return slideInHorizontally(animationSpec = tween(300)) { -it / 4 * dir } +
        fadeIn(animationSpec = tween(300))
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.veneraPredictiveExit(
    swipeEdge: Int,
): ExitTransition {
    val dir = predictiveBackDirection(swipeEdge)
    return slideOutHorizontally(animationSpec = tween(300)) { it * dir } +
        fadeOut(animationSpec = tween(300))
}

private fun routeFor(tab: VeneraNavTab): Any = when (tab) {
    VeneraNavTab.HOME -> HomeRoute
    VeneraNavTab.HISTORY -> HistoryRoute
    VeneraNavTab.FAVORITES -> FavoritesRoute
    VeneraNavTab.SEARCH -> SearchRoute
    VeneraNavTab.EXPLORE -> ExploreRoute
}

private fun titleFor(tab: VeneraNavTab): String = when (tab) {
    VeneraNavTab.HOME -> "Venera"
    VeneraNavTab.HISTORY -> "历史"
    VeneraNavTab.FAVORITES -> "我的收藏"
    VeneraNavTab.SEARCH -> "搜索与发现"
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
    // 设置 Activity 的三个越界出口（阅读器 / 详情 / 标签搜索）在另一个 Activity 里发起，
    // 目标页只在本图上有：落地时消费一次交接槽，消费即清，避免重建时重复推页。
    LaunchedEffect(Unit) { navController.consumeSettingsEscape(shell) }
    val view = LocalView.current
    val context = LocalContext.current
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
        destination.hasRoute(HistoryRoute::class) -> VeneraNavTab.HISTORY
        destination.hasRoute(FavoritesRoute::class) -> VeneraNavTab.FAVORITES
        destination.hasRoute(SearchRoute::class) -> VeneraNavTab.SEARCH
        destination.hasRoute(ExploreRoute::class) -> VeneraNavTab.EXPLORE
        // 旧的「分类索引」路由重定向到合并后的「探索」页，避免深链失效。
        destination.hasRoute(CategoriesRoute::class) -> VeneraNavTab.EXPLORE
        // 子页一律 currentTab = null（底栏与壳顶栏隐藏）。设置主页与漫画源管理已搬进
        // SettingsActivity / SettingsSubActivity，走系统跨 activity 转场，不再是本图的目的地。
        else -> null
    }

    fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    fun openComic(comic: ComicItem) {
        haptic()
        shell.selectedComic = comic
        navController.navigate(DetailRoute(comic.id, comic.sourceName))
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
    Box(modifier = Modifier.fillMaxSize()) {
        VeneraAmbientBackground {
        val layoutDirection = LocalLayoutDirection.current
        val navigationInsets = WindowInsets.navigationBars.asPaddingValues()
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            // 左右滑切页的「让位带」登记表：外壳与页面必须共用同一个实例。
            // 首页推荐轮播那一整块自己吃横滑，起手点落在它里面就不许翻页。
            val swipeExclusions = remember { TabSwipeExclusionRegistry() }
            CompositionLocalProvider(LocalTabSwipeExclusions provides swipeExclusions) {
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
                        .then(if (contentLayerBackdrop != null) Modifier.layerBackdrop(contentLayerBackdrop) else Modifier)
                        // 主页面之间左右滑动切页；只在 5 个主 tab 上生效，
                        // 详情页/阅读器等子页面不参与（currentTab == null）。
                        .tabSwipePager(
                            enabled = currentTab != null,
                            exclusions = swipeExclusions,
                            // 底栏手势排除带 = 契约 clearanc + 系统 navigationBars inset。
                            // 不再手写 64 + 12 这类几何 magic number。
                            bottomExclusionDp = (
                                VeneraSpacing.bottomBarClearance +
                                    navigationInsets.calculateBottomPadding()
                                ).value.toInt(),
                            onSwipeForward = {
                                val tabs = VeneraNavTab.entries
                                val i = tabs.indexOf(currentTab).coerceAtLeast(0)
                                if (i < tabs.lastIndex) {
                                    haptic()
                                    navController.gotoTab(tabs[i + 1])
                                }
                            },
                            onSwipeBackward = {
                                val tabs = VeneraNavTab.entries
                                val i = tabs.indexOf(currentTab).coerceAtLeast(0)
                                if (i > 0) {
                                    haptic()
                                    navController.gotoTab(tabs[i - 1])
                                }
                            },
                        ),
                    // 转场配方见文件顶部 veneraEnter/Exit/PopEnter/PopExit 一组：
                    // 与 SettingsActivity 的设置 NavHost 共用同一份，不在这里各写一遍。
                    enterTransition = { veneraEnter() },
                    exitTransition = { veneraExit() },
                    popEnterTransition = { veneraPopEnter() },
                    popExitTransition = { veneraPopExit() },
                    predictivePopEnterTransition = { swipeEdge -> veneraPredictiveEnter(swipeEdge) },
                    predictivePopExitTransition = { swipeEdge -> veneraPredictiveExit(swipeEdge) },
                ) {
                    composable<HomeRoute> {
                        CoverTransitionHost(animatedVisibilityScope = this) {
                            AndroidHomeScreen(
                                // 统一契约：页面只消费 bottomBarClearance（底栏高度 + 底栏底部间距）。
                                // 系统 navigationBars inset 由上面的 NavHost padding 统一提供，
                                // 页面**不得**再自行叠加 —— 此前 Liquid 88dp / fallback 8dp 的路径差异
                                // 叠加 Home 内部 80dp Spacer，正是 168dp 双重留白的根因。
                                bottomContentPadding = VeneraSpacing.bottomBarClearance,
                                animatedVisibilityScope = this,
                                onSelect = ::openComic,
                                onOpenHistory = {
                                    haptic()
                                    // 历史已是主 Tab：分区头直达 = 切 Tab（不压栈，返回语义不变）。
                                    navController.gotoTab(VeneraNavTab.HISTORY)
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
                                onOpenImageFavorites = {
                                    haptic()
                                    context.openSettingsSubScreen(SettingsSubScreen.FAVORITE_IMAGES)
                                },
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
                            )
                        }
                    }
                    composable<HistoryRoute> {
                        // 主 Tab：底栏常驻，无返回语义。
                        CoverTransitionHost(animatedVisibilityScope = this) {
                            AndroidHistoryScreen(
                                onSelect = ::openComic,
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
                        AndroidComicDetailScreen(
                            comic = comic,
                            animatedVisibilityScope = this,
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
                        val session = shell.pendingSession
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
                                onBack = {
                                    haptic()
                                    shell.pendingSession = null
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                }
            }
        }
        // 玻璃底栏作为覆盖层叠在内容之上，位于录制层之外（Pixez 结构）。
        // 录制只覆盖页面内容，因此玻璃采样不会递归包含自身。
        // 用 Box 承担底部对齐：VeneraAmbientBackground 的 content 是普通 lambda，
        // 不是 BoxScope，直接 .align() 不会生效（底栏会跑到左上角）。
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentTab != null && contentLayerBackdrop != null) {
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
                        .fillMaxWidth(),
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
            } else if (currentTab != null) {
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
                        .fillMaxWidth(),
                )
            }
            }
        }
        }
    }
}
