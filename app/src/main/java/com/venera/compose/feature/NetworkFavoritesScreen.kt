/**
 * S5 网络收藏夹 —— 手风琴原地展开交互（Stage 0~2 信息架构升级）。
 *
 * 旧交互是三级整屏下钻（源列表 → 文件夹 → 漫画网格），看其他源要反复返回；
 * 新交互为**单开手风琴**：所有支持网络收藏的源以折叠卡头纵向排列，点击某源
 * 原地展开其收藏网格（多文件夹源在展开区顶部挂横向 FolderChip 原地切夹），
 * 再点一次收起；点开新源自动收起旧源（内存紧凑、列表轻快）。
 *
 * 虚拟化契约：全页唯一 LazyColumn，展开区漫画按 chunked(columns) 逐行挂载
 * （行 = LazyItem 参与回收），绝不嵌套同向 Lazy 组件（Infinity constraints 崩溃）。
 * 所有网络调用走 [NetworkFavoritesViewModel] → FavoriteData（retryZone 自动重登）。
 */
package com.venera.compose.feature

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.venera.compose.components.ComicCardLayout
import com.venera.compose.components.ComicLayoutToggleButton
import com.venera.compose.components.ComicTileDetailed
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.comicListColumnCount
import com.venera.compose.components.rememberComicListDisplayMode
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraSourceBadge
import com.venera.compose.components.venera.VeneraTagChip
import com.venera.compose.source.model.Comic
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AndroidNetworkFavoritesScreen(
    onSelect: (ComicItem) -> Unit,
    scrollConnection: NestedScrollConnection? = null,
    backdrop: LayerBackdrop? = null,
    topPadding: Dp = 0.dp,
    /** 把内部列表的滚动位置上抛（是否已下滑 / 回到顶部动作），供外壳渲染顶置按钮。 */
    onScrollStateChange: (canScrollUp: Boolean, hasScrolled: Boolean, scrollToTop: () -> Unit) -> Unit = { _, _, _ -> },
) {
    val tokens = VeneraTokens
    val vm: NetworkFavoritesViewModel = viewModel()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }.collect { hasScrolled ->
            onScrollStateChange(
                listState.canScrollBackward,
                hasScrolled,
            ) { scope.launch { listState.animateScrollToItem(0) } }
        }
    }
    val sources by vm.sourcesFlow.collectAsState()
    val displayMode = rememberComicListDisplayMode()

    val selectedKey = vm.selectedSourceKey
    val isMulti = vm.isMultiFolder
    val folders = vm.folders
    val currentFolder = vm.currentFolderId
    val comics = vm.comics
    val isLoading = vm.isLoading
    val isFolderLoading = vm.isFolderLoading
    val error = vm.error
    val columns = comicListColumnCount(displayMode.value)
    val isDetailed = displayMode.value == "detailed"
    // 待确认的移除请求（长按卡片触发，二次确认后执行删除）。
    var pendingDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Comic?>(null) }

    // 首屏判定：只有「尚无任何内容」的加载才全屏 Loader；loadMore 期间列表原地不动。
    val firstLoading = isLoading && comics.isEmpty()


    // ── 下拉手动刷新（本轮改造）──
    // 取消 M3 顶栏那枚圆形箭头（indicator = {}）：它浮在状态栏区域，和折叠顶栏抢位置。
    // 改为「源栏正下方一行波浪环」，行高由下拉进度 1:1 驱动 —— 下拉时卡片跟手下移，
    // 放手后刷新中停在满高，刷新完成收回，卡片上移露出新内容。
    //
    // isRefreshing 不再直接吃 VM 的 isLoading：那个标志同时被 loadMore 复用
    //（NetworkFavoritesViewModel:242 / :274），照搬会把「滚到底加载更多」也点亮成刷新。
    // 这里在屏幕侧记一次「由用户下拉发起」，等 VM 的 loading 起落后再收回。
    val pullState = rememberPullToRefreshState()
    var pullInFlight by remember { androidx.compose.runtime.mutableStateOf(false) }
    // 必须「先见过 loading 起来、再落下」才算这次刷新结束：立刻收回，不留延迟。
    // 之前用固定 delay(250) 收尾，会让环在数据已经回来后还多挂 250ms 才塌 —— 真机看到的就是这一下「闪」。
    var pullSawLoading by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(pullInFlight, isLoading, isFolderLoading) {
        if (!pullInFlight) {
            pullSawLoading = false
            return@LaunchedEffect
        }
        if (isLoading || isFolderLoading) {
            pullSawLoading = true
            return@LaunchedEffect
        }
        if (!pullSawLoading) {
            // refresh() 可能同步早退（favoriteData 为空，或走的是 loadFolders 之外的分支），
            // 兜一个上限，避免环永远挂着收不回来。
            delay(600)
            if (pullInFlight && !isLoading && !isFolderLoading && !pullSawLoading) pullInFlight = false
        } else {
            pullInFlight = false
        }
    }
    val pullProgress = if (pullInFlight) 1f else pullState.distanceFraction.coerceIn(0f, 1f)
    val pullRowHeight by animateDpAsState(
        targetValue = tokens.spacing.pullRefreshRowHeight * pullProgress,
        label = "nf-pull-row-height",
    )
    // 刷新期间把旧卡片留在原位：VM 的 refresh() 一进来就 `comics = emptyList()`
    //（NetworkFavoritesViewModel:165），跟着清空会变成「卡片消失 → 转圈 → 卡片回来」的硬切。
    // ⚠️ 快照必须在调用 refresh() **之前**抓：onRefresh 是普通 lambda，refresh() 里的
    // resetComicState() 是同步执行的，等到 LaunchedEffect（重组之后）再抓，拿到的已经是空表。
    var pullSnapshot by remember { androidx.compose.runtime.mutableStateOf<List<Comic>>(emptyList()) }
    val shownComics = if (pullInFlight && comics.isEmpty()) pullSnapshot else comics
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = pullInFlight,
        onRefresh = {
            if (selectedKey != null) {
                pullSnapshot = comics
                pullSawLoading = false
                pullInFlight = true
                vm.refresh()
            }
        },
        state = pullState,
        enabled = selectedKey != null,
        indicator = {},
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(if (scrollConnection != null) Modifier.nestedScroll(scrollConnection) else Modifier)
            .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        contentPadding = PaddingValues(
            start = tokens.spacing.rowHorizontal,
            end = tokens.spacing.rowHorizontal,
            top = topPadding,
            bottom = VeneraSpacing.bottomBarClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
    ) {
        if (sources.isEmpty()) {
            item(key = "nf-empty") {
                VeneraEmptyView(
                    title = "暂无支持网络收藏的源",
                    message = "在「源管理」中登录账号后，这里会显示对应的网络收藏夹",
                )
            }
        }

        // ── 顶部横向源切换栏 + 单双列切换：同行排布，无多余空行。──
        item(key = "nf-source-bar") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TopSourceBar(
                        sources = sources,
                        selectedKey = selectedKey,
                        onSelect = { key ->
                            if (key != selectedKey) vm.expandSource(key) else vm.collapseSource()
                        },
                    )
                }
                ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
            }
        }

        // ── 下拉刷新指示行：源栏正下方。行高跟手（0 → 48dp），刷新中满高旋转，
        //    完成后收回 0 高 —— 卡片随之上移。高度归零时整行不挂载，避免留下空档。──
        if (pullRowHeight > 0.dp) {
            item(key = "nf-pull-indicator") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pullRowHeight)
                        .clipToBounds()
                        // 随下拉进度淡入，而不是被行高从中间切一半
                        .graphicsLayer { alpha = pullProgress },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator(
                        // 直径小于行高，收回过程中不会被裁成一条线
                        modifier = Modifier.size(tokens.spacing.loaderInline),
                        color = tokens.color.primary,
                        trackColor = tokens.color.surfaceVariant,
                    )
                }
            }
        }

        // ── 当前源内容（未选源时给引导空态）。──
        val current = sources.firstOrNull { it.key == selectedKey }
        if (current == null) {
            item(key = "nf-idle") {
                VeneraEmptyView(
                    title = "选择一个漫画源",
                    message = "点击上方的源药丸，即可原地查看该源的网络收藏",
                )
            }
            return@LazyColumn
        }

        // 多文件夹源：内容区顶部紧跟横滑文件夹 Chips（原地切夹）。
        if (isMulti) {
            item(key = "nf-chips") {
                FolderChipRow(
                    folders = folders,
                    current = currentFolder,
                    isLoading = isFolderLoading,
                    onSelect = { vm.enterFolder(it) },
                )
            }
        }

        when {
            // 修复：全屏 Loader 仅首屏（无内容时）；loadMore 期间列表原地保持。
            // 下拉刷新中不切 Loader —— 进度由源栏下方那行波浪环单独表达。
            firstLoading && !pullInFlight -> item(key = "nf-loading") { AccordionLoader() }
            error != null && shownComics.isEmpty() -> item(key = "nf-error") {
                VeneraEmptyView(
                    title = "收藏加载失败",
                    message = error,
                    actionText = "重试",
                    onAction = { vm.refresh() },
                )
            }
            // 刷新中且没有旧内容可留（首次展开就下拉）：什么都不画，只留上方波浪环。
            shownComics.isEmpty() -> {
                if (!pullInFlight) {
                    item(key = "nf-folder-empty") {
                        VeneraEmptyView(message = "该收藏夹暂无漫画")
                    }
                }
            }
            isDetailed -> {
                items(
                    shownComics,
                    key = { "ncard-" + it.id },
                ) { comic ->
                    NetComicDetailedCard(comic, current.name, onSelect,
                        onDelete = { pendingDelete = comic },
                    )
                }
            }
            else -> {
                // 行级虚拟化：每行 = 一个 LazyItem，滑出屏幕立即回收。
                shownComics.chunked(columns).forEachIndexed { rowIndex, row ->
                    item(key = "nrow-" + rowIndex + "-" + (row.firstOrNull()?.id ?: "")) {
                        ComicGridRow(
                            row = row,
                            sourceName = current.name,
                            onSelect = onSelect,
                            onDelete = { pendingDelete = it },
                        )
                    }
                }
            }
        }

        // 触底自动加载：loadMore 期间 footer 显示动画，列表原地不动。
        if (vm.hasMore && !isLoading && error == null && comics.isNotEmpty()) {
            item(key = "nmore") {
                LaunchedEffect(currentFolder) {
                    if (!vm.isLoading) vm.loadMore()
                }
                LoadMoreFooter(isLoading = vm.isLoading)
            }
        }
    }

    }

    // 长按移除确认弹窗（补回）：破坏性操作必须二次确认。
    pendingDelete?.let { comic ->
        NetRemoveConfirmDialog(
            comicTitle = comic.title,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                vm.deleteComic(comic.id, currentFolder ?: "", null) { _, _ -> }
                pendingDelete = null
            },
        )
    }
}

