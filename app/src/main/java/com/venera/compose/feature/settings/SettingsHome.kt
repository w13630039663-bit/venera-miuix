package com.venera.compose.feature.settings

import com.venera.compose.components.PredictiveBackStack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.data.prefs.VeneraPreferences
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class SettingsCategory(val route: String, val title: String, val icon: ImageVector, val color: Color)

private val categories = listOf(
    SettingsCategory("explore", "探索", Icons.Filled.Explore, Color(0xFF2196F3)),
    SettingsCategory("blocking", "屏蔽与过滤", Icons.Filled.FilterAlt, Color(0xFFEF5350)),
    SettingsCategory("reader", "阅读", Icons.Filled.Book, Color(0xFF4CAF50)),
    SettingsCategory("appearance", "外观", Icons.Filled.ColorLens, Color(0xFF9C27B0)),
    SettingsCategory("favorites", "本地收藏", Icons.Filled.CollectionsBookmark, Color(0xFFFF9800)),
    SettingsCategory("app", "应用", Icons.Filled.Apps, Color(0xFF00BCD4)),
    SettingsCategory("network", "网络", Icons.Filled.Public, Color(0xFF009688))
)

/** 独立且可保存的页栈，不修改应用 Navigation，也不把分类页变成弹窗。 */
@Composable
internal fun SettingsHome(
    onSources: () -> Unit, onDownloads: () -> Unit, onLocalComics: () -> Unit,
    onStats: () -> Unit, onImages: () -> Unit, onGuard: () -> Unit,
    onSync: () -> Unit, onLogs: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { VeneraPreferences.getInstance(context) }
    var stack by rememberSaveable { mutableStateOf(listOf("home")) }
    fun push(page: String) { if (stack.last() != page) stack = stack + page }
    PredictiveBackStack(
        entries = stack,
        onBack = { if (stack.size > 1) stack = stack.dropLast(1) },
        modifier = Modifier.fillMaxSize(),
        entryKey = { it },
    ) { route ->
        // A retained/preview page must never pop the current page via a stale callback.
        fun back() { if (stack.last() == route && stack.size > 1) stack = stack.dropLast(1) }
        when {
            route == "home" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 80.dp)) {
                AboutSection()
                SettingsGroup {
                    categories.forEach { category ->
                        Row(Modifier.fillMaxWidth().clickable { push(category.route) }
                            .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionIconBadge(category.color) { Icon(category.icon, null, tint = category.color, modifier = Modifier.size(20.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Text(category.title, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text("›", fontSize = 22.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .4f))
                        }
                    }
                }
            }
            route == "explore" -> ExploreSettings(::back, onSources, { push("rules/KEYWORD") })
            route == "blocking" -> BlockingSettings(::back, { push("rules/$it") }, onGuard)
            route.startsWith("rules/") -> BlockingRulesSettings(route.substringAfter("/"), ::back)
            route == "reader" -> ReaderSettings(prefs, ::back, onImages, onStats)
            route == "appearance" -> AppearanceSettings(prefs, ::back)
            route == "favorites" -> LocalFavoritesSettings(prefs, ::back)
            route == "app" -> AppSettings(prefs, ::back, onSync, onLogs, onDownloads, onLocalComics)
            route == "network" -> NetworkSettings(prefs, ::back)
        }
    }
}
