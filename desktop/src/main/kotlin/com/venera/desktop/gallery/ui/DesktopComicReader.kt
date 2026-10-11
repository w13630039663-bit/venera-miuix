package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import com.venera.desktop.gallery.data.DesktopReaderMode
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.gallery.data.DesktopComicReaderPreferences
import com.venera.desktop.gallery.data.DesktopReadingProgress
import com.venera.desktop.gallery.data.desktopComicPageCacheKey
import com.venera.desktop.gallery.data.desktopSessionCover
import com.venera.desktop.gallery.data.desktopWriteReadingHistory
import com.venera.engineprobe.EngineSession
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 阅读那一屏的载荷：从详情那一屏的（哪一本 + 哪一话）推出来，或从 `--read=` 种子造出来。 */
data class DesktopComicReading(
    val comicId: String,
    val title: String,
    val episodeId: String,
    val episodeName: String,
    val placeholder: Boolean,
    /**
     * 这一话在章表里的第几行。**历史那一列要它**（对岸 `lastChapterIndex` 写的就是这一个，
     * 手机侧读它续读），而话号本身换不成行号 —— 章表次序由源站给，桌面不重排。
     *
     * `--read=` 那条种子不知道行号，只能给 0：那一趟只跑在隔离的数据目录里（无人值守取证），
     * 不会把用户的真实进度挪到第一话。
     */
    val episodeIndex: Int,
    /**
     * 进这一屏时**先落在哪一页**（0 起）。从详情那一行进来是 0（第一次读这本）；
     * `--read-page=` 那颗种子把它当取证用的落点（批 82.5），为的是让真窗口自己念出
     * 「页号从 `LazyListState` 流进写的那一笔」—— 离屏那一趟量不到滚动，见 rounds 那份文件。
     *
     * 必填、没有默认值：起点是**调用方的决定**而不是这一屏的常识，让它必须交代一次。
     * 「从库里那一行续读」今天还没接（历史那一屏按下去目前从第一页起），所以这里唯一的非零来源是种子。
     */
    val startPage: Int,
    /**
     * 这一本的封面地址，**在拼载荷那一刻就算好**（详情那张优先、入口那枚兜底）而不是留到写口再算：
     * 写口那一颗件（`desktopHistoryRecord`）要的是"这一笔落库的内容"，把选哪一张的决定留在里面
     * 就等于让两端共用那张表上的一格由写口自己发明。
     *
     * `--read=` 那条种子给空串：那一趟没有入口卡、也不走详情屏，猜一张比留空更糟。
     */
    val coverUrl: String,
)

/**
 * 详情页那一行 → 阅读载荷。
 *
 * 章名与话号**原样带上**而不是现取：占位章（"整部就一章"那一类）的话号取页时要还原成 null，
 * 而"它是不是占位章"这件事只有 [EngineSession.Episode.placeholder] 知道 —— 所以那句话的账在这一刻就定下来。
 *
 * 封面在这一刻算：[detailCover] 是详情那一屏刚从 `loadInfo` 读回来的那一张，[target] 手上那枚是入口带的，
 * 优先级只在 [desktopSessionCover] 一处（对岸那条口径，判据回核它）。
 */
internal fun desktopComicReadingOf(
    target: DesktopComicTarget,
    episode: EngineSession.Episode,
    episodeIndex: Int,
    startPage: Int,
    detailCover: String,
): DesktopComicReading =
    DesktopComicReading(
        comicId = target.comicId,
        title = target.title,
        episodeId = episode.id,
        episodeName = episode.name,
        placeholder = episode.placeholder,
        episodeIndex = episodeIndex,
        startPage = startPage,
        coverUrl = desktopSessionCover(detailCover, target.cover),
    )

/**
 * 起始页号 → **这一页表里真有的**那个索引（批 82.5）。
 *
 * 三档都是外人递进来的数撞得上的：页表还没回来（0 页）、种子比页数大、种子是负数。
 * 直接把没折的数交给 `scrollToItem` 就撞在"请求一个不存在的项"那一档，而屏上对这一条没有解释。
 * 折的口径：负数与空表都落第一页，超界落最后一页（不是"猜一个中间值"）。
 */
internal fun desktopReaderStartPageIndex(start: Int, pages: Int): Int = when {
    pages <= 0 -> 0
    start <= 0 -> 0
    start >= pages -> pages - 1
    else -> start
}

