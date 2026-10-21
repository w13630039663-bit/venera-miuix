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

private fun estimatedGridHeight(count: Int): androidx.compose.ui.unit.Dp {
    val rows = (count + 5) / 6
    val cardWidth = 132.dp
    val cardHeight = cardWidth * (4f / 3f) + 22.dp
    // ⚠️ Dp 只能写在乘号的**左边**（`Int.times(Dp)` 没有这个重载，`Dp.times(Int)` 才有）
    return cardHeight * rows + 8.dp * (rows - 1).coerceAtLeast(0)
}

