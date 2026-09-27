package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryForYouMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 猜你喜欢**一页**的合成行为锁。
 *
 * 这一层要钉的全是"错了不会报错、只会静默长丑"的性质：
 * 排掉已收藏（不排就是把自己的收藏顶回来，读起来像坏了）、跨页去重（不去重就是滑半天只见同一张）、
 * 以及"滤完 0 条"的两种成因必须分叉（把正当的空炸成技术错误，或把判据的错说成正当的空，
 * 两种都是假读数）。
 *
 * builder 抄 `GalleryMergeTest.kt:26` 那一份：本站本地、不上 Robolectric，只用纯函数。
 */
class GalleryForYouMergeTest {

    private fun post(
        site: GallerySite,
        id: Long,
        ext: String = "jpg",
        md5: String = "${site.routeKey}-$id",
        source: String = "",
        preview: String = "https://example.invalid/$site/$id.jpg",
    ) = GalleryPost(
        site = site,
        id = id,
        rating = "s",
        previewUrl = preview,
        fileUrl = "https://example.invalid/$site/$id.$ext",
        fileExt = ext,
        md5 = md5,
        source = source,
    )

    private fun list(site: GallerySite, n: Int, from: Long = 1) =
        (0 until n).map { post(site, from + it) }

    private fun page(
        yandere: List<GalleryPost> = emptyList(),
        gelbooru: List<GalleryPost> = emptyList(),
        seed: Long = 20260928L,
        page: Int = 1,
        seenKeys: Set<String> = emptySet(),
        favouriteUids: Set<String> = emptySet(),
    ) = GalleryForYouMerge.page(
        pools = mapOf(GallerySite.YANDERE to yandere, GallerySite.GELBOORU to gelbooru),
        seed = seed,
        page = page,
        seenKeys = seenKeys,
        favouriteUids = favouriteUids,
    ).getOrThrow()

    @Test
    fun `排除收藏里的图 且数量报得出来`() {
        val favourites = setOf("yandere:2", "gelbooru:1")

        val p = page(
            yandere = list(GallerySite.YANDERE, 3),
            gelbooru = list(GallerySite.GELBOORU, 2),
            favouriteUids = favourites,
        )

        assertEquals(2, p.excludedFavourite) // yandere:2 与 gelbooru:1 各一张
        assertEquals(
            listOf("yandere:1", "yandere:3", "gelbooru:2").sorted(),
            p.posts.map { it.uid }.sorted(),
        )
    }

    @Test
    fun `收藏 uid 与帖子 uid 同形 否则排除判据恒不命中`() {
        // 收藏存的是 siteKey（routeKey 串），帖子算的是 site.routeKey —— 两处一旦漂移，
        // "排掉已收藏"就变成一条永远不成立的判据，屏上照样摆回那几张，而且谁都不报错。
        GallerySite.entries.forEach { site ->
            val fav = GalleryFavorite(siteKey = site.routeKey, id = 77L)
            val post = post(site, 77L)
            assertEquals(fav.uid, post.uid)
        }
    }

    @Test
    fun `跨页去重 第 2 页不见第 1 页见过的 uid`() {
        // 站方第 2 页真的会重给同一条（同一串标签、不同 pid 撞上同图），不去重就是滑半天只见同一张。
        val first = list(GallerySite.YANDERE, 5)
        val seen = first.flatMap { GalleryForYouMerge.keysOf(it) }.toSet()

        val second = page(yandere = first + list(GallerySite.YANDERE, 3, from = 6), seenKeys = seen)

        // 进屏的是哪些条是判据，**顺序不是**（整页按 seed 打乱，见下面那条"同 seed 同序列"）。
        assertEquals(setOf(6L, 7L, 8L), second.posts.map { it.id }.toSet())
        assertEquals(5, second.droppedDuplicates)
    }

    @Test
    fun `md5 或出处命中也算重复 即便 id 不同`() {
        val anchor = post(GallerySite.YANDERE, 1, md5 = "shared-md5", source = "shared-src")

        val p = page(
            yandere = listOf(
                anchor,
                post(GallerySite.YANDERE, 2, md5 = "shared-md5"),
            ),
            // 3 号与锚点 id 不同、md5 也不同，但**上游出处相同** —— 跨站也算同一张。
            gelbooru = listOf(
                post(GallerySite.GELBOORU, 3, md5 = "other", source = "shared-src"),
                post(GallerySite.GELBOORU, 4, md5 = "another"),
            ),
        )

        assertEquals(setOf(1L, 4L), p.posts.map { it.id }.toSet())
        assertEquals(2, p.droppedDuplicates)
    }

    @Test
    fun `每站最多 20 张 站内保持站方顺序`() {
        val p = page(yandere = list(GallerySite.YANDERE, 100), gelbooru = list(GallerySite.GELBOORU, 100))

        assertEquals(40, p.posts.size)
        assertEquals(20, p.perSite.getValue(GallerySite.YANDERE))
        assertEquals(20, p.perSite.getValue(GallerySite.GELBOORU))
        // 站内不许随机抽着取：搜出来的第 1 条是最匹配的，抽掉了屏上就读成"推得不准"。
        // 这里能钉住的是"每站进屏的那 20 条确实是各自的前 20 条"。
        val bySite = p.posts.groupBy { it.site }
        assertEquals((1L..20L).toSet(), bySite.getValue(GallerySite.YANDERE).map { it.id }.toSet())
        assertEquals((1L..20L).toSet(), bySite.getValue(GallerySite.GELBOORU).map { it.id }.toSet())
    }

