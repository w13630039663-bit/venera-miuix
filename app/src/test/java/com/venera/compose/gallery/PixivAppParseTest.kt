package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivAppParse
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * `app-api` 那一侧的形状解析（2026-10-17 第五轮，用户「下面推荐应该自动换成 pixiv 站方的推荐」）。
 *
 * 判据只喂**样本 JSON**、不发网：这一层要回答的是"站方答了这样一份东西，我们读出了什么"。
 * 真正的端点可不可达只能上机，那是 `PixivAppClient` 的事（它连状态码都不在这一层判）。
 *
 * 三条口径与本仓其余解析件同源（`PixivFeedParse` 类注释那张表）：
 * 1. 整份读不懂 = 抛；
 * 2. 有 N 条却一条都认不出 = 也抛；
 * 3. `illusts: []` 是**合法空表**（站方答上了"这一轮没有"）。
 */
class PixivAppParseTest {

    /** 两份真实形状的样本：一份单页（带 `meta_single_page`），一份多页（`meta_pages`）。 */
    private val sample = """
        {
          "illusts": [
            {
              "id": 150822243,
              "title": "海辺の午後",
              "type": "illust",
              "image_urls": {
                "px_128x128": "https://i.pximg.net/c/128x128/img-master/a_p0_square1200.jpg",
                "px_360x360": "https://i.pximg.net/c/360x360_70/img-master/a_p0_square1200.jpg",
                "px_540x540": "https://i.pximg.net/c/540x540_70/img-master/a_p0_master1200.jpg",
                "square_medium": "https://i.pximg.net/c/360x360_70/img-master/a_p0_square1200.jpg",
                "medium": "https://i.pximg.net/c/540x540_70/img-master/a_p0_master1200.jpg",
                "large": "https://i.pximg.net/c/1200x1200/img-master/a_p0_master1200.jpg"
              },
              "user": { "id": 12345, "name": "繪師A", "account": "a" },
              "tags": [ { "name": "風景" }, { "name": "海" } ],
              "width": 1200,
              "height": 1600,
              "page_count": 1,
              "x_restrict": 0,
              "total_bookmarks": 4321,
              "meta_single_page": { "original_image_url": "https://i.pximg.net/img-original/img/a_p0.png" },
              "meta_pages": []
            },
            {
              "id": 150926978,
              "title": "三枚組",
              "type": "illust",
              "image_urls": {
                "medium": "https://i.pximg.net/c/540x540_70/img-master/b_p0_master1200.jpg",
                "large": "https://i.pximg.net/c/1200x1200/img-master/b_p0_master1200.jpg"
              },
              "user": { "id": 987, "name": "繪師B", "account": "b" },
              "tags": [ { "name": "オリジナル" } ],
              "width": 900,
              "height": 1200,
              "page_count": 3,
              "x_restrict": 1,
              "total_bookmarks": 12,
              "meta_single_page": {},
              "meta_pages": [
                { "image_urls": { "original": "https://i.pximg.net/img-original/img/b_p0.jpg" } },
                { "image_urls": { "original": "https://i.pximg.net/img-original/img/b_p1.jpg" } }
              ]
            }
          ],
          "next_url": "https://app-api.pixiv.net/v1/illust/recommended?offset=30"
        }
    """.trimIndent()

    @Test
    fun `站方推荐读出条数、分级、收藏数与三档图址`() {
        val works = PixivAppParse.illustrationRecommended(sample).works
        assertEquals(2, works.size)

        val first = works[0]
        assertEquals(GallerySite.PIXIV, first.post.site)
        assertEquals(150822243L, first.post.id)
        assertEquals("海辺の午後", first.title)
        // 站方推荐**没有名次**：编一个"这一排里的第几枚"当排名就是假读数。
        assertNull(first.rank)
        // 缩略取 `medium`（540 宽），不是 `square_medium`（方裁，塞进瀑布流会变形）。
        assertEquals("https://i.pximg.net/c/540x540_70/img-master/a_p0_master1200.jpg", first.post.previewUrl)
        assertEquals("https://i.pximg.net/c/1200x1200/img-master/a_p0_master1200.jpg", first.post.largeUrl)
        // 单页：原图在 `meta_single_page.original_image_url`，扩展名从地址尾巴取（逐张不同）。
        assertEquals("https://i.pximg.net/img-original/img/a_p0.png", first.post.fileUrl)
        assertEquals("png", first.post.fileExt)
        assertEquals("general", first.post.rating)
        assertEquals(4321, first.post.favCount)
        assertEquals("繪師A", first.post.author)
        assertEquals("https://www.pixiv.net/users/12345", first.post.source)
        assertEquals("風景 海", first.post.tags)
    }

