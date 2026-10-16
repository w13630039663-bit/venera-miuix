package com.venera.desktop.gallery.ui

import androidx.compose.runtime.collectAsState
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.data.desktopComicDownloadsRoot
import com.venera.desktop.gallery.data.DesktopComicOfflineBadge
import com.venera.desktop.gallery.data.desktopComicOfflineKey
import com.venera.desktop.gallery.data.desktopComicOfflineHitReading
import com.venera.desktop.gallery.data.DesktopCookieLogin
import com.venera.desktop.gallery.data.DesktopComicReaderPreferences
import com.venera.desktop.gallery.data.DesktopSourceCatalog
import com.venera.desktop.gallery.data.DesktopSourceNames
import com.venera.desktop.gallery.data.DesktopSourceSelection
import com.venera.desktop.gallery.data.DesktopSourceState
import com.venera.engineprobe.DesktopJsHost
import com.venera.engineprobe.EngineAssets
import com.venera.engineprobe.EngineSession
import io.github.composefluent.component.Text
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 漫画域 —— 2026-10-04 从 `VeneraDesktop.kt` 回收的那一批链路在这里成了真页面。
 *
 * ## 回收前它们是什么
 *
 * 原先 `VeneraDesktop()` 里躺着一组状态：`cards` / `covers` / `session` / `open` / `pages` /
 * `pageStatus` / `showFavorites` / `favTree` —— 三个 `LaunchedEffect` 忙着往里写，
 * 而这批东西**一个消费点都没有**：那颗组合的最后一行只有 `DesktopGalleryHome(ports)`。
 *
 * 最坏的一处不是"写了没用"，是**它在真花钱**：`main()` 一开口就去装载源脚本、拉探索页、
 * 然后串行拉 30 张封面图。那是三十来次真实出网，为的是喂一串从不参与布局的变量。
 * 用户看到的是"启动有点慢"，看不到那一串读数是被丢掉的。
 *
 * 现在它们有了消费点，代价是它们**不再在开机时跑** —— 装载挂在域层的引擎持有件
 * [rememberComicEngine] 的 `LaunchedEffect` 上（批 82.7 之前住在 [ComicExplorePane] 里，
 * 于是"站在别的行时载荷进不了详情/阅读那一屏"，见 `docs/rounds/desktop-comic-history-row-2026-10-10.md`），
 * 也就是"切到漫画域才装载"。同一份 IO，换了一个诚实的时机。
 *
 * ## 这一域的两页为什么各自
 *
 * - **探索**：`EngineSession` 的探索页（分区 + 封面墙）+ 点开看前 8 页。
 * - **收藏夹**：[LocalFavoritesManager]，落在本机 SQLite 的那份。
 *
 * ⚠️ 后者与 rail 上「收藏」那一枚**不是同一件事** —— 那边是画廊收藏
 * （[com.venera.compose.gallery.data.GalleryFavorites]，落 `gallery_favorites.json`），
 * 这边是漫画收藏（落 `LocalFavoriteDatabase`）。两边条数不相等是**正常的**，
 * 把两个域的收藏数念成同一个数就是假读数。
 */
/**
 * 漫画域的装配参数。
 *
 * ⚠️ `public` 是**被包名决定的**，不是随便挑的：集成方（`com.venera.desktop.VeneraDesktop`）
 * 在另一个包里，要把这份参数交给 [VeneraDesktopApp] 就必须够得着它。
 * 这一层里其余的器件都是 `internal`（`sibling` 之间互相调用），只有这颗与 [VeneraDesktopApp] 是例外。
 */
