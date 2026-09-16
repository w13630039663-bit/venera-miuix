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

    // 网络与安全
    private val _enableDoH = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_DOH, true))
    val enableDoH: StateFlow<Boolean> = _enableDoH.asStateFlow()

    private val _proxyType = MutableStateFlow(prefs.getString(KEY_PROXY_TYPE, "NONE") ?: "NONE")
    val proxyType: StateFlow<String> = _proxyType.asStateFlow()

    private val _proxyHost = MutableStateFlow(prefs.getString(KEY_PROXY_HOST, "") ?: "")
    val proxyHost: StateFlow<String> = _proxyHost.asStateFlow()

    private val _proxyPort = MutableStateFlow(prefs.getInt(KEY_PROXY_PORT, 7890))
    val proxyPort: StateFlow<Int> = _proxyPort.asStateFlow()

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
        private const val KEY_ENABLE_DOH = "pref_enable_doh"
        private const val KEY_PROXY_TYPE = "pref_proxy_type"
        private const val KEY_PROXY_HOST = "pref_proxy_host"
        private const val KEY_PROXY_PORT = "pref_proxy_port"

        @Volatile
        private var INSTANCE: VeneraPreferences? = null

        fun getInstance(context: Context): VeneraPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VeneraPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}