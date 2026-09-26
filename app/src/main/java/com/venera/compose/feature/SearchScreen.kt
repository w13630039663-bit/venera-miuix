package com.venera.compose.feature

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.data.prefs.AppearanceStyle
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.data.tags.rememberTagDisplayLabel
import com.venera.compose.ui.tokens.VeneraPreviewTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.venera.compose.components.ComicCardContextMenu
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.ComicRowCard
import com.venera.compose.components.ComicRowMetadata
import com.venera.compose.components.ComicSharedTransition
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.comicListColumnCount
import com.venera.compose.components.rememberComicListDisplayMode
import com.venera.compose.components.rememberContentWidth
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraSourceBadge
import com.venera.compose.components.venera.VeneraTagChip
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.SearchOptionGroup
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * 搜索页（SearchRoute 与 TagSearchRoute **共享同一套 UI**）。
 *
 * 两个 Route 的唯一差异：
 *  - [consumesBottomBarClearance]：SearchRoute 为 true（主 Tab，底栏显示），
 *    TagSearchRoute 为 false（详情页下钻，底栏不显示）。
 *  - [onNavigateBack]：TagSearchRoute 提供返回 affordance；SearchRoute 传 null（主 Tab 无需返回）。
 * 除此之外视觉与交互完全一致，不存在第二套布局。
 *
 * 设计原则（服从项目真实能力）：
 *  - Source-native capability 优先于统一假设：排序入口内容随当前源 optionList 变化，
 *    源未声明排序时不伪造能力。
 *  - 不制造不存在的业务能力：条件 BottomSheet 只提供 Tag；年份/语言/页数因
 *    数据模型无可靠支持而不显示（记录为 Search Capability 专项）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidSearchScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit,
    initialQuery: String = "",
    /**
     * 详情页点标签跳进来时携带的**所属漫画源显示名**；非空则先把搜索目标切到该源再搜。
     * 只传名字不传 key：DetailRoute 本身就只有 sourceName（见 Navigation.kt）。
     */
    initialSourceName: String = "",
    /**
     * 统计页题材云下钻携带的**结构化标签**（详情页点标签仍走 [initialQuery] 那条纯文本路径）。
     * 只有走 SearchTag，非原生标签语法源才会改吃客户端过滤而不是把 `tag:xxx` 当关键字。
     */
    initialTag: SearchTag? = null,
    viewModel: SearchViewModel = viewModel(),
    consumesBottomBarClearance: Boolean = true,
    onNavigateBack: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val density = LocalDensity.current
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val sources by viewModel.sourcesFlow.collectAsStateWithLifecycle()
    val groups by viewModel.searchOptions.collectAsStateWithLifecycle()
    val selected by viewModel.selectedOptions.collectAsStateWithLifecycle()
    var displayMode by rememberComicListDisplayMode()
    var showOptions by remember(ui.selectedSourceKey) { mutableStateOf(false) }
    var showConditions by remember { mutableStateOf(false) }

    val guard = ContentGuardManager.getInstance(LocalContext.current)
    // 一次性读偏好值（不订阅）：只作进入页面时的源预选，之后交给会话内选择。
    val defaultSearchTarget = VeneraPreferences.getInstance(LocalContext.current)
        .defaultSearchTarget.value
    val nsfwMode by guard.nsfwMaskMode.collectAsStateWithLifecycle()
    // 带 Comic 走守卫 LRU 判定（含源级预设）：jm/哔咔等整站源在搜索结果里同样整站打码。
    // 判定非 VISIBLE 一律打码 —— 原来只认 "BLURRED"，把 AI 屏蔽的 "HIDDEN" 映射成了**不打码**，
    // 与其余各页（VISIBLE 之外一律 Masked）口径不一致；列表未重新检索时就会露出封面。
    fun mask(comic: Comic): VeneraCoverMask =
        if (guard.coverMaskStateFor(comic) == "VISIBLE")
            VeneraCoverMask.Visible else VeneraCoverMask.Masked

    fun select(comic: Comic, sourceName: String) = onSelect(
        ComicItem(
            id = comic.id, title = comic.title, author = comic.subTitle, coverUrl = comic.cover,
            // 一律以 comic.sourceKey 为准：卡片那侧的共享元素 key 就是用它拼的，
            // 这里若塞显示名（聚合结果的 event.sourceName 就是显示名），两端 key
            // 对不上，飞行会静默失效 —— 不报错，只是什么都不飞。
            // 详情页吃 sourceKey 是已验证可用的口径（网络收藏/本地收藏都这么交）。
            sourceName = comic.sourceKey.ifBlank { sourceName },
            tags = comic.tags, description = comic.description,
            rating = comic.rating?.toString().orEmpty(), likesCount = comic.likesCount,
        )
    )

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showBackToTop by remember { mutableStateOf(false) }
    // 滚动位置存在 ViewModel 里：从详情页返回会重建整个组合，`rememberLazyListState()`
    // 没有恢复来源（导航条目没开 saveState），不补这一手就会跳回顶部。
    // 先 `remember` 取快照，再让记录器开始写 —— 否则记录器的首次发射会把待恢复值冲成 (0,0)。
    val pendingAnchor = remember { viewModel.listAnchor }
    LaunchedEffect(listState) {
        pendingAnchor?.let { (index, offset) ->
            if (index != 0 || offset != 0) listState.scrollToItem(index, offset)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { viewModel.listAnchor = it }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { showBackToTop = it > 10 }
    }
    // 统一顶栏：大标题折叠 + 毛玻璃（与其他页面保持一致）。
    // 搜索框仍留在列表首项（随滚动隐藏）；顶栏右侧搜索图标在搜索框滚出视口后淡入，
    // 提供不打断阅读的快捷重搜入口。
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()

    // 顶栏搜索图标的淡入进度：与 VeneraTopAppBar 的背板同一套 contentOffset/48dp 契约，
    // 保证「毛玻璃出现」与「快捷入口出现」在视觉上同步，不会一个先一个后。
    val searchActionAlpha = remember {
        derivedStateOf {
            val offset = topBarBehavior.state.contentOffset
            val thresholdPx = with(density) { 48.dp.toPx() }
            if (thresholdPx > 0f) (-offset / thresholdPx).coerceIn(0f, 1f) else 0f
        }
    }
    var showSearchDialog by remember { mutableStateOf(false) }
    // 单源搜索：滑到接近底部自动加载下一页。
    // 网格已逐行挂载（每行 = 一个 LazyItem），totalItemsCount 真实反映行数，
    // 倒数第 2 行进入可视区即触发，平滑且不会滥发请求。
    LaunchedEffect(listState, ui.canLoadMore, ui.results.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (ui.canLoadMore && !ui.isSearching && !ui.loadingMore &&
                ui.selectedSourceKey != SearchViewModel.KEY_ALL && total > 0 &&
                lastVisible >= total - 2
            ) viewModel.loadMore()
        }
    }
    LaunchedEffect(ui.selectedSourceKey) { viewModel.loadSearchOptions(ui.selectedSourceKey) }
    // 「默认搜索目标」：偏好只在首次进入时预选一次源，之后完全交给会话内的选择，
    // 不订阅后续变化、也不自动发起搜索。
    LaunchedEffect(Unit) {
        // 从详情页返回会重建整个组合，这个 effect 会再跑一遍 —— 不守卫就会把用户
        // 在会话里选的源改回默认目标并触发重搜（表现为「返回时搜索页又刷新」）。
        if (!viewModel.shouldApplyEntryParam("default-target")) return@LaunchedEffect
        val target = defaultSearchTarget
        if (target.isNotBlank()) {
            viewModel.sourcesFlow.value.find { it.key == target }?.let {
                viewModel.onSourceSelected(it.key, it.name)
            }
        }
    }
    // 详情页标签下钻：先把搜索目标切到那本漫画**所属的源**，再用同一个词搜。
    // 顺序不能反 —— search() 读的是 selectedSourceKey 的快照，先搜就会落在全网聚合上。
    // 名字在源列表里解不到就保持默认目标，绝不凭名字猜一个 key（同名异源是真实存在的）。
    LaunchedEffect(initialQuery, initialSourceName, initialTag) {
        // 一次性：入参只认第一次消费，组合重建（进详情再返回）时不得重搜。
        if (!viewModel.shouldApplyEntryParam("drill-down")) return@LaunchedEffect
        if (initialSourceName.isNotBlank()) {
            // 这个入参历史上不统一：从搜索页进详情时是**显示名**，从收藏页进详情时是
            // **sourceKey**（Comic.toComicItem / FavoriteItem.toComicItem 都填 sourceKey）。
            // 只按 name 匹配的话，收藏页 → 详情 → 点标签这条路的源预选会静默失效。
            sources.find { it.key == initialSourceName || it.name == initialSourceName }?.let {
                viewModel.onSourceSelected(it.key, it.name)
            }
        }
        // 先挂标签再搜：search() 取的是 tags 的快照，反过来这一发就不带标签了。
        // addTag 内部自带一次 search，所以纯标签下钻时**不能**再补一发（会双发请求、结果闪一下）。
        initialTag?.let { tag -> viewModel.addTag(raw = tag.raw, label = tag.label, namespace = tag.namespace) }
        if (initialQuery.isNotBlank()) viewModel.search(initialQuery)
    }

    if (showOptions) SearchOptionsSheet(
        sourceName = ui.selectedSourceLabel,
        aggregate = ui.selectedSourceKey == SearchViewModel.KEY_ALL,
        groups = groups, selected = selected, loading = ui.optionsLoading, error = ui.optionsError,
        onRetry = { viewModel.loadSearchOptions(ui.selectedSourceKey) },
        onDismiss = { showOptions = false },
        onApply = { values -> viewModel.applySearchOptions(ui.selectedSourceKey, values); showOptions = false },
    )
    if (showConditions) SearchConditionSheet(
        viewModel = viewModel,
        onDismiss = { showConditions = false },
    )

    // LazyColumn 的 content lambda 不是 @Composable，因此把 Token 提前取出。
    val tokenSet = tokens.current
    val colorSet = tokens.color

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 基础顶部间距 = 状态栏 + 折叠顶栏（约 104dp），与 SourceSectionScreen 同契约。
    val baseTopPadding = statusBarTop + 104.dp
    // 标签译文显示器：只改药丸文本，点击与请求仍走源生原文（见 rememberTagDisplayLabel 注释）。
    // 在屏幕顶层取一次再往下传，避免每张卡片各自订阅字典加载状态。
    val tagLabel = rememberTagDisplayLabel()

    // 结果网格列数按列表实测可用宽推（宽屏加列，master 口径），左右内边距要先扣掉
    val (listWidth, listWidthModifier) = rememberContentWidth(tokens.spacing.rowHorizontal * 2)
    val resultColumns = comicListColumnCount(displayMode, listWidth)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(listWidthModifier)
                .nestedScroll(topBarBehavior.nestedScrollConnection)
                .blurBackdropSource(topBarBackdrop),
            contentPadding = PaddingValues(
                start = tokens.spacing.rowHorizontal,
                end = tokens.spacing.rowHorizontal,
                top = baseTopPadding,
                // 统一契约：主 Tab 避让底栏；下钻子页仅保留自身滚动留白。
                bottom = if (consumesBottomBarClearance) VeneraSpacing.bottomBarClearance else tokens.spacing.space8,
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sectionGap),
        ) {
            item(key = "header") {
                SearchHeader(
                    query = ui.query,
                    hasConditions = ui.tags.isNotEmpty(),
                    onQueryChange = viewModel::onQueryChange,
                    onSubmit = { viewModel.search(ui.query) },
                    onClear = { viewModel.clearQuery() },
                )
            }

            // 常驻：即使还没有条件，也要有「+ 添加标签」入口。
            // （旧实现把它放在 AssistChip 常显；本轮改成条件区常驻，语义更清晰。）
            item(key = "conditions") {
                ActiveConditions(
                    tags = ui.tags,
                    onRemove = { viewModel.removeTag(it) },
                    onClearAll = { viewModel.clearQuery() },
                    onAdd = { showConditions = true },
                )
            }

            if (ui.history.isNotEmpty() && ui.query.isEmpty() && ui.tags.isEmpty()) {
                item(key = "history") {
                    SearchHistory(
                        history = ui.history,
                        onPick = { viewModel.search(it) },
                        onClear = viewModel::clearHistory,
                    )
                }
            }

            if (sources.isNotEmpty()) {
                item(key = "sources") {
                    SourceSelector(
                        sources = sources,
                        selectedKey = ui.selectedSourceKey,
                        allLabel = SearchViewModel.SOURCE_ALL_LABEL,
                        onSelect = { key, label -> viewModel.onSourceSelected(key, label) },
                    )
                }
            }

            item(key = "controls") {
                SearchControls(
                    groups = groups,
                    isAggregate = ui.selectedSourceKey == SearchViewModel.KEY_ALL,
                    optionsLoading = ui.optionsLoading,
                    optionsError = ui.optionsError,
                    displayMode = displayMode,
                    onToggleDisplayMode = { displayMode = it },
                    onOpenSort = { showOptions = true },
                )
            }

            ui.matchedUrlComic?.let { matched ->
                item(key = "url-match") {
                    VeneraCard(onClick = {
                        onSelect(
                            ComicItem(
                                id = matched.comicId, title = matched.comicId, author = "",
                                coverUrl = "", sourceName = matched.sourceName,
                            )
                        )
                    }) {
                        Text(
                            text = "识别到【" + matched.sourceName + "】漫画链接",
                            fontSize = tokens.type.body,
                            color = tokens.color.textPrimary,
                        )
                        Spacer(Modifier.height(tokens.spacing.space2))
                        Text(
                            text = "点击直达详情",
                            fontSize = tokens.type.caption,
                            color = tokens.color.primary,
                        )
                    }
                }
            }

            if (ui.tagSuggestions.isNotEmpty()) {
                item(key = "tag-suggest") {
                    // 联想面板：轻量底色 + 圆角，紧贴搜索框下方（吸顶感）
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(tokens.shape.medium),
                        color = tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(tokens.spacing.space4)) {
                            Text(
                                text = "标签联想",
                                fontSize = tokens.type.caption,
                                color = tokens.color.textSecondary,
                                modifier = Modifier.padding(bottom = tokens.spacing.space2),
                            )
                            FlowChips {
                                ui.tagSuggestions.forEach { (raw, label) ->
                                    // 有中文翻译时以「中文 (原文)」呈现，语义清晰；
                                    // 无翻译则直接用原文。namespace 交给源级回调解析。
                                    val display = if (label.isNotBlank() && !label.equals(raw, ignoreCase = true)) {
                                        label + " (" + raw + ")"
                                    } else raw
                                    VeneraTagChip(
                                        text = display,
                                        onClick = { viewModel.selectTagSuggestion(raw = raw, label = label, namespace = "") },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (ui.selectedSourceKey == SearchViewModel.KEY_ALL) {
                this@LazyColumn.AggregatedResults(
                    ui = ui,
                    tokens = tokenSet,
                    colors = colorSet,
                    mask = ::mask,
                    displayMode = displayMode,
                    tagLabel = tagLabel,
                    onSelect = { comic, sourceName -> select(comic, sourceName) },
                    onOpenSource = { key, label -> viewModel.onSourceSelected(key, label) },
                    onRetry = { viewModel.search(ui.query) },
                )
            } else {
                this@LazyColumn.SingleSourceResults(
                    ui = ui,
                    tokens = tokenSet,
                    colors = colorSet,
                    displayMode = displayMode,
                    columns = resultColumns,
                    tagLabel = tagLabel,
                    mask = ::mask,
                    onSelect = { select(it, ui.selectedSourceLabel) },
                    onRetry = { viewModel.search(ui.query) },
                    onLoadMore = { viewModel.loadMore() },
                )
            }

            item(key = "tail") { Spacer(Modifier.height(tokens.spacing.space2)) }
        }

        // 回到顶部：位置基于契约计算，不手工猜底栏高度。
        SearchBackToTop(
            visible = showBackToTop,
            modifier = Modifier.align(Alignment.BottomEnd),
            bottomOffset = if (consumesBottomBarClearance) {
                VeneraSpacing.bottomBarClearance + VeneraSpacing.space9
            } else {
                VeneraSpacing.space10
            },
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
        )

        // 顶栏浮层：大标题折叠 + 毛玻璃。导航图标仅在子页面（下钻）出现，
        // 与列表内搜索框的返回按钮保持同一条可见性契约（主 Tab 不产生重复返回）。
        VeneraTopAppBar(
            title = "搜索",
            largeTitle = "搜索",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                if (onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = tokens.color.textPrimary,
                        )
                    }
                }
            },
            actions = {
                // 搜索框滚出视口后淡入，避免与列表首项里的搜索框视觉重复。
                val actionAlpha by searchActionAlpha
                if (actionAlpha > 0.01f) {
                    IconButton(
                        onClick = { showSearchDialog = true },
                        modifier = Modifier
                            .size(tokens.spacing.iconButtonSize)
                            .graphicsLayer { alpha = actionAlpha },
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "搜索",
                            tint = tokens.color.primary,
                        )
                    }
                }
            },
        )

        // 快捷搜索弹窗：复用同一份查询状态与源选择，确认后回到顶部展示新结果。
        if (showSearchDialog) {
            SearchQuickDialog(
                query = ui.query,
                onQueryChange = viewModel::onQueryChange,
                sourceLabel = ui.selectedSourceLabel,
                onDismiss = { showSearchDialog = false },
                onConfirm = {
                    showSearchDialog = false
                    viewModel.search(ui.query)
                    scope.launch { listState.animateScrollToItem(0) }
                },
            )
        }
    }
}

