package com.venera.compose.feature

import androidx.compose.runtime.Composable
import com.venera.compose.feature.settings.SettingsHome

/** 外部导航契约保持兼容；分类及下级页面由设置中心自己的返回栈承载。 */
@Composable
fun AndroidSettingsScreen(
    onNavigateToSourceManage: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToLocalComics: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToFavoriteImages: () -> Unit = {},
    onNavigateToGuard: () -> Unit = {},
    onNavigateToSync: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {}
) = SettingsHome(
    onNavigateToSourceManage, onNavigateToDownloads, onNavigateToLocalComics,
    onNavigateToStats, onNavigateToFavoriteImages, onNavigateToGuard,
    onNavigateToSync, onNavigateToLogs
)
