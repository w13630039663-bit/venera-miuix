package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryAvatarEndpointKind
import com.venera.compose.gallery.domain.GalleryArtistLinkPlatform
import com.venera.compose.gallery.domain.GalleryArtistLinks
import com.venera.compose.gallery.domain.GalleryArtistAvatarProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 从**帖子自带的出处（source）**派生画师入口与头像探针（批次 L · L9）。
 *
 * 为什么要有这一路（用户 2026-09-30 拍板"做，从 source 派生"）：Gelbooru 侧没有任何匿名可读的
 * 画师外链端点（DAPI `s=artist` 401、原生画师页的 `name=` 实测根本不生效），唯一的第三方供体
 * Danbooru 从当天出口卡盾。而**出处那一栏实测覆盖 18/20**（pixiv 6 / baraag 7 / twitter 2 …），
 * 它是站方逐条记在帖子上的，不需要再问一次"这位是谁"。
 *
 * 一条红线贯穿全部判据：**出处指向作品 ≠ 出处指向这个人**。
 * `pixiv.net/artworks/123` 里那串是作品号，拿它当用户号去要头像，就是**把别人的脸挂到这位名下**，
 * 而且全程不报错。所以认不出的形状一律 null，宁缺。
 */
class GalleryArtistSourceTest {

    @Test
    fun `出处里能认出人的三种形状`() {
        // pixiv 个人主页（直接给号，最干净）
        assertEquals(
            GalleryArtistLinkPlatform.PIXIV,
            GalleryArtistLinks.fromSource("http://www.pixiv.net/member.php?id=80903725")?.platform,
        )
        assertEquals(
            GalleryArtistLinkPlatform.PIXIV,
            GalleryArtistLinks.fromSource("https://www.pixiv.net/en/users/49622591")?.platform,
        )
        // 推特状态页：账号段就是发帖人，规范化到主页（入口要落在"这个人"上，不是那条状态）
        assertEquals(
            "https://x.com/bartolomeobari2",
            GalleryArtistLinks.fromSource("https://twitter.com/bartolomeobari2/status/1418704980316405764")?.url,
        )
        // fanbox 文章页：子域就是创作者，同样规范化到创作者页
        assertEquals(
            "https://setmen.fanbox.cc/",
            GalleryArtistLinks.fromSource("https://setmen.fanbox.cc/posts/1234567")?.url,
        )
    }

    @Test
    fun `作品页与裸图一律认不出 不许当成这个人`() {
        listOf(
            // **作品号不是用户号**：这一条是整个判据层最贵的一个坑。
            "https://www.pixiv.net/artworks/113455481",
            "http://www.pixiv.net/member_illust.php?mode=medium&illust_id=80903725",
            "https://i.pximg.net/img-original/img/2026/08/03/00/00/35/147950802_p0.jpg",
            // 与画师无关的那一大类出处
            "https://www.dlsite.com/maniax/work/=/product_id/RJ01712818.html",
            "https://mega.nz/file/xxxx",
            "https://www.bilibili.com/video/BV1xx",
            "https://twitter.com/intent/tweet?url=x",
            "https://x.com/i/lists/1234",
            "",
            "not a url",
        ).forEach { src ->
            assertNull("这一条不该被认成任何人的主页：$src", GalleryArtistLinks.fromSource(src))
        }
    }

    @Test
    fun `出处派生的那条不会盖过站方登记的记录`() {
        // 同一位既有站方 artist 记录、又有出处时，入口以站方记录为准（那是画师页自己声明的），
        // 出处只补站方没给的那一档。头像也从站方的用户号取 —— 出处可能是"这张图的发布处"。
        val fromSite = listOf("https://www.pixiv.net/users/7075448", "https://twitter.com/Setmen_uU")
        val plan = GalleryArtistLinks.iconPlan(fromSite, source = "https://www.pixiv.net/users/99887766")
        assertEquals(
            listOf(GalleryArtistLinkPlatform.PIXIV, GalleryArtistLinkPlatform.TWITTER),
            plan.map { it.platform },
        )
        assertEquals("https://www.pixiv.net/users/7075448", plan.first().url)
    }

