package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.data.db.HistoryDao
import com.venera.desktop.gallery.data.DesktopComicChapterRun
import com.venera.desktop.gallery.data.desktopComicChapterCancelledLine
import com.venera.desktop.gallery.data.desktopComicChapterDir
import com.venera.desktop.gallery.data.desktopComicChapterDownloadLine
import com.venera.desktop.gallery.data.desktopComicChapterProgressLine
import com.venera.desktop.gallery.data.desktopComicDownloadChapter
import java.io.File
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.data.desktopComicQueueReading
import com.venera.desktop.gallery.data.desktopComicQueueTaskId
import com.venera.engineprobe.EngineSession
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 详情那一屏的落点载荷（挂在应用根，与画廊的 `selected`、画师的 `artistTarget` 同一族）。
 *
 * [title] 是**卡上那个题名**：`loadInfo` 还没回来时屏上得有个题名，而那时站方题名我们还不晓得。
 * 拿漫画号充数是另一种谎（用户看到的是"这本叫 123"）。
 *
 * [cover] 是**入口那一枚封面地址**（首页卡上的那串 / 历史行里已有的那串 / 种子那趟没有 ⇒ 空串）。
 * 它存在的理由只有一格：写进 `comic_history` 的那一笔要它（对岸的口径是"详情那张优先、入口这枚兜底"，
 * 见 `desktopSessionCover`），而两端共用那张表。
 */
data class DesktopComicTarget(val comicId: String, val title: String, val cover: String)

/** 详情那一屏的三态。`Failed` 吃的是**已经收成一行**的原话（catch 现场负责一行化）。 */
internal sealed interface ComicDetailState {
    data object Loading : ComicDetailState
    data class Failed(val reason: String) : ComicDetailState
    data class Ready(
        val title: String,
        val description: String,
        val episodes: List<EngineSession.Episode>,
        /** `loadInfo` 答复里那一栏 `cover`（缺栏 = 空串）。历史那一笔的封面优先吃它。 */
        val cover: String,
    ) : ComicDetailState
}

/**
 * stdout 那一行：这一屏开得成不成、几章、简介有没有、封面取没取到。
 *
 * 为什么要有一行（而不是只看屏）：详情这一屏的三态里，`Loading` 与 `Failed` 在像素上都是
 * "左上角一行小字"，隔着一张分不出；而"话=0"这一格正是"整本翻不出页"那条老缺陷的形状。
 *
 * `封面=` 那一格只念有无、不念那串地址：它是这一笔历史封面的**第一个来源**，而"站方没给"与
 * "桌面没读"塌成同一格时，落库那行为空就查不出是哪一站（地址本身还可能带签名参数，不进 stdout）。
 */
internal fun desktopComicDetailReading(comicId: String, sourceKey: String, state: ComicDetailState): String {
    val head = "D_详情 源=$sourceKey 号=$comicId"
    return when (state) {
        ComicDetailState.Loading -> "$head 态=等回话"
        is ComicDetailState.Failed -> "$head 态=没成 因=${desktopComicDetailOneLine(state.reason)}"
        is ComicDetailState.Ready -> {
            val desc = state.description.trim()
            // 话号那一串是**给取证抄的**：`--read=<号>/<话号>` 要一枚真话号，而这一屏从前不念它
            // ⇒ 阅读那一屏只能靠屏上点一下才到得了，而鼠标是禁的（手册 §8）。批 30 的 `--ranking=` 同一套办法。
            "$head 态=上屏 话=${state.episodes.size} 简介=${if (desc.isEmpty()) "无" else "${desc.length}字"}" +
                " 封面=${if (state.cover.isBlank()) "无" else "有"} 话号=${desktopComicEpisodeIds(state.episodes)}"
        }
    }
}

/**
 * 前 5 枚话号，占位章念 `-`。
 *
 * 上限 5 是**有意的**：整本刷进 stdout 既没人抄，也会把那一行拉到几百字。
 * `-` 而不是空串：空串在两枚相邻的话号之间看不出来，而"这一章是占位章"正是取页时要换算法的那一格。
 */
internal fun desktopComicEpisodeIds(episodes: List<EngineSession.Episode>): String =
    episodes.take(5).joinToString(",") { if (it.placeholder || it.id.isBlank()) "-" else it.id }

/** 一行化：站方的原话常带 stack 尾巴，那会把读数拆成两行，抓读数的脚本就漏掉后半句。 */
private fun desktopComicDetailOneLine(reason: String): String =
    reason.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').trim().take(240)

/**
 * stdout 那一行：「读到哪儿」那一格是**读到了**、**库里没这一本**、还是**库没读到**。
 *
 * 为什么这一档要有读数（而屏上没有）：第三档在屏上的形状是"那枚胶囊不见了"，
 * 而"这本没读过"与"库没开到"塌成同一格 —— 只有这一行分得出（与 [desktopComicDetailReading] 里
 * `Loading`/`Failed` 都是"左上角一行小字"那一条同一个理由）。
 *
 * `记录=没读到` 那一档故意**不摆胶囊**而只念这一行：那种时候摆「开始阅读」，按下就把
 * `comic_history`（两端共用那张表）里"下次从第 40 页读"写成第一话第 0 页 —— 少一枚钮是可见的慢，
 * 挪走另一端的进度是静默的错。
 */
