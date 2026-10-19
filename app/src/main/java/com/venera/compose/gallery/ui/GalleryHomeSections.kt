package com.venera.compose.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.ImageLoader
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.venera.HScrollEndSpacer
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.VeneraCoverShimmer
import com.venera.compose.components.venera.VeneraPressFeedback
import com.venera.compose.components.venera.hScrollEndContentPadding
import com.venera.compose.components.venera.hScrollEndFade
import com.venera.compose.components.wideScreenLayoutMode
import com.venera.compose.feature.MiuixSectionHeader
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.FollowedArtistRef
import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.gallery.data.PixivCredentials
import com.venera.compose.gallery.data.PixivRankingMode
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.gallery.data.PixivWork
import com.venera.compose.gallery.domain.GalleryFollowedArtists
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.domain.GalleryFollowedArtistsGap
import com.venera.compose.gallery.domain.GalleryFollowedArtistsReadout
import com.venera.compose.gallery.domain.GalleryWallFeed
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/** 墙首的一节：key 定死（它是 grid 的 item key），内容随状态换。 */
internal data class GalleryGridSection(val key: String, val content: @Composable () -> Unit)

/**
 * 首页那几节的 **key** —— 与 [GALLERY_HOME_SECTION_ORDER] 一起构成"节的清单"这唯一一份事实源。
 *
 * 为什么要有这两样（而不是像以前那样在函数体里就地 `listOf(...)`）：
 *
 * 1. **key 会进 `item(key = …)`**，重复的 key 会让 `LazyStaggeredGrid` **直接崩**。
 *    以前那是"看着不会重复"的字符串字面量，现在它是一份可单测的清单（本项目没有 Robolectric，
 *    composable 测不到，所以判据必须落在常量上）。
 * 2. **对照新版效果图的模块次序**是一次具体的改动，而"顺序"这种东西散在函数体里就没人守得住。
 *    收成一份常量之后，重排只改一行，而"加了一节却忘了改清单"是**编译期**错误
 *    （见 [galleryHomeSections] 里那个无 `else` 的 `when`）。
 */
internal object GalleryHomeSectionKey {
    const val ARTISTS = "section-artists"
    const val DAILY = "section-daily"
    const val FOR_YOU = "section-for-you"
    // 内容域那一排**不在这里**（2026-10-17 第三轮，用户稿「看下站源切换能不能做成这样」）：
    // 它从前是 `section-domain` 自己占一行，现在住进搜索条左边那一格（见 `GalleryHomeChrome`）。
    const val PIXIV_ARTISTS = "section-pixiv-artists"
    const val PIXIV_RANKING = "section-pixiv-ranking"
    const val PIXIV_RECOMMEND = "section-pixiv-recommend"
}

/**
 * 三节的**次序**，就是屏上自上而下的次序。
 *
 * 2026-09-30 第二轮（用户点名）从五节收到三节：
 * - 「根据你的收藏」那一栏**整节撤下**（用户：「去掉根据你的收藏这个栏」）。
 *   推荐标签本身没消失 —— 它仍是**猜你喜欢**那面墙的取数种子，也是搜索卡空框时那一行入口；
 *   撤掉的只是首页这排展示栏。
 * - 「最近搜索」**搬回搜索卡**（用户：「最近搜索放回点击搜索框后下面框框着」）——
 *   同一份历史只该在全站出现一处，而它的常态位置是"用户已经决定要搜"的那一刻。
 *
 * ⚠️ 节的**数量与次序都不许跟着数据变**：懒列表在第 0 项之后插一个新项会让视口锚点漂到下一项，
 * 内容于是被画进 `contentPadding` 里（首页顶栏重叠那次真机缺陷的真根因，见记忆
 * 「首页顶栏重叠的真根因：锚定漂移」）。所以三节**恒在**，"这一节此刻有没有货"由项内部判。
 */
/**
 * 节的**次序** —— 2026-10-17 起它是"内容域"的纯函数（用户口径：「不是替换，是切换到 pixiv 的话
 * 页面就换成这些排布，图库站保持不变」）。原来这里是**一颗常量**。
 *
 * ⚠️ [GalleryHomeDomain.BOORU] 那一档逐字就是从前那三节原样 ——
 *   判据在 `GalleryHomeSectionsTest` 里钉着那条字面量，别"顺手重排"。
 * ⚠️ 上面那条"节的数量与次序不许跟着**数据**变"的红线仍然守；它现在跟着**用户选中的域**变，
 *   那是人的动作不是数据。两档**节数相等**（都是三节）正是为此：切域时不会有"多出来的一节
 *   插在第 0 项之后"那一族锚点漂移，剩下的"内容换了"用回到顶部压掉（见 GalleryScreen 那一笔）。
 *
 * 第三轮（同日）把内容域那一排从这里**摘了出去**：两档各剩三节，仍然相等 —— 摘的是同一枚 key，
 * 所以不是"某一档少一节"那种会漂锚点的形状。
 */
internal fun galleryHomeSectionOrder(domain: GalleryHomeDomain): List<String> = when (domain) {
    GalleryHomeDomain.BOORU -> listOf(
        GalleryHomeSectionKey.ARTISTS,
        GalleryHomeSectionKey.DAILY,
        GalleryHomeSectionKey.FOR_YOU,
    )

    GalleryHomeDomain.PIXIV -> listOf(
        GalleryHomeSectionKey.PIXIV_ARTISTS,
        GalleryHomeSectionKey.PIXIV_RANKING,
        GalleryHomeSectionKey.PIXIV_RECOMMEND,
    )
}

/**
 * Pixiv 域那三节要的东西（一袋参数，不给 [galleryHomeSections] 添十颗位置参数）。
 *
 * 刻意不是 data class、也不 `remember`：这一袋里读的都是快照状态，记错键就会拿着旧值画新状态
 * （与那颗"这个列表每帧重建"同一条理由）。
 */
