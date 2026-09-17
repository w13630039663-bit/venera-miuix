/**
 * 主页 —— 对齐原项目 venera-miuix home_page.dart 的 Miuix 六分区结构：
 * 今日推荐 / 阅读统计 / 历史（横向两行网格）/ 漫画源状态（全量源+延迟）/ 本地 / 图片收藏。
 * 数据全部来自真实库（收藏/历史/下载/本地扫描/favorite_images 表），无硬编码。
 */
package com.venera.compose.feature

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.reader.*
import com.venera.compose.data.db.*
import com.venera.compose.data.prefs.*
import com.venera.compose.download.DownloadManager
import com.venera.compose.download.DownloadStatus
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.*

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
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val extra by viewModel.uiStateExtra.collectAsStateWithLifecycle()
    val historyList = ui.history
    val sourceManager = remember { ComicSourceManager.getInstance(context) }
    val latencyMap by sourceManager.latencyMapFlow.collectAsState()
    val sources = remember { sourceManager.searchTargets() }
    var selectedImgFavType by remember { mutableIntStateOf(0) }

    // 进入主页即刷新扩展分区（本地数量/下载任务/图片收藏统计）
    LaunchedEffect(Unit) { viewModel.refreshExtras() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ==================== 分区 1：今日推荐（_TodayUpdates） ====================
        item {
            Column {
                MiuixSectionHeader(title = "今日推荐", onTap = onOpenHistory)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (ui.shelfTop.isEmpty()) {
                        item {
                            Text(
                                text = "收藏漫画后，有更新的作品会出现在这里",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                    items(ui.shelfTop, key = { "shelf-" + it.id }) { comic ->
                        Card(
                            modifier = Modifier
                                .width(264.dp)
                                .height(152.dp)
                                .clickable { onSelect(comic) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = comic.coverUrl,
                                    contentDescription = comic.title,
                                    modifier = Modifier
                                        .size(width = 96.dp, height = 136.dp)
                                        .sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "image-" + comic.id),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = comic.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = listOf(comic.sourceName, comic.latestChapter)
                                                .filter { it.isNotBlank() }
                                                .joinToString(" · "),
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xCCD32F2F))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "NEW",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = comic.updateTime,
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== 分区 2：阅读统计（_MiuixReadingStats） ====================
        if (ui.todayPages > 0 || ui.weekPages > 0) {
            item {
                Column {
                    MiuixSectionHeader(title = "阅读统计", onTap = onOpenStats)
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenStats() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = ui.todayPages.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "今日页数", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                            }
                            Box(modifier = Modifier.width(1.dp).height(26.dp).background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.2f)))
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = ui.weekPages.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "本周累计", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                            }
                            Box(modifier = Modifier.width(1.dp).height(26.dp).background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.2f)))
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = ui.streakDays.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "连续天数", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                            }
                        }
                    }
                }
            }
        }

        // ==================== 分区 3：历史（_MiuixHistory 横向两行网格） ====================
        if (historyList.isNotEmpty()) {
            item {
                Column {
                    MiuixSectionHeader(title = "历史记录", onTap = onOpenHistory)
                    Spacer(modifier = Modifier.height(4.dp))
                    // 对齐原版：横向滚动的两行网格（crossAxisCount=2，卡片宽 124dp，高 420）
                    val chunked = remember(historyList) { historyList.chunked(2) }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chunked.forEach { row ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { record ->
                                    HistoryCardHome(
                                        record = record,
                                        coverKey = "cover-" + record.comicId,
                                        onClick = { onSelect(record.toComicItem()) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== 分区 4：漫画源网络状态（_MiuixComicSources） ====================
        if (sources.isNotEmpty()) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "漫画源",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onOpenSourceManage() }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "管理源", tint = MiuixTheme.colorScheme.onBackgroundVariant)
                        }
                        IconButton(onClick = { sourceManager.refreshPings() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "重新测速", tint = MiuixTheme.colorScheme.onBackgroundVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            sources.forEach { source ->
                                val latency = latencyMap[source.key]
                                SourceStatusRowHome(
                                    name = source.name,
                                    latency = latency,
                                    onRetest = { sourceManager.refreshPings() }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== 分区 5：本地（_MiuixLocal） ====================
        item {
            Column {
                MiuixSectionHeader(title = "本地", onTap = onOpenLocal)
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenLocal() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "本地漫画", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    extra.localComicCount < 0 -> "正在扫描本地书架..."
                                    else -> "共 ${extra.localComicCount} 部本地作品"
                                },
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (extra.downloadingCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = "${extra.downloadingCount} 个下载任务",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            ) {
                                Text(
                                    text = "管理本地 ›",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== 分区 6：图片收藏（_MiuixImageFavorites） ====================
        item {
            Column {
                MiuixSectionHeader(title = "图片收藏", onTap = onOpenImageFavorites)
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenImageFavorites() }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (extra.imageFavTotal > 0) {
                            Text(
                                text = "从 ${extra.imageFavComicCount} 部作品中收藏了 ${extra.imageFavTotal} 张图片",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        } else if (extra.localComicCount >= 0) {
                            Text(
                                text = "暂无图片收藏 · 阅读时在菜单里可收藏当前页",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }
                        if (extra.imageFavTotal > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("标签", "画师", "作品").forEachIndexed { idx, typeName ->
                                    val selected = selectedImgFavType == idx
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedImgFavType = idx }
                                    ) {
                                        Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = typeName,
                                                fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            val statsList = when (selectedImgFavType) {
                                0 -> extra.imageFavTags
                                1 -> extra.imageFavAuthors
                                else -> extra.imageFavComics
                            }
                            if (statsList.isEmpty()) {
                                Text(
                                    text = "该维度暂无统计数据",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant
                                )
                            } else {
                                val maxCount = statsList.maxOf { it.second }
                                statsList.forEach { (label, count) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            modifier = Modifier.width(96.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth((count.toFloat() / maxCount).coerceIn(0.06f, 1f))
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MiuixTheme.colorScheme.primary)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = count.toString(),
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                                            modifier = Modifier.width(30.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 底部避让悬浮底栏
        item {
            Spacer(modifier = Modifier.height(68.dp))
        }
    }
}

/** 历史卡片：对齐原版 _HistoryCard（封面在上弹性填充 + 标题两行 + 副标题一行） */
@Composable
private fun HistoryCardHome(
    record: HistoryRecord,
    coverKey: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
        ) {
            AsyncImage(
                model = record.coverUrl,
                contentDescription = record.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = record.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        Text(
            text = "${record.lastChapterTitle} · P.${record.lastPageIndex + 1}",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 源状态行：对齐原版 _SourceStatusRow（状态点 + 延迟文案，点击重测） */
@Composable
private fun SourceStatusRowHome(
    name: String,
    latency: Long?,
    onRetest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRetest)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        val (dotColor, text) = when {
            latency == null -> Color(0xFF9E9E9E) to "未测速"
            latency > 0 && latency < 400 -> Color(0xFF4CAF50) to "$latency ms"
            latency > 0 -> Color(0xFFFFA000) to "$latency ms"
            else -> Color(0xFFE53935) to "连接失败"
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, fontSize = 12.sp, color = dotColor)
        Spacer(modifier = Modifier.width(12.dp))
    }
}
