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
import com.venera.compose.components.rememberContentWidth
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.Comic
import com.venera.compose.data.prefs.AppearanceStyle
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
private enum class SourceHealth { Connected, Degraded, Failing, Unknown }

/** 由延迟值推导健康度：null=未测速，<0=失败，<400ms=健康，其余=偏高。 */
private fun healthOf(latency: Long?): SourceHealth = when {
    latency == null -> SourceHealth.Unknown
    latency < 0 -> SourceHealth.Failing
    latency < 400 -> SourceHealth.Connected
    else -> SourceHealth.Degraded
}

@Composable
private fun SourceHealth.color(): Color = when (this) {
    SourceHealth.Connected -> StatusColors.Healthy
    SourceHealth.Degraded -> StatusColors.Degraded
    SourceHealth.Failing -> StatusColors.Failing
    SourceHealth.Unknown -> StatusColors.Unknown
}

@Composable
private fun SourceHealth.label(latency: Long?): String = when (this) {
    SourceHealth.Connected, SourceHealth.Degraded -> "$latency ms"
    SourceHealth.Failing -> "连接失败"
    SourceHealth.Unknown -> "未测速"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidHomeScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit,
    /** S5-4：历史分区标题点击 → 完整历史页 */
    onOpenHistory: () -> Unit = {},
    /** S7：阅读统计分区点击 → 完整统计分析页 */
    onOpenStats: () -> Unit = {},
    /** S8：本地分区 → 本地书架页 */
    onOpenLocal: () -> Unit = {},
    /** S8：漫画源分区 → 源管理页 */
    onOpenSourceManage: () -> Unit = {},
    /** 低频操作收口：设置从外壳顶栏迁入首页顶栏齿轮（页内自治）。 */
    onOpenSettings: () -> Unit = {},
    bottomContentPadding: androidx.compose.ui.unit.Dp = VeneraTokens.spacing.space4,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val extra by viewModel.uiStateExtra.collectAsStateWithLifecycle()
    val recommend by viewModel.recommend.collectAsStateWithLifecycle()
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
                top = statusBarTop + 104.dp,
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
                    Spacer(Modifier.height(tokens.spacing.space2))
                    when {
                        // 刷新中（含首拉）：整排灰骨架呼吸，不铺假封面也不留白。
                        // 放在 comics 判断之前 —— 「换一批」期间也要走骨架，不能拿旧批次挡着。
                        recommend.loading -> RecommendSkeleton()
                        recommend.comics.isNotEmpty() -> {
                            // 宽屏档不用 Hero 轮播：1280dp 上 hero 会被算成约 1040×220，
                            // 竖版封面横向裁成一坨放大碎片。改按漫画网格同一口径排单行封面卡
                            // （comicListColumnCount：每 220dp 一列 → 1280dp 上 5 列）。
                            // 手机档 columns 恒为 2 → 仍走下面的轮播，几何与交互零改动。
                            val (blockWidth, blockWidthModifier) = rememberContentWidth()
                            val gridColumns = comicListColumnCount("brief", blockWidth)
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
                                    comics = recommend.comics.take(gridColumns),
                                    columns = gridColumns,
                                    onOpen = openComic,
                                    modifier = blockWidthModifier,
                                )
                            } else {
                                RecommendCarousel(
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
                                    text = "重试",
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
 * 局部组件（仅 HomeScreen 使用）
 * ------------------------------------------------------------------ */

/** 统计单元格：数字为主、标签为辅。 */
@Composable
private fun StatCell(value: Int, label: String, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            fontSize = tokens.type.statNumber,
            fontWeight = tokens.type.weightBold,
            color = tokens.color.textPrimary,
        )
        Spacer(Modifier.height(tokens.spacing.space1))
        Text(
            text = label,
            fontSize = tokens.type.caption,
            color = tokens.color.textSecondary,
        )
    }
}

/** 极弱分隔线：仅用于统计区，不引入新的 Card 风格。 */
@Composable
private fun StatDivider() {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .width(tokens.spacing.space1)
            .height(tokens.spacing.statDividerHeight)
            .background(tokens.color.divider.copy(alpha = tokens.current.dividerAlpha))
    )
}

