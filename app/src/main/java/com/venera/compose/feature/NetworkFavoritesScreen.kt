/**
 * 网络收藏夹 —— 手风琴原地展开交互（Stage 0~2 信息架构升级）。
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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.venera.compose.components.ComicLayoutToggleButton
import com.venera.compose.components.ComicRowCard
import com.venera.compose.components.ComicSharedTransition
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.comicListColumnCount
import com.venera.compose.components.rememberContentWidth
import com.venera.compose.components.rememberComicListDisplayMode
import com.venera.compose.components.selection.MultiSelectState
import com.venera.compose.components.selection.SelectableCardFrame
import com.venera.compose.components.selection.VeneraMultiSelectBar
import com.venera.compose.components.selection.MultiSelectBarAction
import com.venera.compose.components.selection.rememberMultiSelectState
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraFilterPill
import com.venera.compose.components.venera.VeneraSourceBadge
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

@Composable
fun AndroidNetworkFavoritesScreen(
    onSelect: (ComicItem) -> Unit,
    scrollConnection: NestedScrollConnection? = null,
    backdrop: LayerBackdrop? = null,
    topPadding: Dp = 0.dp,
    /** 把内部列表的滚动位置上抛（是否已下滑 / 回到顶部动作），供外壳渲染顶置按钮。 */
    onScrollStateChange: (canScrollUp: Boolean, hasScrolled: Boolean, scrollToTop: () -> Unit) -> Unit = { _, _, _ -> },
    /** 收藏页外壳已把布局切换收纳为右上角悬浮小钮，此处让位不再内联；其他入口默认保留。 */
    showLayoutToggle: Boolean = true,
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
    val (gridWidth, gridWidthModifier) = rememberContentWidth(tokens.spacing.rowHorizontal * 2)
    val columns = comicListColumnCount(displayMode.value, gridWidth)
    val isDetailed = displayMode.value == "detailed"
    /**
     * 多选状态机：与另外三处收藏面板**同一份**（见 `components/selection/`）。
     *
     * 键取 `(sourceKey, id)` —— 网络收藏一面屏只显示一个源，id 本来唯一；
     * 带上 sourceKey 是为了将来"一面墙混排多源"时不用回头改键。
     */
    val multi = rememberMultiSelectState<Pair<String, String>>()
    /** 待确认的批量移除；true = 弹层已开。 */
    var deleteConfirm by remember { mutableStateOf(false) }
    // 系统返回先退多选，不要把整个收藏 tab 弹掉。
    BackHandler(enabled = multi.active) { multi.exit() }

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
    // 当前屏上这一批的顺序，区间选要用它。
    val shownKeys = remember(shownComics) { shownComics.map { it.selectionKey() } }
    // 卡片动作收口成两个局部函数：brief 与 detailed 两种行都要同一份，
    // 各写一遍就会出现"改了一种忘了另一种"。
    val onToggleCard: (Comic) -> Unit = { multi.toggle(it.selectionKey()) }
    val onLongPressCard: (Comic) -> Unit = { comic ->
        // 不在多选态 = 进入多选并选中；已在多选态 = 区间反选（官方语义）。
        if (multi.active) multi.toggleRange(comic.selectionKey(), shownKeys) else multi.enter(comic.selectionKey())
    }
    // 面板内浮层工具条要有个 Box 才能 align —— PullToRefreshBox 自己不是 BoxScope，
    // 在外面套一层是最小改动（它内部那些下拉手势与嵌套滚动原样保留）。
    Box(modifier = Modifier.fillMaxSize()) {
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
            .then(gridWidthModifier)
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

        // ── 顶部横向源切换栏（+ 可选内联单双列切换）。──
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
                if (showLayoutToggle) {
                    ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
                }
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
                // 宽屏下详细模式同样加列（master：每 360dp 一列），行级挂载与 brief 同构
                shownComics.chunked(columns).forEachIndexed { rowIndex, row ->
                    item(key = "nfrow-" + rowIndex + "-" + (row.firstOrNull()?.id ?: "")) {
                        ComicDetailedRow(
                            row = row,
                            columns = columns,
                            multi = multi,
                            onSelect = onSelect,
                            onToggleSelect = onToggleCard,
                            onLongPress = onLongPressCard,
                        )
                    }
                }
            }
            else -> {
                // 行级虚拟化：每行 = 一个 LazyItem，滑出屏幕立即回收。
                shownComics.chunked(columns).forEachIndexed { rowIndex, row ->
                    item(key = "nrow-" + rowIndex + "-" + (row.firstOrNull()?.id ?: "")) {
                        ComicGridRow(
                            row = row,
                            columns = columns,
                            sourceName = current.name,
                            multi = multi,
                            onSelect = onSelect,
                            onToggleSelect = onToggleCard,
                            onLongPress = onLongPressCard,
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

        if (multi.active) {
            // 浮层工具条，与插图收藏 / 画廊收藏那两条同款同位置。
            VeneraMultiSelectBar(
                selectedCount = multi.count,
                allSelected = shownKeys.isNotEmpty() && multi.count == shownKeys.size,
                onExit = { multi.exit() },
                onSelectAll = { multi.selectAll(shownKeys) },
                onInvert = { multi.invert(shownKeys) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = tokens.spacing.rowHorizontal,
                        end = tokens.spacing.rowHorizontal,
                        bottom = tokens.spacing.space8 + VeneraSpacing.bottomBarClearance,
                    ),
            ) {
                MultiSelectBarAction(
                    icon = Icons.Outlined.Delete,
                    label = "移除",
                    destructive = true,
                    onClick = { deleteConfirm = true },
                )
            }
        }
    }

    // 批量移除确认弹窗：破坏性操作必须二次确认
    //（站方收藏夹删了就是删了，本应用没有撤销入口）。
    if (deleteConfirm) {
        val doomed = vm.comics.filter { it.selectionKey() in multi.selected }
        NetRemoveConfirmDialog(
            count = doomed.size,
            onDismiss = { deleteConfirm = false },
            onConfirm = {
                deleteConfirm = false
                vm.deleteComics(doomed.map { it.id }, currentFolder ?: "")
                multi.exit()
            },
        )
    }
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
            VeneraFilterPill(
                text = src.name,
                selected = src.key == selectedKey,
                logged = src.logged,
                onClick = { onSelect(src.key) },
            )
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
        // 只有 0 或 1 个文件夹时整行不渲染：单个「全部」pill 不提供任何选择，
        // 挂在源栏下方就像多出来的一块（用户真机反馈）。
        folders == null || folders.size <= 1 -> {}
        else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            items(folders.toList(), key = { it.first }) { (id, name) ->
                // 与源栏同一套 VeneraFilterPill：文件夹胶囊样式与源药丸完全一致。
                VeneraFilterPill(
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

/** 一行 N 个等宽条目；不足 N 个补空位，避免尾行被拉伸。一个 Row 就是一个 LazyItem。 */
@Composable
private fun ComicGridRow(
    row: List<Comic>,
    columns: Int,
    sourceName: String,
    multi: MultiSelectState<Pair<String, String>>,
    onSelect: (ComicItem) -> Unit,
    onToggleSelect: (Comic) -> Unit,
    onLongPress: (Comic) -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
    ) {
        row.forEach { comic ->
            val key = comic.selectionKey()
            Box(Modifier.weight(1f)) {
                NetComicCard(
                    comic = comic,
                    sourceName = sourceName,
                    selecting = multi.active,
                    selected = multi.contains(key),
                    // 多选态下点整张卡 = 勾选（只让人去够右上角那个小圈太费劲）。
                    onClick = { if (multi.active) onToggleSelect(comic) else onSelect(comic.toComicItem()) },
                    onToggleSelect = { onToggleSelect(comic) },
                    onLongClick = { onLongPress(comic) },
                )
            }
        }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** 详细模式的行卡同样按列数并排（卡片自身已是整行形态，这里只做等宽切分）。 */
@Composable
private fun ComicDetailedRow(
    row: List<Comic>,
    columns: Int,
    multi: MultiSelectState<Pair<String, String>>,
    onSelect: (ComicItem) -> Unit,
    onToggleSelect: (Comic) -> Unit,
    onLongPress: (Comic) -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
    ) {
        row.forEach { comic ->
            val key = comic.selectionKey()
            Box(Modifier.weight(1f)) {
                NetComicDetailedCard(
                    comic = comic,
                    selecting = multi.active,
                    selected = multi.contains(key),
                    onSelect = onSelect,
                    onToggleSelect = { onToggleSelect(comic) },
                    onLongClick = { onLongPress(comic) },
                )
            }
        }
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun NetComicDetailedCard(
    comic: Comic,
    selecting: Boolean,
    selected: Boolean,
    onSelect: (ComicItem) -> Unit,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
) {
    val maskState = netMaskState(comic)
    // 打码命中不飞行；两端比例同为 0.72（行卡封面 = listCoverWidth + coverAspectRatio），
    // 所以 sharedElement 足够 —— 真机对照过：不同构时改挂 sharedBounds + RemeasureToBounds
    // 会在落地那一帧重测内容，观感就是「封面闪一下」。
    val coverModifier = Modifier.coverSharedElement(
        key = ComicSharedTransition.coverKey(comic.sourceKey, comic.id),
        allowFly = maskState == "VISIBLE",
    )
    SelectableCardFrame(
        selecting = selecting,
        selected = selected,
        onToggleSelect = onToggleSelect,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 与搜索页单列、本地收藏单列共用 components.ComicRowCard —— 全应用一种行卡形态。
        // 原先的右上角源名徽章不再传：搜索页行卡本来就没这个元素，且源名已在手风琴分区标题上。
        ComicRowCard(
            title = comic.title,
            coverUrl = comic.cover,
            subtitle = comic.subTitle,
            description = comic.description,
            tags = comic.tags,
            mask = if (maskState == "VISIBLE") VeneraCoverMask.Visible else VeneraCoverMask.Masked,
            onClick = { if (selecting) onToggleSelect() else onSelect(comic.toComicItem()) },
            onLongClick = onLongClick,
            likesCount = comic.likesCount,
            rating = comic.rating,
            updateTime = comic.updateTime,
            coverModifier = coverModifier,
        )
    }
}

@Composable
private fun NetComicCard(
    comic: Comic,
    sourceName: String,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val maskState = netMaskState(comic)
    SelectableCardFrame(
        selecting = selecting,
        selected = selected,
        onToggleSelect = onToggleSelect,
        modifier = Modifier.fillMaxWidth(),
    ) {
        VeneraCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            onLongClick = onLongClick,
        ) {
            com.venera.compose.components.venera.VeneraCover(
                url = comic.cover,
                contentDescription = comic.title,
                shimmerWhileLoading = false,
                // 双列同样参与飞行：源名徽章在封面的内容槽里，会随封面一起飞、落地后消失。
                modifier = Modifier.coverSharedElement(
                    key = ComicSharedTransition.coverKey(comic.sourceKey, comic.id),
                    allowFly = maskState == "VISIBLE",
                ),
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

/** 批量移除确认弹窗（系统级 Dialog：独立窗口层级，绝无被遮挡/不渲染的可能）。 */
@Composable
private fun NetRemoveConfirmDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
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
                text = "是否从网络收藏夹中移除选中的 $count 部漫画？",
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

/**
 * 多选用的身份键。
 *
 * 网络收藏一面屏只显示一个源，`id` 本来唯一；带上 `sourceKey` 是为了将来"一面墙混排多源"
 * 时不用回头改键 —— 两站的 id 各自编号，同 id 是两本不同的书。
 */
private fun Comic.selectionKey(): Pair<String, String> = sourceKey to id

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

