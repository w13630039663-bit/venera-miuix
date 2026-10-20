package com.venera.compose.gallery.ui

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader
import com.venera.compose.components.VeneraEmptyTone
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRecommendations
import com.venera.compose.gallery.domain.galleryNotices
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import com.venera.compose.ui.tokens.LocalBottomBarClearance

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
    /**
     * 这一面墙由谁填、空/忙/页尾各念哪一句（[GalleryHomeWallReading]）。
     *
     * 从前这里摊开的是 `fvm` + `wall` + `favoriteCount` + `onGoToDaily` 四样，而页里五处各读一遍
     * —— 两档内容域共用这一面墙之后，那种摊法会让"屏上是 A 池、页尾念 B 账"成为默认写法。
     */
    reading: GalleryHomeWallReading,
    imageLoader: ImageLoader,
    columnCount: Int,
    state: LazyStaggeredGridState,
    topPadding: Dp,
    backdrop: LayerBackdrop?,
    onOpen: (GalleryPost) -> Unit,
    /** 提示块里那枚「重试」。⚠️ 走调用方那条清熔断 + force 的路径，不是 `fvm.refresh()`（假按钮）。 */
    onRetry: () -> Unit,
    sections: List<GalleryGridSection> = emptyList(),
    /**
     * 常驻 chrome（一条搜索入口条）—— 2026-09-30 用户口径「搜索栏保持在这里就行」：
     * 它是**页面内容的第一行**，跟着内容一起滚走，不再钉在顶栏下方。
     *
     * 放在 [GalleryCardsGrid] 的 header 槽位（比节头还靠前），滚动时它与三节一起离屏。
     * 传 null = 这一面墙不摆（目前只有首页传）。
     */
    chrome: (@Composable () -> Unit)? = null,
    /**
     * 画廊首页头图（2026-10-15，用户「图廊首页也加上和设置页面一样的可以切换的头图」）。
     *
     * 摆在**同一颗 header item 里、chrome 之上**，而不是新插一枚 item —— 后者会撞本仓
     * 两族已量过的坑：懒列表"第 0 项之后插入"的锚定漂移（首页顶栏重叠那次真机根因），
     * 以及"header 必须是单根 Column"（2026-10-01 错误提示压在搜索栏上）。
     * 槽位恒在、判空在调用方（未设置头图时这里就是 null，墙的首帧几何与从前逐像素一致）。
     */
    hero: (@Composable () -> Unit)? = null,
    /**
     * 整页下拉刷新（2026-10-17 第五轮，用户「做整页刷新：下拉刷新，动画在搜索框下面的区域弹性下拉整页」）。
     *
     * 传 null = 这一面墙不接下拉（其余调用点零改动）。
     *
     * ⚠️ 指示器的位置是**下拉行**，不是 M3 那颗浮在顶部的圆环 —— 那颗会跟顶栏/状态栏抢位置
     * （网络收藏那一屏为此撤过它）。这里把行摆在 header 里 **chrome 之后**，
     * 于是它长在"搜索框下面那块区域"，下拉时整页跟着它下移，也就是用户要的弹性。
     */
    onRefresh: (() -> Unit)? = null,
    /** 这一次下拉是否还在刷新。由调用方按"用户下拉发起 + 各腿的 loading 起落"判定。 */
    refreshing: Boolean = false,
    /**
     * 这一面墙上**站方自己起的标题**（`uid → 标题`）。
     *
     * 有的站给标题、有的站不给：booru 三站的 API 都没有这一格，屏上那行是
     * `GalleryCardTitle` 从标签**算**出来的；而 pixiv 的每一行都带 `title`。
     * 空白表 = 与从前逐字一致（只用算出来的那批）。
     *
     * ⚠️ 覆盖而不是"合并成两行"：一张卡只有一个标题格，站方给了真题就不该再摆我们猜的那个。
     */
    stationTitles: Map<String, String> = emptyMap(),
    /**
     * 作者头像（`uid → 地址`，与 [stationTitles] 同一条路径，见 [GalleryCardsGrid]）。
     * 空 map = 不画头像（默认值 ⇒ 搜索墙 / 收藏墙那几处逐像素不变）；
     * 非空 ⇒ 卡片文字区走「头像 + 标题/画师名」两行（2026-10-17 第三十二轮，
     * 见 [GalleryCardCaption]）。
     */
    authorAvatars: Map<String, String> = emptyMap(),
    /**
     * 非空 = 这一面墙上**有一段内容坐在一张卡面上**（推荐那一节，见 [GallerySectionSurface]）。
     *
     * 只是透传给 [GalleryCardsGrid]：卡面的几何与绘制全在那一层，这一页不掺和。
     */
    sectionSurface: GallerySectionSurface? = null,
) {
    val tokens = VeneraTokens
    val cards = reading.wall.cards
    val pageModifier = Modifier
        .fillMaxSize()
        .blurBackdropSource(backdrop)
    // 下拉那一行的高度：由下拉进度 1:1 驱动，松手后停在满高，刷新完成收回。
    // `rememberPullToRefreshState` **无条件**先建（写进 if 里就是条件调用 Composable）。
    val pullState = rememberPullToRefreshState()
    val pullProgress = if (refreshing) 1f else pullState.distanceFraction.coerceIn(0f, 1f)
    val pullRowHeight by animateDpAsState(
        targetValue = tokens.spacing.pullRefreshRowHeight * pullProgress,
        label = "gallery-pull-row-height",
    )
    val contentPadding = PaddingValues(
        start = tokens.spacing.screenHorizontal,
        end = tokens.spacing.screenHorizontal,
        top = topPadding,
        bottom = LocalBottomBarClearance.current,
    )

    // ── 空档与加载档**不再整页换脸**（2026-10-17，用户「现在切换到 pixiv 会这样」+ 截图）──
    //
    // 从前这两档各渲染一个占满页面的居中 Box，把 chrome、头图、以及首页那几节一起摘走 ——
    // 而「图库站 / Pixiv」那颗**域切换就住在节里**（`galleryHomeSectionOrder`）。
    // 于是 Pixiv 域那一轮没回图时用户被**困在 Pixiv**：屏上只剩一句"稍后再刷一次，或者多收藏几张"，
    // 而能带他离开的那排切换器已经不在屏上了。
    // 现在墙只有一条形状：`cards.isEmpty()` 只把"卡片那一格"换成空态或环，其余与有货时逐字同形
    // —— 这也是本仓那条锚定漂移口径（节的数量与形状不许跟着数据变）。
    //
    // IDLE = 还没判过档（第一次组合与那次取数触发之间的一帧）。
    // 摆空态是假读数 —— 那一刻只是"还没算"，不是"没有东西"。
    //
    // ⚠️ `cards.isEmpty()` 这道前提（2026-09-30 第三轮）保留：**换一批**时 `refresh()` 已经不再清
    // `posts`（理由见那里），旧卡片留着、三节不动，"正在换"由猜你喜欢节头那枚环去说 ——
    // 只按 `pending` 判就会在这一刻把脚下的卡换成一个居中环。
    val loadingSlot: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(tokens.spacing.loaderPage),
                color = tokens.color.primary,
                trackColor = tokens.color.surfaceVariant,
            )
        }
    }
    val emptySlot: @Composable () -> Unit = {
        val copy = reading.empty
        VeneraEmptyView(
            title = copy.title,
            message = copy.message,
            tone = copy.tone,
            actionText = copy.actionText,
            onAction = copy.onAction,
        )
    }
    val grid: @Composable () -> Unit = {
        GalleryCardsGrid(
            cards = cards,
            imageLoader = imageLoader,
            columnCount = columnCount,
            state = state,
            contentPadding = contentPadding,
            modifier = pageModifier,
            onOpen = onOpen,
            // 有头图就禁掉过度滚动（2026-10-16，用户「禁止在设置了头图的页面使用过度滚动」）：
            // 到顶继续下拉会把整张头图连内容一起顶下去、上方露出一大片背景，观感很糟。
            //
            // ⚠️ 这一条与下拉刷新**不冲突**，也不许为了下拉把它撤掉：过度滚动是"内容被拉长/拉飞"，
            // 下拉刷新是"父级吃掉这段位移、在 header 里长出一行"——后者不依赖 overscrollEffect。
            disableOverscroll = hero != null,
            stationTitles = stationTitles,
            authorAvatars = authorAvatars,
            sectionSurface = sectionSurface,
            header = {
                // ⚠️ 必须包进一个 Column：懒列表 item 的内容是按「单一根」量的，
                // 这里如果摆两个并列的根级组件（chrome 与失败提示），它们会被当成
                // 同一个 Box 的两个子元素**叠在同一位置画**（2026-10-01 真机缺陷：
                // 错误提示整段压在搜索栏上）。Column 是唯一正确的容器。
                Column {
                    // 头图（未设置 = null，整段不参与，见参数文档）。
                    //
                    // ⚠️ 头图必须**整屏通栏**（2026-10-16，用户「左右还是有空白」）：
                    // 它住在网格的 header item 里，会跟着吃到 `contentPadding` 的左右内缩。
                    // 设置页那边的头图摆在内容列**之外**（见 SettingsHome 的外/内双 Column），
                    // 所以天然通栏；画廊这边它在网格里，只能自己把那圈内缩还回去。
                    // 实测（截图逐像素）：头图左右各留 26px ≈ 11dp，正是 screenHorizontal 那一档。
                    hero?.let { heroSlot ->
                        Box(Modifier.fullBleedHorizontal(tokens.spacing.screenHorizontal)) {
                            heroSlot()
                        }
                    }
                    // 常驻 chrome（一条搜索入口条）是**内容的第一行**：
                    // 随内容滚走（2026-09-30 用户口径「搜索栏保持在这里就行」）。
                    // ⚠️ 它**不**通栏 —— 搜索条左右本来就该有内缩（它是个圆角条，不是整幅图）。
                    chrome?.invoke()
                    // ── 下拉刷新那一行（2026-10-17 第五轮）──
                    //
                    // 位置是**用户点名的那一处**：「动画在搜索框下面的区域」。所以它必须排在
                    // `chrome` 之后、提示块之前 —— 排到 chrome 之前就成了"在搜索框上面"。
                    // 高度 0dp 时不占位，所以屏上有无这一行在静止态**逐像素一致**。
                    if (onRefresh != null) {
                        // ⚠️ 这一行**必须有东西**（2026-10-17 第七轮，用户「下拉没有加载指示器」）：
                        // 前一版只长高、内容为空 —— 下拉时整页确实被推开了一截，但屏上一个转的东西都没有，
                        // 读起来是"页面被我拉歪了"而不是"正在刷新"。行高由手势驱动，环跟着行高淡入，
                        // 所以「刚拉一点点」时它只是隐约一枚、「松手后满高」时才完整。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(pullRowHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (pullProgress > 0f) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier
                                        .size(tokens.spacing.loaderInline)
                                        .alpha(pullProgress.coerceIn(0f, 1f)),
                                    color = tokens.color.primary,
                                    trackColor = tokens.color.surfaceVariant,
                                )
                            }
                        }
                    }
                    // ── 站点筛选栏**下方**那条提示（2026-10-12 改造）──
                    //
                    // 槽位恒在、判空在内部（理由见 [GalleryCardsGrid] 的 header 注释）。
                    // 少了一站必须占一条**可见**的位置：两站混摆静默退化成单源，
                    // 用户只会看到"怎么没有另一站的图"，而原因就在这一块里。
                    //
                    // 从前这里是**一行灰字**（`站：原因 · 站：原因`），三类处境同一张脸、
                    // 且一个能按的东西都没有。现在按档分块：未配账号那一档带一枚
                    // 「去配置账号」（真能到设置 → 画廊），出错那一档带「重试」。
                    //
                    // ⚠️ 重试走 `onRetry`（= 首页那枚「换一批」同一条路径：先清熔断再置 force）：
                    // 直接调 `fvm.refresh()` 撞上 60s 熔断窗口时，按下去必然还是同一句
                    // "熔断中" —— 那是一枚假按钮。
                    GalleryNotices(
                        notices = remember(reading.notices) { galleryNotices(reading.notices) },
                        compact = true,
                        onRetry = onRetry,
                        modifier = Modifier.padding(top = tokens.spacing.space3),
                    )
                }
            },
            sections = sections,
            // 页尾由读数那一档给（图库站 = 猜你喜欢的按站张数/页码；Pixiv = 今天不摆）。
            // 那道「页码还没落地就不摆页尾」的闸，与它为什么在，逐字搬到了 [forYouWallReading] 里。
            //
            // ⚠️ 空/忙两档**占用同一格**：墙上没有卡的时候，"卡片该在的那一格"就是空态或环。
            // 判据用 `cards.isNotEmpty()` 打头而不是把两档写回 `when` 的顶层分支 ——
            // 后者就是刚被撤掉的整页换脸那个形状。
            footer = when {
                cards.isNotEmpty() -> reading.footer
                reading.pending -> loadingSlot
                else -> emptySlot
            },
        )
    }
    if (onRefresh == null) {
        grid()
    } else {
        // `indicator = {}`：M3 那颗圆环浮在整个 Box 的顶上（= 顶栏/状态栏那一带），
        // 与"动画在搜索框下面"正好相反，也跟折叠顶栏抢位置。位置由上面那行自己说了。
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = pullState,
            indicator = {},
            modifier = Modifier.fillMaxSize(),
        ) {
            grid()
        }
    }
}

