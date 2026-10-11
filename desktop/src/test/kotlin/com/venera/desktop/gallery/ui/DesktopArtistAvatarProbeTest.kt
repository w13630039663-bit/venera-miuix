package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryArtistAvatars
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryAvatarProbeEndpoint
import com.venera.compose.gallery.domain.GalleryAvatarEndpointKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十批 ②：画师头像现取。
 *
 * ## 这批修的是屏上一句**做不到的承诺**
 *
 * 「画师」那一屏的圆座第一档写的是「本机存过的真头像」，而桌面**只 peek 从不写** ——
 * 那一档今天永远命中不了，屏上那句话等于把"没接"说成了"没有"。
 * 本批把它接上：看得见的几位才去取（用户 2026-10-04 拍的闸门），取到就落盘。
 *
 * ## 三条钉死的口径，各对应一种会被顺手改坏的形状
 *
 * - **键同轨**（③）：地址档的键是 `(site, name, source)`，桌面读侧一直交空串。写侧一旦用真出处
 *   URL 当键，"修完取数还是读不到" —— 那是本缺陷的第二版，而且比第一版更难发现。
 *   所以读写两侧不许各写一个字面量，只许吃同一枚常量。
 * - **视口闸门**（④⑤）：整屏 forEach 现取 = 进屏就把名单里所有人都问一遍（名单可达几十位）。
 * - **定论与失败分手**（⑧）：「没有可探的端点」是定论，落档后不再重问；
 *   「请求本身失败了」**不是**定论，落档就等于把一次断网变成"这位永远没头像"。
 *
 * ①②⑤⑧ 都是**真跑**取值（纯函数 / 假件，不发请求）；③④⑥⑦ 扫源码，
 * 因为那几件事的形状就是"源码里有没有那一句"。
 */
class DesktopArtistAvatarProbeTest {

    /** 地址档的假件：只记账，不碰磁盘。键的拼法是假件自己的事，判据在共享那颗件里。 */
    private class FakeAvatars(private val answers: Map<String, String?> = emptyMap()) : GalleryArtistAvatars {
        val peeks = mutableListOf<Triple<GallerySite, String, String>>()
        val writes = mutableListOf<Quad>()

        override fun peek(site: GallerySite, name: String, source: String): String? {
            peeks += Triple(site, name, source)
            return answers["${site.name}|$name|$source"]
        }

        override suspend fun remember(site: GallerySite, name: String, source: String, avatar: String?) {
            writes += Quad(site, name, source, avatar)
        }

        data class Quad(val site: GallerySite, val name: String, val source: String, val avatar: String?)
    }

    private object Urls {
        const val FANBOX = "https://setmen.fanbox.cc/"
        const val MASTODON = "https://baraag.net/@bitty"
        const val PIXIV = "https://www.pixiv.net/users/123"
    }

    /** ① 端点计划的四种形状：链接里的 fanbox / 链接里的 Mastodon / 只有退路 / 三样都没有。 */
    @Test
    fun 头像端点计划的四种形状() {
        val fromFanboxLink = desktopAvatarProbePlan(listOf(Urls.PIXIV, Urls.FANBOX), sourceFallback = "")
        assertNotNull("链接表里有 fanbox 主页却没派生出端点", fromFanboxLink)
        assertEquals(GalleryAvatarEndpointKind.FANBOX, fromFanboxLink!!.kind)
        // Origin 是 fanbox 那台 api 的**硬条件**（不带一律 400），端点对象里必须还带着它。
        assertNotNull("派生出来的 fanbox 端点丢了 Origin —— 带不出去，等于白跑一趟", fromFanboxLink.origin)

        val fromMastodonLink = desktopAvatarProbePlan(listOf(Urls.MASTODON), sourceFallback = "")
        assertEquals(
            "链接表里的 Mastodon 主页也要能派生：桌面名单里没有出处 URL，链接表是唯一来源",
            GalleryAvatarEndpointKind.MASTODON,
            fromMastodonLink?.kind,
        )

        val fromFallback = desktopAvatarProbePlan(emptyList(), sourceFallback = Urls.MASTODON)
        assertEquals("没有链接表时退路那一条得顶上", GalleryAvatarEndpointKind.MASTODON, fromFallback?.kind)

        assertNull(
            "既没有可探链接也没有出处 ⇒ 不派生（发一笔注定失败的请求就是硬凑）",
            desktopAvatarProbePlan(listOf(Urls.PIXIV), sourceFallback = "https://yande.re/post/show/1"),
        )
    }

