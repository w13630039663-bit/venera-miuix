package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 批 81 · 阅读那一屏的**接线层**判据。
 *
 * 屏上那一层由 `DesktopComicReaderSemanticsTest` 钉、算式与锁由 `DesktopComicPageFetcherTest` 钉，
 * 这一颗只管那两层够不着的事：三跳一处不缺、挂载次序、装载在 IO 线程上问、展示层不吃引擎句柄、
 * 阅读屏不许自己造引擎。同一件事不重测两把尺子（本仓的老毛病）。
 */
class DesktopComicReaderWiringTest {

    private val readerFile = DesktopSourceTree.desktopUiSource("DesktopComicReader.kt")
    private val paneFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicPane.kt")
    private val detailFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicDetail.kt")
    private val appFile = DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")
    private val mainFile = DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt")
    private val pipelineFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopImagePipeline.kt")
    private val fetcherFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicPageFetcher.kt")

    /** ① 页表那一问在 IO 线程上，而且**先转线程再问**（顺序钉，不是两枚存在性判式）。 */
    @Test
    fun 页表那一问排在转线程之后而装载只念一次读数() {
        val reader = DesktopSourceTree.codeText(readerFile)
        assertEquals("问页表那一跳不止一处", 1, DesktopSourceTree.codeOccurrences(readerFile, "engine.pages(reading.comicId, episode)"))
        val hop = reader.indexOf("withContext(Dispatchers.IO)")
        val ask = reader.indexOf("engine.pages(")
        assertTrue("那一问没放进 IO 线程（`loadEp` 是 GraalJS 同步往返，落在组合线程上就是卡整屏）", hop in 0 until ask)
        assertEquals("装载完那句读数只许念一次", 1, DesktopSourceTree.codeOccurrences(readerFile, "println(desktopComicReaderReading("))
    }

    /** ② 展示层不吃引擎句柄：它一吃，语义树那一层就整体退回只有像素。 */
    @Test
    fun 展示层不吃引擎句柄() {
        val reader = DesktopSourceTree.codeText(readerFile)
        val contentStart = reader.indexOf("internal fun DesktopComicReaderContent")
        assertTrue("展示层没被切出来（找不到那颗函数头）", contentStart > 0)
        assertFalse(
            "展示层又去吃引擎句柄了：那一件一吃 session 就渲不出来，语义树那层会整体退回到只有像素",
            reader.substring(contentStart).contains("EngineSession"),
        )
        // 对照组：装载那一件确实吃（撤掉它等于这一屏永远停在"等回话"）
        assertTrue("装载层没吃引擎句柄（那这一屏根本没东西可摆）", reader.contains("session: EngineSession?"))
    }

    /** ③ 阅读屏不许自己造引擎 —— 引擎句柄只有一颗，住在探索那一屏（批 80 ⑦ 钉的是退场，这一条钉的是新建）。 */
    @Test
    fun 阅读屏不新建引擎() {
        val reader = DesktopSourceTree.codeText(readerFile)
        assertEquals("阅读屏里出现了造引擎那一笔（换一话应当复用同一颗，重建等于重新 parse 整份源脚本）", 0, Regex("""DesktopJsHost\(""").findAll(reader).count())
        assertEquals("阅读屏里出现造会话那一笔", 0, Regex("""EngineSession\(""").findAll(reader).count())
        // 对照组：造引擎那两笔仍在探索那一屏里（不是被整体撤掉了）
        val pane = DesktopSourceTree.codeText(paneFile)
        assertTrue("探索那一屏不再造引擎了（那这一条判据就成了恒真的装饰）", pane.contains("DesktopJsHost(config.assetDir"))
    }

    /** ④ 取字节那一跳被锁包着，而锁与那一问的**次序**要紧（两枚存在性判式盖不住"锁在外头"）。 */
    @Test
    fun 取页那一问被引擎锁包着() {
        val fetcher = DesktopSourceTree.codeText(fetcherFile)
        val lock = fetcher.indexOf("withDesktopEngineLock(page.lockKey)")
        val ask = fetcher.indexOf("engine.pageImage(")
        assertTrue("找不到锁那一笔", lock >= 0)
        assertTrue("取字节那一问排在锁**之外**（并发进去就是批 80 那种 Context cancelled）", lock < ask)
        assertEquals("取页那一问只许出现在锁里那一处", 1, DesktopSourceTree.codeOccurrences(fetcherFile, "engine.pageImage("))
        // 工厂必须吃那颗谓词：整串表达式（只数前缀的话"恒返回一颗 fetcher"也绿 —— 坑 ㉔）
        assertTrue(
            "工厂没走那颗谓词（引擎缺席时硬造一页，红得像网络坏了）",
            fetcher.contains("if (!desktopComicPageCanFetch(data)) return null"),
        )
    }

