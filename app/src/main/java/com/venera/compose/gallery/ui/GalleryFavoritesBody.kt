package com.venera.compose.gallery.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraDialog
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.isWideScreen
import com.venera.compose.components.selection.MultiSelectBarAction
import com.venera.compose.components.selection.VeneraMultiSelectBar
import com.venera.compose.components.selection.rememberMultiSelectState
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.gallery.data.GalleryFavoritesStore
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.toPost
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

/**
 * 画廊收藏面板 —— 「收藏 → 图片收藏 → 画廊收藏」那一档。
 *
 * ## 为什么复用日榜那面墙，而不是另写一张卡
 *
 * 卡片外观、比例夹取（0.4~2.5）、"点开时截下这一帧"的滑入垫图、来源/视频角标 ——
 * 这几条口径一旦分叉，同一张图在画廊和收藏里会长得不一样，或在收藏里点开时
 * 进场没有垫图（黑一下）。所以这里直接用 [GalleryCardsGrid] + [buildGalleryWall]：
 * **连"哪张要打码、哪张按屏蔽规则收起"也是同一把判据**（见 [buildGalleryWall] 的注释），
 * 否则会出现"墙上被挡、收藏里全裸"这条最不该有的分叉。
 *
 * ## 与本地漫画那面墙的区别（不是遗漏）
 *
 * - **不做多选搬运**：本地漫画那边有收藏夹、有移动/复制/导出，画廊这批是单层平铺，
 *   摆一套多选工具条只会有"移到哪"这一个真动作，不够摆一条工具条。
 * - **不做比例回填**：两站的 JSON 直接带中间档宽高，摆放前就知道高度（见卡片注释）。
 * - **没有加载态**：收藏档是本地文件，[GalleryFavoritesStore] 在构造时同步读完，
 *   第一帧就有内容。摆一个转圈只会是"假装在加载"。
 *
 * ## 移除的入口
 *
 * 长按卡片 → 确认弹层。**刻意不**在每张卡上挂一颗"取消收藏"的心：这一屏里每一张都已经
 * 是收藏，那个心不是开关而是删除钮，摆在卡上既抢画面又容易被误触。要取消收藏还有一条
 * 更自然的路：点开这张图，在底栏那颗心上再点一下。
 */
