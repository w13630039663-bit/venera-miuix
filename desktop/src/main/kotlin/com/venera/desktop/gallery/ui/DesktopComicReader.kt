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
