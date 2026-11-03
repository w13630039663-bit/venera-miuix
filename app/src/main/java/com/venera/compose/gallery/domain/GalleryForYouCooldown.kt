package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import java.util.Random

/**
 * 「换一批」的随机性与**冷却轮**判据（2026-11-03，用户「换一批总是一模一样的，之前出现过一次的过几轮再出现」）。
 *
 * ## 它治的两件事
 *
 * 1. **池子同序**：每站 `searchPosts` 的页按站方固定排序返回，「换一批」即便重抽了标签，
 *    只要标签串相近，第 1 页回的就是同一批头部条目 —— 屏上"换了一批"实际是原批重排。
 *    治法 = [depthPages]：换一批时每一站往榜单深处随机挪几页再取（首载不挪，相关性优先）。
 * 2. **刚见过的马上又见**：跨页去重（`GalleryForYouMerge` 的 `seenKeys`）只管本轮，
 *    [refresh] 一清就归零，下一轮完全可能再摆上一轮那四十张。
 *    治法 = [pushRound]/[activeKeys]：最近 [ROUNDS] 批的上屏键记成一条环形队列，
 *    取数时并进 `seenKeys` 一起剔 —— 与跨页去重同一条轴、同一个语义（"已经摆过"），
 *    所以 [GalleryForYouMerge] 一个字不用改。
 *
 * ## 池子见底不许把屏弄空
 *
 * 冷却会把池子越吃越小；小收藏 + 小标签量时"整批都被冷却吃掉"是真的会发生的事。
 * 调用方（`GalleryForYouViewModel.fetchPage`）那一笔重算必须带**赦免兜底**：
 * 滤空了就松开冷却再排一次 —— 宁可重现一张旧图，也不许摆一屏空白（空白的读数是"站没图"，那是谎）。
 *
 * ## 纯函数、确定性
 *
 * 全对象不吃时钟不吃随机全局：深度由注入的 seed 推导，同一 seed 同一站必得同一页深 ——
 * 与 `GalleryForYouMerge` "随机只来自注入的 seed" 那条口径完全一致（组合销毁重建不许换序列）。
 */
object GalleryForYouCooldown {

    /** 冷却几轮：最近三批上屏过的条目不再出现（第四批起，最旧那批解禁）。 */
    const val ROUNDS = 3

    /**
     * 换一批时最深挪到第几页（相对当前 pageSize）。
     *
     * 5 是量出来的保守值：pageSize≈30 时最深探到第 180 条附近，热门标签的结果量撑得住，
     * 而 danbooru 系的深页限制也碰不到。再深会让"一次换批"在三站各多等一截慢页。
     */
    const val MAX_DEPTH_PAGES = 5

    /** 环形队列推入一批：返回**新表**（旧表不动），只保留最近 [ROUNDS] 轮。 */
    fun pushRound(rounds: List<List<String>>, batchKeys: List<String>): List<List<String>> {
        if (batchKeys.isEmpty()) return rounds
        val next = rounds + listOf(batchKeys)
        return if (next.size > ROUNDS) next.takeLast(ROUNDS) else next
    }

    /** 现在处于冷却中的所有去重键（并集）。空表给空集，调用方可以直接并入 seen。 */
    fun activeKeys(rounds: List<List<String>>): Set<String> {
        if (rounds.isEmpty()) return emptySet()
        val union = HashSet<String>(rounds.sumOf { it.size })
        rounds.forEach { union += it }
        return union
    }

    /**
     * 换一批时这一站在"换一批"的那一轮里往深处挪几页（0 = 不动，榜首）。
     *
     * 每站独立推导（seed + 站序号进流），两站不会齐步走成"一起看同一段榜"。
     */
    fun depthPages(seed: Long, siteOrdinal: Int, bound: Int = MAX_DEPTH_PAGES): Int =
        Random(seed + siteOrdinal).nextInt(bound + 1)

    /**
     * 带冷却的一页合成：在 [GalleryForYouMerge.page] 的去重轴上并进冷却键，
     * 并带**赦免兜底** —— 池子被冷却吃光时松开冷却纯重算一次，宁可重现不许摆空。
     *
     * 除 `rounds` 外全部参数逐字转交 merge，判据不在这里多写一份（去重、滤收藏、
     * "滤完 0 条"分叉全在 [GalleryForYouMerge]）。
     */
    fun page(
        pools: Map<GallerySite, List<GalleryPost>>,
        seed: Long,
        page: Int,
        seenKeys: Set<String>,
        favouriteUids: Set<String>,
        rounds: List<List<String>>,
    ): Result<GalleryForYouMerge.Paged> {
        val cooldown = activeKeys(rounds)
        val first = GalleryForYouMerge.page(
            pools = pools,
            seed = seed,
            page = page,
            seenKeys = if (cooldown.isEmpty()) seenKeys else seenKeys + cooldown,
            favouriteUids = favouriteUids,
        )
        if (cooldown.isNotEmpty() && first.isSuccess && first.getOrNull()?.posts?.isEmpty() == true) {
            return GalleryForYouMerge.page(
                pools = pools,
                seed = seed,
                page = page,
                seenKeys = seenKeys,
                favouriteUids = favouriteUids,
            )
        }
        return first
    }
}
