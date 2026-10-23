package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GallerySite

/** 一枚已选标签：名字 + 是不是排除项（站方语法就是在前面加 `-`）。 */
data class GalleryTagFilter(val name: String, val excluded: Boolean = false) {
    val token: String get() = if (excluded) "-$name" else name

    companion object {
        /** 从站方语法串还原（历史条目、一次粘贴多个标签都走这里）。空名返回 null。 */
        fun of(token: String): GalleryTagFilter? {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return null
            if (!trimmed.startsWith("-")) return GalleryTagFilter(trimmed)
            val name = trimmed.removePrefix("-").trim()
            return name.takeIf { it.isNotEmpty() }?.let { GalleryTagFilter(it, excluded = true) }
        }
    }
}

/**
 * 交接标签名的**清洗**（2026-10-23，用户「去掉多余前缀与非法符号」）。
 *
 * 为什么需要这一层：交接槽的写入方有三类，没有一类能保证送来的是干净词 ——
 * 1. 大图页信息面板里点的标签 chip：站方 `tags` 平铺串切出来的原串；
 * 2. 画师介绍页「看 TA 全部作品」交回的画师名：pixiv 用户名可以带 `@`、空格甚至表情；
 * 3. pixiv 作品页的标签 chip：站方译名只影响显示，检索与屏蔽一律用原串。
 *
 * 脏词进去的代价不是"不好看"：booru 的 `tags=` 按空格分词，一枚带空格的"标签"会被站方
 * 当成两枚；前导 `-` 会被 [GalleryTagFilter.token] 拼成排除项（搜到的是"除了它"）；
 * 引号 / 尖括号会捅穿拼 URL 那一步。所以在这里一次性收口，**两个域都受益**
 * （pixiv 那一侧 `pixivTerm` 是整词发送，同样怕这些符号）。
 *
 * 洗什么、**不洗什么**（这份清单是判据，不是口味）：
 * - 洗：首尾空白与不可见字符（NBSP / 零宽空格 / BOM / 全角空格）、成串的前导符号
 *   （`-` 排除项 / `@` 画师 handle / `#` 话题 / `+` `~` 垃圾与 OR 前缀）、
 *   控制字符、引号 / 尖括号 / 反斜杠 / 竖线，以及被它们撑出来的连续空白；
 * - **不洗**内部空格与 `/` `_` `:` `(` `)` `'` —— pixiv 标签名里它们都合法
 *   （"Fate/Grand Order" 就是带空格和斜杠的），洗了就是把能搜到的词改成搜不到。
 *
 * 返回空串 = 整枚都是符号、没有可搜的词（调用方据此不写交接槽）。
 */
fun sanitizeGalleryTagName(raw: String): String {
    // ① 控制字符与不可见字符一律换空白：它们进 URL 必炸，而在屏上什么也不显示。
    //    引号 / 尖括号 / 反斜杠 / 竖线同样换空白 —— 查询语法与文件名里的非法符号。
    //    其余原样保留（含 `/` `_` `:` 这些在一个域里合法、在另一个域里是语法的字符）。
    val mapped = buildString(raw.length) {
        for (ch in raw) {
            when {
                ch.isISOControl() -> append(' ')
                ch == '\u00A0' || ch == '\u200B' || ch == '\uFEFF' -> append(' ')
                ch == '"' || ch == '<' || ch == '>' || ch == '\\' || ch == '|' -> append(' ')
                else -> append(ch)
            }
        }
    }
    // ② 前导符号**成串**去掉（连同空白）：`-` 会被拼成排除项、`@` 是画师 handle、
    //    `#` 是话题写法、`+` `~` 是垃圾前缀（`~` 还是 booru 的 OR）。
    val body = mapped.trimStart { ch ->
        ch == '-' || ch == '@' || ch == '#' || ch == '+' || ch == '~' || ch.isWhitespace()
    }
    // ③ 连续空白压成一个（全角空格也算空白），首尾再由 split/join 收干净。
    return body.split(' ', '\t', '\n', '\r', '\u000B', '\u000C', '\u3000')
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}

