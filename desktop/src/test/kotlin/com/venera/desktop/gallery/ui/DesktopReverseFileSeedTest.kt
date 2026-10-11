package com.venera.desktop.gallery.ui

import com.venera.desktop.ForensicFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 桌面第七十七批 批 77 · 下半 · `--reverse-file=` 那颗取证种子的接线链。
 *
 * ## 它补的是哪一格
 *
 * 「挑一张本机的图」那一步要弹系统文件框，而鼠标自验是禁的（手册 §8）⇒ 没有这枚参数时，
 * 路径之后的三跳（读那张文件 → 发车 → 结果那一格）结构性地拍不到，与批 36 的
 * `--reverse`、批 19 的单人页、批 29 的期次按钮是同一种缺口，补法也一样。
 *
 * ## 它与批 36 那枚种子有一处**实差**，各处都要点名
 *
 * `--reverse` 是"自动按一次按钮"，那颗按钮本来就在屏上；这一枚是"替用户跳过文件框"，
 * 而文件框那一跳**今天仍然没有读数**（它能坏在"根本没弹出来"这一档，而那一档只有人手点得出来）。
 * 所以 ⑤ 钉的是"种子那条路不许去开文件框"，而 rounds 文档与类注释都不许写成"挑图那一步验过了"。
 */
class DesktopReverseFileSeedTest {

    /** ① 三跳一处不缺：构建脚本 → 集成方（读 + 出声 + 递进应用根）→ 消费点。 */
    @Test
    fun 三跳一处不缺() {
        val gradle = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertTrue("`app` 任务没读 gradleProperty(\"reverseFile\")", gradle.contains("""gradleProperty("reverseFile")"""))
        assertTrue("那一枚没被拼进 args（值传不下去，判读层再对也接不到）", gradle.contains("\"--reverse-file=\$it\""))
        assertTrue("对照组失效：这份构建脚本里没有 :desktop:app 那颗粒任务", gradle.contains("""tasks.register<JavaExec>("app")"""))

        val main = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
        assertTrue("集成方没读 --reverse-file", main.contains("""ForensicFlags.reverseFile(args.asList())"""))
        assertTrue("集成方没让那一枚落空时出声", main.contains("ForensicFlags.reverseFileNote("))
        assertTrue(
            "集成方没把它递进应用根（整串都得钉：只数前缀的话恒传 null 也绿 —— 坑 ㉔）",
            main.contains("startReverseFile = reverseFile"),
        )
        assertTrue("对照组失效：`--reverse` 那一跳不在这颗文件里了", main.contains("startReverse = reverse == true"))

        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue("应用根没收它", app.contains("startReverseFile: String?"))
        assertTrue("应用根没把它递给那一片 body", app.contains("seedLocalReverse = startReverseFile"))
        assertTrue("那一格没从构造点吃到它（覆盖层读不到 provider，批 36 那一课）",
            app.contains("DesktopGallerySearchBody(row, ports, searchSeed, seedLocalReverse)"))
        assertTrue("对照组失效：`--reverse` 那一跳不在应用根里了", app.contains("seedReverse = startReverse"))
    }

    /** ② 中间两跳不许断：域 body 与搜索那一屏各把这一枚往下递一次。 */
    @Test
    fun 中间两跳各递一次() {
        val panes = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDomainPanes.kt"))
        assertTrue("域 body 没收这一枚", panes.contains("seedLocalReverse: String?"))
        assertTrue("域 body 没递给搜索那一屏", panes.contains("DesktopGallerySearchPane(ports, searchSeed, seedLocalReverse)"))
        val paneFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGallerySearchPane.kt")
        val pane = DesktopSourceTree.codeText(paneFile)
        assertTrue("搜索那一屏没收这一枚", pane.contains("seedLocalReverse: String?"))
        assertEquals("入口那一行吃到的种子不止一处", 1, DesktopSourceTree.codeOccurrences(paneFile, "seedPath = seedLocalReverse"))
    }

