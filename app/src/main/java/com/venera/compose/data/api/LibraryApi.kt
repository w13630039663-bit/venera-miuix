package com.venera.compose.data.api

import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.stats.ComicStatItem
import com.venera.compose.stats.DailyTrendItem
import com.venera.compose.stats.ReadingStatsSummary
import com.venera.compose.stats.TagStats
import kotlinx.coroutines.flow.StateFlow

/**
 * 阅读历史的读写口。
 *
 * 为什么要这一颗：`reader/VeneraReaderScreen.kt:445` 今天在一层 `LaunchedEffect` +
 * `withContext(NonCancellable + Dispatchers.IO)` 里**直调 DAO** —— 审计
 * `responsibility-dependency-audit-2026-10-03.md` §二 第 8 行点名的第一处，§七 又写明
 * 「动它必然改调度时序」。所以这里只收「取用」这一件事：协程上下文、`NonCancellable`、
 * effect 的 key 全部留在调用点，契约只做一行转发。
 *
 * 不收 `getHistory(comicId)` 与 `clearAll()`：UI 目录零命中（复核：
 * `grep -rn "getHistory\|\.clearAll()" app/src/main/java/com/venera/compose/{feature,gallery/ui,reader,components}`）。
 */
interface ReadingHistory {

    val historyFlow: StateFlow<List<HistoryRecord>>

    /** 重读一遍库、刷新上面那条流。 */
    fun refresh()

    suspend fun saveHistory(record: HistoryRecord)

    suspend fun deleteHistory(comicId: String, sourceName: String)

    suspend fun batchDeleteHistory(records: List<HistoryRecord>)
}

/**
 * 阅读统计。契约里零类型牵连 —— 五个成员的参数全是原始类型 / `List<String>`，
 * 返回的四个类型都住在 `stats/ReadingStatsModels.kt`（纯 data class）。
 *
 * 消费面只有三处：`feature/StatsScreen.kt`、`feature/HomeViewModel.kt:209`（题材统计）、
 * `reader/VeneraReaderScreen.kt:306`（读完记一笔）。
 */
interface ReadingStats {

    suspend fun recordSession(
        comicId: String,
        comicTitle: String,
        sourceName: String,
        tags: List<String> = emptyList(),
        chapterTitle: String = "",
        pagesRead: Int = 1,
        durationSeconds: Long = 0,
    )

    suspend fun summary(): ReadingStatsSummary

    suspend fun recent14DaysTrend(): List<DailyTrendItem>

    suspend fun topComics(limit: Int = 10): List<ComicStatItem>

    suspend fun tagStats(days: Int): TagStats
}
