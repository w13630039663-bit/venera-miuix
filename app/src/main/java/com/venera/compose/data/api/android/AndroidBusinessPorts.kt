package com.venera.compose.data.api.android

import android.content.Context
import com.venera.compose.data.api.AppearancePreferences
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.data.api.ComicPreferences
import com.venera.compose.data.api.ComicContentApi
import com.venera.compose.data.api.ContentGuard
import com.venera.compose.data.api.GuardRuleBook
import com.venera.compose.data.api.HttpTextFetch
import com.venera.compose.data.api.HttpTextResponse
import com.venera.compose.data.api.NamedStoreFactory
import com.venera.compose.data.api.NetworkHygiene
import com.venera.compose.data.api.NetworkPreferences
import com.venera.compose.data.api.ReaderPreferences
import com.venera.compose.data.api.ReadingHistory
import com.venera.compose.data.api.ReadingStats
import com.venera.compose.data.api.SourceCatalog
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.network.HostCircuitBreaker
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.data.platform.android.AndroidKeyValueStore
import com.venera.compose.data.prefs.AppearanceStyle
import com.venera.compose.data.prefs.NavigationBarStyle
import com.venera.compose.data.prefs.SurfaceMaterial
import com.venera.compose.data.prefs.TagTranslationMode
import com.venera.compose.data.prefs.ThemeColorSource
import com.venera.compose.data.prefs.ThemeMode
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.security.guard.ContentGuardManager
import com.venera.compose.source.ComicSource
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.explore.SourceExploration
import com.venera.compose.source.js.JsComicSource
import com.venera.compose.source.model.CategoryComicsOption
import com.venera.compose.source.model.CategoryComicsResult
import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.ComicDetails
import com.venera.compose.source.model.ResolvedImageConfig
import com.venera.compose.source.model.ResolvedThumbnailConfig
import com.venera.compose.source.model.SearchPage
import com.venera.compose.source.model.ThumbnailPage
import com.venera.compose.security.guard.GuardRule
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ExplorePagePart
import com.venera.compose.stats.ComicStatItem
import com.venera.compose.stats.DailyTrendItem
import com.venera.compose.stats.ReadingStatsManager
import com.venera.compose.stats.ReadingStatsSummary
import com.venera.compose.stats.TagStats
import kotlinx.coroutines.flow.StateFlow

/**
 * 业务 API 的 Android 接线：把六颗窄契约贴在既有单例上。
 *
 * ⚠️ **适配器的构造一律不许调 `getInstance`**（`BusinessPorts.kt` 的第三条硬约束）：
 * 每张卡都在成员方法体里现取。于是「第一次真正用到才建那颗单例」的时机与改造前逐点相同，
 * 启动阶段一行 IO 都不新增 —— 抄的是 `AndroidDatabasePorts.kt:56-67` 与
 * `LocalFavoritesManager.kt:829-835` 两次落地过的口径。
 *
 * 落点选 `data/api/android/` 而不与 `data/api/` 同层，是为了让「接口」与「认识 Android
 * 类型的那一半」物理分开：将来把这棵树放进桌面 `srcDir` 时，按子包点名排除实现这一半即可
 * （与 `desktop/build.gradle.kts:21-22` 那两行同构）。
 */
object AndroidBusinessPorts {

    const val PLATFORM = "android"

    @Volatile
    private var ports: BusinessPorts? = null

    fun install() {
        BusinessPorts.install(platform = PLATFORM) { handle -> createPorts(handle) }
    }

    private fun createPorts(handle: Any?): BusinessPorts = ports ?: synchronized(this) {
        ports ?: run {
            val context = handle as? Context
                ?: throw IllegalStateException(
                    "业务 API 的 Android 接线需要 Context，实际收到：${handle?.javaClass?.name ?: "null"}"
                )
            BusinessPorts(
                contentGuard = AndroidContentGuard(context),
                guardRuleBook = AndroidGuardRuleBook(context),
                readerPrefs = AndroidReaderPreferences(context),
                appearancePrefs = AndroidAppearancePreferences(context),
                comicPrefs = AndroidComicPreferences(context),
                networkPrefs = AndroidNetworkPreferences(context),
                stores = AndroidNamedStoreFactory(context),
                comics = AndroidComicContentApi(context),
                sources = AndroidSourceCatalog(context),
                history = AndroidReadingHistory(context),
                stats = AndroidReadingStats(context),
                network = AndroidNetworkHygiene(context),
                httpText = AndroidHttpTextFetch(context),
            ).also { ports = it }
        }
    }
}

