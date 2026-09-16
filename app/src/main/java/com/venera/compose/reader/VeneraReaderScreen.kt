package com.venera.compose.reader

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.network.ImageHeaderPolicy
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.source.ComicSourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Venera 生产级 Jetpack Compose 工业级漫画阅读器 (S3 升级)
 *
 * 核心技术栈：
 * 1. saket/telephoto v0.19.0 (ZoomableAsyncImage + SubSampling)：超大图分块与子采样，杜绝 8000px+ 长图 OOM
 * 2. 5 种全量阅读排版：条漫连续流、日漫从右至左(RTL)、美漫从左至右(LTR)、横向连续流、双页对开拼合
 * 3. 前瞻预加载流水线：N+1..N+3 后台自动拉取并缓存至 Coil 磁盘与内存
 * 4. 动态章节调度与抽屉：支持任意章节跳转与未载入章节按需拉取
 * 5. 全面沉浸式控制层：夜间反色滤镜、音量键翻页、屏幕常亮、边缘点击翻页、保存相册与分享
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeneraReaderScreen(
    session: ReaderSession,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val activity = context as? ComponentActivity

    val prefs = remember { VeneraPreferences.getInstance(context) }
    val sourceManager = remember { ComicSourceManager.getInstance(context) }

    // 活跃章节列表（支持动态加载新章节页码）
    val chaptersState = remember { mutableStateListOf<ReaderChapter>().apply { addAll(session.chapters) } }

    var currentChapterIndex by remember {
        mutableIntStateOf(session.initialChapterIndex.coerceIn(0, (chaptersState.size - 1).coerceAtLeast(0)))
    }

    val currentChapter = chaptersState.getOrNull(currentChapterIndex)
        ?: return

    // 阅读模式配置 (默认读取偏好)
    val savedModeKey by prefs.defaultReadingMode.collectAsState()
    var readingMode by remember {
        mutableStateOf(ReaderReadingMode.fromKey(savedModeKey))
    }

    // 设置项
    var pageGapDp by remember { mutableFloatStateOf(prefs.pageGapDp.value) }
    var isNightFilter by remember { mutableStateOf(prefs.nightFilter.value) }
    var keepScreenOn by remember { mutableStateOf(prefs.keepScreenOn.value) }
    var volumeKeyTurn by remember { mutableStateOf(prefs.volumeKeyTurn.value) }
    var clickToTurn by remember { mutableStateOf(prefs.clickToTurn.value) }

    // 控制浮层显隐
    var isControlsVisible by remember { mutableStateOf(false) }

    // 弹窗状态
    var showChapterDrawer by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var isChapterLoading by remember { mutableStateOf(false) }

    // 焦点捕获器（供音量键监听）
    val focusRequester = remember { FocusRequester() }

    // 纵向列表状态 (条漫模式)
    val verticalListState = rememberLazyListState(
        initialFirstVisibleItemIndex = session.initialPageIndex.coerceIn(0, (currentChapter.pages.size - 1).coerceAtLeast(0))
    )

    // 横向连续列表状态
    val horizontalListState = rememberLazyListState(
        initialFirstVisibleItemIndex = session.initialPageIndex.coerceIn(0, (currentChapter.pages.size - 1).coerceAtLeast(0))
    )

    // LTR 翻页状态
    val ltrPagerState = rememberPagerState(
        initialPage = session.initialPageIndex.coerceIn(0, (currentChapter.pages.size - 1).coerceAtLeast(0)),
        pageCount = { currentChapter.pages.size }
    )

    // RTL 翻页状态 (反向索引)
    val rtlInitialPage = if (currentChapter.pages.isNotEmpty()) {
        (currentChapter.pages.lastIndex - session.initialPageIndex).coerceIn(0, currentChapter.pages.lastIndex)
    } else 0
    val rtlPagerState = rememberPagerState(
        initialPage = rtlInitialPage,
        pageCount = { currentChapter.pages.size }
    )

    // 双页拼合翻页状态 (每页2张)
    val doublePairCount = if (currentChapter.pages.isEmpty()) 0 else (currentChapter.pages.size + 1) / 2
    val doublePagerState = rememberPagerState(
        initialPage = (session.initialPageIndex / 2).coerceIn(0, (doublePairCount - 1).coerceAtLeast(0)),
        pageCount = { doublePairCount }
    )

    // 当前可视页码推导 (0-indexed)
    val currentPageIndex by remember {
        derivedStateOf {
            if (currentChapter.pages.isEmpty()) return@derivedStateOf 0
            when (readingMode) {
                ReaderReadingMode.VERTICAL_CONTINUOUS -> {
                    verticalListState.firstVisibleItemIndex.coerceIn(0, currentChapter.pages.lastIndex)
                }
                ReaderReadingMode.HORIZONTAL_CONTINUOUS -> {
                    horizontalListState.firstVisibleItemIndex.coerceIn(0, currentChapter.pages.lastIndex)
                }
                ReaderReadingMode.HORIZONTAL_LTR -> {
                    ltrPagerState.currentPage.coerceIn(0, currentChapter.pages.lastIndex)
                }
                ReaderReadingMode.HORIZONTAL_RTL -> {
                    (currentChapter.pages.lastIndex - rtlPagerState.currentPage).coerceIn(0, currentChapter.pages.lastIndex)
                }
                ReaderReadingMode.DOUBLE_PAGE -> {
                    (doublePagerState.currentPage * 2).coerceIn(0, currentChapter.pages.lastIndex)
                }
            }
        }
    }

    // 页面跳转统一函数
    fun jumpToPage(targetPage: Int) {
        val page = targetPage.coerceIn(0, (currentChapter.pages.size - 1).coerceAtLeast(0))
        scope.launch {
            when (readingMode) {
                ReaderReadingMode.VERTICAL_CONTINUOUS -> verticalListState.scrollToItem(page)
                ReaderReadingMode.HORIZONTAL_CONTINUOUS -> horizontalListState.scrollToItem(page)
                ReaderReadingMode.HORIZONTAL_LTR -> ltrPagerState.scrollToPage(page)
                ReaderReadingMode.HORIZONTAL_RTL -> rtlPagerState.scrollToPage(currentChapter.pages.lastIndex - page)
                ReaderReadingMode.DOUBLE_PAGE -> doublePagerState.scrollToPage(page / 2)
            }
        }
    }

    fun turnToNextPage(): Boolean {
        if (currentPageIndex < currentChapter.pages.lastIndex) {
            jumpToPage(currentPageIndex + 1)
            return true
        }
        return false
    }

    fun turnToPrevPage(): Boolean {
        if (currentPageIndex > 0) {
            jumpToPage(currentPageIndex - 1)
            return true
        }
        return false
    }

    // 动态章节加载与切换
    fun switchToChapter(newChapterIndex: Int, initialPage: Int = 0) {
        if (newChapterIndex !in chaptersState.indices) return
        val targetCh = chaptersState[newChapterIndex]
        if (targetCh.isLoaded && targetCh.pages.isNotEmpty()) {
            currentChapterIndex = newChapterIndex
            jumpToPage(initialPage)
        } else {
            // 需要联网拉取该章节页面
            scope.launch {
                isChapterLoading = true
                val key = session.sourceKey.ifBlank { "copymanga" }
                val res = sourceManager.getChapterPages(key, session.comicId, targetCh.id)
                val pagesData = res.getOrNull()
                val pageUrls = pagesData?.pages.orEmpty()
                if (pageUrls.isNotEmpty()) {
                    pagesData?.headers?.takeIf { it.isNotEmpty() }?.let { hdrs ->
                        ImageHeaderPolicy.publishForUrls(pageUrls, hdrs)
                    }
                    val mappedPages = pageUrls.mapIndexed { idx, u ->
                        ComicPageSource.Network(url = u, pageIndex = idx)
                    }
                    chaptersState[newChapterIndex] = targetCh.copy(pages = mappedPages, isLoaded = true)
                    currentChapterIndex = newChapterIndex
                    jumpToPage(initialPage)
                } else {
                    Toast.makeText(context, "加载章节失败：${res.exceptionOrNull()?.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                }
                isChapterLoading = false
            }
        }
    }

    // ==================== 前瞻预加载流水线 (N+1..N+3) ====================
    LaunchedEffect(currentPageIndex, currentChapterIndex) {
        val pages = currentChapter.pages
        if (pages.isEmpty()) return@LaunchedEffect
        val imageLoader = context.imageLoader
        for (offset in 1..3) {
            val nextIdx = currentPageIndex + offset
            if (nextIdx in pages.indices) {
                val page = pages[nextIdx]
                if (page is ComicPageSource.Network) {
                    val req = ImageRequest.Builder(context)
                        .data(page.url)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .allowHardware(true)
                        .build()
                    imageLoader.enqueue(req)
                }
            }
        }
    }

    // ==================== 历史记录无缝写回 ====================
    LaunchedEffect(currentChapterIndex, currentPageIndex) {
        withContext(Dispatchers.IO) {
            HistoryDao.getInstance(context).saveHistory(
                HistoryRecord(
                    comicId = session.comicId,
                    title = session.comicTitle,
                    author = "",
                    coverUrl = session.coverUrl,
                    sourceName = session.sourceName.ifBlank { "拷贝漫画" },
                    lastChapterTitle = currentChapter.title,
                    lastChapterIndex = currentChapterIndex,
                    lastPageIndex = currentPageIndex,
                    totalPages = currentChapter.pages.size,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // ==================== 屏幕常亮控制 ====================
    DisposableEffect(keepScreenOn) {
        val window = activity?.window
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ==================== 沉浸式全屏与导航栏 ====================
    DisposableEffect(isControlsVisible) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (!isControlsVisible) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.window?.let { win ->
                WindowCompat.getInsetsController(win, win.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 返回键拦截
    PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { }
            if (isControlsVisible) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                isControlsVisible = false
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onBack()
            }
        } catch (_: Exception) { }
    }

    // 反色滤镜
    val nightColorFilter = remember(isNightFilter) {
        if (isNightFilter) {
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f,  0f,  0f, 0f, 255f,
                         0f, -1f,  0f, 0f, 255f,
                         0f,  0f, -1f, 0f, 255f,
                         0f,  0f,  0f, 1f,   0f
                    )
                )
            )
        } else null
    }

    // 当前显示的图片源
    val currentImageSource = currentChapter.pages.getOrNull(currentPageIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (volumeKeyTurn && keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            if (!turnToNextPage()) {
                                if (currentChapterIndex < chaptersState.lastIndex) {
                                    switchToChapter(currentChapterIndex + 1, 0)
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            if (!turnToPrevPage()) {
                                if (currentChapterIndex > 0) {
                                    val prevCh = chaptersState[currentChapterIndex - 1]
                                    switchToChapter(currentChapterIndex - 1, (prevCh.pages.size - 1).coerceAtLeast(0))
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        // ==================== 5 种模式阅读视图 ====================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(readingMode, clickToTurn) {
                    detectTapGestures(
                        onTap = { offset ->
                            val width = size.width
                            if (clickToTurn && readingMode != ReaderReadingMode.VERTICAL_CONTINUOUS) {
                                when {
                                    offset.x < width * 0.25f -> {
                                        // 点击左侧 25%
                                        if (readingMode == ReaderReadingMode.HORIZONTAL_RTL) {
                                            turnToNextPage()
                                        } else {
                                            turnToPrevPage()
                                        }
                                    }
                                    offset.x > width * 0.75f -> {
                                        // 点击右侧 25%
                                        if (readingMode == ReaderReadingMode.HORIZONTAL_RTL) {
                                            turnToPrevPage()
                                        } else {
                                            turnToNextPage()
                                        }
                                    }
                                    else -> {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        isControlsVisible = !isControlsVisible
                                    }
                                }
                            } else {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isControlsVisible = !isControlsVisible
                            }
                        }
                    )
                }
        ) {
            when (readingMode) {
                ReaderReadingMode.VERTICAL_CONTINUOUS -> {
                    // 1. 条漫·纵向连续流
                    LazyColumn(
                        state = verticalListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(pageGapDp.dp)
                    ) {
                        itemsIndexed(currentChapter.pages, key = { index, _ -> "v-$currentChapterIndex-$index" }) { index, page ->
                            ReaderSinglePageItem(
                                page = page,
                                index = index,
                                total = currentChapter.pages.size,
                                colorFilter = nightColorFilter,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth().wrapContentHeight()
                            )
                        }
                    }
                }
                ReaderReadingMode.HORIZONTAL_CONTINUOUS -> {
                    // 2. 横向·连续画卷滚动
                    LazyRow(
                        state = horizontalListState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(pageGapDp.dp)
                    ) {
                        itemsIndexed(currentChapter.pages, key = { index, _ -> "h-$currentChapterIndex-$index" }) { index, page ->
                            ReaderSinglePageItem(
                                page = page,
                                index = index,
                                total = currentChapter.pages.size,
                                colorFilter = nightColorFilter,
                                contentScale = ContentScale.FillHeight,
                                modifier = Modifier.fillMaxHeight().wrapContentWidth()
                            )
                        }
                    }
                }
                ReaderReadingMode.HORIZONTAL_LTR -> {
                    // 3. 美漫·从左至右翻页 (Telephoto Zoomable)
                    HorizontalPager(
                        state = ltrPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIdx ->
                        val page = currentChapter.pages.getOrNull(pageIdx)
                        if (page != null) {
                            ReaderTelephotoPageItem(
                                page = page,
                                index = pageIdx,
                                colorFilter = nightColorFilter,
                                onDoubleTap = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            )
                        }
                    }
                }
                ReaderReadingMode.HORIZONTAL_RTL -> {
                    // 4. 日漫·从右至左翻页 (Telephoto Zoomable)
                    HorizontalPager(
                        state = rtlPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { rtlIdx ->
                        val realIdx = (currentChapter.pages.lastIndex - rtlIdx).coerceIn(0, currentChapter.pages.lastIndex)
                        val page = currentChapter.pages.getOrNull(realIdx)
                        if (page != null) {
                            ReaderTelephotoPageItem(
                                page = page,
                                index = realIdx,
                                colorFilter = nightColorFilter,
                                onDoubleTap = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            )
                        }
                    }
                }
                ReaderReadingMode.DOUBLE_PAGE -> {
                    // 5. 对开·双页拼合 (Double Page)
                    HorizontalPager(
                        state = doublePagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pairIdx ->
                        val pageIdx1 = pairIdx * 2
                        val pageIdx2 = pairIdx * 2 + 1
                        val p1 = currentChapter.pages.getOrNull(pageIdx1)
                        val p2 = currentChapter.pages.getOrNull(pageIdx2)

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (p1 != null) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    ReaderSinglePageItem(
                                        page = p1,
                                        index = pageIdx1,
                                        total = currentChapter.pages.size,
                                        colorFilter = nightColorFilter,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            if (p2 != null) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    ReaderSinglePageItem(
                                        page = p2,
                                        index = pageIdx2,
                                        total = currentChapter.pages.size,
                                        colorFilter = nightColorFilter,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== 顶部悬浮控制栏 ====================
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.88f),
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onBack()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = session.comicTitle,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentChapter.title,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 模式快捷选择胶囊
                    Surface(
                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showSettingsSheet = true
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = readingMode.icon, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = readingMode.label,
                                color = MiuixTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // ==================== 底部悬浮控制栏 ====================
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.90f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 页码气泡
                    Text(
                        text = "${currentPageIndex + 1} / ${currentChapter.pages.size.coerceAtLeast(1)}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 进度滑块
                    Slider(
                        value = currentPageIndex.toFloat(),
                        onValueChange = { targetPage ->
                            jumpToPage(targetPage.toInt())
                        },
                        valueRange = 0f..(currentChapter.pages.size - 1).coerceAtLeast(1).toFloat(),
                        steps = (currentChapter.pages.size - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = MiuixTheme.colorScheme.primary,
                            activeTrackColor = MiuixTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 动作条：上一话、目录抽屉、存图、分享、设置、下一话
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 上一话
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentChapterIndex > 0) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.clickable(enabled = currentChapterIndex > 0) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                switchToChapter(currentChapterIndex - 1, 0)
                            }
                        ) {
                            Text(
                                text = "⏮ 上一话",
                                color = if (currentChapterIndex > 0) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }

                        // 目录抽屉
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showChapterDrawer = true
                        }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "章节列表", tint = Color.White)
                        }

                        // 存图
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            saveCurrentImage(context, currentImageSource)
                        }) {
                            Icon(Icons.Outlined.SaveAlt, contentDescription = "保存当前页", tint = Color.White)
                        }

                        // 分享
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            shareCurrentImage(context, currentImageSource, session.comicTitle, currentChapter.title, currentPageIndex + 1)
                        }) {
                            Icon(Icons.Outlined.Share, contentDescription = "分享当前页", tint = Color.White)
                        }

                        // 设置
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showSettingsSheet = true
                        }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "阅读设置", tint = Color.White)
                        }

                        // 下一话
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentChapterIndex < chaptersState.lastIndex) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.clickable(enabled = currentChapterIndex < chaptersState.lastIndex) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                switchToChapter(currentChapterIndex + 1, 0)
                            }
                        ) {
                            Text(
                                text = "下一话 ⏭",
                                color = if (currentChapterIndex < chaptersState.lastIndex) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // ==================== 章节列表抽屉 (BottomSheet) ====================
        if (showChapterDrawer) {
            ModalBottomSheet(
                onDismissRequest = { showChapterDrawer = false },
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                var isDesc by remember { mutableStateOf(false) }
                val displayChapters = remember(chaptersState, isDesc) {
                    if (isDesc) chaptersState.mapIndexed { idx, ch -> idx to ch }.reversed()
                    else chaptersState.mapIndexed { idx, ch -> idx to ch }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "章节目录 (${chaptersState.size}话)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isDesc = !isDesc }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                if (isDesc) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                                contentDescription = "排序",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDesc) "倒序" else "正序",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(displayChapters, key = { _, pair -> pair.second.id }) { _, (origIdx, ch) ->
                            val isActive = origIdx == currentChapterIndex
                            Surface(
                                color = if (isActive) MiuixTheme.colorScheme.primary.copy(alpha = 0.2f) else Color(0xFF282828),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        showChapterDrawer = false
                                        switchToChapter(origIdx, 0)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ch.title,
                                        fontSize = 14.sp,
                                        color = if (isActive) MiuixTheme.colorScheme.primary else Color.White,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isActive) {
                                        Surface(
                                            color = MiuixTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "阅读中",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

        // ==================== 阅读设置面板 (BottomSheet) ====================
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 36.dp)
                ) {
                    Text(
                        text = "阅读器设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "排版翻页模式",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 5 种模式 Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderReadingMode.values().take(3).forEach { mode ->
                            val isSelected = readingMode == mode
                            Surface(
                                color = if (isSelected) MiuixTheme.colorScheme.primary else Color(0xFF2C2C2C),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        readingMode = mode
                                        prefs.setDefaultReadingMode(mode.key)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = mode.icon, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = mode.label.substringBefore("·"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderReadingMode.values().drop(3).forEach { mode ->
                            val isSelected = readingMode == mode
                            Surface(
                                color = if (isSelected) MiuixTheme.colorScheme.primary else Color(0xFF2C2C2C),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        readingMode = mode
                                        prefs.setDefaultReadingMode(mode.key)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = mode.icon, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = mode.label.substringBefore("·"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else Color.LightGray
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 条漫间距调节
                    if (readingMode == ReaderReadingMode.VERTICAL_CONTINUOUS || readingMode == ReaderReadingMode.HORIZONTAL_CONTINUOUS) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "页面间隔", fontSize = 14.sp, color = Color.White)
                            Text(text = "${pageGapDp.toInt()} dp", fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
                        }
                        Slider(
                            value = pageGapDp,
                            onValueChange = {
                                pageGapDp = it
                                prefs.setPageGapDp(it)
                            },
                            valueRange = 0f..32f,
                            colors = SliderDefaults.colors(
                                thumbColor = MiuixTheme.colorScheme.primary,
                                activeTrackColor = MiuixTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 开关项 1: 夜间反色滤镜
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "夜间反色滤镜", fontSize = 14.sp, color = Color.White)
                            Text(text = "黑白互换保护夜间视力", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = isNightFilter,
                            onCheckedChange = {
                                isNightFilter = it
                                prefs.setNightFilter(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MiuixTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 开关项 2: 保持屏幕常亮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "保持屏幕常亮", fontSize = 14.sp, color = Color.White)
                            Text(text = "阅读时不自动锁屏", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = {
                                keepScreenOn = it
                                prefs.setKeepScreenOn(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MiuixTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 开关项 3: 音量键翻页
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "音量键翻页", fontSize = 14.sp, color = Color.White)
                            Text(text = "音量下键下一页，音量上键上一页", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = volumeKeyTurn,
                            onCheckedChange = {
                                volumeKeyTurn = it
                                prefs.setVolumeKeyTurn(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MiuixTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 开关项 4: 点击边缘翻页
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "点击屏幕边缘翻页", fontSize = 14.sp, color = Color.White)
                            Text(text = "左右两侧快速点击翻页", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = clickToTurn,
                            onCheckedChange = {
                                clickToTurn = it
                                prefs.setClickToTurn(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MiuixTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        // ==================== 章节加载中的转圈提示 ====================
        if (isChapterLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF222222),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MiuixTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "正在载入章节画质...", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * 单页渲染单元 (用于连续流与对开拼合)
 */
@Composable
private fun ReaderSinglePageItem(
    page: ComicPageSource,
    index: Int,
    total: Int,
    colorFilter: ColorFilter?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (page) {
            is ComicPageSource.Network -> {
                SubcomposeAsyncImage(
                    model = page.url,
                    contentDescription = "第 ${index + 1} 页",
                    colorFilter = colorFilter,
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .background(Color(0xFF161616)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "加载中 (${index + 1}/$total)...",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            }
            is ComicPageSource.LocalFile -> {
                SubcomposeAsyncImage(
                    model = page.file,
                    contentDescription = "第 ${index + 1} 页",
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            }
            is ComicPageSource.ZipEntry -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "ZIP: ${page.entryName}", color = Color.White)
                }
            }
        }
    }
}

/**
 * 生产级 Telephoto 页面渲染单元 (用于日漫/美漫单页翻页模式)
 * 自动接管多指缩放、双击缩放以及 >8000px 超大图分块与子采样
 */
@Composable
private fun ReaderTelephotoPageItem(
    page: ComicPageSource,
    index: Int,
    colorFilter: ColorFilter?,
    onDoubleTap: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val zoomState = rememberZoomableState()
        val zoomableImageState = rememberZoomableImageState(zoomState)

        when (page) {
            is ComicPageSource.Network -> {
                ZoomableAsyncImage(
                    model = page.url,
                    contentDescription = "第 ${index + 1} 页",
                    state = zoomableImageState,
                    colorFilter = colorFilter,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ComicPageSource.LocalFile -> {
                ZoomableAsyncImage(
                    model = page.file,
                    contentDescription = "第 ${index + 1} 页",
                    state = zoomableImageState,
                    colorFilter = colorFilter,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ComicPageSource.ZipEntry -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "ZIP: ${page.entryName}", color = Color.White)
                }
            }
        }
    }
}

/**
 * 保存当前页图片到系统相册
 */
private fun saveCurrentImage(context: Context, pageSource: ComicPageSource?) {
    if (pageSource == null) return
    val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    coroutineScope.launch {
        try {
            val urlOrFile = when (pageSource) {
                is ComicPageSource.Network -> pageSource.url
                is ComicPageSource.LocalFile -> pageSource.file.absolutePath
                else -> null
            }
            if (urlOrFile == null) return@launch

            val req = ImageRequest.Builder(context)
                .data(urlOrFile)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(req)
            if (result is SuccessResult) {
                val bitmap = (result.image as? coil3.BitmapImage)?.bitmap
                    ?: (result.image as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val filename = "Venera_$timeStamp.jpg"

                    var fos: OutputStream? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Venera")
                        }
                        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            fos = context.contentResolver.openOutputStream(uri)
                        }
                    } else {
                        val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/Venera"
                        val file = File(imagesDir)
                        if (!file.exists()) file.mkdirs()
                        val image = File(file, filename)
                        fos = FileOutputStream(image)
                    }

                    fos?.use {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已成功保存到相册 Pictures/Venera", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存图片失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * 分享当前页图片
 */
private fun shareCurrentImage(
    context: Context,
    pageSource: ComicPageSource?,
    comicTitle: String,
    chapterTitle: String,
    pageNumber: Int
) {
    if (pageSource == null) return
    val shareText = "《$comicTitle》$chapterTitle 第 $pageNumber 页\n来自 Venera 漫画阅读器"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "$comicTitle - $chapterTitle")
        putExtra(Intent.EXTRA_TEXT, when (pageSource) {
            is ComicPageSource.Network -> "$shareText\n${pageSource.url}"
            else -> shareText
        })
    }
    context.startActivity(Intent.createChooser(intent, "分享漫画单页"))
}