package com.venera.compose.feature

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ExplorePageData
import com.venera.compose.source.model.ExplorePagePart
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 探索页面（对齐原版 ExplorePage）
 *
 * 核心特性：
 * 1. 动态自全量已启用源读取 explore 页面定义（如 拷贝漫画、包子漫画、禁漫、MangaDex 等）
 * 2. 支持单页多分区块（singlePageWithMultiPart，如推荐、热门、今日排行、最新更新等）
 * 3. 支持多页列表流（multiPageComicList）及分页上拉加载
 * 4. 原生 Miuix 胶囊分段 Tab 切换与沉浸式卡片流
 * 5. 全程对接真实漫画源数据，杜绝硬编码假数据
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidExploreScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceManager = remember { ComicSourceManager.getInstance(context) }
    val guardManager = remember { com.venera.compose.security.guard.ContentGuardManager.getInstance(context) }
    // 屏蔽规则变化时重新对已加载分区执行过滤（S7 内容守卫实际生效点）
    val guardRules by guardManager.rules.collectAsState()

    var explorePages by remember { mutableStateOf<List<ExplorePageData>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isInitialLoading by remember { mutableStateOf(true) }

    var parts by remember { mutableStateOf<List<ExplorePagePart>>(emptyList()) }
    var isLoadingContent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }

    fun loadContentForTab(tab: ExplorePageData, page: Int = 1) {
        scope.launch {
            isLoadingContent = true
            errorMessage = null
            currentPage = page
            val res = sourceManager.loadExplorePage(tab.sourceKey, tab.pageIndex, page)
            if (res.isSuccess) {
                // S7 内容守卫：命中屏蔽词/标签/作者/ID 的条目在此剔除（空分区整块隐藏）
                val newParts = guardManager.filterExploreParts(res.getOrDefault(emptyList()))
                if (page == 1) {
                    parts = newParts
                } else {
                    // 追加列表
                    if (newParts.isNotEmpty()) {
                        val firstNew = newParts.first()
                        val oldList = parts.toMutableList()
                        if (oldList.isNotEmpty() && oldList.last().title == firstNew.title) {
                            val merged = oldList.last().copy(comics = oldList.last().comics + firstNew.comics)
                            oldList[oldList.size - 1] = merged
                            parts = oldList
                        } else {
                            parts = oldList + newParts
                        }
                    }
                }
                hasMore = newParts.isNotEmpty() && tab.type != "singlePageWithMultiPart"
            } else {
                if (page == 1) {
                    parts = emptyList()
                    errorMessage = res.exceptionOrNull()?.message ?: "加载探索内容失败"
                }
            }
            isLoadingContent = false
        }
    }

    fun refreshAll() {
        scope.launch {
            isInitialLoading = true
            val pages = sourceManager.getAllExplorePages()
            explorePages = pages
            isInitialLoading = false
            if (pages.isNotEmpty()) {
                val safeIdx = selectedIndex.coerceIn(0, pages.size - 1)
                selectedIndex = safeIdx
                loadContentForTab(pages[safeIdx], page = 1)
            } else {
                parts = emptyList()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    // 屏蔽规则被增删/启停时，对当前已加载内容立即重放过滤（无需手动刷新）
    LaunchedEffect(guardRules) {
        if (parts.isNotEmpty()) {
            parts = guardManager.filterExploreParts(parts)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 1. 顶部 Tab 栏与刷新按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (explorePages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(explorePages) { index, pageData ->
                        val isSelected = index == selectedIndex
                        val displayName = if (pageData.title.contains(pageData.sourceName)) {
                            pageData.title
                        } else {
                            "${pageData.sourceName} · ${pageData.title}"
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MiuixTheme.colorScheme.primaryContainer
                            else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable {
                                if (selectedIndex != index) {
                                    selectedIndex = index
                                    loadContentForTab(pageData, page = 1)
                                }
                            }
                        ) {
                            Text(
                                text = displayName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            IconButton(
                onClick = {
                    val currentTab = explorePages.getOrNull(selectedIndex)
                    if (currentTab != null) {
                        loadContentForTab(currentTab, page = 1)
                    } else {
                        refreshAll()
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "刷新",
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        }

        // 2. 主体内容展示区域
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isInitialLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MiuixTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                }

                explorePages.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "暂无可用探索页面",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "请在漫画源管理中启用支持探索的漫画源（如拷贝漫画、包子漫画等）",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { refreshAll() },
                                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)
                                ) {
                                    Text("重新检测", color = Color.White)
                                }
                            }
                        }
                    }
                }

                errorMessage != null && parts.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "探索内容加载失败",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = errorMessage.orEmpty(),
                                    fontSize = 13.sp,
                                    color = Color(0xFFE53935)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                val currentTab = explorePages.getOrNull(selectedIndex)
                                Button(
                                    onClick = {
                                        if (currentTab != null) loadContentForTab(currentTab, page = 1)
                                    },
                                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)
                                ) {
                                    Text("重试", color = Color.White)
                                }
                            }
                        }
                    }
                }

                else -> {
                    val currentTab = explorePages.getOrNull(selectedIndex)
                    val currentSourceName = currentTab?.sourceName ?: ""

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(parts) { part ->
                            ExploreSectionView(
                                part = part,
                                sourceName = currentSourceName,
                                onSelect = onSelect
                            )
                        }

                        if (hasMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingContent) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MiuixTheme.colorScheme.primary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Button(
                                            onClick = {
                                                if (currentTab != null) {
                                                    loadContentForTab(currentTab, page = currentPage + 1)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                            )
                                        ) {
                                            Text(
                                                text = "加载下一页",
                                                fontSize = 13.sp,
                                                color = MiuixTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 探索分区展示组件（分区大标题 + 2列卡片网格）
 */
@Composable
private fun ExploreSectionView(
    part: ExplorePagePart,
    sourceName: String,
    onSelect: (ComicItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 分区标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = part.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            if (part.viewMore != null) {
                Text(
                    text = "查看更多 ›",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // 2列流式排列漫画卡片
        val comics = part.comics
        val chunked = remember(comics) { comics.chunked(2) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (rowComics in chunked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (comic in rowComics) {
                        Box(modifier = Modifier.weight(1f)) {
                            ExploreComicCard(
                                comic = comic,
                                sourceName = sourceName,
                                onClick = {
                                    onSelect(
                                        ComicItem(
                                            id = comic.id,
                                            title = comic.title,
                                            author = comic.subTitle,
                                            coverUrl = comic.cover,
                                            tags = comic.tags,
                                            description = comic.description,
                                            sourceName = sourceName.ifBlank { comic.sourceKey },
                                            updateTime = comic.updateTime
                                        )
                                    )
                                }
                            )
                        }
                    }
                    if (rowComics.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 单张漫画卡片（对齐原版 Miuix 2列画风）
 */
@Composable
private fun ExploreComicCard(
    comic: Comic,
    sourceName: String,
    onClick: () -> Unit
) {
    // S7 分级遮罩：BLUR 模式下命中规则的封面打码（HIDE 已在数据层整条剔除，此处兜底）
    val guardManager = com.venera.compose.security.guard.ContentGuardManager.getInstance(LocalContext.current)
    val nsfwMode by guardManager.nsfwMaskMode.collectAsState()
    val maskState = remember(comic.id, nsfwMode) {
        guardManager.coverMaskStateFor(comic.title, comic.subTitle, comic.tags, comic.id)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 封面图片（3:4 比例）
            AsyncImage(
                model = comic.cover,
                contentDescription = comic.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (maskState == "BLURRED") Modifier.blur(18.dp) else Modifier
                    )
            )
            if (maskState == "BLURRED") {
                // 打码角标
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "R18 · 已打码",
                        fontSize = 10.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 标题
            Text(
                text = comic.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            // 副标题 / 作者 / 标签
            val subText = comic.subTitle.ifBlank {
                comic.tags.take(2).joinToString(" · ")
            }
            if (subText.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subText,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
