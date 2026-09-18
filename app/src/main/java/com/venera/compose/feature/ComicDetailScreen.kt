package com.venera.compose.feature

import android.content.Intent
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraSourceBadge
import com.venera.compose.components.venera.VeneraTagChip
import com.venera.compose.download.GALLERY_CHAPTER_ID
import com.venera.compose.download.downloadChapters
import com.venera.compose.reader.*
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.source.model.*
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.AndroidComicDetailScreen(
    comic: ComicItem,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onStartLiveReading: (ReaderSession) -> Unit,
    /** S8: 点击标签 → 跳转该标签的搜索结果（对齐官方 handleClickTagEvent 默认语义） */
    onSearchTag: (String) -> Unit = {},
    /** S8 批次C: 点击封面 → 全屏查看器 */
    onOpenCoverViewer: (String) -> Unit = {}
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
    var newCommentText by remember(comic.sourceName, comic.id, detailState.replyTo?.id) { mutableStateOf("") }
    val isSendingComment = detailState.isSendingComment
    var showDownloadDialog by remember { mutableStateOf(false) }

    LaunchedEffect(comic.sourceName, comic.id) {
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
            // 无章节源（EH 图库等）：整本即一章。对齐官方 reader.dart 的 eid 语义：
            // 无章节时 eid 固定为 0。
            launchChapter(GALLERY_CHAPTER_ID, comic.title, 0)
        } else {
            // 详情尚未解析出章节列表 → 如实提示。
            // （历史上这里会打开「演示会话」，用硬编码占位图冒充漫画内容。）
            Toast.makeText(context, "章节列表尚未加载完成，请稍后再试", Toast.LENGTH_SHORT).show()
        }
    }

    // Route back is owned by NavHost, including its seekable predictive transition.
    val tokens = VeneraTokens
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // 顶栏收敛：只留返回 + 分享（上滑时标题淡入）。
            // 不用 Miuix TopAppBar(title = "")——它在底层仍预留 60+dp 大标题位，
            // 详情页顶部会出现巨大的空白荒漠；这里改为自绘 48dp 紧凑单行顶栏。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(48.dp)
                    .padding(horizontal = tokens.spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
                // 上滑时标题淡入，在顶部时保持通透（标题已在封面右侧展示）
                AnimatedVisibility(
                    visible = listState.firstVisibleItemIndex > 0,
                    modifier = Modifier.weight(1f).padding(horizontal = tokens.spacing.space2),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = liveDetails?.comic?.title ?: comic.title,
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightBold,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (listState.firstVisibleItemIndex == 0) {
                    Spacer(modifier = Modifier.weight(1f))
                }
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText(liveDetails, comic))
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "分享漫画"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "分享",
                        tint = tokens.color.textPrimary,
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(tokens.spacing.rowHorizontal),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sectionGap)
        ) {
            // 0. 详情加载失败横幅（此前 error 只写进 state 不渲染，用户无从得知失败原因）
            detailState.error?.let { err ->
                item(key = "detail-error") {
                    Surface(
                        shape = RoundedCornerShape(tokens.shape.small),
                        color = tokens.color.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space5),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = StatusColors.Failing
                            )
                            Spacer(modifier = Modifier.width(tokens.spacing.space4))
                            Text(
                                text = err,
                                color = tokens.color.textSecondary,
                                fontSize = tokens.type.caption
                            )
                        }
                    }
                }
            }
            // 1. 顶部封面与作品标题信息 (S2 扩展字段对齐)
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val coverUrl = liveDetails?.comic?.cover?.ifBlank { comic.coverUrl } ?: comic.coverUrl
                    // 内容守卫：详情页同样走判定链（JM/哔咔/R18 等命中 → 毛玻璃打码 + R18 角标）。
                    // sourceKey 传显示名 comic.sourceName，由守卫别名解析链（显示名 → sourceKey）对齐源级预设。
                    val guard = ContentGuardManager.getInstance(context)
                    val maskState = guard.coverMaskStateFor(
                        sourceKey = comic.sourceName,
                        title = liveDetails?.comic?.title ?: comic.title,
                        author = liveDetails?.author ?: comic.author,
                        tags = liveDetails?.comic?.tags ?: comic.tags,
                        comicId = comic.id,
                    )
                    // 封面容器：VeneraCover 的 fillMaxWidth + aspectRatio 契约由定宽 Box 表达，
                    // sharedElement 挂在外层 Box 上（与列表页卡片同一 shared key，过渡照常）。
                    Box(
                        modifier = Modifier
                            .width(tokens.spacing.detailCoverWidth)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "image-" + comic.id),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                    ) {
                        VeneraCover(
                            url = coverUrl,
                            contentDescription = "封面，点击全屏查看",
                            shimmerWhileLoading = false,
                            mask = if (maskState == "VISIBLE") VeneraCoverMask.Visible else VeneraCoverMask.Masked,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            VeneraSourceBadge(name = comic.sourceName)
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { onOpenCoverViewer(coverUrl) }
                        )
                    }
                    Spacer(modifier = Modifier.width(tokens.spacing.space6))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(tokens.spacing.detailCoverHeight),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = liveDetails?.comic?.title ?: comic.title,
                                fontWeight = tokens.type.weightBold,
                                fontSize = tokens.type.itemTitle,
                                color = tokens.color.textPrimary,
                                maxLines = 2,
                                lineHeight = tokens.type.screenTitle
                            )
                            Spacer(modifier = Modifier.height(tokens.spacing.space2))
                            Text(
                                text = liveDetails?.author?.ifBlank { comic.author } ?: comic.author,
                                fontSize = tokens.type.caption,
                                color = tokens.color.textSecondary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(tokens.spacing.space2))
                            val statusText = liveDetails?.status.orEmpty()
                            if (statusText.isNotBlank()) {
                                val finished = statusText.contains("完结")
                                Surface(
                                    shape = RoundedCornerShape(tokens.shape.extraSmall),
                                    color = if (finished) StatusColors.Healthy.copy(alpha = 0.15f) else StatusColors.Degraded.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = statusText,
                                        fontSize = tokens.type.overline,
                                        color = if (finished) StatusColors.Healthy else StatusColors.Degraded,
                                        fontWeight = tokens.type.weightMedium,
                                        modifier = Modifier.padding(horizontal = tokens.spacing.space2, vertical = 1.dp)
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
                                text = if (stars > 0f) "★ " + kotlin.String.format(java.util.Locale.ROOT, "%.1f", stars) else "★ 暂无评分",
                                fontSize = tokens.type.body,
                                fontWeight = tokens.type.weightBold,
                                color = StatusColors.RatingStar
                            )
                            val updateTime = liveDetails?.updateTime ?: comic.latestChapter
                            if (updateTime.isNotBlank()) {
                                Text(
                                    text = updateTime.take(10),
                                    fontSize = tokens.type.overline,
                                    color = tokens.color.textTertiary
                                )
                            }
                        }
                    }
                }
            }


            // 2. 主操作条：唯一大号胶囊「开始/继续阅读」+「离线下载」。
            //    废除旧的「彩色圆钮条里的开始/分享」与旧双按钮 —— 分享收口顶栏，阅读只此一处。
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6)
                ) {
                    // 大号胶囊「开始阅读 / 继续阅读（带历史进度提示）」
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = tokens.color.primary,
                        modifier = Modifier
                            .weight(1f)
                            .height(tokens.spacing.detailPrimaryButtonHeight)
                            .clickable { onTriggerRead() },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayCircleOutline,
                                contentDescription = null,
                                tint = tokens.color.onPrimary,
                                modifier = Modifier.size(tokens.spacing.badgeIconSize)
                            )
                            Spacer(modifier = Modifier.width(tokens.spacing.space3))
                            Column {
                                Text(
                                    text = if (historyRecord != null) "继续阅读" else "开始阅读",
                                    fontSize = tokens.type.body,
                                    fontWeight = tokens.type.weightBold,
                                    color = tokens.color.onPrimary
                                )
                                if (historyRecord != null) {
                                    Text(
                                        text = "继续 " + historyRecord.lastChapterTitle + " · 第 " + (historyRecord.lastPageIndex + 1) + " 页",
                                        fontSize = tokens.type.overline,
                                        color = tokens.color.onPrimary.copy(alpha = 0.8f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    // 离线下载（次级表面色胶囊）
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
                        modifier = Modifier
                            .height(tokens.spacing.detailPrimaryButtonHeight)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showDownloadDialog = true
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = tokens.spacing.space7),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                tint = tokens.color.textSecondary,
                                modifier = Modifier.size(tokens.spacing.badgeIconSize)
                            )
                            Spacer(modifier = Modifier.width(tokens.spacing.space2))
                            Text(
                                text = "离线下载",
                                fontSize = tokens.type.body,
                                fontWeight = tokens.type.weightSemibold,
                                color = tokens.color.textSecondary
                            )
                        }
                    }
                }
            }

            // 3. 辅助操作行：收藏 / 点赞 / 评论 / 分享 四联功能钮（语义色走语义色板）
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailActionButton(
                        icon = if (isFav) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        label = if (isFav) "已收藏" else "收藏",
                        iconColor = StatusColors.Favorite,
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
                    DetailActionButton(
                        icon = if (detailState.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        label = if (detailState.likesCount > 0) "" + detailState.likesCount else "点赞",
                        iconColor = StatusColors.Like,
                        isActive = detailState.isLiked,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.toggleLike()
                        }
                    )
                    val commentCount = liveDetails?.commentCount ?: detailState.comments.size
                    DetailActionButton(
                        icon = Icons.Outlined.Comment,
                        label = if (commentCount > 0) "$commentCount" else "评论",
                        iconColor = StatusColors.Comment,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showCommentSheet = true
                        }
                    )
                    DetailActionButton(
                        icon = Icons.Outlined.Share,
                        label = "分享",
                        iconColor = StatusColors.Share,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText(liveDetails, comic))
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享漫画"))
                        }
                    )
                }
            }

            // 4. 分类与命名空间标签卡片：VeneraTagChip + 点击直达标签搜索
            item {
                val tagMap = liveDetails?.tagMap.orEmpty()
                val flatTags = liveDetails?.comic?.tags?.ifEmpty { comic.tags } ?: comic.tags

                VeneraCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "标签与分类",
                        fontWeight = tokens.type.weightBold,
                        fontSize = tokens.type.sectionTitle,
                        color = tokens.color.textPrimary
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space5))

                    if (tagMap.isNotEmpty()) {
                        tagMap.forEach { (category, tags) ->
                            if (tags.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.padding(vertical = tokens.spacing.space2),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "$category:",
                                        fontSize = tokens.type.caption,
                                        fontWeight = tokens.type.weightBold,
                                        color = tokens.color.textSecondary,
                                        modifier = Modifier.width(tokens.spacing.detailCategoryLabelWidth)
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3)
                                    ) {
                                        tags.forEach { tag ->
                                            VeneraTagChip(
                                                text = tag,
                                                onClick = { onSearchTag(tag) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4)
                        ) {
                            flatTags.forEach { tag ->
                                VeneraTagChip(
                                    text = tag,
                                    onClick = { onSearchTag(tag) }
                                )
                            }
                        }
                    }
                }
            }

            // 5. 作品简介卡片 (支持折叠展开)
            item {
                VeneraCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDescExpanded = !isDescExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "作品简介",
                            fontWeight = tokens.type.weightBold,
                            fontSize = tokens.type.sectionTitle,
                            color = tokens.color.textPrimary
                        )
                        Text(
                            text = if (isDescExpanded) "收起 ↑" else "展开 ↓",
                            fontSize = tokens.type.caption,
                            color = tokens.color.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(tokens.spacing.space4))
                    val desc = liveDetails?.comic?.description?.ifBlank { comic.description } ?: comic.description
                    Text(
                        text = desc.ifBlank { "暂无详细简介" },
                        fontSize = tokens.type.caption,
                        lineHeight = tokens.type.body * 1.6f,
                        color = tokens.color.textSecondary,
                        maxLines = if (isDescExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { isDescExpanded = !isDescExpanded }
                    )
                }
            }

            // 6. 官方预览图（对齐官方 comic_details_page/thumbnails.dart：
            //    网格铺排 + 分页加载 + 点任意一张从该页开读）
            val thumbnails = detailState.thumbnails.ifEmpty { liveDetails?.thumbnails.orEmpty() }
            if (thumbnails.isNotEmpty() || detailState.isLoadingThumbnails) {
                item {
                    VeneraCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (thumbnails.isEmpty()) "预览" else "预览 (" + thumbnails.size + " 页)",
                                fontWeight = tokens.type.weightBold,
                                fontSize = tokens.type.sectionTitle,
                                color = tokens.color.textPrimary
                            )
                            if (detailState.isLoadingThumbnails) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(tokens.spacing.statusDotSize * 2),
                                    strokeWidth = 2.dp,
                                    color = tokens.color.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(tokens.spacing.space5))

                        // 三列网格（末行不足三张时补空位，避免被拉伸变形）
                        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
                            thumbnails.chunked(3).forEachIndexed { rowIdx, rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
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
                                                .clip(RoundedCornerShape(tokens.shape.small))
                                                .clickable { launchChapter(targetChId, targetChTitle, 0, pageIndex) }
                                        ) {
                                            AsyncImage(
                                                model = thumbUrl,
                                                contentDescription = "第 " + (pageIndex + 1) + " 页",
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
                            Spacer(modifier = Modifier.height(tokens.spacing.space4))
                            Text(
                                text = err,
                                fontSize = tokens.type.overline,
                                color = StatusColors.Failing
                            )
                        }

                        if (detailState.hasMoreThumbnails) {
                            Spacer(modifier = Modifier.height(tokens.spacing.space5))
                            Surface(
                                shape = RoundedCornerShape(tokens.shape.small),
                                color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.loadThumbnails(loadMore = true) },
                            ) {
                                Text(
                                    text = "加载更多预览",
                                    fontSize = tokens.type.caption,
                                    color = tokens.color.textSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = tokens.spacing.space5)
                                )
                            }
                        }
                    }
                }
            }

            // 7. 章节目录卡片（多分组 Tabs + 排序切换 + 已读标记）
            item {
                val groups = liveDetails?.chapterGroups ?: emptyList()
                val currentChapters = if (groups.isNotEmpty()) {
                    val group = groups.getOrNull(detailState.selectedGroupIndex) ?: groups.first()
                    group.chapters
                } else {
                    liveDetails?.chapters ?: emptyList()
                }
                // 真实章节数：只数源详情给出的章节，不再回退列表页那份 comic.chapters
                // （那份数据的默认值曾是硬编码的 60 个假章节，会导致 EH 等无章节源
                //   显示一份凭空捏造的目录）。
                val totalChapterCount = currentChapters.size
                val hasRealChapters = currentChapters.isNotEmpty()

                VeneraCard(modifier = Modifier.fillMaxWidth()) {
                    // 标题与正倒序切换（无章节可排时不显示排序按钮）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasRealChapters) "章节目录 (共 " + totalChapterCount + " 话)" else "章节目录",
                            fontWeight = tokens.type.weightBold,
                            fontSize = tokens.type.sectionTitle,
                            color = tokens.color.textPrimary
                        )
                        if (hasRealChapters) {
                            VeneraChip(
                                text = if (isReversed) "正序 ↑" else "倒序 ↓",
                                selected = false,
                                onClick = { viewModel.setReversed(!isReversed) }
                            )
                        }
                    }

                    // 多分组 Tabs（若存在多个章节分组，如单行本/连载中/番外篇）
                    if (groups.size > 1) {
                        Spacer(modifier = Modifier.height(tokens.spacing.space5))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
                            items(groups.indices.toList()) { gIdx ->
                                val group = groups[gIdx]
                                val isSelected = detailState.selectedGroupIndex == gIdx
                                VeneraChip(
                                    text = group.name,
                                    selected = isSelected,
                                    onClick = { viewModel.selectGroup(gIdx) }
                                )
                            }
                        }
                    }

                    if (loadingMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(tokens.spacing.space4))
                        Surface(
                            shape = RoundedCornerShape(tokens.shape.extraSmall),
                            color = tokens.color.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⏳ " + loadingMessage,
                                fontSize = tokens.type.caption,
                                color = tokens.color.primary,
                                modifier = Modifier.padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space3)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(tokens.spacing.space6))

                    when {
                        // 详情加载中：标准空态组件，不再是一行裸文字
                        liveDetails == null && detailState.isLoading -> VeneraEmptyView(
                            message = "章节信息加载中…"
                        )
                        // 详情加载失败：标准空态组件 + 重试
                        liveDetails == null && detailState.error != null -> VeneraEmptyView(
                            title = "章节信息加载失败",
                            message = detailState.error ?: "",
                            actionText = "重试",
                            onAction = { viewModel.load(comic) }
                        )
                        hasRealChapters -> {
                            // 章节网格：VeneraCard 胶囊 + 已读/未读对比度走 Token
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4)
                            ) {
                                val list = if (isReversed) currentChapters.reversed() else currentChapters
                                list.take(80).forEachIndexed { idx, ch ->
                                    val actualIdx = if (isReversed) currentChapters.lastIndex - idx else idx
                                    val isCurrentHistoryChapter = historyRecord?.lastChapterIndex == actualIdx
                                    VeneraCard(
                                        modifier = Modifier
                                            .widthIn(min = tokens.spacing.detailChapterChipMinWidth)
                                            .border(
                                                width = 1.dp,
                                                color = if (isCurrentHistoryChapter) tokens.color.primary else Color.Transparent,
                                                shape = RoundedCornerShape(tokens.shape.card)
                                            ),
                                        onClick = { launchChapter(ch.id, ch.title, actualIdx) }
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = ch.title,
                                                fontSize = tokens.type.caption,
                                                color = if (isCurrentHistoryChapter) tokens.color.primary else tokens.color.textPrimary,
                                                fontWeight = if (isCurrentHistoryChapter) tokens.type.weightBold else tokens.type.weightRegular,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isCurrentHistoryChapter) {
                                                Spacer(modifier = Modifier.width(tokens.spacing.space2))
                                                Icon(
                                                    imageVector = Icons.Filled.CheckCircle,
                                                    contentDescription = "上次读到这里",
                                                    tint = tokens.color.primary,
                                                    modifier = Modifier.size(tokens.spacing.statusDotSize * 2)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        liveDetails != null -> {
                            // 无章节源（EH 图库等）：整本即一章，给出真实的阅读入口。
                            // 对齐官方 reader.dart 的 eid 语义（无章节时 eid 固定为 0）。
                            // 这里此前回退渲染 comic.chapters —— 而它的默认值是硬编码的
                            // 60 个假章节，点进去只会弹「章节信息未加载完成」。
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "本作没有章节目录，整本一次读完。",
                                    fontSize = tokens.type.caption,
                                    color = tokens.color.textSecondary
                                )
                                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                                val totalPages = liveDetails?.maxPage ?: 0
                                Surface(
                                    shape = RoundedCornerShape(tokens.shape.medium),
                                    color = tokens.color.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { launchChapter(GALLERY_CHAPTER_ID, comic.title, 0) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = tokens.spacing.space7, vertical = tokens.spacing.space6),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PlayCircleOutline,
                                            contentDescription = null,
                                            tint = tokens.color.primary
                                        )
                                        Spacer(modifier = Modifier.width(tokens.spacing.space4))
                                        Column {
                                            Text(
                                                text = "阅读整本",
                                                fontSize = tokens.type.body,
                                                fontWeight = tokens.type.weightBold,
                                                color = tokens.color.primary
                                            )
                                            if (totalPages > 1) {
                                                Text(
                                                    text = "共 " + totalPages + " 页",
                                                    fontSize = tokens.type.overline,
                                                    color = tokens.color.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. 关联推荐作品 (recommend)
            val recommendList = liveDetails?.recommend.orEmpty()
            if (recommendList.isNotEmpty()) {
                item {
                    VeneraCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "相关推荐",
                            fontWeight = tokens.type.weightBold,
                            fontSize = tokens.type.sectionTitle,
                            color = tokens.color.textPrimary
                        )
                        Spacer(modifier = Modifier.height(tokens.spacing.space5))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space5)) {
                            items(recommendList) { recComic ->
                                Column(
                                    modifier = Modifier
                                        .width(tokens.spacing.detailRecommendWidth)
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
                                            .size(width = tokens.spacing.detailRecommendWidth, height = tokens.spacing.detailRecommendCoverHeight)
                                            .clip(RoundedCornerShape(tokens.shape.small)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(tokens.spacing.space2))
                                    Text(
                                        text = recComic.title,
                                        fontSize = tokens.type.caption,
                                        fontWeight = tokens.type.weightMedium,
                                        color = tokens.color.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 9. 真实评论区卡片
            item {
                val comments = detailState.comments
                VeneraCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "精彩评论 (" + (if (comments.isNotEmpty()) comments.size else "0") + ")",
                            fontWeight = tokens.type.weightBold,
                            fontSize = tokens.type.sectionTitle,
                            color = tokens.color.textPrimary
                        )
                        Text(
                            text = "写评论 / 全部 ›",
                            fontSize = tokens.type.caption,
                            color = tokens.color.primary,
                            modifier = Modifier.clickable { showCommentSheet = true }
                        )
                    }
                    Spacer(modifier = Modifier.height(tokens.spacing.space5))

                    CommentLoadStatus(
                        thread = detailState.commentThread,
                        supported = detailState.commentCapabilities.canLoad,
                        onRetry = { viewModel.loadComments(loadMore = detailState.commentThread.requestedPage > 1) }
                    )
                    if (comments.isEmpty() && detailState.commentThread.loaded &&
                        !detailState.commentThread.isLoading && detailState.commentThread.error == null) {
                        Text(
                            text = "暂无评论",
                            fontSize = tokens.type.caption,
                            color = tokens.color.textSecondary,
                            modifier = Modifier.padding(vertical = tokens.spacing.space4)
                        )
                    } else {
                        comments.take(3).forEach { c ->
                            Column(modifier = Modifier.padding(vertical = tokens.spacing.space3)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = tokens.color.primary.copy(alpha = 0.2f),
                                            modifier = Modifier.size(tokens.spacing.badgeSize - tokens.spacing.space2)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = c.userName.take(1),
                                                    fontSize = tokens.type.overline,
                                                    color = tokens.color.primary
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(tokens.spacing.space2))
                                        Text(
                                            text = c.userName,
                                            fontSize = tokens.type.caption,
                                            fontWeight = tokens.type.weightSemibold,
                                            color = tokens.color.textPrimary
                                        )
                                    }
                                    Text(
                                        text = c.time.orEmpty(),
                                        fontSize = tokens.type.overline,
                                        color = tokens.color.textTertiary
                                    )
                                }
                                Spacer(modifier = Modifier.height(tokens.spacing.space2))
                                Text(
                                    text = c.content,
                                    fontSize = tokens.type.caption,
                                    color = tokens.color.textSecondary
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = tokens.color.divider,
                                modifier = Modifier.padding(vertical = tokens.spacing.space2)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(tokens.spacing.space9))
            }
        }
    }


    // Root comments and reply threads share a sheet but never overwrite one another.
    if (showCommentSheet) {
        val thread = detailState.activeCommentThread
        val replyTo = detailState.replyTo
        ModalBottomSheet(
            onDismissRequest = { showCommentSheet = false; viewModel.closeReplies() },
            containerColor = MiuixTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (replyTo != null) {
                        TextButton(onClick = { viewModel.closeReplies() }, enabled = !isSendingComment) {
                            Text("返回评论")
                        }
                    }
                    Text(
                        text = if (replyTo == null) "全部评论 (" + thread.items.size + ")" else "回复 " + replyTo.userName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (detailState.commentCapabilities.canLoad) {
                        TextButton(
                            onClick = { viewModel.loadComments(replyId = replyTo?.id) },
                            enabled = !thread.isLoading
                        ) { Text("刷新") }
                    }
                }
                if (replyTo != null) {
                    Text(replyTo.content, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (detailState.commentCapabilities.canSend) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = newCommentText,
                            onValueChange = { newCommentText = it },
                            enabled = !isSendingComment,
                            placeholder = { Text(if (replyTo == null) "说点什么吧..." else "回复这条评论...", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 140.dp),
                            maxLines = 5,
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
                            enabled = newCommentText.isNotBlank() && !isSendingComment,
                            onClick = {
                                viewModel.sendComment(newCommentText, replyTo?.id) { success, errMsg ->
                                    if (success) newCommentText = ""
                                    Toast.makeText(context, if (success) "评论发表成功" else errMsg ?: "发表失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) { Text(if (isSendingComment) "发送中" else "发送", fontSize = 13.sp) }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                CommentLoadStatus(
                    thread = thread,
                    supported = detailState.commentCapabilities.canLoad,
                    onRetry = { viewModel.loadComments(loadMore = thread.requestedPage > 1, replyId = replyTo?.id) }
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (thread.loaded && thread.items.isEmpty() && !thread.isLoading && thread.error == null) {
                        item { Text("暂无评论", fontSize = 13.sp) }
                    }
                    items(thread.items) { c ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = c.userName, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(text = c.time.orEmpty(), fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = c.content, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface)
                            // Missing replyCount means unsupported; an explicit zero still allows replies.
                            if (c.replyCount != null && c.id.isNotBlank() && detailState.commentCapabilities.canLoad) {
                                TextButton(onClick = { viewModel.openReplies(c) }, enabled = !isSendingComment) {
                                    Text("查看 / 回复 (" + c.replyCount + ")", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    if (thread.loaded && thread.hasMore && !thread.isLoading && thread.error == null) {
                        item {
                            TextButton(onClick = { viewModel.loadComments(loadMore = true, replyId = replyTo?.id) }) {
                                Text("加载更多评论")
                            }
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
            sourceName = comic.sourceName,
            onDismiss = { viewModel.closeFavoritePanel() },
            onToggleLocal = { folder -> viewModel.toggleLocalFavorite(comic, folder) },
            onCreateFolder = { name, onErr -> viewModel.createLocalFolder(name, onErr) },
            onToggleNetwork = { folderId, isAdded -> viewModel.toggleNetworkFavorite(comic, folderId, isAdded) },
        )
    }

    // 批量离线下载对话框 (S6)
    if (showDownloadDialog) {
        val allChs = downloadChapters(liveDetails, detailState.selectedGroupIndex)
        val isGallery = liveDetails != null && liveDetails.chapters.isEmpty() &&
            liveDetails.chapterGroups.all { it.chapters.isEmpty() }
        var selectedIds by remember(allChs) { mutableStateOf(allChs.map { it.id }.toSet()) }

        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isGallery) "下载整本" else "选择下载章节", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        selectedIds = if (selectedIds.size == allChs.size) emptySet() else allChs.map { it.id }.toSet()
                    }) {
                        Text(if (selectedIds.size == allChs.size) "全不选" else "全选", fontSize = 12.sp)
                    }
                }
            },
            text = {
                if (allChs.isEmpty()) {
                    Text(if (liveDetails == null) "详情尚未加载完成，请稍后重试" else "当前分组暂无可下载章节", fontSize = 13.sp)
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
                                sourceKey = viewModel.currentSourceKey(),
                                comicId = liveDetails?.comic?.id ?: comic.id,
                                comicTitle = liveDetails?.comic?.title ?: comic.title,
                                comicCover = liveDetails?.comic?.cover ?: comic.coverUrl,
                                chapters = toDownload
                            )
                            Toast.makeText(context, "已将 " + toDownload.size + " 话加入下载队列", Toast.LENGTH_SHORT).show()
                        }
                        showDownloadDialog = false
                    },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text("开始下载 (" + selectedIds.size + ")")
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

/** 分享文案（顶栏与辅助行共用，消除重复实现）。 */
private fun shareText(
    details: ComicDetails?,
    comic: ComicItem,
): String =
    "【" + (details?.comic?.title ?: comic.title) + "】\n作者：" + (details?.author ?: comic.author) + "\n" + (details?.url ?: "")

/** 内容守卫判定（详情页封面用；sourceKey 传显示名，走守卫别名解析链）。 */
private fun detailMaskState(
    guard: ContentGuardManager,
    sourceName: String,
    title: String,
    author: String,
    tags: List<String>,
    comicId: String,
): String = guard.coverMaskStateFor(
    sourceKey = sourceName,
    title = title,
    author = author,
    tags = tags,
    comicId = comicId,
)

@Composable
private fun CommentLoadStatus(thread: DetailCommentState, supported: Boolean, onRetry: () -> Unit) {
    val tokens = VeneraTokens
    when {
        thread.isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(tokens.spacing.statusDotSize * 2),
                strokeWidth = 2.dp,
                color = tokens.color.primary
            )
            Spacer(modifier = Modifier.width(tokens.spacing.space4))
            Text(
                "评论加载中…",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary
            )
        }
        thread.error != null -> Column {
            Text(
                thread.error,
                fontSize = tokens.type.caption,
                color = StatusColors.Failing
            )
            TextButton(onClick = onRetry) {
                Text("重试", color = tokens.color.primary)
            }
        }
        !supported && thread.items.isEmpty() -> Text(
            "该源未提供评论加载功能",
            fontSize = tokens.type.caption,
            color = tokens.color.textSecondary
        )
    }
}

// ==================== 详情页收藏面板 ====================

/** 分区标题（对齐官方 _LocalSection / _NetworkSection 的小标题）。 */
@Composable
private fun FavSectionTitle(text: String) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.body,
        fontWeight = tokens.type.weightSemibold,
        color = tokens.color.primary,
        modifier = Modifier.padding(start = tokens.spacing.space2, top = tokens.spacing.space3, bottom = tokens.spacing.space1),
    )
}

/** 收藏夹行右侧的「收藏 / 移除」胶囊（对齐官方 _HoverButton）。 */
@Composable
private fun FavToggleChip(
    isAdded: Boolean,
    enabled: Boolean = true,
    loading: Boolean = false,
    onToggle: () -> Unit,
) {
    val tokens = VeneraTokens
    if (loading) {
        Box(modifier = Modifier.size(tokens.spacing.badgeSize + 2.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(tokens.spacing.badgeIconSize),
                strokeWidth = 2.dp,
                color = tokens.color.primary
            )
        }
        return
    }
    val bg = when {
        !enabled -> tokens.color.surfaceVariant
        isAdded -> StatusColors.Failing
        else -> tokens.color.primary
    }
    Surface(
        shape = RoundedCornerShape(tokens.shape.small),
        color = bg,
        modifier = Modifier.clickable(enabled = enabled) { onToggle() },
    ) {
        Text(
            text = if (isAdded) "移除" else "收藏",
            fontSize = tokens.type.caption,
            fontWeight = tokens.type.weightMedium,
            color = if (enabled) StatusColors.OnBadgeSurface else tokens.color.textDisabled,
            modifier = Modifier.padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space1),
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
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.small))
            .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = tokens.type.body,
            color = tokens.color.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (added) {
            Surface(
                shape = RoundedCornerShape(tokens.shape.extraSmall),
                color = tokens.color.primary.copy(alpha = 0.15f),
            ) {
                Text(
                    text = "已添加",
                    fontSize = tokens.type.overline,
                    color = tokens.color.primary,
                    modifier = Modifier.padding(horizontal = tokens.spacing.space2, vertical = 1.dp),
                )
            }
            Spacer(modifier = Modifier.width(tokens.spacing.space4))
        }
        trailing()
    }
}

/**
 * 收藏面板内容。
 *
 * 官方结构：本地分区永远显示；网络分区只在「源声明了 favorites 且已登录」时出现，
 * 两者由一个分隔线隔开，顺序由设置项 localFavoritesFirst 决定（默认本地在前）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritePanelSheet(
    state: FavoritePanelState,
    /** 漫画所属源（显示名，与网络分区摘要对齐）。 */
    sourceName: String,
    onDismiss: () -> Unit,
    onToggleLocal: (String) -> Unit,
    onCreateFolder: (String, (String) -> Unit) -> Unit,
    onToggleNetwork: (folderId: String, isAdded: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val tokens = VeneraTokens
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
                .padding(horizontal = tokens.spacing.space8)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "收藏",
                fontSize = 18.sp,
                fontWeight = tokens.type.weightBold,
                color = tokens.color.textPrimary,
            )
            Spacer(modifier = Modifier.height(tokens.spacing.space2))

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

            // 新建收藏夹（对齐官方 _LocalSection 末尾那行）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.shape.small))
                    .clickable { showNewFolder = true }
                    .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = tokens.color.primary,
                    modifier = Modifier.size(tokens.spacing.badgeIconSize + 2.dp),
                )
                Spacer(modifier = Modifier.width(tokens.spacing.space2))
                Text(text = "新建收藏夹", fontSize = tokens.type.body, color = tokens.color.primary)
            }

            // ---------- 网络收藏分区 ----------
            if (state.hasNetwork) {
                Spacer(modifier = Modifier.height(tokens.spacing.space6))
                HorizontalDivider(color = tokens.color.divider)
                Spacer(modifier = Modifier.height(tokens.spacing.space2))
                FavSectionTitle("网络收藏")
                // 源归属与收藏状态摘要：该漫画来自哪个源、在源账号上是否已被收藏。
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = tokens.spacing.space2, top = tokens.spacing.space1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "漫画源：" + sourceName,
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                    )
                    Spacer(modifier = Modifier.width(tokens.spacing.space4))
                    val added = if (state.networkMultiFolder) state.networkAdded.isNotEmpty() else state.networkSingleAdded
                    when {
                        state.isLoadingNetwork -> Text(
                            text = "查询中…",
                            fontSize = tokens.type.overline,
                            color = tokens.color.textTertiary,
                        )
                        state.networkError != null -> Text(
                            text = "查询失败",
                            fontSize = tokens.type.overline,
                            color = StatusColors.Degraded,
                        )
                        else -> Text(
                            text = if (added) "已在 " + sourceName + " 收藏" else "尚未在 " + sourceName + " 收藏",
                            fontSize = tokens.type.overline,
                            color = if (added) StatusColors.Healthy else tokens.color.textTertiary,
                            fontWeight = if (added) tokens.type.weightSemibold else tokens.type.weightRegular,
                        )
                    }
                }

                when {
                    state.isLoadingNetwork -> {
                        repeat(2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space6),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(tokens.spacing.space1))
                                        .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha)),
                                )
                                Spacer(modifier = Modifier.width(60.dp))
                            }
                        }
                    }

                    state.networkError != null -> {
                        Text(
                            text = "网络收藏加载失败：" + state.networkError,
                            fontSize = tokens.type.caption,
                            color = StatusColors.Failing,
                            modifier = Modifier.padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space4),
                        )
                    }

                    // 多收藏夹源
                    state.networkMultiFolder -> {
                        if (state.networkFolders.isEmpty()) {
                            Text(
                                text = "该账号下还没有网络收藏夹",
                                fontSize = tokens.type.caption,
                                color = tokens.color.textSecondary,
                                modifier = Modifier.padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space4),
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
                                    loading = "net:" + id in state.pending,
                                    onToggle = { onToggleNetwork(id, added) },
                                )
                            }
                        }
                    }

                    // 单收藏夹源：一个整体开关（folderId 传空串，对齐官方）
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
                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                Text(
                    text = "" + sourceName + "未登录或不支持网络收藏，当前仅能收藏到本地",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                    modifier = Modifier.padding(horizontal = tokens.spacing.space2),
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
                        Spacer(modifier = Modifier.height(tokens.spacing.space2))
                        Text(text = it, fontSize = tokens.type.caption, color = StatusColors.Failing)
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