data class DesktopComicEngineConfig(
    val assetDir: File,
    val dataDir: File,
    val proxy: String?,
    /** 起始源脚本的 key（就是 `sources/<key>.js` 那颗文件名）。 */
    val sourceKey: String,
    /**
     * 收藏管理器的**懒装载**。做成 lambda 而不是直接一颗 manager，是因为
     * `LocalFavoritesManager.getInstance` 会建两棵库 —— 在 `main()` 里装配等于开机即建，
     * 哪怕用户今天一整天都不点开漫画域。第一次真正要看收藏时才取。
     */
    val favorites: () -> LocalFavoritesManager,
    /**
     * 阅读历史（`comic_history`）那颗件的**懒装载**，与上面同一口径：
     * `HistoryDao.getInstance` 要开库，而"今天翻开过一本"不等于"要看历史"。
     */
    val history: () -> HistoryDao,
    /**
     * 源停用清单那份盘上状态的**懒装载**（批 83）。同一口径：`JsonKeyValueStore` 一建就要
     * 数据目录，而"今天切到漫画域"不等于"要看源"；只有源管理那一屏与选源那一下真碰它。
     */
    val sourceState: () -> DesktopSourceState,
    /**
     * 取证种子那一颗（批 83.1，`--disable=<源键>` 读来的）。**只并进本次装载，不写盘上那份**：
     * `venera_desktop_sources.json` 是用户在这台机器上的状态，取证参数把它改掉等于量具动了被量的东西。
     *
     * 必填、不给默认值（同一颗纪律）：漏传的形状是"`-Pdisable=jm` 递上而屏上照旧"，
     * 那与本批要抓的缺陷长得一模一样，绝不能靠默认值悄悄通过。
     */
    val seedDisabled: Set<String>,
    /**
     * `--download=<漫画号>/<话号>` 那枚取证种子（批 96）。必填、不给默认值：
     * 漏在这一跳上就是"`--download=…` 递上而屏上什么都没发生"，与本批要防的那一类长得一模一样。
     */
    val downloadSeed: Pair<String, String>?,
    /**
     * `--download-cancel-after=<N>` 那枚取证种子（批 100）。必填、不给默认值：
     * 漏在这一跳上就是「参数递上而那一趟照旧跑到尾」，与 `downloadSeed` 那一条纪律同形。
     */
    val downloadCancelAfter: Int?,
    /**
     * `--delete=<漫画号>/<话号>` 那枚取证种子（批 98）。必填、不给默认值：
     * 漏在这一跳上就是「种子递上而盘上一棵都没少」，与 `downloadSeed` 那一条纪律同形。
     */
    val deleteSeed: Pair<String, String>?,
    /** 第一百一十一批：`--repair=` 那一枚种子（把某一话排回队列），与删除同一族、同一屏。 */
    val repairSeed: Pair<String, String>?,
    /**
     * `--cookie=<host>:<名>=<值>` 那几枚取证种子（批 88·下半），原样递给宿主去并罐。
     *
     * 必填、不给默认值（同一颗纪律）：漏传的形状是「`-Pcookie=…` 递上而请求照旧不带凭据」，
     * 与本批要防的"参数打对了而没人接"同形。
     *
     * 为什么在这里仍是**未解析的串**：解析要懂罐的那一形（按请求 host 分桶），而那一形住在
     * `:engine-probe`；桌面重述一遍就等于两处各认一种写法。
     */
    val seedCookies: List<String>,
    /**
     * 阅读那一屏的偏好（第八十七批：模式那一档）。与 [history] / [favorites] 同一族处理 ——
     * 装配参数在集成方手里，界面只吃形参，不许在 UI 里自己取静态口。
     */
    val readerPrefs: DesktopComicReaderPreferences,
)

internal object DesktopComicSectionKey {
    const val EXPLORE = "comic-explore"
    const val FAVORITES = "comic-favorites"
    const val HISTORY = "comic-history"
    const val SOURCES = "comic-sources"
    // 批 97：本机存过的那些整话（盘上那一棵树终于有了一屏数它）。key 追加在末尾而不插进中间：
    // 既有尺子按 rows[2]/rows[3] 点 key（`DesktopComicHistoryScreenTest` ①），插中间会把那两枚指到别的话上去。
    const val DOWNLOADS = "comic-downloads"
    // 第一百零五批：队列里每一笔那一屏。同样**追加在末尾**（同一条理由，且既有尺子现在逐枚点五行的 key）。
    const val QUEUE = "comic-queue"
}

/** 漫画域的两页，次序就是 pane 里自上而下的次序。 */
internal fun desktopComicRows(): List<DesktopGalleryHomeRow> = listOf(
    DesktopGalleryHomeRow(
        key = DesktopComicSectionKey.EXPLORE,
        title = "探索",
        glyph = "探",
        content = DesktopGallerySectionContent.ComicExplore,
        reason = null,
    ),
    DesktopGalleryHomeRow(
        key = DesktopComicSectionKey.FAVORITES,
        title = "收藏夹",
        glyph = "夹",
        content = DesktopGallerySectionContent.ComicFavorites,
        reason = null,
    ),
    // 批 82：阅读那一屏每翻一页就写一笔进 `comic_history`，这一行从今天起点得动了。
    DesktopGalleryHomeRow(
        key = DesktopComicSectionKey.HISTORY,
        title = "阅读历史",
        glyph = "历",
        content = DesktopGallerySectionContent.ComicHistory,
        reason = null,
    ),
    // 批 83：源脚本今天只有命令行那一枚 `-Pkey=` 能挑，屏上看不见有哪几颗 —— 这一行补的就是那一格。
    DesktopGalleryHomeRow(
        key = DesktopComicSectionKey.SOURCES,
        title = "源管理",
        glyph = "源",
        content = DesktopGallerySectionContent.ComicSources,
        reason = null,
    ),
    // 批 97：本机存过的那些整话 —— 写的那一路在批 96 落地，这一行是它缺的读侧。
    // 追加在末尾（不插进「源管理」之前）：既有尺子按 rows[2]/rows[3] 点 key 与题名，插中间会把那两枚指到别处。
    DesktopGalleryHomeRow(
        key = DesktopComicSectionKey.DOWNLOADS,
        title = "本机已下载",
        glyph = "本",
        content = DesktopGallerySectionContent.ComicDownloads,
        reason = null,
    ),
    // 第一百零五批：批 101 让队列能跑，这一行补的是"看得见"—— 每一笔一行、两枚把手（停手 / 撤掉）。
    // 同样追加在末尾：既有尺子逐枚点前五行的 key 与次序（`DesktopComicQueueScreenTest` ① 钉着这件事）。
    DesktopGalleryHomeRow(
        key = DesktopComicSectionKey.QUEUE,
        title = "下载队列",
        glyph = "队",
        content = DesktopGallerySectionContent.ComicQueue,
        reason = null,
    ),
)

