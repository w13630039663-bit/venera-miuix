package com.venera.compose.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.venera.compose.components.normalizeComicDisplayMode
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.data.platform.android.AndroidKeyValueStore
import com.venera.compose.data.platform.contains

/** Separate file: list layout is independent of reader settings and source preferences. */
class ComicListPreferences(context: Context) {
    private val preferences = AndroidKeyValueStore(context.applicationContext, PreferenceKeys.PREFS_COMIC_LIST_PRESENTATION)

    init {
        if (!preferences.contains(KEY_MODE)) {
            // 首次进入从 VeneraPreferences 那张表搬一次（表名与它内部的 PREFS_NAME 对齐）
            val legacy = AndroidKeyValueStore(context.applicationContext, PreferenceKeys.PREFS_NAME)
            preferences.put(KEY_MODE, normalizeComicDisplayMode(legacy.getString("pref_comic_display_mode", null)))
        }
    }

    var displayMode: String
        get() = normalizeComicDisplayMode(preferences.getString(KEY_MODE, null))
        set(value) { preferences.put(KEY_MODE, normalizeComicDisplayMode(value)) }

    // 变更监听仍是 Android 原生回调：接口里没有这一格，调用方（列表卡片）的签名不动
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = preferences.registerListener(listener)
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = preferences.unregisterListener(listener)

    private companion object { const val KEY_MODE = "display_mode" }
}
