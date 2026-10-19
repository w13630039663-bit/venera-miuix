package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.PixivRestrict

/**
 * 顶栏那一枚可见性下拉的三档（2026-10-19，pixiv 个人账号页）。
 *
 * ## 为什么「全部」不是一条腿，而是两趟
 *
 * 站方的 `restrict` 只有 `public` / `private` 两个值，**没有**"两档都要"这一个值。
 * 所以 [All] 的含义是"同一个调用点发两趟再合"，不是"传一个更宽的值"。
 * 这一条必须写在类型上：早先的稿子里 [All] 差点被实现成 `restrict=public` 加一句"应该也含私密的"，
 * 那正是本仓反复立案的**假开关**那一族 —— 屏上摆着一档"全部"，实际只读了公开。
 *
 * 用户 2026-10-19 拍板「留全部」，代价就是每一屏多一跳；两趟的游标各记各的（见 [PixivLegCursor]）。
 */
enum class PixivReadScope(val screenLabel: String) {
    /** 公开 + 非公开两趟合并。 */
    All("全部"),

    /** 只读公开那一趟。 */
    Public("公开"),

    /** 只读非公开那一趟（号主自己的私密档）。 */
    Private("非公开"),
    ;

    /**
     * 这一档要发哪几腿。
     *
     * 顺序**就是**合屏后的摆放顺序（公开的在前）。反过来不改变内容，但会让"两趟到货先后"
     * 直接决定屏上顺序 —— 那样每次刷新顺序都可能不一样，用户会以为墙在跳。
     */
    val legs: List<PixivRestrict>
        get() = when (this) {
            All -> listOf(PixivRestrict.Public, PixivRestrict.Private)
            Public -> listOf(PixivRestrict.Public)
            Private -> listOf(PixivRestrict.Private)
        }

    /** 这一档里有没有私密内容 —— 决定屏上那句常驻点名摆不摆。 */
    val showsPrivate: Boolean get() = this != Public
}

/**
 * 一条腿的翻页账。
 *
 * [nextUrl] 为 null = 站方没给下一跳，也就是**这一条腿**到底了。它不等于整屏到底 ——
 * 见 [pixivFeedExhausted]。
 */
internal data class PixivLegCursor(
    val restrict: PixivRestrict,
    val nextUrl: String?,
) {
    val exhausted: Boolean get() = nextUrl == null
}

/**
 * 整屏到底的判据：**每一条在途的腿都到底**才算到底。
 *
 * 只问第一条是这一格最容易写错的地方：公开档通常几百页，私密档可能一页就没有，
 * 拿"公开那趟到底"当"整屏到底"的话，页脚会念一句"都摆完了"而私密那批永远进不来。
 */
internal fun pixivFeedExhausted(cursors: List<PixivLegCursor>): Boolean =
    cursors.isNotEmpty() && cursors.all { it.exhausted }

/**
 * 把两趟的答复合成一面墙。
 *
 * 去重按 [com.venera.compose.gallery.data.GalleryPost.uid]（`站:作品号`），**不是**按标题或作者 ——
 * 同一位画师同名两张图是真实存在的。去重的理由也不只是"好看"：书签与动态两族端点跨页会返回
 * 重复项（站方游标是 `max_bookmark_id` / `max_id`，同一秒内入库的多张会跨页重出），
 * 不去重就是同一张图连着出现两遍。
 *
 * @param legs 按 [PixivReadScope.legs] 那个顺序递进来；空表回空表（不猜）。
 * @param dedupKey 那一枚身份键，实际调用点交的就是 `GalleryPost.uid`。
 *   收成形参而不是直接吃 `PixivWork`：合屏与去重这件事与"这是哪种卡"无关，
 *   测试就能只钉"同键只留第一枚"，不必造一整颗十几枚必填字段的数据模型。
 */
internal fun <T> mergePixivLegs(legs: List<List<T>>, dedupKey: (T) -> String): List<T> {
    val seen = HashSet<String>(legs.sumOf { it.size })
    val out = ArrayList<T>(legs.sumOf { it.size })
    for (leg in legs) {
        for (work in leg) {
            if (seen.add(dedupKey(work))) out += work
        }
    }
    return out
}
