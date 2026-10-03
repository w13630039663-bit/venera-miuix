package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.network.HostCircuitBreaker
import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.data.platform.android.AndroidKeyValueStore
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryAvatarProbeEndpoint
import com.venera.compose.gallery.domain.GalleryTitleTag
import kotlinx.coroutines.flow.StateFlow

/**
 * `GalleryBusinessApi` 十二颗契约的 **Android 适配器**（契约本体留在同目录那颗）。
 *
 * ## 为什么单独一颗、且落在 `android/` 子目录
 *
 * 契约面只有 `StateFlow` + `Result` + 本仓数据模型，一句平台类型都没有，桌面侧要用同一套契约另接一份实现；
 * 而这十三颗的构造参数 `Context` 正是它们上不了桌面编译面的原因。目录 `android/` 被
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

    override val favorites: StateFlow<List<GalleryFavorite>> get() = store.favorites
    override suspend fun toggle(post: GalleryPost): Boolean = store.toggle(post)
    override suspend fun removeAll(uids: Collection<String>) = store.removeAll(uids)
    override suspend fun clear() = store.clear()
    override fun consumeNotice(): String? = store.consumeNotice()
}

internal class AndroidGalleryArtistFollows(private val context: Context) : GalleryArtistFollows {

    private val store: GalleryArtistFollowsStore get() = GalleryArtistFollowsStore.getInstance(context)

    override val follows: StateFlow<List<GalleryArtistFollow>> get() = store.follows
    override suspend fun toggle(site: GallerySite, name: String): Boolean = store.toggle(site, name)
    override fun consumeNotice(): String? = store.consumeNotice()
}

internal class AndroidGalleryArtistAvatars(private val context: Context) : GalleryArtistAvatars {

    private val store: GalleryArtistAvatarStore get() = GalleryArtistAvatarStore.getInstance(context)

    override fun peek(site: GallerySite, name: String, source: String): String? = store.peek(site, name, source)
    override suspend fun remember(site: GallerySite, name: String, source: String, avatar: String?) =
        store.remember(site, name, source, avatar)
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