    /** ② 链接表里那一条**先于**退路：站方逐条登记的地址比"这张图发布在哪儿"更像本人。 */
    @Test
    fun 链接表优先于出处退路() {
        val plan = desktopAvatarProbePlan(listOf(Urls.FANBOX), sourceFallback = Urls.MASTODON)
        assertEquals(GalleryAvatarEndpointKind.FANBOX, plan?.kind)
        assertTrue("退路顶到了链接前面：${plan?.endpoint}", plan!!.endpoint.contains("fanbox"))
    }

    /** ③ 键同轨：每一处 peek/remember 调用都必须吃那一枚常量，不许各写一个字面量。 */
    @Test
    fun 地址档键读写两侧同轨() {
        assertEquals("规范键就是空串（桌面名单不带出处 URL）", "", DESKTOP_AVATAR_SOURCE_KEY)
        val files = listOf(
            // 批 42 起是 4 行：peek 一枚 + 落档三处（pixiv 拿到脸 / 没有可探端点 / 探针答完）
            "DesktopArtistsPolicy.kt" to 4,
            "DesktopArtistsPage.kt" to 1,
            "DesktopFollowedArtistsPane.kt" to 1,
        )
        var totalCallSites = 0
        files.forEach { (name, expectedLines) ->
            val lines = DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopUiSource(name))
                .filter { it.contains("avatars.peek(") || it.contains("avatars.remember(") }
            totalCallSites += lines.size
            assertEquals("「$name」里读写地址档的行数与预期不符（先看清是不是形状变了）", expectedLines, lines.size)
            lines.forEach { line ->
                assertTrue(
                    "「$name」这一处没吃 DESKTOP_AVATAR_SOURCE_KEY：$line",
                    line.contains("DESKTOP_AVATAR_SOURCE_KEY"),
                )
            }
        }
        // 对照组：这几处调用点真的都在（上面那条行数断言不是数了个零）。
        assertEquals("读写两侧合计应有 6 处地址档调用", 6, totalCallSites)
    }

    /** ④ 视口闸门在位：那一屏必须按可见集发车，整屏 forEach 现取那种形状不许存在。 */
    @Test
    fun 头像现取只由视口闸门发车() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistsPage.kt"))
        listOf(
            "rememberLazyListState()" to "没有列表状态就拿不到可见集",
            "visibleItemsInfo" to "可见集不是从 layoutInfo 现算的",
            "snapshotFlow" to "可见集变化没有订阅，滚进来的那几位永远不会被取",
            "LazyColumn" to "整屏不是 LazyColumn，就拿不到「哪些位在视口里」",
        ).forEach { (token, why) ->
            assertTrue("$why（源码里找不到「$token」）", code.contains(token))
        }
        // 旧形状：整屏 forEach 现取 = 进屏把名单全问一遍。
        assertTrue("那一屏又长成整屏 forEach 现取 —— 闸门就没了", code.contains("artists.forEach").not())
        assertTrue("整屏 verticalScroll 摆行拿不到可见集，不该残留", code.contains("verticalScroll").not())
    }

    /** ⑤ 可见集推导：去重、保持视口次序、查过的与已有脸的一律不重发。 */
    @Test
    fun 可见集只挑还没查过的那些位() {
        val keys = listOf<Any?>(0, "artist:z", "header", "artist:y", "artist:z")
        assertEquals(
            "次序按视口次序、重复行只留一次",
            listOf("z", "y"),
            desktopVisibleProbeTargets(keys, resolved = emptySet(), hasFace = emptySet()),
        )
        assertEquals(
            "查过的（含「查过、没有」）不许重发，否则每次滚动都重试",
            listOf("y"),
            desktopVisibleProbeTargets(keys, resolved = setOf("z"), hasFace = emptySet()),
        )
        assertEquals(
            "档里已经有脸的不许再问",
            emptyList<String>(),
            desktopVisibleProbeTargets(keys, resolved = emptySet(), hasFace = setOf("z", "y")),
        )
        assertEquals(
            "非行 key（节头页脚那些 item）与不带名字的空 key 一律忽略",
            emptyList<String>(),
            desktopVisibleProbeTargets(listOf<Any?>(0, "caption", "artist:"), emptySet(), emptySet()),
        )

        // 页面确实把两张表都交进去了（少交一张 = 上面那两条在真链路上不成立）。
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistsPage.kt"))
        assertTrue("页面没在调可见集那颗件", code.contains("desktopVisibleProbeTargets("))
        assertTrue("页面没把「查过的」那张表交给它", code.contains("probed.keys"))
        assertTrue("页面没把「档里已有脸」那张表交给它", code.contains("seeded.keys"))
    }

    /**
     * ⑥ 整页那句读数要点名两件事：**只给看得见的几位取**（视口闸门），以及**够得着的三条路**。
     *
     * ⚠️ 这一条在批 42 改过判。原本它钉的是"pixiv 没接所以必须点名"，而 pixiv 那一路已经接上了 ——
     * 判式却只查 `pixiv` 那三个字母在不在句子里，所以它**绿着替一句假话说作了保**（新那族坑：
     * 读数换了方向而尺子的方向没换）。现在句子少点名了任何一条已接的路都要红。
     */
    @Test
    fun 整页那句读数点名视口与够得着的三条路() {
        listOf(
            "看得见的几位" to "视口闸门不说出来，用户只会读到「头像时有时无」",
            "pixiv" to "三条里少点名一条，就是把接了的那条说成没接",
            "fanbox" to "三条里少点名一条，就是把接了的那条说成没接",
            "Mastodon" to "三条里少点名一条，就是把接了的那条说成没接",
            "首字母座" to "取不到落哪一档必须说",
        ).forEach { (token, why) ->
            assertTrue("$why（那句读数里没有「${token}」）", DesktopArtistsPageCaliberCaption.contains(token))
        }
        // 旧那句在整页已经不实：不许再被渲染。
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistsPage.kt"))
        assertTrue("整页还在念「不主动去站方取头像」—— 这句现在假的", code.contains("DesktopArtistFaceCaliberCaption").not())
        assertTrue("整页没把新的那句摆上屏", code.contains("DesktopArtistsPageCaliberCaption"))
    }

    /** ⑦ 首页那一栏（横卡 8 位）不许被顺手接上现取：八位全在视口里 = 进来就发 16 笔。 */
    @Test
    fun 首页那一栏仍只读本机已有的() {
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopFollowedArtistsPane.kt"))
        listOf("probeAvatarUrl", "avatars.remember", "desktopArtistAvatarChain", "visibleItemsInfo").forEach { banned ->
            assertTrue(
                "首页那一栏出现了「$banned」—— 那一栏按 ROW_SIZE 截断，八位全在视口里等于进来就发车",
                pane.contains(banned).not(),
            )
        }
        // 那一栏仍念旧那句（对它自己仍是真话）。
        assertTrue("首页那一栏把「不主动取」那句撤了 —— 它确实不取，那句得留着", DesktopArtistFaceCaliberCaption.contains("不主动"))
        assertTrue("首页那一栏没念那句", pane.contains("DesktopArtistFaceCaliberCaption"))
        // 对照组：整页那一侧确实接上了，否则上面四条负断言是恒真。
        val page = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistsPage.kt"))
        assertTrue("对照组失效：整页根本没接现取", page.contains("desktopArtistProbedFace"))
    }

    /** ⑧ 定论与失败分手：只有"定论"落档，一次请求失败不许把这位永久判成没头像。 */
    @Test
    fun 只有定论才落地址档() {
        runBlocking {
            // A：档里已经答过（空串 = 查过、没有）⇒ 一次请求都不许多发。
            val answered = FakeAvatars(mapOf("YANDERE|a|" to ""))
            val a = desktopArtistAvatarChain(
                answered,
                GallerySite.YANDERE,
                "a",
                linkUrls = { _, _ -> error("档里已有记录还去要链接表") },
                probe = { error("档里已有记录还去发探针") },
                pixivAvatar = { error("档里已有记录还去问 pixiv") },
            )
            assertNull("查过、没有 ⇒ 交回的应该是 null 而不是「没查过」", a.getOrNull())
            assertEquals("命中记录却重复查了档", 1, answered.peeks.size)
            assertTrue("命中记录还落了一次档", answered.writes.isEmpty())

            // B：链接表里有 fanbox、探针答上 ⇒ 落档，键的 source 必须是空串。
            val b = FakeAvatars()
            val got = desktopArtistAvatarChain(
                b,
                GallerySite.SAFEBOORU,
                "setmen",
                linkUrls = { _, _ -> Result.success(listOf(Urls.FANBOX)) },
                probe = { Result.success("https://p.fanbox.cc/a.jpg") },
                pixivAvatar = { error("链接表里没有 pixiv 用户号，不该问它") },
            )
            assertEquals("https://p.fanbox.cc/a.jpg", got.getOrThrow())
            assertEquals(
                "落档的键必须与读侧同轨（source 空串）",
                listOf(FakeAvatars.Quad(GallerySite.SAFEBOORU, "setmen", "", "https://p.fanbox.cc/a.jpg")),
                b.writes,
            )

            // C：没有可探端点 = 定论 ⇒ 落档"查过、没有"，下次不再问。
            val c = FakeAvatars()
            val none = desktopArtistAvatarChain(
                c,
                GallerySite.GELBOORU,
                "whoever",
                linkUrls = { _, _ -> Result.success(listOf(Urls.PIXIV)) },
                probe = { error("没有可探端点不该发第 2 笔") },
                pixivAvatar = { Result.success(null) },
            )
            assertNull("没有可探端点是定论，不是失败", none.exceptionOrNull())
            assertNull(none.getOrNull())
            assertEquals(listOf(FakeAvatars.Quad(GallerySite.GELBOORU, "whoever", "", null)), c.writes)

            // D：链接表这一笔就失败了 ⇒ 不是定论，**不许**落档（落档 = 一次网络抖动把这位判死）。
            val d = FakeAvatars()
            val boom = desktopArtistAvatarChain(
                d,
                GallerySite.YANDERE,
                "flaky",
                linkUrls = { _, _ -> Result.failure(java.io.IOException("站方没答上")) },
                probe = { error("第 1 笔失败了不该有第 2 笔") },
                pixivAvatar = { error("第 1 笔失败了不该有第 2 笔") },
            )
            assertNotNull(
                "请求失败被吞成了「没有」—— 屏上分不出「没头像」与「没走通」全靠这一枚",
                boom.exceptionOrNull(),
            )
            assertTrue("失败也落了档", d.writes.isEmpty())

            // E：端点答上了但没有可用头像 = 定论（站方回 200 而 JSON 里没那张脸）。
            val e = FakeAvatars()
            val faceless = desktopArtistAvatarChain(
                e,
                GallerySite.YANDERE,
                "faceless",
                linkUrls = { _, _ -> Result.success(listOf(Urls.MASTODON)) },
                probe = { Result.success(null) },
                pixivAvatar = { error("链接表里没有 pixiv 用户号，不该问它") },
            )
            assertNull(faceless.getOrThrow())
            assertEquals(listOf(FakeAvatars.Quad(GallerySite.YANDERE, "faceless", "", null)), e.writes)

            // F：探针这一笔失败 ⇒ 同样不是定论。
            val f = FakeAvatars()
            val probeFailed = desktopArtistAvatarChain(
                f,
                GallerySite.YANDERE,
                "flaky2",
                linkUrls = { _, _ -> Result.success(listOf(Urls.FANBOX)) },
                probe = { Result.failure(java.io.IOException("403")) },
                pixivAvatar = { error("链接表里没有 pixiv 用户号，不该问它") },
            )
            assertNotNull(probeFailed.exceptionOrNull())
            assertTrue("探针失败也落了档", f.writes.isEmpty())
        }
    }
    /** ⑧′ 批 42：链接表里有 pixiv 用户号 ⇒ 链**先问 pixiv**，答上了就不发探针那一笔。 */
    @Test
    fun pixiv那一档先问而答上了不再发探针() {
        runBlocking {
            val avatars = FakeAvatars()
            val asked = mutableListOf<Long>()
            val outcome = desktopArtistAvatarChain(
                avatars,
                GallerySite.YANDERE,
                "pixivonly",
                linkUrls = { _, _ -> Result.success(listOf(Urls.PIXIV)) },
                probe = { error("pixiv 答上了就不该有探针那一笔") },
                pixivAvatar = { userId ->
                    asked += userId
                    Result.success("https://px1.pixiv.net/a170.jpg")
                },
            )
            // 用户号是从共享那颗 GalleryArtistLinks.pixivUserId 取的（桌面不重述规则）：Urls.PIXIV 里是 123
            assertEquals(listOf(123L), asked)
            assertEquals("https://px1.pixiv.net/a170.jpg", outcome.getOrThrow())
            assertEquals(
                "pixiv 拿到的地址要落本机档（键的 source 恒空串）",
                listOf(FakeAvatars.Quad(GallerySite.YANDERE, "pixivonly", "", "https://px1.pixiv.net/a170.jpg")),
                avatars.writes,
            )
        }
    }

    /** ⑧″ pixiv 那一笔**失败**而再没别的端点 ⇒ 交 failure 且不落档（"没问到"不是"没有"）。 */
    @Test
    fun pixiv那一笔失败不许退成没有头像() {
        runBlocking {
            val avatars = FakeAvatars()
            val outcome = desktopArtistAvatarChain(
                avatars,
                GallerySite.GELBOORU,
                "flaky3",
                linkUrls = { _, _ -> Result.success(listOf(Urls.PIXIV)) },
                probe = { error("链接表里没有可探端点") },
                pixivAvatar = { Result.failure(java.io.IOException("403")) },
            )
            assertNotNull(
                "pixiv 那一笔的失败被吞成了「没有」—— 落档就等于把这位永久判成只剩字母座",
                outcome.exceptionOrNull(),
            )
            assertTrue("失败也落了档", avatars.writes.isEmpty())
        }
    }

    /** ⑧ pixiv 答上了而没那张脸 ⇒ 按次序退到探针那一档（两条来路是接力不是互斥）。 */
    @Test
    fun pixiv没那张脸时按次序退到探针那一档() {
        runBlocking {
            val avatars = FakeAvatars()
            var probed = 0
            val outcome = desktopArtistAvatarChain(
                avatars,
                GallerySite.SAFEBOORU,
                "relay",
                linkUrls = { _, _ -> Result.success(listOf(Urls.PIXIV, Urls.FANBOX)) },
                probe = { probed++; Result.success("https://p.fanbox.cc/b.jpg") },
                pixivAvatar = { Result.success(null) },
            )
            assertEquals(1, probed)
            assertEquals("https://p.fanbox.cc/b.jpg", outcome.getOrThrow())
            assertEquals(
                listOf(FakeAvatars.Quad(GallerySite.SAFEBOORU, "relay", "", "https://p.fanbox.cc/b.jpg")),
                avatars.writes,
            )
        }
    }
}
