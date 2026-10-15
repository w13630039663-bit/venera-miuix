package com.venera.compose.source.explore

import com.venera.compose.source.model.CategoryData
import com.venera.compose.source.model.CategoryPart
import com.venera.compose.source.model.ExplorePageData
import kotlin.random.Random

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
     * 该源**原生**的分类分区（分类 / Tag / 专题）。
     * 直接来自 [CategoryData.parts]，保持源自己的命名与分组，**不含任何合成组**。
     */
    val nativeSections: List<NativeSection> = emptyList(),

    /**
     * 排行榜分区（源声明 `enableRankingPage` 且有档位时非 null），一档一枚入口。
     *
     * 单独开字段而不是塞进 [nativeSections]：那个字段的契约是「原样保留源声明的分区名、
     * 绝不合并」，而 zaimanhua 自己就有一个叫「排行榜」的 fixed 分区，合成组进去会撞名。
     * 每项的 [NativeEntry.rankingOption] 非 null，UI 据此走「页内切档」而不是跳二级页。
     */
    val rankingSection: NativeSection? = null,

    /** 该源是否支持排行榜（决定是否显示排行榜入口）。 */
    val hasRanking: Boolean = false,

    /** 该源是否声明了分类页（false 表示只有 explore，没有分类矩阵）。 */
    val hasCategoryPage: Boolean = false,

    /**
     * 探测这颗源时**抛了异常**（网络失败 / 源脚本报错），能力未知。
     *
     * true 时 [modes] / [nativeSections] 都是空的，但这一颗**必须留在列表里** ——
     * 探测失败不等于源没有探索能力，一次网络抖动就把它剔除，屏上表现就是
     * "源凭空消失"（2026-10-14 真机：16:57 还有禁漫天堂，18:26 就只剩两颗源）。
     * UI 据此摆重试，而不是让用户以为这颗源不能探索。
     */
    val probeFailed: Boolean = false,

    /**
     * 这颗源是否**支持登录**（`SourceAccountInfo.hasAccount`）。
     *
     * 只服务于错误文案的分支：源站回 401/403 时，支持登录的源十有八九是"要先登录"，
     * 不支持的只能是源站自己拦了 —— 两种情况的下一步完全不同，合成一句话就会把用户
     * 往错的方向指（2026-10-14 真机：包子漫画被说成"源站防护或网络波动"，
     * 用户在原话里直接否掉了这句）。
     */
    val supportsLogin: Boolean = false,
) {
    val isEmpty: Boolean get() = !probeFailed && modes.isEmpty() && nativeSections.isEmpty() && !hasRanking
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
        /**
         * `mixed` 型：第 1 页给若干**带标题的分区**，第 2 页起给**裸作品列表**（nhentai）。
         *
         * 为什么单开一档而不是并进 [EXPLORE_MULTI_PART]：后者按定义是「单页」，
         * 在页面上永不翻页（`hasMore` 恒 false）。mixed 的 `load(page)` 是**真分页**的
         * （nhentai 的 New Uploads 靠它续页），并进多分区就等于把「翻页」这件事一起丢掉。
         * 也不能并进 [EXPLORE_PAGE]：那样第 1 页的两个分区标题会被压平成一个大网格。
         */
        EXPLORE_MIXED,
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
    /**
     * 这一项该跳去哪一种页 —— **原样透传官方 `PageJumpTarget.page`（即源声明的 `itemType`）**。
     *
     * 官方 `models.dart:537-560` 的 `jump()` 就按这个字段分发：
     *  - [PAGE_KIND_CATEGORY]（默认）→ CategoryComicsPage，走 `categoryComics.load`；
     *  - [PAGE_KIND_SEARCH] → SearchResultPage，走 `search.load`，关键词取 [keyword]。
     *
     * **丢了它就会把「search 型」当「category 型」发请求**：这类项没有 `categoryParams`，
     * `param` 恒为 null，`categoryComics.load` 里的 `param ??= category` 于是拿分类名去查分类接口
     * —— 禁漫天堂的「主題A漫 / 角色扮演 / 特殊PLAY」全部退化成同一份内容（本字段修复前的真实现象）。
     */
    val page: String = PAGE_KIND_CATEGORY,
    /**
     * search 型的搜索关键词（官方 `attributes["keyword"]`，**源侧原文、未被字典翻译**）。
     *
     * 显示用 [label]（可能已翻译），发请求必须用本字段，否则翻译过的标签会被拿去搜索。
     * 官方对 `itemType == "search"` 只写 keyword（`parser.dart:503`），对 `search_with_namespace`
     * 写的是 `"$name:$tags[i]"`（`:505-507`）—— 两种情况本字段都已是最终值。
     */
    val keyword: String? = null,
    /**
     * 非 null 表示这是一枚**排行榜档位**入口，值就是要回传给源的 option key
     * （空串合法：manhuagui 的 `-最新发布` 拆出空 key，源用它表示默认排序）。
     *
     * UI 见到它必须走「页内选中对应探索方式」，**不能**跳 [com.venera.compose.feature.explore.SourceSectionScreen] ——
     * 那一页只会调 `loadCategoryComics`（分类端点），对排行请求是错的端点。
     */
    val rankingOption: String? = null,
)

