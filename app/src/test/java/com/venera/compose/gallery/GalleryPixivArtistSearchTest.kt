package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv 搜索「作品 / 画师」两页签的**接线判据**（2026-10-18 第二十三轮）。
 *
 * 用户原话：「搜索作者（画师）的分栏怎么没做」。这一轮钉的是"**画师那一栏真的存在、
 * 而且是一个独立的东西**"——同一批要防的事有五件：
 * 1. 画师那一栏塞进作品那面墙：名单与瀑布流混在一处，续页判据互相踩；
 * 2. 没登录时那一栏**静默变空**（或悄悄退回作品那一栏）⇒ 用户以为"这个词没有画师"；
 * 3. 翻页按页数走：`/v1/search/user` 与 `/v1/search/illust` 一样只认 `next_url`；
 * 4. 画师那一栏也去点图库站那条续页闸门：pixiv 域没有标签胶囊，判据恒假 ⇒ 永远滑不到底；
 * 5. 页签行被 notice 那一行顶掉：点了没登录却看不到回响。
 *
 * 为什么用源码判据而不是组合测试：`app/` 没有 Robolectric（同 `GalleryPixivHomeWiringTest`
 * 那条口径），而这一族要守的全是"**有没有、顺序对不对、判据吃的是哪个量**"。
 *
 * ⚠️ 注释行一律不参与匹配（`codeLines` 那把口径）：这些文件里大段注释都写着同样的函数名。
 */
class GalleryPixivArtistSearchTest {

    private val domain = codeLines("com/venera/compose/gallery/domain", "PixivSearchKind.kt")
    // 页签那一枚的"为什么只有登录腿"写在 KDoc 里，而 KDoc 全被 codeLines 过滤掉了 ——
    // 这一条判据要的是**注释本身**，所以给它一份不过滤的原文。
    private val domainRaw = rawText("com/venera/compose/gallery/domain", "PixivSearchKind.kt")
    private val api = codeLines("com/venera/compose/gallery/data", "GalleryBusinessApi.kt")
    private val client = codeLines("com/venera/compose/gallery/data", "PixivAppClient.kt")
    private val adapter = codeLines("com/venera/compose/gallery/data/android", "GalleryAdaptersAndroid.kt")
    private val vm = codeLines("com/venera/compose/gallery/ui", "GallerySearchViewModel.kt")
    private val area = codeLines("com/venera/compose/gallery/ui", "GalleryPixivSearchArea.kt")
    private val screen = codeLines("com/venera/compose/gallery/ui", "GalleryScreen.kt")

