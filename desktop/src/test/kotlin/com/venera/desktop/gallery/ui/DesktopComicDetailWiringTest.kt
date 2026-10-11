package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 批 80 · 漫画卡点开进详情那一屏的**接线层**判据。
 *
 * 屏上那一层（题名/简介/章数/两枚钮）由离屏语义树判据 `DesktopComicDetailSemanticsTest` 钉，
 * 这一颗只管语义树够不着的那几件事：卡位点得动、种子那三跳一处不缺、落点那行念得出详情开没开、
 * 展示层不吃引擎句柄。两份不重测（同一件事两把尺子是本项目的老毛病）。
 */
class DesktopComicDetailWiringTest {

    private val paneFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicPane.kt")
    private val detailFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicDetail.kt")
    private val appFile = DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")

    /** ① 卡位点得动：`onClick` 必填无默认，且整张封面真挂上一次点击。 */
    @Test
    fun 封面卡位可点而那一枚参数不许带默认值() {
        val pane = DesktopSourceTree.codeText(paneFile)
        assertTrue(
            "卡上那枚点击回调带默认值 = 漏传不报错，屏上就是一枚按不动的封面",
            Regex("""onClick: \(EngineSession\.ComicCard\) -> Unit(?! =)""").containsMatchIn(pane),
        )
        assertEquals("整张封面上只许挂一次点击", 1, DesktopSourceTree.codeOccurrences(paneFile, ".clickable { onClick(card) }"))
        // 对照组：卡角那枚「收藏」的接线还在（两枚动作分岔，别把那张封面点进收藏）
        assertEquals("卡角那枚收藏的调用点不止一处", 2, DesktopSourceTree.codeOccurrences(paneFile, "ComicFavoritePill("))
    }

    /** ② 落点沿路只被一个主人改，而"递给谁"这一格在批 82.7 变了形：不再绕探索那一屏，域 body 自己分岔。 */
    @Test
    fun 详情那颗落点沿路只被一个主人改() {
        val pane = DesktopSourceTree.codeText(paneFile)
        assertTrue("域 body 没收详情落点", pane.contains("comicTarget: DesktopComicTarget?"))
        // ⚠️ 这一条从前钉的是 `ComicExplorePane(config,` —— 那是**当时那一版实现的形状**，
        // 而那个形状正是缺陷本身（详情只在探索那一支里组合 ⇒ 站在「阅读历史」那行按一行，
        // 载荷设上了而屏不换）。批 82.7 把渲染点提到域层后，锚点跟着改成两样：
        // 载荷进了那颗纯判断、探索那一屏仍从域 body 组合（引擎现在是域层持有的那颗）。
        // 锚点仍只到"被调件名 + 第一枚实参"那一层（批 81 钉满整串一加参数就误红；坑 ㉖ 第七次）。
        // 参数表本身由 `DesktopComicReaderWiringTest` ⑥ 那颗"实参表非空且七枚都在"的判式钉。
        assertEquals("载荷没进那颗纯判断", 1, DesktopSourceTree.codeOccurrences(paneFile, "hasComic = comicTarget != null"))
        assertEquals("域 body 没把引擎递给探索那一屏", 1, DesktopSourceTree.codeOccurrences(paneFile, "ComicExplorePane(engine,"))
        // 锚点只到「那一处调了 onOpenComic(DesktopComicTarget(」这一层：实参表以后还会添（批 82.6 就添了封面），
        // 钉满整串会把合法的生长红成误伤（手册 §0 坑 ㉖）。封面那一枚实参由 DesktopComicCoverChainTest 钉。
        assertEquals("卡上点开那一处不止一处", 1, DesktopSourceTree.codeOccurrences(paneFile, "onOpenComic(DesktopComicTarget("))
        // 挂载点这一枚锚点退到**被调件名**那一层：实参表换行（批 82.7 域 body 那一支就是长到换行的）
        // 不该红在这里；"恰好一处"这件事本来就只关于件名。声明在别的文件，全数这一颗文件够准。
        assertEquals("详情那一屏的挂载点不止一处", 1, DesktopSourceTree.codeOccurrences(paneFile, "DesktopComicDetailPane("))
        assertFalse("空实现那一种假动作不许出现在这一屏", pane.contains("onClick = { }"))
    }

    /** ③ 装载那一件真问一次，而且是在 IO 线程上问。 */
    @Test
    fun 详情那一屏真去问一次目录而不在组合期问() {
        val detail = DesktopSourceTree.codeText(detailFile)
        assertEquals("问目录那一跳不止一处", 1, DesktopSourceTree.codeOccurrences(detailFile, "engine.details(target.comicId)"))
        assertTrue("那一问没放进 IO 线程（`loadInfo` 是 GraalJS 同步往返，落在组合线程上就是卡整屏）",
            detail.contains("withContext(Dispatchers.IO)") && detail.indexOf("withContext(Dispatchers.IO)") < detail.indexOf("engine.details("))
        assertEquals("装载完那句读数只许念一次", 1, DesktopSourceTree.codeOccurrences(detailFile, "println(desktopComicDetailReading("))
        // 引擎句柄只住装载那一件；展示层吃纯数据，才可能被喂着离屏渲（语义树那颗判据的前提）
        val contentStart = detail.indexOf("internal fun DesktopComicDetailContent")
        assertTrue("展示层没被切出来（找不到那颗函数头）", contentStart > 0)
        assertFalse(
            "展示层又去吃引擎句柄了：那一件一吃 session 就渲不出来，语义树那层会整体退回到只有像素",
            detail.substring(contentStart).contains("EngineSession"),
        )
    }