/** 源 `itemType` / 官方 `PageJumpTarget.page`：走源的分类接口。 */
const val PAGE_KIND_CATEGORY = "category"

/** 源 `itemType` / 官方 `PageJumpTarget.page`：走源的搜索接口。 */
const val PAGE_KIND_SEARCH = "search"

/**
 * 把源的 [ExplorePageData] + [CategoryData] 收敛成一份 [SourceExploration]。
 *
 * 这是**唯一的**构建入口：UI 永远不直接读 explore/category 原始结构，
 * 这样将来新增漫画源只要源脚本声明标准字段就能自动出现在探索页，无需改 UI。
 */
object SourceExplorationFactory {

    /**
     * explore 页 `type` 字段的四个取值，一个都不能少。
     *
     * 2026-10-14 真机读数：nhentai 与禁漫天堂的默认页一个报错、一个空白。根因不在 UI，
     * 在这四个值里有三个没被引擎认全 —— 于是「默认该显示什么」根本无从谈起：
     *  - [TYPE_MULTI_PART] `singlePageWithMultiPart`：单页多分区（包子 / 拷贝 / Komga…），
     *    `load()` 无参，返回 `{分区名: Comic[]}` 或 `[{title, comics}]`；
     *  - [TYPE_MULTI_PART_PAGE] `multiPartPage`：**10 颗源在用**（禁漫天堂 / 紳士漫畫 / MangaDex /
     *    漫画柜 / 漫画人 / mh18 / 漫小肆 / 优酷漫画 / goda / h-comic），`load(page)` 返回
     *    `[{title, comics, viewMore?}]`；
     *  - [TYPE_MULTI_LIST] `multiPageComicList`：分页列表（ehentai / picacg / hitomi…），
     *    返回 `{comics, maxPage}`；
     *  - [TYPE_MIXED] `mixed`：首页给分区、后续页给裸列表（nhentai），
     *    返回 `{data: [Comic[] | {title, comics}], maxPage}`。
     *
     * 前两者都是「一页多分区」，在 UI 上是同一件事（按分区渲染），故同归
     * [ExploreMode.Kind.EXPLORE_MULTI_PART]；[TYPE_MIXED] 是唯一「首页分区、后续页平铺且
     * 可分页」的混合体，单开 [ExploreMode.Kind.EXPLORE_MIXED]。
     */
    private const val TYPE_MULTI_PART = "singlePageWithMultiPart"
    private const val TYPE_MULTI_PART_PAGE = "multiPartPage"
    private const val TYPE_MULTI_LIST = "multiPageComicList"
    private const val TYPE_MIXED = "mixed"

