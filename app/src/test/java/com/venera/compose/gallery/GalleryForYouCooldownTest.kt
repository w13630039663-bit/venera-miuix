package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryForYouCooldown
import com.venera.compose.gallery.domain.GalleryForYouMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「换一批」冷却环与随机深度的行为锁（2026-11-03，用户「换一批总是一模一样，出现过的过几轮再出现」）。
 *
 * builder 抄 `GalleryForYouMergeTest.kt` 那一份：本站本地、不上 Robolectric，只用纯函数。
 */
class GalleryForYouCooldownTest {

    private fun post(site: GallerySite, id: Long, ext: String = "jpg") = GalleryPost(
        site = site,
        id = id,
        rating = "s",
        previewUrl = "https://example.invalid/$site/$id.jpg",
        fileUrl = "https://example.invalid/$site/$id.$ext",
        fileExt = ext,
        md5 = "${site.routeKey}-$id",
        source = "",
    )

    private fun list(site: GallerySite, n: Int, from: Long = 1) =
        (0 until n).map { post(site, from + it) }

    @Test
    fun `冷却环只留最近三批`() {
        var rounds = emptyList<List<String>>()
        repeat(5) { i -> rounds = GalleryForYouCooldown.pushRound(rounds, listOf("k$i")) }
        assertEquals(listOf(listOf("k2"), listOf("k3"), listOf("k4")), rounds)
    }

    @Test
    fun `空批不进环`() {
        // 「换一批」按下但屏上本来没卡（首屏失败态）时，不许往环里塞一个空轮把三档名额吃掉一格。
        val rounds = GalleryForYouCooldown.pushRound(listOf(listOf("k0")), emptyList())
        assertEquals(listOf(listOf("k0")), rounds)
    }

    @Test
    fun `池子被冷却吃光时松开重排 不摆空屏`() {
        val pool = list(GallerySite.YANDERE, 6) + list(GallerySite.GELBOORU, 4)
        val first = GalleryForYouMerge.page(
            pools = mapOf(GallerySite.YANDERE to pool.filter { it.site == GallerySite.YANDERE }, GallerySite.GELBOORU to pool.filter { it.site == GallerySite.GELBOORU }),
            seed = 1L,
            page = 1,
            seenKeys = emptySet(),
            favouriteUids = emptySet(),
        ).getOrThrow()
        val rounds = GalleryForYouCooldown.pushRound(
            emptyList(),
            first.posts.flatMap { GalleryForYouMerge.keysOf(it) },
        )

        val second = GalleryForYouCooldown.page(
            pools = mapOf(GallerySite.YANDERE to pool.filter { it.site == GallerySite.YANDERE }, GallerySite.GELBOORU to pool.filter { it.site == GallerySite.GELBOORU }),
            seed = 2L,
            page = 1,
            seenKeys = emptySet(),
            favouriteUids = emptySet(),
            rounds = rounds,
        ).getOrThrow()

        // 池子 10 条全被上一批吃掉 → 赦免兜底放开重排，第二屏**允许**再出现（不许摆空）。
        assertTrue("池子被冷却吃光时必须松开而不是空屏", second.posts.isNotEmpty())
    }

    @Test
    fun `池子够大时冷却真的挡得住`() {
        val yandere = list(GallerySite.YANDERE, 30, from = 1)
        val round1 = GalleryForYouMerge.page(
            pools = mapOf(GallerySite.YANDERE to yandere.take(10), GallerySite.GELBOORU to emptyList()),
            seed = 1L, page = 1, seenKeys = emptySet(), favouriteUids = emptySet(),
        ).getOrThrow()
        val rounds = GalleryForYouCooldown.pushRound(
            emptyList(), round1.posts.flatMap { GalleryForYouMerge.keysOf(it) },
        )

        val round2 = GalleryForYouCooldown.page(
            pools = mapOf(GallerySite.YANDERE to yandere, GallerySite.GELBOORU to emptyList()),
            seed = 7L, page = 1, seenKeys = emptySet(), favouriteUids = emptySet(), rounds = rounds,
        ).getOrThrow()

        val cooled = round1.posts.map { it.uid }.toSet()
        assertTrue("第一批摆过的条目不该原样再摆", round2.posts.map { it.uid }.none { it in cooled })
        assertEquals(20, round2.posts.size) // 池 30 剔掉冷却里那 10 张，余 20 恰好铺满每站每页上限
    }

    @Test
    fun `同一 seed 同一站的深度是确定的`() {
        // 组合销毁重建（点进大图再返回）不许换一轮页深 —— 与 merge 的 seed 口径同一件事。
        val a = GalleryForYouCooldown.depthPages(42L, GallerySite.YANDERE.ordinal)
        val b = GalleryForYouCooldown.depthPages(42L, GallerySite.YANDERE.ordinal)
        assertEquals(a, b)
        assertTrue(a in 0..GalleryForYouCooldown.MAX_DEPTH_PAGES)
    }

    @Test
    fun `activeKeys 空表给空集`() {
        assertEquals(emptySet<String>(), GalleryForYouCooldown.activeKeys(emptyList()))
    }
}
