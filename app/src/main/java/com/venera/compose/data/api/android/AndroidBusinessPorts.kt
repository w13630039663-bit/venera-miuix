package com.venera.compose.data.api.android

import android.content.Context
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.data.api.ContentGuard
import com.venera.compose.data.api.GuardRuleBook
import com.venera.compose.data.api.HttpTextFetch
import com.venera.compose.data.api.HttpTextResponse
import com.venera.compose.data.api.NetworkHygiene
import com.venera.compose.data.api.ReadingHistory
import com.venera.compose.data.api.ReadingStats
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.network.HostCircuitBreaker
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.security.guard.ContentGuardManager
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

    override fun refresh() = dao.refresh()

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

    override suspend fun summary(): ReadingStatsSummary = manager.getSummary()

    override suspend fun recent14DaysTrend(): List<DailyTrendItem> = manager.getRecent14DaysTrend()

    override suspend fun topComics(limit: Int): List<ComicStatItem> = manager.getTopComics(limit)

    override suspend fun tagStats(days: Int): TagStats = manager.getTagStats(days)
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
