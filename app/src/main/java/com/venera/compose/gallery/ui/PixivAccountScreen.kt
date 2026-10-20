package com.venera.compose.gallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.venera.compose.components.VeneraEmptyTone
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.rememberImageWallColumnCount
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraDialog
import com.venera.compose.components.venera.VeneraTextField
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.components.venera.veneraAvatarRing
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPixivWorks
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivAccountPreferences
import com.venera.compose.gallery.data.PixivAppPage
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.gallery.data.PixivCredentials
import com.venera.compose.gallery.data.PixivRestrict
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.gallery.data.PixivUserDetail
import com.venera.compose.gallery.data.PixivUserHead
import com.venera.compose.gallery.data.PixivUserPage
import com.venera.compose.gallery.data.PixivWork
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.compose.gallery.domain.PixivAccountGate
import com.venera.compose.gallery.domain.PixivLegCursor
import com.venera.compose.gallery.domain.PixivReadScope
import com.venera.compose.gallery.domain.mergePixivLegs
import com.venera.compose.gallery.domain.pixivAccountGate
import com.venera.compose.gallery.domain.pixivFeedExhausted
import com.venera.compose.ui.tokens.ImageOverlayColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text

/**
 * pixiv **个人账号页**（方案 `docs/rounds/gallery-pixiv-account-page-2026-10-19.html`）。
 *
 * ## 这一屏为什么存在
 *
 * 顶栏那颗头像以前点开只是"这是谁 + 退出"。用户 2026-10-19 要的是把账号里那三样东西
 * 真读出来摆在应用里：**关注的人的新作**、**我的收藏（含非公开）**、**我关注的人（含私密关注）**。
 * 这三样都存在 pixiv 账号维度，匿名那条腿一件都给不了（读数记在
 * `memory/project-pixiv-anonymous-endpoint-facts.md`）。
 *
 * ## 结构（与稿逐行对）
 *
 * 常驻 chrome 三行 + 一条滚动的内容列：主行（返回 + 名字 + 「看哪一份」那枚）/
 * 一级三段（动态 · 收藏 · 已关注）/ 二级三档（只在动态段：我发布的 · 关注动态 · 账号管理）。
 * 二级那一行**也挂 chrome**、不随内容滚走 —— 口径同「收藏页二级分段器必须挂顶栏 chrome」那条拍板。
 *
 * ## 「全部」这一档在这里是怎么落的
 *
 * 站方的 `restrict` 只有 `public` / `private` 两值，**没有**"都要"。所以这一档是**同一处发两趟再合**
 * （判据在 `PixivReadScope.legs`，合并与"到底"在 `PixivAccountFeed`）。把它实现成
 * "传 public 然后声称含私密"就是本仓反复立案的**假开关** —— 屏上摆着「全部」而只读了公开。
 *
 * ## 四档闸门
 *
 * 发任何一趟之前先过 [pixivAccountGate]：没登录 / 登录着却没记下用户号 / 这一刻取不到票据 / 齐了。
 * 四档各念一句不一样的话，而**只有票据那一档摆「重试」**（其余三档的出路在别处，
 * 在这一屏按一万次还是同一句话 —— 那是假把手）。
 *
 * @param onBack 返回（宿主 Activity `finish()`）。
 * @param onOpenPost 打开大图页（跨 Activity；飞行体与转场归 hero 那一轮，本屏只递数据）。
 */
