package com.venera.compose.gallery.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.venera.compose.gallery.data.PixivAuthState
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.wideScreenLayoutMode
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.rememberImageWallColumnCount
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.veneraAvatarRing
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.getInstance
import com.venera.compose.gallery.data.PixivArtistSection
import com.venera.compose.gallery.data.PixivUserDetail
import com.venera.compose.gallery.data.PixivUserHead
import com.venera.compose.gallery.data.PixivUserWorks
import com.venera.compose.gallery.data.PixivRestrict
import com.venera.compose.gallery.data.PixivWork
import com.venera.compose.gallery.domain.GalleryArtistFollows
import com.venera.compose.gallery.domain.GalleryArtistLinks
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.compose.ui.tokens.ImageOverlayColors
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/**
 * **pixiv 画师页**（2026-10-19）。图库三站那屏（[GalleryArtistProfileScreen]）一行未改。
 *
 * ## 为什么必须另开一屏
 *
 * 那屏的骨架是 booru 的查询语言：`GalleryRankings.searchQuery(site, filters, ranking)` 拼出
 * `标签 order:score` 那一串。pixiv 一条都不吃它 —— `suffix(PIXIV, …)` 走 `GallerySite.notWired()`
 * **直接抛**，这就是"点画师闪退"的根因。而 pixiv 的画师身份是 `user_id`、内容在
 * `/ajax/user/{id}/…` 三条匿名腿上，两套形状没有一个可复用点。
 *
 * ## 这一屏的四件实事
 *
 * 1. **hero 横图 = 作者自设背景**（`body.background.url`，站方已裁成 1920×960 的横档）。
 *    没设置就回退到**他的第一张插画**，再没有才是一块卡面色块 —— 三档回退，不留半截实现。
 *    高度按宽度算再夹（`pixivArtistHeroMin/MaxHeight`），**不用定高**：定高在平板上会被拉成
 *    9.6:1 的扁带（那是 `artistHeroHeightWide` 记下的旧账）。
 * 2. **三段：插画 / 漫画 / 收藏**（用户点名）。前两段的 id 表来自 `profile/all`，
 *    收藏段要票据腿（未登录那档见 [BookmarkLocked]）。
 * 3. **头部跟着滚走**（用户 2026-10-19 拍板）：整页一条纵向流，hero/头像/名字/简介/数据行/
 *    外链/三段切换全是 `item(span = FullLine)`，卡片才是网格项 —— 与 `GalleryScreen` 主墙
 *    同一种写法，不另造容器。
 * 4. **点画师头像进这一页**：跳转在调用点（`GalleryCardCaption` / 首页横排 / 搜索分栏），
 *    本屏只负责"进来之后显示什么"。
 *
 * ## 匿名腿够不够
 *
 * 够。三条匿名腿（`userHead` / `profileAll` / `worksByIds`）把头部、id 表、卡片全给齐，
 * 收藏段是唯一要票据的那一段 —— 且它**失败的时候页面其余部分照旧完整**，
 * 所以"没登录"在这里是一个**有出口的空态**，不是整页失败。
 */
