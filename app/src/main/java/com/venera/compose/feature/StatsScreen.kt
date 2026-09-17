package com.venera.compose.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
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
import com.venera.compose.stats.ComicStatItem
import com.venera.compose.stats.DailyTrendItem
import com.venera.compose.stats.ReadingStatsManager
import com.venera.compose.stats.ReadingStatsSummary
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 生产级阅读统计看板 (S7)
 *
 * 核心特性：
 * 1. 真实汇总阅读时长、页数、漫画本数与连续打卡
 * 2. 14 天阅读趋势原生自绘 Canvas 柱状图
 * 3. 常读漫画 Top 10 榜单
 * 4. 偏好题材/标签热度分布
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val statsManager = remember { ReadingStatsManager.getInstance(context) }

    var summary by remember { mutableStateOf(ReadingStatsSummary()) }
    var dailyTrends by remember { mutableStateOf<List<DailyTrendItem>>(emptyList()) }
    var topComics by remember { mutableStateOf<List<ComicStatItem>>(emptyList()) }
    var topTags by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        summary = statsManager.getSummary()
        dailyTrends = statsManager.getRecent14DaysTrend()
        topComics = statsManager.getTopComics()
        topTags = statsManager.getTopTags()
        isLoading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "阅读足迹与统计",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MiuixTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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

                // 4. 偏好题材/标签热度
                if (topTags.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "题材偏好热度",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    topTags.forEach { (tag, count) ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = tag,
                                                    fontSize = 12.sp,
                                                    color = MiuixTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = count.toString(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MiuixTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
