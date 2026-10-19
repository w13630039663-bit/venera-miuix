package com.venera.compose.gallery

import com.venera.compose.gallery.data.PixivFeedParse
import com.venera.compose.gallery.domain.PixivWorkFact
import com.venera.compose.gallery.domain.PixivLockReason
import com.venera.compose.gallery.domain.PixivWorkPage
import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv **作品页**那一腿的读数与三格纯判据（2026-10-18）。
 *
 * 夹具**不是编的**：两份都从 2026-10-18 本机现量的匿名答复里裁下来
 * （真号 150320368 —— `ajax/illust/150320368` 与 `ajax/illust/150320368/pages`），
 * 只删掉与判据无关的键。留真值的理由与 `PixivFeedParseTest` 同一族：
 * 编出来的夹具只会通过编出来的解析。
 */
class PixivWorkPageDataTest {

    /** 详情腿：`illustId`/`userId` 是 string、`pageCount`/`xRestrict`/`viewCount` 是 number、标签内层键叫 `tag`。 */
    private val detailBody = """
        {"error":false,"message":"","body":{
          "illustId":"150320368","id":"150320368","title":"[CHARA] Kakkoi Elbertson",
          "illustType":0,"createDate":"2026-10-01T06:12:00+00:00","uploadDate":"2026-10-01T06:12:00+00:00",
          "restrict":0,"xRestrict":0,"sl":2,
          "urls":{"mini":"https://i.pximg.net/c/48x48/img-master/img/2026-10-01/15/12/34/150320368_p0_square1200.jpg",
                  "thumb":"https://i.pximg.net/c/250x250_80_a2/img-master/img/2026-10-01/15/12/34/150320368_p0_square1200.jpg",
                  "regular":"https://i.pximg.net/img-master/img/2026-10-01/15/12/34/150320368_p0_master1200.jpg",
                  "original":"https://i.pximg.net/img-original/img/2026-10-01/15/12/34/150320368_p0.png"},
          "tags":{"authorId":"12956047","isLocked":false,"writable":false,"tags":[
            {"tag":"コイカツ","locked":true,"userId":"12956047"},
            {"tag":"東方Project","locked":true,"userId":"12956047"},
            {"tag":"東方","locked":true,"userId":"12956047"},
            {"tag":"キャラ配布(コイカツ)","locked":true,"userId":"12956047"},
            {"tag":"よその子","locked":true,"userId":"12956047"},
            {"tag":"gensokyoodyssey","locked":true,"userId":"12956047"},
            {"tag":"kakkoi","locked":true,"userId":"12956047"}]},
          "userId":"12956047","userName":"Big Red","userAccount":"jaymonoto",
          "pageCount":4,"width":1365,"height":920,
          "bookmarkCount":32,"likeCount":19,"commentCount":6,"viewCount":851,
          "commentOff":0,"aiType":1,"seriesNavData":null,"bookmarkData":null}}
    """.trimIndent()

    /**
     * 多页腿：`body` **本身就是数组**（四枚，各带 urls/width/height），没有 `meta_pages` 那一层。
     * 四条地址就是 2026-10-18 现量回来的那四条（`_p0`…`_p3`，扩展名 `.png`）；
     * 第 0 枚带全 `thumb_mini` 那一档，证明"每枚元素是一张页"而不是"一页套一层键"。
     */
    private val fourPageUrls = (0..3).map {
        "https://i.pximg.net/img-original/img/2026-10-01/15/12/34/150320368_p$it.png"
    }

    private fun pagesBodyWith(urls: List<String>): String {
        val items = urls.mapIndexed { index, url ->
            val square = if (index == 0) {
                "\"thumb_mini\":\"https://i.pximg.net/c/128x128/a/img-master/img/2026-10-01/15/12/34/150320368_p0_square1200.jpg\","
            } else {
                ""
            }
            "{\"urls\":{$square\"original\":\"$url\"},\"width\":1365,\"height\":920}"
        }.joinToString(",")
        return """{"error":false,"message":"","body":[$items]}"""
    }

    /** 一页的答复形状：哪一格缺就传 null（**整键不写**，不是写空串 —— 站方缺键就是缺键）。 */
    private class PageFixture(val regular: String?, val original: String?, val width: Int?, val height: Int?)

    private fun tieredBody(pages: List<PageFixture>): String {
        val items = pages.joinToString(",") { page ->
            val tiers = buildList {
                page.regular?.let { add("\"regular\":\"$it\"") }
                page.original?.let { add("\"original\":\"$it\"") }
            }.joinToString(",")
            val dims = buildList {
                page.width?.let { add("\"width\":$it") }
                page.height?.let { add("\"height\":$it") }
            }.joinToString(",")
            "{\"urls\":{$tiers}${if (dims.isEmpty()) "" else ","}$dims}"
        }
        return """{"error":false,"message":"","body":[$items]}"""
    }