    fun build(
        sourceKey: String,
        sourceName: String,
        explorePages: List<ExplorePageData>,
        categoryData: CategoryData?,
        supportsLogin: Boolean = false,
    ): SourceExploration {
        val modes = buildModes(sourceKey, explorePages, categoryData)
        val sections = categoryData?.parts.orEmpty().map { it.toNativeSection() }
        // 有排行能力 = 声明了 enableRankingPage **且**真给了档位：
        // 只有前者就出入口的话，点上去没有任何可请求的 option，等于摆一个死按钮。
        val hasRanking = categoryData?.enableRankingPage == true && categoryData.rankingOptions.isNotEmpty()
        // 榜位逐档一枚入口，标签用**源自己给的档名**（jm「日排行」、picacg「日」、nhentai "Popular Today"），
        // 不套「排行·X」前缀 —— 那一层前缀是为了在探索方式行里和「随机/最新」区分开，
        // 而在排行榜分区里每一枚都是榜，前缀纯属重复。
        val rankingSection = if (hasRanking) NativeSection(
            name = RANKING_SECTION_NAME,
            type = TYPE_RANKING,
            items = categoryData?.rankingOptions.orEmpty().map { opt ->
                NativeEntry(label = opt.label, param = null, rankingOption = opt.key)
            },
        ) else null
        return SourceExploration(
            sourceKey = sourceKey,
            sourceName = sourceName,
            modes = modes,
            nativeSections = sections,
            rankingSection = rankingSection,
            hasRanking = hasRanking,
            hasCategoryPage = sections.isNotEmpty() || hasRanking,
            supportsLogin = supportsLogin,
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
                // 四个 type 收敛到三种 Kind：两种「一页多分区」形态在 UI 上是同一件事。
                // 漏掉 multiPartPage 的后果是实打实的 —— 那 10 颗源的默认页会当成
                // 「分页列表」去解析，拿回来的分区对象里没有 comic 字段，整页解析成空。
                kind = when (page.type) {
                    TYPE_MULTI_PART, TYPE_MULTI_PART_PAGE -> ExploreMode.Kind.EXPLORE_MULTI_PART
                    TYPE_MIXED -> ExploreMode.Kind.EXPLORE_MIXED
                    else -> ExploreMode.Kind.EXPLORE_PAGE
                },
                pageIndex = page.pageIndex,
            )
        }

        // 排行榜：**档位逐条来自源自己声明的 `categoryComics.ranking.options`**。
        // 原先这里硬编码一个 mode、option 写死 "day" —— 那是我们臆造的值：哔咔的合法档位是
        // H24/D7/D30（picacg.js:360-364），禁漫是 mv/mv_m/mv_w/mv_t（jm.js），EH 是 15/13/12/11。
        // 发出去的 `tt=day` 要么被源判非法、要么静默空榜（错误此前还被吞成空列表）。
        // 现在一档一个入口；源没声明档位就不出入口（本类型的既有契约：不伪造按钮）。
        if (categoryData?.enableRankingPage == true) {
            categoryData.rankingOptions.forEach { opt ->
                modes += ExploreMode(
                    id = "$sourceKey:ranking:${opt.key}",
                    label = "排行·${opt.label}",
                    kind = ExploreMode.Kind.RANKING,
                    rankingOption = opt.key,
                )
            }
        }
        return modes.distinctBy { it.id }
    }

    private fun CategoryPart.toNativeSection(): NativeSection {
        val mapped = items.map {
            NativeEntry(
                label = it.label,
                param = it.target.attributes?.get("param")?.toString(),
                // page / keyword 必须原样带出来：只看 label 与 param 无法区分 category 型与 search 型
                // （search 型的 param 恒为 null），会把后者的请求发到错误的端点。
                page = it.target.page,
                keyword = it.target.attributes?.get("keyword")?.toString(),
            )
        }
        return NativeSection(
            name = name,
            type = type,
            items = if (type == TYPE_RANDOM) randomWindow(mapped, randomNumber.coerceAtLeast(1)) else mapped,
        )
    }

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

    /** 合成排行榜分区的名字，与源脚本无关，因此不受「原样保留源声明分区名」那条契约约束。 */
    const val RANKING_SECTION_NAME = "排行榜"

    /** [NativeSection.type] 里给合成榜组用的标记值。 */
    const val TYPE_RANKING = "ranking"

    private const val TYPE_RANDOM = "random"

    /**
     * `type == "random"` 分区的随机窗口，**逐字对齐官方** `category.dart:82-86`：
     * 窗口不小于全组就整组原样，否则起点在 `[0, size-n)` 里摇（官方 Dart 的
     * `nextInt(max)` 同样上界开区间，所以最后一枚永远不会作为窗口尾出现，这不是我们的偏差）。
     *
     * 摇的时机是**每次重建 SourceExploration**（冷启动 / 手动刷新），不是每次读数 ——
     * 官方在 getter 里摇，而我们的组合会随导航条目重建（见导航条目重建那条既有结论），
     * 照抄那一条就变成"标签自己在页面上乱变"。
     */
    internal fun randomWindow(
        items: List<NativeEntry>,
        size: Int,
        random: Random = Random.Default,
    ): List<NativeEntry> {
        if (size >= items.size) return items
        val start = random.nextInt(items.size - size)
        return items.subList(start, start + size)
    }
}
