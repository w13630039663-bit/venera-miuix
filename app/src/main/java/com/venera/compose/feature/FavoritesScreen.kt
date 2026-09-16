/**
 * S0-3 机械拆分自 MainActivity.kt（代码正文逐行原样搬运，未作改写）。
 */
package com.venera.compose.feature

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.reader.*
import com.venera.compose.data.db.*
import com.venera.compose.data.prefs.*
import com.venera.compose.source.model.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidFavoritesScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit
) {
    val context = LocalContext.current
    val favoriteList by FavoriteDao.getInstance(context).favoritesFlow.collectAsState()
    var currentFolder by remember { mutableStateOf("全部") }
    val folders = listOf("全部", "默认", "追更", "完结", "稍后")

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部收藏夹多分类 Tab
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(folders) { folder ->
                val isSelected = currentFolder == folder
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { currentFolder = folder }
                ) {
                    Text(
                        text = folder,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        val filteredList = if (currentFolder == "全部") {
            favoriteList
        } else {
            favoriteList.filter { it.folderName == currentFolder }
        }

        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(text = "⭐", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "暂无收藏漫画", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "在漫画详情页点击「收藏」即可加入此文件夹",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredList, key = { "fav-${it.comicId}" }) { record ->
                    // S0-5：不再回退 sampleComics，也不给每条收藏硬编 9.6 分
                    val match = record.toComicItem().copy(description = "已收藏至「${record.folderName}」")
                    Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(match) }) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            AsyncImage(
                                model = record.coverUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = record.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            Text(
                                text = "${record.sourceName} · ${record.folderName}",
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 5. 探索页 (ExplorePage) 1:1 复刻 ====================
