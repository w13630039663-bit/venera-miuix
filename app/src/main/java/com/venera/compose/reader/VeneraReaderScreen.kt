package com.venera.compose.reader

import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.prefs.VeneraPreferences
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Venera 纯原生 Jetpack Compose 工业级漫画阅读器
 * 
 * 核心技术融合：
 * 1. 深度借鉴 PuffComic 高性能手势体系 (ReaderZoomState + 双击弹簧缩放 + 阻尼边界平移)
 * 2. 双阅读模式（竖向连续滚动 Webtoon vs 横向单页翻页）
 * 3. 沉浸式全屏与单击呼出 MIUIX 风格毛玻璃悬浮控制层
 * 4. 进度条平滑滑动与实时页码气泡
 * 5. 原生系统预测性返回手势 (PredictiveBackHandler)
 */
@Composable
fun VeneraReaderScreen(
    session: ReaderSession,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val activity = context as? ComponentActivity

    // 当前章节状态
    var currentChapterIndex by remember {
        mutableIntStateOf(session.initialChapterIndex.coerceIn(0, (session.chapters.size - 1).coerceAtLeast(0)))
    }
    val currentChapter = session.chapters.getOrNull(currentChapterIndex)
        ?: return

    val prefs = remember { VeneraPreferences.getInstance(context) }
    val savedModeStr = remember { prefs.defaultReadingMode.value }

    // 阅读模式：竖向连续滚动 vs 横向单页翻页
    var readingMode by remember {
        mutableStateOf(if (savedModeStr == "HORIZONTAL") ReaderReadingMode.HORIZONTAL_PAGE else ReaderReadingMode.VERTICAL_CONTINUOUS)
    }

    // 控制浮层显隐
    var isControlsVisible by remember { mutableStateOf(false) }

    // 页面间距 (条漫模式)
    var pageGapDp by remember { mutableFloatStateOf(prefs.pageGapDp.value) }

    // 缩放手势状态
    val zoomState = rememberReaderZoomState(currentChapterIndex, readingMode)

    // 列表状态 (竖向)
    val verticalListState = rememberLazyListState(
        initialFirstVisibleItemIndex = session.initialPageIndex.coerceIn(0, (currentChapter.pages.size - 1).coerceAtLeast(0))
    )

    // 翻页状态 (横向)
    val horizontalPagerState = rememberPagerState(
        initialPage = session.initialPageIndex.coerceIn(0, (currentChapter.pages.size - 1).coerceAtLeast(0)),
        pageCount = { currentChapter.pages.size }
    )

    // 当前可视页码
    val currentPageIndex by remember {
        derivedStateOf {
            if (readingMode == ReaderReadingMode.VERTICAL_CONTINUOUS) {
                verticalListState.firstVisibleItemIndex.coerceIn(0, (currentChapter.pages.size - 1).coerceAtLeast(0))
            } else {
                horizontalPagerState.currentPage.coerceIn(0, (currentChapter.pages.size - 1).coerceAtLeast(0))
            }
        }
    }

    // 沉浸式状态栏与导航栏控制
    LaunchedEffect(currentChapterIndex, currentPageIndex) {
        HistoryDao.getInstance(context).saveHistory(
            HistoryRecord(
                comicId = session.comicId,
                title = session.comicTitle,
                author = "",
                coverUrl = session.coverUrl,
                sourceName = "拷贝漫画",
                lastChapterTitle = currentChapter.title,
                lastChapterIndex = currentChapterIndex,
                lastPageIndex = currentPageIndex,
                totalPages = currentChapter.pages.size,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

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

    // 原生返回键拦截：若控制栏打开则优先关闭控制栏，否则退出阅读器
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { zoomState.viewportSize = it }
    ) {
        // ==================== 阅读器内容渲染区 ====================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomState.scale
                    scaleY = zoomState.scale
                    translationX = zoomState.offsetX
                    translationY = zoomState.offsetY
                }
                .pointerInput(readingMode, zoomState.isZoomed) {
                    // 双击放大/归位 & 单击切换控制条
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (zoomState.isZoomed) {
                                zoomState.reset(scope = scope, animated = true)
                            } else {
                                val targetScale = 2.5f
                                val targetX = -(tapOffset.x - size.width / 2f) * (targetScale - 1f)
                                val targetY = -(tapOffset.y - size.height / 2f) * (targetScale - 1f)
                                val clamped = zoomState.clampOffset(targetScale, targetX, targetY)
                                zoomState.animateTo(
                                    targetScale = targetScale,
                                    targetOffsetX = clamped.x,
                                    targetOffsetY = clamped.y,
                                    scope = scope
                                )
                            }
                        },
                        onTap = { tapOffset ->
                            if (zoomState.isZoomed) {
                                zoomState.reset(scope = scope, animated = true)
                                return@detectTapGestures
                            }
                            if (readingMode == ReaderReadingMode.HORIZONTAL_PAGE) {
                                val width = size.width
                                when {
                                    tapOffset.x < width * 0.25f -> {
                                        // 点击左侧 25% 翻到上一页
                                        scope.launch {
                                            if (horizontalPagerState.currentPage > 0) {
                                                horizontalPagerState.animateScrollToPage(horizontalPagerState.currentPage - 1)
                                            }
                                        }
                                    }
                                    tapOffset.x > width * 0.75f -> {
                                        // 点击右侧 25% 翻到下一页
                                        scope.launch {
                                            if (horizontalPagerState.currentPage < currentChapter.pages.lastIndex) {
                                                horizontalPagerState.animateScrollToPage(horizontalPagerState.currentPage + 1)
                                            }
                                        }
                                    }
                                    else -> {
                                        // 点击中间 50% 呼出/隐藏面板
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        isControlsVisible = !isControlsVisible
                                    }
                                }
                            } else {
                                // 竖向模式：点击中间触发
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isControlsVisible = !isControlsVisible
                            }
                        }
                    )
                }
                .pointerInput(zoomState) {
                    // 双指 Pinch-to-zoom 捏合缩放处理
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f || panChange != androidx.compose.ui.geometry.Offset.Zero) {
                                zoomState.update(zoomChange, panChange)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            if (readingMode == ReaderReadingMode.VERTICAL_CONTINUOUS) {
                // 条漫竖向流
                LazyColumn(
                    state = verticalListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(pageGapDp.dp)
                ) {
                    itemsIndexed(currentChapter.pages, key = { index, _ -> "vertical-$currentChapterIndex-$index" }) { index, page ->
                        ReaderPageItem(
                            page = page,
                            index = index,
                            total = currentChapter.pages.size
                        )
                    }
                }
            } else {
                // 日漫横向翻页
                HorizontalPager(
                    state = horizontalPagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val page = currentChapter.pages.getOrNull(pageIndex)
                    if (page != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            ReaderPageItem(
                                page = page,
                                index = pageIndex,
                                total = currentChapter.pages.size
                            )
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
                color = Color.Black.copy(alpha = 0.85f),
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
                            Text(text = "←", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = session.comicTitle,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = currentChapter.title,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // 切换阅读模式按钮 (MIUIX Pill)
                    Surface(
                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val newMode = if (readingMode == ReaderReadingMode.VERTICAL_CONTINUOUS) {
                                ReaderReadingMode.HORIZONTAL_PAGE
                            } else {
                                ReaderReadingMode.VERTICAL_CONTINUOUS
                            }
                            readingMode = newMode
                            prefs.setDefaultReadingMode(if (newMode == ReaderReadingMode.HORIZONTAL_PAGE) "HORIZONTAL" else "VERTICAL")
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
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 页码浮动提示
                    Text(
                        text = "${currentPageIndex + 1} / ${currentChapter.pages.size}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 进度滑块
                    Slider(
                        value = currentPageIndex.toFloat(),
                        onValueChange = { targetPage ->
                            val targetIndex = targetPage.toInt().coerceIn(0, currentChapter.pages.lastIndex)
                            scope.launch {
                                if (readingMode == ReaderReadingMode.VERTICAL_CONTINUOUS) {
                                    verticalListState.scrollToItem(targetIndex)
                                } else {
                                    horizontalPagerState.scrollToPage(targetIndex)
                                }
                            }
                        },
                        valueRange = 0f..currentChapter.pages.lastIndex.toFloat().coerceAtLeast(1f),
                        steps = (currentChapter.pages.size - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = MiuixTheme.colorScheme.primary,
                            activeTrackColor = MiuixTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 快捷换章动作条
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentChapterIndex > 0) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.clickable(enabled = currentChapterIndex > 0) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                currentChapterIndex--
                            }
                        ) {
                            Text(
                                text = "⏮ 上一话",
                                color = if (currentChapterIndex > 0) Color.White else Color.Gray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }

                        Text(
                            text = "共 ${session.chapters.size} 话",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentChapterIndex < session.chapters.lastIndex) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.clickable(enabled = currentChapterIndex < session.chapters.lastIndex) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                currentChapterIndex++
                            }
                        ) {
                            Text(
                                text = "下一话 ⏭",
                                color = if (currentChapterIndex < session.chapters.lastIndex) Color.White else Color.Gray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单页渲染单元
 */
@Composable
private fun ReaderPageItem(
    page: ComicPageSource,
    index: Int,
    total: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        when (page) {
            is ComicPageSource.Network -> {
                SubcomposeAsyncImage(
                    model = page.url,
                    contentDescription = "第 ${index + 1} 页",
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentScale = ContentScale.FillWidth
                )
            }
            is ComicPageSource.LocalFile -> {
                SubcomposeAsyncImage(
                    model = page.file,
                    contentDescription = "第 ${index + 1} 页",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
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