    private fun regularUrl(index: Int) = "https://i.pximg.net/img-master/img/2026-10-01/15/12/34/150320368_p$index-master1200.jpg"

    /**
     * ① 每一页要**两档**：中档给这一屏连读，原图档只给播放器那一层的 HD。
     *
     * 立案读数（用户 2026-10-18 真机）："加载图片应该用低清点的图片" —— 从前这一腿只取
     * `urls.original`，作品页一开屏就在拉原图。
     */
    @Test
    fun `多页腿每页带中档与原图两档，中档不是原图`() {
        val pages = PixivFeedParse.workPages(
            tieredBody(
                listOf(
                    PageFixture(regularUrl(0), fourPageUrls[0], 1365, 920),
                    PageFixture(regularUrl(1), fourPageUrls[1], 1365, 920),
                ),
            ),
            expected = 2,
        )
        assertEquals("中档那一排取的不是站方 regular", listOf(regularUrl(0), regularUrl(1)), pages.map { it.mediumUrl })
        assertEquals("原图档取的不是站方 original", fourPageUrls.take(2), pages.map { it.originalUrl })
    }

    /** ② 宽高是"图回来之前按比例占位"的唯一依据，缺键就交 null，不许替站方编一个 1:1。 */
    @Test
    fun `每页宽高跟着读，缺键那页给 null 而不是编一个比例`() {
        val pages = PixivFeedParse.workPages(
            tieredBody(
                listOf(
                    PageFixture(regularUrl(0), fourPageUrls[0], 1365, 920),
                    PageFixture(regularUrl(1), fourPageUrls[1], null, null),
                ),
            ),
            expected = 2,
        )
        assertEquals(Pair(1365, 920), Pair(pages[0].width, pages[0].height))
        assertEquals("站方没报宽高的一页不许被编成正方", Pair(null, null), Pair(pages[1].width, pages[1].height))
    }

    /** ③ 只给一档时**退回那一档**而不是丢页：丢一枚就是"4 页只画 3 页"那一族假话。 */
    @Test
    fun `只给一档的那一页退回那一档，两档同串但不丢页`() {
        val pages = PixivFeedParse.workPages(
            tieredBody(
                listOf(
                    PageFixture(regular = null, original = fourPageUrls[0], width = 10, height = 20),
                    PageFixture(regular = regularUrl(1), original = null, width = 10, height = 20),
                ),
            ),
            expected = 2,
        )
        assertEquals(2, pages.size)
        assertEquals("缺中档 ⇒ 两档同用原图那一串", fourPageUrls[0], pages[0].mediumUrl)
        assertEquals(fourPageUrls[0], pages[0].originalUrl)
        assertEquals("缺原图 ⇒ 两档同用中档那一串", regularUrl(1), pages[1].originalUrl)
        assertEquals(regularUrl(1), pages[1].mediumUrl)
    }

    /** ④ 两档都没有才算丢页 —— 对账仍然要抛，且说得出两头各是几。 */
    @Test
    fun `两档都没有的那一页算丢页，对账抛而不静默少画`() {
        try {
            PixivFeedParse.workPages(
                tieredBody(
                    listOf(
                        PageFixture(regularUrl(0), fourPageUrls[0], 10, 20),
                        PageFixture(regular = null, original = null, width = 10, height = 20),
                    ),
                ),
                expected = 2,
            )
            throw AssertionError("空的那一页也被当成了合法答复")
        } catch (expected: java.io.IOException) {
            assertTrue(
                "报错要说得出对账的两头，实际=${expected.message}",
                expected.message?.contains("一共 2 页") == true && expected.message?.contains("1 页") == true,
            )
        }
    }

    @Test
    fun `多页腿：body 是数组，四枚按序、扩展名从尾巴取（这一作是 png）`() {
        assertEquals(
            listOf("_p0.png", "_p1.png", "_p2.png", "_p3.png"),
            PixivFeedParse.workPages(pagesBodyWith(fourPageUrls), 4).map { it.originalUrl.substringAfterLast("150320368") },
        )
    }

    @Test
    fun `多页腿少给一页就抛：不把「4 页只回 3 页」读成「这一作只有 3 页」`() {
        try {
            PixivFeedParse.workPages(pagesBodyWith(fourPageUrls.dropLast(1)), 4)
            throw AssertionError("少一页也被当成了合法答复")
        } catch (expected: java.io.IOException) {
            assertTrue(
                "报错要说得出对账的两头，实际=${expected.message}",
                expected.message!!.contains("4") && expected.message!!.contains("3"),
            )
        }
    }

