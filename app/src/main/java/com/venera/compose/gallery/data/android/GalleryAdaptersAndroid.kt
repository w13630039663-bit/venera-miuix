package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.network.HostCircuitBreaker
import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.data.platform.android.AndroidKeyValueStore
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryAvatarProbeEndpoint
import com.venera.compose.gallery.domain.GalleryBrowseRow
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.compose.gallery.domain.PixivSearchFilter
import kotlinx.coroutines.flow.StateFlow

/**
 * `GalleryBusinessApi` 十三颗契约的 **Android 适配器**（契约本体留在同目录那颗）。
 *
 * ## 为什么单独一颗、且落在 `android/` 子目录
 *
 * 契约面只有 `StateFlow` + `Result` + 本仓数据模型，一句平台类型都没有，桌面侧要用同一套契约另接一份实现；
 * 而这十四颗的构造参数 `Context` 正是它们上不了桌面编译面的原因。目录 `android/` 被
 * `desktop/build.gradle.kts` 那颗全局的 android 目录排除挡在外面，包名仍写
 * `com.venera.compose.gallery.data`（Kotlin 允许包名不等于目录名）⇒ 同包的
 * `AndroidGalleryPorts.createPorts` 照常直接构造，`gallery/ui` 一行不动。
 *
 * 纪律（与 `AndroidBusinessPorts.kt` 从头守到尾的是同一条）：适配器**不在构造里调 `getInstance`**，
 * 全部走 `private val x: X get() = X.getInstance(context)` 在成员体里现取 ——
 * "第一次真正用到才建那颗单例"的时机与改造前逐点相同。
 */

internal class AndroidGalleryFavorites(private val context: Context) : GalleryFavorites {

    private val store: GalleryFavoritesStore get() = GalleryFavoritesStore.getInstance(context)
    private val account: PixivAccount get() = PixivAccount.getInstance(context)
    private val appClient: PixivAppClient get() = PixivAppClient.getInstance(context)

    override val favorites: StateFlow<List<GalleryFavorite>> get() = store.favorites

    /**
     * 收藏 / 取消收藏：**本地先落**（这一步是唯一保证成功的），pixiv 那一站再往站方同步一笔
     * （2026-10-18 用户「pixiv 站点收藏图片自动同步到 pixiv 站点」，并点头按推荐的档位做）。
     *
     * 四条纪律：
     * - **只有 pixiv 这一站往站方写**：booru 那三家没有写接口，于是不摆"会同步"的暗示，也不做假开关；
     * - 未登录或这一会儿取不到票据 ⇒ 只落本地，并把这句说上屏；
     * - `restrict` 按会员位给（会员私密、非会员公开）—— 私密书签是 pixiv 的会员档，
     *   非会员发过去会被站方拒 ⇒ 宁可公开，也不要留一条"看着收藏上了、其实站方没收"的假记录；
     * - 站方那一跳失败 ⇒ **本地那条留着**（用户真要收藏它），另说一句"站方那份没加上"。
     */
    override suspend fun toggle(post: GalleryPost): Boolean {
        if (post.site == GallerySite.PIXIV) {
            val userId = (account.authState.value as? PixivAuthState.SignedIn)?.userId
            val bearer = runCatching { account.bearerOrNull() }.getOrNull()
            if (bearer == null || userId.isNullOrBlank()) {
                store.notice("请先登录 pixiv 账号，站方收藏需要登录凭据")
                return false
            }
            val private = PixivPremium.of(userId) { appClient.userPremium(bearer, userId).getOrNull() }
            val outcome = appClient.bookmarkAdd(bearer, post.id, private = private)
            store.removeAll(listOf(post.uid))
            return outcome.isSuccess
        }
        val now = store.toggle(post)
        syncToPixiv(post, added = now)
        return now
    }

    override suspend fun removeAll(uids: Collection<String>) = store.removeAll(uids)
    override suspend fun clear() = store.clear()
    override fun consumeNotice(): String? = store.consumeNotice()