@Composable
internal fun PixivArtistScreen(
    artistName: String,
    source: String,
    onBack: () -> Unit,
    onOpenPost: (GalleryPost) -> Unit,
) {
    val context = LocalContext.current
    val tokens = VeneraTokens
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val imageLoader: ImageLoader = remember { GalleryImageLoader.get(context) }
    val ports = remember(context) { GalleryPorts.of(context) }
    val guard = remember(context) { ports.contentGuard }
    val maskMode by guard.nsfwMaskMode.collectAsState()
    val blockAi by ports.prefs.galleryBlockAi.collectAsState()
    val follows by remember { ports.follows }.follows.collectAsState()
    /**
     * 登录态只从 `authState` 那一条流读（与顶栏那颗头像、首页「已关注」同一源）。
     * 收藏段那三档判据全靠它 —— 另算一遍就是第二份"谁在用这个账号"。
     */
    val pixivAuth = ports.pixivAccount?.authState?.collectAsState()?.value ?: PixivAuthState.Anonymous

    // 用户号：**从 source 取，不从名字猜**（名字可改可重名；拿名字当身份会串人）。
    val userId = remember(source) { GalleryArtistLinks.pixivUserId(source) }

    val layoutMode = wideScreenLayoutMode(LocalConfiguration.current.screenWidthDp.dp)
    val wideScreen = layoutMode != WideScreenLayoutMode.Compact
    val contentMaxWidth = contentMaxWidthFor(layoutMode)
    val columnMode by ports.prefs.galleryColumnMode.collectAsState()
    val columns = GallerySettingsModel.gridColumns(columnMode, rememberImageWallColumnCount())

    // ── 这一页的状态（刻意不进任何全局 store：画师页是一屏一人的快照，
    //    切走再进来重取一次是可接受的代价，而"进过一次就永远显示旧数据"才是错）──
    var head by remember(userId) { mutableStateOf<PixivUserHead?>(null) }
    var headFailed by remember(userId) { mutableStateOf(false) }
    var works by remember(userId) { mutableStateOf<PixivUserWorks?>(null) }
    var tab by remember(userId) { mutableStateOf(PixivArtistSection.ILLUST) }
    var cards by remember(userId) { mutableStateOf<List<PixivWork>>(emptyList()) }
    var loadingBatch by remember(userId) { mutableStateOf(false) }
    var batchFailed by remember(userId) { mutableStateOf(false) }
    /**
     * **作品索引那一腿**（`profile/all`）失败了没有。
     *
     * 2026-10-19 真机读数补上这一格：从前这一腿只 `Log.w`、不置任何状态位，于是它一抛
     * 整屏就是"四格全「—」+ 一张卡都没有 + 一句失败话都没有"（用户报的"很多画师页不出图"）。
     * 索引是这一屏的**唯一 id 来源**，它断了后面那批根本不会发 —— 那种"断在第一跳"的处境
     * 必须与"这一批没取上"（[batchFailed]）分开念，也要与"这位真的没有投稿"分开念。
     */
    var worksFailed by remember(userId) { mutableStateOf(false) }
    /** 已经取到第几批（切 tab 归零）。**只有匿名档用它** —— 票据档的游标是站方的 `next_url`。 */
    var batchIndex by remember(userId) { mutableIntStateOf(0) }

    /**
     * **站方那一份读数**（`/v1/user/detail`，要票据）：投稿总数、粉丝数、以及**我是否关注了这位**。
     *
     * null = 没票据或那一腿没答上 ⇒ 整屏退匿名档，且屏上要说出来（见 [anonymousReason]）。
     */
    var detail by remember(userId) { mutableStateOf<PixivUserDetail?>(null) }
    /**
     * 这一屏走哪条腿。true = 票据（`/v1/user/illusts`，含成人档、一页带全字段）；
     * false = 匿名兜底（`profile/all` 的 id 表 + `profile/illusts` 按 id 取卡）。
     *
     * 它不是"多一个开关"而是**作品来源本身**：两条腿的游标不同（`next_url` vs id 表偏移），
     * 所以每一颗取数 effect 都要先问它，不然两条腿会同时往 `cards` 上写。
     */
    var ticketMode by remember(userId) { mutableStateOf(false) }
    /**
     * 为什么这一屏是匿名档。null = 走的是票据档（什么都不摆）。
     *
     * 这句话**必须上屏**：匿名档的张数是站方过滤过的（2026-10-19 读数：屏上 3 张 / 网页 26 张），
     * 不说就等于把"少给"演成"这位只有这些"。用户拍的是「退匿名腿 + 屏上明说」。
     */
    var anonymousReason by remember(userId) { mutableStateOf<String?>(null) }
    /** 票据档的游标（站方 `next_url`；null = 到底了）。 */
    var nextUrl by remember(userId, tab) { mutableStateOf<String?>(null) }
    /** 票据档第一页**答过了**（成功或失败都算）—— 页尾那格靠它区分"没到底"与"还没开始"。 */
    var ticketFirstDone by remember(userId, tab) { mutableStateOf(false) }
    /** 续页触发：页尾每要一次抬一格。第一页由 [tab] 自己带，所以 0 表示"别发"。 */
    var moreTick by remember(userId, tab) { mutableIntStateOf(0) }
    /** 关注写腿在跑（按下去到站方答上之间那一段），与上一笔的站方原话。 */
    var followBusy by remember(userId) { mutableStateOf(false) }
    var followError by remember(userId) { mutableStateOf<String?>(null) }
    /**
     * 用户按「重试」抬一次。
     *
     * 为什么不是"把 batchIndex 减一再加一"：那样会把**已经取到的那一批**当成"没取到"再拼一遍，
     * 而页尾那条注释定的是"失败不清屏" —— 重试只该重发**失败那一批**，前面那些留在屏上。
     */
    var retryTick by remember(userId) { mutableIntStateOf(0) }

    /**
     * 收藏段的状态（2026-10-19）。
     *
     * 这一段与前两段**不是同一条腿**：前两段是匿名（`profile/all` 的 id 表 + `profile/illusts` 按批），
     * 收藏要票据（`app-api /v1/user/bookmarks/illust`）。所以它自己一套装载态，
     * 且**失败只影响这一段**（前两段照旧完整）。
     */
    var bookmarkWorks by remember(userId) { mutableStateOf<List<PixivWork>>(emptyList()) }
    var bookmarkNext by remember(userId) { mutableStateOf<String?>(null) }
    var bookmarkLoading by remember(userId) { mutableStateOf(false) }
    /**
     * 收藏段"为什么读不到"那一句话。null = 没失败。
     *
     * 三种成因**分开念**（合成一句就等于替用户下了一个它没下的判断）：
     * 未登录 / 票据这一会儿取不到 / 站方 401 或 403。
     */
    var bookmarkError by remember(userId) { mutableStateOf<String?>(null) }
    /** 已经向站方要过第一页（切回来不再重打一次；登录态一变由下面那条 effect 作废）。 */
    var bookmarkAsked by remember(userId) { mutableStateOf(false) }

    val gridState = rememberLazyStaggeredGridState()

    // ── 头部 + 选腿（票据优先，匿名兜底）──────────────────────────────────
    //
    // 走契约（`ports.pixiv`）而不是直连 `PixivClient`：本仓有架构守卫钉着
    // "UI 不许直连实现类，白名单只许缩短"（`BusinessApiBoundaryTest`），要开口就加契约。
    LaunchedEffect(userId, retryTick) {
        val id = userId ?: return@LaunchedEffect
        val client = ports.pixiv ?: return@LaunchedEffect
        // 头部这一跳**匿名也读得到**（横图 / 简介 / 外链只有 www 那一腿给），所以两条路都打它。
        client.userHead(id).onFailure {
            Log.w(TAG, "画师 $id 的头部没取到：${it.message}")
            headFailed = true
        }.onSuccess { head = it }

        val bearer = ports.pixivAccount?.bearerOrNull()
        if (bearer == null) {
            ticketMode = false
            detail = null
            anonymousReason = "没有可用的登录票据"
        } else {
            client.userDetail(bearer, id.toString()).onFailure { failure ->
                Log.w(TAG, "画师 $id 的站方详情没取到，整屏退匿名档：${failure.message}")
                detail = null
                ticketMode = false
                anonymousReason = "站方详情这一跳没答上（${failure.message ?: "未知原因"}）"
            }.onSuccess { stats ->
                detail = stats
                ticketMode = true
                anonymousReason = null
            }
        }
        // 匿名 id 表**只在退到匿名档时**才是作品的来源。票据档不发这一跳：
        // 它按站方口径把成人档整批滤掉（2026-10-19 读数：屏上 3 张 / 网页 26 张），
        // 而票据档的作品另有自己那条腿（`/v1/user/illusts`）。
        if (!ticketMode) {
            client.profileAll(id).onFailure {
                Log.w(TAG, "画师 $id 的作品索引没取到：${it.message}")
                worksFailed = true
            }.onSuccess { index ->
                works = index
                worksFailed = false
            }
        }
    }

    // ── 票据档：一次一页带全字段的作品（含成人档），游标是站方的 next_url ──
    //
    // ⚠️ 收藏段**必须在这一颗之外**：它有自己的书签腿（`/v1/user/bookmarks/illust`，
    // 游标是 `max_bookmark_id`），而 `ticketType()` 落到 BOOKMARK 是 `error(...)` ——
    // 少这一道闸门，"切到收藏"这个动作本身就会把这一屏抛掉。
    LaunchedEffect(userId, tab, ticketMode, retryTick) {
        if (!ticketMode) return@LaunchedEffect
        if (tab == PixivArtistSection.BOOKMARK) return@LaunchedEffect
        val id = userId ?: return@LaunchedEffect
        val client = ports.pixiv ?: return@LaunchedEffect
        val bearer = ports.pixivAccount?.bearerOrNull() ?: return@LaunchedEffect
        loadingBatch = true
        batchFailed = false
        ticketFirstDone = false
        client.userIllusts(bearer, id.toString(), tab.ticketType()).onFailure { failure ->
            Log.w(TAG, "画师 $id 的${tab.label}第一页没取到：${failure.message}")
            batchFailed = true
        }.onSuccess { page ->
            cards = page.works
            nextUrl = page.nextUrl
        }
        ticketFirstDone = true
        loadingBatch = false
    }

    // ── 票据档的续页（页尾每要一次抬一格 moreTick）────────────────────────
    LaunchedEffect(userId, tab, moreTick) {
        if (!ticketMode || moreTick == 0) return@LaunchedEffect
        if (tab == PixivArtistSection.BOOKMARK) return@LaunchedEffect
        val id = userId ?: return@LaunchedEffect
        val client = ports.pixiv ?: return@LaunchedEffect
        val next = nextUrl ?: return@LaunchedEffect
        val bearer = ports.pixivAccount?.bearerOrNull() ?: return@LaunchedEffect
        loadingBatch = true
        batchFailed = false
        client.userIllusts(bearer, id.toString(), tab.ticketType(), next).onFailure { failure ->
            // 续页失败**不清屏**（按一下等于删一屏），也不置到底 —— 再划一下会重判。
            Log.w(TAG, "画师 $id 的${tab.label}续页没取到：${failure.message}")
            batchFailed = true
        }.onSuccess { page ->
            val seen = cards.mapTo(HashSet()) { it.post.uid }
            cards = cards + page.works.filter { it.post.uid !in seen }
            nextUrl = page.nextUrl
        }
        loadingBatch = false
    }

    // ── 匿名档：每批 24 枚 id（站方实测通过的上限口径）────────────────────
    val batchIds: List<Long> = remember(works, tab, batchIndex) {
        val all = works?.idsFor(tab).orEmpty()
        all.drop(batchIndex * BATCH_SIZE).take(BATCH_SIZE)
    }
    LaunchedEffect(userId, tab, batchIds, retryTick) {
        // 票据档**不发这一跳**：作品的来源已经换成 `/v1/user/illusts`，两条腿同时往 `cards`
        // 上写就会打架（谁后回来谁赢，屏上的张数跟着网络抖动）。
        if (ticketMode) return@LaunchedEffect
        val id = userId ?: return@LaunchedEffect
        if (batchIds.isEmpty()) return@LaunchedEffect
        val client = ports.pixiv ?: return@LaunchedEffect
        loadingBatch = true
        batchFailed = false
        client.worksByIds(
            userId = id,
            ids = batchIds,
            workCategory = if (tab == PixivArtistSection.MANGA) "manga" else "illust",
            firstPage = batchIndex == 0,
        ).onFailure {
            Log.w(TAG, "画师 $id 的第 ${batchIndex + 1} 批没取到：${it.message}")
            batchFailed = true
        }.onSuccess { found ->
            val fresh = found.filterNotNull()
            cards = if (batchIndex == 0) fresh else (cards + fresh)
        }
        loadingBatch = false
    }

    // ── 收藏段的第一页（要票据；未登录那一档不置失败态，见 BookmarkLocked）────
    LaunchedEffect(userId, tab, pixivAuth) {
        val id = userId ?: return@LaunchedEffect
        if (tab != PixivArtistSection.BOOKMARK) return@LaunchedEffect
        if (bookmarkAsked) return@LaunchedEffect
        // 未登录：不读、不置失败 —— 屏上摆的是"为什么读不到 + 怎么才读得到"那个有出口的空态。
        if (pixivAuth !is PixivAuthState.SignedIn) {
            bookmarkError = null
            bookmarkLoading = false
            return@LaunchedEffect
        }
        val bearer = ports.pixivAccount?.bearerOrNull()
        if (bearer == null) {
            // 票据**这一刻**取不到（刚过期之类），不记账：下一次组合还能再试。
            bookmarkError = "这枚登录票据这一会儿取不到（可能刚过期）—— 点顶栏那颗头像重新登录一次。"
            bookmarkLoading = false
            return@LaunchedEffect
        }
        bookmarkLoading = true
        bookmarkError = null
        // `bookmarkIllusts` 的 `userId` 就是"读**这位**的公开收藏"—— 那条腿自己带
        // `restrict=public`，读不到私密档（那是用户给站方划的界，见文件头）。
        ports.pixiv?.bookmarkedIllusts(bearer, id.toString(), PixivRestrict.Public)?.onFailure { failure ->
            Log.w(TAG, "画师 $id 的公开收藏没取到：${failure.message}")
            bookmarkError = failure.message?.takeIf { it.isNotBlank() } ?: "站方没答上"
        }?.onSuccess { page ->
            bookmarkWorks = page.works
            bookmarkNext = page.nextUrl
            bookmarkAsked = true
        }
        bookmarkLoading = false
    }

    // ── 收藏段的续页（游标 = 站方给的 next_url）──────────────────────────
    LaunchedEffect(gridState, tab, userId, bookmarkNext) {
        if (tab != PixivArtistSection.BOOKMARK) return@LaunchedEffect
        val id = userId ?: return@LaunchedEffect
        // 三道闸门（与云端书签墙同一套）：有墙、没到底、没在忙。
        if (bookmarkWorks.isEmpty() || bookmarkLoading) return@LaunchedEffect
        snapshotFlow {
            val info = gridState.layoutInfo
            GalleryLoadMoreState(
                wallActive = bookmarkWorks.isNotEmpty(),
                exhausted = bookmarkNext == null,
                busy = bookmarkLoading,
                failed = bookmarkError != null,
                settled = true,
                lastIndex = info.totalItemsCount - 1,
                visibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1,
            )
        }
            .distinctUntilChanged()
            .collect { state ->
                if (!state.wallActive || state.exhausted || state.busy) return@collect
                if (state.lastIndex < 0 || state.visibleIndex < state.lastIndex - LOAD_MORE_AHEAD) return@collect
                val next = bookmarkNext ?: return@collect
                val bearer = ports.pixivAccount?.bearerOrNull() ?: run {
                    // 票据这一刻取不到：这一轮到此为止，别把页脚永远转着。
                    bookmarkNext = null
                    return@collect
                }
                bookmarkLoading = true
                ports.pixiv?.bookmarkedIllusts(bearer, id.toString(), PixivRestrict.Public, next)?.onFailure { failure ->
                    // 续页失败**不清屏**（按一下等于删一屏），也不置到底 —— 再划一下会重判。
                    Log.w(TAG, "画师 $id 的公开收藏续页没取到：${failure.message}")
                }?.onSuccess { page ->
                    val seen = bookmarkWorks.mapTo(HashSet()) { it.post.uid }
                    bookmarkWorks = bookmarkWorks + page.works.filter { it.post.uid !in seen }
                    bookmarkNext = page.nextUrl
                }
                bookmarkLoading = false
            }
    }

    // ── 切段：只管把墙滚回顶 ────────────────────────────────────────────
    //
    // 从前这一颗还顺手做 `cards = emptyList(); batchIndex = 0` —— **今天必须搬走**：
    // 两条"第一页"effect 都以 `tab` 为 key 且注册在这一颗**之前**，同一帧里它们先跑完、
    // 这一颗再把刚取回的那一页清空，症状正是"切完段屏上什么都没有"。
    // 清屏改到 `onPick` 那一刻做（在事件里改状态，不在组合期），顺序这件事从根上没了。
    LaunchedEffect(gridState, tab) {
        if (gridState.firstVisibleItemIndex > 0) gridState.animateScrollToItem(0)
    }

    /**
     * 这一段当前要摆的那批作品。
     *
     * 收藏段与前两段**合流到这一颗**：三段的卡片形状、屏蔽判据、点击行为必须一模一样
     * （同一张图在插画段与收藏段待遇不同，那是本仓反复立案的那族错）。
     * 差别只在"数据从哪来"：前两段吃 `cards`（匿名批），收藏段吃 `bookmarkWorks`（票据腿）。
     */
    val sectionWorks: List<PixivWork> =
        if (tab == PixivArtistSection.BOOKMARK) bookmarkWorks else cards

    val wall = remember(sectionWorks, maskMode, blockAi) {
        buildGalleryWall(sectionWorks.map { it.post }, maskMode, blockAi = blockAi) { post ->
            guard.blockedGalleryRule(author = post.author, tags = post.tagList)?.pattern
        }
    }
    val titles = remember(sectionWorks) { sectionWorks.associate { it.post.uid to it.title } }
    val avatars = remember(sectionWorks) { sectionWorks.associate { it.post.uid to it.authorAvatarUrl } }

    // 头图三档回退：自设背景 → 他的第一张插画 → 卡面色块（见文件头第 1 条）。
    // ⚠️ 回退取的是**插画段的第一张**，不是"当前这一段的第一张"：切到收藏段时头图不该
    // 换成别人的作品 —— 那会读成"这张是这位画的"。
    val heroUrl = head?.backgroundUrl
        ?: cards.firstOrNull()?.post?.previewUrl
    // 高度按宽算再夹。宽窗下与正文一起受 contentMaxWidth 收口 ⇒ 不会被拉成扁条。
    val heroHeight = if (wideScreen) tokens.spacing.pixivArtistHeroMaxHeight else tokens.spacing.pixivArtistHeroMinHeight

    // 关注态：**站方为准，本地只当缓存**（用户 2026-10-19 拍板）。
    // `is_followed` 有值就照它 —— 那是站方眼里的关系；null（没票据 / 站方没答这一格）才退本地那本账。
    //
    // ⚠️ 两本账的**键不同轨**：站方按 `user_id`，本地按画师名（名单里刻意不存编号，理由写在
    // `GalleryArtistFollowsStore` 头注）。所以退本地那一档答的其实是"这个名字我记过没有"，
    // 与站方那一格不是同一个问题 —— 这一格差别必须靠上面那句匿名档读数说出来，不许静默。
    val followingLocal = GalleryArtistFollows.isFollowing(follows, GallerySite.PIXIV, artistName)
    val following = detail?.isFollowed ?: followingLocal

    val sidePadding = tokens.spacing.screenHorizontal
    // 宽屏收口：hero 与正文同一宽度，否则外链 chips 会沿 1891dp 铺满整行。
    val capped = contentMaxWidth?.let { Modifier.widthIn(max = it) } ?: Modifier

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = tokens.spacing.space11),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
        verticalItemSpacing = tokens.spacing.gridGap,
    ) {
        // ── hero：整宽整行，跟着滚走 ──
        item(key = "hero", span = StaggeredGridItemSpan.FullLine) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .background(tokens.color.surfaceContainerHigh),
            ) {
                if (heroUrl != null) {
                    AsyncImage(
                        model = heroUrl,
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 顶上那层 scrim 是必需项：白字 pill 与状态栏图标要压在任意颜色的照片上。
                    // 复用 `ImageOverlayColors` 那两档**固定黑**（与设置页 hero 同一套色值），
                    // 不新造色 —— 图上文字的可读性靠 scrim 浓度兜底，不靠主题色
                    // （图是作者选的，亮度和色相完全不可控）。
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to ImageOverlayColors.ScrimTop,
                                    0.55f to Color.Transparent,
                                    1f to ImageOverlayColors.ScrimBottom,
                                ),
                            ),
                    )
                }
            }
        }

        // ── 头部：头像压角 + 名字 + 关注 ──
        item(key = "head", span = StaggeredGridItemSpan.FullLine) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(capped)
                    .padding(horizontal = sidePadding),
            ) {
                // 头像负 margin 咬进 hero 一截（`space11`=32dp，这里用 38dp 的视觉档）。
                Box(modifier = Modifier.padding(top = 0.dp)) {
                    PixivArtistFace(
                        avatarUrl = head?.avatarUrl,
                        name = artistName,
                        imageLoader = imageLoader,
                        modifier = Modifier.size(tokens.spacing.pixivArtistAvatarSize),
                    )
                }
                Spacer(Modifier.height(tokens.spacing.space4))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = head?.name ?: artistName,
                            fontSize = tokens.type.display,
                            fontWeight = tokens.type.weightSemibold,
                            color = tokens.color.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val account = head?.account.orEmpty()
                        if (account.isNotBlank()) {
                            Text(
                                text = "@$account",
                                fontSize = tokens.type.caption,
                                color = tokens.color.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = tokens.spacing.space1),
                            )
                        }
                    }
                    FollowPill(
                        following = following,
                        busy = followBusy,
                        onClick = {
                            val target = !following
                            scope.launch {
                                togglePixivFollow(
                                    ports = ports,
                                    userId = userId,
                                    artistName = artistName,
                                    target = target,
                                    setBusy = { followBusy = it },
                                    setNotice = { followError = it },
                                    alignSite = { detail = detail?.copy(isFollowed = it) },
                                )
                            }
                        },
                    )
                }
                // 站方那一笔的结果（成功不占这一格，按钮那两个字就是反馈）；只在"没写成"或
                // "只记了本地"时说一句 —— 按下去一声不吭，读起来就是"这钮是画的"。
                followError?.let { note ->
                    Text(
                        text = note,
                        fontSize = tokens.type.overline,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(top = tokens.spacing.space2),
                    )
                }

                val bio = head?.comment.orEmpty()
                if (bio.isNotBlank()) {
                    Text(
                        text = bio,
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = tokens.spacing.space4),
                    )
                }

                // ── 数据行：四个数各有出处，缺一格就整格不摆（不拿 0 顶）──
                PixivArtistStats(works = works, detail = detail)

                val links = head?.links.orEmpty()
                if (links.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = tokens.spacing.space6),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    ) {
                        links.take(MAX_LINKS).forEach { url ->
                            PixivLinkChip(url = url, onOpen = { openExternal(context, url) })
                        }
                    }
                }
            }
        }

        // ── 三段切换 ──
        item(key = "tabs", span = StaggeredGridItemSpan.FullLine) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(capped)
                    .padding(horizontal = sidePadding, vertical = tokens.spacing.space4),
            ) {
                PixivSectionRow(
                    selected = tab,
                    // 与数据行同一对来源：站方优先（含成人档），匿名 id 表长度只作兜底。
                    counts = mapOf(
                        PixivArtistSection.ILLUST to
                            (detail?.totalIllusts ?: works?.illusts?.size),
                        PixivArtistSection.MANGA to
                            (detail?.totalManga ?: works?.manga?.size),
                        PixivArtistSection.BOOKMARK to
                            (detail?.totalBookmarksPublic ?: works?.publicBookmarkCount),
                    ),
                    onPick = { picked ->
                        if (picked != tab) {
                            // 切段先清屏再换段（**在事件里**改状态，不在 effect 里，见上面那颗
                            // `LaunchedEffect(gridState, tab)` 的注释）。两档的游标各自归零。
                            cards = emptyList()
                            bookmarkWorks = emptyList()
                            bookmarkNext = null
                            bookmarkAsked = false
                            batchIndex = 0
                            nextUrl = null
                            ticketFirstDone = false
                            tab = picked
                        }
                    },
                )
            }
        }

        // ── 匿名档那一行：这一屏的张数是**站方过滤过**的，必须说出来 ──
        //
        // 不说的后果比看起来严重：匿名 `profile/all` 把成人档整批滤掉，屏上"插画 3"与
        // pixiv 网页"插画·漫画 26"是同一位画师的两个数，用户只能读成"这应用数错了"。
        // 这一句把成因（没票据）与影响（少给）都点名，而不是留一句"加载失败"。
        anonymousReason?.let { reason ->
            item(key = "anonymous-notice", span = StaggeredGridItemSpan.FullLine) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding)) {
                    PixivArtistLine(
                        text = "现在是匿名档（$reason）：成人档与部分作品取不到，" +
                            "上面的张数会比 pixiv 网页少。",
                    )
                }
            }
        }

        // ── 收藏段：三档成因分开念（未登录 / 票据取不到 / 站方挡下），成功后正常摆墙 ──
        if (tab == PixivArtistSection.BOOKMARK) {
            val signedIn = pixivAuth is PixivAuthState.SignedIn
            when {
                // F 档：未登录 —— **有出口的空态**，不是失败态（摆"重试"按下去还是同一句话就是假开关）。
                !signedIn -> item(key = "bookmark-locked", span = StaggeredGridItemSpan.FullLine) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding)) {
                        BookmarkLocked()
                    }
                }
                // H 档：401 / 403 或票据这一刻取不到 —— 站方原话上屏，不合并成"没取到"。
                bookmarkError != null && bookmarkWorks.isEmpty() -> {
                    item(key = "bookmark-failed", span = StaggeredGridItemSpan.FullLine) {
                        PixivArtistLine(
                            text = "$bookmarkError（点这里重试）",
                            onClick = { bookmarkAsked = false },
                        )
                    }
                }
                // G 档：登录着、答上了、一张都没有 = 真读数。
                !bookmarkLoading && bookmarkWorks.isEmpty() && bookmarkAsked ->
                    item(key = "bookmark-empty", span = StaggeredGridItemSpan.FullLine) {
                        PixivArtistLine(text = "这一位没有公开收藏")
                    }
                // 还在取第一页。
                bookmarkLoading && bookmarkWorks.isEmpty() ->
                    item(key = "bookmark-loading", span = StaggeredGridItemSpan.FullLine) {
                        PixivArtistLine(text = "正在取公开收藏…")
                    }
            }
        } else if (!ticketMode && worksFailed && cards.isEmpty()) {
            // 断在**第一跳**（匿名 id 表没拿到，后面那批压根没发）。这句必须与"这一批没取上"分开念
            // —— 后者屏上通常还留着前几批，重试只补失败那一批；这一档是什么都没有。
            item(key = "index-failed", span = StaggeredGridItemSpan.FullLine) {
                PixivArtistLine(
                    text = "这一位的作品索引没取到 —— 点这里重试",
                    onClick = { retryTick += 1 },
                )
            }
        } else if (batchFailed && cards.isEmpty()) {
            // "这一批没取上" —— 可点是重试；**已取到的留着**（见页尾那颗的注释）。
            item(key = "failed", span = StaggeredGridItemSpan.FullLine) {
                PixivArtistLine(
                    text = "这一批没取上 —— 点这里重试",
                    onClick = { retryTick += 1 },
                )
            }
        } else if (!loadingBatch && cards.isEmpty() &&
            (if (ticketMode) ticketFirstDone else works != null)
        ) {
            // "空"有**三种**成因，合成一句就是假话。2026-10-19 真机读数立案：那一屏同时挂着
            // 「这一位还没有插画作品」与「已经到底了 · 共 3 张」—— 一句假一句真，
            // 而那两句本该说的是同一件事。假的那句来自"把没取回当成没有"。
            val idCount = works?.idsFor(tab)?.size
            item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                when {
                    // 站方那一腿答上了、这一桶就是零条 —— 这一档才配说"没有"。
                    ticketMode || idCount == 0 ->
                        PixivArtistLine(text = "这一位没有${tab.label}作品")
                    // id 表报了 N 条却一条内容都没回来：那是**取数没成**，不是"这位没有"。
                    // 重试要挂着，否则这一屏就是一句读不出真相的死话。
                    else -> PixivArtistLine(
                        text = "站方说这一位有 $idCount 条${tab.label}，但一条内容都没取回 —— 点这里重试",
                        onClick = { retryTick += 1 },
                    )
                }
            }
        }

        // ── 卡片墙：三段共用，与上面那几档状态行**互不排斥** ──
        //
        // 从前这一句挂在上面那条 if-else 链的 `else` 分支上，于是收藏段（走**第一个**分支）
        // 永远走不到摆卡 —— 2026-10-19 真机读数：页尾念「已经到底了 · 共 285 张」
        // （`bookmarkWorks` 确实装着 285 条），屏上一张卡都没有。
        // 那句"共 285 张"来自 `bookmarkWorks.size`，所以这一屏同时给出了"取到了"与"什么都没画"
        // 两个事实 —— 而它读起来只像"取不到"。
        //
        // 上面每一档的判据里都带着"这一屏没有内容"（`cards.isEmpty()` / `bookmarkWorks.isEmpty()`），
        // 所以它们与摆卡不会同时成立；这里再叠一层 `isNotEmpty()` 只是让"空表摆 0 项"这件事
        // 写在明面上，不靠 items 对空集合的默认行为。
        if (wall.cards.isNotEmpty()) {
            items(wall.cards, key = { it.post.uid }) { card ->
                // 头像那一下去谁家 —— **收藏段和另外两段不是同一个答案**：
                // - 插画 / 漫画段：作者恒是这位画师 ⇒ 头像指回本页（点了等于原地不动，所以**不挂**）；
                // - 收藏段：作者是**别人** ⇒ 点头像进那位作者的画师页（这才是用户要的那一下）。
                //
                // 判据用 `post.author` 与 `artistName` 比对做不到（同名不同人是真实存在的），
                // 所以按**段**分：收藏段的 `source` 里就是那位原作者的用户号。
                val authorEntry: (() -> Unit)? =
                    if (tab == PixivArtistSection.BOOKMARK) {
                        GalleryArtistLinks.pixivUserId(card.post.source)?.let { authorId ->
                            { context.openPixivArtistPage(authorId, card.post.author) }
                        }
                    } else {
                        null
                    }
                GalleryPostCard(
                    card = card,
                    imageLoader = imageLoader,
                    titles = titles,
                    authorAvatar = avatars[card.post.uid],
                    onOpenArtist = authorEntry,
                    onOpen = { rect: Rect ->
                        // 与墙上那张卡同一个动作：趁卡片还在屏上截下封面那一块。
                        // 队列一起交 —— 少了它大图页认不出"同墙的那批"。
                        GalleryViewerQueue.set(wall.cards.map { it.post })
                        GalleryFlyIn.capture(view, rect)
                        onOpenPost(card.post)
                    },
                )
            }
        }

        // ── 页尾：还有下一批就续，到底了就念总张数 ──
        //
        // 两段的"还有没有下一批"不是同一样东西：
        // - 插画 / 漫画：游标是那张 id 表本身（`batchIndex` 往后走）；
        // - 收藏：游标是站方给的 `next_url`（`max_bookmark_id` 在里面，不自己拼）。
        item(key = "footer", span = StaggeredGridItemSpan.FullLine) {
            if (tab == PixivArtistSection.BOOKMARK) {
                PixivArtistFooter(
                    loading = bookmarkLoading,
                    failed = bookmarkError != null && bookmarkWorks.isNotEmpty(),
                    exhausted = bookmarkAsked && bookmarkNext == null && bookmarkError == null,
                    total = bookmarkWorks.size.takeIf { bookmarkNext == null && bookmarkAsked },
                    onLoadMore = { /* 收藏续页见下面那条 LaunchedEffect：它吃 next_url */ },
                )
            } else if (ticketMode) {
                // 票据档的游标是站方的 `next_url`，所以"还有没有下一页"只问它。
                // ⚠️ 第一页答过之前不许说"到底"：那一格初值也是 null，直接读会把
                // "还没开始"念成"已经到底了 · 共 N 张"。
                val atEnd = ticketFirstDone && nextUrl == null
                PixivArtistFooter(
                    loading = loadingBatch,
                    failed = batchFailed,
                    exhausted = atEnd,
                    // 总张数吃**站方的总数**（含成人档），不是"这一屏摆出来了几张"。
                    total = if (atEnd) {
                        if (tab == PixivArtistSection.MANGA) detail?.totalManga else detail?.totalIllusts
                    } else {
                        null
                    },
                    onLoadMore = { if (!loadingBatch) moreTick += 1 },
                )
            } else {
                PixivArtistFooter(
                    loading = loadingBatch,
                    failed = batchFailed,
                    exhausted = works != null && batchIds.isEmpty(),
                    total = works?.idsFor(tab)?.size,
                    onLoadMore = { if (!loadingBatch) batchIndex += 1 },
                )
            }
        }
    }

    // ── 悬浮返回 pill（浮在 hero 左上，滚过之后它自己就是"怎么回去"）──
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = sidePadding, top = tokens.spacing.space4),
        ) {
            VeneraTopBarPill(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = tokens.color.textPrimary,
                )
            }
        }
    }
}

