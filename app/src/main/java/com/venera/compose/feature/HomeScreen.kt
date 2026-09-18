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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.data.prefs.AppearanceStyle
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraPreviewTheme
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

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
    /** S8：图片收藏分区 → 插画收藏页 */
    onOpenImageFavorites: () -> Unit = {},
    /** S8：漫画源分区 → 源管理页 */
    onOpenSourceManage: () -> Unit = {},
    bottomContentPadding: androidx.compose.ui.unit.Dp = VeneraTokens.spacing.space4,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val extra by viewModel.uiStateExtra.collectAsStateWithLifecycle()
    val historyList = ui.history
    val sourceManager = remember { ComicSourceManager.getInstance(context) }
    val latencyMap by sourceManager.latencyMapFlow.collectAsState()
    val registeredSources by sourceManager.sourcesFlow.collectAsStateWithLifecycle()
    val installedSources by sourceManager.installedMeta.collectAsStateWithLifecycle()
    val sources = remember(registeredSources, installedSources) { sourceManager.searchTargets() }
    var selectedImgFavType by remember { mutableIntStateOf(0) }

    // 进入主页即刷新扩展分区（本地数量/下载任务/图片收藏统计）—— 逻辑未改
    LaunchedEffect(Unit) { viewModel.refreshExtras() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = tokens.spacing.rowHorizontal,
            end = tokens.spacing.rowHorizontal,
            top = tokens.spacing.space4,
            bottom = bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.rowHorizontal),
    ) {
        // ==================== 分区 2：阅读统计 ====================
        if (ui.todayPages > 0 || ui.weekPages > 0) {
            item {
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

        // ==================== 分区 3：历史记录 ====================
        if (historyList.isNotEmpty()) {
            item {
                Column {
                    MiuixSectionHeader(title = "历史记录", onTap = onOpenHistory)
                    Spacer(Modifier.height(tokens.spacing.space2))
                    // 对齐原版：横向滚动的两行网格（crossAxisCount=2）
                    val chunked = remember(historyList) { historyList.chunked(2) }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                    ) {
                        chunked.forEach { row ->
                            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
                                row.forEach { record ->
                                    HistoryCardHome(
                                        record = record,
                                        onClick = { onSelect(record.toComicItem()) },
                                    )
                                }
                            }
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
                        IconButton(onClick = { onOpenSourceManage() }) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "管理源",
                                tint = tokens.color.textSecondary,
                            )
                        }
                        IconButton(onClick = { sourceManager.refreshPings() }) {
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

        // ==================== 分区 6：图片收藏 ====================
        item {
            Column {
                MiuixSectionHeader(title = "图片收藏", onTap = onOpenImageFavorites)
                Spacer(Modifier.height(tokens.spacing.space2))
                VeneraCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenImageFavorites() },
                ) {
                    Column(modifier = Modifier.padding(tokens.spacing.rowHorizontal)) {
                        if (extra.imageFavTotal > 0) {
                            Text(
                                text = "从 " + extra.imageFavComicCount + " 部作品中收藏了 " +
                                    extra.imageFavTotal + " 张图片",
                                fontSize = tokens.type.caption,
                                color = tokens.color.textSecondary,
                            )
                        } else if (extra.localComicCount >= 0) {
                            Text(
                                text = "暂无图片收藏 · 阅读时在菜单里可收藏当前页",
                                fontSize = tokens.type.caption,
                                color = tokens.color.textSecondary,
                            )
                        }
                        if (extra.imageFavTotal > 0) {
                            Spacer(Modifier.height(tokens.spacing.space5))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                            ) {
                                listOf("标签", "画师", "作品").forEachIndexed { idx, typeName ->
                                    val selected = selectedImgFavType == idx
                                    Surface(
                                        shape = RoundedCornerShape(tokens.shape.extraLarge),
                                        color = if (selected) tokens.color.primaryContainer
                                        else tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedImgFavType = idx },
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = tokens.spacing.space3),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = typeName,
                                                fontSize = tokens.type.caption,
                                                fontWeight = if (selected) tokens.type.weightSemibold
                                                else tokens.type.weightRegular,
                                                color = if (selected) tokens.color.primary
                                                else tokens.color.textPrimary,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(tokens.spacing.space6))
                            val statsList = when (selectedImgFavType) {
                                0 -> extra.imageFavTags
                                1 -> extra.imageFavAuthors
                                else -> extra.imageFavComics
                            }
                            if (statsList.isEmpty()) {
                                Text(
                                    text = "该维度暂无统计数据",
                                    fontSize = tokens.type.caption,
                                    color = tokens.color.textSecondary,
                                )
                            } else {
                                val maxCount = statsList.maxOf { it.second }
                                statsList.forEach { (label, count) ->
                                    StatBarRow(
                                        label = label,
                                        count = count,
                                        maxCount = maxCount,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 注意：此处**故意不再**添加底部 Spacer。
        // 底部留白由 Navigation 通过 bottomContentPadding = bottomBarClearance 统一提供，
        // 页面再叠一次就会造成双重留白（这正是 Round 2.5 修复的问题）。
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
