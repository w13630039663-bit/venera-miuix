package com.venera.desktop.gallery.ui

import com.venera.desktop.ForensicFlags
import com.venera.desktop.gallery.data.DesktopSavedImage
import com.venera.desktop.gallery.data.DesktopSavedListing
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 85 的接线与那一屏。
 *
 * ## 为什么这一颗测试与 `DesktopImageSaverTest` 分开
 *
 * 那颗钉的是"字节怎么写下去"（纯函数 + 真磁盘），这一颗钉的是"这件事在界面上走得到吗"：
 * 首页那一行点亮了没有、按钮与取证种子是不是同一条路、参数有没有在 gradle 那一跳掉队。
 * 与本仓 `DesktopReverseSeedTest` / `DesktopCopyImageTest` 同一族形状。
 *
 * ## 三条只在这一颗里钉得住的
 *
 * 1. **那一屏不建目录**：`desktopGallerySaveDir` 是纯算的，但 UI 里顺手 `.mkdirs()` 一下就能把
 *    "去看一眼"变成"在盘上造一只目录" —— 所以这条钉在**那一颗界面件**上，而不是数据层。
 * 2. **存的是原图档，复制的是屏上那一档**：两枚按钮挨在一起，共用一枚地址参数的话，
 *    用户切了预览档就会把降档的那一张当成原图存下来。这一条只能钉在调用形状上。
 * 3. **「下载」那一行的旧理由不能再留在树上**：那句"存图落点还没接"从今天起是假话，
 *    而本项目中招过四次"文案说没有而东西已在"（反方向同样要防）。
 */
class DesktopSaveImageSeedTest {

    private val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
    private val home = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryHomeSections.kt"))
    private val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
    private val main = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
    private val gradle = DesktopSourceTree.codeText(File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts"))
    private val flags = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("ForensicFlags.kt"))

    // ① 首页那一行真点亮了：有内容件，也没有了那句"还没接"的理由。
    @Test
    fun `下载那一行点亮了`() {
        val row = desktopGalleryHomeRows().byKey(DesktopGalleryHomeSectionKey.DOWNLOADS)
        assertEquals("行模型改完要点名的还是那一行", "下载", row.title)
        assertTrue("那一行还挂着缺席说明：" + row.reason, row.content is DesktopGallerySectionContent.Downloads)
        assertNull("点亮了却还留着 reason（那两句会同时上屏）：" + row.reason, row.reason)
    }

    // ② 旧那句"存图落点还没接"不许再活在树上（它从今天起是假话）。
    @Test
    fun `旧那句落点没接不再在树上`() {
        val hits = DesktopSourceTree.desktopMainSources()
            .flatMap { file -> DesktopSourceTree.codeLinesOf(file).mapNotNull { if ("存图落点还没接" in it) "${file.name}: $it" else null } }
        assertEquals("还留着那句：" + hits, emptyList<String>(), hits)
    }

    // ③ 内容区的分岔真走到那一屏（表达式位置整串钉住，别只钉一半）。
    @Test
    fun `内容区 routed 到那一屏`() {
        assertTrue("body 没把那一档接到屏上：" + home, home.contains("DesktopGallerySectionContent.Downloads -> DesktopDownloadsPane(saveDir)"))
        assertTrue("行摆那一路漏了这一档（摆不出来就点不到）", home.contains("DesktopGallerySectionContent.Downloads -> DesktopGalleryHomeSelectableRow"))
    }

