/**
 * 收藏页（对齐原版 `pages/favorites/side_bar.dart` + `local_favorites_page.dart`）。
 *
 * 原版是「宽屏双栏（侧栏 + 内容）/ 窄屏抽屉」；本项目是手机单栏，
 * 因此把收藏夹选择做成顶部横向 chips（行为等价：切换当前收藏夹 + 显示计数），
 * 文件夹动作收纳进右上角菜单。
 */
package com.venera.compose.feature

import android.app.Activity
import com.venera.compose.feature.favoriteimages.FavoriteImageItem
import com.venera.compose.gallery.ui.GalleryFavoritesBody
import com.venera.compose.openGalleryPost
import com.venera.compose.feature.favoriteimages.toComicItem
import com.venera.compose.ui.tokens.LocalBottomBarClearance
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens

import com.venera.compose.components.*
import com.venera.compose.components.selection.MultiSelectBarAction
import com.venera.compose.components.selection.MultiSelectState
import com.venera.compose.components.selection.SelectableCardFrame
import com.venera.compose.components.selection.VeneraMultiSelectBar
import com.venera.compose.components.selection.rememberMultiSelectState
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraFilterPill
import com.venera.compose.components.venera.VeneraSegmentedButton
import com.venera.compose.components.venera.VeneraTagChip
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.components.venera.rememberTopBarBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.ListView

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.components.venera.VeneraTopBarPill

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AndroidFavoritesScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelect: (ComicItem) -> Unit,
    /** 点插图卡 → 预览页（那张图与预览页之间是一条共享元素飞行）。 */
    onPreviewFavoriteImage: (FavoriteImageItem) -> Unit,
    /** 图片收藏长按「从该页开始阅读」：宿主负责推详情页并带上定位请求。 */
    onReadFavoriteImage: (FavoriteImageItem) -> Unit,
) {
    val tokens = VeneraTokens
    val vm: FavoritesViewModel = viewModel()
    val displayMode = rememberComicListDisplayMode()
    val folders by vm.folders.collectAsState()
    val counts by vm.counts.collectAsState()
    /**
     * 多选状态机。四处收藏面板**共用同一份实现**（见 `components/selection/MultiSelectState`）：
     * 长按 = 进多选并选中、多选态内长按 = 区间反选、选中归零 = 自动退出。
     *
     * 键取 `(id, type)` —— 与官方 `id + type` 判等一致：同一个 id 在两张源里是两本不同的书，
     * 只用 id 会让跨源的同号作品互相选中。
     */
    val selection = rememberMultiSelectState<Pair<String, Int>>()
    // 当前选中的条目（按当前列表顺序取）。移动到 / 复制到 / 删除三处**同一份取法** ——
    // 各自拿 `vm.comics.filter{...}` 抄一遍，改动漏一处就会出现"删掉的和我勾的不是同一批"。
    fun selectedComics(): List<com.venera.compose.data.db.FavoriteItem> =
        vm.comics.filter { (it.id to it.type) in selection.selected }

    var showMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    var searchMode by remember { mutableStateOf(false) }
    // 网络收藏置于首位并作为默认入口。
    // **必须 rememberSaveable**：从详情页返回时导航条目是重建的，普通 remember 会把
    // 这里打回默认值 —— 真机实测「从本地收藏点进详情，返回却落在网络收藏」。
    // 它同时是封面共享元素返回时闪一下的根因之一：目的地那一支整支没被组合出来。
    var mode by rememberSaveable { mutableStateOf(FavoritesMode.Network) }
    // 「图片收藏」那一档内部的两块：**漫画**（阅读器里收入的单页插图）/ **画廊**（图站收藏）。
    // 放在这里而不是顶栏里：顶栏那三枚是"页面级目的地"，这两枚是同一块面板内部的视图切换，
    // 层级不同就该在不同的位置上（见 ImageFavoritesPanel）。
    var imageSection by rememberSaveable { mutableStateOf(ImageSection.Comics) }
    // 三块面板现在是一个 pager 的三页：本页的横滑只切「网络 / 图片 / 本地」这三段
    // （09-29 起主 tab 那条内容横滑已经整体撤掉，见 _trash/tab-swipe-pager-2026-09-29/）。
    // 跟手位移、过阈值提交、不到阈值弹簧回弹，全是 pager 自带的行为。
    val pagerState = rememberPagerState(
        initialPage = favoritesModesNetworkFirst.indexOf(mode),
    ) { favoritesModesNetworkFirst.size }
    // 滑动 → mode：只在**停稳**之后落。拖到一半就改 mode 会让顶栏 actions、
    // 内容避让高度、分段小药丸三处同时跟着跳，看着像整页在抖。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { mode = favoritesModesNetworkFirst[it] }
    }
    // mode → 滑动：点分段器走与手滑同一条欠阻尼弹簧，两条路径观感一致。
    LaunchedEffect(mode) {
        val index = favoritesModesNetworkFirst.indexOf(mode)
        if (pagerState.settledPage != index) {
            pagerState.animateScrollToPage(
                page = index,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    val selectionBack = com.venera.compose.components.rememberPredictiveBackState(
        enabled = mode == FavoritesMode.Local && selection.active && !showMenu && dialog == null,
    ) { selection.exit() }
    // 大标题折叠 + 毛玻璃顶栏（页内自治）
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 分段器所在行（FavoritesModeToggle：上下 space3 + 分段高度，宽屏档换 56dp）的真实高度，
    // 由 token 推导而非写死，顶栏加高时内容避让自动跟随。
    val segmentedRowHeight =
        (if (isWideScreen(LocalConfiguration.current.screenWidthDp.dp)) {
            tokens.spacing.segmentedHeightWide
        } else {
            tokens.spacing.segmentedHeight
        }) + tokens.spacing.space3 * 2
    val topPadding = statusBarTop + 104.dp + segmentedRowHeight + if (mode == FavoritesMode.Local && selection.active) 76.dp else 0.dp

    // 顶栏折叠判定：沿用 miuix TopAppBar 自己的阈值（collapsedFraction * 3 >= 1，
    // 即 smallTitle 出现的同一时刻），保证「分段切换器收起」与「小标题淡入」严格同步。
    val topBarCollapsed by remember(topBarBehavior) {
        derivedStateOf { topBarBehavior.state.collapsedFraction * 3f >= 1f }
    }
    /**
     * 「图片收藏」那一屏的顶栏比另外两屏**多一条常驻分段器**（漫画收藏 / 画廊收藏）。
     * 它挂在 `bottomContent` 里且**不跟折叠收起**（收掉就没法切另一面墙），所以这一屏的让位
     * 要在通用地板上再加一行分段器的高度。
     *
     * 让位是**常量**、并且交给网格的 `contentPadding`（不是把整面墙往下顶）：折叠时顶栏从
     * 682px 收到 426px，而墙的视口始终满屏，卡片只跟着手指走 —— 与另外两屏同一套契约。
     * 真机实测（2026-09-27，PJZ110）：上一版改用 `onSizeChanged` 逐帧量顶栏真实高度，
     * 空带确实归零（分段器起点 524px / 268px 与顶栏底边分毫不差），但换来两个新问题：
     * 玻璃在顶栏底边留一条横贯全屏的硬边，分段器孤零零坐在硬边之外的裸背景上；
     * 且视口高度跟着顶栏变，折叠那 98px 行程里内容额外多走 256px。归进 chrome 一起解决。
     */
    val imagesTopPadding = topPadding + segmentedRowHeight
    // 当前面板的列表滚动状态（由子面板上抛）：是否已下滑、以及回到顶部的动作。
    // 用回调而非持有 ListState，避免 LazyListState / LazyGridState 两种类型互相污染。
    // **按页各存一份**：三块面板现在同时活在 pager 里，共用一格会被相邻页的
    // 「未滚动」覆盖掉，顶置按钮就在切页后消失。
    val panelScrollSlots = remember {
        List(favoritesModesNetworkFirst.size) {
            mutableStateOf<Pair<Boolean, (() -> Unit)?>?>(null)
        }
    }
    val settledSlot = panelScrollSlots[pagerState.currentPage].value
    val favHasScrolled = settledSlot?.first ?: false
    val favScrollToTop = settledSlot?.second

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 回弹：库默认用无阻尼弹簧收尾（到位就停）。这里换成欠阻尼，松手过阈值后
            // 会冲过头再收回来一点 —— 用户要的"回弹滑动"就是这个过冲。
            // 与分段小药丸那条 0.7 同族、只更松一档，不是一弹三跳的高弹。
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
            // 页外的面板不参与毛玻璃采样：三块同时往同一个 LayerBackdrop 里录制，
            // 顶栏模糊到底糊谁就成了未定义行为。只喂当前这一页。
            pageContent = { page ->
                val backdrop = if (pagerState.currentPage == page) topBarBackdrop else null
                val reportScroll: (Boolean, Boolean, () -> Unit) -> Unit = { _, hasScrolled, scrollToTop ->
                    panelScrollSlots[page].value = hasScrolled to scrollToTop
                }
                when (favoritesModesNetworkFirst[page]) {
                    FavoritesMode.Local -> FavoriteGrid(
                        vm = vm,
                        folders = folders,
                        counts = counts,
                        searchMode = searchMode,
                        topPadding = topPadding,
                        onSelect = onSelect,
                        selection = selection,
                        scrollConnection = topBarBehavior.nestedScrollConnection,
                        backdrop = backdrop,
                        onScrollStateChange = reportScroll,
                    )

                    FavoritesMode.Images -> ImageFavoritesPanel(
                        topPadding = imagesTopPadding,
                        section = imageSection,
                        scrollConnection = topBarBehavior.nestedScrollConnection,
                        backdrop = backdrop,
                        onPreviewImage = onPreviewFavoriteImage,
                        onOpenComicDetail = { item -> onSelect(item.toComicItem()) },
                        onReadFromPage = onReadFavoriteImage,
                        onScrollStateChange = reportScroll,
                    )

                    FavoritesMode.Network -> AndroidNetworkFavoritesScreen(
                        onSelect = onSelect,
                        scrollConnection = topBarBehavior.nestedScrollConnection,
                        backdrop = backdrop,
                        topPadding = topPadding,
                        showLayoutToggle = false,
                        onScrollStateChange = reportScroll,
                    )
                }
            },
        )

        // ── 统一顶栏：置于前景层，毛玻璃对底层内容进行物理级模糊 ──
        VeneraTopAppBar(
            title = "收藏",
            largeTitle = "收藏",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            actions = {
                    if (mode == FavoritesMode.Local) {
                        FavoritesSortMenu(
                            sortOrder = vm.sortOrder,
                            onSortOrderChange = { vm.updateSortOrder(it) },
                        )
                        VeneraTopBarPill(onClick = { searchMode = !searchMode }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "搜索收藏",
                                tint = tokens.color.textSecondary,
                            )
                        }
                        VeneraTopBarPill(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "收藏夹操作",
                                tint = tokens.color.textSecondary,
                            )
                        }
                    }
                // 布局切换钮收进顶栏 actions：跟随大标题折叠与毛玻璃、常驻右上角
                // （用户拍板「跟随顶栏在右上角」，不再悬浮于折叠顶栏下缘）。
                // 图片收藏段是固定两列瀑布流，单双列切换对它无意义 —— 不摆假开关。
                if (mode != FavoritesMode.Images) {
                    ComicLayoutToggleButton(
                        displayMode = displayMode.value,
                        onToggle = { displayMode.value = it },
                    )
                }
            },
            bottomContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 分段切换器只在顶置（未下滑）时出现：下滑折叠后顶栏不再同时挂着
                    // 「网络收藏 / 本地收藏」两个按钮，让折叠态顶栏保持干净。
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !topBarCollapsed,
                        enter = androidx.compose.animation.fadeIn() +
                            androidx.compose.animation.expandVertically(),
                        exit = androidx.compose.animation.fadeOut() +
                            androidx.compose.animation.shrinkVertically(),
                    ) {
                        FavoritesModeToggle(
                            mode = mode,
                            onModeChange = { mode = it },
                        )
                    }
                    // 「漫画收藏 / 画廊收藏」这一条**常驻**，而且必须挂在 chrome 里。
                    // 它是这一屏的控制器，顶栏折叠后仍要能切墙，所以不能跟上面那条一起收起；
                    // 而它一旦被钉在顶栏**外面**，顶栏一收就必然出状况 —— 真机实测过两种：
                    // 让位写常量 → 玻璃底边与药丸之间多一条空带；让位改成逐帧量顶栏高度 →
                    // 空带归零，但玻璃在底边留一条横贯全屏的硬边，药丸孤零零坐在硬边之外的裸背景上。
                    // 挂进 bottomContent 之后它就是顶栏的一部分：跟着走、被同一块玻璃罩住，
                    // 两种毛病同时不可能发生，也不需要任何测量。
                    //
                    // 门控用 `mode`（横滑**停稳**才落）而不是 `currentPage`（过半就翻）：
                    // 后者会在拖动中途把这一条抽掉，顶栏当场跳一下。
                    // 外面照上面那条同款 AnimatedVisibility 包一层，切页时它是收/长出来的，不是消失的。
                    androidx.compose.animation.AnimatedVisibility(
                        visible = mode == FavoritesMode.Images,
                        enter = androidx.compose.animation.fadeIn() +
                            androidx.compose.animation.expandVertically(),
                        exit = androidx.compose.animation.fadeOut() +
                            androidx.compose.animation.shrinkVertically(),
                    ) {
                        ImageSectionToggle(
                            section = imageSection,
                            onSectionChange = { imageSection = it },
                        )
                    }
                    if (mode == FavoritesMode.Local && selection.active) {
                        // 选中的条目由**当前列表**过滤出来交给 ViewModel：ViewModel 不再持有
                        // 多选集合（那是 UI 的事），它只负责"对这批条目做动作"。
                        val allKeys = vm.comics.map { it.id to it.type }
                        VeneraMultiSelectBar(
                            selectedCount = selection.count,
                            allSelected = allKeys.isNotEmpty() && selection.count == allKeys.size,
                            onExit = { selection.exit() },
                            onSelectAll = { selection.selectAll(allKeys) },
                            onInvert = { selection.invert(allKeys) },
                            modifier = Modifier
                                .graphicsLayer { alpha = 1f - selectionBack.progress }
                                .padding(
                                    horizontal = tokens.spacing.rowHorizontal,
                                    vertical = tokens.spacing.space2,
                                ),
                        ) {
                            MultiSelectBarAction(
                                icon = Icons.Outlined.DriveFileMove,
                                label = "移动到",
                                onClick = { dialog = FolderDialog.Move },
                            )
                            MultiSelectBarAction(
                                icon = Icons.Outlined.ContentCopy,
                                label = "复制到",
                                onClick = { dialog = FolderDialog.Copy },
                            )
                            MultiSelectBarAction(
                                icon = Icons.Filled.Delete,
                                label = "删除",
                                destructive = true,
                                onClick = {
                                    vm.deleteItems(selectedComics())
                                    selection.exit()
                                },
                            )
                        }
                    }
                }
            },
        )

        // 右下角「顶置」按钮：下滑一段距离后淡入，点击回到列表顶部。
        // 底部让位读契约真源（LocalBottomBarClearance）再加额外留白，不手写 magic number。
        // 直读 VeneraSpacing 那个常量会在侧栏档浮空 —— 那档底栏不渲染、留白应归 0。
        val favBackToTopBottom = LocalBottomBarClearance.current + VeneraSpacing.space9
        androidx.compose.animation.AnimatedVisibility(
            visible = favHasScrolled,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = tokens.spacing.space9,
                    bottom = favBackToTopBottom,
                ),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(tokens.shape.large),
                color = tokens.color.primaryContainer,
                modifier = Modifier.clickable {
                    favScrollToTop?.invoke()
                },
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = tokens.spacing.chipHorizontalPadding,
                        vertical = tokens.spacing.chipVerticalPadding,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "回到顶部",
                        tint = tokens.color.onPrimaryContainer,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                    Text(
                        text = "顶部",
                        fontSize = tokens.type.caption,
                        color = tokens.color.onPrimaryContainer,
                    )
                }
            }
        }
    }

    if (showMenu) {
        FolderMenuSheet(
            folderName = vm.currentFolder,
            canEditFolder = vm.currentFolder != LOCAL_ALL_FOLDER,
            onDismiss = { showMenu = false },
            onCreate = { dialog = FolderDialog.Create },
            onRename = { dialog = FolderDialog.Rename },
            onDelete = { vm.deleteFolder(vm.currentFolder) },
            onReorder = { dialog = FolderDialog.Reorder },
            onExport = { dialog = FolderDialog.Export },
        )
    }

    when (dialog) {
        FolderDialog.Create -> InputDialog(
            title = "新建收藏夹",
            hint = "收藏夹名称",
            confirmText = "创建",
            onDismiss = { dialog = null },
            onConfirm = { vm.createFolder(it) { msg -> /* TODO toast */ } },
        )

        FolderDialog.Rename -> InputDialog(
            title = "重命名收藏夹",
            hint = "新的名称",
            initialValue = vm.currentFolder,
            confirmText = "确定",
            onDismiss = { dialog = null },
            onConfirm = { vm.renameFolder(vm.currentFolder, it) },
        )

        FolderDialog.Move -> FolderPickerDialog(
            title = "移动到",
            folders = folders.filter { it != vm.currentFolder },
            onDismiss = { dialog = null },
            onPick = {
                vm.moveItemsTo(selectedComics(), it)
                selection.exit()
            },
        )

        FolderDialog.Copy -> FolderPickerDialog(
            title = "复制到",
            folders = folders.filter { it != vm.currentFolder },
            onDismiss = { dialog = null },
            onPick = {
                vm.copyItemsTo(selectedComics(), it)
                selection.exit()
            },
        )

        FolderDialog.Reorder -> ReorderDialog(
            folders = folders,
            onDismiss = { dialog = null },
            onConfirm = { vm.saveFolderOrder(it) },
        )

        FolderDialog.Export -> ExportDialog(
            folder = vm.currentFolder,
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

/** 收藏页模式：网络收藏（各漫画源账号）/ 图片收藏（单页插图）/ 本地收藏（本地数据库）。 */
private enum class FavoritesMode { Local, Network, Images }

/**
 * 「图片收藏」这一档内部的**两块**（用户 2026-09-26 点名要分开）。
 *
 * - [Comics] —— 阅读器里收入的单页插图（原有能力，[FavoriteImagesBody]）；
 * - [Gallery] —— 图站（yande.re / Gelbooru）收藏的整张画（[GalleryFavoritesBody]）。
 *
 * 两者**不合并成一格**：来源不同、点开之后的去处不同（预览页 / 图站大图页）、
 * 能对它们做的事也不同（"从该页开始阅读"对一张画廊图没有意义）。
 * 混成一面墙之后，"点这张会发生什么"就没有统一答案了。
 */
private enum class ImageSection { Comics, Gallery }

private val imageSectionEntries = listOf("漫画收藏", "画廊收藏")

/** 手机档二级分段**单段**占屏宽的比例：比一级那三枚（0.25）再收一档，一眼看出是从属关系。 */
private const val subSegmentedCellWidthFraction = 0.22f

/**
 * 图片收藏面板的宿主：只做两件事 —— 选哪面墙、把顶栏让位交给墙自己的 `contentPadding`。
 *
 * 二级分段器（漫画收藏 / 画廊收藏）**不在这一层**，它在收藏页顶栏的 `bottomContent` 里，
 * 理由与"为什么不能留在内容里"都写在那一处。因此这里既没有静态 `padding(top=)`，
 * 也没有 `weight(1f)` 那种会被顶栏高度推着变的视口：两面墙都是**满屏视口 + contentPadding 让位**，
 * 与「网络收藏」「本地收藏」完全同一套契约 —— 顶栏折叠时视口不动，卡片只跟着手指走。
 *
 * [topPadding] 因此可以是常量（宿主那一处推导）。
 */
@Composable
private fun ImageFavoritesPanel(
    topPadding: Dp,
    section: ImageSection,
    scrollConnection: NestedScrollConnection?,
    backdrop: LayerBackdrop?,
    onPreviewImage: (FavoriteImageItem) -> Unit,
    onOpenComicDetail: (FavoriteImageItem) -> Unit,
    onReadFromPage: (FavoriteImageItem) -> Unit,
    onScrollStateChange: (canScrollUp: Boolean, hasScrolled: Boolean, scrollToTop: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    when (section) {
        ImageSection.Comics -> FavoriteImagesBody(
            topPadding = topPadding,
            scrollConnection = scrollConnection,
            backdrop = backdrop,
            onPreviewImage = onPreviewImage,
            onOpenComicDetail = onOpenComicDetail,
            onReadFromPage = onReadFromPage,
            onScrollStateChange = onScrollStateChange,
        )

        ImageSection.Gallery -> GalleryFavoritesBody(
            topPadding = topPadding,
            scrollConnection = scrollConnection,
            backdrop = backdrop,
            onOpenPost = { post ->
                // 与画廊一级点卡片走**同一条**路：跨 Activity 才有实时 blur-behind 与
                // 预测式返回；滑入垫着的那一帧由 GalleryCardsGrid 在点击那一刻截好。
                (context as? Activity)?.openGalleryPost(post.site, post.id)
            },
            onScrollStateChange = onScrollStateChange,
        )
    }
}

@Composable
private fun ImageSectionToggle(
    section: ImageSection,
    onSectionChange: (ImageSection) -> Unit,
) {
    val modes = remember { ImageSection.entries.toList() }
    val wide = isWideScreen(LocalConfiguration.current.screenWidthDp.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VeneraTokens.spacing.space3),
        contentAlignment = Alignment.Center,
    ) {
        VeneraSegmentedButton(
            options = remember { imageSectionEntries },
            selectedIndex = modes.indexOf(section),
            onSelect = { onSectionChange(modes[it]) },
            modifier = if (wide) Modifier.fillMaxWidth()
            else Modifier.fillMaxWidth(subSegmentedCellWidthFraction * modes.size),
        )
    }
}

@Composable
private fun FavoritesModeToggle(
    mode: FavoritesMode,
    onModeChange: (FavoritesMode) -> Unit,
) {
    // 分段控制器：每颗选项自持一颗药丸（选中 = 实心主题色，未选中 = 半透明底），没有外框。
    // 形态出处见 VeneraSegmentedButton 的头注（2026-09-28 换掉了旧的"药丸中的药丸"容器描边）。
    // 手机档收成居中一小条（用户真机反馈「太宽太散」）；平板档维持全宽不动。
    val modes = remember { favoritesModesNetworkFirst }
    val wide = isWideScreen(LocalConfiguration.current.screenWidthDp.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VeneraTokens.spacing.space3),
        contentAlignment = Alignment.Center,
    ) {
        VeneraSegmentedButton(
            options = remember { favoritesModeEntriesWithNetworkFirst },
            selectedIndex = modes.indexOf(mode),
            onSelect = { onModeChange(modes[it]) },
            // 手机档宽度按「每一段占屏宽 25%」推导：用户当初对**两段**控制器定的口径是
            // 整条 50% 居中，段数变三就是 3×25%。此前宽度写死 50% 没跟着段数走，
            // 三段各分 16.7% 屏宽，四个字的标签全被削成「网…图…本…」。
            modifier = if (wide) Modifier.fillMaxWidth()
            else Modifier.fillMaxWidth(segmentedCellWidthFraction * modes.size),
        )
    }
}

/** 手机档分段控制器**单段**占屏宽的比例（源自用户「整条 50% ÷ 两段」的口径）。 */
private const val segmentedCellWidthFraction = 0.25f

/** 模式顺序即分段器段序：网络第一、图片第二、本地第三（用户拍板）。与标签列表严格同序。 */
private val favoritesModesNetworkFirst =
    listOf(FavoritesMode.Network, FavoritesMode.Images, FavoritesMode.Local)

/** 网络收藏优先排序（用户拍板）。 */
val favoritesModeEntriesWithNetworkFirst = listOf(
    "网络收藏",
    "图片收藏",
    "本地收藏"
)


/** 收藏夹操作类型。 */
private sealed interface FolderDialog {

    data object Create : FolderDialog
    data object Rename : FolderDialog
    data object Move : FolderDialog
    data object Copy : FolderDialog
    data object Reorder : FolderDialog
    data object Export : FolderDialog
}

// region ---- 顶部收藏夹 chips ----

@Composable
private fun FolderChipRow(
    folders: List<String>,
    counts: Map<String, Int>,
    current: String,
    onSelectFolder: (String) -> Unit,
) {
    val tokens = com.venera.compose.ui.tokens.VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.space9, vertical = tokens.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing),
        ) {
            item {
                VeneraFilterPill(
                    text = "全部 · " + counts.values.sum(),
                    selected = current == LOCAL_ALL_FOLDER,
                    onClick = { onSelectFolder(LOCAL_ALL_FOLDER) },
                )
            }
            rowItems(folders, key = { it }) { folder ->
                VeneraFilterPill(
                    text = folder + " · " + (counts[folder] ?: 0),
                    selected = current == folder,
                    onClick = { onSelectFolder(folder) },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = keyword,
                onValueChange = onKeywordChange,
                modifier = Modifier.weight(1f),
            )
            if (keyword.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.size(18.dp).clickable { onKeywordChange("") },
                )
            }
        }
    }
}