internal class PixivHomeState(
    val rankingMode: PixivRankingMode,
    val ranking: List<PixivWork>,
    val rankingLoading: Boolean,
    val rankingExpanded: Boolean,
    /**
     * 这一张要不要糊起来。**由调用点用 buildGalleryWall 那同一把算**，这里不另立判式 ——
     * 否则会长出"预览行上没被挡、主墙上全裸"那族分叉（首页那三批卡早就为此统一过一次）。
     * 2026-10-17 之后只剩排行榜那一排吃它：推荐那一池改走首页主墙，打码在墙里自己算。
     * 否则会长出"预览行上没被挡、主墙上全裸"那族分叉（首页那三批卡早就为此统一过一次）。
     */
    val maskedOf: (GalleryPost) -> Boolean,
    val onPickRankingMode: (PixivRankingMode) -> Unit,
    val onToggleRankingExpanded: () -> Unit,
    /** 点开一张卡。**整池要一起交出去** —— 大图页左右滑吃的是这一池：
     *  排行榜那排点进去却把推荐的池子设进队列，读起来就是「卡上这张、滑过去全是别的」。
     */
    val onOpen: (List<PixivWork>, GalleryPost) -> Unit,
    // 内容域那一排的回调**不在这一袋里了**（第三轮）：那一排住在搜索条左边，
    // 由 `GalleryHomeChrome` 自己收 `onPickDomain`，节这一层不再碰它。
    /**
     * 顶栏那颗头像背后的同一个账号件（2026-10-17 账号系统）。
     *
     * 「关注画师」那一节要按**登录与否**念两句话，而登录态只有 [PixivCredentials.authState]
     * 这一个来源 —— 在这里另算一遍"看着像登过"就是第二把尺子。null = 这一侧没接账号件。
     */
    val credentials: PixivCredentials?,
    /**
     * 「推荐」那一节的**来源备注**（2026-10-17 第五轮）。
     *
     * 只在"这一池不是站方推荐"时非空 —— 那时屏上必须说得出为什么（没登录 ⇒ 走的是按收藏标签
     * 那一跳）。**站方推荐正常时不摆话**：一池真推荐不需要再解释自己是谁。
     */
    val recommendNote: String? = null,
    /**
     * 排行榜那三档（有货 / 在取 / 空）里**空那一档**要说的话。
     *
     * 加它的理由（2026-10-17 第七轮，用户截图）：那一档原先写死一句「pixiv 这一档这一轮没有答上」，
     * 而取数那边把异常 `.getOrNull().orEmpty()` 吞掉了 —— 于是"站方限流了""票据过期了"
     * "这一档真的没图"三种处境在屏上是同一句话。现在由取数那一侧把站方原话交上来。
     */
    val rankingCaption: String? = null,
    /**
     * 自己关注的画师（`/v1/user/following`）与站方推荐用户（`/v1/user/recommended`）。
     *
     * 两栏**分开放**（2026-10-17 第八轮，用户「也加上没用关注的推荐用户，但要区分开」）：
     * 合成一排的话，屏上读不出"哪些是我真关注的"，那是这一节最容易产生的误解。
     * 空表 + 不是 loading 时才摆那句话 —— 那时"没人"是真的没人。
     */
    val following: List<PixivUser> = emptyList(),
    val recommendedUsers: List<PixivUser> = emptyList(),
    val artistsLoading: Boolean = false,
    /** 关注那一栏取数失败时站方/本地那句话；null = 没失败。 */
    val followingCaption: String? = null,
    /** 推荐那一栏同上。两栏各存各的：一条腿坏了不该把另一条也说成坏。 */
    val usersCaption: String? = null,
    /** 点一位画师。 */
    val onOpenUser: (PixivUser) -> Unit = {},
    /**
     * 「画师」那一格当前摆哪一半（2026-10-17 第九轮：关注 / 发现合并成一节两个 Tab）。
     *
     * ⚠️ 它与上面那两份数据**不是**一回事：数据是同时取的、各存各的；这个只是"摆哪一份"。
     * 状态住在 `GalleryScreen`（与其余首页状态同层），不在这里 remember ——
     * 那样切 Tab 回来会重置成"已关注"，而用户上一次明明切去了「发现」。
     */
    val artistTab: PixivArtistTab = PixivArtistTab.FOLLOWING,
    val onPickArtistTab: (PixivArtistTab) -> Unit = {},
    /**
     * 排行榜卡右下那颗 ♥（2026-10-17 第十八轮，用户稿）。
     *
     * 走**本地收藏档**（与收藏页、大图页那颗 ♥ 同一份数据），不是 pixiv 站方的收藏 ——
     * 站方收藏要另一条带票据的写接口，而"我在这里点一下心"在本仓的既有语义就是存进本地。
     * 默认 `null` ⇒ 不画（不给假把手）；[favoritedOf] 也只在这条路径上有意义。
     */
    val favoritedOf: (GalleryPost) -> Boolean = { false },
    val onToggleFavorite: ((GalleryPost) -> Unit)? = null,
    /**
     * 「换一批」现在**能不能摆**（2026-10-17 第二十三轮，用户「推荐标题右边要多个换一批的胶囊」）。
     *
     * 默认 `false` 是有意的兜底：漏接就变成"不摆"，而不会变成"摆一枚按了没反应的"。
     * 判定只有一条 —— **登录态**。已登录走站方推荐 `/v1/illust/recommended`，那一腿回 `next_url`，
     * 取下一页就是一批真新图；匿名走种子那一跳，重取同一枚种子还是同一批，属于假把手。
     *
     * 「站方推荐已经翻到底」（`next_url` 缺席）也算不能摆：那一按没有下一页可取。
     */
    val recommendRemixable: Boolean = false,
    /** 换批期间为真 —— 胶囊念「正在换」并禁按（不换文案也不禁按 = 按下去像卡住了）。 */
    val recommendRemixLoading: Boolean = false,
    val onRemixRecommend: () -> Unit = {},
)

/** 横卡一行摆几枚。与画师行同一档，超出的那几枚由节点头念出来（读数须真）。 */
private const val ROW_ITEMS = GalleryFollowedArtists.ROW_SIZE

/** 骨架比实卡少几枚：它只负责"这里马上有东西"，不必铺满一屏宽。 */
private const val ROW_SKELETON_ITEMS = 4

@Composable
private fun GalleryRowCaption(text: String) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.caption,
        color = tokens.color.textTertiary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space4),
    )
}

/**
 * 一行的骨架占位。
 *
 * 为什么不能"取到再画"：那一行第一次到货时会**插在用户正在看的位置上面**，整屏往下顶一格 ——
 * 现象读起来像"页面自己跳了"。骨架先把高度占住，到货只是把内容填进去。
 *
 * ⚠️ 每一格必须套**与实卡同一个容器**（[VeneraCard]），不能只画一块 shimmer 矩形：
 * 实卡的高度是「卡内衬 8dp + 封面 + 4dp + 署名行 + 卡内衬 8dp」，
 * 骨架若省掉卡的那两层内衬，两边就差 16+16dp，到货那一帧仍然会跳。
 * 走同一个容器还顺带保证圆角与表面不会各算各的。
 */
@Composable
private fun GalleryRowSkeleton(
    /**
     * 封面的宽高比。`null` = 保持定高 [VeneraTokens.spacing.historyCoverHeight](170dp)。
     *
     * 为什么这一节要能换形状（2026-10-17 第二十二轮）：骨架存在的唯一理由就是"到货那一瞬不许跳"
     * （见头注第 3 条）。排行榜那张实卡从 124×170 改成了近方图（`RANKING_COVER_ASPECT`），
     * 骨架若还停在 170dp，加载完那一帧就会**往下跳 50dp** —— 恰好是最不该跳的一瞬。
     *
     * 骨架宽仍是定值 `historyCardWidth`(124dp)，所以比例一定 ⇒ 高度确定，抖动不会被引进来。
     */
    coverAspect: Float? = null,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            // 右端渐隐 + 尾部内衬（2026-10-17 第三十轮）：三个调用点（每日热门 / pixiv 画师 /
            // pixiv 排行榜）**全都坐在白卡里**，所以渐隐底色统一取卡面色。
            //
            // 这一行加它不只是"跟实卡对齐"：骨架 → 真数据切换那一瞬，如果只有实卡带渐隐，
            // 屏上就是"渐隐凭空冒出来 / 凭空消失"—— 那一帧本该是纯填充，不该有别的变化。
            .hScrollEndFade(container = tokens.color.surface),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        repeat(ROW_SKELETON_ITEMS) {
            VeneraCard(modifier = Modifier.width(tokens.spacing.historyCardWidth)) {
                Box(
                    modifier = (
                        if (coverAspect != null) {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(coverAspect)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .height(tokens.spacing.historyCoverHeight)
                        }
                        )
                        .clip(RoundedCornerShape(tokens.shape.medium)),
                ) {
                    VeneraCoverShimmer()
                }
                // 署名行的占位：与 GalleryCardCaption 同高（那里面最大的是 18dp 那枚站标）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.space2)
                        .height(tokens.spacing.sourceMarkSize)
                        .clip(RoundedCornerShape(tokens.shape.extraSmall))
                        .background(tokens.color.surfaceVariant),
                )
            }
        }
        // 尾部占位：`Row` 没有 `contentPadding`，末枚退不出渐隐区就只能这么摆
        // （宽度与渐隐带宽同档，见 [HScrollEndSpacer]）。
        HScrollEndSpacer()
    }
}

/**
 * 横向图片卡一行（每日热门 / 猜你喜欢那一行的预览）。
 *
 * 卡片形状照参考图：**封面在上、来源标识与标题在下**，整卡是一枚圆角面板。
 * 标题来自离线词典的作品/角色档（判据见 [GalleryCardTitle]），算不出来时那一行只有来源标识。
 *
 * 卡宽高仍取 `historyCardWidth × historyCoverHeight`（与首页历史那排同一口径）：
 * 参考图的卡片更大，但那是**整屏一列**的形态，而这一排是横滑的节内预览 ——
 * 尺寸跟着放大只会让一屏看不见第二枚。
 */
