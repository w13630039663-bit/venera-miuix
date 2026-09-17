package com.venera.compose.feature

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.FavoriteRecord
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.db.LocalFavoritesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 首页各分区的数据（全部来自本地库，不再有 sampleComics 兜底） */
data class HomeUiState(
    val history: List<HistoryRecord> = emptyList(),
    val favorites: List<FavoriteRecord> = emptyList(),
    val shelfTop: List<ComicItem> = emptyList(),
    val todayPages: Int = 0,
    val weekPages: Int = 0,
    val streakDays: Int = 0,
    val tagStats: List<Pair<String, Int>> = emptyList(),
    val authorStats: List<Pair<String, Int>> = emptyList(),
    val comicStats: List<Pair<String, Int>> = emptyList(),
)

/**
 * 首页 ViewModel（S0-4 + S0-5）。
 *
 * 之前首页的三块「阅读统计 42 / 286 / 12 天」、「标签·画师·作品 Top 榜」、
 * 以及历史为空时铺出来的 4 张 sampleComics 卡片，全是写死字面量。
 * 现在统一从 FavoriteDao / HistoryDao 的真实记录里算：
 * 页数 = 各记录 last_page_index + 1 之和；连续打卡 = 去重后的活跃日连续段。
 *
 * 说明：原版这些数字来自 read_stats 表（逐话逐页真实计时），那是 S7 的范围；
 * 本阶段先保证「显示的是真数据、哪怕口径暂时粗略」，不再显示假数据。
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val favoritesManager = LocalFavoritesManager.getInstance(app)
    private val historyDao = HistoryDao.getInstance(app)

    /**
     * 收藏条目流（S5 修正）。
     *
     * 数据源必须是 S5 的 [LocalFavoritesManager]——它与收藏页、追更同源。
     * 早期这里读的是已废弃的旧单表 `comic_favorite`，而详情页现在写进动态夹表，
     * 于是「详情页收藏了，首页书架却是空的」。
     * 这里把 [FavoriteItem] 适配回 [FavoriteRecord]，[build] 的统计逻辑保持不变。
     */
    private val favoritesFlow: Flow<List<FavoriteRecord>> =
        favoritesManager.version.map { favoritesManager.getAllComics().map { it.toLegacyRecord() } }

    val uiState: StateFlow<HomeUiState> =
        combine(favoritesFlow, historyDao.historyFlow) { favs, hist -> build(favs, hist) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun build(favs: List<FavoriteRecord>, hist: List<HistoryRecord>): HomeUiState {
        val dayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val todayStart = now / dayMs * dayMs
        val todayPages = hist.filter { it.updatedAt >= todayStart }.sumOf { it.lastPageIndex + 1 }
        val weekPages = hist.filter { it.updatedAt >= now - 7 * dayMs }.sumOf { it.lastPageIndex + 1 }

        val activeDays = hist.map { it.updatedAt / dayMs }.distinct().sortedDescending()
        var streak = 0
        var expect = now / dayMs
        for (d in activeDays) {
            when {
                d == expect -> { streak++; expect = d - 1 }
                d == expect - 1 -> { /* 今天还没读，从昨天往前数 */ expect = d - 1 }
                else -> break
            }
        }

        return HomeUiState(
            history = hist,
            favorites = favs,
            shelfTop = favs.take(4).map { it.toComicItem() },
            todayPages = todayPages,
            weekPages = weekPages,
            streakDays = streak,
            tagStats = favs.flatMap { f -> f.tags.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() } }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
            authorStats = favs.map { it.author }.filter { it.isNotBlank() }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(4).map { it.key to it.value },
            comicStats = hist.sortedByDescending { it.lastChapterIndex }.take(4)
                .map { it.title to (it.lastChapterIndex + 1) },
        )
    }
}

internal fun FavoriteRecord.toComicItem() = ComicItem(
    id = comicId,
    title = title,
    author = author,
    coverUrl = coverUrl,
    tags = tags.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() },
    rating = "",
    description = "",
    sourceName = sourceName,
    latestChapter = latestChapter,
    updateTime = "",
    hasUpdate = hasUpdate,
    chapters = emptyList(),
)

/**
 * 适配层：S5 动态夹表的 [FavoriteItem] → 首页沿用的 [FavoriteRecord]。
 * 首页只需要展示字段，忽略收藏夹与追更列。
 */
private fun FavoriteItem.toLegacyRecord() = FavoriteRecord(
    comicId = id,
    title = name,
    author = author,
    coverUrl = coverPath,
    sourceName = sourceKey,
    tags = tags.joinToString(","),
    hasUpdate = false,
    latestChapter = "",
)

internal fun HistoryRecord.toComicItem() = ComicItem(
    id = comicId,
    title = title,
    author = author,
    coverUrl = coverUrl,
    tags = emptyList(),
    rating = "",
    description = "上次阅读至 $lastChapterTitle",
    sourceName = sourceName,
    latestChapter = lastChapterTitle,
    updateTime = "",
    hasUpdate = false,
    chapters = emptyList(),
)
