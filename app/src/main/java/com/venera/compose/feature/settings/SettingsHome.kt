package com.venera.compose.feature.settings

import com.venera.compose.components.PredictiveBackStack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.ui.tokens.SettingsBadgeColors
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

private data class SettingsCategory(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val color: Color,
)

/**
 * 设置分区清单。
 *
 * 颜色来自 [SettingsBadgeColors] Token，不在页面内联 Color(0xFF…)（A3.1）。
 * listOf 在顶层求值，因此这里读的是编译期常量，不涉及 Compose 运行时。
 */
private val categories = listOf(
    SettingsCategory("explore", "探索", Icons.Filled.Explore, SettingsBadgeColors.Discovery),
    SettingsCategory("blocking", "屏蔽与过滤", Icons.Filled.FilterAlt, SettingsBadgeColors.Moderation),
    SettingsCategory("reader", "阅读", Icons.Filled.Book, SettingsBadgeColors.Reading),
    SettingsCategory("appearance", "外观", Icons.Filled.ColorLens, SettingsBadgeColors.Theming),
    SettingsCategory("favorites", "本地收藏", Icons.Filled.CollectionsBookmark, SettingsBadgeColors.Library),
    SettingsCategory("app", "应用", Icons.Filled.Apps, SettingsBadgeColors.Application),
    SettingsCategory("network", "网络", Icons.Filled.Public, SettingsBadgeColors.Connectivity),
)

/**
 * 独立且可保存的页栈，不把分类页变成弹窗。
 *
 * Stage 0~2 信息架构调整：设置从底栏 Tab 收口为顶栏齿轮进入的子页，
 * 因此新增外层返回箭头 [onBack]（pop 回来源主 Tab）；分类页内部返回仍由
 * [PredictiveBackStack] 承载，两层返回互不干扰。
 */
@Composable
internal fun SettingsHome(
    onSources: () -> Unit, onDownloads: () -> Unit, onLocalComics: () -> Unit,
    onStats: () -> Unit, onImages: () -> Unit, onGuard: () -> Unit,
    onSync: () -> Unit, onLogs: () -> Unit, onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember(context) { VeneraPreferences.getInstance(context) }
    var stack by rememberSaveable { mutableStateOf(listOf("home")) }
    fun push(page: String) { if (stack.last() != page) stack = stack + page }
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        PredictiveBackStack(
            entries = stack,
            onBack = { if (stack.size > 1) stack = stack.dropLast(1) },
            modifier = Modifier.fillMaxSize(),
            entryKey = { it },
        ) { route ->
            // A retained/preview page must never pop the current page via a stale callback.
            fun back() { if (stack.last() == route && stack.size > 1) stack = stack.dropLast(1) }
            when {
                route == "home" -> SettingsHomeContent(
                    prefs = prefs,
                    onPush = ::push,
                    scrollConnection = topBarBehavior.nestedScrollConnection,
                    backdrop = topBarBackdrop,
                    topPadding = statusBarTop + 104.dp,
                )
                route == "explore" -> ExploreSettings(::back, onSources, { push("rules/KEYWORD") })
                route == "blocking" -> BlockingSettings(::back, { push("rules/$it") }, onGuard)
                route.startsWith("rules/") -> BlockingRulesSettings(route.substringAfter("/"), ::back)
                route == "reader" -> ReaderSettings(prefs, ::back, onImages, onStats)
                route == "appearance" -> AppearanceSettings(prefs, ::back)
                route == "favorites" -> LocalFavoritesSettings(prefs, ::back)
                route == "app" -> AppSettings(prefs, ::back, onSync, onLogs, onDownloads, onLocalComics)
                route == "network" -> NetworkSettings(prefs, ::back)
            }
        }

        // 外层顶栏：仅设置首页显示（分类子页有自己的返回箭头，避免双重返回入口）。
        if (stack.last() == "home") {
            com.venera.compose.components.venera.VeneraTopAppBar(
                title = "设置",
                largeTitle = "设置与偏好",
                scrollBehavior = topBarBehavior,
                backdrop = topBarBackdrop,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = VeneraTokens.color.textPrimary,
                        )
                    }
                },
            )
        }
    }
}

/**
 * 设置首页内容。
 *
 * 布局（本轮修正点）：
 *  - 分组标题与卡片内条目左右对齐（原实现标题 start=16dp、卡片内条目 14dp，视觉上错位）。
 *  - 行尾指示符「›」字号收敛到 Token（原 22sp 明显过大，压过 16sp 的标题）。
 *  - 页面底部留白取 [VeneraTokens.spacing.bottomBarClearance]，避免最后一项被悬浮导航栏压住。
 */
@Composable
private fun SettingsHomeContent(
    prefs: VeneraPreferences,
    onPush: (String) -> Unit,
    scrollConnection: NestedScrollConnection? = null,
    backdrop: LayerBackdrop? = null,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val tokens = VeneraTokens
    Column(
        Modifier
            .fillMaxSize()
            .then(if (scrollConnection != null) Modifier.nestedScroll(scrollConnection) else Modifier)
            .blurBackdropSource(backdrop)
            .verticalScroll(rememberScrollState())
            .padding(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                top = topPadding,
                bottom = tokens.spacing.bottomBarClearance,
            )
    ) {
        AboutSection()
        SettingsGroup {
            categories.forEach { category ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPush(category.route) }
                        .padding(
                            horizontal = tokens.spacing.rowHorizontal,
                            vertical = tokens.spacing.rowVertical,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionIconBadge(category.color) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = category.color,
                            modifier = Modifier.size(tokens.spacing.badgeIconSize),
                        )
                    }
                    Spacer(Modifier.width(tokens.spacing.rowHorizontal))
                    Text(
                        text = category.title,
                        fontSize = tokens.type.itemTitle,
                        color = tokens.color.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "›",
                        fontSize = tokens.type.chevron,
                        color = tokens.color.textTertiary,
                    )
                }
            }
        }
    }
}
