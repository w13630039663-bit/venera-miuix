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
 * 不收 `getHistory(comicId)` 与 `refresh()`：UI 目录零命中（复核式
 * `grep -rn "\.refresh()" app/src/main/java/com/venera/compose/{feature,gallery/ui,reader,components}`
 * 只打到 VM 自己那颗 `vm.refresh()`，没有一处打在 DAO 句柄上；`refresh` 是 `saveHistory` /
 * `clearAll` 内部自己调的那一步）。
 *
 * ⚠️ B0 这行原写「`clearAll()` 也是零命中」，**那句是错的**：`feature/HistoryViewModel.kt:68`
 * 的「清空历史」就在调它，当时按接收者名 `historyDao` 扫、漏掉了那颗文件里名叫 `dao` 的句柄。
 * B4 把它补进契约，同时把这条复核式改成按成员名扫（不再依赖句柄命名）。
 */
interface ReadingHistory {

    val historyFlow: StateFlow<List<HistoryRecord>>

    suspend fun saveHistory(record: HistoryRecord)

    suspend fun deleteHistory(comicId: String, sourceName: String)

    suspend fun batchDeleteHistory(records: List<HistoryRecord>)

    /** 整表清空（历史页「清空历史」那一路）；清完实现自己会重读一遍库。 */
    suspend fun clearAll()
}

/**
 * 阅读统计。契约里零类型牵连 —— 五个成员的参数全是原始类型 / `List<String>`，
 * 返回的四个类型都住在 `stats/ReadingStatsModels.kt`（纯 data class）。
 *
 * 消费面只有三处：`feature/StatsScreen.kt`、`feature/HomeViewModel.kt:210`（题材统计）、
 * `reader/VeneraReaderScreen.kt:306`（读完记一笔）。
 *
 * 四个读口的**方法名与实现逐字同串**（`getSummary` / `getRecent14DaysTrend` / `getTopComics` /
 * `getTagStats`）：这一颗契约的参数与返回类型本来就已全中性，改名不换来任何窄化的收益，
 * 只把「换从哪拿」这一件事扩成"还要连着改四个调用点的方法名"——本批的 diff 因此只动接收者。
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

    suspend fun getSummary(): ReadingStatsSummary

    suspend fun getRecent14DaysTrend(): List<DailyTrendItem>

    suspend fun getTopComics(limit: Int = 10): List<ComicStatItem>

    suspend fun getTagStats(days: Int): TagStats
}