    @Test
    fun `详情腿：作品页要的那几格逐枚读得回来（真号 150320368 现量值）`() {
        val head = PixivFeedParse.workDetailPage(detailBody)!!
        assertEquals("[CHARA] Kakkoi Elbertson", head.title)
        assertEquals(4, head.pageCount)
        // 同一份读数也落进**共用那颗行模型**：卡片上那枚「4P」与页轴播放器都吃它，
        // 只留在 `PixivWorkDetail` 上的话，卡那一侧就永远拿不到页数（要另开一条腿才能知道）。
        assertEquals(4, head.post.pageCount)
        assertEquals(851, head.viewCount)
        assertEquals("2026-10-01T06:12:00+00:00", head.uploadDate)
        assertEquals(6, head.commentCount)
        assertEquals(false, head.commentsOff)
        assertEquals(false, head.ugoira)
        assertEquals(12956047L, head.authorId)
        assertEquals("jaymonoto", head.authorAccount)
        // 标签逐枚、顺序照站方、带括号那一枚不被劈开（并成空格串再切就会劈）
        assertEquals(
            listOf("コイカツ", "東方Project", "東方", "キャラ配布(コイカツ)", "よその子", "gensokyoodyssey", "kakkoi"),
            head.tags,
        )
        // 共用那颗行模型继续吃同一份读数：收藏数与 AI 硬位不另开一本账
        assertEquals(32, head.post.favCount)
        assertEquals(false, head.post.siteAiFlag)
        assertEquals("general", head.post.rating)
    }

    /**
     * 骨架那一格按什么比例摆：只认站方报的宽高。
     *
     * 退化输入（0 / 负 / 缺任一格）一律交 null = **不预留**，而不是算出一个 Infinity 或 1:1：
     * 前者会让骨架撑满整屏，后者会在图落位那一刻把整屏顶一下（用户读到的就是"这屏在抽风"）。
     */
    @Test
    fun `占位比例只由站方报的宽高算，缺格或退化值就不预留`() {
        assertEquals(1365f / 920f, PixivWorkPage.placeholderAspect(1365, 920)!!, 0.0001f)
        assertNull(PixivWorkPage.placeholderAspect(null, 920))
        assertNull(PixivWorkPage.placeholderAspect(1365, null))
        assertNull(PixivWorkPage.placeholderAspect(0, 920))
        assertNull(PixivWorkPage.placeholderAspect(1365, 0))
        assertNull(PixivWorkPage.placeholderAspect(-3, -3))
    }

    @Test
    fun `详情腿只到第 0 页：多页时 pages 是一枚，取齐由第二跳负责`() {
        val head = PixivFeedParse.workDetailPage(detailBody)!!
        assertEquals(listOf("https://i.pximg.net/img-original/img/2026-10-01/15/12/34/150320368_p0.png"), head.pages.map { it.originalUrl })
        // 单页那一枚也不能一上来就只带原图：详情腿自己就有 `regular` 与 `width`/`height`。
        assertEquals(
            "详情腿的中档取的是站方 urls.regular（作品页摆这一档）",
            listOf("https://i.pximg.net/img-master/img/2026-10-01/15/12/34/150320368_p0_master1200.jpg"),
            head.pages.map { it.mediumUrl },
        )
        assertEquals(Pair(1365, 920), Pair(head.pages.single().width, head.pages.single().height))
    }

    @Test
    fun `多页腿形状不对也抛：app-api 那族的 meta_pages 键名不许套到这一族`() {
        try {
            PixivFeedParse.workPages("""{"error":false,"body":{"meta_pages":[]}}""", 4)
            throw AssertionError("body 不是数组也被当成了合法答复")
        } catch (expected: java.io.IOException) {
            assertTrue(expected.message!!.contains("不是数组"))
        }
    }

    @Test
    fun `三格计数：站方没给的那格整格掉出去，不摆 0 也不摆占位`() {
        assertEquals(
            listOf(PixivWorkFact.VIEWS, PixivWorkFact.BOOKMARKS, PixivWorkFact.PUBLISHED),
            PixivWorkPage.facts(851, 32, "2026-10-01T06:12:00+00:00").map { it.kind },
        )
        assertEquals(listOf("851", "32"), PixivWorkPage.facts(851, 32, null).map { it.text })
        assertEquals(
            listOf(PixivWorkFact.BOOKMARKS),
            PixivWorkPage.facts(null, 32, "这一格站方给坏了").map { it.kind },
        )
        assertTrue(PixivWorkPage.facts(null, null, null).isEmpty())
        // 0 是真读数（刚发布的作品），不许被当成"没有"抹掉
        assertEquals(2, PixivWorkPage.facts(0, 0, null).size)
    }

