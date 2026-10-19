package com.venera.compose.gallery.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import com.venera.compose.components.venera.VeneraIconButtonTouchSize
import com.venera.compose.components.VeneraEmptyTone
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.venera.VeneraShimmer
import com.venera.compose.components.venera.veneraCoverCornerRadius
import com.venera.compose.data.platform.CacheDirs
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.gallery.data.GalleryConnectivity
import com.venera.compose.gallery.data.allowAnimatedImage
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySaver
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.domain.GalleryAutoPlay
import com.venera.compose.gallery.domain.GalleryExitGestureCommand
import com.venera.compose.gallery.domain.GalleryExitGestureEvent
import com.venera.compose.gallery.domain.GalleryExitGesturePhase
import com.venera.compose.gallery.domain.GalleryGuard
import com.venera.compose.gallery.domain.GalleryMotion
import com.venera.compose.gallery.domain.GalleryPreload
import com.venera.compose.gallery.domain.GalleryVolumeKeys
import com.venera.compose.gallery.watercolor.WatercolorPainterScreen
import com.venera.compose.gallery.domain.exitBodyAlpha
import com.venera.compose.gallery.domain.exitPageAlpha
import com.venera.compose.gallery.domain.galleryArtistNames
import com.venera.compose.gallery.domain.handoffBodyAlpha
import com.venera.compose.gallery.domain.handoffPageAlpha
import com.venera.compose.gallery.domain.nextExitGesture
import com.venera.compose.gallery.domain.watercolorEntryVisible
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlin.math.roundToInt
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import top.yukonga.miuix.kmp.basic.Text
import com.venera.compose.ui.tokens.LocalBottomBarClearance

/**
 * 画廊的**满屏播放器**（2026-09-25 改版，照 Breadboard 的两张真机截图，
 * 方案见 `gallery-viewer-toolbar-and-infosheet-2026-09.md`）。
 *
 * 这一层**吃掉了原来的二级 + 三级**：以前二级是"large 档 + 两张往下滚的卡"、
 * 三级是"file_url + 缩放"的黑底 overlay。二级一旦满屏，它和三级就只剩"多一个 HD 钮"的差别，
 * 而守卫 blur、进度环、chrome 那套判定会变成两份实现 ——
 * 今天刚因为"一级与二级各算一套"修过一次分叉（方案 §十四），不再留第二份。
 * 所以：图满屏、`HD` 钮负责 `large ↔ file` 换档、信息与标签收进 `(i)` 拉起的
 * [GalleryInfoSheet]，`GalleryFullViewer.kt` 删除。
 *
 * 背景**不在这里画**：这一页在独立 Activity 里，窗口透，后面那一屏由系统 blur-behind 实时糊
 * 顶栏整条去掉（用户 2026-09-25：要"图悬浮在页面上"的感觉），
 * 四个动作全收进底部一条 Dock（[GalleryViewerToolbar]）。
 * 原来顶栏上的"在站点打开"没丢，搬进了 [GalleryInfoSheet] 那一行。
 *
 * **左右翻页**（用户 2026-09-26 新增）：点开的那一面墙里有哪些条目，由 [GalleryViewerQueue]
 * 在点击那一刻交过来，屏上就是一个 [HorizontalPager] —— 横着翻页、竖着仍是下滑关闭，
 * 两条手势各占一个方向，互不干扰（放大之后 telephoto 会吃掉单指拖拽，翻页自然让位）。
 * 没有同墙上下文时（深链进来、上一轮列表已换）就是"只有一页的 pager"，
 * 渲染与手势仍是同一份代码，只是页数不同。
 *
 * 预测式返回**不在这里挂 handler**：本页是路由目的地，那条链归 NavHost 的 seekable 机制
 * （口径见 `components/PredictiveBack.kt` 的注释），这里再挂一个会抢同一次返回手势。
 */
