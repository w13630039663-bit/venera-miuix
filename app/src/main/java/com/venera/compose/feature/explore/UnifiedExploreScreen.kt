package com.venera.compose.feature.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import com.venera.compose.feature.ComicItem
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.explore.ExploreMode
import com.venera.compose.source.explore.NativeEntry
import com.venera.compose.source.explore.NativeSection
import com.venera.compose.source.explore.SourceExploration
import com.venera.compose.source.explore.UnifiedTag
import com.venera.compose.source.explore.unifiedTagsFor
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ExplorePagePart
import com.venera.compose.source.model.PageJumpTarget
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * 统一「探索」页。
 *
 * 结构（严格对应「合并页面入口，而不是合并数据体系」）：
 *
 *   第一层 选漫画源      [Picacg] [nhentai] [禁漫天堂] [ehentai]
 *   第二层 探索方式      —— 只显示**当前源真正支持**的（随机/最新/H24/D7/排行榜…）
 *   第三层 该源原生分类  —— 分类 / Tag / 专题，全部来自当前源自己的声明
 *   （辅助）通用标签     —— 应用层可选映射，绝不覆盖源的原生 Tag
 *   底部   作品列表
 *
 * Batch 1（卡片与状态统一落地）：
 *  - R6  ContentGuardManager 数据过滤 + VeneraCover(mask) 打码；规则变化重放。
 *  - V5/V6  VeneraCard / VeneraCover / VeneraSourceBadge / VeneraChip / VeneraTagChip。
 *  - R4/R5/V7  VeneraEmptyView 承载错误态 / 空源态 / 空内容态。
 *  - R3  分区「查看更多 ›」下钻（仅 category 型 target，不伪造入口）。
 *
 * Batch 2（网格自适应、布局切换与分页流）：
 *  - R2  顶栏 ComicLayoutToggleButton，接入全局 rememberComicListDisplayMode；
 *        detailed 单列复用 ComicTileDetailed（coverMaskState 同步传递给封面）。
 *  - V4  列数 = comicListColumnCount(displayMode)（brief 双列 / detailed 单列，与
 *        Home/Favorites/NetworkFavorites 同源）。注意：exploreColumnCount 是给源选择器
 *        的网格展开用的（coerceAtLeast(3)），**不用于漫画卡片**。
 *  - 网格虚拟化：全页唯一 LazyColumn，items(comics.chunked(columns)) 逐行挂载，
 *        每行是独立 LazyItem 参与回收；绝不在 item 内嵌套同向 LazyVerticalGrid
 *        （会因无界最大高度直接抛 IllegalStateException）。
 *  - R1  分页流：触底自动加载（footer 进入组合即触发），新页数据经
 *        ExplorePolicy.mergeExploreParts 同名分区续接 + 按 (sourceKey,id) 去重后
 *        原地追加，不整页重刷；守卫过滤只作用于展示层，避免空页误判终止。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedExploreScreen(
    onSelectComic: (ComicItem) -> Unit,
    onOpenNativeSection: (NativeSectionArgs) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceManager = remember { ComicSourceManager.getInstance(context) }
    val guardManager = remember { ContentGuardManager.getInstance(context) }
    val guardRules by guardManager.rules.collectAsState()
    val nsfwMaskMode by guardManager.nsfwMaskMode.collectAsState()
    val state = remember { ExploreUiState() }
    // R2：全局布局偏好唯一真源（与 Favorites / NetworkFavorites / History 一致）。
    val displayMode = rememberComicListDisplayMode()

    var explorations by remember { mutableStateOf<List<SourceExploration>>(emptyList()) }
    var isLoadingSources by remember { mutableStateOf(true) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var refreshTick by rememberSaveable { mutableIntStateOf(0) }

    var content by remember { mutableStateOf<ExploreContent>(ExploreContent.Empty) }
    var isLoadingContent by remember { mutableStateOf(false) }
    var contentError by remember { mutableStateOf<String?>(null) }

    // 探索方式切换计数器：让下面的 LaunchedEffect 重新执行（不重载源列表）。
    var modeTick by remember { mutableIntStateOf(0) }

    // ── R1 分页状态 ──
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    /** 跨页累计的原始分区（未过滤），分页追加与去重的唯一数据源。 */
    var accumulatedParts by remember { mutableStateOf<List<ExplorePagePart>>(emptyList()) }
    /** 展示形态：true = 按分区渲染（singlePageWithMultiPart），false = 平铺列表。 */
    var showAsSections by remember { mutableStateOf(false) }

    // ── 加载每个源的探索能力 ──
    LaunchedEffect(refreshTick) {
        isLoadingSources = true
        sourceError = null
        try {
            val list = sourceManager.getSourceExplorations()
            explorations = list
            val kept = state.selectedSourceKey?.takeIf { key -> list.any { it.sourceKey == key } }
            state.selectedSourceKey = kept ?: list.firstOrNull()?.sourceKey
        } catch (e: Exception) {
            sourceError = e.message ?: "加载漫画源失败"
        } finally {
            isLoadingSources = false
        }
    }

    val currentSource = explorations.firstOrNull { it.sourceKey == state.selectedSourceKey }

    // ── 切换源 / 切换探索方式 -> 重新拉第 1 页 ──
    LaunchedEffect(currentSource?.sourceKey, currentSource?.modes, refreshTick, modeTick) {
        val src = currentSource
        val mode = src?.let { state.resolveMode(it.sourceKey, it.modes) }
        if (src == null || mode == null) {
            content = ExploreContent.Empty
            accumulatedParts = emptyList()
            hasMore = false
            return@LaunchedEffect
        }
        isLoadingContent = true
        contentError = null
        loadMoreError = null
        isLoadingMore = false
        hasMore = false
        content = ExploreContent.Empty
        try {
            val raw = fetchModeParts(sourceManager, src, mode, 1)
            accumulatedParts = raw
            currentPage = 1
            showAsSections = mode.kind == ExploreMode.Kind.EXPLORE_MULTI_PART &&
                !(raw.size == 1 && raw.first().title.isBlank())
            // 旧版口径：singlePageWithMultiPart 是单页结构，永远没有下一页。
            hasMore = mode.kind != ExploreMode.Kind.EXPLORE_MULTI_PART && raw.any { it.comics.isNotEmpty() }
            content = buildExploreContent(guardManager, raw, showAsSections, 1, hasMore, nsfwMaskMode)
        } catch (e: Exception) {
            contentError = e.message ?: "加载失败"
        } finally {
            isLoadingContent = false
        }
    }

    // ── R1：触底加载下一页。新页先 mergeExploreParts 续接去重，再整体重建展示内容，
    // 已加载部分原地保留（LazyColumn 按 key 复用），不发生整页重刷。──
    fun loadMore() {
        val src = currentSource ?: return
        val mode = state.resolveMode(src.sourceKey, src.modes) ?: return
        if (!hasMore || isLoadingMore || isLoadingContent) return
        scope.launch {
            isLoadingMore = true
            loadMoreError = null
            try {
                val next = currentPage + 1
                val raw = fetchModeParts(sourceManager, src, mode, next)
                accumulatedParts = mergeExploreParts(accumulatedParts, raw)
                currentPage = next
                hasMore = raw.isNotEmpty() && raw.any { it.comics.isNotEmpty() }
                content = buildExploreContent(guardManager, accumulatedParts, showAsSections, next, hasMore, nsfwMaskMode)
            } catch (e: Exception) {
                loadMoreError = e.message ?: "加载失败"
            } finally {
                isLoadingMore = false
            }
        }
    }

    // ── 屏蔽规则 / 打码模式变化时，基于累计数据重建展示层（HIDE 剔除 / BLUR 打码重算）──
    LaunchedEffect(guardRules, nsfwMaskMode) {
        if (accumulatedParts.isNotEmpty()) {
            content = buildExploreContent(guardManager, accumulatedParts, showAsSections, currentPage, hasMore, nsfwMaskMode)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = VeneraTokens.spacing.rowHorizontal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "探索",
                fontSize = VeneraTokens.type.itemTitle,
                fontWeight = VeneraTokens.type.weightBold,
                modifier = Modifier.weight(1f)
            )
            // R2：单列/双列切换，与 Favorites 等页面同款按钮、同一份偏好。
            ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
            IconButton(onClick = { refreshTick++ }) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "刷新",
                    tint = VeneraTokens.color.primary
                )
            }
        }

        // 通用标签的可用性只依赖当前源，提前算好（LazyListScope 内不能调用 remember）。
        val unifiedAvailability = remember(currentSource) {
            currentSource?.let { unifiedTagsFor(it) }
                ?: com.venera.compose.source.explore.UnifiedTagAvailability(emptyList())
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = VeneraTokens.spacing.rowHorizontal,
                end = VeneraTokens.spacing.rowHorizontal,
                top = VeneraTokens.spacing.space2,
                bottom = VeneraTokens.spacing.bottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.sectionGap)
        ) {
            if (explorations.isNotEmpty()) {
                item(key = "source-picker") {
                    SourcePicker(
                        sources = explorations,
                        selectedKey = currentSource?.sourceKey,
                        enabled = !isLoadingSources,
                        onSelect = { state.selectedSourceKey = it },
                    )
                }
            }

            if (isLoadingSources) {
                item { CenteredLoader() }
                return@LazyColumn
            }

            if (explorations.isEmpty()) {
                item {
                    VeneraEmptyView(
                        title = "暂无可用漫画源",
                        message = sourceError ?: "请在漫画源管理中启用支持探索的漫画源后再试。",
                        actionText = "重新检测",
                        onAction = { refreshTick++ },
                    )
                }
                return@LazyColumn
            }

            val src = currentSource ?: return@LazyColumn

            if (src.modes.isNotEmpty()) {
                item(key = "modes-" + src.sourceKey) {
                    ExploreModeRow(
                        modes = src.modes,
                        selectedId = state.resolveMode(src.sourceKey, src.modes)?.id,
                        onSelect = { mode ->
                            state.selectMode(src.sourceKey, mode.id)
                            modeTick++
                        },
                    )
                }
            }

            if (src.nativeSections.isNotEmpty()) {
                itemsIndexed(
                    src.nativeSections,
                    key = { index, section -> "sec-" + src.sourceKey + "-" + index + "-" + section.name }
                ) { _, section ->
                    NativeSectionBlock(
                        section = section,
                        onEntryClick = { entry ->
                            onOpenNativeSection(
                                NativeSectionArgs(
                                    sourceKey = src.sourceKey,
                                    sourceTitle = src.sourceName,
                                    category = entry.label,
                                    param = entry.param,
                                )
                            )
                        },
                    )
                }
            }

            if (src.hasRanking) {
                item(key = "ranking-" + src.sourceKey) {
                    RankingEntry(
                        sourceName = src.sourceName,
                        onClick = {
                            onOpenNativeSection(
                                NativeSectionArgs(
                                    sourceKey = src.sourceKey,
                                    sourceTitle = src.sourceName,
                                    category = "排行",
                                    param = "ranking",
                                )
                            )
                        },
                    )
                }
            }

            if (!unifiedAvailability.isEmpty) {
                item(key = "unified-" + src.sourceKey) {
                    UnifiedTagBlock(
                        tags = unifiedAvailability.available,
                        onTagClick = { tag ->
                            onOpenNativeSection(
                                NativeSectionArgs(
                                    sourceKey = src.sourceKey,
                                    sourceTitle = src.sourceName,
                                    category = tag.label,
                                    param = null,
                                    unifiedTag = tag,
                                )
                            )
                        },
                    )
                }
            }

            item(key = "content-header-" + src.sourceKey) { SectionHeader("内容") }

            val columns = comicListColumnCount(displayMode.value)
            val isDetailed = displayMode.value == "detailed"

            when {
                isLoadingContent -> item { CenteredLoader() }
                contentError != null -> item {
                    VeneraEmptyView(
                        title = "探索内容加载失败",
                        message = contentError.orEmpty(),
                        actionText = "重试",
                        onAction = { modeTick++ },
                    )
                }
                content.isEmpty -> item {
                    VeneraEmptyView(message = "该探索方式暂无内容")
                }
                content is ExploreContent.Sections -> {
                    // 先解构局部变量：LazyListScope lambda 内对 sealed 成员的智能转换不可靠。
                    val sectionsContent = content as ExploreContent.Sections
                    val parts: List<ExplorePagePart> = sectionsContent.parts
                    parts.forEachIndexed { partIndex: Int, part: ExplorePagePart ->
                        item(key = "hdr-$partIndex-${part.title}") {
                            PartHeader(
                                part = part,
                                onViewMore = { target ->
                                    onOpenNativeSection(
                                        NativeSectionArgs(
                                            sourceKey = src.sourceKey,
                                            sourceTitle = src.sourceName,
                                            category = part.title,
                                            param = target.attributes?.get("param")?.toString(),
                                        )
                                    )
                                },
                            )
                        }
                        if (isDetailed) {
                            items(
                                part.comics,
                                key = { "dcard-$partIndex-${it.id}" },
                            ) { comic ->
                                ExploreDetailedCard(comic, src.sourceName, guardManager, nsfwMaskMode, onSelectComic)
                            }
                        } else {
                            part.comics.chunked(columns).forEachIndexed { rowIndex, row ->
                                item(key = "prow-$partIndex-$rowIndex-${row.firstOrNull()?.id}") {
                                    ExploreCardRow(row, src.sourceName, guardManager, nsfwMaskMode, onSelectComic)
                                }
                            }
                        }
                    }
                }
                content is ExploreContent.ComicList -> {
                    val listContent = content as ExploreContent.ComicList
                    val flatComics: List<Comic> = listContent.comics
                    if (isDetailed) {
                        items(
                            flatComics,
                            key = { "dcard-${it.id}" },
                        ) { comic ->
                            ExploreDetailedCard(comic, src.sourceName, guardManager, nsfwMaskMode, onSelectComic)
                        }
                    } else {
                        flatComics.chunked(columns).forEachIndexed { rowIndex, row ->
                            item(key = "crow-$rowIndex-${row.firstOrNull()?.id}") {
                                ExploreCardRow(row, src.sourceName, guardManager, nsfwMaskMode, onSelectComic)
                            }
                        }
                    }
                }
            }

            // R1：触底加载 footer。footer 进入组合即触发一次加载；成功翻页后
            // currentPage 变化使 LaunchedEffect 重新执行，滚动停在底部即连续加载。
            if (hasMore && !isLoadingContent && contentError == null) {
                item(key = "load-more") {
                    LaunchedEffect(currentPage) {
                        if (!isLoadingMore && loadMoreError == null) loadMore()
                    }
                    LoadMoreFooter(
                        isLoading = isLoadingMore,
                        error = loadMoreError,
                        onRetry = {
                            loadMoreError = null
                            loadMore()
                        },
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 数据加载：把「探索方式」翻译成源调用（统一按分区返回，便于分页续接）
 * ------------------------------------------------------------------ */

/** 拉取某个探索方式的第 [page] 页，统一归一成 [ExplorePagePart] 列表。 */
private suspend fun fetchModeParts(
    manager: ComicSourceManager,
    source: SourceExploration,
    mode: ExploreMode,
    page: Int,
): List<ExplorePagePart> = when (mode.kind) {
    ExploreMode.Kind.RANKING -> {
        val option = mode.rankingOption ?: "day"
        manager.loadCategoryRanking(source.sourceKey, option, page).fold(
            onSuccess = { comics -> listOf(ExplorePagePart(title = "", comics = comics)) },
            onFailure = { throw it },
        )
    }
    ExploreMode.Kind.EXPLORE_MULTI_PART,
    ExploreMode.Kind.EXPLORE_PAGE -> {
        manager.loadExplorePage(source.sourceKey, mode.pageIndex, page).fold(
            onSuccess = { it },
            onFailure = { throw it },
        )
    }
}

/**
 * 由累计分区构建展示内容。守卫过滤只作用于展示层（accumulated 保留原始数据），
 * 因此「HIDE 规则清空某页」不会把 hasMore 误判成没有下一页。
 */
private fun buildExploreContent(
    guard: ContentGuardManager,
    parts: List<ExplorePagePart>,
    asSections: Boolean,
    page: Int,
    hasMore: Boolean,
    maskMode: String,
): ExploreContent =
    when (maskMode) {
        // HIDE：命中条目整条剔除（空分区整块隐藏）。
        "HIDE" ->
            if (asSections) {
                ExploreContent.Sections(guard.filterExploreParts(parts))
            } else {
                ExploreContent.ComicList(
                    comics = guard.filterComicModels(parts.flatMap { it.comics }),
                    page = page,
                    hasMore = hasMore,
                )
            }
        // BLUR：**严禁剔除**——命中条目必须完整保留，交由卡片 coverMaskStateFor
        // 算出 Masked 后由 VeneraCover 呈现打码。提前剔除会让打码在数学上永远无法触发。
        // OFF：不过滤也不打码。
        else ->
            if (asSections) {
                ExploreContent.Sections(parts)
            } else {
                ExploreContent.ComicList(
                    comics = parts.flatMap { it.comics },
                    page = page,
                    hasMore = hasMore,
                )
            }
    }

/* ------------------------------------------------------------------ *
 * 第一层：漫画源
 * ------------------------------------------------------------------ */

@Composable
private fun SourcePicker(
    sources: List<SourceExploration>,
    selectedKey: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.chipSpacing)) {
        items(sources, key = { it.sourceKey }) { source ->
            val selected = source.sourceKey == selectedKey
            VeneraChip(
                text = source.sourceName,
                selected = selected,
                enabled = enabled,
                onClick = { if (!selected) onSelect(source.sourceKey) },
            )
        }
    }
}

/* ------------------------------------------------------------------ *
 * 区块标题 / 加载态
 * ------------------------------------------------------------------ */

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = VeneraTokens.type.itemTitle,
        fontWeight = VeneraTokens.type.weightBold,
        color = VeneraTokens.color.textPrimary,
        modifier = Modifier.padding(bottom = VeneraTokens.spacing.space2)
    )
}

