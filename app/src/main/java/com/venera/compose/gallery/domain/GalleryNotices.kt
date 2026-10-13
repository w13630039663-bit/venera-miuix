package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GallerySite

/**
 * 一条「这一轮少了什么」的提示。
 *
 * ── 为什么要有这一层，而不是让页面各自拼串 ──
 *
 * 这一屏从前把"某站没配账号"和"某站这轮没答上"和"某站本来就没货"三件事
 * **拼成同一句灰字**（`failures.entries.joinToString(" · ") { "站：原因" }`）。
 * 后果不是难看，是**三类处境的出路完全不同却在屏上长得一样**：
 * 一类要用户去设置里填两个字段，一类要重试，还有一类什么都不用做（重试也没用）。
 * 拼成一串之后，用户只能靠把整段字读完才知道自己该干什么 —— 而那段字里
 * 连一个能按的东西都没有。
 *
 * 所以这一层只做一件机器能判、且有对错的事：**把一句原因归到哪一种**，
 * 并按站表序把同一类的站并成一条。文案与出口由 `gallery/ui/GalleryNotices.kt` 接。
 *
 * ── 顺序 ──
 *
 * [sites] 恒按 `GallerySite.entries` 定死，与 `GallerySearchSource` 那条"腿的顺序"
 * 同一条纪律：跟着 `HashMap` 的无序飘，同一份原因会在两次组合之间换名字顺序，
 * `remember` 与 diff 都会跟着抖。
 */
class GalleryNotice(
    val kind: GalleryNoticeKind,
    /** 这一条在说哪几站。至少一条；按站表序。 */
    val sites: List<GallerySite>,
    /** 这几站各自的原因，**原样**带着（一个字的加工都没有）。 */
    val reasons: Map<GallerySite, String>,
    /**
     * 正文。与标题**分工**，不是标题的复述。
     *
     * ⚠️ 这一格是 2026-10-13 真机截图上读出来的：那一版把 `reasons` 直接当正文，
     * 于是 Gelbooru 那块成了「Gelbooru 需要先配置账号才能用」+「Gelbooru 需要先配置账号才能用：它的接口…」
     * —— 同一件事在一张块里说了两遍；Safebooru 那块更糟，标题与正文几乎是同一句。
     * 所以正文由 [bodyOf] 按 kind 单独给：标题管"是什么事"，正文只管**标题里没有的那部分**
     * （为什么 + 怎么办，或逐站的原始原因）。
     */
    val body: String,
) {
    val tone: GalleryNoticeTone get() = kind.tone

    /** 这一条给用户的出口；`null` = 这一档本来就没什么可做的。 */
    val action: GalleryNoticeAction? get() = kind.action
}

/**
 * 这一条**是哪一种**缺。
 *
 * ── 为什么不是"一个严重度枚举"就够 ──
 *
 * 严重度管的是**颜色**，而标题的措辞要按"哪一种"分：同属"没什么可做"这一档的
 * "这一站这轮没给内容"与"这一站还没有可推荐的标签"是两句话，出路也不同
 * （前者可以等等再刷，后者要去收藏几张）。
 *
 * 把"哪一种"当成**数据**而不是让 UI 回头去猜原因串，是本层最重要的一个决定：
 * 字符串猜法（`reason.contains("推荐")`）在措辞调整时会**静默错档** ——
 * 不报错，只在屏上换一句话、换一个颜色。
 */
enum class GalleryNoticeKind(
    val tone: GalleryNoticeTone,
    val action: GalleryNoticeAction?,
) {
    /** 这一站整站发不出车：没配账号。出路是去配置。 */
    MissingAccount(GalleryNoticeTone.Warning, GalleryNoticeAction.ConfigureAccount),

    /** 这一笔真出错了（超时 / 被拒 / 解析不出来）。出路是重试。 */
    LegFailed(GalleryNoticeTone.Error, GalleryNoticeAction.Retry),

    /** 站方答上了、只是这一轮一行都没给。重试还是这个结果 —— 不给出口。 */
    NoContent(GalleryNoticeTone.Info, null),

    /** 这一站还没有可推荐的标签（用户还没收藏它的图）。重发不会变 —— 不给出口。 */
    NoRecommendTags(GalleryNoticeTone.Info, null),
}

