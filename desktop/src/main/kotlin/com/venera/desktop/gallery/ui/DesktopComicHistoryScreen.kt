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