/**
 * 空/错档要说的那几样。分四档说，见 [emptyCopyOf]。
 *
 * [tone] 管**图标**（见 [VeneraEmptyTone]）：这一页四档里同时有"用户还没做"（还没收藏）与
 * "我们没取成"两种完全不同的处境，而老写法一律摆 `Icons.Outlined.Image` ——
 * 于是"先去收藏几张"与"这一轮没取成"在屏上是同一枚图，只能靠读字才分得开。
 */
internal class ForYouEmptyCopy(
    val title: String,
    val message: String,
    val actionText: String?,
    val onAction: (() -> Unit)?,
    val tone: VeneraEmptyTone,
)

/**
 * **首页那面墙的读数**（2026-10-17：两档内容域共用同一面墙，读数必须各给各的）。
 *
 * 为什么要把它抽出来而不是在页里加一个 `if (domain == PIXIV)`：这一页在**五处**读 `fvm`
 * （加载档、空态四档、按站提示、页尾那 8 项、以及页尾出现与否那道 `page > 0` 闸）。
 * 五处各写一个分支，就是"屏上是 pixiv 那一池、页尾还念着 booru 两站的张数"这种错配的温床 ——
 * 而本仓为这类错配量过太多次。收成一份读数之后，页里只剩"读这一份"，两档的差异全在造读数那两处。
 *
 * ⚠️ [notices] 与 [footer] 不许在页里再算第二遍；[pending] 是"还没判过档"，
 * 拿 `wall.cards.isEmpty()` 单独当判据会把"还没算"说成"没有东西"（从前正是 `stage == IDLE`
 * 与 `isLoading` 两道一起判，那条理由逐字搬进 [forYouWallReading]）。
 */
