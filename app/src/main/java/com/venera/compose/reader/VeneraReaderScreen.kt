package com.venera.compose.reader

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
// ── 阅读器仍保留的 M3 直连（登记在册，不是漏做）──
// · ModalBottomSheet ×3（章节表 / 阅读设置 / 亮度色彩调节）：miuix 无窗口级底部面板件。
// · Icon ×8：玻璃挂外壳不挂图标；指示器沿用「统一走波浪环」的既有裁决。
// 本屏的开关与滑条已走 VeneraSwitch / VeneraSlider（含 checkedThumbColor 与三档轨道色，
// 两家各有落点，见 components/venera/VeneraControls.kt）。
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.venera.compose.components.WideScreenDrawerWidth
import com.venera.compose.components.wideScreenChromeMaxWidth
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.network.ImageHeaderPolicy
import com.venera.compose.data.network.ImagePipelinePolicy
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.source.ComicSourceManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
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
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.venera.compose.components.venera.VeneraSlider
import com.venera.compose.components.venera.VeneraSwitch

// 前瞻预加载页数已改为偏好 pref_preload_image_count（默认 5）：
// 动态页每页都要跨 WebView 调一次源 JS 再由源发起网络请求，串行预取会把翻页等待线性叠加，
// 因此预取循环保持并发（见 preloadPages）。

/**
 * Venera Jetpack Compose 漫画阅读器 
 *
 * 核心技术栈：
 * 1. saket/telephoto v0.19.0 (ZoomableAsyncImage + SubSampling)：超大图分块与子采样，杜绝 8000px+ 长图 OOM
 * 2. 5 种全量阅读排版：条漫连续流、日漫从右至左(RTL)、美漫从左至右(LTR)、横向连续流、双页对开拼合
 * 3. 前瞻预加载流水线：N+1..N+3 后台自动拉取并缓存至 Coil 磁盘与内存
 * 4. 动态章节调度与抽屉：支持任意章节跳转与未载入章节按需拉取
 * 5. 全面沉浸式控制层：夜间反色滤镜、音量键翻页、屏幕常亮、边缘点击翻页、保存相册与分享
 */