    /** ⑤ 内存缓存那两枚成对挂：只挂 fetcher 不挂 Keyer 时 Coil 交回 null 键，屏上看不出来而缓存是假的。 */
    @Test
    fun 取图那台成对挂着键与取数件() {
        val pipeline = DesktopSourceTree.codeText(pipelineFile)
        val block = Regex("""components \{([\s\S]*?)\n\s*\}\s*\n\s*\.build""").find(pipeline)
        assertTrue("找不到 components 那一段（换了写法就要跟着换截法）", block != null)
        val body = block!!.groupValues[1]
        assertTrue("没挂页的取数件", body.contains("add(DesktopComicPageFetcher.Factory())"))
        assertTrue("没挂页的缓存键：那一格的语义是「不进内存缓存」，而屏上完全看不出来", body.contains("add(DesktopComicPageKeyer)"))
        // 对照组：网络那一枚仍在（撤掉它等于画廊的图全断了）
        assertTrue("对照组失效：网络取数件不在这段里了", body.contains("OkHttpNetworkFetcherFactory("))
    }

    /** ⑥ 三跳一处不缺：构建脚本 → 集成方 → 应用根 → 域 body → 探索那一屏。 */
    @Test
    fun 种子那三跳一处不缺() {
        val gradle = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertTrue("`app` 任务没读 -Pread", gradle.contains("""gradleProperty("read")"""))
        assertTrue("那一枚没被拼进 args（值传不下去，判读层再对也接不到）", gradle.contains("\"--read=\$it\""))

        val main = DesktopSourceTree.codeText(mainFile)
        assertTrue("集成方没读 --read", main.contains("ForensicFlags.read(args.asList(), readPageSeed)"))
        assertTrue("集成方没走接管那颗纯函数", main.contains("ForensicFlags.readLanding("))
        assertTrue("接管没落地时那句实话被省了", main.contains("ForensicFlags.readNote("))
        assertTrue(
            "整串都得钉：只数前缀的话恒传 null 也绿（`--artist` 抢走落点时递下去就是让 D_落点 念一句假话）",
            main.contains("startRead = readSeed.takeIf { readLanding.domain == DesktopDomain.COMIC }"),
        )

        val app = DesktopSourceTree.codeText(appFile)
        assertTrue("应用根没收它", app.contains("startRead: DesktopComicReading?"))
        assertTrue("应用根没把它种进落点", app.contains("mutableStateOf(startRead)"))
        assertEquals("域 body 的挂载点不止一处", 1, DesktopSourceTree.codeOccurrences(appFile, "DesktopComicBody("))
    }

    /**
     * ⑦ 阅读比详情高一层，且装载那一件在**分岔之前**（退回时底下那一屏已经在，不重跑 `loadInfo`）。
     *
     * ⚠️ 这一条从前钉的是源码里 `readTarget?.let` / `comicTarget?.let` 两块的**先后**。
     * 那个先后是"当时那一版实现的形状"，而形状本身是缺陷（两屏只挂在探索那一支里 ⇒ 从「阅读历史」
     * 按一行进不去详情）；批 82.7 把渲染点提到域层后，优先级归了纯判断 `desktopComicScreen`
     * —— 那一格的判据在 `DesktopComicRowDispatchTest`（同一件事只许一把尺子钉措辞，这里不重钉）。
     * 这一颗改成钉它自己那一份**接线顺序**：装载 effect 必须排在分岔**前面**。
     */
    @Test
    fun 阅读挂在详情之前而两枚槽各归各() {
        val pane = DesktopSourceTree.codeText(paneFile)
        // 两枚载荷都进了那颗纯判断（只进一枚 = 另一枚设上了也没屏可换，正是这一批拍到的那一格）
        assertTrue("阅读载荷没进判断", pane.contains("hasRead = readTarget != null"))
        assertTrue("详情载荷没进判断", pane.contains("hasComic = comicTarget != null"))
        // 顺序判据，不是存在判据：**持有件先要到手，再分岔**。装载写在某一支里的话，
        // 换一行就重跑一次整源装载，而"代码里出现过 LaunchedEffect"那种存在性判式全绿（手册 §0 坑 ㉖）。
        // 批 82.7 起装载搬进了 `rememberComicEngine`（定义在 body 之后的另一颗函数里），
        // 于是这一格钉的是"调用排在 `when` 前面"，dispose 那一格由 `DesktopComicRowDispatchTest` ⑤ 钉。
        val loadHop = pane.indexOf("rememberComicEngine(")
        val dispatchHop = pane.indexOf("when (screen)")
        assertTrue("域 body 没要引擎（持有件没被调）", loadHop >= 0)
        assertTrue("分岔那一格不见了", dispatchHop >= 0)
        assertTrue("引擎在分岔**之后**才要 ⇒ 分岔里某一屏拿到的永远是 null", loadHop < dispatchHop)
        assertEquals("阅读屏的挂载点不止一处", 1, DesktopSourceTree.codeOccurrences(paneFile, "DesktopComicReaderPane("))
        // 两枚槽不许并成一枚：并了的话「← 返回详情」只能回到列表
        assertTrue("域 body 没单收阅读槽（与详情共一枚 = 返回时回不到章节表）", pane.contains("readTarget: DesktopComicReading?"))
    }

