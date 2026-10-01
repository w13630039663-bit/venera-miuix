package com.venera.compose.gallery.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import coil3.ImageLoader
import com.venera.compose.openGalleryPost
import com.venera.compose.components.CoverHeroBackdrop
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.VeneraGallerySourceMark
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.gallery.data.DanbooruArtistClient
import com.venera.compose.gallery.data.GalleryArtistFollowsStore
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GelbooruClient
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivClient
import com.venera.compose.gallery.data.YandeReClient
import com.venera.compose.gallery.domain.GalleryArtistFollows
import com.venera.compose.gallery.domain.GalleryArtistLink
import com.venera.compose.gallery.domain.GalleryArtistLinkPlatform
import com.venera.compose.gallery.domain.GalleryArtistLinks
import com.venera.compose.gallery.domain.GalleryArtistProfile
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GalleryRankings
import com.venera.compose.gallery.domain.GalleryTagFilter
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.ui.tokens.VeneraTokens
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * 画师介绍页（批次 Q+R · 批量 5；2026-10-01 用户报「感觉不太行」后按参考图重排）。
 *
 * ## 为什么这一版要重排：旧版没有视觉重心
 *
 * 旧版的头部是一个**设置页条目** —— 头像摆 `sourceAvatarSize`（34dp，那是「源状态行」的档）、
 * 名字摆 `itemTitle`（17sp），两样挤在**一行** `VeneraCard` 里，右边还并着站标与「关注」。
 * 于是一整页读下来是"一个列表 + 一堆小图"，不像"这是一位画师"。
 *
 * 重排后自上而下是：**hero**（唯一的主视觉）→ 外链那排 → **动作排** → 居中的节标题 → 作品网格。
 * 四条改动各自解决一件具体的事，不是把元素挪个位置：
 *
 * 1. **hero 立人**。名字从 17sp 抬到 `display`（MD3 28 / MIUIX 32sp），底下压一张该画师
 *    热门作品首图的**轻模糊底**。组件复用漫画详情页那枚 [CoverHeroBackdrop]，
 *    但**档位是另传的**（`artistHeroBackdropBlur` / `artistHeroBackdropScale`）：
 *    详情页那套 32dp × 1.35 是把底图当"氛围"用的（它压在封面卡下面），
 *    而这一页的底图**就是主视觉本身**，同一个档摆过来只剩一块色块
 *    —— 2026-10-01 用户报「模糊过头」，这是两处成因之一，另一处是取图档位（见下）。
 *    底图取的是 `wall.cards` 里**第一张没被打码的**，不是 `posts` 的第一张 —— 后者会把
 *    "命中了内容守卫的那一张"画成整页最大的图，那正是遮罩唯一可能被绕开的地方。
 *    取不到（没有作品 / 全被打码 / 这一站没答上）就只是一块卡面，**不留半截实现**。
 *    档位取 `post.backdropUrl`（中档 `sample`）**不是** `previewUrl`：缩略档 300×212 级
 *    铺满 361dp 宽的这块要放大三倍多，铺出来是一张糊图 —— 那正是「模糊过头」的另一半成因。
 * 2. **「关注」从头部挪进动作排**。它此前跟站标挤在同一行最右侧 —— 一枚主行动被降格成装饰。
 * 3. **节标题居中 + 上下细分隔线**（参考图那一档）。它同时把「看 TA 全部作品」从节头
 *    挤出来，那枚动作搬进动作排与「关注」并排。
 * 4. **网格钉死卡高**（[GalleryArtistPostGrid]）。旧版按 `post.cardRatio` 摆，
 *    而这一格是**按行**摆的（行与行必然对齐）⇒ 一张矮卡底下就留一块空洞，
 *    数据比例一参差整面墙就"看着不太行"。钉死 3:4 之后一行三张等高，代价是原图会被裁。
 *
 * ⚠️ **一处刻意与参考图不同**：参考图那个页面没有头像（它用名字当主视觉），
 * 这里把头像留了下来、摆在名字左边，用的是与首页画师卡同一个 `artistAvatarSize`(56dp)。
 * 理由是"这位是谁"最直接的证据还是那张脸，且两处尺寸同源就不会读成两种头像。
 *
 * ## 三节各自"答上了才摆"，且"没取到"与"没有"在屏上同形
 *
 * 与详情面板那排画师入口同一条红线：别名、外链、Popular posts 三节，**取不到 = 那一节整块不出现**，
 * 屏上永不出现"这位没有别名 / 没有外链"这种我们没资格断言的话 —— danbooru 那台在 2026-09-30
 * 本机出口是 403 卡盾，把"被挡在门外"折叠成"没有"就是假答复。区别只留在日志里，每档失败各一句话。
 *
 * ## 别名只有 Gelbooru 腿有
 *
 * yande.re 那一侧不是"我们没做"，是**量过之后结构性够不着**（18 个真名样本同批凑得出别名表 = 0/18，
 * 天花板 10/111 ≈ 9%，探针与读数见 `_probe/yande_re_alias_coverage.*`）。所以 yande.re 那一站
 * 这节恒不出现，能给的只有"站方记的正名是 X"那一句（批次 J 已有的解析链）。
 *
 * ## 平台入口那排的字形从哪来
 *
 * 五家有单色矢量档（`scripts/build_platform_icons.mjs` 从 Simple Icons 转出，见
 * [PlatformBadge]）；FANBOX 没有可用矢量档，退品牌色底上的字母标「F」——
 * 与两站站标那条"有真图标就摆真图标、没有就摆品牌色字母标"是同一份口径。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryArtistProfileScreen(
    site: GallerySite,
    artistName: String,
    source: String,
    onBack: () -> Unit,
    onBrowseAllWorks: () -> Unit,
) {
    val context = LocalContext.current
    val tokens = VeneraTokens
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val imageLoader: ImageLoader = remember { GalleryImageLoader.get(context) }
    val guard = remember { ContentGuardManager.getInstance(context) }
    val maskMode by guard.nsfwMaskMode.collectAsState()
    val blockAi by VeneraPreferences.getInstance(context).galleryBlockAi.collectAsState()
    val store = remember { GalleryArtistFollowsStore.getInstance(context) }
    val follows by store.follows.collectAsState()

    var credits by remember(site, artistName) { mutableStateOf(ProfileCredits()) }
    var posts by remember(site, artistName) { mutableStateOf<List<GalleryPost>>(emptyList()) }
    var postsFailed by remember(site, artistName) { mutableStateOf(false) }

    LaunchedEffect(site, artistName, source) {
        credits = loadCredits(context, site, artistName, source)
    }
    LaunchedEffect(site, artistName) {
        val today = LocalDate.now(ZoneOffset.UTC)
        val query = GalleryRankings.searchQuery(
            site = site,
            filters = listOf(GalleryTagFilter(artistName)),
            ranking = GalleryRanking.ALL,
            anchor = null,
            todayUtc = today,
        )
        // `ALL` 那一档本身就是"全站按分数"（两站后缀 `order:score` / `sort:score:desc`
        // 都在 GalleryRankings 那一处钉着），这里不另造参数、也不互抄写法。
        val outcome = when (site) {
            GallerySite.YANDERE ->
                YandeReClient.getInstance(context).searchPosts(query, page = 1, limit = SLOT_FETCH_SIZE)
            GallerySite.GELBOORU ->
                GelbooruClient.getInstance(context).searchPosts(query, page = 1, limit = SLOT_FETCH_SIZE)
        }
        outcome.onFailure { failure ->
            // "这一站没答上"与"这位只有这几张"是两句话，前者**在屏上也要说出来**（那是真话），
            // 而"没取到别名/没取到外链"那两档不说 —— 屏上永远不摆"这位没有 X"。
            Log.w(TAG, "画师「$artistName」($site) 的 Popular posts 没取到：${failure.message}")
            postsFailed = true
        }.onSuccess { found ->
            posts = found
        }
    }

    val wall = remember(posts, maskMode, blockAi) {
        buildGalleryWall(posts, maskMode, blockAi = blockAi) { post ->
            guard.findGalleryBlockedRule(author = post.author, tags = post.tagList)?.pattern
        }
    }
    val titles = rememberGalleryCardTitles(wall.cards.map { it.post })
    val following = GalleryArtistFollows.isFollowing(follows, site, artistName)

    // 头图的取法见文件头注第 1 条：**只从过了守卫的那批里取**，且跳过打码那张。
    // 档位取 `backdropUrl`（中档 `sample`）而**不是** `previewUrl`：缩略档只有 300×212 级，
    // 铺满这一块要放大三倍多，再叠一层模糊就只剩色块、认不出画的是什么
    // （2026-10-01 用户报「模糊过头」，两处成因之一）。
    val heroImage = wall.cards.firstOrNull { !it.masked }?.post?.backdropUrl.orEmpty()
    val aliasLine = GalleryArtistProfile.aliasLine(credits.canonicalName, credits.aliasReadout)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space4),
    ) {
        // 顶栏只剩一枚悬浮返回钮：参考图那一页没有标题栏，而「画师」两个字会把这页
        // 读成"某个二级列表页"—— hero 里那行大字已经把这页是什么说清楚了。
        VeneraTopBarPill(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = tokens.color.textPrimary,
            )
        }

        Spacer(modifier = Modifier.height(tokens.spacing.space4))

        // ── hero：这一页唯一的主视觉 ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(tokens.spacing.artistHeroHeight)
                .clip(RoundedCornerShape(tokens.shape.extraLarge))
                .background(tokens.color.surfaceContainerHigh),
        ) {
            // 底图（有就画、没有就是上面那块卡面）。要显式传画廊自己的 ImageLoader：
            // 这一链路的图带 Referer/UA 才拿得到，用默认单例换来的是一枚 403 的空底。
            //
            // 模糊档必须显式换掉：组件默认那套（`space11` 32dp × 1.35 放大）是为详情页
            // "把头部染成封面的颜色"调的氛围档，屏上实际模糊约 43dp —— 摆在这一页
            // （底图是唯一主视觉）只会是一块色块。两处各传各的，详情页那一处零改动。
            CoverHeroBackdrop(
                coverUrl = heroImage,
                imageLoader = imageLoader,
                blurRadius = tokens.spacing.artistHeroBackdropBlur,
                backdropScale = tokens.spacing.artistHeroBackdropScale,
                modifier = Modifier.matchParentSize(),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(tokens.spacing.space9),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                verticalAlignment = Alignment.Bottom,
            ) {
                GalleryArtistAvatar(
                    avatarUrl = credits.avatarUrl,
                    name = artistName,
                    imageLoader = imageLoader,
                    // 与首页「正在关注的画师」同一档：两处是同一个人的同一张脸，
                    // 尺寸不同就会读成两种头像（那一档刚从 44 抬到 56，见 Spacing.kt）。
                    modifier = Modifier.size(tokens.spacing.artistAvatarSize),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    ) {
                        Text(
                            text = artistName,
                            fontSize = tokens.type.display,
                            fontWeight = tokens.type.weightSemibold,
                            color = tokens.color.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // 站点徽标必须留在屏上：本页**不跨站合并**（一站一条关注），
                        // 而下面的作品 / 别名都只属于这一站 —— 不写出来用户没法判断自己在看哪一站。
                        VeneraGallerySourceMark(site = site)
                    }
                    if (aliasLine != null) {
                        Spacer(modifier = Modifier.height(tokens.spacing.space2))
                        Text(
                            text = aliasLine,
                            fontSize = tokens.type.body,
                            color = tokens.color.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // ── 外链一整排：不受画师行那 MAX_ICONS=2 的约束（用户 2026-10-01 点名"全部展开"），
        //    认不出平台的也照摆，徽标退中性色 + 通用链接字形。
        if (credits.links.isNotEmpty()) {
            Spacer(modifier = Modifier.height(tokens.spacing.space6))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing),
            ) {
                credits.links.forEach { link ->
                    VeneraChip(
                        text = GalleryArtistLinks.chipLabel(link),
                        variant = VeneraChipVariant.Assist,
                        leading = { PlatformBadge(link.platform) },
                        onClick = { openExternal(context, link.url) },
                    )
                }
            }
        }

        // ── 动作排：两件真事，各自一条（**不摆按不动的钮** —— 参考图最右那枚「⋮」里
        //    放的是举报/屏蔽这类动作，本仓没有对应的语义可承接，画出来就是个假开关）。
        Spacer(modifier = Modifier.height(tokens.spacing.space6))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
        ) {
            ArtistActionPill(
                text = if (following) "已关注" else "关注",
                primary = true,
                onClick = { scope.launch { store.toggle(site, artistName) } },
                modifier = Modifier.weight(1f),
            )
            ArtistActionPill(
                text = "看 TA 全部作品",
                primary = false,
                onClick = onBrowseAllWorks,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(tokens.spacing.space8))

        ArtistSectionRule(title = "按分数排的热门作品")

        Spacer(modifier = Modifier.height(tokens.spacing.space5))

        GalleryArtistPostGrid(
            wall = wall,
            titles = titles,
            imageLoader = imageLoader,
            postsFailed = postsFailed,
            site = site,
            onOpen = { card, rect ->
                // 与墙上那张卡（`GalleryPostCard`）同一个动作：趁卡片还在屏上把**封面那一块**截下来。
                GalleryFlyIn.capture(view, rect)
                (context as? Activity)?.openGalleryPost(card.post.site, card.post.id)
            },
        )
    }
}

/**
 * 「热门作品」那面墙：**按行**摆、**钉死卡高**、卡位恒 [SLOT_COUNT]。
 *
 * ## 为什么钉死卡高
 *
 * 这一格是 `Row + weight(1f)` 的**行式**排法（不是首页主墙那种列式瀑布流），
 * 行与行必然对齐。而卡高取自 `post.cardRatio` ⇒ 一行三张里最矮的那张底下会留一块空洞，
 * 数据比例一参差整面墙就读成"没排齐"（真机截图上能直接看到，用户这一轮的原话是"感觉不太行"）。
 * 钉死 3:4（`coverAspectRatio`，现成 token）之后一行三张等高。
 * **代价如实说**：原图会被 `ContentScale.Crop` 裁掉两侧或上下 —— 这是"整齐"换"信息完整"，
 * 点开大图看的仍是全图。首页主墙那种瀑布流**不该**跟着钉（那里列与列各自往下排，错落是刻意的）。
 *
 * ## 为什么卡位恒 SLOT_COUNT
 *
 * 未回来时那一节摆 [SLOT_COUNT] 个空位（骨架由 [GalleryPostCard] 自己画），回来后有几张摆几张、
 * 剩下的位置留空 —— **不塌成两行**：节高跟着数据变会让"内容自己长出来"读成像卡顿，
 * 而空位不画边框也不会有人以为那里少了什么。
 *
 * 卡用墙上那一枚（[GalleryPostCard]），屏蔽与分级也走**同一把** [buildGalleryWall]：
 * 同一张图在首页预览、主墙、介绍页三处必须同样遮或不遮（那是"同一页面两处待遇不同"的同一类错）。
 */
@Composable
private fun GalleryArtistPostGrid(
    wall: GalleryWall,
    titles: Map<String, String>,
    imageLoader: ImageLoader,
    postsFailed: Boolean,
    site: GallerySite,
    /** 第二枚参数是封面那一块的窗口矩形 —— 大图页的飞行起点要它，见 `GalleryPostCard` 的 `onOpen`。 */
    onOpen: (GalleryCard, Rect) -> Unit,
) {
    val tokens = VeneraTokens
    if (postsFailed && wall.cards.isEmpty()) {
        // 失败那一档**在屏上说得出**："这一站没答上"是真话，与"这位只有这几张"不是一回事。
        // 摆六个空位等一笔不会再回来的请求，读起来就是"这页卡住了"。
        Text(
            text = if (site == GallerySite.GELBOORU) "这一站这次没答上（可能要先在漫画侧过一次验证）"
            else "这一站这次没答上",
            fontSize = tokens.type.caption,
            color = tokens.color.textSecondary,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap)) {
        // 卡位恒 SLOT_COUNT：行与列都按固定数生成，数据多少只改"这一格有没有卡"。
        (0 until SLOT_COUNT).chunked(COLUMNS).forEach { rowSlots ->
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap)) {
                rowSlots.forEach { index ->
                    Box(modifier = Modifier.weight(1f)) {
                        wall.cards.getOrNull(index)?.let { card ->
                            GalleryPostCard(
                                card = card,
                                imageLoader = imageLoader,
                                titles = titles,
                                fixedRatio = tokens.spacing.coverAspectRatio,
                                onOpen = { rect -> onOpen(card, rect) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一条动作（关注 / 看 TA 全部作品）。**大号圆角矩形**，不是胶囊 ——
 * 参考图那一排就是这一档，而 `VeneraChip` 的 28dp 全圆是"标签"的形状，
 * 摆两枚主行动会读成"这里有两枚筛选条件"。
 *
 * 主行动取 `primary` 实底（与漫画详情页那枚「开始阅读」同一档），
 * 次要的走 `surfaceContainerHigh` + 发丝描边 —— 两者必须是**不同的形状语言**，
 * 否则用户没法一眼看出哪个是这页最该按的钮。
 */
@Composable
private fun ArtistActionPill(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(tokens.shape.large)
    Surface(
        shape = shape,
        color = if (primary) tokens.color.primary else tokens.color.surfaceContainerHigh,
        border = if (primary) null else BorderStroke(tokens.spacing.hairlineThin, tokens.color.outlineVariant),
        modifier = modifier
            .height(tokens.spacing.segmentedHeight)
            .clickable { onClick() },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = tokens.type.itemTitle,
                fontWeight = tokens.type.weightMedium,
                color = if (primary) tokens.color.onPrimary else tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 居中的节标题，两侧各一条发丝线（参考图那一档）。
 *
 * 与 [com.venera.compose.feature.MiuixSectionHeader] 的分工：那个是**左对齐 + 行尾动作**
 * （首页三节用它），这个只有标题、不带动作 —— 本页那枚「看 TA 全部作品」已经搬进动作排，
 * 再在节头摆一枚就成了同一个动作长在两处。
 */
@Composable
private fun ArtistSectionRule(title: String) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space7),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionRuleLine()
        Text(
            text = title,
            fontSize = tokens.type.itemTitle,
            fontWeight = tokens.type.weightSemibold,
            color = tokens.color.textPrimary,
            maxLines = 1,
        )
        SectionRuleLine()
    }
}

/**
 * 节标题两侧那条发丝线。
 *
 * 独立成 [RowScope] 扩展而不是就地算一个 `Modifier` 变量：`weight` 是 `RowScope` 的扩展，
 * 在 `Row` 外面算出来的 Modifier 根本挂不上（这里编译报错过一次）。
 */
@Composable
private fun RowScope.SectionRuleLine() {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .weight(1f)
            .height(tokens.spacing.hairline)
            .background(tokens.color.outlineVariant),
    )
}

/*
 * `PlatformBadge`（那枚品牌色小方块）2026-10-01 **搬到 `GalleryArtistRows.kt` 了**，
 * 改成 `internal` 两处共用：信息卡里那一行外链（`GalleryArtistCreditRows`）现在也要摆它。
 * 同一枚东西在两个文件里各留一份实现，迟早会漂成两种样子
 * （`GalleryArtistLinks.chipLabel` 那条注释记着同一类教训）。
 *
 * 迁到 `gallery/ui` 而不是上提 `components/venera`：它依赖 `GalleryPlatformColors`
 * 与 domain 层的 `GalleryArtistLinkPlatform`，上提会让基础组件层挂上画廊语义。
 */

/** 一次进页要摆的三样读数。`loaded` 与"空"是两件事，见头注那条红线。 */
private class ProfileCredits(
    val links: List<GalleryArtistLink> = emptyList(),
    val aliasReadout: GalleryArtistProfile.AliasReadout = GalleryArtistProfile.AliasReadout(emptyList(), 0),
    val canonicalName: String? = null,
    val avatarUrl: String? = null,
)

/**
 * 介绍页头部那三样：外链全排、别名、头像（外加 yande.re 的正名指针）。
 *
 * 与详情面板那排入口共用 [resolveArtistAvatar] 那份头像试法（pixiv 用户档 → fanbox → Mastodon → 首字母座），
 * 失败只在日志里留话。取数一笔都不多花：外链与别名是**同一条记录的两个字段**。
 */
private suspend fun loadCredits(
    context: Context,
    site: GallerySite,
    name: String,
    source: String,
): ProfileCredits {
    val urls: List<String>
    val otherNames: List<String>
    var canonical: String? = null
    when (site) {
        GallerySite.YANDERE -> {
            val client = YandeReClient.getInstance(context)
            urls = client.artistLinks(name).getOrElse { failure ->
                Log.w(TAG, "画师「$name」(yande.re) 的外链没取到：${failure.message}")
                emptyList()
            }
            // 探针量过：这一站的别名表结构性够不着（见文件头），所以这里连请求都不多发。
            otherNames = emptyList()
            canonical = client.resolveArtistAlias(name).getOrElse { failure ->
                Log.w(TAG, "画师「$name」(yande.re) 的正名没取到：${failure.message}")
                null
            }
        }
        GallerySite.GELBOORU -> {
            val record = DanbooruArtistClient.getInstance(context).artistCredits(name).getOrElse { failure ->
                Log.w(TAG, "画师「$name」(danbooru 供体) 的记录没取到（多半是 403 卡盾）：${failure.message}")
                null
            }
            urls = record?.urls.orEmpty()
            otherNames = record?.otherNames.orEmpty()
        }
    }
    val preliminary = GalleryArtistProfile.profilePlan(urls, source = source)
    // 只有这一排里**没有 pixiv 那一档**才值得多花一笔去反查作品作者（与详情面板同一条闸门）。
    val author = if (preliminary.any { it.platform == GalleryArtistLinkPlatform.PIXIV }) null
    else GalleryArtistLinks.pixivArtworkId(source)?.let { artworkId ->
        PixivClient.getInstance(context).artworkAuthor(artworkId).getOrElse { failure ->
            Log.w(TAG, "画师「$name」的出处是作品 $artworkId，但 pixiv 没答上作者：${failure.message}")
            null
        }
    }
    val plan = if (author == null) preliminary else GalleryArtistProfile.profilePlan(
        urls = urls,
        source = source,
        artworkAuthorUrl = "https://www.pixiv.net/users/${author.userId}",
        artworkAuthorAccount = author.account,
    )
    val readout = GalleryArtistProfile.aliasReadout(otherNames, name)
    return ProfileCredits(
        links = plan,
        aliasReadout = readout,
        canonicalName = canonical,
        avatarUrl = resolveArtistAvatar(context, name, plan, source),
    )
}

private fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Log.w(TAG, "外链打不开：$url（${it.message}）")
    }
}

/** Popular posts 那一节的卡位数（3 列 × 2 行，用户 2026-10-01 拍的默认档）。 */
private const val SLOT_COUNT = 6
private const val COLUMNS = 3

/** 多取一些再按屏蔽判据筛：筛完还剩几张就摆几张，取刚好 6 会让那一节经常缺角。 */
private const val SLOT_FETCH_SIZE = 18

private const val TAG = "GalleryArtistProfile"
