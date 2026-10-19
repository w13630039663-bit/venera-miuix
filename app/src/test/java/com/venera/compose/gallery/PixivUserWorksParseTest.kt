package com.venera.compose.gallery

import com.venera.compose.gallery.data.PixivArtistSection
import com.venera.compose.gallery.data.PixivUserWorksParse
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * pixiv 画师页那三份匿名答复的解析判据（2026-10-19）。
 *
 * 样本来自**本机实测的真答复**（用户 7075448 / 11 / 49384704，2026-10-19 直连
 * `www.pixiv.net`，不带任何头），键名逐字照抄，不按印象补字段。
 *
 * 这一族钉的是一件事：**"没设置" 与 "没答上" 不许在屏上同形** ——
 * `background: null` 是站方明说"这位没有背景"（要回退首图），而整份读不懂是失败（要念出来）。
 */
class PixivUserWorksParseTest {

    // ─────────────────────────────────────────────────────────────────────
    // 一跳：头部
    // ─────────────────────────────────────────────────────────────────────

    /** 真答复（截取）：有背景、有外链、有简介。 */
    private val headWithBackground = """
        {"error":false,"message":"","body":{
          "userId":"7075448","name":"\u30bb\u30c8\u30de\u30f3",
          "image":"https://i.pximg.net/user-profile/img/x_50.png",
          "imageBig":"https://i.pximg.net/user-profile/img/x_170.png",
          "premium":true,"isFollowed":false,
          "background":{"repeat":null,"color":null,
            "url":"https://i.pximg.net/c/1920x960_80_a2_g5/background/img/7075448_5ac3.png",
            "isPrivate":false},
          "following":179,
          "comment":"\u30a4\u30e9\u30b9\u30c8\u306e\u7121\u65ad\u8ee2\u8f09\u306f\u304a\u65ad\u308a\u3057\u3066\u304a\u308a\u307e\u3059\u3002",
          "webpage":null,
          "social":{"twitter":{"url":"https://twitter.com/Setmen_uU"}}
        }}
    """.trimIndent()

    @Test
    fun `头部 - 背景与外链与两档头像都认出来`() {
        val head = PixivUserWorksParse.userHead(headWithBackground)
        assertEquals(7075448L, head.id)
        assertEquals("\u30bb\u30c8\u30de\u30f3", head.name)
        assertEquals("", head.account) // 这条真答复没有 account 键 ⇒ 空串（键不在 ≠ 崩）
        assertEquals(
            "https://i.pximg.net/c/1920x960_80_a2_g5/background/img/7075448_5ac3.png",
            head.backgroundUrl,
        )
        // 头像取 170 档（imageBig 优先）。
        assertEquals("https://i.pximg.net/user-profile/img/x_170.png", head.avatarUrl)
        assertEquals(179, head.followingCount)
        assertTrue(head.premium)
        assertEquals(listOf("https://twitter.com/Setmen_uU"), head.links)
    }

    /**
     * **这一条是本族的红线**：`background` 整个为 null ⇒ `backgroundUrl = null`。
     *
     * 不是空串 —— 空串会让"没设置"与"地址坏掉"在屏上同形，而这两件事的处理完全不同
     * （前者回退首张作品，后者该被日志查出来）。
     */
    @Test
    fun `头部 - background 为 null 时交 null 而不是空串`() {
        val body = """
            {"error":false,"message":"","body":{
              "userId":"49384704","name":"\u5d16","image":"https://i.pximg.net/a_50.jpg",
              "imageBig":"https://i.pximg.net/a_170.jpg","premium":false,
              "background":null,"following":0,"comment":"","webpage":null,"social":[]
            }}
        """.trimIndent()
        val head = PixivUserWorksParse.userHead(body)
        assertNull("background:null 必须交 null", head.backgroundUrl)
    }

    /** 背景对象在、但 `url` 为空 ⇒ 同样算"没设置"（不能把空串摆上去变成一张空图）。 */
    @Test
    fun `头部 - background 对象里 url 为空也交 null`() {
        val body = """
            {"error":false,"body":{"userId":"11","name":"a","background":{"url":null},
             "social":[]}}
        """.trimIndent()
        assertNull(PixivUserWorksParse.userHead(body).backgroundUrl)
    }