/**
 * 提示的严重度。**与视觉那一枚（`components.venera.VeneraNoticeTone`）是一对，
 * 由 UI 侧一次穷尽 `when` 映射过去** —— 域层不 import components（分层守卫会拦），
 * 而 `when` 不留 `else` 保证加档时编译器当场报错，不会静默长成同一个颜色。
 */
enum class GalleryNoticeTone {
    /** 本来就没什么可做的。 */
    Info,

    /** 能用，但缺一块。 */
    Warning,

    /** 这一笔真出错了。 */
    Error,
}

/** 这一条提示给用户的出口。 */
enum class GalleryNoticeAction {
    /** 去「设置 → 画廊 → 画廊站点账号」填凭据。 */
    ConfigureAccount,

    /** 原地把这一轮重发一次。 */
    Retry,
}

/**
 * 「这一站还没有能推荐的标签」那句原因（**站名前置**，与另外几档的措辞同形）。
 *
 * 提到 domain 而不是留在 `GalleryForYouViewModel` 里就地写串：归类那一层要认它
 * （见 [kindOfReason]），而 `gallery/domain` 不许 import `gallery/ui`（分层守卫拦着）。
 * 两处各写一份的后果是**归类静默错档** —— 那句会掉进 `LegFailed`，
 * 于是屏上给一枚按下去没用的「重试」，而这一站只是还没有收藏。
 */
fun noRecommendTagsReason(site: GallerySite): String = "${site.displayName}$NO_RECOMMEND_TAGS_SUFFIX"

/** 上面那句的固定尾巴（归类判据读它，别在别处再抄一遍）。 */
const val NO_RECOMMEND_TAGS_SUFFIX: String = "这一站还没有能推荐的标签"

/**
 * 把「按站一份的缺席原因」归成**有层级、可操作**的几条。
 *
 * 归类的判据是**原因串的出处**，不是关键词猜：每一档都指向一个唯一的常量
 * （[GallerySearchSource.NEEDS_ACCOUNT_TITLE] / [GalleryLegGuard.NO_CONTENT] /
 * [NO_RECOMMEND_TAGS_SUFFIX]）。判据写错会当场红在单测里，
 * 而不是长成"某条提示颜色不对"这种只能靠肉眼发现的偏差。
 *
 * @param failures 站 → 原因。空表返回空表（页面据此整块不摆）。
 */
fun galleryNotices(failures: Map<GallerySite, String>): List<GalleryNotice> {
    if (failures.isEmpty()) return emptyList()

    // 先按"哪一种"分桶，桶内站序由 `GallerySite.entries` 这一次遍历统一收口。
    val buckets = LinkedHashMap<GalleryNoticeKind, MutableList<GallerySite>>()
    GallerySite.entries.forEach { site ->
        val reason = failures[site] ?: return@forEach
        buckets.getOrPut(kindOfReason(reason)) { mutableListOf() } += site
    }

    // 条与条的顺序 = 严重度从高到低，同档内按 [GalleryNoticeKind.entries] 定死。
    // "出错了"必须排在最前 —— 它才是用户此刻唯一该看的那一条；
    // 按枚举序而不是按 `LinkedHashMap` 的插入序，是为了让同一份 failures
    // 无论以什么顺序填进 Map，出来的条序都一样（`remember` 与 diff 才不抖）。
    //
    // ⚠️ **降序**：`GalleryNoticeTone` 的声明序是 Info → Warning → Error（从轻到重读着顺），
    // 而屏上要的是从重到轻。写成 `sortedBy` 会把"这一站这轮没货"顶到最上面、
    // 把真正该看的"这一轮没答上"沉到下面 —— 那是读数顺序在说谎。
    return GalleryNoticeKind.entries
        .mapNotNull { kind -> buckets[kind]?.takeIf { it.isNotEmpty() }?.let { kind to it } }
        .sortedByDescending { (kind, _) -> kind.tone.ordinal }
        .map { (kind, sites) ->
            val reasons = sites.associateWith { failures.getValue(it) }
            GalleryNotice(
                kind = kind,
                sites = sites,
                reasons = reasons,
                body = bodyOf(kind, sites, reasons),
            )
        }
}