@Composable
fun VeneraReaderScreen(
    session: ReaderSession,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    // A new book/session must not inherit remembered chapters, gestures or open panels.
    key(session) { ReaderSessionContent(session, onBack, onOpenHistory) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderSessionContent(
    session: ReaderSession,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    val activity = context as? ComponentActivity

    // 宽屏档收口：顶栏与底部控制岛在 >600dp 时与底栏共用同一个 540dp 口径并居中，
    // 手机档拿到 null、仍走 fillMaxWidth，几何零改动（口径出处见 WideScreenPolicy）。
    val chromeWidth = wideScreenChromeMaxWidth(LocalConfiguration.current.screenWidthDp.dp)
    val chromeWidthModifier = if (chromeWidth == null) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.width(chromeWidth)
    }
    // 抽屉内容限宽：master 给章节目录与阅读设置抽屉的是定宽 400（scaffold.dart:662,727）。
    // 手机档仍 fillMaxWidth；宽档用 wrapContentSize 把 400 宽的内容摆到中间，
    // sheet 本体保持通栏 —— 不动 material3 抽屉的进出场动画与下拉手势。
    val drawerWidthModifier = if (chromeWidth == null) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.fillMaxWidth().wrapContentSize(Alignment.TopCenter).width(WideScreenDrawerWidth)
    }

    val prefs = remember { VeneraPreferences.getInstance(context) }
    val sourceManager = remember { ComicSourceManager.getInstance(context) }

    // 活跃章节列表（支持动态加载新章节页码）
    val chaptersState = remember { mutableStateListOf<ReaderChapter>().apply { addAll(session.chapters) } }

    var currentChapterIndex by remember {
        mutableIntStateOf(session.initialChapterIndex.coerceIn(0, (chaptersState.size - 1).coerceAtLeast(0)))
    }

    val currentChapter = chaptersState.getOrNull(currentChapterIndex)
        ?: return

    // 阅读模式与设置项**直接订阅偏好**：本文件里每一处本地改动都紧跟一次 prefs.setX(...)，
    // 再另存一份本地副本只会造成"设置页改完必须重开阅读器"这一个后果，没有别的好处。
    // （此前正是如此：pageGap/夜间滤镜/常亮/音量键/点击翻页全部一次性取值。）
    val modeKey by prefs.defaultReadingMode.collectAsState()
    val readingMode = ReaderReadingMode.fromKey(modeKey)
    val pageGapDp by prefs.pageGapDp.collectAsState()
    val isNightFilter by prefs.nightFilter.collectAsState()
    val keepScreenOn by prefs.keepScreenOn.collectAsState()
    val volumeKeyTurn by prefs.volumeKeyTurn.collectAsState()
    val clickToTurn by prefs.clickToTurn.collectAsState()
    val reverseTap by prefs.reverseTapDirection.collectAsState()

    // 控制浮层显隐
    var isControlsVisible by rememberSaveable { mutableStateOf(false) }

    // ── Auto-Scroll 自动巡航（Kotatsu 特色，仅条漫连续流生效）──
    // 播放中按 px/s 匀速下滑；触碰屏幕/手动滑动即暂停。速度不持久化：巡航是临时态。
    var isAutoScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(60f) }

    // 弹窗状态
    var activePanel by rememberSaveable { mutableStateOf(ReaderPanel.NONE) }
    val showChapterDrawer = activePanel == ReaderPanel.CHAPTERS
    val showSettingsSheet = activePanel == ReaderPanel.SETTINGS
    val showChapterCommentsSheet = activePanel == ReaderPanel.COMMENTS
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
    val currentPageIndex by remember(currentChapter) {
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

    // 阅读统计记录
    val sessionStartTime = remember { System.currentTimeMillis() }
    var maxPageReached by remember { mutableIntStateOf(session.initialPageIndex + 1) }

    LaunchedEffect(currentPageIndex) {
        if (currentPageIndex + 1 > maxPageReached) {
            maxPageReached = currentPageIndex + 1
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val durationSec = ((System.currentTimeMillis() - sessionStartTime) / 1000).coerceAtLeast(1)
            // recordSession 按纪律写失败当场抛（磁盘满/写锁/库损坏）。这里原来是
            // `CoroutineScope(Dispatchers.IO).launch` —— 没 Job 也没 CoroutineExceptionHandler，
            // 异常会走线程默认未捕获路径，把"退出阅读器"变成进程级崩溃。
            // 换成带 handler 的作用域：留痕（含 comicId 与失败原因）+ 一次性可见提示，既不静默也不崩。
            val statsComicId = session.comicId
            val statsFailureHandler = CoroutineExceptionHandler { _, error ->
                Log.e("VeneraReader", "阅读统计写入失败：comicId=$statsComicId", error)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "本次阅读统计未能保存", Toast.LENGTH_SHORT).show()
                }
            }
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO + statsFailureHandler).launch {
                com.venera.compose.stats.ReadingStatsManager.getInstance(context).recordSession(
                    comicId = session.comicId,
                    comicTitle = session.comicTitle,
                    sourceName = session.sourceName,
                    tags = session.tags,
                    chapterTitle = currentChapter.title,
                    pagesRead = maxPageReached,
                    durationSeconds = durationSec
                )
            }
        }
    }

    // Chapter switching must wait for composition to publish the new pager count.
    var pendingChapterPage by remember { mutableStateOf<Int?>(null) }
    suspend fun scrollToPage(targetPage: Int) {
        if (currentChapter.pages.isEmpty()) return
        val page = targetPage.coerceIn(0, currentChapter.pages.lastIndex)
        when (readingMode) {
            ReaderReadingMode.VERTICAL_CONTINUOUS -> verticalListState.scrollToItem(page)
            ReaderReadingMode.HORIZONTAL_CONTINUOUS -> horizontalListState.scrollToItem(page)
            ReaderReadingMode.HORIZONTAL_LTR -> ltrPagerState.scrollToPage(page)
            ReaderReadingMode.HORIZONTAL_RTL -> rtlPagerState.scrollToPage(currentChapter.pages.lastIndex - page)
            ReaderReadingMode.DOUBLE_PAGE -> doublePagerState.scrollToPage(page / 2)
        }
    }
    fun jumpToPage(targetPage: Int) { scope.launch { scrollToPage(targetPage) } }
    LaunchedEffect(currentChapter, pendingChapterPage) {
        pendingChapterPage?.let { target ->
            scrollToPage(target)
            pendingChapterPage = null
        }
    }

    fun turnToNextPage(): Boolean {
        // 步长归 readerPageStep：双页一组两页，写死 +1 会被 scrollToPage 的 /2 收回同一组
        // （现象就是"点右下没反应"，而页码气泡与进度条一起冻在偶数页）。
        val next = nextPageIndex(currentPageIndex, readingMode, currentChapter.pages.lastIndex) ?: return false
        jumpToPage(next)
        return true
    }

    fun turnToPrevPage(): Boolean {
        val prev = previousPageIndex(currentPageIndex, readingMode) ?: return false
        jumpToPage(prev)
        return true
    }

    // 动态章节加载与切换
    fun switchToChapter(newChapterIndex: Int, initialPage: Int = 0) {
        if (isChapterLoading || newChapterIndex !in chaptersState.indices) return
        // 换章即退出自动巡航（新章节从页首开始，续播语义不成立）
        isAutoScrolling = false
        val targetCh = chaptersState[newChapterIndex]
        if (targetCh.isLoaded && targetCh.pages.isNotEmpty()) {
            currentChapterIndex = newChapterIndex
            pendingChapterPage = initialPage
        } else {
            // 需要联网拉取该章节页面
            isChapterLoading = true
            scope.launch {
                try {
                    val key = session.sourceKey.ifBlank { "copymanga" }
                    val res = sourceManager.getChapterPages(key, session.comicId, targetCh.id)
                    val pagesData = res.getOrNull()
                    val pageUrls = pagesData?.pages.orEmpty()
                    if (pageUrls.isNotEmpty()) {
                        val useKeys = pagesData?.useOnImageLoad == true
                        if (!useKeys) {
                            pagesData?.headers?.takeIf { it.isNotEmpty() }?.let { hdrs ->
                                ImageHeaderPolicy.publishForUrls(pageUrls, hdrs)
                            }
                        }
                        val mappedPages = pageUrls.mapIndexed { idx, u ->
                            if (useKeys) {
                                // 图片键模式：真实地址由源 JS onImageLoad 逐页解析
                                ComicPageSource.DynamicNetwork(
                                    imageKey = u,
                                    pageIndex = idx,
                                    sourceKey = key,
                                    comicId = session.comicId,
                                    epId = targetCh.id
                                )
                            } else {
                                ComicPageSource.Network(url = u, pageIndex = idx)
                            }
                        }
                        chaptersState[newChapterIndex] = targetCh.copy(pages = mappedPages, isLoaded = true)
                        currentChapterIndex = newChapterIndex
                        pendingChapterPage = initialPage
                    } else {
                        Toast.makeText(context, "加载章节失败：${res.exceptionOrNull()?.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isChapterLoading = false
                }
            }
        }
    }

    // ==================== 前瞻预加载流水线 (N+1..N+5，并发发出) ====================
    LaunchedEffect(currentPageIndex, currentChapterIndex) {
        val pages = currentChapter.pages
        if (pages.isEmpty()) return@LaunchedEffect
        val imageLoader = context.imageLoader
        // 并发预取。此前是 `for (offset in 1..3)` 串行等待：动态页每页都要跨 WebView
        // 调一次源 JS 再由源发起网络请求（EH 每次几百毫秒），串行 3 页就把「翻到下一页」
        // 的等待叠成 1 秒以上。并发后总耗时约等于最慢的一页。
        // 同一页被预取与当前页渲染同时请求时，由 ComicSourceManager 的并发去重兜住，
        // 不会重复解析、也不会重复下载（同一 cacheKey）。
        coroutineScope {
            for (offset in 1..prefs.preloadImageCount.value) {
                val nextIdx = currentPageIndex + offset
                if (nextIdx !in pages.indices) continue
                val page = pages[nextIdx]
                launch {
                    when (page) {
                        is ComicPageSource.Network -> {
                            val req = ImageRequest.Builder(context)
                                .data(page.url)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .allowHardware(true)
                                .build()
                            imageLoader.enqueue(req)
                        }
                        // 动态页：走源 JS onImageLoad 解析（含 nl 换源重试）并落缓存
                        is ComicPageSource.DynamicNetwork ->
                            resolveDynamicPageUrl(context, sourceManager, page)
                        else -> {}
                    }
                }
            }
        }
    }

    // ==================== 历史记录无缝写回 ====================
    LaunchedEffect(currentChapterIndex, currentPageIndex) {
        // Finish an already-started save even when NavHost disposes this destination.
        withContext(NonCancellable + Dispatchers.IO) {
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
    DisposableEffect(isControlsVisible, activePanel) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (!isControlsVisible && activePanel == ReaderPanel.NONE) {
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

    // Sheets own their window's back gesture, then controls, then NavHost's route pop.
    // HUD 收起用官方 BackHandler：与「点空白收起」走完全相同的动画管线
    // （isControlsVisible = false → AnimatedVisibility 的标准 exit），杜绝二次动画。
    BackHandler(enabled = activePanel == ReaderPanel.NONE && isControlsVisible) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        isControlsVisible = false
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

    // pointerInput(Unit) must use the latest chapter/page callbacks, not its first composition.
    val onReaderTap by rememberUpdatedState<(Float) -> Unit> { fraction ->
        when (readerTapAction(fraction, readingMode, clickToTurn, activePanel, reverseTap)) {
            ReaderTapAction.TOGGLE_CONTROLS -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                isControlsVisible = !isControlsVisible
            }
            ReaderTapAction.NEXT_PAGE -> turnToNextPage()
            ReaderTapAction.PREVIOUS_PAGE -> turnToPrevPage()
            ReaderTapAction.NONE -> Unit
        }
    }

    // Auto-Scroll 巡航协程：withFrameNanos 逐帧推进 scrollBy(speed * dt)，
    // 帧率无关（60/90/120Hz 屏速度一致）；暂停/模式切走/换章自动停。
    LaunchedEffect(isAutoScrolling, readingMode) {
        if (!isAutoScrolling) return@LaunchedEffect
        when (readingMode) {
            // 条漫：逐帧匀速滚动（帧率无关）
            ReaderReadingMode.VERTICAL_CONTINUOUS -> {
                var lastFrame = 0L
                while (isActive) {
                    withFrameNanos { now ->
                        val dt = if (lastFrame == 0L) 0f else (now - lastFrame) / 1_000_000_000f
                        lastFrame = now
                        dt
                    }.let { dt ->
                        if (dt > 0f) verticalListState.scrollBy(scrollSpeed * dt)
                    }
                }
            }
            // 美漫 LTR / 日漫 RTL：每 4 秒自动翻到下一页（复用 turnToNextPage，
            // RTL 的镜像索引映射在其内部处理）；到末页自动停。
            ReaderReadingMode.HORIZONTAL_LTR, ReaderReadingMode.HORIZONTAL_RTL -> {
                var acc = 0f
                var lastFrame = 0L
                while (isActive) {
                    withFrameNanos { now ->
                        val dt = if (lastFrame == 0L) 0f else (now - lastFrame) / 1_000_000_000f
                        lastFrame = now
                        dt
                    }.let { dt ->
                        acc += dt
                        // 间隔读偏好实时值而非启动时快照：设置页或面板改完，下一页就生效。
                        if (acc >= prefs.autoScrollPageIntervalSec.value) {
                            acc = 0f
                            if (!turnToNextPage()) isAutoScrolling = false
                        }
                    }
                }
            }
            // 其余模式（横向连续流/双页）不支持巡航
            else -> isAutoScrolling = false
        }
    }

    // 用户触碰屏幕 → 自动暂停巡航（Kotatsu 语义：人一介入就交还控制权）。
    // 用 awaitFirstDown(requireUnconsumed = false) 纯被动观察：不消费事件，
    // 不干扰 Telephoto 缩放/翻页手势；触摸按下（含拖动起点）即暂停。
    // 注意不能用 isScrollInProgress 判定：巡航自己的 scrollBy 每帧也会短暂置位它。
    val pauseAutoScrollOnTouch = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitFirstDown(requireUnconsumed = false)
                isAutoScrolling = false
            }
        }
    }

    // 当前显示的图片源
    val currentImageSource = currentChapter.pages.getOrNull(currentPageIndex)

    LaunchedEffect(activePanel) {
        if (activePanel == ReaderPanel.NONE) focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (activePanel == ReaderPanel.NONE && volumeKeyTurn && keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
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
        // ==================== 5 种模式阅读视图 ====================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(pauseAutoScrollOnTouch)
                .pointerInput(Unit) {
                    // Only unconsumed taps reach here; Telephoto owns its own single/double taps.
                    detectTapGestures(onTap = { offset -> onReaderTap(offset.x / size.width.coerceAtLeast(1)) })
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
                                onTap = onReaderTap
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
                                onTap = onReaderTap
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

        // ==================== 顶部悬浮胶囊岛（Floating Pill Island）====================
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            // 四边留白悬浮药丸：不再是贴顶直角黑条。shadow 提供浮起感，
            // 24dp 大圆角 + 92% BadgeSurface + 微光描边外框。
            Surface(
                color = StatusColors.BadgeSurface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .statusBarsPadding()
                    .then(chromeWidthModifier)
                    .shadow(12.dp, RoundedCornerShape(24.dp))
                    .border(0.5.dp, StatusColors.OnBadgeSurface.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space6),
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = StatusColors.OnBadgeSurface)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = session.comicTitle,
                                color = StatusColors.OnBadgeSurface,
                                fontSize = tokens.type.body,
                                fontWeight = tokens.type.weightBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentChapter.title,
                                color = StatusColors.OnBadgeSurface.copy(alpha = 0.7f),
                                fontSize = tokens.type.caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 历史：底栏让出主 Tab 后，阅读器是"读到一半想换一本"的最高频出口，
                    // 所以走顶栏胶囊位（与返回键同一 40dp 圆形口径），不挤底部功能键行。
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onOpenHistory()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.History, contentDescription = "阅读历史", tint = StatusColors.OnBadgeSurface)
                    }

                    Spacer(modifier = Modifier.width(tokens.spacing.space3))

                    // 模式快捷选择胶囊
                    Surface(
                        color = tokens.color.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(tokens.shape.small),
                        modifier = Modifier.clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            activePanel = ReaderPanel.SETTINGS
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = readingMode.icon, fontSize = tokens.type.body)
                            Spacer(modifier = Modifier.width(tokens.spacing.space1))
                            Text(
                                text = readingMode.label,
                                color = tokens.color.primary,
                                fontSize = tokens.type.caption,
                                fontWeight = tokens.type.weightSemibold
                            )
                        }
                    }
                }
            }
        }

        // ==================== 底部悬浮控制岛（Floating Control Island）====================
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // 底部居中悬浮岛：16/12dp 四边留白 + 28dp 大圆角 + 16dp 阴影 + 微光描边。
            // 两行结构：上一话/Slider/下一话 + 功能键行（自动播放/目录/存图/设置）。
            Surface(
                color = StatusColors.BadgeSurface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding()
                    .then(chromeWidthModifier)
                    .shadow(16.dp, RoundedCornerShape(28.dp))
                    .border(0.5.dp, StatusColors.OnBadgeSurface.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space4),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── 第一行：上一话 | Slider（页码气泡浮于其上） | 下一话 ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 上一话
                        Surface(
                            shape = RoundedCornerShape(tokens.shape.small),
                            color = if (currentChapterIndex > 0) StatusColors.OnBadgeSurface.copy(alpha = 0.15f) else StatusColors.OnBadgeSurface.copy(alpha = 0.05f),
                            modifier = Modifier.clickable(enabled = currentChapterIndex > 0) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                switchToChapter(currentChapterIndex - 1, 0)
                            }
                        ) {
                            Text(
                                text = "⏮",
                                color = if (currentChapterIndex > 0) StatusColors.OnBadgeSurface else StatusColors.OnBadgeSurface.copy(alpha = 0.4f),
                                fontSize = tokens.type.body,
                                modifier = Modifier.padding(horizontal = tokens.spacing.space3, vertical = tokens.spacing.space2)
                            )
                        }

                        Spacer(modifier = Modifier.width(tokens.spacing.space3))

                        Column(modifier = Modifier.weight(1f)) {
                            // 实时页码气泡：悬浮于 Slider 上方居中
                            Text(
                                text = (currentPageIndex + 1).toString() + " / " + currentChapter.pages.size.coerceAtLeast(1),
                                color = StatusColors.OnBadgeSurface,
                                fontSize = tokens.type.overline,
                                fontWeight = tokens.type.weightBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(tokens.spacing.space1))
                            VeneraSlider(
                                value = currentPageIndex.toFloat(),
                                onValueChange = { targetPage ->
                                    jumpToPage(targetPage.toInt())
                                },
                                valueRange = 0f..(currentChapter.pages.size - 1).coerceAtLeast(1).toFloat(),
                                steps = (currentChapter.pages.size - 2).coerceAtLeast(0),
                                thumbColor = tokens.color.primary,
                                activeTrackColor = tokens.color.primary,
                                inactiveTrackColor = StatusColors.OnBadgeSurface.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.width(tokens.spacing.space3))

                        // 下一话
                        Surface(
                            shape = RoundedCornerShape(tokens.shape.small),
                            color = if (currentChapterIndex < chaptersState.lastIndex) StatusColors.OnBadgeSurface.copy(alpha = 0.15f) else StatusColors.OnBadgeSurface.copy(alpha = 0.05f),
                            modifier = Modifier.clickable(enabled = currentChapterIndex < chaptersState.lastIndex) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                switchToChapter(currentChapterIndex + 1, 0)
                            }
                        ) {
                            Text(
                                text = "⏭",
                                color = if (currentChapterIndex < chaptersState.lastIndex) StatusColors.OnBadgeSurface else StatusColors.OnBadgeSurface.copy(alpha = 0.4f),
                                fontSize = tokens.type.body,
                                modifier = Modifier.padding(horizontal = tokens.spacing.space3, vertical = tokens.spacing.space2)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(tokens.spacing.space2))

                    // ── 第二行：功能按键行（均分宽度四键）──
                    // [▶ 自动播放]（仅条漫生效）｜[📑 章节目录]｜[💾 存图]｜[⚙️ 阅读设置]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3)
                    ) {
                        // 自动播放：条漫逐帧滚动，美漫/日漫每 4 秒翻页；点按进入巡航并收起控制栏
                        val autoScrollEnabled = readingMode == ReaderReadingMode.VERTICAL_CONTINUOUS ||
                            readingMode == ReaderReadingMode.HORIZONTAL_LTR ||
                            readingMode == ReaderReadingMode.HORIZONTAL_RTL
                        Surface(
                            shape = RoundedCornerShape(tokens.shape.small),
                            color = if (autoScrollEnabled) tokens.color.primary.copy(alpha = 0.30f) else StatusColors.OnBadgeSurface.copy(alpha = 0.05f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = autoScrollEnabled) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    isAutoScrolling = true
                                    isControlsVisible = false
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = tokens.spacing.space2).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (autoScrollEnabled) "▶" else "▶",
                                    color = if (autoScrollEnabled) StatusColors.OnBadgeSurface else StatusColors.OnBadgeSurface.copy(alpha = 0.4f),
                                    fontSize = tokens.type.body
                                )
                                Text(
                                    text = "自动播放",
                                    color = if (autoScrollEnabled) StatusColors.OnBadgeSurface else StatusColors.OnBadgeSurface.copy(alpha = 0.4f),
                                    fontSize = tokens.type.badge
                                )
                            }
                        }

                        // 章节目录
                        Surface(
                            shape = RoundedCornerShape(tokens.shape.small),
                            color = StatusColors.OnBadgeSurface.copy(alpha = 0.08f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    activePanel = ReaderPanel.CHAPTERS
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = tokens.spacing.space2).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = "章节列表",
                                    tint = StatusColors.OnBadgeSurface,
                                    modifier = Modifier.size(tokens.spacing.badgeIconSize)
                                )
                                Text(
                                    text = "目录",
                                    color = StatusColors.OnBadgeSurface,
                                    fontSize = tokens.type.badge
                                )
                            }
                        }

                        // 存图
                        Surface(
                            shape = RoundedCornerShape(tokens.shape.small),
                            color = StatusColors.OnBadgeSurface.copy(alpha = 0.08f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    saveCurrentImage(context, currentImageSource)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = tokens.spacing.space2).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Outlined.SaveAlt,
                                    contentDescription = "保存当前页",
                                    tint = StatusColors.OnBadgeSurface,
                                    modifier = Modifier.size(tokens.spacing.badgeIconSize)
                                )
                                Text(
                                    text = "存图",
                                    color = StatusColors.OnBadgeSurface,
                                    fontSize = tokens.type.badge
                                )
                            }
                        }

                        // 收藏当前页为插图。`favoriteCurrentPage` 早就写好了，
                        // 只是从来没有按钮调它 —— 插图收藏页因此永远是空的。
                        Surface(
                            shape = RoundedCornerShape(tokens.shape.small),
                            color = StatusColors.OnBadgeSurface.copy(alpha = 0.08f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    favoriteCurrentPage(
                                        context, session, currentChapter.title,
                                        currentPageIndex, currentImageSource
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = tokens.spacing.space2).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Outlined.FavoriteBorder,
                                    contentDescription = "收藏当前页为插图",
                                    tint = StatusColors.OnBadgeSurface,
                                    modifier = Modifier.size(tokens.spacing.badgeIconSize)
                                )
                                Text(
                                    text = "插图",
                                    color = StatusColors.OnBadgeSurface,
                                    fontSize = tokens.type.badge
                                )
                            }
                        }

                        // 分享当前页图片
                        Surface(
                            shape = RoundedCornerShape(tokens.shape.small),
                            color = StatusColors.OnBadgeSurface.copy(alpha = 0.08f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    shareCurrentImage(context, currentImageSource, session.comicTitle, currentChapter.title, currentPageIndex + 1)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = tokens.spacing.space2).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = "分享当前页",
                                    tint = StatusColors.OnBadgeSurface,
                                    modifier = Modifier.size(tokens.spacing.badgeIconSize)
                                )
                                Text(
                                    text = "分享",
                                    color = StatusColors.OnBadgeSurface,
                                    fontSize = tokens.type.badge
                                )
                            }
                        }

                        // 阅读设置
                        Surface(
                            shape = RoundedCornerShape(tokens.shape.small),
                            color = StatusColors.OnBadgeSurface.copy(alpha = 0.08f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    activePanel = ReaderPanel.SETTINGS
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = tokens.spacing.space2).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = "阅读设置",
                                    tint = StatusColors.OnBadgeSurface,
                                    modifier = Modifier.size(tokens.spacing.badgeIconSize)
                                )
                                Text(
                                    text = "设置",
                                    color = StatusColors.OnBadgeSurface,
                                    fontSize = tokens.type.badge
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== Auto-Scroll 迷你悬浮控制器（播放时显示于右下）====================
        if (isAutoScrolling && !isControlsVisible && activePanel == ReaderPanel.NONE) {
            Surface(
                color = StatusColors.BadgeSurface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .border(0.5.dp, StatusColors.OnBadgeSurface.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = tokens.spacing.space3, vertical = tokens.spacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2)
                ) {
                    // 暂停
                    Surface(
                        shape = CircleShape,
                        color = tokens.color.primary.copy(alpha = 0.35f),
                        modifier = Modifier.size(tokens.spacing.iconButtonSize - tokens.spacing.space4).clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isAutoScrolling = false
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(text = "⏸", color = StatusColors.OnBadgeSurface, fontSize = tokens.type.caption)
                        }
                    }
                    // 减速 / 加速（仅条漫滚动模式有意义；翻页模式固定 4s/页）
                    if (readingMode == ReaderReadingMode.VERTICAL_CONTINUOUS) {
                        Surface(
                            shape = CircleShape,
                            color = StatusColors.OnBadgeSurface.copy(alpha = 0.10f),
                            modifier = Modifier.size(tokens.spacing.iconButtonSize - tokens.spacing.space4).clickable {
                                scrollSpeed = (scrollSpeed - 15f).coerceAtLeast(15f)
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(text = "−", color = StatusColors.OnBadgeSurface, fontSize = tokens.type.itemTitle)
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = StatusColors.OnBadgeSurface.copy(alpha = 0.10f),
                            modifier = Modifier.size(tokens.spacing.iconButtonSize - tokens.spacing.space4).clickable {
                                scrollSpeed = (scrollSpeed + 15f).coerceAtMost(480f)
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(text = "+", color = StatusColors.OnBadgeSurface, fontSize = tokens.type.itemTitle)
                            }
                        }
                        // 当前速度
                        Text(
                            text = scrollSpeed.toInt().toString() + " px/s",
                            color = StatusColors.OnBadgeSurface.copy(alpha = 0.75f),
                            fontSize = tokens.type.badge,
                            modifier = Modifier.padding(end = tokens.spacing.space1)
                        )
                    } else {
                        Text(
                            text = "4s/页",
                            color = StatusColors.OnBadgeSurface.copy(alpha = 0.75f),
                            fontSize = tokens.type.badge,
                            modifier = Modifier.padding(end = tokens.spacing.space1)
                        )
                    }
                }
            }
        }

        // ==================== 章节列表抽屉 (BottomSheet) ====================
        if (showChapterDrawer) {
            ModalBottomSheet(
                onDismissRequest = { activePanel = ReaderPanel.NONE },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = StatusColors.BadgeSurface,
                contentColor = StatusColors.OnBadgeSurface,
                shape = RoundedCornerShape(topStart = tokens.shape.extraLarge, topEnd = tokens.shape.extraLarge)
            ) {
                var isDesc by remember { mutableStateOf(false) }
                val displayChapters = remember(chaptersState, isDesc) {
                    if (isDesc) chaptersState.mapIndexed { idx, ch -> idx to ch }.reversed()
                    else chaptersState.mapIndexed { idx, ch -> idx to ch }
                }

                Column(
                    modifier = Modifier
                        .then(drawerWidthModifier)
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
                            fontSize = tokens.type.itemTitle,
                            fontWeight = tokens.type.weightBold,
                            color = StatusColors.OnBadgeSurface
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
                                fontSize = tokens.type.sectionTitle,
                                color = tokens.color.primary,
                                fontWeight = tokens.type.weightMedium
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
                                color = if (isActive) tokens.color.primary.copy(alpha = 0.2f) else StatusColors.OnBadgeSurface.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(tokens.shape.small),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        activePanel = ReaderPanel.NONE
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
                                        fontSize = tokens.type.body,
                                        color = if (isActive) tokens.color.primary else StatusColors.OnBadgeSurface,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isActive) {
                                        Surface(
                                            color = tokens.color.primary,
                                            shape = RoundedCornerShape(tokens.shape.extraSmall)
                                        ) {
                                            Text(
                                                text = "阅读中",
                                                color = tokens.color.onPrimary,
                                                fontSize = tokens.type.badge,
                                                fontWeight = tokens.type.weightBold,
                                                modifier = Modifier.padding(horizontal = tokens.spacing.badgeHorizontalPadding, vertical = tokens.spacing.badgeVerticalPadding)
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
                onDismissRequest = { activePanel = ReaderPanel.NONE },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = StatusColors.BadgeSurface,
                contentColor = StatusColors.OnBadgeSurface,
                shape = RoundedCornerShape(topStart = tokens.shape.extraLarge, topEnd = tokens.shape.extraLarge)
            ) {
                Column(
                    modifier = Modifier
                        .then(drawerWidthModifier)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 36.dp)
                ) {
                    Text(
                        text = "阅读器设置",
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightBold,
                        color = StatusColors.OnBadgeSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "排版翻页模式",
                        fontSize = tokens.type.sectionTitle,
                        color = StatusColors.OnBadgeSurface.copy(alpha = 0.6f)
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
                                color = if (isSelected) tokens.color.primary else StatusColors.OnBadgeSurface.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(tokens.shape.small),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        prefs.setDefaultReadingMode(mode.key)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = tokens.spacing.space5),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = mode.icon, fontSize = tokens.type.itemTitle)
                                    Spacer(modifier = Modifier.height(tokens.spacing.space1))
                                    Text(
                                        text = mode.label.substringBefore("·"),
                                        fontSize = tokens.type.caption,
                                        fontWeight = tokens.type.weightSemibold,
                                        color = if (isSelected) tokens.color.onPrimary else StatusColors.OnBadgeSurface.copy(alpha = 0.75f)
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
                                color = if (isSelected) tokens.color.primary else StatusColors.OnBadgeSurface.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(tokens.shape.small),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        prefs.setDefaultReadingMode(mode.key)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = tokens.spacing.space5),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = mode.icon, fontSize = tokens.type.itemTitle)
                                    Spacer(modifier = Modifier.height(tokens.spacing.space1))
                                    Text(
                                        text = mode.label.substringBefore("·"),
                                        fontSize = tokens.type.caption,
                                        fontWeight = tokens.type.weightSemibold,
                                        color = if (isSelected) tokens.color.onPrimary else StatusColors.OnBadgeSurface.copy(alpha = 0.75f)
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
                            Text(text = "页面间隔", fontSize = tokens.type.body, color = StatusColors.OnBadgeSurface)
                            Text(text = "${pageGapDp.toInt()} dp", fontSize = tokens.type.sectionTitle, color = tokens.color.primary)
                        }
                        VeneraSlider(
                            value = pageGapDp,
                            onValueChange = {
                                prefs.setPageGapDp(it)
                            },
                            valueRange = 0f..32f,
                            thumbColor = tokens.color.primary,
                            activeTrackColor = tokens.color.primary
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
                            Text(text = "黑白互换保护夜间视力", fontSize = tokens.type.overline, color = StatusColors.OnBadgeSurface.copy(alpha = 0.55f))
                        }
                        VeneraSwitch(
                            checked = isNightFilter,
                            onCheckedChange = {
                                prefs.setNightFilter(it)
                            },
                            checkedThumbColor = MiuixTheme.colorScheme.primary
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
                            Text(text = "阅读时不自动锁屏", fontSize = tokens.type.overline, color = StatusColors.OnBadgeSurface.copy(alpha = 0.55f))
                        }
                        VeneraSwitch(
                            checked = keepScreenOn,
                            onCheckedChange = {
                                prefs.setKeepScreenOn(it)
                            },
                            checkedThumbColor = MiuixTheme.colorScheme.primary
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
                            Text(text = "音量下键下一页，音量上键上一页", fontSize = tokens.type.overline, color = StatusColors.OnBadgeSurface.copy(alpha = 0.55f))
                        }
                        VeneraSwitch(
                            checked = volumeKeyTurn,
                            onCheckedChange = {
                                prefs.setVolumeKeyTurn(it)
                            },
                            checkedThumbColor = MiuixTheme.colorScheme.primary
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
                            Text(text = "左右两侧快速点击翻页", fontSize = tokens.type.overline, color = StatusColors.OnBadgeSurface.copy(alpha = 0.55f))
                        }
                        VeneraSwitch(
                            checked = clickToTurn,
                            onCheckedChange = {
                                prefs.setClickToTurn(it)
                            },
                            checkedThumbColor = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // ==================== 章节评论 Sheet（对齐官方阅读器里的那一屏）====================
        if (showChapterCommentsSheet) {
            ModalBottomSheet(
                onDismissRequest = { activePanel = ReaderPanel.NONE },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = StatusColors.BadgeSurface,
                contentColor = StatusColors.OnBadgeSurface,
                shape = RoundedCornerShape(topStart = tokens.shape.extraLarge, topEnd = tokens.shape.extraLarge)
            ) {
                ChapterCommentsSheetContent(
                    sourceKey = session.sourceKey,
                    comicId = session.comicId,
                    chapterId = currentChapter.id,
                    chapterTitle = currentChapter.title
                )
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
                    color = StatusColors.BadgeSurface,
                    shape = RoundedCornerShape(tokens.shape.large),
                    modifier = Modifier.padding(tokens.spacing.space9)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ReaderWavyIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "正在载入章节画质...", color = StatusColors.OnBadgeSurface, fontSize = tokens.type.body)
                    }
                }
            }
        }
    }
}

