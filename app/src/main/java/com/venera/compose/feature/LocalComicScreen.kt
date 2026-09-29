package com.venera.compose.feature

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.content.FileProvider
import com.venera.compose.components.*
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.download.DownloadManager
import com.venera.compose.download.DownloadStatus
import com.venera.compose.download.DownloadTask
import com.venera.compose.download.LocalChapter
import com.venera.compose.download.LocalComic
import com.venera.compose.download.LocalComicManager
import com.venera.compose.reader.ComicPageSource
import com.venera.compose.reader.ReaderChapter
import com.venera.compose.reader.ReaderSession
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/**
 * 生产级本地离线书架 (S6)
 *
 * 核心特性：
 * 1. 离线阅读已下载或导入的本地漫画
 * 2. 支持导入外部 CBZ / ZIP 漫画归档
 * 3. 支持将离线漫画一键导出为标准 CBZ 归档并调用系统分享
 * 4. 离线断网秒开阅读
 */
@Composable
fun LocalComicScreen(
    onBack: () -> Unit,
    onOpenLocalSession: (ReaderSession) -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onOpenComicDetail: (ComicItem) -> Unit = {}
) {
    val context = LocalContext.current
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    val displayMode = rememberComicListDisplayMode()
    val localComicManager = remember { LocalComicManager.getInstance(context) }

    var comics by remember { mutableStateOf<List<LocalComic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedComicForChapters by remember { mutableStateOf<LocalComic?>(null) }
    var comicChapters by remember { mutableStateOf<List<LocalChapter>>(emptyList()) }

    // ── 下载中心联动：观察任务流 + 源分类过滤 ──
    val downloadManager = remember { DownloadManager.getInstance(context) }
    val downloadTasks by downloadManager.tasks.collectAsState()
    val activeDownloadTasks = remember(downloadTasks) {
        downloadTasks.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }
    }
    val failedDownloadTasks = remember(downloadTasks) {
        downloadTasks.filter { it.status == DownloadStatus.FAILED }
    }
    // ── 多选批量删除状态 ──
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedComicPaths by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // 源分类过滤："全部" + 书架实际包含的源标签
    var selectedCategory by remember { mutableStateOf("全部") }
    val categories = remember(comics) {
        (listOf("全部") + comics.map { it.sourceName.ifBlank { "本地导入" } }.distinct()).sortedBy { if (it == "全部") "" else it }
    }
    val filteredComics = remember(comics, selectedCategory) {
        if (selectedCategory == "全部") comics
        else comics.filter { it.sourceName.ifBlank { "本地导入" } == selectedCategory }
    }

    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }

    fun refresh() {
        scope.launch {
            isLoading = true
            comics = localComicManager.getLocalComics()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    // CBZ 文件导入选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val tempFile = File(context.cacheDir, "import_temp.cbz")
                        tempFile.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                        inputStream.close()

                        val result = localComicManager.importCbz(tempFile)
                        if (result.isSuccess) {
                            Toast.makeText(context, "成功导入: ${result.getOrNull()?.title}", Toast.LENGTH_SHORT).show()
                            refresh()
                        } else {
                            Toast.makeText(context, "导入失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "读取文件异常: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 统一顶栏：大标题折叠 + 毛玻璃；多选态由 actions 平滑接管
    val topBarBehavior = com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = statusBarTop + 104.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MiuixTheme.colorScheme.primary)
                }
            }

            comics.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = statusBarTop + 104.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VeneraEmptyView(
                        icon = Icons.Outlined.MenuBook,
                        title = "暂无本地离线漫画",
                        message = "可以在漫画详情页下载章节，或点击右上角导入外部 CBZ 归档",
                    )
                }
            }

            else -> {
                val (gridWidth, gridWidthModifier) =
                    rememberContentWidth(tokens.spacing.rowHorizontal * 2)
                // 列数按实测宽推（宽屏加列）；chunked 要的是列数，不是网格自己
                val columns = comicListColumnCount(displayMode.value, gridWidth)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(gridWidthModifier)
                        .nestedScroll(topBarBehavior.nestedScrollConnection)
                        .blurBackdropSource(topBarBackdrop),
                    contentPadding = PaddingValues(
                        start = tokens.spacing.rowHorizontal,
                        end = tokens.spacing.rowHorizontal,
                        top = statusBarTop + 104.dp,
                        bottom = VeneraSpacing.bottomBarClearance,
                    ),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                ) {
                        // ── 正在下载任务横幅（点击直达下载中心）──
                        if (activeDownloadTasks.isNotEmpty()) {
                            item(key = "downloading-banner") {
                                DownloadingBanner(
                                    tasks = activeDownloadTasks,
                                    onClick = onNavigateToDownloads,
                                )
                            }
                        }
                        // ── 失败任务警告横幅（红色警告 + 如实错误详情 + 一键重试）──
                        if (failedDownloadTasks.isNotEmpty()) {
                            item(key = "failed-banner") {
                                FailedTasksBanner(
                                    tasks = failedDownloadTasks,
                                    onRetry = { task -> downloadManager.resume(task.taskId) },
                                    onClick = onNavigateToDownloads,
                                )
                            }
                        }
                        // ── 源分类过滤胶囊条 ──
                        if (categories.size > 1) {
                            item(key = "category-bar") {
                                CategoryFilterBar(
                                    categories = categories,
                                    selected = selectedCategory,
                                    onSelect = { selectedCategory = it },
                                )
                            }
                        }
                        // ── 书架网格（按源过滤后的列表，行级 chunked 保持双列语义）──
                        filteredComics.chunked(columns).forEachIndexed { rowIdx, row ->
                            item(key = "comic-row-" + rowIdx + "-" + (row.firstOrNull()?.rootPath ?: "")) {
                                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap)) {
                                    row.forEach { comic ->
                                        Box(Modifier.weight(1f)) {
                                            LocalComicCard(
                                comic = comic,
                                detailed = displayMode.value == "detailed",
                                isSelected = isSelectionMode && comic.rootPath in selectedComicPaths,
                                onClick = {
                                    if (isSelectionMode) {
                                        // 多选态：点击切换选中，不触发阅读
                                        selectedComicPaths = if (comic.rootPath in selectedComicPaths) {
                                            selectedComicPaths - comic.rootPath
                                        } else {
                                            selectedComicPaths + comic.rootPath
                                        }
                                    } else {
                                        scope.launch {
                                            val chapters = localComicManager.getLocalChapters(comic)
                                            if (chapters.size == 1) {
                                                // 单章节直接秒开
                                                openChapterSession(comic, chapters.first(), chapters, onOpenLocalSession)
                                            } else {
                                                // 多章节弹窗选章
                                                selectedComicForChapters = comic
                                                comicChapters = chapters
                                            }
                                        }
                                    }
                                },
                                onExportCbz = {
                                    scope.launch {
                                        isExporting = true
                                        exportProgress = 0f
                                        val outDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
                                        val targetFile = File(outDir, "${comic.title}.cbz")
                                        val res = localComicManager.exportToCbz(comic, targetFile) { p ->
                                            exportProgress = p
                                        }
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
                                                // 不吞：以前这里是 `catch (_: Exception) {}`，
                                                // 而落点不在 file_paths 里时 getUriForFile 必抛
                                                // IllegalArgumentException —— 于是"导出成功"弹了、
                                                // 分享面板从没出现过，且没有任何线索。
                                                Toast.makeText(context, "分享没打开：${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "导出失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        localComicManager.deleteLocalComic(comic)
                                        Toast.makeText(context, "已删除本地漫画", Toast.LENGTH_SHORT).show()
                                        refresh()
                                    }
                                },
                                onOpenComicDetail = if (!comic.isExternal && comic.sourceName.isNotBlank() && comic.sourceName != "local") {
                                    { ->
                                        onOpenComicDetail(
                                            ComicItem(
                                                id = comic.id,
                                                title = comic.title,
                                                coverUrl = comic.coverPath,
                                                author = "",
                                                sourceName = comic.sourceName,
                                            )
                                        )
                                    }
                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 批量删除确认对话框（VeneraCard 风格）
            if (showDeleteConfirmDialog) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { showDeleteConfirmDialog = false }) {
                    VeneraCard(modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space8)) {
                        Column(modifier = Modifier.padding(tokens.spacing.space9)) {
                            Text(
                                text = "批量删除",
                                fontSize = tokens.type.itemTitle,
                                fontWeight = tokens.type.weightBold,
                                color = tokens.color.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(tokens.spacing.space5))
                            Text(
                                text = "确定删除选中的 " + selectedComicPaths.size + " 部漫画及所有离线文件吗？此操作不可逆",
                                fontSize = tokens.type.caption,
                                color = tokens.color.textSecondary,
                            )
                            Spacer(modifier = Modifier.height(tokens.spacing.space7))
                            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space5)) {
                                top.yukonga.miuix.kmp.basic.Button(
                                    onClick = { showDeleteConfirmDialog = false },
                                    modifier = Modifier.weight(1f),
                                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
                                        color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)
                                    ),
                                ) { Text("取消", color = tokens.color.textPrimary) }
                                top.yukonga.miuix.kmp.basic.Button(
                                    onClick = {
                                        showDeleteConfirmDialog = false
                                        scope.launch {
                                            val targets = comics.filter { it.rootPath in selectedComicPaths }
                                            targets.forEach { localComicManager.deleteLocalComic(it) }
                                            Toast.makeText(context, "已删除 " + targets.size + " 部本地漫画", Toast.LENGTH_SHORT).show()
                                            selectedComicPaths = emptySet()
                                            isSelectionMode = false
                                            refresh()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
                                        color = StatusColors.Failing
                                    ),
                                ) { Text("删除", color = StatusColors.OnBadgeSurface) }
                            }
                        }
                    }
                }
            }

            // 章节选择对话框
            if (selectedComicForChapters != null) {
                AlertDialog(
                    onDismissRequest = { selectedComicForChapters = null },
                    title = { Text(text = "${selectedComicForChapters?.title} (选择阅读章节)") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                        ) {
                            comicChapters.forEach { ch ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val c = selectedComicForChapters ?: return@clickable
                                            openChapterSession(c, ch, comicChapters, onOpenLocalSession)
                                            selectedComicForChapters = null
                                        }
                                        .padding(vertical = tokens.spacing.space5, horizontal = tokens.spacing.space2),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ch.title,
                                        fontSize = tokens.type.body,
                                        fontWeight = tokens.type.weightMedium,
                                        color = tokens.color.textPrimary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${ch.pageCount} 页",
                                        fontSize = tokens.type.caption,
                                        color = tokens.color.textSecondary
                                    )
                                }
                                HorizontalDivider(color = tokens.color.divider)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { selectedComicForChapters = null }) {
                            Text("关闭")
                        }
                    }
                )
            }

            // 导出进度遮罩
            if (isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VeneraCard(modifier = Modifier.padding(tokens.spacing.space9)) {
                        Column(
                            modifier = Modifier.padding(tokens.spacing.space9),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "正在打包导出 CBZ...",
                                fontSize = tokens.type.body,
                                fontWeight = tokens.type.weightBold,
                                color = tokens.color.textPrimary
                            )
                            Spacer(modifier = Modifier.height(tokens.spacing.space7))
                            LinearProgressIndicator(
                                progress = { exportProgress },
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(tokens.spacing.barHeight)
                                    .clip(RoundedCornerShape(tokens.spacing.space1)),
                                color = tokens.color.primary
                            )
                            Spacer(modifier = Modifier.height(tokens.spacing.space4))
                            Text(
                                "${(exportProgress * 100).toInt()} %",
                                fontSize = tokens.type.caption,
                                color = tokens.color.primary
                            )
                        }
                    }
            }
        }

        com.venera.compose.components.venera.VeneraTopAppBar(
            title = if (isSelectionMode) "已选 " + selectedComicPaths.size + " 部" else "本地书架",
            largeTitle = if (isSelectionMode) "已选 " + selectedComicPaths.size + " 部" else "本地离线书架",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            actions = {
                if (isSelectionMode) {
                    // ── 多选态：全选 / 全不选 · 批量删除 · 退出 ──
                    TextButton(onClick = {
                        selectedComicPaths = if (selectedComicPaths.size == filteredComics.size) {
                            emptySet()
                        } else {
                            filteredComics.map { it.rootPath }.toSet()
                        }
                    }) {
                        Text(
                            text = if (selectedComicPaths.size == filteredComics.size && filteredComics.isNotEmpty()) "全不选" else "全选",
                            fontSize = tokens.type.caption,
                            color = tokens.color.primary,
                        )
                    }
                    IconButton(
                        onClick = { if (selectedComicPaths.isNotEmpty()) showDeleteConfirmDialog = true },
                        enabled = selectedComicPaths.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "批量删除",
                            tint = if (selectedComicPaths.isNotEmpty()) StatusColors.Failing else tokens.color.textDisabled,
                        )
                    }
                    IconButton(onClick = {
                        isSelectionMode = false
                        selectedComicPaths = emptySet()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "退出多选",
                            tint = tokens.color.textPrimary,
                        )
                    }
                } else {
                    // ── 普通态：多选入口 · 布局切换 · 下载中心(带徽章) · 导入 CBZ ──
                    IconButton(onClick = { isSelectionMode = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Checklist,
                            contentDescription = "多选模式",
                            tint = tokens.color.textPrimary,
                        )
                    }
                    ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
                    Box {
                        IconButton(onClick = onNavigateToDownloads) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = "下载中心",
                                tint = tokens.color.textPrimary,
                            )
                        }
                        if (activeDownloadTasks.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = StatusColors.Degraded,
                                modifier = Modifier.align(Alignment.TopEnd).padding(tokens.spacing.space1),
                            ) {
                                Text(
                                    text = activeDownloadTasks.size.toString(),
                                    fontSize = tokens.type.badge,
                                    fontWeight = tokens.type.weightBold,
                                    color = StatusColors.OnBadgeSurface,
                                    modifier = Modifier.padding(horizontal = tokens.spacing.badgeHorizontalPadding),
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/vnd.comicbook+zip", "application/zip", "application/x-zip-compressed", "*/*"))
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = "导入 CBZ",
                            tint = tokens.color.primary,
                        )
                    }
                }
            },
        )
    }
}

private fun openChapterSession(
    comic: LocalComic,
    currentChapter: LocalChapter,
    allChapters: List<LocalChapter>,
    onOpenLocalSession: (ReaderSession) -> Unit
) {
    val readerChapters = allChapters.map { ch ->
        val mappedPages = ch.pageFiles.mapIndexed { idx, path ->
            ComicPageSource.LocalFile(File(path), idx)
        }
        ReaderChapter(
            id = ch.chapterId,
            title = ch.title,
            pages = mappedPages,
            isLoaded = true
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
        initialPageIndex = 0
    )
    onOpenLocalSession(session)
}

@Composable
fun LocalComicCard(
    comic: LocalComic,
    detailed: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onExportCbz: () -> Unit,
    onDelete: () -> Unit,
    onOpenComicDetail: (() -> Unit)? = null
) {
    val tokens = VeneraTokens
    var showMenu by remember { mutableStateOf(false) }

    VeneraCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Box {
            ComicCardLayout(
            detailed = detailed,
            modifier = Modifier.padding(tokens.spacing.cardContentPadding),
            cover = {
                Box {
                    VeneraCover(
                        url = comic.coverPath,
                        contentDescription = comic.title,
                        shimmerWhileLoading = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (detailed) Modifier.height(180.dp) else Modifier)
                    )

                    // 更多选项按钮
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                    ) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "选项",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // 本地导入/外部归档无源可跳，隐藏在线详情入口
                            if (onOpenComicDetail != null) {
                                DropdownMenuItem(
                                    text = { Text("查看在线详情") },
                                    onClick = {
                                        showMenu = false
                                        onOpenComicDetail?.invoke()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Language, contentDescription = null)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("导出标准 CBZ") },
                                onClick = {
                                    showMenu = false
                                    onExportCbz()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.FileUpload, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除离线文件", color = StatusColors.Failing) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = StatusColors.Failing)
                                }
                            )
                        }
                    }
                }
            },
            content = {
                Column(modifier = Modifier.padding(top = if (detailed) 0.dp else tokens.spacing.space2)) {
                    Text(
                        text = comic.title,
                        fontSize = if (detailed) tokens.type.body else tokens.type.caption,
                        fontWeight = tokens.type.weightMedium,
                        color = tokens.color.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(tokens.spacing.space1))

                    // 话数/P 数徽标：badge 字号 + primary 弱化色
                    Text(
                        text = "${comic.chapterCount} 话 · ${comic.totalPages}P",
                        fontSize = tokens.type.badge,
                        color = tokens.color.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space1))
                    // 源标识徽章：BadgeSurface 固定深色语义（压任何底色可读），badge 字号
                    Surface(
                        shape = RoundedCornerShape(tokens.shape.extraSmall),
                        color = StatusColors.BadgeSurface,
                    ) {
                        Text(
                            text = comic.sourceName.ifBlank { "离线" },
                            fontSize = tokens.type.badge,
                            fontWeight = tokens.type.weightSemibold,
                            color = StatusColors.OnBadgeSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                horizontal = tokens.spacing.badgeHorizontalPadding,
                                vertical = tokens.spacing.badgeVerticalPadding,
                            ),
                        )
                    }
                }
            }
        )

            // 多选态选中反馈：主色边框高亮 + 左上角勾选框
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = 2.dp,
                            color = tokens.color.primary,
                            shape = RoundedCornerShape(tokens.shape.card),
                        ),
                )
                Surface(
                    shape = CircleShape,
                    color = tokens.color.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(tokens.spacing.space2)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "已选中",
                        tint = tokens.color.onPrimary,
                        modifier = Modifier.padding(tokens.spacing.space1)
                    )
                }
            }
        }
    }
}


/* ------------------------------------------------------------------ *
 * 下载中心联动组件：正在下载横幅 / 失败警告横幅 / 源分类过滤条
 * ------------------------------------------------------------------ */

/** 正在下载任务横幅：漫画名 + 进度 + 速度，点击直达下载中心。 */
@Composable
private fun DownloadingBanner(tasks: List<DownloadTask>, onClick: () -> Unit) {
    val tokens = VeneraTokens
    VeneraCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    tint = tokens.color.primary,
                    modifier = Modifier.size(tokens.spacing.badgeIconSize)
                )
                Spacer(modifier = Modifier.width(tokens.spacing.space2))
                Text(
                    text = "正在下载 " + tasks.size + " 个任务",
                    fontSize = tokens.type.body,
                    fontWeight = tokens.type.weightSemibold,
                    color = tokens.color.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "查看 ›",
                    fontSize = tokens.type.caption,
                    color = tokens.color.primary
                )
            }
            tasks.take(2).forEach { task ->
                Spacer(modifier = Modifier.height(tokens.spacing.space2))
                Column {
                    Text(
                        text = task.comicTitle + " · " + task.chapterTitle,
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space1))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val progress = if (task.totalPages > 0) {
                            task.downloadedPages.toFloat() / task.totalPages
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(tokens.spacing.barHeight)
                                .clip(RoundedCornerShape(tokens.spacing.space1)),
                            color = tokens.color.primary,
                            trackColor = tokens.color.surfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(tokens.spacing.space3))
                        Text(
                            text = task.downloadedPages.toString() + "/" + task.totalPages + " 页" +
                                (if (task.speedText.isNotBlank()) " · " + task.speedText else ""),
                            fontSize = tokens.type.badge,
                            color = tokens.color.textTertiary
                        )
                    }
                }
            }
        }
    }
}