/**
 * 顶栏快捷搜索弹窗。
 *
 * 刻意只做「输入 + 确认」两件事：源选择与高级筛选仍由页面内的 SourceSelector /
 * SearchControls 负责，避免同一个能力出现两套入口而行为不一致。
 * 输入直接写回 viewModel.query，与列表内搜索框共享同一份状态。
 */
@Composable
private fun SearchQuickDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    sourceLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val tokens = VeneraTokens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "搜索",
                fontSize = tokens.type.itemTitle,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = "当前源：" + sourceLabel,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(tokens.spacing.space4))
                Surface(
                    shape = RoundedCornerShape(tokens.shape.large),
                    color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
                    modifier = Modifier.fillMaxWidth().height(tokens.spacing.searchFieldHeight),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = tokens.spacing.fieldHorizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = tokens.color.textTertiary,
                            modifier = Modifier.size(tokens.spacing.chipIconSize),
                        )
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "搜索作品、作者或标签",
                                    fontSize = tokens.type.body,
                                    color = tokens.color.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = tokens.type.body,
                                    color = tokens.color.textPrimary,
                                ),
                                cursorBrush = SolidColor(tokens.color.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onConfirm() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                text = "搜索",
                fontSize = tokens.type.body,
                fontWeight = tokens.type.weightMedium,
                color = tokens.color.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(tokens.shape.small))
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = tokens.spacing.space5, vertical = tokens.spacing.space3),
            )
        },
        dismissButton = {
            Text(
                text = "取消",
                fontSize = tokens.type.body,
                color = tokens.color.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(tokens.shape.small))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = tokens.spacing.space5, vertical = tokens.spacing.space3),
            )
        },
    )
}
/* ================================================================== *
 * Search 私有 UI 组件
 *
 * 刻意保持 private：除 Search 外无页面需要，不提升到 components/。
 * ================================================================== */

