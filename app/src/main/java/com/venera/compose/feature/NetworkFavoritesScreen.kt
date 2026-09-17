/**
 * S5 网络收藏夹（对齐官方 `pages/favorites/network_favorites_page.dart` + `side_bar.dart`）。
 *
 * 三层导航（手机单栏适配，等价原版侧栏维度）：
 *   源列表 → 某源的文件夹列表（multiFolder）/ 漫画网格（单文件夹）→ 某文件夹的漫画网格。
 * 所有网络调用走 [NetworkFavoritesViewModel] → [com.venera.compose.source.FavoriteData]，
 * 内部已含「未登录拦截 / 登录过期自动重登」(retryZone)。
 */
package com.venera.compose.feature

import com.venera.compose.components.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.source.model.Comic

@Composable
fun AndroidNetworkFavoritesScreen(onSelect: (ComicItem) -> Unit) {
    val vm: NetworkFavoritesViewModel = viewModel()
    val sources by vm.sourcesFlow.collectAsState()

    val selectedKey = vm.selectedSourceKey
    val isMulti = vm.isMultiFolder
    val folders = vm.folders
    val currentFolder = vm.currentFolderId
    val comics = vm.comics
    val isLoading = vm.isLoading
    val isFolderLoading = vm.isFolderLoading
    val error = vm.error

    Column(modifier = Modifier.fillMaxSize()) {
        val level = when {
            selectedKey == null -> 0
            isMulti && currentFolder == null -> 1
            else -> 2
        }
        val title = when {
            selectedKey == null -> "网络收藏"
            isMulti && currentFolder == null -> sources.find { it.key == selectedKey }?.name ?: selectedKey
            else -> folders?.get(currentFolder) ?: "收藏"
        }
        NetworkTopBar(
            level = level,
            title = title,
            onBack = {
                when {
                    selectedKey == null -> {}
                    isMulti && currentFolder != null -> vm.backToFolders()
                    else -> vm.backToSources()
                }
            },
            onRefresh = { vm.refresh() },
        )

        when {
            selectedKey == null -> SourceList(
                sources = sources,
                onItemClick = { vm.selectSource(it) },
            )
            isMulti && currentFolder == null -> FolderList(
                folders = folders,
                isLoading = isFolderLoading,
                error = error,
                canCreate = vm.favoriteData?.addFolder != null,
                canDelete = vm.favoriteData?.deleteFolder != null,
                onOpen = { vm.enterFolder(it) },
                onCreate = { vm.createFolder(it) },
                onDelete = { vm.deleteFolder(it) },
                onRefresh = { vm.loadFolders() },
            )
            else -> NetworkComicGrid(
                comics = comics,
                isLoading = isLoading,
                error = error,
                hasMore = vm.hasMore,
                onSelect = onSelect,
                onLoadMore = { vm.loadMore() },
                onDelete = { comic -> vm.deleteComic(comic.id, currentFolder ?: "", null) { _, _ -> } },
            )
        }
    }
}

@Composable
private fun NetworkTopBar(level: Int, title: String, onBack: () -> Unit, onRefresh: () -> Unit) {
    val displayMode = rememberComicListDisplayMode()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (level > 0) {
            IconBox(Icons.Filled.ArrowBack, "返回") { onBack() }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
        IconBox(Icons.Filled.Refresh, "刷新") { onRefresh() }
    }
}

@Composable
private fun IconBox(imageVector: ImageVector, desc: String, onClick: () -> Unit) {
    Icon(
        imageVector = imageVector,
        contentDescription = desc,
        tint = MiuixTheme.colorScheme.onBackground,
        modifier = Modifier
            .size(22.dp)
            .clickable { onClick() },
    )
}

// region ---- 源列表 ----

@Composable
private fun SourceList(
    sources: List<NetSourceUi>,
    onItemClick: (String) -> Unit,
) {
    if (sources.isEmpty()) {
        EmptyHint("暂无支持网络收藏的源", "在「源管理」中登录账号后，这里会显示对应的网络收藏夹")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(sources, key = { it.key }) { src ->
            SourceRow(
                name = src.name,
                logged = src.logged,
                onClick = { onItemClick(src.key) },
            )
        }
    }
}

