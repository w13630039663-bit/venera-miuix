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
    /** ③ 「网页登录」那一格：不许把缺的东西说成"只差一颗 WebView 内核"，也不许把已落地的两半还说成缺。 */
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
    /** ⑥ 同一格的第三半句：批 99 起卡上有那一枚标记，那两句"行内标记还没接"不许回流。 */
    /** ⑦ 第一百批：那一趟下载现在念得出"写到第几页"也停得下来，那两句"还没接进度与取消"不许回流。 */
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
