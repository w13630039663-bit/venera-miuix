package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.splitGalleryTags
import java.util.Random

/**
 * 一轮「按收藏推荐」的产出。
 *
 * 三种情形**分开**，因为页面对用户说的话不一样：合成一个"空列表"会让
 * "你还没收藏"和"收藏了但算不出"长成同一张脸 —— 前者要引导去收藏，
 * 后者是我们的缺陷，得能炸出来（本仓口径：不许静默交错）。
 */
sealed interface GalleryRecommendation {

    /**
     * 抽出来的标签，**可能少于** [GalleryRecommendations.SELECTION_SIZE]：
     * 共现断了就收手，不硬凑（凑出来的第 N 枚会推得"看着像推荐、其实是噪声"）。
     */
    data class Tags(val tags: List<String>) : GalleryRecommendation

    /** 这一站一条收藏都没有。 */
    data object NoSeeds : GalleryRecommendation

    /** 有收藏，但标签全空或全被守卫滤掉。 */
    data object NoUsableTags : GalleryRecommendation
}

/**
 * 从**用户自己的收藏**里挑几枚标签出来 —— 挑完之后这就是一串普通的搜索条件，
 * 取数走 [com.venera.compose.gallery.data.YandeReClient.searchPosts] /
 * [com.venera.compose.gallery.data.GelbooruClient.searchPosts]，**不新写任何取数链路**。
 *
 * 机制参考 Breadboard（`util/RecommendationsHelper.kt`），三条**刻意不同**的地方：
 *
 * 1. **随机必须带种子**（[pickTags] 收 [Random] 而不是用 `Random.Default`）。
 *    它的实现里同一份收藏每次进来标签都可能变，这与我们"种子只在一处生成、
 *    组合重建后还是同一个序列"的既有裁决直接冲突（见 `GalleryViewModel.seed`、
 *    `GalleryMerge` 顶部注释）。
 * 2. **共现计数走预计算的 Set**（[pickTags] 里那句 `sets.map { it.toSet() }`）。
 *    它是在三层循环里对每张图 `map { lowercase() }` 再 `containsAll`，
 *    收藏上千张会明显慢。
 * 3. **同次数的标签再按名字定序**（[tagPool]）。它的池子顺序跟着收藏顺序飘，
 *    于是"同一份收藏必得同一批标签"这条不成立。
 *
 * ⚠️ **没有分类权重**（它的 `copyright 3.5 / character 2.0 / artist 1.5` 那一档）。
 * 原因是数据面拿不到：两站的 **post** JSON 都没有分类字段（实测，见
 * `GalleryPost` 顶部那张表与 `GalleryTag.kt:19-25`），分类只在 **tag 端点**上有 `type`，
 * 而那个端点不能批量精确查 —— 想要就得给候选池每枚各打一笔请求。
 * 这一档整个留在方案 `gallery-recommendations-from-favourites-2026-09.md` §九 第 3 步，
 * 单独一笔、可单独回退。
 */
object GalleryRecommendations {

    /** 候选池取词频前几名。写死（方案 §十.4：先不摆滑块）。 */
    const val POOL_SIZE = 7

    /** 一次抽几枚标签拼进搜索。写死；共现断了会少给。 */
    const val SELECTION_SIZE = 3

    /** 权重放大倍数：词频是整数，放大后将来接小数系数（分类权重）不用再改这一处。 */
    private const val WEIGHT_SCALE = 10

    /** 一条收藏的标签集（站方那串是空格分隔的小写下划线标识符）。 */
    fun tagsOf(seed: GalleryFavorite): List<String> = splitGalleryTags(seed.tags).map { it.lowercase() }

    /**
     * 词频池。**必须先过黑名单**：被挡着的词不该有机会变成推荐。
     *
     * 判据由调用方注入（UI 那边给的是 `ContentGuardManager.findGalleryBlockedRule`），
     * 这样这一层保持纯函数、能上单测，而且**与那面墙用的是同一把判据** ——
     * 不在这里另写一套"什么算被屏蔽"。
     */
    fun tagPool(
        tagSets: List<List<String>>,
        poolSize: Int = POOL_SIZE,
        isBlocked: (String) -> Boolean = { false },
    ): List<String> {
        if (poolSize <= 0) return emptyList()
        return tagSets.flatten()
            .filterNot(isBlocked)
            .groupingBy { it }
            .eachCount()
            .entries
            // 次数相同必须再按名字定序，不然池子跟着收藏顺序飘。
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(poolSize)
            .map { it.key }
    }

