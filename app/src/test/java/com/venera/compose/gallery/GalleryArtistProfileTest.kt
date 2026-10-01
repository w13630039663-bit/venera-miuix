package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryArtistLinkPlatform
import com.venera.compose.gallery.domain.GalleryArtistLinks
import com.venera.compose.gallery.domain.GalleryArtistProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画师介绍页的判据（批次 Q+R · 批量 5）。
 *
 * **形状来自读数，不来自"这家平台一般长什么样"**。2026-10-01 把仓里已有真样本
 * （`_probe/artist_sample.json` 与 `_probe/hub/` 那七份画师记录）捞了一遍第三方域名
 * （`_probe/third_party_url_shapes.cjs`，4 条）：
 * - instagram 三种写法全是**单段 handle**（`instagram.com/jyaian3399`、`www.instagram.com/pavanioficial/`、
 *   `…/zerotwo_ov/`）；`/reel/`、`/stories/`、`/share/profile/` **一条都没有**。
 *   → 判据只认"单段 handle"，多段的（内容页、分享页、平台自家页）一律 OTHER。宁可漏一枚，
 *     不可把"这张图"挂成"这位人" —— 与 pixiv `/artworks/N` 判 OTHER 同一条口径。
 * - tumblr 只出现**子域即创作者**那一种（`j-pegillustmain.tumblr.com/`）。
 * - youtube 一条都没有：只收结构上唯一的两条（`@handle`、`/channel/UC…`），其余 OTHER。
 */
class GalleryArtistProfileTest {

    @Test
    fun `instagram 只认单段 handle 其余一律不是本人页`() {
        // 三条都来自真样本。
        assertEquals(
            GalleryArtistLinkPlatform.INSTAGRAM,
            GalleryArtistLinks.platformOf("http://instagram.com/jyaian3399"),
        )
        assertEquals(
            GalleryArtistLinkPlatform.INSTAGRAM,
            GalleryArtistLinks.platformOf("https://www.instagram.com/pavanioficial/"),
        )
        assertEquals(
            GalleryArtistLinkPlatform.INSTAGRAM,
            GalleryArtistLinks.platformOf("https://www.instagram.com/zerotwo_ov/"),
        )
        // 单条内容页、分享出去的链接、平台自家页面：挂了就是把"这张图"冒充成"这位人"。
        listOf(
            "https://www.instagram.com/reel/CxYz1234/",
            "https://www.instagram.com/p/CxYz1234/",
            "https://www.instagram.com/stories/mochida/123456",
            "https://www.instagram.com/share/profile/abc/",
            "https://www.instagram.com/reels/",
            "https://www.instagram.com/explore/tags/cat/",
            "https://www.instagram.com/",
            "https://www.instagram.com/accounts/login/",
        ).forEach { url ->
            assertEquals("这一条不该被认成某位本人的页面：$url", GalleryArtistLinkPlatform.OTHER, GalleryArtistLinks.platformOf(url))
        }
    }

    @Test
    fun `youtube 频道认得出而单条视频不认`() {
        assertEquals(
            GalleryArtistLinkPlatform.YOUTUBE,
            GalleryArtistLinks.platformOf("https://www.youtube.com/@mochida"),
        )
        assertEquals(
            GalleryArtistLinkPlatform.YOUTUBE,
            GalleryArtistLinks.platformOf("https://m.youtube.com/@mochida/featured"),
        )
        assertEquals(
            GalleryArtistLinkPlatform.YOUTUBE,
            GalleryArtistLinks.platformOf("https://www.youtube.com/channel/UC1234567890abcdef"),
        )
        listOf(
            "https://www.youtube.com/watch?v=abc123",
            "https://youtu.be/abc123",
            "https://www.youtube.com/shorts/abc123",
            "https://www.youtube.com/user/oldform",
            "https://www.youtube.com/",
        ).forEach { url ->
            assertEquals("这一条不是频道页：$url", GalleryArtistLinkPlatform.OTHER, GalleryArtistLinks.platformOf(url))
        }
    }

