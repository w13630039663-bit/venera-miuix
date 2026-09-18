/**
 * S5-3 收藏页（对齐原版 `pages/favorites/side_bar.dart` + `local_favorites_page.dart`）。
 *
 * 原版是「宽屏双栏（侧栏 + 内容）/ 窄屏抽屉」；本项目是手机单栏，
 * 因此把收藏夹选择做成顶部横向 chips（行为等价：切换当前收藏夹 + 显示计数），
 * 文件夹动作收纳进右上角菜单。
 */
package com.venera.compose.feature

import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens

import com.venera.compose.components.*
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraTagChip
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.components.venera.rememberTopBarBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
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
    val tokens = VeneraTokens
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
    // 大标题折叠 + 毛玻璃顶栏（页内自治）
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = statusBarTop + 104.dp + 48.dp + if (mode == FavoritesMode.Local && vm.multiSelectMode) 76.dp else 0.dp

    // 顶栏折叠判定：沿用 miuix TopAppBar 自己的阈值（collapsedFraction * 3 >= 1，
    // 即 smallTitle 出现的同一时刻），保证「分段切换器收起」与「小标题淡入」严格同步。
    val topBarCollapsed by remember(topBarBehavior) {
        derivedStateOf { topBarBehavior.state.collapsedFraction * 3f >= 1f }
    }
    // 当前面板的列表滚动状态（由子面板上抛）：是否已下滑、以及回到顶部的动作。
    // 用回调而非持有 ListState，避免 LazyListState / LazyGridState 两种类型互相污染。
    var favHasScrolled by remember { mutableStateOf(false) }
    var favScrollToTop by remember { mutableStateOf<(() -> Unit)?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (mode == FavoritesMode.Local) {
            FavoriteGrid(
                vm = vm,
                folders = folders,
                counts = counts,
                searchMode = searchMode,
                topPadding = topPadding,
                onSelect = onSelect,
                scrollConnection = topBarBehavior.nestedScrollConnection,
                backdrop = topBarBackdrop,
                onScrollStateChange = { _, hasScrolled, scrollToTop ->
                    favHasScrolled = hasScrolled
                    favScrollToTop = scrollToTop
                },
            )
        } else {
            AndroidNetworkFavoritesScreen(
                onSelect = onSelect,
                scrollConnection = topBarBehavior.nestedScrollConnection,
                backdrop = topBarBackdrop,
                topPadding = topPadding,
                onScrollStateChange = { _, hasScrolled, scrollToTop ->
                    favHasScrolled = hasScrolled
                    favScrollToTop = scrollToTop
                },
            )
        }

        // ── 统一顶栏：置于前景层，毛玻璃对底层内容进行物理级模糊 ──
        VeneraTopAppBar(
            title = "收藏",
            largeTitle = "收藏",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            actions = {
                if (mode == FavoritesMode.Local) {
                    FavoritesSortMenu(
                        sortOrder = vm.sortOrder,
                        onSortOrderChange = { vm.updateSortOrder(it) },
                    )
                    IconButton(onClick = { searchMode = !searchMode }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "搜索收藏",
                            tint = tokens.color.textSecondary,
                        )
                    }
                    ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "收藏夹操作",
                            tint = tokens.color.textSecondary,
                        )
                    }
                } else {
                    ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
                }
            },
            bottomContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 分段切换器只在顶置（未下滑）时出现：下滑折叠后顶栏不再同时挂着
                    // 「网络收藏 / 本地收藏」两个按钮，让折叠态顶栏保持干净。
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !topBarCollapsed,
                        enter = androidx.compose.animation.fadeIn() +
                            androidx.compose.animation.expandVertically(),
                        exit = androidx.compose.animation.fadeOut() +
                            androidx.compose.animation.shrinkVertically(),
                    ) {
                        FavoritesModeToggle(
                            mode = mode,
                            onModeChange = { mode = it },
                        )
                    }
                    if (mode == FavoritesMode.Local && vm.multiSelectMode) {
                        MultiSelectActionBar(
                            selectedCount = vm.selected.size,
                            onExit = { vm.exitMultiSelect() },
                            onSelectAll = { vm.selectAll() },
                            onMove = { dialog = FolderDialog.Move },
                            onCopy = { dialog = FolderDialog.Copy },
                            onDelete = { vm.deleteSelected() },
                            modifier = Modifier.graphicsLayer {
                                alpha = 1f - selectionBack.progress
                            },
                        )
                    }
                }
            },
        )

        // 右下角「顶置」按钮：下滑一段距离后淡入，点击回到列表顶部。
        // 底部让位沿用与其他页面一致的契约（底栏 clearanc + 额外留白），不手写 magic number。
        val favBackToTopBottom = VeneraSpacing.bottomBarClearance + VeneraSpacing.space9
        androidx.compose.animation.AnimatedVisibility(
            visible = favHasScrolled,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = tokens.spacing.space8,
                    bottom = favBackToTopBottom,
                ),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(tokens.shape.large),
                color = tokens.color.primaryContainer,
                modifier = Modifier.clickable {
                    favScrollToTop?.invoke()
                },
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = tokens.spacing.chipHorizontalPadding,
                        vertical = tokens.spacing.chipVerticalPadding,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "回到顶部",
                        tint = tokens.color.onPrimaryContainer,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                    Text(
                        text = "顶部",
                        fontSize = tokens.type.caption,
                        color = tokens.color.onPrimaryContainer,
                    )
                }
            }
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
    // 分段选择药丸容器（Segmented Control）：44dp 高、完全胶囊圆角、弱底色，
    // 选中项高亮药丸 + 触控反馈。大目标易点，替代此前过小的 VeneraChip。
    val tokens = com.venera.compose.ui.tokens.VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = tokens.spacing.rowHorizontal,
                vertical = tokens.spacing.space3,
            )
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha))
            .padding(tokens.spacing.space1),
    ) {
        SegmentOption(
            text = "网络收藏",
            selected = mode == FavoritesMode.Network,
            onClick = { onModeChange(FavoritesMode.Network) },
            modifier = Modifier.weight(1f),
        )
        SegmentOption(
            text = "本地收藏",
            selected = mode == FavoritesMode.Local,
            onClick = { onModeChange(FavoritesMode.Local) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 分段选择容器里的单段：44dp 高、居中、选中高亮药丸。 */
@Composable
private fun SegmentOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = com.venera.compose.ui.tokens.VeneraTokens
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) tokens.color.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "SegmentBg",
    )
    Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = tokens.spacing.space6),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = tokens.type.body,
            fontWeight = tokens.type.weightSemibold,
            color = if (selected) tokens.color.onPrimaryContainer else tokens.color.textSecondary,
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
) {
    val tokens = com.venera.compose.ui.tokens.VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = com.venera.compose.ui.tokens.VeneraTokens.spacing.rowHorizontal, vertical = com.venera.compose.ui.tokens.VeneraTokens.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(com.venera.compose.ui.tokens.VeneraTokens.spacing.chipSpacing),
        ) {
            item {
                VeneraChip(
                    text = "全部 · " + counts.values.sum(),
                    selected = current == LOCAL_ALL_FOLDER,
                    onClick = { onSelectFolder(LOCAL_ALL_FOLDER) },
                )
            }
            rowItems(folders, key = { it }) { folder ->
                VeneraChip(
                    text = folder + " · " + (counts[folder] ?: 0),
                    selected = current == folder,
                    onClick = { onSelectFolder(folder) },
                )
            }
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
    folders: List<String>,
    counts: Map<String, Int>,
    searchMode: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    onSelect: (ComicItem) -> Unit,
    /** 顶栏折叠行为：下滑时大标题收起、毛玻璃淡入。 */
    scrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
    backdrop: LayerBackdrop? = null,
    /** 把内部列表的滚动位置上抛（能否回到顶部 / 是否已下滑），供外壳渲染顶置按钮。 */
    onScrollStateChange: (canScrollUp: Boolean, hasScrolled: Boolean, scrollToTop: () -> Unit) -> Unit = { _, _, _ -> },
) {
    val tokens = VeneraTokens
    val displayMode = rememberComicListDisplayMode()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
        }.collect { hasScrolled ->
            onScrollStateChange(
                gridState.canScrollBackward,
                hasScrolled,
            ) { scope.launch { gridState.animateScrollToItem(0) } }
        }
    }
    // HIDE 模式：命中守卫判定链的条目整条剔除（UI 层过滤，不落数据库）。
    val guardManager = com.venera.compose.security.guard.ContentGuardManager.getInstance(LocalContext.current)
    val visibleComics = if (guardManager.nsfwMaskMode.collectAsState().value == "HIDE") {
        vm.comics.filter { item ->
            !guardManager.coverMaskStateFor(
                sourceKey = item.sourceKey, title = item.name, author = item.author,
                tags = item.tags, comicId = item.id, description = item.description,
            ).let { it == "HIDDEN" }
        }
    } else vm.comics

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(comicListColumnCount(displayMode.value)),
        contentPadding = PaddingValues(
            start = tokens.spacing.rowHorizontal,
            end = tokens.spacing.rowHorizontal,
            top = topPadding,
            bottom = VeneraSpacing.bottomBarClearance,
        ),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
        modifier = Modifier
            .fillMaxSize()
            .then(if (scrollConnection != null) Modifier.nestedScroll(scrollConnection) else Modifier)
            .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "fav-folders") {
            FolderChipRow(
                folders = folders,
                counts = counts,
                current = vm.currentFolder,
                onSelectFolder = { vm.selectFolder(it) },
            )
        }

        if (searchMode) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "fav-search") {
                SearchField(
                    keyword = vm.keyword,
                    onKeywordChange = { vm.updateKeyword(it) },
                    modifier = Modifier.padding(vertical = tokens.spacing.space1),
                )
            }
        }

        if (visibleComics.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "fav-empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VeneraEmptyView(
                        title = if (vm.keyword.isBlank()) "暂无收藏漫画" else "没有匹配的收藏",
                        message = if (vm.keyword.isBlank()) {
                            "在漫画详情页点击「收藏」即可加入收藏夹"
                        } else {
                            "试试其他关键词"
                        },
                        icon = Icons.Outlined.StarBorder,
                    )
                }
            }
        } else {
            gridItems(visibleComics, key = { "${it.id}-${it.type}" }) { item ->
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
    // 内容守卫：带 sourceKey 走全量判定链（源级预设 + 用户规则 + 显式标记），
    // BLUR 命中打码；HIDE 命中在数据层已剔除，此处兜底。
    val guard = com.venera.compose.security.guard.ContentGuardManager.getInstance(LocalContext.current)
    val maskState = guard.coverMaskStateFor(
        sourceKey = item.sourceKey,
        title = item.name,
        author = item.author,
        tags = item.tags,
        comicId = item.id,
        description = item.description,
    )
    val tokens = com.venera.compose.ui.tokens.VeneraTokens
    com.venera.compose.components.venera.VeneraCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Box {
            ComicCardLayout(detailed = detailed, modifier = Modifier.padding(tokens.spacing.space2), cover = {
                com.venera.compose.components.venera.VeneraCover(
                    url = item.coverPath,
                    contentDescription = item.name,
                    shimmerWhileLoading = false,
                    mask = if (maskState == "VISIBLE") com.venera.compose.components.venera.VeneraCoverMask.Visible
                           else com.venera.compose.components.venera.VeneraCoverMask.Masked,
                )
            }) {
                Spacer(modifier = Modifier.height(tokens.spacing.space3))
                ComicMetrics(metrics.rating, metrics.likesCount)
                Text(
                    text = item.name,
                    fontWeight = tokens.type.weightBold,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = item.description,
                    fontSize = tokens.type.overline,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                )
                // 复用现成的 Tag 截断/归一化逻辑（searchVisibleTags），不另写一套。
                // 最多 2 个，空 tags 时整段不渲染（不产生空白区域）。
                val visibleTags = com.venera.compose.feature.searchVisibleTags(item.tags, emptyList())
                if (visibleTags.isNotEmpty()) {
                    Spacer(Modifier.height(tokens.spacing.space3))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                    ) {
                        visibleTags.take(2).forEach { tag ->
                            Box(Modifier.weight(1f, fill = false)) {
                                VeneraTagChip(text = tag, onClick = null)
                            }
                        }
                    }
                }
            }
            if (multiSelectMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) tokens.color.primary else Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(tokens.spacing.space5)
                        .size(tokens.spacing.badgeSize),
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
    // 顶部二级工具条（对齐 HistoryScreen 模式）：底部 overlay 版本会被悬浮底栏遮挡。
    val tokens = com.venera.compose.ui.tokens.VeneraTokens
    Surface(
        shape = RoundedCornerShape(tokens.shape.medium),
        color = MiuixTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.rowHorizontal, vertical = tokens.spacing.space2),
    ) {
        Column(modifier = Modifier.padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已选择 $selectedCount 项",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "全选",
                    fontSize = tokens.type.caption,
                    color = tokens.color.primary,
                    modifier = Modifier.clickable { onSelectAll() }.padding(tokens.spacing.space2),
                )
                Spacer(modifier = Modifier.width(tokens.spacing.space2))
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "退出多选",
                    tint = tokens.color.textPrimary,
                    modifier = Modifier
                        .size(tokens.spacing.chipIconSize)
                        .clickable { onExit() },
                )
            }
            Spacer(modifier = Modifier.height(tokens.spacing.space3))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2)) {
                ActionChip(icon = {
                    Icon(Icons.Outlined.DriveFileMove, null, tint = tokens.color.primary, modifier = Modifier.size(tokens.spacing.chipIconSize))
                }, label = "移动到", modifier = Modifier.weight(1f), onClick = onMove)
                ActionChip(icon = {
                    Icon(Icons.Outlined.ContentCopy, null, tint = tokens.color.primary, modifier = Modifier.size(tokens.spacing.chipIconSize))
                }, label = "复制到", modifier = Modifier.weight(1f), onClick = onCopy)
                ActionChip(icon = {
                    Icon(Icons.Filled.Delete, null, tint = tokens.color.primary, modifier = Modifier.size(tokens.spacing.chipIconSize))
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
    val tokens = com.venera.compose.ui.tokens.VeneraTokens
    Surface(
        shape = RoundedCornerShape(tokens.shape.small),
        color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.spacing.space5, vertical = tokens.spacing.space5),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            icon()
            Spacer(modifier = Modifier.width(tokens.spacing.space3))
            Text(text = label, fontSize = tokens.type.caption, color = tokens.color.textPrimary)
        }
    }
}

