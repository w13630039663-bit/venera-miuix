/**
 * 主页 —— 对齐原项目 venera-miuix home_page.dart 的分区结构：
 * 阅读统计 / 历史 / 漫画源状态 / 本地 / 图片收藏。
 * （今日推荐分区已按信息架构调整移除。）
 * 数据全部来自真实库（收藏/历史/下载/本地扫描/favorite_images 表），无硬编码。
 *
 * 本轮（HomeScreen 专项）只改视觉层：
 *  - 所有颜色 / 字号 / 字重 / 间距 / 圆角 / alpha 走 ui/tokens Token。
 *  - 空数据统一使用 VeneraEmptyView，消除大面积空白。
 *  - 统计数字提升视觉等级、说明文字降级、分隔线弱化。
 *  - 历史卡片统一圆角 Token，标题两行 ellipsis，进度用次要文字色。
 *  - 源状态按 Connected / Degraded / Failing / Unknown 四态着色。
 * 数据流、导航回调、ViewModel、分页与业务逻辑均未改动。
 */
package com.venera.compose.feature

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.venera.compose.components.ComicSharedTransition
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.venera.VeneraShimmer
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.comicListColumnCount
import com.venera.compose.components.recommendStripColumns
import com.venera.compose.components.rememberContentWidth
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.feature.pet.PetMascot
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.Comic
import com.venera.compose.data.platform.AppearanceStyle
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraPreviewTheme
import com.venera.compose.ui.tokens.VeneraTokens
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.VeneraIconButton

