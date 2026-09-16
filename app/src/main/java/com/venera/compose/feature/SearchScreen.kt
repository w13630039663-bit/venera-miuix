package com.venera.compose.feature

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.venera.compose.source.model.*

/**
 * 搜索页（S0-4 已 ViewModel 化）。
 *
 * 原先 query / 选中源 / 结果 / 加载中 / 假历史全部用 remember 存在 Composable 里，
 * 旋转即丢、重组即重发请求；现在状态来自 SearchViewModel.uiState。
 * 这一页也顺手清掉了两处假数据：写死的搜索历史、以及无结果时铺满屏幕的 sampleComics。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTransitionScope.AndroidSearchScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. MIUI 风格药丸搜索栏
        item {
            OutlinedTextField(
                value = ui.query,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = { Text("搜索作品、作者、英文名...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearQuery() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MiuixTheme.colorScheme.primary,
                    unfocusedBorderColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. 漫画源选择胶囊条
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchViewModel.SOURCES) { source ->
                    val isSelected = ui.selectedSource == source
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { viewModel.onSourceSelected(source) }
                    ) {
                        Text(
                            text = source,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 3. 搜索历史（SharedPreferences 持久化，带一键清空）
        if (ui.history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "搜索历史", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { viewModel.clearHistory() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "清空", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.history.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            modifier = Modifier.clickable { viewModel.search(tag) }
                        ) {
                            Text(text = tag, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }

        // 4. 搜索结果
        if (ui.isSearching) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "正在从 ${ui.selectedSource} 检索数据...", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
                    }
                }
            }
        } else if (ui.results.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "检索结果 (共 ${ui.results.size} 条)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(text = "来自 ${ui.selectedSource}", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                }
            }
            items(ui.results) { comic ->
                val displaySourceName = when (comic.sourceKey) {
                    "manga_dex" -> SearchViewModel.SOURCE_MANGA_DEX
                    "copy_manga" -> SearchViewModel.SOURCE_COPY_MANGA
                    "baozi" -> SearchViewModel.SOURCE_BAOZI
                    else -> ui.selectedSource
                }
                val comicItem = ComicItem(
                    id = comic.id,
                    title = comic.title,
                    author = comic.subTitle,
                    coverUrl = comic.cover,
                    tags = comic.tags,
                    rating = "",
                    description = comic.description,
                    sourceName = displaySourceName,
                    latestChapter = "",
                    updateTime = comic.updateTime,
                    hasUpdate = false,
                    chapters = emptyList()
                )
                Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(comicItem) }) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = comicItem.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.size(width = 75.dp, height = 100.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = comicItem.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                            if (comicItem.author.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = "${comicItem.author} · ${displaySourceName}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (comicItem.rating.isNotEmpty()) {
                                    Text(text = "★ ${comicItem.rating}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB800))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(text = comicItem.updateTime.ifEmpty { "—" }, fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                            }
                        }
                    }
                }
            }
        } else {
            // 无结果：给真实提示，不再铺 sampleComics 假数据
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = ui.error ?: "输入至少 2 个字符开始检索",
                            fontSize = 13.sp,
                            color = if (ui.error != null) Color(0xFFE53935) else MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "当前可用源：${SearchViewModel.SOURCES.dropLast(1).joinToString("、")}",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(68.dp))
        }
    }
}
