package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import java.util.Random

/**
 * 猜你喜欢**一页**的合成：两站这一页各自返回的池 → 剔掉不能摆的 → 剔掉已经收藏的 →
 * 跨页跨站去重 → 各站取前 [PER_SITE_PAGE] → 整体打乱。
 *
 * ## 为什么不复用 [GalleryMerge.mix]
 *
 * `mix` 是"一次采 20、整屏打乱、**无页**"那一形，它的翻页 `exclude` 当初是被**故意删掉**的
 * （`GalleryMerge.kt:14-16`：日榜没有下一页，留着"还有更多"的状态机只会产出
 * 「到底了还显示加载更多」那类假象）。这一面墙要的恰恰是那三件它没有的事：
 * 每页各取 20、跨页去重、按站判到底。形不同就不能硬套 —— 硬套的代价是把"日榜不许有翻页"
 * 那条裁决反过来破坏掉。
 *
 * 但**判据本身全共用现成的**：能不能摆上屏走 [GalleryMerge.isDisplayable]，
 * 去重键走 [GalleryMerge.dedupKeys]（两处各写一份就会长成"日榜认得出重复、推荐页认不出"的分叉）。
 *
 * ## 排掉已收藏（方案 §三，本轮的第一性需求）
 *
 * 真机实测的语料只有 5 张（yandere 3 / gelbooru 2），123 个去重标签里 **115 个只出现 1 次**。
 * 把 `GalleryRecommendations` 的真实判据跑在这批语料上：gelbooru 两图词频全 1、
 * 池内只有 9/21 对共现 >0，主标签之外的候选**只剩同一张图自己的其余标签** ——
 * 于是抽出来的标签串就是"用户收藏那几张的标签子集"，拿去站方一搜，顶回来的正是那几张。
 *
 * 所以这不是优化项，是**上线前必须挡住**的一种失败：屏上摆着自己的收藏，读起来像坏了。
 * 计数进 [Paged.excludedFavourite]，页尾要如实报 —— 屏上少了几张而说不出原因，
 * 就是本仓最忌的静默交错（同 `GalleryFeedSource` 那条"慢的站这一轮缺席，必须被说出来"）。
 *
 * ## 「滤完 0 条」有两种完全不同的成因，必须分叉
 *
 * - 站方给了行、被 `isDisplayable` 滤光 → **我们的判据错了**（历史上扩展名白名单漏档就出过这情形），
 *   返回 failure 炸出来，与 [GalleryMerge.mix] 同一条口径；
 * - 站方给了行、被"已收藏"剔光 → 这是**用户的正当状态**（口味已经全收完了），
 *   返回 success 空列表 + `excludedFavourite`，交给页尾那档空态去说。
 *
 * 把第二种也炸成 failure，就会用一句技术错误盖住一个正当的空；反过来把第一种当正当的空，
 * 就成了"屏上一张没有但谁都没错"那种最难查的假读数。
 *
 * ## 随机只来自注入的 seed
 *
 * 同一个 seed + 同一页必得同一个序列。这不是洁癖：本项目导航条目被覆盖时组合会销毁，
 * "点进大图再返回"会重跑这段逻辑，裸 `Random()` 会让整屏换序（记忆「导航条目会重建组合」）。
 * seed 由 `GalleryForYouViewModel` 持有，只有"换一批"才换（方案 §四.2：两页 seed 不联动）。
 */
object GalleryForYouMerge {

    /** 每站每页取多少张进屏。与日榜那屏同一口径（用户给的数：「各取 20 张」）。 */
    const val PER_SITE_PAGE = 20

    data class Paged(
        val posts: List<GalleryPost>,
        /** 各站实际进屏的张数。少了就要在页尾说出来，不能装成正常。 */
        val perSite: Map<GallerySite, Int>,
        /** 因"已经在收藏里"被剔掉的张数 —— 页尾要报这个数。 */
        val excludedFavourite: Int,
        val droppedUnusable: Int,
        val droppedDuplicates: Int,
    ) {
        val videos: Int get() = posts.count { it.isVideo }
    }

