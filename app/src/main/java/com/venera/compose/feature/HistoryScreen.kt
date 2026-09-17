/**
 * S5-4 历史页（对齐原版 `pages/history_page.dart`）。
 *
 * 原版是**网格** + 多选删除 + 清空（全部 / 仅未收藏），并没有「今天/昨天/更早」
 * 的时间分组（那是分阶段任务书里的设想）。这里按官方实现：网格 + 多选 + 清空。
 */
package com.venera.compose.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.venera.compose.data.db.HistoryRecord
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AndroidHistoryScreen(
    onBack: () -> Unit,
    onSelect: (ComicItem) -> Unit,
) {
    val vm: HistoryViewModel = viewModel()
    val records by vm.history.collectAsState()
    var showClearMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (vm.multiSelectMode) Icons.Filled.Close else Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp).clickable {
                        if (vm.multiSelectMode) vm.exitMultiSelect() else onBack()
                    },
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = if (vm.multiSelectMode) "已选择 ${vm.selected.size} 项" else "历史记录",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (vm.multiSelectMode) {
                    Text(
                        text = "全选",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.clickable { vm.selectAll(records) }.padding(6.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "删除",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.clickable { vm.deleteSelected() }.padding(6.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp).clickable { showClearMenu = true },
                    )
                }
            }

            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🕘", fontSize = 46.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "还没有阅读记录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "去随便翻两页吧",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(records, key = { "${it.comicId}-${it.sourceName}" }) { record ->
                        HistoryCard(
                            record = record,
                            selected = (record.comicId to record.sourceName) in vm.selected,
                            multiSelectMode = vm.multiSelectMode,
                            onClick = {
                                if (vm.multiSelectMode) {
                                    vm.toggleSelect(record)
                                } else {
                                    onSelect(record.toComicItem())
                                }
                            },
                            onLongClick = { vm.enterMultiSelect(record) },
                        )
                    }
                }
            }
        }
    }

    if (showClearMenu) {
        ClearHistoryMenu(
            onDismiss = { showClearMenu = false },
            onClearUnfavorited = { vm.clearUnfavorited() },
            onClearAll = { vm.clearAll() },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    record: HistoryRecord,
    selected: Boolean,
    multiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            Column(modifier = Modifier.padding(6.dp)) {
                AsyncImage(
                    model = record.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(text = record.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    text = record.progressDescription(),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = record.sourceName,
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                )
            }
            if (multiSelectMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MiuixTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ClearHistoryMenu(
    onDismiss: () -> Unit,
    onClearUnfavorited: () -> Unit,
    onClearAll: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(horizontal = 28.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "清空历史记录", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "「清空未收藏」会保留仍在你收藏夹里的漫画的阅读进度。",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onClearUnfavorited(); onDismiss() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                    ) { Text("清空未收藏") }
                    Button(
                        onClick = { onClearAll(); onDismiss() },
                        modifier = Modifier.weight(1f),
                    ) { Text("全部清空") }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                ) { Text("取消") }
            }
        }
    }
}

/** 无涟漪点击，用于图标/文字这类小目标。 */
