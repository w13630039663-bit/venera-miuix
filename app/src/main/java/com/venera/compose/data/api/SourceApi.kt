package com.venera.compose.data.api

import com.venera.compose.source.ComicSource
import com.venera.compose.source.model.CategoryComicsOption
import com.venera.compose.source.model.CategoryComicsResult
import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicDetails
import com.venera.compose.source.model.ExplorePagePart
import com.venera.compose.source.model.ResolvedImageConfig
import com.venera.compose.source.model.ResolvedThumbnailConfig
import com.venera.compose.source.model.SearchPage
import com.venera.compose.source.model.ThumbnailPage
import com.venera.compose.source.explore.SourceExploration
import kotlinx.coroutines.flow.StateFlow

/**
 * 上游漫画源的两颗业务口：**取内容**与**问源清单**。
 *
 * `source/ComicSourceManager.kt` 的公开面是 77 枚（含 30 余枚只有 `feature/sourcemanage` 那两颗
 * 文件用的装源/卸源/排序/仓库地址动作）。审计 §二 第 8 行记的穿透点是**屏与 VM 直接抓这颗管理器**，
 * 而 `feature/sourcemanage/` 整域按方案 §七.1 判为「契约宽度≈实现宽度，窄接口在这颗上不成立」，
 * 所以这里只收两类真正的内容动作与清单动作：
 *
 * - [ComicContentApi] —— 搜索、详情、章节页码、缩略图、分类与探索页；外加 [tagSuggestionKeyword]
 *   这一枚"源级标签转换"，它把 `feature/SearchViewModel.kt:167` 那句
 *   `getSource(key) as? JsComicSource`（**穿过接口拿实现再调实现方法**）的两级搬进适配器。
 * - [SourceCatalog] —— 源清单、当前选中源、延迟与可用更新。
 *
 * `installedMeta` **故意不收**：它的类型 `InstalledSourceMeta` 声明在 `ComicSourceManager.kt:61`
 * 那颗类体里，收进契约等于让 `data/api` import 实现类去拿一个嵌套类型。范围内只有一颗文件要它
 * （`feature/settings/PreferredIpSpeedTestScreen.kt:88`），解锁条件 = 与 Part B 的 W1 同批把
 * 那族嵌套类型搬进 `source/model/`。
 *
 * `SourceSearchResult` 同一条前提：它是 `ComicSourceManager` 的嵌套 data class，而
 * `feature/SearchViewModel.kt` 构造它六次 —— 那颗文件今天的取用已全部改引这两颗契约，
 * 剩下的只有那六处**类型引用位**（守卫的断言 C 盯着它，解锁条件同为 W1）。
 */

/** 向一个源要内容。返回的全是 `source/model/` 里本来就中性（无 Android 类型）的模型。 */
interface ComicContentApi {

    suspend fun search(
        sourceKey: String,
        keyword: String,
        page: Int = 1,
        options: List<String?>? = null,
    ): Result<SearchPage>

    suspend fun getComicDetails(sourceKey: String, comicId: String): Result<ComicDetails>

    suspend fun getChapterPages(sourceKey: String, comicId: String, chapterId: String): Result<ChapterPages>

    suspend fun loadThumbnails(sourceKey: String, comicId: String, next: String? = null): Result<ThumbnailPage>

    /** 一批 URL 的取图配置（请求头/降采样），没解析出来的那条不在返回的 Map 里。 */
    suspend fun resolveThumbnailConfigs(sourceKey: String, urls: List<String>): Map<String, ResolvedThumbnailConfig>

    /**
     * 动态图源那一页的真实地址解析（源 JS 每次给新签名，过期就得重解）。
     *
     * `nl` 是上一轮解析拿到的续页游标；`forceRefresh` 绕过解析缓存 —— 阅读器重试链靠这两枚
     * 参数区分「首轮」与「上一轮地址已失效」，语义与原处逐字一致。
     */
    suspend fun resolveImageLoadingConfig(
        sourceKey: String,
        comicId: String,
        epId: String,
        imageKey: String,
        nl: String? = null,
        forceRefresh: Boolean = false,
    ): Result<ResolvedImageConfig>

    suspend fun getSourceExplorations(): List<SourceExploration>

    suspend fun loadExplorePage(sourceKey: String, pageIndex: Int, page: Int = 1): Result<List<ExplorePagePart>>

    suspend fun loadCategoryComics(
        sourceKey: String,
        category: String,
        param: String?,
        options: List<String>,
        page: Int = 1,
    ): Result<CategoryComicsResult>

    suspend fun getCategoryComicsOptions(
        sourceKey: String,
        category: String,
        param: String?,
    ): Result<List<CategoryComicsOption>>

    suspend fun loadCategoryRanking(sourceKey: String, option: String, page: Int = 1): Result<List<Comic>>

    /**
     * 标签联想被点击时的**源级转换**（官方 `search.onTagSuggestionSelected`）。
     *
     * 只有 JS 源有这条规则，返回 null 表示该源没有专用语法 —— 与改造前 `as? JsComicSource`
     * 拿不到就跳过是完全同一个判据，只是那一层类型探测从 UI 挪进了适配器。
     */
    suspend fun tagSuggestionKeyword(sourceKey: String, namespace: String, raw: String): String?
}

/** 装了哪些源、当前选哪个、活得怎么样。 */
interface SourceCatalog {

    val sourcesFlow: StateFlow<List<ComicSource>>

    /** 当前选中源（`"ALL"` 表示全部源）。 */
    val activeSourceKey: StateFlow<String>

    /** 各源最近一次测得的延迟毫秒数；没测过的源不在表里。 */
    val latencyMapFlow: StateFlow<Map<String, Long>>

    /** 仓库里有更新的那些源：key → 远端版本号。 */
    val availableUpdates: StateFlow<Map<String, String>>

    /** 按 key 取一颗源；没装或已停用给 null。返回的是**接口** `ComicSource`，不是实现类。 */
    fun getSource(key: String): ComicSource?

    /** 参与"全部源"聚合检索的那些源。 */
    fun searchTargets(): List<ComicSource>

    /** 重新测一轮延迟（首页的源状态行要它）。 */
    fun refreshPings()

    /**
     * 查一轮源更新，返回有更新的源的数量。
     *
     * 仓库地址不参数化：实现类那个 `DEFAULT_REPO_URL` 是它自己的私有常量，用户在源管理页里
     * 改的是 `repoUrl` 那条流，静默检查与手动检查都读同一份 —— 传参进去反而会绕过它。
     */
    suspend fun checkUpdates(force: Boolean = false): Int
}