    /**
     * 从池子里抽出至多 [selectionSize] 枚。
     *
     * 先按词频加权随机抽一枚当主标签，之后每一枚都只看"**与已选的在同一张收藏里
     * 真的一起出现过**"的候选 —— 这条共现约束是全部质量来源：只按词频抽会得到
     * `1girl solo long_hair` 这种每张图都有的废组合。
     */
    fun pickTags(
        tagSets: List<List<String>>,
        pool: List<String>,
        selectionSize: Int = SELECTION_SIZE,
        random: Random,
    ): List<String> {
        if (pool.isEmpty() || selectionSize <= 0) return emptyList()
        val sets = tagSets.map { it.toSet() }
        val remaining = pool.toMutableList()
        val chosen = mutableListOf<String>()

        val frequencies = pool.associateWith { tag -> sets.count { tag in it } }
        val primary = pickWeighted(remaining, frequencies, random) ?: return emptyList()
        chosen += primary
        remaining -= primary

        while (chosen.size < selectionSize && remaining.isNotEmpty()) {
            val coExistence = remaining.associateWith { candidate ->
                sets.count { it.contains(candidate) && it.containsAll(chosen) }
            }.filterValues { it > 0 }
            // 共现断了就收手。剩下的候选与已选的没有任何一张收藏同时出现过，
            // 再抽就是往搜索条件里塞噪声。
            if (coExistence.isEmpty()) break
            val next = pickWeighted(coExistence.keys.toList(), coExistence, random) ?: break
            chosen += next
            remaining -= next
        }
        return chosen
    }

    /**
     * 一站一份推荐。
     *
     * ⚠️ [seeds] 必须是**同一个站**的收藏。两站词表不通，合并算会把 A 站的高频词
     * 拼到 B 站的搜索条件里去（同一串标签换个站就是另一个结果 —— 这条我们已经在
     * 搜索历史 `GallerySearchEntry` 上写过同样的判据）。按站分组的活儿在 [recommendBySite]。
     */
    fun recommend(
        seeds: List<GalleryFavorite>,
        seed: Long,
        isBlocked: (String) -> Boolean = { false },
        poolSize: Int = POOL_SIZE,
        selectionSize: Int = SELECTION_SIZE,
    ): GalleryRecommendation {
        if (seeds.isEmpty()) return GalleryRecommendation.NoSeeds
        val tagSets = seeds.map { tagsOf(it) }
        val pool = tagPool(tagSets, poolSize, isBlocked)
        if (pool.isEmpty()) return GalleryRecommendation.NoUsableTags
        val tags = pickTags(tagSets, pool, selectionSize, Random(seed))
        return if (tags.isEmpty()) GalleryRecommendation.NoUsableTags else GalleryRecommendation.Tags(tags)
    }

    /**
     * 每站各算一份。两站用**各自的抽样流**（种子 + 站点序号），
     * 与 `GalleryMerge.mix` 同一条理由：共用一段随机数会让两站的抽法互相牵连。
     */
    fun recommendBySite(
        favourites: List<GalleryFavorite>,
        seed: Long,
        isBlocked: (String) -> Boolean = { false },
    ): Map<GallerySite, GalleryRecommendation> = GallerySite.entries.associateWith { site ->
        recommend(
            seeds = favourites.filter { it.site == site },
            seed = seed + site.ordinal,
            isBlocked = isBlocked,
        )
    }

    /** 加权随机。全零权重交 null 让调用方收手，而不是 random 一枚糊上去。 */
    private fun pickWeighted(candidates: List<String>, weightOf: Map<String, Int>, random: Random): String? {
        if (candidates.isEmpty()) return null
        val weights = candidates.map { (weightOf[it] ?: 0).coerceAtLeast(0) * WEIGHT_SCALE }
        val total = weights.sum()
        if (total <= 0) return null
        var point = random.nextInt(total)
        for (index in candidates.indices) {
            point -= weights[index]
            if (point < 0) return candidates[index]
        }
        return candidates.last()
    }
}
