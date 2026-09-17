package com.venera.compose.feature

import android.content.Intent
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.reader.*
import com.venera.compose.source.model.*

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.AndroidComicDetailScreen(
    comic: ComicItem,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onStartLiveReading: (ReaderSession) -> Unit,
    /** S8: 点击标签 → 跳转该标签的搜索结果（对齐官方 handleClickTagEvent 默认语义） */
    onSearchTag: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val viewModel: ComicDetailViewModel = viewModel()
    val detailState by viewModel.uiState.collectAsStateWithLifecycle()

    val isFav by viewModel.isLocalFav.collectAsStateWithLifecycle()
    val favPanel by viewModel.favPanel.collectAsStateWithLifecycle()

    val historyList by viewModel.historyFlow.collectAsState()
    val historyRecord = historyList.find { it.comicId == comic.id }

    val isReversed = detailState.reversed
    val liveDetails = detailState.details
    val loadingMessage = detailState.loadingMessage

    var isDescExpanded by remember { mutableStateOf(false) }
    var showCommentSheet by remember { mutableStateOf(false) }
    var newCommentText by remember { mutableStateOf("") }
    var isSendingComment by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }

    LaunchedEffect(comic.id) {
        viewModel.load(comic)
        viewModel.syncLocalFav(comic)
    }

    // 收藏面板的一次性提示
    LaunchedEffect(favPanel.toast) {
        favPanel.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.readerEvents.collect { event ->
            when (event) {
                is ReaderEvent.Live -> onStartLiveReading(event.session)
                // 解析不出图片就如实报错（历史上会悄悄打开一整套占位图）
                is ReaderEvent.Failed ->
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun launchChapter(chapterId: String, chapterTitle: String, fallbackIdx: Int, initialPage: Int = 0) {
        viewModel.openChapter(comic, chapterId, chapterTitle, fallbackIdx, initialPage)
    }

    fun onTriggerRead() {
        val groups = liveDetails?.chapterGroups ?: emptyList()
        val chs = if (groups.isNotEmpty()) {
            val group = groups.getOrNull(detailState.selectedGroupIndex) ?: groups.first()
            group.chapters
        } else {
            liveDetails?.chapters ?: emptyList()
        }

        if (chs.isNotEmpty()) {
            val target = chs.first()
            launchChapter(target.id, target.title, 0)
        } else if (liveDetails != null) {
            // 无章节源（EH 图库等）：整本即一章。对齐官方 reader.dart 的 eid 语义
            // `chapters?.ids.elementAtOrNull(chapter - 1) ?? '0'` —— 无章节时 eid 固定为 '0'。
            launchChapter("0", comic.title, 0)
        } else {
            // 详情尚未解析出章节列表 → 如实提示。
            // （历史上这里会打开「演示会话」，用硬编码占位图冒充漫画内容。）
            Toast.makeText(context, "章节列表尚未加载完成，请稍后再试", Toast.LENGTH_SHORT).show()
        }
    }

    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            onBack()
        } catch (_: Exception) { }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = liveDetails?.comic?.title ?: comic.title,
                navigationIcon = {
                    Box(modifier = Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
                        Text(text = "←", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "【${liveDetails?.comic?.title ?: comic.title}】\n作者：${liveDetails?.author ?: comic.author}\n${liveDetails?.url ?: ""}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "分享漫画"))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Outlined.Share, contentDescription = "分享", tint = MiuixTheme.colorScheme.onBackground)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. 详情加载失败横幅（此前 error 只写进 state 不渲染，用户无从得知失败原因）
            detailState.error?.let { err ->
                item(key = "detail-error") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 1. 顶部封面与作品标题信息 (S2 扩展字段对齐)
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val coverUrl = liveDetails?.comic?.cover?.ifBlank { comic.coverUrl } ?: comic.coverUrl
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 110.dp, height = 152.dp)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "image-${comic.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .shadow(
                                elevation = 10.dp,
                                shape = RoundedCornerShape(12.dp),
                                spotColor = Color.Black.copy(alpha = 0.40f)
                            )
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(152.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = liveDetails?.comic?.title ?: comic.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 2,
                                lineHeight = 24.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = liveDetails?.author?.ifBlank { comic.author } ?: comic.author,
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                ) {
                                    Text(
                                        text = comic.sourceName,
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                // 状态标签：源给了才显示（EH 等无连载概念的源不再被安上"连载中"）
                                val statusText = liveDetails?.status.orEmpty()
                                if (statusText.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (statusText.contains("完结")) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = statusText,
                                            fontSize = 11.sp,
                                            color = if (statusText.contains("完结")) Color(0xFF388E3C) else Color(0xFFE65100),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 评分与更新时间
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val stars = liveDetails?.stars ?: liveDetails?.rating ?: 0f
                            Text(
                                text = if (stars > 0f) "★ ${String.format("%.1f", stars)}" else "★ 暂无评分",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB800)
                            )
                            val updateTime = liveDetails?.updateTime ?: comic.latestChapter
                            if (updateTime.isNotBlank()) {
                                Text(
                                    text = updateTime.take(10),
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant
                                )
                            }
                        }
                    }
                }
            }

            // 2. 历史进度横幅
            if (historyRecord != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTriggerRead() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "📖", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "上次阅读至 ${historyRecord.lastChapterTitle} 第 ${historyRecord.lastPageIndex + 1} 页",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "继续阅读 ›",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 3. 原版经典彩色交互圆钮条
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    item {
                        DetailActionButton(
                            icon = Icons.Outlined.PlayCircleOutline,
                            label = if (historyRecord != null) "继续" else "开始",
                            iconColor = Color(0xFFFF9800),
                            onClick = { onTriggerRead() }
                        )
                    }
                    item {
                        DetailActionButton(
                            icon = if (detailState.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            label = if (detailState.likesCount > 0) "${detailState.likesCount}" else "点赞",
                            iconColor = Color(0xFFE91E63),
                            isActive = detailState.isLiked,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.toggleLike()
                            }
                        )
                    }
                    item {
                        DetailActionButton(
                            icon = if (isFav) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            label = if (isFav) "已收藏" else "收藏",
                            iconColor = Color(0xFF9C27B0),
                            isActive = isFav,
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                viewModel.quickFavorite(comic)
                            },
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.openFavoritePanel(comic)
                            }
                        )
                    }
                    item {
                        val commentCount = liveDetails?.commentCount ?: detailState.comments.size
                        DetailActionButton(
                            icon = Icons.Outlined.Comment,
                            label = if (commentCount > 0) "$commentCount" else "评论",
                            iconColor = Color(0xFF4CAF50),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showCommentSheet = true
                            }
                        )
                    }
                    item {
                        DetailActionButton(
                            icon = Icons.Outlined.Share,
                            label = "分享",
                            iconColor = Color(0xFF2196F3),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "【${liveDetails?.comic?.title ?: comic.title}】\n作者：${liveDetails?.author ?: comic.author}\n${liveDetails?.url ?: ""}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "分享漫画"))
                            }
                        )
                    }
                }
            }

            // 4. 双主按钮 (全本下载 + 开始/继续阅读)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showDownloadDialog = true
                        },
                        modifier = Modifier.weight(0.4f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        content = {
                            Text(text = "离线下载", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    )
                    Button(
                        onClick = { onTriggerRead() },
                        modifier = Modifier.weight(0.6f),
                        content = {
                            Text(
                                text = if (historyRecord != null) "继续阅读" else "开始阅读",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            // 5. 分类与命名空间标签卡片
            item {
                val tagMap = liveDetails?.tagMap.orEmpty()
                val flatTags = liveDetails?.comic?.tags?.ifEmpty { comic.tags } ?: comic.tags

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "标签与分类", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        if (tagMap.isNotEmpty()) {
                            tagMap.forEach { (category, tags) ->
                                if (tags.isNotEmpty()) {
                                    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "$category:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                                            modifier = Modifier.width(48.dp)
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            tags.forEach { tag ->
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                                    modifier = Modifier.clickable { onSearchTag(tag) }
                                                ) {
                                                    Text(
                                                        text = tag,
                                                        fontSize = 11.sp,
                                                        color = MiuixTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                flatTags.forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                        modifier = Modifier.clickable { onSearchTag(tag) }
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. 作品简介卡片 (支持折叠展开)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .clickable { isDescExpanded = !isDescExpanded }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "作品简介", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = if (isDescExpanded) "收起 ↑" else "展开 ↓",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val desc = liveDetails?.comic?.description?.ifBlank { comic.description } ?: comic.description
                        Text(
                            text = desc.ifBlank { "暂无详细简介" },
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = if (isDescExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 7. 官方预览图（对齐官方 comic_details_page/thumbnails.dart：
            //    网格铺排 + 分页加载 + 点任意一张从该页开读）
            val thumbnails = detailState.thumbnails.ifEmpty { liveDetails?.thumbnails.orEmpty() }
            if (thumbnails.isNotEmpty() || detailState.isLoadingThumbnails) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (thumbnails.isEmpty()) "预览" else "预览 (${thumbnails.size} 页)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (detailState.isLoadingThumbnails) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // 三列网格（末行不足三张时补空位，避免被拉伸变形）
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                thumbnails.chunked(3).forEachIndexed { rowIdx, rowItems ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowItems.forEachIndexed { colIdx, thumbUrl ->
                                            val pageIndex = rowIdx * 3 + colIdx
                                            val targetChId = detailState.previewChapterId.ifBlank {
                                                liveDetails?.chapters?.firstOrNull()?.id ?: "0"
                                            }
                                            val targetChTitle = liveDetails?.chapters?.firstOrNull { it.id == targetChId }?.title
                                                ?: comic.title
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(0.7f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { launchChapter(targetChId, targetChTitle, 0, pageIndex) }
                                            ) {
                                                AsyncImage(
                                                    model = thumbUrl,
                                                    contentDescription = "第 ${pageIndex + 1} 页",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                        repeat(3 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            detailState.thumbnailError?.let { err ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = err, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }

                            if (detailState.hasMoreThumbnails) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { viewModel.loadThumbnails(loadMore = true) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ),
                                    content = { Text(text = "加载更多预览", fontSize = 13.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // 8. 章节目录卡片（多分组 Tabs + 排序切换 + 已读标记）
            item {
                val groups = liveDetails?.chapterGroups ?: emptyList()
                val currentChapters = if (groups.isNotEmpty()) {
                    val group = groups.getOrNull(detailState.selectedGroupIndex) ?: groups.first()
                    group.chapters
                } else {
                    liveDetails?.chapters ?: emptyList()
                }
                // 真实章节数：只数源详情给出的章节，**不再回退**列表页那份 comic.chapters
                // （那份数据的默认值曾是硬编码的 60 个假章节，会导致 EH 等无章节源
                //   显示一份凭空捏造的目录）。
                val totalChapterCount = currentChapters.size
                val hasRealChapters = currentChapters.isNotEmpty()

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // 标题与正倒序切换（无章节可排时不显示排序按钮）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (hasRealChapters) "章节目录 (共 $totalChapterCount 话)" else "章节目录",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (hasRealChapters) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    modifier = Modifier.clickable { viewModel.setReversed(!isReversed) }
                                ) {
                                    Text(
                                        text = if (isReversed) "正序 ↑" else "倒序 ↓",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // 多分组 Tabs（若存在多个章节分组，如单行本/连载中/番外篇）
                        if (groups.size > 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(groups.indices.toList()) { gIdx ->
                                    val group = groups[gIdx]
                                    val isSelected = detailState.selectedGroupIndex == gIdx
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable { viewModel.selectGroup(gIdx) }
                                    ) {
                                        Text(
                                            text = group.name,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (loadingMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⏳ $loadingMessage",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 章节网格
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (hasRealChapters) {
                                val list = if (isReversed) currentChapters.reversed() else currentChapters
                                list.take(80).forEachIndexed { idx, ch ->
                                    val actualIdx = if (isReversed) currentChapters.lastIndex - idx else idx
                                    val isCurrentHistoryChapter = historyRecord?.lastChapterIndex == actualIdx
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrentHistoryChapter) {
                                            MiuixTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        } else {
                                            MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        },
                                        modifier = Modifier.clickable {
                                            launchChapter(ch.id, ch.title, actualIdx)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = ch.title,
                                                fontSize = 12.sp,
                                                color = if (isCurrentHistoryChapter) {
                                                    MiuixTheme.colorScheme.primary
                                                } else {
                                                    MiuixTheme.colorScheme.onSurface
                                                },
                                                fontWeight = if (isCurrentHistoryChapter) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (isCurrentHistoryChapter) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(text = "•", color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            } else if (liveDetails != null) {
                                // 无章节源（EH 图库等）：整本即一章，给出**真实**的阅读入口。
                                // 对齐官方 reader.dart 的 eid 语义（无章节时 eid 固定 '0'）。
                                //
                                // 这里此前回退渲染 comic.chapters —— 而它的默认值是硬编码的
                                // 60 个假章节（"第 60 话"…"第 1 话"），点进去只会弹
                                // 「章节信息未加载完成」。任何无章节概念的源都会因此显示
                                // 一份凭空捏造的目录。
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "本作没有章节目录，整本一次读完。",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onBackgroundVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val totalPages = liveDetails?.maxPage ?: 0
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { launchChapter("0", comic.title, 0) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.PlayCircleOutline,
                                                contentDescription = null,
                                                tint = MiuixTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "阅读整本",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MiuixTheme.colorScheme.primary
                                                )
                                                if (totalPages > 1) {
                                                    Text(
                                                        text = "共 $totalPages 页",
                                                        fontSize = 11.sp,
                                                        color = MiuixTheme.colorScheme.onBackgroundVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // 详情尚未返回：如实说明，不伪造任何内容
                                Text(
                                    text = "章节信息加载中…",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant
                                )
                            }
                        }
                    }
                }
            }

            // 9. 关联推荐作品 (recommend)
            val recommendList = liveDetails?.recommend.orEmpty()
            if (recommendList.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = "相关推荐", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(recommendList) { recComic ->
                                    Column(
                                        modifier = Modifier
                                            .width(90.dp)
                                            .clickable {
                                                // 切换加载推荐作品
                                                viewModel.load(
                                                    ComicItem(
                                                        id = recComic.id,
                                                        title = recComic.title,
                                                        coverUrl = recComic.cover,
                                                        author = "",
                                                        sourceName = recComic.sourceKey.ifBlank { comic.sourceName }
                                                    )
                                                )
                                            }
                                    ) {
                                        AsyncImage(
                                            model = recComic.cover,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(width = 90.dp, height = 120.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = recComic.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 10. 真实评论区卡片
            item {
                val comments = detailState.comments
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "精彩评论 (${if (comments.isNotEmpty()) comments.size else "0"})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "写评论 / 全部 ›",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showCommentSheet = true }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        if (comments.isEmpty()) {
                            Text(
                                text = "暂无评论，快来抢首评吧~",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            comments.take(3).forEach { c ->
                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(text = c.userName.take(1), fontSize = 11.sp, color = MiuixTheme.colorScheme.primary)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = c.userName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text(text = c.time.orEmpty(), fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = c.content, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface)
                                }
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // 评论全量浏览与发表 Sheet
    if (showCommentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCommentSheet = false },
            containerColor = MiuixTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .imePadding()
            ) {
                Text(
                    text = "全部评论 (${detailState.comments.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 输入框
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        placeholder = { Text("说点什么吧...", fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newCommentText.isNotBlank() && !isSendingComment) {
                                isSendingComment = true
                                viewModel.sendComment(newCommentText) { success, errMsg ->
                                    isSendingComment = false
                                    if (success) {
                                        newCommentText = ""
                                        Toast.makeText(context, "评论发表成功", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, errMsg ?: "发表失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        content = {
                            Text(text = if (isSendingComment) "发送中" else "发送", fontSize = 13.sp)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 评论列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(detailState.comments) { c ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = c.userName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = c.time.orEmpty(), fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = c.content, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    // 收藏面板（对齐官方 _FavoritePanel：本地 + 网络双分区，互不同步）
    if (favPanel.visible) {
        FavoritePanelSheet(
            state = favPanel,
            onDismiss = { viewModel.closeFavoritePanel() },
            onToggleLocal = { folder -> viewModel.toggleLocalFavorite(comic, folder) },
            onCreateFolder = { name, onErr -> viewModel.createLocalFolder(name, onErr) },
            onToggleNetwork = { folderId, isAdded -> viewModel.toggleNetworkFavorite(comic, folderId, isAdded) },
        )
    }

    // 批量离线下载对话框 (S6)
    if (showDownloadDialog) {
        val groups = liveDetails?.chapterGroups ?: emptyList()
        val allChs = if (groups.isNotEmpty()) {
            val group = groups.getOrNull(detailState.selectedGroupIndex) ?: groups.first()
            group.chapters
        } else {
            liveDetails?.chapters ?: emptyList()
        }
        var selectedIds by remember(allChs) { mutableStateOf(allChs.map { it.id }.toSet()) }

        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("选择下载章节", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        selectedIds = if (selectedIds.size == allChs.size) emptySet() else allChs.map { it.id }.toSet()
                    }) {
                        Text(if (selectedIds.size == allChs.size) "全不选" else "全选", fontSize = 12.sp)
                    }
                }
            },
            text = {
                if (allChs.isEmpty()) {
                    Text("暂无可下载章节", fontSize = 13.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                    ) {
                        items(allChs, key = { it.id }) { ch ->
                            val isChecked = selectedIds.contains(ch.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (isChecked) selectedIds - ch.id else selectedIds + ch.id
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + ch.id else selectedIds - ch.id
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = ch.title,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDownload = allChs.filter { selectedIds.contains(it.id) }
                        if (toDownload.isNotEmpty()) {
                            val dlMgr = com.venera.compose.download.DownloadManager.getInstance(context)
                            dlMgr.enqueue(
                                sourceKey = liveDetails?.sourceKey ?: comic.sourceName,
                                comicId = comic.id,
                                comicTitle = liveDetails?.comic?.title ?: comic.title,
                                comicCover = liveDetails?.comic?.cover ?: comic.coverUrl,
                                chapters = toDownload
                            )
                            Toast.makeText(context, "已将 ${toDownload.size} 话加入下载队列", Toast.LENGTH_SHORT).show()
                        }
                        showDownloadDialog = false
                    },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text("开始下载 (${selectedIds.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ==================== 详情页收藏面板 ====================

/** 分区标题（对齐官方 `_LocalSection` / `_NetworkSection` 的小标题）。 */
@Composable
private fun FavSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
    )
}

/** 收藏夹行右侧的「收藏 / 移除」胶囊（对齐官方 `_HoverButton`）。 */
@Composable
private fun FavToggleChip(
    isAdded: Boolean,
    enabled: Boolean = true,
    loading: Boolean = false,
    onToggle: () -> Unit,
) {
    if (loading) {
        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        return
    }
    val bg = when {
        !enabled -> MiuixTheme.colorScheme.surfaceVariant
        isAdded -> Color(0xFFE53935)
        else -> MiuixTheme.colorScheme.primary
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = Modifier.clickable(enabled = enabled) { onToggle() },
    ) {
        Text(
            text = if (isAdded) "移除" else "收藏",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White else MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** 一个收藏夹条目：名字 + （可选）已添加徽标 + 右侧动作。 */
@Composable
private fun FavRow(
    title: String,
    added: Boolean,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (added) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
            ) {
                Text(
                    text = "已添加",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        trailing()
    }
}

/**
 * 收藏面板内容。
 *
 * 官方结构：本地分区永远显示；网络分区只在「源声明了 favorites 且已登录」时出现，
 * 两者由一个分隔线隔开，顺序由设置项 `localFavoritesFirst` 决定（默认本地在前）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritePanelSheet(
    state: FavoritePanelState,
    onDismiss: () -> Unit,
    onToggleLocal: (String) -> Unit,
    onCreateFolder: (String, (String) -> Unit) -> Unit,
    onToggleNetwork: (folderId: String, isAdded: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var showNewFolder by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MiuixTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "收藏",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))

            // ---------- 本地收藏分区 ----------
            FavSectionTitle("本地收藏")
            state.localFolders.forEach { folder ->
                val added = folder in state.localAdded
                FavRow(title = folder, added = added) {
                    FavToggleChip(
                        isAdded = added,
                        loading = folder in state.pending,
                        onToggle = { onToggleLocal(folder) },
                    )
                }
            }

            // 新建收藏夹（对齐官方 `_LocalSection` 末尾那行）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showNewFolder = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "新建收藏夹", fontSize = 14.sp, color = MiuixTheme.colorScheme.primary)
            }

            // ---------- 网络收藏分区 ----------
            if (state.hasNetwork) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(4.dp))
                FavSectionTitle("网络收藏")

                when {
                    state.isLoadingNetwork -> {
                        repeat(2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f)),
                                )
                                Spacer(modifier = Modifier.width(60.dp))
                            }
                        }
                    }

                    state.networkError != null -> {
                        Text(
                            text = "网络收藏加载失败：${state.networkError}",
                            fontSize = 13.sp,
                            color = Color(0xFFE53935),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }

                    // 多收藏夹源
                    state.networkMultiFolder -> {
                        if (state.networkFolders.isEmpty()) {
                            Text(
                                text = "该账号下还没有网络收藏夹",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        state.networkFolders.forEach { (id, name) ->
                            val added = id in state.networkAdded
                            // 只允许单夹的源：已进别的夹时，其余夹的「收藏」按钮禁用
                            val enabled = !(state.singleFolderForSingleComic && state.networkAdded.isNotEmpty() && !added)
                            FavRow(title = name, added = added) {
                                FavToggleChip(
                                    isAdded = added,
                                    enabled = enabled,
                                    loading = "net:$id" in state.pending,
                                    onToggle = { onToggleNetwork(id, added) },
                                )
                            }
                        }
                    }

                    // 单收藏夹源：一个整体开关（folderId 传 ""，对齐官方）
                    else -> {
                        val added = state.networkSingleAdded
                        FavRow(title = "网络收藏", added = added) {
                            FavToggleChip(
                                isAdded = added,
                                loading = "net:" in state.pending,
                                onToggle = { onToggleNetwork("", added) },
                            )
                        }
                    }
                }
            } else if (state.localFolders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "该漫画源未登录或不支持网络收藏，当前仅能收藏到本地",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (showNewFolder) {
        var folderName by remember { mutableStateOf("") }
        var nameError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("新建收藏夹", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = {
                            folderName = it
                            nameError = null
                        },
                        singleLine = true,
                        label = { Text("收藏夹名") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    nameError?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = it, fontSize = 12.sp, color = Color(0xFFE53935))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isBlank()) {
                            nameError = "收藏夹名不能为空"
                        } else {
                            onCreateFolder(folderName) { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            showNewFolder = false
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolder = false }) { Text("取消") }
            },
        )
    }
}
