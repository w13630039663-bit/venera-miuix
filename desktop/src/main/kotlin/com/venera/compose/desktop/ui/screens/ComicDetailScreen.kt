package com.venera.compose.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.venera.compose.desktop.ui.components.AsyncNetworkImage
import com.venera.compose.desktop.ui.components.CapsuleTag
import com.venera.compose.desktop.ui.components.StarRating
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTransitionScope.ComicDetailScreen(
    comic: ComicItem,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isReversedOrder by remember { mutableStateOf(false) }
    var isFavorited by remember { mutableStateOf(false) }
    var isLiked by remember { mutableStateOf(false) }

    val displayChapters = remember(isReversedOrder, comic.chapters) {
        if (isReversedOrder) comic.chapters.reversed() else comic.chapters
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = comic.title,
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "←",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 顶部海报与核心信息
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    AsyncNetworkImage(
                        url = comic.coverUrl,
                        modifier = Modifier
                            .size(width = 120.dp, height = 165.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "image-${comic.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(165.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = comic.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "作者: ${comic.author}",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "源: ${comic.sourceName} · ${comic.latestChapter}",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant
                            )
                        }

                        Column {
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

            // 2. 快捷操作栏 (收藏 / 下载 / 分享 / 点赞)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 收藏按钮
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isFavorited = !isFavorited }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(text = if (isFavorited) "❤️" else "🤍", fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isFavorited) "已收藏" else "收藏",
                                fontSize = 11.sp,
                                color = if (isFavorited) Color(0xFFE53935) else MiuixTheme.colorScheme.onSurface
                            )
                        }

                        // 下载缓存
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(text = "⬇️", fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(text = "离线缓存", fontSize = 11.sp)
                        }

                        // 分享
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(text = "🔗", fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(text = "分享作品", fontSize = 11.sp)
                        }

                        // 点赞
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isLiked = !isLiked }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(text = if (isLiked) "👍" else "👌", fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isLiked) "已推荐" else "推荐",
                                fontSize = 11.sp,
                                color = if (isLiked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 3. 开始阅读主按钮
            item {
                Button(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    content = {
                        Text(
                            text = "开始阅读 (第 1 话)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                )
            }

            // 4. 作品简介卡片
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "作品简介",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = comic.description,
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // 5. 章节列表卡片 (带正序/倒序切换)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "章节目录 (${comic.chapters.size} 话)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { isReversedOrder = !isReversedOrder }
                            ) {
                                Text(
                                    text = if (isReversedOrder) "正序 ↑" else "倒序 ↓",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            displayChapters.take(24).forEach { chapter ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { }
                                ) {
                                    Text(
                                        text = chapter,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. 页面缩略图预览
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "页面缩略图预览",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(8) { pageIdx ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 65.dp, height = 95.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "P ${pageIdx + 1}",
                                        fontSize = 12.sp,
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
}