@Composable
fun GalleryPostScreen(
    site: GallerySite,
    postId: Long,
    onBack: () -> Unit,
    /**
     * 画师行点头像 + 名字：进介绍页。参数是（画师名，这张图的出处）。
     *
     * 出处要一起交：介绍页在外链缺档时拿它补一枚入口、在头像缺档时从它派生 fanbox /
     * Mastodon 的公开端点（批次 L 那条"站方登记 > 出处"的优先次序），而只有本页知道当前这张图。
     */
    onOpenArtist: (String, String) -> Unit,
    /**
     * 本页**开始入场动作**的那一刻（图起飞 / 整页抬起，两者之一）—— 此刻才允许摘掉"窗口是透明的"这件事。
     *
     * 宿主拿它去挂 blur-behind。为什么不能像别页那样 `onStart` 就挂：那一层糊盖着的正是
     * 后面那屏主界面，第一帧就挂等于**新窗口一出现就把后面糊掉** —— 2026-10-01 12:26 录屏
     * 逐帧量出来的那一下"闪"就是它（详见 [backdropEntryAlpha]）。
     */
    onEnterStart: () -> Unit = {},
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val imageLoader: ImageLoader = remember { GalleryImageLoader.get(context) }
    val guard = remember { GalleryPorts.of(context).contentGuard }
    val maskMode by guard.nsfwMaskMode.collectAsState()
    // 浏览历史件（2026-10-14 起记「看过」）。可空：桌面那一侧不接这一颗（它有自己的历史屏），
    // 而这一屏是两端共享的源码文件 —— 空着就是不记，不是报错。
    val history = remember { GalleryPorts.of(context).history }

    // ── 大图页的四条行为档位（批次 C1，2026-09-29；方案 §一）──
    // 全都在 `GalleryViewerPolicies` 里算，这里只读档 + 接线：那三条判据要能上单测。
    val prefs = remember { GalleryPorts.of(context).prefs }
    val keepScreenOn by prefs.galleryKeepScreenOn.collectAsState()
    val volumeKeyTurn by prefs.galleryVolumeKeyTurn.collectAsState()
    val autoPlaySec by prefs.galleryAutoPlaySec.collectAsState()
    val preloadMode by prefs.galleryPreload.collectAsState()
    // ── 批次 C2：动图该不该解动画、背景那一层画什么 ──
    val animatedMode by prefs.galleryAnimated.collectAsState()
    val backdrop by prefs.galleryBackdrop.collectAsState()
    // 「画成水彩」Beta：默认关（图廊设置里开）。关着时底栏不摆那枚笔刷钮，
    // 连着连播/信息两颗的位置都不动（GalleryViewerToolbar 留的是空位不是灰钮）。
    val watercolorBeta by prefs.galleryWatercolorBeta.collectAsState()
    /**
     * 网络读数**只在这一屏打开时问一次**。
     * 逐张问的后果是同一面墙里忽动忽静（翻到第 3 张时切了网络），那是最难解释的一种表现；
     * 而"重进大图页才按新网络判"这条边界用户看得见、也说得清。
     */
    val unmetered = remember { GalleryConnectivity.isUnmetered(context) }
    val animateGifs = GalleryMotion.animates(animatedMode, unmetered)
    val focusRequester = remember { FocusRequester() }

    // 底栏那颗 ▶ 调的是**本次这一屏**的速度：进页时取偏好的默认值，页内改动不写回偏好
    // （用户 2026-09-29 拍板：随手一调不该污染设置里的默认值）。所以用 remember 而不是 saveable
    // —— 退出大图页就丢，重进回到「设置 → 画廊 → 大图页」那一条。
    var autoPlaySecNow by remember(site.name, postId) { mutableIntStateOf(autoPlaySec) }
    var autoPlayLastSec by remember(site.name, postId) {
        mutableIntStateOf(if (autoPlaySec > 0) autoPlaySec else AUTOPLAY_DEFAULT_SEC)
    }

    // ── 左右翻页的上下文（用户 2026-09-26；页轴那一版见 2026-10-18）──
    // 两种"墙"各归各的交接件，**页轴优先**：
    // - [GalleryPageAxis] = 这一条作品自己的几页（pixiv 作品页点图进来，用户点名"不能切换其他作品"）；
    // - [GalleryViewerQueue] = 把我送进来的那面邻居墙（点开哪张翻哪张）。
    // 从前只有一颗，两件事就会互相 `set` 掉：作品页把「相关图片」写进邻居墙那一刻，
    // 播放器正拿着它翻页 —— 所以这里分成两颗，而不是在同一颗里加一枚类型旗标。
    // 认不出（深链、或列表已换过一轮）就退回单张模式，这一条自己去站方取。
    val axis = remember(site, postId) { GalleryPageAxis.peek(site, postId) }
    val wall = remember(site, postId) { axis?.pages ?: GalleryViewerQueue.snapshot(site, postId) }
    val initialIndex = remember(wall, site, postId) {
        axis?.startIndex?.coerceIn(0, (wall.size - 1).coerceAtLeast(0))
            ?: wall.indexOfFirst { it.site == site && it.id == postId }.coerceAtLeast(0)
    }
    /**
     * **整屏档**：图吃整扇窗口，不再被"顶部让开状态栏 / 左右各一枚 `screenHorizontal` / 底部让开
     * 底栏净空"三面夹住（用户 2026-10-18：「放大的时候应该是像图三那样全屏放大」）。
     *
     * 判据是"这一屏翻的是不是同一作的几页"（页轴），**不是入口姓什么**：只有作品页那条路会写
     * [GalleryPageAxis]，而那条路上点开的正是详情页里**已经整屏宽**的那一页 —— 三面留白把它夹回
     * 一张悬浮卡，屏上就成了"点开反而小一圈、上下还挂着玻璃带"。
     *
     * 图库三站（邻居墙、深链、单张补取）继续走留白那一档：2026-09-25 定的「图悬浮在玻璃上」不动。
     * 顺带这一档把放大的天花板顶到整屏 —— [galleryCoverZoomFactor] 吃的就是这一框的视口，
     * 视口被夹小，`covered` 那一档就跟着够不着全屏（2026-10-15 那一轮的同一条因，只是这次在留白上）。
     */
    val fullBleed = axis != null
    // 页状态只此一份：单张模式 = "只有一页的 pager"，翻页模式 = 整面墙。
    // 一套渲染、一套手势，不必为两种入口各写一份（差别只在页数、以及要不要发那笔补取）。
    var pages by remember(site, postId) { mutableStateOf(wall) }

    var error by remember(site, postId) { mutableStateOf<String?>(null) }
    // 「重试」的凭据：只在点击时 +1，让下面的 effect 重新发一次请求。
    var retryTick by remember(site, postId) { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(initialPage = initialIndex) { pages.size }

    var showChrome by rememberSaveable(site.name, postId) { mutableStateOf(true) }
    var infoOpen by rememberSaveable(site.name, postId) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    /** 分享在途：视频原片可能几十 MB，防重复点击并发下载同一份。 */
    var sharing by remember { mutableStateOf(false) }
    // ── 水彩绘制浮层：非空即"正在画这张图" ──
    // 它盖住整页，所以连播（信息面板开着不翻的那一条判据同款）、
    // 音量键翻页都以"浮层开着"为前提让位 —— 人在看画的时候不该被翻走。
    var painterPost by remember(site.name, postId) { mutableStateOf<GalleryPost?>(null) }
    // 逐张记 HD。用 `ArrayList<String>` 而不是 `mutableStateMapOf`：map 那套进不了
    // `rememberSaveable`（Bundle 认的是 Serializable / Parcelable 这一族），旋转一下就丢，
    // 而"翻回来 HD 还亮着"是这张图上唯一可见的档位状态，不该丢。
    var hdUids by rememberSaveable(site.name, postId) { mutableStateOf(ArrayList<String>()) }
    // 哪几张正处在缩放态（工具条与页码据此让位）。只在**跨过 1x 的那一瞬间**写一次 ——
    // 每帧写会让整页跟着每帧重组，而这件事一秒只可能发生一两次。
    val zoomedPages = remember(site, postId) { mutableStateMapOf<String, Boolean>() }

    val current = pages.getOrNull(pagerState.currentPage)
    val currentUid = current?.uid
    // HD 与放大态按**逐页身份**记（页轴里四页共用一个 uid：按 uid 记就是"第 1 页开了 HD，
    // 第 2 页的钮也亮着而画的是中档"）。非页轴那些 `viewerKey` 逐字等于 `uid`，三站零变化。
    val currentViewerKey = current?.viewerKey
    val preferHd = currentViewerKey != null && hdUids.contains(currentViewerKey)
    val zoomedIn = currentViewerKey != null && zoomedPages[currentViewerKey] == true

    // ── 屏幕常亮（C1.1）：口径逐字照抄阅读器那段（VeneraReaderScreen 的常亮块）。 ──
    // 没有算式可抽，所以这一条**没有单测点**：验点只能在真机上停一张图等它灭。
    DisposableEffect(keepScreenOn) {
        val window = (view.context as? Activity)?.window
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // 音量键要走进来，得先有焦点。弹层开着的时候不抢 —— 那会儿按键该归弹层。
    LaunchedEffect(infoOpen) { if (!infoOpen) focusRequester.requestFocus() }

    // ── 连播进度线（§一）──
    // 它与计时**同一条钟**（见下面那个 effect 的头注）：线走到头的那一刻就是翻页那一刻。
    // 它每帧都变，所以只读进绘制 lambda（`GalleryAutoPlayProgressLine(progress = { … })`），
    // 不在组合期取值 —— 取值会让整页每帧重走一遍组合。
    val autoPlayProgress = remember(site.name, postId) { Animatable(0f) }
    // 摆不摆这条线，判据与 `shouldAdvance` **完全一致**：不会翻的时候摆线就是说假话
    // （视频页 / 放大中 / 弹层开着 / 已经到最后一张）。
    val autoPlayRunning = GalleryAutoPlay.shouldAdvance(
        seconds = autoPlaySecNow,
        infoOpen = infoOpen || painterPost != null,
        isVideo = current?.isVideo == true,
        zoomed = zoomedIn,
        currentPage = pagerState.currentPage,
        pageCount = pages.size,
    )

    // ── 自动连播（C1.3）：key 带着当前页 = 翻一页计时器自己重来，不做"暂停后恢复"那份额外状态 ──
    // **等待与进度线是同一条钟**（§一，2026-10-01）：老写法是 `delay()` 走一条、屏上那条线再走一条，
    // 两条钟各自积分，线走到头与真正翻页之间就会漂。大图页 hero 那一轮刚栽过一次同形的坑
    // （交叉淡入挂在弹簧的百分比上 —— 弹簧的百分比不是路程，见 gallery-hero-transition-*.md §十七），
    // 所以这里一开始就只留一条：等待本身就是这条动画，线走到头就是翻页那一刻。
    LaunchedEffect(pagerState.currentPage, autoPlaySecNow, infoOpen, painterPost != null, zoomedIn, current?.isVideo, pages.size) {
        // 每条出口都必须把线归零：留在半路的线读起来是"暂停在六成"，
        // 而它其实已经重来或不走了。
        if (autoPlaySecNow <= 0) {
            autoPlayProgress.snapTo(0f)
            return@LaunchedEffect
        }
        // **起线之前先判一次**：不会翻的那几档（视频页 / 放大中 / 弹层开着 / 水彩浮层开着 / 到底了）线根本不该出现 ——
        // 起一条"还有 5 秒翻页"的线却永远不翻，比不摆线更坏。
        if (!GalleryAutoPlay.shouldAdvance(
                seconds = autoPlaySecNow,
                infoOpen = infoOpen || painterPost != null,
                isVideo = current?.isVideo == true,
                zoomed = zoomedIn,
                currentPage = pagerState.currentPage,
                pageCount = pages.size,
            )) {
            autoPlayProgress.snapTo(0f)
            return@LaunchedEffect
        }
        autoPlayProgress.snapTo(0f)
        autoPlayProgress.animateTo(
            targetValue = 1f,
            // 线性：它是计时器不是缓动 —— 这条线撒谎的地方只有一处，就是"还有多久"。
            animationSpec = tween(durationMillis = autoPlaySecNow * 1000, easing = LinearEasing),
        )
        // 等着的这几秒里人可能已经放大、打开弹层、或自己翻走了 —— 走之前**再判一次**，
        // 按旧读数把人翻走是最难查的那类"它自己在动"。
        // 上面那一判管"起不起线"，这一判管"翻不翻"（key 覆盖不到的状态由它兜底）。
        if (!GalleryAutoPlay.shouldAdvance(
                seconds = autoPlaySecNow,
                infoOpen = infoOpen,
                isVideo = current?.isVideo == true,
                zoomed = zoomedIn,
                currentPage = pagerState.currentPage,
                pageCount = pages.size,
            )) return@LaunchedEffect
        // 翻页这一步**不能跑在本 effect 的协程里**：`currentPage` 与 `current?.isVideo` 都是本 effect
        // 的 key，而动画滚过 50% 那一刻它们就变 → effect 重启 → 正在跑的 animateScrollToPage 被取消
        // → pager 冻在两页中间（2026-09-29 真机读数：卡片间一大片空白、谁都没贴边）。
        // 交给页面作用域的 scope：计时归 effect（key 变就重来），动画归 scope（跑完为止）。
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
    }

    // ── 智能预加载（C1.4）：主动把前后那几页的 large 档拉进缓存 ──
    // 必须复用 galleryLargeRequest 而不是自造一份请求：它同时带着 memory/disk 两个 cacheKey
    // （视频条目还会换成 `poster` 那个键）。自造 = 预加载与显示两个键 = 白下一遍。
    LaunchedEffect(pagerState.currentPage, pages, preloadMode, animateGifs) {
        GalleryPreload.plan(preloadMode, pagerState.currentPage, pages.size).forEach { index ->
            val post = pages.getOrNull(index) ?: return@forEach
            imageLoader.enqueue(galleryLargeRequest(context, post, animateGifs))
        }
    }

    // ── 「关于这张图」要的三份额外数据：站方分类 / 画师兜底 / 标签译名 ──
    // 为什么不能像别的字段一样在解析期就挂在 GalleryPost 上：两站的 post JSON **都不给分类**
    // （实测 yande.re 44 个键里没有、Gelbooru 只有一串平铺 tags），站方对"这张画"的判定
    // 只在它那张帖的 HTML 上 —— 而那一笔只在用户真的打开面板时才值得发（理由见 GalleryTagCategories）。
    // 键取 currentUid：翻到下一张重取，翻回来由那一层 LruCache 接住，不会再发。
    var tagCategories by remember(currentUid) { mutableStateOf<Map<String, Int>?>(null) }
    var fallbackArtists by remember(currentUid) { mutableStateOf<Set<String>>(emptySet()) }
    var tagTranslations by remember(currentUid) { mutableStateOf<Map<String, String>>(emptyMap()) }

    /*
     * 离线那两样**不等面板**：词典在本地（SQLite 查一趟），早取早显示，一分请求都不花。
     *
     * 为什么这一半要提前：**画师的外链与头像现在由这一页在打开时取**（用户 2026-10-01 第 2 条），
     * 而"哪几个名字算画师"是取数的输入 —— 站方分类那一笔要等面板开，词典这份不用。
     * 两套判据混用是安全的：兜底表只兜画师一栏，且"判成画师而站方不判"实测 0 例，
     * 所以先按词典取一轮，站方分类回来后再把多出来的那几位补上（下面第三个 effect）。
     */
    LaunchedEffect(currentUid) {
        val post = current ?: return@LaunchedEffect
        val dictionary = GalleryPorts.of(context).lexicon
        tagTranslations = dictionary.translations(post.tagList)
        fallbackArtists = dictionary.artistNames(post.tagList)
    }

    // 站方分类那一笔仍然**只在你真要那一页信息时才发**：一次翻页浏览都只为了看画，
    // 没有谁在等一个分组。
    LaunchedEffect(currentUid, infoOpen) {
        val post = current ?: return@LaunchedEffect
        if (!infoOpen) return@LaunchedEffect
        val categories = GalleryPorts.of(context).lexicon.fetchTagCategories(post.pageUrl)
        tagCategories = categories
        // **兜底只在站方判定没拿到时才参与**：拿到分类就把词典那一份撤掉（两者的作用域必须互斥，
        // 否则两套判据会同时进 [galleryArtistNames]，标签墙里会出现"站方判通用、词典判画师"这种谁都解释不了的分组）。
        // 离线那一次已经把 `tagTranslations` 备好了，这里只管分类与"撤兜底"。
        if (!categories.isNullOrEmpty()) fallbackArtists = emptySet()
    }

    /*
     * ── 画师署名：**打开这一张图时就取**（用户 2026-10-01 第 2 条）──
     *
     * 取什么由 [galleryArtistNames] 决定 —— 与信息卡里那一块**同一个函数**
     * （两处各写一遍"谁算画师"迟早会对不上：那边会多出几个没取过的名字，面板一开又是当场等）。
     * 打开时它先按词典那几位开跑（离线、即时），站方分类回来之后 key 变了、effect 重启，
     * 补取多出来的那几位 —— 已经进过缓存的名字不会重发。
     */
    val artistNames = remember(current, tagCategories, fallbackArtists) {
        current?.let { galleryArtistNames(it.tagList, tagCategories, fallbackArtists) }.orEmpty()
    }
    var artistCredits by remember(currentUid) { mutableStateOf<Map<String, ArtistCredits>>(emptyMap()) }
    LaunchedEffect(currentUid, artistNames) {
        val post = current ?: return@LaunchedEffect
        artistNames.forEach { name ->
            val cached = GalleryArtistCreditsCache.peek(post.site, name, post.source)
            if (cached != null) {
                artistCredits = artistCredits + (name to cached)
                return@forEach
            }
            val fetched = loadArtistCredits(context, post.site, name, post.source)
            GalleryArtistCreditsCache.remember(post.site, name, post.source, fetched)
            artistCredits = artistCredits + (name to fetched)
        }
    }

    // ── 收藏 ──
    // 直接读那份存档（不另存一份本地状态）：收藏页与这里读的是同一个 StateFlow，
    // 所以"在这一页取消、回收藏页还亮着"不可能发生。存档只有几百条，`any` 是遍历几十项。
    val favoritesStore = remember { GalleryPorts.of(context).favorites }
    val favorites by favoritesStore.favorites.collectAsState()

    // ── 下滑关闭 ──
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val offsetY = remember(site, postId) { Animatable(0f) }
    var dismissing by remember(site, postId) { mutableStateOf(false) }
    val draggableState = rememberDraggableState { delta ->
        scope.launch { offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f)) }
    }

    fun closePage() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            offsetY.animateTo(screenHeightPx, spring(stiffness = Spring.StiffnessMediumLow))
            onBack()
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // 底换成深色玻璃之后，系统两条栏的图标要跟着翻成浅色：浅色主题下全局那处
    // （`feature/VeneraTheme.kt:116`）会把图标设成深色，压在黑玻璃上就看不见了。
    // 那个 SideEffect 只在主题变化时跑，不会替本页翻回来，所以本页进出各设一次。
    val isDarkTheme = LocalVeneraDarkTheme.current
    DisposableEffect(view, isDarkTheme) {
        val controller = (view.context as? Activity)?.window
            ?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = !isDarkTheme
            controller?.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    // ── 进场：卡片那一帧**飞**到大图的位置（2026-09-29 第四轮，用户点名要共享元素）──
    // 平台没有跨 activity 的共享图像 API（实测本机 android.jar 33~36 里只有启动图那套），
    // 所以这里自己把它的两样输入递过来：起点那一帧（[GalleryFlyIn.payload]）+ 起点矩形
    // （[GalleryFlyIn.origin]），落点由画面那一框回报（[GalleryViewerPage] 的 onImageBounds）。
    // 到位后淡出、交棒给画面框里那一帧占位，再被真图盖掉 —— 全程没有空帧。
    //
    // 原来的"整页从屏幕下沿抬上来"在这种形状下会与之抢戏（两套动作并行就是散，
    // 漫画侧换 shared axis 时同一条理由），所以能飞的时候**不抬页**，只让内容淡进来。
    // 拿不到起点矩形（收藏页、反搜那些没有"墙上那一张卡"的入口）时**照旧抬页** ——
    // 退路必须存在，不能因为飞不起来就白屏。
    val cardFrame = GalleryFlyIn.payload
    val flyOrigin = GalleryFlyIn.origin
    /** 两样输入齐了才飞（缺一样就退回抬页那一档）。 */
    val canFly = cardFrame != null && flyOrigin != null
    /**
     * **每一页**画面框的窗口矩形：`uid → Rect`。
     *
     * 为什么不是一份：去程要的是"打开那一张"的落点，而返回程要的是"**当前页**"的起点 ——
     * 用户翻过页之后这两个已经不是同一张（框的尺寸还随图的比例变）。pager 会预组左右邻页，
     * 所以各页都会陆续回报，按**逐页身份键**（[GalleryPost.viewerKey]）存才分得清谁是谁。
     *
     * ⚠️ 键不许退回 `uid`：页轴那一屏里一个作品的四页**共用同一个 uid**（uid 说的是"这一条作品"，
     * 收藏与历史按它记是对的），四页各自回报的画面框盖进同一个键，去程落点就成了邻页那一框 ——
     * 邻页比例与这一页不同 ⇒ 飞到位还差一截（用户 2026-10-18 报的"进大图错位很严重"里的一路）。
     * 非页轴那些 `viewerKey` 逐字等于 `uid`，图库三站一个像素都不受影响。
     */
    val frameBounds = remember(site, postId) { mutableStateMapOf<String, Rect>() }
    /**
     * 打开时那一张的**逐页身份键** —— 去程的落点、以及"垫帧只垫它"那几条都按它认。
     *
     * ⚠️ 认不出页轴时**退回路由里那一条**（`站点键:id`，与 [GalleryViewerQueue] 认领时同一条算式），
     * 不能写成 `wall.getOrNull(initialIndex)?.uid` —— 那一串在**没交队列的入口**上是空列表，
     * 于是它成了 `null` ⇒ 落点那一路 `await` 的对象永远不存在 ⇒ 吃满超时预算、一路退回
     * "整页抬上来"。用户 2026-10-01 第 4 条报的"收藏页点图没有飞入、返回却有"就是这一条：
     * 返回程查的是**当前这张**的键（[GalleryFlyIn.cardBoundsOf]），与有没有交队列无关，
     * 所以那一半一直是好的 —— **两半的判据不一致**才是真成因。
     *
     * 墙里认不认得出这一条，是 `pages` 那一层的事（认不出就退回单张模式、自己补取），
     * 与"这一页要打开哪一个键"无关：那是路由已经说死了的。
     */
    val initialViewerKey = remember(site, postId, axis) {
        axis?.pages?.getOrNull(initialIndex)?.viewerKey ?: "${site.routeKey}:$postId"
    }
    val entrance = remember { Animatable(1f) }
    val fly = remember { Animatable(0f) }
    /**
     * **交棒**那一档：飞行体已经落在画面框上之后，才轮到"页面铺满 + 图变清晰"。
     *
     * 为什么它必须**独立于 [fly]**、而且要等到飞完才开始（2026-10-01 第四次报"打开还是闪"，
     * 录屏逐帧量出来的）：老写法把页面的淡入挂在**弹簧的百分比**上（`fly ≥ 0.75` 起淡），
     * 可弹簧的百分比不是路程 —— `fly=0.75` 时飞行体还在**路程的 75%**（离落点约 130px、小 13%）。
     * 于是同一张图有两份同时可见：页面那份在终点、飞行体那份还在半路，读起来是**重影**；
     * 而两层 alpha 相乘后总不透明度只有 `0.75`（`a_page + a_body·(1-a_page)` 在 a=0.5 处取最小），
     * 图还会先**暗一下**再回来 —— 用户说的"图片本身闪一下"就是这两条。
     * 录屏读数：重影峰值落在 `fly=0.875`（交叉中点），与这条算式逐位吻合。
     *
     * 现在的口径：飞行全程**不淡任何东西**（飞行体就是不透明的图，它就是那一帧屏上像素）；
     * 落位之后再用这一档做两件事 ——
     * 前半程页面（含 chrome）淡进来（此刻图那一块被**不透明**的飞行体盖着，看不出页面在下面），
     * 后半程飞行体淡出（此刻页面已经不透明，总不透明度恒为 1，淡的只是"糊 → 清晰"）。
     * 见 [HANDOFF_PAGE_FRACTION]。
     */
    val handoff = remember { Animatable(1f) }
    var contentReady by remember(site, postId) { mutableStateOf(false) }
    /**
     * 入场姿态：[PENDING] 站在原位但全透明（等落点）→ [FLY] 飞进来 → 或 [RISE] 整页抬上来。
     * **只在 effect 里定一次，之后不再翻**。
     *
     * 两条真机读数各钉掉一种写法（2026-10-01，同一处连栽两次）：
     *
     * 1. 不能拿组合期派生的 `canFly` 当分支：飞行一结束就要把 payload 交还
     *    （[GalleryFlyIn.consume]），`canFly` 当场翻回 false，graphicsLayer 随即改走"抬页"那一支 ——
     *    可飞这一程根本没用过 `entrance`，它还停在 1f，于是整页（连底部那条栏）被平移出一屏，
     *    屏上只剩窗口的模糊。
     * 2. 等待落点的那一段**不能站在"抬页"姿态上**（`translationY = 一屏`）：
     *    `boundsInWindow()` 报的是**裁到窗口可见部分**之后的矩形，页面整片在屏幕外时
     *    落点就是 `0×0`（读数：`size=1016x1576` 恒定，而 window 高只有 99）。
     *    于是"等落点"永远等不到 —— 上一版把 0×0 当目的地（飞行体缩向屏幕左上角消失），
     *    这一版把 0×0 挡掉（于是永远不飞）。两条都是同一个姿态错误的两面。
     *    站在原位、只是不透明=0，落点第一帧就量得到。
     */
    var entranceMode by remember(site, postId) {
        mutableStateOf(if (flyOrigin == null) Entrance.RISE else Entrance.PENDING)
    }
    /**
     * 飞行结束（或压根不飞）：画面框里那一帧占位从这一刻起才接管。
     *
     * 起点只看**矩形**、不看像素：`capture` 里矩形是同步给的，而像素要等 `PixelCopy` 回来
     * （约一到数帧）。如果这里按"两样齐不齐"取初值，就会在像素到位前一帧把这一档定成
     * "压根不飞"，像素一到就直接在**落点**摆出那一帧 —— 飞行体根本没有起飞的时机，
     * 观感是"图先在原位闪一下"。有矩形就先按"要飞"摆，像素到了再起飞。
     */
    var flightDone by remember(site, postId) { mutableStateOf(flyOrigin == null) }
    /**
     * 交棒动画播完（或压根没有交棒这一档）。
     *
     * 它比 [flightDone] 晚 [HANDOFF_MS]：**起点那一帧（[GalleryFlyIn.payload]）必须活到这一刻**，
     * 因为页面铺满那半程还要靠它盖着图那一块，而它也是"糊 → 清晰"的淡出体本身。
     * 老写法在 `flightDone` 那一刻就 `consume()`，位图一清、这一档当场塌掉（真机读数：
     * `pad=true` 只活了 10ms），观感就是图上"闪一下"。
     */
    var handoffDone by remember(site, postId) { mutableStateOf(flyOrigin == null) }
    // ── 临时取证探针（与 capture 里那条同批，读数后一起撤）──
    LaunchedEffect(entranceMode, canFly, flyOrigin) {
        android.util.Log.i(
            "FlyProbe",
            "mode=$entranceMode canFly=$canFly frame=${cardFrame?.width}x${cardFrame?.height} " +
                "origin=$flyOrigin entrance=${entrance.value} fly=${fly.value}",
        )
    }
    LaunchedEffect(site, postId) {
        // 压根没有"墙上那一张卡"当起点（深链、反搜那些入口）：没有什么可等的，直接抬页。
        // 这一条短路存在的理由是**别让人家为一笔永远不会来的落点白等一笔预算**。
        // ⚠️ 判据只能是 [GalleryFlyIn.origin]（点击那一刻同步写的那个矩形），**不是"入口姓什么"**：
        // 收藏页曾经被写在这一串里（它当年确实没飞），但它今天与画廊一级走同一条路 ——
        // 交队列 + 截帧都做了，只是那一格（旧名 `initialUid`，现名 [initialViewerKey]）当时取的是墙里的那一条（空 → null），
        // 落点永远等不到（2026-10-01 第 4 条）。别再按入口名去猜这一条。
        if (GalleryFlyIn.origin == null) {
            flightDone = true
            handoffDone = true
            // 幕布要开始抬了，宿主可以去挂 blur-behind 了（[onEnterStart] 的头注）。
            onEnterStart()
            fly.snapTo(1f)
            entrance.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            return@LaunchedEffect
        }
        // ── 临时取证探针（2026-10-01 批次 R2，读数拿到后与 `GalleryFlyIn` 里那条一起撤）──
        val probeStart = android.os.SystemClock.elapsedRealtime()
        // 起点那一帧（异步）与落点（要等画面那一框量出来）**并发等**，共用同一笔预算：
        // 串着等最坏是两倍时长，而这两件事本来就没有先后关系。
        val frameJob = async {
            withTimeoutOrNull(FLY_WAIT_MS) {
                snapshotFlow { GalleryFlyIn.stage }
                    .first { it != GalleryFlyInStage.WAITING }
            }
        }
        val boundsJob = async {
            withTimeoutOrNull(FLY_WAIT_MS) {
                snapshotFlow { frameBounds[initialViewerKey] }.filterNotNull().first()
            }
        }
        frameJob.await()
        val target = boundsJob.await()
        android.util.Log.i(
            "FlyProbe",
            "stage=${GalleryFlyIn.stage} frame=${GalleryFlyIn.payload?.width}x${GalleryFlyIn.payload?.height} " +
                "origin=${flyOrigin} target=$target waited=${android.os.SystemClock.elapsedRealtime() - probeStart}ms",
        )
        val flying = GalleryFlyIn.payload != null && GalleryFlyIn.origin != null && target != null
        entrance.snapTo(1f)
        if (!flying) {
            // 退路：整页从屏幕下沿抬上来。两种"飞不起来"都归这里 —— 压根没有卡片当起点
            //（深链、反搜）、`PixelCopy` 没给像素、落点超过预算才量出来。
            // 半路把已经起飞的飞行体停在原地，比一开始就不飞难看。
            entranceMode = Entrance.RISE
            flightDone = true
            handoffDone = true
            onEnterStart()
            fly.snapTo(1f)
            entrance.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            return@LaunchedEffect
        }
        entranceMode = Entrance.FLY
        // **起飞这一刻**就把交棒档归零 —— 整条飞行程都不淡任何东西（飞行体就是不透明的起点像素）。
        // 漏了这一句的后果是反的：`handoff` 还停在上一档的 1，飞行体一开场就是全透明
        // （`handoffBodyAlpha(1) == 0`），屏上只剩幕布 —— 又是一次"闪"，而且是更黑的那种。
        handoff.snapTo(0f)
        // 起飞的这一刻才允许后面那屏变糊、变暗 —— 第一帧就更是那一下"闪"本身（见 [backdropEntryAlpha]）。
        onEnterStart()
        fly.snapTo(0f)
        // 抬页那一支的表达式读 `entrance`（默认停在 1f = 一整屏位移）。这一档虽然不再走它，
        // 但给它留一个 1f 的读数就是下一次翻车的引线 —— 归零，让各档互不牵连。
        entrance.snapTo(0f)
        // 与下滑关闭同一档弹簧：进与出手感对称，且不另造数字。
        fly.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
        android.util.Log.i("FlyProbe", "flight end at=${android.os.SystemClock.elapsedRealtime()}")
        flightDone = true
        // 飞完才开始交棒：此刻飞行体**正好压在画面框上**，这才是"两份图重合"的唯一时刻。
        // 页面早一帧淡入都不行（弹簧的百分比不是路程，见 [handoff] 的头注）。
        handoff.animateTo(1f, tween(durationMillis = HANDOFF_MS))
        handoffDone = true
    }
    // 占位那一帧**换到位才交还**：早交会在"页到位"与"图加载好"之间露出一块玻璃，
    // 观感就是闪一下。取不到详情时也要交 —— 不然这张位图一直攥在手里没人再消费它。
    //
    // ⚠️ 两道门，缺一次就是一次"闪"（两轮真机读数各钉掉一道）：
    // 1. **飞完**（[flightDone]）：这一页从墙上点进来时 `contentReady` 二十来毫秒就为真
    //    （卡片那张预览档本来就在 Coil 内存里），而飞行体还要用 `payload` 与 `origin`
    //    画完剩下的三百多毫秒。早交就是把飞行体半路抹掉 —— 真机读数停在 `fly=0.041`。
    // 2. **交棒也播完**（[handoffDone]，2026-10-01 第四次报"打开还是闪"）：交棒那半程还要靠
    //    这张位图盖着图那一块、并充当"糊 → 清晰"的淡出体。老写法只看 `flightDone`，
    //    于是 `consume()` 紧跟 `flightDone` 落地（真机读数：`pad=true` 只活了 10ms），
    //    一帧糊、又立刻变清晰 —— 那就是"图片本身闪一下"。
    //
    // 只认这两个 + 图到位，不再猜图什么时候好。`contentReady` 蕴含中档落位（见 `onReadyChange`），
    // 所以交还的那一刻垫帧本来就已经被撤了（`cardFrame != null && !largeSettled` 那条），
    // 位图清掉不会把画面抽空。
    LaunchedEffect(contentReady, error, handoffDone) {
        if (handoffDone && GalleryFlyIn.payload != null && (contentReady || error != null)) {
            android.util.Log.i("FlyProbe", "consume ready=$contentReady error=${error != null}")
            GalleryFlyIn.consume()
        }
    }
    // ── 临时取证探针（第二轮：查"飞行结束后那一下闪"是哪一层）──
    // 飞行体交棒之后屏上还能动的东西只有这四类：图换档、框里那份占位、加载态（骨架/环）、chrome。
    // 各自都有唯一指纹（下面三条 + GalleryViewerMedia 里的 tier 那条），时间线对齐就能定死是哪一类。
    LaunchedEffect(showChrome, zoomedIn, flightDone, contentReady, cardFrame != null, infoOpen) {
        android.util.Log.i(
            "FlyProbe",
            "state chrome=$showChrome zoomed=$zoomedIn flightDone=$flightDone ready=$contentReady " +
                "frame=${cardFrame != null} info=$infoOpen",
        )
    }

    // 单张模式才补取：从一面墙点进来时，那一批（含这一条）已经随点击交过来了，
    // 再打一笔 `tags=id:N` 是白跑 —— 而且它落地的时机还不定，反而可能把已经画好的页面盖一下。
    LaunchedEffect(site, postId, retryTick) {
        if (wall.isNotEmpty()) return@LaunchedEffect
        error = null
        // 两站各自一个客户端，取法同一套（都是 `tags=id:N`），这里按站点分支就够了 ——
        // 为一处分支抽"通用图库站接口"会把只有一站会用的参数（favCount）拖进抽象层。
        val result = when (site) {
            GallerySite.YANDERE -> GalleryPorts.of(context).boards.yandere.fetchById(postId)
            GallerySite.GELBOORU -> GalleryPorts.of(context).boards.gelbooru.fetchById(postId)
            GallerySite.SAFEBOORU -> GalleryPorts.of(context).boards.safebooru.fetchById(postId)
            // pixiv 走自己那三条腿（不是 board）：`/ajax/illust/{id}` 现量 200，五档图址齐。
            // 端口槽在桌面是 null ⇒ 那一侧交一句"桌面不接 pixiv"，而不是悄悄交回空。
            GallerySite.PIXIV -> GalleryPorts.of(context).pixiv?.fetchById(postId)
                ?: Result.failure(UnsupportedOperationException("这一侧不接 pixiv（桌面）"))
        }
        result.onSuccess { loaded ->
            // 取到了空 = 站方没有这条（被删/合并），要说话，不要留一屏黑。
            if (loaded == null) {
                error = "${site.displayName} 上没有 #$postId 这条（可能已被删除或合并）"
            } else {
                pages = listOf(loaded)
            }
        }.onFailure { e ->
            android.util.Log.w("VeneraDebug", "load gallery post failed", e)
            error = "这一条没读出来 🥲 稍后再试，或换一条"
        }
    }

    /**
     * 这一张要不要打码。与一级那面墙**同一把判据**（[GalleryGuard.maskStateFor] +
     * GalleryPorts.contentGuard.blockedGalleryRule）：大图页不能换一套匹配口径，
     * 否则会出现"墙上被挡掉、点进来却全裸"（或反之）的分叉。
     *
     * 从 val 改成"按页算"：左右翻之后屏上那张不再是打开时那张，判据必须跟着当前页走。
     */
    fun maskedOf(target: GalleryPost): Boolean = GalleryGuard.maskStateFor(
        post = target,
        mode = maskMode,
        blockedByUser = guard.blockedGalleryRule(
            author = target.author,
            tags = target.tagList,
        ) != null,
    ) == GalleryGuard.BLURRED

    // ── 浏览历史：看到的那一张记一行（2026-10-14，与画廊首页顶栏那枚「浏览历史」同一轮）──
    //
    // 写在大图页而不是七处开卡点（首页墙 / 日榜二级页 / 搜索墙 / 画师页 / 收藏页 / 反搜结果 / 深链）：
    // 那七条路径全在这一页汇流，一处写完覆盖全部；键取 currentUid，左右翻页每滑一张也各记一笔 ——
    // 划过去没停下来也算看过，这正是"浏览"二字的含义。
    //
    // 守卫照桌面批 86 那颗用例（「挡住的那一档不记」）：maskedOf 为真 = 分级遮罩或用户屏蔽规则
    // 把这张挡住了，用户没看见画，记一条「看过」就是假历史。
    LaunchedEffect(currentUid) {
        val post = current ?: return@LaunchedEffect
        if (maskedOf(post)) return@LaunchedEffect
        history?.recordView(post, System.currentTimeMillis())
    }

    // sheet 浮在上面时，系统返回先关 sheet，不要把本页一起弹掉。
    BackHandler(enabled = infoOpen) { infoOpen = false }

    // ── 返回程 hero：把画面那一框截下来，飞回墙上那张卡（用户 2026-10-01 点名要做）──
    // 与去程是同一套机制的镜像。去程的两样输入是点击那一刻递过来的；返回程自己现取：
    // **起点** = 画面框（[imageBounds]），**落点** = 墙上那张卡此刻的封面矩形
    // （[GalleryFlyIn.cardBoundsOf]，由一级那一侧布局时回写）。
    //
    // 为什么能直接读后台那一屏的落点：大图页压上来之后 MainActivity 只是 `onStop`，
    // View 树与布局原封不动（人也没法在大图页里滚那一面墙），所以那份读数就是"离开那一刻的位置"。
    //
    // 三条边界都是硬口径，任一条不成立就**照旧走系统那套返回动画**，绝不硬切：
    // 1. 打码这一张两边都不飞 —— 墙上那张卡因为打码压根没回写落点，这里天然查不到；
    // 2. 翻到墙外那一张、或卡片已被回收 → 查不到落点；
    // 3. 截不到像素（超出窗口、窗口没有 surface）。
    /**
     * 画面框（以及飞行体）的圆角。**必须在组合期取出来**：`VeneraTokens.shape` 是 @Composable 的
     * getter，绘制阶段（`drawBehind` 的 lambda）读不到它。`.toPx()` 留在 DrawScope 里做 ——
     * 那里才是 Density。
     */
    val flyCornerRadius = tokens.shape.large
    /**
     * **封面自己那圈圆角**（唯一出处见 [veneraCoverCornerRadius]）。去程飞行体与垫帧都要按它
     * 把位图四角那点"卡片面色"裁掉：位图截的是"卡片里封面那一块"，而封面四角不属于图。
     *
     * 与 [flyCornerRadius] 是**两件事**，不许互换：那一颗管"落到画面框时形状对得上"（画面框
     * 就是 `shape.large`），这一颗管"这块像素本身圆到哪儿"。旧写法两处都用前者 ⇒ `shape.large`
     * 比 `shape.medium` 松一档 ⇒ 四角露白（2026-10-16 真机图证）。
     */
    val coverCornerRadius = veneraCoverCornerRadius()
    var exitFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var exitStart by remember { mutableStateOf<Rect?>(null) }
    var exitTarget by remember { mutableStateOf<Rect?>(null) }
    val exitFly = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }

    /** 离场时整页（含压暗那一层）的透明度：**首程**淡出，算式在判据层（[exitPageAlpha]）。 */
    fun exitFadeAlpha(): Float =
        if (!leaving) 1f else exitPageAlpha(exitFly.value)

    /**
     * 入场时压暗那一层的透明度。**它不能第一帧就是 1** —— 这是 2026-10-01 第三次报"打开还是闪"
     * 之后，用录屏逐帧量出来的那条读数：
     *
     * ```
     * t=1.20~1.43s  lum=174.0  帧间差 0.00   ← 点击后主界面一动不动
     * t=1.450s      lum 173.8 → 106.1        ← 一帧之内暗掉 39%，就是用户看到的"闪"
     * t=1.45~1.78s  106 → 147  缓慢爬升       ← 图飞过来的 350ms
     * ```
     *
     * 那次暴跌既不在飞行体上、也不在交棒上（那两处上一轮已经修过，读数也对）：它就是
     * **新窗口的第一帧**。窗口一出现，幕布（0.45 的黑 + 32dp 的系统模糊）就整块盖下来，
     * 而图还要过两帧才起飞 —— 于是屏上先"空闪"一下，才有运动。
     *
     * 判据：**入场第一帧必须与点击前那一帧看起来一样**。所以幕布跟着图起飞一起渐入，
     * 起步那一下屏上还是原来那屏（只是它上面多出一张与卡片同位同像素的图，看不出来）。
     * 另一半在 [onEnterStart]：窗口模糊也一并推迟到这一刻（见 `VeneraSubActivityBase.armBlurBehind`）。
     */
    fun backdropEntryAlpha(): Float = when (entranceMode) {
        // 等落点/等像素这一段：后面那屏保持原样（清晰、不压暗），与点击前逐像素相同。
        Entrance.PENDING -> 0f
        // 飞这一程：幕布在图起飞的前段里落齐，与它共用同一条钟（不另起一条）。
        Entrance.FLY -> (fly.value / BACKDROP_ENTRY_FRACTION).coerceIn(0f, 1f)
        // 抬页那一档：`entrance` 1→0 是页面从屏外抬到位，幕布与它同程渐入。
        Entrance.RISE -> (1f - entrance.value).coerceIn(0f, 1f)
    }

    /**
     * 离场那两条路共用的守卫：当前页的**画面框**与**墙上那张卡的落点**。
     *
     * 交回 null = 这一张飞不了（打码那一张两边都不飞、量不到画面框、翻到墙外、卡片已被 LazyGrid 回收），
     * 调用方必须直接 finish —— 不许把"飞不起来"演成"飞完了"。
     */
    fun exitRectsOrNull(): Pair<Rect, Rect>? {
        val post = current ?: return null
        // 两半用**同一把键**（`viewerKey`）：起点是这一页自己回报的画面框，落点是"把我送进来
        // 那一侧"按同一把键回写的矩形。页轴里四页共用一个 uid，退回 uid 就是把邻页的框当这一页。
        val box = frameBounds[post.viewerKey] ?: return null
        val target = GalleryFlyIn.cardBoundsOf(post.viewerKey) ?: return null
        if (box.width <= 0f || box.height <= 0f || maskedOf(post)) return null
        return box to target
    }

    /**
     * 离场第一件事是**摘掉窗口模糊**：图要飞回的是清晰的那一屏，落在一张糊掉的卡片上不算"回去"。
     * 基类挂模糊走的是 addFlags，清掉即回到透明窗口底；状态栏图标同理跟着翻回来（底下那屏是浅色主题）。
     */
    fun clearChromeForExit() {
        val host = view.context as? Activity
        host?.window?.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        // 新字段也要归零：Android 12 起窗口背景模糊走 `setBackgroundBlurRadius` 这条独立字段，
        // 只摘 flag 在部分 ROM 上会留着一层糊（2026-10-19 作品页返回那条读数同因）。
        host?.window?.setBackgroundBlurRadius(0)
        host?.window?.let { WindowCompat.getInsetsController(it, view) }
            ?.isAppearanceLightStatusBars = !isDarkTheme
    }

    /**
     * 回弹收尾：玻璃底与状态栏图标放回去、取消"正在离开"、**把离场那三样输入清掉**。
     *
     * 它排在"视觉回到 0"那条命令**之后**才执行（决策层交回的指令是顺序跑的），
     * 所以这一刻画面已经站回原位，不会看见"背景先清晰、页面还在缩"的两步。
     *
     * 那三样必须清：批 2 之前 `leaving` 是单行道（本页必 finish），留着无所谓；现在回弹之后人还可能
     * 继续翻页，而那一层飞行体的挂载条件只看"三样齐不齐"—— 不清就是**旧那一张的截屏压在新那一张上面**。
     */
    fun restoreChromeAfterCancel() {
        leaving = false
        exitFrame = null
        exitStart = null
        exitTarget = null
        val host = view.context as? Activity
        host?.window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        // 与 [clearChromeForExit] 成对：摘的时候把新字段归零了，这里要放回去，
        // 否则"拖到一半松手回弹"之后这一屏的玻璃底就永久没了。
        // 半径不另写常量 —— `LayoutParams.blurBehindRadius` 那一位从头到尾没被动过，直接照抄回来。
        host?.window?.setBackgroundBlurRadius(host.window.attributes.blurBehindRadius)
        host?.window?.let { WindowCompat.getInsetsController(it, view) }
            ?.isAppearanceLightStatusBars = false
    }

    fun leavePage() {
        if (leaving) return
        val rects = exitRectsOrNull()
        if (rects == null) {
            onBack()
            return
        }
        val (box, target) = rects
        leaving = true
        clearChromeForExit()
        GalleryFlyIn.captureRegion(view, box) { frame, capturedRect ->
            if (frame == null || capturedRect == null) {
                onBack()
                return@captureRegion
            }
            exitFrame = frame
            // 起点用**钳到窗口后**的那一块（批 B §3 地基：像素与矩形同一块）。
            // 画面框现在是"没裁过"的位置那把，竖长条页会被钳短 —— 飞行体装的是钳后的像素，
            // 矩形也必须是钳后的那一块，否则第一帧就不是"逐像素相同"。
            exitStart = capturedRect
            exitTarget = target
            scope.launch {
                // ── 临时取证探针（与那 9 处 `FlyProbe` 同批，验收完一起撤）──
                android.util.Log.i("FlyProbe", "exit start=$box target=$target")
                // 与下滑关闭、进场同一档弹簧：进与出手感对称，且不另造数字。
                exitFly.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                android.util.Log.i("FlyProbe", "exit end")
                onBack()
            }
        }
    }

    // ── 跟手返回（批 2）──
    // 决策层在 `GalleryExitGesture`（四道门闩），视觉算式在 `GalleryExitExpansion`（批 1 抽出来的那颗）。
    // 这里只负责：把系统事件喂进去、按交回的指令顺序执行、不再自己判"这一笔该不该理"。
    var exitPhase by remember { mutableStateOf(GalleryExitGesturePhase.Idle) }
    /** 最近一次收到的手势进度：`Arming` 期间攒着，像素一到就补到手指已经拖到的那一档。 */
    var exitLastProgress by remember { mutableFloatStateOf(0f) }
    /**
     * 跟手期的高刚度弹簧。
     *
     * 不用 `snapTo`：澎湃 / HyperOS 这类机器在拖动期间只派发少量进度点，直接落位就是整页跳位。
     * 3000f 无回弹那一档是"几乎贴手指、稀疏时平滑追赶"，与仓内既有的跟手口径同源
     * （`components/PredictiveBack.kt` 的回弹用 600f，那是回弹不是跟手）。
     */
    val exitFollowSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 3000f,
        visibilityThreshold = 0.0005f,
    )

    fun onExitGestureEvent(event: GalleryExitGestureEvent) {
        val decision = nextExitGesture(exitPhase, exitLastProgress, event)
        exitPhase = decision.phase
        if (decision.commands.isEmpty()) return
        // 用组合那侧的 scope，不用手势事件自己的协程：回弹这一笔正是发生在事件协程被取消之后。
        scope.launch {
            for (command in decision.commands) {
                when (command) {
                    is GalleryExitGestureCommand.SetVisualTarget ->
                        exitFly.animateTo(command.target, exitFollowSpec)

                    // 备料（跟手那一趟）：守卫 + 摘玻璃底 + 起 `PixelCopy`。
                    // 像素回来之前画面**钉在 0**（决策层的 `Arming` 档），所以那几帧不会先空一下。
                    // 本地函数不能前向引用，这一条就地做完、只回调自己。
                    GalleryExitGestureCommand.Prepare -> {
                        val rects = exitRectsOrNull()
                        if (rects == null) {
                            // 飞不起来：交回失败那条事件，让决策层直接 finish（半路没有帧可画，别自己编一帧）。
                            onExitGestureEvent(GalleryExitGestureEvent.PixelsFailed)
                        } else {
                            val (box, target) = rects
                            leaving = true
                            clearChromeForExit()
                            GalleryFlyIn.captureRegion(view, box) { frame, capturedRect ->
                                if (frame == null || capturedRect == null) {
                                    android.util.Log.w("FlyProbe", "gesture exit: no pixels for $box")
                                    onExitGestureEvent(GalleryExitGestureEvent.PixelsFailed)
                                } else {
                                    exitFrame = frame
                                    // 起点 = 钳到窗口后那一块（与像素同块，批 B §3 地基）。
                                    exitStart = capturedRect
                                    exitTarget = target
                                    onExitGestureEvent(GalleryExitGestureEvent.PixelsReady)
                                }
                            }
                        }
                    }

                    GalleryExitGestureCommand.Restore -> restoreChromeAfterCancel()
                    GalleryExitGestureCommand.FlySelfDriven -> leavePage()
                    GalleryExitGestureCommand.Finish -> onBack()
                }
            }
        }
    }

    // 系统返回 / 侧滑手势：**跟手**。进度直接驱动离场那一帧，松手确认就落回卡片、回弹就原路退回。
    // 三键点返回与 `adb` 送按键**没有**进度事件，照旧走原来那趟自驱弹簧 —— 两条共用同一颗算式与同一份落点账本。
    // 代价如实记账：这一页自己接管之后，系统那套整窗口预览没有了（它给的是整扇窗，不是元素级）。
    // sheet 开着时那条优先级更高（enabled 互斥）。
    // ⚠️ 下滑关闭（[closePage]）**不走这条** —— 那时页面已经跟着手指滑出屏幕，
    // 再让图从屏幕外飞回来是自相矛盾；那条保持原样，由系统返回动画收尾。
    PredictiveBackHandler(enabled = !infoOpen) { events ->
        var receivedProgress = false
        try {
            events.collect { event ->
                exitLastProgress = event.progress
                val first = !receivedProgress
                receivedProgress = true
                onExitGestureEvent(
                    if (first) GalleryExitGestureEvent.Started(event.progress)
                    else GalleryExitGestureEvent.Progress(event.progress),
                )
            }
        } catch (cancelled: CancellationException) {
            // 平台在取消手势时撤掉这一笔协程：画面回原位、玻璃挂回来，然后把取消交回去。
            // 没有进度事件就没有动过画面（`Arming` 都没进），什么都不必做。
            if (receivedProgress) onExitGestureEvent(GalleryExitGestureEvent.Cancel)
            throw cancelled
        }
        onExitGestureEvent(
            if (receivedProgress) GalleryExitGestureEvent.Commit
            else GalleryExitGestureEvent.BackWithoutProgress,
        )
    }

    fun toggleChrome() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        showChrome = !showChrome
    }

    /**
     * 分享一条**原档**（图或视频）+ 单页链接。
     *
     * 动作本体在 [shareGalleryPost]（墙上长按共用，2026-10-04 从这一处搬出去的），
     * 三条口径与"失败必须出声"的理由都写在那颗的头注里。这里只留本宿主的三件事：
     * 震动、防重入的 [sharing] 旗（视频原片 16~26 MB，取档要真下载一次）、以及把旗收回。
     */
    fun share(target: GalleryPost) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (sharing) return
        sharing = true
        scope.launch {
            // 动作本体在 [shareGalleryPost]：墙上长按走的是同一份，别在这儿留第二份实现。
            shareGalleryPost(context, target)
            sharing = false
        }
    }

    fun download(target: GalleryPost) {
        if (saving) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        saving = true
        scope.launch {
            GallerySaver.save(context, target)
                .onSuccess {
                    // 两件事都要说：**存到哪了**（落点跟着"本地漫画存储路径"走，用户改过，
                    // 不报路径就只能靠猜）与**存了多大**（一条视频原片实测 16~26 MB，
                    // 不报数用户不知道刚才存了什么）。
                    Toast.makeText(
                        context,
                        "已保存 ${it.path} · ${"%.1f".format(it.bytesSaved / 1024.0 / 1024.0)} MB",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                .onFailure { e ->
                    android.util.Log.w("VeneraDebug", "save gallery post failed", e)
                    Toast.makeText(context, "保存没成功 🥲 看一眼存储空间再试一次", Toast.LENGTH_LONG).show()
                }
            saving = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ── 音量键翻页（C1.2）：焦点件与那对 modifier 照抄阅读器（:570-597）──
            // 关掉的时候 targetPage 返回 null → 这里回 false → 事件交回系统，
            // 所以"关"是真的关（按音量键还是音量条），不是半吃。
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                val isDown = event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                if (!isDown && event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
                    return@onKeyEvent false
                }
                val target = GalleryVolumeKeys.targetPage(
                    enabled = volumeKeyTurn,
                    isVolumeDown = isDown,
                    currentPage = pagerState.currentPage,
                    pageCount = pages.size,
                ) ?: return@onKeyEvent false
                scope.launch { pagerState.animateScrollToPage(target) }
                true
            },
    ) {
        // 背景那一层按档位画（批次 C2）：
        // - 现状（默认）= **不画东西**，这一页在独立 Activity 里、窗口是透的，
        //   系统 blur-behind 已经把后面那一屏一级列表实时糊好了（[GalleryPostActivity]），
        //   这里只补一层压暗 —— 只糊不压暗时，照片墙花花绿绿会把主体那张图吃掉。
        // - 纯黑 / 深灰 / 纯白 = 一层**不透明**底，它自然盖住窗口模糊，所以不需要在运行时
        //   去动 `FLAG_BLUR_BEHIND`（那半只在"从这三档改回现状"时才要重进页面才恢复模糊，
        //   这条如实写进了设置页文案）。
        // 这一层**不参与滑入**：玻璃是"后面那一屏"，页面向上抬时它当然不动，
        // 动的只有浮在上面的那些，这才是"图从玻璃后面升起来"的感觉。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 入场那半：跟着图起飞一起渐入（第一帧等于 0），见 [backdropEntryAlpha]。
                    // 离场那半：与页面同步让开 —— 它压暗的正是图要飞回去的那一屏（见 [leavePage]）。
                    // 两者相乘而不是各写一遍：进与出不会重叠，但乘起来两条路都不必假设对方的状态。
                    alpha = backdropEntryAlpha() * exitFadeAlpha()
                }
                .background(
                    backdrop.argb?.let { Color(it) }
                        ?: Color.Black.copy(alpha = BACKDROP_SCRIM_ALPHA)
                ),
        )

        Box(
            Modifier
                .fillMaxSize()
                // `entrance.value` / `fly.value` / `handoff.value` 在 graphicsLayer 的 lambda 里读
                // = 绘制阶段读，每帧只重绘不重组；拿到组合里读会让整页每帧重走一遍组合。
                .graphicsLayer {
                    when (entranceMode) {
                        // 等落点这一段：**站在原位、只是全透明**。抬页姿态（整片在屏幕外）会让
                        // 落点被窗口裁成 `0×0` —— 那是这一处连栽两轮的根（见 [Entrance] 的头注）。
                        Entrance.PENDING -> {
                            alpha = 0f
                            translationY = 0f
                        }
                        // 飞行全程页面**不出现**：此刻屏上那份图是飞行体，它就是不透明的起点像素。
                        // 页面在这里露头就会被看见"两份图"（终点一份、半路一份）—— 2026-10-01 录屏
                        // 量到的重影正是它。页面等到**飞完**才开始淡入（见 [handoff] 的头注）。
                        Entrance.FLY -> {
                            // 算式在判据层（`handoffPageAlpha` / `handoffBodyAlpha` / `handoffCoverage`）：
                            // "两层叠起来恒为不透明"这条只能到那儿去守，UI 里守不住。
                            alpha = handoffPageAlpha(handoff.value)
                            translationY = 0f
                        }
                        Entrance.RISE -> {
                            alpha = 1f
                            translationY = entrance.value * screenHeightPx
                        }
                    }
                    // 离场那一半（首程淡出，算式在判据层 [exitPageAlpha]）：乘上去而不是另起一支 ——
                    // 入场与离场本来不会重叠，但乘起来两条路都不必假设对方的状态。
                    alpha *= exitFadeAlpha()
                },
        ) {
            when {
                current != null -> {
                    val target = current
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(0, offsetY.value.roundToInt()) }
                            // 放大时禁下滑：否则与 telephoto 的平移抢手势（参考实现同判据）。
                            .draggable(
                                state = draggableState,
                                orientation = Orientation.Vertical,
                                enabled = !zoomedIn && !infoOpen && !dismissing,
                                onDragStopped = { velocity ->
                                    scope.launch {
                                        val far = offsetY.value > screenHeightPx * DISMISS_DISTANCE_FRACTION
                                        val fast = velocity > screenHeightPx * DISMISS_VELOCITY_FACTOR
                                        if ((far || fast) && velocity > 0f) closePage() else offsetY.animateTo(0f)
                                    }
                                },
                            ),
                    ) {
                        // 没有顶栏了，所以只让开状态栏那一档 + 一点呼吸（图要像浮在玻璃上）。
                        // 整屏档不留这一格（判据与理由见 [fullBleed]，下面左右与底部那两格同一档）。
                        if (!fullBleed) Spacer(modifier = Modifier.height(statusBarTop + tokens.spacing.space5))
                        // ── 左右翻页 ──
                        // 横向翻与纵向下滑分属两个方向，各自被各自的 Orientation 锁住，不会互相抢；
                        // 放大之后 telephoto 会吃掉单指拖拽，那时翻页自然让位（正在看图的人不该被翻走）。
                        //
                        // `beyondViewportPageCount` 跟着「智能预加载」那一档走（C1.4）：
                        // 把左右邻页也组出来开始取图，翻过去就能接上（第一档永远命中隔壁页刚下好的缓存）。
                        // ⚠️ 这个参数是**对称**的，所以"下一张"那一档仍会把上一张组出来 ——
                        // 不对称的那一半在 GalleryPreload.plan() 里：我们只主动预取前方。
                        // key 用 uid：两站 id 会撞，key 也跟着撞的话翻页时状态会串。
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = GalleryPreload.beyondPages(preloadMode),
                            key = { index -> pages.getOrNull(index)?.viewerKey ?: index },
                            modifier = Modifier.weight(1f),
                        ) { page ->
                            val pagePost = pages[page]
                            GalleryViewerPage(
                                post = pagePost,
                                masked = maskedOf(pagePost),
                                preferHd = hdUids.contains(pagePost.viewerKey),
                                animated = animateGifs,
                                imageLoader = imageLoader,
                                // 垫着的那一帧只属于**打开时那一张**：翻到别处它已经不成立了。
                                // 而且只在飞行**结束之后**才摆进画面框 —— 途中它正该在手指点过的那张卡的位置上，
                                // 框里同时摆一份就会看见"两个起点"。
                                cardFrame = if (page == initialIndex && flightDone) cardFrame else null,
                                onReady = { if (page == initialIndex) contentReady = true },
                                // **每一页**都回报（按逐页身份键存）：去程要"打开那一张"的落点，
                                // 返回程要"当前页"的起点 —— 翻过页之后这两个不是同一张。
                                onImageBounds = { rect ->
                                    // 落点**只认有面积的那一版**。`boundsInWindow()` 报的是裁到窗口
                                    // 可见部分之后的矩形（真机读数：`size=1016x1576` 恒定而 window 高只有 99），
                                    // 所以页面只要还站在屏幕外，落点就是 `0×0` —— 认了它，飞行体会缩向
                                    // 屏幕左上角消失。姿态已按 [Entrance] 修对（等待期间站在原位），
                                    // 这一道留着当保险：一个零面积的目的地永远不该被拿去飞。
                                    if (rect.width > 0f && rect.height > 0f) {
                                        if (page == initialIndex && frameBounds[pagePost.viewerKey] == null) {
                                            android.util.Log.i("FlyProbe", "first landing rect=$rect")
                                        }
                                        frameBounds[pagePost.viewerKey] = rect
                                    }
                                },
                                onZoomedChange = { zoomedPages[pagePost.viewerKey] = it },
                                onHdError = { reason ->
                                    // HD 档取不到要说出来，并且**把钮拨回去**：
                                    // 留着 HD 亮着、画的却是原档，就是一个假开关。
                                    hdUids = ArrayList(hdUids).also { it.remove(pagePost.viewerKey) }
                                    Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                                },
                                onImageTap = ::toggleChrome,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = if (fullBleed) 0.dp else tokens.spacing.screenHorizontal),
                            )
                        }
                        // 工具条是浮层，所以底部留白自己给，图不会被它盖住。
                        // 整屏档让位给"图吃满整扇窗"：那一档里 chrome 压在图上是**要的**（图三同一形状），
                        // 而 chrome 只在 `showChrome && !zoomedIn` 时出现，点一下就没了。
                        if (!fullBleed) Spacer(modifier = Modifier.height(LocalBottomBarClearance.current))
                    }

                    // ── 连播进度线（§一）──
                    // 摆不摆只看 `autoPlayRunning`（与连播判据同一条），**不看 chrome 显隐**：
                    // 它属内容层，chrome 藏起来看图时正是最该在的那一条。
                    if (autoPlayRunning) {
                        GalleryAutoPlayProgressLine(
                            progress = { autoPlayProgress.value },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(
                                    start = tokens.spacing.screenHorizontal,
                                    end = tokens.spacing.screenHorizontal,
                                    bottom = tokens.spacing.space2,
                                ),
                        )
                    }

                    // 左上角那一条跟着 chrome 显隐：一枚**看得见的返回** + 页码读数。
                    AnimatedVisibility(
                        visible = showChrome && !zoomedIn,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = tokens.spacing.screenHorizontal,
                                top = statusBarTop + tokens.spacing.space5,
                            ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GalleryViewerBackButton(onBack = onBack)
                            if (pages.size > 1) {
                                GalleryViewerPageCounter(
                                    page = pagerState.currentPage,
                                    total = pages.size,
                                )
                            }
                            // AI 位也带进大图页（2026-10-18）：同一张图在卡上带过角标、点开就没了，
                            // 会被读成"换一页就不是 AI 了"。套一层 Box 只是为了拿到 BoxScope ——
                            // 借的是卡上那颗**同一个**胶囊，不在这里另画一份形状。
                            val aiBadgeOn by GalleryPorts.of(LocalContext.current).prefs
                                .galleryAiBadge.collectAsState()
                            if (aiBadgeOn && GalleryAi.isAiMarked(target.siteAiFlag, target.tagList)) {
                                Box { GalleryAiPill() }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showChrome && !zoomedIn,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        GalleryViewerToolbar(
                            post = target,
                            preferHd = preferHd,
                            isFavorite = favorites.any { it.uid == target.uid },
                            saving = saving,
                            sharing = sharing,
                            autoPlaySec = autoPlaySecNow,
                            onToggleAutoPlay = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (autoPlaySecNow > 0) {
                                    // 关的时候记住这一档：再点开要回到刚才的速度，
                                    // 弹回默认值会读成"我刚才调的那一下没生效"。
                                    autoPlayLastSec = autoPlaySecNow
                                    autoPlaySecNow = 0
                                } else {
                                    autoPlaySecNow = autoPlayLastSec
                                }
                            },
                            onAutoPlaySecChange = { sec ->
                                autoPlaySecNow = sec
                                autoPlayLastSec = sec
                            },
                            onToggleFavorite = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                scope.launch { favoritesStore.toggle(target) }
                            },
                            onToggleHd = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                hdUids = ArrayList(hdUids).also { set ->
                                    if (!set.remove(target.viewerKey)) set.add(target.viewerKey)
                                }
                            },
                            onDownload = { download(target) },
                            // 页轴那一屏（从 pixiv 作品页点图进来）**不摆信息钮**（用户 2026-10-18 点名）：
                            // 这一作的标题、计数、标签、画师在详情页本来就整屏摆着，大图里再拉一层是重复。
                            // 与上面那档 `fullBleed` 同判据但**各写一次** —— 那是"图多大"，这是"摆不摆钮"，
                            // 合成一枚旗标就会在下次只想改其中一件时把另一件带走。
                            showInfo = axis == null,
                            onInfo = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                infoOpen = true
                            },
                            onShare = { share(target) },
                        )
                    }
                }

                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VeneraEmptyView(
                        title = "这张图取不到",
                        message = error.orEmpty(),
                        // 与「最新流加载失败」同一条：这一档说"没取回来"，不是"这儿有图"。
                        tone = VeneraEmptyTone.Failed,
                        actionText = "重试",
                        onAction = { retryTick++ },
                    )
                }

                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(tokens.spacing.loaderPage),
                        color = tokens.color.primary,
                        trackColor = tokens.color.surfaceVariant,
                    )
                }
            }

            // 顶栏**整条去掉**（用户 2026-09-25 点名：图要像悬浮在页面上面）。
            // 返回因此只剩两条路：下滑关闭 与 系统返回（预测式返回由跨 activity 的 AOSP 动画接管）。
            // 原来挂在顶栏的"在站点打开"没有丢，搬进了 GalleryInfoSheet 那一行。
        }

        // ── 飞行途中的那一帧（共享元素的"主体"）──
        // 画在整页之上、sheet 之下：它只负责"卡片原位 → 大图位置"那一段。
        // `fly.value` / `handoff.value` / `frameBounds[...]` 都在 drawBehind 的 lambda 里读 = 绘制阶段读，
        // 每帧只重绘这一块，不把整页拖进重组（与上面 graphicsLayer 同一条口径）。
        // 2026-10-19 批 A：两趟绘制并成共享件（进场 [GalleryEntryFlyBody]、离场 [GalleryExitFlyBody]），
        // 几何与圆角不再在这颗 2219 行的屏里各写一遍。
        val frame = cardFrame
        val startRect = flyOrigin
        // 抬页那一档**不画飞行体**：走到那一档时像素可能已经在手上（`payload` 非空）但落点没量出来，
        // 画出来就是"卡片封面僵在原位"等着页面抬上来。它现在整程不透明（见下面 alpha），
        // 多这一道判断才不会把那种僵住的画面亮出来。
        if (frame != null && startRect != null && entranceMode != Entrance.RISE) {
            // ── 临时取证探针：这一档成立 = 飞行体真的进了组合；不成立就是它压根没画出来。──
            LaunchedEffect(frame, startRect) {
                android.util.Log.i(
                    "FlyProbe",
                    "overlay composed frame=${frame.width}x${frame.height} start=$startRect",
                )
            }
            GalleryEntryFlyBody(
                progress = { fly.value },
                from = startRect,
                // 落点还没量出来时**冻在封面原位**垫着（"垫着不空"的职责）：等落点那几帧屏上
                // 不能只剩窗口模糊。量到了下一帧就改道，所以落点在 lambda 里每帧现取。
                to = { frameBounds[initialViewerKey] ?: startRect },
                frame = frame,
                coverCornerRadius = coverCornerRadius,
                // 去程全程不淡；交棒后半程才淡出（那时页面已不透明，见 [handoff]）。
                alpha = {
                    if (entranceMode == Entrance.FLY) handoffBodyAlpha(handoff.value) else 1f
                },
            )
        }

        // ── 返回程那一帧：画面框 → 墙上那张卡 ──
        // 与上面那一层是镜像：去程画"卡片 → 画面框"，这一层画"画面框 → 卡片"。
        // 首程**不淡**：起点是屏上那一帧的真实像素（与它盖住的那张图逐像素相同），
        // 淡了就是"图还没飞就先虚了"。但**末段必须淡**（算式在判据层 [exitBodyAlpha]）——
        // 落点那一侧自己画着同一张图且一直全亮，飞行体不淡就是一块不透明的同一张画
        // 压在同一张画上滑过去（2026-10-19 真机帧：返回第 12 帧两份比例不同的同一张叠着）。
        val exitFrom = exitStart
        val exitTo = exitTarget
        val exitBitmap = exitFrame
        if (exitBitmap != null && exitFrom != null && exitTo != null) {
            GalleryExitFlyBody(
                progress = { exitFly.value },
                from = exitFrom,
                to = { exitTo },
                frame = exitBitmap,
                flyCornerRadius = flyCornerRadius,
                coverCornerRadius = coverCornerRadius,
                alpha = { exitBodyAlpha(exitFly.value) },
            )
        }

        // 信息面板那一格有**两版**：页轴时交回"把我送进来那一侧"给的那一版（pixiv 作品页的那一版，
        // 用户 2026-10-18 点名「点信息面板用回为 p 站做的信息面板」），其余一律走本页自己的那颗。
        // 判据只有一枚布尔，两处不会同时画；这一颗播放器**不解释** `panelData` 是什么类型，
        // 所以它不需要认识任何新屏的模型（隔离那条尺子钉的就是这个）。
        val axisPanel = axis?.infoPanel
        if (infoOpen && current != null && axisPanel != null) {
            axisPanel(
                axis?.panelData,
                GalleryPageAxis.PanelParams(
                    post = current,
                    imageLoader = imageLoader,
                    onOpenArtist = { name, postSource -> onOpenArtist(name, postSource) },
                    onDownload = { download(current) },
                    onDismiss = { infoOpen = false },
                    // 面板里点标签 → 收起面板、把这一枚交回画廊那面墙去搜（用户 2026-10-19 第 3 条）。
                    // 三步顺序：先 `postTag`（交接槽必须先写上，否则消费方读不到），再收面板，
                    // 最后 `onBack()` 把这一层退掉 —— 与画师介绍页那条链同一形状（`postTag` + finish），
                    // 不多造一条"跳走了但还压在栈上"的路。
                    onSearchTag = { tag ->
                        GallerySearchHandoff.postTag(site, tag)
                        infoOpen = false
                        onBack()
                    },
                ),
            )
        } else if (infoOpen && current != null) {
            GalleryInfoSheet(
                post = current,
                categories = tagCategories,
                fallbackArtistNames = fallbackArtists,
                // 这一份在**打开这一张图**时就开取了（见上面那个 effect），面板打开时通常已经到位。
                artistCredits = artistCredits,
                tagTranslations = tagTranslations,
                imageLoader = imageLoader,
                // 底部那条「下载原图」与底栏那枚是**同一个动作**：进度与 toast 都归这一层持有，
                // 面板只负责显示（两处各存一个布尔，就会出现"底部条在转圈、底栏那颗还能点"）。
                saving = saving,
                onDownload = { download(current) },
                // 「画成水彩」的入口（2026-10-14 从底栏那枚笔刷搬进这一条动作条）。
                // 判据在 `watercolorEntryVisible`：Beta 关着、或这一张是视频 ⇒ 交回 null，那一格**整格不摆**。
                // 点它先把面板收掉：浮层画完关掉，回到的是那张图，不是压在图上的面板。
                onPaint = if (watercolorEntryVisible(betaEnabled = watercolorBeta, isVideo = current.isVideo)) {
                    { painterPost = current }
                } else {
                    null
                },
                onDismiss = { infoOpen = false },
                // 画师行那一块整块可点 → 介绍页。它**不离开本页**（不同于点标签那一条），
                // 所以不写交接槽、也不 finish：介绍页是又压上来的一级，返回就回到这张图。
                onOpenArtist = { name -> onOpenArtist(name, current.source) },
                // 点标签 = 回一级画廊搜这一枚。这里负责"离开本页"，把结果那一屏留给画廊：
                // 搜索结果归 [GallerySearchViewModel] 持有，在本页另起一份就变成两处各存一半。
                // 走 onBack（= Activity.finish）而不是 closePage 那套下滑动画 —— 用户要的是立刻看到
                // 结果，多那 300ms 的离场动画挡在前面，看起来像点了没反应。
                onSearchTag = { tag ->
                    GallerySearchHandoff.postTag(current.site, tag)
                    infoOpen = false
                    onBack()
                },
            )
        }

        // ── 水彩绘制浮层（2026-10-13）──
        // 整屏盖在本页之上：它自己是不透明白底，取的像素与屏上同一张 large 档
        // （同一颗缓存键，不再下一遍）。连播在它开着时让位（autoPlayRunning 那两处判据）。
        AnimatedVisibility(
            visible = painterPost != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            painterPost?.let { post ->
                WatercolorPainterScreen(
                    post = post,
                    imageLoader = imageLoader,
                    onClose = { painterPost = null },
                )
            }
        }
    }
}

