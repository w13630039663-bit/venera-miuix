package com.venera.compose.gallery.ui

import android.widget.Toast
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

    /** 待确认移除的那一张；null = 弹层未开。 */
    var pendingRemove by remember { mutableStateOf<GalleryPost?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }

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
                onOpen = onOpenPost,
                onLongPress = { pendingRemove = it },
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
    }

    pendingRemove?.let { post ->
        VeneraDialog(
            show = true,
            onDismissRequest = { pendingRemove = null },
            title = "从画廊收藏移除？",
            content = {
                Text(
                    "${post.site.displayName} #${post.id}" +
                        if (post.author.isBlank()) "" else "\n${post.author}",
                    fontSize = tokens.type.body,
                )
            },
            confirmText = "移除",
            confirmDestructive = true,
            onConfirm = {
                val uid = post.uid
                pendingRemove = null
                // 不弹"已移除"提示：卡片当场从墙上消失，本身就是最清楚的反馈。
                scope.launch { store.remove(uid) }
            },
            dismissText = "取消",
            onDismiss = { pendingRemove = null },
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
