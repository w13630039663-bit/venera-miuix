package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 第一百一十六批 · **离线阅读**：翻开那一屏先问本机那一棵树，缺的才问源站。
 *
 * ## 量到的缺口（现查，不是推测）
 *
 * 批 96 把整话写下盘，97 数得着，98 删得掉，111/113 补得齐，115 把那些接上了屏 —— 而**翻开那一屏**
 * 一页都不吃盘：`VeneraDesktopLargeScreen.kt` 的取页 effect 只有 `e.load` + `e.pages`，
 * `DesktopComicPageFetcher.fetch()` 只有 `engine.pageImage`。用户攒在机器上的那 24 MB：
 * 断网翻不开，联网还得把同样的字节再取一遍。
 *
 * ## 这一把尺子钉的四件事
 *
 * 1. **认话那一格按源站话号**（行号会漂），且比的那两格**先净化再比** —— 盘上目录名是净化过的而
 *    阅读器手上是原始值，不同轨就是「本机有而还去问源站」（② 是本批自审时补逮的那一条）。
 * 2. **该不该问引擎**那三档 + 页表那三支形状。缺页时每一页两路都带，谁吃谁由 `fetch()` 判。
 * 3. **缓存键不掺 `localDir`**：离线读过一次不该让联网那次的缓存作废。
 * 4. **屏顶那一句守两条假话**：不许把「靠盘上翻开」念成整话都在，也不许念成整屏失败。
 */
class DesktopComicOfflineReadingTest {

    private fun tempRoot(): File = Files.createTempDirectory("b116or").toFile()

    /** 按**写侧那一条公式**造一话：目录名不对就整条认不出，所以必须共用那颗公式而不是手拼。 */
    private fun chapterOnDisk(
        root: File, sourceKey: String, comicId: String, chapterId: String,
        order: Int, title: String, expected: Int, whole: Int, broken: Int = 0,
    ): File {
        val dir = desktopComicChapterDir(root, sourceKey, comicId, order, title)
        dir.mkdirs()
        for (index in 0 until whole) DesktopComicPageFixtures.writeWholePage(dir, index)
        for (offset in 0 until broken) DesktopComicPageFixtures.writeBrokenPage(dir, whole + offset)
        desktopComicWriteChapterManifest(dir, chapterId, order, title, expected, complete = whole >= expected)
        return dir
    }

    private fun local(dir: File, written: Int, expected: Int) = DesktopComicLocalChapter(dir, written, expected)

    /** ① 按源站话号认出本机那一话；认不到就是 `null`，绝不猜一条目录。 */
    @Test
    fun 本机那一话按源站话号认而不按行号猜() {
        val root = tempRoot()
        try {
            val dir = chapterOnDisk(root, "jm", "1461316", "g42", order = 5, "第5話", expected = 3, whole = 3)
            val got = desktopComicLocalChapter(root, "jm", "1461316", "g42")
            assertNotNull("清单里明写着这一枚话号而认不到：", got)
            assertEquals("认下来的就是那条目录：", dir.absolutePath, got!!.dir.absolutePath)
            assertEquals("整话三页都该算已有：", 3, got.writtenPages)
            assertEquals("应写跟着清单：", 3, got.expectedPages)
            // 行号那一格**不参与**认话：拿 order 当话号问就是猜目录。
            assertNull("按行号（5）问也认到了 ⇒ 两端不是只认话号：",
                desktopComicLocalChapter(root, "jm", "1461316", "5"))
            assertNull("话号对不上就该交回 null：", desktopComicLocalChapter(root, "jm", "1461316", "g43"))
            assertNull("号对不上就该交回 null：", desktopComicLocalChapter(root, "jm", "9", "g42"))
            assertNull("站对不上就该交回 null：", desktopComicLocalChapter(root, "goda", "1461316", "g42"))
        } finally { root.deleteRecursively() }
    }

