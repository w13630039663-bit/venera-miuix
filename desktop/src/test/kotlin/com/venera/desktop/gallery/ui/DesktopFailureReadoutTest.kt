package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GelbooruClient
import com.venera.compose.gallery.data.GelbooruCredentials
import com.venera.compose.gallery.data.SafebooruClient
import com.venera.compose.gallery.data.ScoredBoard
import com.venera.compose.gallery.data.YandeReBoard
import com.venera.compose.gallery.data.YandeReClient
import com.venera.compose.gallery.domain.GalleryDailyFeed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3/S4 的核心判据：**`failures` 与界面上"缺席站"那句读数必须逐字相等**。
 *
 * ## 这条用例要拦的失败形态
 *
 * 三站混搭静默退化成一两站刷屏 —— 那是本仓最忌的静默交错：用户看到一屏图，不知道少了两个站，
 * 于是把"这一轮只有 40 张"当成正常。防线是两层，且**两层都要有**：
 * - 取数层（[GalleryDailyFeed]）把每站没给内容的原因交在 `failures` 里；
 * - 界面层把缺席名单**逐字取自 `failures.keys`**，不自己数、不自己判哪站算缺。
 *
 * ## 为什么前一版是自证的
 *
 * 前一版写成 `val renderedSites = failures` 再断言相等 —— 无论生产代码怎么坏它都绿。
 * 现在这两条都打真 client（[FakeHttpEngine] 掐在 `HttpEngine` 那一层，只是不真的出去），
 * 所以"`failures` 少一站"或"界面名单多一站"都会当场红。
 */
class DesktopFailureReadoutTest {

    /**
     * 三站都答上：进屏的每站都有货，`failures` **必须为空**。
     *
     * 反向的失败形态是"把成功也报成缺席"（那会让用户以为有站坏了），
     * 所以这条与下面那条是**双向**的：不多报、不少报。
     */
    @Test
    fun `三站都答上时 failures 必须为空`() = runBlocking {
        val feed = dailyFeed(gelbooru = 200)
        val daily = feed.loadDaily(seed = 7L).getOrThrow()

        assertEquals("三站都答上就不许报缺席", emptySet<Any>(), daily.failures.keys.toSet())
        assertTrue("三站都答上时至少要有图进屏", daily.merged.posts.isNotEmpty())
    }

    /**
     * 匿名跑一轮：缺席名单**恰好是 Gelbooru 一站**，且带上 401 那句原因。
     *
     * 钉的是"缺席要说清是哪一站 + 为什么"，而不只是"缺席了" ——
     * 少一站（静默少一站）在这里红，名单里多一站也在这里红。
     */
    @Test
    fun `匿名时缺席名单恰好是 Gelbooru 且带 401 原因`() = runBlocking {
        val feed = dailyFeed(gelbooru = 401)
        val daily = feed.loadDaily(seed = 7L).getOrThrow()

        assertEquals(
            "缺席名单必须恰好是 Gelbooru 一站（多了少了都是静默交错）",
            setOf("Gelbooru"),
            daily.failures.keys.map { it.displayName }.toSet(),
        )
        assertTrue(
            "缺席必须点名 401 那句原因，实际：${daily.failures.values}",
            daily.failures.values.any { it.contains("401") || it.contains("没接受") },
        )
    }

    /**
     * 两站同时缺席：名单是**两枚**，一字不少。
     *
     * 这是最容易写成"只报第一个失败"的地方 —— `async` 三腿里哪一腿先失败就先落哪一条，
     * 于是"三站挂了两站"读起来像"挂了一站"。所以这条钉的是集合大小。
     */
    @Test
    fun `两站缺席时名单是两枚不是一枚`() = runBlocking {
        val feed = dailyFeed(gelbooru = 401, safebooru = 500)
        val daily = feed.loadDaily(seed = 7L).getOrThrow()

        assertEquals(
            "缺席要两枚都报出来",
            setOf("Gelbooru", "Safebooru"),
            daily.failures.keys.map { it.displayName }.toSet(),
        )
    }

