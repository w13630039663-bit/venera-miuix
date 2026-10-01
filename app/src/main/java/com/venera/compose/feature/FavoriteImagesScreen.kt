package com.venera.compose.feature

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
// 登记保留：Scaffold（miuix 的同名件参数面不同，换它=重做一层窗口内布局）、
// DropdownMenu（无锚点对应物）、三颗"图标+文字"的 TextButton（miuix 文字按钮没有 slot）。
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.venera.compose.components.ComicSharedTransition
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.isWideScreen
import com.venera.compose.components.selection.MultiSelectBarAction
import com.venera.compose.components.selection.SelectableCardFrame
import com.venera.compose.components.selection.VeneraMultiSelectBar
import com.venera.compose.components.selection.rememberMultiSelectState
import com.venera.compose.feature.favoriteimages.FavoriteImageItem
import com.venera.compose.feature.favoriteimages.FavoriteImagesManager
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.components.venera.VeneraIconButton
import com.venera.compose.components.venera.VeneraTextButton

/**
 * 插图收藏独立页（设置 → 阅读设置 → 单页与插图收藏）。
 *
 * 收藏页已把它做成第二个分段直接内嵌；这条入口仍留给设置页，两者共用
 * [FavoriteImagesBody] 一份实现。
 *
 * 三个跳转动作由宿主给（设置那侧走 [SettingsEscape] 交回 MainActivity 的导航图）。
 * 刻意不设默认值：这条入口若哪天不接线，编译期就该报错，而不是留下一个点了没反应的菜单。
 */
@Composable
fun FavoriteImagesScreen(
    onBack: () -> Unit,
    onPreviewImage: (FavoriteImageItem) -> Unit,
    onOpenComicDetail: (FavoriteImageItem) -> Unit,
    onReadFromPage: (FavoriteImageItem) -> Unit,
) {
    val tokens = VeneraTokens
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VeneraIconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(tokens.spacing.space2))
                Text(
                    text = "单页与插图收藏",
                    fontSize = tokens.type.itemTitle,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
        }
    ) { innerPadding ->
        FavoriteImagesBody(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onPreviewImage = onPreviewImage,
            onOpenComicDetail = onOpenComicDetail,
            onReadFromPage = onReadFromPage,
        )
    }
}

/**
 * 插图收藏主体：瀑布流 + 长按定位菜单。
 *
 * 卡片只有三样（用户拍板：统计卡整块不做）—— 图、漫画名、作者名。
 * 章节/页码不再占卡片面，仍在 [FavoriteImagePreviewScreen] 里显示。
 *
 * 列数分档（用户真机口径）：手机两列，平板三列 —— 平板上两列会把每张图撑到
 * 半屏宽，瀑布流的高低错落反而变成"一屏看不下一张半"。
 *
 * 与收藏页其余面板同一套宿主契约：[topPadding] 由顶栏让位决定、[scrollConnection] 接顶栏
 * 折叠、[backdrop] 供毛玻璃采样、滚动状态经 [onScrollStateChange] 上抛给「顶置」按钮。
 * 三个去处都由宿主接自己的导航图：[onPreviewImage] 点卡开预览页（卡片那张图与预览页之间
 * 是一条共享元素飞行）、[onReadFromPage] 与 [onOpenComicDetail] 是长按菜单的两个去处。
 */