/**
 * 头像那颗圆。
 *
 * 与 `GalleryArtistAvatar` 的分工：那一颗吃 `artistAvatarSize`(64dp) 且自带首字母退路，
 * 是**横排 / 信息卡**那一档的件；画师页的头像是这一页的第二主视觉（76dp），
 * 且要一圈**页面底色**描边（圆压在照片上必须有环，否则边缘被抗锯齿吃掉、读成"糊了一圈"）。
 * 尺寸与环都不同档，所以不硬套那一颗 —— 但"空档退首字母"这条纪律照它：
 * 站方没给就摆名字首字，**不拿别人的头像顶**。
 */
@Composable
private fun PixivArtistFace(
    avatarUrl: String?,
    name: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(tokens.color.surfaceVariant)
            .veneraAvatarRing(CircleShape)
            .then(
                // 页面底色那一圈：靠 border 画在内容之上（`veneraAvatarRing` 内部先 clip 再描）。
                Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                fontSize = tokens.type.statNumber,
                color = tokens.color.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 点关注那一下：**先写站方，写成了才对齐本地那本账**。
 *
 * 顺序反过来是最难查的那种错：本地先记上、站方没写成，屏上亮着"已关注"而 pixiv 那边根本没有
 * 这位 —— 用户下次进 pixiv 主页看不到他，读起来就是"应用替我关注了个寂寞"。
 * 所以 [alignSite] 只在 `onSuccess` 里调，失败那一路**本地一个字都不动**。
 *
 * 没有票据时只动本地，并把"这一笔没同步"那句话摆上屏（[setNotice]）—— 那是允许的降级，
 * 前提是她明说自己没同步；静默按本地记账、屏上装作同步过，才是本仓反复修的那一族假开关。
 *
 * @param target 按下去**之后**该是什么状态（true = 关注）。由调用方按当前显示值取反，
 *   不在这里读任何状态 —— 这里读一次快照，晚到的那一笔就会把屏上的值当成本地值。
 */
private suspend fun togglePixivFollow(
    ports: GalleryPorts,
    userId: Long?,
    artistName: String,
    target: Boolean,
    setBusy: (Boolean) -> Unit,
    setNotice: (String?) -> Unit,
    alignSite: (Boolean) -> Unit,
) {
    val id = userId ?: return
    val bearer = ports.pixivAccount?.bearerOrNull()
    if (bearer == null) {
        setBusy(true)
        ports.follows.toggle(GallerySite.PIXIV, artistName)
        setBusy(false)
        setNotice("没有登录票据：这一笔只记进了本地名单，pixiv 那边没有动。")
        return
    }
    val client = ports.pixiv ?: return
    setBusy(true)
    // `restrict=private` 是会员档（非会员发过去站方拒），所以这里恒给 public ——
    // 宁可公开，也不留一条"看起来关注上了其实站方没收"的假记录。
    val outcome = if (target) client.followAdd(bearer, id.toString(), private = false)
    else client.followRemove(bearer, id.toString())
    outcome.onFailure { failure ->
        Log.w(TAG, "画师 $id 的关注没写成：${failure.message}")
        setNotice(failure.message?.takeIf { it.isNotBlank() } ?: "站方没答上，这一笔没记上。")
    }.onSuccess {
        ports.follows.setFollowed(GallerySite.PIXIV, artistName, target)
        alignSite(target)
        setNotice(null)
    }
    setBusy(false)
}

/**
 * 关注 pill（38dp）。
 *
 * 已关注走 tonal（`surfaceContainerHigh` + 发丝描边），未关注走 `primary` 实底 ——
 * 两者必须是**不同的形状语言**，否则用户看不出哪个是这页最该按的钮。
 *
 * 2026-10-19 两处改口径（用户「已经关注的画师右边的按钮也要显示已经关注」「关注画师要和 pixiv 对齐」，
 * 并点名「我这些操作全部都是有登录的」）：
 * - 这一格的真相从**本地名单**换成站方 `is_followed`（见调用处 `following`），本地退成缓存；
 * - 按下去**真写站方**（`/v1/user/follow/add|delete`）。此前这里挂着一条判负：
 *   "本机没有有效票据验不了，所以这一轮不切" —— 那条前提被用户当场否掉，写腿因此接上了。
 * - [busy] 那一档把文案换成"处理中…"并吃掉点击：站方那一跳是网络往返，按下去没反应
 *   会被读成"这钮是画的"，连点两下更会发出两笔相反的请求。
 */
@Composable
private fun FollowPill(following: Boolean, busy: Boolean, onClick: () -> Unit) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(tokens.shape.large)
    Box(
        modifier = Modifier
            .height(tokens.spacing.pixivFollowPillHeight)
            .clip(shape)
            .background(if (following) tokens.color.surfaceContainerHigh else tokens.color.primary)
            .then(if (busy) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = tokens.spacing.space9),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                busy -> "处理中…"
                following -> "已关注"
                else -> "关注"
            },
            fontSize = tokens.type.caption,
            fontWeight = tokens.type.weightSemibold,
            color = if (following) tokens.color.textPrimary else tokens.color.onPrimary,
            maxLines = 1,
        )
    }
}