    /** 同步那一跳的落点。[added] = 这一次是"收藏"还是"取消"。 */
    private suspend fun syncToPixiv(post: GalleryPost, added: Boolean) {
        if (post.site != GallerySite.PIXIV) return
        val userId = (account.authState.value as? PixivAuthState.SignedIn)?.userId
        val bearer = runCatching { account.bearerOrNull() }.getOrNull()
        if (bearer == null || userId.isNullOrBlank()) {
            if (added) store.notice("这条只存进本地收藏 —— 没登录 pixiv，站方那份加不上")
            return
        }
        val private = PixivPremium.of(userId) { appClient.userPremium(bearer, userId).getOrNull() }
        val outcome = if (added) {
            appClient.bookmarkAdd(bearer, post.id, private = private)
        } else {
            appClient.bookmarkRemove(bearer, post.id)
        }
        val failure = outcome.exceptionOrNull()
        val line = when {
            failure != null && added ->
                "本地收藏好了，站方那份没加上：${failure.message ?: "pixiv 没答"}"
            failure != null ->
                "本地已取消，站方那份没撤掉：${failure.message ?: "pixiv 没答"}"
            added -> if (private) "已同步到 pixiv 的私密收藏" else "已同步到 pixiv 的公开收藏"
            else -> null // 取消成功不必再啰嗦一句
        }
        if (line != null) store.notice(line)
    }
}

internal class AndroidGalleryArtistFollows(private val context: Context) : GalleryArtistFollows {

    private val store: GalleryArtistFollowsStore get() = GalleryArtistFollowsStore.getInstance(context)

    override val follows: StateFlow<List<GalleryArtistFollow>> get() = store.follows
    override suspend fun toggle(site: GallerySite, name: String): Boolean = store.toggle(site, name)
    override suspend fun setFollowed(site: GallerySite, name: String, followed: Boolean) {
        store.setFollowed(site, name, followed)
    }
    override fun consumeNotice(): String? = store.consumeNotice()
}

internal class AndroidGalleryArtistAvatars(private val context: Context) : GalleryArtistAvatars {

    private val store: GalleryArtistAvatarStore get() = GalleryArtistAvatarStore.getInstance(context)

    override fun peek(site: GallerySite, name: String, source: String): String? = store.peek(site, name, source)
    override suspend fun remember(site: GallerySite, name: String, source: String, avatar: String?) =
        store.remember(site, name, source, avatar)
}

internal class AndroidGalleryHistory(private val context: Context) : GalleryBrowseHistory {

    private val store: GalleryHistoryStore get() = GalleryHistoryStore.getInstance(context)

    override val rows: StateFlow<List<GalleryBrowseRow>> get() = store.rows
    override suspend fun recordView(post: GalleryPost, at: Long) = store.recordView(post, at)
    override suspend fun clear() = store.clear()
    override suspend fun removeAll(uids: Collection<String>) = store.removeAll(uids)
    override fun consumeNotice(): String? = store.consumeNotice()
}

internal class AndroidGalleryTagLexicon(private val context: Context) : GalleryTagLexicon {

    private val dictionary: GalleryTagDictionary get() = GalleryTagDictionary.getInstance(context)
    private val categories: GalleryTagCategories get() = GalleryTagCategories.getInstance(context)

    override suspend fun artistNames(names: List<String>): Set<String> = dictionary.artistNames(names)
    override suspend fun translations(names: List<String>): Map<String, String> = dictionary.translations(names)
    override suspend fun fetchTagCategories(pageUrl: String): Map<String, Int>? = categories.fetch(pageUrl)
    override suspend fun titleTags(names: List<String>): Map<String, GalleryTitleTag>? =
        dictionary.titleTags(names)
}

internal class AndroidGalleryCredentials(private val context: Context) : GalleryCredentials {

    private val gelbooru: GelbooruAccount get() = GelbooruAccount.getInstance(context)
    private val sauceNao: SauceNaoAccount get() = SauceNaoAccount.getInstance(context)

