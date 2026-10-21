package com.venera.compose.gallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.ImageBitmap
import com.venera.compose.components.venera.veneraCoverCornerRadius
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.venera.compose.components.ChromeCaptureSuppress
import com.venera.compose.components.reportChromeBand
import com.venera.compose.components.venera.LocalTopBarBackdrop
import com.venera.compose.components.venera.VeneraShimmer
import com.venera.compose.components.venera.VeneraIconButton
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.VeneraTopBarBackdrop
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySaver
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.gallery.data.PixivComment
import com.venera.compose.gallery.data.PixivPage
import com.venera.compose.gallery.data.PixivWorkDetail
import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.domain.GalleryArtistFollows
import com.venera.compose.gallery.domain.PixivCommentSegment
import com.venera.compose.gallery.domain.PixivPageSeam
import com.venera.compose.gallery.domain.PixivWorkFact
import com.venera.compose.gallery.domain.PixivWorkPage
import com.venera.compose.gallery.domain.GalleryExitGestureCommand
import com.venera.compose.gallery.domain.GalleryExitGestureEvent
import com.venera.compose.gallery.domain.GalleryExitGesturePhase
import com.venera.compose.gallery.domain.nextExitGesture
import com.venera.compose.gallery.domain.exitBodyAlpha
import com.venera.compose.gallery.domain.exitPageAlpha
import com.venera.compose.gallery.domain.handoffBodyAlpha
import com.venera.compose.gallery.domain.handoffDetailAlpha
import com.venera.compose.gallery.domain.HANDOFF_DETAIL_LEAD_FLY
import com.venera.compose.gallery.domain.HANDOFF_DETAIL_MS
import com.venera.compose.gallery.domain.handoffPageAlpha
import com.venera.compose.gallery.domain.pixivEmojiId
import com.venera.compose.gallery.domain.pixivEmojiUrl
import com.venera.compose.gallery.domain.sideSlideTopOffset
import com.venera.compose.gallery.domain.splitPixivComment
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * **pixiv 作品页**（2026-10-18 落地，站源独用）。
 *
 * 与图库三站那套 [GalleryPostScreen] 完全隔离：那颗继续按"一张 = 一屏、左右翻邻居"来做，
 * 这一颗按 pixiv 自己的形状来做 ——
 *
 * - **纵轴 = 同一作品的多页自然接续**（唯一一档）：滚过最后一页，详情段接在同一条滚动流里，
 *   全程**一个滚动容器**（不是抽屉里的第二个滚动者）；流末尾才是「下一作品」那一格提醒；
 * - **横轴 = 换作品**：吃"把我送进来的那面墙"的本地顺序（2026-10-18 现量：详情腿没有
 *   `nextUrl`/`previousUrl`/`hasNext`、`seriesNavData` 恒 null ⇒ 站方给不出邻居，只能本地数）；
 * - 页间距两档（[PixivPageSeam]）。**从前还有一档"两轴互换"（横着一页一页翻），2026-10-18
 *   第四批按用户那句「还是直接删了横着一页一页那个模式切换吧，只留竖着连续翻页模式」整档撤掉** ——
 *   枚举、偏好键、设置页那一行与屏上那一支一起没，不留半套。
 *
 * 底下复用的还是同一批能力：取流走 [GalleryImageLoader] 那颗 ImageLoader、收藏走
 * `GalleryFavoritesStore`、下载走 [GallerySaver]、分享走 [shareGalleryPost]；画师那一行的**关注**走
 * `ports.follows`（与「画廊 → 关注画师」同一份存档），**屏蔽**走 `ports.contentGuard.addRule("AUTHOR", …)`
 * —— 存的是 AUTHOR 规则而不是画师卡那条 TAG，成因写在 `artistActionsOf` 头上。
 *
 * ⚠️ 两件**故意没摆**的，别当缺口：
 * 1. 应用内评论列表 —— 匿名腿四形状现量 404，站方只给得到**数**；屏上念数的地方只有一处
 *    （动作行那枚评论，同排把出口一起带上），详情段里不再重复念第二遍。
 * 2. 站方"上一张/下一张" —— 深链直开时横轴整轴不摆，页码那一格留空，不说假话。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixivWorkScreen(
    postId: Long,
    onBack: () -> Unit,
    /** 交站点 + 作者名 + 出处地址，由宿主开画师介绍页（与旧看图器同一枚回调形状）。 */
    onOpenArtist: (GallerySite, String, String) -> Unit,
    /** 点「相关图片」里的一张：本屏已把那一排写进 [GalleryViewerQueue]，宿主换号重开这一屏。 */
    onOpenWork: (Long) -> Unit,
    /**
     * 交旧看图器（那颗 GalleryPostActivity）的两条出口（2026-10-18 第二批加了第二条）：
     * 1) 动图 ugoira —— 那一颗会解逐帧动画，这一屏只画得出静帧；
     * 2) 页轴（点某一页要放大看）—— 调用方先把这一作的几页写进 GalleryPageAxis，
     *    那一层读到的墙就是这几页，左右翻翻不出别条作品。
     * 两条都只交一个作品号：那一层自己去交接件里读它该翻什么。
     */
    onOpenViewer: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    // 去程起点那一帧要 PixelCopy **这一扇窗口**已经合成的表面（与墙上那一跳同一口径）。
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val tokens = VeneraTokens
    val ports = remember { GalleryPorts.of(context) }
    // 那两档读的是隔壁那颗 `PixivWorkPreferences`，不挂在跨端共享的 `GalleryPreferences` 上：
    // 桌面逐枚实现那颗读面，而这一屏桌面没有 ⇒ 挂进去就是替每一档各写一个没人读的 getter。
    // 空槽在 Android 装配口不可能出现（每次都交 `galleryPrefs`）；真出现 = 装配口漏了那一行，
    // 当场点名比替它挑一档默认值诚实 —— 挑了默认值会把"漏接线"读成"用户选了某一档"。
    val workPrefs = requireNotNull(ports.pixivWork) {
        "GalleryPorts.pixivWork 没接线：Android 装配口（GalleryPortsAndroid.createPorts）少交那一颗，" +
            "这一屏的两档没有读处"
    }
    val seam by workPrefs.pageSeam.collectAsState()
    val detailsInFlow by workPrefs.detailsInFlow.collectAsState()
    val aiBadgeOn by ports.prefs.galleryAiBadge.collectAsState()
    val favorites by ports.favorites.favorites.collectAsState()
    val imageLoader: ImageLoader = remember { GalleryImageLoader.get(context) }
    val pixiv = ports.pixiv

    // 横轴：把我送进来的那面墙。认不出邻居（深链、或列表已换过一轮）⇒ 空表 ⇒ 那一轴不摆。
    val works = remember(postId) { GalleryViewerQueue.snapshot(GallerySite.PIXIV, postId) }
    var workIndex by remember(postId) {
        mutableIntStateOf(works.indexOfFirst { it.id == postId }.coerceAtLeast(0))
    }
    val pagerState = rememberPagerState(initialPage = workIndex) { works.size.coerceAtLeast(1) }
    LaunchedEffect(pagerState) { snapshotFlow { pagerState.currentPage }.collect { workIndex = it } }

    val details = remember(postId) { mutableStateMapOf<Long, PixivWorkDetail>() }
    val failures = remember(postId) { mutableStateMapOf<Long, String>() }
    val related = remember(postId) { mutableStateMapOf<Long, List<GalleryPost>>() }
    /**
     * 评论那一节的状态，按作品号存（翻到下一件作品各自一份）。
     *
     * 与 [details] / [related] 同一个理由：宿主的换作品是**原地换号重开**（`recreate`），
     * 但横轴翻页是在同一屏里换 `slot` —— 那时不该把上一件的评论贴到这一件身上。
     */
    val commentStates = remember(postId) { mutableStateMapOf<Long, PixivCommentsState>() }
    /** 登录态只从 `authState` 那一条流读（与顶栏那颗头像、首页「已关注」同一源）。 */
    val pixivAuth = ports.pixivAccount?.authState?.collectAsState()?.value ?: PixivAuthState.Anonymous
    var retryTick by remember(postId) { mutableIntStateOf(0) }
    var sheetOpen by rememberSaveable(postId) { mutableStateOf(false) }
    // 抽屉的状态要 hoist 出来 remember：写在 if 分支里等于每次进那一支都新建一份，
    // 与 GalleryInfoSheet 同一口径（那颗也是把 SheetState 放在页面这一层）。
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var tagsExpanded by rememberSaveable(postId) { mutableStateOf(false) }

    val currentId = works.getOrNull(workIndex)?.id ?: postId
    val detail = details[currentId]
    val pages = detail?.pages.orEmpty()

    /**
     * 评论这一节展开没有（按**当前这一位**存，横翻换作品就归零）。
     *
     * 为什么由宿主持有而不是这一节自己记：这一节有**两个宿主**（下面那条滚动流 + 右上角「更多信息」
     * 那一层），各记一份就地展开的开关，用户在抽屉里点了「更多评论」、回到流里又只剩一条 —— 两把尺。
     */
    var commentsExpanded by remember(currentId) { mutableStateOf(false) }

    // 四枚动作、画师那一行的两枚把手、头像那一条腿 —— 三枚装配都住在 `PixivWorkInfoPanel.kt`，
    // 因为「更多信息」那一层现在有两个宿主（这一屏 + 大图播放器那一版面板）。留一份抄本在
    // 另一颗文件里就是等着分叉的那两处，而它们各自连着真写口（收藏、关注、屏蔽、头像落盘）。
    val artistAvatar = pixivAvatarUrl(detail)

    /**
     * 点图 = 交大图播放器，而**只翻这一作的这几页**（用户 2026-10-18：
     * 「支持左右滑动切换本张图片，不能切换其他作品，HD 才显示原图」）。
     *
     * 走 [GalleryPageAxis] 而不是 [GalleryViewerQueue]：那颗装的是"把我送进来的邻居作品"，
     * 这里要的是"同一作的第 2、3、4 页"，两件事混一颗就会互相 `set` 掉。
     * 墙由 domain 那颗纯函数长出来（`PixivWorkPage.pageWallOf`：中档走 large 档、原图只住 fileUrl、
     * 逐页宽高与页位）—— 屏上不自己拼四份 copy，那种拼法错一格只有真机能看见。
     *
     * ⚠️ [rect] 是**这一页此刻在屏上的那一块**（窗口坐标），必须就地递给 [GalleryFlyIn.capture]。
     * 那颗交接件是进程内单例，而"把我送进这一屏"的那一笔（墙上那张卡）谁都没清过它 ——
     * 只有大图播放器会在"飞完 + 交棒播完"之后 `consume()`，这一屏压根不读它。
     * 于是点图那一刻它手里还攥着**墙上那张卡的矩形与那一帧**，播放器照着它飞 = 从一块不在
     * 这一屏的位置起飞、带的还是缩略那一档的构图（用户 2026-10-18 报的"进大图错位很严重"）。
     * 重新 capture 一次把矩形与像素一起换成本屏这一页；量不到矩形时**清掉**而不是留着 ——
     * 那一档播放器老实退回"整页抬上来"，宁可不飞也不飞错地方。
     */
    val openPages: (PixivWorkDetail?, Int, Rect?) -> Unit = { target, index, rect ->
        if (target != null) {
            GalleryPageAxis.open(
                GalleryPageAxis.Wall(
                    site = GallerySite.PIXIV,
                    postId = target.post.id,
                    startIndex = index,
                    pages = pageWallOf(target),
                    panelData = target,
                    infoPanel = ::pixivWorkInfoPanel,
                ),
            )
            if (rect != null) GalleryFlyIn.capture(view, rect) else GalleryFlyIn.consume()
            onOpenViewer(target.post.id)
        }
    }

    /** 屏底摘要条那一条动作的下载那一枚：作品页自己持有进度，不借用播放器那一层的。 */
    val saveWork: (PixivWorkDetail) -> Unit = { one -> scope.launch { GallerySaver.save(context, one.post) } }

    LaunchedEffect(currentId, retryTick) {
        if (pixiv == null || details.containsKey(currentId)) return@LaunchedEffect
        failures.remove(currentId)
        // 票据在这一刻现取（不缓存、不进任何上屏消息）：屏上那些卡的来源是登录腿，
        // 详情只走匿名腿就会在已登录的设备上念"这一条没有图"（2026-10-18 第四批第七件）。
        pixiv.workDetail(currentId, ports.pixivAccount?.bearerOrNull())
            .onSuccess { head ->
                if (head == null) {
                    // 只念"没给图 + 没竖旗标"这一档；被挡的四种由取数那一层带句子抛上来，
                    // 走下面 onFailure 那一句。从前这三种病盖成同一句「被删或被锁」，
                    // 用户在已登录的机器上读到它就只剩"我明明登录了"（第四批第七件）。
                    failures[currentId] = "站方答上了，可这一条没给图，也没说为什么（可能已被删或还在审核）"
                } else {
                    details[currentId] = head
                }
            }
            .onFailure { failures[currentId] = it.message ?: "取这一条的时候没接通" }
    }

    // ── 评论（批 231-F，2026-10-19）：**只有登录腿拿得到正文** ──
    // 匿名四形状 404（见 `PixivAppClient.illustComments` 头注），所以这里三档分明：
    // 未登录 → NeedLogin（说一句"登录才看得到"，不摆空排）；
    // 有票据 → 取第一页；
    // 取失败 → Failed（站方原话，401/403 由 `execute` 分开念）。
    LaunchedEffect(currentId, retryTick, pixivAuth) {
        if (pixiv == null) return@LaunchedEffect
        if (pixivAuth !is PixivAuthState.SignedIn) {
            commentStates[currentId] = PixivCommentsState.NeedLogin
            return@LaunchedEffect
        }
        if (commentStates[currentId] is PixivCommentsState.Loaded) return@LaunchedEffect
        val bearer = ports.pixivAccount?.bearerOrNull()
        if (bearer == null) {
            // 票据这一会儿取不到是**这一刻**的事，不记账成失败 —— 说清去哪重登。
            commentStates[currentId] = PixivCommentsState.Failed(
                "这枚登录票据这一会儿取不到（可能刚过期）—— 点顶栏那颗头像重新登录一次。",
            )
            return@LaunchedEffect
        }
        commentStates[currentId] = PixivCommentsState.Loading
        pixiv.illustComments(bearer, currentId)
            .onSuccess { page ->
                commentStates[currentId] = PixivCommentsState.Loaded(
                    comments = page.comments,
                    totalCount = page.totalCount,
                    exhausted = page.nextUrl == null,
                )
            }
            .onFailure { failure ->
                commentStates[currentId] = PixivCommentsState.Failed(
                    failure.message?.takeIf { it.isNotBlank() } ?: "评论这一栏没取上",
                )
            }
    }
    LaunchedEffect(currentId, detail?.tags) {
        val head = detail ?: return@LaunchedEffect
        val word = head.tags.firstOrNull() ?: return@LaunchedEffect
        if (pixiv == null || related.containsKey(currentId)) return@LaunchedEffect
        pixiv.tagWorks(word, page = 1).onSuccess { rows ->
            related[currentId] = rows.map { it.post }
                .filter { it.id != currentId && !it.isVideo }
                .take(RELATED_MAX)
        }
    }
    LaunchedEffect(currentId) { tagsExpanded = false }

    // ⚠️ 这条滚动状态**不许由本屏持有**（2026-10-19 读数改口径）。
    // 从前这里是 `rememberLazyListState()` 一颗、再传进 `HorizontalPager` 的**每一个槽**，
    // 于是同一颗 `LazyListState` 同时挂在两三面 `LazyColumn` 上：邻位那一面此刻只有一两个条目
    // （详情还没到），它每次布局都会把共享游标**夹回自己够得着的那一档**，正在看的那一面跟着被顶走 ——
    // 用户读到的就是"点进来这一页被顶得很上"（真机两帧：顶边对齐、底边 1132 vs 1439）。
    // 现在每槽自持（`PixivWorkFlow` 里面 `rememberLazyListState()`），页位由那一槽自己报上来。
    var pageIndex by remember(currentId) { mutableIntStateOf(0) }
    /**
     * 顶栏模糊的滚动进度（0 = 停在顶部、完全不出；1 = 已滑过 48dp、全显）。
     *
     * 由 [PixivWorkFlow] 那面墙自己算好报上来（它才拿得到那槽的 `LazyListState`），
     * 这里只存一份给背板层用。换作品时归零：新的一件从顶部起手。
     */
    var scrollBlur by remember(currentId) { mutableFloatStateOf(0f) }
    /**
     * 背板这一层要不要挂在组合里。
     *
     * 只在**跨过 0** 那一刻翻面：`scrollBlur > 0f` 直接写进 `if` 会每帧变一次组合结构，
     * 滚动全程都在重组整页；而透明度本身由 `graphicsLayer` 绘制期读，不需要重组。
     */
    val needsBackdrop by remember { derivedStateOf { scrollBlur > 0f } }

    // ── 离场飞行体（批 231-E）──
    // 三份状态 + 一个动画值，与 `GalleryPostScreen` 同名同义：那一屏是样板，
    // 这里不另造名字，免得两处对不上时连"说的是不是一个东西"都要先猜。
    var leaving by remember(postId) { mutableStateOf(false) }
    var exitFrame by remember(postId) { mutableStateOf<ImageBitmap?>(null) }
    var exitFrom by remember(postId) { mutableStateOf<Rect?>(null) }
    var exitTo by remember(postId) { mutableStateOf<Rect?>(null) }
    val exitFly = remember(postId) { Animatable(0f) }

    // ── 进场飞行体（批 231-E 的另一半：墙上那一格 → 这一屏第 0 页那一格）──
    // 三份输入与去程同一套，且**只从交接件读**：起点那一帧（`GalleryFlyIn.payload`）、
    // 起点矩形（`origin`，唯一写入口是墙上点击那一刻的 `capture`）、落点（这一作第 0 页那一格，
    // 由 [PixivPageImage] 按**逐页键**回写）。
    //
    // ⚠️ 落点键用的是逐页键，而**返回程**查的是作品键（见 [exitTargetOrNull] 那张表）：
    // 去程落在"这一作的第 1 页画在屏上的那一块"，回程落在"墙上那张卡"，两头本来就不是一个格子。
    val cardFrame = GalleryFlyIn.payload
    val flyOrigin = GalleryFlyIn.origin
    val entryFly = remember(postId) { Animatable(0f) }
    /**
     * 交棒档：飞完才开始，与 `GalleryPostScreen` 同一颗算式、同一档时长（`ENTRY_HANDOFF_MS`）。
     *
     * 缺了这一档的时候，飞行体是画在一张**已经全不透明**的页面上 —— 同一张画两份同时可见
     * （2026-10-19 真机帧证：卡片截图 496×634 停在左上角，而详情大图 1080×1058 已经全亮）。
     * 样板那一屏早就是三档（等落点 / 飞 / 交棒），这一屏漏抄了中间与末尾两档。
     *
     * 批 B④⑤（2026-10-19）之后它的职责**收窄成一格**：只驱动**页面**的交棒（[pageAlpha]），
     * 不再兼飞行体的淡出。活画面那一族压根没有 alpha（同一份画从卡片形变到落点形，
     * 没有"两份"可淡）；只有截图族那一颗还要它（深链那一族没有 URL 可用）。
     * 于是"一标量两层"在这里的形状是：**几何一颗**（[entryFly]，两族共用）、
     * **交棒一颗**（这一颗，只服务页面）、**内容区一颗**（[entryDetail]，起止都不同故不并）。
     */
    val entryHandoff = remember(postId) { Animatable(0f) }
    /**
     * 等落点这一档：页面全透明、起点那一帧**冻在卡片原位**垫着。
     *
     * 新窗口第一帧必须与点击前那一帧看起来一样，否则就是"点一下先黑一块"——
     * 2026-10-19 真机读数：点击后一帧之内整屏亮度 128 → 35，撑了 18 帧（约 150ms）才回到 127。
     * 本窗口是 `Theme.Venera.GlassOverlay`（透的），所以不画底时透出来的是后面那面墙；
     * 而垫帧与卡上那张封面同位同像素，两样合起来读不出切换。
     */
    var entryWaiting by remember(postId) { mutableStateOf(flyOrigin != null) }
    /**
     * 这一次等待已经超时放弃。
     *
     * 与 [entryWaiting] 分开记的理由：那一格翻 false 之后，"放弃"与"起飞"看起来是一样的。
     * 落点在预算边缘回来时（真机上是 590~620ms 那一档），只看 `entryWaiting` 就会让页面先露出来、
     * 再被起飞抽走 —— 又回到用户报的那一下闪。
     */
    var entryGaveUp by remember(postId) { mutableStateOf(false) }
    var entryTarget by remember(postId) { mutableStateOf<Rect?>(null) }
    var entryFlying by remember(postId) { mutableStateOf(false) }
    /**
     * 详情内容区（图以下那一块）自己的钟。
     *
     * 初值取 **1**（= 内容正常显示），只有**真的起飞**那一刻才归零（见下面那条 effect）。
     * 反过来（初值 0、等起飞再抬起来）的话，五条"不飞了"的退路（无起点 / 无键 / 量不到落点 /
     * 等待超时 / 起飞前已放弃）每一条都要记得把它抬回 1 —— 漏一条就是"详情页永远空白"。
     * 取 1 当默认，退路天然是对的。
     */
    val entryDetail = remember(postId) { Animatable(1f) }
    /**
     * 批 B⑦：无锚那一趟的**侧滑**钟（0 = 屏外，1 = 归位）。
     *
     * 与 hero 那两颗（[entryFly] / [entryHandoff]）**互斥**：有锚才飞、无锚才侧滑，
     * 分岔判据只有 [hasAnchor] 那一枚（可见比两档 ≥25%）。三条"不飞了"的退路
     * （没有起点 / 没有逐页键 / 量不到落点）从前是"页面直接显示"，现在起动这一颗 ——
     * 计划 §5 ⑦ 的原话：不再走"整页从下沿抬上来"。
     *
     * 初值 1（= 已归位）：五条退路之外的所有路径（压根没有起点那一族、页面正常显示）
     * 都不该被侧滑拖住，取 1 当默认就不必每条都记得抬回来。
     */
    val sideSlide = remember(postId) { Animatable(1f) }
    /** 这一趟是不是在走侧滑（UI 层按它平移；hero 那两颗不为空时不许为真）。 */
    var sideSliding by remember(postId) { mutableStateOf(false) }
    var pageZeroDrawn by remember(currentId) { mutableStateOf(false) }
    // 逐页键仍由 `pageWallOf` 长出（屏上不自己拼 "…:p0"，那是第二处真相）。
    // 批 B⑥（不等落点）：详情没到时用"把我送进来的那条作品"长出一枚**种子**走同一颗算式，
    // 于是 `uid:p0` 首帧就有（种子与真详情第 0 页的键逐字相等，见 `PixivWorkPage.seedDetailOf`）。
    // 认不出邻居（深链、列表换过一轮）⇒ works 里没这条 ⇒ 交空串 ⇒ 照旧不飞、老实显示。
    val entryKey = remember(detail, currentId, works) {
        val source = detail ?: works.firstOrNull { it.id == currentId }?.let { PixivWorkPage.seedDetailOf(it) }
        pageWallOf(source).firstOrNull()?.viewerKey.orEmpty()
    }
    /**
     * 批 B⑦：起动**侧滑退路**（无锚那一趟）。
     *
     * 三条"不飞了"的路径共用它（没有逐页键 / 量不到落点 / 落点在预算边缘回来而超时已放弃）——
     * 口径只此一份，三条各写一遍就是三处真相。计划 §5 ⑦ 的原话：
     * 「可见比不足 25% ⇒ 当场判无锚、走全宽侧滑，**不再**走整页从下沿抬上来」。
     *
     * 与 hero 互斥：这一颗只在**没有起飞**的路径上被调（起飞那一段直接走 [entryFly]），
     * 两条路都上屏就是"既飞又滑"。
     */
    fun startSideSlide() {
        if (entryFlying) return
        sideSliding = true
        // 复用进场那一颗 coroutine scope：侧滑与 hero 不会同时跑（上面那道闸门），
        // 另起一颗只会多一处"取消时忘了管"的隐患。
        // `snapTo` 是 suspend，与动画同一段里跑（先归位再走，不会出现"跳一半"）。
        scope.launch {
            entryDetail.snapTo(1f)
            // 与进场同一档弹簧：侧滑也是"进场"，手感不该因为是退路就轻一档。
            sideSlide.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
            sideSliding = false
        }
    }

    /**
     * 这一屏此刻页面该有多不透明。
     *
     * 三段与样板同源：等待期 0（屏上只有垫帧）、飞行期 0（飞行体就是不透明的起点像素，
     * 页面一露头就是两份图）、交棒期 0→1。离场那半乘上去，进与出不重叠但也不必假设对方状态。
     */
    fun pageAlpha(): Float {
        val entering = when {
            entryWaiting -> 0f
            entryFlying -> handoffPageAlpha(entryHandoff.value)
            else -> 1f
        }
        return entering * if (leaving) exitPageAlpha(exitFly.value) else 1f
    }
    /**
     * 图以下那一块（详情段 / 末页之后那几格）此刻该有多不透明。
     *
     * 与 [pageAlpha] 是**两条钟**，起止都不同 —— 这正是 2026-10-19 用户报的那一条的修法：
     * 那一块原来跟着交棒走，而交棒从**落位**才开始，于是"图飞完了，下面的界面才在几帧里整块长出来"。
     *
     * 现在这一条从**飞行后段**起手、走到**落位之后**才收（[ENTRY_DETAIL_LEAD_FLY] / [ENTRY_DETAIL_MS]），
     * 与飞行重叠 ⇒ 读起来是"页面跟着图一起成形"，而不是"先飞完，再加载"。
     *
     * ⚠️ 图那一块**不能**跟着这条走：它在落位之前必须恒 0（防"同一张画两份"，见 [handoffPageAlpha]）。
     * 拆开的前提是图与内容分成两层画（见下面 pager 那两层 alpha 的接线）。
     */
    fun detailEntryAlpha(): Float {
        val entering = if (entryWaiting) 0f else handoffDetailAlpha(entryDetail.value)
        return entering * if (leaving) exitPageAlpha(exitFly.value) else 1f
    }
    LaunchedEffect(postId, entryKey, cardFrame, flyOrigin) {
        val frame = cardFrame
        val start = flyOrigin
        // ── 临时取证探针（`FlyProbe` 同批，验收完一起撤）：三份输入各是什么，缺哪一样 ──
        Log.i(
            "FlyProbe",
            "pixiv 进场输入 id=$postId key=$entryKey payload=${frame != null} origin=$start detail=${detail != null}",
        )
        // 压根没有起点（深链直开 / 从搜索页换进来时那笔已被读过）：不飞，页面照常显示。
        if (start == null) {
            entryWaiting = false
            return@LaunchedEffect
        }        // 这一次的等待已经被下面那条兜底放弃了：不再起飞 —— 否则页面先露出来、再被抽走，
        // 比一开始就不飞难看（这一条同时挡住"像素迟到"那一档）。
        if (entryGaveUp) return@LaunchedEffect
        // 像素还在路上（PixelCopy 是异步的）：继续等，页面保持透明、垫帧继续站着。
        if (frame == null) return@LaunchedEffect
        // 批 B⑥（不等落点）：**详情不再是起飞前置**。首帧那一格由"送我进来的那条作品"
        // 长出的种子摆出来（`PixivWorkPage.seedDetailOf` 走 `pageWallOf` 同一颗算式 ⇒ 键逐字
        // 相等），量落点从此只等布局与像素，不再等网络。真机旧病：慢首开详情 700ms > 预算
        // 600ms ⇒ 排行榜那一格"点卡进去压根不飞"（§9.3 读数，`起飞` 那行压根没打）。
        // 连种子都没有（深链直开 / 认不出邻居）⇒ 这一屏不飞，页面照常显示
        // （口径同上面那条"压根没有起点"；`entryKey` 在同样处境下也是空串，两处不是两把尺）。
        // 批 B⑦：不飞 ≠ 直接显示 —— 起动侧滑（无锚退路，见 [sideSlide]）。
        if (entryKey.isBlank()) {
            Log.w("FlyProbe", "pixiv 进场没有逐页键 key=空 ⇒ 走侧滑退路")
            entryWaiting = false
            if (GalleryFlyIn.payload === frame) GalleryFlyIn.consume()
            startSideSlide()
            return@LaunchedEffect
        }
        val target = withTimeoutOrNull(ENTRY_WAIT_MS) {
            snapshotFlow { GalleryFlyIn.cardBoundsOf(entryKey) }
                .first { it != null && it.width > 0f && it.height > 0f }
        }
        if (target == null) {
            // 量不到落点：不演"飞完了"（同 [back] 那一步的口径），走侧滑退路。
            Log.w("FlyProbe", "pixiv 进场落点没等到 key=$entryKey 预算=${ENTRY_WAIT_MS}ms ⇒ 走侧滑退路")
            entryWaiting = false
            if (GalleryFlyIn.payload === frame) GalleryFlyIn.consume()
            startSideSlide()
            return@LaunchedEffect
        }
        Log.i("FlyProbe", "pixiv 进场起飞 target=$target origin=$start")
        // 落点是在预算边缘回来的：这几百毫秒里超时那一条可能已经放弃了，页面也已经露出来了。
        // 这时再起飞就是把页面抽走，所以宁可不飞（判据同上面那一条）⇒ 也走侧滑退路。
        if (entryGaveUp) {
            entryWaiting = false
            if (GalleryFlyIn.payload === frame) GalleryFlyIn.consume()
            startSideSlide()
            return@LaunchedEffect
        }
        entryWaiting = false
        entryTarget = target
        entryFlying = true
        entryHandoff.snapTo(0f)
        entryFly.snapTo(0f)
        entryDetail.snapTo(0f)
        // 内容区那条钟：与飞行**并行**跑，不是等它跑完再起（等完就是用户报的那一条）。
        //
        // 起手点挂在**飞行进度**上而不是写死毫秒：弹簧的实际时长随机型与动画缩放而变，
        // 写死 170ms 在另一档上就会"内容先成形、图还在半空"。
        // 挂的也不是路程：`fly=0.85` 时飞行体还在路程的 85%（`handoffPageAlpha` 头注那条老账），
        // 取 [HANDOFF_DETAIL_LEAD_FLY] 是因为这一刻它已经压在落点那一格附近，内容区在下面起来不会读成抢跑。
        // `withTimeoutOrNull` 兜一层：万一弹簧没走到那一格（被打断 / 阈值提前判settled），
        // 内容区也必须起来 —— 否则详情页永远空白，这是这一屏最不能出的错。
        launch {
            withTimeoutOrNull(ENTRY_DETAIL_WAIT_MS) {
                snapshotFlow { entryFly.value }.first { it >= HANDOFF_DETAIL_LEAD_FLY }
            }
            entryDetail.animateTo(1f, tween(HANDOFF_DETAIL_MS.toInt(), easing = LinearEasing))
        }
        entryFly.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
        // 落点那一格**真画出像素**才交棒：落在一块骨架上就是又一次"闪"（量过的那一族）。
        val handed = withTimeoutOrNull(ENTRY_HANDOFF_MS) { snapshotFlow { pageZeroDrawn }.first { it } } != null
        Log.i("FlyProbe", "pixiv 进场落点真画出像素=$handed")
        // 飞完才交棒：此刻飞行体正好压在落点那一格上，是"两份图重合"的唯一时刻。
        entryHandoff.animateTo(1f, tween(durationMillis = ENTRY_HANDOFF_MS.toInt()))
        // ⚠️ 只清**自己那一笔**：等落点的这几百毫秒里用户可能已经点进了某一页，
        // 那一跳会重新 capture 一份起点（见 [openPages]）—— 那时这一屏不许把别人的账清了，
        // 否则播放器到手是空的，表现成"从作品页进大图不飞"。判据 = 手里那帧还是不是当初那一帧。
        if (GalleryFlyIn.payload === frame) GalleryFlyIn.consume()
        entryFlying = false
    }
    /**
     * 等待有上限：接口一直不回来时，页面会永远停在"透出后面那屏"的样子 ——
     * 用户既看不到失败横幅，也看不出这一屏到底有没有在做事。
     * 预算与等落点同一档（[ENTRY_WAIT_MS]），到点老实把页面显出来，退化成改动之前那一档。
     */
    LaunchedEffect(postId, entryWaiting) {
        if (!entryWaiting) return@LaunchedEffect
        delay(ENTRY_WAIT_MS)
        entryGaveUp = true
        entryWaiting = false
    }

    /**
     * **整屏**在窗口里的矩形 —— 离场那一帧的起点。
     *
     * 与 `GalleryPostScreen` 的 `box` 是同一件事：那一屏起点是"画面框"（图在屏中央那一块），
     * 而这一屏整屏就是一面竖连的图墙，没有"中央画面框"这一说 ⇒ 起点取整屏。
     *
     * 现量而不是写死：状态栏 / 导航条两档 inset 随机器而变，写死数就会在某一档上
     * 截到一块比屏大的矩形（PixelCopy 直接失败，表现是"返回不飞"）。
     */
    var fullFrameBox by remember(postId) { mutableStateOf(Rect.Zero) }
    // 底部那一叠 chrome（出错时的那条横幅）的高度**现量**，不写死：
    // 写死一个数就会在报错那一档把最后半页压在 chrome 底下。
    val density = LocalDensity.current
    val startClearance = tokens.spacing.none
    var chromeHeight by remember(postId) { mutableStateOf(startClearance) }
    // 顶栏那一格有多高：状态栏 + 顶栏塌下来那一档。**内容不让位**（图照旧穿到状态栏之上，
    // 用户 2026-10-18 第四批改的口径），这一格只用来摆那颗全站同档的模糊背板。
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBandHeight = statusBarTop + tokens.spacing.topBarCollapsedHeight
    // 屏底摘要条撤掉之后，让位要自己补上导航条那一格（从前是摘要条的高度顺带盖住的）。
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // ④ 顶栏玻璃座的采样源。这一屏没有氛围底（整页透出后面那屏），被采样的那一层就是下面那堵图墙。
    val topBarBackdrop = rememberTopBarBackdrop()

    /**
     * **离场起点**：屏上**当前那一页图块**的矩形（2026-10-19 · 批 B②）。
     *
     * ## 为什么从整屏换成那一页
     *
     * 从前起点是 `fullFrameBox`（整屏截一块再飞）—— 多 P 作翻到第 3 页按返回，飞行体带着
     * 整摞页（三页图 + 动作条 + 标签）从整屏那么大一框缩回一张**小卡**上；而落点是一张
     * 作品卡，两者不是同一张画。末段必须淡掉才读得下去（`exitBodyAlpha`），
     * 落地那一帧是"一张小号页面压在封面之上"（§9.3b 记的那一族）。换成当前页之后，
     * 飞行体带的就是**用户此刻正在看的那一页**，落回卡上时是同源构图 —— 这才是 pixez 那一族
     * "同一个视图变形成卡"的形状（计划 §5 批 B② 的原话：不许 `fullFrameBox`）。
     *
     * ## 键与 [exitTargetOrNull] 是两把，故意如此
     *
     * - 起点查**逐页键** `uid:pN`（这一屏自己的每一页按 `PixivWorkFlow` 报的 `pageIndex`）；
     * - 终点查**作品键** `uid`（墙上那张卡登记的那把）。
     *
     * 两把都从 `GalleryFlyIn.cardBoundsOf` 这一张表问 —— 表里两类键都在（页自己登记 `uid:pN`，
     * 墙登记 `uid`），屏上不另拼任何一把。
     *
     * ## 取不到就交 null（不演）
     *
     * 那一页还没量出矩形（刚滑过来还没布局完）/ 没登记（打码命中那一族压根不登记）⇒ null ⇒
     * 调用方整页抬回去。宁可不飞，也不飞一块不属于用户正在看的那页的图。
     *
     * ## 身份从 [exitTargetOrNull] 那一颗接过来（不另起一条链）
     *
     * 批 B② 一开始在这里重写了一遍"队列 → 详情 post"，被接线尺当场红（`离场取身份只许这一句`）
     * —— 两句话逐字相同，可那就是**第二处真相**：明天改身份链的人只改一处，这一屏的起点就
     * 悄悄退化回旧行为。所以现在吃参数：身份仍只那一句，本颗只管"换成逐页键去查"。
     */
    fun exitStartOrNull(post: GalleryPost): Rect? {
        // 逐页键与页轴同一颗算式（`pageWallOf` 的 `viewerKey` = `uid:pN`），屏上不自己拼。
        val pagePost = post.copy(pageOrdinal = pageIndex)
        return GalleryFlyIn.cardBoundsOf(pagePost.viewerKey)
    }

    /**
     * **返回程的落点**：墙上那一格在窗口里的矩形（2026-10-19 · 批 231-E）。
     *
     * ## 两端不是同一把键 —— 这是这一批最容易静默写错的一处
     *
     * | 谁 | 回写用的键 | 例 |
     * |---|---|---|
     * | 墙上那两排卡（`PixivWorkCard`） | **作品键** `post.viewerKey` | `pixiv:148343100` |
     * | 这一屏自己的每一页 | **逐页键** `post.viewerKey`（页轴生成的） | `pixiv:148343100:p0` |
     *
     * 所以返回时查的是那件作品的**作品键**（不带页位）—— 墙上摆的是一整张作品，
     * 没有一个"第几页"的格子可落。用逐页键去查会永远查不到，表现是"返回时整页抬回去"：
     * 不报错、不崩溃，谁也不会来报（`GalleryPostScreen` 头注里记着同一条静默错）。
     *
     * ## 邻居队列不是这件作品的身份（2026-10-19 批 A 真机验收补）
     *
     * `works` 是**把我送进来的那面邻居墙**，它只服务横轴翻邻居，可以正当为空：
     * 历史那一格的 `tapTile` 只写飞行交接件、不写队列（历史列表混着站点与多页，
     * 左右翻出去的顺序根本不是用户刚才看到的顺序）。旧版在这里"队列认不出 ⇒ 交回 null"，
     * 真机读数（17:25:38，`pixiv:150828286`）是**落点就在屏上登记着，返回照样不飞** ——
     * 队列认不出只等于"没有邻居"，不等于"这件作品没有落点"。
     * 所以身份链退回一级：邻居队列 → **本件作品的详情 post**（它的 `viewerKey` 由
     * `site.routeKey:id` 长出，与墙登记那侧逐字同串，不是第二把键）。
     *
     * 交回 null = 这一趟飞不了（墙那一格已被 Lazy 回收 / 详情还没回来）。
     *
     * ## 为什么这一步只到"查得到"为止
     *
     * `GalleryFlyIn.origin` 的 setter 是 **private**，唯一写入口是 `capture(view, bounds)` ——
     * 而它必然要**截一块像素**（PixelCopy）。返回时屏上是我们自己的画面、起点不是一张卡，
     * 没有可截的那一块。硬塞落点要么改交接件的封装（那一颗是跨三屏共用的），
     * 要么伪造一次 capture —— 两条都是"为了这一屏好看去动别人的地基"，不做。
     *
     * 所以这一半（**查哪儿**）先落地并被判据钉住；"怎么画"等离场动画那一套接进来时再补，
     * 那时这一段不用改。判据：`作品页离场要按同一把键取落点`。
     *
     * 批 B② 之后身份链升到 `back` 那一颗（起点终点都要用），这里吃参数 ——
     * 两句话逐字相同就是第二处真相（接线尺 `离场取身份只许这一句` 钉着）。
     */
    fun exitTargetOrNull(post: GalleryPost): Rect? = GalleryFlyIn.cardBoundsOf(post.viewerKey)
    /**
     * **备料**：查起点 / 落点、起一次 `PixelCopy`（批 C1 从 [back] 里抽出来的那一半）。
     *
     * 交回 false = 这一趟压根备不了料（没有落点 / 那一页没登记）—— 调用方老实 finish，
     * 不演"飞完了"。交回 true 时**像素还在路上**：`exitFrame` 要等 `captureRegion` 的回调。
     *
     * 跟手那条与三键那条共用它：跟手期手指已经拖出去时，像素一到就能补到那一档
     * （决策层 `Arming` 期间把进度攒着，见 `GalleryExitGesture`）。
     */
    fun prepareExit(onPixels: (ok: Boolean) -> Unit): Boolean {
        val post = works.getOrNull(workIndex) ?: detail?.post
        val target = post?.let { exitTargetOrNull(it) }
        // 起点那一页：身份接 `post`（不另起一条链，见 `exitStartOrNull` 头注）。
        val start = post?.let { exitStartOrNull(it) }
        // ── 临时取证探针（`FlyProbe` 同批）：回程查到的落点是什么、起点那一页多大一块 ──
        Log.i("FlyProbe", "pixiv 返回查落点 target=$target start=$start fullFrame=$fullFrameBox")
        if (target == null || start == null) {
            Log.d(TAG, "返回程没有落点或没有起点那一页（墙那一格已回收 / 该页没登记 / 不是从墙上点进来的）⇒ 整页退")
            return false
        }
        // 起点改成**当前那一页图块**（批 B②）：截的就是那一页所在的矩形，飞行体带的
        // 是用户此刻正在看的那一页，不是整摞页 + 动作条 + 标签那一大框。
        GalleryFlyIn.captureRegion(view, start) { frame, capturedRect ->
            if (frame == null || capturedRect == null) {
                // 截不到帧：直接退。把"没能飞"演成"飞完了"是本仓最忌的那种假开关。
                Log.w("FlyProbe", "pixiv 离场没截到帧 start=$start ⇒ 整页退")
                onPixels(false)
            } else {
                Log.i("FlyProbe", "pixiv 离场截到帧 from=$capturedRect to=$target")
                exitFrame = frame
                // 起点用**钳到窗口后**的那一块：飞行体的像素与矩形必须同一块（批 B §3 地基）。
                exitFrom = capturedRect
                exitTo = target
                onPixels(true)
            }
        }
        return true
    }

    /**
     * **自己驱动**的固定弹簧离场（三键导航 / `adb` 送按键这类**没有进度事件**的返回用它）。
     *
     * 与跟手那条共用同一颗算式（`GalleryExitExpansion`）与同一份落点账本 ——
     * 两处各飞一遍就会"点顶栏与按手势落点不一样"，而那种错只有真机能看见。
     */
    fun flySelfDrivenExit() {
        leaving = true
        if (!prepareExit { ok ->
                if (!ok) {
                    leaving = false
                    onBack()
                } else {
                    scope.launch {
                        // 与下滑关闭、进场同一档弹簧（数值同 `GalleryPostScreen`，不另造一个）。
                        exitFly.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))
                        Log.i("FlyProbe", "pixiv 离场飞完 value=${exitFly.value}")
                        onBack()
                    }
                }
            }
        ) {
            // 备不了料（没有落点 / 没有起点那一页）：一步都还没动，直接退。
            leaving = false
            onBack()
        }
    }

    /**
     * 顶栏那颗返回。
     *
     * **飞得起来就先飞，飞不起来直接退**（2026-10-19 · 批 231-E 补齐"怎么画"）。
     *
     * 三步与 `GalleryPostScreen` 逐字同一条链（那一屏是这一套的样板）：
     * 1. `exitTargetOrNull()` 拿**墙上那一格**的落点（查不到 ⇒ 老实 finish，不演"飞完了"）；
     * 2. `GalleryFlyIn.captureRegion` 截**本屏此刻**这一帧（截不到 ⇒ 同样老实 finish）；
     * 3. 弹簧把飞行体推到 1 再 finish —— 进与出手感对称（同一档 `StiffnessMediumLow`）。
     *
     * 飞行体本身是**共享件** `GalleryExitFlyBody`（渲染）+ `GalleryExitExpansion`（整帧算式，
     * 有单测逐位钉着）—— 不在这里另写一遍绘制：两处各画一遍，同一部作品从两面墙点进去
     * 就会飞得不一样，而那种错只有真机能看见。
     *
     * 批 C1 起它**只管三键 / 顶栏那一档**（没有手指可跟）；手势那一条走 [onExitGestureEvent]，
     * 两条共用 [prepareExit] 与同一颗算式。
     */
    val back: () -> Unit = { flySelfDrivenExit() }

    /**
     * 系统返回（三键 / 手势）与顶栏那颗走**同一条链**，但**手势那一条是跟手的**（批 C1）。
     *
     * 这一屏原先没有接 `OnBackPressed`：顶栏那颗有飞行，而系统返回直接把 Activity finish 掉 ——
     * 2026-10-19 真机读数，返回后第 7 帧（约 0.1s）屏上已经是画廊，中间一帧飞行体都没有。
     * 用户读到的"返回没动画"就是这一条，不是落点算错（同一趟点顶栏返回，探针里
     * `返回查落点 target=Rect(32,1385,528,2046)` 是拿得到的）。
     *
     * ## 为什么从 `BackHandler` 换成 `PredictiveBackHandler`（批 C1，2026-10-20）
     *
     * 原先那句注释写的是「这一屏的离场是"整屏一帧缩回卡片"，没有跟手的中间态可言」——
     * **那是批 B②③ 之前的老判断，已经过期**：起点换成**当前那一页图块**、圆角换成内容缩放比
     * 之后，离场是一次真正的形变（页块 → 卡封面），跟手有中间态可画了。
     *
     * 决策层仍是现成那颗 [GalleryExitGesture]（四道门闩：杂散事件 / 像素未到 / 截不到装飞 / 确认后回滚），
     * 视觉算式仍是 [GalleryExitExpansion] —— 这一屏**不另写一套判断**，只把系统事件喂进去、
     * 按交回的指令顺序执行（与 `GalleryPostScreen` 那条逐字同形）。
     *
     * 代价如实记账：页面自己接管之后，系统那套**整窗口**预览没有了（它给的是整扇窗，不是元素级）。
     */
    var exitPhase by remember(postId) { mutableStateOf(GalleryExitGesturePhase.Idle) }
    /** 最近一次收到的手势进度：`Arming` 期间攒着，像素一到就补到手指已经拖到的那一档。 */
    var exitLastProgress by remember(postId) { mutableFloatStateOf(0f) }
    /**
     * 跟手期的高刚度弹簧。
     *
     * 不用 `snapTo`：澎湃 / HyperOS 这类机器在拖动期间只派发少量进度点，直接落位就是整页跳位。
     * 3000f 无回弹那一档是"几乎贴手指、稀疏时平滑追赶"，与仓内既有的跟手口径同源
     * （`components/PredictiveBack.kt` 的回弹用 600f，那是回弹不是跟手）。
     */
    val exitFollowSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 3000f,
            visibilityThreshold = 0.0005f,
        )
    }

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
                    GalleryExitGestureCommand.Prepare -> {
                        leaving = true
                        if (!prepareExit { ok ->
                                if (ok) {
                                    onExitGestureEvent(GalleryExitGestureEvent.PixelsReady)
                                } else {
                                    onExitGestureEvent(GalleryExitGestureEvent.PixelsFailed)
                                }
                            }
                        ) {
                            // 备不了料（没有落点 / 没有起点那一页）：交回失败，让决策层直接 finish
                            // —— 半路没有帧可画，别自己编一帧（"宁可错慢不可静默交错"）。
                            onExitGestureEvent(GalleryExitGestureEvent.PixelsFailed)
                        }
                    }

                    // 取消：画面**回原位**、把离场那三样输入清掉。
                    //
                    // 次序（与样板 `restoreChromeAfterCancel` 逐字同形）：
                    // 1. 决策层交回的 [SetVisualTarget(0f)] 先跑完 —— 那一刻画面已经站回原位，
                    //    所以看不见"背景先清晰、页面还在缩"的两步；
                    // 2. 三样必须清：渲染条件只看"三样齐不齐"（②③那轮删掉了 `leaving`），
                    //    不清就是**旧那一趟的截屏压在新那一趟上面**；
                    GalleryExitGestureCommand.Restore -> {
                        leaving = false
                        exitFrame = null
                        exitFrom = null
                        exitTo = null
                    }

                    GalleryExitGestureCommand.FlySelfDriven -> flySelfDrivenExit()
                    GalleryExitGestureCommand.Finish -> onBack()
                }
            }
        }
    }

    // 系统返回 / 侧滑手势：**跟手**。进度直接驱动离场那一帧，松手确认就落回卡片、回弹就原路退回。
    // 三键点返回与 `adb` 送按键**没有**进度事件，照旧走 `back` 那趟自驱弹簧 ——
    // 两条共用同一颗算式与同一份落点账本（见 [back] 头注）。
    //
    // ⚠️ `enabled` **不挂 `leaving`**：`Prepare` 那一笔就会把 `leaving` 翻真，挂上去等于
    // **手势一开始就把自己这条事件流关掉**（进度全丢、画面钉在原地）。样板 `GalleryPostScreen`
    // 挂的是弹层开关（与离场状态无关）—— 这里跟它同形，恒开。
    // 防重入交给上面那个决策层（`Idle` 才激活、`Committed` 之后一律丢）。
    PredictiveBackHandler { events ->
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
            // 平台在取消手势时撤掉这一笔协程：画面回原位，然后把取消交回去。
            // 没有进度事件就没有动过画面（`Arming` 都没进），什么都不必做。
            if (receivedProgress) onExitGestureEvent(GalleryExitGestureEvent.Cancel)
            throw cancelled
        }
        onExitGestureEvent(
            if (receivedProgress) GalleryExitGestureEvent.Commit
            else GalleryExitGestureEvent.BackWithoutProgress,
        )
    }

    // 根容器：**自己量自己**整屏矩形（离场起点用它，见 `fullFrameBox`）。
    // 挂在最外层那个 Box 上而不是内层图墙：图墙是横轴 pager，它的矩形不等于整屏。
    // 底色在**组合期**取出来：`tokens.color` 是 @Composable getter，绘制阶段读不到。
    val pageBackground = tokens.color.background
    Box(
        Modifier
            .fillMaxSize()
            // 批 B⑦：无锚那一趟走**全宽侧滑**（顶层平移）。走 hero 时这一层不许动
            // （`sideSliding` 只在三条退路上翻真），两条路都上屏就是"既飞又滑"。
            // 用 `graphicsLayer` 而不是 padding：平移是纯绘制层的，几何零变化 ——
            // 起点矩形是点击那一刻量的，挪了就对不上屏上那一块（`ChromeCaptureSuppress` 同族）。
            .graphicsLayer {
                if (sideSliding) {
                    translationX = size.width * sideSlideTopOffset(sideSlide.value)
                }
            }
            // 底**不是**一块恒定的不透明色：入场那两档它必须让开（见 [pageAlpha]）。
            // 用 `drawBehind` 而不是 `background`：后者在组合期取值，每帧都会把整页拖进重组。
            //
            // 底色取**两条钟里高的那一档**：内容区（图以下那一块）比图早起手，如果底色还挂在
            // 交棒那一档上，飞行中途就会出现"下面一整块深色细节浮在亮牆上"的一道硬边。
            // 跟着内容区走，那面墙就是整体慢慢暗下去的；此时飞行体画在最上面，不受影响。
            .drawBehind {
                drawRect(
                    color = pageBackground,
                    alpha = maxOf(pageAlpha(), detailEntryAlpha()),
                )
            }
            .onGloballyPositioned { coords ->
                // 整屏框只给 captureRegion 截像素用（PixelCopy 只读窗口内表面）：显式吃裁过的。
                val bounds = coords.boundsInWindow(clipBounds = true)
                if (bounds.width > 0f && bounds.height > 0f) fullFrameBox = bounds
            },
    ) {
        val openArtist: (GallerySite, String, String) -> Unit = { site, name, source -> onOpenArtist(site, name, source) }
        // ⚠️ 这一层**不再挂 alpha**：图与"图以下那一块"现在是**两条钟**（见 [pageAlpha] /
        // [detailEntryAlpha]）。挂在这一层上就只有一条钟，内容区必然被图卡在后面 ——
        // 那正是用户 2026-10-19 报的"飞入完成才突然加载出下面的界面"。
        // 两条 alpha 都下到 [PixivWorkFlow] 的**每一项**上，各自的钟各自走。
        Box(
            Modifier
                .fillMaxSize()
                .blurBackdropSource(topBarBackdrop),
        ) {
            // 页轴只剩这一档（用户 2026-10-18 第四批：「还是直接删了横着一页一页那个模式切换吧，
            // 只留竖着连续翻页模式」）。**内容铺满整屏**：图可以一直穿到状态栏之上（同一批里
            // 用户改的顶栏口径 —— 「上滑的时候图片还是能过状态栏，不过要有顶栏模糊效果」），
            // 顶栏那一行不把内容压下去，改由下面那颗全站同档的模糊背板保文字对比。
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { slot ->
                val slotId = works.getOrNull(slot)?.id ?: postId
                // 批 B⑥：这一槽的详情没到，就按**这一位在墙上的那条**长出种子，首帧摆得出
                // 第 0 页那一格（量落点从此不等网络）。详情到了种子自动退场（见 `pages` 那行）。
                // 认不出 ⇒ null ⇒ 与从前逐字一致（页面等详情，不飞）。
                val slotSeed = if (details[slotId] != null) null
                else works.firstOrNull { it.id == slotId }?.let { PixivWorkPage.seedDetailOf(it) }
                PixivWorkFlow(
                    detail = details[slotId],
                    seedDetail = slotSeed,
                    seam = seam,
                    detailsInFlow = detailsInFlow,
                    imageLoader = imageLoader,
                    related = related[slotId].orEmpty(),
                    tagsExpanded = tagsExpanded,
                    onExpandTags = { tagsExpanded = true },
                    onOpenArtist = openArtist,
                    onDownload = saveWork,
                    openPages = openPages,
                    onOpenWork = { id ->
                        GalleryViewerQueue.set(related[slotId].orEmpty())
                        onOpenWork(id)
                    },
                    onOpenViewer = onOpenViewer,
                    // 流末尾那枚"下一作品"提醒：只在墙里认得出下一位时才递进来（判据在调用方这一句）。
                    nextWork = works.getOrNull(slot + 1),
                    onOpenNextWork = { scope.launch { pagerState.animateScrollToPage(slot + 1) } },
                    bottomClearance = chromeHeight + navBarBottom + tokens.spacing.space6,
                    aiBadgeOn = aiBadgeOn,
                    // 评论那一节：这一屏自己持有状态（按作品号存），滚动流只是摆。
                    comments = commentStates[slotId] ?: PixivCommentsState.Loading,
                    commentsExpanded = commentsExpanded,
                    onToggleCommentsExpanded = { commentsExpanded = !commentsExpanded },
                    // 只有**当前这一位**的第 0 页算数：邻位是 pager 预组合的，它画没画过不影响进场落点。
                    onPageDrawn = { index -> if (index == 0 && slotId == currentId) pageZeroDrawn = true },
                    // 页位（顶栏那颗「2/4」）只认当前槽自己那面墙的读数：
                    // 邻槽滚到哪儿与屏上这一行的数字无关，混着报就会跳。
                    onPageIndexChanged = { index ->
                        if (slotId == currentId) pageIndex = index
                    },
                    // 顶栏模糊的滚动读数：同样只认当前槽 —— 邻槽被 pager 预组合、停在顶部，
                    // 它报上来的 0 会把正在滑的那一面顶回"顶部不模糊"。
                    onScrollProgress = { progress ->
                        if (slotId == currentId) scrollBlur = progress
                    },
                    // 点作品标签 → 交回画廊那面墙去搜这一枚（用户 2026-10-19 第 3 条）。
                    // 走 `GallerySearchHandoff` 而不是本地跳转：这一屏是独立 Activity，
                    // 搜索状态的宿主在 MainActivity 那一侧（交接件的由来写在它头上）。
                    //
                    // ⚠️ 2026-10-20 修「点标签没反应」：从前这里**只写槽、不离开本屏**，
                    // 而槽的消费方（`GalleryScreen` 里那条 `LaunchedEffect`）只有在画廊那一屏
                    // **组合活着**时才跑得起来。可这一屏压在它上面、它是 paused 的；
                    // 更糟的是从收藏页那面墙点进来时，画廊那一屏组合**压根没了**
                    // （见 `Navigation.kt` 那条"谁先到谁吃掉"的注释）。于是槽写进去，没人读 ——
                    // 用户点了没反应，直到他手动返回，槽才被读到：那时屏上会切进 pixiv 那一域、
                    // 卡在空词上（空词不发请求），看起来正是"首页冒出个空搜索框"。
                    // 所以这里与图库三站那颗 `GalleryPostScreen` 同一形状：先写上槽，再退掉本层。
                    onSearchTag = { tag ->
                        GallerySearchHandoff.postTag(GallerySite.PIXIV, tag)
                        // 与上面那颗旧看图器同一理由：用户要的是立刻看到结果，
                        // 多那一段离场飞行挡在前面读起来就是"点了没反应"。
                        back()
                    },
                    // 两条钟只对**当前这一作**有读数：邻槽是 pager 预组合的，
                    // 它跟着进场归零就会在旁边闪一下（用户横翻回来时读到的"隔壁那页空了一下"）。
                    pageEntryAlpha = { if (slotId == postId) pageAlpha() else 1f },
                    detailEntryAlpha = { if (slotId == postId) detailEntryAlpha() else 1f },
                )
            }
        }

        // 顶栏背板：全站主页面那一档（渐变高斯模糊从上往下收 + 轻补底保文字对比），
        // 玻璃色采的就是上面那堵被 `blurBackdropSource` 标记的图墙。
        // ⚠️ 它必须画在采样层**之外**：放进采样层里就是"把模糊再录一遍"，逐帧回灌。
        //
        // 2026-10-19 用户改的口径：「在顶部的时候不要有顶栏模糊，往上滑的时候再出现，
        // 这个过程要流畅过渡」—— 所以背板不再恒显，改由 [scrollBlur] 那一条跟手的读数驱动：
        // 顶部 = 完全不出，上滑 48dp 内线性推到全显，回弹再原路收回。
        //
        // ⚠️ 挂不挂这一层读的是 [needsBackdrop]（`derivedStateOf`，只在**跨过 0** 那一刻翻），
        // 不是 `scrollBlur > 0f` 直接读 —— 后者每帧都在变，等于滚动全程把整页拖进重组。
        // 真正的透明度交给 `graphicsLayer` 在绘制期取，滚动只失效图层、不重组。
        if (needsBackdrop) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(topBandHeight)
                    .graphicsLayer { alpha = pageAlpha() }
                    .align(Alignment.TopCenter),
            ) {
                VeneraTopBarBackdrop(
                    backdrop = topBarBackdrop,
                    modifier = Modifier.matchParentSize(),
                    progress = { scrollBlur },
                )
            }
        }

        // ── 顶部 chrome：④ 两枚按钮走全站同一颗玻璃座（批次 H 定的「顶栏图标按钮唯一形态」）──
        // 三件一起接才磨砂：采样源（上面那颗）、被采样的那一层（图墙那堵 Box）、
        // 以及把采样层递到按钮手里的那枚 CompositionLocal。少任何一件，那颗件会**可见地**
        // 回落成实心底 —— 而不是什么都不画（判据写在 `VeneraTopBarPill` 头上）。
        CompositionLocalProvider(LocalTopBarBackdrop provides topBarBackdrop) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = tokens.spacing.screenHorizontal)
                    .height(tokens.spacing.topBarCollapsedHeight)
                    // 截"进大图那一帧"之前让位：这三枚浮在画面之上，而 PixelCopy 读的是合成后的
                    // 表面 —— 不让位就会跟着被截进飞行体（2026-10-19 真机帧证：飞行体里带着
                    // 返回箭头、更多与「1/1」页码，交棒时变成一层鬼影）。
                    .reportChromeBand(PIXIV_WORK_TOP_CHROME_BAND)
                    .graphicsLayer {
                        alpha = if (ChromeCaptureSuppress.suppressed) 0f else pageAlpha()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                VeneraTopBarPill(onClick = back) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                        modifier = Modifier.size(tokens.spacing.badgeIconSize),
                    )
                }
                // 中间那枚「1 / N」页数指示器已撤（用户 2026-10-19：「去掉中间的页数指示器」）。
                // SpaceBetween 在只剩两枚时仍把返回钉在左、更多钉在右，中间那一格天然空出来，
                // 页位读数改由这一屏的滚动本身表达，不再在顶栏上重复念一遍。
                VeneraTopBarPill(onClick = { sheetOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "详情",
                        tint = tokens.color.textPrimary,
                        modifier = Modifier.size(tokens.spacing.badgeIconSize),
                    )
                }
            }
        }

        // ── 底部只剩失败横幅（用户 2026-10-18 第三批：「这个栏不应该有可以删掉了」+「横着一页一页翻
        // 应该和竖着连续翻页模式结构一样」）。屏底那条摘要条从前与详情段里那行画师信息是同一份读数
        // 画两遍，两支一起撤；「更多信息」的入口留在顶栏那一枚上。
        // **失败横幅留在屏上** —— 取数失败时弹层是空的，把错误也搬进去等于让用户对着白屏找"为什么没图"。
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .graphicsLayer { alpha = pageAlpha() }
                .onSizeChanged { chromeHeight = with(density) { it.height.toDp() } }
                .align(Alignment.BottomCenter),
        ) {
            val failure = failures[currentId]
            if (failure != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.screenHorizontal)
                        .clip(RoundedCornerShape(tokens.shape.card))
                        .background(tokens.color.errorContainer)
                        .clickable(onClick = { retryTick++ })
                        .padding(horizontal = tokens.spacing.space5, vertical = tokens.spacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                ) {
                    Text(failure, fontSize = tokens.type.caption, color = tokens.color.onErrorContainer, modifier = Modifier.weight(1f))
                    Text("再试一次", fontSize = tokens.type.caption, color = tokens.color.onErrorContainer)
                }
                Spacer(Modifier.height(tokens.spacing.space3))
            }
        }

        if (sheetOpen && detail != null) {
            ModalBottomSheet(
                onDismissRequest = { sheetOpen = false },
                sheetState = sheetState,
            ) {
                PixivWorkDetails(
                    detail = detail,
                    related = related[currentId].orEmpty(),
                    tagsExpanded = true,
                    onExpandTags = {},
                    onOpenArtist = openArtist,
                    artist = pixivArtistActions(detail.post.author),
                    actions = pixivWorkActions(detail, onDownload = { saveWork(detail) }),
                    avatarUrl = artistAvatar,
                    imageLoader = imageLoader,
                    onOpenWork = { id ->
                        GalleryViewerQueue.set(related[currentId].orEmpty())
                        sheetOpen = false
                        onOpenWork(id)
                    },
                    aiBadgeOn = aiBadgeOn,
                    // 抽屉里点标签与流里那一枚走**同一条**链（写交接槽 + 退本层）：
                    // 两处各写一份就会出现"流里点得动、抽屉里点了没反应"那种半接线。
                    onSearchTag = { tag ->
                        GallerySearchHandoff.postTag(GallerySite.PIXIV, tag)
                        // 先收抽屉再退：抽屉是这一屏的浮层，留着它就会盖在退场动画上。
                        sheetOpen = false
                        back()
                    },
                    // 「更多信息」那一层也摆评论（与滚动流里那一节同一份状态，
                    // 切到抽屉看到的是同一句话，不会两处对不上）。
                    comments = commentStates[currentId] ?: PixivCommentsState.Loading,
                    commentsExpanded = commentsExpanded,
                    onToggleCommentsExpanded = { commentsExpanded = !commentsExpanded },
                    modifier = Modifier.padding(bottom = tokens.spacing.space9),
                )
            }
        }

        // ── 进场飞行体（墙那一格 → 第 0 页那一格）：三样齐了才画 ──
        // 与离场那一层互为镜像，共用同一颗共享件（[GalleryEntryFlyBody] / [GalleryExitFlyBody]）：
        // 几何只住一份（判据层 `exitExpansion`），同一部作品从两面墙点进来飞得一样。
        // 等像素 / 等详情那一段由 progress 冻在 0 垫着（进度 0 = 站在卡片原位，与点击前
        // 那一帧同位同像素）—— 屏上必须还有东西，否则透出来的就是后面那面墙 + 一块空白。
        val entryBitmap = cardFrame
        val entryFrom = flyOrigin
        // 活画面那一族能不能接管（批 B①）：手里得有那颗**与墙卡同源**的封面地址与比例。
        // 两边都是 `GalleryPost`：详情腿 `detail.post`（`pageWallOf` 的第 0 页吃 `previewUrl` 那一档），
        // 列表腿 `works` 里同一号码那一颗（⑥ 的种子那一格吃的就是它的 `previewUrl`）。
        // 认不出来（深链、列表换过一轮、`cardRatio` 还没量出来）⇒ 交 false ⇒ 走截图族。
        val livePost: GalleryPost? = detail?.post ?: works.firstOrNull { it.id == currentId }
        val liveUrl = livePost?.previewUrl.orEmpty()
        val liveRatio = livePost?.cardRatio?.takeIf { it > 0f }
        // ⚠️ 接管判据必须是**位图到手**，不能只是"URL 非空"（2026-10-20 真机那一格泛白的病根）：
        // 按 URL 判会让截图族提前让开，而活画面这边图还没到手也什么都没画 ⇒ 两条路都不画、
        // 屏上只剩半透明的页面压在墙上（录屏逐帧证：`g007`）。所以位图在组合期取一次，
        // 拿得到才是"活画面接管"，拿不到就老实走截图族。
        val liveBitmap = rememberLiveFlyBitmap(liveUrl)
        val liveTakesOver = liveBitmap != null && liveRatio != null
        // 两颗**严格互斥**：活画面接管时截图族那颗不画（同一 from/to 画两遍就是"同一张画两份"，
        // 而那正是本仓反复在治的那一族；活画面自己没到手也不画，见那颗函数里的提前返回）。
        if ((entryFlying || entryWaiting) && entryBitmap != null && entryFrom != null && !liveTakesOver) {
            GalleryEntryFlyBody(
                progress = { if (entryFlying) entryFly.value else 0f },
                from = entryFrom,
                // 落点是晚到的那一样：量出来之前冻在起点（`entryTarget` 由等落点那段写），
                // 所以在 lambda 里每帧现取，不冻在组合期。
                to = { entryTarget ?: entryFrom },
                frame = entryBitmap,
                // ⚠️ 圆角那一档在**组合期**读（那颗是 @Composable getter），`drawBehind` 里只换算不读：
                // 绘制阶段读 compositionLocal 拿不到随主题变的值，本仓为这一格栽过（见 GalleryFlyIn 头注）。
                coverCornerRadius = veneraCoverCornerRadius(),
                // 去程**全程不淡**（同播放器那条 2026-10-01 的读数）：弹簧的百分比不是路程，
                // 中途淡出会和身后的页面叠成重影。交棒后半程才淡出（此刻页面已经接住了那一块）。
                alpha = { handoffBodyAlpha(entryHandoff.value) },
            )
        }

        // ── 活画面飞行体（批 B①）：飞的是**与墙上那张卡同一颗 URL** 的封面 ──
        // 与上面那颗截图族**二选一**（同一个 from / to / 几何，只是内容换了）：
        // - 图已到手（同 URL ⇒ 命中墙卡那个缓存项）⇒ 走活画面：同一份画从卡片形变到落点形，
        //   没有"两份"，末段也就不需要淡出补丁（`handoffBodyAlpha` 这一路整条不用上）。
        // - 没到手 ⇒ 仍走截图族（上面那颗）—— 屏上必须还有东西，否则透出来的是后面那面墙。
        // 所以这里**不**是替换：`GalleryEntryFlyBody` 仍留着（没有 URL 可用的深链那一族靠它）。
        //
        // 比例为什么取 `cardRatio`：两端（墙上那张卡 / 这一作第 0 页那一格）都按同一个比例构图
        // （列表腿与详情腿同源），所以由调用侧量一次递进去，飞行体里不猜。
        if ((entryFlying || entryWaiting) && entryFrom != null && liveTakesOver) {
            GalleryLiveFlyBody(
                progress = { if (entryFlying) entryFly.value else 0f },
                bitmap = liveBitmap,
                from = entryFrom,
                to = { entryTarget ?: entryFrom },
                imageRatio = liveRatio,
                // 起点那一档 = 墙上卡封面自己的圆角（唯一出处 `veneraCoverCornerRadius`）；
                // 终点那一档 = 这一作第 0 页那一格的圆角（`PixivPageImage` 是裸 Box ⇒ 0dp 方角）。
                // ⚠️ 两档都在**组合期**读（那颗是 @Composable getter）：绘制期读 compositionLocal
                // 拿不到随主题变的值，本仓为这一格栽过（见 `GalleryFlyIn` 头注）。
                cardCornerRadiusPx = with(density) { veneraCoverCornerRadius().toPx() },
                toCornerRadiusPx = 0f,
            )
        }

        // ── 离场飞行体（批 231-E）：三样齐了才画 ──
        // 起点是整屏那一帧、终点是墙上那一格。**首程不淡**（那一段屏上只有它），
        // 但**末段必须淡**：这一屏的落点是一张作品卡，卡上自己画着**封面**，
        // 而飞行体里装的是整页（三页图 + 动作条 + 标签）——两者不是同一张画。
        // 不淡的话，落地那一帧是一张小号页面压在封面之上，然后 finish 才换成封面，
        // 读起来是"贴错了再撕下来"（2026-10-19 慢放帧证：`s2x/t6.88`）。
        // 淡出算式与播放器那一屏同一颗（[exitBodyAlpha]），不另造一个数字。
        // 少任何一样就不画 —— 调用方（`back`）在那种情况下已经老实 finish 了。
        val flyFrom = exitFrom
        val flyTo = exitTo
        val flyBitmap = exitFrame
        // ⚠️ 与样板 `GalleryPostScreen` 逐字一致：**不**要求 `leaving`。
        // 2026-10-19 真机读数：挂着 `leaving &&` 时返回全程屏上没有飞行体（录屏逐帧证）——
        // `leaving` 是 `remember(postId)` 的 state，`back()` 里翻真之后这一层理应重组，
        // 可实测它把飞行体关在了门外。三样齐了本身就是"正在飞"的充分条件
        // （`exitFrame` 只在 capture 成功那一瞬才非空），不需要第四颗布尔。
        if (flyBitmap != null && flyFrom != null && flyTo != null) {
            // 批 B③：起点是**当前那一页图块**（`PixivPageImage` 是裸 Box、压根不裁圆角，
            // 见那颗函数的定义），所以起点档就是 0dp 方角；终点是卡里那张封面
            // （`veneraCoverCornerRadius()` = `shape.medium`）。两档之间随路程插值，
            // 两端各自精确落在该档 —— 不再吃 `dst.width / to.width` 那把旧锚
            // （批 B② 之后起点换成页块，旧锚量的是"整屏缩到卡那么大"，与新起点不再同义）。
            val pageCornerRadius = androidx.compose.ui.unit.Dp(0f)
            GalleryExitFlyBody(
                progress = { exitFly.value },
                from = flyFrom,
                to = { flyTo },
                frame = flyBitmap,
                flyCornerRadius = pageCornerRadius,
                coverCornerRadius = veneraCoverCornerRadius(),
                alpha = { exitBodyAlpha(exitFly.value) },
            )
        }
    }
}