/**
 * 大图页的**一页**（一张图 / 一段视频）。
 *
 * 单独抽出来只为一件事：**缩放状态必须每页一份**。
 * 挂在页面级的话，翻到下一张会把上一张的缩放带过去（下一张一进来就是放大的、
 * 还偏在某个角上）；翻回来也理应重新从 1x 看起 —— 这是相册的通用语义。
 *
 * 缩放与否**上抛**给页面：底栏与页码在放大时要让位（放大的人在看细节，不该被浮层挡着），
 * 而纵向下滑关闭也要在放大时禁掉（否则与 telephoto 的平移抢手势）。
 * 只在跨过 1x 那一瞬间上报，不是每帧 —— 每帧写一个 state 会让整页跟着每帧重组。
 */
@Composable
private fun GalleryViewerPage(
    post: GalleryPost,
    masked: Boolean,
    preferHd: Boolean,
    /** 这一屏该不该把动图解成动画（大图页按档位与网络算出来，见 GalleryMotion）。 */
    animated: Boolean,
    imageLoader: ImageLoader,
    /** 一级卡片里**封面那一块**那一帧（窗口坐标裁出来的位图）。只有"打开时那一张"才拿得到。 */
    cardFrame: ImageBitmap?,
    onImageTap: () -> Unit,
    /** 开门档或中档已落位（成功或失败都算）—— 页面据此交还垫着的那一帧。 */
    onReady: () -> Unit,
    onHdError: (String) -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    /** 画面那一框在**窗口坐标**里的矩形，回报给宿主算飞行的落点（只有打开那一张要用）。 */
    onImageBounds: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 缩放状态**不在这里建**：上限要按"这一屏铺满要几倍"算，而那一框的尺寸是
    // [GalleryViewerMedia] 里定框那段的产物 —— 放在这边就只能猜一个数（见那颗文件的 zoomSpec 那处）。
    GalleryViewerMedia(
        post = post,
        masked = masked,
        preferHd = preferHd,
        animated = animated,
        imageLoader = imageLoader,
        onZoomedChange = onZoomedChange,
        cardFrame = cardFrame,
        onImageTap = onImageTap,
        onReadyChange = { onReady() },
        onHdError = onHdError,
        onImageBounds = onImageBounds,
        modifier = modifier,
    )
}