@Composable
internal fun DesktopComicBody(
    row: DesktopGalleryHomeRow,
    config: DesktopComicEngineConfig,
    /** 详情那一屏开在哪一本（null = 列表）。由应用根持有，与画廊的 `selected`、画师的 `artistTarget` 同一族。 */
    comicTarget: DesktopComicTarget?,
    /** 阅读那一屏开在哪一话（null = 停在详情或列表）。同上由根持有；它比 [comicTarget] 更高一层。 */
    readTarget: DesktopComicReading?,
    /** 卡上点开（必填：漏传的形状是一枚按不动的封面）。 */
    onOpenComic: (DesktopComicTarget) -> Unit,
    /** 详情那一屏的「← 返回列表」（必填，同上）。 */
    onBackComic: () -> Unit,
    /** 某一话点开（必填：漏传的形状是一行摆着能点的样子而按不动）。 */
    onOpenEpisode: (DesktopComicReading) -> Unit,
    /** 阅读那一屏的「← 返回详情」（必填，同上）。 */
    onBackReader: () -> Unit,
    /**
     * 取证种子顶上的那一档阅读模式（第八十七批；`null` = 没顶过，用盘上那档）。
     * 必填、不给默认值：漏传的形状是「`-PreadMode=rtl` 递上而屏上照旧是纵向」，
     * 与本批要防的那一类"参数打对了而没人接"长得一模一样。
     */
    startReaderMode: DesktopReaderMode?,
) {
    val rendered: Unit = run {
        // 与 `DesktopGalleryHomeBody` / 两颗新 body 里那几处 error 对称：**域路不许串**。
        val content = row.content ?: error("漫画域今天没有未实现的行，「${row.title}」却带了空的 content 位")
        if (content != DesktopGallerySectionContent.ComicExplore &&
            content != DesktopGallerySectionContent.ComicFavorites &&
            content != DesktopGallerySectionContent.ComicHistory &&
            content != DesktopGallerySectionContent.ComicSources &&
            content != DesktopGallerySectionContent.ComicDownloads &&
            content != DesktopGallerySectionContent.ComicQueue
        ) {
            error("「${row.title}」不是漫画域的页 —— 分岔见 VeneraDesktopApp.DesktopDomainBody")
        }
        // 批 82.7：**渲染哪一屏由载荷先决定**，而不是由"用户当时站在哪一行"决定。
        // 从前详情与阅读两屏只挂在「探索」那一支里，于是站在「阅读历史」那一行按一行：
        // 载荷设上了、屏不换、也不报错 —— 起窗拍到的就是那一格
        //（`D_落点 … 行=阅读历史 详情=开` 而 `D_详情` 一个字都没念，手册行 124）。
        val screen = desktopComicScreen(content, hasComic = comicTarget != null, hasRead = readTarget != null)
        // 批 83：当前用哪一颗源是**这一域**的状态 —— 源管理那一屏改它，探索/详情/阅读都跟着换。
        // 引擎吃的是折过停用之后的那一颗：停用了还照它装载，那枚开关就是假的。
        val sources = rememberDesktopSources(config)
        val activeConfig = config.copy(sourceKey = sources.activeKey)
        // 第一百零一批：队列表在**这一层**收一次再递给详情那一屏（展示那一件只吃纯数据，不吃那颗件本身）。
        val queueTasks = DesktopComicDownloadQueue.tasks.collectAsState().value
        // 第一百零五批：那一屏还要多知道一格"这张表读回来过没有"—— 没读回来时不许念「本机队列空」
        //（包内实测到那一帧，成因与修法见 rounds `desktop-comic-queue-screen-2026-10-13.md` §7c）。
        val queuePrimed = DesktopComicDownloadQueue.primed.collectAsState().value
        // 第一百零六批：「下载队列」那一行要不要引擎，由那张表**有没有活**决定（泵挂在句柄上，句柄没了泵就不跑）。
        // ⚠️ `RUNNING` 那一档必须一起数：手上那一笔在内存里正是 RUNNING，只认 PENDING 的话跑第一笔的同一刻
        // `needed` 就翻假 ⇒ 句柄被回收 ⇒ 泵被掐 ⇒ `abandonRunning` 把它交回 PENDING ⇒ `needed` 又翻真 ——
        // 起一颗引擎、掐一圈泵来回打转，而每转一圈都重新问一遍页表。这一形是实施前从接线推出来的，不是量到的。
        val queueHasWork = queueTasks.any { it.status == DesktopComicQueueStatus.PENDING || it.status == DesktopComicQueueStatus.RUNNING }
        val engine = rememberComicEngine(activeConfig, needed = desktopComicEngineNeeded(screen, queueHasWork))
        val session = engine.session
        when (screen) {
            // `requireNotNull` 不是兜底：载荷在不在是上面那颗纯判断吃进的两枚布尔，
            // 两边对不上就是代码 bug —— 那种形状要炸得响，不许静默画一屏空壳。
            DesktopComicScreen.READER ->
                DesktopComicReaderPane(
                    session,
                    activeConfig.sourceKey,
                    requireNotNull(readTarget),
                    config.history,
                    config.readerPrefs,
                    startReaderMode,
                    onBackReader,
                )
            DesktopComicScreen.DETAIL ->
                DesktopComicDetailPane(
                    // 全部改**命名实参**（批 96）：那一屏的形参表已经十枚，而 `onBack`/`onFavorite`/
                    // `onOpenEpisode`/`onDownloadEpisode` 之间全是同形的 lambda —— 位置实参插一枚进去
                    // 会安静地把下载种子递进别人的格子里，编译照过。
                    session = session,
                    target = requireNotNull(comicTarget),
                    sourceKey = activeConfig.sourceKey,
                    favorites = config.favorites,
                    history = config.history,
                    // 章节下载的根：懒装载那一族（批 96）—— 建目录由写那一侧做，这一跳只递"该写到哪里"。
                    // 必填不给默认值：漏传的形状是行上那枚「下载」按下去静默无反馈。
                    downloadsRoot = { desktopComicDownloadsRoot(config.dataDir) },
                    // 取证种子：必填不给默认值 —— 漏在这一跳上就是"`-Pdownload=…` 递上而盘上一颗文件都没有"。
                    downloadSeed = config.downloadSeed,
                    // 第一百批：取消那一档的种子必填不给默认值（同一颗纪律）。
                    downloadCancelAfter = config.downloadCancelAfter,
                    // 第一百零一批：那一屏那一行念的是队列里这一本的那一笔（必填不给默认值）。
                    queueTasks = queueTasks,
                    onBack = onBackComic,
                    onOpenEpisode = onOpenEpisode,
                )
            DesktopComicScreen.EXPLORE -> ComicExplorePane(engine, activeConfig, onOpenComic)
            DesktopComicScreen.FAVORITES -> ComicFavoritesPane(config)
            DesktopComicScreen.HISTORY -> DesktopComicHistoryPane(activeConfig, sources.nameOf(sources.activeKey), onOpenComic)
            // 批 97：这一屏不吃饭局与引擎，只吃 downloads 根（懒装载那一族：建不建目录归写那一侧管）。
            DesktopComicScreen.DOWNLOADS -> DesktopComicDownloadsPane(desktopComicDownloadsRoot(config.dataDir), deleteSeed = config.deleteSeed, repairSeed = config.repairSeed)
            // 第一百零五批：这一屏也不吃饭局 —— 它的对象是那张任务表（盘上那份），与顶栏那枚牌子同一份数据。
            DesktopComicScreen.QUEUE -> DesktopComicQueuePane(desktopComicDownloadsRoot(config.dataDir), queueTasks, queuePrimed)
            DesktopComicScreen.SOURCES -> DesktopComicSourcesPane(
                sources,
                // 「登录」那一下才起引擎（这一屏平时刻意不起，见 desktopComicEngineNeeded）。
                // 装载的是**用户点的那一颗**而不是当前在用那颗 —— 手填凭据是给那一站的，
                // 拿在用那颗去校验会登错门（批 88·下半 rounds §4 第 2 条）。
                login = { key, values ->
                    DesktopCookieLogin.run(
                        assetDir = config.assetDir,
                        dataDir = config.dataDir,
                        proxy = config.proxy,
                        sourceKey = key,
                        values = values,
                        seedCookies = config.seedCookies,
                    )
                },
            )
        }
    }
}

