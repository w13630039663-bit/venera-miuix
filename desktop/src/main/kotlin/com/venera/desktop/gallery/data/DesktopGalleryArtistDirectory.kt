package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.DanbooruArtistClient
import com.venera.compose.gallery.data.DanbooruArtistCredits
import com.venera.compose.gallery.data.GalleryArtistDirectory
import com.venera.compose.gallery.data.GalleryArtistProbeClient
import com.venera.compose.gallery.data.PixivArtworkAuthor
import com.venera.compose.gallery.domain.GalleryAvatarProbeEndpoint

/**
 * 桌面侧的画师出处与头像：三颗客户端里**接了的**照 [com.venera.compose.gallery.data.GalleryArtistDirectory] 转发，
 * **没接的**（pixiv 那两枚）交 [PixivNotWiredOnDesktop]。
 *
 * 取用面与 `android/GalleryAdaptersAndroid.kt` 里那颗 `AndroidGalleryArtistDirectory` 逐枚对齐，
 * 成员名一个不改 —— 两端的差别只在"这一路有没有接"，不在"这一路叫什么"。
 *
 * 头像链的落点顺序因此是：先查本地地址档（[com.venera.compose.gallery.data.GalleryArtistAvatars]，
 * 已共享、桌面真落盘）→ 未命中走 `probeAvatarUrl`（fanbox / Mastodon 两路，两颗 client 都已脱
 * Context）→ 都空才首字母座。**pixiv 那一枚今天不在链上**，而 `GalleryArtistRows` 那句
 * "Pixiv 未接入，本行不代表该画师没有头像"就是为这个形状写的。
 */
class DesktopGalleryArtistDirectory(private val handle: DesktopGalleryHandle) : GalleryArtistDirectory {

    private val danbooru: DanbooruArtistClient get() = DanbooruArtistClient.getInstance(handle.engine)
    private val probe: GalleryArtistProbeClient get() = GalleryArtistProbeClient.getInstance(handle.engine)

    override suspend fun danbooruArtistUrls(name: String): Result<List<String>> = danbooru.artistUrls(name)

    override suspend fun danbooruArtistCredits(name: String): Result<DanbooruArtistCredits> =
        danbooru.artistCredits(name)

    override suspend fun probeAvatarUrl(endpoint: GalleryAvatarProbeEndpoint): Result<String?> =
        probe.avatarUrl(endpoint)

    // 下面两枚今天刻意不转 pixiv：本轮拍板"先不接入 pixiv"。真接的那天把这两行换成
    // pixiv.artworkAuthor(…) / pixiv.avatarUrl(…) 即可，形状已经对了。
    override suspend fun pixivArtworkAuthor(illustId: Long): Result<PixivArtworkAuthor> =
        Result.failure(PixivNotWiredOnDesktop())

    override suspend fun pixivAvatarUrl(userId: Long): Result<String?> =
        Result.failure(PixivNotWiredOnDesktop())
}
