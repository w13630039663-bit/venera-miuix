package com.venera.compose.feature

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.grid.items
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
import com.venera.compose.components.*
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.components.venera.blurBackdropSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.venera.compose.download.DownloadManager
import com.venera.compose.download.DownloadStatus
import com.venera.compose.download.DownloadTask
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
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
    val tokens = VeneraTokens
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val tasks by downloadManager.tasks.collectAsState()
    val displayMode = rememberComicListDisplayMode()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: 下载中/排队, 1: 已完成

    val activeTasks = remember(tasks) {
        tasks.filter { it.status != DownloadStatus.COMPLETED }.sortedByDescending { it.createTime }
    }
    val completedTasks = remember(tasks) {
        tasks.filter { it.status == DownloadStatus.COMPLETED }.sortedByDescending { it.updateTime }
    }

    // 统一顶栏：大标题折叠 + 毛玻璃（二级子页面同款）
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = statusBarTop + 104.dp + 92.dp
    val currentList = if (selectedTab == 0) activeTasks else completedTasks

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding, bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                if (selectedTab == 0) {
                    VeneraEmptyView(
                        icon = Icons.Outlined.CloudDownload,
                        message = "下载队列为空",
                        title = "暂无下载任务",
                    )
                } else {
                    VeneraEmptyView(
                        icon = Icons.Outlined.DoneAll,
                        message = "暂无已完成的离线章节",
                        title = "还没有完成的下载",
                    )
                }
            }
        } else {
            val (gridWidth, gridWidthModifier) = rememberContentWidth(14.dp * 2)
            LazyVerticalGrid(
                columns = GridCells.Fixed(comicListColumnCount(displayMode.value, gridWidth)),
                modifier = Modifier
                    .fillMaxSize()
                    .then(gridWidthModifier)
                    .nestedScroll(topBarBehavior.nestedScrollConnection)
                    .blurBackdropSource(topBarBackdrop),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = topPadding,
                    bottom = VeneraSpacing.bottomBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(currentList, key = { it.taskId }) { task ->
                    DownloadTaskCard(
                        task = task,
                        detailed = displayMode.value == "detailed",
                        onPause = { downloadManager.pause(task.taskId) },
                        onResume = { downloadManager.resume(task.taskId) },
                        onDelete = { downloadManager.delete(task.taskId, deleteFiles = true) }
                    )
                }
            }
        }

        com.venera.compose.components.venera.VeneraTopAppBar(
            title = "离线下载",
            largeTitle = "离线下载管理",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary
                    )
                }
            },
            actions = {
                TextButton(onClick = onNavigateToLocalLibrary) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "本地书架",
                        tint = tokens.color.primary,
                        modifier = Modifier.size(tokens.spacing.chipIconSize + 2.dp)
                    )
                    Spacer(modifier = Modifier.width(tokens.spacing.space1))
                    Text(
                        text = "本地书架",
                        fontSize = tokens.type.sectionTitle,
                        color = tokens.color.primary
                    )
                }
            },
            bottomContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Tab 切换条：胶囊分段规范（选中 primary 实底 + onPrimary，未选 surfaceVariant）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space3),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space5)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selectedTab == 0) tokens.color.primary
                                    else tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = 0 }
                        ) {
                            Box(modifier = Modifier.padding(vertical = tokens.spacing.space5), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "下载队列 (${activeTasks.size})",
                                    fontSize = tokens.type.sectionTitle,
                                    fontWeight = if (selectedTab == 0) tokens.type.weightBold else tokens.type.weightRegular,
                                    color = if (selectedTab == 0) tokens.color.onPrimary else tokens.color.textSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selectedTab == 1) tokens.color.primary
                                    else tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = 1 }
                        ) {
                            Box(modifier = Modifier.padding(vertical = tokens.spacing.space5), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "已完成 (${completedTasks.size})",
                                    fontSize = tokens.type.sectionTitle,
                                    fontWeight = if (selectedTab == 1) tokens.type.weightBold else tokens.type.weightRegular,
                                    color = if (selectedTab == 1) tokens.color.onPrimary else tokens.color.textSecondary
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
                        ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
                        Spacer(modifier = Modifier.weight(1f))
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
                }
            }
        )
    }
}

@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    detailed: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = VeneraTokens
    VeneraCard(modifier = Modifier.fillMaxWidth()) {
        ComicCardLayout(
            detailed = detailed,
            modifier = Modifier.padding(tokens.spacing.cardContentPadding),
            cover = {
                VeneraCover(
                    url = task.comicCover,
                    contentDescription = task.comicTitle,
                    shimmerWhileLoading = false,
                    modifier = Modifier.height(if (detailed) 180.dp else 200.dp)
                )
            },
            content = {
                Column(modifier = Modifier.padding(top = if (detailed) 0.dp else 8.dp)) {
                    Text(
                        text = task.comicTitle,
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightBold,
                        color = tokens.color.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space1))
                    Text(
                        text = task.chapterTitle,
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
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
                            DownloadStatus.COMPLETED -> StatusColors.Healthy
                            DownloadStatus.FAILED -> StatusColors.Failing
                            DownloadStatus.PAUSED, DownloadStatus.PENDING -> StatusColors.Degraded
                            else -> tokens.color.primary
                        },
                        trackColor = tokens.color.surfaceVariant.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
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
                            fontSize = tokens.type.overline,
                            color = when (task.status) {
                                DownloadStatus.COMPLETED -> StatusColors.Healthy
                                DownloadStatus.FAILED -> StatusColors.Failing
                                DownloadStatus.DOWNLOADING -> tokens.color.primary
                                else -> tokens.color.textSecondary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (task.totalPages > 0) {
                            Text(
                                text = "${task.downloadedPages}/${task.totalPages}",
                                fontSize = tokens.type.badge,
                                color = tokens.color.textTertiary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // 动作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (task.status) {
                            DownloadStatus.DOWNLOADING -> {
                                IconButton(onClick = onPause, modifier = Modifier.size(40.dp)) {
                                    Icon(
                                        imageVector = Icons.Outlined.Pause,
                                        contentDescription = "暂停",
                                        tint = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }
                            DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.PENDING -> {
                                IconButton(onClick = onResume, modifier = Modifier.size(40.dp)) {
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
                                    tint = StatusColors.Healthy,
                                    modifier = Modifier.size(24.dp).padding(end = 4.dp)
                                )
                            }
                            else -> {}
                        }

                        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        )
    }
}