internal fun desktopComicHistoryProgressLine(comicId: String, outcome: Result<HistoryRecord?>): String {
    val head = "D_续读 号=$comicId"
    val record = outcome.getOrElse {
        return "$head 记录=没读到 因=${it::class.java.simpleName}: ${desktopComicDetailOneLine(it.message ?: "")}"
    }
    return if (record == null) "$head 记录=无" else "$head 记录=有 话=${record.lastChapterIndex} 页=${record.lastPageIndex}"
}

/**
 * `--download=` 那枚种子**命中**时的一句（批 96）。
 *
 * 行号念的是"第几话"（从 1 数起）而不是索引 —— 与续读那一行同一口径：库里存索引、人话从 1 数起，
 * 量过的样例是库里 7 ⇒ 屏上「第 8 页」。取证读数的职责是让人把它跟屏上那一行对上。
 */
internal fun desktopComicDownloadSeedLine(comicId: String, episodeId: String, index: Int, episodeCount: Int): String =
    "D_下载种子 号=$comicId 话=$episodeId 落在=第 ${index + 1} 话 目录=$episodeCount 话"

/** 种子里那个话号在这本的目录里找不到：念一行，不静默（静默会把"我话号打错了"读成"下载坏了"）。 */
internal fun desktopComicDownloadSeedMissLine(comicId: String, episodeId: String, episodeCount: Int): String =
    "D_下载种子 号=$comicId 话=$episodeId 目录=$episodeCount 话 落在=话号不在目录里"

/** 种子点的不是当前开着的那一本（`--comic` 与 `--download` 各写一本时会走到这一格）。 */
internal fun desktopComicDownloadSeedOtherComicLine(seedComicId: String, openedComicId: String): String =
    "D_下载种子 要=$seedComicId 开着=$openedComicId 落在=不是这一本"

/**
 * 这一笔下载状态的**收口**（批 96）。
 *
 * 为什么这一颗存在：包内 b96d1 那一帧量到的正是这里 —— 89 页的那一话在第 63 页被
 * `stream was reset: CANCEL` 掐断，而读数那一行念的是 `页=0/0`：分母从前从 `Result` 里取，
 * 抛出时那一跳连它一起没了（写这一行的时候并不知道会被真网络抓到，屏上念的是"0 页里写了 0 页"这种谎）。
 *
 * `knownPages` 是"页表那一步已经答出来的页数"；那一跳也没成时它是 0，此时念 `0/0` 才是实话 —— **不许编一个分母出来**。
 *
 * `pagesOnDisk` 是进度那一格最后一次递回来的分子（盘上已有几页）：批 100 之前失败那档写死 `written = 0`，
 * 于是第 63 页被掐断那一趟屏上念的是"下载失败"而**不提盘上已经有 62 页** —— 那与 b96d1 那帧的 `页=0/0` 是同一族谎。
 */
internal fun desktopComicChapterOutcome(
    chapterIndex: Int, outcome: Result<Pair<Int, DesktopComicChapterRun>>, knownPages: Int, pagesOnDisk: Int,
): DesktopComicChapterDownload = outcome.fold(
    { (total, run) ->
        when (run) {
            is DesktopComicChapterRun.Completed ->
                DesktopComicChapterDownload(chapterIndex, run.written, total, busy = false, failure = null, pagesOnDisk = pagesOnDisk, cancelled = false, queued = false)
            is DesktopComicChapterRun.Cancelled ->
                DesktopComicChapterDownload(chapterIndex, run.written, total, busy = false, failure = null, pagesOnDisk = pagesOnDisk, cancelled = true, queued = false)
        }
    },
    { cause ->
        DesktopComicChapterDownload(chapterIndex, 0, knownPages, busy = false, failure = oneLineOf(cause), pagesOnDisk = pagesOnDisk, cancelled = false, queued = false)
    },
)

/**
 * 漫画详情那一屏（批 80）：题名 + 简介 + 章节表 + 那枚收藏。
 *
 * ## 为什么引擎句柄是从外面递进来的
 *
 * `EngineSession` 是探索那一屏在 IO 线程里构造的 GraalJS context，它归那一屏的
 * `DisposableEffect` 关（`DesktopDomainNavigationTest` ⑩/⑪ 钉着：应用根不许自己造一颗）。
 * 详情这一屏要的只是"用同一颗问一次 `loadInfo`"，所以句柄由持有者传下来。
 *
 * ## 为什么这一屏不是覆盖层
 *
 * 画廊那张卡点开是覆盖层（半屏还能看见墙），而漫画详情要点开某一话——那是**换一屏**，
 * 与画师单人页同形。覆盖层那条路要处理"压在模糊之上"的一整族问题，这里没有收益。
 */
