package com.venera.compose.stats

import android.content.Context
import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.ReadingStatsStore
import com.venera.compose.data.tags.ChineseVariantConverter
import com.venera.compose.data.tags.TagNormalizer
import com.venera.compose.data.tags.TagTranslationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.*

/**
 * 阅读统计管理服务
 *
 * 核心特性：
 * 1. 毫秒级阅读会话与页数自动累加记录
 * 2. 连续打卡天数精准计算
 * 3. 14 天阅读趋势分析
 * 4. 常看漫画 Top 榜与偏好题材热度统计
 *
 * `reading_stats` 的 SQL 全部在 [ReadingStatsStore] 那一层（本类不碰游标、不碰列名），
 * 这里只剩三件必须吃 Context 才能做的事：取平台接线口、等标签字典异步加载、按本地时区格式化日期。
 */
class ReadingStatsManager private constructor(private val context: Context) {

    private val store = ReadingStatsStore(DatabasePorts.of(context).core)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())

    /**
     * 上报一次阅读会话
     *
     * **写不进去就当场抛出**，不再吞异常：改造前这里套的是 `catch (_: Exception) {}`，
     * 而 `SQLiteDatabase.insert` 失败既不抛也不回滚，表现成"读了书、统计页永远 0 条"——
     * 写坏被扮成没数据，这是本仓明禁的那一类（4a 评审点名移交本轮）。
     * 抛出后的传播路径见 [ReadingStatsStore.insertSession] 的注释与 4b 报告。
     */
    suspend fun recordSession(
        comicId: String,
        comicTitle: String,
        sourceName: String,
        tags: List<String> = emptyList(),
        chapterTitle: String = "",
        pagesRead: Int = 1,
        durationSeconds: Long = 0
    ) = withContext(Dispatchers.IO) {
        if (pagesRead <= 0 && durationSeconds <= 0) return@withContext

        store.insertSession(
            comicId = comicId,
            comicTitle = comicTitle,
            sourceName = sourceName,
            tags = tags.joinToString(TAG_SEPARATOR),
            chapterTitle = chapterTitle,
            pagesRead = pagesRead,
            durationSeconds = durationSeconds,
            readDate = dateFormat.format(Date()),
            createdAt = System.currentTimeMillis(),
        )
    }

    /**
     * 获取全站阅读总览
     */
    suspend fun getSummary(): ReadingStatsSummary = withContext(Dispatchers.IO) {
        val todayStr = dateFormat.format(Date())

        var totalSec = 0L
        var totalPages = 0
        val comicSet = mutableSetOf<String>()
        var todaySec = 0L
        var todayPages = 0

        for (row in store.summaryRows()) {
            val p = row.pagesRead
            val d = row.durationSeconds

            totalSec += d
            totalPages += p
            // 「一本」的键只有这一颗（同题材占比那把尺子共用，见 [comicKey]）
            comicSet.add(comicKey(row.sourceName, row.comicId))

            if (row.readDate == todayStr) {
                todaySec += d
                todayPages += p
            }
        }

        // 计算连续打卡天数
        val streak = calculateStreakDays()

        ReadingStatsSummary(
            totalSeconds = totalSec,
            totalPages = totalPages,
            totalComics = comicSet.size,
            streakDays = streak,
            todaySeconds = todaySec,
            todayPages = todayPages
        )
    }

    /**
     * 获取最近 14 天每日阅读趋势
     */
    suspend fun getRecent14DaysTrend(): List<DailyTrendItem> = withContext(Dispatchers.IO) {
        val daysList = mutableListOf<String>()

        for (i in 13 downTo 0) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            daysList.add(dateFormat.format(c.time))
        }

        val map = mutableMapOf<String, Pair<Int, Long>>() // date -> (pages, seconds)
        for (d in daysList) {
            map[d] = 0 to 0L
        }

        for (row in store.trendRows(daysList.first())) {
            map[row.readDate] = row.pages to row.seconds
        }

        daysList.map { d ->
            val pair = map[d] ?: (0 to 0L)
            val dateParsed = runCatching { dateFormat.parse(d) }.getOrNull() ?: Date()
            DailyTrendItem(
                date = shortDateFormat.format(dateParsed),
                pages = pair.first,
                seconds = pair.second
            )
        }
    }

    /**
     * 获取阅读时间最长的常看漫画 Top 10
     */
    suspend fun getTopComics(limit: Int = 10): List<ComicStatItem> = withContext(Dispatchers.IO) {
        store.topComics(limit).map { row ->
            ComicStatItem(
                comicId = row.comicId,
                comicTitle = row.comicTitle,
                sourceName = row.sourceName,
                pagesRead = row.pages,
                durationSeconds = row.seconds
            )
        }
    }

    /**
     * 题材分布：取最近 [days] 天带标签的阅读行，交给 [aggregateTagStats] 成桶。
     *
     * 这一层只留两件吃 `Context` 的事：等字典（[buildNormalizer]）与按本地时区算日期下限。
     * 判据本体（怎么切标签、怎么数页、怎么去重数本）在 `TagStatsAggregation.kt`，
     * 那里一句 Android 类型都没有，所以能在单测里对着构造出来的行判对错。
     *
     * 四格屏上读数各自的口径写在那颗文件头：**页加权**是首页推荐用的那把尺，
     * **本数**是统计页那半区念的那把 —— 两把不许混着引。
     */
    suspend fun getTagStats(days: Int): TagStats = withContext(Dispatchers.IO) {
        val startKey = dateFormat.format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(days - 1)) }.time
        )
        val normalizer = buildNormalizer()
        aggregateTagStats(store.taggedRows(startKey), days) { normalizer.normalize(it) }
    }

    /**
     * 组装归一化器：等字典到位 + 读简繁表。
     *
     * 简体字典是 `TagTranslationManager` 构造时**异步**加载的，第一次进统计页很可能还没好，
     * 所以最多等 [DICT_WAIT_TIMEOUT_MS]。超时也照常返回 —— 少字典只是不把英文键折成中文规范名，
     * 标签仍会按原值成桶，比整块不显示好。
     */
    private suspend fun buildNormalizer(): TagNormalizer {
        val tagManager = TagTranslationManager.getInstance(context)
        val converter = ChineseVariantConverter.getInstance(context)
        // 两者都是构造即异步自加载，第一次进统计页很可能都没好；共享同一份等待预算，
        // 超时也照常返回。
        withTimeoutOrNull(DICT_WAIT_TIMEOUT_MS) {
            tagManager.loadedLanguages.first { TagTranslationManager.LANGUAGE_SIMPLIFIED in it }
            converter.ready.first { it }
        }
        return TagNormalizer(
            dictionary = TagNormalizer.buildDictionary(
                tagManager.topicEntries(TagNormalizer.TOPIC_NAMESPACES)
            ),
            toSimplified = converter::traditionalToSimplified,
            hasTraditional = converter::hasChineseTraditional,
        )
    }

    /**
     * 连续打卡天数。日期运算留在这里（要 `Calendar` 与本地时区），
     * "从今天/昨天起逐日往回数连续命中"的判据在 [ReadingStatsStore.streakDays]，两边各一份就一定会漂。
     */
    private fun calculateStreakDays(): Int {
        val dates = store.distinctReadDates()
        if (dates.isEmpty()) return 0

        val today = dateFormat.format(Date())
        val yesterday = dayBefore(today)

        return store.streakDays(dates, today, yesterday, ::dayBefore)
    }

    /** 把 `yyyy-MM-dd` 往前挪一天（与改造前同一份 Calendar 走法，含夏令时那套行为）。 */
    private fun dayBefore(date: String): String {
        val cal = Calendar.getInstance()
        cal.time = dateFormat.parse(date) ?: Date()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return dateFormat.format(cal.time)
    }

    companion object {
        /**
         * `tags` 列的多值分隔符：ASCII 单元分隔符 `U+001F`。
         *
         * 为什么不用逗号：源标签里空格很常见（`big breasts`、`sole female`，e-hentai /
         * nhentai 尤其多），旧实现按 `,，、空格` 一起 split，等于把这类标签**劈成两个假题材**；
         * 而 `\u001F` 不可能出现在标签里，往返即无损。
         *
         * 不需要数据迁移：`tags` 列此前**从没有写入者**（`recordSession` 的 `tags` 形参无调用方
         * 传值），库里存量行恒为空串，没有需要按旧规则解读的数据。
         *
         * internal 而非 private：插图收藏卡片要按 comic_id 反查作者（`Author:` 命名空间），
         * 那是同一列的另一种读法 —— 分隔符只能有一处真相。
         */
        internal const val TAG_SEPARATOR = "\u001F"

        /** 等标签字典异步加载的上限；超时只是不合中文规范名，不阻断统计。 */
        private const val DICT_WAIT_TIMEOUT_MS = 3000L

        @Volatile
        private var INSTANCE: ReadingStatsManager? = null

        fun getInstance(context: Context): ReadingStatsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReadingStatsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
