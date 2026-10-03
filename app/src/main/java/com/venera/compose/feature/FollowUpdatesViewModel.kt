package com.venera.compose.feature

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.db.FavoriteItemWithUpdateInfo
import com.venera.compose.data.db.FollowUpdatesRepository
import com.venera.compose.data.api.BusinessPorts
import kotlinx.coroutines.launch

/**
 * 追更 / 更新列表页 ViewModel。
 */
class FollowUpdatesViewModel(
    application: Application,
    private val ports: BusinessPorts,
) : AndroidViewModel(application) {

    /** 装配入口：调用点（17 处 viewModel()）与 AndroidViewModelFactory 反射的那颗 arity=1 构造逐字不变。 */
    constructor(application: Application) : this(application, BusinessPorts.of(application))

    private val repo = FollowUpdatesRepository.getInstance(application)
    private val prefs = ports.comicPrefs

    /** 当前用于追更的收藏夹（null = 未开启追更）。 */
    var followFolder: String? by mutableStateOf(prefs.followUpdatesFolder.value)
        private set

    var updates: List<FavoriteItemWithUpdateInfo> by mutableStateOf(emptyList())
        private set

    var isChecking: Boolean by mutableStateOf(false)
        private set

    var progress: String? by mutableStateOf(null)
        private set

    fun refresh() {
        viewModelScope.launch {
            followFolder = prefs.followUpdatesFolder.value
            val folder = followFolder
            updates = if (folder.isNullOrBlank()) emptyList() else repo.getUpdates(folder)
        }
    }

    /**
     * 立即检查。
     * @param ignoreCheckTime true = 忽略「24 小时内已检查过」的节流，强制全量
     */
    fun checkNow(ignoreCheckTime: Boolean = false) {
        val folder = followFolder
        if (folder.isNullOrBlank()) return
        viewModelScope.launch {
            isChecking = true
            progress = "准备检查…"
            repo.updateFolder(folder, ignoreCheckTime) { p ->
                progress = "检查中 ${p.current}/${p.total} · 更新 ${p.updated} · 失败 ${p.errors}"
            }
            updates = repo.getUpdates(folder)
            isChecking = false
            progress = null
        }
    }

    fun markAsRead(item: FavoriteItemWithUpdateInfo) {
        viewModelScope.launch {
            repo.markAsRead(item.item.id, item.item.sourceKey)
            refresh()
        }
    }

    /** 收藏夹清单，供「选择追更夹」对话框使用。 */
    fun folderList(): kotlinx.coroutines.flow.StateFlow<List<String>> =
        ports.favorites.folders

    /** 把某个收藏夹设为/取消追更夹。 */
    fun chooseFollowFolder(folder: String?) {
        viewModelScope.launch {
            prefs.setFollowUpdatesFolder(folder)
            if (!folder.isNullOrBlank()) {
                ports.favorites
                    .prepareTableForFollowUpdates(folder)
            }
            refresh()
        }
    }
}