@Composable
private fun CenteredLoader() {
    Box(Modifier.fillMaxWidth().padding(VeneraTokens.spacing.space10), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = VeneraTokens.color.primary)
    }
}

/* ------------------------------------------------------------------ *
 * 第二层：探索方式
 * ------------------------------------------------------------------ */

@Composable
private fun ExploreModeRow(
    modes: List<ExploreMode>,
    selectedId: String?,
    onSelect: (ExploreMode) -> Unit,
) {
    Column {
        SectionHeader("探索方式")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.chipSpacing)) {
            items(modes, key = { it.id }) { mode ->
                val selected = mode.id == selectedId
                VeneraChip(
                    text = mode.label,
                    selected = selected,
                    onClick = { if (!selected) onSelect(mode) },
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 第三层：源原生分类 / Tag（原样呈现，不合并）
 * ------------------------------------------------------------------ */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NativeSectionBlock(
    section: NativeSection,
    onEntryClick: (NativeEntry) -> Unit,
) {
    Column {
        SectionHeader(section.name)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.chipSpacing),
            verticalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.chipSpacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            section.items.forEach { entry ->
                VeneraChip(text = entry.label, onClick = { onEntryClick(entry) })
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 排行榜入口（仅源声明 enableRankingPage 时渲染）
 * ------------------------------------------------------------------ */

@Composable
private fun RankingEntry(sourceName: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(VeneraTokens.shape.medium),
        color = VeneraTokens.color.surfaceVariant.copy(alpha = VeneraTokens.current.selectedSurfaceAlpha),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = VeneraTokens.spacing.rowHorizontal,
                vertical = VeneraTokens.spacing.rowVertical,
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🏆 " + sourceName + " 排行榜",
                fontSize = VeneraTokens.type.body,
                fontWeight = VeneraTokens.type.weightSemibold,
            )
            Spacer(Modifier.weight(1f))
            Text("查看 ›", fontSize = VeneraTokens.type.caption, color = VeneraTokens.color.primary)
        }
    }
}

/* ------------------------------------------------------------------ *
 * 辅助层：通用标签（VeneraTagChip 描边样式，与源原生标签明确区分）
 * ------------------------------------------------------------------ */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnifiedTagBlock(tags: List<UnifiedTag>, onTagClick: (UnifiedTag) -> Unit) {
    Column {
        SectionHeader("通用标签 · 跨源快捷筛选")
        Text(
            "应用层映射，不修改各源原生 Tag；点击后按关键词搜索当前源。",
            fontSize = VeneraTokens.type.overline,
            color = VeneraTokens.color.textTertiary,
            modifier = Modifier.padding(bottom = VeneraTokens.spacing.space2),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.chipSpacing),
            verticalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.chipSpacing),
        ) {
            tags.forEach { tag ->
                VeneraTagChip(text = tag.label, onClick = { onTagClick(tag) })
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 作品列表：分区头 / 卡片行 / 单列大卡 / 触底 footer
 * ------------------------------------------------------------------ */

/** 分区标题 + R3「查看更多 ›」（仅 category 型 target 能被 SourceSectionRoute 承载）。 */
@Composable
private fun PartHeader(part: ExplorePagePart, onViewMore: (PageJumpTarget) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = part.title.ifBlank { "推荐" },
            fontSize = VeneraTokens.type.itemTitle,
            fontWeight = VeneraTokens.type.weightBold,
            color = VeneraTokens.color.textPrimary,
            modifier = Modifier.weight(1f),
        )
        val target = part.viewMore
        if (target != null && target.page == "category") {
            Text(
                text = "查看更多 ›",
                fontSize = VeneraTokens.type.caption,
                color = VeneraTokens.color.primary,
                modifier = Modifier
                    .padding(start = VeneraTokens.spacing.space2)
                    .clickable { onViewMore(target) },
            )
        }
    }
}

/** 双列（或按宽度自适应列数）卡片行：一个 Row 就是一个 LazyItem，参与行级回收。 */
@Composable
private fun ExploreCardRow(
    row: List<Comic>,
    sourceName: String,
    guardManager: ContentGuardManager,
    nsfwMaskMode: String,
    onSelectComic: (ComicItem) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.gridGap),
    ) {
        row.forEach { comic ->
            Box(Modifier.weight(1f)) {
                ExploreComicCard(comic, sourceName, guardManager, nsfwMaskMode, onSelectComic)
            }
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
    }
}

/** 单列大卡（detailed）：复用 ComicTileDetailed，打码状态同步传给封面。 */
@Composable
private fun ExploreDetailedCard(
    comic: Comic,
    sourceName: String,
    guardManager: ContentGuardManager,
    nsfwMaskMode: String,
    onSelectComic: (ComicItem) -> Unit,
) {
    // 直接传 Comic 走守卫内部 LRU 判定缓存；每次组合重算保证规则/模式变化即时生效
    // （守卫设置变更会 invalidate，remember 反而可能读到过期值）。
    val maskState = guardManager.coverMaskStateFor(comic)
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
        onClick = {
            onSelectComic(
                ComicItem(
                    id = comic.id,
                    title = comic.title,
                    author = comic.subTitle,
                    coverUrl = comic.cover,
                    tags = comic.tags,
                    description = comic.description,
                    sourceName = sourceName,
                    rating = comic.rating?.toString().orEmpty(),
                    likesCount = comic.likesCount,
                    updateTime = comic.updateTime,
                )
            )
        },
    )
}

