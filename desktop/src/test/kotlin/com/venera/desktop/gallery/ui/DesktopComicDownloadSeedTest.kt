package com.venera.desktop.gallery.ui

import com.venera.desktop.ForensicFlags
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 96：漫画「一整话存到本机」那枚取证种子 `--download=<漫画号>/<话号>`。
 *
 * ## 为什么这一批非有种子不可
 *
 * 那一格今天的入口是**长按**章表里某一话（桌面 `combinedClickable` 有 `onLongClick` 而没有
 * `onRightClick`，那一族在本仓是结构性缺口），而取证不许动鼠标（手册 §8.1）。于是"接上了"这件事
 * 只剩两种证据：离屏语义树（量得到动作挂没挂上，量不到落盘）与真起窗（量得到落盘，走不到那一下）。
 * ⇒ 种子补的就是那一条：让同一颗 runner 在没人按鼠标的情况下自己跑一遍。
 *
 * ## 种子必须吃长按那一条路（这一颗测试存在的主要理由）
 *
 * 另开一条"只为取证"的下载路，量到的是那条路而不是用户那一条 —— 目录名、话号、`D_下载` 读数、
 * 失败那一笔抛不抛，全都可以各自正确而屏上那一条是坏的。所以这里的判式不是"种子也能下载"，
 * 而是**沿路只许有一颗 runner 与一处目录名算式**（判据 ⑤⑥）：多一处就红。
 *
 * ## 三档落空各念一句，不许静默
 *
 * `--download` 与 `--comic` 没配套（详情那一屏根本没组合）、种子点的不是开着的那一本、
 * 话号在那本目录里找不到 —— 三种都是"参数打对了而屏上没动"，只有 stdout 念得出。
 * 本仓那条老账：`D_` 念过 ≠ 屏上动了，但**不念就一定没人接**。
 */
class DesktopComicDownloadSeedTest {

    private val flags = DesktopSourceTree.desktopSourceOf("ForensicFlags.kt")
    private val hostFile = DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt")
    private val host = DesktopSourceTree.codeText(hostFile)
    private val pane = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicPane.kt")
    private val detail = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicDetail.kt")
    private val gradle = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts")

    /** 收 stdout：这一族的读数只活在 stdout，屏上什么都没有。 */
    private fun speak(block: () -> Unit): String {
        val captured = ByteArrayOutputStream()
        val previous = System.out
        try {
            System.setOut(PrintStream(captured, true, "UTF-8"))
            block()
        } finally {
            System.setOut(previous)
        }
        return captured.toString("UTF-8")
    }

