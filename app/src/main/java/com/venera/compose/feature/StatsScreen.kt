package com.venera.compose.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.stats.ComicStatItem
import com.venera.compose.stats.DailyTrendItem
import com.venera.compose.stats.MonthlyTagTop
import com.venera.compose.stats.ReadingStatsSummary
import com.venera.compose.stats.TagStatBucket
import com.venera.compose.stats.TagStats
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.ui.tokens.LocalBottomBarClearance
import com.venera.compose.data.api.BusinessPorts

/**
 * 生产级阅读统计看板
 *
 * 核心特性：
 * 1. 真实汇总阅读时长、页数、漫画本数与连续打卡
 * 2. 14 天阅读趋势原生自绘 Canvas 柱状图
 * 3. 常读漫画 Top 10 榜单
 * 4. 题材分布半区：归一化后的占比条 / 本命题材 / 标签云 / 追漫轨迹（点击下钻搜索）
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    /** 点题材 → 带着该桶的**源生原值**去搜索页（不是中文显示名，见 TagStatBucket 的说明）。 */
    onDigTag: (TagStatBucket) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val statsManager = remember { BusinessPorts.of(context).stats }

    var summary by remember { mutableStateOf(ReadingStatsSummary()) }
    var dailyTrends by remember { mutableStateOf<List<DailyTrendItem>>(emptyList()) }
    var topComics by remember { mutableStateOf<List<ComicStatItem>>(emptyList()) }
    /** 题材分布：随时间范围重算，与四格大卡/趋势图分开的两条 effect。 */
    var tagStats by remember { mutableStateOf(TagStats()) }

    /**
     * 首算是否已完成。**不能拿 buckets 空来当「没数据」判据** —— 那会在字典/简繁表还在异步
     * 加载时先闪一句「去读几本漫画吧」，而那句话在用户读过的本子其实够多的情况下是句假话。
     */
    var tagStatsLoaded by remember { mutableStateOf(false) }

    /** 题材统计范围（天）。对齐原版：近 30 天 / 近 1 年两档。 */
    var scopeDays by remember { mutableStateOf(TAG_SCOPE_MONTH) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        summary = statsManager.getSummary()
        dailyTrends = statsManager.getRecent14DaysTrend()
        topComics = statsManager.getTopComics()
        isLoading = false
    }

    // 切范围只重算题材分布：汇总/趋势/榜单与范围无关，不该跟着再拉一遍。
    LaunchedEffect(scopeDays) {
        tagStats = statsManager.getTagStats(scopeDays)
        tagStatsLoaded = true
    }

    // 统一顶栏：大标题折叠 + 毛玻璃（二级子页面同款）
    val tokens = VeneraTokens
    val topBarBehavior = com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop + tokens.spacing.topBarFloor),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MiuixTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topBarBehavior.nestedScrollConnection)
                    .blurBackdropSource(topBarBackdrop),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = statusBarTop + tokens.spacing.topBarFloor,
                    bottom = LocalBottomBarClearance.current,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 1. 核心数据四格大卡
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 总阅读时长
                            val hours = summary.totalSeconds / 3600
                            val mins = (summary.totalSeconds % 3600) / 60
                            val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

                            StatCard(
                                title = "总阅读时长",
                                value = timeStr,
                                subtitle = "今日 ${summary.todaySeconds / 60} 分钟",
                                icon = Icons.Outlined.Schedule,
                                color = Color(0xFF2196F3),
                                modifier = Modifier.weight(1f)
                            )

                            // 翻阅总页数
                            StatCard(
                                title = "累计阅读页数",
                                value = "${summary.totalPages} P",
                                subtitle = "今日 ${summary.todayPages} 页",
                                icon = Icons.Outlined.BarChart,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 读过本数
                            StatCard(
                                title = "读过漫画",
                                value = "${summary.totalComics} 部",
                                subtitle = "书架沉淀",
                                icon = Icons.Outlined.MenuBook,
                                color = Color(0xFF9C27B0),
                                modifier = Modifier.weight(1f)
                            )

                            // 连续打卡
                            StatCard(
                                title = "连续打卡",
                                value = "${summary.streakDays} 天",
                                subtitle = if (summary.streakDays > 0) "保持连胜！" else "去翻两页吧",
                                icon = Icons.Outlined.LocalFireDepartment,
                                color = Color(0xFFFF5722),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. 近 14 天阅读趋势原生图表
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "近 14 天阅读趋势 (每日页数)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            DailyTrendChart(
                                items = dailyTrends,
                                primaryColor = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            )
                        }
                    }
                }

                // 3. 常读漫画 Top 10
                if (topComics.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "常读漫画榜单 Top 10",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                topComics.forEachIndexed { index, comic ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 排名徽章
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    when (index) {
                                                        0 -> Color(0xFFFFD700)
                                                        1 -> Color(0xFFC0C0C0)
                                                        2 -> Color(0xFFCD7F32)
                                                        else -> MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = (index + 1).toString(),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (index < 3) Color.Black else MiuixTheme.colorScheme.onSurface
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = comic.comicTitle,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MiuixTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = comic.sourceName,
                                                fontSize = 10.sp,
                                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${comic.pagesRead} 页",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MiuixTheme.colorScheme.primary
                                            )
                                            val cMins = comic.durationSeconds / 60
                                            Text(
                                                text = "${cMins} 分钟",
                                                fontSize = 10.sp,
                                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    if (index < topComics.size - 1) {
                                        HorizontalDivider(color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. 题材分布半区：范围切换 → 占比条 → 本命题材 + 标签云 → 追漫轨迹
                if (tagStatsLoaded) {
                    if (tagStats.buckets.isEmpty()) {
                        // 有阅读记录但一个题材桶都没归出来 —— 该说明，而不是整块凭空消失。
                        if (summary.totalPages > 0) item { TopicEmptyCard() }
                    } else {
                        item {
                            TopicScopeCard(days = scopeDays, onSelect = { scopeDays = it })
                        }
                        item {
                            TopicShareCard(
                                buckets = tagStats.buckets,
                                taggedPages = tagStats.taggedPages,
                                scopeDays = scopeDays,
                                onDig = onDigTag,
                            )
                        }
                        item {
                            SoulTagCard(
                                buckets = tagStats.buckets,
                                taggedPages = tagStats.taggedPages,
                                scopeDays = scopeDays,
                                onDig = onDigTag,
                            )
                        }
                        item {
                            TopicTimelineCard(monthly = tagStats.monthlyTop)
                        }
                    }
                }
            }
        }

        com.venera.compose.components.venera.VeneraTopAppBar(
            title = "阅读统计",
            largeTitle = "阅读足迹与统计",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
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

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 10.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun DailyTrendChart(
    items: List<DailyTrendItem>,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val maxPages = items.maxOfOrNull { it.pages }?.coerceAtLeast(10) ?: 10

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        items.forEach { item ->
            val ratio = (item.pages.toFloat() / maxPages).coerceIn(0.04f, 1f)

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (item.pages > 0) {
                    Text(
                        text = item.pages.toString(),
                        fontSize = 8.sp,
                        color = primaryColor,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .fillMaxHeight(ratio * 0.8f)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(primaryColor, primaryColor.copy(alpha = 0.4f))
                            )
                        )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.date.substringAfter("-"),
                    fontSize = 9.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
    }
}

// ==================== 题材分布半区 ====================

/** 题材统计的两档范围（天）。 */
private const val TAG_SCOPE_MONTH = 30
private const val TAG_SCOPE_YEAR = 365

/** 占比条展示几名，其余折进「其它」。与原版 `_PreferenceDonut` 的 take(6) 一致。 */
private const val TOPIC_SHARE_ROWS = 6

/** 标签云展示几名。与原版 `take(12)` 一致。 */
private const val TOPIC_CLOUD_SIZE = 12

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

private fun scopeLabel(days: Int): String = if (days > TAG_SCOPE_MONTH) "近 1 年" else "近 30 天"

/** 范围切换。刻意做成裸的一行而不是卡内元素：它作用于下面**所有**题材卡。 */
@Composable
private fun TopicScopeCard(days: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScopeChip("近 30 天", TAG_SCOPE_MONTH, days, onSelect)
        ScopeChip("近 1 年", TAG_SCOPE_YEAR, days, onSelect)
    }
}

@Composable
private fun ScopeChip(label: String, value: Int, current: Int, onSelect: (Int) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val selected = value == current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.primary else colors.surfaceVariant.copy(alpha = 0.6f))
            .clickable(enabled = !selected) { onSelect(value) }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onPrimary else colors.onSurface,
        )
    }
}