@Composable
private fun ExploreComicCard(
    comic: Comic,
    sourceName: String,
    guardManager: ContentGuardManager,
    nsfwMaskMode: String,
    onSelectComic: (ComicItem) -> Unit,
) {
    // 直接传 Comic 走守卫内部 LRU 判定缓存；每次组合重算保证规则/模式变化即时生效
    // （守卫设置变更会 invalidate，remember 反而可能读到过期值）。
    val maskState = guardManager.coverMaskStateFor(comic)
    VeneraCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            onSelectComic(
                ComicItem(
                    id = comic.id,
                    title = comic.title,
                    author = comic.subTitle,
                    coverUrl = comic.cover,
                    tags = comic.tags,
                    description = comic.description,
                    sourceName = sourceName,
                    rating = comic.rating?.toString().orEmpty(),
                    likesCount = comic.likesCount,
                    updateTime = comic.updateTime,
                )
            )
        },
    ) {
        VeneraCover(
            url = comic.cover,
            contentDescription = comic.title,
            mask = if (maskState == "VISIBLE") VeneraCoverMask.Visible else VeneraCoverMask.Masked,
        ) {
            VeneraSourceBadge(name = sourceName)
        }
        Spacer(Modifier.height(VeneraTokens.spacing.cardCoverGap))
        Text(
            text = comic.title,
            fontSize = VeneraTokens.type.caption,
            fontWeight = VeneraTokens.type.weightSemibold,
            color = VeneraTokens.color.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val sub = comic.subTitle.ifBlank { comic.tags.take(2).joinToString(" · ") }
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(VeneraTokens.spacing.space1))
            Text(
                text = sub,
                fontSize = VeneraTokens.type.overline,
                color = VeneraTokens.color.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** R1 触底 footer：加载中转圈；失败可点击重试；静默态给出上滑提示。 */
@Composable
private fun LoadMoreFooter(isLoading: Boolean, error: String?, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = VeneraTokens.spacing.space5),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                color = VeneraTokens.color.primary,
                modifier = Modifier.size(VeneraTokens.spacing.badgeSize),
            )
            error != null -> Text(
                text = "加载失败：$error · 点击重试",
                fontSize = VeneraTokens.type.caption,
                color = VeneraTokens.color.primary,
                modifier = Modifier.clickable(onClick = onRetry),
            )
            else -> Text(
                text = "上滑加载更多",
                fontSize = VeneraTokens.type.overline,
                color = VeneraTokens.color.textTertiary,
            )
        }
    }
}

/**
 * 打开某个源的原生分类 / Tag 的入参。
 *
 * [unifiedTag] 非空表示这是应用层「通用标签」入口 —— 处理方应按关键词搜索，
 * 而不是当成源的原生分类去查，避免污染源自己的分类体系。
 */
data class NativeSectionArgs(
    val sourceKey: String,
    val sourceTitle: String,
    val category: String,
    val param: String?,
    val unifiedTag: UnifiedTag? = null,
) : java.io.Serializable
