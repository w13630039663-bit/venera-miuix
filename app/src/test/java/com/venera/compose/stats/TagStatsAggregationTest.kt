package com.venera.compose.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 题材分布聚合的判据（2026-10-13，本数那把尺子今天新加）。
 *
 * 归一化在这里被换成一枚**假管线**（`author:` 前缀的词全丢），为的是只钉"怎么数"这一件事；
 * 字典与繁简那条链由 `TagNormalizer` 自己的用例管，两层各一把尺子。
 */
class TagStatsAggregationTest {

    /** 只认 `author:` 这一类前缀为"归不出去"的词。 */
    private val normalize: (String) -> String? = { raw ->
        if (raw.startsWith("author:")) null else raw.trim()
    }

    private fun row(
        comicId: String,
        date: String,
        pages: Int,
        vararg tags: String,
        sourceName: String = "eh",
    ) = com.venera.compose.data.db.TaggedReadRow(
        readDate = date,
        pages = pages,
        tags = tags.joinToString(ReadingStatsManager.TAG_SEPARATOR),
        comicId = comicId,
        sourceName = sourceName,
    )

    private fun bucket(stats: TagStats, display: String): TagStatBucket =
        stats.buckets.first { it.display == display }

    @Test fun sameComicAcrossDaysCountsOneComicButAllPages() {
        val stats = aggregateTagStats(
            listOf(
                row("c1", "2026-10-01", 10, "maid"),
                row("c1", "2026-10-02", 12, "maid"),
                row("c1", "2026-10-03", 8, "maid"),
            ),
            days = 30,
            normalize = normalize,
        )
        assertEquals(30, stats.buckets.first().pages)
        assertEquals(1, bucket(stats, "maid").comics)
        // 分母也按本去重：三行读下来是「1 本」，不是「3 本」
        assertEquals(1, stats.taggedComics)
    }

    /** `comic_id` 是**源内**编号：不同源的同 id 是两本，绝不能并成一本。 */
    @Test fun sameIdFromDifferentSourcesAreTwoComics() {
        val stats = aggregateTagStats(
            listOf(
                row("c1", "2026-10-01", 10, "maid", sourceName = "eh"),
                row("c1", "2026-10-01", 10, "maid", sourceName = "nh"),
            ),
            days = 30,
            normalize = normalize,
        )
        assertEquals(2, bucket(stats, "maid").comics)
        assertEquals(2, stats.taggedComics)
    }

    /** 一本带两个题材：两桶各数它一次，而**分母只数一本**（否则占比条会被灌水）。 */
    @Test fun oneComicTwoTopicsAddsBothBucketsButOneToDenominator() {
        val stats = aggregateTagStats(
            listOf(row("c1", "2026-10-01", 10, "maid", "coffee")),
            days = 30,
            normalize = normalize,
        )
        assertEquals(1, bucket(stats, "maid").comics)
        assertEquals(1, bucket(stats, "coffee").comics)
        assertEquals(1, stats.taggedComics)
    }

    /** 标签全被管线滤掉的行**不进分母**（与改造前那条口径一致，只是量的东西从页换成本）。 */
    @Test fun rowsWhoseTagsAllDropOutDoNotEnterTheDenominator() {
        val stats = aggregateTagStats(
            listOf(
                row("c1", "2026-10-01", 10, "maid"),
                row("c2", "2026-10-01", 50, "author:someone"),
            ),
            days = 30,
            normalize = normalize,
        )
        assertEquals(1, stats.taggedComics)
        assertEquals(listOf("maid"), stats.buckets.map { it.display })
    }

    @Test fun zeroPageRowsAreSkippedEntirely() {
        val stats = aggregateTagStats(
            listOf(row("c1", "2026-10-01", 0, "maid")),
            days = 30,
            normalize = normalize,
        )
        assertTrue(stats.buckets.isEmpty())
        assertEquals(0, stats.taggedComics)
    }

    /**
     * 桶的**序**仍然是页加权（首页推荐用的那一把），本数只是多带的一格读数。
     * 这一条钉的是"两把尺子别互相冒充"：读到 `buckets.first()` 的人拿到的必须是页数第一。
     */
    @Test fun bucketOrderStaysPageWeightedWhileComicsRideAlong() {
        val stats = aggregateTagStats(
            listOf(
                row("deep", "2026-10-01", 200, "long"),
                row("wide1", "2026-10-01", 2, "short"),
                row("wide2", "2026-10-02", 2, "short"),
                row("wide3", "2026-10-03", 2, "short"),
            ),
            days = 30,
            normalize = normalize,
        )
        assertEquals("long", stats.buckets.first().display)
        assertEquals(200, stats.buckets.first().pages)
        // 而按本念是反过来的：那一本 200 页的深读只算 1 本
        assertEquals(1, bucket(stats, "long").comics)
        assertEquals(3, bucket(stats, "short").comics)
    }

