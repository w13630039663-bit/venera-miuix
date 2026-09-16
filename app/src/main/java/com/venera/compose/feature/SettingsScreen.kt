/**
 * S0-3 机械拆分自 MainActivity.kt（代码正文逐行原样搬运，未作改写）。
 */
package com.venera.compose.feature

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.reader.*
import com.venera.compose.data.db.*
import com.venera.compose.data.prefs.*
import com.venera.compose.source.model.*

@Composable
fun AndroidSettingsScreen(
    onNavigateToSourceManage: () -> Unit = {}
) {
    val settingCategories = listOf(
        Triple("探索与主页配置", Icons.Filled.Explore, Color(0xFF2196F3)),
        Triple("内容屏蔽与过滤", Icons.Filled.FilterAlt, Color(0xFFE53935)),
        Triple("阅读器体验", Icons.Filled.Book, Color(0xFF4CAF50)),
        Triple("外观与动效", Icons.Filled.ColorLens, Color(0xFF9C27B0)),
        Triple("本地与收藏夹", Icons.Filled.CollectionsBookmark, Color(0xFFFF9800)),
        Triple("应用与通用", Icons.Filled.Apps, Color(0xFF00BCD4)),
        Triple("网络与代理", Icons.Filled.Public, Color(0xFF009688))
    )

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部 About 区块
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MiuixTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "V", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Venera Compose", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "版本 1.0.0 · 纯原生复刻版", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)) {
                            Text(text = "检查更新", fontSize = 11.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                            Text(text = "GitHub 源码", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }

        // 重点推荐入口：漫画源扩展管理
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSourceManage() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF673AB7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Extension,
                            contentDescription = "漫画源扩展管理",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "漫画源扩展管理", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "管理 33 条官方/本地规则源、测速与更新",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant
                        )
                    }
                    Text(text = "›", fontSize = 20.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            }
        }

        // 7 大分类设置入口 (配备原版专属彩色方块徽章)
        items(settingCategories) { (title, icon, badgeColor) ->
            Card(modifier = Modifier.fillMaxWidth().clickable { }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(text = "›", fontSize = 20.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                }
            }
        }
    }
}
