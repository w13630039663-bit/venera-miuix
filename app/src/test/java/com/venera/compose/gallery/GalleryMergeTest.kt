package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 两站**日榜**合成一屏的行为锁。
 *
 * 用户 2026-09-25 第四次改判的原话：「（两站上一天的热门）**各取 20 张然后打乱**，
 * 如果 donmai 有 mp4 就引入 mp4」。所以这里钉三件事：每站 20 的上限、
 * 同种子必得同序列（组合重建不能换序，见记忆「导航条目会重建组合」）、
 * 以及视频进屏 / `zip` 这类继续被滤。
 *
 * 上一版钉的是"不严格交替、单源连出 1~3 张"—— 那套交错随本轮一起作废：
 * 打乱之后连出几张是随机序列的自然结果，不再控制。翻页 `exclude` 也一并删了
 * （日榜没有下一页；留着"还有更多"的状态机只会产出「到底了还显示加载更多」那类假象）。
 */
class GalleryMergeTest {

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

    private fun list(site: GallerySite, n: Int, transform: (Int) -> GalleryPost = { post(site, it.toLong()) }) =
        (1..n).map(transform)

    private fun mix(
        yandere: List<GalleryPost> = emptyList(),
        gelbooru: List<GalleryPost> = emptyList(),
        seed: Long = 20260925L,
    ) = GalleryMerge.mix(
        pools = mapOf(GallerySite.YANDERE to yandere, GallerySite.GELBOORU to gelbooru),
        seed = seed,
    )

    @Test
    fun `两站各取 20 张 一屏满量 40`() {
        val merged = mix(list(GallerySite.YANDERE, 40), list(GallerySite.GELBOORU, 200)).getOrThrow()

        assertEquals(40, merged.posts.size)
        assertEquals(mapOf(GallerySite.YANDERE to 20, GallerySite.GELBOORU to 20), merged.perSite)
    }

    @Test
    fun `同一种子必得同一序列`() {
        val yande = list(GallerySite.YANDERE, 40)
        val gelbooru = list(GallerySite.GELBOORU, 200)

        val first = mix(yande, gelbooru, seed = 7L).getOrThrow().posts
        val second = mix(yande, gelbooru, seed = 7L).getOrThrow().posts

        // 点进大图再返回会重跑这段逻辑；两次结果不同就是"整屏顺序变了"。
        assertEquals(first.map { it.uid }, second.map { it.uid })
    }

    @Test
    fun `换种子就换顺序`() {
        val yande = list(GallerySite.YANDERE, 40)
        val gelbooru = list(GallerySite.GELBOORU, 200)

        val a = mix(yande, gelbooru, seed = 1L).getOrThrow().posts.map { it.uid }
        val b = mix(yande, gelbooru, seed = 2L).getOrThrow().posts.map { it.uid }

        // 打乱真在起作用（不是固定顺序的假打乱），「刷新换一批」才是真按钮。
        assertTrue("两个种子的序列完全一致，打乱没生效", a != b)
    }

    @Test
    fun `打乱之后两站是混着的 不是一站连着一站`() {
        val posts = mix(list(GallerySite.YANDERE, 40), list(GallerySite.GELBOORU, 200)).getOrThrow().posts

        val switches = posts.zip(posts.drop(1)).count { (x, y) -> x.site != y.site }
        assertTrue("站点切换只有 $switches 次，不像打乱过的序列", switches >= 10)
    }

    @Test
    fun `mp4 现在要进屏`() {
        val merged = mix(
            yandere = list(GallerySite.YANDERE, 5),
            gelbooru = list(GallerySite.GELBOORU, 20) + post(GallerySite.GELBOORU, 900, ext = "mp4"),
        ).getOrThrow()

        assertTrue(merged.posts.any { it.isVideo })
        assertEquals(1, merged.videos)
    }

    @Test
    fun `zip 这类不可摆的扩展名继续滤掉`() {
        val merged = mix(
            yandere = list(GallerySite.YANDERE, 5),
            gelbooru = list(GallerySite.GELBOORU, 5) + post(GallerySite.GELBOORU, 901, ext = "zip"),
        ).getOrThrow()

        assertEquals(0, merged.posts.count { it.fileExt == "zip" })
        assertEquals(10, merged.posts.size)
        assertEquals(1, merged.droppedUnusable)
    }