    @Test
    fun `站方没登记时出处单独成一枚入口 同平台不重复摆`() {
        // Gelbooru 那一侧"站方什么都没有"是常态（画师端点全要凭据），出处就是唯一那一条。
        val only = GalleryArtistLinks.iconPlan(emptyList(), source = "https://setmen.fanbox.cc/posts/1")
        assertEquals(listOf(GalleryArtistLinkPlatform.FANBOX), only.map { it.platform })
        assertEquals("https://setmen.fanbox.cc/", only.first().url)

        // 同平台的第二条不再摆：两枚都写 pixiv 在屏上读起来是"这里挂了两位"。
        val same = GalleryArtistLinks.iconPlan(
            listOf("https://www.pixiv.net/users/7075448"),
            source = "https://www.pixiv.net/en/users/99887766",
        )
        assertEquals(listOf(GalleryArtistLinkPlatform.PIXIV), same.map { it.platform })
        assertEquals("https://www.pixiv.net/users/7075448", same.first().url)

        // 认不出的出处（作品页那一类）不进结果，站方给的那条也不受影响。
        val artwork = GalleryArtistLinks.iconPlan(
            listOf("https://twitter.com/Setmen_uU"),
            source = "https://www.pixiv.net/artworks/113455481",
        )
        assertEquals(listOf(GalleryArtistLinkPlatform.TWITTER), artwork.map { it.platform })
    }

    @Test
    fun `fanbox 的探针要的是子域而不是数字号`() {
        // 实测：`creatorId=setmen` 回 200 带 iconUrl；`creatorId=7075448`（pixiv 用户号）回 400。
        val hit = GalleryArtistAvatarProbe.fanboxCreator("https://setmen.fanbox.cc/posts/123")
        assertEquals("https://api.fanbox.cc/creator.get?creatorId=setmen", hit?.endpoint)
        // **Origin 不是可选项**：2026-09-30 14:05 逐档实测，那台 api 对「不带 Origin」「只带 Referer」
        // 一律回 400 general_error，只有带 `Origin: https://<子域>.fanbox.cc`（www 那档也行）才 200。
        // 于是探针必须把这一条一起交出去 —— 只给地址的探针在取数层会稳定失败，而且失败得毫不起眼。
        assertEquals("https://setmen.fanbox.cc", hit?.origin)
        assertNull(GalleryArtistAvatarProbe.fanboxCreator("https://www.pixiv.net/fanbox/creator/123456"))
    }

    @Test
    fun `Mastodon 系的探针带实例与 handle`() {
        assertEquals(
            "https://baraag.net/api/v1/accounts/lookup?acct=BittyCat",
            GalleryArtistAvatarProbe.mastodonLookup("https://baraag.net/@BittyCat/111530355840891666")?.endpoint,
        )
        assertEquals(
            "https://pawoo.net/api/v1/accounts/lookup?acct=lalazyt",
            GalleryArtistAvatarProbe.mastodonLookup("https://pawoo.net/@lalazyt")?.endpoint,
        )
        // 实测匿名直取 200，不需要任何 Origin/Referer —— 那一档留 null 就是"别多带东西"。
        assertNull(GalleryArtistAvatarProbe.mastodonLookup("https://pawoo.net/@lalazyt")?.origin)
        // 认不出 handle 的（没有 @ 段、或 @ 后面是首页那类）一律 null
        assertNull(GalleryArtistAvatarProbe.mastodonLookup("https://baraag.net/about"))
        assertNull(GalleryArtistAvatarProbe.mastodonLookup("https://www.pixiv.net/users/1"))
    }

