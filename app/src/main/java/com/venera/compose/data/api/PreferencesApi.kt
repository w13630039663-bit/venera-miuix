package com.venera.compose.data.api

import com.venera.compose.data.prefs.AppearanceStyle
import com.venera.compose.data.prefs.NavigationBarStyle
import com.venera.compose.data.prefs.SurfaceMaterial
import com.venera.compose.data.prefs.TagTranslationMode
import com.venera.compose.data.prefs.ThemeColorSource
import com.venera.compose.data.prefs.ThemeMode
import com.venera.compose.data.platform.KeyValueStore
import kotlinx.coroutines.flow.StateFlow

/**
 * 偏好侧的业务 API：把 `VeneraPreferences`（公开面 101 枚）切成**照已有的页切**的四份。
 *
 * 为什么必须切：审计口径下 UI 一共摸到 99 枚成员（逐颗调用点数出来的实数，见
 * `docs/rounds/business-api-boundary-2026-10.md` §五 B2），
 * 如果只立一颗 `Preferences` 契约，它的宽度就等于实现宽度 —— 收口只换个名字，什么都没固定住。
 * 分域的判据不是发明分层，而是**消费面本来就分开了**：
 *
 * - [ReaderPreferences] —— 只有 `reader/VeneraReaderScreen.kt` 与 `feature/settings/ReaderSettings.kt`
 *   两处（外加详情页那一枚章节顺序），与其余偏好**零交集**。
 * - [AppearancePreferences] —— `feature/VeneraTheme.kt`、`feature/settings/AppearanceSettings.kt`
 *   与设置页那三处头图/名言卡读写。
 * - [ComicPreferences] —— 收藏行为、默认搜索目标、防窥、检查更新。
 * - [NetworkPreferences] —— 代理、下载线程、HTTP 缓存、存储根、Cloudflare 优选 IP
 *   （`feature/settings/NetworkSettings.kt`、`PreferredIpSettings.kt`、`AppSettings.kt` 三颗页）。
 *
 * 画廊那一份不在这里：它归画廊自持（`gallery/data/GalleryPorts.kt` 的 `GalleryPreferences`，
 * getter-only 12 枚），口径与 B1 把守卫契约分成 `ContentGuard` / `GalleryContentGuard` 一致。
 *
 * 每颗契约的成员**逐条来自调用点**，没有一枚是为了"看起来完整"而加的：
 * `setAutoScrollPageIntervalSec` 有调用点所以收，`setMoveFavoriteAfterRead` 没有（它只被
 * `data/db/LocalFavoritesManager.kt:529` 读）所以不收。谁将来需要，就在那一批把它加进对应契约 ——
 * 加字段必须动这颗文件，而它就在守卫用例的视野里。
 *
 * 类型不搬：`ThemeMode` 等六枚枚举声明在 `data/prefs/VeneraPreferences.kt:18-58`，那颗文件
 * `import android.content.Context`。本轮把契约指向它们（同包引用，不新增 import 面），
 * 类型搬家是 Part B 的 W1/W2 那一族动作，与本轮「换从哪拿、不换怎么算」的边界不是一件事。
 */

/** 阅读器行为与外观。消费面只有阅读器本体与设置页。 */
interface ReaderPreferences {

    val defaultReadingMode: StateFlow<String>
    val clickToTurn: StateFlow<Boolean>

    /** 左右半屏的翻页方向（反转后左半屏=上一页）。 */
    val reverseTapDirection: StateFlow<Boolean>
    val volumeKeyTurn: StateFlow<Boolean>
    val keepScreenOn: StateFlow<Boolean>
    val nightFilter: StateFlow<Boolean>
    val pageGapDp: StateFlow<Float>

    /** 自动滚动的每页间隔秒数。 */
    val autoScrollPageIntervalSec: StateFlow<Float>
    val preloadImageCount: StateFlow<Int>

    /** 章节目录倒序（详情页与探索设置页都读它，阅读语义而非列表语义）。 */
    val reverseChapterOrder: StateFlow<Boolean>

    fun setDefaultReadingMode(mode: String)
    fun setClickToTurn(enable: Boolean)
    fun setReverseTapDirection(enabled: Boolean)
    fun setVolumeKeyTurn(enable: Boolean)
    fun setKeepScreenOn(enable: Boolean)
    fun setNightFilter(enable: Boolean)
    fun setPageGapDp(gap: Float)
    fun setAutoScrollPageIntervalSec(sec: Float)
    fun setPreloadImageCount(count: Int)
    fun setReverseChapterOrder(enabled: Boolean)
}