@Composable
internal fun DesktopComicDetailPane(
    session: EngineSession?,
    target: DesktopComicTarget,
    sourceKey: String,
    favorites: () -> LocalFavoritesManager,
    /**
     * 阅读历史（`comic_history`）那颗件的**懒装载**，与 [favorites] 同一口径（第一次用到才开库）。
     *
     * 必填、不给默认值：漏传的形状是那枚胶囊**恒念「开始阅读」**—— 而屏上看着完全合理
     * （没历史可不就是这么一句），只有按下那一下会把真进度写成第一话第 0 页。
     */
    history: () -> HistoryDao,
    /**
     * 章节下载那一格的落盘根（`数据根/downloads`，与对岸 `ComicStorageRoot` 的默认根同一档）。
     *
     * 必填、不给默认值：漏传的形状是行上那枚「下载」按下去**静默无反馈** —— 屏上按钮还在而什么都不写，
     * 与批 94 那枚"读口漏传就恒念「开始阅读」"同一族（本仓那条：漏传不报错的参数不要用默认值）。
     */
    downloadsRoot: () -> File,
    /**
     * 队列表里当前那一份（第一百零一批）。必填、不给默认值：漏在这一跳上的形状是
     * 「行上按下去真的排上了，而屏上那一行永远念不着」—— 与批 94 那枚"读口漏传就恒念「开始阅读」"同一族。
     *
     * ⚠️ 这一格吃的是**纯数据**而不是队列那颗件本身：展示那一件要能喂着渲成语义树（批 80 立的分家规矩）。
     */
    queueTasks: List<DesktopComicQueueTask>,
    /**
     * `--download=<漫画号>/<话号>` 那枚取证种子（批 96；`null` = 没给）。
     *
     * 必填、不给默认值：漏在这一跳上的形状是"`-Pdownload=…` 递上而屏上什么都没发生"，
     * 与本批要防的"参数打对了而没人接"同形。
     *
     * ⚠️ 它按下去走的是**长按同一颗 runner**（[runChapterDownload] 那一颗，只在沿路留一份），
     * 而不是另开一条"只为取证"的下载路 —— 另开一条，量到的是那条路而用户那一条可以仍是坏的。
     */
    downloadSeed: Pair<String, String>?,
    /**
     * `--download-cancel-after=<N>` 那枚取证种子（批 100）：**这一趟写下 N 页之后自己停手**。
     *
     * 为什么要有这一枚而不是"人去按那枚把手"：取消那一路是**跑在 IO 线程里的一圈页循环**，
     * 起窗手动复现要卡在"正好那一秒"上（而 89 页那一趟要等真网络），量具复现不了；
     * 而它吃的必须仍是**同一颗闸**（下面 `shouldCancel` 那一格），否则量到的是那条路而用户那一条可以仍是坏的。
     */
    downloadCancelAfter: Int?,
    onBack: () -> Unit,
    onOpenEpisode: (DesktopComicReading) -> Unit,
) {
    var state by remember(target.comicId) { mutableStateOf<ComicDetailState>(ComicDetailState.Loading) }
    var favoriteOutcome by remember(target.comicId) { mutableStateOf<DesktopComicFavoriteOutcome?>(null) }
    var progress by remember(target.comicId) { mutableStateOf<HistoryRecord?>(null) }
    var progressUnread by remember(target.comicId) { mutableStateOf(false) }
    var cancelRequested by remember(target.comicId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 第一百零一批：那一屏的状态槽不再自己装"这一趟的结果"，而是从队列表里**挑出这一本的那一笔**。
    // 于是它念得出「排队中」—— 以前那一档根本没有，第二笔会被一枚早退闸静默吞掉而屏上一字不念。
    val activeTask = desktopComicQueueTaskOf(queueTasks, sourceKey, target.comicId)
    val download = desktopComicChapterDownloadOf(activeTask)
    // key 带 session：装载没完成时这一格先摆"等回话"，句柄到手那一刻 effect 重跑并真去问。
    LaunchedEffect(session, target.comicId) {
        val engine = session
        if (engine == null) {
            state = ComicDetailState.Loading
            return@LaunchedEffect
        }
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                engine.details(target.comicId).let {
                    ComicDetailState.Ready(it.title, it.description, it.episodes, it.cover)
                }
            }
        }
        // 「读到哪儿」在目录上屏**之前**问完：Ready 那一支一摆就要么带着记录、要么带着"没读到"，
        // 中间不许出现"屏上念着「开始阅读」而库里其实有"那一档（批 94 立的正是这一条）。
        // 两次各开一个 IO 跳而不是并成一跳：目录那一跳是 GraalJS 同步往返，别让 SQLite 排在它前面。
        val resume = outcome.getOrNull()?.let {
            withContext(Dispatchers.IO) { runCatching { history().getHistory(target.comicId) } }
        }
        if (resume != null) {
            progress = resume.getOrNull()
            progressUnread = resume.isFailure
            println(desktopComicHistoryProgressLine(target.comicId, resume))
        }
        state = outcome.fold(
            { it },
            { ComicDetailState.Failed("${it::class.java.simpleName}: ${it.message?.take(240)}") },
        )
        println(desktopComicDetailReading(target.comicId, sourceKey, state))
    }
    // 第一百零一批：这一屏不再自己跑那一圈 —— 长按与取证种子都只把「排进队列」这一枚意图交出去，
    // 真正跑的那一圈在域那一层（`DesktopComicQueueRunner.kt`）。于是换屏、退详情都不会把它掐死，
    // 而"第二笔被静默吞掉"那一格有了正解：它排得上队，而屏上念得出它排在第几。
    val enqueueChapterDownload: (Int) -> Unit = { index ->
        val ready = state as? ComicDetailState.Ready
        val episode = ready?.episodes?.getOrNull(index)
        if (ready != null && episode != null) {
            cancelRequested = false
            DesktopComicDownloadQueue.enqueue(
                downloadsRoot(), sourceKey, target.comicId, currentTitle(state, target), ready.cover,
                episode.id, episode.name, index, episode.placeholder,
            )
            // 种子那一档扳的是与屏上那枚把手**同一颗闸**（页上限记在队列那一侧）：另开一条"只为取证"的
            // 取消路，量到的是那条路而用户那一条可以仍是坏的（批 98 那条纪律的同一形）。
            val cancelAfter = downloadCancelAfter
            if (cancelAfter != null) {
                DesktopComicDownloadQueue.armPageCap(desktopComicQueueTaskId(sourceKey, target.comicId, episode.id), cancelAfter)
            }
            println(desktopComicQueueReading(DesktopComicDownloadQueue.tasks.value, DesktopComicDownloadQueue.asideName))
        }
    }
    // 那枚「取消」把手按下去只是把闸扳过去（协作式）：正在飞的那一笔请求掐不掉，所以屏上念"正在停手"而不是"已停止"。
    val stopActiveTask: () -> Unit = {
        cancelRequested = true
        val active = activeTask
        if (active != null) DesktopComicDownloadQueue.requestStop(active.taskId)
    }
    // 种子那一笔排在目录回来**之后**（钥匙里有 `state`）：那一话还没进章表时按下,只会得到一句
    // 「话号不在目录里」—— 那是把"我起窗太早"读成"这本没有这一话"。
    // 三档落空（不是这一本 / 话号不在目录 / 命中）都出声：静默就是把"参数打错了"读成"下载坏了"。
    LaunchedEffect(state, downloadSeed, target.comicId) {
        val seed = downloadSeed ?: return@LaunchedEffect
        val ready = state as? ComicDetailState.Ready ?: return@LaunchedEffect
        // 已经排进队的那一笔不再重复入队：这一枚 effect 会随 state 再变一次，两遍来回就是两趟入队。
        val queueTaskOfThisEpisode = queueTasks.firstOrNull { it.taskId == desktopComicQueueTaskId(sourceKey, target.comicId, seed.second) }
        if (queueTaskOfThisEpisode != null) return@LaunchedEffect
        if (seed.first != target.comicId) {
            println(desktopComicDownloadSeedOtherComicLine(seed.first, target.comicId))
            return@LaunchedEffect
        }
        val index = ready.episodes.indexOfFirst { it.id == seed.second }
        if (index < 0) {
            println(desktopComicDownloadSeedMissLine(seed.first, seed.second, ready.episodes.size))
            return@LaunchedEffect
        }
        println(desktopComicDownloadSeedLine(seed.first, seed.second, index, ready.episodes.size))
        enqueueChapterDownload(index)
    }
    DesktopComicDetailContent(
        state = state,
        target = target,
        favoriteOutcome = favoriteOutcome,
        progress = progress,
        progressUnread = progressUnread,
        onBack = onBack,
        onFavorite = {
            // 与卡角那一枚吃同一颗动作与同一份三态措辞（两处各写一句就会念成两个名字）。
            val card = EngineSession.ComicCard(currentTitle(state, target), target.comicId, "")
            scope.launch { favoriteOutcome = desktopAddComicFavorite(favorites, sourceKey, card) }
        },
        onOpenEpisode = onOpenEpisode,
        download = download,
        cancelRequested = cancelRequested,
        onDownloadEpisode = { index -> enqueueChapterDownload(index) },
        onCancelDownload = stopActiveTask,
    )
}