/**
 * 「请求滚到那一页」与「量到的那一页」那一行自证读数（批 82.5）。
 *
 * 为什么必须有：`--read-page=3` 起窗后库里那一行念的是 `页=0/89`，而那一格**同时是**两种故障的形状 ——
 * "种子没活到 pane"与"活到了而列表没滚"。少了这一行就只能拿其中一种去解释另一种，那是推断不是读数。
 * 本仓那条老纪律在这里第 N 次成立：**拿量具下结论之前，先量一次量具动不动得起来。**
 */
internal fun desktopReaderStartPageReading(
    requested: Int,
    observed: Int,
    pageCount: Int,
    tries: Int,
): String =
    "D_阅读 起点 请求=$requested 量到=$observed 页表=$pageCount 试=$tries" +
        if (requested == observed) "" else " 没落到"

/**
 * 落起点那一颗件（连同 `DESKTOP_READER_START_PAGE_TRIES` 与那枚等待上限）搬到了 `DesktopReaderPaging.kt`：
 * 第八十七批起两档共用同一个有界重试循环，而**各滚各的那面墙**（拿列表去滚 pager 那面墙是空操作，
 * 不报错、屏上只是不动 —— 本仓记过同族的一条）。批 82.5 那三条读数原样带过去，一条没放宽。
 */

/** 阅读那一屏的三态（与详情那一屏同一族形状：装载与展示分家，展示件能被喂着离屏渲）。 */
internal sealed interface ComicReaderState {
    data object Loading : ComicReaderState
    data class Failed(val reason: String) : ComicReaderState
    data class Ready(val pages: List<String>) : ComicReaderState
}

/**
 * 每页之间那条缝：**逐字照对岸** `prefs.pageGapDp` 的默认值（`data/prefs/VeneraPreferences.kt:72`）。
 *
 * 桌面这一批不做那枚偏好，所以它在这里是常量。判据回读对岸那一行核对，对岸改了而这里没跟上时红的是判据。
 */
internal const val DESKTOP_READER_PAGE_GAP_DP: Int = 8

/**
 * 列表 `key`：对岸那枚是 `"v-$currentChapterIndex-$index"`（`VeneraReaderScreen.kt:640` 那一族）。
 *
 * 桌面多一档：占位章的话号是空串，直接拼会得出 `"v--3"`，而**两本不同的占位章各有一枚 `"v--3"`** ⇒
 * LazyColumn 把上一话的位当同一项复用，屏上表现为"换了一页还是上一页那张"。所以空话号退成漫画号。
 */
internal fun desktopReaderItemKey(episodeId: String, comicId: String, index: Int): String {
    val scope = episodeId.ifBlank { comicId }
    return "v-$scope-$index"
}

/**
 * 那一话在屏上念什么。**三种情形三种念法**，差别全在"我们晓不晓得它是占位章"：
 * - 占位章 ⇒ 与详情那一屏念同一句（两处各写一句就会念成两个名字）；
 * - 有章名 ⇒ 章名；
 * - 章名空而**不是**占位章（`--read=` 那颗种子只带话号）⇒ 念话号。
 *
 * 最后这一档是图证抓出来的：第一版在这里念了「整部（这一本没有分话）」，而那一本有 15 话
 * （同一轮 stdout 上的 `D_详情 … 话=15` 就是反证）—— 不知道章名就念话号，不替源站编一句。
 * 两样都没有时交回空串：由调用方**不摆那一格**，而不是留一枚悬着的分隔符。
 */
internal fun desktopReaderEpisodeLabel(episodeName: String, placeholder: Boolean, episodeId: String): String = when {
    placeholder -> "整部（这一本没有分话）"
    episodeName.isNotBlank() -> episodeName
    episodeId.isNotBlank() -> "话 $episodeId"
    else -> ""
}

