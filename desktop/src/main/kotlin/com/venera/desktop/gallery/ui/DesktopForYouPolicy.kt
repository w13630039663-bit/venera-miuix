package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRecommendation
import com.venera.compose.gallery.domain.GalleryRecommendations
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GalleryTagFilter

/**
 * 桌面「发现」那一屏的**取数计划**（第九批）。纯数据，不 require `@Composable` ——
 * 为的是它能被单测直接取值（与 `DesktopGalleryWallPolicy` 同一条理由）。
 *
 * ## 这颗件只转交，不重述
 *
 * 三件事一律走共享那颗判据，桌面这边一行都不重写：
 *
 * | 事 | 出处 |
 * |---|---|
 * | 每站抽哪几个标签、抽不出来算哪一档 | [GalleryRecommendations.recommendBySite]（每站各用自己的抽样流，种子 + 站点序号） |
 * | 标签集 → 真发出去的那一串 | [GallerySearch.queryOf] |
 * | 两站结果怎么混、剔掉已收藏、滤掉不能上屏的 | `GalleryForYouMerge.page`（在 pane 那一侧调，本颗不碰） |
 *
 * ⚠️ 桌面**不许自己 `joinToString(" ")` 拼那一串**：拼串口径全仓只该有一处，
 * 否则页尾念的那串与真发出去的那串会长得不一样 —— 那是"读数与行为不符"这一族里最难查的一种。
 * 这条由 `DesktopForYouPlanTest` ⑥ 扫着。
 *
 * ## 为什么这里带着三档空态而不是一个布尔
 *
 * 「你还没收藏」与「我们的判据算不出东西」是两句话（Android 那侧 `stageOf` 的头注就是这么写的）。
 * 合成一句就是拿"用户没干活"替自己的缺陷遮丑；而把两站都没货与只有一站没货混成"屏上是空的"，
 * 又会把"少一站"报成"整屏没内容"。所以这三档分开交回，由屏上逐档说不同的话。
 */
internal class DesktopForYouPlan(
    /** 每站抽到的那一档（原样交回共享那颗的产物，桌面不加工）。 */
    val perSite: Map<GallerySite, GalleryRecommendation>,
    /** **发得出去**的站 → 那一串。空表 = 这一轮一笔都不该发。 */
    val queries: Map<GallerySite, String>,
) {
    /** 只要有一站抽得出标签就该发车 —— 少一站由 [silentSites] 去说，不该把整屏报空。 */
    val canDispatch: Boolean get() = queries.isNotEmpty()

    /** 这一轮没发出去的站（逐站点名用，次序恒为枚举自然序）。 */
    val silentSites: List<GallerySite> get() = GallerySite.entries.filter { it !in queries.keys }

    /** 这一站为什么没发出去；发得出去给 `null`。 */
    fun reasonFor(site: GallerySite): String? = when (val rec = perSite[site]) {
        is GalleryRecommendation.Tags -> null
        GalleryRecommendation.NoSeeds ->
            "${site.displayName}这一站还没有能推荐的标签：你还没收藏过它的图"
        GalleryRecommendation.NoUsableTags ->
            "${site.displayName}这一站有收藏，但标签全被屏蔽规则滤掉了 —— 是我们的判据没东西可发，不是你还没收藏"
        null -> "${site.displayName}这一站今天没参与抽样"
    }

    /**
     * 整屏一条都发不出去时该说的那一句。
     *
     * ⚠️ 两档的**优先次序**是判据：`NoUsableTags` 盖过 `NoSeeds`。
     * 反过来写会让"有收藏却被我们滤光"那一档念成"快去收藏" —— 用户照做了还是空的，
     * 而那句引导就成了错的诊断。
     *
     * ⚠️ 那句**不许写死站点数目**：`GallerySite` 今天是三枚（yande.re / Gelbooru / Safebooru），
     * 写"两站"就是又埋一条会过期的读数（同一族教训见 §22.3 那条活了四批的说明）。
     */
    val blankScreenReason: String? get() = when {
        canDispatch -> null
        perSite.values.any { it is GalleryRecommendation.NoUsableTags } ->
            "有收藏，但抽出来的标签全被屏蔽规则滤掉了 —— 这一屏是我们的判据空，不是你没干活"
        else -> "还没有可以推荐的收藏：这一屏是从你的收藏里抽标签的，先去「收藏」那一屏或画廊里存几张图"
    }
}

/**
 * 收藏 → 每站的抽样与那一串。
 *
 * @param seed 本轮种子。**只从调用点进来**（桌面这一屏的种子归 pane 持有），
 *   同一颗种子必须交回同一串 —— 组合重建时不换序是这一屏能"点进去再返回还在原处"的前提。
 * @param isBlocked 黑名单判据，由调用点把屏蔽规则那一份接进来（桌面走 `contentGuard`）。
 *   **必填、不给默认值**：带默认值时"漏传"不报错，而漏传的形状正好是"屏蔽规则不参与抽样"，
 *   且屏上那句「是我们的判据空」会永远走不到（同一族规矩：漏传不报错的新参数一律必填）。
 *   单测要"什么都不挡"就显式传 `{ false }`。
 */
internal fun desktopForYouPlan(
    favourites: List<GalleryFavorite>,
    seed: Long,
    isBlocked: (String) -> Boolean,
): DesktopForYouPlan {
    val perSite = GalleryRecommendations.recommendBySite(favourites, seed, isBlocked)
    val queries = perSite.mapNotNull { (site, rec) ->
        val tags = (rec as? GalleryRecommendation.Tags)?.tags ?: return@mapNotNull null
        site to GallerySearch.queryOf(tags.map { GalleryTagFilter(it) })
    }.toMap()
    return DesktopForYouPlan(perSite = perSite, queries = queries)
}
