package com.venera.compose.feature

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.core.content.FileProvider
import com.venera.compose.components.*
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraSourceBadge
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
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
    onOpenLocalSession: (ReaderSession) -> Unit
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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.space2, vertical = tokens.spacing.space4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(tokens.spacing.space1))
                Text(
                    text = "本地离线书架",
                    fontSize = tokens.type.screenTitle,
                    fontWeight = tokens.type.weightBold,
                    color = tokens.color.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
                Spacer(modifier = Modifier.width(tokens.spacing.space2))
                Button(
                    onClick = {
                        importLauncher.launch(arrayOf("application/vnd.comicbook+zip", "application/zip", "application/x-zip-compressed", "*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(color = tokens.color.primary)
                ) {
                    Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null, tint = tokens.color.onPrimary, modifier = Modifier.size(tokens.spacing.chipIconSize))
                    Spacer(modifier = Modifier.width(tokens.spacing.space1))
                    Text(text = "导入 CBZ", color = tokens.color.onPrimary, fontSize = tokens.type.caption)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MiuixTheme.colorScheme.primary)
                    }
                }

                comics.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        VeneraEmptyView(
                            icon = Icons.Outlined.MenuBook,
                            title = "暂无本地离线漫画",
                            message = "可以在漫画详情页下载章节，或点击右上角导入外部 CBZ 归档",
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (displayMode.value == "detailed") 1 else 2),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(comics, key = { it.rootPath }) { comic ->
                            LocalComicCard(
                                comic = comic,
                                detailed = displayMode.value == "detailed",
                                onClick = {
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
                                            } catch (_: Exception) {}
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
                                }
                            )
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
        }
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
    onClick: () -> Unit,
    onExportCbz: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = VeneraTokens
    var showMenu by remember { mutableStateOf(false) }

    VeneraCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
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
    }
}
