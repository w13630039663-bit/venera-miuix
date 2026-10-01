package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryArtistEndpointParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画师取数层里**形状不确定**的那几处的解析判据（批次 L · L2）。
 *
 * 为什么这一份要单测：Danbooru 的三个同库站点（danbooru / safebooru / testbooru）在 2026-09-30
 * 本机出口**全部 403 卡在过盾页**（`Just a moment...`），拿不到一份真样例。而"字段形态猜错"的
 * 表现是**安静地什么都不显示** —— 编译器不报、运行时不报、用户读成"这位没有外链"。
 * 所以这里不是"猜一个形态去接反序列化"，而是把**源码里查得到的候选形态全部吃掉**，
 * 并让每一种形态都有一条断言兜着：
 *
 * - `artist_policy.rb` 的写入侧字段叫 `url_string`（`url_array.join("\n")`，
 *   而 `url_array = urls.map(&:to_s).sort`，`to_s` 对停用那条带 `-` 前缀）；
 * - 同源的 yande.re `artist.json` 实测给的是 `urls` **字符串数组**（同日取证）。
 *
 * 过盾页那一条尤其要紧：**挑战页是 200/403 + 一坨 HTML**，
 * 把它切成"0 条地址"就是本仓最忌的静默降级 —— 用户分不清"这位真没外链"和"我们被挡在门外"。
 */
class GalleryArtistEndpointParseTest {

    @Test
    fun `urls 给成字符串数组时逐条收下`() {
        val body = """[{"name":"setmen","urls":["https://www.pixiv.net/users/7075448","https://twitter.com/setmen"]}]"""
        assertEquals(
            listOf("https://www.pixiv.net/users/7075448", "https://twitter.com/setmen"),
            GalleryArtistEndpointParse.artistUrlsByName(body, "setmen"),
        )
    }

    @Test
    fun `urls 给成对象数组时取它里面的 url 字段`() {
        val body = """[{"name":"setmen","urls":[{"url":"https://www.pixiv.net/users/1","is_active":true}]}]"""
        assertEquals(
            listOf("https://www.pixiv.net/users/1"),
            GalleryArtistEndpointParse.artistUrlsByName(body, "setmen"),
        )
    }

    @Test
    fun `只给 url_string 那一串时按空白切分`() {
        val body = """[{"name":"setmen","url_string":"https://www.pixiv.net/users/1\n-https://dead.example/x"}]"""
        // 带 `-` 的那条是站方标停用，同一条口径由 GalleryArtistUrls.split 守住（那边已有单测）。
        assertEquals(
            listOf("https://www.pixiv.net/users/1"),
            GalleryArtistEndpointParse.artistUrlsByName(body, "setmen"),
        )
    }

    @Test
    fun `名字要全等 站方给的是前缀匹配`() {
        val body = """[{"name":"setmen_backup","urls":["http://a/1"]},{"name":"setmen","urls":["http://b/2"]}]"""
        assertEquals(listOf("http://b/2"), GalleryArtistEndpointParse.artistUrlsByName(body, "setmen"))
        // 大小写按站方口径不敏感（同一条画师记录名实测两种写法都出现过）。
        assertEquals(listOf("http://b/2"), GalleryArtistEndpointParse.artistUrlsByName(body, "SETMEN"))
        // 一条全等都没有 = 站方没这条记录，交空表而不是硬挑第一条。
        assertEquals(emptyList<String>(), GalleryArtistEndpointParse.artistUrlsByName(body, "setme"))
    }

    @Test
    fun `过盾页不是空答复 必须报失败`() {
        val challenge = """<!DOCTYPE html><html><head><title>Just a moment...</title></head><body>x</body></html>"""
        val error = runCatching { GalleryArtistEndpointParse.artistUrlsByName(challenge, "setmen") }.exceptionOrNull()
        assertTrue("过盾页必须抛错，不能回空表（空表在屏上读起来是「这位没有外链」）", error != null)
    }

    @Test
    fun `认不出的形状一律抛错 不退化成空`() {
        listOf("[]", "{}", "{\"errors\":[\"x\"]}", "", "   ").forEach { body ->
            // `[]` 是"这一轮真的没有全等记录"（空表，合法答复）；其余是"我们没读懂这一页"（抛错）。
            if (body.trim() == "[]") {
                assertEquals(emptyList<String>(), GalleryArtistEndpointParse.artistUrlsByName(body, "setmen"))
            } else {
                val failure = runCatching { GalleryArtistEndpointParse.artistUrlsByName(body, "setmen") }.exceptionOrNull()
                assertTrue("认不出就要抛错，别静默交回空表：$body", failure != null)
            }
        }
    }