/* ------------------------------------------------------------------ *
 * 手风琴折叠卡头
 * ------------------------------------------------------------------ */

@Composable
private fun TopSourceBar(sources: List<NetSourceUi>, selectedKey: String?, onSelect: (String) -> Unit) {
    val tokens = VeneraTokens
    LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
        items(sources, key = { it.key }) { src ->
            val selected = src.key == selectedKey
            // 大号源药丸：登录态圆点 + 源名；选中高亮 primaryContainer。
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) tokens.color.primaryContainer
                        else tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
                modifier = Modifier.clickable { onSelect(src.key) },
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = tokens.spacing.space6,
                        vertical = tokens.spacing.space3,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(tokens.spacing.statusDotSize)
                            .clip(CircleShape)
                            .background(if (src.logged) StatusColors.Healthy else tokens.color.textDisabled),
                    )
                    Spacer(Modifier.width(tokens.spacing.space2))
                    Text(
                        text = src.name,
                        fontSize = tokens.type.caption,
                        fontWeight = if (selected) tokens.type.weightSemibold else tokens.type.weightMedium,
                        color = if (selected) tokens.color.onPrimaryContainer else tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 展开区：文件夹 chips / 加载态 / 网格行 / 触底 footer
 * ------------------------------------------------------------------ */

@Composable
private fun FolderChipRow(folders: Map<String, String>?, current: String?, isLoading: Boolean, onSelect: (String) -> Unit) {
    val tokens = VeneraTokens
    when {
        isLoading -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space2),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(tokens.spacing.loaderInline),
                color = tokens.color.primary,
                trackColor = tokens.color.surfaceVariant,
            )
        }
        folders.isNullOrEmpty() -> {}
        else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            items(folders.toList(), key = { it.first }) { (id, name) ->
                VeneraChip(
                    text = name,
                    selected = current == id,
                    onClick = { if (current != id) onSelect(id) },
                )
            }
        }
    }
}

