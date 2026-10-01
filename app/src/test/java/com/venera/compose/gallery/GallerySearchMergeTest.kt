package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearchMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「全部」档两腿结果的合成判据（批次 K）。
 *
 * 与日榜那套 [com.venera.compose.gallery.domain.GalleryMerge] 的**三处刻意不同**，
 * 每一条都有代价，所以逐条钉住：
 *
 * 1. **站内保持站方顺序、两腿轮转**，不打乱。日榜要的是"一屏多样性"（两站各抽 20 再洗），
 *    而搜索结果的顺序就是相关性顺序 —— 洗一遍会把用户要找的那张冲走，
 *    与推荐墙「抽着取会推得不准」是同一条理由。
 * 2. **两腿都空 = 正常结果**，不炸。搜索搜不到东西是常态；
 *    日榜那头空池才是异常（它一天总有 40 条）。
 * 3. **一整腿被滤光不炸**，交回 `droppedUnusable` 由页尾那句念出来。日榜那头炸（它没有续页可言，
 *    空池就是判据坏了），搜索这头炸会把一整轮换成错误页 —— 用户要的是"那几张坏了"，不是"搜失败"。
 */
class GallerySearchMergeTest {

    private fun post(
        site: GallerySite,
        id: Long,
        ext: String = "jpg",
        md5: String = "${site.routeKey}-$id",
        preview: String = "https://example.invalid/$site/$id.jpg",
    ) = GalleryPost(
        site = site,
        id = id,
        rating = "s",
        previewUrl = preview,
        fileUrl = "https://example.invalid/$site/$id.$ext",
        fileExt = ext,
        md5 = md5,
        source = "",
    )

    private fun legs(
        yandere: List<GalleryPost> = emptyList(),
        gelbooru: List<GalleryPost> = emptyList(),
    ) = mapOf(GallerySite.YANDERE to yandere, GallerySite.GELBOORU to gelbooru)

    private fun merged(
        yandere: List<GalleryPost> = emptyList(),
        gelbooru: List<GalleryPost> = emptyList(),
        seenKeys: Set<String> = emptySet(),
    ): GallerySearchMerge.Legged =
        GallerySearchMerge.interleave(legs(yandere, gelbooru), seenKeys).getOrThrow()

    @Test
    fun `两腿按各自站方顺序轮转混排`() {
        val out = merged(
            yandere = listOf(post(GallerySite.YANDERE, 1), post(GallerySite.YANDERE, 2), post(GallerySite.YANDERE, 3)),
            gelbooru = listOf(post(GallerySite.GELBOORU, 4), post(GallerySite.GELBOORU, 5)),
        )
        // 不许出现"yande 三张连完再来 Gelbooru"—— 那样两站各 20 张时首屏只看得见一个站。
        assertEquals(listOf(1L, 4L, 2L, 5L, 3L), out.posts.map { it.id })
        // 站内相对次序必须原样保留（相关性顺序）。
        assertEquals(listOf(1L, 2L, 3L), out.posts.filter { it.site == GallerySite.YANDERE }.map { it.id })
    }

    @Test
    fun `跨腿重复只留站表序在前的那条并计数`() {
        val out = merged(
            yandere = listOf(post(GallerySite.YANDERE, 1, md5 = "same")),
            gelbooru = listOf(post(GallerySite.GELBOORU, 2, md5 = "same")),
        )
        assertEquals(listOf(1L), out.posts.map { it.id })
        assertEquals(1, out.droppedDuplicates)
    }

    @Test
    fun `上一页见过的键这一页不再上屏`() {
        val first = merged(
            yandere = listOf(post(GallerySite.YANDERE, 1), post(GallerySite.YANDERE, 2)),
            gelbooru = listOf(post(GallerySite.GELBOORU, 3)),
        )
        // 站方翻页窗口滑动时会重发同一批，不去重就会看到"翻一页三张里两张是刚才那两张"。
        val second = merged(
            yandere = listOf(post(GallerySite.YANDERE, 2), post(GallerySite.YANDERE, 9)),
            gelbooru = listOf(post(GallerySite.GELBOORU, 3)),
            seenKeys = first.seenKeys,
        )
        assertEquals(listOf(9L), second.posts.map { it.id })
        assertEquals(2, second.droppedDuplicates)
    }

    @Test
    fun `给了行没给图的不进屏并计入丢弃数`() {
        val out = merged(
            yandere = listOf(post(GallerySite.YANDERE, 1, preview = "")),
            gelbooru = listOf(post(GallerySite.GELBOORU, 2)),
        )
        assertEquals(listOf(2L), out.posts.map { it.id })
        assertEquals(1, out.droppedUnusable)
    }

    @Test
    fun `整腿给的行都没图时不炸 但丢弃数要念得出来`() {
        val out = GallerySearchMerge.interleave(
            legs(
                yandere = listOf(post(GallerySite.YANDERE, 1, preview = ""), post(GallerySite.YANDERE, 2, preview = "")),
                gelbooru = emptyList(),
            ),
        ).getOrThrow()
        // 与日榜那头刻意不同：搜索有既成的"跳过 N 张（站方给了条目但没给可用的图）"那条读数，
        // 这里炸会把一整轮搜索变成错误页，而用户要的是"这一串里那两张坏了"。
        assertTrue(out.posts.isEmpty())
        assertEquals(2, out.droppedUnusable)
    }

    @Test
    fun `两腿都空是正常结果而不是失败`() {
        val out = GallerySearchMerge.interleave(legs())
        // 搜索搜不到东西是常态；这一条与日榜那头刻意相反（日榜空池才是异常）。
        assertTrue(out.isSuccess)
        assertTrue(out.getOrThrow().posts.isEmpty())
    }

    @Test
    fun `每站实际进屏的条数念得出来`() {
        val out = merged(
            yandere = listOf(post(GallerySite.YANDERE, 1), post(GallerySite.YANDERE, 2)),
            gelbooru = listOf(post(GallerySite.GELBOORU, 3)),
        )
        assertEquals(2, out.perSite.getValue(GallerySite.YANDERE))
        assertEquals(1, out.perSite.getValue(GallerySite.GELBOORU))
    }

    @Test
    fun `缺席的那条腿不算到底 到底的那条腿只停它自己`() {
        val limit = mapOf(GallerySite.YANDERE to 100, GallerySite.GELBOORU to 100)
        // 这一轮 Gelbooru 整个缺席（超时/熔断/未配账号都算），不许把它当成"它没货了"永久踢掉。
        val done = GallerySearchMerge.exhaustedSites(
            returned = mapOf(GallerySite.YANDERE to 37),
            limitOf = { limit.getValue(it) },
        )
        assertEquals(setOf(GallerySite.YANDERE), done)

        val both = GallerySearchMerge.exhaustedSites(
            returned = mapOf(GallerySite.YANDERE to 37, GallerySite.GELBOORU to 100),
            limitOf = { limit.getValue(it) },
        )
        assertEquals(setOf(GallerySite.YANDERE), both)
    }
}