    /** ② 目录名是净化过的而阅读器手上是原始值：两轨必须归一，否则「本机有而还去问源站」。 */
    @Test
    fun 源与号带路径字符时仍认得同一话() {
        val root = tempRoot()
        // 这两枚带 `/` 与 `:`，落盘时被净化成 `_` ⇒ 盘上目录名与阅读器手上的值不同形。
        try {
            chapterOnDisk(root, "jm/sub", "14:61316", "g42", order = 0, "t", expected = 2, whole = 2)
            val byRaw = desktopComicOfflineLocalChapter(root, "jm/sub", "14:61316", "g42")
            assertNotNull("原始值认不到（净化前后不同轨）：", byRaw)
            assertEquals("带斜杠的源照样两页都在：", 2, byRaw!!.writtenPages)
            // 对照组：净化后的那一形也该认得 —— 两向都通才叫归一。
            assertNotNull("净化值反而认不到：",
                desktopComicOfflineLocalChapter(root, "jm_sub", "14_61316", "g42"))
            // 归一**不许**住在读侧那颗件里（第一百一十二批那条纪律）：直接拿原始值问读侧就认不到，
            // 那一屏念的「已下全 / 缺 N 页」因此只跟着盘上状态改口，不跟着净化口径改口。
            assertNull("读侧那颗件自己算了净化（那条纪律被搬进来了）：",
                desktopComicLocalChapter(root, "jm/sub", "14:61316", "g42"))
            // 两把源码尺把"归一只有这一个落点"钉住：离线那颗件恰两处（源与号各一），读侧那颗仍零处。
            val offlineFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicOfflineReading.kt")
            assertEquals("离线阅读那颗件里净化恰两处（源与号各一处）：", 2,
                DesktopSourceTree.codeOccurrences(offlineFile, "desktopComicSafePathKey("))
            val listFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicDownloadedList.kt")
            assertEquals("读侧那颗件仍零处净化：", 0,
                DesktopSourceTree.codeOccurrences(listFile, "desktopComicSafePathKey("))
        } finally { root.deleteRecursively() }
    }

    /** ③ 坏页不算「本机已有」：那一页必须回落到源站，而不是本机读到一张过盾页 HTML。 */
    @Test
    fun 坏页那一档本机不算数而盘上颗数只数整页() {
        val root = tempRoot()
        try {
            val dir = chapterOnDisk(root, "jm", "9", "g1", order = 0, "t", expected = 4, whole = 2, broken = 2)
            val got = desktopComicLocalChapter(root, "jm", "9", "g1")
            assertNotNull("那条目录该认得：", got)
            assertEquals("四页里只有两颗算已有（过盾页那两颗不算）：", 2, got!!.writtenPages)
            assertEquals("应写仍是清单那一格：", 4, got.expectedPages)
            assertTrue("缺页时该问源站（页键只有源站认得）：", desktopComicOfflineShouldAskEngine(got))
            assertFalse("本机整话已齐时不该问：", desktopComicOfflineShouldAskEngine(local(dir, 4, 4)))
        } finally { root.deleteRecursively() }
    }

    /** ④ 该不该问引擎的三档（含「清单自己都说不出该有几页」那一档）。 */
    @Test
    fun 三档都答要问引擎而只有整话齐了才不问() {
        assertTrue("本机没这一话：", desktopComicOfflineShouldAskEngine(null))
        assertTrue("盘上小于应写：", desktopComicOfflineShouldAskEngine(local(File("."), 2, 3)))
        assertTrue("应写为零时不许据此断网：", desktopComicOfflineShouldAskEngine(local(File("."), 0, 0)))
        assertTrue("应写为负（烂清单）：", desktopComicOfflineShouldAskEngine(local(File("."), 3, -1)))
        assertFalse("齐了才答不问：", desktopComicOfflineShouldAskEngine(local(File("."), 3, 3)))
        // `EpisodeIsWhole` 与它是同一判的反面，两处不许漂成两把尺子。
        assertTrue("整话齐那一判与「要不要问」反号了：",
            desktopComicOfflineEpisodeIsWhole(local(File("."), 5, 3)))
        assertFalse("缺页不算整话齐：", desktopComicOfflineEpisodeIsWhole(local(File("."), 2, 3)))
        assertFalse("空清单不算整话齐（否则断网也念成已齐）：",
            desktopComicOfflineEpisodeIsWhole(local(File("."), 0, 0)))
    }

