/**
 * S0-3 机械拆分自 MainActivity.kt（代码正文逐行原样搬运，未作改写）。
 */
package com.venera.compose.feature

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidHomeScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit
) {
    val context = LocalContext.current
    // S0-4/S0-5：首页数据统一由 HomeViewModel 从本地库算（原来是 DAO 直连 + 一片写死字面量）
    val viewModel: HomeViewModel = viewModel()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val historyList = ui.history
    val sourceManager = remember { ComicSourceManager.getInstance(context) }
    val latencyMap by sourceManager.latencyMapFlow.collectAsState()
    var selectedStatsType by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 分区 1：今日推荐 (_TodayUpdates)
        item {
            Column {
                MiuixSectionHeader(title = "今日推荐", onTap = { })
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (ui.shelfTop.isEmpty()) {
                        item {
                            Text(
                                text = "书架还是空的 · 收藏漫画或接入源之后这里会有内容",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                    items(ui.shelfTop) { comic ->
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
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(width = 96.dp, height = 136.dp)
                                        .sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "image-${comic.id}"),
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
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "${comic.sourceName} · ${comic.latestChapter}",
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFE53935))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "NEW",
                                                color = Color.White,
                                                fontSize = 9.sp,
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

        // 分区 2：阅读统计 (_MiuixReadingStats)
        item {
            Column {
                MiuixSectionHeader(title = "阅读统计", onTap = { })
                Spacer(modifier = Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = ui.todayPages.toString(), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "今日页数", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        }
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.2f)))
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = ui.weekPages.toString(), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "本周累计", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        }
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.2f)))
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "${ui.streakDays} 天", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "连续打卡", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                        }
                    }
                }
            }
        }

        // 分区 3：历史记录 (_MiuixHistory)
        item {
            Column {
                MiuixSectionHeader(
                    title = if (historyList.isNotEmpty()) "历史记录 (${historyList.size})" else "历史记录",
                    onTap = { }
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (historyList.isNotEmpty()) {
                        items(historyList, key = { "hist-${it.comicId}" }) { record ->
                            val match = record.toComicItem()
                            Card(modifier = Modifier.width(112.dp).clickable { onSelect(match) }) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    AsyncImage(
                                        model = record.coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = record.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(text = "${record.lastChapterTitle} P.${record.lastPageIndex + 1}", fontSize = 11.sp, color = MiuixTheme.colorScheme.primary, maxLines = 1)
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "还没有阅读记录 · 去随便翻两页吧",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 分区 4：漫画源网络状态 (_MiuixComicSources)
        item {
            Column {
                MiuixSectionHeader(
                    title = "漫画源网络状态 (点击重新测速)",
                    onTap = { sourceManager.refreshPings() }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        listOf(
                            Triple("MangaDex (全球开源API)", "manga_dex", "https://mangadex.org"),
                            Triple("拷贝漫画 (国内主流)", "copy_manga", "https://api.copy2000.online"),
                            Triple("包子漫画 (国内免翻)", "baozi", "https://baozimhcn.com")
                        ).forEach { (name, key, _) ->
                            val latency = latencyMap[key]
                            val ok = latency != null && latency > 0
                            val pingText = when {
                                latency == null -> "测速中..."
                                latency > 0 -> "${latency}ms"
                                else -> "连接超时"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    !ok -> Color(0xFFE53935)
                                                    latency!! < 400 -> Color(0xFF4CAF50)
                                                    else -> Color(0xFFFFA000)
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = pingText,
                                        fontSize = 12.sp,
                                        color = if (ok) Color(0xFF4CAF50) else Color(0xFFE53935)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 分区 5：本地漫画 (_MiuixLocal)
        item {
            Column {
                MiuixSectionHeader(title = "本地漫画", onTap = { })
                Spacer(modifier = Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "本地与下载", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "已缓存 0 部作品 · 下载管理器未实现（S6）",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.clickable { }
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

        // 分区 6：图片收藏 (_MiuixImageFavorites)
        item {
            Column {
                MiuixSectionHeader(title = "图片收藏", onTap = { })
                Spacer(modifier = Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "图片收藏与偏好统计尚未接入（S7）",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("标签", "画师", "作品").forEachIndexed { idx, typeName ->
                                val selected = selectedStatsType == idx
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedStatsType = idx }
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = typeName,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // 真实统计：标签/画师来自收藏，作品来自历史已读话数（S7 换 read_stats）
                        val statsList = when (selectedStatsType) {
                            0 -> ui.tagStats
                            1 -> ui.authorStats
                            else -> ui.comicStats
                        }
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
                                    modifier = Modifier.width(90.dp),
                                    maxLines = 1
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
                                            .fillMaxWidth((count / 140f).coerceIn(0.1f, 1f))
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

        // 底部避让悬浮底栏
        item {
            Spacer(modifier = Modifier.height(68.dp))
        }
    }
}

// ==================== 2. 漫画详情页 (ComicPage) 1:1 复刻 ====================
