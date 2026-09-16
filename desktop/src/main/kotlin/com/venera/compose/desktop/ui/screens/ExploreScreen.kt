package com.venera.compose.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.desktop.models.ComicItem
import com.venera.compose.desktop.models.MockData
import com.venera.compose.desktop.ui.components.AsyncNetworkImage
import com.venera.compose.desktop.ui.components.CapsuleTag
import com.venera.compose.desktop.ui.components.StarRating
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ExploreScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelectComic: (ComicItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        "拷贝漫画" to "热门推荐",
        "哔咔漫画" to "周榜",
        "MangaDex" to "最新连载",
        "E-Hentai" to "首页发现"
    )
    var selectedTabIndex by remember { mutableStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部胶囊双列来源/分区 Tab
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabs.indices.toList()) { index ->
                val (source, section) = tabs[index]
                val isSelected = selectedTabIndex == index
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedTabIndex = index }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = source,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = " · ",
                            fontSize = 12.sp,
                            color = if (isSelected) MiuixTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MiuixTheme.colorScheme.onBackgroundVariant
                        )
                        Text(
                            text = section,
                            fontSize = 12.sp,
                            color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                }
            }
        }

        // 探索内容列表
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(MockData.sampleComics, key = { "explore-${it.id}" }) { comic ->
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
                                .size(width = 80.dp, height = 110.dp)
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
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${comic.author} · ${comic.latestChapter}",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
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
}
