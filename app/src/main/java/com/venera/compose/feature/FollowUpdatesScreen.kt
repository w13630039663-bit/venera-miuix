/**
 * S5-5 追更 / 更新列表页（对齐原版 `pages/follow_updates_page.dart`）。
 */
package com.venera.compose.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AndroidFollowUpdatesScreen(
    onBack: () -> Unit,
    onSelect: (ComicItem) -> Unit,
) {
    val vm: FollowUpdatesViewModel = viewModel()
    var showFolderPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp).clickable { onBack() },
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "追更更新",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "立即检查",
                    tint = if (vm.isChecking) MiuixTheme.colorScheme.onBackgroundVariant
                    else MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).clickable(enabled = !vm.isChecking) { vm.checkNow() },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "设置",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showFolderPicker = true }.padding(6.dp),
                )
            }

            if (vm.followFolder.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🔔", fontSize = 46.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "还没有开启追更", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "点右上角「设置」选择一个收藏夹作为追更夹",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = "追更夹：${vm.followFolder}",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
                if (vm.progress != null) {
                    Text(
                        text = vm.progress!!,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }

                if (vm.updates.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "✨", fontSize = 42.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = if (vm.isChecking) "正在检查…" else "暂无新更新", fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "每天会自动检查一次，也可以点右上角立即检查",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(vm.updates, key = { "${it.item.id}-${it.item.type}" }) { entry ->
                            UpdateRow(
                                title = entry.item.name,
                                cover = entry.item.coverPath,
                                sourceName = entry.item.sourceKey,
                                updateTime = entry.updateTime,
                                onClick = {
                                    onSelect(
                                        ComicItem(
                                            id = entry.item.id,
                                            title = entry.item.name,
                                            author = entry.item.author,
                                            coverUrl = entry.item.coverPath,
                                            tags = entry.item.tags,
                                            sourceName = entry.item.sourceKey,
                                        )
                                    )
                                    vm.markAsRead(entry)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (showFolderPicker) {
            FollowFolderPicker(
                current = vm.followFolder,
                onDismiss = { showFolderPicker = false },
                onPick = { vm.chooseFollowFolder(it) },
            )
        }
    }
}

@Composable
private fun UpdateRow(
    title: String,
    cover: String,
    sourceName: String,
    updateTime: String?,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier
                    .width(64.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(sourceName)
                        if (!updateTime.isNullOrBlank()) append(" · $updateTime")
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 1,
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MiuixTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = "NEW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun FollowFolderPicker(
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val vm: FollowUpdatesViewModel = viewModel()
    val folders by vm.folderList().collectAsState(initial = emptyList())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(horizontal = 28.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "选择追更收藏夹", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "选中的收藏夹会每天自动检查一次更新。",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    folders.forEach { folder ->
                        val isCurrent = folder == current
                        Text(
                            text = if (isCurrent) "$folder ✔" else folder,
                            fontSize = 15.sp,
                            color = if (isCurrent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(if (isCurrent) null else folder)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                ) { Text("取消") }
            }
        }
    }
}