internal class GalleryHomeWallReading(
    val wall: GalleryWall,
    val pending: Boolean,
    val empty: ForYouEmptyCopy,
    /** 「这一轮哪几站没给内容、各是什么原因」，按站一份。pixiv 那一档是空表 —— 它不按站算。 */
    val notices: Map<GallerySite, String>,
    val footer: (@Composable () -> Unit)?,
)

/**
 * Pixiv 那一档的读数（2026-10-17，用户点名「pixiv 站的推荐应该做成和图库站猜你喜欢那种的」）。
 *
 * 只带这一档**真的知道**的事：种子是哪几枚标签、这一轮取回几张、为什么是空的。两件事刻意不做：
 *  - **不摆页尾** —— 猜你喜欢那串读数（按站各几张 / 第几页 / 排掉几张已收藏）在 pixiv 这一池
 *    一条都成立不了。宁可不念，也不拿别的账凑一句"看起来像读数"的话（那是本仓最忌的假读数）；
 *  - [GalleryHomeWallReading.notices] 恒空 —— 那块提示说的是"哪几站没给内容、各是什么原因"，
 *    而这一池不按站分（种子是标签，取数是一跳一站）。
 *
 * 空态两档脸（同 [emptyCopyOf] 那份"四档不许同脸"的口径）：没种子 = 用户还没干活（[VeneraEmptyTone.NotYet]，
 * 不给按钮）；有种子却没取回 = 我们这一轮没成（[VeneraEmptyTone.Failed] + 一枚真能重发的重试）。
 */