@Composable
internal fun PixivAccountScreen(
    startSection: PixivAccountSection,
    onBack: () -> Unit,
    onOpenPost: (GallerySite, Long) -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val ports = remember(context) { GalleryPorts.of(context) }
    val works = ports.pixiv
    val account = ports.pixivAccount

    val pixivAuth = account?.authState?.collectAsState()?.value ?: PixivAuthState.Anonymous
    val signed = pixivAuth as? PixivAuthState.SignedIn
    val me = signed?.userId

    // 票据**这一刻**有没有：每次登录态一变、或用户按了「再试一次」就现读一次。
    // 读一次就钉住是假读数 —— 自动续期发生在屏外。
    var bearerReady by remember { mutableStateOf(true) }
    var bearerTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(pixivAuth, account, bearerTick) {
        bearerReady = account?.bearerOrNull() != null
    }
    val gate = pixivAccountGate(signedIn = signed != null, userId = me, bearerReady = bearerReady)

    // 三段的位置归 Pager 管（左滑进、右滑回），`section` 只是"当前停在哪一页"的派生读数。
    // `rememberPagerState` 自带 saveable：旋转后仍停原来那一页。
    val pagerState = rememberPagerState(initialPage = startSection.ordinal) {
        PixivAccountSection.entries.size
    }
    val section = PixivAccountSection.entries[pagerState.currentPage]
    val uiScope = rememberCoroutineScope()

    // 二级那一行是"同一页里的三档"，不走手势：外层 Pager 已经在横滑，里层再套一层会抢手势。
    // 转场用方向化的 AnimatedContent —— 新页从右侧滑入、旧页向左滑出；倒退时反过来。
    var feedTabName by rememberSaveable { mutableStateOf(PixivAccountFeedTab.FOLLOW.name) }
    val feedTab = PixivAccountFeedTab.entries.firstOrNull { it.name == feedTabName }
        ?: PixivAccountFeedTab.FOLLOW

    // 「进来先看哪一批」那两档（方案 §2 的 ⑭）。槽位可空 = 这一侧没有这一档（桌面不摆账号页），
    // 那时退成"每次进来都要我挑一次"的旧行为，而不是替它编一个默认值。
    val accountPrefs = ports.pixivAccountPrefs
    val storedScope = accountPrefs?.restrictDefault?.collectAsState()?.value ?: PixivReadScope.All
    val showPrivate = accountPrefs?.showPrivate?.collectAsState()?.value ?: true
    var pickedScope by remember { mutableStateOf(storedScope) }
    // 总闸是**闸**不是**滤镜**：关掉之后私密那一趟根本不发，屏上也不留任何"含非公开"的读数。
    // 派生而不是写回 `pickedScope`：用户的原选择要在他把闸开回去时还在。
    val scope = if (showPrivate) pickedScope else PixivReadScope.Public
    var restrictPickerOpen by remember { mutableStateOf(false) }

    // 哪几屏要带 restrict：一枚**穷尽 when**，加一档段/子档就编译不过。
    val restrictApplies = section.restrictApplies(feedTab)

    // ── 身份读数：一次取，三处用（顶栏那行的计数 / 我发布的头部 / 账号管理身份卡）──
    //
    // 从前这三处各自发一趟同一颗端点，于是同一屏对 `/v1/user/detail` 要两次，
    // 而两趟之间站方改了数就会读到两个答案。提到屏级之后只剩一份真相；
    // 2026-10-19 起这份真相活进会话仓 —— 旋转、切屏、离开再回来都不重取。
    val identity = remember(me) { me?.let { PixivAccountSessionStore.identity(it) } }
    val head = identity?.head
    val detail = identity?.detail
    // 下拉刷新与票据重试各推一下它；0 且取过 = 什么都不做（"只在下拉时刷新"）。
    var identityTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(me, works, account, bearerTick, identityTick) {
        val session = identity ?: return@LaunchedEffect
        val userId = me ?: return@LaunchedEffect
        if (identityTick == 0 && bearerTick == 0 && session.asked) return@LaunchedEffect
        val id = userId.toLongOrNull() ?: return@LaunchedEffect
        if (works == null) return@LaunchedEffect
        works.userHead(id).getOrNull()?.let { session.head = it }
        val bearer = account?.bearerOrNull()
        if (!bearer.isNullOrBlank()) {
            works.userDetail(bearer, userId).getOrNull()?.let { session.detail = it }
        }
        session.asked = true
    }
    // 段名后面那个数是**那一档的总数**，不是"这一屏摆出来几张"。
    //
    // ⚠️ 只在「公开」那一档摆：站方给的 `total_illust_bookmarks_public` 与匿名主页那格
    // 「关注数」数的都是公开那份，切到「全部」还继续摆它，就是把公开数说成合计数
    // —— 取不到合计数就**不带数**（口径同"退『—』不拿 0 顶"）。
    val sectionCounts: Map<PixivAccountSection, Int?> = mapOf(
        PixivAccountSection.DYNAMIC to null,
        PixivAccountSection.BOOKMARKS to detail?.totalBookmarksPublic.takeIf { scope == PixivReadScope.Public },
        PixivAccountSection.FOLLOWING to head?.followingCount.takeIf { scope == PixivReadScope.Public },
    )
    val mineCount = detail?.totalIllusts

    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    // 三页共用一条顶栏：翻到新那一页时把折叠进度归零，否则上一页滚下去的状态会原样带过来
    // （新页明明停在顶部，大标题却还是收着的）。用 settledPage 而不是 currentPage —— 后者在
    // 滑动过半那一刻就变了，归零会把大标题在滑到一半时突然弹出来。
    LaunchedEffect(pagerState.settledPage) { topBarBehavior.state.contentOffset = 0f }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // chrome 附加高各页自留（不进 `topBarFloor` 那枚地板常量 —— 那把尺子只钉"地板"）。
    // 这里刻意不写 `0.dp`：全仓裸 dp 字面量有配额（`TokenBudgetFrozenTest`），少造一处是一处。
    // `tokens.spacing` 本身是 @Composable get：这颗本地函数必须跟着标，否则读不到它。
    @Composable
    fun chromeExtraOf(target: PixivAccountSection): Dp = if (target == PixivAccountSection.DYNAMIC) {
        tokens.spacing.pixivAccountPrimaryRow + tokens.spacing.space4 + tokens.spacing.pixivAccountSecondaryRow + tokens.spacing.space4
    } else {
        tokens.spacing.pixivAccountPrimaryRow + tokens.spacing.space4
    }
    val contentTop = statusBarTop + VeneraSpacing.topBarFloor + chromeExtraOf(section)
    // 不适用 restrict 的那一屏退回「公开」：那一趟压根没有这一枚参数，但墙本身照发。
    fun wallScopeFor(target: PixivAccountSection, tab: PixivAccountFeedTab): PixivReadScope =
        if (target.restrictApplies(tab)) scope else PixivReadScope.Public

    val columnMode by ports.prefs.galleryColumnMode.collectAsState()
    val columns = GallerySettingsModel.gridColumns(columnMode, rememberImageWallColumnCount())
    val imageLoader = remember { GalleryImageLoader.get(context) }

    // 三面墙的"哪条腿"全在这一颗穷尽 when 里：少一支就编译不过，
    // 于是"新加一档子屏忘了接腿"不会变成一个静默的空屏。
    fun wallFetchFor(
        target: PixivAccountSection,
        tab: PixivAccountFeedTab,
    ): (suspend (PixivRestrict, String?) -> Result<PixivAppPage>)? {
        val currentWorks = works ?: return null
        val userId = me ?: return null
        return when (target) {
            PixivAccountSection.BOOKMARKS -> { restrict, next ->
                currentWorks.bookmarkedIllusts(bearerOf(account), userId, restrict, next)
            }

            PixivAccountSection.FOLLOWING -> null
            PixivAccountSection.DYNAMIC -> when (tab) {
                PixivAccountFeedTab.MINE -> { _, next ->
                    currentWorks.userIllusts(bearerOf(account), userId, PixivWorkCategory.illust, next)
                }

                PixivAccountFeedTab.FOLLOW -> { restrict, next ->
                    currentWorks.followIllustFeed(bearerOf(account), restrict, next)
                }

                PixivAccountFeedTab.ACCOUNT -> null
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (gate != PixivAccountGate.Ready) {
            PixivAccountGateState(
                gate = gate,
                topPadding = contentTop,
                onRetry = { bearerTick++ },
            )
        } else {
            // 三段横向翻页（2026-10-19 用户点名 ③）：手指左右划 = 换段，点一级药丸 = 滚过去。
            // ⚠️ 每一页自己算让位：动态段多一行二级，chrome 的实高随段变，所以不能共用一份 top。
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { PixivAccountSection.entries[it].name },
            ) { page ->
                val target = PixivAccountSection.entries[page]
                val pageTop = statusBarTop + VeneraSpacing.topBarFloor + chromeExtraOf(target)
                val pageScope = wallScopeFor(target, feedTab)
                when (target) {
                    PixivAccountSection.DYNAMIC -> {
                        // 二级三档不走横滑（外层 Pager 已经在横滑，里层再吃手势会抢），
                        // 但换档仍要有方向：新页从右边进来、旧页往左退；倒着切反过来。
                        AnimatedContent(
                            targetState = feedTab,
                            transitionSpec = {
                                val forward = targetState.ordinal > initialState.ordinal
                                (
                                    slideInHorizontally(initialOffsetX = { if (forward) it else -it }) + fadeIn()
                                    ) togetherWith
                                    (
                                        slideOutHorizontally(targetOffsetX = { if (forward) -it else it }) + fadeOut()
                                        ) using SizeTransform(clip = false)
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "pixivAccountFeedTab",
                        ) { tab ->
                            when (tab) {
                                PixivAccountFeedTab.ACCOUNT -> PixivAccountManageSection(
                                    topPadding = pageTop,
                                    scrollBehavior = topBarBehavior,
                                    account = account,
                                    userId = me,
                                    authLine = pixivAuth.screenLine,
                                    signedName = signed?.userName,
                                    head = head,
                                    scope = scope,
                                    showPrivate = showPrivate,
                                    accountPrefs = accountPrefs,
                                    onPickScope = { restrictPickerOpen = true },
                                    onToggleShowPrivate = {
                                        val next = !showPrivate
                                        accountPrefs?.setShowPrivate(next)
                                        if (!next) accountPrefs?.setRestrictDefault(PixivReadScope.Public)
                                    },
                                    onSignedOut = {
                                        // 退出之后旧账号那几面墙一条都不该留：换一个号进来时同上。
                                        PixivAccountSessionStore.clear()
                                        onBack()
                                    },
                                )

                                else -> {
                                    val wallScope = wallScopeFor(PixivAccountSection.DYNAMIC, tab)
                                    val fetch = wallFetchFor(PixivAccountSection.DYNAMIC, tab)
                                    if (fetch == null) {
                                        PixivAccountWallUnavailable(topPadding = pageTop)
                                    } else {
                                        PixivTwoLegWall(
                                            legs = wallScope.legs,
                                            sessionKey = "DYNAMIC:${tab.name}:$me:${wallScope.name}",
                                            fetch = fetch,
                                            imageLoader = imageLoader,
                                            columns = columns,
                                            topPadding = pageTop,
                                            scrollBehavior = topBarBehavior,
                                            showsPrivate = wallScope.showsPrivate,
                                            onOpenPost = onOpenPost,
                                            onRefreshIdentity = { identityTick++ },
                                            header = pixivWallHeader(
                                                target = PixivAccountSection.DYNAMIC,
                                                tab = tab,
                                                scope = wallScope,
                                                head = head,
                                                detail = detail,
                                                account = account,
                                                works = works,
                                                userId = me,
                                                fallbackName = signed?.userName.orEmpty(),
                                                imageLoader = imageLoader,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    PixivAccountSection.BOOKMARKS -> {
                        val fetch = wallFetchFor(target, feedTab)
                        if (fetch == null) {
                            PixivAccountWallUnavailable(topPadding = pageTop)
                        } else {
                            PixivTwoLegWall(
                                legs = pageScope.legs,
                                sessionKey = "BOOKMARKS:$me:${pageScope.name}",
                                fetch = fetch,
                                imageLoader = imageLoader,
                                columns = columns,
                                topPadding = pageTop,
                                scrollBehavior = topBarBehavior,
                                showsPrivate = pageScope.showsPrivate,
                                onOpenPost = onOpenPost,
                                onRefreshIdentity = { identityTick++ },
                                header = pixivWallHeader(
                                    target = target,
                                    tab = feedTab,
                                    scope = pageScope,
                                    head = head,
                                    detail = detail,
                                    account = account,
                                    works = works,
                                    userId = me,
                                    fallbackName = signed?.userName.orEmpty(),
                                    imageLoader = imageLoader,
                                ),
                            )
                        }
                    }

                    PixivAccountSection.FOLLOWING -> {
                        val fetchUsers: (suspend (PixivRestrict, String?) -> Result<PixivUserPage>)? =
                            works?.let { current ->
                                { restrict, next ->
                                    current.following(bearerOf(account), me.orEmpty(), restrict, next)
                                }
                            }
                        val fetchCovers: (suspend (String) -> Result<PixivAppPage>)? =
                            works?.let { current ->
                                { userId ->
                                    current.userIllusts(bearerOf(account), userId, PixivWorkCategory.illust, null)
                                }
                            }
                        if (fetchUsers == null || fetchCovers == null) {
                            PixivAccountWallUnavailable(topPadding = pageTop)
                        } else {
                            PixivFollowingSection(
                                legs = pageScope.legs,
                                sessionKey = "FOLLOWING:$me:${pageScope.name}",
                                topPadding = pageTop,
                                scrollBehavior = topBarBehavior,
                                fetchUsers = fetchUsers,
                                fetchCovers = fetchCovers,
                                onRefreshIdentity = { identityTick++ },
                            )
                        }
                    }
                }
            }
        }

        VeneraTopAppBar(
            // 主行只报"这是哪一屏"。**名字不在这里** —— 头部（④）与身份卡（⑤）各摆一份大的，
            // 顶栏再摆一份就是同一屏三处"极东的Djehuty"（2026-10-19 真机读数：三屏都这样）。
            title = "我的 pixiv",
            largeTitle = "我的 pixiv",
            scrollBehavior = topBarBehavior,
            // 2026-10-19 用户点名 ④「顶栏…不要透过」：不传 backdrop（玻璃采的是内容层，传了就必透），
            // 实底由 opaqueChrome 把同一份氛围光铺满整条顶栏 —— 与全站顶栏的折叠行为一致，只是不透。
            opaqueChrome = true,
            navigationIcon = {
                VeneraTopBarPill(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            actions = {
                if (restrictApplies) {
                    // 稿上那一枚是「锁 + 档位 + 尖角」的下拉把手，不是一句"看：全部"的裸文字：
                    // 尖角说"点开还有别的档"，锁说"这一档管的是看不看得到非公开"。
                    VeneraChip(
                        text = scope.screenLabel,
                        leadingIcon = Icons.Outlined.Lock,
                        trailingIcon = Icons.Outlined.ExpandMore,
                        onClick = { restrictPickerOpen = true },
                    )
                }
            },
            bottomContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 稿 §1 的 `.seg` / `.sub2` 都留 12dp 屏边、二级行再留 8dp 上距与 8dp 收尾；
                        // 少了边距就是两行药丸贴着屏边跑（2026-10-19 真机读数的"两段 pill 重叠"之一）。
                        .padding(
                            start = tokens.spacing.screenHorizontal,
                            end = tokens.spacing.screenHorizontal,
                            bottom = tokens.spacing.space4,
                        ),
                ) {
                    PixivAccountSegmentTrack(
                        selected = section,
                        counts = sectionCounts,
                        onPick = { target ->
                            uiScope.launch { pagerState.animateScrollToPage(target.ordinal) }
                        },
                    )
                    if (section == PixivAccountSection.DYNAMIC) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = tokens.spacing.space4)
                                .height(tokens.spacing.pixivAccountSecondaryRow),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                        ) {
                            PixivAccountFeedTab.entries.forEach { picked ->
                                PixivAccountSubPill(
                                    text = picked.screenLabel,
                                    count = picked.takeIf { it == PixivAccountFeedTab.MINE }?.let { mineCount },
                                    selected = feedTab == picked,
                                    onClick = { feedTabName = picked.name },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    // 「看哪一份」开的是**三选一**，不是一枚循环：循环的话用户看不出还有第三档。
    //
    // 总闸关掉时这里**只剩一档**（不是把另两档灰着）：那两档要发的趟已经被闸掐了，
    // 摆出来按下去只会得到"没反应"，那是假把手。
    VeneraDialog(
        show = restrictPickerOpen,
        onDismissRequest = { restrictPickerOpen = false },
        title = "看哪一份",
        dismissText = "关闭",
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                pixivScopeChoices(showPrivate).forEach { picked ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pickedScope = picked
                                accountPrefs?.setRestrictDefault(picked)
                                restrictPickerOpen = false
                            }
                            .padding(vertical = tokens.spacing.space3),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                    ) {
                        Text(
                            text = picked.screenLabel,
                            fontSize = tokens.type.itemTitle,
                            color = if (picked == scope) tokens.color.primary else tokens.color.textPrimary,
                        )
                        Text(
                            text = picked.explain,
                            fontSize = tokens.type.caption,
                            color = tokens.color.textTertiary,
                        )
                    }
                }
            }
        },
    )
}

/** 下拉里摆哪几档：闸开着三档都在，关掉只剩公开那一档。 */
private fun pixivScopeChoices(showPrivate: Boolean): List<PixivReadScope> =
    if (showPrivate) PixivReadScope.entries.toList() else listOf(PixivReadScope.Public)

/**
 * 一级三段那一行：**一整条轨道 + 选中那一格白胶囊**（稿 §1 的 ②）。
 *
 * 形状逐条对齐画师页的 `PixivSectionRow`（轨道 `surfaceContainerHigh`、选中 `surface`、
 * 数字走 `overline`/`textTertiary`、**取不到数就不带数**）。为什么不 import 那一颗：它是
 * `private` 且写死了 `PixivArtistSection.entries`，而画师页此刻归并行改 hero 的那一轮 ——
 * 折成一颗共用件是那一轮收口后的挂账（记在手册里），不在这里偷偷复制第二种形状。
 *
 * 从前这里摆的是三颗各自描边的 `VeneraFilterPill`，真机上与下面那行二级**长得一模一样重**，
 * 一级二级分不开（2026-10-19 那轮读数）。
 */
@Composable
private fun PixivAccountSegmentTrack(
    selected: PixivAccountSection,
    counts: Map<PixivAccountSection, Int?>,
    onPick: (PixivAccountSection) -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.spacing.pixivAccountPrimaryRow)
            .clip(RoundedCornerShape(percent = 50))
            .background(tokens.color.surfaceContainerHigh)
            .padding(tokens.spacing.space1),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
    ) {
        PixivAccountSection.entries.forEach { picked ->
            val on = picked == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (on) tokens.color.surface else tokens.color.surfaceContainerHigh)
                    .clickable { onPick(picked) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = picked.screenLabel,
                    fontSize = tokens.type.caption,
                    fontWeight = if (on) tokens.type.weightSemibold else tokens.type.weightRegular,
                    color = if (on) tokens.color.textPrimary else tokens.color.textSecondary,
                    maxLines = 1,
                )
                counts[picked]?.let { total ->
                    Text(
                        text = " $total",
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
 * 二级那一颗：比一级**低一档** —— 只描边、不填主色（稿 §2 那句"不与一级抢重量"）。
 *
 * 一级是"去哪一屏"，二级是"同一屏换哪条腿"。填成同一款实心红就会读成两个并列的按钮组。
 */
@Composable
private fun PixivAccountSubPill(
    text: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) tokens.color.primary.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = tokens.spacing.hairline,
                color = if (selected) tokens.color.primary else tokens.color.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = tokens.type.caption,
            fontWeight = if (selected) tokens.type.weightSemibold else tokens.type.weightRegular,
            color = if (selected) tokens.color.primary else tokens.color.textSecondary,
            maxLines = 1,
        )
        // 取不到数就一个数都不摆：0 是"这位真的一张没发"那句断言。
        count?.let { total ->
            Text(
                text = " $total",
                fontSize = tokens.type.overline,
                color = tokens.color.textTertiary,
                maxLines = 1,
            )
        }
    }
}

/**
 * 私密档那一行常驻说明（稿 §1 的 ⑧）：选了含非公开的那几档时钉在**内容第一行**。
 *
 * 从前挂在顶栏副标题上，真机上读起来像"这一屏的标题叫含非公开"；而墙里那些图到底含不含
 * 非公开，是**内容**的事实，归内容说。不涂不透明底（玻璃顶栏内联区那条老约束）。
 */
@Composable
private fun PixivPrivateScopeLine(scope: PixivReadScope) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                top = tokens.spacing.space4,
                bottom = tokens.spacing.space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = tokens.color.textTertiary,
            modifier = Modifier.size(tokens.spacing.chipIconSize),
        )
        Text(
            text = when (scope) {
                PixivReadScope.Private -> "这一档只有你标为非公开的，只有你自己看得到。"
                else -> "公开与非公开合在一起摆，非公开那批只有你自己看得到。"
            },
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
        )
    }
}

/** 三档各一句人话（只在弹层里用；domain 那侧管的是"发几趟"，不管屏上怎么说）。 */
private val PixivReadScope.explain: String
    get() = when (this) {
        PixivReadScope.All -> "公开与非公开合着摆"
        PixivReadScope.Public -> "只有公开的"
        PixivReadScope.Private -> "只有你标为非公开的"
    }

/** 一级三段。[restrictApplies] 是"这一屏要不要带 restrict"的唯一判据。 */
internal enum class PixivAccountSection(val screenLabel: String) {
    DYNAMIC("动态"),
    BOOKMARKS("收藏"),
    FOLLOWING("已关注"),
    ;

    /**
     * 这一段的哪一子屏要带 `restrict`。
     *
     * ⚠️ 穷尽 when 且**没有 else**：`/v1/user/illusts`（我发布的）压根没有这一枚参数，
     * 账号管理不发趟。把这两支写成"默认要"就是往站方发一枚它不认的参数 ——
     * 站方不理，而屏上那枚「看：全部」还亮着。
     */
    fun restrictApplies(tab: PixivAccountFeedTab): Boolean = when (this) {
        DYNAMIC -> when (tab) {
            PixivAccountFeedTab.MINE -> false
            PixivAccountFeedTab.FOLLOW -> true
            PixivAccountFeedTab.ACCOUNT -> false
        }

        BOOKMARKS -> true
        FOLLOWING -> true
    }
}

/** 动态段那一行的二级三档。 */
internal enum class PixivAccountFeedTab(val screenLabel: String) {
    MINE("我发布的"),
    FOLLOW("关注动态"),
    ACCOUNT("账号管理"),
}

/**
 * 站方切作品桶那枚 `type` 的唯一出处。
 *
 * ⚠️ 只钉 `illust` 这一支是**刻意的**：稿上「我发布的」不分插画/漫画两桶（2026-10-19 拍板 ⑥），
 * 所以这一屏只发 `illust` 那一桶，漫画在卡上带角标。哪天要接第二桶，加一颗名字而不是改这一颗的值。
 */
private object PixivWorkCategory {
    const val illust = "illust"
}

/**
 * 票据在这里现取。取不到就交空串 —— 空串必然被站方拒（401），那一趟的失败由调用点念成"这一回没取上"。
 *
 * ⚠️ 这一颗**不再判闸门**：能走到这里的调用点都过了 [pixivAccountGate]，再判一遍就是第二把尺子。
 */
private suspend fun bearerOf(account: PixivCredentials?): String = account?.bearerOrNull().orEmpty()

/** 闸门没过的那一屏：一句话，外加**只在票据那一档**才摆得出来的重试。 */
@Composable
private fun PixivAccountGateState(
    gate: PixivAccountGate,
    topPadding: Dp,
    onRetry: () -> Unit,
) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, start = tokens.spacing.screenHorizontal, end = tokens.spacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        VeneraEmptyView(
            title = when (gate) {
                PixivAccountGate.NotSignedIn -> "还没登录 pixiv"
                PixivAccountGate.MissingUserId -> "这个登录没记下用户号"
                PixivAccountGate.BearerUnavailable -> "登录信息这会儿取不到"
                PixivAccountGate.Ready -> "这一屏暂时没有内容"
            },
            // 那句话由 domain 那侧说（四档两两不同、不许带机器味 —— 判据在 `PixivAccountGateTest`）。
            message = gate.screenLine.orEmpty(),
            tone = if (gate.retryIsMeaningful) VeneraEmptyTone.Failed else VeneraEmptyTone.NotYet,
            // ⚠️ 出路不在这一屏的那两档**不给按钮**：按下去还是同一句话的按钮是假把手。
            actionText = if (gate.retryIsMeaningful) "再试一次" else null,
            onAction = if (gate.retryIsMeaningful) onRetry else null,
        )
    }
}

/**
 * 这一侧压根没接 pixiv 取数腿（`GalleryPorts.pixiv == null`）时的那一屏。
 *
 * 与"没登录"分开念：那一档用户能自己修，这一档是他这台机器上就没有这条路 ——
 * 说"去登录"就是骗他白跑一趟。
 */
@Composable
private fun PixivAccountWallUnavailable(topPadding: Dp) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, start = tokens.spacing.screenHorizontal, end = tokens.spacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        VeneraEmptyView(
            title = "这一台机器上读不了 pixiv",
            message = "这一侧没有接 pixiv 的取数腿，账号里的东西取不到。",
            tone = VeneraEmptyTone.NotYet,
        )
    }
}

/**
 * 两面"同一件事的两种看法"共用的那一颗墙：收藏段与关注动态段走的就是它。
 *
 * 三条判据全部走 `PixivAccountFeed` 那几颗纯函数，不在这里另写一遍：
 * 两趟各记各的游标（[PixivLegCursor]）、合屏按 [mergePixivLegs]（公开的在前 + 按 uid 去重）、
 * "到底"按 [pixivFeedExhausted]（**每一条在途的腿都到底**才算到底）。
 * 这三件事散在两处各写一遍的翻页里，正是"公开到底了就宣布都摆完了"那一族错的地方。
 *
 * 数据与滚动位置都活在 [PixivAccountSessionStore]：切段 / 切档 / 离开这一页都不清、不重取；
 * 第一页只由 `ensure` 发一次，之后只有下拉刷新与错误页的「重试」才再发。
 *
 * @param sessionKey 这一面墙的身份（段 + 子档 + 用户号 + 档位）。key 相同 = 同一份真相。
 * @param header 只有「我发布的」与含非公开那几档给：整宽一行画在墙之前、跟着滚。
 */
@Composable
private fun PixivTwoLegWall(
    legs: List<PixivRestrict>,
    sessionKey: String,
    fetch: suspend (PixivRestrict, String?) -> Result<PixivAppPage>,
    imageLoader: ImageLoader,
    columns: Int,
    topPadding: Dp,
    scrollBehavior: ScrollBehavior,
    showsPrivate: Boolean,
    onOpenPost: (GallerySite, Long) -> Unit,
    onRefreshIdentity: () -> Unit,
    header: (@Composable () -> Unit)?,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val ports = remember(context) { GalleryPorts.of(context) }
    val guard = remember(context) { ports.contentGuard }
    val maskMode by guard.nsfwMaskMode.collectAsState()
    val blockAi by ports.prefs.galleryBlockAi.collectAsState()

    val session = remember(sessionKey) { PixivAccountSessionStore.wall(sessionKey) }
    val pages = session.pages

    // 只把**答过一趟**的腿记进账：没答过的腿 nextUrl 也是 null，直接读会把"还没开始"念成"已经到底"。
    val cursors = legs.mapNotNull { restrict -> pages[restrict]?.let { PixivLegCursor(restrict, it.nextUrl) } }
    val arrived = cursors.size == legs.size
    val exhausted = pixivFeedExhausted(cursors)

    val merged = remember(pages, legs) {
        mergePixivLegs(legs.map { pages[it]?.works ?: emptyList() }) { it.post.uid }
    }
    val wall = remember(merged, maskMode, blockAi) {
        buildGalleryWall(merged.map { it.post }, maskMode, blockAi = blockAi) { post ->
            guard.blockedGalleryRule(author = post.author, tags = post.tagList)?.pattern
        }
    }
    val titles = remember(merged) { merged.associate { it.post.uid to it.title } }
    val avatars = remember(merged) { merged.associate { it.post.uid to it.authorAvatarUrl } }

    // 第一页**只在这里发一次**。「全部」少发一趟就是屏上写着全部而只读了公开；
    // 而回来看见 `asked` 就不再发 —— 刷新是用户的动作，不是重组的副作用。
    LaunchedEffect(sessionKey) { session.ensure(legs, fetch) }

    // 滚动位置从会话里接着摆；每一次滚动再写回去（切走 / 离开这一页时组合会整个销毁）。
    // ⚠️ 输入键带上 sessionKey：换范围 = 另一面墙，状态不重建就会把上一档的位置带过去，
    // 而快照回写又会把它写进新那一档的账。
    val gridState = rememberSaveable(sessionKey, saver = LazyStaggeredGridState.Saver) {
        LazyStaggeredGridState(session.scrollIndex, session.scrollOffset)
    }
    LaunchedEffect(gridState, sessionKey) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                session.scrollIndex = index
                session.scrollOffset = offset
            }
    }

    // 续页：三道闸门与云端书签墙同一套（墙得是活的 / 到底了不判 / 在取时不并发）。
    // 闸门与"到底"的判定收在 session.loadMore 里，屏这边只负责"快到底了"这一个读数。
    LaunchedEffect(gridState, sessionKey, arrived, exhausted) {
        snapshotFlow {
            val info = gridState.layoutInfo
            GalleryLoadMoreState(
                wallActive = merged.isNotEmpty(),
                exhausted = exhausted,
                busy = session.busy,
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
                session.loadMore(legs, fetch)
            }
    }

    val pullState = rememberPullToRefreshState()
    val refresh: () -> Unit = {
        // 身份读数（关注数 / 收藏数 / 头部）跟着一起换新：它们与墙是同一批事实。
        onRefreshIdentity()
        session.refresh(legs, fetch)
    }
    // 顶栏要灰、要收，靠的是这一条 nestedScroll；少了它内容从顶栏底下直穿而过。
    val contentModifier = Modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection)

    PullToRefreshBox(
        isRefreshing = session.refreshing,
        onRefresh = refresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            // 默认贴在 Box 顶上 = 压在 chrome 底下看不见；让到内容第一行下面。
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = session.refreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topPadding),
                color = tokens.color.primary,
                containerColor = tokens.color.surface,
            )
        },
    ) {
        when {
            session.loading && merged.isEmpty() -> Box(contentModifier, contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(tokens.spacing.loaderPage),
                    color = tokens.color.primary,
                    trackColor = tokens.color.surfaceVariant,
                )
            }

            session.pageError != null && merged.isEmpty() -> Box(
                modifier = contentModifier.padding(horizontal = tokens.spacing.screenHorizontal),
                contentAlignment = Alignment.Center,
            ) {
                VeneraEmptyView(
                    title = "这一屏没取上",
                    message = session.pageError.orEmpty(),
                    tone = VeneraEmptyTone.Failed,
                    actionText = "重新获取",
                    onAction = refresh,
                )
            }

            else -> GalleryCardsGrid(
            cards = wall.cards,
            imageLoader = imageLoader,
            columnCount = columns,
            state = gridState,
            contentPadding = PaddingValues(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                top = topPadding,
                bottom = tokens.spacing.space11,
            ),
            modifier = contentModifier,
            header = header,
            stationTitles = titles,
            authorAvatars = avatars,
            onOpen = { post ->
                // 队列一起交 —— 少了它大图页左右划认不出"同墙的这批"。
                GalleryViewerQueue.set(wall.cards.map { it.post })
                onOpenPost(post.site, post.id)
            },
            footer = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = tokens.spacing.space6),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        session.loadingMore -> CircularWavyProgressIndicator(
                            modifier = Modifier.size(tokens.spacing.loaderInline),
                            color = tokens.color.primary,
                            trackColor = tokens.color.surfaceVariant,
                        )

                        session.moreError != null -> Text(
                            text = session.moreError.orEmpty(),
                            fontSize = tokens.type.caption,
                            lineHeight = TEXT_LINE_HEIGHT.em,
                            color = tokens.color.textTertiary,
                            textAlign = TextAlign.Center,
                        )

                        // 「到底」要两趟**都答过且都到底**（arrived && exhausted）：
                        // 只问第一条腿的话，私密那批永远进不来而页脚已经在念"都摆完了"。
                        // 后面那串是**已经摆出来几张**（本地数得着），不是站方的总数 ——
                        // 「全部」那一档的合计数站方从来不给，稿上"共 451 张"那一格只能这样落。
                        arrived && exhausted -> Text(
                            text = (if (showsPrivate) "公开与非公开都摆完了" else "都摆完了") +
                                " · 共 ${wall.cards.size} 张",
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
}

/**
 * 墙前面那一颗头部：含非公开的档位念一行、`mine` 那一档摆 hero 与身份。
 *
 * 返回 null = 这一墙没有头部（不让空 lambda 在墙里占一行）。
 */
private fun pixivWallHeader(
    target: PixivAccountSection,
    tab: PixivAccountFeedTab,
    scope: PixivReadScope,
    head: PixivUserHead?,
    detail: PixivUserDetail?,
    account: PixivCredentials?,
    works: GalleryPixivWorks?,
    userId: String?,
    fallbackName: String,
    imageLoader: ImageLoader,
): (@Composable () -> Unit)? {
    val scopeLine = target.restrictApplies(tab) && scope.showsPrivate
    val mineHeader = target == PixivAccountSection.DYNAMIC && tab == PixivAccountFeedTab.MINE
    if (!scopeLine && !mineHeader) return null
    return {
        if (scopeLine) PixivPrivateScopeLine(scope)
        if (mineHeader) {
            PixivMineHeader(
                head = head,
                detail = detail,
                account = account,
                works = works,
                userId = userId,
                fallbackName = fallbackName,
                imageLoader = imageLoader,
            )
        }
    }
}

/**
 * 「已关注」那一屏：一列**人**，不是墙。
 *
 * 每位再发一跳取三张低清预览（2026-10-19 拍板 ②）。取不到的那位**不空一格**：
 * 退成只有头像与名字的一行 —— 那一行读起来是"这一位没预览"，不是"这一位没关注上"。
 *
 * ⚠️ 这一屏与首页「正在关注的画师」是同一本账（站方 `restrict` 那一枚决定含不含私密关注），
 * 但两处各发各的趟：合并成一份缓存的话，账号页切「非公开」会把首页那一栏一起带歪。
 */
@Composable
private fun PixivFollowingSection(
    legs: List<PixivRestrict>,
    sessionKey: String,
    topPadding: Dp,
    scrollBehavior: ScrollBehavior,
    fetchUsers: suspend (PixivRestrict, String?) -> Result<PixivUserPage>,
    fetchCovers: suspend (String) -> Result<PixivAppPage>,
    onRefreshIdentity: () -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val imageLoader = remember { GalleryImageLoader.get(context) }

    // 名单与三张预览都活在会话仓：切走再回来直接接着摆，不按行重发。
    val session = remember(sessionKey) { PixivAccountSessionStore.following(sessionKey) }
    val userPages = session.userPages

    // 两趟各记各的游标：只留第一条腿的游标 = "全部"这一档里私密那批永远翻不到（与那两面墙同一条错）。
    val cursors = legs.mapNotNull { restrict -> userPages[restrict]?.let { PixivLegCursor(restrict, it.nextUrl) } }
    val arrived = cursors.size == legs.size
    val exhausted = pixivFeedExhausted(cursors)
    val users = remember(userPages, legs) {
        mergePixivLegs(legs.map { userPages[it]?.users ?: emptyList() }) { it.id.toString() }
    }

    LaunchedEffect(sessionKey) { session.ensure(legs, fetchUsers) }

    // 与网格那面墙同理：换范围是另一份名单，状态必须跟着 sessionKey 重建。
    val listState = rememberSaveable(sessionKey, saver = LazyListState.Saver) {
        LazyListState(session.scrollIndex, session.scrollOffset)
    }
    LaunchedEffect(listState, sessionKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                session.scrollIndex = index
                session.scrollOffset = offset
            }
    }

    LaunchedEffect(listState, sessionKey, arrived, exhausted) {
        snapshotFlow {
            val info = listState.layoutInfo
            GalleryLoadMoreState(
                wallActive = users.isNotEmpty(),
                exhausted = exhausted,
                busy = session.busy,
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
                session.loadMore(legs, fetchUsers)
            }
    }

    val pullState = rememberPullToRefreshState()
    val refresh: () -> Unit = {
        onRefreshIdentity()
        session.refresh(legs, fetchUsers)
    }
    val contentModifier = Modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection)

    PullToRefreshBox(
        isRefreshing = session.refreshing,
        onRefresh = refresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = session.refreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topPadding),
                color = tokens.color.primary,
                containerColor = tokens.color.surface,
            )
        },
    ) {
        when {
            session.loading && users.isEmpty() -> Box(contentModifier, contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(tokens.spacing.loaderPage),
                    color = tokens.color.primary,
                    trackColor = tokens.color.surfaceVariant,
                )
            }

            session.error != null && users.isEmpty() -> Box(
                contentModifier.padding(horizontal = tokens.spacing.screenHorizontal),
                contentAlignment = Alignment.Center,
            ) {
                VeneraEmptyView(
                    title = "关注列表没取上",
                    message = session.error.orEmpty(),
                    tone = VeneraEmptyTone.Failed,
                    actionText = "重新获取",
                    onAction = refresh,
                )
            }

            users.isEmpty() -> Box(
                contentModifier.padding(horizontal = tokens.spacing.screenHorizontal),
                contentAlignment = Alignment.Center,
            ) {
                VeneraEmptyView(
                    title = "还没有关注任何人",
                    message = "在画师页按「关注」之后就到这里来看这一份名单。",
                    tone = VeneraEmptyTone.Nothing,
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = contentModifier,
                contentPadding = PaddingValues(
                    start = tokens.spacing.screenHorizontal,
                    end = tokens.spacing.screenHorizontal,
                    top = topPadding,
                    bottom = tokens.spacing.space11,
                ),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                items(users, key = { it.id }) { user ->
                    PixivFollowingRow(
                        user = user,
                        previews = session.covers[user.id].orEmpty(),
                        imageLoader = imageLoader,
                        onOpen = { context.openPixivArtistPage(user.id, user.name) },
                        onCovers = { urls -> session.putCovers(user.id, urls) },
                        fetchCovers = fetchCovers,
                    )
                }
                item(key = "following-footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = tokens.spacing.space6),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            session.loadingMore -> CircularWavyProgressIndicator(
                                modifier = Modifier.size(tokens.spacing.loaderInline),
                                color = tokens.color.primary,
                                trackColor = tokens.color.surfaceVariant,
                            )

                            session.moreError != null -> Text(
                                text = session.moreError.orEmpty(),
                                fontSize = tokens.type.caption,
                                color = tokens.color.textTertiary,
                                textAlign = TextAlign.Center,
                            )

                            // 与那两面墙同一条判据：**每一趟都答过且都到底**才念"都在这儿了"。
                            arrived && exhausted -> Text(
                                text = "关注的人都在这儿了，共 ${users.size} 位",
                                fontSize = tokens.type.caption,
                                color = tokens.color.textTertiary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一行一位（稿 §1 的 ③）：**三张封面拼成一条压在上方**，下方才是头像 + 名字 + @账号。
 *
 * 2026-10-19 真机读数：旧版把三张 56×42 的小图挤在行尾，竖图 / 方图 / 横图混在同一列里
 * 参差成锯齿，第三张几乎贴到屏右缘。改成整行宽三等分之后每一格同宽同高 ——
 * 比例由这一行给，不由图自己给。
 *
 * 一张都没取到就**整条不摆**：留三格空位会被读成"这位的图挂了"，而真话是"预览没取到"。
 * 少于一张时后面的格子空着（不拉伸），一格的预览就该只占一格。
 */
@Composable
private fun PixivFollowingRow(
    user: PixivUser,
    previews: List<String>,
    imageLoader: ImageLoader,
    onOpen: () -> Unit,
    onCovers: (List<String>) -> Unit,
    fetchCovers: suspend (String) -> Result<PixivAppPage>,
) {
    val tokens = VeneraTokens
    LaunchedEffect(user.id) {
        if (previews.isNotEmpty()) return@LaunchedEffect
        fetchCovers(user.id.toString()).onSuccess { page ->
            onCovers(page.works.take(PixivFollowingPreviewCount).map { it.post.previewUrl })
        }
        // 失败什么都不说：这一行退成"只有头像与名字"就是它要说的那句话。
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.medium))
            .background(tokens.color.surface)
            .clickable(onClick = onOpen),
    ) {
        if (previews.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tokens.spacing.pixivAccountCoverStripHeight),
            ) {
                val tiles = List(PixivFollowingPreviewCount) { previews.getOrNull(it) }
                tiles.forEach { url ->
                    if (url == null) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    } else {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = tokens.spacing.space4,
                    end = tokens.spacing.space4,
                    top = tokens.spacing.space3,
                    bottom = tokens.spacing.space4,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        ) {
            PixivRoundAvatar(
                url = user.avatarUrl.ifBlank { null },
                name = user.name,
                size = tokens.spacing.pixivAccountMiniAvatar,
                imageLoader = imageLoader,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    fontSize = tokens.type.itemTitle,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 稿上这一格写的是「作品 N · N 天前更新」—— 那两样站方在这一条腿里**都没给**
                // （`/v1/user/following` 每人只回 id / name / account / 头像 / 是否屏蔽），
                // 所以这里摆真给得了的那一样：@账号。要凑那句就得替每人再发一趟并编一个时间。
                Text(
                    text = if (user.account.isBlank()) "这位没设账号名" else "@${user.account}",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 稿上这一格是**状态**不是把手：这一页里每一位都在关注列表里，
            // 所以它不挂 onClick（整行才是去画师页的那一下）。灰描边而非主色，免得抢名字。
            PixivFollowStatePill(text = "已关注")
        }
    }
}

@Composable
private fun PixivFollowStatePill(text: String) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(percent = 50)
    Text(
        text = text,
        fontSize = tokens.type.caption,
        color = tokens.color.textSecondary,
        maxLines = 1,
        modifier = Modifier
            .border(tokens.spacing.hairline, tokens.color.outlineVariant, shape)
            .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space1),
    )
}

/** 「已关注」那一行几位预览的张数（站方卡片那一档是三张）。 */
private const val PixivFollowingPreviewCount = 3

/**
 * 「我发布的」那一屏的头部（稿 §2 的 ④）：hero + 头像**压进** hero + 名字与出口 + 四个数。
 *
 * 四个数**退「—」不拿 0 顶**：站方没答那一格与"这位真的零张"是两件事，
 * 写成 0 就是替站方编一个它没给过的结论（本仓反复立案的那族假读数）。
 *
 * 头像为什么必须压边而不是摆在 hero 下面：压在照片上那一圈描边环才对着页面底色，
 * 读起来是"这一位的主视觉"；整颗掉到 hero 之下就变成两张不相干的图（2026-10-19 真机读数）。
 */
@Composable
private fun PixivMineHeader(
    head: PixivUserHead?,
    detail: PixivUserDetail?,
    account: PixivCredentials?,
    works: GalleryPixivWorks?,
    userId: String?,
    fallbackName: String,
    imageLoader: ImageLoader,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val name = head?.name?.takeIf { it.isNotBlank() } ?: fallbackName
    val heroUrl = head?.backgroundUrl ?: firstWorkUrl(works, userId, account)
    // 压进去的那半颗：头像一半在照片上、一半在页面底上，所以正文要让出**半颗**的高度。
    val avatarOverlap = tokens.spacing.pixivArtistAvatarSize / 2

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(tokens.spacing.pixivArtistHeroMinHeight)
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
            // 这颗**不**挂在这条 Box 的 clip 里（Box 默认不裁），所以它能越出照片下沿。
            PixivRoundAvatar(
                url = head?.avatarUrl,
                name = name,
                size = tokens.spacing.pixivArtistAvatarSize,
                imageLoader = imageLoader,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = tokens.spacing.screenHorizontal)
                    .offset(y = avatarOverlap),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tokens.spacing.pixivArtistHeroMinHeight + avatarOverlap)
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    ) {
                        Text(
                            text = name,
                            fontSize = tokens.type.display,
                            fontWeight = tokens.type.weightSemibold,
                            color = tokens.color.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 会员位只作读数：站方标了才摆，没标一个像素都不占。
                        if (head?.premium == true) {
                            PixivAccountBadge(text = "会员")
                        }
                    }
                    val accountName = head?.account.orEmpty()
                    val identityLine = buildString {
                        if (accountName.isNotBlank()) append("@$accountName")
                        if (!userId.isNullOrBlank()) {
                            if(isNotEmpty()) append(" · ")
                            append("pixiv 账号 $userId")
                        }
                    }
                    if (identityLine.isNotBlank()) {
                        Text(
                            text = identityLine,
                            fontSize = tokens.type.caption,
                            color = tokens.color.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = tokens.spacing.space1),
                        )
                    }
                }
                // 站方原页的正当出口（小说、约稿、完整收藏列表只有那边有）。
                // 没记下用户号就**不摆这一颗**：它要打开的那一页在这一版根本拼不出来，摆上就是假把手。
                if (!userId.isNullOrBlank()) {
                    PixivAccountGhostButton(
                        text = "在 pixiv 打开",
                        icon = Icons.Outlined.OpenInNew,
                    ) {
                        context.openExternal("https://www.pixiv.net/users/$userId")
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.space5, bottom = tokens.spacing.space6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixivAccountStat("粉丝", detail?.totalFollowers, Modifier.weight(1f))
                PixivAccountStatDivider()
                PixivAccountStat("作品", detail?.totalIllusts, Modifier.weight(1f))
                PixivAccountStatDivider()
                PixivAccountStat("关注", head?.followingCount, Modifier.weight(1f))
                PixivAccountStatDivider()
                PixivAccountStat("收藏", detail?.totalBookmarksPublic, Modifier.weight(1f))
            }
        }
    }
}

