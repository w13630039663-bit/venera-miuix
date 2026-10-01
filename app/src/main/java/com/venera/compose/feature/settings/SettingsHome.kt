package com.venera.compose.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.feature.SettingsSubScreen
import com.venera.compose.feature.openSettingsSubScreen
import com.venera.compose.ui.tokens.SettingsBadgeColors
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text
import java.io.File

/**
 * 设置分区：比旧版多一个可选 [subtitle]，显示在标题下方（个性化组的「外观」用它）。
 * [screen] 可空：「阅读历史」不是分区（它走越界交接跳 MainActivity 的图），只是借同一行几何。
 */
private data class SettingsCategory(
    val screen: SettingsSubScreen?,
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val subtitle: String? = null,
)

/**
 * 设置分区清单（2026-10-02 分组重组：8 个分区按语义归 4 组，一个不丢）。
 *
 * 颜色来自 [SettingsBadgeColors] Token，不在页面内联 Color(0xFF…)（A3.1）。
 * listOf 在顶层求值，因此这里读的是编译期常量，不涉及 Compose 运行时。
 */
private val groupContent = listOf(
    SettingsCategory(SettingsSubScreen.EXPLORE, "探索", Icons.Filled.Explore, SettingsBadgeColors.Discovery),
    SettingsCategory(SettingsSubScreen.GALLERY, "画廊", Icons.Filled.Image, SettingsBadgeColors.Gallery),
    SettingsCategory(SettingsSubScreen.BLOCKING, "屏蔽与过滤", Icons.Filled.FilterAlt, SettingsBadgeColors.Moderation),
)
private val groupPersonalization = listOf(
    SettingsCategory(
        SettingsSubScreen.APPEARANCE, "外观", Icons.Filled.ColorLens, SettingsBadgeColors.Theming,
        subtitle = "主题 · 材质 · 主图",
    ),
)
private val groupData = listOf(
    SettingsCategory(SettingsSubScreen.LOCAL_FAVORITES, "本地收藏", Icons.Filled.CollectionsBookmark, SettingsBadgeColors.Library),
)
private val groupSystem = listOf(
    SettingsCategory(SettingsSubScreen.APP, "应用", Icons.Filled.Apps, SettingsBadgeColors.Application),
    SettingsCategory(SettingsSubScreen.NETWORK, "网络", Icons.Filled.Public, SettingsBadgeColors.Connectivity),
)