/** 主题、色板、底栏形态与设置页的两张自绘图。 */
interface AppearancePreferences {

    val themeMode: StateFlow<ThemeMode>
    val appearanceStyle: StateFlow<AppearanceStyle>
    val navigationBarStyle: StateFlow<NavigationBarStyle>
    val surfaceMaterial: StateFlow<SurfaceMaterial>
    val tagTranslationMode: StateFlow<TagTranslationMode>
    val themeColorSource: StateFlow<ThemeColorSource>
    val themeSeedColor: StateFlow<Int>

    /** 设置页头图与名言卡小图：存的是应用私有目录里的绝对路径，空串=未设置。 */
    val settingsHeroPath: StateFlow<String>
    val settingsQuoteAvatarPath: StateFlow<String>

    /** 冷启动落在哪一档 Tab。 */
    val startPage: StateFlow<String>

    fun setThemeMode(mode: ThemeMode)
    fun setAppearanceStyle(style: AppearanceStyle)
    fun setNavigationBarStyle(style: NavigationBarStyle)
    fun setSurfaceMaterial(material: SurfaceMaterial)
    fun setTagTranslationMode(mode: TagTranslationMode)
    fun setThemeColorSource(source: ThemeColorSource)
    fun setThemeSeedColor(argb: Int)
    fun setSettingsHeroPath(path: String)
    fun setSettingsQuoteAvatarPath(path: String)
    fun setStartPage(tab: String)
}

/** 收藏与阅读行为、默认搜索目标、防窥、检查更新。 */
interface ComicPreferences {

    val defaultSearchTarget: StateFlow<String>
    val checkUpdateOnStart: StateFlow<Boolean>

    /**
     * 上次「启动检查」的时刻。**是 `var` 不是流** —— 它当账本不当偏好（24 小时节流，
     * 且发请求前先占坑，一次断网不该让之后每次冷启动都再打一遍发布通道）。
     */
    var lastUpdateCheckAt: Long

    val favoriteSortOrder: StateFlow<String>
    val newFavoriteAddTo: StateFlow<String>
    val quickFavorite: StateFlow<String?>
    val followUpdatesFolder: StateFlow<String?>
    val secureScreen: StateFlow<Boolean>

    fun setDefaultSearchTarget(key: String)
    fun setCheckUpdateOnStart(enable: Boolean)
    fun setFavoriteSortOrder(order: String)
    fun setNewFavoriteAddTo(value: String)
    fun setQuickFavorite(folder: String?)
    fun setFollowUpdatesFolder(folder: String?)
    fun setSecureScreen(enabled: Boolean)
}

/** 代理、下载并发、HTTP 缓存、存储根与 Cloudflare 优选 IP。 */
interface NetworkPreferences {

    val proxyType: StateFlow<String>
    val proxyHost: StateFlow<String>
    val proxyPort: StateFlow<Int>
    val downloadThreads: StateFlow<Int>
    val httpCacheMaxMb: StateFlow<Int>
    val comicStoragePath: StateFlow<String>
    val cfPreferredIpEnabled: StateFlow<Boolean>
    val cfPreferredIps: StateFlow<String>
    val cfPreferredHosts: StateFlow<String>

    /** 三条代理字段一次写完（逐条写会把一次事务拆成多次落盘）。 */
    fun setProxy(type: String, host: String, port: Int)
    fun setDownloadThreads(threads: Int)
    fun setHttpCacheMaxMb(mb: Int)
    fun setComicStoragePath(path: String)
    fun setCfPreferredIp(enabled: Boolean, ips: String, hosts: String)
}

/**
 * 按名字取一颗键值存储 —— 给「不属于任何偏好域的自有缓存」用（首页推荐快照、搜索历史）。
 *
 * 契约返回的是端口 [KeyValueStore]（`data/platform/KeyValueStore.kt`，两端各一个实现），
 * 而不是 Android 那一颗实现类。**存储名一律由调用点原样递进来**：一颗缓存归谁、叫什么，
 * 是它的调用点的语义，收口不许把它改名（改了就是换数据文件，冷启动读不到上次的内容）。
 */
interface NamedStoreFactory {

    fun open(name: String): KeyValueStore
}
