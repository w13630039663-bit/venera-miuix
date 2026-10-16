package com.venera.desktop.gallery.ui

import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.gallery.data.GalleryArtistAvatars
import com.venera.compose.gallery.data.GalleryArtistDirectory
import com.venera.compose.gallery.data.GalleryArtistFollows
import com.venera.compose.gallery.data.GalleryBoards
import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryCredentials
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryFavorites
import com.venera.compose.gallery.data.GalleryNetworkHygiene
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryPreferences
import com.venera.compose.gallery.data.GalleryReverseSearch
import com.venera.compose.gallery.data.GalleryStoreFactory
import com.venera.compose.gallery.data.GalleryTagLexicon
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.data.SauceNaoPage
import com.venera.compose.gallery.data.ScoredBoard
import com.venera.compose.gallery.data.YandeReBoard
import com.venera.compose.gallery.data.GelbooruIdentity
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.DanbooruArtistCredits
import com.venera.compose.gallery.data.PixivArtworkAuthor
import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryAvatarProbeEndpoint
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
import com.venera.compose.security.guard.GuardRule
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.StateFlow

/**
 * 测试侧的画廊端口（桌面第三十一批 批 31）。
 *
 * ## 它补的是哪一格
 *
 * 桌面测试从前只有两层：`FakeHttpEngine`（替网络）与 `TestBoards`（替三站客户端，住在
 * `DesktopFailureReadoutTest` 里而且是 private）。**取数链那三本账（逐腿页码 / 去重键 / 到底名单）
 * 因此从来没有真跑过** —— 批 23 那颗扰动 p3（"页码账另造、恒发第 1 页"）只红在文本锚点上就是证据：
 * 那一格没有任何一条判据真跑过。这一颗把 `fetchWallPage(ports, …)` 整条链接起来跑，
 * 于是"少一批图"那种不崩不红字的形状第一次有了尺子。
 *
 * ## 未脚本的成员一律抛而不是给空值
 *
 * 十二枚构造参数全是必填，而今天只有 `boards` 有脚本。其余十一枚的每个成员都
 * `error("…没脚本…")` —— **不是偷懒，是这一族的规矩**：给空值/默认值会让"哪天有一条路开始吃它"
 * 静默通过（屏上少一列而测试全绿），抛出来则会把那一次变成红着要求补脚本。
 * 判据 ⑥ 钉的就是"这些 stub 不是恒真的装饰"。
 */
internal fun fakeGalleryPorts(boards: GalleryBoards): GalleryPorts = GalleryPorts(
    boards = boards,
    contentGuard = UnscriptedGuard,
    prefs = UnscriptedPrefs,
    favorites = UnscriptedFavorites,
    follows = UnscriptedFollows,
    avatars = UnscriptedAvatars,
    lexicon = UnscriptedLexicon,
    credentials = UnscriptedCredentials,
    stores = UnscriptedStores,
    artists = UnscriptedArtists,
    reverse = UnscriptedReverse,
    hygiene = UnscriptedHygiene,
)

/** 抛出去的那一句要能指到成员 —— "没脚本"三个字不够，得说清是哪一路开始被走了。 */
private fun unscripted(member: String): Nothing =
    error("fake GalleryPorts：成员「$member」没有脚本（这一路今天不需要它；需要就先在这里补，别给静默默认值）")

private val UnscriptedGuard = object : GalleryContentGuard {
    override val nsfwMaskMode: StateFlow<String> get() = unscripted("GalleryContentGuard.nsfwMaskMode")
    override val rules: StateFlow<List<GuardRule>> get() = unscripted("GalleryContentGuard.rules")
    override fun blockedGalleryRule(author: String, tags: List<String>): GuardRule? =
        unscripted("GalleryContentGuard.blockedGalleryRule")

    override suspend fun addRule(type: String, pattern: String, isRegex: Boolean): Long =
        unscripted("GalleryContentGuard.addRule")
}

private val UnscriptedPrefs = object : GalleryPreferences {
    override val galleryColumnMode: StateFlow<GalleryColumnMode> get() = unscripted("galleryColumnMode")
    override val galleryPreviewQuality: StateFlow<GalleryPreviewQuality> get() = unscripted("galleryPreviewQuality")
    override val galleryKeepScreenOn: StateFlow<Boolean> get() = unscripted("galleryKeepScreenOn")
    override val galleryVolumeKeyTurn: StateFlow<Boolean> get() = unscripted("galleryVolumeKeyTurn")
    override val galleryAutoPlaySec: StateFlow<Int> get() = unscripted("galleryAutoPlaySec")
    override val galleryPreload: StateFlow<GalleryPreloadMode> get() = unscripted("galleryPreload")
    override val galleryAnimated: StateFlow<GalleryAnimatedMode> get() = unscripted("galleryAnimated")
    override val galleryBackdrop: StateFlow<GalleryViewerBackdrop> get() = unscripted("galleryBackdrop")
    override val galleryBlockAi: StateFlow<Boolean> get() = unscripted("galleryBlockAi")
    override val galleryAiBadge: StateFlow<Boolean> get() = unscripted("galleryAiBadge")
    override val galleryHideTopBar: StateFlow<Boolean> get() = unscripted("galleryHideTopBar")
    override val galleryHideBottomBar: StateFlow<Boolean> get() = unscripted("galleryHideBottomBar")
    override val galleryWatercolorBeta: StateFlow<Boolean> get() = unscripted("galleryWatercolorBeta")
}

