package com.venera.compose.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.desktop.models.ComicItem
import com.venera.compose.desktop.models.MockData
import com.venera.compose.desktop.ui.components.AsyncNetworkImage
import com.venera.compose.desktop.ui.components.CapsuleTag
import com.venera.compose.desktop.ui.components.StarRating
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTransitionScope.SearchScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelectComic: (ComicItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf("拷贝漫画") }

    val filteredComics = remember(searchQuery) {
        if (searchQuery.isBlank()) MockData.sampleComics
        else MockData.sampleComics.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.author.contains(searchQuery, ignoreCase = true) ||
            it.tags.any { tag -> tag.contains(searchQuery, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. 搜索框与漫画源筛选
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 搜索源选择胶囊条
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("拷贝漫画", "哔咔漫画", "MangaDex", "聚合搜索").forEach { source ->
                        val isSelected = selectedSource == source
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { selectedSource = source }
                        ) {
                            Text(
                                text = source,
                                color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // 搜索输入框模拟卡片
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔍", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "搜索作品、作者、标签..." else searchQuery,
                            fontSize = 14.sp,
                            color = if (searchQuery.isEmpty()) MiuixTheme.colorScheme.onBackgroundVariant else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                text = "✕",
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { searchQuery = "" }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. 搜索历史 Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallTitle(text = "搜索历史")
                    Text(
                        text = "清除",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.clickable { }
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MockData.searchHistory.forEach { keyword ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { searchQuery = keyword }
                        ) {
                            Text(
                                text = keyword,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. 热门探索标签
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTitle(text = "热门标签")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MockData.trendingTags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { searchQuery = tag }
                        ) {
                            Text(
                                text = "# $tag",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. 搜索结果
        item {
            SmallTitle(text = "探索发现 (${filteredComics.size})")
        }

        items(filteredComics, key = { "search-${it.id}" }) { comic ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "card-${comic.id}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp))
                    )
                    .clickable { onSelectComic(comic) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncNetworkImage(
                        url = comic.coverUrl,
                        modifier = Modifier
                            .size(width = 75.dp, height = 100.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "image-${comic.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .clip(RoundedCornerShape(10.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = comic.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${comic.author} · ${comic.sourceName}",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            comic.tags.take(3).forEach { tag ->
                                CapsuleTag(text = tag)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        StarRating(rating = comic.rating)
                    }
                }
            }
        }
    }
}
