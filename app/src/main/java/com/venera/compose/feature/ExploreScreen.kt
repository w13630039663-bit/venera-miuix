/**
 * S0-3 机械拆分自 MainActivity.kt（代码正文逐行原样搬运，未作改写）。
 */
package com.venera.compose.feature

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
fun SharedTransitionScope.AndroidExploreScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit
) {
    var selectedTab by remember { mutableStateOf("拷贝漫画 · 热门") }
    val exploreTabs = listOf(
        "拷贝漫画 · 热门",
        "拷贝漫画 · 更新",
        "哔咔 · 日榜",
        "哔咔 · 周榜",
        "MangaDex · 热门"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(exploreTabs) { tabName ->
                val isSelected = selectedTab == tabName
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { selectedTab = tabName }
                ) {
                    Text(
                        text = tabName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // S0-5：探索内容必须由源提供（ComicSource 目前还没有 getExploreComics 能力槽，
            // 见差距清单 §2.3 与阶段书 S1-7），因此这里不再铺 sampleComics 假数据。
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(text = "探索功能等待漫画源协议接入", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "当前 3 个手写源没有 explore 能力；官方 33 条 .js 规则源的 explore 依赖 S1 脚本引擎。",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            }
        }
    }
}

// ==================== 6. 分类页 (CategoriesPage) 1:1 复刻 ====================