/**
 * 数据行：粉丝 / 插画 / 漫画 / 公开收藏。
 *
 * **每格都有两个来源，站方优先、匿名兜底**（2026-10-19 改口径）：
 * - 站方那一档 = `/v1/user/detail` 的 `profile.total_*`（要票据）—— 含成人档，与 pixiv 网页同数；
 * - 匿名那一档 = `profile/all` 那两张 id 表的长度 —— **站方按口径过滤过**，
 *   所以它是个偏小的数（读数：同一位画师屏上 3 / 网页 26）。退到这一档时上面那行
 *   匿名档读数会点名说这件事，不留"少给却装作给全"的屏。
 * - 粉丝数**只有站方那一档有**：匿名腿压根不给这一格，所以没票据时它照旧退「—」。
 *
 * ⚠️ 退「—」而不是退 `0`：0 是"这个人没有粉丝"的断言，而我们只是不知道。
 */
@Composable
private fun PixivArtistStats(works: PixivUserWorks?, detail: PixivUserDetail?) {
    val tokens = VeneraTokens
    val cells = listOf(
        (detail?.totalFollowers?.toString() ?: "—") to "粉丝",
        (detail?.totalIllusts?.toString() ?: works?.illusts?.size?.toString() ?: "—") to "插画",
        (detail?.totalManga?.toString() ?: works?.manga?.size?.toString() ?: "—") to "漫画",
        (detail?.totalBookmarksPublic?.toString() ?: works?.publicBookmarkCount?.toString() ?: "—") to "公开收藏",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = tokens.spacing.space6),
    ) {
        cells.forEachIndexed { index, (value, label) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (index == 0) {
                            Modifier
                        } else {
                            Modifier.padding(start = tokens.spacing.space6)
                        },
                    ),
            ) {
                Text(
                    text = value,
                    fontSize = tokens.type.itemTitle,
                    fontWeight = tokens.type.weightSemibold,
                    color = if (value == "—") tokens.color.textTertiary else tokens.color.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = label,
                    fontSize = tokens.type.overline,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 一枚外链胶囊。平台名由 [GalleryArtistLinks] 判（全仓唯一一份平台判定，本屏不抢它的活）。 */
@Composable
private fun PixivLinkChip(url: String, onOpen: () -> Unit) {
    val tokens = VeneraTokens
    val label = GalleryArtistLinks.hostLabel(url)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tokens.color.surfaceContainerHigh)
            .clickable(onClick = onOpen)
            .padding(
                horizontal = tokens.spacing.chipHorizontalPadding,
                vertical = tokens.spacing.chipVerticalPadding,
            ),
    ) {
        Text(
            text = label,
            fontSize = tokens.type.overline,
            color = tokens.color.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 三段切换（轨道 + 选中胶囊），复用首页那一颗 `PixivArtistTabRow` 的**形状**。
 *
 * 数字是**真数**：来自 `profile/all` 的 id 表长度与 `bookmarkCount`，不是"这一批取回了几张"。
 * 取不到（null）就只摆名字、不带数字 —— 那一格我们不摆 0。
 */
@Composable
private fun PixivSectionRow(
    selected: PixivArtistSection,
    counts: Map<PixivArtistSection, Int?>,
    onPick: (PixivArtistSection) -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(tokens.color.surfaceContainerHigh)
            .padding(tokens.spacing.space1),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
    ) {
        PixivArtistSection.entries.forEach { entry ->
            val on = entry == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(tokens.spacing.pixivFollowPillHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (on) tokens.color.surface else tokens.color.surfaceContainerHigh)
                    .clickable { onPick(entry) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.label,
                    fontSize = tokens.type.caption,
                    fontWeight = if (on) tokens.type.weightSemibold else tokens.type.weightRegular,
                    color = if (on) tokens.color.textPrimary else tokens.color.textSecondary,
                    maxLines = 1,
                )
                counts[entry]?.let { count ->
                    Text(
                        text = " $count",
                        fontSize = tokens.type.overline,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 收藏段的未登录那一档：**有出口的空态**，不是失败态。
 *
 * 为什么不能匿名读：`www.pixiv.net/ajax/user/{id}/illusts/bookmarks` 今日实测 **400**
 * （换三个 user_id、三档 limit、两种 lang 全 400）。而 `app-api /v1/user/bookmarks/illust`
 * 要 Bearer —— 站方把"别人的公开收藏"放在了要登录的那一侧。
 *
 * **不摆「重试」**：按下去还是同一句话，那就是假开关（本仓反复修的那一族）。
 * 摆的是"为什么读不到 + 怎么才读得到"。
 */
@Composable
private fun BookmarkLocked() {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.large))
            .background(tokens.color.surfaceContainerHigh)
            .padding(tokens.spacing.space10),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "公开收藏要登录 pixiv 才读得到",
            fontSize = tokens.type.itemTitle,
            fontWeight = tokens.type.weightSemibold,
            color = tokens.color.textPrimary,
        )
        Spacer(Modifier.height(tokens.spacing.space2))
        Text(
            text = "站方这一栏不对匿名请求答话。登录之后，这里就是他公开收藏的那一批。",
            fontSize = tokens.type.caption,
            color = tokens.color.textSecondary,
        )
    }
}

/** 一行读数（空态 / 失败两档共用）。[onClick] 非空才可点。 */
@Composable
private fun PixivArtistLine(text: String, onClick: (() -> Unit)? = null) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space8)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Text(
            text = text,
            fontSize = tokens.type.caption,
            color = if (onClick != null) tokens.color.primary else tokens.color.textSecondary,
        )
    }
}