/**
 * 搜索头部：输入框 + 清空。
 *
 * 返回 affordance 由 VeneraTopAppBar 承担（常驻、不随列表滚走），因此本组件不再渲染返回，
 * 避免同一屏出现两个返回按钮。
 *
 * 输入框用 BasicTextField 自绘而非 M3 OutlinedTextField，原因：
 *  - M3 TextField 携带完整 Material 交互语义（容器色、label 动画、指示线），
 *    与统一 Venera 表面冲突；
 *  - 自绘可让 placeholder / focus / 圆角完全走 Token。
 */
@Composable
private fun SearchHeader(
    query: String,
    hasConditions: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        Surface(
            shape = RoundedCornerShape(tokens.shape.large),
            color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
            modifier = Modifier.weight(1f).height(tokens.spacing.searchFieldHeight),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = tokens.spacing.fieldHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = tokens.color.textTertiary,
                    modifier = Modifier.size(tokens.spacing.chipIconSize),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索作品、作者或标签",
                            fontSize = tokens.type.body,
                            color = tokens.color.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = tokens.type.body,
                            color = tokens.color.textPrimary,
                        ),
                        cursorBrush = SolidColor(tokens.color.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty() || hasConditions) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "清空搜索条件",
                        tint = tokens.color.textTertiary,
                        modifier = Modifier
                            .size(tokens.spacing.chipIconSize)
                            .clickable(onClick = onClear),
                    )
                }
            }
        }
    }
}

