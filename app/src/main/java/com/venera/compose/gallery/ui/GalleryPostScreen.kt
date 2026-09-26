package com.venera.compose.gallery.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.gallery.data.GalleryFavoritesStore
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySaver
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GelbooruClient
import com.venera.compose.gallery.data.YandeReClient
import com.venera.compose.gallery.domain.GalleryGuard
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlin.math.roundToInt
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val imageLoader: ImageLoader = remember { GalleryImageLoader.get(context) }
    val guard = remember { ContentGuardManager.getInstance(context) }
    val maskMode by guard.nsfwMaskMode.collectAsState()

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

    // ── 向上滑入（照 Breadboard 的 `OffsetBasedLargeImageView`：整页从屏幕下沿抬上来）──
    // 平台没有跨 activity 的共享图像 API（实测本机 android.jar 33~36 里只有启动图那套），
    // 所以抬上来的这一层由 GalleryFlyIn 递过来的"卡片那一帧"占位，大图到位就换掉；
    // 系统那套"打开"转场已在 openGalleryPost 里压掉，免得两套动画叠着跑。
    // 弹簧与下面的下滑关闭同一档（StiffnessMediumLow）：进与出手感对称，且不另造数字。
    val cardFrame = GalleryFlyIn.payload
    val entrance = remember { Animatable(1f) }
    var contentReady by remember(site, postId) { mutableStateOf(false) }
    LaunchedEffect(site, postId) {
        entrance.snapTo(1f)
        entrance.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
    }
    // 占位那一帧**换到位才交还**：早交会在"页到位"与"图加载好"之间露出一块玻璃，
    // 观感就是闪一下。取不到详情时也要交 —— 不然这张位图一直攥在手里没人再消费它。
    LaunchedEffect(contentReady, error) {
        if (cardFrame != null && (contentReady || error != null)) GalleryFlyIn.consume()
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
                    val uri = runCatching {
                        val dir = File(context.cacheDir, "shared_images").apply { if (!exists()) mkdirs() }
                        val name = "${target.site.routeKey}-${target.id}.${target.fileExt.ifBlank { "bin" }}"
                        val file = File(dir, name)
                        file.outputStream().use { it.write(bytes) }
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    }.onFailure { e ->
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
                        }
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
            .fillMaxSize(),
    ) {
        // 背景**不画东西**：这一页在独立 Activity 里，窗口是透的，
        // 系统 blur-behind 已经把后面那一屏一级列表实时糊好了（[GalleryPostActivity]）。
        // 这里只补一层压暗 —— 只糊不压暗时，照片墙花花绿绿会把主体那张图吃掉。
        // 这一层**不参与滑入**：玻璃是"后面那一屏"，页面向上抬时它当然不动，
        // 动的只有浮在上面的那些，这才是"图从玻璃后面升起来"的感觉。
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = BACKDROP_SCRIM_ALPHA)))

        Box(
            Modifier
                .fillMaxSize()
                // `entrance.value` 在 graphicsLayer 的 lambda 里读 = 绘制阶段读，
                // 每帧只重绘不重组；拿到组合里读会让整页每帧重走一遍组合。
                .graphicsLayer { translationY = entrance.value * screenHeightPx },
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
                        // `beyondViewportPageCount = 1`：把左右邻页也组出来开始取图，
                        // 翻过去就能接上（第一档永远命中隔壁页刚下好的缓存）。
                        // key 用 uid：两站 id 会撞，key 也跟着撞的话翻页时状态会串。
                        HorizontalPager(
                            state = pagerState,
                            beyondViewportPageCount = 1,
                            key = { index -> pages.getOrNull(index)?.uid ?: index },
                            modifier = Modifier.weight(1f),
                        ) { page ->
                            val pagePost = pages[page]
                            GalleryViewerPage(
                                post = pagePost,
                                masked = maskedOf(pagePost),
                                preferHd = hdUids.contains(pagePost.uid),
                                imageLoader = imageLoader,
                                // 垫着的那一帧只属于**打开时那一张**：翻到别处它已经不成立了。
                                cardFrame = if (page == initialIndex) cardFrame else null,
                                onReady = { if (page == initialIndex) contentReady = true },
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

        if (infoOpen && current != null) {
            GalleryInfoSheet(
                post = current,
                onDismiss = { infoOpen = false },
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
    imageLoader: ImageLoader,
    /** 一级卡片那一帧（窗口坐标裁出来的位图）。只有"打开时那一张"才拿得到。 */
    cardFrame: ImageBitmap?,
    onImageTap: () -> Unit,
    /** 开门档或中档已落位（成功或失败都算）—— 页面据此交还垫着的那一帧。 */
    onReady: () -> Unit,
    onHdError: (String) -> Unit,
    onZoomedChange: (Boolean) -> Unit,
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
        imageLoader = imageLoader,
        zoomState = zoomState,
        cardFrame = cardFrame,
        onImageTap = onImageTap,
        onReadyChange = { onReady() },
        onHdError = onHdError,
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
    imageLoader: ImageLoader,
    zoomState: ZoomableState,
    onImageTap: () -> Unit,
    /** 一级卡片那一帧（窗口坐标裁出来的位图）—— 滑入期间垫在最上面。 */
    cardFrame: ImageBitmap?,
    /** 开门档或中档已落位（成功或失败都算）—— 垫着的那一帧到这里才交还。 */
    onReadyChange: (Boolean) -> Unit,
    /** HD 档取不到。调用方要把 HD 钮拨回原档并说一句话，不能静默换档。 */
    onHdError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val ratio = post.cardRatio.takeIf { it > 0f } ?: tokens.spacing.coverAspectRatio

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 定框：横图按高铺满、竖图按宽铺满，另一个方向留白。
        // 这样圆角是贴着图的。
        val boxRatio = maxWidth / maxHeight
        val sizedByWidth = ratio >= boxRatio
        val boxWidth = if (sizedByWidth) maxWidth else maxHeight * ratio
        val boxHeight = if (sizedByWidth) maxWidth / ratio else maxHeight

        if (post.isVideo) {
            // 视频没有"再高一档"可切，静帧到位即算落位（滑入不用等原片缓冲完）。
            Box(Modifier.size(boxWidth, boxHeight)) {
                GalleryVideoViewer(post = post, masked = masked, imageLoader = imageLoader)
                LaunchedEffect(post.uid) { onReadyChange(true) }
            }
        } else {
            val fastUrl = post.fastUrl
            val fastRequest = remember(post.uid, fastUrl) {
                if (fastUrl.isBlank()) null else galleryFastRequest(context, fastUrl)
            }
            val largeRequest = remember(post.uid) { galleryLargeRequest(context, post) }
            val fileRequest = remember(post.uid) { galleryFileRequest(context, post) }

            // 三档各自的落地读数。`onLoading` 必须把值打回 false：切 HD、翻回来重取都要
            // 重新点亮加载态，一次置位就再也不灭的读数等于没有加载态。
            // 快照档**只认成功**：它失败时该继续摆骨架（中档还在路上，别先摆一个空框）。
            var fastLoaded by remember(post.uid) { mutableStateOf(false) }
            var largeSettled by remember(post.uid) { mutableStateOf(false) }
            var hdSettled by remember(post.uid) { mutableStateOf(false) }
            // "第一档落位"垫到**快照档或中档任一落位**为止：快照档通常白拿
            // （与网格卡片同址，缓存直接命中），它一到位那帧位图就能交还，
            // 不必再攥到中档取回来 —— 位图攥得越久，越容易在巨幅图上叠出一次 OOM。
            LaunchedEffect(fastLoaded, largeSettled) {
                if (fastLoaded || largeSettled) onReadyChange(true)
            }

            Box(
                Modifier
                    .size(boxWidth, boxHeight)
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
                if (cardFrame != null && !fastLoaded && !largeSettled) {
                    // 卡片与这块框同一个 `cardRatio`，所以 Crop 不改变构图 ——
                    // 大图到位时是"变清晰"，不是"跳一下"。
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
internal fun galleryLargeRequest(context: Context, post: GalleryPost): ImageRequest =
    ImageRequest.Builder(context)
        .data(post.largeUrl)
        .memoryCacheKey(galleryCacheKey("large", post))
        .diskCacheKey(galleryCacheKey("large", post))
        .build()

/**
 * `file_url` 原图档。两条防护（口径抄 PixEz 的注释理由）：
 *
 * - `size(4096) + INEXACT`：解码边长上限。注意 Coil 的 `size` 是**下限**语义，
 *   这条只挡"比 4096 还巨"的画作瞬间撑爆堆，不是省内存的主手段。
 * - `memoryCachePolicy(READ_ONLY)`：**87 MB 的位图不进内存缓存**（实测最大一例
 *   3907×5600 解码后约 87 MB）。关掉写入，页面一关这块就能被回收，
 *   否则它会长期占着画廊那 64 MB 预算、把一级的缩略图全挤出去。
 */
internal fun galleryFileRequest(context: Context, post: GalleryPost): ImageRequest =
    ImageRequest.Builder(context)
        .data(post.fileUrl)
        .memoryCacheKey(galleryCacheKey("file", post))
        .diskCacheKey(galleryCacheKey("file", post))
        .size(HD_DECODE_EDGE_PX, HD_DECODE_EDGE_PX)
        .precision(Precision.INEXACT)
        .memoryCachePolicy(CachePolicy.READ_ONLY)
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
