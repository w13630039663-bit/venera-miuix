package com.venera.desktop


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.components.VeneraNavTab
import com.venera.compose.components.VeneraSideBar
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.imageWallColumnCount
import com.venera.compose.components.imageWallHorizontalPadding
import com.venera.compose.components.sideBarModeFor
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.wideScreenLayoutMode
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.desktop.favorites.DesktopFavoritesScreen
import com.venera.desktop.search.DesktopSearchScreen
import com.venera.desktop.sourcemanage.DesktopSourceManageScreen
import com.venera.desktop.explore.DesktopExploreScreen
import com.venera.desktop.detail.DesktopComicDetailScreen
import com.venera.desktop.reader.DesktopReaderScreen
import com.venera.desktop.queue.DesktopComicQueueScreen
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.ui.desktopComicQueueRemovedLine
import com.venera.desktop.gallery.ui.desktopComicQueueStoppedLine
import com.venera.desktop.gallery.ui.desktopComicEpisodeDownloadNote
import com.venera.desktop.gallery.ui.desktopComicStartQueuePump
import com.venera.desktop.gallery.ui.DesktopWallCardModel
import com.venera.desktop.downloads.DesktopComicDownloadsScreen
import com.venera.desktop.gallery.ui.DesktopComicDownloadListing
import com.venera.desktop.gallery.data.DesktopComicShelfRawIdIndex
import com.venera.desktop.gallery.data.DesktopComicLocalChapter
import com.venera.desktop.gallery.data.DesktopComicSourceOutcome
import com.venera.desktop.gallery.data.desktopComicOfflineLocalChapter
import com.venera.desktop.gallery.data.desktopComicOfflineReadingNote
import com.venera.desktop.gallery.data.desktopComicOfflineReadingReading
import com.venera.desktop.gallery.data.desktopComicOfflineShouldAskEngine
import com.venera.desktop.gallery.data.desktopComicReaderPages
import com.venera.desktop.gallery.data.desktopComicChaptersByComic
import com.venera.desktop.gallery.data.desktopComicDownloadedScan
import com.venera.desktop.gallery.data.desktopComicRepairChapter
import com.venera.desktop.gallery.data.desktopComicShelfRerollKey
import com.venera.desktop.gallery.ui.desktopComicDeleteChapter
import com.venera.desktop.gallery.ui.desktopComicDeleteRowKey
import com.venera.desktop.gallery.ui.desktopComicDeleteScreenLine
import com.venera.desktop.gallery.ui.desktopComicDeleteShouldFire
import com.venera.desktop.gallery.ui.desktopComicDeleteNextKey
import com.venera.desktop.gallery.ui.desktopComicDownloadedReading
import top.yukonga.miuix.kmp.theme.MiuixTheme


/**
 * 桌面大屏壳 —— **复用安卓大屏那一份 UI**，不是另做一套。
 *
 * ## 为什么现在才这样写（2026-10-20，写法 A）
 *
 * 此前桌面侧有两条"平行线"：一条是 Fluent 风格的 `desktop/gallery/ui/`（72 颗），
 * 一条是照着大屏断点重画的 `desktop/ui/`（9 颗 pane）。两条都在**维护第二份 UI**，
 * 正是用户最初说「这样维护太麻烦了」要解决的问题。
 *
 * 这一版把两条都换成「同一份源码两端编译」：
 *  - 侧栏 = `components/VeneraSideBar.kt`（安卓大屏那一颗，逐字同一份）；
 *  - 断点 = `components/WideScreenPolicy.kt` 的 `wideScreenLayoutMode` / `sideBarModeFor`；
 *  - 网格列数 = `imageWallColumnCount` + `imageWallHorizontalPadding`（与安卓同一把尺）；
 *  - 卡片 = `components/venera/VeneraCard.kt` + `VeneraCover.kt`；
 *  - 色板 / 间距 / 字号 = `ui/tokens/`（两端同一份 token）。
 *
 * 桌面侧**自持**的只剩这一颗装配文件（窗口、进程、数据接线那些平台事），
 * UI 本身一行没抄。
 *
 * ## 窗口宽怎么进来
 *
 * `LocalWindowWidth`（`components/LocalWindowWidth.kt`）由这里 provide —— 值取自
 * Compose `Window` 的实际尺寸。Android 侧由 `MainActivity` provide `screenWidthDp`。
 * 两端同一条调用式、各自供给，这就是方案文档 §1.5 说的"收口"。
 */