/** 轻量图标动作按钮（替代 TextButton 的图标场景）。 */
@Composable
private fun SearchIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tokens.color.textPrimary,
        modifier = Modifier
            .size(tokens.spacing.iconButtonSize)
            .clip(RoundedCornerShape(tokens.shape.small))
            .clickable(onClick = onClick)
            .padding(tokens.spacing.space5),
    )
}

/** 轻量文字动作（替代 TextButton）。 */
@Composable
private fun SearchTextAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.caption,
        fontWeight = tokens.type.weightMedium,
        color = if (enabled) tokens.color.primary else tokens.color.textDisabled,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(tokens.shape.extraSmall))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = tokens.spacing.space3, vertical = tokens.spacing.space2),
    )
}

/** 自动换行的 Chip 容器（避免直接依赖 M3 FlowRow 的语义）。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    val tokens = VeneraTokens
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

/**
 * 当前生效的搜索条件。
 *
 * 搜索源**不是**条件（Source identity ≠ Tag identity），因此不在本区展示。
 */
@Composable
private fun ActiveConditions(
    tags: List<SearchTag>,
    onRemove: (Int) -> Unit,
    onClearAll: () -> Unit,
    onAdd: () -> Unit,
) {
    val tokens = VeneraTokens
    Column {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "搜索条件",
                fontSize = tokens.type.caption,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textSecondary,
                modifier = Modifier.weight(1f),
            )
            // 没有条件时不显示「清除全部」，避免无意义的可点项
            if (tags.isNotEmpty()) {
                SearchTextAction(text = "清除全部", onClick = onClearAll)
            }
        }
        FlowChips {
            tags.forEachIndexed { index, tag ->
                VeneraTagChip(
                    text = listOf(tag.namespace, tag.label)
                        .filter { it.isNotBlank() }
                        .joinToString(":"),
                    selected = true,
                    onClick = { onRemove(index) },
                )
            }
            // 与旧版「＋ 添加标签」等价的常驻入口。
            // 使用 Tag variant（实底 surfaceVariant）而非 Assist（透明底 + 细描边）：
            // 后者在 MIUIX/MD3 某些配色下 outline 与表面色过于接近，按钮几乎看不见 ——
            // 这正是"添加入口消失"报告的真实成因。视觉更重，但语义仍是同一组件。
            VeneraChip(
                text = "添加标签",
                leadingIcon = Icons.Outlined.Add,
                onClick = onAdd,
                variant = VeneraChipVariant.Tag,
            )
        }
    }
}

/** 搜索历史：最多 5 条，无内容时不渲染（调用方已保证）。 */
@Composable
private fun SearchHistory(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    val tokens = VeneraTokens
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "最近搜索",
                fontSize = tokens.type.caption,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textSecondary,
                modifier = Modifier.weight(1f),
            )
            SearchTextAction(text = "清空", onClick = onClear)
        }
        FlowChips {
            // 只展示最近 5 条；不新增独立历史页面。
            history.take(5).forEach { keyword ->
                VeneraChip(text = keyword, onClick = { onPick(keyword) })
            }
        }
    }
}
/**
 * 漫画源选择器。
 *
 * 与 [VeneraSourceBadge] 的区别是刻意的：
 *  - SourceBadge 用于卡片封面左上角的身份标记（overlay）；
 *  - 本组件是**选择器**，用 VeneraChip 承载，选中态用强调容器色。
 * 二者语义不同，因此不共用实现。
 *
 * 横向滚动由实际内容宽度决定，不限制"最多几个"。
 */
@Composable
private fun SourceSelector(
    sources: List<com.venera.compose.source.ComicSource> ,
    selectedKey: String,
    allLabel: String,
    onSelect: (String, String) -> Unit,
) {
    val tokens = VeneraTokens
    Column {
        Text(
            text = "漫画源",
            fontSize = tokens.type.caption,
            fontWeight = tokens.type.weightSemibold,
            color = tokens.color.textSecondary,
            modifier = Modifier.padding(bottom = tokens.spacing.space2),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            itemsIndexed(sources, key = { _, s -> "src-" + s.key }) { _, source ->
                VeneraChip(
                    text = source.name,
                    selected = selectedKey == source.key,
                    onClick = { onSelect(source.key, source.name) },
                )
            }
            item(key = "src-all") {
                // 全网聚合置于末尾，视觉上不抢具体源的注意力
                VeneraChip(
                    text = allLabel,
                    selected = selectedKey == SearchViewModel.KEY_ALL,
                    onClick = { onSelect(SearchViewModel.KEY_ALL, allLabel) },
                )
            }
        }
    }
}

/**
 * 排序与视图控制。
 *
 * 排序入口**不写死三档**：内容完全来自当前源的 optionList。
 *  - 全网聚合：无统一排序语义 -> 入口禁用（不伪造）。
 *  - 当前源无 optionList -> 入口禁用（不伪造）。
 *  - 有 optionList -> 可点击，打开该源的排序/高级筛选。
 */
@Composable
private fun SearchControls(
    groups: List<SearchOptionGroup>,
    isAggregate: Boolean,
    optionsLoading: Boolean,
    optionsError: String?,
    displayMode: String,
    onToggleDisplayMode: (String) -> Unit,
    onOpenSort: () -> Unit,
) {
    val tokens = VeneraTokens
    /*
     * 排序入口**始终可点击**。
     *
     * 为什么不是"无能力就禁用"：旧实现的入口始终可用，点开后由面板本身说明情况
     * （全网聚合 / 正在读取 / 读取失败 / 该源未声明选项）。禁用的灰色按钮既不告诉
     * 用户"为什么不能点"，也让人以为整个功能被删掉了 —— 那正是本次回归的成因。
     *
     * 仍然不伪造排序能力：面板内容**完全**来自当前源的 optionList，
     * 无选项时展示的是说明而不是假的 相关度/最新/热度。
     */
    val sortAvailable = true
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        // 高级选择 / 排序入口：真实能力驱动。
        // 使用 VeneraChip(Tag variant) 实底呈现 —— 半透明 Surface + 细描边在某些配色下
        // 几乎不可见，这正是"高级选择入口消失"报告的真实成因。
        // 文案沿用原实现的语义（排序与高级筛选），不重新设计功能。
        VeneraChip(
            text = "排序与高级筛选",
            leadingIcon = Icons.Filled.Tune,
            onClick = onOpenSort,
            enabled = sortAvailable,
            variant = VeneraChipVariant.Tag,
        )
        Spacer(Modifier.weight(1f))
        // 视图切换：Grid（默认）/ List
        SearchIconAction(
            icon = if (displayMode == "detailed") Icons.Outlined.GridView else Icons.Outlined.ViewAgenda,
            contentDescription = if (displayMode == "detailed") "切换网格" else "切换列表",
            onClick = { onToggleDisplayMode(if (displayMode == "detailed") "brief" else "detailed") },
        )
    }
}