@Composable
private fun AccordionLoader() {
    val tokens = VeneraTokens
    Box(Modifier.fillMaxWidth().padding(tokens.spacing.space9), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            modifier = Modifier.size(tokens.spacing.loaderPage),
            color = tokens.color.primary,
            trackColor = tokens.color.surfaceVariant,
        )
    }
}

/** 双列网格行：等宽卡片 + 尾部补空，一个 Row 就是一个 LazyItem。 */
@Composable
private fun ComicGridRow(
    row: List<Comic>,
    sourceName: String,
    onSelect: (ComicItem) -> Unit,
    onDelete: (Comic) -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
    ) {
        row.forEach { comic ->
            Box(Modifier.weight(1f)) {
                NetComicCard(comic = comic, sourceName = sourceName,
                    onClick = { onSelect(comic.toComicItem()) },
                    onLongClick = { onDelete(comic) },
                )
            }
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NetComicDetailedCard(comic: Comic, sourceName: String, onSelect: (ComicItem) -> Unit, onDelete: () -> Unit) {
    val maskState = netMaskState(comic)
    ComicTileDetailed(
        title = comic.title,
        coverUrl = comic.cover,
        subtitle = comic.subTitle,
        description = comic.description,
        tags = comic.tags,
        rating = comic.rating?.toDouble(),
        likesCount = comic.likesCount,
        badge = sourceName,
        coverMaskState = maskState,
        onClick = { onSelect(comic.toComicItem()) },
        onLongClick = onDelete,
    )
}

@Composable
private fun NetComicCard(comic: Comic, sourceName: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val tokens = VeneraTokens
    val maskState = netMaskState(comic)
    VeneraCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        com.venera.compose.components.venera.VeneraCover(
            url = comic.cover,
            contentDescription = comic.title,
            shimmerWhileLoading = false,
            mask = if (maskState == "VISIBLE") VeneraCoverMask.Visible else VeneraCoverMask.Masked,
        ) {
            VeneraSourceBadge(name = sourceName)
        }
        Spacer(Modifier.height(tokens.spacing.cardCoverGap))
        Text(
            text = comic.title,
            fontSize = tokens.type.caption,
            fontWeight = tokens.type.weightSemibold,
            color = tokens.color.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val visibleTags = com.venera.compose.feature.searchVisibleTags(comic.tags, emptyList())
        if (visibleTags.isNotEmpty()) {
            Spacer(Modifier.height(tokens.spacing.space1))
            Text(
                text = visibleTags.take(2).joinToString(" · "),
                fontSize = tokens.type.overline,
                color = tokens.color.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 内容守卫：BLUR 命中打码（源级预设 + 用户规则）；HIDE 已在数据层剔除、此处兜底。 */
@Composable
private fun netMaskState(comic: Comic): String {
    val guard = com.venera.compose.security.guard.ContentGuardManager.getInstance(LocalContext.current)
    return guard.coverMaskStateFor(comic)
}

@Composable
private fun LoadMoreFooter(isLoading: Boolean) {
    val tokens = VeneraTokens
    Box(
        Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space5),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(tokens.spacing.loaderInline),
                color = tokens.color.primary,
                trackColor = tokens.color.surfaceVariant,
            )
        } else {
            Text(
                text = "上滑加载更多",
                fontSize = tokens.type.overline,
                color = tokens.color.textTertiary,
            )
        }
    }
}

/** 长按移除确认弹窗（系统级 Dialog：独立窗口层级，绝无被遮挡/不渲染的可能）。 */
@Composable
private fun NetRemoveConfirmDialog(comicTitle: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val tokens = VeneraTokens
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        VeneraCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.space10),
        ) {
            Text(
                text = "移除漫画",
                fontSize = tokens.type.itemTitle,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textPrimary,
            )
            Spacer(Modifier.height(tokens.spacing.space5))
            Text(
                text = "是否从网络收藏夹中移除《" + comicTitle + "》？",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
            )
            Spacer(Modifier.height(tokens.spacing.space7))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space5)) {
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
                        color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)
                    ),
                ) { Text("取消") }
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
                        color = StatusColors.Failing
                    ),
                ) { Text("移除", color = androidx.compose.ui.graphics.Color.White) }
            }
        }
    }
}

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