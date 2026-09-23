package com.venera.compose.feature

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.components.CoverHeroBackdrop
import com.venera.compose.components.RichCommentContent
import com.venera.compose.components.comicPreviewColumnCount
import com.venera.compose.components.rememberContentWidth
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.data.network.ComicUrlTable
import com.venera.compose.data.network.ImagePipelinePolicy
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.data.tags.TagTranslationManager
import com.venera.compose.data.tags.rememberTagDisplayLabel
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraShimmer
import com.venera.compose.components.venera.VeneraSourceBadge
import com.venera.compose.components.venera.VeneraTagChip
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.download.GALLERY_CHAPTER_ID
import com.venera.compose.download.downloadChapters
import com.venera.compose.reader.*
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.source.model.*
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens

/** 章节胶囊首屏上限；超出后给「显示全部」出口，不静默裁掉。 */
private const val CHAPTER_CHIP_LIMIT = 80

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.AndroidComicDetailScreen(
    comic: ComicItem,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onStartLiveReading: (ReaderSession) -> Unit,
    /**
     * 进这一页后要立刻打开的那一页（插图收藏长按带来的）。
     * null = 普通进入。非空时本页会在**源详情目录就绪后**按章节标题找回章节并推阅读器；
     * 标题对不上时如实提示并留在详情页，绝不随手开第一章糊弄。
     */
    readTarget: ReadTarget? = null,
    /** S8: 点击标签 → 跳转该标签的搜索结果（对齐官方 handleClickTagEvent 默认语义） */
    onSearchTag: (String) -> Unit = {},
    /** S8 批次C: 点击封面 → 全屏查看器 */
    onOpenCoverViewer: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val viewModel: ComicDetailViewModel = viewModel()
    // 标签译文只改药丸显示文本；onSearchTag 仍收到站点原文，点击语义与翻译无关。
    val tagLabel = rememberTagDisplayLabel()
    // 分组名（命名空间）中文化用同一本字典的 rows；字典没有的键原样显示，不做猜测翻译。
    val tagDict = remember(context) { TagTranslationManager.getInstance(context) }
    // 标签长按菜单的两个动作。**复制的是显示名**（所见即所复制），
    // **屏蔽的是站点原值** —— TAG 规则对「ns:value」与裸 value 双路命中，存原值两种写法都盖得住；
    // 存译文会因字典更新而失效（屏蔽列表里出现中文键）。
    val guardForTags = remember(context) {
        com.venera.compose.security.guard.ContentGuardManager.getInstance(context)
    }
    val tagActionScope = rememberCoroutineScope()
    val copyTag: (String) -> Unit = { raw ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("tag", tagLabel(raw)))
        Toast.makeText(context, "已复制「${tagLabel(raw)}」", Toast.LENGTH_SHORT).show()
    }
    val blockTag: (String) -> Unit = { raw ->
        tagActionScope.launch {
            val exists = guardForTags.rules.value
                .any { it.type == "TAG" && it.pattern.equals(raw, ignoreCase = true) }
            when {
                exists -> Toast.makeText(context, "「${tagLabel(raw)}」已在屏蔽列表", Toast.LENGTH_SHORT).show()
                guardForTags.addRule("TAG", raw) >= 0 -> Toast.makeText(
                    context, "已屏蔽「${tagLabel(raw)}」，可在设置 → 内容屏蔽管理", Toast.LENGTH_SHORT
                ).show()
                else -> Toast.makeText(context, "屏蔽失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val detailState by viewModel.uiState.collectAsStateWithLifecycle()

    val isFav by viewModel.isLocalFav.collectAsStateWithLifecycle()
    // 源账号（网络收藏）上的收藏状态：参与收藏图标标深与摘要判定
    val isNetworkFav by viewModel.isNetworkFav.collectAsStateWithLifecycle()
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

    // ── 预览图挂载窗口 ──
    // 折叠态只渲染前 PREVIEW_LIMIT 张：详情自带全量预览的源（nhentai、hitomi）一次返回整本
    // 两三百张，不拦就是几百个图片请求同时发出 + 几百项长期挂在组合里（每张封面还带一个
    // VeneraShimmer 无限动画，未加载完之前每帧都在跑）。「查看更多预览」每点一次窗口翻倍，
    // 于是每轮的组合量与并发取图数都被限在"和上一轮同量级"，而不是一口气全挂。
    var previewMount by remember(comic.id) { mutableIntStateOf(PREVIEW_LIMIT) }
    // 预览格宽上限 200dp（master thumbnails.dart 的 maxCrossAxisExtent），手机上原本
    // 就是 3 列，所以 3 是下限：大屏只许加列，不许把已定稿的手机三列改窄。
    val (previewWidth, previewWidthModifier) = rememberContentWidth()
    val previewColumns = comicPreviewColumnCount(previewWidth)
    val previewThumbnails = remember(
        detailState.thumbnails, liveDetails, detailState.thumbnailsExpanded, previewMount,
    ) {
        val all = detailState.thumbnails.ifEmpty { liveDetails?.thumbnails.orEmpty() }
        if (detailState.thumbnailsExpanded) all.take(previewMount) else all.take(PREVIEW_LIMIT)
    }
    // chunked 每次都会新建两层 List，不 remember 就是每次重组换引用，把这一块的强跳过打掉。
    val previewRows = remember(previewThumbnails, previewColumns) {
        previewThumbnails.chunked(previewColumns)
    }
    // 只换算**挂载窗口**这批的缩略图配置（源 comic.onThumbnailLoad：给防盗头、可能换镜像域）。
    // 挂在窗口变化上而不是 load 完成时一次算全 —— 详情自带全量预览的源一次返回整本两三百张，
    // 全算等于把 JS 主线程往返按整本长度放大。VM 侧按 url 记一次，展开时只补新增的那批。
    LaunchedEffect(previewThumbnails) {
        viewModel.ensureThumbnailConfigs(previewThumbnails)
    }

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

    // 「默认倒序排列章节」：偏好只作进入详情页时的初值，用户在本页的手动切换优先，
    // 因此只在首次组合应用一次，不订阅后续偏好变化。
    LaunchedEffect(Unit) {
        if (VeneraPreferences.getInstance(context).reverseChapterOrder.value) {
            viewModel.setReversed(true)
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

    // ── 插图收藏「从该页开始阅读」 ──
    // 收藏里落下的是章节**标题**（章节 id 当年没入库），而阅读会话只能在详情就绪后由源
    // 解析目录构造，所以这条只能在页内找回章节。跨**所有**分组找：jm 这类源一本书几十个
    // 分组，只看当前选中分组会稳定找不回来。目录迟迟不来的失败态如实说，不开第一章冒充。
    var readTargetFired by remember { mutableStateOf(false) }
    LaunchedEffect(readTarget, liveDetails, detailState.error) {
        val target = readTarget
        val details = liveDetails
        if (target == null || readTargetFired) return@LaunchedEffect
        if (details == null) {
            val error = detailState.error
            if (error != null) {
                readTargetFired = true
                Toast.makeText(context, "详情加载失败，没能定位到「${target.chapterTitle}」：$error", Toast.LENGTH_LONG)
                    .show()
            }
            return@LaunchedEffect
        }
        when (val hit = findReadTargetChapter(details, target.chapterTitle)) {
            is ReadChapterHit.Found -> {
                readTargetFired = true
                // 先把该章所在分组切过去：openChapter 取的 allChapters 是「当前分组」的兄弟章，
                // 不切的话阅读器里的上下章会串到别的分组去。
                hit.groupIndex?.let { viewModel.selectGroup(it) }
                launchChapter(hit.chapter.id, hit.chapter.title, hit.indexInGroup, target.pageIndex)
            }
            // 无章节目录的源（EH 图库等）：整本即一章，与 onTriggerRead 同一口径。
            is ReadChapterHit.WholeComic -> {
                readTargetFired = true
                launchChapter(GALLERY_CHAPTER_ID, target.chapterTitle, 0, target.pageIndex)
            }
            is ReadChapterHit.NotFound -> {
                readTargetFired = true
                Toast.makeText(
                    context,
                    "目录里已无「${target.chapterTitle}」，请在章节列表里手动选择",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    // Route back is owned by NavHost, including its seekable predictive transition.
    val tokens = VeneraTokens
    val listState = rememberLazyListState()
    val detailBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .blurBackdropSource(detailBackdrop),
            contentPadding = PaddingValues(
                start = tokens.spacing.rowHorizontal,
                end = tokens.spacing.rowHorizontal,
                top = statusBarTop + tokens.spacing.detailTopBarClearance,
                bottom = tokens.spacing.space8,
            ),
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
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Hero 背景：模糊放大的封面铺满头部，并**出血**到屏幕左/右/上三条边
                    // （左右消掉列表 contentPadding 留下的两条边，上边顶穿状态栏与顶栏）。
                    // 出血量由 CoverHeroBackdrop 用绘制层变换实现 —— 这里不能给 Modifier.padding
                    // 传负值，Compose 硬校验，运行时直接 "Padding must be non-negative" 闪退。
                    // ⚠️ 打码命中时**整层不画**：前景那张封面被模糊 + 暗遮罩 + R18 角标三重压住，
                    // 背景再铺一张同图的放大模糊版，等于把打码绕过一半。
                    if (maskState == "VISIBLE") {
                        CoverHeroBackdrop(
                            coverUrl = coverUrl,
                            modifier = Modifier.matchParentSize(),
                            bleedHorizontal = tokens.spacing.rowHorizontal,
                            bleedTop = statusBarTop + tokens.spacing.detailTopBarClearance,
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                    // 封面容器：VeneraCover 的 fillMaxWidth + aspectRatio 契约由定宽 Box 表达，
                    // sharedElement 挂在外层 Box 上。key 与列表卡片同一口径
                    //（ComicSharedTransition.coverKey，一律 sourceKey —— 真机探针已核对两端逐字相同）。
                    // 打码命中时不挂：共享元素会被渲染进 SharedTransitionLayout 的 overlay，
                    // 不能给遮罩留任何绕开页面裁剪的机会。
                    val coverSharedKey = remember(comic.sourceName, comic.id) {
                        com.venera.compose.components.ComicSharedTransition.coverKey(comic.sourceName, comic.id)
                    }
                    Box(
                        modifier = Modifier
                            .width(tokens.spacing.detailCoverWidth)
                            .then(
                                if (maskState == "VISIBLE") {
                                    Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState(key = coverSharedKey),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = com.venera.compose.components.ComicSharedTransition.CoverBounds,
                                    )
                                } else {
                                    Modifier
                                }
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

                        // 评分与更新时间。**已提交的评分必须优先显示** ——
                        // 否则交完评分界面还是源里的旧均值，用户读到的是「点了没反应」。
                        var showRatingDialog by remember { mutableStateOf(false) }
                        val myRating = detailState.userRating
                        val stars = if (myRating > 0f) myRating
                            else liveDetails?.stars ?: liveDetails?.rating ?: 0f
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = (if (stars > 0f) "★ " + kotlin.String.format(java.util.Locale.ROOT, "%.1f", stars) else "★ 暂无评分") +
                                    if (myRating > 0f) " 我的评分" else "",
                                fontSize = tokens.type.body,
                                fontWeight = tokens.type.weightBold,
                                color = StatusColors.RatingStar,
                                // 只有源支持评分才可点：无 starRating 的源调用会静默失败。
                                modifier = if (liveDetails != null) Modifier
                                    .clickable { showRatingDialog = true }
                                    .padding(vertical = tokens.spacing.space2)
                                else Modifier,
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
                        if (showRatingDialog) {
                            AlertDialog(
                                onDismissRequest = { showRatingDialog = false },
                                title = { Text("给本作评分") },
                                text = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space5)) {
                                        (1..5).forEach { n ->
                                            Text(
                                                text = "$n★",
                                                fontSize = tokens.type.itemTitle,
                                                fontWeight = tokens.type.weightBold,
                                                color = if (myRating >= n) StatusColors.RatingStar
                                                    else tokens.color.textTertiary,
                                                modifier = Modifier
                                                    .clickable {
                                                        viewModel.rateComic(n.toFloat())
                                                        showRatingDialog = false
                                                    }
                                                    .padding(tokens.spacing.space2),
                                            )
                                        }
                                    }
                                },
                                confirmButton = {},
                                dismissButton = {
                                    TextButton(onClick = { showRatingDialog = false }) { Text("取消") }
                                },
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
                    // 章节进度线：胶囊底边一条细线，替代「读到哪了」的第二次文字解释。
                    // 章节总数未知时不给线 —— 没有分母的百分比是假数据。
                    val totalChapters = liveDetails?.chapters?.size ?: 0
                    val readProgress = historyRecord
                        ?.takeIf { totalChapters > 0 }
                        ?.let { ((it.lastChapterIndex + 1f) / totalChapters).coerceIn(0f, 1f) }
                        ?: 0f
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = tokens.color.primary,
                        modifier = Modifier
                            .weight(1f)
                            .height(tokens.spacing.detailPrimaryButtonHeight)
                            .clickable { onTriggerRead() },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(50))
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
                            if (readProgress > 0f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .height(tokens.spacing.space1)
                                        .background(tokens.color.onPrimary.copy(alpha = 0.28f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(readProgress)
                                            .fillMaxSize()
                                            .background(tokens.color.onPrimary.copy(alpha = 0.9f))
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

            // 3. 辅助操作行：收藏 / 点赞 / 评论 / 分享 四联功能钮（MD3 下自动取色，MIUIX 下固定语义色）
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailActionButton(
                        icon = if (isFav || isNetworkFav) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        label = when {
                            isFav && isNetworkFav -> "本地+源"
                            isFav -> "已收藏"
                            isNetworkFav -> "源已藏"
                            else -> "收藏"
                        },
                        iconColor = tokens.color.actionFavorite,
                        isActive = isFav || isNetworkFav,
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
                        // 数字必须带名词：光秃秃的「7」只能靠图标颜色猜是什么计数
                        label = if (detailState.likesCount > 0) "点赞 " + detailState.likesCount else "点赞",
                        iconColor = tokens.color.actionLike,
                        isActive = detailState.isLiked,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.toggleLike()
                        }
                    )
                    val commentCount = liveDetails?.commentCount ?: detailState.comments.size
                    DetailActionButton(
                        icon = Icons.Outlined.Comment,
                        label = if (commentCount > 0) "评论 $commentCount" else "评论",
                        iconColor = tokens.color.actionComment,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showCommentSheet = true
                        }
                    )
                    DetailActionButton(
                        icon = Icons.Outlined.Share,
                        label = "分享",
                        iconColor = tokens.color.actionShare,
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
                    DetailActionButton(
                        icon = Icons.Outlined.Link,
                        label = "复制链接",
                        iconColor = tokens.color.actionShare,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val link = shareLink(liveDetails, comic)
                            if (link == null) {
                                // 如实说没有链接：把空串写进剪贴板再弹"已复制"就是假反馈。
                                Toast.makeText(
                                    context,
                                    "这个源没有提供漫画页地址，只能分享标题",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("漫画链接", link))
                                Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                            }
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
                                        // 字典 rows 覆盖 12 个命名空间（female→女性、parody→原作…），
                                        // 源自造的键（Author / Chinese Team / Work…）走源原生兜底表；
                                        // 都不在表里才原样显示。
                                        text = "${tagDict.getNamespaceName(category)}:",
                                        fontSize = tokens.type.caption,
                                        fontWeight = tokens.type.weightBold,
                                        color = tokens.color.textSecondary,
                                        // 组名列只给最小宽度且禁止折行：定宽会把长键从单词中间截断
                                        // （真机出现过「Categorie / s:」），列宽随内容增长才成词。
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .widthIn(min = tokens.spacing.detailCategoryLabelWidth)
                                            .padding(end = tokens.spacing.space2)
                                    )
                                    FlowRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3)
                                    ) {
                                        tags.forEach { tag ->
                                            DetailTagChip(
                                                // 译文只用于显示；点击仍把站点原文交给 onSearchTag。
                                                // 纯数值（JM 的 View=浏览量）不是可检索标签，点击等于
                                                // 拿"3132"去搜索 —— 保留展示，取消点击。
                                                label = tagLabel(tag),
                                                searchable = !NUMERIC_ONLY_VALUE.matches(tag),
                                                onClick = { onSearchTag(tag) },
                                                onCopy = { copyTag(tag) },
                                                onBlock = { blockTag(tag) },
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
                                DetailTagChip(
                                    label = tagLabel(tag),
                                    onClick = { onSearchTag(tag) },
                                    onCopy = { copyTag(tag) },
                                    onBlock = { blockTag(tag) },
                                )
                            }
                        }
                    }
                }
            }

            // 5. 作品简介卡片 (支持折叠展开)
            item {
                val desc = liveDetails?.comic?.description?.ifBlank { comic.description } ?: comic.description
                // 「展开」只在文字确实被截断时才挂出来：一行简介配一个展开按钮是假开关。
                // 折叠态下由 onTextLayout 判定；展开后不再回写，否则「收起」会自己消失。
                var descOverflows by remember(desc) { mutableStateOf(false) }
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
                        if (descOverflows) {
                            Text(
                                text = if (isDescExpanded) "收起 ↑" else "展开 ↓",
                                fontSize = tokens.type.caption,
                                color = tokens.color.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(tokens.spacing.space4))
                    Text(
                        text = desc.ifBlank { "暂无详细简介" },
                        fontSize = tokens.type.caption,
                        lineHeight = tokens.type.body * 1.6f,
                        color = tokens.color.textSecondary,
                        maxLines = if (isDescExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            if (!isDescExpanded && result.lineCount > 0) {
                                descOverflows = result.isLineEllipsized(result.lineCount - 1)
                            }
                        },
                        modifier = Modifier.clickable { isDescExpanded = !isDescExpanded }
                    )
                }
            }

            // 6. 官方预览图（对齐官方 comic_details_page/thumbnails.dart：
            //    网格铺排 + 分页加载 + 点任意一张从该页开读）
            val thumbnails = detailState.thumbnails.ifEmpty { liveDetails?.thumbnails.orEmpty() }
            // 实际渲染的是 previewThumbnails（挂载窗口在函数顶部算，
            // LazyColumn 的内容 lambda 不是组合上下文）。
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
                                // 波浪环低于 ~24dp 就看不出波浪了，所以从原来的 16dp 提到 loaderInline
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(tokens.spacing.loaderInline),
                                    color = tokens.color.primary,
                                    trackColor = tokens.color.surfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(tokens.spacing.space5))

                        // 列数按卡片实测宽推（宽屏加列）；末行不足补空位，避免被拉伸变形
                        Column(
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                            modifier = Modifier.then(previewWidthModifier),
                        ) {
                            previewRows.forEachIndexed { rowIdx, rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
                                    rowItems.forEachIndexed { colIdx, thumbUrl ->
                                        val pageIndex = rowIdx * previewColumns + colIdx
                                        val targetChId = detailState.previewChapterId.ifBlank {
                                            liveDetails?.chapters?.firstOrNull()?.id ?: "0"
                                        }
                                        val targetChTitle = liveDetails?.chapters?.firstOrNull { it.id == targetChId }?.title
                                            ?: comic.title
                                        // 加载/失败双标志，判据与 VeneraCover 一致
                                        var thumbLoaded by remember(thumbUrl) { mutableStateOf(false) }
                                        var thumbFailed by remember(thumbUrl) { mutableStateOf(false) }
                                        // 真图淡入：0 → 1，同时把骨架微光反向淡出
                                        val thumbAlpha by animateFloatAsState(
                                            targetValue = if (thumbLoaded) 1f else 0f,
                                            animationSpec = tween(tokens.motion.imageFadeInMillis),
                                            label = "preview-thumb-fade",
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(0.7f)
                                                .clip(RoundedCornerShape(tokens.shape.small))
                                                // 占位底色与 VeneraCover 同源：图未到时不留「洞」
                                                .background(
                                                    tokens.color.surfaceVariant.copy(
                                                        alpha = tokens.current.placeholderAlpha
                                                    )
                                                )
                                                .clickable { launchChapter(targetChId, targetChTitle, 0, pageIndex) }
                                        ) {
                                            // 骨架微光：与真图**反向淡出**做交叉过渡，
                                            // 否则骨架会突然消失、露出灰底闪一下再进图。
                                            // alpha 到 1 后整层卸载，不让 12 个无限动画一直跑。
                                            // 加载失败则直接不画 —— 骨架在死图上继续呼吸 = 假装还在加载。
                                            if (!thumbFailed && thumbAlpha < 1f) {
                                                VeneraShimmer(
                                                    Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer { alpha = 1f - thumbAlpha }
                                                )
                                            }
                                            // 真图：加载完成后 200ms 淡入
                                            // 预览这批 URL 从没走 comic.onImageLoad，只拿得到「URL 推导」的块数，
                                            // key 带上块数，避免它和源脚本那份还原结果共用同一条内存缓存。
                                            val previewCtx = LocalContext.current
                                            // 源 comic.onThumbnailLoad 声明的地址优先（EH 会换成镜像域，
                                            // 多数源是原址返回）。cacheKey **仍按原 url** 算 —— 对齐 master
                                            // images.dart:15-24 的口径，否则换域前后会各存一份缓存。
                                            val previewUrl =
                                                detailState.thumbnailConfigs[thumbUrl]?.url ?: thumbUrl
                                            val previewModel = remember(thumbUrl, previewUrl) {
                                                val previewKey = ImagePipelinePolicy.cacheKeyFor(thumbUrl)
                                                ImageRequest.Builder(previewCtx)
                                                    .data(previewUrl)
                                                    .memoryCacheKey(previewKey)
                                                    .diskCacheKey(previewKey)
                                                    .build()
                                            }
                                            AsyncImage(
                                                model = previewModel,
                                                contentDescription = "第 " + (pageIndex + 1) + " 页",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .graphicsLayer { alpha = thumbAlpha },
                                                contentScale = ContentScale.Crop,
                                                onSuccess = { thumbLoaded = true },
                                                onError = { thumbFailed = true },
                                            )
                                            // 页码角标：预览页常是白页/纯色页，没有角标时读起来像加载失败，
                                            // 而且「点任意一张从该页开读」这个能力原本完全不可见。
                                            Text(
                                                text = (pageIndex + 1).toString(),
                                                fontSize = tokens.type.badge,
                                                fontWeight = tokens.type.weightBold,
                                                color = StatusColors.OnBadgeSurface,
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(tokens.spacing.badgeInset)
                                                    .background(
                                                        StatusColors.BadgeSurface,
                                                        RoundedCornerShape(tokens.shape.extraSmall)
                                                    )
                                                    .padding(
                                                        horizontal = tokens.spacing.badgeHorizontalPadding,
                                                        vertical = tokens.spacing.badgeVerticalPadding
                                                    )
                                            )
                                        }
                                    }
                                    repeat(previewColumns - rowItems.size) {
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

                        // 折叠 / 展开预览图。三种「还有更多」必须都算上，缺一个就点不动：
                        //  - thumbnailsCollapsed：本地已有 > PREVIEW_LIMIT 张被折叠（nhentai 全量返回）
                        //  - hasMoreThumbnails：源还有未拉取的分页（EH 的 loadThumbnails 分页）
                        //  - 已展开但本轮只挂载了一部分（previewMount 翻倍放出的窗口还没吃完）
                        val collapsed = !detailState.thumbnailsExpanded && thumbnails.size > PREVIEW_LIMIT
                        val canLoadMore = detailState.hasMoreThumbnails
                        val moreMounted = detailState.thumbnailsExpanded &&
                                previewThumbnails.size < thumbnails.size
                        if (collapsed || canLoadMore || moreMounted) {
                            Spacer(modifier = Modifier.height(tokens.spacing.space5))
                            val hidden = (thumbnails.size - PREVIEW_LIMIT).coerceAtLeast(0)
                            // 折叠的本地图与「源还有下一页」是两件事，文案要分别说清，
                            // 否则用户点开后发现又多出一批会以为界面在骗人。
                            val label = when {
                                moreMounted -> "查看更多预览（已显示 ${previewThumbnails.size} / ${thumbnails.size} 张）"
                                collapsed && canLoadMore -> "查看更多预览（已显示 $PREVIEW_LIMIT / ${thumbnails.size}+ 张）"
                                collapsed -> "查看更多预览（还有 $hidden 张）"
                                canLoadMore -> "加载更多预览"
                                else -> "查看更多预览"
                            }
                            Surface(
                                shape = RoundedCornerShape(tokens.shape.small),
                                color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        when {
                                            // 已展开、只是窗口没放完：本地翻倍，不发请求。
                                            moreMounted ->
                                                previewMount = (previewMount * 2).coerceAtMost(thumbnails.size)
                                            collapsed -> {
                                                // 首次展开：窗口放到两批，同时让 VM 补该源的分页
                                                //（expandThumbnails 内部只在该源确实还有下一页时才拉）。
                                                previewMount = PREVIEW_LIMIT * 2
                                                viewModel.expandThumbnails()
                                            }
                                            // 已展开且本地窗口放完，源还有下一页。这里**不能**再走
                                            // expandThumbnails() —— 它在已展开时直接 return，
                                            // 按钮会变成按了没反应的死控件（EH 第 3 页永远拿不到）。
                                            canLoadMore -> viewModel.loadThumbnails(loadMore = true)
                                            else -> viewModel.expandThumbnails()
                                        }
                                    },
                            ) {
                                Text(
                                    text = label,
                                    fontSize = tokens.type.caption,
                                    color = tokens.color.textSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = tokens.spacing.space5)
                                )
                            }
                        }

                        // 已展开且图很多时给一个收起入口，否则展开后详情页会被撑得过长
                        if (detailState.thumbnailsExpanded && thumbnails.size > PREVIEW_LIMIT) {
                            Spacer(modifier = Modifier.height(tokens.spacing.space4))
                            Surface(
                                shape = RoundedCornerShape(tokens.shape.small),
                                color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // 收起要把挂载窗口一起收回折叠量，否则下次展开
                                        // 直接落回上一轮的大窗口，等于没收起。
                                        previewMount = PREVIEW_LIMIT
                                        viewModel.collapseThumbnails()
                                    },
                            ) {
                                Text(
                                    text = "收起预览",
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
                // 章节胶囊一次性全组合出来会拖垮详情页（当初就是为此截断的），
                // 但截断必须留出口 —— 否则 >80 章的本子后面的章节**静默不可达**。
                var showAllChapters by rememberSaveable { mutableStateOf(false) }

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
                        // 详情加载中：真·不定进度指示器 + 文案。
                        // 原先复用 VeneraEmptyView，它顶着一枚静态 Inbox 图标 —— 读起来是
                        // 「这里没有内容」而不是「还在拉」。改用 M3 Expressive 的波浪形圆环
                        // （material3 1.5.0-alpha22 已解析到；Miuix 0.9.4-rc01 无此组件）。
                        liveDetails == null && detailState.isLoading -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = tokens.spacing.space9),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(tokens.spacing.space11),
                                color = tokens.color.primary,
                                trackColor = tokens.color.surfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(tokens.spacing.space5))
                            Text(
                                text = "章节信息加载中…",
                                fontSize = tokens.type.caption,
                                color = tokens.color.textSecondary,
                            )
                        }
                        // 详情加载失败：标准空态组件 + 重试
                        liveDetails == null && detailState.error != null -> VeneraEmptyView(
                            title = "章节信息加载失败",
                            message = detailState.error ?: "",
                            actionText = "重试",
                            onAction = { viewModel.load(comic) }
                        )
                        hasRealChapters -> {
                            val list = if (isReversed) currentChapters.reversed() else currentChapters
                            val visibleChapters =
                                if (showAllChapters) list else list.take(CHAPTER_CHIP_LIMIT)
                            // 章节网格：VeneraCard 胶囊 + 已读/未读对比度走 Token
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4)
                            ) {
                                visibleChapters.forEachIndexed { idx, ch ->
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
                            // 出口文案直说还剩多少，别只写「显示全部」让人猜是不是被裁了。
                            if (!showAllChapters && list.size > CHAPTER_CHIP_LIMIT) {
                                Text(
                                    text = "显示全部 ${list.size} 章（已列出 $CHAPTER_CHIP_LIMIT 章）",
                                    fontSize = tokens.type.caption,
                                    fontWeight = tokens.type.weightMedium,
                                    color = tokens.color.primary,
                                    modifier = Modifier
                                        .padding(top = tokens.spacing.space4)
                                        .clickable { showAllChapters = true },
                                )
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
                                val totalPages = liveDetails.maxPage
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
                                RichCommentContent(
                                    content = c.content,
                                    modifier = Modifier.padding(vertical = 2.dp),
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

        // ── 2. 详情页专属顶栏：悬浮在 LazyColumn 之上 ──
        val collapsed by remember(listState) {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40 }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) {
            DetailTopBarBackdrop(
                visible = { collapsed },
                backdrop = detailBackdrop,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(tokens.spacing.detailTopBarHeight)
                    // 与 LazyColumn 的 contentPadding 同值：悬浮钮此前用 4dp，比封面/卡片左边缘
                    // 外凸 10dp，读起来像贴屏幕边（真机反馈）。
                    .padding(horizontal = tokens.spacing.rowHorizontal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 返回：顶置时次级表面胶囊底，折叠后转为顶栏纯图标（底色透明）
                DetailOverlayIconButton(
                    collapsed = collapsed,
                    onClick = onBack,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
                // 下滑后标题居中淡入（顶置时标题已在封面右侧展示，不重复）
                AnimatedVisibility(
                    visible = collapsed,
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!collapsed) {
                    Spacer(modifier = Modifier.weight(1f))
                }
                DetailOverlayIconButton(
                    collapsed = collapsed,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText(liveDetails, comic))
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "分享漫画"))
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "分享",
                        tint = tokens.color.textPrimary,
                    )
                }
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
                            RichCommentContent(content = c.content)
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
            networkFavHint = isNetworkFav,
            onDismiss = { viewModel.closeFavoritePanel() },
            onToggleLocal = { folder -> viewModel.toggleLocalFavorite(comic, folder) },
            onCreateFolder = { name, onErr -> viewModel.createLocalFolder(name, onErr) },
            onToggleNetwork = { folderId, isAdded -> viewModel.toggleNetworkFavorite(comic, folderId, isAdded) },
        )
    }

    // 批量离线下载对话框 (S6)
    if (showDownloadDialog) {
        val allChs = downloadChapters(liveDetails, detailState.selectedGroupIndex)
        val downloadManager = com.venera.compose.download.DownloadManager.getInstance(context)
        val detailSourceKey = viewModel.currentSourceKey()
        val detailComicId = liveDetails?.comic?.id ?: comic.id
        val isGallery = liveDetails != null && liveDetails.chapters.isEmpty() &&
            liveDetails.chapterGroups.all { it.chapters.isEmpty() }
        // 默认仅选中「尚未下载」的章节，避免重复下载已离线内容
        val undownloadedIds = allChs
            .filterNot { downloadManager.isChapterDownloaded(detailSourceKey, detailComicId, it.id) }
            .map { it.id }
            .toSet()
        var selectedIds by remember(allChs) { mutableStateOf(undownloadedIds) }

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
                        selectedIds = if (selectedIds.size == undownloadedIds.size) emptySet() else undownloadedIds
                    }) {
                        Text(
                            if (undownloadedIds.isNotEmpty() && selectedIds.size == undownloadedIds.size) "全不选" else "全选未下载",
                            fontSize = 12.sp
                        )
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
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                // 已下载章节：绿色「已下载」徽章（避免重复下载）
                                if (downloadManager.isChapterDownloaded(detailSourceKey, detailComicId, ch.id)) {
                                    Surface(
                                        shape = RoundedCornerShape(tokens.shape.extraSmall),
                                        color = StatusColors.Healthy.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "已下载",
                                            color = StatusColors.Healthy,
                                            fontSize = tokens.type.badge,
                                            modifier = Modifier.padding(horizontal = tokens.spacing.badgeHorizontalPadding, vertical = tokens.spacing.badgeVerticalPadding)
                                        )
                                    }
                                }
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

/**
 * 纯数值标签（浏览量/点赞数这类计数）—— 它们不是可检索的标签词，
 * 点击只会把数字当关键词发给源。刻意只认「数字 + 千分位分隔符」，
 * 避免把 `24小时`、`801` 这类真实标签误判成计数。
 */
private val NUMERIC_ONLY_VALUE = Regex("^[0-9][0-9.,\\s]*$")

/**
 * 详情页标签药丸：点击 = 直达该标签搜索；长按 = 弹出「复制 / 屏蔽」。
 *
 * 菜单锚点必须逐项各挂一个 —— 标签是 FlowRow 排布，共用锚会让菜单永远从
 * 第一个药丸的位置弹出来。按压反馈仍由 [VeneraTagChip] 统一持有，这里只给回调。
 */
@Composable
private fun DetailTagChip(
    label: String,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onBlock: () -> Unit,
    searchable: Boolean = true,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        VeneraTagChip(
            text = label,
            onClick = if (searchable) onClick else null,
            onLongClick = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                menuExpanded = true
            },
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = {
                    Text("复制「$label」", fontSize = tokens.type.caption, color = tokens.color.textPrimary)
                },
                onClick = {
                    menuExpanded = false
                    onCopy()
                },
            )
            DropdownMenuItem(
                text = {
                    Text("屏蔽该标签", fontSize = tokens.type.caption, color = StatusColors.Failing)
                },
                onClick = {
                    menuExpanded = false
                    onBlock()
                },
            )
        }
    }
}

/**
 * 分享用的原站链接，两层取值（口径与 master 的 `comic_details_page/actions.dart:89-110` 同族）：
 * ① 详情里源自己回的 `url`；② 缺了才按「源 + 漫画 id」查 [ComicUrlTable] 的分享模板。
 *
 * 两层都没有就返回 null —— 只分享标题。**不拿源根地址或站内搜索页凑一个"看起来像链接"的东西**，
 * 那是假链接：点进去不是这本。
 */
private fun shareLink(
    details: ComicDetails?,
    comic: ComicItem,
): String? =
    details?.url?.takeIf { it.isNotBlank() }
        ?: ComicUrlTable.shareUrlFor(comic.sourceName, comic.id)

/** 分享文案（顶栏与辅助行共用，消除重复实现）。无链接时不留尾随空行。 */
private fun shareText(
    details: ComicDetails?,
    comic: ComicItem,
): String = buildString {
    append("【").append(details?.comic?.title ?: comic.title).append("】")
    append('\n').append("作者：").append(details?.author ?: comic.author)
    shareLink(details, comic)?.let { append('\n').append(it) }
}

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
    /** 源账号上是否已收藏（详情 isFavorite 判定，摘要兜底用）。 */
    networkFavHint: Boolean,
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
                    // 详情 isFavorite 与面板查询结果取或：任一来源确认为真即显示已收藏，
                    // EH 的二次查询失败也不会把真实状态打回「尚未收藏」。
                    val added = networkFavHint ||
                        (if (state.networkMultiFolder) state.networkAdded.isNotEmpty() else state.networkSingleAdded)
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

/* ------------------------------------------------------------------ *
 * 详情页顶栏私有组件（悬浮胶囊 + 动态高斯模糊背板）
 * ------------------------------------------------------------------ */

/**
 * 详情页顶栏图标按钮：
 * - 顶置展开态（悬浮在页面背景上）：次级表面胶囊底（与「离线下载」同一语义）+ 主文字色图标。
 *   原先是 35% 半透黑底配白图标 —— 那是为「压在封面上」设计的，但按钮实际悬浮在页面背景上，
 *   浅色主题下就成了一块发灰的脏色，故改走主题 Token；
 * - 下滑折叠态（在顶栏上）：底色透明，对齐系统标准 TopAppBar 图标，绝无突兀黑圈。
 */
@Composable
private fun DetailOverlayIconButton(
    collapsed: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .size(tokens.spacing.iconButtonSize)
            .clip(CircleShape)
            .background(
                if (collapsed) Color.Transparent
                else tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 详情页顶栏渐变高斯模糊背板（对齐 pixez-miuix 规范）：
 * - 滑动后由透明平滑过渡到全宽 HyperOS 渐变式高斯模糊；
 * - 底色 30% alpha 通透微调，彻底消除纯白死板色块。
 */
@Composable
private fun DetailTopBarBackdrop(
    visible: () -> Boolean,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val surfaceColor = MiuixTheme.colorScheme.surface
    val textPrimary = tokens.color.textPrimary
    val target = if (visible()) 1f else 0f
    val fraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "DetailTopBarBlur",
    )
    if (fraction <= 0f) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = fraction
            }
            .then(
                if (backdrop != null && isRuntimeShaderSupported()) {
                    Modifier.progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                        blurRadius = 10f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                // 与 VeneraTopAppBar 同步：轻补底保文字对比，玻璃色交给采样层氛围光。
                                BlendColorEntry(color = surfaceColor.copy(alpha = 0.16f)),
                            ),
                        ),
                    )
                } else {
                    Modifier.background(surfaceColor.copy(alpha = 0.85f))
                }
            )
            .drawBehind {
                drawRect(
                    color = textPrimary.copy(alpha = 0.08f * fraction),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 0.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx()),
                )
            },
    )
}

