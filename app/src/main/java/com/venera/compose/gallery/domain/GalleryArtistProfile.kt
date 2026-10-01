package com.venera.compose.gallery.domain

/**
 * 画师介绍页的判据层（批次 Q+R · 批量 5）。
 *
 * 这里只放**能脱离 Android 单跑**的判断（`_probe/l0/run-judgment-tests.sh` 里挂着），
 * 取数与摆法都在 `gallery/ui/GalleryArtistProfileScreen.kt` 那边。
 *
 * ## 别名：两站给出的不是一样东西，所以只上一半
 *
 * 2026-10-01 的只读探针（`_probe/yande_re_alias_coverage.cjs`，读数同目录 `.txt`）量的就是
 * "这一档到底摆不摆得出别名表"：
 *
 * - **Gelbooru 腿（借 danbooru）有真供体**：`artists.json` 记录里就带着 `other_names`
 *   （实测样本 `_probe/hub/dan_setmen.json`：`name=setmen`、`other_names=["u_u","セトマン"]`），
 *   而且是**同一笔现有请求**里的另一个字段，零新端点。→ 上，走 [aliasReadout]。
 * - **yande.re 的别名列表不上**（读数决定性）：它的 `artist.json` 键表只有
 *   `id,name,alias_id,group_id,urls`（探针逐条打出来过），别名关系是"别的记录 `alias_id` 指回正名"，
 *   而那些记录**不保证落在同一个 `name=` 前缀批次**里。18 个横跨字母表的真名样本，
 *   同批凑得出别名表的 = **0/18**；结构天花板（别名名与正名同前缀）= **10/111 ≈ 9%**。
 *   → 这一档**整块不摆**，屏上也永远不出现"这位没有别名"。
 *   yande.re 那边能给的只有"站方记的正名是 X"那**一句**，走批次 J 已有的
 *   [GalleryArtistAlias] + `YandeReClient.resolveArtistAlias`，不在这里再造一份。
 *
 * 探针顺手量到的另一条站方事实，写在这里免得下轮再猜：`artist.json` 的 **`limit=` 也被静默忽略**
 * （`limit=1` 与 `limit=40` 都回 25 条，而 `page=` 是生效的）。按全名查时批次本来就短，不影响正确性，
 * 但**不要拿"批内条数"当分页预算用**。
 */
object GalleryArtistProfile {

    /** 别名最多摆这么几条，剩下的只说"还有 N 个"（屏上那一行不能被一长串假名占满）。 */
    const val MAX_ALIASES = 8

    /**
     * 站方给的别名清单 → 屏上那一行。
     *
     * 三条都是"宁可少摆"的方向：去自身（正名自己不算别名，大小写无关）、去空白、去重复
     * （站方 `other_names` 里同名异写是真会出现的）。超出 [MAX_ALIASES] 的计入 [hiddenCount]，
     * 由调用方决定说"还有 N 个"还是什么都不说 —— 这里不静默丢掉。
     *
     * 一条都不剩时回 `visible = false`：**那一行整块不出现**，而不是摆一个空的"别名："。
     */
    fun aliasReadout(otherNames: List<String>, canonicalName: String): AliasReadout {
        val seen = HashSet<String>()
        val kept = ArrayList<String>(MAX_ALIASES)
        var eligible = 0
        for (raw in otherNames) {
            val name = raw.trim()
            if (name.isEmpty()) continue
            if (name.equals(canonicalName.trim(), ignoreCase = true)) continue
            if (!seen.add(name.lowercase())) continue
            if (kept.size < MAX_ALIASES) kept.add(name)
            eligible++
        }
        return AliasReadout(kept, eligible - kept.size)
    }

    /** 别名的读数。[visible] 为假时**那一行不摆**（与"没取到"在屏上同形，区别只在日志）。 */
    data class AliasReadout(val names: List<String>, val hiddenCount: Int) {
        val visible: Boolean get() = names.isNotEmpty()
    }

