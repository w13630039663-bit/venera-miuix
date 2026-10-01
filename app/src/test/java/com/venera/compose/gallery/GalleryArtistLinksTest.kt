package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryArtistFollows
import com.venera.compose.gallery.domain.GalleryArtistLinkPlatform
import com.venera.compose.gallery.domain.GalleryArtistLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画师外链的**平台判定与图标摆放**判据（批次 L）。
 *
 * fixtures 全部来自 2026-09-30 的真读数（yande.re `artist.json` 的 `urls`、Gelbooru 原生画师记录页、
 * SauceNAO 那侧混着给的形态），不是照着"常见的写法"造的 —— 这一层的价值恰恰在于
 * 它认的是**站方真会给出来的那批脏串**。三条不能松的口径：
 *
 * - **老式 pixiv 图床目录不算个人主页**（`img42.pixiv.net/img/tehu48/`、`i1.pixiv.net/img25/…`）：
 *   那是一条目录列表，不是"这位本人"，摆进画师行就是把一个不像主页的页面伪装成主页。
 * - **pixiv 作品页的编号不是用户 id**（`/artworks/1234567`）：认错一次就把别人的头像挂到这位名下。
 * - **摆放顺序定死**（pixiv > 推 > fanbox），不跟站方给的原序 —— 同一个人两条推特链很常见，
 *   跟着原序就会出现"这次摆 fanbox 下次摆推"的读不稳。
 */
class GalleryArtistLinksTest {

    @Test
    fun `pixiv 的四种真形态都认成 pixiv`() {
        assertEquals(
            GalleryArtistLinkPlatform.PIXIV,
            GalleryArtistLinks.platformOf("https://www.pixiv.net/users/7075448"),
        )
        assertEquals(
            GalleryArtistLinkPlatform.PIXIV,
            GalleryArtistLinks.platformOf("https://www.pixiv.net/en/users/49622591"),
        )
        // Gelbooru 原生记录里 14/14 都是这一种老写法。
        assertEquals(
            GalleryArtistLinkPlatform.PIXIV,
            GalleryArtistLinks.platformOf("http://www.pixiv.net/member.php?id=1360347"),
        )
        assertEquals(
            GalleryArtistLinkPlatform.PIXIV,
            GalleryArtistLinks.platformOf("https://pixiv.me/setmen"),
        )
    }

    @Test
    fun `pixiv 的老图床目录与作品页都不是个人主页`() {
        // 实测原文：Gelbooru 画师记录里挨着 member.php 给的就是这条；yande.re 某条记录里也有 i1 那台。
        assertEquals(
            GalleryArtistLinkPlatform.OTHER,
            GalleryArtistLinks.platformOf("http://img42.pixiv.net/img/tehu48/"),
        )
        assertEquals(
            GalleryArtistLinkPlatform.OTHER,
            GalleryArtistLinks.platformOf("http://i1.pixiv.net/img25/img/xerd008ss/"),
        )
        // 作品页（SauceNAO 的 ext_urls 常给）不是"这位的主页"，摆错语义就变了。
        assertEquals(
            GalleryArtistLinkPlatform.OTHER,
            GalleryArtistLinks.platformOf("https://www.pixiv.net/artworks/120334746"),
        )
        // sketch 与 stacc 是 pixiv 名下的个人页，认成 pixiv 但**给不出用户 id**（形态不同源）。
        assertEquals(GalleryArtistLinkPlatform.PIXIV, GalleryArtistLinks.platformOf("https://sketch.pixiv.net/@seoyul"))
        assertEquals(GalleryArtistLinkPlatform.PIXIV, GalleryArtistLinks.platformOf("https://www.pixiv.net/stacc/q18607"))
    }

    @Test
    fun `认不出的服务一律 OTHER 不猜`() {
        listOf(
            "https://yunting.artstation.com/",
            "https://linktr.ee/lllichiko",
            "http://bekkankou.at.infoseek.co.jp/index.html",
            "https://pawoo.net/@lalazyt",
            "https://weibo.com/u/1627496283",
            "http://rainboy.jp/",
            "http://nijie.info/members.php?id=1529",
            "",
            "不是个链接",
        ).forEach { url ->
            assertEquals("这一条不该被认成任何已知平台：$url", GalleryArtistLinkPlatform.OTHER, GalleryArtistLinks.platformOf(url))
        }
        // 这两条 fanbox 形态都来自今天的真读数（前者是 artist.json 给的，后者是站方另一写法）。
        assertEquals(GalleryArtistLinkPlatform.FANBOX, GalleryArtistLinks.platformOf("https://aoi-sakura.fanbox.cc/"))
        assertEquals(GalleryArtistLinkPlatform.FANBOX, GalleryArtistLinks.platformOf("https://www.pixiv.net/fanbox/creator/123456"))
    }