/** 头部那一枚小标（会员）。不涂不透明主色：它住在名字右端，抢色就抢了名字。 */
@Composable
private fun PixivAccountBadge(text: String) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(tokens.spacing.space1)
    Text(
        text = text,
        fontSize = tokens.type.overline,
        color = tokens.color.primary,
        modifier = Modifier
            .clip(shape)
            .background(tokens.color.primary.copy(alpha = 0.12f))
            .padding(horizontal = tokens.spacing.space2, vertical = tokens.spacing.space1),
    )
}

/** 描边 ghost 按钮：这一屏里"去别处看看"那一类动作共用一颗。 */
@Composable
private fun PixivAccountGhostButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(tokens.spacing.hairline, tokens.color.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.color.textSecondary,
            modifier = Modifier.size(tokens.spacing.chipIconSize),
        )
        Text(text = text, fontSize = tokens.type.caption, color = tokens.color.textSecondary, maxLines = 1)
    }
}

/** 四格之间那道竖线：只在两格之间，首尾不摆。 */
@Composable
private fun PixivAccountStatDivider() {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .width(tokens.spacing.hairline)
            .height(tokens.spacing.space6)
            .background(tokens.color.outlineVariant),
    )
}

/** 一格的数：null 念「—」，不念 0。 */
@Composable
private fun PixivAccountStat(label: String, value: Int?, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    Column(modifier = modifier) {
        Text(
            text = value?.toString() ?: "—",
            fontSize = tokens.type.itemTitle,
            fontWeight = tokens.type.weightSemibold,
            color = tokens.color.textPrimary,
        )
        Text(text = label, fontSize = tokens.type.caption, color = tokens.color.textTertiary)
    }
}