@Composable
fun FavoriteImagesBody(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    scrollConnection: NestedScrollConnection? = null,
    backdrop: LayerBackdrop? = null,
    onPreviewImage: (FavoriteImageItem) -> Unit,
    onOpenComicDetail: (FavoriteImageItem) -> Unit,
    onReadFromPage: (FavoriteImageItem) -> Unit,
    onScrollStateChange: (canScrollUp: Boolean, hasScrolled: Boolean, scrollToTop: () -> Unit) -> Unit = { _, _, _ -> },
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { FavoriteImagesManager.getInstance(context) }
    val gridState = rememberLazyStaggeredGridState()

    var images by remember { mutableStateOf<List<FavoriteImageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    // 大屏三列、手机两列（用户真机反馈「平板上太大了」）。
    val wide = isWideScreen(LocalConfiguration.current.screenWidthDp.dp)
    val columnCount = if (wide) 3 else 2
    /**
     * 每张图的真实宽高比，加载成功后回填。
     *
     * 瀑布流要在**摆放前**就知道高度，否则各列会先等分再集体跳动。这里按 id 缓存实测比例，
     * 没加载到的先用封面比例 3:4 占位；比例夹在 0.4~2.5，防横长条把一整列撑出屏幕。
     * 平板再抬一道比例下限把卡高压进「1.1 倍列宽」（推导见 spacing.favoriteImageMinRatio），
     * 超出部分 Crop 裁掉，预览页里仍是全图；手机档不压（用户：手机上没问题）。
     */
    val ratios = remember { mutableStateMapOf<Long, Float>() }

    // ── 批量整理（多选）──
    // 与本地收藏、网络收藏、画廊收藏**共用同一份状态机**（components/selection/）：
    // 长按 = 进多选并选中、多选态内长按 = 区间反选、选中归零 = 自动退出。
    // 键取插图自身那行主键 id（Long）。
    val selection = rememberMultiSelectState<Long>()
    // 工具条做在**本面板内部**（不塞进收藏页顶栏）：顶栏归 FavoritesScreen，
    // 而它在冻结声明第三批标了验收冻结 —— 这一轮只读不改它。
    // 系统返回先退多选，不要把整个收藏 tab 弹掉。
    BackHandler(enabled = selection.active) { selection.exit() }

    fun refresh() {
        scope.launch {
            isLoading = true
            images = manager.getAllFavorites()
            isLoading = false
            // 选中项可能已被删掉（含另一条入口那侧删的）—— 按最新列表收敛，别留幽灵选择。
            // 收敛到一项不剩时状态机自己退出多选，不会留下"已选择 0 项"的空工具条。
            selection.retainAll(images.map { it.id })
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

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

    // 墙上这一批的顺序，区间选要用它。整批算一次而不是每张卡各算一遍（那是 O(n²)）。
    val visibleIds = remember(images) { images.map { it.id } }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // 整页级加载按项目裁决走波浪环，不用 material 的 CircularProgressIndicator。
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(tokens.spacing.loaderPage),
                        color = tokens.color.primary,
                        trackColor = tokens.color.surfaceVariant,
                    )
                }
            }

            images.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VeneraEmptyView(
                        title = "暂无收藏的单页插图",
                        message = "在阅读器中点击或长按画面即可将喜欢的页面收入此处",
                        icon = Icons.Outlined.Collections,
                    )
                }
            }

            else -> {
                LazyVerticalStaggeredGrid(
                    state = gridState,
                    columns = StaggeredGridCells.Fixed(columnCount),
                    contentPadding = PaddingValues(
                        start = tokens.spacing.screenHorizontal,
                        end = tokens.spacing.screenHorizontal,
                        top = topPadding,
                        bottom = VeneraSpacing.bottomBarClearance,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space5),
                    verticalItemSpacing = tokens.spacing.gridGap,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (scrollConnection != null) Modifier.nestedScroll(scrollConnection) else Modifier)
                        .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
                ) {
                    items(images, key = { it.id }) { item ->
                        // 手势走 miuix Card 官方可点击重载：combinedClickable 挂在
                        // squircleSurface **里面**那一层。叠在外层会被 squircle 吞掉长按
                        // （真机实测教训，见 VeneraCard 的分层注释与冻结声明同批记录）。
                        //
                        // 选中态与选择框交给 SelectableCardFrame（四处收藏面板同一份实现）：
                        // 长按菜单**已撤**，"从该页开始阅读 / 查看漫画详情"两个动作搬进了工具条
                        // （只在恰好选中 1 项时出现）。原先卡片右下角那颗垃圾桶也一并撤掉 ——
                        // 卡上摆删除钮既抢画面又极易误触，删除统一走工具条。
                        SelectableCardFrame(
                            selecting = selection.active,
                            selected = selection.contains(item.id),
                            onToggleSelect = { selection.toggle(item.id) },
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                // 圆角跟 [VeneraCard] 同一口径（tokens.shape.card）：不传的话
                                // miuix 自家默认是 16dp，MIUIX 模式下会比别处的卡片小一档，
                                // 且 [SelectableCardFrame] 的洗底按 shape.card 裁就对不上了。
                                cornerRadius = tokens.shape.card,
                                // 多选态下点整张卡就是**勾选** —— 只让人去点那个小圆圈太费劲。
                                onClick = {
                                    if (selection.active) {
                                        selection.toggle(item.id)
                                    } else {
                                        onPreviewImage(item)
                                    }
                                },
                                // 长按：不在多选态 = 进入多选并选中；已在多选态 = 区间反选。
                                onLongPress = {
                                    if (selection.active) {
                                        selection.toggleRange(item.id, visibleIds)
                                    } else {
                                        selection.enter(item.id)
                                    }
                                },
                            ) {
                                Column(modifier = Modifier.padding(tokens.spacing.space3)) {
                                    AsyncImage(
                                        model = item.localPath.ifBlank { item.imageUrl },
                                        contentDescription = item.comicTitle,
                                        contentScale = ContentScale.Crop,
                                        onSuccess = { state ->
                                            val size = state.painter.intrinsicSize
                                            if (size.width > 0f && size.height > 0f) {
                                                ratios[item.id] = (size.width / size.height).coerceIn(0.4f, 2.5f)
                                            }
                                        },
                                        // 与预览页那支同串 key 的共享元素（favoriteImageKey）。
                                        // 挂链首 = 跟封面那对同一层级：外层是共享元素，内层才是尺寸与圆角。
                                        modifier = Modifier
                                            .coverSharedElement(
                                                key = ComicSharedTransition.favoriteImageKey(item.id),
                                            )
                                            .fillMaxWidth()
                                            .aspectRatio(
                                                (ratios[item.id] ?: tokens.spacing.coverAspectRatio)
                                                    .let { if (wide) maxOf(it, tokens.spacing.favoriteImageMinRatio) else it }
                                            )
                                            .clip(RoundedCornerShape(tokens.shape.small)),
                                    )
                                    Spacer(modifier = Modifier.height(tokens.spacing.space3))
                                    Text(
                                        text = item.comicTitle,
                                        fontSize = tokens.type.caption,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(tokens.spacing.space1))
                                    // 作者反查不到时留空而不拿源名顶上：宁可少一行，不摆假数据。
                                    Text(
                                        text = item.author,
                                        fontSize = tokens.type.overline,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (selection.active) {
            val selectedItems = images.filter { it.id in selection.selected }
            // 面板内浮层工具条（不动收藏页顶栏那条冻结线）。底部留白把最后两行抬起来，
            // 否则瀑布流末尾的卡片会被它压住点不到。
            VeneraMultiSelectBar(
                selectedCount = selection.count,
                allSelected = visibleIds.isNotEmpty() && selection.count == visibleIds.size,
                onExit = { selection.exit() },
                onSelectAll = { selection.selectAll(visibleIds) },
                onInvert = { selection.invert(visibleIds) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = tokens.spacing.screenHorizontal,
                        end = tokens.spacing.screenHorizontal,
                        bottom = tokens.spacing.space8 + VeneraSpacing.bottomBarClearance,
                    ),
            ) {
                // 「从该页开始阅读 / 查看漫画详情」是**单张**才有意义的动作：
                // 恰好选中 1 项时它们才上条。这两个动作原先在长按菜单里，而长按已经归多选所有
                // （四处收藏面板统一），所以搬到这里 —— 动作没丢，入口跟着多选走。
                if (selectedItems.size == 1) {
                    val one = selectedItems.first()
                    MultiSelectBarAction(
                        icon = Icons.Outlined.MenuBook,
                        label = "从该页阅读",
                        onClick = {
                            selection.exit()
                            onReadFromPage(one)
                        },
                    )
                    MultiSelectBarAction(
                        icon = Icons.Outlined.Info,
                        label = "详情",
                        onClick = {
                            selection.exit()
                            onOpenComicDetail(one)
                        },
                    )
                }
                MultiSelectBarAction(
                    icon = Icons.Outlined.Delete,
                    label = "移除",
                    destructive = true,
                    onClick = {
                        val ids = selectedItems.map { it.id }
                        if (ids.isNotEmpty()) {
                            scope.launch {
                                // 如实报数：removeFavorites 返回真正删掉的行数，0 就是没删成，
                                // 不能照旧弹「已移除」（那是假反馈）。
                                val removed = manager.removeFavorites(ids)
                                Toast.makeText(
                                    context,
                                    if (removed > 0) "已移除 $removed 张插图收藏" else "移除失败",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                selection.exit()
                                refresh()
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * 插图预览页：点收藏卡之后，那张图**飞进来**的那一页。
 *
 * 原本它是页内 `Dialog` 灯箱。改成导航目的地只为一件事：共享元素的飞行由 NavHost 那层
 * `SharedTransitionLayout` 驱动，而 Dialog 是**独立窗口** —— `sharedElement` 跨不过窗口边界，
 * 挂在 Dialog 上不报错，就是不飞。
 *
 * 落点形状必须贴着图本身：容器若取「剩余空间」，图在里面 Fit 就留 letterbox 边，
 * 飞的是框不是图，观感退化成"闪一下"。所以这里也按实测比例定容器，并且
 * **比例未就绪时的占位值取 spacing.coverAspectRatio —— 与卡片那一支逐字相同**，
 * 起飞帧与落地帧因此同形。
 */
@Composable
fun FavoriteImagePreviewScreen(
    item: FavoriteImageItem,
    onBack: () -> Unit,
    onOpenComicDetail: (FavoriteImageItem) -> Unit,
    onReadFromPage: (FavoriteImageItem) -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { FavoriteImagesManager.getInstance(context) }
    val sharedKey = remember(item.id) { ComicSharedTransition.favoriteImageKey(item.id) }
    // spacing 是随主题切换的 @Composable 取值，不能在 remember {} 里读（那 lambda 禁 composable 调用）。
    val placeholderRatio = tokens.spacing.coverAspectRatio
    var ratio by remember { mutableStateOf(placeholderRatio) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 暗场浓度沿用封面遮罩那条现成口径（VeneraCover 读的就是 maskScrimAlpha）：
            // 同一个物理含义的遮罩全应用只该有一个浓度，不再造一个"灯箱黑"。
            .background(Color.Black.copy(alpha = tokens.current.maskScrimAlpha))
            .statusBarsPadding()
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(tokens.shape.large),
            color = MiuixTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(tokens.spacing.space8),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // 大图本身 = 「读到这一页」。
                    AsyncImage(
                        model = item.localPath.ifBlank { item.imageUrl },
                        contentDescription = item.comicTitle,
                        contentScale = ContentScale.Fit,
                        onSuccess = { state ->
                            val size = state.painter.intrinsicSize
                            if (size.width > 0f && size.height > 0f) ratio = size.width / size.height
                        },
                        modifier = Modifier
                            .coverSharedElement(key = sharedKey)
                            // 先按高定宽、放不下再按宽定高 = "在剩余空间里塞下整张图"。
                            // 这一步不能写成 weight(1f)：Box 给子项的是 min=max 的锁死约束，
                            // aspectRatio 缩不回去，容器又变回"框比图大"。
                            .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(tokens.shape.medium))
                            .clickable { onReadFromPage(item) },
                    )
                }
                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                Text(
                    text = "${item.comicTitle} - ${item.chapterTitle} (P.${item.pageIndex + 1})",
                    fontSize = tokens.type.body,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space1))
                // 点图阅读没有可见按钮，得留一句话当可供性提示，否则这个手势探不出来。
                Text(
                    text = "轻触图片从该页开始阅读",
                    fontSize = tokens.type.overline,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    // 三钮等宽而不是 SpaceEvenly：手机最窄档（360dp）下三个「图标+4 字」
                    // 按钮按自然宽排会顶出卡片，等分后各自有余量、超长只截字不溢出。
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, item.imageUrl)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "分享插图链接"))
                            } catch (_: Exception) {}
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(tokens.spacing.space5),
                        )
                        Spacer(modifier = Modifier.width(tokens.spacing.space2))
                        Text("分享链接")
                    }

                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenComicDetail(item) },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(tokens.spacing.space5),
                        )
                        Spacer(modifier = Modifier.width(tokens.spacing.space2))
                        Text("查看漫画")
                    }

                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                manager.removeFavorite(item.id)
                                Toast.makeText(context, "已移除该插图收藏", Toast.LENGTH_SHORT).show()
                                // 移除后这一条已无可看之物，留在原地就是个空壳 —— 直接退。
                                // 列表那侧重新进入组合时会自己重读一次表，不需要跨页通知。
                                onBack()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = StatusColors.Failing,
                            modifier = Modifier.size(tokens.spacing.space5),
                        )
                        Spacer(modifier = Modifier.width(tokens.spacing.space2))
                        Text("移除收藏", color = StatusColors.Failing)
                    }
                }
            }
        }
    }
}