    @Test
    fun `没有预览地址的条目不摆`() {
        val merged = mix(
            yandere = list(GallerySite.YANDERE, 5),
            gelbooru = list(GallerySite.GELBOORU, 5) + post(GallerySite.GELBOORU, 902, preview = "  "),
        ).getOrThrow()

        assertEquals(10, merged.posts.size)
    }

    @Test
    fun `同一份内容只留一条`() {
        val byMd5 = mix(
            yandere = list(GallerySite.YANDERE, 1),
            gelbooru = list(GallerySite.GELBOORU, 1) { post(GallerySite.GELBOORU, it.toLong(), md5 = "yandere-1") },
        ).getOrThrow()
        assertEquals(1, byMd5.posts.size)
        assertEquals(1, byMd5.droppedDuplicates)

        val bySource = mix(
            yandere = listOf(post(GallerySite.YANDERE, 1, source = "https://PXIMG.NET/a/123.png")),
            gelbooru = listOf(post(GallerySite.GELBOORU, 2, source = "https://www.pximg.net/a/123.png")),
        ).getOrThrow()
        assertEquals(1, bySource.posts.size)
    }

    @Test
    fun `两站同 id 是两张不同的图`() {
        val posts = mix(
            yandere = list(GallerySite.YANDERE, 2) { post(GallerySite.YANDERE, 4L + it) },
            gelbooru = list(GallerySite.GELBOORU, 2) { post(GallerySite.GELBOORU, 4L + it) },
        ).getOrThrow().posts

        assertEquals(setOf("yandere:5", "yandere:6", "gelbooru:5", "gelbooru:6"), posts.map { it.uid }.toSet())
    }

    @Test
    fun `某站不足 20 条时全进 不硬凑`() {
        val merged = mix(
            yandere = list(GallerySite.YANDERE, 3),
            gelbooru = list(GallerySite.GELBOORU, 200),
        ).getOrThrow()

        assertEquals(23, merged.posts.size)
        assertEquals(3, merged.perSite.getValue(GallerySite.YANDERE))
        assertEquals(20, merged.perSite.getValue(GallerySite.GELBOORU))
    }

    @Test
    fun `站方给了内容却滤光要炸出来 不能交回空屏`() {
        val onlyUnusable = list(GallerySite.YANDERE, 30) { post(GallerySite.YANDERE, it.toLong(), ext = "zip") }

        val failure = mix(yandere = onlyUnusable, gelbooru = list(GallerySite.GELBOORU, 30)).exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure!!.message!!.contains("扩展名白名单判据要复查"))
    }

    @Test
    fun `只剩一站时不报错但也不假装是双源`() {
        val merged = mix(gelbooru = list(GallerySite.GELBOORU, 200)).getOrThrow()

        assertEquals(20, merged.posts.size)
        assertFalse(GallerySite.YANDERE in merged.perSite)
    }

    @Test
    fun `两站都没有内容时是失败而不是空态`() {
        assertTrue(mix().isFailure)
    }

    @Test
    fun `本地重排只在池子真够时放行`() {
        // 批次 K 把「换一批」改成"只换种子、不再联网"（用户 2026-09-30 拍板）。
        // 池子不够时必须回真取一次 —— 按下去屏上纹丝不动就是假按钮，
        // 而冷启动那一屏是从缓存铺出来的，手上压根没有原始池。
        assertFalse(GalleryMerge.canRemix(emptyMap()))
        assertFalse(GalleryMerge.canRemix(mapOf(GallerySite.YANDERE to list(GallerySite.YANDERE, 1))))
        assertFalse(GalleryMerge.canRemix(mapOf(GallerySite.YANDERE to emptyList(), GallerySite.GELBOORU to emptyList())))
        // 只有一站给了两张也算够：另一站这一轮缺席是另一件事，页尾那条读数会说出来。
        assertTrue(GalleryMerge.canRemix(mapOf(GallerySite.YANDERE to list(GallerySite.YANDERE, 2))))
    }
}