/**
 * 头像圆座：有地址画地址，没地址退**首字母**，绝不空一圈（空圈会被读成"这张图挂了"）。
 * 口径与顶栏那颗头像、卡上那枚小头像同一条。
 */
@Composable
private fun PixivRoundAvatar(
    url: String?,
    name: String,
    size: Dp,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .size(size)
            .veneraAvatarRing(CircleShape)
            .clip(CircleShape)
            .background(tokens.color.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            if (name.isBlank()) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = tokens.color.textSecondary,
                    modifier = Modifier.size(size / 2),
                )
            } else {
                Text(
                    text = name.take(1),
                    fontSize = tokens.type.body,
                    color = tokens.color.textSecondary,
                )
            }
        } else {
            AsyncImage(
                model = url,
                contentDescription = name,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** hero 没自设背景时退"我自己第一张作品"的封面（口径同画师页那三档回退）。 */
@Composable
private fun firstWorkUrl(works: GalleryPixivWorks?, userId: String?, account: PixivCredentials?): String? {
    var url by remember(userId) { mutableStateOf<String?>(null) }
    LaunchedEffect(userId, account) {
        val bearer = account?.bearerOrNull() ?: return@LaunchedEffect
        if (works == null || userId == null) return@LaunchedEffect
        url = works.userIllusts(bearer, userId, PixivWorkCategory.illust, null).getOrNull()?.works
            ?.firstOrNull()?.post?.previewUrl
    }
    return url
}

/**
 * 「账号管理」那一子页（稿 §2 的 ⑤）：分组表单，**不是一排同款灰块**。
 *
 * 2026-10-19 真机读数：旧版三行都写「打开」（包括退出登录那一行），既没有分组标题，
 * 也没有稿上那两行"进来先看哪一批"。这一屏的每一行右端那枚把手说的就是这一下会干什么 ——
 * 复制、重新登录、贴进来、打开、一个值、退出，**六样各写各的**，不许复用成一句「打开」。
 *
 * 退出是**破坏性**动作（清这台机器上的凭据），所以走二次确认那一档，
 * 而不是"点一下就没了"。确认文案逐字对齐顶栏那颗头像以前那弹层（两处各写一句迟早分叉）。
 */
@Composable
private fun PixivAccountManageSection(
    topPadding: Dp,
    scrollBehavior: ScrollBehavior,
    account: PixivCredentials?,
    userId: String?,
    authLine: String,
    signedName: String?,
    head: PixivUserHead?,
    scope: PixivReadScope,
    showPrivate: Boolean,
    accountPrefs: PixivAccountPreferences?,
    onPickScope: () -> Unit,
    onToggleShowPrivate: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var signOutOpen by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val name = head?.name?.takeIf { it.isNotBlank() } ?: signedName ?: "已登录 pixiv"
    val accountName = head?.account.orEmpty()
    val identityLine = buildString {
        if (accountName.isNotBlank()) append("@$accountName")
        if (!userId.isNullOrBlank()) {
            if (isNotEmpty()) append(" · ")
            append("pixiv 账号 $userId")
        }
        if (head?.premium == true) {
            if (isNotEmpty()) append(" · ")
            append("会员")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 这一屏也要让顶栏跟着收：不接 nestedScroll 的话内容从顶栏底下穿过去。
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(top = topPadding)
            .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space6),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
    ) {
        PixivAccountGroup(title = null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(tokens.spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                PixivRoundAvatar(
                    url = head?.avatarUrl,
                    name = name,
                    size = tokens.spacing.pixivAccountIdentityAvatar,
                    imageLoader = remember { GalleryImageLoader.get(context) },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightSemibold,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // 用户号没记下就直说：这一格退「—」比退成一串空白好读。
                        text = identityLine.ifBlank { "这台机器上没记下用户号" },
                        fontSize = tokens.type.caption,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PixivAccountHandle(text = "复制账号", destructive = false) {
                    val line = listOfNotNull(
                        accountName.takeIf { it.isNotBlank() }?.let { "@$it" },
                        userId?.takeIf { it.isNotBlank() }?.let { "pixiv 账号 $it" },
                    ).joinToString(" · ")
                    if (line.isBlank()) {
                        notice = "这一版没记下可复制的账号信息。"
                    } else {
                        clipboard.setText(AnnotatedString(line))
                        notice = "已经复制：$line"
                    }
                }
            }
        }

        PixivAccountGroup(title = "登录状态") {
            PixivAccountFormRow(
                label = authLine,
                hint = "登录信息存在这台机器上",
            ) {
                PixivAccountHandle(text = "重新登录", destructive = false) {
                    if (account == null || !account.isClientConfigured) {
                        notice = MISSING_CLIENT_HINT
                    } else {
                        val challenge = account.beginLogin()
                        notice = if (challenge == null) {
                            MISSING_CLIENT_HINT
                        } else {
                            openPixivLoginInBrowser(context, challenge.authorizeUrl)
                        }
                    }
                }
            }
            PixivAccountRowDivider()
            PixivAccountFormRow(
                label = "手动填登录信息",
                hint = "浏览器那一步没跳回来时，可以把 pixiv 的登录串贴进来",
            ) {
                PixivAccountHandle(text = "贴进来", destructive = false) { pasteOpen = true }
            }
            if (!userId.isNullOrBlank()) {
                PixivAccountRowDivider()
                PixivAccountFormRow(
                    label = "在浏览器里打开",
                    hint = "看小说、约稿、完整的收藏列表",
                ) {
                    PixivAccountHandle(text = "打开", destructive = false) {
                        context.openExternal("https://www.pixiv.net/users/$userId")
                    }
                }
            }
        }

        // 稿上这一组两行**要有下落才摆**：偏好槽位没接的那一侧（桌面）压根没有这两枚键，
        // 摆出来就是两枚按了不改任何东西的假开关。
        if (accountPrefs != null) {
            PixivAccountGroup(title = "进来时先看哪一批") {
                PixivAccountFormRow(
                    label = "默认看的范围",
                    hint = "打开这一页时先给你哪一批",
                ) {
                    PixivAccountValue(text = scope.screenLabel, onClick = onPickScope)
                }
                PixivAccountRowDivider()
                PixivAccountFormRow(
                    label = "显示非公开内容",
                    hint = "关掉后，你标为非公开的收藏和关注都不列出来",
                ) {
                    PixivAccountValue(
                        text = if (showPrivate) "显示" else "不显示",
                        onClick = onToggleShowPrivate,
                    )
                }
            }
        }

        PixivAccountGroup(title = "退出") {
            PixivAccountFormRow(
                label = "退出登录",
                hint = "只会退出这台手机上的登录，收藏和关注都还在 pixiv 那边",
            ) {
                PixivAccountHandle(text = "退出", destructive = true) { signOutOpen = true }
            }
        }
    }

    VeneraDialog(
        show = signOutOpen,
        onDismissRequest = { signOutOpen = false },
        title = "退出 pixiv",
        content = {
            Text(
                text = "退出会清掉这台机器上的 pixiv 登录凭据。收藏与关注都存在站方那边，不受影响。",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
            )
        },
        confirmText = "退出登录",
        confirmDestructive = true,
        onConfirm = {
            account?.signOut()
            signOutOpen = false
            // 退完就回首页：这一屏的三段在没有凭据之后全是同一句"还没登录"，留着只是让人再点三次。
            onSignedOut()
        },
        dismissText = "先不",
    )

    PixivPasteCredentialDialog(
        show = pasteOpen,
        account = account,
        onDismiss = { pasteOpen = false },
        onDone = {
            pasteOpen = false
            notice = "登录信息已经换上，这一屏重新读一遍。"
        },
        onError = { line -> notice = line },
    )

    VeneraDialog(
        show = notice != null,
        onDismissRequest = { notice = null },
        title = "说一句",
        content = { notice?.let { line -> Text(text = line, fontSize = tokens.type.caption) } },
        confirmText = "知道了",
        onConfirm = { notice = null },
    )
}

/** 客户端凭据缺失那一句（与顶栏那颗头像的登录那一路同一句，两处各抄一份迟早分叉）。 */
private const val MISSING_CLIENT_HINT = "这台机器还没填 pixiv 的客户端凭据，登录这一路暂时走不了。"

/** 一组：卡外一行小标题 + 一张圆角卡。标题为空 = 只有那张卡。 */
@Composable
private fun PixivAccountGroup(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val tokens = VeneraTokens
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                fontSize = tokens.type.overline,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(start = tokens.spacing.space2, bottom = tokens.spacing.space2),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.shape.medium))
                .background(tokens.color.surface),
            content = content,
        )
    }
}