@Composable
private fun GalleryPosterRow(
    cards: List<GalleryCard>,
    imageLoader: ImageLoader,
    onOpen: (GalleryPost) -> Unit,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    // 这一行的标题整批算一次（要开 SQLite，逐卡各查一次就是好几趟）。
    val titles = rememberGalleryCardTitles(cards.map { it.post })
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            // 右端渐隐 + 尾部内衬（2026-10-17 第三十轮）：这一行随「每日热门」整节进了白卡，
            // 与 pixiv 排行榜那一排同档。只加渐隐不加尾部内衬的话，滑到底时最后一枚仍压在
            // 渐隐底下（"渐隐与尾部内衬成对"那条纪律，见 [hScrollEndFade] 头注）。
            //
            // ⚠️ 普通横滑行按仓库既有口径（Favorites / Search / ComicDetail）只配右端：
            // 左端那一道是懒横排（画师 / 排行榜）专属 —— 它要跟滚动位置动，得有自己的
            // `LazyListState`，而这里是一把裸 `rememberScrollState` 的 Row。
            .hScrollEndFade(container = tokens.color.surface),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        cards.take(ROW_ITEMS).forEach { card ->
            // 起点矩形与墙上那张卡同一口径：量**封面那一块**，不是整张卡
            // （整卡像素里带着下面那条标题，落到大图页的画面框里就会多出一圈"包裹"再消失 —— 那就是闪）。
            var coverBounds by remember { mutableStateOf(Rect.Zero) }
            // 这一行也会被回收（横滑出屏那一侧、整节换了一批）——撤掉自己的返回落点，
            // 与墙上那张卡同一口径（`GalleryPostCard`）。
            DisposableEffect(card.post.uid) {
                onDispose { GalleryFlyIn.forgetCardBounds(card.post.uid) }
            }
            VeneraCard(
                modifier = Modifier.width(tokens.spacing.historyCardWidth),
                onClick = {
                    // 与墙上那张卡（`GalleryPostCard`）**同一个动作**：趁卡片还在屏上把这一帧截下来，
                    // 交给大图页当飞行体的起点。这一行此前根本没截 ⇒ `canFly` 恒假 ⇒
                    // 点预览行的图必然走"整页从屏幕下沿抬上来"那条退路（2026-10-01 查出来的）。
                    GalleryFlyIn.capture(view, coverBounds)
                    onOpen(card.post)
                },
                // 与墙上那张卡同一档反馈（口径见 [VeneraPressFeedback]）：这一行的卡在主墙上有同 uid 的
                // 孪生卡，两处按下去的力度不一样会读成"两张卡不是同一个东西"。
                pressFeedback = VeneraPressFeedback.card,
            ) {
                VeneraCover(
                    url = card.post.previewUrl,
                    contentDescription = "${card.post.site.displayName} #${card.post.id}",
                    // 宽高由外部槽位定死，封面不能再自持比例，否则两侧留缝（轮播那条同因）。
                    preserveAspectRatio = false,
                    mask = if (card.masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tokens.spacing.historyCoverHeight)
                        .onGloballyPositioned {
                            val rect = it.boundsInWindow()
                            coverBounds = rect
                            // 返回程的落点，与墙上的卡同一把：打码卡不回写（红线：两边都不飞）。
                            if (!card.masked) GalleryFlyIn.noteCardBounds(card.post.uid, rect)
                        }
                        // 打码命中不飞行：飞行内容会渲染进 SharedTransitionLayout 的 overlay，
                        // 等于绕开页面级裁剪 —— 遮罩不能有机会被揭开（与漫画侧同一条硬口径）。
                        .coverSharedElement(
                            key = galleryCoverKey(card.post.uid),
                            allowFly = !card.masked,
                        ),
                ) {
                    // AI 角标（2026-10-18，用户「开了之后没见到一个图片有角标」）：这一排从前**不画**，
                    // 只有墙上的卡画 ⇒ 同一张图在首页有一枚、进搜索再没有。判据与开关都吃同一颗，
                    // 不在这里另立一套（读法也与墙上同口径：按卡各读一次，不往下透传）。
                    val aiBadgeOn by GalleryPorts.of(LocalContext.current).prefs
                        .galleryAiBadge.collectAsState()
                    if (aiBadgeOn && GalleryAi.isAiMarked(card.post.siteAiFlag, card.post.tagList)) {
                        GalleryAiPill()
                    }
                }
                GalleryCardCaption(
                    site = card.post.site,
                    title = titles[card.post.uid],
                    modifier = Modifier.padding(top = tokens.spacing.space2),
                )
            }
        }
        // 尾部占位：`Row` 没有 `contentPadding`，末枚退不出渐隐区就只能这么摆
        // （宽度与渐隐带宽同档，见 [HScrollEndSpacer]）。
        HScrollEndSpacer()
    }
}

/**
 * 「正在关注的画师」一行（裸圆头像 + 名字）。
 *
 * 圆座**先摆真头像**（现取，见 [artistAvatarOnSite]），取不到才退"那位名下最近一张被收藏的图"，
 * 两者都没有才是首字母圆座。
 *
 * ## 2026-10-17 第九轮：从「每位一张 88dp 方卡」改成裸摆
 *
 * 用户原话：「图库那边的关注画师列也要同步更改，不要只改 pixiv 那边的」。
 * 形状对齐 [PixivArtistRail] —— 一格 = 裸圆头像 + 一行名字，中间不再有卡面。两件事值得记下来：
 *  · 卡面会把一排人读成"一排卡片"，而这一行摆的是**人**；
 *  · 裸摆 56dp 头像 + 8dp 间距，一屏 5 位（方卡那档只有 4 位）。
 *
 * 随着卡面一起撤掉的是格子里那行「[站标] N 张收藏」：56dp 一格的宽度装不下第二行读数，
 * 硬塞会把名字挤成省略号（`artistAvatarSlotWidth`/`artistSourceMarkSize` 两枚令牌因此退役）。
 * 这两条读数没有消失 —— 长按进的介绍页上有站点徽标，张数随搜索结果一起看；
 * 这一行溢出的部分仍由节头右边那行「另有 N 位」（真数，见 [GalleryFollowedArtistsReadout]）交代。
 *
 * ⚠️ **圆形是有代价的**：圆外的像素会被裁掉，而头像与站方缩略图都是任意比例。
 * 整颗脸仍走全站唯一那颗件 [GalleryArtistAvatar]（`ContentScale.Crop` + 正方形容器），
 * 保证圆内始终填满、不留白边。
 *
 * 关注了、收藏里没有他名下的图、平台那头也没脸 —— 那位**照样摆**，圆座落回首字母
 * （用户 2026-09-30 拍板）。把他挑掉等于让"关注"这件事在没收藏他的图时凭空失效。
 *
 * ## 2026-10-17 第三十轮：这一节进了白卡，两道防抖随同搬过来
 *
 * 用户原话：「关注画师和每日热门 做成相同风格的ui」。节外壳吃到 `surface = true` 之后，
 * 这一排就与 [PixivArtistRail] 是同一种容器了，第二十/二十一轮在那一边修的两族
 * "白卡病"在这边一样会犯，所以一并搬过来（判据与链条见那边头注，不在这里重抄）：
 *
 * 1. **行高写死**（`lineHeight = TEXT_LINE_HEIGHT.em`）—— 治源头：行高否则跟着这一行
 *    实际用到的字形走，纯拉丁 / CJK / 带 emoji 差 1~2px；
 * 2. **外框 `heightIn(min = railHeight)`** —— 治传播：懒行交叉轴报高是"已测项的最大高"，
 *    不钉下限的话"视口里换了谁"就成了白卡的高度；
 * 3. **`overscrollEffect = null`** —— 治甩动：这一排五六位，一次用力甩必然顶到尽头，
 *    平台默认的 stretch 会把圆头像往卡面圆角外挤（用户报过的"整个框在抖"）；
 * 4. **两端渐隐同一颗**（`horizontalRailFades`，container 传卡面色）—— 与 pixiv 那一排
 *    是同一颗件，不是各写一版；左端跟滚动位置、右端恒亮，理由见那颗的头注。
 *
 * 间距 4dp → 12dp（`space6`）：第十九轮用户在 pixiv 那边定的档（「拉大画师头像的间隔」），
 * 裸圆之间没有卡面帮忙分隔，4dp 在 64dp 的球之间读起来是"挤在一起的一条"。
 * 尾部内衬从 `screenHorizontal`(12dp) 换成渐隐那一档 16dp：进了白卡之后左右由卡面 12dp
 * 内衬负责，尾部这一截要配渐隐带宽（"渐隐与尾部内衬成对"那条纪律）。
 *
 * ⚠️ 这一排与 pixiv 那一排**刻意仍是两颗件**：数据（[FollowedArtistRef] vs [PixivUser]）、
 * 手势（单击直接搜 + 长按进介绍页 vs 单击开站方页）、头像件（[GalleryArtistAvatar] vs
 * 行内自绘）都不同。同步的是**形状**，不是合成一颗 —— 判据在 `GalleryBooruSectionStyleTest`
 * 里逐条钉着"两边同档"。
 */
