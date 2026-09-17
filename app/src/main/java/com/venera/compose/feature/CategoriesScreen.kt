package com.venera.compose.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.venera.compose.source.model.*
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分类页面（对齐原版 CategoriesPage 与 CategoryComicsPage）
 *
 * 核心特性：
 * 1. 动态自各真实源读取 category 矩阵元数据（如 拷贝漫画、包子漫画、MangaDex 等）
 * 2. 真实排行榜橱窗预览（Ranking Preview）
 * 3. 分类标签流转：点击任意题材/标签/分类，即刻下钻进入真实分类漫画流（CategoryComicsPage）
 * 4. 分类漫画流支持源自声明的 optionList 动态筛选（受众/排序/状态）与分页翻页
 * 5. 全程对接真实漫画源数据，杜绝硬编码假数据
 */
@Composable
fun AndroidCategoriesScreen(
    onSelect: (ComicItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceManager = remember { ComicSourceManager.getInstance(context) }

    // 所有支持分类的漫画源元数据
    var categorySources by remember { mutableStateOf<List<CategoryData>>(emptyList()) }
    var selectedSourceIndex by remember { mutableIntStateOf(0) }
    var isInitialLoading by remember { mutableStateOf(true) }

    // 下钻视图状态：非 null 时显示分类漫画列表视图 (CategoryComicsPage)
    var activeCategoryComicsView by remember { mutableStateOf<CategoryComicsViewArgs?>(null) }

    fun refreshCategorySources() {
        scope.launch {
            isInitialLoading = true
            val list = sourceManager.getAllCategories()
            categorySources = list
            isInitialLoading = false
            if (list.isNotEmpty()) {
                selectedSourceIndex = selectedSourceIndex.coerceIn(0, list.size - 1)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshCategorySources()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val currentDetail = activeCategoryComicsView
        if (currentDetail != null) {
            // 真实分类漫画瀑布流视图 (CategoryComicsPage)
            CategoryComicsScreen(
                args = currentDetail,
                onBack = { activeCategoryComicsView = null },
                onSelect = onSelect
            )
        } else {
            // 分类矩阵总览视图 (CategoriesPage)
            CategoryMatrixOverview(
                categorySources = categorySources,
                selectedIndex = selectedSourceIndex,
                isLoading = isInitialLoading,
                onSelectSource = { selectedSourceIndex = it },
                onRefresh = { refreshCategorySources() },
                onTagClick = { sourceKey, sourceTitle, category, param ->
                    activeCategoryComicsView = CategoryComicsViewArgs(
                        sourceKey = sourceKey,
                        sourceTitle = sourceTitle,
                        category = category,
                        param = param
                    )
                },
                onSelect = onSelect
            )
        }
    }
}

/**
 * 分类矩阵总览视图（分类胶囊 Tab + 排行榜预览 + 分类标签矩阵）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryMatrixOverview(
    categorySources: List<CategoryData>,
    selectedIndex: Int,
    isLoading: Boolean,
    onSelectSource: (Int) -> Unit,
    onRefresh: () -> Unit,
    onTagClick: (sourceKey: String, sourceTitle: String, category: String, param: String?) -> Unit,
    onSelect: (ComicItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 1. 顶部 Tab 栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (categorySources.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(categorySources) { index, catData ->
                        val isSelected = index == selectedIndex
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MiuixTheme.colorScheme.primaryContainer
                            else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { onSelectSource(index) }
                        ) {
                            Text(
                                text = catData.title,
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
                onClick = onRefresh,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "刷新",
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        }

        // 2. 主体矩阵
        when {
            isLoading -> {
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

            categorySources.isEmpty() -> {
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
                                text = "暂无可用分类页面",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "请在漫画源管理中启用支持分类的漫画源（如拷贝漫画、包子漫画等）",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)
                            ) {
                                Text("重新检测", color = Color.White)
                            }
                        }
                    }
                }
            }

            else -> {
                val currentCategory = categorySources.getOrNull(selectedIndex) ?: return
                val context = LocalContext.current
                val sourceManager = remember { ComicSourceManager.getInstance(context) }

                // 排行榜预览数据
                var rankingComics by remember(currentCategory.sourceKey) { mutableStateOf<List<Comic>?>(null) }
                LaunchedEffect(currentCategory.sourceKey, currentCategory.enableRankingPage) {
                    if (currentCategory.enableRankingPage) {
                        val res = sourceManager.loadCategoryRanking(currentCategory.sourceKey, "day", page = 1)
                        rankingComics = res.getOrNull()?.take(8)
                    } else {
                        rankingComics = null
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 排行榜区块预览（若该源支持）
                    if (currentCategory.enableRankingPage) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🏆 排行榜",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "查看完整榜单 ›",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable {
                                                onTagClick(
                                                    currentCategory.sourceKey,
                                                    currentCategory.title,
                                                    "排行",
                                                    "ranking"
                                                )
                                            }
                                            .padding(start = 8.dp)
                                    )
                                }

                                val list = rankingComics
                                if (list != null && list.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        itemsIndexed(list) { index, comic ->
                                            RankingPreviewCard(
                                                comic = comic,
                                                rank = index + 1,
                                                sourceName = currentCategory.title,
                                                onClick = {
                                                    onSelect(
                                                        ComicItem(
                                                            id = comic.id,
                                                            title = comic.title,
                                                            author = comic.subTitle,
                                                            coverUrl = comic.cover,
                                                            tags = comic.tags,
                                                            description = comic.description,
                                                            sourceName = currentCategory.title,
                                                            updateTime = comic.updateTime
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 分类各分区（如 题材、地区、进度、类型）
                    items(currentCategory.parts) { part ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = part.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (item in part.items) {
                                    val targetParam = item.target.attributes?.get("param")?.toString()
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.clickable {
                                            onTagClick(
                                                currentCategory.sourceKey,
                                                currentCategory.title,
                                                item.label,
                                                targetParam
                                            )
                                        }
                                    ) {
                                        Text(
                                            text = item.label,
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
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

/**
 * 分类漫画真实瀑布流页面 (CategoryComicsPage)
 */
@Composable
private fun CategoryComicsScreen(
    args: CategoryComicsViewArgs,
    onBack: () -> Unit,
    onSelect: (ComicItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceManager = remember { ComicSourceManager.getInstance(context) }
    val guardManager = remember { com.venera.compose.security.guard.ContentGuardManager.getInstance(context) }
    val guardRules by guardManager.rules.collectAsState()

    var optionsList by remember { mutableStateOf<List<CategoryComicsOption>>(emptyList()) }
    var selectedOptions by remember { mutableStateOf<List<String>>(emptyList()) }

    var comics by remember { mutableStateOf<List<Comic>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(1) }
    var maxPage by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadComics(page: Int) {
        scope.launch {
            isLoading = true
            errorMessage = null
            currentPage = page
            val res = sourceManager.loadCategoryComics(
                sourceKey = args.sourceKey,
                category = args.category,
                param = args.param,
                options = selectedOptions,
                page = page
            )
            if (res.isSuccess) {
                val data = res.getOrNull()
                // S7 内容守卫：分类漫画流同步剔除命中屏蔽规则的作品
                comics = guardManager.filterComicModels(data?.comics.orEmpty())
                maxPage = data?.maxPage
            } else {
                comics = emptyList()
                errorMessage = res.exceptionOrNull()?.message ?: "加载分类漫画失败"
            }
            isLoading = false
        }
    }

    // 屏蔽规则变化时对当前列表重放过滤
    LaunchedEffect(guardRules) {
        if (comics.isNotEmpty()) {
            comics = guardManager.filterComicModels(comics)
        }
    }

    // 初始化加载筛选选项
    LaunchedEffect(args.sourceKey, args.category, args.param) {
        val optRes = sourceManager.getCategoryComicsOptions(args.sourceKey, args.category, args.param)
        val opts = optRes.getOrDefault(emptyList())
        optionsList = opts
        // 初始选择各项的第 1 个 option key
        selectedOptions = opts.map { it.options.keys.firstOrNull() ?: "" }
        loadComics(page = 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // 顶部导航栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回分类",
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = args.category,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = args.sourceTitle,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = { loadComics(currentPage) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "刷新",
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        }

        // 筛选选项行（如果该分类有筛选条件）
        if (optionsList.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                optionsList.forEachIndexed { groupIdx, optionGroup ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (optionGroup.label.isNotBlank()) {
                            Text(
                                text = "${optionGroup.label}:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(optionGroup.options.entries.toList()) { (optKey, optLabel) ->
                                val isSelected = selectedOptions.getOrNull(groupIdx) == optKey
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MiuixTheme.colorScheme.primaryContainer
                                    else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.clickable {
                                        if (!isSelected) {
                                            val newOpts = selectedOptions.toMutableList()
                                            while (newOpts.size <= groupIdx) newOpts.add("")
                                            newOpts[groupIdx] = optKey
                                            selectedOptions = newOpts
                                            loadComics(page = 1)
                                        }
                                    }
                                ) {
                                    Text(
                                        text = optLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 漫画瀑布流 / 状态
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && comics.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MiuixTheme.colorScheme.primary)
                    }
                }

                errorMessage != null && comics.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "加载失败", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = errorMessage.orEmpty(), fontSize = 12.sp, color = Color(0xFFE53935))
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { loadComics(page = 1) },
                                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)
                                ) {
                                    Text("重试", color = Color.White)
                                }
                            }
                        }
                    }
                }

                comics.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "该分类下暂无漫画",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                else -> {
                    val chunked = remember(comics) { comics.chunked(2) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(chunked) { rowComics ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (comic in rowComics) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSelect(
                                                        ComicItem(
                                                            id = comic.id,
                                                            title = comic.title,
                                                            author = comic.subTitle,
                                                            coverUrl = comic.cover,
                                                            tags = comic.tags,
                                                            description = comic.description,
                                                            sourceName = args.sourceTitle.ifBlank { comic.sourceKey },
                                                            updateTime = comic.updateTime
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
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = comic.title,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MiuixTheme.colorScheme.onSurface,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val sub = comic.subTitle.ifBlank {
                                                    comic.tags.take(2).joinToString(" · ")
                                                }
                                                if (sub.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
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
                                }
                                if (rowComics.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        // 分页控制栏
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentPage > 1) {
                                    Button(
                                        onClick = { loadComics(page = currentPage - 1) },
                                        colors = ButtonDefaults.buttonColors(
                                            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        )
                                    ) {
                                        Text("上一页", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                }

                                Text(
                                    text = "第 $currentPage 页" + (maxPage?.let { " / $it" } ?: ""),
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )

                                val canGoNext = maxPage == null || currentPage < maxPage!!
                                if (canGoNext) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Button(
                                        onClick = { loadComics(page = currentPage + 1) },
                                        colors = ButtonDefaults.buttonColors(
                                            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        )
                                    ) {
                                        Text("下一页", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface)
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
 * 排行榜横向预览卡片
 */
@Composable
private fun RankingPreviewCard(
    comic: Comic,
    rank: Int,
    sourceName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = comic.cover,
                    contentDescription = comic.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(6.dp))
                )
                // 排名胶囊角标
                Surface(
                    shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
                    color = when (rank) {
                        1 -> Color(0xFFFFD700)
                        2 -> Color(0xFFC0C0C0)
                        3 -> Color(0xFFCD7F32)
                        else -> Color(0xCC000000)
                    },
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "$rank",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rank <= 3) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = comic.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 传递给 CategoryComicsScreen 的参数
 */
data class CategoryComicsViewArgs(
    val sourceKey: String,
    val sourceTitle: String,
    val category: String,
    val param: String?
)