@Composable
fun GalleryFavoritesBody(
    modifier: Modifier = Modifier,
    /** 顶栏让位。宿主把它交给上面那行二级分段器，所以内容这一层默认 0 —— 两处都给就空一大截。 */
    topPadding: Dp = 0.dp,
    scrollConnection: NestedScrollConnection? = null,
    backdrop: LayerBackdrop? = null,
    onOpenPost: (GalleryPost) -> Unit,
    onScrollStateChange: (canScrollUp: Boolean, hasScrolled: Boolean, scrollToTop: () -> Unit) -> Unit =
        { _, _, _ -> },
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { GalleryFavoritesStore.getInstance(context) }
    val favorites by store.favorites.collectAsState()
    val guard = remember { ContentGuardManager.getInstance(context) }
    val maskMode by guard.nsfwMaskMode.collectAsState()
    // 收藏那一面墙也走**同一把** AI 屏蔽判据（批次 C2）：不同墙各判一次，
    // 就会出"日榜上被 AI 挡掉的那张，在收藏里全裸"这种最难解释的分叉。
    val blockAi by VeneraPreferences.getInstance(context).galleryBlockAi.collectAsState()
    val imageLoader = remember { GalleryImageLoader.get(context) }
    val gridState = rememberLazyStaggeredGridState()
    val wide = isWideScreen(LocalConfiguration.current.screenWidthDp.dp)
    val columnCount = if (wide) 3 else 2

    /**
     * 多选状态机：与本地收藏、图片收藏、网络收藏**同一份**（见 `components/selection/`）。
     * 键取 `uid`（带站键）—— 两站的 id 各自编号，同 id 是两张不同的图，只用 id 会串。
     */
    val multi = rememberMultiSelectState<String>()
    /** 待确认的批量移除；false = 弹层未开。 */
    var removeConfirm by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    // 系统返回先退多选，不要把整个收藏 tab 弹掉。
    BackHandler(enabled = multi.active) { multi.exit() }

    // 档坏了之类的说明只说一次（读走即清）。
    LaunchedEffect(Unit) {
        store.consumeNotice()?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    // 存档 → 卡片墙。三个输入都进 key：收藏变了要重排，用户在设置里改了「成人内容处理」或
    // 画廊那把 AI 屏蔽开关，回到这一屏都必须立刻跟着变（键里少一个就是"改了要重进才生效"的假开关）。
    val wall = remember(favorites, maskMode, blockAi) {
        val posts = favorites.mapNotNull { fav -> fav.site?.let { fav.toPost(it) } }
        buildGalleryWall(posts, maskMode, blockAi = blockAi) { post ->
            guard.findGalleryBlockedRule(author = post.author, tags = post.tagList)?.pattern
        }
    }
    // 墙上这一批的 uid 顺序，区间选要用它。
    val wallUids = remember(wall) { wall.cards.map { it.post.uid } }

    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
        }.collect { hasScrolled ->
            onScrollStateChange(gridState.canScrollBackward, hasScrolled) {
                scope.launch { gridState.animateScrollToItem(0) }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            favorites.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VeneraEmptyView(
                    title = "还没有收藏的画廊图片",
                    message = "在画廊里点开一张图，底栏那颗心点一下就会收到这里",
                    icon = Icons.Outlined.FavoriteBorder,
                )
            }

            // 有收藏、却一张都摆不出来 = 全被「彻底隐藏」或用户规则收起了。
            // 这里必须**如实说成因**，不能退回上面那个"还没有收藏"的空态 —— 用户明明攒了东西。
            wall.cards.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VeneraEmptyView(
                    title = "收藏里的图都被挡下了",
                    message = buildString {
                        if (wall.hiddenByRating > 0) append("${wall.hiddenByRating} 张按「成人内容处理」收起")
                        if (wall.blockedCount > 0) {
                            if (length > 0) append("，")
                            append("${wall.blockedCount} 张命中你的屏蔽规则")
                        }
                    },
                    icon = Icons.Outlined.FavoriteBorder,
                )
            }

            else -> GalleryCardsGrid(
                cards = wall.cards,
                imageLoader = imageLoader,
                columnCount = columnCount,
                state = gridState,
                contentPadding = PaddingValues(
                    start = tokens.spacing.screenHorizontal,
                    end = tokens.spacing.screenHorizontal,
                    top = topPadding,
                    bottom = VeneraSpacing.bottomBarClearance,
                ),
                onOpen = { post ->
                    /*
                     * 交队列这一步**必须有**，与画廊一级那三面墙逐字同一条（用户 2026-10-01 第 4 条）。
                     *
                     * 少了它的后果不是"少个功能"，而是一次**静默的降级**：
                     * 大图页按 uid 去认领"与这一条同墙的那批"，认不出就退回单张模式 ——
                     * 而单张模式下 `pages` 要等自己发一笔 `tags=id:N` 才落地，于是
                     * ① 左右翻页没了；② 去程那个落点（画面框的矩形）在预算内等不到 →
                     * 一律退回"整页抬上来" —— 用户看到的就是"收藏页点图没有飞入"。
                     * 返回程不受影响（它查的是**当前这张**的 uid，与墙无关），
                     * 所以症状恰好是"去程没有、返回有"这个最难解释的样子。
                     *
                     * 交的是 `wall.cards`（已过屏蔽/分级判据的那批）而不是 `favorites`：
                     * 左右翻必须与用户刚才看到的顺序一致，把被挡掉的算进去会出现"翻到一张从没见过的图"。
                     */
                    GalleryViewerQueue.set(wall.cards.map { it.post })
                    onOpenPost(post)
                },
                onLongPress = { post ->
                    // 长按：不在多选态 = 进入多选并选中这一张；已在多选态 = 区间反选。
                    // 区间用的是 **wall.cards**（已过屏蔽/分级判据的那批）的 uid 顺序 ——
                    // 拿 favorites 的顺序会出现"长按一下，选中了一堆屏上根本没有的图"。
                    if (multi.active) multi.toggleRange(post.uid, wallUids) else multi.enter(post.uid)
                },
                selection = if (multi.active) {
                    { post: GalleryPost -> multi.contains(post.uid) }
                } else {
                    null
                },
                onToggleSelect = { post -> multi.toggle(post.uid) },
                modifier = Modifier
                    .then(if (scrollConnection != null) Modifier.nestedScroll(scrollConnection) else Modifier)
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
                header = {
                    FavoritesCountRow(
                        count = favorites.size,
                        hiddenCount = wall.hiddenByRating + wall.blockedCount,
                        onClear = { clearConfirm = true },
                    )
                },
            )
        }
        if (multi.active) {
            // 浮层工具条，与「图片收藏 → 漫藏插图」那条同款同位置（同一个 Box 的底边）。
            VeneraMultiSelectBar(
                selectedCount = multi.count,
                allSelected = wallUids.isNotEmpty() && multi.count == wallUids.size,
                onExit = { multi.exit() },
                onSelectAll = { multi.selectAll(wallUids) },
                onInvert = { multi.invert(wallUids) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = tokens.spacing.screenHorizontal,
                        end = tokens.spacing.screenHorizontal,
                        bottom = tokens.spacing.space8 + VeneraSpacing.bottomBarClearance,
                    ),
            ) {
                MultiSelectBarAction(
                    icon = Icons.Outlined.Delete,
                    label = "移除",
                    destructive = true,
                    onClick = { removeConfirm = true },
                )
            }
        }
    }

    if (removeConfirm) {
        val doomed = multi.selected.toList()
        VeneraDialog(
            show = true,
            onDismissRequest = { removeConfirm = false },
            title = "从画廊收藏移除？",
            content = {
                Text(
                    "${doomed.size} 张会从收藏里移除，图片本身不受影响。",
                    fontSize = tokens.type.body,
                )
            },
            confirmText = "移除",
            confirmDestructive = true,
            onConfirm = {
                removeConfirm = false
                multi.exit()
                // 不弹"已移除"提示：卡片当场从墙上消失，本身就是最清楚的反馈。
                scope.launch { store.removeAll(doomed) }
            },
            dismissText = "取消",
            onDismiss = { removeConfirm = false },
        )
    }

    if (clearConfirm) {
        VeneraDialog(
            show = true,
            onDismissRequest = { clearConfirm = false },
            title = "清空画廊收藏？",
            content = {
                Text(
                    "${favorites.size} 张会从收藏里全部移除，图片本身不受影响。",
                    fontSize = tokens.type.body,
                )
            },
            confirmText = "清空",
            confirmDestructive = true,
            onConfirm = {
                clearConfirm = false
                scope.launch { store.clear() }
            },
            dismissText = "取消",
            onDismiss = { clearConfirm = false },
        )
    }
}

