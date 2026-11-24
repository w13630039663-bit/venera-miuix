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

    /**
     * 追更夹里的**全部**条目（含今天没有新章的），新章在前、其余按检查时间倒序。
     *
     * 收藏页「更新」段列这一份而不是 [updates]：用户的原话是"追更页应该也能显示
     * 目前追更的漫画，而不是只有更新之后才显示" —— 只列新章会让这一屏在
     * "今天没更新"时看起来像空的，用户无法确认自己到底在追哪几本。
     */
    var followed: List<FavoriteItemWithUpdateInfo> by mutableStateOf(emptyList())
        private set

    /** 收藏页「更新」段的角标：夹内 `has_new_update = 1` 的条数（0 时 UI 不画角标）。 */
    var badgeCount: Int by mutableStateOf(0)
        private set

    /**
     * 追更夹里的**总本数**（含没有新章的）。
     *
     * 不能用 [updates].size 代替：那只返回有新章的条目，
     * 刚在详情页加进来的书还没检查过、也没有 `has_new_update`，会被算成 0 本。
     */
    var followedTotal: Int by mutableStateOf(0)
        private set

    /** 最近一次检查时刻（毫秒）；取夹内**全部**条目 `last_check_time` 的最大值，没有就 null。 */
    var lastCheckAt: Long? by mutableStateOf(null)
        private set

    var isChecking: Boolean by mutableStateOf(false)
        private set

    var progress: String? by mutableStateOf(null)
        private set

    fun refresh() {
        viewModelScope.launch {
            followFolder = prefs.followUpdatesFolder.value
            val folder = followFolder
            if (folder.isNullOrBlank()) {
                updates = emptyList()
                followed = emptyList()
                badgeCount = 0
                followedTotal = 0
                lastCheckAt = null
                return@launch
            }
            val all = repo.getAllInFolder(folder)
            updates = repo.getUpdates(folder)
            followed = sortFollowed(all)
            badgeCount = repo.countUpdates(folder)
            followedTotal = all.size
            lastCheckAt = all.mapNotNull { it.lastCheckTime }.maxOrNull()
        }
    }

    /**
     * 立即检查。
     * @param ignoreCheckTime true = 忽略「24 小时内已检查过」的节流，强制全量。
     *        页面上的「立即检查」必须传 true：默认 false 时 24h 内待检队列为空，
     *        点了等于没点（仓库本身早就支持这颗参数，只是此前没有任何调用点传它）。
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
            val all = repo.getAllInFolder(folder)
            updates = repo.getUpdates(folder)
            followed = sortFollowed(all)
            badgeCount = repo.countUpdates(folder)
            followedTotal = all.size
            lastCheckAt = all.mapNotNull { it.lastCheckTime }.maxOrNull()
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

    /**
     * 更新段的排序：有新章的置顶（蓝点/NEW 就是"要先看"的语义），
     * 同组内按上次检查时间倒序 —— 刚查过的排前面，读起来像"最近有动静"。
     */
    private fun sortFollowed(all: List<FavoriteItemWithUpdateInfo>): List<FavoriteItemWithUpdateInfo> =
        all.sortedWith(
            compareByDescending<FavoriteItemWithUpdateInfo> { it.hasNewUpdate }
                .thenByDescending { it.lastCheckTime ?: 0L }
        )

    /** 把某个收藏夹设为/取消追更夹。 */
    fun chooseFollowFolder(folder: String?) {
        viewModelScope.launch {
            prefs.setFollowUpdatesFolder(folder)
            if (!folder.isNullOrBlank()) {
                // clearData 必须显式 false：默认 true 会把选中的收藏夹里已有的
                // `has_new_update` / `last_check_time` 全清掉，等于一换夹就把积压的新章标记抹平。
                ports.favorites.prepareTableForFollowUpdates(folder, clearData = false)
            }
            refresh()
        }
    }
}