    /**
     * @param pools     两站**这一页**各自返回的条目（缺某站就传空列表，由调用方的 `failures` 去说原因）。
     * @param seed      本轮的种子。
     * @param page      第几页（1 起）。只进随机流 —— 第 1 页与第 2 页的打乱序列必须不同，
     *                  否则两页会呈现同一种"交错节奏"，看着像没换内容。
     * @param seenKeys  前面各页已经上屏条目的去重键（uid / `md5:` / `src:`）。
     * @param favouriteUids 当前收藏的 uid 集合，**与 [GalleryPost.uid] 同形**（`routeKey:id`）。
     */
    fun page(
        pools: Map<GallerySite, List<GalleryPost>>,
        seed: Long,
        page: Int,
        seenKeys: Set<String>,
        favouriteUids: Set<String>,
    ): Result<Paged> = with(GalleryMerge) {
        var droppedUnusable = 0
        var excludedFavourite = 0
        // **按站分开**记"被收藏剔掉了几张"：全局一个计数会让"A 站被判据滤光、B 站被收藏剔过"
        // 互相掩护 —— 那一炸就炸不出来了（本函数头注那条分叉的全部意义就在这儿）。
        val excludedBySite = HashMap<GallerySite, Int>()
        val usable = pools.mapValues { (site, list) ->
            list.filter { post ->
                // 先判"是不是这一站的东西 + 能不能摆"，再判"是不是已收藏"：
                // 已收藏的计数只统计**本来能上屏**的，否则页尾那句"排掉 N 张你收藏过的图"
                // 会把压缩包这类根本不能摆的行也算进去，读数就假了。
                if (post.site != site || !isDisplayable(post)) {
                    droppedUnusable++
                    false
                } else if (post.uid in favouriteUids) {
                    excludedFavourite++
                    excludedBySite[site] = (excludedBySite[site] ?: 0) + 1
                    false
                } else {
                    true
                }
            }
        }

        for ((site, list) in pools) {
            if (list.isNotEmpty() && usable.getValue(site).isEmpty() && excludedBySite[site] == null) {
                return Result.failure(
                    IllegalStateException(
                        "${site.displayName} 给了 ${list.size} 条，滤完 0 条可用（扩展名白名单判据要复查）",
                    ),
                )
            }
        }

        val seen = HashSet(seenKeys)
        var droppedDuplicates = 0
        val posts = ArrayList<GalleryPost>(PER_SITE_PAGE * GallerySite.entries.size)
        // GallerySite.entries 的顺序即"先去重的优先序"，固定下来才不会因为 HashMap 无序而飘。
        for (site in GallerySite.entries) {
            val kept = ArrayList<GalleryPost>(PER_SITE_PAGE)
            // 站内**保持站方顺序**取前 20：日榜那边是从 100 条高分池里**随机抽** 20（它要的是"一屏多样性"），
            // 而这一面墙的每条都是照着用户的口味搜出来的 —— 抽着取会把最匹配的那几张随机掉，
            // 屏上就变成"推得不准"。
            for (post in usable[site].orEmpty()) {
                val keys = post.dedupKeys()
                if (keys.any { it in seen }) {
                    droppedDuplicates++
                    continue
                }
                seen += keys
                kept += post
                if (kept.size == PER_SITE_PAGE) break
            }
            posts += kept
        }

        // 混排只打乱"两站之间"的节奏，同一 seed 同一页永远同一个序列。
        posts.shuffle(Random(seed + page))

        Result.success(
            Paged(
                posts = posts,
                perSite = posts.groupingBy { it.site }.eachCount(),
                excludedFavourite = excludedFavourite,
                droppedUnusable = droppedUnusable,
                droppedDuplicates = droppedDuplicates,
            ),
        )
    }

    /** 一条上屏条目的去重键 —— 调用方要**累积这些键**，作为下一页 [page] 的 `seenKeys`。 */
    fun keysOf(post: GalleryPost): List<String> = with(GalleryMerge) { post.dedupKeys() }

    /**
     * 这一轮**哪些站到底了** —— 返回的集合就是"下一页不再向它们发请求"的那些站。
     *
     * 为什么按站单独判，而不是混成一个全局 `exhausted`：两站的池子深浅不一样
     * （实测 yande.re 的日榜恒 40 条、Gelbooru 的高分池 100 条），混成一个标志必然出现
     * "A 站还能出货、页尾写着到底了"这种假读数 —— 而那正是搜索页 §7 与日榜那头刚修掉的同一类缺陷。
     * 判据本身复用现成的 [GallerySearch.isExhausted]（返回条数不足一页即到底），不在这儿另写一把。
     *
     * ⚠️ **缺席的站不算到底**：[returned] 里没有这个站的键，含义是"这一轮它没给数"（超时、熔断、
     * 未配账号都算），不是"它没有货了"。把它当到底就会一次抖动永久踢掉这一站 ——
     * 那种"少了但没人说"的降级是本仓最忌的（`GalleryFeedSource` 那头同理：缺席要挂进 `failures` 说出来）。
     */
    fun exhaustedSites(
        returned: Map<GallerySite, Int>,
        limitOf: (GallerySite) -> Int,
    ): Set<GallerySite> = returned
        .filter { (site, count) -> GallerySearch.isExhausted(count, limitOf(site)) }
        .keys
}