/** 站方那句报错常带 stack 尾巴，上屏与读数都要先一行化（与 `desktopComicDetailOneLine` 同一口径）。 */
private fun oneLineOf(cause: Throwable): String =
    "${cause::class.java.simpleName}: ${(cause.message ?: "").replace('\n', ' ').replace('\r', ' ').trim().take(160)}"

/** 长按那一枚动作的说明（桌面没有右键，动作没有可见把手 ⇒ 句子必须上屏，本仓那条口径）。 */
internal const val DESKTOP_CHAPTER_DOWNLOAD_HINT: String = "长按某一话可把它存到本机"

/**
 * 那一行状态的人话（屏上只摆这一句，`D_下载` / `D_下载进度` / `D_下载取消` 三行只进 stdout —— 取证读数不进界面）。
 *
 * 五档分开写而不是拼一句：`written == 0` 与 `written > 0` 在屏上必须念不同的话 ——
 * 前者是"这一话本机早有了"（对岸那句"点一下就直接重下一遍"挡的就是这一档），
 * 后者是"这一趟真写了页"。合成一句就会把"没干活"念成"干了活"。
 *
 * 批 100 添的两档：`busy` 那一档现在带得上**进度**（从前只有"正在写本机…"一句，89 页那一趟从头到尾不吭声），
 * 而 `cancelled` 那一档念的是"停在第几页 + 下次接着补" —— 它**不许**塌进"已写下 N 页"那一档，
 * 因为那一趟没干完活（runner 自己交回的是 `Cancelled`，见 `DesktopComicChapterRun`）。
 */
