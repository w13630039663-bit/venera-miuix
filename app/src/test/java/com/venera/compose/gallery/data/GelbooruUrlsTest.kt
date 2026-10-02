package com.venera.compose.gallery.data

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gelbooru 的域名只许有一个出处。
 *
 * 收口前 DAPI 前缀、校验探针 URL、账号页 URL 三处各写了一遍 `https://gelbooru.com/...`，
 * 而 `GallerySite.GELBOORU.apiHost` 才是这一站域名该被问的地方 ——
 * 画廊的「刷新/重试要清掉这一站的域名熔断」用的就是它。两处一旦不同步，
 * 症状是「清了熔断还是 403」这种查不出根因的组合。
 */
class GelbooruUrlsTest {

    @Test
    fun `账号页 URL 的值逐字不变`() {
        assertEquals(
            "https://gelbooru.com/index.php?page=account&s=options",
            GelbooruAccount.ACCOUNT_PAGE,
        )
    }

    @Test
    fun `URL 确实由 GallerySite 的 apiHost 派生`() {
        val host = GallerySite.GELBOORU.apiHost
        assertEquals("gelbooru.com", host)
        assertTrue(GelbooruAccount.ACCOUNT_PAGE.startsWith("https://$host/index.php"))
    }

    @Test
    fun `画廊侧除出处本身外不许再手写这个域名`() {
        // 出处 = GallerySite 那一行枚举。它派生的 GelbooruClient / GelbooruAccount
        // 现在写的是 "https://${apiHost}/…"，串里没有域名字面量，所以它们也在扫描范围内。
        val definition = "com/venera/compose/gallery/data/GallerySite.kt"
        val urlLiteral = Regex(""""https?://[^"]*gelbooru\.com""")
        val hits = RepoSources.allMainLines().filter { (rel, _, line) ->
            val t = line.trim()
            rel.startsWith("com/venera/compose/gallery/") && rel != definition &&
                urlLiteral.containsMatchIn(line) && !t.startsWith("//") && !t.startsWith("*")
        }
        // ⚠️ 漫画侧的 `data/network/ImageHeaderPolicy.kt` 那条 "Referer" to "https://gelbooru.com/"
        // 故意不改引 GallerySite：改成 import gallery.data 会新增一条「漫画基建 → 画廊」的反向边，
        // 违反隔离口径。它的值由 ImageHeaderPolicyTest 那边的 Referer 断言逐字钉住。
        // 通用口径就一句：跨侧一致性用测试钉，不用共享符号。
        //
        // 注释里那些实测记录（302→hotlink.php、img4 分片能通）保留原样 —— 那是证据不是配置，
        // 所以扫描跳过整行注释。
        assertTrue(
            "gelbooru 域名又被手写进画廊侧代码：\n" + hits.joinToString("\n") { "${it.first}:${it.second} ${it.third.trim()}" },
            hits.isEmpty(),
        )
    }
}
