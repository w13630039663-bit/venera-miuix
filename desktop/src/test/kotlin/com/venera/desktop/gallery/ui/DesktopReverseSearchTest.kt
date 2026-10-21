package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryReverseSearch
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GallerySiteRef
import com.venera.compose.gallery.data.SauceNaoException
import com.venera.compose.gallery.data.SauceNaoHit
import com.venera.compose.gallery.data.SauceNaoPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第三十五批 批 35 · 反搜第二格（卡上那一枚入口 + 结果那一格）。
 *
 * ## 这一批会让哪几句旧说明变成假话
 *
 * 桌面这一族从前是两枚直接抛的桩，所以"整颗没接"那几句（端口注释、设置那一屏的类注释、
 * `DesktopNotWired` 里那颗异常）当时都是实话。本批把桩摘了 ⇒ 同一批把它们点名改掉，
 * ⑧⑨ 两条钉住它们不许以注释的形态留在树上（[[project-implemented-but-unwired]] 那条反方向的账）。
 *
 * ## 一条本机读数（2026-10-08，量法写在同批 rounds 里）
 *
 * 本机到 saucenao.com **裸连与走 7890 代理都回 HTTP 403**，body 是 Cloudflare 那张
 * "Just a moment..." 的 HTML（5,984 字节）。桌面这一层没有交互式过盾、也没有 CookieJar
 * （`DesktopHttpEngine` 类注释那两条），所以客户端那句"把人机验证窗口点过去"在这台机器上**没有对象**
 * ⇒ ⑤ 钉的是"本机那半句必须与原话一起上屏"。有 key 会不会不同：**没量**（本机没有 key），屏上不许替它答。
 */
class DesktopReverseSearchTest {

    /** 只记"发出去的是什么"，不发网络。 */
    private class FakeReverse(
        override val maxResults: Int = 20,
        private val page: SauceNaoPage? = null,
        private val failure: Throwable? = null,
    ) : GalleryReverseSearch {
        val asked = mutableListOf<String>()

        override suspend fun searchByFile(
            bytes: ByteArray,
            fileName: String,
            mimeType: String,
            allowNsfw: Boolean,
        ): SauceNaoPage = error("这一族发的是站方那一张的地址；本机的图源走「用本机的一张图找」那一格（批 77 下半）")

        override suspend fun searchByUrl(imageUrl: String, allowNsfw: Boolean): SauceNaoPage {
            asked += "$imageUrl|allowNsfw=$allowNsfw"
            failure?.let { throw it }
            return requireNotNull(page) { "这颗替身没被交给结果，却被走了一次" }
        }
    }

    private fun hit(
        similarity: Int = 95,
        indexName: String = "danbooru2023",
        artist: String? = "anmi",
        site: GallerySite? = GallerySite.YANDERE,
        id: Long = 1234567L,
        part: String? = null,
    ) = SauceNaoHit(
        similarity = similarity,
        thumbnailUrl = "https://i.saucenao.com/thumb/1.jpg",
        title = "某作品",
        artistName = artist,
        extUrls = listOf("https://yande.re/post/show/1234567"),
        indexName = indexName,
        part = part,
        siteRef = site?.let { GallerySiteRef(it, id) },
    )

    private fun page(
        hits: List<SauceNaoHit>,
        status: Int = 0,
        message: String? = null,
        returned: Int = hits.size,
    ) = SauceNaoPage(
        status = status,
        message = message,
        resultsReturned = returned,
        totalMatches = returned,
        hits = hits,
    )

    private fun post(
        fileUrl: String = "https://img.yande.re/1234567.jpg",
        largeUrl: String = "https://img.yande.re/sample-1234567.jpg",
        previewUrl: String = "https://img.yande.re/preview-1234567.jpg",
    ) = GalleryPost(
        site = GallerySite.YANDERE,
        id = 1234567L,
        fileUrl = fileUrl,
        largeUrl = largeUrl,
        previewUrl = previewUrl,
    )