internal fun pixivRecommendReading(
    wall: GalleryWall,
    pending: Boolean,
    seeds: List<String>,
    caption: String?,
    onRetry: () -> Unit,
    /**
     * 这一池来自**站方推荐**（`/v1/illust/recommended`，要登录）还是**种子标签**那一跳（匿名可读）。
     *
     * 为什么必须分两档说：两者的空态不是同一件事 —— 站方推荐空了是"站方这一轮没给你"，
     * 种子空了是"你的标签这一轮没命中"。同一句话会把两种处境读成一种（本仓"四档空态不许同脸"那条）。
     */
    station: Boolean = false,
    /** 瀑布流正在取下一屏（2026-10-17 第七轮：这一池改成无限下拉）。 */
    loadingMore: Boolean = false,
    /** 这一池已经到底（站方没给 `next_url` / 种子那一页没新东西）。 */
    exhausted: Boolean = false,
): GalleryHomeWallReading = GalleryHomeWallReading(
    wall = wall,
    // `pending` 由调用点给"还没判过档 || 正在取"两道的并（口径同图库站那档的 `stage == IDLE`）。
    pending = pending,
    empty = if (station) {
        ForYouEmptyCopy(
            title = "pixiv 这一轮没给你推荐",
            message = caption ?: "站方推荐的答复是空的 —— 再多收藏几张、或过一会儿再刷一次。",
            actionText = "再刷一次",
            onAction = onRetry,
            tone = VeneraEmptyTone.Failed,
        )
    } else if (seeds.isEmpty()) {
        ForYouEmptyCopy(
            title = "还没有能推荐的基础",
            message = caption ?: "先去大图页点收藏几张图，这一栏就照着那些标签去 pixiv 找。",
            actionText = null,
            onAction = null,
            tone = VeneraEmptyTone.NotYet,
        )
    } else {
        ForYouEmptyCopy(
            title = "pixiv 这一轮没有回图",
            message = (caption ?: "种子那几枚标签（「${seeds.joinToString("、")}」）这一轮没从 pixiv 取回图。") +
                "\n稍后再刷一次，或者多收藏几张换换口味。",
            actionText = "再刷一次",
            onAction = onRetry,
            tone = VeneraEmptyTone.Failed,
        )
    },
    notices = emptyMap(),
    // 页尾只说**这一池自己知道的事**（还有没有下一屏）。猜你喜欢那串按站读数在这一池
    // 一条都成立不了，所以这里不复用它 —— 但"到底了 / 正在取"是这一池真有的两档。
    footer = if (wall.cards.isEmpty()) {
        null
    } else {
        @Composable { PixivRecommendEnd(loadingMore = loadingMore, exhausted = exhausted) }
    },
)