/** 漫画域此刻渲染的是哪一层（批 82.7：从前这一格由**行**决定，于是从「阅读历史」按一行进不去详情）。 */
internal enum class DesktopComicScreen { READER, DETAIL, EXPLORE, FAVORITES, HISTORY, SOURCES, DOWNLOADS, QUEUE }

/**
 * 载荷优先于行：详情与阅读是**域层**的落点（两枚槽由应用根持有），不是「探索」那一行的私产。
 * 阅读排在详情之上 —— 开着阅读时底下那一屏留在原处，「← 返回详情」清阅读槽即回到同一本的章节表。
 */
internal fun desktopComicScreen(
    content: DesktopGallerySectionContent,
    hasComic: Boolean,
    hasRead: Boolean,
): DesktopComicScreen = when {
    hasRead -> DesktopComicScreen.READER
    hasComic -> DesktopComicScreen.DETAIL
    content == DesktopGallerySectionContent.ComicFavorites -> DesktopComicScreen.FAVORITES
    content == DesktopGallerySectionContent.ComicHistory -> DesktopComicScreen.HISTORY
    content == DesktopGallerySectionContent.ComicSources -> DesktopComicScreen.SOURCES
    content == DesktopGallerySectionContent.ComicDownloads -> DesktopComicScreen.DOWNLOADS
    content == DesktopGallerySectionContent.ComicQueue -> DesktopComicScreen.QUEUE
    else -> DesktopComicScreen.EXPLORE
}