@Composable
private fun GalleryArtistRow(
    artists: List<FollowedArtistRef>,
    imageLoader: ImageLoader,
    translations: Map<String, String>,
    onPick: (FollowedArtistRef) -> Unit,
    /** 长按 = 进介绍页（批次 Q+R · 批量 4，用户 2026-09-30 拍板 2 那条不动：**单击仍直接搜**）。 */
    onOpenProfile: (FollowedArtistRef) -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    // 圆座**先试真头像**（用户 2026-10-01 报"关注了却只有英文字母"）：一位一位串行现取，
    // 谁先回来谁先换脸 —— 有收藏图垫着的那几位在这一趟里本来就有东西可摆，不是空着等。
    // 取法与代价见 [artistAvatarOnSite]。这句从前写的是「内含进程内缓存，切走再回来不重发」，
    // 2026-10-03 的探针把它驳掉了：档里确实有地址，但那次 peek 排在一笔外链请求之后，
    // 所以每次进栏都重发一遍（读数 `首页档=有地址` 紧跟 `外链耗时=1032ms`）—— 现在档在前面。
    // 切走再回来时这一栏的组合是**重建**的（2026-10-03 真机读数：`faces已填=3` 下一次进栏回到 0），
    // 所以初值不能是空表 —— 空表的第一帧按 faceOf(null, previewUrl) 摆名下收藏图，等异步跑完才换脸，
    // 用户读出来就是"每次切页面头像都刷一遍"。这里直接用地址档（peek 纯内存查表）同步播种：
    // 档里答上过的那几位第一帧就是真头像，剩下的仍由下面的 effects 逐位补。
    val avatars = remember(context) { GalleryPorts.of(context).avatars }
    var faces by remember(artists) {
        mutableStateOf(GalleryFollowedArtists.seedFaces(artists) { site, name -> avatars.peek(site, name, "") })
    }
    LaunchedEffect(artists) {
        artists.forEach { artist ->
            if (faces[artist.name] != null) return@forEach
            // 跨站同名并成一条时按徽标次序试，先答上的赢（`sites` 恒为枚举自然序，不会飘）。
            val avatar = artist.sites.firstNotNullOfOrNull { site ->
                artistAvatarOnSite(context, site, artist.name)
            }
            if (avatar != null) faces = faces + (artist.name to avatar)
        }
    }
    // ── 高度必须由**几何**决定，不能由"视口里正好是谁"决定（2026-10-17 第三十轮）──
    //
    // 这一节进了白卡（`surface = true`），第二十一轮在 [PixivArtistRail] 那边修的
    // "白卡上下跳 1~2px"在这里一样会犯，链条原文见那边头注（懒行交叉轴报高 = 已测项最大高
    // ＋ 行高随字形 ⇒ 视口换人就变高），这里只留"为什么两颗件要同档"这一句：
    // 两站读起来是同一个 UI，就不能只有一边不跳。
    val nameLineHeight = with(LocalDensity.current) {
        (tokens.type.caption * TEXT_LINE_HEIGHT).toDp()
    }
    val railHeight = tokens.spacing.artistAvatarSize + tokens.spacing.space1 + nameLineHeight
    val railScroll = rememberLazyListState()
    LazyRow(
        state = railScroll,
        modifier = Modifier
            .fillMaxWidth()
            // 用 `heightIn(min = ...)` 而不是 `height(...)`：`LineHeightStyle` 默认 `Trim.None`，
            // 行高小于字体度量时不裁溢出、而是让文本更高 ⇒ 算出来的 `railHeight` 是**下限**而非精确值。
            .heightIn(min = railHeight)
            // 两端渐隐同一颗（与 pixiv 那一排共用 [horizontalRailFades]，不是各写一版）：
            // 右端恒亮说"还能往右滑"，左端跟着滚动位置（停在起点时不画，否则糊第一枚左缘）。
            .horizontalRailFades(railScroll, tokens.color.surface),
        // 禁掉这一排的过度滚动（与 pixiv 那一排同一条）：这一排五六位，一次用力甩必然顶到
        // 尽头，平台默认的 stretch 会把圆头像往卡面圆角外挤（用户报过的"整个框在抖"）。
        // ⚠️ 恒传 `null` ⇒ 不需要 `rememberOverscrollEffect()`（没有开关就没有条件调用）。
        overscrollEffect = null,
        // 间距 4dp → 12dp：第十九轮用户在 pixiv 那边定的档（「拉大画师头像的间隔」），
        // 裸圆之间没有卡面帮忙分隔，4dp 在 64dp 的球之间读起来是"挤在一起的一条"。
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
        // 尾部内衬换成渐隐那一档 16dp：进了白卡之后左右由卡面 12dp 内衬负责，
        // 尾部这一截要配渐隐带宽（"渐隐与尾部内衬成对"那条纪律）。
        contentPadding = hScrollEndContentPadding(),
    ) {
        // 不挂 key：这一行按名字并过（跨站同名合成一条，见 [GalleryFollowedArtists]），
        // 理论上不会有重名，但懒列表的重复 key 是**直接崩**而不是报警 —— 不值得拿一条推断去赌。
        items(artists) { artist ->
            // 圆座次序：真头像 > 名下最近一张收藏 > 首字母座（判据在 [GalleryFollowedArtists.faceOf]）。
            val face = GalleryFollowedArtists.faceOf(faces[artist.name], artist.previewUrl)
            // 可点面就是"头像 + 名字"这一整列（与 [PixivArtistRail] 同一形状）：
            //  · 单击仍直接搜（2026-09-30 拍板 2 那条不动），长按仍进介绍页 ⇒ 必须 `combinedClickable`；
            //  · 卡面撤了，按压反馈就得自己挂 —— 走 `SinkFeedback`
            //    （[VeneraPressFeedback.indication]，与 miuix 卡片同一个原语）。不挂就是
            //    "按下去没回话"，这一栏 2026-10-16 刚修过同一种病。
            Column(
                modifier = Modifier
                    .width(tokens.spacing.artistAvatarSize)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = VeneraPressFeedback.indication,
                        onClick = { onPick(artist) },
                        onLongClick = { onOpenProfile(artist) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 脸走全站唯一那颗件（2026-10-04 统一）。这里从前自己手写了一份圆座 +
                // 首字母退路，于是同一个人在首页是 22sp 的字母、在信息卡是 14sp，
                // 改一处忘另一处。次序仍由 faceOf 定（真头像 > 名下最近一张收藏 > 首字母）。
                GalleryArtistAvatar(
                    avatarUrl = face,
                    name = artist.name,
                    imageLoader = imageLoader,
                )
                Text(
                    text = translations[artist.name] ?: artist.name,
                    // caption 这一档没有变（批次 N · N4 回改过）。变了的是它下面那行
                    // 「[站标] N 张收藏」—— 那行随卡面一起撤了，名字于是不必再与谁同级。
                    fontSize = tokens.type.caption,
                    // 行高写死（2026-10-17 第三十轮随白卡一起搬过来）：不写的话行高跟着
                    // 这一行用到的字形走（拉丁 / CJK 差 1~2px），横滑时"视口里换了谁"
                    // 就成了白卡的高度 —— 链条见 [PixivArtistRail] 头注。
                    lineHeight = TEXT_LINE_HEIGHT.em,
                    color = tokens.color.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.space1),
                )
            }
        }
    }
}

