package com.venera.desktop

import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.ReadingStatsStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 桌面首页的**真数据**：直接读那颗已经在桌面落过盘的 `venera_core.db`。
 *
 * ## 为什么这么读，而不是复用 `HomeViewModel`
 *
 * `HomeViewModel` 的构造要吃 `BusinessPorts`（17 个成员、175 个必须实现的方法），
 * 而其中大部分成员的实现在 Android 侧绑 Context / 通知渠道 / assets。
 * 为了首页那三格数字去把 175 个方法全敷衍一遍，是把「接真数据」做成「接一堆空壳」。
 *
 * 而首页要的数字**本来就在库里**：
 *  - 今日页数 / 本周累计 / 连续天数 ← `reading_stats` 表（`ReadingStatsStore` 的 SQL）
 * 而 `ReadingStatsStore` / `HistoryDao` / `DatabasePorts` **已经在 `:desktop` 的编译面里**
 * （它们是本轮之前就共享的那批，`data/db` 那一组 `srcDir`）。
 *
 * 所以这里做的是**只读一条真实通路**：桌面侧自己开库、自己查，数字与安卓端同一张表、
 * 同一套 SQL。不重写判据、不编造数字。
 *
 * ## 与安卓端的一致性
 *
 * 「连续天数」那条判据在 `ReadingStatsStore.streakDays` 里，本文件直接调它 ——
 * 不在这里再写一份（两份一定会漂）。日期口径也照抄安卓：本地时区、`yyyy-MM-dd`
 * 落库、今天没读就从昨天起算。
 */
internal object DesktopHomeStatsSource {

    /** 库里 `read_date` 的格式。与 `ReadingStatsStore.insertSession` 写库时一致。 */
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * 读一次首页要的全部数字。
     *
     * 任何一步失败都**返回带成因的空数据**，不编数字、不假装成功 ——
     * 首页那三格显示 0 时，屏上会说清是「这个库还没有阅读记录」还是「库打不开」。
     */
    suspend fun read(): DesktopHomeStats =
        // `DatabasePorts.of` 未接线时**抛**（`DatabasePorts.kt:93-105`）。
        // 早先这里直接 `readFrom(DatabasePorts.of(null).core)` ⇒ 异常在 readFrom 的
        // try 之前就抛出去 ⇒ `note` 分支走不到 ⇒ "读不到就如实报成因"在这条路上有洞
        // （架构评审 R4）。改成先兜住取 core 这一步。
        runCatching { DatabasePorts.of(null).core }.fold(
            onSuccess = { readFrom(it) },
            onFailure = { t ->
                DesktopHomeStats.Empty.copy(
                    note = "读不到阅读统计库：${t.javaClass.simpleName} " +
                        (t.message?.take(120) ?: ""),
                )
            },
        )

    /**
     * 从**指定库文件**读（[dbFile] 为 null 时用进程级 `DatabasePorts`）。
     *
     * 摆这个入口是为了让判据测得了：测试自己造一颗临时库、写确定的数据，再断言
     * 读回来的数字与直接算出来的一致。没有这个入口，那条判据只能对着真库
     * （内容随主人读什么而变），断不了具体数字。
     */
    suspend fun readFrom(core: com.venera.compose.data.db.SqlDatabaseSource): DesktopHomeStats =
        withContext(Dispatchers.IO) {
            try {
                val store = ReadingStatsStore(core)

                val today = LocalDate.now()
                val weekStart = today.minusDays(today.dayOfWeek.value - 1L) // 周一为一周之始
                val todayKey = today.format(DATE)
                val weekStartKey = weekStart.format(DATE)

                // ① 今日页数：从今天那一行取。
                val rows = store.summaryRows()
                val todayPages = rows.filter { it.readDate == todayKey }.sumOf { it.pagesRead }

                // ② 本周累计：与安卓同一句 SQL（按 read_date 分组后从周一起求和）。
                val weekPages = store.trendRows(weekStartKey).sumOf { it.pages }

                // ③ 连续天数：判据在 store 里，这里只喂它今天/昨天两个锚点。
                val streak = streakDays(store, today)

                DesktopHomeStats(
                    todayPages = todayPages,
                    weekPages = weekPages,
                    streakDays = streak,
                    sources = emptyList(),   // 源状态要 SourceCatalog，那是另一批（见下）
                    weekBar = weekBar(store, today),
                    note = null,
                )
            } catch (t: Throwable) {
                // 库还没建、表还没建、JDBC 驱动缺失 —— 都走这里。**如实说**，不吞成 0。
                DesktopHomeStats.Empty.copy(
                    note = "读不到阅读统计库：${t.javaClass.simpleName} " +
                        (t.message?.take(120) ?: ""),
                )
            }
        }

    /**
     * 连续打卡天数。判据在 [ReadingStatsStore.streakDays]（安卓侧同一颗），
     * 这里只做「今天没读就从昨天起算」这一步，与安卓 `ReadingStatsManager` 逐字一致。
     */
    private fun streakDays(store: ReadingStatsStore, today: LocalDate): Int {
        val dates = store.distinctReadDates()
        if (dates.isEmpty()) return 0
        val todayKey = today.format(DATE)
        val yesterdayKey = today.minusDays(1).format(DATE)
        return store.streakDays(dates, todayKey, yesterdayKey) { d -> dayBefore(d) }
    }

    /** `2026-10-20` → `2026-10-19`。解析失败就返回原串（`streakDays` 会据此停下，不会崩）。 */
    private fun dayBefore(dateKey: String): String = try {
        LocalDate.parse(dateKey, DATE).minusDays(1).format(DATE)
    } catch (e: Exception) {
        dateKey
    }

    /** 本周逐日页数（周一 → 周日，7 格）。没读的那天是 0，不是缺格。 */
    private fun weekBar(store: ReadingStatsStore, today: LocalDate): List<Int> {
        val weekStart = today.minusDays(today.dayOfWeek.value - 1L)
        val byDate = store.trendRows(weekStart.format(DATE)).associate { it.readDate to it.pages }
        return (0 until 7).map { offset ->
            byDate[weekStart.plusDays(offset.toLong()).format(DATE)] ?: 0
        }
    }
}
