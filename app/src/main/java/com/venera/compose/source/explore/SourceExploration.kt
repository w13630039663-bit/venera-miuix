package com.venera.compose.source.explore

import com.venera.compose.source.model.CategoryData
import com.venera.compose.source.model.CategoryPart
import com.venera.compose.source.model.ExplorePageData

/**
 * 一个漫画源**自己**的探索能力与分类体系。
 *
 * 设计契约（务必遵守）：
 *  - 本类型只描述「这个源有什么」，不参与任何跨源合并。
 *  - [nativeSections] 原样保留源声明的分区名（如「热门主题」「作品类型」「专题」/「Group」「Parody」），
 *    绝不做同义归并；Picacg 的「专题」和 nhentai 的「Parody」是两个不同的东西。
 *  - 某源没有的能力就是 null / 空，UI 据此隐藏，**不伪造空按钮**。
 */
data class SourceExploration(
    /** 源 key，唯一身份。 */
    val sourceKey: String,
    /** 源显示名（Picacg / nhentai / 禁漫天堂 / ehentai）。 */
    val sourceName: String,

    /**
     * 探索方式（随机 / 最新 / H24 / D7 / D30 / 热门 / 关注 / 排行榜…）。
     * 来自源声明的 explore 页面 + 排行榜能力，**只包含该源真正支持的**。
     */
    val modes: List<ExploreMode> = emptyList(),

    /**
     * 该源**原生**的分类分区（分类 / Tag / 专题 / 排行榜入口）。
     * 直接来自 [CategoryData.parts]，保持源自己的命名与分组。
     */
    val nativeSections: List<NativeSection> = emptyList(),

    /** 该源是否支持排行榜（决定是否显示排行榜入口）。 */
    val hasRanking: Boolean = false,

    /** 该源是否声明了分类页（false 表示只有 explore，没有分类矩阵）。 */
    val hasCategoryPage: Boolean = false,
) {
    val isEmpty: Boolean get() = modes.isEmpty() && nativeSections.isEmpty() && !hasRanking
}

/**
 * 一种探索方式。语义由源自己决定 —— 应用层只负责呈现与转发，
 * 不解释 [kind] 以外的含义（也不假设所有源都有同一种 kind）。
 */
data class ExploreMode(
    /** 稳定标识：源 key + 页面下标，用于记住每个源的选中态。 */
    val id: String,
    /** 显示名，如「随机」「H24」「D7」「排行榜」。 */
    val label: String,
    val kind: Kind,
    /** multiPageComicList 时对应的 explore 页面下标；其他 kind 为 -1。 */
    val pageIndex: Int = -1,
    /** 排行榜内部选项（如 "day" / "week"）；非排行榜为 null。 */
    val rankingOption: String? = null,
) {
    enum class Kind {
        /** 源声明的多页列表流（最新 / H24 / D7 …）。 */
        EXPLORE_PAGE,
        /** 源声明的单页多分区（推荐 / 热门 混合在几个 part 里）。 */
        EXPLORE_MULTI_PART,
        /** 排行榜。 */
        RANKING,
    }
}

/**
 * 源自有的一个分类分区。与 [CategoryPart] 一一对应，不改名、不合并。
 */
data class NativeSection(
    val name: String,
    val type: String,
    val items: List<NativeEntry>,
)

/**
 * 分区里的一项（一个可点击的分类 / Tag / 专题）。
 */
data class NativeEntry(
    val label: String,
    /** 传给源的参数（[com.venera.compose.source.model.PageJumpTarget] 的 attributes["param"]）。 */
    val param: String?,
)

/**
 * 把源的 [ExplorePageData] + [CategoryData] 收敛成一份 [SourceExploration]。
 *
 * 这是**唯一的**构建入口：UI 永远不直接读 explore/category 原始结构，
 * 这样将来新增漫画源只要源脚本声明标准字段就能自动出现在探索页，无需改 UI。
 */
object SourceExplorationFactory {

    /** 官方 explore 页 type 字段的取值。 */
    private const val TYPE_MULTI_PART = "singlePageWithMultiPart"
    private const val TYPE_MULTI_LIST = "multiPageComicList"

    fun build(
        sourceKey: String,
        sourceName: String,
        explorePages: List<ExplorePageData>,
        categoryData: CategoryData?,
    ): SourceExploration {
        val modes = buildModes(sourceKey, explorePages, categoryData)
        val sections = categoryData?.parts.orEmpty().map { it.toNativeSection() }
        return SourceExploration(
            sourceKey = sourceKey,
            sourceName = sourceName,
            modes = modes,
            nativeSections = sections,
            hasRanking = categoryData?.enableRankingPage == true,
            hasCategoryPage = sections.isNotEmpty() || categoryData?.enableRankingPage == true,
        )
    }

    private fun buildModes(
        sourceKey: String,
        explorePages: List<ExplorePageData>,
        categoryData: CategoryData?,
    ): List<ExploreMode> {
        val modes = mutableListOf<ExploreMode>()

        // 源自己声明的 explore 页面 -> 逐个变成一个探索方式。
        // 标题做最小清洗（去掉与源名重复的部分），但语义仍是源自己的。
        explorePages.forEachIndexed { index, page ->
            val label = cleanExploreLabel(page.title, page.sourceName)
            if (label.isBlank()) return@forEachIndexed
            modes += ExploreMode(
                id = "$sourceKey:page:$index",
                label = label,
                kind = if (page.type == TYPE_MULTI_PART) ExploreMode.Kind.EXPLORE_MULTI_PART
                else ExploreMode.Kind.EXPLORE_PAGE,
                pageIndex = page.pageIndex,
            )
        }

        // 排行榜：只有源声明了 enableRankingPage 才出现，否则连按钮都不渲染。
        if (categoryData?.enableRankingPage == true) {
            modes += ExploreMode(
                id = "$sourceKey:ranking:day",
                label = "排行榜",
                kind = ExploreMode.Kind.RANKING,
                rankingOption = "day",
            )
        }
        return modes.distinctBy { it.id }
    }

    private fun CategoryPart.toNativeSection() = NativeSection(
        name = name,
        type = type,
        items = items.map { NativeEntry(it.label, it.target.attributes?.get("param")?.toString()) },
    )

    /** 去掉「源名 + 分隔符」前缀，例如 "拷贝漫画 - 最新" -> "最新"。 */
    private fun cleanExploreLabel(title: String, sourceName: String): String {
        val trimmed = title.trim()
        if (sourceName.isBlank()) return trimmed
        val stripped = trimmed
            .removePrefix(sourceName)
            .trimStart('-', '—', ':', '：', ' ', '·')
            .trim()
        return stripped.ifBlank { trimmed }
    }
}