    /** ④ `--comic=` 那三跳一处不缺（构建脚本 → 集成方 → 应用根）。 */
    @Test
    fun 取证种子那三跳一处不缺() {
        val gradle = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertTrue("`app` 任务没读 gradleProperty(\"comic\")", gradle.contains("""gradleProperty("comic")"""))
        assertTrue("那一枚没被拼进 args（值传不下去，判读层再对也接不到）", gradle.contains("\"--comic=\$it\""))
        assertTrue("对照组失效：这份构建脚本里没有 :desktop:app 那颗粒任务", gradle.contains("""tasks.register<JavaExec>("app")"""))

        val main = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
        assertTrue("集成方没读 --comic", main.contains("ForensicFlags.comic(args.asList())"))
        assertTrue(
            "集成方没把它递进应用根（整串都得钉：只数前缀的话恒传 null 也绿 —— 坑 ㉔）",
            main.contains("startComic = comicSeed.takeIf { comicLanding.domain == DesktopDomain.COMIC }"),
        )
        // 接管那一步与那句落空实话都必须从 main 里走：少了它们，`--comic` 会得到一句假读数
        //（第一次起窗就是这样：`详情=开` 而屏上停在图库）
        assertTrue("集成方没走接管那颗纯函数", main.contains("ForensicFlags.comicLanding("))
        assertTrue("接管没落地时那句实话被省了", main.contains("ForensicFlags.comicNote("))
        assertTrue("对照组失效：画师那颗种子不在这颗文件里了", main.contains("ForensicFlags.artist(args.asList())"))

        val app = DesktopSourceTree.codeText(appFile)
        assertTrue("应用根没收它", app.contains("startComic: DesktopComicTarget?"))
        assertTrue("应用根没把它种进落点", app.contains("mutableStateOf(startComic)"))
    }

    /** ⑤ 落点那一行念得出详情开没开，而且钥匙里有那一枚状态。 */
    @Test
    fun 落点那行念得出详情开没开() {
        val app = DesktopSourceTree.codeText(appFile)
        val lines = app.lines().filter { it.contains("\"D_落点") }
        assertEquals("落点读数不止一行", 1, lines.size)
        val line = lines.single()
        assertTrue("那一行没念详情开没开", line.contains("详情=\${if (comicTarget != null)"))
        assertTrue("那一行没带\"是谁改走的\"那枚戳（四五种解释互相打不掉，批 48 那一课）",
            line.contains("(\$comicStamp)"))
        assertTrue("那颗 effect 的钥匙里没有详情落点：写了不改钥匙 = 那一行停在旧值上",
            app.contains("startViewerZoom, comicTarget != null, comicStamp"))
    }

    /** ⑦ 引擎句柄的退场必须关掉**上一颗**而不是刚建好的那一颗（批 80 第一次真跑踩到的）。 */
    @Test
    fun 退场那一笔关掉的是取出来的那一颗而不是变量当下的值() {
        val pane = DesktopSourceTree.codeText(paneFile)
        assertEquals("dispose 前先把值取进局部", 1, DesktopSourceTree.codeOccurrences(paneFile, "val closing = session"))
        assertEquals("关的是那一颗局部取出的", 1, DesktopSourceTree.codeOccurrences(paneFile, "onDispose { closing?.close() }"))
        assertEquals(
            "闭包里直接读 `session` 的那一形不许回来：它读的是变量当下的值，" +
                "`null → 引擎` 那次切换关掉的正是刚建好的那一颗（屏上表现为详情永远念 态=没成）",
            0,
            DesktopSourceTree.codeOccurrences(paneFile, "onDispose { session?.close() }"),
        )
        // 对照组：那颗 DisposableEffect 本身还在（撤掉它等于句柄泄漏，批 44 那一条判据也钉着）
        assertTrue("退场关句柄那一笔整个没了", pane.contains("DisposableEffect(session)"))
    }

    /** ⑧ 落点读数不许说谎：详情那一格吃的是"递下来的种子"而不是"参数打没打过"。 */
    @Test
    fun 递下去的种子只在域真跟过去时才有() {
        val main = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
        assertTrue(
            "`--comic` 与 `--artist`/`--ranking` 同给而赢的是那一枚时，递下去就等于让 `D_落点` 念一句假话",
            main.contains("startComic = comicSeed.takeIf { comicLanding.domain == DesktopDomain.COMIC }"),
        )
    }

    /** ⑥ 种子那格登记了 `--comic`（漏登记时 `unknown` 会把它报成拼错的参数）。 */
    @Test
    fun 那枚参数登记在已知清单里() {
        val flags = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("ForensicFlags.kt"))
        // ⚠️ 只许截**表内那一段**：写成 `KNOWN = listOf\([\s\S]*?COMIC[\s\S]*?\)` 会一路跨过表尾
        // 吃到 464 行 `comic()` 自己用的那两枚 `COMIC` ⇒ 删掉登记也照样绿。
        // 这一条不是假设：扰动 P8 第一次跑就念 "BAD 一笔都没红"，抓到的就是本鱼这条假尺子。
        val known = Regex("""KNOWN = listOf\(([\s\S]*?)\)""").find(flags)?.groupValues?.get(1)
        assertTrue("找不到 KNOWN 那张表（换了写法就要跟着换截法）", known != null)
        assertTrue("没登记：带 `--comic` 起窗会额外念一句\"不认识的参数\"，读数自相矛盾", known!!.contains("COMIC"))
        assertEquals("判读那颗函数不止一处定义", 1, Regex("""fun comic\(args: List<String>\)""").findAll(flags).count())
    }
}