    @Test
    fun `tumblr 子域就是创作者而平台页面不是`() {
        // 样本里出现的就是这一种（`j-pegillustmain.tumblr.com/`）。
        assertEquals(
            GalleryArtistLinkPlatform.TUMBLR,
            GalleryArtistLinks.platformOf("https://j-pegillustmain.tumblr.com/"),
        )
        // **不带路径段**那一形钉在这里：站方真会给只到域名的地址，而 host 解析写错时它的表现
        // 是"这条判成 OTHER" —— 屏上只少一枚胶囊，没人会来报（本仓最忌的那种静默）。
        assertEquals(
            GalleryArtistLinkPlatform.TUMBLR,
            GalleryArtistLinks.platformOf("https://j-pegillustmain.tumblr.com"),
        )
        // 同一条语义的第二种写法（结构上仍是"这个人"），真样本里没有，但认它不会挂错人。
        assertEquals(
            GalleryArtistLinkPlatform.TUMBLR,
            GalleryArtistLinks.platformOf("https://www.tumblr.com/mochida"),
        )
        listOf(
            "https://www.tumblr.com/dashboard",
            "https://www.tumblr.com/explore",
            "https://www.tumblr.com/",
        ).forEach { url ->
            assertEquals("这一条不是某位本人的 blog：$url", GalleryArtistLinkPlatform.OTHER, GalleryArtistLinks.platformOf(url))
        }
    }

    @Test
    fun `介绍页的链接排固定平台次序且认不出的照摆`() {
        // 故意打乱送进来：结果次序必须由判据定，不能跟着站方给的顺序走。
        val plan = GalleryArtistProfile.profilePlan(
            listOf(
                "https://mochida.tumblr.com/",
                "https://www.dlsite.com/works/announce/PL01234567",
                "https://www.instagram.com/mochida/",
                "https://www.pixiv.net/users/7075448",
                "https://aoi-sakura.fanbox.cc/",
                "https://x.com/mochida",
            ),
        )
        assertEquals(
            listOf(
                GalleryArtistLinkPlatform.PIXIV,
                GalleryArtistLinkPlatform.TWITTER,
                GalleryArtistLinkPlatform.INSTAGRAM,
                GalleryArtistLinkPlatform.FANBOX,
                GalleryArtistLinkPlatform.TUMBLR,
                GalleryArtistLinkPlatform.OTHER,
            ),
            plan.map { it.platform },
        )
        // 认不出平台的那一枚**要摆**，标签给域名尾巴 —— 画师自己挂的链接我们没资格判成"不重要"。
        assertEquals("www.dlsite.com", GalleryArtistLinks.chipLabel(plan.last()))
    }

    @Test
    fun `介绍页不受画师行那两枚上限约束`() {
        val plan = GalleryArtistProfile.profilePlan(
            listOf(
                "https://www.pixiv.net/users/7075448",
                "https://x.com/mochida",
                "https://www.instagram.com/mochida/",
                "https://aoi-sakura.fanbox.cc/",
                "https://mochida.tumblr.com/",
                "https://www.youtube.com/@mochida",
            ),
        )
        assertEquals(6, plan.size)
        assertEquals(2, GalleryArtistLinks.iconPlan(
            listOf(
                "https://www.pixiv.net/users/7075448",
                "https://x.com/mochida",
                "https://www.instagram.com/mochida/",
            ),
        ).size)
    }

    @Test
    fun `同一账号的重复链接只留一枚`() {
        // 实测 lichiko 就是 twitter.com 与 x.com 两条并给；尾斜杠与 www 的差别也不该多摆一枚。
        val plan = GalleryArtistProfile.profilePlan(
            listOf(
                "https://twitter.com/mochida",
                "https://x.com/Mochida",
                "https://mochida.tumblr.com",
                "https://mochida.tumblr.com/",
                "https://www.pixiv.net/member.php?id=7075448",
                "https://www.pixiv.net/users/7075448/",
            ),
        )
        assertEquals(
            listOf(
                GalleryArtistLinkPlatform.PIXIV,
                GalleryArtistLinkPlatform.TWITTER,
                GalleryArtistLinkPlatform.TUMBLR,
            ),
            plan.map { it.platform },
        )
    }