    /** 站方明说 `error=true` ⇒ 抛（不是"这位没背景"，是"这一跳没成"）。 */
    @Test
    fun `头部 - error=true 时抛`() {
        val body = """{"error":true,"message":"user not found","body":[]}"""
        try {
            PixivUserWorksParse.userHead(body)
            fail("error=true 必须抛")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("error=true"))
        }
    }

    /** 缺 `body` ⇒ 抛（读不懂不许静默变空）。 */
    @Test
    fun `头部 - 缺 body 时抛`() {
        try {
            PixivUserWorksParse.userHead("""{"error":false}""")
            fail("缺 body 必须抛")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("body"))
        }
    }

    /** 没有可用 userId ⇒ 抛（拿 0 当号去取数就是取别人的）。 */
    @Test
    fun `头部 - 没有 userId 时抛`() {
        try {
            PixivUserWorksParse.userHead("""{"error":false,"body":{"name":"x"}}""")
            fail("没有 userId 必须抛")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("userId"))
        }
    }

    /** 非 http(s) 的地址一律不认（站方哪天给相对路径，摆上去就是空图）。 */
    @Test
    fun `头部 - 非 http 地址的外链被滤掉`() {
        val body = """
            {"error":false,"body":{"userId":"1","name":"x",
             "imageBig":"/user-profile/relative.png",
             "social":{"twitter":{"url":"twitter.com/no-scheme"}},
             "webpage":"https://example.com/me"}}
        """.trimIndent()
        val head = PixivUserWorksParse.userHead(body)
        assertNull("相对路径的头像不认", head.avatarUrl)
        assertEquals(listOf("https://example.com/me"), head.links)
    }

    // ─────────────────────────────────────────────────────────────────────
    // 二跳：作品索引
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `索引 - 两个桶的 id 表与公开收藏数`() {
        val body = """
            {"error":false,"body":{
              "illusts":{"148343100":null,"143413633":null,"131105199":null},
              "manga":{"148343121":null},
              "bookmarkCount":{"public":{"illust":860,"novel":0,"collection":0},
                               "private":{"illust":42,"novel":0}}
            }}
        """.trimIndent()
        val works = PixivUserWorksParse.profileAll(body)
        assertEquals(listOf(148343100L, 143413633L, 131105199L), works.illusts)
        assertEquals(listOf(148343121L), works.manga)
        // 只读 public 那一格（私密数不参与，否则屏上"公开收藏 N"与墙的张数对不上）。
        assertEquals(860, works.publicBookmarkCount)
    }

    /**
     * id 表**原样保留站方顺序**（实测降序 = 最新在前）。
     *
     * 去重或排序都会把"最新在前"这个依据抹掉 —— 而屏上第一屏摆的就是最新那几张。
     */
    @Test
    fun `索引 - 保持站方给出的降序`() {
        val body = """
            {"error":false,"body":{"illusts":{"900":null,"800":null,"700":null},"manga":{}}}
        """.trimIndent()
        assertEquals(listOf(900L, 800L, 700L), PixivUserWorksParse.profileAll(body).illusts)
    }

    /** 桶键不在 ⇒ 空表（这位没投过漫画，是合法答复）。 */
    @Test
    fun `索引 - 缺桶键时交空表`() {
        val body = """{"error":false,"body":{"illusts":{"1":null}}}"""
        val works = PixivUserWorksParse.profileAll(body)
        assertTrue(works.manga.isEmpty())
        assertNull("没有 bookmarkCount ⇒ 收藏数未知，不是 0", works.publicBookmarkCount)
    }

    /**
     * 桶在、但**带着元素的数组**或非对象 ⇒ 抛（形状真变了不许说成"没有"）。
     *
     * ⚠️ 2026-10-19 就地改判：这一条从前写的是 `illusts:[]` 必须抛，而**那句是错的** ——
     * 站方对**空桶给的就是 `[]`**（现量：用户 12956047 的 `profile/all` 回
     * `"illusts":{133 条},"manga":[],"novels":[]`，同一形状在只发插画的画师身上是常态）。
     * 旧写法把"这位没投过漫画"读成形状异常 ⇒ 整条 `profileAll` 抛 ⇒ 而屏上把它吞了
     * （只 Log.w、不置状态位）⇒ 用户读到的就是"很多画师页不出图、四格全是「—」、一句失败话都没有"。
     * 空数组那一档另起一条用例钉住（见下面那条），这里只留"真认不出"的那一档。
     */
    @Test
    fun `索引 - 桶是带元素的数组或非对象时抛`() {
        try {
            PixivUserWorksParse.profileAll("""{"error":false,"body":{"illusts":[{"id":1}]}}""")
            fail("带元素的数组不是我们认的桶形状，必须抛")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("illusts"))
        }
        try {
            PixivUserWorksParse.profileAll("""{"error":false,"body":{"illusts":"148343100"}}""")
            fail("桶是标量时必须抛")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("illusts"))
        }
    }

    /**
     * **空桶的两档形状都要认**：`{}`（有键无元素）与 `[]`（站方给空数组）都是"这位没有这一类投稿"。
     *
     * 这一条就是 2026-10-19 那批"画师页不出图"的病根用例：`manga:[]` 曾被当成形状异常抛出去，
     * 于是一整页安静地空着。判据钉的是"不许把空数组读成失败"，不是钉 manga 这一格。
     */
    @Test
    fun `索引 - 空桶无论是空对象还是空数组都交空表`() {
        val asObject = PixivUserWorksParse.profileAll(
            """{"error":false,"body":{"illusts":{"1":null},"manga":{},"bookmarkCount":{"public":{"illust":7}}}}""",
        )
        val asArray = PixivUserWorksParse.profileAll(
            """{"error":false,"body":{"illusts":{"1":null},"manga":[],"bookmarkCount":{"public":{"illust":7}}}}""",
        )
        assertTrue("manga 是空对象 ⇒ 空表", asObject.manga.isEmpty())
        assertTrue("manga 是空数组 ⇒ 同样空表，不许抛也不许读成失败", asArray.manga.isEmpty())
        // 两档形状下其余两格必须完全一致（不许"空数组那一档顺手把别的格也读空"）
        assertEquals(listOf(1L), asArray.illusts)
        assertEquals(7, asArray.publicBookmarkCount)
    }

    /** `idsFor` 把 tab 映射到 id 表（收藏段不在其中 —— 它走票据腿）。 */
    @Test
    fun `索引 - idsFor 按 tab 取表`() {
        val works = PixivUserWorksParse.profileAll(
            """{"error":false,"body":{"illusts":{"1":null},"manga":{"2":null}}}""",
        )
        assertEquals(listOf(1L), works.idsFor(PixivArtistSection.ILLUST))
        assertEquals(listOf(2L), works.idsFor(PixivArtistSection.MANGA))
    }

    // ─────────────────────────────────────────────────────────────────────
    // 三跳：按 id 取一批
    // ─────────────────────────────────────────────────────────────────────

    private val oneWork = """
        {"error":false,"body":{"works":{
          "148343100":{
            "id":"148343100","title":"\u30a4\u30d6\u30ad\uff01\uff01","illustType":0,
            "xRestrict":0,"sl":2,
            "url":"https://i.pximg.net/c/250x250_80_a2/custom-thumb/img/148343100_p0_custom1200.jpg",
            "tags":["\u5973\u306e\u5b50","\u30ed\u30ea"],
            "userId":"7075448","userName":"\u30bb\u30c8\u30de\u30f3",
            "width":2342,"height":4198,"pageCount":1,
            "isMasked":false,"aiType":1,
            "profileImageUrl":"https://i.pximg.net/user-profile/img/x_50.png"
          },
          "143413633":null
        }}}
    """.trimIndent()

    @Test
    fun `批次 - 按请求顺序交回且空位保留 null`() {
        val got = PixivUserWorksParse.worksByIds(oneWork, listOf(143413633L, 148343100L))
        assertEquals(2, got.size)
        // 请求顺序 = 站方 id 表顺序（最新在前），不跟 JSON 对象键序走。
        assertNull("null 那一格必须保留位子（它是站方说'这条没了'）", got[0])
        val work = got[1]
        assertNotNull("第二条该认得出", work)
        assertEquals(148343100L, work!!.post.id)
        assertEquals("\u30a4\u30d6\u30ad\uff01\uff01", work.title)
        assertEquals("\u30bb\u30c8\u30de\u30f3", work.post.author)
        assertEquals("https://www.pixiv.net/users/7075448", work.post.source)
        assertEquals(2342, work.post.width)
        assertEquals(1, work.post.pageCount)
        assertEquals("\u5973\u306e\u5b50 \u30ed\u30ea", work.post.tags)
        // AI 硬位照站方给：`aiType` **2 = AI 生成 / 1 = 非 AI / 其余 = 不知道**
        // （判据在 `pixivAiFlagOf`，本仓只此一处）。样本是 1 ⇒ false，不是 null ——
        // "站方说不是 AI"与"站方没说"是两件事，前者能给就用前者。
        assertEquals(false, work.post.siteAiFlag)
        // 缩略档有、大图档空 —— 这一腿不给大图，不许把 url 冒充成 largeUrl。
        assertEquals("", work.post.largeUrl)
        assertTrue(work.post.previewUrl.startsWith("https://i.pximg.net/"))
    }

    /**
     * 分级翻译：`xRestrict` 0=general / 2=r15 / 其余 r18。
     *
     * ⚠️ 这张表在 [com.venera.compose.gallery.data.PixivFeedParse] 里也有一份（private）。
     * 那顆文件正被另一条线频繁改，不去动它；两边同值由这条用例钉住 —— 一处漂了当场红。
     */
    @Test
    fun `批次 - xRestrict 翻译与搜索腿一致`() {
        fun ratingOf(x: String): String = PixivUserWorksParse.worksByIds(
            """{"error":false,"body":{"works":{"9":{"id":"9","url":"https://x/a.jpg","xRestrict":$x}}}}""",
            listOf(9L),
        ).single()!!.post.rating
        assertEquals("general", ratingOf("0"))
        assertEquals("r15", ratingOf("2"))
        assertEquals("r18", ratingOf("1"))
        assertEquals("r18", ratingOf("999"))
    }

    /** 认不出的一张交 null（不抛、不拖垮同批另外那些）。 */
    @Test
    fun `批次 - 单条认不出不拖垮整批`() {
        val body = """
            {"error":false,"body":{"works":{
              "1":{"id":"1","url":"https://i.pximg.net/a.jpg"},
              "2":{"title":"no id and no url"},
              "3":{"id":"3"}
            }}}
        """.trimIndent()
        val got = PixivUserWorksParse.worksByIds(body, listOf(1L, 2L, 3L))
        assertNotNull("有 id 与 url 的那条该认得出", got[0])
        assertNull("没有 id/url 的那条交 null", got[1])
        assertNull("没有缩略图的那条交 null", got[2])
    }

    @Test
    fun `批次 - 没有 works 时抛`() {
        try {
            PixivUserWorksParse.worksByIds("""{"error":false,"body":{}}""", listOf(1L))
            fail("缺 body.works 必须抛")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("works"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 路由：这一族不许把 booru 的查询语言带回来
    // ─────────────────────────────────────────────────────────────────────

    /**
     * **闪退那条的守门用例**：pixiv 画师页那条路上不许出现 `GalleryRankings.searchQuery`
     * （它对 PIXIV 是 `notWired()` ⇒ 抛 ⇒ 整屏闪退）。
     *
     * 用**声明扫描**而不是跑真实组合：那条腿会抛，Compose 组合起不来。
     */
    @Test
    fun `路由 - pixiv 画师屏不引用 booru 查询算式`() {
        // ⚠️ 扫**代码**不扫注释：那一颗文件的头注里正解释着"为什么不能用
        // `GalleryRankings.searchQuery`"，把注释也算进来就成了"解释病名即得病"。
        val source = readSource("app/src/main/java/com/venera/compose/gallery/ui/PixivArtistScreen.kt")
            .lineSequence()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
            .joinToString("\n")
        assertTrue(
            "PixivArtistScreen.kt 不许引用 searchQuery / GalleryRankings",
            !source.contains("searchQuery") && !source.contains("GalleryRankings"),
        )
        // 图库三站那屏里，PIXIV 那一支必须是"根本不发"（return），不许再算查询串。
        val three = readSource(
            "app/src/main/java/com/venera/compose/gallery/ui/GalleryArtistProfileScreen.kt",
        )
        assertTrue(
            "GalleryArtistProfileScreen 的 PIXIV 分支不许再构造 searchQuery",
            !three.contains("GallerySite.PIXIV -> Result.failure"),
        )
    }

    /**
     * 画师页那一屏的**第一跳失败要说话**（2026-10-19 · 批 233-1，用户「很多画师页还是不显示图片」）。
     *
     * 立案读数：那一跳从前失败只 `onFailure { }` 空吞，屏上于是摆"这一位没有作品"那一档 ——
     * 说的是"站方回空"，读起来却像"没图"。修好之后屏上要能分出三档：
     * 第一跳没取到（**可重试**）/ 这一批没取上（可重试，留着前几批）/ 站方真给空桶（一句读数，没有把手）。
     *
     * ## 2026-10-19 晚改判：三档变四档，而第一跳那一档多了一枚 `!ticketMode`
     *
     * 用户报「插画数量少很多」后，这一屏的作品来源换成了带票据的 `/v1/user/illusts`，
     * 匿名的 `profile/all` 退成"没有票据"那一档的兜底。于是：
     * - **②那一档的判式多一枚 `!ticketMode`**：票据档压根不发 `profile/all` 那一跳，
     *   再摆"索引没取到"就是把一条没发的请求说成失败（同一屏另一处 `if (!ticketMode)` 是它的成因）。
     *    needle 因此跟着换字面 —— 这条尺子钉的是**那一整行表达式**，不是"worksFailed 在场"；
     * - **③从 2 枚把手变 3 枚**：多出来的那一枚属于新第四档「站方报了 N 条却一条内容都没回来」。
     *   它既不是"索引没取到"（索引取到了），也不是"这一批没取上"（那一批回了、只是全空），
     *   更不是"这位没有作品"（站方说有 N 条）—— 合成任何一档都是在替用户下一个我们没下的判断，
     *   而 2026-10-19 那份真机读数恰恰是"还没有插画作品"与"共 3 张"两句同时挂在屏上。
     *
     * 用声明扫描而不是跑组合：这一屏要真票据才画得出图。
     */
    @Test
    fun `路由 - 画师页第一跳失败时屏上说话而空桶不报失败`() {
        val lines = readSource("src/main/java/com/venera/compose/gallery/ui/PixivArtistScreen.kt")
            .lines().map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
        // ① 失败旗标只有一处抬、一处落（两处各写两遍就是两把尺，改日只坏一半）。
        assertEquals("失败旗标只在第一跳失败时抬", 1, lines.countWith("worksFailed = true"))
        assertEquals("取上了就把旗标落下", 1, lines.countWith("worksFailed = false"))
        // ② 那一档的判式**整行**钉死：旗标抬着 **且** 屏上一条都没有 **且** 走的是匿名兜底档。
        assertEquals(
            "第一跳那一档只在整屏空且确实是匿名档时摆",
            1,
            lines.countWith("else if (!ticketMode && worksFailed && cards.isEmpty()) {"),
        )
        // ③ 重试把手按**次数**钉：三档（索引没取到 / 这一批没取上 / 报了 N 条却全空）各一枚，
        //    谁也不能替谁说话。
        assertEquals("三档失败各一枚真把手", 3, lines.countWith("onClick = { retryTick += 1 }"))
        assertEquals("重试抬的那一枚只有一处声明", 1, lines.countWith("var retryTick by remember(userId)"))
        // ④ 顺序判据：那一跳的 effect 必须**吃 retryTick**，否则按下去什么也不发（把手变假把手）。
        assertEquals("索引那一跳吃重试那一枚", 1, lines.countWith("LaunchedEffect(userId, retryTick) {"))
        // ⑤ 屏上那句不许与"这一批没取上"合成一句（两种成因两条腿，合成一句就替用户下了判断）。
        // needle 带到 `text = ` 那一行：页尾那档也念"这一批没取上 · 重试"（同一件事的第二处措辞），
        // 裸那半句会把两处一起算进来 —— 尺子咬到邻居，不是邻居错了。
        assertEquals("第一跳那句自己的措辞", 1, lines.countWith("text = \"这一位的作品索引没取到 —— 点这里重试\""))
        assertEquals("这一批那句自己的措辞", 1, lines.countWith("text = \"这一批没取上 —— 点这里重试\""))
        assertEquals(
            "报了 N 条却全空那一句自己的措辞（不许并入上面任何一档）",
            1,
            lines.countWith("text = \"站方说这一位有 \$idCount 条\${tab.label}，但一条内容都没取回 —— 点这里重试\","),
        )
        // ⑥ 撤掉的是"空吞"：onFailure 里除旗标外不许再留裸的空块。
        assertEquals("不许有空吞的 onFailure", 0, lines.countWith("onFailure { }"))
    }

    /**
     * 画师页的**选腿**（2026-10-19 晚，用户「返回的插画数量少很多」+「我这些操作全部都是有登录的」）。
     *
     * 立案读数：匿名 `profile/all` 对同一位画师只回 3 个 id，而 pixiv 网页给 26 —— 差的那一截
     * 是**成人档**（站方对匿名请求整批过滤）。修法不是"多要几页"，是换一条带票据的腿。
     *
     * 这一屏从此有**两条作品来源**，所以尺子钉的不是任何一条腿自己，而是三件事：
     * ① 两条不许同时往 `cards` 上写（谁后回来谁赢，屏上张数会跟着网络抖）；
     * ② 退到匿名档必须**上屏**（少给却不说是本仓最硬那条红线）；
     * ③ 关注那笔**先写站方、写成了才对齐本地**（顺序反了屏上就亮着一段站方不存在的关注关系）。
     */
    @Test
    fun `画师页 - 有票据走站方腿而匿名腿只作兜底且退档上屏`() {
        val lines = readSource("src/main/java/com/venera/compose/gallery/ui/PixivArtistScreen.kt")
            .lines().map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
        // ① 票据腿：第一页与续页各一处调用点（两处的游标不同，不许并成一处分叉）。
        assertEquals(
            "票据腿第一页只有一处", 1,
            lines.countWith("client.userIllusts(bearer, id.toString(), tab.ticketType())"),
        )
        assertEquals(
            "票据腿续页只有一处", 1,
            lines.countWith("client.userIllusts(bearer, id.toString(), tab.ticketType(), next)"),
        )
        // ② 匿名那两条必须被票据档关在门外。
        assertEquals("匿名 id 表只在非票据档发", 1, lines.countWith("if (!ticketMode) {"))
        assertEquals("匿名那一批也认票据档", 1, lines.countWith("if (ticketMode) return@LaunchedEffect"))
        // ③ 段 → 站方桶名只有一处（传错桶**不报错**，只会回另一类作品）。
        assertEquals("桶名只从这一处出", 1, lines.countWith("PixivArtistSection.ILLUST -> \"illust\""))
        // ④ 退档必须上屏说一句。
        assertEquals("退匿名档那句只有一处", 1, lines.countWith("现在是匿名档"))
        // ⑤ 关注态取值只有一处：站方优先、本地兜底。
        assertEquals(
            "关注态只有一处取值", 1,
            lines.countWith("val following = detail?.isFollowed ?: followingLocal"),
        )
        // ⑥ 写腿两枚各一处，且本地对齐**排在站方那一跳之后**。
        assertEquals(
            "站方关注那一跳只有一处", 1,
            lines.countWith("client.followAdd(bearer, id.toString(), private = false)"),
        )
        assertEquals("站方取关那一跳只有一处", 1, lines.countWith("client.followRemove(bearer, id.toString())"))
        assertEquals(
            "对齐本地只有一处、且按目标状态而不是翻一格", 1,
            lines.countWith("ports.follows.setFollowed(GallerySite.PIXIV, artistName, target)"),
        )
        // 两个索引取自**同一份过滤后的列表** ⇒ 同一坐标系统，可比（不是文件行号，别拿它去比原始行）。
        val removeAt = lines.indexOfFirst { it.contains("client.followRemove(bearer, id.toString())") }
        val alignAt = lines.indexOfFirst {
            it.contains("ports.follows.setFollowed(GallerySite.PIXIV, artistName, target)")
        }
        assertTrue("本地对齐要排在站方那一跳之后（align=$alignAt remove=$removeAt）", alignAt > removeAt)
        // ⑦ 票据腿那两道必须先把**收藏段**挡在门外。这一条不是防"少取一页"，是防**闪退**：
        //    `ticketType()` 落到 BOOKMARK 是 `error(...)`（那一桶压根不存在，交默认值就是假接线），
        //    而收藏段自己有一条书签腿。少这道闸门，"切到收藏"这个动作本身就把整屏抛掉 ——
        //    2026-10-19 真机栈在案：`IllegalStateException: 收藏段没有票据版的作品桶`
        //    at `PixivArtistScreen.kt:279`（就是第一页那颗 effect 里那一枚 `tab.ticketType()`）。
        assertEquals(
            "票据腿两道的收藏段闸门各一枚（第一页 + 续页）", 2,
            lines.countWith("if (tab == PixivArtistSection.BOOKMARK) return@LaunchedEffect"),
        )
        // 而且它必须排在 `ticketType()` **之前**（同一份过滤后列表的索引，同一坐标系统）。
        val gateAt = lines.indexOfFirst { it.contains("if (tab == PixivArtistSection.BOOKMARK) return@LaunchedEffect") }
        val callAt = lines.indexOfFirst { it.contains("client.userIllusts(bearer, id.toString(), tab.ticketType())") }
        assertTrue("闸门要在调用点之前（gate=$gateAt call=$callAt）", gateAt in 0 until callAt)
        // ⑧ 摆卡那一句**不许挂在状态行那条 if-else 链的 `else` 上**。
        //    2026-10-19 真机读数：收藏段取到 285 条却一张不画 —— 收藏段走的是链首那个分支
        //    （它有自己的四档状态行），而摆卡当时挂在链尾的 `else`，永远到不了。
        //    页尾那句「共 285 张」读的是 `bookmarkWorks.size`，所以那一屏同时给出了
        //    "取到了"与"什么都没画"两个事实，而它读起来只像"取不到"。
        val itemsAt = lines.indexOfFirst {
            it.contains("items(wall.cards, key = { it.post.uid }) { card ->")
        }
        assertEquals("摆卡只有一处", itemsAt, lines.indexOfLast {
            it.contains("items(wall.cards, key = { it.post.uid }) { card ->")
        })
        assertTrue("摆卡那一句得在场（itemsAt=$itemsAt）", itemsAt > 0)
        assertEquals(
            "摆卡要由一层独立的非空守卫开启（整行相等，不用 contains：`}` 也算包含）",
            "if (wall.cards.isNotEmpty()) {",
            lines[itemsAt - 1],
        )
    }

    private fun List<String>.countWith(needle: String): Int = count { it.contains(needle) }

    private fun readSource(relative: String): String {
        // 单测的 cwd 是 `app/`（Gradle 的 test 工作目录），所以优先按 app 子目录找，
        // 找不到再回退到仓库根 —— 两种 cwd 都能跑，不让用例本身变成环境依赖。
        val candidates = listOf(java.io.File(relative), java.io.File("../$relative"))
        val file = candidates.firstOrNull { it.exists() } ?: return ""
        assertTrue("找不到源文件 $relative", file.exists())
        return file.readText()
    }
}
