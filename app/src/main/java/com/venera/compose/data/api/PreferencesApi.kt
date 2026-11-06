package com.venera.compose.data.api

import com.venera.compose.data.platform.AppearanceStyle
import com.venera.compose.data.platform.NavigationBarStyle
import com.venera.compose.data.platform.SurfaceMaterial
import com.venera.compose.data.platform.TagTranslationMode
import com.venera.compose.data.platform.ThemeColorSource
import com.venera.compose.data.platform.ThemeMode
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

    /** 条漫连续流自动巡航速度（px/s）。与间隔那枚同待遇：持久化，全局一份。 */
    val autoScrollSpeedPx: StateFlow<Float>
    fun setAutoScrollSpeedPx(speed: Float)

    /** 「记住速度」开关（默认开）：关时速度只活在本次阅读会话，不落偏好。 */
    val autoScrollSpeedPersist: StateFlow<Boolean>
    fun setAutoScrollSpeedPersist(enabled: Boolean)

    /**
     * 按本记忆阅读模式（默认开）：开时在阅读器里换模式只影响这一本，重进同本自动恢复；
     * 关时阅读器换的就是全局默认档（原行为）。
     */
    val comicModeMemoryEnabled: StateFlow<Boolean>
    fun setComicModeMemory(enabled: Boolean)

    /** 这一本记住的阅读模式 key（`ReaderReadingMode.key`）；没记过返回 null，由调用方落全局档。 */
    fun comicReadingMode(comicKey: String): String?

    /** 记（或传 null 清）这一本的阅读模式。comicKey = `<sourceKey>|<comicId>`。 */
    fun setComicReadingMode(comicKey: String, modeKey: String?)

    /**
     * 阅读器常驻信息角标（默认开）：左角页码、右角时间与电量，双角胶囊。
     * 入口只在阅读器设置面板。
     */
    val readerHudEnabled: StateFlow<Boolean>
    fun setReaderHudEnabled(enabled: Boolean)

    /** 阅读时保留系统状态栏（默认关 = 全沉浸，只藏导航栏）。入口只在阅读器设置面板。 */
    val readerShowStatusBar: StateFlow<Boolean>
    fun setReaderShowStatusBar(enabled: Boolean)
    val preloadImageCount: StateFlow<Int>

    /** 巡航到本话末页时接着开下一话（会**自动联网取下一话**，默认开）。 */
    val autoNextChapter: StateFlow<Boolean>

    // ── 图片增强（2026-10-13）。五枚一组，默认全中性 = 不改观感。 ──
    // 收进契约的理由与别处一致：消费点有阅读器本体与设置页两处，
    // 而"哪一枚算中性"这件事必须只有一个说法（判据在 `ReaderEnhancement.readerColorMatrix`）。

    /** 总开关。关着时下面四枚一律不参与渲染。 */
    val readerEnhanceEnabled: StateFlow<Boolean>

    /** 锐化 0..1（0 = 不锐化）。 */
    val readerEnhanceSharpen: StateFlow<Float>

    /** 对比度 0.5..1.5（1 = 原图）。 */
    val readerEnhanceContrast: StateFlow<Float>

    /** 饱和度 0..2（1 = 原图）。 */
    val readerEnhanceSaturation: StateFlow<Float>

    /** 亮度 −0.25..0.25（0 = 原图）。 */
    val readerEnhanceBrightness: StateFlow<Float>

    /** 章节目录倒序（详情页与探索设置页都读它，阅读语义而非列表语义）。 */
    val reverseChapterOrder: StateFlow<Boolean>

    /** 翻页特效档位（ReaderPageTurnEffect 的键）。默认 "NONE" = 原样。 */
    val pageTurnEffect: StateFlow<String>

    fun setDefaultReadingMode(mode: String)
    fun setClickToTurn(enable: Boolean)
    fun setReverseTapDirection(enabled: Boolean)
    fun setVolumeKeyTurn(enable: Boolean)
    fun setKeepScreenOn(enable: Boolean)
    fun setNightFilter(enable: Boolean)
    fun setPageGapDp(gap: Float)
    fun setAutoScrollPageIntervalSec(sec: Float)
    fun setPreloadImageCount(count: Int)
    fun setAutoNextChapter(enabled: Boolean)
    fun setReaderEnhanceEnabled(enabled: Boolean)
    fun setReaderEnhanceSharpen(value: Float)
    fun setReaderEnhanceContrast(value: Float)
    fun setReaderEnhanceSaturation(value: Float)
    fun setReaderEnhanceBrightness(value: Float)
    fun setReverseChapterOrder(enabled: Boolean)
    fun setPageTurnEffect(effect: String)
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

    /** 首页顶栏那只桌宠摆不摆（默认关）。 */
    val homePetSticker: StateFlow<Boolean>

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
    fun setHomePetSticker(on: Boolean)
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

    /**
     * 用户点过「以后再说」的那一版。**是 `var` 不是流**，与 [lastUpdateCheckAt] 同形（账本不当偏好）。
     * 写 null 撤销。判据在 `AppUpdateChecker.shouldAnnounce`：只有"远程就是被跳过的那一版"才压住。
     */
    var ignoredUpdateVersion: String?

    val favoriteSortOrder: StateFlow<String>
    val newFavoriteAddTo: StateFlow<String>
    val quickFavorite: StateFlow<String?>
    val followUpdatesFolder: StateFlow<String?>
    val secureScreen: StateFlow<Boolean>

    /** 首次启动引导是否完成（引导完成页写一次；完成后冷启动不再出现引导）。 */
    val onboardingDone: StateFlow<Boolean>

    fun setDefaultSearchTarget(key: String)
    fun setCheckUpdateOnStart(enable: Boolean)
    fun setFavoriteSortOrder(order: String)
    fun setNewFavoriteAddTo(value: String)
    fun setQuickFavorite(folder: String?)
    fun setFollowUpdatesFolder(folder: String?)
    fun setSecureScreen(enabled: Boolean)
    fun setOnboardingDone()
}

/** 代理、下载并发、HTTP 缓存、存储根与 Cloudflare 优选 IP。 */
interface NetworkPreferences {

    val proxyType: StateFlow<String>
    val proxyHost: StateFlow<String>
    val proxyPort: StateFlow<Int>
    val downloadThreads: StateFlow<Int>
    val httpCacheMaxMb: StateFlow<Int>
    val comicStoragePath: StateFlow<String>

    /**
     * 当前生效的下载根目录绝对路径（配了用配的，没配落应用私有目录）。
     *
     * 开口加在契约上而不是让 UI 直连 `ComicStorageRoot`（`BusinessApiBoundaryTest` 的 E 条：
     * 要开口就加契约）。消费点：设置「应用与数据」与首次启动引导的「下载目录」那一行。
     */
    val downloadRootPath: String
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
