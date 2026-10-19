package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryBoards
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.data.ScoredBoard
import com.venera.compose.gallery.data.YandeReBoard
import com.venera.compose.gallery.domain.GalleryDailyFeed
import com.venera.compose.gallery.domain.GalleryEmptyRound
import com.venera.compose.gallery.domain.GalleryNoticeAction
import com.venera.compose.gallery.domain.GalleryNoticeKind
import com.venera.compose.gallery.domain.GalleryNoticeTone
import com.venera.compose.gallery.domain.galleryNotices
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 整轮取数失败时「屏上说得出话」的判据（2026-10-15，问题清单 P0-1）。
 *
 * ── 这条缺陷长什么样 ──
 *
 * 画廊首页断网冷启动：屏上铺的是离线缓存那一屏，而整页那面"加载失败"视图的门写在
 * `error != null && !hasPosts` 之后 —— 有卡，所以错误视图上不了屏；而当时**唯一**能
 * 上屏的失败位置（网格首行那排按档提示）吃的是 `GalleryViewModel.failures`，
 * 那份结构只在成功那一轮被 `accept()` 写过。于是失败轮在屏上是**零说法**：
 * 用户读到"这几站没图"，实情是没网。（真机读数：`_qa/ui-verify-2026-10-15/scr-a8-gallery.png`，
 * 语义树里与状态相关的文本只有 `标签 / 画师 / 作品` 与一条上一轮留下的推荐空态。）
 *
 * ── 为什么尺子立在 domain 而不是 UI ──
 *
 * 根因是**发车那一处把已经算出来的结构丢了**（`GalleryDailyFeed` 全空分支只交出一句
 * 拼好的串）。修复也是在那一处：交出 [GalleryEmptyRound]，页面与提示件一行没改。
 * 所以这里钉的是"结构带得出来 + 归得了档 + 给得了重试"这三件事，全是纯 JVM 判据；
 * 而"屏上真的出现了那一排"要真机成对截图补（本机无设备，见交付说明）。
 */
class GalleryRoundFailureReadoutTest {

    /** 三站都发不出车的墙：每一笔只回一个异常，页面侧不该看到任何卡。 */
    private class AllLegsDown : GalleryBoards {
        override val yandere = object : YandeReBoard {
            override val pageSize = 40
            override suspend fun searchPosts(tags: String, page: Int, limit: Int) =
                Result.failure<List<GalleryPost>>(IOException("Unable to resolve host"))

            override suspend fun searchTags(term: String) =
                Result.failure<List<GalleryTagSuggestion>>(IOException("Unable to resolve host"))

            override suspend fun fetchById(id: Long) = Result.failure<GalleryPost?>(IOException("no route"))
            override suspend fun artistLinks(name: String) = Result.failure<List<String>>(IOException("no route"))
            override suspend fun resolveArtistAlias(name: String) = Result.failure<String?>(IOException("no route"))
            override suspend fun fetchDailyPopular() =
                Result.failure<List<GalleryPost>>(IOException("Unable to resolve host \"g21022.gelbooru.com\""))
        }

        override val gelbooru = object : ScoredBoard {
            override val pageSize = 100
            override suspend fun searchPosts(tags: String, page: Int, limit: Int) =
                Result.failure<List<GalleryPost>>(IOException("Connection reset"))

            override suspend fun searchTags(term: String) =
                Result.failure<List<GalleryTagSuggestion>>(IOException("Connection reset"))

            override suspend fun fetchById(id: Long) = Result.failure<GalleryPost?>(IOException("no route"))
            override suspend fun fetchTopScored() = Result.failure<List<GalleryPost>>(IOException("Connection reset"))
        }

        override val safebooru = object : ScoredBoard {
            override val pageSize = 100
            override suspend fun searchPosts(tags: String, page: Int, limit: Int) =
                Result.failure<List<GalleryPost>>(IOException("ConnectException"))

            override suspend fun searchTags(term: String) =
                Result.failure<List<GalleryTagSuggestion>>(IOException("ConnectException"))

            override suspend fun fetchById(id: Long) = Result.failure<GalleryPost?>(IOException("no route"))
            override suspend fun fetchTopScored() = Result.failure<List<GalleryPost>>(IOException("ConnectException"))
        }
    }

    /** 一、两站答上且给了行，另一站挂掉 —— 用来对照"整轮"与"缺一腿"不是一件事。 */
    private fun post(site: GallerySite, id: Long) = GalleryPost(
        site = site,
        id = id,
        previewUrl = "https://example.invalid/$id.jpg",
        fileExt = "jpg",
    )

    /**
     * 「每一站都要有自己的原因」与「这条通知点名了哪几站」里的**那一排站**。
     *
     * 指的是**每日热门这台机器真打的那几腿**，不是 [GallerySite] 的枚举全集 —— 写成字面量是**故意的**：
     * 2026-10-16 pixiv 进了枚举但**不进**每日热门（选型⑤：pixiv 只从首页那一格=内容域进，
     * 另吃 `ranking.php` 那条榜单腿与 `ajax/search/artworks/{词}` 那一跳）。
     * 以后谁把第四站接进这台机器却忘了改这里，这把尺子要红并逼一次表态，
     * 而不是拿 `entries.toSet()` 自动把它算成"欠一个原因"（那正是本批一开始量到的那两枚红）。
     */
    private val dailyFeedLegs = setOf(GallerySite.YANDERE, GallerySite.GELBOORU, GallerySite.SAFEBOORU)