/**
 * 「结果未按所选标签精确过滤」提示。
 *
 * 只在严格 AND 过滤会把整页清空、于是退回未过滤结果时出现。不写这句就等于把
 * 「没过滤」伪装成「过滤后就这么多样」，是假信息；写出来用户至少知道该换源或改关键词。
 */
@Composable
private fun TagFilterRelaxedNotice() {
    val tokens = VeneraTokens
    Text(
        text = "所选标签在该源的结果里没有对得上的项，以下结果没有按标签精确过滤。" +
            "（多数源的列表项不带完整标签；繁简写法不同也判为不匹配。）",
        fontSize = tokens.type.overline,
        lineHeight = tokens.type.caption * 1.5f,
        color = tokens.color.textSecondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 结果区标题：「已加载 N · 约 M」，源不声明页数时显示「总数未知」。
 *
 * 「约」不能省：33 个源里 **0 个** 往外返回条目总数（jm 内部有 `data.total`，但它的
 * `search.load` 只吐 `maxPage`），所以 M 只能用 `maxPage × 首页条数` 估，
 * 而且只会高估不会低估。编一个看起来精确的数比不显示更糟。
 */
@Composable
private fun ResultHeader(loaded: Int, estimatedTotal: Int?) {
    val tokens = VeneraTokens
    Text(
        text = "检索结果（已加载 $loaded 个" +
            (if (estimatedTotal != null) " · 约 $estimatedTotal 个" else " · 总数未知") + "）",
        fontSize = tokens.type.itemTitle,
        fontWeight = tokens.type.weightSemibold,
        color = tokens.color.textPrimary,
    )
}
/* ================================================================== *
 * 结果区
 * ================================================================== */

/** 全网聚合：按 Source 分组，保留 Source identity，绝不合并各源数据体系。 */
private fun androidx.compose.foundation.lazy.LazyListScope.AggregatedResults(
    ui: SearchUiState,
    tokens: com.venera.compose.ui.tokens.VeneraTokenSet,
    colors: com.venera.compose.ui.tokens.VeneraColorTokens,
    mask: (Comic) -> VeneraCoverMask,
    displayMode: String,
    tagLabel: (String) -> String,
    onSelect: (Comic, String) -> Unit,
    onOpenSource: (String, String) -> Unit,
    onRetry: () -> Unit,
) {
    if (ui.aggregatedResults.isEmpty()) {
        when {
            ui.isSearching -> item(key = "agg-loading") { SearchLoadingIndicator() }
            ui.hasSearched -> item(key = "agg-empty") {
                VeneraEmptyView(
                    title = "没有可用的搜索源",
                    message = "请先在漫画源管理中启用至少一个源",
                )
            }
            else -> item(key = "agg-idle") {
                VeneraEmptyView(
                    title = "开始搜索",
                    message = "输入关键词，或从上方选择一个漫画源",
                )
            }
        }
        return
    }
    // 注意：本函数是 LazyListScope 扩展（非 @Composable），
    // 所有 Composable 调用都必须包在 item { } / items { } 里。

    ui.aggregatedResults.values.forEach { event ->
        // 每个 item 必须有跨源唯一 key，否则不同源的行会互相复用导致内容错位。
        item(key = "src:" + event.sourceKey) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.sourceName,
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightSemibold,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (event.comics.isNotEmpty()) {
                        SearchTextAction(
                            text = "查看全部",
                            onClick = { onOpenSource(event.sourceKey, event.sourceName) },
                        )
                    }
                }
                // 该源的卡片标签对不上所选标签、已退回未过滤结果 —— 必须说出来，
                // 否则这一栏看起来像是"过滤后只剩这些"。
                if (event.tagFilterRelaxed) TagFilterRelaxedNotice()
                // 单源失败只影响该源，不把整页拖进错误态。
                when {
                    event.error != null -> VeneraEmptyView(
                        message = event.error,
                        actionText = "重试",
                        onAction = onRetry,
                    )
                    event.isLoading -> SearchLoadingIndicator(compact = true)
                    event.comics.isEmpty() -> VeneraEmptyView(
                        message = "该源未找到相关漫画",
                    )
                    else -> LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
                    ) {
                        items(event.comics, key = { it.id }) { comic ->
                            Box(Modifier.width(tokens.spacing.comicCardMinWidth)) {
                                SearchResultCard(
                                    comic = comic,
                                    sourceName = event.sourceName,
                                    mask = mask(comic),
                                    tagLabel = tagLabel,
                                    onClick = { onSelect(comic, event.sourceName) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 单源结果：网格 / 列表 + 分页加载。 */
private fun androidx.compose.foundation.lazy.LazyListScope.SingleSourceResults(
    ui: SearchUiState,
    tokens: com.venera.compose.ui.tokens.VeneraTokenSet,
    colors: com.venera.compose.ui.tokens.VeneraColorTokens,
    displayMode: String,
    columns: Int,
    tagLabel: (String) -> String,
    mask: (Comic) -> VeneraCoverMask,
    onSelect: (Comic) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val detailed = displayMode == "detailed"

    when {
        // 首次加载：居中波浪环（原先是两张 shimmer 空卡，看着像真搜到了空漫画）
        ui.isSearching && ui.results.isEmpty() -> item(key = "ss-loading") { SearchLoadingIndicator() }

        ui.error != null && ui.results.isEmpty() -> item(key = "ss-error") {
            VeneraEmptyView(
                title = "搜索失败",
                message = ui.error,
                actionText = "重试",
                onAction = onRetry,
            )
        }

        ui.results.isEmpty() && ui.hasSearched -> item(key = "ss-empty") {
            VeneraEmptyView(
                title = "未找到相关漫画",
                message = "试试调整关键词、标签或切换漫画源",
            )
        }

        ui.results.isEmpty() -> item(key = "ss-idle") {
            VeneraEmptyView(
                title = "开始搜索",
                message = "输入关键词，或从上方选择一个漫画源",
            )
        }

        else -> {
            item(key = "ss-header") {
                ResultHeader(loaded = ui.results.size, estimatedTotal = ui.estimatedTotal)
            }
            if (ui.tagFilterRelaxed) {
                item(key = "ss-tag-relaxed") { TagFilterRelaxedNotice() }
            }
            if (detailed) {
                // 详细模式同样加列（master：宽窗每 360dp 一列），行级挂载与网格分支同构
                ui.results.chunked(columns).forEachIndexed { rowIndex, row ->
                    item(key = "srowd-" + rowIndex + "-" + (row.firstOrNull()?.id ?: "")) {
                        ComicDetailedRow(
                            row = row,
                            columnCount = columns,
                            mask = mask,
                            tagLabel = tagLabel,
                            onSelect = onSelect,
                        )
                    }
                }
            } else {
                // 网格：与 Explore 相同的逐行挂载（对齐 Explore 的成熟做法）。
                // 列数在外层按可用宽度推导（与 GridCells.Adaptive 同语义），
                // 每行 = 一个独立 LazyItem，行级虚拟化回收——滑出屏幕立即释放，
                // 杜绝把成百上千张封面堆进单个普通 Column 导致的内存堆积与切页假死。
                ui.results.chunked(columns).forEachIndexed { rowIndex, row ->
                    item(key = "srow-" + rowIndex + "-" + (row.firstOrNull()?.id ?: "")) {
                        ComicGridRow(
                            row = row,
                            columnCount = columns,
                            mask = mask,
                            tagLabel = tagLabel,
                            onSelect = onSelect,
                        )
                    }
                }
            }
            // 翻页落点：触底自动加载（见上方 LaunchedEffect）保留，但**必须可见** ——
            // 只有自动触发时，用户不知道下面还有货，也不知道自己为什么停住了。
            if (ui.loadingMore) {
                item(key = "ss-more") {
                    SearchLoadingIndicator(compact = true, label = "正在加载更多…")
                }
            } else if (ui.canLoadMore) {
                item(key = "ss-more-button") {
                    Surface(
                        shape = RoundedCornerShape(tokens.shape.small),
                        color = colors.surfaceVariant.copy(alpha = tokens.selectedSurfaceAlpha),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLoadMore() },
                    ) {
                        Text(
                            text = "加载更多",
                            fontSize = tokens.type.caption,
                            color = colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = tokens.spacing.space5),
                        )
                    }
                }
            }
            // 加载更多失败的原因原先被吞掉：ui.error 只在 results 为空时渲染（上面的 ss-error 分支），
            // 于是「已经有一屏结果、再翻页失败」这种情况界面上什么都不说。
            ui.error?.let { reason ->
                if (ui.results.isNotEmpty() && !ui.isSearching) {
                    item(key = "ss-more-error") {
                        VeneraEmptyView(
                            message = reason,
                            actionText = "重试",
                            onAction = onLoadMore,
                        )
                    }
                }
            }
            // 到底提示。源声明了 maxPage 时这个判定是**精确**的（VM 用 nextPage < maxPage
            // 算 canLoadMore，不会再发一次注定为空的请求）；游标型源只能在某页返回空之后确定。
            // 有翻页错误时不显示 —— 那不是"没有了"，是"失败了"，上面已经说了。
            // 结果本来为空时也不显示：那一行说的是"没有搜索结果"，再补一句"已经没有了"是重复。
            if (!ui.canLoadMore && !ui.loadingMore && ui.hasSearched &&
                ui.error == null && ui.results.isNotEmpty()
            ) {
                item(key = "ss-end") {
                    Text(
                        text = "已经没有了",
                        fontSize = tokens.type.overline,
                        color = colors.textTertiary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = tokens.spacing.space6),
                    )
                }
            }
        }
    }
}

/** 网格中的一行：等宽卡片 + 尾部补空，保证最后一行左对齐。 */
@Composable
private fun ComicGridRow(
    row: List<Comic>,
    columnCount: Int,
    mask: (Comic) -> VeneraCoverMask,
    tagLabel: (String) -> String,
    onSelect: (Comic) -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
    ) {
        row.forEach { comic ->
            Box(Modifier.weight(1f)) {
                SearchResultCard(
                    comic = comic,
                    sourceName = comic.sourceKey,
                    mask = mask(comic),
                    tagLabel = tagLabel,
                    onClick = { onSelect(comic) },
                )
            }
        }
        // 末行不足时用等宽 Spacer 占位，避免卡片被拉伸成不等宽
        repeat(columnCount - row.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** 详细模式的行卡按列数并排；补位规则与 [ComicGridRow] 一致。 */
@Composable
private fun ComicDetailedRow(
    row: List<Comic>,
    columnCount: Int,
    mask: (Comic) -> VeneraCoverMask,
    tagLabel: (String) -> String,
    onSelect: (Comic) -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
    ) {
        row.forEach { comic ->
            Box(Modifier.weight(1f)) {
                SearchResultRowItem(
                    comic = comic,
                    mask = mask(comic),
                    tagLabel = tagLabel,
                    onClick = { onSelect(comic) },
                )
            }
        }
        repeat(columnCount - row.size) {
            Spacer(Modifier.weight(1f))
        }
    }
}
/* ================================================================== *
 * 结果卡片与状态
 * ================================================================== */

/**
 * 漫画结果卡。
 *
 * 信息优先级：标题 > 作者·来源 > 可选 Metadata > 最多两个 Tag。
 * 缺失字段直接不渲染（不显示 0 likes / — / N/A / 空占位）。
 */
@Composable
private fun SearchResultCard(
    comic: Comic,
    sourceName: String,
    mask: VeneraCoverMask,
    tagLabel: (String) -> String,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    var cardMenu by remember { mutableStateOf(false) }
    VeneraCard(onClick = onClick, onLongClick = { cardMenu = true }) {
        ComicCardContextMenu(
            comic = comic,
            expanded = cardMenu,
            onDismiss = { cardMenu = false },
            onOpenDetail = onClick,
        )
        VeneraCover(
            url = comic.cover,
            contentDescription = comic.title,
            // 双列网格卡也参与飞行；打码命中不飞（见 Modifier.coverSharedElement）。
            modifier = Modifier.coverSharedElement(
                key = ComicSharedTransition.coverKey(comic.sourceKey, comic.id),
                allowFly = mask == VeneraCoverMask.Visible,
            ),
            mask = mask,
        ) {
            VeneraSourceBadge(name = sourceName.ifBlank { comic.sourceKey })
        }
        Spacer(Modifier.height(tokens.spacing.cardCoverGap))
        Text(
            text = comic.title,
            fontSize = tokens.type.caption,
            fontWeight = tokens.type.weightMedium,
            color = tokens.color.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val byline = listOf(comic.subTitle, sourceName)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
        if (byline.isNotBlank()) {
            Text(
                text = byline,
                fontSize = tokens.type.overline,
                color = tokens.color.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OptionalMetadata(comic)
        val tags = searchVisibleTags(comic.tags, emptyList(), tagLabel)
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(tokens.spacing.space2))
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2)) {
                tags.take(2).forEach { tag ->
                    VeneraTagChip(text = tag, onClick = null)
                }
            }
        }
    }
}

/**
 * 列表模式的单行卡：左封面 + 右信息。
 *
 * 实现已抽到 `components.ComicRowCard`（逐行原样搬走，尺寸/颜色未改），
 * 因为收藏页两条路径也要同一形态 —— 各自画一套就会同页多种高度。
 */
@Composable
private fun SearchResultRowItem(
    comic: Comic,
    mask: VeneraCoverMask,
    tagLabel: (String) -> String,
    onClick: () -> Unit,
) {
    // 列表模式也要长按：只有网格能长按是另一种假一致。
    var cardMenu by remember { mutableStateOf(false) }
    Box {
    ComicRowCard(
        title = comic.title,
        coverUrl = comic.cover,
        subtitle = comic.subTitle,
        description = comic.description,
        tags = comic.tags,
        mask = mask,
        onClick = onClick,
        onLongClick = { cardMenu = true },
        likesCount = comic.likesCount,
        rating = comic.rating,
    updateTime = comic.updateTime,
    tagLabel = tagLabel,
    coverModifier = Modifier.coverSharedElement(
        key = ComicSharedTransition.coverKey(comic.sourceKey, comic.id),
        allowFly = mask == VeneraCoverMask.Visible,
    ),
    )
        ComicCardContextMenu(
            comic = comic,
            expanded = cardMenu,
            onDismiss = { cardMenu = false },
            onOpenDetail = onClick,
        )
    }
}

/**
 * 可选 Metadata 行。
 *
 * 只渲染**确实存在**的字段；任一字段缺失即整段省略，
 * 绝不输出 0 likes / — / N/A / 空占位符。实现与 [ComicRowCard] 共用一份。
 */
@Composable
private fun OptionalMetadata(comic: Comic) = ComicRowMetadata(
    likesCount = comic.likesCount,
    rating = comic.rating,
    updateTime = comic.updateTime,
)

/**
 * 搜索页统一的加载指示（M3 Expressive 波浪圆环）。
 *
 * 前身是「双列 shimmer 卡片骨架」—— 真机反馈那两张空卡看起来像**真的搜到了两张空漫画**，
 * 而不是在加载。加载态不该伪造结果形状，故整页换成一枚居中波浪环。
 *
 * @param compact 行内小环（触底「加载更多」、筛选加载）；否则整页居中大环。
 * @param label 小环右侧的说明文字（翻页时用，避免只有一个圈不知道在干什么）。
 */
@Composable
private fun SearchLoadingIndicator(compact: Boolean = false, label: String? = null) {
    val tokens = VeneraTokens
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space4),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(tokens.spacing.loaderInline),
                color = tokens.color.primary,
                trackColor = tokens.color.surfaceVariant,
            )
            if (label != null) {
                Spacer(modifier = Modifier.width(tokens.spacing.space4))
                Text(
                    text = label,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                )
            }
        }
        return
    }
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space9),
        contentAlignment = Alignment.Center,
    ) {
        CircularWavyProgressIndicator(
            modifier = Modifier.size(tokens.spacing.loaderPage),
            color = tokens.color.primary,
            trackColor = tokens.color.surfaceVariant,
        )
    }
}

