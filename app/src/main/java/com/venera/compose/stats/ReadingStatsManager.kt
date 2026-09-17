package com.venera.compose.stats

import android.content.ContentValues
import android.content.Context
import com.venera.compose.data.db.VeneraDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 阅读统计管理服务 (S7)
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
                put("tags", tags.joinToString(","))
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
     * 获取常看题材标签统计
     */
    suspend fun getTopTags(limit: Int = 12): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val counts = mutableMapOf<String, Int>()

        val cursor = db.rawQuery("SELECT tags FROM reading_stats WHERE tags != ''", null)
        cursor.use {
            val tagsIdx = it.getColumnIndex("tags")
            while (it.moveToNext()) {
                val raw = it.getString(tagsIdx)
                val split = raw.split(',', '，', '、', ' ')
                for (t in split) {
                    val clean = t.trim()
                    if (clean.length in 2..10) {
                        counts[clean] = (counts[clean] ?: 0) + 1
                    }
                }
            }
        }

        counts.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
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
        @Volatile
        private var INSTANCE: ReadingStatsManager? = null

        fun getInstance(context: Context): ReadingStatsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReadingStatsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
