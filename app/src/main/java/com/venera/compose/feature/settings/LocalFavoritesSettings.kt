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
    val moveAfterRead by prefs.moveFavoriteAfterRead.collectAsState()
    val favoriteSort by prefs.favoriteSortOrder.collectAsState()
    // Empty folder names are rejected/renamed by the manager, so "" is a safe None key.
    val quickOptions = remember(folders) {
        listOf("" to "未设置") + folders.map { it to it }
    }
    val quickSummary = when {
        quickFavorite == null -> "未设置；长按收藏按钮时打开收藏面板"
        quickFavorite in folders -> "当前：$quickFavorite；长按收藏按钮快速加入此收藏夹"
        else -> "原收藏夹「$quickFavorite」已不存在，请重新选择"
    }

    SettingsPage(title = "本地收藏", onBack = onBack) {
        // 审计后删掉「在网络收藏前显示本地收藏」：键与 setter 都在，但没有任何消费者
        // （收藏面板顺序是硬编码的），留着就是一个改不动也无效的假开关。
        // 「点击收藏时」一并删除：需要新的行为分支与偏好，不属于补入口。
        SettingsGroup {
            UnsupportedSetting("操作后自动关闭收藏面板", "尚无对应的持久化设置与自动关闭逻辑。")
            SettingsSelect(
                title = "新收藏添加到",
                value = newFavoriteAddTo,
                options = listOf("start" to "开头", "end" to "末尾"),
                onSelected = prefs::setNewFavoriteAddTo
            )
            UnsupportedSetting("阅读后移动收藏", "已保存：" + when (moveAfterRead) { "start" -> "开头"; "end" -> "末尾"; else -> "不移动" } + "；阅读流程尚未调用管理器的移动方法。")
            SettingsSelect(
                title = "快捷收藏",
                value = quickFavorite ?: "",
                options = quickOptions,
                onSelected = { prefs.setQuickFavorite(it.takeIf { name -> name.isNotEmpty() }) },
                summary = quickSummary
            )
            UnsupportedSetting("删除所有不可用的本地收藏条目", "尚无不可用条目判定与清理方法；不会以清空全部收藏代替。")
        }
        SettingsGroup("收藏列表") {
            // 偏好早已真实生效（FavoritesViewModel 读写），但此前只能在收藏页顶栏改，
            // 设置树里没有行 —— 这里补的是入口，不是新能力，两处共用同一个键。
            SettingsSelect(
                title = "收藏夹排序",
                value = favoriteSort,
                options = FavoriteSortOrder.entries.map { it.name to it.label },
                onSelected = prefs::setFavoriteSortOrder,
                summary = "与收藏页顶栏的排序共用同一偏好，任一处修改即同步。",
            )
        }
    }
}
