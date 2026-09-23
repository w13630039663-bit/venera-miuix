package com.venera.compose.source.model

/**
 * 通用漫画精简元数据（用于列表展示、搜索结果、收藏条目等）
 */
data class Comic(
    val id: String,
    val title: String,
    val subTitle: String = "",
    val cover: String = "",
    val sourceKey: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val updateTime: String = "",
    val isFavorite: Boolean = false,
    val rating: Float? = null,
    val likesCount: Int? = null
)

/**
 * 漫画单话/单卷元数据
 */
data class ComicChapter(
    val id: String,
    val title: String,
    val order: Int = 0,
    val updatedAt: String = "",
    val group: String = "默认",
    val isRead: Boolean = false
)

/**
 * 搜索筛选组（S8 批次B，对齐官方 SearchOptions）：
 * 源 search.optionList 声明的单组筛选（如排序/语言/分类）。
 *
 * @param label 组标题（空串则不显示标题行）
 * @param options LinkedHashMap：key 传给源，value 为显示名；首项为默认值
 */
data class SearchOptionGroup(
    val label: String = "",
    val options: LinkedHashMap<String, String> = LinkedHashMap(),
    val type: String = "select",
    val defaultValue: String? = null
) {
    /** 多选按协议传 JSON 数组；下拉允许不选，不能偷换成第一项。 */
    val defaultKey: String?
        get() = defaultValue ?: when (type) {
            "multi-select" -> "[]"
            "dropdown" -> null
            else -> options.keys.firstOrNull().orEmpty()
        }
}

/**
 * 章节分组（多卷、连载中、单行本、番外篇等）
 */
data class ChapterGroup(
    val name: String,
    val chapters: List<ComicChapter>
)

/**
 * 漫画详情页评论实体（对齐 venera-init.js 中的 Comment 构造函数）
 */
data class CommentCapabilities(val canLoad: Boolean = false, val canSend: Boolean = false)

data class CommentPage(val comments: List<Comment>, val maxPage: Int? = null)

data class Comment(
    val id: String = "",
    val userName: String,
    val avatar: String? = null,
    val content: String,
    val time: String? = null,
    val replyCount: Int? = null,
    val isLiked: Boolean = false,
    val score: Int = 0,
    val voteStatus: Int = 0 // 1: upvote, -1: downvote, 0: none
)

/**
 * 漫画完整详情数据（29 字段全量覆盖对齐原版）
 */
data class ComicDetails(
    val comic: Comic,
    val author: String = "",
    /** 作品状态；源未提供时为空串（UI 不显示该标签，绝不臆造"连载中"）。 */
    val status: String = "",
    val rating: Float = 0f,
    val chapters: List<ComicChapter> = emptyList(),
    val chapterGroups: List<ChapterGroup> = emptyList(),
    val thumbnails: List<String> = emptyList(),
    val recommend: List<Comic> = emptyList(),
    val tagMap: Map<String, List<String>> = emptyMap(), // 带有命名空间的标签（如 "作者" -> ["..."], "题材" -> ["..."]）
    val uploader: String = "",
    val uploadTime: String = "",
    val updateTime: String = comic.updateTime,
    val url: String = "",
    val stars: Float = rating,
    val maxPage: Int = 1,
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val isFavorite: Boolean = comic.isFavorite,
    val commentCount: Int = 0,
    val comments: List<Comment> = emptyList(),
    val subId: String? = null,
    val sourceKey: String = comic.sourceKey
) {
    /**
     * 打平成 `namespace:tag` 的源生标签（对齐 master 分支 `Comic.plainTags`）。
     *
     * 为什么不能用 [comic] 的 tags：`JsComicSource` 解析源返回的 Map 形态 tags 时交出来的是
     * `tagMap.values.flatten()` —— **命名空间在那一步就丢了**。而题材归一化的第一级判据
     * 正是命名空间（作者/状态/分类词不参与统计），且管线按原口径**丢弃所有不带 `:` 的裸标签**，
     * 所以拿扁平的 tags 去统计会得到「一个桶都没有」。
     *
     * 值里已经自带命名空间（`hitomi` 会写 `"f:" + 标签`、`ehentai` 列表项是 `female:xxx`）时
     * 不再重复加前缀，否则会拼出 `标签:female:lolicon` 这种两头都不像的键。
     */
    val plainTags: List<String>
        get() = tagMap.flatMap { (namespace, values) ->
            values.map { value -> if (value.contains(':')) value else "$namespace:$value" }
        }.distinct()
}

/**
 * 一页搜索结果。
 *
 * 之所以不直接返回 `List<Comic>`：搜索页要告诉用户「已加载多少 / 大约还有多少」，
 * 而这件事只有源自己知道。
 *
 * [maxPage] **只在源自己声明时非空**。实测 33 个源：26 个返回 `maxPage`，
 * 7 个（含 ehentai / nhentai / lanraragi）连它都不给；游标型（`loadNext`）源在协议上
 * 就没有「总页数」这个概念，只有「还有下一页吗」。
 *
 * 刻意**没有** total 字段：33 个源里 0 个返回条目总数（jm 内部有 `data.total`，
 * 但它的 `search.load` 只往外返回 `maxPage`）。所以条目总数只能估算，不能当事实显示。
 */