    override val gelbooruIdentity: StateFlow<GelbooruIdentity?> get() = gelbooru.identity
    override val sauceNaoHasKey: StateFlow<Boolean> get() = sauceNao.hasKey
}

internal class AndroidGalleryStoreFactory(private val context: Context) : GalleryStoreFactory {
    override fun open(name: String): KeyValueStore = AndroidKeyValueStore(context, name)
}

internal class AndroidGelbooruBoard(private val context: Context) : ScoredBoard {
    private val client: GelbooruClient get() = GelbooruClient.getInstance(context)
    override val pageSize: Int get() = GelbooruClient.POOL_SIZE
    override suspend fun searchPosts(tags: String, page: Int, limit: Int) = client.searchPosts(tags, page, limit)
    override suspend fun searchTags(term: String) = client.searchTags(term)
    override suspend fun fetchById(id: Long) = client.fetchById(id)
    override suspend fun fetchTopScored() = client.fetchTopScored()
}

/**
 * Gelbooru 时间窗的定界取数（2026-10-13）：状态机在 `gallery/domain`，这里只把两笔问句递到那颗 client。
 *
 * 走 [GelbooruClient] 而不并进 [AndroidGelbooruBoard]：那颗 `ScoredBoard` 是 Gelbooru 与 Safebooru
 * 共用的契约，往公共口加成员就得给 Safebooru 挂一枚"永远失败"的假实现（理由见 `GalleryBusinessApi.kt`
 * 里 `YandeReBoard.fetchDailyPopular` 与 `GelbooruWindowProbeSource` 上那两段说明）。
 */
internal class AndroidGelbooruWindowProbe(private val context: Context) : GelbooruWindowProbeSource {

    private val client: GelbooruClient get() = GelbooruClient.getInstance(context)

    override suspend fun fetchLatestPostId(): Result<Long> = client.fetchLatestId()

    override suspend fun probeWindowStart(atLeastId: Long): Result<GelbooruWindowProbe> =
        client.probeWindowStart(atLeastId)
}

internal class AndroidSafebooruBoard(private val context: Context) : ScoredBoard {
    private val client: SafebooruClient get() = SafebooruClient.getInstance(context)
    override val pageSize: Int get() = SafebooruClient.POOL_SIZE
    override suspend fun searchPosts(tags: String, page: Int, limit: Int) = client.searchPosts(tags, page, limit)
    override suspend fun searchTags(term: String) = client.searchTags(term)
    override suspend fun fetchById(id: Long) = client.fetchById(id)
    override suspend fun fetchTopScored() = client.fetchTopScored()
}

internal class AndroidYandeReBoard(private val context: Context) : YandeReBoard {
    private val client: YandeReClient get() = YandeReClient.getInstance(context)
    override val pageSize: Int get() = YandeReClient.SEARCH_PAGE_SIZE
    override suspend fun searchPosts(tags: String, page: Int, limit: Int) = client.searchPosts(tags, page, limit)
    override suspend fun searchTags(term: String) = client.searchTags(term)
    override suspend fun fetchById(id: Long) = client.fetchById(id)
    override suspend fun artistLinks(name: String) = client.artistLinks(name)
    override suspend fun resolveArtistAlias(name: String) = client.resolveArtistAlias(name)
    override suspend fun fetchDailyPopular() = client.fetchDailyPopular()
}

internal class AndroidGalleryBoards(private val context: Context) : GalleryBoards {
    override val yandere: YandeReBoard by lazy { AndroidYandeReBoard(context) }
    override val gelbooru: ScoredBoard by lazy { AndroidGelbooruBoard(context) }
    override val safebooru: ScoredBoard by lazy { AndroidSafebooruBoard(context) }
}

internal class AndroidGalleryArtistDirectory(private val context: Context) : GalleryArtistDirectory {

    private val danbooru: DanbooruArtistClient get() = DanbooruArtistClient.getInstance(context)
    private val pixiv: PixivClient get() = PixivClient.getInstance(context)
    private val probe: GalleryArtistProbeClient get() = GalleryArtistProbeClient.getInstance(context)