/**
 * 一条搜索历史：**在哪些站搜过** + 那串标签。
 *
 * 为什么带站点：两站词表不通，同一串标签换个站可能就是另一个结果。
 * 为什么从"一个站"改成"一组站"：来源轴上了「全部」之后，一次搜索本来就同时打两站，
 * 按 `(站, 词)` 存会让同一个词占两行 —— 历史区立刻被自己的重复撑满（真机就是这个形状）。
 *
 * [sites] 恒按站表序归一：写盘字节要稳定，`joinToString` 与 diff 才不会跟着集合实现飘。
 */
class GallerySearchEntry(sites: Collection<GallerySite>, val query: String) {

    val sites: Set<GallerySite> = GallerySite.entries.filter { it in sites }.toCollection(LinkedHashSet())

    init {
        require(this.sites.isNotEmpty()) { "一条历史至少要有一个站" }
    }

    /** 单站那一档（含旧的 `(站, 词)` 写法）走这条路。 */
    constructor(site: GallerySite, query: String) : this(listOf(site), query)

    /** 站表序里的第一个站。只给"这一条从哪个站续上"这类派生读法用，别拿它当身份。 */
    val site: GallerySite get() = sites.first()

    val filters: List<GalleryTagFilter> get() = GallerySearch.parseQuery(query)

    /** 去重键：与书写顺序无关，但排除项算另一条（见 [GallerySearch.canonicalKeyOf]）。 */
    val canonicalKey: String get() = GallerySearch.canonicalKeyOf(query)

    override fun equals(other: Any?): Boolean =
        other is GallerySearchEntry && other.sites == sites && other.query == query

    override fun hashCode(): Int = 31 * sites.hashCode() + query.hashCode()

    override fun toString(): String = "GallerySearchEntry(${sites.joinToString("+") { it.routeKey }},$query)"
}

/**
 * 画廊搜索的判据层 —— 全是纯函数，为的是能上单测（本项目单元测试没有 Robolectric）。
 * 判据来自实测（全表见 `gallery-search-2026-09.md`），不是照另一站推断的。
 */
object GallerySearch {

    /**
     * 本站允许的标签数；`null` = 不限。
     *
     * ⚠️ **两站现在都是不限**，但这条判据本身留着 —— 它是"发请求之前先拦住"的唯一落点，
     * 而预算这件事是**会变的**：换回一个有限额的站、或站方哪天收紧，只要在这里返回一个数，
     * UI 就会自动在**加标签那一刻**拦住并报原因，不必再改一遍调用点。
     *
     * Gelbooru 这一档的依据：官方 wiki `howto:api` 与 `howto:cheatsheet` 通篇
     * **没有任何标签数上限**的说明（`limit` 那一项限的是"一次取回多少条"，
     * 不是"能写几枚标签"），实测的 `tag1 {tag2 ~ tag3}` 这类多标签组合也照常工作。
     * 与 Danbooru 那个"匿名第 3 枚直接 422"是完全不同的形态。
     */
    fun tagBudget(site: GallerySite): Int? = when (site) {
        GallerySite.GELBOORU -> null
        GallerySite.YANDERE -> null
        // Safebooru 与 Danbooru 同一内核（官方全年龄镜像，匿名访问）—— 匿名档
        // `tags=` 上限 2 枚，超了站方直接 422。与 Danbooru 账号那套
        // （匿名/Restricted/Member=2，见 GallerySearchViewModel 的 tagsPerSearch）同源。
        GallerySite.SAFEBOORU -> 2
        // pixiv 不进来源轴（2026-10-16 选型⑤：首页那一格是**内容域**切换，不是第五枚来源 chip），
        // 它的入口是榜单档名，不是 booru 那套 `tags=` 语法。
        // ⚠️ 别把这句读成"pixiv 匿名搜不到"：`ajax/search/artworks/{词}` 现量 200 / 每页 60 条，
        // 那是首页「最新作品」那一腿要用的，跟来源轴无关（来源轴并进去就是两套语法一张墙，假读数）。
        GallerySite.PIXIV -> GallerySite.notWired("标签预算")
    }

