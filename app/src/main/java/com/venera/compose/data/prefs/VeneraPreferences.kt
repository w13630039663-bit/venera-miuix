package com.venera.compose.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GallerySaveNaming
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
import com.venera.compose.ui.tokens.ThemeSeedPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class AppearanceStyle {
    MIUIX, MD3
}

enum class NavigationBarStyle {
    MD3, LIQUID_GLASS
}

/**
 * 内容层的表面材质 —— 与 [AppearanceStyle]（色板/形状/字号）、[NavigationBarStyle]（只管底栏）
 * **正交的第三轴**（2026-09-29 拍板）。
 *
 * 为什么不塞进 `AppearanceStyle`：那一枚同时驱动色板派生、形状阶梯、字号阶梯三件事
 * （见 `feature/VeneraTheme.kt:63-98`），把材质并进去就等于强迫「开玻璃必然改色板与圆角」，
 * 而用户要的是材质能单独开关；分开还保得住「MD3 + 玻璃」与「Miuix + 玻璃」两种组合都成立。
 *
 * 默认 [SOLID] —— 这一条不是保守，是**零回归的判据本身**：默认关闭 ⇒ 升级后所有人看到的
 * 东西与今天逐像素相同，测试也钉这一点（`SurfaceMaterialPolicyTest.默认档是实色`）。
 */
enum class SurfaceMaterial {
    SOLID, LIQUID_GLASS
}

/**
 * 色板来源。仅在 [AppearanceStyle.MD3] 下有意义 —— Miuix 风格用的是它自己设计好的固定色板。
 *
 * WALLPAPER = 系统壁纸动态取色（`DynamicColors`，Android 12+）；
 * CUSTOM = 用户选定的种子色，经 MaterialKolor 展开成整套 MD3 色板。
 */
enum class ThemeColorSource {
    WALLPAPER, CUSTOM
}

/** 标签译文显示模式。SYSTEM = 由系统语言同时决定「译不译」与「译成简还是繁」。 */
enum class TagTranslationMode {
    SYSTEM, SIMPLIFIED, TRADITIONAL, OFF
}

class VeneraPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 阅读器设置
    private val _defaultReadingMode = MutableStateFlow(prefs.getString(KEY_DEFAULT_READING_MODE, "VERTICAL") ?: "VERTICAL")
    val defaultReadingMode: StateFlow<String> = _defaultReadingMode.asStateFlow()

    private val _pageGapDp = MutableStateFlow(prefs.getFloat(KEY_PAGE_GAP_DP, 8f))
    val pageGapDp: StateFlow<Float> = _pageGapDp.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _volumeKeyTurn = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_KEY_TURN, false))
    val volumeKeyTurn: StateFlow<Boolean> = _volumeKeyTurn.asStateFlow()

    private val _autoCropBorders = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CROP_BORDERS, false))
    val autoCropBorders: StateFlow<Boolean> = _autoCropBorders.asStateFlow()

    private val _nightFilter = MutableStateFlow(prefs.getBoolean(KEY_NIGHT_FILTER, false))
    val nightFilter: StateFlow<Boolean> = _nightFilter.asStateFlow()

    private val _clickToTurn = MutableStateFlow(prefs.getBoolean(KEY_CLICK_TO_TURN, true))
    val clickToTurn: StateFlow<Boolean> = _clickToTurn.asStateFlow()

    /** 翻页模式下自动巡航的每页停留秒数。条漫连续流用的是 px/s 速度，不走这个值。 */
    private val _autoScrollPageIntervalSec = MutableStateFlow(prefs.getFloat(KEY_AUTO_SCROLL_INTERVAL_SEC, 4f))
    val autoScrollPageIntervalSec: StateFlow<Float> = _autoScrollPageIntervalSec.asStateFlow()

    // 隐私
    /** 屏幕防窥：置位后禁止截图、录屏与任务列表缩略图（窗口级 FLAG_SECURE）。 */
    private val _secureScreen = MutableStateFlow(prefs.getBoolean(KEY_SECURE_SCREEN, false))
    val secureScreen: StateFlow<Boolean> = _secureScreen.asStateFlow()

    // 审计后从灰行转正的开关（见 settings-audit-2026-09.md §9）
    /** 翻页前额外预加载的页数。原为编译期常量 5。 */
    private val _preloadImageCount = MutableStateFlow(prefs.getInt(KEY_PRELOAD_COUNT, 5))
    val preloadImageCount: StateFlow<Int> = _preloadImageCount.asStateFlow()

    /** 反转点击翻页的左右方向（左利手友好）。 */
    private val _reverseTapDirection = MutableStateFlow(prefs.getBoolean(KEY_REVERSE_TAP, false))
    val reverseTapDirection: StateFlow<Boolean> = _reverseTapDirection.asStateFlow()

    /** 双击图片切换缩放。telephoto 的 ZoomableAsyncImage 已在用，这里只控制该手势。 */
    private val _doubleTapZoom = MutableStateFlow(prefs.getBoolean(KEY_DOUBLE_TAP_ZOOM, true))
    val doubleTapZoom: StateFlow<Boolean> = _doubleTapZoom.asStateFlow()

    /** 进入搜索页时预选的源；空串表示"不预设，保持会话内选择"。 */
    private val _defaultSearchTarget = MutableStateFlow(prefs.getString(KEY_DEFAULT_SEARCH_TARGET, "") ?: "")
    val defaultSearchTarget: StateFlow<String> = _defaultSearchTarget.asStateFlow()

    /** 启动后停留的主 Tab（VeneraNavTab 名）；默认首页。 */
    private val _startPage = MutableStateFlow(prefs.getString(KEY_START_PAGE, "HOME") ?: "HOME")
    val startPage: StateFlow<String> = _startPage.asStateFlow()

    /** 章节列表默认倒序（新章在前）。原为详情页会话内状态。 */
    private val _reverseChapterOrder = MutableStateFlow(prefs.getBoolean(KEY_REVERSE_CHAPTERS, false))
    val reverseChapterOrder: StateFlow<Boolean> = _reverseChapterOrder.asStateFlow()

    /** 下载并发数。DownloadManager 已是 Semaphore 队列，只差这个参数入口。 */
    private val _downloadThreads = MutableStateFlow(prefs.getInt(KEY_DOWNLOAD_THREADS, 2))
    val downloadThreads: StateFlow<Int> = _downloadThreads.asStateFlow()

    /** 网络响应缓存上限（MB）。OkHttp Cache.maxSize 运行时可改。 */
    private val _httpCacheMaxMb = MutableStateFlow(prefs.getInt(KEY_HTTP_CACHE_MAX_MB, 100))
    val httpCacheMaxMb: StateFlow<Int> = _httpCacheMaxMb.asStateFlow()

    /**
     * 本地漫画存储根目录的绝对路径；空串表示用应用私有内部存储的默认位置。
     *
     * 这里只存路径本身：可写性一律以 [com.venera.compose.download.ComicStorageRoot.probeWritable]
     * 的实测为准，「有权限」和「写得进去」在分区存储下不等价，权限也可能被用户随时撤销。
     */
    private val _comicStoragePath = MutableStateFlow(prefs.getString(KEY_COMIC_STORAGE_PATH, "") ?: "")
    val comicStoragePath: StateFlow<String> = _comicStoragePath.asStateFlow()

    fun setComicStoragePath(path: String) {
        prefs.edit { putString(KEY_COMIC_STORAGE_PATH, path) }
        _comicStoragePath.value = path
    }

    // ---- 画廊（设置页那一整块，2026-09-29 第五轮）----

    /** 墙上摆几列。AUTO = 按屏宽定（大屏 3、手机 2）。 */
    private val _galleryColumnMode = MutableStateFlow(readEnum(KEY_GALLERY_COLUMNS, GalleryColumnMode.AUTO))
    val galleryColumnMode: StateFlow<GalleryColumnMode> = _galleryColumnMode.asStateFlow()

    /** 墙上那一格取站方的哪一档地址。 */
    private val _galleryPreviewQuality = MutableStateFlow(readEnum(KEY_GALLERY_PREVIEW, GalleryPreviewQuality.PREVIEW))
    val galleryPreviewQuality: StateFlow<GalleryPreviewQuality> = _galleryPreviewQuality.asStateFlow()

    /**
     * 画廊图片磁盘缓存上限（MB）。
     *
     * 与 [httpCacheMaxMb] 是**两个不同的桶**：那一档管 OkHttp 的 HTTP 响应缓存（两路共用），
     * 这一档管 Coil 解过码之前那份原字节，画廊单独一个目录（隔离裁决，见 GalleryImageLoader）。
     */
    private val _galleryCacheMaxMb = MutableStateFlow(prefs.getInt(KEY_GALLERY_CACHE_MB, 512))
    val galleryCacheMaxMb: StateFlow<Int> = _galleryCacheMaxMb.asStateFlow()

    /** 画廊另设下载目录；空串 = 沿用「本地漫画存储根/图库」（不设第二份默认值，防两处漂）。 */
    private val _galleryDownloadPath = MutableStateFlow(prefs.getString(KEY_GALLERY_DOWNLOAD_PATH, "") ?: "")
    val galleryDownloadPath: StateFlow<String> = _galleryDownloadPath.asStateFlow()

    /** 保存到相册时的文件名规则。 */
    private val _gallerySaveNaming = MutableStateFlow(readEnum(KEY_GALLERY_SAVE_NAMING, GallerySaveNaming.SITE_ID))
    val gallerySaveNaming: StateFlow<GallerySaveNaming> = _gallerySaveNaming.asStateFlow()

    fun setGalleryColumnMode(mode: GalleryColumnMode) {
        prefs.edit { putString(KEY_GALLERY_COLUMNS, mode.name) }
        _galleryColumnMode.value = mode
    }

    fun setGalleryPreviewQuality(quality: GalleryPreviewQuality) {
        prefs.edit { putString(KEY_GALLERY_PREVIEW, quality.name) }
        _galleryPreviewQuality.value = quality
    }

    fun setGalleryCacheMaxMb(mb: Int) {
        val clamped = mb.coerceIn(64, 4096)
        prefs.edit { putInt(KEY_GALLERY_CACHE_MB, clamped) }
        _galleryCacheMaxMb.value = clamped
    }

    fun setGalleryDownloadPath(path: String) {
        prefs.edit { putString(KEY_GALLERY_DOWNLOAD_PATH, path) }
        _galleryDownloadPath.value = path
    }

    fun setGallerySaveNaming(naming: GallerySaveNaming) {
        prefs.edit { putString(KEY_GALLERY_SAVE_NAMING, naming.name) }
        _gallerySaveNaming.value = naming
    }

    // ---- 画廊大图页的行为档位（批次 C1，2026-09-29）----

    /**
     * 停在大图页时保持屏幕常亮。默认开，与阅读器那把同口径
     * （看图看到一半屏幕黑了，观感上等同于"这页图没加载出来"）。
     */
    private val _galleryKeepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_GALLERY_KEEP_SCREEN_ON, true))
    val galleryKeepScreenOn: StateFlow<Boolean> = _galleryKeepScreenOn.asStateFlow()

    /**
     * 音量键翻大图页的张。默认**关**：阅读器那把默认开，但画廊这页在透明玻璃窗 Activity 里、
     * 根节点此前没有任何 focus 件 —— 默认开等于把音量键从系统手里抢过来走一条没在真机上验过的链。
     */
    private val _galleryVolumeKeyTurn = MutableStateFlow(prefs.getBoolean(KEY_GALLERY_VOLUME_KEY, false))
    val galleryVolumeKeyTurn: StateFlow<Boolean> = _galleryVolumeKeyTurn.asStateFlow()

    /**
     * 大图页自动连播的间隔（秒）。**0 = 关闭连播**，所以这一枚不设单独的开关。
     * 判据在 `GalleryAutoPlay`（到底就停、视频页与弹层开着都不走）。
     */
    private val _galleryAutoPlaySec = MutableStateFlow(prefs.getInt(KEY_GALLERY_AUTOPLAY_SEC, 0))
    val galleryAutoPlaySec: StateFlow<Int> = _galleryAutoPlaySec.asStateFlow()

    /**
     * 大图页主动预取前后几页的 large 档。默认 NEXT = 今天既有的行为（邻居会被预组合、顺带下第一档）。
     * ⚠️ `OFF` 只关掉"主动预取 + 邻居预组合"，当前页自己的三档照旧发 —— 别把它当流量总闸。
     */
    private val _galleryPreload = MutableStateFlow(readEnum(KEY_GALLERY_PRELOAD, GalleryPreloadMode.NEXT))
    val galleryPreload: StateFlow<GalleryPreloadMode> = _galleryPreload.asStateFlow()

    fun setGalleryKeepScreenOn(on: Boolean) {
        prefs.edit { putBoolean(KEY_GALLERY_KEEP_SCREEN_ON, on) }
        _galleryKeepScreenOn.value = on
    }

    fun setGalleryVolumeKeyTurn(on: Boolean) {
        prefs.edit { putBoolean(KEY_GALLERY_VOLUME_KEY, on) }
        _galleryVolumeKeyTurn.value = on
    }

    /** 夹在 0..30：滑条那一头已经给了区间，这里再兜一道，防别处写进一个负数把连播变成"每 0 秒翻一张"。 */
    fun setGalleryAutoPlaySec(seconds: Int) {
        val clamped = seconds.coerceIn(0, 30)
        prefs.edit { putInt(KEY_GALLERY_AUTOPLAY_SEC, clamped) }
        _galleryAutoPlaySec.value = clamped
    }

    fun setGalleryPreload(mode: GalleryPreloadMode) {
        prefs.edit { putString(KEY_GALLERY_PRELOAD, mode.name) }
        _galleryPreload.value = mode
    }

    // ---- 画廊大图页的观感与 AI 判据（批次 C2，2026-09-29）----

    /**
     * 动图（GIF / 动图 WebP）要不要解成动画。**默认仅 Wi-Fi**。
     *
     * 只管大图页：墙上的卡片恒静帧，那一半不读这一档（拦在解码闸门里，见 GalleryAnimationGate）。
     */
    private val _galleryAnimated = MutableStateFlow(readEnum(KEY_GALLERY_ANIMATED, GalleryAnimatedMode.WIFI_ONLY))
    val galleryAnimated: StateFlow<GalleryAnimatedMode> = _galleryAnimated.asStateFlow()

    /** 大图页背景那一层。默认 = 用户已经看惯的那一版（窗口模糊 + 0.45 压暗）。 */
    private val _galleryBackdrop = MutableStateFlow(readEnum(KEY_GALLERY_BACKDROP, GalleryViewerBackdrop.GLASS))
    val galleryBackdrop: StateFlow<GalleryViewerBackdrop> = _galleryBackdrop.asStateFlow()

    /**
     * 画廊**自己的** AI 屏蔽开关。
     *
     * 与漫画守卫页那把 `block_ai`（在 venera_guard_prefs 里）是两枚独立开关 —— 用户 2026-09-29
     * 拍板"画廊和漫画分开"。分开的是开关，词表仍以 `security.guard.AiTagKeys` 那一张为准
     * （画廊侧只做**收窄**：剔掉裸 `ai`，理由见 `gallery/domain/GalleryAi.kt`）。
     */
    private val _galleryBlockAi = MutableStateFlow(prefs.getBoolean(KEY_GALLERY_BLOCK_AI, false))
    val galleryBlockAi: StateFlow<Boolean> = _galleryBlockAi.asStateFlow()

    /** 命中 AI 标签的条目在卡片上摆不摆「AI」角标。默认摆（屏蔽关着时总得有个读数）。 */
    private val _galleryAiBadge = MutableStateFlow(prefs.getBoolean(KEY_GALLERY_AI_BADGE, true))
    val galleryAiBadge: StateFlow<Boolean> = _galleryAiBadge.asStateFlow()

    fun setGalleryAnimated(mode: GalleryAnimatedMode) {
        prefs.edit { putString(KEY_GALLERY_ANIMATED, mode.name) }
        _galleryAnimated.value = mode
    }

    fun setGalleryBackdrop(backdrop: GalleryViewerBackdrop) {
        prefs.edit { putString(KEY_GALLERY_BACKDROP, backdrop.name) }
        _galleryBackdrop.value = backdrop
    }

    fun setGalleryBlockAi(on: Boolean) {
        prefs.edit { putBoolean(KEY_GALLERY_BLOCK_AI, on) }
        _galleryBlockAi.value = on
    }

    fun setGalleryAiBadge(on: Boolean) {
        prefs.edit { putBoolean(KEY_GALLERY_AI_BADGE, on) }
        _galleryAiBadge.value = on
    }

    // 外观与主题
    private val _themeMode = MutableStateFlow(readEnum(KEY_THEME_MODE, ThemeMode.SYSTEM))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _appearanceStyle = MutableStateFlow(readEnum(KEY_APPEARANCE_STYLE, AppearanceStyle.MIUIX))
    val appearanceStyle: StateFlow<AppearanceStyle> = _appearanceStyle.asStateFlow()

    private val _navigationBarStyle = MutableStateFlow(readEnum(KEY_NAVIGATION_BAR_STYLE, NavigationBarStyle.MD3))
    val navigationBarStyle: StateFlow<NavigationBarStyle> = _navigationBarStyle.asStateFlow()

    private val _surfaceMaterial = MutableStateFlow(readEnum(KEY_SURFACE_MATERIAL, SurfaceMaterial.SOLID))
    val surfaceMaterial: StateFlow<SurfaceMaterial> = _surfaceMaterial.asStateFlow()

    private val _tagTranslationMode = MutableStateFlow(readEnum(KEY_TAG_TRANSLATION_MODE, TagTranslationMode.SYSTEM))
    val tagTranslationMode: StateFlow<TagTranslationMode> = _tagTranslationMode.asStateFlow()

    private val _themeColorSource = MutableStateFlow(readEnum(KEY_THEME_COLOR_SOURCE, ThemeColorSource.WALLPAPER))
    val themeColorSource: StateFlow<ThemeColorSource> = _themeColorSource.asStateFlow()

    /** 自定义取色的种子色（ARGB）。只有 [themeColorSource] 为 CUSTOM 时才参与色板生成。 */
    private val _themeSeedColor = MutableStateFlow(prefs.getInt(KEY_THEME_SEED_COLOR, ThemeSeedPresets.DefaultArgb))
    val themeSeedColor: StateFlow<Int> = _themeSeedColor.asStateFlow()

    // Unknown values from backups/newer versions must not prevent the app from opening.
    private inline fun <reified T : Enum<T>> readEnum(key: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == prefs.getString(key, null) } ?: fallback

    // ---- 收藏（S5，对齐原版 appdata.settings 同名键） ----

    /** 新收藏插入位置：`"start"`（默认，加到开头）或 `"end"`。对应官方 `newFavoriteAddTo`。 */
    private val _newFavoriteAddTo = MutableStateFlow(prefs.getString(KEY_NEW_FAVORITE_ADD_TO, "start") ?: "start")
    val newFavoriteAddTo: StateFlow<String> = _newFavoriteAddTo.asStateFlow()

    /** 阅读后把漫画移动到收藏夹的哪一端：`"start"` / `"end"` / null（不动）。对应官方 `moveFavoriteAfterRead`。 */
    private val _moveFavoriteAfterRead = MutableStateFlow(prefs.getString(KEY_MOVE_FAVORITE_AFTER_READ, null))
    val moveFavoriteAfterRead: StateFlow<String?> = _moveFavoriteAfterRead.asStateFlow()

    /**
     * 收藏面板中「本地收藏」分区是否排在「网络收藏」之前。
     * 对齐官方 `appdata.settings['localFavoritesFirst'] ?? true`。
     */
    private val _localFavoritesFirst = MutableStateFlow(prefs.getBoolean(KEY_LOCAL_FAVORITES_FIRST, true))
    val localFavoritesFirst: StateFlow<Boolean> = _localFavoritesFirst.asStateFlow()

    /**
     * 长按详情页收藏按钮时的快捷收藏目标夹名。
     * 对齐官方 `appdata.settings['quickFavorite']`；未设置时为 null（长按退化为打开面板）。
     */
    private val _quickFavorite = MutableStateFlow(prefs.getString(KEY_QUICK_FAVORITE, null))
    val quickFavorite: StateFlow<String?> = _quickFavorite.asStateFlow()

    /** 追更所用的收藏夹（null = 未开启追更）。对应官方 `followUpdatesFolder`。 */
    private val _followUpdatesFolder = MutableStateFlow(prefs.getString(KEY_FOLLOW_UPDATES_FOLDER, null))
    val followUpdatesFolder: StateFlow<String?> = _followUpdatesFolder.asStateFlow()

    // 网络与安全
    private val _enableDoH = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_DOH, true))
    val enableDoH: StateFlow<Boolean> = _enableDoH.asStateFlow()

    private val _proxyType = MutableStateFlow(prefs.getString(KEY_PROXY_TYPE, "NONE") ?: "NONE")
    val proxyType: StateFlow<String> = _proxyType.asStateFlow()

    private val _proxyHost = MutableStateFlow(prefs.getString(KEY_PROXY_HOST, "") ?: "")
    val proxyHost: StateFlow<String> = _proxyHost.asStateFlow()

    private val _proxyPort = MutableStateFlow(prefs.getInt(KEY_PROXY_PORT, 7890))
    val proxyPort: StateFlow<Int> = _proxyPort.asStateFlow()

    // ---- 漫画列表布局（S8，对齐原版 appdata.settings['comicDisplayMode']） ----

    /**
     * 漫画列表展示模式：[MODE_BRIEF] = 双列封面网格；[MODE_DETAILED] = 单列大卡
     * （左封面 + 右侧标题/副标题/标签/评分/描述，对齐原版 ComicTile detailed 模式）。
     * 列表页 AppBar 的切换按钮读写此键，全部网格页监听即时重排。
     */
    private val _comicDisplayMode = MutableStateFlow(prefs.getString(KEY_COMIC_DISPLAY_MODE, MODE_BRIEF) ?: MODE_BRIEF)
    val comicDisplayMode: StateFlow<String> = _comicDisplayMode.asStateFlow()

    fun setComicDisplayMode(mode: String) {
        prefs.edit { putString(KEY_COMIC_DISPLAY_MODE, mode) }
        _comicDisplayMode.value = mode
    }

    // ---- 本地收藏夹排序（名称 / 时间 / 自定义） ----

    /**
     * 本地收藏列表排序规则（存 [FavoriteSortOrder] 的枚举名）。
     * 默认「最新收藏」，与官方收藏页首次进入的观感一致。
     */
    private val _favoriteSortOrder = MutableStateFlow(
        prefs.getString(KEY_FAVORITE_SORT_ORDER, "TIME_DESC") ?: "TIME_DESC"
    )
    val favoriteSortOrder: StateFlow<String> = _favoriteSortOrder.asStateFlow()

    fun setFavoriteSortOrder(order: String) {
        prefs.edit { putString(KEY_FAVORITE_SORT_ORDER, order) }
        _favoriteSortOrder.value = order
    }

    // ---- 关于 / 检查更新（对齐 master 分支 appdata 的同名键与默认值） ----

    /** 启动时是否检查更新。默认关，与 master 分支 `appdata.dart` 的 `checkUpdateOnStart: false` 一致。 */
    private val _checkUpdateOnStart = MutableStateFlow(prefs.getBoolean(KEY_CHECK_UPDATE_ON_START, false))
    val checkUpdateOnStart: StateFlow<Boolean> = _checkUpdateOnStart.asStateFlow()

    fun setCheckUpdateOnStart(enable: Boolean) {
        prefs.edit { putBoolean(KEY_CHECK_UPDATE_ON_START, enable) }
        _checkUpdateOnStart.value = enable
    }

    /**
     * 上次「启动检查」的时刻。master 存在 `implicitData` 而不是 `settings`，
     * 这里同样只当账本、不当偏好：24 小时节流，并且**发请求前先占坑** ——
     * 一次断网不该让之后每次冷启动都再打一遍发布通道。
     */
    var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK_AT, 0L)
        set(value) {
            prefs.edit { putLong(KEY_LAST_UPDATE_CHECK_AT, value) }
        }

    // 写入方法
    fun setDefaultReadingMode(mode: String) {
        prefs.edit { putString(KEY_DEFAULT_READING_MODE, mode) }
        _defaultReadingMode.value = mode
    }

    fun setPageGapDp(gap: Float) {
        prefs.edit { putFloat(KEY_PAGE_GAP_DP, gap) }
        _pageGapDp.value = gap
    }

    fun setKeepScreenOn(enable: Boolean) {
        prefs.edit { putBoolean(KEY_KEEP_SCREEN_ON, enable) }
        _keepScreenOn.value = enable
    }

    fun setVolumeKeyTurn(enable: Boolean) {
        prefs.edit { putBoolean(KEY_VOLUME_KEY_TURN, enable) }
        _volumeKeyTurn.value = enable
    }

    fun setAutoCropBorders(enable: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_CROP_BORDERS, enable) }
        _autoCropBorders.value = enable
    }

    fun setNightFilter(enable: Boolean) {
        prefs.edit { putBoolean(KEY_NIGHT_FILTER, enable) }
        _nightFilter.value = enable
    }

    fun setClickToTurn(enable: Boolean) {
        prefs.edit { putBoolean(KEY_CLICK_TO_TURN, enable) }
        _clickToTurn.value = enable
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
        _themeMode.value = mode
    }

    fun setAppearanceStyle(style: AppearanceStyle) {
        prefs.edit { putString(KEY_APPEARANCE_STYLE, style.name) }
        _appearanceStyle.value = style
    }

    fun setThemeColorSource(source: ThemeColorSource) {
        prefs.edit { putString(KEY_THEME_COLOR_SOURCE, source.name) }
        _themeColorSource.value = source
    }

    fun setThemeSeedColor(argb: Int) {
        prefs.edit { putInt(KEY_THEME_SEED_COLOR, argb) }
        _themeSeedColor.value = argb
    }

    fun setNavigationBarStyle(style: NavigationBarStyle) {
        prefs.edit { putString(KEY_NAVIGATION_BAR_STYLE, style.name) }
        _navigationBarStyle.value = style
    }

    fun setSurfaceMaterial(material: SurfaceMaterial) {
        prefs.edit { putString(KEY_SURFACE_MATERIAL, material.name) }
        _surfaceMaterial.value = material
    }

    fun setTagTranslationMode(mode: TagTranslationMode) {
        prefs.edit { putString(KEY_TAG_TRANSLATION_MODE, mode.name) }
        _tagTranslationMode.value = mode
    }

    fun setAutoScrollPageIntervalSec(sec: Float) {
        val clamped = sec.coerceIn(1f, 15f)
        prefs.edit { putFloat(KEY_AUTO_SCROLL_INTERVAL_SEC, clamped) }
        _autoScrollPageIntervalSec.value = clamped
    }

    fun setSecureScreen(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SECURE_SCREEN, enabled) }
        _secureScreen.value = enabled
    }

    fun setPreloadImageCount(count: Int) {
        val v = count.coerceIn(0, 20)
        prefs.edit { putInt(KEY_PRELOAD_COUNT, v) }
        _preloadImageCount.value = v
    }

    fun setReverseTapDirection(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_REVERSE_TAP, enabled) }
        _reverseTapDirection.value = enabled
    }

    fun setDoubleTapZoom(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DOUBLE_TAP_ZOOM, enabled) }
        _doubleTapZoom.value = enabled
    }

    fun setDefaultSearchTarget(key: String) {
        prefs.edit { putString(KEY_DEFAULT_SEARCH_TARGET, key) }
        _defaultSearchTarget.value = key
    }

    fun setStartPage(tab: String) {
        prefs.edit { putString(KEY_START_PAGE, tab) }
        _startPage.value = tab
    }

    fun setReverseChapterOrder(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_REVERSE_CHAPTERS, enabled) }
        _reverseChapterOrder.value = enabled
    }

    fun setDownloadThreads(threads: Int) {
        val v = threads.coerceIn(1, 16)
        prefs.edit { putInt(KEY_DOWNLOAD_THREADS, v) }
        _downloadThreads.value = v
    }

    fun setHttpCacheMaxMb(mb: Int) {
        val v = mb.coerceIn(16, 1024)
        prefs.edit { putInt(KEY_HTTP_CACHE_MAX_MB, v) }
        _httpCacheMaxMb.value = v
    }

    fun setNewFavoriteAddTo(value: String) {
        prefs.edit { putString(KEY_NEW_FAVORITE_ADD_TO, value) }
        _newFavoriteAddTo.value = value
    }

    fun setMoveFavoriteAfterRead(value: String?) {
        prefs.edit { putString(KEY_MOVE_FAVORITE_AFTER_READ, value) }
        _moveFavoriteAfterRead.value = value
    }

    fun setLocalFavoritesFirst(value: Boolean) {
        prefs.edit { putBoolean(KEY_LOCAL_FAVORITES_FIRST, value) }
        _localFavoritesFirst.value = value
    }

    fun setQuickFavorite(folder: String?) {
        prefs.edit { putString(KEY_QUICK_FAVORITE, folder) }
        _quickFavorite.value = folder
    }

    fun setFollowUpdatesFolder(folder: String?) {
        prefs.edit { putString(KEY_FOLLOW_UPDATES_FOLDER, folder) }
        _followUpdatesFolder.value = folder
    }

    fun setEnableDoH(enable: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLE_DOH, enable) }
        _enableDoH.value = enable
    }

    fun setProxy(type: String, host: String, port: Int) {
        prefs.edit {
            putString(KEY_PROXY_TYPE, type)
            putString(KEY_PROXY_HOST, host)
            putInt(KEY_PROXY_PORT, port)
        }
        _proxyType.value = type
        _proxyHost.value = host
        _proxyPort.value = port
    }

    companion object {
        private const val PREFS_NAME = "venera_preferences"

        private const val KEY_DEFAULT_READING_MODE = "pref_reading_mode"
        private const val KEY_PAGE_GAP_DP = "pref_page_gap_dp"
        private const val KEY_KEEP_SCREEN_ON = "pref_keep_screen_on"
        private const val KEY_VOLUME_KEY_TURN = "pref_volume_key_turn"
        private const val KEY_AUTO_CROP_BORDERS = "pref_auto_crop_borders"
        private const val KEY_NIGHT_FILTER = "pref_night_filter"
        private const val KEY_CLICK_TO_TURN = "pref_click_to_turn"
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_APPEARANCE_STYLE = "pref_appearance_style"
        private const val KEY_NAVIGATION_BAR_STYLE = "pref_navigation_bar_style"
        private const val KEY_SURFACE_MATERIAL = "pref_surface_material"
        private const val KEY_TAG_TRANSLATION_MODE = "pref_tag_translation_mode"
        private const val KEY_THEME_COLOR_SOURCE = "pref_theme_color_source"
        private const val KEY_THEME_SEED_COLOR = "pref_theme_seed_color"
        private const val KEY_AUTO_SCROLL_INTERVAL_SEC = "pref_auto_scroll_interval_sec"
        private const val KEY_SECURE_SCREEN = "pref_secure_screen"
        private const val KEY_PRELOAD_COUNT = "pref_preload_image_count"
        private const val KEY_REVERSE_TAP = "pref_reverse_tap_direction"
        private const val KEY_DOUBLE_TAP_ZOOM = "pref_double_tap_zoom"
        private const val KEY_DEFAULT_SEARCH_TARGET = "pref_default_search_target"
        private const val KEY_START_PAGE = "pref_start_page"
        private const val KEY_REVERSE_CHAPTERS = "pref_reverse_chapter_order"
        private const val KEY_DOWNLOAD_THREADS = "pref_download_threads"
        private const val KEY_HTTP_CACHE_MAX_MB = "pref_http_cache_max_mb"
        private const val KEY_COMIC_STORAGE_PATH = "pref_comic_storage_path"
        private const val KEY_GALLERY_COLUMNS = "pref_gallery_columns"
        private const val KEY_GALLERY_PREVIEW = "pref_gallery_preview"
        private const val KEY_GALLERY_CACHE_MB = "pref_gallery_cache_max_mb"
        private const val KEY_GALLERY_DOWNLOAD_PATH = "pref_gallery_download_path"
        private const val KEY_GALLERY_SAVE_NAMING = "pref_gallery_save_naming"
        private const val KEY_GALLERY_KEEP_SCREEN_ON = "pref_gallery_keep_screen_on"
        private const val KEY_GALLERY_VOLUME_KEY = "pref_gallery_volume_key"
        private const val KEY_GALLERY_AUTOPLAY_SEC = "pref_gallery_autoplay_sec"
        private const val KEY_GALLERY_PRELOAD = "pref_gallery_preload"
        private const val KEY_GALLERY_ANIMATED = "pref_gallery_animated"
        private const val KEY_GALLERY_BACKDROP = "pref_gallery_backdrop"
        private const val KEY_GALLERY_BLOCK_AI = "pref_gallery_block_ai"
        private const val KEY_GALLERY_AI_BADGE = "pref_gallery_ai_badge"
        private const val KEY_NEW_FAVORITE_ADD_TO = "pref_new_favorite_add_to"
        private const val KEY_MOVE_FAVORITE_AFTER_READ = "pref_move_favorite_after_read"
        private const val KEY_LOCAL_FAVORITES_FIRST = "pref_local_favorites_first"
        private const val KEY_QUICK_FAVORITE = "pref_quick_favorite"
        private const val KEY_FOLLOW_UPDATES_FOLDER = "pref_follow_updates_folder"
        private const val KEY_ENABLE_DOH = "pref_enable_doh"
        private const val KEY_PROXY_TYPE = "pref_proxy_type"
        private const val KEY_PROXY_HOST = "pref_proxy_host"
        private const val KEY_PROXY_PORT = "pref_proxy_port"
        private const val KEY_COMIC_DISPLAY_MODE = "pref_comic_display_mode"
        private const val KEY_FAVORITE_SORT_ORDER = "pref_favorite_sort_order"
        private const val KEY_CHECK_UPDATE_ON_START = "pref_check_update_on_start"
        private const val KEY_LAST_UPDATE_CHECK_AT = "pref_last_update_check_at"

        /** 双列封面网格（默认）。 */
        const val MODE_BRIEF = "brief"
        /** 单列大卡（左封面 + 右侧完整信息含标签）。 */
        const val MODE_DETAILED = "detailed"

        @Volatile
        private var INSTANCE: VeneraPreferences? = null

        fun getInstance(context: Context): VeneraPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VeneraPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}