// endregion

// region ---- 对话框（Miuix 风格自建，避免依赖不确定的 Dialog API） ----

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    com.venera.compose.components.PredictiveBackOverlay(onDismiss = onDismiss) {
        Box(modifier = Modifier.clickable(enabled = false) {}) {
            Card(modifier = Modifier.padding(horizontal = com.venera.compose.ui.tokens.VeneraTokens.spacing.space10)) { content() }
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
        Column(modifier = Modifier.padding(com.venera.compose.ui.tokens.VeneraTokens.spacing.space8)) {
            Text(text = title, fontSize = com.venera.compose.ui.tokens.VeneraTokens.type.itemTitle, fontWeight = com.venera.compose.ui.tokens.VeneraTokens.type.weightSemibold)
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
                    colors = ButtonDefaults.buttonColors(color = com.venera.compose.ui.tokens.VeneraTokens.color.surfaceVariant.copy(alpha = com.venera.compose.ui.tokens.VeneraTokens.current.selectedSurfaceAlpha)),
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
        Column(modifier = Modifier.padding(com.venera.compose.ui.tokens.VeneraTokens.spacing.space8)) {
            Text(text = title, fontSize = com.venera.compose.ui.tokens.VeneraTokens.type.itemTitle, fontWeight = com.venera.compose.ui.tokens.VeneraTokens.type.weightSemibold)
            Spacer(modifier = Modifier.height(10.dp))
            if (folders.isEmpty()) {
                Text(
                    text = "没有其他收藏夹，请先新建一个",
                    fontSize = 13.sp,
                    color = com.venera.compose.ui.tokens.VeneraTokens.color.textSecondary,
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
                colors = ButtonDefaults.buttonColors(color = com.venera.compose.ui.tokens.VeneraTokens.color.surfaceVariant.copy(alpha = com.venera.compose.ui.tokens.VeneraTokens.current.selectedSurfaceAlpha)),
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
        Column(modifier = Modifier.padding(com.venera.compose.ui.tokens.VeneraTokens.spacing.space8)) {
            Text(text = "调整收藏夹顺序", fontSize = com.venera.compose.ui.tokens.VeneraTokens.type.itemTitle, fontWeight = com.venera.compose.ui.tokens.VeneraTokens.type.weightSemibold)
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
                    colors = ButtonDefaults.buttonColors(color = com.venera.compose.ui.tokens.VeneraTokens.color.surfaceVariant.copy(alpha = com.venera.compose.ui.tokens.VeneraTokens.current.selectedSurfaceAlpha)),
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
        Column(modifier = Modifier.padding(com.venera.compose.ui.tokens.VeneraTokens.spacing.space8)) {
            Text(text = "导出收藏夹", fontSize = com.venera.compose.ui.tokens.VeneraTokens.type.itemTitle, fontWeight = com.venera.compose.ui.tokens.VeneraTokens.type.weightSemibold)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (folder == LOCAL_ALL_FOLDER) {
                    "请先切换到具体的收藏夹再导出（「全部」是虚拟视图）。"
                } else {
                    "「$folder」的导出功能将在 S7（同步/备份）阶段与 WebDAV 一同落地。"
                },
                fontSize = 13.sp,
                color = com.venera.compose.ui.tokens.VeneraTokens.color.textSecondary,
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
        Column(modifier = Modifier.padding(com.venera.compose.ui.tokens.VeneraTokens.spacing.space8)) {
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
                colors = ButtonDefaults.buttonColors(color = com.venera.compose.ui.tokens.VeneraTokens.color.surfaceVariant.copy(alpha = com.venera.compose.ui.tokens.VeneraTokens.current.selectedSurfaceAlpha)),
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

/**
 * 顶栏排序菜单：名称 / 时间 / 自定义排序，当前项主色勾选。
 *
 * 从 FolderChipRow 行内提升到顶栏 actions —— 与「布局切换 / 更多」并列，
 * 消除原二级工具条的横向拥挤。
 */
@Composable
private fun FavoritesSortMenu(
    sortOrder: FavoriteSortOrder,
    onSortOrderChange: (FavoriteSortOrder) -> Unit,
) {
    val tokens = VeneraTokens
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = "排序方式",
                tint = if (sortOrder == FavoriteSortOrder.CUSTOM) tokens.color.textSecondary else tokens.color.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FavoriteSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = order.label,
                            fontSize = tokens.type.body,
                            color = if (order == sortOrder) tokens.color.primary else tokens.color.textPrimary,
                        )
                    },
                    trailingIcon = if (order == sortOrder) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "当前排序",
                                tint = tokens.color.primary,
                                modifier = Modifier.size(tokens.spacing.chipIconSize),
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        onSortOrderChange(order)
                    },
                )
            }
        }
    }
}