    private fun codeLines(dir: String, name: String): List<String> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，根：${RepoSources.root.absolutePath}）")
        return file.readLines().filter { line ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    private fun rawText(dir: String, name: String): String {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，根：${RepoSources.root.absolutePath}）")
        return file.readText()
    }

    /**
     * 量具本身差点坏过一次：自定义扩展不能叫 `indexOf` —— 与 stdlib 的
     * `List<String>.indexOf(element)` 同签名同名，解析赢的是 stdlib 那一枚，
     * 于是它按**整行相等**去找，找不到就返回 -1，判据全数静默失效
     * （本文件初版 6 条红里有 2 条就是这么来的）。一律走 [rowOf]。
     */
    private fun List<String>.rowOf(anchor: String): Int =
        indexOfFirst { it.contains(anchor) }.also { assertTrue("找不到锚点 `$anchor`", it >= 0) }

    private fun List<String>.textOf(anchor: String, span: Int = 8): String {
        val from = rowOf(anchor)
        return subList(from, (from + span).coerceAtMost(size)).joinToString("\n")
    }

    // ── 一、页签本身 ────────────────────────────────────────────────────────

    @Test
    fun `两个页签，画师只有登录腿`() {
        // 页签名与"为什么只有登录腿"都走原文：后者在 KDoc 里（被 codeLines 过滤掉的那一族）。
        assertTrue("要有作品那一档：\n$domainRaw", domainRaw.contains("ILLUST(\"作品\")"))
        assertTrue("要有画师那一档：\n$domainRaw", domainRaw.contains("ARTIST(\"画师\")"))
        assertTrue(
            "画师页签要写明没有匿名腿、不能静默退回：\n$domainRaw",
            domainRaw.contains("只有登录腿") && domainRaw.contains("不许静默退回"),
        )
    }

    @Test
    fun `画师走 app-api 的 search-user`() {
        val url = client.textOf("SEARCH_USER = ")
        assertTrue(
            "端点必须是 /v1/search/user：\n$url",
            url.contains("https://app-api.pixiv.net/v1/search/user"),
        )
        // 翻页只有 next_url 一条路（同一个端点两用：带上就是下一页）。
        val fun3 = client.textOf("suspend fun searchUser(", 14)
        assertTrue("要收 next_url 当游标：\n$fun3", fun3.contains("nextUrl"))
    }

    // ── 二、端口契约 ────────────────────────────────────────────────────────

    @Test
    fun `契约与安卓适配都接了画师这一跳`() {
        val contract = api.textOf("suspend fun searchUser(", 8)
        assertTrue(
            "契约里画师那一跳只收 bearer（没有匿名腿）：\n$contract",
            contract.contains("bearer: String") && contract.contains("Result<PixivUserPage>"),
        )
        assertTrue(
            "适配器要把这一跳转给 appClient：\n$adapter",
            adapter.any { it.contains("override suspend fun searchUser(") } &&
                adapter.any { it.contains("appClient.searchUser(bearer, word, nextUrl)") },
        )
    }

    // ── 三、ViewModel：独立的状态与游标 ────────────────────────────────────

    @Test
    fun `画师那一栏的状态与作品那一栏完全分开`() {
        for (anchor in listOf(
            "pixivArtists = emptyList()",
            "pixivArtistError = null",
            "pixivArtistsExhausted = false",
            "pixivArtistPage = 0",
            "pixivArtistNextUrl = null",
            "isSearchingArtists",
        )) {
            assertTrue("画师那一栏要有自己的状态 `$anchor`", vm.any { it.contains(anchor) })
        }
        // 名单不许塞进作品那面墙的 `results`：两栏墙形不同，混在一处续页判据一定互相踩。
        assertTrue(
            "画师名单不许写进 results（那会把名单当瀑布流续页）：\n" + vm.joinToString("\n"),
            vm.none { it.contains("results = results + pixivArtists") },
        )
    }

    @Test
    fun `没登录时画师那一栏说明原因，不退回作品`() {
        val body = vm.textOf("val bearer = pixivAccount?.bearerOrNull()", 6)
        assertTrue(
            "没票据必须抛一句人话（不许静默变空）：\n$body",
            body.contains("画师那一栏要登录 pixiv 才搜得动"),
        )
    }

    @Test
    fun `翻页按 next_url 走，按 id 去重，没有 next_url 就是到底`() {
        val body = vm.textOf("runPixivArtistSearch(first = false)", 6)
        assertTrue("下一页必须是再调一次同一跳：\n$body", body.contains("runPixivArtistSearch(first = false)"))
        val body2 = vm.textOf("val cursorAtRequest = if (first) null else pixivArtistNextUrl", 34)
        assertTrue("下一页要带上站方游标：\n$body2", body2.contains("port.searchUser(bearer, word, cursorAtRequest)"))
        assertTrue(
            "去重按用户号（站方翻页边界会重复给同一位）：\n$body2",
            body2.contains("val seen = pixivArtists.mapTo(HashSet()) { it.id }"),
        )
        assertTrue(
            "站方没给 next_url 就是到底：\n$body2",
            body2.contains("pixivArtistsExhausted = page.nextUrl == null"),
        )
    }

    @Test
    fun `词或域或页签换了，在途那一页不许落地`() {
        // 锚点必须落在画师那一支**函数体内**：`val roundAtRequest = contextRound` 在
        // booru 的 runSearch 里还有一模一样的一行，取第一处就会去校验另一条腿。
        val fn = vm.drop(vm.rowOf("private fun runPixivArtistSearch"))
        val body = fn.take(40).joinToString("\n")
        for (anchor in listOf(
            "roundAtRequest != contextRound",
            "domain != GalleryHomeDomain.PIXIV",
            "pixivKind != PixivSearchKind.ARTIST",
            "pixivTerm.trim() != word",
        )) {
            assertTrue("落地前要复验 `$anchor`：\n$body", body.contains(anchor))
        }
    }

    // ── 四、搜索面板：页签行 + 画师态不摆作品那套筛选 ──────────────────────

    @Test
    fun `面板顶上常驻一枚作品与画师的页签行`() {
        val row = area.textOf("private fun PixivKindRow(", 22)
        assertTrue("两枚页签都要摆：\n$row", row.contains("PixivSearchKind.entries.forEach"))
        assertTrue("点哪一枚要回给上层：\n$row", row.contains("onPick(candidate)"))
        // 调用点在 svm.notice 那一行**上面**：notice 是"点了没生效"的回响，
        // 它排在页签行下面；顺序反了不会报错，但那一行会把页签顶出屏。
        assertTrue(
            "页签行要摆在 notice 那一行上面（notice 不长在它上面）：",
            area.rowOf("kind = svm.pixivKind,") < area.rowOf("svm.notice?.let"),
        )
    }

    @Test
    fun `画师态不摆作品的排序与面板，只留词与历史`() {
        val start = area.rowOf("if (svm.pixivKind == PixivSearchKind.ARTIST) {")
        val end = area.drop(start).indexOfFirst { it.trimStart().startsWith("} else {") }
        assertTrue("画师那一支要有个 else（作品那一支还在）", end > 0)
        val branch = area.subList(start, start + end).joinToString("\n")
        assertTrue("画师态要留历史那一格：\n$branch", branch.contains("PixivHistoryBlock("))
        assertTrue("画师态不许再摆排序行：\n$branch", !branch.contains("PixivSortRow("))
        assertTrue("画师态不许再摆筛选面板：\n$branch", !branch.contains("PixivFilterPanel("))
        assertTrue("画师态不许再捧出一个开合态：\n$branch", !branch.contains("pixivPanelOpen"))
    }

    @Test
    fun `未登录那句话在画师态也真的上屏`() {
        // 这一格从前是空的：pixiv 卡整块没渲染过 svm.notice，点了没登录也不会有回响。
        val body = area.textOf("svm.notice?.let", 12)
        assertTrue("那句话要有真读数是 notice 自己：\n$body", body.contains("svm.notice?.let"))
        assertTrue("要有落地的显示件：\n$body", body.contains("SearchNoticeLine(message)"))
    }

    // ── 五、GalleryScreen：独立的三支 + 读数分流 ────────────────────────────

    /**
     * 交了词就要**收条**（2026-10-22，用户报「pixiv 站点搜索后返回画廊，会显示这个搜索框」）。
     *
     * 立案读数：`runPixivSearch` 从头到尾不碰 `mode`，而 booru 那条路 `runSearch(thenCollapse)`
     * 会在提交时收成 RESULTS。于是 pixiv 这边交完词，屏上留着一张**展开的**输入卡 + 键盘，
     * 结果墙在它底下 —— 用户从大图页退回来看到的就是"只剩一个搜索框"。
     *
     * 钉的是 `submitPixivTerm` 里那一行的位置：收条必须排在**两条腿之前**。
     * 排在之后的话，画师那一栏（`runPixivArtistSearch` 自己也会动 mode）会先落一帧 INPUT。
     */
    @Test
    fun `交了词就收条，不留一张展开的输入卡`() {
        val fn = vm.drop(vm.rowOf("fun submitPixivTerm()"))
        val collapseAt = fn.indexOfFirst { it.contains("mode = GallerySearchMode.RESULTS") }
        assertTrue("submitPixivTerm 里必须收条：\n$fn", collapseAt >= 0)
        val legAt = fn.indexOfFirst { it.contains("runPixivArtistSearch(first = true)") }
        assertTrue("画师那条腿要还在：\n$fn", legAt >= 0)
        assertTrue("收条要排在两条腿之前（collapse=$collapseAt leg=$legAt）", collapseAt < legAt)
        // 空词那一档不许收条：那一档直接 return，屏上留的是"先打一个词"。
        assertTrue(
            "空词要提前返回：\n$fn",
            fn.indexOfFirst { it.contains("pixivTerm.isBlank()") } < collapseAt,
        )
    }

    @Test
    fun `画师那一栏有加载中、空态、名单三支`() {
        assertTrue(
            "要判 pixivArtistMode：\n" + screen.joinToString("\n"),
            screen.any { it.contains("val pixivArtistMode = domain == GalleryHomeDomain.PIXIV") },
        )
        assertTrue(
            "加载中一支：",
            screen.any { it.contains("svm.pixivArtists.isEmpty() && svm.isSearchingArtists") },
        )
        assertTrue(
            "空态一支：",
            screen.any { it.contains("svm.pixivArtists.isEmpty() && !svm.isSearchingArtists") },
        )
        val list = screen.textOf("pixivArtistMode && svm.active -> GalleryPixivArtistResults(", 16)
        assertTrue(
            "名单一支，且滑到底要接着取：\n$list",
            list.contains("onLoadMore = { svm.loadMorePixivArtists() }"),
        )
        assertTrue(
            "名单那一支自己挂 nestedScroll（不借作品那面墙的连接）：\n$list",
            list.contains("nestedScroll(topBarBehavior.nestedScrollConnection)"),
        )
    }

    @Test
    fun `两栏各报自己的读数`() {
        val call = screen.textOf("resultCount = if (pixivArtistMode)", 8)
        assertTrue("画师报的是人数：\n$call", call.contains("svm.pixivArtists.size"))
        assertTrue("作品报的是张数：\n$call", call.contains("searchWall.cards.size"))
        // 一次都还没落地时不报 0。
        assertTrue("没到货不报 0：\n$call", call.contains("takeIf { svm.pixivArtistPage > 0 }"))
        // 「N 位 / N 张」这句是搜索卡自己写的（两栏一句话分流），也得在面板里。
        val strip = area.textOf("resultCount 位", 3)
        assertTrue("画师要报的是 N 位：\n$strip", strip.contains("\$resultCount 位"))
        assertTrue("作品要报的是 N 张：\n$strip", strip.contains("\$resultCount 张"))
    }

    // ── 六、两族墙的续页判据 ───────────────────────────────────────────────

    @Test
    fun `pixiv 域没有标签胶囊，续页判据吃 hasQuery`() {
        val gates = screen.filter { it.contains("val wallActive = svm.active && svm.hasQuery") }
        assertTrue(
            "两处续页判据都要改成 svm.hasQuery（pixiv 域 filters 恒空 ⇒ 老判据恒假）：现在 ${gates.size} 处",
            gates.size >= 2,
        )
        assertTrue(
            "不许再有拿 filters 当 gate 的写法：\n" + screen.joinToString("\n"),
            screen.none { it.contains("val wallActive = svm.active && svm.filters.isNotEmpty()") },
        )
    }

    // ── 七、搜索卡与顶栏的间距：跟着折叠一起收 ───────────────────────────

    @Test
    fun `顶栏折起来那一截空白，搜索卡要跟着收上去`() {
        // 用户 2026-10-18：「搜索栏和顶部的间距还是太高了，压下」。
        // 成因不是卡自己高，而是卡的落点取的是**展开态**地板（104dp）—— 用户点 🔍 时
        // 墙往往已滚过一段，顶栏早折成 52dp 那一行、大标题 alpha 归零，屏上就多出一段
        // 什么都没有的 52dp。这里钉的是"那一段 = 落点 − 折叠地板"。
        val define = screen.textOf("val searchOverlayFollow", 8)
        assertTrue(
            "收起的量必须从现成 token 里算出来、且跟着**落点**走（不新立数字、不另钉 topBarFloor）：\n$define",
            // ⚠️ 2026-10-18 01:32 改判（用户「在首页搜索框的时候，头图会异常显示」连带）：
            // 被减数从 `topBarFloor` 换成 `searchOverlayTop`。有头图时落点是"状态栏 + 148 − 24"那一档，
            // 硬钉 topBarFloor 会算出与卡实际锚点不同源的一截，两枝各漂各的；
            // 无头图时两者相等（都是 statusBar + 104dp）⇒ 这一档的量仍是 104 − 52 = 52dp，逐像素不变。
            // `coerceAtLeast(0.dp)` 也在锚点串里：落点若被改到比折叠地板还高，宁可收 0 也不许负位移。
            define.contains(
                "(searchOverlayTop - statusBarTop - VeneraSpacing.topBarCollapsedHeight).coerceAtLeast(0.dp)",
            ),
        )
        val offset = screen.textOf("IntOffset(", 8)
        assertTrue(
            "位移要乘折叠进度（不是写死一截）：\n$offset",
            offset.contains("searchOverlayFollow.toPx() * topBarBehavior.state.collapsedFraction"),
        )
        // 折叠进度是逐帧读数：在组合期读 = 滚动全程每帧重组这一整屏。
        assertTrue(
            "必须走 offset 的布局 lambda（惰性读取只重跑布局）：\n" + screen.joinToString("\n"),
            screen.any { it.trim() == ".offset {" },
        )
    }
}
