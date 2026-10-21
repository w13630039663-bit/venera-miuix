package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryReverseSearch
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GallerySiteRef
import com.venera.compose.gallery.data.SauceNaoHit
import com.venera.compose.gallery.data.SauceNaoPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 桌面第七十七批 批 77 · 下半 · 「用本机的一张图找同一张」那一格。
 *
 * ## 这一批让哪几句旧说明变成假话
 *
 * `DesktopGalleryReverseSearch.kt` 的类注释里有一整节写着「接上了而界面上没有入口」，
 * 那是批 35 的实话（`searchByFile` 真零调用点）。本批把入口做出来 ⇒ 那一节必须改掉，
 * ⑮ 钉住它不许以注释的形态留在树上（[[project-implemented-but-unwired]] 反方向那一条）。
 *
 * ## 输入端那一颗 gate 为什么是六档而不是四档
 *
 * 方案里点名的四档（没挑 / 文件不存在 / 不是图 / 站方无结果）里，只有头两档是**本机的输入**，
 * 后两档其实分家在两处：「不是图」在桌面比 Android 多两档（目录、0 字节 —— 那边由
 * ContentResolver 代挑，桌面吃的是用户手打或文件框给的一串路径），
 * 而「站方无结果」根本不在输入端，它是 `desktopLocalReverseRun` 交回的那一档
 * （复用批 35 那颗 [DesktopReverseOutcome]，不新造第二份落点）。
 * 再加上「没配键」与「超过上限」两档（都逐字取自对岸 `GalleryReverseViewModel`，见 ④）。
 *
 * ## 一台机器上做不到的那一跳，这里不假装做得到
 *
 * 「挑图」这一步要弹系统的文件框，而 agent 侧不许动鼠标（手册 §8）⇒ 种子走的是
 * **跳过文件框、直接给路径**那一条（`--reverse-file=`），它验的是路径之后的全部三跳。
 * 文件框本身那一条 2026-10-09 09:02 **改判过措辞**：起窗那一跑里外部有人按了那枚按钮并挑走一张 2 MB 的 png，
 * 屏上与 stdout 同刻念着那张的名字 ⇒ 那一跳"有读数"而"无人可复核是谁点的"，本批没驱动它。
 * ⑰ 钉的仍是"不许把没量过的写成验过了"，只是这一句现在写的是"有外部读数、无可复核"。
 */
class DesktopGalleryLocalReverseTest {

    /** 只记"本机这一路交出去的是什么"，不发网络。 */
    private class FakeReverse(
        override val maxResults: Int = 20,
        private val page: SauceNaoPage? = null,
        private val failure: Throwable? = null,
    ) : GalleryReverseSearch {
        val asked = mutableListOf<String>()
        var sentBytes: Int? = null

        override suspend fun searchByFile(
            bytes: ByteArray,
            fileName: String,
            mimeType: String,
            allowNsfw: Boolean,
        ): SauceNaoPage {
            sentBytes = bytes.size
            asked += "$fileName|$mimeType|allowNsfw=$allowNsfw"
            failure?.let { throw it }
            return requireNotNull(page) { "这颗替身没被交给结果，却被走了一次" }
        }

        override suspend fun searchByUrl(imageUrl: String, allowNsfw: Boolean): SauceNaoPage =
            error("本机的图源这一族只走 searchByFile，地址那一路是浮层那一格的事")
    }

    private fun hit(similarity: Int = 91) = SauceNaoHit(
        similarity = similarity,
        thumbnailUrl = "https://i.saucenao.com/thumb/1.jpg",
        title = "某作品",
        artistName = "anmi",
        extUrls = listOf("https://yande.re/post/show/1234567"),
        indexName = "danbooru2023",
        part = null,
        siteRef = GallerySiteRef(GallerySite.YANDERE, 1234567L),
    )