/**
 * 评论那一节的**四种状态**（2026-10-19 · 批 231-F）。
 *
 * 为什么不是"一个列表 + 一个错误串"：这一节的四种处境在屏上必须是**四句不同的话**，
 * 而把它们折成两个变量就会出现"有错但列表非空"这种说不清的组合
 * （那时该念错还是该念列表？）。所以先生成一个封闭类型，屏上 `when` 穷尽它。
 */
internal sealed interface PixivCommentsState {
    /** 这一屏不摆评论（播放器那一版面板 / 调用方还没接）。 */
    data object Hidden : PixivCommentsState

    /** 未登录：站方那一侧不给正文，说一句"登录才看得到"就够 —— **不摆空排**。 */
    data object NeedLogin : PixivCommentsState

    /** 正在取第一页。 */
    data object Loading : PixivCommentsState

    /** 取失败：站方原话上屏（401 / 403 / 其它各按 `execute` 那两句分开念）。 */
    data class Failed(val message: String) : PixivCommentsState

    /** 取到了。[exhausted] = 站方没给 `next_url`。 */
    data class Loaded(
        val comments: List<PixivComment>,
        val totalCount: Int?,
        val exhausted: Boolean,
    ) : PixivCommentsState
}

/**
 * 评论那一节：**默认只摆最新那一条**，其余收进节头右侧那枚「更多评论」。
 *
 * 用户 2026-10-19 的读数：「评论太多了，应该只显示一条最新评论，其他评论放到查看更多评论」。
 * 三条口径：
 * - "最新那一条"吃的是**站方给的第一条**，不是我们排的序 —— 真机那一屏逐条日期是降序
 *   （2023-07-24 → 07-22 → 07-09 → 07-04），所以"第一条 = 最新"这条读得出来；
 * - 「更多评论」是**就地展开**（同一屏、同一批数据），不是再开一层，也不是去浏览器 ——
 *   浏览器那条出口留在动作行那枚 💬 上（一件事一个去处，两处都给就是两处真相）；
 * - 只有一条时**不摆这枚把手**（点开了也不会多出任何东西 = 本仓反复立案的假开关）。
 *
 * 四种状态**四种长相**，一句都不许混（这正是本仓反复立案的那一族）。
 */