/**
 * 当前这一屏要不要问源站。「收藏」「阅读历史」「源管理」三屏读的是本地（盘上那份清单与状态），
 * 白起一颗 GraalJS 加一整源装载（30 张封面）是代价不是准备；
 * 而从历史那一行进详情时这一格变成"要"，装载就在那一刻开始。
 *
 * [hasQueueWork] 只有两档吃它：「下载队列」与「本机已下载」（第一百一十一批起后者也会自己造队列活）。
 * **必填、不给默认值**（同一颗纪律第四次 ——
 * 漏传的形状是"参数在而永远恒真/恒假"，编译面什么都看不见）。
 */
internal fun desktopComicEngineNeeded(screen: DesktopComicScreen, hasQueueWork: Boolean): Boolean = when (screen) {
    // 「本机已下载」读的是盘上那一棵树，一站都不问源站（批 97）—— 但第一百一十一批起那一行**自己会造队列活**
    // （缺页那一档的「补齐」把手只入队），所以它与 `QUEUE` 同一档：有活才起，空表不白起一颗 GraalJS。
    DesktopComicScreen.DOWNLOADS, DesktopComicScreen.QUEUE -> hasQueueWork
    DesktopComicScreen.FAVORITES, DesktopComicScreen.HISTORY, DesktopComicScreen.SOURCES -> false
    // 第一百零五批把那一屏做出来了，第一百零六批才发现它**只读不驱动**：句柄为 null 时泵那一圈走不到，
    // 盘上那一笔 `PENDING` 永远不动（包内读数 `D_队列 笔=3 跑=0 待=1`）。所以这一档改吃"表里有没有活"：
    // 有活才起引擎（空表还去装载整源是代价），没活就交回 null（跑完了一行也不留一颗没人用的句柄）。
    DesktopComicScreen.EXPLORE, DesktopComicScreen.DETAIL, DesktopComicScreen.READER -> true
}

// ─────────────────────────── 探索 ───────────────────────────

private sealed interface ComicExploreState {
    data object Loading : ComicExploreState
    data class Failed(val reason: String) : ComicExploreState
    data class Ready(
        val sourceName: String,
        val sections: List<EngineSession.Section>,
        val covers: Map<String, ImageBitmap>,
        val readCount: Int,
    ) : ComicExploreState
}

/**
 * 一次装载带回的两样东西。
 *
 * ⚠️ [engine] 必须在结果里一路带回来、而不是让组合里的 state 自己再建一颗：
 * `EngineSession` 是在 IO 线程里构造的 GraalJS context，它是这一次装载**唯一**的那颗句柄，
 * 丢了它再建一颗等于把已装载的源脚本文档重新 parse 一遍。
 */
private data class ComicLoadResult(
    val engine: EngineSession,
    val sourceName: String,
    val sections: List<EngineSession.Section>,
    val covers: Map<String, ImageBitmap>,
)

/** 一次就取 12 个分区 × 每区 12 条；再往下堆对一个"看一眼"的画面没有意义。 */
private const val COMIC_EXPLORE_SECTION_LIMIT = 12
private const val COMIC_EXPLORE_ITEM_LIMIT = 12

/** 封面并发上限。原版是串行拉 30 张，一张慢就把整屏拖住。 */
private const val COMIC_COVER_LIMIT = 30

/** 域层持有的那颗引擎：装载状态、句柄本身、以及「重取」那一下。 */
private class DesktopComicEngine(
    val state: ComicExploreState,
    val session: EngineSession?,
    val retry: () -> Unit,
)

/**
 * 漫画域引擎的持有件（批 82.7 从「探索」那一屏提到**域层**）。
 *
 * 提上来的理由是那条拍到的缺陷：详情与阅读两屏要用同一颗句柄，而句柄从前住在探索那一支里
 * ⇒ 站在「阅读历史」那一行时它压根不存在，载荷设上了也没有那一屏可换。
 *
 * [needed] 由 `desktopComicEngineNeeded` 算：不需要时把句柄交回 `null`，
 * 挂在这里的 `DisposableEffect` 会关掉那一颗 context ——
 * 于是"从详情退回历史那一屏"不会留一颗没人用而也没人关的引擎。
 */