    /**
     * 名字底下那一行。**两段不同出处的读数合成一行**（2026-10-01 用户报"感觉不太行"后改的：
     * 旧版正名一行、别名一行、溢出计数还挂在第二行尾，名字底下最多占三行）。
     *
     * 两段的措辞一个字没改 —— 它们是两种不同的读数，合并的只是排版：
     * - `站方记的正名是 X`：yande.re `artist.json` 里 `alias_id` 反指的指针（批次 J 的解析链）。
     * - `别名：A、B（另有 N 个）`：Gelbooru 供体 danbooru 记录的 `other_names`（批次 Q+R 量过覆盖率）。
     *
     * 都没取到就回 null ⇒ **整行不出现**，而不是摆一个空的"别名："——
     * 屏上永远不出现"这位没有别名"这种我们没资格断言的话（头注那条红线）。
     */
    fun aliasLine(canonicalName: String?, readout: AliasReadout): String? {
        val parts = ArrayList<String>(2)
        canonicalName?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add("站方记的正名是 $it") }
        if (readout.names.isNotEmpty()) {
            parts.add(
                "别名：" + readout.names.joinToString("、") +
                    if (readout.hiddenCount > 0) "（另有 ${readout.hiddenCount} 个）" else "",
            )
        }
        return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
    }

    /**
     * 介绍页那排链接：全平台、去重、按固定次序摆。
     *
     * 与 [GalleryArtistLinks.iconPlan] **刻意是两份**（那条钉着画师行的 `MAX_ICONS = 2`，
     * 这一条不受它约束 —— 用户 2026-10-01 点名"社交链接全部展开"）。三档来源的优先次序照旧：
     * 站方登记的记录 > 这条帖子的出处 > 作品页反查出的作者主页，后两条都只在**自己那一档还空着**时才补。
     *
     * 去重按"同一个身份"而不是同一条字符串：同 handle 的 `twitter.com` 与 `x.com` 是一枚
     * （实测 lichiko 就并给两条），带用户号的 pixiv 两种写法是一枚，子域相同的 tumblr 是一枚，
     * 其余按归一化后的地址（尾斜杠、`www`、大小写）。
     *
     * 认不出平台的那一枚**照摆**，标签给域名尾巴：那是画师自己挂在外链栏里的东西，
     * 我们没资格判它"不重要"。
     */
    fun profilePlan(
        urls: List<String>,
        source: String? = null,
        artworkAuthorUrl: String? = null,
        artworkAuthorAccount: String? = null,
    ): List<GalleryArtistLink> {
        val candidates = ArrayList<GalleryArtistLink>(urls.size + 2)
        urls.map { it.trim() }.filter { it.isNotEmpty() }.forEach { url ->
            candidates.add(GalleryArtistLink(GalleryArtistLinks.platformOf(url), url))
        }
        GalleryArtistLinks.fromSource(source)?.takeIf { source ->
            candidates.none { it.platform == source.platform }
        }?.let { candidates.add(it) }
        if (candidates.none { it.platform == GalleryArtistLinkPlatform.PIXIV }) {
            artworkAuthorUrl?.takeIf { GalleryArtistLinks.pixivUserId(it) != null }?.let { authorUrl ->
                candidates.add(
                    GalleryArtistLink(
                        GalleryArtistLinkPlatform.PIXIV,
                        authorUrl,
                        artworkAuthorAccount,
                    ),
                )
            }
        }
        val byIdentity = LinkedHashMap<String, GalleryArtistLink>()
        candidates.forEach { link -> byIdentity.putIfAbsent(identityOf(link), link) }
        // sortedBy 是稳定排序：同一档里保持站方给的原序，不额外造"谁更该排前面"的猜测。
        return byIdentity.values.sortedBy { PLATFORM_ORDER.indexOf(it.platform) }
    }

    /** 档序。写死一份在这里，介绍页与任何复用这排链接的地方都按它摆。 */
    private val PLATFORM_ORDER = listOf(
        GalleryArtistLinkPlatform.PIXIV,
        GalleryArtistLinkPlatform.TWITTER,
        GalleryArtistLinkPlatform.INSTAGRAM,
        GalleryArtistLinkPlatform.FANBOX,
        GalleryArtistLinkPlatform.TUMBLR,
        GalleryArtistLinkPlatform.YOUTUBE,
        GalleryArtistLinkPlatform.OTHER,
    )

    private fun identityOf(link: GalleryArtistLink): String = when (link.platform) {
        // 能取出"身份"的那几档按身份去重，取不出就退回归一化地址（不是"当两条都不认"）。
        GalleryArtistLinkPlatform.PIXIV ->
            GalleryArtistLinks.pixivUserId(link.url)?.let { "pixiv:$it" } ?: normalized(link.url)
        GalleryArtistLinkPlatform.TWITTER ->
            GalleryArtistLinks.twitterHandle(link.url)?.lowercase()?.let { "tw:$it" } ?: normalized(link.url)
        GalleryArtistLinkPlatform.INSTAGRAM ->
            GalleryArtistLinks.instagramHandle(link.url)?.lowercase()?.let { "ig:$it" } ?: normalized(link.url)
        GalleryArtistLinkPlatform.YOUTUBE ->
            GalleryArtistLinks.youtubeChannel(link.url)?.lowercase()?.let { "yt:$it" } ?: normalized(link.url)
        else -> normalized(link.url)
    }

    /** 同一条地址的两种写法（尾斜杠、`www`、大小写）要归成一枚。 */
    private fun normalized(url: String): String {
        val host = GalleryArtistLinks.hostLabel(url).removePrefix("www.")
        val path = url.substringAfter("://", url).substringAfter('/', "").substringBefore('?').trim('/').lowercase()
        return "$host/$path"
    }
}