    @Test
    fun `推特与 X 是同一家 且账号页以外的路径不算主页`() {
        assertEquals(GalleryArtistLinkPlatform.TWITTER, GalleryArtistLinks.platformOf("https://twitter.com/CheLA_777"))
        assertEquals(GalleryArtistLinkPlatform.TWITTER, GalleryArtistLinks.platformOf("https://x.com/llichiko"))
        // 站方真给过 http 的老写法。
        assertEquals(GalleryArtistLinkPlatform.TWITTER, GalleryArtistLinks.platformOf("http://twitter.com/stealyy"))
        assertEquals("CheLA_777", GalleryArtistLinks.twitterHandle("https://twitter.com/CheLA_777"))
        assertEquals("llichiko", GalleryArtistLinks.twitterHandle("https://x.com/llichiko/media"))
        // 这几条不是"某个人"，挂到画师行上就是把平台自己的页面冒充成画师主页。
        assertNull(GalleryArtistLinks.twitterHandle("https://twitter.com/home"))
        assertNull(GalleryArtistLinks.twitterHandle("https://twitter.com/intent/tweet?url=x"))
        assertNull(GalleryArtistLinks.twitterHandle("https://twitter.com/search?q=1"))
        assertNull(GalleryArtistLinks.twitterHandle("https://x.com/i/lists/1234"))
    }

    @Test
    fun `pixiv 用户 id 只从个人主页形态取`() {
        assertEquals(7075448L, GalleryArtistLinks.pixivUserId("https://www.pixiv.net/users/7075448"))
        assertEquals(49622591L, GalleryArtistLinks.pixivUserId("https://www.pixiv.net/en/users/49622591"))
        assertEquals(1360347L, GalleryArtistLinks.pixivUserId("http://www.pixiv.net/member.php?id=1360347"))
        // **作品编号不是用户编号**：认错一次就把别人的头像挂到这位名下。
        assertNull(GalleryArtistLinks.pixivUserId("https://www.pixiv.net/artworks/120334746"))
        assertNull(GalleryArtistLinks.pixivUserId("https://sketch.pixiv.net/@seoyul"))
        assertNull(GalleryArtistLinks.pixivUserId("https://www.pixiv.net/stacc/q18607"))
        assertNull(GalleryArtistLinks.pixivUserId("http://img42.pixiv.net/img/tehu48/"))
        assertNull(GalleryArtistLinks.pixivUserId("https://pixiv.me/setmen"))
        // member.php 给了非数字 id 时不许硬转（站方那种脏值真出现过在别处）。
        assertNull(GalleryArtistLinks.pixivUserId("https://www.pixiv.net/member.php?id=abc"))
    }

    @Test
    fun `图标顺序定死 pixiv 第一 第二枚推优先 没有推才摆 fanbox`() {
        fun plan(vararg urls: String) = GalleryArtistLinks.iconPlan(urls.toList()).map { it.platform }
        // 站方三链齐全（实测 aoi_sakura_(seak5545) 就是这个形状）。
        assertEquals(
            listOf(GalleryArtistLinkPlatform.PIXIV, GalleryArtistLinkPlatform.TWITTER),
            plan(
                "https://twitter.com/6Yaoyue",
                "https://www.pixiv.net/users/108878342",
                "https://aoi-sakura.fanbox.cc/",
            ),
        )
        // 没有推特才轮到 fanbox。
        assertEquals(
            listOf(GalleryArtistLinkPlatform.PIXIV, GalleryArtistLinkPlatform.FANBOX),
            plan("https://www.pixiv.net/users/1", "https://x.fanbox.cc/"),
        )
        // 只有 pixiv 就一枚；只有 fanbox 也摆一枚（不能因为"没有 pixiv"就整排不摆）。
        assertEquals(listOf(GalleryArtistLinkPlatform.PIXIV), plan("https://www.pixiv.net/users/1"))
        assertEquals(listOf(GalleryArtistLinkPlatform.FANBOX), plan("https://abc.fanbox.cc/"))
        // 全是认不出的服务 → 一枚都不摆（**不摆 = 不骗**，比摆一个别的站重要）。
        assertEquals(emptyList<Any>(), plan("http://bekkankou.at.infoseek.co.jp/index.html", "https://linktr.ee/x"))
        // 只有老图床目录那种"看着像 pixiv"的，也不许摆成 pixiv。
        assertEquals(emptyList<Any>(), plan("http://img42.pixiv.net/img/tehu48/"))
    }