internal fun desktopComicChapterStatusText(state: DesktopComicChapterDownload): String = when {
    state.queued -> DESKTOP_CHAPTER_DOWNLOAD_QUEUED_HINT
    state.busy -> "正在写本机… " + state.pagesOnDisk + "/" + state.total + " 页"
    state.failure != null -> "下载失败：" + state.failure + "（本机已有 " + state.pagesOnDisk + " 页）"
    state.cancelled -> "已停在 " + state.pagesOnDisk + "/" + state.total + " 页 · 下次从这里接着补"
    state.written == 0 -> "这一话本机已有 " + state.total + " 页"
    else -> "已写下 " + state.written + " 页"
}

/**
 * 排队那一档的人话（批 101）。
 *
 * ⚠️ 那句里「回到漫画域就接着跑」是**定档 C 的那条边界**写在屏上：执行器挂在漫画域那一层，
 * 退出这一域时那一圈泵会被协作式掐掉，而任务表与已写的页都留在盘上。
 * 不许写成「已暂停」—— 那听上去像有一枚"继续"的把手，而它今天没有。
 */
internal const val DESKTOP_CHAPTER_DOWNLOAD_QUEUED_HINT: String = "排着队 · 回到漫画域就接着跑"

/**
 * 这一本当前那一笔（**正在跑的优先，其次排着的，最后才是这一本最近的那一笔**）。
 *
 * 为什么要三档而不是只看"在跑的那一笔"：用户按完第二话会退到列表去看别的，
 * 回来时那一笔已经跑完了 —— 只看 RUNNING 就得到"屏上一个字都不念"，而盘上明明多了 89 页。
 */
internal fun desktopComicQueueTaskOf(
    tasks: List<DesktopComicQueueTask>, sourceKey: String, comicId: String,
): DesktopComicQueueTask? {
    val mine = tasks.filter { it.sourceKey == sourceKey && it.comicId == comicId }
    return mine.firstOrNull { it.status == DesktopComicQueueStatus.RUNNING }
        ?: mine.firstOrNull { it.status == DesktopComicQueueStatus.PENDING }
        ?: mine.lastOrNull()
}

/**
 * 把队列里那一笔映射成那一行状态（**纯映射**，展示那一件不收引擎句柄也不收任务表本身）。
 *
 * ⚠️ `written = 0` 那一格是刻意的：`DONE` 那一档映射过去吃的是既有的「这一话本机已有 N 页」那一句 ——
 * 队列里没有"这一趟写了几页"那一格（那是批 100 内存里那一趟的口径），硬造一枚就是假数据。
 */
internal fun desktopComicChapterDownloadOf(task: DesktopComicQueueTask?): DesktopComicChapterDownload? {
    task ?: return null
    return when (task.status) {
        DesktopComicQueueStatus.PENDING -> DesktopComicChapterDownload(
            task.chapterOrder, written = 0, total = task.totalPages, busy = false,
            failure = null, pagesOnDisk = task.downloadedPages, cancelled = false, queued = true,
        )

        DesktopComicQueueStatus.RUNNING -> DesktopComicChapterDownload(
            task.chapterOrder, written = 0, total = task.totalPages, busy = true,
            failure = null, pagesOnDisk = task.downloadedPages, cancelled = false, queued = false,
        )

        DesktopComicQueueStatus.DONE -> DesktopComicChapterDownload(
            task.chapterOrder, written = 0, total = task.downloadedPages, busy = false,
            failure = null, pagesOnDisk = task.downloadedPages, cancelled = false, queued = false,
        )

        DesktopComicQueueStatus.STOPPED -> DesktopComicChapterDownload(
            task.chapterOrder, written = 0, total = task.totalPages, busy = false,
            failure = null, pagesOnDisk = task.downloadedPages, cancelled = true, queued = false,
        )

        DesktopComicQueueStatus.FAILED -> DesktopComicChapterDownload(
            task.chapterOrder, written = 0, total = task.totalPages, busy = false,
            failure = task.error, pagesOnDisk = task.downloadedPages, cancelled = false, queued = false,
        )
    }
}

