/**
 * S5-3 收藏页（对齐原版 `pages/favorites/side_bar.dart` + `local_favorites_page.dart`）。
 *
 * 原版是「宽屏双栏（侧栏 + 内容）/ 窄屏抽屉」；本项目是手机单栏，
 * 因此把收藏夹选择做成顶部横向 chips（行为等价：切换当前收藏夹 + 显示计数），
 * 文件夹动作收纳进右上角菜单。
 */
package com.venera.compose.feature

import com.venera.compose.components.*

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidFavoritesScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit,
) {
    val vm: FavoritesViewModel = viewModel()
    val displayMode = rememberComicListDisplayMode()
    val folders by vm.folders.collectAsState()
    val counts by vm.counts.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    var searchMode by remember { mutableStateOf(false) }
    // 网络收藏置于首位并作为默认入口
    var mode by remember { mutableStateOf(FavoritesMode.Network) }
    val selectionBack = com.venera.compose.components.rememberPredictiveBackState(
        enabled = mode == FavoritesMode.Local && vm.multiSelectMode && !showMenu && dialog == null,
    ) { vm.exitMultiSelect() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            FavoritesModeToggle(
                mode = mode,
                onModeChange = { mode = it },
            )

            if (mode == FavoritesMode.Local) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
                }
                FolderChipRow(
                    folders = folders,
                    counts = counts,
                    current = vm.currentFolder,
                    onSelectFolder = { vm.selectFolder(it) },
                    onSearchClick = { searchMode = !searchMode },
                    onMenuClick = { showMenu = true },
                )

                if (searchMode) {
                    SearchField(
                        keyword = vm.keyword,
                        onKeywordChange = { vm.updateKeyword(it) },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }

                FavoriteGrid(vm = vm, onSelect = onSelect)
            } else {
                AndroidNetworkFavoritesScreen(onSelect = onSelect)
            }
        }

        if (mode == FavoritesMode.Local && vm.multiSelectMode) {
            MultiSelectActionBar(
                selectedCount = vm.selected.size,
                onExit = { vm.exitMultiSelect() },
                onSelectAll = { vm.selectAll() },
                onMove = { dialog = FolderDialog.Move },
                onCopy = { dialog = FolderDialog.Copy },
                onDelete = { vm.deleteSelected() },
                modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer {
                    translationY = size.height * selectionBack.progress
                    alpha = 1f - selectionBack.progress
                },
            )
        }
    }

    if (showMenu) {
        FolderMenuSheet(
            folderName = vm.currentFolder,
            canEditFolder = vm.currentFolder != LOCAL_ALL_FOLDER,
            onDismiss = { showMenu = false },
            onCreate = { dialog = FolderDialog.Create },
            onRename = { dialog = FolderDialog.Rename },
            onDelete = { vm.deleteFolder(vm.currentFolder) },
            onReorder = { dialog = FolderDialog.Reorder },
            onExport = { dialog = FolderDialog.Export },
        )
    }

    when (dialog) {
        FolderDialog.Create -> InputDialog(
            title = "新建收藏夹",
            hint = "收藏夹名称",
            confirmText = "创建",
            onDismiss = { dialog = null },
            onConfirm = { vm.createFolder(it) { msg -> /* TODO toast */ } },
        )

        FolderDialog.Rename -> InputDialog(
            title = "重命名收藏夹",
            hint = "新的名称",
            initialValue = vm.currentFolder,
            confirmText = "确定",
            onDismiss = { dialog = null },
            onConfirm = { vm.renameFolder(vm.currentFolder, it) },
        )

        FolderDialog.Move -> FolderPickerDialog(
            title = "移动到",
            folders = folders.filter { it != vm.currentFolder },
            onDismiss = { dialog = null },
            onPick = { vm.moveSelectedTo(it) },
        )

        FolderDialog.Copy -> FolderPickerDialog(
            title = "复制到",
            folders = folders.filter { it != vm.currentFolder },
            onDismiss = { dialog = null },
            onPick = { vm.copySelectedTo(it) },
        )

        FolderDialog.Reorder -> ReorderDialog(
            folders = folders,
            onDismiss = { dialog = null },
            onConfirm = { vm.saveFolderOrder(it) },
        )

        FolderDialog.Export -> ExportDialog(
            folder = vm.currentFolder,
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

/** 收藏页模式：本地收藏（本地数据库）/ 网络收藏（各漫画源账号）。对齐原版侧栏的两个并列入口。 */
private enum class FavoritesMode { Local, Network }

@Composable
private fun FavoritesModeToggle(
    mode: FavoritesMode,
    onModeChange: (FavoritesMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentChip(
            text = "网络收藏",
            selected = mode == FavoritesMode.Network,
            onClick = { onModeChange(FavoritesMode.Network) },
            modifier = Modifier.weight(1f),
        )
        SegmentChip(
            text = "本地收藏",
            selected = mode == FavoritesMode.Local,
            onClick = { onModeChange(FavoritesMode.Local) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SegmentChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MiuixTheme.colorScheme.primaryContainer
        } else {
            MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
        )
    }
}

/** 收藏夹操作类型。 */
private sealed interface FolderDialog {
    data object Create : FolderDialog
    data object Rename : FolderDialog
    data object Move : FolderDialog
    data object Copy : FolderDialog
    data object Reorder : FolderDialog
    data object Export : FolderDialog
}

// region ---- 顶部收藏夹 chips ----

@Composable
private fun FolderChipRow(
    folders: List<String>,
    counts: Map<String, Int>,
    current: String,
    onSelectFolder: (String) -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FolderChip(
                    name = "全部",
                    count = counts.values.sum(),
                    selected = current == LOCAL_ALL_FOLDER,
                    onClick = { onSelectFolder(LOCAL_ALL_FOLDER) },
                )
            }
            rowItems(folders, key = { it }) { folder ->
                FolderChip(
                    name = folder,
                    count = counts[folder] ?: 0,
                    selected = current == folder,
                    onClick = { onSelectFolder(folder) },
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = "搜索收藏",
            tint = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp).clickable { onSearchClick() },
        )
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "收藏夹操作",
            tint = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp).clickable { onMenuClick() },
        )
    }
}