    override suspend fun danbooruArtistUrls(name: String) = danbooru.artistUrls(name)
    override suspend fun danbooruArtistCredits(name: String) = danbooru.artistCredits(name)
    override suspend fun pixivArtworkAuthor(illustId: Long) = pixiv.artworkAuthor(illustId)
    override suspend fun pixivAvatarUrl(userId: Long) = pixiv.avatarUrl(userId)
    override suspend fun probeAvatarUrl(endpoint: GalleryAvatarProbeEndpoint) = probe.avatarUrl(endpoint)
}

internal class AndroidGalleryPixivWorks(private val context: Context) : GalleryPixivWorks {

    private val client: PixivClient get() = PixivClient.getInstance(context)

    /** 带凭据那一侧（app-api）。与上面那颗**分开持有**，理由见 `PixivAppClient` 文件头。 */
    private val appClient: PixivAppClient get() = PixivAppClient.getInstance(context)

    override suspend fun ranking(mode: PixivRankingMode, page: Int) = client.ranking(mode, page)

    override suspend fun tagWorks(word: String, page: Int, filter: PixivSearchFilter) =
        client.tagWorks(word, page, filter)

    override suspend fun searchIllust(
        bearer: String,
        word: String,
        filter: PixivSearchFilter,
        premium: Boolean,
        nextUrl: String?,
    ) = appClient.searchIllust(bearer, word, filter, premium, nextUrl)

    override suspend fun popularPreview(
        bearer: String,
        word: String,
    ) = appClient.popularPreview(bearer, word)

    override suspend fun userPremium(bearer: String, userId: String) =
        appClient.userPremium(bearer, userId)

    override suspend fun searchUser(bearer: String, word: String, nextUrl: String?) =
        appClient.searchUser(bearer, word, nextUrl)

    override suspend fun fetchById(id: Long) = client.workById(id)

    /**
     * 作品页详情：**有票据先走登录腿**，登录腿"答上了却读不出图"才退匿名腿。
     *
     * 为什么屏上那些卡需要这一层（2026-10-18 第四批第七件）：卡的来源已经是登录腿
     * （关注画师 / 推荐 / 排行榜 / 书签），而详情从前只有匿名腿 —— 站方对未登录读者把
     * 成人向、仅登录可见、私密那几条整条撤图 ⇒ "卡明明在墙上、点开说没有"。
     *
     * ⚠️ 登录腿**失败（抛）时不退**：那一句（票据过期 / 403 / 这一条不存在）就是真成因，
     * 退匿名腿会把它盖成"这一条没有图" —— 静默交错那一族，本仓反复立过案。
     * 只有 isSuccess 且 getOrNull() == null（读得懂、可没给可看的图）才退，
     * 让匿名腿去分那四面旗标（它分得出来就抛，分不出来才交 null）。
     */
    override suspend fun workDetail(illustId: Long, bearer: String?): Result<PixivWorkDetail?> {
        if (bearer == null) return client.workDetail(illustId)
        val viaApp = appClient.illustDetail(bearer, illustId)
        return if (viaApp.isSuccess && viaApp.getOrNull() == null) client.workDetail(illustId) else viaApp
    }

    override suspend fun recommended(bearer: String, nextUrl: String?) =
        appClient.recommendedIllusts(bearer, nextUrl)

    override suspend fun rankingInApp(mode: PixivRankingMode, bearer: String, nextUrl: String?) =
        appClient.rankingIllusts(mode, bearer, nextUrl)

    override suspend fun following(
        bearer: String,
        userId: String,
        restrict: PixivRestrict,
        nextUrl: String?,
    ) = appClient.following(bearer, userId, restrict, nextUrl)

    override suspend fun followIllustFeed(
        bearer: String,
        restrict: PixivRestrict,
        nextUrl: String?,
    ) = appClient.followIllustFeed(bearer, restrict, nextUrl)