    @Test
    fun `多页组图取第 0 页的原图，不是拿 large 冒充`() {
        val second = PixivAppParse.illustrationRecommended(sample).works[1]
        assertEquals("https://i.pximg.net/img-original/img/b_p0.jpg", second.post.fileUrl)
        // `x_restrict = 1` ⇒ r18（本仓既有的那张翻译表）。
        assertEquals("r18", second.post.rating)
    }

    /** 登录腿那一格叫 `page_count`（数字）—— 与匿名榜腿那枚**字符串** `illust_page_count` 不同形。 */
    @Test
    fun `登录腿的 page_count 也落到行模型（卡上那枚角标在登录那一侧同样有出处）`() {
        val works = PixivAppParse.illustrationRecommended(sample).works
        assertEquals(1, works[0].post.pageCount)
        assertEquals(3, works[1].post.pageCount)
    }

    @Test
    fun `空表是合法答复，不是坏读数`() {
        assertTrue(PixivAppParse.illustrationRecommended("""{"illusts":[]}""").works.isEmpty())
    }

    @Test
    fun `有 N 条却一条都认不出必须炸，不许静默交空表`() {
        // 站方改键名的表现就是"整墙消失"；那与"这一轮真的没有推荐"必须能分开。
        val renamed = """{"illusts":[{"illust_id":1,"image_url":"x"},{"illust_id":2,"image_url":"y"}]}"""
        try {
            PixivAppParse.illustrationRecommended(renamed)
            fail("应当抛：两条都认不出作品号与图址")
        } catch (e: IOException) {
            // 消息里的字是「认得出」（"没有一条认得出作品号与图址"）—— 判据照抄那个字，
            // 别自己换个近义词，否则测的是措辞而不是行为。
            assertTrue(e.message.orEmpty().contains("认得出"))
        }
    }

    @Test
    fun `app-api 的 error 对象要念出站方那句原因，而不是一句读不懂`() {
        val error = """
            {"error":{"user_message":"","message":"","reason":"rate limit","status_code":400}}
        """.trimIndent()
        try {
            PixivAppParse.illustrationRecommended(error)
            fail("应当抛")
        } catch (e: IOException) {
            val message = e.message.orEmpty()
            assertTrue("要带状态码：$message", message.contains("400"))
            assertTrue("要带站方那句原因：$message", message.contains("rate limit"))
        }
    }

