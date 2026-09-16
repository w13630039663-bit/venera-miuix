package com.venera.compose.feature

import android.content.Intent
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    onStartReading: (chapterIndex: Int, pageIndex: Int) -> Unit,
    onStartLiveReading: (ReaderSession) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val viewModel: ComicDetailViewModel = viewModel()
    val detailState by viewModel.uiState.collectAsStateWithLifecycle()

    val favorites by viewModel.favoritesFlow.collectAsState()
    val isFav = favorites.any { it.comicId == comic.id }

    val historyList by viewModel.historyFlow.collectAsState()
    val historyRecord = historyList.find { it.comicId == comic.id }

    val isReversed = detailState.reversed
    val liveDetails = detailState.details
    val loadingMessage = detailState.loadingMessage

    var isDescExpanded by remember { mutableStateOf(false) }
    var showCommentSheet by remember { mutableStateOf(false) }
    var newCommentText by remember { mutableStateOf("") }
    var isSendingComment by remember { mutableStateOf(false) }

    LaunchedEffect(comic.id) { viewModel.load(comic) }

    LaunchedEffect(Unit) {
        viewModel.readerEvents.collect { event ->
            when (event) {
                is ReaderEvent.Live -> onStartLiveReading(event.session)
                is ReaderEvent.Sample -> onStartReading(event.chapterIndex, event.pageIndex)
            }
        }
    }

    fun launchChapter(chapterId: String, chapterTitle: String, fallbackIdx: Int) {
        viewModel.openChapter(comic, chapterId, chapterTitle, fallbackIdx)
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
        } else if (historyRecord != null) {
            onStartReading(historyRecord.lastChapterIndex, historyRecord.lastPageIndex)
        } else {
            onStartReading(0, 0)
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
                                val statusText = liveDetails?.status ?: "连载中"
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
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.toggleFavorite(comic)
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
                            Toast.makeText(context, "批量下载功能已加入下载队列 (S6)", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(0.4f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        content = {
                            Text(text = "下载全本", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                                                    modifier = Modifier.clickable {
                                                        Toast.makeText(context, "搜索标签: $tag", Toast.LENGTH_SHORT).show()
                                                    }
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
                                        modifier = Modifier.clickable {
                                            Toast.makeText(context, "搜索标签: $tag", Toast.LENGTH_SHORT).show()
                                        }
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

            // 7. 章节全景画廊/缩略图 (thumbnails)
            val thumbnails = liveDetails?.thumbnails.orEmpty()
            if (thumbnails.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = "精彩画廊 (${thumbnails.size} 页)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(thumbnails) { thumbUrl ->
                                    AsyncImage(
                                        model = thumbUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(width = 80.dp, height = 110.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
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
                val totalChapterCount = if (currentChapters.isNotEmpty()) currentChapters.size else comic.chapters.size

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // 标题与正倒序切换
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "章节目录 (共 $totalChapterCount 话)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
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
                            if (currentChapters.isNotEmpty()) {
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
                            } else {
                                val list = if (isReversed) comic.chapters.reversed() else comic.chapters
                                list.take(36).forEachIndexed { idx, chapter ->
                                    val actualIdx = if (isReversed) comic.chapters.lastIndex - idx else idx
                                    val isCurrentHistoryChapter = historyRecord?.lastChapterIndex == actualIdx
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrentHistoryChapter) {
                                            MiuixTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        } else {
                                            MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        },
                                        modifier = Modifier.clickable {
                                            val pIndex = if (isCurrentHistoryChapter) historyRecord.lastPageIndex else 0
                                            onStartReading(actualIdx, pIndex)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = chapter,
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
}
