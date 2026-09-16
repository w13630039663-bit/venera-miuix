package com.venera.compose.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.FavoritesScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelectComic: (ComicItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFolder by remember { mutableStateOf(MockData.favoriteFolders.first()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 收藏夹分类选择条
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            items(MockData.favoriteFolders) { folder ->
                val isSelected = selectedFolder == folder
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { selectedFolder = folder }
                ) {
                    Text(
                        text = folder,
                        color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // 收藏漫画网格流 (2 列卡片)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(MockData.sampleComics, key = { "fav-${it.id}" }) { comic ->
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
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            AsyncNetworkImage(
                                url = comic.coverUrl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "image-${comic.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                            )

                            if (comic.hasUpdate) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE53935))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "有更新",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = comic.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "${comic.sourceName} · ${comic.latestChapter}",
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
