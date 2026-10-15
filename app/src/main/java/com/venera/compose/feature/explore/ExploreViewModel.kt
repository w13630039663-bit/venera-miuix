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

    /**
     * 上一次**成功探测**的源列表（内存级缓存，不落盘）。
     *
     * 缓存兜底：本轮探测失败的源若上次成功过，沿用上次的结果 —— 网络抖动时
     * 列表不缩水，用户看到的还是那颗熟悉的源而不是"源凭空消失"。
     * 只缓存成功的（probeFailed = false），失败源下一轮重新探测。
     */
    var lastGoodExplorations by mutableStateOf<List<SourceExploration>>(emptyList())

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

    /**
     * 分类区里，**超预算的那些组是否也摆出来**。
     *
     * 分类区本身是**常驻**的（2026-10-14 从「分类 · N」按钮改回来：探索 = 浏览，
     * 分类就是主干道，折进按钮等于要先点一下才看得见能逛什么）。这一枚只管区内
     * 那枚「更多分类」—— 首屏只露前 [SECTION_ENTRY_BUDGET] 个入口，预算用尽后被
     * 整组切掉的尾巴由这一枚放出。**默认 false 不变**，首屏观感沿用旧基线。
     *
     * ⚠️ 改的是职责边界，不是数值：8 与 20 两道闸一个都不动。
     */
    var sectionsExpanded by mutableStateOf(false)

    /**
     * 动作行里「筛选 · N」是否就地撑开（展开的是通用标签 FlowRow）。
     *
     * 筛选是**辅助**动作（通用标签通常 3~5 枚），折在按钮里点开就地撑开 ——
     * 推走内容，不是弹层、不是新页、也没有「确定」那一步。
     * 切源 / 切模式必须收起（筛选本身也会被清掉，见 UnifiedExploreScreen 的纪律），
     * 否则会留着上一个源的标签在新源里摊着一屏。
     */
    var filterExpanded by mutableStateOf(false)

    companion object {
        /**
         * 首屏默认露出的分类入口数（约 2–3 行）。
         *
         * 源脚本里没有频率/热度数据可用，所以截断口径 = 源脚本自带顺序，
         * 官方顺序本身就是按重要性排的。
         */
        const val SECTION_ENTRY_BUDGET = 8

        /**
         * 折叠态下**单个分区**渲染的入口上限 —— 预算管的是"露几组"（组级，2026-09-20 已拍板），
         * 这条管"一组最长成什么样"，是 mh1234「题材」343 枚那种大组唯一的闸门。
         *
         * 20 不是造的数：它是全仓唯一一处由源自己声明的窗口尺寸
         * （nhentai.js:551 `randomNumber: 20`）。定在它下面，会让 fixed 组比官方的 random 组更短。
         */
        const val SECTION_ITEM_CAP = 20
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
