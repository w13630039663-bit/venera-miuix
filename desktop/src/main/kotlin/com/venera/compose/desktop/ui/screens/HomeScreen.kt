package com.venera.compose.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.desktop.models.ComicItem
import com.venera.compose.desktop.models.MockData
import com.venera.compose.desktop.models.SourceConnectionStatus
import com.venera.compose.desktop.ui.components.AsyncNetworkImage
import com.venera.compose.desktop.ui.components.MiuixSectionHeader
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.HomeScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelectComic: (ComicItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 今日推荐 (横向卡片流 + NEW 角标)
        item {
            Column {
                MiuixSectionHeader(
                    title = "今日推荐",
                    onClick = { }
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(MockData.sampleComics, key = { "today-${it.id}" }) { comic ->
                        Card(
                            modifier = Modifier
                                .width(270.dp)
                                .height(130.dp)
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "card-${comic.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp))
                                )
                                .clickable { onSelectComic(comic) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                AsyncNetworkImage(
                                    url = comic.coverUrl,
                                    modifier = Modifier
                                        .size(width = 86.dp, height = 114.dp)
                                        .sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "image-${comic.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                        .clip(RoundedCornerShape(10.dp))
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
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${comic.sourceName} · ${comic.latestChapter}",
                                            fontSize = 11.sp,
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
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
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

        // 2. 阅读统计摘要卡片
        item {
            Column {
                MiuixSectionHeader(
                    title = "阅读统计",
                    onClick = { }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${MockData.readingStats.todayPages}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "今日页数",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(26.dp)
                                .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.2f))
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${MockData.readingStats.weekPages}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "本周累计",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(26.dp)
                                .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.2f))
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${MockData.readingStats.streakDays} 天",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "连续打卡",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }
                    }
                }
            }
        }

        // 3. 历史记录 (横向两行流)
        item {
            Column {
                MiuixSectionHeader(
                    title = "历史记录",
                    onClick = { }
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(MockData.historyItems, key = { "hist-${it.comic.id}" }) { item ->
                        Card(
                            modifier = Modifier
                                .width(115.dp)
                                .clickable { onSelectComic(item.comic) }
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                AsyncNetworkImage(
                                    url = item.comic.coverUrl,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(145.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.comic.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.lastReadChapter,
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. 漫画源网络监控卡片
        item {
            Column {
                MiuixSectionHeader(
                    title = "漫画源网络状态",
                    onClick = { }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        MockData.comicSources.forEachIndexed { index, source ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = source.name.take(1),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = source.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isOk = source.status == SourceConnectionStatus.OK
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isOk) Color(0xFF4CAF50) else Color(0xFFE53935))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isOk) "网络正常" else (source.message ?: "连接失败"),
                                        fontSize = 11.sp,
                                        color = if (isOk) Color(0xFF4CAF50) else Color(0xFFE53935)
                                    )
                                }
                            }
                            if (index < MockData.comicSources.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp)
                                        .height(0.5.dp)
                                        .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f))
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. 本地漫画与导入卡片
        item {
            Column {
                MiuixSectionHeader(
                    title = "本地漫画",
                    onClick = { }
                )
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
                            Text(text = "本地存储库", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "共 8 部已缓存作品 · 0 个进行中的下载任务",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }
                        Button(
                            onClick = { },
                            content = { Text("管理", fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    }
}