    @Test
    fun `探针交出的端点自带身份 取数层照它选解析器`() {
        // 为什么要这一个字段：端点串里"哪家 api"只能靠嗅 host 认，而嗅出来的那条一旦站方换域名就
        // 静默走错解析器（走错的表现是"读不懂 → 抛错"，看起来像站方坏了）。身份由派生它的那一路写明。
        assertEquals(
            GalleryAvatarEndpointKind.FANBOX,
            GalleryArtistAvatarProbe.fanboxCreator("https://setmen.fanbox.cc/posts/1")?.kind,
        )
        assertEquals(
            GalleryAvatarEndpointKind.MASTODON,
            GalleryArtistAvatarProbe.mastodonLookup("https://pawoo.net/@lalazyt")?.kind,
        )
    }

    @Test
    fun `作品号能从出处里取出来 用户号不算`() {
        // 这一条是"要不要多花一笔请求去反查作者"的闸门：认得出作品号才有可反查的东西。
        assertEquals(109123828L, GalleryArtistLinks.pixivArtworkId("https://www.pixiv.net/artworks/109123828"))
        assertEquals(109123828L, GalleryArtistLinks.pixivArtworkId("https://www.pixiv.net/en/artworks/109123828"))
        assertEquals(
            80903725L,
            GalleryArtistLinks.pixivArtworkId("http://www.pixiv.net/member_illust.php?mode=medium&illust_id=80903725"),
        )
        listOf(
            // 用户号不是作品号：那一页本来就是"这个人"，没有可反查的东西。
            "https://www.pixiv.net/users/7075448",
            "http://www.pixiv.net/member.php?id=80903725",
            "https://www.pixiv.net/artworks/",
            "https://www.pixiv.net/artworks/abc",
            "",
        ).forEach { src ->
            assertNull("这一条里没有可反查的作品号：$src", GalleryArtistLinks.pixivArtworkId(src))
        }
    }

    /**
     * 裸图出处的文件名里本来就带着作品号（批次 M · M4）。
     *
     * 用户 2026-09-30 报「img.pixiv.net 这类出处的画师信息有时能显示有时不能」。旧判据把裸图
     * 一律判 null，理由是"它不是一页作品页"—— 那个理由站不住：pixiv 图床的文件名是
     * `<作品号>_p<页>.<扩展名>`，而站方的 `ajax/illust/{作品号}` 匿名答得上作者。设备取到的
     * 100 条 yande.re 样本里，出处落在 www.pixiv.net 的 26 条、落在 i.pximg.net 的 8 条，
     * 后者全被旧判据丢掉 —— "有时不能"就是这 8 条。
     */
    @Test
    fun `裸图出处的文件名里也认得出作品号`() {
        assertEquals(
            147950802L,
            GalleryArtistLinks.pixivArtworkId("https://i.pximg.net/img-original/img/2026/08/03/00/00/35/147950802_p0.jpg"),
        )
        // 缩略档：多出来一段 `_square1200` 也认，号还在老位置。
        assertEquals(
            150281277L,
            GalleryArtistLinks.pixivArtworkId("https://i.pximg.net/img-master/img/2026/08/03/00/00/35/150281277_p0_square1200.jpg"),
        )
        // 多页那一档：`_p1` 与 `_p0` 是同一件作品，反查作者不该因为翻到第二页就失败。
        assertEquals(
            118833771L,
            GalleryArtistLinks.pixivArtworkId("https://i.pximg.net/img-original/img/2026/08/03/00/00/35/118833771_p1.png"),
        )
        // 动图那一档：扩展名是 zip。
        assertEquals(
            60474831L,
            GalleryArtistLinks.pixivArtworkId("https://i.pximg.net/img-zip-ugoira/img/2026/08/03/00/00/35/60474831_ugoira600x600.zip"),
        )
        // 用户点名的那一档：老式图床子域 + img/<账号>/ 目录。
        assertEquals(50878459L, GalleryArtistLinks.pixivArtworkId("http://img.pixiv.net/img/setmen/50878459_p0.png"))
        // 更老的一档：文件名里连 `_p页` 都没有，只剩裸号。
        assertEquals(1234567L, GalleryArtistLinks.pixivArtworkId("http://img42.pixiv.net/img/tehu48/1234567.jpg"))
        // 大小写不该成为判据（站方两处都给过大写扩展名）。
        assertEquals(
            147950802L,
            GalleryArtistLinks.pixivArtworkId("https://i.pximg.net/img-original/img/2026/08/03/00/00/35/147950802_P0.JPG"),
        )
    }

