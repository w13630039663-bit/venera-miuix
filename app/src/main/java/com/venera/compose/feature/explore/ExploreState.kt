package com.venera.compose.feature.explore

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.venera.compose.source.explore.ExploreMode

/**
 * 「探索」页的跨源状态。
 *
 * 关键设计：**筛选状态按源分开保存**（[perSourceMode] / [perSourceScrollKey]）。
 * 用户从 Picacg 切到 nhentai 再切回来，Picacg 之前选中的探索方式与下钻位置都还在 ——
 * 这就是「保留当前漫画源的筛选状态」，而不是把各源状态压成一份全局状态。
 */
@Stable
class ExploreUiState {

    /** 当前选中的源 key（null = 还没选/还没加载完）。 */
    var selectedSourceKey by mutableStateOf<String?>(null)

    /** 每个源各自选中的探索方式 id。 */
    private val perSourceMode = mutableStateMapOf<String, String>()

    fun selectedModeId(sourceKey: String): String? = perSourceMode[sourceKey]

    /**
     * 记住某源选中的探索方式。切换源时不清理，因此切回来仍是上次那个。
     */
    fun selectMode(sourceKey: String, modeId: String) {
        perSourceMode[sourceKey] = modeId
    }

    /**
     * 为一个源挑选默认探索方式：优先沿用上次的选择（若仍存在），
     * 否则取第一个可用项。返回 null 表示该源没有任何探索方式。
     */
    fun resolveMode(sourceKey: String, modes: List<ExploreMode>): ExploreMode? {
        if (modes.isEmpty()) return null
        val remembered = perSourceMode[sourceKey]
        return modes.firstOrNull { it.id == remembered } ?: modes.first()
    }
}
