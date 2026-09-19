package com.venera.compose.feature.explore

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.feature.ComicItem
import com.venera.compose.feature.SourceSectionRoute
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.explore.UnifiedTag
import com.venera.compose.source.model.CategoryComicsOption
import com.venera.compose.source.model.Comic
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * 某个漫画源的**原生**分类 / Tag 下钻页。
 *
 * Batch 1：
 *  - 卡片统一 VeneraCard + VeneraCover(mask) + 封面覆盖层 VeneraSourceBadge（V5/V6/R6 兜底打码）；
 *  - optionList 筛选项改用 VeneraChip（selected 态由组件统一表达）；
 *  - 错误态 / 空内容态统一 VeneraEmptyView（R4/V7，清除硬编码颜色）；
 *  - 视觉全部走 VeneraTokens，不再出现字面值。
 * Batch 2：
 *  - R2 顶栏 ComicLayoutToggleButton（与一级页共用全局 displayMode 偏好）；
 *  - V4 列数 = comicListColumnCount(displayMode)，逐行 items 挂载（行级回收，
 *    绝不在 LazyColumn item 内嵌套同向 LazyVerticalGrid）；
 *  - detailed 单列复用 ComicTileDetailed（打码状态同步传递）。
 * Batch 3：
 *  - 迁移到 VeneraTopAppBar + Box 布局，修复顶栏被系统状态栏遮挡；
 *  - 添加大标题折叠 + 毛玻璃效果，与其他页面保持视觉一致。
 */