    private fun page(hits: List<SauceNaoHit>, message: String? = null, returned: Int = hits.size) =
        SauceNaoPage(
            status = 0,
            message = message,
            resultsReturned = returned,
            totalMatches = returned,
            hits = hits,
        )

    private val root: File = File(System.getProperty("java.io.tmpdir"), "b77local-${System.nanoTime()}")

    private fun fixture(name: String, bytes: Int): String {
        root.mkdirs()
        val file = File(root, name)
        if (bytes >= 0) file.writeBytes(ByteArray(bytes)) else file.createNewFile()
        return file.absolutePath
    }

    /** 一个大文件：不写真字节，只把长度撑起来（`setLength` 是稀疏的，一秒内完成）。 */
    private fun sparse(name: String, length: Long): String {
        root.mkdirs()
        val file = File(root, name)
        java.io.RandomAccessFile(file, "rw").use { it.setLength(length) }
        return file.absolutePath
    }

    private fun directory(): String {
        root.mkdirs()
        val dir = File(root, "那一堆")
        dir.mkdirs()
        return dir.absolutePath
    }

    private fun runGate(
        path: String?,
        hasKey: Boolean = true,
    ): DesktopLocalReverseGate = desktopLocalReverseGate(path, hasKey)

    /** ① 没挑 ⇒ 一发都不发，而那一句不许去问键（次序：先说缺的是输入）。 */
    @Test
    fun 没挑图时不发车而那一句不去问键() {
        val blocked = runGate(null)
        assertTrue("没挑也发了：" + blocked, blocked is DesktopLocalReverseGate.Blocked)
        val why = (blocked as DesktopLocalReverseGate.Blocked).why
        assertTrue("没挑那一句不该把注意力带去配键：" + why, !why.contains("key"))
        // 又没挑又没键时念的是**挑**那一句（对岸同一序：输入空先于配键检查）。
        val both = runGate("", hasKey = false) as DesktopLocalReverseGate.Blocked
        assertFalse("两档同时成立时念错了那一句：" + both.why, both.why.contains("key"))
    }