@Composable
fun VeneraDesktopLargeScreen(
    windowWidth: Dp,
    ports: GalleryPorts,
    modifier: Modifier = Modifier,
    /**
     * 分级遮罩档的当前值（`OFF` / `BLUR` / `HIDE`），由装配点递进来。
     *
     * 2026-10-21：这两个形参是为修验收发现的 R3 加的 —— `LocalUxPlatformServices`
     * 此前**从未 provide**，桌面一直吃 `NoOpUxPlatformServices`，它的
     * `coverMaskStateOf(...)` 恒返回 `"VISIBLE"` ⇒ 共享零件里的封面**全不打码**。
     *
     * 为什么不在 UI 里自己去取：`DesktopUiIsolationTest` ② 那条「UI 里不许找静态口」。
     * 装配点（`VeneraDesktop.kt`）手上有 `DesktopGalleryHandle`，让它递进来就对。
     */
    maskModeOf: () -> String = { "BLUR" },
    /** 用户黑名单是否命中该条（KEYWORD / TAG / AUTHOR 规则）。命中 ⇒ `HIDDEN`。 */
    blockedByUserOf: (sourceKey: String, title: String, author: String, tags: List<String>) -> Boolean =
        { _, _, _, _ -> false },
    /**
     * 取数层要的那颗守卫（它自己调 `blockedGalleryRule`）。
     *
     * 2026-10-21 修安全评审 **B**：`DesktopGalleryWallPolicy.of` 此前在 `desktop/src/main`
     * 里**零调用点**，而 `desktopWallCardOf` 的 `masked` 默认 `false` ⇒
     * **墙上无论档位如何，一张图都不遮**。
     *
     * 缺省 `null` 时取数层会打印 `D_守卫 未判` —— 不静默，但也不假装判过。
     */
    wallGuardOf: () -> com.venera.compose.gallery.data.GalleryContentGuard? = { null },
    /** `prefs.galleryBlockAi` —— 墙上的 AI 角标那一档。 */
    wallBlockAiOf: () -> Boolean = { false },
    /**
     * 源脚本引擎。详情与阅读器都要它：`details()` 给章节表、`pages()` 给页表。
     *
     * `null` 时详情/阅读器会如实说"引擎没起来"，不假装有内容。
     */
    engine: com.venera.engineprobe.EngineSession? = null,
    /** 写阅读历史那一跳。页码一变就调它 —— **这是首页统计/历史的唯一上游**。 */
    writeHistory: (suspend (com.venera.compose.data.db.HistoryRecord) -> Unit)? = null,
    /**
     * 写分级遮罩档。
     *
     * 安全评审 **H4**：`DesktopGalleryContentGuard.setNsfwMaskMode` 此前**零调用点**
     * ⇒ 用户没有任何入口能改这个档 ⇒ 遮罩永远停在默认 `OFF`（= 什么都不遮）。
     * 这颗 lambda 把那个写口接到源管理屏上。
     */
    setMaskMode: ((String) -> Boolean)? = null,
    /** 写「屏蔽 AI 图」。同一个理由，同一个入口。 */
    setBlockAi: ((Boolean) -> Unit)? = null,
    /** 读源清单（key + 显示名 + 是否启用）。 */
    readSources: (() -> List<com.venera.desktop.sourcemanage.DesktopSourceRowUi>)? = null,
    /** 写某颗源的启停。 */
    writeSourceEnabled: ((String, Boolean) -> Unit)? = null,
    /** 读收藏（返回 Pair：列表, 是否已读回）。 */
    readFavorites: (suspend () -> Pair<List<com.venera.compose.data.db.FavoriteRecord>, Boolean>)? = null,
    /** 移出收藏。 */
    removeFavorite: (suspend (String) -> Unit)? = null,
    /**
     * 这一本收没收过（详情页那枚钮的三态里 `null` 与 `false` 就靠它分开）。
     *
     * 为什么不用 `FavoriteDao.toggleFavorite`：那颗读的是 `_favoritesFlow.value` 判"存不存在"，
     * 而那颗流是 `scope.launch` 发起即返回的（与 `readFavorites` 踩过同一个坑）⇒
     * 冷启动头几按下它会把"还没读回来"当成"没收过"，于是**再加一遍**而不是取消。
     * 这里由壳拿着真查库的结果显式选 `add` / `remove`，那颗坑就绕过去了。
     */
    isFavoriteOf: (suspend (String) -> Boolean)? = null,
    /** 收进收藏。`null` 而 `isFavoriteOf` 有值 = 只能看不能改 ⇒ 屏上那枚钮**不摆**。 */
    addFavorite: (suspend (com.venera.compose.data.db.FavoriteRecord) -> Unit)? = null,
    /** 搜索：给关键词，回结果。 */
    searchComics: (suspend (String) -> List<com.venera.engineprobe.EngineSession.ComicCard>)? = null,
    /**
     * 漫画章节下载那一格的**根目录**（装配点给的是 `数据根/downloads`）。
     *
     * 为什么可空：这一路的写口只有 `DesktopComicDownloadQueue.enqueue(root, …)` 一颗，
     * 而它必须要绝对根。拿不到根 ⇒ 详情那一屏的「下载这一话」**整颗不摆**、
     * 那一圈泵也**不起** —— 与「没有写口就没有那枚钮」同一条口径（评审 R6 那一族）。
     *
     * 泵为什么挂在这颗壳上而不是详情那一屏：详情那一屏的 runner 从前挂在
     * `rememberCoroutineScope()` 上 ⇒ **离开那一屏就死**，跑到一半那一话既不完成也不留痕。
     * 挂在这一层（与窗口同寿命的那颗 `engine` 同一个作用域）才是"换屏不断"。
     */
    downloadsRoot: java.io.File? = null,
) {
    // 墙上的卡片在**壳里**拉，不在 pane 里拉：pane 只吃 `List<DesktopWallCardModel>`
    // （`DesktopUiIsolationTest` ② 那条"UI 只吃形参"的形状）。
    //
    // 为什么用 `produceState` 而不是 `LaunchedEffect` + `mutableStateOf`：
    // 前者的初值必须现在给，于是"还没拉回来"这件事在类型上就成立（空列表），
    // 而后者很容易写成"先渲染占位再不等它"，那是本仓记过的那类假完成。
    //
    // 依赖键只有 `ports`：换窗口宽不该重发网络请求（列数是纯布局，与数据无关）。
    // 首页那三格数字：直接读真实的 venera_core.db（DesktopHomeStatsSource 说明为什么
    // 不复用 HomeViewModel）。库没建/没表时它返回带成因的空数据，屏上照原话说。
    // 历史那一格：与统计同理，冷启动第一帧是 Loading（不是"没有历史"）。


    // ── 二级页路由（2026-10-21）──
    //
    // 只有两层，所以不引导航库：一个 sealed 接口 + 一个 `remember` 就够。
    // 引导航库会带来一堆这一批用不上的约定（深链、回栈、进程恢复），
    // 而真正要的只是"从详情点一章进阅读器、按返回回来"。
    var route by remember { mutableStateOf<DesktopRoute>(DesktopRoute.Tab) }


    // 详情/阅读器要的数据：当前这一本 + 这一话。
    var detailInfo by remember { mutableStateOf<com.venera.engineprobe.ComicInfo?>(null) }
    var detailNote by remember { mutableStateOf<String?>(null) }
    // 详情页那枚收藏钮的三态：`null` = 还没查回来（**不许**当"没收过"渲染）。
    var detailFavorited by remember { mutableStateOf<Boolean?>(null) }
    // 按下去到写落之间那一档：屏上念「正在写…」并禁用，避免连点把两条 INSERT 打进同一格。
    var detailFavoriteBusy by remember { mutableStateOf(false) }
    // 写完一次就 bump 一下，让下面那颗 effect 重查真值（乐观反映会被它校正回来）。
    var favoriteTick by remember { mutableIntStateOf(0) }
    var readerPages by remember {
        mutableStateOf<List<com.venera.desktop.gallery.data.DesktopComicPage>>(emptyList())
    }
    var readerNote by remember { mutableStateOf<String?>(null) }
    var readerPage by remember { mutableIntStateOf(0) }


    // ── 探索屏的状态（2026-10-21）──
    var exploreSections by remember { mutableStateOf<List<com.venera.engineprobe.EngineSession.Section>>(emptyList()) }
    var exploreNote by remember { mutableStateOf<String?>(null) }
    // 重取一次的触发器：`reloadTick` 变一次，下面那个 LaunchedEffect 就重跑一次。
    var exploreTick by remember { mutableIntStateOf(0) }
    // 探索并进搜索分段（2026-11-03，与安卓同一形态）：底栏/侧栏四格，探索内容住搜索格的分段里。
    var desktopExploreSegment by remember { mutableStateOf(false) }


    // ── 收藏 / 搜索 / 源管理 的状态（2026-10-21）──
    var favorites by remember { mutableStateOf<List<com.venera.compose.data.db.FavoriteRecord>>(emptyList()) }
    var favoritesLoaded by remember { mutableStateOf(false) }
    var favoritesNote by remember { mutableStateOf<String?>(null) }


    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<com.venera.engineprobe.EngineSession.ComicCard>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchNote by remember { mutableStateOf<String?>(null) }
    var searchTick by remember { mutableIntStateOf(0) }


    var sourceRows by remember { mutableStateOf<List<com.venera.desktop.sourcemanage.DesktopSourceRowUi>>(emptyList()) }
    var sourceNote by remember { mutableStateOf<String?>(null) }
    var sourceTick by remember { mutableIntStateOf(0) }

    // ── 「本机已下载」那一屏的状态（第一百一十五批）──
    //
    // 那一棵树数回来的结果。`null` = **还没数回来**（屏上念另一句，不念"本机没有"）。
    var downloadsListing by remember { mutableStateOf<DesktopComicDownloadListing?>(null) }
    // 上一次按下的果（补齐排进队 / 删完了 / 删不动）。
    var downloadsNote by remember { mutableStateOf<String?>(null) }
    // 删除是**同步**改盘的（返回就是删完了）⇒ 按完立刻自己重读一次盘，不等队列那张表。
    var downloadsTick by remember { mutableIntStateOf(0) }
    // 「武装 → 再按才真删」那两步的状态：记的是**哪一行**武装着（身份串取自那颗纯函数）。
    //
    // ⚠️ 它挂在壳里而不是屏里：屏只吃 `armedKey: String?`，于是"第二次按下才真删"那条纪律
    // 能被无头喂数据钉住（屏里有状态就得靠点界面来确认，而本仓不许靠人眼点）。
    var armedDeleteKey by remember { mutableStateOf<String?>(null) }


    // 打码判据：与墙上同一条链（`LocalUxPlatformServices`），不另立一套。
    // 档位判定吃**窗口宽**（不是物理屏宽）：桌面没有"整块屏"这个概念，
    // 而 `WideScreenPolicy` 的注释本来就写着"宽度传窗口宽"。
    val layoutMode = wideScreenLayoutMode(windowWidth)
    // 侧栏是否真的接管导航。桌面恒接管（安卓侧还要看 `LIQUID_GLASS` 那条样式轴，
    // 桌面上没有玻璃底栏，所以那一轴恒为 false ⇒ 侧栏接管）。
    val sideBarActive = layoutMode != WideScreenLayoutMode.Compact
    val effectiveMode = sideBarModeFor(layoutMode, sideBarActive)


    var currentTab by remember { mutableStateOf(VeneraNavTab.HOME) }


    // 用户动作触发的异步落库（移出收藏、写源启停）。
    // 用 `rememberCoroutineScope` 而不是 `LaunchedEffect`：这些是**一次性动作**，
    // 不是"某个状态变了就重跑" —— 用 LaunchedEffect 会让它们跟着重组反复执行。
    val scope = rememberCoroutineScope()

    // ── 漫画章节下载那一圈泵（第一百一十四批：泵接上 UI）──
    //
    // 那一圈泵自批 101 起就写好了，而 `desktopComicStartQueuePump` 在全 main 里**零调用点**
    // —— 也就是"表有人读、没人跑"：详情把那一笔排进去之后，盘上一页都不会多。
    // 今天接上。
    //
    // 为什么用自持的 `CoroutineScope(SupervisorJob() + Dispatchers.IO)` 而不是 `scope`：
    // `rememberCoroutineScope()` 的那颗作用域跟着**组合**走 —— 详情那一屏从前就是这么挂的，
    // 于是离开那一屏泵就死（批 101 类注释记的那条）。这里要的是"换屏不断、退窗才停"。
    //
    // ⚠️ `onDispose` 只许掐这颗作用域，**不许关 `engine`**：那颗常驻引擎的生命周期在装配点
    //（`VeneraDesktop.kt` 的 `DisposableEffect(uiEngine)`），壳里关一次就会把探索/详情/阅读一起带走。
    // 泵被掐掉那一档由 runner 自己的 `finally` 把「正在跑」交回「待跑」，下次起窗接着排。
    val queueScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    DisposableEffect(engine, downloadsRoot) {
        val root = downloadsRoot
        if (root != null) desktopComicStartQueuePump(queueScope, engine, root)
        onDispose { queueScope.cancel() }
    }
    // 那张表与"读回来过没有"由壳 collect 下来递下去（屏自己不碰那颗进程级单例）。
    val queueTasks by DesktopComicDownloadQueue.tasks.collectAsState()
    val queuePrimed by DesktopComicDownloadQueue.primed.collectAsState()
    var queueNote by remember { mutableStateOf<String?>(null) }


    val ux = com.venera.compose.components.LocalUxPlatformServices.current


    val homeHistory by produceState(initialValue = DesktopHomeHistorySnapshot.Loading, ports) {
        value = DesktopHomeHistorySource.read()
    }


    val homeStats by produceState(initialValue = DesktopHomeStats.Empty, ports) {
        value = DesktopHomeStatsSource.read()
    }


    // ── 详情取数（2026-10-21）──
    //
    // 键取 `route`：进详情拉一次、换一本再拉一次，回阅读器不重拉。
    // 失败要落在 `detailNote` 上屏 —— 空白详情页看起来像"这本没内容"。
    LaunchedEffect(route) {
        val r = route
        if (r !is DesktopRoute.Detail) return@LaunchedEffect
        detailInfo = null
        detailNote = null
        val e = engine
        if (e == null) {
            detailNote = "引擎没起来：取不到这一本的章节。"
            return@LaunchedEffect
        }
        detailNote = desktopEngineCall(e) {
            e.load(r.sourceKey)
            e.details(r.comicId)
        }.fold(
            onSuccess = { info ->
                detailInfo = info
                null
            },
            onFailure = { t ->
                "读不到这一本：${t.javaClass.simpleName} " + (t.message?.take(160) ?: "")
            },
        )
    }


    // ── 详情页那枚收藏钮的三态（2026-10-21）──
    //
    // 独立一颗 effect 而不并进上面那颗：那颗读的是**源站**（要过引擎那把锁），
    // 这一颗读的是**本地库**（不占锁）。合在一起会让"查收藏"排在"取章节"后面，
    // 而源站慢的时候屏上就一直念"正在读收藏…" —— 那是自己造的等待。
    //
    // 键里带 `favoriteTick`：写完一次要重查真值，乐观反映才有东西校正它。
    LaunchedEffect(route, favoriteTick) {
        val r = route
        if (r !is DesktopRoute.Detail) return@LaunchedEffect
        val ask = isFavoriteOf
        if (ask == null) {
            detailFavorited = null
            return@LaunchedEffect
        }
        detailFavorited = null
        val got = withContext(Dispatchers.IO) { runCatching { ask(r.comicId) } }
        // 查失败念成"读不回来"而不是"没收过"：后者会让用户按下去写一条重复的。
        detailFavorited = got.getOrNull()
    }


    // ── 阅读器取页表（2026-10-21；第一百一十六批改成本机优先）──
    //
    // **先数本机那一棵树，再决定要不要问源站**。三条路（`desktopComicOfflineShouldAskEngine` 的三档）：
    //  - 本机整话齐了 ⇒ **一趟都不问源站**（引擎根本不起来也行）—— 断网翻得开就是这一档；
    //  - 本机有而缺几页 ⇒ 问引擎拿页键（缺页的页键只有源站认得），而每一页都带 `localDir`
    //    ⇒ 盘上有的那几页直接吃盘，缺的才去取；
    //  - 本机一颗都没有 ⇒ 与从前同（全问引擎）。
    //
    // 键里仍只有 `route`：读盘那一趟**只在进那一屏时问一次**，不进逐页循环
    // （第一百一十三批刚为"每刷一页就全量读一遍盘是空转"改掉一次同类空转，这里不添回来）。
    LaunchedEffect(route) {
        val r = route
        if (r !is DesktopRoute.Reader) return@LaunchedEffect
        val root = downloadsRoot
        // 读盘（含每页头部那判）在 IO 线程上，且与"问引擎"分开：塌了不影响另一路。
        val local: DesktopComicLocalChapter? =
            if (root == null) null else withContext(Dispatchers.IO) {
                runCatching {
                    // 源与号先归一到盘上那一形（净化）再去认话，见那颗件自己的 KDoc。
                    desktopComicOfflineLocalChapter(root, r.sourceKey, r.comicId, r.episode.id)
                }.getOrNull()
            }
        val shouldAsk = desktopComicOfflineShouldAskEngine(local)
        val e = engine

        // 源站那一趟的三档各是一条分支（`DesktopComicSourceOutcome`）。
        // ⚠️ 「想问而引擎没起来」**不清掉页表**：盘上有的那几页照样翻开，那一档由屏顶那句念成因。
        //    从前这里是 `e == null` 就整屏失败 + `readerPages = emptyList()`（24 MB 一并清空）。
        var imageKeys: List<String> = emptyList()
        val outcome: DesktopComicSourceOutcome = when {
            // 本机整话已齐 ⇒ 一颗源站都不问（引擎没起来也无所谓，断网翻得开就是这一档）。
            !shouldAsk -> DesktopComicSourceOutcome.NotNeeded
            e == null -> DesktopComicSourceOutcome.Unreachable("引擎没起来")
            else -> {
                val got = desktopEngineCall(e) {
                    e.load(r.sourceKey)
                    // `pages` 交回的是**页键**（源站自己的标识），不是能直接 GET 的地址。
                    //
                    // ⚠️ 必须包成 `DesktopComicPage` **对象**，不能拼字符串。
                    // `DesktopComicPageFetcher.Factory : Fetcher.Factory<DesktopComicPage>`
                    // 只认这个类型；字符串会被 Coil 当普通 URL 交给网络 fetcher，而它不是
                    // http(s) ⇒ 直接失败 ⇒ 阅读器一片黑（端到端实测过）。
                    // 那个 `"comic|…"` 字符串是 `desktopComicPageCacheKey` 的**缓存键**，
                    // 不是 model 的格式 —— 这两件事此前被本喵混成了一件。
                    //
                    // 页对象现在由 `desktopComicReaderPages` 那颗**纯决策件**造（它同时要喂本机那一格，
                    // 无头才钉得住"哪一页该吃盘"）；这里只交回页键。
                    e.pages(r.comicId, r.episode)
                }
                got.fold(
                    onSuccess = { keys ->
                        imageKeys = keys
                        DesktopComicSourceOutcome.Delivered(keys.size)
                    },
                    onFailure = { t ->
                        DesktopComicSourceOutcome.Unreachable(
                            "取页失败：${t.javaClass.simpleName} " + (t.message?.take(160) ?: ""),
                        )
                    },
                )
            }
        }

        val served = desktopComicReaderPages(
            sourceKey = r.sourceKey,
            comicId = r.comicId,
            episodeId = r.episode.id,
            imageKeys = imageKeys,
            localDir = local?.dir,
            localPages = local?.writtenPages ?: 0,
            // 纯本机那一趟里引擎可以没有（`null`），那时页表里每一页都带 `localDir` ⇒ 仍算可取。
            engine = e,
        )
        readerPages = served
        readerNote = desktopComicOfflineReadingNote(
            local = local,
            outcome = outcome,
            servedPages = served.size,
        )
        println(
            desktopComicOfflineReadingReading(
                episodeId = r.episode.id,
                local = local,
                outcome = outcome,
                servedPages = served.size,
                pagesWithLocalDir = served.count { it.localDir != null },
            ),
        )
    }


    // ── 收藏取数（2026-10-21）──
    //
    // ⚠️ `FavoriteDao` 与 `HistoryDao` 是**同一个坑**：`refresh()` 是
    // `scope.launch { _favoritesFlow.value = … }`，发起即返回，而初值是 `emptyList()`。
    // 紧接 `.value` 会读到初值 ⇒ 屏上显示"还没有收藏"，而库里其实有。
    // 所以用 `first { it.isNotEmpty() }` 等一个**满足条件的值**，超时后再看一次 `.value`。
    LaunchedEffect(currentTab, favoritesLoaded) {
        if (currentTab != VeneraNavTab.FAVORITES) return@LaunchedEffect
        val read = readFavorites ?: return@LaunchedEffect
        val got = withContext(Dispatchers.IO) { runCatching { read() } }
        got.fold(
            onSuccess = { (list, loaded) ->
                favorites = list
                favoritesLoaded = loaded
                favoritesNote = null
            },
            onFailure = { t ->
                favoritesLoaded = true
                favoritesNote = "读不到本地收藏：${t.javaClass.simpleName} " + (t.message?.take(160) ?: "")
            },
        )
    }


    // ── 搜索（2026-10-21）──
    //
    // 键取 `searchTick`：回车/点按钮时 `searchTick++`，这里才真发一次。
    // 不用 `searchQuery` 做键 —— 那样每敲一个字都会搜一次。
    LaunchedEffect(searchTick) {
        if (searchTick == 0) return@LaunchedEffect
        val doSearch = searchComics ?: return@LaunchedEffect
        searching = true
        searchNote = null
        val got = withContext(Dispatchers.IO) { runCatching { doSearch(searchQuery) } }
        searching = false
        got.fold(
            onSuccess = { searchResults = it },
            onFailure = { t ->
                searchResults = emptyList()
                searchNote = "搜索失败：${t.javaClass.simpleName} " + (t.message?.take(160) ?: "")
            },
        )
    }


    // ── 源清单（2026-10-21）──
    //
    // ⚠️ 键里必须有 `route`：入口接上之后用户是**从探索屏切进这一路由**的，
    // 而 `currentTab` 那一路没变（还停在 EXPLORE）⇒ 光靠 `currentTab + sourceTick`
    // 这一趟不会重跑，屏上永远是"一个源都没有"。与 B2 那颗（写历史的 effect
    // 键里缺 `readerPages`）同一个形状：**effect 读了哪一路状态，键里就得有哪一路**。
    LaunchedEffect(currentTab, route, sourceTick) {
        if (currentTab != VeneraNavTab.FAVORITES && route !is DesktopRoute.SourceManage) return@LaunchedEffect
        val read = readSources ?: return@LaunchedEffect
        runCatching { read() }.fold(
            onSuccess = { sourceRows = it; sourceNote = null },
            onFailure = { t ->
                sourceRows = emptyList()
                sourceNote = "读不到源清单：${t.javaClass.simpleName} " + (t.message?.take(160) ?: "")
            },
        )
    }


    // ── 「本机已下载」那一屏的读盘（第一百一十五批）──
    //
    // 键里有三样，各管一件事（缺一件就有一档不刷）：
    //  - `route`：进到那一屏才数一趟（不进那一屏不去读盘，与源清单那颗 effect 同一口径）；
    //  - `downloadsTick`：**删除**那一路是同步改盘的（返回就是删完了），按完立刻自己重读一次；
    //  - `desktopComicShelfRerollKey(queueTasks)`（第一百一十三批那一枚键）：**补齐**那一路是异步的 ——
    //    按下只是排进队列，那一刻盘上一颗文件都没多，只有那一笔落到终局（完/败/停）才算真该重读。
    //    逐页刷进度**不换键**：每刷一页就把整棵树重数一遍是空转（那一枚键自己就是这么设计的）。
    //
    // ⚠️ 读盘整趟过 `runCatching`：那一棵树里什么都有（手工放的书、半截的清单、导进来的裸目录），
    //    塌了要念「数不出来 + 成因」，而不是念「目录建好了，里面还一条都没有」。
    LaunchedEffect(route, downloadsTick, desktopComicShelfRerollKey(queueTasks)) {
        if (route !is DesktopRoute.Downloads) return@LaunchedEffect
        val root = downloadsRoot
        if (root == null) {
            downloadsListing = DesktopComicDownloadListing(
                groups = emptyList(), skipped = 0, rootExists = false,
                error = "下载根目录拿不到 ⇒ 那棵树数不着",
            )
            return@LaunchedEffect
        }
        val listing = withContext(Dispatchers.IO) {
            runCatching {
                val (chapters, skipped) = desktopComicDownloadedScan(root)
                DesktopComicDownloadListing(
                    groups = desktopComicChaptersByComic(chapters),
                    skipped = skipped,
                    rootExists = root.isDirectory,
                )
            }.getOrElse { t ->
                DesktopComicDownloadListing(
                    groups = emptyList(), skipped = 0, rootExists = root.isDirectory,
                    error = "${t.javaClass.simpleName} ${t.message?.take(160) ?: ""}",
                )
            }
        }
        downloadsListing = listing
        // 取证读数（只进 stdout，不进界面）：那六格与表头那句出自**同一次**读盘，不另走一趟。
        if (listing.error == null) println(desktopComicDownloadedReading(listing))
    }


    /**
     * 「本机已下载」那一行的删除那两步（第一百一十五批接上门）。
     *
     * 第一次按只**武装**（盘上一颗文件都不动），第二次按才真删 —— 而"该不该真删"和"武装记谁"
     * 都不在这里重写，问的是 `DesktopComicDownloadsDelete.kt` 里那两颗纯函数。
     * 删完立刻 `downloadsTick++`：删除是同步改盘的，那一行不该等队列那张表才改口。
     */
    fun onDownloadsDelete(chapter: com.venera.desktop.gallery.data.DesktopComicDownloadedChapter) {
        val root = downloadsRoot
        if (root == null) {
            downloadsNote = "下载根目录拿不到 ⇒ 这一话删不了"
            return
        }
        val key = desktopComicDeleteRowKey(chapter)
        if (!desktopComicDeleteShouldFire(armedDeleteKey, key)) {
            armedDeleteKey = desktopComicDeleteNextKey(armedDeleteKey, key)
            return
        }
        armedDeleteKey = null
        scope.launch {
            val outcome = desktopComicDeleteChapter(root, chapter)
            downloadsNote = desktopComicDeleteScreenLine(chapter, outcome)
            downloadsTick++
        }
    }


    // ── 写阅读历史（2026-10-21）──
    //
    // **这是首页统计/历史的唯一上游。** 没有它，首页那三格与历史卡永远是空的
    // （此前那些数字是手工插库插进去的，不是读出来的）。
    //
    // `distinctUntilChanged` 不能省：滚动时 `firstVisibleItemIndex` 会连续变，
    // 不去重就是每帧一次 INSERT，把库打爆。
    LaunchedEffect(route, readerPage, readerPages.size) {
        val r = route
        if (r !is DesktopRoute.Reader) return@LaunchedEffect
        val write = writeHistory ?: return@LaunchedEffect
        if (readerPages.isEmpty()) return@LaunchedEffect
        val progress = com.venera.desktop.gallery.data.DesktopReadingProgress(
            comicId = r.comicId,
            title = r.title,
            sourceKey = r.sourceKey,
            chapterTitle = r.episode.name,
            chapterIndex = detailInfo?.episodes?.indexOfFirst { it.id == r.episode.id } ?: 0,
            pageIndex = readerPage,
            totalPages = readerPages.size,
            coverUrl = r.cover,
        )
        withContext(Dispatchers.IO) {
            com.venera.desktop.gallery.data.desktopWriteReadingHistory(
                progress = progress,
                nowMillis = System.currentTimeMillis(),
                save = write,
            )
        }
    }


    val cards by produceState(initialValue = emptyList(), ports) {
        val configured = com.venera.desktop.gallery.ui.desktopGelbooruConfigured(ports)
        val loaded = runCatching {
            com.venera.desktop.gallery.ui.desktopWallFirstPage(
                ports = ports,
                gelbooruConfigured = configured,
                // 守卫必须真的递进来：不传 ⇒ 取数层走 `guard == null` 分支 ⇒ 一张图都不遮
                // （安全评审 B）。接口改完不等于修好，这一步才是修好。
                guard = wallGuardOf(),
                maskMode = maskModeOf(),
                blockAi = wallBlockAiOf(),
            )
        }.onFailure {
            // 取数整条路塌了要念出来：空墙静默是最难查的那种失败。
            println("D_取数 首屏失败 ${it::class.java.simpleName}: ${it.message?.take(200)}")
        }.getOrDefault(emptyList())
        println("D_取数 上屏=${loaded.size} 张")
        value = loaded
    }


    // ── 探索取数（2026-10-21）──
    //
    // 键取 `currentTab + exploreTick`：切到探索那一格才取、点「重新取」才重取。
    // `engine.load(sourceKey)` 是必须的前置 —— `explore()` 内部直接读
    // `ComicSource.sources[key]`，没 load 过就是 undefined。
    LaunchedEffect(desktopExploreSegment, exploreTick) {
        if (!desktopExploreSegment) return@LaunchedEffect
        val e = engine
        if (e == null) {
            exploreNote = "引擎没起来：取不到探索页。"
            return@LaunchedEffect
        }
        exploreNote = null
        // 过 `desktopEngineCall`：这颗引擎同时被详情/阅读器/搜索用着，
        // 裸调会让两趟 `load()` 交错（实测：`装载后取不到 source key`）。
        val got = desktopEngineCall(e) {
            e.load(DESKTOP_EXPLORE_SOURCE)
            e.explore(1)
        }
        got.fold(
            onSuccess = { page ->
                exploreSections = page.sections.ifEmpty {
                    // 有的源 `explore()` 回的是单个 comics 列表（不带分区）。
                    // 那种情况包成一个无名分区，而不是让屏空着。
                    if (page.comics.isEmpty()) emptyList()
                    else listOf(com.venera.engineprobe.EngineSession.Section(title = "", comics = page.comics))
                }
                exploreNote = page.error?.takeIf { it.isNotBlank() }
                    ?: if (exploreSections.isEmpty()) "这一源没回探索内容。" else null
            },
            onFailure = { t ->
                exploreSections = emptyList()
                exploreNote = "取探索页失败：${t.javaClass.simpleName} " + (t.message?.take(160) ?: "")
            },
        )
    }






    CompositionLocalProvider(
        com.venera.compose.components.LocalWindowWidth provides windowWidth,
        // 深色那一条：桌面侧恒为深色。
        //
        // 为什么不去问 `MiuixTheme.colorSchemeMode`：那枚举的公开取值在 desktop 变体上
        // 没有稳定的读法（`ColorSchemeMode` 只是个枚举壳，取值名随 Miuix 小版本变化），
        // 而我们真正需要的语义是"这一窗用深色"——桌面壳本来就只提供深色那一档
        // （旧 Fluent 那条线的 `FluentTheme(colors = DesktopTheme.colors())` 也是深色）。
        // 所以这里显式给 true，而不是从主题反推 —— 反推会把"主题怎么实现"这件事
        // 绑进集成方，而那正是两端解耦要避免的。
        //
        // 将来要接明暗切换时，正确的位置是窗口级偏好（`JsonKeyValueStore` 那一侧），
        // 不是在这里猜 Miuix 的枚举。
        com.venera.compose.ui.tokens.LocalVeneraDarkTheme provides true,
        // ── 内容守卫（2026-10-21 修 R3）──
        //
        // `LocalUxPlatformServices` 此前**从未 provide**，桌面一直吃
        // `staticCompositionLocalOf { NoOpUxPlatformServices }`，它的
        // `coverMaskStateOf(...)` 恒返回 `"VISIBLE"`。
        //
        // 后果不是样式问题：共享零件 `RecommendCarouselItem` / `HistoryCardHome` 里那句
        // `maskState == "VISIBLE"` 恒真 ⇒ **封面全不打码**；而且 `coverSharedElement(allowFly=…)`
        // 会允许本该遮住的项也做共享元素飞行（安卓侧明确禁止）。
        //
        // 两个 lambda 由装配点递进来（`VeneraDesktop.kt` 手上有 `DesktopGalleryHandle`），
        // 不在 UI 里手伸进去取静态口 —— `DesktopUiIsolationTest` ② 那条。
        com.venera.compose.components.LocalUxPlatformServices provides
            remember(maskModeOf, blockedByUserOf) {
                DesktopUxPlatformServices(
                    maskMode = maskModeOf,
                    blockedByUser = blockedByUserOf,
                )
            },
    ) {
        MiuixTheme {
            VeneraSideBar(
                currentTab = currentTab,
                // 切 tab 要**同时退出二级页**：那一路由 (`when (route)`) 走在底栏分派**之前**
                // （`DesktopRoute.Tab -> when (currentTab)` 在最下面那一支），所以只改 `currentTab`
                // 而不清 `route` 的话，停在详情/阅读器/源管理时点侧栏任何一格，
                // 屏上**仍是那一本漫画**，而侧栏高亮已经跳过去了 —— 看着像侧栏坏了。
                //
                // ⚠️ 这一格是既有缺口，不是本轮弄出来的（本轮只是让"停在二级页"更容易撞上：
                // 源管理入口接上后，从探索进源管理再点侧栏就撞到了）。修法取"切 tab 就回 tab 那一层"，
                // 不做"每个 tab 记住自己的二级页"—— 那要引入回栈，而清单 §路由 那条口径明写着
                // "只有两层，不引导航库"。
                onTabSelected = {
                    // 「下载与本地」格（2026-11-03）：内容由 DesktopRoute.Files（合并页）承载，
                    // 不落 currentTab —— 这条分支永远不会渲染，when 完备性各给一个空块。
                    if (it == VeneraNavTab.DOWNLOADS) {
                        route = DesktopRoute.Downloads
                    } else {
                        currentTab = it
                        route = DesktopRoute.Tab
                    }
                },
                mode = effectiveMode,
                modifier = modifier,
            ) {
                // ⚠️ 2026-10-20 更正：五个 tab 各对**安卓自己的那一屏**，不是都摆同一面墙。
                //
                // 上一版这里写的是「HOME / GALLERY / FAVORITES 都画图片墙，else 也画图片墙」——
                // 那让桌面看起来"复用"了，实际是把**画廊**那一屏错放到了首页，并且
                // 搜索/探索两屏根本不存在（else 悄悄兜回首页）。用户当场就看出来了：
                // 「你这个首页怎么和安卓端的完全不一样？」—— 安卓首页是漫画推荐页
                // （阅读统计 / 可能你感兴趣 / 历史，`feature/HomeScreen.kt` 1124 行），
                // 图片墙是 GALLERY 那一屏（`gallery/ui/GalleryScreen.kt` 3453 行）。
                //
                // 所以现在**只有画廊那一格是真的**（真取数、真卡片）；其余四格如实报
                // 「这一屏还没接」而不是拿别的屏顶上 —— 假装比空白更糟：用户会以为
                // 那就是他的首页，然后发现内容全不对。
                // ── 二级页优先（2026-10-21）──
                //
                // 先看 `route`，是 `Tab` 才走底栏分派。这样详情/阅读器能全屏铺开、
                // 不被侧栏挤压；返回时 `route = Tab`，底栏那一格还在原处（`currentTab` 没动）。
                when (val r = route) {
                    DesktopRoute.SourceManage -> DesktopSourceManageScreen(
                        mode = layoutMode,
                        rows = sourceRows,
                        maskMode = maskModeOf(),
                        blockAi = wallBlockAiOf(),
                        note = sourceNote,
                        onToggleSource = { key, enabled ->
                            // 先本地立刻反映，再异步落盘（同「移出收藏」那条口径）。
                            sourceRows = sourceRows.map {
                                if (it.key == key) it.copy(enabled = enabled) else it
                            }
                            runCatching { writeSourceEnabled?.invoke(key, enabled) }
                                .onFailure { sourceNote = "写不进去：${it.javaClass.simpleName}" }
                        },
                        onMaskModeChange = { mode ->
                            val ok = setMaskMode?.invoke(mode) ?: false
                            sourceNote = if (ok) null else "改分级档失败：写盘被拒（档位只接受 OFF/BLUR/HIDE）"
                        },
                        onBlockAiChange = { on -> runCatching { setBlockAi?.invoke(on) } },
                        onBack = { route = DesktopRoute.Tab },
                    )


                    is DesktopRoute.Detail -> DesktopComicDetailScreen(
                        mode = layoutMode,
                        title = r.title,
                        info = detailInfo,
                        note = detailNote,
                        masked = detailInfo != null && ux.coverMaskStateOf(
                            sourceKey = r.sourceKey,
                            title = r.title,
                            author = "",
                            tags = emptyList(),
                            comicId = r.comicId,
                        ) != "VISIBLE",
                        favorited = detailFavorited,
                        favoriteBusy = detailFavoriteBusy,
                        onToggleFavorite = if (addFavorite == null || removeFavorite == null) null else {
                            {
                                val wantAdd = detailFavorited != true
                                detailFavoriteBusy = true
                                scope.launch {
                                    val got = runCatching {
                                        if (wantAdd) {
                                            addFavorite.invoke(
                                                com.venera.compose.data.db.FavoriteRecord(
                                                    comicId = r.comicId,
                                                    title = r.title,
                                                    author = "",
                                                    coverUrl = detailInfo?.cover?.ifBlank { r.cover } ?: r.cover,
                                                    // 写 key 而不是显示名：与桌面写历史那一行同一条口径，
                                                    // 也与对岸 `HomeViewModel.toLegacyRecord`（`sourceName = sourceKey`）一致。
                                                    sourceName = r.sourceKey,
                                                ),
                                            )
                                        } else {
                                            removeFavorite.invoke(r.comicId)
                                        }
                                    }
                                    detailFavoriteBusy = false
                                    // 写失败要把成因念出来；那一档**不**手改，交给下面回查真值
                                    // （屏上"已收藏"而库里没这条，比"收藏没写上"更难查）。
                                    if (got.isFailure) {
                                        val t = got.exceptionOrNull()
                                        detailNote = "收藏没写上：${t?.javaClass?.simpleName} " + (t?.message?.take(160) ?: "")
                                    }
                                    favoriteTick++
                                }
                            }
                        },
                        onBack = { route = DesktopRoute.Tab },
                        onOpenEpisode = { ep ->
                            readerPage = 0
                            readerPages = emptyList()
                            readerNote = "正在取这一话的页…"
                            route = DesktopRoute.Reader(
                                comicId = r.comicId,
                                title = r.title,
                                cover = r.cover,
                                sourceKey = r.sourceKey,
                                episode = ep,
                            )
                        },
                        // ── 下载那一格（第一百一十四批）──
                        //
                        // 没有根目录 ⇒ 递 `null` ⇒ 那一枚把手**整颗不摆**（不是摆了按不动）。
                        onDownloadEpisode = if (downloadsRoot == null) null else { ep ->
                            val order = detailInfo?.episodes?.indexOfFirst { it.id == ep.id } ?: -1
                            if (order < 0) {
                                detailNote = "这一话没在章节表里认出来 ⇒ 没排队（话号取自表里的行号）"
                            } else {
                                DesktopComicDownloadQueue.enqueue(
                                    root = downloadsRoot,
                                    sourceKey = r.sourceKey,
                                    comicId = r.comicId,
                                    comicTitle = r.title,
                                    comicCover = detailInfo?.cover?.ifBlank { r.cover } ?: r.cover,
                                    chapterId = ep.id,
                                    chapterTitle = ep.name,
                                    chapterOrder = order,
                                    placeholder = ep.placeholder,
                                )
                                detailNote = null
                            }
                        },
                        // 那一行下面念的是**这一话自己**的状态（按行号取，三行不许齐念同一句）。
                        episodeNoteOf = { ep ->
                            val order = detailInfo?.episodes?.indexOfFirst { it.id == ep.id } ?: -1
                            desktopComicEpisodeDownloadNote(queueTasks, r.sourceKey, r.comicId, order)
                        },
                    )


                    is DesktopRoute.Reader -> DesktopReaderScreen(
                        pages = readerPages,
                        episodeLabel = r.episode.name,
                        note = readerNote,
                        masked = ux.coverMaskStateOf(
                            sourceKey = r.sourceKey,
                            title = r.title,
                            author = "",
                            tags = emptyList(),
                            comicId = r.comicId,
                        ) != "VISIBLE",
                        currentPage = readerPage,
                        onPageChange = { readerPage = it },
                        onBack = { route = DesktopRoute.Detail(r.comicId, r.title, r.cover, r.sourceKey) },
                    )


                    DesktopRoute.Queue -> DesktopComicQueueScreen(
                        mode = layoutMode,
                        tasks = queueTasks,
                        primed = queuePrimed,
                        note = queueNote,
                        onStop = { task ->
                            DesktopComicDownloadQueue.requestStop(task.taskId)
                            queueNote = desktopComicQueueStoppedLine(task)
                        },
                        onRemove = { task ->
                            val root = downloadsRoot
                            if (root == null) queueNote = "下载根目录拿不到 ⇒ 这一笔撤不掉"
                            else queueNote = desktopComicQueueRemovedLine(
                                task,
                                DesktopComicDownloadQueue.drop(root, task.taskId),
                            )
                        },
                        onBack = { route = DesktopRoute.Tab },
                    )


                    DesktopRoute.Downloads -> DesktopComicDownloadsScreen(
                        mode = layoutMode,
                        listing = downloadsListing,
                        note = downloadsNote,
                        armedKey = armedDeleteKey,
                        // 「补齐这一话」：只把那一案排进队列，盘上一颗文件都不动 ——
                        // 真正的重读由上面那颗 effect 的终局键负责（按下那一刻重数只会念出同一句「缺 N 页」）。
                        onRepair = { chapter ->
                            val root = downloadsRoot
                            if (root == null) {
                                downloadsNote = "下载根目录拿不到 ⇒ 这一话补不了"
                            } else {
                                downloadsNote = desktopComicRepairChapter(
                                    root,
                                    chapter,
                                    DesktopComicShelfRawIdIndex.of(queueTasks),
                                )
                            }
                        },
                        onDelete = ::onDownloadsDelete,
                        onBack = {
                            // 出了那一屏就把武装那格交回去：停在"已武装"出去转一圈再回来，
                            // 第二按就删的是当初没点过的那一话（批 77 收藏清空同一口径）。
                            armedDeleteKey = null
                            route = DesktopRoute.Tab
                        },
                    )

                    DesktopRoute.Tab -> when (currentTab) {
                    VeneraNavTab.GALLERY -> DesktopGalleryWallPane(layoutMode, cards)
                    // 四屏**逐一点名**，不写 `else ->`：
                    // `else` 会让"谁都没给它写屏"这件事编译期看不出来（上一版就是这么
                    // 让三个 tab 共用一面墙的）。逐一点名之后，将来 `VeneraNavTab` 真加了
                    // 新档，这里是 `when` 不完备的编译错误，而不是又一次静默冒充。
                    // 首页 = 安卓首页那一批零件拼的（统计格 / 源状态 / 分区头），
                    // 不是图片墙。数据口子是 DesktopHomeStats.Empty（还没接 BusinessPorts）。
                    VeneraNavTab.HOME -> DesktopHomePane(layoutMode, homeStats, homeHistory)
                    VeneraNavTab.FAVORITES -> DesktopFavoritesScreen(
                        mode = layoutMode,
                        favorites = favorites,
                        loaded = favoritesLoaded,
                        note = favoritesNote,
                        maskedOf = { fav ->
                            ux.coverMaskStateOf(
                                sourceKey = fav.sourceName,
                                title = fav.title,
                                author = fav.author,
                                tags = fav.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                comicId = fav.comicId,
                            ) != "VISIBLE"
                        },
                        onOpen = { fav ->
                            detailInfo = null
                            detailNote = null
                            route = DesktopRoute.Detail(
                                comicId = fav.comicId,
                                title = fav.title,
                                cover = fav.coverUrl,
                                sourceKey = fav.sourceName,
                            )
                        },
                        onRemove = { fav ->
                            // 移出后本地立刻反映（先把这行摘掉），再异步落库 ——
                            // 等落库再刷会让按钮"按了没反应"。
                            favorites = favorites.filterNot { it.comicId == fav.comicId }
                            scope.launch {
                                runCatching { removeFavorite?.invoke(fav.comicId) }
                            }
                        },
                    )


                    // 「下载与本地」格由 DesktopRoute.Files（合并页）承载：侧栏点下那一下
                    // 已被 onTabSelected 特判改写路由，这里的 when 落不到 DOWNLOADS。
                    VeneraNavTab.DOWNLOADS -> {}
                    VeneraNavTab.SEARCH -> {
                        // 探索并入搜索分段（2026-11-03，与安卓同一形态）：拨到「探索」时
                        // 渲染探索 pane；取数键与门见上方 LaunchedEffect(desktopExploreSegment)。
                        Column {
                            com.venera.compose.components.SearchDiscoverSegmentBar(
                                current = if (desktopExploreSegment) {
                                    com.venera.compose.components.SearchDiscoverSegment.EXPLORE
                                } else {
                                    com.venera.compose.components.SearchDiscoverSegment.SEARCH
                                },
                                onSwitch = { desktopExploreSegment = it == com.venera.compose.components.SearchDiscoverSegment.EXPLORE },
                                modifier = Modifier.padding(horizontal = VeneraTokens.spacing.screenHorizontal, vertical = VeneraTokens.spacing.space4),
                            )
                            if (!desktopExploreSegment) {
                                DesktopSearchScreen(
                                    mode = layoutMode,
                                    sourceKey = DESKTOP_EXPLORE_SOURCE,
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onSearch = { if (searchQuery.isNotBlank()) searchTick++ },
                                    results = searchResults,
                                    searching = searching,
                                    note = searchNote,
                                    maskedOf = { card ->
                                        ux.coverMaskStateOf(
                                            sourceKey = DESKTOP_EXPLORE_SOURCE,
                                            title = card.title,
                                            author = "",
                                            tags = emptyList(),
                                            comicId = card.id,
                                        ) != "VISIBLE"
                                    },
                                    onOpen = { card ->
                                        detailInfo = null
                                        detailNote = null
                                        route = DesktopRoute.Detail(
                                            comicId = card.id,
                                            title = card.title,
                                            cover = card.cover,
                                            sourceKey = DESKTOP_EXPLORE_SOURCE,
                                        )
                                    },
                                )
                            } else {
                                DesktopExploreScreen(
                                    mode = layoutMode,
                                    sourceKey = DESKTOP_EXPLORE_SOURCE,
                                    sections = exploreSections,
                                    note = exploreNote,
                                    maskedOf = { card ->
                                        // 打码判据走共享的那一条（`UxPlatformServices`），不在这里另算。
                                        ux.coverMaskStateOf(
                                            sourceKey = DESKTOP_EXPLORE_SOURCE,
                                            title = card.title,
                                            author = "",
                                            tags = emptyList(),
                                            comicId = card.id,
                                        ) != "VISIBLE"
                                    },
                                    onOpen = { card ->
                                        detailInfo = null
                                        detailNote = null
                                        route = DesktopRoute.Detail(
                                            comicId = card.id,
                                            title = card.title,
                                            cover = card.cover,
                                            sourceKey = DESKTOP_EXPLORE_SOURCE,
                                        )
                                    },
                                    onReload = { exploreTick++ },
                                    // 源管理入口（此前挂在探索格上，随分段一起留在这里）。
                                    onOpenSourceManage = { route = DesktopRoute.SourceManage },
                                    onOpenQueue = { route = DesktopRoute.Queue },
                                    onOpenDownloads = if (downloadsRoot == null) null else {
                                        { route = DesktopRoute.Downloads }
                                    },
                                )
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
 * 画廊那一屏 —— 用**真实的** `VeneraCard` + `VeneraCover` 摆一面自适应墙。
 *
 * 这是今天桌面上**唯一**接了真数据的屏：`GalleryPorts` 拉 yande.re / safebooru
 * 的第一页（gelbooru 要账号，没配就不发车），混排去重后上墙。
 *
 * ⚠️ 它还不是安卓那一颗 `GalleryScreen`（3453 行：日榜三站 · 猜你喜欢 · 搜索 ·
 * 反向搜图 · pixiv 三屏 · 收藏夹 · 屏蔽规则那一整套）。今天它只画"最新那一格"，
 * 也就是那 3453 行里最小的一块 —— 复用的是**同一批零件**（`VeneraCard` /
 * `VeneraCover` / `VeneraTokens` / 列数尺），不是同一屏。
 */
@Composable
private fun DesktopGalleryWallPane(mode: WideScreenLayoutMode, cards: List<DesktopWallCardModel>) {
    DesktopWallScaffold(mode, title = "画廊", cards = cards)
}


/**
 * 一面墙（列数、间距、卡片形态全走同一批 token 与判据）。
 *
 * 提出来不是为了少写几行，是为了让"哪些地方用同一把尺"在代码上成立：
 * 各写一遍的话，下一轮改列数就会只改一处，而那正是本仓记过的那类漂移。
 */
@Composable
private fun DesktopWallScaffold(
    mode: WideScreenLayoutMode,
    title: String,
    cards: List<DesktopWallCardModel>,
) {
    val tokens = VeneraTokens
    val windowWidth = com.venera.compose.components.LocalWindowWidth.current ?: 1280.dp
    val columns = imageWallColumnCount(windowWidth, mode, imageWallHorizontalPadding())
    val maxWidth = contentMaxWidthFor(mode)
    Column(
        modifier = Modifier
            .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
            .fillMaxSize(),
    ) {
        Text(
            text = title,
            fontSize = tokens.type.screenTitle,
            color = tokens.color.textPrimary,
            modifier = Modifier.padding(
                start = tokens.spacing.screenHorizontal,
                top = tokens.spacing.space6,
                bottom = tokens.spacing.space4,
            ),
        )
        if (cards.isEmpty()) {
            // 空态也是共享件：`VeneraEmptyView` 与安卓那几屏用的是同一颗。
            com.venera.compose.components.VeneraEmptyView(
                title = "这一面墙还是空的",
                message = "取数没答上，或者刚起来还在路上 —— 日志里有 `D_取数` 那一行。",
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                bottom = tokens.spacing.space6,
            ),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
            verticalItemSpacing = tokens.spacing.gridGap,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(cards) { card ->
                VeneraCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(card.aspect)) {
                            VeneraCover(
                                url = card.url,
                                contentDescription = card.title,
                                mask = if (card.masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                            )
                        }
                        Spacer(Modifier.height(tokens.spacing.space2))
                        Text(
                            text = card.title,
                            fontSize = tokens.type.body,
                            color = tokens.color.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                horizontal = tokens.spacing.space2,
                                vertical = tokens.spacing.space1,
                            ),
                        )
                    }
                }
            }
        }
    }
}