/**
 * 题材占比：Top6 + 「其它」，每行一条占比条。
 *
 * 为什么不做环形图（原版的 `_PreferenceDonut`）：一是要在这页从 0 自绘 `drawArc` 与图例
 * 点色序列；二是「第 1 名吃掉多少」用横条比扇形更好读。
 *
 * 分母是**带标签记录的那些页**（[taggedPages]），不是全部阅读页数 —— 标题里必须把分母写出来，
 * 否则读起来像「我读的漫画里有 40% 是某题材」，而实际口径是「可归类的页里占 40%」。
 */
@Composable
private fun TopicShareCard(
    buckets: List<TagStatBucket>,
    taggedPages: Int,
    scopeDays: Int,
    onDig: (TagStatBucket) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val total = taggedPages.coerceAtLeast(1)
    val shown = buckets.take(TOPIC_SHARE_ROWS)
    val otherPages = buckets.drop(TOPIC_SHARE_ROWS).sumOf { it.pages }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            SectionTitle(text = "题材占比（${scopeLabel(scopeDays)} · $taggedPages 页）")
            Spacer(modifier = Modifier.height(10.dp))
            shown.forEach { bucket ->
                ShareRow(
                    label = bucket.display,
                    pages = bucket.pages,
                    share = bucket.pages.toFloat() / total,
                    accent = colors.primary,
                    track = colors.surfaceVariant,
                    onClick = { onDig(bucket) },
                )
            }
            if (otherPages > 0) {
                // 「其它」不是一个可检索的题材，所以不给点击。
                ShareRow(
                    label = "其它 ${buckets.size - shown.size} 项",
                    pages = otherPages,
                    share = otherPages.toFloat() / total,
                    accent = colors.onSurface.copy(alpha = 0.35f),
                    track = colors.surfaceVariant,
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun ShareRow(
    label: String,
    pages: Int,
    share: Float,
    accent: Color,
    track: Color,
    onClick: (() -> Unit)?,
) {
    val colors = MiuixTheme.colorScheme
    var rowModifier: Modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    if (onClick != null) {
        rowModifier = rowModifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(86.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(track.copy(alpha = 0.6f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${(share * 100).roundToInt()}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.width(36.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$pages 页",
            fontSize = 11.sp,
            color = colors.onSurface.copy(alpha = 0.45f),
        )
    }
}

/**
 * 本命题材 + 题材云。
 *
 * 权重公式照抄原版：字号 `12 + 8w`、底色 alpha `0.10 + 0.22w`（w = 本页数 / 第一名页数），
 * 让「读得多」在**同一屏内**可比，而不是靠颜色数量硬堆层级。
 */
@Composable
private fun SoulTagCard(
    buckets: List<TagStatBucket>,
    taggedPages: Int,
    scopeDays: Int,
    onDig: (TagStatBucket) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val top = buckets.first()
    val maxPages = top.pages.coerceAtLeast(1)
    val total = taggedPages.coerceAtLeast(1)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            SectionTitle(text = "本命题材（${scopeLabel(scopeDays)}）")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "#${top.display}",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDig(top) },
            )
            Text(
                text = "${top.pages} 页 · 占可归类阅读的 ${((top.pages.toFloat() / total) * 100).roundToInt()}%",
                fontSize = 12.sp,
                color = colors.onSurface.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                buckets.take(TOPIC_CLOUD_SIZE).forEach { bucket ->
                    val weight = (bucket.pages.toFloat() / maxPages).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.primary.copy(alpha = 0.10f + 0.22f * weight))
                            .clickable { onDig(bucket) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "#${bucket.display}",
                            fontSize = (12f + 8f * weight).sp,
                            color = colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** 追漫轨迹：每月读得最多的两个题材。 */
@Composable
private fun TopicTimelineCard(monthly: List<MonthlyTagTop>) {
    if (monthly.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            SectionTitle(text = "追漫轨迹（每月 Top 题材）")
            Spacer(modifier = Modifier.height(10.dp))
            monthly.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.month.replace('-', '.'),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.width(58.dp),
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.primary),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (entry.tags.isEmpty()) "无题材记录"
                        else entry.tags.joinToString("　") { "${it.first} ${it.second} 页" },
                        fontSize = 13.sp,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 有阅读记录、但一个题材桶都没归出来时的说明卡。
 *
 * 为什么需要：这张卡在过去一直**不显示**（标签从未写入库），现在写入了也可能因为
 * 「源只给分类词」「标签不带 namespace 一律不参与统计」而空着。空着要讲清是哪一条，
 * 否则用户以为功能坏了。
 */
@Composable
private fun TopicEmptyCard() {
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            SectionTitle(text = "题材分布")
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "这段时间还没有可归类的题材。多数源在列表里只给分类词，" +
                    "而不带命名空间（如 female:xxx）的标签按原口径不参与统计。",
                fontSize = 12.sp,
                color = colors.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