@Composable
private fun PixivCommentsSection(
    state: PixivCommentsState,
    imageLoader: ImageLoader,
    /** 是否已展开全部（由宿主持有，两个宿主读同一份 —— 滚动流里展开了，抽屉那一版也必须是展开的）。 */
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val tokens = VeneraTokens
    if (state is PixivCommentsState.Hidden) return
    val loaded = state as? PixivCommentsState.Loaded
    val moreCount = (loaded?.comments?.size ?: 0) - COMMENTS_SHOWN
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = tokens.color.textSecondary,
                modifier = Modifier.size(tokens.spacing.badgeIconSize),
            )
            Spacer(Modifier.width(tokens.spacing.space2))
            Text(
                "评论",
                fontSize = tokens.type.sectionTitle,
                color = tokens.color.textPrimary,
                modifier = Modifier.weight(1f),
            )
            // 总条数只在"站方真给了"时念（`total_comments`）。念两遍会与动作行那一枚对不上。
            val total = loaded?.totalCount
            if (total != null) {
                Text(
                    "$total",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                )
                Spacer(Modifier.width(tokens.spacing.space2))
            }
            if (moreCount > 0) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = onToggleExpanded)
                        .semantics {
                            contentDescription =
                                if (expanded) "收起评论" else "查看更多评论，另有 $moreCount 条"
                        }
                        .padding(
                            horizontal = tokens.spacing.chipHorizontalPadding,
                            vertical = tokens.spacing.chipVerticalPadding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (expanded) "收起" else "更多评论",
                        fontSize = tokens.type.caption,
                        color = tokens.color.primary,
                    )
                    Icon(
                        // 箭头方向跟着这一枚**做的事**换：展开是往下收，收起是往上收。
                        if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = tokens.color.primary,
                        modifier = Modifier.size(tokens.spacing.platformGlyphSize),
                    )
                }
            }
        }
        Spacer(Modifier.height(tokens.spacing.space2))
        when (state) {
            PixivCommentsState.Hidden -> Unit
            PixivCommentsState.NeedLogin -> Text(
                "登录 pixiv 之后才看得到评论内容 —— 站方这一栏不对匿名请求答话。",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
            )
            PixivCommentsState.Loading -> Text(
                "正在取评论…",
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
            )
            is PixivCommentsState.Failed -> Text(
                state.message,
                fontSize = tokens.type.caption,
                color = tokens.color.error,
            )
            is PixivCommentsState.Loaded ->
                if (state.comments.isEmpty()) {
                    Text(
                        "这一条还没有评论",
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                    )
                } else {
                    val shown = if (expanded) state.comments else state.comments.take(COMMENTS_SHOWN)
                    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
                        // 未展开只摆最新那一条；展开后摆**已取到的这一整页**（不是再要一页）。
                        //
                        // 2026-10-19 用户第 4 条：「展开评论的时候应该，逐条错峰淡入，每条评论
                        // 依次出现，列表从下往上轻微滑入」。所以每条外面套一颗**按序错开**的入场动画，
                        // 键用评论 id —— 换一批评论（投稿换了 / 重新取数）时序列从头再演一遍，
                        // 而**已经演完的那些不会因为重组重新演**（同一个 key ⇒ 同一个 remember 槽）。
                        shown.forEachIndexed { index, comment ->
                            StaggeredCommentEntry(
                                key = comment.id,
                                index = index,
                                // 同一批里最多错开 8 条：再多的评论同时落进屏内也读不出"先后"，
                                // 只会让最后那几条等到用户以为卡住了。
                                staggerCap = COMMENT_STAGGER_CAP,
                            ) {
                                PixivCommentRow(comment, imageLoader)
                            }
                        }
                    }
                }
        }
    }
}