@Composable
private fun SourceRow(name: String, logged: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (logged) Color(0xFF4CAF50) else Color.Gray, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (!logged) {
                Text(
                    text = "未登录",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// endregion

// region ---- 文件夹列表 ----

@Composable
private fun FolderList(
    folders: Map<String, String>?,
    isLoading: Boolean,
    error: String?,
    canCreate: Boolean,
    canDelete: Boolean,
    onOpen: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    // 待确认的删除请求：(文件夹 id, 名称)。对齐官方 _FolderTile —— 删除前必须二次确认
    var pendingDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (canCreate) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "新建文件夹",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showCreate = true },
                )
            }
        }

        when {
            isLoading -> LoadingHint()
            error != null -> ErrorHint(error) { onRefresh() }
            folders == null || folders.isEmpty() -> EmptyHint("暂无收藏夹", "点击右上角「新建文件夹」")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(folders.toList(), key = { it.first }) { (id, name) ->
                    FolderRow(
                        name = name,
                        canDelete = canDelete,
                        onOpen = { onOpen(id) },
                        onDelete = { pendingDelete = id to name },
                    )
                }
            }
        }
    }

    if (showCreate) {
        NetInputDialog(
            title = "新建文件夹",
            hint = "文件夹名称",
            confirmText = "创建",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                onCreate(name)
            },
        )
    }

    pendingDelete?.let { (id, name) ->
        NetConfirmDialog(
            title = "删除文件夹",
            message = "确定要删除「$name」吗？",
            confirmText = "删除",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                onDelete(id)
            },
        )
    }
}

@Composable
private fun FolderRow(name: String, canDelete: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (canDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除文件夹",
                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onDelete() },
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// endregion

// region ---- 漫画网格 ----

@Composable
private fun NetworkComicGrid(
    comics: List<Comic>,
    isLoading: Boolean,
    error: String?,
    hasMore: Boolean,
    onSelect: (ComicItem) -> Unit,
    onLoadMore: () -> Unit,
    onDelete: (Comic) -> Unit,
) {
    if (comics.isEmpty() && !isLoading) {
        if (error != null) ErrorHint(error) { onLoadMore() }
        else EmptyHint("暂无收藏", "这个收藏夹里还没有漫画")
        return
    }

    val displayMode = rememberComicListDisplayMode()
    val context = androidx.compose.ui.platform.LocalContext.current
    val metricsCache = remember(context) { com.venera.compose.data.prefs.ComicMetricsCache(context) }
    androidx.compose.runtime.LaunchedEffect(comics) {
        comics.forEach { metricsCache.put(it.sourceKey, it.id, it.rating?.toDouble(), it.likesCount) }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(comicListColumnCount(displayMode.value)),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(comics, key = { it.id }) { comic ->
            NetworkComicCard(
                comic = comic,
                detailed = displayMode.value == "detailed",
                onClick = { onSelect(comic.toComicItem()) },
                onLongClick = { onDelete(comic) },
            )
        }
        if (hasMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        Text("加载中…", fontSize = 13.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                    } else {
                        Text(
                            text = "加载更多",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onLoadMore() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkComicCard(comic: Comic, detailed: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        ComicCardLayout(detailed = detailed, modifier = Modifier.padding(8.dp), cover = {
            AsyncImage(
                model = comic.cover,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        }) {
            Spacer(Modifier.height(6.dp))
            ComicMetrics(comic.rating?.toDouble(), comic.likesCount)
            Text(text = comic.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text(
                text = comic.description,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                maxLines = 1,
            )
        }
    }
}

// endregion

// region ---- 通用：提示 / 对话框 ----

@Composable
private fun EmptyHint(title: String, sub: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(text = "📂", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(text = sub, fontSize = 13.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
        }
    }
}

@Composable
private fun LoadingHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "加载中…", fontSize = 14.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
    }
}

@Composable
private fun ErrorHint(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(text = "⚠️", fontSize = 40.sp)
            Spacer(Modifier.height(10.dp))
            Text(text = message, fontSize = 14.sp, color = MiuixTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "点击重试",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.clickable { onRetry() },
            )
        }
    }
}

@Composable
private fun NetScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    com.venera.compose.components.PredictiveBackOverlay(onDismiss = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MiuixTheme.colorScheme.surface,
            modifier = Modifier
                .padding(24.dp)
                .clickable { },
        ) {
            content()
        }
    }
}

@Composable
private fun NetInputDialog(
    title: String,
    hint: String,
    initialValue: String = "",
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    NetScrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.isBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text = hint, fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                ) { Text("取消") }
                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            onConfirm(text)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(confirmText) }
            }
        }
    }
}

/** 二次确认弹窗（删除等破坏性操作）。对齐官方 ContentDialog 的 Confirm 语义。 */
@Composable
private fun NetConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    NetScrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(text = message, fontSize = 14.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                ) { Text("取消") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                ) { Text(confirmText) }
            }
        }
    }
}

// endregion

private fun Comic.toComicItem() = ComicItem(
    id = id,
    title = title,
    author = subTitle,
    coverUrl = cover,
    tags = tags,
    description = description,
    sourceName = sourceKey,
    updateTime = updateTime,
    rating = rating?.toString().orEmpty(),
    likesCount = likesCount,
)