    /**
     * 再加一枚还装得下吗。**排除项也吃预算** —— 站方数的是 `tags=` 里空格分隔的段数，
     * 不在乎那一段前面有没有 `-`。
     */
    fun fitsTagBudget(site: GallerySite, afterAdding: Int): Boolean {
        val budget = tagBudget(site) ?: return true
        return afterAdding <= budget
    }

    /** 预算满了要说的那句话；两站现在都不限，恒回 null。UI 据此决定摆不摆提示。 */
    fun budgetNotice(site: GallerySite): String? {
        val budget = tagBudget(site) ?: return null
        return "${site.displayName} 一次最多 $budget 个标签（排除项也算在内）"
    }

    /** 拼成站方 `tags=` 那一段。 */
    fun queryOf(filters: List<GalleryTagFilter>): String = filters.joinToString(" ") { it.token }

    /** 把一串站方语法拆回 chips。 */
    fun parseQuery(query: String): List<GalleryTagFilter> =
        query.split(' ').mapNotNull { GalleryTagFilter.of(it) }

    /**
     * 历史的**去重键**：同一批标签算同一条，与书写顺序无关。
     *
     * 站方那串是 AND，先写哪个不影响结果集 —— 所以 `a b` 与 `b a` 合成一条是真合并，
     * 不是把哪一次搜错的结果盖上去。
     *
     * ⚠️ 排除项**必须**算不同的串（`a -b` 与 `a` 是两个结果集）：合成一条就是假读数 ——
     * 用户点开历史看到的会是他没搜过的那一次。[GalleryTagFilter.token] 带着前导 `-`，
     * 所以这里按 token 定序天然把排除项分开了，别改成按 name 定序。
     */
    fun canonicalKeyOf(query: String): String =
        parseQuery(query).map { it.token }.distinct().sorted().joinToString(" ")

    /**
     * 到底了没：这次给回的条数**少于**要的条数，就是站方没有下一批了。
     *
     * 不用"返回 0 条"判 —— 那要多空跑一页才知道到底，用户会先看到一次"没有更多"再看到页尾，
     * 与搜索页 §7 那条"到底了还显示加载更多"是同一类假读数，只是反着长。
     * 也不用总数 —— 两站都没有总数端点。
     */
    fun isExhausted(returned: Int, requestedLimit: Int): Boolean = returned < requestedLimit

    /** 历史最多存几条、空态摆几条 —— 与漫画搜索同口径（`feature/SearchViewModel.kt:440`）。 */
    const val HISTORY_MAX = 20
    const val HISTORY_SHOWN = 5

    /**
     * 盘上那枚历史键名：**按内容域各一份**（2026-10-18，用户点「分域各存一份」）。
     *
     * 为什么放在这一颗而不是各端自己写：Android 与桌面**读写的是同一个文件**
     * （`PreferenceKeys.PREFS_GALLERY_SEARCH`，桌面那颗 `desktopHistoryStore` 的注释自己就这么说），
     * 键名各定一份的话，一端改名另一端不会跟着 —— 那是"两端不同轨"那一族的老账。
     *
     * 桌面搜索只有图库站那一域（pixiv 那条它直接抛不支持），所以它用 `BOORU` 这一枚。
     */
    fun historyKeyOf(domain: GalleryHomeDomain): String = "history_${domain.name.lowercase()}"

    /** 旧的**混域**键：两域共用一份、同词会被并成一条。只读一次摊平，然后删掉。 */
    const val LEGACY_SHARED_HISTORY_KEY = "history"
}