    @Test
    fun `pixiv 那侧 error 为真时算失败 不是没有头像`() {
        val dead = """{"error":true,"message":"can't find user","body":{"userId":"1360347","image":"","imageBig":""}}"""
        val failure = runCatching { GalleryArtistEndpointParse.pixivAvatarUrl(dead) }.exceptionOrNull()
        assertTrue("站方明说 error=true 就是这一路没走通，不能长成「这位没头像」", failure != null)

        val bigOnly = """{"error":false,"body":{"imageBig":"https://i.pximg.net/a_170.png","image":"https://i.pximg.net/a_50.png"}}"""
        assertEquals("https://i.pximg.net/a_170.png", GalleryArtistEndpointParse.pixivAvatarUrl(bigOnly))

        // 站方两档都给了空串：那是"取不到"，交 null 让调用方换成首字母占位。
        val none = """{"error":false,"body":{"imageBig":"","image":""}}"""
        assertEquals(null, GalleryArtistEndpointParse.pixivAvatarUrl(none))
    }

    @Test
    fun `fanbox 的头像在 body_user_iconUrl 上`() {
        // 2026-09-30 14:05 真答复（带 Origin 那一次），字段逐字照抄。
        val body = """{"body":{"user":{"userId":"7075448","name":"セットマン","iconUrl":"https://pixiv.pximg.net/c/160x160_90_a2_g5/fanbox/public/images/user/7075448/icon/r5ssgvWzPSdItny2clTedYdM.jpeg"},"creatorId":"setmen","hasPublishedPost":true}}"""
        assertEquals(
            "https://pixiv.pximg.net/c/160x160_90_a2_g5/fanbox/public/images/user/7075448/icon/r5ssgvWzPSdItny2clTedYdM.jpeg",
            GalleryArtistEndpointParse.fanboxAvatarUrl(body),
        )
    }

    @Test
    fun `fanbox 那台的 error 串与认不出的形状都算失败`() {
        // 实测 400 的答复体是 `{"error":"general_error"}`。取数层是"非 2xx 先抛"，所以这里收到它
        // 只会发生在站方改成 200 之后 —— 那一档也必须抛，不能回 null（null 在屏上是"这位没头像"）。
        listOf("""{"error":"general_error"}""", "{}", """{"body":{}}""", "[]", "not json").forEach { body ->
            val failure = runCatching { GalleryArtistEndpointParse.fanboxAvatarUrl(body) }.exceptionOrNull()
            assertTrue("读不懂的答复必须抛错：$body", failure != null)
        }
        // 读得懂、但那位确实没设头像（空串）→ null，交调用方退回首字母座。
        assertEquals(null, GalleryArtistEndpointParse.fanboxAvatarUrl("""{"body":{"user":{"iconUrl":""}}}"""))
    }

    @Test
    fun `Mastodon 的头像优先取静态档`() {
        val body = """{"id":"1039732","acct":"lalazyt","avatar":"https://img.pawoo.net/a.gif","avatar_static":"https://img.pawoo.net/a.jpg"}"""
        // 头像位只有 34dp，那是 chrome 不是内容 —— 为一枚圆座挂动图解码器不值（同一口径见画廊墙缩略档恒 jpg）。
        assertEquals("https://img.pawoo.net/a.jpg", GalleryArtistEndpointParse.mastodonAvatarUrl(body))
        assertEquals(
            "https://img.pawoo.net/b.gif",
            GalleryArtistEndpointParse.mastodonAvatarUrl("""{"avatar":"https://img.pawoo.net/b.gif","avatar_static":""}"""),
        )
    }

    @Test
    fun `Mastodon 的缺省头像不算头像`() {
        // 没设过头像的账号，实例给的是 `/avatars/original/missing.png` 那张通用剪影。
        // 摆上屏读起来是"这位有头像"，而首字母座说的是同一件事的诚实版本 —— 所以这里交 null。
        assertEquals(
            null,
            GalleryArtistEndpointParse.mastodonAvatarUrl(
                """{"avatar":"https://img.pawoo.net/images/missing.png","avatar_static":"https://img.pawoo.net/images/missing.png"}""",
            ),
        )
        // 读不懂（既不是对象、也没有那两根键）一律抛错。
        listOf("[]", """{"acct":"x"}""", "not json").forEach { body ->
            val failure = runCatching { GalleryArtistEndpointParse.mastodonAvatarUrl(body) }.exceptionOrNull()
            assertTrue("读不懂的答复必须抛错：$body", failure != null)
        }
    }