/**
 * stdout 那一行：这一屏开得成不成、几页、**起点种成了第几页**。
 *
 * 为什么要有一行：`Loading` 与 `Failed` 在像素上都是"左上角一行小字"，隔着一张分不出；
 * 而"页=0"这一格正是「整本翻不出页」那条老缺陷的形状 —— 它必须念得出来，不许折成成功之外的美话。
 *
 * `起点=` 是批 82.5 补的那一格：`--read-page=` 那颗种子从判读到屏上要走四跳（判读 → 载荷 → 应用根 → pane），
 * 而"库里那一行停在第 1 页"这一条读数**同时是**"种子没种进来"与"种进来了而列表没滚"两种故障的形状。
 * 少了这一格就只能拿其中一种去解释另一种（那是推断不是读数）。
 */
internal fun desktopComicReaderReading(
    reading: DesktopComicReading,
    sourceKey: String,
    state: ComicReaderState,
): String {
    val head = "D_阅读 源=$sourceKey 号=${reading.comicId} 话=${reading.episodeId} 起点=${reading.startPage}"
    return when (state) {
        ComicReaderState.Loading -> "$head 态=等回话"
        is ComicReaderState.Failed -> "$head 态=没成 因=${desktopComicReaderOneLine(state.reason)}"
        is ComicReaderState.Ready -> "$head 态=上屏 页=${state.pages.size}"
    }
}

/** 一行化：站方原话常带 stack 尾巴，两行会把证据送到抓读数脚本看不见的地方。 */
private fun desktopComicReaderOneLine(reason: String): String =
    reason.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').trim().take(200)

/**
 * 阅读那一屏的**装载**件：问一次页表，折成三态，然后交展示件。
 *
 * 引擎句柄由持有者传下来（`DesktopDomainNavigationTest` ⑩/⑪ 钉着：应用根不许自己造一颗），
 * 而换话时**不许新建引擎** —— 那一屏的键只到 `episodeId`，`session` 换了才重跑。
 */
