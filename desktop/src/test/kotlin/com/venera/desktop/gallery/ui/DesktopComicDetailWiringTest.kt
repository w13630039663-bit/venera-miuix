package com.venera.desktop.gallery.ui

import com.venera.desktop.gallery.data.DesktopComicChapterRun
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
        assertEquals("关的是那一颗局部取出的", 1, DesktopSourceTree.codeOccurrences(paneFile, "onDispose { queueScope.cancel(); closing?.close() }"))
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

    /** ⑨ 失败那一档也必须交出**分母**（包内 b96d1 那一帧抓出来的：站方有 89 页而屏上念 `页=0/0`）。 */
    @Test
    fun 失败那一档的分母从页表来而不从结果来() {
        val cause = IllegalStateException("取页失败 页=63 共=89 因=stream was reset: CANCEL")
        val failed = desktopComicChapterOutcome(1, Result.failure(cause), knownPages = 89, pagesOnDisk = 62)
        assertEquals("页表已经拿到 89 页，失败那一档的分母就不许塌成 0（那正是包内量到的那一句）：", 89, failed.total)
        assertEquals("失败那一档写的页数（这一笔没写成）：", 0, failed.written)
        assertEquals("失败那一档不许还在忙：", false, failed.busy)
        assertTrue("失败那一档要带得上原因：" + failed.failure, !failed.failure.isNullOrBlank())
        assertTrue("原因里要念得出是哪一页：" + failed.failure, failed.failure!!.contains("页=63"))
        // 批 100 添的那一格：失败那一趟盘上已经有 62 页，屏上那句必须说得出 —— 只报"失败"就是把"下了 62 页"抹掉。
        assertEquals("失败那一档也要带得上盘上已有几页：", 62, failed.pagesOnDisk)
        assertEquals("失败那一档不是取消（两档不许塌成一格）：", false, failed.cancelled)

        val ready = desktopComicChapterOutcome(
            1, Result.success(89 to DesktopComicChapterRun.Completed(written = 26)), knownPages = 89, pagesOnDisk = 89,
        )
        assertEquals("成功那一档吃算式自己报的分母：" + ready.total, 89, ready.total)
        assertEquals("成功那一档吃算式自己报的写入数（补缺的那几页，不是整话）：" + ready.written, 26, ready.written)
        assertEquals("成功那一档不许有原因：" + ready.failure, null, ready.failure)
        assertEquals("跑完那一档不许被说成取消：" + ready.cancelled, false, ready.cancelled)

        // 批 100 那一档：runner 自己交回 `Cancelled` 而**不是**调用点猜的 —— 屏上由此才念得出"下次接着补"。
        val stopped = desktopComicChapterOutcome(
            1, Result.success(89 to DesktopComicChapterRun.Cancelled(written = 3)), knownPages = 89, pagesOnDisk = 3,
        )
        assertEquals("取消那一档要认得出：" + stopped.cancelled, true, stopped.cancelled)
        assertEquals("取消那一档不许有原因（它是停手，不是坏）：" + stopped.failure, null, stopped.failure)
        assertEquals("取消那一档的分子是盘上已有：" + stopped.pagesOnDisk, 3, stopped.pagesOnDisk)

        val blind = desktopComicChapterOutcome(1, Result.failure(IllegalStateException("过盾没成")), knownPages = 0, pagesOnDisk = 0)
        assertEquals("页表根本没拿到时不许编一个分母出来：" + blind.total, 0, blind.total)
        assertTrue("页表没拿到也要念得出失败：" + blind.failure, !blind.failure.isNullOrBlank())
    }

    /** ⑩ 那一颗收口件只许有一个调用点，而把分母丢掉的那一形不许回来。 */
    @Test
    fun 收口那一颗只接在一处() {
        assertEquals(
            "声明 + 一处调用 = 2：" + DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, "desktopComicChapterOutcome(") },
            2, DesktopSourceTree.desktopMainSources().sumOf { DesktopSourceTree.codeOccurrences(it, "desktopComicChapterOutcome(") },
        )
        // 页表那一步的页数必须先落到"这一趟知道的页数"，否则失败分支拿不到分母（就是包内那一帧）。
        assertEquals("泵里没有先记下页数（搬家后那一格仍在，只是不在详情那颗文件里了）", 1, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopComicQueueRunner.kt"), "knownPages = pages.size"))
        assertEquals(
            "旧那一形回来了（失败时 `?: 0` 把分母一起吃掉）：",
            0, DesktopSourceTree.codeOccurrences(detailFile, "outcome.getOrNull()?.first ?: 0"),
        )
        // 对照组：读数那一行仍由那颗纯函数收的两枚数拼（改成内联一句就绕过了 ⑨ 那三档输入）。
        assertEquals("失败那一档的读数没吃收口后的两枚数", 1, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopComicQueueRunner.kt"), "cause = cause)"))
    }

    /**
     * ⑪ 调用点必须把**话号**递给清单那一格（第九十七批那条真缺陷的接线面）。
     *
     * 两枚 id 都在这颗件手里（`target.comicId` 与 `episode.id`），递错哪一枚都不会编译失败、也不会红单测的纯函数 ——
     * 只有钉这一整串实参才看得见。写成"含 `episode.id`"是不够的：那一行本来就有多处 `episode.id`。
     */
    @Test
    fun 调用点递的是话号而不是本子号() {
        assertEquals(
            "号与话号两枚各就各位（顺序：`dir, 号, 话号, 行, 题名, 页数`）：" +
                DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopComicQueueRunner.kt"), "desktopComicDownloadChapter(dir, task.comicId, task.chapterId, task.chapterOrder,"),
            1, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopComicQueueRunner.kt"), "desktopComicDownloadChapter(dir, task.comicId, task.chapterId, task.chapterOrder,"),
        )
        assertEquals(
            "旧那一形回来了（两枚都递本子号 ⇒ 清单里的 `chapterId` 装的是号，两端互认全断）：",
            0, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopComicQueueRunner.kt"), "desktopComicDownloadChapter(dir, task.comicId, task.comicId,"),
        )
        // ⚠️ 批 104 搬家预备：**这一枚单独成一颗判式**（先前寄在这条里面，台架 Q2 跑出来红的是上面那条字面锚点，
        //    这一枚压根没跑到 ⇒ "它咬得住"那句话没有证据）。同一条 @Test 里断言是**短路**的：
        //    第一枚一红，后面的判式就只是装饰，归因必须按"哪一颗 @Test 红"来分，不能按"哪一枚断言写的什么"来分。
    }

    /**
     * ⑪b 与位置无关的那枚钉法（批 104）：跨全 main 扫"第二枚与第三枚实参是**同一个表达式**"这一形。
     *
     * 上面 ⑪ 钉的是"**详情那一颗文件里的这一整串**"，而批 101 定档 C 要把那颗 runner 抽成共用件 ⇒
     * 调用点会搬到新文件、形参名也会换（`target.comicId` / `episode.id` 那种写法届时不存在）。
     * 这一枚不依赖任何具体变量名，抽件之后仍然成立，而这一族的真缺陷（两枚 id 递成同一枚）恰是它咬得住的形状。
     * ⚠️ "调用不许消失 / 不许有第二条落盘路"那一层由 `DesktopStaleClaimsTest` 与 `DesktopReadmeStaleClaimsTest`
     * 那两枚跨 main 次数判据承担（同一批一起改的），这一枚不重复管。
     */
    @Test
    fun 调用里两枚实参不许是同一个表达式() {
        val sameArgTwice = Regex("""desktopComicDownloadChapter\(\s*dir\s*,\s*([A-Za-z0-9_.]+)\s*,\s*\1\s*,""")
        val offenders = DesktopSourceTree.desktopMainSources().filter { sameArgTwice.containsMatchIn(DesktopSourceTree.codeText(it)) }
        assertTrue("有调用把两枚 id 递成了同一个表达式（清单那一格装错号 ⇒ 两端互认全断）：" + offenders.joinToString { it.name }, offenders.isEmpty())
    }

    /**
     * ⑫ 第一百批：那两枚新形参**必填、不给默认值**（`shouldCancel` 与 `progress`）。
     *
     * 给了默认值的形状是"调用点漏传不报错"：进度漏传 ⇒ 屏上那一行永远停在起手那一格（看起来像"卡在 0 页"），
     * 取消闸漏传 ⇒ 那枚把手摆着而永远扳不动 —— 两形都是本仓那一族「实现了但没人接」。
     * ⚠️ 判式扫的是**声明那一行**（`codeText` 只吃代码行），所以 `= { false }` 这种默认值一出现就红。
     */
    @Test
    fun 进度与取消那两枚形参必填不给默认值() {
        val data = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicChapterDownload.kt")
        val code = DesktopSourceTree.codeText(data)
        assertTrue("声明里找不到 `shouldCancel` 那一枚（改名了要连本条判据一起改）：" + code, code.contains("shouldCancel: () -> Boolean"))
        assertTrue("声明里找不到 `progress` 那一枚：" + code, code.contains("progress: suspend (written: Int, expected: Int) -> Unit"))
        assertEquals("`shouldCancel` 带了默认值（漏传不报错那一族）：", 0, code.split("shouldCancel: () -> Boolean =").size - 1)
        assertEquals("`progress` 带了默认值：", 0, code.split("progress: suspend (written: Int, expected: Int) -> Unit =").size - 1)
        // 对照：调用点两枚都真递了（只钉声明不钉调用点 = 灰行）。
        assertEquals("那一屏没把取消闸递进去：", 1, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopComicQueueRunner.kt"), "shouldCancel = {"))
        assertEquals("那一屏没把进度递进去：", 1, DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopComicQueueRunner.kt"), "progress = { written, expected ->"))
    }

    /**
     * ⑬ 第一百批：`--download-cancel-after=` 与屏上那枚「取消」**扳的是同一颗闸**。
     *
     * 另开一条"只为取证"的取消路，量到的是那条路而用户那一条可以仍是坏的（批 98 那条纪律的同一形，
     * 也是本仓「种子与把手同一颗 runner」那一族）。所以钉的是**那一整串表达式**，不是"含 `cancelRequested`"——
     * 那个名字在这一屏出现多处，含不含都判不出接线。
     */
    @Test
    fun 取消的种子与把手扳的是同一颗闸() {
        assertEquals(
            "那一颗闸不是「种子与把手共用」的形状（整串表达式）：",
            1, DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopUiSource("DesktopComicQueueRunner.kt"), "shouldCancel = { DesktopComicDownloadQueue.stopRequested(taskId) }",
            ),
        )
        assertEquals(
            "那枚把手没接到扳队列那颗闸的那一枚挂手上：",
            1, DesktopSourceTree.codeOccurrences(detailFile, "onCancelDownload = stopActiveTask"),
        )
        // 反向：种子单独扳一闸 / 把手单独扳一闸，两形都不许出现。
        assertEquals("把手与种子分家了（只认种子那一枚）：", 0, codeOccurrencesOfOnlySeed())
    }

    /** ⑬ 的反向锚点：`shouldCancel` 那一格里只出现 `downloadCancelAfter` 而没有 `cancelRequested` 的那一形。 */
    private fun codeOccurrencesOfOnlySeed(): Int {
        val gate = Regex("shouldCancel = \\{[^}]*\\}").find(DesktopSourceTree.codeText(detailFile))?.value ?: return 0
        return if (gate.contains("downloadCancelAfter") && !gate.contains("cancelRequested")) 1 else 0
    }
}
