package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertEquals
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
