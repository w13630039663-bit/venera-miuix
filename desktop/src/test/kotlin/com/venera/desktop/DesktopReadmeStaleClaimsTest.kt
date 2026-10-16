package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 84′ · 对外文档那两句假话的尺子（2026-10-10）。
 *
 * ## 为什么把手册口径的尺子伸进 README
 *
 * `README.md:256` 与 `README.en.md:256` 写着「桌面 classpath 上没有 gif 解码器，所以那一档在桌面上
 * **不解动**」。前半句到今天仍然真（`coil-gif` 确实没有 `-jvm` 变体），后半句**是假的**：批 51 起大图
 * 那一屏自己拿 `org.jetbrains.skia.Codec` 逐帧解（`DesktopAnimatedImage.kt:58`），会动。
 * 这正是本仓记过一整族的那件事 —— 「文案说还没有 X，而 X 早落地」—— 只不过宿主换成了**对外文档**，
 * 而对外文档比屏上那句更没人回查：屏上的话有 `DesktopStaleClaimsTest` 一族盯着，README 一把尺子都没有。
 *
 * ## 每条禁串都配一条"为什么它已经不成立"的正面证据
 *
 * 与 `DesktopStaleClaimsTest` 同一套配法：禁一句措辞等于把后来的代码钉死在今天的形状上，
 * 所以对照钉的是**能力真在**（逐帧解那颗件还在、还被大图那一屏接着）。哪天动图被撤了，
 * 对照会先红，读的人立刻知道那句该回来，而不是被要求改文档。
 *
 * ## 中英两份必须一起改
 *
 * 本仓的文档口径是「中文主文档 + 全量对照 `README.en.md`，改一处必须同步另一处」，而这句话从前
 * **只靠人记**。这里不比对措辞（两份语言不同，没法比），比的是同一节里**条目的枚数** ——
 * 漏同步的形状正是"加了一条、另一份没加"。
 */
class DesktopReadmeStaleClaimsTest {

    private val zh = File(DesktopSourceTree.repoRoot, "README.md")
    private val en = File(DesktopSourceTree.repoRoot, "README.en.md")

    private fun linesOf(file: File): List<String> {
        assertTrue("读不到对外文档：${file.path}", file.isFile)
        return file.readText().lines()
    }

    /** 那一节里的条目：标题后空一行，再连着数 `- `，数到第一个非条目行为止。 */
    private fun bulletsAfter(lines: List<String>, header: String): List<String> {
        val start = lines.indexOfFirst { it.trim().startsWith(header) }
        check(start >= 0) { "README 里找不到那一节标题：$header（这一节搬家了就先改这把尺子的对象）" }
        return lines.drop(start + 1).dropWhile { it.isBlank() }.takeWhile { it.startsWith("- ") }
    }

    /** ① 动图那一格：假话整树不许出现，且那一条必须点名今天真的那一半。 */
    @Test
    fun 动图那一格不再把会动的说成不解动() {
        val zhLines = linesOf(zh)
        val enLines = linesOf(en)
        assertEquals("README.md 里还写着「不解动」", 0, zhLines.count { it.contains("不解动") })
        assertEquals("README.en.md 里还写着那句 static", 0, enLines.count { it.contains("so that tier stays static on desktop") })

        val bullet = zhLines.firstOrNull { it.startsWith("- **动图") }
            ?: error("README.md 里那一条动图说明不见了（那句若重新成立，要连本条判据一起撤）")
        assertTrue("改完只删不说了：那一条没点名还停在首帧的是哪两处 —— $bullet", bullet.contains("墙上与浮层"))
        assertTrue("那一条没念出今天的行为是哪一帧 —— $bullet", bullet.contains("第一帧"))

        val enBullet = enLines.firstOrNull { it.startsWith("- **Animated images") }
            ?: error("README.en.md 里那一条动图说明不见了")
        assertTrue("英文那份只删不说了 —— $enBullet", enBullet.contains("wall") && enBullet.contains("overlay"))
        assertTrue("英文那份没念出是哪一帧 —— $enBullet", enBullet.contains("first frame"))

        // 对照（证据件）：这句被禁**是因为大图那一屏真在逐帧解**。走 codeText —— 坑 ㉑：
        // 名字留在类注释里也算"看得见"，只有代码行里真调了才算能力在。
        val animated = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopAnimatedImage.kt"))
        assertTrue(
            "对照组失效：那一屏不再走 Skia `Codec` 逐帧解（那 README 那句反而该回来）",
            animated.contains("Codec.makeFromData"),
        )
        val viewer = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt"))
        assertTrue("对照组失效：逐帧解那颗件没接到查看器上", viewer.contains("DesktopAnimatedImage("))
    }