    /** ② 没键 ⇒ 不发，而那一句与批 35 那一枚**同源**（不许有第二份字面量）。 */
    @Test
    fun 没配键时不发车而那一句只有一份() {
        val path = fixture("一张.png", 12)
        val blocked = runGate(path, hasKey = false) as DesktopLocalReverseGate.Blocked
        assertTrue("没键那一句没点名去哪儿：" + blocked.why, blocked.why.contains("免费"))
        val literal = "先去填一枚免费 key"
        val total = DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, literal) }
        assertEquals("发车判据的两条路各写了一份配键那句", 1, total)
    }

    /** ③ 输入端五档各一句，且**都没发车**（读字节那一步一次都没被走到）。 */
    @Test
    fun 本机那一串路径的五档各归各() {
        val missing = runGate(File(root, "没有那一个.png").absolutePath) as DesktopLocalReverseGate.Blocked
        val dir = runGate(directory()) as DesktopLocalReverseGate.Blocked
        val notImage = runGate(fixture("一份.txt", 12)) as DesktopLocalReverseGate.Blocked
        val empty = runGate(fixture("空的.png", 0)) as DesktopLocalReverseGate.Blocked
        val huge = runGate(sparse("很大的.png", DESKTOP_LOCAL_REVERSE_MAX_UPLOAD_BYTES + 1)) as DesktopLocalReverseGate.Blocked

        assertTrue("不存在那句没说出找不到：" + missing.why, missing.why.contains("找不到"))
        assertTrue("目录那句没说出那不是图：" + dir.why, dir.why.contains("目录"))
        assertTrue("认不出那句没列出接哪几种：" + notImage.why, notImage.why.contains("jpg"))
        assertTrue("0 字节那句没念那个数：" + empty.why, empty.why.contains("0"))
        assertTrue("超上限那句没念 MB：" + huge.why, huge.why.contains("MB"))

        val sentences = listOf(missing.why, dir.why, notImage.why, empty.why, huge.why)
        assertEquals("五档里有两档塌成同一句：" + sentences, 5, sentences.distinct().size)

        // 五档都不许走到"读字节"那一步 —— 不发就不该把整张图塞进内存。
        var reads = 0
        runBlocking {
            listOf(missing, dir, notImage, empty, huge).forEach { gate ->
                desktopLocalReverseRun(FakeReverse(page = page(listOf(hit()))), gate, false, readFile = {
                    reads++
                    ByteArray(0)
                }, onThrowable = {})
            }
        }
        assertEquals("挡住的那几档读了字节", 0, reads)
    }

    /** ④ 上限那枚数与对岸同值（口径不是桌面自己定的）。 */
    @Test
    fun 上限那枚数与对岸同一把() {
        val across = DesktopSourceTree.androidSource("gallery/ui/GalleryReverseViewModel.kt").readText()
        assertTrue("对岸那道闸的写法变了而桌面没跟上：" + across, across.contains("MAX_UPLOAD_BYTES = 6L * 1024 * 1024"))
        assertEquals("桌面这一颗没跟对岸同值", 6L * 1024 * 1024, DESKTOP_LOCAL_REVERSE_MAX_UPLOAD_BYTES)
    }

    /** ⑤ 认哪些扩展名：大小写都要认，别的种类交回 null（= 不是图那一档）。 */
    @Test
    fun 那张是不是图由扩展名判() {
        listOf("jpg", "jpeg", "png", "gif", "webp", "bmp").forEach { ext ->
            assertNotNull("接这一种却认不出：$ext", desktopLocalReverseMimeType("一张.$ext"))
        }
        assertEquals("jpeg 与 jpg 该同一种", "image/jpeg", desktopLocalReverseMimeType("A.JPG"))
        assertNull("那种不是图", desktopLocalReverseMimeType("一份.txt"))
        assertNull("没有扩展名不算图", desktopLocalReverseMimeType("没有后缀"))
        assertNull("光有点不算图", desktopLocalReverseMimeType("."))
    }

    /** ⑥ 发出去的是**那张的字节 + 那一种 mime + 守卫那把判据**。 */
    @Test
    fun 发车带的是字节与类型与守卫() = runBlocking {
        val reverse = FakeReverse(page = page(listOf(hit())))
        val path = fixture("这一张.png", 17)
        val gate = runGate(path)
        val proceed = gate as DesktopLocalReverseGate.Proceed
        assertEquals("mime 认错了种类", "image/png", proceed.mimeType)
        assertEquals("长度没取到", 17L, proceed.sizeBytes)
        val outcome = desktopLocalReverseRun(reverse, gate, allowNsfw = true, readFile = { File(it).readBytes() }, onThrowable = {})
        assertEquals("一趟只该发一笔", 1, reverse.asked.size)
        assertEquals("带的不是那一张的字节数", 17, reverse.sentBytes)
        assertTrue("没把守卫那把判据带过去：" + reverse.asked[0], reverse.asked[0].endsWith("allowNsfw=true"))
        assertTrue("文件名没交出去：" + reverse.asked[0], reverse.asked[0].startsWith("这一张.png"))
        assertTrue("结果那一档不对：" + outcome, outcome is DesktopReverseOutcome.Answered)
    }

    /** ⑦ 落点复用批 35 那颗四档：封顶吃契约那枚数、无结果带站方回条数、失败留原话。 */
    @Test
    fun 落点复用批35那颗四档() = runBlocking {
        val answered = FakeReverse(maxResults = 9, page = page(listOf(hit(), hit())))
        val cap = desktopLocalReverseRun(answered, runGate(fixture("甲.png", 4)), false, readFile = { ByteArray(3) }, onThrowable = {})
        assertEquals("封顶没吃契约那枚数", 9, (cap as DesktopReverseOutcome.Answered).cap)
        assertNull("本机这一路没有退档那件事，不该念那句", cap.note)

        val none = FakeReverse(page = page(emptyList(), message = "No Similar Images.", returned = 0))
        val noSimilar = desktopLocalReverseRun(none, runGate(fixture("乙.png", 4)), false, readFile = { ByteArray(3) }, onThrowable = {})
        val said = noSimilar as DesktopReverseOutcome.NoSimilar
        assertEquals("站方那句原话没留着", "No Similar Images.", said.message)
        assertEquals("回条数没带上来", 0, said.returned)

        val broken = FakeReverse(failure = IllegalStateException("SauceNAO 回 403"))
        val failed = desktopLocalReverseRun(broken, runGate(fixture("丙.png", 4)), false, readFile = { ByteArray(3) }, onThrowable = {})
        assertEquals("原话没留着：" + failed, "SauceNAO 回 403", (failed as DesktopReverseOutcome.Failed).reason)
    }

    /** ⑧ 取消**不算一次失败**（换域、关屏都会取消它）。 */
    @Test
    fun 取消不算一次失败() {
        val reverse = object : GalleryReverseSearch {
            override val maxResults = 20
            override suspend fun searchByFile(bytes: ByteArray, fileName: String, mimeType: String, allowNsfw: Boolean) =
                throw CancellationException("换域了")

            override suspend fun searchByUrl(imageUrl: String, allowNsfw: Boolean) = error("不该走到这里")
        }
        var thrown = false
        try {
            runBlocking { desktopLocalReverseRun(reverse, runGate(fixture("丁.png", 4)), false, readFile = { ByteArray(2) }, onThrowable = {}) }
        } catch (cancellation: CancellationException) {
            thrown = true
        }
        assertTrue("取消被翻成了一个结果", thrown)
    }

    /** ⑨ 读字节那一步炸了要念成"没搜成"，而不是把异常抛到界面上。 */
    @Test
    fun 读不出字节时念成没搜成() {
        val outcome = runBlocking {
            desktopLocalReverseRun(FakeReverse(page = page(listOf(hit()))), runGate(fixture("戊.png", 4)), false, readFile = {
                throw IllegalStateException("那张正被别的程序占着")
            }, onThrowable = {})
        }
        assertTrue("读字节失败被吞了：" + outcome, outcome is DesktopReverseOutcome.Failed)
        assertTrue("原话没上来：" + outcome, (outcome as DesktopReverseOutcome.Failed).reason.contains("占着"))
    }

    /** ⑩ 屏上那几句**全部**来自共享那颗件：新那两颗不许自带第二份反搜口径。 */
    /** ⑪ 挑中的那一句要念得出**是哪一张、多大**，并明说整张原样交出去。 */
    @Test
    fun 挑中那一句念得出哪一张与多大() {
        val small = desktopLocalReversePickedReading("一张.png", 3 * 1024)
        assertTrue("没点名是哪一张：" + small, small.contains("一张.png"))
        assertTrue("没念大小：" + small, small.contains("KB"))
        assertTrue("没说出整张原样：" + small, small.contains("原样"))
        val big = desktopLocalReversePickedReading("很大.jpg", 2 * 1024 * 1024)
        assertTrue("MB 那一档没念对：" + big, big.contains("MB"))
    }

    /** ⑫ `D_` 读数：三档落点各不同、前缀一致、失败那句带型与栈。 */
    @Test
    fun 读数三档各归各() {
        val answered = desktopLocalReverseReadout(
            "x/一张.png",
            DesktopLocalReverseGate.Proceed("x/一张.png", "一张.png", "image/png", 4L),
            DesktopReverseOutcome.Answered(listOf(hit(), hit()), 20, null),
        )
        val notSent = desktopLocalReverseReadout(
            "x/一张.png",
            DesktopLocalReverseGate.Blocked("本机找不到那一个"),
            DesktopReverseOutcome.NotSent("本机找不到那一个"),
        )
        val failed = desktopLocalReverseReadout(
            "x/一张.png",
            DesktopLocalReverseGate.Proceed("x/一张.png", "一张.png", "image/png", 4L),
            DesktopReverseOutcome.Failed("SauceNAO 回 403"),
        )
        listOf(answered, notSent, failed).forEach { line ->
            assertTrue("读数前缀不对：" + line, line.startsWith("D_反搜本地"))
        }
        assertEquals("三档念成同一句", 3, listOf(answered, notSent, failed).distinct().size)
        assertTrue("命中的条数没进读数：" + answered, answered.contains("命中=2"))
        assertTrue("没发车那档要念得出没发：" + notSent, notSent.contains("发=否"))
        assertTrue("失败那句要带原话：" + failed, failed.contains("403"))
        // ⚠️ 这一条是**图证抓出来的**（2026-10-09 帧 A）：第一版从闸口那一颗取"挑的是哪一张"，
        // 而挡住时那颗闸口没有文件名 ⇒ 用户明明给了一条路径，stdout 却念「挑=没挑」。
        // 读数与行为不一致时，只有屏上/日志那一眼抓得到（手册 §8 那条"图证的第二个用途"）。
        assertTrue("给了路径而挡住时不许念成没挑：" + notSent, notSent.contains("挑=一张.png"))
        assertTrue("真没挑时才念没挑：" + desktopLocalReverseReadout(null, DesktopLocalReverseGate.Blocked("先挑一张"), DesktopReverseOutcome.NotSent("先挑一张")),
            desktopLocalReverseReadout(null, DesktopLocalReverseGate.Blocked("先挑一张"), DesktopReverseOutcome.NotSent("先挑一张")).contains("挑=没挑"))

        val refusal = desktopLocalReverseRefusalReading(
            "SauceNAO 回 403",
            IllegalStateException("https://saucenao.com/search.php?api_key=SECRETVALUE&db=999"),
        )
        assertTrue("没念异常类型：" + refusal, refusal.contains("型=IllegalStateException"))
        assertTrue("没念栈顶那一帧：" + refusal, refusal.contains("栈="))
        assertTrue("键值要被念得出（才知道是哪一枚）：" + refusal, refusal.contains("api_key=***"))
        assertFalse("键值漏进读数了：" + refusal, refusal.contains("SECRETVALUE"))
        // 读数那一句同样不许漏：站方失败的原话常常**就是**那条带 query 的 URL。
        val leaky = desktopLocalReverseReadout(
            "x/一张.png",
            DesktopLocalReverseGate.Proceed("x/一张.png", "一张.png", "image/png", 4L),
            DesktopReverseOutcome.Failed("https://saucenao.com/search.php?api_key=SECRETVALUE 回 403"),
        )
        assertFalse("原话里的键值被念进读数了：" + leaky, leaky.contains("SECRETVALUE"))
        assertTrue("打码之后那句仍然说得出是 403：" + leaky, leaky.contains("403"))
    }

    /** ⑬ 接线：那一格里发车只有一条路，而挑图那一步只有按钮走得到。 */
    /** ⑭ 那一格真挂在搜索那一屏上，且排在输入行之后（按完看不见东西与没做同一件事）。 */
    /** ⑮ 旧说明下架：那颗数据层不许再写「界面上没有入口」。 */
    @Test
    fun 那句没有入口的旧说明已下架() {
        val data = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryReverseSearch.kt").readText()
        assertFalse("那一节还在说没有入口：" + data, data.contains("没有入口"))
        assertTrue("改完要说清入口在哪：" + data, data.contains("用本机的一张图"))
    }

    /** ⑯ 开浏览器那枚能力判据只有一份（两处界面共用一颗件）。 */
    /** ⑰ 没量过的那一跳不许写成验过了。 */
}