data class SearchPage(
    val comics: List<Comic> = emptyList(),
    val maxPage: Int? = null,
)

/**
 * 章节全量漫画图片列表（用于阅读器）
 */
data class ChapterPages(
    val comicId: String,
    val chapterId: String,
    val chapterTitle: String = "",
    val pages: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    /**
     * [pages] 是否为「图片键」而非最终 URL。
     *
     * 对齐官方 `GetImageLoadingConfigFunc` 语义（parser.dart:1069）：源声明了
     * `comic.onImageLoad` 时，`loadEp` 返回的每一项都必须经
     * `onImageLoad(imageKey, comicId, epId)` 解析成真实 `{url, headers}` 才能展示
     * —— EH 图库的 loadEp 返回页码、jm/nhentai 等则做 URL 重写或加签，皆属此类。
     */
    val useOnImageLoad: Boolean = false
)

/**
 * `comic.onImageLoad` 对单个图片键的解析结果（对齐官方 `network/images.dart` 的 configs）。
 *
 * [nl] 是 EH 图库分发 API 的换源参数：下载失败时带上它重新解析，
 * 等价于官方调用源脚本返回的 `onLoadFailed` 闭包（我们跨不出 JS 闭包，改为显式传参）。
 */
data class ResolvedImageConfig(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val nl: String? = null,
    val modifyImage: String? = null
)

/**
 * 源 `comic.loadThumbnails(id, next)` 的一页返回值。
 *
 * 对齐官方 `parser.dart:_parseThumbnailLoader` —— 该接口是**分页**的：
 * 返回本页缩略图与下一页 token（官方 `Res(data, subData: res['next'])`），
 * `next == null` 表示没有更多。EH 用它抓 gallery 页面里的官方预览小图，
 * 一次请求拿一页 HTML 里的全部缩略图，比逐页请求大图快得多。
 */
data class ThumbnailPage(
    val thumbnails: List<String> = emptyList(),
    /** 下一页 token；null 表示已到末页。 */
    val next: String? = null
)

/**
 * 源 `comic.onThumbnailLoad(url)` 对单个缩略图的解析结果。
 *
 * ⚠️ 别把它当成"换成小图"的钩子：实测 34 个内置源里 14 个实现了它，
 * **没有一个是把页面地址换成低分辨率变体**，全部是"原 url 原样回 + 加防盗头"
 * （唯一的例外是 EH，把 `s.exhentai.org` 换成同分辨率镜像域 `ehgt.org`）。
 * 它就是缩略图版的 [ResolvedImageConfig]，作用是让缩略图请求带上源要求的
 * referer/UA，以及 nhentai 那类地址修正。协议原文里 `modifyImage` 与
 * `onLoadFailed` 被明确忽略，所以这里也不带那两项。
 */
data class ResolvedThumbnailConfig(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * 探索页面元数据（对齐原版 ExplorePageData）
 */
data class ExplorePageData(
    val title: String,
    val type: String = "multiPageComicList",
    val sourceKey: String,
    val sourceName: String,
    val pageIndex: Int = 0
)

/**
 * 探索页面的具体分区（对齐原版 ExplorePagePart，如 "推荐", "热门", "今日排行" 等）
 */
data class ExplorePagePart(
    val title: String,
    val comics: List<Comic>,
    val viewMore: PageJumpTarget? = null
)

/**
 * 页面跳转目标（对齐原版 PageJumpTarget）
 */
data class PageJumpTarget(
    val sourceKey: String,
    val page: String, // "search", "category"
    val attributes: Map<String, Any?>? = null
)

/**
 * 分类页面元数据（对齐原版 CategoryData）
 */
data class CategoryData(
    val title: String,
    val key: String,
    val enableRankingPage: Boolean = false,
    val parts: List<CategoryPart> = emptyList(),
    val buttons: List<CategoryButtonData> = emptyList(),
    val sourceKey: String = ""
)

data class CategoryButtonData(
    val label: String,
    val action: String = ""
)

data class CategoryPart(
    val name: String,
    val type: String = "fixed", // "fixed", "random", "dynamic"
    val items: List<CategoryItem> = emptyList()
)

data class CategoryItem(
    val label: String,
    val target: PageJumpTarget
)

data class CategoryComicsOption(
    val label: String,
    val options: Map<String, String>, // value -> label
    val notShowWhen: List<String> = emptyList(),
    val showWhen: List<String>? = null
)

data class CategoryComicsResult(
    val comics: List<Comic>,
    val maxPage: Int? = null,
    val next: String? = null
)