    @Test
    fun `ajax illust 答复里的作者三档字段`() {
        // 2026-09-30 14:26 真答复（作品 113455481），三个字段都是**字符串**，逐字照抄形状。
        val ok = """{"error":false,"message":"","body":{"illustId":"113455481","userId":"7075448","userName":"セトマン","userAccount":"tokenbox"}}"""
        val author = GalleryArtistEndpointParse.pixivArtworkAuthor(ok)
        assertEquals(7075448L, author.userId)
        assertEquals("tokenbox", author.account)
        assertEquals("セトマン", author.name)
        // 账号是空串 → null：胶囊那枚就写 pixiv，不写成 "pixiv "（尾随空格在屏上像个坏掉的控件）。
        assertEquals(
            null,
            GalleryArtistEndpointParse.pixivArtworkAuthor("""{"error":false,"body":{"userId":"1","userAccount":""}}""").account,
        )
    }

    @Test
    fun `ajax illust 的 error 与缺字段都算失败`() {
        // 作品被删/被锁时站方回 **404 + `{"error":true}`**（15 个样本里 4 个是这一档，含真机那张 109123828）。
        // 取数层非 2xx 先抛，这里再兜一次：把"读不懂"交成 null 就是让画师行少一枚而没人知道为什么。
        listOf(
            """{"error":true,"message":"Not Found","body":[]}""",
            // 没有 userId 就不能猜一个号去要头像 —— 那是把别人的脸挂到这位名下。
            """{"error":false,"message":"","body":{"userName":"x"}}""",
            """{"error":false,"body":[]}""",
            "{}",
            "not json",
        ).forEach { body ->
            val failure = runCatching { GalleryArtistEndpointParse.pixivArtworkAuthor(body) }.exceptionOrNull()
            assertTrue("读不懂或站方明说没有，都必须抛：$body", failure != null)
        }
    }

    @Test
    fun `同一条记录的外链与别名一次取齐`() {
        // 这份就是 2026-09-30 的真样本 `_probe/hub/dan_setmen.json` 的形状（多给一个 urls 键，
        // 因为同一站不同 API 版本给的键集不一样，两种都要吃）。
        val body = """[{"id":183883,"name":"setmen","other_names":["u_u","セトマン"],""" +
            """"urls":["https://www.pixiv.net/users/7075448"]}]"""
        val credits = GalleryArtistEndpointParse.artistCreditsByName(body, "setmen")
        assertEquals(listOf("https://www.pixiv.net/users/7075448"), credits.urls)
        assertEquals(listOf("u_u", "セトマン"), credits.otherNames)
    }

    @Test
    fun `没有 other_names 键时别名交空表而不抛`() {
        // 键不在 = 站方答上了"这位没登记别名"（真样本里就有这种：yande.re 的 artist.json 压根没这个键）。
        val body = """[{"name":"setmen","urls":["https://x.com/setmen"]}]"""
        val credits = GalleryArtistEndpointParse.artistCreditsByName(body, "setmen")
        assertEquals(listOf("https://x.com/setmen"), credits.urls)
        assertTrue(credits.otherNames.isEmpty())
    }

    @Test
    fun `前缀批次里的邻居记录绝不能算这一位`() {
        // `name=` 是前缀匹配：`setmen` 会连着 `setmen_2`、`setmenxxx` 一起回，
        // 挑第一条就是把别人的别名与外链挂到这位名下。
        val body = """[{"name":"setmen_2","other_names":["别的画师"],""" +
            """"urls":["https://pixiv.net/users/1"]}, """ +
            """{"name":"setmen","other_names":["u_u"],"urls":["https://x.com/setmen"]}]"""
        val credits = GalleryArtistEndpointParse.artistCreditsByName(body, "setmen")
        assertEquals(listOf("u_u"), credits.otherNames)
        assertEquals(listOf("https://x.com/setmen"), credits.urls)
    }

    @Test
    fun `别名形状读不懂只拖别名那一栏不拖外链`() {
        // 画师行已经能摆的外链，不能因为介绍页新读的一个键形状对不上就一起没。
        val body = """[{"name":"setmen","urls":["https://x.com/setmen"],"other_names":"u_u"}]"""
        assertEquals(
            listOf("https://x.com/setmen"),
            GalleryArtistEndpointParse.artistCreditsByName(body, "setmen").urls,
        )
        assertEquals(
            listOf("https://x.com/setmen"),
            GalleryArtistEndpointParse.artistUrlsByName(body, "setmen"),
        )
    }

    @Test
    fun `查无此人时两栏都交空表`() {
        val credits = GalleryArtistEndpointParse.artistCreditsByName("""[{"name":"别人"}]""", "setmen")
        assertTrue(credits.urls.isEmpty() && credits.otherNames.isEmpty())
    }

    @Test
    fun `过盾页那种非数组答复照抛`() {
        listOf("<html>Just a moment...</html>", "{}", "")
            .forEach { body ->
                val failure = runCatching {
                    GalleryArtistEndpointParse.artistCreditsByName(body, "setmen")
                }.exceptionOrNull()
                assertTrue("形态对不上不能退成空表：$body", failure != null)
            }
    }
}


