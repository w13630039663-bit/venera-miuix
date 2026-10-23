/**
 * 主页 —— 重新排版（对齐 _design/home-redesign.html 杂志化设计）：
 * 阅读统计 / 最近在读错落流 / 推荐漫画 (正常/R18) / 画廊每日热门 (纯图海报) / Pixiv关注画师动态 / 本地漫画 (书脊风格) / 漫画源。
 */
package com.venera.compose.feature

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.venera.compose.GalleryPostActivity
import com.venera.compose.openGalleryPost
import com.venera.compose.openGalleryPostSafe
import android.app.Activity
import com.venera.compose.components.LocalWindowWidthDp
import com.venera.compose.components.comicListColumnCount
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.recommendStripColumns
import com.venera.compose.components.rememberContentWidth
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraIconButton
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.components.wideScreenLayoutMode
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.feature.pet.PetMascot
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.ui.openPixivArtistPage
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.Comic
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import java.util.Calendar
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidHomeScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenLocal: () -> Unit = {},
    onOpenSourceManage: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
    bottomContentPadding: androidx.compose.ui.unit.Dp = VeneraTokens.spacing.space4,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val ux = com.venera.compose.components.LocalUxPlatformServices.current
    val viewModel: HomeViewModel = viewModel()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val extra by viewModel.uiStateExtra.collectAsStateWithLifecycle()
    val recommend by viewModel.recommend.collectAsStateWithLifecycle()
    val recommendMode by viewModel.recommendMode.collectAsStateWithLifecycle()
    val dailyPosts by viewModel.dailyPosts.collectAsStateWithLifecycle()
    val pixivArtists by viewModel.pixivArtists.collectAsStateWithLifecycle()
    val historyList = ui.history
    val sourceManager = remember { ComicSourceManager.getInstance(context) }

    LaunchedEffect(Unit) {
        runCatching { sourceManager.checkUpdates() }
    }
    val registeredSources by sourceManager.sourcesFlow.collectAsStateWithLifecycle()
    val availableUpdates by sourceManager.availableUpdates.collectAsStateWithLifecycle()
    val updateCount = availableUpdates.size
    val installedSources by sourceManager.installedMeta.collectAsStateWithLifecycle()
    val sources = remember(registeredSources, installedSources) { sourceManager.searchTargets() }

    LaunchedEffect(Unit) {
        viewModel.refreshExtras()
        viewModel.loadGalleryDailyAndArtists()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                viewModel.autoRefreshRecommend()
                viewModel.loadGalleryDailyAndArtists()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val appearancePrefs = remember(context) { BusinessPorts.of(context).appearancePrefs }
    val petStickerOn by appearancePrefs.homePetSticker.collectAsState()

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when (hour) {
            in 5..8 -> "早安，又是元气满满的一天"
            in 9..11 -> "上午好，读一本喜欢的漫画吧"
            in 12..13 -> "午后小憩，随心翻翻"
            in 14..17 -> "下午好，享受阅读时光"
            in 18..22 -> "夜幕降临，沉浸漫画世界"
            else -> "夜深了，还在看啊"
        }
    }

    val contentMaxWidth = contentMaxWidthFor(wideScreenLayoutMode(LocalWindowWidthDp))

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(contentMaxWidth?.let { Modifier.widthIn(max = it) } ?: Modifier)
                .align(Alignment.TopCenter)
                .nestedScroll(topBarBehavior.nestedScrollConnection)
                .blurBackdropSource(topBarBackdrop),
            contentPadding = PaddingValues(
                start = tokens.spacing.rowHorizontal,
                end = tokens.spacing.rowHorizontal,
                // ── 顶部内边距**不吃** `topBarFloor` 那枚 104dp 的地板 ──
                //
                // 2026-10-19 用户原话：「首页这边的高度边距怎么那么大」。那枚地板是给
                // **顶栏带大标题**的页面量的：104 = 折叠行 52 + 大标题行 52。而首页的顶栏是
                // `largeTitle = ""`（大标题由下面 hero 自绘，那颗 30sp 的「首页」就是它），
                // 于是那 52dp 大标题行在屏上**什么都没画** —— 104dp 里有一半是纯空白，
                // 真机读数就是「标题离状态栏 106dp」，一屏顶上一大块空。
                //
                // 改成「折叠行 + 4dp」：标题顶落在胶囊行（中心 inset + 26dp、视觉 40dp 高）
                // 下面 12dp 处，与顶栏折叠后那颗小标题同一条基准线。
                //
                // ⚠️ 只改这一页的**内边距**，不动 `topBarFloor` 那枚全站尺子 —— 它被
                // `TopBarFloorGuardTest` 钉着，另外 19 屏（下载/收藏/历史/探索/设置…）都在吃它，
                // 那些页的顶栏确实带大标题，砍了它们的地板就是砍它们的标题。
                top = statusBarTop + tokens.spacing.topBarCollapsedHeight + tokens.spacing.space4,
                bottom = bottomContentPadding,
            ),
            // 分区之间 12dp（原 16dp）：与顶部收紧同一个诉求 —— 一屏里能多看见一块内容。
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
        ) {
            // 1. 顶部问候与标题区
            item(key = "hero-header") {
                // hero 自己不再垫上边距（那一截已经由 contentPadding 精确算过）：
                // 这里再加一份就是「标题离顶还有一大截」的第二只手。
                Column(modifier = Modifier.padding(start = tokens.spacing.space1, bottom = tokens.spacing.space2)) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "首页",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = tokens.color.textPrimary,
                            lineHeight = 34.sp,
                        )
                        if (ui.streakDays > 0) {
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = Color(0xFFE8A45C).copy(alpha = HomeStreakTintAlpha),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = "🔥", fontSize = 10.sp)
                                    Text(
                                        text = "${ui.streakDays} 天",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE8A45C)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = greeting,
                        fontSize = 13.5.sp,
                        color = tokens.color.textSecondary,
                        letterSpacing = 0.02.sp
                    )
                }
            }

            // 2. 阅读统计卡片
            if (ui.todayPages > 0 || ui.weekPages > 0) {
                item(key = "stats") {
                    VeneraCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenStats() },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = tokens.spacing.rowVertical),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatCell(value = ui.todayPages, label = "今日页数", modifier = Modifier.weight(1f))
                            StatDivider()
                            StatCell(value = ui.weekPages, label = "本周累计", modifier = Modifier.weight(1f))
                            StatDivider()
                            StatCell(value = ui.streakDays, label = "连续天数", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // 3. 最近在读（错落卡片流）
            if (historyList.isNotEmpty()) {
                item(key = "recent-history") {
                    Column {
                        HomeTrackHeader(
                            title = "最近在读",
                            enTag = "",
                            accentColor = tokens.color.primary,
                            trailing = {
                                Text(
                                    text = "全部历史 ›",
                                    fontSize = 11.5.sp,
                                    color = tokens.color.textSecondary,
                                    modifier = Modifier.clickable { onOpenHistory() }
                                )
                            }
                        )
                        Spacer(Modifier.height(8.dp))

                        val primaryRecord = historyList.firstOrNull()
                        val secondaryRecords = remember(historyList) { historyList.drop(1).take(5) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            primaryRecord?.let { record ->
                                val maskState = ux.coverMaskStateOf(
                                    sourceKey = record.sourceName,
                                    title = record.title,
                                    author = record.author,
                                    comicId = record.comicId,
                                )
                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(112.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onSelect(record.toComicItem()) }
                                ) {
                                    VeneraCover(
                                        url = record.coverUrl,
                                        contentDescription = record.title,
                                        mask = if (maskState == "VISIBLE") VeneraCoverMask.Visible else VeneraCoverMask.Masked,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0x990A080A),
                                                        Color(0xF00A080A)
                                                    )
                                                )
                                            )
                                    )
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(horizontal = 9.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = record.title,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${record.lastChapterTitle} · P.${record.lastPageIndex + 1}",
                                            fontSize = 9.5.sp,
                                            color = Color(0xCCFFFFFF),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .padding(top = 4.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(Color(0x44FFFFFF))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.65f)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(1.5.dp))
                                                    .background(tokens.color.primary)
                                            )
                                        }
                                    }
                                }
                            }

                            secondaryRecords.forEach { record ->
                                val maskState = ux.coverMaskStateOf(
                                    sourceKey = record.sourceName,
                                    title = record.title,
                                    author = record.author,
                                    comicId = record.comicId,
                                )
                                Box(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(96.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSelect(record.toComicItem()) }
                                ) {
                                    VeneraCover(
                                        url = record.coverUrl,
                                        contentDescription = record.title,
                                        mask = if (maskState == "VISIBLE") VeneraCoverMask.Visible else VeneraCoverMask.Masked,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0x44000000),
                                                        Color(0xD00A080A)
                                                    )
                                                )
                                            )
                                    )
                                    Text(
                                        text = record.lastChapterTitle.ifBlank { record.title },
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(horizontal = 7.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. 推荐漫画（可能你感兴趣）
            if (recommend.loading || recommend.comics.isNotEmpty() || recommend.note != null) {
                item(key = "recommend") {
                    Column {
                        HomeTrackHeader(
                            title = "推荐漫画",
                            enTag = "FOR YOU",
                            accentColor = tokens.color.primary,
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    RecommendModeSelector(
                                        selected = recommendMode,
                                        onSelect = { viewModel.setRecommendMode(it) },
                                    )
                                    VeneraIconButton(
                                        onClick = { viewModel.loadRecommend(force = true) },
                                        enabled = !recommend.loading,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Refresh,
                                            contentDescription = "换一批",
                                            tint = tokens.color.textSecondary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        )
                        if (recommend.tags.isNotEmpty()) {
                            Text(
                                text = "按你最近 30 天读得最多的题材：" +
                                    recommend.tags.joinToString(" · ") { it.display },
                                fontSize = 10.5.sp,
                                color = tokens.color.textTertiary,
                                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                            )
                        }
                        if (recommend.sourcesLabel.isNotEmpty()) {
                            Text(
                                text = "来自 ${recommend.sourcesLabel}",
                                fontSize = 10.sp,
                                color = tokens.color.textTertiary.copy(alpha = 0.8f),
                                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))

                        val (blockWidth, blockWidthModifier) = rememberContentWidth()
                        val gridColumns = comicListColumnCount("brief", blockWidth)
                        val stripColumns = recommendStripColumns(
                            availableWidth = blockWidth,
                            cardWidth = tokens.spacing.historyCardWidth,
                            gap = tokens.spacing.gridGap,
                        )
                        when {
                            recommend.loading -> RecommendSkeleton(columns = stripColumns)
                            recommend.comics.isNotEmpty() -> {
                                val openComic: (Comic) -> Unit = { comic ->
                                    onSelect(
                                        ComicItem(
                                            id = comic.id,
                                            title = comic.title,
                                            author = comic.subTitle,
                                            coverUrl = comic.cover,
                                            tags = comic.tags,
                                            description = comic.description,
                                            sourceName = comic.sourceKey,
                                            likesCount = comic.likesCount,
                                        )
                                    )
                                }
                                if (gridColumns >= 3) {
                                    RecommendGridRow(
                                        ux = ux,
                                        comics = recommend.comics.take(stripColumns),
                                        columns = stripColumns,
                                        onOpen = openComic,
                                        modifier = blockWidthModifier,
                                    )
                                } else {
                                    RecommendCarousel(
                                        ux = ux,
                                        comics = recommend.comics,
                                        batch = recommend.batch,
                                        onOpen = openComic,
                                    )
                                }
                            }
                            else -> Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                            ) {
                                Text(
                                    text = recommend.note.orEmpty(),
                                    fontSize = tokens.type.caption,
                                    color = tokens.color.textSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (recommend.canRetry) {
                                    Text(
                                        text = "再来一次",
                                        fontSize = tokens.type.caption,
                                        fontWeight = tokens.type.weightSemibold,
                                        color = tokens.color.primary,
                                        modifier = Modifier.clickable {
                                            viewModel.loadRecommend(force = true)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. 画廊每日热门（纯图海报卡片流）
            if (dailyPosts.isNotEmpty()) {
                item(key = "gallery-daily") {
                    Column {
                        HomeTrackHeader(
                            title = "画廊",
                            enTag = "GALLERY",
                            accentColor = Color(0xFF93D2C8),
                            trailing = {
                                Text(
                                    text = "每日热门 ›",
                                    fontSize = 11.5.sp,
                                    color = tokens.color.textSecondary,
                                    modifier = Modifier.clickable { onOpenGallery() }
                                )
                            }
                        )
                        Spacer(Modifier.height(tokens.spacing.space4))

                        val (blockWidth, _) = rememberContentWidth()
                        // 手机 2 列不变；宽窗列数**由目标宽反推**（用户 2026-10-23 大屏截图：
                        // 「主页画廊这些卡片又太大了」——前一批的固定 3 列在 600dp+ 宽窗上每张
                        // 近半屏宽）。1280dp 宽的屏一排能摆 6 张，每张回到 ~170dp 的阅读尺寸；
                        // 列数夹在 [3, 6]，免得极宽屏上碎成一把瓜子。
                        val isWide = blockWidth >= 600.dp
                        val gridCols = if (!isWide) 2 else (
                            (blockWidth / tokens.spacing.homeGalleryCardTargetWidth).toInt()
                                .coerceIn(3, 6)
                        )
                        val displayPosts = remember(dailyPosts, gridCols) {
                            dailyPosts.take(gridCols * 2)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
                            displayPosts.chunked(gridCols).forEach { rowPosts ->
                                // ── 一行一个**共同高宽比**，不再给卡钉死高度 ──
                                //
                                // 用户原话：「不应该有裁剪和黑边」。旧写法是 `.height(130.dp)` +
                                // `ContentScale.Fit`：图的比例与 130dp 那一格对不上时，剩下的地方
                                // 就露出一条底色（读起来就是黑边）；若改成 `Crop` 则变成裁头裁脚。
                                // 两条都不对，因为错的不是 `ContentScale`，是**格子比例**。
                                //
                                // 现在格子比例取这一行各图 `cardRatio` 的均值（真宽高比，站方数据）：
                                // 格子与图同比例 ⇒ `Fit` 恰好铺满，既不裁也不留边。行内取同一个值
                                // 是为了行底对齐（逐卡各算会把网格排成参差不齐的锯齿）。
                                //
                                // 夹在 [0.55, 1.5] 是因为站方数据会飘：`0f`（未报宽高）与超长条
                                // （横幅 / 长图）都会把网格撞开；夹完之后真有差的那一点由底下那层
                                // **同色底**兑住（surfaceVariant，不是黑色）。
                                //
                                // ⚠️ `coverAspectRatio` 必须在 `remember` **外面**取：`tokens.spacing`
                                // 是一枚 @Composable 取值器（它跟着风格档走），在 remember 的 lambda
                                // 里读就是“@Composable 调用不在 @Composable 上下文”。
                                val fallbackRatio = tokens.spacing.coverAspectRatio
                                // ⚠️ token 取值必须在 remember **外**：`tokens.spacing` 是
                                // @Composable 取值器（跟着风格档走），在 lambda 里读就是
                                // “@Composable 调用不在 @Composable 上下文”。
                                val wideCardMaxHeight = tokens.spacing.homeGalleryCardMaxHeight
                                val rowRatio = remember(rowPosts, fallbackRatio, isWide, wideCardMaxHeight) {
                                    val ratios = rowPosts.map { post ->
                                        post.cardRatio.takeIf { it > 0f } ?: fallbackRatio
                                    }
                                    // 宽窗档把竖图下限抬到 0.9：列数反推后每张卡宽已回阅读尺寸，
                                    // 若再放竖图原图比例（0.67 那一档），行高就冲到近半屏 ——
                                    // 那是大屏截图里「卡太大」的另一半成因。抬档后竖图在宽窗上
                                    // 读作「方一点的海报」，差额由底下同色底兑住。
                                    (ratios.average().toFloat())
                                        .coerceIn(
                                            if (isWide) WideMinHomeCardRatio else MinHomeCardRatio,
                                            MaxHomeCardRatio,
                                        )
                                        // 宽窗档的行高保险：超出上限就把比例放宽（变扁一点），
                                        // 高度回落 —— 双保险，列数反推算出的卡宽理论上已不超。
                                        .let { ratio ->
                                            val maxRatio = blockWidth / gridCols / wideCardMaxHeight
                                            if (isWide) ratio.coerceAtLeast(maxRatio) else ratio
                                        }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4)
                                ) {
                                    rowPosts.forEach { post ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(rowRatio)
                                                .clip(RoundedCornerShape(tokens.shape.card))
                                                .background(
                                                    tokens.color.surfaceVariant.copy(
                                                        alpha = tokens.current.placeholderAlpha
                                                    )
                                                )
                                                .clickable {
                                                    context.openGalleryPostSafe(post.site, post.id)
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    // 中档优先，缺档退回大图 —— 与格卡那面墙同一口径。
                                                    .data(
                                                        post.previewUrl.ifBlank { post.largeUrl }
                                                    )
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    repeat(gridCols - rowPosts.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. 画师头像栏（仅在登录 Pixiv 后获取并展示）
            if (pixivArtists.isNotEmpty()) {
                item(key = "pixiv-artists-follow") {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "关注画师新作动态",
                                fontSize = 11.5.sp,
                                color = tokens.color.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            pixivArtists.forEach { artist ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(52.dp)
                                        .clickable {
                                            context.openPixivArtistPage(artist.id, artist.name)
                                        }
                                ) {
                                    Box(modifier = Modifier.size(48.dp)) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(artist.avatarUrl.ifBlank { null })
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = artist.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .align(Alignment.TopEnd)
                                                .clip(CircleShape)
                                                .background(Color(0xFF93D2C8))
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = artist.name,
                                        fontSize = 10.sp,
                                        color = tokens.color.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. 本地漫画
            item(key = "local-shelf") {
                VeneraCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenLocal() },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeBookSpines()
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "本地漫画",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.color.textPrimary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = when {
                                    extra.localComicCount < 0 -> "正在扫描本地书架..."
                                    else -> "共 ${extra.localComicCount} 部本地作品" +
                                        if (extra.downloadingCount > 0) " · ${extra.downloadingCount} 个下载任务" else ""
                                },
                                fontSize = 11.sp,
                                color = tokens.color.textSecondary,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = tokens.color.primary.copy(alpha = 0.12f),
                            modifier = Modifier.clickable { onOpenLocal() }
                        ) {
                            Text(
                                text = "管理 ›",
                                fontSize = 11.5.sp,
                                color = tokens.color.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // 8. 漫画源
            if (sources.isNotEmpty()) {
                item(key = "sources-summary") {
                    VeneraCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenSourceManage() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "漫画源",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.color.textPrimary,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${sources.size} 个可用源 · 点击进入源管理与网络测速",
                                    fontSize = 11.sp,
                                    color = tokens.color.textSecondary,
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = tokens.color.textTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 顶栏（去掉了刷新按钮）
        VeneraTopAppBar(
            title = "首页",
            largeTitle = "",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = { if (petStickerOn) PetMascot() },
            actions = {
                Box {
                    VeneraTopBarPill(onClick = { onOpenSourceManage() }) {
                        MiuixIcon(
                            imageVector = Icons.Outlined.Extension,
                            contentDescription = "源管理",
                            tint = tokens.color.textSecondary,
                        )
                    }
                    if (updateCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = StatusColors.Degraded,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(tokens.spacing.space1),
                        ) {
                            Text(
                                text = updateCount.toString(),
                                fontSize = tokens.type.badge,
                                fontWeight = tokens.type.weightBold,
                                color = StatusColors.OnBadgeSurface,
                                modifier = Modifier.padding(horizontal = tokens.spacing.badgeHorizontalPadding),
                            )
                        }
                    }
                }

                VeneraTopBarPill(onClick = { onOpenSettings() }) {
                    MiuixIcon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "设置",
                        tint = tokens.color.textSecondary,
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * 首页画廊格卡**宽高比**的允许区间（宽 ÷ 高）。
 *
 * 格子比例取自站方报的真宽高（[com.venera.compose.gallery.data.GalleryPost.cardRatio]），
 * 这样 `ContentScale.Fit` 恰好铺满：**不裁剪、不留黑边**（用户 2026-10-19 的两条诉求）。
 *
 * 为什么还要夹：站方数据会飘 —— `0f`（没报宽高）会让比例变 0、长图/横幅会把一行拉成
 * 一根竖条或一条扁带。两头各卡一道：
 * - [MinHomeCardRatio] 0.55 —— 比常见竖图（2:3 ≈ 0.67、漫画页 ≈ 0.7）更竖一档，
 *   长条图才会被兑成“正常海报”而不是一根针；
 * - [MaxHomeCardRatio] 1.5 —— 横图到 3:2 为止，再宽就变成一条细横幅，那一行就没高度了。
 *
 * 夹完之后真差的那一点由底下那层 `surfaceVariant` 兑住（页面底色，不是黑色）。
 */
private const val MinHomeCardRatio = 0.55f
private const val MaxHomeCardRatio = 1.5f

/**
 * 宽窗档（600dp+）竖图比例的下限：0.9 ≈ 接近方图。手机档仍是 [MinHomeCardRatio] 0.55 ——
 * 那一档的阅读尺寸用户已验收；宽窗上列数反推 + 这一档抬升合起来才压得住行高。
 */
private const val WideMinHomeCardRatio = 0.9f

/**
 * 连续天数那枚小徽标的底色透明度。
 *
 * 单独起一颗名字而不是就地写 `copy(alpha = 0.16f)`：全仓有一条尺
 * （`UiAuditBatch6Test`）禁止逐处抄写“玻璃补底强度”那枚 0.16 —— 它是**同值不同义**，
 * 但字面量一样就会被扫到。起名之后两边各说各的，也不给以后改色留一个找不着的数。
 */
private const val HomeStreakTintAlpha = 0.16f
