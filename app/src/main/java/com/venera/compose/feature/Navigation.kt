package com.venera.compose.feature

import android.view.HapticFeedbackConstants
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
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
import com.venera.compose.reader.SampleReaderData
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
@Serializable data object FavoritesRoute
@Serializable data object ExploreRoute
@Serializable data object CategoriesRoute
@Serializable data object SettingsRoute
@Serializable data object ComicSourceManageRoute

/** 详情页的入口载荷。S2 会改成「带 sourceKey+comicId，由 ViewModel 拉真详情」，现在先保持行为一致。 */
@Serializable data class DetailRoute(val comicId: String, val sourceName: String)

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
    VeneraNavTab.CATEGORIES -> CategoriesRoute
    VeneraNavTab.SETTINGS -> SettingsRoute
}

private fun titleFor(tab: VeneraNavTab): String = when (tab) {
    VeneraNavTab.HOME -> "Venera"
    VeneraNavTab.SEARCH -> "搜索与发现"
    VeneraNavTab.FAVORITES -> "我的收藏"
    VeneraNavTab.EXPLORE -> "全站探索"
    VeneraNavTab.CATEGORIES -> "分类索引"
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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val currentTab: VeneraNavTab? = when {
        destination == null -> null
        destination.hasRoute(HomeRoute::class) -> VeneraNavTab.HOME
        destination.hasRoute(SearchRoute::class) -> VeneraNavTab.SEARCH
        destination.hasRoute(FavoritesRoute::class) -> VeneraNavTab.FAVORITES
        destination.hasRoute(ExploreRoute::class) -> VeneraNavTab.EXPLORE
        destination.hasRoute(CategoriesRoute::class) -> VeneraNavTab.CATEGORIES
        destination.hasRoute(SettingsRoute::class) -> VeneraNavTab.SETTINGS
        else -> null
    }

    fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    fun openComic(comic: ComicItem) {
        haptic()
        shell.selectedComic = comic
        navController.navigate(DetailRoute(comic.id, comic.sourceName))
    }

    VeneraAmbientBackground {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = { if (currentTab != null) TopAppBar(title = titleFor(currentTab!!)) },
                bottomBar = {
                    if (currentTab != null) {
                        VeneraFloatingNavBar(
                            currentTab = currentTab,
                            onTabSelected = { tab ->
                                haptic()
                                navController.gotoTab(tab)
                            },
                        )
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    composable<HomeRoute> {
                        AndroidHomeScreen(animatedVisibilityScope = this, onSelect = ::openComic)
                    }
                    composable<SearchRoute> {
                        AndroidSearchScreen(animatedVisibilityScope = this, onSelect = ::openComic)
                    }
                    composable<FavoritesRoute> {
                        AndroidFavoritesScreen(animatedVisibilityScope = this, onSelect = ::openComic)
                    }
                    composable<ExploreRoute> {
                        AndroidExploreScreen(animatedVisibilityScope = this, onSelect = ::openComic)
                    }
                    composable<CategoriesRoute> {
                        AndroidCategoriesScreen()
                    }
                    composable<SettingsRoute> {
                        AndroidSettingsScreen(
                            onNavigateToSourceManage = {
                                haptic()
                                navController.navigate(ComicSourceManageRoute)
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
                    composable<DetailRoute> { entry ->
                        val route = entry.toRoute<DetailRoute>()
                        val comic = shell.selectedComic ?: return@composable
                        AndroidComicDetailScreen(
                            comic = comic,
                            animatedVisibilityScope = this,
                            onBack = {
                                haptic()
                                navController.popBackStack()
                            },
                            onStartReading = { chapterIndex, pageIndex ->
                                haptic()
                                shell.pendingSession = SampleReaderData.createSampleSession(
                                    comicId = comic.id,
                                    comicTitle = comic.title,
                                    coverUrl = comic.coverUrl,
                                    chapterNames = comic.chapters,
                                    initialIndex = chapterIndex,
                                    initialPageIndex = pageIndex,
                                )
                                navController.navigate(ReaderRoute)
                            },
                            onStartLiveReading = { session ->
                                haptic()
                                shell.pendingSession = session
                                navController.navigate(ReaderRoute)
                            },
                        )
                    }
                    composable<ReaderRoute> {
                        val session = shell.pendingSession
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
    }
}
