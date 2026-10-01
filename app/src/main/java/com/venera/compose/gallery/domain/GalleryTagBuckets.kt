package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryTagGroup
import com.venera.compose.gallery.data.galleryTagCategoryLabel

/**
 * 画廊标签的**分桶**判据与站方 HTML 分类的解析。
 *
 * 为什么判据要单独成文件、而且是纯函数：两站的 post JSON **都不给分类**
 * （实测 yande.re 44 个键里没有、Gelbooru 只有一串平铺 `tags`，见
 * `gallery-tag-category-translation-and-nav-2026-09.md` §0.3），
 * 但两站的 **post HTML 条目页**把每枚标签标了 `tag-type-*` 类名 —— 那是站方对"这张画"的判定，
 * 不是我们按名字猜。取回来之后怎么分桶、分不出来怎么降级，全在这里，网络与缓存都在外面。
 */
internal object GalleryTagCategory {
    /** 编号与 [galleryTagCategoryLabel] / `GalleryTagSuggestion.category` **同一套**，不另造。 */
    const val GENERAL = 0
    const val ARTIST = 1
    const val COPYRIGHT = 3
    const val CHARACTER = 4
    const val METADATA = 5

    /**
     * 类名 → 档位。
     *
     * 只收这五个：实测两站网页就出这五种 `tag-type-*`。
     * 认不出的类名**不当成通用**（站方新增一档时我们要的是"这一枚没判定"，
     * 而不是把它塞进一个语义已经定了的桶里）。
     */
    fun fromClassName(name: String): Int? = when (name.lowercase()) {
        "general" -> GENERAL
        "artist" -> ARTIST
        "copyright" -> COPYRIGHT
        "character" -> CHARACTER
        "metadata" -> METADATA
        else -> null
    }
}

/**
 * 从一张帖子的 HTML 里读「标签 → 站方给的档位」。
 *
 * 读的是**同一条目里那条 `…tags=<名字>` 链接的参数值**，不是锚文本 —— 实测（方案 §0.7）：
 * yande.re 的锚文本是给人看的空格形式（`no bra`），而参数值才是标识符原形（`no_bra`），
 * 且参数值与 API 那串 `tags` **逐字相同**（3/3 帖全对，锚文本↔参数值 10/10 只差空格）。
 * 取锚文本就要做"空格转下划线"的还原，而那一步在有 `+`、`!`、重名变体的标签上并不安全。
 *
 * 形状（两站实测，只差中间那层 `<span>`/wiki 链接）：
 * ```
 * <li class="tag-type-artist"><a href="/artist/show?name=gijang">?</a> <a href="/post?tags=gijang">gijang</a>
 * <li class="tag-type-character"><span class="sm-hidden"><a href="…search=imai_lisa">?</a> </span><a href="…&tags=imai_lisa">imai lisa</a>
 * ```
 * 所以模式是「类名 → 该条目内的第一条 `tags=`」：锚文本先出现的 wiki 链接带的是 `search=`/`name=`，
 * 不会被误吃；`&amp;` 先还原成 `&` 才能匹配到 Gelbooru 那一路。
 *
 * ⚠️ 按 `</li>` **切段**再找，不用一条正则跨段匹配：某一枚如果自己没有帖子链接
 * （站方给未知标签渲染的是别的形状），跨段匹配会把**下一枚**的名字算到这一档上 ——
 * 那是"分错桶"，比"这一枚没分桶"坏得多，而且屏上看不出来。
 */