/** 组里的一行：左边名字 + 一句人话，右边那一枚把手**由调用方给**（六行六种把手，不是一句「打开」）。 */
@Composable
private fun PixivAccountFormRow(
    label: String,
    hint: String,
    handle: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = tokens.type.itemTitle,
                color = tokens.color.textPrimary,
            )
            Text(text = hint, fontSize = tokens.type.caption, color = tokens.color.textTertiary)
        }
        handle()
    }
}

/** 行与行之间那道淡线：画在卡内，卡外不画（卡与卡之间靠间距分）。 */
@Composable
private fun PixivAccountRowDivider() {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = tokens.spacing.space4)
            .height(tokens.spacing.hairline)
            .background(tokens.color.outlineVariant.copy(alpha = 0.4f)),
    )
}

/** 把手那一档：描边药丸。`destructive` **没有默认值** —— 每一行都要自己说是不是破坏性的。 */
@Composable
private fun PixivAccountHandle(
    text: String,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(percent = 50)
    val tint = if (destructive) tokens.color.error else tokens.color.primary
    Text(
        text = text,
        fontSize = tokens.type.caption,
        fontWeight = tokens.type.weightMedium,
        color = tint,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (destructive) tint.copy(alpha = 0.12f) else Color.Transparent)
            .border(tokens.spacing.hairline, tint, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
    )
}