/**
 * 一条评论的**错峰入场**外壳：淡入 + 自下而上轻微滑入（用户 2026-10-19 第 4 条）。
 *
 * ## 只用一条 Animatable，不做"透明度一条钟、位移另一条钟"
 *
 * 两条钟各自缓动会让同一条评论的"变亮"与"落位"错开，读起来像两层东西在叠；
 * 一条 `0f → 1f` 同时喂 alpha 与位移，只有一个起止，视觉上就是"这一条整体浮上来"。
 *
 * ## 位移只许小
 *
 * 12dp —— 超过这个量，评论会像是从屏外另一处飞进来；用户要的是"轻微滑入"，
 * 不是列表元素在做转场。位移方向固定**从下往上**（起点 `+12dp`，落位 `0`）。
 *
 * ## 键与重演
 *
 * `key`（评论 id）与 `index` 一起进 [LaunchedEffect] 的键：
 * - 评论 id 变了 ⇒ 这是另一条评论 ⇒ 从头演；
 * - 位置变了（展开后前面插进新的）⇒ 也重演，读起来与"新出现"一致；
 * - 只是重组（滚动、主题切换）⇒ 键不变 ⇒ 不重演，不会边滚边闪。
 *
 * 延迟按 `index × COMMENT_STAGGER_STEP` 递增，但只取前 [staggerCap] 档：
 * 一条帖子可能一次回来几十条评论，让第 40 条等 40×40ms 会被读成"卡住了"；
 * 超过那一档之后统一按同一档起手，屏内读起来仍是"一批接一批"。
 */
