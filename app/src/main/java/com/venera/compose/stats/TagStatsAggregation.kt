package com.venera.compose.stats

import com.venera.compose.data.db.TaggedReadRow

/**
 * 题材分布的**聚合**（纯函数，2026-10-13 从 `ReadingStatsManager.getTagStats` 搬出来）。
 *
 * 搬的理由只有一个：这一圈循环里既有"标签怎么成桶"又有"本数怎么数"，而两样都能对着
 * 构造出来的行判对错 —— 留在 manager 里就得吃 `Context`（等字典、取接线口），
 * 单测一碰到那两处异步就测不到真身。本文件**一句 Android 类型都没有**。
 *
 * ## 为什么一个桶同时带 `pages` 与 `comics`
 *
 * 它们是**两个问题**的读数，不是一把尺子的两档：
 * - `pages`（页加权）答「哪一题材最占我的时间」—— 首页推荐取的就是这一把，
 *   口径与 [ReadingStatsManager.getTagStats] 原那条注释一样：翻 200 页的一本比翻 2 页就退的更该上榜；
 * - `comics`（去重后的**本数**）答「我读过的哪几本里有它」—— 统计页那半区用的是这一把，
 *   因为"占比 40% 是页"这种说法读起来像时间分配，而用户要看的是"十本里有四本"。
 *
 * ⚠️ 两个读数**必须各自明说自己在数什么**（屏上那句带"本"，推荐那侧不外露），
 * 合起来当一把尺子就会做出"条子长短与数字对不上"那种读不出对错的屏。
 */
internal fun aggregateTagStats(
    rows: List<TaggedReadRow>,
    days: Int,
    normalize: (String) -> String?,
): TagStats {
    val buckets = HashMap<String, TagBucketAccumulator>()
    val monthly = HashMap<String, HashMap<String, MutableSet<String>>>()
    val countedComics = HashSet<String>()
    var taggedComics = 0

    for (row in rows) {
        val pages = row.pages
        if (pages <= 0) continue
        val key = comicKey(row.sourceName, row.comicId)
        val month = row.readDate.take(MONTH_KEY_LENGTH)
        var contributed = false
        for (plainTag in row.tags.split(ReadingStatsManager.TAG_SEPARATOR)) {
            val display = normalize(plainTag) ?: continue
            contributed = true
            val acc = buckets.getOrPut(display) { TagBucketAccumulator() }
            acc.pages += pages
            acc.comics.add(key)
            acc.originals[plainTag] = (acc.originals[plainTag] ?: 0) + 1
            monthly.getOrPut(month) { HashMap() }
                .getOrPut(display) { HashSet() }
                .add(key)
        }
        // 分母只数**至少贡献了一个桶**的那些本：只有作者/分类词的本子不该灌进占比的分母。
        // 同一本读三天会出三行，所以这里按 key 去重 —— 否则「30 本里 12 本有它」会念成「90 本里 36 本有它」。
        if (contributed && countedComics.add(key)) taggedComics++
    }

    val sorted = buckets.entries.sortedWith(
        compareByDescending<Map.Entry<String, TagBucketAccumulator>> { it.value.pages }
            .thenBy { it.key }
    )
    return TagStats(
        buckets = sorted.map { (display, acc) ->
            val (namespace, raw) = acc.pickOriginal()
            TagStatBucket(
                display = display,
                pages = acc.pages,
                comics = acc.comics.size,
                searchNamespace = namespace,
                searchRaw = raw,
            )
        },
        taggedComics = taggedComics,
        monthlyTop = monthly.entries.sortedByDescending { it.key }
            .take(if (days > 30) 12 else 6)
            .map { (month, counts) ->
                MonthlyTagTop(
                    month = month,
                    tags = counts.entries
                        .sortedWith(
                            compareByDescending<Map.Entry<String, Set<String>>> { it.value.size }
                                .thenBy { it.key }
                        )
                        .take(MONTHLY_TOP_TAGS)
                        .map { it.key to it.value.size },
                )
            },
    )
}

/**
 * 「一本」的键：`源名 + 本子号`。
 *
 * ⚠️ 这一枚**必须与 [ReadingStatsManager.getSummary] 数"看过多少本"用的是同一个形状** ——
 * 不同源的同 id 撞车在画廊/漫画那侧真会发生（`comic_id` 是源内编号，不是全局编号）。
 * 两边各写一遍迟早分叉（一边带源名一边不带 ⇒ 两格读数差几本，而屏上看不出来），所以这里只留一颗。
 */
internal fun comicKey(sourceName: String, comicId: String): String = "${sourceName}_$comicId"

/** 页加权那把尺子的累加容器（本数与原始标签计数都挂在这里）。 */
internal class TagBucketAccumulator {
    var pages = 0

    /** 归到本桶的**去重本键** —— 一本读三天只算一本。 */
    val comics = HashSet<String>()

    /** 归到本桶的**原始** `namespace:tag` → 出现次数，用于挑一个可检索的原值。 */
    val originals = HashMap<String, Int>()

    /**
     * 取出现次数最高的原始标签；并列时按字典序取最小，保证同一份数据每次算出的结果一致
     * （否则「这次点进去搜 A、下次搜 B」这种不可复现的行为会留在统计页）。
     */
    fun pickOriginal(): Pair<String, String> {
        val plain = originals.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        ).firstOrNull()?.key ?: return "" to ""
        val idx = plain.indexOf(':')
        return if (idx > 0) {
            plain.substring(0, idx).trim() to plain.substring(idx + 1).trim()
        } else {
            "" to plain.trim()
        }
    }
}

private const val MONTH_KEY_LENGTH = 7
private const val MONTHLY_TOP_TAGS = 2