    /** 全桌面 main 源里那颗名字的代码行出现次数（声明 + 调用点）。 */
    private fun occurrencesAcrossMain(token: String): Int =
        DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, token) }

    /** ① 判读真跑：没给不吭声，认两截，五档读不出各念一句实话。 */
    @Test
    fun 判读认两截而读不出时念得出() {
        assertNull("没给这枚参数时不许改任何东西", ForensicFlags.download(emptyList()))
        val silent = speak { ForensicFlags.download(emptyList()) }
        assertEquals("没给参数却念了读数：" + silent, 0, silent.lines().count { it.contains("D_") })

        assertEquals(
            "两截没拆成（号, 话号）",
            "1461316" to "1461318",
            ForensicFlags.download(listOf("--download=1461316/1461318")),
        )
        val hit = speak { ForensicFlags.download(listOf("--download=1461316/1461318")) }
        assertTrue("正面那一行没点名走的是哪条路：" + hit, hit.contains("长按"))
        assertTrue("正面那一行没把号与话念出来：" + hit, hit.contains("号=1461316") && hit.contains("话=1461318"))

        // 五档读不出：不带值 / 空值 / 只有一截 / 前头空 / 尾巴空。
        val bad = listOf(
            listOf("--download"),
            listOf("--download="),
            listOf("--download=1461316"),
            listOf("--download=/1461318"),
            listOf("--download=1461316/"),
        )
        val text = speak { bad.forEach { ForensicFlags.download(it) } }
        assertEquals("五档各该念一句，多念少念都不对：" + text, 5, text.lines().count { it.contains("D_取证参数 --download") })
        bad.forEach { args ->
            assertNull("${args} 被认成了某一枚种子：" + text, ForensicFlags.download(args))
        }
        assertTrue("那句实话没写该怎么写：" + text, text.contains("<漫画号>/<话号>"))
        assertTrue("那句实话没交代本轮不下载：" + text, text.contains("本轮不下载"))
    }

    /** ② 登记：漏了 KNOWN 那一格，带它起窗时 unknown 会多念一句「不认识的参数」，读数自相矛盾。 */
    @Test
    fun 参数登记过而拼错照报() {
        assertTrue("`--download` 没登记进 KNOWN", ForensicFlags.KNOWN.contains("--download"))
        assertEquals("登记过却被报成拼错", emptyList<String>(), ForensicFlags.unknown(listOf("--download=1461316/1461318")))
        // 对照组：那张表仍然真在管不认识的那一族（清空 KNOWN 会让上面两条恒真）。
        assertEquals("对照组失效：拼错的参数没被报出来", listOf("--downlond=1/2"), ForensicFlags.unknown(listOf("--downlond=1/2")))
    }

    /** ③ 只给了 `--download` 而详情那一屏没开：必须出声（`detailTierNote` 同一族）。 */
    @Test
    fun 没开详情那一屏时念得出() {
        assertNull("没给种子时不该吭声", ForensicFlags.downloadNote(null, detailOpens = false))
        assertNull("详情本来就要开，没有可念的", ForensicFlags.downloadNote("1461316" to "1461318", detailOpens = true))
        val note = requireNotNull(ForensicFlags.downloadNote("1461316" to "1461318", detailOpens = false))
        assertTrue("没点名是那一枚参数：" + note, note.contains("没有配套的 --comic"))
        assertTrue("没念出要的是哪一本：" + note, note.contains("1461316"))
        assertTrue("没交代本轮不下载：" + note, note.contains("不下载"))
        assertTrue("集成方没念这一句：" + host, host.contains("ForensicFlags.downloadNote(downloadSeed"))
    }

    /** ④ 六层接线都在：判读层、构建脚本那一跳、集成方、config、域 body、详情那一屏的形参。 */
    @Test
    fun 六层接线都在() {
        val flagSrc = DesktopSourceTree.codeText(flags)
        assertTrue("判读层没登记那枚名字：" + flagSrc, flagSrc.contains("private const val DOWNLOAD = \"--download\""))
        // 读数住在判读层：那一行必须出现在 ForensicFlags 的代码行里（在消费点补一句 = 参数根本没被读过也念得出）。
        assertTrue(
            "判读层没念这一枚",
            DesktopSourceTree.codeLinesOf(flags).any { it.contains("DOWNLOAD") && it.contains("D_取证参数") && it.contains("println") },
        )

        // 构建脚本只看**非注释行**（与 `DesktopSourceTree.codeText` 同一口径）：那一枚种子在本仓活过一次
        // "跳被注释掉而文档没改"，用 raw readText 的话注释里那句 `--download=$it` 会把这条尺子喂饱。
        val script = gradle.readLines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
        assertTrue("构建脚本那一跳漏了（`-Pdownload=…` 会静默丢掉）：", script.contains("providers.gradleProperty(\"download\").orNull"))
        assertTrue("那一跳没拼成进程参数：", script.contains("\"--download=\$it\""))

        assertTrue("集成方没判读：" + host, host.contains("ForensicFlags.download(args.asList())"))
        assertTrue("集成方没把它递进 config：" + host, DesktopSourceTree.codeOccurrences(hostFile, "downloadSeed = downloadSeed,") == 1)

        val paneSrc = DesktopSourceTree.codeText(pane)
        assertTrue("config 没收这一枚：" + paneSrc, paneSrc.contains("val downloadSeed: Pair<String, String>?"))
        assertFalse("config 那枚带了默认值（漏传不报错的形状不许有）：" + paneSrc, Regex("downloadSeed: Pair<String, String>\\?\\s*=").containsMatchIn(paneSrc))
        assertTrue("域 body 没把它递给详情那一屏：" + paneSrc, paneSrc.contains("downloadSeed = config.downloadSeed,"))

        val detailSrc = DesktopSourceTree.codeText(detail)
        assertTrue("详情那一屏没收这一枚：" + detailSrc, detailSrc.contains("downloadSeed: Pair<String, String>?"))
        assertFalse("详情那一屏的形参带了默认值：" + detailSrc, Regex("downloadSeed: Pair<String, String>\\?\\s*=").containsMatchIn(detailSrc))
        // 挂载点那一枚实参必须**命名**：那一屏的形参表今天十枚，位置实参插到中间就会把种子递进别人的格子里，
        // 而编译照过（`Pair?` 与 `() -> File` 那种形状不同才侥幸报错；`onBack`/`onOpenEpisode` 之间全是 lambda）。
        assertEquals("命名实参那一笔不止一处：" + DesktopSourceTree.codeOccurrences(pane, "downloadSeed = config.downloadSeed,"), 1, DesktopSourceTree.codeOccurrences(pane, "downloadSeed = config.downloadSeed,"))
    }

    /** ⑤ 沿路只许有一颗 runner 与一圈泵：长按与种子吃同一条「排进队列」的路。 */
    @Test
    fun 种子吃的是长按那一条路() {
        assertEquals(
            "声明 + 长按 + 种子 = 3：" + occurrencesAcrossMain("enqueueChapterDownload"),
            3, occurrencesAcrossMain("enqueueChapterDownload"),
        )
        assertEquals("调用点不止两处（一处声明两处按）：" + occurrencesAcrossMain("enqueueChapterDownload("), 2, occurrencesAcrossMain("enqueueChapterDownload("))
        val detailSrc = DesktopSourceTree.codeText(detail)
        assertTrue("长按那一笔没排进队列：" + detailSrc, detailSrc.contains("onDownloadEpisode = { index -> enqueueChapterDownload(index) }"))
        assertTrue("种子那一笔没排进队列：" + detailSrc, detailSrc.contains("enqueueChapterDownload(index)"))
        assertTrue("入队那一枚不在挂手上（两处调用点就会各写一份下载）：" + detailSrc, detailSrc.contains("val enqueueChapterDownload: (Int) -> Unit"))
        // 批 101 改判 3 → 2：详情那一屏不再自己算落盘目录（那一跳跟着执行器搬进 `DesktopComicQueueRunner.kt`，
        // 而它用的是队列那一侧的 `desktopComicQueueDir` —— 相对目录名是那份表里的字段，不是当场算的）。
        // 剩下两处 = 那颗算式的声明 + 删除那一颗件复用同一算式反推目录（批 98 改判 2 → 3 的那一格仍在）。
        assertEquals("落盘目录算式的调用点不止一处：" + occurrencesAcrossMain("desktopComicChapterDir("), 2, occurrencesAcrossMain("desktopComicChapterDir("))
        // 一圈泵而**只许一圈**：声明一处 + 域那一层起它一处。两枚就是两个执行器同时写同一颗目录。
        assertEquals("泵那一枚的接线点不止一处：" + occurrencesAcrossMain("desktopComicStartQueuePump("), 2, occurrencesAcrossMain("desktopComicStartQueuePump("))
    }

    /** ⑥ 三档落空各有一句读数，而每一句只许有一个生产者。 */
    @Test
    fun 三档落空各念一句() {
        val other = desktopComicDownloadSeedOtherComicLine("1461316", "9999999")
        assertTrue("没念要的是哪一本：" + other, other.contains("要=1461316"))
        assertTrue("没念开着的是哪一本：" + other, other.contains("开着=9999999"))
        assertTrue("没念落在哪一格：" + other, other.contains("落在=不是这一本"))

        val miss = desktopComicDownloadSeedMissLine("1461316", "1461318", 15)
        assertTrue("没念话号：" + miss, miss.contains("话=1461318"))
        assertTrue("没念目录里有几话：" + miss, miss.contains("目录=15 话"))
        assertTrue("没念落在哪一格：" + miss, miss.contains("落在=话号不在目录里"))

        val hit = desktopComicDownloadSeedLine("1461316", "1461318", 2, 15)
        assertTrue("命中的那一行没念行号（人话从 1 数起，与续读那一行同一口径）：" + hit, hit.contains("落在=第 3 话"))
        assertTrue("命中的那一行没念目录里有几话：" + hit, hit.contains("目录=15 话"))
        assertTrue("三行都得起头在 D_下载种子：" + listOf(other, miss, hit).joinToString(" | "), listOf(other, miss, hit).all { it.startsWith("D_下载种子") })

        // 每一句只许有一个生产者：在别处再手写一句同形的，读数就会有一处指错地方。
        listOf(
            "desktopComicDownloadSeedOtherComicLine(",
            "desktopComicDownloadSeedMissLine(",
            "desktopComicDownloadSeedLine(",
        ).forEach { token ->
            assertEquals("$token 该是声明 + 一处调用：" + occurrencesAcrossMain(token), 2, occurrencesAcrossMain(token))
        }
    }

    /** ⑦ 种子那一笔挂在目录上屏之后，而三条早退路都出声。 */
    @Test
    fun 种子的钥匙与早退路() {
        val detailSrc = DesktopSourceTree.codeText(detail)
        // 钥匙里没有 `state`：目录没回来就跑，那一话还不存在，只会得到一句「话号不在目录里」。
        assertTrue("那颗 effect 的钥匙里没有目录状态：" + detailSrc, detailSrc.contains("LaunchedEffect(state, downloadSeed, target.comicId)"))
        listOf("desktopComicDownloadSeedOtherComicLine(", "desktopComicDownloadSeedMissLine(", "desktopComicDownloadSeedLine(").forEach {
            assertTrue("早退路上有一处没念读数（静默就是把「我没打对」读成「下载坏了」）：$it", detailSrc.contains("println($it"))
        }
        // 上一笔没落地时不许再开一笔（同一枚 `download` 状态被两笔来回写就是两笔在跑）。
        assertTrue("没挡住第二笔种子下载：" + detailSrc, detailSrc.contains("if (queueTaskOfThisEpisode != null) return@LaunchedEffect"))
    }
}
