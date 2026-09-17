package com.venera.compose.components

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.data.prefs.ComicListPreferences
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared by all lists. Writes immediately; preference callbacks keep mounted screens in sync. */
@Composable
fun rememberComicListDisplayMode(): MutableState<String> {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { ComicListPreferences(context) }
    val current = remember(preferences) { mutableStateOf(preferences.displayMode) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            current.value = preferences.displayMode
        }
        preferences.registerListener(listener)
        current.value = preferences.displayMode
        onDispose { preferences.unregisterListener(listener) }
    }
    return remember(preferences, current) {
        object : MutableState<String> {
            override var value: String
                get() = current.value
                set(value) {
                    val normalized = normalizeComicDisplayMode(value)
                    current.value = normalized
                    preferences.displayMode = normalized
                }
            override fun component1(): String = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}

/** Observe real source metrics without querying a source from a card or changing favorite schemas. */
@Composable
fun rememberCachedComicMetrics(sourceKey: String, comicId: String): State<com.venera.compose.data.prefs.CachedComicMetrics> {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { com.venera.compose.data.prefs.ComicMetricsCache(context) }
    val state = remember(cache, sourceKey, comicId) { mutableStateOf(cache.get(sourceKey, comicId)) }
    DisposableEffect(cache, sourceKey, comicId) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.value = cache.get(sourceKey, comicId) }
        cache.registerListener(listener)
        state.value = cache.get(sourceKey, comicId)
        onDispose { cache.unregisterListener(listener) }
    }
    return state
}

/** A neutral responsive body: the caller retains click, long-click, selection and menu actions. */
@Composable
fun ComicCardLayout(
    detailed: Boolean,
    modifier: Modifier = Modifier,
    cover: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (detailed) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(122.dp)) { cover() }
            Column(Modifier.weight(1f)) { content() }
        }
    } else {
        Column(modifier) {
            cover()
            content()
        }
    }
}

/** Numeric text only: the source did not supply a maximum, so do not invent a five-star scale. */
@Composable
fun ComicMetrics(rating: Double? = null, likesCount: Int? = null, modifier: Modifier = Modifier) {
    val text = comicMetricsText(rating, likesCount)
    if (text.isNotEmpty()) {
        Text(text = text, fontSize = 11.sp, color = MiuixTheme.colorScheme.primary, modifier = modifier)
    }
}