internal fun parseGalleryTagCategories(html: String): Map<String, Int> {
    if (html.isEmpty()) return emptyMap()
    val decoded = html.replace("&amp;", "&")
    val out = LinkedHashMap<String, Int>()
    var cursor = 0
    while (true) {
        val open = decoded.indexOf(LI_TAG_TYPE_PREFIX, cursor)
        if (open < 0) break
        val classStart = open + LI_TAG_TYPE_PREFIX.length
        val classEnd = decoded.indexOf('"', classStart)
        if (classEnd < 0) break
        // 段尾：这一条 `<li>` 到它自己的 `</li>`；站方漏了闭合标签时只读到文末，
        // 至多少收几枚，不会跨条错配（游标仍然前进，不会原地打转）。
        val close = decoded.indexOf(LI_END, classEnd)
        val segmentEnd = if (close < 0) decoded.length else close
        val category = GalleryTagCategory.fromClassName(decoded.substring(classStart, classEnd))
        if (category != null) {
            val name = TAG_PARAM_PATTERN.find(decoded.substring(classEnd, segmentEnd))
                ?.groupValues?.get(1)
                ?.let { decodePercent(it).lowercase() }
                ?.takeIf { it.isNotEmpty() }
            // 同名只出现一次时以首次判定为准：站方不会给同一枚标签两个档位，
            // 真出现就是页面结构变了，静默保留第一条比后面覆盖前面更容易被发现。
            if (name != null) out.putIfAbsent(name, category)
        }
        cursor = if (close < 0) decoded.length else close + LI_END.length
    }
    return out
}

private const val LI_TAG_TYPE_PREFIX = "<li class=\"tag-type-"
private const val LI_END = "</li>"

// `tags=` 之后到第一个引号 / `&` / 空白为止：地址后面紧跟 `">`，
// 而标签名里合法的非 ASCII 只有 percent-encode 过的字节。
private val TAG_PARAM_PATTERN = Regex("[?&]tags=([^\\s\"'&<>]+)")

/**
 * 只做 `%XX` 解码，**不动 `+`**。
 *
 * `java.net.URLDecoder` 会把 `+` 解成空格，而画廊里真有带 `+` 的标签（`c+union` 这类），
 * 一旦解成空格就和 API 那串对不上了 —— 对不上的表现是"这一枚没分桶"，
 * 属于那种看不出来的错。这里手写，只处理百分号。
 */
private fun decodePercent(value: String): String {
    if (!value.contains('%')) return value
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hi = Character.digit(value[i + 1], 16)
            val lo = Character.digit(value[i + 2], 16)
            if (hi >= 0 && lo >= 0) {
                bytes.add(((hi shl 4) + lo).toByte())
                i += 3
                continue
            }
        }
        // 非 ASCII 的裸字节在 URL 里本就该被编码；真出现在站方 HTML 里就当字面字符收下，
        // 至多这一枚对不上键，不影响其它标签。
        bytes.add(c.code.toByte())
        i++
    }
    return String(bytes.toByteArray(), Charsets.UTF_8)
}

/**
 * 把一串标签按档位分桶，给「关于这张图」那面板用。
 *
 * **两条来路，优先级写死**：
 * 1. [categories]（站方 HTML 的判定）非空 → 完全按它分；查不到的那一枚进「标签」桶，
 *    **绝不塞进「通用」** —— 「通用」是站方明说的档位，我们冒充不了。
 * 2. [categories] 为空/为 null（取失败、站方改版认不出类名、深链进来还没取到）→
 *    只兜**画师**这一栏：[fallbackArtistNames] 是离线词典里 `category=1` 的那几个名字。
 *    其余一律留在「标签」桶，不摆其它桶名。
 *
 * 为什么兜底只兜画师、不兜齐全五桶（方案 §1.4）：那份词典是 Danbooru 的词条表，
 * 对 Gelbooru 有 98% 的名称覆盖、对 yande.re 只有 73%（`pantsu`、`seifuku` 这类
 * Danbooru 已删/改名而本站仍活着的词它没有）。拿它补满五桶，就是**把两三成标签挂进错桶**；
 * 而实测它"判成画师而站方不判画师"的**误报是 0 例**（Gelbooru 253 枚 / yande.re 56 枚真值），
 * 漏的方向只是"这一栏不摆"。宁可漏，不可错。
 *
 * 桶序固定「画师 → 角色 → 作品 → 通用 → 元数据 → 标签」：人最常找的是画师，
 * 而「标签」这一桶语义最弱（"没判定过的"），排最后。空桶不产出 —— 摆一个空的"角色"区比不摆更糟。
 */
