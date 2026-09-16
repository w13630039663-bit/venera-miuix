package com.venera.compose.feature

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.Comic
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分类矩阵与分类漫画瀑布流页面 (S4 核心重构)
 *
 * 核心特性：
 * 1. 分类矩阵页 (category{fixed/random/dynamic})：多维度题材、地区、进度筛选
 * 2. 热门排行榜入口 (日榜/周榜/月榜/总榜)
 * 3. 动态流转：点击任意标签或分类即刻进入分类漫画瀑布流并支持分页
 * 4. 多源联动：在拷贝漫画、包子漫画、MangaDex 分类矩阵间无缝切换
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AndroidCategoriesScreen(
    onSelect: (ComicItem) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val sourceManager = remember { ComicSourceManager.getInstance(context) }

    // 活跃源选择
    var selectedSourceKey by remember { mutableStateOf("copy_manga") }

    // 当前选中的分类筛选（null 表示在分类矩阵总览，非 null 表示在分类漫画流视图）
    var activeCategoryFilter by remember { mutableStateOf<String?>(null) }
    var activeCategoryTitle by remember { mutableStateOf<String?>(null) }

    // 分类漫画列表状态
    var comicList by remember { mutableStateOf<List<Comic>>(emptyList()) }
    var isLoadingComics by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }

    // 数据拉取函数
    fun loadComicsForCategory(filter: String, page: Int = 1) {
        scope.launch {
            isLoadingComics = true
            currentPage = page
            val res = sourceManager.search(selectedSourceKey, filter, page)
            comicList = res.getOrDefault(emptyList())
            isLoadingComics = false
        }
    }

    // 分类矩阵定义
    val copyMangaMatrix = remember {
        listOf(
            "热门榜单" to listOf("🏆 日榜", "🏆 周榜", "🏆 月榜", "🏆 总榜"),
            "题材分类" to listOf("热血", "恋爱", "校园", "冒险", "搞笑", "百合", "耽美", "科幻", "魔幻", "悬疑", "奇幻", "治愈", "历史", "战争", "萌系", "武侠", "格斗"),
            "地区分类" to listOf("日本", "国产", "欧美", "韩国"),
            "连载进度" to listOf("连载中", "已完结")
        )
    }

    val baoziMatrix = remember {
        listOf(
            "题材分类" to listOf("热血", "恋爱", "纯爱", "奇幻", "冒险", "科幻", "武侠", "搞笑", "悬疑", "都市"),
            "连载进度" to listOf("连载", "完结"),
            "地区分类" to listOf("国漫", "日本", "韩国", "欧美")
        )
    }

    val mangaDexMatrix = remember {
        listOf(
            "Genres" to listOf("Action", "Romance", "Fantasy", "Comedy", "Sci-Fi", "Mystery", "Horror", "Slice of Life", "Supernatural", "Adventure"),
            "Themes" to listOf("School Life", "Isekai", "Harem", "Magic", "Military", "Historical", "Psychological"),
            "Format" to listOf("Manga", "Manhwa", "Manhua")
        )
    }

    val currentMatrix = when (selectedSourceKey) {
        "copy_manga" -> copyMangaMatrix
        "baozi" -> baoziMatrix
        else -> mangaDexMatrix
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .padding(top = 8.dp)
    ) {
        // ==================== 顶部源切换胶囊条 ====================
        val sources = listOf(
            "copy_manga" to "拷贝漫画",
            "baozi" to "包子漫画",
            "manga_dex" to "MangaDex"
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sources) { (key, name) ->
                val isSelected = selectedSourceKey == key
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        selectedSourceKey = key
                        activeCategoryFilter = null
                    }
                ) {
                    Text(
                        text = name,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (activeCategoryFilter == null) {
            // ==================== 1. 分类矩阵总览 ====================
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(currentMatrix) { (group, tags) ->
                    Text(text = group, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (tag.startsWith("🏆")) MiuixTheme.colorScheme.primary.copy(alpha = 0.2f) else MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    modifier = Modifier.clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        val cleanFilter = tag.removePrefix("🏆").trim()
                                        activeCategoryFilter = cleanFilter
                                        activeCategoryTitle = tag
                                        loadComicsForCategory(cleanFilter, 1)
                                    }
                                ) {
                                    Text(
                                        text = "$tag ›",
                                        fontSize = 13.sp,
                                        fontWeight = if (tag.startsWith("🏆")) FontWeight.Bold else FontWeight.Normal,
                                        color = if (tag.startsWith("🏆")) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ==================== 2. 分类漫画瀑布流视图 ====================
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                activeCategoryFilter = null
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "返回矩阵", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "分类矩阵", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = activeCategoryTitle ?: "",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 分页指示
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (currentPage > 1) MiuixTheme.colorScheme.primaryContainer else Color.DarkGray.copy(alpha = 0.3f),
                            modifier = Modifier.clickable(enabled = currentPage > 1) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                activeCategoryFilter?.let { loadComicsForCategory(it, currentPage - 1) }
                            }
                        ) {
                            Text(text = "上一页", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Text(text = "第 $currentPage 页", fontSize = 12.sp, color = Color.Gray)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MiuixTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                activeCategoryFilter?.let { loadComicsForCategory(it, currentPage + 1) }
                            }
                        ) {
                            Text(text = "下一页", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoadingComics) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MiuixTheme.colorScheme.primary)
                    }
                } else if (comicList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "当前分类下暂无漫画", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(comicList, key = { it.id }) { comic ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        val srcName = sources.find { it.first == selectedSourceKey }?.second ?: "拷贝漫画"
                                        onSelect(
                                            ComicItem(
                                                id = comic.id,
                                                title = comic.title,
                                                author = comic.subTitle,
                                                coverUrl = comic.cover,
                                                sourceName = srcName,
                                                tags = comic.tags,
                                                description = comic.description
                                            )
                                        )
                                    }
                            ) {
                                AsyncImage(
                                    model = comic.cover,
                                    contentDescription = comic.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.72f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF222222))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = comic.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = comic.subTitle,
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
