package com.venera.compose.gallery.domain

/**
 * 首页的**内容域**（2026-10-16 选型 ⑤；2026-10-17 用户点名**只要两格**）。
 *
 * ⚠️ 从前那张示意上还有第三格「综合」，本鱼量过之后撤了：每日热门那台合并机器今天只吃
 * booru 三站（`GalleryDailyFeed` 就那三把键），所以「综合」与「图库站」是**同一个读数** ——
 * 两枚 chip 画着而行为一模一样，就是用户零容忍的那种假开关。
 * 要让那一格活起来，得真把 pixiv 日榜并进合并池（多一笔取数），不是换个字。
 *
 * ⚠️ 这一枚与 [GallerySearchSource] 那排**来源轴不是一件事**，两本账各自长：
 * - 来源轴长在**展开的搜索卡里**（全部 / yande.re / Gelbooru / Safebooru），决定"这一句搜哪几站"；
 * - 内容域长在**首页那一排**，决定"首页那几节吃什么料"。
 * 把它们并成一排就会出现两套来源切换器（2026-10-15 那排来源轴被摘掉的理由在这儿同样成立）。
 *
 * pixiv **不进来源轴**（`GallerySearchSource.ALL` 与 `options` 都显式剥了它）：
 * booru 三站吃 `tags=a+b order:score`，pixiv 吃"一个词 + 站内那串 `s_mode/order`"，
 * 并进去就是"半面墙按标签、半面墙按另一套语法"那种假读数 —— 这是**选择**，
 * 不是"匿名搜不到"（那一跳现量 200 / 每页 60 条，见 `PixivClient.tagWorks`）。
 */
enum class GalleryHomeDomain(val label: String) {

    /** 图库站那三节：`正在关注的画师 / 每日热门 / 猜你喜欢`，**一字不动**。 */
    BOORU("图库站"),

    /** Pixiv 那三节：`关注画师 / 排行榜 / 推荐`（用户 2026-10-17 定的次序，**没有**"最新作品"）。 */
    PIXIV("Pixiv"),
    ;

    companion object {
        /** 首页那一排画出来的顺序，就是用户看到的顺序。 */
        val HOME_OPTIONS: List<GalleryHomeDomain> = entries.toList()
    }
}
