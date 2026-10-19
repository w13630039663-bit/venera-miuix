package com.venera.compose.gallery.domain

/**
 * pixiv 搜索结果的两个**页签**：作品 / 画师（2026-10-18）。
 *
 * ## 为什么单独一格，而不是塞进 [PixivSearchFilter]
 *
 * [PixivSearchFilter] 收的是"这一批要哪一些"（排序 / 收藏门槛 / 作品类型 / 比例 / 时间），
 * 它们全都只作用于**作品**那一栏。而这一枚回答的是"**要作品还是要点人**"——换的是
 * 整条取数腿（`/v1/search/illust` 与 `/v1/search/user` 两个端点、两套答复形状）与
 * 整面结果墙的形状（瀑布流 vs 名单）。把它并进条件里，就会出现"筛画师的比例"这种
 * 读起来通、发出去没有意义的状态。
 *
 * ## 出处
 *
 * pixez 的搜索结果页是一颗两页签的 `TabBar`（`lib/page/search/result_page.dart`：
 * `tabs: [Tab(illust), Tab(painter)]`），画师那一页只调 `/v1/search/user`
 * （`lib/page/search/result/painter/search_result_painter_page.dart`）。
 * 用户 2026-10-18 原话：「搜索作者（画师）的分栏怎么没做」。
 */
enum class PixivSearchKind(val label: String) {

    /** 插画 / 漫画 / 动图，走 `/v1/search/illust`（有匿名 www 腿兜底）。 */
    ILLUST("作品"),

    /**
     * 画师，走 `/v1/search/user`。
     *
     * ⚠️ **只有登录腿**：这一条没有匿名版本。没登录时那一栏必须把原因说出来，
     * 不许静默退回 [ILLUST] —— 那样用户会以为"这个词没有画师"。
     */
    ARTIST("画师"),
}