@Composable
private fun StaggeredCommentEntry(
    key: Long,
    index: Int,
    staggerCap: Int,
    content: @Composable () -> Unit,
) {
    val enter = remember(key, index) { Animatable(0f) }
    LaunchedEffect(key, index) {
        val step = index.coerceAtMost(staggerCap)
        if (step > 0) delay((step * COMMENT_STAGGER_STEP_MS).toLong())
        enter.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = COMMENT_ENTER_MS,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    val lift = with(LocalDensity.current) { COMMENT_ENTER_LIFT.toPx() }
    Box(
        modifier = Modifier.graphicsLayer {
            val p = enter.value
            alpha = p
            // 起点在下方（+lift），随进度回到原位 —— "从下往上"就是这一条符号。
            translationY = (1f - p) * lift
        },
    ) {
        content()
    }
}

/** 一条评论：头像 + 名字 + 时间 + 正文；回复别条时带一句「回复 #id」。 */
@Composable
private fun PixivCommentRow(comment: PixivComment, imageLoader: ImageLoader) {    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(tokens.spacing.space11)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(tokens.color.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (comment.userAvatarUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(comment.userAvatarUrl).build(),
                    contentDescription = comment.userName,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 删号 / 没给头像 ⇒ 首字母座，**不拿别人的脸顶**。
                Text(
                    comment.userName.take(1).ifBlank { "?" },
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.userName.ifBlank { "已注销用户" },
                    fontSize = tokens.type.caption,
                    fontWeight = tokens.type.weightSemibold,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 时间只摆站方给的 ISO 串**前半截**（日期 + 时分）。换算与时区归
                // `PixivWorkPage` 那一层管，本屏不自己造一个格式。
                comment.date?.take(16)?.replace('T', ' ')?.let { shortDate ->
                    Text(
                        "  $shortDate",
                        fontSize = tokens.type.overline,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                    )
                }
            }
            if (comment.replyToId != null) {
                Text(
                    "回复 #${comment.replyToId}",
                    fontSize = tokens.type.overline,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                )
            }
            PixivCommentBody(comment, imageLoader)
        }
    }
}

/**
 * 一条评论的正文：**小表情 `(name)` 画成站方那张图**，**贴纸（stamp）画在正文下方**
 * （用户 2026-10-19「也要正常显示评论图和表情」）。
 *
 * ## 三条口径
 *
 * - **认得的才换图**：表里没有那一串原样念出 `(name)` —— 站方哪天新增一枚，屏上读到的是它的
 *   字面短码，不是一块破图、也不是一个空洞（地址与号段见 `domain/PixivCommentEmoji`）；
 * - **表情尺寸吃 1em**（正文字号），不另造一个数；贴纸吃 [VeneraSpacing.artistAvatarSize]
 *   这一档（与本页画师头像同值，读起来正好"一行高"）；
 * - **纯贴纸评论**（正文空）也要摆：那一档由取数层放行（见 `PixivAppParse.illustComments`），
 *   这里 `segments` 为空就只画贴纸。
 *
 * ⚠️ 排版走分段流而不是 `InlineContent`：Compose 1.12 那颗 API 的签名本鱼没在源码里对上，
 * 与其押一个没验过的形状，不如用一维 `FlowRow` —— pixiv 评论正文是 CJK 为主，
 * 段与段之间换行的读感与站方一致，而文字段内部照常自动折行。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PixivCommentBody(comment: PixivComment, imageLoader: ImageLoader) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val emojiSize = with(LocalDensity.current) { tokens.type.caption.toDp() }
    val segments = remember(comment.text) { splitPixivComment(comment.text) }
    if (segments.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = tokens.spacing.space1),
            horizontalArrangement = Arrangement.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            segments.forEach { segment ->
                when (segment) {
                    is PixivCommentSegment.Text -> Text(
                        segment.value,
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                    )
                    is PixivCommentSegment.Emoji -> {
                        val id = pixivEmojiId(segment.name)
                        if (id == null) {
                            Text(
                                "(${segment.name})",
                                fontSize = tokens.type.caption,
                                color = tokens.color.textSecondary,
                            )
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(pixivEmojiUrl(id)).build(),
                                // 读屏念的是"表情 什么"，与屏上那一小块图同一份出处。
                                contentDescription = "表情 ${segment.name}",
                                imageLoader = imageLoader,
                                modifier = Modifier
                                    .padding(horizontal = tokens.spacing.space1)
                                    .size(emojiSize),
                            )
                        }
                    }
                }
            }
        }
    }
    if (comment.stampUrl.isNotBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(comment.stampUrl).build(),
            contentDescription = "贴纸",
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(top = tokens.spacing.space2)
                .size(tokens.spacing.artistAvatarSize)
                .clip(RoundedCornerShape(tokens.shape.badge)),
        )
    }
}

/**
 * 把一条作品长成大图播放器那一面墙（**这一颗文件里只此一处**调 domain 那颗函数）。
 * 布局那一头要按页取 viewerKey 回写落点，起跳那一头要交整面墙 —— 两处各抄一遍
 * 就是两把算式，改日页序或档位一变，只有真机能看见。
 */
private fun pageWallOf(detail: PixivWorkDetail?): List<GalleryPost> =
    detail?.let { PixivWorkPage.pageWallOf(it) }.orEmpty()

/** 相关图片那一排最多摆几枚：三列 × 三行，再多就是另一屏（那一屏叫搜索结果）。 */
private const val RELATED_MAX = 9

/**
 * 评论这一节最多摆几条。
 *
 * 第一页（站方默认给 10 条左右）就够"顺带看一眼"；这一屏的主轴是图，不是评论区。
 * 要看全部本来就是站方网页更合适 —— 动作行那枚「在浏览器里看评论」就是那条出口。
 */
/**
 * 未展开时摆几条（2026-10-19 用户：「评论太多了，应该只显示一条最新评论」）。
 *
 * 从前是 10 条全摆 —— 那一屏的主轴是图，评论区把详情段撑成一堵字墙。
 * 现在这一页取到的其余几条收在节头那枚「更多评论」后面（就地展开，不再要第二页）。
 */
private const val COMMENTS_SHOWN = 1

/**
 * 评论展开时逐条错峰入场的三枚观感数字（用户 2026-10-19 第 4 条）。
 *
 * 三枚一起改才有意义：
 * - [COMMENT_STAGGER_STEP_MS] 是**相邻两条**的起手间隔。太小（<20ms）读不出"依次"，
 *   太大（>80ms）会让十几条评论排成一条长队、像加载卡住；40ms 落在"看得出来是先后、
 *   又不像在等"的那一档。
 * - [COMMENT_ENTER_MS] 是**单条**的入场时长。必须大于步长，否则每条都演完了下一条才起手，
 *   读起来是一串独立的小闪；比步长长一些才会叠成一条连续的流。
 * - [COMMENT_STAGGER_CAP] 是**最多错开几档**。一条帖子一次可能回来几十条评论，
 *   不封顶的话第 40 条要等 1.6 秒（用户会读成"点了没反应"）；封顶之后剩下的按同一档
 *   起手，屏内读起来仍是"一批接一批"。
 */
private const val COMMENT_STAGGER_STEP_MS = 40
private const val COMMENT_ENTER_MS = 260
private const val COMMENT_STAGGER_CAP = 8

/** 单条评论入场的上浮行程：只许"轻微"，量取 12dp（超过这个量就像从屏外另一处飞进来）。 */
private val COMMENT_ENTER_LIFT = 12.dp

/**
 * 进场飞行体等**落点量出来**的预算（毫秒）。值与 `GalleryPostScreen` 那颗同名常量同一档，
 * 因为那条读数就是这里要防的：先前停在 400ms 时真机 `target=null waited=406ms`，放宽到 600ms
 * 仍然不飞 —— 真实原因是页面站在窗口外、落点被裁成 0×0。**预算不是病，姿态才是病。**
 * 这一屏的落点由 [PixivPageImage] 自己回报（它一定在屏内），所以这一档只作保险：
 * 超时不飞，屏上不会留一块空白，也不会把那一帧一直攥着。
 */
private const val ENTRY_WAIT_MS = 600L

/**
 * 飞行体落位之后，再等**落点那一格真画出像素**的时长上限（毫秒）。
 * 与样板那颗 `HANDOFF_MS` 同值同义：撤早了就是把飞行体摞在一块骨架上再抽走 —— 一次闪。
 * 超时也撤（图压根下不来时不能一直盖着屏）。
 */
private const val ENTRY_HANDOFF_MS = 160L

/**
 * 等"飞行走到 [HANDOFF_DETAIL_LEAD_FLY]"的预算上限（毫秒）。
 *
 * 只是兜底：弹簧被打断或提前判 settled 时内容区也必须起来 —— 那一格起不来就是详情页永远空白。
 */
private const val ENTRY_DETAIL_WAIT_MS = 260L

/**
 * 顶栏那一排（返回 / 页码 / 更多）登记进 [ChromeCaptureSuppress] 用的键。
 *
 * 键里带屏名而不是与底栏共用一枚：转场期两屏可能同时在组合里，共用键时先卸的那一支
 * 会把还在用的登记一起撤掉（`reportChromeBand` 的头注记着这条，方向是安全但不该主动撞）。
 */
private const val PIXIV_WORK_TOP_CHROME_BAND = "pixiv-work-top"

/** 站方那一作的地址（`pixivWorkUrl`）与"在浏览器里打开"（`openWorkInBrowser`）都住在 `PixivWorkInfoPanel.kt` —— 两个宿主共用同一颗，不在这里留抄本。 */

/**
 * 作品页那一档（唯一一档，2026-10-18 第四批撤了横向那一档）：一条 `LazyColumn` 里先把这一作的
 * 几页摆完，再把详情段接在末页之后，最后才是「下一作品」那一格 —— **全程一个滚动容器**
 * （详情不是抽屉里的第二个滚动者）。
 *
 * 每一页都可点：点开交大图播放器，而那一层翻的是**这几页**（[openPages] 写 [GalleryPageAxis]）。
 * 换作品走这一条流的**左右两轴**（外层 pager）：滚到末尾那一格是提醒，不是唯一出口。
 */
@Composable
private fun PixivWorkFlow(
    detail: PixivWorkDetail?,
    /**
     * 批 B⑥（不等落点）：详情没到时**只用来摆第 0 页那一格**的种子。
     *
     * 为什么单开这一枚而不复用 `detail`：详情段（标题 / 动作 / 标签 / 评论）那一支读的是
     * `detail` 本体，拿种子顶上去就是拿空标题空计数摆一张脸 —— 屏上念的没人看得懂的读数。
     * 所以这里画页只问种子，其余三处（详情段 / ugoira 分流 / 页码语义）一概仍只认真 `detail`。
     * 由宿主在"这一槽没有详情 + 墙里认得出这位"时算好递进来；认不出 ⇒ null ⇒ 与从前逐字一致。
     */
    seedDetail: PixivWorkDetail? = null,
    seam: PixivPageSeam,
    detailsInFlow: Boolean,
    imageLoader: ImageLoader,
    related: List<GalleryPost>,
    tagsExpanded: Boolean,
    onExpandTags: () -> Unit,
    onOpenArtist: (GallerySite, String, String) -> Unit,
    onDownload: (PixivWorkDetail) -> Unit,
    openPages: (PixivWorkDetail?, Int, Rect?) -> Unit,
    onOpenWork: (Long) -> Unit,
    onOpenViewer: (Long) -> Unit,
    /** 墙里认得出的下一位；认不出（深链直开、或这一排已到最后一条）就交 null ⇒ 末尾那一格不摆。 */
    nextWork: GalleryPost?,
    onOpenNextWork: () -> Unit,
    bottomClearance: Dp,
    aiBadgeOn: Boolean,
    /** 评论那一节的状态（批 231-F）。默认 Hidden ⇒ 这一版面板不摆评论。 */
    comments: PixivCommentsState = PixivCommentsState.Hidden,
    /** 评论是否已展开（宿主持有：滚动流与抽屉两个宿主读同一份，见 `PixivWorkScreen` 那侧的 `commentsExpanded`）。 */
    commentsExpanded: Boolean = false,
    onToggleCommentsExpanded: () -> Unit = {},
    /** 第几页**真画出像素**了（进场飞行体落点那一格要等这一句，见 [PixivPageImage]）。 */
    onPageDrawn: (Int) -> Unit = {},
    /** 这一槽自己滚到了第几条（顶栏那颗页位读数）。由本槽的 state 报上来，宿主按位号过滤。 */
    onPageIndexChanged: (Int) -> Unit = {},
    /**
     * **图那一块**的入场透明度（交棒那条钟：飞行期恒 0、落位之后 0→1）。
     *
     * 交的是取数 lambda 而不是 Float：这个值每帧都在变，当参数传会把整条流每帧拖进重组；
     * 交给 `graphicsLayer` 在绘制期读，只失效图层。
     */
    pageEntryAlpha: () -> Float = { 1f },
    /**
     * **图以下那一块**（详情段 / 末页之后那几格）的入场透明度（它自己那条钟，见 `detailEntryAlpha`）。
     *
     * 与 [pageEntryAlpha] 分开的理由就是 2026-10-19 用户报的那一条：合在一条上，内容区只能等图飞完。
     */
    detailEntryAlpha: () -> Float = { 1f },
    /**
     * 这一槽的**滚动进度**（0..1，一旦到顶就是 0）。
     *
     * 顶栏那颗模糊背板要"顶部不出、上滑跟手渐显"，读数只能从真正在滚的那面墙里取 ——
     * 而每槽一颗 `LazyListState`（见上面那条注释），所以由槽自己算好递上去。
     *
     * 交的是 lambda 不是 Float：滚动时这个值每帧都在变，当参数传等于把整条流每帧拖进重组。
     */
    onScrollProgress: ((Float) -> Unit)? = null,
    /** 点一枚作品标签 → 按它搜一轮（透传给 [PixivWorkDetails]，理由见那里）。 */
    onSearchTag: ((String) -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val gap = if (seam == PixivPageSeam.NONE) tokens.spacing.none else tokens.spacing.hairline
    // 批 B⑥：详情没到时画种子那一格。**只有页那一条**用种子：`detail` 为空的槽是"连详情都没
    // 到位"的槽，`detail` 非空就不必种子（种子页只有一枚且带糊图，下去只会把稳的页顶掉）。
    val pages = detail?.pages.orEmpty().ifEmpty { (detail ?: seedDetail)?.pages.orEmpty() }
    // 顶栏模糊的淡入行程（与 `VeneraTopAppBar` 同一个 48dp 口径，不在这里另造一个数）。
    val scrollBlurThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    // **每槽一颗**（不是宿主发下来的一颗共享 state，理由见调用处那条注释）：
    // `rememberLazyListState()` 本身走 `rememberSaveable`，键在组合槽上 ⇒ 横翻回来还在原来那一页。
    val listState = rememberLazyListState()
    /**
     * 页第一次长出来那一刻，把锚拽回顶部（2026-10-19 · 批 233-2 的另一半）。
     *
     * 真机读数（用户「图还是会被顶上去……往下拉一点就正常，从画廊进来就不正常」，现场取的）：
     * 站方详情腿回 `"width":2400,"height":4000` 而设备里画的那张 medium 是 324x540（同比例，没裁），
     * 可 `uiautomator` 量到那一格只有 `[0,0][1080,979]`，像素层复量左右**没有黑边**
     * ⇒ 不是比例算错，是**上面那 821px 在屏外** —— 这一格被滚走了。
     *
     * 成因是本仓量过那一族：懒列表在锚**之上**插入项时锚不动（首页顶栏重叠那次同一根因）。
     * 进场那一刻 `detail` 还没到 ⇒ 这一摞页压根不存在，锚停在详情那几格上；页长出来那一刻整摞
     * 插在锚之上，于是第一页被顶出屏外。用户"往下拉一点"就是手动把锚拽回第 0 项。
     *
     * 归零只发生在**"这一作第一次有页"**那一刻：横翻走再翻回同一作时页数不变 ⇒ 不归零，
     * 那条"横翻回来还在原来那一页"的既有裁决照旧成立（所以键不能写成 `Unit`）。
     *
     * 批 B⑥（不等落点）之后这一条依然成立，只是**名单换了**：从前等的是详情长出的那一摞，
     * 现在首帧就是种子那一格（糊图、只有一枚）—— 见下面 `pages` 那行与 `pagesAppeared` 那处。
     */
    val workId = (detail ?: seedDetail)?.post?.id ?: 0L
    var pagesAppeared by remember(workId) { mutableStateOf(false) }
    LaunchedEffect(workId, pages.size) {
        // 归零只认**真页**：批 B⑥ 的种子只有一枚糊图，不算"页长出来了"。详情一到立刻归零 ——
        // 那时整摞真页醒着，多 P 作一次多出好几摞，正是批 233-2 防的那一"顶出屏外"。
        if (pages.isNotEmpty() && !pagesAppeared && detail != null) {
            pagesAppeared = true
            // ── 临时取证探针（`FlyProbe` 同批）：归零**之前**锚在哪儿，就是被顶上去的那一段 ──
            Log.i(
                "FlyProbe",
                "页第一次长出 id=$workId idx=${listState.firstVisibleItemIndex} " +
                    "off=${listState.firstVisibleItemScrollOffset} 页数=${pages.size}",
            )
            // ⚠️ 归零只认**真页**：`pages` 在批 B⑥ 里可能是种子那一格（只有一枚糊图）。
            // 拿种子当"页长出来了"的话，详情到手那一刻 `pagesAppeared` 已经翻真，锚不再归零 ——
            // 而那一刻醒着的是整摞真页（多 P 作一次多出好几摞），正是这一条要防的那一跳。
            // 批 B⑥：种子那一格首帧就在锚上 ⇒ 真页到手时不再有"整摞插在锚之上"那一跳
            // （批 233-2 那条"第一页被顶出屏外"读数的成因）。归零照旧只在第一次有页时发生。
            listState.scrollToItem(0)
        }
    }
    // 逐页墙仍由 domain 那颗纯函数长出来：屏上不自己拼 "$uid:p$index"（拼一份就是第二处真相，
    // 而它连着的正是"返回程飞不回这一页"那一条）。批 B⑥:详情或种子任一场合都走这一颗。
    val wallPages = remember(detail, seedDetail) { pageWallOf(detail ?: seedDetail) }
    LaunchedEffect(listState, pages.size) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect {
            // 条目里除了页还有详情段那几格，页位要夹在 [0, pages-1] 内再报上去。
            onPageIndexChanged(if (pages.isEmpty()) 0 else it.coerceAtMost(pages.size - 1))
        }
    }
    // ── 顶栏模糊的滚动读数（用户 2026-10-19：「在顶部的时候不要有顶栏模糊，往上滑的时候再出现」）──
    //
    // 口径照全站顶栏那一档（`VeneraTopAppBar`）：**走 48dp 线性淡入**，不看 `canScrollBackward`
    // 那一类布尔 —— 布尔只能给两档，而这里要的是跟手。
    //
    // 用 `snapshotFlow` + `distinctUntilChanged` 而不是每帧无条件报：列表布局每帧都会刷新
    // `firstVisibleItemScrollOffset`，无条件写上去等于让宿主每帧重组一次。
    LaunchedEffect(listState, pages.size, onScrollProgress) {
        if (onScrollProgress == null) return@LaunchedEffect
        snapshotFlow {
            val offsetPx = listState.firstVisibleItemScrollOffset.toFloat()
            val index = listState.firstVisibleItemIndex
            // 第 0 项之外（详情段、末页之后那几格）一律算"已经滑走"：那时顶栏必须全显，
            // 不能因为回到某一项的顶端就把模糊收掉。
            if (index > 0) 1f else {
                val thresholdPx = scrollBlurThresholdPx
                if (thresholdPx <= 0f) 0f else (offsetPx / thresholdPx).coerceIn(0f, 1f)
            }
        }
            .distinctUntilChanged()
            .collect(onScrollProgress)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomClearance),
    ) {
        if (detail?.ugoira == true) {
            // 动图（ugoira）不在这一屏放：站方给的是 zip 加一张静帧，这一屏只会画出那张静帧，
            // 而旧看图器已经会解动画。所以这里明说一句并给一条真出口，不装作能放。
            item(key = "ugoira-${detail.post.id}") {
                // 这一格是**字**不是图：跟着内容区那条钟走（ugoira 没有页 ⇒ 压根不飞，两档都是 1）。
                Box(Modifier.graphicsLayer { alpha = detailEntryAlpha() }) {
                    // 2026-10-19 用户报「动图这一作整屏上半是白的」—— 立案读数：这一支从前**只摆一句字**，
                    // 而站方对 ugoira 同样给了 `image_urls` 那几档（第一帧的静图，见 `PixivAppParse` /
                    // `PixivFeedParse` 的 workDetail：它们照常长出了 `pages`）。于是图上那几百像素
                    // 一片空白，读起来像"这一屏没加载出来"。
                    //
                    // 现在把手里那第一帧摆上去（中档，与连读同一档，不为静帧去拉原图），
                    // 提示语与出口接在图下面 —— 明说"只画得出第一帧"这件事没变，只是不再空着。
                    val still = pages.firstOrNull()
                    if (still != null) {
                        PixivPageImage(
                            page = still,
                            loader = imageLoader,
                            description = pageSemantic(0, pages.size),
                            viewerKey = wallPages.firstOrNull()?.viewerKey.orEmpty(),
                            onClick = { rect -> openPages(detail, 0, rect) },
                            onDrawn = { onPageDrawn(0) },
                        )
                    }
                    UgoiraNotice { onOpenViewer(detail.post.id) }
                }
            }
        } else {
            // 批 B⑥：没详情时 `pages` 是种子那一格（只有第 0 页，糊图，站方真比例）。
            // 页码语义与点击出口仍只认真 `detail`：种子还没资格念"共几页"，也还不能把一摞
            // 半份页轴交给播放器（那正是"站方说 4 页而手里只有 1 页"的假话）。
            items(pages.size, key = { "page-$it" }) { index ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        // 图那一块挂**交棒**那条钟：落位之前恒 0，否则屏上同时有两份同一张画。
                        .graphicsLayer { alpha = pageEntryAlpha() },
                ) {
                    if (index > 0 && gap > tokens.spacing.none) Spacer(Modifier.height(gap))
                    PixivPageImage(
                        page = pages[index],
                        loader = imageLoader,
                        description = if (detail != null) pageSemantic(index, pages.size) else "",
                        viewerKey = wallPages.getOrNull(index)?.viewerKey.orEmpty(),
                        onClick = { rect -> openPages(detail, index, rect) },
                        onDrawn = { onPageDrawn(index) },
                    )
                }
            }
        }
        // 详情段：只在"接在末页之后"那一档开着时才是这条流里的一段；关掉它，详情只在抽屉里。
        if (detail != null && detailsInFlow) {
            item(key = "detail-${detail.post.id}") {
                PixivWorkDetails(
                    detail = detail,
                    related = related,
                    tagsExpanded = tagsExpanded,
                    onExpandTags = onExpandTags,
                    onOpenArtist = onOpenArtist,
                    artist = pixivArtistActions(detail.post.author),
                    actions = pixivWorkActions(detail, onDownload = { onDownload(detail) }),
                    avatarUrl = pixivAvatarUrl(detail),
                    imageLoader = imageLoader,
                    onOpenWork = onOpenWork,
                    aiBadgeOn = aiBadgeOn,
                    comments = comments,
                    commentsExpanded = commentsExpanded,
                    onToggleCommentsExpanded = onToggleCommentsExpanded,
                    onSearchTag = onSearchTag,
                    // `graphicsLayer` 必须排在 `background` **之前**（更外层）：排后面时
                    // 底色画在图层外面，淡入会变成"一块不透明的底 + 一层淡的字"。
                    modifier = Modifier
                        .graphicsLayer { alpha = detailEntryAlpha() }
                        .background(tokens.color.surface),
                )
            }
        }
        // 「下一作品」那一格（用户 2026-10-18 第三批「在最后一页再滑动设计个提醒，然后再切换下一个
        // 作品（最好有些 ui 提醒）」的存活形态：横档撤了，这一档的"往下"就是滚到这条流的末尾）。
        // 只在墙里认得出下一位时才摆 —— 没有下一位还摆一枚把手就是假开关。
        if (nextWork != null) {
            item(key = "next-work-${nextWork.id}") {
                Box(Modifier.graphicsLayer { alpha = detailEntryAlpha() }) {
                    PixivNextWorkRow(work = nextWork, onOpen = onOpenNextWork)
                }
            }
        }
    }
}

