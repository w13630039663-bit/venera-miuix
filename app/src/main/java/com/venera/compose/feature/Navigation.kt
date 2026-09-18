package com.venera.compose.feature

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
import com.venera.compose.feature.sourcemanage.ComicSourceScreen
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
    /** S8: 详情页标签点击的搜索直达（携带预填关键词） */
    @Serializable data class TagSearchRoute(val keyword: String)
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
@Serializable data object SettingsRoute
@Serializable data object ComicSourceManageRoute
@Serializable data object DownloadRoute
@Serializable data object LocalComicRoute
@Serializable data object StatsRoute
@Serializable data object FavoriteImagesRoute
@Serializable data object ContentGuardRoute
@Serializable data object SyncBackupRoute
@Serializable data object LogViewerRoute

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

class VeneraShellViewModel : ViewModel() {
    var pendingSession: com.venera.compose.reader.ReaderSession? = null
    var selectedComic: com.venera.compose.feature.ComicItem? = null
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
    val view = LocalView.current
    val prefs = VeneraPreferences.getInstance(LocalContext.current)
    val navigationBarStyle by prefs.navigationBarStyle.collectAsState()
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
        // SettingsRoute 不再是主 Tab：作为顶栏齿轮进入的子页（currentTab = null，
        // 底栏与壳顶栏隐藏），按返回键 / 预测返回退回来源 Tab。
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
                    startDestination = HomeRoute,
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
                    // 预返回跟手：navigation-compose 2.8.9 的 seekable predictive back
                    // 会在手势进度中驱动 popExit 转场，取消时平滑回弹。
                    enterTransition = {
                        slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(animationSpec = tween(300))
                    },
                    exitTransition = {
                        slideOutHorizontally(animationSpec = tween(300)) { -it / 4 } + fadeOut(animationSpec = tween(300))
                    },
                    popEnterTransition = {
                        slideInHorizontally(animationSpec = tween(300)) { -it / 4 } + fadeIn(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut(animationSpec = tween(300))
                    },
                ) {
                    composable<HomeRoute> {
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
                            onOpenStats = {
                                haptic()
                                navController.navigate(StatsRoute)
                            },
                            onOpenLocal = {
                                haptic()
                                navController.navigate(LocalComicRoute)
                            },
                            onOpenImageFavorites = {
                                haptic()
                                navController.navigate(FavoriteImagesRoute)
                            },
                            onOpenSourceManage = {
                                haptic()
                                navController.navigate(ComicSourceManageRoute)
                            },
                            // 设置齿轮从外壳迁入首页顶栏（页内自治）
                            onOpenSettings = {
                                haptic()
                                navController.navigate(SettingsRoute)
                            }
                        )
                    }
                    composable<SearchRoute> {
                        // 主 Tab：底栏显示，需要避让。
                        AndroidSearchScreen(
                            animatedVisibilityScope = this,
                            onSelect = ::openComic,
                            consumesBottomBarClearance = true,
                        )
                    }
                    // S8: 标签直达搜索（详情页标签点击跳入，自动执行搜索）
                    composable<TagSearchRoute> { backStackEntry ->
                        val keyword = backStackEntry.arguments?.getString("keyword") ?: ""
                        // 下钻子页：底栏不显示，只保留自身滚动留白。
                        AndroidSearchScreen(
                            animatedVisibilityScope = this,
                            onSelect = ::openComic,
                            initialQuery = keyword,
                            consumesBottomBarClearance = false,
                        )
                    }
                    composable<FavoritesRoute> {
                        AndroidFavoritesScreen(animatedVisibilityScope = this, onSelect = ::openComic)
                    }
                    composable<HistoryRoute> {
                        // 主 Tab：底栏常驻，无返回语义。
                        AndroidHistoryScreen(
                            onSelect = ::openComic,
                        )
                    }
                    composable<ExploreRoute> {
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
                    // 旧「分类索引」入口重定向到合并后的探索页。
                    composable<CategoriesRoute> {
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
                    composable<SourceSectionRoute> { entry ->
                        val route = entry.toRoute<SourceSectionRoute>()
                        SourceSectionScreen(
                            route = route,
                            onBack = {
                                haptic()
                                navController.popBackStack()
                            },
                            onSelectComic = ::openComic,
                        )
                    }
                    composable<SettingsRoute> {
                        // 子页形态：顶栏齿轮进入，返回退回来源主 Tab。
                        AndroidSettingsScreen(
                            onBack = {
                                haptic()
                                navController.popBackStack()
                            },
                            onNavigateToSourceManage = {
                                haptic()
                                navController.navigate(ComicSourceManageRoute)
                            },
                            onNavigateToDownloads = {
                                haptic()
                                navController.navigate(DownloadRoute)
                            },
                            onNavigateToLocalComics = {
                                haptic()
                                navController.navigate(LocalComicRoute)
                            },
                            onNavigateToStats = {
                                haptic()
                                navController.navigate(StatsRoute)
                            },
                            onNavigateToFavoriteImages = {
                                haptic()
                                navController.navigate(FavoriteImagesRoute)
                            },
                            onNavigateToGuard = {
                                haptic()
                                navController.navigate(ContentGuardRoute)
                            },
                            onNavigateToSync = {
                                haptic()
                                navController.navigate(SyncBackupRoute)
                            },
                            onNavigateToLogs = {
                                haptic()
                                navController.navigate(LogViewerRoute)
                            }
                        )
                    }
                    composable<ComicSourceManageRoute> {
                        ComicSourceScreen(
                            onNavigateBack = {
                                haptic()
                                navController.popBackStack()
                            }
                        )
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
                    composable<StatsRoute> {
                        StatsScreen(
                            onBack = {
                                haptic()
                                navController.popBackStack()
                            }
                        )
                    }
                    composable<FavoriteImagesRoute> {
                        FavoriteImagesScreen(
                            onBack = {
                                haptic()
                                navController.popBackStack()
                            }
                        )
                    }
                    composable<ContentGuardRoute> {
                        ContentGuardScreen(
                            onBack = {
                                haptic()
                                navController.popBackStack()
                            }
                        )
                    }
                    composable<SyncBackupRoute> {
                        SyncBackupScreen(
                            onBack = {
                                haptic()
                                navController.popBackStack()
                            }
                        )
                    }
                    composable<LogViewerRoute> {
                        LogViewerScreen(
                            onBack = {
                                haptic()
                                navController.popBackStack()
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
                                navController.navigate(TagSearchRoute(keyword = tag))
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
