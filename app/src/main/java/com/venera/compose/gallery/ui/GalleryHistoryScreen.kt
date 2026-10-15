package com.venera.compose.gallery.ui

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import coil3.ImageLoader
import com.venera.compose.components.PredictiveBackOverlay
import com.venera.compose.components.VeneraEmptyTone
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.rememberImageWallColumnCount
import com.venera.compose.components.rememberPredictiveBackState
import com.venera.compose.components.selection.MultiSelectBarAction
import com.venera.compose.components.selection.VeneraMultiSelectBar
import com.venera.compose.components.selection.SelectableCardFrame
import com.venera.compose.components.selection.rememberMultiSelectState
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySaver
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.domain.GalleryBrowseRow
import com.venera.compose.gallery.domain.GalleryBrowseRows
import com.venera.compose.gallery.domain.GalleryDayGroup
import com.venera.compose.gallery.domain.GalleryGuard
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.compose.ui.tokens.LocalBottomBarClearance
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text

/**
 * 画廊的**浏览历史**页（2026-10-14 立，2026-10-15 加多选与三把手）。
 *
 * ## 为什么是独立路由
 *
 * 与「每日热门」二级页同一条理由：历史是**另一屏内容**，做成首页页内一层会把返回语义
 * 变成页内状态机（"按返回是退出历史还是退出画廊？"）。独立路由天然有返回栈与大标题折叠。
 *
 * ## 进场与返回的跟手
 *
 * 页面级预测返回**不在这一屏里做**：NavHost 已经挂了 `predictivePopEnter/Exit`
 * （`feature/Navigation.kt:700-701`，吃 `swipeEdge`，形状与 pop 同族），这一条路由自动继承。
 * 本屏自己管的是**页内两层**的跟手：多选态退出走 [rememberPredictiveBackState]，
 * 清空确认走 [PredictiveBackOverlay]（那颗自己就跟着手势进度淡出）。
 * 与 `components/PredictiveBack.kt:32` 那句口径一致：路由 pop 归 NavHost，页内浮层归这一屏。
 *
 * ## 多选三把手为什么要"先现取"
 *
 * 历史行只有 站 + 号 + 缩略图地址 + 时刻 + 比例，而「下载」与「共享」出去的都该是**原档**
 * （与大图页那两枚同一把手、同一结果）。所以选中之后按 `fetchById` 按站现取一条 `GalleryPost`
 * 再交出去 —— 代价如实写在下面那个汇总读数里：N 张选中就是 N 笔请求，取不到的逐档出声。
 *
 * ## 导出前过一遍守卫（2026-10-15 用户拍板）
 *
 * 墙上会被分级/屏蔽规则挡下的那几张，**不从这里导出**（判据就复用 `buildGalleryWall` 那一条，
 * 见 [galleryPostWouldBeMasked]）。屏上的缩略图仍过不了那把尺子（行里没存分级与标签，
 * 那条口子是本文件 2026-10-14 立时就写明并保留的），但**导出是另一回事**：
 * 从历史能整批把墙上看不见的图存进相册，就是给屏蔽规则开了后门。
 *
 * @param onOpenPost 打开大图页（`openGalleryPost(站, 号)` 按号现取，所以"从历史那一格回到原图"
 *   在移动端天然成立 —— 递回去的就是当初记的那对键）。
 * @param onBack 返回。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryHistoryScreen(
    onOpenPost: (GallerySite, Long) -> Unit,
    onBack: () -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val ports = remember { GalleryPorts.of(context) }
    /*
     * 契约上可空只是为了让桌面不必接这一颗（它有自己的历史屏与历史件）。这一屏住在移动端的
     * 导航壳里，`AndroidGalleryPorts` 恒装这一件 —— 读不到就是接线断了，当场抛。
     * 不走"空着当没历史"那条退化路：那面墙与"真的还没看过任何图"长得一模一样，
     * 用户会去翻两张图来"验证开关坏没坏"（假开关那一族）。
     */
    val history = remember { checkNotNull(ports.history) }
    val rows by history.rows.collectAsState()
    val imageLoader = remember { GalleryImageLoader.get(context) }

    // 导出前要复核的那两样：与墙上三面墙**同一把判据**（规则本体由 `guard.blockedGalleryRule`
    // 在点击那一刻现读，这里不提前抄一份 —— 抄了就是一份会过期的第二真相）。
    val guard = ports.contentGuard
    val maskMode by guard.nsfwMaskMode.collectAsState()
    val blockAi by ports.prefs.galleryBlockAi.collectAsState()

    var siteFilter by remember { mutableStateOf<GallerySite?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    val multi = rememberMultiSelectState<String>()
    /** 一批现取或落盘在途：这面旗只服务"按第二次要不要出声"，不当禁用态用（条上没有那档）。 */
    var exporting by remember { mutableStateOf(false) }

    /**
     * 删除时**冻住**的那一份名单快照，与"正在散"的那几格。
     *
     * 顺序是有意的：先落库真删（`removeAll`），再让屏上拿这一份快照把消散演完，演完才放开快照。
     * 反过来做（演完才删）会有两种假：演到一半用户退出这一屏 → 动画的回调没了 → 记录还在盘上，
     * 而屏上已经"删掉了"；或者删失败而粒子已经散完。现在这两件事不互相依赖：**数据先少，
     * 观感后跟**，中途退出最多看到"散到一半整块消失"那条退路（见 [GalleryDisintegration]）。
     */
    var frozenRows by remember { mutableStateOf<List<GalleryBrowseRow>?>(null) }
    var dissolving by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 多选态的跟手退出（浮层那一档归本屏，路由 pop 归 NavHost —— 见文件头注）。
    // 不接返回值：那颗件自己装了 back 回调，留着变量只会读成"还有个进度层没接上"。
    rememberPredictiveBackState(
        enabled = multi.active && !showClearDialog,
    ) { multi.exit() }

    /*
     * "现在"在进页那一刻钉一次：分组判据要求调用方注入（理由见 `GalleryBrowseRows.dayGroupOf` ——
     * 每帧现取会让跨午夜那一刷把一行从「昨天」跳回「今天」）。代价是停留期间不自动改组，
     * 而这一屏不是让人守到零点的地方。
     */
    val now = remember { System.currentTimeMillis() }

    /*
     * 坏档那句只说一次（`consumeNotice` 读走即清，与关注名单栏同一条口径）。
     * 屏上没有历史可摆时它就是空态的归因；有得摆时改走 Toast —— 一条说法不许出现两遍，
     * 也不许因为下面已经有图就整个吞掉（留档改名这件事说了才有地方找回）。
     */
    var brokenNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        history.consumeNotice()?.let { notice ->
            brokenNotice = notice
            if (history.rows.value.isNotEmpty()) {
                Toast.makeText(context, notice, Toast.LENGTH_LONG).show()
            }
        }
    }

    val live = remember(rows, siteFilter) { GalleryBrowseRows.ofSite(rows, siteFilter) }
    // 快照期间墙上摆那份**旧的**：名单已经在盘上少掉了，但那一格还要在原位把消散演完。
    // 跟着真名单重排的话，被删的那几格会先跳到别处再散 —— 那就是假动作。
    val visible = frozenRows ?: live
    val groups = remember(visible, now) { GalleryBrowseRows.groupedByDay(visible, now) }
    val order = remember(visible) { visible.map { it.uid } }

    // `VeneraTokens.motion` 是 @Composable getter，读它要留在组合里 ——
    // 挪进下面那颗 LaunchedEffect 里就编不过（那颗协程不是 composable 上下文）。
    val disintegrationMs = tokens.motion.disintegrationMs
    LaunchedEffect(dissolving) {
        if (dissolving.isEmpty()) return@LaunchedEffect
        // 多给 120ms：动画真正起跑比这里晚"一帧取景 + 采样建条"（条与粒子在那一刻才算得出），
        // 卡在正好 900ms 收尾会把最后两三帧**已经透明的一格**突然换成整块消失 ——
        // 而那恰好是观感上最像"没动画"的两帧。
        delay(disintegrationMs.toLong() + 120L)
        dissolving = emptySet()
        frozenRows = null
    }

    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val gridState = rememberLazyStaggeredGridState()
    val columnMode by ports.prefs.galleryColumnMode.collectAsState()
    // 与画廊那三面墙同一把列数尺子（同一张图在墙上与在历史里该占一样宽）。
    val columnCount = GallerySettingsModel.gridColumns(columnMode, rememberImageWallColumnCount())

    // 换站点筛子等于换一整批项：锚点留在原处会把新名单的中间一节对齐到视口顶，
    // 于是第一组 header 画进 contentPadding 里（首页顶栏重叠那次真机缺陷的同一根因）。
    LaunchedEffect(siteFilter) { gridState.scrollToItem(0) }

    /** 多选态下点卡 = 勾选；非多选态 = 开大图页。两条路都从这一枚把手走。 */
    fun tapTile(row: GalleryBrowseRow) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (multi.active) {
            multi.toggle(row.uid)
        } else {
            onOpenPost(row.site, row.postId)
        }
    }

    /** 长按 = 进多选并选中这一张；已在多选态 = 区间反选（与三面墙同一条语义）。 */
    fun longPressTile(row: GalleryBrowseRow) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (multi.active) multi.toggleRange(row.uid, order) else multi.enter(row.uid)
    }

    fun startDelete() {
        val doomed = visible.filter { multi.contains(it.uid) }
        if (doomed.isEmpty()) return
        multi.exit()
        frozenRows = visible
        dissolving = doomed.map { it.uid }.toSet()
        scope.launch { history.removeAll(doomed.map { it.uid }) }
    }

    /**
     * 把选中的行现取成 `GalleryPost` 并按守卫分档，然后交付给 [onReady]。
     *
     * 三档失败各数各的（取不到 / 站方没这条 / 被规则挡下），汇总那一句由
     * [GalleryExportTally.readout] 出 —— 屏上的话住在纯函数里，改句子要有单测跟着改。
     */
    fun export(asDownload: Boolean) {
        if (exporting) {
            Toast.makeText(context, "上一批还在取 🐢 等一下再按", Toast.LENGTH_SHORT).show()
            return
        }
        val picked = visible.filter { multi.contains(it.uid) }
        if (picked.isEmpty()) return
        exporting = true
        scope.launch {
            val usable = ArrayList<GalleryPost>(picked.size)
            var fetchFailed = 0
            var notOnSite = 0
            var blocked = 0
            for (row in picked) {
                // 三站各自一个客户端，按站分支就够了（与大图页那条同一形状，
                // 不为这一处分支去抽"通用图库站接口"—— 那会把只有一站会用的参数拖进抽象层）。
                val fetched = when (row.site) {
                    GallerySite.YANDERE -> ports.boards.yandere.fetchById(row.postId)
                    GallerySite.GELBOORU -> ports.boards.gelbooru.fetchById(row.postId)
                    GallerySite.SAFEBOORU -> ports.boards.safebooru.fetchById(row.postId)
                }
                val post = fetched.getOrNull()
                when {
                    fetched.isFailure -> fetchFailed++
                    post == null -> notOnSite++
                    galleryPostWouldBeMasked(post, maskMode, blockAi, guard::blockedGalleryRule) -> blocked++
                    else -> usable.add(post)
                }
            }
            var saved = 0
            var saveFailed = 0
            if (asDownload) {
                for (post in usable) {
                    if (GallerySaver.save(context, post).isSuccess) saved++ else saveFailed++
                }
            } else if (usable.isNotEmpty()) {
                shareGalleryPosts(context, usable)
                saved = usable.size
            }
            Toast.makeText(
                context,
                GalleryExportTally(
                    handed = saved,
                    fetchFailed = fetchFailed,
                    notOnSite = notOnSite,
                    blocked = blocked,
                    saveFailed = saveFailed,
                    asDownload = asDownload,
                ).readout(),
                Toast.LENGTH_LONG,
            ).show()
            exporting = false
            multi.exit()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (visible.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding(statusBarTop)),
                contentAlignment = Alignment.Center,
            ) {
                GalleryHistoryEmpty(
                    brokenNotice = brokenNotice,
                    filtered = rows.isNotEmpty(),
                    siteFilter = siteFilter,
                    onShowAll = { siteFilter = null },
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                state = gridState,
                columns = StaggeredGridCells.Fixed(columnCount),
                contentPadding = PaddingValues(
                    start = tokens.spacing.screenHorizontal,
                    end = tokens.spacing.screenHorizontal,
                    top = topPadding(statusBarTop),
                    // 多选条**浮**在底边，这一屏不再自行叠加它的高度 ——
                    // 口径写在 `ui/tokens/Spacing.kt:214-215`（Screen 只消费 LocalBottomBarClearance），
                    // 与同一把条的另一个宿主 `GalleryFavoritesBody` 逐字同形。
                    bottom = LocalBottomBarClearance.current,
                ),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space5),
                verticalItemSpacing = tokens.spacing.gridGap,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topBarBehavior.nestedScrollConnection)
                    .blurBackdropSource(topBarBackdrop),
            ) {
                // 胶囊行是**恒在的第一个项**：有没有货、筛到哪一站都不改它的存在与否，
                // 只改里面哪一枚亮着（懒列表在第 0 项之后增删会把视口锚点带走，见上面那条）。
                item(key = "history-filter", span = StaggeredGridItemSpan.FullLine) {
                    GalleryHistorySiteFilter(
                        selected = siteFilter,
                        onPick = { siteFilter = it },
                    )
                }
                groups.forEach { (group, rowsOfGroup) ->
                    item(key = "history-group-${group.name}", span = StaggeredGridItemSpan.FullLine) {
                        GalleryHistoryGroupHeader(group)
                    }
                    items(rowsOfGroup, key = { it.uid }) { row ->
                        val dissolvingThis = row.uid in dissolving
                        val tile: @Composable () -> Unit = {
                            GalleryHistoryTile(
                                row = row,
                                imageLoader = imageLoader,
                                selecting = multi.active,
                                selected = multi.contains(row.uid),
                                onClick = { tapTile(row) },
                                onLongPress = { longPressTile(row) },
                                onCoverBounds = { bounds ->
                                    // 大图页返回程的落点，与墙上那张卡同一把口径。
                                    GalleryFlyIn.noteCardBounds(row.uid, bounds)
                                },
                            )
                        }
                        if (dissolvingThis) {
                            val disState = rememberGalleryDisintegrationState(
                                tokens.motion.disintegrationMs,
                            )
                            /*
                             * 包上去之后**必须自己按下**：`GalleryDisintegration` 只管"按了之后怎么画"，
                             * `triggered` 恒 false 时它就是一层原样转发的 graphicsLayer ——
                             * 2026-10-15 真机读数「删除一次动画都没有」就是这一句漏了：
                             * 瓦片原样画满 900ms，然后被定时器整块摘掉。
                             */
                            LaunchedEffect(disState) { disState.disintegrate() }
                            GalleryDisintegration(state = disState) { tile() }
                        } else {
                            tile()
                        }
                    }
                }
            }
        }

        if (multi.active) {
            VeneraMultiSelectBar(
                selectedCount = multi.count,
                allSelected = order.isNotEmpty() && multi.count == order.size,
                onExit = { multi.exit() },
                onSelectAll = { multi.selectAll(order) },
                onInvert = { multi.invert(order) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = tokens.spacing.screenHorizontal,
                        end = tokens.spacing.screenHorizontal,
                        bottom = tokens.spacing.space8 + LocalBottomBarClearance.current,
                    ),
            ) {
                MultiSelectBarAction(
                    icon = Icons.Outlined.Download,
                    label = "下载",
                    onClick = { export(asDownload = true) },
                )
                MultiSelectBarAction(
                    icon = Icons.Outlined.Share,
                    label = "共享",
                    onClick = { export(asDownload = false) },
                )
                MultiSelectBarAction(
                    icon = Icons.Outlined.Delete,
                    label = "删除",
                    destructive = true,
                    onClick = { startDelete() },
                )
            }
        }

        VeneraTopAppBar(
            title = "浏览历史",
            largeTitle = "浏览历史",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                VeneraTopBarPill(onClick = { if (multi.active) multi.exit() else onBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        // 多选态下这一枚的动作是退出多选，读数要说清它当下干的那件事。
                        contentDescription = if (multi.active) "退出多选" else "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            actions = {
                // 空名单上不摆这枚：按下去没有任何事可做的钮就是假开关。多选态也不摆
                // （那一会儿屏上同时有两套"选中一批之后干什么"的把手，用户分不出哪个是清全部）。
                if (rows.isNotEmpty() && !multi.active) {
                    VeneraTopBarPill(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        showClearDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "清空浏览历史",
                            tint = tokens.color.textSecondary,
                        )
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (showClearDialog) {
        ClearGalleryHistoryDialog(
            onDismiss = { showClearDialog = false },
            onClearAll = {
                scope.launch { history.clear() }
                showClearDialog = false
            },
        )
    }
}

/** 顶栏避让地板：这一屏没有常驻 chrome（搜索条在画廊首页那一屏），所以只有地板本身。 */
private fun topPadding(statusBarTop: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    statusBarTop + VeneraSpacing.topBarFloor

/** 站点筛子那一行。标签取枚举自带的 [GallerySite.displayName]，不另造词。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryHistorySiteFilter(
    selected: GallerySite?,
    onPick: (GallerySite?) -> Unit,
) {
    val tokens = VeneraTokens
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = tokens.spacing.space3),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
    ) {
        // 四枚在 360dp 窄屏上会超一行，用 Row 会直接裁掉最右那枚（漫画详情页那排同判据）。
        VeneraChip(
            text = "全部",
            selected = selected == null,
            variant = VeneraChipVariant.Assist,
            onClick = { onPick(null) },
        )
        GallerySite.entries.forEach { site ->
            VeneraChip(
                text = site.displayName,
                selected = selected == site,
                variant = VeneraChipVariant.Assist,
                onClick = { onPick(site) },
            )
        }
    }
}

/**
 * 分组头的日期词。放在这里而不是 domain 那颗枚举上：屏上文案住在屏这边，
 * 而 domain 只有判据 —— 这颗是纯函数，单测直接点它，不用去 grep 源码。
 */
internal fun galleryDayGroupLabel(group: GalleryDayGroup): String = when (group) {
    GalleryDayGroup.TODAY -> "今天"
    GalleryDayGroup.YESTERDAY -> "昨天"
    GalleryDayGroup.OLDER -> "更早"
}

@Composable
private fun GalleryHistoryGroupHeader(group: GalleryDayGroup) {
    val tokens = VeneraTokens
    Text(
        text = galleryDayGroupLabel(group),
        fontSize = tokens.type.sectionTitle,
        fontWeight = tokens.type.weightSemibold,
        color = tokens.color.textPrimary,
        modifier = Modifier.padding(bottom = tokens.spacing.space3),
    )
}

/**
 * 一格 = 看过的那张图的缩略图。比例**照墙上那一面**（错落，不等高）。
 *
 * 比例是当初记下行时从 `GalleryPost.cardRatio` 抄的一份；取不到（0）时与墙上同一条退路 ——
 * 退占位比例再夹进 0.4~2.5，判据在 [GalleryBrowseRows.tileRatio]。
 *
 * 反馈用 material3 的 `ripple()` 而**不是**这一屏本来的 miuix 按压洗底：
 * `MiuixIndication` 画的是整层 alpha 洗底（`_qa/mxsrc/.../MiuixIndication.kt` 现读），
 * 按下去没有从触点扩开的那一圈。长按要的就是那一圈，所以这一处显式递 `indication`。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryHistoryTile(
    row: GalleryBrowseRow,
    imageLoader: ImageLoader,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onCoverBounds: (Rect) -> Unit,
) {
    val tokens = VeneraTokens
    val interactionSource = remember { MutableInteractionSource() }
    var coverBounds by remember { mutableStateOf(Rect.Zero) }
    // 瓦片被懒网格回收（滚出屏幕）时撤掉自己那条返回落点：留着过期矩形比"这一次不飞"糟得多。
    DisposableEffect(row.uid) {
        onDispose { GalleryFlyIn.forgetCardBounds(row.uid) }
    }
    SelectableCardFrame(
        selecting = selecting,
        selected = selected,
        onToggleSelect = onClick,
        // 洗底要跟着这一格的圆角裁：默认档是 `shape.card`，而这一格裁的是 `shape.medium`，
        // 不显式递就会在四个角外头染出一小片主色（那颗件的头注专门写着这件事）。
        cornerRadius = tokens.shape.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(GalleryBrowseRows.tileRatio(row.ratio, tokens.spacing.coverAspectRatio))
                .clip(RoundedCornerShape(tokens.shape.medium))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClick = onLongPress,
                ),
        ) {
            VeneraCover(
                url = row.thumbUrl,
                contentDescription = "${row.site.displayName} #${row.postId}",
                // 比例由外面的槽位定死，封面不再自持比例，否则两侧留缝（轮播那条同因）。
                preserveAspectRatio = false,
                imageLoader = imageLoader,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        val rect = it.boundsInWindow()
                        coverBounds = rect
                        onCoverBounds(rect)
                    },
            )
        }
    }
}

@Composable
private fun GalleryHistoryEmpty(
    brokenNotice: String?,
    filtered: Boolean,
    siteFilter: GallerySite?,
    onShowAll: () -> Unit,
) {
    when {
        brokenNotice != null -> VeneraEmptyView(
            title = "浏览历史读不出来",
            message = brokenNotice,
            tone = VeneraEmptyTone.Failed,
        )

        // 筛子点空了：这不是"没历史"，是"这一站没有"。给一枚真能点回去的出口，
        // 别让人以为记录丢了（归因说反的代价见日榜那一面墙的同一段说明）。
        // 话里指的那枚就是下面 actionText 那颗 —— 这一档上胶囊行已经不摆（没有可筛的项），
        // 所以说"点下面那枚"，不能说"换回「全部」"（那一枚此刻不在屏上）。
        filtered -> VeneraEmptyView(
            title = "这一站没有记录",
            message = "${siteFilter?.displayName ?: ""} 在这一份历史里一张也没有。" +
                "点下面那枚「看全部」就能看到其它站的。",
            icon = Icons.Outlined.History,
            actionText = "看全部",
            onAction = onShowAll,
        )

        else -> VeneraEmptyView(
            title = "还没有浏览记录",
            message = "在画廊里点开一张图，这里就记一次。反复看同一张只挪时间，不会多占一行。",
            icon = Icons.Outlined.History,
        )
    }
}

/** 清空确认。形状逐条照漫画历史页那颗 `ClearHistoryMenu`（同一枚浮层、同一套按钮档）。 */
@Composable
private fun ClearGalleryHistoryDialog(
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
) {
    val tokens = VeneraTokens
    PredictiveBackOverlay(onDismiss = onDismiss) {
        Card(modifier = Modifier.padding(horizontal = tokens.spacing.space9)) {
            Column(modifier = Modifier.padding(tokens.spacing.space8)) {
                Text(
                    text = "清空浏览历史",
                    fontSize = tokens.type.itemTitle,
                    fontWeight = tokens.type.weightSemibold,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                Text(
                    text = "只清这一份看图记录。收藏、关注的画师、漫画侧的阅读进度都不受影响，" +
                        "清空之后新看的一张会重新记起来。",
                    fontSize = tokens.type.caption,
                    color = VeneraTokens.color.textSecondary,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space7))
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space5)) {
                    Button(
                        onClick = onClearAll,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(color = tokens.color.errorContainer),
                    ) { Text("全部清空", color = tokens.color.onErrorContainer) }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = tokens.color.surfaceVariant.copy(
                                alpha = tokens.current.placeholderAlpha,
                            ),
                        ),
                    ) { Text("取消", color = tokens.color.textPrimary) }
                }
            }
        }
    }
}

