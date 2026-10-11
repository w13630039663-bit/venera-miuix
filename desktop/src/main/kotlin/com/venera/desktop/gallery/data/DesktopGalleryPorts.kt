package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryPorts

/**
 * 桌面侧的画廊业务 API 接线。形状逐字照 `android/GalleryPortsAndroid.kt`（同一条容器的另一半）：
 * `install(platform, factory)` 只装 lambda、`of(handle)` 现取，`@Volatile` + `synchronized` 双检一次。
 *
 * ## 十二枚构造参数全是必填
 *
 * `GalleryPorts` 不给默认值，所以桌面必须一次交满 12 颗**存在的对象** —— 包括今天内容还是空的那几颗
 * （词典那三枚在批 40 已经换成真跑，pixiv 那两枚在批 42 转交共享的 `PixivClient`）。
 * 不是 `null`、不是抛在装配那一刻：接线必须能建得出对象，屏才画得出来，
 * 而"这一路没接"要等到用户真走到那一步才被说出来。
 *
 * ⚠️ `reverse` 从前也在这份名单里（两枚方法直接抛），批 35 起它吃引擎与凭据**真接**了 ——
 * 名单跟着代码走，别照着这一句去推断那一颗。
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

    /**
     * 桌面侧那颗**具体的**偏好件 —— 设置页要写侧，而共享接口 `GalleryPreferences` 只有读面
     * （Android 那侧的 `setGalleryXxx` 也不在接口上，而在 `VeneraPreferences` 那颗类上）。
     *
     * 为什么要这颗口而不是在界面里 `ports.prefs as DesktopGalleryPreferences`：
     * 转换点放在集成方，界面仍然只吃形参（`DesktopUiIsolationTest` ③ 管的就是"UI 里不许取静态口"）。
     * 拿到的不是那一颗就直接抛，不静默退成"读得到但改不动"：那正好是假开关的形状。
     */
    fun preferences(handle: DesktopGalleryHandle): DesktopGalleryPreferences {
        val prefs = createPorts(handle).prefs
        return prefs as? DesktopGalleryPreferences
            ?: error("桌面接线给的偏好件不是 DesktopGalleryPreferences，实际是 ${prefs::class.qualifiedName}")
    }

    /**
     * 桌面那颗**具体的**内容守卫件 —— 分级档与屏蔽规则的写侧在这一颗上，共享接口只有读面
     * （`GalleryContentGuard` 只有 `nsfwMaskMode` / `rules` / `blockedGalleryRule` / `addRule`）。
     *
     * 与 [preferences] 同一条理由：转换点在集成方，界面只吃形参；拿到的不是那一颗就直接抛，
     * 不静默退成"读得到但改不动"（那正是假开关的形状）。这一颗为什么不能省成"界面读 `ports.contentGuard`
     * 再自己 as 一下"：`DesktopUiIsolationTest` 管的就是 UI 里不许找静态口。
     */
    fun contentGuard(handle: DesktopGalleryHandle): DesktopGalleryContentGuard {
        val guard = createPorts(handle).contentGuard
        return guard as? DesktopGalleryContentGuard
            ?: error("桌面接线给的守卫件不是 DesktopGalleryContentGuard，实际是 ${guard::class.qualifiedName}")
    }

    /**
     * 凭据那一颗的**具体件**（批 34：设置那一屏要往本机写 SauceNao 的 key，而契约面只有两枚读流）。
     *
     * 交出的必须是 `createPorts` 里那颗 —— 不是再 `new` 一颗：界面问的"配没配"、请求拼的 `api_key=`
     * 与用户刚填进去的那一枚必须是同一个事实（`createPorts` 里那句"凭据只建一颗"的同一条理由）。
     */
    fun credentials(handle: DesktopGalleryHandle): DesktopGalleryCredentials {
        val credentials = createPorts(handle).credentials
        return credentials as? DesktopGalleryCredentials
            ?: error("桌面接线给的凭据件不是 DesktopGalleryCredentials，实际是 ${credentials::class.qualifiedName}")
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
                reverse = DesktopGalleryReverseSearch(handle.engine, credentials),
                hygiene = DesktopGalleryNetworkHygiene(),
            ).also { ports = it }
        }
    }
}
