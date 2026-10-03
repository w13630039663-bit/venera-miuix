package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryBoards
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.data.GelbooruClient
import com.venera.compose.gallery.data.GelbooruCredentials
import com.venera.compose.gallery.data.SafebooruClient
import com.venera.compose.gallery.data.ScoredBoard
import com.venera.compose.gallery.data.YandeReBoard
import com.venera.compose.gallery.data.YandeReClient

/**
 * 桌面侧的三站墙。三站都是真网络，取数面与 `android/GalleryAdaptersAndroid.kt` 里那三颗
 * 逐枚对齐（成员名、默认值、分页口径都不重新发明）。
 *
 * ⚠️ [GelbooruBoard] 与 [SafebooruBoard] 的差别只在凭据：Gelbooru 的 DAPI **匿名一律 401**
 * （实测，写在那颗 client 的类注释里），所以它必须拿到 [DesktopGalleryCredentials] 那枚
 * `GelbooruCredentials`；没配就是每轮都失败，而那笔失败会顺着 `GalleryDailyFeed` 的
 * `failures` 被界面说出来 —— 不许在这里替它兜成空池。
 */
class DesktopGalleryBoards(
    private val handle: DesktopGalleryHandle,
    private val credentials: GelbooruCredentials,
) : GalleryBoards {

    override val yandere: YandeReBoard by lazy { YandeReBoardImpl(handle) }
    override val gelbooru: ScoredBoard by lazy { GelbooruBoard(handle, credentials) }
    override val safebooru: ScoredBoard by lazy { SafebooruBoard(handle) }
}

private class YandeReBoardImpl(private val handle: DesktopGalleryHandle) : YandeReBoard {

    private val client: YandeReClient get() = YandeReClient.getInstance(handle.engine)

    override val pageSize: Int get() = YandeReClient.SEARCH_PAGE_SIZE
    override suspend fun searchPosts(tags: String, page: Int, limit: Int) = client.searchPosts(tags, page, limit)
    override suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>> = client.searchTags(term)
    override suspend fun fetchById(id: Long): Result<GalleryPost?> = client.fetchById(id)
    override suspend fun artistLinks(name: String) = client.artistLinks(name)
    override suspend fun resolveArtistAlias(name: String) = client.resolveArtistAlias(name)
    override suspend fun fetchDailyPopular() = client.fetchDailyPopular()
}

private class GelbooruBoard(
    private val handle: DesktopGalleryHandle,
    private val credentials: GelbooruCredentials,
) : ScoredBoard {

    private val client: GelbooruClient get() = GelbooruClient.getInstance(handle.engine, credentials)

    override val pageSize: Int get() = GelbooruClient.POOL_SIZE
    override suspend fun searchPosts(tags: String, page: Int, limit: Int) = client.searchPosts(tags, page, limit)
    override suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>> = client.searchTags(term)
    override suspend fun fetchById(id: Long): Result<GalleryPost?> = client.fetchById(id)
    override suspend fun fetchTopScored() = client.fetchTopScored()
}

private class SafebooruBoard(private val handle: DesktopGalleryHandle) : ScoredBoard {

    private val client: SafebooruClient get() = SafebooruClient.getInstance(handle.engine)

    override val pageSize: Int get() = SafebooruClient.POOL_SIZE
    override suspend fun searchPosts(tags: String, page: Int, limit: Int) = client.searchPosts(tags, page, limit)
    override suspend fun searchTags(term: String): Result<List<GalleryTagSuggestion>> = client.searchTags(term)
    override suspend fun fetchById(id: Long): Result<GalleryPost?> = client.fetchById(id)
    override suspend fun fetchTopScored() = client.fetchTopScored()
}
