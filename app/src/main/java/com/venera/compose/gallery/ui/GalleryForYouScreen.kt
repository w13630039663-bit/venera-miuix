package com.venera.compose.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 「猜你喜欢」那面墙（从**用户自己的收藏**里抽标签，再拿那串标签去站方搜）。
 *
 * 批次 K 之前它是同一目的地里的第 1 页（左右滑切换）；合一屏之后它是**详情区的一档**，
 * 由节点头的「查看全部」切进来，见 [GalleryWallFeed]。
 *
 * 判据全部在 `GalleryRecommendations`（纯函数、有单测）与 [GalleryForYouMerge]（分页合并），
 * 这一层只做"屏上摆哪一档"的选择。四档空态**必须不同脸**（方案 §四.8）：
 * 合成一个"空"就会让"你还没收藏"和"我们的判据算不出东西"长成同一张脸 ——
 * 前者该引导去收藏，后者是我们的缺陷，得能炸出来。
 */
@Composable
internal fun GalleryForYouPage(
    fvm: GalleryForYouViewModel,
    wall: GalleryWall,
    favoriteCount: Int,
    imageLoader: ImageLoader,
    columnCount: Int,
    state: LazyStaggeredGridState,
    topPadding: Dp,
    backdrop: LayerBackdrop?,
    onOpen: (GalleryPost) -> Unit,
    onRetry: () -> Unit,
    /**
     * 空态里那枚「去看每日热门」。
     *
     * ⚠️ 2026-09-30 第二轮起首页**可以传 null**：那条主墙恒为猜你喜欢，首页没有"另一面墙"
     * 可切（每日热门搬去了独立二级页），所以这个动作在首页是"把同一面墙重滚一次"——
     * 传 null 即为"不摆这枚按钮"，而不是摆一个按下去没反应的假按钮。
     */
    onGoToDaily: (() -> Unit)?,
    sections: List<GalleryGridSection> = emptyList(),
    /**
     * 常驻 chrome（搜索入口条 + 来源分段器）—— 2026-09-30 用户口径「搜索栏保持在这里就行」：
     * 它是**页面内容的第一行**，跟着内容一起滚走，不再钉在顶栏下方。
     *
     * 放在 [GalleryCardsGrid] 的 header 槽位（比节头还靠前），滚动时它与三节一起离屏。
     * 传 null = 这一面墙不摆（目前只有首页传）。
     */
    chrome: (@Composable () -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val cards = wall.cards
    val pageModifier = Modifier
        .fillMaxSize()
        .blurBackdropSource(backdrop)
    val contentPadding = PaddingValues(
        start = tokens.spacing.screenHorizontal,
        end = tokens.spacing.screenHorizontal,
        top = topPadding,
        bottom = VeneraSpacing.bottomBarClearance,
    )

    when {
        // IDLE = 还没判过档（第一次组合与那次取数触发之间的一帧）。
        // 摆空态是假读数 —— 那一刻只是"还没算"，不是"没有东西"。
        //
        // ⚠️ 加了 `cards.isEmpty()` 这道前提（2026-09-30 第三轮）：**整屏**波浪环只在
        // 屏上一张卡都没有时才该出现。用户点「换一批」时 `refresh()` 已经不再清 `posts`
        // （理由见那里），旧卡片留着、三节不动，"正在换"由猜你喜欢节头那枚环去说 ——
        // 旧写法会在这一刻把三节一起换成一个居中环，读起来就是"整页刷新"。
        cards.isEmpty() && (fvm.stage == GalleryForYouStage.IDLE || fvm.isLoading) -> Box(
            pageModifier,
            contentAlignment = Alignment.Center,
        ) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(tokens.spacing.loaderPage),
                color = tokens.color.primary,
                trackColor = tokens.color.surfaceVariant,
            )
        }

        cards.isEmpty() && !fvm.isLoading -> {
            val copy = emptyCopyOf(fvm, wall, favoriteCount, onRetry, onGoToDaily)
            Box(
                pageModifier.padding(horizontal = tokens.spacing.screenHorizontal),
                contentAlignment = Alignment.Center,
            ) {
                VeneraEmptyView(
                    title = copy.title,
                    message = copy.message,
                    icon = Icons.Outlined.Image,
                    actionText = copy.actionText,
                    onAction = copy.onAction,
                )
            }
        }

        else -> GalleryCardsGrid(
            cards = cards,
            imageLoader = imageLoader,
            columnCount = columnCount,
            state = state,
            contentPadding = contentPadding,
            modifier = pageModifier,
            onOpen = onOpen,
            header = {
                // 常驻 chrome（搜索入口条 + 来源分段器）是**内容的第一行**：
                // 随内容滚走（2026-09-30 用户口径「搜索栏保持在这里就行」）。
                chrome?.invoke()
                // 槽位恒在、判空在内部（理由见 [GalleryCardsGrid] 的 header 注释）。
                // 少了一站必须占一条**可见**的位置：两站混摆静默退化成单源，
                // 用户只会看到"怎么没有另一站的图"，而原因就在这一行里。
                fvm.failures.takeIf { it.isNotEmpty() }?.let { failures ->
                    Text(
                        text = failures.entries.joinToString(" · ") { "${it.key.displayName}：${it.value}" },
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spacing.space3),
                    )
                }
            },
            sections = sections,
            // ⚠️ 多加一道 `page > 0`（2026-09-30 第三轮）：「换一批」之后 `posts` 留着上一批、
            // 而 `page` / `perSite` / `excludedFavourite` 都清了（它们是新第一批到货时**累加**的，
            // 不清就会把上一批的张数算进新一批）。这个窗口里页尾会念出
            // 「已摆出 40 张（Yande.re 0 · Gelbooru 0）」—— 卡片明明在屏上，读数却说两站各 0 张，
            // 是典型的假读数。`page == 0` 就等于"这批读数还没落地"，那时整段页尾不摆。
            footer = if (fvm.posts.isNotEmpty() && fvm.page > 0) {
                @Composable {
                    GalleryForYouEnd(
                        cards = cards.size,
                        queryBySite = fvm.queryBySite,
                        perSite = fvm.perSite,
                        page = fvm.page,
                        videos = fvm.videos,
                        excludedFavourite = fvm.excludedFavourite,
                        blockedCount = wall.blockedCount,
                        blockedRules = wall.blockedRules,
                        hiddenByRating = wall.hiddenByRating,
                        exhausted = fvm.exhausted,
                        loadingMore = fvm.isLoadingMore,
                        loadMoreError = fvm.loadMoreError,
                        onRetryLoadMore = { fvm.retryLoadMore() },
                    )
                }
            } else {
                null
            },
        )
    }
}

