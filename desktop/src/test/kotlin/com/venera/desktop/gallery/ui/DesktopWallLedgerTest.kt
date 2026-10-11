package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第三十一批 批 31 · 取数链那三本账第一次**真跑**（fake `GalleryPorts`）。
 *
 * ## 补的是哪一格
 *
 * 页码账（逐腿）、去重键、到底名单这三本账从前只有文本判据：批 23 那颗扰动 p3
 * （"页码账另造、恒发第 1 页"）**只红在扫源码那一条上**，那一格没有任何一条判据真跑过。
 * 这一族最坏的缺陷形状恰好是"不崩、不红字、屏上只写这一站没答上"，而**少掉的那一批图没人会去数**
 * （手册 §0 坑 ㉓ 记的就是它）。这一颗把 `fetchWallPage` 整条链跑起来：脚本化的三站墙 + 真 reducer + 真混排。
 *
 * ## 一条都不在这里重定
 *
 * 腿的取舍、下一笔要第几页、一页怎么落地，全部走产品里那三颗转交件
 * （[desktopGalleryBatchLegs] / [desktopGalleryBatchNextPages] / [desktopWallBatchAfter]），
 * 而它们又各自转交共享面的 `nextPagesForAppend` 与 `GallerySearchMerge.interleave`。
 * 本文件断言的是**发出去的那一串页码**与**最后落在墙上的那几张**，不是任何一句字符串。
 */
class DesktopWallLedgerTest {

    private val yandere = GallerySite.YANDERE
    private val safebooru = GallerySite.SAFEBOORU

    private fun post(site: GallerySite, id: Long) = GalleryPost(
        site = site,
        id = id,
        tags = "ledger",
        previewUrl = "https://img/$site-$id.jpg",
        fileExt = "jpg",
    )

    private fun legsOf(vararg sites: GallerySite) = sites.associateWith { "q-${it.name}" }

    private fun firstRound(vararg sites: GallerySite) = DesktopGalleryBatch(
        seed = 0L,
        queries = sites.associateWith { "q-${it.name}" },
    )

    private fun run(ports: GalleryPorts, base: DesktopGalleryBatch, legs: Map<GallerySite, String>) =
        runBlocking { fetchWallPage(ports, base, legs).getOrThrow() }

    private fun contains(what: String, needle: String) = assertTrue("读数是「$what」，里面没有「$needle」", what.contains(needle))

    /**
     * ① 两页重叠时一张不摆两遍。
     *
     * 批 22 的扰动 R1 交出过数字（36 而不是 35），但那是在 reducer 那一层 —— 取数链整条从没跑过。
     */
    @Test
    fun `① 续页重叠的那一张不摆两遍`() {
        val boards = ScriptedBoards(
            pageSize = mapOf(yandere to 2),
            pages = mapOf(
                yandere to listOf(
                    Result.success(listOf(post(yandere, 1), post(yandere, 2))),
                    Result.success(listOf(post(yandere, 2), post(yandere, 3))),
                ),
            ),
        )
        val ports = fakeGalleryPorts(boards)
        val round1 = run(ports, firstRound(yandere), legsOf(yandere))
        val round2 = run(ports, round1, legsOf(yandere))

        assertEquals("第一笔两张", 2, round1.posts.size)
        assertEquals("两页一共四张而其中一张重复 ⇒ 墙上该有三张", 3, round2.posts.size)
        assertEquals("墙上出现了重复条目", 3, round2.posts.map { it.id }.distinct().size)
        assertTrue("重复那一张没被记进账（页尾那句「去掉 N 张跨站重复的」就成了假话）", round2.droppedDuplicates >= 1)
        assertEquals("两笔的页码", listOf(1, 2), boards.calls.map { it.page })
    }

