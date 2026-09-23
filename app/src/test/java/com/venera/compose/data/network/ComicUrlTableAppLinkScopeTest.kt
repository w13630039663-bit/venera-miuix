package com.venera.compose.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 漫画 URL 双向表的口径锁。
 *
 * 这张表现在同时喂三个方向（分享拼链接 / 入站认链接 / manifest 声明域），
 * 三处一旦漂移就是"分享出去的链接自己认不回来""设置里少一个域开关"这类**静默**缺陷 ——
 * 所以不靠人记，靠这三条断言。
 */
class ComicUrlTableAppLinkScopeTest {

    /** manifest 与本仓库源码同级：单测的工作目录是模块目录 `app/`。 */
    private val manifestText: String by lazy {
        val file = File("src/main/AndroidManifest.xml")
        assertTrue("找不到 $file.absolutePath", file.exists())
        file.readText()
    }

    @Test
    fun `分享拼出的链接能被同一张表认回来`() {
        // 每个源用它**真实形状**的 id：哔咔是 24 位 hex ObjectId，其余是数字/短横线串。
        // 拿一个通用 id 去试全部源，测的就不是"双向一致"而是"正则恰好宽松"了。
        val samples = mapOf(
            "copy_manga" to "123456",
            "baozi" to "123456",
            "manga_dex" to "123456",
            "bilibili" to "123456",
            "jm" to "123456",
            "ehentai" to "123456",
            "nhentai" to "123456",
            "wnacg" to "123456",
            "hitomi" to "123456",
            "picacg" to "58f649a80a48790773c7017c",
        )
        for ((key, id) in samples) {
            val url = ComicUrlTable.shareUrlFor(key, id)
            assertTrue("$key 没有分享模板", url != null)
            val matched = ComicUrlTable.match(url!!)
            assertTrue("$key 的分享链接 $url 认不回来", matched != null)
            assertEquals("$key 双向 id 不一致", id, matched!!.comicId)
            assertEquals("$key 双向源不一致", key, matched.sourceKey)
        }
    }

    /** 哔咔的阅读器路径与详情页 comments 子路径也要能认回同一本。 */
    @Test
    fun `哔咔的阅读器与评论子路径认回同一本`() {
        val id = "58f649a80a48790773c7017c"
        for (url in listOf(
            "https://manhuabika.com/comic/$id",
            "https://manhuabika.com/comic/$id/",
            "https://manhuabika.com/comic/$id/comments",
            "https://manhuabika.com/comic/reader/$id/01",
        )) {
            val matched = ComicUrlTable.match(url)
            assertEquals("认不出 $url", id, matched?.comicId)
            assertEquals("picacg", matched?.sourceKey)
        }
        // 单复数不能混：/comics/creator/<id> 是**列表页**，不是一本漫画。
        assertNull(ComicUrlTable.match("https://manhuabika.com/comics/creator/$id"))
    }

    @Test
    fun `manifest 声明的域与表的声明位逐条对齐`() {
        val fromXml = Regex("""android:host="([^"]+)"\s+android:pathPrefix="([^"]+)"""")
            .findAll(manifestText)
            .map { ComicUrlTable.AppLinkScope(it.groupValues[1], it.groupValues[2]) }
            .toList()
        val fromTable = ComicUrlTable.appLinkScopes
        assertEquals(
            "XML 与 ComicUrlTable 的 (host, pathPrefix) 集合不一致",
            fromTable.sortedBy { "${it.host}${it.pathPrefix}" }.joinToString("\n") { "${it.host} ${it.pathPrefix}" },
            fromXml.sortedBy { "${it.host}${it.pathPrefix}" }.joinToString("\n") { "${it.host} ${it.pathPrefix}" },
        )
        assertTrue("一条声明位都没有，正则或表写坏了", fromXml.isNotEmpty())
    }

    /** 本轮拍板：第三-party 域永远验证不过，写 autoVerify 只会得到一个失败状态。 */
    @Test
    fun `清单里不出现 autoVerify 属性`() {
        // 只查属性本身：manifest 上方那段注释里写着"刻意不写 android:autoVerify"，是散文不是声明。
        assertTrue(
            "autoVerify 需要域名所有者发布 assetlinks.json，这些站不是我们的域名",
            !Regex("""android:autoVerify\s*=""").containsMatchIn(manifestText),
        )
    }

    @Test
    fun `表里没有的源一律不编造链接`() {
        // goda / mh18 都是装了源但表里没格式的样本：这类源就只分享标题。
        assertNull(ComicUrlTable.shareUrlFor("goda", "123456"))
        assertNull(ComicUrlTable.shareUrlFor("mh18", "123456"))
        assertNull(ComicUrlTable.shareUrlFor("jm", ""))
        assertNull(ComicUrlTable.shareUrlFor("jm", null))
        assertNull(ComicUrlTable.shareUrlFor(null, "123456"))
        assertNull(ComicUrlTable.match("18comic.vip/album/123456"))
    }
}