/** 底部 chrome 由现量高度让位（见调用点），这里不留写死的数。 */

/**
 * 这一屏的其中一页：摆**中档**（`PixivPage.mediumUrl`），图没落位之前摆现成的骨架。
 *
 * 格子按**屏宽 + 站方报的真比例**预留（骨架有高度可撑）；站方没报宽高就不预留 ——
 * 拿 1:1 顶一个会在图落位那一刻把整屏顶一下（`placeholderAspect` 那条判据）。
 *
 * ⚠️ "不预留"不等于"让子件自己找高度"（2026-10-18 真机读数：「多 p 图片被压得很小」
 * 「进入图片后得要往下拉一点才能显示正常大小」）。缺比例那一档如果仍按 `fillMaxSize()` 塞，
 * 父容器给的是**无界高度**，Compose 会退回位图自己的像素尺寸 —— 中档只有 540~768 宽，
 * 于是整格缩成半屏小方块。现在的口径：缺比例就 `wrapContentHeight()`，图一到手立刻按它
 * **自己报回来的比例**补上那一格（`intrinsicSize`，见下面那颗 `loadedAspect`）。
 * 为什么只有多页会踩：登录腿的逐页对象里**没有** `width`/`height`（那是匿名腿 `/pages` 那一跳才给的），
 * 而第 0 页另有作品根节点的宽高兜着 —— 所以症状精确地长在"第 2 页往后"。
 * 横向翻页那一档已随模式切换一起撤掉（2026-10-18 第四批），所以这里不再有"铺满视口"那一支。
 *
 * 这一屏**不挂缩放层**：双击与捏合都住在大图播放器那一层（用户 2026-10-18 拍板"只在大图播放器里"）。
 * 顺带消掉一处真缺陷：连读那一档的 `maxHeight` 是 Infinity，缩放上限算出来也是 Infinity，捏合直接失灵。
 */