/** 源连通性四态（UI 语义，不改动 source 数据模型）。 */

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidHomeScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit,
    /** 历史分区标题点击 → 完整历史页 */
    onOpenHistory: () -> Unit = {},
    /** 阅读统计分区点击 → 完整统计分析页 */
    onOpenStats: () -> Unit = {},
    /** 本地分区 → 本地书架页 */
    onOpenLocal: () -> Unit = {},
    /** 漫画源分区 → 源管理页 */
    onOpenSourceManage: () -> Unit = {},
    /** 低频操作收口：设置从外壳顶栏迁入首页顶栏齿轮（页内自治）。 */
    onOpenSettings: () -> Unit = {},
    bottomContentPadding: androidx.compose.ui.unit.Dp = VeneraTokens.spacing.space4,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    // 平台服务：内容守卫那条判定链要它。取一次、往下以形参传，
    // 与 `DesktopUiIsolationTest` ②（UI 里不许取静态口）同一口径。
    val ux = com.venera.compose.components.LocalUxPlatformServices.current
    val viewModel: HomeViewModel = viewModel()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val extra by viewModel.uiStateExtra.collectAsStateWithLifecycle()
    val recommend by viewModel.recommend.collectAsStateWithLifecycle()
    val recommendMode by viewModel.recommendMode.collectAsStateWithLifecycle()
    val historyList = ui.history
    val sourceManager = remember { ComicSourceManager.getInstance(context) }
    // 静默触发一次源更新检测：让可更新角标数据保持实时有效（结果落 availableUpdates 流）
    LaunchedEffect(Unit) {
        runCatching { sourceManager.checkUpdates() }
    }
    val latencyMap by sourceManager.latencyMapFlow.collectAsState()
    val registeredSources by sourceManager.sourcesFlow.collectAsStateWithLifecycle()
    // 可更新源数量（fileName -> 远端版本号）
    val availableUpdates by sourceManager.availableUpdates.collectAsStateWithLifecycle()
    val updateCount = availableUpdates.size
    val installedSources by sourceManager.installedMeta.collectAsStateWithLifecycle()
    val sources = remember(registeredSources, installedSources) { sourceManager.searchTargets() }
    // 进入主页即刷新扩展分区（本地数量 / 下载任务数）—— 逻辑未改
    LaunchedEffect(Unit) { viewModel.refreshExtras() }
    // 启动应用与回到首页（从详情页返回、Tab 切回）自动刷新推荐。
    // 两条路径都要覆盖：① 进程起来时条目生命周期从头同步，会补发 ON_START/ON_RESUME；
    // ② 从详情页返回时首页条目组合重建，addObserver 同样会补发这两个事件。
    // VM 内部按并发锁 + 60s 节流收敛，所以这里重复触发是安全的。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                viewModel.autoRefreshRecommend()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 大标题折叠 + 真实毛玻璃顶栏（页内自治）
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 桌宠开关**直接订阅偏好**：设置页拨完立刻生效，不留一份本地副本（同 VeneraReaderScreen 那条判据）。
    val appearancePrefs = remember(context) { BusinessPorts.of(context).appearancePrefs }
    val petStickerOn by appearancePrefs.homePetSticker.collectAsState()
    // 避让口径与历史/收藏/搜索完全一致（`statusBarTop + 104.dp`）。真机诊断实测这个留白
    // 本身没问题（pad=149dp、顶栏 ho=0 全展开），首页那几轮「重叠」另有原因，见下方 stats 项。

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topBarBehavior.nestedScrollConnection)
                .blurBackdropSource(topBarBackdrop),
            contentPadding = PaddingValues(
                start = tokens.spacing.rowHorizontal,
                end = tokens.spacing.rowHorizontal,
                top = statusBarTop + VeneraTokens.spacing.topBarFloor,
                bottom = bottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.rowHorizontal),
        ) {
        // ==================== 分区 2：阅读统计 ====================
        // 这一项**恒定存在**，只有内容按数据判空。让条件去决定「项存在与否」时，冷启动
        // `ui.todayPages` 还是 0 → 该项不存在，Room 数据到达那一刻它插到列表头部，
        // LazyList 随即把视口锚到下一项（idx 1），第 0 项被画进 contentPadding 留白里 ——
        // 真机表现就是「阅读统计先贴在顶栏上、再自己跳下来」那一闪，以及滑一下才好。
        item(key = "stats") {
            if (ui.todayPages > 0 || ui.weekPages > 0) {
                Column {
                    MiuixSectionHeader(title = "阅读统计", onTap = onOpenStats)
                    Spacer(Modifier.height(tokens.spacing.space2))
                    VeneraCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenStats() },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = tokens.spacing.rowVertical),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatCell(
                                value = ui.todayPages,
                                label = "今日页数",
                                modifier = Modifier.weight(1f),
                            )
                            StatDivider()
                            StatCell(
                                value = ui.weekPages,
                                label = "本周累计",
                                modifier = Modifier.weight(1f),
                            )
                            StatDivider()
                            StatCell(
                                value = ui.streakDays,
                                label = "连续天数",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        // ====== 分区 2.5：可能你感兴趣（最近 30 天读得最多的题材 → 禁漫天堂）======
        // 空态与失败只留一行说明，绝不铺假封面占位；刷新按钮重随机换一批。
        if (recommend.loading || recommend.comics.isNotEmpty() || recommend.note != null) {
            item(key = "recommend") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            MiuixSectionHeader(title = "可能你感兴趣", onTap = onOpenStats)
                        }
                        // 分级切换：正常 = MangaDex/爱看漫/拷贝漫画，R18 = 禁漫天堂/nhentai。
                        // 切档会立刻清空并重拉（见 HomeViewModel.setRecommendMode），所以这里不做防抖。
                        RecommendModeSelector(
                            selected = recommendMode,
                            onSelect = { viewModel.setRecommendMode(it) },
                        )
                        // 换一批 = 重新随机页码与排序再拉一次（页内完成，不跳走）
                        VeneraIconButton(
                            onClick = { viewModel.loadRecommend(force = true) },
                            enabled = !recommend.loading,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "换一批",
                                tint = tokens.color.textSecondary,
                            )
                        }
                    }
                    if (recommend.tags.isNotEmpty()) {
                        Text(
                            text = "按你最近 30 天读得最多的题材：" +
                                recommend.tags.joinToString(" · ") { it.display },
                            fontSize = tokens.type.overline,
                            color = tokens.color.textTertiary,
                        )
                    }
                    if (recommend.sourcesLabel.isNotEmpty()) {
                        Text(
                            text = "来自 ${recommend.sourcesLabel}",
                            fontSize = tokens.type.overline,
                            color = tokens.color.textTertiary,
                        )
                    }
                    Spacer(Modifier.height(tokens.spacing.space2))
                    // 宽屏档不用 Hero 轮播：1280dp 上 hero 会被算成约 1040×220，
                    // 竖版封面横向裁成一坨放大碎片。改排**单行封面卡**，而每一张的宽用现成的
                    // `historyCardWidth`（124dp）倒推列数，不用 `comicListColumnCount` 那把 220dp 的尺 ——
                    // 后者是给整面网格的，一行推荐按它排就变成"四张海报占掉半屏"（2026-10-13 反馈）。
                    // 闸门仍留在 `gridColumns >= 3`：手机档恒为 2 ⇒ 仍走下面的轮播，几何与交互零改动。
                    val (blockWidth, blockWidthModifier) = rememberContentWidth()
                    val gridColumns = comicListColumnCount("brief", blockWidth)
                    val stripColumns = recommendStripColumns(
                        availableWidth = blockWidth,
                        cardWidth = tokens.spacing.historyCardWidth,
                        gap = tokens.spacing.gridGap,
                    )
                    when {
                        // 刷新中（含首拉）：整排灰骨架呼吸，不铺假封面也不留白。
                        // 放在 comics 判断之前 —— 「换一批」期间也要走骨架，不能拿旧批次挡着。
                        // 骨架的张数与真数据那一支同一把尺，否则呼吸完会"整排跳一次"。
                        recommend.loading -> RecommendSkeleton(columns = stripColumns)
                        recommend.comics.isNotEmpty() -> {
                            val openComic: (Comic) -> Unit = { comic ->
                                onSelect(
                                    ComicItem(
                                        id = comic.id,
                                        title = comic.title,
                                        author = comic.subTitle,
                                        coverUrl = comic.cover,
                                        tags = comic.tags,
                                        description = comic.description,
                                        // 一律 sourceKey：与卡片共享元素 key 同一个口径，
                                        // 交显示名会让封面静默不飞（搜索页踩过）。
                                        sourceName = comic.sourceKey,
                                        likesCount = comic.likesCount,
                                    )
                                )
                            }
                            if (gridColumns >= 3) {
                                RecommendGridRow(
                                    ux = ux,
                                    comics = recommend.comics.take(stripColumns),
                                    columns = stripColumns,
                                    onOpen = openComic,
                                    modifier = blockWidthModifier,
                                )
                            } else {
                                RecommendCarousel(
                                    ux = ux,
                                    comics = recommend.comics,
                                    batch = recommend.batch,
                                    onOpen = openComic,
                                )
                            }
                        }
                        else -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                        ) {
                            Text(
                                text = recommend.note.orEmpty(),
                                fontSize = tokens.type.caption,
                                color = tokens.color.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            if (recommend.canRetry) {
                                Text(
                                    text = "再来一次",
                                    fontSize = tokens.type.caption,
                                    fontWeight = tokens.type.weightSemibold,
                                    color = tokens.color.primary,
                                    modifier = Modifier.clickable {
                                        viewModel.loadRecommend(force = true)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== 分区 3：历史记录 ====================
        if (historyList.isNotEmpty()) {
            item {
                Column {
                    MiuixSectionHeader(title = "历史记录", onTap = onOpenHistory)
                    Spacer(Modifier.height(tokens.spacing.space2))
                    // 横向单行，最多 4 张 —— 原先是两行网格（chunked(2)），首屏被历史占太高。
                    val historyStrip = remember(historyList) { historyList.take(4) }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                    ) {
                        historyStrip.forEach { record ->
                            HistoryCardHome(
                                record = record,
                                ux = ux,
                                onClick = { onSelect(record.toComicItem()) },
                            )
                        }
                    }
                }
            }
        }

        // ==================== 分区 4：漫画源网络状态 ====================
        if (sources.isNotEmpty()) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "漫画源",
                            fontSize = tokens.type.itemTitle,
                            fontWeight = tokens.type.weightSemibold,
                            color = tokens.color.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        // 可更新源数量徽章：>0 时显示橙色角标，点击直达源管理
                        Box {
                            VeneraIconButton(onClick = { onOpenSourceManage() }) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = "管理源",
                                    tint = tokens.color.textSecondary,
                                )
                            }
                            if (updateCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = StatusColors.Degraded,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(tokens.spacing.space1)
                                ) {
                                    Text(
                                        text = updateCount.toString(),
                                        fontSize = tokens.type.badge,
                                        fontWeight = tokens.type.weightBold,
                                        color = StatusColors.OnBadgeSurface,
                                        modifier = Modifier.padding(
                                            horizontal = tokens.spacing.badgeHorizontalPadding,
                                            vertical = 0.dp,
                                        )
                                    )
                                }
                            }
                        }
                        VeneraIconButton(onClick = { sourceManager.refreshPings() }) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "重新测速",
                                tint = tokens.color.textSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(tokens.spacing.space2))
                    VeneraCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = tokens.spacing.space6,
                                vertical = tokens.spacing.space3,
                            )
                        ) {
                            sources.forEach { source ->
                                SourceStatusRowHome(
                                    name = source.name,
                                    latency = latencyMap[source.key],
                                    onRetest = { sourceManager.refreshPings() },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== 分区 5：本地 ====================
        item {
            Column {
                MiuixSectionHeader(title = "本地", onTap = onOpenLocal)
                Spacer(Modifier.height(tokens.spacing.space2))
                VeneraCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenLocal() },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = tokens.spacing.rowHorizontal,
                                vertical = tokens.spacing.rowVertical,
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "本地漫画",
                                fontSize = tokens.type.itemTitle,
                                fontWeight = tokens.type.weightSemibold,
                                color = tokens.color.textPrimary,
                            )
                            Spacer(Modifier.height(tokens.spacing.space1))
                            Text(
                                text = when {
                                    extra.localComicCount < 0 -> "正在扫描本地书架..."
                                    else -> "共 " + extra.localComicCount + " 部本地作品"
                                },
                                fontSize = tokens.type.caption,
                                color = tokens.color.textSecondary,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (extra.downloadingCount > 0) {
                                PillButton(
                                    text = extra.downloadingCount.toString() + " 个下载任务",
                                    prominent = false,
                                    modifier = Modifier.padding(end = tokens.spacing.space4),
                                )
                            }
                            PillButton(text = "管理本地 ›", prominent = true)
                        }
                    }
                }
            }
        }

        // 图片收藏已整体并入收藏页（第三个分段），主页不再挂这一区。
        // 注意：此处**故意不再**添加底部 Spacer。
        // 底部留白由 Navigation 通过 bottomContentPadding = bottomBarClearance 统一提供，
        // 页面再叠一次就会造成双重留白（这正是 Round 2.5 修复的问题）。
    }

    VeneraTopAppBar(
        title = "首页",
        largeTitle = "首页",
        scrollBehavior = topBarBehavior,
        backdrop = topBarBackdrop,
        // 设置 → 外观与主题 → 首页顶栏 → 桌宠小肥鱼（默认关）。
        // 关着时这一格根本不组合，不是"画了个隐形的"。
        navigationIcon = { if (petStickerOn) PetMascot() },
        actions = {
            // 源更新角标 + 刷新 + 设置齿轮（低频操作收口）。
            // 三个动作钮走 VeneraTopBarPill（40dp 磨砂圆座 + 48dp 触达），与收藏页那颗同源；
            // 图标本体仍是 miuix 的 MiuixIcon，线宽与顶栏其余 chrome 一致。
            Box {
                VeneraTopBarPill(onClick = { onOpenSourceManage() }) {
                    MiuixIcon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = "源管理",
                        tint = tokens.color.textSecondary,
                    )
                }
                if (updateCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = StatusColors.Degraded,
                        modifier = Modifier.align(Alignment.TopEnd).padding(tokens.spacing.space1),
                    ) {
                        Text(
                            text = updateCount.toString(),
                            fontSize = tokens.type.badge,
                            fontWeight = tokens.type.weightBold,
                            color = StatusColors.OnBadgeSurface,
                            modifier = Modifier.padding(horizontal = tokens.spacing.badgeHorizontalPadding),
                        )
                    }
                }
            }
            VeneraTopBarPill(onClick = { sourceManager.refreshPings() }) {
                MiuixIcon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "刷新",
                    tint = tokens.color.textSecondary,
                )
            }
            VeneraTopBarPill(onClick = { onOpenSettings() }) {
                MiuixIcon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "设置",
                    tint = tokens.color.textSecondary,
                )
            }
        },
        modifier = Modifier.align(Alignment.TopCenter),
    )
}
}

