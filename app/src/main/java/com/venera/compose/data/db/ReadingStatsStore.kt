package com.venera.compose.data.db

import com.venera.compose.data.platform.SqlDatabase

/**
 * `reading_stats` 的读写层。
 *
 * 为什么单独一层（调用方是 `stats/ReadingStatsManager`）：那张表的列名、聚合语句与"哪些列不许为
 * null"属于持久层，而 manager 那一侧还有 Context 才能做的事（等标签字典异步加载、繁简转换、
 * 日期格式化）。分开后这一层不碰 Android 类型，`:desktop:test` 就能拿真 `JdbcSqliteDatabase`
 * 跑「累计 → 查询」，而不是只在真机上点一次统计页。
 *
 * 列的可空性逐列对过 `SchemaSql.V1_CREATE_STATEMENTS` 里 `reading_stats` 的建表语句：
 * 十列全 `NOT NULL` ⇒ 文本一律走 [requiredString]（读出 null 就是表结构与代码不符，抛）。
 * `SUM(...)` 的两个别名（`sum_pages` / `sum_dur`）只在有行的分组里出现，所以同样按不许为 null 读。
 */
class ReadingStatsStore(private val source: SqlDatabaseSource) {

    /**
     * 落一行阅读会话。
     *
     * **写不进去就抛**（消息带 SQL 原文，由 [SqlDatabase.exec] 兜），这里不留 `catch`：
     * 改造前 `db.insert(...)` 外面套的是 `catch (_: Exception) {}`，而 SQLite 的 `insert`
     * 失败既不抛也不回滚 —— 表现是"读了书、统计页永远是 0"，写坏被扮成没数据（4a 评审点名的那条）。
     */
    fun insertSession(
        comicId: String,
        comicTitle: String,
        sourceName: String,
        tags: String,
        chapterTitle: String,
        pagesRead: Int,
        durationSeconds: Long,
        readDate: String,
        createdAt: Long,
    ) {
        source.writer().exec(INSERT_STATS, comicId, comicTitle, sourceName, tags, chapterTitle,
            pagesRead, durationSeconds, readDate, createdAt)
    }

    /** 总览要用的全部行（`comic_id, source_name, pages_read, duration_seconds, read_date`）。 */
    fun summaryRows(): List<StatsSummaryRow> =
        source.reader().query(
            "SELECT comic_id, source_name, pages_read, duration_seconds, read_date FROM reading_stats"
        ).map {
            StatsSummaryRow(
                comicId = it.requiredString("comic_id"),
                sourceName = it.requiredString("source_name"),
                pagesRead = it.long("pages_read").toInt(),
                durationSeconds = it.long("duration_seconds"),
                readDate = it.requiredString("read_date"),
            )
        }

    /** 从 [fromDate]（含）起的逐日合计，键是库里的 `read_date` 原文。 */
    fun trendRows(fromDate: String): List<TrendRow> =
        source.reader().query(
            "SELECT read_date, SUM(pages_read) as sum_pages, SUM(duration_seconds) as sum_dur " +
                "FROM reading_stats WHERE read_date >= ? GROUP BY read_date",
            fromDate
        ).map {
            TrendRow(
                readDate = it.requiredString("read_date"),
                pages = it.long("sum_pages").toInt(),
                seconds = it.long("sum_dur"),
            )
        }

    /** 阅读时间最长的前 [limit] 本（时长优先、页数其次，与改造前的 ORDER BY 一致）。 */
    fun topComics(limit: Int): List<TopComicRow> =
        source.reader().query(
            "SELECT comic_id, comic_title, source_name, SUM(pages_read) as sum_pages," +
                " SUM(duration_seconds) as sum_dur" +
                " FROM reading_stats" +
                " GROUP BY comic_id, source_name" +
                " ORDER BY sum_dur DESC, sum_pages DESC" +
                " LIMIT ?",
            limit
        ).map {
            TopComicRow(
                comicId = it.requiredString("comic_id"),
                comicTitle = it.requiredString("comic_title"),
                sourceName = it.requiredString("source_name"),
                pages = it.long("sum_pages").toInt(),
                seconds = it.long("sum_dur"),
            )
        }

