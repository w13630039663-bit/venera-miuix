package com.venera.compose.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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

    // 外观与主题
    private val _themeMode = MutableStateFlow(readEnum(KEY_THEME_MODE, ThemeMode.SYSTEM))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _appearanceStyle = MutableStateFlow(readEnum(KEY_APPEARANCE_STYLE, AppearanceStyle.MIUIX))
    val appearanceStyle: StateFlow<AppearanceStyle> = _appearanceStyle.asStateFlow()

    private val _navigationBarStyle = MutableStateFlow(readEnum(KEY_NAVIGATION_BAR_STYLE, NavigationBarStyle.MD3))
    val navigationBarStyle: StateFlow<NavigationBarStyle> = _navigationBarStyle.asStateFlow()

    private val _tagTranslationMode = MutableStateFlow(readEnum(KEY_TAG_TRANSLATION_MODE, TagTranslationMode.SYSTEM))
    val tagTranslationMode: StateFlow<TagTranslationMode> = _tagTranslationMode.asStateFlow()

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

    fun setNavigationBarStyle(style: NavigationBarStyle) {
        prefs.edit { putString(KEY_NAVIGATION_BAR_STYLE, style.name) }
        _navigationBarStyle.value = style
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
        private const val KEY_TAG_TRANSLATION_MODE = "pref_tag_translation_mode"
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