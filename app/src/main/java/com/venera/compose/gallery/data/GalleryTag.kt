package com.venera.compose.gallery.data

import kotlinx.serialization.Serializable

/**
 * 补全一次向站方要多少条**候选**（复检 + 截断之前）。
 *
 * 复检会吃掉不少：站方按前缀给，而用户输入的往往是词根（`hime` 前缀在两站都能命中几十条），
 * 20 条按张数降序足够让常用标签排在前面，又不至于把整段词表拖进内存。
 */
const val GALLERY_TAG_SUGGESTION_LIMIT = 20

/**
 * 一条**标签补全**候选（搜索页输入时下拉那些行）。
 *
 * 两站的 tag 端点形状不同但语义同源（全部实测，见 `gallery-search-2026-09.md` §〇）：
 * Gelbooru 是 `s=tag` 的 `name / count / type / ambiguous`，
 * yande.re 是 `/tag.json` 的 `name / count / type`。
 * 数字命名空间**是同一套**（0 通用 / 1 画师 / 3 作品 / 4 角色 / 5 元数据），
 * 所以档位标签共用下面那张表，不各造一份。
 *
 * ⚠️ 与 [GalleryPost.tagGroups] 不是一回事：那边是"这张画带了哪些分类的 tag"，
 * 而两站的 **post 里都没有**分类字段（yande.re 44 个键实测不含 `tag_string_*`；
 * Gelbooru 只有一串平铺 `tags`），只能一桶；
 * 但它们的 **tag 端点有** `type` —— 所以补全行两站都能标档位，别反过来推断"post 也没有分类"。
 */
data class GalleryTagSuggestion(
    val site: GallerySite,
    val name: String,
    /** 站方给的该标签张数。**不是**本次查询的命中数 —— 两站都没有总数端点（实测 404）。 */
    val count: Int,
    val category: Int,
    val deprecated: Boolean,
) {
    val categoryLabel: String get() = galleryTagCategoryLabel(category)
}

/**
 * 数字档 → 中文档位名。取不到就回"其他"，**不回空串**：
 * 空串在列表里会长成"这行没有档位"，而实际是站方给了个我们没记录的编号。
 */
fun galleryTagCategoryLabel(category: Int): String = when (category) {
    0 -> "通用"
    1 -> "画师"
    2 -> "未知"
    3 -> "作品"
    4 -> "角色"
    5 -> "元数据"
    else -> "其他"
}

/**
 * Gelbooru `s=tag&q=index&json=1` 的应答元素（2026-09-26 实测，原文）：
 *
 * ```json
 * {"id":126,"name":"touhou","count":1024947,"type":3,"ambiguous":0}
 * ```
 *
 * ⚠️ 三条与 yande.re **不**同名的坑：
 * - 张数字段叫 **`count`**（不是 Danbooru 的 `post_count`）；
 * - 分类字段叫 **`type`**（不是 `category`），数值命名空间两站一致；
 * - **没有 `is_deprecated`** —— 一律 false，不要拿 `ambiguous` 去顶替它：
 *   那个字段的意思是"这枚标签有歧义"，与"已废弃"是两件事。
 */
@Serializable
internal data class GelbooruTagDto(
    val id: Long = 0,
    val name: String = "",
    val count: Int = 0,
    val type: Int = 0,
    val ambiguous: Int = 0,
) {
    fun toSuggestion(): GalleryTagSuggestion =
        GalleryTagSuggestion(GallerySite.GELBOORU, name, count, type, deprecated = false)
}

@Serializable
internal data class YandeReTagDto(
    val id: Long = 0,
    val name: String = "",
    val count: Int = 0,
    val type: Int = 0,
) {
    /** 本站没有 `is_deprecated` 这个键 —— 一律 false，**不要**拿别的字段猜一个填上。 */
    fun toSuggestion(): GalleryTagSuggestion =
        GalleryTagSuggestion(GallerySite.YANDERE, name, count, type, deprecated = false)
}

/**
 * 补全结果的**客户端复检**（这一条是整个搜索功能里最容易静默出错的地方）。
 *
 * 两站的"前缀匹配"参数都**可能被静默忽略**，而且忽略之后回的还是 200 + 一个像样的数组：
 * - Gelbooru：`s=tag` 那条的 `name_pattern` 是 **SQL LIKE 语法**（官方 wiki：`%` 是多字符通配、
 *   `_` 是单字符通配），所以前缀匹配要写成 `词%`；
 * - yande.re：`name=hime*` 才是前缀匹配；`search[name]=hime` 被忽略，
 *   回了一条 `name:""` 的空标签加一串不相干的东西；`name=~hime*` 又回 **空数组**。
 *
 * 也就是说：**站方给错数据时不会有任何信号**。所以拿回来的每一条都按原始输入复检一次前缀，
 * 对不上的直接丢 —— 宁可少给几条，也不能把不相干的标签摆成"你要搜的"。
 * 空名（站方真有 `name:""` 这种脏数据）一并滤掉。
 *
 * 排序：**未废弃的在前**，同组内按站方给的张数降序（`orderby=count` 已经这么排了，
 * 这里重排是为了站方偶尔不认 `orderby` 时表现一致），最后按名字兜平。
 * 废弃标签**不删**：它照样能搜出东西，删掉等于替用户决定"这个你别想搜"。
 */
fun refineGalleryTagSuggestions(term: String, raw: List<GalleryTagSuggestion>, limit: Int): List<GalleryTagSuggestion> {
    val needle = term.trim().lowercase().replace(' ', '_')
    return raw.asSequence()
        .filter { it.name.isNotBlank() }
        .filter { needle.isEmpty() || it.name.lowercase().startsWith(needle) }
        .sortedWith(compareBy<GalleryTagSuggestion> { it.deprecated }.thenByDescending { it.count }.thenBy { it.name })
        .take(limit.coerceAtLeast(0))
        .toList()
}
