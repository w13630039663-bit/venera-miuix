package com.venera.compose.desktop.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.desktop.models.ComicItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class NavTab(val title: String, val icon: String) {
    HOME("首页", "🏠"),
    SEARCH("搜索", "🔍"),
    FAVORITES("收藏", "⭐"),
    EXPLORE("探索", "🧭"),
    CATEGORIES("分类", "🏷️"),
    SETTINGS("设置", "⚙️")
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(NavTab.HOME) }
    var selectedComic by remember { mutableStateOf<ComicItem?>(null) }

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedComic,
            transitionSpec = {
                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            },
            label = "ComicTransition"
        ) { targetComic ->
            if (targetComic == null) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = when (currentTab) {
                                NavTab.HOME -> "Venera"
                                NavTab.SEARCH -> "搜索与发现"
                                NavTab.FAVORITES -> "我的收藏"
                                NavTab.EXPLORE -> "全站探索"
                                NavTab.CATEGORIES -> "分类索引"
                                NavTab.SETTINGS -> "设置与关于"
                            }
                        )
                    },
                    bottomBar = {
                        MiuixBottomBar(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it }
                        )
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        when (currentTab) {
                            NavTab.HOME -> HomeScreen(
                                animatedVisibilityScope = this@AnimatedContent,
                                onSelectComic = { selectedComic = it }
                            )
                            NavTab.SEARCH -> SearchScreen(
                                animatedVisibilityScope = this@AnimatedContent,
                                onSelectComic = { selectedComic = it }
                            )
                            NavTab.FAVORITES -> FavoritesScreen(
                                animatedVisibilityScope = this@AnimatedContent,
                                onSelectComic = { selectedComic = it }
                            )
                            NavTab.EXPLORE -> ExploreScreen(
                                animatedVisibilityScope = this@AnimatedContent,
                                onSelectComic = { selectedComic = it }
                            )
                            NavTab.CATEGORIES -> CategoriesScreen()
                            NavTab.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            } else {
                ComicDetailScreen(
                    comic = targetComic,
                    animatedVisibilityScope = this@AnimatedContent,
                    onBack = { selectedComic = null }
                )
            }
        }
    }
}

@Composable
fun MiuixBottomBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MiuixTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tab.icon,
                        fontSize = if (isSelected) 18.sp else 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }
        }
    }
}