/**
 * 历史编解码（纯函数，SharedPreferences 那一层只管存这个串）。
 *
 * **v2 格式**：首行是版本哨兵 `v2`，其后一条一行，行内 `routeKey,routeKey<Tab>标签串`。
 * 字段分隔用制表符而不是 `=`：标签串里可能出现 `=`（用户手打 `source=xxx` 这类），
 * 而 routeKey 是我们自己写的死串，绝不含制表符 —— 拆第一个制表符就不会把查询吃掉半截。
 *
 * **旧档不改写也要能用**：读的时候先嗅首行。没有哨兵就按 v1（`routeKey=标签串`，一行一站）逐行读，
 * 读完再按 canonical 键现场并一次 —— 于是 v1 里 `setmen` 那两行会自然长成一条带两个站的记录。
 * 版本只在**写回**时升，读侧永远兼容，不会出现"升级那天历史全空"。
 *
 * 认不出站点的那一行**丢掉而不是硬归某个站**：把一串 Gelbooru 标签拿去 yande.re 搜
 * 会得到一句"没有结果"，那是比少一条历史更坏的结果（宁可错慢不可静默交错）。
 */
private const val HISTORY_V2 = "v2"
private const val HISTORY_FIELD = '\t'

fun encodeGallerySearchHistory(entries: List<GallerySearchEntry>): String {
    val body = entries.take(GallerySearch.HISTORY_MAX).joinToString("\n") { entry ->
        val sites = entry.sites.joinToString(",") { it.routeKey }
        "$sites$HISTORY_FIELD${entry.query}"
    }
    return if (body.isEmpty()) HISTORY_V2 else "$HISTORY_V2\n$body"
}

fun decodeGallerySearchHistory(raw: String?): List<GallerySearchEntry> {
    val lines = raw.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return emptyList()
    val versioned = lines.first() == HISTORY_V2
    val body = if (versioned) lines.drop(1) else lines

    // 按 canonical 键合并：v1 的一行一站与 v2 的一行多站都走这同一条路，不留两份合并逻辑。
    val merged = LinkedHashMap<String, Pair<MutableSet<GallerySite>, String>>()
    for (line in body) {
        val parsed = parseHistoryLine(line, versioned) ?: continue
        val (sites, query) = parsed
        val key = GallerySearch.canonicalKeyOf(query)
        val hit = merged[key]
        if (hit == null) {
            merged[key] = LinkedHashSet<GallerySite>(sites) to query
        } else {
            hit.first += sites
        }
    }
    return merged.values.map { (sites, query) -> GallerySearchEntry(sites, query) }
}

private fun parseHistoryLine(line: String, versioned: Boolean): Pair<Set<GallerySite>, String>? {
    val (sitesPart, query) = if (versioned) {
        line.substringBefore(HISTORY_FIELD, "") to line.substringAfter(HISTORY_FIELD, "")
    } else {
        // v1 一行只有一个站；只在第一个 `=` 处拆，后面的 `=` 属于查询。
        line.substringBefore('=', "") to line.substringAfter('=', "")
    }
    if (query.isBlank()) return null
    val sites = sitesPart.split(',')
        .mapNotNull { GallerySite.fromRouteKey(it.trim()) }
        .toSet()
    return sites.takeIf { it.isNotEmpty() }?.let { it to query.trim() }
}

/**
 * 新条目排到最前。去重键是 **canonical 标签串**（不含站点）：
 * 同一批标签换个站搜、或者调个顺序搜，都算同一条历史，站点并进那一条里。
 */
fun pushGallerySearchHistory(
    current: List<GallerySearchEntry>,
    entry: GallerySearchEntry,
): List<GallerySearchEntry> {
    // 必须并**所有**同键的旧条目：解码之后的档里不该出现两条同键，但 push 不能假定输入干净 ——
    // 只并第一条就会把另一条的站点静默丢掉，那条历史看起来就"从没在那个站搜过"。
    val same = current.filter { it.canonicalKey == entry.canonicalKey }
    val sites = entry.sites + same.flatMap { it.sites }
    val rest = current.filterNot { it.canonicalKey == entry.canonicalKey }
    return (listOf(GallerySearchEntry(sites, entry.query)) + rest).take(GallerySearch.HISTORY_MAX)
}