    /** 从 [fromDate]（含）起**带标签**的行；题材分布的原料（标签列的拆分与归一在调用侧）。 */
    fun taggedRows(fromDate: String): List<TaggedReadRow> =
        source.reader().query(
            "SELECT read_date, pages_read, tags FROM reading_stats WHERE read_date >= ? AND tags != ''",
            fromDate
        ).map {
            TaggedReadRow(
                readDate = it.requiredString("read_date"),
                pages = it.long("pages_read").toInt(),
                tags = it.requiredString("tags"),
            )
        }

    /** 库里出现过的阅读日期，倒序（连续打卡天数的原料）。 */
    fun distinctReadDates(): List<String> =
        source.reader().query(
            "SELECT DISTINCT read_date FROM reading_stats ORDER BY read_date DESC"
        ).map { it.requiredString("read_date") }

    /**
     * **带标签**的行的 `comic_id` 与标签串（没有日期下限）。
     *
     * 这是同一列的另一种读法：插图收藏的卡片要按 comic_id 反查作者，用的就是阅读时落下的
     * 源生标签（调用方是 `FavoriteImagesManager`）。
     */
    fun comicTagPairs(): List<ComicTagsRow> =
        source.reader().query(
            "SELECT comic_id, tags FROM reading_stats WHERE tags != ''"
        ).map {
            ComicTagsRow(
                comicId = it.requiredString("comic_id"),
                tags = it.requiredString("tags"),
            )
        }

    /**
     * 连续打卡天数。判据与改造前逐条相同，只是不再自己查库、也不自己做日期运算：
     * 今天或昨天**都**没有记录 ⇒ 0；否则从今天（今天没有就从昨天）逐日往回数连续命中的天数。
     *
     * [today] / [yesterday] 由调用方按库内那套 `yyyy-MM-dd` 给；
     * [dayBefore] 是"把日期串往前挪一天"，仍由调用方用它那份 `SimpleDateFormat` 算
     * （这层不碰格式化与时区，所以它能在 JVM 里被测到；挪日子的语义与改造前同一条 Calendar 走法）。
     */
    fun streakDays(
        dates: List<String>,
        today: String,
        yesterday: String,
        dayBefore: (String) -> String,
    ): Int {
        if (dates.isEmpty()) return 0
        // 今天没读、昨天读了 —— 连续仍然成立，与改造前的判据一致
        if (!dates.contains(today) && !dates.contains(yesterday)) return 0

        var streak = 0
        var cursor = if (dates.contains(today)) today else dayBefore(today)
        while (dates.contains(cursor)) {
            streak++
            cursor = dayBefore(cursor)
        }
        return streak
    }

    companion object {
        /**
         * 列序与改造前 `ContentValues` 的 put 顺序一致；
         * [CoreTableBackup] 恢复备份里的 `stats.json` 用的就是这一条，列名只有一处定义。
         */
        internal const val INSERT_STATS =
            "INSERT INTO reading_stats " +
                "(comic_id, comic_title, source_name, tags, chapter_title, pages_read, " +
                "duration_seconds, read_date, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
    }
}

/** 总览用的一行阅读统计（`comic_history` 那种全列形状在这一层没必要）。 */
data class StatsSummaryRow(
    val comicId: String,
    val sourceName: String,
    val pagesRead: Int,
    val durationSeconds: Long,
    val readDate: String,
)

/** 某一天的合计。 */
data class TrendRow(val readDate: String, val pages: Int, val seconds: Long)

/** 常看漫画榜的一行。 */
data class TopComicRow(
    val comicId: String,
    val comicTitle: String,
    val sourceName: String,
    val pages: Int,
    val seconds: Long,
)

/** 带标签的一行阅读记录。 */
data class TaggedReadRow(val readDate: String, val pages: Int, val tags: String)

/** 一行阅读记录的「哪本漫画 + 它的标签串」。 */
data class ComicTagsRow(val comicId: String, val tags: String)