/** 空/错档要说的那三样。分四档说，见 [emptyCopyOf]。 */
private class ForYouEmptyCopy(
    val title: String,
    val message: String,
    val actionText: String?,
    val onAction: (() -> Unit)?,
)

/**
 * 每一档"屏上为什么没有图"要说不同的话：
 *
 * - **NO_SEEDS**：用户还没收藏 → 引导去收藏，不许写成技术错误；
 * - **NO_USABLE_TAGS**：收藏了，但抽出来的标签全被黑名单吃掉 → **这是我们的判据链的效果**，
 *   说成"快去收藏"就是拿"用户没干活"替自己遮丑；
 * - **error**：这一轮真的没取成 → 给一枚真能重发的重试；
 * - **全被"已收藏"剔空**：口味已经全收完了。这既不是用户没干活也不是我们算不出，
 *   所以第四档单独存在，并报出排掉了几张（方案 §三那个"复读页"问题的可见面）。
 *
 * 最后一档 `else`（站方给了行但没有可摆的图 / 两站都空手而归却报不上原因）
 * 刻意**不说"没有图"**，而是把发出去的条件与缺席的站念出来 —— 读数不对，用户就没法判断该动什么。
 */
private fun emptyCopyOf(
    fvm: GalleryForYouViewModel,
    wall: GalleryWall,
    favoriteCount: Int,
    onRetry: () -> Unit,
    onGoToDaily: (() -> Unit)?,
): ForYouEmptyCopy = when (fvm.stage) {
    GalleryForYouStage.NO_SEEDS -> ForYouEmptyCopy(
        title = "还没有能推荐的基础",
        message = "先去大图页点收藏几张图，这一屏就照着你的口味找。" +
            "现在两站都还没有收藏，抽不出标签来。",
        actionText = null,
        onAction = null,
    )

    GalleryForYouStage.NO_USABLE_TAGS -> ForYouEmptyCopy(
        title = "标签都被你的规则挡了",
        message = "$favoriteCount 张收藏里抽出来的标签，全部命中了你的屏蔽规则 —— " +
            "图站的标签是 long_hair、tail 这类下划线写法，短关键字很容易整站命中。" +
            "把那条规则收掉或改长，这里才会有推荐。",
        actionText = null,
        onAction = null,
    )

    else -> when {
        fvm.error != null -> ForYouEmptyCopy(
            title = "这一轮没取成",
            message = fvm.error.orEmpty(),
            actionText = "重试",
            // 走调用方那枚 onRetry：它会先清域名熔断再置 force。
            // 直接 fvm.refresh() 的话，撞上 60s 熔断窗口时这一按必然还是同一句"熔断中"，是假按钮。
            onAction = onRetry,
        )

        fvm.excludedFavourite > 0 -> ForYouEmptyCopy(
            title = "能推的都收藏过了",
            message = "这一轮排掉了 ${fvm.excludedFavourite} 张你已经收藏过的图，剩下的没有可摆的。" +
                "多看几张、再收藏几张，这里会长出新东西。",
            // 2026-09-30 第二轮起这一档多半没有出口了：首页那条主墙恒为猜你喜欢，
            // 没有"另一面墙"可切（热门搬去了二级页）。`onGoToDaily` 传 null 时不摆按钮 ——
            // 这是刻意的：按下去只是把同一面墙重滚一次，那是一枚假按钮。
            actionText = "去看每日热门".takeIf { onGoToDaily != null },
            onAction = onGoToDaily,
        )

        else -> ForYouEmptyCopy(
            title = "这些标签没有可摆的图",
            message = listOfNotNull(
                fvm.queryBySite.takeIf { it.isNotEmpty() }?.let { queries ->
                    "发出去的条件：" + queries.entries.joinToString(" · ") { "${it.key.displayName}「${it.value}」" }
                },
                fvm.failures.takeIf { it.isNotEmpty() }?.let { failures ->
                    failures.entries.joinToString(" · ") { "${it.key.displayName}：${it.value}" }
                },
                wall.blockedRules.takeIf { it.isNotEmpty() }?.let { "命中屏蔽规则 ${it.joinToString("、")}" },
            ).joinToString("\n").ifBlank { "两站这一轮都没有给出可摆的条目" },
            actionText = "重试",
            onAction = onRetry,
        )
    }
}