/** 内容守卫的评估面。`ContentGuardManager` 自身仍在冻结清单里（`FREEZE-STATEMENT.md:11`），本轮**从外面包、未进一颗字符**。 */
private class AndroidContentGuard(private val context: Context) : ContentGuard {

    private val manager: ContentGuardManager get() = ContentGuardManager.getInstance(context)

    override val nsfwMaskMode: StateFlow<String> get() = manager.nsfwMaskMode
    override val blockAiComics: StateFlow<Boolean> get() = manager.blockAiComics

    override fun setNsfwMaskMode(mode: String) = manager.setNsfwMaskMode(mode)

    override fun setBlockAiComics(enabled: Boolean) = manager.setBlockAiComics(enabled)

    override fun maskStateFor(title: String, author: String, tags: List<String>, comicId: String, description: String): String =
        manager.coverMaskStateFor(title = title, author = author, tags = tags, comicId = comicId, description = description)

    override fun maskStateFor(sourceKey: String, title: String, author: String, tags: List<String>, comicId: String, description: String): String =
        manager.coverMaskStateFor(sourceKey = sourceKey, title = title, author = author, tags = tags, comicId = comicId, description = description)

    override fun maskStateFor(comic: Comic): String = manager.coverMaskStateFor(comic)

    override fun filterComics(comics: List<Comic>): List<Comic> = manager.filterComicModels(comics)

    override fun filterExploreParts(parts: List<ExplorePagePart>): List<ExplorePagePart> =
        manager.filterExploreParts(parts)
}

/** 用户屏蔽规则的读写口。 */
private class AndroidGuardRuleBook(private val context: Context) : GuardRuleBook {

    private val manager: ContentGuardManager get() = ContentGuardManager.getInstance(context)

    override val rules: StateFlow<List<GuardRule>> get() = manager.rules

    override suspend fun addRule(type: String, pattern: String, isRegex: Boolean): Long =
        manager.addRule(type, pattern, isRegex)

    override suspend fun deleteRule(id: Long): Boolean = manager.deleteRule(id)
}

/** 阅读历史。调用点的协程上下文（含 `NonCancellable`）留在调用点，这里只做一行转发。 */
private class AndroidReadingHistory(private val context: Context) : ReadingHistory {

    private val dao: HistoryDao get() = HistoryDao.getInstance(context)

    override val historyFlow: StateFlow<List<HistoryRecord>> get() = dao.historyFlow

    override suspend fun clearAll() = dao.clearAll()

    override suspend fun saveHistory(record: HistoryRecord) = dao.saveHistory(record)

    override suspend fun deleteHistory(comicId: String, sourceName: String) = dao.deleteHistory(comicId, sourceName)

    override suspend fun batchDeleteHistory(records: List<HistoryRecord>) = dao.batchDeleteHistory(records)
}

/** 阅读统计。 */
private class AndroidReadingStats(private val context: Context) : ReadingStats {

    private val manager: ReadingStatsManager get() = ReadingStatsManager.getInstance(context)

    override suspend fun recordSession(
        comicId: String,
        comicTitle: String,
        sourceName: String,
        tags: List<String>,
        chapterTitle: String,
        pagesRead: Int,
        durationSeconds: Long,
    ) = manager.recordSession(comicId, comicTitle, sourceName, tags, chapterTitle, pagesRead, durationSeconds)

    override suspend fun getSummary(): ReadingStatsSummary = manager.getSummary()

    override suspend fun getRecent14DaysTrend(): List<DailyTrendItem> = manager.getRecent14DaysTrend()

    override suspend fun getTopComics(limit: Int): List<ComicStatItem> = manager.getTopComics(limit)

    override suspend fun getTagStats(days: Int): TagStats = manager.getTagStats(days)
}

/** 熔断与取流客户端的运维动作。 */
private class AndroidNetworkHygiene(private val context: Context) : NetworkHygiene {

    override fun resetBreaker(host: String) = HostCircuitBreaker.reset(host)

    override fun resetAllBreakers() = HostCircuitBreaker.resetAll()