    /** 追漫轨迹那个月的计数是**本数**，不是页数之和。 */
    @Test fun monthlyTopCountsComicsNotPages() {
        val stats = aggregateTagStats(
            listOf(
                row("c1", "2026-09-01", 10, "maid"),
                row("c1", "2026-09-20", 12, "maid"),
                row("c2", "2026-09-05", 4, "maid"),
                row("c3", "2026-08-05", 4, "coffee"),
            ),
            days = 90,
            normalize = normalize,
        )
        val september = stats.monthlyTop.first { it.month == "2026-09" }
        assertEquals(listOf("maid" to 2), september.tags)
        assertEquals("2026-08", stats.monthlyTop[1].month)
    }

    /**
     * 月份多的时候取几档：`days > 30` 给 12 个月，否则 6 个月（这一条与改造前同形），
     * 而且取的是**最近的**那几档 —— 时间轴倒着排，第一行必须是本月/最近那一档。
     */
    @Test fun monthWindowFollowsTheRequestedSpanAndKeepsTheRecentOnes() {
        // 14 个互不相同的月份：2025-01 … 2025-12、2026-01、2026-02
        val rows = (0 until 14).map { i ->
            val year = if (i < 12) 2025 else 2026
            val month = if (i < 12) i + 1 else i - 11
            row("c$i", "%04d-%02d-01".format(year, month), 1, "maid")
        }
        val six = aggregateTagStats(rows, days = 7, normalize = normalize).monthlyTop
        assertEquals(6, six.size)
        assertEquals(listOf("2026-02", "2026-01", "2025-12"), six.map { it.month }.take(3))
        assertEquals(12, aggregateTagStats(rows, days = 365, normalize = normalize).monthlyTop.size)
    }

    /** 下钻用的原值：并列时按字典序，保证同一份数据每次算出的都是同一个搜索词。 */
    @Test fun drillDownOriginalIsDeterministicOnTies() {
        val stats = aggregateTagStats(
            listOf(
                row("c1", "2026-10-01", 5, "b_tie", "a_tie"),
                row("c2", "2026-10-02", 5, "a_tie", "b_tie"),
            ),
            days = 30,
            normalize = normalize,
        )
        val maid = bucket(stats, "a_tie")
        assertEquals("" to "a_tie", maid.searchNamespace to maid.searchRaw)
        assertEquals(2, maid.comics)
    }

    /** 带命名空间的原值要拆成 `(namespace, raw)` 两格 —— 原生源认的是 `female:lolicon` 那一种。 */
    @Test fun namespacedOriginalIsSplitForDrillDown() {
        val stats = aggregateTagStats(
            listOf(row("c1", "2026-10-01", 5, "female:lolicon")),
            days = 30,
            normalize = { raw -> raw.substringAfter(':') },
        )
        val b = stats.buckets.first()
        assertEquals("female", b.searchNamespace)
        assertEquals("lolicon", b.searchRaw)
        assertEquals(1, b.comics)
    }

    @Test fun normalRatingExcludesR18Sources() {
        val stats = aggregateTagStats(
            listOf(
                row("c1", "2026-10-01", 10, "school", sourceName = "copymanga"),
                row("c2", "2026-10-01", 10, "pure_love", sourceName = "jm"),
                row("c3", "2026-10-01", 10, "harem", sourceName = "ehentai"),
            ),
            days = 30,
            rating = ReadingContentRating.NORMAL,
            normalize = normalize,
        )
        assertEquals(listOf("school"), stats.buckets.map { it.display })
        assertEquals(1, stats.taggedComics)
    }

    @Test fun r18RatingExcludesNormalSources() {
        val stats = aggregateTagStats(
            listOf(
                row("c1", "2026-10-01", 10, "school", sourceName = "copymanga"),
                row("c2", "2026-10-01", 10, "pure_love", sourceName = "jm"),
                row("c3", "2026-10-01", 10, "harem", sourceName = "ehentai"),
            ),
            days = 30,
            rating = ReadingContentRating.R18,
            normalize = normalize,
        )
        assertEquals(setOf("pure_love", "harem"), stats.buckets.map { it.display }.toSet())
        assertEquals(2, stats.taggedComics)
    }
}
