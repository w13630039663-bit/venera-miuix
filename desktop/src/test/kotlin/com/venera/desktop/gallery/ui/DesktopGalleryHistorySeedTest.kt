package com.venera.desktop.gallery.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 86 · 图库浏览历史那条链的接线判据。
 *
 * 这一颗不管业务口径（那颗在 `DesktopGalleryBrowseHistoryTest`），只管**屏上每句断言背后那一层真在**：
 * 注册表那一行转正、写口挂在真正记那一笔的那颗文件上、集成两侧都有形参、派发只有一处。
 *
 * ## 为什么每颗尺子都数"出现次数"而不是问"存不存在"
 *
 * 接线类判据红过的那一族是：假动作存在（排完立刻 dispose、参数递了但没人读），存在性判式全绿。
 * 数次数至少管住两件事 —— 该在的一处不少，不该多的地方不多（同一句话写第二份就会漂）。
 */
class DesktopGalleryHistorySeedTest {

    private val sections = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGalleryHomeSections.kt")
    private val panel = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGalleryDetailPanel.kt")
    private val pane = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGalleryBrowseHistoryPane.kt")
    private val app = DesktopSourceTree.desktopSourceOf("gallery/ui/VeneraDesktopApp.kt")
    private val entry = DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt")
    private val saver = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryBrowseHistory.kt")

    private fun text(file: File) = DesktopSourceTree.codeText(file)

    // ① 首页那一行**转正**：带上了内容件，reason 撤下来（那一行不再是"未实现"形状）。
    @Test
    fun 历史那一行已经点亮() {
        val row = desktopGalleryHomeRows().first { it.key == DesktopGalleryHomeSectionKey.HISTORY }
        assertEquals("那一行要拿到内容件", DesktopGallerySectionContent.BrowseHistory, row.content)
        assertNull("点亮了就不许再带缺席理由", row.reason)
        assertTrue("标题还是那两个字", row.title == "历史")
    }

    // ② 六行今天**全都有**内容件。谁新加一行不带内容件，这条会红 —— 那是本仓唯一的"未实现"豁免位，
    //    加一行要先想清楚（手册 §0 记着这条定点豁免的由来）。
    @Test
    fun 首页那六行今天全有内容件() {
        val absent = desktopGalleryHomeRows().filter { it.content == null }
        assertEquals(
            "首页又长出未实现的行了：${absent.map { it.title }} —— 要么把那一屏补上，要么当场写明为什么留空",
            0,
            absent.size,
        )
    }

    // ③ 那句假话不许活下来：界面上再出现「浏览历史还没存过」就是那一族的第七次（东西已在而文案说没有）。
    //     扫的是 `gallery/ui/` **整目录**而不是文件名单（名单会红在拼写上、盖不住漏列的文件）。
    // ④ 那一屏只是去看一眼：**不许建目录、不许拿到能建目录的手**。
    @Test
    fun 那一屏不建目录() {
        assertEquals("那一屏里出现了 mkdirs：看一眼不许在盘上多出目录", 0, DesktopSourceTree.codeOccurrences(pane, "mkdirs"))
        assertEquals("那一屏拿到了 PathProvider（那等于递进来一条能建目录的手）", 0, DesktopSourceTree.codeOccurrences(pane, "PathProvider"))
    }

    // ⑤ 写口挂在**真正记那一笔的那颗文件**上（浮层），而不是挂在发起方那一句参数上。
    // ⑥ 挡住的那一档**不记**：用户没看见画，记一条"看过"就是假历史。
    // ⑦ 浮层、那一屏与派发那一层拿到的历史件都是**必填、不给默认值**：
    //    漏传的形状是"链子挂上了但永远不落盘"，而它不报错（本仓量过三次的那一条）。
    //    应用根那一枚叫 `galleryHistory`（与 `gallerySaveDir` 同一个前缀族），单独钉。
    // ⑧ 集成两侧都要有读数：发起方建那颗、收下方接那枚。**两侧各钉一处**（批 85 那一课：
    //    锚点曾钉在发起方，而真正做那一步的是收下方）。
    // ⑨ 派发只有一处认得那一屏，其余四颗域与设置域各自补穷尽分支：总次数钉死。
    // ⑩ 屏上那两句读数真的被那一屏用着；stdout 那句挂在**写下那一笔的那一颗**里。
    // ⑪ 两屏念时刻要同一个形状：这一串在桌面主源里恰好两处（批 85 那一屏与本批那一屏）。
    //     第三处要先想清楚是不是又在造一枚新口径（手册「观感尺寸用现成口径」那条）。
    @Test
    fun 两屏的时刻格式同串() {
        val hits = DesktopSourceTree.desktopMainSources().flatMap { file ->
            DesktopSourceTree.codeLinesOf(file).filter { it.contains("yyyy-MM-dd HH:mm") }
        }
        assertEquals("时刻格式串的枚数变了：${hits.map { it.take(40) }}", 2, hits.size)
    }

