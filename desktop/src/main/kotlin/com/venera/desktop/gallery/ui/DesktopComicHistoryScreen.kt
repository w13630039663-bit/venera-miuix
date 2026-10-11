package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.data.db.HistoryRecord
import io.github.composefluent.component.Text

/**
 * 批 82 · 阅读历史那一屏（读侧）。
 *
 * ## 三处措辞与一处算式都抄对岸，不自己定
 *
 * 空态那两句抄 `feature/HistoryScreen.kt` 的 `VeneraEmptyView(title = …, message = …)`，
 * 进度那一句抄 `feature/HistoryViewModel.kt:95-105` 的 `progressDescription()`。
 * 那颗扩展函数住在 `feature/`，**不在桌面编译面上**（shared 的只有 `data/platform` 与 `data/db` 那两片），
 * 所以这里只能抄一份再让判据回读原件钉住"没漂"。
 *
 * ## 一屏一源这一档（批 82.8 改口径）
 *
 * 漫画域这一趟只装载一颗源，而**那颗源运行期可换**（源管理那一屏「选它」，批 83）—— 旧那句写的
 * 是"换源要重启"，它在批 83 之后就成了假话，屏上那句说明也跟着改了。
 *
 * 更要紧的一格：**两端往库里写的东西本来就不同轨**。手机侧 `VeneraReaderScreen.kt:445` 往
 * `source_name` 写的是**显示名**（`session.sourceName`），桌面侧 `DesktopComicHistory.kt:51` 写的是**键**。
 * 于是从前"只认键"的判法把手机记的那一行摆成按不动的样子。现在与对岸 `resolveSourceKey` 同一条口径：
 * **键、或那颗键对应的显示名，任一 ignoreCase 命中即算当前源**（对岸 `ComicDetailViewModel.kt:961-965`，
 * 判据把对岸那两枚 `equals(…, ignoreCase = true)` 钉住了，它一改这条先红）。
 *
 * ⚠️ 一处**刻意不与对岸同轨**：对岸解析不出时退到"当前那颗源"（它的语境是"给这本收藏"，退到当前源才对），
 * 而这一屏的语境是"筛出属于当前源的行" —— 退到当前源会把别源的行一起点亮，那是拿 A 站凭据问 B 站那一族。
 * 所以这里解析不出就是按不动，并把为什么念到屏上。
 *
 * ## 看得见与看不见的失败
 *
 * `note` 只有一种来源：`config.history()` 那颗件**建不起来**（端口没装、库文件开不了）。
 * 而"建起来了但那条 SELECT 抛了"落在 DAO 自己那根 IO 协程里（`HistoryDao.refresh` 是 `scope.launch`），
 * 屏上拿不到 —— 那种失败今天只会在 stdout 上响。这一格没验，别把它当成覆盖了。
 */

/** 空态那两句（抄对岸，判据回读 `HistoryScreen.kt` 那一块）。 */
internal const val DESKTOP_HISTORY_EMPTY_TITLE: String = "还没有阅读记录"
internal const val DESKTOP_HISTORY_EMPTY_MESSAGE: String = "去随便翻两页吧"

/** 异源那一行的说明。**必须上屏**：一枚按不动的卡与一句"为什么按不动"是两件事。 */
internal const val DESKTOP_HISTORY_OTHER_SOURCE_NOTE: String = "这一行是别的源记的，去源管理换过来才打得开"

/**
 * 「读到第几话第几页」那一句。算式逐字照对岸 `progressDescription()`：
 * 两处都从 1 数起（人话），各带一枚 `>= 1` 的门，两截之间 ` - `，都关着时整句是空串。
 */
internal fun desktopComicHistoryProgressText(record: HistoryRecord): String {
    val ep = record.lastChapterIndex + 1
    val page = record.lastPageIndex + 1
    return buildString {
        if (ep >= 1) append("第 $ep 话")
        if (page >= 1) {
            if (isNotEmpty()) append(" - ")
            append("第 $page 页")
        }
    }
}

/**
 * 这一行今天开不开得动：只认与这一趟同一个源的那些行。
 *
 * 口径与对岸 `resolveSourceKey` 同轨 —— 库里那一列**两端写的不是同一种东西**（手机写显示名、桌面写键），
 * 所以键与显示名任一 ignoreCase 命中都算同一颗源；两档都不中才是别源的。
 * `sourceName` 是那颗源的显示名，名字表没命中时递空串：空串**不放行**（先挡库里那一行为空，
 * 再让空显示名去比会把"没名字"读成"什么行都匹配"）。
 */