    @Test
    fun `出处只在缺 pixiv 时补一枚且排在站方之后`() {
        val withSource = GalleryArtistProfile.profilePlan(
            urls = listOf("https://x.com/mochida"),
            source = "https://mochida.fanbox.cc/posts/123456",
        )
        assertEquals(
            listOf(GalleryArtistLinkPlatform.TWITTER, GalleryArtistLinkPlatform.FANBOX),
            withSource.map { it.platform },
        )
        // 出处补进来的那一枚**规范化到创作者主页**（`/posts/123456` 是这张帖，不是"这位"），
        // 并且同一档只留一枚 —— 站方已经给了 fanbox 时出处那条不能再摆一遍。
        val pixivAlready = GalleryArtistProfile.profilePlan(
            urls = listOf("https://aoi-sakura.fanbox.cc/"),
            source = "https://mochida.fanbox.cc/posts/123456",
        )
        assertEquals(1, pixivAlready.count { it.platform == GalleryArtistLinkPlatform.FANBOX })
        assertEquals(
            "https://aoi-sakura.fanbox.cc/",
            pixivAlready.first { it.platform == GalleryArtistLinkPlatform.FANBOX }.url,
        )
    }

    @Test
    fun `作品页反查来的那一枚带账号`() {
        val plan = GalleryArtistProfile.profilePlan(
            urls = listOf("https://x.com/mochida"),
            artworkAuthorUrl = "https://www.pixiv.net/users/49622591",
            artworkAuthorAccount = "tokenbox",
        )
        val pixiv = plan.first { it.platform == GalleryArtistLinkPlatform.PIXIV }
        assertEquals("pixiv tokenbox", GalleryArtistLinks.chipLabel(pixiv))
    }

    @Test
    fun `别名只收全等指向正名的去自身去空并限八条`() {
        val readout = GalleryArtistProfile.aliasReadout(
            otherNames = listOf("u_u", "セトマン", "setmen", "  ", "a", "b", "c", "d", "e", "f", "g", "h", "i"),
            canonicalName = "setmen",
        )
        // 自身那条（全等，大小写无关）与空白都不进；第 9 条起算进不下的那一档。
        assertEquals(listOf("u_u", "セトマン", "a", "b", "c", "d", "e", "f"), readout.names)
        assertEquals(3, readout.hiddenCount)
        assertTrue(readout.visible)
    }

    @Test
    fun `别名一条都没有时这一行整块不摆`() {
        val readout = GalleryArtistProfile.aliasReadout(listOf("setmen"), "setmen")
        assertFalse("站方只给出正名自身，屏上不能出现一个空的别名行", readout.visible)
        assertEquals(0, readout.names.size)
    }

    @Test
    fun `别名按大小写无关去重`() {
        assertEquals(listOf("U_U"), GalleryArtistProfile.aliasReadout(listOf("U_U", "u_u"), "setmen").names)
    }

    // ── 名字底下那一行（2026-10-01：正名与别名从两行合成一行）──────────────────────────

    @Test
    fun `别名行把正名与别名并成一行`() {
        val readout = GalleryArtistProfile.aliasReadout(listOf("u_u", "セトマン"), "setmen")
        assertEquals(
            "站方记的正名是 setmen · 别名：u_u、セトマン",
            GalleryArtistProfile.aliasLine("setmen", readout),
        )
    }

    @Test
    fun `别名行在超限时把溢出计数挂在同一行尾`() {
        val readout = GalleryArtistProfile.aliasReadout(
            otherNames = (1..11).map { "a$it" },
            canonicalName = "setmen",
        )
        assertEquals(8, readout.names.size)
        assertEquals(3, readout.hiddenCount)
        assertEquals(
            "别名：" + (1..8).map { "a$it" }.joinToString("、") + "（另有 3 个）",
            GalleryArtistProfile.aliasLine(null, readout),
        )
    }

    @Test
    fun `别名行两段都没货时整行不出现`() {
        // 这是那条红线：屏上永不出现"这位没有别名"，所以这里必须回 null 而不是空串。
        assertEquals(null, GalleryArtistProfile.aliasLine(null, GalleryArtistProfile.AliasReadout(emptyList(), 0)))
        // 只有空白也算没货（站方真会给一串空格）。
        assertEquals(null, GalleryArtistProfile.aliasLine("   ", GalleryArtistProfile.AliasReadout(emptyList(), 0)))
    }

    @Test
    fun `别名行只有正名时不留分隔符`() {
        assertEquals(
            "站方记的正名是 セトマン",
            GalleryArtistProfile.aliasLine("セトマン", GalleryArtistProfile.AliasReadout(emptyList(), 0)),
        )
    }
}
