package com.venera.compose.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.feature.FavoriteSortOrder

@Composable
fun LocalFavoritesSettings(prefs: VeneraPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { LocalFavoritesManager.getInstance(context) }
    val folders by manager.folders.collectAsState()
    val newFavoriteAddTo by prefs.newFavoriteAddTo.collectAsState()
    val quickFavorite by prefs.quickFavorite.collectAsState()
    val favoriteSort by prefs.favoriteSortOrder.collectAsState()
    // Empty folder names are rejected/renamed by the manager, so "" is a safe None key.
    val quickOptions = remember(folders) {
        listOf("" to "未设置") + folders.map { it to it }
    }
    val quickSummary = when {
        quickFavorite == null -> "未设置。长按收藏按钮会打开收藏面板"
        quickFavorite in folders -> "当前：$quickFavorite。长按收藏按钮直接加进这个收藏夹"
        else -> "原来的收藏夹 $quickFavorite 已经不存在了，重新选一个"
    }

    SettingsPage(title = "收藏", onBack = onBack, largeTitle = "本地收藏", heroSubtitle = "存储位置 · 排序") {
        // 审计后删掉「在网络收藏前显示本地收藏」：键与 setter 都在，但没有任何消费者
        // （收藏面板顺序是硬编码的），留着就是一个改不动也无效的假开关。
        // 「点击收藏时」一并删除：需要新的行为分支与偏好，不属于补入口。
        SettingsGroup {
            SettingsSelect(
                title = "新收藏添加到",
                value = newFavoriteAddTo,
                options = listOf("start" to "开头", "end" to "末尾"),
                onSelected = prefs::setNewFavoriteAddTo
            )
            SettingsSelect(
                title = "快捷收藏",
                value = quickFavorite ?: "",
                options = quickOptions,
                onSelected = { prefs.setQuickFavorite(it.takeIf { name -> name.isNotEmpty() }) },
                summary = quickSummary
            )
        }
        SettingsGroup("收藏列表") {
            // 偏好早已真实生效（FavoritesViewModel 读写），但此前只能在收藏页顶栏改，
            // 设置树里没有行 —— 这里补的是入口，不是新能力，两处共用同一个键。
            SettingsSelect(
                title = "收藏夹排序",
                value = favoriteSort,
                options = FavoriteSortOrder.entries.map { it.name to it.label },
                onSelected = prefs::setFavoriteSortOrder,
                summary = "这里改和收藏页顶栏改是同一个设置。",
            )
        }
    }
}