    /**
     * ② 上一笔没答上的那一站，下一笔仍从**第 1 页**发，而答上的那站进第 2 页。
     *
     * 这就是批 8 修掉的那个缺陷（发现屏从前对所有腿发同一个 `page + 1`，超时那一站被跳过第 1 页，
     * **那一页的图永久漏掉**）。共享面从第一天起写着这条判据，桌面从前只有扫源码的尺子。
     */
    @Test
    fun `② 没答上那一站下一笔从第一页补`() {
        val boards = ScriptedBoards(
            pageSize = mapOf(yandere to 2, safebooru to 2),
            pages = mapOf(
                yandere to listOf(
                    Result.success(listOf(post(yandere, 1), post(yandere, 2))),
                    Result.success(listOf(post(yandere, 3), post(yandere, 4))),
                ),
                safebooru to listOf(Result.success(listOf(post(safebooru, 11), post(safebooru, 12)))),
            ),
            failFirst = setOf(safebooru),
        )
        val ports = fakeGalleryPorts(boards)
        val round1 = run(ports, firstRound(yandere, safebooru), legsOf(yandere, safebooru))
        assertEquals("没答上那一站要挂着缺席原因", setOf(safebooru), round1.failures.keys)
        assertEquals("页码账里只该有真答上那一站", setOf(yandere), round1.pageBySite.keys)

        val round2 = run(ports, round1, legsOf(yandere, safebooru))
        val bySite = boards.calls.groupBy { it.site }
        assertEquals("答上那一站第二笔该进第 2 页", listOf(1, 2), bySite.getValue(yandere).map { it.page })
        assertEquals(
            "没答上那一站第二笔仍该是第 1 页（跟着别腿跳页就是永久漏掉那一页）",
            listOf(1, 1),
            bySite.getValue(safebooru).map { it.page },
        )
        assertEquals("墙上该是第一笔两张 + 第二笔四张（重复零张）", 6, round2.posts.size)
        assertEquals("补上的那一站这回该答上了", emptySet<GallerySite>(), round2.failures.keys)
    }

    /** ③ 到底的那一站不再问（「到底」只记真答上过而不足一页的）。 */
    @Test
    fun `③ 到底的那一站不再发车`() {
        val boards = ScriptedBoards(
            pageSize = mapOf(yandere to 4, safebooru to 2),
            pages = mapOf(
                yandere to listOf(Result.success(listOf(post(yandere, 1), post(yandere, 2)))),
                safebooru to listOf(
                    Result.success(listOf(post(safebooru, 11), post(safebooru, 12))),
                    Result.success(listOf(post(safebooru, 13))),
                ),
            ),
        )
        val ports = fakeGalleryPorts(boards)
        val round1 = run(ports, firstRound(yandere, safebooru), legsOf(yandere, safebooru))
        assertEquals("yande.re 两不足四张 ⇒ 该记到底", setOf(yandere), round1.sitesDone)
        assertTrue("Safebooru 刚好给满一页，不该被记到底", safebooru !in round1.sitesDone)

        val round2 = run(ports, round1, legsOf(yandere, safebooru))
        assertTrue("到底那一站被再问了一次", boards.calls.none { it.site == yandere && it.page == 2 })
        assertEquals("第二笔只该问没到底的那一站", listOf(safebooru), boards.calls.filter { it.page == 2 }.map { it.site })
        assertEquals("墙上该是 2 + 2 + 1 张", 5, round2.posts.size)
        assertTrue("两站都到底了而 atEnd 仍为假", round2.atEnd)
    }

