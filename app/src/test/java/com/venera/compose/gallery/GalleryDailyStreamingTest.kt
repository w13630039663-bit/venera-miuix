package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryBoards
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.data.ScoredBoard
import com.venera.compose.gallery.data.YandeReBoard
import com.venera.compose.gallery.domain.GalleryDailyFeed
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「某一站先回来，就先把它交给屏上」—— `GalleryDailyFeed.loadDaily` 的 `onLeg` 那一格。
 *
 * ── 为什么要有这条判据（2026-10-17 第七轮）──
 *
 * 用户那张截图上是「每日热门」一整排骨架，而真机同一条反馈里写着「这个加载时间太长了」。
 * 成因不在并发（三站本来就是 `async` 并发的），而在**整轮要等最慢的那一站**：
 * `Triple(y.await(), g.await(), s.await())` 之后才有第一个结果，慢站一档单站预算 12s。
 * 修法是让"已经到货的那几站"先落屏（UI 侧 `GalleryViewModel.acceptPartial`）。
 *
 * ── 判据怎么保证不飘 ──
 *
 * 慢站不看时间、看闸门（`CompletableDeferred`）：断言"第一次回调里只有快站"时，慢站
 * 一定还没回来 —— 用 `delay(500)` 猜时序的写法在 CI 慢机上必红（本仓栽过）。
 */
class GalleryDailyStreamingTest {

    private fun post(site: GallerySite, id: Long) = GalleryPost(
        site = site,
        id = id,
        rating = "s",
        previewUrl = "https://example.invalid/$site/$id.jpg",
        fileUrl = "https://example.invalid/$site/$id.jpg",
        fileExt = "jpg",
        md5 = "${site.routeKey}-$id",
    )

    /** 快站（yande.re）立刻给两张；慢站（gelbooru）挂在闸门上；第三站这一轮直接失败。 */
    private inner class GatedBoards : GalleryBoards {

        val slowGate = CompletableDeferred<Unit>()

        override val yandere = object : YandeReBoard {
            override val pageSize = 40
            override suspend fun searchPosts(tags: String, page: Int, limit: Int) = Result.success(emptyList<GalleryPost>())
            override suspend fun searchTags(term: String) = Result.success(emptyList<GalleryTagSuggestion>())
            override suspend fun fetchById(id: Long) = Result.success<GalleryPost?>(null)
            override suspend fun artistLinks(name: String) = Result.success(emptyList<String>())
            override suspend fun resolveArtistAlias(name: String) = Result.success<String?>(null)
            override suspend fun fetchDailyPopular() =
                Result.success(listOf(post(GallerySite.YANDERE, 1), post(GallerySite.YANDERE, 2)))
        }

        override val gelbooru = object : ScoredBoard {
            override val pageSize = 100
            override suspend fun searchPosts(tags: String, page: Int, limit: Int) = Result.success(emptyList<GalleryPost>())
            override suspend fun searchTags(term: String) = Result.success(emptyList<GalleryTagSuggestion>())
            override suspend fun fetchById(id: Long) = Result.success<GalleryPost?>(null)
            override suspend fun fetchTopScored(): Result<List<GalleryPost>> {
                slowGate.await()
                return Result.success(listOf(post(GallerySite.GELBOORU, 3)))
            }
        }

        override val safebooru = object : ScoredBoard {
            override val pageSize = 100
            override suspend fun searchPosts(tags: String, page: Int, limit: Int) = Result.success(emptyList<GalleryPost>())
            override suspend fun searchTags(term: String) = Result.success(emptyList<GalleryTagSuggestion>())
            override suspend fun fetchById(id: Long) = Result.success<GalleryPost?>(null)
            override suspend fun fetchTopScored() =
                Result.failure<List<GalleryPost>>(IOException("ConnectException"))
        }
    }

    @Test
    fun `快站先到就先交给屏上，不等慢站`() = runBlocking {
        val boards = GatedBoards()
        val feed = GalleryDailyFeed(boards)
        val calls = CopyOnWriteArrayList<Map<GallerySite, List<GalleryPost>>>()

        val round = async { feed.loadDaily(seed = 7L) { pools -> calls += pools } }

        // 等到至少一次回调（快站落地）。等的是"回调出现"这件事，不是某个固定时长。
        withTimeout(5_000) {
            while (calls.isEmpty()) delay(10)
        }
        val first = calls.first()
        assertEquals("第一次落屏只该有已经到货的那一站", setOf(GallerySite.YANDERE), first.keys)
        assertEquals(2, first.getValue(GallerySite.YANDERE).size)

        // 慢站放行 → 整轮收口，最终结果仍按同一把合并口径（三站都在池里）。
        boards.slowGate.complete(Unit)
        val daily = round.await().getOrThrow()
        assertEquals(3, daily.pools.size)
        assertTrue(daily.failures.containsKey(GallerySite.SAFEBOORU))
        assertTrue("最终那一屏要有内容", daily.merged.posts.isNotEmpty())
    }

    @Test
    fun `不传回调时行为与从前逐字一致`() = runBlocking {
        // 老调用点（单测与桌面探针）不传 onLeg —— 这里钉住"默认参数不改变结果形状"。
        val boards = GatedBoards().also { it.slowGate.complete(Unit) }
        val daily = GalleryDailyFeed(boards).loadDaily(seed = 7L).getOrThrow()
        assertEquals(3, daily.pools.size)
        assertTrue(daily.merged.posts.isNotEmpty())
    }
}