    /** ⑤ 页表那三支形状：纯本机 / 混合 / 两路都无。 */
    @Test
    fun 页表三支形状各自正确() {
        val dir = File("D:/x/话")
        // 纯本机：引擎没起来也造得出页表（断网翻得开就是这一支）。
        val diskOnly = desktopComicReaderPages("jm", "9", "g1", emptyList(), dir, 28, null)
        assertEquals("本机 28 页而一颗源站都没问：", 28, diskOnly.size)
        assertEquals("纯本机那几页的页名要能对上盘上页名：",
            (0 until 28).map { desktopComicChapterPageFileName(it) }, diskOnly.map { it.imageKey })
        assertTrue("纯本机那一支每一页都带目录且没有引擎：",
            diskOnly.all { it.localDir == dir && it.engine == null && desktopComicPageCanFetch(it) })
        // 混合：源站回了 30 枚页键而盘上只有 28 页 ⇒ 每一页两路都带上，谁吃谁由 fetch 判。
        val keys = (0 until 30).map { "k$it.jpg" }
        val mixed = desktopComicReaderPages("jm", "9", "g1", keys, dir, 28, null)
        assertEquals("混合时页表按源站回的页键数：", 30, mixed.size)
        assertEquals("页键直接跟源站那一串：", keys, mixed.map { it.imageKey })
        assertTrue("混合时每一页都带本机目录（缺的那几页由判式挡回去问引擎）：",
            mixed.all { it.localDir == dir })
        assertEquals("页序 0 基（掺一枚 ±1 就是复合键两端不同轨那一族）：",
            (0 until 30).toList(), mixed.map { it.page })
        // 两路都无：空表，屏上念「这一话没取到页」而不是摆一排空白。
        assertEquals("两路都没有就该是空表：", 0,
            desktopComicReaderPages("jm", "9", "g1", emptyList(), null, 0, null).size)
        assertEquals("本机有目录而颗数为零也是空表：", 0,
            desktopComicReaderPages("jm", "9", "g1", emptyList(), dir, 0, null).size)
    }

    /** ⑥ 缓存键不掺本机目录：离线读过一次不该让联网那次的缓存作废。 */
    @Test
    fun 缓存键不掺本机目录() {
        val base = DesktopComicPage("jm", "1461316", "g", "1760845530.1.jpg#crop", 0, null)
        assertEquals("带目录与不带目录必须是同一个键：",
            base.cacheKey, base.copy(localDir = File("D:/x/话")).cacheKey)
        assertEquals("键仍是那四枚字符串的算式：",
            "comic|jm|1461316|g|1760845530.1.jpg#crop", base.cacheKey)
        // 批 81 那条判据没被这一批削弱：两路都没有仍然不可取。
        assertFalse("引擎与本机两路都没有才判为不可取：", desktopComicPageCanFetch(base))
        assertTrue("只有本机也算一条能取的路（断网翻得开）：",
            desktopComicPageCanFetch(base.copy(localDir = File("D:/x/话"))))
    }

    /** ⑦ 盘上那一颗字节真的取得回来（吃本机排在问引擎前面）。 */
    @Test
    fun 盘上算整页的那些真吃本机而坏页交回空() {
        val root = tempRoot()
        try {
            val dir = chapterOnDisk(root, "jm", "9", "g1", order = 0, "t", expected = 3, whole = 2, broken = 1)
            val first = desktopComicLocalPageBytes(dir, 0)
            assertNotNull("盘上第一颗该读出字节：", first)
            assertTrue("读出的就是盘上那一颗：", first!!.contentEquals(File(dir, "0001.jpg").readBytes()))
            assertNull("过盾页那一颗不许交回字节（该回落到源站重取）：", desktopComicLocalPageBytes(dir, 2))
            assertNull("缺的那一页不许交回空数组冒充：", desktopComicLocalPageBytes(dir, 7))
            assertNull("整条目录都没有：", desktopComicLocalPageBytes(File(root, "没有这一条"), 0))
        } finally { root.deleteRecursively() }
    }

