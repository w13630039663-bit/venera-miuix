package com.venera.compose.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.venera.compose.components.VeneraEmptyTone
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.rememberImageWallColumnCount
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivAppPage
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.gallery.data.PixivWork
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.compose.ui.tokens.LocalBottomBarClearance
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 「Pixiv 书签」二级页：**云端**收藏（2026-10-17 第三十一轮，用户稿那枚 🔖 卡点进来的地方）。
 *
 * ## 它读的是哪一份数据
 *
 * `/v1/user/bookmarks/illust`（app-api，带票据 + 自己的用户号，只读 `restrict=public`）。
 * 与本地收藏（`GalleryFavoritesStore`）是**两套账**：这边只能读，删除要走站方；
 * 屏上两个入口分开摆（见 [GalleryBookmarksHubScreen] 卡下那行说明），不合并、不去重。
 *
 * ## 为什么复用 `GalleryCardsGrid` 而不是新写一张 pixiv 卡
 *
 * 猜你喜欢那一面 pixiv 推荐墙（`GalleryScreen` 的 `pixivRecommendWall`）走的就是这条路：
 * `PixivWork.post` 摊平进同一颗 `buildGalleryWall`（打码 / 屏蔽规则 / AI 过滤同一把尺），
 * 标题与头像按 `uid` 摊成两张表递进去（`stationTitles` / `authorAvatars`）。
 * 两处都是瀑布流，卡却长两个样 —— 那正是用户这次点名"做成相同风格的ui"要消掉的东西。
 *
 * ## 翻页
 *
 * 只认站方的 `next_url`（它自带 `max_bookmark_id` 游标），与推荐墙同一套三道闸门
 * （[GalleryLoadMoreState]：墙得是活的 / 到底了不判 / 在取时不并发）。追加按 uid 去重 ——
 * 书签列表跨页重复时不去重就是同一张图连着出现两遍。
 *
 * @param onOpenPost 打开大图页（跨 Activity，由调用方递进来）。
 * @param onBack 返回（回收藏 hub）。
 */
