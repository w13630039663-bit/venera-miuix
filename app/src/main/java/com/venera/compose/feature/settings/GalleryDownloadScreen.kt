package com.venera.compose.feature.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.venera.compose.components.venera.VeneraGallerySourceMark
import com.venera.compose.components.venera.VeneraIconButton
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.gallery.data.GalleryDownloadManager
import com.venera.compose.gallery.data.GalleryDownloadStatus
import com.venera.compose.gallery.data.GalleryDownloadTask
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import java.io.File

private enum class DownloadTab(val label: String) {
    ALL("全部"),
    RUNNING("运行中"),
    COMPLETED("完成"),
    FAILED("失败"),
}

/**
 * 画廊统一下载任务管理器（2026-10-22）。
 *
 * 参照图 7 交互设计，支持全站多源任务查看、状态筛选、一键打开文件、重试、删除与长按多选批量删除。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryDownloadScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val tokens = VeneraTokens
    val manager = remember(context) { GalleryDownloadManager.getInstance(context) }
    val allTasks by manager.tasks.collectAsState()

    var selectedTab by remember { mutableStateOf(DownloadTab.ALL) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    val filteredTasks = remember(allTasks, selectedTab) {
        when (selectedTab) {
            DownloadTab.ALL -> allTasks
            DownloadTab.RUNNING -> allTasks.filter { it.status == GalleryDownloadStatus.RUNNING }
            DownloadTab.COMPLETED -> allTasks.filter { it.status == GalleryDownloadStatus.COMPLETED }
            DownloadTab.FAILED -> allTasks.filter { it.status == GalleryDownloadStatus.FAILED }
        }
    }

    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()

    Scaffold(
        topBar = {
            VeneraTopAppBar(
                title = if (isSelectionMode) "已选 ${selectedIds.size} 项" else "下载任务",
                largeTitle = if (isSelectionMode) "已选 ${selectedIds.size} 项" else "下载任务",
                scrollBehavior = topBarBehavior,
                backdrop = topBarBackdrop,
                navigationIcon = {
                    VeneraIconButton(
                        onClick = {
                            if (isSelectionMode) {
                                isSelectionMode = false
                                selectedIds = emptySet()
                            } else {
                                onBack()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = if (isSelectionMode) "取消选择" else "返回",
                            tint = tokens.color.textPrimary,
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        VeneraIconButton(
                            onClick = {
                                selectedIds = if (selectedIds.size == filteredTasks.size) {
                                    emptySet()
                                } else {
                                    filteredTasks.map { it.id }.toSet()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SelectAll,
                                contentDescription = "全选",
                                tint = tokens.color.textPrimary,
                            )
                        }
                        VeneraIconButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    manager.deleteBatch(selectedIds)
                                    Toast.makeText(context, "已删除 ${selectedIds.size} 个任务", Toast.LENGTH_SHORT).show()
                                    isSelectionMode = false
                                    selectedIds = emptySet()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "删除选中",
                                tint = if (selectedIds.isNotEmpty()) StatusColors.Failing else tokens.color.textTertiary,
                            )
                        }
                    } else if (allTasks.isNotEmpty()) {
                        VeneraIconButton(
                            onClick = {
                                isSelectionMode = true
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "批量管理",
                                tint = tokens.color.textSecondary,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(topBarBehavior.nestedScrollConnection),
        ) {
            // 分段选择器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space2),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            ) {
                DownloadTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val count = when (tab) {
                        DownloadTab.ALL -> allTasks.size
                        DownloadTab.RUNNING -> allTasks.count { it.status == GalleryDownloadStatus.RUNNING }
                        DownloadTab.COMPLETED -> allTasks.count { it.status == GalleryDownloadStatus.COMPLETED }
                        DownloadTab.FAILED -> allTasks.count { it.status == GalleryDownloadStatus.FAILED }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(tokens.spacing.space9)
                            .clip(RoundedCornerShape(tokens.spacing.space11))
                            .background(
                                if (isSelected) tokens.color.primary else tokens.color.surfaceContainerHigh
                            )
                            .clickable { selectedTab = tab },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (count > 0) "${tab.label} $count" else tab.label,
                            fontSize = tokens.type.caption,
                            fontWeight = if (isSelected) tokens.type.weightSemibold else tokens.type.weightRegular,
                            color = if (isSelected) tokens.color.onPrimary else tokens.color.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(tokens.spacing.space8),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无相关下载任务",
                        fontSize = tokens.type.body,
                        color = tokens.color.textTertiary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .blurBackdropSource(topBarBackdrop),
                    contentPadding = PaddingValues(
                        start = tokens.spacing.screenHorizontal,
                        end = tokens.spacing.screenHorizontal,
                        top = tokens.spacing.space2,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + tokens.spacing.space4,
                    ),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        val isSelected = task.id in selectedIds
                        DownloadTaskItem(
                            task = task,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                selectedIds = if (isSelected) selectedIds - task.id else selectedIds + task.id
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedIds = setOf(task.id)
                                }
                            },
                            onRetry = { manager.retry(task.id) },
                            onOpen = { openFile(context, task.localPath) },
                            onDelete = {
                                manager.delete(task.id)
                                Toast.makeText(context, "已删除下载记录", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadTaskItem(
    task: GalleryDownloadTask,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = VeneraTokens

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.large))
            .background(tokens.color.surfaceVariant)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelect()
                    } else if (task.status == GalleryDownloadStatus.COMPLETED) {
                        onOpen()
                    }
                },
                onLongClick = onLongClick,
            )
            .padding(tokens.spacing.space3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        ) {
            // 选择框
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) tokens.color.primary else tokens.color.textTertiary,
                    modifier = Modifier.size(tokens.spacing.space6),
                )
            }

            // 缩略图与源站徽标
            Box(
                modifier = Modifier
                    .size(tokens.spacing.quoteAvatarSize)
                    .clip(RoundedCornerShape(tokens.shape.medium))
                    .background(tokens.color.surfaceContainerHigh),
            ) {
                AsyncImage(
                    model = task.thumbnailUrl,
                    contentDescription = task.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // 站方标识角标
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(tokens.spacing.space1),
                ) {
                    VeneraGallerySourceMark(
                        site = task.site,
                        size = tokens.spacing.platformBadgeSize,
                    )
                }
            }

            // 标题与副标题信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
            ) {
                Text(
                    text = task.title,
                    fontSize = tokens.type.itemTitle,
                    fontWeight = tokens.type.weightMedium,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = task.subtitle,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // 状态指示行
                when (task.status) {
                    GalleryDownloadStatus.RUNNING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(tokens.spacing.space3),
                                strokeWidth = tokens.spacing.hairline,
                                color = tokens.color.primary,
                            )
                            Text(
                                text = "下载中...",
                                fontSize = tokens.type.caption,
                                color = tokens.color.primary,
                            )
                        }
                    }
                    GalleryDownloadStatus.COMPLETED -> {
                        Text(
                            text = if (task.fileSize > 0) "已完成 · " + "%.1f".format(task.fileSize / 1024.0 / 1024.0) + " MB" else "已完成",
                            fontSize = tokens.type.caption,
                            color = StatusColors.Healthy,
                        )
                    }
                    GalleryDownloadStatus.FAILED -> {
                        Text(
                            text = "下载失败: " + (task.errorMessage ?: "网络异常"),
                            fontSize = tokens.type.caption,
                            color = StatusColors.Failing,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 右侧动作按钮
            if (!isSelectionMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
                ) {
                    if (task.status == GalleryDownloadStatus.FAILED) {
                        VeneraIconButton(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "重试",
                                tint = tokens.color.primary,
                            )
                        }
                    }

                    if (task.status == GalleryDownloadStatus.COMPLETED) {
                        VeneraIconButton(onClick = onOpen) {
                            Icon(
                                imageVector = Icons.Outlined.Visibility,
                                contentDescription = "打开",
                                tint = tokens.color.textSecondary,
                            )
                        }
                        VeneraIconButton(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "重新下载",
                                tint = tokens.color.textTertiary,
                            )
                        }
                    }

                    VeneraIconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "删除记录",
                            tint = tokens.color.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

private fun openFile(context: Context, path: String?) {
    if (path.isNullOrBlank()) {
        Toast.makeText(context, "文件路径无效", Toast.LENGTH_SHORT).show()
        return
    }
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "文件不存在或已被移除", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val mime = if (path.endsWith(".mp4", ignoreCase = true) || path.endsWith(".webm", ignoreCase = true)) {
            "video/*"
        } else {
            "image/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "打不开文件: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}