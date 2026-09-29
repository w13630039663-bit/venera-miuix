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
    /** 长按菜单当前锚定在哪张卡片；null = 未开。 */
    var menuImage by remember { mutableStateOf<FavoriteImageItem?>(null) }
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
    // 工具条刻意做在**本面板内部**，不塞进收藏页顶栏：顶栏归 FavoritesScreen，
    // 而它在冻结声明第三批标了验收冻结 —— 这一轮只读不改它。
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }
    // 系统返回先退多选，不要把整个收藏 tab 弹掉。
    BackHandler(enabled = selectionMode) { exitSelection() }

    fun refresh() {
        scope.launch {
            isLoading = true
            images = manager.getAllFavorites()
            isLoading = false
            // 选中项可能已被删掉（含另一条入口那侧删的）—— 按最新列表收敛，别留幽灵选择。
            selectedIds = selectedIds.intersect(images.mapTo(mutableSetOf()) { it.id })
            // 一张不剩时自动退出多选，否则工具条挂在空列表上还报"已选择 N 项"。
            if (images.isEmpty()) selectionMode = false
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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            // 多选态下点整张卡就是**勾选** —— 只让人去点那个小圆圈太费劲。
                            // 长按菜单在多选态让位（勾完就勾完，不再叠一层弹层）。
                            onClick = {
                                if (selectionMode) {
                                    selectedIds =
                                        if (item.id in selectedIds) selectedIds - item.id
                                        else selectedIds + item.id
                                    if (selectedIds.isEmpty()) selectionMode = false
                                } else {
                                    onPreviewImage(item)
                                }
                            },
                            onLongPress = { if (!selectionMode) menuImage = item },
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // 作者反查不到时留空而不拿源名顶上：宁可少一行，不摆假数据。
                                    Text(
                                        text = item.author,
                                        fontSize = tokens.type.overline,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (selectionMode) {
                                        // 勾选圈放在原先垃圾桶的位置：同一格换语义，
                                        // 不额外占高度，也不去碰上面那支共享元素图的几何。
                                        val checked = item.id in selectedIds
                                        VeneraIconButton(
                                            onClick = {
                                                selectedIds =
                                                    if (checked) selectedIds - item.id
                                                    else selectedIds + item.id
                                                if (selectedIds.isEmpty()) selectionMode = false
                                            },
                                            modifier = Modifier.size(tokens.spacing.space9),
                                        ) {
                                            Icon(
                                                imageVector = if (checked) Icons.Filled.CheckCircle
                                                else Icons.Outlined.Circle,
                                                contentDescription = if (checked) "取消选择" else "选择",
                                                tint = if (checked) tokens.color.primary
                                                else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                modifier = Modifier.size(tokens.spacing.space5),
                                            )
                                        }
                                    } else {
                                        VeneraIconButton(
                                            onClick = {
                                                scope.launch {
                                                    manager.removeFavorite(item.id)
                                                    refresh()
                                                }
                                            },
                                            modifier = Modifier.size(tokens.spacing.space9),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = "删除",
                                                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                modifier = Modifier.size(tokens.spacing.space5),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        FavoriteImageMenu(
                            expanded = menuImage?.id == item.id,
                            item = item,
                            onDismiss = { menuImage = null },
                            onOpenComicDetail = onOpenComicDetail,
                            onReadFromPage = onReadFromPage,
                            onMultiSelect = {
                                selectionMode = true
                                selectedIds = setOf(it.id)
                            },
                        )
                    }
                }
            }
        }
        if (selectionMode) {
            // 面板内浮层工具条（不动收藏页顶栏那条冻结线）。底部留白把最后两行抬起来，
            // 否则瀑布流末尾的卡片会被它压住点不到。
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = tokens.spacing.screenHorizontal,
                        end = tokens.spacing.screenHorizontal,
                        bottom = tokens.spacing.space8 + VeneraSpacing.bottomBarClearance,
                    ),
                shape = RoundedCornerShape(tokens.shape.large),
                color = MiuixTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = tokens.spacing.space5,
                        end = tokens.spacing.space2,
                        top = tokens.spacing.space1,
                        bottom = tokens.spacing.space1,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "已选择 ${selectedIds.size} 项",
                        fontSize = tokens.type.caption,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    VeneraTextButton(
                        text = if (selectedIds.size == images.size) "取消全选" else "全选",
                        onClick = {
                            selectedIds = if (selectedIds.size == images.size) emptySet()
                            else images.mapTo(mutableSetOf()) { it.id }
                        },
                    )
                    VeneraTextButton(
                        text = "移除",
                        destructive = true,
                        onClick = {
                            val ids = selectedIds.toList()
                            if (ids.isEmpty()) return@VeneraTextButton
                            scope.launch {
                                // 如实报数：removeFavorites 返回真正删掉的行数，0 就是没删成，
                                // 不能照旧弹「已移除」（那是假反馈）。
                                val removed = manager.removeFavorites(ids)
                                Toast.makeText(
                                    context,
                                    if (removed > 0) "已移除 $removed 张插图收藏" else "移除失败",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                exitSelection()
                                refresh()
                            }
                        },
                    )
                    VeneraTextButton(text = "关闭", onClick = { exitSelection() })
                }
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

/**
 * 插图卡片长按菜单：把「这一页」还原成两个可去的去处。
 *
 * 菜单锚在**被长按的那一格**上（逐项各挂一个 DropdownMenu）；共用一个锚会从第一格弹出，
 * 详情页的标签长按已经踩过这个坑。
 *
 * 「从该页开始阅读」带的是收藏时记下的章节标题与页码 —— 章节 **id** 并没有入库，
 * 所以由详情页解析出章节目录后按标题找回，见 Navigation.kt 的 ReadTarget。
 */
@Composable
private fun FavoriteImageMenu(
    expanded: Boolean,
    item: FavoriteImageItem,
    onDismiss: () -> Unit,
    onOpenComicDetail: (FavoriteImageItem) -> Unit,
    onReadFromPage: (FavoriteImageItem) -> Unit,
    onMultiSelect: (FavoriteImageItem) -> Unit,
) {
    if (!expanded) return
    val tokens = VeneraTokens
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Column {
                    Text(
                        text = "从该页开始阅读",
                        fontSize = tokens.type.caption,
                        color = tokens.color.textPrimary,
                    )
                    Text(
                        text = "${item.chapterTitle} · 第 ${item.pageIndex + 1} 页",
                        fontSize = tokens.type.overline,
                        color = tokens.color.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            onClick = { onDismiss(); onReadFromPage(item) },
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = "查看漫画详情",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textPrimary,
                )
            },
            onClick = { onDismiss(); onOpenComicDetail(item) },
        )
        // 进多选的入口挂在长按菜单里：长按已经归"操作菜单"所有，不能再拿长按当多选开关，
        // 而卡片本身在多选态下整卡可勾，所以这里只需要一个"起手"入口。
        DropdownMenuItem(
            text = {
                Text(
                    text = "多选",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textPrimary,
                )
            },
            onClick = { onDismiss(); onMultiSelect(item) },
        )
    }
}