/**
 * 可翻页时的页码读数（`3 / 324`）。
 *
 * 为什么值得占一块屏：左右翻是**没有可见入口**的动作，摆一对箭头钮会把画面切碎、
 * 也没人按（几百张时点箭头是酷刑）。但"这里有别的图"必须有个暗示，否则用户根本不知道能翻。
 * 一枚读数就够 —— 不做小圆点：几百张时圆点要么挤成一条线、要么只能显示邻近几个，
 * 那才是骗人的指示器。
 *
 * 位置与卡片角标同一套：深色固定底 + 近白字（压在内容完全不可控的图上，
 * 只有固定色对能保证可读，理由同 [GalleryCornerPill]）。
 */
@Composable
private fun GalleryViewerPageCounter(page: Int, total: Int) {
    val tokens = VeneraTokens
    Text(
        text = "${page + 1} / $total",
        fontSize = tokens.type.caption,
        color = StatusColors.OnBadgeSurface,
        modifier = Modifier
            .background(
                StatusColors.BadgeSurface.copy(alpha = PAGE_COUNTER_BG_ALPHA),
                RoundedCornerShape(tokens.shape.extraLarge),
            )
            .padding(
                horizontal = tokens.spacing.badgeHorizontalPadding,
                vertical = tokens.spacing.badgeVerticalPadding,
            ),
    )
}

