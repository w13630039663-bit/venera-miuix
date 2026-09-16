/**
 * S0-3 机械拆分自 MainActivity.kt（代码正文逐行原样搬运，未作改写）。
 */
package com.venera.compose.feature

import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
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
import com.venera.compose.data.db.*
import com.venera.compose.data.network.ImageHeaderPolicy
import com.venera.compose.data.prefs.*
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
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
    // S0-4：状态与网络请求下沉到 ComicDetailViewModel（旋转不再中断请求、离开页面可取消）
    val viewModel: ComicDetailViewModel = viewModel()
    val detailState by viewModel.uiState.collectAsStateWithLifecycle()

    val favorites by viewModel.favoritesFlow.collectAsState()
    val isFav = favorites.any { it.comicId == comic.id }

    val historyList by viewModel.historyFlow.collectAsState()
    val historyRecord = historyList.find { it.comicId == comic.id }

    val isReversed = detailState.reversed
    val liveDetails = detailState.details
    val isLoadingChapters = detailState.isLoading
    val loadingMessage = detailState.loadingMessage

    val sourceKey = ComicDetailViewModel.sourceKeyOf(comic.sourceName)

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
        val liveChs = liveDetails?.chapters ?: emptyList()
        if (liveChs.isNotEmpty()) {
            val target = liveChs.first()
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
                title = comic.title,
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
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "···", fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
            // 1. 顶部封面与作品标题信息
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = comic.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 104.dp, height = 144.dp)
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
                            .height(144.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = comic.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 2,
                                lineHeight = 24.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = comic.author,
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
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
                        }
                        Text(
                            text = "★ ${comic.rating}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB800)
                        )
                    }
                }
            }

            // 2. 历史进度悬浮横幅 (若读过则展示)
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

            // 3. 原版经典彩色圆钮动作条 (_ActionButton)
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
                            icon = Icons.Outlined.Download,
                            label = "下载",
                            iconColor = Color(0xFF00BCD4),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
                        DetailActionButton(
                            icon = Icons.Outlined.Comment,
                            label = "128",
                            iconColor = Color(0xFF4CAF50),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
                            }
                        )
                    }
                }
            }

            // 4. 移动端双主按钮 (下载 + 阅读)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) },
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
                                text = if (historyRecord != null) "继续阅读" else "开始阅读 (第 1 话)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            // 5. 分类标签卡片
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "标签与分类", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val tagsList = liveDetails?.comic?.tags?.ifEmpty { comic.tags } ?: comic.tags
                            tagsList.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
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

            // 6. 作品简介卡片
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "作品简介", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        val desc = liveDetails?.comic?.description?.ifEmpty { comic.description } ?: comic.description
                        Text(
                            text = desc.ifEmpty { "暂无详细简介" },
                            fontSize = 13.sp,
                            lineHeight = 22.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 7. 章节目录卡片
            item {
                val liveChs = liveDetails?.chapters ?: emptyList()
                val totalChapterCount = if (liveChs.isNotEmpty()) liveChs.size else comic.chapters.size

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (liveChs.isNotEmpty()) {
                                val list = if (isReversed) liveChs.reversed() else liveChs
                                list.take(60).forEachIndexed { idx, ch ->
                                    val actualIdx = if (isReversed) liveChs.lastIndex - idx else idx
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

            // 8. 最新评论卡片
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "最新评论 (128)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(text = "查看全部 ›", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        listOf(
                            Triple("勇者欣梅尔", "这集的分镜与演出太神了，治愈而感动！", "2小时前"),
                            Triple("魔法使菲伦", "追番到现在最满意的一部，坐等下一话更新。", "5小时前")
                        ).forEach { (user, comment, time) ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = user, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = time, fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = comment, fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
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
}

