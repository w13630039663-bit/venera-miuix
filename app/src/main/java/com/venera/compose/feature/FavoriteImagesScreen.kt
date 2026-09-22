package com.venera.compose.feature

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.isWideScreen
import com.venera.compose.feature.favoriteimages.FavoriteImageItem
import com.venera.compose.feature.favoriteimages.FavoriteImagesManager
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 插图收藏独立页（设置 → 阅读设置 → 单页与插图收藏）。
 *
 * 收藏页已把它做成第二个分段直接内嵌；这条入口仍留给设置页，两者共用
 * [FavoriteImagesBody] 一份实现。
 *
 * 两个跳转动作由宿主给（设置那侧走 [SettingsEscape] 交回 MainActivity 的导航图）。
 * 刻意不设默认值：这条入口若哪天不接线，编译期就该报错，而不是留下一个点了没反应的菜单。
 */
@Composable
fun FavoriteImagesScreen(
    onBack: () -> Unit,
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
                IconButton(onClick = onBack) {
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
            onOpenComicDetail = onOpenComicDetail,
            onReadFromPage = onReadFromPage,
        )
    }
}

/**
 * 插图收藏主体：瀑布流 + 大图灯箱 + 长按定位菜单。
 *
 * 卡片只有三样（用户拍板：统计卡整块不做）—— 图、漫画名、作者名。
 * 章节/页码不再占卡片面，仍在大图灯箱里显示。
 *
 * 列数分档（用户真机口径）：手机两列，平板三列 —— 平板上两列会把每张图撑到
 * 半屏宽，瀑布流的高低错落反而变成"一屏看不下一张半"。
 *
 * 与收藏页其余面板同一套宿主契约：[topPadding] 由顶栏让位决定、[scrollConnection] 接顶栏
 * 折叠、[backdrop] 供毛玻璃采样、滚动状态经 [onScrollStateChange] 上抛给「顶置」按钮。
 * [onOpenComicDetail] / [onReadFromPage] 是长按菜单的两个去处，由宿主接到各自的导航图上。
 */
@Composable
fun FavoriteImagesBody(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    scrollConnection: NestedScrollConnection? = null,
    backdrop: LayerBackdrop? = null,
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
    var previewImage by remember { mutableStateOf<FavoriteImageItem?>(null) }
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
     * 超出部分 Crop 裁掉，灯箱里仍是全图；手机档不压（用户：手机上没问题）。
     */
    val ratios = remember { mutableStateMapOf<Long, Float>() }

    fun refresh() {
        scope.launch {
            isLoading = true
            images = manager.getAllFavorites()
            isLoading = false
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
                            onClick = { previewImage = item },
                            onLongPress = { menuImage = item },
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
                                    modifier = Modifier
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
                                    IconButton(
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
                        FavoriteImageMenu(
                            expanded = menuImage?.id == item.id,
                            item = item,
                            onDismiss = { menuImage = null },
                            onOpenComicDetail = onOpenComicDetail,
                            onReadFromPage = onReadFromPage,
                        )
                    }
                }
            }
        }

        // 大图查看灯箱
        if (previewImage != null) {
            val current = previewImage!!
            Dialog(
                onDismissRequest = { previewImage = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { previewImage = null },
                    contentAlignment = Alignment.Center
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
                            AsyncImage(
                                model = current.localPath.ifBlank { current.imageUrl },
                                contentDescription = current.comicTitle,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(tokens.shape.medium))
                            )
                            Spacer(modifier = Modifier.height(tokens.spacing.space5))
                            Text(
                                text = "${current.comicTitle} - ${current.chapterTitle} (P.${current.pageIndex + 1})",
                                fontSize = tokens.type.body,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(tokens.spacing.space5))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(onClick = {
                                    try {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, current.imageUrl)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "分享插图链接"))
                                    } catch (_: Exception) {}
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(tokens.spacing.space5),
                                    )
                                    Spacer(modifier = Modifier.width(tokens.spacing.space2))
                                    Text("分享链接")
                                }

                                TextButton(onClick = {
                                    scope.launch {
                                        manager.removeFavorite(current.id)
                                        previewImage = null
                                        refresh()
                                        Toast.makeText(context, "已移除该插图收藏", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = Color(0xFFE53935),
                                        modifier = Modifier.size(tokens.spacing.space5),
                                    )
                                    Spacer(modifier = Modifier.width(tokens.spacing.space2))
                                    Text("移除收藏", color = Color(0xFFE53935))
                                }
                            }
                        }
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
    }
}
