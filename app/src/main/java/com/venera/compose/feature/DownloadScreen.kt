package com.venera.compose.feature

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import com.venera.compose.download.DownloadManager
import com.venera.compose.download.DownloadStatus
import com.venera.compose.download.DownloadTask
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 生产级下载中心管理页面 (S6)
 */
@Composable
fun DownloadScreen(
    onBack: () -> Unit,
    onNavigateToLocalLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val tasks by downloadManager.tasks.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 下载中/排队, 1: 已完成

    val activeTasks = remember(tasks) {
        tasks.filter { it.status != DownloadStatus.COMPLETED }.sortedByDescending { it.createTime }
    }
    val completedTasks = remember(tasks) {
        tasks.filter { it.status == DownloadStatus.COMPLETED }.sortedByDescending { it.updateTime }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "离线下载管理",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onNavigateToLocalLibrary) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "本地书架",
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "本地书架",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab 切换条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedTab == 0) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 0 }
                ) {
                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "下载队列 (${activeTasks.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) Color.White else MiuixTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedTab == 1) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 1 }
                ) {
                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "已完成 (${completedTasks.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) Color.White else MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 控制条（全部开始/全部暂停/清空）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedTab == 0) {
                    TextButton(onClick = { downloadManager.resumeAll() }) {
                        Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = "全部继续", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(onClick = { downloadManager.pauseAll() }) {
                        Icon(imageVector = Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = "全部暂停", fontSize = 12.sp)
                    }
                } else {
                    TextButton(onClick = {
                        downloadManager.clearCompleted()
                        Toast.makeText(context, "已清空已完成记录", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(imageVector = Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = "清空记录", fontSize = 12.sp)
                    }
                }
            }

            val currentList = if (selectedTab == 0) activeTasks else completedTasks

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Outlined.CloudDownload else Icons.Outlined.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (selectedTab == 0) "当前没有正在下载的任务" else "暂无已完成的离线章节",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(currentList, key = { it.taskId }) { task ->
                        DownloadTaskCard(
                            task = task,
                            onPause = { downloadManager.pause(task.taskId) },
                            onResume = { downloadManager.resume(task.taskId) },
                            onDelete = { downloadManager.delete(task.taskId, deleteFiles = true) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面图
            AsyncImage(
                model = task.comicCover,
                contentDescription = task.comicTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 54.dp, height = 74.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 任务信息与进度
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.comicTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = task.chapterTitle,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))

                val progress = if (task.totalPages > 0) {
                    (task.downloadedPages.toFloat() / task.totalPages).coerceIn(0f, 1f)
                } else 0f

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when (task.status) {
                        DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
                        DownloadStatus.FAILED -> Color(0xFFF44336)
                        DownloadStatus.PAUSED -> Color(0xFFFF9800)
                        else -> MiuixTheme.colorScheme.primary
                    },
                    trackColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when (task.status) {
                        DownloadStatus.DOWNLOADING -> "下载中 ${task.speedText.ifBlank { "" }}"
                        DownloadStatus.PENDING -> "等待队列中..."
                        DownloadStatus.PAUSED -> "已暂停"
                        DownloadStatus.COMPLETED -> "已完成"
                        DownloadStatus.FAILED -> "下载失败: ${task.errorMsg ?: "网络异常"}"
                        DownloadStatus.CANCELED -> "已取消"
                    }
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        color = when (task.status) {
                            DownloadStatus.COMPLETED -> Color(0xFF388E3C)
                            DownloadStatus.FAILED -> Color(0xFFD32F2F)
                            DownloadStatus.DOWNLOADING -> MiuixTheme.colorScheme.primary
                            else -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (task.totalPages > 0) {
                        Text(
                            text = "${task.downloadedPages}/${task.totalPages}",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 动作按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Pause,
                                contentDescription = "暂停",
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                    DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.PENDING -> {
                        IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = "继续",
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "已完成",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp).padding(end = 4.dp)
                        )
                    }
                    else -> {}
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