// endregion

// region ---- 内容网格 ----

@Composable
private fun FavoriteGrid(
    vm: FavoritesViewModel,
    folders: List<String>,
    counts: Map<String, Int>,
    searchMode: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    onSelect: (ComicItem) -> Unit,
    selection: MultiSelectState<Pair<String, Int>>,
    /** 顶栏折叠行为：下滑时大标题收起、毛玻璃淡入。 */
    scrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
    backdrop: LayerBackdrop? = null,
    /** 把内部列表的滚动位置上抛（能否回到顶部 / 是否已下滑），供外壳渲染顶置按钮。 */
    onScrollStateChange: (canScrollUp: Boolean, hasScrolled: Boolean, scrollToTop: () -> Unit) -> Unit = { _, _, _ -> },
) {
    val tokens = VeneraTokens
    val displayMode = rememberComicListDisplayMode()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
        }.collect { hasScrolled ->
            onScrollStateChange(
                gridState.canScrollBackward,
                hasScrolled,
            ) { scope.launch { gridState.animateScrollToItem(0) } }
        }
    }
    // HIDE 模式：命中守卫判定链的条目整条剔除（UI 层过滤，不落数据库）。
    val guardManager = com.venera.compose.security.guard.ContentGuardManager.getInstance(LocalContext.current)
    val visibleComics = if (guardManager.nsfwMaskMode.collectAsState().value == "HIDE") {
        vm.comics.filter { item ->
            !guardManager.coverMaskStateFor(
                sourceKey = item.sourceKey, title = item.name, author = item.author,
                tags = item.tags, comicId = item.id, description = item.description,
            ).let { it == "HIDDEN" }
        }
    } else vm.comics

    val (gridWidth, gridWidthModifier) = rememberContentWidth(tokens.spacing.space9 * 2)
    // 墙上这一批的**可见**顺序，区间选要用它。整批算一次而不是每张卡各算一遍（那是 O(n²)）。
    val visibleKeys = remember(visibleComics) { visibleComics.map { it.id to it.type } }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(comicListColumnCount(displayMode.value, gridWidth)),
        contentPadding = PaddingValues(
            start = tokens.spacing.space9,
            end = tokens.spacing.space9,
            top = topPadding,
            bottom = LocalBottomBarClearance.current,
        ),
        // 用户拍板：横向列间距 16dp、纵向行间距 12dp（横 ≈ 纵的 1.5 倍，消除「横散纵挤」）。
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space8),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
        modifier = Modifier
            .fillMaxSize()
            .then(gridWidthModifier)
            .then(if (scrollConnection != null) Modifier.nestedScroll(scrollConnection) else Modifier)
            .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
    ) {
        // 一个收藏夹都没有时整行不渲染：只剩「全部 · N」一颗 pill 不提供任何选择，
        // 挂在网格顶部就像多出来的一块（用户真机反馈，与网络收藏侧同一处置）。
        if (folders.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "fav-folders") {
                FolderChipRow(
                    folders = folders,
                    counts = counts,
                    current = vm.currentFolder,
                    onSelectFolder = { vm.selectFolder(it) },
                )
            }
        }

        if (searchMode) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "fav-search") {
                SearchField(
                    keyword = vm.keyword,
                    onKeywordChange = { vm.updateKeyword(it) },
                    modifier = Modifier.padding(vertical = tokens.spacing.space1),
                )
            }
        }

        if (visibleComics.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "fav-empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    VeneraEmptyView(
                        title = if (vm.keyword.isBlank()) "暂无收藏漫画" else "没有匹配的收藏",
                        message = if (vm.keyword.isBlank()) {
                            "在漫画详情页点击「收藏」即可加入收藏夹"
                        } else {
                            "试试其他关键词"
                        },
                        icon = Icons.Outlined.StarBorder,
                    )
                }
            }
        } else {
            gridItems(visibleComics, key = { "${it.id}-${it.type}" }) { item ->
                val key = item.id to item.type
                FavoriteCard(
                    item = item,
                    detailed = displayMode.value == "detailed",
                    selecting = selection.active,
                    selected = key in selection.selected,
                    // 多选态下点整张卡 = 勾选（只让人去够右上角那个小圈太费劲）；
                    // 不在多选态才是"打开这一本"。
                    onClick = {
                        if (selection.active) selection.toggle(key) else onSelect(item.toComicItem())
                    },
                    onToggleSelect = { selection.toggle(key) },
                    // 长按：不在多选态 = 进入多选并选中这一项；已在多选态 = 区间反选。
                    // 两条都是官方 local_favorites_page.dart:728-760 的原始语义。
                    onLongClick = {
                        if (selection.active) {
                            selection.toggleRange(key, visibleKeys)
                        } else {
                            selection.enter(key)
                        }
                    },
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FavoriteCard(
    item: com.venera.compose.data.db.FavoriteItem,
    detailed: Boolean,
    /** 当前是否处于多选态（决定右上角那枚选择框摆不摆）。 */
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    /** 点右上角那枚选择框（与"点整卡"是同一条路，只是入口不同）。 */
    onToggleSelect: () -> Unit,
    onLongClick: () -> Unit,
) {
    val metrics by rememberCachedComicMetrics(item.sourceKey, item.id)
    // 内容守卫：带 sourceKey 走全量判定链（源级预设 + 用户规则 + 显式标记），
    // BLUR 命中打码；HIDE 命中在数据层已剔除，此处兜底。
    val guard = com.venera.compose.security.guard.ContentGuardManager.getInstance(LocalContext.current)
    val maskState = guard.coverMaskStateFor(
        sourceKey = item.sourceKey,
        title = item.name,
        author = item.author,
        tags = item.tags,
        comicId = item.id,
        description = item.description,
    )
    val tokens = com.venera.compose.ui.tokens.VeneraTokens
    val coverMask = if (maskState == "VISIBLE") {
        com.venera.compose.components.venera.VeneraCoverMask.Visible
    } else {
        com.venera.compose.components.venera.VeneraCoverMask.Masked
    }
    // 打码命中不飞行：飞行内容会渲染进 SharedTransitionLayout 的 overlay，
    // 等于绕开页面级裁剪 —— 遮罩不能有机会被揭开。
    val coverModifier = Modifier.coverSharedElement(
        key = com.venera.compose.components.ComicSharedTransition.coverKey(item.sourceKey, item.id),
        allowFly = maskState == "VISIBLE",
    )

    // 单列形态直接共用搜索页那套行卡（components.ComicRowCard）：收藏页与搜索页
    // 从此同一份实现，不会再各页一种高度、改一处忘一处。
    if (detailed) {
        SelectableCardFrame(
            selecting = selecting,
            selected = selected,
            onToggleSelect = onToggleSelect,
        ) {
            com.venera.compose.components.ComicRowCard(
                title = item.name,
                coverUrl = item.coverPath,
                subtitle = item.author,
                description = item.description,
                tags = item.tags,
                mask = coverMask,
                onClick = onClick,
                onLongClick = onLongClick,
                likesCount = metrics.likesCount,
                rating = metrics.rating?.toFloat(),
                coverModifier = coverModifier,
            )
        }
        return
    }

    com.venera.compose.components.venera.VeneraCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        SelectableCardFrame(
            selecting = selecting,
            selected = selected,
            onToggleSelect = onToggleSelect,
        ) {
            ComicCardLayout(detailed = detailed, modifier = Modifier.padding(tokens.spacing.space2), cover = {
                com.venera.compose.components.venera.VeneraCover(
                    url = item.coverPath,
                    contentDescription = item.name,
                    shimmerWhileLoading = false,
                    modifier = coverModifier,
                    mask = coverMask,
                )
            }) {
                Spacer(modifier = Modifier.height(tokens.spacing.space3))
                ComicMetrics(metrics.rating, metrics.likesCount)
                Text(
                    text = item.name,
                    fontWeight = tokens.type.weightBold,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textPrimary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = item.description,
                    fontSize = tokens.type.overline,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                )
                // 复用现成的 Tag 截断/归一化逻辑（searchVisibleTags），不另写一套。
                // 最多 2 个，空 tags 时整段不渲染（不产生空白区域）。
                val visibleTags = com.venera.compose.feature.searchVisibleTags(item.tags, emptyList())
                if (visibleTags.isNotEmpty()) {
                    Spacer(Modifier.height(tokens.spacing.space3))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                    ) {
                        visibleTags.take(2).forEach { tag ->
                            Box(Modifier.weight(1f, fill = false)) {
                                VeneraTagChip(text = tag, onClick = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun com.venera.compose.data.db.FavoriteItem.toComicItem() = ComicItem(
    id = id,
    title = name,
    author = author,
    coverUrl = coverPath,
    tags = tags,
    sourceName = sourceKey,
    description = description,
)

// endregion

// region ---- 对话框（Miuix 风格自建，避免依赖不确定的 Dialog API） ----

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    com.venera.compose.components.PredictiveBackOverlay(onDismiss = onDismiss) {
        Box(modifier = Modifier.clickable(enabled = false) {}) {
            Card(modifier = Modifier.padding(horizontal = com.venera.compose.ui.tokens.VeneraTokens.spacing.space10)) { content() }
        }
    }
}

@Composable
private fun InputDialog(
    title: String,
    hint: String,
    initialValue: String = "",
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    Scrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(com.venera.compose.ui.tokens.VeneraTokens.spacing.space8)) {
            Text(text = title, fontSize = com.venera.compose.ui.tokens.VeneraTokens.type.itemTitle, fontWeight = com.venera.compose.ui.tokens.VeneraTokens.type.weightSemibold)
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.isBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = hint, fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(color = com.venera.compose.ui.tokens.VeneraTokens.color.surfaceVariant.copy(alpha = com.venera.compose.ui.tokens.VeneraTokens.current.selectedSurfaceAlpha)),
                ) { Text("取消") }
                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            onConfirm(text)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(confirmText) }
            }
        }
    }
}

@Composable
private fun FolderPickerDialog(
    title: String,
    folders: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    Scrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(com.venera.compose.ui.tokens.VeneraTokens.spacing.space8)) {
            Text(text = title, fontSize = com.venera.compose.ui.tokens.VeneraTokens.type.itemTitle, fontWeight = com.venera.compose.ui.tokens.VeneraTokens.type.weightSemibold)
            Spacer(modifier = Modifier.height(10.dp))
            if (folders.isEmpty()) {
                Text(
                    text = "没有其他收藏夹，请先新建一个",
                    fontSize = 13.sp,
                    color = com.venera.compose.ui.tokens.VeneraTokens.color.textSecondary,
                )
            } else {
                Column {
                    folders.forEach { folder ->
                        Text(
                            text = folder,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(folder)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(color = com.venera.compose.ui.tokens.VeneraTokens.color.surfaceVariant.copy(alpha = com.venera.compose.ui.tokens.VeneraTokens.current.selectedSurfaceAlpha)),
            ) { Text("取消") }
        }
    }
}

@Composable
private fun ReorderDialog(
    folders: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val order = remember(folders) { mutableStateOf(folders.toList()) }

    fun move(index: Int, delta: Int) {
        val list = order.value.toMutableList()
        val target = index + delta
        if (target !in list.indices) return
        val tmp = list[index]
        list[index] = list[target]
        list[target] = tmp
        order.value = list
    }

    Scrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(com.venera.compose.ui.tokens.VeneraTokens.spacing.space8)) {
            Text(text = "调整收藏夹顺序", fontSize = com.venera.compose.ui.tokens.VeneraTokens.type.itemTitle, fontWeight = com.venera.compose.ui.tokens.VeneraTokens.type.weightSemibold)
            Spacer(modifier = Modifier.height(10.dp))
            Column {
                order.value.forEachIndexed { i, folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = folder, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(
                            text = "↑",
                            fontSize = 16.sp,
                            color = if (i == 0) MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.3f)
                            else MiuixTheme.colorScheme.primary,
                            modifier = Modifier.clickable { move(i, -1) }.padding(horizontal = 10.dp),
                        )
                        Text(
                            text = "↓",
                            fontSize = 16.sp,
                            color = if (i == order.value.lastIndex) MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.3f)
                            else MiuixTheme.colorScheme.primary,
                            modifier = Modifier.clickable { move(i, 1) }.padding(horizontal = 10.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(color = com.venera.compose.ui.tokens.VeneraTokens.color.surfaceVariant.copy(alpha = com.venera.compose.ui.tokens.VeneraTokens.current.selectedSurfaceAlpha)),
                ) { Text("取消") }
                Button(
                    onClick = {
                        onConfirm(order.value)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun ExportDialog(
    folder: String,
    onDismiss: () -> Unit,
) {
    Scrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(com.venera.compose.ui.tokens.VeneraTokens.spacing.space8)) {
            Text(text = "导出收藏夹", fontSize = com.venera.compose.ui.tokens.VeneraTokens.type.itemTitle, fontWeight = com.venera.compose.ui.tokens.VeneraTokens.type.weightSemibold)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (folder == LOCAL_ALL_FOLDER) {
                    "请先切换到具体的收藏夹再导出（「全部」是虚拟视图）。"
                } else {
                    "「$folder」的导出功能将在 S7（同步/备份）阶段与 WebDAV 一同落地。"
                },
                fontSize = 13.sp,
                color = com.venera.compose.ui.tokens.VeneraTokens.color.textSecondary,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("知道了") }
        }
    }
}

@Composable
private fun FolderMenuSheet(
    folderName: String,
    canEditFolder: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onReorder: () -> Unit,
    onExport: () -> Unit,
) {
    Scrim(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(com.venera.compose.ui.tokens.VeneraTokens.spacing.space8)) {
            Text(
                text = if (canEditFolder) folderName else "收藏夹操作",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            MenuItem(text = "新建收藏夹", icon = { Icon(Icons.Outlined.Add, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }) { onDismiss(); onCreate() }
            if (canEditFolder) {
                MenuItem(text = "重命名当前收藏夹", icon = { }) { onDismiss(); onRename() }
                MenuItem(text = "删除当前收藏夹", icon = { }) { onDismiss(); onDelete() }
            }
            MenuItem(text = "调整收藏夹顺序", icon = { }) { onDismiss(); onReorder() }
            MenuItem(text = "导出当前收藏夹", icon = { }) { onDismiss(); onExport() }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(color = com.venera.compose.ui.tokens.VeneraTokens.color.surfaceVariant.copy(alpha = com.venera.compose.ui.tokens.VeneraTokens.current.selectedSurfaceAlpha)),
            ) { Text("取消") }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, fontSize = 15.sp)
    }
}

// endregion

/**
 * 顶栏排序菜单：名称 / 时间 / 自定义排序，当前项主色勾选。
 *
 * 从 FolderChipRow 行内提升到顶栏 actions —— 与「布局切换 / 更多」并列，
 * 消除原二级工具条的横向拥挤。
 */
@Composable
private fun FavoritesSortMenu(
    sortOrder: FavoriteSortOrder,
    onSortOrderChange: (FavoriteSortOrder) -> Unit,
) {
    val tokens = VeneraTokens
    var expanded by remember { mutableStateOf(false) }
    Box {
        VeneraTopBarPill(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = "排序方式",
                tint = if (sortOrder == FavoriteSortOrder.CUSTOM) tokens.color.textSecondary else tokens.color.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FavoriteSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = order.label,
                            fontSize = tokens.type.body,
                            color = if (order == sortOrder) tokens.color.primary else tokens.color.textPrimary,
                        )
                    },
                    trailingIcon = if (order == sortOrder) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "当前排序",
                                tint = tokens.color.primary,
                                modifier = Modifier.size(tokens.spacing.chipIconSize),
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        onSortOrderChange(order)
                    },
                )
            }
        }
    }
}
