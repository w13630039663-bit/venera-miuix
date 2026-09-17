package com.venera.compose.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.venera.compose.components.normalizeComicDisplayMode

/** Separate file: list layout is independent of reader settings and source preferences. */
class ComicListPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("comic_list_presentation", Context.MODE_PRIVATE)

    init {
        if (!preferences.contains(KEY_MODE)) {
            val legacy = context.applicationContext.getSharedPreferences("venera_preferences", Context.MODE_PRIVATE)
            preferences.edit().putString(KEY_MODE, normalizeComicDisplayMode(legacy.getString("pref_comic_display_mode", null))).apply()
        }
    }

    var displayMode: String
        get() = normalizeComicDisplayMode(preferences.getString(KEY_MODE, null))
        set(value) { preferences.edit().putString(KEY_MODE, normalizeComicDisplayMode(value)).apply() }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = preferences.registerOnSharedPreferenceChangeListener(listener)
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = preferences.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object { const val KEY_MODE = "display_mode" }
}