    override suspend fun recommendedUsers(bearer: String, nextUrl: String?) =
        appClient.recommendedUsers(bearer, nextUrl)

    override suspend fun bookmarkedIllusts(
        bearer: String,
        userId: String,
        restrict: PixivRestrict,
        nextUrl: String?,
    ) = appClient.bookmarkIllusts(bearer, userId, restrict, nextUrl)

    // ── 画师页那三条匿名腿（2026-10-19）：转发到 `client`（PixivClient，匿名）。──
    // 它们住在这条契约上而不是让 UI 直连，理由见 `GalleryPixivWorks.userHead` 的注释。
    override suspend fun illustComments(bearer: String, illustId: Long, nextUrl: String?) =
        appClient.illustComments(bearer, illustId, nextUrl)

    override suspend fun userHead(userId: Long) = client.userHead(userId)

    override suspend fun profileAll(userId: Long) = client.profileAll(userId)

    override suspend fun worksByIds(
        userId: Long,
        ids: List<Long>,
        workCategory: String,
        firstPage: Boolean,
    ) = client.worksByIds(userId, ids, workCategory, firstPage)

    // ── 画师页的登录版那几腿：转发到 `appClient`（PixivAppClient，带 Bearer）。──
    override suspend fun userIllusts(bearer: String, userId: String, type: String, nextUrl: String?) =
        appClient.userIllusts(bearer, userId, type, nextUrl)

    override suspend fun userDetail(bearer: String, userId: String) =
        appClient.userDetail(bearer, userId)

    override suspend fun followAdd(bearer: String, userId: String, private: Boolean) =
        appClient.followAdd(bearer, userId, private)

    override suspend fun followRemove(bearer: String, userId: String) =
        appClient.followRemove(bearer, userId)

    override suspend fun bookmarkAdd(bearer: String, illustId: Long, private: Boolean): Result<Unit> =
        appClient.bookmarkAdd(bearer, illustId, private)

    override suspend fun bookmarkRemove(bearer: String, illustId: Long): Result<Unit> =
        appClient.bookmarkRemove(bearer, illustId)


    override suspend fun relatedIllusts(bearer: String, illustId: Long): Result<PixivAppPage> =
        appClient.relatedIllusts(bearer, illustId)

    override suspend fun fetchNextPage(bearer: String, nextUrl: String): Result<PixivAppPage> =
        appClient.fetchNextPage(bearer, nextUrl)

    override suspend fun ugoiraClip(illustId: Long, original: Boolean): Result<PixivUgoiraClip?> =
        runCatching {
            var meta = client.ugoiraMeta(illustId).getOrNull()
            if (meta == null) {
                val bearer = runCatching { PixivAccount.getInstance(context).bearerOrNull() }.getOrNull()
                if (bearer != null) {
                    val appMeta = appClient.ugoiraMetadata(bearer, illustId).getOrNull()
                    if (appMeta != null) {
                        meta = appMeta
                    }
                }
            }
            if (meta == null) return@runCatching null
            PixivUgoiraStore.getInstance(context).clip(illustId, meta, original).getOrThrow()
        }
}

internal class AndroidGalleryReverseSearch(private val context: Context) : GalleryReverseSearch {

    private val client: SauceNaoClient get() = SauceNaoClient.getInstance(context)

    override val maxResults: Int get() = SauceNaoClient.SAUCE_NUM_RESULTS

    override suspend fun searchByFile(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        allowNsfw: Boolean,
    ): SauceNaoPage = client.searchByFile(bytes, fileName, mimeType, allowNsfw)

    override suspend fun searchByUrl(imageUrl: String, allowNsfw: Boolean): SauceNaoPage =
        client.searchByUrl(imageUrl, allowNsfw)
}

internal class AndroidGalleryNetworkHygiene(private val context: Context) : GalleryNetworkHygiene {
    override fun resetAllSiteBreakers() {
        GallerySite.entries.forEach { HostCircuitBreaker.reset(it.apiHost) }
    }
}
