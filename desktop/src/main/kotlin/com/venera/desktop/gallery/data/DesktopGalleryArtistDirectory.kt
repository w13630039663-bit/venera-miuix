package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.DanbooruArtistClient
import com.venera.compose.gallery.data.DanbooruArtistCredits
import com.venera.compose.gallery.data.GalleryArtistDirectory
import com.venera.compose.gallery.data.GalleryArtistProbeClient
import com.venera.compose.gallery.data.PixivArtworkAuthor
import com.venera.compose.gallery.data.PixivClient
import com.venera.compose.gallery.domain.GalleryAvatarProbeEndpoint

/**
 * 桌面侧的画师出处与头像：三颗客户端**全部转发**（批 42 起 pixiv 那两枚也不再交抛桩）。
 * 三颗 client 都只收 `HttpEngine`（`PixivClient` 的注释原话："只收 HttpEngine，不收 Context"），
 * 而它们都在 `gallery/data/` 里 ⇒ 本来就在桌面编译面上 —— 当年"接不了"的那条前提从来没被量过。
 *
 * 取用面与 `android/GalleryAdaptersAndroid.kt` 里那颗 `AndroidGalleryArtistDirectory` 逐枚对齐，
 * 成员名一个不改 —— 两端的差别只在"这一路有没有接"，不在"这一路叫什么"。
 *
 * 头像链的落点顺序因此是：先查本地地址档（[com.venera.compose.gallery.data.GalleryArtistAvatars]，
 * 已共享、桌面真落盘）→ 链接表里有 pixiv 用户号先问 `pixivAvatarUrl` → 再走 `probeAvatarUrl`
 * （fanbox / Mastodon 两路）→ 都空才首字母座。次序与"失败不落档"两条都照 Android 那一颗
 * （`GalleryArtistRows.probeArtistAvatar`），桌面不另判一套。
 */
class DesktopGalleryArtistDirectory(private val handle: DesktopGalleryHandle) : GalleryArtistDirectory {

    private val danbooru: DanbooruArtistClient get() = DanbooruArtistClient.getInstance(handle.engine)
    private val probe: GalleryArtistProbeClient get() = GalleryArtistProbeClient.getInstance(handle.engine)
    private val pixiv: PixivClient get() = PixivClient.getInstance(handle.engine)

    override suspend fun danbooruArtistUrls(name: String): Result<List<String>> = danbooru.artistUrls(name)

    override suspend fun danbooruArtistCredits(name: String): Result<DanbooruArtistCredits> =
        danbooru.artistCredits(name)

    override suspend fun probeAvatarUrl(endpoint: GalleryAvatarProbeEndpoint): Result<String?> =
        probe.avatarUrl(endpoint)

    // 批 42：这两枚不再交抛桩。本机实测 `https://www.pixiv.net/ajax/user/{id}` 走代理与裸连都回 200，
    // 而 pixiv 那一档在头像链里的位置与失败口径写在 `desktopArtistAvatarChain`（Android 同一次序）。
    override suspend fun pixivArtworkAuthor(illustId: Long): Result<PixivArtworkAuthor> =
        pixiv.artworkAuthor(illustId)

    override suspend fun pixivAvatarUrl(userId: Long): Result<String?> =
        pixiv.avatarUrl(userId)
}