@Composable
private fun rememberComicEngine(config: DesktopComicEngineConfig, needed: Boolean): DesktopComicEngine {
    var attempt by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<ComicExploreState>(ComicExploreState.Loading) }
    // EngineSession 持有 GraalJS 的 context，必须随这一屏的退场关掉 —— 不关就是句柄泄漏，
    // 而每切一次走漫画域再回来会再建一颗，来看几次就能把 JVM 撑死。
    //
    // key 用 session 而不是 Unit：换一颗新 session 时旧 effect 的 `onDispose` 会先跑，
    // 把上一颗 context 关掉。写 `Unit` 的话它在这一屏存活期间只 dispose 一次，
    // 于是"重取"一次就泄漏一颗。
    var session by remember { mutableStateOf<EngineSession?>(null) }
    // 第一百零一批：队列那一圈泵的宿主。它必须活得比详情那一屏久（换屏不断），而**不必**活得比这一域久
    // —— 退出漫画域就协作式停手，任务留在盘上，回到这一域接着跑。
    // key 与 session 同一枚：换一颗引擎就换一圈泵，绝不留一圈泵打在已经关掉的 context 上。
    val queueScope = remember(session) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    DisposableEffect(session) {
        // ⚠️ 必须**在这一刻把值取出来**再交给闭包：`onDispose { session?.close() }` 读的是变量当下的值，
        // 于是 `null → 引擎` 那一次切换时它关掉的是刚建好的那一颗，而不是上一颗。
        // 批 80 之前没人看得见这一格：装载那一段用完引擎就不再碰它；详情那一屏是第一个
        // "在 session 换过之后还要用同一颗"的消费点，第一次真跑就得到
        // `D_详情 … 态=没成 因=PolyglotException: Context execution was cancelled.`
        val closing = session
        desktopComicStartQueuePump(queueScope, closing, desktopComicDownloadsRoot(config.dataDir))
        // ⚠️ 顺序要紧：先掐作用域（那一圈泵从此不再问引擎要下一页），再关 context。
        // 反过来就是"任务还在跑而句柄已被关"，其近亲是批 80 真跑撞到的 `PolyglotException: Context execution was cancelled.`
        // 判据钉的是这一整串而不是"两件事都在"（钉存在会放过写反的那一版）。
        onDispose { queueScope.cancel(); closing?.close() }
    }
    LaunchedEffect(config.sourceKey, attempt, needed) {
        if (!needed) {
            // 这一屏不要引擎：交回句柄（置 null 会触发上面那颗 onDispose 关掉那一颗 context），
            // 状态退回"等回话"，下次真要用的那一刻重新装载 —— 不留一颗没人关的引擎。
            session = null
            state = ComicExploreState.Loading
            return@LaunchedEffect
        }
        if (config.sourceKey.isBlank()) {
            // 走到这里只剩一种成因：盘上那份停用清单被改到**全停用**（界面上最后一颗是关不掉的）。
            // 宁可不装并说一句实话，也不硬挑一颗用户关掉的去装 —— 那是假开关的另一种写法。
            session = null
            state = ComicExploreState.Failed("没有可装载的源：清单里的源全被停用")
            return@LaunchedEffect
        }
        state = ComicExploreState.Loading
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val host = DesktopJsHost(config.assetDir, config.dataDir, config.proxy, config.seedCookies)
                val engine = EngineSession(host, config.assetDir)
                val name = engine.load(config.sourceKey)
                val page = engine.explore(1)
                val shown = page.sections.map { it.copy(comics = it.comics.take(COMIC_EXPLORE_ITEM_LIMIT)) }
                    .take(COMIC_EXPLORE_SECTION_LIMIT)
                val all = shown.flatMap { it.comics }
                if (all.isEmpty()) {
                    // 空手而归必须说清是哪种空：没有 explore / 信封失败 / 源回了 0 条
                    error(page.error ?: "源返回 0 条（分区 ${page.sections.size} 个）")
                }
                val covers = LinkedHashMap<String, ImageBitmap>()
                all.take(COMIC_COVER_LIMIT).forEach { card ->
                    if (card.cover.isBlank()) return@forEach
                    runCatching {
                        org.jetbrains.skia.Image.makeFromEncoded(engine.coverBytes(card.cover))
                            .toComposeImageBitmap()
                    }.getOrNull()?.let { covers[card.id] = it }
                }
                ComicLoadResult(engine, name, shown, covers)
            }
        }
        state = outcome.fold(
            // 顺序要紧：先交 session（换 session 会触发旧 effect 的 onDispose，把上一颗 context 关掉），
            // 再置 state。反过来会得到一小段"state 已经是新的、而 session 还是旧的"的窗口，
            // 那时点开的详情会打在已经关掉的那颗 host 上。
            onSuccess = { result ->
                session = result.engine
                ComicExploreState.Ready(
                    sourceName = result.sourceName,
                    sections = result.sections,
                    covers = result.covers,
                    readCount = result.covers.size,
                )
            },
            onFailure = { ComicExploreState.Failed("${it::class.java.simpleName}: ${it.message?.take(240)}") },
        )
    }

    // retry 交给域层用：「重取」那一枚从前摸的是这一支里的 `attempt`，
    // 现在那一屏住在 ComicExplorePane 里，够不着这个 var —— 递出去的是"把这一笔再来一次"这个动作。
    return DesktopComicEngine(state, session) { attempt += 1 }
}