/** 回到顶部。 */
@Composable
private fun SearchBackToTop(
    visible: Boolean,
    modifier: Modifier = Modifier,
    bottomOffset: Dp,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(
            end = tokens.spacing.space8,
            bottom = bottomOffset,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(tokens.shape.large),
            color = tokens.color.primaryContainer,
            modifier = Modifier.clickable(onClick = onClick),
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
/* ================================================================== *
 * 条件 / 排序 面板
 * ================================================================== */

/**
 * 添加条件面板。
 *
 * 本轮**只提供 Tag**。年份 / 语言 / 页数区间因 SearchTag 与数据模型
 * 无可靠支持而不显示 —— 不制造 fake filter / fake data / UI-only pseudo filtering。
 * 记录为后续 Search Capability / Query Model 专项。
 *
 * 使用 Compose 内置 AlertDialog 承载（旧版 SearchTagDialog 同 host）；
 * 内部全部走 Venera 组件与 Token。
 */
@Composable
private fun SearchConditionSheet(
    viewModel: SearchViewModel,
    onDismiss: () -> Unit,
) {
    val tokens = VeneraTokens
    var input by remember { mutableStateOf("") }
    var namespace by remember { mutableStateOf("") }
    val suggestions = remember(input) { viewModel.suggestTags(input) }

    // 用 Compose 内置 AlertDialog 承载（与旧版 SearchTagDialog 同 host）。
    // 不用 miuix OverlayBottomSheet：它的内容渲染在独立窗口，CompositionLocal
    // 无法跨窗口传播，miuix 内部的 NavigationBackHandler 读不到
    // LocalNavigationEventDispatcherOwner 而抛 IllegalStateException（真机复现）。
    // 这里保留本轮的新视觉内容，只换回稳定的 Dialog host。
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "添加条件",
                fontSize = tokens.type.itemTitle,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textPrimary,
            )
        },
        text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(tokens.spacing.rowHorizontal),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
        ) {
            Text(
                text = "标签",
                fontSize = tokens.type.itemTitle,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textPrimary,
            )
            Text(
                text = "中文用于查找与显示；提交的是标签原文，由各源自行转换查询格式。" +
                    "未声明标签语法的源按普通关键词搜索。",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
            )
            SearchTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = "标签原文或中文联想",
            )
            SearchTextField(
                value = namespace,
                onValueChange = { namespace = it },
                placeholder = "命名空间（可选，如 artist）",
            )
            if (suggestions.isNotEmpty()) {
                FlowChips {
                    suggestions.forEach { (raw, label) ->
                        VeneraTagChip(
                            text = label + "（" + raw + "）",
                            onClick = {
                                viewModel.addTag(raw, label, namespace)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    },
        confirmButton = {
            SearchTextAction(
                text = "按原文添加",
                enabled = input.isNotBlank(),
                onClick = {
                    viewModel.addTag(input, namespace = namespace)
                    onDismiss()
                },
            )
        },
        dismissButton = {
            SearchTextAction(text = "取消", onClick = onDismiss)
        },
    )
}

/** Sheet 内使用的单行输入框（复用 Search Header 的视觉语言）。 */
@Composable
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val tokens = VeneraTokens
    Surface(
        shape = RoundedCornerShape(tokens.shape.medium),
        color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
        modifier = Modifier.fillMaxWidth().height(tokens.spacing.searchFieldHeight),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.fieldHorizontalPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = tokens.type.body,
                    color = tokens.color.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = tokens.type.body,
                    color = tokens.color.textPrimary,
                ),
                cursorBrush = SolidColor(tokens.color.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 排序与高级筛选面板（内容完全来自当前源的 optionList）。
 *
 * 全网聚合或无 optionList 时不展示任何伪造选项。
 */
@Composable
private fun SearchOptionsSheet(
    sourceName: String,
    aggregate: Boolean,
    groups: List<SearchOptionGroup>,
    selected: List<String?>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onApply: (List<String?>) -> Unit,
) {
    val tokens = VeneraTokens
    var draft by remember(groups, selected) {
        mutableStateOf(
            groups.mapIndexed { index, group ->
                if (index < selected.size) selected[index] else group.defaultKey
            }
        )
    }

    // 同 SearchConditionSheet：换回 Compose 内置 AlertDialog（旧版 SearchOptionsDialog 同 host），
    // 规避 miuix OverlayBottomSheet 独立窗口读不到 LocalNavigationEventDispatcherOwner 的崩溃。
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = sourceName + " · 排序与筛选",
                fontSize = tokens.type.itemTitle,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textPrimary,
            )
        },
        text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(tokens.spacing.rowHorizontal),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
        ) {
            when {
                aggregate -> VeneraEmptyView(
                    message = "全网聚合没有统一排序。请先选择一个具体漫画源。",
                )
                loading -> SearchLoadingIndicator(compact = true)
                error != null -> VeneraEmptyView(
                    message = error,
                    actionText = "重新加载",
                    onAction = onRetry,
                )
                groups.isEmpty() -> VeneraEmptyView(
                    message = "该源未声明排序或高级筛选选项。",
                )
                else -> {
                    groups.forEachIndexed { index, group ->
                        Column {
                            Text(
                                text = group.label.ifBlank { "选项 " + (index + 1) },
                                fontSize = tokens.type.caption,
                                fontWeight = tokens.type.weightSemibold,
                                color = tokens.color.textSecondary,
                                modifier = Modifier.padding(bottom = tokens.spacing.space2),
                            )
                            val value = draft.getOrNull(index)
                            when (group.type) {
                                "dropdown" -> SearchDropdownOption(
                                    group = group,
                                    value = value,
                                    onValueChange = { next ->
                                        draft = draft.toMutableList().also { it[index] = next }
                                    },
                                )
                                "select", "multi-select" -> FlowChips {
                                    group.options.forEach { (key, label) ->
                                        val checked = if (group.type == "multi-select") {
                                            key in SearchOptionValues.selectedKeys(value)
                                        } else {
                                            value == key
                                        }
                                        VeneraChip(
                                            text = label,
                                            selected = checked,
                                            onClick = {
                                                draft = draft.toMutableList().also {
                                                    it[index] = SearchOptionValues.toggle(group, value, key)
                                                }
                                            },
                                        )
                                    }
                                }
                                else -> {
                                    // 不支持的类型：保留源默认值，不生成伪造选项（与旧版一致）。
                                }
                            }
                        }
                    }
                }
            }
        }
    },
        confirmButton = {
            SearchTextAction(text = "确定并重搜", onClick = { onApply(draft) })
        },
        dismissButton = {
            SearchTextAction(text = "取消", onClick = onDismiss)
        },
    )
}