    private fun run(
        reverse: GalleryReverseSearch,
        post: GalleryPost,
        masked: Boolean = false,
        hasKey: Boolean = true,
        allowNsfw: Boolean = false,
    ): DesktopReverseOutcome = runBlocking {
        desktopReverseRun(reverse, post, masked, hasKey, allowNsfw)
    }

    /** ① 发出去的是**原图那一档**的地址，而封顶那枚数吃的是契约上的成员。 */
    @Test
    fun 发车带的是原图档而封顶那枚数来自契约() = runBlocking {
        val reverse = FakeReverse(maxResults = 7, page = page(listOf(hit(), hit())))
        val outcome = desktopReverseRun(reverse, post(), masked = false, hasKey = true, allowNsfw = false)
        assertEquals("一趟只该发一笔", 1, reverse.asked.size)
        assertTrue("发出去的不是原图那一档：" + reverse.asked[0], reverse.asked[0].startsWith("https://img.yande.re/1234567.jpg"))
        assertTrue("没把守卫那把判据带过去：" + reverse.asked[0], reverse.asked[0].endsWith("allowNsfw=false"))
        val answered = outcome as DesktopReverseOutcome.Answered
        assertEquals(2, answered.hits.size)
        assertEquals("上限没吃契约那枚数", 7, answered.cap)
        assertNull("有原图档就不该念退档那句", answered.note)
    }

    /** ② 没键 ⇒ 一发都不发，而那一句点名去哪儿填。 */
    @Test
    fun 没填键时不发车() {
        val reverse = FakeReverse(page = page(listOf(hit())))
        val outcome = run(reverse, post(), hasKey = false, allowNsfw = true)
        assertTrue("没键也发车了 —— 那一笔只会白等", outcome is DesktopReverseOutcome.NotSent)
        assertEquals("没发车就是零笔", 0, reverse.asked.size)
        val why = (outcome as DesktopReverseOutcome.NotSent).why
        assertTrue("那句没点出去哪儿填：" + why, why.contains(DESKTOP_SAUCE_NAO_KEY_SECTION_TITLE))
        assertFalse("不许说成「搜失败了」", why.contains("没搜成"))
    }

    /** ③ 被分级挡住的那一张**不交给站方比对**，而这一档排在"有没有键"之前。 */
    @Test
    fun 挡住的那一张不发出去() {
        val reverse = FakeReverse(page = page(listOf(hit())))
        val masked = run(reverse, post(), masked = true)
        assertTrue(masked is DesktopReverseOutcome.NotSent)
        assertEquals(0, reverse.asked.size)
        val why = (masked as DesktopReverseOutcome.NotSent).why
        assertTrue("那句要说清是分级挡的：" + why, why.contains("分级"))
        // 次序本身要单独钉：扰动把两档换了个位置而上面那一条**照样绿**（默认递的是 hasKey=true，
        // 换序后仍然走到 masked 那一支）—— 只有"又被挡又没键"这一格才分得出谁在前。
        val both = run(reverse, post(), masked = true, hasKey = false) as DesktopReverseOutcome.NotSent
        assertTrue("没键时被挡那张跑去念「去填键」了：" + both.why, both.why.contains("分级"))
        assertFalse("缺席那句不该同时指两处", both.why.contains(DESKTOP_SAUCE_NAO_KEY_SECTION_TITLE))
        // 反着钉一次：没键而没被挡时说的是"填键"，两档不许塌成同一句。
        val noKey = run(reverse, post(), hasKey = false) as DesktopReverseOutcome.NotSent
        assertFalse("两档缺席说成了同一句", noKey.why == why)
    }