@Composable
internal fun DesktopComicReaderPane(
    session: EngineSession?,
    sourceKey: String,
    reading: DesktopComicReading,
    history: () -> HistoryDao,
    /**
     * 模式那枚偏好件（第八十七批）。与批 86 那颗历史件同一条处理：**件递进来而不是在屏上取静态口**
     * （`DesktopUiIsolationTest` 钉的就是"UI 不在 UI 里 getInstance"）。
     */
    readerPrefs: DesktopComicReaderPreferences,
    /** 取证种子顶上的那一档；没顶过传 `null`（种子只作初值、不写盘 —— 批 83.1 那条口径）。 */
    startReaderMode: DesktopReaderMode?,
    onBack: () -> Unit,
) {
    var state by remember(reading.episodeId) { mutableStateOf<ComicReaderState>(ComicReaderState.Loading) }
    val listState = rememberLazyListState()
    val storedMode by readerPrefs.readingMode.collectAsState()
    val mode = desktopReaderModeOf(startReaderMode, storedMode)
    // 整页翻那一档的那面墙。状态在**装载这一层**造（与 listState 同一理由）：
    // 展示件自己 remember 的话，落定的那一页就永远出不了这一件，历史那一笔只能恒写第一页。
    // 页数那一步就地读 `state`（而不是递一枚外面算好的常量）：页表回来时这一句要跟着变，
    // 递常量就会把 pager 永远停在"0 页"那一档。
    val pagerState = rememberPagerState(pageCount = { (state as? ComicReaderState.Ready)?.pages?.size ?: 0 })
    // 「上一页 / 下一页」那两枚按钮，与 ← / → / PageUp / PageDown 那四枚键的**同一颗**落点
    // （第八十七批·下半：这一颗从"只管 pager"改成"按档二选一"，键在两档都落，按钮仍只在整页翻那一档摆）。
    // 步长与钳位走那一族的算式（[desktopReaderStepTarget]，与对岸同轨、由回核尺子钉着），
    // 而**滚哪面墙由档决定**：拿列表去滚 pager 那一档是**空操作**（不报错、屏上不动），
    // 所以这里必须是二选一而不是一路都滚。
    // 翻不动时**照实念一句**而不是原地不动地吞掉 ——
    // 屏上一枚按下去什么都没发生的按钮，就是本仓最忌的假开关。
    val stepScope = rememberCoroutineScope()
    val stepReaderPage: (Int) -> Unit = { delta ->
        val total = (state as? ComicReaderState.Ready)?.pages?.size ?: 0
        val paged = desktopReaderIsPaged(mode)
        val current = if (paged) pagerState.currentPage else listState.firstVisibleItemIndex
        val target = desktopReaderStepTarget(current, delta, mode, total - 1)
        if (target == null) {
            // 页号那一格共用 [desktopReaderPageLabel]：空表时它交回空串，这里念「没有页」，
            // 不念"第 1 / 0 页"那种自相矛盾的读数（同一件事在两处各算一遍就会漂）。
            val pageNote = desktopReaderPageLabel(current, total).ifEmpty { "没有页" }
            println(
                "D_翻页 到边了 档=${mode.label} $pageNote " +
                    "方向=${if (delta >= 0) "后一页" else "前一页"}",
            )
        } else {
            stepScope.launch {
                if (paged) pagerState.scrollToPage(target) else listState.scrollToItem(target)
            }
        }
    }
    LaunchedEffect(session, reading.comicId, reading.episodeId, reading.placeholder) {
        val engine = session
        if (engine == null) {
            state = ComicReaderState.Loading
            return@LaunchedEffect
        }
        val episode = EngineSession.Episode(reading.episodeName, reading.episodeId, reading.placeholder)
        val outcome = withContext(Dispatchers.IO) { runCatching { engine.pages(reading.comicId, episode) } }
        state = outcome.fold(
            { ComicReaderState.Ready(it) },
            { ComicReaderState.Failed("${it::class.java.simpleName}: ${it.message?.take(240)}") },
        )
        println(desktopComicReaderReading(reading, sourceKey, state))
    }
    // 历史那一笔（批 82）：**顶部那一页一变就写**，与对岸 `LaunchedEffect(currentChapterIndex, currentPageIndex)`
    // 同一形状。页表没到手之前不写 —— 那一档在 `desktopWatchReaderProgress` 里面（不是这里），
    // 因为"提前写一笔 `页=0/0`"是要被量到的行为，而不是调用方的自觉。
    // 这一屏**已经摆过**的那一页（`null` = 还没摆过）：换档时的落点取它，见 `desktopReaderCarriedPage`。
    // 它由盯梢那一笔现取，而不是去问另一面墙 —— 换档那一刻那面墙已经离开组合，问出来的是 stale 值或 0。
    var shownPage by remember(reading.episodeId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(listState, pagerState, reading, state, mode) {
        // 先落到起始那一页，**再**开始盯（次序由判据钉住）：反过来时第一笔历史写的是"还没滚之前的 0"，
        // 那一笔会被读成"种子没生效"，而其实是盯梢早开了一步。
        val pageCount = (state as? ComicReaderState.Ready)?.pages?.size ?: 0
        val seedPage = desktopReaderStartPageIndex(reading.startPage, pageCount)
        // 换档会重跑这一颗（键里有 `mode`），所以落点取"这一屏已经摆过的那一页"而不是重新落一次起点：
        // 后者把人甩回第 1 页，而盯梢那一笔会跟着把第 1 页写进两端共用的那张表（rounds §2.4）。
        val startPage = desktopReaderCarriedPage(seedPage, shownPage)
        // 两档各滚各的那面墙，所以这里必须是二选一而不是一路都滚：
        // 拿列表去滚 pager 那一档是**空操作**（不报错、屏上不动），而白等的最坏那一格是 12 × 400 毫秒。
        if (desktopReaderIsPaged(mode)) {
            println(desktopApplyReaderStartPagePager(pagerState, startPage, pageCount))
        } else {
            println(desktopApplyReaderStartPage(listState, startPage, pageCount))
        }
        val shownFlow: Flow<Int> = if (desktopReaderIsPaged(mode)) {
            snapshotFlow { pagerState.settledPage }
        } else {
            snapshotFlow { listState.firstVisibleItemIndex }
        }
        val pageIndex: Flow<Int> = shownFlow.onEach { shownPage = it }
        desktopWatchReaderProgress(
            pageIndex = pageIndex,
            reading = reading,
            sourceKey = sourceKey,
            state = state,
            // 时钟在这里取，不在纯件里取（判据把两边各钉一次）。
            nowMillis = { System.currentTimeMillis() },
            save = { record -> history().saveHistory(record) },
        )
    }
    DesktopComicReaderContent(
        reading = reading,
        state = state,
        sourceKey = sourceKey,
        listState = listState,
        pagerState = pagerState,
        mode = mode,
        seedNote = desktopReaderModeSeedNote(startReaderMode, storedMode),
        onModeChange = { picked -> readerPrefs.setReadingMode(picked) },
        onStep = stepReaderPage,
        onBack = onBack,
        pageImage = { index, imageKey ->
            DesktopComicReaderPageImage(
                DesktopComicPage(
                    sourceKey = sourceKey,
                    comicId = reading.comicId,
                    episodeId = reading.episodeId,
                    imageKey = imageKey,
                    page = index,
                    engine = session,
                ),
            )
        },
    )
}

/**
 * 页表到手之后**盯住当前那一页**，每变一次交一笔历史。
 *
 * 单拎出来是为了能被量到：那一屏的装载件要真引擎（离屏渲不出来），而"写的一直是 `页=0`"
 * 恰好是屏上完全看不出来的那一族 —— 判读层与消费点接好了也不算通，得有人真滚一次给判据看。
 * 于是时钟做成 `nowMillis` 由调用点递（测试递常量，生产递 `System.currentTimeMillis()`），
 * 落库那一半做成 `save`（测试收进行表，生产走 `HistoryDao`）。
 *
 * 第八十七批把第一枚参数从 `LazyListState` 换成了**页号流**：那一屏现在有两面墙
 * （纵向的列表、整页翻的 pager），而"盯哪一面"是调用方的决定不是这一颗的常识。
 * 递错那面墙的形状本仓记过：它不报错、不崩，只是每一笔都记在第一个位置上。
 *
 * 两档行为都住在这里而不是调用方：页表没到手就**一笔都不发**（那时发出去的是一个没发生过的进度），
 * 到手之后哪怕 0 页也发一笔（对岸也写，见 `DesktopComicHistoryTest` 那一条）。
 */
internal suspend fun desktopWatchReaderProgress(
    pageIndex: Flow<Int>,
    reading: DesktopComicReading,
    sourceKey: String,
    state: ComicReaderState,
    nowMillis: () -> Long,
    save: suspend (HistoryRecord) -> Unit,
) {
    val pages = (state as? ComicReaderState.Ready)?.pages ?: return
    pageIndex
        .distinctUntilChanged()
        .collect { index ->
            println(
                desktopWriteReadingHistory(
                    DesktopReadingProgress(
                        comicId = reading.comicId,
                        title = reading.title,
                        sourceKey = sourceKey,
                        // 章名**原样**交下去：不知道就不编（这一列两端共用，手机侧直接念它）。
                        chapterTitle = reading.episodeName,
                        chapterIndex = reading.episodeIndex,
                        pageIndex = index,
                        totalPages = pages.size,
                        // 封面吃载荷里那枚（在详情那一行就算好了）：这一列两端共用，写空串等于抹掉手机侧那张。
                        coverUrl = reading.coverUrl,
                    ),
                    nowMillis = nowMillis(),
                    save = save,
                )
            )
        }
}

/**
 * 阅读那一屏的**展示**件：吃纯数据，不发请求、不读引擎。
 *
 * 拆开的理由与详情那一屏同一条（批 80）：这一件能被喂着离屏渲成语义树来验，
 * 而装载那一件要真 GraalJS 与真源，测试里渲不出来。
 * [pageImage] 是那一格的插槽 —— 真实的槽要 Coil 与 `PlatformContext`，离屏渲的时候由判据喂一枚占位件。
 */
@Composable
internal fun DesktopComicReaderContent(
    reading: DesktopComicReading,
    state: ComicReaderState,
    sourceKey: String,
    listState: LazyListState,
    /** 整页翻那一档的那面墙（由装载那一层递进来，理由与 [listState] 同一条）。 */
    pagerState: PagerState,
    mode: DesktopReaderMode,
    /** 取证种子顶过档时的那句实话；没顶过交回 `null` = 不摆那一格。 */
    seedNote: String?,
    onModeChange: (DesktopReaderMode) -> Unit,
    /**
     * 步进那一颗（`-1` 前一页、`+1` 后一页）。
     *
     * 两枚按钮只在整页翻那一档摆（纵向那一档本来就是连续滚动，给它一枚"下一页"是另一套真相），
     * 而第八十七批·下半起**键在两档都落**这一颗 —— 落哪面墙由装载那一层按档二选一，
     * 这一件只管把键翻成一次 `onStep`（键表在 [onDesktopReaderKey]，那里是唯一一处）。
     */
    onStep: (Int) -> Unit,
    onBack: () -> Unit,
    pageImage: @Composable (index: Int, imageKey: String) -> Unit,
) {
    // ⌨ 这一屏**自己把焦点要过来**：没有焦点时整棵组合树收不到任何按键
    // （`FocusOwnerImpl.dispatchKeyEvent` 无焦点那一路找的是 `rootFocusNode` 的祖先，而那颗上面没有东西 ——
    // 理由全文在 [onDesktopOverlayKey] 的注释里，第五十批在浮层那一屏已经量过一遍）。
    // 要不到焦点不是"键盘没有"，是"键盘接了而屏上不认"，所以它必须念出一行读数。
    val readerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 先让过一帧：这一屏是这一帧才进组合的，`focusRequester` 的节点要等放置之后才挂上。
        withFrameNanos { }
        var granted = readerFocus.requestFocus()
        var retries = 0
        while (!granted && retries < 2) {
            retries++
            withFrameNanos { }
            granted = readerFocus.requestFocus()
        }
        if (!granted) {
            // 两句都带一截 ASCII（`granted=0/1`）：交付包那一层的中文 stdout 全成乱码（批 87 上半 §6.5 量的），
            // 只写中文的话，这一格在包里就永远读不出来 —— 而"键到不到得了这一层"正是要在真人机上量的那一格。
            println("KEYNAV=阅读那一屏 焦点=没拿到 granted=0：← / → 与 PageUp / PageDown 这一轮都不生效，屏上只剩那两枚按钮")
        } else {
            // 拿到也要念：这一批没有 OS 级输入（按键属可见影响），焦点那一格是"键到不到得了这一层"
            // 唯一能在真人窗口里量到的那半句。只在失败时吭声的形状 = 每一帧都得靠"没念"反推。
            println("KEYNAV=阅读那一屏 焦点=拿到 granted=1：← / → 与 PageUp / PageDown 归这一屏")
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .focusRequester(readerFocus)
            .focusTarget()
            .onKeyEvent { event ->
                // 摘读数交给共享那颗件，落哪一步交给共享那张表（本件不另写一份键位判据）。
                val signal = event.asDesktopKeySignal()
                val delta = onDesktopReaderKey(signal)
                if (signal.isKeyDown) {
                    // 每一枚到得了这一层的键都念一行，**包括不接的那些**：
                    // 「没落到」与「根本没送到」糊成同一种哑巴，音量键那一格就永远量不出来。
                    // `原始=` 取的是 AWT 那一层的码；离屏合成注入取不到 ⇒ 念 `无`（那不是失败，是身份）。
                    println(desktopReaderKeyReading(signal.key, event.awtEventOrNull?.keyCode, delta))
                }
                if (delta == null) false else {
                    onStep(delta)
                    true
                }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("← 返回详情", color = DesktopTheme.TextSecondary, fontSize = 11.sp)
            }
            Text(
                " ${reading.title}",
                color = DesktopTheme.TextPrimary,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            desktopReaderEpisodeLabel(reading.episodeName, reading.placeholder, reading.episodeId)
                .takeIf { it.isNotEmpty() }
                ?.let { label ->
                    Text(
                        " · $label",
                        color = DesktopTheme.TextTertiary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }
        // 阅读档那一排：点了就换，没有"保存"那一步（条件即输入是这一屏的既有口径）。
        // 只摆三档 —— 那三档今天真能用；`HORIZONTAL_CONTINUOUS` 与 `DOUBLE_PAGE` 不摆，
        // 因为按不动的一枚比缺的一枚更糟。名字取自那张档位表的 `label`（逐字照对岸，由回核尺子钉着）。
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DESKTOP_READER_MODE_CHOICES.forEach { choice ->
                val picked = choice == mode
                Column(
                    Modifier
                        .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                        .clickable { onModeChange(choice) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        choice.label,
                        color = if (picked) DesktopTheme.AccentBeni else DesktopTheme.TextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        // 取证种子顶过档时那句实话：不写出来，屏上那一档就说不清是用户选的还是参数顶的。
        seedNote?.let { note ->
            Text(note, color = DesktopTheme.AccentBeni, fontSize = 12.sp)
        }
        when (val current = state) {
            ComicReaderState.Loading ->
                Text("正在问源站这一话的页表…", color = DesktopTheme.TextTertiary, fontSize = 12.sp)

            is ComicReaderState.Failed ->
                Text("没问到页表：${current.reason}", color = DesktopTheme.AccentBeni, fontSize = 12.sp)

            is ComicReaderState.Ready -> {
                // 「第 N / M 页」念的是**当前这一屏真摆着的那一页**：纵向那档取列表的顶部项，
                // 整页翻那档取 pager 的当前页。两档各读各的那一面墙（读错了那面就是恒 1 的假读数）。
                val shownPage = if (desktopReaderIsPaged(mode)) pagerState.currentPage else listState.firstVisibleItemIndex
                // 两枚节点分开：合成一句会让"共 N 页"那一条离屏尺子读不到（而它钉的是"页数的读数上屏"）。
                val pageNote = desktopReaderPageLabel(shownPage, current.pages.size)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("共 ${current.pages.size} 页", color = DesktopTheme.TextTertiary, fontSize = 12.sp)
                    if (pageNote.isNotEmpty()) {
                        Text(pageNote, color = DesktopTheme.TextTertiary, fontSize = 12.sp)
                    }
                }
                if (current.pages.isEmpty()) {
                    // 源回了 0 页时不摆空列表就完事 —— 那一格得念出来（读数与屏上念同一件事）。
                    Text("源站这一话回了 0 页。", color = DesktopTheme.AccentBeni, fontSize = 12.sp)
                } else if (desktopReaderIsPaged(mode)) {
                    Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 两枚翻页钮只在这一档摆：纵向那一档本来就是连续滚动，给它一枚"下一页"是另一套真相。
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(-1 to "‹ 上一页", 1 to "下一页 ›").forEach { (delta, label) ->
                                Column(
                                    Modifier
                                        .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                                        .clickable { onStep(delta) }
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(label, color = DesktopTheme.TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                        // 一屏一张：`HorizontalPager` 自带一页一格，`reverseLayout` 让日漫那一档从右往左排。
                        // 拖动、滚轮与这两枚按钮都归同一面墙管，桌面这边不另造一套页码账。
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            reverseLayout = desktopReaderReverses(mode),
                            userScrollEnabled = true,
                            pageSpacing = DESKTOP_READER_PAGE_GAP_DP.dp,
                        ) { index ->
                            pageImage(index, current.pages[index])
                        }
                    }
                } else {
                    // LazyColumn 而不套 verticalScroll：`LazyVerticalGrid × verticalScroll` 那一条整屏崩的
                    // 不变式对这一族同样成立，这里靠 weight 拿到有界高度。
                    // 列表状态由装载那一层递进来（批 82）：这里自己 remember 一枚的话，
                    // 顶部页号就永远出不了这一件，历史那一笔只能恒写第一页。
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(DESKTOP_READER_PAGE_GAP_DP.dp),
                    ) {
                        items(current.pages.size, key = { index ->
                            desktopReaderItemKey(reading.episodeId, reading.comicId, index)
                        }) { index ->
                            pageImage(index, current.pages[index])
                        }
                    }
                }
            }
        }
    }
}

/**
 * 真实的那一格：走 Coil（与画廊图**同一条轨**，批 47 那笔"两条轨"的账不再犯）。
 *
 * 取不到时念"这一页没取成"而不是摆一片空白 —— 空白会被读成"这一页还没加载完"，
 * 而引擎那边"还原不了就抛"（不把混淆图当正常图交回）这一条只有念出来才看得见。
 */
@Composable
internal fun DesktopComicReaderPageImage(page: DesktopComicPage) {
    val cacheKey = desktopComicPageCacheKey(page.sourceKey, page.comicId, page.episodeId, page.imageKey)
    var failed by remember(cacheKey) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().wrapContentHeight().background(if (failed) Color.Black.copy(alpha = 0.05f) else Color.Transparent)) {
        if (failed) {
            Text(
                "第 ${page.page + 1} 页没取成",
                color = DesktopTheme.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center).padding(vertical = 40.dp),
            )
        } else {
            AsyncImage(
                model = page,
                contentDescription = "第 ${page.page + 1} 页",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                onError = { failed = true },
            )
        }
    }
}