    /** ③ 种子那条路**不许去开文件框**（开了就是无人值守时挂死在模态框上）。 */
    @Test
    fun 种子不去开文件框() {
        val section = DesktopSourceTree.desktopUiSource("DesktopGalleryLocalReverseSection.kt")
        val body = DesktopSourceTree.codeText(section)
        assertTrue("种子没落到那枚动作上", body.contains("fireLocalReverse(seedPath)"))
        val seedLine = DesktopSourceTree.codeLinesOf(section).first { it.contains("fireLocalReverse(seedPath)") }
        assertTrue("种子那一格自己去开文件框了：" + seedLine, !seedLine.contains("desktopPickLocalImage("))
        assertEquals("文件框那一枚只许按钮走得到", 1, DesktopSourceTree.codeOccurrences(section, "desktopPickLocalImage("))
        // 对照组：那颗文件真在被扫（读空会让上面三条恒真）。
        assertTrue("对照组失效：那颗文件里根本没有那枚入口的名字", body.contains("DESKTOP_LOCAL_REVERSE_ENTRY_LABEL"))
    }

    /** ④ 那枚种子走**必填参数**，不许又做成覆盖层读得到的局部槽（同一颗坑第三次的那一条）。 */
    @Test
    fun 那颗种子走必填参数() {
        val section = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryLocalReverseSection.kt"))
        assertTrue("种子没做成必填参数（漏传不报错的形状不许有）", section.contains("seedPath: String?"))
        assertEquals(
            "为这一族新造了一枚局部槽 —— 覆盖层永远读到默认值",
            0,
            DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopGalleryLocalReverseSection.kt"), "compositionLocalOf"),
        )
        // 对照组：既有的那两枚局部槽仍在原处（读空了会让上面第二条恒真）。
        assertTrue(
            "对照组失效：那颗文件里根本没有形参这一族（读错文件了）",
            section.contains("ports: GalleryPorts"),
        )
    }

    /** ⑤ 判读层那颗纯函数：没给 / 给了空串 / 给了路径而域不对，三档各一句。 */
    @Test
    fun 判读三档各归各() {
        assertNull("没给参数时不该有路径", ForensicFlags.reverseFile(emptyList()))
        assertNull("给了空串不等于一条路径", ForensicFlags.reverseFile(listOf("--reverse-file=")))
        assertEquals("给了路径没原样交", "C:/图/一张.png", ForensicFlags.reverseFile(listOf("--reverse-file=C:/图/一张.png")))

        // 给了空串：那一句必须念出来（静默 = 假开关的口径）。
        val blank = ForensicFlags.reverseFileNote(raw = "--reverse-file=", resolved = null, domain = DesktopDomain.SEARCH)
        assertTrue("空串那一句没说出本轮不挑图：" + blank, blank != null && blank.contains("没有那一串路径"))
        // 没给参数：一句都不许多念。
        assertNull("没给参数时不该念空串那句", ForensicFlags.reverseFileNote(raw = null, resolved = null, domain = DesktopDomain.SEARCH))
        // 给了路径而起始域不是搜索那一屏：那一格根本不在组合里，必须出声。
        val wrong = ForensicFlags.reverseFileNote(raw = "--reverse-file=x", resolved = "x", domain = DesktopDomain.GALLERY)
        assertTrue("落不到那一格时没出声：" + wrong, wrong != null && wrong.contains("搜索"))
        assertTrue("那句没给出可照做的下一句：" + wrong, wrong != null && wrong.contains("--start-domain"))
        assertNull("落在搜索那一域时不该念那句", ForensicFlags.reverseFileNote(raw = "--reverse-file=x", resolved = "x", domain = DesktopDomain.SEARCH))
        // 登记：漏登记时 `unknown` 会把打进去的参数报成拼错（读数自相矛盾那一族）。
        assertTrue("--reverse-file 没登记进 KNOWN", ForensicFlags.KNOWN.any { it == "--reverse-file" })
    }
}