    @Test
    fun `没有作品号的那些形状仍然一律 null 不许猜`() {
        listOf(
            // 目录列表：站方确实会把这一档当"个人主页"给出来，里面没有任何号。
            "https://img42.pixiv.net/img/tehu48/",
            "https://i1.pixiv.net/img25/img/xerd008ss/",
            // 文件名里没号（封面那一类），以及号的位置根本不是数字。
            "https://i.pximg.net/img-original/cover.png",
            "https://i.pximg.net/img-master/img/2026/08/03/00/00/35/abc_p0.jpg",
            // 别家图床不许认：推特的图床文件名里也可能出现一串数字，
            // 拿它去问 pixiv 的 ajax 就是把别人的作品号当成这件的。
            "https://pbs.twimg.com/media/Gxxxxx150281277_p0.jpg",
            // 路径中间那几段日期是纯数字，只看最后一段才不会被它骗到。
            "https://i.pximg.net/img-original/img/2026/08/03/00/00/35/",
            "",
        ).forEach { src ->
            assertNull("这一条里没有可反查的作品号：$src", GalleryArtistLinks.pixivArtworkId(src))
        }
    }

    @Test
    fun `作品页反查出的作者排在站方记录与出处之后`() {
        val author = "https://www.pixiv.net/users/7075448"
        // ① 站方与出处都没有 pixiv → 反查那枚补上，且**带账号**（用户 2026-09-30 拍板）。
        val filled = GalleryArtistLinks.iconPlan(
            urls = listOf("https://twitter.com/Setmen_uU"),
            source = "https://www.pixiv.net/artworks/113455481",
            artworkAuthorUrl = author,
            artworkAuthorAccount = "tokenbox",
        )
        assertEquals(listOf(GalleryArtistLinkPlatform.TWITTER, GalleryArtistLinkPlatform.PIXIV), filled.map { it.platform })
        assertEquals(author, filled.last().url)
        assertEquals("tokenbox", filled.last().account)

        // ② 站方已经给了 pixiv → 反查那枚不再挂（两枚 pixiv 胶囊读起来是"这里挂了两位"）。
        val siteWins = GalleryArtistLinks.iconPlan(
            urls = listOf("https://www.pixiv.net/users/12345"),
            source = "https://www.pixiv.net/artworks/113455481",
            artworkAuthorUrl = author,
            artworkAuthorAccount = "tokenbox",
        )
        assertEquals(listOf("https://www.pixiv.net/users/12345"), siteWins.map { it.url })

        // ③ 没做反查（出处不是作品页，或那一笔失败了）→ 结果与今天一致，不多摆空位。
        val none = GalleryArtistLinks.iconPlan(
            urls = listOf("https://twitter.com/Setmen_uU"),
            source = "https://baraag.net/@BittyCat/111",
        )
        assertEquals(listOf(GalleryArtistLinkPlatform.TWITTER), none.map { it.platform })
    }

    @Test
    fun `探针只认 https 的公开端点 不带任何凭据`() {
        // 这一条钉的是安全面：派生出来的地址会被 app 直接发出去。
        val url = GalleryArtistAvatarProbe.fanboxCreator("https://a-b_c.fanbox.cc/x")
        assertEquals("https://api.fanbox.cc/creator.get?creatorId=a-b_c", url?.endpoint)
        assertEquals("https://a-b_c.fanbox.cc", url?.origin)
        assertEquals(null, GalleryArtistAvatarProbe.mastodonLookup("http://baraag.net/@x"))
    }
}
