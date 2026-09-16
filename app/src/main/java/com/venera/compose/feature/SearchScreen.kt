package com.venera.compose.feature

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.venera.compose.data.network.ComicUrlMatcher
import com.venera.compose.source.model.Comic
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 工业级搜索与全网聚合界面 (S4 核心升级)
 *
 * 核心特性：
 * 1. 聚合语义反转：全网流式聚合响应，各源独立骨架屏，先返回先渲染
 * 2. 动态源选项卡：集成所有内置源与已注册 JS 扩展源
 * 3. 漫画链接 URL 自动识别：智能识别外部漫画分享链接并支持一键直达
 * 4. 标签翻译与联想推荐：实时匹配 1MB tags.json 字典
 * 5. 多维度结果排序 (默认 / 标题 / 作者)
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTransitionScope.AndroidSearchScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val allSources by viewModel.sourcesFlow.collectAsStateWithLifecycle()
    val view = LocalView.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ==================== 1. MIUI 风格药丸搜索栏 ====================
        item {
            OutlinedTextField(
                value = ui.query,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = { Text("输入作品名、作者、标签或粘贴漫画链接...", fontSize = 13.sp) },
                leadingIcon = {
                    IconButton(onClick = {
                        if (ui.query.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.search(ui.query)
                        }
                    }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search", tint = MiuixTheme.colorScheme.primary)
                    }
                },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) {
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.clearQuery()
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (ui.query.isNotBlank()) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.search(ui.query)
                    }
                }),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MiuixTheme.colorScheme.primary,
                    unfocusedBorderColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ==================== 2. 漫画链接 URL 识别直达卡片 ====================
        ui.matchedUrlComic?.let { matched ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = "链接", tint = MiuixTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "识别到【${matched.sourceName}】漫画链接",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.primary
                                )
                                Text(
                                    text = "ID: ${matched.comicId}",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSelect(
                                    ComicItem(
                                        id = matched.comicId,
                                        title = matched.comicId,
                                        author = "",
                                        coverUrl = "",
                                        sourceName = matched.sourceName
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)
                        ) {
                            Text("直达详情", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ==================== 3. 标签联想建议 Chips ====================
        if (ui.tagSuggestions.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "💡 标签联想", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ui.tagSuggestions.forEach { (raw, translated) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.search(translated)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = translated, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
                                    if (raw != translated) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "($raw)", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== 4. 漫画源动态选择胶囊条 ====================
        item {
            val tabs = remember(allSources) {
                listOf(SearchViewModel.KEY_ALL to SearchViewModel.SOURCE_ALL_LABEL) +
                        allSources.map { it.key to it.name }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tabs) { (key, label) ->
                    val isSelected = ui.selectedSourceKey == key
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.onSourceSelected(key, label)
                        }
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // ==================== 5. 搜索历史 ====================
        if (ui.history.isNotEmpty() && ui.query.isEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "搜索历史", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.clearHistory()
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "清空", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ui.history.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.onQueryChange(tag)
                                viewModel.search(tag)
                            }
                        ) {
                            Text(text = tag, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }

        // ==================== 6. 搜索结果展示 ====================
        if (ui.selectedSourceKey == SearchViewModel.KEY_ALL) {
            // ----- 全网流式聚合展示 -----
            if (ui.aggregatedResults.isNotEmpty()) {
                item {
                    Text(
                        text = "全网聚合结果",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(ui.aggregatedResults.values.toList(), key = { it.sourceKey }) { event ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        viewModel.onSourceSelected(event.sourceKey, event.sourceName)
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = event.sourceName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                    if (event.comics.isNotEmpty()) {
                                        Text(
                                            text = " (${event.comics.size} 部)",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "进入源搜索", fontSize = 12.sp, color = Color.Gray)
                                    Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            when {
                                event.isLoading -> {
                                    // 骨架屏占位
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(4) {
                                            Box(
                                                modifier = Modifier
                                                    .width(100.dp)
                                                    .height(140.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF222222))
                                            )
                                        }
                                    }
                                }
                                event.error != null || event.comics.isEmpty() -> {
                                    Text(
                                        text = event.error ?: "未找到相关漫画",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                                else -> {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(event.comics) { comic ->
                                            Column(
                                                modifier = Modifier
                                                    .width(104.dp)
                                                    .clickable {
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                        onSelect(
                                                            ComicItem(
                                                                id = comic.id,
                                                                title = comic.title,
                                                                author = comic.subTitle,
                                                                coverUrl = comic.cover,
                                                                sourceName = event.sourceName,
                                                                tags = comic.tags,
                                                                description = comic.description
                                                            )
                                                        )
                                                    }
                                            ) {
                                                AsyncImage(
                                                    model = comic.cover,
                                                    contentDescription = comic.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .width(104.dp)
                                                        .height(144.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF222222))
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = comic.title,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = comic.subTitle,
                                                    fontSize = 10.sp,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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
        } else {
            // ----- 单源搜索结果列表 -----
            if (ui.isSearching) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MiuixTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "正在检索 ${ui.selectedSourceLabel}...", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
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
                        Text(
                            text = "检索结果 (共 ${ui.results.size} 条)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SearchSortBy.values().forEach { sort ->
                                val isSelected = ui.sortBy == sort
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MiuixTheme.colorScheme.primary else Color.DarkGray.copy(alpha = 0.3f),
                                    modifier = Modifier.clickable { viewModel.setSortBy(sort) }
                                ) {
                                    Text(
                                        text = sort.label,
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                items(ui.results, key = { it.id }) { comic ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSelect(
                                    ComicItem(
                                        id = comic.id,
                                        title = comic.title,
                                        author = comic.subTitle,
                                        coverUrl = comic.cover,
                                        sourceName = ui.selectedSourceLabel,
                                        tags = comic.tags,
                                        description = comic.description
                                    )
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            AsyncImage(
                                model = comic.cover,
                                contentDescription = comic.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(88.dp)
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF222222))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = comic.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = comic.subTitle,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (comic.tags.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        maxItemsInEachRow = 3
                                    ) {
                                        comic.tags.take(3).forEach { tag ->
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            ) {
                                                Text(
                                                    text = tag,
                                                    fontSize = 10.sp,
                                                    color = MiuixTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ui.selectedSourceLabel,
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(text = "查看详情 ›", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            } else if (ui.query.length >= 2 && !ui.isSearching) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "未找到相关漫画", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "请尝试更换关键字或切换至【全网聚合】检索", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
