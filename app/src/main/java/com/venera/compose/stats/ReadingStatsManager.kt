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
            comicSet.add("${row.sourceName}_${row.comicId}")

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
     * 题材分布：取最近 [days] 天带标签的阅读行，逐条过 [TagNormalizer] 归一，按**页数**加权。
     *
     * 与旧版 `getTopTags` 的四处本质差别（旧实现即使喂上数据也会产出错统计，已删）：
     *  - 权重是页数而非记录条数 —— 翻 200 页的本子比翻 2 页就退的更该上榜；
     *  - 走归一化管线，`lolicon` / `蘿莉` / `萝莉` 合成一个桶；
     *  - 按 [TAG_SEPARATOR] 取原值，不再按空格与逗号切（`big breasts` 会被劈成两半）；
     *  - 去掉 `length in 2..10` 的字符数闸门（它会把带命名空间的 `female:lolicon` 整条丢掉）。
     *
     * 与原版的一处**有意**分歧：原版把「所有带 tags 的行」都计入分母 `taggedPages`，
     * 包括标签全被管线滤掉的行；这里只计**至少贡献了一个桶**的行 —— 否则占比条的分母
     * 会被「只有作者/分类词的本子」灌水，读起来像「题材很分散」。
     */
    suspend fun getTagStats(days: Int): TagStats = withContext(Dispatchers.IO) {
        val startKey = dateFormat.format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(days - 1)) }.time
        )
        val normalizer = buildNormalizer()
        val buckets = HashMap<String, TagBucketAccumulator>()
        val monthly = HashMap<String, HashMap<String, Int>>()
        var taggedPages = 0

        for (row in store.taggedRows(startKey)) {
            val pages = row.pages
            if (pages <= 0) continue
            val month = row.readDate.take(MONTH_KEY_LENGTH)
            var contributed = false
            for (plainTag in row.tags.split(TAG_SEPARATOR)) {
                val display = normalizer.normalize(plainTag) ?: continue
                contributed = true
                val acc = buckets.getOrPut(display) { TagBucketAccumulator() }
                acc.pages += pages
                acc.originals[plainTag] = (acc.originals[plainTag] ?: 0) + 1
                val monthCounts = monthly.getOrPut(month) { HashMap() }
                monthCounts[display] = (monthCounts[display] ?: 0) + pages
            }
            if (contributed) taggedPages += pages
        }

        val sorted = buckets.entries.sortedWith(
            compareByDescending<Map.Entry<String, TagBucketAccumulator>> { it.value.pages }
                .thenBy { it.key }
        )
        TagStats(
            buckets = sorted.map { (display, acc) ->
                val (namespace, raw) = acc.pickOriginal()
                TagStatBucket(
                    display = display,
                    pages = acc.pages,
                    searchNamespace = namespace,
                    searchRaw = raw,
                )
            },
            taggedPages = taggedPages,
            monthlyTop = monthly.entries.sortedByDescending { it.key }
                .take(if (days > 30) 12 else 6)
                .map { (month, counts) ->
                    MonthlyTagTop(
                        month = month,
                        tags = counts.entries
                            .sortedWith(
                                compareByDescending<Map.Entry<String, Int>> { it.value }
                                    .thenBy { it.key }
                            )
                            .take(MONTHLY_TOP_TAGS)
                            .map { it.key to it.value },
                    )
                },
        )
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

    /** 一个题材桶的累加器。 */
    private class TagBucketAccumulator {
        var pages = 0

        /** 归到本桶的**原始** `namespace:tag` → 出现次数，用于挑一个可检索的原值。 */
        val originals = HashMap<String, Int>()

        /**
         * 取出现次数最高的原始标签；并列时按字典序取最小，保证同一份数据每次算出的结果一致
         * （否则「这次点进去搜 A、下次搜 B」这种不可复现的行为会留在统计页）。
         */
        fun pickOriginal(): Pair<String, String> {
            val plain = originals.entries.sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
            ).firstOrNull()?.key ?: return "" to ""
            val idx = plain.indexOf(':')
            return if (idx > 0) {
                plain.substring(0, idx).trim() to plain.substring(idx + 1).trim()
            } else {
                "" to plain.trim()
            }
        }
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

        /** `read_date` 形如 "yyyy-MM-dd"，取前 7 位即月份键。 */
        private const val MONTH_KEY_LENGTH = 7

        /** 时间轴每个月展示几个题材。 */
        private const val MONTHLY_TOP_TAGS = 2

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