/** 失败任务警告横幅：红色警告态 + 如实错误详情 + 一键重试。 */
@Composable
private fun FailedTasksBanner(
    tasks: List<DownloadTask>,
    onRetry: (DownloadTask) -> Unit,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    VeneraCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = StatusColors.Failing,
                    modifier = Modifier.size(tokens.spacing.badgeIconSize)
                )
                Spacer(modifier = Modifier.width(tokens.spacing.space2))
                Text(
                    text = tasks.size.toString() + " 个任务下载失败",
                    fontSize = tokens.type.body,
                    fontWeight = tokens.type.weightSemibold,
                    color = StatusColors.Failing,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "管理 ›",
                    fontSize = tokens.type.caption,
                    color = tokens.color.primary
                )
            }
            tasks.take(2).forEach { task ->
                Spacer(modifier = Modifier.height(tokens.spacing.space2))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.comicTitle + " · " + task.chapterTitle,
                            fontSize = tokens.type.caption,
                            color = tokens.color.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 如实展示详细错误（网络状态码 / 超时 / 具体页码重试失败原因）
                        Text(
                            text = task.errorMsg ?: "未知错误",
                            fontSize = tokens.type.badge,
                            color = StatusColors.Failing,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(tokens.spacing.space3))
                    Surface(
                        shape = RoundedCornerShape(tokens.shape.small),
                        color = tokens.color.primary,
                        modifier = Modifier.clickable { onRetry(task) }
                    ) {
                        Text(
                            text = "重试",
                            fontSize = tokens.type.caption,
                            color = tokens.color.onPrimary,
                            modifier = Modifier.padding(
                                horizontal = tokens.spacing.space4,
                                vertical = tokens.spacing.space2,
                            )
                        )
                    }
                }
            }
        }
    }
}

/** 源分类过滤胶囊条（对齐收藏页药丸样式）。 */
@Composable
private fun CategoryFilterBar(categories: List<String>, selected: String, onSelect: (String) -> Unit) {
    val tokens = VeneraTokens
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)
    ) {
        items(categories.size) { idx ->
            val name = categories[idx]
            val isSelected = name == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) tokens.color.primaryContainer
                        else tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
                modifier = Modifier.clickable { onSelect(name) }
            ) {
                Text(
                    text = name,
                    fontSize = tokens.type.caption,
                    fontWeight = if (isSelected) tokens.type.weightSemibold else tokens.type.weightMedium,
                    color = if (isSelected) tokens.color.onPrimaryContainer else tokens.color.textSecondary,
                    modifier = Modifier.padding(
                        horizontal = tokens.spacing.space6,
                        vertical = tokens.spacing.space2,
                    )
                )
            }
        }
    }
}