private val UnscriptedFavorites = object : GalleryFavorites {
    override val favorites: StateFlow<List<GalleryFavorite>> get() = unscripted("GalleryFavorites.favorites")
    override suspend fun toggle(post: GalleryPost): Boolean = unscripted("GalleryFavorites.toggle")
    override suspend fun removeAll(uids: Collection<String>) = unscripted("GalleryFavorites.removeAll")
    override suspend fun clear() = unscripted("GalleryFavorites.clear")
    override fun consumeNotice(): String? = unscripted("GalleryFavorites.consumeNotice")
}

private val UnscriptedFollows = object : GalleryArtistFollows {
    override val follows: StateFlow<List<GalleryArtistFollow>> get() = unscripted("GalleryArtistFollows.follows")
    override suspend fun toggle(site: GallerySite, name: String): Boolean = unscripted("GalleryArtistFollows.toggle")
    override fun consumeNotice(): String? = unscripted("GalleryArtistFollows.consumeNotice")
}

private val UnscriptedAvatars = object : GalleryArtistAvatars {
    override fun peek(site: GallerySite, name: String, source: String): String? = unscripted("GalleryArtistAvatars.peek")
    override suspend fun remember(site: GallerySite, name: String, source: String, avatar: String?) =
        unscripted("GalleryArtistAvatars.remember")
}

private val UnscriptedLexicon = object : GalleryTagLexicon {
    override suspend fun artistNames(names: List<String>): Set<String> = unscripted("GalleryTagLexicon.artistNames")
    override suspend fun translations(names: List<String>): Map<String, String> =
        unscripted("GalleryTagLexicon.translations")

    override suspend fun fetchTagCategories(pageUrl: String): Map<String, Int>? =
        unscripted("GalleryTagLexicon.fetchTagCategories")

    override suspend fun titleTags(names: List<String>): Map<String, GalleryTitleTag>? =
        unscripted("GalleryTagLexicon.titleTags")
}

private val UnscriptedCredentials = object : GalleryCredentials {
    override val gelbooruIdentity: StateFlow<GelbooruIdentity?> get() = unscripted("GalleryCredentials.gelbooruIdentity")
    override val sauceNaoHasKey: StateFlow<Boolean> get() = unscripted("GalleryCredentials.sauceNaoHasKey")
}

private val UnscriptedStores = object : GalleryStoreFactory {
    override fun open(name: String): KeyValueStore = unscripted("GalleryStoreFactory.open")
}

private val UnscriptedArtists = object : GalleryArtistDirectory {
    override suspend fun danbooruArtistUrls(name: String): Result<List<String>> =
        unscripted("GalleryArtistDirectory.danbooruArtistUrls")

    override suspend fun danbooruArtistCredits(name: String): Result<DanbooruArtistCredits> =
        unscripted("GalleryArtistDirectory.danbooruArtistCredits")

    override suspend fun pixivArtworkAuthor(illustId: Long): Result<PixivArtworkAuthor> =
        unscripted("GalleryArtistDirectory.pixivArtworkAuthor")

    override suspend fun pixivAvatarUrl(userId: Long): Result<String?> = unscripted("GalleryArtistDirectory.pixivAvatarUrl")
    override suspend fun probeAvatarUrl(endpoint: GalleryAvatarProbeEndpoint): Result<String?> =
        unscripted("GalleryArtistDirectory.probeAvatarUrl")
}

private val UnscriptedReverse = object : GalleryReverseSearch {
    override val maxResults: Int get() = unscripted("GalleryReverseSearch.maxResults")
    override suspend fun searchByFile(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        allowNsfw: Boolean,
    ): SauceNaoPage = unscripted("GalleryReverseSearch.searchByFile")

    override suspend fun searchByUrl(imageUrl: String, allowNsfw: Boolean): SauceNaoPage =
        unscripted("GalleryReverseSearch.searchByUrl")
}

private val UnscriptedHygiene = object : GalleryNetworkHygiene {
    override fun resetAllSiteBreakers() = unscripted("GalleryNetworkHygiene.resetAllSiteBreakers")
}