/* ------------------------------------------------------------------ *
 * Compose Preview：LIGHT / DARK × MIUIX / MD3 四种组合
 *
 * 预览数据为纯静态样本，不接入 ViewModel，因此不触发任何数据获取。
 * 使用 Preview 专用参数（静态列表），不改变 AndroidHomeScreen 的签名。
 * ------------------------------------------------------------------ */

/**
 * 预览内容：静态样本，覆盖六个分区的视觉结构。
 *
 * 刻意不复用 AndroidHomeScreen —— 后者依赖 ViewModel / SharedTransitionScope，
 * 在 Preview 里无法构造。这里只重建**视觉骨架**，用于确认 Token 在两套主题下的取值。
 */
@Composable
private fun HomePreviewContent() {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(tokens.spacing.rowHorizontal),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.rowHorizontal),
    ) {
        // 阅读统计
        MiuixSectionHeader(title = "阅读统计", onTap = {})
        Spacer(Modifier.height(tokens.spacing.space2))
        VeneraCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.rowVertical),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatCell(value = 42, label = "今日页数", modifier = Modifier.weight(1f))
                StatDivider()
                StatCell(value = 286, label = "本周累计", modifier = Modifier.weight(1f))
                StatDivider()
                StatCell(value = 12, label = "连续天数", modifier = Modifier.weight(1f))
            }
        }

        // 空状态
        MiuixSectionHeader(title = "历史记录", onTap = {})
        Spacer(Modifier.height(tokens.spacing.space2))
        VeneraEmptyView(
            title = "还没有阅读记录",
            message = "开始阅读后，进度会出现在这里",
        )
    }
}

@Preview(name = "MIUIX · Light", showBackground = true)
@Composable
private fun HomePreviewMiuixLight() {
    VeneraPreviewTheme(appearance = AppearanceStyle.MIUIX, dark = false) { HomePreviewContent() }
}

@Preview(name = "MIUIX · Dark", showBackground = true)
@Composable
private fun HomePreviewMiuixDark() {
    VeneraPreviewTheme(appearance = AppearanceStyle.MIUIX, dark = true) { HomePreviewContent() }
}

@Preview(name = "MD3 · Light", showBackground = true)
@Composable
private fun HomePreviewMd3Light() {
    VeneraPreviewTheme(appearance = AppearanceStyle.MD3, dark = false) { HomePreviewContent() }
}

@Preview(name = "MD3 · Dark", showBackground = true)
@Composable
private fun HomePreviewMd3Dark() {
    VeneraPreviewTheme(appearance = AppearanceStyle.MD3, dark = true) { HomePreviewContent() }
}
