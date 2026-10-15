package com.venera.compose.feature

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.FavoriteRecord
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.source.model.Comic
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
import com.venera.compose.data.api.SourceCatalog

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
    /**
     * 本批实际用上的源（显示名，顿号分隔）。界面在依据那行后面挂出来 ——
     * 两档各自绑一组源，用户有权知道这一行到底是哪儿来的。
     * 缓存快照里为空（不落盘），下次真拉到后会被覆盖。
     */
    val sourcesLabel: String = "",
)

/**
 * 「可能你感兴趣」的内容分级。两档各自绑定一组源（见 [HomeViewModel] 里的源集常量）：
 *
 * - [NORMAL] 全年龄向：MangaDex / 爱看漫 / 拷贝漫画
 * - [R18] 成人向：禁漫天堂 / nhentai
 *
 * 存的是 [key]（大小写不敏感读回），所以改枚举名不会让老用户的档位掉回默认。
 */
enum class HomeRecommendMode(val key: String, val label: String) {
    NORMAL("NORMAL", "正常"),
    R18("R18", "R18");

    companion object {
        fun fromKey(key: String?): HomeRecommendMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: NORMAL
    }
}

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
    /** 本地漫画数量（本地库真实扫描） */
    val localComicCount: Int = 0,
    /** 下载中任务数（下载队列） */
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
class HomeViewModel(
    app: Application,
    private val ports: BusinessPorts,
) : AndroidViewModel(app) {

    /** 装配入口：调用点（17 处 viewModel()）与 AndroidViewModelFactory 反射的那颗 arity=1 构造逐字不变。 */
    constructor(app: Application) : this(app, BusinessPorts.of(app))

    private val favoritesManager = ports.favorites
    private val historyDao = ports.history
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
            val localCount = ports.localComics
                .getLocalComics().size
            val dlCount = ports.downloads
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
    private val recommendPrefs = ports.stores.open(PreferenceKeys.PREFS_HOME_RECOMMEND_CACHE)

    /**
     * 当前分级档（正常 / R18）。与推荐快照同存一处（`PREFS_HOME_RECOMMEND_CACHE`），
     * 快照按档分开存（[cacheKeyFor]），否则切档瞬间会先闪出另一档的内容。
     */
    private val _recommendMode =
        MutableStateFlow(HomeRecommendMode.fromKey(recommendPrefs.getString(KEY_RECOMMEND_MODE, null)))
    val recommendMode: StateFlow<HomeRecommendMode> = _recommendMode.asStateFlow()

    init {
        hydrateRecommendFromCache()
    }

    /**
     * 切换分级档。**清空当前展示、铺新档的缓存、立刻拉一批** —— 三件事都要做：
     * 留着旧档那批内容会让人以为切档没生效；只铺缓存不拉新的，用户看到的是上一轮的残影；
     * 不重置 `recommendLoaded/lastRecommendAt`，下一次 `autoRefreshRecommend` 会被节流挡掉。
     */
    fun setRecommendMode(mode: HomeRecommendMode) {
        if (_recommendMode.value == mode) return
        _recommendMode.value = mode
        recommendPrefs.put(KEY_RECOMMEND_MODE, mode.key)
        recommendLoaded = false
        lastRecommendAt = 0L
        // loading=true 同时兜住一件事：这一档缓存为空时，分区不会因为"空态且无说明"而整块消失
        // ——那会让用户连切回原来那一档的入口都找不到。
        _recommend.value = readCacheFor(mode)?.copy(loading = true) ?: HomeRecommendState(loading = true)
        loadRecommend(force = true)
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
        val sources = ports.sources
        val comics = ports.comics
        val mode = _recommendMode.value
        val candidates = sourcesFor(mode)
        // JS 源是异步注册的，冷启动这一发几乎总是早于注册完成：给一个有界等待窗口，
        // 等到**至少一颗**就继续（等全套会把首屏拖到十几秒；漏掉的那几颗下一次刷新会补上）。
        val ready = awaitReadySources(sources, candidates)
        if (ready.isEmpty()) {
            failRecommend(
                note = "推荐取自${mode.label}源（${candidates.joinToString("、") { it.displayName }}），当前都没启用",
                canRetry = true,
            )
            return
        }
        val sourcesLabel = ready.joinToString(" · ") { it.displayName }
        _recommend.value = _recommend.value.copy(loading = true)
        val guard = ports.contentGuard
        val buckets = runCatching {
            ports.stats.getTagStats(RECOMMEND_WINDOW_DAYS).buckets
        }.getOrNull().orEmpty().take(3)
        if (buckets.isEmpty()) {
            // 读几本之后就该能推了，允许下次进入首页再试
            failRecommend("最近 $RECOMMEND_WINDOW_DAYS 天还没有足够的阅读记录", canRetry = false)
            return
        }
        // 每个 (源 × 题材) 各拉一页，再 round-robin 混排去重 —— 否则整行都是 top1 一个题材，
        // 或者一个源把它自己的风格刷满。页码与排序都随机取，用户点刷新就会看到新的本子。
        // 页码压在 1..maxPage：冷门题材的深页会直接给空结果，而像爱看漫这种搜索本身不分页的源
        // 只能取第 1 页（深页返回空，白打一次请求）。
        val random = kotlin.random.Random
        val perCombo = buildList {
            for (src in ready) {
                for (bucket in buckets) {
                    val page = random.nextInt(1, src.maxPage + 1)
                    val options = src.sortOptions.random(random)
                    add(
                        runCatching {
                            comics.search(src.key, bucket.searchRaw, page, options).getOrNull()?.comics.orEmpty()
                        }.getOrDefault(emptyList())
                    )
                }
            }
        }
        val merged = ArrayList<Comic>(RECOMMEND_LIMIT)
        val seen = HashSet<String>()
        var index = 0
        while (merged.size < RECOMMEND_LIMIT) {
            var addedThisRound = false
            for (list in perCombo) {
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
        val visible = guard.filterComics(merged)
        if (visible.isEmpty()) {
            failRecommend(
                note = if (perCombo.all { it.isEmpty() })
                    "${mode.label}源暂时拉不到内容，稍后重试"
                else "推荐内容都被屏蔽规则过滤掉了",
                canRetry = true,
                tags = buckets,
                sourcesLabel = sourcesLabel,
            )
            return
        }
        recommendBatch++
        val state = HomeRecommendState(
            batch = recommendBatch,
            tags = buckets,
            comics = visible,
            sourcesLabel = sourcesLabel,
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
    private fun failRecommend(
        note: String,
        canRetry: Boolean,
        tags: List<TagStatBucket> = emptyList(),
        sourcesLabel: String = "",
    ) {
        recommendLoaded = false
        val previous = _recommend.value
        _recommend.value = if (previous.comics.isEmpty()) {
            HomeRecommendState(
                batch = previous.batch,
                tags = tags,
                note = note,
                canRetry = canRetry,
                sourcesLabel = sourcesLabel,
            )
        } else {
            previous.copy(loading = false, note = note, canRetry = canRetry)
        }
    }

    /**
     * JS 源是 `ComicSourceManager` 构造里 `scope.launch { loadInstalledJsSources() }` 异步注册的，
     * 冷启动这一发几乎总是早于注册完成 —— 直接判「未启用该源」就是真机看到的症状。
     * 给它一个有界等待窗口，等到**至少一颗**候选源可用就返回；等不到才认输。
     *
     * 刻意不逐颗等：三颗各等 10s 会把首屏拖到半分钟。漏掉的那几颗下一次刷新（回首页）会补上。
     */
    private suspend fun awaitReadySources(
        manager: SourceCatalog,
        candidates: List<RecommendSourceDef>,
    ): List<RecommendSourceDef> {
        fun ready() = candidates.filter { manager.getSource(it.key) != null }
        if (ready().isNotEmpty()) return ready()
        withTimeoutOrNull(RECOMMEND_SOURCE_WAIT_MS) {
            while (ready().isEmpty()) delay(300)
        }
        return ready()
    }

    /** 退出前的那批推荐先铺上，网络回来再换（冷启动首屏不该是一段错误文案）。 */
    private fun hydrateRecommendFromCache() {
        val restored = readCacheFor(_recommendMode.value) ?: return
        if (restored.comics.isEmpty()) return
        _recommend.value = restored
        // 缓存当作「刚拉过」：启动时不再自动打网络，只铺上次退出前的那批。
        // 之后回到首页超过节流窗口才会自动刷新，用户主动「换一批」不受此限。
        recommendLoaded = true
        lastRecommendAt = System.currentTimeMillis()
    }

    /** 读某一档的快照；键按档分开，切档不会串内容。 */
    private fun readCacheFor(mode: HomeRecommendMode): HomeRecommendState? {
        val raw = recommendPrefs.getString(cacheKeyFor(mode), null) ?: return null
        return runCatching { readRecommendSnapshot(raw) }.getOrNull()
    }

    private fun writeRecommendCache(state: HomeRecommendState) {
        recommendPrefs.put(cacheKeyFor(_recommendMode.value), writeSnapshot(state))
    }

    private fun cacheKeyFor(mode: HomeRecommendMode): String = "recommend_snapshot_${mode.key.lowercase()}"

    private companion object {
        /** 题材榜窗口：固定最近 30 天（不跟随统计页的范围切换）。 */
        const val RECOMMEND_WINDOW_DAYS = 30

        /** 一行放几本。 */
        const val RECOMMEND_LIMIT = 10

        /** 自动刷新（启动/回首页）的最小间隔，避免往复导航把源打爆。 */
        const val RECOMMEND_AUTO_REFRESH_INTERVAL_MS = 120_000L

        /** 等 JS 源注册上界的窗口。 */
        const val RECOMMEND_SOURCE_WAIT_MS = 10_000L

        const val KEY_RECOMMEND_MODE = "recommend_mode"

        /**
         * 推荐取数的一个源 + 它的搜索排序候选。
         *
         * [sortOptions] 的每一项是**一次 search 的 options 实参**（元素为各 optionList 里的
         * "值-文案" 左半边），不是单个字符串 —— 因为有的源搜索是**多段**的（MangaDex 是
         * 排序 / 内容分级 / 状态三段，缺一段会在源脚本里拼出字面量 "undefined" 的查询串）。
         * 空列表 = 这个源不吃 options（爱看漫、拷贝漫画）。
         */
        data class RecommendSourceDef(
            val key: String,
            val displayName: String,
            val sortOptions: List<List<String?>>,
            val maxPage: Int,
        )

        /** 正常档：MangaDex / 爱看漫 / 拷贝漫画。 */
        val NORMAL_RECOMMEND_SOURCES = listOf(
            RecommendSourceDef(
                key = "manga_dex",
                displayName = "MangaDex",
                // 后两段固定 "any"（不筛分级 / 状态）：源脚本里 options[1]/options[2] 一旦不是 "any"
                // 就会被拼进 URL，给 null 同样会拼出 "undefined"。
                sortOptions = listOf(
                    listOf("popular", "any", "any"),
                    listOf("recent", "any", "any"),
                    listOf("updated", "any", "any"),
                    listOf("rating", "any", "any"),
                ),
                maxPage = 10,
            ),
            RecommendSourceDef(
                key = "ikmmh",
                displayName = "爱看漫",
                sortOptions = listOf(emptyList()),
                // 爱看漫的 search 没有分页（源里 maxPage 恒 1），深页只会给空结果。
                maxPage = 1,
            ),
            RecommendSourceDef(
                key = "copy_manga",
                displayName = "拷贝漫画",
                sortOptions = listOf(emptyList()),
                maxPage = 10,
            ),
        )

        /** R18 档：禁漫天堂 / nhentai。 */
        val R18_RECOMMEND_SOURCES = listOf(
            RecommendSourceDef(
                key = "jm",
                displayName = "禁漫天堂",
                // jm.js 的 search optionList 取值（源里 options[0]，传错会静默退回默认排序）。
                sortOptions = listOf(
                    listOf("mr"), listOf("mv"), listOf("mv_t"), listOf("mp"), listOf("tf"),
                ),
                maxPage = 10,
            ),
            RecommendSourceDef(
                key = "nhentai",
                displayName = "nhentai",
                sortOptions = listOf(
                    listOf("date"), listOf("popular-week"), listOf("popular"),
                ),
                maxPage = 10,
            ),
        )

        fun sourcesFor(mode: HomeRecommendMode): List<RecommendSourceDef> = when (mode) {
            HomeRecommendMode.NORMAL -> NORMAL_RECOMMEND_SOURCES
            HomeRecommendMode.R18 -> R18_RECOMMEND_SOURCES
        }
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
    state.tags.forEach {
        tags.put(
            JSONObject()
                .put("display", it.display)
                .put("pages", it.pages)
                .put("comics", it.comics),
        )
    }
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
                // 旧快照没这一枚 ⇒ 0。缓存那一格只用 `display`（首页副标题那串题材名），
                // 而重进首页就会按当天读数整片重算，所以这里不拿 0 去念成"零本"。
                comics = item.optInt("comics"),
                searchNamespace = "",
                searchRaw = "",
            )
        )
    }
    // batch 固定 0：缓存态是「起点」，不该被当成「换了一批」而随机跳焦点。
    return HomeRecommendState(tags = tagList, comics = comicList)
}