    /** ⑧ 屏顶那一句守两条假话。 */
    @Test
    fun 屏顶那一句不许把靠盘上翻开念成整话都在() {
        val dir = File("D:/x/话")
        // 正常两档：源站带回页键 / 本机齐到不必问 ⇒ 没什么要说。
        assertNull("源站带回页键时不该念东西：",
            desktopComicOfflineReadingNote(null, DesktopComicSourceOutcome.Delivered(30), 30))
        assertNull("整话齐而一颗没问时不该念东西：",
            desktopComicOfflineReadingNote(local(dir, 30, 30), DesktopComicSourceOutcome.NotNeeded, 30))
        // 假话一：靠盘上翻开而缺页 ⇒ 必须念得出「缺的那几页看不了」。
        val partial = desktopComicOfflineReadingNote(
            local(dir, 28, 30), DesktopComicSourceOutcome.Unreachable("引擎没起来"), 28,
        )
        assertNotNull("源站问不到而盘上撑着时一句都不念（用户以为整话都在）：", partial)
        assertTrue("念不出「缺的那几页看不了」：" + partial, partial!!.contains("缺的那几页看不了"))
        assertTrue("念不出盘上颗数：" + partial, partial.contains("28/30"))
        assertTrue("念不出成因：" + partial, partial.contains("引擎没起来"))
        // 假话二：两路都空 ⇒ 不许念成「靠本机翻开」（本机一颗都没有）。
        val none = desktopComicOfflineReadingNote(null, DesktopComicSourceOutcome.Unreachable("引擎没起来"), 0)
        assertNotNull(none)
        assertTrue("两路都空时念不出翻不开：" + none, none!!.contains("翻不开"))
        assertFalse("两路都空却念成靠本机翻开：" + none, none.contains("靠本机"))
        // 源站回来了而回了 0 页（本机也空）：那是源站自己说的话，要念出来。
        val zero = desktopComicOfflineReadingNote(null, DesktopComicSourceOutcome.Delivered(0), 0)
        assertNotNull(zero)
        assertTrue("回了 0 页而屏上空白却不念成因：" + zero, zero!!.contains("回了 0 页"))
    }

    /** ⑨ 取证读数那一行：三档源站说得开，而最后一格是「候选」不是「已吃盘」。 */
    @Test
    fun 取证读数把三档源站说得开() {
        val dir = File("D:/x/话")
        val notNeeded = desktopComicOfflineReadingReading("g1", local(dir, 30, 30),
            DesktopComicSourceOutcome.NotNeeded, 30, 30)
        assertTrue("本机齐了那一档念不出未问：" + notNeeded, notNeeded.contains("源站=未问"))
        assertTrue("本机颗数念错：" + notNeeded, notNeeded.contains("本机=30/30"))
        val mixed = desktopComicOfflineReadingReading("g1", local(dir, 28, 30),
            DesktopComicSourceOutcome.Unreachable("引擎没起来"), 30, 28)
        assertTrue("源站问不到那一档念不出：" + mixed, mixed.contains("源站=问不到"))
        assertTrue("最后一格是候选而不是谎称已吃盘：" + mixed, mixed.contains("本机候选=28"))
        val delivered = desktopComicOfflineReadingReading("g1", null,
            DesktopComicSourceOutcome.Delivered(30), 30, 0)
        assertTrue("带回页键那一档要念得出颗数：" + delivered, delivered.contains("源站=回了30页键"))
        assertTrue("话号要在读数里：" + delivered, delivered.contains("话=g1"))
    }

    /** ⑩ 接线：翻开那一屏真的先问本机，而源站塌了不再清空页表。 */
    @Test
    fun 阅读器那一屏接在本机之后而源站塌了不清页表() {
        val file = DesktopSourceTree.desktopSourceOf("VeneraDesktopLargeScreen.kt")
        val code = DesktopSourceTree.codeText(file)
        for (token in listOf(
            "desktopComicOfflineLocalChapter(",
            "desktopComicOfflineShouldAskEngine(",
            "desktopComicReaderPages(",
            "desktopComicOfflineReadingNote(",
            "desktopComicOfflineReadingReading(",
        )) {
            assertEquals("取页那一屏少了这一格 " + token, 1, DesktopSourceTree.codeOccurrences(file, token))
        }
        // 次序：先读盘 → 再决定问不问源站 → 最后上页表。
        val read = code.indexOf("desktopComicOfflineLocalChapter(")
        val ask = code.indexOf("desktopComicOfflineShouldAskEngine(")
        val served = code.indexOf("readerPages = served")
        assertTrue("读盘排在「决定要不要问引擎」之后（那就是拿没读过的状态做判断）：" + read + " / " + ask,
            read in 0 until ask)
        assertTrue("页表那一趟排在读盘之后（否则 localDir 无处来）：" + ask + " / " + served, ask < served)
        // 那一段里不许再有「清空页表」：源站塌了把本机那 28 页一并抹掉就是这一批要治的第二条假话。
        val block = code.substring(read, served)
        assertEquals("读盘到上屏之间仍有把页表清空的写法：", 0,
            Regex("""readerPages\s*=\s*emptyList\(\)""").findAll(block).count())
        // 这一批没新增第二条引擎路。
        assertEquals("源站那一趟的 pages 仍只一处：", 1,
            DesktopSourceTree.codeOccurrences(file, "e.pages(r.comicId, r.episode)"))
    }
}
