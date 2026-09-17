package com.venera.compose.feature.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.feature.ComicItem
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.explore.ExploreMode
import com.venera.compose.source.explore.NativeEntry
import com.venera.compose.source.explore.NativeSection
import com.venera.compose.source.explore.SourceExploration
import com.venera.compose.source.explore.UnifiedTag
import com.venera.compose.source.explore.unifiedTagsFor
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ExplorePagePart
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
 * 切换源时：分类 / 探索方式 / 通用标签全部立即换成该源的；源没有的能力直接不渲染。
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun UnifiedExploreScreen(
    onSelectComic: (ComicItem) -> Unit,
    onOpenNativeSection: (NativeSectionArgs) -> Unit,
) {
    val context = LocalContext.current
    val sourceManager = remember { ComicSourceManager.getInstance(context) }
    val state = remember { ExploreUiState() }

    var explorations by remember { mutableStateOf<List<SourceExploration>>(emptyList()) }
    var isLoadingSources by remember { mutableStateOf(true) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var refreshTick by rememberSaveable { mutableIntStateOf(0) }

    var content by remember { mutableStateOf<ExploreContent>(ExploreContent.Empty) }
    var isLoadingContent by remember { mutableStateOf(false) }
    var contentError by remember { mutableStateOf<String?>(null) }

    // 探索方式切换计数器：让下面的 LaunchedEffect 重新执行（不重载源列表）。
    var modeTick by remember { mutableIntStateOf(0) }

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

    // ── 切换源 / 切换探索方式 -> 重新拉内容 ──
    LaunchedEffect(currentSource?.sourceKey, currentSource?.modes, refreshTick, modeTick) {
        val src = currentSource
        if (src == null) {
            content = ExploreContent.Empty
            return@LaunchedEffect
        }
        val mode = state.resolveMode(src.sourceKey, src.modes)
        if (mode == null) {
            content = ExploreContent.Empty
            return@LaunchedEffect
        }
        isLoadingContent = true
        contentError = null
        content = ExploreContent.Empty
        try {
            content = loadMode(sourceManager, src, mode)
        } catch (e: Exception) {
            contentError = e.message ?: "加载失败"
        } finally {
            isLoadingContent = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("探索", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { refreshTick++ }) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "刷新",
                    tint = MiuixTheme.colorScheme.primary
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
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    Text(
                        sourceError ?: "暂无可用漫画源，请在漫画源管理中启用后再试。",
                        modifier = Modifier.padding(16.dp)
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
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().clickable {
                            onOpenNativeSection(
                                NativeSectionArgs(
                                    sourceKey = src.sourceKey,
                                    sourceTitle = src.sourceName,
                                    category = "排行",
                                    param = "ranking",
                                )
                            )
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🏆 " + src.sourceName + " 排行榜",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            Text("查看 ›", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                        }
                    }
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

            // 先解构成明确的局部变量，避免 when 分支里对 sealed 成员做智能转换时类型推断失败。
            val loaded = content
            when {
                isLoadingContent -> item { CenteredLoader() }
                contentError != null -> item {
                    Text(contentError.orEmpty(), color = Color(0xFFE53935))
                }
                loaded.isEmpty -> item {
                    Text(
                        "该探索方式暂无内容",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                loaded is ExploreContent.Sections -> {
                    val parts = loaded.parts
                    itemsIndexed(
                        items = parts,
                        key = { index: Int, part: ExplorePagePart -> "part-" + index + "-" + part.title }
                    ) { _, part ->
                        ExplorePartView(part, src.sourceName, onSelectComic)
                    }
                }
                loaded is ExploreContent.ComicList -> {
                    item(key = "list") {
                        ExploreComicGrid(loaded.comics, src.sourceName, onSelectComic)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 数据加载：把「探索方式」翻译成源调用
 * ------------------------------------------------------------------ */

private suspend fun loadMode(
    manager: ComicSourceManager,
    source: SourceExploration,
    mode: ExploreMode,
): ExploreContent = when (mode.kind) {
    ExploreMode.Kind.RANKING -> {
        val option = mode.rankingOption ?: "day"
        val res = manager.loadCategoryRanking(source.sourceKey, option, 1)
        res.fold(
            onSuccess = { ExploreContent.ComicList(it, page = 1, hasMore = it.isNotEmpty()) },
            onFailure = { throw it },
        )
    }
    ExploreMode.Kind.EXPLORE_MULTI_PART,
    ExploreMode.Kind.EXPLORE_PAGE -> {
        val res = manager.loadExplorePage(source.sourceKey, mode.pageIndex, 1)
        res.fold(
            onSuccess = { parts ->
                val isPlainList = parts.size == 1 && parts.first().title.isBlank()
                if (isPlainList || mode.kind == ExploreMode.Kind.EXPLORE_PAGE) {
                    ExploreContent.ComicList(
                        comics = parts.flatMap { it.comics },
                        page = 1,
                        hasMore = parts.any { it.comics.isNotEmpty() },
                    )
                } else {
                    ExploreContent.Sections(parts)
                }
            },
            onFailure = { throw it },
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sources, key = { it.sourceKey }) { source ->
            val selected = source.sourceKey == selectedKey
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MiuixTheme.colorScheme.primaryContainer
                else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.Tab,
                    onClick = { if (!selected) onSelect(source.sourceKey) },
                )
            ) {
                Text(
                    text = source.sourceName,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
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
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun CenteredLoader() {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MiuixTheme.colorScheme.primary)
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(modes, key = { it.id }) { mode ->
                val selected = mode.id == selectedId
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { if (!selected) onSelect(mode) }
                ) {
                    Text(
                        text = mode.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color.White else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            section.items.forEach { entry ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { onEntryClick(entry) }
                ) {
                    Text(
                        text = entry.label,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 辅助层：通用标签（描边样式，与源原生标签的实心样式明确区分）
 * ------------------------------------------------------------------ */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnifiedTagBlock(tags: List<UnifiedTag>, onTagClick: (UnifiedTag) -> Unit) {
    Column {
        SectionHeader("通用标签 · 跨源快捷筛选")
        Text(
            "应用层映射，不修改各源原生 Tag；点击后按关键词搜索当前源。",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MiuixTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onTagClick(tag) }
                ) {
                    Text(
                        text = tag.label,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 作品列表
 * ------------------------------------------------------------------ */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExplorePartView(
    part: ExplorePagePart,
    sourceName: String,
    onSelectComic: (ComicItem) -> Unit,
) {
    Column {
        SectionHeader(part.title.ifBlank { "推荐" })
        ExploreComicGrid(part.comics, sourceName, onSelectComic)
    }
}

@Composable
private fun ExploreComicGrid(
    comics: List<Comic>,
    sourceName: String,
    onSelectComic: (ComicItem) -> Unit,
) {
    val rows = remember(comics) { comics.chunked(2) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { comic ->
                    Box(Modifier.weight(1f)) {
                        ExploreComicCard(comic, sourceName, onSelectComic)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExploreComicCard(
    comic: Comic,
    sourceName: String,
    onSelectComic: (ComicItem) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
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
        }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = comic.cover,
                contentDescription = comic.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = comic.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val sub = comic.subTitle.ifBlank { comic.tags.take(2).joinToString(" · ") }
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sub,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
