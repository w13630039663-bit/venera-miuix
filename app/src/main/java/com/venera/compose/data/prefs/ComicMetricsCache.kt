package com.venera.compose.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.data.platform.android.AndroidKeyValueStore

/** Optional snapshot of metrics actually returned by a source. No database schema change. */
data class CachedComicMetrics(val rating: Double? = null, val likesCount: Int? = null)

/** Length-prefix keys avoid collisions between sources and ids containing separators. */
fun comicMetricCacheKey(sourceKey: String, comicId: String): String = "${sourceKey.length}:$sourceKey$comicId"

class ComicMetricsCache(context: Context) {
    private val preferences = AndroidKeyValueStore(context.applicationContext, PreferenceKeys.PREFS_COMIC_METRICS)

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
        // 攒成一次 putAll：两条观测同进同出，跟收口前那个 editor 一个口径
        val values = mutableMapOf<String, Any?>()
        validRating?.let { values["$key:rating"] = it.toString() }
        validLikes?.let { values["$key:likes"] = it.toString() }
        preferences.putAll(values)
    }

    // 变更监听仍是 Android 原生回调：接口里没有这一格，调用方（列表卡片）的签名不动
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = preferences.registerListener(listener)
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = preferences.unregisterListener(listener)
}
