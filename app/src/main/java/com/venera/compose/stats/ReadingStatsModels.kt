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