    /** ④ 站方没给原图档 ⇒ 退到下一档发出去，而屏上必须念出"退的是哪一步"。 */
    @Test
    fun 没有原图档时退一档并把退档那句带上来() {
        val reverse = FakeReverse(page = page(listOf(hit())))
        val outcome = run(reverse, post(fileUrl = "", largeUrl = "https://img.yande.re/sample-9.jpg"), allowNsfw = true)
        assertTrue("发出去的不是大图那一档：" + reverse.asked[0], reverse.asked[0].startsWith("https://img.yande.re/sample-9.jpg"))
        val note = (outcome as DesktopReverseOutcome.Answered).note
        assertTrue("没念出这是退档发的那一笔：" + note, note != null && note.contains("原图"))
        // 三档地址全空 ⇒ 不发，也不许把空串发出去。
        val empty = FakeReverse(page = page(emptyList()))
        assertTrue(run(empty, post(fileUrl = "", largeUrl = "", previewUrl = "")) is DesktopReverseOutcome.NotSent)
        assertEquals("地址全空还是发车了", 0, empty.asked.size)
    }

    /** ⑤ 失败那一句：**原话留着**，而本机那半句一起念（403 那句里的人机验证窗口在桌面没有对象）。 */
    @Test
    fun 失败原话留着而本机那半句一起念() {
        val reason = "被 Cloudflare 挡住了（403）。刚才那个人机验证窗口要么被关掉、要么没通过 —— 再搜一次并把它点过去"
        val outcome = run(FakeReverse(failure = SauceNaoException(reason)), post())
        val failed = outcome as DesktopReverseOutcome.Failed
        assertTrue("原话被吞了：" + failed.reason, failed.reason == reason)
        val reading = desktopReverseFailedReading(failed.reason)
        assertTrue("没把原话端上屏：" + reading, reading.contains("（403）"))
        assertTrue("没念本机这一层没有过盾那一步：" + reading, reading.contains("人机验证") && reading.contains("本机"))
    }

    /** ⑥ 站方"没有相似图"是**一次答案**，与"发失败了"、与"回了而摆不出"是三档。 */
    @Test
    fun 没有相似图与搜失败了与摆不出是三档() {
        val none = run(FakeReverse(page = page(emptyList(), status = -2, message = "No results")), post())
            as DesktopReverseOutcome.NoSimilar
        val noneReading = desktopReverseNoSimilarReading(none.message, none.returned)
        val failed = run(FakeReverse(failure = SauceNaoException("连不上 SauceNAO：timeout")), post())
            as DesktopReverseOutcome.Failed
        val failedReading = desktopReverseFailedReading(failed.reason)
        assertTrue("两句同形就分不清该换图还是该重试", noneReading != failedReading)
        assertTrue("没把站方那句带上：" + noneReading, noneReading.contains("No results"))
        assertFalse("答案那档不许说成失败", noneReading.contains("没搜成"))
        // 站方回了 3 条而一条都摆不出（被解析那一步挡了）—— 不是"没有相似图"。
        val dropped = run(FakeReverse(page = page(emptyList(), returned = 3)), post()) as DesktopReverseOutcome.NoSimilar
        val droppedReading = desktopReverseNoSimilarReading(dropped.message, dropped.returned)
        assertTrue("没说清回了 3 条而摆不出：" + droppedReading, droppedReading.contains("3 条") && droppedReading.contains("摆不出"))
        assertFalse("摆不出那档不许谎称站方说没有", droppedReading.contains("没有相似图"))
    }

    /** ⑦ 协程被取消**原样抛出去**，不许翻成"这一张没搜成"（那是把打断读成结果）。 */
    @Test
    fun 取消不算一次失败() {
        val thrown = runCatching {
            run(FakeReverse(failure = CancellationException("换了一张卡")), post())
        }.exceptionOrNull()
        assertTrue("取消被吞成了结果：" + thrown, thrown is CancellationException)
    }