/**
 * 设置主页（2026-10-02 重设计）。
 *
 * 原先这里挂着一套自研的页面内部栈（`PredictiveBackStack` + `stack`），7 个分区在同一个
 * Activity 内换内容渲染。那带来两个后果：转场是自绘的、观感比系统那套生硬，而且窗口层面
 * 根本没有换页，所以拿不到 blur-behind。现在分区改成跨 Activity
 * （见 [com.venera.compose.SettingsSubActivity]），本页只剩「首页内容 + 外层顶栏」，
 * 因此也不再需要往外透传各叶子页的跳转回调 —— 那些都由子页宿主自己构造。
 *
 * 重设计后（用户拍板：hero 替代大标题区、图源仅相册自选）：
 * - **没选图**：与旧版逐像素一致（大标题「设置与偏好」+ 分组卡）。
 * - **选了图**：hero 全宽铺在顶部（含状态栏背后），标题压图上；大标题退场
 *   （[VeneraTopAppBar.largeTitle] 传空串），滚过 hero 后由现款毛玻璃胶囊顶栏接管，
 *   折叠行为逐像素不变。
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
    val tokens = VeneraTokens

    val heroPath by prefs.settingsHeroPath.collectAsState()
    val heroFile = remember(heroPath) { SettingsHeroImageStore.resolve(heroPath) }

    // ── hero 页的滚动契约（2026-10-02 第五轮重写）──
    //
    // hero 在场时 behavior 的 nestedScroll 连接**不挂**：空 largeTitle 仍占一行字体
    // 度量，miuix 会给 behavior 留出几十 dp 的「幽灵折叠行程」——上滑先被 behavior
    // 在 pre-scroll 里吃掉这段（列表看似没反应），回顶后 onPostScroll 的展开支路
    // 又把顶栏「拉开」（真机实测）。折叠机制整个退场后，玻璃/小标题的进度改由列表
    // scrollState 直接推导：48dp 淡满，与其他页 contentOffset 的口径一致。
    // overscroll 仍禁（CompositionLocalProvider 在内容侧），到顶下拉从此无任何消费者。
    val density = LocalDensity.current
    val heroGlassRangePx = remember(density) { with(density) { 48.dp.toPx() } }
    val scrollState = rememberScrollState()
    val heroScrollProgress = if (heroFile != null) {
        remember(scrollState, heroGlassRangePx) {
            derivedStateOf { (scrollState.value / heroGlassRangePx).coerceIn(0f, 1f) }
        }
    } else {
        null
    }
    val heroScrolledPast by remember(heroFile, heroGlassRangePx) {
        derivedStateOf { heroFile != null && scrollState.value >= heroGlassRangePx }
    }
    SettingsHeroSystemBarIcons(heroPresent = heroFile != null, scrolledPastHero = heroScrolledPast)

    // ⚠️ 临时调试（2026-10-01 状态栏间隔排查）：页面根 Box 顶部画蓝线。
    Box(modifier = Modifier.fillMaxSize()) {
        SettingsHomeContent(
            prefs = prefs,
            heroFile = heroFile,
            onOpen = { screen -> context.openSettingsSubScreen(screen) },
            onOpenHistory = onOpenHistory,
            scrollState = scrollState,
            scrollConnection = if (heroFile != null) null else topBarBehavior.nestedScrollConnection,
            backdrop = topBarBackdrop,
            topPadding = statusBarTop + 104.dp,
            heroHeight = statusBarTop + tokens.spacing.settingsHeroHeight,
        )

        VeneraTopAppBar(
            title = "设置",
            // hero 在场时大标题退场（标题已压在图上）；传空串而不是 null——
            // miuix TopAppBar 的 largeTitle 是 String，空串即不渲染，其余 12 个调用点零影响。
            largeTitle = if (heroFile != null) "" else "设置与偏好",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            scrollProgressOverride = heroScrollProgress,
            navigationIcon = {
                VeneraTopBarPill(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
        )
    }
}

/**
 * 设置首页内容。
 *
 * 布局（2026-10-02 重设计）：
 *  - hero 在场时：头图全宽在最顶（吃掉内容列的横向 padding，见外/内双 Column 结构），
 *    其后是应用身份卡、外链快捷行、四组设置、页尾名言卡。
 *  - hero 不在场时：与旧版一致 —— 顶部留顶栏折叠区（topPadding），直接进分组。
 *  - 页面底部留白取 [VeneraTokens.spacing.bottomBarClearance]，避免最后一项被悬浮导航栏压住。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsHomeContent(
    prefs: VeneraPreferences,
    heroFile: File?,
    onOpen: (SettingsSubScreen) -> Unit,
    onOpenHistory: () -> Unit,
    scrollState: ScrollState,
    scrollConnection: NestedScrollConnection? = null,
    backdrop: LayerBackdrop? = null,
    topPadding: Dp = 0.dp,
    heroHeight: Dp = 0.dp,
) {
    val tokens = VeneraTokens
    // hero 在场时 scrollConnection 传 null：折叠 behavior 不参与（见 SettingsHome 注释）。
    // 禁掉 overscroll：到顶继续下拉会把 hero 图拉伸变形（Android 12+ 的 stretch 效果
    // 恰好作用在图上），设置页宁可没有回弹反馈。用户 2026-10-02 拍板「直接禁止」。
    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
    Column(
        Modifier
            .fillMaxSize()
            .then(if (scrollConnection != null) Modifier.nestedScroll(scrollConnection) else Modifier)
            .blurBackdropSource(backdrop)
            .verticalScroll(scrollState)
    ) {
        if (heroFile != null) {
            SettingsHeroImage(
                file = heroFile,
                title = "设置",
                subtitle = "设置与偏好",
                totalHeight = heroHeight,
                overlapBottom = tokens.spacing.settingsHeroOverlap,
            )
        }
        Column(
            Modifier.padding(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                // hero 不在场时，内容自顶栏折叠区下方开始（旧版几何）；
                // 在场时应用身份卡**叠在图的下段上**（hero 占位已让出 overlap），
                // 这里只留一点点呼吸缝 —— 过渡靠叠图，不靠留白。
                top = if (heroFile != null) tokens.spacing.space3 else topPadding,
                bottom = tokens.spacing.bottomBarClearance,
            )
        ) {
            AppIdentityCard(prefs)
            SettingsQuickLinks()
            SettingsGroup("内容") {
                groupContent.forEach { category ->
                    SettingsEntryRow(category) { onOpen(category.screen!!) }
                }
            }
            SettingsGroup("个性化") {
                groupPersonalization.forEach { category ->
                    SettingsEntryRow(category) { onOpen(category.screen!!) }
                }
            }
            SettingsGroup("数据") {
                groupData.forEach { category ->
                    SettingsEntryRow(category) { onOpen(category.screen!!) }
                }
                // 阅读历史与「本地收藏」同属数据，但语义不同：它不是偏好项，而是
                // 2026-09-23 从主 Tab 降回二级页后保留的直达入口，点下去是**跳回 MainActivity 的图**
                // （走 SettingsEscape.OpenHistory），与分区那种在本 Activity 内换页语义不同。
                SettingsEntryRow(
                    SettingsCategory(
                        null, "阅读历史",
                        Icons.Filled.History, SettingsBadgeColors.Reading,
                    ),
                    onClick = onOpenHistory,
                )
            }
            SettingsGroup("系统") {
                groupSystem.forEach { category ->
                    SettingsEntryRow(category) { onOpen(category.screen!!) }
                }
            }
            SettingsQuoteCard(prefs)
        }
    }
    }
}

/** 设置首页的一行入口：图标徽标 + 标题（+可选副标） + 右尖角。分区与历史入口共用同一几何。 */
@Composable
private fun SettingsEntryRow(
    category: SettingsCategory,
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
        SectionIconBadge(category.color) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = category.color,
                modifier = Modifier.size(tokens.spacing.badgeIconSize),
            )
        }
        Spacer(Modifier.width(tokens.spacing.rowHorizontal))
        Column(Modifier.weight(1f)) {
            Text(
                text = category.title,
                fontSize = tokens.type.itemTitle,
                color = tokens.color.textPrimary,
            )
            if (!category.subtitle.isNullOrBlank()) {
                Text(
                    text = category.subtitle,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(top = tokens.spacing.space1),
                )
            }
        }
        Text(
            text = "›",
            fontSize = tokens.type.chevron,
            color = tokens.color.textTertiary,
        )
    }
}