    override fun unreachableHosts(): Set<String> = HostCircuitBreaker.openHosts()

    override fun rebuildHttpClient() = VeneraNetworkClient.getInstance(context).rebuildClient()
}

/**
 * 一次取正文。**不做上下文切换**（见 `NetworkApi.kt` 的语义第 1 条：今天的调用点已经在
 * `withContext(Dispatchers.IO)` 里，这里再套一层就是凭空多一次切换）。
 */
private class AndroidHttpTextFetch(private val context: Context) : HttpTextFetch {

    override suspend fun fetchText(url: String): HttpTextResponse {
        val client = VeneraNetworkClient.getInstance(context).okHttpClient
        val request = okhttp3.Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            return HttpTextResponse(
                ok = response.isSuccessful && !body.isNullOrBlank(),
                code = response.code,
                body = body,
            )
        }
    }
}

/**
 * 偏好的四份分域贴在**同一颗** `VeneraPreferences` 上（它内部本来就是一份 StateFlow 镜像 +
 * 一份键值存储，分域只是把可见面收窄）。`get() = prefs.xxx` 交回的是**同一个 StateFlow 实例**，
 * 所以 `collectAsState()` 的订阅对象没换、重组时序没换（`AndroidDatabasePorts.kt:32-40` 同一手法）。
 */
private class AndroidReaderPreferences(private val context: Context) : ReaderPreferences {

    private val prefs: VeneraPreferences get() = VeneraPreferences.getInstance(context)

    override val defaultReadingMode: StateFlow<String> get() = prefs.defaultReadingMode
    override val clickToTurn: StateFlow<Boolean> get() = prefs.clickToTurn
    override val reverseTapDirection: StateFlow<Boolean> get() = prefs.reverseTapDirection
    override val volumeKeyTurn: StateFlow<Boolean> get() = prefs.volumeKeyTurn
    override val keepScreenOn: StateFlow<Boolean> get() = prefs.keepScreenOn
    override val nightFilter: StateFlow<Boolean> get() = prefs.nightFilter
    override val pageGapDp: StateFlow<Float> get() = prefs.pageGapDp
    override val autoScrollPageIntervalSec: StateFlow<Float> get() = prefs.autoScrollPageIntervalSec
    override val preloadImageCount: StateFlow<Int> get() = prefs.preloadImageCount
    override val reverseChapterOrder: StateFlow<Boolean> get() = prefs.reverseChapterOrder

    override fun setDefaultReadingMode(mode: String) = prefs.setDefaultReadingMode(mode)
    override fun setClickToTurn(enable: Boolean) = prefs.setClickToTurn(enable)
    override fun setReverseTapDirection(enabled: Boolean) = prefs.setReverseTapDirection(enabled)
    override fun setVolumeKeyTurn(enable: Boolean) = prefs.setVolumeKeyTurn(enable)
    override fun setKeepScreenOn(enable: Boolean) = prefs.setKeepScreenOn(enable)
    override fun setNightFilter(enable: Boolean) = prefs.setNightFilter(enable)
    override fun setPageGapDp(gap: Float) = prefs.setPageGapDp(gap)
    override fun setAutoScrollPageIntervalSec(sec: Float) = prefs.setAutoScrollPageIntervalSec(sec)
    override fun setPreloadImageCount(count: Int) = prefs.setPreloadImageCount(count)
    override fun setReverseChapterOrder(enabled: Boolean) = prefs.setReverseChapterOrder(enabled)
}

private class AndroidAppearancePreferences(private val context: Context) : AppearancePreferences {

    private val prefs: VeneraPreferences get() = VeneraPreferences.getInstance(context)

    override val themeMode: StateFlow<ThemeMode> get() = prefs.themeMode
    override val appearanceStyle: StateFlow<AppearanceStyle> get() = prefs.appearanceStyle
    override val navigationBarStyle: StateFlow<NavigationBarStyle> get() = prefs.navigationBarStyle
    override val surfaceMaterial: StateFlow<SurfaceMaterial> get() = prefs.surfaceMaterial
    override val tagTranslationMode: StateFlow<TagTranslationMode> get() = prefs.tagTranslationMode
    override val themeColorSource: StateFlow<ThemeColorSource> get() = prefs.themeColorSource
    override val themeSeedColor: StateFlow<Int> get() = prefs.themeSeedColor
    override val settingsHeroPath: StateFlow<String> get() = prefs.settingsHeroPath
    override val settingsQuoteAvatarPath: StateFlow<String> get() = prefs.settingsQuoteAvatarPath
    override val startPage: StateFlow<String> get() = prefs.startPage