    /** ⑧ 一条命中的三句读数各说各的；"对上了我们那两站"那一档要点名本机做不到什么。 */
    @Test
    fun 命中那三句读数() {
        val line = desktopReverseHitReading(hit(similarity = 42, indexName = "materialize", part = "(3/3)"))
        assertTrue("相似度、库名、段号都得在：" + line, line.contains("42%") && line.contains("materialize") && line.contains("(3/3)"))
        assertEquals("作者未知要念出来而不是留空行", "作者未知", desktopReverseArtistReading(hit(artist = null)))
        assertEquals("作者：anmi", desktopReverseArtistReading(hit(artist = "anmi")))
        val onSite = requireNotNull(desktopReverseSiteRefReading(hit())) { "对上了站内却没句子" }
        assertTrue("没点名站名与编号：" + onSite, onSite.contains("yande.re") && onSite.contains("1234567"))
        assertTrue("没坦白本机没有按编号开大图页那一层：" + onSite, onSite.contains("本机") && onSite.contains("开大图页"))
        assertNull("对不上我们那两站时这一句不该存在", desktopReverseSiteRefReading(hit(site = null)))
        assertTrue("封顶那句没吃契约那枚数", desktopReverseCapReading(cap = 20, taken = 3).contains("20"))
        assertFalse("换个上限念出同一句就是写死了", desktopReverseCapReading(20, 3) == desktopReverseCapReading(9, 3))
    }

    /** ⑨ 摘桩：那颗件里不许再有抛桩异常，而客户端拿的是**反搜那一枚**凭据。 */
    @Test
    fun 抛桩摘干净而凭据取对的那枚() {
        val body = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryReverseSearch.kt"))
        val wiredName = "ReverseSearch" + "NotWiredOnDesktop"
        val left = DesktopSourceTree.desktopMainSources().sumOf { file -> file.readText().split(wiredName).size - 1 }
        assertEquals("摘桩没摘干净（桌面 main 里还剩它）", 0, left)
        assertTrue("没走共享那颗客户端：" + body, body.contains("SauceNaoClient.getInstance("))
        assertEquals("反搜只许带 sauceNao 那一枚凭据", 1, body.split("credentials.sauceNao").size - 1)
        assertFalse("不许把 Gelbooru 那枚凭据带进反搜", body.contains("credentials.apiKey"))
        assertTrue("上限没转交共享那枚常量", body.contains("SauceNaoClient.SAUCE_NUM_RESULTS"))
        val ports = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryPorts.kt"))
        assertTrue("端口没把引擎与凭据递给反搜件", ports.contains("DesktopGalleryReverseSearch(handle.engine, credentials"))
    }

    /** ⑩ 那一格真接在浮层上：入口一枚、跑一次、结果排在动作之后而标签墙之前。 */
    /** ⑪ 分级那把判据与对岸同一把（逐字回核，不留第二份口径）。 */
    @Test
    fun `⑪ AllowNsfw 与对岸同一把`() {
        val screen = DesktopSourceTree.androidSource("gallery/ui/GalleryScreen.kt").readText()
        val anchor = Regex("""val allowNsfw = maskMode == "([^"]+)"""").find(screen)?.groupValues?.get(1)
        assertTrue("对照组失效：对岸那句没读到", anchor != null)
        assertTrue("对岸那把判据变了而这颗没跟着变", desktopAllowNsfw(anchor!!))
        assertFalse(desktopAllowNsfw("BLUR"))
        assertFalse(desktopAllowNsfw("HIDE"))
    }

    /** ⑫ 那一区的标题只有一个出处：设置那一屏的头部与"去哪儿填键"那句共用同一颗常量。 */
    @Test
    fun 那一区的标题不许有两份() {
        val settings = DesktopSourceTree.desktopUiSource("DesktopGallerySettingsPane.kt")
        val literal = "\"$DESKTOP_SAUCE_NAO_KEY_SECTION_TITLE\""
        assertEquals("标题字面量在设置那一屏不止一处", 1, DesktopSourceTree.codeOccurrences(settings, literal))
        val policy = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopReverseSearchPolicy.kt"))
        assertTrue("缺席那句没引用那枚常量（改名会留下一句指错地方的话）", policy.contains("DESKTOP_SAUCE_NAO_KEY_SECTION_TITLE"))
    }
}
