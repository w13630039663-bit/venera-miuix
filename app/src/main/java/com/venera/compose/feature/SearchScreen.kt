package com.venera.compose.feature

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.venera.compose.components.ComicLayoutToggleButton
import com.venera.compose.components.ComicMetrics
import com.venera.compose.components.ComicTileDetailed
import com.venera.compose.components.rememberComicListDisplayMode
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.SearchOptionGroup
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTransitionScope.AndroidSearchScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit,
    initialQuery: String = "",
    viewModel: SearchViewModel = viewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val sources by viewModel.sourcesFlow.collectAsStateWithLifecycle()
    val groups by viewModel.searchOptions.collectAsStateWithLifecycle()
    val selected by viewModel.selectedOptions.collectAsStateWithLifecycle()
    var displayMode by rememberComicListDisplayMode()
    var showOptions by remember(ui.selectedSourceKey) { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    val guard = com.venera.compose.security.guard.ContentGuardManager.getInstance(LocalContext.current)
    val nsfwMode by guard.nsfwMaskMode.collectAsStateWithLifecycle()
    fun mask(comic: Comic): String = guard.coverMaskStateFor(comic.title, comic.subTitle, comic.tags, comic.id)
    fun select(comic: Comic, sourceName: String) = onSelect(ComicItem(
        id = comic.id, title = comic.title, author = comic.subTitle, coverUrl = comic.cover,
        sourceName = sourceName, tags = comic.tags, description = comic.description,
        rating = comic.rating?.toString().orEmpty(), likesCount = comic.likesCount
    ))

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showBackToTop by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }
            .collect { showBackToTop = it > 10 }
    }
    // 单源搜索：滑到列表末尾自动加载下一页。
    LaunchedEffect(listState, ui.canLoadMore, ui.results.size) {
        androidx.compose.runtime.snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (ui.canLoadMore && !ui.isSearching && !ui.loadingMore &&
                ui.selectedSourceKey != SearchViewModel.KEY_ALL && total > 0 &&
                lastVisible >= total - 3) {
                viewModel.loadMore()
            }
        }
    }
    LaunchedEffect(ui.selectedSourceKey) { viewModel.loadSearchOptions(ui.selectedSourceKey) }
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) viewModel.search(initialQuery)
    }

    if (showOptions) SearchOptionsDialog(
        sourceName = ui.selectedSourceLabel,
        aggregate = ui.selectedSourceKey == SearchViewModel.KEY_ALL,
        groups = groups, selected = selected, loading = ui.optionsLoading, error = ui.optionsError,
        onRetry = { viewModel.loadSearchOptions(ui.selectedSourceKey) },
        onDismiss = { showOptions = false },
        onApply = { values -> viewModel.applySearchOptions(ui.selectedSourceKey, values); showOptions = false }
    )
    if (showTags) SearchTagDialog(viewModel, onDismiss = { showTags = false })

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = ui.query, onValueChange = viewModel::onQueryChange,
                placeholder = { Text("作品名、作者或漫画链接", fontSize = 13.sp) },
                leadingIcon = { IconButton(onClick = { viewModel.search(ui.query) }) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索")
                } },
                trailingIcon = { if (ui.query.isNotEmpty() || ui.tags.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) { Icon(Icons.Outlined.Close, contentDescription = "清空搜索条件") }
                } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(ui.query) }),
                singleLine = true, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(onClick = { showTags = true }, label = { Text("＋ 添加标签") })
                ui.tags.forEachIndexed { index, tag ->
                    InputChip(selected = true, onClick = { viewModel.removeTag(index) },
                        label = { Text(listOf(tag.namespace, tag.label).filter { it.isNotBlank() }.joinToString(":"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "移除标签", modifier = Modifier.size(16.dp)) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showOptions = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("排序与高级筛选")
                }
                Spacer(Modifier.weight(1f))
                ComicLayoutToggleButton(displayMode = displayMode, onToggle = { displayMode = it })
            }
            Text(
                if (ui.selectedSourceKey == SearchViewModel.KEY_ALL) "全网聚合使用各源默认选项；高级筛选请先选择下方漫画源。"
                else if (ui.optionsLoading) "正在读取源提供的搜索选项…"
                else if (ui.optionsError != null) ui.optionsError!!
                else if (groups.isEmpty()) "该源未声明排序或高级筛选选项。"
                else "已接入源提供的 ${groups.size} 组搜索选项，结果保持源排序。",
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tabs = listOf(SearchViewModel.KEY_ALL to SearchViewModel.SOURCE_ALL_LABEL) + sources.map { it.key to it.name }
                items(tabs, key = { it.first }) { (key, label) ->
                    FilterChip(selected = ui.selectedSourceKey == key,
                        onClick = { viewModel.onSourceSelected(key, label) }, label = { Text(label) })
                }
            }
        }
        ui.matchedUrlComic?.let { matched -> item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("识别到【${matched.sourceName}】漫画链接")
                    TextButton(onClick = { onSelect(ComicItem(id = matched.comicId, title = matched.comicId, author = "", coverUrl = "", sourceName = matched.sourceName)) }) {
                        Text("直达详情")
                    }
                }
            }
        } }
        if (ui.tagSuggestions.isNotEmpty()) item {
            Text("标签联想（点击添加原文标签）", fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.tagSuggestions.forEach { (raw, label) -> AssistChip(
                    onClick = { viewModel.addTag(raw, label) }, label = { Text("$label（$raw）", maxLines = 1) }
                ) }
            }
        }
        if (ui.history.isNotEmpty() && ui.query.isEmpty() && ui.tags.isEmpty()) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("搜索历史", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(onClick = viewModel::clearHistory) { Text("清空历史") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.history.forEach { keyword -> AssistChip(onClick = { viewModel.search(keyword) }, label = { Text(keyword) }) }
            }
        }
        ui.error?.let { error -> item {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { viewModel.search(ui.query) }) { Text("重试搜索") }
        } }
        if (ui.selectedSourceKey == SearchViewModel.KEY_ALL) {
            ui.aggregatedResults.values.forEach { event ->
                // 聚合区每个 item 都必须有跨源唯一的 key：LazyColumn 按源分组后
                // 若行用默认 key，不同源的行互相复用，会出现第一个源的卡片
                // 覆盖后续源内容的错位 bug。
                if (event.isLoading) item(key = "source:${event.sourceKey}:loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                else if (event.error != null || event.comics.isEmpty()) item(key = "source:${event.sourceKey}:empty") { Text(event.error ?: "未找到相关漫画", fontSize = 13.sp) }
                else item(key = "source:${event.sourceKey}:row") {
                    // 每源只展示 5 个，横向滑动浏览——单屏一眼扫过所有源。
                    Column {
                        TextButton(onClick = { viewModel.onSourceSelected(event.sourceKey, event.sourceName) }) {
                            Text("${event.sourceName} · ${event.comics.size} 部 · 进入源搜索", fontSize = 12.sp)
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()) {
                            items(event.comics.take(5), key = { it.id }) { comic ->
                                MiniComicCard(comic, nsfwMode, ::mask) { select(comic, event.sourceName) }
                            }
                        }
                    }
                }
            }
            if (ui.hasSearched && !ui.isSearching && ui.aggregatedResults.isEmpty() && ui.error == null) item { Text("没有可用的搜索源，请先启用漫画源。") }
        } else {
            if (ui.isSearching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (ui.results.isNotEmpty()) {
                item { Text("检索结果（${ui.results.size} 条，源排序）", fontWeight = FontWeight.Bold) }
                items(ui.results.chunked(if (displayMode == "brief") 2 else 1)) { row ->
                    SearchResultRow(row, displayMode, ui.selectedSourceLabel, nsfwMode, ::mask) { select(it, ui.selectedSourceLabel) }
                }
            } else if (ui.hasSearched && !ui.isSearching && ui.error == null) item {
                Text("未找到相关漫画，请调整关键词、标签或源筛选。")
            }
            if (ui.selectedSourceKey != SearchViewModel.KEY_ALL && ui.loadingMore) item(key = "loading-more") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("正在加载更多…", fontSize = 12.sp)
                }
            }
        }
    }
    // 浮置「回到顶部」：下滑超过一屏后出现，单击回顶。
    androidx.compose.animation.AnimatedVisibility(visible = showBackToTop, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 110.dp)) {
        androidx.compose.material3.ExtendedFloatingActionButton(onClick = {
            scope.launch { listState.animateScrollToItem(0) }
        }) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "回到顶部")
            Spacer(Modifier.width(4.dp)); Text("顶部", fontSize = 13.sp)
        }
    }
    }
}