    override fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)
    override fun setAppearanceStyle(style: AppearanceStyle) = prefs.setAppearanceStyle(style)
    override fun setNavigationBarStyle(style: NavigationBarStyle) = prefs.setNavigationBarStyle(style)
    override fun setSurfaceMaterial(material: SurfaceMaterial) = prefs.setSurfaceMaterial(material)
    override fun setTagTranslationMode(mode: TagTranslationMode) = prefs.setTagTranslationMode(mode)
    override fun setThemeColorSource(source: ThemeColorSource) = prefs.setThemeColorSource(source)
    override fun setThemeSeedColor(argb: Int) = prefs.setThemeSeedColor(argb)
    override fun setSettingsHeroPath(path: String) = prefs.setSettingsHeroPath(path)
    override fun setSettingsQuoteAvatarPath(path: String) = prefs.setSettingsQuoteAvatarPath(path)
    override fun setStartPage(tab: String) = prefs.setStartPage(tab)
}

private class AndroidComicPreferences(private val context: Context) : ComicPreferences {

    private val prefs: VeneraPreferences get() = VeneraPreferences.getInstance(context)

    override val defaultSearchTarget: StateFlow<String> get() = prefs.defaultSearchTarget
    override val checkUpdateOnStart: StateFlow<Boolean> get() = prefs.checkUpdateOnStart
    override var lastUpdateCheckAt: Long
        get() = prefs.lastUpdateCheckAt
        set(value) { prefs.lastUpdateCheckAt = value }
    override val favoriteSortOrder: StateFlow<String> get() = prefs.favoriteSortOrder
    override val newFavoriteAddTo: StateFlow<String> get() = prefs.newFavoriteAddTo
    override val quickFavorite: StateFlow<String?> get() = prefs.quickFavorite
    override val followUpdatesFolder: StateFlow<String?> get() = prefs.followUpdatesFolder
    override val secureScreen: StateFlow<Boolean> get() = prefs.secureScreen

    override fun setDefaultSearchTarget(key: String) = prefs.setDefaultSearchTarget(key)
    override fun setCheckUpdateOnStart(enable: Boolean) = prefs.setCheckUpdateOnStart(enable)
    override fun setFavoriteSortOrder(order: String) = prefs.setFavoriteSortOrder(order)
    override fun setNewFavoriteAddTo(value: String) = prefs.setNewFavoriteAddTo(value)
    override fun setQuickFavorite(folder: String?) = prefs.setQuickFavorite(folder)
    override fun setFollowUpdatesFolder(folder: String?) = prefs.setFollowUpdatesFolder(folder)
    override fun setSecureScreen(enabled: Boolean) = prefs.setSecureScreen(enabled)
}

private class AndroidNetworkPreferences(private val context: Context) : NetworkPreferences {

    private val prefs: VeneraPreferences get() = VeneraPreferences.getInstance(context)

    override val proxyType: StateFlow<String> get() = prefs.proxyType
    override val proxyHost: StateFlow<String> get() = prefs.proxyHost
    override val proxyPort: StateFlow<Int> get() = prefs.proxyPort
    override val downloadThreads: StateFlow<Int> get() = prefs.downloadThreads
    override val httpCacheMaxMb: StateFlow<Int> get() = prefs.httpCacheMaxMb
    override val comicStoragePath: StateFlow<String> get() = prefs.comicStoragePath
    override val cfPreferredIpEnabled: StateFlow<Boolean> get() = prefs.cfPreferredIpEnabled
    override val cfPreferredIps: StateFlow<String> get() = prefs.cfPreferredIps
    override val cfPreferredHosts: StateFlow<String> get() = prefs.cfPreferredHosts

    override fun setProxy(type: String, host: String, port: Int) = prefs.setProxy(type, host, port)
    override fun setDownloadThreads(threads: Int) = prefs.setDownloadThreads(threads)
    override fun setHttpCacheMaxMb(mb: Int) = prefs.setHttpCacheMaxMb(mb)
    override fun setComicStoragePath(path: String) = prefs.setComicStoragePath(path)
    override fun setCfPreferredIp(enabled: Boolean, ips: String, hosts: String) =
        prefs.setCfPreferredIp(enabled, ips, hosts)
}

