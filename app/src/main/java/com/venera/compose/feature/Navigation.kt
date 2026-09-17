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
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.reader.ReaderSession
import com.venera.compose.reader.VeneraReaderScreen
import com.venera.compose.feature.sourcemanage.ComicSourceScreen
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar

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
    VeneraNavTab.SEARCH -> SearchRoute
    VeneraNavTab.FAVORITES -> FavoritesRoute
    VeneraNavTab.EXPLORE -> ExploreRoute
    VeneraNavTab.SETTINGS -> SettingsRoute
}

private fun titleFor(tab: VeneraNavTab): String = when (tab) {
    VeneraNavTab.HOME -> "Venera"
    VeneraNavTab.SEARCH -> "搜索与发现"
    VeneraNavTab.FAVORITES -> "我的收藏"
    VeneraNavTab.EXPLORE -> "探索"
    VeneraNavTab.SETTINGS -> "设置与偏好"
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
        destination.hasRoute(SearchRoute::class) -> VeneraNavTab.SEARCH
        destination.hasRoute(FavoritesRoute::class) -> VeneraNavTab.FAVORITES
        destination.hasRoute(ExploreRoute::class) -> VeneraNavTab.EXPLORE
        // 旧的「分类索引」路由重定向到合并后的「探索」页，避免深链失效。
        destination.hasRoute(CategoriesRoute::class) -> VeneraNavTab.EXPLORE
        destination.hasRoute(SettingsRoute::class) -> VeneraNavTab.SETTINGS
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
            Scaffold(
                containerColor = Color.Transparent,
                topBar = { if (currentTab != null) TopAppBar(title = titleFor(currentTab!!)) },
                bottomBar = {
                    if (currentTab != null) {
                        val barModifier = Modifier.padding(bottom = 12.dp).padding(horizontal = 16.dp)
                        if (contentLayerBackdrop == null) {
                            VeneraFloatingNavBar(
                                currentTab = currentTab,
                                onTabSelected = { tab ->
                                    haptic()
                                    navController.gotoTab(tab)
                                },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            if (useLiquidGlass && currentTab != null) PaddingValues(
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                top = innerPadding.calculateTopPadding(),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                                bottom = navigationInsets.calculateBottomPadding(),
                            ) else innerPadding
                        )
                        .then(if (contentLayerBackdrop != null) Modifier.layerBackdrop(contentLayerBackdrop) else Modifier)
                        // 主页面之间左右滑动切页；只在 5 个主 tab 上生效，
                        // 详情页/阅读器等子页面不参与（currentTab == null）。
                        .tabSwipePager(
                            enabled = currentTab != null,
                            // 悬浮底栏（64dp）+ 12dp 外边距 + 系统导航栏内边距：
                            // 这一带的手势属于底栏，翻页必须让开。
                            bottomExclusionDp = 64 + 12 + navigationInsets.calculateBottomPadding().value.toInt(),
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
                            bottomContentPadding = if (useLiquidGlass) 88.dp else 8.dp,
                            animatedVisibilityScope = this,
                            onSelect = ::openComic,
                            onOpenHistory = {
                                haptic()
                                navController.navigate(HistoryRoute)
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
                            }
                        )
                    }
                    composable<SearchRoute> {
                        AndroidSearchScreen(animatedVisibilityScope = this, onSelect = ::openComic)
                    }
                    // S8: 标签直达搜索（详情页标签点击跳入，自动执行搜索）
                    composable<TagSearchRoute> { backStackEntry ->
                        val keyword = backStackEntry.arguments?.getString("keyword") ?: ""
                        AndroidSearchScreen(
                            animatedVisibilityScope = this,
                            onSelect = ::openComic,
                            initialQuery = keyword
                        )
                    }
                    composable<FavoritesRoute> {
                        AndroidFavoritesScreen(animatedVisibilityScope = this, onSelect = ::openComic)
                    }
                    composable<HistoryRoute> {
                        AndroidHistoryScreen(
                            onBack = {
                                haptic()
                                navController.popBackStack()
                            },
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
                        AndroidSettingsScreen(
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
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
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
            }
        }
        }
    }
}