    @Test
    fun `同一串标签跨站撞图时 站序决定谁留下`() {
        val p = page(
            yandere = listOf(post(GallerySite.YANDERE, 1, md5 = "same")),
            gelbooru = listOf(post(GallerySite.GELBOORU, 2, md5 = "same")),
        )

        // GallerySite.entries 的顺序就是先去重的优先序；不钉这条，HashMap 顺序会让结果跟着飘。
        assertEquals(listOf(1L), p.posts.map { it.id })
        assertEquals(1, p.droppedDuplicates)
    }

    @Test
    fun `一站到底只停那一站 另一站继续`() {
        val limit = mapOf(GallerySite.YANDERE to 100, GallerySite.GELBOORU to 100)
        val done = GalleryForYouMerge.exhaustedSites(
            returned = mapOf(GallerySite.YANDERE to 37, GallerySite.GELBOORU to 100),
            limitOf = { limit.getValue(it) },
        )

        // 混成一个全局标志就会出"A 站还能出货、页尾写着到底了"的假读数。
        assertEquals(setOf(GallerySite.YANDERE), done)

        val bothDone = GalleryForYouMerge.exhaustedSites(
            returned = mapOf(GallerySite.YANDERE to 37, GallerySite.GELBOORU to 12),
            limitOf = { limit.getValue(it) },
        )
        assertEquals(2, bothDone.size)
    }

    @Test
    fun `这一轮没给数的站不算到底`() {
        // 超时 / 熔断 / 未配账号都会让一站这一轮没给数。把它当"到底"，一次抖动就永久踢掉这一站，
        // 屏上从此只剩一站 —— 而且谁都不报错。缺席的原因归 failures 说，不归到底判据管。
        val done = GalleryForYouMerge.exhaustedSites(
            returned = mapOf(GallerySite.YANDERE to 100),
            limitOf = { 100 },
        )

        assertEquals(setOf<GallerySite>(), done)
    }

    @Test
    fun `没有可用图片地址的行不计进屏 计入 droppedUnusable`() {
        val p = page(
            yandere = listOf(
                post(GallerySite.YANDERE, 1, preview = ""),
                post(GallerySite.YANDERE, 2, ext = "zip", md5 = "z2"),
                post(GallerySite.YANDERE, 3),
            ),
        )

        assertEquals(listOf(3L), p.posts.map { it.id })
        assertEquals(2, p.droppedUnusable)
    }

    @Test
    fun `给了内容被判据滤光要炸出来 不返回空成功`() {
        val broken = GalleryForYouMerge.page(
            pools = mapOf(GallerySite.YANDERE to listOf(post(GallerySite.YANDERE, 1, ext = "zip", md5 = "z")), GallerySite.GELBOORU to emptyList()),
            seed = 1L,
            page = 1,
            seenKeys = emptySet(),
            favouriteUids = emptySet(),
        )

        // 站方明明给了行、滤完 0 条 = 白名单那把判据漏档了，这是我们的缺陷，必须炸。
        assertTrue(broken.isFailure)
        assertTrue(broken.exceptionOrNull()!!.message!!.contains("判据要复查"))
    }

    @Test
    fun `被已收藏剔光是正当空态 不炸`() {
        val favourites = setOf("yandere:1")

        val p = GalleryForYouMerge.page(
            pools = mapOf(GallerySite.YANDERE to listOf(post(GallerySite.YANDERE, 1)), GallerySite.GELBOORU to emptyList()),
            seed = 1L,
            page = 1,
            seenKeys = emptySet(),
            favouriteUids = favourites,
        ).getOrThrow()

        // 这一档不是"坏了"，是"口味已经全收完了" —— 页尾那档空态要说这句话，不能报技术错误。
        assertTrue(p.posts.isEmpty())
        assertEquals(1, p.excludedFavourite)
    }

    @Test
    fun `同一 seed 同一页必得同一序列 换了页就换节奏`() {
        val pools = mapOf(
            GallerySite.YANDERE to list(GallerySite.YANDERE, 10),
            GallerySite.GELBOORU to list(GallerySite.GELBOORU, 10),
        )
        fun uids(seed: Long, page: Int) = GalleryForYouMerge
            .page(pools, seed, page, emptySet(), emptySet())
            .getOrThrow().posts.map { it.uid }

        // 组合重建（点进大图再返回）会重跑这段：裸 Random 会让整屏换序。
        assertEquals(uids(7L, 1), uids(7L, 1))
        assertNotEquals(uids(7L, 1), uids(8L, 1))
        // 同一种子下第 1 页与第 2 页的打乱流必须错开，否则两页看着像没换内容。
        assertNotEquals(uids(7L, 1), uids(7L, 2))
    }

    @Test
    fun `视频进屏并且数得出来`() {
        val p = page(
            yandere = listOf(post(GallerySite.YANDERE, 1, ext = "mp4", md5 = "v1"), post(GallerySite.YANDERE, 2)),
        )

        assertEquals(2, p.posts.size)
        assertEquals(1, p.videos)
    }
}
