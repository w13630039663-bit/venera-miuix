package com.venera.compose.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.venera.compose.components.venera.VeneraDialog
import com.venera.compose.components.venera.VeneraSlider
import com.venera.compose.components.venera.VeneraSwitch
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.components.venera.veneraGlassCardColors
import com.venera.compose.components.venera.veneraGlassSurface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.ui.tokens.LocalBottomBarClearance

/**
 * 设置页通用组件。
 *
 * 本轮改造目标（A2 / A3.1）：
 *  - 清零所有硬编码：颜色走 VeneraTokens.color，字号走 VeneraTokens.type，
 *    间距走 VeneraTokens.spacing，圆角走 VeneraTokens.shape。
 *  - 组件来源：容器一律 miuix（Card / Text）；**控件一律走 `Venera*` 转发件**
 *    （VeneraSwitch / VeneraSlider / VeneraIconButton / VeneraTextButton），由转发件按
 *    [com.venera.compose.data.prefs.AppearanceStyle] 选后端。
 *    这里原来写的是"保持「容器用 miuix、控件用 M3」的既有边界"—— 2026-09-29 用户改判
 *    「整个项目 UI 完全符合 Miuix 设计规范」，所以控件这一半从此跟风格轴走；
 *    MD3 档仍然拿到 M3 控件（那是"现状"那一档，逐像素不变）。
 *  - 材质轴（[com.venera.compose.data.prefs.SurfaceMaterial]）只决定分组卡要不要玻璃，
 *    由 `veneraGlassSurface` / `veneraGlassCardColors` 那一处决定，本文件不留第二份判断。
 *  - 弹层已从 `AlertDialog` 收进 [com.venera.compose.components.venera.VeneraDialog]。
 *    记一条走过的错路以免重演：miuix 侧最初判成 `OverlayDialog`，实际**接不通** ——
 *    它的 `DialogLayout` 只把状态注册进 `LocalDialogStates`，真正绘制由 miuix `Scaffold` 内的
 *    `MiuixPopupHost()` 负责，而设置页整条链（`VeneraSettingsHost` → `AndroidSettingsScreen`）
 *    没有 miuix Scaffold，接上就得到"点了没反应的假弹窗"。能用的窗口级件叫 `WindowDialog`。
 */

/** 对应原版 SettingsSection：标题在卡片外，行靠留白区分。 */
@Composable
internal fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) SettingsGroupTitle(title)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 圆角取 CardDefaults.CornerRadius（miuix Card 这处本来就用默认值 16dp），
            // 不是 tokens.shape.card —— 玻璃必须裁在同一圈上，否则 SOLID 档会先变一次圆角。
            .veneraGlassSurface(VeneraGlassRole.SETTINGS_GROUP, CardDefaults.CornerRadius),
        colors = veneraGlassCardColors(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = VeneraTokens.spacing.space2),
            content = content,
        )
    }
    Spacer(Modifier.height(VeneraTokens.spacing.space4))
}

/**
 * 只有分组标题、没有卡片容器。
 *
 * 抽出来给"分组里摆的是自绘卡片"的那种分区用（画廊设置的账号区就是 —— 那两张卡本身
 * 已经是 VeneraCard，再套一层 SettingsGroup 的 Card 就是卡里嵌卡）。
 * 标题样式仍由 [SettingsGroup] 走这一处，两份实现必然漂的毛病从这里就不存在。
 */
@Composable
internal fun SettingsGroupTitle(title: String) {
    val tokens = VeneraTokens
    Text(
        text = title,
        fontSize = tokens.type.sectionTitle,
        fontWeight = tokens.type.weightSemibold,
        color = tokens.color.textSecondary,
        modifier = Modifier.padding(
            start = tokens.spacing.rowHorizontal,
            top = tokens.spacing.rowHorizontal,
            bottom = tokens.spacing.space3,
        ),
    )
}

@Composable
private fun RowScope.SettingLabel(title: String, summary: String?, enabled: Boolean = true) {
    val tokens = VeneraTokens
    Column(Modifier.weight(1f)) {
        Text(
            text = title,
            fontSize = tokens.type.itemTitle,
            fontWeight = tokens.type.weightMedium,
            color = if (enabled) tokens.color.textPrimary else tokens.color.textDisabled,
        )
        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(top = tokens.spacing.space1),
            )
        }
    }
}

@Composable
internal fun SettingsAction(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(
                horizontal = tokens.spacing.rowHorizontal,
                vertical = tokens.spacing.rowVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabel(title, summary, enabled)
        if (enabled && onClick != null) {
            Text(
                text = "›",
                fontSize = tokens.type.chevron,
                color = tokens.color.textTertiary,
            )
        }
    }
}

@Composable
internal fun SettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val tokens = VeneraTokens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(
                horizontal = tokens.spacing.rowHorizontal,
                vertical = tokens.spacing.space5,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabel(title, summary, enabled)
        VeneraSwitch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
        )
    }
}

@Composable
internal fun SettingsSelect(
    title: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    summary: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == value }?.second ?: "存的值认不出来：$value"
    SettingsAction(title, listOfNotNull(label, summary).joinToString("\n")) { open = true }
    VeneraDialog(
        show = open,
        onDismissRequest = { open = false },
        title = title,
        confirmText = "取消",
        content = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (key, text) ->
                    SettingsAction(text, if (value == key) "已选择" else null) {
                        onSelected(key)
                        open = false
                    }
                }
            }
        },
    )
}