/**
 * 「收藏那一页」在详情目录里的落点。
 *
 * 用 sealed 而不是可空三元组：`null` 只能表达"没找到"，分不清"本源根本没有章节目录"
 * （EH / nhentai 这类图库，整本就是一章，照旧该开阅读器）。
 */
private sealed interface ReadChapterHit {
    /** groupIndex 为 null 表示该源不分组，章节在平铺的 details.chapters 里。 */
    data class Found(val groupIndex: Int?, val indexInGroup: Int, val chapter: ComicChapter) : ReadChapterHit

    /** 目录为空：图库类源，整本即一章。 */
    data object WholeComic : ReadChapterHit

    /** 有目录但已经没有这一章（源端改名或下架）。 */
    data object NotFound : ReadChapterHit
}

/**
 * 按**标题**找回收藏时那一页所在的章节。
 *
 * 跨所有分组找，不看当前选中分组：jm 这类源一本书有几十个分组，只扫首组会稳定找不回来。
 * 只做全等比较 —— 章节标题当年就是从这份目录里取的原值，模糊匹配反而会挑错章。
 */
private fun findReadTargetChapter(details: ComicDetails, chapterTitle: String): ReadChapterHit {
    val groups = details.chapterGroups
    if (groups.isNotEmpty()) {
        groups.forEachIndexed { gi, group ->
            group.chapters.forEachIndexed { ci, ch ->
                if (ch.title == chapterTitle) return ReadChapterHit.Found(gi, ci, ch)
            }
        }
    } else if (details.chapters.isNotEmpty()) {
        details.chapters.forEachIndexed { ci, ch ->
            if (ch.title == chapterTitle) return ReadChapterHit.Found(null, ci, ch)
        }
    } else {
        return ReadChapterHit.WholeComic
    }
    return ReadChapterHit.NotFound
}