    /**
     * ④ `limit` 真吃**这一站**的 `pageSize`（两种值各喂一次）。
     *
     * 只喂一种值等于没喂（手册 §0 坑 ⑲）：写死一枚常数的实现与"逐站取"在一种值下不可分辨。
     */
    @Test
    fun `④ 每腿的 limit 吃这一站自己的档位`() {
        val boards = ScriptedBoards(
            pageSize = mapOf(yandere to 32, safebooru to 18),
            pages = mapOf(
                yandere to listOf(Result.success(listOf(post(yandere, 1)))),
                safebooru to listOf(Result.success(listOf(post(safebooru, 2)))),
            ),
        )
        val ports = fakeGalleryPorts(boards)
        run(ports, firstRound(yandere, safebooru), legsOf(yandere, safebooru))
        val limits = boards.calls.associate { it.site to it.limit }
        assertEquals("yande.re 的 limit 没吃它自己的 pageSize", 32, limits.getValue(yandere))
        assertEquals("Safebooru 的 limit 没吃它自己的 pageSize", 18, limits.getValue(safebooru))
        // 对照：查询串逐腿各发各的（不是拿一枚全局串去问三站）。
        assertEquals(
            "发出去的查询串没逐腿各算",
            setOf("q-YANDERE", "q-SAFEBOORU"),
            boards.calls.map { it.query }.toSet(),
        )
    }

    /**
     * ⑤ 全腿都没答上：墙是空的、逐站点名各自那句原因、而**不叫到底**。
     *
     * "空屏"与"看完了"必须是两句话（`atEnd` 那枚下限写的就是这个）。
     */
    @Test
    fun `⑤ 全腿没答上时空屏不叫到底`() {
        val boards = ScriptedBoards(
            pageSize = mapOf(yandere to 2, safebooru to 2),
            pages = mapOf(
                yandere to listOf(Result.failure(IllegalStateException("这一站超时了"))),
                safebooru to listOf(Result.failure(IllegalStateException("这一站 403"))),
            ),
        )
        val ports = fakeGalleryPorts(boards)
        val batch = run(ports, firstRound(yandere, safebooru), legsOf(yandere, safebooru))
        assertEquals("一笔都没答上而墙上有东西", 0, batch.posts.size)
        assertEquals("缺席要逐站点名", setOf(yandere, safebooru), batch.failures.keys)
        contains(batch.failures.getValue(yandere), "超时")
        contains(batch.failures.getValue(safebooru), "403")
        assertTrue("全腿没答上被判成到底（那是空屏不是看完）", !batch.atEnd)
        assertEquals("没答上的腿不该进到底名单", emptySet<GallerySite>(), batch.sitesDone)
    }

    /**
     * ⑥ 没脚本的成员**真会抛**，而脚本用完那一页也真会抛（这一条是那两颗夹具的牙）。
     *
     * 十一枚端口今天没人吃，如果它们给静默默认值，"哪天有一条路开始吃它"就会悄悄通过；
     * 同理脚本外的页若回空列表，"到底了还在发"那条判据就成了装饰。两处都点名差的是哪一格。
     */
    @Test
    fun `⑥ 没脚本与超出脚本都会抛`() {
        val ports = fakeGalleryPorts(ScriptedBoards(pageSize = emptyMap(), pages = emptyMap()))
        contains(assertThrows(IllegalStateException::class.java) { ports.prefs.galleryBlockAi }.message ?: "", "galleryBlockAi")
        contains(
            assertThrows(IllegalStateException::class.java) { runBlocking { ports.lexicon.artistNames(listOf("anmi")) } }.message ?: "",
            "artistNames",
        )
        contains(assertThrows(IllegalStateException::class.java) { ports.stores.open("gallery") }.message ?: "", "GalleryStoreFactory.open")

        val scripted = ScriptedBoards(
            pageSize = mapOf(yandere to 2),
            pages = mapOf(yandere to listOf(Result.success(emptyList<GalleryPost>()))),
        )
        // 对照：脚本里有的那一页不抛（否则上面几条只是"这颗夹具恒抛"的形状）。
        assertTrue(
            "脚本第 1 页给的是空表，取回却非空",
            runBlocking { scripted.yandere.searchPosts("q", page = 1, limit = 2) }.getOrThrow().isEmpty(),
        )
        contains(
            assertThrows(IllegalStateException::class.java) {
                runBlocking { scripted.yandere.searchPosts("q", page = 2, limit = 2) }
            }.message ?: "",
            "只有 1 页",
        )
    }
}