/** 页码底色透明度，与卡片角标同一档（0.72）。 */
private const val PAGE_COUNTER_BG_ALPHA = 0.72f

/**
 * 大图页左上角那枚**返回**。
 *
 * 此前这一屏没有任何可见的"出去"入口：退出走系统返回、下滑手势与左右边缘滑，
 * 而那三条都不写在屏上 —— 第一次进来的人要答的恰恰是"这屏怎么出去"
 * （2026-10-14 手机档 P2：顶部只有「4 / 180」）。
 *
 * 底色与页码读数共用那一套固定色对：压在内容完全不可控的图上，只有固定色对保证可读
 * （理由同 [GalleryViewerPageCounter] 头注）。触达位吃 [VeneraIconButtonTouchSize]，
 * 与底部那排 dock 同尺寸 —— 同一屏上的两排钮不该有两种触摸面积。
 */
@Composable
private fun GalleryViewerBackButton(onBack: () -> Unit) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .size(VeneraIconButtonTouchSize)
            .clip(CircleShape)
            .background(StatusColors.BadgeSurface.copy(alpha = PAGE_COUNTER_BG_ALPHA))
            .clickable(onClick = onBack, onClickLabel = "退出这张图"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "返回",
            tint = StatusColors.OnBadgeSurface,
            modifier = Modifier.size(tokens.spacing.chipIconSize),
        )
    }
}