/**
 * 页尾三档：正在取 / 这一批没取上 / 到底了。
 *
 * **失败只动这一行，不清屏** —— 按一下重试等于把已经看过的几十张删掉，那是另一种伤。
 * 到底才念总张数（`next` 为空 = 真到底，不是"这一批刚好几十张"）。
 */
@Composable
private fun PixivArtistFooter(
    loading: Boolean,
    failed: Boolean,
    exhausted: Boolean,
    total: Int?,
    onLoadMore: () -> Unit,
) {
    val tokens = VeneraTokens
    val text = when {
        loading -> "正在取下一批…"
        failed -> "这一批没取上 · 重试"
        exhausted && total != null -> "已经到底了 · 共 $total 张"
        exhausted -> "已经到底了"
        else -> null
    }
    if (text == null) {
        // 还有下一批且没在取 ⇒ 滚到页脚就续（"进组合"本身就等于"用户滚到了底"）。
        LaunchedEffect(total) { onLoadMore() }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = tokens.spacing.listRowMinHeight)
            .then(if (failed) Modifier.clickable { onLoadMore() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = tokens.type.overline,
            color = if (failed) tokens.color.primary else tokens.color.textTertiary,
        )
    }
}

private fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { Log.w(TAG, "外链打不开：$url（${it.message}）") }
}

/** 一批多少个 id。站方 `profile/illusts` 实测 24 枚一批通过（更多没试，不冒进）。 */
private const val BATCH_SIZE = 24

/**
 * 屏上那一段 → 站方 `/v1/user/illusts` 的 `type` 桶名。
 *
 * ⚠️ 传错桶**不报错**，只会回另一类作品（匿名那条 `profile/illusts` 实测就是这个行为），
 * 所以这一段与那一枚参数的绑定只许有这一处。
 *
 * 收藏段压根不走这条路（它有自己的书签腿 `next_url`），落到这里就是接线错了 ——
 * 当场抛，不许悄悄退成 `illust` 把收藏念成投稿。
 */
private fun PixivArtistSection.ticketType(): String = when (this) {
    PixivArtistSection.ILLUST -> "illust"
    PixivArtistSection.MANGA -> "manga"
    PixivArtistSection.BOOKMARK -> error("收藏段没有票据版的作品桶，走到这里说明调用点接错了")
}

/** 外链最多摆几枚（手机档一行放得下、又不至于把简介挤走）。 */
private const val MAX_LINKS = 3

private const val TAG = "PixivArtist"