    @Test
    fun `发布日只到日且换到本机时区：坏形与缺键都交 null`() {
        assertNull(PixivWorkPage.publishedDay(null))
        assertNull(PixivWorkPage.publishedDay(""))
        assertNull(PixivWorkPage.publishedDay("2026-10-01"))
        val day = PixivWorkPage.publishedDay("2026-10-01T20:00:00+00:00")!!
        assertEquals(
            OffsetDateTime.parse("2026-10-01T20:00:00+00:00")
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDate().toString(),
            day,
        )
    }

    @Test
    fun `标签同屏 8 枚是上限不是目标：不足 8 枚时 hidden 恒 0（更多把手不许出现）`() {
        val seven = PixivFeedParse.workDetailPage(detailBody)!!.tags
        assertEquals(7, seven.size)
        assertEquals(0, PixivWorkPage.tagsHidden(seven))
        assertEquals(seven, PixivWorkPage.tagsShown(seven, expanded = false))

        val twelve = (seven + listOf("水着", "3D", "CHARA", "原创", "加工可")).take(12)
        assertEquals(4, PixivWorkPage.tagsHidden(twelve))
        assertEquals(8, PixivWorkPage.tagsShown(twelve, expanded = false).size)
        assertEquals(twelve, PixivWorkPage.tagsShown(twelve, expanded = true))
        // 恰好 8 枚：还是不许出把手
        assertEquals(0, PixivWorkPage.tagsHidden(twelve.take(8)))
    }

    // ── 第四批·第七件（用户 2026-10-18「看下 pixiv 为啥有报错图片看不了」）─────

    /**
     * 「答上了却没有图」在站方那一份答复里其实是**四种不同的事**（现量键位：匿名详情腿 body 的
     * `restrict` / `xRestrict` / `isLoginOnly` / `isUnlisted`）。从前它们与"真被删"盖成同一句
     * 「被删或被锁」，用户读到的是"我这明明登录了怎么说被删"。分档才有救：
     * 前三档在**登录腿**下都能取到图，只有最后一档才是真的没有。
     */
    @Test
    fun `锁因分四档，各自念得出是哪一面墙`() {
        // 私密（restrict=1）优先于分级：作者自己关了的那一条，念"R-18"就是骂人
        assertEquals(PixivLockReason.PRIVATE, PixivWorkPage.lockReason(false, 0, 1, false))
        assertEquals(PixivLockReason.PRIVATE, PixivWorkPage.lockReason(true, 1, 1, true))
        // 不公开列出（isUnlisted）单独一档：它说的是"作者不让它在站方列表里出现"，
        // 与"只有本人能看"（restrict=1）不是一回事，念错方向会误导用户去开登录。
        assertEquals(PixivLockReason.UNLISTED, PixivWorkPage.lockReason(false, 0, 0, true))
        // 私密那一档压过其余：restrict=1 时别的旗标怎样都不必再看
        assertEquals(PixivLockReason.PRIVATE, PixivWorkPage.lockReason(true, 1, 1, true))
        // 分级（xRestrict=1 R-18 / =2 R-18G）在其余两档之后
        assertEquals(PixivLockReason.ADULT, PixivWorkPage.lockReason(false, 1, 0, false))
        assertEquals(PixivLockReason.ADULT, PixivWorkPage.lockReason(false, 2, 0, false))
        // 登录限定（isLoginOnly）：不是分级，是"仅登录可见"
        assertEquals(PixivLockReason.LOGIN_ONLY, PixivWorkPage.lockReason(true, 0, 0, false))
        // 一枚都没竖 ⇒ 这一条不是被锁，是没有（被删 / 号错了 / 审核中）
        assertNull(PixivWorkPage.lockReason(false, 0, 0, false))
        // 站方这一格压根没给（可空）不能当成"锁着" —— 缺键与置位是两句话
        assertNull(PixivWorkPage.lockReason(null, null, null, null))
    }

    /** 每档的屏上措辞都要念得出**下一步做什么**，不只念成因。 */
    @Test
    fun `四档锁因各有一句能念的话，且不是一句通用兜底`() {
        val words = PixivLockReason.entries.map { it.sentence }
        assertEquals("四档四句话（两档同句就是没分开）", PixivLockReason.entries.size, words.toSet().size)
        words.forEach { w -> assertTrue("那句太短，念不出下一步：$w", w.length >= 8) }
        assertTrue("登录限定那一档要念出登录", PixivLockReason.LOGIN_ONLY.sentence.contains("登录"))
        assertTrue("分级那一档要念出成人向", PixivLockReason.ADULT.sentence.contains("成人"))
    }

}
