package com.venera.compose.feature.explore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.venera.compose.source.explore.SourceExploration
import com.venera.compose.source.explore.UnifiedTag
import com.venera.compose.source.model.CategoryComicsOption
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ExplorePagePart

/**
 * 探索页的全部页面状态。
 *
 * **为什么必须是 ViewModel，而不是屏幕里的 remember**：进入详情页时，本导航条目的组合会被
 * 销毁（真机实测过同样的坑：收藏页的 mode 用普通 remember，返回就被打回默认值）。字段全
 * 重建意味着 `selectedSourceKey` 变 null、两个 LaunchedEffect 重新执行 —— 于是每次返回都
 * 自动重拉源列表与第 1 页，并跳回**第一个源的默认探索方式**（picacg 的「随机」）。
 *
 * ViewModelStore 挂在导航条目上，条目还在返回栈上时它不销毁，所以返回后：选中源与探索方式
 * 保持原样、已加载的内容还在、也不再发请求。手动刷新（↻）与切源/切方式照常触发。
 */
class ExploreViewModel : ViewModel() {

    /** 选中源 + 每源各自的探索方式（跨源记忆，逻辑见 [ExploreUiState]）。 */
    val ui = ExploreUiState()

    var explorations by mutableStateOf<List<SourceExploration>>(emptyList())
    var isLoadingSources by mutableStateOf(true)
    var sourceError by mutableStateOf<String?>(null)

    var content by mutableStateOf<ExploreContent>(ExploreContent.Empty)
    var isLoadingContent by mutableStateOf(false)
    var contentError by mutableStateOf<String?>(null)

    var currentPage by mutableIntStateOf(1)
    var hasMore by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var loadMoreError by mutableStateOf<String?>(null)

    /** 跨页累计的原始分区（未过滤），分页追加与去重的唯一数据源。 */
    var accumulatedParts by mutableStateOf<List<ExplorePagePart>>(emptyList())

    /** 展示形态：true = 按分区渲染（singlePageWithMultiPart）。 */
    var showAsSections by mutableStateOf(false)

    /** 手动刷新计数：↻ 按钮与错误重试自增，驱动两个加载 effect 重跑。 */
    var refreshTick by mutableIntStateOf(0)

    /** 探索方式切换计数：让内容 effect 重跑（不重载源列表）。 */
    var modeTick by mutableIntStateOf(0)

    /**
     * 已经拉完源列表时的 [refreshTick] 值。effect 在组合重建后会再跑一次，
     * 靠这个值判定「这一轮已经拉过了」直接返回 —— 不这么拦，返回就必然重拉。
     */
    var sourcesLoadedForTick by mutableIntStateOf(-1)

    /** 已经拉好第 1 页的 `sourceKey#modeId`，同上，用于跳过返回时的重复请求。 */
    var contentLoadedKey by mutableStateOf<String?>(null)

    /**
     * 上方「快捷筛选」区块里选中的跨源通用标签。
     *
     * 非 null 时内容区展示的是**本源搜索该标签**的结果（页内刷新，不跳二级页）；
     * 切源、切探索方式或点「清除」都会置回 null。
     */
    var unifiedFilter by mutableStateOf<UnifiedTag?>(null)

    /** 下方「本源分类」区块是否展开全部入口（默认只露前 [SECTION_ENTRY_BUDGET] 个）。 */
    var sectionsExpanded by mutableStateOf(false)

    companion object {
        /**
         * 首屏默认露出的分类入口数（约 2–3 行）。
         *
         * 源脚本里没有频率/热度数据可用，所以截断口径 = 源脚本自带顺序，
         * 官方顺序本身就是按重要性排的。
         */
        const val SECTION_ENTRY_BUDGET = 8
    }
}

/**
 * 分类页（探索的二级页）状态，理由同 [ExploreViewModel]：返回时不重建、不自动刷新。
 *
 * 每个 SourceSectionRoute 条目各持一份，切分类不会串数据。
 */
class SourceSectionViewModel : ViewModel() {

    var comics by mutableStateOf<List<Comic>>(emptyList())
    var options by mutableStateOf<List<CategoryComicsOption>>(emptyList())
    var selectedOptions by mutableStateOf<List<String>>(emptyList())
    var page by mutableIntStateOf(1)
    var maxPage by mutableStateOf<Int?>(null)
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    /** 手动刷新计数（↻ 按钮）。 */
    var reloadTick by mutableIntStateOf(0)

    /** 已加载完第 1 页时的 `reloadTick`；返回时命中就不再发请求。 */
    var loadedForTick by mutableIntStateOf(-1)

    /** 已加载完第 1 页时的筛选项选择，用于判定「切了排序才重拉」。 */
    var loadedOptions by mutableStateOf<List<String>>(emptyList())
}
