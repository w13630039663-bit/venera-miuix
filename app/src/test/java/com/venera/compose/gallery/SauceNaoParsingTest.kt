package com.venera.compose.gallery

import com.venera.compose.gallery.data.isDirectImageUrl
import com.venera.compose.gallery.data.preferredExternalUrl
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.SauceNaoException
import com.venera.compose.gallery.data.parseSauceNao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SauceNAO `output_type=2` 响应的解析判据。
 *
 * 这些用例锁的是**站方的不规则之处**，不是我们自己想要的形状 —— 每一条都对应
 * 一个真会漏到界面上的读数：相似度是字符串、作者名有四个候选键而且可能是
 * 数组字面量串、"没找到"是答案不是故障、hidden 条目要在本地再拦一道。
 *
 * 解析器刻意不碰网络也不碰 `org.json`（Android 上是 stub，纯 JVM 跑不了），
 * 所以这一整层是能在 JVM 上锁住的 —— 客户端那一层（真发请求）不能，也不假装能。
 */
class SauceNaoParsingTest {

    private fun body(headerJson: String, resultsJson: String) =
        """{"header":$headerJson,"results":$resultsJson}"""

    @Test
    fun `相似度是字符串数字，取整交回`() {
        val page = parseSauceNao(
            body(
                """{"status":0,"results_returned":"2","total_matches":137}""",
                """[
                     {"header":{"similarity":"95.29","thumbnail":"t1","index_name":"danbooru2023"},
                      "data":{"yandere_id":1238144,"member_name":"某画师"}},
                     {"header":{"similarity":"80.6","thumbnail":"t2","index_name":"gelbooru_v154"},
                      "data":{"gelbooru_id":5933260}}
                   ]""",
            ),
            allowNsfw = false,
        )

        assertEquals(0, page.status)
        assertEquals(137, page.totalMatches)
        assertEquals(2, page.hits.size)
        // 95.29 → 95，不是 96：站方给的是排序用的浮点，念出来只要整数。
        assertEquals(95, page.hits[0].similarity)
        assertEquals(80, page.hits[1].similarity)
    }

    @Test
    fun `yandere_id 与 gelbooru_id 映射回我们自己的两站`() {
        val page = parseSauceNao(
            body(
                """{"status":0}""",
                """[
                     {"header":{"similarity":"99","thumbnail":""},"data":{"yandere_id":1238144}},
                     {"header":{"similarity":"98","thumbnail":""},"data":{"gelbooru_id":"5933260"}},
                     {"header":{"similarity":"97","thumbnail":""},"data":{"pixiv_id":999}},
                     {"header":{"similarity":"96","thumbnail":""},"data":{"yandere_id":0}}
                   ]""",
            ),
            allowNsfw = false,
        )

        assertEquals(GallerySite.YANDERE to 1238144L, page.hits[0].siteRef?.let { it.site to it.id })
        // gelbooru_id 有时是字符串数字，也要能映射上。
        assertEquals(GallerySite.GELBOORU to 5933260L, page.hits[1].siteRef?.let { it.site to it.id })
        // pixiv 命中开不了我们的大图页，只能走外链。
        assertNull(page.hits[2].siteRef)
        // id=0 是"没有"而不是"第 0 条"。
        assertNull(page.hits[3].siteRef)
    }

    @Test
    fun `没有作者名的命中不丢，只要能映射回本站或有外链`() {
        // Breadboard 在这里 continue 掉了（它那一屏是"按图找画师"）。
        // 我们这一屏是"按图找这张图"，一条带 yandere_id 的命中即使没作者也照样能点开、能收藏。
        val page = parseSauceNao(
            body(
                """{"status":0}""",
                """[{"header":{"similarity":"90","thumbnail":""},"data":{"yandere_id":42}}]""",
            ),
            allowNsfw = false,
        )

        assertEquals(1, page.hits.size)
        assertNull(page.hits.single().artistName)
        assertEquals(42L, page.hits.single().siteRef?.id)
    }

    @Test
    fun `作者名四个候选键依次兜底，且吃数组字面量串`() {
        val page = parseSauceNao(
            body(
                """{"status":0}""",
                """[
                     {"header":{"similarity":"1","thumbnail":""},"data":{"member_name":"a","author_name":"b"}},
                     {"header":{"similarity":"1","thumbnail":""},"data":{"author_name":"b2","creator":"c"}},
                     {"header":{"similarity":"1","thumbnail":""},"data":{"creator":"c2"}},
                     {"header":{"similarity":"1","thumbnail":""},"data":{"twitter_user_handle":"[\"d\"]"}},
                     {"header":{"similarity":"1","thumbnail":""},"data":{"twitter_user_handle":"not-json-array"}}
                   ]""",
            ),
            allowNsfw = false,
        )

        assertEquals(listOf("a", "b2", "c2", "d", "not-json-array"), page.hits.map { it.artistName })
    }

    @Test
    fun `hidden 条目只在允许成人内容时才留下`() {
        val json = body(
            """{"status":0}""",
            """[
                 {"header":{"similarity":"90","thumbnail":"","hidden":1},"data":{"yandere_id":1}},
                 {"header":{"similarity":"80","thumbnail":"","hidden":0},"data":{"yandere_id":2}}
               ]""",
        )

        assertEquals(listOf(2L), parseSauceNao(json, allowNsfw = false).hits.map { it.siteRef?.id })
        assertEquals(
            listOf(1L, 2L),
            parseSauceNao(json, allowNsfw = true).hits.map { it.siteRef?.id },
        )
    }