@Composable
private fun PixivPageImage(
    page: PixivPage?,
    loader: ImageLoader,
    description: String,
    /** 这一页在大图播放器那一侧的**逐页身份键**（`GalleryPost.viewerKey`，由 `pageWallOf` 长出）。
     *  空串 = 认不出这一页是谁，那就不回写落点（回写一条错键比不回写更糟）。 */
    viewerKey: String,
    /** 点击交出**这一页此刻的窗口矩形**（去程起点那一帧要它，见 [openPages] 那头）。 */
    onClick: (Rect?) -> Unit,
    /** 这一页**真画出像素**了（成功或失败都算，失败时屏上是错误占位而不是骨架）。
     *  进场飞行体撤之前要等这一句：落点还空着就撤，等于把飞行体摞在一块骨架上再抽走 —— 一次闪。 */
    onDrawn: () -> Unit,
) {
    val context = LocalContext.current
    val url = page?.mediumUrl.orEmpty()
    val aspect = PixivWorkPage.placeholderAspect(page?.width, page?.height)
    var drawn by remember(url) { mutableStateOf(false) }
    // 站方没报比例时，图自己落位后报回来的那一档就是这一格的比例（不是我们编的）。
    var loadedAspect by remember(url) { mutableStateOf<Float?>(null) }
    // 布局回调每帧都来（Lazy 项在窗口坐标里一直在动），所以只在**真的变了**才写 ——
    // 无条件赋值等于滚动每一帧把这格重组一遍，墙上那颗卡片栽过同一条（见 GalleryFlyIn 头注）。
    var rect by remember(url) { mutableStateOf<Rect?>(null) }
    DisposableEffect(viewerKey) {
        onDispose { if (viewerKey.isNotBlank()) GalleryFlyIn.forgetCardBounds(viewerKey) }
    }
    // 有比例（站方报的，或图落位后自己报的）就按它预留；一个都没有才让高度随内容走。
    // 顺序不能反：`wrapContentHeight()` 与 `aspectRatio()` 同时挂是两种互相矛盾的高度口径。
    val boxAspect = aspect ?: loadedAspect
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (boxAspect != null) Modifier.aspectRatio(boxAspect) else Modifier.wrapContentHeight())
            .onGloballyPositioned {
                // 截像素/点击矩形那一路必须吃裁过的（PixelCopy 只读窗口内表面）：显式写出来。
                val bounds = it.boundsInWindow(clipBounds = true)
                // 批 B §3 地基：登记落点吃没裁过的那一把。pixiv 的页是竖长条，
                // 真机读数 width 2400 / height 4000 而窗口那格只有 [0,0][1080,979] ——
                // 从前把裁短的矩形当落点，交棒那一帧画面高度对不上（读起来"错位/压一下"）。
                val full = it.boundsInWindow(clipBounds = false)
                // 零尺寸只忽略不注销（Lazy 项回收前最后一次回调给的就是 0×0），
                // 真没了由上面那颗 DisposableEffect 负责。
                if (bounds.width > 0f && bounds.height > 0f) {
                    if (bounds != rect) rect = bounds
                    // 返回程的落点：大图那一屏离场时按**同一把键**来查这一格。
                    if (viewerKey.isNotBlank()) GalleryFlyIn.noteCardBounds(viewerKey, full, bounds)
                }
            }
            // 点整页 = 交大图播放器（那一层才有缩放与 1:1）；这一屏自己不挂缩放层。
            .clickable { onClick(rect) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                imageLoader = loader,
                onSuccess = { state ->
                    drawn = true
                    onDrawn()
                    val size = state.painter.intrinsicSize
                    if (size.width > 0f && size.height > 0f) loadedAspect = size.width / size.height
                },
                onError = { drawn = true; onDrawn() },
                // 有预留比例 ⇒ 铺满那一格；没有 ⇒ 只钉住宽度，高度让图自己的比例说话。
                // 无界高度里再挂 `fillMaxSize()` 就是本轮那个"缩成小方块"的成因。
                modifier = if (boxAspect != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            )
        }
        // 骨架盖在同一格里（`matchParentSize` 不参与父测量，不会因为多一颗子件把格子撑大）。
        if (!drawn) VeneraShimmer(modifier = Modifier.matchParentSize())
    }
}

private fun pageSemantic(index: Int, total: Int): String =
    if (total <= 0) "" else "第 ${index + 1} 页，共 $total 页"

/**
 * 这条流末尾那一格提醒（用户 2026-10-18 第三批：「在最后一页再滑动设计个提醒，然后再切换下一个
 * 作品（最好有些 ui 提醒）」；第四批撤了横向那一档之后，它的存活形态就是"滚到这条流的末尾"）。
 *
 * 三条口径：
 * - **只在墙里认得出下一位时才摆**（判据在调用方那一句 `works.getOrNull(slot + 1)`）——
 *   没有下一位还摆一枚把手，按下去不动就是本仓反复立案的假开关；
 * - 念的是**站方给的那一位**的画师名，不写"这一排还有更多"这种屏上验不了的话；
 * - 点这里与左右滑是同一件事（外层 pager 走一格），不另造一条换作品的路。
 */
