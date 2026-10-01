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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.venera.VeneraShimmer
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.gallery.data.GalleryFavoritesStore
import com.venera.compose.gallery.data.GalleryConnectivity
import com.venera.compose.gallery.data.allowAnimatedImage
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySaver
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagCategories
import com.venera.compose.gallery.data.GalleryTagDictionary
import com.venera.compose.gallery.data.GelbooruClient
import com.venera.compose.gallery.data.YandeReClient
import com.venera.compose.gallery.domain.GalleryAutoPlay
import com.venera.compose.gallery.domain.GalleryGuard
import com.venera.compose.gallery.domain.GalleryMotion
import com.venera.compose.gallery.domain.GalleryPreload
import com.venera.compose.gallery.domain.GalleryVolumeKeys
import com.venera.compose.gallery.domain.coverSourceRect
import com.venera.compose.gallery.domain.handoffBodyAlpha
import com.venera.compose.gallery.domain.handoffPageAlpha
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlin.math.roundToInt
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import top.yukonga.miuix.kmp.basic.Text

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
    val guard = remember { ContentGuardManager.getInstance(context) }
    val maskMode by guard.nsfwMaskMode.collectAsState()

    // ── 大图页的四条行为档位（批次 C1，2026-09-29；方案 §一）──
    // 全都在 `GalleryViewerPolicies` 里算，这里只读档 + 接线：那三条判据要能上单测。
    val prefs = remember { VeneraPreferences.getInstance(context) }
    val keepScreenOn by prefs.galleryKeepScreenOn.collectAsState()
    val volumeKeyTurn by prefs.galleryVolumeKeyTurn.collectAsState()
    val autoPlaySec by prefs.galleryAutoPlaySec.collectAsState()
    val preloadMode by prefs.galleryPreload.collectAsState()
    // ── 批次 C2：动图该不该解动画、背景那一层画什么 ──
    val animatedMode by prefs.galleryAnimated.collectAsState()
    val backdrop by prefs.galleryBackdrop.collectAsState()
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

    // ── 左右翻页的上下文（用户 2026-09-26）──
    // 点开那一面墙的条目列表由 `GalleryCardsGrid` 在点击那一刻交过来（[GalleryViewerQueue]）：
    // 有它 = 可以左右翻，翻的就是刚才屏上那批、同一个顺序；没有它（深链进来、
    // 或上次留下的列表已经换过一轮）就退回单张模式，这一条自己去站方取。
    val wall = remember(site, postId) { GalleryViewerQueue.snapshot(site, postId) }
    val initialIndex = remember(wall, site, postId) {
        wall.indexOfFirst { it.site == site && it.id == postId }.coerceAtLeast(0)
    }
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
    // 逐张记 HD。用 `ArrayList<String>` 而不是 `mutableStateMapOf`：map 那套进不了
    // `rememberSaveable`（Bundle 认的是 Serializable / Parcelable 这一族），旋转一下就丢，
    // 而"翻回来 HD 还亮着"是这张图上唯一可见的档位状态，不该丢。
    var hdUids by rememberSaveable(site.name, postId) { mutableStateOf(ArrayList<String>()) }
    // 哪几张正处在缩放态（工具条与页码据此让位）。只在**跨过 1x 的那一瞬间**写一次 ——
    // 每帧写会让整页跟着每帧重组，而这件事一秒只可能发生一两次。
    val zoomedPages = remember(site, postId) { mutableStateMapOf<String, Boolean>() }

    val current = pages.getOrNull(pagerState.currentPage)
    val currentUid = current?.uid
    val preferHd = currentUid != null && hdUids.contains(currentUid)
    val zoomedIn = currentUid != null && zoomedPages[currentUid] == true

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

    // ── 自动连播（C1.3）：key 带着当前页 = 翻一页计时器自己重来，不做"暂停后恢复"那份额外状态 ──
    LaunchedEffect(pagerState.currentPage, autoPlaySecNow, infoOpen, zoomedIn, current?.isVideo, pages.size) {
        if (autoPlaySecNow <= 0) return@LaunchedEffect
        delay(autoPlaySecNow * 1000L)
        // 等着的这几秒里人可能已经放大、打开弹层、或自己翻走了 —— 走之前**再判一次**，
        // 按旧读数把人翻走是最难查的那类"它自己在动"。
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
    LaunchedEffect(currentUid, infoOpen) {
        val post = current ?: return@LaunchedEffect
        // 面板没开时不做这件事：一次翻页浏览都只为了看画，没有谁在等一个分组。
        if (!infoOpen) return@LaunchedEffect
        val dictionary = GalleryTagDictionary.getInstance(context)
        tagTranslations = dictionary.translations(post.tagList)
        val categories = GalleryTagCategories.getInstance(context).fetch(post.pageUrl)
        tagCategories = categories
        // **兜底只在站方判定没拿到时才查**，而且只兜画师一栏：那份离线表对 yande.re 只有 73%
        // 的名称覆盖，拿它补满五桶会把两三成标签挂进错桶；而它"判成画师而站方不判"实测 0 例。
        // 两套判据混在同一桶里是最难发现的一类错，所以这里宁可二选一。
        fallbackArtists = if (categories.isNullOrEmpty()) dictionary.artistNames(post.tagList)
        else emptySet()
    }

    // ── 收藏 ──
    // 直接读那份存档（不另存一份本地状态）：收藏页与这里读的是同一个 StateFlow，
    // 所以"在这一页取消、回收藏页还亮着"不可能发生。存档只有几百条，`any` 是遍历几十项。
    val favoritesStore = remember { GalleryFavoritesStore.getInstance(context) }
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
     * 所以各页都会陆续回报，按 uid 存才分得清谁是谁。
     */
    val frameBounds = remember(site, postId) { mutableStateMapOf<String, Rect>() }
    /** 打开时那一张的 uid —— 去程的落点、以及"垫帧只垫它"那几条都按它认。 */
    val initialUid = remember(wall, site, postId) { wall.getOrNull(initialIndex)?.uid }
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
        // 压根没有卡片当起点（深链、收藏页、反搜那些入口）：没有什么可等的，直接抬页。
        // 这一条短路存在的理由是**别让人家为一笔永远不会来的落点白等一笔预算**。
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
                snapshotFlow { initialUid?.let { frameBounds[it] } }.filterNotNull().first()
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
            // （深链、收藏页、反搜）、`PixelCopy` 没给像素、落点超过预算才量出来。
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
            GallerySite.YANDERE -> YandeReClient.getInstance(context).fetchById(postId)
            GallerySite.GELBOORU -> GelbooruClient.getInstance(context).fetchById(postId)
        }
        result.onSuccess { loaded ->
            // 取到了空 = 站方没有这条（被删/合并），要说话，不要留一屏黑。
            if (loaded == null) {
                error = "${site.displayName} 上没有 #$postId 这条（可能已被删除或合并）"
            } else {
                pages = listOf(loaded)
            }
        }.onFailure { error = it.message ?: "加载失败" }
    }

    /**
     * 这一张要不要打码。与一级那面墙**同一把判据**（[GalleryGuard.maskStateFor] +
     * [ContentGuardManager.findGalleryBlockedRule]）：大图页不能换一套匹配口径，
     * 否则会出现"墙上被挡掉、点进来却全裸"（或反之）的分叉。
     *
     * 从 val 改成"按页算"：左右翻之后屏上那张不再是打开时那张，判据必须跟着当前页走。
     */
    fun maskedOf(target: GalleryPost): Boolean = GalleryGuard.maskStateFor(
        post = target,
        mode = maskMode,
        blockedByUser = guard.findGalleryBlockedRule(
            author = target.author,
            tags = target.tagList,
        ) != null,
    ) == GalleryGuard.BLURRED

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
    var exitFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var exitStart by remember { mutableStateOf<Rect?>(null) }
    var exitTarget by remember { mutableStateOf<Rect?>(null) }
    val exitFly = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }

    /** 离场时整页（含压暗那一层）的透明度：**首程**淡出，与去程尾程那半共用同一个常量。 */
    fun exitFadeAlpha(): Float =
        if (!leaving) 1f else (1f - exitFly.value / FLIGHT_FADE_FRACTION).coerceIn(0f, 1f)

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

    fun leavePage() {
        if (leaving) return
        val post = current
        val box = post?.let { frameBounds[it.uid] }
        val target = post?.let { GalleryFlyIn.cardBoundsOf(it.uid) }
        if (post == null || box == null || target == null ||
            box.width <= 0f || box.height <= 0f || maskedOf(post)
        ) {
            onBack()
            return
        }
        leaving = true
        // 离场第一件事是**摘掉窗口模糊**：图要飞回的是清晰的那一屏，落在一张糊掉的卡片上不算"回去"。
        // 基类挂模糊走的是 addFlags，清掉即回到透明窗口底；"要重进页面才恢复模糊"在这里无所谓 ——
        // 本页马上 finish。状态栏图标同理跟着翻回来（底下那屏是浅色主题）。
        val host = view.context as? Activity
        host?.window?.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        host?.window?.let { WindowCompat.getInsetsController(it, view) }
            ?.isAppearanceLightStatusBars = !isDarkTheme
        GalleryFlyIn.captureRegion(view, box) { frame ->
            if (frame == null) {
                onBack()
                return@captureRegion
            }
            exitFrame = frame
            exitStart = box
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

    // 系统返回 / 侧滑手势：接管它，返回才有自家这一趟 hero。
    // 代价如实记账：预测式返回那半"边滑边看到整页缩小"的系统预览没有了，
    // 现在统一是"松手后图飞回卡片"。sheet 开着时那条优先级更高（enabled 互斥）。
    // ⚠️ 下滑关闭（[closePage]）**不走这条** —— 那时页面已经跟着手指滑出屏幕，
    // 再让图从屏幕外飞回来是自相矛盾；那条保持原样，由系统返回动画收尾。
    BackHandler(enabled = !infoOpen) { leavePage() }

    fun toggleChrome() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        showChrome = !showChrome
    }

    /**
     * 分享一条**原档**（图或视频），并把单页链接一并带上。
     *
     * ## 为什么直接给字节而不是给 URL
     *
     * 旧实现只发一行文本 `"站名 #id URL"` —— 用户拿到的不是图，是链接。
     * 现在取 `file_url` 的真实字节写入 `cacheDir/shared_images/`，
     * 经 FileProvider 以 `ACTION_SEND` 交给外部应用（附 `EXTRA_TEXT` 单页链接）。
     *
     * ## 三条口径，都是沿用既有裁决
     *
     * - **取原档**（`GallerySaver.fetchBytes`），与「保存」那条路同源同档 ——
     *   分享出去的和保存下来的必须是同一份。
     * - **原样落字节，不解码不重编码**：不用阅读器那条 `bitmap.compress(JPEG, 95)`，
     *   那会洗掉 png 的 alpha 并把原图降质（`GallerySaver` 顶部注释已明令禁止这条路）。
     * - **MIME 按站方扩展名**（`GallerySaver.mimeOf`），未知扩展名退 `application/octet-stream`
     *   而不是猜一个。
     *
     * ## 视频
     *
     * 视频条目**照原片分享**（mp4/webm）。不把它换成"分享链接"—— 那枚钮在两种条目上
     * 长得一样，行为却不同，用户会以为按钮坏了。
     *
     * ⚠️ 视频原片实测 16~26 MB，分享前要真下载一次（不命中 Coil 缓存），
     * 所以走 [sharing] 标志防重入，并由 Toast 报失败原因 —— 静默失败最坏。
     */
    fun share(target: GalleryPost) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (sharing) return
        sharing = true
        scope.launch {
            GallerySaver.fetchBytes(context, target)
                .onSuccess { bytes ->
                    // 类型**写死成 Uri**，并且用 getOrElse 而不是 onFailure：
                    // `runCatching{…}.onFailure{…}` 交出的是 `Result<Uri>` 而**不是** `Uri`，
                    // 于是下面那句 `putExtra(EXTRA_STREAM, uri)` 静默挑中了
                    // `putExtra(String, Serializable)` 那个重载（kotlin.Result 声明了 Serializable）——
                    // 编译通过、真机当场 "Parcelable encountered IOException writing serializable
                    // object (name = kotlin.Result)"，分享**一次都没有成功过**。
                    // 显式类型 + getOrElse 让这一类错落在编译期，而不是落在用户手上。
                    val uri: Uri = runCatching {
                        // 落盘要在 IO 上：视频原片实测 16~26 MB，
                        // 跟在 Main.immediate 的续点上写就是拿主线程写几十兆，能卡出 ANR。
                        withContext(Dispatchers.IO) {
                            val dir = File(context.cacheDir, "shared_images").apply { if (!exists()) mkdirs() }
                            val name = "${target.site.routeKey}-${target.id}.${target.fileExt.ifBlank { "bin" }}"
                            val file = File(dir, name)
                            file.outputStream().use { it.write(bytes) }
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        }
                    }.getOrElse { e ->
                        Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
                        return@onSuccess
                    }
                    runCatching {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = GallerySaver.mimeOf(target)
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, "${target.site.displayName} #${target.id} ${target.pageUrl}")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享图片"))
                    }.onFailure { e ->
                        Toast.makeText(context, "分享没打开：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .onFailure { e ->
                    Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
                }
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
                .onFailure { Toast.makeText(context, "保存失败：${it.message}", Toast.LENGTH_LONG).show() }
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
                    // 离场那一半（首程淡出，同一个 FLIGHT_FADE_FRACTION）：乘上去而不是另起一支 ——
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
                        Spacer(modifier = Modifier.height(statusBarTop + tokens.spacing.space5))
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
                            key = { index -> pages.getOrNull(index)?.uid ?: index },
                            modifier = Modifier.weight(1f),
                        ) { page ->
                            val pagePost = pages[page]
                            GalleryViewerPage(
                                post = pagePost,
                                masked = maskedOf(pagePost),
                                preferHd = hdUids.contains(pagePost.uid),
                                animated = animateGifs,
                                imageLoader = imageLoader,
                                // 垫着的那一帧只属于**打开时那一张**：翻到别处它已经不成立了。
                                // 而且只在飞行**结束之后**才摆进画面框 —— 途中它正该在手指点过的那张卡的位置上，
                                // 框里同时摆一份就会看见"两个起点"。
                                cardFrame = if (page == initialIndex && flightDone) cardFrame else null,
                                onReady = { if (page == initialIndex) contentReady = true },
                                // **每一页**都回报（按 uid 存）：去程要"打开那一张"的落点，
                                // 返回程要"当前页"的起点 —— 翻过页之后这两个不是同一张。
                                onImageBounds = { rect ->
                                    // 落点**只认有面积的那一版**。`boundsInWindow()` 报的是裁到窗口
                                    // 可见部分之后的矩形（真机读数：`size=1016x1576` 恒定而 window 高只有 99），
                                    // 所以页面只要还站在屏幕外，落点就是 `0×0` —— 认了它，飞行体会缩向
                                    // 屏幕左上角消失。姿态已按 [Entrance] 修对（等待期间站在原位），
                                    // 这一道留着当保险：一个零面积的目的地永远不该被拿去飞。
                                    if (rect.width > 0f && rect.height > 0f) {
                                        if (page == initialIndex && frameBounds[pagePost.uid] == null) {
                                            android.util.Log.i("FlyProbe", "first landing rect=$rect")
                                        }
                                        frameBounds[pagePost.uid] = rect
                                    }
                                },
                                onZoomedChange = { zoomedPages[pagePost.uid] = it },
                                onHdError = { reason ->
                                    // HD 档取不到要说出来，并且**把钮拨回去**：
                                    // 留着 HD 亮着、画的却是原档，就是一个假开关。
                                    hdUids = ArrayList(hdUids).also { it.remove(pagePost.uid) }
                                    Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                                },
                                onImageTap = ::toggleChrome,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = tokens.spacing.screenHorizontal),
                            )
                        }
                        // 工具条是浮层，所以底部留白自己给，图不会被它盖住。
                        Spacer(modifier = Modifier.height(VeneraSpacing.bottomBarClearance))
                    }

                    // 页码跟着 chrome 一起显隐，位置在左上角（底部四个动作已经占满那一条）。
                    AnimatedVisibility(
                        visible = pages.size > 1 && showChrome && !zoomedIn,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = tokens.spacing.screenHorizontal,
                                top = statusBarTop + tokens.spacing.space5,
                            ),
                    ) {
                        GalleryViewerPageCounter(
                            page = pagerState.currentPage,
                            total = pages.size,
                        )
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
                                    if (!set.remove(target.uid)) set.add(target.uid)
                                }
                            },
                            onDownload = { download(target) },
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
                        icon = Icons.Outlined.Image,
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
        // `fly.value` / `exitFly.value` 与各页的画面框都在 drawBehind 的 lambda 里读 = 绘制阶段读，
        // 每帧只重绘这一块，不把整页拖进重组（与上面 graphicsLayer 同一条口径）。
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
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // 落点还没量出来时**冻在封面原位**垫着（这正是这一层最初"垫着不空"的职责），
                        // 量到了才起程。直接不画的话，等落点那几帧屏上只剩窗口模糊。
                        val end = initialUid?.let { frameBounds[it] } ?: startRect
                        val t = fly.value.coerceIn(0f, 1f)
                        val left = startRect.left + (end.left - startRect.left) * t
                        val top = startRect.top + (end.top - startRect.top) * t
                        val width = startRect.width + (end.width - startRect.width) * t
                        val height = startRect.height + (end.height - startRect.height) * t
                        // 源像素按 **cover 语义**取（保持它自己的比例、居中裁切），**不是**拉伸填满：
                        // 起点框与落点框的比例并不总是同一档（墙上卡片被夹在 0.4~2.5、预览行的封面固定
                        // 124×170，而落点框按原图比例定）。拉伸会把整块内容拉扯变形，交棒那一下再把形状
                        // 还回来，读起来就是"闪"。算式抽在判据层（`coverSourceRect`，带单测）——
                        // 比例一致时算出来的就是整张位图，与老写法逐像素等价。
                        val src = coverSourceRect(frame.width, frame.height, width, height)
                        // 圆角必须跟着走：飞行体本身画的是**矩形**，而落点那一框是 `shape.large` 圆角。
                        // 不裁它，交棒那一刻就是"方角 → 圆角"的一次跳变 —— 2026-10-01 12:09 那两张
                        // 真机截图量出来的就是这条：飞行中四角全是直角、到位后四角全是圆角。
                        // 半径按当前宽度等比缩放，所以落到终点时**逐像素等于框的圆角**，形状差归零。
                        val radius = flyCornerRadius.toPx() *
                            (width / end.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        clipPath(
                            Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        left = left,
                                        top = top,
                                        right = left + width,
                                        bottom = top + height,
                                        cornerRadius = CornerRadius(radius, radius),
                                    ),
                                )
                            },
                        ) {
                            drawImage(
                                image = frame,
                                srcOffset = IntOffset(src.left, src.top),
                                srcSize = IntSize(src.width, src.height),
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                                // 去程**全程不淡**（2026-10-01 第四次报"打开还是闪"，录屏量出来的）：
                                // 老写法让它在这条弹簧的尾段淡出，可"弹簧的百分比"不是"路程" ——
                                // 页面同时开始淡入，两份图错着位叠在一起 = 重影，而且两层 alpha
                                // 相乘后总不透明度只有 0.75，图还会先暗一下。现在它整程不透明，
                                // 淡出挪到**飞完之后的交棒后半程**（那时页面已不透明，见 [handoff]）。
                                alpha = if (entranceMode == Entrance.FLY) {
                                    handoffBodyAlpha(handoff.value)
                                } else {
                                    1f
                                },
                            )
                        }
                    },
            )
        }

        // ── 返回程那一帧：画面框 → 墙上那张卡 ──
        // 与上面那一层是镜像：去程画"卡片 → 画面框"，这一层画"画面框 → 卡片"。
        // **不淡入也不淡出**：起点是屏上那一帧的真实像素（与它盖住的那张图逐像素相同），
        // 终点正好是卡片封面那一块，finish 之后卡片把它接住 —— 两头都无缝，中间加淡入淡出
        // 反而会把身后的东西露出来。
        val exitFrom = exitStart
        val exitTo = exitTarget
        val exitBitmap = exitFrame
        if (exitBitmap != null && exitFrom != null && exitTo != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val t = exitFly.value.coerceIn(0f, 1f)
                        val left = exitFrom.left + (exitTo.left - exitFrom.left) * t
                        val top = exitFrom.top + (exitTo.top - exitFrom.top) * t
                        val width = exitFrom.width + (exitTo.width - exitFrom.width) * t
                        val height = exitFrom.height + (exitTo.height - exitFrom.height) * t
                        // 源像素同样是 cover 语义：起点是画面框的截屏、终点是卡片封面那一块，
                        // 两者比例未必同档（卡封面被夹过、预览行那条还是固定的 124×170）。
                        val src = coverSourceRect(exitBitmap.width, exitBitmap.height, width, height)
                        // 圆角同一条口径（按当前宽度等比缩放）：到终点时等于框的圆角，
                        // 与卡片自己的圆角同档，落回去那一瞬间没有形状差。
                        val radius = flyCornerRadius.toPx() *
                            (width / exitTo.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        clipPath(
                            Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        left = left,
                                        top = top,
                                        right = left + width,
                                        bottom = top + height,
                                        cornerRadius = CornerRadius(radius, radius),
                                    ),
                                )
                            },
                        ) {
                            drawImage(
                                image = exitBitmap,
                                srcOffset = IntOffset(src.left, src.top),
                                srcSize = IntSize(src.width, src.height),
                                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                            )
                        }
                    },
            )
        }

        if (infoOpen && current != null) {
            GalleryInfoSheet(
                post = current,
                categories = tagCategories,
                fallbackArtistNames = fallbackArtists,
                tagTranslations = tagTranslations,
                imageLoader = imageLoader,
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
    val zoomState = rememberZoomableState()
    LaunchedEffect(zoomState) {
        snapshotFlow { (zoomState.zoomFraction ?: 0f) > 0f }
            .distinctUntilChanged()
            .collect { onZoomedChange(it) }
    }
    GalleryViewerMedia(
        post = post,
        masked = masked,
        preferHd = preferHd,
        animated = animated,
        imageLoader = imageLoader,
        zoomState = zoomState,
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
 * **离场**时做交叉淡出的那一段（占整条返回程的比例）。
 *
 * 去程曾经也用它（把页面的淡入挂在 `fly` 尾段上），2026-10-01 录屏把它量掉了：
 * 弹簧的"百分比"不是"路程"，`fly=0.75` 时飞行体还在半路，交叉淡入就变成**重影 + 暗一下**。
 * 去程现在走 [HANDOFF_MS] 那一档（飞完才开始交棒）。返回程没有这个问题 ——
 * 离场是"起点现截一帧、原地起飞"，飞行体与页面在首帧**逐像素相同**，交叉淡出不会露馅。
 */
private const val FLIGHT_FADE_FRACTION = 0.25f

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
    zoomState: ZoomableState,
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
            val window = coordinates.boundsInWindow()
            // `size` 是布局给的尺寸（不含 graphicsLayer 位移），`window` 含位移。
            // 两者一比就够分：size 在长 = 布局在抖；size 恒定而 window 在动 = 入场平移在动落点。
            android.util.Log.i(
                "FlyProbe",
                "landing size=${coordinates.size.width}x${coordinates.size.height} window=$window",
            )
            onImageBounds(window)
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

            Box(
                Modifier
                    .size(boxWidth, boxHeight)
                    .then(boundsReporter)
                    .clip(RoundedCornerShape(tokens.shape.large)),
            ) {
                // 三档叠画（用户 2026-09-26 点名）：谁先到谁先显形，后到的直接盖上去。
                // 三档是同一构图、只是分辨率不同，所以盖上去读起来是"变清晰"，不是"跳一下"。
                //
                // 缩放与点击挂在**这层容器**上，不再挂在最上面那一档：老写法只有最顶那层带
                // zoomable，于是"中档还没回来"的那段时间里点画面切不动 chrome —— 现在只要
                // 有任何一档在屏上就点得动。
                Box(
                    Modifier
                        .fillMaxSize()
                        .zoomable(state = zoomState, onClick = { onImageTap() })
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
                if (cardFrame != null && !largeSettled) {
                    // 这一帧是**卡片封面那一块**（不是整卡，见 GalleryFlyIn 文件头注），与这块框共用
                    // 同一个 `cardRatio`，所以 Crop 不改变构图 —— 大图到位时是"变清晰"，不是"跳一下"。
                    //
                    // ⚠️ 撤它的条件**只有中档落位**，不能再带"快照档也落位"：快照档比它还糊，
                    // 交棒到它等于先把图换糊一次（同上面 `onReadyChange` 那处头注）。
                    Image(
                        bitmap = cardFrame,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
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
            }

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
internal fun galleryCacheKey(kind: String, post: GalleryPost) = "gallery-$kind-${post.site.routeKey}-${post.id}"

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