    @Test
    fun `三站全没货时交出的那笔失败带着按站原因`() = runBlocking {
        val result = GalleryDailyFeed(AllLegsDown()).loadDaily(seed = 7L)

        assertTrue("三站全没货必须是失败而不是空墙", result.isFailure)
        val e = result.exceptionOrNull()
        assertTrue(
            "失败必须是 GalleryEmptyRound，否则按站那份结构在半路又丢了：$e",
            e is GalleryEmptyRound,
        )
        val reasons = (e as GalleryEmptyRound).reasons
        assertEquals("每一站都要有自己的原因：${reasons.keys}", dailyFeedLegs, reasons.keys)
        reasons.values.forEach {
            assertTrue("原因不许是空串（空串等于没说过）：$reasons", it.isNotBlank())
        }
    }

    @Test
    fun `失败消息仍逐站点名（与桌面那把旧尺子同读一条）`() {
        // 换类型不换读数：desktop 侧 `DesktopFailureReadoutTest` 钉的是 message 里点名三站。
        val reasons = GallerySite.entries.associateWith { "${it.displayName} 连不上" }
        val message = GalleryEmptyRound(reasons).message.orEmpty()
        GallerySite.entries.forEach {
            assertTrue("失败消息要点名 ${it.displayName}：$message", message.contains(it.displayName))
        }
    }

    @Test
    fun `整轮失败的原因归成一档出错并给得出重试`() = runBlocking {
        val reasons = (GalleryDailyFeed(AllLegsDown()).loadDaily(seed = 7L)
            .exceptionOrNull() as GalleryEmptyRound).reasons

        val notices = galleryNotices(reasons)

        // 三站同属"没答上"这一档，按站表序并成一条 —— 不是一句合并后的"部分站点无结果"。
        assertEquals(1, notices.size)
        val notice = notices.single()
        assertEquals(GalleryNoticeKind.LegFailed, notice.kind)
        assertEquals(GalleryNoticeTone.Error, notice.tone)
        assertEquals("失败必须给得出路，出路就是重试", GalleryNoticeAction.Retry, notice.action)
        // 同 :103 那一条的口径：这条通知覆盖的是**每日热门真打的那三腿**，不是枚举全集。
        // pixiv 于 2026-10-16 进枚举但不进这台机器（选型⑤ 走首页那一格=内容域，另吃 `ranking.php`），
        // 所以它不该出现在这条通知里 —— 用字面量写死，将来接进这台机器时必须改这里并留一句为什么。
        assertEquals(dailyFeedLegs.toList(), notice.sites)
        // 正文逐站念原始原因，一个字都不合并（合并就读不出是哪一站、也读不出为什么）。
        dailyFeedLegs.forEach {
            assertTrue("正文要点名 ${it.displayName}：${notice.body}", notice.body.contains(it.displayName))
        }
    }

    @Test
    fun `缺一腿不算整轮失败`() = runBlocking {
        // 对照档：两站有货时 loadDaily 必须成功，缺席那站进 failures —— 本批没动这条形状，
        // 它保证我改的那一支不是把"部分缺席"也升成了整轮失败。
        val boards = object : GalleryBoards {
            override val yandere = object : YandeReBoard {
                override val pageSize = 40
                override suspend fun searchPosts(tags: String, page: Int, limit: Int) =
                    Result.success(listOf(post(GallerySite.YANDERE, 1)))

                override suspend fun searchTags(term: String) = Result.success(emptyList<GalleryTagSuggestion>())
                override suspend fun fetchById(id: Long) = Result.success<GalleryPost?>(null)
                override suspend fun artistLinks(name: String) = Result.success(emptyList<String>())
                override suspend fun resolveArtistAlias(name: String) = Result.success<String?>(null)
                override suspend fun fetchDailyPopular() =
                    Result.success(listOf(post(GallerySite.YANDERE, 1), post(GallerySite.YANDERE, 2)))
            }

            override val gelbooru = object : ScoredBoard {
                override val pageSize = 100
                override suspend fun searchPosts(tags: String, page: Int, limit: Int) =
                    Result.success(listOf(post(GallerySite.GELBOORU, 1)))

                override suspend fun searchTags(term: String) = Result.success(emptyList<GalleryTagSuggestion>())
                override suspend fun fetchById(id: Long) = Result.success<GalleryPost?>(null)
                override suspend fun fetchTopScored() =
                    Result.success(listOf(post(GallerySite.GELBOORU, 3), post(GallerySite.GELBOORU, 4)))
            }

            override val safebooru = object : ScoredBoard {
                override val pageSize = 100
                override suspend fun searchPosts(tags: String, page: Int, limit: Int) =
                    Result.failure<List<GalleryPost>>(IOException("403"))

                override suspend fun searchTags(term: String) =
                    Result.failure<List<GalleryTagSuggestion>>(IOException("403"))

                override suspend fun fetchById(id: Long) = Result.failure<GalleryPost?>(IOException("403"))
                override suspend fun fetchTopScored() = Result.failure<List<GalleryPost>>(IOException("403"))
            }
        }

        val daily = GalleryDailyFeed(boards).loadDaily(seed = 7L).getOrThrow()

        assertEquals("只缺 Safebooru 一站", setOf(GallerySite.SAFEBOORU), daily.failures.keys)
    }
}