/**
 * 列表头顶那一行：**共有多少张**（以及被挡下多少）+ 清空。
 *
 * 报出"被挡下多少"不是装饰：收藏是用户自己攒的东西，回来一看少了十几张却没有任何说明，
 * 只会让人以为收藏丢了。数字来自 [buildGalleryWall] 的两个成因统计。
 */
@Composable
private fun FavoritesCountRow(count: Int, hiddenCount: Int, onClear: () -> Unit) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = tokens.spacing.space3, bottom = tokens.spacing.space5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (hiddenCount > 0) "共 $count 张 · 已挡下 $hiddenCount 张" else "共 $count 张",
            fontSize = tokens.type.caption,
            color = tokens.color.textSecondary,
        )
        // 用 surfaceContainerHigh 而不是 primaryContainer：清空是破坏性动作，
        // 主色容器是"主行动"的色，摆在那儿会读成"点我"。
        Surface(
            shape = RoundedCornerShape(tokens.shape.large),
            color = tokens.color.surfaceContainerHigh,
            modifier = Modifier.clickable(onClick = onClear),
        ) {
            Text(
                text = "清空",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
                modifier = Modifier.padding(
                    horizontal = tokens.spacing.chipHorizontalPadding,
                    vertical = tokens.spacing.chipVerticalPadding,
                ),
            )
        }
    }
}
