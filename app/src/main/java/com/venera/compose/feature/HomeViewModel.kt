package com.venera.compose.feature

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.FavoriteRecord
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.platform.android.AndroidKeyValueStore
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.Comic
import com.venera.compose.stats.ReadingStatsManager
import com.venera.compose.stats.TagStatBucket
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * 首页「可能你感兴趣」推荐区状态。
 *
 * [note] 非空且 [comics] 为空时，界面显示这一行说明（可选带重试）—— 空态与失败都要**说清
 * 原因**，绝不铺假封面占位。
 */
data class HomeRecommendState(
    /** 推荐依据：最近 30 天读得最多的题材（页数加权、已归一化）。 */
    val tags: List<TagStatBucket> = emptyList(),
    val comics: List<Comic> = emptyList(),
    val loading: Boolean = false,
    val note: String? = null,
    val canRetry: Boolean = false,
    /** 每成功拉一批 +1：界面用它区分「换了一批」与「还是那批」，并把焦点滚到新位置。 */
    val batch: Int = 0,
)

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
    /** 本地漫画数量（LocalComicManager 真实扫描） */
    val localComicCount: Int = 0,
    /** 下载中任务数（DownloadManager 队列） */
    val downloadingCount: Int = 0,
)

/**
 * 首页 ViewModel。
 *
 * 之前首页的三块「阅读统计 42 / 286 / 12 天」、「标签·画师·作品 Top 榜」、
 * 以及历史为空时铺出来的 4 张 sampleComics 卡片，全是写死字面量。
 * 现在统一从 FavoriteDao / HistoryDao 的真实记录里算：
 * 页数 = 各记录 last_page_index + 1 之和；连续打卡 = 去重后的活跃日连续段。
 *
 * 说明：原版这些数字来自 read_stats 表（逐话逐页真实计时），那是另有一轮的范围；
 * 本阶段先保证「显示的是真数据、哪怕口径暂时粗略」，不再显示假数据。
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val favoritesManager = LocalFavoritesManager.getInstance(app)
    private val historyDao = HistoryDao.getInstance(app)
    private val appContext = app

    /**
     * 收藏条目流（改过一次：早前接的是旧那套收藏流）。
     *
     * 数据源必须是收藏那套的 [LocalFavoritesManager]——它与收藏页、追更同源。
     * 早期这里读的是已废弃的旧单表 `comic_favorite`，而详情页现在写进动态夹表，
     * 于是「详情页收藏了，首页书架却是空的」。
     * 这里把 [FavoriteItem] 适配回 [FavoriteRecord]，[build] 的统计逻辑保持不变。
     */
    private val favoritesFlow: Flow<List<FavoriteRecord>> =
        favoritesManager.version.map {
            val started = android.os.SystemClock.elapsedRealtime()
            val comics = favoritesManager.getAllComics()
            com.venera.compose.StartupTrace.recordElapsed("HomeVM: getAllComics", started)
            comics.map { it.toLegacyRecord() }
        }

    val uiState: StateFlow<HomeUiState> =
        combine(favoritesFlow, historyDao.historyFlow) { favs, hist ->
            val started = android.os.SystemClock.elapsedRealtime()
            val state = build(favs, hist)
            com.venera.compose.StartupTrace.recordElapsed("HomeVM: build(uiState)", started)
            state
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** 刷新那几个新增分区的数据（本地漫画数 / 下载任务数 / 图片收藏统计），主页进入时调用 */
    fun refreshExtras() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val localCount = com.venera.compose.download.LocalComicManager.getInstance(appContext)
                .getLocalComics().size
            val dlCount = com.venera.compose.download.DownloadManager.getInstance(appContext)
                .tasks.value.count { it.status != com.venera.compose.download.DownloadStatus.COMPLETED }
            // 图片收藏的统计/列表已整体搬到收藏页（第三个分段），主页扩展区只剩本地数量与下载任务。
            _uiStateExtra.value = HomeUiState(
                localComicCount = localCount,
                downloadingCount = dlCount,
            )
        }
    }

    /** 扩展区数据（与主 uiState 分流，避免整表重算） */
    private val _uiStateExtra = MutableStateFlow(
        HomeUiState(localComicCount = -1) // -1 = 尚未加载
    )
    val uiStateExtra: StateFlow<HomeUiState> = _uiStateExtra.asStateFlow()

    /**
     * 「可能你感兴趣」推荐区。
     *
     * 单独一条流：它要发网络请求，不能把首页整表重算带起来。
     */
    private val _recommend = MutableStateFlow(HomeRecommendState())
    val recommend: StateFlow<HomeRecommendState> = _recommend.asStateFlow()

    /** 退出前那批推荐的落盘位置（冷启动首屏直接铺它，见 [hydrateRecommendFromCache]）。 */
    private val recommendPrefs = AndroidKeyValueStore(app, "home_recommend_cache")

    init {
        hydrateRecommendFromCache()
    }

    /**
     * 冷启动时「组合首次进入」与「生命周期 ON_START」会同时要求刷新；这里挡掉并发的那一发，
     * 避免同一时刻对同一个源打双份请求。
     */
    private var recommendInFlight = false

    /** 本进程是否已经拉过一次：只有非 force 的调用会看它。 */
    private var recommendLoaded = false

    /** 上一次真正发起拉取的时刻（含自动刷新），用于给「回首页」节流。 */
    private var lastRecommendAt = 0L

    /** 已成功/失败的拉取批次号，单调递增；见 [HomeRecommendState.batch]。 */
    private var recommendBatch = 0

    /**
     * 启动应用与回到首页时的自动刷新。
     *
     * 冷启动那次必须拉，但用户在列表与详情页之间往复时不该每次回来都发 3 个搜索请求，
     * 因此非首次的自动刷新按 [RECOMMEND_AUTO_REFRESH_INTERVAL_MS] 节流；
     * 用户主动点「换一批」不走这条路径，永远立即生效。
     */
    fun autoRefreshRecommend() {
        if (recommendLoaded &&
            System.currentTimeMillis() - lastRecommendAt < RECOMMEND_AUTO_REFRESH_INTERVAL_MS
        ) return
        loadRecommend(force = true)
    }

    /**
     * 依据「最近 30 天读得最多的题材」从禁漫天堂推一批本子。
     *
     * 口径刻意不用 [HomeUiState.tagStats]：那个榜是**收藏**里 tags 的裸词频（没过归一化、
     * 混着作者与分类词），语义不是"读得最多"。这里吃 ReadingStatsManager 的题材分布 ——
     * 真实阅读记录、页数加权、五级归一化，且每个桶自带可直接搜索的 searchRaw。
     *
     * 选禁漫天堂：免登录即可搜索，且搜索支持分页与排序 —— 「换一批」能做到真随机，
     * 而不是把同一页重排。
     *
     * @param force 换一批 / 重试 / 启动与回首页的自动刷新都传 true；
     *   false 只用于「本进程还没拉过」的首次加载。
     */
    fun loadRecommend(force: Boolean = false) {
        if (recommendInFlight) return
        if (recommendLoaded && !force) return
        recommendLoaded = true
        recommendInFlight = true
        lastRecommendAt = System.currentTimeMillis()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                loadRecommendOnce()
            } finally {
                recommendInFlight = false
            }
        }
    }

    private suspend fun loadRecommendOnce() {
        val sourceManager = ComicSourceManager.getInstance(appContext)
        if (!awaitSourceRegistered(sourceManager, RECOMMEND_SOURCE_KEY)) {
            failRecommend("推荐取自禁漫天堂（免登录），当前未启用该源", canRetry = true)
            return
        }
        _recommend.value = _recommend.value.copy(loading = true)
        val guard = com.venera.compose.security.guard.ContentGuardManager.getInstance(appContext)
        val buckets = runCatching {
            ReadingStatsManager.getInstance(appContext).getTagStats(RECOMMEND_WINDOW_DAYS).buckets
        }.getOrNull().orEmpty().take(3)
        if (buckets.isEmpty()) {
            // 读几本之后就该能推了，允许下次进入首页再试
            failRecommend("最近 $RECOMMEND_WINDOW_DAYS 天还没有足够的阅读记录", canRetry = false)
            return
        }
        // 每个题材各拉一页，再 round-robin 混排去重 —— 否则整行都是 top1 一个题材。
        // 「刷新即换一批」：页码与排序都随机取，用户点刷新就会看到新的本子。
        // 页码压在 1..10：冷门题材的深页会直接给空结果。
        val random = kotlin.random.Random
        val perTag = buckets.map { bucket ->
            val page = random.nextInt(1, RECOMMEND_MAX_PAGE + 1)
            val sort = RECOMMEND_SORTS.random(random)
            runCatching {
                sourceManager.search(
                    RECOMMEND_SOURCE_KEY, bucket.searchRaw, page, listOf(sort)
                ).getOrNull()?.comics.orEmpty()
            }.getOrDefault(emptyList())
        }
        val merged = ArrayList<Comic>(RECOMMEND_LIMIT)
        val seen = HashSet<String>()
        var index = 0
        while (merged.size < RECOMMEND_LIMIT) {
            var addedThisRound = false
            for (list in perTag) {
                val comic = list.getOrNull(index) ?: continue
                if (seen.add(comic.id)) {
                    merged.add(comic)
                    addedThisRound = true
                    if (merged.size >= RECOMMEND_LIMIT) break
                }
            }
            if (!addedThisRound) break
            index++
        }
        // HIDE 命中整条剔除（BLUR 交由卡片自身打码），与探索页同一口径。
        val visible = guard.filterComicModels(merged)
        if (visible.isEmpty()) {
            failRecommend(
                note = if (perTag.all { it.isEmpty() }) "禁漫天堂暂时拉不到内容，稍后重试"
                       else "推荐内容都被屏蔽规则过滤掉了",
                canRetry = true,
                tags = buckets,
            )
            return
        }
        recommendBatch++
        val state = HomeRecommendState(
            batch = recommendBatch,
            tags = buckets,
            comics = visible,
        )
        _recommend.value = state
        writeRecommendCache(state)
    }

    /**
     * 失败 / 空态的统一出口。
     *
     * 关键取舍：**上一批还在就继续铺着**，只把说明与「重试」挂上去（界面在 comics 非空时
     * 不显示 note）。冷启动时那一批很可能就是 [hydrateRecommendFromCache] 读出来的退出前
     * 快照 —— 因为源没就绪或网络抖一下就把内容清空，等于把用户的退出前状态弄丢了。
     *
     * batch 不递增：轮播不该因为一次失败请求就随机跳焦点。
     */
    private fun failRecommend(note: String, canRetry: Boolean, tags: List<TagStatBucket> = emptyList()) {
        recommendLoaded = false
        val previous = _recommend.value
        _recommend.value = if (previous.comics.isEmpty()) {
            HomeRecommendState(batch = previous.batch, tags = tags, note = note, canRetry = canRetry)
        } else {
            previous.copy(loading = false, note = note, canRetry = canRetry)
        }
    }

    /**
     * JS 源是 `ComicSourceManager` 构造里 `scope.launch { loadInstalledJsSources() }` 异步注册的，
     * 冷启动这一发几乎总是早于注册完成 —— 直接判「未启用该源」就是真机看到的症状。
     * 给它一个有界等待窗口，等不到才认输。
     */
    private suspend fun awaitSourceRegistered(manager: ComicSourceManager, key: String): Boolean {
        if (manager.getSource(key) != null) return true
        return withTimeoutOrNull(RECOMMEND_SOURCE_WAIT_MS) {
            while (manager.getSource(key) == null) delay(300)
            true
        } ?: false
    }

    /** 退出前的那批推荐先铺上，网络回来再换（冷启动首屏不该是一段错误文案）。 */
    private fun hydrateRecommendFromCache() {
        val raw = recommendPrefs.getString(KEY_RECOMMEND_CACHE, null) ?: return
        val restored = runCatching { readRecommendSnapshot(raw) }.getOrNull() ?: return
        if (restored.comics.isEmpty()) return
        _recommend.value = restored
        // 缓存当作「刚拉过」：启动时不再自动打网络，只铺上次退出前的那批。
        // 之后回到首页超过节流窗口才会自动刷新，用户主动「换一批」不受此限。
        recommendLoaded = true
        lastRecommendAt = System.currentTimeMillis()
    }

    private fun writeRecommendCache(state: HomeRecommendState) {
        recommendPrefs.put(KEY_RECOMMEND_CACHE, writeSnapshot(state))
    }

    private companion object {
        /**
         * 推荐取数源：禁漫天堂免登录即可搜索，且搜索支持分页与排序，
         * 所以「刷新换一批」能做到真随机（而不是把同一页重排）。
         */
        const val RECOMMEND_SOURCE_KEY = "jm"

        /** 题材榜窗口：固定最近 30 天（不跟随统计页的范围切换）。 */
        const val RECOMMEND_WINDOW_DAYS = 30

        /** 一行放几本。 */
        const val RECOMMEND_LIMIT = 10

        /** 自动刷新（启动/回首页）的最小间隔，避免往复导航把源打爆。 */
        const val RECOMMEND_AUTO_REFRESH_INTERVAL_MS = 120_000L

        /** 等 JS 源注册上界的窗口。 */
        const val RECOMMEND_SOURCE_WAIT_MS = 10_000L

        /** 随机页码上限：冷门题材的深页会直接给空结果。 */
        const val RECOMMEND_MAX_PAGE = 10

        /** jm.js 的 search optionList 取值（源里 `options[0]`，传错会静默退回默认排序）。 */
        val RECOMMEND_SORTS = listOf(
            "mr", "mv", "mv_m", "mv_w", "mv_t", "mp", "tf",
        )

        const val KEY_RECOMMEND_CACHE = "recommend_snapshot"
    }

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
 * 适配层：动态夹表那套的 [FavoriteItem] → 首页沿用的 [FavoriteRecord]。
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