/** 那一枚把手的人话（批 100）：桌面没有右键，动作没有可见把手就没人找得到（本仓那条口径）。 */
internal const val DESKTOP_CHAPTER_DOWNLOAD_CANCEL_LABEL: String = "取消"

/**
 * 按下去之后屏上先念的那一句（批 100）：**不许**写成"已停止" —— [DESKTOP_CHAPTER_DOWNLOAD_CANCEL_LABEL]
 * 那枚把手停的是"下一页不再取"，而正在飞的那一笔请求掐不掉（协作式取消）。
 */
internal const val DESKTOP_CHAPTER_DOWNLOAD_CANCELLED_HINT: String = "正在停手…"

/**
 * 一整话下载的那点状态（**纯数据**，展示那一件只吃它）。
 *
 * 为什么把 `busy` 与 `failure` 分两格而不是一个可空串：屏上那一行在"正在下"与"下完"两档念的是不同的话，
 * 而失败那一档还要把按钮留成可再按（对岸那一判是"点一下就直接重下一遍"，桌面这一批让它只补缺的那几页）。
 *
 * 批 100 添的两格：`pagesOnDisk` 是**盘上已有**的页数（进度那一格递回来的，失败那档也要它 ——
 * 第 63 页被掐断时说"本机已有 62 页"才是实话），`cancelled` 是 runner 自己交回的那一档而不是调用点猜的。
 *
 * 批 101 添的那一格：`queued` 是**还没轮到它跑**那一档。它必须与 `busy` 分开 ——
 * 合成一格就得到"正在写本机… 0/0 页"这种既没干活又宣称在干活的话（本仓那条「不许静默交错」）。
 */
internal data class DesktopComicChapterDownload(
    val chapterIndex: Int,
    val written: Int,
    val total: Int,
    val busy: Boolean,
    val failure: String?,
    val pagesOnDisk: Int,
    val cancelled: Boolean,
    val queued: Boolean,
)

/**
 * 纯展示那一件：吃 [ComicDetailState] 这块纯数据，**不发请求、不读盘、不要引擎句柄**。
 *
 * 拆开的理由是这一件能被喂着离屏渲成语义树来验（`DesktopComicDetailSemanticsTest`）——
 * 装载那一件要真 GraalJS 与真源，测试里渲不出来；而"屏上那句话到底变没变"这一层
 * 从前只有起窗拍帧一条路（一批 3~5 分钟），现在是一条断言几秒。
 */