    @Test
    fun `同一平台多条时取站方原序第一条 且同人双链去重`() {
        // lichiko 那条实测同时给了 x.com 与 twitter.com 同一个 handle。
        val picked = GalleryArtistLinks.iconPlan(
            listOf(
                "https://www.pixiv.net/users/37257600",
                "https://www.pixiv.net/stacc/seoyul",
                "https://sketch.pixiv.net/@seoyul",
                "https://x.com/llichiko",
                "https://twitter.com/llichiko",
                "https://linktr.ee/lllichiko",
            ),
        )
        assertEquals(2, picked.size)
        assertEquals(GalleryArtistLinkPlatform.PIXIV, picked[0].platform)
        assertEquals(37257600L, GalleryArtistLinks.pixivUserId(picked[0].url))
        // 同一个人的两条推特只占一枚 —— 摆两条就是"这有两位画师"的错觉。
        assertEquals(GalleryArtistLinkPlatform.TWITTER, picked[1].platform)
    }

    @Test
    fun `pixiv 那条优先挑带用户 id 的 头像要的是它`() {
        // 站方给的顺序里先出现 stacc（没有数字 id），必须让位给带 id 的那条。
        val picked = GalleryArtistLinks.iconPlan(
            listOf("https://www.pixiv.net/stacc/q18607", "https://www.pixiv.net/users/4792758"),
        )
        assertEquals(1, picked.size)
        assertEquals(4792758L, GalleryArtistLinks.pixivUserId(picked[0].url))
    }

    @Test
    fun `关注名单认站到名字 跨站同名是两条而不是并一条`() {
        val base = listOf(
            GalleryArtistFollow(GallerySite.YANDERE, "setmen", 10L),
            GalleryArtistFollow(GallerySite.GELBOORU, "setmen", 20L),
        )
        // 两站的搜索域不同，并成一条就会出现"从 yande 那条点过去却搜了 Gelbooru"的串台。
        assertEquals(2, GalleryArtistFollows.dedupe(base).size)
        // 同站同名只留一条，且留的是**较早那次关注**（时间戳不重掷，取关再关注会排到后面去）。
        val dup = base + GalleryArtistFollow(GallerySite.YANDERE, "SETMEN", 30L)
        val kept = GalleryArtistFollows.dedupe(dup)
        assertEquals(2, kept.size)
        assertEquals(10L, kept.single { it.site == GallerySite.YANDERE }.addedAt)
    }

    @Test
    fun `名单排序定死 同秒按名字 不许跟着集合实现飘`() {
        val entries = listOf(
            GalleryArtistFollow(GallerySite.YANDERE, "b_artist", 100L),
            GalleryArtistFollow(GallerySite.GELBOORU, "a_artist", 100L),
            GalleryArtistFollow(GallerySite.YANDERE, "c_artist", 200L),
        )
        val sorted = GalleryArtistFollows.sorted(entries)
        assertEquals(listOf("c_artist", "a_artist", "b_artist"), sorted.map { it.name })
        // 空表回空表（不是 null，也不摆"你还没有关注"那种由调用方说的话）。
        assertEquals(emptyList<Any>(), GalleryArtistFollows.sorted(emptyList()))
    }

    @Test
    fun `取关只删那一格 关注是幂等的`() {
        val entries = listOf(
            GalleryArtistFollow(GallerySite.YANDERE, "setmen", 10L),
            GalleryArtistFollow(GallerySite.GELBOORU, "setmen", 20L),
        )
        val removed = GalleryArtistFollows.removed(entries, GallerySite.YANDERE, "setmen")
        assertEquals(listOf(GallerySite.GELBOORU), removed.map { it.site })

        val added = GalleryArtistFollows.withAdded(entries, GalleryArtistFollow(GallerySite.YANDERE, "tokenbox", 30L))
        assertEquals(3, added.size)
        // 再点一次同一个人：不许长出一条重复，也不许把原时间戳冲掉。
        val again = GalleryArtistFollows.withAdded(added, GalleryArtistFollow(GallerySite.YANDERE, "tokenbox", 99L))
        assertEquals(3, again.size)
        assertEquals(30L, again.single { it.name == "tokenbox" }.addedAt)
        assertTrue(GalleryArtistFollows.isFollowing(entries, GallerySite.YANDERE, "SETMEN"))
    }

    @Test
    fun `uid 口径与收藏一致 都是站点键冒号加名字`() {
        val uid = GalleryArtistFollow.uidOf(GallerySite.GELBOORU, "setmen")
        assertEquals("${GallerySite.GELBOORU.routeKey}:setmen", uid)
    }
}