@Composable
private fun FolderChip(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MiuixTheme.colorScheme.primaryContainer
        } else {
            MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = count.toString(),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

@Composable
private fun SearchField(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = keyword,
                onValueChange = onKeywordChange,
                modifier = Modifier.weight(1f),
            )
            if (keyword.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.size(18.dp).clickable { onKeywordChange("") },
                )
            }
        }
    }
}

// endregion

// region ---- 内容网格 ----

@Composable
private fun FavoriteGrid(
    vm: FavoritesViewModel,
    onSelect: (ComicItem) -> Unit,
) {
    if (vm.comics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Text(text = "⭐", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (vm.keyword.isBlank()) "暂无收藏漫画" else "没有匹配的收藏",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (vm.keyword.isBlank()) {
                        "在漫画详情页点击「收藏」即可加入收藏夹"
                    } else {
                        "试试其他关键词"
                    },
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
        return
    }

    val displayMode = rememberComicListDisplayMode()
    LazyVerticalGrid(
        columns = GridCells.Fixed(comicListColumnCount(displayMode.value)),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(vm.comics, key = { "${it.id}-${it.type}" }) { item ->
            FavoriteCard(
                item = item,
                detailed = displayMode.value == "detailed",
                selected = (item.id to item.type) in vm.selected,
                multiSelectMode = vm.multiSelectMode,
                onClick = {
                    if (vm.multiSelectMode) {
                        vm.toggleSelect(item)
                    } else {
                        onSelect(item.toComicItem())
                    }
                },
                onLongClick = { vm.enterMultiSelect(item) },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FavoriteCard(
    item: com.venera.compose.data.db.FavoriteItem,
    detailed: Boolean,
    selected: Boolean,
    multiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val metrics by rememberCachedComicMetrics(item.sourceKey, item.id)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            ComicCardLayout(detailed = detailed, modifier = Modifier.padding(8.dp), cover = {
                AsyncImage(
                    model = item.coverPath,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            }) {
                Spacer(modifier = Modifier.height(6.dp))
                ComicMetrics(metrics.rating, metrics.likesCount)
                Text(text = item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                Text(
                    text = item.description,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                )
            }
            if (multiSelectMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MiuixTheme.colorScheme.primary else Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(22.dp),
                )
            }
        }
    }
}

private fun com.venera.compose.data.db.FavoriteItem.toComicItem() = ComicItem(
    id = id,
    title = name,
    author = author,
    coverUrl = coverPath,
    tags = tags,
    sourceName = sourceKey,
    description = description,
)

// endregion

// region ---- 多选动作条 ----

@Composable
private fun MultiSelectActionBar(
    selectedCount: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MiuixTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "已选择 $selectedCount 项", fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "全选",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onSelectAll() }.padding(6.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "退出多选",
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp).clickable { onExit() },
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(icon = {
                    Icon(Icons.Outlined.DriveFileMove, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }, label = "移动到", modifier = Modifier.weight(1f), onClick = onMove)
                ActionChip(icon = {
                    Icon(Icons.Outlined.ContentCopy, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }, label = "复制到", modifier = Modifier.weight(1f), onClick = onCopy)
                ActionChip(icon = {
                    Icon(Icons.Filled.Delete, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }, label = "删除", modifier = Modifier.weight(1f), onClick = onDelete)
            }
        }
    }
}

@Composable
private fun ActionChip(
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            icon()
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 12.sp)
        }
    }
}

