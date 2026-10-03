package com.venera.compose.gallery.data

import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryAvatarProbeEndpoint
import com.venera.compose.gallery.domain.GalleryTitleTag
import kotlinx.coroutines.flow.StateFlow

/**
 * 画廊侧的**业务 API 契约**（B7' / D1 的「状态类」那一半）。
 *
 * 为什么不并进漫画侧 `data/api/`：隔离口径要求画廊各侧自持，让 `gallery/ui` 去 import 漫画侧的
 * 业务口会把「屏蔽口径、分级模式」这些只读共享重新绑回一个发布者。为什么不写进 `GalleryPorts.kt`：
 * 那颗已经装着容器 + 守卫口 + 偏好口，再涨六颗就没人读得动了；同包内 `internal` 可见，
 * 所以 `AndroidGalleryPorts.createPorts` 照样能直接构造。
 *
 * 宽度一律按**消费面实数**（复算 `_qa/b7-recon.mjs`），没一枚是为「看起来完整」加的。
 * 适配器全部**不在构造里调 `getInstance`**（成员体里现取），于是「第一次真正用到才建那颗单例」的
 * 时机与改造前逐点相同 —— 与 `AndroidBusinessPorts.kt` 从头守到尾的是同一条纪律。
 *
 * ## 这颗文件一句平台类型都没有
 *
 * 原先「契约在上、接线在下」同处一颗，`Context` 把整颗钉死在 Android 面；S1 把下面那十三颗
 * 搬进 `android/GalleryAdaptersAndroid.kt` 后，这颗只剩 `StateFlow` + `Result` + 本仓数据模型，
 * 于是它是桌面编译面的一部分（`:desktop` 排掉 android 子目录就拿到了契约、没拿到接线）。
 */

/** 画廊图片收藏。`clear()`（整表）与 `removeAll()`（按 uid）是两条不同的路。 */
interface GalleryFavorites {

    val favorites: StateFlow<List<GalleryFavorite>>

    /** 给回**切换后**的状态（true = 现在收藏着）。 */
    suspend fun toggle(post: GalleryPost): Boolean

    suspend fun removeAll(uids: Collection<String>)

    suspend fun clear()

    /** 取走并清空那条「刚刚发生了什么」的提示；没有给 null。 */
    fun consumeNotice(): String?
}

/** 关注的画师。 */
interface GalleryArtistFollows {

    val follows: StateFlow<List<GalleryArtistFollow>>

    suspend fun toggle(site: GallerySite, name: String): Boolean

    fun consumeNotice(): String?
}

/** 画师头像的本地缓存位（地址落盘是 2026-10-01 的改判）。 */
interface GalleryArtistAvatars {

    /** 有记录给地址（空串也算记录，调用点自己 `ifBlank`）；没记录给 null = 该去取。 */
    fun peek(site: GallerySite, name: String, source: String): String?

    suspend fun remember(site: GallerySite, name: String, source: String, avatar: String?)
}

/** 标签词典 + 分类：三枚都是「给一堆 tag，问它是什么」。 */
interface GalleryTagLexicon {

    suspend fun artistNames(names: List<String>): Set<String>

    suspend fun translations(names: List<String>): Map<String, String>

    /** 那一页的分类计数；拿不到给 null（面板自己决定不显示）。 */
    suspend fun fetchTagCategories(pageUrl: String): Map<String, Int>?

    /**
     * 从 tag 里挑「可当标题」的那几枚。
     *
     * 返回 null 与返回空表是**两件事**（库没打开 vs 这批名字里确实没有），调用点按这条区分，
     * 判式不许在外面合并（`GalleryTagDictionary.kt:135-137` 的原口径）。
     */
    suspend fun titleTags(names: List<String>): Map<String, GalleryTitleTag>?
}

/**
 * 画廊侧问凭据只需要「配没配」，而这两枚今天正是以 `StateFlow` 形态被消费的
 * （`var x by mutableStateOf(...)` + 一路 `collect`）。
 *
 * ⚠️ 这里**原样交回同一个 flow 实例**，不做 `map { it != null }` 那种派生：派生要 `stateIn(scope)`，
 * 就得给契约造一颗 scope —— 那是新增协程容器、会改取消时机。§七.8 说的「两处变一处」是可选收益，
 * 不是本批的目标（本批的硬约束是行为零变更）。
 */
interface GalleryCredentials {

    val gelbooruIdentity: StateFlow<GelbooruIdentity?>

    val sauceNaoHasKey: StateFlow<Boolean>
}

/** 画廊侧的具名 KV（今天只有搜索缓存那一颗）。存储名由调用点原样递进来，收口不许改名 = 换数据文件。 */
interface GalleryStoreFactory {

    fun open(name: String): KeyValueStore
}

// 十三颗 `Android*` 适配器不住在这颗文件里，住在 `android/GalleryAdaptersAndroid.kt`。
// 搬出去的唯一原因是构造参数 `Context`：契约面一句平台类型都没有，可以进桌面编译面，
// 而 `android/` 子目录被 `:desktop` 的全局排除挡在外面。包名不变 ⇒ 同包构造点无需 import。