internal fun desktopComicHistoryRowCanOpen(record: HistoryRecord, sourceKey: String, sourceName: String): Boolean {
    val row = record.sourceName
    if (row.isBlank()) return false
    return row.equals(sourceKey, ignoreCase = true) || row.equals(sourceName, ignoreCase = true)
}

/** stdout 那一行：这一屏摆了几行、库开不开得了。行数由调用方从真状态现取。 */
internal fun desktopComicHistoryScreenReading(sourceKey: String, sourceName: String, rowCount: Int, note: String?): String {
    val head = "D_历史 屏 源=$sourceKey 名=$sourceName 行=$rowCount"
    return if (note == null) head else "$head 因=${note.replace('\n', ' ').replace('\r', ' ')}"
}

/**
 * 阅读历史那一屏的**展示**件：吃现成的行表，不开库、不查表（与详情/阅读那两屏同一族拆法，
 * 所以这一层能离屏渲成语义树来验）。
 */
@Composable
internal fun DesktopComicHistoryContent(
    sourceKey: String,
    /** 这颗源的显示名（名字表没命中时是空串）—— 库里那一列两端写的不是同一种东西，见文件头那一节。 */
    sourceName: String,
    records: List<HistoryRecord>,
    note: String?,
    onOpen: (DesktopComicTarget) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("阅读历史", color = DesktopTheme.TextPrimary, fontSize = 18.sp)
        Text(
            "这一份落在本机 SQLite（comic_history），与手机侧那份是同一张表的形状；每一行记的是那一本读到哪儿。",
            color = DesktopTheme.TextTertiary,
            fontSize = 11.sp,
        )
        val failure = note
        when {
            failure != null -> Text("历史读不出来：$failure", color = DesktopTheme.AccentBeni, fontSize = 12.sp)
            records.isEmpty() -> {
                Text(DESKTOP_HISTORY_EMPTY_TITLE, color = DesktopTheme.TextSecondary, fontSize = 13.sp)
                Text(DESKTOP_HISTORY_EMPTY_MESSAGE, color = DesktopTheme.TextTertiary, fontSize = 11.sp)
            }

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records.size) { index ->
                    val rec = records[index]
                    val progress = desktopComicHistoryProgressText(rec)
                    val canOpen = desktopComicHistoryRowCanOpen(rec, sourceKey, sourceName)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                            .then(if (canOpen) Modifier.clickable { onOpen(DesktopComicTarget(rec.comicId, rec.title, rec.coverUrl)) } else Modifier)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            rec.title,
                            color = DesktopTheme.TextPrimary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (progress.isNotEmpty()) {
                            Text(progress, color = DesktopTheme.AccentBeni, fontSize = 11.sp, maxLines = 2)
                        }
                        // 源名永远念（点不动的那一行更要念出它是谁的源），后面跟那一句为什么点不动。
                        Text(
                            if (canOpen) rec.sourceName else "${rec.sourceName} · $DESKTOP_HISTORY_OTHER_SOURCE_NOTE",
                            color = DesktopTheme.TextTertiary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 阅读历史那一屏的**装载**件：第一次用到才开库（`config.history()` 是懒装载那一颗），
 * 然后盯着那颗件推出的 flow —— 阅读那一屏每写一笔，这一屏的行就跟着动（`saveHistory` 末尾自己 `refresh()`）。
 */
@Composable
internal fun DesktopComicHistoryPane(
    config: DesktopComicEngineConfig,
    /** 这颗源的显示名，由域 body 从源管理那张表里取（空串 = 名字表没命中）。 */
    sourceName: String,
    onOpenComic: (DesktopComicTarget) -> Unit,
) {
    val dao = remember(config) { runCatching { config.history() } }
    var records by remember { mutableStateOf<List<HistoryRecord>>(emptyList()) }
    LaunchedEffect(dao) {
        dao.getOrNull()?.historyFlow?.collect { rows -> records = rows }
    }
    val note = dao.exceptionOrNull()?.let { "${it::class.java.simpleName}: ${it.message?.take(200)}" }
    LaunchedEffect(records.size, note) {
        println(desktopComicHistoryScreenReading(config.sourceKey, sourceName, records.size, note))
    }
    DesktopComicHistoryContent(config.sourceKey, sourceName, records, note, onOpenComic)
}
