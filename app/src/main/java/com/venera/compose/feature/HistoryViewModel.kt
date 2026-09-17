package com.venera.compose.feature

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.venera.compose.data.db.HistoryDao
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.data.db.LocalFavoritesManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 历史页 ViewModel（S5-4）。
 *
 * 对齐原版 `pages/history_page.dart`：网格展示 + 多选删除 + 清空（全部 / 仅未收藏）。
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = HistoryDao.getInstance(application)
    private val favorites = LocalFavoritesManager.getInstance(application)

    val history: StateFlow<List<HistoryRecord>> = dao.historyFlow

    var multiSelectMode: Boolean by mutableStateOf(false)
        private set

    /** 选中项：`(comicId, sourceName)`，与 v2 主键一致。 */
    var selected: Set<Pair<String, String>> by mutableStateOf(emptySet())
        private set

    fun toggleSelect(record: HistoryRecord) {
        val key = record.comicId to record.sourceName
        selected = if (key in selected) selected - key else selected + key
        if (selected.isEmpty()) multiSelectMode = false
    }

    fun enterMultiSelect(record: HistoryRecord) {
        multiSelectMode = true
        selected = selected + (record.comicId to record.sourceName)
    }

    fun selectAll(records: List<HistoryRecord>) {
        selected = records.map { it.comicId to it.sourceName }.toSet()
    }

    fun exitMultiSelect() {
        multiSelectMode = false
        selected = emptySet()
    }

    fun deleteSelected() {
        val records = history.value.filter { (it.comicId to it.sourceName) in selected }
        viewModelScope.launch {
            dao.batchDeleteHistory(records)
            exitMultiSelect()
        }
    }

    fun deleteSingle(record: HistoryRecord) {
        viewModelScope.launch { dao.deleteHistory(record.comicId, record.sourceName) }
    }

    fun clearAll() {
        viewModelScope.launch {
            dao.clearAll()
            exitMultiSelect()
        }
    }

    /** 只清空「未被收藏」的历史（官方 `clearUnfavoritedHistory`）。 */
    fun clearUnfavorited() {
        viewModelScope.launch {
            val all = history.value
            val toDelete = all.filter { record ->
                !favorites.isExist(record.comicId, record.sourceName)
            }
            dao.batchDeleteHistory(toDelete)
            exitMultiSelect()
        }
    }
}

/**
 * 进度描述，对齐原版 `history_page.dart` 的 `getDescription`：
 * 章节号与页码都 >= 1 才显示，两者都有时用 " - " 连接。
 */
fun HistoryRecord.progressDescription(): String {
    val ep = lastChapterIndex + 1
    val page = lastPageIndex + 1
    return buildString {
        if (ep >= 1) append("第 $ep 话")
        if (page >= 1) {
            if (isNotEmpty()) append(" - ")
            append("第 $page 页")
        }
    }
}
