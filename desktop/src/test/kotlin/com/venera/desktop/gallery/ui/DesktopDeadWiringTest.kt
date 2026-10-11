package com.venera.desktop.gallery.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第二十六批 批 26 · **桌面自有件不许留"实现了而没人调"的那一格**。
 *
 * ## 为什么这一族要单独立一颗类
 *
 * 手册 §22 记过一整轮「实现了但零调用点」，而它的**镜像**（文案说没有而东西在）已经由
 * `DesktopStaleClaimsTest` 管着。这一颗管的是正向那一半，而三格都是 批 25 收尾时
 * 逐枚 grep 复核出来的（不是子代理报什么就记什么）：
 *
 * - `desktopFaceProbeNotice`：类注释写着"失败那句**只进日志**"，而**没有任何一处把它递给日志** ——
 *   那是"说明与接线不符"，比"没做"更坏（读注释的人会以为它在工作）；本批**接上**。
 * - `DesktopDomain.isInTopGroup`：零调用点，唯一"提及"是另一颗类注释里的 `[链接]` ⇒ **删**（连带那句注释）。
 * - `saveSauceNaoKey` / `clearSauceNaoKey`：成对死（后者唯一调用方是前者），而桌面今天没有
 *   以图反搜的入口（那一路整族不做）⇒ **删**。留着一枚"能写而没人读的开关"就是假开关的近亲。
 *
 * ## 判式口径
 *
 * 数"调用点"一律只看**代码行**（`DesktopSourceTree.codeLinesOf` 跳注释），否则注释里提一次名字
 * 就算"有人在用"；而"这颗件存在不存在"扫全文（含注释），因为**说明也算一处引用**。
 */
class DesktopDeadWiringTest {

    private fun desktopMainText(): List<Pair<String, String>> =
        DesktopSourceTree.desktopMainSources().map { it.name to it.readText() }

    /**
     * 某个名字在桌面 main 的**代码行**里出现几次（声明行自己算一次）。
     *
     * ⚠️ 传进来的名字必须是**拼出来的**（`"isInTop" + "Group"`）：这颗类自己的源码里若写着那个字面量，
     * 判式就会扫到自己 ⇒ 恒红。这是"扫描尺子把被扫的语料含进自己"那一族的第三种形态。
     */
    private fun codeUses(name: String): List<String> =
        DesktopSourceTree.desktopMainSources().flatMap { file ->
            DesktopSourceTree.codeLinesOf(file).filter { it.contains(name) }.map { "${file.name}: ${it.trim().take(90)}" }
        }

    /** ① 探针那句**真被递给日志**（它自己的注释就是这么承诺的）。 */
    @Test
    fun 探针那句真进日志() {
        val page = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistsPage.kt"))
        val calls = page.lines().count { it.contains("desktopFaceProbeNotice(") }
        assertEquals("那一屏没吃探针那句（或吃了两遍）：$calls", 1, calls)
        assertTrue(
            "递出去那一句没走带去重计数的日志件（一屏几十位，逐位打就是刷屏）",
            page.contains("DesktopGalleryFailureLog.warn("),
        )
        // 对照：那句本身仍然三态可跑（撤了那颗件，上面两条会一起红，而不是恒真）
        assertEquals("Found 那档必须不吭声", "", desktopFaceProbeNotice(DesktopFaceProbe.Found("u")))
        assertTrue("失败那句必须带成因：" + desktopFaceProbeNotice(DesktopFaceProbe.Failed("timeout")),
            desktopFaceProbeNotice(DesktopFaceProbe.Failed("timeout")).contains("timeout"))
    }

    /** ② 域分组那枚死件下架：桌面 main 与 test 里一处都不许留（注释里的 `[链接]` 也算一处）。 */
    @Test
    fun 域分组那枚死件下架() {
        val dead = "isInTop" + "Group"
        val main = desktopMainText().flatMap { (name, text) -> text.lines().filter { it.contains(dead) }.map { "$name: ${it.trim()}" } }
        // 测试源只看**代码行**：这一颗类的注释里要写"撤的是哪一枚"，扫全文就会自咬（手册 §0 坑 ㉑ 的近亲）。
        val tests = File(DesktopSourceTree.repoRoot, "desktop/src/test").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { f -> DesktopSourceTree.codeLinesOf(f).filter { it.contains(dead) }.map { "${f.name}: ${it.trim()}" } }
            .toList()
        assertEquals("还留着那枚零调用点的域分组判据（main）：" + (main + tests).joinToString(" ; "), 0, (main + tests).size)
        // 对照：rail 上那枚间隔条仍由**另一枚**在用的判据决定（撤的不是这件事本身）。
        val rail = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryRail.kt"))
        assertTrue("对照组失效：rail 不再吃任何域级判据（那上面那句该重看）", rail.contains("precedesSpacer"))
    }

    /** ③ SauceNao 的写侧成对撤掉，而读侧那一路仍然在（"能写"这件事今天没有入口）。 */
    @Test
    fun 凭据写侧不留死口() {
        listOf("saveSauceNao" + "Key", "clearSauceNao" + "Key").forEach { dead ->
            assertEquals("还留着零调用点的写口「$dead」：" + codeUses(dead).joinToString(" ; "), 0, codeUses(dead).size)
        }
        // 对照：读侧还在（key 从哪儿来是另一件事，屏上不许有人说"填了就能反搜"）。
        assertTrue("对照组失效：读侧那枚「有没有 key」的读数没了", codeUses("sauceNaoHasKey").isNotEmpty())
        val creds = DesktopSourceTree.codeText(
            File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopGalleryCredentials.kt"),
        )
        assertTrue("对照组失效：那颗键值件与 key 的读法没了", creds.contains("sauceNaoStore.getString("))
    }
}
