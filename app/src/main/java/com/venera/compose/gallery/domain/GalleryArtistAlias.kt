package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GallerySite

/**
 * 站方的一条**画师记录**（yande.re `artist.json` 的四个字段）。
 *
 * 形状放在 domain 而不是复用 data 层的 DTO：判据层要能被单测问倒，
 * 而 DTO 带着一堆没人消费的站方字段（`updated_at`、`is_active` …）。
 *
 * @param aliasId 这条记录自己是个**别名**、真正的内容挂在另一条画师记录上时，那是目标的 id。
 *                站方给 null 就是"这个名字本身就是正名"，没有可换的。
 * @param urls 站方记的这位的外链地址（批次 L 用它摆平台图标与取 pixiv 头像）。
 *             **原样交回**、不在这里判平台 —— 判定归 [GalleryArtistLinks]，
 *             而"这一条到底是不是这位本人"只有那一份判据说得清。
 *             默认空表：站方某条记录没给 `urls` 键是真会出现的，那不是失败。
 */
data class GalleryArtistRecord(
    val id: Long,
    val name: String,
    val aliasId: Long?,
    val urls: List<String> = emptyList(),
)

/**
 * 搜索回 0 张时的**站内画师别名解析**判据（批次 J）。
 *
 * 一句话动因（用户 2026-09-30 拍板）：
 * 「在明确的 Artist 搜索场景中，某站原始查询返回 0 张时，查询该站 Artist 元数据，
 * 尝试解析 canonical name / aliases / source-specific identifier；
 * **只有获得明确站内关联时才重搜，否则保持 0 结果**」。
 *
 * 为什么只在 0 张时动（这是红线，不是保守）：抽样 11 条别名记录里有一档
 * "原词有货、正名反而更少"（`a-10` 原词 12 张 → 正名 `fuwa_daisuke` 只有 1 张）。
 * 非 0 张时替换会**净损失**用户已经能看到的图。
 *
 * 为什么判据读站方元数据而不是我们的词表或字符串形状：`artist.json?name=` 里能按精确同名
 * 取到一条画师记录，这件事本身就是"用户在搜一个画师名"的站方证据 —— 猜形状
 * （含下划线、像不像名字）会把 `1girl` 这类也拖进解析链。
 *
 * 外部提案里"拿 Danbooru 当跨站身份枢纽"那一半已被实测推翻（判据见
 * `gallery-artist-alias-2026-09.md` §〇 与 §1.2），这里只剩**各站解各站的别名**。
 */
object GalleryArtistAlias {

    /**
     * 这一站有没有可用的匿名别名路由。
     *
     * Gelbooru 没有：候选端点逐个试过（方案 §1.6），匿名一律拿不到画师记录表。
     * 把这条判死不是为了省事，是为了**不许在屏上暗示"两站都会自动换名"**。
     */
    fun supports(site: GallerySite): Boolean = site == GallerySite.YANDERE

    /**
     * "明确的画师场景"：用户那一排条件**只有一枚、且是包含型**。
     *
     * 必须读 filters 而不是发出去的那一串 —— 后者混着排行伪标签（`GalleryRankings.searchQuery`），
     * 按枚数判就会在"只选了一档排行"的那一轮里误判成两枚。
     * 多条件时 0 张的成因不止画师名，换名会把另一枚条件的锅算到这一枚上。
     */
    fun singleArtistToken(filters: List<GalleryTagFilter>): String? =
        filters.singleOrNull()?.takeIf { !it.excluded }?.name

    /**
     * 从站方回的一批记录里取**精确同名**的那条，且它必须带着别名指针。
     *
     * `artist.json?name=` 是前缀匹配（实测 `name=se` 回 16 条），所以"取第一条"是错的；
     * 取不到全等、或全等那条没有 `alias_id`，都交回 null = **不重搜、保持 0 结果**。
     */
    fun exactRecord(records: List<GalleryArtistRecord>, name: String): GalleryArtistRecord? =
        records.firstOrNull { it.name == name && it.aliasId != null }

    /**
     * 从同一批记录里取**精确同名**的那条，**不看它有没有别名指针**。
     *
     * 与 [exactRecord] 的差别是有意的，别合并：那一条服务"0 张时换成正名重搜"，没有指针就没得换；
     * 这一条服务"取这位画师自己的外链/头像"，而站方给**正名记录**的 `alias_id` 恒为 null
     * （2026-09-30 真机：`tokenbox` 三条外链都在，屏上一枚都没摆，就是因为复用了上面那条）。
     * 前缀匹配那条约束两边同样成立 —— 名字不全等就当没有。
     */
    fun recordByName(records: List<GalleryArtistRecord>, name: String): GalleryArtistRecord? =
        records.firstOrNull { it.name == name }

    /** 正名与原词相同（站方指针偶尔指回自己）→ null，那一档重搜只是白跑一笔。 */
    fun canonicalName(title: String?, original: String): String? =
        title?.takeIf { it.isNotBlank() && it != original }

    /**
     * 这一站的解析结果。**Gelbooru 一律 null**，判据集中在这里而不是散在调用点 ——
     * "两站都会自动换名"那种暗示就是靠这条拦住的。
     */
    fun canonicalNameFor(site: GallerySite, original: String, title: String?): String? =
        if (supports(site)) canonicalName(title, original) else null

    /** 用正名替换那一枚标签，其余条件与排除面原样。正名 == 原词时回 null（不换）。 */
    fun substitute(filters: List<GalleryTagFilter>, original: String, canonical: String): List<GalleryTagFilter>? {
        if (canonical == original) return null
        return filters.map { if (it.name == original) it.copy(name = canonical) else it }
    }

    /**
     * 这一腿要不要进解析链，要的话换哪一枚词。**四档条件全过才回名字**，
     * 任何一档不过就回 null = 一个字都不换、一笔请求都不多花。
     *
     * 判据收在这里而不是散在 ViewModel 里，是因为这几条各自都对应一种**错了不报错**的形态：
     * - 不限第 1 页 → 续页翻到尽头也回 0 张，会被当成"这是个别名"多打两笔；
     * - 不看 [GalleryLegOutcome.answered] → 超时那条腿也是 0 张，会在"这站没赶上"的读数上
     *   再叠一句"我们换过词"，两句话互相把对方的原因盖掉；
     * - **不许在有货时替换**（这条是红线，不是保守）：抽样里 `a-10` 原词 12 张、正名只有 1 张，
     *   替换会把用户已经看得见的图换少；
     * - 不限本站 → 另一站会被暗示"也会自动换名"，而它拿不到画师记录（[supports]）。
     */
    fun resolveToken(
        site: GallerySite,
        legPage: Int,
        filters: List<GalleryTagFilter>,
        outcome: GalleryLegOutcome,
    ): String? {
        if (!supports(site) || legPage != 1) return null
        if (!outcome.answered || outcome.posts.isNotEmpty()) return null
        return singleArtistToken(filters)
    }

    /**
     * 换过名要说的那句话。**两档不同脸**：只念"已按 X 搜索"而屏上还是 0 张，
     * 用户读到的就是"那一下没生效"；解析失败那一档**不在这里**（那种情况什么都不念，
     * 屏上与没做这个功能时完全一致 —— 见方案 §3.4 的失败口径）。
     */
    fun notice(original: String, canonical: String, hasResults: Boolean): String =
        if (hasResults) "yande.re 把 $original 记作 $canonical 的别名，已按 $canonical 搜索"
        else "yande.re 把 $original 记作 $canonical 的别名，按 $canonical 也没有图"
}