/** 一笔真发出去的取数（页码与 limit 都要能回读 —— 那两枚正是这一族最容易算错的地方）。 */
internal data class WallCall(val site: GallerySite, val query: String, val page: Int, val limit: Int)

/**
 * 按脚本回页的三站墙。
 *
 * [pages] 按**页号**索引（`pages[site][0]` = 第 1 页），而不是按"第几次被问"—— 站方的第 N 页内容是固定的，
 * 而"恒发第 1 页"这个缺陷恰恰只有在按页号索引时才看得见（按调用次序索引的话它只是把第 2 页的内容
 * 当成第 2 次调用给了出去，读数上一切正常）。脚本里没有那一页还发 ⇒ 当场抛：
 * "到底了还发"与"到底不再发"是这一族要钉的事，回一个空列表会让那条判据变成装饰。
 *
 * [failFirst] 是"这一站第一次被问的时候抖了"：那一笔不计入页号脚本（回失败），从第二次起照脚本给。
 * 批 8 那个缺陷的现场就是这一档 —— 没有它，"缺席的腿下一轮补第 1 页"这一路只能测到"它又失败一次"。
 */
internal class ScriptedBoards(
    private val pageSize: Map<GallerySite, Int>,
    private val pages: Map<GallerySite, List<Result<List<GalleryPost>>>>,
    private val failFirst: Set<GallerySite> = emptySet(),
) : GalleryBoards {

    private val queue = ConcurrentLinkedQueue<WallCall>()

    /** 三腿并发取数，所以账本用并发队列收（`ArrayList` 在 `async` 下会丢写）。 */
    val calls: List<WallCall> get() = queue.toList()

    private fun script(site: GallerySite) = WallScript(site, pageSize.getValue(site), pages[site].orEmpty(), site in failFirst, queue)

    // 三本脚本各自懒装：这一轮没发出去的站压根不需要脚本（`pageSize` 少一项时它抛的是
    // "这一站的档位没给"，而不是把缺档静默补成 0 或 32）。
    private val yandereScript by lazy { script(GallerySite.YANDERE) }
    private val gelbooruScript by lazy { script(GallerySite.GELBOORU) }
    private val safebooruScript by lazy { script(GallerySite.SAFEBOORU) }

    override val yandere: YandeReBoard by lazy {
        val script = yandereScript
        object : YandeReBoard {
            override val pageSize: Int get() = script.pageSize
            override suspend fun searchPosts(tags: String, page: Int, limit: Int) = script.searchPosts(tags, page, limit)
            override suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>> =
                unscripted("YandeReBoard.searchTags")

            override suspend fun fetchById(id: Long): Result<GalleryPost?> = unscripted("YandeReBoard.fetchById")
            override suspend fun artistLinks(name: String): Result<List<String>> = unscripted("YandeReBoard.artistLinks")
            override suspend fun resolveArtistAlias(name: String): Result<String?> = unscripted("YandeReBoard.resolveArtistAlias")
            override suspend fun fetchDailyPopular(): Result<List<GalleryPost>> = unscripted("YandeReBoard.fetchDailyPopular")
        }
    }

    override val gelbooru: ScoredBoard by lazy { scoredBoard(gelbooruScript) }
    override val safebooru: ScoredBoard by lazy { scoredBoard(safebooruScript) }

    private fun scoredBoard(script: WallScript): ScoredBoard = object : ScoredBoard {
        override val pageSize: Int get() = script.pageSize
        override suspend fun searchPosts(tags: String, page: Int, limit: Int) = script.searchPosts(tags, page, limit)
        override suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>> = unscripted("ScoredBoard.searchTags")
        override suspend fun fetchById(id: Long): Result<GalleryPost?> = unscripted("ScoredBoard.fetchById")
        override suspend fun fetchTopScored(): Result<List<GalleryPost>> = unscripted("ScoredBoard.fetchTopScored")
    }
}

private class WallScript(
    val site: GallerySite,
    val pageSize: Int,
    private val pages: List<Result<List<GalleryPost>>>,
    private val failFirst: Boolean,
    private val calls: ConcurrentLinkedQueue<WallCall>,
) {
    private var attempts = 0

    suspend fun searchPosts(query: String, page: Int, limit: Int): Result<List<GalleryPost>> {
        calls += WallCall(site, query, page, limit)
        attempts += 1
        if (failFirst && attempts == 1) return Result.failure(IllegalStateException("这一站第一次发的时候抖了"))
        val at = page - 1
        check(at in pages.indices) {
            "「$site」的脚本只有 ${pages.size} 页，而第 $page 页被发了出去 —— 到底了还在发，或那一腿的页码账算错了"
        }
        return pages[at]
    }
}
