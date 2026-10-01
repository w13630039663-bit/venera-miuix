package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite

/**
 * 把「全部」那一档的**两条腿**合成一面墙。
 *
 * 与日榜那头 [GalleryMerge] 的三处刻意不同，每一条都有代价，所以逐条写明白：
 *
 * 1. **站内保持站方顺序、两腿轮转**，不打乱。日榜要的是"一屏多样性"（两站各抽 20 再洗），
 *    而搜索结果的顺序就是相关性顺序 —— 洗一遍会把用户要找的那张冲走。
 *    这与推荐墙「抽着取会推得不准」是同一条理由（`GalleryForYouMerge` 那头也保序）。
 * 2. **两腿都空 = 正常结果**，不炸。搜索搜不到东西是常态，日榜那头空池才是异常（它一天总有 40 条）。
 * 3. **一整腿被滤光也不炸**，交回 [Legged.droppedUnusable] 由页尾那句念出来。
 *    搜索这头炸会把一整轮换成错误页，而用户要的是"那几张坏了"。
 *
 * 去重键**沿用日榜那把**（`GalleryMerge.dedupKeys`）：两处各写一份就会长成
 * "日榜认得出重复、搜索认不出"的分叉。
 */
object GallerySearchMerge {

    data class Legged(
        val posts: List<GalleryPost>,
        /** 各腿实际进屏的条数。少了就要在页面上说出来，不能装成正常。 */
        val perSite: Map<GallerySite, Int>,
        /** 累积到现在的去重键 —— 调用方要把它传回下一页的 [interleave]。 */
        val seenKeys: Set<String>,
        val droppedUnusable: Int,
        val droppedDuplicates: Int,
    ) {
        val videos: Int get() = posts.count { it.isVideo }
    }

    /**
     * @param legs 每条腿这一页给回的内容。**缺键的腿**含义是"这一轮它没给数"（超时、熔断、
     *   未配账号都算），它不参与混排，也不该被算进"到底"（见 [exhaustedSites]）。
     * @param seenKeys 已经上过屏的键。站方翻页窗口滑动时会重发同一批，不去重就会看到
     *   "翻一页三张里两张是刚才那两张"。
     */
    fun interleave(
        legs: Map<GallerySite, List<GalleryPost>>,
        seenKeys: Set<String> = emptySet(),
    ): Result<Legged> {
        var droppedUnusable = 0
        val usable = legs.mapValues { (site, list) ->
            list.filter { post ->
                val ok = post.site == site && GalleryMerge.isDisplayable(post)
                if (!ok) droppedUnusable++
                ok
            }
        }

        val seen = HashSet(seenKeys)
        var droppedDuplicates = 0
        val posts = ArrayList<GalleryPost>(usable.values.sumOf { it.size })
        // 站表序即轮转的起始序，也是跨腿撞图时"谁留下"的优先序 —— 固定下来才不会跟着 Map 实现飘。
        val order = GallerySite.entries.filter { usable.containsKey(it) }
        val longest = usable.values.maxOfOrNull { it.size } ?: 0
        for (row in 0 until longest) {
            for (site in order) {
                val post = usable[site]?.getOrNull(row) ?: continue
                val keys = with(GalleryMerge) { post.dedupKeys() }
                if (keys.any { it in seen }) {
                    droppedDuplicates++
                    continue
                }
                seen += keys
                posts += post
            }
        }

        return Result.success(
            Legged(
                posts = posts,
                perSite = posts.groupingBy { it.site }.eachCount(),
                seenKeys = seen,
                droppedUnusable = droppedUnusable,
                droppedDuplicates = droppedDuplicates,
            ),
        )
    }

    /**
     * 这一轮**哪些腿到底了** —— 返回的就是"下一页不再向它们发请求"的那些腿。
     *
     * 按腿单独判而不是混成一个全局标志：两站池子深浅不同（实测 yande.re 的 `limit` 天花板 320、
     * Gelbooru 每页上限 100），混起来必然出现"A 站还能出货、页尾写着到底了"那种假读数。
     * 判据本身复用现成的 [GallerySearch.isExhausted]，不在这儿另写一把。
     *
     * ⚠️ **缺席的腿不算到底**：[returned] 里没有这个腿的键，含义是"这一轮它没给数"，
     * 不是"它没有货了"。当成到底就会让一次抖动永久踢掉一站 —— 那种"少了但没人说"的降级是本仓最忌的。
     *
     * （这一条从 `GalleryForYouMerge` 搬来：推荐墙与搜索的「全部」档要的是同一把判据，
     * 两处各写一份迟早会分叉。）
     */
    fun exhaustedSites(
        returned: Map<GallerySite, Int>,
        limitOf: (GallerySite) -> Int,
    ): Set<GallerySite> = returned
        .filter { (site, count) -> GallerySearch.isExhausted(count, limitOf(site)) }
        .keys
}