/**
 * 正文页取图的成因为什么要落 logcat：这一段的失败在 UI 上只有一句
 * 「第 N 页加载失败，点按重试」，而**源 JS 拒绝解析**、**解析给了空 url（要换 nl 源）**、
 * **地址有效但下载失败**、**满 5 轮仍未成功**是四种完全不同的病，处置方向两两相反
 * （前三条分别指向源脚本、站点分流、防盗链/字节链）。没有成因就只能靠猜。
 */
private const val PAGE_LOAD_TAG = "ReaderPageLoad"

/**
 * 「动态页」解析状态：真实 URL、失败标志与手动重试计数
 */
private class DynamicPageState {
    var url by mutableStateOf<String?>(null)
    var failed by mutableStateOf(false)
    var attempt by mutableIntStateOf(0)
}

/**
 * 解析「动态页」并预取进图片缓存，返回可直接展示的 URL；全部重试失败返回 null。
 *
 * 对齐官方 `network/images.dart:_loadComicImage`：
 * 先调源 JS `onImageLoad(imageKey, cid, eid)` 拿 `{url, headers, nl}`；
 * 下载失败带上 `nl` 重新解析（等价官方执行 onLoadFailed 闭包），上限 5 次。
 * 解析出的真实 URL + headers 会发布进 [ImageHeaderPolicy]（Referer 防盗链）。
 *
 * 图片请求用 [ComicPageSource.DynamicNetwork.cacheKey]（`imageKey@sourceKey@cid@eid`）
 * 作缓存键 —— 与官方 `network/images.dart` 一致。源解析出的真实 URL 往往带临时
 * 签名（EH 尤甚），若用 URL 当键，翻回旧页会被当成新图重新下载；用 imageKey 作键
 * 才能命中缓存，也让「解析出的地址已过期」时能靠磁盘缓存兜住。
 *
 * @param forceRefresh 绕过解析缓存强制重新解析（手动重试 / 上一轮已下载失败时用）
 */