    @Test
    fun `外链里 ext_urls 在前、source 只当补充，且 source 不是链接时不进去`() {
        // 第一例：source 与 ext_urls 里同一条 → 只留一份（去重仍要）。
        // ⚠️ 顺序语义在 2026-09-27 改过：从前是 source 在前，理由是"它才是图真正的出处"；
        // 真机上那正是「打开来源全跳 i.pximg.net」的成因 —— pixiv 库的 `source`
        // 常常是**图片直链**，而 `ext_urls` 里才是作品页。所以现在 ext_urls 在前。
        val merged = parseSauceNao(
            body(
                """{"status":0}""",
                """[{"header":{"similarity":"90","thumbnail":""},
                    "data":{"source":"https://yande.re/post/show/42",
                           "ext_urls":["https://yande.re/post/show/42","https://pixiv.net/1"]}}]""",
            ),
            allowNsfw = false,
        )
        assertEquals(
            listOf("https://yande.re/post/show/42", "https://pixiv.net/1"),
            merged.hits.single().extUrls,
        )

        // 第二例：source 常常根本不是链接（站方在这里塞过文件名与画师名的中文串）。
        // 那一类不能进外链，否则界面上会出现一条点了没反应的"链接"。
        val notALink = parseSauceNao(
            body(
                """{"status":0}""",
                """[{"header":{"similarity":"90","thumbnail":""},
                    "data":{"source":"IMG_1234.jpg","ext_urls":["https://pixiv.net/1"]}}]""",
            ),
            allowNsfw = false,
        )
        assertEquals(listOf("https://pixiv.net/1"), notALink.hits.single().extUrls)
    }

    @Test
    fun `打开来源要跳作品页而不是 pximg 直链`() {
        // 2026-09-27 真机反馈的原样：pixiv 库的 source 是 i.pximg.net 直链，
        // ext_urls 里才有作品页。直接取 firstOrNull 会跳到一张裸图。
        val page = parseSauceNao(
            body(
                """{"status":0}""",
                """[{"header":{"similarity":"93","thumbnail":""},
                    "data":{"source":"https://i.pximg.net/img-original/img/2024/01/02/a.jpg",
                           "ext_urls":["https://www.pixiv.net/en/artworks/12345"]}}]""",
            ),
            allowNsfw = false,
        )
        val urls = page.hits.single().extUrls
        assertEquals("https://www.pixiv.net/en/artworks/12345", preferredExternalUrl(urls))
        assertTrue(isDirectImageUrl("https://i.pximg.net/img-original/img/2024/01/02/a.jpg"))
        assertTrue(!isDirectImageUrl("https://www.pixiv.net/en/artworks/12345"))
    }

    @Test
    fun `只有直链时也还能打开，不至于变成点不动的钮`() {
        // 退路：整条命中只剩一条直链时，仍然交出来 —— 宁可跳一张图，
        // 也不要把那枚「打开来源」变成点了没反应的假开关。
        assertEquals(
            "https://i.pximg.net/x.jpg",
            preferredExternalUrl(listOf("https://i.pximg.net/x.jpg")),
        )
        assertEquals(null, preferredExternalUrl(emptyList()))
    }

    @Test
    fun `status 负二是答案不是故障，交回空页且不抛`() {
        val page = parseSauceNao(
            body("""{"status":-2,"message":"No results found."}""", "[]"),
            allowNsfw = false,
        )

        assertEquals(-2, page.status)
        assertTrue("空 hits 才对", page.hits.isEmpty())
        assertEquals("No results found.", page.message)
    }

    @Test
    fun `其他非零状态一律抛错，且优先念站方自己那句 message`() {
        val withMessage = runCatching {
            parseSauceNao(body("""{"status":-1,"message":"Invalid API key."}""", "[]"), false)
        }
        assertTrue(withMessage.exceptionOrNull() is SauceNaoException)
        assertEquals("Invalid API key.", withMessage.exceptionOrNull()?.message)

        val withoutMessage = runCatching {
            parseSauceNao(body("""{"status":-7}""", "[]"), false)
        }
        // 站方没给话时才由我们补一句状态码，不编造成"网络失败"。
        assertEquals("SauceNAO 返回状态码 -7", withoutMessage.exceptionOrNull()?.message)
    }

    @Test
    fun `body 不是 JSON 时说的是被拦了，而不是解析异常`() {
        val outcome = runCatching {
            parseSauceNao("<html><body>Attention Required! | Cloudflare</body></html>", false)
        }
        val message = outcome.exceptionOrNull()?.message.orEmpty()
        assertTrue("实际：$message", message.contains("JSON"))
    }

    @Test
    fun `缺字段与 null 字段都不炸`() {
        val page = parseSauceNao(
            body(
                """{"status":0}""",
                """[{"header":{"similarity":null},"data":{"title":null,"ext_urls":null}}]""",
            ),
            allowNsfw = false,
        )

        val hit = page.hits.single()
        assertEquals(0, hit.similarity)
        assertNull(hit.title)
        assertEquals(emptyList<String>(), hit.extUrls)
        assertNull(hit.siteRef)
    }

    @Test
    fun `没有 results 键时交回空页而不是空指针`() {
        val page = parseSauceNao("""{"header":{"status":0}}""", allowNsfw = false)
        assertTrue(page.hits.isEmpty())
    }
}