@Composable
fun SourceSectionScreen(
    route: SourceSectionRoute,
    onBack: () -> Unit,
    onSelectComic: (ComicItem) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceManager = remember { ComicSourceManager.getInstance(context) }
    val guardManager = remember { ContentGuardManager.getInstance(context) }
    val guardRules by guardManager.rules.collectAsState()
    val nsfwMaskMode by guardManager.nsfwMaskMode.collectAsState()
    // R2：全局布局偏好唯一真源（与一级页 / Favorites 等一致）。
    val displayMode = rememberComicListDisplayMode()

    val unifiedTag = remember(route.unifiedTag) {
        route.unifiedTag?.let { name -> UnifiedTag.entries.firstOrNull { it.name == name } }
    }

    var comics by remember { mutableStateOf<List<Comic>>(emptyList()) }
    var options by remember { mutableStateOf<List<CategoryComicsOption>>(emptyList()) }
    var selectedOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var maxPage by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }

    // 通用标签入口 -> 按关键词搜索；原生入口 -> 走分类接口。
    fun load(targetPage: Int) {
        scope.launch {
            isLoading = true
            error = null
            val result = if (unifiedTag != null) {
                // 只取条目：本页维持改造前的行为（maxPage 传 null，即"页数未知"）。
                // 接口现在能给出真实 maxPage 了，但启用它会改变这个冻结页的分页判定，
                // 属于另一件事，要改请单独评审。
                sourceManager.search(route.sourceKey, unifiedTag.label, targetPage, null)
                    .map { page -> page.comics to null }
            } else {
                sourceManager.loadCategoryComics(
                    sourceKey = route.sourceKey,
                    category = route.category,
                    param = route.param,
                    options = selectedOptions,
                    page = targetPage,
                ).map { data -> data.comics to data.maxPage }
            }
            result.fold(
                onSuccess = { (list, mp) ->
                    // HIDE：命中条目整条剔除；BLUR：保留条目交由卡片打码；OFF：原样。
                    comics = if (nsfwMaskMode == "HIDE") guardManager.filterComicModels(list) else list
                    maxPage = mp
                    page = targetPage
                },
                onFailure = { e ->
                    error = e.message ?: "加载失败"
                },
            )
            isLoading = false
        }
    }

    // 初始化：读取该分类的源自定义筛选项。
    LaunchedEffect(route.sourceKey, route.category, route.param, reloadTick) {
        if (unifiedTag != null) {
            options = emptyList()
            load(1)
            return@LaunchedEffect
        }
        val optResult = sourceManager.getCategoryComicsOptions(route.sourceKey, route.category, route.param)
        val opts = optResult.getOrDefault(emptyList())
        options = opts
        // CategoryComicsOption 的默认值就是 options 的第一项（协议约定，无单独 defaultKey 字段）。
        selectedOptions = opts.map { it.options.keys.firstOrNull().orEmpty() }
        load(1)
    }

    // 屏蔽规则变化时重放过滤。
    // 屏蔽规则 / 打码模式变化时重放（仅 HIDE 才物理剔除；BLUR 依赖卡片级重算打码）。
    LaunchedEffect(guardRules, nsfwMaskMode) {
        if (comics.isNotEmpty() && nsfwMaskMode == "HIDE") comics = guardManager.filterComicModels(comics)
    }

    // 统一顶栏：大标题折叠 + 毛玻璃（与其他页面保持一致）
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 基础顶部间距 = 状态栏 + 折叠顶栏（约 104dp）
    val baseTopPadding = statusBarTop + 104.dp

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && comics.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(top = baseTopPadding),
                contentAlignment = Alignment.Center,
            ) {
                CenteredLoader()
            }
            error != null && comics.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(top = baseTopPadding),
                contentAlignment = Alignment.Center,
            ) {
                VeneraEmptyView(
                    title = "加载失败",
                    message = error.orEmpty(),
                    actionText = "重试",
                    onAction = { load(1) },
                )
            }
            comics.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(top = baseTopPadding),
                contentAlignment = Alignment.Center,
            ) {
                VeneraEmptyView(message = "该分类下暂无漫画")
            }
            else -> {
                // V4：列数与全局布局偏好同源（brief 双列 / detailed 单列），
                // 逐行 items 挂载，行级内存复用，不嵌套同向 Lazy 组件。
                val isDetailed = displayMode.value == "detailed"
                val columns = comicListColumnCount(displayMode.value)
                val rows = remember(comics, columns) { comics.chunked(columns) }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(topBarBehavior.nestedScrollConnection)
                        .blurBackdropSource(topBarBackdrop),
                    contentPadding = PaddingValues(
                        start = VeneraTokens.spacing.rowHorizontal,
                        end = VeneraTokens.spacing.rowHorizontal,
                        top = baseTopPadding,
                        bottom = VeneraTokens.spacing.bottomBarClearance,
                    ),
                    verticalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.gridGap),
                ) {
                    // 通用标签提示横幅
                    if (unifiedTag != null) {
                        item(key = "notice") {
                            NoticeBanner(
                                text = "「" + unifiedTag.label + "」是应用层通用标签，正在以关键词搜索 " + route.sourceTitle +
                                    "；它不会修改该源的任何原生分类。",
                            )
                        }
                    }

                    if (isDetailed) {
                        itemsIndexed(comics, key = { _, c -> "sc-${c.id}" }) { _, comic ->
                            SectionDetailedCard(comic, route.sourceTitle, guardManager, nsfwMaskMode, onSelectComic)
                        }
                    } else {
                        itemsIndexed(
                            rows,
                            key = { _, row -> "srow-${row.firstOrNull()?.id}" },
                        ) { _, row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.gridGap)) {
                                row.forEach { comic ->
                                    Box(Modifier.weight(1f)) {
                                        SectionComicCard(comic, route.sourceTitle, guardManager, nsfwMaskMode, onSelectComic)
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = VeneraTokens.spacing.space6),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (page > 1) {
                                PagerButton(text = "上一页", enabled = !isLoading) { load(page - 1) }
                                Spacer(Modifier.width(VeneraTokens.spacing.space7))
                            }
                            Text(
                                "第 " + page + " 页" + (maxPage?.let { " / " + it } ?: ""),
                                fontSize = VeneraTokens.type.caption,
                                color = VeneraTokens.color.textSecondary,
                            )
                            val canNext = maxPage == null || page < maxPage!!
                            if (canNext) {
                                Spacer(Modifier.width(VeneraTokens.spacing.space7))
                                PagerButton(text = "下一页", enabled = !isLoading) { load(page + 1) }
                            }
                        }
                    }
                }
            }
        }

        // 顶栏浮层：VeneraTopAppBar + 毛玻璃
        VeneraTopAppBar(
            title = route.category,
            largeTitle = route.category,
            subtitle = route.sourceTitle + if (unifiedTag != null) " · 通用标签搜索" else "",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回探索",
                        tint = VeneraTokens.color.textPrimary
                    )
                }
            },
            actions = {
                // R2：单列/双列切换，与一级页同款按钮、同一份偏好。
                ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
                IconButton(
                    onClick = { reloadTick++ },
                    modifier = Modifier.size(VeneraTokens.spacing.iconButtonSize),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = VeneraTokens.color.primary)
                }
            },
            bottomContent = {
                // 源自定义筛选项（options）放入顶栏底部内容，随顶栏折叠
                if (options.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = VeneraTokens.spacing.rowHorizontal, vertical = VeneraTokens.spacing.space2),
                        verticalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space2),
                    ) {
                        options.forEachIndexed { groupIdx, group ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (group.label.isNotBlank()) {
                                    Text(
                                        group.label + ":",
                                        fontSize = VeneraTokens.type.caption,
                                        fontWeight = VeneraTokens.type.weightSemibold,
                                        color = VeneraTokens.color.textSecondary,
                                        modifier = Modifier.padding(end = VeneraTokens.spacing.space2),
                                    )
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space2)) {
                                    items(group.options.entries.toList()) { (key, label) ->
                                        val selected = selectedOptions.getOrNull(groupIdx) == key
                                        VeneraChip(
                                            text = label,
                                            selected = selected,
                                            onClick = {
                                                if (!selected) {
                                                    val next = selectedOptions.toMutableList()
                                                    while (next.size <= groupIdx) next.add("")
                                                    next[groupIdx] = key
                                                    selectedOptions = next
                                                    load(1)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun CenteredLoader() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = VeneraTokens.color.primary)
    }
}

@Composable
private fun NoticeBanner(text: String) {
    Surface(
        shape = RoundedCornerShape(VeneraTokens.shape.small),
        color = VeneraTokens.color.surfaceVariant.copy(alpha = VeneraTokens.current.placeholderAlpha),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            fontSize = VeneraTokens.type.overline,
            color = VeneraTokens.color.textSecondary,
            modifier = Modifier.padding(horizontal = VeneraTokens.spacing.space5, vertical = VeneraTokens.spacing.space2),
        )
    }
}

@Composable
private fun PagerButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(color = VeneraTokens.color.surfaceVariant.copy(alpha = VeneraTokens.current.selectedSurfaceAlpha)),
    ) { Text(text, fontSize = VeneraTokens.type.caption, color = VeneraTokens.color.textPrimary) }
}

/** 单列大卡（detailed）：复用 ComicTileDetailed，打码状态同步传给封面。 */
@Composable
private fun SectionDetailedCard(
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
private fun SectionComicCard(
    comic: Comic,
    sourceName: String,
    guardManager: ContentGuardManager,
    nsfwMaskMode: String,
    onSelectComic: (ComicItem) -> Unit,
) {
    // R6：HIDE 模式下命中条目已在数据层剔除（兜底）；BLUR 模式保留条目、在此打码。
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
    }
}