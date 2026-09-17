package com.venera.compose.data.prefs

import android.content.Context
import android.content.SharedPreferences

/** Optional snapshot of metrics actually returned by a source. No database schema change. */
data class CachedComicMetrics(val rating: Double? = null, val likesCount: Int? = null)

/** Length-prefix keys avoid collisions between sources and ids containing separators. */
fun comicMetricCacheKey(sourceKey: String, comicId: String): String = "${sourceKey.length}:$sourceKey$comicId"

class ComicMetricsCache(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("comic_source_metrics", Context.MODE_PRIVATE)

    fun get(sourceKey: String, comicId: String): CachedComicMetrics {
        val key = comicMetricCacheKey(sourceKey, comicId)
        return CachedComicMetrics(
            preferences.getString("$key:rating", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 },
            preferences.getString("$key:likes", null)?.toIntOrNull()?.takeIf { it >= 0 },
        )
    }

    /** Absent fields do not erase a previously observed metric from another response. */
    fun put(sourceKey: String, comicId: String, rating: Double?, likesCount: Int?) {
        if (sourceKey.isBlank() || comicId.isBlank()) return
        val validRating = rating?.takeIf { it.isFinite() && it >= 0 }
        val validLikes = likesCount?.takeIf { it >= 0 }
        if (validRating == null && validLikes == null) return
        val key = comicMetricCacheKey(sourceKey, comicId)
        val editor = preferences.edit()
        validRating?.let { editor.putString("$key:rating", it.toString()) }
        validLikes?.let { editor.putString("$key:likes", it.toString()) }
        editor.apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = preferences.registerOnSharedPreferenceChangeListener(listener)
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = preferences.unregisterOnSharedPreferenceChangeListener(listener)
}