@Composable
internal fun SettingsSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    steps: Int = 0,
    suffix: String = "",
    summary: String = "",
) {
    val tokens = VeneraTokens
    Column(
        Modifier.padding(
            horizontal = tokens.spacing.rowHorizontal,
            vertical = tokens.spacing.space5,
        )
    ) {
        Text(
            text = "$title · " + (if (value % 1f == 0f) value.toInt().toString() else value.toString()) + suffix,
            fontSize = tokens.type.itemTitle,
        )
        VeneraSlider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
            )
        }
    }
}

/**
 * 设置子页的公共骨架（2026-10-02 重设计：可选 hero 槽 + 页尾名言卡）。
 *
 * ── hero 契约 ──
 *
 * 用户在设置里选过头图（`prefs.settingsHeroPath` 非空且文件还在）时，本页顶部
 * 渲染 [SettingsHeroImage]（首页与全部子页**共用同一张**，换一处全局生效），
 * 大标题退场、页名压图上、副标题由 [heroSubtitle] 给；滚过 hero 后由现款毛玻璃
 * 胶囊顶栏接管，折叠行为与旧版逐像素一致。
 *
 * **没选图 = 本函数与旧版逐像素一致**（大标题顶栏，hero 与名言卡之外的部分零变化）。
 *
 * @param title 折叠后的小标题，也是 hero 在场时压在图上的页名。
 * @param largeTitle 展开态大标题（hero 在场时退场）。
 * @param heroSubtitle hero 在场时压在图上的副标题；默认用 [largeTitle]
 *   （如「外观与主题」），各页可传更具体的功能概述（如「翻页 · 预加载 · 音量键」）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    /** 展开态大标题。全站顶栏都是"短标题折叠 + 长标题展开"两级（见 DownloadScreen /
     *  LocalComicScreen / SettingsHome），缺了它大标题折叠就没有视觉变化。 */
    largeTitle: String = title,
    heroSubtitle: String? = largeTitle,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = VeneraTokens
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // hero 图链：与首页同一份 prefs 键、同一条拷贝落盘链。SettingsPage 自取 prefs
    // 而不是让 8 个调用点各传一遍 —— 子页们对头图只有「显示」没有「管理」的差别
    // （换图 / 清图的入口只在外观设置里）。
    val context = LocalContext.current
    val prefs = remember(context) { VeneraPreferences.getInstance(context) }
    val heroPath by prefs.settingsHeroPath.collectAsState()
    val heroFile = remember(heroPath) { SettingsHeroImageStore.resolve(heroPath) }

    // ── hero 页的滚动契约：与 SettingsHome 同一套（2026-10-02 第五轮）──
    //
    // hero 在场时 behavior 的 nestedScroll 连接不挂（空 largeTitle 会给 miuix 留出
    // 几十 dp 幽灵折叠行程：上滑先被吃一段、回顶下拉又把顶栏拉开），玻璃与小标题
    // 的进度从列表 scrollState 直接推导（48dp 淡满，与其他页口径一致）。
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

    Box(Modifier.fillMaxSize()) {
        // 禁掉 overscroll：stretch 会把 hero 图拉变形（用户 2026-10-02 拍板「直接禁止」）。
        // 下拉的其余消费者（behavior 展开、overscroll）在 hero 页全部退场，见上。
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (heroFile == null) {
                        Modifier.nestedScroll(topBarBehavior.nestedScrollConnection)
                    } else {
                        Modifier
                    }
                )
                .blurBackdropSource(topBarBackdrop)
                .verticalScroll(scrollState),
        ) {
            if (heroFile != null) {
                SettingsHeroImage(
                    file = heroFile,
                    title = title,
                    subtitle = heroSubtitle,
                    totalHeight = statusBarTop + tokens.spacing.settingsHeroHeightSubpage,
                    overlapBottom = tokens.spacing.settingsHeroOverlap,
                )
            }
            Column(
                Modifier
                    .padding(
                        start = tokens.spacing.screenHorizontal,
                        end = tokens.spacing.screenHorizontal,
                        // hero 不在场：内容自顶栏折叠区下方开始（旧几何）；
                        // 在场：第一组卡**叠在图的下段上**（hero 占位已让出 overlap），
                        // 只留呼吸缝 —— 组标题落在图底溶接渐变带里，灰字仍可读。
                        top = if (heroFile != null) tokens.spacing.space3 else statusBarTop + tokens.spacing.topBarFloor,
                        bottom = LocalBottomBarClearance.current,
                    ),
                content = {
                    content()
                    // 页尾名言卡：首页与全部子页同位（参考图布局）。
                    SettingsQuoteCard(prefs)
                },
            )
        }
        }

        // 顶栏浮层：与设置首页 / 其余页面同契约（大标题折叠 + 毛玻璃）。
        // 此前这里是纯 Column 自带标题行，没有状态栏内边距处理，
        // 导致二级页顶栏被系统状态栏压住。
        // hero 在场时大标题退场（页名已压在图上）。
        VeneraTopAppBar(
            title = title,
            largeTitle = if (heroFile != null) "" else largeTitle,
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            scrollProgressOverride = heroScrollProgress,
            navigationIcon = {
                VeneraTopBarPill(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
        )
    }
}

@Composable
internal fun SectionIconBadge(color: Color, content: @Composable () -> Unit) {
    val tokens = VeneraTokens
    Box(
        Modifier
            .size(tokens.spacing.badgeSize)
            .clip(RoundedCornerShape(tokens.shape.badge))
            .background(color.copy(alpha = tokens.current.badgeAlpha)),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