@Composable
internal fun GalleryPixivBookmarksScreen(
    onOpenPost: (GallerySite, Long) -> Unit,
    onBack: () -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current

    val pixivAccount = remember(context) { GalleryPorts.of(context).pixivAccount }
    val pixivWorks = remember(context) { GalleryPorts.of(context).pixiv }
    val pixivAuth = pixivAccount?.authState?.collectAsState()?.value ?: PixivAuthState.Anonymous
    val guard = remember(context) { GalleryPorts.of(context).contentGuard }
    val maskMode by guard.nsfwMaskMode.collectAsState()
    val rules by guard.rules.collectAsState()
    val blockAi by GalleryPorts.of(context).prefs.galleryBlockAi.collectAsState()
    val imageLoader = remember { GalleryImageLoader.get(context) }

    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val gridState = rememberLazyStaggeredGridState()
    val columnMode by GalleryPorts.of(context).prefs.galleryColumnMode.collectAsState()
    // 与画廊墙同一把自适应列数（每列预算 200dp，手机档恒 2 列），理由见 GalleryScreen 那一处。
    val columnCount = GallerySettingsModel.gridColumns(columnMode, rememberImageWallColumnCount())

    // ── 这一页自己的一份状态（刻意不进 `GalleryPixivHomeStore`：书签不是首页那一屏的内容，
    //    切 Tab 回来重取一次是可接受的代价，而"进过一次就永远显示旧书签"才是错）──
    var bookmarks by remember { mutableStateOf<List<PixivWork>>(emptyList()) }
    var bookmarksNext by remember { mutableStateOf<String?>(null) }
    var bookmarksExhausted by remember { mutableStateOf(false) }
    var bookmarksLoadedKey by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var moreError by remember { mutableStateOf<String?>(null) }
    /** 用户按「重试」抬一次：让首屏那一跳重新判（否则失败会被凭证钉成空态）。 */
    var reloadTick by remember { mutableStateOf(0) }

    val me = (pixivAuth as? PixivAuthState.SignedIn)?.userId
    val signedIn = pixivAuth is PixivAuthState.SignedIn

    // ── 第一页 ──────────────────────────────────────────────────────────────
    //
    // 凭证判据与关注画师那一跳同一条：`bookmarksLoadedKey` 记"这个账号取过了"，
    // 切走再进来不重发；登录态一变由下面的 effect 作废（见 `authSeen`）。
    LaunchedEffect(pixivAuth, pixivWorks, reloadTick) {
        val works = pixivWorks ?: return@LaunchedEffect
        if (me.isNullOrBlank()) {
            bookmarks = emptyList()
            bookmarksNext = null
            bookmarksExhausted = false
            bookmarksLoadedKey = null
            loading = false
            // 这一档**不设 pageError**：未登录 / 没存下用户号都不是"这一跳失败了"，
            // 摆一枚「重试」按下去还是同样一句话 —— 那是假开关。文案由下面空态那两档念。
            pageError = null
            return@LaunchedEffect
        }
        val requestKey = "bookmarks:$me"
        if (bookmarksLoadedKey == requestKey) return@LaunchedEffect
        val bearer = pixivAccount?.bearerOrNull()
        if (bearer == null) {
            // 票据取不到是**这一刻**的事，不记账：下一次组合还能再试，别把失败钉成空态。
            pageError = "这枚登录票据这一会儿取不到（可能刚过期）—— 点顶栏那颗头像重新登录一次。"
            loading = false
            return@LaunchedEffect
        }
        loading = true
        pageError = null
        moreError = null
        val result = works.bookmarkedIllusts(bearer, me)
        val page: PixivAppPage? = result.getOrNull()
        if (page == null) {
            pageError = result.exceptionOrNull()?.message
            bookmarks = emptyList()
        } else {
            bookmarks = page.works
            bookmarksNext = page.nextUrl
            bookmarksExhausted = page.nextUrl == null
        }
        loading = false
        bookmarksLoadedKey = requestKey
    }

    // 登录态一变，这一页的凭证整批作废：换一个账号读的就是另一个人的书签。
    // 首帧不许清（同上一条 effect 开头那个凭证的口径：刚恢复的存档不能被自己抹掉）。
    var authSeen by remember { mutableStateOf(pixivAuth) }
    LaunchedEffect(pixivAuth) {
        if (pixivAuth == authSeen) return@LaunchedEffect
        authSeen = pixivAuth
        bookmarksLoadedKey = null
        bookmarksNext = null
        bookmarksExhausted = false
    }

    // ── 无限下拉（与推荐墙同一套三道闸门）────────────────────────────────────
    LaunchedEffect(gridState, pixivAuth, pixivWorks) {
        val works = pixivWorks ?: return@LaunchedEffect
        // 没登录 / 没存用户号：这一排根本没有墙，闸门无从判起（早退，别空转）。
        if (me.isNullOrBlank()) return@LaunchedEffect
        snapshotFlow {
            val info = gridState.layoutInfo
            GalleryLoadMoreState(
                wallActive = bookmarks.isNotEmpty(),
                exhausted = bookmarksExhausted,
                busy = loading || loadingMore,
                failed = false,
                settled = true,
                lastIndex = info.totalItemsCount - 1,
                visibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1,
            )
        }
            .distinctUntilChanged()
            .collect { state ->
                if (!state.wallActive || state.exhausted || state.busy) return@collect
                if (state.lastIndex < 0 || state.visibleIndex < state.lastIndex - LOAD_MORE_AHEAD) return@collect
                val next = bookmarksNext
                val bearer = pixivAccount?.bearerOrNull()
                if (next == null || bearer == null) {
                    // 没有游标 / 票据这一刻取不到：这一轮到此为止，别把页脚永远转着。
                    bookmarksExhausted = true
                    return@collect
                }
                loadingMore = true
                moreError = null
                val result = works.bookmarkedIllusts(bearer, me, next)
                val page = result.getOrNull()
                if (page == null) {
                    // 续页失败**不清屏**（按一下等于删了一屏内容），也不置 exhausted ——
                    // 再划一下闸门会重新判，用户划回去再划回来就是重试。
                    moreError = result.exceptionOrNull()?.message
                } else {
                    val seen = bookmarks.mapTo(HashSet()) { it.post.uid }
                    bookmarks = bookmarks + page.works.filter { it.post.uid !in seen }
                    bookmarksNext = page.nextUrl
                    if (page.nextUrl == null) bookmarksExhausted = true
                }
                loadingMore = false
            }
    }

    // 与首页那面 pixiv 推荐墙**同一把过滤判据**（`buildGalleryWall`）：同一张图在首页与这一页
    // 待遇必须一致（打码 / 屏蔽规则 / AI 过滤各是各的，分叉了最难解释）。
    val wall = remember(bookmarks, maskMode, rules, blockAi) {
        buildGalleryWall(bookmarks.map { it.post }, maskMode, blockAi = blockAi) { post ->
            guard.blockedGalleryRule(author = post.author, tags = post.tagList)?.pattern
        }
    }
    // 站方标题与作者头像按 uid 摊平（同推荐墙那两张表）：整批算一次，而不是让每张卡回头找。
    val titles = remember(bookmarks) { bookmarks.associate { it.post.uid to it.title } }
    val avatars = remember(bookmarks) { bookmarks.associate { it.post.uid to it.authorAvatarUrl } }

    val topPadding = statusBarTop + VeneraSpacing.topBarFloor
    val contentPadding = PaddingValues(
        start = tokens.spacing.screenHorizontal,
        end = tokens.spacing.screenHorizontal,
        top = topPadding,
        bottom = LocalBottomBarClearance.current,
    )
    val wallModifier = Modifier
        .fillMaxSize()
        .blurBackdropSource(topBarBackdrop)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topBarBehavior.nestedScrollConnection),
        ) {
            when {
                // 整页级加载（第一页在途）。
                loading && bookmarks.isEmpty() -> Box(wallModifier, contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(tokens.spacing.loaderPage),
                        color = tokens.color.primary,
                        trackColor = tokens.color.surfaceVariant,
                    )
                }

                // 第一页就没取上。
                pageError != null && bookmarks.isEmpty() -> Box(
                    wallModifier.padding(horizontal = tokens.spacing.screenHorizontal),
                    contentAlignment = Alignment.Center,
                ) {
                    VeneraEmptyView(
                        title = "书签没取上",
                        message = pageError.orEmpty(),
                        tone = VeneraEmptyTone.Failed,
                        actionText = "重试",
                        onAction = {
                            bookmarksLoadedKey = null
                            reloadTick++
                        },
                    )
                }

                // 没登录：说清去哪儿登录，**不给按钮**（这一页没有直通登录的路，
                // 摆一枚"去登录"却跳不出去就是假把手；回首页点顶栏那颗头像是真路径）。
                !signedIn -> Box(
                    wallModifier.padding(horizontal = tokens.spacing.screenHorizontal),
                    contentAlignment = Alignment.Center,
                ) {
                    VeneraEmptyView(
                        title = "还没登录 pixiv",
                        message = "云端书签在 pixiv 账号里。回到画廊首页点顶栏那颗头像登录，" +
                            "这里就能读到账号里的书签。",
                        tone = VeneraEmptyTone.NotYet,
                    )
                }

                // 登录着却**没存下用户号**（老版本写下的登录记录）：这一跳的 `user_id` 没有值可传。
                // 与上一档分开念 —— 顶栏说"已登录"、这一页不能也说"你还没登录"，两句话会打架。
                // 同样不给按钮：出路是重新登录（在首页），在这一页重试一万次也没有用户号。
                me.isNullOrBlank() -> Box(
                    wallModifier.padding(horizontal = tokens.spacing.screenHorizontal),
                    contentAlignment = Alignment.Center,
                ) {
                    VeneraEmptyView(
                        title = "这个登录没记下用户号",
                        message = "这一版没记下你的 pixiv 用户号 —— 回到画廊首页点顶栏那颗头像" +
                            "重新登录一次，这里就能读出来。",
                        tone = VeneraEmptyTone.NotYet,
                    )
                }

                // 登录了、也取回来了，就是一件都没有。
                bookmarks.isEmpty() -> Box(
                    wallModifier.padding(horizontal = tokens.spacing.screenHorizontal),
                    contentAlignment = Alignment.Center,
                ) {
                    VeneraEmptyView(
                        title = "这个账号还没有书签",
                        message = "pixiv 账号里一件公开书签都没有 —— 这一页只读公开书签，" +
                            "私密那部分要去站方 App 里看。",
                        tone = VeneraEmptyTone.Nothing,
                    )
                }

                else -> GalleryCardsGrid(
                    cards = wall.cards,
                    imageLoader = imageLoader,
                    columnCount = columnCount,
                    state = gridState,
                    contentPadding = contentPadding,
                    modifier = wallModifier,
                    onOpen = { post ->
                        // 与每日热门二级页同一把队列：大图页左右划跟着这一页的顺序走。
                        GalleryViewerQueue.set(wall.cards.map { it.post })
                        onOpenPost(post.site, post.id)
                    },
                    stationTitles = titles,
                    authorAvatars = avatars,
                    footer = {
                        // 页脚三档：在取 / 续页失败 / 到底。槽位恒在（空书签那一档走上面的空态，
                        // 到不了这里）—— 懒列表尾项跟着有无内容插拔会让视口锚点漂。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = tokens.spacing.space6),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                loadingMore -> CircularWavyProgressIndicator(
                                    modifier = Modifier.size(tokens.spacing.loaderInline),
                                    color = tokens.color.primary,
                                    trackColor = tokens.color.surfaceVariant,
                                )

                                moreError != null -> Text(
                                    text = "下一页这一回没取上：${moreError}—— 再划一下重试。",
                                    fontSize = tokens.type.caption,
                                    lineHeight = TEXT_LINE_HEIGHT.em,
                                    color = tokens.color.textTertiary,
                                    textAlign = TextAlign.Center,
                                )

                                bookmarksExhausted -> Text(
                                    text = "书签都摆完了",
                                    fontSize = tokens.type.caption,
                                    color = tokens.color.textTertiary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    },
                )
            }
        }

        VeneraTopAppBar(
            title = "Pixiv 书签",
            largeTitle = "Pixiv 书签",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                VeneraTopBarPill(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
