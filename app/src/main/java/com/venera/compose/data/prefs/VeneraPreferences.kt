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

    // 外观与主题
    private val _themeMode = MutableStateFlow(ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

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