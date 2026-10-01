package com.venera.compose.stats

import android.content.ContentValues
import android.content.Context
import com.venera.compose.data.db.VeneraDatabase
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
 */
class ReadingStatsManager private constructor(private val context: Context) {

    private val dbHelper = VeneraDatabase.getInstance(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())

    /**
     * 上报一次阅读会话
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

        try {
            val db = dbHelper.writableDatabase
            val today = dateFormat.format(Date())
            val cv = ContentValues().apply {
                put("comic_id", comicId)
                put("comic_title", comicTitle)
                put("source_name", sourceName)
                put("tags", tags.joinToString(TAG_SEPARATOR))
                put("chapter_title", chapterTitle)
                put("pages_read", pagesRead)
                put("duration_seconds", durationSeconds)
                put("read_date", today)
                put("created_at", System.currentTimeMillis())
            }
            db.insert("reading_stats", null, cv)
        } catch (_: Exception) {}
    }

    /**
     * 获取全站阅读总览
     */
    suspend fun getSummary(): ReadingStatsSummary = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val todayStr = dateFormat.format(Date())

        var totalSec = 0L
        var totalPages = 0
        val comicSet = mutableSetOf<String>()
        var todaySec = 0L
        var todayPages = 0

        val cursor = db.rawQuery(
            "SELECT comic_id, source_name, pages_read, duration_seconds, read_date FROM reading_stats",
            null
        )
        cursor.use {
            val cidIdx = it.getColumnIndex("comic_id")
            val srcIdx = it.getColumnIndex("source_name")
            val pagesIdx = it.getColumnIndex("pages_read")
            val durIdx = it.getColumnIndex("duration_seconds")
            val dateIdx = it.getColumnIndex("read_date")

            while (it.moveToNext()) {
                val p = it.getInt(pagesIdx)
                val d = it.getLong(durIdx)
                val dt = it.getString(dateIdx)
                val cid = "${it.getString(srcIdx)}_${it.getString(cidIdx)}"

                totalSec += d
                totalPages += p
                comicSet.add(cid)

                if (dt == todayStr) {
                    todaySec += d
                    todayPages += p
                }
            }
        }

        // 计算连续打卡天数
        val streak = calculateStreakDays(db)

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
        val db = dbHelper.readableDatabase
        val calendar = Calendar.getInstance()
        val daysList = mutableListOf<String>()

        for (i in 13 downTo 0) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            daysList.add(dateFormat.format(c.time))
        }

        val map = mutableMapOf<String, Pair<Int, Long>>() // date -> (pages, seconds)
        for (d in daysList) {
            map[d] = 0 to 0L
        }

        val cursor = db.rawQuery(
            "SELECT read_date, SUM(pages_read) as sum_pages, SUM(duration_seconds) as sum_dur FROM reading_stats WHERE read_date >= ? GROUP BY read_date",
            arrayOf(daysList.first())
        )
        cursor.use {
            val dateIdx = it.getColumnIndex("read_date")
            val pagesIdx = it.getColumnIndex("sum_pages")
            val durIdx = it.getColumnIndex("sum_dur")
            while (it.moveToNext()) {
                val dt = it.getString(dateIdx)
                val p = it.getInt(pagesIdx)
                val d = it.getLong(durIdx)
                map[dt] = p to d
            }
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
        val db = dbHelper.readableDatabase
        val list = mutableListOf<ComicStatItem>()

        val cursor = db.rawQuery(
            """
            SELECT comic_id, comic_title, source_name, SUM(pages_read) as sum_pages, SUM(duration_seconds) as sum_dur
            FROM reading_stats
            GROUP BY comic_id, source_name
            ORDER BY sum_dur DESC, sum_pages DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        )
        cursor.use {
            val idIdx = it.getColumnIndex("comic_id")
            val titleIdx = it.getColumnIndex("comic_title")
            val srcIdx = it.getColumnIndex("source_name")
            val pagesIdx = it.getColumnIndex("sum_pages")
            val durIdx = it.getColumnIndex("sum_dur")
            while (it.moveToNext()) {
                list.add(
                    ComicStatItem(
                        comicId = it.getString(idIdx),
                        comicTitle = it.getString(titleIdx),
                        sourceName = it.getString(srcIdx),
                        pagesRead = it.getInt(pagesIdx),
                        durationSeconds = it.getLong(durIdx)
                    )
                )
            }
        }
        list
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

        val cursor = dbHelper.readableDatabase.rawQuery(
            "SELECT read_date, pages_read, tags FROM reading_stats " +
                "WHERE read_date >= ? AND tags != ''",
            arrayOf(startKey)
        )
        cursor.use {
            val dateIdx = it.getColumnIndex("read_date")
            val pagesIdx = it.getColumnIndex("pages_read")
            val tagsIdx = it.getColumnIndex("tags")
            while (it.moveToNext()) {
                val pages = it.getInt(pagesIdx)
                if (pages <= 0) continue
                val month = it.getString(dateIdx).take(MONTH_KEY_LENGTH)
                var contributed = false
                for (plainTag in it.getString(tagsIdx).split(TAG_SEPARATOR)) {
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

    private fun calculateStreakDays(db: android.database.sqlite.SQLiteDatabase): Int {
        val cursor = db.rawQuery(
            "SELECT DISTINCT read_date FROM reading_stats ORDER BY read_date DESC",
            null
        )
        val dates = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                dates.add(it.getString(0))
            }
        }
        if (dates.isEmpty()) return 0

        val today = dateFormat.format(Date())
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.let { dateFormat.format(it.time) }

        // 如果今天或昨天有记录，则连续打卡成立
        if (!dates.contains(today) && !dates.contains(yesterday)) {
            return 0
        }

        var streak = 0
        val cal = Calendar.getInstance()
        if (!dates.contains(today)) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        while (true) {
            val dateStr = dateFormat.format(cal.time)
            if (dates.contains(dateStr)) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
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