@Composable
internal fun DesktopComicDetailContent(
    state: ComicDetailState,
    target: DesktopComicTarget,
    favoriteOutcome: DesktopComicFavoriteOutcome?,
    /**
     * 库里那一本读到哪儿（`null` = 这一本没读过）。必填、不给默认值 —— 理由见装载那一件里那句注释。
     *
     * 吃的是 [HistoryRecord] 这块**纯数据**而不是 DAO 句柄：展示层一吃要开库的那件东西，
     * 语义树那一层就渲不出来了（与 `EngineSession` 那一条同一个规矩，批 80 立的）。
     */
    progress: HistoryRecord?,
    /** 库**没读到**那一档（开库失败）：那一档两枚胶囊都不摆，只由 `D_续读` 那一行念原因，见 [DesktopComicDetailPane] 里那段。 */
    progressUnread: Boolean,
    /**
     * 某一话的下载状态（`null` = 这一屏还没下过任何话）。必填、不给默认值：
     * 漏传的形状是那枚「下载」按下去屏上**一句反馈都没有** —— 与"按钮在而动作没接"那一族同形。
     */
    download: DesktopComicChapterDownload?,
    /**
     * 那枚「取消」按下去没有（批 100）。必填、不给默认值：漏传的形状是按下去屏上**一个字都不变**，
     * 而协作式取消"要等下一页边界"这一格本来就需要一句反馈，否则量不出它接上了。
     */
    cancelRequested: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    /**
     * 某一话点开（进阅读那一屏）。必填、不给默认值：漏传的形状是一行**摆着能点的样子而按不动**，
     * 而批 81 之前这里正是"屏上写着「第1话」而按下去什么都不发生"。
     * 交回的是纯数据 [DesktopComicReading] 而不是引擎的那个 `Episode` —— 展示层一吃引擎侧的形状，
     * 语义树那一层就渲不出来了（`DesktopComicDetailWiringTest` ③ 钉着这一条）。
     */
    onOpenEpisode: (DesktopComicReading) -> Unit,
    /**
     * 把某一话整话写到本机（交回的是**行号**而不是 `Episode`：同一句理由 —— 展示层不吃引擎侧形状）。
     * 行号在装载那一件里再换成"这一行的话号与标题"，所以这一格在语义树里能按得动（`DesktopComicDetailSemanticsTest` 量得到）。
     */
    onDownloadEpisode: (Int) -> Unit,
    /**
     * 扳那一颗取消闸（批 100）。必填、不给默认值：漏传的形状是那枚把手**摆着而按下去什么都没接**，
     * 正是本仓「灰行不许留在屏上」要防的那一形。它**不许**另开一条取消路 —— 与 `--download-cancel-after=`
     * 扳的是同一格 `shouldCancel`（见 [DesktopComicDetailPane] 里那一颗 runner）。
     */
    onCancelDownload: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("← 返回列表", color = DesktopTheme.TextSecondary, fontSize = 11.sp)
            }
            Text(
                " ${currentTitle(state, target)}",
                color = DesktopTheme.TextPrimary,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(
                Modifier
                    .padding(start = 8.dp)
                    .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                    .clickable(onClick = onFavorite)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("收藏", color = DesktopTheme.TextSecondary, fontSize = 11.sp)
            }
        }
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
        when (val current = state) {
            ComicDetailState.Loading ->
                Text("正在问源站这本的目录…", color = DesktopTheme.TextTertiary, fontSize = 12.sp)

            is ComicDetailState.Failed ->
                Text("没问到目录：${current.reason}", color = DesktopTheme.AccentBeni, fontSize = 12.sp)

            is ComicDetailState.Ready -> {
                // 简介：源不给就是一行都不摆（摆"暂无简介"是替源站撒一句它没说过的话）。
                current.description.trim().takeIf { it.isNotEmpty() }?.let { description ->
                    Text(
                        description,
                        color = DesktopTheme.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // 先取进局部：`progress` 是委托属性，智能转换过不了那句 `!= null`（编译面就红）。
                val record = progress
                // 进度线：没有分母的百分比是假数据，所以话数为 0 时那一档给的是 0f，下面据此不摆线。
                val readProgress = record?.let { desktopComicReadProgress(it.lastChapterIndex, current.episodes.size) } ?: 0f
                // 「开始/继续阅读」那一枚主胶囊（批 94，对岸 `feature/ComicDetailScreen.kt:550-615` 那一格）。
                // 按下走的是**记录那一话那一页**：对岸那枚是半接线（屏上念「继续 第 8 页」而动作开的是
                // 第一章第 0 页，见 `desktopComicResumeReading` 的注释），本批不许照抄那一半。
                if (!progressUnread) {
                    // 填色走自家那一档而**不挂库那条链**：`AccentButton` 的填充读 `Colors.fillAccent`，
                    // 而 `darkColors(accent = 藤紫)` 吃不到我们递进去的那枚 —— 库里那张预置表只有一颗键（`#0078D4`）
                    // 而回落就是它自己那份，六枚候选量到同一枚蓝（四条判据见 `DesktopAccentFillTest`；
                    // 批 94 的包内图证抓到的是天蓝）。
                    // 底与文字两档都取自稿：`--accent-fill` = `#5B47A8`、`--on-accent` = `#FFFFFF`
                    //（后者与既有 [DesktopTheme.TextPrimary] 同值 ⇒ 不新增色值）；层级差用**字号**给，不用 alpha。
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(DesktopTheme.AccentFill, RoundedCornerShape(4.dp))
                            .clickable {
                                val resume = desktopComicResumeReading(record, current.episodes.map { episode -> episode.id })
                                if (resume != null) {
                                    val episode = current.episodes[resume.first]
                                    onOpenEpisode(
                                        desktopComicReadingOf(
                                            target, episode, resume.first,
                                            startPage = resume.second, detailCover = current.cover,
                                        ),
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            desktopComicReadButtonLabel(record != null),
                            color = DesktopTheme.TextPrimary,
                            fontSize = 13.sp,
                        )
                        if (record != null) {
                            Text(
                                desktopComicContinueCaption(record.lastChapterTitle, record.lastPageIndex),
                                color = DesktopTheme.TextPrimary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (readProgress > 0f) {
                        // 那条线贴在胶囊底边：2.dp 取对岸 `tokens.spacing.space1`（`ui/tokens/Spacing.kt:20`），
                        // 不新造一档。底与填充走既有两档（SurfaceRaised / AccentFuji），没有新的色值字面量。
                        Box(Modifier.fillMaxWidth().height(2.dp).background(DesktopTheme.SurfaceRaised)) {
                            Box(Modifier.fillMaxWidth(readProgress).height(2.dp).background(DesktopTheme.AccentFuji))
                        }
                    }
                }
                Text(
                    "共 ${current.episodes.size} 话",
                    color = DesktopTheme.TextTertiary,
                    fontSize = 12.sp,
                )
                // 长按那一枚动作没有可见把手（桌面没有右键），所以那句说明必须上屏 —— 与卡列那一条同一口径。
                Text(
                    DESKTOP_CHAPTER_DOWNLOAD_HINT,
                    color = DesktopTheme.TextTertiary,
                    fontSize = 11.sp,
                )
                // LazyColumn 而不套 verticalScroll：`LazyVerticalGrid × verticalScroll` 那一条整屏崩的
                // 不变式对这一族同样成立，这里靠 weight 拿到有界高度。
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(current.episodes.size) { index ->
                        val episode = current.episodes[index]
                        // 载荷在这一行就拼齐（起点与封面都是**这一处**的决定，见下面那两句注释）。
                        val reading = desktopComicReadingOf(target, episode, index, startPage = 0, detailCover = current.cover)
                        // 与对岸 `:1197` 同一格：`lastChapterIndex` 存的正是"这一话在章表里的第几行"，
                        // 于是描边、主色、加粗、那枚「上次读到这里」四样都跟着它（不重排、不换成话号 —— 两端同一口径）。
                        val isResumeChapter = record?.lastChapterIndex == index
                        // 整行点得动（批 81）：从前这一行是死的 —— 屏上写着「第1话」而按下去什么都不发生。
                        // `clickable` 排在 `padding` 之前，那一圈内衬因此也算进命中区（与「重取」那枚同一口径）。
                        Column(
                            Modifier
                                .fillMaxWidth()
                                // 未读到的那一行吃透明描边而不是"没有描边"：那一格会少 1.dp，整列行高跟着跳。
                                .border(
                                    DesktopGalleryMetrics.hairline,
                                    if (isResumeChapter) DesktopTheme.AccentFuji else Color.Transparent,
                                    RoundedCornerShape(4.dp),
                                )
                                .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                                // 起点写在这里而不是由默认值补：那一列话的各行按下仍是**从该话开头**读
                                //（照对岸 `:1206 launchChapter(ch.id, ch.title, actualIdx)`，它也不带页号）；
                                // 只有上面那枚胶囊才续读 —— 批 94 接的是"胶囊念什么就按什么"，不是给行加语义。
                                //
                                // 第二枚动作走**长按**而不是行里再套一枚可点的：批 96 第一版就是这么写的，
                                // 结果那枚嵌进的 `clickable` 把整行的点击吃掉了 —— 三颗既有语义树判据当场红
                                // （"按一行该恰好落一次"变成 0 次）。那不是测试噪音，是屏上真按不动。
                                // 桌面这一族早有口径：`combinedClickable` 有 `onLongClick` 而没有 `onRightClick`
                                // ⇒ 第二枚动作只能挂长按，而那两句说明必须上屏（见下面 `DESKTOP_CHAPTER_DOWNLOAD_HINT`）。
                                .combinedClickable(
                                    onClick = { onOpenEpisode(reading) },
                                    onLongClick = { onDownloadEpisode(index) },
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    desktopReaderEpisodeLabel(episode.name, episode.placeholder, episode.id),
                                    color = if (isResumeChapter) DesktopTheme.AccentFuji else DesktopTheme.TextPrimary,
                                    fontSize = 12.sp,
                                )
                                if (isResumeChapter) {
                                    // 那枚标记独立成一句而不与题名拼串：对岸是 `Spacer(space2)` + 图标
                                    //（`ComicDetailScreen.kt:1218-1224`），这里换不成图标就留那句人话，
                                    // 4.dp = 对岸 `tokens.spacing.space2`（`ui/tokens/Spacing.kt:21`）。
                                    Text(
                                        DESKTOP_HISTORY_CHAPTER_MARK_DESCRIPTION,
                                        color = DesktopTheme.AccentFuji,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                            val chapterDownload = download
                            if (chapterDownload != null && chapterDownload.chapterIndex == index) {
                                // 那一行状态走屏上的人话，`D_下载` 那一行只进 stdout（取证读数不进界面，本仓那条口径）。
                                Text(
                                    desktopComicChapterStatusText(chapterDownload),
                                    color = if (chapterDownload.failure == null) DesktopTheme.TextTertiary else DesktopTheme.AccentBeni,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                // 取消那一枚把手**只在跑着的时候摆**（批 100）：跑完再摆一枚按下去什么都不会发生的钮，
                                // 就是本仓那条「灰行不许留在屏上」的形状。
                                if (chapterDownload.busy) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(onClick = onCancelDownload) {
                                            Text(
                                                if (cancelRequested) DESKTOP_CHAPTER_DOWNLOAD_CANCELLED_HINT else DESKTOP_CHAPTER_DOWNLOAD_CANCEL_LABEL,
                                                fontSize = 11.sp,
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
    }
}

/** 站方题名没回来之前先用卡上那个；回来了以站方为准（空答复不算"题名是空串"）。 */
private fun currentTitle(state: ComicDetailState, target: DesktopComicTarget): String =
    (state as? ComicDetailState.Ready)?.title?.takeIf { it.isNotBlank() } ?: target.title