    // ⑫ 归档那一格**不买库**：桌面构建脚本与版本目录里不许出现 zip4j / commons-compress。
    //     现量与理由在 rounds 文档 §1.1（对岸两处已经在用 `java.util.zip`，交付包镜像里 43 条 zip 类）。
    @Test
    fun 没为归档引第三方库() {
        val build = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        val catalog = File(DesktopSourceTree.repoRoot, "gradle/libs.versions.toml").readText()
        listOf("zip4j", "commons-compress").forEach { needle ->
            assertEquals("构建脚本里出现了 $needle", 0, build.lines().count { !it.trim().startsWith("//") && it.contains(needle) })
            assertEquals("版本目录里出现了 $needle", 0, catalog.lines().count { !it.trim().startsWith("#") && it.contains(needle) })
        }
        // 对照组：这两份文件真的被读到了（否则上面那些禁串是恒真的空判式）。
        assertTrue("对照组失效：构建脚本没读到", build.contains("providers.gradleProperty(\"save\")"))
        assertTrue("对照组失效：版本目录没读到", catalog.contains("coil3"))
    }

    // ⑬ 落点那一颗只许一处算它：声明 + 那颗件自己用它建实例，两处之外再算一遍就是两份账。
    @Test
    fun 落点那颗只有一处算它() {
        val total = DesktopSourceTree.desktopMainSources()
            .sumOf { DesktopSourceTree.codeOccurrences(it, "desktopBrowseHistoryFile(") }
        assertEquals(
            "落点的出处变了（声明一处 + getInstance 一处 = 2）：实际 $total 处",
            2,
            total,
        )
    }

    // ⑭ 两份 README 那一格同轨：旧那句不许留，新那句两边都得有（改一处必须同步另一处，本仓口径）。
    @Test
    fun 两份README的那一条同轨() {
        val zh = File(DesktopSourceTree.repoRoot, "README.md").readText()
        val en = File(DesktopSourceTree.repoRoot, "README.en.md").readText()
        assertFalse("中文那份还写着旧那句", zh.contains("那一行仍未点亮"))
        assertFalse("英文那份还写着旧那句", en.contains("row is still not lit up"))
        assertTrue("中文那份没写点开就记一行", zh.contains("点开一张卡就在详情浮层里记一行"))
        assertTrue("英文那份没写点开就记一行", en.contains("records one row from the detail overlay"))
        assertTrue("中文那份点名还缺的那一层", zh.contains("从那一格回到原卡"))
        assertTrue("英文那份点名还缺的那一层", en.contains("back to the post itself"))
        // 挡住那一档不记这件事两边都得念到 —— 它是这一格唯一的行为边界。
        assertTrue("中文那份没念挡住的那一档不记", zh.contains("挡住的那一档不记"))
        assertTrue("英文那份没念挡住的那一档不记", en.contains("masked posts are not recorded"))
    }

    // ⑮ 那颗存储件只许有一颗：全桌面主源里一行类声明。
    //     ⚠️ 它是 `internal` 的 —— 那颗件交出的是 internal 的那一行类型，公开出来就红
    //     （public 暴露 internal 返回类型是编译错误，本仓记过那一课）。
    @Test
    fun 那颗存储件只有一颗() {
        val declared = DesktopSourceTree.desktopMainSources().flatMap { file ->
            DesktopSourceTree.codeLinesOf(file).filter { it.startsWith("internal class DesktopGalleryBrowseHistory") }
        }
        assertEquals("声明处不止一颗，或那颗改成了公开：$declared", 1, declared.size)
    }

    // ⑯ 坏档那句提示由那一屏念，且**只在这一屏被读**：
    //    读走即清那套挂在屏里会随那一屏离开组合而消失（批 77 那一课），所以这里走 collectAsState 而不是 consume。
    // ⑰ 那一屏的形参**只要那颗件**，不要路径也不要 provider（与批 85 那颗 `saveDir: File` 同一族口径：
    //    读侧的形参表就是它的权限边界）。
}