// ─────────────────────────── D1b：站点侧 ───────────────────────────

/**
 * 一站的墙。三站今天**各自是一颗类**，`when(site)` 表留在 UI 原处（本批只做纯改引，
 * 不把实测那 7 处三站表折成一遍 —— 那是 §十一 里明确拆出去默认不做的行为敏感项）。
 *
 * [pageSize] 是「这一站一次给多少条」，值就是各站 companion 里的那枚常量
 * （`POOL_SIZE` / `SEARCH_PAGE_SIZE`）；把它做成实例属性而不是让 UI 去 import 实现类拿常量，
 * 是因为 UI 本来就在问这个问题（`limitOf(site)` / `pageSizeFor(site)` 那两张表）。
 */
interface BoardSource {

    val pageSize: Int

    /** `limit` 不设默认值：调用点全部显式给数，让契约替它兜默认反而是新增行为。 */
    suspend fun searchPosts(tags: String, page: Int, limit: Int): Result<List<GalleryPost>>

    /** 只给 term：条数默认值留在实现侧，与改造前同一枚常量。 */
    suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>>

    suspend fun fetchById(id: Long): Result<GalleryPost?>
}

/** yande.re 独有的两枚（另两站没有别名解析与画师出处接口）。 */
interface YandeReBoard : BoardSource {

    suspend fun artistLinks(name: String): Result<List<String>>

    suspend fun resolveArtistAlias(name: String): Result<String?>

    /**
     * 昨日日榜（桌面首页「今日推荐」那一格取的正是它）。
     *
     * 只住在 `YandeReClient`（那颗的 `fetchDailyPopular`），Gelbooru/Safebooru 两站**没有**对应接口，
     * 所以它进 yande.re 的专属窄口而不是公共 `BoardSource` —— 进公共口就等于逼那两站的适配器
     * 写一枚"永远失败"的假实现，而假实现是本仓最忌的静默交错。
     */
    suspend fun fetchDailyPopular(): Result<List<GalleryPost>>
}

/**
 * 「这一站有按分数排的墙」—— 只有两站有（`GelbooruClient` / `SafebooruClient` 的 `fetchTopScored`），
 * `YandeReClient` **没有**，所以它不进公共 [BoardSource]（理由同 [YandeReBoard.fetchDailyPopular]，
 * 方向相反：那里是 yande.re 独有，这里是另两站独有）。
 *
 * 收窄 [GalleryBoards] 那两枚的声明类型对既有调用方**源码兼容**：子类型只加成员、不减成员，
 * 原先按 `BoardSource` 用它们的 `when(site)` 表一行都不用改。
 */
interface ScoredBoard : BoardSource {

    /** 一次给多少条由实现侧那枚 `POOL_SIZE` 定，契约不兜默认（口径同 [BoardSource.searchPosts]）。 */
    suspend fun fetchTopScored(): Result<List<GalleryPost>>
}

interface GalleryBoards {

    val yandere: YandeReBoard
    val gelbooru: ScoredBoard
    val safebooru: ScoredBoard
}

/** 画师出处与头像：三颗客户端的取用面合成一颗（成员名带站点前缀，因为两站都叫 `avatarUrl`）。 */
interface GalleryArtistDirectory {

    suspend fun danbooruArtistUrls(name: String): Result<List<String>>

    suspend fun danbooruArtistCredits(name: String): Result<DanbooruArtistCredits>

    suspend fun pixivArtworkAuthor(illustId: Long): Result<PixivArtworkAuthor>

    suspend fun pixivAvatarUrl(userId: Long): Result<String?>

    suspend fun probeAvatarUrl(endpoint: GalleryAvatarProbeEndpoint): Result<String?>
}

/** 以图搜图。返回类型 `SauceNaoPage` 与本文件同包（`gallery/data/SauceNao.kt:56`）。 */
interface GalleryReverseSearch {

    /**
     * 站方一次只回前 N 条 —— 这枚**是要给用户看的**（`GalleryReverseResults` 的「到底了」那行文案），
     * 所以它进契约，判据与 `BoardSource.pageSize` 同一条：UI 在问业务问题，不是在调参。
     */
    val maxResults: Int

    suspend fun searchByFile(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        allowNsfw: Boolean,
    ): SauceNaoPage

    suspend fun searchByUrl(imageUrl: String, allowNsfw: Boolean): SauceNaoPage
}

/**
 * 画廊侧的熔断卫生口。
 *
 * 与漫画侧 `data/api/NetworkApi.kt` 的 `NetworkHygiene.resetAllBreakers()` 是**同一条动作的两份**，
 * 按各侧自持不合并（`gallery/ui/GalleryFeedReadout.kt:32` 那条纪律：跨侧相似不许并成一颗）。
 * 循环体逐字照抄改造前 UI 那两行，一条 host 都不许多都不许少。
 */
interface GalleryNetworkHygiene {

    fun resetAllSiteBreakers()
}