private suspend fun resolveDynamicPageUrl(
    context: Context,
    sourceManager: ComicSourceManager,
    page: ComicPageSource.DynamicNetwork,
    maxAttempts: Int = 5,
    forceRefresh: Boolean = false
): String? {
    var nl: String? = null
    repeat(maxAttempts) { round ->
        val resolved = sourceManager
            .resolveImageLoadingConfig(
                page.sourceKey, page.comicId, page.epId, page.imageKey, nl,
                // 首轮可吃解析缓存；进入第二轮说明上一轮拿到的地址下载失败了
                // （多半是临时签名过期）→ 必须强制重新解析，否则会拿着同一份
                // 失效地址空转满 5 次。
                forceRefresh = forceRefresh || round > 0
            )
        val cfg = resolved.getOrNull()
        if (cfg == null) {
            // 成因必须留痕。这里原本是一句 `?: return null`，于是"源 JS 拒绝"与
            // "地址拿到了但下载挂了"这两种完全相反的病，在屏幕上长得一模一样
            // （都只剩"第 N 页加载失败"），排查只能靠猜。
            Log.w(
                PAGE_LOAD_TAG,
                "解析失败 src=${page.sourceKey} ep=${page.epId} img=${page.imageKey} nl=$nl round=$round",
                resolved.exceptionOrNull(),
            )
            return null
        }
        if (cfg.url.isBlank()) {
            Log.w(
                PAGE_LOAD_TAG,
                "解析返回空 url，按 nl 换源重试 src=${page.sourceKey} ep=${page.epId} img=${page.imageKey} nl=${cfg.nl}",
            )
            nl = cfg.nl
            return@repeat
        }
        cfg.headers.takeIf { it.isNotEmpty() }?.let {
            ImageHeaderPolicy.publishForUrls(listOf(cfg.url), it)
        }
        val req = ImageRequest.Builder(context)
            .data(cfg.url)
            .memoryCacheKey(page.cacheKey)
            .diskCacheKey(page.cacheKey)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .build()
        val result = context.imageLoader.execute(req)
        if (result is SuccessResult) return cfg.url
        // 只记**不带 query 的**地址：JM 的签名 token 常在 query 上，整串进 logcat 等于往日志里落凭据。
        val downloadDiag = "下载失败 url=${cfg.url.substringBefore('?')} " +
            "走字节链=${ImagePipelinePolicy.needsBytePipeline(cfg.url)} " +
            "JM块数=${ImagePipelinePolicy.getScrambleNum(cfg.url)} 头=${cfg.headers.keys} round=$round"
        when (result) {
            is ErrorResult -> Log.w(PAGE_LOAD_TAG, downloadDiag, result.throwable)
            else -> Log.w(PAGE_LOAD_TAG, "$downloadDiag 且 Coil 未给出异常（${result::class.simpleName}）")
        }
        nl = cfg.nl
    }
    Log.w(PAGE_LOAD_TAG, "满 $maxAttempts 轮仍未成功 src=${page.sourceKey} ep=${page.epId} img=${page.imageKey}")
    return null
}