/**
 * 推荐那面墙的页尾（2026-10-17 第七轮，无限下拉配套）。
 *
 * 只说两件事：正在取下一屏 / 已经到底。**没有"上滑继续取"那一句** —— 无限下拉是滑到底
 * 自动发的，不像搜索页要用户再滑一下。
 */
@Composable
private fun PixivRecommendEnd(loadingMore: Boolean, exhausted: Boolean) {
    val tokens = VeneraTokens
    val line = when {
        loadingMore -> "正在取下一屏…"
        exhausted -> "pixiv 这一池到底了"
        // 还有下一页但还没滑到：**不念**"上滑继续取"（那是搜索页的手动口径，这里自动发）。
        else -> null
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (line != null) {
            Text(
                text = line,
                fontSize = tokens.type.overline,
                color = tokens.color.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 图库站那一档（猜你喜欢）的读数。
 *
 * 里面的每一条判据都是**从前 [GalleryForYouPage] 函数体里逐字搬过来的**，一个字没改：
 *  - `stage == IDLE || isLoading` 那道加载闸（摆空态就是假读数）；
 *  - 四档空态各说各的话（见 [emptyCopyOf] 那份清单）；
 *  - 页尾多一道 `page > 0`（2026-09-30 第三轮：「换一批」之后 `posts` 留着上一批而页码已清，
 *    那一刻页尾会念出「已摆出 40 张（Yande.re 0 · Gelbooru 0）」——卡明明在屏上）。
 */
internal fun forYouWallReading(
    fvm: GalleryForYouViewModel,
    wall: GalleryWall,
    favoriteCount: Int,
    onRetry: () -> Unit,
    onGoToDaily: (() -> Unit)?,
): GalleryHomeWallReading = GalleryHomeWallReading(
    wall = wall,
    pending = fvm.stage == GalleryForYouStage.IDLE || fvm.isLoading,
    empty = emptyCopyOf(fvm, wall, favoriteCount, onRetry, onGoToDaily),
    notices = fvm.failures,
    footer = if (fvm.posts.isNotEmpty() && fvm.page > 0) {
        @Composable {
            GalleryForYouEnd(
                cards = wall.cards.size,
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
        tone = VeneraEmptyTone.NotYet,
    )

    GalleryForYouStage.NO_USABLE_TAGS -> ForYouEmptyCopy(
        title = "标签都被你的规则挡了",
        message = "$favoriteCount 张收藏里抽出来的标签，全部命中了你的屏蔽规则 —— " +
            "图站的标签是 long_hair、tail 这类下划线写法，短关键字很容易整站命中。" +
            "把那条规则收掉或改长，这里才会有推荐。",
        actionText = null,
        onAction = null,
        // "去改那条规则"仍是一条**用户要做的事**，所以归 NotYet 而不是 Failed：
        // 我们这一笔取数没出错，规则也确实按他写的生效了。
        tone = VeneraEmptyTone.NotYet,
    )

    else -> when {
        fvm.error != null -> ForYouEmptyCopy(
            title = "这一轮没取成",
            message = fvm.error.orEmpty(),
            tone = VeneraEmptyTone.Failed,
            actionText = "再刷一次",
            // 走调用方那枚 onRetry：它会先清域名熔断再置 force。
            // 直接 fvm.refresh() 的话，撞上 60s 熔断窗口时这一按必然还是同一句"熔断中"，是假按钮。
            onAction = onRetry,
        )

        fvm.excludedFavourite > 0 -> ForYouEmptyCopy(
            title = "能推的都收藏过了",
            message = "这一轮排掉了 ${fvm.excludedFavourite} 张你已经收藏过的图，剩下的没有可摆的。" +
                "多看几张、再收藏几张，这里会长出新东西。",
            // "真的没有可摆的"：这一档是前面几层判据都跑通了的结果，
            // 既不是我们没取成，也不是用户还没做。
            tone = VeneraEmptyTone.Nothing,
            // 2026-09-30 第二轮起这一档多半没有出口了：首页那条主墙恒为猜你喜欢，
            // 没有"另一面墙"可切（热门搬去了二级页）。`onGoToDaily` 传 null 时不摆按钮 ——
            // 这是刻意的：按下去只是把同一面墙重滚一次，那是一枚假按钮。
            actionText = "去看每日热门".takeIf { onGoToDaily != null },
            onAction = onGoToDaily,
        )

        else -> ForYouEmptyCopy(
            title = "这些标签没有可摆的图",
            tone = VeneraEmptyTone.Nothing,
            message = listOfNotNull(
                fvm.queryBySite.takeIf { it.isNotEmpty() }?.let { queries ->
                    "发出去的条件：" + queries.entries.joinToString(" · ") { "${it.key.displayName}「${it.value}」" }
                },
                fvm.failures.takeIf { it.isNotEmpty() }?.let { failures ->
                    failures.entries.joinToString(" · ") { "${it.key.displayName}：${it.value}" }
                },
                wall.blockedRules.takeIf { it.isNotEmpty() }?.let { "命中屏蔽规则 ${it.joinToString("、")}" },
            ).joinToString("\n").ifBlank { "两站这一轮都没有给出可摆的条目" },
            actionText = "再刷一次",
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
                // 只念**这一轴真问过的站**（发车名单唯一的一份在 domain）：写 `GallerySite.entries`
                // 会让屏上多出一格"Pixiv 0"，那读起来是"问过它、它回了 0 条"，而真话是这一轴不问它。
                append(GalleryRecommendations.RECOMMEND_LEG_SITES.joinToString(" · ") { "${it.displayName} ${perSite[it] ?: 0}" })
                if (excludedFavourite > 0) append("；已排除 $excludedFavourite 张你收藏过的图")
                if (blockedCount > 0) append("；另有 ${blockedByRulesFragment(blockedCount, blockedRules)}")
                if (hiddenByRating > 0) append("；${ratingHiddenFragment(hiddenByRating)}")
                // 失败档说空串，原因与重试由下面那行说 —— 两头都说就是两行重复读数。
                append(feedTailStatus(loadingMore, exhausted, loadMoreError != null))
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
        GalleryLoadMoreRetryLine(loadMoreError, onRetryLoadMore)
    }
}

/**
 * 让这一格**吃掉容器的左右内缩**，按整屏宽铺开（只在本容器水平居中、两侧等宽时成立）。
 *
 * 用在哪：首页头图。它住在网格的 header item 里，而网格的 `contentPadding` 是**所有项共用**
 * 的（搜索条、卡片那几行都靠它留出左右边距），所以只能由头图自己把这个内缩还回去。
 *
 * 做法：把宽度约束放到 `内容宽 + 2 × inset`，量完之后按 `-inset` 摆 —— 报告的高度仍是子元素的
 * 高度，所以网格那一行的占位**一点没变**（变的只有绘制范围）。这跟 `SettingsHeroImage` 内部
 * 「占位矮一截、画面画全高」是同一类手法：**报告的尺寸与绘制范围解耦**。
 *
 * ⚠️ 不要改用 `.padding(horizontal = -inset)`：`Modifier.padding` 对负值有 `require` 检查，会崩。
 *
 * @param inset 容器左右各自的内缩（本仓口径 = `tokens.spacing.screenHorizontal`）。
 */
private fun Modifier.fullBleedHorizontal(inset: Dp): Modifier = layout { measurable, constraints ->
    val insetPx = inset.roundToPx()
    // 容器宽未知（无限约束）时不做任何事，宁可少通栏也不要量出一个荒谬的宽度。
    if (insetPx <= 0 || constraints.maxWidth == androidx.compose.ui.unit.Constraints.Infinity) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val fullWidth = constraints.maxWidth + insetPx * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = fullWidth, maxWidth = fullWidth)
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-insetPx, 0)
    }
}