/**
 * source-native `dropdown` 类型筛选项（SearchScreen 内部私有组件）。
 *
 * 完整恢复旧版 SearchOptionsDialog 中 group.type == "dropdown" 的语义：
 *  1. 选择一个 option（值来自 group.options，不伪造）
 *  2. 保持「未选择」（value == null 时显示「未选择」——Chip flow 无法表达）
 *  3. 清空选择（写回 null）
 *  4. 恢复源默认值（写回 group.defaultKey）
 *
 * @param group 源声明的筛选组。
 * @param value 当前选中 key；null 表示未选择。
 * @param onValueChange 写回回调；null 表示清空。
 */
@Composable
private fun SearchDropdownOption(
    group: SearchOptionGroup,
    value: String?,
    onValueChange: (String?) -> Unit,
) {
    val tokens = VeneraTokens
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = RoundedCornerShape(tokens.shape.small),
            color = tokens.color.surfaceVariant.copy(alpha = tokens.current.tagContainerAlpha),
            border = BorderStroke(tokens.spacing.space1 / 2, tokens.color.outlineVariant),
            modifier = Modifier.clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = tokens.spacing.chipHorizontalPadding,
                    vertical = tokens.spacing.chipVerticalPadding,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            ) {
                Text(
                    text = group.options[value] ?: "未选择",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = tokens.color.textTertiary,
                    modifier = Modifier.size(tokens.spacing.chipIconSize),
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = "清空选择", fontSize = tokens.type.caption) },
                onClick = { onValueChange(null); expanded = false },
            )
            DropdownMenuItem(
                text = { Text(text = "恢复源默认值", fontSize = tokens.type.caption) },
                onClick = { onValueChange(group.defaultKey); expanded = false },
            )
            group.options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontSize = tokens.type.caption,
                            color = if (value == key) tokens.color.primary else tokens.color.textPrimary,
                        )
                    },
                    onClick = { onValueChange(key); expanded = false },
                )
            }
        }
    }
}

