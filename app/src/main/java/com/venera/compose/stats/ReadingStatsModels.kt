package com.venera.compose.stats

data class ReadingStatsSummary(
    val totalSeconds: Long = 0,
    val totalPages: Int = 0,
    val totalComics: Int = 0,
    val streakDays: Int = 1,
    val todaySeconds: Long = 0,
    val todayPages: Int = 0
)

data class DailyTrendItem(
    val date: String, // "MM-dd"
    val pages: Int,
    val seconds: Long
)

data class ComicStatItem(
    val comicId: String,
    val comicTitle: String,
    val sourceName: String,
    val pagesRead: Int,
    val durationSeconds: Long
)

/**
 * 一个归一化后的题材桶。
 *
 * [display] 是给人看的中文规范名；[searchNamespace] / [searchRaw] 是该桶里出现次数最高的
 * **源生原始标签**，点击下钻时必须用它 —— 站方标签源（EH / nhentai / hitomi / manga_dex）
 * 认的是 `lolicon` 而不是「萝莉」，只带显示名的话原生源那一路必然空手而归
 * （原版 `stats_page.dart` 的 `_dig` 就踩在这里，移植时一并修掉）。
 *
 * [pages] 与 [comics] 是**两把尺子**（口径与取舍见 `TagStatsAggregation` 的文件头）：
 * 前者是页加权、首页推荐按它排序；后者是**去重后的本数**、统计页那半区按它念。
 */
data class TagStatBucket(
    val display: String,
    val pages: Int,
    /** 归到本桶的**本数**（同一本读三天只算一本）。 */
    val comics: Int,
    val searchNamespace: String,
    val searchRaw: String
)

/** 某个月的题材 Top 榜（追漫轨迹时间轴用）。[month] 形如 "2026-09"；计数是**本数**。 */
data class MonthlyTagTop(
    val month: String,
    val tags: List<Pair<String, Int>>
)

/** 题材分布聚合结果。 */
data class TagStats(
    val buckets: List<TagStatBucket> = emptyList(),
    /** 有标签记录的**本数**（统计页那半区念的分母；同一本读三天只算一本）。 */
    val taggedComics: Int = 0,
    val monthlyTop: List<MonthlyTagTop> = emptyList()
)