    @Test
    fun `不是 JSON 就炸，并把开头截进消息里`() {
        try {
            PixivAppParse.illustrationRecommended("<html>风控</html>")
            fail("应当抛")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("不是 JSON"))
        }
    }

    // ── 翻页与榜单（2026-10-17 第七轮）────────────────────────────────────────

    @Test
    fun `下一页地址原样带出来，供无限下拉接着取`() {
        val page = PixivAppParse.illustrationRecommended(sample)
        // ⚠️ 原样，不是我们拼的：站方那串自己带 offset 与 filter，
        // 我们重组一次就等于替它猜分页口径。
        assertEquals("https://app-api.pixiv.net/v1/illust/recommended?offset=30", page.nextUrl)
    }

    @Test
    fun `没有 next_url 就是到底了，不是下一页未知`() {
        val page = PixivAppParse.illustrationRecommended(
            """{"illusts":[],"next_url":null}""",
        )
        assertNull(page.nextUrl)
    }

    @Test
    fun `榜单读出站方给的名次（推荐那一腿必须没有）`() {
        val ranked = """
            {"illusts":[{"id":1,"title":"一","image_urls":{"medium":"https://i.pximg.net/m.jpg"},
             "user":{"id":9,"name":"a"},"tags":[],"width":10,"height":20,"x_restrict":0,"rank":1}],
             "next_url":"https://app-api.pixiv.net/v1/illust/ranking?mode=day&offset=50"}
        """.trimIndent()
        val page = PixivAppParse.illustrationRanking(ranked)
        assertEquals(1, page.works.size)
        assertEquals(1, page.works[0].rank)
        // 同一份内容走推荐那一条时不许冒出名次：那一位是榜单腿的读数，不是这张图的属性。
        assertNull(PixivAppParse.illustrationRecommended(ranked).works[0].rank)
    }

    @Test
    fun `榜单答复缺 illusts 时说的是榜单，不是推荐`() {
        try {
            PixivAppParse.illustrationRanking("""{"illusts":[]}""".replace("illusts", "works"))
            fail("应当抛")
        } catch (e: IOException) {
            assertTrue("消息要点名是哪条腿：${e.message}", e.message.orEmpty().contains("榜单"))
        }
    }

    // ── 第四批·第七件（2026-10-18）：作品页的**登录腿**详情 ─────────────────

    /**
     * 样本按 [illustrationRecommended] 那份**同一个 illust 对象**的形状拼（app-api 的
     * `/v1/illust/detail` 答复里就是这一颗对象，外面套一层 `illust`）：列表腿的键表在本文件
     * 上面那笔用例里已经逐枚量过，这里只多三格只有作品页要的 —— `total_view` / `create_date` / 逐页 `meta_pages`。
     *
     * ⚠️ 这一份**不是**站方原文截来的：本机没有票据（`app-api` 不带 Bearer 现量回 400
     * 「Error occurred at the OAuth process」），所以真读数只能等包内回读（记在手册行 231 的待真机那一格）。
     * 键表写错的后果不是崩，是"这一腿永远读不出图"，而调用点在它读不出时**退回匿名腿** ⇒ 屏上不会变坏，
     * 只会保持本轮之前的样子。这一条也写进了 `PixivAppClient.illustDetail` 的头注。
     */
    private val detailSample = """
        {
          "illust": {
            "id": 150320368,
            "title": "[CHARA] Kakkoi Elbertson",
            "type": "illust",
            "image_urls": {
              "thumb": "https://i.pximg.net/c/250x250/img-master/a_p0_master1200.jpg",
              "small": "https://i.pximg.net/c/164x164/img-master/a_p0_master1200.jpg",
              "medium": "https://i.pximg.net/c/360x360_70/img-master/a_p0_master1200.jpg",
              "large": "https://i.pximg.net/c/1200x1200/img-master/a_p0_master1200.jpg"
            },
            "create_date": "2026-10-01T06:12:00+00:00",
            "page_count": 2,
            "width": 1200,
            "height": 1600,
            "x_restrict": 0,
            "illust_ai_type": 1,
            "total_view": 851,
            "total_bookmarks": 32,
            "user": {
              "id": 12345, "name": "繪師A", "account": "gaemA",
              "profile_image_urls": { "medium": "https://i.pximg.net/user-profile/img/12345_m.png" }
            },
            "tags": [ { "name": "東方Project" }, { "count": 3, "name": "キャラ配布" } ],
            "meta_single_page": {},
            "meta_pages": [
              {
                "title": "1p",
                "image_urls": {
                  "medium": "https://i.pximg.net/c/360x360_70/img-master/a_p0_master1200.jpg",
                  "original": "https://i.pximg.net/img-original/img/a_p0.png"
                },
                "width": 1200, "height": 1600
              },
              {
                "title": "2p",
                "image_urls": { "original": "https://i.pximg.net/img-original/img/a_p1.jpg" },
                "width": 900, "height": 1200
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `登录腿详情读出逐页两档、宽高与只有作品页要的那几格`() {
        val d = PixivAppParse.workDetail(detailSample) ?: error("这一份该读出来，不该交 null")
        assertEquals(150320368L, d.post.id)
        assertEquals("[CHARA] Kakkoi Elbertson", d.title)
        assertEquals(12345L, d.authorId)
        assertEquals("gaemA", d.authorAccount)
        // 逐页：页数 2 ⇒ 两页都在，顺序照站方给的来
        assertEquals(2, d.pageCount)
        assertEquals("站方说 2 页就必须手里 2 页", 2, d.pages.size)
        assertEquals("https://i.pximg.net/c/360x360_70/img-master/a_p0_master1200.jpg", d.pages[0].mediumUrl)
        assertEquals("https://i.pximg.net/img-original/img/a_p0.png", d.pages[0].originalUrl)
        assertEquals(1200, d.pages[0].width)
        assertEquals(1600, d.pages[0].height)
        // 第二页站方只给 original ⇒ medium **退回同一串**（宁可在 HD 那档说"没有更高清"，也不少画一页）
        assertEquals("https://i.pximg.net/img-original/img/a_p1.jpg", d.pages[1].mediumUrl)
        assertEquals("https://i.pximg.net/img-original/img/a_p1.jpg", d.pages[1].originalUrl)
        assertEquals(900, d.pages[1].width)
        // 标签逐枚、顺序照站方（并成空格串再切会把带空格的那枚劈开）
        assertEquals(listOf("東方Project", "キャラ配布"), d.tags)
        // 三格计数：浏览数与发布时间这一腿都有出处
        assertEquals(851, d.viewCount)
        assertEquals("2026-10-01T06:12:00+00:00", d.uploadDate)
        assertEquals(32, d.post.favCount)
        // ⚠️ 评论数：app-api 这一腿**没有**这一格 ⇒ 恒 null，屏上那一枚不摆数（不许拿 0 顶）
        assertNull(d.commentCount)
        assertFalse("这一腿读不出评论开关，不能替作者关上它", d.commentsOff)
        assertFalse(d.ugoira)
    }

    /** 单页那一形：站方给 `meta_single_page`、`meta_pages` 是空表 ⇒ 只能长出一页。 */
    @Test
    fun `登录腿详情在单页那一形下恰好长出一页`() {
        val d = PixivAppParse.workDetail(
            """
            {
              "illust": {
                "id": 150822243,
                "title": "海辺の午後",
                "type": "illust",
                "image_urls": { "medium": "https://i.pximg.net/c/360x360_70/img-master/s_p0.jpg" },
                "create_date": "2026-10-02T01:00:00+00:00",
                "page_count": 1,
                "width": 900, "height": 900,
                "user": { "id": 7, "name": "繪師C", "account": "c" },
                "tags": [],
                "meta_single_page": { "original_image_url": "https://i.pximg.net/img-original/img/s_p0.png" },
                "meta_pages": []
              }
            }
            """.trimIndent(),
        ) ?: error("单页这一形该读出来")
        assertEquals(1, d.pageCount)
        assertEquals("站方说 1 页就只能手里 1 页", 1, d.pages.size)
        assertEquals("https://i.pximg.net/img-original/img/s_p0.png", d.pages[0].originalUrl)
        // 单页时 medium 那一档从 image_urls.medium 取（`meta_single_page` 只带原图一档）
        assertEquals("https://i.pximg.net/c/360x360_70/img-master/s_p0.jpg", d.pages[0].mediumUrl)
    }

    /** 读不懂就抛，不交 null：null 在屏上是"这一条没有图"，而这里说的是"我们没看懂这一份答复"。 */
    @Test
    fun `登录腿详情缺 illust 那一层就抛，不静默交回没有图`() {
        val e = runCatching { PixivAppParse.workDetail("""{"foo":1}""") }.exceptionOrNull()
        assertTrue("缺键应当抛，现量 " + e, e is java.io.IOException)
        assertTrue("抛的句子要念得出缺的是哪一层：" + e?.message, e!!.message!!.contains("illust"))
    }

    /** app-api 的错误信封是**平铺在 error 上的对象**（与匿名腿的 `error:true` 不同形），要照列表腿那颗同一把尺抛。 */
    @Test
    fun `登录腿详情读得懂 app-api 的错误信封并把站方那句带出来`() {
        val e = runCatching {
            PixivAppParse.workDetail("""{"error":{"status_code":404,"message":"illust not found","reason":""}}""")
        }.exceptionOrNull()
        assertTrue("错误信封应当抛，现量 " + e, e is java.io.IOException)
        assertTrue("要把站方那句 message 带上屏：" + e?.message, e!!.message!!.contains("illust not found"))
    }

    /**
     * `/v1/user/detail` 的读数（2026-10-19 晚，用户「已经关注的画师右边的按钮也要显示已经关注」）。
     *
     * 这一颗要守的是 **`is_followed` 的三档不许并成两档**：
     * `true` = 站方答"关注了"、`false` = 站方答"没关注"、缺键 = 站方没答（没票据 / 改了形状）。
     * 把缺键折成 `false` 就是替用户断言"你没关注这位" —— 那恰好是他报上来的那个症状。
     * 屏上第三档要退到本地那本账，并且**明说自己退到了本地**（见画师页 `anonymousReason` 那一行）。
     */
    @Test
    fun `画师详情 - 四格总数与 is_followed 的三档各说各话`() {
        val followed = PixivAppParse.userDetail(
            """{"user":{"id":1,"is_premium":true},
               "profile":{"total_illusts":26,"total_manga":0,"total_follow_users":1234,
                          "total_illust_bookmarks_public":7},
               "is_followed":true}""",
        )
        assertEquals(26, followed.totalIllusts)
        assertEquals(0, followed.totalManga)
        assertEquals(1234, followed.totalFollowers)
        assertEquals(7, followed.totalBookmarksPublic)
        assertEquals(true, followed.isFollowed)

        val notFollowed = PixivAppParse.userDetail(
            """{"user":{"id":1},"profile":{"total_illusts":3},
               "is_followed":false}""",
        )
        assertEquals(false, notFollowed.isFollowed)
        // 站方只给了部分格子：没给的那几格是 null，不是 0（0 是"这位没有投稿"的断言）。
        assertNull(notFollowed.totalFollowers)
        assertNull(notFollowed.totalBookmarksPublic)

        val silent = PixivAppParse.userDetail("""{"user":{"id":1},"profile":{"total_illusts":3}}""")
        assertNull("缺 is_followed 不许折成 false", silent.isFollowed)

        val thrown = runCatching { PixivAppParse.userDetail("""{"user":{"id":1}}""") }.exceptionOrNull()
        assertTrue("缺 profile 那一层要抛而不是交一份全 null：" + thrown, thrown is IOException)
    }

    /**
     * 画师作品那一腿与书签/推荐/搜索**共用同一颗行解析**（2026-10-19 晚）。
     *
     * 单独钉一条的理由：这条腿存在的意义就是"比匿名那条多给成人档"，
     * 而它交回的行模型必须与其它腿**逐格同构**（同一张图在搜索墙与画师墙待遇不同，
     * 是本仓反复立案的那一族错）。所以这里喂一份真形状，核对四格而不是只看它跑通。
     */
    @Test
    fun `画师作品腿读出的行模型与书签腿同构`() {
        val page = PixivAppParse.userIllusts(
            """{"illusts":[{"id":123,"title":"夜","type":"illust","width":1000,"height":3000,
                "x_restrict":1,"illust_ai_type":1,"page_count":1,
                "image_urls":{"medium":"https://i.pximg.net/c/540x540/img-master/123_p0.jpg"},
                "meta_single_page":{"original_image_url":"https://i.pximg.net/img-original/img/123_p0.png"},
                "tags":[{"name":"R-18"}],
                "user":{"id":9,"name":"某画师","profile_image_urls":{"medium":"https://i.pximg.net/9.png"}}}],
               "next_url":"https://app-api.pixiv.net/v1/user/illusts?user_id=9&offset=30"}""",
        )
        assertEquals(1, page.works.size)
        val post = page.works.single().post
        assertEquals(123L, post.id)
        assertEquals(GallerySite.PIXIV, post.site)
        // 分级：x_restrict=1 → r18（这张表与匿名腿那份必须同值）
        assertEquals("r18", post.rating)
        // 比例来源：站方给的是**原图** 1000×3000，不是缩略档的尺寸
        assertEquals(1000, post.width)
        assertEquals(3000, post.height)
        assertEquals("某画师", post.author)
        assertEquals("https://i.pximg.net/9.png", page.works.single().authorAvatarUrl)
        assertEquals("https://app-api.pixiv.net/v1/user/illusts?user_id=9&offset=30", page.nextUrl)
    }

}