    // ④ 那一屏**不许建目录**：去看一眼不该在盘上造出一只目录。
    @Test
    fun `那一屏不建目录`() {
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopDownloadsPane.kt"))
        assertEquals("界面件里出现了 mkdirs：" + pane, 0, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopDownloadsPane.kt"), "mkdirs"))
        assertTrue("那一屏走的是枚举读侧而不是写侧", pane.contains("desktopListSavedImages("))
    }

    // ⑤ 按钮与取证种子共用同一枚 lambda（种子跑通的那条路必须就是用户按的那条）。
    @Test
    fun `按钮与种子共用同一枚 lambda`() {
        assertTrue("按钮没走那枚共用 lambda：" + panel, panel.contains("Button(onClick = fireSaveImage)"))
        assertTrue("种子自己又写了一份发车（那它量的就不是用户那条路）：" + panel, panel.contains("LaunchedEffect(post.uid) { fireSaveImage() }"))
    }

    // ⑥ 两枚新形参都是必填（覆盖层在 provider 外面，漏传不报错的形状不许有）。
    @Test
    fun `新形参必填不给默认值`() {
        assertTrue("没给那一屏落点（写到哪里去）：" + panel, panel.contains("saveDir: File,"))
        assertTrue("种子那枚没进形参表：" + panel, panel.contains("autoSaveImage: Boolean,"))
        assertFalse("给了默认值（漏传就是静默不干事）", panel.contains("autoSaveImage: Boolean =") || panel.contains("saveDir: File ="))
    }

    // ⑦ 存的原图档，不是屏上正在摆的那一档（与「复制图片」刻意不同轨）。
    @Test
    fun `存的是原图那一档`() {
        assertTrue("没把落点递给那颗件：" + panel, panel.contains("desktopSaveImageRun(platformContext, saveDir, post)"))
        val line = panel.lines().first { it.contains("desktopSaveImageRun(") }
        assertFalse("调用里出现了屏上那一档的 url（那就会把降档的那张当原图存）：" + line, line.contains(", url"))
    }

    // ⑧ `--save` 登记过，不然带它起窗时 unknown 会多念一句"不认识的参数"。
    @Test
    fun `参数登记过`() {
        assertTrue(flags.contains("private const val SAVE = \"--save\""))
        assertTrue("KNOWN 里没这一枚（读数会自相矛盾）", flags.contains("VIEWER, VIEWER_ZOOM, COPY, SAVE, CRASH"))
    }

    // ⑨ 浮层接管那颗函数现在是四枚必填，且 `--save` 单独也能把那枚按钮带到屏上。
    @Test
    fun `带按钮在浮层里所以要接管浮层`() {
        assertTrue(ForensicFlags.detailPanel(detail = null, reverse = null, copy = null, save = true))
        assertFalse("值为关那一档不该被当成开", ForensicFlags.detailPanel(detail = null, reverse = null, copy = null, save = false))
        assertFalse("四枚都没给时浮层不该自己开", ForensicFlags.detailPanel(detail = null, reverse = null, copy = null, save = null))
        assertTrue(ForensicFlags.detailPanel(detail = null, reverse = null, copy = true, save = null))
    }

    // ⑩ 替用户多开了一扇窗就得说清是为了哪几枚（三档读数两两不许相同）。
    @Test
    fun `接管浮层那句念得出是为了哪一枚`() {
        assertNull("没给任何浮层里的种子 ⇒ 不吭声", ForensicFlags.overlaySeedNote(detail = null, copy = null, save = null))
        assertNull("--detail 本来就开着", ForensicFlags.overlaySeedNote(detail = true, copy = null, save = true))
        val copy = requireNotNull(ForensicFlags.overlaySeedNote(detail = null, copy = true, save = null))
        val save = requireNotNull(ForensicFlags.overlaySeedNote(detail = null, copy = null, save = true))
        val both = requireNotNull(ForensicFlags.overlaySeedNote(detail = null, copy = true, save = true))
        assertEquals("三句不许撞：" + listOf(copy, save, both), 3, listOf(copy, save, both).distinct().size)
        assertTrue("只给了 copy 却把存也念进去了：" + copy, !copy.contains("存到本机") && copy.contains("复制图片"))
        assertTrue("只给了 save 却没念那一枚：" + save, save.contains("存到本机"))
        assertTrue("两枚都给时要两句都在：" + both, both.contains("复制图片") && both.contains("存到本机"))
    }

    // ⑪ 五层接线一层都不能少：判读、集成方、界面、那一跳 gradle、种子清单。
    @Test
    fun `五层接线都在`() {
        assertTrue("main 没判读这一枚：" + main, main.contains("ForensicFlags.state(args.asList(), \"--save\")"))
        assertTrue("main 没把落点算出来递进去：" + main, main.contains("gallerySaveDir = desktopGallerySaveDir(paths)"))
        assertTrue("main 没走那颗接管浮层的纯函数（四枚）：" + main, main.contains("ForensicFlags.detailPanel(detail, reverse, copy, save)"))
        // `startSave = save == true` 住在**集成方**（VeneraDesktop.kt），应用根只是收下来往下递 ——
        // 这一格第一轮把它写成 app 的锚点，红过一次：锚点要钉在真正做那一步的文件上。
        assertTrue("集成方没把三态折成开关：" + main, main.contains("startSave = save == true,"))
        assertTrue("应用根没把它收进形参表：" + app, app.contains("startSave: Boolean,"))
        assertTrue("浮层没收到种子：" + app, app.contains("autoSaveImage = startSave,"))
        assertTrue("D_落点 那行的种子清单漏了这一枚：" + app, app.contains("\"save\".takeIf { startSave },"))
        assertTrue(
            "gradle 那一跳漏了的话 -Psave=1 就是「不报错也不做事」：" + gradle,
            gradle.contains("providers.gradleProperty(\"save\").orNull?.let { \"--save=\$it\" },"),
        )
    }

    // ⑫ 取字节仍只有一颗实现：新加这一格不许自带第二份冷缓存逻辑。
    @Test
    fun `取字节仍只有一处`() {
        val snapshots = DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, "openSnapshot(") }
        assertEquals("`openSnapshot` 在桌面 main 里出现了 $snapshots 次（重述一遍=将来只改一处）", 1, snapshots)
        val saver = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopImageSaver.kt"))
        assertTrue("存这一路没吃那颗共用件（冷缓存那段教训管不到它）", saver.contains("desktopCoilBytes("))
    }

    // ⑬ 那一屏的表头四态各一句（还没建 / 空着 / 读不出 / 有东西是四句话）。
    @Test
    fun `表头四态各念一句`() {
        val lines = listOf(
            desktopSavedHeaderReading(DesktopSavedListing.Missing("D:\\a")),
            desktopSavedHeaderReading(DesktopSavedListing.Empty("D:\\a")),
            desktopSavedHeaderReading(DesktopSavedListing.Unreadable("D:\\a")),
            desktopSavedHeaderReading(DesktopSavedListing.Listed("D:\\a", listOf(DesktopSavedImage("a.jpg", 100L, 1L)))),
        )
        assertEquals("四档要各一句：" + lines, lines.size, lines.distinct().size)
        val listed = lines.last()
        assertTrue("有东西那一档没带出张数：" + listed, listed.contains("1"))
        assertTrue("有东西那一档没说去哪儿看：" + listed, listed.contains("D:\\a"))
    }

    // ⑭ 行里那句带得出名字、真实字节数与时刻，而不带出站方地址。
    @Test
    fun `行里那句念得出名字字节与时刻`() {
        val line = desktopSavedItemLine(DesktopSavedImage("yandere-123.jpg", 4096L, 1_700_000_000_000L))
        assertTrue("少了文件名：" + line, line.contains("yandere-123.jpg"))
        assertTrue("少了字节数：" + line, line.contains("4096"))
        assertTrue("时刻那一串得是年月日时分 shape：" + line, Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""").containsMatchIn(line))
        assertFalse("界面句里出现了 http：" + line, line.contains("http"))
    }

    // ⑮ 那一屏的落点由集成方算好递进来（界面件自己别碰 PathProvider —— 一碰就有第二条建目录的路）。
    @Test
    fun `落点由外面递进来`() {
        val pane = DesktopSourceTree.desktopUiSource("DesktopDownloadsPane.kt")
        val text = DesktopSourceTree.codeText(pane)
        assertEquals("那一屏自己算了落点：" + text, 0, DesktopSourceTree.codeOccurrences(pane, "desktopGallerySaveDir("))
        assertEquals("那一屏自己 import 了 PathProvider", 0, DesktopSourceTree.codeOccurrences(pane, "PathProvider"))
        assertTrue("形参表里没有递进来的落点：" + text, text.contains("saveDir: File"))
    }

    // ⑯ 枚举那颗在界面件里只被叫一次（每次重组都重读盘就是自己造负载）。
    @Test
    fun `枚举那一颗只被叫一次`() {
        val pane = DesktopSourceTree.desktopUiSource("DesktopDownloadsPane.kt")
        assertEquals("读盘那一笔出现了 ${DesktopSourceTree.codeOccurrences(pane, "desktopListSavedImages(")} 次：" + DesktopSourceTree.codeText(pane), 1, DesktopSourceTree.codeOccurrences(pane, "desktopListSavedImages("))
    }

    // ⑰ 两份 README 那一格同轨：旧那句不许留，新那句两边都得有（改一处必须同步另一处，本仓口径）。
    @Test
    fun `两份 README 的那一条同轨`() {
        val zh = File(DesktopSourceTree.repoRoot, "README.md").readText()
        val en = File(DesktopSourceTree.repoRoot, "README.en.md").readText()
        assertFalse("中文那份还写着旧那句", zh.contains("存图落点还没接"))
        assertFalse("英文那份还写着旧那句", en.contains("the save target is not wired"))
        assertTrue("中文那份没写落盘已接", zh.contains("图片落盘已经接上"))
        assertTrue("英文那份没写落盘已接", en.contains("saving images to disk is wired"))
        // 两边都点名"还缺的是章节下载"那一层，不许一边多一条一边少一条。
        assertTrue("中文那份没点名还缺的那一层", zh.contains("漫画章节下载"))
        assertTrue("英文那份没点名还缺的那一层", en.contains("Comic chapter downloads"))
    }
}
