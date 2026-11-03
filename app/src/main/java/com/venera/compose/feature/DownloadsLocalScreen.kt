package com.venera.compose.feature

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import com.venera.compose.openGalleryPost
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.rememberContentWidth
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.data.platform.CacheDirs
import com.venera.compose.download.DownloadStatus
import com.venera.compose.download.DownloadTask
import com.venera.compose.download.LocalChapter
import com.venera.compose.download.LocalComic
import com.venera.compose.gallery.data.GalleryDownloadManager
import com.venera.compose.gallery.data.GalleryDownloadStatus
import com.venera.compose.gallery.data.GalleryDownloadTask
import com.venera.compose.reader.ComicPageSource
import com.venera.compose.reader.ReaderChapter
import com.venera.compose.reader.ReaderSession
import com.venera.compose.ui.tokens.LocalBottomBarClearance
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import coil3.compose.AsyncImage
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「下载与本地」（2026-11-04 定稿：底栏第五格，全项目唯一入口）。
 *
 * ## 信息架构（减法稿 + 两点修正）
 *
 * 汇总一行（任务数 · 速率 · 失败角标）下面三段胶囊 —— **队列 / 漫画 / 图片**，
 * 一屏只摆一个列表：
 *  - 队列：活动任务卡（右侧快捷 暂停/继续/重试，点卡身弹详情单）；
 *  - 漫画：按作品聚合的**紧凑行卡**（首版的大格网格被主人打回："卡片怎么那么大"）；
 *  - 图片：图廊下载记录（`GalleryDownloadManager` 的持久化任务表）。
 *
 * ## 老用户的数据一条不丢（主人 04 点名钉死的迁移线）
 *
 * 本页不新开任何存储 —— 三段读的就是旧三屏一直在用的三份持久化源：
 *  - 队列吃 `DownloadManager` 的任务表（`downloads_tasks.json`，暂停/失败/排队原样在）；
 *  - 漫画吃 `LocalComicManager` 对下载根目录的**磁盘扫描**（历史下载与导入的 CBZ
 *    都落在那棵树里，升级后第一次进页就扫得出来；导入的本子带「导入」角标）；
 *  - 图片吃 `GalleryDownloadManager` 的记录表（含本地路径与大小，逐条可见）。
 * 旧的"已完成"档位不再单列：完成即落盘，磁盘扫描就是它的真相（进度页只留活动任务）。
 *
 * ## 与旧三屏的关系
 *
 * 旧 `DownloadScreen`、`LocalComicScreen`、设置里的 `GalleryDownloadScreen` 三屏的
 * 功能全部并入本页；全项目入口只剩底栏这一格（主人拍板）。多选跟着段走：
 * 漫画段勾**磁盘文件**（危险确认），图片段勾**记录**（不碰相册本体）。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadsLocalScreen(
    onSelectComic: (ComicItem) -> Unit,
    onOpenLocalSession: (ReaderSession) -> Unit,
) {
    val context = LocalContext.current
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    val ports = remember(context) { BusinessPorts.of(context) }
    val downloadManager = remember(context) { ports.downloads }
    val localComicManager = remember(context) { ports.localComics }
    // 图廊记录与 `GalleryPostScreen` 那颗同源（直连点已在 B 台账登记）。
    val galleryManager = remember(context) { GalleryDownloadManager.getInstance(context) }

    val tasks by downloadManager.tasks.collectAsState()
    val galleryTasks by galleryManager.tasks.collectAsState()
    var comics by remember { mutableStateOf<List<LocalComic>>(emptyList()) }
    var isLoadingComics by remember { mutableStateOf(true) }
    // 漫画段的体积读数：rootPath 整棵树的大小，进页时后台算一轮（不逐卡实时算）。
    var bytesByPath by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    var segment by rememberSaveable { mutableStateOf(DownloadsLocalSegment.QUEUE) }
    var isMultiSelect by rememberSaveable { mutableStateOf(false) }
    val selectedComicPaths = remember { mutableStateMapOf<String, Boolean>() }
    val selectedGalleryIds = remember { mutableStateMapOf<String, Boolean>() }

    // 动作单目标（互斥）。
    var taskSheetFor by remember { mutableStateOf<DownloadTask?>(null) }
    var bookSheetFor by remember { mutableStateOf<LocalComic?>(null) }
    var bookChapters by remember { mutableStateOf<List<LocalChapter>>(emptyList()) }
    var gallerySheetFor by remember { mutableStateOf<GalleryDownloadTask?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }

    fun refreshComics() {
        scope.launch {
            isLoadingComics = true
            comics = localComicManager.getLocalComics()
            isLoadingComics = false
        }
    }
    LaunchedEffect(Unit) { refreshComics() }
    LaunchedEffect(comics) {
        if (comics.isEmpty()) return@LaunchedEffect
        bytesByPath = withContext(Dispatchers.IO) {
            comics.associate { it.rootPath to dirSize(File(it.rootPath)) }
        }
    }

    fun closeSheet() {
        taskSheetFor = null
        bookSheetFor = null
        gallerySheetFor = null
    }

    // CBZ 导入：选择器 → 暂存 cacheDir → manager 导入（原本地书架流程原样）。
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val tempFile = File(context.cacheDir, "import_temp.cbz")
                        tempFile.outputStream().use { output -> inputStream.copyTo(output) }
                        inputStream.close()
                        val result = localComicManager.importCbz(tempFile)
                        if (result.isSuccess) {
                            Toast.makeText(context, "成功导入: ${result.getOrNull()?.title}", Toast.LENGTH_SHORT).show()
                            refreshComics()
                            segment = DownloadsLocalSegment.COMIC
                        } else {
                            android.util.Log.w("VeneraDebug", "importCbz failed", result.exceptionOrNull())
                            Toast.makeText(context, "这个文件没能导入 🥲 确认是完整的 CBZ 压缩包再试", Toast.LENGTH_LONG).show()
                        }
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("VeneraDebug", "local comic import read failed", e)
                    Toast.makeText(context, "这个文件读不出来 🥲 换一个文件试试", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── 派生读数（两条下载通道合并计数）──
    val activeTasks = remember(tasks) {
        tasks.filter { it.status != DownloadStatus.COMPLETED && it.status != DownloadStatus.CANCELED }
    }
    val galleryRunning = remember(galleryTasks) { galleryTasks.count { it.status == GalleryDownloadStatus.RUNNING } }
    val galleryFailed = remember(galleryTasks) { galleryTasks.count { it.status == GalleryDownloadStatus.FAILED } }
    val comicFailed = remember(tasks) { tasks.count { it.status == DownloadStatus.FAILED } }
    val failedTotal = comicFailed + galleryFailed
    val speedText = remember(tasks) {
        tasks.firstOrNull { it.status == DownloadStatus.DOWNLOADING && it.speedText.isNotBlank() }?.speedText.orEmpty()
    }
    val activeTotal = activeTasks.size + galleryRunning
    // 漫画段按窗口宽收口（大屏不把行卡拉到全宽）。
    val (_, comicWidthModifier) = rememberContentWidth(VeneraTokens.spacing.rowHorizontal * 2)

    fun openGalleryImage(task: GalleryDownloadTask) {
        val path = task.localPath ?: return
        val file = File(path)
        if (!file.isFile) {
            Toast.makeText(context, "这个文件不在了 🥲 可能被相册那边删掉了", Toast.LENGTH_SHORT).show()
            return
        }
        val mime = when (file.extension.lowercase(Locale.US)) {
            "mp4", "webm", "mov", "mkv" -> "video/*"
            else -> "image/*"
        }
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }.onFailure {
            android.util.Log.w("VeneraDebug", "open gallery image failed", it)
            Toast.makeText(context, "没能打开看图应用 🥲 可以直接去相册找它", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        VeneraTopAppBar(
            title = "下载与本地",
            actions = {
                if (isMultiSelect && segment != DownloadsLocalSegment.QUEUE) {
                    val n = if (segment == DownloadsLocalSegment.COMIC) selectedComicPaths.size else selectedGalleryIds.size
                    if (n > 0) {
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Text("删除 $n", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = {
                        isMultiSelect = false
                        selectedComicPaths.clear()
                        selectedGalleryIds.clear()
                    }) { Text("取消") }
                } else if (segment != DownloadsLocalSegment.QUEUE) {
                    TextButton(onClick = { isMultiSelect = true }) { Text("选择") }
                }
            },
        )

        // 汇总一行：全局只讲这一句；失败是唯一可点的角标（跳队列段）。
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.rowHorizontal, vertical = tokens.spacing.space2)
                .clip(RoundedCornerShape(VeneraTokens.shape.medium))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(VeneraTokens.shape.small))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(tokens.spacing.space4))
            Text(
                text = when {
                    speedText.isNotBlank() -> "$speedText · $activeTotal 个任务进行中"
                    activeTotal == 0 -> "没有进行中的下载任务"
                    else -> "$activeTotal 个任务等待中"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (failedTotal > 0) {
                Text(
                    text = "$failedTotal 失败",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(RoundedCornerShape(VeneraTokens.shape.small))
                        .clickable { segment = DownloadsLocalSegment.QUEUE }
                        .padding(horizontal = tokens.spacing.space3, vertical = tokens.spacing.space1),
                )
            }
        }

        Spacer(Modifier.height(tokens.spacing.space3))
        DownloadsLocalSegmentBar(
            current = segment,
            queueCount = activeTotal,
            comicCount = comics.size,
            imageCount = galleryTasks.size,
            onSwitch = { segment = it },
            modifier = Modifier.padding(horizontal = tokens.spacing.rowHorizontal),
        )
        Spacer(Modifier.height(tokens.spacing.space3))

        AnimatedContent(
            targetState = segment,
            modifier = Modifier.weight(1f),
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "downloads_local_segment",
        ) { target ->
            when (target) {
                DownloadsLocalSegment.QUEUE -> QueuePane(
                    tasks = activeTasks,
                    onTogglePauseResume = { task ->
                        if (task.status == DownloadStatus.DOWNLOADING) downloadManager.pause(task.taskId)
                        else downloadManager.resume(task.taskId)
                    },
                    onTaskTap = { taskSheetFor = it },
                    onClearCompleted = { downloadManager.clearCompleted() },
                )
                DownloadsLocalSegment.COMIC -> ComicPane(
                    comics = comics,
                    isLoading = isLoadingComics,
                    bytesByPath = bytesByPath,
                    gridWidthModifier = comicWidthModifier,
                    multiSelect = isMultiSelect,
                    selectedKeys = selectedComicPaths,
                    bottomClearance = LocalBottomBarClearance.current,
                    onOpen = { comic ->
                        scope.launch {
                            val chapters = localComicManager.getLocalChapters(comic)
                            if (chapters.size == 1) {
                                openChapterSession(comic, chapters.first(), chapters, onOpenLocalSession)
                            } else {
                                bookSheetFor = comic
                                bookChapters = chapters
                            }
                        }
                    },
                    // 长按 = 管理单：源站详情 / 导出 CBZ / 删除整本（与点按多章弹出的同一颗）。
                    // 单章书也走这里，不能因为"点按直接开读"就找不到删除入口。
                    onLongPress = { comic ->
                        scope.launch {
                            bookChapters = localComicManager.getLocalChapters(comic)
                            bookSheetFor = comic
                        }
                    },
                    onToggleSelect = { comic ->
                        val key = comic.rootPath
                        if (key in selectedComicPaths) selectedComicPaths.remove(key) else selectedComicPaths[key] = true
                    },
                    onImport = {
                        importLauncher.launch(
                            arrayOf("application/zip", "application/vnd.comicbook+zip", "application/x-zip-compressed", "application/octet-stream"),
                        )
                    },
                )
                DownloadsLocalSegment.IMAGE -> ImagePane(
                    tasks = galleryTasks,
                    multiSelect = isMultiSelect,
                    selectedKeys = selectedGalleryIds,
                    bottomClearance = LocalBottomBarClearance.current,
                    onTap = { task ->
                        when {
                            isMultiSelect -> {
                                if (task.id in selectedGalleryIds) selectedGalleryIds.remove(task.id) else selectedGalleryIds[task.id] = true
                            }
                            task.status == GalleryDownloadStatus.COMPLETED -> openGalleryImage(task)
                            else -> gallerySheetFor = task
                        }
                    },
                    onLongPress = { task -> gallerySheetFor = task },
                    onClearFinished = { galleryManager.clearFinished() },
                )
            }
        }
    }

    // ── 批量删除确认（漫画=删文件，图片=删记录，两套语义分开说）──
    if (showDeleteConfirm) {
        val deleteComics = segment == DownloadsLocalSegment.COMIC
        val n = if (deleteComics) selectedComicPaths.size else selectedGalleryIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (deleteComics) "删除所选 $n 部漫画？" else "删除所选 $n 条记录？") },
            text = {
                Text(
                    if (deleteComics) "将从本地磁盘永久删除选中的 $n 部漫画及全部离线文件，删除后不可恢复。"
                    else "只清掉下载记录，相册里的图片本体不动。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    isMultiSelect = false
                    if (deleteComics) {
                        val targets = comics.filter { it.rootPath in selectedComicPaths }
                        scope.launch {
                            targets.forEach { localComicManager.deleteLocalComic(it) }
                            Toast.makeText(context, "已删除 ${targets.size} 部", Toast.LENGTH_SHORT).show()
                            selectedComicPaths.clear()
                            refreshComics()
                        }
                    } else {
                        galleryManager.deleteBatch(selectedGalleryIds.keys.toSet())
                        selectedGalleryIds.clear()
                        Toast.makeText(context, "已删除 $n 条记录", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
        )
    }

    // ── 任务详情单 ──
    taskSheetFor?.let { task ->
        ModalBottomSheet(onDismissRequest = ::closeSheet) {
            TaskActionsSheet(
                task = task,
                onPauseOrResume = {
                    if (task.status == DownloadStatus.DOWNLOADING) downloadManager.pause(task.taskId)
                    else downloadManager.resume(task.taskId)
                    closeSheet()
                },
                onCancel = {
                    downloadManager.delete(task.taskId)
                    closeSheet()
                },
            )
        }
    }

    // ── 书卡话级清单单 ──
    bookSheetFor?.let { comic ->
        ModalBottomSheet(onDismissRequest = ::closeSheet) {
            BookActionsSheet(
                comic = comic,
                chapters = bookChapters,
                bytesLabel = bytesByPath[comic.rootPath]?.let { formatBytesLabel(it) }.orEmpty(),
                canOpenDetail = !comic.isExternal && comic.sourceName.isNotBlank() && comic.sourceName != "local",
                onResume = { chapter ->
                    closeSheet()
                    openChapterSession(comic, chapter, bookChapters, onOpenLocalSession)
                },
                onDeleteChapter = { chapter ->
                    scope.launch {
                        val ok = localComicManager.deleteChapter(chapter)
                        Toast.makeText(context, if (ok) "这一话的离线文件已删除" else "没删掉 🥲 再试一次", Toast.LENGTH_SHORT).show()
                        if (ok) bookChapters = localComicManager.getLocalChapters(comic)
                    }
                },
                onExport = {
                    closeSheet()
                    scope.launch {
                        isExporting = true
                        exportProgress = 0f
                        val outDir = CacheDirs.inCache(context.cacheDir, CacheDirs.EXPORTS)
                        val targetFile = File(outDir, "${comic.title}.cbz")
                        val res = localComicManager.exportToCbz(comic, targetFile) { p -> exportProgress = p }
                        isExporting = false
                        if (res.isSuccess) {
                            Toast.makeText(context, "导出成功: ${targetFile.name}", Toast.LENGTH_SHORT).show()
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.comicbook+zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "分享/保存 CBZ 漫画"))
                            } catch (e: Exception) {
                                android.util.Log.w("VeneraDebug", "share local comic failed", e)
                                Toast.makeText(context, "分享没打开 🥲 可以改用「保存到相册」", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            android.util.Log.w("VeneraDebug", "export local comic failed", res.exceptionOrNull())
                            Toast.makeText(context, "导出没成功 🥲 看一眼存储空间，再试一次", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onDeleteComic = {
                    scope.launch {
                        localComicManager.deleteLocalComic(comic)
                        Toast.makeText(context, "已删除本地漫画", Toast.LENGTH_SHORT).show()
                        closeSheet()
                        refreshComics()
                    }
                },
                onOpenDetail = {
                    closeSheet()
                    onSelectComic(
                        ComicItem(
                            id = comic.id, title = comic.title, author = comic.author,
                            coverUrl = comic.coverPath, sourceName = comic.sourceName,
                        )
                    )
                },
            )
        }
    }

    // ── 图廊记录动作单 ──
    gallerySheetFor?.let { task ->
        ModalBottomSheet(onDismissRequest = ::closeSheet) {
            GalleryTaskActionsSheet(
                task = task,
                onOpen = {
                    closeSheet()
                    openGalleryImage(task)
                },
                onOpenSource = {
                    closeSheet()
                    // 进**软件内**的详情页：走 openGalleryPost 这颗唯一分流点，
                    // pixiv 自动进作品页（多页连读）、图库三站进大图页 —— 跳浏览器是外行做法。
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        activity.openGalleryPost(task.post.site, task.post.id)
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(task.post.pageUrl))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.onFailure {
                            Toast.makeText(context, "打不开源站页面 🥲", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onRetry = {
                    galleryManager.retry(task.id)
                    closeSheet()
                },
                onDelete = {
                    galleryManager.delete(task.id)
                    closeSheet()
                },
                onDeleteWithImage = {
                    closeSheet()
                    // 相册本体 + 记录一起删；文件删不掉（被系统相册占用等）也把记录清掉
                    val fileGone = task.localPath?.let { p -> runCatching { File(p).delete() }.getOrDefault(false) } ?: false
                    galleryManager.delete(task.id)
                    Toast.makeText(
                        context,
                        if (fileGone) "图片和记录都已删除" else "记录已删，图片本体没删掉 🥲",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    if (isExporting) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(tokens.spacing.space3))
                Text(
                    "正在打包导出 CBZ…" + if (exportProgress > 0f) " ${(exportProgress * 100).toInt()}%" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 本页的三段（形态与「搜索与发现」那枚胶囊同一把尺，三段版）。 */
private enum class DownloadsLocalSegment(val label: String) {
    QUEUE("队列"),
    COMIC("漫画"),
    IMAGE("图片"),
}

@Composable
private fun DownloadsLocalSegmentBar(
    current: DownloadsLocalSegment,
    queueCount: Int,
    comicCount: Int,
    imageCount: Int,
    onSwitch: (DownloadsLocalSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(VeneraTokens.shape.medium))
            .background(scheme.surfaceContainerHigh)
            .padding(3.dp),
    ) {
        val third = maxWidth / 3
        val slide by animateDpAsState(
            targetValue = when (current) {
                DownloadsLocalSegment.QUEUE -> 0.dp
                DownloadsLocalSegment.COMIC -> third
                DownloadsLocalSegment.IMAGE -> third * 2
            },
            animationSpec = tween(260, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
            label = "downloadsSegmentSlide",
        )
        Box(
            Modifier
                .width(third)
                .fillMaxHeight()
                .offset(x = slide)
                .clip(RoundedCornerShape(VeneraTokens.shape.small))
                .background(scheme.surfaceContainerHighest),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            DownloadsLocalSegment.entries.forEach { seg ->
                val selected = seg == current
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(VeneraTokens.shape.small))
                        .clickable { onSwitch(seg) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (seg) {
                            DownloadsLocalSegment.QUEUE -> "队列 ($queueCount)"
                            DownloadsLocalSegment.COMIC -> "漫画 ($comicCount)"
                            DownloadsLocalSegment.IMAGE -> "图片 ($imageCount)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 队列段：活动任务卡。右侧快捷钮直达暂停/继续/重试，点卡身弹详情单。 */
@Composable
private fun QueuePane(
    tasks: List<DownloadTask>,
    onTogglePauseResume: (DownloadTask) -> Unit,
    onTaskTap: (DownloadTask) -> Unit,
    onClearCompleted: () -> Unit,
) {
    if (tasks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            VeneraEmptyView(
                icon = Icons.Outlined.CloudDownload,
                title = "当前没有进行中的下载任务",
                message = "可在作品详情或阅读器中添加下载任务",
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = VeneraTokens.spacing.rowHorizontal,
            end = VeneraTokens.spacing.rowHorizontal,
            top = VeneraTokens.spacing.space2,
            bottom = VeneraTokens.spacing.space11,
        ),
        verticalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space3),
    ) {
        items(tasks, key = { it.taskId }) { task ->
            QueueTaskCard(
                task = task,
                onClick = { onTaskTap(task) },
                onTogglePauseResume = { onTogglePauseResume(task) },
            )
        }
        item(key = "hint") {
            Column(Modifier.padding(top = VeneraTokens.spacing.space2)) {
                Text(
                    "点任务卡看详情，右侧按钮直接暂停或重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "清空已完成记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onClearCompleted() }
                        .padding(vertical = VeneraTokens.spacing.space2),
                )
            }
        }
    }
}

@Composable
private fun QueueTaskCard(
    task: DownloadTask,
    onClick: () -> Unit,
    onTogglePauseResume: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (chipLabel, chipColor) = when (task.status) {
        DownloadStatus.DOWNLOADING -> "下载中" to scheme.primary
        DownloadStatus.PENDING -> "排队" to scheme.onSurfaceVariant
        DownloadStatus.PAUSED -> "已暂停" to scheme.onSurfaceVariant
        DownloadStatus.FAILED -> "失败" to scheme.error
        else -> task.status.name to scheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VeneraTokens.shape.card))
            .background(scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(VeneraTokens.spacing.space4),
        horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VeneraCover(
            url = task.comicCover,
            contentDescription = null,
            modifier = Modifier.width(54.dp).height(72.dp),
            preserveAspectRatio = false,
            shimmerWhileLoading = false,
        )
        Column(Modifier.weight(1f)) {
            Text(
                "${task.comicTitle} · ${task.chapterTitle}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(VeneraTokens.spacing.space2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chipLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = chipColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(VeneraTokens.shape.extraSmall))
                        .background(chipColor.copy(alpha = 0.13f))
                        .padding(horizontal = VeneraTokens.spacing.space2, vertical = 1.5.dp),
                )
                Spacer(Modifier.width(VeneraTokens.spacing.space3))
                // meta 各段按存在性拼装（此前 "N/M 页 · · 错误" 的双分隔符就是这么来的）。
                val meta = buildList {
                    if (task.totalPages > 0) add("${task.downloadedPages}/${task.totalPages} 页")
                    if (task.speedText.isNotBlank()) add(task.speedText)
                    task.errorMsg?.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(VeneraTokens.spacing.space2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val progress = if (task.totalPages > 0) {
                    (task.downloadedPages.toFloat() / task.totalPages).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(VeneraTokens.shape.extraSmall)),
                    color = if (task.status == DownloadStatus.FAILED) scheme.error else scheme.primary,
                    trackColor = scheme.surfaceContainerHighest,
                )
                if (task.totalPages > 0) {
                    Spacer(Modifier.width(VeneraTokens.spacing.space3))
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        // 快捷钮：下载中→暂停；排队/暂停→继续；失败→重试。子件 clickable 先于父卡消费点击。
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(chipColor.copy(alpha = 0.12f))
                .clickable(onClick = onTogglePauseResume),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (task.status) {
                    DownloadStatus.DOWNLOADING -> Icons.Filled.Pause
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = when (task.status) {
                    DownloadStatus.DOWNLOADING -> "暂停这一笔"
                    DownloadStatus.FAILED -> "重试这一笔"
                    else -> "继续这一笔"
                },
                tint = chipColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 任务详情单：进度与成因 + 暂停/继续/重试 + 撤掉。 */
@Composable
private fun TaskActionsSheet(task: DownloadTask, onPauseOrResume: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.padding(horizontal = VeneraTokens.spacing.rowHorizontal).padding(bottom = VeneraTokens.spacing.space8)) {
        Text(task.comicTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            buildList {
                add(task.chapterTitle)
                if (task.totalPages > 0) add("${task.downloadedPages}/${task.totalPages} 页")
                if (task.speedText.isNotBlank()) add(task.speedText)
            }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        task.errorMsg?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(VeneraTokens.spacing.space2))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(VeneraTokens.spacing.space3))
        SheetActionRow(
            icon = when (task.status) {
                DownloadStatus.DOWNLOADING -> Icons.Filled.Pause
                else -> Icons.Filled.PlayArrow
            },
            label = when (task.status) {
                DownloadStatus.DOWNLOADING -> "暂停这一笔"
                DownloadStatus.FAILED -> "重试这一笔"
                else -> "继续这一笔"
            },
            onClick = onPauseOrResume,
        )
        SheetActionRow(icon = Icons.Outlined.Download, label = "撤掉这一笔", onClick = onCancel)
    }
}

/** 书卡话级清单单：行点击续读该话，行内删除；整本动作收在底部。 */
@Composable
private fun BookActionsSheet(
    comic: LocalComic,
    chapters: List<LocalChapter>,
    bytesLabel: String,
    canOpenDetail: Boolean,
    onResume: (LocalChapter) -> Unit,
    onDeleteChapter: (LocalChapter) -> Unit,
    onExport: () -> Unit,
    onDeleteComic: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    Column(Modifier.padding(horizontal = VeneraTokens.spacing.rowHorizontal).padding(bottom = VeneraTokens.spacing.space8)) {
        Text(comic.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(
                "${comic.chapterCount} 话",
                bytesLabel.takeIf { it.isNotBlank() },
                if (comic.isExternal) "导入" else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(VeneraTokens.spacing.space3))
        LazyColumn(modifier = Modifier.height(200.dp)) {
            items(chapters, key = { it.chapterId }) { ch ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onResume(ch) }
                        .padding(vertical = VeneraTokens.spacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        ch.title.ifBlank { "第 ${ch.order} 话" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${ch.pageCount} 页",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(VeneraTokens.spacing.space3))
                    TextButton(onClick = { onDeleteChapter(ch) }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Spacer(Modifier.height(VeneraTokens.spacing.space3))
        Row(horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space2)) {
            if (canOpenDetail) TextButton(onClick = onOpenDetail) { Text("查看源站详情 ›") }
            TextButton(onClick = onExport) { Text("导出 CBZ") }
        }
        Spacer(Modifier.height(VeneraTokens.spacing.space2))
        TextButton(onClick = onDeleteComic) {
            Text("删除整本漫画", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SheetActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = VeneraTokens.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space4),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 漫画段：按作品聚合的紧凑行卡列表（首版大格网格被主人打回，行数密度优先）。 */
@Composable
private fun ComicPane(
    comics: List<LocalComic>,
    isLoading: Boolean,
    bytesByPath: Map<String, Long>,
    gridWidthModifier: Modifier,
    multiSelect: Boolean,
    selectedKeys: Map<String, Boolean>,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onOpen: (LocalComic) -> Unit,
    onLongPress: (LocalComic) -> Unit,
    onToggleSelect: (LocalComic) -> Unit,
    onImport: () -> Unit,
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (comics.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(horizontal = VeneraTokens.spacing.rowHorizontal)) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                VeneraEmptyView(
                    icon = Icons.Outlined.MenuBook,
                    title = "暂无本地离线漫画",
                    message = "可以在漫画详情页下载章节，或导入外部 CBZ 归档",
                )
            }
            ImportButton(onImport)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().then(gridWidthModifier),
        contentPadding = PaddingValues(
            start = VeneraTokens.spacing.rowHorizontal,
            end = VeneraTokens.spacing.rowHorizontal,
            top = VeneraTokens.spacing.space2,
            bottom = bottomClearance + VeneraTokens.spacing.space8,
        ),
        verticalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space3),
    ) {
        items(comics, key = { it.rootPath }) { comic ->
            ComicRowCard(
                comic = comic,
                bytesLabel = bytesByPath[comic.rootPath]?.let { formatBytesLabel(it) }.orEmpty(),
                multiSelect = multiSelect,
                selected = comic.rootPath in selectedKeys,
                onClick = { if (multiSelect) onToggleSelect(comic) else onOpen(comic) },
                onLongClick = { if (!multiSelect) onLongPress(comic) },
            )
        }
        item(key = "import") {
            ImportButton(onImport)
        }
    }
}

@Composable
private fun ComicRowCard(
    comic: LocalComic,
    bytesLabel: String,
    multiSelect: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VeneraTokens.shape.card))
            .background(scheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(VeneraTokens.spacing.space4),
        horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VeneraCover(
            url = comic.coverPath,
            contentDescription = comic.title,
            modifier = Modifier.width(54.dp).height(72.dp),
            preserveAspectRatio = false,
            shimmerWhileLoading = false,
        )
        Column(Modifier.weight(1f)) {
            Text(
                comic.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(VeneraTokens.spacing.space1))
            val meta = buildList {
                add("${comic.chapterCount} 话")
                if (bytesLabel.isNotBlank()) add(bytesLabel)
                if (comic.sourceName.isNotBlank()) add(comic.sourceName)
            }.joinToString(" · ")
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (comic.isExternal) {
                Spacer(Modifier.height(VeneraTokens.spacing.space1))
                Text(
                    "导入",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StatusColors.Healthy,
                    modifier = Modifier
                        .clip(RoundedCornerShape(VeneraTokens.shape.extraSmall))
                        .background(StatusColors.Healthy.copy(alpha = 0.13f))
                        .padding(horizontal = VeneraTokens.spacing.space2, vertical = 1.5.dp),
                )
            }
        }
        if (multiSelect) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) scheme.primary else Color.Transparent)
                    .border(2.dp, if (selected) scheme.primary else scheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = "已选中", tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        } else {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ImportButton(onImport: () -> Unit) {
    TextButton(
        onClick = onImport,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = VeneraTokens.spacing.space4)
            .height(44.dp),
    ) {
        Text("导入本地漫画（.zip / .cbz）", style = MaterialTheme.typography.bodyMedium)
    }
}

/** 图片段：图廊下载记录（`GalleryDownloadManager` 的持久化任务表，老记录升级后原样在）。 */
@Composable
private fun ImagePane(
    tasks: List<GalleryDownloadTask>,
    multiSelect: Boolean,
    selectedKeys: Map<String, Boolean>,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onTap: (GalleryDownloadTask) -> Unit,
    onLongPress: (GalleryDownloadTask) -> Unit,
    onClearFinished: () -> Unit,
) {
    if (tasks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            VeneraEmptyView(
                icon = Icons.Outlined.Image,
                title = "暂无图廊下载记录",
                message = "在图廊大图页点下载，图片会先进系统相册，这里留着它的记录",
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = VeneraTokens.spacing.rowHorizontal,
            end = VeneraTokens.spacing.rowHorizontal,
            top = VeneraTokens.spacing.space2,
            bottom = bottomClearance + VeneraTokens.spacing.space8,
        ),
        horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space3),
        verticalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space3),
    ) {
        items(tasks, key = { it.id }) { task ->
            val scheme = MaterialTheme.colorScheme
            val model: Any? = task.localPath?.let { if (File(it).isFile) File(it) else null } ?: task.thumbnailUrl
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(VeneraTokens.shape.medium))
                    .combinedClickable(
                        onClick = { onTap(task) },
                        onLongClick = { onLongPress(task) },
                    ),
            ) {
                AsyncImage(
                    model = model,
                    contentDescription = task.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(scheme.surfaceContainerHigh),
                )
                when (task.status) {
                    GalleryDownloadStatus.RUNNING -> Box(
                        Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.42f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = scheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                    GalleryDownloadStatus.FAILED -> Box(
                        Modifier.fillMaxSize().background(scheme.error.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "下载失败，点按重试", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    GalleryDownloadStatus.COMPLETED -> {
                        // 底部渐隐让白底图也不糊边（与图廊墙那面同一手法）。
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(22.dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, scheme.scrim.copy(alpha = 0.25f)))),
                        )
                    }
                }
                if (multiSelect) {
                    val selected = task.id in selectedKeys
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(VeneraTokens.spacing.space2)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (selected) scheme.primary else Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = "已选中", tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }
        item(key = "clear", span = { GridItemSpan(3) }) {
            Text(
                "清空已完成与失败的记录",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onClearFinished() }
                    .padding(vertical = VeneraTokens.spacing.space2),
            )
        }
    }
}

/** 图廊记录动作单：打开 / 源站详情 / 重试 / 删图片和记录 / 只删记录（相册本体不动）。 */
@Composable
private fun GalleryTaskActionsSheet(
    task: GalleryDownloadTask,
    onOpen: () -> Unit,
    onOpenSource: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onDeleteWithImage: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.padding(horizontal = VeneraTokens.spacing.rowHorizontal).padding(bottom = VeneraTokens.spacing.space8)) {
        Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            buildList {
                add(task.subtitle)
                add(task.site.displayName)
                when (task.status) {
                    GalleryDownloadStatus.COMPLETED -> task.fileSize.takeIf { it > 0 }?.let { add(formatBytesLabel(it)) }
                    GalleryDownloadStatus.RUNNING -> add("下载中")
                    GalleryDownloadStatus.FAILED -> add("失败")
                }
            }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        task.errorMessage?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(VeneraTokens.spacing.space2))
            Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error)
        }
        // 权限导致的失败要给得出路：一条提示 + 直达系统授权页，别让人对着裸报错猜。
        // 判据吃 GallerySaver.saveToFile 里那条可读报错的关键词，别用 EPERM 原文。
        if (task.status == GalleryDownloadStatus.FAILED &&
            task.errorMessage?.contains("所有文件访问") == true
        ) {
            Spacer(Modifier.height(VeneraTokens.spacing.space2))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = scheme.errorContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = VeneraTokens.spacing.space3, vertical = VeneraTokens.spacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "缺少「所有文件访问」权限，图片写不进所选目录",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "去授权",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                        modifier = Modifier
                            .clickable { com.venera.compose.feature.settings.openAllFilesSettings(context) }
                            .padding(VeneraTokens.spacing.space2),
                    )
                }
            }
        }
        Spacer(Modifier.height(VeneraTokens.spacing.space3))
        if (task.status == GalleryDownloadStatus.COMPLETED && task.localPath != null) {
            SheetActionRow(icon = Icons.Outlined.Image, label = "打开图片", onClick = onOpen)
        }
        // 源站详情：跳浏览器开这张图的帖子页（gelbooru/yandere 等的 post 页）
        SheetActionRow(icon = Icons.AutoMirrored.Outlined.OpenInNew, label = "源站详情", onClick = onOpenSource)
        if (task.status == GalleryDownloadStatus.FAILED) {
            SheetActionRow(icon = Icons.Outlined.Refresh, label = "重新下载", onClick = onRetry)
        }
        if (task.status == GalleryDownloadStatus.COMPLETED && task.localPath != null) {
            // 相册里的图片本体 + 记录一起删，与下面"只删记录"分开说清
            SheetActionRow(icon = Icons.Outlined.Delete, label = "删图片和记录", onClick = onDeleteWithImage)
        }
        SheetActionRow(icon = Icons.Outlined.Download, label = "只删这条记录", onClick = onDelete)
    }
}

/** 离线阅读会话构造：原本地书架的判据原样（本机页直读，不起引擎）。 */
private fun openChapterSession(
    comic: LocalComic,
    currentChapter: LocalChapter,
    allChapters: List<LocalChapter>,
    onOpenLocalSession: (ReaderSession) -> Unit,
) {
    val readerChapters = allChapters.map { ch ->
        val mappedPages = ch.pageFiles.mapIndexed { idx, path ->
            ComicPageSource.LocalFile(File(path), idx)
        }
        ReaderChapter(
            id = ch.chapterId,
            title = ch.title,
            pages = mappedPages,
            isLoaded = true,
        )
    }
    val curIdx = allChapters.indexOf(currentChapter).coerceAtLeast(0)
    val session = ReaderSession(
        comicId = comic.id,
        comicTitle = comic.title,
        coverUrl = comic.coverPath,
        sourceName = comic.sourceName.ifBlank { "本地" },
        sourceKey = "local",
        chapters = readerChapters,
        initialChapterIndex = curIdx,
        initialPageIndex = 0,
    )
    onOpenLocalSession(session)
}

/** 体积读数（漫画段进页后台算一轮）：目录树 walk，档位与桌面 `desktopByteLabel` 同口径。 */
internal fun dirSize(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf { dirSize(it) } ?: 0L
}

internal fun formatBytesLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GiB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.0f MiB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(Locale.US, "%.0f KiB", bytes / 1024.0)
    else -> "$bytes B"
}