/**
 * 这一条帖子在墙上会不会被挡下。**复用墙上的那一条判据**而不是另写一把：
 * `buildGalleryWall` 用的就是「用户规则命中 或 AI 标记（开了屏蔽时）」送进 `maskStateFor`，
 * 只要不是 VISIBLE 就得上遮罩或收起。这里要问的是同一个问题。
 *
 * `tags` 是**词表**（`GalleryPost.tagList`，切分口径全仓只有一处），不是站方那串原文 ——
 * 屏蔽规则按整词匹配，递字符串会让长标签命中而短标签整站漏判。
 */
private fun galleryPostWouldBeMasked(
    post: GalleryPost,
    maskMode: String,
    blockAi: Boolean,
    blockedRuleOf: (author: String, tags: List<String>) -> Any?,
): Boolean {
    val blockedByUser = blockedRuleOf(post.author, post.tagList) != null ||
        (blockAi && GalleryAi.isAiMarked(post.tagList))
    return GalleryGuard.maskStateFor(post, maskMode, blockedByUser = blockedByUser) != GalleryGuard.VISIBLE
}

/**
 * 一次多选导出结束时上屏的那一句读数的**料**。
 *
 * 分五档是有理由的：「取不到」是网络/凭据，「站方没有这条」是本子已被删或合并，
 * 「被规则挡下」是用户自己的屏蔽设置，「存失败」是存储权限或空间 ——
 * 合成一句"部分没成功"就读不出该动哪一手（这四类的修法完全不同）。
 */
internal data class GalleryExportTally(
    val handed: Int,
    val fetchFailed: Int,
    val notOnSite: Int,
    val blocked: Int,
    val saveFailed: Int,
    val asDownload: Boolean,
) {

    /** 屏上那一句。零数的那一档不写进句子（"0 张被挡下"是噪声，不是读数）。 */
    fun readout(): String = buildString {
        append(if (asDownload) "存好 $handed 张" else "带上 $handed 张")
        val misses = fetchFailed + notOnSite + blocked + saveFailed
        if (misses > 0) {
            append(" · 没带上的 $misses 张：")
            val parts = mutableListOf<String>()
            if (fetchFailed > 0) parts.add("$fetchFailed 张没取回来")
            if (notOnSite > 0) parts.add("$notOnSite 张站方说没这条")
            if (blocked > 0) parts.add("$blocked 张被你的屏蔽规则挡下")
            if (saveFailed > 0) parts.add("$saveFailed 张没存进去")
            append(parts.joinToString("、"))
        }
    }
}