/** 缓存的实例按名字各建一颗（与改造前 `AndroidKeyValueStore(app, NAME)` 逐字同形：同一名字每次新建）。 */
private class AndroidNamedStoreFactory(private val context: Context) : NamedStoreFactory {

    override fun open(name: String): KeyValueStore = AndroidKeyValueStore(context, name)
}

/**
 * 上游源的内容面。**每一枚都是一行转发**，参数默认值留在这颗适配器上与原处一致
 * （`page = 1`、`options = null`、`next = null`）。
 */
private class AndroidComicContentApi(private val context: Context) : ComicContentApi {

    private val manager: ComicSourceManager get() = ComicSourceManager.getInstance(context)

    override suspend fun search(sourceKey: String, keyword: String, page: Int, options: List<String?>?): Result<SearchPage> =
        manager.search(sourceKey, keyword, page, options)

    override suspend fun getComicDetails(sourceKey: String, comicId: String): Result<ComicDetails> =
        manager.getComicDetails(sourceKey, comicId)

    override suspend fun getChapterPages(sourceKey: String, comicId: String, chapterId: String): Result<ChapterPages> =
        manager.getChapterPages(sourceKey, comicId, chapterId)

    override suspend fun loadThumbnails(sourceKey: String, comicId: String, next: String?): Result<ThumbnailPage> =
        manager.loadThumbnails(sourceKey, comicId, next)

    override suspend fun resolveThumbnailConfigs(sourceKey: String, urls: List<String>): Map<String, ResolvedThumbnailConfig> =
        manager.resolveThumbnailConfigs(sourceKey, urls)

    override suspend fun resolveImageLoadingConfig(
        sourceKey: String,
        comicId: String,
        epId: String,
        imageKey: String,
        nl: String?,
        forceRefresh: Boolean,
    ): Result<ResolvedImageConfig> =
        manager.resolveImageLoadingConfig(sourceKey, comicId, epId, imageKey, nl, forceRefresh)

    override suspend fun getSourceExplorations(): List<SourceExploration> = manager.getSourceExplorations()

    override suspend fun loadExplorePage(sourceKey: String, pageIndex: Int, page: Int): Result<List<ExplorePagePart>> =
        manager.loadExplorePage(sourceKey, pageIndex, page)

    override suspend fun loadCategoryComics(
        sourceKey: String,
        category: String,
        param: String?,
        options: List<String>,
        page: Int,
    ): Result<CategoryComicsResult> = manager.loadCategoryComics(sourceKey, category, param, options, page)

    override suspend fun getCategoryComicsOptions(
        sourceKey: String,
        category: String,
        param: String?,
    ): Result<List<CategoryComicsOption>> = manager.getCategoryComicsOptions(sourceKey, category, param)

    override suspend fun loadCategoryRanking(sourceKey: String, option: String, page: Int): Result<List<Comic>> =
        manager.loadCategoryRanking(sourceKey, option, page)

    override suspend fun tagSuggestionKeyword(sourceKey: String, namespace: String, raw: String): String? =
        (manager.getSource(sourceKey) as? JsComicSource)?.onTagSuggestionSelected(namespace, raw)
}

/** 装了哪些源、当前选哪个、活得怎么样。 */
private class AndroidSourceCatalog(private val context: Context) : SourceCatalog {

    private val manager: ComicSourceManager get() = ComicSourceManager.getInstance(context)

    override val sourcesFlow: StateFlow<List<ComicSource>> get() = manager.sourcesFlow
    override val activeSourceKey: StateFlow<String> get() = manager.activeSourceKey
    override val latencyMapFlow: StateFlow<Map<String, Long>> get() = manager.latencyMapFlow
    override val availableUpdates: StateFlow<Map<String, String>> get() = manager.availableUpdates

    override fun getSource(key: String): ComicSource? = manager.getSource(key)

    override fun searchTargets(): List<ComicSource> = manager.searchTargets()

    override fun refreshPings() = manager.refreshPings()

    override suspend fun checkUpdates(force: Boolean): Int = manager.checkUpdates(force = force)
}
