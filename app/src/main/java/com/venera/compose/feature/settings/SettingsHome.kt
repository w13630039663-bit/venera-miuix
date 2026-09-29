package com.venera.compose.feature.settings

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
import androidx.compose.runtime.*
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
import com.venera.compose.components.venera.VeneraIconButton
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.feature.SettingsSubScreen
import com.venera.compose.feature.openSettingsSubScreen
import com.venera.compose.ui.tokens.SettingsBadgeColors
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

private data class SettingsCategory(
    val screen: SettingsSubScreen,
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
    SettingsCategory(SettingsSubScreen.EXPLORE, "探索", Icons.Filled.Explore, SettingsBadgeColors.Discovery),
    SettingsCategory(SettingsSubScreen.GALLERY, "画廊", Icons.Filled.Image, SettingsBadgeColors.Gallery),
    SettingsCategory(SettingsSubScreen.BLOCKING, "屏蔽与过滤", Icons.Filled.FilterAlt, SettingsBadgeColors.Moderation),
    SettingsCategory(SettingsSubScreen.READER, "阅读", Icons.Filled.Book, SettingsBadgeColors.Reading),
    SettingsCategory(SettingsSubScreen.APPEARANCE, "外观", Icons.Filled.ColorLens, SettingsBadgeColors.Theming),
    SettingsCategory(SettingsSubScreen.LOCAL_FAVORITES, "本地收藏", Icons.Filled.CollectionsBookmark, SettingsBadgeColors.Library),
    SettingsCategory(SettingsSubScreen.APP, "应用", Icons.Filled.Apps, SettingsBadgeColors.Application),
    SettingsCategory(SettingsSubScreen.NETWORK, "网络", Icons.Filled.Public, SettingsBadgeColors.Connectivity),
)

/**
 * 设置主页。
 *
 * 原先这里挂着一套自研的页面内部栈（`PredictiveBackStack` + `stack`），7 个分区在同一个
 * Activity 内换内容渲染。那带来两个后果：转场是自绘的、观感比系统那套生硬，而且窗口层面
 * 根本没有换页，所以拿不到 blur-behind。现在分区改成跨 Activity
 * （见 [com.venera.compose.SettingsSubActivity]），本页只剩「首页内容 + 外层顶栏」，
 * 因此也不再需要往外透传各叶子页的跳转回调 —— 那些都由子页宿主自己构造。
 */
@Composable
internal fun SettingsHome(
    onBack: () -> Unit,
    /** 跳回 MainActivity 的图看阅读历史（走越界交接，见 [SettingsEscape.OpenHistory]）。 */
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { VeneraPreferences.getInstance(context) }
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsHomeContent(
            prefs = prefs,
            onOpen = { screen -> context.openSettingsSubScreen(screen) },
            onOpenHistory = onOpenHistory,
            scrollConnection = topBarBehavior.nestedScrollConnection,
            backdrop = topBarBackdrop,
            topPadding = statusBarTop + 104.dp,
        )

        com.venera.compose.components.venera.VeneraTopAppBar(
            title = "设置",
            largeTitle = "设置与偏好",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                VeneraIconButton(onClick = onBack) {
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
    onOpen: (SettingsSubScreen) -> Unit,
    onOpenHistory: () -> Unit,
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
                SettingsEntryRow(
                    icon = category.icon,
                    badgeColor = category.color,
                    title = category.title,
                    onClick = { onOpen(category.screen) },
                )
            }
        }
        // 阅读历史单独一组，不混进上面那七个「设置分区」：它不是偏好项，而是
        // 2026-09-23 从主 Tab 降回二级页后保留的直达入口，点下去是**跳回 MainActivity 的图**
        // （走 SettingsEscape.OpenHistory），与分区那种在本 Activity 内换页语义不同。
        SettingsGroup {
            SettingsEntryRow(
                icon = Icons.Filled.History,
                badgeColor = SettingsBadgeColors.Reading,
                title = "阅读历史",
                onClick = onOpenHistory,
            )
        }
    }
}

/** 设置首页的一行入口：图标徽标 + 标题 + 右尖角。分区与历史入口共用同一几何。 */
@Composable
private fun SettingsEntryRow(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = tokens.spacing.rowHorizontal,
                vertical = tokens.spacing.rowVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionIconBadge(badgeColor) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(tokens.spacing.badgeIconSize),
            )
        }
        Spacer(Modifier.width(tokens.spacing.rowHorizontal))
        Text(
            text = title,
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
