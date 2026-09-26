package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRecommendation
import com.venera.compose.gallery.domain.GalleryRecommendations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * 「按收藏推荐」判据层的单测。
 *
 * 全部围绕四条**错了不会报错**的性质：
 * 1. 池子只由词频决定，且**与收藏的先后顺序无关**（顺序一飘，同一份收藏就会算出另一批标签）；
 * 2. 抽出来的标签必须**在用户收藏里真的共现过**（这是全部质量来源，断了就必须收手）；
 * 3. 同一个种子必得同一个序列（本项目导航条目重建时会重跑这段逻辑）；
 * 4. "没收藏"与"收藏了但算不出"是两种结果，不能都长成空列表。
 */
class GalleryRecommendationsTest {

    private fun fav(site: GallerySite, id: Long, tags: String) =
        GalleryFavorite(siteKey = site.routeKey, id = id, tags = tags)

    private fun setsOf(vararg rows: String): List<List<String>> =
        rows.map { it.split(' ').filter { t -> t.isNotEmpty() } }

    // ── 池子 ──

    @Test
    fun `池子按词频降序，同次数按名字定序`() {
        val sets = setsOf("hat 1girl", "1girl cat", "1girl", "solo")
        val pool = GalleryRecommendations.tagPool(sets, poolSize = 7)
        assertEquals(listOf("1girl", "cat", "hat", "solo"), pool)
    }

    @Test
    fun `收藏顺序变了，池子不变`() {
        // 这条是参考实现没管住的那一处：它只按次数排，次数相同的跟着输入顺序走，
        // 于是"同一份收藏每次算出同一批标签"不成立。
        val a = GalleryRecommendations.tagPool(setsOf("x y", "y z", "z x"))
        val b = GalleryRecommendations.tagPool(setsOf("z x", "y z", "x y"))
        assertEquals(a, b)
    }

    @Test
    fun `池子受 poolSize 截断，且被屏蔽的词进不来`() {
        val sets = setsOf("aaa bbb ccc", "aaa bbb ddd")
        assertEquals(listOf("aaa", "bbb", "ccc", "ddd"), GalleryRecommendations.tagPool(sets, poolSize = 4))
        assertEquals(listOf("aaa", "bbb"), GalleryRecommendations.tagPool(sets, poolSize = 2))
        assertTrue(GalleryRecommendations.tagPool(sets, poolSize = 0).isEmpty())
        val blocked = GalleryRecommendations.tagPool(sets, poolSize = 4) { it == "aaa" }
        assertFalse(blocked.contains("aaa"))
        assertTrue(blocked.contains("bbb"))
    }

    // ── 共现 ──

    @Test
    fun `抽出的每一枚都与已选的在同一张收藏里共现过`() {
        val sets = setsOf("hat 1girl", "1girl cat", "1girl", "solo")
        val pool = GalleryRecommendations.tagPool(sets)
        for (i in 0..200) {
            val picked = GalleryRecommendations.pickTags(sets, pool, selectionSize = 3, random = Random(i.toLong()))
            assertTrue("种子 $i 什么都没抽到", picked.isNotEmpty())
            // 前缀判据：抽到第 k 枚时，必须存在一张收藏同时带着前 k 枚。
            for (k in 1..picked.size) {
                val prefix = picked.take(k).toSet()
                assertTrue(
                    "种子 $i 的第 $k 枚不相干：$picked",
                    sets.any { it.toSet().containsAll(prefix) },
                )
            }
        }
    }

    @Test
    fun `共现断了就少给，不硬凑第 N 枚`() {
        // `c` 与 `a`/`b` 从来没有在同一张收藏里出现过。
        val sets = setsOf("a b", "c")
        val pool = GalleryRecommendations.tagPool(sets)
        for (i in 0..200) {
            val picked = GalleryRecommendations.pickTags(sets, pool, selectionSize = 3, random = Random(i.toLong()))
            assertFalse("种子 $i 把不相干的 c 和 a/b 拼到了一起：$picked", picked.contains("c") && picked.size > 1)
        }
    }

