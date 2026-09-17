package com.venera.compose.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.venera.compose.feature.ComicItem
import com.venera.compose.feature.SourceSectionRoute
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.explore.UnifiedTag
import com.venera.compose.source.model.CategoryComicsOption
import com.venera.compose.source.model.Comic
import com.venera.compose.security.guard.ContentGuardManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 某个漫画源的**原生**分类 / Tag 下钻页。
 *
 * 与旧 CategoriesScreen 的区别：
 *  - 这里是「某一个源」的视图，标题始终带你选的源名，不会串源。
 *  - 该分类有源自定义的 optionList（受众 / 排序 / 状态…）时按源声明渲染，不硬编码筛选项。
 *  - 若入口来自应用层「通用标签」，则退化为按关键词搜索该源，不触碰源的原生分类。
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
                sourceManager.search(route.sourceKey, unifiedTag.label, targetPage, null)
                    .map { list -> list to null }
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
                    comics = guardManager.filterComicModels(list)
                    maxPage = mp
                    page = targetPage
                },
                onFailure = { e ->
                    // 排行榜先用分类接口；失败时退回关键词搜索，避免整页空白。
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
    LaunchedEffect(guardRules) {
        if (comics.isNotEmpty()) comics = guardManager.filterComicModels(comics)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回探索",
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    route.category,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // 源名始终可见：让用户明确「这是哪个源的分类」。
                    route.sourceTitle + if (unifiedTag != null) " · 通用标签搜索" else "",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = { reloadTick++ }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = MiuixTheme.colorScheme.primary)
            }
        }

        if (unifiedTag != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "「" + unifiedTag.label + "」是应用层通用标签，正在以关键词搜索 " + route.sourceTitle +
                        "；它不会修改该源的任何原生分类。",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (options.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                options.forEachIndexed { groupIdx, group ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (group.label.isNotBlank()) {
                            Text(
                                group.label + ":",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(group.options.entries.toList()) { (key, label) ->
                                val selected = selectedOptions.getOrNull(groupIdx) == key
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MiuixTheme.colorScheme.primaryContainer
                                    else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.clickable {
                                        if (!selected) {
                                            val next = selectedOptions.toMutableList()
                                            while (next.size <= groupIdx) next.add("")
                                            next[groupIdx] = key
                                            selectedOptions = next
                                            load(1)
                                        }
                                    }
                                ) {
                                    Text(
                                        label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MiuixTheme.colorScheme.primary
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

        Spacer(Modifier.height(6.dp))

        Box(Modifier.fillMaxSize()) {
            when {
                isLoading && comics.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MiuixTheme.colorScheme.primary)
                }
                error != null && comics.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("加载失败", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(error.orEmpty(), fontSize = 12.sp, color = Color(0xFFE53935))
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { load(1) },
                                colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)
                            ) { Text("重试", color = Color.White) }
                        }
                    }
                }
                comics.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "该分类下暂无漫画",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                else -> {
                    val rows = remember(comics) { comics.chunked(2) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(rows) { _, row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { comic ->
                                    Box(Modifier.weight(1f)) {
                                        SectionComicCard(comic, route.sourceTitle, onSelectComic)
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (page > 1) {
                                    Button(
                                        onClick = { load(page - 1) },
                                        colors = ButtonDefaults.buttonColors(
                                            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        )
                                    ) { Text("上一页", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface) }
                                    Spacer(Modifier.width(16.dp))
                                }
                                Text(
                                    "第 " + page + " 页" + (maxPage?.let { " / " + it } ?: ""),
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                val canNext = maxPage == null || page < maxPage!!
                                if (canNext) {
                                    Spacer(Modifier.width(16.dp))
                                    Button(
                                        onClick = { load(page + 1) },
                                        colors = ButtonDefaults.buttonColors(
                                            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        )
                                    ) { Text("下一页", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionComicCard(
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
        Column(Modifier.padding(8.dp)) {
            AsyncImage(
                model = comic.cover,
                contentDescription = comic.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.height(6.dp))
            Text(
                comic.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