/**
 * 探索那一屏的界面（引擎由域层持有，见 [rememberComicEngine]）。
 *
 * ⚠️ 这一颗不许写成"自己装载"的形状：详情与阅读要用**同一颗**句柄，
 * 每屏各装一颗等于每次点开都重新 parse 一遍源脚本 + 多拉 30 张封面。
 */
@Composable
private fun ComicExplorePane(
    engine: DesktopComicEngine,
    config: DesktopComicEngineConfig,
    onOpenComic: (DesktopComicTarget) -> Unit,
) {
    val state = engine.state
    val scope = rememberCoroutineScope()
    var favoriteOutcome by remember { mutableStateOf<DesktopComicFavoriteOutcome?>(null) }
    // 卡上那一枚「已下载 / 离线不全」：读盘那一跳住在另一颗文件里（见 `DesktopComicOfflineMark.kt`），
    // 这一屏只吃它交回的那张表 —— 引擎装载那一跳与它不同轨，重取一次源不该顺手把本机的那棵树也重数一遍。
    val offline = rememberDesktopComicOfflineBadges(config.dataDir, config.sourceKey)
    // 「墙上那排卡里贴出来几枚」：盘上数得着 ≠ 屏上贴得出。包内那一帧量到的正是这一档 ——
    // 墙上 30 张卡而本机那棵树里只有 `1461316`（它不在墙上），所以那一枚一枚都没贴出来。
    // 没有这句读数，"没贴上"与"贴上了而我没拍到"就分不开。
    LaunchedEffect(offline, state) {
        val ready = state as? ComicExploreState.Ready ?: return@LaunchedEffect
        println(
            desktopComicOfflineHitReading(
                config.sourceKey,
                offline,
                ready.sections.flatMap { it.comics }.map { it.id },
            )
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("漫画探索", color = DesktopTheme.TextPrimary, fontSize = 18.sp)
            Text(
                " · 源 ${config.sourceKey}${config.proxy?.let { " · 代理 $it" } ?: " · 直连"}",
                color = DesktopTheme.TextSecondary,
                fontSize = 12.sp,
            )
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                    .clickable { engine.retry() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("重取", color = DesktopTheme.TextSecondary, fontSize = 11.sp)
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (val current = state) {
                ComicExploreState.Loading ->
                    Text("装载源 ${config.sourceKey}…", color = DesktopTheme.TextSecondary, fontSize = 12.sp)

                is ComicExploreState.Failed ->
                    Text(current.reason, color = DesktopTheme.AccentBeni, fontSize = 12.sp)

                is ComicExploreState.Ready -> {
                    Text(
                        "${current.sourceName} · ${current.sections.size} 个分区 / " +
                            "${current.sections.sumOf { it.comics.size }} 条 · 出图 ${current.readCount} 张",
                        color = DesktopTheme.TextSecondary,
                        fontSize = 12.sp,
                    )
                    current.sections.forEach { section ->
                        ComicSection(section, current.covers, config.sourceKey, offline, { card ->
                            // 写那一笔走的是屏上这颗懒装载（第一次按下才建库），三态里哪一种落回 favoriteOutcome。
                            scope.launch {
                                favoriteOutcome = desktopAddComicFavorite(config.favorites, config.sourceKey, card)
                            }
                        }, onOpen = { card ->
                            onOpenComic(DesktopComicTarget(card.id, card.title, card.cover))
                        })
                    }
                }
            }
            // 按下去之后屏上要有回话：三态各一句（"已在里面"与"没成"塌成一句就是"点了没反应"）。
            favoriteOutcome?.let { outcome ->
                Text(
                    desktopComicFavoriteScreenReading(outcome),
                    color = if (outcome is DesktopComicFavoriteOutcome.Failed) {
                        DesktopTheme.AccentBeni
                    } else {
                        DesktopTheme.TextSecondary
                    },
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ComicSection(
    section: EngineSession.Section,
    covers: Map<String, ImageBitmap>,
    sourceKey: String,
    offline: Map<String, DesktopComicOfflineBadge>,
    onFavorite: (EngineSession.ComicCard) -> Unit,
    onOpen: (EngineSession.ComicCard) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            section.title,
            color = DesktopTheme.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        // 固定 6 列而不是 Adaptive：宽度 kost, wall 那是 deli 立面 стекла，
        // 这里是一排同样尺寸的封面，列数跟窗宽走会让"这张封面多大"变成变量。
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(estimatedGridHeight(section.comics.size)),
        ) {
            items(section.comics) { card ->
                ComicCard(
                    card,
                    covers[card.id],
                    offline[desktopComicOfflineKey(sourceKey, card.id)],
                    onFavorite,
                    onOpen,
                )
            }
        }
    }
}

/** 每行列数固定 6，封面按 3:4，加行间距 —— 高度必须算出来，否则 Grid 在 Column 里会争无限高度。 */
private fun estimatedGridHeight(count: Int): androidx.compose.ui.unit.Dp {
    val rows = (count + 5) / 6
    val cardWidth = 132.dp
    val cardHeight = cardWidth * (4f / 3f) + 22.dp
    // ⚠️ Dp 只能写在乘号的**左边**（`Int.times(Dp)` 没有这个重载，`Dp.times(Int)` 才有）
    return cardHeight * rows + 8.dp * (rows - 1).coerceAtLeast(0)
}

@Composable
private fun ComicCard(
    card: EngineSession.ComicCard,
    cover: ImageBitmap?,
    offline: DesktopComicOfflineBadge?,
    onFavorite: (EngineSession.ComicCard) -> Unit,
    onClick: (EngineSession.ComicCard) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(Modifier.width(132.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(
                    if (hovered) DesktopTheme.SurfaceRaised else DesktopTheme.CardBackground,
                    RoundedCornerShape(6.dp),
                )
                .hoverable(interaction)
                // 整张封面点得动（批 80）。排在 `hoverable` 之后：那一枚只管变色，不吃事件。
                // 卡角那枚「收藏」是 Box 的子件，子件先拿事件 ⇒ 压在封面上的那一枚照旧只收藏。
                .clickable { onClick(card) },
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 封面没取到也要"`没出图`是看得见的" —— 空白块会被读成"这张还没加载完"
                Text("无封面", color = DesktopTheme.TextTertiary, fontSize = 11.sp)
            }
            // 压在封面上那一角（与画廊卡那两枚角标同一个位置口径），高度就不动 `estimatedGridHeight` 那笔账。
            // 常驻而不是 hover 才出现：桌面没有"鼠标悬停=可点"的暗示，藏起来的入口等于没有入口。
            ComicFavoritePill(Modifier.align(Alignment.BottomStart).padding(6.dp)) { onFavorite(card) }
            // 右下角那一枚「已下载 / 离线不全」（左下角那枚是收藏，两枚不抢同一格）。它**不可点**，
            // 也不加行 —— 行高由 `estimatedGridHeight` 那笔算式定。
            if (offline != null) ComicOfflineMark(offline, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        Text(
            card.title,
            color = DesktopTheme.TextSecondary,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * 卡角那一枚「收藏」（批 76）。
 *
 * 形状照画廊卡的角标（`OverlayScrim` 圆座 + 10.sp），只是它**点得动** —— 而 `clickable` 排在
 * `background` 之后、内衬 `padding` 之前，那一圈内衬因此也算进命中区（与「重取」那枚同一口径）。
 */
@Composable
private fun ComicFavoritePill(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(DesktopTheme.OverlayScrim, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text("收藏", color = DesktopTheme.TextPrimary, fontSize = 10.sp)
    }
}

// ─────────────────────────── 收藏夹 ───────────────────────────

@Composable
private fun ComicFavoritesPane(config: DesktopComicEngineConfig) {
    var tree by remember { mutableStateOf<List<Pair<String, List<FavoriteItem>>>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val manager = config.favorites()
                manager.currentFolders().map { it to manager.getFolderComics(it) }
            }
        }
        outcome.fold(
            onSuccess = { tree = it; note = null },
            onFailure = {
                note = "收藏列表读取失败：${it::class.java.simpleName}: ${it.message?.take(200)}"
                tree = emptyList()
            },
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("漫画收藏夹", color = DesktopTheme.TextPrimary, fontSize = 18.sp)
        Text(
            "这一份落在本机 SQLite（LocalFavoriteDatabase），与 rail「收藏」那一枚的画廊收藏不是同一份数据。",
            color = DesktopTheme.TextTertiary,
            fontSize = 11.sp,
        )

        val currentNote = note
        if (currentNote != null) {
            Text(currentNote, color = DesktopTheme.AccentBeni, fontSize = 12.sp)
        } else if (tree.isEmpty()) {
            Text("还没有收藏任何一本。", color = DesktopTheme.TextSecondary, fontSize = 12.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tree.forEach { (folder, items) ->
                    item {
                        Column {
                            Text("$folder · ${items.size} 本", color = DesktopTheme.TextPrimary, fontSize = 14.sp)
                            items.forEach { comic ->
                                Text(
                                    "　${comic.name}　（源 ${comic.sourceKey}）",
                                    color = DesktopTheme.TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