    /** ③ 「网页登录」那一格：不许把缺的东西说成"只差一颗 WebView 内核"，也不许把已落地的两半还说成缺。 */
    @Test
    fun 网页登录那一格点名的是动线而不是内核() {
        val zhLines = linesOf(zh)
        val enLines = linesOf(en)
        // 假话的形状 A：把结构性缺口归给"没引入 JCEF"。真缺口是**动线**（对岸抓的是内嵌 WebView 自己的罐）。
        assertEquals("README.md 还写着那句「WebView 登录要 JCEF」", 0, zhLines.count { it.contains("JCEF") })
        assertEquals("README.en.md 还写着那句 needs JCEF", 0, enLines.count { it.contains("needs JCEF") })

        val bullet = zhLines.firstOrNull { it.startsWith("- **网页登录") }
            ?: error("README.md 里那一条登录说明不见了（若那句重新成立，要连本条判据一起撤）")
        val enBullet = enLines.firstOrNull { it.startsWith("- **Web login") }
            ?: error("README.en.md 里那一条登录说明不见了")
        assertTrue("那一条没念出结构性缺的是哪一形 —— $bullet", bullet.contains("内嵌 WebView"))
        assertTrue("英文那份没念出是哪一形 —— $enBullet", enBullet.contains("embedded WebView"))
        // 假话的形状 B（批 88·下半 之后才有的那一半）：罐已经落盘、入口已经上屏，
        // 那两句"只在内存里 / 重启就丢 / 缺一个粘贴入口"从这里开始是**反的**。
        // 坑 ㉖ 那一族的正解：钉的是**旧那句假话不许回流**，不是钉现在这句怎么写 ——
        // 句子以后还会改，而那三句假话不管怎么写都还是假的。
        for (banned in listOf("重启就丢", "内存里的罐", "缺的是**手工粘贴")) {
            assertEquals("README.md 那句假话回来了（$banned）", 0, bullet.split(banned).size - 1)
        }
        for (banned in listOf("lost on restart", "in-memory jar", "an entry to paste")) {
            assertEquals("README.en.md 那句假话回来了（$banned）", 0, enBullet.split(banned).size - 1)
        }
        // 对照（正面证据）：那条说要给的这一形**真在屏上**，不是又一句"接缝在"。
        val screen = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopUiSource("DesktopComicSourcesScreen.kt"),
        )
        assertTrue("对照失效：源管理那一屏没有粘贴凭据那一格了（README 那句要改回'还没有'）",
            screen.contains("DesktopCookieLogin.payload("))
        assertTrue("对照失效：那一屏不再念出是哪一形桌面没有宿主", screen.contains("desktopWebLoginAbsentLine"))