/**
 * 节头右边那一半。
 *
 * 2026-09-30 起它**只服务每日热门**：那一节的头上有「查看全部」（进二级页），
 * 「换一批」搬进了二级页（首页只做预览，换一批在那儿才是"看全量的人"的动作）。
 * 猜你喜欢那一节的头只有「换一批」—— 它本身就是下面那条墙，没有"全部"可切。
 */
@Composable
private fun DailySectionActions(onShowAll: () -> Unit) {
    val tokens = VeneraTokens
    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VeneraChip(text = "查看全部", variant = VeneraChipVariant.Assist, onClick = onShowAll)
    }
}

/**
 * 猜你喜欢节头右边那半：只有「换一批」（重抽标签并重取，走调用方那条清熔断的路径）。
 *
 * ## 2026-10-14 起这是首页唯一的「换一批」入口
 *
 * 顶栏那枚同名钮（「按收藏重抽一批」）被「浏览历史」取代了 —— 两处调的是同一条
 * `retryForYouFromUser()`，所以撤掉它不丢任何功能（下拉刷新 2026-09-30 撤下时，
 * 那条注释写的就是"这个动作现在由节头这枚承担"）。原顶栏注释里还有一条口径一并搬到这里：
 * 搜索态下屏上是搜索结果，换一批按下去用户看不见效果，所以它只管这一面墙 ——
 * 节头这枚在搜索墙那一态根本不参与渲染（墙换了，节头就不在），天然满足。
 *
 * ## 重取期间的轻指示为什么摆在这里（2026-09-30 第三轮）
 *
 * 用户这轮的原话是「换一批不要整个页面刷新，只刷新下面推荐的内容」。旧写法是
 * `refresh()` 清空 `posts` + 打回 `IDLE`，于是整屏换成一个居中波浪环、**三节跟着一起消失**。
 * 现在旧卡片留着、三节不动，那"正在换"这件事就得有个说法 —— 摆在**用户刚点的那枚旁边**
 * 是最强的位置：他一定看得见（这枚节头就在屏幕上，否则他点不到），也不需要给整屏压遮罩。
 *
 * 芯片在重取期间**禁用**：连点两下会发两笔请求、换两次种子，屏上只会看到第一批被第二批盖掉。
 */
@Composable
private fun ForYouSectionActions(loading: Boolean, onRemix: () -> Unit) {
    val tokens = VeneraTokens
    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(tokens.spacing.loaderInline),
                color = tokens.color.primary,
                trackColor = tokens.color.surfaceVariant,
            )
        }
        VeneraChip(
            text = "换一批",
            variant = VeneraChipVariant.Assist,
            enabled = !loading,
            onClick = onRemix,
        )
    }
}

/**
 * 画廊首页那**三节**（2026-09-30 第二轮定型）—— 正在关注的画师 / 每日热门 / 猜你喜欢。
 *
 * 结构上只有一件事是新的：**节与节之间不加分割线、不加分区底色**。参考图那种"内容很满"的观感
 * 来自每节都有真数据、一屏能看见三四节，而不是来自装饰 —— 深色底、圆角卡、浅蓝选中态、
 * 低对比描边都是既有语言，尺寸全部取现成 token，一个新数字都不立。
 *
 * ⚠️ 这一条 2026-09-30 当天被批次 M · M7 **推翻过一次**（"每组卡片层次分明"→ 三节各套一块背板），
 * 又在同日被批次 N · N2 **撤回来**：背板加在"节"上把每日热门变成卡里嵌卡、把画师行留成裸图标列，
 * 两节各错一半，而参考图的抬亮本来就只发生在**条目**上。
 * 走了一来一回不是白走 —— 现在"层次该由谁承载"这件事在 [GalleryHomeSection]、[GalleryArtistRow]、
 * [GalleryPosterRow] 三处都写着，而"条目卡为什么看得见地亮"落在 `veneraGlassCardColors()` 那一处。
 *
 * ## 为什么每一节都是墙里一个**恒定存在**的项
 *
 * 懒列表在第 0 项之后插一个新项，视口锚点会漂到下一项，内容就画进 `contentPadding` 里
 * （首页顶栏重叠那次真机缺陷的根因，见记忆「首页顶栏重叠的真根因：锚定漂移」）。
 * 所以节的**数量恒定**（三节永远在），"这一节此刻有没有货"由**项内部**去判 ——
 * 宁可看见一节空着的标题，也不要一节突然插进来把整屏推下去。
 *
 * ## 哪两节带预览行、哪一节不带
 *
 * - **每日热门**带：它的全量在**二级页**（`GalleryDailyRoute`），所以首页需要一行横卡当入口，
 *   节头给「查看全部」；
 * - **猜你喜欢**不带：首页那条**主墙就是它**，一行预览会让同一批图出现两遍
 *   （前 8 张横着一次、紧接着又在网格里）。所以这一节只是一个节头 + 那枚「换一批」。
 * - **收藏里的画师**带：它是一栏独立内容，全量在收藏页。
 *
 * ## 为什么横卡用固定尺寸
 *
 * 卡高若取自 `post.cardRatio`，摆放前就不知道它多高：懒列表要等测完才知道这一行占多少，
 * 于是每换一批都可能重排整屏。行里的卡一律 `historyCardWidth` 宽、封面
 * `historyCoverHeight` 高（与首页历史那排同一口径），下面再挂一行来源 + 标题。
 *
 * ## 为什么用 `Row + horizontalScroll` 而不是 LazyRow
 *
 * LazyRow 自带一把滚动状态（这一屏已经有两三把网格状态要记），而且**没有预组合** ——
 * 横滑时那排卡是一张一张现拉的，首滑会看到封面逐个蹦出来。一行最多八枚，全量摆不贵。
 *
 * ## 为什么这个列表每帧重建
 *
 * 刻意不 `remember`：这些内容里读的是 `fvm.isLoading`、`artists` 这类快照状态，
 * 记错键就会拿着旧值画新状态（"卡上写着 A 串、点进去 B 串"那类返祖正是这么来的）。
 * grid 项有恒定 key，成本就是三个 lambda。
 *
 * ## 节的次序与唯一性由 [GALLERY_HOME_SECTION_ORDER] 一处定
 *
 * 函数体写成"清单 → `when(key)`"的形状而不是一串 `listOf(...)` 字面量，为的是让
 * **加一节忘了登记**变成编译错误，而**重排**只改清单那一行。
 */