internal fun buildGalleryTagBuckets(
    tagList: List<String>,
    categories: Map<String, Int>?,
    fallbackArtistNames: Set<String> = emptySet(),
): List<GalleryTagGroup> {
    if (tagList.isEmpty()) return emptyList()
    val fromSite = categories.orEmpty()
    if (fromSite.isEmpty()) {
        val artists = tagList.filter { it.lowercase() in fallbackArtistNames }
        val rest = tagList.filterNot { it.lowercase() in fallbackArtistNames }
        // ⚠️ 兜底这一路**不给 category**：`fallbackArtistNames` 是离线词典判的，
        // 不是站方给的档位。屏上按档位上色时它会退到中性色 —— 那是刻意的，
        // 一桶"我们猜它是画师"不该长得跟"站方说它是画师"一模一样。
        return listOfNotNull(
            artists.takeIf { it.isNotEmpty() }
                ?.let { GalleryTagGroup(galleryTagCategoryLabel(GalleryTagCategory.ARTIST), it) },
            rest.takeIf { it.isNotEmpty() }
                ?.let { GalleryTagGroup(UnresolvedBucketLabel, it) },
        )
    }
    val byCategory = LinkedHashMap<Int, MutableList<String>>()
    val unresolved = mutableListOf<String>()
    for (tag in tagList) {
        val category = fromSite[tag.lowercase()]
        if (category == null) unresolved.add(tag) else byCategory
            .getOrPut(category) { mutableListOf() }
            .add(tag)
    }
    val ordered = BucketOrder.mapNotNull { category ->
        byCategory[category]?.takeIf { it.isNotEmpty() }
            ?.let { GalleryTagGroup(galleryTagCategoryLabel(category), it.toList(), category) }
    }
    val tail = unresolved.takeIf { it.isNotEmpty() }
        ?.let { GalleryTagGroup(UnresolvedBucketLabel, it) }
    return if (tail == null) ordered else ordered + tail
}

/**
 * 这一串标签里，**能当「这位画师」摆出来的那几个** —— [buildGalleryTagBuckets] 那一桶的展开。
 *
 * 存在的理由只有一个：这条判据现在有**两个**读者，而它们必须逐字同解。
 * 1. 「关于这张图」面板：把这一桶摆成画师卡（头像 + 名字 + 平台入口 + 关注）；
 * 2. 大图页：用户一打开这一页，就按这几个名字**先把外链与头像取回来**
 *    （2026-10-01 用户点名："不要点信息才加载"）。
 *
 * 第二个读者是 2026-10-01 才出现的 —— 在那之前"哪几个是画师"只写在面板那一处。
 * 两处各写一遍的后果不是编译错，而是**取数的名字与摆出来的名字对不上**：
 * 屏上留着首字母座、而后台刚取回来的那份挂在另一个名字下，谁都看不出来。
 */
internal fun galleryArtistNames(
    tagList: List<String>,
    categories: Map<String, Int>?,
    fallbackArtistNames: Set<String> = emptySet(),
): List<String> = buildGalleryTagBuckets(tagList, categories, fallbackArtistNames)
    .firstOrNull { it.label == galleryTagCategoryLabel(GalleryTagCategory.ARTIST) }
    ?.tags
    .orEmpty()

/** 站方判定过的那几档，按人最常看的在前。 */
private val BucketOrder = listOf(
    GalleryTagCategory.ARTIST,
    GalleryTagCategory.CHARACTER,
    GalleryTagCategory.COPYRIGHT,
    GalleryTagCategory.GENERAL,
    GalleryTagCategory.METADATA,
)

/**
 * 「站方没判定过」那一桶的名字，沿用今天屏上已有的**「标签」**。
 *
 * 刻意不叫「未知」：`galleryTagCategoryLabel(2)` 那句"未知"说的是"站方给了个我们没记录的编号"，
 * 而这一桶是"站方对这枚没有分类"—— 两者不是一回事，摆成"未知 12"会让人以为读出了什么。
 * 也不能并进「通用」：「通用」是站方明确的一档（实测 yande.re 的 `no_bra` 就是 `type=0`），
 * 我们冒充不了。
 */
internal const val UnresolvedBucketLabel = "标签"