// endregion

// region ---- 对话框（Miuix 风格自建，避免依赖不确定的 Dialog API） ----

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    com.venera.compose.components.PredictiveBackOverlay(onDismiss = onDismiss) {
        Box(modifier = Modifier.clickable(enabled = false) {}) {
            Card(modifier = Modifier.padding(horizontal = 28.dp)) { content() }
        }
    }
}

@Composable
private fun InputDialog(
    title: String,
    hint: String,
    initialValue: String = "",
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    Scrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.isBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = hint, fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
            }
            Spacer(modifier = Modifier.height(16.dp))
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

@Composable
private fun FolderPickerDialog(
    title: String,
    folders: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    Scrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            if (folders.isEmpty()) {
                Text(
                    text = "没有其他收藏夹，请先新建一个",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            } else {
                Column {
                    folders.forEach { folder ->
                        Text(
                            text = folder,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(folder)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            ) { Text("取消") }
        }
    }
}

@Composable
private fun ReorderDialog(
    folders: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val order = remember(folders) { mutableStateOf(folders.toList()) }

    fun move(index: Int, delta: Int) {
        val list = order.value.toMutableList()
        val target = index + delta
        if (target !in list.indices) return
        val tmp = list[index]
        list[index] = list[target]
        list[target] = tmp
        order.value = list
    }

    Scrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "调整收藏夹顺序", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            Column {
                order.value.forEachIndexed { i, folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = folder, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(
                            text = "↑",
                            fontSize = 16.sp,
                            color = if (i == 0) MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.3f)
                            else MiuixTheme.colorScheme.primary,
                            modifier = Modifier.clickable { move(i, -1) }.padding(horizontal = 10.dp),
                        )
                        Text(
                            text = "↓",
                            fontSize = 16.sp,
                            color = if (i == order.value.lastIndex) MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.3f)
                            else MiuixTheme.colorScheme.primary,
                            modifier = Modifier.clickable { move(i, 1) }.padding(horizontal = 10.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                ) { Text("取消") }
                Button(
                    onClick = {
                        onConfirm(order.value)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun ExportDialog(
    folder: String,
    onDismiss: () -> Unit,
) {
    Scrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "导出收藏夹", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (folder == LOCAL_ALL_FOLDER) {
                    "请先切换到具体的收藏夹再导出（「全部」是虚拟视图）。"
                } else {
                    "「$folder」的导出功能将在 S7（同步/备份）阶段与 WebDAV 一同落地。"
                },
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("知道了") }
        }
    }
}

@Composable
private fun FolderMenuSheet(
    folderName: String,
    canEditFolder: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onReorder: () -> Unit,
    onExport: () -> Unit,
) {
    Scrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (canEditFolder) folderName else "收藏夹操作",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            MenuItem(text = "新建收藏夹", icon = { Icon(Icons.Outlined.Add, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }) { onDismiss(); onCreate() }
            if (canEditFolder) {
                MenuItem(text = "重命名当前收藏夹", icon = { }) { onDismiss(); onRename() }
                MenuItem(text = "删除当前收藏夹", icon = { }) { onDismiss(); onDelete() }
            }
            MenuItem(text = "调整收藏夹顺序", icon = { }) { onDismiss(); onReorder() }
            MenuItem(text = "导出当前收藏夹", icon = { }) { onDismiss(); onExport() }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            ) { Text("取消") }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, fontSize = 15.sp)
    }
}

// endregion

// ==================== 5. 探索页 (ExplorePage) 1:1 复刻 ====================