    @Test
    fun `池子里有收藏里不存在的词时不随机糊一枚上去`() {
        // 词频全零 = 没有任何信号。这时候交空表，而不是随便挑一枚装成"推荐出来了"。
        val picked = GalleryRecommendations.pickTags(
            tagSets = setsOf("a b"),
            pool = listOf("zzz"),
            selectionSize = 3,
            random = Random(1),
        )
        assertTrue(picked.isEmpty())
    }

    // ── 可复现 ──

    @Test
    fun `同一个种子两次抽出的序列完全相同`() {
        val sets = setsOf("hat 1girl", "1girl cat", "1girl dress", "solo")
        val pool = GalleryRecommendations.tagPool(sets)
        val first = GalleryRecommendations.pickTags(sets, pool, 3, Random(7L))
        val second = GalleryRecommendations.pickTags(sets, pool, 3, Random(7L))
        assertEquals(first, second)
    }

    @Test
    fun `换种子确实会换一批（随机是活的）`() {
        val sets = setsOf("a b c d e f", "f e d c b a")
        val pool = GalleryRecommendations.tagPool(sets)
        val results = (0..50L).map { GalleryRecommendations.pickTags(sets, pool, 3, Random(it)) }.distinct()
        assertTrue("50 个种子只抽出 ${results.size} 种结果，随机没生效", results.size > 1)
    }

    // ── 三种结果的区分 ──

    @Test
    fun `没有种子与算不出是两种不同的结果`() {
        assertEquals(
            GalleryRecommendation.NoSeeds,
            GalleryRecommendations.recommend(emptyList(), seed = 1L),
        )
        // 有收藏，但标签是空的（站方给过这种行）→ 算不出，不是"没收藏"。
        val blank = listOf(fav(GallerySite.YANDERE, 1, tags = ""))
        assertEquals(
            GalleryRecommendation.NoUsableTags,
            GalleryRecommendations.recommend(blank, seed = 1L),
        )
        // 有标签但全被屏蔽 → 同样要报"算不出"，不能退化成"没收藏"那句引导。
        val blocked = listOf(fav(GallerySite.YANDERE, 2, tags = "naughty_thing"))
        assertEquals(
            GalleryRecommendation.NoUsableTags,
            GalleryRecommendations.recommend(blocked, seed = 1L, isBlocked = { it == "naughty_thing" }),
        )
    }

    @Test
    fun `每站各算各的，不会把另一站的词拼过来`() {
        val favourites = listOf(
            fav(GallerySite.YANDERE, 1, tags = "yandere_only_tag"),
            fav(GallerySite.GELBOORU, 2, tags = "gelbooru_only_tag"),
        )
        val bySite = GalleryRecommendations.recommendBySite(favourites, seed = 3L)
        val yande = bySite.getValue(GallerySite.YANDERE) as GalleryRecommendation.Tags
        val gelbooru = bySite.getValue(GallerySite.GELBOORU) as GalleryRecommendation.Tags
        assertEquals(listOf("yandere_only_tag"), yande.tags)
        assertEquals(listOf("gelbooru_only_tag"), gelbooru.tags)
    }

    @Test
    fun `认不出站点的那条收藏不参与任何一站`() {
        // `GalleryFavorite.site` 对未知站点键回 null（不是"当成某一站"），
        // 硬归会让一串 Gelbooru 标签被打到 yande.re 去搜。
        val junk = GalleryFavorite(siteKey = "no_such_site", id = 9, tags = "whatever_tag")
        val bySite = GalleryRecommendations.recommendBySite(listOf(junk), seed = 5L)
        assertTrue(
            "实际：$bySite",
            bySite.values.all { it is GalleryRecommendation.NoSeeds },
        )
    }
}