/** 聚合搜索专用迷你卡：固定小尺寸（宽 96dp），在 LazyRow 里横向滑动。 */
@Composable
private fun MiniComicCard(
    comic: Comic, maskMode: String,
    mask: (Comic) -> String, onSelect: (Comic) -> Unit
) {
    val coverMask = remember(comic, maskMode) { mask(comic) }
    Card(modifier = Modifier.width(96.dp).clickable { onSelect(comic) }) {
        Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AsyncImage(model = comic.cover, contentDescription = comic.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(6.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
                    .then(if (coverMask == "BLURRED") Modifier.blur(16.dp) else Modifier))
            Text(comic.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun SearchResultRow(
    comics: List<Comic>, mode: String, sourceName: String, maskMode: String,
    mask: (Comic) -> String, onSelect: (Comic) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        comics.forEach { comic ->
            val coverMask = remember(comic, maskMode) { mask(comic) }
            Box(Modifier.weight(1f)) {
                if (mode == "detailed") ComicTileDetailed(
                    title = comic.title, coverUrl = comic.cover, subtitle = comic.subTitle,
                    description = comic.description, tags = comic.tags, badge = sourceName,
                    rating = comic.rating?.toDouble(), likesCount = comic.likesCount,
                    coverMaskState = coverMask, onClick = { onSelect(comic) }
                ) else Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(comic) }) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AsyncImage(model = comic.cover, contentDescription = comic.title, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(8.dp))
                                .background(MiuixTheme.colorScheme.surfaceVariant)
                                .then(if (coverMask == "BLURRED") Modifier.blur(16.dp) else Modifier))
                        Text(comic.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        if (comic.subTitle.isNotBlank()) Text(comic.subTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                        ComicMetrics(rating = comic.rating?.toDouble(), likesCount = comic.likesCount)
                    }
                }
            }
        }
        if (mode == "brief" && comics.size == 1) Spacer(Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchOptionsDialog(
    sourceName: String, aggregate: Boolean, groups: List<SearchOptionGroup>, selected: List<String?>,
    loading: Boolean, error: String?, onRetry: () -> Unit, onDismiss: () -> Unit, onApply: (List<String?>) -> Unit
) {
    var draft by remember(groups, selected) {
        mutableStateOf(groups.mapIndexed { index, group -> if (index < selected.size) selected[index] else group.defaultKey })
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("$sourceName · 排序与高级筛选") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                aggregate -> Text("全网聚合没有统一筛选。请返回页面选择具体漫画源，再设置该源提供的排序和高级选项。")
                loading -> { CircularProgressIndicator(); Text("正在读取源选项…") }
                error != null -> { Text(error); TextButton(onClick = onRetry) { Text("重新加载") } }
                groups.isEmpty() -> Text("该源未声明搜索筛选选项，不会添加不受支持的筛选条件。")
                else -> {
                    Text("以下选项由漫画源提供；确定后重新搜索，取消不保存。", fontSize = 12.sp)
                    TextButton(onClick = { draft = SearchOptionValues.defaults(groups) }) { Text("恢复源默认值") }
                    groups.forEachIndexed { index, group ->
                        Text(group.label.ifBlank { "选项 ${index + 1}" }, fontWeight = FontWeight.Bold)
                        val value = draft.getOrNull(index)
                        fun setValue(next: String?) { draft = draft.toMutableList().also { it[index] = next } }
                        when (group.type) {
                            "select", "multi-select" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.options.forEach { (key, label) ->
                                    val checked = if (group.type == "multi-select") key in SearchOptionValues.selectedKeys(value) else value == key
                                    FilterChip(selected = checked, onClick = { setValue(SearchOptionValues.toggle(group, value, key)) }, label = { Text(label) })
                                }
                            }
                            "dropdown" -> {
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(onClick = { expanded = true }) { Text(group.options[value] ?: "未选择") }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        DropdownMenuItem(text = { Text("清空选择") }, onClick = { setValue(null); expanded = false })
                                        DropdownMenuItem(text = { Text("恢复源默认值") }, onClick = { setValue(group.defaultKey); expanded = false })
                                        group.options.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { setValue(key); expanded = false }) }
                                    }
                                }
                            }
                            else -> Text("暂不支持此源选项类型：${group.type}，保留源默认值。", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }, confirmButton = {
        if (!aggregate && groups.isNotEmpty()) TextButton(onClick = { onApply(draft) }, enabled = !loading && error == null) { Text("确定并重搜") }
        else TextButton(onClick = onDismiss) { Text("知道了") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchTagDialog(viewModel: SearchViewModel, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var namespace by remember { mutableStateOf("") }
    val suggestions = remember(input) { viewModel.suggestTags(input) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("添加搜索标签") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("中文用于查找和显示；实际提交标签原文，由各源转换查询格式。未声明标签语法的源按普通关键词搜索。", fontSize = 12.sp)
            OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("标签原文或中文联想") }, singleLine = true)
            OutlinedTextField(value = namespace, onValueChange = { namespace = it }, label = { Text("命名空间（可选，如 artist）") }, singleLine = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { (raw, label) -> AssistChip(onClick = {
                    viewModel.addTag(raw, label, namespace); onDismiss()
                }, label = { Text("$label（$raw）") }) }
            }
        }
    }, confirmButton = { TextButton(enabled = input.isNotBlank(), onClick = {
        viewModel.addTag(input, namespace = namespace); onDismiss()
    }) { Text("按原文添加") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