    /** ⑧ 落点那一行念得出阅读开没开、带"是谁改的"那枚戳，钥匙里有那两枚状态。 */
    @Test
    fun 落点那行念得出阅读开没开() {
        val app = DesktopSourceTree.codeText(appFile)
        val lines = app.lines().filter { it.contains("\"D_落点") }
        assertEquals("落点读数不止一行", 1, lines.size)
        val line = lines.single()
        assertTrue("那一行没念阅读开没开", line.contains("阅读=\${if (readTarget != null)"))
        assertTrue("那一行没带\"是谁改走的\"那枚戳", line.contains("(\$readStamp)"))
        assertTrue("那颗 effect 的钥匙里没有阅读落点：写了不改钥匙 = 那一行停在旧值上",
            app.contains("readTarget != null, readStamp)"))
        // 换域/换页时两枚一起收：只收详情的话切一次域会留下"阅读屏开在别的域上"的假象。
        // 截的是那一颗 effect 的**体内那一段**（数全局出现次数会被"点了别本""退回详情"两处喂饱）。
        val collapse = Regex("""if \(comicLandingSlot == slot\) return@LaunchedEffect([\s\S]*?)\n\}""").find(app)
        assertTrue("找不到换域换页那笔清理（换了写法就要跟着换截法）", collapse != null)
        val body = collapse!!.groupValues[1]
        assertTrue("换域换页时没收掉详情", body.contains("comicTarget = null"))
        assertTrue("换域换页时没收掉阅读槽：切一次域会留下「阅读屏开在别的域上」的假象", body.contains("readTarget = null"))
    }

    /** ⑨ 章节行点得动，而且那句"整部（这一本没有分话）"只住在一颗件里。 */
    @Test
    fun 章节行点得动而那句兜底话只有一处字面() {
        val detail = DesktopSourceTree.codeText(detailFile)
        assertEquals("章节行没挂上点击", 1, DesktopSourceTree.codeOccurrences(detailFile, ".clickable { onOpenEpisode("))
        assertTrue("点击回调没沿形参递下来（形参收了而调用点写死一枚 lambda 也算没接）",
            detail.contains("onOpenEpisode = onOpenEpisode"))
        // 那句兜底话从前在详情里手拼一遍，如今两处共用一颗件：字面量只许活在那一颗件里
        assertFalse("详情里又手拼了一遍那句兜底话", detail.contains("整部（这一本没有分话）"))
        assertEquals("那一颗件里的字面只许一处", 1, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopComicReader.kt"), "整部（这一本没有分话）"))
        assertTrue("详情没改吃那颗件", detail.contains("desktopReaderEpisodeLabel(episode.name,"))
    }

    /** ⑩ 观感那三枚口径是从对岸抄来的，不许悄悄换成桌面自定的值。 */
    @Test
    fun 观感口径抄的是对岸那一行() {
        val reader = DesktopSourceTree.codeText(readerFile)
        assertTrue("每页不再铺满宽度按比例放（对岸纵向流那一行原样）", reader.contains("ContentScale.FillWidth"))
        assertTrue("间距没吃那枚常量（换成写死的数就断了与对岸默认值的那笔账）",
            reader.contains("Arrangement.spacedBy(DESKTOP_READER_PAGE_GAP_DP.dp)"))
        assertTrue("列表键没吃那颗纯函数", reader.contains("desktopReaderItemKey(reading.episodeId, reading.comicId, index)"))
        // 布局不变式：这一族永远不许把 Lazy 列表塞进 verticalScroll
        assertFalse("LazyColumn 又套上 verticalScroll 了（那一条整屏崩的不变式）", reader.contains("verticalScroll"))
    }
}