    /**
     * 三站全没货 ⇒ `loadDaily` **整体失败**，且失败消息里带着那三句原因。
     *
     * 这条钉的是"优先把原因交出去"那条（`GalleryDailyFeed.kt:87-90`）：
     * 全空时若只交一句笼统的"没有可用内容"，真正的原因（401 / 超时 / 池空）就死在里面了。
     */
    @Test
    fun `三站全没货时整体失败且消息里带三句原因`() = runBlocking {
        val feed = dailyFeed(gelbooru = 401, safebooru = 500, yandeRe = 503)
        val result = feed.loadDaily(seed = 7L)

        assertTrue("三站全没货必须是失败而不是空墙", result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        listOf("Gelbooru", "Safebooru", "yande.re").forEach { site ->
            assertTrue("失败消息要点名 $site ：$message", message.contains(site))
        }
    }

    /**
     * 各站进屏条数与缺席名单是**两笔独立读数**，缺席的一站不进 `perSite`。
     *
     * 界面上那句「进屏 N 张 · 各站：x 张」不能把缺席站按 0 算进去 ——
     * 那会让"Gelbooru 0 张"和"没取到 Gelbooru"读起来一样，正是本仓最忌的那句合并。
     */
    @Test
    fun `缺席站不进各站条数表`() = runBlocking {
        val feed = dailyFeed(gelbooru = 401)
        val daily = feed.loadDaily(seed = 7L).getOrThrow()

        assertTrue("缺席站不该出现在 perSite 里", !daily.merged.perSite.containsKey(
            com.venera.compose.gallery.data.GallerySite.GELBOORU,
        ))
        assertEquals(2, daily.merged.perSite.size)
    }

    /**
     * 装一颗三站都答上的 feed。
     *
     * 走的是**真 client**（`FakeHttpEngine` 只掐掉 socket），所以 `GalleryMerge` 的合并、
     * `GalleryLegGuard` 的失败翻话、client 的解信封全部是生产代码。
     * 缺席那几站按 [gelbooru] / [safebooru] / [yandeRe] 指定的 HTTP 状态码触发。
     */
    private fun dailyFeed(
        gelbooru: Int,
        safebooru: Int = 200,
        yandeRe: Int = 200,
    ): GalleryDailyFeed {
        val engine = FakeHttpEngine.fresh {
            on("https://gelbooru.com/index.php?page=dapi&s=post", gelbooru, GELBOORU_POOL_BODY)
            on("https://safebooru.donmai.us/posts.json", safebooru, SAFEBOORU_POOL_BODY)
            on("https://yande.re/post/popular_recent.json", yandeRe, YANDERE_POPULAR_BODY)
        }

        return GalleryDailyFeed(TestBoards(engine))
    }

    /**
     * 契约面那一层的三颗 board 实现 —— **不引入第二份假实现**。
     *
     * 这里复刻 `DesktopGalleryBoards` 的形状（真 client + 真凭据注入），
     * 而不是另写三个"直接返回构造好的 List"的方法：那会跳过
     * `fetchTopScored` / `fetchDailyPopular` / 解信封 / 401 翻话整条路，
     * 于是"`failures` 与缺席名单对不对得上"这条判据就退化成自证。
     */
    private class TestBoards(private val engine: FakeHttpEngine) :
        com.venera.compose.gallery.data.GalleryBoards {

        private val credentials: GelbooruCredentials = FakeHttpEngine.AnonymousCredentials

        override val yandere: YandeReBoard = object : YandeReBoard {
            private val client: YandeReClient get() = YandeReClient.getInstance(engine)
            override val pageSize: Int get() = YandeReClient.SEARCH_PAGE_SIZE
            override suspend fun searchPosts(tags: String, page: Int, limit: Int) =
                client.searchPosts(tags, page, limit)
            override suspend fun searchTags(term: String) = client.searchTags(term)
            override suspend fun fetchById(id: Long) = client.fetchById(id)
            override suspend fun artistLinks(name: String) = client.artistLinks(name)
            override suspend fun resolveArtistAlias(name: String) = client.resolveArtistAlias(name)
            override suspend fun fetchDailyPopular() = client.fetchDailyPopular()
        }

        override val gelbooru: ScoredBoard = object : ScoredBoard {
            private val client: GelbooruClient get() = GelbooruClient(engine, credentials)
            override val pageSize: Int get() = GelbooruClient.POOL_SIZE
            override suspend fun searchPosts(tags: String, page: Int, limit: Int) =
                client.searchPosts(tags, page, limit)
            override suspend fun searchTags(term: String) = client.searchTags(term)
            override suspend fun fetchById(id: Long) = client.fetchById(id)
            override suspend fun fetchTopScored() = client.fetchTopScored()
        }

        override val safebooru: ScoredBoard = object : ScoredBoard {
            private val client: SafebooruClient get() = SafebooruClient.getInstance(engine)
            override val pageSize: Int get() = SafebooruClient.POOL_SIZE
            override suspend fun searchPosts(tags: String, page: Int, limit: Int) =
                client.searchPosts(tags, page, limit)
            override suspend fun searchTags(term: String) = client.searchTags(term)
            override suspend fun fetchById(id: Long) = client.fetchById(id)
            override suspend fun fetchTopScored() = client.fetchTopScored()
        }
    }

    private companion object {
        // ⚠️ 三站的 body 都**必须**给 `file_ext` / `preview_url` 两枚，缺一就会被
        // `GalleryMerge.isDisplayable`（`GalleryMerge.kt:58`：扩展名在白名单 **且** 缩略图非空）
        // 当成"给了行但没给可摆的图"滤掉，然后 `GalleryMerge.kt:100` 那句
        // 「滤完 0 条可用」当场抛 —— 那条抛得对：站方给了内容而我们一条没摆，是判据错了。
        // 写假 body 时漏掉这两枚，报错读起来像"merge 有 bug"，真因是 fixture 不全。
        const val GELBOORU_POOL_BODY =
            """{"@attributes":{"limit":100,"offset":0,"count":14304678},"post":[
              {"id":14970719,"tags":"rating:g solo","score":31144,"rating":"general",
               "owner":"someone","md5":"abc","file_ext":"png",
               "file_url":"https://i.example/g.png",
               "preview_url":"https://i.example/g-prev.png","width":800,"height":1000}
            ]}"""

        const val YANDERE_POPULAR_BODY =
            """[{"id":37,"tags":"rating:g","score":120,"rating":"g","author":"an",
               "file_ext":"png","preview_url":"https://i.example/y.png",
               "file_url":"https://i.example/y-full.png","width":800,"height":600}]"""

        const val SAFEBOORU_POOL_BODY =
            """[{"id":9000001,"tag_string":"rating:g solo","score":50,"rating":"g",
               "tag_string_artist":"an","fav_count":12,"file_ext":"jpg",
               "preview_file_url":"https://i.example/s.png",
               "file_url":"https://i.example/s-full.png","image_width":800,"image_height":600}]"""
    }
}