/** 胶囊按钮：prominent = 主操作（管理本地），否则为次要信息胶囊。 */
@Composable
private fun PillButton(text: String, prominent: Boolean, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    Surface(
        shape = RoundedCornerShape(if (prominent) tokens.shape.small else tokens.shape.large),
        color = if (prominent) tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)
        else tokens.color.primaryContainer.copy(alpha = tokens.current.badgeAlpha),
        modifier = modifier,
    ) {
        Text(
            text = text,
            fontSize = tokens.type.caption,
            color = if (prominent) tokens.color.textPrimary else tokens.color.primary,
            fontWeight = if (prominent) tokens.type.weightMedium else tokens.type.weightRegular,
            modifier = Modifier.padding(
                horizontal = if (prominent) tokens.spacing.space6 else tokens.spacing.space5,
                vertical = tokens.spacing.space3,
            ),
        )
    }
}

/** 历史卡片：封面 + 标题两行 ellipsis + 进度（次要文字色）。 */
@Composable
private fun HistoryCardHome(
    record: HistoryRecord,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .width(tokens.spacing.historyCardWidth)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(tokens.spacing.historyCoverHeight)
                .clip(RoundedCornerShape(tokens.shape.medium))
                .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha)),
        ) {
            // 内容守卫：历史记录 sourceName 是显示名，由守卫别名解析后走源级预设判定。
            val guard = com.venera.compose.security.guard.ContentGuardManager.getInstance(LocalContext.current)
            val maskState = guard.coverMaskStateFor(
                sourceKey = record.sourceName,
                title = record.title,
                author = record.author,
                comicId = record.comicId,
            )
            com.venera.compose.components.venera.VeneraCover(
                url = record.coverUrl,
                contentDescription = record.title,
                shimmerWhileLoading = false,
                mask = if (maskState == "VISIBLE") com.venera.compose.components.venera.VeneraCoverMask.Visible
                       else com.venera.compose.components.venera.VeneraCoverMask.Masked,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(tokens.spacing.space3))
        Text(
            text = record.title,
            fontSize = tokens.type.caption,
            fontWeight = tokens.type.weightMedium,
            color = tokens.color.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // 阅读进度：保留原有数据（lastChapterTitle + lastPageIndex），仅降级为次要文字
        Text(
            text = record.lastChapterTitle + " · P." + (record.lastPageIndex + 1),
            fontSize = tokens.type.caption,
            color = tokens.color.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 推荐轮播：material3 的 **MD3 标准「中央 Hero」轮播**（`HorizontalCenteredHeroCarousel`）——
 * 一张主卡在正中，左右各挂一张缩窄的侧卡；主卡从两侧平滑滑入中心，两侧同时反向缩放，
 * 全部由组件自带的 keyline 插值完成（不引第三方库、不做真 3D 变换）。
 *
 * 尺寸口径照 material3 官方 sample：`maxItemWidth` 不指定 → 主卡吃掉「视口 − 两张侧卡」，
 * 侧卡目标宽是主卡的 1/3，再用 min/max 把它压在 64~96dp（约主卡的 0.6 倍，贴近设计稿的
 * 1.5:1 观感）。侧卡宽必须留在视口 1/3 以内，否则求解器会排成「两张大卡」而不是一个 Hero。
 *
 * 交互沿用 MD3 约定：点焦点卡进详情，点侧卡先把焦点滚过去。
 * 标题只给焦点卡（单独摆在轮播下方）—— 卡片宽度边滑边变，文字放在卡里会跟着重排抖动。
 * 「换一批」后随机落一个焦点位，让刷新在视觉上也真的是新一批；首次进入仍从第 0 张看起。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendCarousel(
    comics: List<Comic>,
    batch: Int,
    onOpen: (Comic) -> Unit,
) {
    val tokens = VeneraTokens
    val state = rememberCarouselState { comics.size }
    val animScope = rememberCoroutineScope()
    val initialBatch = remember { batch }
    LaunchedEffect(batch) {
        if (batch == initialBatch || comics.size < 2) return@LaunchedEffect
        state.animateScrollToItem(Random.nextInt(comics.size))
    }
    HorizontalCenteredHeroCarousel(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.spacing.recommendHeroHeight)
            // 排除区挂在 padding **之前** —— 量到的是含两侧 24dp 让位的整行，才盖得住屏幕边缘热区
            .systemGestureExclusionBand()
            // 两侧各让开 24dp：一是 MD3 官方 sample 就是这么摆 Hero 的，二是让横拖的落点
            // 离开系统「边缘返回」热区（边缘热区内的触摸应用收不到，只能靠让位 + 排除区两招）。
            .padding(horizontal = tokens.spacing.space10),
        itemSpacing = tokens.spacing.space4,
        minSmallItemWidth = tokens.spacing.recommendSideMinWidth,
        maxSmallItemWidth = tokens.spacing.recommendSideMaxWidth,
    ) { index ->
        val comic = comics[index]
        val focused = state.currentItem == index
        RecommendCarouselItem(
            comic = comic,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clickable {
                    if (focused) onOpen(comic) else animScope.launch { state.animateScrollToItem(index) }
                }
                .maskClip(RoundedCornerShape(tokens.shape.extraLarge)),
        )
    }
    val focal = comics.getOrNull(state.currentItem)
    if (focal != null) {
        // 标题与作者都居中：Hero 在正中，左对齐的文字会让整块看起来往左倒。
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(tokens.spacing.space3))
            Text(
                text = focal.title,
                fontSize = tokens.type.caption,
                fontWeight = tokens.type.weightMedium,
                color = tokens.color.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (focal.subTitle.isNotBlank()) {
                Text(
                    text = focal.subTitle,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 宽屏档的推荐区形态：按全站漫画网格同一列宽口径排**单行**封面卡。
 *
 * 只做一行 —— 首页首屏厚度是定过的（历史记录那一区正是从两行改成单行才不压首屏），
 * 推荐区没理由更厚，所以宽屏取 columns 张铺满一行就收。
 * 封面比例复用应用内现成的 historyCardWidth : historyCoverHeight（124 : 170），不新造数；
 * 卡片本体仍走 [RecommendCarouselItem]，共享元素飞行与封面遮罩判定因此和轮播完全一致。
 */
@Composable
private fun RecommendGridRow(
    comics: List<Comic>,
    columns: Int,
    onOpen: (Comic) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
    ) {
        comics.forEach { comic ->
            Column(modifier = Modifier.weight(1f)) {
                RecommendCarouselItem(
                    comic = comic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(tokens.spacing.historyCardWidth / tokens.spacing.historyCoverHeight)
                        .clickable { onOpen(comic) }
                        // maskClip 是轮播 CarouselItemScope 的成员扩展，网格这里用不上；
                        // 卡片不滚动变形，普通 clip 足够。
                        .clip(RoundedCornerShape(tokens.shape.extraLarge)),
                )
                Spacer(Modifier.height(tokens.spacing.cardCoverGap))
                Text(
                    text = comic.title,
                    fontSize = tokens.type.caption,
                    fontWeight = tokens.type.weightMedium,
                    color = tokens.color.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (comic.subTitle.isNotBlank()) {
                    Text(
                        text = comic.subTitle,
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // 末批不足一行时用等宽空位补齐，卡片不会被 weight 拉宽
        repeat(columns - comics.size) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * 轮播单张卡：宽高都由轮播的 keyline 给（焦点卡宽、侧卡窄），内容只做封面 + 遮罩圆角。
 * 封面参与「→ 详情页」共享元素飞行；打码命中一律不飞（飞行层会绕开页面裁剪）。
 */
@Composable
private fun RecommendCarouselItem(comic: Comic, modifier: Modifier) {
    val tokens = VeneraTokens
    val guard = com.venera.compose.security.guard.ContentGuardManager.getInstance(LocalContext.current)
    // 直接传 Comic 走守卫 LRU 判定链（源级预设 + 用户规则）
    val maskState = guard.coverMaskStateFor(comic)
    Box(
        modifier = modifier.background(
            tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha)
        ),
    ) {
        com.venera.compose.components.venera.VeneraCover(
            url = comic.cover,
            contentDescription = comic.title,
            shimmerWhileLoading = false,
            mask = if (maskState == "VISIBLE") com.venera.compose.components.venera.VeneraCoverMask.Visible
                   else com.venera.compose.components.venera.VeneraCoverMask.Masked,
            // 槽位宽高都由轮播给，封面不能再自持比例（否则固定高度里会横向留缝）
            preserveAspectRatio = false,
            modifier = Modifier
                .fillMaxSize()
                .coverSharedElement(
                    key = ComicSharedTransition.coverKey(comic.sourceKey, comic.id),
                    allowFly = maskState == "VISIBLE",
                ),
        )
    }
}

/**
 * 刷新中的占位：与轮播同高同圆角的灰块 + 呼吸脉冲。
 * 复用全站那套 [VeneraShimmer]（它本来就只做 alpha 呼吸，不引 shader），不新造动画。
 */
@Composable
private fun RecommendSkeleton() {
    val tokens = VeneraTokens
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
        repeat(3) {
            VeneraShimmer(
                modifier = Modifier
                    .width(tokens.spacing.historyCardWidth)
                    .height(tokens.spacing.recommendHeroHeight),
                shape = RoundedCornerShape(tokens.shape.extraLarge),
            )
        }
    }
}

/**
 * 把这一条横滑区域登记成**系统手势排除区**。
 *
 * 全面屏手势下，屏幕左右边缘的横拖被系统判成「返回」，轮播贴到边缘就永远滑不动 ——
 * 真机症状是「想抽卡却退出了页面」。排除区只是给系统的提示（OEM 可忽略），
 * 且每条边最多 200dp 高，所以只挂在这一行，不是整页。
 */
@Composable
private fun Modifier.systemGestureExclusionBand(): Modifier {
    val view = LocalView.current
    var band by remember { mutableStateOf<android.graphics.Rect?>(null) }
    DisposableEffect(view) {
        onDispose { view.systemGestureExclusionRects = emptyList() }
    }
    LaunchedEffect(band) {
        val rect = band ?: return@LaunchedEffect
        view.systemGestureExclusionRects = listOf(rect)
    }
    return this.onGloballyPositioned { coords ->
        val position = coords.positionInRoot()
        val size = coords.size
        // 量化到 16px：列表滚动时每像素都改排除区，等于给渲染线程挂一条持续重算。
        val top = (position.y / 16f).roundToInt() * 16
        val next = android.graphics.Rect(
            position.x.roundToInt(),
            top,
            (position.x + size.width).roundToInt(),
            top + size.height,
        )
        if (next != band) band = next
    }
}

/** 源状态行：健康度圆点 + 延迟文案，点击重测。 */
@Composable
private fun SourceStatusRowHome(
    name: String,
    latency: Long?,
    onRetest: () -> Unit,
) {
    val tokens = VeneraTokens
    val health = healthOf(latency)
    val healthColor = health.color()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRetest)
            .padding(vertical = tokens.spacing.space5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(tokens.spacing.sourceAvatarSize)
                .clip(CircleShape)
                .background(tokens.color.primary.copy(alpha = tokens.current.badgeAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1),
                fontSize = tokens.type.body,
                fontWeight = tokens.type.weightBold,
                color = tokens.color.primary,
            )
        }
        Spacer(Modifier.width(tokens.spacing.space6))
        Text(
            text = name,
            fontSize = tokens.type.body,
            fontWeight = tokens.type.weightMedium,
            color = tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(tokens.spacing.space4))
        Box(
            modifier = Modifier
                .size(tokens.spacing.statusDotSize)
                .clip(CircleShape)
                .background(healthColor),
        )
        Spacer(Modifier.width(tokens.spacing.space3))
        Text(
            text = health.label(latency),
            fontSize = tokens.type.caption,
            color = healthColor,
        )
        Spacer(Modifier.width(tokens.spacing.space6))
    }
}

/** 统计条：标签 + 比例条 + 数值。 */
@Composable
private fun StatBarRow(label: String, count: Int, maxCount: Int) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = tokens.type.caption,
            color = tokens.color.textPrimary,
            modifier = Modifier.width(tokens.spacing.barLabelWidth),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(tokens.spacing.barHeight)
                .clip(RoundedCornerShape(tokens.shape.extraSmall))
                .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((count.toFloat() / maxCount).coerceIn(0.06f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(tokens.shape.extraSmall))
                    .background(tokens.color.primary),
            )
        }
        Spacer(Modifier.width(tokens.spacing.space5))
        Text(
            text = count.toString(),
            fontSize = tokens.type.overline,
            color = tokens.color.textSecondary,
            modifier = Modifier.width(tokens.spacing.barValueWidth),
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