/**
 * 在组合内解析动态页：进入可视范围才调源 JS（与官方懒加载一致），
 * 失败后由 UI 触发 [DynamicPageState.attempt] 自增重试。
 */
@Composable
private fun rememberDynamicPageResolution(page: ComicPageSource.DynamicNetwork): DynamicPageState {
    val context = LocalContext.current
    val state = remember(page) { DynamicPageState() }
    LaunchedEffect(page, state.attempt) {
        if (state.url == null) {
            state.failed = false
            val url = resolveDynamicPageUrl(
                context,
                ComicSourceManager.getInstance(context),
                page,
                // 用户点按重试说明上一轮解析出的地址已经不可用 → 绕过解析缓存重来
                forceRefresh = state.attempt > 0
            )
            if (url != null) {
                state.url = url
            } else {
                state.failed = true
            }
        }
    }
    return state
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
                                .background(StatusColors.BadgeSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ReaderWavyIndicator()
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
            is ComicPageSource.DynamicNetwork -> {
                val dyn = rememberDynamicPageResolution(page)
                val resolvedUrl = dyn.url
                val requestContext = LocalContext.current
                when {
                    resolvedUrl != null -> SubcomposeAsyncImage(
                        // 用 imageKey 组合键作缓存键（对齐官方 network/images.dart）：
                        // 源签发的真实地址会变，拿 URL 当键会让翻回旧页被判成新图重新下载。
                        // 必须 remember：ImageRequest 没有值相等，每次重组 Coil 都判"模型换了"，
                        // 于是取消在飞请求重发 —— 整条 下载+去混淆 链在阅读器里被反复重启。
                        model = remember(resolvedUrl, page.cacheKey) {
                            ImageRequest.Builder(requestContext)
                                .data(resolvedUrl)
                                .memoryCacheKey(page.cacheKey)
                                .diskCacheKey(page.cacheKey)
                                .build()
                        },
                        contentDescription = "第 ${index + 1} 页",
                        colorFilter = colorFilter,
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .background(StatusColors.BadgeSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ReaderWavyIndicator()
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
                    dyn.failed -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .background(StatusColors.BadgeSurface)
                            .clickable { dyn.attempt++ },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "第 ${index + 1} 页加载失败，点按重试",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                    else -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .background(StatusColors.BadgeSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        ReaderWavyIndicator()
                    }
                }
            }
            is ComicPageSource.LocalFile -> {
                // 本地文件同样配齐加载占位与失败提示：避免磁盘卡顿/文件损坏时整屏塌陷白屏
                SubcomposeAsyncImage(
                    model = page.file,
                    contentDescription = "第 ${index + 1} 页",
                    colorFilter = colorFilter,
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .background(StatusColors.BadgeSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            ReaderWavyIndicator()
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .background(StatusColors.BadgeSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "第 ${index + 1} 页读取失败",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = contentScale
                )
            }
            is ComicPageSource.ZipEntry -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(StatusColors.BadgeSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "ZIP: ${page.entryName}", color = Color.White)
                }
            }
        }
    }
}

/**
 * Telephoto 页面渲染单元 (用于日漫/美漫单页翻页模式)
 * 自动接管多指缩放、双击缩放以及 >8000px 超大图分块与子采样
 */
@Composable
private fun ReaderTelephotoPageItem(
    page: ComicPageSource,
    index: Int,
    colorFilter: ColorFilter?,
    onTap: (Float) -> Unit
) {
    var pageWidth by remember { mutableIntStateOf(1) }
    Box(
        modifier = Modifier.fillMaxSize().onSizeChanged { pageWidth = it.width.coerceAtLeast(1) },
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
                    onClick = { offset -> onTap(offset.x / pageWidth) },
                    colorFilter = colorFilter,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ComicPageSource.DynamicNetwork -> {
                val dyn = rememberDynamicPageResolution(page)
                val resolvedUrl = dyn.url
                val zoomRequestContext = LocalContext.current
                when {
                    resolvedUrl != null -> ZoomableAsyncImage(
                        // 同单页组件：缓存键用 imageKey 组合键，避免地址变化导致重复下载。
                        // 同样必须 remember，否则每次重组都重启整条取图链。
                        model = remember(resolvedUrl, page.cacheKey) {
                            ImageRequest.Builder(zoomRequestContext)
                                .data(resolvedUrl)
                                .memoryCacheKey(page.cacheKey)
                                .diskCacheKey(page.cacheKey)
                                .build()
                        },
                        contentDescription = "第 ${index + 1} 页",
                        state = zoomableImageState,
                        onClick = { offset -> onTap(offset.x / pageWidth) },
                        colorFilter = colorFilter,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    dyn.failed -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { dyn.attempt++ },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "第 ${index + 1} 页加载失败，点按重试",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ReaderWavyIndicator()
                    }
                }
            }
            is ComicPageSource.LocalFile -> {
                ZoomableAsyncImage(
                    model = page.file,
                    contentDescription = "第 ${index + 1} 页",
                    state = zoomableImageState,
                    onClick = { offset -> onTap(offset.x / pageWidth) },
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
                // 动态页先解析出真实地址再取图
                is ComicPageSource.DynamicNetwork ->
                    resolveDynamicPageUrl(context, ComicSourceManager.getInstance(context), pageSource)
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
 * 收藏当前单页为插图
 *
 * 网络页在写表之前先把**去混淆后的原画**落到 filesDir/favorite_images/：
 * 只存 URL 的收藏墙拿到的是混淆原图（禁漫）+ 会过期的签名地址（EH）+ 离线裂图，
 * 这三条一起由落盘解决。本地漫画页的 localPath 本来就指向书本体，不再复制。
 */
private fun favoriteCurrentPage(
    context: Context,
    session: ReaderSession,
    chapterTitle: String,
    pageIndex: Int,
    pageSource: ComicPageSource?
) {
    if (pageSource == null) return
    val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    coroutineScope.launch {
        try {
            val manager = com.venera.compose.feature.favoriteimages.FavoriteImagesManager
                .getInstance(context)
            val urlOrFile = when (pageSource) {
                is ComicPageSource.Network -> pageSource.url
                // 动态页先解析出真实地址再取图
                is ComicPageSource.DynamicNetwork -> resolveDynamicPageUrl(context, com.venera.compose.source.ComicSourceManager.getInstance(context), pageSource)
                is ComicPageSource.LocalFile -> pageSource.file.absolutePath
                else -> null
            }
            if (urlOrFile.isNullOrBlank()) {
                // 地址都没解析出来 —— 说清楚，别静默返回让人以为"点了没反应"。
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "插图收藏失败：这一页还没有可用的图片地址", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            var localPath = if (pageSource is ComicPageSource.LocalFile) urlOrFile else ""
            if (localPath.isBlank()) {
                // 不 recycle：这份位图就是 Coil 内存缓存里的那一个（阅读器正在显示它），
                // 回收它等于把缓存里的图挖掉，下一页翻回来就是"使用已回收位图"。
                decodePageAtOriginalSize(context, urlOrFile)?.let { bitmap ->
                    localPath = manager.persistPage(bitmap, session.comicId, pageIndex) ?: ""
                }
            }
            val rowId = manager.addFavorite(
                comicId = session.comicId,
                comicTitle = session.comicTitle,
                sourceName = session.sourceName,
                chapterTitle = chapterTitle,
                pageIndex = pageIndex,
                imageUrl = urlOrFile,
                localPath = localPath
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    when {
                        // addFavorite 失败返回 -1（它内部吞异常），这里不判就是假成功。
                        rowId < 0 -> "插图收藏失败：写入收藏表未成功"
                        localPath.isBlank() ->
                            "已收藏，但原画没能存下来：离线或该源地址失效时这一条会显示不出"
                        else -> "已收藏当前单页至「插图收藏」"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } catch (e: Exception) {
            // 静默失败会被读成"点了没反应"，进而判成假按钮 —— 存不下来必须说。
            val reason = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "插图收藏失败：$reason", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * 按**原始尺寸**取当前页的位图（收藏落盘专用）。
 *
 * `Size.ORIGINAL` 是这条的关键：请求盒子决定去混淆时的降采样倍数，盒子越大降得越少，
 * ORIGINAL 直接不降 —— 存下来的就是与服务端切块边界完全对齐的那一份。
 * 另外位图短路只交 Bitmap 给 Coil，拿不到硬件位图就得 allowHardware(false) 才能 compress。
 */
private suspend fun decodePageAtOriginalSize(context: Context, urlOrFile: String): Bitmap? =
    runCatching {
        val request = ImageRequest.Builder(context)
            .data(urlOrFile)
            .size(coil3.size.Size.ORIGINAL)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        (result.image as? coil3.BitmapImage)?.bitmap ?: (result.image as? BitmapDrawable)?.bitmap
    }.getOrNull()

/**
 * 分享当前页**图片**：从 Coil 缓存解码 bitmap → 写入 cacheDir 经 FileProvider 暴露 →
 * ACTION_SEND image/jpeg（StreamImagePayload）。附文案标题。
 * 动态页复用 resolveDynamicPageUrl 解析（带缓存），LocalFile 直接读文件。
 */
private fun shareCurrentImage(
    context: Context,
    pageSource: ComicPageSource?,
    comicTitle: String,
    chapterTitle: String,
    pageNumber: Int
) {
    if (pageSource == null) return
    val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    coroutineScope.launch {
        try {
            val urlOrFile = when (pageSource) {
                is ComicPageSource.Network -> pageSource.url
                is ComicPageSource.DynamicNetwork ->
                    resolveDynamicPageUrl(context, ComicSourceManager.getInstance(context), pageSource)
                is ComicPageSource.LocalFile -> pageSource.file.absolutePath
                else -> null
            }
            if (urlOrFile == null) return@launch

            val req = ImageRequest.Builder(context)
                .data(urlOrFile)
                .allowHardware(false) // 分享前要 compress，必须拿到软件位图
                .build()
            val result = context.imageLoader.execute(req)
            val bitmap = (result as? SuccessResult)?.image.let { img ->
                (img as? coil3.BitmapImage)?.bitmap ?: (img as? BitmapDrawable)?.bitmap
            } ?: return@launch

            // 写入 cacheDir/shared_images，经 FileProvider 授予读权限
            val shareDir = File(context.cacheDir, "shared_images").apply { if (!exists()) mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFile = File(shareDir, "Venera_${stamp}_p$pageNumber.jpg")
            imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }

            val sharedUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                imageFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, sharedUri)
                putExtra(Intent.EXTRA_SUBJECT, "$comicTitle - $chapterTitle")
                putExtra(Intent.EXTRA_TEXT, "《$comicTitle》$chapterTitle 第 $pageNumber 页\n来自 Venera 漫画阅读器")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "分享漫画单页"))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * 阅读器加载占位的波浪圆环（M3 Expressive）。
 *
 * 收敛成一处而不是 6 份重复调用：阅读器所有加载态都压在 [StatusColors.BadgeSurface]
 * 这块固定深色底板上，轨道色必须是浅色半透，散着写迟早有一份配错。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderWavyIndicator(modifier: Modifier = Modifier.size(32.dp)) {
    CircularWavyProgressIndicator(
        modifier = modifier,
        color = MiuixTheme.colorScheme.primary,
        trackColor = StatusColors.OnBadgeSurface.copy(alpha = 0.22f),
    )
}