/** 把手的另一档：这一行不改什么，它**报一个值**并按下去让你换（值 + 尖角，不写成动词）。 */
@Composable
private fun PixivAccountValue(text: String, onClick: () -> Unit) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.space2, vertical = tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = tokens.type.caption,
            color = tokens.color.primary,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = tokens.color.primary,
            modifier = Modifier.size(tokens.spacing.chipIconSize),
        )
    }
}

/**
 * 粘贴登录串那一弹层（口径逐字对齐顶栏那颗头像的登录弹层里那一段）。
 *
 * 为什么这里要有一份：账号管理这一子页是这一屏唯一的登录出口，而那一颗头像在**没登录**时
 * 才开原弹层 —— 已经登录的人在这一屏要"换一条凭据"没有别的路。
 */
@Composable
private fun PixivPasteCredentialDialog(
    show: Boolean,
    account: PixivCredentials?,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    onError: (String) -> Unit,
) {
    val tokens = VeneraTokens
    val uiScope = rememberCoroutineScope()
    var pasted by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    VeneraDialog(
        show = show,
        onDismissRequest = { if (!busy) onDismiss() },
        title = "贴一条登录信息",
        dismissText = "先不",
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "支持三种写法：refresh token、登录回跳的整条链接、或者站方给的 token JSON。" +
                        "贴进来只改这台机器上的登录信息。",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                )
                VeneraTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    label = "粘贴 refresh token / 回跳链接 / token JSON",
                    singleLine = false,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.space4),
                )
            }
        },
        confirmText = if (busy) "换票据…" else "用这一条登录",
        onConfirm = {
            val target = account
            if (target == null) {
                onError("这台机器没接上登录件。")
            } else if (pasted.isNotBlank() && !busy) {
                busy = true
                uiScope.launch {
                    target.signInWithPasted(pasted)
                        .onSuccess {
                            busy = false
                            pasted = ""
                            onDone()
                        }
                        .onFailure {
                            busy = false
                            onError(it.readableWallError())
                        }
                }
            }
        },
    )
}

/** 交给真浏览器（口径同顶栏那颗头像的登录那一路）。 */
private fun Context.openExternal(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