/**
 * 推荐快照落盘：只存卡片真正要看的字段。
 *
 * 不用反射序列化 [Comic] —— 那会把 description / rating 这些首页不读的字段一起写进去，
 * 且源侧模型一改就解不出来；手写 JSON 遇到缺字段能自然退回默认值。
 * 缓存里的题材桶只有显示名（首屏那行「按你最近 30 天读得最多的题材」用它），
 * search* 留空 —— 缓存态不参与下钻，重新拉到后会被真桶覆盖。
 */
private fun writeSnapshot(state: HomeRecommendState): String {
    val comics = JSONArray()
    state.comics.forEach { comic ->
        comics.put(
            JSONObject().apply {
                put("id", comic.id)
                put("title", comic.title)
                put("subTitle", comic.subTitle)
                put("cover", comic.cover)
                put("sourceKey", comic.sourceKey)
                put("tags", JSONArray().apply { comic.tags.forEach { put(it) } })
                comic.likesCount?.let { put("likes", it) }
            }
        )
    }
    val tags = JSONArray()
    state.tags.forEach { tags.put(JSONObject().put("display", it.display).put("pages", it.pages)) }
    return JSONObject().put("tags", tags).put("comics", comics).toString()
}

private fun readRecommendSnapshot(raw: String): HomeRecommendState {
    val root = JSONObject(raw)
    val tags = root.optJSONArray("tags") ?: JSONArray()
    val comics = root.optJSONArray("comics") ?: JSONArray()
    val comicList = ArrayList<Comic>(comics.length())
    for (i in 0 until comics.length()) {
        val item = comics.optJSONObject(i) ?: continue
        val tagArray = item.optJSONArray("tags") ?: JSONArray()
        comicList.add(
            Comic(
                id = item.optString("id"),
                title = item.optString("title"),
                subTitle = item.optString("subTitle"),
                cover = item.optString("cover"),
                sourceKey = item.optString("sourceKey"),
                tags = List(tagArray.length()) { tagArray.optString(it) },
                likesCount = if (item.has("likes")) item.optInt("likes") else null,
            )
        )
    }
    val tagList = ArrayList<TagStatBucket>(tags.length())
    for (i in 0 until tags.length()) {
        val item = tags.optJSONObject(i) ?: continue
        tagList.add(
            TagStatBucket(
                display = item.optString("display"),
                pages = item.optInt("pages"),
                searchNamespace = "",
                searchRaw = "",
            )
        )
    }
    // batch 固定 0：缓存态是「起点」，不该被当成「换了一批」而随机跳焦点。
    return HomeRecommendState(tags = tagList, comics = comicList)
}