@Composable
private fun PixivNextWorkRow(work: GalleryPost, onOpen: () -> Unit) {
    val tokens = VeneraTokens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "下一作品，画师 ${work.author}" }
            .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        Column(Modifier.weight(1f)) {
            Text("下一作品", fontSize = tokens.type.caption, color = tokens.color.textTertiary)
            Text(
                work.author,
                fontSize = tokens.type.itemTitle,
                color = tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = tokens.spacing.space1),
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = "切到下一作品",
            tint = tokens.color.textSecondary,
            modifier = Modifier.size(tokens.spacing.badgeIconSize),
        )
    }
}

/**
 * 标题下面那一行：浏览 · 收藏 · 发布，三枚单色描边小图标。
 *
 * **站方没给的那格整格掉出去**（判据在 [PixivWorkPage.facts]）—— 用户 2026-10-18「虚假数据去掉」，
 * 所以这里不出现"0 浏览"这种看着像读数的占位。
 */
@Composable
private fun PixivWorkFactsLine(detail: PixivWorkDetail?, aiBadgeOn: Boolean) {
    val tokens = VeneraTokens
    val facts = detail?.let { PixivWorkPage.facts(it.viewCount, it.post.favCount, it.uploadDate) }.orEmpty()
    Row(
        Modifier.fillMaxWidth().padding(top = tokens.spacing.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        facts.forEachIndexed { index, cell ->
            if (index > 0) Text("·", fontSize = tokens.type.caption, color = tokens.color.textTertiary)
            FactIcon(cell.kind, tokens.color.textSecondary)
            Text(cell.text, fontSize = tokens.type.caption, color = tokens.color.textSecondary)
        }
        if (detail != null && aiBadgeOn && GalleryAi.isAiMarked(detail.post.siteAiFlag, detail.tags)) {
            Text("AI", fontSize = tokens.type.badge, color = tokens.color.error)
        }
    }
}

@Composable
private fun FactIcon(kind: PixivWorkFact, tint: Color) {
    val vector: ImageVector = when (kind) {
        PixivWorkFact.VIEWS -> Icons.Outlined.Visibility
        PixivWorkFact.BOOKMARKS -> Icons.Outlined.Bookmarks
        PixivWorkFact.PUBLISHED -> Icons.Outlined.DateRange
    }
    Icon(vector, contentDescription = null, tint = tint, modifier = Modifier.size(VeneraTokens.spacing.platformGlyphSize))
}

/**
 * 详情段：动作那一行（收藏 / 评论 / 下载 / 分享）+ 画师行 + 原题 + 计数行 +
 * 标签（同屏 8 枚封顶）+ 相关图片。
 *
 * 这一段的动作从前住在屏底那排 dock 上（用户 2026-10-18：与顶栏那枚「更多信息」重复进同一层），
 * 现在它是「更多信息」那一层的内容开头 —— 滚动流末尾与抽屉两处画的是同一颗，动作不会分叉。
 *
 * `internal` 而不是 `private`：大图播放器那一版面板（`pixivWorkInfoPanel`）画的是**同一颗**，
 * 差别只在宿主各自交进来的那几枚回调。两处各写一份详情段，就是本仓反复立案的那种分叉。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PixivWorkDetails(
    detail: PixivWorkDetail,
    related: List<GalleryPost>,
    tagsExpanded: Boolean,
    onExpandTags: () -> Unit,
    onOpenArtist: (GallerySite, String, String) -> Unit,
    artist: PixivArtistActions,
    actions: PixivWorkActions,
    avatarUrl: String?,
    imageLoader: ImageLoader,
    onOpenWork: (Long) -> Unit,
    aiBadgeOn: Boolean,
    /**
     * **评论那一节的状态**（2026-10-19 · 批 231-F）。
     *
     * 默认 [PixivCommentsState.Hidden]：不传就当"这一屏没有评论可摆" ——
     * 播放器那一版面板（`pixivWorkInfoPanel`）不传，于是那一层不会多出一块空节。
     */
    comments: PixivCommentsState = PixivCommentsState.Hidden,
    /** 评论是否已展开（两个宿主共用同一份，见 `PixivWorkFlow` 那侧同名参数）。 */
    commentsExpanded: Boolean = false,
    onToggleCommentsExpanded: () -> Unit = {},
    /**
     * 点一枚作品标签 → 按它搜一轮（用户 2026-10-19：「作品标签目前不可以点击，
     * 点击后跳转到标签的搜索页面」）。
     *
     * `null` = 这一版**没有搜索出口**，标签保持纯展示（不摆点了没反应的假开关）。
     * 两个宿主都接得上：作品页交的是 `GallerySearchHandoff`，播放器面板交的是它自己那条。
     */
    onSearchTag: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // 复制与屏蔽两个动作要有 Context（剪贴板 / Toast / 落库那一路的端口），
    // 与信息卡那面标签墙同一套取法（那边也是 `LocalContext.current` 现取，不收形参）。
    val chipContext = LocalContext.current
    // 屏蔽是 suspend 的，作用域建在**详情段这一级**（理由见 `blockTagText` 头注）：
    // 每枚 chip 自己建的话，菜单关闭那一刻 chip 已被移出组合就会把那一笔写库掐掉。
    val tagScope = rememberCoroutineScope()
    val shown = PixivWorkPage.tagsShown(detail.tags, tagsExpanded)
    val hidden = PixivWorkPage.tagsHidden(detail.tags)
    var artistMenu by remember(detail.post.author) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(top = tokens.spacing.space6)) {
        PixivWorkActionRow(actions)
        Spacer(Modifier.height(tokens.spacing.space4))
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    // 点 = 进画师介绍页；长按 = 「屏蔽该画师」那一份菜单（与画师卡同一手势口径，
                    // 菜单本身就是那一步确认，不在屏上摆一枚一按就挡人的把手）。
                    .combinedClickable(
                        onClick = { onOpenArtist(GallerySite.PIXIV, detail.post.author, detail.post.source) },
                        onLongClick = { artistMenu = true },
                    )
                    .padding(horizontal = tokens.spacing.screenHorizontal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                GalleryArtistAvatar(
                    avatarUrl = avatarUrl,
                    name = detail.post.author,
                    imageLoader = imageLoader,
                    modifier = Modifier.size(tokens.spacing.artistAvatarSize),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        detail.post.author,
                        fontSize = tokens.type.itemTitle,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        detail.title,
                        fontSize = tokens.type.body,
                        color = tokens.color.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = tokens.spacing.space1),
                    )
                    PixivWorkFactsLine(detail, aiBadgeOn)
                }
                // 关注胶囊：判定复用 `GalleryArtistFollows.isFollowing`，写侧走 `ports.follows.toggle`
                // —— 与「画廊 → 关注画师」那一档读同一份存档，点完那一栏当场多一枚。
                // 2026-10-19 规格照那一档：**没关注时是实心的主色药丸 + 一枚「+」**（从前是光秃秃一行字，
                // 与它左边那行画师名同色，读起来不像把手）；已关注退成灰底、不带「+」。
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            if (artist.isFollowed) tokens.color.surfaceContainerHigh else tokens.color.primary
                        )
                        .clickable(onClick = artist.onToggleFollow)
                        .semantics {
                            contentDescription =
                                if (artist.isFollowed) "已关注 ${detail.post.author}，点按取消关注" else "关注 ${detail.post.author}"
                        }
                        .padding(
                            horizontal = tokens.spacing.chipHorizontalPadding,
                            vertical = tokens.spacing.chipVerticalPadding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
                ) {
                    if (!artist.isFollowed) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = tokens.color.onPrimary,
                            modifier = Modifier.size(tokens.spacing.platformGlyphSize),
                        )
                    }
                    Text(
                        if (artist.isFollowed) "已关注" else "关注",
                        fontSize = tokens.type.caption,
                        color = if (artist.isFollowed) tokens.color.textSecondary else tokens.color.onPrimary,
                    )
                }
            }
            if (artistMenu) {
                DropdownMenu(expanded = artistMenu, onDismissRequest = { artistMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("屏蔽该画师", fontSize = tokens.type.caption, color = tokens.color.error) },
                        onClick = {
                            artistMenu = false
                            artist.onBlock()
                        },
                    )
                }
            }
        }
        if (shown.isNotEmpty()) {
            // 节头 + 整片就地铺开（2026-10-18 第四批，用户给了官方那张稿）：
            // 从前是「四枚一列 × 两列」的硬切，标签长短不一时右列会被短标签拉出大洞，
            // 「更多」那枚还孤零零排在最后一列尾巴上，读起来像第四枚标签。
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.LocalOffer,
                    contentDescription = null,
                    tint = tokens.color.textSecondary,
                    modifier = Modifier.size(tokens.spacing.badgeIconSize),
                )
                Spacer(Modifier.width(tokens.spacing.space2))
                Text(
                    "作品标签",
                    fontSize = tokens.type.sectionTitle,
                    color = tokens.color.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (!tagsExpanded && hidden > 0) {
                    // 规格照那一档：把手是"一句话 + 一枚箭头"，箭头就是"点这里还有更多"的形状承诺；
                    // 被收起的**枚数**留给读屏念（屏上那排标签自己数得出来，再印一个数就是重复读数）。
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable(onClick = onExpandTags)
                            .semantics { contentDescription = "展开全部标签，另有 $hidden 枚未展开" }
                            .padding(
                                horizontal = tokens.spacing.chipHorizontalPadding,
                                vertical = tokens.spacing.chipVerticalPadding,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "查看全部",
                            fontSize = tokens.type.caption,
                            color = tokens.color.primary,
                        )
                        Icon(
                            Icons.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = tokens.color.primary,
                            modifier = Modifier.size(tokens.spacing.platformGlyphSize),
                        )
                    }
                }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            ) {
                // 同屏最多 8 枚；不足 8 枚时 hidden==0 ⇒ 节头右侧根本不会出现「查看全部」那枚把手
                shown.forEach { tag ->
                    // 三件事一起接（2026-10-20，用户「作品标签加入 pixiv 的多语言/翻译 tag 和
                    // 长按标签可以屏蔽 复制 搜索的选项」）：
                    // - 显示文本走**站方给的译名**（`detail.tagTranslations`，只改显示）；
                    // - 点 = 按**原名**去搜（译文会变，检索引擎只认站方那个词）；
                    // - 长按 = 搜索 / 复制 / 屏蔽三项（与画廊信息卡那面标签墙同一颗件、同一份措辞）。
                    //
                    // 为什么用共用那颗 `GalleryTagChip` 而不在本地画一枚：菜单项的顺序、屏蔽落的是
                    // 哪种规则、双显的括号形，这三件在信息卡那边已经定过；抄一份到这里就成了两处真相。
                    GalleryTagChip(
                        tag = tag,
                        text = tagDisplayLabel(tag, detail.tagTranslations),
                        onClick = onSearchTag?.let { search -> { search(tag) } },
                        onCopy = { copyTagText(chipContext, tag) },
                        onBlock = { tagScope.launch { blockTagText(chipContext, tag) } },
                    )
                }
            }
        }
        // 评论从前在这里单独占一行（「评论 N 条 · 在浏览器里看」），而动作行那枚已经同时念数
        // 并给同一条出口 ⇒ 同一件事在屏上念两遍，2026-10-18 第四批撤掉这一行。
        //
        // ── 2026-10-19 批 231-F：**真评论列表**（登录腿）────────────────────────
        // 匿名拿不到正文（四形状 404），所以这一节只在登录时才有内容；
        // 未登录那一档说的是"登录才看得到"，**不摆空排**。
        PixivCommentsSection(
            state = comments,
            imageLoader = imageLoader,
            expanded = commentsExpanded,
            onToggleExpanded = onToggleCommentsExpanded,
        )
        if (related.isNotEmpty()) {
            Text(
                "相关图片",
                fontSize = tokens.type.sectionTitle,
                color = tokens.color.textPrimary,
                modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space3),
            )
            // 三列**静态**墙：这一整段住在上面那条 LazyColumn 里，网套滚 = 整屏崩
            // （桌面侧那条不变式已被量过五次，这里不试第二遍）。
            related.chunked(3).forEach { line ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
                ) {
                    line.forEach { post ->
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(tokens.shape.card))
                                .background(tokens.color.surfaceVariant)
                                .clickable {
                                    GalleryViewerQueue.set(related)
                                    onOpenWork(post.id)
                                },
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(post.previewUrl).build(),
                                contentDescription = post.author,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(tokens.spacing.gridGap))
            }
        }
    }
}

/**
 * 长按菜单那两个**写动作**（复制 / 屏蔽），住在这一颗文件里是因为它们只服务作品页这一处。
 *
 * ## 复制的永远是**站方原名**
 *
 * 用户手上可能显示着「女の子 (女孩子)」，但复制要给他 `女の子` —— 那一串才是能拿去搜、
 * 能贴给别人的词。译文只改显示（口径与 `tagDisplayLabel` 的注释同一条）。
 *
 * ## 屏蔽落的是 **TAG 规则**
 *
 * 这个选择是**量出来的差异**，不是照抄：`PixivWorkInfoPanel` 那颗头注记着 ——
 * pixiv 的 `post.tagList` 就是这些**作品标签**，所以 TAG 规则加下去在墙上真生效；
 * 而画师名**不在** tagList 里（pixiv 那一格是作品标签），所以画师那一条存 AUTHOR。
 * 抄错成 KEYWORD 会把作者名一起误伤（同一个词出现在别人的标签里就挡人）。
 */
private fun copyTagText(context: Context, tag: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        ?: return
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("标签", tag))
    Toast.makeText(context, "已复制「$tag」", Toast.LENGTH_SHORT).show()
}

/**
 * 屏蔽一枚标签（suspend 落库，作用域由调用方给）。
 *
 * 为什么 scope 要**在详情段那一级**建、而不是每枚 chip 自己建：写库是 suspend 的，
 * 而菜单关闭那一刻这一枚 chip 完全可能已被移出组合（标签墙收起 / 横翻换作品）——
 * 跟着 chip 走的 scope 会在写库半路被取消，表现为"点了屏蔽、Toast 没出、规则也没落"。
 * 详情段这一级活得比任何一枚 chip 都久，而这一笔写的又是用户明确点下的存档。
 */
private suspend fun blockTagText(context: Context, tag: String) {
    val guard = com.venera.compose.gallery.data.GalleryPorts.of(context).contentGuard
    val exists = guard.rules.value.any { it.type == "TAG" && it.pattern.equals(tag, ignoreCase = true) }
    val message = when {
        exists -> "「$tag」已经在屏蔽列表里了"
        guard.addRule("TAG", tag) >= 0 -> "已屏蔽「$tag」，可在 设置 → 内容屏蔽管理 里改"
        else -> "屏蔽没成功，再试一次"
    }
    withContext(kotlinx.coroutines.Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

/** 动图那一档的明说 + 出口：不说"看不了"，也不摆一枚点了没反应的播放钮。 */
@Composable
private fun UgoiraNotice(onOpen: () -> Unit) {
    val tokens = VeneraTokens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space9)
            .padding(horizontal = tokens.spacing.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        Text(
            "这一作是动图，站方给的是逐帧压缩包，这一屏只画得出第一帧。",
            fontSize = tokens.type.body,
            color = tokens.color.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "去看图器放",
            fontSize = tokens.type.body,
            color = tokens.color.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onOpen)
                .padding(
                    horizontal = tokens.spacing.chipHorizontalPadding,
                    vertical = tokens.spacing.chipVerticalPadding,
                ),
        )
    }
}
/** 这一屏 的日志标签（返回程落点那一档要能从 logcat 分清「没落点」与「落点有但动画没接」）。 */
private const val TAG = "PixivWork"