/**
 * 大图页的三种入场姿态（用法与为什么必须是三档，见 `GalleryPostScreen` 里 [entranceMode] 那处头注）。
 *
 * 刻意做成枚举而不是两个布尔：栽过的那两轮都是"两档互相牵连"——一处翻，另一处的表达式就读到脏值。
 */
private enum class Entrance { PENDING, FLY, RISE }

/**
 * 等画面那一框报回落点的最长时间。
 *
 * 姿态修对之后（等待期间页面站在原位、只是全透明）落点应当**第一帧**就有 ——
 * 这一档现在只是笔保险：万一那框压根量不出来（布局被别的动画压住、页面结构改了），
 * 也不能让页面永远透明地站着。超时就退回抬页，屏上不会留一块空白。
 *
 * 记一笔栽过的读法：先前它停在 400ms 时真机读数 `target=null waited=406ms`，
 * 而第一个非零落点在 651~657ms —— 那时以为是"布局慢"，放宽到 600ms 仍然不飞。
 * 真实原因是页面站在屏幕外，落点被窗口裁成 `0×0`（见 [Entrance]）。**预算不是病，姿态才是病。**
 */
private const val FLY_WAIT_MS = 600L

/**
 * 交棒的时长（毫秒）。飞行体落位之后才开始走，见 `GalleryPostScreen` 里 [handoff] 的头注。
 *
 * 160ms 是"看得出来是一次收束、又不至于让人等"的一档，比进场那一档弹簧短得多 ——
 * 它不再承担位移，只做"页面铺满 + 糊变清晰"。
 */
