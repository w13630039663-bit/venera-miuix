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
import com.venera.desktop.gallery.ui.DesktopWallCardModel
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


    // ── 阅读器取页表（2026-10-21）──
    LaunchedEffect(route) {
        val r = route
        if (r !is DesktopRoute.Reader) return@LaunchedEffect
        val e = engine
        if (e == null) {
            readerNote = "引擎没起来：取不到这一话的页。"
            return@LaunchedEffect
        }
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
                e.pages(r.comicId, r.episode).mapIndexed { i, key ->
                    com.venera.desktop.gallery.data.DesktopComicPage(
                        sourceKey = r.sourceKey,
                        comicId = r.comicId,
                        episodeId = r.episode.id,
                        imageKey = key,
                        page = i,
                        // 非空 ⇒ 这一格能真取到字节（字节由引擎取，含 jm 系解混淆）。
                        engine = e,
                    )
                }
        }
        got.fold(
            onSuccess = { list ->
                readerPages = list
                readerNote = if (list.isEmpty()) "源站这一话回了 0 页。" else null
            },
            onFailure = { t ->
                readerPages = emptyList()
                readerNote = "取页失败：${t.javaClass.simpleName} " + (t.message?.take(160) ?: "")
            },
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
    LaunchedEffect(currentTab, exploreTick) {
        if (currentTab != VeneraNavTab.EXPLORE) return@LaunchedEffect
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
                    currentTab = it
                    route = DesktopRoute.Tab
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


                    VeneraNavTab.SEARCH -> DesktopSearchScreen(
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
                    VeneraNavTab.EXPLORE -> DesktopExploreScreen(
                        mode = layoutMode,
                        sourceKey = DESKTOP_EXPLORE_SOURCE,
                        sections = exploreSections,
                        note = exploreNote,
                        maskedOf = { card ->
                            // 打码判据走共享的那一条（`UxPlatformServices`），不在这里另算。
                            // 探索页拿不到 tags，所以只有源级预设那一道会生效 ——
                            // 这正是本轮修 C 时补上预设表的原因。
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
                        // 源管理那一屏唯一的入口（此前它挂在那条路由上而全仓无人切过去）。
                        // 不顺手清 `detailInfo` / `readerPages`：那两路各自有 `LaunchedEffect(route)`
                        // 在进屏时自己置空重取，这里多清一次只会让人以为"不清就会串数据"。
                        onOpenSourceManage = { route = DesktopRoute.SourceManage },
                    )
                    }
                }
            }
        }
    }
}


/**
 * 「这一屏还没接」—— 如实报，不拿别的屏顶上。
 *
 * ## 为什么需要这一颗（2026-10-20）
 *
 * 上一版把画廊那面图墙摆到了 HOME 位置，`else` 也兜回它 —— 于是五个 tab 里有四个
 * 长得一模一样，而用户一眼看出「你这个首页怎么和安卓端的完全不一样？」。
 * 安卓首页是**漫画推荐页**（`feature/HomeScreen.kt`，1124 行：阅读统计 / 可能你感兴趣 /
 * 历史分区），图片墙是 GALLERY 那一屏（`gallery/ui/GalleryScreen.kt`，3453 行）。
 *
 * 假装比空白更糟：空白会让人问"怎么没有"，假装会让人以为"内容全错了"。
 *
 * ## 为什么不直接渲染安卓那几屏
 *
 * 那四屏都 `viewModel()` 到 `AndroidViewModel(Application)` 上（`GalleryViewModel`、
 * `HomeViewModel`…），要 `BusinessPorts` 那 17 个成员门面 + `ComicSourceManager`
 * + `AndroidViewModelFactory`。那不是"再挂一个 srcDir"，是要给整个漫画业务层做一份
 * 桌面实现 —— 独立一批，不在本批伪装成已完成。
 */
@Composable
private fun DesktopNotWiredPane(tab: VeneraNavTab) {
    val tokens = VeneraTokens
    val androidScreen = when (tab) {
        VeneraNavTab.HOME -> "漫画推荐首页（阅读统计 · 可能你感兴趣 · 历史）"
        VeneraNavTab.FAVORITES -> "漫画收藏"
        VeneraNavTab.SEARCH -> "搜索"
        VeneraNavTab.EXPLORE -> "探索"
        VeneraNavTab.GALLERY -> "画廊" // 到不了：GALLERY 那一格走真墙
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(tokens.spacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = tab.title,
                fontSize = tokens.type.screenTitle,
                color = tokens.color.textPrimary,
            )
            Spacer(Modifier.height(tokens.spacing.space3))
            Text(
                text = "这一屏还没接到桌面：安卓端是「$androidScreen」。",
                fontSize = tokens.type.body,
                color = tokens.color.textSecondary,
                modifier = Modifier.widthIn(max = 520.dp),
            )
            Spacer(Modifier.height(tokens.spacing.space2))
            Text(
                text = "侧栏、断点、卡片、色板都已经和安卓同一份源码；缺的是驱动这几屏的" +
                    "业务门面（`BusinessPorts` 的 17 个成员 + `AndroidViewModel`）的桌面实现。",
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                modifier = Modifier.widthIn(max = 620.dp),
            )
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
