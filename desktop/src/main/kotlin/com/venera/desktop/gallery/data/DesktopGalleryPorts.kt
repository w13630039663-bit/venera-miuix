package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryPorts
import com.venera.desktop.platform.DesktopLogger

/**
 * 桌面侧的画廊业务 API 接线。形状逐字照 `android/GalleryPortsAndroid.kt`（同一条容器的另一半）：
 * `install(platform, factory)` 只装 lambda、`of(handle)` 现取，`@Volatile` + `synchronized` 双检一次。
 *
 * ## 十二枚构造参数全是必填
 *
 * `GalleryPorts` 不给默认值，所以桌面必须一次交满 12 颗**存在的对象** —— 包括今天做不了的那几颗
 * （词典三枚、reverse 两枚、pixiv 两枚）。做不了的那几颗的内容是"显式失败 + 一句为什么"，
 * 不是 `null`、不是抛在装配那一刻：接线必须能建得出对象，屏才画得出来，
 * 而"这一路没接"要等到用户真走到那一步才被说出来。
 *
 * ## 取用时机与 Android 侧同点
 *
 * 这里没有一枚适配器在构造里取单例或读盘：全部走成员体里的 `get()`（`DesktopGalleryCredentials`
 * 那两枚 `StateFlow` 是唯一例外 —— Android 侧的 `GelbooruAccount` 也是在构造里读一次，
 * 照它，不改成每次现读）。
 *
 * ⚠️ 接线由集成负责调：`DesktopGalleryPorts.install(DesktopGalleryHandle.of(paths))` 要排在
 * `DesktopDatabasePorts.install(paths)` 之后 —— [DesktopGalleryContentGuard] 首问规则时会经
 * `DatabasePorts.of` 取库，未接线就抛（那是那颗容器自带的语义，这里不加 try/catch 咽掉）。
 */
object DesktopGalleryPorts {

    const val PLATFORM = "desktop"

    @Volatile
    private var ports: GalleryPorts? = null

    fun install(handle: DesktopGalleryHandle) {
        GalleryPorts.install(platform = PLATFORM) { received -> createPorts(received ?: handle) }
    }

    private fun createPorts(received: Any?): GalleryPorts = ports ?: synchronized(this) {
        ports ?: run {
            val handle = received as? DesktopGalleryHandle
                ?: throw IllegalStateException(
                    "画廊业务 API 的桌面接线需要 DesktopGalleryHandle，实际收到：${received?.javaClass?.name ?: "null"}",
                )
            // 凭据只建一颗：界面问的"配没配"与请求拼参数用的那两枚串必须是同一个事实。
            val credentials = DesktopGalleryCredentials(handle)
            GalleryPorts(
                contentGuard = DesktopGalleryContentGuard(handle),
                prefs = DesktopGalleryPreferences(handle.paths),
                favorites = DesktopGalleryFavorites(handle),
                follows = DesktopGalleryArtistFollows(handle),
                avatars = DesktopGalleryArtistAvatars(handle),
                lexicon = DesktopGalleryTagLexicon(handle),
                credentials = credentials,
                stores = DesktopStoreFactory(handle),
                boards = DesktopGalleryBoards(handle, credentials),
                artists = DesktopGalleryArtistDirectory(handle),
                reverse = DesktopGalleryReverseSearch(),
                hygiene = DesktopGalleryNetworkHygiene(),
            ).also { ports = it }
        }
    }

    /** 桌面这一路只用得上 [DesktopLogger] 一处；留给接线自持，避免各件自己 new 一份 stdout 包装。 */
    internal val logger = DesktopLogger
}