        // 能力那半 —— 那颗罐挂在客户端上（"重启也带得上"这句的前提）。
        val probe = DesktopSourceTree.engineProbeSource("DesktopJsHost.kt")
        val code = DesktopSourceTree.codeText(probe)
        assertTrue("对照组失效：那颗罐没再挂在客户端上（那 README 那句该换成更硬的'带不上请求'）", code.contains(".cookieJar("))
    }

    /** ④ 「漫画章节下载」那一格：整话落盘已接（批 96），那两句"还没接"不许回流。 */
    @Test
    fun 整话落盘那一格不把已接的说成还没接() {
        val zhLines = linesOf(zh)
        val enLines = linesOf(en)
        // 假话只禁那一句措辞的形状（坑 ㉖：钉"旧那句不许回来"，不钉今天这句怎么写）。
        val bullet = zhLines.firstOrNull { it.startsWith("- **漫画章节下载") }
            ?: error("README.md 里那一条章节下载说明不见了")
        val enBullet = enLines.firstOrNull { it.startsWith("- **Comic chapter downloads") }
            ?: error("README.en.md 里那一条章节下载说明不见了")
        assertEquals("README.md 那句「还没接的是漫画那一侧」回来了", 0, bullet.split("还没接的是漫画那一侧").size - 1)
        assertEquals("README.en.md 那句 \"download a whole episode\" 回来了", 0, enBullet.split("\"download a whole episode\"").size - 1)
        // 那一条必须点名**还缺的那一层**（只说"接上了"而不说缺什么 = 另一种假话）：队列那一屏今天确实没有。
        assertTrue("那一条没点名还缺的是哪一层 —— $bullet", bullet.contains("下载队列"))
        assertTrue("英文那份没点名还缺的那一层 —— $enBullet", enBullet.contains("download queue screen"))

        // 对照（正面证据）：那句被禁**是因为整话落盘真接上了**，而接上的证据是消费点而不是声明。
        // ⚠️ 2026-10-13 搬家预备（批 104）：与 `DesktopStaleClaimsTest` 同一格同源的那把一起改指**跨全 main 数次数** ——
        //    批 101 抽共用件时调用点会搬家，这一把不必再动，而"恰一处消费点、不许第二条落盘路"一字未减。
        val runnerCalls = DesktopSourceTree.desktopMainSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "desktopComicDownloadChapter(dir,") }
        assertEquals(
            "对照组失效：全 main 里那颗 runner 的调用不是恰一处（零处=README 那句该换回'还没接'并连本条判据一起撤；两处=第二条落盘路）：" + runnerCalls,
            1,
            runnerCalls,
        )
    }

    /** ⑤ 同一格的后半句：批 97 接上了「本机已下载」那一屏，那两句"屏上还数不着"不许回流。 */
    @Test
    fun 已下载的那些不再被说成屏上数不着() {
        val zhLines = linesOf(zh)
        val enLines = linesOf(en)
        assertEquals("README.md 那句「任何一屏都数不着」回来了", 0, zhLines.count { it.contains("任何一屏都数不着") })
        assertEquals("README.en.md 那句 not listed anywhere 回来了", 0, enLines.count { it.contains("not listed anywhere on screen today") })

        val bullet = zhLines.firstOrNull { it.startsWith("- **漫画章节下载") }
            ?: error("README.md 里那一条章节下载说明不见了")
        val enBullet = enLines.firstOrNull { it.startsWith("- **Comic chapter downloads") }
            ?: error("README.en.md 里那一条章节下载说明不见了")
        // 只删不说也是一种假话：那一条必须念出今天那一屏叫什么、说了什么。
        assertTrue("那一条没念出已下的那些今天在哪一屏数得着 —— $bullet", bullet.contains("本机已下载"))
        assertTrue("那一条没念出那一屏说得出什么 —— $bullet", bullet.contains("缺 N 页"))
        assertTrue("英文那份没念出是哪一屏 —— $enBullet", enBullet.contains("are listed on screen now"))
        assertTrue("英文那份没念出那一屏说得出什么 —— $enBullet", enBullet.contains("缺 N 页"))
        // 第九十八批改判（这两行从前钉的是反面）：那一屏**删得掉**了，两份都得念出这一格，
        // 而"还删不掉"那串一旦回到这一条里就是假话 —— 机械地禁在同一条子弹里。
        assertTrue("那一条没念出那一屏删得掉 —— $bullet", bullet.contains("删得掉"))
        assertTrue("英文那份没念出那一屏删得掉 —— $enBullet", enBullet.contains("deleted from the app"))
        assertFalse("那一条还在说删不掉（批 98 之后那是假话）—— $bullet", bullet.contains("删不掉"))
        assertFalse("英文那份还在说 cannot be deleted —— $enBullet", enBullet.contains("cannot be deleted"))

        // 对照（正面证据）：这两句被禁**是因为那一屏真在读盘**，走 codeText —— 坑 ㉑：声明在不算。
        val pane = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopUiSource("DesktopComicDownloadsPane.kt"),
        )
        assertTrue(
            "对照失效：那一屏不再读盘上那一棵树（README 那句要改回'还数不着'，并连本条判据一起撤）",
            pane.contains("desktopComicDownloadedChapters("),
        )
        // 对照二（批 98）：那句「删得掉」的正证是那颗 runner 真在屏上被按到，而不是声明在。
        assertTrue(
            "对照失效：那一屏不再调那颗删除 runner（README 那句要改回'还删不掉'，并连本条判据一起撤）",
            pane.contains("desktopComicDeleteChapter("),
        )
    }

    /** ⑥ 同一格的第三半句：批 99 起卡上有那一枚标记，那两句"行内标记还没接"不许回流。 */
    @Test
    fun 卡上那枚标记不再被说成没接() {
        val zhLines = linesOf(zh)
        val enLines = linesOf(en)
        val bullet = zhLines.firstOrNull { it.startsWith("- **漫画章节下载") }
            ?: error("README.md 里那一条章节下载说明不见了")
        val enBullet = enLines.firstOrNull { it.startsWith("- **Comic chapter downloads") }
            ?: error("README.en.md 里那一条章节下载说明不见了")
        // 禁的是旧那句假话的形状（坑 ㉖：不钉今天这句怎么写）。
        assertEquals("README.md 那句「离线不全」的行内标记还挂在还没接那半句里", 0, bullet.split("离线不全」的行内标记").size - 1)
        assertEquals("README.en.md 那句 two in-row markers 还挂在 missing 里",
            0, enBullet.split("the other side's two in-row markers").size - 1)
        // 只删不说也是假话：那一条要念出卡上那一枚今天说得出哪两档。
        assertTrue("那一条没念出卡上那一枚今天说什么 —— $bullet", bullet.contains("已下载"))
        assertTrue("那一条没点名那一枚挂在**卡**上 —— $bullet", bullet.contains("卡上"))
        assertTrue("英文那份没念出那一枚挂在卡上 —— $enBullet", enBullet.contains("on the card"))

        // 对照（正面证据）：那句被禁**是因为那一枚真摆在卡上**，走 codeText —— 坑 ㉑：声明在不算，要屏上调到。
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicPane.kt"))
        assertTrue(
            "对照失效：卡上不再摆那一枚（README 那句要改回'还没接'，并连本条判据一起撤）",
            pane.contains("ComicOfflineMark("),
        )
    }

    /** ⑦ 第一百批：那一趟下载现在念得出"写到第几页"也停得下来，那两句"还没接进度与取消"不许回流。 */
    @Test
    fun 逐页进度与取消不再被说成没接() {
        val zhLines = linesOf(zh)
        val enLines = linesOf(en)
        val bullet = zhLines.firstOrNull { it.startsWith("- **漫画章节下载") }
            ?: error("README.md 里那一条章节下载说明不见了（若那一格被撤了，要连本条判据一起撤）")
        val enBullet = enLines.firstOrNull { it.startsWith("- **Comic chapter downloads") }
            ?: error("README.en.md 里那一条章节下载说明不见了")
        // 禁的是旧那半句的形状（坑 ㉖：不钉今天这句怎么写）—— 它把已经接上的两格还挂在"还没接"里。
        assertEquals("README.md 那半句「以及**逐页进度与取消**」回来了", 0, bullet.split("以及**逐页进度与取消**").size - 1)
        assertEquals("README.en.md 那半句 \"and **per-page progress with cancel**\" 回来了",
            0, enBullet.split("and **per-page progress with cancel**").size - 1)
        // 只删不说也是假话：那一条要念得出屏上那两句今天的人话。
        assertTrue("那一条没念出进度那一档长什么样 —— $bullet", bullet.contains("正在写本机"))
        assertTrue("那一条没念出停手之后要说「下次接着补」 —— $bullet", bullet.contains("下次从这里接着补"))
        assertTrue("那一条没点名那枚把手叫什么 —— $bullet", bullet.contains("「取消」"))
        assertTrue("英文那份没念出进度那一档 —— $enBullet", enBullet.contains("in progress"))
        assertTrue("英文那份没念出按下去会改口 —— $enBullet", enBullet.contains("正在停手"))

        // 对照（正面证据）：那两句被禁**是因为进度与取消真接上了**，走 codeText —— 坑 ㉑：声明在不算，要屏上调到。
        // ⚠️ 批 101 搬家：那两句读数跟着执行器从详情那一屏搬进 `DesktopComicQueueRunner.kt` ⇒ 证据改指**全 main**
        //    （"消费点而不只是声明在"那一层含义一字未减：`codeText` 跳注释，而两枚都是调用形状）。
        val mainCodes = DesktopSourceTree.desktopMainSources().joinToString("\n") { DesktopSourceTree.codeText(it) }
        val detail = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicDetail.kt"))
        assertTrue("对照失效：全 main 不再念逐页进度（README 那句要改回'还没接'，并连本条判据一起撤）",
            mainCodes.contains("desktopComicChapterProgressLine("))
        assertTrue("对照失效：全 main 不再调停手那一句", mainCodes.contains("desktopComicChapterCancelledLine("))
        assertTrue("对照失效：那枚把手没接到队列那颗闸上（摆着而按不动那一族）",
            detail.contains("onCancelDownload = stopActiveTask"))
    }

    /** ② 中英两份「桌面今天缺的那几格」条目枚数必须一致。 */
    @Test
    fun 缺的那几格两份枚数对得上() {
        val zhBullets = bulletsAfter(linesOf(zh), "**桌面端（`:desktop`）今天缺的那几格**")
        val enBullets = bulletsAfter(linesOf(en), "**What the desktop (`:desktop`) still lacks**")
        assertTrue("那一节是空的（枚数比对没有对象）", zhBullets.isNotEmpty())
        assertEquals(
            "两份只改了一份：中文 ${zhBullets.size} 条 / 英文 ${enBullets.size} 条" +
                "（本仓口径：改一处必须同步另一处）",
            zhBullets.size,
            enBullets.size,
        )
    }
}