private const val HANDOFF_MS = 160

/**
 * 满屏的画面：按真实比例定框、圆角贴着图、缩放挂在容器上。
 *
 * **三档叠画**（用户 2026-09-26 点名改的形态）：开门档 → 中档 → 原档，谁先到谁先显形。
 * 打开一张图时先看到的是十几 KB 的那一档（yande.re 与网格卡片同址，缓存直接命中），
 * 中档（164~209 KB 的 sample）随后盖上，HD 钮再往上是站方原图。
 * 加载环只有一枚、只有一个落点（右下角），且**只在屏上已经有画面、更高的那一档还在飞时**才挂 ——
 * 一档都没有的时候仍然铺整块骨架（全站口径）。
 *
 * 不开 `placeholderMemoryCacheKey`：它会让 `onSuccess` 在底图命中缓存时就先响一次，
 * 加载态那条读数就再也准不了（真机反馈"加载的时候没有骨架图"的根因）。
 *
 * 视频走 [GalleryVideoViewer] 就地播、**不进缩放**：缩放层对 mp4 没有意义，
 * 而站方给的原片就是播放用的那一档（实测没有更小的转码档）。
 */
@Composable
private fun GalleryViewerMedia(
    post: GalleryPost,
    masked: Boolean,
    preferHd: Boolean,
    /** 这一屏该不该把动图解成动画（判据 `GalleryMotion`，墙上永远传 false）。 */
    animated: Boolean,
    imageLoader: ImageLoader,
    /** 正在放大（喂给宿主：放大时禁下滑、chrome 让位，与「翻到别张」共用那一档读数）。 */
    onZoomedChange: (Boolean) -> Unit,
    onImageTap: () -> Unit,
    /** 一级卡片那一帧（窗口坐标裁出来的位图）—— 滑入期间垫在最上面。 */
    cardFrame: ImageBitmap?,
    /** 开门档或中档已落位（成功或失败都算）—— 垫着的那一帧到这里才交还。 */
    onReadyChange: (Boolean) -> Unit,
    /** HD 档取不到。调用方要把 HD 钮拨回原档并说一句话，不能静默换档。 */
    onHdError: (String) -> Unit,
    /** 画面那一框的窗口矩形 → 宿主拿它当共享元素的**落点**。null = 没人要（翻页后的各页）。 */
    onImageBounds: ((Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    /**
     * 垫帧（= 去程那一帧）**自己那圈圆角**：与飞行体同一条口径 —— 位图四角画的是卡片面色，
     * 得按封面那一档裁掉（见 [veneraCoverCornerRadius] 与 [payloadCornerRadiusPx]）。
     * 垫帧画在这块框里是 `ContentScale.Crop`，倍率就是下面这颗 `padCornerRadius`。
     */
    val coverCornerRadius = veneraCoverCornerRadius()
    val density = LocalDensity.current
    val ratio = post.cardRatio.takeIf { it > 0f } ?: tokens.spacing.coverAspectRatio
    // 共享元素的落点回报。挂在布局回调上而不是自己算：那一框的尺寸是下面"按真实比例定框"
    // 那段逻辑的产物，复制一份算式就是第二处真相（改圆角/改留白时必然漂）。
    val boundsReporter = if (onImageBounds == null) {
        Modifier
    } else {
        Modifier.onGloballyPositioned { coordinates ->
            // ── 临时取证探针（只挂在打开那一张上）──
            // 落点报回来的时刻比预算晚（真机两次读数 651ms / 657ms，而预算 600ms），
            // 且回报的矩形是"宽恒定、高在长"—— 与定框算式对不上。这里一次量齐三样：
            // 布局给的尺寸、坐标自己算的窗口矩形、以及**不含 graphicsLayer 位移**的根相对矩形。
            // 三者一比就能分清：是页面在被平移（那就是入场动画在动落点）、还是尺寸真在长（布局在抖）。
            // 批 B §3 地基：这一框**两用**——落点/终点是"位置"（要没裁过的），离场截像素是"表面"（要裁过的）。
            // 这里只把**没裁过**的那一把交出去当位置；截像素那一路在 [GalleryFlyIn.captureRegion] 内部
            // 自己钳回窗口（PixelCopy 只能读窗口内的表面），所以消费侧存 full、不存两份真相。
            val full = coordinates.boundsInWindow(clipBounds = false)
            // `size` 是布局给的尺寸（不含 graphicsLayer 位移），`window` 含位移。
            // 两者一比就够分：size 在长 = 布局在抖；size 恒定而 window 在动 = 入场平移在动落点。
            android.util.Log.i(
                "FlyProbe",
                "landing size=${coordinates.size.width}x${coordinates.size.height} full=$full",
            )
            onImageBounds(full)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 定框：横图按高铺满、竖图按宽铺满，另一个方向留白。
        // 这样圆角是贴着图的。
        val boxRatio = maxWidth / maxHeight
        val sizedByWidth = ratio >= boxRatio
        val boxWidth = if (sizedByWidth) maxWidth else maxHeight * ratio
        val boxHeight = if (sizedByWidth) maxWidth / ratio else maxHeight

        if (post.isVideo) {
            // 视频没有"再高一档"可切，静帧到位即算落位（滑入不用等原片缓冲完）。
            // 全屏状态**握在这里、不放进播放器**：要改的是"这一页给视频多大一块框"，
            // 那是下面那段定框逻辑的事，播放器自己扩不出去。
            var videoFullscreen by rememberSaveable(post.uid) { mutableStateOf(false) }
            Box(
                (if (videoFullscreen) Modifier.fillMaxSize() else Modifier.size(boxWidth, boxHeight))
                    .then(boundsReporter)
            ) {
                GalleryVideoViewer(
                    post = post,
                    masked = masked,
                    imageLoader = imageLoader,
                    fullscreen = videoFullscreen,
                    onToggleFullscreen = { videoFullscreen = it },
                )
                LaunchedEffect(post.uid) { onReadyChange(true) }
            }
        } else {
            val fastUrl = post.fastUrl
            val fastRequest = remember(post.uid, fastUrl) {
                if (fastUrl.isBlank()) null else galleryFastRequest(context, fastUrl)
            }
            val largeRequest = remember(post.uid, animated) { galleryLargeRequest(context, post, animated) }
            val fileRequest = remember(post.uid, animated) { galleryFileRequest(context, post, animated) }

            // 三档各自的落地读数。`onLoading` 必须把值打回 false：切 HD、翻回来重取都要
            // 重新点亮加载态，一次置位就再也不灭的读数等于没有加载态。
            // 快照档**只认成功**：它失败时该继续摆骨架（中档还在路上，别先摆一个空框）。
            var fastLoaded by remember(post.uid) { mutableStateOf(false) }
            var largeSettled by remember(post.uid) { mutableStateOf(false) }
            var hdSettled by remember(post.uid) { mutableStateOf(false) }
            // 垫帧（= 去程那一帧）**等中档落位才交还**（2026-10-01 12:09 真机读数改的口径）。
            //
            // 老写法是"快照档或中档任一落位就算到位"，而快照档两站都是**特别小**的一档
            // （yande.re 的 `preview_url` 实测 300×212）：它铺进一屏宽的画面框里要放大三倍多，
            // 比手里这张垫帧（卡封面那一块的屏上像素）还糊。于是交棒读起来是"变糊一下"、
            // 中档回来再"变清晰一下" —— 用户报的"闪几下"里就有它一份。
            // 中档（sample）才是这一屏的成品档，它落位才算真到位；它取不到时 onError 也会把
            // `largeSettled` 置真，所以不会把上面那份位图永远攥着。
            LaunchedEffect(largeSettled) {
                if (largeSettled) onReadyChange(true)
            }
            // ── 临时取证探针（同上第二轮）：这一页哪一档在什么时刻落位。──
            // 飞行交棒后如果闪与这条的翻动同一时刻，那就是**图本身换档**（分辨率跳变），
            // 与透明度、chrome 都无关。
            LaunchedEffect(fastLoaded, largeSettled, hdSettled, cardFrame != null) {
                android.util.Log.i(
                    "FlyProbe",
                    "tier id=${post.id} fast=$fastLoaded large=$largeSettled hd=$hdSettled pad=${cardFrame != null}",
                )
            }

            /*
             * 缩放层吃**整屏**，不吃"图的那一框"（2026-10-15 真机读数：捏到最大也出不了那一块）。
             * 两处叠在一起才成那副观感：
             * ① `Modifier.zoomable` 默认 `clipToBounds = true` —— 裁的就是它自己那一层，
             *    挂在定框上等于放大之后再剪回原框；
             * ② `rememberZoomableState()` 的默认上限是 **2×**（`ZoomSpec(maxZoomFactor = 2f)`），
             *    而宽幅图的框本来就只有屏高的一小条，2× 也够不着全屏。
             * 上限按 master 那一档口径给：`PhotoViewComputedScale.covered * 10.0`
             * （`lib/pages/image_favorites_page/image_favorites_photo_view.dart:64`），
             * 其中 `covered` 在这里就是"把这一框铺满整屏要几倍"，由定框那两个尺寸现算，不猜数。
             */
            val coverToFill = galleryCoverZoomFactor(boxWidth, boxHeight, maxWidth, maxHeight)
            // 1:1 那一档吃**原图像素**：站方报的 `width` 就是原图宽（HD 关掉时画的仍是中档，
            // 但"放大到 1:1"指的是把原图那一档的像素一个一个摆出来，用户 2026-10-18 点名的正是这一句）。
            val nativeZoom = galleryNativeZoomFactor(
                post.width,
                with(density) { boxWidth.toPx() }.toInt(),
            )
            val zoomState = rememberZoomableState(
                zoomSpec = ZoomSpec(maxZoomFactor = maxOf(coverToFill, nativeZoom) * GALLERY_ZOOM_OVERCOVER_FACTOR),
            )
            LaunchedEffect(zoomState) {
                snapshotFlow { (zoomState.zoomFraction ?: 0f) > 0f }
                    .distinctUntilChanged()
                    .collect { onZoomedChange(it) }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    // 双击走**原图像素 1:1**，不走"铺满整屏"那一档，也不走默认的上限（×10 那一档）：
                    // 铺满会把屏外两头裁掉并且比 1:1 还糊（用户 2026-10-18：「放大整个图片而不是限制在
                    // 一个大小里放大」）。算不出 1:1（站方没给宽高，或小图本来就不止 1:1）时把这枚撤掉 ——
                    // 按下去什么都不变的那一枚就是假开关。
                    .zoomable(
                        state = zoomState,
                        onClick = { onImageTap() },
                        onDoubleClick = if (nativeZoom > COVER_TOLERANCE) {
                            DoubleClickToZoomListener.cycle(nativeZoom)
                        } else {
                            null
                        },
                    ),
            ) {
                // 三档叠画（用户 2026-09-26 点名）：谁先到谁先显形，后到的直接盖上去。
                // 三档是同一构图、只是分辨率不同，所以盖上去读起来是"变清晰"，不是"跳一下"。
                //
                // 缩放与点击挂在**整屏那一层**，图这一框只跟着一起变换：老写法把 zoomable 放在
                // 这一框里面，于是放大被这一框的边界裁死（见上面那段）。
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(boxWidth, boxHeight)
                        .then(boundsReporter)
                        .clip(RoundedCornerShape(tokens.shape.large))
                        .then(if (masked) Modifier.blur(tokens.spacing.space10) else Modifier),
                ) {
                    // 底：开门档（两站都用各自的 preview_url）。
                    // 打开那一瞬间就有画面，用户看到的不再是一整块骨架。
                    if (fastRequest != null) {
                        AsyncImage(
                            model = fastRequest,
                            contentDescription = null,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Fit,
                            onSuccess = { fastLoaded = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // 中：站方 sample 档（164~209 KB），也就是默认这一屏的成品。
                    AsyncImage(
                        model = largeRequest,
                        contentDescription = "${post.site.displayName} #${post.id}",
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Fit,
                        onLoading = { largeSettled = false },
                        onSuccess = { largeSettled = true },
                        // 中档取不到也照样算"落位"：底下还压着开门档，画面不是空的，
                        // 再挂一个永远转下去的环就是假读数。
                        onError = { largeSettled = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 顶：原图档，只有开了 HD 才有这一层。
                    if (preferHd) {
                        AsyncImage(
                            model = fileRequest,
                            contentDescription = null,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Fit,
                            onLoading = { hdSettled = false },
                            onSuccess = { hdSettled = true },
                            onError = { state ->
                                hdSettled = true
                                // 取流层抛的是 `HTTP 403: Failed to fetch comic image at <url>`，
                                // 这里只要那个状态码 —— 把整串糊在 Toast 上没人读得完。
                                val detail = state.result.throwable.message
                                    ?.substringBefore(" at http")
                                    ?.substringBefore(":")
                                    .orEmpty()
                                onHdError(
                                    "HD 档取不到${if (detail.isBlank()) "" else "（$detail）"}，" +
                                        "已退回原档。这一档是站方原图，被挡多在 CDN 侧的限流",
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // ── 不跟着放大那一层 ──
            // 垫帧、骨架、加载环、打码提示都按"图那一框"定位，但它们不是被看的对象：
            // 放大到几十倍时还让骨架与环跟着涨就荒唐了。所以它们住在缩放层**外面**，
            // 另起一框与那一框同心同尺寸。
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(boxWidth, boxHeight),
            ) {
                if (cardFrame != null && !largeSettled) {
                    // 这一帧是**卡片封面那一块**（不是整卡，见 GalleryFlyIn 文件头注），与这块框共用
                    // 同一个 `cardRatio`，所以 Crop 不改变构图 —— 大图到位时是"变清晰"，不是"跳一下"。
                    //
                    // ⚠️ 撤它的条件**只有中档落位**，不能再带"快照档也落位"：快照档比它还糊，
                    // 交棒到它等于先把图换糊一次（同上面 `onReadyChange` 那处头注）。
                    //
                    // ⚠️ 必须先按**封面自己那圈圆角**裁（倍率就是 Crop 的倍率）：这一帧的四角画的
                    // 是卡片面色，不裁就是四枚白角挂在图上（2026-10-16 用户报的那条）。
                    val padCornerRadius = with(density) {
                        // 倍率按 `Crop` 的定义取两个方向的**大者**（比例一致时两个方向相等）。
                        val scale = maxOf(
                            boxWidth.toPx() / cardFrame.width.coerceAtLeast(1),
                            boxHeight.toPx() / cardFrame.height.coerceAtLeast(1),
                        )
                        (coverCornerRadius.toPx() * scale).toDp()
                    }
                    Image(
                        bitmap = cardFrame,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(padCornerRadius))
                            // 位图就是封面那一块（只有图）—— 浮层 caption 那一档第三十二轮已退役
                            // （用户「改成图片能在外面预览全部显示那种不要浮层」），整张铺进来
                            // 与画面框同构，不再需要把底部那一绺裁掉。
                            .then(if (masked) Modifier.blur(tokens.spacing.space10) else Modifier),
                    )
                }
                // 加载态分两段（用户 2026-09-26 的口径：先把图给我看，再告诉我还在取更清晰的）：
                //  - 一档都没落位 = 全屏什么都没有 → 骨架（全站口径：整块重拉用骨架不用转圈）；
                //  - 已经有画面在屏、只是更高的那一档还在飞 → 只挂一个波浪环，不再盖骨架，
                //    更不盖满屏 —— 用户要看的正是那张已经出来的图。
                //
                // 环**只有一个落点**（用户 2026-09-26 二次反馈）：右下角。
                // 原先中档那一枚挂在正中、HD 那一枚在右下角，同一件事（在取更清晰的档）换一档就换个
                // 地方冒出来，读起来像两个互不相干的状态；而且居中那枚正压着用户在看的画面正中。
                // 现在中档 / HD 共用这一枚环、同一个尺寸 —— 切 HD 时它不会挪窝、也不会缩一下。
                when {
                    !(fastLoaded || largeSettled || hdSettled) -> VeneraShimmer(Modifier.fillMaxSize())
                    !largeSettled || (preferHd && !hdSettled) -> GalleryTierLoadingRing(
                        size = tokens.spacing.loaderInline,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(tokens.spacing.space6),
                    )
                }
                // 打码提示住在这一层：它说的是"这张被打码了"，不是画面的一部分，
                // 所以放大时它不该跟着飘（落点仍是那一框的正中，与挪进来之前的位置同一处）。
                if (masked) {
                    Text(
                        text = "已按你的分级设置打码",
                        fontSize = tokens.type.caption,
                        // 底是深色玻璃，所以走角标那套固定白（主题的 textPrimary 在浅色主题下是深色，
                        // 压在黑玻璃上就读不出来了）。
                        color = StatusColors.OnBadgeSurface,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(tokens.spacing.space4),
                    )
                }
            }
        }
    }
}

/**
 * 把图那一框**铺满整屏**要几倍（master 里 `PhotoViewComputedScale.covered` 的同义）。
 *
 * 定框那段是 Fit（哪一轴装不下就缩哪一轴），所以另一轴留白；要盖满视口得按**留白更多**那一轴算，
 * 取两轴商里大的那个。下限钉在 1：框本来就比视口大（不会发生，但纯函数要能单测）时不许报小于 1。
 */
internal fun galleryCoverZoomFactor(
    boxWidth: Dp,
    boxHeight: Dp,
    viewportWidth: Dp,
    viewportHeight: Dp,
): Float {
    // 退化输入一律退回 1 倍：`Dp / 0.dp` 交出来的是 Infinity，上限一旦是 Inf 或 NaN，
    // 捏合直接失灵而不是"退到默认档"。比较用 `.value` 不吃裸 dp（存量已冻结那把尺子）。
    if (listOf(boxWidth, boxHeight, viewportWidth, viewportHeight).any { it.value <= 0f }) return 1f
    return maxOf(viewportWidth / boxWidth, viewportHeight / boxHeight, 1f)
}

/** 上限 = 铺满那一档再乘的头数。照 master 图片收藏页那档：`covered * 10.0`。 */
internal const val GALLERY_ZOOM_OVERCOVER_FACTOR = 10f

/**
 * 放大到**原图像素 1:1** 要几倍（用户 2026-10-18：「双击放大图片应该是放大整个图片，
 * 而不是限制在一个大小里放大」，并点了「循环：适应屏宽 ↔ 原图像素 1:1」那一档）。
 *
 * 与 [galleryCoverZoomFactor] 的分别是真的：`covered` 是"把这一框裁到铺满视口"，
 * 宽幅图放大后左右两头都在屏外，而它常常比 1:1 还大（1080 宽的屏把 2000 px 的图拉到 3200 px）
 * —— 双击跳到那一档，用户读到的是"放大=裁掉+糊"。
 *
 * 入参是**像素**：`boxWidthPx` 就是定框那一框在屏上占的物理宽（1 倍时画面就铺在那一框里，
 * 所以要的倍数 = 原图宽 / 那一框宽）。
 * 退化输入一律回 1：站方没给宽高时 1 会顺着调用点那条判据退成"这一张不摆双击"（按下去
 * 什么都不变的那一枚就是假开关），而不是把 NaN 喂进 `ZoomSpec` 让捏合整个失灵。
 */
internal fun galleryNativeZoomFactor(naturalWidthPx: Int, boxWidthPx: Int): Float {
    if (naturalWidthPx <= 0 || boxWidthPx <= 0) return 1f
    return naturalWidthPx.toFloat() / boxWidthPx.toFloat()
}

/** 已经铺满的容差：小于这一档就别摆双击那一枚（按下去看不出变化 = 假开关）。 */
internal const val COVER_TOLERANCE = 1.01f

/** 位移超过 25% 屏高就算"要关"（阈值口径照参考实现）。 */
private const val DISMISS_DISTANCE_FRACTION = 0.25f

/** 甩动速度超过 0.6 屏高/秒也算"要关"。 */
private const val DISMISS_VELOCITY_FACTOR = 0.6f

/** 暗化那一档的 alpha（糊完之后压在玻璃上的黑）。 */
private const val BACKDROP_SCRIM_ALPHA = 0.45f

/**
 * 入场时压暗那一层在飞行的**前几成**里渐入到齐。
 *
 * 不另起一条钟：幕布与图本来就是同一件事的两面（图浮起来、后面的屏退下去）。
 *
 * 这个数字是**实测调出来的**，不是拍的（2026-10-01 12:30 修复后录屏逐帧）：
 * 取 0.35 时幕布只用 **67ms** 就落了满 —— `spring(StiffnessMediumLow)` 起步近似线性，
 * 临界阻尼 `x(t)=1-(1+ωt)e^(-ωt)`（ω=√400=20）在 t=0.067s 处已经到 0.387。
 * 67ms 的渐变仍然是"闪"，所以档位加深到 0.8：同一条曲线在 t≈0.14s 处到 0.8，
 * 幕后整段（约 140ms）都在退，读起来才是"图飞过去、后面的屏一起退开"。
 */
private const val BACKDROP_ENTRY_FRACTION = 0.8f

/**
 * 缓存 key 口径（原三级与二级共用，现在满屏那两层图共用）。
 *
 * **必须带站点**：两站 id 各自独立编号，只按 id 做 key 会让另一站的同号条目直接命中
 * yande.re #123 的缓存位 —— 那是"点这张看到那张"的静默错图。
 */
internal fun galleryCacheKey(kind: String, post: GalleryPost) =
    "gallery-$kind-${post.site.routeKey}-${post.id}" + (post.pageOrdinal?.let { "-p$it" } ?: "")

/**
 * 大图页的**开门档**请求。
 *
 * **刻意不写内存/磁盘 cache key**（与下面两档相反）：网格卡片取的就是这张的地址，
 * 而 Coil 3.6.2 的默认键就是地址本身（`Keyer` 只认映射后的 Uri，尺寸不进键 ——
 * 读了 coil-core 的 `MemoryCacheService.key` 确认），所以**不写键才白拿卡片那份缓存**。
 * 一写自定义键就变成"同图不同键"，白白再下一次。
 */
internal fun galleryFastRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .build()

/**
 * large 档（yande.re 的 `sample_url` / Gelbooru 的 `sample_url`），写死 cache key 供别处当底图。
 *
 * ⚠️ 拿它当**视频**的底图时要留个心：Gelbooru 对视频的 `sample_url` 给空串，
 * 而翻译时兜底成了 `file_url`（原片 mp4）—— Coil 解不了视频，那张底图会落空。
 * 视频页对此已有处理（见 `GalleryVideoViewer`），这里不改判据：
 * 「中档兜底到原图」对**图片**是对的（那正是站方"小图不需要样本"的语义）。
 */
internal fun galleryLargeRequest(context: Context, post: GalleryPost, animated: Boolean = false): ImageRequest {
    // 视频条目这一档要换成 [GalleryPost.videoPosterUrl]，并且**缓存键也要分开**：
    // 旧写法用同一个 `large` 键，而那个键下已经存过被兜底成原片的 **mp4 字节**
    // （Coil 的显式键不认地址，只认键名），换了判据还会命中那份坏数据 ——
    // 结果是"修好了真机上依旧黑屏"。分开键就绕开它，旧条目交给那 512 MB 的 LRU 收拾。
    val kind = if (post.isVideo) "poster" else "large"
    // 动/静两种解法**只分内存键，不分磁盘键**：磁盘上存的是编码字节（与解法无关，共用不浪费），
    // 内存里存的却是解出来的 `AnimatedImage` 或位图 —— 共用一条键就会出"从'始终'改成'从不'
    // 之后那张图还在动"（旧条目还在内存里等着）。
    val memoryKind = if (animated && !post.isVideo) "$kind-a" else kind
    return ImageRequest.Builder(context)
        .data(if (post.isVideo) post.videoPosterUrl else post.largeUrl)
        .memoryCacheKey(galleryCacheKey(memoryKind, post))
        .diskCacheKey(galleryCacheKey(kind, post))
        .allowAnimatedImage(animated && !post.isVideo)
        .build()
}

/**
 * `file_url` 原图档。两条防护（口径抄 PixEz 的注释理由）：
 *
 * - `size(4096) + INEXACT`：解码边长上限。注意 Coil 的 `size` 是**下限**语义，
 *   这条只挡"比 4096 还巨"的画作瞬间撑爆堆，不是省内存的主手段。
 * - `memoryCachePolicy(READ_ONLY)`：**87 MB 的位图不进内存缓存**（实测最大一例
 *   3907×5600 解码后约 87 MB）。关掉写入，页面一关这块就能被回收，
 *   否则它会长期占着画廊那 64 MB 预算、把一级的缩略图全挤出去。
 */
internal fun galleryFileRequest(context: Context, post: GalleryPost, animated: Boolean = false): ImageRequest =
    ImageRequest.Builder(context)
        .data(post.fileUrl)
        .memoryCacheKey(galleryCacheKey(if (animated) "file-a" else "file", post))
        .diskCacheKey(galleryCacheKey("file", post))
        .size(HD_DECODE_EDGE_PX, HD_DECODE_EDGE_PX)
        .precision(Precision.INEXACT)
        .memoryCachePolicy(CachePolicy.READ_ONLY)
        // 原图档是**唯一可能整片吃下动图原档**的那一层（站方没有更小的动图转码档），
        // 所以"要不要解动画"在这里同样要表态 —— 不表态就是静帧，与闸门默认一致。
        .allowAnimatedImage(animated)
        .build()

/** 解码边长上限，口径同 PixEz 的 `loadOriginalSize`（注释理由：防巨幅撑爆堆）。 */
private const val HD_DECODE_EDGE_PX = 4096

/**
 * 浮在画面上的「更高一档还在取」指示器 —— M3 Expressive 波浪环（全站加载口径）。
 *
 * 两件事都取**固定色**而不是主题色，理由与 `GalleryCornerPill` 同一条：这枚环压在
 * **内容完全不可控**的图上（可能纯白、纯黑、高饱和插画），主题色的对比度保证不了
 * （浅色主题的 primary 偏深，压在一张夜景插画上就读不出来了）。所以：
 * 深灰圆底 + 近白环，这一对是全站"浮在图上"的元素已经在用的口径。
 *
 * 底板是必要的一层：只有环没有底时，环的深色轨道段在白底图上会被吃成一片白，
 * 环本身在白底图上也几乎看不见。
 */
@Composable
private fun GalleryTierLoadingRing(size: Dp, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .background(StatusColors.BadgeSurface, CircleShape)
            .padding(tokens.spacing.space5),
    ) {
        CircularWavyProgressIndicator(
            modifier = Modifier.size(size),
            color = StatusColors.OnBadgeSurface,
            trackColor = StatusColors.OnBadgeSurface.copy(alpha = TIER_RING_TRACK_ALPHA),
        )
    }
}

/**
 * 环的轨道透明度。0.24 是"看得见轨道、又不跟环抢"的那一档：
 * 更淡则轨道在白底图上消失（环就只剩一段孤零零的弧），更浓则一眼像个双环控件。
 */
private const val TIER_RING_TRACK_ALPHA = 0.24f

/**
 * 底栏那颗 ▶ 从"关"切回"开"时，若这一屏还没调过速度、设置里也是 0，就用这个默认间隔。
 * 5 秒是用户原话里给的那个数（「比如每 5 秒切一张」），不另造一个。
 */
private const val AUTOPLAY_DEFAULT_SEC = 5