/**
 * 正文 —— **只写标题里没有的那部分**。
 *
 * 标题由 `gallery/ui/GalleryNotices.kt` 给（它管措辞与站名），正文在这里定：
 * 两处分开是因为"逐站念原始原因"这件事要读 [reasons]，而它是域层的数据。
 *
 * ⚠️ 这一格是 2026-10-13 真机截图上读出来的：那一版把 `reasons` 一律当正文，
 * 于是 Gelbooru 那块成了「…需要先配置账号才能用」+「…需要先配置账号才能用：它的接口…」
 * —— 同一件事在一张块里说了两遍；Safebooru 那块更糟，标题与正文几乎是同一句。
 *
 * 所以除了"每站原因各不相同"的 [GalleryNoticeKind.LegFailed] 之外，
 * 其余三档的正文一律是**标题答不出的那半句**：要么是"怎么办"，要么是"这不是你的错"。
 */
private fun bodyOf(
    kind: GalleryNoticeKind,
    sites: List<GallerySite>,
    reasons: Map<GallerySite, String>,
): String = when (kind) {
    // 为什么用不了 + 去哪儿配。那一句本来就是共享文案，逐站念反而会与标题撞车。
    GalleryNoticeKind.MissingAccount -> GallerySearchSource.NEEDS_ACCOUNT_BODY

    // 逐站念原始原因，一个都不合并 —— 这一档的原因**各站确实不同**（超时 / 401 / 解析失败），
    // 合并成"部分站点无结果"是本仓最忌的静默降级：用户读不出是哪一站、也读不出为什么。
    GalleryNoticeKind.LegFailed -> sites.joinToString("\n") { site -> lineOf(site, reasons.getValue(site)) }

    // 站方答上了、只是没货。标题已说"这一轮没有内容"，正文补上**这不是错误**与下一步。
    GalleryNoticeKind.NoContent ->
        "站方这一轮没有回内容。不是出错，也不是被屏蔽规则挡掉 —— 稍后再刷一次，或换一天。"

    // 标题已说"还没有可推荐的标签"，正文补上**为什么**与**去哪儿解决**。
    GalleryNoticeKind.NoRecommendTags ->
        "还没有收藏过${if (sites.size > 1) "这几站" else "这一站"}的图。" +
            "先去大图页收藏几张，这里就会照着你的口味推荐。"
}

/** 一站一行。站名自带时不再前缀（避免"Gelbooru：Gelbooru 需要…"那种重名）。 */
private fun lineOf(site: GallerySite, reason: String): String =
    if (reason.startsWith(site.displayName)) reason else "${site.displayName}：$reason"

/**
 * 一句原因属于哪一种。
 *
 * ⚠️ **只认常量，不做关键词匹配**。"超时"、"401"这类词可能出现在任意站方的报错里，
 * 拿它们当判据会让"某站换了句报错文案"变成"这条提示换了档位" —— 那种错不报错、
 * 只在屏上换个颜色，是最难查的一类。
 *
 * ⚠️ 三档的顺序**不能调**：`NO_CONTENT` 那句也完全可能出现在别的串里，
 * 而它比"未知原因"更具体，所以先判它。
 */
internal fun kindOfReason(reason: String): GalleryNoticeKind = when {
    // 发车前就拦住的那一类：它自带完整措辞（见 GallerySearchSource），
    // 所以判据是"以那句标题起头"，而不是"含『账号』两个字"。
    reason.startsWith(GallerySearchSource.NEEDS_ACCOUNT_TITLE) -> GalleryNoticeKind.MissingAccount
    // 站方答上了、只是这一轮一行都没给 —— 不是失败，重试也还是这个结果。
    reason == GalleryLegGuard.NO_CONTENT -> GalleryNoticeKind.NoContent
    // 这一站还没有可推荐的标签：不是出错，是"你还没收藏它的图"。
    reason.endsWith(NO_RECOMMEND_TAGS_SUFFIX) -> GalleryNoticeKind.NoRecommendTags
    else -> GalleryNoticeKind.LegFailed
}