@Composable
internal fun galleryHomeSections(
    daily: List<GalleryCard>,
    dailyLoading: Boolean,
    onOpenDaily: (GalleryPost) -> Unit,
    artists: GalleryFollowedArtistsReadout,
    onPickArtist: (FollowedArtistRef) -> Unit,
    /** 「正在关注的画师」长按 = 进介绍页（单击仍是直接搜，批次 M 拍板 2 不动）。 */
    onOpenArtistProfile: (FollowedArtistRef) -> Unit,
    /** 「查看全部」= 进每日热门二级页看全量（首页这一行只是预览）。 */
    onOpenDailyAll: () -> Unit,
    /**
     * 「猜你喜欢」节头的「换一批」。
     *
     * ⚠️ 这一节**不收卡片数据**（`forYou` / `forYouStarted` 那两个参数 2026-09-30 第二轮删了）：
     * 首页那条主墙恒为猜你喜欢，卡片就在这一节下面由 `GalleryForYouPage` 画 ——
     * 把同一批图再传进来摆一行预览，就会出现"前 8 张横着一次、网格里又一次"。
     * 所以这一节只需要那枚动作与标题。
     */
    onRemixForYou: () -> Unit,
    /**
     * 猜你喜欢那面墙**此刻是不是在重取**（含冷启动第一轮）。
     *
     * 只用于节头那枚轻指示（见 [ForYouSectionActions]）。刻意不给默认值：
     * 漏传会编译不过，而给个 `false` 就会静默变成"按了换一批没有任何反应"的假按钮观感。
     */
    forYouLoading: Boolean,
    /** 首页那一排选中的内容域（决定下面这几节吃什么料）。 */
    domain: GalleryHomeDomain,
    pixiv: PixivHomeState,
    imageLoader: ImageLoader,
): List<GalleryGridSection> {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val artistNames = artists?.artists?.map { it.name }.orEmpty()
    var translations by remember(artistNames) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(artistNames) {
        translations = if (artistNames.isEmpty()) emptyMap() else GalleryPorts.of(context).lexicon.translations(artistNames)
    }

    // 清单驱动：`when` **不留 else** —— 加了 key 却忘了在这里出内容会编译不过，
    // 而"内容有了却忘了登记进清单"会在单测里红（见 GalleryHomeSectionsTest）。
    return galleryHomeSectionOrder(domain).map { key ->
        GalleryGridSection(key) {
            when (key) {
                GalleryHomeSectionKey.ARTISTS -> GalleryHomeSection(
                    wallAdjacent = false,
                    // 2026-10-17 第三十轮（用户「关注画师和每日热门 做成相同风格的ui」）：
                    // 图库站这两节对齐 pixiv 那两节（画师 / 排行榜）的白卡形态 ——
                    // `surface = true` + 节尾 `space2`，与那边逐项同值（卡底 8dp + 4dp = 12dp，
                    // 正好与「搜索条 → 画师」那一段的网格行距相等）。卡一上，行内那两族
                    // "白卡病"就得一并治，见 [GalleryArtistRow] 头注（第二十/二十一轮那两条）。
                    surface = true,
                    gapAfter = VeneraTokens.spacing.space2,
                ) {
                    MiuixSectionHeader(
                        title = "正在关注的画师",
                        icon = Icons.Outlined.Person,
                        // 这一节**没有全量页**：那枚「›」画出来也点不动，是假把手（去掉它，
                        // 不是把整行做成链接 —— 整行链接同样无处可去）。
                        chevron = false,
                        trailing = artists.takeIf { it.overflow > 0 }?.let { readout ->
                            @Composable {
                                Text(
                                    text = "另有 ${readout.overflow} 位",
                                    fontSize = tokens.type.caption,
                                    color = tokens.color.textTertiary,
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space2))
                    when (artists.gap) {
                        GalleryFollowedArtistsGap.NONE -> GalleryArtistRow(
                            artists.artists,
                            imageLoader,
                            translations,
                            onPickArtist,
                            onOpenArtistProfile,
                        )
                        // 一个都没关注 —— 这一节**照样摆**（首页恒三节，撤一节会让懒列表的锚点漂），
                        // 只把内容换成一句引导。它说的是"你还没做"，不是"这里坏了"。
                        GalleryFollowedArtistsGap.NO_FOLLOWS -> GalleryRowCaption("在大图上点关注，这一栏会列出你关注的人")
                    }
                }

                GalleryHomeSectionKey.DAILY -> GalleryHomeSection(
                    wallAdjacent = false,
                    // 同上一节：白卡 + 节尾 4dp（用户「关注画师和每日热门 做成相同风格的ui」）。
                    // 下一节「猜你喜欢」只有节头（它自己就是墙），所以这 4dp + 卡底 8dp
                    // 是与 pixiv「排行榜 → 推荐」同一段的读数。
                    surface = true,
                    gapAfter = VeneraTokens.spacing.space2,
                ) {
                    MiuixSectionHeader(
                        title = GalleryWallFeed.DAILY.label,
                        // 火焰 = 热门（2026-10-17 第三十轮补）：节头带图标是 pixiv 那两节的
                        // 既有形态（画师 = Person、排行榜 = EmojiEvents），这一节从前没有。
                        // 同一枚在统计页「连续打卡」上用过（material-icons-extended 在依赖里）。
                        icon = Icons.Outlined.LocalFireDepartment,
                        // 只留「查看全部」：它进二级页看全量，而「换一批」搬到了那一页
                        // （首页这一行是预览，"换一批"是看全量的人的动作）。
                        trailing = { DailySectionActions(onShowAll = onOpenDailyAll) },
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space2))
                    when {
                        daily.isNotEmpty() -> GalleryPosterRow(daily, imageLoader, onOpenDaily)
                        dailyLoading -> GalleryRowSkeleton()
                        else -> GalleryRowCaption("这一轮还没有可摆的热门")
                    }
                }

                GalleryHomeSectionKey.FOR_YOU -> GalleryHomeSection(
                    // 这一节就是脚下那面墙本身 ⇒ 不收口（收了会让节头比墙窄、左缘对不齐）。
                    wallAdjacent = true,
                ) {
                    MiuixSectionHeader(
                        title = GalleryWallFeed.FOR_YOU.label,
                        // 猜你喜欢**没有「查看全部」**：它本身就是下面那条墙。
                        trailing = { ForYouSectionActions(loading = forYouLoading, onRemix = onRemixForYou) },
                    )
                    // ⚠️ 这一节**只有节头**，没有预览行。
                    //
                    // 2026-09-30 第二轮：首页那条主墙**恒为猜你喜欢**（用户点名"主页下面的内容
                    // 改为猜你喜欢"），所以这一节底下紧接着的就是它自己的全部卡片。
                    // 再摆一行预览横卡就成了同一批图连着出现两遍（前 8 张横着、然后又在网格里），
                    // 那会让用户以为"上面那排是另一批"。
                    //
                    // 与「每日热门」那一节的区别正在这里：热门的全量在**二级页**，
                    // 所以首页需要一行预览当入口；猜你喜欢的全量就在脚下，不需要。
                }

                // ── Pixiv 域那三节（用户 2026-10-17 定的次序：关注画师 / 排行榜 / 推荐）──

                GalleryHomeSectionKey.PIXIV_ARTISTS -> GalleryHomeSection(
                    wallAdjacent = false,
                    surface = true,
                    // 与下面那节同一个值：两节之间 = 卡底 8dp + 这里 4dp = 12dp，
                    // 正好与「搜索条 → 画师」那一段的网格行距（`gridGap` 12dp）相等
                    // （第二十四轮，用户「三处间隔太高了，调低点」）。
                    gapAfter = VeneraTokens.spacing.space2,
                ) {
                    // ── 2026-10-17 第九轮：两节合并成一节 + 两个 Tab（用户「推荐画师合并到发现和已关注供切换」）──
                    //
                    // 从前这里是「关注画师」与「推荐画师」两节：各自一个节头、各自一排、各一段空态，
                    // 一屏里占掉两段高度，而两排都是"人"。设计稿把它们收成一格：节头「画师」、
                    // 下面一枚「已关注 / 发现」切换，两排共用同一块地方。
                    //
                    // ⚠️ 数据仍是**两条腿各存各的**（`following` / `recommendedUsers`），
                    // 切 Tab 只换"摆哪一份"—— 这样切过去不会先空一帧（空一帧在屏上读起来像"没有关注的人"）。
                    //
                    // 第十八轮：这一节（连同下面两节）包进一张实色卡（用户稿：三节各一张白卡）。
                    MiuixSectionHeader(
                        title = "画师",
                        icon = Icons.Outlined.Person,
                        // 稿上这里是「全部 →」。**没有落地页就不摆**：本仓没有"全部画师"那一屏，
                        // 摆一枚点了没反应的箭头正是本仓反复修掉的那族假把手。
                        chevron = false,
                        // 2026-10-17 第十九轮（用户「已关注和发现选择器放在画师标题的右侧」）：
                        // 从"标题下面独占一行"挪进节头行内 —— 省掉一整行高度，且"这一节是什么 +
                        // 看哪一半"合成一眼能读完的一组。切换器是紧凑档（26dp，不是 48dp 的
                        // `segmentedHeight`），塞进节头行不会把行高撑起来。
                        titleTrailing = {
                            PixivArtistTabRow(selected = pixiv.artistTab, onPick = pixiv.onPickArtistTab)
                        },
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space3))
                    // 登录态只从 `authState` 那一条流读（同源），穷尽三档 —— P6 之后它是屏上
                    // 唯一能读出"这一趟没登上"的地方（方案 §三 红线：降级不许静默）。
                    val auth = pixiv.credentials?.authState?.collectAsState()?.value
                        ?: PixivAuthState.Anonymous
                    val onFollowing = pixiv.artistTab == PixivArtistTab.FOLLOWING
                    val users = if (onFollowing) pixiv.following else pixiv.recommendedUsers
                    val failure = if (onFollowing) pixiv.followingCaption else pixiv.usersCaption
                    when {
                        users.isNotEmpty() -> PixivArtistRail(
                            users = users,
                            onOpen = pixiv.onOpenUser,
                            // 画廊那一份取图器（不传就吃漫画那份单例 —— 见 PixivArtistRail 参数注）。
                            imageLoader = imageLoader,
                        )

                        pixiv.artistsLoading -> GalleryRowSkeleton()

                        // 已登录 + 没有失败 + 空表 = 真读数（这一档不必解释自己）。
                        auth is PixivAuthState.SignedIn -> GalleryRowCaption(
                            failure ?: if (onFollowing) {
                                "你在 pixiv 上还没有关注画师 —— 这一栏会跟着你的关注自动长出来。"
                            } else {
                                "站方这一轮没有给推荐用户。"
                            },
                        )

                        else -> GalleryRowCaption(
                            "这一栏要读 pixiv 的画师，而" + auth.screenLine +
                                " —— 点顶栏左边那颗头像登录 pixiv。",
                        )
                    }
                }

                GalleryHomeSectionKey.PIXIV_RANKING -> GalleryHomeSection(
                    wallAdjacent = false,
                    surface = true,
                    // 下一节就是推荐，而推荐的墙一直铺到屏底 —— 中间留 14dp 的话
                    // 屏上是「白卡 → 30dp 空 → 「推荐」两字 → 紧跟着满屏图」，
                    // 读起来像两段不相干的内容。收到 `space2`(4dp)：
                    // 加上卡底 12dp + 节头 4dp，屏上剩 20dp，够分属两节又不散。
                    gapAfter = VeneraTokens.spacing.space2,
                ) {
                    MiuixSectionHeader(
                        title = "排行榜",
                        icon = Icons.Outlined.EmojiEvents,
                        trailing = {
                            PixivRankingShowAll(
                                expanded = pixiv.rankingExpanded,
                                onToggle = pixiv.onToggleRankingExpanded,
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space2))
                    PixivRankingModeChips(selected = pixiv.rankingMode, onPick = pixiv.onPickRankingMode)
                    Spacer(modifier = Modifier.height(tokens.spacing.space3))
                    when {
                        pixiv.ranking.isNotEmpty() -> PixivRankedWorkGrid(
                            works = if (pixiv.rankingExpanded) pixiv.ranking
                            else pixiv.ranking.take(GalleryPixivHome.RANKING_PREVIEW_ITEMS),
                            maskedOf = pixiv.maskedOf,
                            imageLoader = imageLoader,
                            onOpen = { post -> pixiv.onOpen(pixiv.ranking, post) },
                            expanded = pixiv.rankingExpanded,
                            favoritedOf = pixiv.favoritedOf,
                            onToggleFavorite = pixiv.onToggleFavorite,
                        )

                        // 骨架必须与实卡同一个封面形状，否则到货那一帧会跳（见 [GalleryRowSkeleton] 那个参数）。
                        pixiv.rankingLoading -> GalleryRowSkeleton(
                            coverAspect = GalleryPixivHome.RANKING_COVER_ASPECT,
                        )
                        // 空那一档念**站方给的原话**（限流 / 票据 / 真没图）；取数没给原因时才退回那句。
                        else -> GalleryRowCaption(pixiv.rankingCaption ?: "pixiv 这一档这一轮没有答上")
                    }
                }

                GalleryHomeSectionKey.PIXIV_RECOMMEND -> GalleryHomeSection(
                    // 这一节**就是脚下那面墙本身** ⇒ 不收口（收了节头会比墙窄、左缘对不齐），
                    // 而且它只有节头 —— 与图库站「猜你喜欢」同一形状。
                    //
                    // 2026-10-17 第二轮（用户点名「pixiv 站的推荐应该做成和图库站猜你喜欢那种的，
                    // 不是现在的卡片状」）：从前这里是一排 PixivRankedWorkGrid 卡（六张、带标题行），
                    // 现在整池交给首页那面墙去摆。空/忙/页尾三样都不在这一节里说 ——
                    // 它们由 pixivRecommendReading 那份读数给，与墙上那批卡同源，
                    // 在这儿再判一次"有没有货"就是第二把尺子。
                    wallAdjacent = true,
                ) {
                    MiuixSectionHeader(
                        title = "推荐",
                        icon = Icons.Outlined.AutoAwesome,
                        // 这一节**就是脚下那面墙**，所以**不在这颗外壳里**包实色卡 ——
                        // 外壳把一节当成一个 Column，而墙在它外面（是网格的后续 item）。
                        // 2026-10-17 第二十二轮给它包的那张卡面走的是另一条路：
                        // `GallerySectionSurface` 在**网格下层**画一整层，见 `GalleryScreen.kt`。
                        // 「换一批」（第二十三轮，用户「推荐标题右边要多个换一批的胶囊」）：
                        //
                        // ⚠️ **只在登录态摆**。已登录走站方推荐 `/v1/illust/recommended`，那一腿回
                        // `next_url`，所以「换一批」= 取下一页并整批替换 ⇒ 按下去真的有新图。
                        // 匿名走的是**种子那一跳**：种子是 booru 那边推来的标签，重取同一枚种子
                        // 拿回来的还是同一批 —— 那种处境下摆一枚按下去看不出变化的把手，就是假按钮
                        // （下面那段注是本轮之前的老结论，现在只针对匿名那一档）。
                        trailing = if (pixiv.recommendRemixable) {
                            {
                                PixivRemixChip(
                                    loading = pixiv.recommendRemixLoading,
                                    onRemix = pixiv.onRemixRecommend,
                                )
                            }
                        } else {
                            null
                        },
                        chevron = false,
                    )
                    // 来源备注（2026-10-17 第五轮）：未登录时这一池来自"按收藏标签那一跳"，
                    // 不说清就会被读成"站方推荐就长这样"。
                    pixiv.recommendNote?.let { note ->
                        Spacer(modifier = Modifier.height(tokens.spacing.space2))
                        GalleryRowCaption(note)
                    }
                }
            }
        }
    }
}

/**
 * 首页一节的**外壳**：只有宽度与节间距，**没有背板**。
 *
 * ## 为什么背板撤了（批次 N · N2，推翻 M7）
 *
 * 批次 M · M7 曾给每一节套一块 `VeneraCard` 背板，当时的理由原文是：
 * 「三节原先直接铺在页面底色上，只靠 sectionGap 那点留白分组 —— 真机对照参考图之后那个"组"
 * 根本读不出来。一节一块圆角表面，是"这几样东西属于同一组"在屏上唯一的说法。」
 *
 * 那个说法**方向错了**。参考图里根本没有"节"这一层表面 —— 抬亮发生在**每一条**上
 * （每位画师一张方卡、每张海报一张卡），页面底 → 条目卡只有两层。
 * 把背板加在"节"上同时错了两处：
 * - **每日热门**变成卡里嵌卡（节卡 + 条目卡），而两层颜色只差一档里的一小截，
 *   实测条目卡面比页面底只亮 +7，两层糊成一层；
 * - **正在关注的画师**那一节里面摆的是**裸条目**（没有自己的卡），于是它连"卡中卡"都不是，
 *   读起来是一排图标而不是四个人的卡。
 * 两节各错一半，而**两节的分层形状还不一样** —— 这比原来"三节糊在一片底色上"更糟。
 *
 * 本轮把表面下放到条目：撤节背板，画师行每位一张 [VeneraCard]，海报行本来就有。
 * 层次该由谁承载这件事，[GalleryArtistRow] 与 [GalleryPosterRow] 各自的注释里都写着。
 *
 * ⚠️ **2026-10-17 第九轮又动了一次上面那条结论（只动画师行）**：画师那一格从"每位一张卡"
 * 改回**裸摆**（用户「关注画师列也要同步更改」+ 设计稿里那是一排人、不是一排卡）。
 * 于是「每日热门」仍走"卡下沉到条目"，而「正在关注的画师」这一节**没有条目卡**了 ——
 * 两节的分层形状**故意不同**：海报是内容（需要有边界），人是头像（边界就是那颗圆）。
 * 这不是回到 M7 那个"节背板"方案：背板仍是撤的，谁都没有在节上铺色。
 *
 * ## 抬亮本身不在这里做
 *
 * 条目卡为什么"看得见地比页面亮一档"，判据在 `veneraGlassCardColors()` /
 * `containerColorFor(CONTAINER)` 那一处（批次 N · N1：容器色从 `surface` 换成
 * `surfaceContainerHigh`）。这里只负责"表面该摆在哪一层"。**两处别互相顶**：
 * 在这里加背板救不了抬亮，在那里加也救不了分层。
 *
 * ## 宽窗收口（2026-10-15，问题清单 G3）
 *
 * [wallAdjacent] 是**必填**而不是带默认值的开关：默认值会让下一节忘了收口而没人报错
 * （本仓那条"漏传不报错的参数不要用默认值"）。判据只有一条 —— **这一节自己是不是墙的一部分**：
 *  - `false`（画师行、每日热门预览行）：内容是本节自己的横滑行，与下面那面墙不成列，
 *    所以宽窗按现成的 `contentMaxWidthFor` 收口并居中（Medium 720 / Expanded 840，
 *    **Compact 恒 null ⇒ 手机档逐像素不变**，用户 2026-10-02 的硬约束）；
 *  - `true`（「猜你喜欢」那一节，只有节头）：它紧接着的就是**它自己的全部卡片**，
 *    节头就是这面墙的第一行。把节头收到 720 居中，平板上会得到"节头比墙窄、
 *    左边对不齐"这一种**新的**错位 —— 收口这一格要防的正是它（清单 §一-4 那句"墙本体不限宽"的边界）。
 */
@Composable
private fun GalleryHomeSection(
    wallAdjacent: Boolean,
    /** 这一节要不要包一张**实色卡**（2026-10-17 第十八轮，用户稿：pixiv 三节各是一张白卡）。 */
    surface: Boolean = false,
    /**
     * 这一节尾部留的间距（`null` = 用 [VeneraTokens.spacing.sectionGap]，14dp）。
     *
     * 为什么允许单独收窄（2026-10-17 第二十二轮，用户「压缩下推荐和排行榜的间距」）：
     * 14dp 是**页面级**区块的间距，而这两节各自还带边缘内衬 ——
     * 排行榜卡底 12dp（卡内衬）+ 推荐节头 4dp + 这 14dp = 屏上 **30dp** 的连续空白，
     * 读起来像两个互不相干的区块。相邻两节都带内衬时，中段那枚得让位。
     *
     * 刻意不给成 `Dp` 的默认值：`VeneraTokens.spacing` 是 `@Composable` getter，
     * 参数默认值在函数外求值，写 `= VeneraTokens.spacing.sectionGap` 编译不过。
     */
    gapAfter: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gap = gapAfter ?: VeneraTokens.spacing.sectionGap
    // 手机档 contentMaxWidth = null ⇒ 下面那道 `.then` 不挂任何 Modifier，逐像素走旧几何
    //（与 `GalleryArtistProfileScreen` 那处收口同一形状）。
    val contentMaxWidth = if (wallAdjacent) {
        null
    } else {
        contentMaxWidthFor(wideScreenLayoutMode(LocalConfiguration.current.screenWidthDp.dp))
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        // 收口之后要在满宽的 item 里居中。用 Box 的 contentAlignment 而不是子件的
        // `.align(Alignment.CenterHorizontally)` —— 那一枚是 ColumnScope 的用法（`GalleryArtistProfileScreen`
        // 那处住在 Column 里），而这里子件住在 Box 里，BoxScope 的 `align` 要的是整枚 `Alignment`。
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(contentMaxWidth?.let { Modifier.widthIn(max = it) } ?: Modifier)
                // 节间距必须挂在卡**外**（2026-10-17 第十九轮修正）。
                //
                // `Modifier.background` 画的是「它之后所有 modifier 决定的尺寸」—— 所以从前的
                // `.then(卡面段).padding(bottom = sectionGap)` 里那段 gap 其实在 `background`
                // **之内**，被一起涂成了卡面色：两卡之间那 14dp 留白在屏上等于不存在，
                // 只剩两张圆角咬合出来的一道缝。挪到卡面段之前（= 更外层）才是"卡与卡之间的距离"。
                .padding(bottom = gap)
                .then(
                    if (surface) {
                        Modifier
                            // 顺序要紧：先裁圆角再铺色（反过来色块会带直角），内衬挂在**铺色之后**
                            // ⇒ 那圈留白属于卡面（与 miuix `Surface` 把整条链塞进自己那颗 Box
                            // 是同一个道理，见记忆里"padding 串在 Surface 之前会被一起涂色"那条）。
                            //
                            // 内衬 6dp → 12dp（第十九轮）：节头自己还带 `space2` 横向内衬，
                            // 6 + 4 = 10dp 的合计左衬读起来是"标题顶着卡边"（稿上折合 ≈16dp）。
                            // 12 + 4 = 16dp 正好落到稿上的量；复用现成 `space6`，不新增 token。
                            //
                            // ⚠️ **纵向**那一项在第二十四轮收成 `space4`(8dp)（用户「三处间隔太高了」）：
                            // 上下内衬不是"左右留白"那一档需求 —— 它的真正成本是**节间距**：
                            // 卡底 12dp 内衬 + `gapAfter` 叠加起来，两节之间能有 26dp 连续空白，
                            // 读起来像三个互不相干的区块。收到 8dp 之后，那三段等距 12dp。
                            // 左右仍留 12dp：稿上的标题缩进是 16dp（含节头那 4dp），不许动。
                            .clip(RoundedCornerShape(VeneraTokens.shape.large))
                            .background(VeneraTokens.color.surface)
                            .padding(
                                horizontal = VeneraTokens.spacing.space6,
                                vertical = VeneraTokens.spacing.space4,
                            )
                    } else if (wallAdjacent) {
                        // 贴墙那一节（「猜你喜欢」/ pixiv「推荐」）**只有节头**，包卡会把整节读成一块面，
                        // 所以它走无卡那一支 —— 但**左起点必须与有卡那两节同一档**：
                        // 用户 2026-10-18 第四批的读数就是「推荐的文字和图标没和上面的排行榜对齐」。
                        // 有卡那两节的节头左缘 = 卡内衬 `space6`(12) + 节头自己那 4 = 16dp，
                        // 无卡这一支从前什么都不加 ⇒ 节头只吃节头那 4dp，**偏左 12dp**。
                        // 这里补的就是那 12dp：同一颗 `space6`，不另造数、也不去动节头那颗件。
                        Modifier.padding(horizontal = VeneraTokens.spacing.space6)
                    } else {
                        Modifier
                    },
                ),
            content = content,
        )
    }
}

// 「最近搜索」的渲染件 2026-09-30 第二轮**搬回 `GallerySearchArea.kt`**（改回原名
// `HistoryHeader` / `HistoryList`）：用户拍板"最近搜索放回点击搜索框后下面框框着"，
// 同一份历史只该在全站出现一处。这里刻意不留副本、也不留空壳 ——
// 两份实现会在"在一处删掉、另一处还在"那里分叉，而那正是本轮要收口的事。