/**
 * 猜你喜欢那一面的页尾。
 *
 * 样式与读数口径照 `GallerySearchEnd`（overline + textTertiary + 居中，
 * 「已经到底 / 上滑继续取 / 正在取下一页」三档互斥），只多一件事：
 * **报出排掉了几张已收藏的图** —— 屏上比站方给的少是刻意的，不报出来就是静默交错。
 *
 * 各站张数**含 0 也照报**（同 `GalleryFeedEnd` 那条）：某一站被规则清空时，
 * 只报总数就等于把唯一线索抹掉。
 */
@Composable
private fun GalleryForYouEnd(
    cards: Int,
    queryBySite: Map<GallerySite, String>,
    perSite: Map<GallerySite, Int>,
    page: Int,
    videos: Int,
    excludedFavourite: Int,
    blockedCount: Int,
    blockedRules: List<String>,
    hiddenByRating: Int,
    exhausted: Boolean,
    loadingMore: Boolean,
    loadMoreError: String?,
    onRetryLoadMore: () -> Unit,
) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = buildString {
                append("按你的收藏已摆出 $cards 张")
                if (page > 1) append(" · 第 $page 页")
                if (videos > 0) append(" · 含 $videos 个视频")
                append("（")
                append(GallerySite.entries.joinToString(" · ") { "${it.displayName} ${perSite[it] ?: 0}" })
                if (excludedFavourite > 0) append("；已排除 $excludedFavourite 张你收藏过的图")
                if (blockedCount > 0) append("；另有 $blockedCount 张命中屏蔽规则 ${blockedRules.joinToString("、")}")
                if (hiddenByRating > 0) append("；$hiddenByRating 张按「成人内容处理」收起")
                append(
                    when {
                        loadingMore -> "；正在取下一页"
                        exhausted -> "；已经到底"
                        // 失败时**不能**再说"上滑继续取"：此刻怎么滑都不会再发请求，那是假读数。
                        loadMoreError != null -> ""
                        else -> "；上滑继续取"
                    },
                )
                append("）")
            },
            fontSize = tokens.type.overline,
            color = tokens.color.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        queryBySite.entries.joinToString(" · ") { "${it.key.displayName}「${it.value}」" }
            .takeIf { it.isNotBlank() }?.let { queries ->
                Spacer(modifier = Modifier.height(tokens.spacing.space3))
                Text(
                    text = "条件：$queries",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        if (loadMoreError != null) {
            Spacer(modifier = Modifier.height(tokens.spacing.space3))
            Text(
                text = "下一页没取到：$loadMoreError",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(tokens.spacing.space2))
            VeneraChip(
                text = "重试",
                variant = VeneraChipVariant.Assist,
                onClick = onRetryLoadMore,
            )
        }
    }
}