/* ================================================================== *
 * UI 层标签归一化（只作用于展示，不改任何源数据）
 * ================================================================== */

/**
 * 结果卡上要显示的标签文本。
 *
 * 折叠规则严格对应方案 §4.1 的「只合并字面同义」：
 * - 大小写 / 空白差异 → 同一枚（`Full Color` 与 `full  color`）；
 * - **裸词**与其**带命名空间**写法 → 留带命名空间那个（`chinese` 让位给 `language:chinese`）；
 * - **两个不同命名空间**下的同名值 → 各留一枚。真机图里 `東方: 靈夢` 这类前缀就是这种形态；
 *   早期版本只拿「去前缀后的值」当唯一键，会把 `female:dog` 与 `parody:dog` 错误并成一枚。
 * - 语义同义（`chinese` 与 `translated`）一律不合并 —— 它们是不同检索面词，合并等于丢功能。
 *
 * 存活下来的原文才是发给源的词，所以这里只会减少重复药丸，不可能改变搜索结果。
 */
internal fun searchVisibleTags(
    tags: List<String>,
    exclude: List<String>,
    display: (String) -> String = { it },
): List<String> {
    val excluded = exclude.map { conceptKey(it) }.toSet()
    val entries = tags.map { it.trim() }
        .filter { it.isNotEmpty() && conceptKey(it) !in excluded }
    // 已被带命名空间写法认领的概念，裸词不再单独出现。
    val claimedByQualified = entries.filter { ':' in it }.map { conceptKey(it) }.toSet()
    val seen = HashSet<String>()
    val survivors = ArrayList<String>()
    entries.forEach { raw ->
        if (':' !in raw && conceptKey(raw) in claimedByQualified) return@forEach
        // 带前缀的按完整字面键去重（不同前缀互不吞并），裸词按概念键去重（只并大小写/空白）。
        val key = if (':' in raw) literalKey(raw) else conceptKey(raw)
        if (seen.add(key)) survivors.add(raw)
    }
    return survivors.map(display)
}

/** 概念键：小写 + 折叠全部空白 + 去命名空间前缀。 */
private fun conceptKey(value: String): String {
    val literal = literalKey(value)
    return literal.substringAfter(':', literal)
}

/** 字面键：小写 + 折叠全部空白，保留命名空间前缀。 */
private fun literalKey(value: String): String =
    value.trim().lowercase().replace(WHITESPACE_RUN, "")

private val WHITESPACE_RUN = Regex("\\s+")
